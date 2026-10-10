# Repo map

Which folder on Mauro's PC (`C:\Users\mauro\`) goes to which GitHub repo. Everything for the game lives
under the **cyclistmass** GitHub account (Mauro DiBenedetto, maurodibenedetto@gmail.com). Commits use
that name and address. Update this table when a folder or repo is added, moved or retired.

| Local folder | GitHub repo / path | What it is | How it gets there |
|---|---|---|---|
| `sharable\` | [cyclistmass/sharable](https://github.com/cyclistmass/sharable) (root) | The published repo: README, this map, issues, CI (`.github/workflows/wgbg-ci.yml`) | `git push` from here |
| `catan-android\` (branch `main`) | cyclistmass/sharable → `working-german-board-game/` | The game: engine, AI, CLI, Android app, tools, docs, `firestore.rules` | commit in `catan-android`, then in `sharable`: `git subtree pull --prefix=working-german-board-game ../catan-android main` and push |
| `catan-android-perfB\` (`codex/perf`) | same, via `main`; on GitHub as branch **`wgbg/codex-perf`** (2184477, includes local 07d4c4b; local and pushed match) | Codex render-speed pass (#33) with A/B raw results, review only | based on cf4f5ac (= catan-android fdb55cf), so rebase onto main before merging (it conflicts in RollingDice) |
| `catan-android-perf2\` (`codex/perf2`), `catan-android-perf2-base\` (detached) | same, via `main`; **local only** | A second Codex performance pass, started at main 3e162af (owner: Crest/Codex) | push or merge when reviewed |
| `catan-android-tiles\` (`codex/tiles`), `catan-android-tiles3d\` (`codex/tiles3d`, b01ccbb) | same, via `main`; on GitHub as branch **`wgbg/codex-tiles3d`** (ee97678; includes codex/tiles, which is its ancestor) | Codex 3D-modelled tiles V2 (#32): 6 terrains, alpine Stone, alpacas + goats, ART_SPEC.md, bandit V1 with 8 tilt steps, Blender scripts; 368 files, ~69 MB of renders | review only; based on cf4f5ac, so rebase onto main before merging |
| `catan-android-net\` | none (stale folder, no longer a git worktree) | Old online-play (Firebase, #7) checkout; all of it is merged into `main` | safe to delete once checked |
| `wgbg-art\` | **not in git yet** | Source art: Codex/ChatGPT batches, prompts, logs, palettes, inventory page | finished art is copied into `catan-android\app\src\main\res\drawable-nodpi\`; the sources need a home (see below) |
| `wgbg-games\` | **not in git** | Play-by-file test games (`play.cmd`, `playtest1`) | local scratch; the CLI itself is in the game repo (`cli/`) |
| `wgbg-art\dice-themed\` | **not in git yet** | Codex dice art: antique and pirate, white / red / event dice, faces + tumbles; `orig\` and `orig2\` are backups (#31, #48) | finished dice are copied into `res\drawable-nodpi\`; sources wait on the wgbg-art repo decision |
| `wgbg-art\session-crest-2026-10-09\` | **not in git yet** | Crest's Codex briefs, red-option and dice sheets, playtest scripts | archive of that chat |
| `wgbg-art\_chat-2026-10-09_pip_5c3f1b3d\` | **not in git yet** | Pip's chat archive: every file generated in that chat, timestamped, with MANIFEST.md | archive of that chat |
| `wgbg-art\claude-artifacts\` | **not in git yet** | saved copies of the claude.ai pages (see below) | saved before each login switch |

Merged and removed on 2026-10-09 (Bosun): worktrees `catan-android-pip\` (`pip/render-layers`) and `catan-android-fixes\` (`codex/fixes`), and branches `pip/*`, `codex/fixes` and `worktree-agent-*`. Everything in them is in `main`.

Never committed, on purpose:
- `catan-android\app\google-services.json`: the Firebase app config, gitignored; download it again from the Firebase console (project `workinggermangame`).
- API tokens: kept in Git Credential Manager only.

Open question: `wgbg-art\` is about 136 MB of source images. Options are a separate repo
(`cyclistmass/wgbg-art`, ideally with Git LFS) or `working-german-board-game/art-src/` with LFS. Not decided yet.

## Who owns what (agents on this PC)

- **Bosun** (cloud-nudging-momentum): the app and `main`. Merges branches, tests on emulator lane 1, re-records the GIF and publishes to GitHub.
- **Crest** (mauro-7a): the Codex dice art lane and the 3D-tile art lane, plus four open art decisions and their follow-through: the red die A–E (#31/#48), the bandit B1–B5 (#32), whether the goats get bigger (#32), and the number plate vs scenery density (#32). Send Mauro's answers on these to Crest.
- **Pip** (mauro-0a): render performance, this repo map, the art inventory page, and the colour pass (`wgbg-art\desat-10\`, waiting on Mauro).

## claude.ai pages (not git)

Design canvases and pages for the game belong in **Mauro's** claude.ai account (mauro.dibenedetto@gmail.com).
Check which account Claude Code is logged in as before publishing; the PC also logs in as Erin
(quailhollowcookies.com), and anything published then lands in her gallery.

| Page | Account | Note |
|---|---|---|
| Tiles canvas https://claude.ai/artifact/4egJhXbNhRjjzZSv5zXhja | Mauro | palette for tile art |
| Cards and names canvas https://claude.ai/artifact/JQGQiUm4tAjHQcR9ZLstn9 | Mauro | palette for cards and the names table |
| Art inventory https://claude.ai/artifact/EPCzewYHf6xvRnF8sQQ8pY | Mauro | every image and its status; source `wgbg-art\inventory\`, saved copy `wgbg-art\claude-artifacts\art-inventory\` |
| Hexhold (early web prototype, 2026-10-01) https://claude.ai/artifact/CvWUT69SZnGeKhZ4bcYcUt | **Erin (wrong account)** | copy saved at `wgbg-art\claude-artifacts\hexhold\`; republish under Mauro's account or share it to him |
| Game Start & Lobby https://claude.ai/artifact/QpUuSrw6XhxKvrfYWwrnps | **Erin (wrong account)** | empty canvas, created by mistake; safe to delete (note in `wgbg-art\claude-artifacts\game-start-and-lobby\`) |
| Lobby/board layout canvas https://claude.ai/artifact/3325unbrfXKARLrn8MbcAC | unknown, unreadable since a login switch | designs were copied to `working-german-board-game/docs/design/` |

Saved copies of every claude.ai page (HTML plus files) live in `wgbg-art\claude-artifacts\<page>\`, so a login switch never loses them.

Other projects on this PC (`quailhollow-cookies-2026\`, `whisper\`, …) are not part of the game and are not in cyclistmass/sharable.
