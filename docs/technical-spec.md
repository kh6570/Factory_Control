# Factory Security & Door Control System: Technical Specification v1.2

| Part | Content |
|---|---|
| **A** | Universal Node Board: detailed design, schematic blocks, pin map, BOM |
| **B** | Communication Protocol: MQTT topics, CAN IDs, message format, security |
| **C** | How-To: Central Server Setup (Docker, Frigate, MQTT, backend) |
| **D** | Android Software Architecture |

> Applies to design document v1.1 (cameras, sensors, 48 door nodes, 16 sensor nodes, 4 users, Active Cameras grid).
> ⚠️ Part numbers and values are a solid starting point. **Check every IC against its current datasheet** before PCB layout.

---

# PART A: Universal Node Board

## A1. Overview

One PCB (**FSN-1 "Factory Security Node"**) is built 64 times (+ ~6 spares). The node's role (door / sensor / gateway) is set by **configuration**, and its connection type by the **plug-in comms module**.

```
             ┌──────────────────────── FSN-1 MAINBOARD ────────────────────────┐
 VIN 9–32V ─►│ PTC → Schottky → TVS → Buck 5V ──► Buck 3V3                      │
             │                         │  └──► Relays, comms slot                │
             │                         └──────► VIN_PROT ──► slot (LTE power)   │
             │                                                                  │
             │  ┌────────────┐  I2C   ┌──────────┐ ┌────────┐ ┌──────────┐      │
             │  │ ESP32-S3   │◄──────►│ATECC608B │ │TMP102  │ │PCA9554   │─LEDs │
             │  │ WROOM-1    │        └──────────┘ └────────┘ │IO expand │─Tamper
             │  │            │                                └──────────┘      │
             │  │  ADC ◄─────┼── 4× supervised inputs (EOL) ◄── field terminals  │
             │  │  GPIO ◄────┼── 2× opto-isolated inputs    ◄── field terminals  │
             │  │  GPIO ─────┼─► AND ◄─ TLC556 max-on timer ─► MOSFET ─► RELAY 1 │─► LOCK
             │  │  GPIO ─────┼─► AND ◄─ TLC556 max-on timer ─► MOSFET ─► RELAY 2 │─► AUX
             │  │  USB ──────┼── USB-C (ESD)                                     │
             │  │  EN ◄──────┼── TPS3823 watchdog/supervisor                     │
             │  └─────┬──────┘                                                  │
             │        │ CAN TX/RX · UART · SPI · I2C · INT/RST/PWR              │
             │   ┌────┴─────────── COMMS SLOT (2×12 header) ──────────┐         │
             └───┴────────────────────────────────────────────────────┴─────────┘
                     │            │             │              │
                  M-CAN        M-LTE         M-ETH          M-GW (ETH+CAN)
```

## A2. Power Stage

```
VIN+ ──[F1 PTC 0.75A/60V]──►|── D1 SS26 (60V Schottky) ──┬──► VIN_PROT (to slot, AUX+)
                                                         │
                                                   D2 SMBJ33A (TVS to GND)
                                                         │
                                       C 2×10µF/50V ceramic + 47µF/50V electrolytic
                                                         │
                                         U2 LMR36015 (4.2–60V buck, 1.5A) ──► +5V
                                                                              │
                                                     U3 TLV62569 (5V→3.3V, 2A) ──► +3V3
VIN_PROT ──[F7 PTC 0.3A]──► AUX+ terminal (powers motion sensor, 12V systems)
VIN_PROT ── R 100k / 8.2k divider ──► GPIO6 (VIN_SENSE, ADC1)
```

| Rail | Consumers | Budget |
|---|---|---|
| VIN_PROT (9–32 V) | LTE module buck, AUX sensor output | ≤ 1 A |
| +5 V | Relay coils (2 × ~80 mA), CAN isolated DC-DC (~200 mA) | ≤ 600 mA |
| +3V3 | ESP32-S3 (peak ~500 mA Wi-Fi), W5500 (~130 mA), logic | ≤ 900 mA |

**Notes**
- Use a **12 V system** for sensor nodes (most dual-tech sensors run on 9–16 V). Door nodes may use 12 or 24 V.
- The lock has its **own supply input** (`LOCK_PWR`). Lock current never flows through the logic supply.
- `VIN_SENSE` detects mains failure and a low battery: the node reports `POWER_FAIL` when VIN drops below a set threshold. Wire the PSU's "AC fail" contact to `ISO_IN2` for an earlier warning.

## A3. MCU Core & Pin Map

**Module:** `ESP32-S3-WROOM-1-N16` (16 MB flash, **no octal PSRAM**). Avoid the `R8` (octal PSRAM) variants, because they use GPIO35–37.

| GPIO | Function | Notes |
|---|---|---|
| 0 | BOOT / CONFIG button | Hold 5 s at runtime = provisioning mode |
| 1 | IN1 supervised (ADC1_CH0) | Door: reed contact · Sensor: alarm |
| 2 | IN2 supervised (ADC1_CH1) | Door: exit button · Sensor: sensor tamper |
| 4 | IN3 supervised (ADC1_CH3) | Spare |
| 5 | IN4 supervised (ADC1_CH4) | Spare / external tamper |
| 6 | VIN_SENSE (ADC1_CH5) | 100k/8.2k divider |
| 7 | VLOCK_IN_SENSE (ADC1_CH6) | Lock supply present (fuse OK) |
| 8 | VLOCK_OUT_SENSE (ADC1_CH7) | Lock actually energized |
| 9 | BOARD_REV (ADC1_CH8) | Resistor divider = HW revision |
| 10 | RELAY1_REQ | 100k pull-down |
| 11 | RELAY2_REQ | 100k pull-down |
| 12 | TIMER1_TRIG | Falling edge starts max-on window |
| 13 | TIMER2_TRIG | |
| 14 | ISO_IN1 | Opto output, 10k pull-up |
| 38 | ISO_IN2 | Opto output, 10k pull-up |
| 15 / 16 | CAN_TX / CAN_RX (TWAI) | To slot |
| 17 / 18 | UART_TX / UART_RX | To slot (LTE) |
| 19 / 20 | USB D− / D+ | Native USB (flash, console) |
| 21 | WDI (watchdog kick) | High-Z during boot = watchdog disabled |
| 35 / 36 / 37 | MOD_INT / MOD_RST / MOD_PWR | To slot |
| 39 / 40 / 41 / 42 | SPI SCK / MOSI / MISO / CS | To slot (W5500, LoRa) |
| 47 / 48 | I2C SDA / SCL | 4.7k pull-ups. ATECC608B, TMP102, PCA9554, slot EEPROM |
| 3, 45, 46 | **Not used** | Strapping pins. GPIO45 high at boot breaks 3.3 V flash |

**I2C addresses:** PCA9554 `0x20`, TMP102 `0x48`, Module EEPROM `0x50`, ATECC608B `0x60`.

**PCA9554 I/O expander:** P0 LED_STATUS (green), P1 LED_COMM (blue), P2 LED_FAULT (red), P3 BUZZER, P4 TIMER1_OUT readback, P5 TIMER2_OUT readback, P6 enclosure TAMPER switch, P7 spare.

## A4. Relay Channel with Hardware Maximum On-Time

The relay turns on **only if** firmware requests it **AND** the hardware timer window is open. A stuck GPIO or crashed firmware cannot keep a door unlocked.

```
                         +3V3
                          │ R 100k
GPIO12 TIMER1_TRIG ──C 10nF──┴── TRIG (TLC556 ch1)        TLC556 ch1 (monostable)
                                  THR/DIS ──┬── R 910k ── +3V3
                                            └── C 10µF (low-leakage X7R/film) ── GND
                                  OUT ──┬────────────────► PCA9554 P4 (readback)
                                        │
                                   [JP1: MAX-ON ENABLE / BYPASS]
                                        │
GPIO10 RELAY1_REQ ──(100k↓)──► 74LVC2G08 AND ──R 100Ω──► AO3400 gate (100k↓)
                                                          │ drain
                                    +5V ── K1 coil ───────┘
                                     └── 1N4148W flyback across coil

t_on(max) = 1.1 × R × C = 1.1 × 910k × 10µF ≈ 10 s  (tolerance ±20%)
```

**Lock contact wiring (K1 = Omron G5LE-1 DC5, 10 A SPDT):**

```
LOCK_PWR+ ──[F2 2A fuse]──┬── K1 COM
                          └── VLOCK_IN divider → GPIO7
K1 NO ──┬── LOCK+ terminal ──► fail-secure lock (+)
        └── VLOCK_OUT divider → GPIO8
LOCK_PWR− ────────────────── LOCK− terminal ──► lock (−)
SMBJ33CA across LOCK+/LOCK−; fit a diode across a DC lock coil at the lock itself.
```

**Firmware sequence for "pulse open 5 s":** pull `TIMER1_TRIG` low for 1 ms → check readback = high → set `RELAY1_REQ` high → after 5 s set it low. If readback ≠ high, abort and report `HW_FAULT`.

> **JP1 BYPASS** is only for doors that need "hold open" schedules (e.g. business hours). Bypass is reported in `Status.flags`, so the server can show it.

## A5. Supervised Inputs (EOL, ×4)

```
                   +3V3
                    │ R_PU 4.7k 1%
TERM INx ──[PTC 50mA]──┬───┴────────── R 1k ──┬──► ADC (GPIO1/2/4/5)
                       │                      ├── BAT54S clamp (3V3/GND)
                  SMAJ5.0A                    └── C 100nF ── GND
                       │
                      GND
Field side (at the sensor):   INx ── R_EOL 4.7k ──┬── NC contact ──┬── GND
                                                  └── R_ALM 4.7k ──┘
```

| Loop state | Loop R | V_ADC (nominal) | Decision threshold |
|---|---|---|---|
| Short (sabotage) | 0 Ω | 0 V | < 0.82 V |
| Normal (contact closed) | 4.7 kΩ | 1.65 V | 0.82–1.93 V |
| Alarm (contact open) | 9.4 kΩ | 2.20 V | 1.93–2.75 V |
| Cut wire (sabotage) | ∞ | 3.3 V (saturates) | > 2.75 V |

- Sampled every 20 ms, debounce 3 samples (configurable). Use ESP-IDF `adc_cali` (curve fitting), 12 dB attenuation.
- Per-input mode: `NO`, `NC`, `EOL_SINGLE`, `EOL_DUAL` (default for sensors and reed contacts).

## A6. Opto-Isolated Inputs (×2, 10–30 V wet)

```
ISOx+ ── 1N4148W ── 3.3k (2512, 1 W) ──► LED (TLP293 / LTV-827) ──► ISOx−
                                 10k across LED (raises threshold, noise immunity)
Output: collector ── 10k ── +3V3, collector → GPIO14 / GPIO38; emitter → GND
```

Use for the PSU "AC fail" contact, a fire-alarm interface, or an external system contact.

## A7. Watchdog, Security, Sensors

| Block | Part | Connection |
|---|---|---|
| Supervisor + watchdog | TPS3823-33 (1.6 s timeout, 2.93 V reset) | RESET → ESP32 EN, MR → reset button, WDI ← GPIO21. **High-Z WDI disables watchdog during boot/OTA. Verify in datasheet.** |
| Secure element | ATECC608B (TNGTLS pre-provisioned or blank + own provisioning) | I2C 0x60. Holds device private key (never leaves chip) + server command-signing public keys |
| Temperature | TMP102 | I2C 0x48 |
| USB ESD | USBLC6-2SC6 | USB-C D+/D− |

