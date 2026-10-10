# Tile and art spec: making parts that fit

Basic rules so tiles, pieces and other art from any artist or AI drop into the game without rework.
The code that relies on these numbers is `NUMBER_TOKEN_*` and `drawTile` in `app/.../MainActivity.kt`;
`TileSpecTest` checks them.

## Terrain tiles

| Rule | Value |
|---|---|
| Terrains | Timber (forest), Clay (hills), Fleece (pasture), Grain (fields), Stone (mountains), Desert |
| Shape | Pointy-top hexagon, side = 1 unit; art fills the hex to its edges (visible inset radius 0.93) |
| File | 512 × 512 px transparent PNG or WebP, hex centred; top-down view, or the same orthographic camera for every tile |
| Light | Sun from the upper left on every tile, so neighbours match |
| Files | `tile_<terrain>.webp` (lower-case terrain: `tile_timber`, `tile_clay`, ...) in `app/src/main/res/drawable-nodpi/` |

### Number plate: lower right, always readable

- The round number plate sits in the **lower right** of the tile: centre **(+0.36, +0.30)** hex units from the
  tile centre (x right, y down), radius **0.27**.
- Keep a **flat, plain clearing of radius 0.31** at that spot on every tile, desert included: ground only
  (glade, grass, stubble, rock shelf, clay yard, sand). No props, trees, buildings or animals in it.
- **Tilt rule:** the board can tilt up to 55°, so anything drawn *below* (in front of) the plate would cover
  it. Keep the lower edge of the tile low: grass, paths, low walls, small animals. Put tall things (trees,
  peaks, kilns, mine towers, haystacks) in the upper half and the left side, behind or beside the plate.
- The Bandit stands on the plate and hides it; no extra space is needed for him.

### Look

- Rich and detailed to the edges: forest greens, golden grain, red-orange Clay (clearly redder than the
  Desert's ochre), warm rock with snow for Stone, ochre sand for Desert.
- Fleece is mostly sheep; a few small alpacas and goats are welcome. Keep animals small next to the plate.
- No text, numbers or borders painted into tile art (the game draws the plate and the optional frame).

## Pieces, tokens and other sprites

- Transparent PNG or WebP, the object centred, a little padding all round, same upper-left light.
- Players' colours are applied by the game (wood pieces are stained at runtime): draw pieces neutral unless
  the spec for that piece says otherwise.
- Dice: faces cropped to the die body at 192 px; tumbling frames 256 px, 10 frames that loop as one spin.
- Animated strips (for example `gull_flap`): square frames side by side in one row; the frame count is
  width ÷ height.

## Names, licences and checking

- Use our names only (see `AGENTS.md`): no names, logos or card text from the original game.
- Art must be our own or CC0. Add every new file and its source to `CREDITS.md`.
- Put new art on the in-app **Inventory** screen and check it on the board flat and fully tilted before it
  counts as done.
