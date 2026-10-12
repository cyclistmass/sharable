#!/usr/bin/env python3
"""Generate core-rule query fixtures from installed Catanatron 3.2.1 (not its source code)."""
import argparse
import random
from importlib.metadata import version

from catanatron.models.board import Board, STATIC_GRAPH, longest_acyclic_path
from catanatron.models.player import Color
from catanatron.models.enums import SETTLEMENT


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', required=True)
    args = parser.parse_args()
    assert version('catanatron') == '3.2.1'
    rnd = random.Random(4070)
    edges = sorted(tuple(sorted(e)) for e in STATIC_GRAPH.subgraph(range(54)).edges())
    rows = ['# Catanatron 3.2.1; seed 4070; own roads|other roads|buildings|unblocked longest|free nodes|connected nodes|buildable edges']
    for case in range(200):
        board = Board()
        owned = set()
        nodes = {rnd.randrange(54)}
        for _ in range(case % 16):
            choices = [e for e in edges if e not in owned and (e[0] in nodes or e[1] in nodes)]
            edge = rnd.choice(choices)
            owned.add(edge); nodes.update(edge)
        other = set(rnd.sample([e for e in edges if e not in owned], case % 6))
        for color, roads in [(Color.RED, owned), (Color.BLUE, other)]:
            for a, b in roads:
                board.roads[(a, b)] = color; board.roads[(b, a)] = color
        # Its low-level longest-path helper undercounts trails with BOTH ends blocked by opponents.
        # Cross-check unblocked paths here; the engine's existing cut-road tests cover those endpoints.
        unblocked_longest = len(longest_acyclic_path(board, set(range(54)), Color.RED))
        buildings = []
        for _ in range(case % 8):
            if not board.board_buildable_ids:
                break
            node = rnd.choice(sorted(board.board_buildable_ids))
            owner = rnd.randrange(2)
            buildings.append((node, owner))
            board.buildings[node] = (Color.RED if owner == 0 else Color.BLUE, SETTLEMENT)
            board.board_buildable_ids.discard(node)
            board.board_buildable_ids.difference_update(STATIC_GRAPH.neighbors(node))
        own_nodes = set(n for e in owned for n in e) | {n for n, p in buildings if p == 0}
        board.connected_components[Color.RED] = [own_nodes]
        def edge_text(es): return ','.join(f'{a}:{b}' for a, b in sorted(es))
        rows.append('|'.join([
            edge_text(owned), edge_text(other), ','.join(f'{n}:{p}' for n, p in buildings),
            str(unblocked_longest),
            ','.join(map(str, board.buildable_node_ids(Color.RED, initial_build_phase=True))),
            ','.join(map(str, board.buildable_node_ids(Color.RED))), edge_text(board.buildable_edges(Color.RED))]))
    with open(args.output, 'w', encoding='utf-8', newline='\n') as output:
        output.write('\n'.join(rows) + '\n')
    print('Generated 200 independent core-rule fixtures from Catanatron 3.2.1.')


if __name__ == '__main__':
    main()