ESP-IDF features to enable: **Secure Boot v2, Flash Encryption, NVS encryption, anti-rollback**.

## A8. Comms Slot Pinout (2×12, 2.54 mm)

| Pin | Signal | Pin | Signal |
|---|---|---|---|
| 1 | VIN_PROT | 2 | VIN_PROT |
| 3 | +5V | 4 | +5V |
| 5 | +3V3 | 6 | +3V3 |
| 7 | GND | 8 | GND |
| 9 | CAN_TX (GPIO15) | 10 | CAN_RX (GPIO16) |
| 11 | UART_TX (GPIO17) | 12 | UART_RX (GPIO18) |
| 13 | SPI_SCK (39) | 14 | SPI_MOSI (40) |
| 15 | SPI_MISO (41) | 16 | SPI_CS (42) |
| 17 | I2C_SDA (47) | 18 | I2C_SCL (48) |
| 19 | MOD_INT (35) | 20 | MOD_RST (36) |
| 21 | MOD_PWR (37) | 22 | GND |
| 23 | RSV | 24 | RSV |

Each module has an I2C **ID EEPROM at 0x50** (module type, revision, serial). Ethernet modules use **24AA02E48**, which also provides a unique MAC address. Firmware reads it at boot and loads the right driver.

## A9. Comms Modules

### M-CAN (isolated CAN)

```
CAN_TX/RX ─► ISO1042 (logic side 3V3) ║ isolation ║ bus side 5V_ISO ◄─ B0505S-1WR3 (isolated DC-DC)
                                        CANH/CANL ─► CM choke ACT45B-510 ─► PESD2CAN (TVS)
                                                 ─► split termination 2×60.4Ω + 4.7nF (JP: ON/OFF)
                                                 ─► GDT 90 V to PE/shield (outdoor/long runs)
Connectors: 2 × 5-pin pluggable (IN / OUT daisy-chain): CAN_H, CAN_L, CAN_GND(iso), SHIELD, NC
```

- **Termination ON only at the two physical ends** of each segment.
- Cable: shielded twisted pair, 120 Ω, ≥ 0.75 mm² for 2–2.5 km (e.g. DeviceNet thick or a CAN bus cable). Carry **CAN_GND** as a third conductor. Ground the shield at **one point only**.

### M-LTE (4G Cat-1)

| Part | Example |
|---|---|
| Modem | SIMCom A7670 (choose E/SA/G variant for your bands) or Quectel EC200U |
| Power | LMR33630 buck VIN_PROT → 3.8 V / 3 A + 2 × 470 µF polymer + 100 nF + 33 pF at module |
| SIM | Nano-SIM push-push holder + ESD array (e.g. ESDA6V1-5SC6) |
| Level shift | Modem IO is 1.8 V → TXS0104E or BSS138 shifters for UART/PWRKEY/RESET |
| PWRKEY | NPN (MMBT3904) driven by MOD_PWR |
| Antenna | U.FL → SMA bulkhead pigtail, external IP67 LTE antenna |

### M-ETH (Ethernet)

W5500 + 25 MHz crystal + HR911105A (RJ45 with magnetics) + 24AA02E48 (MAC). Powered from +3V3.

### M-GW (Gateway combo = Ethernet + CAN)

M-ETH + M-CAN circuits on one module. Used in the server room (2 pcs, 1 spare).

### M-LORA (optional)

Ebyte E22-900M22S (SX1262) on SPI + SMA connector.

## A10. Field Terminals (pluggable, 5.08 mm)

| Connector | Pins |
|---|---|
| J_PWR | VIN+, VIN−, AUX+ (sensor supply), AUX− |
| J_LOCK | LOCK_PWR+, LOCK_PWR−, LOCK+ (relay NO), LOCK− |
| J_REL2 | COM, NO, NC (dry contact) |
| J_IN | IN1, GND, IN2, GND, IN3, GND, IN4, GND |
| J_ISO | ISO1+, ISO1−, ISO2+, ISO2− |

**Role wiring**

| Role | IN1 | IN2 | IN3 | IN4 | Relay 1 | Relay 2 |
|---|---|---|---|---|---|---|
| Door node | Door reed | Exit button | Spare | Ext. tamper | Lock | Buzzer / spare |
| Sensor node | Sensor alarm | Sensor tamper | Spare | Ext. tamper | (unused) | Siren / spare |
| Gateway | – | – | – | – | – | – |

## A11. PCB Layout Rules

- **4 layers:** L1 signals, L2 solid GND, L3 power, L4 signals. Board ~100 × 80 mm in a DIN-rail enclosure, IP65 outer box.
- **ESP32 antenna:** follow Espressif's keep-out (antenna at the board edge, no copper under it).
- **Protection parts sit directly at the terminals**, with short paths to GND. Field side at one edge, logic in the middle.
- **Relay/lock traces:** ≥ 2 mm wide (1 oz copper, 3 A), ≥ 2 mm clearance to logic.
- **Isolation barrier** on M-CAN: no copper under ISO1042/DC-DC, ≥ 4 mm creepage between sides.
- Buck converters: tight input loop, follow the datasheet layout.
- Optional conformal coating for humid or dusty factory areas.

## A12. Bill of Materials

### Mainboard (per board, ~100 pcs pricing)

| Ref | Function | Example part | Qty | ~USD |
|---|---|---|---|---|
| U1 | MCU module | ESP32-S3-WROOM-1-N16 | 1 | 3.50 |
| U2 | Buck VIN→5V | TI LMR36015 + inductor | 1 | 2.20 |
| U3 | Buck 5V→3V3 | TI TLV62569 + inductor | 1 | 0.70 |
| U4 | Supervisor/watchdog | TPS3823-33 | 1 | 0.50 |
| U5 | Secure element | ATECC608B | 1 | 0.80 |
| U6 | I/O expander | PCA9554 / TCA9534 | 1 | 0.60 |
| U7 | Temperature | TMP102 | 1 | 0.80 |
| U8 | Dual timer | TLC556 | 1 | 0.60 |
| U9 | Dual AND | 74LVC2G08 | 1 | 0.15 |
| U10 | Optocouplers | LTV-827 (dual) | 1 | 0.30 |
| U11 | USB ESD | USBLC6-2SC6 | 1 | 0.20 |
| K1, K2 | Relay 5 V, 10 A | Omron G5LE-1 DC5 | 2 | 2.00 |
| Q1, Q2 | Relay drivers | AO3400A | 2 | 0.10 |
| D1 | Reverse protection | SS26 | 1 | 0.10 |
| D2 / D5 | TVS input / lock | SMBJ33A / SMBJ33CA | 2 | 0.30 |
| D6–D9 / D10–D13 | Input TVS / clamps | SMAJ5.0A / BAT54S | 8 | 0.45 |
| F1 / F3–F6 / F7 | PTCs | Bourns MF-R075 / MF-R005 / MF-R030 | 6 | 0.90 |
| F2 | Lock fuse + holder | 2 A, 5×20 mm | 1 | 0.60 |
| J1 | USB-C 16-pin | – | 1 | 0.30 |
| J_* | Pluggable terminals | Phoenix MSTB / Degson | 5 | 2.50 |
| J_SLOT | 2×12 female header | – | 1 | 0.40 |
| SW, LED | Buttons, tamper switch, 4 LEDs | – | – | 1.00 |
| – | Passives | – | – | 2.00 |
| – | 4-layer PCB + assembly | – | – | 8–12 |
| | **Mainboard total** | | | **~$30–35** |

### Modules (approx.)

| Module | Key parts | ~USD |
|---|---|---|
| M-CAN | ISO1042, B0505S, choke, TVS, GDT, 2 connectors, EEPROM | 10–12 |
| M-LTE | A7670/EC200U, LMR33630, caps, SIM holder, shifters, antenna | 22–30 |
| M-ETH | W5500, RJ45 magjack, 24AA02E48 | 7–9 |
| M-GW | M-ETH + M-CAN | 15–18 |
| M-LORA | E22-900M22S, SMA | 8–10 |

**Build quantity:** 70 mainboards (64 + 6 spares). CAN and LTE module counts follow your cable plan (e.g. 50 CAN + 14 LTE) + 10% spares. Plus 3 M-GW.

## A13. Bring-up & Production Test

1. Lab supply, current limit 200 mA. Check VIN_PROT, 5V, 3V3 and ripple.
2. Flash test firmware via USB-C. Verify I2C scan: `0x20, 0x48, 0x50 (if module), 0x60`.
3. **Relay test:** request without trigger → relay must stay OFF. Trigger + request → ON, then OFF after ~10 s even if the request stays high.
4. **ADC calibration jig:** 0 Ω / 4.7 k / 9.4 k / open on each input → states must match the A5 table.
5. Watchdog: stop kicking WDI → reset within 1.6 s.
6. ATECC608B provisioning: generate key → CSR → sign with CA (Part C6) → lock config.
7. Module tests: CAN loopback to a second board · LTE attach + MQTT/TLS · Ethernet DHCP + MQTT.
8. 48 h burn-in at 40 °C with relay cycling. Record serial + results in a database.

> Build a **pogo-pin test jig** plus a Python script for steps 2–7. It pays off at 70 boards.

---

# PART B: Communication Protocol

## B1. Layers

```
┌──────────────────────────────────────────────────────────┐
│ Application: DoorCommand, Event, Status, Ack, TimeSync...│
├──────────────────────────────────────────────────────────┤
│ Envelope: Protobuf Body + ECDSA P-256 signature          │  ← identical on all transports
├───────────────────────────┬──────────────────────────────┤
│ MQTT 3.1.1/5 over TLS 1.2 │ ISO-TP (ISO 15765-2) on CAN  │
│ (4G, Ethernet, gateway)   │ 11-bit IDs, 20 kbit/s        │
└───────────────────────────┴──────────────────────────────┘
```

The **gateway does not decode or re-sign**. It moves the same `Envelope` bytes between CAN and MQTT. The server verifies signatures end-to-end.

## B2. Addressing

| Item | Format | Example |
|---|---|---|
| Site | short string / uint16 | `f1` / `1` |
| Node ID | 1–127, unique site-wide | `17` |
| Node name | `N` + 3 digits | `N017` |
| Gateway ID | `gw01`, `gw02` (CAN address 0 on its own segment) | `gw01` |
| Server ID (in `src`) | `0xFFFF` | – |

## B3. Message Format (Protobuf, `fsec/v1`)

Use **nanopb** on the ESP32 and the official protobuf library on the server.

