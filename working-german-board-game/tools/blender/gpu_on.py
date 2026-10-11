"""Render Blender Cycles on the GPU without editing the render scripts.

Run it before any render script:
  blender -b -P tools/blender/gpu_on.py -P <render script>.py -- <script args>

It picks a GPU backend for Cycles and forces every Cycles scene onto the GPU at render time.
Configure with the WGBG_GPU environment variable:
  WGBG_GPU=auto   (default) OptiX, then CUDA, then HIP, then oneAPI, then Metal; CPU if no GPU is found
  WGBG_GPU=optix | cuda | hip | oneapi | metal   force that backend (falls back to CPU if unavailable)
  WGBG_GPU=cpu    leave Cycles on the CPU (for A/B checks)
On this PC (RTX 4070 SUPER), OptiX renders a V3 tile frame in ~1.2 s vs ~80 s on 2 CPU threads.
"""
import os

import bpy

choice = os.environ.get('WGBG_GPU', 'auto').strip().upper()
order = ['OPTIX', 'CUDA', 'HIP', 'ONEAPI', 'METAL'] if choice in ('', 'AUTO') else [choice]

prefs = bpy.context.preferences.addons['cycles'].preferences
backend = None
if choice != 'CPU':
    for kind in order:
        try:
            prefs.compute_device_type = kind
        except TypeError:          # backend not supported by this Blender build
            continue
        prefs.get_devices()
        if any(d.type == kind for d in prefs.devices):
            backend = kind
            break
for d in prefs.devices:
    d.use = backend is not None and d.type == backend
print(f'[gpu_on] WGBG_GPU={choice.lower()} backend={backend or "CPU"} '
      f'devices={[d.name for d in prefs.devices if d.use]}', flush=True)


def _force_gpu(scene, *_):
    if backend and scene.render.engine == 'CYCLES' and scene.cycles.device != 'GPU':
        scene.cycles.device = 'GPU'
        print(f'[gpu_on] scene {scene.name!r} -> GPU ({backend})', flush=True)


bpy.app.handlers.render_init.append(_force_gpu)
bpy.app.handlers.render_pre.append(_force_gpu)
