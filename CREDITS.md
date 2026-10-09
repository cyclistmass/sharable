# Credits

## Art

- Wood grain on the player pieces and tokens (`app/src/main/res/drawable-nodpi/wood_grain.png`):
  "Wood049" from ambientCG (https://ambientcg.com/view?id=Wood049), CC0 1.0 Universal (public domain).
  Converted to grayscale and stained per player at runtime.

All other graphics are drawn procedurally in code.
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
