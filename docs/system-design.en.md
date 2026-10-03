<div dir="rtl">


# Factory Security & Door Control System: Design Document v1.1

> **What's new in v1.1:** Users can start any camera themselves. All active cameras (started by an alarm or by hand) appear side by side in one **Active Cameras** grid. Tap a camera to maximize it, then press Back to return to the grid.

---

## 0. Key Design Decisions

1. **Cameras stay powered all the time.** A motion sensor triggers the **live view and high-quality recording**, not the camera's power. IP cameras take 30–90 s to boot, so switching power on at trigger time would miss the event. Optional per-camera **PoE power control** is still supported.
2. **The server records everything.** Android can **save any clip** to the phone with one tap.
3. **One universal controller board.** 48 door nodes + 16 sensor nodes = **64 copies of the same board**. Only the plug-in comms module changes (CAN, 4G/SIM or Ethernet).
4. **NEW: Cameras can be started by an alarm or by hand.** All active cameras appear together in one adaptive grid. Tap one to maximize it, press Back to return.

---

## 1. Requirements Summary

| Topic                  | Requirement                                                                                        |
| ---------------------- | -------------------------------------------------------------------------------------------------- |
| Cameras                | 16 IP cameras (LAN/PoE preferred, Wi-Fi where needed)                                              |
| Sensors                | 16 dual-technology sensors (PIR + microwave), one per camera                                       |
| Sensor action          | Alarm on Android + automatic live view of linked cameras                                           |
| Manual cameras         | User can start/stop any camera; active cameras shown side by side; tap to maximize, Back to return |
| Sensor → camera links | Set by the user, e.g. "Sensor 3 → Cameras 2, 5, 7"                                                |
| Door nodes             | 48 nodes, each switches a relay                                                                    |
| Distance               | Up to 3 km; wired (CAN) or wireless (4G/SIM), modular                                              |
| Power failure          | Doors are**fail-secure** (stay locked)                                                       |
| Android                | Phone or tablet, inside and outside the factory                                                    |
| Users                  | 4 users with roles                                                                                 |
| Recordings             | Recorded on the server, clips can be saved on Android                                              |

---

## 2. System Architecture

```
                         INTERNET (4 users, remote)
                               │  WireGuard VPN / HTTPS
                     ┌─────────┴─────────┐
                     │ Router/Firewall   │◄── 4G backup WAN
                     │ (dual WAN)        │
                     └─────────┬─────────┘
                               │
              ┌────────────────┴────────────────┐
              │   Managed PoE Switch (VLANs)    │
              └──┬──────────┬───────────┬───────┘
                 │          │           │
       16 PoE Cameras   Wi-Fi AP     CENTRAL SERVER
       (fiber for far   (Wi-Fi cams  ├─ Frigate NVR (recording)
        ones)            if needed)  ├─ go2rtc (live video → phone)
                                     ├─ MQTT broker (TLS)
                                     ├─ Backend API + rules engine
                                     ├─ Camera session manager (NEW)
                                     ├─ Database + audit log
                                     └─ Push alarms (Firebase)
                                           │
                    ┌──────────────────────┴───────────────┐
                    │                                      │
            CAN GATEWAY (wired)                     4G / SIM nodes
            (universal board                        MQTT/TLS over
             + Ethernet + CAN)                      the internet
                    │                                      │
     ── CAN bus (isolated, up to 2.5 km/segment) ──        │
       │        │        │        │                        │
   Door node Door node Sensor node ...              Door node / Sensor node
```

**Main idea:** every node sends the same messages whether it is wired or on 4G. The CAN gateway converts CAN messages into the same MQTT messages the 4G nodes send, so the server never needs to know how a node is connected.

---

## 3. Hardware to DESIGN

### 3.1 Universal Node Mainboard (one PCB, 64 pieces)

