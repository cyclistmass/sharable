# Render-speed pass

## Direct changes

- `TerrainArt.kt`: cached Item stroke styles, indexed item drawing, reusable deep-zoom
  hex clip, bounded port-clip geometry cache, primitive-key two-size bitmap cache and
  repeated-scale quantization cache. Art seeds, geometry, clipping coordinates and
  bitmap draw rounding are unchanged. Allocation remains on cache misses, first-use
  art construction and camera/size changes; Android/framework drawing was not profiled.
- `Seabird.kt`: primitive two-wing loop instead of a new boxed list/iterator in the
  vector fallback. Sprite drawing is already cheap; this is a small fallback improvement.
- New `RenderCache.kt`: pure cache, scale and decode-sampling policies.
- New `RenderAssets.kt`: size-aware decode, width-sized texture decode that releases
  its private intermediate, and `rememberRenderBitmap` for asynchronous IO decode.
  The async loader is an integration point for Pip, not yet used by BoardScreen.
- New `RenderCacheTest.kt`: four JVM tests, including randomized zoom/sample policies,
  size eviction behavior and a warmed allocation/timing check.
- `app/build.gradle.kts`: release R8 optimization/resource shrinking. The conditional
  google-services application remains intact. `gradle/libs.versions.toml`: declare
  ProfileInstaller 1.4.1 (released October 2, 2024), added to app dependencies.
  Release contains `assets/dexopt/baseline.prof` and `.profm` merged from dependencies.
  No app-specific baseline profile was recorded without a device.
- New tools: `perf_ab.sh`, `perf_gfxinfo.awk`, `test_perf_parser.sh`, `perf_assets.mjs`,
  and `prepare_perf_patches.cjs`. The last tool regenerates proposals without writing
  owner-held source files and fails if expected source fragments have changed.

R8 setup follows [Android optimization guidance](https://developer.android.com/topic/performance/app-optimization/enable-app-optimization).
Profile installation follows [ProfileInstaller](https://developer.android.com/jetpack/androidx/releases/profileinstaller)
and [baseline profile guidance](https://developer.android.com/topic/performance/baselineprofiles/overview).

## Owner proposals, ranked by expected usefulness

These are unapplied to the shared repository. Every patch has a slow-part/why/win/risk
header and passes `git apply --check --ignore-space-change`. All five applied together
in an isolated scratch copy also compile successfully with `:app:assembleDebug`.

1. `WoodPieces.kt.patch`: one source wood decode for all stains rather than one per
   color; reuse polygon Path and edge Stroke. Brushes/shaders are already cached.
   Expected startup/board-recording win; no sea-frame drawing win from the cached still
   layer. Scratch geometry assumes synchronous, non-reentrant drawing.
2. `Tabletop.kt.patch`: sampled texture decode instead of full-size intermediate,
   and cached table wash gradient. Density/size determine sampling; inspect filtering
   parity. Brass-panel gradients could follow the same pattern in the owner's lane.
3. `TradePanel.kt.patch`: cache chest gradients/rim and replace corner lists with
   primitive loops. Expected fewer objects per panel redraw; exact geometry preserved.
4. `RollingDice.kt.patch`: decode sheets only when a throw can be drawn; eliminate
   Pair/boxed return values on the non-slot trajectory. Slot throws already avoid that
   pair. First-throw async prewarm is still an owner task.
5. `Hud.kt.patch`: skip hidden die face decoding and retain its outline Stroke.
   Small startup/redraw improvement; visible new faces still decode synchronously.

`NOTES-for-Pip.md` separately ranks cloud caching, shared Layout geometry, raster
water experiments, and lazy asset hookup. No MainActivity/Sea patches were made.
Water BASE is already recorded at roughly 6 Hz; graphics-layer command caching does
not by itself prove lower GPU raster work. Sea Path and ocean shader caches already
exist. No seaTick leak was found in HUD/trade composition: preserve the move tick.
CloudFeel was left unchanged; its Pair-returning cap is used for gestures, not each
cloud draw. The actual cloud gradients live in Pip's Sea.kt.

## Measurements

- Forced JVM rerun: warmed cache+ladder pair **4.2831 ns**, **0 allocated bytes** for
  1,000,000 pairs after 500,000 warmup pairs on this host. Initial run: 4.0954 ns.
  These are JVM helper measurements, not Android frame-time or GPU measurements.
- Four cliff candidates under `perf-patches/assets/`, originals untouched:

| Asset | Original dimensions / file bytes | Candidate dimensions / file bytes |
|---|---:|---:|
| cliff_top_1 | 768×174 / 83,418 | 384×87 / 52,934 |
| cliff_top_2 | 768×174 / 72,170 | 384×87 / 46,652 |
| cliff_top_3 | 768×174 / 86,254 | 384×87 / 56,838 |
| cliff_straight | 1024×225 / 148,654 | 512×113 / 109,878 |

Combined files: **390,496 → 266,302 bytes** (31.8% smaller). Nominal RGBA decode
memory: **2,525,184 → 632,320 bytes** (75.0% smaller; actual stride/GPU overhead can
differ). Each lossless candidate has zero visible premultiplied-RGBA pixel error
against the identical Lanczos reference resample, including alpha. All four palettes
were generated; `cliff_top_1_palette.png` was visually inspected. Left is reference,
right is candidate. No Android visual parity or deep-zoom sharpness claim is made.
The shipped source cliffs are already WebP, not the older 2.9 MB PNG in the survey.
Tiles are already about 443×512. Candidates remain outside app resources pending
palette/device review; provenance is the existing original art, with no new artwork.

- No-config final APKs: debug **22,926,978 bytes**, unsigned R8 release **6,477,073
  bytes**. These are different build variants, not a measured before/after R8 comparison.
- Offline gfxinfo parser: **2 fixtures passed**, including missing metrics (NA) and
  ignoring legacy jank percentage. A/B script syntax passed. No adb/device command ran.

## Verification

An isolated source copy at `C:/Users/mauro/.cache/wgbg-render-check` avoids other lanes'
build outputs and excludes `app/google-services.json`. Its only extra build setting
points debug signing at the existing local debug key; no key contents were read.
Java 17 / Gradle command, all tasks forced to execute:

```text
./gradlew :engine:test :ai:test :net:test :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease -Pkotlin.compiler.execution.strategy=in-process --max-workers=2 --console=plain --rerun-tasks
```

| Module | Tests | Failures / errors / skipped |
|---|---:|---:|
| engine | 114 | 0 / 0 / 0 |
| ai | 13 | 0 / 0 / 0 |
| net | 19 | 0 / 0 / 0 |
| app | 89 | 0 / 0 / 0 |
| Total | **235** | **0 / 0 / 0** |

Both assemblies and release vital lint passed. Final forced run: BUILD SUCCESSFUL
in 1m 58s, 103 tasks executed. Existing Kotlin/Gradle warnings remain. Proposal-only
scratch debug assembly passed in 20s. Firestore rules were not changed or rerun in
this render task. No commit, push, deploy, emulator, install or adb use occurred.

## Still needs device A/B

Run `bash tools/perf_ab.sh <apkA> <apkB>` on an idle device with identically signed
APKs. It alternates A/B/A/B and collects the requested total frames, janky percentage,
p50/p90 and Slow UI thread counts after 10 s warmup / 15 s collection. Raw `am start
-W` launch timings are retained too; that is not a controlled cold-boot benchmark.
Confirm inventory/board/zoom/texture/throw parity, generate an app-specific baseline
profile, and measure startup and sustained frame behavior after owner integration.
No FPS, jank, cold-start reduction, or water/cloud GPU win has been measured here.
