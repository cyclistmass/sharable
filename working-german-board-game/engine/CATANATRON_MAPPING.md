# Catanatron mapping

The engine models its actions and turn phases on
[Catanatron](https://github.com/bcollazo/catanatron) (`catanatron/models/enums.py`,
`models/actions.py`, `apply_action.py`, `state.py`), so a policy trained in Python
can drive this engine. This file lists how ids, actions and phases map between the two, and
where the rules differ.

`wgbg.engine.CatanatronMapping` computes every id translation in code. Use it rather than
copying the tables below by hand.

## Indices

| Concept | Ours | Catanatron |
|---|---|---|
| Resources | `Resource.WOOD..ORE` = 0..4 | `RESOURCES = [WOOD, BRICK, SHEEP, WHEAT, ORE]` (same order) |
| Dev cards | `DevCard.KNIGHT, YEAR_OF_PLENTY, MONOPOLY, ROAD_BUILDING, VICTORY_POINT` = 0..4 | `DEVELOPMENT_CARDS`, same order |
| Players | seat index 0..n-1, seat 0 starts | `state.colors` tuple (shuffled at game start), index = seat |
| Tiles | `Topology.hexagon(2)` hex id (axial `q, r`) | cube `(x, y, z)` with `x = q`, `z = r`, `y = -q - r`; land tile id = position in `BASE_MAP_TEMPLATE` |
| Nodes | vertex id from `Topology` | node id from `initialize_tiles` (tiles visited in template order, new corners numbered N, NE, SE, S, SW, NW) |
| Edges | edge id from `Topology` | sorted node pair `(a, b)` |
| Ports | `BoardGen.PORT_SLOTS[i]` | port id `i` (same template order and same water-hex/direction) |

Both boards are pointy-top hexes, and both list corners clockwise from the top (NORTH), so
`Topology.hexVertices[h][k]` is Catanatron's `tile.nodes[NodeRef[k]]`.

### Tile table (our hex id = Catanatron tile id, cube coordinate)

```
0=13(-2,2,0)   1=12(-2,1,1)   2=11(-2,0,2)   3=14(-1,2,-1)  4=4(-1,1,0)
5=3(-1,0,1)    6=10(-1,-1,2)  7=15(0,2,-2)   8=5(0,1,-1)    9=0(0,0,0)
10=2(0,-1,1)   11=9(0,-2,2)   12=16(1,1,-2)  13=6(1,0,-1)   14=1(1,-1,0)
15=8(1,-2,1)   16=17(2,0,-2)  17=18(2,-1,-1) 18=7(2,-2,0)
```

### Vertex table (our vertex id = Catanatron node id)

```
 0=40   1=18   2=17   3=39   4=41   5=42   6=15   7=14   8=37
 9=38  10=13  11=34  12=35  13=36  14=43  15=21  16=16  17=44
18=5   19=4   20=3   21=12  22=11  23=32  24=33  25=45  26=46
27=19  28=47  29=20  30=0   31=1   32=2   33=9   34=10  35=29
36=30  37=31  38=48  39=49  40=22  41=23  42=6   43=7   44=8
45=27  46=28  47=50  48=51  49=52  50=53  51=24  52=25  53=26
```

### Edge table (our edge id = Catanatron edge)

```
 0=(18,40)  1=(17,18)  2=(17,39)  3=(39,41)  4=(41,42)  5=(40,42)  6=(15,17)  7=(14,15)
 8=(14,37)  9=(37,38) 10=(38,39) 11=(13,14) 12=(13,34) 13=(34,35) 14=(35,36) 15=(36,37)
16=(21,43) 17=(16,21) 18=(16,18) 19=(40,44) 20=(43,44) 21=(5,16)  22=(4,5)   23=(4,15)
24=(3,4)   25=(3,12)  26=(12,13) 27=(11,12) 28=(11,32) 29=(32,33) 30=(33,34) 31=(45,46)
32=(19,46) 33=(19,21) 34=(43,47) 35=(45,47) 36=(19,20) 37=(0,20)  38=(0,5)   39=(0,1)
40=(1,2)   41=(2,3)   42=(2,9)   43=(9,10)  44=(10,11) 45=(10,29) 46=(29,30) 47=(30,31)
48=(31,32) 49=(48,49) 50=(22,49) 51=(20,22) 52=(46,48) 53=(22,23) 54=(6,23)  55=(1,6)
56=(6,7)   57=(7,8)   58=(8,9)   59=(8,27)  60=(27,28) 61=(28,29) 62=(50,51) 63=(51,52)
64=(23,52) 65=(49,50) 66=(52,53) 67=(24,53) 68=(7,24)  69=(24,25) 70=(25,26) 71=(26,27)
```

(`CatanatronMappingTest` prints these tables and checks that they are bijections.)

## Phases (ActionPrompt)

