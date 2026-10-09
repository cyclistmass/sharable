# Palette report

Open [palette-models.png](palette-models.png). Full-size neighbour mocks: [V1](board-patch_V1_max.png), [V2](board-patch_V2_max.png).

Two variants: V1 faceted low-poly; V2 sculpted stylised foliage and irregular crags. Both are genuine procedural Blender meshes. V2 is my recommendation for its softer tree silhouettes, subtle surface pigment and more natural ridge shapes. V1 is the better option if simple, crisp miniature pieces are preferred. Neither matches the detail density of the original painted illustrations; this palette establishes modelled volume and an art direction before investing in the full set.

I inspected the first output, replaced V2's separate foliage blobs with integrated scalloped boughs, reduced random triangular rock colour changes and added smaller crags. The final 12 sprites pass size/alpha-margin checks. Nine independently rendered sprites overlap rear neighbours correctly in each max-tilt patch. This is a visual terrain prototype, not gameplay validation; number-token clearance and sampled-angle seams remain integration work.

Final render timings: 1.69–2.19 seconds per 512px tile, 22.64 seconds summed render calls (excluding scene construction/startup/composition), CPU Cycles with eight threads, 32 samples and denoising, Blender 4.5.1 LTS. Two render passes were made for inspection/refinement. Exact per-image final timings are in render-manifest.json. Times vary by host.

Files:

- `timber_V1_{flat,mid,max}.png`, `stone_V1_{flat,mid,max}.png`
- `timber_V2_{flat,mid,max}.png`, `stone_V2_{flat,mid,max}.png` — 12 transparent 512px tile sources total.
- `palette-models.png` — labelled contact sheet on the app's wood midtone #4A3020.
- `board-patch_V1_max.png`, `board-patch_V2_max.png` — 1024 × 680, nine tiles each; actual sprite composition with Layout's row scaling.
- `render-manifest.json` — Blender version, viewport, origin anchor, tilt and timings.
- `validation.json` — image dimensions/alpha checks, actual PNG and trial WebP byte sizes. Trial WebPs are measured in memory, not shipping assets.
- `PLAN.md`, `REPORT.md` — integration, limitations, budgets, comparison and test plan.
- `tools/blender/render_models.py`, `tools/blender/compose_palette.py` — deterministic model/render and Pillow contact-sheet/board-patch source.
- `CREDITS.md` — own-asset provenance added.

From the worktree root, PowerShell:

```powershell
& 'C:/Program Files/Blender Foundation/Blender 4.5/blender.exe' -b -t 8 -P tools/blender/render_models.py
& 'C:/Users/mauro/AppData/Local/Programs/Python/Python312/python.exe' tools/blender/compose_palette.py
```

The second command requires Pillow. All output paths resolve relative to the scripts' repository root; no external writes or downloaded assets. Model scenes rebuild procedurally; no .blend file is necessary. The original app renderer and TileLife are untouched. No emulator, adb, commit or push.
