# Modelled tiles: route (a) prototype and integration plan

Status: palette for Mauro's selection, not final art or an app integration. Two own procedural styles, Timber and Stone only. No MainActivity or TileLife changes. No downloaded models, AI image generation, emulator, adb, commit or push.

## Palette and geometry

- V1: faceted low-poly pines and crags. Strong small-scale readability; intentionally toy-like.
- V2: softly shaded scalloped pine boughs, pigment variation, irregular ridges and subsidiary crags. Recommended starting point: less mechanical, closer in colour and softness to the existing owned paintings. It is still stylised miniature art, not a reproduction of those paintings.
- Both have real mesh trunks, layered branches, peaks, stones, stacked logs and a bevelled hex with an earth side. The original painted art was inspected for direction but is not baked onto these models.

`Camera.MAX_TILT = 0.96` radians; `Layout.squash = cos(tilt)` in MainActivity. Orthographic elevation is `90° - degrees(tilt)`: flat 90°, mid 62.498°, maximum 34.996°. Preview tilt samples are 0, .48 and .96 radians. No invented 60° maximum. Camera faces from world -Y; +Y maps to screen up. Light is upper left, with a sun and large soft area source plus world illumination. Transparent film, Cycles 32 samples with denoising, Blender 4.5.1 LTS.

Pointy-top hex has circumradius .93, width `sqrt(3) * .93`, height `2 * .93`, matching TileGeometry's inset. Ground/top plane is z=0. Base depth .22 closely approximates the existing full-tilt side depth (.219 scale units); it is intentionally a fixed physical depth rather than reproducing the old tilt-dependent decorative band. At exactly top-down its side naturally vanishes. Bevel .018. Timber rises to roughly .8, highest Stone peak 1.03 world units.

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

After style selection, render one style × six terrain types × eight tilt steps = **48 sprites**. Prototype delivers 2 styles × 2 terrains × 3 angles = 12. No full set until palette selection. Proposed eight steps are `t = .96 * i / 7`, i=0..7: 0°, 7.858°, 15.716°, 23.573°, 31.431°, 39.289°, 47.147°, 55.004°. Six steps reduce storage to 36 sprites but increase silhouette jumps.

The current 512px PNGs are about 160–211 KB each. Keeping PNGs gives an estimated 7.7–10.1 MB for 48 sprites (decimal units); PNG is already compressed, so APK ZIP compression offers limited further savings. These are extrapolations from Timber/Stone, not a measured full-set APK delta. Keep palette, patches and source scripts out of app resources. `validation.json` includes measured lossless WebP and quality-90 WebP byte sizes; compare alpha edges before choosing a shipping codec. Do not ship both styles. Remove old relief assets only after integration and fallback decisions, in a later change.

Trial encoding of these actual renders measured **51.7–84.3 KB for lossless WebP**, implying roughly **2.5–4.0 MB for 48 sprites**. Quality-90 WebP measured **10.2–15.7 KB**, implying **0.49–0.75 MB**, but that smaller option is lossy and has not been visually approved. The lossless option is the initial recommendation. These ranges exclude additional layout variations, optional split passes and Android packaging overhead. Compression reduces APK bytes, not the decoded bitmap budgets below.

| Decoded RGBA8888 strategy at 512 × 512 | CPU bitmap bytes |
| --- | ---: |
| 48 images, all eight angles, all six terrains | 48 MiB |
| 36 images, six angles, all six terrains | 36 MiB |
| Only neighbouring pair, all six terrains | 12 MiB |
| Pair plus one prefetched angle, all six terrains | 18 MiB |
| Prototype, all 12 images | 12 MiB |

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

Keep TileLife.kt and its deterministic eight-second loop and density switch. For an initial integration, keep its existing ground-anchored marks as a global overlay over terrain. This maintains motion but means leaves/glints can appear in front of peaks; it is an artistic overlay, not depth-tested animation. Use the same continuously projected centers, scale and squash; the animated coordinates must not use the padded sprite bounding box.

