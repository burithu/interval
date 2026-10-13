# Interval — Reliability checklist

Run on every test device before v1.0, and again after any change to scheduling or ringing.
Evidence comes from the event log (Settings → Event log): each ring shows `RANG` with how late it was.

**Pass:** every ring arrives within 60 s of its scheduled time (except where noted) and the next ring is booked.

## Setup
- Complete onboarding; every step Done.
- R1: notification, every 1 min (Custom), all day (same start and end), every day, Restart timer.
- R2: alarm (Full alarm), every 2 min, otherwise as R1. Ring for 30 s, snooze 5 min.
- Settings → Missed after: 2 min, so unanswered rings keep the schedule moving (about every 3 min for R1).
- Keep the time inside the active window for the whole run.

## Tests
| # | Test | How | Expect |
|---|---|---|---|
| 1 | Foreground | App open, screen on | R1 rings; Dismiss books the next ring 1 min later |
| 2 | Background | Home button, wait 3 rings | All on time |
| 3 | Swiped from recents | Clear Interval from recent apps, wait 3 rings | All on time |
| 4 | Screen off / Doze | Screen off 30 min or more (emulator: force idle) | All on time; alarms turn the screen on |
| 5 | Battery saver | Turn battery saver on, wait 3 rings | All on time |
| 6 | Restricted app bucket | Emulator: `am set-standby-bucket <pkg> restricted` | All on time |
| 7 | Reboot | Restart the phone, don't open the app | Rings resume within one interval |
| 8 | Time zone change | Change time zone | Next ring stays the same number of minutes away |
| 9 | Alarm over lock screen | Lock the phone; wait for R2 | Alarm screen shows over the lock screen with sound |
| 10 | Missed | Ignore R2 | Stops after 30 s, notification says Missed, next ring booked |
| 11 | Snooze | Snooze from notification and alarm screen | Rings again after the snooze length |
| 12 | Silent mode | Ringer on silent | No sound or vibration; alarm shows its screen; with Vibrate on silent: vibrates only |
| 12b | Do Not Disturb | DND on (default: alarms allowed), then Total silence | Full alarm rings with sound; notifications silent (vibrate only with Vibrate on silent). Total silence: screen only |
| 12c | Ring-mode matrix | Debug build: `tools/ring-matrix.sh <log> [serial]` drives every ring type × normal, vibrate, silent, DND levels, locked | Each ring matches SPEC "Ring style and phone mode" |
| 13 | Pause all | Pause 30 min, then Resume | No rings while paused; Resume restarts from now |
| 14 | Overnight | Leave R1 off, a 2 h reminder on overnight | Morning log shows no gaps or late rings |

## Runs
| Date | Device | Android / skin | Result | Notes |
|---|---|---|---|---|
| 2026-10-12 | Emulator, Medium Phone | Android 17, stock | Partial: 1, 2, 3, 5, 6, 7, 8, 10 pass | Every ring 0 s late. Test 8 found a bug (restart ring kept outside the window after a time-zone change), fixed in 0.13.1. Test 4: rings with screen off on time, but deep Doze never entered (emulator reports charging; rerun with `dumpsys battery unplug`). Not run: 9, 11, 12, 13, 14 |
| 2026-10-13 | Emulator, Medium Phone | Android 17, stock | 12, 12b, 12c pass (0.14.0) | Matrix before the fix found two bugs: DND silenced Full alarms (Android reports DND as silent), and Android 17 muted the background notification chime. After: 51 rings, all as specified, including locked screen. On stock Android, silent mode turns on DND, so it behaves as DND |
