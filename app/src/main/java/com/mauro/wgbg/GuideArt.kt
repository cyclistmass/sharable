package com.mauro.wgbg

import wgbg.engine.Commodity
import wgbg.engine.DevCard
import wgbg.engine.Resource

/**
 * Per-theme art for the card guide and the Inventory screen's knight cards (#71): one small place for a future
 * theme (#79) to add its own cards, instead of scattering `if (pirate)` checks through the UI.
 */
object GuideArt {
    val KNIGHT_NORMAL = listOf(R.drawable.card_knight_normal_1, R.drawable.card_knight_normal_2, R.drawable.card_knight_normal_3)
    val KNIGHT_PIRATE = listOf(R.drawable.card_knight_pirate_1, R.drawable.card_knight_pirate_2, R.drawable.card_knight_pirate_3)

    /** The knight card for [level] (1..3) in the chosen theme. */
    fun knightCard(level: Int, pirate: Boolean): Int = (if (pirate) KNIGHT_PIRATE else KNIGHT_NORMAL)[(level - 1).coerceIn(0, 2)]

    /**
     * A background picture for a base-game Fortune card ([card], a [DevCard] index): Sentinel shows the theme's
     * own knight card (level 1, a full painted card already); the others get a resource or commodity that fits
     * what they do (Windfall: Grain, Corner the Market: Silver, Trailblazers: Timber, Landmark: Stone).
     */
    fun fortuneArt(card: Int, pirate: Boolean): Int = when (card) {
        DevCard.KNIGHT -> knightCard(1, pirate)
        DevCard.YEAR_OF_PLENTY -> ALL_CARD_ART[Resource.WHEAT]
        DevCard.MONOPOLY -> ALL_CARD_ART[Commodity.COIN]
        DevCard.ROAD_BUILDING -> ALL_CARD_ART[Resource.WOOD]
        DevCard.VICTORY_POINT -> ALL_CARD_ART[Resource.ORE]
        else -> ALL_CARD_ART[Resource.WHEAT]
    }

    /** True for the one Fortune card (Sentinel) whose picture is a full knight card, not the cream card face. */
    fun fortuneIsKnightCard(card: Int): Boolean = card == DevCard.KNIGHT
}
