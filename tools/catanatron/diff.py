#!/usr/bin/env python3
"""Differential test against Catanatron (#12): replays our engine's game traces in Catanatron.

Usage: python tools/catanatron/diff.py engine/build/catanatron-traces [--max-report N]
Needs `pip install catanatron` (3.2.x). Traces come from CatanatronTraceTest (engine tests).

For every move it checks, in both engines:
  - who acts,
  - the set of legal moves (rolls, builds, robber targets, trades, cards),
  - after the move: each player's cards, Fortune cards, points, pieces left, the bank, the robber,
    longest road, largest army and the winner.
The first difference in each game is printed; the exit code is 1 if any game differs.

Known Catanatron simplifications, where the official rules (and our engine) differ; the comparison allows for them:
  1. Catanatron lets you play a Fortune (development) card on the turn you bought it. The rules don't.
  2. Before rolling, Catanatron only offers the Knight. The rules let you play any Fortune card before rolling.
  3. Catanatron asks for a road's wood and brick even for Road Building's two free roads (and so won't offer the card
     to a player who can't pay). The free roads are free.
  4. Catanatron's longest road drops a road's last segment when it ends at an opponent's building. Only a building
     the road runs *through* breaks it. Here the longest road is recomputed by brute force with the real rule, our
     award is checked against it, and Catanatron's award is then set to match so the game stays in step.
  5. On a 7 everyone over the limit discards at once; the order isn't a rule. We ask from the roller round the table,
     Catanatron from seat 1, so a run of discards is compared as a whole: the same players, the same counts.
  6. When the bank is short of a resource and only one player is owed it, that player gets what is left. Catanatron
     gives nobody anything. The owed amounts are worked out here from the board and our payout is checked against the
     rule, then Catanatron is brought into step.
  7. Catanatron offers a one-card Windfall whenever one pair of resources runs out. The rule is take any two; one card
     only when the bank holds fewer than two cards in all.
"""
import json
import random
import sys
from pathlib import Path

from catanatron.models.enums import ActionType, RESOURCES
from catanatron.models.map import BASE_MAP_TEMPLATE, CatanMap, initialize_tiles
from catanatron.models.player import Color, SimplePlayer
from catanatron.models.actions import Action
from catanatron.state import State, apply_action
from catanatron.state_functions import (
    get_actual_victory_points,
    player_key,
)

COLORS = [Color.RED, Color.BLUE, Color.ORANGE, Color.WHITE]
DEV = ["KNIGHT", "YEAR_OF_PLENTY", "MONOPOLY", "ROAD_BUILDING", "VICTORY_POINT"]


def build_state(header):
    tiles = header["tiles"]  # by Catanatron tile id: [resource, number]
    # initialize_tiles pops from the end of each list, in topology order (= tile id order).
    resources = [t[0] for t in tiles][::-1]
    numbers = [t[1] for t in tiles if t[0] is not None][::-1]
    ports = list(header["ports"])[::-1]
    catan_map = CatanMap.from_tiles(initialize_tiles(BASE_MAP_TEMPLATE, numbers, ports, resources))
    players = [SimplePlayer(c) for c in COLORS]
    state = State(players, catan_map)
    # Undo State's seat shuffle: seat i is our player i.
    state.players = players
    state.colors = tuple(COLORS)
    state.color_to_index = {c: i for i, c in enumerate(COLORS)}
    from catanatron.models.actions import generate_playable_actions
    state.playable_actions = generate_playable_actions(state)
    return state


def norm_value(t, v):
    """A move's value in one comparable form."""
    if v is None:
        return None
    if t == "BUILD_ROAD":
        return tuple(sorted(v))
    if t == "MOVE_ROBBER":
        coord, victim, stolen = v
        if isinstance(victim, Color):
            victim = COLORS.index(victim)
        return (tuple(coord), victim, None)  # the stolen card is chance, not a choice
    if t == "PLAY_YEAR_OF_PLENTY":
        return tuple(sorted(v))
    if t in ("MARITIME_TRADE",):
        return tuple(v)
    if t in ("ROLL", "DISCARD", "BUY_DEVELOPMENT_CARD"):
        return None  # chance decides these
    return v


def ours_legal(step):
    return {(t, norm_value(t, v if not isinstance(v, list) else tuple(map(lambda x: tuple(x) if isinstance(x, list) else x, v)))) for t, v in step["legal"]}


