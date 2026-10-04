In the name of God, the Most Gracious, the Most Merciful

# Herz Android development to-do

Ordered task list for the Android app. Module layout and rules are in [android-architecture.md](android-architecture.md). Specs: [technical-spec.md](technical-spec.md) Part D and B11, [camera-module-plan.md](camera-module-plan.md), [system-design.en.md](system-design.en.md) section 6.

Rules for every task: one module or one feature per task, unit tests for every ViewModel and use case (JUnit + Turbine + fakes from `:core:testing`), then `./gradlew assembleDebug lint` from `android/` before the task is done. Tick the box and update the module status in the architecture file.

## Phase 0: Skeleton

- [x] Empty Compose app `com.raghim.herz`, minSdk 26
- [x] `build-logic` convention plugins: `herz.jvm.library`, `herz.android.library`, `herz.android.library.compose`, `herz.android.feature`
- [x] Empty modules for the full graph, wired into `:app`
- [x] Build check: a feature may not depend on another feature
- [x] Commit the skeleton
- [ ] `herz.android.application` convention plugin, flavors `dev` and `prod`. Switch workflow to `testDevDebugUnitTest` / `assembleDevDebug`
- [ ] Move `:camera:*` and `:feature:discovery` to `devImplementation`
- [x] `herz.hilt` plugin (Hilt + KSP), `HerzApplication` with `@HiltAndroidApp`
- [x] Add to the catalog: coroutines, lifecycle-viewmodel-compose, navigation-compose, kotlinx-serialization, Turbine, coroutines-test
- [ ] CI: ktlint + detekt, unit tests, lint, assemble (spec D17)

## Phase 1: Camera slice on fake data (emulator)

Camera plan steps 2 and 3. All of it runs on the emulator.

- [x] `:core:model`: `Camera`, `ActiveCamera`, `SessionSource`, `StreamState`, `StreamQuality`, `DiscoveredDevice`, `NetworkMode`
- [ ] `:core:model`: `StreamProfile`
- [x] `:core:common`: `AppResult`, dispatcher qualifiers, `@AppScope`
- [x] `:core:domain`: `CameraSource`, `CameraDiscovery`, `CameraConnector`, `ActiveCamerasRepository`; use cases `ObserveActiveCameras`, `StartCamera`, `StopCamera`, `ObserveNetworkMode`; `liveBudget()` and alarm-first sorting with tests
- [x] `:core:video`: `VideoPlayer`, `PlayerState`, `VideoPlayerFactory`, `PlayerPool`
- [x] `:core:testing`: fakes for every interface above, `FakeVideoPlayer`, `MainDispatcherRule`
- [ ] `:core:testing`: dev flavor data, 6 fake cameras, 2 offline (waits for flavors)
- [x] `:core:designsystem`: move `HerzTheme` out of `:app`, `StatusBadge`
- [ ] `:core:designsystem`: shared `CameraTile` frame (the tile lives in `:feature:liveview` for now)
- [x] `:core:ui`: loading, empty, error composables
- [x] `:feature:liveview`: adaptive grid (columns per system-design 6.3 table), tap to maximize, `HorizontalPager` swipe, Back to grid with the same scroll position, per-tile error and reconnect overlay, `SavedStateHandle` for the maximized id. ViewModel tests with Turbine. `gridColumns` tests
- [x] `:feature:cameras`: list with Off / Starting / Live / Offline, Start/Stop, multi-select, "Show Active Cameras (N)"
- [x] `:app`: type-safe NavHost, bottom bar on phone, rail on tablet (Cameras, Live (N) for now; Dashboard, Doors, Alarms come with their features)

## Phase 2: Real video

- [x] `:core:video-rtsp`: `RtspPlayer` on Media3. Sub stream in grid, main stream when maximized, release off-screen players (camera plan step 4)
- [ ] Check on a device that the last frame stays visible during the sub to main stream switch
- [x] Live-tile budget per network mode
- [ ] `SnapshotPlayer` beyond the budget (spec D9)
- [ ] `:core:datastore`: tile limits, stream quality, player choice
- [ ] Test against go2rtc from the emulator: `rtsp://10.0.2.2:8554/cam01`
- [ ] Second RTSP impl on rtsp-client-android behind the same interface, setting to switch (camera plan step 6). Keep the better one

## Phase 3: Direct LAN cameras (dev, real phone)

Camera plan step 5. Needs a real phone on the cameras' Wi-Fi.

- [x] `:camera:discovery`: WS-Discovery (own codec, no ONVIF library), `MulticastLock`, `CHANGE_WIFI_MULTICAST_STATE`, timeout, /24 RTSP port scan
- [x] `:camera:onvif`: RTSP connect with Digest/Basic auth, brand RTSP path fallback, add by RTSP URL. `DirectLanCameraSource` lives in `:core:data`
- [ ] `:camera:onvif`: ONVIF profiles, snapshot URLs. Needs a decision, because ONVIF SOAP is cleartext HTTP (see android-architecture.md, "No cleartext to cameras")
- [x] `:core:database`: Room setup (`herz.android.room` plugin), saved LAN cameras, active sessions
- [x] `:core:security`: camera passwords encrypted with Keystore
- [x] `:feature:discovery`: scan list, enter login, test connection, save
- [x] `:app`: request `ACCESS_LOCAL_NETWORK` on Android 17
- [ ] Test discovery and playback on a real phone on the cameras' Wi-Fi

