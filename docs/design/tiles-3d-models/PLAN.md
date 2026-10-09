# Modelled V2 tiles: density pass and integration plan

Status: number-clearing palette revision across all six terrains; MainActivity and TileLife unchanged. Current proof and measurements: token-clearing/REPORT.md, token-check.png and ART_SPEC.md. Earlier density/R3 reports are historical.

## Geometry and projection

- Fleece: lush rolling grass, flowers, bushes, three-course irregular dry-stone wall and wooden gate; six baked alpacas with long fluffy necks, upright ears and mixed cream/white/fawn/brown coats (two looking up), plus two tiny brown/white goats on rock outcrops. The exclusive moving layer adds one grazing/wandering alpaca and one grazing goat: seven alpacas / three goats in motion. Goats have slimmer bodies, upright tails, horns and beards.
- Grain: curved rows cut around the plate reservation, dense modelled ears with kernels/awns, continuous rounded canopy masses, three stooks, a wooden cart with wheels/spokes and poppies along the field edge.
- Clay: one deep excavation with three cut terraces, terracotta-red terraces/cut faces, wet dark red-brown floor/puddle, kiln/chimney, brick stacks, two slatted drying racks and plank shed. The hex top cap is opened so it cannot cover the below-ground pit; floor z=-.115 remains above the base bottom -.22.
- Desert: diagonal relief dunes with modelled ripples, rock clusters, loose stones, scrub, dead bleached tree and tiny skull. Deliberately retains more open sand than the other terrains.
- Stone: selected R3 Alpine chain with seven varied summits arranged around the number shelf (.65/.74/.79), shared shoulders/saddles, warm dark bedrock/clefts, snow caps, scree and foothill pines. Three 512px views plus a 1024px single source; the main neighbour proof renders nine edge-sharing model instances at 1536 x 1024. A separate 2304 x 1536 sprite mock retains game spacing. See token-clearing/REPORT.md for the current revision.

Names, hex base/thickness and upper-left sun/soft sky follow the approved V2 set. Camera.MAX_TILT=.96 radians; Layout squash=cos(tilt). Orthographic elevations are 90, 62.498, 34.996 degrees for tilt 0, .48, .96. World +Y maps screen-up. Pointy hex radius .93, depth .22, bevel .018; viewport 2.65 world units. Every static sprite is padded 512 x 512 RGBA, origin (256,256); widest hex face is about 311px. Preserve padding/anchor and do not squash the already rendered geometry again.

`render_density.py` merges grass, flowers, wheat and rubble by material to avoid thousands of scene objects. Run with two threads and close Blender after each terrain / animal-phase process. `run_density.ps1` waits for each process exit before starting the next. Current retained static renders and the native Stone patch total 158.87s, excluding construction/startup and discarded iterations; current per-view timings are in token-clearing/budget.json.

## Assets, APK and memory

Current delivery: six terrains × three views = 18 static sources, all refreshed for number clearance. PNGs: **3,485,237 bytes**; trial lossless WebP: **1,347,352 bytes**. Eight-step estimate remains 72 shipping files + metadata, **4,065,603 bytes**, excluding review animations and sheets.

| Proposed eight-step shipping assets | Count | Estimated compressed bytes |
| --- | ---: | ---: |
| Six terrain bases | 48 | 3,592,939 |
| 16-phase alpaca-and-goat atlases | 8 | 371,136 |
| Grain wind masks | 8 | 16,528 |
| Desert heat masks | 8 | 24,376 |
| Total | **72 files + metadata** | **4,065,603 bytes** |

At six steps: 36 bases + 6 atlases + 12 masks = 54 files, about 3.05 MB. Preview animated WebPs, raw 512px animal sources, historical sheep-only sprites, comparison sheets, 1024px Stone mock and bandit options are excluded. Ship only the selected bandit, separately measured. PNG/WebP ZIP savings are small; these are asset estimates, not measured APK deltas.

Decoded RGBA at 512px: all eight angles cost 48 MiB; the recommended adjacent-angle pair shared across six terrains costs 12 MiB. Two 1024 x 256 animal atlases add 2 MiB; two derived Grain patches and two Desert patches add 4 MiB. Four 256px ALPHA8 masks add .25 MiB (1 MiB if RGBA). Total active CPU bitmap estimate **18.25-19 MiB**, plus decode intermediates, board layers and possible GPU copies. Avoid per-hex duplicates and eagerly loading all angles. Mipmaps add about one third; a 1024px tier quadruples memory.

## Animation layers

Use TileLife's existing eight-second clock with deterministic offsets. Full-tile animated WebPs are review files: 64 x 125ms, infinite loops and exact phase 0/8 equality. Keep the still animal phase when motion is paused.

