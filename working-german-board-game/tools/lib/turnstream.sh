#!/usr/bin/env bash
# Event-driven turn lines for the device scripts (#139): one logcat stream instead of a `logcat -d` poll a second,
# and a stall watchdog. Source it, then:
#   ts_open                 start the stream (clears the log first; call again after a restart to reopen)
#   ts_wait [secs]          wait up to secs (default 5) for a new WGBG-TURN line for seat 0; 0 and TS_LINE set when one
#                           came, 1 on a quiet wait; a WGBG-GAME "over" line sets TS_OVER=1 and returns 0
#   ts_check_stall [label]  when nothing new for TS_STALL s (default 45): print why (device, app alive, last line),
#                           save a screenshot, exit 2
# Needs ADB (adb binary) and ANDROID_SERIAL like the scripts that source it.
TS_PKG=${TS_PKG:-com.mauro.wgbg}
TS_STALL=${TS_STALL:-45}
TS_LINE=""; TS_OVER=0; TS_MOVED=$SECONDS; TS_PID=""

ts_open() {
  [ -n "$TS_PID" ] && kill "$TS_PID" 2>/dev/null
  "${ADB:-adb}" logcat -c
  exec 7< <(exec "${ADB:-adb}" logcat -v brief -s WGBG-TURN:I WGBG-GAME:I 2>/dev/null); TS_PID=$!
  trap 'kill $TS_PID 2>/dev/null' EXIT # never leave the stream running (it held the caller's pipe open)
  TS_LINE=""; TS_OVER=0; TS_MOVED=$SECONDS
}

ts_wait() {
  local until=$((SECONDS + ${1:-5})) line
  while [ $SECONDS -lt $until ]; do
    IFS= read -r -t 1 -u 7 line || continue
    case "$line" in
      *WGBG-GAME*over*) TS_OVER=1; TS_MOVED=$SECONDS; return 0 ;;
      *WGBG-TURN*player=0*) [ "$line" = "$TS_LINE" ] && continue; TS_LINE=$line; TS_MOVED=$SECONDS; return 0 ;;
      *WGBG-TURN*) TS_MOVED=$SECONDS ;;
    esac
  done
  return 1
}

# Anything the script did that counts as progress (a tap that took, a step passed) resets the watchdog.
ts_alive() { TS_MOVED=$SECONDS; }

ts_check_stall() {
  [ $((SECONDS - TS_MOVED)) -lt "$TS_STALL" ] && return 0
  local shot="${OUT:-.}/stuck-${1:-run}.png" # the working dir by default, like the trade scripts (CI uploads *.png)
  echo "STUCK${1:+ ($1)}: nothing new for ${TS_STALL}s. device=$("${ADB:-adb}" get-state 2>&1) app_pid=$("${ADB:-adb}" shell pidof $TS_PKG)"
  echo "last line: ${TS_LINE:0:300}"
  "${ADB:-adb}" exec-out screencap -p > "$shot" 2>/dev/null && echo "screenshot: $shot"
  exit 2
}
