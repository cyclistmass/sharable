#!/usr/bin/env python3
"""Scale the saturation of a GLB's vertex colours (COLOR_0) in place.

Mauro, 2026-10-12: "the whole 3d game needs 20% less sat on the tiles - leave everything else as is".
Each colour moves toward its own luminance: c' = L + factor * (c - L), alpha untouched. Works on float, normalized
unsigned byte and unsigned short colours, VEC3 or VEC4.

    python tools/glb_saturation.py 0.68 --warmth=0.03 app/src/main/assets/filament/tile_*.glb   # 0.8 x 0.85 (BOARD_SATURATION), BOARD_WARMTH

Run it on the original exports (git checkout them first) so the factor is never applied twice.
"""
import json
import struct
import sys

LUMA = (0.2126, 0.7152, 0.0722)  # glTF vertex colours are linear
FORMATS = {5126: ('f', 1.0), 5121: ('B', 255.0), 5123: ('H', 65535.0)}
WIDTH = {'VEC3': 3, 'VEC4': 4}


def scale(path, factor, warmth=0.0):
    data = bytearray(open(path, 'rb').read())
    magic, _, _ = struct.unpack_from('<4sII', data, 0)
    assert magic == b'glTF', path
    json_len, json_type = struct.unpack_from('<II', data, 12)
    assert json_type == 0x4E4F534A
    gltf = json.loads(data[20:20 + json_len])
    bin_start = 20 + json_len + 8
    done = set()
    for mesh in gltf['meshes']:
        for prim in mesh['primitives']:
            a = prim['attributes'].get('COLOR_0')
            if a is None or a in done:
                continue
            done.add(a)
            acc = gltf['accessors'][a]
            view = gltf['bufferViews'][acc['bufferView']]
            code, top = FORMATS[acc['componentType']]
            width = WIDTH[acc['type']]
            size = struct.calcsize(code)
            stride = view.get('byteStride', size * width)
            base = bin_start + view.get('byteOffset', 0) + acc.get('byteOffset', 0)
            for i in range(acc['count']):
                at = base + i * stride
                rgb = [v / top for v in struct.unpack_from('<3' + code, data, at)]
                lum = sum(w * c for w, c in zip(LUMA, rgb))
                out = [lum + factor * (c - lum) for c in rgb]
                out = [min(1.0, max(0.0, c * w)) for c, w in zip(out, (1 + warmth, 1.0, 1 - warmth))]
                if code == 'f':
                    struct.pack_into('<3f', data, at, *out)
                else:
                    struct.pack_into('<3' + code, data, at, *[round(c * top) for c in out])
    open(path, 'wb').write(data)
    return len(done)


if __name__ == '__main__':
    factor = float(sys.argv[1])
    warmth = 0.0
    paths = sys.argv[2:]
    if paths and paths[0].startswith('--warmth='):
        warmth = float(paths.pop(0).split('=', 1)[1])
    for p in paths:
        print(p, scale(p, factor, warmth), 'colour sets')
