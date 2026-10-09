# Stone: selected R3 Alpine chain

Mauro selected R3. The current V2 Stone now has **seven summits**: the study's four plus three secondary peaks at heights .65, .74 and .79. The dominant peak remains 1.08; the .92 secondary remains below it. All belong to one triangulated bedrock surface with broad shared shoulders and saddle links. Snow caps, warm/dark density-pass rock and cleft shading, 76 scree stones and five V2 pines complete the tile.

The R3 study images are preserved. A missing boundary strip in the original study generator was fixed in source; the current mesh contains all 6,144 top triangles plus a closed skirt. No changes to other terrain preview files or app sources were made in this round.

## Files to inspect

- [Flat / mid / max sheet](stone-R3-views.png): the three current 512px transparent Stone views, also installed into the full palette.
- [Nine-tile range](../stone-range-patch_V2_max.png): native edge-sharing model render over wood, with continuous shoulders and neighbour shadows.
- [Game-spacing sprite mock](../stone-range-sprite-patch_V2_max.png): nine independently composited sprites at the current Layout spacing, rear-to-front.
- [25% phone proof](stone-phone-25pct.png): exact 128px single-sprite samples, about 78px of visible hex width; native nine-tile geometry patch at 25%.
- [Phone-size animation](stone-mid-phone-animated.webp): transparent eight-second loop, 64 frames; [all phone frames](stone-phone-all64.png).
- [Full animated palette](../palette-v2.html) and [static palette](../palette-v2.png).

Updated sources: `../stone_V2_flat.png`, `stone_V2_mid.png`, `stone_V2_max.png`, `stone_V2_max_mock1024.png`, and `stone_V2_mid_animated.webp`. `stone_R3_chain_patch_max.png` is the native transparent 1536 x 1024 geometry patch; `stone_R3_chain_patch_max_board.png` adds labels/wood. The canonical range mock is 1600 x 1180; the sprite mock is 2304 x 1536. Both are intentionally retained.

## Continuity and scale

The height function is periodic over physical radius-.93 hexes. Equivalent heights match on all three shared edge orientations to floating-point tolerance; validation samples 99 positions. The nine-instance Blender render uses edge-sharing physical centers, so the terrain is contiguous. The game's nominal-radius-1 Layout retains its existing inset gaps: the sprite mock shows those separately. This is a geometry/art proof, not a claim that route (a)'s app seams or terrain/piece occlusion are solved. Measure gaps during integration; border terrain extensions or a shared ground pass remain options.

Inspected all three views, the geometry patch and sprite patch, all 64 full-size overlay cells, and all 64 phone-size composed frames. At phone scale the dominant peak and smaller white caps remain distinct; warm stone and green foothill pines distinguish the tile. Mist is deliberately faint, and the bird is a small moving accent rather than a gameplay marker.

## Animation and timing

Three height-aware mist wisps cross the saddles; one pale bird follows a closed soaring path with slight wing motion. Both are procedural overlays without temporal sprite assets. The preview uses the existing eight-second clock/reference and transparent compositing; TileLife remains untouched. Phase 0/8 equality, 64 frames x 125ms and infinite-loop metadata are verified.

Final Cycles32 denoised renders, two CPU threads:

| View | Time |
| --- | ---: |
| Flat 512 | 5.39s |
| Mid 512 | 5.20s |
| Max 512 | 4.81s |
| Max single 1024 | 19.02s |
| Nine-instance patch 1536 x 1024 | 55.36s |

Times exclude scene construction/startup. A first inspection batch was discarded after strengthening the shoulder and closing the mesh boundary. Blender exited after the single-tile batch before the patch batch started. Instance meshes are shared rather than duplicated, with only two rendering threads. `render.log`, `patch-render.log` and `../density-manifests/stone*.json` retain render evidence.

Final single views plus the nine-instance patch total 89.78s of rendering. Blender's reported peak counter was about 175 MB for the single-tile batch and 269 MB for the patch; these counters are not total process resident memory.

## Reproduce and verify

From this worktree, run each command to completion before the next:

```powershell
$blender = 'C:/Program Files/Blender Foundation/Blender 4.5/blender.exe'
$python = 'C:/Users/mauro/AppData/Local/Programs/Python/Python312/python.exe'
& $blender -b -t 2 -P tools/blender/render_density.py -- --terrain stone
& $blender -b -t 2 -P tools/blender/render_density.py -- --stone-patch
& $python tools/blender/animate_v2.py --terrain stone
& $python tools/blender/compose_alpine.py
& $python tools/blender/compose_v2_set.py
& $python tools/blender/finalize_density.py
& $python tools/blender/check_v2_assets.py
```

Geometry source: `tools/blender/alpine_chain.py`; dispatcher: `render_density.py`; overlay: `animate_v2.py`; proof: `compose_alpine.py`. All own procedural art, recorded in CREDITS.md. The non-Stone/application baseline and `validation.json` cover 60 preserved file hashes. Updated PLAN.md includes the current storage estimate and geometry-versus-game-spacing distinction. Older density comparison sheets remain historical.

No app code, emulator/adb, commit or push. Blender is closed after rendering. Browser playback/device frame-time testing is outside this palette round; local references and JavaScript syntax are checked.
