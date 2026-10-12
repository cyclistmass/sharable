#!/usr/bin/env python3
"""Sample actual opaque scene geometry between runtime number glyphs and the camera.

Requires numpy. Sprite overlays are deliberately excluded; review the screenshots for those.
"""
import argparse
import json
import math
import pathlib
import struct
import numpy as np


def triangles(path):
    data = path.read_bytes()
    size = struct.unpack_from('<I', data, 12)[0]
    gltf = json.loads(data[20:20+size])
    binary = data[28+size:]
    def accessor(index, width):
        a = gltf['accessors'][index]; v = gltf['bufferViews'][a['bufferView']]
        dtype = {5126: '<f4', 5125: '<u4', 5123: '<u2', 5121: 'u1'}[a['componentType']]
        element = np.dtype(dtype).itemsize
        start = v.get('byteOffset', 0) + a.get('byteOffset', 0)
        return np.ndarray((a['count'], width), dtype=dtype, buffer=binary, offset=start,
                          strides=(v.get('byteStride', width * element), element)).copy()
    meshes = []
    for node in gltf['nodes']:
        assert not any(k in node for k in ['matrix', 'translation', 'rotation', 'scale']), 'Unexpected local transform'
        if 'mesh' not in node:
            continue
        for primitive in gltf['meshes'][node['mesh']]['primitives']:
            points = accessor(primitive['attributes']['POSITION'], 3)
            indices = accessor(primitive['indices'], 1).reshape(-1, 3)
            meshes.append(points[indices])
    return np.concatenate(meshes)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--scene', type=pathlib.Path, required=True)
    parser.add_argument('--assets', type=pathlib.Path, required=True)
    parser.add_argument('--output', type=pathlib.Path, required=True)
    args = parser.parse_args()
    scene = json.loads(args.scene.read_text())
    cache = {}
    meshes = []
    numbers = []
    for p in scene['placements']:
        if p['model'].startswith('digit_'):
            numbers.append(p); continue
        if p['model'] in ['sea', 'number_plate']:
            continue
        if p['model'] not in cache:
            cache[p['model']] = triangles(args.assets / (p['model'] + '.glb'))
        points = cache[p['model']].copy() * p['scale']
        c, s = math.cos(p['heading']), math.sin(p['heading'])
        x = points[:, :, 0].copy(); z = points[:, :, 2].copy()
        points[:, :, 0] = c*x+s*z+p['x']; points[:, :, 2] = -s*x+c*z+p['z']
        points[:, :, 1] += p['height']
        meshes.append(points)
    tri = np.concatenate(meshes).astype(np.float32)
    a = tri[:, 0]; e1 = tri[:, 1] - a; e2 = tri[:, 2] - a
    results = []
    for tilt in [0, math.pi/4, .96]:
        for degrees in range(0, 360, 45):
            yaw = math.radians(degrees)
            direction = np.array([math.sin(tilt)*math.sin(yaw), math.cos(tilt), math.sin(tilt)*math.cos(yaw)], dtype=np.float32)
            pv = np.cross(direction, e2)
            det = np.einsum('ij,ij->i', e1, pv)
            inv = np.divide(1, det, out=np.zeros_like(det), where=np.abs(det) > 1e-8)
            occluded = []
            for index, p in enumerate(numbers):
                covered = 0
                # Centre and four samples within the digit area, rather than the entire decorative disk.
                for dx, dz in [(0, 0), (.10, 0), (-.10, 0), (0, .10), (0, -.10)]:
                    # Runtime glyph orientation cancels yaw, so samples follow that same orientation.
                    origin = np.array([p['x']+math.cos(yaw)*dx+math.sin(yaw)*dz, p['height']+.001,
                                       p['z']-math.sin(yaw)*dx+math.cos(yaw)*dz], dtype=np.float32)
                    tv = origin-a
                    u = np.einsum('ij,ij->i', tv, pv) * inv
                    qv = np.cross(tv, e1)
                    v = np.einsum('ij,j->i', qv, direction) * inv
                    t = np.einsum('ij,ij->i', e2, qv) * inv
                    hit = (np.abs(det)>1e-8) & (u>=0) & (v>=0) & (u+v<=1) & (t>.001)
                    covered += bool(np.any(hit))
                if covered:
                    occluded.append(dict(index=index, number=p['model'], x=p['x'], z=p['z'], coveredSamples=covered))
            result = dict(tilt=tilt, yawDegrees=degrees, numbers=len(numbers),
                          clearAllFive=len(numbers)-len(occluded), occluded=occluded)
            results.append(result)
            print(f'tilt {tilt:.3f}, yaw {degrees:3d}: {result["clearAllFive"]}/{len(numbers)} fully clear sample groups', flush=True)
    args.output.write_text(json.dumps(dict(sceneHash=scene['hash'], triangles=len(tri), samplePoints=5, results=results), indent=2))


if __name__ == '__main__':
    main()
