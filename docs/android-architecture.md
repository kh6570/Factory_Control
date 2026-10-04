In the name of God, the Most Gracious, the Most Merciful

# Herz Android architecture and module progress

How the Android app in [android/](../android) is split into modules, what each module may depend on, and the state of each module. This merges [technical-spec.md](technical-spec.md) Part D with the camera slice in [camera-module-plan.md](camera-module-plan.md). If they disagree, this file wins for module layout. [setup-progress.md](setup-progress.md) wins for setup state.

The task list lives in [android-todo.md](android-todo.md).

## 1. Principles

1. **Layers point inward.** UI, then ViewModel (MVI), then Domain. Data implements Domain. Domain knows nothing about Android (spec D2).
2. **Interfaces in `:core:domain`, implementations at the edges.** Features only ever see interfaces. Real, fake, server, and direct-LAN implementations are swapped in `:app` by Hilt and flavor, never in a feature.
3. **Features are islands.** A feature never depends on another feature. They talk through `:core:domain` (shared repositories) and navigation routes. A build check enforces this.
4. **Heavy libraries are isolated.** WebRTC, Media3, ONVIF, Room, Firebase each live in one module. Changing a screen does not recompile them, and features do not get them on their compile classpath.
5. **Build config lives in one place.** Convention plugins in `build-logic/` own SDK levels, Java level, Compose, namespaces, and test deps. A module build file only names its plugin and its allowed dependencies. Versions live only in `gradle/libs.versions.toml`.
6. **Pure Kotlin where possible.** `:core:model`, `:core:common`, `:core:domain` are JVM modules: fast to compile, unit tests without Robolectric, and no way to import Android by mistake.

## 2. Module graph

```
                              :app  (wiring, NavHost, Hilt root, flavors)
                                │ depends on every module
   ┌────────────────────────────┼─────────────────────────────────────────┐
   │                            │                                         │
:feature:*  ───────────► :core:domain ◄──────── :core:data          :camera:discovery
(13 islands)                │  (interfaces,       │   │   │            :camera:onvif
   │                        │   use cases)        │   │   │            (dev, direct LAN)
   │                        ▼                     │   │   │                 │
   ├──► :core:ui ──► :core:designsystem           │   │   └─► :core:datastore
   │                 :core:model ◄────────────────┘   └─────► :core:database
   ├──► :core:video  (liveview, playback)              └─────► :core:network ─► :core:security
   │        ▲
   │        ├── :core:video-rtsp    (Media3 RTSP)
   │        └── :core:video-webrtc  (go2rtc WebRTC) ─► :core:network
   └──► :core:security (doors only: BiometricPrompt + DeviceKey, spec D11)

:core:common   pure Kotlin helpers, allowed everywhere
:core:notifications ─► :core:domain   (FCM, AlarmNotifier, full-screen intent)
:core:testing  ─► api: domain, model, common, video   (fakes, used as testImplementation)
```

### Who implements which domain interface

| Interface in `:core:domain` / `:core:video` | Production impl | Dev / test impl |
| --- | --- | --- |
| `CameraSource` | `ServerCameraSource` in `:core:data` | `DirectLanCameraSource` in `:core:data`, `FakeCameraSource` in `:core:testing` |
| `CameraRepository` | `:core:data` (server sync later) | `OfflineFirstCameraRepository` in `:core:data`, fake in `:core:testing` |
| `CameraDiscovery` | none (server knows cameras) | `LanCameraDiscovery` in `:camera:discovery`, fake in `:core:testing` |
| `CameraConnector` | none | `RtspCameraConnector` in `:camera:onvif`, fake in `:core:testing` |
| `NetworkMonitor` | `ConnectivityNetworkMonitor` in `:core:network` | fake in `:core:testing` |
| `ActiveCamerasRepository`, `DoorRepository`, `AlarmRepository`, ... | `:core:data` (`LocalActiveCamerasRepository` until the server exists) | fakes in `:core:testing` |
| `VideoPlayerFactory` | `WebRtcPlayer` in `:core:video-webrtc` | `Media3RtspPlayer` in `:core:video-rtsp`, `FakeVideoPlayer` in `:core:testing` |

