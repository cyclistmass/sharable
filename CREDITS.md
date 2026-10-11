# Credits

Working German Board Game is GPL-3.0 (see `LICENSE`). Everything below is either ours (made by or for
Mauro DiBenedetto) or used under the licence named. Art files are in `app/src/main/res/drawable-nodpi/`
unless another path is given.

## Art: textures (ambientCG, CC0)

- Wood grain on the player pieces, tokens and panels (`wood_grain.png`): "Wood049" from ambientCG
  (https://ambientcg.com/view?id=Wood049), CC0 1.0 Universal (public domain). Converted to grayscale and
  stained per player at runtime.
- Old brass on panel edges and buttons (`old_brass.jpg`, the 1K colour map scaled to 512 px): "Metal042B"
  from ambientCG (https://ambientcg.com/view?id=Metal042B), CC0 1.0 Universal (public domain).

## Art: made by Mauro DiBenedetto with ChatGPT image generation

- Illustrated resource cards (`card_timber`, `card_clay`, `card_fleece`, `card_grain`, `card_stone`),
  retitled with our names using Georgia Bold.
- Painted ocean tile (`ocean_tile.webp`): seamless top-down ocean texture.
- Painted hex tiles (`tile_*.webp`, framed and unframed), normalised to a standard pointy-top hex.
- Settings gear and reset arrow icons (`settings_gear*`, `reset_arrow*`).
- Knight tokens, Pirate themed (`knight_pirate_*`).

## Art: generated with OpenAI Codex for Mauro DiBenedetto

- Knight tokens, Normal (`knight_normal_*`), matched to the pirate set.
- Raiders ship (`raiders_ship.webp`) and knight cards (`card_knight_*`).
- Gulls and player sloops (`gull_*`, `boat_*`); the 16-frame wingbeat `gull_flap.png` (#45).
- Top-down cliff strips (`cliff_top_*`).
- Brass button plates and road brick texture (`btn_*`, `road_bricks.png`).
- Dice, Normal and Pirate sets, faces and tumbling frames (`dice_n*`, `dice_p*`, `dice_tumble_*`), with the
  Antique and Pirate event dice. The pirate event die style options (`dice_pe1_*` Brass-engraved,
  `dice_pe2_*` Ship-timber, `dice_pe3_*` Brass cage, and their tumbles) are from the same source, imported at
  −25% saturation by `tools/import_event_dice.py`.
- Rendered buildings (`bld_*`, #41): homestead, town, roads in three directions, Ramparts and three Capitals, in a Default and a Pirate set, each a tintable wood layer plus a brass/stone trim layer in 8 tilt steps; own Blender models and procedural materials, imported by `tools/import_buildings.py`.
- Intro fly-over (`res/raw/intro_flyover.mp4`): the gull riding beside a sloop and over the island, rendered by OpenAI Codex for Mauro (#54).
- App icon (`ic_launcher*`): our gull over deep sea in an old-brass roundel (option A, Mauro's pick 2026-10-10), rendered by OpenAI Codex for Mauro.
- Bandit V2 "skeleton corsair" (`bandit_tilt_0..7`): the painted-miniature finish with an aged ivory skull, bigger afro and upturned brim, a 3D model rendered in Blender, 8 tilt steps (Mauro's pick, 2026-10-10).
- Tile candidates for Menu › Tiles: compare (`tile3d_v2_*`, `tile_a1_relief_*`): 3D renders and relief faces,
  for picking only, not on the board yet.
- 3D knights (`kn_*`): Normal armoured knights and the Pirate skeleton crew, levels 1-3, original Blender models built
  by OpenAI Codex and rendered on Mauro's PC for Mauro (Mauro's pick, 2026-10-11); a tintable body plus trim layer in
  8 tilt steps, imported by `tools/import_knights.py`.
- V3 3D tiles (`t3d_*`), Settings › Tiles › 3D (preview): original Blender models built by OpenAI Codex and rendered on
  Mauro's PC, 8 tilt steps, imported by `tools/import_tiles3d.py`. The Pirate buildings' flat view was re-rendered
  (it was turned 180 degrees).

## Fonts

- Alegreya SC and Alegreya Sans (`app/src/main/res/font/alegreya_*.ttf`), by Huerta Tipográfica
  (https://github.com/huertatipografica/Alegreya, https://github.com/huertatipografica/Alegreya-Sans),
  SIL Open Font License 1.1. Licence texts: `docs/licenses/OFL-Alegreya*.txt`.

## Sound

- Trade chime (`app/src/main/res/raw/trade_chime.wav`): our own, synthesised in Python (two decaying bell
  tones, E6 and B6), 2026-10-09.
- Carnival of Venice (`app/src/main/res/raw/tune_carnival_of_venice.ogg`): public-domain melody (after James
  Kerr, "Merry Melodies"); our own arrangement and synthesis via `tools/make_sounds.py`.

## Libraries

- Kotlin standard library (JetBrains), Apache License 2.0.
- Jetpack Compose, AndroidX Activity, AndroidX ProfileInstaller and AndroidX Test (Google / Android Open
  Source Project), Apache License 2.0.
- Firebase Android SDK: Authentication and Cloud Firestore (Google), Apache License 2.0.
- ZXing core (https://github.com/zxing/zxing), Apache License 2.0: draws the lobby's invite QR code.
- JUnit 5 (https://junit.org), Eclipse Public License 2.0: tests only, not shipped in the app.
- Gradle wrapper (`gradle/wrapper/`), Apache License 2.0: build tooling.
- Firestore rules tests (`firebase/rules-test/`) use `@firebase/rules-unit-testing` and the Firebase JS SDK
  via npm (Apache License 2.0) with Node's built-in test runner; not shipped in the app.

## References

- Catanatron (https://github.com/bcollazo/catanatron), GPL-3.0: we cross-check our rules against it and map
  its actions to ours (`engine/CATANATRON_MAPPING.md`) so bots trained there can play here. No Catanatron
  code is copied into this project.
- Game rules are compatible with the classic German settlement-building game and its knights expansion.
  Not affiliated with or endorsed by Catan GmbH; all names, card text and art here are our own.
