"""Display state and a loopback-only preview server, sharing the BLE event loop."""
import asyncio
import json
import math
import struct
import time
from pathlib import Path

URL = 'http://127.0.0.1:8876'
WEB = Path(__file__).parent / 'web'


class DisplayModel:
    def __init__(self, state):
        self.state = state
        self.connected = False
        self.pairing = False
        self.started = time.monotonic()
        self.clock = None
        self.media = self.decode_media(b'\0' * 5)
        self.demo_media = None
        self.temperature = 22.0
        self.ambient = 2000
        self.bme_connected = True
        self.rtc_connected = True
        self.sensor_stale = False
        self.humidity = 48.0
        self.pressure = 1013.2
        self.notification = None
        self.volume_popup = None
        self.low_battery = None
        self.low_latches = {'phone_battery': False, 'intercom_battery': False}
        self.last_write = None
        self.reinit_until = 0
        self.units = dict(imperial=False, fahrenheit=False, twelve=False, large=False)
        self.navigation = None
        self.trip = None
        self.weather = None
        self.scenario = "live"
        self.guided_started = None

    def environment_frame(self):
        now = time.monotonic()
        valid = self.bme_connected and not self.sensor_stale
        temperature = self.temperature - struct.unpack_from('<f', self.state.settings, 24)[0]
        valid = valid and math.isfinite(temperature) and -40 <= temperature <= 85
        age = 20000 if self.sensor_stale else int(now % 5 * 1000)
        return struct.pack('<BBHfffI', 1, 3 if valid else 2, int(now // 5) & 65535,
                           temperature if valid else 0, self.humidity if valid else 0,
                           self.pressure if valid else 0, age if self.bme_connected else 0xffffffff)

    @staticmethod
    def validate_ride(data):
        from bumble.att import ATT_Error, ATT_INVALID_ATTRIBUTE_LENGTH_ERROR
        try:
            if len(data) > 470:
                raise ValueError('Frame too large')
            frame = json.loads(data.decode('utf-8'))
            if not isinstance(frame, dict) or frame.get('v') != 1:
                raise ValueError('Unsupported version')
            kind = frame.get('type')
            fields = {'units': ['imperial', 'fahrenheit', 'twelve', 'large'], 'nav': ['active', 'stale'], 'trip': ['active'], 'weather': ['active', 'stale', 'available']}
            if kind not in fields or any(type(frame.get(k)) is not bool for k in fields[kind]):
                raise ValueError('Invalid fields')
            if kind == 'units' and 'musicLeft' in frame and type(frame['musicLeft']) is not bool:
                raise ValueError('Invalid screen order')
            if kind == 'units' and frame.get('drivingField', 'distance_time') not in ('distance_time', 'distance', 'time', 'gps'):
                raise ValueError('Invalid driving field')
            strings = {'units': [], 'nav': ['instruction', 'direction', 'distance', 'arrival', 'source'], 'trip': ['distance', 'duration', 'gps'], 'weather': ['temperature', 'conditions', 'wind', 'rain', 'status']}
            if any(not isinstance(frame.get(k), str) or len(frame[k]) > 240 for k in strings[kind]):
                raise ValueError('Invalid text')
            if kind == 'nav' and frame['direction'] not in ('', 'left', 'right', 'straight', 'uturn', 'roundabout', 'arrive'):
                raise ValueError('Invalid maneuver')
            return frame
        except (ValueError, TypeError, UnicodeError):
            raise ATT_Error(ATT_INVALID_ATTRIBUTE_LENGTH_ERROR)

    @staticmethod
    def decode_media(data):
        playing, position, duration = struct.unpack_from('<BHH', data)
        title, _, artist = data[5:].decode('utf-8', errors='replace').partition('\x03')
        return dict(playing=bool(playing), position=position, duration=duration,
                    title=title.strip(), artist=artist.strip(), received=time.monotonic())

    def on_write(self, name, data):
        now = time.monotonic()
        self.last_write = {'name': name, 'at': now}
        if name == 'ride':
            frame = self.validate_ride(data)
            if frame['type'] == 'units':
                self.units = frame
            elif frame['type'] == 'nav':
                if frame['active'] and not (self.navigation and self.navigation['active']):
                    self.notification = None
                self.navigation = dict(frame, received=now)
            elif frame['type'] == 'weather':
                self.weather = dict(frame, received=now)
            else:
                self.trip = dict(frame, received=now)
        elif name == 'time' and data[0] < 24 and data[1] < 60 and data[2] < 60:
            self.clock = (data[0] * 3600 + data[1] * 60 + data[2], now)
        elif name == 'media':
            self.media = self.decode_media(data)
        elif name == 'notification':
            parts = data.decode('utf-8', errors='replace').split('\x03', 2)
            parts += [''] * (3 - len(parts))
            self.notification = dict(app=parts[0], title=parts[1], text=parts[2],
                                     until=now + struct.unpack_from('<I', self.state.settings, 20)[0] / 1000,
                                     source='Bluetooth')
        elif name == 'volume':
            self.volume_popup = dict(level=self.state.volume, until=now + 3, source='Bluetooth')
        elif name in self.low_latches:
            threshold = self.state.settings[17 if name == 'phone_battery' else 18]
            low = 0 < data[0] <= threshold
            if low and not self.low_latches[name]:
                label = 'Phone' if name == 'phone_battery' else 'Intercom'
                self.low_battery = dict(text=f'{label}: {data[0]}%',
                                        until=now + self.state.settings[19], source='Bluetooth')
            self.low_latches[name] = low
        elif name == 'reinit':
            self.reinit_until = now + 0.3

    def snapshot(self):
        now = time.monotonic()
        s = self.state.settings
        media = dict(self.demo_media if self.demo_media is not None else self.media)
        if media['playing']:
            media['position'] += int(now - media['received'])
        if media['duration']:
            media['position'] = min(media['duration'], media['position'])
        media.pop('received')
        media['source'] = 'Sample media' if self.demo_media is not None else 'Bluetooth'
        seconds = ((self.clock[0] + int(now - self.clock[1])) % 86400 if self.clock
                   else time.localtime().tm_hour * 3600 + time.localtime().tm_min * 60 + time.localtime().tm_sec)
        low, high = struct.unpack_from('<HH', s, 2)
        brightness = s[1]
        if s[0] and high > low:
            brightness = round(25 + 230 * max(0, min(1, (self.ambient - low) / (high - low))))
        phone = self.state.latest.get('phone_battery', b'\xff\0')
        intercom = self.state.latest.get('intercom_battery', b'\xff')
        net = self.state.latest.get('network', b'\0\0')
        def active(value):
            return value if value and now < value['until'] else None
        clock = f'{seconds // 3600:02}:{seconds // 60 % 60:02}'
        if self.units['twelve']:
            clock = f'{seconds // 3600 % 12 or 12}:{seconds // 60 % 60:02}' + (' PM' if seconds // 3600 >= 12 else ' AM')
        temperature = self.temperature - struct.unpack_from('<f', s, 24)[0]
        if self.units['fahrenheit']:
            temperature = temperature * 9 / 5 + 32
        navigation = dict(self.navigation) if self.navigation else None
        if navigation:
            navigation['stale'] = navigation['stale'] or now - navigation['received'] > 15 or not self.connected
        trip = dict(self.trip) if self.trip else None
        if trip and (now - trip['received'] > 15 or not self.connected):
            trip['gps'] = 'Trip data stale'
        weather = dict(self.weather) if self.weather else None
        if weather:
            weather['stale'] = weather['stale'] or now - weather['received'] > 15 or not self.connected
        result = dict(
            units=self.units, navigation=navigation, trip=trip, weather=weather,
            temperatureUnit='F' if self.units['fahrenheit'] else 'C',
            connected=self.connected, pairing=self.pairing,
            layout=min(s[6], 2), brightness=brightness, adaptive=bool(s[0]),
            clock=clock if self.clock or self.rtc_connected else "--:--",
            clockSource='Bluetooth clock' if self.clock else ('Simulated DS3231 clock' if self.rtc_connected else 'RTC disconnected; waiting for phone time'),
            temperature=round(temperature, 1) if self.bme_connected and not self.sensor_stale else None,
            i2c=dict(bme280=self.bme_connected, ds3231=self.rtc_connected, stale=self.sensor_stale, humidity=self.humidity, pressure=self.pressure, simulated=True),
            sensorTemperature=self.temperature, ambient=self.ambient,
            phoneBattery=phone[0] if phone[0] <= 100 else None, charging=bool(phone[1]),
            intercomBattery=intercom[0] if intercom[0] <= 100 else None,
            batteryIcons=list(s[8:12]), batteryFirst=s[12],
            batteryInterval=struct.unpack_from('<I', s, 13)[0] / 1000,
            elapsed=now - self.started, networkSignal=min(net[0], 5),
            networkType=['!', 'G', 'E', '3G', 'H', 'H+', '4G', '4G+', '5G', '5G+'][net[1]] if net[1] < 10 else '',
            media=media, notification=active(self.notification), volume=active(self.volume_popup),
            lowBattery=active(self.low_battery), reinitializing=now < self.reinit_until,
            lastWrite=self.last_write['name'] if self.last_write else None,
            lastWriteAge=round(now - self.last_write['at'], 1) if self.last_write else None)

        result['scenario'] = self.scenario
        if self.scenario != 'live':
            result['units'] = dict(self.units, musicLeft=self.units.get('musicLeft', True))
            result['media'] = dict(title='Sample road trip soundtrack', artist='Simulator artist', playing=True, position=42, duration=210, source='Sample scenario')
            result['trip'] = dict(active=True, distance='12.4 mi' if self.units['imperial'] else '20.0 km', duration='0:32:10', gps='Sample GPS')
            result['navigation'] = dict(active=True, stale=False, instruction='Turn right onto Sample Road', direction='right', distance='300 ft' if self.units['imperial'] else '90 m', arrival='Sample ETA 10 min', source='Sample route')
            result['weather'] = dict(active=True, stale=False, available=True, temperature='50°F' if self.units['fahrenheit'] else '10°C', conditions='Clear', wind='8 mph' if self.units['imperial'] else '13 km/h', rain='10%', status='Sample weather')
            if self.scenario == 'rain': result['weather'].update(conditions='Rain', rain='90%')
            if self.scenario == 'gps_loss': result['trip']['gps'] = 'GPS unavailable'; result['weather']['stale'] = True
            if self.scenario == 'stale': result['navigation']['stale'] = True; result['weather']['stale'] = True
            if self.scenario == 'disconnected': result['connected'] = False; result['navigation']['stale'] = True; result['weather']['stale'] = True
            if self.scenario == 'low_battery': result['phoneBattery'] = 10; result['lowBattery'] = dict(text='Sample phone: 10%', source='Sample scenario')
            if self.scenario == 'long_text': result['navigation']['instruction'] = 'Turn right onto the extremely long sample intersection and avenue name'; result['media']['title'] = 'A very long sample song title to check scrolling and clipping'
            if self.scenario == 'trip_only': result['navigation']['active'] = False
        if self.scenario == 'guided':
            elapsed = min(180, max(0, now - self.guided_started))
            progress = elapsed / 180
            result['media'].update(title=['Open road', 'Coastal afternoon', 'Homeward bound'][min(2, int(elapsed // 60))], position=int(elapsed % 60), duration=60, playing=elapsed < 180)
            result['trip'].update(distance=f'{progress*3.1:.1f} mi' if self.units['imperial'] else f'{progress*5:.1f} km', duration=f'0:{int(elapsed)//60:02}:{int(elapsed)%60:02}', gps='GUIDED DEMO')
            result['navigation'].update(instruction='Arrived' if elapsed >= 180 else ['Continue straight', 'Turn right at Sample Street', 'Coffee stop', 'Head toward home'][min(3, int(elapsed // 45))], direction='right' if elapsed < 90 else 'straight', distance=f'{max(0, int(300*(1-progress)))} m')
            value = 16 + 2 * math.sin(progress * 6.28)
            result['temperature'] = round(value * 9/5+32 if self.units['fahrenheit'] else value, 1)
            result['guidedProgress'] = round(progress*100)
        return result

    def demo(self, data):
        action = data.get('action')
        def number(key, low, high):
            value = float(data[key])
            if not math.isfinite(value) or not low <= value <= high:
                raise ValueError(f'{key} must be between {low} and {high}')
            return value
        def text(key, fallback):
            value = data.get(key, fallback)
            if not isinstance(value, str) or len(value) > 160:
                raise ValueError(f'Invalid {key}')
            return value
        now = time.monotonic()
        if action == 'scenario':
            value = data.get('name')
            if value not in ('live', 'touring', 'rain', 'gps_loss', 'stale', 'disconnected', 'low_battery', 'long_text', 'trip_only', 'guided'):
                raise ValueError('Unknown scenario')
            self.scenario = value
            self.guided_started = now if value == "guided" else None
        elif action == 'i2c':
            for key in ('bme280', 'ds3231', 'stale'):
                if type(data.get(key)) is not bool:
                    raise ValueError('Device states must be booleans')
            humidity, pressure = number('humidity', 0, 100), number('pressure', 300, 1100)
            self.bme_connected, self.rtc_connected, self.sensor_stale = data['bme280'], data['ds3231'], data['stale']
            self.humidity, self.pressure = humidity, pressure
        elif action == 'sensors':
            temperature = number('temperature', -40, 85)
            ambient = number('ambient', 0, 4095)
            self.temperature, self.ambient = temperature, int(ambient)
        elif action == 'media':
            self.demo_media = dict(title=text('title', 'Northern Lights'), artist=text('artist', 'Demo artist'),
                                   playing=True, position=32, duration=215, received=now)
        elif action == 'pause':
            if self.demo_media is None:
                raise ValueError('Load sample media first')
            if self.demo_media['playing']:
                self.demo_media['position'] += int(now - self.demo_media['received'])
            self.demo_media['playing'] = not self.demo_media['playing']
            self.demo_media['received'] = now
        elif action == 'live':
            self.demo_media = None
        elif action == 'notification':
            self.notification = dict(app='Messages', title='Sample notification',
                                     text='Ready for a ride? Meet at the next stop.',
                                     until=now + struct.unpack_from('<I', self.state.settings, 20)[0] / 1000,
                                     source='Sample notification')
        elif action == 'volume':
            self.volume_popup = dict(level=int(number('level', 0, 100)), until=now + 3, source='Sample volume')
        elif action == 'battery':
            self.low_battery = dict(text='Phone: 15%', until=now + self.state.settings[19], source='Sample warning')
        else:
            raise ValueError('Unknown sample action')


async def serve_display(model):
    async def handle(reader, writer):
        status, content_type, body = 200, 'application/json', b''
        try:
            header = await asyncio.wait_for(reader.readuntil(b'\r\n\r\n'), 5)
            if len(header) > 8192:
                raise ValueError('Headers too large')
            lines = header.decode('latin1').split('\r\n')
            method, target, _ = lines[0].split(' ')
            headers = dict(line.lower().split(':', 1) for line in lines[1:] if ':' in line)
            headers = {k: v.strip() for k, v in headers.items()}
            if headers.get('host') != '127.0.0.1:8876':
                raise ValueError('Use the local preview URL')
            if method == 'GET' and target == '/state':
                body = json.dumps(model.snapshot(), allow_nan=False).encode()
            elif method == 'GET' and target in ('/', '/display.js', '/display.css', '/icons.json'):
                filename = 'index.html' if target == '/' else target[1:]
                content_type = {'/': 'text/html', '/display.js': 'text/javascript',
                                '/display.css': 'text/css', '/icons.json': 'application/json'}[target]
                body = (WEB / filename).read_bytes()
            elif method == 'POST' and target == '/demo':
                if headers.get('origin') != URL or headers.get('content-type') != 'application/json':
                    raise ValueError('Only local JSON controls are accepted')
                length = int(headers.get('content-length', '0'))
                if not 0 < length <= 2048:
                    raise ValueError('Invalid body length')
                data = json.loads(await asyncio.wait_for(reader.readexactly(length), 5))
                if not isinstance(data, dict):
                    raise ValueError('Expected an object')
                model.demo(data)
                body = b'{"ok":true}'
            else:
                status, body = 404, b'{"error":"Not found"}'
        except (ValueError, KeyError, TypeError, UnicodeError, asyncio.TimeoutError,
                asyncio.IncompleteReadError, asyncio.LimitOverrunError):
            status, body = 400, b'{"error":"Invalid preview request"}'
        except Exception:
            import logging
            logging.getLogger('ryker-simulator').exception('Display request failed')
            status, body = 500, b'{"error":"Display unavailable"}'
        try:
            writer.write((f'HTTP/1.1 {status} Response\r\nContent-Type: {content_type}; charset=utf-8\r\n'
                          f'Content-Length: {len(body)}\r\nCache-Control: no-store\r\n'
                          "Content-Security-Policy: default-src 'self'; script-src 'self'; style-src 'self'; connect-src 'self'; frame-ancestors 'none'\r\n"
                          'X-Content-Type-Options: nosniff\r\nConnection: close\r\n\r\n').encode() + body)
            await writer.drain()
        except ConnectionError:
            pass
        finally:
            writer.close()
            await writer.wait_closed()
    return await asyncio.start_server(handle, '127.0.0.1', 8876, limit=8192)
