#!/bin/bash
# Ring-mode matrix (CHECKLIST 12c): every ring type under normal, vibrate, silent, each Do Not
# Disturb level, and locked. Needs the debug build installed with setup done.
# Usage: tools/ring-matrix.sh <outfile> [serial]
# Replaces all reminders on the device with test reminders.
export MSYS_NO_PATHCONV=1
ADB="$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe"
S="${2:-emulator-5554}"
A="$ADB -s $S"
P=io.github.burithu.interval.debug
R="$P/io.github.burithu.interval.DebugReceiver"
OUT="$1"
: > "$OUT"
say() { echo "$*" | tee -a "$OUT"; }

ringer() { # 0 silent, 1 vibrate, 2 normal; the app must be visible to change it (Android 17)
  $A shell am start -W -n $P/io.github.burithu.interval.MainActivity >/dev/null
  sleep 1
  $A shell am broadcast -n $R -a ringer --ei mode $1 >/dev/null
  sleep 1
  $A shell input keyevent KEYCODE_HOME
  sleep 2
}

ring() { # name, extras...
  local name="$1"; shift
  $A logcat -c
  local t0; t0=$($A shell date +%H:%M:%S | tr -d '\r')
  $A shell am broadcast -n $R -a ring "$@" >/dev/null
  sleep 8
  local pid; pid=$($A shell pidof $P | tr -d '\r')
  local decision; decision=$($A logcat -d -s IntervalRing | grep "ring " | sed 's/.*IntervalRing: //' | tr -d '\r')
  local started; started=$($A logcat -d -s IntervalRing | grep -c "sound started")
  local muted; muted=$($A logcat -d | grep -c "background playback muted for $P")
  local player; player=$($A shell dumpsys audio | grep "u/pid:[0-9]*/$pid state:started" | grep -o "usage=[A-Z_]*\|mutedState:[^ ]*" | tr '\n' ' ' | tr -d '\r')
  local sline; sline=$($A logcat -d -s IntervalRing | grep "sound started" | sed 's/.*sound started: //' | tr -d '\r')
  local top; top=$($A shell dumpsys activity activities | grep -m1 topResumedActivity | grep -o "AlarmActivity\|MainActivity\|Launcher[A-Za-z]*\|NexusLauncher[A-Za-z]*" | tr -d '\r')
  local screen; screen=$($A shell dumpsys power | grep -m1 -o "mWakefulness=[A-Za-z]*" | tr -d '\r')
  $A shell am broadcast -n $R -a clear >/dev/null
  sleep 2
  # Repeating alarm vibrations reach the history only once the ring is cleared.
  local vib; vib=$($A shell dumpsys vibrator_manager | tr -d '\r' | grep "$P" | grep -o "| *[a-z_]* | duration.*start: [0-9:.]*.*usage: [A-Z]*" \
    | awk -v t0="$t0" '{ match($0, /start: [0-9:]+/); s = substr($0, RSTART + 7, 8); if (s >= t0) { match($0, /usage: [A-Z]+/); u = substr($0, RSTART, RLENGTH); split($0, f, "|"); gsub(/ /, "", f[2]); print f[2] " " u } }' | sort -u | tr '\n' ';')
  say "[$name] $decision"
  say "    started=$started hardeningMuted=$muted player=[$player] top=$top $screen"
  say "    sound=[$sline] vibrations=[$vib]"
}

cases() {
  ring notif            --es style NOTIFICATION --ez vibrate true
  ring notif-novib      --es style NOTIFICATION --ez vibrate false
  ring notif-vos        --es style NOTIFICATION --ez vibrate true --ez vos true
  ring alarm-screen     --es style ALARM --es alarmMode SCREEN
  ring alarm-vibrate    --es style ALARM --es alarmMode VIBRATE
  ring alarm-full       --es style ALARM --es alarmMode FULL
  ring alarm-vib-vos    --es style ALARM --es alarmMode VIBRATE --ez vos true
  ring alarm-full-vos   --es style ALARM --es alarmMode FULL --ez vos true
}

$A shell input keyevent KEYCODE_WAKEUP; $A shell wm dismiss-keyguard
$A shell cmd notification set_dnd off
say "=== NORMAL";  ringer 2; cases
say "=== VIBRATE"; ringer 1; cases
say "=== SILENT";  ringer 0; cases
$A shell cmd notification set_dnd off; sleep 1
say "=== DND priority (alarms allowed by default)"; ringer 2; $A shell cmd notification set_dnd priority; sleep 1; cases
$A shell cmd notification set_dnd off; sleep 1
say "=== DND alarms only"; ringer 2; $A shell cmd notification set_dnd alarms; sleep 1; cases
$A shell cmd notification set_dnd off; sleep 1
say "=== DND total silence"; ringer 2; $A shell cmd notification set_dnd none; sleep 1; cases
$A shell cmd notification set_dnd off; sleep 1
ringer 2
locked() { $A shell input keyevent KEYCODE_SLEEP; sleep 3; ring "$@"; }
say "=== NORMAL, screen off and locked"
locked notif        --es style NOTIFICATION --ez vibrate true
locked alarm-full   --es style ALARM --es alarmMode FULL
locked alarm-vibrate --es style ALARM --es alarmMode VIBRATE
$A shell input keyevent KEYCODE_WAKEUP; $A shell wm dismiss-keyguard
say "=== done"
