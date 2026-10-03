
# Building and Testing the FSEC Android App with Cursor

**Yes, you can.** Cursor's Agent mode can write the Kotlin code and run Gradle builds and tests in the terminal. It can read the errors and fix them, then repeat until the tests pass. A few jobs still work better in Android Studio, so most people keep both open on the same project folder.

| Task                                                           | Best tool                                       |
| -------------------------------------------------------------- | ----------------------------------------------- |
| Writing code, refactoring, generating modules and tests        | **Cursor**                                |
| Running unit tests and builds (`./gradlew ...`)              | **Cursor** (terminal, Agent fixes errors) |
| Emulator, Compose previews, Layout Inspector, debugger         | **Android Studio**                        |
| Gradle sync problems and SDK management                        | **Android Studio**                        |
| Testing alarms on a locked phone, biometrics, 16 video streams | **Real phones**                           |

---

## 1. Setup (one time)

1. **Install Android Studio.** It provides the Android SDK, the emulator and a JDK 17+.
2. **Install Cursor** and open the project folder.
3. **Add a Kotlin extension to Cursor.** Use the official JetBrains Kotlin LSP extension, or the community "Kotlin" extension. This gives you basic autocomplete and error highlighting. It is weaker than Android Studio, which is why the Gradle build is what you really rely on.
4. **Set environment variables** so Cursor's terminal can find the SDK:

   ```bash
   # macOS/Linux (~/.zshrc or ~/.bashrc)
   export ANDROID_HOME=$HOME/Android/Sdk          # macOS: $HOME/Library/Android/sdk
   export PATH=$PATH:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator
   ```

   On Windows, set `ANDROID_HOME` to `%LOCALAPPDATA%\Android\Sdk` and add `platform-tools` and `emulator` to PATH.
5. **Create the project skeleton in Android Studio** using the "Empty Activity (Compose)" template. This gives you a known-good Gradle setup. After that, do most of the work in Cursor.
6. **Create an emulator** (AVD) with a **"Google Play" or "Google APIs" system image**, which push notifications (FCM) need. API 34 or 35 is a good choice.

---

## 2. Give Cursor the specification and project rules

1. Put the spec in the project as `docs/technical-spec.md`.
2. Create the rules file below. Cursor reads it automatically and follows it in every chat.

**File: `.cursor/rules/android.mdc`**

```markdown
---
description: FSEC Android app rules
alwaysApply: true
---
# Project
- Factory security app. The full spec is in docs/technical-spec.md (Part D = Android architecture, Part B11 = server events).
- Kotlin only, Jetpack Compose, Material 3, minSdk 26, MVI with StateFlow.
- Modules: follow the module graph in spec D3. Feature modules must not depend on other feature modules.
- DI: Hilt. Database: Room. Network: Retrofit + OkHttp + kotlinx.serialization.
- Library versions are only in gradle/libs.versions.toml. Never hardcode versions in build files.

# Workflow
- After every change, run: ./gradlew :<module>:testDebugUnitTest
- Before finishing a task, run: ./gradlew assembleDevDebug lint
- If a build fails, read the full error, fix the cause, and run again. Do not delete or skip tests to make a build pass.
- Write unit tests for every ViewModel and use case (JUnit + Turbine + fakes in :core:testing).
- Small steps: one module or one feature per task.

# Security (do not simplify)
- Never log tokens, passwords or full URLs with credentials.
- Do not disable certificate pinning or allow cleartext, not even for "testing". Use the dev flavor config instead.
- The DeviceKey / BiometricPrompt code must follow spec D11 exactly.
```

---

## 3. Build in phases (with example prompts)

Do **one phase per chat** in Agent mode, and commit to git after each phase works. Git lets you undo cleanly if the AI breaks something.

| Phase | What                                           | Example prompt for Cursor                                                                                                                                                            |
| ----- | ---------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| 1     | Module structure, build-logic, version catalog | "Create the module structure from spec D3 with convention plugins in build-logic. Use the latest stable versions. Make`./gradlew assembleDebug` pass."                             |
| 2     | `:core:model` + `:core:domain`             | "Implement the models and repository interfaces for cameras, active cameras, doors, alarms and nodes. Pure Kotlin, with unit tests."                                                 |
| 3     | **Fake data layer** (dev flavor)         | "Create fake repositories that simulate a server: 16 cameras, 48 doors, random alarms every 60 s. Inject them in the`dev` flavor."                                                 |
| 4     | Active Cameras grid + maximize/back            | "Implement :feature:liveview from spec D10. Use a placeholder video tile (coloured box with the camera name) for now. Write ViewModel tests for maximize, minimize, swipe and stop." |
| 5     | Real networking                                | "Implement :core:network: Retrofit API from spec C8, the WebSocket EventStream from D7, and token refresh. Test with MockWebServer."                                                 |
| 6     | Video                                          | "Implement WebRtcPlayer, SnapshotPlayer and PlayerPool from spec D9."                                                                                                                |
| 7     | Doors + biometric signing                      | "Implement :feature:doors with HoldToConfirmButton and DeviceKey from spec D11."                                                                                                     |
| 8     | Alarms + FCM                                   | "Implement :core:notifications and AlarmActivity from spec D12."                                                                                                                     |
| 9     | Admin screens, settings, polish                | –                                                                                                                                                                                   |

