import struct
import tempfile
import unittest
from pathlib import Path
from esp_simulator import EspState, UUIDS

class I2cTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.state = EspState(Path(self.temp.name) / 'state.json')
        self.view = self.state.display
    def tearDown(self): self.temp.cleanup()
    def devices(self, **changes):
        data=dict(action='i2c',bme280=True,ds3231=True,stale=False,humidity=62,pressure=998.4)
        data.update(changes); self.view.demo(data)
    def test_environment_matches_android_wire_format(self):
        self.assertEqual(UUIDS['environment'],'cac36b81-1245-4f86-a437-001dc1b86a02')
        self.devices()
        frame=self.state.read('environment')
        self.assertEqual(len(frame),20)
        v,flags,seq,t,h,p,age=struct.unpack('<BBHfffI',frame)
        self.assertEqual((v,flags),(1,3));self.assertEqual(h,62);self.assertAlmostEqual(p,998.4,places=3);self.assertLessEqual(age,5000)
    def test_disconnect_stale_and_recovery(self):
        for options in [dict(bme280=False),dict(stale=True)]:
            self.devices(**options)
            self.assertEqual(self.state.read('environment')[1],2)
            self.assertIsNone(self.view.snapshot()['temperature'])
        self.devices();self.assertEqual(self.state.read('environment')[1],3)
        self.assertIsNotNone(self.view.snapshot()['temperature'])
    def test_rtc_fault_keeps_phone_time_when_available(self):
        self.devices(ds3231=False)
        self.assertEqual(self.view.snapshot()['clock'],'--:--')
        self.view.on_write('time',bytes([14,30,0]))
        self.assertEqual(self.view.snapshot()['clock'],'14:30')
    def test_invalid_controls_do_not_partially_mutate_state(self):
        before=self.view.snapshot()['i2c']
        for value in [float('nan'),-1,101]:
            with self.assertRaises(ValueError):self.devices(bme280=False,humidity=value)
            self.assertEqual(self.view.snapshot()['i2c'],before)
        with self.assertRaises(ValueError):self.devices(bme280='false')

if __name__=='__main__':unittest.main()