```proto
syntax = "proto3";
package fsec.v1;

// Outer wrapper. The signature covers the raw "body" bytes, so there are no
// protobuf canonicalization issues.
message Envelope {
  bytes  body   = 1;   // serialized Body
  bytes  sig    = 2;   // ECDSA P-256, raw r||s (64 bytes) over SHA-256("FSEC1" || body)
  uint32 key_id = 3;   // signing key slot (rotation): 1 or 2
}

message Body {
  uint32 ver     = 1;  // protocol version = 1
  uint32 site    = 2;
  uint32 src     = 3;  // node id, or 0xFFFF = server
  uint32 dst     = 4;  // node id, or 0xFFFF = server
  uint64 counter = 5;  // strictly increasing per (src,dst) direction
  uint32 time    = 6;  // sender unix seconds (0 = not synced)
  uint32 expires = 7;  // commands only: unix seconds
  uint32 req_id  = 8;  // command id (ack correlation / idempotency)

  oneof msg {
    // server → node
    DoorCommand  door_cmd  = 20;
    RelayCommand relay_cmd = 21;
    TimeSync     time_sync = 22;
    ConfigSet    config    = 23;
    OtaControl   ota       = 24;
    Ping         ping      = 25;
    // node → server
    Event        event     = 40;
    Ack          ack       = 41;
    Status       status    = 42;
    Hello        hello     = 43;
  }
}

message DoorCommand {
  enum Action { PULSE_OPEN = 0; LOCK = 1; HOLD_OPEN = 2; RELEASE_HOLD = 3; }
  uint32 door     = 1;   // 1 = relay 1
  Action action   = 2;
  uint32 pulse_ms = 3;   // 500..10000 (hardware max ~10 s)
  uint32 user_ref = 4;   // hashed user id, for the local log
}

message RelayCommand { uint32 relay = 1; bool on = 2; uint32 duration_ms = 3; }
message TimeSync     { uint32 unix_time = 1; int32 tz_offset_min = 2; }
message Ping         { uint32 nonce = 1; }

message InputConfig {
  enum Mode { NO = 0; NC = 1; EOL_SINGLE = 2; EOL_DUAL = 3; DISABLED = 4; }
  enum Use  { GENERIC = 0; DOOR_REED = 1; EXIT_BUTTON = 2; SENSOR_ALARM = 3; TAMPER = 4; }
  uint32 index = 1; Mode mode = 2; Use use = 3; uint32 debounce_ms = 4;
}
message ConfigSet {
  enum Role { DOOR = 0; SENSOR = 1; GATEWAY = 2; }
  Role   role                 = 1;
  repeated InputConfig inputs = 2;   // nanopb max_count = 4
  uint32 heartbeat_s          = 3;   // CAN default 10, 4G default 60
  uint32 door_held_alarm_s    = 4;   // default 30
  bool   exit_button_offline  = 5;   // exit button works when server unreachable (default true)
  uint32 config_version       = 6;
}

message OtaControl {
  string url = 1;          // HTTPS (4G/ETH); empty = CAN transfer
  bytes  sha256 = 2;
  uint32 size = 3;
  string version = 4;
  bytes  image_sig = 5;    // firmware signature (separate firmware key)
}

message Event {
  enum Type {
    ALARM = 0; ALARM_CLEARED = 1; TAMPER = 2;
    DOOR_OPENED = 3; DOOR_CLOSED = 4; DOOR_FORCED = 5; DOOR_HELD_OPEN = 6;
    EXIT_PRESSED = 7; INPUT_FAULT_SHORT = 8; INPUT_FAULT_CUT = 9;
    POWER_FAIL = 10; POWER_RESTORED = 11; LOCK_SUPPLY_FAIL = 12;
    BOOT = 13; HW_FAULT = 14;
  }
  Type   type      = 1;
  uint32 input     = 2;   // input index, if applicable
  uint32 value     = 3;   // raw ADC mV or extra data
  uint32 event_ts  = 4;   // when it happened (may be older than Body.time if buffered)
  bool   buffered  = 5;   // true = sent after reconnect
}

message Ack {
  enum Result {
    OK = 0; BAD_SIGNATURE = 1; REPLAY = 2; EXPIRED = 3; NOT_SYNCED = 4;
    BUSY = 5; HW_FAULT = 6; INVALID = 7; NOT_ALLOWED = 8; DUPLICATE_OK = 9;
  }
  uint32 req_id = 1;
  Result result = 2;
  uint32 detail = 3;
}

message Status {
  string fw_version     = 1;
  uint32 uptime_s       = 2;
  uint32 vin_mv         = 3;
  sint32 temp_c         = 4;
  uint32 inputs         = 5;  // 2 bits per input: 00 normal 01 active 10 short 11 cut
  uint32 relays         = 6;  // bit0 relay1, bit1 relay2
  uint32 flags          = 7;  // bit0 synced, bit1 tamper, bit2 power_fail, bit3 maxon_bypass, bit4 fault
  uint32 comm           = 8;  // 0 CAN, 1 LTE, 2 ETH, 3 LoRa
  sint32 rssi_dbm       = 9;  // LTE/LoRa
  uint32 can_tec        = 10;
  uint32 can_rec        = 11;
  uint32 config_version = 12;
}

message Hello {
  string fw_version   = 1;
  uint32 hw_rev       = 2;
  uint32 module_type  = 3;
  bytes  serial       = 4;
  uint64 last_cmd_ctr = 5;   // last accepted server counter (resync)
}
```

**Typical size:** Body 30–50 B + signature 64 B + overhead ≈ **100–120 bytes**.

## B4. Message Catalog

| Message | Direction | Signed | Priority | MQTT QoS | CAN channel |
|---|---|---|---|---|---|
| Event ALARM / TAMPER / DOOR_FORCED / INPUT_FAULT | node→srv | ✅ node key | Highest | 1 | ALARM (0x080) |
| Event other (door opened/closed, power...) | node→srv | ✅ | Normal | 1 | EVENT (0x280) |
| Ack | node→srv | ✅ | Normal | 1 | EVENT (0x280) |
| Status | node→srv | ✅ | Low | 1, retained | EVENT (0x280) |
| Hello | node→srv | ✅ | Normal | 1 | EVENT (0x280) |
| Heartbeat | node→gw | ❌ | Low | – (MQTT keepalive) | HB (0x380) raw |
| DoorCommand / RelayCommand | srv→node | ✅ server key | High | 1, **never retained** | CMD (0x180) |
| TimeSync / ConfigSet / Ping | srv→node | ✅ | Normal | 1 | CMD (0x180) |
| OtaControl | srv→node | ✅ | Low | 1 | CMD (0x180) + OTA (0x600) |
| Beacon | gw→all | ❌ | – | – | 0x000 raw |
| Delivery ack | gw→node | ❌ | – | (PUBACK) | DACK (0x400) raw |

## B5. Security Rules

| Topic | Rule |
|---|---|
| Keys | Each node has its own P-256 key generated **inside ATECC608B** (never exported). Server command-signing keys (`key_id` 1 and 2 for rotation) have their **public** halves stored in ATECC608B slots. A separate firmware-signing key is used for OTA. |
| Transport | MQTT: TLS 1.2 with **mutual authentication** (node certificate CN = `N017`). CAN: physical bus, protected by signatures. |
| Signature | `ECDSA-P256( SHA-256("FSEC1" ‖ body) )`. Server rejects unsigned or invalid node messages. Node rejects invalid commands (`BAD_SIGNATURE`). |
| Replay | Node stores `last_cmd_counter` in encrypted NVS. Accepts only `counter > last`. Server stores `last_evt_counter` per node and drops `counter ≤ last` (logged as duplicate). |
| Idempotency | Node caches the last 8 `(counter, req_id, result)`. Same counter + same req_id → resend the cached Ack (`DUPLICATE_OK`), **no second action**. |
| Expiry | Commands are valid for 30 s (CAN) / 60 s (4G). Node must be time-synced, otherwise `NOT_SYNCED`. Only a signed `TimeSync` sets the time, never the beacon. |
| Counter persistence | Node writes a "high-water mark" every 256 messages (on boot: counter = HWM, store HWM + 256) to reduce flash wear. |
| Time sync | On `Hello` the server sends TimeSync, then every 10 minutes. |
| Authorization | Only the backend has the command-signing key. Users never talk to nodes directly. |

## B6. MQTT

### Topics

```
fsec/v1/{site}/node/{node}/evt/{kind}    node→srv  QoS1         kind: alarm|door|input|power|tamper|boot|fault
fsec/v1/{site}/node/{node}/ack           node→srv  QoS1
fsec/v1/{site}/node/{node}/status        node→srv  QoS1 retain  every 60 s (4G) / 5 min (via gateway)
fsec/v1/{site}/node/{node}/hello         node→srv  QoS1
fsec/v1/{site}/node/{node}/link          retained JSON  "online/offline" (LWT for 4G/ETH; gateway publishes for CAN)
fsec/v1/{site}/node/{node}/cmd           srv→node  QoS1, NEVER retained
fsec/v1/{site}/node/{node}/ota           srv→node  QoS1
fsec/v1/{site}/gw/{gw}/status            gw→srv    QoS1 retain (JSON)
fsec/v1/{site}/debug/{node}              srv only  decoded JSON mirror (dev builds only)
```

All `evt/ack/status/hello/cmd/ota` payloads are the **binary Envelope**. `link` and `gw/status` are small JSON:

```json
{ "state": "online", "via": "can", "gw": "gw01", "ts": 1767225600 }
```

### Client settings

| Setting | 4G node | Ethernet node / gateway | Backend |
|---|---|---|---|
| Client ID | `N017` | `N017` / `gw01` | `backend-1` |
| Keepalive | 60 s | 30 s | 30 s |
| Clean session | true | true | false |
| LWT | `.../node/N017/link` → `{"state":"offline"}` retained | same / `gw/gw01/status` | – |
| Port | 8883 (mTLS) | 8883 (mTLS) | 1883 (internal network only) |

> Commands use a clean session on purpose: an old queued command will expire anyway and must not be executed late.

### Broker ACL (Mosquitto, `%u` = certificate CN)

```
# Nodes: only their own topics
pattern write fsec/v1/f1/node/%u/evt/#
pattern write fsec/v1/f1/node/%u/ack
pattern write fsec/v1/f1/node/%u/status
pattern write fsec/v1/f1/node/%u/hello
pattern write fsec/v1/f1/node/%u/link
pattern read  fsec/v1/f1/node/%u/cmd
pattern read  fsec/v1/f1/node/%u/ota

# Gateways: relay for CAN nodes
user gw01
topic readwrite fsec/v1/f1/node/+/#
topic write     fsec/v1/f1/gw/gw01/status
```

## B7. CAN Bus

### Physical & timing

| Parameter | Value |
|---|---|
| Bit rate | **20 kbit/s** default (≤ 2.5 km). Configurable: 10k (≤ 5 km), 50k (≤ 1 km), 125k (≤ 500 m) |
| ESP32-S3 TWAI timing (80 MHz) | `brp = 200, tseg1 = 16, tseg2 = 3, sjw = 3` → 20 tq/bit, **sample point 85 %** |
| Frames | Classic CAN, 11-bit IDs, DLC always 8 (pad `0xAA`) |
| Nodes per segment | ≤ 32 recommended (transceiver limit higher; keep bus load low) |
| Bus-off | Automatic recovery after 1 s, report `can_tec/rec` in Status |

```c
// ESP-IDF v6 TWAI (new driver API)
twai_onchip_node_config_t cfg = {
    .io_cfg = { .tx = GPIO_NUM_15, .rx = GPIO_NUM_16 },
    .bit_timing.bitrate = 20000,
    .tx_queue_depth = 16,
};
ESP_ERROR_CHECK(twai_new_node_onchip(&cfg, &can));
// For a fixed 85% sample point, apply twai_timing_advanced_config_t {brp=200,tseg1=16,tseg2=3,sjw=3}
```

### CAN ID map (`ID = function << 7 | node`, node 1–127)

Lower ID = higher priority, so alarms always win arbitration.

