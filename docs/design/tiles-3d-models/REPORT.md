# Full V2 palette report

Open [palette-v2.html](palette-v2.html) for all six eight-second animated WebPs, freeze/resume and static-angle details. [palette-v2.png](palette-v2.png) shows all 18 flat/mid/max views. Neighbour mocks: [nine Stone ranges](stone-range-patch_V2_max.png), [mixed V2 board](board-patch_V2_max.png).

Mauro's selected V2 is retained. Timber's approved artwork is pixel-identical to HEAD. Stone is now a connected mesh with shoulders, a saddle, five lower spurs, foothills and loose scree. Peak height stays approximately 1.0 world units, similar to the old 1.03. Lateral ridge exits align across neighbours; inset gaps and repeated silhouettes remain visible. Other terrains: rolling Fleece pasture with sheep/wall, clustered Grain rows with sheaves/cart, Clay pits/terraces with kiln/chimney/bricks, and Desert dunes/rocks/dead tree/scrub.

I inspected all 18 source renders through full-resolution angle sheets, both board mocks, six four-phase animation sheets and all 64 cropped overlay-frame cells per terrain. Iterations removed raised scree slabs, filled out sparse wheat, softened jagged pit contours, closed dune/terrain edges, improved tiny-effect visibility and replaced the blurry sheep layer with a full-detail crop. A stale Desert animation base was regenerated; source hashes now guard against render/composition overlap.

| Terrain | Motion | Implementation |
| --- | --- | --- |
| Timber | Pale bird wing beats and falling leaves | Procedural lines/ellipses; approved trees retained |
| Stone | Light mist over the saddle | Three cached soft ribbons |
| Fleece | One sheep shifts, grazes and wanders slowly | 16 Blender phases in one 29 KB transparent atlas |
| Grain | Wind waves bend the field image | Procedural textured mesh with a 564-byte mask |
| Clay | Smoke curls out of the chimney | Nine recycled soft puffs |
| Desert | Tiny drifting dust devil | Six translucent spiral paths |

I recommend restrained effects and lossless WebP assets. Fleece and Clay give clear small storytelling cues; mist and dust deliberately stay quiet. Benchmark Grain's textured operation first during integration. At large zoom, sheep interpolation can show a slight dissolve between poses; compare nearest-phase playback against blending at actual game size. Mist/smoke/dust have no real mutual depth tests. Number-token clearance and continuous-tilt seams remain application work.

Measurements:

- Final 15 new static views: **1.44–2.41 seconds each**, **26.50 seconds** total render calls. Timber was retained.
- Final 16 sheep-only 512px renders: **0.94–1.03 seconds each**, **15.56 seconds** total, then cropped to 128 × 128. Construction/startup, cropping and palette composition are excluded. CPU Cycles, eight threads, Blender 4.5.1 LTS; 32 static samples, 24 sheep samples, denoising.
- All 18 static PNGs: **3.22 MB**; trial lossless WebPs: **1.18 MB**. Estimated eight-tilt set plus sheep atlases/masks: **3.40 MB**, extrapolated, not an APK measurement.
- Each review WebP: 512 × 512 RGBA, **64 frames × 125ms = 8 seconds**, infinite looping. Full composed previews are larger than shipping layers, especially Grain; keep them outside the APK.
- Asset checks passed: static size/alpha margins, sheep crop margins and atlas dimensions, six decoded loops with exact duration, full RGBA reference 0/8s equality, actual motion and ordinary-size wrap changes. No Android/rules tests: app/rules code is unchanged.

Desktop headless-browser screenshot verification could not complete in this restricted environment: Chrome's child/GPU process failed or stalled. HTML references/player syntax and decoded WebP assets were validated, and animation frames visually inspected; browser playback/control behaviour has not been asserted as tested. No emulator or adb.

Files under `docs/design/tiles-3d-models/`:

- `{timber,stone,fleece,grain,clay,desert}_V2_{flat,mid,max}.png` — 18 transparent 512px sources.
- `{terrain}_V2_mid_animated.webp` — six transparent eight-second previews.
- `palette-v2.html`, `palette-v2.png` — player and full contact sheet.
- `board-patch_V2_max.png`, `stone-range-patch_V2_max.png` — 3 × 3 independent sprite mocks.
- `inspection-v2-{flat,mid,max}.png` — views grouped by angle.
- `animation-layers/fleece/sheep_mid_{00..15}.png` — 16 transparent 128px crops.
- `animation-layers/fleece/sheep_mid_atlas.webp` — 1024 × 256 lossless atlas, 8 columns × 2 rows. Crop origin (232,232) in the tile's 512px viewport.
- `animation-layers/grain/wind_mask_mid.png` — 256px mask. `wind_patch_mid.png/.webp` are illustrative transparent patch assets; do not ship both.
- `animation-review/{terrain}_phases.png`, `{terrain}_all_overlay_frames.png` — phase/overlay inspection sheets.
- `render-manifest-v2.json`, `validation-v2.json`, `animation-validation.json` — timings, crops, sizes/bounds, loop checks and source hashes.
- `PLAN.md`, `REPORT.md` — budgets, integration and validation. Earlier style-round files remain historical.

Sources: `tools/blender/render_v2_set.py`, `animate_v2.py`, `compose_v2_set.py`, `check_v2_assets.py`. `checks-v2.json` records the final independent asset/HTML/source checks. Existing `render_models.py` now guards its entry point for safe helper imports. `CREDITS.md` records all own assets. All app files, including MainActivity.kt and TileLife.kt, are untouched. No commit or push.

Separate range studies (`tools/blender/range_options.py`, `range-options/`) and a rerun of the original style renders appeared during this round; this work did not create or edit those studies. They are left intact. The original Timber PNG bytes differ from HEAD because of PNG metadata, but all RGBA pixels match. Earlier V1 render metadata and the old manifest also differ; those separate changes have not been reverted.

Reproduce from the worktree root, **serially**, using PowerShell:

```powershell
& 'C:/Program Files/Blender Foundation/Blender 4.5/blender.exe' -b -t 8 -P tools/blender/render_v2_set.py
& 'C:/Users/mauro/AppData/Local/Programs/Python/Python312/python.exe' tools/blender/animate_v2.py
& 'C:/Users/mauro/AppData/Local/Programs/Python/Python312/python.exe' tools/blender/compose_v2_set.py
& 'C:/Users/mauro/AppData/Local/Programs/Python/Python312/python.exe' tools/blender/check_v2_assets.py
```

Pillow is required for composition. Approved Timber PNGs must be present (tracked here). Optional Blender filters: `-- --terrain stone --skip-sheep` regenerates Stone; `-- --sheep-only` regenerates sheep layers. Each command must finish before the next. These commands produce the three-view/mid-motion palette; eight-tilt integration exports remain future work in PLAN.md.
