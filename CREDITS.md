# Credits

## Art

- Wood grain on the player pieces and tokens (`app/src/main/res/drawable-nodpi/wood_grain.png`):
  "Wood049" from ambientCG (https://ambientcg.com/view?id=Wood049), CC0 1.0 Universal (public domain).
  Converted to grayscale and stained per player at runtime.

All other graphics are drawn procedurally in code.
- Modelled tile palettes (`docs/design/tiles-3d-models/`): original procedural Blender
  meshes/materials authored by Codex for Mauro. The approved V2 set covers Timber, Stone,
  Fleece, Grain, Clay and Desert: connected ranges, trees, sheep, wheat, kiln, terraces,
  dunes and thick hex bases. Sources: `tools/blender/render_models.py` and
  `tools/blender/render_v2_set.py` and the density pass `tools/blender/render_density.py`.
  Tiny goats, lush pasture/wall/gate, dense wheat, cut clay terraces/racks/shed,
  relief dunes/ripples/bleached remains and snow caps are original procedural meshes.
  Sheep-and-goat sprite phases are original Blender renders;
  bird, mist, wheat wind, smoke and heat overlays are original procedural code in
  `tools/blender/animate_v2.py`. Palette/mock composition: `compose_palette.py` and
  `compose_v2_set.py` in the same directory. No downloaded models, third-party textures
  or generated-image assets. Palette only; not installed in the app.
- Selected Stone R3 Alpine chain and seven-peak follow-up (`stone-alpine/`): own
  procedural geometry in `tools/blender/alpine_chain.py`, derived from our R3 study.
  Shared ridge/shoulder terrain, snow, scree and V2 pines; warm density-pass materials.
  Original mist/bird overlay code and `compose_alpine.py` phone/geometry proofs.
- Bandit options (`docs/design/bandit/`): original procedural Blender cloaked figures,
  lantern and beacon-ring models authored by Codex for Mauro in `tools/blender/bandit.py`.
  Density round reuses the completed own renders/sprites; new tile/phone proofs are
  composed by `tools/blender/compose_density.py`. No downloaded models or textures.
- Tile relief faces (`relief_*.webp`) and comparison palette (`docs/design/tiles-3d/`):
  authored by Codex for Mauro from Mauro's existing owned tile paintings. The original
  procedural color-height brush relief bake is reproducible with `tools/art/tiles-3d.cjs`.
  Tile side geometry, bevels and terrain life accents are original procedural drawing
  in `TileRelief.kt` / `TileLife.kt`; no third-party art was added.
- Old brass on the lobby and game-start screens (`app/src/main/res/drawable-nodpi/old_brass.jpg`, the
  1K color map scaled to 512 px): "Metal042B" from ambientCG
  (https://ambientcg.com/view?id=Metal042B), CC0 1.0 Universal (public domain).
- Illustrated resource cards (`app/src/main/res/drawable-nodpi/card_*.webp`): made by Mauro
  DiBenedetto with ChatGPT image generation, retitled with our names (Timber, Clay, Fleece,
  Grain, Stone) using Georgia Bold.
- Dice (`res/drawable-nodpi/dice_*.png`): rendered by tools/art/render_dice.py from dice models made by Mauro
  DiBenedetto with ChatGPT (dice_ship_models_v2.zip).

## Fonts

- Alegreya SC and Alegreya Sans (`app/src/main/res/font/alegreya_*.ttf`), by Huerta Tipográfica
  (https://github.com/huertatipografica/Alegreya, .../Alegreya-Sans), SIL Open Font License 1.1.
  Titles and text on the game-start and lobby screens. License texts: `docs/licenses/OFL-Alegreya*.txt`.

## Libraries

- ZXing core (https://github.com/zxing/zxing), Apache License 2.0: draws the lobby's invite QR code.
- Painted ocean tile (`res/drawable-nodpi/ocean_tile.webp`): made by Mauro DiBenedetto with ChatGPT
  image generation (seamless top-down ocean texture).
- Settings gear icon (`res/drawable-nodpi/settings_gear*.webp`): made by Mauro DiBenedetto with ChatGPT.
- Painted hex tiles (`res/drawable-nodpi/tile_*.webp`): made by Mauro DiBenedetto with ChatGPT, normalised to a
  standard pointy-top hex.
- Reset arrow icon (`res/drawable-nodpi/reset_arrow*.webp`): made by Mauro DiBenedetto with ChatGPT.
- Cliff strip (`res/drawable-nodpi/cliff_straight.webp`): made by Mauro DiBenedetto with ChatGPT; fringe cleaned.
- Knight tokens, Pirate themed (`knight_pirate_*`): made by Mauro DiBenedetto with ChatGPT.
- Knight tokens, Normal (`knight_normal_*`): generated with OpenAI Codex image generation for Mauro, matched to the pirate set.
- Raiders ship (`raiders_ship.webp`) and knight cards (`card_knight_*`): generated with OpenAI Codex image generation for Mauro.
- Gulls and player sloops (`gull_*`, `boat_*`): generated with OpenAI Codex image generation for Mauro (via Pip).
- Frameless tiles and top-down cliff strips (`tile_*`, `cliff_top_*`): generated with OpenAI Codex for Mauro (via Pip).
- Brass button plates and road brick texture (`btn_*`, `road_bricks.png`): generated with OpenAI Codex for Mauro.
- Hand-made dice, Normal and Pirate sets (`dice_n*`, `dice_p*`): generated with OpenAI Codex for Mauro (via mauro-7a).

- V2 number-clearing revision (`docs/design/tiles-3d-models/`): own procedural Blender geometry, terrain clearings, plate-proof artwork and exclusion masks created for Mauro with Codex. Sources: `tools/blender/token_space.py`, `render_density.py`, `alpine_chain.py`, `animate_v2.py`, `compose_token_check.py`. No downloaded models or new third-party assets.

- V2 Fleece alpacas and terracotta Clay revision: own procedural models/materials, including `tools/blender/alpacas.py`; no downloaded animal meshes or textures.

- Bandit V1 skeleton corsair: original procedural Blender model, straw weave/fraying, afro curls, skull, coat and cutlass; source `tools/blender/render_bandit_v1.py`. No downloaded meshes or external character references.

Bandit V1 on-plate revision: own procedural corsair meshes, upturned straw brim and own opaque shadow seal, generated by tools/blender/render_bandit_v1.py and compose_bandit_onplate.py; no external models.

- V3 dense terrain dioramas: own procedural geometry, noise/bump materials, AO lighting and comparison/number-proof sheets, generated with `tools/blender/render_v3.py` and `compose_v3.py`. Art references are Mauro's existing painted `tile_<terrain>.webp` faces, read from the catan-android worktree; no downloaded meshes/textures or new third-party assets.

- V3 second modelling pass: own botanical branch/leaf meshes, fine sheep fleece, wall stones, cedar shakes, bark/end-grain rings, bent multi-grain wheat, fanning sheaves, chimney masonry, mine/yard props, fractured outcrops and world-scale procedural materials in `tools/blender/v3_art.py` and `render_v3.py`. No downloaded assets or projected painted-face textures.

- V3 continuation: own logging sawbuck/tools, flowering margins, mineral joints/strata, refined erosion geometry, volumetric kiln smoke and grouped thorn scrub. The intermediate and final palettes are assembled from our Blender renders and Mauro's existing painted references; no external assets were added.

- V3 completion: own broad conifer fans, hauling sled, mineral slabs, pasture gate/hayrack, enlarged sheaves, drying-brick yard, sandstone crowns and clustered foliage in `tools/blender/v3_story.py`, with the shared procedural pigment shader in `v3_finish.py`. Painted reference faces are comparison material only.
