# Render pass A/B: blocked device measurement

Date: 2026-10-09. Target: Pixel6_API34, **emulator-5556 only**.
Baseline and candidate HEAD: `1806f7f1275df59558b3da9798af1ab3db219389`.
A is detached main; B is `codex/perf` plus the render changes.

## Implementation and builds

All five proposals (WoodPieces, Tabletop, TradePanel, RollingDice, Hud) passed
`git apply --check --ignore-space-change` and applied together to B with
`git apply --ignore-space-change`. No rebase was needed. Earlier direct changes
and helpers remain included. A's source files were not edited; its Git status
was clean after building.

Both debug APKs built successfully with Java 17.0.20.101-hotspot. This shell
required explicit `GRADLE_USER_HOME=C:/Users/mauro/.gradle`; otherwise the wrapper
tried to create `C:/.gradle`. JAVA_HOME was supplied through PowerShell before
`bash ./gradlew`, using the installed Adoptium JDK 17.

B command: `:app:assembleDebug :app:assembleRelease :engine:test :ai:test
:net:test :app:testDebugUnitTest --console=plain --max-workers=2`.
B finished **BUILD SUCCESSFUL in 3m 42s**, 103 tasks (68 executed, 35 cached).
`:app:minifyReleaseWithR8`, resource optimization, release vital lint, and
`:app:assembleRelease` succeeded. Release APK is unsigned; debug builds are
the prepared A/B variants. No release-signing configuration was changed.

| APK | Bytes | SHA-256 |
|---|---:|---|
| A debug | 22,906,818 | `50cbdcfca2a0d1c9178a97f299a190863b3a8f1049702d50ea5aa8f4ed94d69f` |
| B debug | 22,906,818 | `8243df8f175bf9e9368fe45fa73ef34ef143bac686793ff5357baef17b88b3d0` |
| B unsigned release | 6,456,913 | Not recorded |

The debug APKs have equal lengths but different hashes; build directories are
separate ordinary directories.

## Device blocker and missing results

Every attempted ADB invocation set `ANDROID_SERIAL=emulator-5556` and supplied
`-s emulator-5556`. ADB exits before communicating with the emulator:

```text
F adb.exe : adb_utils.cpp:315 Cannot mkdir '\.android': Permission denied
```

The same error occurs for `get-state`, `version`, the first A install in
`tools/perf_ab.sh`, and the final baseline reinstall attempt. Setting Android
home variables did not resolve it. Lane 2 readiness could not be determined;
no emulator launch/restart, server termination, or lane 1 command was issued.
No successful install occurred. A restoration was attempted but cannot be
verified. See `script-attempt.txt` and `restore-attempt.txt`.

| Measurement | A | B | Median / spread / conclusion |
|---|---|---|---|
| Total frames (2 runs each) | Not collected | Not collected | Unavailable |
| Janky frames / percentage | Not collected | Not collected | Unavailable |
| p50 / p90 / p95 frame time | Not collected | Not collected | Unavailable |
| Slow UI thread count | Not collected | Not collected | Unavailable |
| Cold process start TotalTime (3 each) | Not collected | Not collected | Unavailable |

The partial `raw/summary.csv` contains only a header, not measurements. No
performance difference or claim that a difference exceeds emulator GL noise
can be supported. The updated script alternates A,B,A,B with 10 s warmup and
15 s collection, then alternates three force-stopped process launches each.
These are cold **process** starts, not cold OS boots or cleared filesystem caches.
It requires ANDROID_SERIAL before doing any work, passes `-s` on every ADB call,
collects p95, and attempts to reinstall A on exit, including failure/interruption.
Offline checks passed: two parser fixtures, unset-serial rejection, shell syntax.

## Visual check and recommendations

No device screenshots or crops could be captured; `A_*.png`, `B_*.png`, and
`ab_compare.png` are unavailable. No on-device visual inspection was possible.
Missing art, color parity, seabirds, tile detail, and cliff sharpness remain
unverified; this is not a visual-parity pass.

The downsampled cliffs remain proposals under `perf-patches/assets/`; they were
not copied into app resources. Therefore the built candidate does not test
their sharpness. Keep the shipped cliffs pending the requested zoom comparison.

| Change | Measurable device win | Keep/drop recommendation |
|---|---|---|
| TerrainArt caches / Seabird loop | Unmeasured | Retain for evaluation; no measured-win claim |
| WoodPieces | Unmeasured | Retain for startup evaluation |
| Tabletop | Unmeasured | Require texture visual check before acceptance |
| TradePanel | Unmeasured | Require panel redraw evaluation |
| RollingDice | Unmeasured | Require first-throw and animation evaluation |
| Hud | Unmeasured | Require hidden/visible die check |
| R8 / ProfileInstaller | Not part of debug A/B | Release build validation only |
| Downsampled cliff candidates | Not deployed | Defer adoption until zoom inspection |

A combined A/B alone would establish the bundle's effect, not isolate individual
patch contributions. Per-patch ablation measurements are needed to name winners.

## Unit tests

| Module | Tests | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|
| engine | 114 | 0 | 0 | 0 |
| ai | 13 | 0 | 0 | 0 |
| net | 19 | 0 | 0 | 0 |
| app | 89 | 0 | 0 | 0 |
| Total | **235** | **0** | **0** | **0** |

Engine/AI/net results came from Gradle's build cache; app tests executed in this
run, including all four RenderCache tests. Existing compiler warnings remain.

## Commit blocker and resumption

`git add` failed with:

```text
fatal: Unable to create 'C:/Users/mauro/catan-android/.git/worktrees/catan-android-perfB/index.lock': Permission denied
```

The shared Git metadata is outside the writable roots. No commit or push was
made; neither main nor its branch reference was changed. The requested commit
trailer must be `Co-Authored-By: Codex <noreply@openai.com>` when committing from
an environment that can write this metadata.

Run from B in a shell with working ADB home-directory access:

```bash
export ANDROID_SERIAL=emulator-5556
export PERF_OUT=perf-patches/ab/raw
bash tools/perf_ab.sh ../catan-android-perfA/app/build/outputs/apk/debug/app-debug.apk app/build/outputs/apk/debug/app-debug.apk
```

Then capture board and zoom views for both APKs at matched states, inspect them,
create the ffmpeg hstack comparison, calculate medians and min-max ranges from
the raw CSVs, and replace the unavailable entries above. MainActivity currently
seeds the game with System.nanoTime(), so identical launch arguments alone do
not guarantee identical board layouts. Record that limitation or provide a
shared deterministic debug fixture for both builds before claiming matched-state
pixel parity. Finally reinstall A explicitly and verify success.