| `DoorRepository` | `ServerDoorRepository` (spec D11, B8), or a Wi-Fi / Bluetooth lock-controller repository | `SimulatedDoorRepository` in `:core:data`, fake in `:core:testing` |
| `DoorCommandSigner` | `BiometricDoorSigner` in `:core:security` (spec D11 `DeviceKey`) | fake in `:core:testing` |
| `UserSettingsRepository` | `PreferencesUserSettingsRepository` in `:core:data` (DataStore) | fake in `:core:testing` |

`DirectLanCameraSource` sits in `:core:data` because it only reads saved cameras and their encrypted passwords from Room.

### Doors: one sequence for every transport

Every door transport uses the same steps: get a one-time challenge, sign `doorId|nonce|timestamp` with the device key after a strong biometric check, send the signed command, wait for the lock controller's acknowledgement. `OpenDoorUseCase` runs these steps. Only the `DoorRepository` binding in `core/data/di/DoorModule.kt` decides where they go (simulator now, server or direct lock controllers later).

On screen, opening is: hold the button (1.5 to 3 s, 2 s by default, set in Settings), then the fingerprint prompt naming the door, then "Unlocking…", then "Unlocked" with a countdown, then "Door open" / "Locked" from the reed contact. Moving the finger, a scroll, or a swipe cancels the hold. `DeviceKey` needs Android 11 (`setUserAuthenticationParameters`), so phones on Android 8 to 10 cannot open doors.

### No cleartext to cameras

The security rules forbid cleartext traffic, and ONVIF SOAP runs over plain HTTP. So the direct-LAN path does not use ONVIF SOAP:

- Discovery sends a WS-Discovery probe over UDP multicast (with a `MulticastLock`) and scans the local /24 for TCP ports 554 and 8554.
- Connecting sends RTSP `DESCRIBE` requests with Digest or Basic auth and tries a catalogue of brand stream paths. The user can also enter an RTSP URL by hand.
- Stream URIs are stored without credentials. Passwords are encrypted with an AndroidKeyStore AES-GCM key.

Enabling ONVIF SOAP (profiles, snapshots, PTZ) needs an explicit decision, for example only in the `dev` flavor.

On Android 17 (targetSdk 37) LAN access needs the runtime permission `ACCESS_LOCAL_NETWORK`. `:app` requests it.
| `TokenStore`, `DeviceKeyStore` | `:core:security` | fake in `:core:testing` |

`CameraDiscovery` and `CameraConnector` sit in `:core:domain`, not in `:camera:*` as the camera plan sketched. Otherwise `:feature:discovery` would have to depend on a non-core module.

## 3. Module catalogue and progress

Status: **Empty** = build file only. **Started** = some code. **Done** = done with tests and `assembleDebug lint` green.

### Pure Kotlin (`herz.jvm.library`)

| Module | Holds | May depend on | Status |
| --- | --- | --- | --- |
| `:core:model` | `Camera`, `ActiveCamera`, `SessionSource`, `StreamState`, `StreamProfile`, `DiscoveredDevice`, `Door`, `Alarm`, `Node`, `User`, `Role`, `NetworkMode` | nothing | Started (camera types) |
| `:core:common` | `AppResult`, dispatcher qualifiers, `@AppScope`, time source | nothing | Done |
| `:core:domain` | Repository and source interfaces, use cases (`ObserveActiveCameras`, `StartCamera`, `StopCamera`, `OpenDoor`, `AcknowledgeAlarm`, ...), `liveBudget`, sorting rules | `:core:model`, `:core:common` (api) | Started (camera use cases) |

### Android core (`herz.android.library`, Compose ones marked)

