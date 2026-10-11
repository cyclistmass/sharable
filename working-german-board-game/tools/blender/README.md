# Blender art tools

Python scripts that **build** the game's 3D art in Blender 4.5 (headless) and render it to transparent PNG sprites.
The game draws the sprites; it doesn't run a 3D engine. Smooth tilting comes from rendering each object at several
**tilt steps** and cross-fading the two nearest steps in the app.

Blender: `C:/Program Files/Blender Foundation/Blender 4.5/blender.exe` (below, `$B`). Run from the repo root.

## Render on the GPU (`gpu_on.py`)

Load `gpu_on.py` before any render script. It switches Cycles to the GPU without changing the script:

```bash
$B -b -P tools/blender/gpu_on.py -P tools/blender/<script>.py -- <script args>
```

| `WGBG_GPU` | Effect |
|---|---|
| `auto` (default) | OptiX, then CUDA, HIP, oneAPI, Metal; CPU if no GPU |
| `optix`, `cuda`, `hip`, `oneapi`, `metal` | force that backend (falls back to CPU if it's not available) |
| `cpu` | stay on the CPU, for A/B checks |

Measured on the dev PC (RTX 4070 SUPER, OptiX): a 512 px V3 tile frame at 64 samples takes **~1.2 s** on the GPU vs
**~80 s** on 2 CPU threads. The first frame of each run takes ~2 min while the scene builds and the OptiX kernels
compile; after that, every step costs about a second. The GPU output matches the CPU renders (checked side by side).

## V3 tiles at N tilt steps (`render_v3_steps.py`)

Builds a V3 terrain scene once with the unchanged `render_v3.py` pipeline, then renders it at each tilt step:

```bash
$B -b -P tools/blender/gpu_on.py -P tools/blender/render_v3_steps.py -- --terrain desert --steps 8 16
```

| Option | Default | Meaning |
|---|---|---|
| `--terrain` | (required) | `timber`, `stone`, `fleece`, `grain`, `clay`, `desert` |
| `--steps N [N ...]` | `8 16` | step counts; step *i* of *N* is at tilt `max_tilt * i / (N - 1)` |
| `--max-tilt RAD` | `0.96` | tilt of the last step (55°, the shared bandit/buildings convention) |
| `--samples N` | `64` | Cycles samples, the same as the shipped V3 renders |
| `--out DIR` | `docs/design/tiles-3d-models/v3` | output folder |

Output names: 8 steps → `<out>/<terrain>_V3_tilt_<i>.png` (the app's convention). Any other N →
`<out>/steps<N>/<terrain>_V3_tilt<N>_<i>.png`. Each run logs its timings in `.v3-tilt-render-temp/<terrain>-gpu-steps.json`.
The legacy form `-- desert 8 16` still works.

All six terrains:

```bash
for t in timber stone fleece grain clay desert; do
  $B -b -P tools/blender/gpu_on.py -P tools/blender/render_v3_steps.py -- --terrain $t --steps 8 16
done
```

## Other scripts

- `render_v3.py` (+ `v3_art.py`, `v3_story.py`, `v3_finish.py`): the V3 tile scenes. With `--views flat mid max` it
  renders the three review views.
- `render_v2_set.py`, `render_models.py`, `alpine_chain.py`, `render_density.py`, …: earlier V1/V2 passes, kept for
  history.
- `render_bandit_v1.py`, `bandit.py`: the bandit V1 options. The shipped bandit V2 lives in `wgbg-art/bandit-v2/`.
- Buildings live outside this repo: `wgbg-art/buildings/` (default) and `wgbg-art/buildings-pirate-astra/` (pirate).
  All of these Cycles scripts can use `gpu_on.py` the same way.

Rules for every render (sizes, anchors, the number-plate clearing, light, tilt convention):
`docs/design/tiles-3d-models/ART_SPEC.md` and `docs/design/art-spec.md` on main.

## Building pieces at N tilt steps (`render_pieces_steps.py`)

Renders saved piece scenes (`.blend`) at any step count, as separate **body** (tinted in game) and **trim** (brass,
untinted) layers. It works for any `.blend` whose meshes carry `layer` (body|trim) and `original` or
`original_material` (material name) custom properties: the default and pirate building sets, and the knights.

```bash
$B -b -P tools/blender/gpu_on.py -P tools/blender/render_pieces_steps.py -- \
   --src C:/Users/mauro/wgbg-art/buildings/default --steps 16
$B -b -P tools/blender/gpu_on.py -P tools/blender/render_pieces_steps.py -- \
   --src C:/Users/mauro/wgbg-art/buildings-pirate-astra --blend-prefix pirate_ --file-prefix pirate_piece_ --steps 8 16
```

| Option | Default | Meaning |
|---|---|---|
| `--src DIR` | (required) | folder of piece `.blend` files |
| `--steps N [N ...]` | `16` | step counts |
| `--max-tilt RAD` | `0.96` | tilt of the last step |
| `--names NAME ...` | all | only these pieces (`.blend` stem without the prefix) |
| `--blend-prefix STR` | `''` | e.g. `pirate_` |
| `--file-prefix STR` | `piece_` | e.g. `pirate_piece_` |
| `--out DIR` | `--src` | files go to `<out>/steps<N>/<file-prefix><name>_[trim_]tilt<N>_<i>.png` |

Measured (RTX 4070 SUPER): a full building set (9 pieces × 16 steps × 2 layers = 288 frames) in ~100–130 s.
Checked against the shipped 8-step sprites: the default set matches pixel for pixel. The pirate set matches at every
step except the shipped flat `tilt_0`, which was rendered with the camera rolled 180° (a straight-down camera has an
ambiguous roll). These renders are consistent at every step and fix it.

## Bandit at N steps

`wgbg-art/bandit-v2/render_bandit_final_steps.py` (step count from the `WGBG_STEPS` env var, default 16) renders the
shipped bandit to `raw/steps<N>/`, and `check_bandit_steps.py` verifies that no number-plate digit shows at any step:

```bash
cd C:/Users/mauro/wgbg-art/bandit-v2
WGBG_STEPS=16 $B -b -P <repo>/tools/blender/gpu_on.py -P render_bandit_final_steps.py
WGBG_STEPS=16 python check_bandit_steps.py
```
