# Build and install the Android app

## Requirements

- Android Studio with Android SDK **37**, platform-tools, emulator, and a compatible SDK build-tools installation.
- A **JDK 21** Gradle runtime. The committed daemon criteria select JetBrains JDK 21; Android source targets Java/Kotlin 17. Let Android Studio configure the Gradle JDK or set `JAVA_HOME` to a JDK 21 installation.
- The committed Gradle wrapper (9.6.1), Android Gradle Plugin 9.3.1 and Kotlin 2.4.10. Use the committed versions; first sync/build downloads dependencies.
- Android **12/API 31 or newer** for the app. Emulator instructions use API 37 Google APIs x86_64 on Windows with virtualization enabled.

Open the **Android** directory in Android Studio, not the repository root or upstream `Software` directory. Allow SDK/JDK setup and Gradle sync. Studio creates your private `local.properties`; it must not be committed. For command-line builds, set `ANDROID_HOME` to your SDK path or create `local.properties` with your own `sdk.dir`.

## Build and check

From `Android/`:

```powershell
.\gradlew.bat assembleCompanionDebug assemblePhoneDebug testCompanionDebugUnitTest testPhoneDebugUnitTest lintCompanionDebug lintPhoneDebug
```

APKs are `app/build/outputs/apk/companion/debug/app-companion-debug.apk` and `app/build/outputs/apk/phone/debug/app-phone-debug.apk`. Debug builds are for development and use your local debug signing key. Release signing credentials are not included. Do not distribute an upstream APK as this fork's current build.

Choose `phoneDebug` in Android Studio for **RykerConnect Phone**, or `companionDebug` for the full ESP edition. The phone package ends in `.phone` and can coexist with the companion. It provides manual GPS recording, the journal/photos, garage, backups and optional DadRides publishing without notification-reading, phone-state or Bluetooth access. Its setup asks for precise location when recording starts, with optional posting permission for its own recording/reminder notifications. The editions store data separately; use reviewed backup/restore to move rides/photos, and enter the publishing key again.

To install on the regular simulator (from the repository root):

```powershell
$adb = Join-Path $env:LOCALAPPDATA 'Android/Sdk/platform-tools/adb.exe'
& $adb -s emulator-5554 install -r Android/app/build/outputs/apk/phone/debug/app-phone-debug.apk
```

Select the intended device explicitly when multiple emulators or phones are connected. A different signing key can prevent upgrading an installed APK. Back up data before making any decision to uninstall; uninstalling erases app data.

## Permissions and operation

Nearby devices/Bluetooth permissions support the main-unit connection. Location permission is needed for real GPS ride recording and location features. Notification access supports forwarded phone/media information; notification permission controls app reminders. Grant only the features you intend to exercise. Synthetic guided demos do not require real GPS recording or hardware pairing.

The companion's five destinations are Dashboard, Connect, My Trips, My Garage and Settings. Phone omits Connect and starts on a manual recording dashboard. The optional publishing settings are under Settings → Add-ons in both editions. Leave them disabled unless you have deployed your own DadRides site.

## Test boundaries

The flavor-specific unit-test and lint tasks do not connect to or erase an emulator. Instrumentation tests require a dedicated test emulator. Avoid `connectedPhoneDebugAndroidTest` or `connectedCompanionDebugAndroidTest` on an emulator with data you want to keep: their install/uninstall flow can erase working app data. Existing integration tests include isolated storage, but the Gradle runner's lifecycle is separate from that isolation. The phone-only `PhoneEditionTest` checks the installed permissions/services, manual start/save flow, and access to publishing/backup tools; its recording test creates an emulator-only trip.

Real phone background restrictions, Samsung fold behavior, road GPS accuracy, notification compatibility and physical BLE/intercom behavior still require device testing. The in-app loopback OLED preview is a debug-only simulator feature, not a physical screen capture.
