import asyncio
import json
import struct
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from display import serve_display
from esp_simulator import EspState


class DisplayTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.state = EspState(Path(self.temp.name) / 'state.json')
        self.view = self.state.display

    def test_weather_stales_on_timeout_and_disconnect_and_rejects_bad_frame(self):
        from bumble.att import ATT_Error
        frame = dict(v=1, type='weather', active=True, stale=False, available=True,
                     temperature='50°F', conditions='Rain', wind='10 mph', rain='40%', status='GPS weather')
        with patch('display.time.monotonic', return_value=100):
            self.view.connected = True
            self.state.write('ride', json.dumps(frame).encode())
            self.assertFalse(self.view.snapshot()['weather']['stale'])
            self.assertEqual(self.view.snapshot()['weather']['temperature'], '50°F')
            with self.assertRaises(ATT_Error):
                self.state.write('ride', json.dumps(dict(frame, available=1)).encode())
            self.assertTrue(self.view.snapshot()['weather']['available'])
        with patch('display.time.monotonic', return_value=116):
            self.assertTrue(self.view.snapshot()['weather']['stale'])
        self.view.connected = False
        self.assertTrue(self.view.snapshot()['weather']['stale'])

    def test_scenarios_are_labeled_and_restore_live_state(self):
        self.view.connected = True
        original = self.view.snapshot()
        self.view.demo({'action': 'scenario', 'name': 'disconnected'})
        sample = self.view.snapshot()
        self.assertEqual(sample['scenario'], 'disconnected')
        self.assertFalse(sample['connected'])
        self.assertTrue(self.view.connected)
        self.assertTrue(sample['weather']['stale'])
        self.view.demo({'action': 'scenario', 'name': 'live'})
        restored = self.view.snapshot()
        self.assertTrue(restored['connected'])
        self.assertEqual(restored['media']['title'], original['media']['title'])
        with self.assertRaises(ValueError):
            self.view.demo({'action': 'scenario', 'name': 'invalid'})

    def test_ble_payloads_reach_display(self):
        self.state.write('media', struct.pack('<BHH', 1, 32, 200) + 'Song ♥\x03Artist'.encode())
        self.state.write('time', bytes([14, 45, 3]))
        self.state.write('phone_battery', bytes([68, 1]))
        self.state.write('network', bytes([4, 8]))
        self.state.write('brightness', bytes([200]))
        self.state.write('screen', bytes([2]))
        data = self.view.snapshot()
        self.assertEqual((data['clock'], data['layout'], data['brightness']), ('14:45', 2, 200))
        self.assertEqual((data['phoneBattery'], data['charging'], data['networkType']), (68, True, '5G'))
        self.assertEqual(data['media']['title'], 'Song ♥')

    def test_samples_do_not_replace_live_media_or_saved_settings(self):
        original = bytes(self.state.settings)
        self.view.demo({'action': 'media', 'title': 'Sample', 'artist': 'Demo'})
        self.state.write('media', struct.pack('<BHH', 0, 8, 100) + b'Live\x03Phone')
        self.assertEqual(self.view.snapshot()['media']['title'], 'Sample')
        self.view.demo({'action': 'live'})
        self.assertEqual(self.view.snapshot()['media']['title'], 'Live')
        self.assertEqual(bytes(self.state.settings), original)
        self.assertFalse(self.state.path.exists())

    def test_popup_expiry_and_threshold_crossing(self):
        with patch('display.time.monotonic', return_value=100):
            self.state.write('notification', b'Messages\x03Hello\x03Test body')
            self.state.write('phone_battery', b'\x0f\0')
        with patch('display.time.monotonic', return_value=106):
            self.state.write('phone_battery', b'\x0e\0')
            self.assertIsNone(self.view.snapshot()['lowBattery'])
            self.assertEqual(self.view.snapshot()['notification']['title'], 'Hello')
        with patch('display.time.monotonic', return_value=111):
            self.assertIsNone(self.view.snapshot()['notification'])

    def test_calibration_and_adaptive_light(self):
        data = bytearray(self.state.settings)
        data[0] = 1
        struct.pack_into('<f', data, 24, 2.5)
        self.state.write('settings', data)
        self.view.demo({'action': 'sensors', 'temperature': 22, 'ambient': 3500})
        self.assertEqual(self.view.snapshot()['brightness'], 255)
        self.assertEqual(self.view.snapshot()['temperature'], 19.5)
        with self.assertRaises(ValueError):
            self.view.demo({'action': 'sensors', 'temperature': float('nan'), 'ambient': 0})

    def test_riding_frames_units_and_disconnected_stale_navigation(self):
        self.state.write('time', bytes([14, 45, 0]))
        self.state.write('ride', json.dumps(dict(v=1, type='units', imperial=True, fahrenheit=True, twelve=True, large=True)).encode())
        self.assertEqual(self.view.snapshot()['clock'], '2:45 PM')
        self.assertEqual(self.view.snapshot()['temperature'], 71.6)
        frame = dict(v=1, type='nav', active=True, stale=False, instruction='Turn left', direction='left', distance='300 ft', arrival='', source='Google Maps')
        with patch('display.time.monotonic', return_value=100):
            self.view.connected = True
            self.state.write('ride', json.dumps(frame).encode())
            self.assertFalse(self.view.snapshot()['navigation']['stale'])
        with patch('display.time.monotonic', return_value=116):
            self.assertTrue(self.view.snapshot()['navigation']['stale'])
        self.view.connected = False
        self.assertTrue(self.view.snapshot()['navigation']['stale'])

    def test_invalid_ride_frame_does_not_replace_last_valid_frame(self):
        from bumble.att import ATT_Error
        valid = json.dumps(dict(v=1, type='trip', active=True, distance='1 mi', duration='00:12', gps='GPS tracking')).encode()
        self.state.write('ride', valid)
        for invalid in (b'not json', b'{"v":2}', b'x' * 471, b'{"v":1,"type":"units","imperial":1}'):
            with self.assertRaises(ATT_Error):
                self.state.write('ride', invalid)
            self.assertEqual(self.state.latest['ride'], valid)


class ServerTests(unittest.IsolatedAsyncioTestCase):
    async def test_routes_and_rejected_cross_origin_control(self):
        with tempfile.TemporaryDirectory() as directory:
            state = EspState(Path(directory) / 'state.json')
            async with await serve_display(state.display):
                async def request(raw):
                    reader, writer = await asyncio.open_connection('127.0.0.1', 8876)
                    writer.write(raw)
                    await writer.drain()
                    response = await reader.read()
                    writer.close()
                    await writer.wait_closed()
                    return response
                result = await request(b'GET /state HTTP/1.1\r\nHost: 127.0.0.1:8876\r\n\r\n')
                self.assertEqual(json.loads(result.split(b'\r\n\r\n', 1)[1])['layout'], 0)
                result = await request(b'GET /bonds.json HTTP/1.1\r\nHost: 127.0.0.1:8876\r\n\r\n')
                self.assertTrue(result.startswith(b'HTTP/1.1 404'))
                result = await request(b'POST /demo HTTP/1.1\r\nHost: 127.0.0.1:8876\r\nOrigin: http://example.com\r\nContent-Type: application/json\r\nContent-Length: 17\r\n\r\n{"action":"live"}')
                self.assertTrue(result.startswith(b'HTTP/1.1 400'))


if __name__ == '__main__':
    unittest.main()
