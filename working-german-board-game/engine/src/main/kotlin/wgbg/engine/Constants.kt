package wgbg.engine

/**
 * Resource card indices, in Catanatron's order. Cities & Knights commodities
 * (paper, cloth, coin) will be appended after ORE by growing [CARD_TYPES].
 */
object Resource {
    const val WOOD = 0
    const val BRICK = 1
    const val SHEEP = 2
    const val WHEAT = 3
    const val ORE = 4
    const val COUNT = 5
    val NAMES = listOf("WOOD", "BRICK", "SHEEP", "WHEAT", "ORE")

    /** Terrain value for hexes that produce nothing. */
    const val NONE = -1
}

/** Number of card slots in hands and bank (resources only in the base game). */
const val CARD_TYPES = Resource.COUNT

/** Each resource has this many cards in the base game. */
const val BANK_PER_RESOURCE = 19

/** Development card indices, in Catanatron's order. */
object DevCard {
    const val KNIGHT = 0
    const val YEAR_OF_PLENTY = 1
    const val MONOPOLY = 2
    const val ROAD_BUILDING = 3
    const val VICTORY_POINT = 4
    const val COUNT = 5
    val NAMES = listOf("KNIGHT", "YEAR_OF_PLENTY", "MONOPOLY", "ROAD_BUILDING", "VICTORY_POINT")

    /** Base-game deck composition (25 cards). */
    val DECK = intArrayOf(14, 2, 2, 2, 5)
}

/** Piece kinds a player owns a limited supply of. */
object Piece {
    const val ROAD = 0
    const val SETTLEMENT = 1
    const val CITY = 2
    const val COUNT = 3
    val LIMITS = intArrayOf(15, 5, 4)
}

/** Building costs as per-card-type counts. */
object Cost {
    val ROAD = intArrayOf(1, 1, 0, 0, 0)
    val SETTLEMENT = intArrayOf(1, 1, 1, 1, 0)
    val CITY = intArrayOf(0, 0, 0, 2, 3)
    val DEV_CARD = intArrayOf(0, 0, 1, 1, 1)
}
