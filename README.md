In the name of God, the Most Gracious, the Most Merciful

# Herz

Factory security Android app (cameras, doors, alarms). The Gradle project lives in [`android/`](android/). Design and build notes are in [`docs/`](docs/), including [`docs/android-dev-setup.md`](docs/android-dev-setup.md) and [`docs/setup-progress.md`](docs/setup-progress.md).

## How to run the emulator in Cursor

You need the Android SDK emulator (from [Android Studio](https://developer.android.com/studio)), an AVD in Device Manager, and the **Android iOS Emulator** extension in Cursor (`DiemasMichiels.emulate`, id `diemasmichiels.emulate`). This repo recommends that extension in [`.vscode/extensions.json`](.vscode/extensions.json).

### One-time setup

1. Set user environment variables (new terminals only after this):
   - `ANDROID_HOME` = `%LOCALAPPDATA%\Android\Sdk`
   - Add `%ANDROID_HOME%\platform-tools` and `%ANDROID_HOME%\emulator` to `PATH`.
2. In Cursor, set the emulator folder for Windows:
   - **Settings → Extensions → Emulator Configuration**
   - `emulator.emulatorPathWindows` = `C:\Users\<yourUsername>\AppData\Local\Android\Sdk\emulator`  
     (folder that contains `emulator.exe`, not the `.exe` itself)
3. Create an AVD in **Android Studio → Tools → Device Manager**. Prefer a **Google Play** or **Google APIs** system image (API 34 or 35) for FCM later. On this machine an example AVD name is `Medium_Phone_API_37.0`.

### Start the emulator from Cursor

1. Reload Cursor after changing settings (**Developer: Reload Window**).
2. Press **Ctrl+Alt+E**, or open the Command Palette and run **Emulator**.
3. Choose your AVD from the list.

### Start the emulator from the terminal

From any terminal where `ANDROID_HOME` and `PATH` are set:

```powershell
emulator -list-avds
emulator -avd Medium_Phone_API_37.0
```

Replace the AVD name with yours from `-list-avds`.

### Install and run Herz on the emulator

With the emulator booted:

```powershell
cd android
.\gradlew installDebug
adb shell am start -n com.raghim.herz/com.raghim.herz.MainActivity
```

### If something fails

- **No devices in the extension list:** Open Device Manager in Android Studio and confirm the AVD starts with the Play button there first.
- **`avdmanager` / Java errors in a plain terminal:** Use Android Studio’s bundled JDK for SDK tools, or install JDK 17+ and point `JAVA_HOME` at it.
- **Compose previews and Layout Inspector:** Still use Android Studio; Cursor is mainly for code, Gradle, and terminal workflows. See [`docs/android-dev-setup.md`](docs/android-dev-setup.md).

### Cursor shows “Error running your Android emulator!”

The **Android iOS Emulator** extension runs the same command as the terminal, then treats a quick exit as failure. The path is usually fine; read the real error in a terminal:

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\emulator\emulator.exe" -avd Medium_Phone_API_37.0
& "$env:LOCALAPPDATA\Android\Sdk\emulator\emulator.exe" -accel-check
```

On this machine the typical failure is:

```text
ERROR | x86_64 emulation currently requires hardware acceleration!
CPU acceleration status: Android Emulator hypervisor driver is not installed on this machine
```

Fix it in order:

1. **Turn on CPU virtualization in BIOS/UEFI** (required for x86_64 AVDs).  
   In PowerShell, `systeminfo` should show **Virtualization Enabled In Firmware: Yes**.  
   If it says **No**, reboot into firmware setup (often F2/Del/Esc on boot) and enable **Intel Virtualization Technology (VT-x)** or **AMD-V**, save, and reboot.

2. **Install the Android hypervisor driver** (after step 1):  
   Android Studio → **Settings → Languages & Frameworks → Android SDK → SDK Tools** → enable **Android Emulator Hypervisor Driver (installer)** → **Apply**.  
   Then run **as Administrator**:

   ```powershell
   & "$env:LOCALAPPDATA\Android\Sdk\extras\google\Android_Emulator_Hypervisor_Driver\silent_install.bat"
   ```

   Alternatively, enable **Windows Hypervisor Platform** in **Turn Windows features on or off**, reboot, and run `-accel-check` again.

3. **Low RAM (about 8 GB):** The emulator may warn that 16 GB is recommended. Close other apps, or in Device Manager edit the AVD and reduce **RAM** (for example 2048 MB). Your GPU may fall back to software rendering (SwiftShader); that is slower but often still usable.

4. **Verify:** `emulator -accel-check` should no longer report that the hypervisor driver is missing. Start the AVD from Android Studio’s Device Manager once, then use **Ctrl+Alt+E** in Cursor again.

If virtualization cannot be enabled (some locked-down PCs), use a **physical phone** with USB debugging and `.\gradlew installDebug` instead of the emulator.
