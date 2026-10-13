#!/usr/bin/env bash
# Drive seat 0 by hooks (rolls, roads, up to 3 bank trades a turn) until a corner offers both a homestead and a knight;
# prints the spot. Resends a move that did not take after 6 s (a hook can be dropped mid-animation).
# Starts a new game when one ends (WGBG-GAME over) and builds towns too, for production.
# Usage: tools/seek_spot.sh [secs]   (ADB=emulator serial, default emulator-5554)
export MSYS_NO_PATHCONV=1; A="adb -s ${ADB:-emulator-5554}"; end=$((SECONDS+${1:-480})); prev=""; same=0; trades=0
while [ $SECONDS -lt $end ]; do
  if $A logcat -d -s WGBG-GAME | grep -q over; then echo "game over, new game"; $A logcat -c
    $A shell am start --activity-clear-task -n com.mauro.wgbg/.MainActivity --es start chivalry --ei players 3 --ez autosetup true >/dev/null; sleep 5; prev=""; continue; fi
  line=$($A logcat -d -s WGBG-TURN | grep "player=0" | tail -1)
  if [ "$line" = "$prev" ]; then same=$((same+1)); [ $same -lt 6 ] && { sleep 1; continue; }; fi
  same=0
  legal=$(echo "$line" | sed 's/.*legal=//; s/ spots=.*//' | tr ',' '\n')
  both=$(comm -12 <(echo "$legal" | sed -n 's/^BUILD_SETTLEMENT://p' | sort) <(echo "$legal" | sed -n 's/^BUILD_KNIGHT://p' | sort) | head -1)
  if [ -n "$both" ]; then echo "FOUND vertex $both"; echo "$line" | grep -o "spots=.*" | tr ' ,' '\n\n' | grep -E "(SETTLEMENT|KNIGHT):$both@" ; exit 0; fi
  echo "$line" | grep -q "ROLL:" && trades=0
  pick=$(echo "$legal" | grep -m1 -E '^(ROLL|BUILD_CITY|BUILD_ROAD):')
  [ -z "$pick" ] && [ $trades -lt 3 ] && pick=$(echo "$legal" | grep '^MARITIME_TRADE:' | shuf -n1) && [ -n "$pick" ] && trades=$((trades+1))
  [ -z "$pick" ] && pick=$(echo "$legal" | grep -m1 '^END_TURN:')
  [ -z "$pick" ] && pick=$(echo "$legal" | head -1)
  [ -z "$pick" ] && { sleep 1; continue; }
  prev="$line"; $A shell am broadcast -a com.mauro.wgbg.MOVE -p com.mauro.wgbg --es move "$pick" >/dev/null; sleep 1
done; echo TIMEOUT; exit 1
