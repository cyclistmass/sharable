#!/usr/bin/env bash
# User-perspective play test (Mauro, 2026-10-10: "you play a game to the end, in each mode, online and local,
# before a push"). Everything a player does is a real tap on the real screens: the start screen (seats,
# ruleset, name), Play or Host/Start game, the glowing board spots, and the on-screen buttons (Roll, End turn,
# discard cards and Discard, pickers, Age of Chivalry buttons). No debug launch flags and no move hooks; the
# game's log is only read to know what is on screen and when the game is over.
# Fails on a crash, an ANR, a turn that never moves on, or a game that doesn't end in time.
# Two people on two phones (Mauro, 2026-10-11): run "host" on one emulator and "join" on another at the same
# time; the host hosts a room with seat 2 as a Friend, the joiner opens Join a room and types the room code,
# both by taps, and each plays its own seat to the end on the real server.
# Each run drives only its own device (ANDROID_SERIAL is required) and keeps its own folder, so two runs at
# once never share files or taps.
# Usage: ANDROID_SERIAL=emulator-5554 tools/userplay.sh <base|chivalry> <local|online|host|join> [players=3] [minutes=40]
#   host/join meet through ROOM_FILE (default $TEMP/wgbg-userplay/room-code); start the host first.
set -uo pipefail
RULESET="$1"; MODE="$2"; PLAYERS="${3:-3}"; MINUTES="${4:-40}"
: "${ANDROID_SERIAL:?set ANDROID_SERIAL to the device to drive (lane 1 emulator-5554, lane 2 emulator-5556)}"
export ANDROID_SERIAL
ADB="${ADB:-adb}"
PKG=com.mauro.wgbg
export MSYS_NO_PATHCONV=1
OUT="${OUT:-$TEMP/wgbg-userplay/$ANDROID_SERIAL}"; mkdir -p "$OUT"
ROOM_FILE="${ROOM_FILE:-$TEMP/wgbg-userplay/room-code}"
TAG="$RULESET-$MODE-${PLAYERS}p"
# One run per device: a second run on the same device would tap over this one.
if [ -f "$OUT/run.pid" ] && kill -0 "$(cat "$OUT/run.pid")" 2>/dev/null; then
  echo "USERPLAY: another run (pid $(cat "$OUT/run.pid")) is already driving $ANDROID_SERIAL; stop it first"; exit 1
fi
echo $$ > "$OUT/run.pid"; trap 'rm -f "$OUT/run.pid"' EXIT

# The screen's text. A uiautomator left over from a stopped run blocks new ones ("already registered"): clear it and retry.
dump() {
  local x
  for _ in 1 2 3; do
    "$ADB" shell rm -f /sdcard/ui.xml; "$ADB" shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
    x=$("$ADB" shell cat /sdcard/ui.xml 2>/dev/null); case "$x" in *hierarchy*) echo "$x" | tr '>' '\n'; return;; esac
    "$ADB" shell pkill -f uiautomator 2>/dev/null; sleep 1
  done
}
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
# Centres of every element with text $1, left to right.
all_text() { echo "$2" | grep -o "text=\"$1\"[^/]*bounds=\"\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]" | grep -o 'bounds=.*' | tr -c '0-9\n' ' ' |
  awk '{print int(($1+$3)/2), int(($2+$4)/2)}' | sort -n -k1,1; }
# Type into a text box (tap it, clear it, type).
type_into() { tap $1 $2; sleep 0.5; "$ADB" shell input keyevent KEYCODE_MOVE_END; for _ in $(seq 1 14); do "$ADB" shell input keyevent KEYCODE_DEL; done
  "$ADB" shell input text "$3"; sleep 0.5; }

