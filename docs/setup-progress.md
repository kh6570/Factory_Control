In the name of God, the Most Gracious, the Most Merciful

# Herz setup progress

Repeatable log for a new machine or a new AI session. Read this file first. Do not redo a step marked done unless the check fails. Update this file at the end of every setup or build step.

Product: factory security Android app (cameras, doors, alarms). Code name: Herz. Design docs live next to the app, not inside the Android module.

## Locked decisions

| Item | Value | Do not change later |
| --- | --- | --- |
| App display name | Herz | No |
| Gradle root project name | Herz | No, unless the Android Studio project is recreated |
| Application id / namespace | `com.raghim.herz` | Yes. Firebase, installs, and signing stick to this |
| Language / UI | Kotlin, Jetpack Compose, Material 3 | Yes |
| minSdk | 26 | Yes |
| First screen to build | Active Cameras grid with fake tiles | Yes |
| Video path for the real system | Server (go2rtc / WebRTC), not a direct camera connection | Yes |
| Direct ONVIF/RTSP | Development only, behind a `CameraSource` interface | Yes |

Rejected package names: `com.example.herz`, `com.sun.herz`, `com.hafez.herz`.

## Repository layout

```
2026_01_10_Factory_CTRL/
  docs/          specifications and this progress log
  android/       Android Studio project (only :app so far)
```

On a new machine, keep this layout. Android Studio will not generate a project into a non-empty folder, so the app stays in `android/`, not in the repo root.

| Doc | Role |
| --- | --- |
| [system-design.en.md](system-design.en.md) | Product design, English |
| [system-design.fa.md](system-design.fa.md) | Same design, Persian |
| [technical-spec.md](technical-spec.md) | Board, protocol, server, Android architecture |
| [camera-module-plan.md](camera-module-plan.md) | First software slice |
| [android-dev-setup.md](android-dev-setup.md) | How to build and test the app |
| [setup-progress.md](setup-progress.md) | This log. Status beats the other docs if they disagree |

## Status

Last updated: 2026-10-03.

| Step | Status | Notes |
| --- | --- | --- |
| Install Android Studio | Done | User confirmed. Version and installed SDK packages were not recorded |
| Organize docs | Done | Renamed into `docs/` |
| Choose package name | Done | `com.raghim.herz` |
| Create Empty Activity (Compose) project | Done | [android](../android) |
| Set minSdk to 26 | Done | [android/app/build.gradle.kts](../android/app/build.gradle.kts) |
| Gradle sync after minSdk change | In progress | User has Android Studio open. Use Sync there. Do not start a second Gradle sync from the terminal while Studio is syncing |
| Create emulator and run Herz | In progress | User is doing this in Android Studio |
| `ANDROID_HOME` and PATH | Done | User variables. New terminals only. SDK is `%LOCALAPPDATA%\Android\Sdk` |
| Kotlin extension | Done in this VS Code | `fwcd.kotlin` is installed. Cursor should install the same recommendation in `.vscode/extensions.json` |
| Opening line on project files | Done | Exact line required. See Opening line below |
| Cursor Android rules | Done | `.cursor/rules/android.mdc`, from the setup doc |
| AI rules for VS Code, Cursor, and Android Studio | Done | Opening line, plus the Cursor Android rules |
| Git commit of the skeleton | Not done | After the emulator shows Hello Android |
| Module skeleton | Not started | Do not start until the empty app runs |

## What the generated project already has

Checked in the wizard output. Do not recreate it.

- Template: Empty Activity, Jetpack Compose.
- Location: `android/`.
- `settings.gradle.kts`: `rootProject.name = "Herz"`, `include(":app")` only.
- Namespace and application id: `com.raghim.herz`.
- Launcher label: Herz (`app/src/main/res/values/strings.xml`).
- `MainActivity` shows `Hello Android!`.
- Version catalog: [android/gradle/libs.versions.toml](../android/gradle/libs.versions.toml).
- AGP 9.4.1, Kotlin 2.2.10, Gradle 9.6.0, Compose BOM 2026.02.01.
- compileSdk / targetSdk 37. Java source compatibility 11.
- minSdk 26 after the edit on 2026-10-03. The wizard had set 24.
- Android Studio found on this machine: 2026.2.1, `C:\Program Files\Android\Android Studio`.

## Opening line

Every text file in this repo starts with:

In the name of God, the Most Gracious, the Most Merciful

On a new machine, do not strip that line. New files must get it in a form that still builds. The AI rule is already stored for all three editors:

| Editor | File | How it applies |
| --- | --- | --- |
| VS Code | [.github/copilot-instructions.md](../.github/copilot-instructions.md) | Copilot instructions for this workspace |
| Cursor | [.cursor/rules/opening-line.mdc](../.cursor/rules/opening-line.mdc) and [.cursor/rules/android.mdc](../.cursor/rules/android.mdc) | Always applied. Open the repo root, not `android/` |
| Android Studio agent | [AGENTS.md](../AGENTS.md) and [android/AGENTS.md](../android/AGENTS.md) | Studio reads `AGENTS.md` from the project root |
| Android Studio Gemini rules | [android/.idea/project.prompts.xml](../android/.idea/project.prompts.xml) | Project rule. Reopen the project if the Rules box is empty, then check Settings, Tools, AI, Prompt Library, project scope |

Do not put the line in binary files, JSON, or generated output under `build/` or `.gradle/`. XML comments go after the XML declaration. A shebang stays on line 1.

## Repeat on a different system

1. Install Android Studio. It supplies the SDK, emulator, and a JDK 17+. Record the version in this file.
2. Install the Android SDK Platform for the project's compileSdk, plus Android SDK Platform-Tools, Emulator, and a Google Play or Google APIs system image (API 34 or 35).
3. Clone or copy this repo. Open `android/` in Android Studio, not the repo root.
4. Confirm [android/app/build.gradle.kts](../android/app/build.gradle.kts) still has `applicationId = "com.raghim.herz"` and `minSdk = 26`. Sync Gradle in Android Studio.
5. Set environment variables for terminal builds, then open a new terminal:
   - `ANDROID_HOME` = `%LOCALAPPDATA%\Android\Sdk`
   - Add `%ANDROID_HOME%\platform-tools` and `%ANDROID_HOME%\emulator` to PATH.
6. Create an AVD with a Google Play or Google APIs image, API 34 or 35. A plain image cannot test push notifications later.
7. Run the app. Done only when the emulator shows `Hello Android!`.
8. Continue at the first unchecked step below. Do not jump to cameras, ONVIF, the server, or the PCB.

## Current next step

Finish the emulator run that is already in progress.

1. In Android Studio, click Sync if it asks after the minSdk edit.
2. Device Manager: create a phone AVD with a Google Play or Google APIs system image, API 34 or 35.
3. Run the `app` configuration.
4. Stop when the screen says `Hello Android!`.
5. Tell the AI. `ANDROID_HOME` is already set for new terminals. The following task is `adb devices`, then a git commit of the skeleton. Not modules yet.

## Not yet

- Product flavors `dev` and `prod`.
- Convention plugins and the module graph (`:core:model`, `:feature:liveview`, and the rest).
- Fake cameras and the adaptive grid.
- Real video, discovery, doors, alarms, FCM.
- Hardware, MQTT, and the central server.
