# Modelled tiles: route (a) prototype and integration plan

Status: Mauro selected V2. The full six-terrain V2 palette and eight-second mid-tilt animation previews are now delivered; app integration is still pending. No MainActivity or TileLife changes. No downloaded models, AI image generation, emulator, adb, commit or push. Open `palette-v2.html` for the current set; `palette-models.png` is the earlier style comparison.

## Palette and geometry

- Timber: approved V2 pixel assets retained exactly; layered scalloped pine boughs and real logs.
- Stone: one connected rock heightfield, two roughly 1.0 / .85-unit peaks, a .56-unit saddle, lateral shoulder exits, five lower spurs and foothills. Scree is material on the actual terrain with small loose stones, not separate hovering sheets. High faces retain V2 crag detail. The nine-Stone mock demonstrates lateral ridge continuity and overdraw; repeated silhouettes and the game's inset gaps remain visible.
- Fleece: smooth rolling pasture, three static woolled sheep and a curved dry-stone wall. A fourth grazing/wandering sheep is exclusively a moving sprite layer.
- Grain: nine rows of clustered modelled wheat, two bound sheaves and a small wooden cart.
- Clay: two stepped clay excavations, a brick kiln/chimney with a dark fire mouth and stacked fired bricks.
- Desert: solid wind-shaped dunes with closed edges, rocks, a dead forked tree and dry scrub; no cactus.

All geometry/materials are original procedural V2 miniature art. Nothing is downloaded or baked from the original paintings. Elevated terrain surfaces have skirts down to z=0, so hill/dune edges cannot float above the common base.

`Camera.MAX_TILT = 0.96` radians; `Layout.squash = cos(tilt)` in MainActivity. Orthographic elevation is `90° - degrees(tilt)`: flat 90°, mid 62.498°, maximum 34.996°. Preview tilt samples are 0, .48 and .96 radians. No invented 60° maximum. Camera faces from world -Y; +Y maps to screen up. Light is upper left, with a sun and large soft area source plus world illumination. Transparent film, Cycles 32 samples with denoising, Blender 4.5.1 LTS.

Pointy-top hex has circumradius .93, width `sqrt(3) * .93`, height `2 * .93`, matching TileGeometry's inset. Ground/top plane is z=0. Base depth .22 closely approximates the existing full-tilt side depth (.219 scale units); it is intentionally a fixed physical depth rather than reproducing the old tilt-dependent decorative band. At exactly top-down its side naturally vanishes. Bevel .018. Timber rises to roughly .8, highest new Stone peak approximately 1.0 world units, similar to the earlier 1.03-unit crag.

All renders use the same 2.65-world-unit square viewport and origin anchor (256,256). Ground hex spans approximately 314 × 359 pixels flat; the remaining transparent area accommodates upright content. A 512px sprite therefore does not mean a 512px hex face. Do not auto-crop individual angles independently.

The board patches composite nine **separate sprites** sorted rear to front. Centers and row sizes use the current scalar projection:

```
k(y,t) = 1 + .22 * sin(t) * y * cos(t) / 4.2
screenX = centerX + x * scale * k
screenY = centerY + y * cos(t) * scale * k
spriteSide = 2.65 * scale * k
```

They show overdraw, not a physically continuous perspective scene. Orthographic sprites approximate the app's mild row perspective through per-row uniform scaling; they cannot reproduce its within-hex perspective exactly. The patches omit number tokens, pieces and TileLife, so they prove terrain layering only.

## Assets, APK and memory budget

Current V2 delivery: **6 terrains × 3 angles = 18 transparent 512px static renders**, plus the animation assets below. V1 remains historical and is not part of the shipping budget. Integration should render **6 terrains × 8 tilt steps = 48 sprites**. Proposed eight steps are `t = .96 * i / 7`, i=0..7: 0°, 7.858°, 15.716°, 23.573°, 31.431°, 39.289°, 47.147°, 55.004°. Six steps reduce static storage to 36 sprites but increase silhouette jumps. Only flat/mid/max static views and mid-tilt animations have actually been produced this round; the eight-step shipping set remains follow-up work.

