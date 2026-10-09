# Credits

## Art

- Wood grain on the player pieces and tokens (`app/src/main/res/drawable-nodpi/wood_grain.png`):
  "Wood049" from ambientCG (https://ambientcg.com/view?id=Wood049), CC0 1.0 Universal (public domain).
  Converted to grayscale and stained per player at runtime.

All other graphics are drawn procedurally in code.
- Modelled Timber and Stone palette (`docs/design/tiles-3d-models/`): original procedural
  Blender meshes and materials authored by Codex for Mauro. Trees, crags, thick hex bases,
  logs and stones are built from scratch in `tools/blender/render_models.py`; contact sheet
  and sprite neighbour mocks in `tools/blender/compose_palette.py`. No downloaded models,
  third-party textures or generated-image assets. Palette only; not installed in the app.
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
