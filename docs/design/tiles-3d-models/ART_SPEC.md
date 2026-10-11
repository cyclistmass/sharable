# V3 tile art specification (V2 is historical)

- Names: **Timber, Stone, Fleece, Grain, Clay, Desert**. Use `terrain_V3_flat/mid/max.png` (V2 is historical), lower-case terrain keys in scripts, descriptive own-model names; no borrowed game names.
- Number plate: nominal hex side **1**, centre **(+0.36, -0.30)** in the game's screen-down lattice coordinates, or **(+0.36, +0.30)** in Blender's screen-up ground plane. Target radius **0.27**. `MainActivity.kt`, `drawTile` → `drawNumberToken`, currently uses the older top-left position and radius-0.30 upright circle; V3 targets top-right and radius 0.27, pending app integration. Its centre's vertical offset scales with `cos(tilt)`; its radius does not.
- Every terrain reserves a **flat, empty ground circle of radius 0.31** (about 15% larger). Forest glade / rock shelf / trampled pasture / threshed wheat / levelled clay yard / sand pan. Blend the edge into local terrain; keep props, canopy overhangs and moving layers outside it. Desert reserves the space even though its game tile is normally unnumbered.
- Camera faces from **world -Y**. Keep foreground ground and props low enough to miss the upright plate at **55°**, including a safety apron beyond the projected clearing ellipse. Put tall trees, peaks and buildings behind or beside it. Also protect the next rear neighbours' plates; the shared height guard uses their nominal hex centres (+/-sqrt(3)/2, +1.5). Validate with real-size round plates at **0°, 27.5°, 55°**, and depth-sort tile-plus-plate layers in a mixed neighbour proof. Never hide collisions by painting every plate on top of the whole board.
- Hex: pointy-top, visible inset radius **0.93**, width **1.6108**, base top **0**, bottom **-0.22**, bevel **0.018**; retain the existing V2 edge material. Terrain clearing surface sits just above the base to avoid coincident-face artifacts.
- Orthographic transparent RGBA, **512 × 512**, camera scale **2.65**; upper-left sun plus soft sky. Palette: moss greens, warm dark rock and ivory snow, golden wheat, rust clay, ochre sand; wood **#4A3020** and old brass for review sheets.
- Assets must be **our own or clearly CC0**, recorded in `CREDITS.md`. These models, effects and proof plates are own procedural assets. Keep TileLife as the overlay layer; apply the same plate exclusion mask after displacement, interpolation and blur.

Reproduce V3 sequentially with `tools/blender/run_v3.ps1`; V2 sources retain their historical left-hand clearing. V3 applies (+0.36,+0.30) inside its renderer/composer process, without changing V2 scripts or app code. No application changes are part of this art pass.

The second V3 modelling pass keeps this top-right position; a lower-right recut is explicitly deferred. Judge the painted/V3 comparison at equal hex width and at 25% scale. Use layered conifer branches, coherent animal bodies with fine fleece, clustered botanical detail, tied/fanning wheat sheaves, irregular excavation cuts and fractured rock ledges. Surface pigment and flat ground markings may texture the protected foreground apron, but must not introduce raised scenery over the plate. Source: `tools/blender/v3_art.py`; preserve the base and eight-angle sequence.

The October 11 completion adds the larger story forms in `tools/blender/v3_story.py`: loaded logging sled, split mineral slabs, pasture gate/hayrack, enlarged tied sheaves, drying-brick pallet, sandstone crowns and broad-leaf islands. Tapered leaning wheat ears, sharp clay terrace normals and softly fading smoke volumes refine the previous detail. Ground-level apron patterns stay outside the existing clearing and below its ground-height tolerance. `v3/resume-baseline.png` records the incoming mixed-generation set, and `v3/story-iteration.png` records the first comparison before the last refinements. The final `palette-v3.png` and exact 25% proof must be generated only from complete, current render manifests.

- **V3 Fleece:** sheep are the main flock; retain a few SMALL alpacas at 65% of the previous model size, long necks/upright ears and mixed fleece, and teeny horned/bearded goats. Lush pasture, flower/hedge groundcover, dry-stone walls and gate. V2 alpaca-only art is historical.
- **Clay colour:** a restrained terracotta/brick-red ground, terraces and cut faces; wet bottom darker red-brown. Keep the kiln and brick stacks distinct, and Clay clearly redder than Desert ochre.

- **V3 density/palette:** use Mauro's painted `tile_<terrain>.webp` faces as terrain/story references. Fill the hex to its edges with detailed real geometry, except the single top-right clearing and protected low foreground. Rich forest/meadow greens, golden crops, red-orange Clay, warm Alpine rock/snow and ochre dunes; material colour noise/fine bump, geometry AO and warm upper-left light. Logging camp/logs/stumps; Clay kiln/smoke/crane/rails/bricks; Stone mine headframe/rails/quarry/pines; field cart/stooks/fence; Desert rock clusters/scrub/dead tree. Current V3 pass is static; rebuild V2 TileLife anchors/guards before reuse.


## Bandit
The approved original V1 skeleton corsair **stands on and blocks the number plate**. Ground foot/base centre is tile **(+0.36,+0.30)** in Blender for V3, projected screen offset (+0.36,-0.30*cos(tilt)). Sprite anchor is **(256,256)** in the padded 512px image, orthographic scale **2.65**. Own model scale **0.70** (previous off-plate prototype 0.46); full-round straw brim diameter **0.644**, above the plate diameter **0.54**. Turn the front brim upward so the skull remains readable at tilt.

Bake an **opaque upright dark base seal radius 0.278** under the feet, matching the upright plate at every tilt. Draw plate first, then the complete Bandit sprite; never redraw blocked digits/pips over it. Preserve padding and use premultiplied cross-fades. Eight steps use tilt=0.96*i/7. Verify zero visible digit/pip pixels at every step, plus flat/mid/max tile and phone proofs. Reproduce with `render_bandit_v1.py --batch onplate`, then `compose_bandit_onplate.py`; earlier off-plate proofs and the left-centre on-plate proof are historical; the current V3 number centre supersedes their tile placement. The Bandit sprite foot anchor/scale stay the same.

## Tilt steps and GPU rendering

The game cross-fades each object between the two nearest **tilt steps**. Step *i* of *N* is rendered at
`tilt = 0.96 * i / (N - 1)` radians (0 = straight down, 0.96 ≈ 55° = full tilt). The app's standard is **8 steps**
(`<terrain>_V3_tilt_0..7.png`); denser sets (e.g. 16 steps in `v3/steps16/`) give smoother tilting at a larger APK size.
Render them with `tools/blender/render_v3_steps.py`, on the GPU via `tools/blender/gpu_on.py`. See
`tools/blender/README.md` for the options and measured speeds.