# A fresh install state, then in through the start screen like a player.
"$ADB" shell am force-stop $PKG
"$ADB" shell pm clear $PKG >/dev/null
"$ADB" logcat -c; "$ADB" logcat -b crash -c 2>/dev/null || true
"$ADB" shell monkey -p $PKG -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1   # the launcher icon
sleep 5
# First launch plays the intro fly-over; a player taps to skip it.
if dump | grep -q 'text="Tap to skip"'; then tap 1200 540; sleep 2; fi
d=$(dump)
if [ "$MODE" = join ]; then
  # The friend: Join a room, their name and the code the host read out, Join, then mark themselves ready.
  for _ in $(seq 1 90); do [ -s "$ROOM_FILE" ] && break; sleep 2; done
  code=$(cat "$ROOM_FILE" 2>/dev/null); [ -n "$code" ] || fail "no room code from the host in $ROOM_FILE"
  # Online needs a name first (the buttons stay off for "You").
  xy=$(find_text "You" "$d") || true; [ -n "$xy" ] && { type_into $xy "Duo"; "$ADB" shell input keyevent KEYCODE_BACK; sleep 1; }
  tap_text "Join a room" || fail "no Join a room button"
  sleep 1.5; d=$(dump)
  # Text boxes top to bottom: name, code. The keyboard moves things, so close it and look again before each box.
  boxes() { dump | grep 'class="android.widget.EditText"' | grep -o 'bounds="[^"]*"' | tr -c '0-9
' ' ' | awk '{print int(($1+$3)/2), int(($2+$4)/2)}' | sort -n -k2,2; }
  [ "$(boxes | grep -c .)" -ge 2 ] || fail "the join screen has no name and code boxes"
  type_into $(boxes | sed -n 1p) "Duo"; "$ADB" shell input keyevent KEYCODE_BACK; sleep 1
  type_into $(boxes | sed -n 2p) "$code"; "$ADB" shell input keyevent KEYCODE_BACK; sleep 1
  dump | grep -q "text=\"$code\"" || fail "the code didn't go into the code box"
  tap_text "Join" || fail "no Join button"
  for _ in $(seq 1 30); do sleep 1; tap_text "Not ready" && break; done || fail "joined $code but never saw my Not ready toggle"
  shot lobby
else
  # Seats: you plus bots; empty the last seats down to the player count. Seat columns are left to right
  # (Seat 2, 3, 4), each with its own Friend / Bot / Empty buttons.
  if [ "$PLAYERS" -lt 4 ]; then
    empties=$(all_text Empty "$d")
    for n in $(seq 4 -1 $((PLAYERS + 1))); do xy=$(echo "$empties" | sed -n "$((n - 1))p"); [ -n "$xy" ] && tap $xy && sleep 0.6; done
  fi
  # Hosting for a friend: seat 2 becomes a Friend seat.
  [ "$MODE" = host ] && { xy=$(all_text Friend "$(dump)" | head -1); [ -n "$xy" ] || fail "no Friend seat button"; tap $xy; sleep 0.6; }
  [ "$RULESET" = chivalry ] && { tap_text "Age of Chivalry" || fail "no ruleset button"; }
  if [ "$MODE" = online ] || [ "$MODE" = host ]; then
    xy=$(find_text "You") || true; [ -n "$xy" ] && { type_into $xy "Tester"; "$ADB" shell input keyevent KEYCODE_BACK; sleep 1; }
    tap_text "Host online room" || fail "no Host online room button"
    sleep 8
    if [ "$MODE" = host ]; then
      # Read the room code off the lobby and hand it to the friend; Start game once they're in and ready.
      code=$(dump | grep -o 'text="[^"]*"' | grep -o '[A-Z]\{3,\}-[1-9][0-9]' | head -1)
      [ -n "$code" ] || fail "no room code on the lobby screen"
      mkdir -p "$(dirname "$ROOM_FILE")"; echo "$code" > "$ROOM_FILE"; echo "USERPLAY $TAG: hosting $code"
      shot lobby
      for _ in $(seq 1 90); do
        "$ADB" logcat -d -s WGBG-TURN:I | grep -q 'phase=' && break
        d=$(dump); echo "$d" | grep -q 'text="Duo"' && tap_text "Start game" "$d"; sleep 3
      done
      rm -f "$ROOM_FILE"
    else
      tap_text "Start game" || fail "no Start game (lobby: $(dump | grep -o 'text="[^"]*ready[^"]*"' | head -2))"
    fi
  else
    tap_text "Play" || fail "no Play button"
  fi
fi
sleep 6
shot start
# The game must actually be on: the board asks for the first placement (the joiner waits for the host's Start).
for _ in $(seq 1 120); do "$ADB" logcat -d -s WGBG-TURN:I | grep -q 'phase=' && break; sleep 1; done
"$ADB" logcat -d -s WGBG-TURN:I | grep -q 'phase=' || fail "the game never started (still on the start screen?)"
# Local games: speed the bots up from the menu like a player can (Menu, tap the speed button to 8x, Hide).
if [ "$MODE" = local ]; then
  tap_text "Menu" && for _ in 1 2 3 4 5; do
    lbl=$(dump | grep -o 'text="\(Max\|[0-9]*×\)"' | head -1 | cut -d'"' -f2)
    [ "$lbl" = "8×" ] && break; [ -n "$lbl" ] && tap_text "$lbl"
  done; tap_text "Hide"
fi

start=$(date +%s); moves=0; last=""; prev=""; same=0; tries=0; changed=$(date +%s)
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
    # Stuck = the game log hasn't moved for 5 minutes (anyone's move moves it; a slow friend or bot doesn't count).
    [ "$line" = "$prev" ] || { changed=$(date +%s); prev="$line"; tries=0; }
    same=$((same + 1)); [ $(( $(date +%s) - changed )) -gt 300 ] && fail "stuck: ${line:0:200}"
    # A move of mine that didn't take: try the on-screen buttons again.
    if [ -n "$legal" ] && [ $((same % 6)) = 5 ]; then last=""; fi
    sleep 1; continue
  fi
  if [ "$line" = "$prev" ]; then tries=$((tries + 1)); else same=0; tries=0; changed=$(date +%s); fi; last="$line"; prev="$line"
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
    opts=$(echo "$d" | grep -o 'text="[^"]\+"' | cut -d'"' -f2 | grep -v -x -E 'Menu|Trade|Hide|Exit|Leave|Settings|End turn|Roll|Counter|Offer|You|Blue|Orange|White|Red|Tester|Duo|\(.*\)|Your turn|[0-9/ ]+|.*turn.*|.*Tap .*|.*built.*|.*rolled.*|.*\+[0-9].*|Raiders|Zoom|Tilt' | head -12)
    # After a few taps that changed nothing, a player ends the turn.
    if [ -n "$opts" ] && [ "$tries" -lt 3 ] && [ $((RANDOM % 3)) != 0 ]; then tap_text "$(echo "$opts" | shuf -n 1)" "$d" || true
    else tap_text "End turn" "$d" || { [ -n "$opts" ] && tap_text "$(echo "$opts" | shuf -n 1)" "$d"; } || true; fi
  fi
  moves=$((moves + 1))
  sleep 1.2
done
anr=$("$ADB" logcat -d | grep -c "ANR in $PKG" || true)
[ "$anr" = 0 ] || fail "ANR"
echo "USERPLAY $TAG PASSED"
