package wgbg.engine

/**
 * Translation between our ids and Catanatron's (base map) ids, so models
 * trained in Catanatron can be run on this engine. See CATANATRON_MAPPING.md.
 *
 * Catanatron uses cube coordinates (x, y, z) with x + y + z = 0 for tiles;
 * ours are axial (q, r) with q = x and r = z. Both are pointy-top with the
 * first node of a tile at its NORTH corner, going clockwise, which matches our
 * corner order. Catanatron numbers nodes by visiting the tiles of
 * BASE_MAP_TEMPLATE in order and giving fresh ids to corners not seen before;
 * edges are sorted node-id pairs; land tile ids follow the same order.
 */
object CatanatronMapping {
    /** BASE_MAP_TEMPLATE.topology keys, in order (19 land tiles then 18 water/port tiles). */
    val TEMPLATE_ORDER: List<Triple<Int, Int, Int>> = listOf(
        Triple(0, 0, 0),
        Triple(1, -1, 0), Triple(0, -1, 1), Triple(-1, 0, 1), Triple(-1, 1, 0), Triple(0, 1, -1), Triple(1, 0, -1),
        Triple(2, -2, 0), Triple(1, -2, 1), Triple(0, -2, 2), Triple(-1, -1, 2), Triple(-2, 0, 2), Triple(-2, 1, 1),
        Triple(-2, 2, 0), Triple(-1, 2, -1), Triple(0, 2, -2), Triple(1, 1, -2), Triple(2, 0, -2), Triple(2, -1, -1),
        Triple(3, -3, 0), Triple(2, -3, 1), Triple(1, -3, 2), Triple(0, -3, 3), Triple(-1, -2, 3), Triple(-2, -1, 3),
        Triple(-3, 0, 3), Triple(-3, 1, 2), Triple(-3, 2, 1), Triple(-3, 3, 0), Triple(-2, 3, -1), Triple(-1, 3, -2),
        Triple(0, 3, -3), Triple(1, 2, -3), Triple(2, 1, -3), Triple(3, 0, -3), Triple(3, -1, -2), Triple(3, -2, -1),
    )

    fun cubeOf(h: Hex) = Triple(h.q, -h.q - h.r, h.r)
    fun hexOf(cube: Triple<Int, Int, Int>) = Hex(cube.first, cube.third)

    /** Our hex id -> Catanatron land tile id. */
    fun tileIds(t: Topology): IntArray {
        val land = TEMPLATE_ORDER.take(19).map(::hexOf)
        return IntArray(t.hexes.size) { land.indexOf(t.hexes[it]) }
    }

    /** Our vertex id -> Catanatron node id. */
    fun nodeIds(t: Topology): IntArray {
        val byPoint = HashMap<Point, Int>()
        for (cube in TEMPLATE_ORDER) {
            val hex = hexOf(cube)
            for (c in 0 until 6) byPoint.getOrPut(Board.cornerPoint(hex, c)) { byPoint.size }
        }
        return IntArray(t.vertexCount) { byPoint.getValue(t.vertexPos[it]) }
    }

    /** Our edge id -> Catanatron edge (sorted node pair). */
    fun edges(t: Topology): List<Pair<Int, Int>> {
        val nodes = nodeIds(t)
        return t.edgeVertices.map { (a, b) -> minOf(nodes[a], nodes[b]) to maxOf(nodes[a], nodes[b]) }
    }

    /** Catanatron's resource strings, indexed by our resource ids. */
    val RESOURCES = Resource.NAMES

    /** Catanatron's dev card strings, indexed by our dev card ids. */
    val DEV_CARDS = DevCard.NAMES
}