| Terrain | Cheapest selected runtime approach |
| --- | --- |
| Timber | Approved procedural bird polyline and three fading leaves; no new temporal assets |
| Stone | Three drifting blurred saddle wisps plus one soaring bird; cached brush/polyline, no temporal assets |
| Fleece | One 16-phase combined sheep/goat atlas per tilt, premultiplied interpolation, one shadow ellipse |
| Grain | Masked static wheat patch on a reused 16 x 16 mesh, 2px sine displacement |
| Clay | Nine looping smoke ellipses with cached soft brush, anchored at chimney (.515,-.045,.726) |
| Desert | Masked dune patch on reused mesh, .8px displacement for subtle heat shimmer |

Animal sources: 16 transparent 128 x 128 crops at fixed origin **(232,208)** in the 512px tile, safely padded over the entire wander loop. `animals_mid_atlas.webp`: 8 columns x 2 rows, 1024 x 256, **46,392 bytes**, decoded 1 MiB. Historical `sheep_mid_*` files are not used by the density palette. At a new tilt, rerender the animal layer and remeasure a common crop; do not reuse a mid-only anchor at max.

Grain `wind_mask_mid.png`: 2,972 bytes; Desert `heat_mask_mid.png`: 2,849 bytes, both 256px. Derive masked patches once from the corresponding base at cache fill; patch PNG/WebP examples need not ship. Grain excludes the cart/stooks; Desert limits distortion to central dunes, away from the base outline. Mesh arrays and blur brushes must be cached, not allocated/blurred each Android frame. Interpolation and effect occlusion still need integration measurement.

## Rendering and cross-fade

1. Keep projection and hit testing in lattice coordinates. Get each center with Layout.project and local scale with hexK. Preserve the fixed origin anchor. Do **not** multiply the whole sprite's height by squash again: its terrain and ground have already been rendered at that elevation.
2. Sort terrain instances by ascending Layout.rowKey (small screen y first), stable tie-break by hex index. Place the complete padded sprite; allow it outside its base hex. Clip only at the board viewport, whose fitted bounds must include peak overhang and bottom thickness. The rear tile is drawn first so nearer peaks cover it. R3 shoulder heights are periodic across physical hex edges (radius .93); the current Layout centers use nominal radius 1 and retain inset gaps. The geometry proof is edge-sharing, while stone-range-sprite-patch_V2_max.png shows the actual game-spacing approximation. Measure these gaps before integration; extend border terrain or use a shared ground pass if they break continuity.
3. Map `u = clamp(t/.96,0,1) * 7`; `lo=floor(u)`, `hi=min(lo+1,7)`, blend fraction `f=u-lo`. At endpoints and exact samples draw one image. Prefetch both before entering a bracket; retain the last complete pair if decode is late.
4. Mix the two sprites' **premultiplied RGBA**: `C=(1-f)*Clo+f*Chi`, `A=(1-f)*Alo+f*Ahi`, then composite the result once over the board. Ordinary source-over draws with alpha `(1-f)` followed by `f` dim even pixels opaque in both images (coverage is only .75 at midpoint). Use a two-texture shader, or a bounded cleared offscreen layer with additive weighted premultiplied inputs; allocate/cache buffers outside the frame loop. Validate the Android renderer's exact blend behaviour.
5. Cross-fade is a dissolve, not geometric interpolation. Peaks can double briefly and base edges move between steps. Eight angles reduce this but cannot remove it. First compare nearest-angle switching against the dissolve at the actual game size. The three preview angles are not enough for production animation.

Production seam treatment needs a specific follow-up experiment: full-sprite base outlines at sampled angles will not exactly match a continuously tilted lattice. Start with eight complete sprites and measure the worst gap/overlap at each bracket midpoint. If noticeable, export ground/top and terrain as separate passes from this same model, draw the base and ground through the continuous Layout projection, and blend only upright content with shared ground anchors. Terrain cast shadows belong to the ground pass. This increases asset/layer count and complicates compositing; update budgets if needed. Avoid fixing seams by globally squashing upright trees. The old TileRelief band must not be drawn underneath a complete thick-base sprite (double thickness).

Whole-tile painter ordering is an approximation: it works for the demonstrated compact, inset models, but cannot resolve objects from two tiles that physically interpenetrate or arbitrarily cross in depth. Keep tall vegetation and peaks inside the ground inset and reserve edge routes. If this limitation is visible, split each tile into depth bands/individual object sprites or choose route (b). Sprite rotation is not a substitute for another camera azimuth: it would rotate the baked light and upright silhouettes.

## TileLife, tokens and pieces