def free_road_edges(state, color):
    k = player_key(state, color)
    if state.player_state[f"{k}_ROADS_AVAILABLE"] == 0:
        return []
    return [tuple(sorted(e)) for e in state.board.buildable_edges(color)]


def theirs_legal(state):
    out = set()
    color = state.current_color()
    if state.current_prompt.name == "PLAY_TURN" and state.is_road_building:
        # Simplification 3: free roads need no wood or brick.
        return {("BUILD_ROAD", e) for e in free_road_edges(state, color)}
    from catanatron.state_functions import player_can_play_dev
    rolled = state.player_state[f"{player_key(state, color)}_HAS_ROLLED"]
    if (state.current_prompt.name == "PLAY_TURN" and rolled and player_can_play_dev(state, color, "ROAD_BUILDING")
            and free_road_edges(state, color)):
        out.add(("PLAY_ROAD_BUILDING", None))
    for a in state.playable_actions:
        t = a.action_type.value if hasattr(a.action_type, "value") else str(a.action_type)
        t = a.action_type.name
        out.add((t, norm_value(t, a.value)))
    return out


def to_action(color, t, v):
    at = ActionType[t]
    if v is None:
        return Action(color, at, None)
    if t == "BUILD_ROAD":
        return Action(color, at, tuple(v))
    if t == "ROLL":
        return Action(color, at, tuple(v))
    if t == "MOVE_ROBBER":
        coord, victim, stolen = v
        return Action(color, at, (tuple(coord), COLORS[victim] if victim is not None else None, stolen))
    if t in ("PLAY_YEAR_OF_PLENTY", "MARITIME_TRADE", "DISCARD"):
        return Action(color, at, tuple(v) if t != "DISCARD" else list(v))
    return Action(color, at, v)


def their_summary(state):
    players = []
    for i, c in enumerate(COLORS):
        k = player_key(state, c)
        ps = state.player_state
        players.append({
            "vp": get_actual_victory_points(state, c),
            "hand": [ps[f"{k}_{r}_IN_HAND"] for r in RESOURCES],
            "dev": [ps[f"{k}_{d}_IN_HAND"] for d in DEV],
            "knights": ps[f"{k}_PLAYED_KNIGHT"],
            "roads": ps[f"{k}_ROADS_AVAILABLE"],
            "settlements": ps[f"{k}_SETTLEMENTS_AVAILABLE"],
            "cities": ps[f"{k}_CITIES_AVAILABLE"],
        })
    road = next((i for i, c in enumerate(COLORS) if state.player_state[f"{player_key(state, c)}_HAS_ROAD"]), -1)
    army = next((i for i, c in enumerate(COLORS) if state.player_state[f"{player_key(state, c)}_HAS_ARMY"]), -1)
    return {"players": players, "road": road, "army": army, "bank": list(state.resource_freqdeck),
            "robber": list(state.board.robber_coordinate)}


def winner(state):
    for i, c in enumerate(COLORS):
        if get_actual_victory_points(state, c) >= 10:
            return i
    return -1


def longest_trail(state, color):
    """Longest chain of the colour's roads (each road once), not passing through an opponent's building."""
    b = state.board
    blocked = {nid for nid, (col, _) in b.buildings.items() if col != color}
    adj = {}
    for e in {tuple(sorted(e)) for e, col in b.roads.items() if col == color}:
        adj.setdefault(e[0], []).append((e[1], e))
        adj.setdefault(e[1], []).append((e[0], e))
    best = 0

    def walk(node, used, length, first):
        nonlocal best
        best = max(best, length)
        if not first and node in blocked:
            return  # the chain may end at an opponent's building, not run through it
        for nxt, e in adj.get(node, ()):
            if e not in used:
                used.add(e)
                walk(nxt, used, length + 1, False)
                used.discard(e)

    for start in adj:
        walk(start, set(), 0, True)
    return best


