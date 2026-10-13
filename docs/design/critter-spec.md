# Critter spec

Critters are everything on the board that moves on its own and isn't a game piece: boats, gulls, the Raiders' ship
underway, the whale, fish, clouds, and in a space theme, space whales and drifting rocks (Mauro, 2026-10-12: "all the
critters can be added by giving a model and spec for movement and altitude and animations"). A critter is a module
inside a theme ([theme-spec.md](theme-spec.md)). Adding one means dropping in a model and one spec file, with no new
game code. The app runs every critter with the same engine on both boards.

## What a critter module contains

```
themes/<theme>/critters/<id>/
  critter.json       the spec below
  <id>.glb           the model with its animations (3D board)
  frames/            flat-board frames, rendered from the same .glb by the art pipeline
```

## critter.json

| Field | Meaning | Example (whale) |
|---|---|---|
| `id`, `name` | file id, and the name a player reads (gallery, settings) | `whale`, "Whale" |
| `model` | the .glb: 1 unit = a hex side; origin at the **contact point** (waterline centre for things on the water, centre of mass for things that fly or swim); facing +X | `whale.glb` |
| `length` | body length in hex units | `2.4` |
| `layer` | which band it lives in (see Layers) | `surface` |
| `altitude` | `[min, max]` height in hex units, relative to sea level (negative = below) | `[-0.6, 0]` |
| `zone` | where it may go: `open-sea`, `coast`, `over-land`, `anywhere`, or `off-board` (the ring outside the sea), and `clear_of_coast` in hex units | `open-sea`, `1.0` |
| `movement` | limits the behaviour must stay inside: top `speed` (hex/s), `turn` (deg/s), `bank` (deg into turns) | `0.15`, `20`, `0` |
| `animations` | each clip in the .glb: `name`, `seconds`, `loop` or `once` | `swim` 4 s loop, `beat` 2 s, `blow` 1.5 s, `dive` 3 s, `surface` 3 s |
| `behavior` | the **modelled behaviour algorithm** (next section): stages with time shares, each with its motion as Δx/Δy/Δz and a pointer to its animation | see below |
| `count` | `[min, max]` on the board at once, and `chance` per game | `[0, 1]`, `0.6` |
| `shadow` | drop a soft shadow on what's below it | `false` |
| `sounds` | optional clip names keyed by animation | `blow: "whale_blow.ogg"` |
| `frames` | flat-board render settings: `headings` (16, or 8 for big slow critters), `tilts` 8, `size` px, `poses` (which clips are sampled, and how many frames each) | `8`, `8`, `288`, d25/d50/d75, beat ×8, blow ×3 |

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

`altitude` places a critter inside its band. A critter can cross bands only through a clip, such as `dive`
(surface to underwater) or `surface` (underwater to surface).

## Rules

- Nothing covers a number plate for longer than a passing moment, and nothing settles over a piece.
- A theme can ship no critters. The default theme's are the boats, gulls, Raiders and whale.
- Sizes for the shipped critters stay as in [theme-spec.md](theme-spec.md) (boat frame 1.08, gull 0.32, whale up to
  5.3).
- The flat-board frames and the 3D model come from the same Blender file, so both boards show the same critter.

## Themes

- **Default:** sloop, Raiders' ship, gull, whale.
- **Space (#88 and the space opera of #42):** space whales in `under` and `sky`, drifting rocks, small shuttles in
  place of boats.
- **Edo fishing village (#76):** fishing boats, cranes, koi under the shallows.