| Block            | Recommendation                                                      | Why                                                         |
| ---------------- | ------------------------------------------------------------------- | ----------------------------------------------------------- |
| MCU              | ESP32-S3 (e.g. ESP32-S3-WROOM-1)                                    | Built-in CAN (TWAI), Wi-Fi for setup, OTA updates, low cost |
| Power input      | 12–24 V DC wide-input buck, reverse-polarity protection, TVS, fuse | Industrial supply, surge protection                         |
| Watchdog         | External supervisor/watchdog IC                                     | Recovers a hung node                                        |
| Security         | ATECC608B secure element                                            | Stores keys so door commands can't be faked                 |
| Outputs          | 2× relay (lock + spare), flyback diode / varistor                  | Switches the lock                                           |
| Inputs           | 4× optically isolated inputs                                       | Door reed contact, exit button, tamper, sensor alarm        |
| Supervised input | End-of-line resistor support                                        | Detects a cut or shorted sensor wire                        |
| Comms slot       | Header for a plug-in module                                         | Modularity                                                  |
| Local            | USB-C, status LEDs, config button                                   | Field installation                                          |
| Extras           | Temperature sensor, input-voltage monitor                           | Health reporting                                            |

**Door safety in hardware:**

- Use the relay's **Normally Open (NO) contact** with a **fail-secure lock**. No power means the relay is off and the door stays locked.
- Add a **hardware maximum on-time** for the relay (e.g. 10 s) so a firmware bug can never leave a door unlocked.
- Read the **door reed contact** so the app shows the real door state.

### 3.2 Plug-in Communication Modules

