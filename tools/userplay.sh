#!/usr/bin/env bash
# User-perspective play test (Mauro, 2026-10-10: "you play a game to the end, in each mode, online and local,
# before a push"). Everything a player does is a real tap on the real screens: the start screen (seats,
# ruleset, name), Play or Host/Start game, the glowing board spots, and the on-screen buttons (Roll, End turn,
# discard cards and Discard, pickers, Age of Chivalry buttons). No debug launch flags and no move hooks; the
# game's log is only read to know what is on screen and when the game is over.
# Fails on a crash, an ANR, a turn that never moves on, or a game that doesn't end in time.
# Usage: tools/userplay.sh <base|chivalry> <local|online> [players=3] [minutes=40]
set -uo pipefail
RULESET="$1"; MODE="$2"; PLAYERS="${3:-3}"; MINUTES="${4:-40}"
ADB="${ADB:-adb}"
PKG=com.mauro.wgbg
export MSYS_NO_PATHCONV=1
OUT="${OUT:-$TEMP/wgbg-userplay}"; mkdir -p "$OUT"
TAG="$RULESET-$MODE-${PLAYERS}p"

dump() { "$ADB" shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; "$ADB" shell cat /sdcard/ui.xml | tr '>' '\n'; }
# Centre of the first on-screen text exactly $1 (optionally in $2 = a dump already taken).
find_text() {
  local d="${2:-$(dump)}"
  echo "$d" | grep -o "text=\"$1\"[^/]*bounds=\"\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]" | head -1 |
    grep -o '\[[0-9]*,[0-9]*\]' | tr -d '[]' | tr '\n' ' ' | awk '{split($1,a,",");split($2,b,",");print int((a[1]+b[1])/2), int((a[2]+b[2])/2)}'
}
tap_text() { local xy; xy=$(find_text "$1" "${2:-}"); [ -n "$xy" ] || return 1; "$ADB" shell input tap $xy; sleep 0.7; }
tap() { "$ADB" shell input tap "$1" "$2"; }
shot() { "$ADB" exec-out screencap -p > "$OUT/$TAG-$1.png"; }
crashed() { [ "$("$ADB" logcat -d -b crash | grep -A8 'FATAL EXCEPTION' | grep -c "$PKG")" != 0 ]; }
fail() { echo "USERPLAY $TAG FAILED: $1"; shot fail; "$ADB" logcat -d -b crash | grep -A30 "FATAL EXCEPTION" | head -40; exit 1; }

# A fresh install state, then in through the start screen like a player.
"$ADB" shell am force-stop $PKG
"$ADB" shell pm clear $PKG >/dev/null
"$ADB" logcat -c; "$ADB" logcat -b crash -c 2>/dev/null || true
"$ADB" shell monkey -p $PKG -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1   # the launcher icon
sleep 5
# First launch plays the intro fly-over; a player taps to skip it.
if dump | grep -q 'text="Tap to skip"'; then tap 1200 540; sleep 2; fi
d=$(dump)
# Seats: you plus bots; empty the last seats down to the player count.
if [ "$PLAYERS" -lt 4 ]; then
  # Seat columns are left to right (Seat 2, 3, 4); each has its own "Empty" button, in that order.
  # One line per Empty button, sorted left to right (the dump's order isn't guaranteed).
  empties=$(echo "$d" | grep -o 'text="Empty"[^/]*bounds="\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]' | grep -o 'bounds=.*' | tr -c '0-9\n' ' ' |
    awk '{print int(($1+$3)/2), int(($2+$4)/2)}' | sort -n -k1,1)
  for n in $(seq 4 -1 $((PLAYERS + 1))); do xy=$(echo "$empties" | sed -n "$((n - 1))p"); [ -n "$xy" ] && tap $xy && sleep 0.6; done
fi
[ "$RULESET" = chivalry ] && { tap_text "Age of Chivalry" || fail "no ruleset button"; }
if [ "$MODE" = online ]; then
  xy=$(find_text "You") || true; [ -n "$xy" ] && { tap $xy; sleep 0.5; "$ADB" shell input keyevent KEYCODE_MOVE_END; for _ in 1 2 3; do "$ADB" shell input keyevent KEYCODE_DEL; done
    "$ADB" shell input text "Tester"; "$ADB" shell input keyevent 66; sleep 1; }
  tap_text "Host online room" || fail "no Host online room button"
  sleep 8
  tap_text "Start game" || fail "no Start game (lobby: $(dump | grep -o 'text="[^"]*ready[^"]*"' | head -2))"
else
  tap_text "Play" || fail "no Play button"
