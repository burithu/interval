# Interval — Product & Design Spec

Status: approved, 2026-10-11. Decisions are numbered D1–D30 and listed at the end.

## 1. Product

**Pitch.** An Android app that repeats reminders at a set interval during the hours you choose. It nudges you to stand up, drink water, stretch or eat, and it restarts its own timer, so you never reset an alarm by hand.

**Target user.** The author, working at a desk. Later, the public via the Play Store.

**Core task.** Create a repeating reminder in a few taps: label, interval, active window (A → B), days.

**Problem today.** Phone timers need a manual reset after every ring, or ~20 separate alarms. Nothing offers a repeating prompt that can be a quiet notification instead of a full alarm.

### Scope

**v1**
- Multiple reminders, each with: label; interval (presets + custom minutes); active window and days; timing mode (*restart from dismiss* / *keep fixed schedule*); ring style (notification / alarm); alarm ring length; snooze length; system ringtone; vibrate on/off; on/off switch.
- Swipe-away counts as dismiss. No response → missed (notification: 5 min; alarm: its ring length). Missed restarts the timer like a dismiss.
- Pause all for a chosen duration.
- Home: big next-ring countdown and the reminder list.
- Light/dark theme follows the phone.
- Survives reboot, time-zone and daylight-saving changes.
- Android Auto Backup.
- Onboarding: permissions + reliability setup for the phone's maker (D36).
- Hidden debug event log (last 200 events).

**Later**
- Daily count of dismissed vs missed.
- Quick Settings "Pause all" tile; home-screen countdown widget.
- Export / import.
- Play Store release (listing, policy declarations, privacy policy, icon artwork).

**Not doing**
- iOS; accounts, cloud sync or any backend; full history/streaks/charts; custom vibration patterns.

### Platform & constraints
- Android only, every maker (D36). Kotlin + Jetpack Compose. Min SDK 26, target latest stable.
- All data on device. No servers. Cost $0 now; $25 Play fee later.
- Personal GitHub project first, built to Play Store standards.
- Primary test device: Xiaomi 13 (HyperOS).

## 2. Screens & navigation

Single stack, no bottom tabs. Home is the root.

```
First run:  Welcome → Permissions → Reliability setup → Home (empty)
Home ── tap row / Add bar ──► Reminder editor
     ── Pause all chip ─────► Pause sheet
     ── ⚙ ──────────────────► Settings ──► Reliability setup / Event log
System: Notification (Dismiss · Snooze) · Alarm screen (over lock screen)
```

| # | Screen | Notes |
|---|---|---|
| 01 | Welcome | One button |
| 02 | Permissions | Notifications, exact alarms, full-screen alarms; only those the Android version needs; skippable |
| 03 | Reliability setup | Battery unrestricted on every phone, plus the maker's own steps (Xiaomi: Autostart, lock screen, pop-ups; Samsung: Never sleeping apps; others: auto-launch) and a dontkillmyapp.com guide; ✓ rechecked on return; skippable |
| 04 | Home: empty | Starters: Water 45 min, Stand 1 h, Stretch 2 h — one tap each |
| 05 | Home | Countdown hero (soonest ring across all reminders), list sorted by next ring, off at bottom |
| 06 | Home: paused | "Paused until 15:00 · Resume" |
| 07 | Home: warning | Banner "Reminders may be late. Fix." when a permission/setting is off |
| 08 | Pause sheet | 30 min · 1 h · 2 h · 4 h · Until tomorrow; each shows its end time |
| 09 | Reminder editor | All fields, hint lines, "+1 DAY" for overnight, Ring for only when Alarm, Delete only when editing |
| 10 | Alarm screen | Label, Dismiss, Snooze, draining ring-length bar |
| 11 | Notification | Dismiss / Snooze actions; swipe = dismiss; missed one stays until replaced |
| 12 | Settings | Theme (System/Light/Dark, D31), reliability status, missed timeout, default snooze, event log, About. Opened from the ⚙ on Home |

Mockups: https://claude.ai/artifact/H6wWtw87euJ4r6XuFwT8GR (style options: https://claude.ai/artifact/JanqQAXH9Czq8V4jcKratz)

## 3. Behavior & scheduling rules

**Fixed schedule**
- Rings at window start + n × interval while inside the window (first ring *at* window start).
- Dismiss, snooze and missed never shift the grid.
- A snooze is dropped if the next grid slot comes first.
- Interval longer than the window → one ring at window start.