Measured 18-view full-set static assets: **3,221,818 bytes PNG** or **1,184,948 bytes trial lossless WebP**. Individual PNGs are 144,172–219,404 bytes; WebPs are 43,314–97,508 bytes. Extrapolating this three-angle mean to eight angles gives about **8.59 MB PNG** or **3.16 MB lossless WebP** for 48 static images. These are asset estimates, not a measured APK delta; PNG/WebP are already compressed, so ZIP offers limited further savings. `validation-v2.json` records actual per-view sizes/bounds. Use lossless WebP initially. Keep HTML, full animated previews, review sheets and source scripts out of the APK. Remove old relief assets only after integration/fallback decisions in a later change. Compression does not reduce decoded memory.

### Animation assets and runtime choices

All effects use `phase = 2π * ((time % 8) / 8)` and deterministic per-tile offsets; no independent timers or random allocations. `tools/blender/animate_v2.py` is the runnable procedural reference. The six `*_V2_mid_animated.webp` files are full-tile **review previews**, not shipping animation assets. Each has 64 frames × 125ms = 8s with infinite looping, no duplicated endpoint, transparent background and validated 0/8s phase equality. The HTML plays them together, includes a freeze/resume button and honours reduced-motion preferences. It is not application code.

| Terrain | Chosen moving elements | Shipping runtime approach | Temporal sprite frames |
| --- | --- | --- | ---: |
| Timber | Small pale bird with wing beats; three falling leaves | One short polyline and three small ellipses, sine-based paths and smooth fade | 0 |
| Stone | Three faint mist ribbons drifting over the saddle | Cached ribbon paths/soft brush; update control points and opacity | 0 |
| Fleece | One sheep wanders a short closed path, shifts its legs, lowers/raises its head to graze | Blender sheep-only 16-phase atlas per tilt; one sprite draw and a contact-shadow ellipse | 16 per tilt |
| Grain | Wind waves through wheat canopies | Small textured bitmap mesh over the static field, maximum displacement 2px at the 512px source; cart/sheaves excluded by a mask | 0 |
| Clay | Nine smoke puffs curl upward and fade | Nine ellipses with a cached soft brush; recycle age modulo one with zero opacity/velocity at birth/death | 0 |
| Desert | Small slow dust devil | Six translucent spiral paths with a cached soft brush | 0 |

The bird/leaf option preserves Timber's approved geometry; tree meshes themselves are not animated. Three Fleece sheep remain baked and stationary; the fourth both wanders and grazes. Grain moves the actual wheat image patch rather than adding unrelated animated glints. Mist/smoke/dust are deliberately subtle graphic overlays, without physically depth-tested volumetric shadows.

Fleece source: 16 transparent **128 × 128** PNG layers, cropped from 512px sheep-only Blender renders. All crops share top-left **(232,232)** in the full tile viewport and preserve path/pose padding. `sheep_mid_atlas.webp` packs 8 columns × 2 rows into **1024 × 256**, measured **29,482 bytes**, decoded **1 MiB**. Cropping improves both detail and memory versus rendering a mostly empty 256px full-tile layer. Render phases do not include t=8 twice. A renderer may use nearest phase initially (at small tile sizes changes are subpixel), or correctly premultiplied neighbouring-phase mixing for close-ups. The preview uses the latter. Place the entire fixed crop; do not re-anchor each frame to its visible sheep bounds. When motion is disabled, keep the fourth sheep at a fixed phase rather than removing it.

Grain source: one 256px `wind_mask_mid.png`, measured **564 bytes**, plus an illustrative 256px transparent `wind_patch_mid.png/.webp`. Ship the mask, derive the masked patch once from the corresponding 512px base image when decoding/cache-filling, and reuse it. The preview uses its full-resolution base for the warped patch. Cache mesh vertex arrays outside the frame loop; update only positions. The Pillow reference uses a 16 × 16 grid. The future Canvas implementation can use an allocation-free native `drawBitmapMesh` equivalent; benchmark this textured operation separately from the small shape overlays. A shader is an alternative, not a dependency introduced here. Soft brushes/blur are generated/cached once; do not copy the preview's per-frame Pillow blur into the Android frame loop.

