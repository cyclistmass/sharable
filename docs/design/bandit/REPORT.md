# Bandit options

Original procedural Blender 4.5 geometry and materials; no external art, image generation, or downloaded assets. Existing project timber faces are used only as composite backgrounds.

- B1: tall midnight cloak, pale linen hood edge and light-facing seam. Soft upper-left key and pale rim lighting.
- B2: same readable silhouette carrying a brass lantern; amber core, warm body bounce, and transparent elliptical warm pool at ground contact.
- B3: dark hooded pawn on an obsidian disc with an ivory emissive base ring. Recommended for the strongest ground-level separation.

`B1.png`, `B2.png`, `B3.png`: 512 × 512 RGBA sprites with transparent backgrounds. Soft ground effects are included in alpha. Feet anchor approximately (256, 445); preserve this anchor when placing sprites.

Each option has three 512 × 432 composites: `B*-on-timber-flat.png`, `B*-on-timber-max.png`, `B*-on-timber-painted.png`. References: the requested V2 flat/max timber PNGs and `app/src/main/res/drawable-nodpi/tile_timber.webp`. Pawn body width is 35% of visible tile width, excluding diffuse glow. Pawn height remains upright in the max-tilt preview.

`bandit-options.png`: labelled 1536 × 1840 contact sheet. `bandit-options-phone-25pct.png`: exact 25% resize, 384 × 460. Visually inspected at this resolution: all three silhouettes remain obvious on all three faces; B2 retains a distinct lantern and pool, while B3 has the clearest base. Individual piping details are secondary at phone scale. This is an art-scale proof, not an emulator or device test.

Reproduce: run Blender with `-b -t 8 -P tools/blender/bandit.py`, then run `docs/design/bandit/compose.py` with Python + Pillow. `B*-render.png` are intermediate transparent renders. `validation.json` records composite dimensions and ratios.

Read `drawRobber` only: current radius is `0.13f * scale`; no app code changed, no emulator run, no commit made.
