# Changelog

Every version is also a [GitHub release](https://github.com/burithu/interval/releases).
Versions before 1.0.0 are pre-releases built during development; 1.0.0 is the first stable release (milestone M7).

## 0.13.1 — Time-zone fix
- Fix: after a time-zone change, a restart-timer reminder could be booked outside its active hours (for example 01:01 for a 09:00–18:00 reminder). It now rings one interval from the change, or at the next window.
- Reliability checklist added (docs/CHECKLIST.md) with the first emulator run.

## 0.13.0 — Every Android phone, accessibility, backup
- Setup now recognises Samsung, OnePlus/Oppo/Realme, Vivo/iQOO, Huawei/Honor and Asus phones, with their own autostart and sleeping-apps steps and a link to a step-by-step guide for each.
- TalkBack: switches read their label and state, options read as selected or not, day boxes read full day names, Home switches say which reminder they control.
- Large system fonts: the countdown, alarm screen and welcome headline stay on screen; setup scrolls as one page.
- Restoring from a backup on a new phone keeps reminders and settings, works out fresh ring times, and runs setup again.
- A custom sound that's missing falls back to the default instead of staying silent.

## 0.12.0 — App icon
- Final app icon: the timer arc, sized down so it sits clear of the icon's edges on every launcher shape.

## 0.11.0 — Vibrate on silent
- New "Vibrate on silent" switch for notifications and alarms: on silent the ring still vibrates, with no sound. Off by default.
- Shown only when the reminder vibrates (Vibrate on for notifications; Vibrate or Full alarm for alarms).

## 0.10.0 — Alarm types and silent mode
- Alarm reminders have a type: Screen only, Vibrate, or Full alarm (sound and vibration). Existing alarms stay Full alarm.
- Alarms respect the phone's mode: on silent they only show on screen; on vibrate they make no sound.
- The editor shows Sound only when it's used, and Vibrate only for notifications.

## 0.9.0 — UI streamline
- One type scale of 11 sizes; nothing smaller than 11sp.
- Dialogs and the time picker use the app font and square corners.
- New gear icon; back, close and arrow icons drawn to match.
- Every control has a 48dp touch area; day boxes at least 48dp tall.
- Reminder rows show the full schedule, wrapping to two lines between items.
- Pause chip is outlined at rest and filled while paused.
- Alarm screen: clock smaller so the reminder name leads.
- Shared top bar, list row and small button, so the same control looks the same everywhere.
- Settings: About row no longer looks tappable. Event log names are easier to read.

## 0.8.0 — Pause all (M6)
- Pause every reminder for 30 min, 1 h, 2 h, 4 h or until tomorrow.
- Home shows "Paused until …" with Resume now; rows read "Paused".
- When a pause ends, restart-timer reminders ring one interval later; fixed schedules ring at their next slot.

## 0.7.1 — Fix
- Editor: start and end time underlines line up when the "+1 DAY" badge shows.

## 0.7.0 — Settings
- Settings screen: theme (System, Light, Dark), reliability check, missed-after time, default snooze, event log, version.

## 0.6.0 — Big Type Home and editor (M5)
- Home: big countdown to the next ring, reminders sorted by next ring, on/off switches.
- Empty Home offers one-tap starters (water, walk, stretch).
- Full editor: label, interval presets or custom, active window (overnight and all-day), days, restart or keep schedule, notification or alarm, ring length, snooze, sound, vibrate, delete.

## 0.5.0 — Onboarding and reliability setup (M3)
- Welcome screen and two setup steps: permissions, then battery and Xiaomi settings (autostart, lock screen, background pop-ups).
- Each step is detected automatically and shows Done.
- Home shows a banner while a required setting is off.

## 0.4.1 — Alarm screen fixes
- Alarm screen opens straight from the ring, over the lock screen, and turns the display on.
- Reliable launch on Android 15 and later.

## 0.4.0 — Alarm-style rings (M4)
- New ring style: full-screen alarm that rings through silent mode for a set length, then counts as missed.
- The app plays its own sound and vibration, so rings are heard even when HyperOS mutes app notification sounds.
- Unanswered notifications are relabelled "Missed".

## 0.3.1 — Fix
- An unanswered ring no longer stalls the schedule: the next ring is booked the moment one rings.

## 0.3.0 — Working reminders (M2)
- Reminders are stored on the phone and ring on time as notifications with Dismiss and Snooze.
- Rescheduled after a reboot, app update, or time and time-zone change.
- Basic list and editor.

## 0.2.0 — Scheduling core (M1)
- Restart-from-dismiss and fixed-schedule timing, active windows (including overnight and all-day), chosen days, snooze, daylight-saving safe.
- Covered by unit tests.

## 0.1.0 — Project setup (M0)
- Android project (Kotlin, Jetpack Compose), build setup, CI, product spec and build plan.
