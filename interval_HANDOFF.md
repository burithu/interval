# Interval — Handoff

Android app for repeating interval reminders (drink water, stand, stretch) with active hours; dismiss restarts the timer.

- **Last updated:** 2026-10-15
- **Branch:** `main` (pushed to github.com/burithu/interval). Working tree clean.
- **Last commit:** tag `v0.14.2` (handoff-on-push rule). Previous: `bd18bed` (handoff refresh), `a0b44e9` (v0.14.1).
- **Version:** 0.14.2 (versionCode 17). The phone runs 0.14.0; 0.14.1 and 0.14.2 change only debug code and docs.

## Resume here
- **Emulator checklist is done** (step 1, finished 2026-10-13). Two v1.0 steps remain; ask the Pilot before starting either:
  1. **Xiaomi 13 checklist**, twice on separate days, including the overnight test 14, plus the ring matrix on the phone. Needs the Pilot: HyperOS blocks `input`, so they must lock, tap and switch modes. Recommended next.
  2. **Signing key, then the signed v1.0.0 GitHub release.** Last step. Warn that a lost key means no in-place updates.
- **The phone ring matrix wipes reminders.** `tools/ring-matrix.sh` deletes every reminder on the device; the Pilot said "not rn" (2026-10-13). Warn before running it.
- **Seed checklist reminders** in debug builds: `adb shell am broadcast -n io.github.burithu.interval.debug/io.github.burithu.interval.DebugReceiver -a seed`. This replaces all reminders.
- **After any code change:** build, test and lint. Every push follows the release workflow.

## Goal & scope
- **For:** desk workers. Several reminders, each with:
  - interval
  - active window (overnight and 24 h supported)
  - days
  - restart-from-dismiss or fixed timing
  - notification or full-screen alarm style
- **Targets every Android phone (D36).** The Xiaomi 13 (HyperOS, Android 16) is the only physical device; a stock Android 17 emulator is the second.
- **Local data only:** Room plus DataStore, with Auto Backup. No accounts and no sync.
- **Out of scope for v1:** history and stats, Quick Settings tile, widget, export/import, Play Store. See SPEC "Later".
- **Full spec:** [docs/SPEC.md](docs/SPEC.md). **Plan:** [docs/PLAN.md](docs/PLAN.md).

## Current status
- [x] M0 Setup · M1 Scheduling core · M2 Working reminders · M3 Onboarding · M4 Alarm rings · M5 Big Type UI · M6 Pause / Settings / log
- M7 Hardening & v1.0:
  - [x] Accessibility (D38)
  - [x] Auto Backup with recompute on restore (D37)
  - [x] Setup for other phone makers (D36)
  - [x] Rings under Do Not Disturb and Android 17 audio hardening (D39). 51-ring matrix passes on the emulator.
  - [x] Emulator checklist: 1–13 plus 12b and 12c pass (runs 2026-10-12 and 2026-10-13). Test 14 (overnight) moves to the Xiaomi runs.
  - [ ] Xiaomi 13 checklist ×2 on separate days, plus the ring matrix on the phone (unverified on HyperOS)
  - [ ] Signed APK as GitHub Release v1.0.0
- Test record: [docs/CHECKLIST.md](docs/CHECKLIST.md), which has a Runs table.

## Recent changes (newest first)
- **v0.14.2** (2026-10-15): `CLAUDE.md` now says to update this handoff before every push; the handoff skill got the same rule. Version bump and CHANGELOG only; the app is unchanged.
- `a0b44e9` **v0.14.1:** emulator checklist complete.
  - `DebugReceiver.kt` gets the `seed` action, with `--ez r2 false` and `--ei r1Minutes n`.
  - `docs/CHECKLIST.md`: test 4 explains the 1-hour Doze rule; one-line seed setup; run row for 2026-10-13.
  - PLAN: emulator item checked.
  - The app itself is unchanged.
- `00b9ed1` **v0.14.0**: Full alarms ring under Do Not Disturb; the Android 17 chime fix.
  - New `ringing/RingOutput.kt`: a pure `ringOutput(reminder, PhoneState)` that decides sound and vibration.
  - `RingService.kt`:
    - `phoneState()` reads the ringer mode, the DND filter and whether the DND policy allows alarms.
    - On API 37+ the notification chime plays as `USAGE_ALARM` at notification-volume scale (`notificationVolumeOnAlarmStream()`).
    - Logs under tag `IntervalRing`.
  - Tests: `test/.../ringing/RingOutputTest.kt`.
  - Debug-only `app/src/debug/` holds `DebugReceiver` (exported; actions `ringer`, `ring`, `clear`) and a manifest with ACCESS_NOTIFICATION_POLICY.
  - `tools/ring-matrix.sh`: the 51-ring matrix.
  - Docs: SPEC D39, CHECKLIST tests 12b and 12c with a run row, CHANGELOG.
  - Installed on the phone.
- `4a41e57` **v0.13.1**: after a time-zone change, a restart ring could be kept outside the window.
  - Fix in `Scheduler.kt`, `isInWindow`.
  - Added CHECKLIST.md and the first emulator run.
