"""Render saved building-piece scenes (.blend) at any number of tilt steps, body and trim layers separately.

Usage (run gpu_on.py first to render on the GPU):
  blender -b -P tools/blender/gpu_on.py -P tools/blender/render_pieces_steps.py -- \
      --src C:/Users/mauro/wgbg-art/buildings/default --steps 16

Options:
  --src DIR            folder with the piece .blend files (each saved scene must have mesh objects tagged with the
                       custom properties 'layer' = body|trim and 'original' (or 'original_material') = material name, as the buildings
                       pipeline writes them)
  --steps N [N ...]    step counts (default 16); tilt_i = max_tilt * i / (N - 1)
  --max-tilt RAD       default 0.96 (the shared tilt convention)
  --names NAME ...     only these pieces (.blend stem without --blend-prefix); default: every .blend in --src
  --blend-prefix STR   prefix on the .blend names, e.g. 'pirate_' (default '')
  --file-prefix STR    sprite prefix (default 'piece_'; use 'pirate_piece_' for the pirate set)
  --out DIR            output root (default: --src). Files go to <out>/steps<N>/<file-prefix><name>_[trim_]tilt<N>_<i>.png

The camera orbit matches the shipped 8-step sprites (camera 8 units out, looking at the origin), so step 0 and the
last step coincide with the shipped tilt_0 and tilt_7.
"""
import argparse
import json
import math
import sys
import time
from pathlib import Path

import bpy
from mathutils import Vector

argv = sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else []
p = argparse.ArgumentParser(prog='render_pieces_steps.py')
p.add_argument('--src', type=Path, required=True)
p.add_argument('--steps', type=int, nargs='+', default=[16])
p.add_argument('--max-tilt', type=float, default=0.96)
p.add_argument('--names', nargs='+')
p.add_argument('--blend-prefix', default='')
p.add_argument('--file-prefix', default='piece_')
p.add_argument('--out', type=Path)
a = p.parse_args(argv)
out_root = a.out or a.src

blends = sorted(a.src.glob(f'{a.blend_prefix}*.blend'))
names = a.names or [b.stem[len(a.blend_prefix):] for b in blends]
log = []
for name in names:
    bpy.ops.wm.open_mainfile(filepath=str(a.src / f'{a.blend_prefix}{name}.blend'))
    s = bpy.context.scene
    cam = s.camera
    objs = [o for o in s.objects if o.type == 'MESH' and 'layer' in o]
    if not objs:
        print(f'SKIP {name}: no layer-tagged meshes', flush=True)
        continue
    # Pipelines name the per-object material property differently ('original' in the default set,
    # 'original_material' in the pirate harbour set); accept either.
    key = next((k for k in ('original', 'original_material') if all(k in o for o in objs)), None)
    if key is None:
        print(f'SKIP {name}: meshes lack an original/original_material property', flush=True)
        continue
    hold = next((bpy.data.materials.get(n) for n in ('Occlusion holdout', 'Opposite layer occlusion')
                 if bpy.data.materials.get(n)), None)
    if hold is None:  # Blender drops unused materials on save; recreate the single holdout shader
        hold = bpy.data.materials.new('Occlusion holdout')
        hold.use_nodes = True
        nodes = hold.node_tree.nodes
        nodes.clear()
        hold.node_tree.links.new(nodes.new('ShaderNodeHoldout').outputs[0],
                                 nodes.new('ShaderNodeOutputMaterial').inputs[0])
    s.render.threads_mode = 'AUTO'
    for n in a.steps:
        folder = out_root / f'steps{n}'
        folder.mkdir(parents=True, exist_ok=True)
        for i in range(n):
            t = a.max_tilt * i / (n - 1)
            cam.location = (0, -8 * math.sin(t), 8 * math.cos(t))
            cam.rotation_euler = (Vector((0, 0, 0)) - cam.location).to_track_quat('-Z', 'Y').to_euler()
            for layer in ('body', 'trim'):
                for o in objs:
                    o.data.materials[0] = bpy.data.materials[o[key]] if o['layer'] == layer else hold
                fn = f'{a.file_prefix}{name}_{"trim_" if layer == "trim" else ""}tilt{n}_{i}.png'
                s.render.filepath = str(folder / fn)
                st = time.monotonic()
                bpy.ops.render.render(write_still=True)
                log.append(dict(piece=name, steps=n, i=i, layer=layer, file=fn, device=s.cycles.device,
                                seconds=round(time.monotonic() - st, 2)))
                print('STEP_OK', json.dumps(log[-1]), flush=True)
(out_root / 'steps_render_log.json').write_text(json.dumps(log, indent=2))
