# Test speed and coverage: ideas

Reviewed after every build and test run (see AGENTS.md, "How to work"). Anyone may add a line or pick one up;
move it to **Done** with the commit when it lands.

## Open

- **CI device tests in parallel shards + cached emulator snapshot**: one job ran monkey, self-play, touch play
  and online back to back (14 of CI's ~17 min); four shards on a cached snapshot should take ~6-7 min. Drafted on
  sharable `wgbg/ci-parallel`. (Bosun, 2026-10-13)
- **ci_trade.sh in CI**: trade-chest taps are only checked locally today. In the same draft. (Bosun, 2026-10-13)
- **A game to the end screen in CI** (`tools/play_to_end.sh chivalry 4`): nothing in CI reached the end of a game
  through the UI. In the same draft. (Bosun, 2026-10-13)
- **Hook-driven scripts react to log lines instead of polling** (`seek_spot.sh`, `ci_trade.sh` setup turns):
  polling once a second with 6 s resends made a 76-turn game take 9 min. `play_to_end.sh` shows the pattern
  (`logcat` stream + `read -t` + stall watchdog). (Bosun, 2026-10-13)
- **Stall watchdog in every device script**: exit with the reason (device, app alive, last turn line, screenshot)
  when nothing moves for N s, so a stuck run fails in seconds instead of at its time limit. (Bosun, 2026-10-13)
- **Card tester** (debug builds): every card in every mode set up on a board for a human to play and pass/fail,
  results sent as a report. Covers card effects the fuzz games rarely reach. (Mauro, 2026-10-13)
- **A long Chivalry game in the report-size test**: reports are capped at 190 000 characters; check a real
  300-turn replay fits whole. (Bosun, 2026-10-13)
- A uiautomator dump takes ~4.5 s in a game (the animated sea never lets it go idle), so scripts can't read the screen
  between two bot answers. A debug "pause the sea" extra or reading the tree from the app's own log would make every
  touch script several times faster. Bosun, 2026-10-13 (#139).
- emulator-5556 wedges after installs (am start -W never returns, blank screen, no focus); a reboot fixes it for a while.
  Every `am start -W` in tools/ now has a 60 s timeout so a script fails fast instead of hanging. Bosun, 2026-10-13 (#134?).

## Done

- Local `--ez autoplay true` (a bot plays your seat offline too): a full base game in ~2.5 min. 7254728.
- `tap()` helpers no longer let `adb shell` eat stdin inside `while read` loops (only the first tap happened).
  7254728, found by Crest.