def sync_longest_road(state, ours_owner):
    """Checks our longest-road award against the real rule (see 4), then makes Catanatron agree. Returns an error or None."""
    lengths = [longest_trail(state, c) for c in COLORS]
    if ours_owner >= 0:
        if lengths[ours_owner] < 5 or lengths[ours_owner] < max(lengths):
            return f"we gave longest road to player {ours_owner} with lengths {lengths}"
    elif max(lengths) >= 5 and lengths.count(max(lengths)) == 1:
        return f"nobody holds longest road although lengths are {lengths}"
    for i, c in enumerate(COLORS):
        k = player_key(state, c)
        had = state.player_state[f"{k}_HAS_ROAD"]
        has = i == ours_owner
        state.player_state[f"{k}_LONGEST_ROAD_LENGTH"] = lengths[i]
        if had != has:
            state.player_state[f"{k}_HAS_ROAD"] = has
            delta = 2 if has else -2
            state.player_state[f"{k}_VICTORY_POINTS"] += delta
            state.player_state[f"{k}_ACTUAL_VICTORY_POINTS"] += delta
    state.board.road_color = COLORS[ours_owner] if ours_owner >= 0 else None
    state.board.road_length = lengths[ours_owner] if ours_owner >= 0 else 0
    return None


def sync_short_bank(state, number, ours_after):
    """Simplification 6: a short bank with a single player owed pays them the rest. Checks ours, then fixes Catanatron."""
    b = state.board
    for ri, r in enumerate(RESOURCES):
        owed = [0] * len(COLORS)
        for coord, tile in b.map.land_tiles.items():
            if tile.resource != r or tile.number != number or coord == b.robber_coordinate:
                continue
            for node in tile.nodes.values():
                if node in b.buildings:
                    col, kind = b.buildings[node]
                    owed[COLORS.index(col)] += 2 if kind == "CITY" else 1
        theirs_bank = state.resource_freqdeck[ri]
        ours_bank = ours_after["bank"][ri]
        if ours_bank == theirs_bank:
            continue
        takers = [i for i, o in enumerate(owed) if o > 0]
        # Catanatron paid nobody; the bank before the roll is what it still holds.
        if len(takers) != 1 or owed[takers[0]] <= theirs_bank:
            return f"{r}: bank ours {ours_bank} vs Catanatron {theirs_bank} with owed {owed}"
        i = takers[0]
        if ours_bank != 0 or ours_after["players"][i]["hand"][ri] != state.player_state[f"{player_key(state, COLORS[i])}_{r}_IN_HAND"] + theirs_bank:
            return f"{r}: player {i} was owed {owed[i]} from a bank of {theirs_bank}, we paid wrongly"
        state.player_state[f"{player_key(state, COLORS[i])}_{r}_IN_HAND"] += theirs_bank
        state.resource_freqdeck[ri] = 0
        from catanatron.models.actions import generate_playable_actions
        state.playable_actions = generate_playable_actions(state)  # the bank changed: trades and Windfalls with it
    return None


def compare(ours, theirs):
    """Differences between two summaries, as readable strings."""
    diffs = []
    for key in ("road", "army", "bank", "robber"):
        if ours[key] != theirs[key]:
            diffs.append(f"{key}: ours {ours[key]} vs Catanatron {theirs[key]}")
    for i, (a, b) in enumerate(zip(ours["players"], theirs["players"])):
        for key in a:
            if a[key] != b[key]:
                diffs.append(f"player {i} {key}: ours {a[key]} vs Catanatron {b[key]}")
    return diffs


PLAYS = {"PLAY_KNIGHT_CARD": "KNIGHT", "PLAY_YEAR_OF_PLENTY": "YEAR_OF_PLENTY", "PLAY_MONOPOLY": "MONOPOLY", "PLAY_ROAD_BUILDING": "ROAD_BUILDING"}


def dev_hand(state, i):
    k = player_key(state, COLORS[i])
    return {d: state.player_state[f"{k}_{d}_IN_HAND"] for d in DEV}


