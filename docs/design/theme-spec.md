# Theme spec

What a theme for the Working German Board Game must and may contain (Mauro, 2026-10-11). A theme swaps the
names and art; the rules stay the same. Geometry, file sizes and tilt steps are in [art-spec.md](art-spec.md): read
it with this page.

## Required

1. **Names** for everything a player reads:
   - the 5 resources and the 3 commodities;
   - homestead, town, road, knight (3 levels), Ramparts, the 3 Capitals, the Bandit, the Raiders;
   - the 3 progress decks and their cards, and the Fortune cards (base game).
2. **Board art**: 6 tiles (5 terrains and the desert), coast and cliffs, the sea texture, and ports showing each
   resource.
3. **Pieces**: road, homestead, town, knight levels 1 to 3 (an active knight raises its banner; see **Knight activation** in art-spec.md), Ramparts at 3 heights,
   and the 3 Capitals. Each is split into a body tinted in the player's colour and an untinted trim.
4. **The Bandit, the Raiders' ship, and the Merchant indicator** (Age of Chivalry: who holds the Merchant and on which
   tile; a coin-like token in the holder's colour with the theme's trade symbol on it).
5. **Cards**: the 5 resources and the 3 commodities (commodities framed differently from resources), a background
   per progress deck tied to its commodity, and the Fortune cards.
6. **Dice**: the two number dice and the event die faces.

## Recommended sizes

How big each object ends up on the board, in **hex units** (1 = a tile hex's side; a tile is 2 tall and 1.732
wide). Make a theme's objects at these sizes so they sit with the shipped ones; "frame" is the square image or model
bounding box, "body" the visible object inside it. These are the sizes the game draws today (scale factors already
applied); the art spec gives the base frames and file sizes they come from.

| Object | Recommended size on the board | Where it sits |
|---|---|---|
| Tile | 1 hex, edge to edge | Number clearing kept plain: radius 0.31 at (+0.36, +0.30) |
| Number plate | radius 0.27 | Lower right of the tile, (+0.36, +0.30) from its centre |
| Homestead | body about **0.45 wide × 0.27 deep** (frame 2.4) | On a corner, door to the viewer |
| Town | body about **0.81 × 0.45**, about twice a homestead's mass | On a corner |
| Road | **0.53 × 0.15**, two pieces per edge, centred ±0.20 from the edge's middle | Along an edge |
| Ramparts (Age of Chivalry) | ring about **0.64** across; the town on top at 0.88 of its size | Under a town or Capital |
| Capital (Age of Chivalry) | a town plus a central keep, about **0.85** across, dome top highest | Replaces a town |
| Knight (Age of Chivalry) | frame **1.30** on the flat board, **0.64** on the 3D board (0.49 of flat) | Stands on a corner, base at the lower centre |
| Bandit | frame **2.65** | Feet on the number plate; its base hides the plate |
| Merchant (Age of Chivalry) | radius **0.20**, ring in the holder's colour | Lies flat on its tile at (−0.36, +0.30), lower LEFT, mirroring the number plate, so it never covers the number |
| Port buoy | radius about **0.39** | 0.80 out to sea from the middle of its coast edge |
| Cliff strip | **1.16** along a coast edge × **0.55** deep | Along each coast edge |
| Boat (sloop) | frame **1.08**, hull about 0.76 long | On the sea |
| Raiders' ship | frame **1.40**, hull about 1.0 long | On the sea, larger than any boat |
| Gull | frame **0.32**, wingspan about 0.25 | Over the sea |
| Whale (optional) | frame up to **5.3**, body well inside it | On the open sea, clear of the coast |

Rules of thumb: a piece's footprint stays inside the corner's share of the tiles around it (no wider than about 0.85),
nothing on a tile reaches the number plate, and sea life and boats stay smaller than a tile (the whale aside).

## Optional

Critters: boats, birds, a whale or other sea life, clouds, each a module with a model and a movement spec (see [critter-spec.md](critter-spec.md)). A music set list and sounds (dice, Raiders), an intro fly-over.

## Rules every theme passes

- **The number is always readable.** The plate sits in the **lower third** of its tile, on a plain clearing. At a 45°
  tilt nothing covers it: not the tile's own features (peaks, trees, buildings), and not the tile in front. Tall
  things go in the upper part of the tile and to the sides. 3D models are turned in Blender to meet this, never
  flipped as images, and checked at every tilt step before they ship.
- **Pieces stand out.** Every piece in all four player colours reads clearly on every tile. The game adds
  saturation and brightness to pieces and takes a little off tiles, so the art should not fight that.
- **Sizes and formats** follow the art spec: 8 tilt steps for everything on the board, 16 headings for anything
  that sails or flies, the frame sizes and contact points given there.
- **Own names only.** Never the original game's names, in any language.
- **Licences.** Art is CC0 or our own, and every source is listed in `CREDITS.md`.
- **Costs stay the same.** A theme renames; the recipes (a road is one of the first resource and one of the second,
  and so on) are unchanged unless the theme declares its own cost table through issue #79.

## Themes

| Theme | Status | Names |
|---|---|---|
| Default | shipped | Timber, Clay, Fleece, Grain, Stone; Parchment, Fabric, Silver |
| Pirate | shipped (art only) | default names |
| Edo fishing village (#76) | planned | Bamboo, Kawara, Silk, Rice, Platinum (or Copper); commodities proposed Washi, Brocade, Koban; Knights are Samurai. Other piece names proposed on #76 |
