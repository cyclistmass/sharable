# Tile depth and life delivery

A1 is the default: continuous extrusion, upper-left directional edge lighting,
thin bevel, and owned brush-relief faces. A2 bakes/caches three raster angles
(0°, 35°, 60°), choosing the nearest. Both were prototyped side by side for all
six terrains. A2 adds memory and visible snapping without improving these paintings
at the sampled angles, so A1 is recommended. A true height mesh was considered
but would require authored height maps and a separate renderer; the current
color-derived height proxy adds restrained texture, not reconstructed terrain geometry.

## Review artifacts

- `docs/design/tiles-3d-palette.html`: self-contained local page, relative owned images;
  six terrain rows, two options, three angles, B1 eight-second life loops and pause.
- `docs/design/tiles-3d/palette-all.png`: full desktop palette capture.
- `docs/design/tiles-3d/palette-0.png` through `palette-5.png`: individual terrain captures.
- `docs/design/tiles-3d-mainactivity.patch`: exact MainActivity diff for Bosun.
- `docs/design/tiles-3d-test-plan.md`: written validation plan.

Regenerate with Node, sharp and Playwright installed outside the repository;
set `TILES_NODE_MODULES` to their node_modules directory, then run
`node tools/art/tiles-3d.cjs` and `node tools/art/screenshot-tiles.cjs`.
The screenshot tool uses installed desktop Chrome. No emulator or adb was used.

## Implementation and Bosun hooks

New rendering and pure scalar math live in `TileRelief.kt`, `TileLife.kt`, and
`TileGeometry.kt`. Set `TileGeometry.OPTION = A2` for the alternative, or change
`LIFE_DENSITY` from 3 to 0 to disable life (range capped at six).

MainActivity changes are limited to:

1. Load the six new relief faces for the existing plain/thin edge choices.
   Classic still uses the existing framed paintings, with new sides and bevels.
2. Remember a `TileLifeBoard` cache; clear and fill it while recording STILL;
   draw its overlay immediately after `drawLayer(boardLayer)`, before OVER.
   No animation time is added to BoardLayerKey.
3. Add one optional `tileLife` argument to drawGame; cache visible tile positions
   after drawTile, using the existing Layout center and row perspective scale.
4. Delegate the existing Layout.project formula to tested scalar helpers;
   its projection behavior is unchanged. Face/corner geometry retains the existing
   tile-local affine approximation of perspective rather than a full warped mesh.
5. In drawTile, obtain the selected squash, use the new depth for shadow,
   replace fixed-shade walls with `TileRelief.sides`, select the A2 raster if enabled,
   and call `TileRelief.bevel`. All of this stays in the still layer.
6. Pass faces into Inventory, show the new tiles and a frozen life sample, and
   label terrain with Timber, Clay, Fleece, Grain, Stone, Desert.

Life uses three marks per non-Desert tile: swaying Grain strokes, shifting Timber
leaves, small moving Fleece sheep, Stone glints and breathing Clay dust. Cached
primitive arrays hold positions, resources and scales. The new frame pass creates
no paths, brushes, lists or random generators. It draws at most 114 primitive
commands on a standard 19-tile board (two sheep commands draw the body/head).
The motion is seeded by tile/mark index and is an eight-second seamless sine loop.

## Validation

Requested JDK 17 command is preserved verbatim in `tools/test-tiles.sh`.
Set `GRADLE_USER_HOME=C:/Users/mauro/.gradle` in this sandbox before invoking it.
Final full task-list run: BUILD SUCCESSFUL, 13 seconds; APK assembled.

| Module | Tests | Failures/errors | Skipped | Execution |
| --- | ---: | ---: | ---: | --- |
| engine | 114 | 0 | 0 | cached initially, up-to-date finally |
| ai | 13 | 0 | 0 | cached initially, up-to-date finally |
| net | 19 | 0 | 0 | cached initially, up-to-date finally |
| app | 92 | 0 | 0 | freshly executed |
| Total | 238 | 0 | 0 | |

Seven new tests cover projection at all sampled angles, regular corner geometry,
extrusion scaling, directional lighting, deterministic periodic motion and seam
velocity, density bounds, and zero bytes allocated by the warmed scalar frame
kernel using the JVM thread allocation counter. That last check does not measure
Compose/native GPU allocations. Existing engine results include 2,000 base and
1,000 expansion random-game fuzz coverage and illegal-action checks; these were
reused from Gradle's cache, not newly replayed in this worktree.

Desktop palette PNGs were inspected. Android board/Inventory screenshots, device
frame timing, GPU allocation profiling and monkey testing remain unverified under
the explicit no-emulator/no-adb instruction. Art credits are in CREDITS.md.

## Commits and outstanding review

No commit was created. Both git add and git commit failed because this linked
worktree's metadata lives at
`C:/Users/mauro/catan-android/.git/worktrees/catan-android-tiles`, outside the allowed
write roots. Git could not create index.lock; there is no writable approval path
in this session. Branch remains codex/tiles, baseline fdb55cf; nothing was pushed
and no other worktree or main was edited.

Suggested commit message after permissions are corrected:

```
Add continuous tile relief and cached terrain life overlay with comparison palette

Co-Authored-By: Codex <noreply@openai.com>
```

Open review points: Mauro's A1/A2 pick; whether the modest color-height relief is
enough or authored object height maps are wanted; whether to rebake Classic's
framed faces too; device review of accent placement against painted terrain and
frame timing at strong tilt. The local palette replaces the external Design
canvas workflow as explicitly requested; no external canvas or issue was edited.
