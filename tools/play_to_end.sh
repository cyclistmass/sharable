#!/usr/bin/env bash
# Drive seat 0 by hooks (build first, then bank trades, then roll / end turn) until the game ends, to reach the
# end-of-game screen. Automated checks only: never counts as user testing.
# Usage: tools/play_to_end.sh [base|chivalry] [players] [secs]   (ADB=emulator serial, default emulator-5554)
export MSYS_NO_PATHCONV=1; A="adb -s ${ADB:-emulator-5554}"; end=$((SECONDS+${3:-900})); prev=""; same=0; trades=0
$A logcat -c
$A shell am start --activity-clear-task -n com.mauro.wgbg/.MainActivity --es start "${1:-base}" --ei players "${2:-3}" --ez autosetup true --ez quickbots true >/dev/null; sleep 5
while [ $SECONDS -lt $end ]; do
  over=$($A logcat -d -s WGBG-GAME | grep -m1 over) && { echo "$over"; exit 0; }
  line=$($A logcat -d -s WGBG-TURN | grep "player=0" | tail -1)
  if [ "$line" = "$prev" ]; then same=$((same+1)); [ $same -lt 6 ] && { sleep 1; continue; }; fi
  same=0
  legal=$(echo "$line" | sed 's/.*legal=//; s/ spots=.*//' | tr ',' '\n')
  echo "$line" | grep -q "ROLL:" && trades=0
  pick=$(echo "$legal" | grep -m1 -E '^(ROLL|BUILD_CITY|BUILD_SETTLEMENT|BUY_DEVELOPMENT_CARD|BUILD_ROAD):')
  [ -z "$pick" ] && [ $trades -lt 3 ] && pick=$(echo "$legal" | grep '^MARITIME_TRADE:' | shuf -n1) && [ -n "$pick" ] && trades=$((trades+1))
  [ -z "$pick" ] && pick=$(echo "$legal" | grep -m1 '^END_TURN:')
  [ -z "$pick" ] && pick=$(echo "$legal" | grep -v '^$' | head -1)
  [ -z "$pick" ] && { sleep 1; continue; }
  prev="$line"; $A shell am broadcast -a com.mauro.wgbg.MOVE -p com.mauro.wgbg --es move "$pick" >/dev/null; sleep 0.5
done; echo TIMEOUT; exit 1
