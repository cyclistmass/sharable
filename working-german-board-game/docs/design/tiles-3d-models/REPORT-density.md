# V2 density round

Open [animated palette](palette-v2.html), [static palette](palette-v2.png), [before / after](palette-v2-density.png), [larger Stone patch](stone-range-patch_V2_max.png), [goat detail](fleece-animals-detail.png), [bandit options](../bandit/bandit-options.png) and [25% bandit proof](../bandit/bandit-options-phone-25pct.png).

Fleece and Grain show the strongest density gains. Fleece's small brown/white goats retain horns, beard, thin legs and upright tails; sheep retain chunky white wool. Clay uses an actual recessed three-level cut, not rings above a flat surface. Desert retains intentional emptiness between relief dunes. Stone now uses the selected R3 Alpine chain plus three secondary peaks, shared shoulders, warmer rock and snow. Historical R1/R2/R3 study images are retained. See stone-alpine/REPORT.md for the Stone follow-up.

Bandit recommendation: **B3** for the clearest ground-level silhouette at 25%; **B2** for atmosphere and its warm lantern/pool. B1 remains clear but has the weakest ground cue. Reused all three completed own Blender raw renders and 512px sprites from the earlier run. New composite proof is 35% of physical hex width (about 109px model width; 27px at 25%) on Timber flat/max and dense Grain max. Bodies stay upright. This is an art-size proof; gameplay grounding/occlusion and device readability remain integration work.

## Render timing

Blender 4.5.1, Cycles32 denoise (animals24), two threads, one terrain/phase per process. Scene construction/startup not included:

| Terrain | Flat / mid / max |
| --- | --- |
| Fleece | 5.80s / 5.19s / 4.45s |
| Grain | 7.78s / 7.05s / 5.98s |
| Clay | 5.09s / 5.05s / 4.50s |
| Desert | 5.69s / 5.48s / 4.75s |
| Stone | 5.39s / 5.20s / 4.81s |

Stone native1024 max: 19.02s. Each of sixteen animal source phases: 2.59-2.80s. Final source views/animal layers total 143.87s; earlier iterations add time. Blender-reported peak counters are in density-manifests/summary.json; these are not total process resident memory. Bandit reused renders: no Blender render time this round.

## Files

- `{stone,fleece,grain,clay,desert}_V2_{flat,mid,max}.png`: 15 updated transparent512 sources.
- Those five `*_V2_mid_animated.webp`: updated seamless8s previews. Timber's three PNGs and animatedWebP remain byte-identical.
- `palette-v2.html`, `palette-v2.png`, `palette-v2-density.png`; three `inspection-v2-*.png` sheets.
- `stone_V2_max_mock1024.png`, `stone-range-patch_V2_max.png` (1600x1180 edge-sharing geometry proof), `stone-range-sprite-patch_V2_max.png` (2304x1536 game-spacing mock), `board-patch_V2_max.png`.
- `fleece-animals-detail.png`; `animation-layers/fleece/animals_mid_00..15.png`, `animals_mid_atlas.webp`, and `raw/` source renders.
- `animation-layers/grain/wind_mask_mid.png`, illustrative wind patches; `animation-layers/desert/heat_mask_mid.png` and illustrative heat patch.
- `animation-review/`: phases and all64 overlay cells per updated terrain. `animation-validation.json`, `validation-v2.json`, `checks-v2.json`, `density-manifests/` and preserved `density-before/` sources/hashes.
- `../bandit/bandit-options.png` (1536x1780), `bandit-options-phone-25pct.png` (384x445), nine `B*-on-{timber-flat,timber-max,grain-max}.png` composites, reused B1/B2/B3 512pxRGBA sprites/raws, updated validation/report.
- `tools/blender/render_density.py`, `animate_v2.py`, `compose_v2_set.py`, `compose_density.py`, `finalize_density.py`, `run_density.ps1`; PLAN.md and CREDITS.md.

Run `powershell -File tools/blender/run_density.ps1` from this worktree to reproduce sequentially. Pillow is required. The script blocks on every Blender process and fails on nonzero exits. Before snapshots are retained rather than recreated from the after artwork. Existing bandit raw renders/final sprites are reused by composition. Full shipping eight-step views remain planned; only flat/mid/max and mid animations exist.

Validation: 18 static RGBA views/margins; 16 safe animal crops; six decoded loops, 384 frames, exactly8000ms each; exact phase endpoint equality and nonzero motion; HTML local references and JavaScript syntax. Timber + MainActivity + TileLife hashes unchanged. Inspected all final views, phase/overlay sheets, neighbour patches and bandit phone proof. No emulator/adb, app wiring, commit or push.
