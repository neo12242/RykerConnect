"""RykerConnect BLE peripheral for Android netsim; no physical hardware access."""
import asyncio
import json
import logging
import struct
import zlib
from pathlib import Path

from bumble.att import ATT_Error, ATT_INVALID_ATTRIBUTE_LENGTH_ERROR, ATT_REQUEST_NOT_SUPPORTED_ERROR
from bumble.core import AdvertisingData, UUID
from bumble.device import Device, DeviceConfiguration
from bumble.gatt import Characteristic, CharacteristicValue, Descriptor, Service
from bumble.hci import Address, OwnAddressType
from bumble.pairing import PairingConfig, PairingDelegate
from bumble.transport import open_transport
from display import DisplayModel, serve_display, URL

ROOT = Path(__file__).resolve().parent
LOG = logging.getLogger('ryker-simulator')
SERVICE = 'db7ba582-229a-4b96-9000-cf0f69f86f73'
UUIDS = {
    'environment': 'cac36b81-1245-4f86-a437-001dc1b86a02',
    'notification': '755cf5b1-ded3-4c7b-a6fc-8c5ce2f99fdb',
    'media': 'dcadc0d8-24ed-40ed-952b-5d1c872a69aa',
    'phone_battery': '1f74ccf5-376a-40b6-ab60-7b1c5efbf652',
    'intercom_battery': '85546838-6ae5-45cb-aa2f-4c8af50d17d4',
    'network': '49c7fba8-9ba7-474b-b8a5-a5431e057e23',
    'time': 'a41dcc81-d45e-4445-99bb-38c37c1ef1c8',
    'reset': '18cb54fe-45e8-4819-a262-24b731c8b236',
    'update': '1d1306c5-98d9-4998-8dfd-35136295575f',
    'brightness': '7bc28f30-10bc-46e2-b84b-96e0545c2f5c',
    'settings': '05f7c3e4-daac-4953-8c71-20eacdf0c7a1',
    'reinit': '3a6e4b2c-8f71-4d09-b5a3-c7e2f1d08a94',
    'firmware': 'fb2385da-5290-4513-bb0c-6d0b21de619a',
    'hardware': '3ae9aece-1b67-4281-a53b-748adf23f484',
    'volume': 'c4e83b7d-5a12-4f8e-b9d6-3e7f1c2a4b8d',
    'screen': '62dbb02d-4a3a-452e-b753-02bcb2272b9d',
    'ride': 'fad31d20-7b59-4d3b-a896-c3d0cb9e21a1',
}


def defaults():
    data = bytearray(38)
    struct.pack_into('<BBHHBB4BbIBBBIf', data, 0,
                     0, 128, 200, 3500, 0, 0, 1, 1, 0, 0, 0,
                     30000, 20, 20, 5, 10000, 0.0)
    return data


class EspState:
    def __init__(self, path):
        self.path = path
        self.settings = defaults()
        self.volume = 50
        self.latest = {}
        if path.exists():
            saved = json.loads(path.read_text())
            data = bytes.fromhex(saved['settings'])
            if len(data) != 38:
                raise ValueError('Saved settings must contain 38 bytes')
            self.settings = bytearray(data)
            self.volume = saved['volume']
        self.display = DisplayModel(self)

    def save(self):
        temporary = self.path.with_suffix('.tmp')
        temporary.write_text(json.dumps({'simulated': True, 'settings': self.settings.hex(),
                                         'volume': self.volume}, indent=2))
        temporary.replace(self.path)

    def read(self, name):
        LOG.info('READ %s', name)
        if name == 'environment':
            return self.display.environment_frame()
        if name == 'settings':
            return bytes(self.settings) + struct.pack('<I', zlib.crc32(self.settings))
        if name == 'firmware':
            return struct.pack('<H', 7)
        if name == 'hardware':
            return b'ESP32S3-REV01'
        if name == 'brightness':
            return bytes([self.settings[1]])
        if name == 'screen':
            return bytes([self.settings[6]])
        return self.latest.get(name, b'\x00\x00\x00')

    def write(self, name, data):
        # Never log notification/media content or firmware Wi-Fi credentials.
        LOG.info('WRITE %s (%d bytes)', name, len(data))
        sizes = {'settings': 38, 'brightness': 1, 'screen': 1, 'volume': 1,
                 'time': 3, 'network': 2, 'phone_battery': 2, 'intercom_battery': 1,
                 'reinit': 1}
        if name in sizes and len(data) != sizes[name]:
            raise ATT_Error(ATT_INVALID_ATTRIBUTE_LENGTH_ERROR)
        if name == 'media' and len(data) < 5:
            raise ATT_Error(ATT_INVALID_ATTRIBUTE_LENGTH_ERROR)
        if name in ('update', 'reset'):
            LOG.warning('%s is intentionally unsupported by the simulator', name)
            raise ATT_Error(ATT_REQUEST_NOT_SUPPORTED_ERROR)
        if name == 'ride':
            self.display.validate_ride(data)
        if name == 'settings':
            self.settings[:] = data
        elif name == 'brightness':
            self.settings[1] = data[0]
        elif name == 'screen':
            self.settings[6] = min(data[0], 2)
        elif name == 'volume':
            self.volume = min(data[0], 100)
        else:
            self.latest[name] = bytes(data)
        if name in ('settings', 'brightness', 'screen', 'volume'):
            self.save()
            LOG.info('STATE brightness=%d screen=%d volume=%d',
                     self.settings[1], self.settings[6], self.volume)
        self.display.on_write(name, data)