| Proposed production assets | Count at eight tilts | Estimated compressed bytes |
| --- | ---: | ---: |
| Six static terrains | 48 files | 3.16 MB lossless WebP |
| Sheep atlases: 16 phases packed per tilt | 8 files (128 source phases) | 0.236 MB, extrapolated from mid |
| Grain masks, one per tilt | 8 files | approximately 0.005 MB, extrapolated from mid |
| Other procedural effects | 0 image files | Code/metadata only |
| Total | **64 image files + shared metadata** | **approximately 3.40 MB** |

At six tilts the equivalent is 36 bases + 6 sheep atlases + 6 masks = **48 image files**, approximately **2.55 MB** using the same means. These estimates exclude optional split base/content passes, variation meshes and packaging overhead. Atlases/masks at seven other camera tilts have not been rendered/measured. Do not multiply all six terrain types by 16 animation phases: only the sheep needs temporal frames. Preview WebPs are much larger, especially the moving wheat patch, because they encode complete composed tiles repeatedly.

| Decoded RGBA8888 strategy at 512 × 512 | CPU bitmap bytes |
| --- | ---: |
| 48 images, all eight angles, all six terrains | 48 MiB |
| 36 images, six angles, all six terrains | 36 MiB |
| Only neighbouring pair, all six terrains | 12 MiB |
| Pair plus one prefetched angle, all six terrains | 18 MiB |
| Current full-set preview, all 18 static images | 18 MiB |

For the recommended two-angle cache, add **2 MiB** for two sheep atlases, **0.125 MiB** for two 256px ALPHA8 grain masks (up to .5 MiB if decoded/uploaded as RGBA), and **2 MiB** for two derived 512px masked wheat patches. The combined CPU image budget is approximately **16.1–16.5 MiB**, plus board caches/offscreen layers, native allocations and GPU copies. All eight sheep atlases resident would cost 8 MiB; avoid loading them all by default. Store effect anchors in world units, crop rectangles and pixel ratios in shared metadata; avoid a decoded sprite or particle collection per terrain instance.

GPU texture uploads can add roughly another copy; decode intermediates, composition layers and the existing board also cost memory. Mipmaps, if used, add approximately one third to texture storage. A 1024px tier quadruples decoded memory and must not become the default. Share bitmaps by terrain/angle, never decode per board hex. Use drawable-nodpi or assets, asynchronous bounded decode cache, adjacent-angle prefetch and eviction. Budget on a low-memory physical Android device before deciding to keep all 48 resident.

## Rendering and cross-fade

1. Keep projection and hit testing in lattice coordinates. Get each center with Layout.project and local scale with hexK. Preserve the fixed origin anchor. Do **not** multiply the whole sprite's height by squash again: its terrain and ground have already been rendered at that elevation.
2. Sort terrain instances by ascending Layout.rowKey (small screen y first), stable tie-break by hex index. Place the complete padded sprite; allow it outside its base hex. Clip only at the board viewport, whose fitted bounds must include peak overhang and bottom thickness. The rear tile is drawn first so nearer peaks cover it.
3. Map `u = clamp(t/.96,0,1) * 7`; `lo=floor(u)`, `hi=min(lo+1,7)`, blend fraction `f=u-lo`. At endpoints and exact samples draw one image. Prefetch both before entering a bracket; retain the last complete pair if decode is late.
4. Mix the two sprites' **premultiplied RGBA**: `C=(1-f)*Clo+f*Chi`, `A=(1-f)*Alo+f*Ahi`, then composite the result once over the board. Ordinary source-over draws with alpha `(1-f)` followed by `f` dim even pixels opaque in both images (coverage is only .75 at midpoint). Use a two-texture shader, or a bounded cleared offscreen layer with additive weighted premultiplied inputs; allocate/cache buffers outside the frame loop. Validate the Android renderer's exact blend behaviour.
5. Cross-fade is a dissolve, not geometric interpolation. Peaks can double briefly and base edges move between steps. Eight angles reduce this but cannot remove it. First compare nearest-angle switching against the dissolve at the actual game size. The three preview angles are not enough for production animation.