> **Tip:** Phase 3 matters most. With fake data you can build and test the whole UI before any server or hardware exists.

---

## 4. How to test

### 4.1 Automatic tests (Cursor runs them itself)

```bash
./gradlew testDevDebugUnitTest                 # all unit tests (fast, no emulator)
./gradlew :feature:liveview:testDevDebugUnitTest
./gradlew lint detekt                          # code quality
./gradlew recordRoborazziDevDebug              # screenshot tests → PNG files
./gradlew verifyRoborazziDevDebug              # compare with saved screenshots
./gradlew connectedDevDebugAndroidTest         # Compose UI tests (emulator must be running)
```

**Screenshot tests (Roborazzi)** are very useful with Cursor. They save PNG images of the grid with 1, 4, 9 and 16 cameras, and you can drag those images into the Cursor chat for the AI to review the layout.

### 4.2 Running on the emulator from Cursor's terminal

```bash
emulator -list-avds
emulator -avd Pixel_8_API_35 &                 # start emulator
./gradlew installDevDebug                      # build + install
adb shell am start -n com.fsec.dev/com.fsec.MainActivity
adb logcat --pid=$(adb shell pidof com.fsec.dev)   # app logs
adb exec-out screencap -p > screen.png         # screenshot → paste into Cursor chat
```

**Useful emulator test commands:**

| Test                               | Command                                                                                                     |
| ---------------------------------- | ----------------------------------------------------------------------------------------------------------- |
| Fingerprint (doors)                | First add a fingerprint in emulator Settings, then run`adb -e emu finger touch 1` when the prompt appears |
| Doze / phone idle (alarm delivery) | `adb shell dumpsys deviceidle force-idle`                                                                 |
| App killed in background           | `adb shell am kill com.fsec.dev`                                                                          |
| Deep link to an alarm              | `adb shell am start -d "fsec://liveview?alarm=test1"`                                                     |
| Slow or lost network               | Emulator → Extended controls → Cellular → network type / signal                                          |
| Reach a server on your PC          | Use`10.0.2.2` instead of `localhost` in the dev flavor                                                  |

### 4.3 Test server without real cameras or nodes

You can run a small fake backend on your PC:

```yaml
# docker-compose.dev.yml (on your PC)
services:
  go2rtc:                                       # fake cameras from video files
    image: alexxit/go2rtc
    ports: ["1984:1984", "8554:8554", "8555:8555/tcp", "8555:8555/udp"]
    volumes: ["./dev/go2rtc.yaml:/config/go2rtc.yaml", "./dev/videos:/videos"]
  mosquitto:
    image: eclipse-mosquitto:2
    ports: ["1883:1883"]
```

```yaml
# dev/go2rtc.yaml – each test video loops forever as a "camera"
streams:
  cam01_sub: ffmpeg:/videos/hall.mp4#video=h264
  cam02_sub: ffmpeg:/videos/gate.mp4#video=h264
  # ... cam03 – cam16 (the same file can be reused)
```

Then ask Cursor:

> "Write a small FastAPI mock backend from spec C8. It needs login, cameras, sessions, the WebRTC proxy to go2rtc, a WebSocket that sends a test alarm when I call POST /dev/trigger-alarm, and fake door open results."

That lets you test the full flow on the emulator: live video, the Active Cameras grid, alarms and door opening.

---

## 5. What you must test on real phones

The emulator can't reliably test these:

- **Decoding many live streams at once.** Phones differ a lot, so use the phones and tablets the 4 users will actually have.
- **Full-screen alarm on a locked phone**, Do Not Disturb, and battery savers from phone makers like Xiaomi, Samsung and Huawei.
- **Fingerprint hardware and Keystore behaviour**, for example what happens after a new fingerprint is enrolled.
- **4G + WireGuard VPN** remote access.
- **Real push notifications (FCM)** with the app killed.

Connect a phone with USB debugging on. `adb devices` should list it, and `./gradlew installDevDebug` then works the same way.

---

## 6. Limits and tips for working with AI

| Problem                                    | What to do                                                                                                                        |
| ------------------------------------------ | --------------------------------------------------------------------------------------------------------------------------------- |
| AI uses outdated libraries or APIs         | Pin versions in`libs.versions.toml` and tell Cursor "use only versions from the catalog". Check the official docs for new APIs. |
| Big tasks produce broken code              | Keep each task small, with one module per chat, and commit to git often.                                                          |
| AI "fixes" a test by deleting it           | Your rules file forbids this. Still check the git diff before every commit.                                                       |
| Gradle sync errors in Cursor               | Open the project in Android Studio and sync there. The error messages are clearer.                                                |
| Security code (Keystore, pinning, signing) | **Review it yourself line by line.** Never paste real passwords, keys or `google-services.json` contents into the chat.   |
| Compose previews                           | Not available in Cursor. Use Android Studio previews or screenshot tests.                                                         |

---

## 7. Suggested first day

1. Create the project in Android Studio, add the rules file and the spec, and run `git init`.
2. Do Phase 1 in Cursor until `./gradlew assembleDebug` passes.
3. Do Phases 2–4 using fake data.
4. Run the result on the emulator: you should see the grid with 16 placeholder cameras, tap to maximize, and Back to return.
