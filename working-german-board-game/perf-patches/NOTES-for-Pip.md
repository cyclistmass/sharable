# Render findings for Pip (read-only review of MainActivity.kt and Sea.kt)

No edits or patches were made for these two files. Findings refer to the landed
BoardPart/GraphicsLayer implementation, not the older survey.

## Ranked owner work

1. **Cache clouds in local coordinates.** `Sea.drawSky` still creates a radial
   brush and a three-color list for every puff on every live frame (4–8 clouds,
   roughly ten puffs each). Record one transparent layer/bitmap per PUFFS shape
   including UNDER/TOPS. Draw with translation, uniform scale, and fade for
   drift, poke shrink, wobble, and poof. Keep the water shadow separately live.
   Expected win: dozens of gradient/shader setups become 4–8 image/layer draws
   per frame. Risk: raster softness at deep zoom and transparent margins; choose
   size buckets or use recorded vector layers, and verify shadows/poof parity.

2. **Share a camera layout across live passes.** `drawGame` calls `Layout.fit`
   before its WATER/UNDER/OVER early returns. `fit` calls `extent`, which maps
   vertex positions into boxed Float lists, then Layout maps them again. UNDER
   and OVER therefore repeat this work every sea frame. Cache immutable lattice
   coordinates/extents by board topology, then projection by board + camera +
   canvas size. Pass the same Layout into all parts. Also replace hexLattice's
   two `vs.map(...).average()` calls with primitive sums/precomputed centers.
   Expected win: remove O(vertices) allocation/work from otherwise unchanged
   live frames. Risk: correct invalidation for board/ports, camera/tilt and size;
   do not cache live game values in this geometry cache.

3. **Measure water raster cost after the existing throttle.** WATER is ALREADY
   recorded on `(sea.time * 6).toInt()` (roughly 6 Hz), not 60 Hz. BASE contains
   the radial gradients, three ocean passes, mottling and coast halos; UNDER
   draws LIVE water. BitmapShader/ShaderBrush triples are ALREADY cached.
   GraphicsLayer recording caches draw commands; replay can still execute their
   GPU raster work unless the renderer retains a raster surface. Measure before
   assuming a 10× pixel-work reduction. Candidate: a half-resolution bitmap or
   explicitly raster-cached water surface, refreshed at 6 Hz, composed at full
   size; separate and bake stationary gradient/coast shading per camera/size.
   Expected win: lower full-screen fill/bandwidth at the cost of a bitmap upload
   on refresh. Risk: soft shoreline, seam/resize artifacts, upload stalls and
   memory; compare full/half-resolution candidates on device. Do not increase
   refresh to 30 Hz without a measured visual/performance reason.

4. **Asset startup owner hookup.** BoardScreen currently loads both tile sets,
   both knight sets, cliffs, raiders, birds and boats at entry. Composable
   `imageResource` caches decodes: this is first-use cost, not per-frame decoding.
   First select the active tile/knight set, then lazily request expansion art and
   decorative sprites. New `rememberRenderBitmap(id, targetWidth, targetHeight)`
   returns `State<ImageBitmap?>`, decoded on Dispatchers.IO with power-of-two
   sampling and `inScaled=false`. Keep a stable size bucket; use the original
   for deep zoom. Read loaded values in the appropriate layer keys so a ready
   asset re-records the still layer. Null is the loading state; decide which
   existing fallback to show. Do not sample sprite strips without updating frame
   dimensions/coordinates. The helper is ready but this owner hookup is pending.

5. **Smaller live allocations after the above.** `drawBoatSprite` allocates
   `intArrayOf(-1, 1)` per boat draw; use a two-index loop. BASE coast halos build
   `listOf(Pair(...))` on re-record; use constants/primitive loops. Rings create
   Stroke objects; reuse per scale. `drawBreak`/`drawWavelet` ALREADY reuse
   `crescent`; coast paths ALREADY cache geometry. The fallback boat alone still
   creates hull/sail Paths. Do not redo those existing caches.

## Recomposition review

`seaTick` reads are inside the Canvas draw lambda, including target pulsing.
RollingDice's `t.value` is also inside its Canvas draw. No sea-frame read was
found in HUD/TradePanel composition. `tick` reads in layout/HUD/trade are the move
version required because the engine state mutates in place; removing them would
leave stale UI. Retain this separation. BoardLayerKey, WaterLayerKey and Density
are small per-draw objects; reuse only after larger geometry/cloud costs.

## Measurement handoff

On an idle physical device, run `bash tools/perf_ab.sh <signedA.apk> <signedB.apk>`.
It installs A/B/A/B, launches `-e start base`, warms 10 s, resets gfxinfo, captures
15 s, and saves raw dumps plus CSV. Same signing key/settings/device/refresh rate
required. Avoid touching the screen during capture; record temperature and repeat
with clouds/waves disabled to isolate their contribution. It never uninstalls or
clears data. Release APK here is unsigned until the normal owner signing step.

No device, emulator, adb, installation, or graphics profiler was used in this lane.