Production seam treatment needs a specific follow-up experiment: full-sprite base outlines at sampled angles will not exactly match a continuously tilted lattice. Start with eight complete sprites and measure the worst gap/overlap at each bracket midpoint. If noticeable, export ground/top and terrain as separate passes from this same model, draw the base and ground through the continuous Layout projection, and blend only upright content with shared ground anchors. Terrain cast shadows belong to the ground pass. This increases asset/layer count and complicates compositing; update budgets if needed. Avoid fixing seams by globally squashing upright trees. The old TileRelief band must not be drawn underneath a complete thick-base sprite (double thickness).

Whole-tile painter ordering is an approximation: it works for the demonstrated compact, inset models, but cannot resolve objects from two tiles that physically interpenetrate or arbitrarily cross in depth. Keep tall vegetation and peaks inside the ground inset and reserve edge routes. If this limitation is visible, split each tile into depth bands/individual object sprites or choose route (b). Sprite rotation is not a substitute for another camera azimuth: it would rotate the baked light and upright silhouettes.

## TileLife, tokens and pieces

Keep TileLife.kt and its deterministic eight-second loop and density switch. No app sources were modified this round. In the follow-up, add a separate V2 overlay implementation driven by the same clock, retaining the current TileLife fallback. The new bird/mist/smoke/dust/sheep anchors have world z heights, so project their height as `screenY = centerY + (groundY * cos(tilt) - z * sin(tilt)) * scale * k`; the Blender world +Y convention is screen-up and must be negated when converting to Layout's groundY. Use the same continuous centers/scale and the stored sheep crop offset, not visible image bounds. Smoke originates at the actual kiln chimney (.485,-.125, approximately .69 world z). Grain masks must be exported at each tilt from that angle's base.

For a first integration the overlay can remain a global readable layer, as current TileLife is. Mist/clouds, birds and smoke will consequently draw in front of terrain instead of being physically occluded. For correct neighbours, move per-tile overlays into each tile's rear-to-front terrain group, while preserving the separate animation clock and caching the still board as appropriate. This is an integration change, not something the palette proves. If stationary rendering is cached as one complete board bitmap, per-tile depth-correct motion cannot simply be painted over that single bitmap; maintain row/depth layers or occlusion masks instead.

Before final art, reserve a clear central number-token area (the current forest clearing is small and the Stone ridges occupy the center), road/vertex corridors and the lower-right TileLife marks. An overlay-only number token remains readable but may look as though it floats in front of a mountain. Choose this intentionally or revise model placement. Later, if ground marks must be hidden by nearer terrain, cache each tile's animation layer and composite it in the same rear-to-front sequence, or use a terrain occlusion mask; keep the overlay API and time loop. Do not delete TileLife to solve depth conflicts.

Draw gameplay tokens/pieces after terrain for readability initially; correct terrain/piece mutual occlusion requires ordering individual upright objects or depth data. No claim that the palette has solved this.

## Route (b): real-time Filament / SceneView glTF board

