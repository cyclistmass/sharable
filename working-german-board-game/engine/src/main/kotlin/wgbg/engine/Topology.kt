package wgbg.engine

/**
 * Board geometry: hexes, the intersections (vertices) where settlements go,
 * and the paths (edges) where roads go.
 *
 * Hexes use pointy-top axial coordinates (q, r). Corners are identified by
 * exact integer positions on a lattice where a hex center is (2q + r, 3r),
 * so corners shared by neighbouring hexes collapse to the same key.
 *
 * Vertex and edge ids are dense ints so game state can live in flat arrays,
 * which keeps cloning cheap for AI search.
 */
data class Hex(val q: Int, val r: Int)

data class Point(val x: Int, val y: Int)

class Topology private constructor(
    val hexes: List<Hex>,
    /** Pixel-lattice position of each vertex, for rendering. */
    val vertexPos: List<Point>,
    /** For each hex, its 6 vertex ids clockwise from the top corner. */
    val hexVertices: List<IntArray>,
    /** For each edge, its two vertex ids. */
    val edgeVertices: List<IntArray>,
    /** For each vertex, ids of adjacent vertices. */
    val vertexNeighbors: List<IntArray>,
    /** For each vertex, ids of touching edges. */
    val vertexEdges: List<IntArray>,
    /** For each vertex, ids of touching hexes. */
    val vertexHexes: List<IntArray>,
) {
    val vertexCount get() = vertexPos.size
    val edgeCount get() = edgeVertices.size

    companion object {
        /** Corner offsets for a pointy-top hex, clockwise from the top. */
        private val CORNERS = listOf(
            Point(0, -2), Point(1, -1), Point(1, 1),
            Point(0, 2), Point(-1, 1), Point(-1, -1),
        )

        /** Standard hexagonal board of the given radius (2 = the 19-hex base game). */
        fun hexagon(radius: Int): Topology {
            val hexes = buildList {
                for (q in -radius..radius) for (r in -radius..radius) {
                    if (kotlin.math.abs(q + r) <= radius) add(Hex(q, r))
                }
            }
            return of(hexes)
        }

        fun of(hexes: List<Hex>): Topology {
            val vertexIds = LinkedHashMap<Point, Int>()
            val hexVertices = hexes.map { h ->
                IntArray(6) { i ->
                    val p = Point(2 * h.q + h.r + CORNERS[i].x, 3 * h.r + CORNERS[i].y)
                    vertexIds.getOrPut(p) { vertexIds.size }
                }
            }
            val edgeIds = LinkedHashMap<Pair<Int, Int>, Int>()
            for (vs in hexVertices) for (i in 0 until 6) {
                val a = vs[i]
                val b = vs[(i + 1) % 6]
                edgeIds.getOrPut(minOf(a, b) to maxOf(a, b)) { edgeIds.size }
            }
            val n = vertexIds.size
            val neighbors = List(n) { mutableListOf<Int>() }
            val edges = List(n) { mutableListOf<Int>() }
            val touching = List(n) { mutableListOf<Int>() }
            for ((pair, id) in edgeIds) {
                neighbors[pair.first] += pair.second
                neighbors[pair.second] += pair.first
                edges[pair.first] += id
                edges[pair.second] += id
            }
            hexVertices.forEachIndexed { hexId, vs -> vs.forEach { touching[it] += hexId } }
            return Topology(
                hexes = hexes,
                vertexPos = vertexIds.keys.toList(),
                hexVertices = hexVertices,
                edgeVertices = edgeIds.keys.map { intArrayOf(it.first, it.second) },
                vertexNeighbors = neighbors.map { it.toIntArray() },
                vertexEdges = edges.map { it.toIntArray() },
                vertexHexes = touching.map { it.toIntArray() },
            )
        }
    }
}
