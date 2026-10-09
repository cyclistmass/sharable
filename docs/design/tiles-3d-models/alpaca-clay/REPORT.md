# Alpaca Fleece and terracotta Clay

Clay ground, terraces, cut faces and the number yard now use restrained terracotta-red materials. Wet clay and the puddle are dark red-brown. Kiln bricks retain their darker fired-brick material, so the structures stay legible; Desert remains ochre.

Fleece has six static alpacas in mixed cream/white/fawn/brown fleece, with two looking up and four grazing. A seventh gently shifts, bends its long neck and turns its head in the 16-phase transparent animal layer. The three tiny horned/bearded goats are retained (two static, one moving). Wall, gate, lush pasture and the flat number circle are retained.

Updated sources: `clay_V2_flat/mid/max.png`, `fleece_V2_flat/mid/max.png`, their two 8s animated WebPs, all 16 `animation-layers/fleece/animals_mid_XX.png` crops and atlas, `palette-v2.html/png`, `token-check.png`, `token-check-patch-max.png`, `ART_SPEC.md`, `PLAN.md`, and `CREDITS.md`. Own model source: `tools/blender/alpacas.py`, with material/placement changes in `render_density.py` and `token_space.py`. Full reproduction: `run_token_clearings.ps1`; focused review: `compose_alpaca_clay.py`.

Phone proof: `clay-fleece-phone-25pct.png`; animation: `fleece-phone-animated.webp` and its 64-frame sheet. Current six view renders and sixteen motion phases total **82.44s** of Blender render time, excluding construction/startup and the discarded Fleece pose iteration. Every Blender process used two threads and exited before the next started.

Checks: six requested views re-rendered; all 18 current RGBA sources pass padding checks; all six current loops decode to 64 frames / 8s and are seamless; 384 overlay frames have no alpha in the plate guard; mixed 3×3 plate proof has no neighbour occlusion; all 146 application-file hashes remain unchanged. No app code, emulator, commit or push.
