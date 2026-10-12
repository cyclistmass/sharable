# Tests: what we have and what is missing

Every test and test script in the project, what it checks, whether CI runs it, and the gaps we know about
(Mauro, 2026-10-12: "an inventory of the test scripts in case there's gaps"). Counts are tests run, from the
JUnit results on 0.2.8. Keep this page current when a test or script is added, moved or retired.

## Unit tests: 396, all run by CI

`./gradlew :engine:test :ai:test :net:test :app:testDebugUnitTest`

| Module | Tests | What they cover |
|---|---|---|
| engine | 118 | Rules of both rulesets: setup, turns, building, trading, counter-offers, Fortune cards, longest road, Age of Chivalry (29: commodities, Ramparts, Raiders, progress cards, Capitals, 13 points), hidden information, determinism and replay. **Fuzz:** 2,000 random base games and 1,000 Age of Chivalry games with invariants after every move. **Break-it:** junk and wrong-time actions in both rulesets. **Dice:** fairness and chi-square tests (a loaded die is caught), deck dice. **Catanatron:** id mapping to Catanatron's board |
| ai | 14 | Bots finish games in both rulesets and trade sensibly without creating or losing cards |
| net | 24 | Optimistic client, rollback, resume, reconnect, move codec, a hostile log, 300 delayed random online games that must agree |
| app | 240 | Start-screen setup logic, lobby and rejoin, invite links, local save (#82), Report contents, Google sign-in logic (#83), settings and Pace, trade panel, build menu, every legal move reachable by a tap, 3D board math against 200 Catanatron reference positions, piece lift, Turn bar mapping, sea, birds, dice art and timing, HUD and hints |

## Device tests (emulator), run by CI

| Test or script | What it does | Fails on |
|---|---|---|
| `tools/ci_monkey.sh` (twice: plain, and in an Age of Chivalry game) | Android `monkey` random taps | crash, ANR, monkey abort |
| `tools/ci_selfplay.sh 60 3` | bots play back-to-back games at Max speed in both rulesets | crash, ANR, fewer than 3 games finished per ruleset |
| `tools/ci_touchplay.sh 100 10` | a human seat played with taps in base and Age of Chivalry, 3 and 4 players | crash, ANR, stuck, fewer than 10 moves |
| `tools/ci_online.sh`: `OnlineTwoClientTest` | two clients host and join a room on the Firebase emulators; bots play a whole game | the two end states differ |
| `tools/ci_online.sh`: `LiveBoardDeviceTest` | real touch on the 3D board: twist, pinch, tilt, pan, place a homestead and a road | gesture or placement not taken |
| `firebase/rules-test` | Firestore security rules and push delivery | a rule lets the wrong player write |

## Scripts not run by CI

| Script | Use |
|---|---|
| `tools/userplay.sh` | The only full game from the start screen with real taps (local, host, join). Run by hand before a release |
| `tools/online_sim.sh` | Two emulators play one online game (autoplay) and compare end states |
| `tools/test_perf_framestats.sh` | Checks the frame-stats parser |
| `tools/record_showcase.sh`, `tools/record_screenshots.sh` | README media; they assert nothing, but they break when buttons move, so they double as a UI smoke check |
| `tools/perf_ab.sh`, `tools/publish_release.sh` | Performance A/B, release (refuses unless CI is green) |

## Gaps

Known places where nothing, or only logic, is tested:

1. **No CI device step starts at the start screen or finishes a game by tapping.** Touch-play stops after 100 s
   and uses the move hook for discards, card picks and steals; self-play and monkey launch with debug extras.
   `userplay.sh` does the real thing but only by hand.
2. **Screens with no UI test:** start screen, Look & art, the gear panel (Pause, speed, Watch bots, Pace), the
   Report panel, the account row, the intro.
3. **Resume after closing the app (#82):** saving and loading are tested; closing and reopening on a device is not.
4. **Google sign-in:** the link/switch/sign-out logic is tested with a fake; the real account picker only by hand.
5. **3D board:** math and gestures are tested; how it looks is not (no screenshot comparison).
6. **Online edge cases:** reconnect and quit-to-bot are tested as logic; deleting a room, a player leaving
   mid-game and two phones on real Firebase are not in CI.
7. **Trades by tapping:** the trade panel's logic is tested; touch-play leaves out trade offers.
8. **Pirate theme, sounds, music and haptics:** art tables only; nothing checks them on a device.
9. **5 and 6 players:** the engine allows 2 to 4 (base) and 3 to 4 (Age of Chivalry); only the piece-supply layout
   is tested for more.
10. **Scripts match screen text literally.** A renamed or moved button breaks them (it happened twice on
    2026-10-12). uiautomator writes `&` as `&amp;`, so a label with `&` must be written that way.
