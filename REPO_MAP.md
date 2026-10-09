# Repo map

Which folder on Mauro's PC (`C:\Users\mauro\`) goes to which GitHub repo. Everything for the game lives
under the **cyclistmass** GitHub account (Mauro DiBenedetto, maurodibenedetto@gmail.com). Commits use
that name and address. Update this table when a folder or repo is added, moved or retired.

| Local folder | GitHub repo / path | What it is | How it gets there |
|---|---|---|---|
| `sharable\` | [cyclistmass/sharable](https://github.com/cyclistmass/sharable) (root) | The published repo: README, this map, issues, CI (`.github/workflows/wgbg-ci.yml`) | `git push` from here |
| `catan-android\` (branch `main`) | cyclistmass/sharable → `working-german-board-game/` | The game: engine, AI, CLI, Android app, tools, docs, `firestore.rules` | commit in `catan-android`, then in `sharable`: `git subtree pull --prefix=working-german-board-game ../catan-android main` and push |
| `catan-android-pip\` (`pip/render-layers`) | same, via `catan-android` `main` | Pip's worktree (render performance) | merged into `main` by Bosun, then subtree pull |
| `catan-android-perfA\`, `catan-android-perfB\` (`codex/perf`) | same, via `main`; on GitHub as branch **`wgbg/codex-perf`** (a2b4a0a) | Codex render-speed pass (#33), review only | the branch is based on cf4f5ac (= catan-android fdb55cf), so rebase onto main before merging (it conflicts in RollingDice) |
| `catan-android-tiles\` (`codex/tiles`), `catan-android-tiles3d\` (`codex/tiles3d`) | same, via `main`; **still local only** | Codex 3D-tile experiments (#32); tiles3d is mid-work (V2 set, teeny goats on Fleece) | push as `wgbg/codex-tiles*` branches when Mauro OKs; merge when approved |
| `catan-android-net\` | same, via `main` | Online-play (Firebase, #7) work; already merged | retire once nothing is left unmerged |
| `wgbg-art\` | **not in git yet** | Source art: Codex/ChatGPT batches, prompts, logs, palettes, inventory page | finished art is copied into `catan-android\app\src\main\res\drawable-nodpi\`; the sources need a home (see below) |
| `wgbg-games\` | **not in git** | Play-by-file test games (`play.cmd`, `playtest1`) | local scratch; the CLI itself is in the game repo (`cli/`) |

Never committed, on purpose:
- `catan-android\app\google-services.json`: the Firebase app config, gitignored; download it again from the Firebase console (project `workinggermangame`).
- API tokens: kept in Git Credential Manager only.

Open question: `wgbg-art\` is about 136 MB of source images. Options are a separate repo
(`cyclistmass/wgbg-art`, ideally with Git LFS) or `working-german-board-game/art-src/` with LFS. Not decided yet.

## claude.ai pages (not git)

Design canvases and pages for the game belong in **Mauro's** claude.ai account (mauro.dibenedetto@gmail.com).
Check which account Claude Code is logged in as before publishing; the PC also logs in as Erin
(quailhollowcookies.com), and anything published then lands in her gallery.

| Page | Account | Note |
|---|---|---|
| Tiles canvas https://claude.ai/artifact/4egJhXbNhRjjzZSv5zXhja | Mauro | palette for tile art |
| Cards and names canvas https://claude.ai/artifact/JQGQiUm4tAjHQcR9ZLstn9 | Mauro | palette for cards and the names table |
| Art inventory https://claude.ai/artifact/EPCzewYHf6xvRnF8sQQ8pY | Mauro | every image and its status; source `wgbg-art\inventory\` |
| Hexhold (early web prototype, 2026-10-01) https://claude.ai/artifact/CvWUT69SZnGeKhZ4bcYcUt | **Erin (wrong account)** | copy saved at `wgbg-art\claude-artifacts\hexhold-2026-10-01.html`; republish under Mauro's account or share it to him |
| Game Start & Lobby https://claude.ai/artifact/QpUuSrw6XhxKvrfYWwrnps | **Erin (wrong account)** | empty canvas, created by mistake; safe to delete |
| Lobby/board layout canvas https://claude.ai/artifact/3325unbrfXKARLrn8MbcAC | unknown, unreadable since a login switch | designs were copied to `working-german-board-game/docs/design/` |

Other projects on this PC (`quailhollow-cookies-2026\`, `whisper\`, …) are not part of the game and are not in cyclistmass/sharable.