Before final art, reserve a clear central number-token area (the current forest clearing is small and the Stone ridges occupy the center), road/vertex corridors and the lower-right TileLife marks. An overlay-only number token remains readable but may look as though it floats in front of a mountain. Choose this intentionally or revise model placement. Later, if ground marks must be hidden by nearer terrain, cache each tile's animation layer and composite it in the same rear-to-front sequence, or use a terrain occlusion mask; keep the overlay API and time loop. Do not delete TileLife to solve depth conflicts.

Draw gameplay tokens/pieces after terrain for readability initially; correct terrain/piece mutual occlusion requires ordering individual upright objects or depth data. No claim that the palette has solved this.

## Route (b): real-time Filament / SceneView glTF board

[Filament](https://github.com/google/filament) provides real-time rendering and glTF support on Android. Its [Android samples](https://github.com/google/filament/blob/main/android/samples/README.md) include surface setup, transparent rendering and a glTF viewer. [SceneView](https://github.com/sceneview/sceneview) supplies a Compose-oriented wrapper. Consult and pin the current compatible APIs when that spike begins; this prototype adds neither dependency.

Export the procedural models as GLB, instance shared meshes across tiles, use one orthographic/perspective camera and a depth buffer. Filament's camera would replace the handcrafted row perspective; input unprojection, projected labels and board fit must follow the same new camera. This gives continuous tilt, camera rotation, true mutual occlusion, dynamic light and per-object animation. TileLife can remain a Compose overlay initially; grounding/depth-testing its marks later requires 3D particles or masks.

Costs: rendering surface/lifecycle integration with Compose, native renderer binaries per ABI, materials/export compatibility (Blender noise nodes must be baked or replaced), lighting/environment setup, GPU/frame/battery budget, picking/unprojection, synchronising screen overlays and physical-device testing. GLB mesh assets can be smaller than dozens of raster angles, but native libraries can erase that win. APK and performance costs need measurement from an ABI-specific build, not a guessed universal number. Route (a) needs no new native engine and reuses the Canvas board; route (b) is a renderer project. Planning estimates: several days for a narrow two-terrain spike and at least several weeks for reliable full-board integration, subject to device/occlusion requirements.

Choose (b) if rotation, large close-ups, convincing inter-tile shadows, moving sheep/trees with real depth, or arbitrary piece occlusion become requirements. Stay with (a) for fixed azimuth, bounded tilt, fixed lighting and a modest terrain set if the eight-step fade looks good on device. Reuse these geometry generators either way.

## Validation plan and current results

Palette checks completed: rendered and visually inspected both styles at flat/mid/max, revised V2 foliage from separate blobs to integrated boughs and revised its crags, inspected the final contact sheet and sprite board patches. Automated checks assert all 12 sources are 512 × 512 RGBA, nonempty and have transparent margin on every edge; measured bounds and codec sizes are in validation.json. render-manifest.json records per-render seconds and camera tilts.

Before app integration is accepted:

- Unit/renderer math: exact endpoints, clamped/nonfinite inputs, angle brackets, fixed anchors, stable ordering, bounded cache. Rules unchanged: engine rule/unit and random-play fuzz checks are not applicable to this art-only prototype; run existing regression suites when application wiring occurs.
- Break-it: rapid tilt reversal with pending decode, missing/corrupt asset, decode failure, resize/rotation, low-memory eviction, background/resume. Retain a valid fallback, no decode on the draw thread and no leaked images.
- Visual: all angle midpoints over wood and ocean, alpha/halo and constant-coverage blend tests, seams/gaps, fit bounds, tall object overlaps, number tokens/roads/settlements, TileLife on/off and animation over mountain silhouettes.
- Physical device: frame time, resident/peak memory, APK delta, battery/GPU cost at fit and 4× zoom, accessibility/readability. Emulator/adb and on-device/monkey tests are intentionally not run this round, as Mauro instructed. No claim of app validation.

Reproduction commands and file inventory are in REPORT.md.
