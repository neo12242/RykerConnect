import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch
from esp_simulator import EspState

class GuidedTest(unittest.TestCase):
    def test_progress_and_live_restore_are_isolated(self):
        with tempfile.TemporaryDirectory() as folder:
            state=EspState(Path(folder)/'state.json');view=state.display
            before=(view.temperature,view.humidity,view.bme_connected,view.connected,dict(view.media))
            with patch('display.time.monotonic',return_value=1000):view.demo(dict(action='scenario',name='guided'))
            with patch('display.time.monotonic',return_value=1000):a=view.snapshot()
            with patch('display.time.monotonic',return_value=1090):b=view.snapshot()
            with patch('display.time.monotonic',return_value=1180):c=view.snapshot()
            self.assertEqual(a['guidedProgress'],0);self.assertEqual(b['guidedProgress'],50);self.assertEqual(c['guidedProgress'],100)
            self.assertNotEqual(a['media']['title'],b['media']['title'])
            self.assertEqual(c['navigation']['instruction'],'Arrived')
            self.assertFalse(c['media']['playing'])
            self.assertEqual(before,(view.temperature,view.humidity,view.bme_connected,view.connected,dict(view.media)))
            view.demo(dict(action='scenario',name='live'))
            self.assertNotIn('guidedProgress',view.snapshot())