**Restart timer**
- Dismiss or missed at time *t* → next ring = *t* + interval.
- Snooze → rings again after snooze length; the later dismiss restarts the timer.
- First ring of a day = window start + interval.
- Next ring past window end → done for today.

**Shared**
- Outside the window or on inactive days: silent.
- Window end < start = overnight; days refer to the day the window starts. End = start = all day (24 h).
- Turning on or saving a reminder schedules fresh from now (restart: now + interval; fixed: next grid slot).
- Simultaneous reminders notify separately; alarms queue.
- Pause: nothing rings. On resume/expiry: restart → resume + interval; fixed → next grid slot. "Until tomorrow" resumes at each reminder's next window start.
- Reboot / time change / restore: recompute all. A restart-mode reminder that was due while off reschedules to now + interval (no ring at boot).
- Missed notification stays in the shade until the next ring replaces it.

**Ring style and phone mode** (D33)
- Notification: chime + vibration (Vibrate switch).
- Alarm: always the full-screen alarm for the ring length, plus its alarm type: Screen only (nothing else), Vibrate, or Full alarm (sound + vibration).
- Both follow the ringer mode. Silent → no sound, no vibration (an alarm only shows its screen). Vibrate → no sound.
- Vibrate on silent (D34), per reminder, off by default, shown only when the ring vibrates: on silent it still vibrates, never sounds.

## 4. Visual design system ("Big Type")

**Color tokens**

| Token | Light | Dark |
|---|---|---|
| bg | #FBFBFA | #0B0B0C |
| fg | #0C0C0D | #F3F3F1 |
| muted | #707076 | #8E8E95 |
| line | #E2E2E5 | #232327 |
| accent | #2F4BFF | #7489FF |
| onAccent | #FFFFFF | #0B0B0C |
| track | #DCDCE0 | #2D2D33 |
| danger | #D22D2D | #FF6B6B |

No dynamic (wallpaper) color. Add bar inverts in dark.

**Type** — Archivo variable (OFL), bundled.

| Token | sp | Weight | Width | Used for |
|---|---|---|---|---|
| display | 84 / lh 76 | 800 | 70% | Welcome headline |
| countdown | 64 / lh 61 (units 22, 600, 100%) | 800 | 70% | Home hero |
| alarmLabel | 48 / lh 46 | 800 | 112%, uppercase | Alarm screen label |
| time | 34 / lh 36 | 800 | 70% | Time window, alarm clock |
| numeral | 22 | 800 | 80% | Setup step numbers |
| screenTitle | 20 | 800 | 112%, uppercase | Home title, setup titles, Dismiss |
| title | 18 | 700 | 105% | Hero name, label input |
| sheetTitle | 15 | 800 | 112%, uppercase | Sub-screen bars, sheets, wordmark |
| nextIn | 15 | 800 | 80% | Next ring time on rows |
| reminderName | 14 | 700 | 105% | Row names, segmented options |
| body | 14 / lh 20 | 400 | 100% | Setting labels, pills, paragraphs |
| meta | 12 / lh 16 | 400 | 100% | Row details, hints |
| fieldLabel | 11 | 700 | 100%, uppercase, +0.08em | Section labels, chips, small buttons |

Screens use these tokens as they are, with no inline size overrides. Material's own text (dialogs, time picker) is mapped to Archivo, and its shapes are square.

**Spacing & shape.** 4dp grid: 2 (title-to-meta only), 4, 8, 12, 16, 20 (screen margin), 24 (between sections). Square corners except pills, chips, switches. Strokes: 2dp rules (hero, inputs), 1.5dp control outlines, 1dp hairlines, 4dp alarm progress.

**Sizes.** 48dp minimum touch target everywhere (40dp controls get a 48dp hit area). Chip 32 · small control 40 · control 48 · single-line row 56 · two-line row 64 · alarm Dismiss 72 · icon 22.

**Components.** CountdownHero, ReminderRow (meta wraps to 2 lines), AddBar, TopBar, ListRow, SmallButton, Chip (outline at rest, filled while paused), OptionPill, Segmented, DayPicker, TimeWindow, SettingRow, Banner, Sheet, AlarmActions. Icons (gear, back, close, arrow) are drawn as 2dp line glyphs, not font characters.

**Motion.** Countdown digit slide (~200ms), sheet slide. Off when "Remove animations" is on.

