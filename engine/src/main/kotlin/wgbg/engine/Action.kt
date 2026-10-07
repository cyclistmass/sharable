package wgbg.engine

/** Action kinds, named after Catanatron's ActionType. New kinds are appended for expansions. */
enum class ActionType {
    ROLL,
    MOVE_ROBBER,
    DISCARD,
    BUILD_ROAD,
    BUILD_SETTLEMENT,
    BUILD_CITY,
    BUY_DEVELOPMENT_CARD,
    PLAY_KNIGHT_CARD,
    PLAY_YEAR_OF_PLENTY,
    PLAY_MONOPOLY,
    PLAY_ROAD_BUILDING,
    MARITIME_TRADE,
    OFFER_TRADE,
    ACCEPT_TRADE,
    REJECT_TRADE,
    CONFIRM_TRADE,
    CANCEL_TRADE,
    END_TURN,
}

/**
 * An action packed into a Long: kind in bits 48..55, payload in bits 0..47.
 * The acting player is implicit ([GameState.actingPlayer]), so a game is just
 * config + seed + a list of these codes.
 *
 * Payloads:
 * - BUILD_ROAD: edge id; BUILD_SETTLEMENT / BUILD_CITY: vertex id
 * - MOVE_ROBBER: hex | (victim + 1) << 8   (victim -1 = steal from nobody)
 * - DISCARD: resource + 1   (0 = discard half the hand at random, Catanatron style)
 * - PLAY_YEAR_OF_PLENTY: r1 | (r2 + 1) << 8   (r2 -1 = only one card)
 * - PLAY_MONOPOLY: resource
 * - MARITIME_TRADE: give | get << 8   (rate is the best one the player has)
 * - OFFER_TRADE: 4 bits per count, offered resources in bits 0..19, asked in 20..39
 * - CONFIRM_TRADE: the accepting player
 */
@JvmInline
value class Action(val code: Long) {
    val typeId: Int get() = (code ushr TYPE_SHIFT).toInt()
    val type: ActionType get() = ActionType.entries[typeId]
    val payload: Long get() = code and PAYLOAD_MASK
    /** Low 8 bits of the payload. */
    val a: Int get() = (code and 0xff).toInt()
    /** Bits 8..15 of the payload. */
    val b: Int get() = ((code ushr 8) and 0xff).toInt()

    val robberHex get() = a
    val robberVictim get() = b - 1
    val discardResource get() = a - 1
    val maritimeGive get() = a
    val maritimeGet get() = b
    val yopFirst get() = a
    val yopSecond get() = b - 1

    fun offerGive(r: Int) = ((code ushr (4 * r)) and 0xf).toInt()
    fun offerWant(r: Int) = ((code ushr (20 + 4 * r)) and 0xf).toInt()

    override fun toString(): String {
        val r = Resource.NAMES
        return when (type) {
            ActionType.MOVE_ROBBER -> "MOVE_ROBBER(hex=$robberHex, victim=$robberVictim)"
            ActionType.DISCARD -> "DISCARD(${if (a == 0) "random" else r[discardResource]})"
            ActionType.BUILD_ROAD -> "BUILD_ROAD(e$payload)"
            ActionType.BUILD_SETTLEMENT, ActionType.BUILD_CITY -> "$type(v$payload)"
            ActionType.PLAY_YEAR_OF_PLENTY ->
                "PLAY_YEAR_OF_PLENTY(${r[yopFirst]}${if (yopSecond >= 0) ", " + r[yopSecond] else ""})"
            ActionType.PLAY_MONOPOLY -> "PLAY_MONOPOLY(${r[a]})"
            ActionType.MARITIME_TRADE -> "MARITIME_TRADE(${r[maritimeGive]} -> ${r[maritimeGet]})"
            ActionType.OFFER_TRADE -> "OFFER_TRADE(give=${(0 until CARD_TYPES).map(::offerGive)}, " +
                "want=${(0 until CARD_TYPES).map(::offerWant)})"
            ActionType.CONFIRM_TRADE -> "CONFIRM_TRADE(with=$payload)"
            else -> type.name
        }
    }

    companion object {
        const val TYPE_SHIFT = 48
        const val PAYLOAD_MASK = (1L shl TYPE_SHIFT) - 1

        fun of(type: ActionType, payload: Long = 0) = Action((type.ordinal.toLong() shl TYPE_SHIFT) or payload)

        val ROLL = of(ActionType.ROLL)
        val BUY_DEVELOPMENT_CARD = of(ActionType.BUY_DEVELOPMENT_CARD)
        val PLAY_KNIGHT_CARD = of(ActionType.PLAY_KNIGHT_CARD)
        val PLAY_ROAD_BUILDING = of(ActionType.PLAY_ROAD_BUILDING)
        val ACCEPT_TRADE = of(ActionType.ACCEPT_TRADE)
        val REJECT_TRADE = of(ActionType.REJECT_TRADE)
        val CANCEL_TRADE = of(ActionType.CANCEL_TRADE)
        val END_TURN = of(ActionType.END_TURN)

        fun buildRoad(edge: Int) = of(ActionType.BUILD_ROAD, edge.toLong())
        fun buildSettlement(vertex: Int) = of(ActionType.BUILD_SETTLEMENT, vertex.toLong())
        fun buildCity(vertex: Int) = of(ActionType.BUILD_CITY, vertex.toLong())
        fun moveRobber(hex: Int, victim: Int) = of(ActionType.MOVE_ROBBER, (hex or ((victim + 1) shl 8)).toLong())
        /** [resource] -1 = Catanatron-style random discard of half the hand. */
        fun discard(resource: Int) = of(ActionType.DISCARD, (resource + 1).toLong())
        fun yearOfPlenty(r1: Int, r2: Int = -1) = of(ActionType.PLAY_YEAR_OF_PLENTY, (r1 or ((r2 + 1) shl 8)).toLong())
        fun monopoly(resource: Int) = of(ActionType.PLAY_MONOPOLY, resource.toLong())
        fun maritimeTrade(give: Int, get: Int) = of(ActionType.MARITIME_TRADE, (give or (get shl 8)).toLong())
        fun confirmTrade(acceptor: Int) = of(ActionType.CONFIRM_TRADE, acceptor.toLong())

        /** Domestic offer: [give] and [want] are per-resource counts (each 0..15). */
        fun offerTrade(give: IntArray, want: IntArray): Action {
            var p = 0L
            for (r in 0 until CARD_TYPES) {
                require(give[r] in 0..15 && want[r] in 0..15) { "trade counts must be 0..15" }
                p = p or (give[r].toLong() shl (4 * r)) or (want[r].toLong() shl (20 + 4 * r))
            }
            return of(ActionType.OFFER_TRADE, p)
        }
    }
}

/** Reusable, growable list of action codes, so move generation allocates nothing in steady state. */
class ActionBuffer(capacity: Int = 128) {
    var codes = LongArray(capacity)
        private set
    var size = 0
        private set

    fun clear() {
        size = 0
    }

    fun add(a: Action) {
        if (size == codes.size) codes = codes.copyOf(size * 2)
        codes[size++] = a.code
    }

    operator fun get(i: Int) = Action(codes[i])

    operator fun contains(a: Action): Boolean {
        for (i in 0 until size) if (codes[i] == a.code) return true
        return false
    }

    fun toList(): List<Action> = List(size) { Action(codes[it]) }
}