## Phase 4: Server connection

- [ ] `:core:security`: `TokenStore` (DataStore + Tink, AEAD key in Keystore)
- [ ] `:core:network`: Retrofit + kotlinx.serialization, OkHttp with `CertificatePinner`, no cleartext, auth interceptor, single-flight token refresh, `X-App-Version`, no secrets in logs (spec D8)
- [x] `:core:network`: `NetworkMonitor` giving LAN / REMOTE_VPN / CELLULAR / OFFLINE
- [ ] `:core:data`: `EventStream` WebSocket with backoff, foreground only, re-fetch snapshots after reconnect (spec D7, B11 events)
- [ ] `:core:data`: `ActiveCamerasRepositoryImpl`, `ServerCameraSource` (REST `/sessions/active`, start/stop, WS session events)
- [ ] `:core:database`: cameras, doors, nodes, alarms cache
- [ ] `:feature:auth`: login, TOTP, device registration, waiting for admin approval
- [ ] `:core:video-webrtc`: `WebRtcPlayer`, SDP via `POST /cameras/{id}/webrtc`. Default player on LAN and VPN

## Phase 5: Alarms

- [ ] `:core:notifications`: FCM service, "alarms" channel (USAGE_ALARM), full-screen intent, dedupe WS + FCM by alarm id, push token upload via WorkManager
- [ ] `:feature:alarms`: `AlarmActivity` (show when locked, turn screen on), list, acknowledge, deep link `fsec://liveview?alarm={id}`
- [ ] `:feature:liveview`: alarm banner while maximized, alarm cameras first with red border
- [ ] Onboarding: `POST_NOTIFICATIONS`, full-screen intent permission on Android 14+, DND override, battery exemption, Xiaomi guidance
- [ ] Test: locked phone, Doze, app killed, 4G + VPN. Target under 2 s

## Phase 6: Doors

- [x] `:core:security`: `DeviceKey` and `BiometricSigner` exactly as spec D11. Do not simplify
- [x] `:core:designsystem`: `HoldToConfirmButton`, hold time 1.5 to 3 s (2 s default) set in `:feature:settings`
- [x] `:core:domain`: `DoorRepository`, `DoorCommandSigner`, `OpenDoorUseCase` (challenge, sign, open) independent of transport
- [x] `:core:data`: `SimulatedDoorRepository` (nonce, signature check, ack, 5 s pulse, reed contact, one offline door)
- [x] `:feature:liveview`: door panel next to the wall, hold, biometric, Unlocking / Unlocked / open / closed, offline
- [x] `:feature:doors`: add, edit (name and area) and remove, saved in Room
- [x] `:feature:settings`: fingerprint on or off. Off means the hold alone opens the door. On by default
- [ ] Decide the lock transport (server, Wi-Fi or Bluetooth controllers) and add its `DoorRepository`
- [ ] Device registration: send `DeviceKey.publicKey()` to the server or controller, re-register after `KeyInvalidated`
- [ ] Link doors to cameras (server rules, or a setting in `:feature:cameras`) so "doors on the wall" sort first and tiles show a lock badge
- [ ] `:feature:doors`: list, detail, logs. Reuse the door row and `OpenDoorUseCase`
- [ ] Hide door buttons for the Viewer role (spec D18)

## Phase 7: The rest

- [ ] `:feature:dashboard`: summary; on tablet the grid next to the alarm list (spec D15)
- [ ] `:feature:playback`: timeline, recording search, export clip
- [ ] `:feature:recordings`: save clip with WorkManager to `MediaStore` Movies/FSEC
- [ ] `:feature:devices`: node health (online, via CAN / 4G, RSSI, voltage)
- [ ] `:feature:rules`: admin sensor to camera links
- [ ] `:feature:users`: admin users, roles, device approval
- [ ] `:feature:settings`: stream quality, tile limits, notifications, `FLAG_SECURE`, about
- [ ] Hide admin and operator actions by role (spec D18). Server still enforces
- [ ] Periodic cache refresh worker, 6 h (spec D14)

## Phase 8: Quality and release

- [ ] Compose UI tests: tile, fullscreen, Back, same scroll position
- [ ] Screenshot tests for 1, 2, 4, 6, 9, 16 tiles, phone and tablet
- [ ] Macrobenchmark: 16 tiles, maximize under 300 ms
- [ ] R8 keep rules for serialization DTOs, release signing
- [ ] Firebase App Distribution, then managed Google Play or MDM