## 5. Data model

**Reminder** (Room)

| Field | Type | Default | Rule |
|---|---|---|---|
| id | Long | auto | PK |
| label | String | — | 1–40 chars |
| intervalMinutes | Int | 60 | 1–1440 |
| windowStart | Int (min of day) | 540 | 0–1439 |
| windowEnd | Int (min of day) | 1080 | < start = overnight; = start = 24 h |
| days | Int bitmask | Mon–Fri | bit 0 = Monday; ≥ 1 day |
| timingMode | RESTART / FIXED | RESTART | |
| ringStyle | NOTIFICATION / ALARM | NOTIFICATION | |
| ringSeconds | Int | 60 | 15–300, alarm only |
| snoozeMinutes | Int | settings default (10) | 1–60 |
| soundUri | String? | null = default | |
| vibrate | Boolean | true | |
| enabled | Boolean | true | |
| createdAt | Instant | now | |

**ReminderState** (Room, 1:1, cascade delete): reminderId, phase (WAITING / RINGING / SNOOZED / DONE_TODAY), nextRingAt: Instant?, ringingSince: Instant?

**EventLog** (Room, capped at 200): id, reminderId?, type, at, scheduledFor?, detail

**AppSettings** (DataStore): missedTimeoutMinutes = 5, defaultSnoozeMinutes = 10, pausedUntil: Instant?, onboardingDone = false

Instants are stored in UTC; windows as local minute-of-day. All scheduling goes through one pure function `nextRing(reminder, state, event, now, zone)`.

## 6. Architecture

| Layer | Choice |
|---|---|
| UI | Compose + Material 3 base with custom Big Type theme |
| Navigation | Navigation Compose, single main Activity + `AlarmActivity` |
| Pattern | MVVM (screens → ViewModels → repositories) |
| Storage | Room + DataStore |
| Async | Coroutines + Flow |
| DI | Manual `AppContainer` |
| Modules | Single app module; packages `ui/ data/ scheduling/ ringing/ onboarding/` |

**Ring pipeline**
```
nextRing() → AlarmManager.setAlarmClock()   (one alarm per reminder)
  → RingReceiver
      post notification (ALARM: AlarmRingService → AlarmActivity, M4)
      schedule the "if ignored" ring now: nextRing(Missed(now + timeout)) via setAlarmClock
  → Dismiss / Snooze / swipe → ActionReceiver → replaces the "if ignored" ring
  → "if ignored" ring fires while still RINGING → log MISSED, ring again
  → update ReminderState → nextRing() → schedule → EventLog
```
- Missed handling never waits on a separate alarm. On HyperOS, `setExactAndAllowWhileIdle` missed checks arrived 13–23 min late (2026-10-11 test), which stalled the reminder. A cosmetic, inexact alarm only relabels the notification "Missed".
- `setAlarmClock()`: Doze-exempt, no 9-min throttle; status-bar alarm icon accepted (D30).
- One notification channel per reminder (per-reminder sound).
- `RescheduleReceiver`: boot, package replaced, time set, time-zone changed, exact-alarm permission changed.

**Permissions:** POST_NOTIFICATIONS, SCHEDULE_EXACT_ALARM (user-granted; not USE_EXACT_ALARM), USE_FULL_SCREEN_INTENT, FOREGROUND_SERVICE + type (confirm against Android 14+ rules during M4), RECEIVE_BOOT_COMPLETED, VIBRATE. Not REQUEST_IGNORE_BATTERY_OPTIMIZATIONS (Play-restricted); open battery settings directly instead.

## 7. Risks
1. **Reliability (high)** — Doze and HyperOS battery management. Mitigated by setAlarmClock, reliability onboarding, event log, Xiaomi test checklist.
2. **Permissions on Android 12–14+** — each explained in onboarding.
3. **Play policy** — exact alarm and full-screen intent declarations at release time.
4. **Name** — "Interval" to be checked before release.