Keep TileLife.kt and its deterministic eight-second loop and density switch. No app sources were modified this round. In the follow-up, add a separate V2 overlay implementation driven by the same clock, retaining the current TileLife fallback. The new bird/mist/smoke/heat/animal anchors have world z heights, so project their height as `screenY = centerY + (groundY * cos(tilt) - z * sin(tilt)) * scale * k`; the Blender world +Y convention is screen-up and must be negated when converting to Layout's groundY. Use the same continuous centers/scale and the stored animal crop offset, not visible image bounds. Smoke originates at the actual kiln chimney (.515,-.045, approximately .726 world z). Grain masks must be exported at each tilt from that angle's base.

For a first integration the overlay can remain a global readable layer, as current TileLife is. Mist/clouds, birds and smoke will consequently draw in front of terrain instead of being physically occluded. For correct neighbours, move per-tile overlays into each tile's rear-to-front terrain group, while preserving the separate animation clock and caching the still board as appropriate. This is an integration change, not something the palette proves. If stationary rendering is cached as one complete board bitmap, per-tile depth-correct motion cannot simply be painted over that single bitmap; maintain row/depth layers or occlusion masks instead.

Before final art, reserve a clear central number-token area (the current forest clearing is small and the Stone ridges occupy the center), road/vertex corridors and the lower-right TileLife marks. An overlay-only number token remains readable but may look as though it floats in front of a mountain. Choose this intentionally or revise model placement. Later, if ground marks must be hidden by nearer terrain, cache each tile's animation layer and composite it in the same rear-to-front sequence, or use a terrain occlusion mask; keep the overlay API and time loop. Do not delete TileLife to solve depth conflicts.

Draw gameplay tokens/pieces after terrain for readability initially; correct terrain/piece mutual occlusion requires ordering individual upright objects or depth data. No claim that the palette has solved this.

## Route (b): real-time Filament / SceneView glTF board