| Func | ID range | Direction | Use | Type |
|---|---|---|---|---|
| 0x0 | `0x000` | gw → all | Beacon (1 s) | raw SF |
| 0x1 | `0x081–0x0FF` | node → gw | **ALARM** data (alarm, tamper, forced, input fault) | ISO-TP |
| 0x2 | `0x101–0x17F` | gw → node | Flow control for 0x1 | ISO-TP FC |
| 0x3 | `0x181–0x1FF` | gw → node | **COMMAND** data | ISO-TP |
| 0x4 | `0x201–0x27F` | node → gw | Flow control for 0x3 | ISO-TP FC |
| 0x5 | `0x281–0x2FF` | node → gw | EVENT / ACK / STATUS / HELLO data | ISO-TP |
| 0x6 | `0x301–0x37F` | gw → node | Flow control for 0x5 | ISO-TP FC |
| 0x7 | `0x381–0x3FF` | node → gw | Heartbeat | raw SF |
| 0x8 | `0x401–0x47F` | gw → node | Delivery ack | raw SF |
| 0x9–0xB | – | – | Reserved | – |
| 0xC | `0x601–0x67F` | gw → node | OTA data (lowest priority) | ISO-TP |
| 0xD | `0x681–0x6FF` | node → gw | OTA flow control | ISO-TP FC |
| 0xE–0xF | – | – | Reserved / diagnostics | – |

### ISO-TP parameters

| Parameter | Value |
|---|---|
| Addressing | Normal (separate ID per direction, table above) |
| Block size (BS) | 0 (sender sends all consecutive frames after one FC) |
| STmin | 0 |
| N_As / N_Bs / N_Cr timeouts | 1000 ms |
| Max message | 512 B (OTA chunks 256 B) |
| Library | `isotp-c` (portable C) on ESP32. Linux prototype: SocketCAN `can-isotp` + `python-can-isotp` |

### Raw frame layouts

**Beacon `0x000` (gateway, every 1 s), informational only, not trusted for security:**

| Byte | Content |
|---|---|
| 0 | Gateway number |
| 1 | Flags: bit0 server link up, bit1 maintenance mode |
| 2–5 | Unix time (uint32 LE), display only |
| 6 | Sequence |
| 7 | Reserved |

Node behavior: no beacon for 5 s → LED_COMM blinks, state "gateway lost", events are buffered.

**Heartbeat `0x380+n` (node, every 10 s):**

| Byte | Content |
|---|---|
| 0 | bit0 time synced, bit1 fault, bit2 tamper, bit3 power fail, bit4 config ok, bits5–7 role |
| 1 | Inputs, 2 bits each (IN1 = bits 0–1): 00 normal, 01 active, 10 short, 11 cut |
| 2 | bit0 relay1, bit1 relay2, bit2 ISO1, bit3 ISO2, bit4 max-on bypass |
| 3 | Sequence |
| 4–5 | VIN in 10 mV units (uint16 LE) |
| 6 | Temperature °C (int8) |
| 7 | CAN TX error counter (saturating) |

**Delivery ack `0x400+n` (gateway, after broker PUBACK):**

| Byte | Content |
|---|---|
| 0 | Type: 1 = delivered |
| 1–4 | Event counter (low 32 bits, LE) |
| 5 | Status: 0 OK, 1 broker down (buffer and retry later) |
| 6–7 | Reserved |

### Bus load (20 kbit/s, 8-byte frame ≈ 138 bits ≈ 6.9 ms)

| Traffic | Load |
|---|---|
| Beacon 1/s | 0.7 % |
| 32 heartbeats / 10 s | 2.2 % |
| One alarm event (~120 B → 19 frames incl. FC) | ~130 ms burst |
| One door command + ack | ~250 ms total |
| OTA throttle | ≤ 30 % (≈ 45 min per node for a 1.5 MB image) |

> If many sensors share a segment and alarm at the same time, events queue (~130 ms each). For dense segments use 50 kbit/s (shorter cable) or split the segment.
> For CAN-node OTA, prefer **Wi-Fi maintenance mode** (server sends `OtaControl` with a URL, node joins a maintenance SSID) where Wi-Fi coverage exists.

### Gateway behavior

1. CAN → MQTT: reassemble ISO-TP. Publish the **unchanged Envelope** to the topic for the source node (`ALARM` channel → `evt/alarm`; EVENT channel → topic by message type, decoded only from the unsigned outer header or forwarded to `evt/any`). After PUBACK, send the delivery ack.
2. MQTT → CAN: subscribe `fsec/v1/f1/node/+/cmd` and `/ota`. Forward only to nodes known on its segment (learned from heartbeats plus server config).
3. Publish `link` online/offline per node (offline after 3 missed heartbeats = 30 s).
4. Publish its own `gw/gw01/status` with bus load, error counters and the node list.

> Simplest gateway topic rule: the gateway forwards everything from channel 0x1 to `evt/alarm` and from channel 0x5 to `evt/any`. The backend decodes the Body and routes it.

## B8. Sequences

### Alarm (target < 2 s to phone)

```
t=0     Sensor relay opens → IN1 = ALARM (debounced 60 ms)
t≈60ms  Node builds Event(ALARM), ATECC signs (~50 ms)
t≈130ms ISO-TP on 0x08x (~130 ms)        | 4G: MQTT publish (~150–400 ms)
t≈300ms Gateway → MQTT → backend verifies signature, dedupes by (node, counter)
t≈350ms Rules engine → camera sessions, Frigate event, FCM push
t≈0.8–1.5s Phone shows full-screen alarm
Node retries every 1 s until delivery ack/PUBACK (30 s), then every 10 s.
```

### Door open

```
App → POST /doors/12/open (JWT + biometric signature)
Backend: RBAC → audit → counter = last+1 (atomic DB) → Body{DoorCommand PULSE_OPEN 5000ms, expires = now+30s} → sign
→ MQTT .../node/N012/cmd → (gateway) → CAN 0x18C
Node: verify sig → counter > last? → synced & not expired? → timer trigger → relay ON
Node → Ack(OK) on 0x28C → backend → app "Unlocked"
Node → Event DOOR_OPENED (reed) → Event DOOR_CLOSED → app shows real state
Relay OFF after pulse_ms; door open > 30 s → DOOR_HELD_OPEN alarm
```

### Boot / reconnect

```
Node boots → Hello (last_cmd_ctr, fw, hw) → server: TimeSync + ConfigSet (if config_version differs)
→ node uploads buffered events (buffered = true, original event_ts) → Status
```

### Offline policy (node cannot reach server)

- Door stays **locked** (fail-secure). Remote commands are impossible.
- Exit button still releases the door if `exit_button_offline = true` (default, for safe exit).
- Events go to a flash ring buffer (256 entries) and are uploaded after reconnect.

## B9. Timeouts & Retries

| Item | CAN | 4G |
|---|---|---|
| Command ack timeout (server) | 2 s | 5 s |
| Command retries (same Envelope) | 2 | 2 |
| Command validity (expires) | 30 s | 60 s |
| Event retry (no delivery ack) | 1 s × 30, then 10 s | MQTT QoS1 + 1 s app retry |
| Node offline detection | 3 missed heartbeats (30 s) | LWT (~90 s) |
| Status period | 5 min | 60 s |

## B10. Versioning

- `Body.ver` = 1, topic prefix `fsec/v1`.
- Protobuf changes are **additive only** (new field numbers). Never reuse numbers.
- Breaking change → `fsec/v2` topics and run both versions during migration.

## B11. Server ↔ App Events (summary)

WebSocket `wss://server/api/v1/events` (JWT). JSON:

```json
{ "type": "alarm.raised", "alarmId": "a-981", "sensor": "S03",
  "cameras": ["cam02","cam05","cam07"], "ts": "2026-01-01T10:22:31Z" }

{ "type": "camera.session.started", "cameraId": "cam04",
  "source": "MANUAL", "startedBy": "u-2", "state": "LIVE" }

{ "type": "door.state", "doorId": "D12", "state": "OPEN", "locked": false }

{ "type": "node.link", "node": "N017", "state": "offline", "via": "can" }
```

---

# PART C: How-To: Central Server Setup

## C1. Hardware & OS

1. Install **Ubuntu Server 24.04 LTS** on the NVMe drive. Set a static IP (example: `10.10.0.5`), timezone and NTP.
2. Create RAID 1 from the 2 × 8 TB disks and mount them at `/srv/media`:

```bash
sudo apt install -y mdadm smartmontools
sudo mdadm --create /dev/md0 --level=1 --raid-devices=2 /dev/sda /dev/sdb
sudo mkfs.ext4 -L media /dev/md0
sudo mkdir -p /srv/media && echo 'LABEL=media /srv/media ext4 defaults,noatime 0 2' | sudo tee -a /etc/fstab
sudo mount -a
sudo mdadm --detail --scan | sudo tee -a /etc/mdadm/mdadm.conf
```

3. Install Docker:

```bash
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker $USER
```

4. Install **NUT** (Network UPS Tools) so the server shuts down cleanly on a long power cut.

## C2. Network Plan

| VLAN | Subnet | Members | Internet |
|---|---|---|---|
| 10 MGMT | 10.10.0.0/24 | Server, switch, router | Updates only |
| 20 CAMERAS | 10.20.0.0/24 | 16 cameras | **Blocked** |
| 30 IOT | 10.30.0.0/24 | Gateways, Ethernet nodes | Blocked |
| 40 USERS | 10.40.0.0/24 | Staff Wi-Fi (phones) | Yes |
| WG | 10.99.0.0/24 | Remote phones (WireGuard) | – |

**Firewall rules (default deny):**
- VLAN20 → server only (RTSP pulled by server). Cameras can't reach anything else.
- VLAN30 → server :8883.
- VLAN40 + WG → server :443 (API) and :8555 (WebRTC) only.
- Internet → router :51820/udp (WireGuard) and, for 4G nodes, :8883 forwarded to the server (mTLS only, rate-limited). If your mobile operator offers a **private APN**, use it instead.

On the cameras, disable UPnP, P2P/cloud, and unused services. Set strong passwords.

## C3. Folder Layout

```
/opt/fsec/
├── docker-compose.yml
├── .env
├── frigate/config/config.yml
├── mosquitto/
│   ├── config/mosquitto.conf
│   ├── config/acl_devices
│   ├── config/acl_internal
│   ├── config/passwd
│   ├── certs/ (ca.crt, server.crt, server.key)
│   └── data/
├── pki/            ← CA + signing keys (root-only, back up offline!)
├── caddy/Caddyfile
├── backend/        ← API source (C8)
└── postgres/data/
```

## C4. `.env` and `docker-compose.yml`

```bash
# /opt/fsec/.env  (chmod 600)
TZ=Asia/Tehran
POSTGRES_PASSWORD=change-me-long-random
FRIGATE_RTSP_PASSWORD=camera-password
FRIGATE_RESTREAM_PASSWORD=restream-password
MQTT_BACKEND_PASSWORD=change-me
JWT_SECRET=change-me-64-random-bytes
```

