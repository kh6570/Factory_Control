In the name of God, the Most Gracious, the Most Merciful

<div dir="rtl">

# سیستم امنیتی و کنترل درب کارخانه: سند طراحی نسخه ۱.۱

> **تغییرات نسخه ۱.۱:** کاربر می‌تواند هر دوربینی را خودش روشن کند. همه دوربین‌های فعال (چه با آلارم روشن شده باشند، چه دستی) کنار هم در یک صفحه به نام **«دوربین‌های فعال»** نمایش داده می‌شوند. با لمس هر دوربین، تصویر آن تمام‌صفحه می‌شود و با دکمه بازگشت دوباره به صفحه دوربین‌ها برمی‌گردید.

---

## ۰. تصمیم‌های کلیدی طراحی

1. **دوربین‌ها همیشه روشن می‌مانند.** سنسور حرکتی **نمایش زنده و ضبط با کیفیت بالا** را فعال می‌کند، نه برق دوربین را. روشن شدن دوربین IP بین 30 تا 90 ثانیه طول می‌کشد، پس اگر برق دوربین با سنسور وصل شود، اتفاق از دست می‌رود. امکان **کنترل برق هر دوربین از طریق PoE** هم به‌صورت اختیاری وجود دارد.
2. **همه تصاویر روی سرور ضبط می‌شوند.** در اندروید می‌توانید با یک لمس هر کلیپی را روی گوشی **ذخیره کنید**.
3. **یک برد کنترلر یکسان برای همه.** 48 نود درب + 16 نود سنسور = **64 عدد از یک برد**. فقط ماژول ارتباطی (CAN، 4G/SIM یا Ethernet) عوض می‌شود.
4. **جدید: دوربین‌ها با آلارم یا به‌صورت دستی روشن می‌شوند.** همه دوربین‌های فعال کنار هم در یک شبکه تطبیقی نمایش داده می‌شوند. با لمس هر کدام بزرگ می‌شود و با «بازگشت» به شبکه برمی‌گردید.

---

## ۱. خلاصه نیازمندی‌ها

| موضوع | نیازمندی |
|---|---|
| دوربین‌ها | 16 دوربین IP (ترجیحاً LAN/PoE، و Wi-Fi در جای لازم) |
| سنسورها | 16 سنسور دوتکنولوژی (PIR + مایکروویو)، برای هر دوربین یک سنسور |
| عملکرد سنسور | آلارم روی اندروید + نمایش زنده خودکار دوربین‌های مرتبط |
| دوربین دستی | کاربر هر دوربینی را روشن/خاموش می‌کند؛ دوربین‌های فعال کنار هم نمایش داده می‌شوند؛ با لمس بزرگ می‌شوند و با «بازگشت» به شبکه برمی‌گردند |
| اتصال سنسور به دوربین | توسط کاربر تنظیم می‌شود، مثلاً «سنسور 3 ← دوربین‌های 2، 5، 7» |
| نودهای درب | 48 نود که هر کدام یک رله را قطع و وصل می‌کند |
| فاصله | تا 3 کیلومتر؛ سیمی (CAN) یا بی‌سیم (4G/SIM)، به‌صورت ماژولار |
| قطع برق | درب‌ها **Fail-Secure** هستند (قفل می‌مانند) |
| اندروید | گوشی یا تبلت، داخل و خارج از کارخانه |
| کاربران | 4 کاربر با سطح دسترسی متفاوت |
| ضبط | ضبط روی سرور، با امکان ذخیره کلیپ روی اندروید |

---

## ۲. معماری سیستم

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

**ایده اصلی:** هر نود، چه سیمی باشد چه 4G، پیام‌های یکسانی می‌فرستد. گیت‌وی CAN پیام‌های CAN را به همان پیام‌های MQTT که نودهای 4G می‌فرستند تبدیل می‌کند. به این ترتیب سرور لازم نیست بداند هر نود چطور وصل شده است.

---

## ۳. سخت‌افزارهایی که باید طراحی کنید

### ۳.۱ برد اصلی نود یکسان (یک PCB، 64 عدد)