[Filament](https://github.com/google/filament) provides real-time rendering and glTF support on Android. Its [Android samples](https://github.com/google/filament/blob/main/android/samples/README.md) include surface setup, transparent rendering and a glTF viewer. [SceneView](https://github.com/sceneview/sceneview) supplies a Compose-oriented wrapper. Consult and pin the current compatible APIs when that spike begins; this prototype adds neither dependency.

Export the procedural models as GLB, instance shared meshes across tiles, use one orthographic/perspective camera and a depth buffer. Filament's camera would replace the handcrafted row perspective; input unprojection, projected labels and board fit must follow the same new camera. This gives continuous tilt, camera rotation, true mutual occlusion, dynamic light and per-object animation. TileLife can remain a Compose overlay initially; grounding/depth-testing its marks later requires 3D particles or masks.

Costs: rendering surface/lifecycle integration with Compose, native renderer binaries per ABI, materials/export compatibility (Blender noise nodes must be baked or replaced), lighting/environment setup, GPU/frame/battery budget, picking/unprojection, synchronising screen overlays and physical-device testing. GLB mesh assets can be smaller than dozens of raster angles, but native libraries can erase that win. APK and performance costs need measurement from an ABI-specific build, not a guessed universal number. Route (a) needs no new native engine and reuses the Canvas board; route (b) is a renderer project. Planning estimates: several days for a narrow two-terrain spike and at least several weeks for reliable full-board integration, subject to device/occlusion requirements.

Choose (b) if rotation, large close-ups, convincing inter-tile shadows, moving alpacas/trees with real depth, or arbitrary piece occlusion become requirements. Stay with (a) for fixed azimuth, bounded tilt, fixed lighting and a modest terrain set if the eight-step fade looks good on device. Reuse these geometry generators either way.

## Validation plan and current results

Inspected all eighteen views, six four-phase sheets and all 64 cells of each overlay sheet, the native Stone patch, and token checks at 25% phone scale. The mixed nine-tile proof draws terrain + plate layers rear-to-front and passes with zero covered plate pixels. All 384 overlay frames have zero alpha in the reserved screen circles; six decoded loops are seamless, 64 frames / 8 seconds each. All 146 application-file hashes match the baseline. ART_SPEC.md is the shared geometry rule; token-clearing/REPORT.md and budget.json record the current sources, timings and bytes. Earlier density and R3 reports are historical.

Before app integration is accepted:

- Unit/renderer math: exact endpoints, clamped/nonfinite inputs, angle brackets, fixed anchors, stable ordering, bounded cache. Rules unchanged: engine rule/unit and random-play fuzz checks are not applicable to this art-only prototype; run existing regression suites when application wiring occurs.
- Break-it: rapid tilt reversal with pending decode, missing/corrupt asset, decode failure, resize/rotation, low-memory eviction, background/resume. Retain a valid fallback, no decode on the draw thread and no leaked images.
- Visual: all angle midpoints over wood and ocean, alpha/halo and constant-coverage blend tests, seams/gaps, fit bounds, tall object overlaps, number tokens/roads/settlements, TileLife on/off and animation over mountain silhouettes.
- Physical device: frame time, resident/peak memory, APK delta, battery/GPU cost at fit and 4× zoom, accessibility/readability. Emulator/adb and on-device/monkey tests are intentionally not run this round, as Mauro instructed. No claim of app validation.

### Follow-up integration sequence

1. Keep this round as palette-only. Evaluate the six terrains and motion at real game size; decide whether Grain's displacement and the low-key mist/smoke need intensity tuning. Reserve number-token space and edge routes in the final compositions before declaring the art ready.
2. Extend `render_density.py` to the eight camera samples, export alpaca-and-goat atlases and Grain/Desert masks at every angle, record common anchors/height-aware effect metadata, and trial lossless WebP. Do not ship mid-only animation at every tilt: ground contact and chimney anchors would drift.
3. Compare hard nearest-angle selection against premultiplied cross-fade using the existing seam-test requirements. If needed, split ground/base and upright content into passes before wiring. Update the 72-file budget if split passes are chosen.
4. Add a bounded async terrain/angle/atlas/mask cache and the V2 renderer in separate files, then wire it into the board in a later authorized MainActivity round. Retain existing relief/TileLife as selectable fallbacks. Group effects with their terrain when depth is needed; share the 8s clock and obey pause/reduced-motion/density controls without deleting the sheep object.
5. Use one alpaca-and-goat atlas draw, allocation-free curves/ellipses and a reused wheat mesh per tile. Prefetch neighbours; test the grain operation and premultiplied mixes for frame budget, not just the number of draw calls. Use deterministic per-tile phase offsets so duplicate ranges/fields do not all move in lockstep.
6. Validate token/piece priority, all adjacent tilt brackets, APK/memory delta, async decode failure and motion at fit/4× zoom on a physical device. No app/device tests were run this round.

Current added validation: 18 static RGBA images with nonempty transparent margins; 16 cropped alpaca-and-goat layers with safe borders; 1024 × 256 atlas layout; six decoded animated WebPs with 64 frames/8s/infinite loop; exact reference phase 0/8 equality, nonzero frame changes and ordinary-size final-to-first changes. SHA-256 base hashes detect stale render/composition races. Visually inspect all three static inspection sheets, both board patches, every terrain's four-phase sheet and all 64 cropped overlay-frame cells. Browser preview automation is separate from these verified asset checks. Engine rules/unit, random fuzz and emulator/monkey checks remain inapplicable/not run because application/rules code is unchanged and Mauro explicitly forbids emulator work.

Reproduce with tools/blender/run_token_clearings.ps1; current preview file inventory is token-clearing/FILES.txt.

## Shared number clearing (current revision)

Follow ART_SPEC.md and the common `token_space.py` constants. Store centre (-0.36, +0.30), ground radius 0.31 and upright plate radius 0.27 in renderer metadata. At runtime project the centre with Layout's tilt/scale, but keep the plate circle upright. Cache one ALPHA8 guard per angle (or generate it analytically); no new shipping files are needed. Apply it after all TileLife blur, sprite interpolation and Grain/Desert displacement. A pair of 512px ALPHA8 guards adds 0.5 MiB to the active bitmap estimate; use 256px cached masks with a conservative margin for 0.125 MiB instead. Do not allocate per hex or per frame.

Order each terrain sprite and its number plate together back-to-front; keep pieces/UI in their existing passes. Verify neighbour overdraw using token-check-patch-max.png before wiring. The low foreground geometry is part of the art, not only a compositing mask. All 18 sources and six review loops were regenerated; the eight production tilt steps still need sequential rendering. Current budget/render evidence is token-clearing/REPORT.md; use `run_token_clearings.ps1` to reproduce this revision.

## Bandit V1 review prototype

One original skeleton corsair, generated by `tools/blender/render_bandit_v1.py`. Eight transparent 512px production-angle PNGs follow t=.96*i/7; their measured total is 642,790 bytes. Three flat/mid/max review sources and two closer portraits are additional review files. Preserve the fixed ground-foot anchor (256,256) and ortho scale 2.65; a later renderer should place this already-sized figure rather than reuse the nearly invisible 0.13 pawn scale. Decoding two adjacent RGBA sprites costs 2 MiB, plus transient decode storage. Cross-fade with premultiplied alpha and draw the pawn in the board depth order.

The Timber/Grain proof places its foot at (+0.25,-0.58), keeping the upright radius-0.27 plate clear. Actual placement and neighbour occlusion still need game integration checks. Review `../bandit/bandit-v1.png`, `../bandit/bandit-v1-tilt-steps.png` and the separate 25% phone proof. Reproduce portraits, then tilt sprites in separate sequential Blender batches, then `compose_bandit_v1.py`. No app integration is included.