| Module | Holds | May depend on | Status |
| --- | --- | --- | --- |
| `:core:data` | Repository impls, `EventStream` router, `ServerCameraSource`, sync workers (D14) | domain, model, common, network, database, datastore, security | Started (local camera repositories, `DirectLanCameraSource`) |
| `:core:network` | Retrofit APIs, OkHttp, WebSocket, `CertificatePinner`, auth interceptor, token refresh, `NetworkMonitor` | model, common, domain, security | Started (`NetworkMonitor` only) |
| `:core:database` | Room DB, DAOs, entities (cameras, doors, nodes, alarms, saved clips, saved LAN cameras) | model, common | Started (cameras, active sessions, doors) |
| `:core:datastore` | DataStore settings: tile limits, stream quality, player choice | model, common | Started (hold-to-open time) |
| `:core:security` | `TokenStore` (Tink + Keystore), `DeviceKey`, `BiometricSigner` exactly as spec D11, encrypted camera passwords | common, domain | Started (camera password cipher, `DeviceKey`, `BiometricDoorSigner`) |
| `:core:notifications` | FCM service, channels, `AlarmNotifier`, full-screen intent, dedupe | domain, model, common | Empty |
| `:core:designsystem` (Compose) | `HerzTheme`, colors, type, `CameraTile` frame, `StatusBadge`, `HoldToConfirmButton` | nothing | Started (theme, `StatusBadge`, `HoldToConfirmButton`, icons) |
| `:core:ui` (Compose) | Loading, empty, error, offline states, shared adaptive helpers | designsystem (api), model | Started (message and loading states) |
| `:core:testing` (Compose) | Fakes for every domain interface, `FakeVideoPlayer`, `MainDispatcherRule`, Turbine helpers | domain, model, common, video (api) | Started (camera fakes) |

### Video (Compose)

| Module | Holds | May depend on | Status |
| --- | --- | --- | --- |
| `:core:video` | `VideoPlayer`, `PlayerState`, `StreamQuality`, `VideoPlayerFactory`, `PlayerPool`, `SnapshotPlayer` | model, common | Started (no `SnapshotPlayer` yet) |
| `:core:video-rtsp` | `RtspPlayer` on Media3. Later a second impl on rtsp-client-android (camera plan step 6) | video, model, common | Done (Media3) |
| `:core:video-webrtc` | `WebRtcPlayer` on stream-webrtc-android, SDP exchange with the server | video, model, common, network | Empty |

### Direct LAN cameras (dev only, `herz.android.library`)

| Module | Holds | May depend on | Status |
| --- | --- | --- | --- |
| `:camera:discovery` | `LanCameraDiscovery`: WS-Discovery, `MulticastLock`, timeout, /24 scan for RTSP ports 554 and 8554 | domain, model, common | Done |
| `:camera:onvif` | `RtspCameraConnector`: RTSP probe, Digest/Basic auth, brand stream paths, add by RTSP URL. Later ONVIF profiles, snapshots, PTZ | domain, model, common | Started (RTSP only, see "No cleartext to cameras") |

### Features (`herz.android.feature`)

Each feature automatically gets model, common, domain, designsystem, ui, and `:core:testing` for tests. Only the extra column is declared in the feature's build file.

