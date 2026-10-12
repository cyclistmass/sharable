#!/usr/bin/env python3
"""Capture the frozen late-game Filament matrix on lane 2, without restarting its game."""
import argparse
import json
import pathlib
import re
import time

from adb_lane2 import shell, stream


def view(**values):
    args = ['am', 'broadcast', '-a', 'com.mauro.wgbg.FILAMENT_VIEW', '-p', 'com.mauro.wgbg']
    for key, value in values.items():
        flag = '--ez' if isinstance(value, bool) else '--ei' if isinstance(value, int) else '--ef'
        args += [flag, key, str(value).lower()]
    response = shell(args).decode()
    if 'Broadcast completed' not in response:
        raise RuntimeError(response)


def capture(output, name):
    (output / (name + '.png')).write_bytes(stream('exec:screencap -p'))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=pathlib.Path, required=True)
    parser.add_argument('--seconds', type=int, default=16)
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    cases = []
    view(playing=False, rotation=0.0, zoom=1.0)
    for pirate in (False, True):
        for lod in (0, 1):
            for angle, tilt in [('flat', 0.0), ('mid', .48), ('max', .96)]:
                name = f'{"pirate" if pirate else "default"}-lod{lod}-{angle}'
                view(live=True, pirate=pirate, lod=lod, tilt=tilt)
                time.sleep(6)  # Exclude asset loading and the first complete timing window.
                shell(['run-as', 'com.mauro.wgbg', 'rm', '-f', 'cache/live-board-frames.jsonl'])
                time.sleep(args.seconds)
                raw = shell(['run-as', 'com.mauro.wgbg', 'cat', 'cache/live-board-frames.jsonl'])
                (args.output / (name + '-frames.jsonl')).write_bytes(raw)
                samples = [json.loads(line) for line in raw.splitlines() if line.startswith(b'{')]
                samples = [s for s in samples if s['lod'] == lod and s['pirate'] == pirate and abs(s['tilt'] - tilt) < .001]
                if len(samples) < 2:
                    raise RuntimeError(f'Insufficient timing windows for {name}: {len(samples)}')
                values = sorted(v for sample in samples for v in sample['intervalMs'])
                elapsed = sum(s['elapsedMs'] for s in samples)
                memory = shell(['dumpsys', 'meminfo', 'com.mauro.wgbg']).decode()
                (args.output / (name + '-meminfo.txt')).write_text(memory)
                pss = re.search(r'TOTAL PSS:\s+(\d+)', memory) or re.search(r'\bTOTAL\s+(\d+)', memory)
                result = dict(name=name, pirate=pirate, lod=lod, tilt=tilt, windows=len(samples), frames=len(values),
                              seconds=elapsed / 1000, fps=len(values) * 1000 / elapsed,
                              p50Ms=values[round(.5 * (len(values) - 1))], p95Ms=values[round(.95 * (len(values) - 1))],
                              skipped=sum(s['skipped'] for s in samples), pssKiB=int(pss[1]) if pss else None,
                              placements=samples[-1]['placements'], renderables=samples[-1]['renderables'])
                cases.append(result)
                print(json.dumps(result), flush=True)
                capture(args.output, name)
                layers = shell(['dumpsys', 'SurfaceFlinger', '--list']).decode().splitlines()
                layer = next((x for x in reversed(layers) if 'com.mauro.wgbg/com.mauro.wgbg.MainActivity#' in x), '')
                if layer:
                    (args.output / (name + '-presentation.txt')).write_bytes(shell(['dumpsys', 'SurfaceFlinger', '--latency', layer]))
                (args.output / 'metrics.json').write_text(json.dumps(cases, indent=2))
        # Same board, same exact tilt setting; existing rendered sprites are the closest comparison.
        for angle, tilt in [('flat', 0.0), ('mid', .48), ('max', .96)]:
            view(live=False, pirate=pirate, sprites3d=True, tilt=tilt)
            time.sleep(2)
            capture(args.output, f'{"pirate" if pirate else "default"}-sprite-{angle}')
    view(live=True, pirate=True, lod=0, tilt=.96)
    for degrees in range(0, 360, 45):
        view(rotation=degrees * 3.141592653589793 / 180)
        time.sleep(1)
        capture(args.output, f'orbit-max-{degrees:03d}')
    view(rotation=0.0, tilt=.48)
    (args.output / 'device-log.txt').write_bytes(shell(['logcat', '-d']))
    print('Matrix, comparisons and orbit captures saved.', flush=True)


if __name__ == '__main__':
    main()
