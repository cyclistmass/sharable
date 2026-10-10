# Issue #33: render pass on main 3e162af

Both APKs use the identical `perf` build type: release inheritance, non-debuggable,
R8 with the optimized default rules, resource shrinking, ProfileInstaller 1.4.1,
package `com.mauro.wgbg`, and the same local debug keystore. Firebase configuration
hashes match. Baseline tracked source/configuration files were compared with HEAD;
only `app/build.gradle.kts` is intentionally changed there.

## Kept

- TerrainArt: cached stroke styles, indexed drawing, reusable deep-zoom hex clip,
  bounded port geometry cache, primitive two-size bitmap cache and scale ladder.
  Today's implementation matches the old source apart from these optimizations.
- Seabird: primitive two-wing loop in the vector fallback.
- WoodPieces proposal: decode grain once for all stains; reuse polygon Path and
  edge Stroke. Existing render layers mean this primarily helps board recording.
- Tabletop proposal: sampled texture decode, release private intermediate, cached wash.
- TradePanel proposal: size-aware cached chest brushes/rim and primitive corner loops.
  The chest implementation survived the surrounding panel rewrite.
- RenderCache policies and four JVM tests; size-aware RenderAssets decode helpers.

## Adapted and new

- HUD proposal: retain today's centred pads and halo geometry; skip hidden normal
  and event faces, cache outline Stroke, and sample visible faces at density-aware size.
- RollingDice: retain themed faces, all ten tumble frames, event die, landing slots,
  animation and handover. Defer decoding until a drawable roll, remember sampled
  frame lists, and skip event art when absent. Decode sizes cover slot/fallback size,
  airborne lift and transparent margins. Pass primitive centre/width instead of
  allocating translated/fallback Rects during drawing.
- DiceArt: select face/frame lists directly instead of constructing a Pair per lookup.
- Bandit: remember a sampled sprite list sized to the longest screen dimension,
  retain all tilt steps and existing cross-fade; compute interpolation using primitives
  in the draw callback instead of a boxed Triple. The compatibility helper remains.
  Sampling may affect extreme zoom sharpness; compare on device.
- R8/ProfileInstaller proposal: shared optimized, debug-signed `perf` variant in both
  trees rather than comparing an optimized candidate against an unoptimized baseline.
- A/B tool: explicit ANDROID_SERIAL; three frame runs per side in A,B,A,B,A,B order;
  five force-stop/start TotalTime measurements per side, alternating A/B. Preserve
  preferences and restore A on exit. Save raw framestats and launch outputs.
  Framestats p95/p99 use nearest ranks of FrameCompleted minus IntendedVsync in ms,
  excluding flagged/incomplete rows; missing durations remain NA. These are process-
  cold launches, not cold storage/boot measurements. Existing ProfileInstaller/ART
  state and device activity can influence results.

## Dropped

- Old RollingDice sheet/Pair trajectory patch: that code was replaced on main.
- Old HUD pad coordinates: incompatible with today's centred slots/halo.
- Unused async asset-loader integration point: no consumer in this port; avoid adding
  asynchronous placeholder/handover behavior to the dice or board.
- Cliff palette/downsize candidates and asset-generation/patch-generation tooling:
  optional art changes pending visual review, outside this runtime optimization pass.
- Speculative sea/cloud/layout work from NOTES-for-Pip: existing render layers remain
  intact; no measured basis for changing their recording cadence or rendering.

## Validation and artifacts

Requested tests: engine 114, ai 13, net 20, app 120; total 267, zero failures/errors/skips.
Both final assemblies succeeded. Both APK signatures verify with certificate SHA-256
`90235a5b47da6ee29b077ea7c4ac94d2907c2126267adb5a999afcc4b5fb0093`.
Package/version match, neither manifest is debuggable, and both contain dependency-
provided `assets/dexopt/baseline.prof` and `.profm`. No app-specific profile was recorded.
APK SHA-256: A `c75ddf94e57a89ae846be1d57ca9e038e96dfba50034825a23c026a90b61a87e`,
B `b50d92d392bba81f11a6d403029299ffe70200e109e6b43c17bf7bd919160b95`.
Build logs are under perf2 `build/reports/perf/` and base `build/reports/perf-build.log`.
Shell syntax and two framestats fixtures (percentiles/filtering, missing data) pass.
No adb command ran; device visual parity and frame/startup gains remain unmeasured.

A: `C:/Users/mauro/catan-android-perf2-base/app/build/outputs/apk/perf/app-perf.apk`

B: `C:/Users/mauro/catan-android-perf2/app/build/outputs/apk/perf/app-perf.apk`

From Git Bash in perf2, substitute your device serial:

```bash
ANDROID_SERIAL=emulator-5556 bash tools/perf_ab.sh \
  /c/Users/mauro/catan-android-perf2-base/app/build/outputs/apk/perf/app-perf.apk \
  /c/Users/mauro/catan-android-perf2/app/build/outputs/apk/perf/app-perf.apk
```

Host build uses Java 17, GRADLE_USER_HOME=C:/Users/mauro/.gradle and
ANDROID_USER_HOME=C:/Users/mauro/.android. Kotlin compilation uses
`-Pkotlin.compiler.execution.strategy=in-process --max-workers=2` to avoid the
sandbox-inaccessible Kotlin daemon marker directory. Run `:app:assemblePerf` in both
trees and `:engine:test :ai:test :net:test :app:testDebugUnitTest` in perf2.
