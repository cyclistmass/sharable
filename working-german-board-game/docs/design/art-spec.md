# Art spec: style, sizes and rules for every object

Read this when you make or change art. It says how the game looks and how big every object is, so parts
from any artist or AI drop in without rework. Sizes are measured from the shipped files and the drawing code
(`MainActivity.kt`, `Sea.kt`, `WoodPieces.kt`, `Hud.kt`); `TileSpecTest` checks the number plate rules.

**Units:** board sizes are in **hex units**: 1 = the side of a tile hex (a pointy-top hex is 2 tall and
1.732 wide). On screen a hex unit is about 60–200 px depending on zoom. UI sizes are in dp.

## House style: rivetless brass, steampunk master craftsman

- A tabletop built by a master craftsman: **dark, warm-stained wood** and **smooth old brass**, hand-finished.
- **Brass is rivetless:** seamless cast or hand-beaten brass with soft polished highlights, fine engraved
  lines, bevelled or rolled edges and inlay. **No rivets, bolts, screw heads or plate seams.** Steampunk in
  craft and materials (gears, filigree, engraved scrollwork, lenses where they fit), never in industrial
  hardware.
- Wood: rich grain, hand-rubbed finish, crisp joinery; colour like `wood_grain.png` stained dark (#4A3020).
- Painted art (tiles, cards) is detailed and warm, like a premium illustrated board game.
- Light comes from the **upper left** in every picture; shadows fall to the lower right.
- No text, numbers or borders painted into art: the game draws numbers, labels and frames.
- Our names only (see `AGENTS.md`); art must be our own or CC0, recorded in `CREDITS.md`.

## Board objects

| Object | Files | File size (px) | On the board | Anchor and notes |
|---|---|---|---|---|
| Terrain tile, painted | `tile_<terrain>.webp`, `_framed` variant | **443 × 512**, the exact box around a pointy-top hex (side 256 px) | 1 hex, edge to edge | Hex fills the file; terrain keys timber, clay, fleece, grain, stone, desert |
| Terrain tile, 3D render | `tile3d_*_{flat,mid,max}` | 512 × 512, orthographic scale 2.65 | 1 hex | Hex centred; one render per tilt step |
| Number plate | drawn by the game | — | radius **0.27**, centre **(+0.36, +0.30)**: **lower right** | Keep a plain clearing of radius 0.31 there; see "Tilt rule" |
| Bandit | `bandit_tilt_0..7` | 384 × 384 (512 at source) | **2.65** hex units square, standing on the plate | Foot anchor at the image centre; opaque base seal radius 0.278 hides the plate; 8 tilt steps, tilt = 0.96·i/7 |
| Homestead / town / road | drawn by the game (`WoodPieces.kt`) | — | homestead about 0.5 hex units wide | Stained in the player's colour at runtime |
| Knight token (Age of Chivalry) | `knight_{normal,pirate}_l{1,2,3}_{active,inactive}` | 256 × 256 | **0.62** hex units, on a ring in the owner's colour | Figure stands on the corner; base at the lower centre |
| Port token | drawn by the game | — | radius 0.30 | 2:1 tokens show their terrain art |
| Cliff strip | `cliff_top_1..3` | 768 × 174 | 1.16 along a coast edge × 0.55 deep | Top-down; grass lip at the top edge, surf at the bottom |
| Ocean | `ocean_tile.webp` | 768 × 768 | tiled under the board | Seamless on all four sides |
| Boat (sloop) | `boat_<colour>` | 192 × 192 | **0.95** hex units | Top-down, bow pointing right; sail in a player colour |
| Boat wake | `boat_wake` | 192 × 96 | 0.7 × 0.3 of the boat | Faint, drawn behind the stern |
| Raiders ship | `raiders_ship` | 256 × 256 | **1.15** hex units | Top-down, bow pointing right |
| Gull | `gull_glide`, `gull_flap`, `gull_shadow` | 128 × 128 per frame | **0.6** hex units (bird about 45% of its frame) | Top-down, head pointing right; `gull_flap` is a strip of square frames, count = width ÷ height |

### Tilt rule (tiles)

The board tilts up to 55°, so anything in front of (below) the number plate can cover it. Keep the lower part
of each tile low: grass, paths, low walls, small animals. Tall things (trees, peaks, kilns, mine towers,
stooks) go in the upper half and the left side. The plate's clearing (radius 0.31 at +0.36, +0.30) stays plain
ground on every tile, desert included.

## Cards, dice and UI

| Object | Files | File size (px) | Shown at | Notes |
|---|---|---|---|---|
| Resource card | `card_<resource>` | about **278 × 390** (portrait, 5:7) | 50 × 70 dp in hand, 76 × 110 dp in Inventory, 21 × 30 dp minis | Title banner with our name at the top, resource icon at the bottom |
| Knight card | `card_knight_{normal,pirate}_1..3` | 256 × 360 | 76 × 110 dp | Same 5:7 portrait shape |
| Die face | `dice_<set>_1..6` | **192 × 192**, cropped to the die body | 36 dp on the turn panel | Sets: nr/ni Normal red/ivory, pr/pb Pirate, ae/pe event dice, pe1–pe3 event styles |
| Die tumble | `dice_tumble_<set>_1..10` | **256 × 256** | thrown across the board | 10 frames that loop as one spin |
| Button plate | `btn_{brass,gold,go,stop}` + `_pressed` | 384 × 128 (3:1) | stretched to the button | Rivetless brass; go = green, stop = red, gold = primary |
| Icons | `settings_gear`, `reset_arrow` + `_pressed` | 256 × 256 / 192 × 192 | about 40 dp | Brass, centred, transparent |
| Panels | `wood_grain.png` + `old_brass.jpg` via `brassEdgedWood()` | textures | any | Never plain grey or black boxes |

## Files and checking

- Transparent PNG or WebP (lossless or quality 90), object centred with a little padding, in
  `app/src/main/res/drawable-nodpi/`, lower-case names with the patterns above.
- Animated sprites: frames side by side in one row, square, same anchor in every frame.
- Before art counts as done: show it on the in-app **Inventory** screen and check it on the board flat and
  fully tilted (the Tilt bar on the right edge), at full view and zoomed in.