| Module         | Parts                                                                                                                                                  | Notes                                                                                  |
| -------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------ | -------------------------------------------------------------------------------------- |
| CAN (wired)    | Isolated transceiver (TI ISO1042), TVS protection, 120 Ω termination jumper                                                                           | 2,500 m at 20 kbit/s, 5,000 m at 10 kbit/s. Isolation is required over long distances. |
| 4G / SIM       | LTE Cat-1 (SIMCom A7670 / Quectel EC200U,**choose the version for your country's bands**), nano-SIM, SMA antenna, large capacitors for 2 A peaks | Use Cat-1, not NB-IoT. NB-IoT responds too slowly for doors.                           |
| Ethernet       | W5500 SPI Ethernet + RJ45                                                                                                                              | ESP32-S3 has no built-in Ethernet MAC                                                  |
| Optional: LoRa | SX1262                                                                                                                                                 | Long range with no SIM costs                                                           |

- Signed commands are longer than 8 bytes, so use **ISO-TP (ISO 15765-2)** to split them across CAN frames.
- If a line is longer than about 2.5 km, split it with a second gateway or use 4G.

### 3.3 CAN Gateway

The same universal board with an **Ethernet module plus a CAN module**, installed in the server room. Build 2 (one spare). For prototyping, a USB-CAN adapter (CANable / PEAK PCAN-USB) works.

### 3.4 Node Firmware Architecture (ESP-IDF / FreeRTOS)

```
App logic (door, sensor, health)        ← same on every node
Message layer (Protobuf/CBOR, signing, replay counter)
Transport interface ──┬─ CAN + ISO-TP driver
                      ├─ LTE + MQTT/TLS driver
                      └─ Ethernet + MQTT/TLS driver
HAL (relays, inputs, watchdog, ATECC608B)
OTA updates + safe rollback
```

The firmware detects which module is plugged in, so **one firmware image runs on all 64 nodes**.

### 3.5 Enclosure & Power (per node)

- IP65 enclosure, DIN rail, cable glands
- DIN-rail supply with battery backup (e.g. Mean Well DRC + 12 V battery)
- Separate fused output for the lock

---

## 4. Devices to BUY

### 4.1 Cameras (16)

Requirements: 4 MP, H.265, **RTSP + ONVIF**, sub-stream, IP67. Use **wired PoE** wherever you can.

| Level            | Example                                     | Approx. price |
| ---------------- | ------------------------------------------- | ------------- |
| Best reliability | EmpireTech IPC-T5442T-ZE                    | ~$130–180    |
| Good value       | Amcrest / Hikvision ColorVu / AcuSense 4 MP | ~$80–120     |
| Budget           | Reolink RLC-520A (use H.264 with Frigate)   | ~$50–65      |
| Wi-Fi only spots | Reolink RLC-510WA or similar                | ~$70–90      |

> ⚠️ In the USA or on government projects, Hikvision/Dahua may be restricted. Use Axis or Hanwha Vision instead.
> **Tip:** buy one camera, test it with the server, then buy the other 15.

### 4.2 Motion Sensors (16): Dual-Tech PIR + Microwave

A dual-tech sensor triggers only when **both** technologies detect motion, so false alarms are rare. It provides a relay output to the sensor node.

- Indoor: Bosch Blue Line Gen2 TriTech, DSC LC-104-PIMW
- Outdoor: Optex VXI-DAM series
- ~$40–150 each

### 4.3 Network

| Item                 | Recommendation                                                                                |
| -------------------- | --------------------------------------------------------------------------------------------- |
| PoE switch           | 24-port managed Gigabit PoE+, ≥250 W, VLAN, SNMP/API (TP-Link Omada TL-SG3428MP or Ubiquiti) |
| Router/firewall      | Dual WAN (fiber + 4G) with VPN (MikroTik, TP-Link ER605, OPNsense)                            |
| Far cameras (>100 m) | Fiber media converters, PoE extenders or a wireless bridge                                    |
| Wi-Fi                | 1–2 business access points                                                                   |
| Protection           | Ethernet surge protectors for outdoor cables                                                  |
| SIM cards            | IoT/M2M plans, only for 4G nodes (~10–50 MB/month)                                           |

### 4.4 Central Server

| Part            | Recommendation                                           |
| --------------- | -------------------------------------------------------- |
| Computer        | Intel N100/N305 or Core i5, 16 GB RAM, 512 GB NVMe       |
| Recording disks | 2 × 8 TB surveillance HDD (WD Purple / SkyHawk), RAID 1 |
| AI (optional)   | Intel iGPU (OpenVINO) or Google Coral                    |
| UPS             | 1000–1500 VA (server + switch + router)                 |
| Spares          | Second gateway board, spare HDD                          |

**Storage:** 16 cameras × 4 Mbit/s ≈ 690 GB/day, so 8 TB holds about 11 days (about 20+ days at 2 Mbit/s with H.265).

### 4.5 Door Hardware (×48)

- Fail-secure electric strike or motor lock, 12/24 V
- Door reed contact
- ⚠️ **Fire safety:** doors must still open **mechanically from the inside**. Check your local fire code.

---

## 5. Server Software (Docker, free)

| Service                        | Job                                                                                          |
| ------------------------------ | -------------------------------------------------------------------------------------------- |
| Frigate                        | NVR: 24/7 and event recording, playback, AI detection                                        |
| go2rtc                         | Live video by WebRTC (remote) or RTSP (local)                                                |
| Mosquitto                      | MQTT with TLS, one certificate per device                                                    |
| Backend API (FastAPI / NestJS) | Users, roles, rules engine,**camera session manager**, door commands, OTA, PoE control |
| PostgreSQL                     | Devices, rules, users, sessions, audit log                                                   |
| Firebase Cloud Messaging       | High-priority alarm push                                                                     |
| WireGuard / Tailscale          | Secure remote access.**Never expose cameras to the internet.**                         |

### 5.1 Alarm Flow (target: under 2 s)

```
Sensor triggers → node sends signed event (CAN→gateway or 4G→MQTT)
→ Backend rule: "Sensor 3 → Cameras 2, 5, 7"
   ├─ Create ALARM camera sessions for cams 2, 5, 7 (shared with all users)
   ├─ Frigate: high-quality event recording
   ├─ (optional) PoE: power on cameras
   └─ FCM high-priority push to all 4 users
→ Android: full-screen alarm + siren
→ Active Cameras grid opens; alarm cameras shown first (red border)
```

### 5.2 Manual Camera Start Flow (NEW)

```
User taps "Start" on Camera 4 (or selects several → "Start selected")
→ POST /api/v1/cameras/4/sessions
→ Session manager:
   ├─ Camera online?   → state = LIVE
   ├─ PoE-controlled?  → power on → state = STARTING → wait for RTSP → LIVE
   └─ (optional) "record while active" → Frigate recording on
→ WebSocket event: camera.session.started
→ Android: Camera 4 tile appears in the Active Cameras grid
```

**Session rules:**

- **Session source:** `ALARM` (shared by all users) or `MANUAL` (belongs to the user who started it).
- **Auto-stop:** a manual session ends when the user stops it, or after a configurable idle time (default 10 min with nobody watching). An alarm session ends when the alarm is acknowledged or times out.
- **PoE reference counting:** a camera is powered off only when **no session at all** remains for it.
- **Permissions:** all roles (Admin, Operator, Viewer) may start and stop cameras. Only Admin can change PoE settings.
- Every start and stop is written to the audit log.

**API:**

```
POST   /api/v1/cameras/{id}/sessions      start camera (manual)
DELETE /api/v1/cameras/{id}/sessions      stop camera (my session)
GET    /api/v1/sessions/active            active cameras for me (manual + alarm)
WS     /api/v1/events                     camera.session.started | camera.session.stopped
                                          | camera.state.changed | alarm.* | door.*
```

### 5.3 Door Command Flow (secure)

```
User taps "Open Door 12" → fingerprint/PIN
→ HTTPS to backend (token) → role check + audit log
→ backend builds {node, door, counter, expiry} + digital signature
→ MQTT (4G) or gateway (CAN)
→ node verifies signature + counter (blocks replay attacks)
→ relay pulses 5 s → reed contact reports opened → closed
→ app shows real door status
```

---

## 6. Android App Architecture

**Stack:** Kotlin, Jetpack Compose, Clean Architecture + MVVM/MVI, Hilt, Coroutines/Flow, Room, DataStore, Retrofit/OkHttp, WorkManager, Media3 ExoPlayer, WebRTC.

### 6.1 Gradle Modules

```
:app                      → navigation, startup, DI
:core:model               → Camera, ActiveCamera, Door, Alarm, User
:core:domain              → use cases (StartCamera, StopCamera, ObserveActiveCameras,
                            OpenDoor, AcknowledgeAlarm...)
:core:data                → repositories (single source of truth)
:core:network             → REST, WebSocket, certificate pinning
:core:database            → Room (cache, history, offline)
:core:security            → tokens, Android Keystore, biometrics
:core:video               → VideoPlayer (WebRTC / RTSP) + PlayerPool
:core:notifications       → FCM, full-screen alarm
:core:designsystem        → theme, CameraTile, DoorButton

:feature:auth             → login, 2FA
:feature:dashboard        → overview: alarms, doors, node health
:feature:cameras          → (NEW) camera list, Start/Stop, multi-select
:feature:liveview         → (UPDATED) Active Cameras grid + maximize/fullscreen
:feature:alarms           → alarm screen, history, acknowledge
:feature:playback         → timeline, search recordings
:feature:recordings       → saved clips on the phone
:feature:doors            → doors, open button, status, logs
:feature:devices          → node health (online, signal, connection type)
:feature:rules            → admin: sensor ↔ camera links
:feature:users            → admin: users and roles
:feature:settings
```

**Rule:** feature modules never depend on each other. They communicate only through `:core:domain` and navigation.

### 6.2 Key Design Points

- **Video:** one `VideoPlayer` interface with two implementations: RTSP (Media3) inside the factory and WebRTC (go2rtc) from outside, chosen automatically. Sub-streams play in the grid, the main stream in fullscreen.
- **Saving recordings:** the "Save clip" button asks the server to export an MP4, and WorkManager downloads it to the phone.
- **Alarms:** FCM high-priority message → full-screen alarm (works on a locked phone) → Active Cameras grid.
- **Roles:** Admin (everything), Operator (view + open doors), Viewer (view only, can start cameras). The **server** enforces roles.
- **Door UI:** biometric confirmation, "hold to open" button, real reed status, audit log.
- **Tablet:** adaptive layout.

### 6.3 Active Cameras View (NEW)

#### Behavior

**Camera list (`:feature:cameras`)**

- Each camera shows its status: `Off` · `Starting…` · `Live` · `Offline`
- **Start / Stop** button per camera
- Multi-select → **"Start selected"**
- Shortcut: "Show Active Cameras (N)"

**Active Cameras grid (`:feature:liveview`)**

- All active cameras (manual and alarm) are shown **side by side**
- The grid adapts to the number of cameras:

| Active cameras | Phone (portrait) | Phone (landscape) / Tablet |
| -------------- | ---------------- | -------------------------- |
| 1              | Full width       | Full screen                |
| 2              | 1 × 2 (stacked) | 2 × 1 (side by side)      |
| 3–4           | 2 × 2           | 2 × 2                     |
| 5–6           | 2 × 3           | 3 × 2                     |
| 7–9           | 3 × 3           | 3 × 3                     |
| 10–16         | 3 × N (scroll)  | 4 × 4                     |

- **Order:** alarm cameras first (red border, "Sensor 3" label), then manual cameras in the order they were started. Drag to reorder is optional.
- Each tile shows the camera name, an `ALARM`/`MANUAL` badge, live status, and **✕** to stop the camera.

**Maximize and return**

- **Tap a tile** → it expands to fullscreen with a smooth animation, and the stream switches from the sub-stream to the main stream (HD).
- Fullscreen controls: snapshot, save clip, record, open linked door, stop camera, **swipe left/right** to the next active camera.
- **Back** (button, gesture or the minimize icon) → returns to the grid in **the same scroll position**. The other tiles stay live.

**Bandwidth protection**

- The grid always uses sub-streams.
- On mobile data, a configurable number of tiles play live (default 4). The others show a snapshot that refreshes every 2 s.
- Tiles that are off-screen or behind the fullscreen view are paused, then resumed instantly.

#### Architecture

```
:feature:cameras ──► StartCameraUseCase / StopCameraUseCase
                                │
:feature:liveview ──► ObserveActiveCamerasUseCase
                                │
:core:data  ActiveCamerasRepository
            ├─ REST: start/stop/list sessions
            ├─ WebSocket: session started/stopped, camera state
            └─ StateFlow<List<ActiveCamera>>   ← single source of truth
                                │
:core:video PlayerPool  (one player per active camera, reused on maximize)
```

```kotlin
// :core:model
data class ActiveCamera(
    val cameraId: String,
    val name: String,
    val source: SessionSource,      // ALARM or MANUAL
    val state: StreamState,         // STARTING, LIVE, ERROR
    val startedBy: String?,
    val startedAt: Instant,
    val alarmId: String? = null
)
enum class SessionSource { ALARM, MANUAL }
enum class StreamState { STARTING, LIVE, ERROR }

// :feature:liveview
data class ActiveCamerasUiState(
    val tiles: List<ActiveCamera> = emptyList(),   // alarm first, then manual
    val maximizedCameraId: String? = null          // null = grid view
)

sealed interface ActiveCamerasIntent {
    data class Maximize(val cameraId: String) : ActiveCamerasIntent
    data object Minimize : ActiveCamerasIntent
    data class Stop(val cameraId: String) : ActiveCamerasIntent
    data class SwipeTo(val cameraId: String) : ActiveCamerasIntent
}
```

**Design notes:**

- **Maximize is a UI state** (`maximizedCameraId`), not a new screen. Players stay alive, so returning to the grid is instant. Compose `BackHandler` sets the state back to `null`. `SharedTransitionLayout` animates the tile into fullscreen.
- The state is stored in `SavedStateHandle`, so rotation and process restore keep the grid and the maximized camera.
- **PlayerPool** keeps each camera's player. On maximize, the last sub-stream frame stays on screen until the first main-stream frame arrives, so there is no black flash.
- If an alarm arrives while a camera is maximized, a banner appears. Tapping it returns to the grid with the alarm cameras at the top.

---

## 7. Rough Budget (without labor and cabling)

| Item                                     | Approx.                  |
| ---------------------------------------- | ------------------------ |
| 16 cameras                               | $1,000–2,500            |
| 16 dual-tech sensors                     | $700–2,000              |
| Switch + router + APs + surge protection | $600–1,200              |
| Server + 2 × 8 TB + UPS                 | $900–1,500              |
| 64 universal nodes + modules             | $1,900–4,500            |
| 48 fail-secure locks + reed contacts     | $2,500–7,000            |
| Node power supplies + enclosures         | $2,000–4,000            |
| SIM plans                                | ~$2–5 per SIM per month |

---

## 8. Build Order

1. **Prototype:** server + 2 cameras + 1 sensor node + 1 CAN door node + 1 4G door node + basic app.
2. **Test:** alarm delay (<2 s), **manual start + grid → maximize → back**, door command security, 3 km cable test on a drum.
3. PCB revision 2, firmware OTA, full app.
4. Install in phases (~10 doors at a time).

---

## 9. References

- CAN in Automation (CiA): CANopen lower layers / bus length — https://can-cia.org/can-knowledge/canopen-lower-layers
- Schneider Electric: maximum CANopen cable length — https://www.se.com/us/en/faqs/FA339840/
- Best PoE cameras for a Frigate NVR — https://computingforgeeks.com/best-poe-cameras-frigate-nvr/
- Reolink PoE camera buying guide — https://reolink.com/blog/poe-ip-cameras-buying-guide/