```yaml
# /opt/fsec/docker-compose.yml
services:
  frigate:
    image: ghcr.io/blakeblackshear/frigate:stable
    restart: unless-stopped
    shm_size: "512mb"                    # ~16 cams; follow Frigate's shm formula
    devices:
      - /dev/dri/renderD128:/dev/dri/renderD128   # Intel iGPU (decode + OpenVINO)
    volumes:
      - /etc/localtime:/etc/localtime:ro
      - ./frigate/config:/config
      - /srv/media:/media/frigate
      - type: tmpfs
        target: /tmp/cache
        tmpfs: { size: 1000000000 }
    environment:
      - FRIGATE_RTSP_PASSWORD=${FRIGATE_RTSP_PASSWORD}
      - FRIGATE_RESTREAM_PASSWORD=${FRIGATE_RESTREAM_PASSWORD}
    ports:
      - "8971:8971"          # Frigate UI (authenticated), admin LAN only
      - "8554:8554"          # RTSP restream (password protected)
      - "8555:8555/tcp"      # WebRTC
      - "8555:8555/udp"
    # 5000 (internal API) and 1984 (go2rtc API) are NOT published, only the backend uses them

  mosquitto:
    image: eclipse-mosquitto:2
    restart: unless-stopped
    volumes:
      - ./mosquitto/config:/mosquitto/config:ro
      - ./mosquitto/certs:/mosquitto/certs:ro
      - ./mosquitto/data:/mosquitto/data
    ports:
      - "8883:8883"          # nodes + gateways (mTLS)

  postgres:
    image: postgres:16
    restart: unless-stopped
    environment:
      POSTGRES_DB: fsec
      POSTGRES_USER: fsec
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}
    volumes:
      - ./postgres/data:/var/lib/postgresql/data

  backend:
    build: ./backend
    restart: unless-stopped
    env_file: .env
    environment:
      DATABASE_URL: postgresql+asyncpg://fsec:${POSTGRES_PASSWORD}@postgres/fsec
      MQTT_HOST: mosquitto
      MQTT_PORT: 1883
      FRIGATE_API: http://frigate:5000/api
      GO2RTC_API: http://frigate:1984/api
      FCM_CREDENTIALS: /run/secrets/fcm.json
      CMD_SIGNING_KEY: /run/secrets/cmd_sign_k1.pem
    secrets: [fcm.json, cmd_sign_k1.pem]
    depends_on: [postgres, mosquitto, frigate]

  caddy:
    image: caddy:2
    restart: unless-stopped
    ports: ["443:443"]
    volumes:
      - ./caddy/Caddyfile:/etc/caddy/Caddyfile:ro
      - caddy_data:/data

secrets:
  fcm.json:        { file: ./pki/fcm-service-account.json }
  cmd_sign_k1.pem: { file: ./pki/cmd_sign_k1.pem }

volumes:
  caddy_data:
```

## C5. Frigate `config.yml`

```yaml
mqtt:
  enabled: true
  host: mosquitto
  port: 1883
  user: frigate
  password: "{FRIGATE_MQTT_PASSWORD}"   # add FRIGATE_MQTT_PASSWORD to .env + environment

ffmpeg:
  hwaccel_args: preset-vaapi

detectors:
  ov:
    type: openvino
    device: GPU

model:
  width: 300
  height: 300
  input_tensor: nhwc
  input_pixel_format: bgr
  path: /openvino-model/ssdlite_mobilenet_v2.xml
  labelmap_path: /openvino-model/coco_91cl_bkgr.txt

record:                         # Frigate 0.16+ syntax (older: record.retain.days)
  enabled: true
  continuous: { days: 7 }       # 24/7 footage
  motion:     { days: 14 }      # motion segments kept longer
  alerts:
    pre_capture: 5
    post_capture: 15
    retain: { days: 30, mode: motion }
  detections:
    retain: { days: 30, mode: motion }

snapshots:
  enabled: true
  retain: { default: 30 }

go2rtc:
  rtsp:
    username: app
    password: "{FRIGATE_RESTREAM_PASSWORD}"
  webrtc:
    candidates:
      - 10.10.0.5:8555          # LAN
      - 10.99.0.1:8555          # WireGuard address of server/router path
      - stun:8555
  streams:
    cam01_main: rtsp://admin:{FRIGATE_RTSP_PASSWORD}@10.20.0.11:554/stream1   # path depends on brand
    cam01_sub:  rtsp://admin:{FRIGATE_RTSP_PASSWORD}@10.20.0.11:554/stream2
    cam02_main: rtsp://admin:{FRIGATE_RTSP_PASSWORD}@10.20.0.12:554/stream1
    cam02_sub:  rtsp://admin:{FRIGATE_RTSP_PASSWORD}@10.20.0.12:554/stream2
    # ... cam03 – cam16

cameras:
  cam01:
    ffmpeg:
      inputs:
        - path: rtsp://127.0.0.1:8554/cam01_main
          input_args: preset-rtsp-restream
          roles: [record]
        - path: rtsp://127.0.0.1:8554/cam01_sub
          input_args: preset-rtsp-restream
          roles: [detect]
    detect: { width: 640, height: 360, fps: 5 }
  cam02:
    ffmpeg:
      inputs:
        - path: rtsp://127.0.0.1:8554/cam02_main
          input_args: preset-rtsp-restream
          roles: [record]
        - path: rtsp://127.0.0.1:8554/cam02_sub
          input_args: preset-rtsp-restream
          roles: [detect]
    detect: { width: 640, height: 360, fps: 5 }
  # ... cam03 – cam16 (generate with a small script)
```

**Backend ↔ Frigate calls used:**

| Purpose | Call |
|---|---|
| Sensor alarm → keep high-quality footage | `POST /api/events/{camera}/sensor_alarm/create` body `{"sub_label":"S03","duration":60,"include_recording":true}` |
| Save clip for phone | `POST /api/export/{camera}/start/{ts}/end/{ts}` → download from the exports list |
| Snapshot | `GET /api/{camera}/latest.jpg?h=360` |
| WebRTC for app | `POST {GO2RTC_API}/webrtc?src=cam01_sub` (SDP offer → answer), proxied by backend after permission check |

## C6. Mosquitto & Certificates

```conf
# mosquitto/config/mosquitto.conf
per_listener_settings true
persistence true
persistence_location /mosquitto/data/
log_dest stdout

# Internal listener: backend + Frigate only (docker network, not published)
listener 1883 0.0.0.0
allow_anonymous false
password_file /mosquitto/config/passwd
acl_file /mosquitto/config/acl_internal

# Devices: mutual TLS
listener 8883 0.0.0.0
allow_anonymous false
cafile   /mosquitto/certs/ca.crt
certfile /mosquitto/certs/server.crt
keyfile  /mosquitto/certs/server.key
require_certificate true
use_identity_as_username true
tls_version tlsv1.2
acl_file /mosquitto/config/acl_devices
```

```conf
# acl_internal
user backend
topic readwrite fsec/#
user frigate
topic readwrite frigate/#
```

`acl_devices`: see Part B6. Create internal users:

```bash
docker compose run --rm mosquitto mosquitto_passwd -c /mosquitto/config/passwd backend
docker compose run --rm mosquitto mosquitto_passwd    /mosquitto/config/passwd frigate
```

**PKI (P-256), run in `/opt/fsec/pki`, keep `ca.key` offline:**

```bash
# Root CA
openssl ecparam -name prime256v1 -genkey -noout -out ca.key
openssl req -x509 -new -key ca.key -sha256 -days 3650 -subj "/CN=FSEC Root CA" -out ca.crt

# MQTT server certificate
openssl ecparam -name prime256v1 -genkey -noout -out server.key
openssl req -new -key server.key -subj "/CN=mqtt.fsec.local" -out server.csr
printf "subjectAltName=DNS:mqtt.fsec.local,IP:10.10.0.5\nextendedKeyUsage=serverAuth\n" > server.ext
openssl x509 -req -in server.csr -CA ca.crt -CAkey ca.key -CAcreateserial \
  -days 825 -sha256 -extfile server.ext -out server.crt

# Node certificate: the CSR is generated INSIDE the ATECC608B by the provisioning firmware
printf "extendedKeyUsage=clientAuth\n" > node.ext
openssl x509 -req -in N017.csr -CA ca.crt -CAkey ca.key -days 3650 -sha256 \
  -extfile node.ext -out N017.crt

# Command-signing key (backend). Public key is written into every node's ATECC slot.
openssl ecparam -name prime256v1 -genkey -noout -out cmd_sign_k1.pem
openssl ec -in cmd_sign_k1.pem -pubout -out cmd_sign_k1.pub.pem

cp ca.crt server.crt server.key ../mosquitto/certs/
```

> Best practice: keep the command-signing key in a **TPM or YubiHSM 2**, or at least in a separate small "signer" container. Back up `pki/` encrypted and offline.

**Test with a node certificate:**

```bash
mosquitto_sub -h 10.10.0.5 -p 8883 --cafile ca.crt --cert N017.crt --key N017.key \
  -t 'fsec/v1/f1/node/N017/cmd' -v
```

## C7. Database Schema (core tables)

```sql
CREATE TABLE users (id uuid PRIMARY KEY, username text UNIQUE NOT NULL, pw_hash text NOT NULL,
  role text NOT NULL CHECK (role IN ('ADMIN','OPERATOR','VIEWER')), totp_secret text, active bool DEFAULT true);

CREATE TABLE app_devices (id uuid PRIMARY KEY, user_id uuid REFERENCES users, public_key text NOT NULL,
  fcm_token text, approved bool DEFAULT false, last_seen timestamptz);

CREATE TABLE nodes (id int PRIMARY KEY, name text UNIQUE, role text, comm text, gateway text,
  cert_fingerprint text, fw_version text, config_version int DEFAULT 0,
  last_cmd_counter bigint DEFAULT 0, last_evt_counter bigint DEFAULT 0,
  link_state text, last_seen timestamptz);

CREATE TABLE doors   (id text PRIMARY KEY, name text, node_id int REFERENCES nodes, relay int DEFAULT 1,
  pulse_ms int DEFAULT 5000, camera_id text);
CREATE TABLE cameras (id text PRIMARY KEY, name text, go2rtc_main text, go2rtc_sub text,
  poe_port int, poe_controlled bool DEFAULT false);
CREATE TABLE sensors (id text PRIMARY KEY, name text, node_id int REFERENCES nodes, input int DEFAULT 1);
CREATE TABLE sensor_camera_rules (sensor_id text REFERENCES sensors, camera_id text REFERENCES cameras,
  PRIMARY KEY (sensor_id, camera_id));

CREATE TABLE camera_sessions (id uuid PRIMARY KEY, camera_id text REFERENCES cameras,
  source text CHECK (source IN ('ALARM','MANUAL')), user_id uuid, alarm_id uuid,
  started_at timestamptz DEFAULT now(), last_viewed timestamptz DEFAULT now(), ended_at timestamptz);

CREATE TABLE alarms (id uuid PRIMARY KEY, sensor_id text, node_id int, type text,
  raised_at timestamptz, acked_by uuid, acked_at timestamptz, cleared_at timestamptz);

CREATE TABLE door_commands (id bigserial PRIMARY KEY, req_id int, door_id text, user_id uuid,
  action text, counter bigint, sent_at timestamptz, result text, acked_at timestamptz);

CREATE TABLE audit_log (id bigserial PRIMARY KEY, ts timestamptz DEFAULT now(), user_id uuid,
  action text, target text, detail jsonb, ip inet);
```

## C8. Backend (FastAPI): Structure & Key Code

```
backend/
├── Dockerfile
├── requirements.txt      # fastapi uvicorn[standard] sqlalchemy asyncpg aiomqtt httpx
│                         # cryptography protobuf firebase-admin pyjwt argon2-cffi pyotp
└── app/
    ├── main.py
    ├── config.py
    ├── api/        auth.py cameras.py sessions.py doors.py alarms.py rules.py users.py
    │               devices.py webrtc.py ws.py
    ├── core/       security.py (JWT, argon2, TOTP) rbac.py audit.py
    ├── domain/     session_manager.py rules_engine.py door_service.py alarm_service.py
    └── infra/      db.py mqtt_bridge.py frigate.py poe.py fcm.py signer.py proto/fsec_pb2.py
```

**Signer (`infra/signer.py`):**