- `e02278a` **v0.13.0**: per-maker setup (`onboarding/SetupChecks.kt`, `Maker` enum), restore handling (`data/RestoreAgent.kt`, `ReminderEngine.onRestored`), accessibility pass.
- `3f70bd3` **v0.12.0**: app icon A1. `748799e` **v0.11.0**: Vibrate on silent.

## Key decisions
The full table D1–D39 is in [docs/SPEC.md §Decisions](docs/SPEC.md). The ones most likely to matter next:

- **D30:** every ring uses `setAlarmClock()`. HyperOS delayed plain exact alarms by 13–23 min. Rejected: `setExactAndAllowWhileIdle`.
- **App plays its own sound and vibration** (RingService). HyperOS mutes per-app notification sound by default, so the channels are silent. Rejected: channel sounds.
- **D33 (2026-10):** each alarm has a type: Screen only, Vibrate, or Full.
  - Silent mode shows the screen only; vibrate mode drops the sound.
  - This overrides the old "alarms ring through silent".
- **D34:** Vibrate on silent, per reminder, using alarm vibration usage.
- **D36 (2026-10-12):** all Android phones, not only Xiaomi.
- **D39 (2026-10-13):**
  - Android reports DND as ringer SILENT at every level, so alarms follow the DND policy instead. They ring if DND allows alarms and show only under Total silence.
  - Notifications treat DND as silent.
  - On stock Android, silent mode = DND, so a Full alarm rings on silent there. On Xiaomi, silent is separate and stays screen only.
  - Android 17 chime on the alarm usage: the only audio-hardening exemption is USAGE_ALARM with exact-alarm permission.
  - Rejected: a mediaPlayback service type (a service started from the background gets no while-in-use access).
- **D35:** icon A1 (arc radius 18, stroke 7). Rejected options: Big I, Rhythm, Dial, Brackets, Progress.

## Architecture map
`app/src/main/java/io/github/burithu/interval/`:
- `IntervalApp.kt`: `AppContainer`, the manual dependency wiring. Consumes the restore marker on start.
- `data/`
  - `Reminder.kt`: domain model and enums.
  - `SettingsStore.kt`: DataStore.
  - `RestoreAgent.kt`
  - `db/`: Room DB v3 with AutoMigrations 1→2→3; schemas in `app/schemas/`; event log.
- `scheduling/`
  - `Scheduler.kt`: the pure `nextRing()`, covered by most unit tests.
  - `ReminderEngine.kt`: every state change, under a mutex; `onRing`, `onAction`, `rescheduleAll`, pause.
- `ringing/`
  - `AlarmScheduler.kt`: `setAlarmClock`.
  - `Receivers.kt`: ring, notification-action and reschedule receivers.
  - `Ringer.kt`: active sessions.
  - `RingService.kt`: foreground service; sound, vibration, alarm screen launch.
  - `RingOutput.kt`: the mode rules.
  - `Notifier.kt`: channels and notifications.
- `ui/`
  - `home`, `editor`, `alarm/AlarmActivity.kt` (over the lock screen), `settings`, `log`.
  - `Components.kt`: shared controls.
  - `theme/`: tokens; `CappedFontScale`.
- `onboarding/`: welcome and setup screens; `SetupChecks.kt` (permission checks, the `Maker` steps).
- `app/src/debug/`: `DebugReceiver`, for the test harness only.
- `tools/ring-matrix.sh`: ring-mode matrix.
- `docs/`: SPEC, PLAN, CHECKLIST, licenses.

## How to run / build / test / release
- **Build, test and lint** (PowerShell):
  - Command: `$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat assembleDebug testDebugUnitTest lintDebug -q`
  - Lint count: `(Select-String -Path app\build\reports\lint-results-debug.sarif -Pattern '"ruleId"').Count`. Expect 0.
  - Last verified 2026-10-15 at v0.14.2: build OK, all unit tests pass, 0 lint issues.
- **CI:** `.github/workflows` runs unit tests and assembleDebug on every push.
- **Phone:**
  - Serial `5a3a83ff`, package `io.github.burithu.interval.debug`.
  - Install: `adb -s 5a3a83ff install -r app/build/outputs/apk/debug/app-debug.apk`
  - HyperOS blocks `input` and `settings put`, so Claude cannot tap or lock it.
  - A 15580-byte screenshot means the screen is asleep.
- **Emulator:**
  - AVD `Medium_Phone_API_37.0` (Android 17, default time zone Asia/Tokyo), serial `emulator-5554`.
  - Start: `"$LOCALAPPDATA/Android/Sdk/emulator/emulator.exe" -avd Medium_Phone_API_37.0 -no-window -no-audio -no-boot-anim -no-snapshot-save -gpu swiftshader_indirect -memory 2048`
  - `input` and `settings` work on it.
  - It has been killed for low memory before, hence `-memory 2048`.
- **Git Bash quirks:**
  - Set `MSYS_NO_PATHCONV=1` for `/sdcard` paths; then pass APK paths as `F:/...`, not `/f/...`.
  - adb output has CRLF line endings; pipe it through `tr -d '\r'`.
