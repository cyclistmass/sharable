package wgbg.engine

/** A harbor: two coastal vertices that grant a trade rate. [type] is a resource index or [GENERIC]. */
class Port(val type: Int, val waterHex: Hex, val v1: Int, val v2: Int) {
    /** Bit set in a player's port mask when they own a settlement/city on this port. */
    val bit get() = if (type == GENERIC) GENERIC_BIT else 1 shl type

    companion object {
        const val GENERIC = -1
        const val GENERIC_BIT = 1 shl 30
    }
}

/**
 * Immutable board layout (terrain, numbers, ports) plus flattened adjacency
 * tables for the hot paths. Shared by every copy of a [GameState].
 */
class Board(
    val topology: Topology,
    /** Resource produced by each hex, or [Resource.NONE] for the desert. */
    val hexResource: IntArray,
    /** Number token per hex, 0 for none. */
    val hexNumber: IntArray,
    val ports: List<Port>,
) {
    val hexCount = topology.hexes.size
    val vertexCount = topology.vertexCount
    val edgeCount = topology.edgeCount

    /** Up to 3 neighbours per vertex, padded with -1. vertexEdge[v*3+i] joins v and vertexNbr[v*3+i]. */
    val vertexNbr = IntArray(vertexCount * 3) { -1 }
    val vertexEdge = IntArray(vertexCount * 3) { -1 }
    val vertexHex = IntArray(vertexCount * 3) { -1 }
    val hexVerts = IntArray(hexCount * 6)
    val edgeA = IntArray(edgeCount)
    val edgeB = IntArray(edgeCount)

    /** Port bit (see [Port.bit]) granted by each vertex, 0 if none. */
    val vertexPortBit = IntArray(vertexCount)

    /** Hexes carrying each number token 2..12. */
    val hexesByNumber: Array<IntArray>
    val desertHex: Int

    init {
        require(hexResource.size == hexCount && hexNumber.size == hexCount)
        for (v in 0 until vertexCount) {
            topology.vertexNeighbors[v].forEachIndexed { i, n -> vertexNbr[v * 3 + i] = n }
            topology.vertexEdges[v].forEachIndexed { i, e -> vertexEdge[v * 3 + i] = e }
            topology.vertexHexes[v].forEachIndexed { i, h -> vertexHex[v * 3 + i] = h }
        }
        for (h in 0 until hexCount) for (i in 0 until 6) hexVerts[h * 6 + i] = topology.hexVertices[h][i]
        for (e in 0 until edgeCount) {
            edgeA[e] = topology.edgeVertices[e][0]
            edgeB[e] = topology.edgeVertices[e][1]
        }
        for (p in ports) {
            vertexPortBit[p.v1] = p.bit
            vertexPortBit[p.v2] = p.bit
        }
        hexesByNumber = Array(13) { n -> (0 until hexCount).filter { hexNumber[it] == n && n != 0 }.toIntArray() }
        desertHex = (0 until hexCount).firstOrNull { hexResource[it] == Resource.NONE } ?: -1
    }

    fun otherEnd(e: Int, v: Int) = if (edgeA[e] == v) edgeB[e] else edgeA[e]

    /** Edge between two adjacent vertices, or -1. */
    fun edgeBetween(a: Int, b: Int): Int {
        for (i in 0 until 3) if (vertexNbr[a * 3 + i] == b) return vertexEdge[a * 3 + i]
        return -1
    }

    fun hexId(hex: Hex) = topology.hexes.indexOf(hex)

    /** Vertex at corner [corner] (0 = top, clockwise) of an arbitrary (possibly off-board) hex, or -1. */
    fun cornerVertex(hex: Hex, corner: Int): Int = vertexByPoint[cornerPoint(hex, corner)] ?: -1

    private val vertexByPoint = HashMap<Point, Int>().apply {
        topology.vertexPos.forEachIndexed { v, p -> put(p, v) }
    }

    companion object {
        private val CORNERS = listOf(
            Point(0, -2), Point(1, -1), Point(1, 1),
            Point(0, 2), Point(-1, 1), Point(-1, -1),
        )

        /** Same lattice as [Topology]: hex center at (2q + r, 3r). */
        fun cornerPoint(hex: Hex, corner: Int) =
            Point(2 * hex.q + hex.r + CORNERS[corner].x, 3 * hex.r + CORNERS[corner].y)
    }
}
