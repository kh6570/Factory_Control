
# FSEC Android – Camera Module Plan

## Is there a project to fork?

**There isn't a polished open-source Android project that already does all of this** (finding cameras, a grid, maximize) and that's also modular and written in modern Kotlin/Compose. The apps that exist are either old, in one big module, or under a GPL license.

The better plan is to start a new project with a good modular structure and plug in proven open-source libraries for the hard parts. Then look at existing apps only for ideas.

---

## 1. What's available

### Libraries to use (the hard parts)

| Need                                                    | Library                                                                                    | Notes                                                                                                                                                                                                                     |
| ------------------------------------------------------- | ------------------------------------------------------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Finding cameras + getting stream URLs (ONVIF)** | [sproctor/ONVIF-Camera-Kotlin](https://github.com/sproctor/ONVIF-Camera-Kotlin)             | A Kotlin library that finds ONVIF cameras on Android. It's on Maven Central (`com.seanproctor:onvifcamera`). You connect with IP, login and password, read the device info and media profiles, then get the stream URL. |
| **Playing video, option A**                       | AndroidX**Media3 ExoPlayer RTSP**                                                    | Made by Google, well documented, Apache license, and AI tools know it well. It adds a small delay because it buffers video.                                                                                               |
| **Playing video, option B (low delay)**           | [alexeyvasilyev/rtsp-client-android](https://github.com/alexeyvasilyev/rtsp-client-android) | Apache-2.0, still maintained. Unlike ExoPlayer it doesn't buffer, so frames show as soon as they arrive. It supports H.264/H.265, Basic/Digest login, and hardware or software decoding.                                  |
| **Project structure**                             | [android/nowinandroid](https://github.com/android/nowinandroid)                             | Google's reference for a modular app. Copy its`build-logic/` setup and module layout.                                                                                                                                   |

### Apps to look at for ideas (don't fork)

| Project                                                                                          | Why look                                                                                                                                   | Why not fork                                                                                       |
| ------------------------------------------------------------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------ | -------------------------------------------------------------------------------------------------- |
| [Ojo](https://github.com/penguin86/ojo)                                                           | A simple camera wall. The grid size follows the camera count: one camera fills the screen, more cameras switch to 2x2, 3x3, 4x4 and so on. | Uses VLC, older code, and the GitHub copy may be out of date (it moved to the author's own server) |
| [warren-bank/Android-RTSP-IPCam-Viewer](https://github.com/warren-bank/Android-RTSP-IPCam-Viewer) | Uses a low-quality stream in the grid and a high-quality stream full screen, with sound only in full screen. This matches your design.     | Old View/Activity code, not modular                                                                |
| [OpenIPC/viewer](https://github.com/OpenIPC/viewer)                                               | The most complete feature set: grid of up to 25 streams, ONVIF/mDNS discovery, subnet scan, auto-reconnect, auto SD/HD switching           | Written in .NET/Avalonia, so it's only useful for ideas                                            |
| [yashrs/ONVIF-Camera](https://github.com/yashrs/ONVIF-Camera)                                     | ONVIF, screenshots, picture-in-picture                                                                                                     | Last updated in 2019                                                                               |

> **License warning:** Check each project's `LICENSE` file. If you copy GPL code, your app may also have to be GPL. The libraries suggested above use Apache-style licenses, which avoid this problem.

---

## 2. Important design point

In the spec, the app gets video **through the server** (go2rtc/WebRTC) and never connects to cameras directly. That still makes sense for the factory: cameras on their own network, remote access over VPN, and access logs.

Connecting **directly over Wi-Fi** is still a good place to start for development and testing. Just put it behind an interface so you can switch to the server later without changing the screens:

```
CameraSource (interface)
 ├── DirectLanSource     ← now: ONVIF discovery + RTSP straight to the camera
 └── ServerSource        ← later: the server API + WebRTC/go2rtc
```

---

## 3. Module layout for the camera part

```
:app
:core:model          Camera, StreamProfile, DiscoveredDevice (pure Kotlin)
:core:common         dispatchers, Result types
:core:designsystem   theme, tiles, icons
:core:database       Room: saved cameras
:core:security       camera passwords encrypted with Keystore
:core:testing        fakes, test rules

:camera:discovery    ONVIF WS-Discovery (+ later: subnet scan for RTSP port 554)
:camera:onvif        connect, profiles, stream/snapshot URLs (later: PTZ)
:camera:player       VideoPlayer interface + Media3 and RtspClient implementations

:feature:discovery   "Find cameras" screen → choose → enter login → test → save
:feature:liveview    grid (1/4/9/16), maximize, swipe, back
:feature:camerasettings   (later) rename, choose stream quality, remove
```

### The key interfaces (write these first)

```kotlin
// :camera:discovery
interface CameraDiscovery {
    fun scan(timeout: Duration = 5.seconds): Flow<DiscoveredDevice>
}

// :camera:onvif
interface CameraConnector {
    suspend fun connect(address: String, user: String, pass: String): Result<CameraInfo>
    suspend fun streamProfiles(cameraId: String): List<StreamProfile> // main + sub stream
}

// :camera:player
interface VideoPlayer {
    val state: StateFlow<PlayerState>   // Idle, Connecting, Playing, Error(reason)
    fun attach(surface: Surface)
    fun play(url: String, user: String, pass: String, lowLatency: Boolean = true)
    fun stop()
    fun release()
}
```

The grid shows each camera's **sub stream** (low resolution) and switches to the **main stream** when you maximize. Without this, 16 tiles will overload most phones.

---

## 4. Things that will catch you out

| Problem                                   | Fix                                                                                                                                                                                                                                                                           |
| ----------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Discovery finds nothing                   | ONVIF discovery uses UDP multicast (239.255.255.250:3702). You need a`WifiManager.MulticastLock` and the `CHANGE_WIFI_MULTICAST_STATE` permission.                                                                                                                        |
| **The emulator can't find cameras** | The emulator sits behind its own internal network (NAT), so multicast never reaches your real Wi-Fi.**Test discovery on a real phone.** On the emulator, use fake discovery or add a camera by hand, e.g. go2rtc at `rtsp://10.0.2.2:8554/cam01`.                     |
| Some Wi-Fi cameras never show up          | Cheap cloud-only cameras (some Xiaomi, Wyze, Ring models) have no ONVIF/RTSP. Some brands support it only after you turn it on. Tapo, for example, needs a "camera account" set up in its app.**Check that your camera models support ONVIF/RTSP before you buy more.** |
| Camera found but no video                 | Fall back to the common RTSP paths for each brand, plus "add by RTSP URL" by hand                                                                                                                                                                                             |
| Grid stutters                             | Use sub streams, keep at most 4 HD streams decoding at once, and pause tiles that are off screen                                                                                                                                                                              |

---

## 5. Build order for Cursor (one chat per step, commit after each)

| Step  | Prompt                                                                                                                                                                                                                                                         |
| ----- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 1     | "Create the module structure from section 3 using convention plugins in`build-logic` (in the style of nowinandroid), Hilt, Compose, and a version catalog. Add `dev` and `prod` flavors. Make `./gradlew assembleDevDebug` pass."                      |
| 2     | "Implement`:core:model` and the interfaces CameraDiscovery, CameraConnector and VideoPlayer. Add fake versions in `:core:testing` and wire them into the dev flavor: 6 fake cameras, 2 of them offline."                                                   |
| 3     | "Implement`:feature:liveview`: an adaptive grid (1/2x2/3x3/4x4), tap to maximize, swipe between cameras when maximized, Back to return to the grid, and an error/reconnect overlay on each tile. Use placeholder tiles. Write ViewModel tests with Turbine." |
| 4     | "Implement`Media3RtspPlayer` in `:camera:player` behind the VideoPlayer interface. Use the sub stream in the grid and the main stream when maximized. Release players for tiles that are off screen."                                                      |
| 5     | "Implement`:camera:discovery` with ONVIF-Camera-Kotlin, including a MulticastLock and a timeout. Implement `:feature:discovery`: scan list → enter login → test connection → save to Room. Store passwords encrypted in `:core:security`."            |
| 6     | "Add`RtspClientPlayer` (rtsp-client-android) as a second VideoPlayer implementation with a setting to switch between them." Then test both on your real cameras and keep whichever works better.                                                             |
| Later | PTZ, snapshots, recording, ServerSource (go2rtc/WebRTC), alarms, doors                                                                                                                                                                                         |

**Testing:** steps 1–4 work fully on the emulator with fake data or go2rtc. Step 5 needs a real phone on the same Wi-Fi as the cameras.

---

## References

- [sproctor/ONVIF-Camera-Kotlin](https://github.com/sproctor/ONVIF-Camera-Kotlin) – ONVIF discovery and connection library
- [alexeyvasilyev/rtsp-client-android](https://github.com/alexeyvasilyev/rtsp-client-android) – low-latency RTSP client ([README](https://github.com/alexeyvasilyev/rtsp-client-android/blob/master/README.md), [Releases](https://github.com/alexeyvasilyev/rtsp-client-android/releases))
- [am3n/RTSP-Client-Android](https://github.com/am3n/RTSP-Client-Android) – another lightweight RTSP client
- [android/nowinandroid](https://github.com/android/nowinandroid) – reference modular architecture
- [penguin86/ojo](https://github.com/penguin86/ojo) – simple RTSP camera wall
- [warren-bank/Android-RTSP-IPCam-Viewer](https://github.com/warren-bank/Android-RTSP-IPCam-Viewer) – RTSP/RTMP viewer
- [OpenIPC/viewer](https://github.com/OpenIPC/viewer) – cross-platform multi-camera viewer
- [yashrs/ONVIF-Camera](https://github.com/yashrs/ONVIF-Camera) – ONVIF viewer with PiP
- [rvi/ONVIFCameraAndroid](https://github.com/rvi/ONVIFCameraAndroid) – older ONVIF demo
- [maksz42/periscope](https://github.com/maksz42/periscope) – Android viewer for Frigate NVR
- [caspermeijn/onvifviewer](https://github.com/caspermeijn/onvifviewer) – ONVIF viewer (Qt/KDE)
