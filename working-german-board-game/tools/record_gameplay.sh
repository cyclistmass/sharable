#!/usr/bin/env bash
# Re-records docs/gameplay-1x.gif: 20 s of 1x bot play on the emulator.
# Run after tools/emulator.ps1 and installing the latest debug build.
# Usage (Git Bash, from the project root): tools/record_gameplay.sh [seconds]
set -euo pipefail
SECS="${1:-20}"
A="$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe"
export MSYS_NO_PATHCONV=1

"$A" shell am force-stop com.mauro.wgbg
"$A" shell am start -n com.mauro.wgbg/.MainActivity >/dev/null
sleep 4

# Taps the on-screen button whose text is $1 (found with uiautomator).
tap_text() {
  "$A" shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  local B
  B=$("$A" shell cat /sdcard/ui.xml | tr '>' '\n' | grep -o "text=\"$1\"[^/]*bounds=\"\[[0-9]*,[0-9]*\]" | grep -o '\[[0-9]*,[0-9]*\]' | tr -d '[]' | head -1)
  [ -n "$B" ] || { echo "button '$1' not found (is the panel layout changed?)"; exit 1; }
  "$A" shell input tap $(( ${B%,*} + 20 )) $(( ${B#*,} + 20 ))
  sleep 0.7
}

# The app opens on the game-start screen: Play, then open the menu, switch to Watch bots
# (starts at Max), Max -> 1x, close the menu.
tap_text "Play"
sleep 3
"$A" shell input tap 2270 84
sleep 1
tap_text "Watch bots"
tap_text "Max"
"$A" shell input tap 2270 84
sleep 3

"$A" shell screenrecord --size 1200x540 --bit-rate 6000000 --time-limit "$SECS" /sdcard/play.mp4
TMP="$(cygpath -w "$TEMP/play.mp4")"
"$A" pull /sdcard/play.mp4 "$TMP" >/dev/null

mkdir -p docs
ffmpeg -loglevel error -y -i "$TMP" \
  -vf "fps=8,scale=640:-1:flags=lanczos,split[s0][s1];[s0]palettegen=max_colors=128[p];[s1][p]paletteuse=dither=sierra2_4a" \
  docs/gameplay-1x.gif
ls -la docs/gameplay-1x.gif
