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
| `movement` | `speed` (hex/s), `turn` (deg/s), `path`: `wander`, `lane`, `circle-island`, `follow:<id>` or `drift`; `bank` (deg into turns) | `0.15`, `20`, `wander`, `0` |
| `animations` | each clip in the .glb: `name`, `seconds`, `loop` or `once` | `swim` 4 s loop, `beat` 2 s, `blow` 1.5 s, `dive` 3 s, `surface` 3 s |
| `cycle` | the behaviour loop: clips and holds in order, each with a time range | surface, swim 8–20 s, blow ×1–3, beat, dive, hidden 30–90 s |
| `count` | `[min, max]` on the board at once, and `chance` per game | `[0, 1]`, `0.6` |
| `shadow` | drop a soft shadow on what's below it | `false` |
| `sounds` | optional clip names keyed by animation | `blow: "whale_blow.ogg"` |
| `frames` | flat-board render settings: `headings` (16, or 8 for big slow critters), `tilts` 8, `size` px, `poses` (which clips are sampled, and how many frames each) | `8`, `8`, `288`, d25/d50/d75, beat ×8, blow ×3 |

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
