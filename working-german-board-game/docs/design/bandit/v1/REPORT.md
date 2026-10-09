# Bandit V1: angry skeleton corsair

One original own-model design, built in `tools/blender/render_bandit_v1.py`: heavy slanted brows and grinning teeth, low broad-brimmed frayed straw hat, irregular charcoal afro, ragged mulberry coat with teal lapels, exposed bones, brass hilt and curved steel cutlass. No top hat, cane, formal suit or red vest; no downloaded models or character references.

Sprites: `bandit_v1_tilt_00.png` through `_07.png`, t=.96*i/7 as PLAN.md specifies, plus flat/mid/max proof sources and front/threequarter portraits: **13 transparent 512px PNGs**. Sprite origin is the ground foot anchor (256,256), ortho scale 2.65; preserve padding. The on-tile silhouette is about 35% of hex width. Portraits use a closer camera and are not shipping sprites.

Review: `../bandit-v1.png` contains both portraits, Timber/Grain at flat/mid/max and a 25% phone proof; `../bandit-v1-phone-25pct.png` is the separate phone sheet. Own model, matching tile upper-left light. Proof composites use placement (+0.25,-0.58) to keep the smaller number plate visible. The source model is the same in every view.

Validation: all thirteen sprites have transparent margins; all six tile composites have zero figure alpha over the number plate; all 146 app-file hashes unchanged. Final retained Bandit render time **39.34s**, excluding Blender startup, modelling and the discarded first portrait. Each batch exits before the next starts.

Reproduce sequentially: Blender `-b -t 2 -P tools/blender/render_bandit_v1.py -- --batch portrait`, then `--batch tiles`, then Python `tools/blender/compose_bandit_v1.py`. Review only: no app wiring, emulator, commit or push.