| بخش | پیشنهاد | دلیل |
|---|---|---|
| میکروکنترلر | ESP32-S3 (مثلاً ESP32-S3-WROOM-1) | CAN داخلی (TWAI)، Wi-Fi برای راه‌اندازی، آپدیت OTA، ارزان |
| ورودی تغذیه | مبدل Buck با ورودی 12 تا 24 ولت، محافظ قطب معکوس، TVS، فیوز | تغذیه صنعتی و محافظت در برابر ضربه ولتاژ |
| Watchdog | آی‌سی Supervisor/Watchdog خارجی | ریست خودکار در صورت هنگ کردن |
| امنیت | تراشه امن ATECC608B | نگهداری کلیدها؛ فرمان درب قابل جعل نیست |
| خروجی‌ها | 2 رله (قفل + رزرو)، دیود هرزگرد/وریستور | قطع و وصل قفل |
| ورودی‌ها | 4 ورودی ایزوله نوری | سنسور مغناطیسی درب، دکمه خروج، Tamper، آلارم سنسور |
| ورودی نظارت‌شده | پشتیبانی از مقاومت انتهای خط (EOL) | تشخیص سیم قطع‌شده یا اتصال کوتاه |
| اسلات ارتباطی | هدر برای ماژول ارتباطی | ماژولار بودن |
| محلی | USB-C، LED وضعیت، دکمه تنظیم | نصب در محل |
| اضافی | سنسور دما، نمایشگر ولتاژ ورودی | گزارش سلامت |

**ایمنی درب در سخت‌افزار:**
- از **کنتاکت NO (عادی باز) رله** همراه با **قفل Fail-Secure** استفاده کنید. وقتی برق نیست، رله خاموش است و درب قفل می‌ماند.
- **حداکثر زمان روشن ماندن رله** را سخت‌افزاری محدود کنید (مثلاً 10 ثانیه) تا هیچ باگ نرم‌افزاری نتواند درب را باز نگه دارد.
- **سنسور مغناطیسی (Reed) درب** را بخوانید تا اپ وضعیت واقعی درب را نشان دهد.

### ۳.۲ ماژول‌های ارتباطی قابل تعویض

| ماژول | قطعات | توضیح |
|---|---|---|
| CAN (سیمی) | ترانسیور ایزوله (TI ISO1042)، محافظ TVS، جامپر ترمیناتور 120 اهم | 2500 متر با 20 kbit/s و 5000 متر با 10 kbit/s. ایزولاسیون در فاصله‌های طولانی ضروری است. |
| 4G / SIM | ماژول LTE Cat-1 (SIMCom A7670 / Quectel EC200U، **نسخه مناسب باندهای کشور خودتان را بخرید**)، نانو سیم‌کارت، آنتن SMA، خازن بزرگ برای جریان لحظه‌ای 2 آمپر | از Cat-1 استفاده کنید، نه NB-IoT؛ پاسخ NB-IoT برای درب خیلی کند است. |
| Ethernet | W5500 (SPI) + RJ45 | ESP32-S3 اترنت داخلی (MAC) ندارد |
| اختیاری: LoRa | SX1262 | برد زیاد بدون هزینه سیم‌کارت |

- فرمان‌های امضاشده از 8 بایت طولانی‌ترند، پس از **ISO-TP (ISO 15765-2)** برای تقسیم آن‌ها بین فریم‌های CAN استفاده کنید.
- اگر طول یک خط بیشتر از حدود 2.5 کیلومتر است، آن را با یک گیت‌وی دوم تقسیم کنید یا از 4G استفاده کنید.

### ۳.۳ گیت‌وی CAN
همان برد یکسان با **ماژول Ethernet و ماژول CAN**، که در اتاق سرور نصب می‌شود. 2 عدد بسازید (یکی یدکی). برای نمونه اولیه، مبدل USB-CAN (مثل CANable یا PEAK PCAN-USB) کافی است.

### ۳.۴ معماری فریمور نود (ESP-IDF / FreeRTOS)

```
App logic (door, sensor, health)        ← same on every node
Message layer (Protobuf/CBOR, signing, replay counter)
Transport interface ──┬─ CAN + ISO-TP driver
                      ├─ LTE + MQTT/TLS driver
                      └─ Ethernet + MQTT/TLS driver
HAL (relays, inputs, watchdog, ATECC608B)
OTA updates + safe rollback
```

