# NSL Sport App

แอปวิ่งสำหรับ Android + WearOS พร้อมฟีเจอร์ Interval Training

## ฟีเจอร์

1. **วัดอัตราการเต้นหัวใจ** — ผ่าน Health Services API บน WearOS ส่งข้อมูลมายังโทรศัพท์
2. **แสดง Pace** — คำนวณจาก GPS (rolling 30 วินาที) แสดงเป็น min:sec/km
3. **แสดงระยะทาง** — GPS บนโทรศัพท์
4. **Interval Training** — 2 โหมด:
   - **Activity List**: เพิ่ม activities ทีละ segment (วิ่ง 500m → เดิน 100m → ...) ตั้งจำนวนรอบได้
   - **ควบคุม Pace**: ตั้งช่วง pace เป้าหมาย (เช่น 6:00–7:00 min/km) สั่นเตือนเมื่อออกนอกช่วง
5. **บันทึกสถิติ** — Room Database บนโทรศัพท์ เก็บ: ระยะทาง, เวลา, avg/max pace, avg/max HR
6. **ประวัติการวิ่ง** — ดูรายการทั้งหมด, ดูรายละเอียดแต่ละครั้ง, ลบข้อมูลได้

## วิธี Build และติดตั้ง (ไม่ต้องเสียเงิน ไม่ต้องขึ้น Store)

### ต้องการ

- [Android Studio](https://developer.android.com/studio) (แนะนำ Hedgehog ขึ้นไป)
- Android SDK 34
- Java 17
- โทรศัพท์ Android (Android 8.0+) หรือ emulator
- WearOS watch (WearOS 3.0+) หรือ WearOS emulator

### Build APK

```bash
# Build APK โทรศัพท์ (debug — ไม่ต้อง sign)
./gradlew :app:assembleDebug
# APK → app/build/outputs/apk/debug/app-debug.apk

# Build APK นาฬิกา (debug)
./gradlew :wear:assembleDebug
# APK → wear/build/outputs/apk/debug/wear-debug.apk
```

### ติดตั้งผ่าน ADB (Sideload)

```bash
# เปิด Developer Options + USB Debugging บนโทรศัพท์
adb install -r app/build/outputs/apk/debug/app-debug.apk

# WearOS watch — เปิด ADB Debugging บน watch
# Settings → Developer Options → ADB Debugging ON
# หรือผ่าน WiFi: Settings → Developer Options → Wireless Debugging
adb connect <watch-ip>:5555
adb -s <watch-ip>:5555 install -r wear/build/outputs/apk/debug/wear-debug.apk
```

### ติดตั้งผ่าน Android Studio (แนะนำ)

1. เปิดโปรเจกต์ใน Android Studio
2. เชื่อมโทรศัพท์/emulator
3. เลือก module `app` หรือ `wear` → กด Run ▶

## Architecture

```
Phone App (com.nsl.sportapp)
├── WorkoutTrackingService    ← Foreground Service: GPS, Pace, Interval Logic, Vibration
├── WorkoutRepository         ← Room Database (workouts + segments)
├── WorkoutViewModel          ← State for active workout UI
├── IntervalSetupViewModel    ← Activity-based & Pace-controlled config
└── HistoryViewModel          ← Load/delete workout history

WearOS App (com.nsl.sportapp.wear)
├── WearWorkoutService        ← Foreground Service: Heart Rate (Health Services)
├── WearDataLayerListener     ← WearableListenerService: receive msgs from phone
└── Screens                  ← Wear Compose UI (main + active workout)
```

## Interval Training — วิธีใช้

### Activity List Mode
- กด "ตั้งค่า Interval" → แท็บ "Activity List"
- กด "+ เพิ่ม" → เลือก RUN/WALK/REST → ใส่ระยะทาง (เมตร)
- เพิ่มหลาย activities ได้ เช่น: วิ่ง 500m → เดิน 100m → วิ่ง 500m
- ตั้งจำนวนรอบ (1–100)
- ตั้งช่วง Pace แจ้งเตือน

### Pace Controlled Mode
- แท็บ "ควบคุม Pace"
- เลือก Preset หรือตั้งเอง (เช่น Tempo: 5:00–6:00)
- สั่นเตือนทุก 5 วินาทีถ้า pace ออกนอกช่วง

## Message Protocol (Phone ↔ Watch)

| Path | Direction | Data |
|------|-----------|------|
| `/workout/start` | Phone → Watch | เริ่ม HR monitoring |
| `/workout/stop` | Phone → Watch | หยุด |
| `/workout/heartrate` | Watch → Phone | HR (Int 4 bytes) |
| `/workout/interval_phase` | Phone → Watch | "RUN" / "WALK" / "REST" |
| `/workout/stats` | Phone → Watch | "distMeters:paceSecsPerKm" |