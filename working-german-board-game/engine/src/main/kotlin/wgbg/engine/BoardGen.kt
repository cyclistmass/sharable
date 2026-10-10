package wgbg.engine

import wgbg.engine.Resource.BRICK
import wgbg.engine.Resource.NONE
import wgbg.engine.Resource.ORE
import wgbg.engine.Resource.SHEEP
import wgbg.engine.Resource.WHEAT
import wgbg.engine.Resource.WOOD

/** Base-game board generation: random (seeded) and the fixed beginner layout. */
object BoardGen {
    val TERRAIN = intArrayOf(
        WOOD, WOOD, WOOD, WOOD, BRICK, BRICK, BRICK, SHEEP, SHEEP, SHEEP, SHEEP,
        WHEAT, WHEAT, WHEAT, WHEAT, ORE, ORE, ORE, NONE,
    )
    val NUMBERS = intArrayOf(2, 3, 3, 4, 4, 5, 5, 6, 6, 8, 8, 9, 9, 10, 10, 11, 11, 12)
    val PORT_TYPES = intArrayOf(WOOD, BRICK, SHEEP, WHEAT, ORE, Port.GENERIC, Port.GENERIC, Port.GENERIC, Port.GENERIC)

    /** A port location: the water hex and the two of its corners touching land. */
    class PortSlot(val water: Hex, val cornerA: Int, val cornerB: Int)

    /**
     * The 9 standard harbor slots, in Catanatron's BASE_MAP_TEMPLATE order (so port
     * index i here is Catanatron's port id i). Corners: 0 = N, clockwise to 5 = NW.
     */
    val PORT_SLOTS = listOf(
        PortSlot(Hex(3, 0), 5, 4),   // cube (3,-3,0)  facing WEST
        PortSlot(Hex(1, 2), 0, 5),   // cube (1,-3,2)  facing NORTHWEST
        PortSlot(Hex(-1, 3), 0, 5),  // cube (-1,-2,3) facing NORTHWEST
        PortSlot(Hex(-3, 3), 1, 0),  // cube (-3,0,3)  facing NORTHEAST
        PortSlot(Hex(-3, 1), 2, 1),  // cube (-3,2,1)  facing EAST
        PortSlot(Hex(-2, -1), 2, 1), // cube (-2,3,-1) facing EAST
        PortSlot(Hex(0, -3), 3, 2),  // cube (0,3,-3)  facing SOUTHEAST
        PortSlot(Hex(2, -3), 4, 3),  // cube (2,1,-3)  facing SOUTHWEST
        PortSlot(Hex(3, -2), 4, 3),  // cube (3,-1,-2) facing SOUTHWEST
    )

    /**
     * Random layout drawn from [rng]. With [avoidAdjacentRed], number tokens are
     * reshuffled until no 6 or 8 touch each other.
     */
    fun random(rng: Rng, avoidAdjacentRed: Boolean = false, topology: Topology = Topology.hexagon(2)): Board {
        val terrain = TERRAIN.copyOf().also(rng::shuffle)
        val numbers = NUMBERS.copyOf()
        val hexNumber = IntArray(terrain.size)
        while (true) {
            rng.shuffle(numbers)
            var k = 0
            for (h in terrain.indices) hexNumber[h] = if (terrain[h] == NONE) 0 else numbers[k++]
            if (!avoidAdjacentRed || !hasAdjacentRed(topology, hexNumber)) break
        }
        val portTypes = PORT_TYPES.copyOf().also(rng::shuffle)
        return build(topology, terrain, hexNumber, portTypes)
    }

    /** The official number-token order A to R, laid along the spiral (the desert is skipped). */
    val SPIRAL_TOKENS = intArrayOf(5, 2, 6, 3, 8, 10, 9, 12, 11, 4, 8, 10, 9, 4, 5, 6, 3, 11)

    /** Axial directions, each 60 degrees on from the last, all the same way round. */
    private val DIRS = listOf(Hex(1, 0), Hex(1, -1), Hex(0, -1), Hex(-1, 0), Hex(-1, 1), Hex(0, 1))