```python
import hashlib
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec
from cryptography.hazmat.primitives.asymmetric.utils import decode_dss_signature, encode_dss_signature
from .proto import fsec_pb2 as pb

PREFIX = b"FSEC1"

class Signer:
    def __init__(self, pem_path: str, key_id: int = 1):
        self.key = serialization.load_pem_private_key(open(pem_path, "rb").read(), None)
        self.key_id = key_id

    def wrap(self, body: pb.Body) -> bytes:
        raw = body.SerializeToString()
        der = self.key.sign(PREFIX + raw, ec.ECDSA(hashes.SHA256()))
        r, s = decode_dss_signature(der)
        sig = r.to_bytes(32, "big") + s.to_bytes(32, "big")
        return pb.Envelope(body=raw, sig=sig, key_id=self.key_id).SerializeToString()

def verify_node(env_bytes: bytes, node_pubkey) -> pb.Body:
    env = pb.Envelope.FromString(env_bytes)
    r, s = int.from_bytes(env.sig[:32], "big"), int.from_bytes(env.sig[32:], "big")
    node_pubkey.verify(encode_dss_signature(r, s), PREFIX + env.body, ec.ECDSA(hashes.SHA256()))
    return pb.Body.FromString(env.body)   # raises InvalidSignature before this if bad
```

**Door command (`domain/door_service.py`):**

```python
async def open_door(door_id: str, user, req_signature_ok: bool):
    if user.role not in ("ADMIN", "OPERATOR") or not req_signature_ok:
        raise Forbidden()
    door = await repo.get_door(door_id)
    counter = await repo.next_cmd_counter(door.node_id)        # UPDATE ... RETURNING (atomic)
    req_id = await repo.new_req_id()
    body = pb.Body(ver=1, site=1, src=0xFFFF, dst=door.node_id, counter=counter,
                   time=now(), expires=now() + ttl(door), req_id=req_id,
                   door_cmd=pb.DoorCommand(door=door.relay, action=pb.DoorCommand.PULSE_OPEN,
                                           pulse_ms=door.pulse_ms, user_ref=hash_user(user.id)))
    env = signer.wrap(body)
    await audit.log(user, "door.open.request", door_id, {"req_id": req_id})
    return await mqtt.request(f"fsec/v1/f1/node/N{door.node_id:03d}/cmd", env,
                              req_id=req_id, timeout=ack_timeout(door), retries=2)
```

**Camera session manager (`domain/session_manager.py`):**

```python
class SessionManager:
    async def start_manual(self, camera_id: str, user):
        s = await repo.find_active(camera_id, source="MANUAL", user_id=user.id)
        if s: return s                                     # idempotent
        if await repo.active_count(camera_id) == 0 and cam(camera_id).poe_controlled:
            await poe.power_on(cam(camera_id).poe_port)    # state STARTING until RTSP ok
        s = await repo.create(camera_id, "MANUAL", user_id=user.id)
        await ws.publish_to(user.id, "camera.session.started", s)
        await audit.log(user, "camera.start", camera_id)
        return s

    async def start_alarm(self, camera_id: str, alarm_id):
        s = await repo.create(camera_id, "ALARM", alarm_id=alarm_id)
        await frigate.create_event(camera_id, "sensor_alarm", duration=60)
        await ws.publish_all("camera.session.started", s)

    async def stop(self, session):
        await repo.end(session.id)
        if await repo.active_count(session.camera_id) == 0 and cam(session.camera_id).poe_controlled:
            await poe.power_off(cam(session.camera_id).poe_port)   # reference counting
        await ws.publish("camera.session.stopped", session)

    async def reaper(self):                         # every 30 s
        for s in await repo.idle_manual(minutes=10):     # nobody viewing
            await self.stop(s)
```

**Alarm handling (`domain/alarm_service.py`):**

```python
async def on_node_event(node_id: int, body: pb.Body):
    if not await repo.accept_evt_counter(node_id, body.counter):   # dedupe / replay
        return
    ev = body.event
    if ev.type in (pb.Event.ALARM, pb.Event.TAMPER, pb.Event.DOOR_FORCED,
                   pb.Event.INPUT_FAULT_CUT, pb.Event.INPUT_FAULT_SHORT):
        alarm = await repo.create_alarm(node_id, ev)
        cams = await rules.cameras_for(node_id, ev.input)
        await asyncio.gather(*(sessions.start_alarm(c, alarm.id) for c in cams))
        await fcm.send_alarm_to_all(alarm, cams)     # data message, priority high, ttl 60 s
        await ws.publish_all("alarm.raised", alarm, cameras=cams)
```

**WebRTC proxy (`api/webrtc.py`), so the app never talks to go2rtc without a permission check:**

```python
@router.post("/cameras/{cam_id}/webrtc", response_class=PlainTextResponse)
async def webrtc(cam_id: str, request: Request, quality: str = "sub", user=Depends(current_user)):
    if not await sessions.user_may_view(user, cam_id):      # active session exists
        raise HTTPException(403)
    src = f"{cam_id}_{'main' if quality == 'main' else 'sub'}"
    offer = await request.body()
    async with httpx.AsyncClient() as c:
        r = await c.post(f"{settings.GO2RTC_API}/webrtc", params={"src": src},
                         content=offer, headers={"Content-Type": "application/sdp"})
    await sessions.touch(user, cam_id)                       # resets idle timer
    return r.text                                            # SDP answer
```

**REST endpoints (summary):**

```
POST /api/v1/auth/login            {username,password,totp} → {access, refresh}
POST /api/v1/auth/refresh
POST /api/v1/devices/register      {publicKey, fcmToken} → admin approves
GET  /api/v1/cameras               list + state
POST /api/v1/cameras/{id}/sessions / DELETE ...      start/stop (manual)
GET  /api/v1/sessions/active
POST /api/v1/cameras/{id}/webrtc?quality=sub|main    SDP offer → answer
GET  /api/v1/cameras/{id}/snapshot
POST /api/v1/cameras/{id}/clips    {start,end} → export job id
GET  /api/v1/clips/{job}           status + download URL
GET  /api/v1/doors / POST /api/v1/doors/{id}/challenge / POST /api/v1/doors/{id}/open
GET  /api/v1/alarms / POST /api/v1/alarms/{id}/ack
GET/PUT /api/v1/rules              sensor ↔ cameras (ADMIN)
GET  /api/v1/nodes                 health
WS   /api/v1/events
```

## C9. Caddy (HTTPS on the LAN)

```caddyfile
fsec.local, 10.10.0.5 {
    tls internal
    encode gzip
    reverse_proxy /api/* backend:8000
}
nvr.fsec.local {
    tls internal
    reverse_proxy https://frigate:8971 {
        transport http { tls_insecure_skip_verify }
    }
}
```

Export Caddy's internal root CA (`/data/caddy/pki/authorities/local/root.crt`) and **pin it in the Android app** (Part D).

## C10. Remote Access (WireGuard)

- **Preferred:** run WireGuard on the router (MikroTik / OPNsense). One peer per phone, `AllowedIPs = 10.10.0.0/24` (server only).
- On phones: install the WireGuard app, import the QR code, and enable **Always-on VPN** in Android settings.
- Alternative: `wg-easy` container on the server, or Tailscale (easier, uses a third-party coordinator).

## C11. Firebase Cloud Messaging

1. Create a Firebase project and add the Android app (package name).
2. Download `google-services.json` (app) and a **service account JSON** (server → `pki/fcm-service-account.json`).
3. Send **data-only, priority high, TTL 60 s** messages. The app always shows a notification, which keeps high-priority delivery reliable.

```python
messaging.send(messaging.Message(
    token=t, data={"type": "alarm", "alarmId": str(a.id), "cams": ",".join(cams)},
    android=messaging.AndroidConfig(priority="high", ttl=60)))
```

## C12. Start & Verify

```bash
cd /opt/fsec
docker compose up -d
docker compose ps
docker compose logs -f frigate      # streams connect? detector loaded?
```

| Check | How |
|---|---|
| Frigate | `https://nvr.fsec.local` → all cameras visible, recording |
| MQTT | `mosquitto_sub` with node certificate (C6) |
| Backend | `curl -k https://fsec.local/api/v1/health` |
| Alarm path | Run `tools/sim_node.py` (sends a signed ALARM with a test key) → phone alarm < 2 s |
| Door path | Bench door node: open → relay pulse → ack → reed events |
| Remote | Phone on 4G + WireGuard → live grid works |

## C13. Operations

| Task | Tool / schedule |
|---|---|
| DB backup | `pg_dump` nightly → NAS + weekly offsite (encrypted) |
| Config backup | `/opt/fsec` (without media) in a private git repo. `pki/` stored separately, offline |
| Disk health | `smartd` email alerts, `mdadm --monitor` |
| Uptime | Uptime Kuma: Frigate, API, MQTT, each gateway |
| Updates | Pin image tags. Update monthly in a maintenance window. Test Frigate upgrades on a copy of the config first |
| Hardening | SSH keys only, `ufw`/router firewall, unattended security updates, no ports open except listed |

---

# PART D: Android Software Architecture

## D1. Goals

| Goal | Target |
|---|---|
| Alarm to screen | < 2 s after server receives the event, also on a locked phone |
| Active Cameras grid | Up to 16 tiles, maximize/back instantly (< 300 ms) |
| Door command | Biometric confirmed, signed, real state feedback |
| Offline | Cached lists and history. Commands disabled with a clear message |
| Security | No secrets in plain storage, certificate pinning, server-enforced roles |
| Devices | Phones + tablets, Android 8.0+ (minSdk 26), target latest SDK |

## D2. Layers & Dependency Rules

```
┌──────────────────────── UI (Compose) ─────────────────────────┐
│  Screens, components, navigation                              │
├──────────────────── Presentation (ViewModel, MVI) ────────────┤
│  UiState (StateFlow), Intents, one-off Effects                │
├───────────────────────── Domain (pure Kotlin) ────────────────┤
│  Use cases, models, repository interfaces                     │
├──────────────────────────── Data ─────────────────────────────┤
│  Repositories (single source of truth), Room, REST, WebSocket │
├────────────────────────── Platform ───────────────────────────┤
│  Video (WebRTC/Media3), FCM, Keystore/Biometric, WorkManager  │
└───────────────────────────────────────────────────────────────┘
Rules: UI → Presentation → Domain ← Data. Domain depends on nothing Android.
Feature modules never depend on other feature modules.
```

## D3. Module Graph

```
build-logic/                 convention plugins (android-library, compose, hilt, feature)
gradle/libs.versions.toml    version catalog

:app                         Application, MainActivity, NavHost, DI root, flavors
:core:model                  Camera, ActiveCamera, Door, Alarm, Node, User, Role
:core:domain                 use cases + repository interfaces
:core:data                   repository implementations, sync, EventStream router
:core:network                Retrofit, OkHttp, WebSocket, pinning, auth interceptor
:core:database               Room DB, DAOs, entities
:core:datastore              settings (DataStore), session info
:core:security               TokenStore (Keystore + Tink), DeviceKey, BiometricSigner
:core:video                  VideoPlayer API, WebRtcPlayer, RtspPlayer, PlayerPool, SnapshotPlayer
:core:notifications          FCM service, channels, AlarmNotifier, full-screen intent
:core:designsystem           theme, CameraTile, StatusBadge, HoldToConfirmButton
:core:ui                     shared composables (empty/error/loading states)
:core:testing                fakes, test rules, Turbine helpers

:feature:auth                login, TOTP, device registration
:feature:dashboard           summary: active alarms, doors, node health
:feature:cameras             camera list, Start/Stop, multi-select
:feature:liveview            Active Cameras grid + maximize
:feature:alarms              full-screen alarm, list, acknowledge
:feature:playback            timeline, recordings search, export clip
:feature:recordings          saved clips (on phone)
:feature:doors               doors list/detail, open, logs
:feature:devices             node health (online, via CAN/4G, RSSI, voltage)
:feature:rules               ADMIN: sensor ↔ camera links
:feature:users               ADMIN: users, roles, approve devices
:feature:settings            stream quality, live-tile limits, notifications, about
```

