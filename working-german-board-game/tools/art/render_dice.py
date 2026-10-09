"""Pre-renders Mauro's dice GLBs to sprite sheets: a tumbling spin and a resting pose per face.
Software rasterizer (painter's algorithm with back-face culling, flat Lambert shading) on Pillow."""
import math, os, sys
import numpy as np, trimesh
from PIL import Image, ImageDraw

D = (os.environ.get("DICE_DIR") or ".") + "/"   # folder with the dice GLBs (Mauro's dice_ship_models_v2.zip)
SIZE, SS = 128, 3                       # output px, supersampling
LIGHT = np.array([-0.45, 0.55, 0.70]); LIGHT /= np.linalg.norm(LIGHT)

FACES = [('z', 1, 1), ('z', -1, 6), ('x', 1, 3), ('x', -1, 4), ('y', 1, 2), ('y', -1, 5)]
PATTERNS = {1: [(0, 0)], 2: [(-1, -1), (1, 1)], 3: [(-1, -1), (0, 0), (1, 1)], 4: [(-1, -1), (-1, 1), (1, -1), (1, 1)],
            5: [(-1, -1), (-1, 1), (0, 0), (1, -1), (1, 1)], 6: [(-1, -1), (-1, 0), (-1, 1), (1, -1), (1, 0), (1, 1)]}

def load(name):
    """The die body from the GLB (first geometry) plus pips as flat discs at generate.py's positions."""
    s = trimesh.load(D + name + ".glb")
    nodes = list(s.graph.nodes_geometry)
    # The body is the 8-corner box; every other mesh is a pip.
    bodyNode = next(nd for nd in nodes if len(s.geometry[s.graph[nd][1]].vertices) == 8)
    pipNode = next(nd for nd in nodes if nd != bodyNode)
    tris, cols, body = [], [], []
    tf, gname = s.graph[bodyNode]
    g = s.geometry[gname].copy(); g.apply_transform(tf)
    vc = g.visual.vertex_colors[:, :3].astype(float)
    for f in g.faces:
        tris.append(g.vertices[f]); cols.append(vc[f].mean(axis=0)); body.append(True)
    tf2, g2name = s.graph[pipNode]
    pip = s.geometry[g2name].visual.vertex_colors[0, :3].astype(float)
    for axis, sign, num in FACES:
        for a, b in PATTERNS[num]:
            a *= .285; b *= .285
            ring = []
            for k in range(20):
                u, v = a + .07 * math.cos(2 * math.pi * k / 20), b + .07 * math.sin(2 * math.pi * k / 20)
                ring.append((u, v, sign * .503) if axis == 'z' else (sign * .503, u, v) if axis == 'x' else (u, sign * .503, v))
            c = np.mean(ring, axis=0)
            for k in range(20):
                tri = np.array([c, ring[k], ring[(k + 1) % 20]])
                nrm = np.cross(tri[1] - tri[0], tri[2] - tri[0])
                out = np.array([1 if axis == 'x' else 0, 1 if axis == 'y' else 0, 1 if axis == 'z' else 0]) * sign
                if nrm @ out < 0: tri = tri[[0, 2, 1]]          # wind outward
                tris.append(tri); cols.append(pip); body.append(False)
    return np.array(tris), np.array(cols), np.array(body)

def rot(axis, a):
    return trimesh.transformations.rotation_matrix(a, axis)[:3, :3]

def render(tris, cols, body, R):
    t = tris @ R.T                                   # rotate
    n = np.cross(t[:, 1] - t[:, 0], t[:, 2] - t[:, 0])
    n /= np.linalg.norm(n, axis=1, keepdims=True) + 1e-9
    vis = n[:, 2] > 1e-4                             # camera looks down -z (toward the viewer is +z)
    depth = t[:, :, 2].mean(axis=1)
    W = SIZE * SS
    img = Image.new("RGBA", (W, W), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    scale = W * 0.40
    # The body first (back to front), then pip caps on top: they sit flush on their face.
    order = [i for i in np.argsort(depth) if body[i]] + [i for i in np.argsort(depth) if not body[i]]
    for i in order:
        if not vis[i]: continue
        lam = 0.42 + 0.58 * max(0.0, float(n[i] @ LIGHT))
        spec = max(0.0, float(n[i] @ (LIGHT + np.array([0, 0, 1])) / np.linalg.norm(LIGHT + np.array([0, 0, 1])))) ** 24 * 0.35
        c = np.clip(cols[i] * lam + 255 * spec, 0, 255).astype(int)
        pts = [(W / 2 + p[0] * scale, W / 2 - p[1] * scale) for p in t[i]]
        d.polygon(pts, fill=(int(c[0]), int(c[1]), int(c[2]), 255))
    return img.resize((SIZE, SIZE), Image.LANCZOS)

# Face -> number, from generate.py: +z=1, -z=6, +x=3, -x=4, +y=2, -y=5. A pose turning face N to point UP (+y):
FACE_UP = {2: np.eye(3), 5: rot([1, 0, 0], math.pi), 1: rot([1, 0, 0], -math.pi / 2), 6: rot([1, 0, 0], math.pi / 2),
           3: rot([0, 0, 1], math.pi / 2), 4: rot([0, 0, 1], -math.pi / 2)}
# Looking down on the table at a steep angle, turned a little: the top face (the roll) dominates, two sides show.
VIEW = rot([1, 0, 0], 0.95) @ rot([0, 1, 0], -0.55)

def sheet(name, tumble=16):
    tris, cols, body = load(name)
    frames = []
    axis = np.array([0.7, 1.0, 0.35]); axis /= np.linalg.norm(axis)
    for k in range(tumble):                         # one full tumble around a skewed axis
        frames.append(render(tris, cols, body, VIEW @ rot(axis, 2 * math.pi * k / tumble) @ rot([0, 0, 1], 0.6 * k)))
    for face in range(1, 7):                        # resting poses 1..6
        frames.append(render(tris, cols, body, VIEW @ FACE_UP[face]))
    out = Image.new("RGBA", (SIZE * len(frames), SIZE))
    for i, f in enumerate(frames): out.paste(f, (i * SIZE, 0))
    out.save(D + f"sheet_{name}.png")
    print(name, len(frames), "frames")

for n in sys.argv[1:] or ["dice_ivory"]:
    sheet(n)