فریمور تشخیص می‌دهد کدام ماژول وصل است، پس **یک فایل فریمور روی هر 64 نود اجرا می‌شود**.

### ۳.۵ جعبه و تغذیه (برای هر نود)
- جعبه IP65، ریل DIN، گلند کابل
- منبع تغذیه ریلی با باتری پشتیبان (مثلاً Mean Well DRC + باتری 12 ولت)
- خروجی فیوزدار جداگانه برای قفل

---

## ۴. تجهیزاتی که باید بخرید

### ۴.۱ دوربین‌ها (16 عدد)
مشخصات لازم: 4 مگاپیکسل، H.265، **RTSP + ONVIF**، Sub-stream، IP67. هر جا ممکن است از **PoE سیمی** استفاده کنید.

| سطح | نمونه | قیمت تقریبی |
|---|---|---|
| بیشترین اطمینان | EmpireTech IPC-T5442T-ZE | حدود 130 تا 180 دلار |
| ارزش خرید خوب | Amcrest / Hikvision ColorVu / AcuSense 4MP | حدود 80 تا 120 دلار |
| اقتصادی | Reolink RLC-520A (با Frigate از H.264 استفاده کنید) | حدود 50 تا 65 دلار |
| فقط نقاط Wi-Fi | Reolink RLC-510WA یا مشابه | حدود 70 تا 90 دلار |

> ⚠️ در آمریکا یا پروژه‌های دولتی، Hikvision و Dahua ممکن است محدودیت داشته باشند. در این صورت از Axis یا Hanwha Vision استفاده کنید.
> **نکته:** اول یک دوربین بخرید و با سرور تست کنید، بعد 15 تای دیگر را بخرید.

### ۴.۲ سنسورهای حرکتی (16 عدد): دوتکنولوژی PIR + مایکروویو
سنسور دوتکنولوژی فقط وقتی آلارم می‌دهد که **هر دو** تکنولوژی حرکت را ببینند، پس آلارم کاذب بسیار کم است. خروجی آن یک رله است که به نود سنسور وصل می‌شود.
- داخلی: Bosch Blue Line Gen2 TriTech، DSC LC-104-PIMW
- بیرونی: سری Optex VXI-DAM
- حدود 40 تا 150 دلار برای هر عدد

### ۴.۳ شبکه

| تجهیز | پیشنهاد |
|---|---|
| سوئیچ PoE | 24 پورت مدیریتی گیگابیت PoE+، حداقل 250 وات، با VLAN و SNMP/API (TP-Link Omada TL-SG3428MP یا Ubiquiti) |
| روتر/فایروال | دو WAN (فیبر + 4G) با VPN (MikroTik، TP-Link ER605، OPNsense) |
| دوربین‌های دور (بیش از 100 متر) | مبدل فیبر نوری، PoE Extender یا پل بی‌سیم |
| Wi-Fi | 1 تا 2 اکسس‌پوینت تجاری |
| محافظت | محافظ ضربه (Surge) اترنت برای کابل‌های بیرونی |
| سیم‌کارت | طرح IoT/M2M، فقط برای نودهای 4G (حدود 10 تا 50 مگابایت در ماه) |

### ۴.۴ سرور مرکزی

| قطعه | پیشنهاد |
|---|---|
| کامپیوتر | Intel N100/N305 یا Core i5، رم 16 گیگ، NVMe 512 گیگ |
| دیسک ضبط | 2 هارد 8 ترابایتی مخصوص نظارت تصویری (WD Purple / SkyHawk)، RAID 1 |
| هوش مصنوعی (اختیاری) | گرافیک داخلی Intel (OpenVINO) یا Google Coral |
| UPS | 1000 تا 1500 ولت‌آمپر (سرور + سوئیچ + روتر) |
| یدکی | برد گیت‌وی دوم، هارد یدکی |

**فضای ذخیره‌سازی:** 16 دوربین × 4 Mbit/s ≈ 690 گیگابایت در روز، پس 8 ترابایت حدود 11 روز جا دارد (با 2 Mbit/s و H.265 بیش از 20 روز).

