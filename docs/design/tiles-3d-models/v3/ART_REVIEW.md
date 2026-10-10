# V3 — second modelling pass

Compared the six painted faces with the existing V3 mid sheet before editing. The previous renders and palette are retained in `before-push/`.

| Terrain | Visible gap to the painted face | Modelling response |
|---|---|---|
| Timber | Round toy canopies; little canopy shadow; camp reads as boxes | Layered needle-bearing branches, overlapping crowns, cut-end rings, bark, shake roofs and logging debris |
| Stone | Smooth dominant peak, muddy unbroken slopes, rudimentary mine | Fractured ledges and rock strata, contrasting warm faces/dark joints, mine bracing/platforms, quarry rubble and sharper pines |
| Fleece | Cotton-ball sheep, uniform walls, evenly scattered flower dots | Smaller wool curls over coherent bodies, articulated faces, irregular coping stones, lush clustered grass, daisies and tall flower spikes |
| Grain | Bristle rows and cylindrical sheaves; box cart | Bent seed-bearing stems, denser irregular heads, flared tied bundles, weathered cart details and straw on paths |
| Clay | Concentric rings, generic shed/kiln, random rubble | Irregular terraced excavation, vertical cuts, kiln bands/brickwork, working platforms, hoist rigging and tools |
| Desert | Pebble noise; indistinct dune crests and pancake rocks | Smooth asymmetric dunes, fine contour ripples, fractured sedimentary outcrops, branching deadwood and clustered scrub |

Across all six: better material-scale variation and contact shadows; close the bare apron visually with ground texture and low detail. Keep the exact current top-right clearing (+0.36,+0.30), plate r0.27 / clearing r0.31 and the low-in-front guard. Do not solve composition by moving the plate.

## Continuation inspection, 2026-10-10

Inspected the partial diff and recomposed the actual starting files against the painted references at equal hex width. The incoming palette was stale: only Timber had been rendered with the new `v3_art.py`; the other five terrain images still showed the older V3 geometry. Retained the botanical/architectural detail module and the crop, terrace and dune improvements.

The partial Timber render had finer geometry but overly dark foliage and obscured logging props. The continuation exposes a winding logging path and both log stacks, raises foliage colour, corrects leaf winding, aims the area light at the tile, and reduces foliage occlusion radius. Stone needs much lighter mineral planes and legible mine structure; Fleece needs clustered flower colour and coherent small animals; Grain needs full sheaf centres instead of hollow circles; Clay needs cut terraces and work-yard detail; Desert needs grouped scrub/outcrops and fewer isolated pebbles. These are visual findings from the starting images, not claims inferred from mesh counts.

Validation plan: render one terrain at a time with two CPU threads; inspect all three final six-terrain sheets, the equal-width painted comparison, and the exact 25% palette and plate proofs. Check transparent 512px bounds, fixed filenames/angles, clearing intrusions and foreground projection at eight angles, neighbour plate overdraw, intact animal assemblies, unchanged app and historical assets, and current source hashes for every render/proof. Rules, emulator and app tests are outside this art-only pass.

## First complete continuation render

Reviewed the six-terrain mid sheet, individual mid/max images and the exact 545x875 phone sheet. Saved `iteration-1-palette.png` and `iteration-1-phone.png` before further edits.

- Timber: brighter needles and a visible logging path work; parallel log stacks still read as dark strips. Angle the stacks to expose more cut ends.
- Stone: pale rock and the mine tower separate much better, but broad peak faces and cut skirts remain smooth. Subdivide only the upper terrain faces against the existing periodic height function and add continuous geological joints/strata in the materials.
- Fleece: the flock, wall and blue flower masses survive reduction. Wool locks are too evenly arranged; jitter their positions and sizes while keeping coherent bodies and all animals intact.
- Grain: the 25% image loses the subpixel grain detail, and the fence is buried. Use fewer, larger visible kernels, brighter straw, taller rooted fencing and flowers along the crop's actual upper perimeter. Filled sheaf centres already improve flat views.
- Clay: richer kiln masonry and excavation colour work, but smoke reads as beads and the long platform planks dominate. Replace the smoke surface with actual volume shading, shorten and rail the platforms, align broken cuts with the squared terrace contours, and reduce loose pit rubble so the excavation is visible.
- Desert: real dune height reads at tilt, but the sand is too mottled and plants are too dispersed. Narrow the sand's pigment range, group the scrub tightly, and emphasize sediment bands on the outcrops.

The phone proof still exposes the compositional cost of the existing upper-right clearing and its low foreground apron. Their position and footprint are deliberately preserved; relocating them belongs to the deferred game-v2 recut.