class DisplayPin(PairingDelegate):
    def __init__(self, display):
        super().__init__(PairingDelegate.DISPLAY_OUTPUT_ONLY)
        self.display = display

    async def generate_passkey(self):
        return 123456  # Public test PIN for this virtual peripheral only.

    async def display_number(self, number, digits):
        self.display.pairing = True
        LOG.info('Virtual ESP pairing PIN: %06d', number)


async def main():
    state = EspState(ROOT / 'state.json')
    config = DeviceConfiguration()
    config.name = 'RykerConnect-MainUnit'
    config.address = Address('F0:F1:F2:F3:F4:F5')
    config.keystore = 'JsonKeyStore:' + str(ROOT / 'bonds.json')
    async with await serve_display(state.display), await open_transport('android-netsim:name=RykerConnect-ESP-Simulator') as transport:
        device = Device.from_config_with_hci(config, transport.source, transport.sink)
        device.pairing_config_factory = lambda connection: PairingConfig(
            sc=True, mitm=True, bonding=True, delegate=DisplayPin(state.display),
            identity_address_type=PairingConfig.AddressType.PUBLIC)
        readable = {'settings', 'brightness', 'screen', 'firmware', 'hardware', 'time', 'environment'}
        protected = {'settings', 'notification', 'update', 'reset', 'ride', 'environment'}
        chars = []
        for name, uuid in UUIDS.items():
            props = Characteristic.Properties(0)
            perms = Characteristic.Permissions(0)
            if name in readable:
                props |= Characteristic.Properties.READ
                perms |= Characteristic.READABLE
            if name not in ('firmware', 'hardware', 'environment'):
                props |= Characteristic.Properties.WRITE
                perms |= Characteristic.WRITEABLE
            if name in ('volume', 'media'):
                props |= Characteristic.Properties.WRITE_WITHOUT_RESPONSE
            if name in protected:
                perms |= Characteristic.WRITE_REQUIRES_ENCRYPTION | Characteristic.WRITE_REQUIRES_AUTHENTICATION
                if name in readable:
                    perms |= Characteristic.READ_REQUIRES_ENCRYPTION | Characteristic.READ_REQUIRES_AUTHENTICATION
            chars.append(Characteristic(uuid, props, perms, CharacteristicValue(
                read=lambda connection, n=name: state.read(n),
                write=lambda connection, value, n=name: state.write(n, value))))
        device.add_service(Service(SERVICE, chars))
        # The firmware advertises a one-button gamepad as well as its custom service.
        # Android uses the HID profile to maintain its connection to the peripheral.
        P = Characteristic.Properties
        report_map = bytes.fromhex('05010905a101850105091901290115002501950175018102950175078101c0')
        device.add_service(Service('1812', [
            Characteristic('2A4A', P.READ, Characteristic.READABLE, bytes.fromhex('11010002')),
            Characteristic('2A4B', P.READ, Characteristic.READABLE, report_map),
            Characteristic('2A4C', P.WRITE_WITHOUT_RESPONSE, Characteristic.WRITEABLE, b'\x00'),
            Characteristic('2A4E', P.READ | P.WRITE_WITHOUT_RESPONSE,
                           Characteristic.READABLE | Characteristic.WRITEABLE, b'\x01'),
            Characteristic('2A4D', P.READ | P.NOTIFY, Characteristic.READABLE, b'\x00',
                           [Descriptor('2908', Descriptor.READABLE, b'\x01\x01')]),
        ]))
        device.add_service(Service('180A', [
            Characteristic('2A29', P.READ, Characteristic.READABLE, b'RykerConnect Simulator'),
            Characteristic('2A50', P.READ, Characteristic.READABLE, bytes.fromhex('01000000010700')),
        ]))
        device.add_service(Service('180F', [
            Characteristic('2A19', P.READ | P.NOTIFY, Characteristic.READABLE, b'\x64'),
        ]))
        device.advertising_data = bytes(AdvertisingData([
            (AdvertisingData.FLAGS, b'\x06'),
            (AdvertisingData.COMPLETE_LIST_OF_128_BIT_SERVICE_CLASS_UUIDS, bytes(UUID(SERVICE))),
            (AdvertisingData.COMPLETE_LIST_OF_16_BIT_SERVICE_CLASS_UUIDS, b'\x12\x18'),
            (AdvertisingData.APPEARANCE, b'\x80\x01'),
        ]))
        device.scan_response_data = bytes(AdvertisingData([
            (AdvertisingData.COMPLETE_LOCAL_NAME, config.name.encode()),
        ]))

        @device.on('connection')
        def connected(connection):
            LOG.info('CONNECTED %s', connection.peer_address)
            state.display.connected = True
            def disconnected(reason):
                state.display.connected = False
                state.display.pairing = False
                LOG.info('DISCONNECTED %s', reason)
            def paired(*args):
                state.display.pairing = False
                LOG.info('PAIRING SUCCESS')
            connection.on('disconnection', disconnected)
            connection.on('pairing', paired)

        await device.power_on()
        await device.start_advertising(auto_restart=True, own_address_type=OwnAddressType.PUBLIC)
        LOG.info('Public Bluetooth address: %s', device.public_address)
        LOG.info('SIMULATED ESP advertising as %s; pairing PIN 123456', config.name)
        LOG.info('OLED display available at %s', URL)
        await transport.source.terminated


if __name__ == '__main__':
    logging.basicConfig(level=logging.WARNING, format='%(asctime)s %(levelname)s %(message)s',
                        handlers=[logging.StreamHandler(), logging.FileHandler(ROOT / 'simulator.log')])
    LOG.setLevel(logging.INFO)
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        pass