**Allowed dependencies**

| Module type | May depend on |
|---|---|
| `:feature:*` | `:core:domain`, `:core:model`, `:core:designsystem`, `:core:ui`, `:core:video` (liveview/playback), `:core:security` (doors) |
| `:core:data` | `:core:domain`, `:core:network`, `:core:database`, `:core:datastore` |
| `:core:domain` | `:core:model` only |
| `:app` | everything (wiring only) |

## D4. Tech Stack (`libs.versions.toml` excerpt)

```toml
[versions]          # use the latest stable versions at project start
kotlin = "<latest>"
agp = "<latest>"
compose-bom = "<latest>"
hilt = "<latest>"
room = "<latest>"
media3 = "<latest>"

[libraries]
compose-bom            = { module = "androidx.compose:compose-bom", version.ref = "compose-bom" }
compose-material3      = { module = "androidx.compose.material3:material3" }
compose-material3-adaptive = { module = "androidx.compose.material3.adaptive:adaptive" }
navigation-compose     = { module = "androidx.navigation:navigation-compose" }
lifecycle-runtime-compose = { module = "androidx.lifecycle:lifecycle-runtime-compose" }
hilt-android           = { module = "com.google.dagger:hilt-android", version.ref = "hilt" }
hilt-navigation-compose = { module = "androidx.hilt:hilt-navigation-compose" }
room-runtime           = { module = "androidx.room:room-runtime", version.ref = "room" }
room-ktx               = { module = "androidx.room:room-ktx", version.ref = "room" }
datastore              = { module = "androidx.datastore:datastore-preferences" }
work-runtime           = { module = "androidx.work:work-runtime-ktx" }
biometric              = { module = "androidx.biometric:biometric" }
retrofit               = { module = "com.squareup.retrofit2:retrofit" }
okhttp                 = { module = "com.squareup.okhttp3:okhttp" }
kotlinx-serialization  = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json" }
media3-exoplayer       = { module = "androidx.media3:media3-exoplayer", version.ref = "media3" }
media3-rtsp            = { module = "androidx.media3:media3-exoplayer-rtsp", version.ref = "media3" }
media3-ui              = { module = "androidx.media3:media3-ui", version.ref = "media3" }
webrtc                 = { module = "io.getstream:stream-webrtc-android" }   # maintained WebRTC build
firebase-bom           = { module = "com.google.firebase:firebase-bom" }
firebase-messaging     = { module = "com.google.firebase:firebase-messaging" }
tink                   = { module = "com.google.crypto.tink:tink-android" }
coil-compose           = { module = "io.coil-kt.coil3:coil-compose" }
turbine                = { module = "app.cash.turbine:turbine" }
```

## D5. Inside a Feature Module

```
feature/liveview/src/main/kotlin/com/fsec/feature/liveview/
├── navigation/LiveViewNavigation.kt     // route + NavGraphBuilder.liveView()
├── ActiveCamerasRoute.kt                // collects state, hands lambdas to screen
├── ActiveCamerasScreen.kt               // stateless UI
├── ActiveCamerasViewModel.kt
├── model/ActiveCamerasUiState.kt        // UiState, Intent, Effect
└── components/
    ├── AdaptiveCameraGrid.kt
    ├── CameraTile.kt
    └── FullscreenCamera.kt
```

## D6. MVI Pattern

```kotlin
interface MviViewModel<S, I, E> {
    val state: StateFlow<S>
    val effects: Flow<E>          // one-off: toast, navigate, haptic
    fun onIntent(intent: I)
}
```

- **UiState** is an immutable data class, exposed as `StateFlow`, and collected with `collectAsStateWithLifecycle()`.
- **Intents** are the only way the UI changes state.
- **Effects** use a `Channel` (`receiveAsFlow()`) for one-off events.
- State that must survive process death (e.g. `maximizedCameraId`, scroll index) lives in `SavedStateHandle`.

## D7. Data Layer

### Single source of truth

```
         REST (initial load / commands)          WebSocket (live events)
                   │                                     │
                   ▼                                     ▼
            ┌─────────────────── Repository ───────────────────┐
            │  writes to Room (lists, history)                 │
            │  or StateFlow (volatile: active sessions)        │
            └──────────────────────┬───────────────────────────┘
                                   ▼
                     Flow<T> to use cases / ViewModels
```

| Data | Storage | Source |
|---|---|---|
| Cameras, doors, nodes, rules | Room | REST + WS updates |
| Alarms + history | Room | REST page + WS `alarm.*` + FCM |
| Active camera sessions | In-memory `StateFlow` | REST `/sessions/active` + WS |
| Door state | Room | WS `door.state` |
| Saved clips | Room + MediaStore | WorkManager |
| Settings | DataStore | Local |

### Event stream (WebSocket)

```kotlin
@Singleton
class EventStream @Inject constructor(
    private val client: OkHttpClient,
    private val tokens: TokenStore,
    private val json: Json,
    @AppScope private val scope: CoroutineScope,
) {
    private val _events = MutableSharedFlow<ServerEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<ServerEvent> = _events

    val connection = MutableStateFlow<ConnState>(ConnState.Disconnected)

    fun start() = scope.launch {
        var backoff = 1_000L
        while (isActive) {
            val closed = CompletableDeferred<Unit>()
            val req = Request.Builder().url(Endpoints.WS)
                .header("Authorization", "Bearer ${tokens.access()}").build()
            client.newWebSocket(req, object : WebSocketListener() {
                override fun onOpen(ws: WebSocket, r: Response) {
                    connection.value = ConnState.Connected; backoff = 1_000L
                }
                override fun onMessage(ws: WebSocket, text: String) {
                    _events.tryEmit(json.decodeFromString(ServerEvent.serializer(), text))
                }
                override fun onFailure(ws: WebSocket, t: Throwable, r: Response?) { closed.complete(Unit) }
                override fun onClosed(ws: WebSocket, code: Int, reason: String) { closed.complete(Unit) }
            })
            closed.await()
            connection.value = ConnState.Reconnecting
            delay(backoff); backoff = (backoff * 2).coerceAtMost(30_000L)
        }
    }
}
```

- Started when the app comes to the foreground (`ProcessLifecycleOwner`) and stopped in the background. Alarms then come via FCM.
- After a reconnect, repositories **re-fetch snapshots** (`/sessions/active`, `/alarms?open=true`) so missed events don't matter.

### Active cameras repository

```kotlin
class ActiveCamerasRepositoryImpl @Inject constructor(
    private val api: CameraApi,
    events: EventStream,
    @AppScope scope: CoroutineScope,
) : ActiveCamerasRepository {

    private val _active = MutableStateFlow<List<ActiveCamera>>(emptyList())
    override val active: StateFlow<List<ActiveCamera>> = _active

    init {
        scope.launch {
            events.events.collect { e ->
                when (e) {
                    is ServerEvent.SessionStarted -> _active.update { (it.filterNot { a -> a.cameraId == e.cam.cameraId } + e.cam).sorted() }
                    is ServerEvent.SessionStopped -> _active.update { list -> list.filterNot { it.cameraId == e.cameraId } }
                    is ServerEvent.CameraState    -> _active.update { list -> list.map { if (it.cameraId == e.cameraId) it.copy(state = e.state) else it } }
                    is ServerEvent.Reconnected    -> refresh()
                    else -> Unit
                }
            }
        }
    }

    override suspend fun refresh() { _active.value = api.activeSessions().map { it.toModel() }.sorted() }
    override suspend fun start(ids: List<String>) = ids.forEach { api.startSession(it) }
    override suspend fun stop(id: String) = api.stopSession(id)

    // Alarm cameras first, then manual cameras by start time
    private fun List<ActiveCamera>.sorted() =
        sortedWith(compareBy<ActiveCamera> { it.source != SessionSource.ALARM }.thenBy { it.startedAt })
}
```

## D8. Networking & Security

| Concern | Implementation |
|---|---|
| Base URL | `https://fsec.local` (internal DNS) or `https://10.10.0.5`. The same address works on-site and over WireGuard |
| TLS pinning | Pin Caddy's internal root CA via `network_security_config.xml` `<pin-set>` or OkHttp `CertificatePinner` |
| Cleartext | Disabled globally |
| Auth | Short access JWT (15 min) + refresh token. OkHttp `Authenticator` refreshes on 401 (single-flight mutex) |
| Token storage | DataStore encrypted with **Tink** (AEAD key wrapped by Android Keystore) |
| Device binding | At first login, generate an EC key in Keystore (D11) and register its public key. Admin approves the device |
| Network awareness | `ConnectivityManager` callbacks → `NetworkMode.LAN / REMOTE_VPN / CELLULAR / OFFLINE` → drives the live-tile limit and stream quality |
| Logging | No tokens or URLs with credentials in logs. Timber with a release tree that drops debug logs |

```kotlin
val client = OkHttpClient.Builder()
    .certificatePinner(CertificatePinner.Builder()
        .add("fsec.local", "sha256/<base64-of-CA-SPKI>").build())
    .addInterceptor(AuthInterceptor(tokenStore))
    .authenticator(TokenRefreshAuthenticator(tokenStore, authApi))
    .pingInterval(20, TimeUnit.SECONDS)        // keeps WebSocket alive
    .build()
```

## D9. Video Architecture

### API

```kotlin
enum class StreamQuality { SUB, MAIN }

interface VideoPlayer {
    val cameraId: String
    val state: StateFlow<PlayerState>          // Idle, Connecting, Playing, Error(msg)
    fun play(quality: StreamQuality)
    fun switchQuality(quality: StreamQuality)  // keeps last frame until new one arrives
    fun pause()
    fun release()
    @Composable fun Surface(modifier: Modifier)
}
```

| Implementation | When | How |
|---|---|---|
| `WebRtcPlayer` (default) | LAN and VPN | SDP offer → `POST /api/v1/cameras/{id}/webrtc?quality=` → answer. `SurfaceViewRenderer` / `TextureView` sink |
| `RtspPlayer` | Fallback / diagnostics | Media3 ExoPlayer + RTSP from go2rtc restream (`rtsp://app:****@server:8554/cam01_sub`) |
| `SnapshotPlayer` | Over the live-tile limit, or on weak network | `GET /snapshot` every 2 s via Coil |

### PlayerPool

```kotlin
@Singleton
class PlayerPool @Inject constructor(private val factory: VideoPlayerFactory) {
    private val players = LinkedHashMap<String, VideoPlayer>()

    fun get(cameraId: String): VideoPlayer =
        players.getOrPut(cameraId) { factory.create(cameraId) }

    /** Keep only active cameras; release the rest. */
    fun retain(activeIds: Set<String>) {
        players.keys.filterNot { it in activeIds }.forEach { players.remove(it)?.release() }
    }

    fun releaseAll() { players.values.forEach { it.release() }; players.clear() }
}
```

### Live-tile budget

```kotlin
fun liveBudget(mode: NetworkMode, settings: Settings): Int = when (mode) {
    NetworkMode.LAN        -> settings.maxLiveTilesWifi      // default 9
    NetworkMode.REMOTE_VPN -> settings.maxLiveTilesRemote    // default 6
    NetworkMode.CELLULAR   -> settings.maxLiveTilesCellular  // default 4
    NetworkMode.OFFLINE    -> 0
}
// Tiles beyond the budget use SnapshotPlayer. Alarm cameras are always inside the budget.
```

