# Interval — Build Plan

See [SPEC.md](SPEC.md) for what is being built. This file covers order of work.

Strategy: riskiest first. Scheduling logic, then real rings on the Xiaomi 13, then the Big Type polish.

## Milestones

### M0 — Project setup
- [x] Install Android Studio (bundled JBR 21 JDK, Android SDK)
- [x] Create project: Kotlin, Compose, min SDK 26, Gradle Kotlin DSL + version catalog
- [x] `git init`, `.gitignore`, README, GitHub repository
- [x] GitHub Actions: build + unit tests on push
- [x] Packages: `ui/ data/ scheduling/ ringing/ onboarding/`
- [x] Bundle Archivo variable font; theme tokens (light + dark)

### M1 — Scheduling core (no UI)
- [x] Models: `Reminder`, `ReminderState`, enums
- [x] `nextRing()` pure function implementing SPEC §3
- [x] Unit tests: both modes, overnight, 24 h, days, snooze vs grid, missed, pause/resume, DST and time-zone changes

Done when: all rules pass their tests.

### M2 — Smallest working version
- [x] Room DB, DAOs, repository; DataStore settings
- [x] Basic Home list + basic editor (unstyled is fine)
- [x] `setAlarmClock` scheduling; `RingReceiver` → notification with Dismiss/Snooze; swipe = dismiss; missed check
- [x] Per-reminder notification channels
- [x] `RescheduleReceiver` (boot, update, time, time zone, permission)
- [x] Event log

Done when: a reminder created on the Xiaomi 13 rings, dismiss restarts it, and it survives a reboot.

### M3 — Onboarding & reliability
- [x] Screens 01–03; permission checks; warning banner (07)
- [x] Xiaomi test pass 1 (screen off 2 h, battery saver, swiped from recents) — review event log

### M4 — Alarm-style rings
- [x] `AlarmRingService` (confirm foreground service type for Android 14+)
- [x] `AlarmActivity` over lock screen via full-screen intent
- [x] Ring length → missed; snooze from alarm screen

### M5 — Big Type UI
- [x] Home: hero countdown, sorting, switches, starters, empty state (04, 05)
- [x] Full editor (09): all fields, overnight "+1 DAY", hints, validation, delete
- [x] Dark theme, countdown animation, reduced motion

### M6 — Pause, Settings, log viewer
- [x] Pause sheet + paused Home (06, 08)
- [x] Settings (12)
- [x] Event log viewer

### M7 — Hardening & v1.0
Interval is for every Android phone (D36). The Xiaomi 13 is the only physical test device.
- [x] Accessibility: TalkBack labels, large fonts, 48dp targets
- [x] Auto Backup config; recompute on restore
- [x] Reliability setup for other makers: Samsung, OnePlus/Oppo/Realme, Vivo/iQOO, Honor/Huawei, Asus, Motorola, stock; dontkillmyapp.com link as fallback
- [ ] Reliability checklist on a stock-Android emulator (Pixel image) — partial run 2026-10-12; remaining: 4 (deep Doze), 9, 11, 12, 13
- [ ] Reliability checklist on the Xiaomi 13, twice, on separate days
- [ ] Signed APK as GitHub Release v1.0

## Testing
Unit (scheduling, largest suite) → DAO → a few Compose UI tests → manual reliability checklist with event-log evidence, on the Xiaomi 13 and a stock emulator. Other makers' vendor screens are not device-tested; every vendor intent falls back to standard Android settings. CI runs unit tests on every push.

## Deploying to the phone
- Development: USB debugging from Android Studio.
- Xiaomi: enable Developer options → **USB debugging** and **Install via USB** (requires a signed-in Xiaomi account).
- Personal releases: signed APK on GitHub Releases.

## Later
Play Store release · daily dismissed/missed count · QS tile · widget · export/import.
