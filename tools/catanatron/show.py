"""Debug helper: replay one trace up to a step and print both engines' views. Usage: show.py TRACE STEP"""
import json, sys
sys.path.insert(0, __import__("os").path.dirname(__file__))
import diff
from catanatron.state import apply_action
path, stop = sys.argv[1], int(sys.argv[2])
lines = open(path).read().splitlines()
state = diff.build_state(json.loads(lines[0]))
for n, line in enumerate(lines[1:], 1):
    step = json.loads(line)
    if n >= stop - 3:
        print(f"--- step {n}: p{step['p']} prompt {state.current_prompt} road_building={state.is_road_building} free={state.free_roads_available} act={step['act']}")
    if n == stop:
        o, t = diff.ours_legal(step), diff.theirs_legal(state)
        print("ours  :", sorted(map(str, o))[:40]); print("theirs:", sorted(map(str, t))[:40])
        print("after ours:", json.dumps(step["after"])[:800]); break
    apply_action(state, diff.to_action(diff.COLORS[step["p"]], *step["act"]))
