# Working German Board Game: guide for AI coding agents

Steering for any AI agent (Claude Code, Codex, Copilot and others) working on this repo. Human
contributors are welcome to follow it too.

Android hex-trading board game with rules compatible with the classic German settlement-building
game, plus our knights expansion, **Age of Chivalry**. Kotlin + Jetpack Compose, GPL-3.0.

| Module | What it is |
|---|---|
| `engine/` | Pure Kotlin, deterministic rules engine (base game + Age of Chivalry). No Android. |
| `ai/` | Pure Kotlin bots (Random, Easy, Greedy). |
| `net/` | Pure Kotlin transport API: optimistic client, move codec, in-memory transport. |
| `app/` | Android UI (Compose) and the Firestore transport. |
| `cli/` | Play-by-file command-line client for testing. |
| `tools/` | Emulator, CI, GIF-recording and art-import scripts. |

## Names (copyright safety, required)

Never use the original game's names in UI, art, card text or docs. Our names:

- Resources: Timber, Clay, Fleece, Grain, Stone. Pieces: Homestead, Town, road. The robber is the **Bandit**.
- Development cards are **Fortune cards**: Sentinel, Landmark, Trailblazers, Windfall, Corner the Market.
- Expansion **Age of Chivalry**: commodities Parchment, Weave, Silver; progress decks Guilds, Academy, Crown;
  the **Raiders** (barbarians), Capital (metropolis), Ramparts (city walls).

Keep the "Not affiliated with or endorsed by Catan GmbH" line in the README. Code identifiers may use the
standard terms (`CitiesAndKnights.kt`, `Resource.ORE`); player-facing text may not.

## Look and feel (required on every panel)

Every panel, dialog, HUD box, menu and button uses the tabletop look: **dark wood panels with old-brass
edges and brass-edged buttons**. Use the shared pieces in `app/.../Tabletop.kt` (`brassEdgedWood()`,
`brassPlate()`, `BrassButton`, `GoButton`, `Choice`, `WoodButton`) and the Alegreya SC / Alegreya Sans
fonts. No plain Material grey buttons or black boxes. Action colours: yes/confirm green, no/cancel red,
counter/primary gold.

## Graphics workflow

0. Follow **`docs/design/tile-spec.md`** so parts fit: pointy-top hex tiles at 512 px with upper-left light,
   the number plate in the **lower right** (centre +0.36, +0.30, radius 0.27) on a plain clearing of radius
   0.31, and nothing tall in front of it, so tilting the board never hides the number.
1. New or changed art is shown as options side by side for the maintainer to pick from before it goes in.
2. Approved art goes into `app/src/main/res/drawable-nodpi/` and onto the in-app **Inventory** screen
   (Menu › Inventory, `drawInventory` in `MainActivity.kt`), so every object can be checked together.
3. Screenshot the Inventory screen and the board to confirm.
4. Art must be CC0 or our own. Record every source in `CREDITS.md`.

## Testing (required with every change)

Write the test plan before calling a change done, and cover all four kinds:

1. **Rules/unit tests:** each rule, card and edge case (empty bank, no pieces left, 2 players, ties).
2. **Random-play fuzz:** thousands of random legal games with invariants checked after every move
   (cards conserved, piece limits, points add up, games finish, no crashes).
3. **Break-it tests:** illegal, garbage and wrong-time actions; each must be rejected and leave the
   state unchanged.
4. **On-device:** install on an emulator, drive it, screenshot it; for UI work also run Android's
   `monkey` random-input tester and report any crash.

Also cross-check core rules against Catanatron, test dice fairness statistically, and report test counts
honestly. Never call something done with failing or skipped tests.

## Build and run

- JDK 17 and the Android SDK (`local.properties` with `sdk.dir`, not committed).
- `./gradlew :engine:test :ai:test :net:test :app:testDebugUnitTest :app:assembleDebug`
- Online play needs `app/google-services.json` from your own Firebase project (never committed), or the
  Firebase emulators: `firebase emulators:start --only firestore,auth --project demo-wgbg`, then launch with
  `--es firebase emu`. See `docs/online-play.md`.

### Driving the app (debug builds)

- Launch extras (`adb shell am start -n com.mauro.wgbg/.MainActivity ...`): `--es start base|chivalry`,
  `--ez watch true` (bots play), `--ez autoplay true`, `--ez autosetup true`, `--es firebase emu`,
  `--ef zoom <f>`, `--ef tilt <f>`. Add `--activity-clear-task`; `pm clear com.mauro.wgbg` drops saved rooms.
- Agent hooks: every turn logs `WGBG-TURN` lines (`turn= phase= player= prompt=".." legal=TYPE:PAYLOAD,...`);
  send a move with `adb shell am broadcast -a com.mauro.wgbg.MOVE -p com.mauro.wgbg --es move TYPE:PAYLOAD`.
  Each finished local game logs a `WGBG-GAME` line.
- Scripts: `tools/ci_monkey.sh`, `tools/ci_selfplay.sh`, `tools/ci_online.sh`, `tools/online_sim.sh`,
  `tools/record_gameplay.sh`; `tools/emulator.ps1` launches a landscape emulator on Windows.

## Issues, commits and CI

- Plans and bugs are GitHub issues on `cyclistmass/sharable` (labels: v1, post-v1, art, ui, engine, ai,
  multiplayer, testing, bug, age-of-chivalry). Reference the issue in commits ("#45"); close it only when
  the work is done and verified.
- CI (`.github/workflows/wgbg-ci.yml` in the sharable repo) runs every test, builds the APK, then runs
  monkey, bot self-play and a two-client online game on an emulator. A red CI is stop-and-fix.
- Every push that changes how the game looks re-records `docs/gameplay-1x.gif` (`tools/record_gameplay.sh`)
  in the same commit.
- Keep git tidy: no stray files, delete merged branches and worktrees, no machine-specific paths in build
  files or scripts.

## How to work

- **Never commit secrets** (tokens, keystores, `google-services.json`, service accounts). Humans log in to
  services themselves.
- **Don't wait blindly.** Estimate every long step (build, CI, emulator boot, install, network call). If it
  overruns, check whether it's stuck (process alive, log moving, device connected, network up, disk full,
  waiting on a prompt) and fix or report it.
- **When you find a bug, hunt its siblings** in code, scripts, CI and tests, and fix them in the same change.
- Prefer doing work directly over spawning many sub-agents.

Local, private notes for a particular machine or agent go in `.claude/` (git-ignored).