| Module | Screens | Extra deps | Status |
| --- | --- | --- | --- |
| `:feature:liveview` | Active Cameras grid, maximize, swipe, Back (spec D10), door panel (bottom panel on phones, side column on wide screens) | `:core:video` | Started (no alarm banner yet) |
| `:feature:cameras` | Camera list, Start/Stop, multi-select, camera settings | | Done |
| `:feature:discovery` | Find cameras, enter login, test, save (dev) | | Done |
| `:feature:auth` | Login, TOTP, device registration | | Empty |
| `:feature:dashboard` | Alarms, doors, node health summary | | Empty |
| `:feature:alarms` | Full-screen alarm, list, acknowledge | | Empty |
| `:feature:playback` | Timeline, recording search, export clip | `:core:video` | Empty |
| `:feature:recordings` | Clips saved on the phone | | Empty |
| `:feature:devices` | Node health | | Empty |
| `:feature:rules` | Admin: sensor to camera links | | Empty |
| `:feature:users` | Admin: users, roles, device approval | | Empty |
| `:feature:doors` | Door list and detail, hold-to-open, biometric, logs | `:core:security` | Started (add, edit, remove) |
| `:feature:settings` | Stream quality, tile limits, notifications, about | | Started (hold time, fingerprint on or off) |

`:feature:camerasettings` from the camera plan is folded into `:feature:cameras`, so there is one camera management screen set.

### App

| Module | Holds | Status |
| --- | --- | --- |
| `:app` | `HerzApplication`, `MainActivity`, NavHost, bottom bar / rail, Hilt bindings that pick implementations per flavor | Started (Hilt root, NavHost with Cameras and Live, bottom bar / rail, local network permission. No flavors yet) |

## 4. Inside a feature module

Same shape in every feature (spec D5, D6), package `com.raghim.herz.feature.<name>`:

```
feature/liveview/src/main/kotlin/com/raghim/herz/feature/liveview/
├── navigation/LiveViewNavigation.kt   @Serializable route + NavGraphBuilder.liveView()
├── ActiveCamerasRoute.kt              collects state, passes lambdas
├── ActiveCamerasScreen.kt             stateless UI, previewable
├── ActiveCamerasViewModel.kt          StateFlow<UiState>, onIntent(), Channel effects
├── model/ActiveCamerasUiState.kt      UiState, Intent, Effect
└── components/                        private composables
```

Tests sit in `src/test/` and use fakes from `:core:testing`. They never use mocks of our own interfaces.

## 5. Build logic

| Plugin id | Applies | Used by |
| --- | --- | --- |
| `herz.jvm.library` | Kotlin JVM, Java 11, JUnit | model, common, domain |
| `herz.android.library` | `com.android.library`, compileSdk 37, minSdk 26, Java 11, namespace from path, JUnit | Android core, camera |
| `herz.android.library.compose` | the above + Compose compiler + Compose BOM, UI, Material 3 | designsystem, ui, testing, video* |
| `herz.android.feature` | the above + standard core deps + the no-feature-to-feature check | every `:feature:*` |

Namespace is derived from the Gradle path: `:core:video-rtsp` becomes `com.raghim.herz.core.video.rtsp`. The application id stays `com.raghim.herz`.

| `herz.hilt` | KSP + Hilt, `hilt-android`, `hilt-compiler` | data, database, network, security, video*, camera, features, app |
| `herz.android.room` | Room + KSP, schemas exported to `schemas/` | database |

Plugins still to add: `herz.kotlin.serialization`, `herz.android.application` (flavors `dev` / `prod`, moves `:app` config into build-logic).

## 6. Flavors (planned)

| Flavor | Camera source | Video | Server |
| --- | --- | --- | --- |
| `dev` | Fake cameras by default, direct LAN (`:camera:*`) as a debug option | RTSP | Staging, debug menu |
| `prod` | Server only. `:camera:*` and `:feature:discovery` are not packaged | WebRTC, RTSP fallback | Production, pinned |

Until the flavors exist, `:app` depends on every module with `implementation`. The `:camera:*` and `:feature:discovery` dependencies become `devImplementation` then.

## 7. Commands

From `android/`:

| What | Command |
| --- | --- |
| Unit tests, Android module | `./gradlew :core:data:testDebugUnitTest` |
| Unit tests, pure Kotlin module | `./gradlew :core:domain:test` |
| Whole app + lint | `./gradlew assembleDebug lint` |
| After flavors exist | `testDevDebugUnitTest`, `assembleDevDebug` |