### ۴.۵ سخت‌افزار درب (48 عدد)
- قفل برقی یا قفل موتوری Fail-Secure، 12 یا 24 ولت
- سنسور مغناطیسی درب
- ⚠️ **ایمنی آتش‌سوزی:** درب‌ها باید **از داخل به‌صورت مکانیکی** باز شوند. مقررات آتش‌نشانی محل خود را بررسی کنید.

---

## ۵. نرم‌افزار سرور (Docker، رایگان)

| سرویس | وظیفه |
|---|---|
| Frigate | NVR: ضبط 24 ساعته و ضبط رویداد، پخش، تشخیص هوشمند |
| go2rtc | تصویر زنده با WebRTC (بیرون) یا RTSP (داخل) |
| Mosquitto | MQTT با TLS، یک گواهی برای هر دستگاه |
| Backend API (FastAPI / NestJS) | کاربران، سطوح دسترسی، موتور قوانین، **مدیر جلسات دوربین**، فرمان درب، OTA، کنترل PoE |
| PostgreSQL | دستگاه‌ها، قوانین، کاربران، جلسات، گزارش رویدادها (Audit Log) |
| Firebase Cloud Messaging | ارسال آلارم با اولویت بالا |
| WireGuard / Tailscale | دسترسی امن از بیرون. **هرگز دوربین‌ها را مستقیم روی اینترنت قرار ندهید.** |

### ۵.۱ روند آلارم (هدف: کمتر از 2 ثانیه)

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

یعنی: سنسور فعال می‌شود، نود رویداد امضاشده را می‌فرستد، سرور طبق قانون «سنسور 3 ← دوربین‌های 2، 5، 7» برای همه کاربران جلسه آلارم می‌سازد، ضبط با کیفیت بالا شروع می‌شود و نوتیفیکیشن فوری ارسال می‌شود. در اندروید آلارم تمام‌صفحه با آژیر نمایش داده می‌شود و صفحه «دوربین‌های فعال» باز می‌شود، با دوربین‌های آلارم در ابتدا و با حاشیه قرمز.

### ۵.۲ روند روشن کردن دستی دوربین (جدید)

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

**قوانین جلسه دوربین:**
- **منبع جلسه:** `ALARM` (مشترک برای همه کاربران) یا `MANUAL` (متعلق به کاربری که آن را روشن کرده).
- **توقف خودکار:** جلسه دستی وقتی کاربر آن را متوقف کند پایان می‌یابد، یا بعد از مدت قابل تنظیمی که کسی تماشا نکند (پیش‌فرض 10 دقیقه). جلسه آلارم با تأیید آلارم یا پایان زمان آن بسته می‌شود.
- **شمارش ارجاع برای PoE:** دوربین فقط وقتی خاموش می‌شود که **هیچ جلسه‌ای** برای آن باقی نمانده باشد.
- **دسترسی:** همه نقش‌ها (Admin، Operator، Viewer) می‌توانند دوربین را روشن و خاموش کنند. فقط Admin تنظیمات PoE را تغییر می‌دهد.
- هر روشن و خاموش کردن در گزارش رویدادها ثبت می‌شود.

**API:**

```
POST   /api/v1/cameras/{id}/sessions      start camera (manual)
DELETE /api/v1/cameras/{id}/sessions      stop camera (my session)
GET    /api/v1/sessions/active            active cameras for me (manual + alarm)
WS     /api/v1/events                     camera.session.started | camera.session.stopped
                                          | camera.state.changed | alarm.* | door.*
```

### ۵.۳ روند فرمان درب (امن)

```
User taps "Open Door 12" → fingerprint/PIN
→ HTTPS to backend (token) → role check + audit log
→ backend builds {node, door, counter, expiry} + digital signature
→ MQTT (4G) or gateway (CAN)
→ node verifies signature + counter (blocks replay attacks)
→ relay pulses 5 s → reed contact reports opened → closed
→ app shows real door status
```

