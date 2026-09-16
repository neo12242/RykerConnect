import struct
import tempfile
import unittest
from pathlib import Path

from bumble.att import ATT_Error
from esp_simulator import EspState


class ProtocolTests(unittest.TestCase):
    def test_settings_round_trip_and_restart(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'state.json'
            state = EspState(path)
            data = bytearray(state.read('settings')[:38])
            self.assertEqual(len(state.read('settings')), 42)
            self.assertEqual(struct.unpack_from('<I', data, 13)[0], 30000)
            data[1] = 201
            data[6] = 2
            state.write('settings', bytes(data))
            self.assertEqual(state.read('settings')[:38], data)
            restored = EspState(path)
            self.assertEqual(restored.read('brightness'), bytes([201]))
            self.assertEqual(restored.read('screen'), b'\x02')

    def test_rejected_writes_do_not_mutate_settings(self):
        with tempfile.TemporaryDirectory() as directory:
            state = EspState(Path(directory) / 'state.json')
            original = state.read('settings')
            for name, payload in [('settings', b'bad'), ('brightness', b''),
                                  ('update', b'ignored'), ('reset', b'ignored')]:
                with self.assertRaises(ATT_Error):
                    state.write(name, payload)
            self.assertEqual(state.read('settings'), original)


if __name__ == '__main__':
    unittest.main()