fi
sleep 6
shot start
# The game must actually be on: the board asks for the first placement.
for _ in $(seq 1 20); do "$ADB" logcat -d -s WGBG-TURN:I | grep -q 'phase=' && break; sleep 1; done
"$ADB" logcat -d -s WGBG-TURN:I | grep -q 'phase=' || fail "the game never started (still on the start screen?)"
# Local games: speed the bots up from the menu like a player can (Menu, tap the speed button to 8x, Hide).
if [ "$MODE" = local ]; then
  tap_text "Menu" && for _ in 1 2 3 4 5; do
    lbl=$(dump | grep -o 'text="\(Max\|[0-9]*×\)"' | head -1 | cut -d'"' -f2)
    [ "$lbl" = "8×" ] && break; [ -n "$lbl" ] && tap_text "$lbl"
  done; tap_text "Hide"
fi

start=$(date +%s); moves=0; last=""; prev=""; same=0
while true; do
  [ $(( $(date +%s) - start )) -gt $((MINUTES * 60)) ] && fail "game not over after $MINUTES minutes ($moves moves)"
  crashed && fail "crash after $moves moves"
  if "$ADB" logcat -d -s WGBG-GAME:I WGBG-ONLINE:I | grep -q -E "over|game over"; then
    echo "USERPLAY $TAG: game over after $moves of my moves, $(( ($(date +%s) - start) / 60 )) min: $("$ADB" logcat -d -s WGBG-GAME:I WGBG-ONLINE:I | grep -E 'over' | tail -1 | grep -o 'over.*\|game over.*')"
    shot end; break
  fi
  line=$("$ADB" logcat -d -s WGBG-TURN:I | tail -1)
  legal=$(echo "$line" | sed -n 's/.* legal=\([^ ]*\).*/\1/p')
  if [ -z "$legal" ] || [ "$line" = "$last" ]; then
    # Not my move (bots playing, dice flying), or my last tap didn't take yet.
    same=$((same + 1)); [ "$same" -gt 90 ] && fail "stuck: ${line:0:200}"
    # A move of mine that didn't take: try the on-screen buttons again.
    if [ -n "$legal" ] && [ $((same % 6)) = 5 ]; then last=""; fi
    sleep 1; continue
  fi
  [ "$line" = "$prev" ] || same=0; last="$line"; prev="$line"
  spots=$(echo "$line" | sed -n 's/.* spots=\([^ ]*\).*/\1/p' | tr ';' '\n')
  d=$(dump)
  if [ -n "$spots" ] && [ $((RANDOM % 5)) != 0 ]; then
    pick=$(echo "$spots" | shuf -n 1); xy=${pick#*@}
    tap "${xy%,*}" "${xy#*,}"; sleep 0.6; tap "${xy%,*}" "${xy#*,}"
  elif echo "$d" | grep -q 'text="Discard [0-9]'; then
    # The discard sheet: tap cards until the Discard button lights, then press it.
    for _ in 1 2 3 4 5 6 7 8 9 10; do
      cards=$(echo "$d" | grep -o 'content-desc="\(Timber\|Clay\|Fleece\|Grain\|Stone\|Parchment\|Weave\|Silver\)"[^/]*bounds="[^"]*"' | grep -o '\[[0-9]*,[0-9]*\]' | tr -d '[]' | paste - - |
        awk '{split($1,a,",");split($2,b,",");print int((a[1]+b[1])/2), int((a[2]+b[2])/2)}' | shuf -n 1)
      [ -n "$cards" ] && tap $cards; sleep 0.4; d=$(dump)
    done
    tap_text "$(echo "$d" | grep -o 'text="Discard [0-9]*"' | head -1 | cut -d'"' -f2)" "$d" || true
  elif tap_text "Roll" "$d"; then :
  else
    # Any other on-screen choice (pickers, victims, Age of Chivalry buttons), else End turn.
    opts=$(echo "$d" | grep -o 'text="[^"]\+"' | cut -d'"' -f2 | grep -v -x -E 'Menu|Trade|Hide|Exit|Leave|Settings|End turn|Roll|Counter|Offer|You|Blue|Orange|White|Red|Your turn|[0-9/ ]+|.*turn.*|.*Tap .*|.*built.*|.*rolled.*|.*\+[0-9].*|Raiders|Zoom|Tilt' | head -12)
    if [ -n "$opts" ] && [ $((RANDOM % 3)) != 0 ]; then tap_text "$(echo "$opts" | shuf -n 1)" "$d" || true
    else tap_text "End turn" "$d" || { [ -n "$opts" ] && tap_text "$(echo "$opts" | shuf -n 1)" "$d"; } || true; fi
  fi
  moves=$((moves + 1))
  sleep 1.2
done
anr=$("$ADB" logcat -d | grep -c "ANR in $PKG" || true)
[ "$anr" = 0 ] || fail "ANR"
echo "USERPLAY $TAG PASSED"