| Our `Phase` | Catanatron |
|---|---|
| `BUILD_INITIAL_SETTLEMENT` | `ActionPrompt.BUILD_INITIAL_SETTLEMENT` |
| `BUILD_INITIAL_ROAD` | `ActionPrompt.BUILD_INITIAL_ROAD` |
| `PLAY_TURN` | `ActionPrompt.PLAY_TURN`. `GameState.freeRoads > 0` corresponds to `is_road_building` / `free_roads_available` |
| `DISCARD` | `ActionPrompt.DISCARD` (`is_discarding`) |
| `MOVE_ROBBER` | `ActionPrompt.MOVE_ROBBER` (`is_moving_knight`) |
| `DECIDE_TRADE` | `ActionPrompt.DECIDE_TRADE` |
| `DECIDE_ACCEPTEES` | `ActionPrompt.DECIDE_ACCEPTEES` |
| `GAME_OVER` | (none; Catanatron checks `winning_color()` / `TURNS_LIMIT` outside the state) |

`GameState.currentPlayer` is `current_turn_index`. `actingPlayer` is `current_player_index`.
`hasRolled` and `devPlayedThisTurn` correspond to `P{i}_HAS_ROLLED` and
`P{i}_HAS_PLAYED_DEVELOPMENT_CARD_IN_TURN`. `devHand - devNew` corresponds to the
`*_OWNED_AT_START` flags.

## Actions (ActionType)

Actions are `Long`s (`wgbg.engine.Action`). The acting player is implicit, while Catanatron's
`Action` carries a `color`.

| Our action | Catanatron `value` | Notes |
|---|---|---|
| `ROLL` | `None` | dice come from the state RNG; `lastRoll` holds the sum |
| `MOVE_ROBBER(hex, victim)` | `(coordinate, color or None)` | hex to cube via `CatanatronMapping.cubeOf`; victim -1 = `None` |
| `DISCARD(resource)` / `DISCARD(-1)` | `None` (random half) | see deviations |
| `BUILD_ROAD(edge)` | `edge` node pair | via the edge table |
| `BUILD_SETTLEMENT(vertex)` / `BUILD_CITY(vertex)` | `node_id` | via the vertex table |
| `BUY_DEVELOPMENT_CARD` | `None` | |
| `PLAY_KNIGHT_CARD` | `None` | then `MOVE_ROBBER` prompt |
| `PLAY_YEAR_OF_PLENTY(r1, r2)` | `(r1, r2)` or `(r1,)` | canonical form `r1 <= r2`; `r2 = -1` for one card |
| `PLAY_MONOPOLY(r)` | `r` | |
| `PLAY_ROAD_BUILDING` | `None` | |
| `MARITIME_TRADE(give, get)` | 5-tuple, e.g. `(WOOD, WOOD, WOOD, None, ORE)` | rate is implicit: always the player's best rate for `give` (Catanatron only generates the best rate too) |
| `OFFER_TRADE(give[5], want[5])` | 10-tuple freqdecks | 4 bits per count |
| `ACCEPT_TRADE` / `REJECT_TRADE` | the 10-tuple | the offer is in `GameState.tradeOffer`, so no payload |
| `CONFIRM_TRADE(seat)` | 11-tuple ending in the acceptor's color | |
| `CANCEL_TRADE` | `None` | |
| `END_TURN` | `None` | |

As in Catanatron, `legalActions` does not list `OFFER_TRADE`, because the offer space is too
large. `Rules.isLegal` validates any offer. For simulations, `GameConfig.generateTradeOffers`
adds simple 1-for-1 offers.

## Rule and model deviations

Where our rules differ from Catanatron's, they follow the official 5th-edition base rules.

1. **Discard**: by default the player picks one card per `DISCARD(resource)` action, repeated
   until they have discarded half. Set `GameConfig.randomDiscard = true` for Catanatron's
   single random `DISCARD`.
2. **Bank shortage**: when only one player is owed a resource the bank cannot fully pay, that
   player gets what is left (official). Catanatron pays nothing.
3. **Victory**: a player wins only during their own turn, checked after every action and at
   the start of their turn. Catanatron checks every player after every action.
4. **Trade responses** run clockwise from the offering player. Catanatron asks players in
   ascending seat order.
5. **Year of Plenty** offers single-card options only when no pair can be drawn. Catanatron
   also offers singles for resources the bank holds only one of.
6. **Longest Road after a break**: the holder keeps the card while still tied for longest. A
   unique longest road of at least 5 takes it. A tie among the other players sets the card
   aside. Catanatron's `maintain_longest_road` may resolve these edge cases differently.
7. **Dev deck**: we draw a random card from the remaining counts when it is bought, instead of
   pre-shuffling the deck. The distribution is the same. It lets `HiddenInfo.determinize`
   resample hidden cards.
8. **Turn counter**: `GameState.turn` counts only regular turns. Catanatron's `num_turns` also
   counts setup placements, and its `TURNS_LIMIT` is 1000. Ours is `GameConfig.maxTurns`.
9. **Seating**: Catanatron shuffles seat order. We always start with seat 0, so the caller
   decides seating.
10. **Number placement**: our random boards shuffle the number tokens, optionally with no
    adjacent 6/8. Catanatron's default is `official_spiral` (the A–R token sequence).
    TODO: add spiral placement for exact board-distribution parity in training.
11. **Not implemented**: Catanatron's `friendly_robber` option and the MINI and TOURNAMENT
    maps. Our `BoardLayout.BEGINNER` uses the rulebook's starting layout, with the
    TOURNAMENT map's port types.
