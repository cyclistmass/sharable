# Live board spike test plan

The sprite board remains the default. Enable **Settings > 3D board (test)** to exercise the spike,
and choose either detail level there. Pinch, pan and two-finger vertical tilt retain the existing
controls; two-finger twist adds an orbit. Double-tap resets the view.

1. Rules/unit: run `:engine:test :ai:test :net:test :app:testDebugUnitTest`. The new pure tests cover
   projection inversion through full orbits, real-state model selection, owner/material isolation,
   setting persistence/reset, and move selection after projection. Existing dice distribution,
   card/rule/edge-case and reference mapping tests must pass.
2. Fuzz: rerun the engine's 2,000 base and 1,000 expansion random-game invariant checks. Record wins,
   turn-limit draws and actual action counts separately; the benchmark games do not replace them.
3. Break-it: run existing illegal/wrong-time action suites, reject invalid projection/LOD inputs,
   switch renderer/theme/LOD repeatedly, background/foreground, and verify stale taps cannot apply
   an illegal move. Renderer exceptions should revert to sprites and turn the test setting off.
4. Device: use emulator lane 2 with host GPU. First turn the setting on with real taps and place a
   Homestead and a road through the board UI. Test zoom, pan, tilt, twist, restart persistence and
   Inventory. Run `tools/ci_monkey.sh` and bot self-play with the saved live-board setting on.

For measurements, use a genuine bot-played late-game position, paused at a chosen turn. The debug-only
`com.mauro.wgbg.FILAMENT_VIEW` broadcast accepts `live`, `lod`, `pirate`, `sprites3d`, `tilt`, `zoom`,
`rotation`, `freezeTurn` and `playing`. It changes view controls and pauses/resumes bots; it adds no pieces.
These hooks never count as user testing. `WGBG-3D-SNAPSHOT` identifies the position and piece counts.

The renderer logs five-second wall-clock intervals between successful frame submissions, including
skipped `beginFrame` attempts. A debug build also writes raw intervals to
`cache/live-board-frames.jsonl`, accessible with `run-as`. They include Android scheduling and engine
backpressure; CPU submission duration is not GPU duration or proof of physical display presentation.
Check SurfaceFlinger presentation timestamps too. Exclude loading and warm-up, compare flat / 0.48 /
0.96 rad at LOD0 / LOD1 in both themes, record PSS and exact APK bytes, and capture sprite/live pairs on
the same frozen board. Sample orbit angles for scenery occlusion, with the Bandit's intentional cover
excluded. Phone thermal stability, power, presentation latency and memory pressure remain necessary.

`tools/filament/benchmark_lane2.py` captures the matrix using the existing ADB server. Its companion
client is fixed to emulator-5556 and is a fallback for restricted Windows profile lookup. It never
starts/stops the shared ADB server. Set `WGBG_PYTHON` when using its shell wrapper with the CI scripts.
All captured files belong in an external artifact directory, not the source tree.