### HyperOS findings (Xiaomi 13 China ROM, HyperOS 3, Android 16 — tested 2026-10-11)
| Problem | Cause | Handling |
|---|---|---|
| Next ring delayed up to 23 min after an unanswered ring | `setExactAndAllowWhileIdle` throttled | Every ring uses `setAlarmClock`; the "if ignored" ring is booked at ring time |
| Notifications silent, no popup | Per-app HyperOS notification switches off by default (`bbbc=0`) | App plays sound/vibration itself (RingService); onboarding links the notification page |
| Full-screen intent never launched | HyperOS SystemUI ignores FSI from third-party apps | RingService starts AlarmActivity itself |
| Background start blocked (`BAL_BLOCK`) | Android 15+ needs `SYSTEM_ALERT_WINDOW` *and* a visible overlay | Declare SAW; show 1×1 overlay during launch |
| Alarm screen hidden behind lock screen | MIUI op 10020 "Show on Lock screen" denied; `showWhenLocked` must be in manifest | Onboarding opens Xiaomi permission editor; manifest attributes |
| adb `appops set` for MIUI ops reverted | Security Center keeps its own copy | Must be set in Settings |

Onboarding (M3) on Xiaomi must cover: Autostart, Battery "No restrictions", notification popups/lock screen, Show on Lock screen, Background pop-ups, Display over other apps, full-screen alarms, exact alarms.

## 8. Decision log

| # | Decision |
|---|---|
| D1 | Android only |
| D2 | Per-reminder timing mode: restart from dismiss / fixed schedule |
| D3 | Per-reminder ring style: notification / alarm |
| D4 | Multiple reminders |
| D5 | Active window A → B per reminder |
| D6 | GitHub personal project now, Play Store later; build to store standards |
| D7 | Kotlin + Jetpack Compose |
| D8 | Weekday selection |
| D9 | Snooze; in restart mode the later dismiss restarts the timer |
| D10 | Pause all for X hours |
| D11 | Local storage only + Android Auto Backup |
| D12 | No history/stats in v1 |
| D13 | System ringtones + vibrate on/off per reminder |
| D14 | Minimal and calm mood |
| D15 | Big Type visual style |
| D16 | Theme follows phone |
| D17 | Swipe = dismiss; unanswered → missed; both restart the timer |
| D18 | Per-reminder alarm ring length |
| D19 | No QS tile or widget in v1 |
| D20 | Name: Interval |
| D21 | Missed timeout 5 min (adjustable in Settings); alarm ring length default 1 min |
| D22 | Test device: Xiaomi 13 |
| D23 | Restart mode first ring = window start + interval |
| D24 | Overnight windows supported in v1 |
| D25 | Missed notification stays until replaced |
| D26 | Fixed cobalt brand color, no dynamic color |
| D27 | Start = end means 24 h |
| D28 | Debug event log, last 200 events |
| D29 | Home sorted by next ring, off at bottom |
| D30 | setAlarmClock(); status-bar icon accepted |
| D31 | In-app theme: System / Light / Dark in Settings, default System (overrides D16's no-toggle) |
| D32 | UI streamline: 11-size type scale, minimum 11sp; shared TopBar/ListRow/SmallButton; drawn icons; keep the filled Add bar; row meta wraps to 2 lines; Pause chip outlined at rest, filled while paused |
| D33 | Alarm type per reminder: Screen only / Vibrate / Full alarm (default Full). Alarms no longer ring through silent: silent shows the screen only, vibrate mode drops the sound (overrides "rings through silent mode") |
| D34 | "Vibrate on silent" switch per reminder (notification and alarm), default off. Silent-mode vibration uses the alarm vibration usage, since Android drops notification vibration on silent |
| D35 | App icon: white three-quarter arc on accent blue (option A1: radius 18, stroke 7 in the 108dp canvas), clear of the mask edges. Status-bar icon stays the same arc. Chosen over Big I, Rhythm, Dial, Brackets, Progress |
| D36 | Interval targets every Android phone; Xiaomi 13 is the test device. Reliability setup detects the maker (Xiaomi, Samsung, OnePlus/Oppo/Realme, Vivo/iQOO, Huawei/Honor, Asus, other) and adds its autostart / sleeping-apps step plus a dontkillmyapp.com guide. Non-Xiaomi vendor pages are untested component names with standard-settings fallbacks |
| D37 | Backup restore: a BackupAgent (Auto Backup, fullBackupOnly) marks a restore; next start recomputes every ring from now, drops an expired pause, and re-runs setup (permissions and confirmed steps are per phone). A missing custom sound falls back to the default |
| D38 | Accessibility: switch rows are single toggles; segmented controls and pills are radio groups; day boxes read full day names; display-size text (countdown, alarm screen, welcome headline) caps font scale at 130% |