[Filament](https://github.com/google/filament) provides real-time rendering and glTF support on Android. Its [Android samples](https://github.com/google/filament/blob/main/android/samples/README.md) include surface setup, transparent rendering and a glTF viewer. [SceneView](https://github.com/sceneview/sceneview) supplies a Compose-oriented wrapper. Consult and pin the current compatible APIs when that spike begins; this prototype adds neither dependency.

Export the procedural models as GLB, instance shared meshes across tiles, use one orthographic/perspective camera and a depth buffer. Filament's camera would replace the handcrafted row perspective; input unprojection, projected labels and board fit must follow the same new camera. This gives continuous tilt, camera rotation, true mutual occlusion, dynamic light and per-object animation. TileLife can remain a Compose overlay initially; grounding/depth-testing its marks later requires 3D particles or masks.

Costs: rendering surface/lifecycle integration with Compose, native renderer binaries per ABI, materials/export compatibility (Blender noise nodes must be baked or replaced), lighting/environment setup, GPU/frame/battery budget, picking/unprojection, synchronising screen overlays and physical-device testing. GLB mesh assets can be smaller than dozens of raster angles, but native libraries can erase that win. APK and performance costs need measurement from an ABI-specific build, not a guessed universal number. Route (a) needs no new native engine and reuses the Canvas board; route (b) is a renderer project. Planning estimates: several days for a narrow two-terrain spike and at least several weeks for reliable full-board integration, subject to device/occlusion requirements.

Choose (b) if rotation, large close-ups, convincing inter-tile shadows, moving sheep/trees with real depth, or arbitrary piece occlusion become requirements. Stay with (a) for fixed azimuth, bounded tilt, fixed lighting and a modest terrain set if the eight-step fade looks good on device. Reuse these geometry generators either way.

## Validation plan and current results

Current palette checks completed: inspected all six V2 terrains at flat/mid/max, both neighbour patches, each terrain's phase sheet and all 64 cropped overlay-frame cells. Reworked floating scree sheets, sparse wheat, tooth-like clay terrace edges and open dune skirts; improved sheep source detail/cropping and overlay visibility. Automated checks assert all 18 static sources are 512 × 512 RGBA with transparent margins; bounds/codec sizes are in validation-v2.json. render-manifest-v2.json records current per-render timings and sheep crop metadata; animation-validation.json records loop durations, seam checks, motion changes and base hashes. The older render-manifest.json/validation.json describe the initial two-style round.

Before app integration is accepted:

- Unit/renderer math: exact endpoints, clamped/nonfinite inputs, angle brackets, fixed anchors, stable ordering, bounded cache. Rules unchanged: engine rule/unit and random-play fuzz checks are not applicable to this art-only prototype; run existing regression suites when application wiring occurs.
- Break-it: rapid tilt reversal with pending decode, missing/corrupt asset, decode failure, resize/rotation, low-memory eviction, background/resume. Retain a valid fallback, no decode on the draw thread and no leaked images.
- Visual: all angle midpoints over wood and ocean, alpha/halo and constant-coverage blend tests, seams/gaps, fit bounds, tall object overlaps, number tokens/roads/settlements, TileLife on/off and animation over mountain silhouettes.
- Physical device: frame time, resident/peak memory, APK delta, battery/GPU cost at fit and 4× zoom, accessibility/readability. Emulator/adb and on-device/monkey tests are intentionally not run this round, as Mauro instructed. No claim of app validation.

### Follow-up integration sequence

1. Keep this round as palette-only. Evaluate the six terrains and motion at real game size; decide whether Grain's displacement and the low-key mist/smoke need intensity tuning. Reserve number-token space and edge routes in the final compositions before declaring the art ready.
2. Extend `render_v2_set.py` to the eight camera samples, export sheep-only atlases and Grain masks at every angle, record common anchors/height-aware effect metadata, and trial lossless WebP. Do not ship mid-only animation at every tilt: ground contact and chimney anchors would drift.
3. Compare hard nearest-angle selection against premultiplied cross-fade using the existing seam-test requirements. If needed, split ground/base and upright content into passes before wiring. Update the 64-file budget if split passes are chosen.
4. Add a bounded async terrain/angle/atlas/mask cache and the V2 renderer in separate files, then wire it into the board in a later authorized MainActivity round. Retain existing relief/TileLife as selectable fallbacks. Group effects with their terrain when depth is needed; share the 8s clock and obey pause/reduced-motion/density controls without deleting the sheep object.
5. Use one sheep atlas draw, allocation-free curves/ellipses and a reused wheat mesh per tile. Prefetch neighbours; test the grain operation and premultiplied mixes for frame budget, not just the number of draw calls. Use deterministic per-tile phase offsets so duplicate ranges/fields do not all move in lockstep.
6. Validate token/piece priority, all adjacent tilt brackets, APK/memory delta, async decode failure and motion at fit/4× zoom on a physical device. No app/device tests were run this round.

Current added validation: 18 static RGBA images with nonempty transparent margins; 16 cropped sheep layers with safe borders; 1024 × 256 atlas layout; six decoded animated WebPs with 64 frames/8s/infinite loop; exact reference phase 0/8 equality, nonzero frame changes and ordinary-size final-to-first changes. SHA-256 base hashes detect stale render/composition races. Visually inspect all three static inspection sheets, both board patches, every terrain's four-phase sheet and all 64 cropped overlay-frame cells. Browser preview automation is separate from these verified asset checks. Engine rules/unit, random fuzz and emulator/monkey checks remain inapplicable/not run because application/rules code is unchanged and Mauro explicitly forbids emulator work.

Reproduction commands and file inventory are in REPORT.md.
