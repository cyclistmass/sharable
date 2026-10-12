package wgbg.engine

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Differential testing against Catanatron (#12), our side: random base games played by our engine, written as
 * traces that tools/catanatron/diff.py replays in Catanatron, comparing the legal moves and the state after every
 * step. Each trace has the board in Catanatron's ids, then one line per move with what our engine allowed, what was
 * played (with its dice, drawn card, stolen or discarded cards) and the state it led to.
 *
 * Catanatron has no trades between players and discards half the hand at random, so the games here use the same:
 * no OFFER_TRADE moves and [GameConfig.randomDiscard].
 */
class CatanatronTraceTest {
    private val topology = Topology.hexagon(2)
    private val nodes = CatanatronMapping.nodeIds(topology)
    private val edges = CatanatronMapping.edges(topology)
    private val tiles = CatanatronMapping.tileIds(topology)
    private val res = Resource.NAMES

    private fun q(s: String) = "\"$s\""
    private fun resOrNull(r: Int) = if (r in 0 until CARD_TYPES) q(res[r]) else "null"
    private fun cube(h: Int): String { val (x, y, z) = CatanatronMapping.cubeOf(topology.hexes[h]); return "[$x,$y,$z]" }

    /** Our move as Catanatron's (type, value); [after] fills in what chance decided (dice, card drawn, card stolen). */
    private fun encode(s: GameState, a: Action, before: GameState? = null, after: GameState? = null): String {
        val p = (before ?: s).actingPlayer
        fun gained(q: Int): List<Int> = (0 until CARD_TYPES).flatMap { r -> List(maxOf(0, after!!.hands[q * CARD_TYPES + r] - before!!.hands[q * CARD_TYPES + r])) { r } }
        fun lost(q: Int): List<Int> = (0 until CARD_TYPES).flatMap { r -> List(maxOf(0, before!!.hands[q * CARD_TYPES + r] - after!!.hands[q * CARD_TYPES + r])) { r } }
        val value = when (a.type) {
            ActionType.BUILD_SETTLEMENT, ActionType.BUILD_CITY -> "${nodes[a.payload.toInt()]}"
            ActionType.BUILD_ROAD -> edges[a.payload.toInt()].let { "[${it.first},${it.second}]" }
            ActionType.ROLL -> if (after == null) "null" else "[${a.a},${a.b}]"
            ActionType.MOVE_ROBBER -> {
                val victim = a.robberVictim
                val stolen = if (after != null && victim >= 0) lost(victim).firstOrNull()?.let { q(res[it]) } ?: "null" else "null"
                "[${cube(a.a)},${if (victim >= 0) victim else "null"},$stolen]"
            }
            ActionType.DISCARD -> if (after == null) "null" else "[" + lost(p).joinToString(",") { q(res[it]) } + "]"
            ActionType.BUY_DEVELOPMENT_CARD -> if (after == null) "null" else
                q(DevCard.NAMES[(0 until DevCard.COUNT).first { after.devHand[p * DevCard.COUNT + it] > before!!.devHand[p * DevCard.COUNT + it] }])
            ActionType.PLAY_YEAR_OF_PLENTY -> "[" + listOfNotNull(a.yopFirst, a.yopSecond.takeIf { it >= 0 }).sorted().joinToString(",") { q(res[it]) } + "]"
            ActionType.PLAY_MONOPOLY -> q(res[a.payload.toInt()])
            ActionType.MARITIME_TRADE -> {
                val give = a.a
                val rate = Rules.tradeRate(s, p, give)
                val get = a.b
                "[" + (List(rate) { q(res[give]) } + List(4 - rate) { "null" } + q(res[get])).joinToString(",") + "]"
            }
            else -> "null"
        }
        return "[${q(a.type.toString())},$value]"
    }

    private fun summary(s: GameState): String {
        val players = (0 until s.numPlayers).joinToString(",") { p ->
            val hand = (0 until CARD_TYPES).joinToString(",") { s.hands[p * CARD_TYPES + it].toString() }
            val dev = (0 until DevCard.COUNT).joinToString(",") { s.devHand[p * DevCard.COUNT + it].toString() }
            "{\"vp\":${s.victoryPoints(p)},\"hand\":[$hand],\"dev\":[$dev],\"knights\":${s.knightsPlayed(p)}," +
                "\"roads\":${Rules.piecesLeft(s, p, Piece.ROAD)},\"settlements\":${Rules.piecesLeft(s, p, Piece.SETTLEMENT)},\"cities\":${Rules.piecesLeft(s, p, Piece.CITY)}}"
        }
        return "{\"players\":[$players],\"road\":${s.longestRoadOwner},\"army\":${s.largestArmyOwner}," +
            "\"bank\":[${(0 until CARD_TYPES).joinToString(",") { s.bank[it].toString() }}],\"robber\":${cube(s.robberHex)},\"winner\":${s.winner}}"
    }

    private fun trace(seed: Long): String {
        val config = GameConfig(numPlayers = 4, randomDiscard = true)
        val s = Rules.newGame(config, seed)
        val rng = Rng(seed * 7919 + 1)
        val b = s.board
        val out = StringBuilder()
        // Tiles and ports in Catanatron's ids.
        val byId = IntArray(19); for (h in 0 until 19) byId[tiles[h]] = h
        out.append("{\"seed\":$seed,\"tiles\":[")
        out.append(byId.joinToString(",") { h -> if (b.hexResource[h] == Resource.NONE) "[null,null]" else "[${q(res[b.hexResource[h]])},${b.hexNumber[h]}]" })
        out.append("],\"ports\":[").append(b.ports.joinToString(",") { resOrNull(it.type) }).append("]}\n")
        val buf = ActionBuffer()
        var steps = 0
        while (!s.isOver && steps++ < 3000) {
            Rules.legalActions(s, buf)
            val legal = (0 until buf.size).map { buf[it] }
            var a = legal[rng.nextInt(legal.size)]
            // Prefer building to wandering, so games reach the late game (longest road, army, 10 points).
            val builds = legal.filter { it.type in setOf(ActionType.BUILD_CITY, ActionType.BUILD_SETTLEMENT, ActionType.BUY_DEVELOPMENT_CARD, ActionType.PLAY_KNIGHT_CARD) }
            if (builds.isNotEmpty() && rng.nextInt(3) > 0) a = builds[rng.nextInt(builds.size)]
            if (a.type == ActionType.ROLL) a = Action.roll(rng.nextInt(6) + 1, rng.nextInt(6) + 1)
            val before = s.copy()
            val legalJson = legal.map { encode(s, it) }.distinct().joinToString(",")
            Rules.applyChecked(s, a)
            out.append("{\"p\":${before.actingPlayer},\"legal\":[$legalJson],\"act\":${encode(before, a, before, s)},\"after\":${summary(s)}}\n")
        }
        return out.toString()
    }

    @Test
    fun `write traces for Catanatron`() {
        val dir = File(System.getProperty("catanatron.dir") ?: "build/catanatron-traces").apply { mkdirs() }
        val games = (System.getProperty("catanatron.games") ?: "200").toInt()
        for (seed in 1L..games) File(dir, "game-$seed.jsonl").writeText(trace(seed))
        assertTrue(dir.listFiles()!!.size >= games)
    }
}