یعنی: کاربر «باز کردن درب 12» را می‌زند و با اثر انگشت یا PIN تأیید می‌کند. سرور دسترسی را بررسی و ثبت می‌کند، سپس فرمان را با شمارنده و زمان انقضا امضا می‌کند. نود امضا و شمارنده را بررسی می‌کند (جلوگیری از حمله تکرار)، رله 5 ثانیه وصل می‌شود و وضعیت واقعی درب در اپ نمایش داده می‌شود.

---

## ۶. معماری اپلیکیشن اندروید

**تکنولوژی‌ها:** Kotlin، Jetpack Compose، Clean Architecture + MVVM/MVI، Hilt، Coroutines/Flow، Room، DataStore، Retrofit/OkHttp، WorkManager، Media3 ExoPlayer، WebRTC.

### ۶.۱ ماژول‌های Gradle

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

**قانون:** ماژول‌های Feature هرگز به هم وابسته نیستند و فقط از طریق `:core:domain` و Navigation با هم ارتباط دارند.

### ۶.۲ نکات کلیدی طراحی
- **ویدیو:** یک اینترفیس `VideoPlayer` با دو پیاده‌سازی: RTSP (Media3) داخل کارخانه و WebRTC (go2rtc) از بیرون، که به‌صورت خودکار انتخاب می‌شود. در شبکه دوربین‌ها Sub-stream پخش می‌شود و در حالت تمام‌صفحه Main-stream.
- **ذخیره ضبط:** دکمه «ذخیره کلیپ» از سرور خروجی MP4 می‌گیرد و WorkManager آن را روی گوشی دانلود می‌کند.
- **آلارم:** پیام FCM با اولویت بالا، آلارم تمام‌صفحه (حتی وقتی گوشی قفل است) و سپس صفحه دوربین‌های فعال.
- **نقش‌ها:** Admin (همه کارها)، Operator (مشاهده + باز کردن درب)، Viewer (فقط مشاهده و روشن کردن دوربین). نقش‌ها را **سرور** اعمال می‌کند.
- **رابط کاربری درب:** تأیید بیومتریک، دکمه «نگه دارید تا باز شود»، وضعیت واقعی درب، گزارش رویدادها.
- **تبلت:** چیدمان تطبیقی.

### ۶.۳ صفحه دوربین‌های فعال (جدید)

#### رفتار

**لیست دوربین‌ها (`:feature:cameras`)**
- هر دوربین وضعیت خود را نشان می‌دهد: `خاموش` · `در حال روشن شدن…` · `زنده` · `آفلاین`
- دکمه **روشن / خاموش** برای هر دوربین
- انتخاب چندتایی و دکمه **«روشن کردن انتخاب‌شده‌ها»**
- میان‌بر: «نمایش دوربین‌های فعال (N)»

**شبکه دوربین‌های فعال (`:feature:liveview`)**
- همه دوربین‌های فعال (دستی و آلارم) **کنار هم** نمایش داده می‌شوند
- چیدمان با تعداد دوربین‌ها تغییر می‌کند:

| تعداد دوربین فعال | گوشی (عمودی) | گوشی (افقی) / تبلت |
|---|---|---|
| 1 | تمام عرض | تمام‌صفحه |
| 2 | 1 × 2 (زیر هم) | 2 × 1 (کنار هم) |
| 3 تا 4 | 2 × 2 | 2 × 2 |
| 5 تا 6 | 2 × 3 | 3 × 2 |
| 7 تا 9 | 3 × 3 | 3 × 3 |
| 10 تا 16 | 3 × N (با اسکرول) | 4 × 4 |

- **ترتیب:** اول دوربین‌های آلارم (با حاشیه قرمز و برچسب «سنسور 3»)، سپس دوربین‌های دستی به ترتیب روشن شدن. جابه‌جایی با کشیدن اختیاری است.
- هر کاشی نام دوربین، برچسب `ALARM` یا `MANUAL`، وضعیت زنده و دکمه **✕** برای خاموش کردن را دارد.

