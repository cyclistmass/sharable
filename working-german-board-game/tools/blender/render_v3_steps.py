"""Render V3 tiles at any number of tilt steps, reusing the unchanged V3 scene builder (render_v3.py).

Usage (run gpu_on.py first to render on the GPU):
  blender -b -P tools/blender/gpu_on.py -P tools/blender/render_v3_steps.py -- --terrain desert --steps 8 16
  # legacy form, still accepted:  ... -- desert 8 16

Options (all optional except the terrain):
  --terrain NAME        timber | stone | fleece | grain | clay | desert
  --steps N [N ...]     step counts to render (default: 8 16). tilt_i = max_tilt * i / (N - 1)
  --max-tilt RAD        tilt of the last step in radians (default 0.96, the shared bandit/buildings convention)
  --samples N           Cycles samples (default 64, same as the shipped V3 renders)
  --out DIR             output folder (default docs/design/tiles-3d-models/v3)

Naming: 8 steps write <out>/<terrain>_V3_tilt_<i>.png (the game's convention); any other count N writes
<out>/steps<N>/<terrain>_V3_tilt<N>_<i>.png. A per-run timing log goes to .v3-tilt-render-temp/<terrain>-gpu-steps.json.
"""
import argparse
import json
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'tools/blender'))
import bpy
import render_v3 as v3

TERRAINS = ['timber', 'stone', 'fleece', 'grain', 'clay', 'desert']
argv = sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else []
if argv and not argv[0].startswith('-'):            # legacy: <terrain> [steps ...]
    argv = ['--terrain', argv[0]] + (['--steps', *argv[1:]] if argv[1:] else [])
p = argparse.ArgumentParser(prog='render_v3_steps.py')
p.add_argument('--terrain', required=True, choices=TERRAINS)
p.add_argument('--steps', type=int, nargs='+', default=[8, 16])
p.add_argument('--max-tilt', type=float, default=0.96)
p.add_argument('--samples', type=int, default=64)
p.add_argument('--out', type=Path, default=ROOT / 'docs/design/tiles-3d-models/v3')
args = p.parse_args(argv)

scratch = ROOT / '.v3-tilt-render-temp'
scratch.mkdir(exist_ok=True)

# Build and validate the scene with the original pipeline; keep its metadata out of the art folder.
v3.OUT = scratch
sys.argv = [sys.argv[0], '--', '--terrain', args.terrain, '--validate-only', '--samples', str(args.samples)]
v3.main()
scene, cam = bpy.context.scene, bpy.context.scene.camera
assert (scene.render.resolution_x, scene.render.resolution_y) == (512, 512)
assert scene.render.film_transparent and abs(cam.data.ortho_scale - 2.65) < 1e-6
scene.cycles.samples = args.samples
scene.render.threads_mode = 'AUTO'

log = []
for n in args.steps:
    if n < 2:
        raise SystemExit(f'--steps must be >= 2, got {n}')
    folder = args.out if n == 8 else args.out / f'steps{n}'
    folder.mkdir(parents=True, exist_ok=True)
    for i in range(n):
        tilt = args.max_tilt * i / (n - 1)
        v3.v2.camera(cam, tilt)
        name = f'{args.terrain}_V3_tilt_{i}.png' if n == 8 else f'{args.terrain}_V3_tilt{n}_{i}.png'
        scene.render.filepath = str(folder / name)
        t = time.monotonic()
        bpy.ops.render.render(write_still=True)
        log.append(dict(steps=n, i=i, tilt=round(tilt, 6), file=name, device=scene.cycles.device,
                        samples=args.samples, seconds=round(time.monotonic() - t, 2)))
        print('STEP_OK', json.dumps(log[-1]), flush=True)
(scratch / f'{args.terrain}-gpu-steps.json').write_text(json.dumps(log, indent=2))
