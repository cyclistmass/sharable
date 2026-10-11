#!/usr/bin/env bash
# Takes the README screenshot gallery (docs/screenshots/*.jpg, 1200x540) on the emulator: the tilted board,
# your pieces on the water, an Age of Chivalry endgame (Pirate theme), the trade chest, the Inventory and
# Settings. Run after tools/emulator.ps1 and installing the latest debug build.
# Usage (Git Bash, from the project root): tools/record_screenshots.sh
set -euo pipefail
A="$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe"
PKG=com.mauro.wgbg
export MSYS_NO_PATHCONV=1
export ANDROID_SERIAL="${ANDROID_SERIAL:-emulator-5554}"
OUT="${OUT:-$TEMP/wgbg-shots}"
mkdir -p "$OUT" docs/screenshots

settings() { # fresh app with these saved settings (run-as works on debug builds)
  "$A" shell am force-stop $PKG
  "$A" shell pm clear $PKG >/dev/null
  { echo "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>"; echo "<map>"
    for kv in "$@"; do echo "    <float name=\"${kv%=*}\" value=\"${kv#*=}\" />"; done; echo "</map>"; } > "$OUT/prefs.xml"
  "$A" push "$(cygpath -w "$OUT/prefs.xml")" /data/local/tmp/wgbg-prefs.xml >/dev/null
  "$A" shell chmod 644 /data/local/tmp/wgbg-prefs.xml
  "$A" shell "run-as $PKG sh -c 'mkdir -p shared_prefs && cp /data/local/tmp/wgbg-prefs.xml shared_prefs/wgbg-settings.xml'"
}
tap_text() { # tap the on-screen element whose text is $1
  local B="" try
  # Retry for up to 10 s: a cold start after pm clear can take longer than the sleep before the tap.
  for try in 1 2 3 4 5 6 7 8 9 10; do
    "$A" shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
    B=$("$A" shell cat /sdcard/ui.xml | tr '>' '\n' | grep -o "text=\"$1\"[^/]*bounds=\"\[[0-9]*,[0-9]*\]" | grep -o '\[[0-9]*,[0-9]*\]' | tr -d '[]' | head -1 || true)
    [ -n "$B" ] && break; sleep 1
  done
  [ -n "$B" ] || { echo "'$1' not found on screen"; exit 1; }
  "$A" shell input tap $(( ${B%,*} + 20 )) $(( ${B#*,} + 20 ))
  sleep 0.8
}

# Like tap_text, but fine if it's gone: at Max speed a game can end and the next one starts with the menu closed.
tap_text_opt() { ( tap_text "$1" ) >/dev/null || true; }

start() { # $1 ruleset, then extra am-start arguments
  local rules="$1"; shift
  "$A" shell am start --activity-clear-task -n $PKG/.MainActivity --es start "$rules" "$@" >/dev/null
  sleep 4
}
shot() { # screen -> docs/screenshots/$1.jpg
  "$A" exec-out screencap -p > "$OUT/$1.png"
  py -c "from PIL import Image; Image.open(r'$(cygpath -w "$OUT/$1.png")').convert('RGB').resize((1200,540), Image.LANCZOS).save(r'docs/screenshots/$1.jpg', quality=85, optimize=True)"
}
play_setup() { # place your opening pieces through the agent hooks (no debug badge), up to the first roll
  local last="" line legal mv
  for _ in $(seq 1 80); do
    line=$("$A" logcat -d -s WGBG-TURN:I | tail -1)
    legal=$(echo "$line" | sed -n 's/.*legal=\([^ ]*\).*/\1/p')
    case "$legal" in *ROLL*) return 0;; esac
    if [ "$line" != "$last" ] && [ -n "$legal" ]; then
      mv=$(echo "$legal" | tr ',' '\n' | grep -E "^BUILD_(SETTLEMENT|ROAD)" | shuf -n 1 || true)
      [ -n "$mv" ] && "$A" shell am broadcast -a $PKG.MOVE -p $PKG --es move "$mv" >/dev/null
      last="$line"
    fi
    sleep 0.6
  done
}
one_game() { # watching: stop on the finished board instead of starting the next
  tap_text "Menu"; tap_text "Nonstop"; tap_text_opt "Hide"; sleep 6
}

# 1. The tilted board late in a game (Default theme): rendered towns and roads, Bandit V2.
settings "view-bars=0.0"
start base --ez watch true --ef tilt 0.7 --ef zoom 1.9
one_game; shot board-tilted
# 2. Age of Chivalry endgame in the Pirate theme: walls, Capitals, knights.
settings "view-bars=0.0" "theme-pirate=1.0" "event-die-style=3.0"
start chivalry --ez watch true --ef tilt 0.6 --ef zoom 1.7
one_game; shot chivalry-endgame
# 3. Your unplayed pieces on the water below the board (pan down).
settings "view-bars=0.0"
"$A" logcat -c
start base --ef tilt 0.45 --ef zoom 1.5
play_setup
"$A" shell input swipe 1500 900 1500 250 900; sleep 1.5
shot your-pieces
# 4. The trade chest after rolling.
"$A" shell am broadcast -a $PKG.MOVE -p $PKG --es move ROLL:0 >/dev/null; sleep 4
# A 7 means the Bandit (and maybe discards and a steal) first: play those out until the turn is back to trading.
for _ in $(seq 1 12); do
  legal=$("$A" logcat -d -s WGBG-TURN:I | tail -1 | sed -n 's/.* legal=\([^ ]*\).*/\1/p')
  echo "$legal" | grep -q 'END_TURN' && break
  mv=$(echo "$legal" | tr ',' '\n' | head -1 || true)
  [ -n "$mv" ] && "$A" shell am broadcast -a $PKG.MOVE -p $PKG --es move "$mv" >/dev/null; sleep 2
done
"$A" shell input tap 1200 540 >/dev/null 2>&1 || true
tap_text "Trade"; sleep 1
shot trade-chest
# 5. The Inventory (every object), then 6. Settings scrolled to the event die styles.
tap_text "Close"; sleep 0.5
tap_text "Menu"; tap_text "Inventory"; tap_text_opt "Hide"; sleep 1
shot inventory
tap_text "Menu"; "$A" shell input tap 1580 254; sleep 1.2
"$A" shell input swipe 1900 950 1900 520 500; sleep 1
shot settings
ls -la docs/screenshots