**بزرگ کردن و بازگشت**
- **لمس یک کاشی:** با انیمیشن نرم تمام‌صفحه می‌شود و تصویر از Sub-stream به Main-stream (کیفیت HD) تغییر می‌کند.
- کنترل‌های حالت تمام‌صفحه: عکس فوری، ذخیره کلیپ، ضبط، باز کردن درب مرتبط، خاموش کردن دوربین، و **کشیدن به چپ/راست** برای رفتن به دوربین فعال بعدی.
- **بازگشت** (دکمه، ژست یا آیکون کوچک‌سازی): به شبکه دوربین‌ها **در همان موقعیت اسکرول** برمی‌گردید و بقیه دوربین‌ها زنده می‌مانند.

**مدیریت پهنای باند**
- شبکه دوربین‌ها همیشه از Sub-stream استفاده می‌کند.
- روی اینترنت موبایل، تعداد قابل تنظیمی کاشی زنده پخش می‌شوند (پیش‌فرض 4). بقیه هر 2 ثانیه یک عکس تازه نشان می‌دهند.
- کاشی‌های خارج از صفحه یا پشت حالت تمام‌صفحه متوقف می‌شوند و فوراً دوباره پخش می‌شوند.

#### معماری

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

**نکات طراحی:**
- **بزرگ کردن یک حالت UI است** (`maximizedCameraId`)، نه یک صفحه جدید. پلیرها زنده می‌مانند و بازگشت به شبکه فوری است. `BackHandler` در Compose مقدار را به `null` برمی‌گرداند و `SharedTransitionLayout` انیمیشن بزرگ شدن کاشی را می‌سازد.
- وضعیت در `SavedStateHandle` ذخیره می‌شود، پس با چرخش صفحه یا بازیابی برنامه، شبکه و دوربین بزرگ‌شده حفظ می‌شوند.
- **PlayerPool** پلیر هر دوربین را نگه می‌دارد. هنگام بزرگ کردن، آخرین فریم Sub-stream روی صفحه می‌ماند تا اولین فریم Main-stream برسد، پس صفحه سیاه نمی‌شود.
- اگر در حالت تمام‌صفحه آلارمی برسد، یک بنر نمایش داده می‌شود. با لمس آن به شبکه برمی‌گردید و دوربین‌های آلارم در ابتدا قرار می‌گیرند.

---

## ۷. بودجه تقریبی (بدون دستمزد و کابل‌کشی)

| مورد | تقریبی |
|---|---|
| 16 دوربین | 1000 تا 2500 دلار |
| 16 سنسور دوتکنولوژی | 700 تا 2000 دلار |
| سوئیچ + روتر + اکسس‌پوینت + محافظ ضربه | 600 تا 1200 دلار |
| سرور + 2 هارد 8 ترابایت + UPS | 900 تا 1500 دلار |
| 64 نود + ماژول‌ها | 1900 تا 4500 دلار |
| 48 قفل Fail-Secure + سنسور درب | 2500 تا 7000 دلار |
| منبع تغذیه و جعبه نودها | 2000 تا 4000 دلار |
| سیم‌کارت | حدود 2 تا 5 دلار برای هر سیم‌کارت در ماه |

---

## ۸. ترتیب ساخت

1. **نمونه اولیه:** سرور + 2 دوربین + 1 نود سنسور + 1 نود درب CAN + 1 نود درب 4G + اپ ساده.
2. **تست:** تأخیر آلارم (کمتر از 2 ثانیه)، **روشن کردن دستی + شبکه دوربین‌ها ← بزرگ کردن ← بازگشت**، امنیت فرمان درب، و تست کابل 3 کیلومتری روی قرقره.
3. نسخه دوم PCB، آپدیت OTA فریمور، اپ کامل.
4. نصب مرحله‌ای (هر بار حدود 10 درب).

---

## ۹. منابع
- CAN in Automation (CiA): لایه‌های پایین CANopen و طول باس — https://can-cia.org/can-knowledge/canopen-lower-layers
- Schneider Electric: حداکثر طول کابل CANopen — https://www.se.com/us/en/faqs/FA339840/
- بهترین دوربین‌های PoE برای Frigate — https://computingforgeeks.com/best-poe-cameras-frigate-nvr/
- راهنمای خرید دوربین PoE شرکت Reolink — https://reolink.com/blog/poe-ip-cameras-buying-guide/

</div>