    /**
     * Hexes of a radius-[radius] board in spiral order: round the outer ring from corner [corner] (0-5), then each
     * inner ring from its matching corner the same way round, ending at the centre.
     */
    fun spiralOrder(radius: Int, corner: Int): List<Hex> = buildList {
        for (k in radius downTo 1) {
            val start = DIRS[(corner + 4) % 6]
            var h = Hex(start.q * k, start.r * k)
            for (side in 0 until 6) {
                val d = DIRS[(corner + side) % 6]
                repeat(k) { add(h); h = Hex(h.q + d.q, h.r + d.r) }
            }
        }
        add(Hex(0, 0))
    }

    /** The official variable setup: shuffled terrain and ports, tokens A-R in a spiral from a random corner. */
    fun spiral(rng: Rng, topology: Topology = Topology.hexagon(2)): Board {
        val terrain = TERRAIN.copyOf().also(rng::shuffle)
        val hexNumber = IntArray(terrain.size)
        var k = 0
        for (hex in spiralOrder(2, rng.nextInt(6))) {
            val h = topology.hexes.indexOf(hex)
            if (terrain[h] != NONE) hexNumber[h] = SPIRAL_TOKENS[k++]
        }
        val portTypes = PORT_TYPES.copyOf().also(rng::shuffle)
        return build(topology, terrain, hexNumber, portTypes)
    }

    /**
     * The fixed starting layout from the 5th-edition rulebook (desert in the centre).
     * Port types follow Catanatron's fixed TOURNAMENT map assignment over the
     * standard slots, since only the slot positions are standardised here.
     */
    fun beginner(topology: Topology = Topology.hexagon(2)): Board {
        val rows = listOf(
            listOf(ORE to 10, SHEEP to 2, WOOD to 9),
            listOf(WHEAT to 12, BRICK to 6, SHEEP to 4, BRICK to 10),
            listOf(WHEAT to 9, WOOD to 11, NONE to 0, WOOD to 3, ORE to 8),
            listOf(WOOD to 8, ORE to 3, WHEAT to 4, SHEEP to 5),
            listOf(BRICK to 5, WHEAT to 6, SHEEP to 11),
        )
        val terrain = IntArray(topology.hexes.size)
        val hexNumber = IntArray(topology.hexes.size)
        rows.forEachIndexed { i, row ->
            val r = i - 2
            val qMin = maxOf(-2, -2 - r)
            row.forEachIndexed { j, (res, num) ->
                val h = topology.hexes.indexOf(Hex(qMin + j, r))
                terrain[h] = res
                hexNumber[h] = num
            }
        }
        val ports = intArrayOf(
            Port.GENERIC, BRICK, WOOD, Port.GENERIC, WHEAT, ORE, Port.GENERIC, SHEEP, Port.GENERIC,
        )
        return build(topology, terrain, hexNumber, ports)
    }

    fun build(topology: Topology, terrain: IntArray, hexNumber: IntArray, portTypes: IntArray): Board {
        val probe = Board(topology, terrain, hexNumber, emptyList())
        val ports = PORT_SLOTS.mapIndexed { i, s ->
            val a = probe.cornerVertex(s.water, s.cornerA)
            val b = probe.cornerVertex(s.water, s.cornerB)
            require(a >= 0 && b >= 0) { "port slot $i is not on the coast" }
            Port(portTypes[i], s.water, a, b)
        }
        return Board(topology, terrain, hexNumber, ports)
    }

    private val AXIAL_DIRS = listOf(Hex(1, 0), Hex(-1, 0), Hex(0, 1), Hex(0, -1), Hex(1, -1), Hex(-1, 1))

    fun hasAdjacentRed(topology: Topology, hexNumber: IntArray): Boolean {
        val index = topology.hexes.withIndex().associate { it.value to it.index }
        for ((h, hex) in topology.hexes.withIndex()) {
            if (hexNumber[h] != 6 && hexNumber[h] != 8) continue
            for (d in AXIAL_DIRS) {
                val n = index[Hex(hex.q + d.q, hex.r + d.r)] ?: continue
                if (hexNumber[n] == 6 || hexNumber[n] == 8) return true
            }
        }
        return false
    }
}
