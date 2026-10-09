# Bandit options: density-round proof

Reused three completed own procedural Blender models/sprites from the preceding run. No downloaded assets or new Blender rendering needed. Source: tools/blender/bandit.py. New proof/composition: tools/blender/compose_density.py.

- B1: tall dark cloak, pale hood rim and light-facing seam.
- B2: lantern carrier, warm cloak light and transparent amber pool at the feet.
- B3: dark hooded pawn with a pale emissive base ring. Recommended for the strongest ground cue at phone scale; B2 is the atmospheric alternative.

B1.png, B2.png, B3.png: unchanged 512 x 512 transparent RGBA sprites. Fixed feet anchor approximately (256,445); soft contact effects included. B*-render.png: complete original transparent source renders, retained unchanged.

bandit-options.png: 1536 x 1780 wood-colour labelled contact sheet. Standalone sprites plus each option on Timber flat/max and dense Grain max. Nine individual 512 x 440 composites: B*-on-timber-flat.png, B*-on-timber-max.png, B*-on-grain-max.png. Older painted-Timber composites remain historical and are not used by this sheet.

Model silhouette (including lantern/ring, excluding diffuse shadow/glow) spans exactly 35% of the physical hex width: approximately 109px for a 311px hex. Model height stays upright at max tilt. Placement uses the current piece offset (.15 radius, .15 radius projected by cos tilt).

bandit-options-phone-25pct.png: exact 25% resize, 384 x 445. Hex face approximately 78px wide, model approximately 27px. Inspected all three: B3 has the strongest base separation; B2 retains a distinct lantern; B1 has a weaker ground cue. Increased proof margins after finding clipping on the tallest silhouette; final heads/feet fit all placements.

Palette composites give pieces priority for readability. Terrain/pawn mutual occlusion, ground elevation, halo projection by tilt and actual physical-device scale remain integration work. Current drawRobber radius .13 was read only.

Reproduce new proofs with Python + Pillow: python tools/blender/compose_density.py. Completed sprites/raw sources required. validation.json records nine width ratios and source hashes. MainActivity unchanged; no emulator/adb, commit or push.
