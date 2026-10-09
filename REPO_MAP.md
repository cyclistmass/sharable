# Repo map

Which folder on Mauro's PC (`C:\Users\mauro\`) goes to which GitHub repo. Everything for the game lives
under the **cyclistmass** GitHub account (Mauro DiBenedetto, maurodibenedetto@gmail.com). Commits use
that name and address. Update this table when a folder or repo is added, moved or retired.

| Local folder | GitHub repo / path | What it is | How it gets there |
|---|---|---|---|
| `sharable\` | [cyclistmass/sharable](https://github.com/cyclistmass/sharable) (root) | The published repo: README, this map, issues, CI (`.github/workflows/wgbg-ci.yml`) | `git push` from here |
| `catan-android\` (branch `main`) | cyclistmass/sharable → `working-german-board-game/` | The game: engine, AI, CLI, Android app, tools, docs, `firestore.rules` | commit in `catan-android`, then in `sharable`: `git subtree pull --prefix=working-german-board-game ../catan-android main` and push |
| `catan-android-pip\` (`pip/render-layers`) | same, via `catan-android` `main` | Pip's worktree (render performance) | merged into `main` by Bosun, then subtree pull |
| `catan-android-perfA\`, `catan-android-perfB\` (`codex/perf`) | same, via `main` | Codex render-speed worktrees | merge `codex/perf` into `main` when reviewed |
| `catan-android-tiles\` (`codex/tiles`), `catan-android-tiles3d\` (`codex/tiles3d`) | same, via `main` | Codex 3D-tile experiments (#32) | merge into `main` when approved |
| `catan-android-net\` | same, via `main` | Online-play (Firebase, #7) work; already merged | retire once nothing is left unmerged |
| `wgbg-art\` | **not in git yet** | Source art: Codex/ChatGPT batches, prompts, logs, palettes, inventory page | finished art is copied into `catan-android\app\src\main\res\drawable-nodpi\`; the sources need a home (see below) |
| `wgbg-games\` | **not in git** | Play-by-file test games (`play.cmd`, `playtest1`) | local scratch; the CLI itself is in the game repo (`cli/`) |

Never committed, on purpose:
- `catan-android\app\google-services.json`: the Firebase app config, gitignored; download it again from the Firebase console (project `workinggermangame`).
- API tokens: kept in Git Credential Manager only.

Open question: `wgbg-art\` is about 136 MB of source images. Options are a separate repo
(`cyclistmass/wgbg-art`, ideally with Git LFS) or `working-german-board-game/art-src/` with LFS. Not decided yet.

Other projects on this PC (`quailhollow-cookies-2026\`, `whisper\`, …) are not part of the game and are not in cyclistmass/sharable.