def replay(path):
    lines = path.read_text().splitlines()
    header = json.loads(lines[0])
    state = build_state(header)
    turn_player, at_start = -1, {}
    steps = [json.loads(line) for line in lines[1:]]
    n = 0
    while n < len(steps):
        step = steps[n]
        n += 1
        p = step["p"]
        if step["act"][0] == "DISCARD":
            # Simplification 5: the whole run of discards at once.
            run = {}
            m = n - 1
            while m < len(steps) and steps[m]["act"][0] == "DISCARD":
                run[steps[m]["p"]] = steps[m]["act"][1]
                m += 1
            over = {i for i, c in enumerate(COLORS) if sum(state.player_state[f"{player_key(state, c)}_{r}_IN_HAND"] for r in RESOURCES) > state.discard_limit}
            if set(run) != over:
                return f"step {n}: discarders differ: ours {sorted(run)}, Catanatron {sorted(over)}"
            for i, cards in run.items():
                hand = sum(state.player_state[f"{player_key(state, COLORS[i])}_{r}_IN_HAND"] for r in RESOURCES)
                if len(cards) != hand // 2:
                    return f"step {n}: player {i} discarded {len(cards)} of {hand}"
            while state.current_prompt.name == "DISCARD":
                i = state.current_player_index
                apply_action(state, Action(COLORS[i], ActionType.DISCARD, list(run[i])))
            n = m
            diffs = compare(steps[m - 1]["after"], their_summary(state))
            if diffs:
                return f"step {m} after the discards: " + "; ".join(diffs[:6])
            continue
        if state.current_player_index != p:
            return f"step {n}: ours has player {p} to act, Catanatron player {state.current_player_index} ({state.current_prompt})"
        if not state.is_initial_build_phase and state.current_turn_index != turn_player:
            turn_player, at_start = state.current_turn_index, dev_hand(state, state.current_turn_index)
        ours, theirs = ours_legal(step), theirs_legal(state)
        rolled = state.player_state[f"{player_key(state, COLORS[p])}_HAS_ROLLED"]
        # Simplification 1: no playing a card bought this turn.
        theirs = {a for a in theirs if a[0] not in PLAYS or at_start.get(PLAYS[a[0]], 0) > 0}
        # Simplification 7: a one-card Windfall only when the bank holds fewer than two cards.
        if sum(state.resource_freqdeck) >= 2:
            theirs = {a for a in theirs if not (a[0] == "PLAY_YEAR_OF_PLENTY" and len(a[1]) == 1)}
        # Simplification 2: before the roll, any Fortune card may be played, not only the Knight.
        if not rolled:
            ours = {a for a in ours if a[0] not in PLAYS or a[0] == "PLAY_KNIGHT_CARD"}
        if ours != theirs:
            only_ours = sorted(map(str, ours - theirs))[:6]
            only_theirs = sorted(map(str, theirs - ours))[:6]
            return f"step {n} ({state.current_prompt}): legal moves differ; only ours {only_ours}; only Catanatron {only_theirs}"
        t, v = step["act"]
        building_free = state.is_road_building
        try:
            apply_action(state, to_action(COLORS[p], t, v))
        except Exception as e:  # noqa: BLE001
            return f"step {n}: Catanatron rejected our {t} {v}: {e}"
        # Simplification 3, continued: Catanatron also stops the free roads early when you couldn't pay for one.
        if building_free and t == "BUILD_ROAD" and not state.is_road_building and state.free_roads_available > 0                 and free_road_edges(state, COLORS[p]):
            state.is_road_building = True
            from catanatron.models.actions import generate_playable_actions
            state.playable_actions = generate_playable_actions(state)
        if t in ("BUILD_ROAD", "BUILD_SETTLEMENT") and not state.is_initial_build_phase:
            err = sync_longest_road(state, step["after"]["road"])
            if err:
                return f"step {n} after {t} {v}: {err}"
        if t == "ROLL" and sum(v) != 7:
            err = sync_short_bank(state, sum(v), step["after"])
            if err:
                return f"step {n} after ROLL {v}: {err}"
        diffs = compare(step["after"], their_summary(state))
        if diffs:
            return f"step {n} after {t} {v}: " + "; ".join(diffs[:6])
        ours_w = step["after"]["winner"]
        if ours_w >= 0 and winner(state) != ours_w:
            return f"step {n}: ours says player {ours_w} won, Catanatron {winner(state)}"
    return None


def main():
    root = Path(sys.argv[1] if len(sys.argv) > 1 else "engine/build/catanatron-traces")
    files = sorted(root.glob("game-*.jsonl"), key=lambda f: int(f.stem.split("-")[1]))
    random.seed(0)
    bad = 0
    moves = 0
    for f in files:
        moves += sum(1 for _ in f.open()) - 1
        err = replay(f)
        if err:
            bad += 1
            if bad <= 20:
                print(f"{f.name}: {err}")
    print(f"Catanatron differential: {len(files)} games, {moves} moves, {bad} differ")
    sys.exit(1 if bad else 0)


if __name__ == "__main__":
    main()
