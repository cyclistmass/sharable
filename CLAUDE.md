# Working German Board Game: dev steering

Android hex-trading board game (Catan-compatible rules), Kotlin + Compose. Modules: `engine/`
(pure Kotlin rules), `ai/` (bots), `app/` (UI). GPL-3. See README.md.

## Graphics workflow (required)

Every new or changed graphic goes onto a **palette for evaluation** before it is final:
1. Draft options on a Design canvas artboard (claude.ai Design artifact) so the user can compare
   variations side by side. Existing canvases: tiles https://claude.ai/artifact/4egJhXbNhRjjzZSv5zXhja,
   cards and names https://claude.ai/artifact/JQGQiUm4tAjHQcR9ZLstn9, lobby/game start/board
   layout https://claude.ai/artifact/3325unbrfXKARLrn8MbcAC. Add new ones as needed.
2. Once approved, port it into the app and add it to the in-app **Inventory** screen
   (`drawInventory` in MainActivity.kt), so every object can be eyeballed together on device.
3. Screenshot the Inventory screen and the board to confirm.

Art assets must be CC0 or our own; record sources in CREDITS.md.

## Names (copyright safety)

Never use Catan names in UI, art or card text. Use our names: Timber, Clay, Fleece, Grain, Stone;
Homestead/Town; Bandit; Fortune cards (Sentinel, Landmark, Trailblazers, Windfall, Corner the
Market); expansion "Age of Chivalry" (Parchment, Weave, Silver; Guilds, Academy, Crown decks;
Raiders, Capital, Ramparts). The full table is on the cards canvas, "Names" artboard.

## Emulator

Use the `emulator` skill (`tools/emulator.ps1 -Launch`). It keeps the emulator landscape and on screen.

## Testing (required: plan it with every feature)

Every feature or change comes with a test plan that fully exercises it, written down before
the work is called done. Cover all four kinds:

1. **Rules/unit tests:** each rule, card and edge case (empty bank, no pieces left, 2 players, ties).
2. **Random-play fuzz:** thousands of games of random legal moves, with invariants checked after
   every move (cards conserved, piece limits, points add up, games finish, no crashes).
3. **Break-it tests:** throw illegal, garbage and wrong-time actions at the engine (and later the
   network layer). Every one must be rejected and leave the state unchanged.
4. **On-device:** install on the emulator (`emulator` skill), drive it, screenshot it; for UI
   work also run Android's `monkey` random-input tester and report any crash.

Also:
- Cross-check rules against Catanatron when touching core rules.
- Dice and randomness: statistical fairness tests.
- Report test counts and results honestly; never call something done with failing or skipped tests.

## Plans, issues and CI

- Plans live as GitHub issues on `cyclistmass/sharable` (labels: v1, post-v1, art, ui, engine,
  ai, multiplayer, testing, bug, age-of-chivalry). New plans become issues; reference the issue
  number in commits ("#3") and close issues when done and verified.
- CI: `.github/workflows/wgbg-ci.yml` in the sharable repo runs all engine/AI/app tests and
  builds the APK on every push touching the game. Check the run after pushing; a red CI is a
  stop-and-fix.
- API access: the fine-grained token in Git Credential Manager (sharable repo only: contents,
  issues, pull requests, workflows read/write; actions read). Read it via `git credential fill`
  into an env var for one command; never print or save it.

## Rules for Claude

- Keep secrets out of git; the user logs in to services themselves.
- Go easy on subagents: do work directly or extend a running agent.
- Build: `export JAVA_HOME="$(ls -d "/c/Program Files/Eclipse Adoptium"/jdk-17*)"; ./gradlew :engine:test :app:assembleDebug`.
- Publish: commit here, then `git subtree pull --prefix=working-german-board-game ../catan-android main` in `C:\Users\mauro\sharable` and push.
- **Every push that changes how the game looks re-records the gameplay GIF**: install the build,
  run `tools/emulator.ps1`, then `tools/record_gameplay.sh`, check a frame, and commit the new
  `docs/gameplay-1x.gif` with the push. It shows on the project README and on the sharable
  front page (`working-german-board-game/docs/gameplay-1x.gif`). Mention the refreshed GIF in the push summary.