- **Ring matrix:** `bash tools/ring-matrix.sh out.log [serial]`.
  - Replaces every reminder on the device.
  - Sets the ringer by briefly opening the app, because Android 17 ignores `setRingerMode` from the background.
  - About 12 min. Evidence: logcat `IntervalRing`, `dumpsys audio`, `dumpsys vibrator_manager`.
- **Release** (every push):
  1. Bump `versionCode` and `versionName` in `app/build.gradle.kts`.
  2. Add a `## X.Y.Z — Title` section to CHANGELOG.md.
  3. Commit (no attribution lines), then `git tag -a vX.Y.Z -m X.Y.Z`, then `git push origin HEAD vX.Y.Z`.
  4. Extract the notes: `awk -v v="X.Y.Z" '/^## /{p = ($2==v)} p && !/^## /' CHANGELOG.md > notes.md`
  5. Publish: `gh release create vX.Y.Z --prerelease --verify-tag --title "X.Y.Z — Title" --notes-file notes.md interval-X.Y.Z-debug.apk`
- **Signing:** no key exists yet.
  - `.gitignore` already excludes `*.jks`, `*.keystore` and `keystore.properties`.
  - Keep the key outside the repo; never commit it.

## Known issues & risks
- **HyperOS behaviour unverified** for D39 (silent vs DND on Xiaomi) and the per-maker vendor intents (D36); the emulator is stock Android. (unverified)
- **Under DND Total silence**, Android blocks even "Vibrate on silent". This is intentional; the app can't override it.
- **Android 17 chime volume** is a linear approximation of the notification volume on the alarm stream. If the alarm volume is lower than the notification volume, the chime is quieter.
- **On stock Android, silent = DND**, so a Full alarm rings when the phone is set to silent. This is intentional (D39).
- **`DebugReceiver` is exported** in debug builds only (`tools:ignore="ExportedReceiver"`). It must never move to `main`.
- **Deep Doze needs two conditions on the emulator:**
  - It must not report charging: run `dumpsys battery unplug`, then `reset` afterwards.
  - No alarm clock may be due within 1 h (`min_time_to_alarm`; this Play Store image blocks changing it). Use `seed --ez r2 false --ei r1Minutes 62`, then about 65 min of waiting.
  - In real use, any reminder under 1 h keeps the phone out of deep Doze.
- **`cmd alarm set-timezone` doesn't survive an emulator reboot.**
- **The "Ring for" picker's minimum is 30 s**, although the model allows 15 s.
- **Session scratch scripts are not in the repo** and disappear with the session scratchpad. Recreate them as needed:
  - `ui.py`: uiautomator dump, then tap by text.
  - `evlog.py`: copies `databases/interval.db` (plus `-wal` and `-shm`) via `exec-out run-as … cat` and prints `event_log` with lateness.
  - `t4c.sh`: the deep-Doze run.
  - Run Windows python on `cygpath -w` paths.

## Open questions
- When to run the Xiaomi checklist days and the phone ring matrix (the matrix wipes reminders).
- The v1.0 signing key: where the Pilot wants it backed up. Warn that a lost key means no in-place updates.

## User preferences & conventions
- **Persona:** BT-7274 (global CLAUDE.md). Address the user as "Pilot"; clipped, declarative; no exclamation points except alerts.
- **Commits and PRs:** never add attribution or Co-Authored-By lines.
- **Every push:** version bump, CHANGELOG entry, annotated tag, GitHub pre-release with the APK attached.
- **Every push also updates this handoff** in the same commit (added 2026-10-15). After the push, tell the Pilot in chat which sections changed.
- **Design or visual choices:** show rendered side-by-side images (HTML mockups rendered with headless Edge), not text.
- **Plan for every Android maker**, not just Xiaomi.
- **Ask before starting** the next milestone step ("wait for confirmation before starting").
- **After a release**, install it on the phone when it is connected; the Pilot asks for this regularly.
- **Code style:** match the surrounding Kotlin idiom and comment density. Record new product decisions as `D<n>` in the SPEC table.

## Session log
- 2026-10-15: status checks (`/handoff-progress`, handoff refresh). Added the rule that every push updates this handoff (skill + `CLAUDE.md`); released v0.14.2 (docs only). Waiting on the Pilot to start the Xiaomi checklist.
- 2026-10-13: v0.14.0.
  - The Pilot reported "Full alarm doesn't work anymore".
  - Found two bugs: DND reported as silent muted Full alarms, and Android 17 audio hardening muted the background chime.
  - Fixed both (D39); built the debug harness and the 51-ring matrix; all pass on the emulator.
  - Installed on the phone. Next steps offered; the Pilot deferred the phone matrix.
  - Created this handoff and `CLAUDE.md`.
  - Then step 1, v0.14.1: emulator tests 4 (including real deep Doze, 62-min ring 0 s late), 9 (PIN lock), 11 (snooze) and 13 (pause) all pass. The emulator checklist is complete.
- 2026-10-12: v0.11.0–v0.13.1.
  - Vibrate on silent; icon A1; all-Android setup, restore, accessibility.
  - First emulator checklist run; time-zone fix.
