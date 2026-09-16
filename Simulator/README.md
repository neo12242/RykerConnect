# Simulator setup and use

The simulator provides a virtual RykerConnect BLE main unit through Android Emulator's netsim transport. A Python model renders two 160 × 132 OLED panels in a browser. It does not execute the ESP firmware. No physical ESP, OLED, sensor or Bluetooth adapter is required.

## 1. Prepare Android

Follow [Android setup](../docs/ANDROID.md) and build `Android/app/build/outputs/apk/debug/app-debug.apk`.

In Android Studio → **Device Manager → Create virtual device**:

1. Choose **Pixel 7**.
2. Install/select the **API 37 Google APIs x86_64** system image.
3. Set the AVD name to exactly **RykerConnect_Pixel_7**.
4. Finish setup. Close it if already running on a different port; the launcher reserves `emulator-5554` for this AVD.

Install Android Emulator and Android SDK Platform-Tools through SDK Manager. The Windows launchers default to `%LOCALAPPDATA%\Android\Sdk`; set `ANDROID_HOME` to your SDK if it is elsewhere. Enable hardware virtualization if the emulator reports that acceleration is unavailable.

## 2. Prepare Python

Install Python 3.13. From the repository root:

```powershell
py -3.13 -m venv Simulator/.venv
.\Simulator\.venv\Scripts\python.exe -m pip install -r Simulator/requirements.txt
```

This pins `bumble[android]==0.0.234`. Keep the virtual environment local; do not copy one from another PC.

## 3. Launch and pair

Double-click **Start RykerConnect Demo.cmd**, or run:

```powershell
& '.\Start RykerConnect Demo.cmd'
```

The launcher starts the named AVD on port 5554, waits for boot, starts the Python simulator, configures `adb reverse tcp:8876 tcp:8876`, installs the debug APK if the app is missing, and opens the app and browser preview.

In **Connect**, select **RykerConnect-MainUnit** and enter **123456** if Android requests a PIN. This is an intentionally public test PIN for the virtual device. The **Intercom Device** remains disconnected because no intercom is simulated.

Open <http://127.0.0.1:8876/> for the browser OLED preview. Android's **OLED preview** opens the same loopback page after the launcher establishes port reversal.

The regular launcher does not reinstall an already-installed app. After changing/rebuilding Android source, install the new APK with `adb -s emulator-5554 install -r ...` as described in the Android guide, then rerun the launcher.

## 4. Explore

- Change display settings in Android and watch brightness, layout and units reach the simulator through BLE.
- **Test display** exercises a BLE write and preview response.
- Browser sample controls display labeled example music/notifications. Return to **Live phone data** to remove overlays.
- **Test scenarios** exercise touring, rain, GPS loss, stale data, disconnection, low battery, long text and trip-only presentation. These overlays do not alter recorded rides or disconnect the underlying BLE session.
- **Start / restart guided ride** plays a three-minute synthetic display sequence.
- **I2C test bench** toggles BME280/RTC presence and stale sensor data, temperature, humidity and pressure. Android labels these samples as simulated. The RTC simulation uses DS3231-era controls; it does not validate REV05's PCF8563 driver.
- Android **My Trips → Demo rides** offers read-only synthetic routes, charts and replay without importing them into recorded history.

Maps/weather may use network services; the local simulator server itself listens only on loopback. Fonts, scrolling and OLED contrast are approximations. Firmware update and factory reset return unsupported-operation errors. The simulated version/up-to-date label is not a physical firmware check. Simulator pairing uses authenticated LE Secure Connections; that differs from the original firmware's legacy pairing.

## Optional foldable presentation

Create a second AVD with the **Pixel 10 Pro Fold**, **API 37 Google APIs x86_64**, named **RykerConnect_Foldable_Demo**. Run **Start RykerConnect Foldable Demo.cmd**. It uses `emulator-5556`, installs the current debug APK, and opens **Guided demo**. Tap Start demo; use **Fold Demo.cmd** and **Unfold Demo.cmd** to change posture.

This AVD has separate app data and does not take over the regular emulator's BLE link. Guided demo is synthetic presentation only: no real recording, GPS permission, Bluetooth writes or music playback. Posture IDs are specific to this configured image/profile. It approximates a foldable Android device, not Samsung One UI.

## Stop and troubleshoot

```powershell
powershell.exe -NoProfile -File Simulator/Stop-Simulator.ps1
```

Stopping preserves settings/pairing and leaves the emulator open.

| Symptom | Check / recovery |
|---|---|
| Missing Python environment | Complete step 2; the launcher uses `Simulator/.venv/Scripts/python.exe`. |
| Missing SDK/AVD | Verify SDK Manager, `ANDROID_HOME`, and exact AVD name. Run the SDK emulator with `-list-avds`. |
| Port 5554 belongs to another AVD | Close that emulator normally and rerun; do not wipe its data. |
| Simulator exits | Read local `Simulator/stderr.log` and `simulator.log`; verify Bumble Android extra and an active compatible emulator/netsim. |
| Port 8876 already used | Stop this simulator before tests. Do not terminate unrelated processes; identify the process using the port. |
| Android preview unavailable | Rerun launcher to restore adb reverse; use the debug build. |
| Old app behavior | Rebuild and explicitly install the new debug APK with `install -r`. |
| Pairing no longer works | Try Connect/reselection first. Forget only the virtual RykerConnect device and pair again if needed; preserve unrelated bonds. |
| Map/weather unavailable | Check network/location permissions as appropriate. No location is invented when unavailable. |

`bonds.json` contains private Bluetooth pairing material. `state.json` holds local simulator settings. Both, the virtual environment, and logs are ignored. Do not upload them with bug reports. Logs do not intentionally record notification/media contents or firmware Wi-Fi credentials, but review any report before sharing.

## Automated checks

Stop the ESP simulator first: HTTP tests use port 8876. From `Simulator/`:

```powershell
.\.venv\Scripts\python.exe -m unittest discover -v
```

The tests cover protocol validation, settings persistence, display/scenario behavior, sensor availability and guided-demo isolation. Passing tests are not proof of physical hardware operation.

Reference: [Bumble Android Emulator transport](https://google.github.io/bumble/transports/android_emulator.html).