> **Hardware limit:** phones can only decode a limited number of video streams at once (often 8–16 at 360p, device-dependent). The budget protects against decoder failures. Test on your actual phones and tablets.

## D10. Active Cameras: Grid, Maximize, Back

```kotlin
data class ActiveCamerasUiState(
    val tiles: List<ActiveCamera> = emptyList(),
    val liveIds: Set<String> = emptySet(),            // within budget
    val maximizedCameraId: String? = null,
    val pendingAlarmBanner: AlarmBanner? = null,
)

sealed interface ActiveCamerasIntent {
    data class Maximize(val cameraId: String) : ActiveCamerasIntent
    data object Minimize : ActiveCamerasIntent
    data class Stop(val cameraId: String) : ActiveCamerasIntent
    data class SwipeTo(val cameraId: String) : ActiveCamerasIntent
    data object ShowAlarmCameras : ActiveCamerasIntent
}

@HiltViewModel
class ActiveCamerasViewModel @Inject constructor(
    observeActive: ObserveActiveCamerasUseCase,
    observeNetwork: ObserveNetworkModeUseCase,
    private val stopCamera: StopCameraUseCase,
    private val pool: PlayerPool,
    private val savedState: SavedStateHandle,
) : ViewModel() {

    private val maximized = savedState.getStateFlow<String?>("max", null)

    val state: StateFlow<ActiveCamerasUiState> =
        combine(observeActive(), observeNetwork(), maximized) { tiles, net, max ->
            pool.retain(tiles.map { it.cameraId }.toSet())
            val budget = liveBudget(net.mode, net.settings)
            ActiveCamerasUiState(
                tiles = tiles,
                liveIds = tiles.take(budget).map { it.cameraId }.toSet(),
                maximizedCameraId = max?.takeIf { id -> tiles.any { it.cameraId == id } },
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ActiveCamerasUiState())

    fun onIntent(i: ActiveCamerasIntent) {
        when (i) {
            is ActiveCamerasIntent.Maximize -> {
                pool.get(i.cameraId).switchQuality(StreamQuality.MAIN)
                savedState["max"] = i.cameraId
            }
            ActiveCamerasIntent.Minimize -> {
                maximized.value?.let { pool.get(it).switchQuality(StreamQuality.SUB) }
                savedState["max"] = null
            }
            is ActiveCamerasIntent.SwipeTo -> {
                maximized.value?.let { pool.get(it).switchQuality(StreamQuality.SUB) }
                pool.get(i.cameraId).switchQuality(StreamQuality.MAIN)
                savedState["max"] = i.cameraId
            }
            is ActiveCamerasIntent.Stop -> viewModelScope.launch { stopCamera(i.cameraId) }
            ActiveCamerasIntent.ShowAlarmCameras -> savedState["max"] = null
        }
    }
}
```

```kotlin
@Composable
fun ActiveCamerasScreen(state: ActiveCamerasUiState, pool: PlayerPool, onIntent: (ActiveCamerasIntent) -> Unit) {
    BackHandler(enabled = state.maximizedCameraId != null) { onIntent(ActiveCamerasIntent.Minimize) }
    val gridState = rememberLazyGridState()        // kept → same scroll position on return

    SharedTransitionLayout {
        AnimatedContent(targetState = state.maximizedCameraId, label = "maximize") { maxId ->
            if (maxId == null) {
                AdaptiveCameraGrid(state.tiles, state.liveIds, gridState, pool,
                    sharedScope = this@SharedTransitionLayout, visibility = this@AnimatedContent,
                    onTap = { onIntent(ActiveCamerasIntent.Maximize(it)) },
                    onStop = { onIntent(ActiveCamerasIntent.Stop(it)) })
            } else {
                FullscreenCamera(state.tiles, maxId, pool,
                    sharedScope = this@SharedTransitionLayout, visibility = this@AnimatedContent,
                    onSwipe = { onIntent(ActiveCamerasIntent.SwipeTo(it)) },
                    onMinimize = { onIntent(ActiveCamerasIntent.Minimize) },
                    onStop = { onIntent(ActiveCamerasIntent.Stop(it)) })
            }
        }
    }
}

/** Columns from the v1.1 layout table. */
fun gridColumns(count: Int, wide: Boolean): Int = when {
    count <= 1 -> 1
    count == 2 -> if (wide) 2 else 1
    count <= 4 -> 2
    count <= 6 -> if (wide) 3 else 2
    count <= 9 -> 3
    else       -> if (wide) 4 else 3
}
```

**Implementation notes**
- `wide` = `WindowSizeClass` width ≥ Medium (landscape phone / tablet).
- Each tile uses `Modifier.sharedElement(rememberSharedContentState("cam-$id"), visibility)` for the smooth expand animation.
- A video surface can only be attached in one place. During the animation, show the **last frame bitmap**, then attach the player to the fullscreen surface. WebRTC can attach two sinks, so it can also render both briefly.
- Fullscreen uses a `HorizontalPager` over active cameras for swipe left/right.
- Pause off-screen tiles (`LazyGridState.layoutInfo.visibleItemsInfo`) and all grid tiles while fullscreen. Resume on return.

## D11. Door Command Security (biometric-signed)

```
App                                   Server
 │ POST /doors/12/challenge ────────► nonce (30 s validity)
 │ BiometricPrompt(CryptoObject(Signature)) – user touches sensor
 │ sign(doorId | nonce | ts) with Keystore key (requires biometric every use)
 │ POST /doors/12/open {nonce, ts, sig} ─► verify with registered device public key
 │                                       → RBAC → signed node command (Part B)
 │ ◄──────── result + live door.state via WebSocket
```

```kotlin
object DeviceKey {
    private const val ALIAS = "fsec_device_key"

    fun ensure() {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (ks.containsAlias(ALIAS)) return
        KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore").apply {
            initialize(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN)
                .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                .setDigests(KeyProperties.DIGEST_SHA256)
                .setUserAuthenticationRequired(true)
                .setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG) // every use
                .setInvalidatedByBiometricEnrollment(true)
                .build())
        }.generateKeyPair()
    }

    fun signatureForPrompt(): Signature {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return Signature.getInstance("SHA256withECDSA").apply {
            initSign(ks.getKey(ALIAS, null) as PrivateKey)
        }
    }
}
// Feature: BiometricPrompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(DeviceKey.signatureForPrompt()))
// onSuccess: result.cryptoObject!!.signature!!.apply { update(payload) }.sign()
```

UI: `HoldToConfirmButton` (1.5 s hold) → biometric → "Unlocking…" → "Unlocked" (from Ack) → "Door open / closed" (from reed events).

## D12. Alarm Pipeline

```
FCM data message (priority high)
  → AlarmMessagingService.onMessageReceived()
  → AlarmNotifier.show(alarm)
       ├─ Channel "alarms": IMPORTANCE_HIGH, alarm sound (USAGE_ALARM), vibration
       ├─ setFullScreenIntent(AlarmActivity)    (if allowed)
       ├─ actions: "View cameras", "Acknowledge"
       └─ dedupe by alarmId (WS + FCM may both arrive)
  → AlarmActivity (showWhenLocked, turnScreenOn)
       → shows sensor + cameras → "View" → deep link fsec://liveview?alarm=a-981
```

```kotlin
class AlarmMessagingService : FirebaseMessagingService() {
    @Inject lateinit var notifier: AlarmNotifier
    @Inject lateinit var tokenSync: PushTokenSync

    override fun onMessageReceived(msg: RemoteMessage) {
        if (msg.data["type"] == "alarm") notifier.show(AlarmPush.from(msg.data))
    }
    override fun onNewToken(token: String) = tokenSync.enqueue(token)   // WorkManager upload
}
```

**Android-version checklist**

| Item | Action |
|---|---|
| Android 13+ | Request `POST_NOTIFICATIONS` at onboarding |
| Android 14+ full-screen intent | Check `NotificationManager.canUseFullScreenIntent()`. If false, guide the user to `Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT`. Google Play only grants it by default to calling/alarm-clock apps, so distribute privately (managed Google Play / MDM) |
| Do Not Disturb | Ask the user to enable "Override Do Not Disturb" for the **Alarms** channel |
| Battery | Ask for exemption from battery optimization (private distribution), or OEM-specific guidance (Xiaomi, Huawei, Samsung) |
| Test | Phone locked, Doze (`adb shell dumpsys deviceidle force-idle`), app killed, 4G + VPN |

## D13. Navigation

- **Type-safe Navigation Compose** (`@Serializable` routes). Each feature exposes `NavGraphBuilder.featureX()` and `NavController.navigateToX()`.
- Deep links: `fsec://liveview?alarm={id}`, `fsec://doors/{id}`, `fsec://alarms/{id}`.
- Bottom bar (phone) / navigation rail (tablet): **Dashboard · Cameras · Live (N) · Doors · Alarms**. The admin sections are under Settings.

```kotlin
@Serializable data object CamerasRoute
@Serializable data class LiveViewRoute(val alarmId: String? = null)
@Serializable data class DoorDetailRoute(val doorId: String)
```

## D14. Background Work

| Job | API | Notes |
|---|---|---|
| Save clip | WorkManager (`CoroutineWorker`, foreground info with progress) | Request export → poll → download → `MediaStore.Video` (Movies/FSEC) |
| Push token upload | WorkManager, network constraint | Retries with backoff |
| Cache refresh | WorkManager periodic (6 h) | Doors, cameras, nodes |
| Audit/history sync | On foreground | Paged REST |

## D15. Adaptive UI

- `currentWindowAdaptiveInfo()` → Compact / Medium / Expanded.
- Tablet: the Dashboard shows the Active Cameras grid next to the alarm list (list-detail).
- Camera screens can use `FLAG_SECURE` (setting) to block screenshots and screen recording.

## D16. Testing Strategy

| Level | Tools | What |
|---|---|---|
| Unit | JUnit5, Turbine, fakes from `:core:testing` | ViewModels (grid/maximize/back), use cases, sorting, `gridColumns`, live budget |
| Data | Room in-memory, MockWebServer | Repositories, WS event handling, token refresh |
| UI | Compose UI tests | Tap tile → fullscreen → Back → same scroll position; Stop from tile; alarm banner |
| Screenshot | Roborazzi / Paparazzi | Grid layouts for 1, 2, 4, 6, 9, 16 cameras, phone and tablet |
| Performance | Macrobenchmark | Grid with 16 tiles, maximize latency |
| End-to-end | Staging server + simulated nodes | Alarm < 2 s, door command flow, reconnect |

## D17. Build, CI & Distribution

- **Flavors:** `dev` (staging server, debug menu, verbose logs) and `prod`.
- **CI** (GitHub Actions / GitLab): ktlint + detekt → unit tests → lint → assemble → screenshot tests → signed release.
- **R8** enabled for release, with keep rules for protobuf/serialization DTOs.
- **Distribution:** Firebase App Distribution for testers, then **managed Google Play (private app)** or MDM for the 4 users. This avoids public Play Store policy limits on full-screen alarms.
- **Versioning:** `versionCode` from CI. The app sends `X-App-Version`, and the server can block outdated versions.

## D18. Roles in the App (server enforces, app only hides)

| Feature | Admin | Operator | Viewer |
|---|---|---|---|
| View cameras / start-stop cameras | ✅ | ✅ | ✅ |
| Acknowledge alarms | ✅ | ✅ | ❌ |
| Open doors | ✅ | ✅ | ❌ |
| Save clips | ✅ | ✅ | ✅ |
| Rules, users, device approval, PoE | ✅ | ❌ | ❌ |

---

*End of Technical Specification v1.2*