# Working German Board Game: dev steering

Android hex-trading board game (Catan-compatible rules), Kotlin + Compose. Modules: `engine/`
(pure Kotlin rules), `ai/` (bots), `app/` (UI). GPL-3. See README.md.

## Graphics workflow (required)

Every new or changed graphic goes onto a **palette for evaluation** before it is final:
1. Draft options on a Design canvas artboard (claude.ai Design artifact) so the user can compare
   variations side by side. Existing canvases: tiles https://claude.ai/artifact/4egJhXbNhRjjzZSv5zXhja,
   cards and names https://claude.ai/artifact/JQGQiUm4tAjHQcR9ZLstn9. Add new ones as needed.
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

## Rules for Claude

- Keep secrets out of git; the user logs in to services themselves.
- Go easy on subagents: do work directly or extend a running agent.
- Build: `export JAVA_HOME="$(ls -d "/c/Program Files/Eclipse Adoptium"/jdk-17*)"; ./gradlew :engine:test :app:assembleDebug`.
- Publish: commit here, then `git subtree pull --prefix=working-german-board-game ../catan-android main` in `C:\Users\mauro\sharable` and push.
