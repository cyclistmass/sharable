# Critter spec

Critters are everything on the board that moves on its own and isn't a game piece: boats, gulls, the Raiders' ship
underway, the whale, fish, clouds, and in a space theme, space whales and drifting rocks (Mauro, 2026-10-12: "all the
critters can be added by giving a model and spec for movement and altitude and animations"). A critter is a module
inside a theme ([theme-spec.md](theme-spec.md)). Adding one means dropping in a model and one spec file, with no new
game code. The app runs every critter with the same engine on both boards.

## What a critter module contains

```
app/src/main/assets/critters/<id>/        (themes/<theme>/critters/<id>/ once themes ship, #88)
  critter.json                   the spec below; its id is the folder's name
  <id>.glb                       the model with its animations (3D board; optional until the 3D board runs critters)
  frames/<pose>_h<H>_t<T>.webp   flat-board frames, rendered from the same .glb by the art pipeline
```

The app loads every folder under `critters/` that holds a `critter.json` (`CritterModules.load`, #130). A module that
fails its checks is logged under `WGBG-CRITTER` and left out; the game never crashes on one. `CritterTest` runs the same
checks on every shipped module, so a broken drop-in fails the build.

**Frames.** `H` is the heading (0 = east on screen, counter-clockwise, `frames.headings` steps) and `T` the tilt step
(0 = straight down, `frames.tilts` steps over tilt 0 to 0.96, as for the buildings). Square, `frames.size` px, with the
contact point at the centre. A pose is a frame name:

- `d50` is a held pose (the whale at 50% surfaced);
- `d50_beat00` … `d50_beat07` animate clip `beat` at pose `d50`, numbered from `00`, spread evenly over the clip.

An `anim` of `clip:pose` plays `<pose>_<clip>NN` if that run exists, else holds `<pose>`; a bare `clip` plays `<clip>NN`
or holds `<clip>`. A clip with no frames of its own can name a stand-in (`frames.stand_in`). The checks: every pose has
all headings × tilts, and every stage's `anim` resolves to frames.

## critter.json

| Field | Meaning | Example (whale) |
|---|---|---|
| `id`, `name` | file id, and the name a player reads (gallery, settings) | `whale`, "Whale" |
| `model` | the .glb: 1 unit = a hex side; origin at the **contact point** (waterline centre for things on the water, centre of mass for things that fly or swim); facing +X | `whale.glb` |
| `length` | body length in hex units | `2.4` |
| `layer` | which band it lives in (see Layers), or `auto` to take the band from its height at each moment | `surface` |
| `altitude` | `[min, max]` height in hex units, relative to sea level (negative = below) | `[-0.6, 0]` |
| `zone` | where it may go: `open-sea`, `coast`, `over-land`, `anywhere` (the default), or `off-board` (the ring outside the sea), and `clear_of_coast` in hex units | `open-sea`, `1.0` |
| `board_contact` | how it meets the board where its path crosses land: `avoid` (the default), `pass-through` or `hold` (see Board contact) | `avoid` |
| `anchor` | optional: keeps it in one place, `{"at": "board-centre"` or `[x, y]`, `"heading": deg}`, with the `anchored` pattern | the space turtle |
| `movement` | limits the behaviour must stay inside: top `speed` (hex/s), `turn` (deg/s), `bank` (deg into turns) | `0.15`, `20`, `0` |
| `animations` | each clip in the .glb: `name`, `seconds`, `loop` or `once` | `swim` 4 s loop, `beat` 2 s, `blow` 1.5 s, `dive` 3 s, `surface` 3 s |
| `behavior` | the **modelled behaviour algorithm** (next section): stages with time shares, each with its motion as Δx/Δy/Δz and a pointer to its animation | see below |
| `count` | `[min, max]` on the board at once, and `chance` per game | `[0, 1]`, `0.6` |
| `shadow` | drop a soft shadow on what's below it | `false` |
| `sounds` | optional clip names keyed by animation | `blow: "whale_blow.ogg"` |
| `frames` | flat-board render settings: `headings` (16, or 8 for big slow critters), `tilts` 8, `size` px, `span` (hex units a frame's side covers on the board), `poses` (which clips the renderer samples, and how many frames each), `stand_in` (frames shown for a clip that has no render yet, spread over it, with `fade` `in` or `out` over its first or last 40%) | `8`, `8`, `288`, `2.16`, d25/d50/d75, beat ×8, blow ×3; `surface`: d25 → d50 fading in |

## Modelled behaviour algorithm

How a critter acts, as data only (Mauro, 2026-10-12: "time % and pointers to the animations at each stage, whether
direction change or dive/surface, all just delta x y z plus what animation for each pos, or the patterns that are
rational for behaviour"). The engine needs no per-critter code: it plays the stages, moves the critter by their deltas
and shows the animation each stage points to.

**Stage.** One step of behaviour:

| Field | Meaning |
|---|---|
| `id` | stage name: `surface`, `cruise`, `turn`, `blow`, `beat`, `dive`, `hidden` |
| `share` | target share of the critter's time spent in this stage, in % (all stages add up to 100) |
| `seconds` | `[min, max]` length of one visit |
| `delta` | motion over the visit as `[dx, dy, dz]` in hex units in the critter's own frame (x forward, y left, z up). `dz` is how it dives or surfaces; a heading change is `turn` in degrees (+ = left), spread over the visit |
| `anim` | the clip it plays (a name from `animations`); for the flat board, the frame pose it maps to |
| `keys` | optional, for anything not straight: `[{t, dx, dy, dz, anim}]` keyframes inside the stage (t from 0 to 1), so a breach, a loop or a zig-zag is just positions plus the animation at each one |
| `next` | where it goes after this stage, with weights: `{"cruise": 3, "blow": 1}` |
| `when` | optional guard: `near-coast`, `far-from-boats`, `raiders-near`, `night`, … |

The engine picks a next stage by weight. Over a game the measured shares converge on `share`, and a stage's delta is
clamped to the `movement` limits and the `zone` (a whale never noses into the coast; it turns away first).

**Patterns.** Ready-made stage sets for behaviour that makes sense for a kind of creature. A critter names one and
overrides only what differs:

| Pattern | For | Stages |
|---|---|---|
| `surface-breather` | whales, dolphins, turtles | surface, cruise, blow, beat, dive, hidden |
| `glider` | gulls, albatross, space whales in `sky` | glide, flap, bank-turn, circle |
| `flock` | small birds, fish shoals | follow the leader with spacing and jitter |
| `sailer` | boats, the Raiders | sail a lane, tack, moor at a harbour |
| `drifter` | clouds, rocks, flotsam | drift with the wind, slow spin |
| `lurker` | things in `under` | slow pass under the board, fade in and out |
| `anchored` | a critter that stays put and only lives: the space turtle holding up the board | rest, look, stir (all with delta 0; clips `idle`, `look`, `stir`) |

**Example (whale, `surface-breather`).** The shares follow Mauro's time budget in sea_props.py: 50% out most of the
time. Depth is part of the pose (d25/d50/d75 = 25/50/75% of the body above the water).

```json
"behavior": { "pattern": "surface-breather", "stages": [
  { "id": "surface", "share": 5,  "seconds": [3, 4],   "delta": [0.3, 0, 0.3],  "anim": "surface", "next": {"cruise": 1} },
  { "id": "cruise",  "share": 40, "seconds": [8, 20],  "delta": [1.6, 0, 0],    "anim": "swim:d50", "next": {"turn": 2, "blow": 2, "beat": 1, "low": 2, "dive": 1} },
  { "id": "turn",    "share": 10, "seconds": [4, 8],   "delta": [0.6, 0, 0], "turn": 60, "anim": "swim:d50", "next": {"cruise": 1, "turn": 1} },
  { "id": "blow",    "share": 10, "seconds": [2, 5],   "delta": [0.1, 0, 0],    "anim": "blow:d50", "next": {"cruise": 2, "blow": 1} },
  { "id": "beat",    "share": 10, "seconds": [2, 2],   "delta": [0.4, 0, 0],    "anim": "beat:d50", "next": {"cruise": 1, "beat": 1, "dive": 1} },
  { "id": "low",     "share": 20, "seconds": [5, 12],  "delta": [0.8, 0, -0.1], "anim": "swim:d25", "next": {"cruise": 1, "low": 1, "dive": 1} },
  { "id": "dive",    "share": 5,  "seconds": [3, 4],   "delta": [0.4, 0, -0.6], "anim": "dive", "next": {"hidden": 1} },
  { "id": "hidden",  "share": 0,  "seconds": [30, 90], "delta": [3, 0, 0],      "anim": null, "next": {"surface": 1} }
] }
```

(`hidden` is off-screen time and doesn't count toward the shares.)

**Rules the engine follows** (worked out by the reference simulator in `wgbg-art/critter-sim`, which runs this whale
for an hour within 1 percentage point of every share):

- A stage with `share` 0 is transit time: it doesn't count toward the shares, and the positive shares add up to 100.
- Every stage with a share must be reachable, and the graph must be able to meet the shares with the given `seconds`.
  Self-edges (`"turn": 1` inside `turn`) let a stage repeat without a forced trip through `cruise`.
- The engine steers toward the shares: at each transition an edge's weight is multiplied by exp(time owed to its stage
  / 4 s). It never jumps along an edge that isn't in `next`.
- `delta` is cumulative motion in the critter's frame, turned by the heading at each step. `keys` positions are
  cumulative too, linear between keys, with t=0 at [0,0,0] and t=1 equal to `delta`.
- `anim` is `clip[:pose]`: the clip in the .glb, plus the flat-board pose. A key without `anim` keeps the last one;
  `null` means invisible. A clip restarts on every stage entry; `loop` wraps and `once` holds its last pose.
- `opacity` (0–1, on a stage or a key) fades a critter in and out, as a `lurker` needs.
- Speed is 3D speed. Motion that the limits or the zone cut off is lost, not made up later; turning away from a
  boundary comes before the stage's own turn.
- `clear_of_coast` is measured from a disk of radius `length` / 2 around the origin to the nearest land hex.
- `count` is rolled once per game with `chance`, from a random stream separate from movement.
- If every `next` guard is false, the critter stays in its current stage until one opens.
- Not settled yet: how `flock`, `sailer` and `drifter` get their leader, lanes and wind; how long "a passing moment"
  over a number plate is; the off-board ring's width. The simulator's spec-gaps.md lists the rest.

## Layers

Bottom to top. The board draws each band in this order, so a critter only needs to name its band.

| Layer | What lives there | Seen how |
|---|---|---|
| `under` | below the whole board: deep-sea shapes, a sunken wreck, space whales gliding under the island in a space theme | through the sea's transparency, and around the board's edge as it tilts |
| `underwater` | fish, a whale's dive, rays | under the sea surface, tinted and blurred by depth |
| `surface` | boats, the Raiders, a surfaced whale, ducks | on the water, with a wake |
| `air` | gulls, flying fish leaps | above the sea and coast, under the pieces' tops |
| `sky` | clouds, high birds, airships, comets | above everything, casting shadows on the board (#24) |

On the flat board the sea is opaque, so the bands draw at these points: `under` and `underwater` over the painted
sea and under the wavelets and glints, `surface` over the waves and under the boats, `air` with the gulls (after the
board), `sky` last, over the clouds. Depth tint and blur for `underwater` come from the frames themselves for now.

`altitude` places a critter inside its band. A critter with a named layer stays in that band whatever its height,
and crosses bands only through a clip, such as `dive` (surface to underwater) or `surface` (underwater to surface).

**`layer: auto`** takes the band from the height, so one critter can fly, land on the water and dive (a tern, a
flying fish, a diving cormorant). The board is 1 hex unit deep and its land tiles rise 0.6 above the sea:

| Height z (hex units) | Band |
|---|---|
| below −1.0 (under the board) | `under` |
| −1.0 to −0.05 | `underwater` |
| −0.05 to +0.05 (sea level is z = 0) | `surface` |
| +0.05 to +2.0 | `air` |
| above +2.0 | `sky` |

## Board contact

Where a critter's path crosses the island, `board_contact` says what happens. The board fills z from −1.0 to 0
under the sea and up to +0.6 where there is land; at sea only the sea floor is in the way, so contact only matters
over land:

- **`avoid`** (default): never inside the board. Short of the coast it climbs over the tiles or sinks under the
  board, whichever is nearer, and holds its position until it is clear. Climbing and moving share one speed, so it
  never goes faster than `movement.speed`. A critter whose `altitude` can never clear the board (top below +0.6 and
  bottom above −1.0) must keep a sea-only `zone` or use `pass-through`; the module check flags it.
- **`pass-through`**: goes straight through, drawn faintly (35%) while inside, as if seen through the board. For
  ghosts, space whales, sonar shapes.
- **`hold`**: lets the board hold it: the engine doesn't stop it entering, and the critter's own stages decide what
  it does there.

**Board edges and surfaces are places too, not only obstacles.** A critter may perch on the board's rim, land on a
tile top, sit on a cliff, crawl up the board's side, or hold the whole board up. The space turtle is the example: it
is anchored at the centre, under the board (z about −1.6), with the board resting on its shell, and its stages only
play `idle`, `look` and `stir`. The engine has the anchor and the `anchored` pattern now. Perching and landing come
next: a stage guard such as `on-rim` or `on-tile`, and a key with a target (`rim`, `tile-top`, `cliff`) in place of
a delta. They are not built yet. Until then, a stage that ends on a surface does it with `hold` and its own deltas.

**Anchored critters.** `anchor` fixes the position (the game's lattice, y down; `board-centre` is 0, 0) and the
heading. Its stages must not move it: every `delta` is 0, there is no `turn` and there are no `keys`. Its life is all
animation, the way a tile or a token can idle on the 3D board (see theme-spec.md).

## Rules

- Nothing covers a number plate for longer than a passing moment, and nothing settles over a piece.
- A theme can ship no critters. The default theme's are the boats, gulls, Raiders and whale.
- Sizes for the shipped critters stay as in [theme-spec.md](theme-spec.md) (boat frame 1.08, gull 0.32, whale up to
  5.3).
- The flat-board frames and the 3D model come from the same Blender file, so both boards show the same critter.

## Themes

- **Default:** sloop, Raiders' ship, gull, whale.
  - The gull (in code, `Seabird.kt`, not a module yet) meanders in random half-circles 0.4–1.0 hex across every
    6–18 s.
  - It rises while it flaps and sinks while it glides. It is drawn larger and its shadow falls further away the
    higher it flies.
  - It crosses the board every 25–75 s.
  - Everything on the flat board's sea (gulls, boats, critters) scales with the board's perspective at its row, so
    nearer things are bigger.
- **Space (#88 and the space opera of #42):** space whales in `under` and `sky`, drifting rocks, small shuttles in
  place of boats, and a space turtle (`anchored`) holding up the board in the 3D board's space theme.
- **Edo fishing village (#76):** fishing boats, cranes, koi under the shallows.
