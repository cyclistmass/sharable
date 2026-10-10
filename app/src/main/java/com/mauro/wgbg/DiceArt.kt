package com.mauro.wgbg

/**
 * Every dice image in one place (#31, #48: Codex art via mauro-7a, approved by Mauro 2026-10-09).
 * Faces are cropped to the die body; tumbles are 10 frames per die that read as one spin when cycled.
 * Event dice files: 1-3 the Raiders' ship, 4 Guilds gate, 5 Academy gate, 6 Crown gate.
 */
object DiceArt {
    /** Normal red: faces 1-6. */
    val FACES_NR = listOf(R.drawable.dice_nr_1, R.drawable.dice_nr_2, R.drawable.dice_nr_3, R.drawable.dice_nr_4, R.drawable.dice_nr_5, R.drawable.dice_nr_6)
    /** Normal red: 10 tumbling frames. */
    val TUMBLE_NR = listOf(R.drawable.dice_tumble_nr_1, R.drawable.dice_tumble_nr_2, R.drawable.dice_tumble_nr_3, R.drawable.dice_tumble_nr_4, R.drawable.dice_tumble_nr_5, R.drawable.dice_tumble_nr_6, R.drawable.dice_tumble_nr_7, R.drawable.dice_tumble_nr_8, R.drawable.dice_tumble_nr_9, R.drawable.dice_tumble_nr_10)
    /** Normal ivory: faces 1-6. */
    val FACES_NI = listOf(R.drawable.dice_ni_1, R.drawable.dice_ni_2, R.drawable.dice_ni_3, R.drawable.dice_ni_4, R.drawable.dice_ni_5, R.drawable.dice_ni_6)
    /** Normal ivory: 10 tumbling frames. */
    val TUMBLE_NI = listOf(R.drawable.dice_tumble_ni_1, R.drawable.dice_tumble_ni_2, R.drawable.dice_tumble_ni_3, R.drawable.dice_tumble_ni_4, R.drawable.dice_tumble_ni_5, R.drawable.dice_tumble_ni_6, R.drawable.dice_tumble_ni_7, R.drawable.dice_tumble_ni_8, R.drawable.dice_tumble_ni_9, R.drawable.dice_tumble_ni_10)
    /** Pirate red (ship timber): faces 1-6. */
    val FACES_PR = listOf(R.drawable.dice_pr_1, R.drawable.dice_pr_2, R.drawable.dice_pr_3, R.drawable.dice_pr_4, R.drawable.dice_pr_5, R.drawable.dice_pr_6)
    /** Pirate red (ship timber): 10 tumbling frames. */
    val TUMBLE_PR = listOf(R.drawable.dice_tumble_pr_1, R.drawable.dice_tumble_pr_2, R.drawable.dice_tumble_pr_3, R.drawable.dice_tumble_pr_4, R.drawable.dice_tumble_pr_5, R.drawable.dice_tumble_pr_6, R.drawable.dice_tumble_pr_7, R.drawable.dice_tumble_pr_8, R.drawable.dice_tumble_pr_9, R.drawable.dice_tumble_pr_10)
    /** Pirate bone: faces 1-6. */
    val FACES_PB = listOf(R.drawable.dice_pb_1, R.drawable.dice_pb_2, R.drawable.dice_pb_3, R.drawable.dice_pb_4, R.drawable.dice_pb_5, R.drawable.dice_pb_6)
    /** Pirate bone: 10 tumbling frames. */
    val TUMBLE_PB = listOf(R.drawable.dice_tumble_pb_1, R.drawable.dice_tumble_pb_2, R.drawable.dice_tumble_pb_3, R.drawable.dice_tumble_pb_4, R.drawable.dice_tumble_pb_5, R.drawable.dice_tumble_pb_6, R.drawable.dice_tumble_pb_7, R.drawable.dice_tumble_pb_8, R.drawable.dice_tumble_pb_9, R.drawable.dice_tumble_pb_10)
    /** Antique event die: faces 1-6. */
    val FACES_AE = listOf(R.drawable.dice_ae_1, R.drawable.dice_ae_2, R.drawable.dice_ae_3, R.drawable.dice_ae_4, R.drawable.dice_ae_5, R.drawable.dice_ae_6)
    /** Antique event die: 10 tumbling frames. */
    val TUMBLE_AE = listOf(R.drawable.dice_tumble_ae_1, R.drawable.dice_tumble_ae_2, R.drawable.dice_tumble_ae_3, R.drawable.dice_tumble_ae_4, R.drawable.dice_tumble_ae_5, R.drawable.dice_tumble_ae_6, R.drawable.dice_tumble_ae_7, R.drawable.dice_tumble_ae_8, R.drawable.dice_tumble_ae_9, R.drawable.dice_tumble_ae_10)
    /** Pirate event die: faces 1-6. */
    val FACES_PE = listOf(R.drawable.dice_pe_1, R.drawable.dice_pe_2, R.drawable.dice_pe_3, R.drawable.dice_pe_4, R.drawable.dice_pe_5, R.drawable.dice_pe_6)
    /** Pirate event die: 10 tumbling frames. */
    val TUMBLE_PE = listOf(R.drawable.dice_tumble_pe_1, R.drawable.dice_tumble_pe_2, R.drawable.dice_tumble_pe_3, R.drawable.dice_tumble_pe_4, R.drawable.dice_tumble_pe_5, R.drawable.dice_tumble_pe_6, R.drawable.dice_tumble_pe_7, R.drawable.dice_tumble_pe_8, R.drawable.dice_tumble_pe_9, R.drawable.dice_tumble_pe_10)
    /** Pirate event die style options (#48, Mauro: "add them all as options"): P1 Brass-engraved, P2 Ship-timber, P3 Brass cage. */
    val FACES_PE1 = listOf(R.drawable.dice_pe1_1, R.drawable.dice_pe1_2, R.drawable.dice_pe1_3, R.drawable.dice_pe1_4, R.drawable.dice_pe1_5, R.drawable.dice_pe1_6)
    val FACES_PE2 = listOf(R.drawable.dice_pe2_1, R.drawable.dice_pe2_2, R.drawable.dice_pe2_3, R.drawable.dice_pe2_4, R.drawable.dice_pe2_5, R.drawable.dice_pe2_6)
    val FACES_PE3 = listOf(R.drawable.dice_pe3_1, R.drawable.dice_pe3_2, R.drawable.dice_pe3_3, R.drawable.dice_pe3_4, R.drawable.dice_pe3_5, R.drawable.dice_pe3_6)
    /** Their tumbling frames (Codex is still adding frames; tools/import_event_dice.py brings them in). */
    val TUMBLE_PE1 = listOf(R.drawable.dice_tumble_pe1_1, R.drawable.dice_tumble_pe1_2)
    val TUMBLE_PE2 = listOf(R.drawable.dice_tumble_pe2_1, R.drawable.dice_tumble_pe2_2, R.drawable.dice_tumble_pe2_3, R.drawable.dice_tumble_pe2_4)
    val TUMBLE_PE3 = listOf(R.drawable.dice_tumble_pe3_1, R.drawable.dice_tumble_pe3_2, R.drawable.dice_tumble_pe3_3)
    /** Settings > Event die names, in style order (0 = the original pirate event die). */
    val EVENT_STYLES = listOf("Classic bone", "Brass-engraved", "Ship-timber", "Brass cage")
    private val PE_FACES = listOf(FACES_PE, FACES_PE1, FACES_PE2, FACES_PE3)
    private val PE_TUMBLES = listOf(TUMBLE_PE, TUMBLE_PE1, TUMBLE_PE2, TUMBLE_PE3)

    /** The face image for a production die: [red] or ivory/bone, Normal or [pirate] set, showing [value]. */
    fun face(red: Boolean, pirate: Boolean, value: Int): Int = faces(red, pirate)[value.coerceIn(1, 6) - 1]

    /** The tumbling frames for a production die. */
    fun tumble(red: Boolean, pirate: Boolean): List<Int> = when { pirate && red -> TUMBLE_PR; pirate -> TUMBLE_PB; red -> TUMBLE_NR; else -> TUMBLE_NI }

    private fun faces(red: Boolean, pirate: Boolean) = when {
        pirate && red -> FACES_PR; pirate -> FACES_PB; red -> FACES_NR; else -> FACES_NI
    }

    /**
     * The event die face for engine face [e] (0-2 ship; 3 trade = Guilds, 4 politics = Crown, 5 science = Academy),
     * Antique set or [pirate] set in event die [style] (see [EVENT_STYLES]). The art numbers its gates
     * 4 Guilds, 5 Academy, 6 Crown.
     */
    fun eventFace(pirate: Boolean, e: Int, style: Int = 0): Int = (if (pirate) PE_FACES[style.coerceIn(0, 3)] else FACES_AE)[eventFile(e) - 1]

    /** Art file number (1-6) for engine event face [e] (0-5). */
    fun eventFile(e: Int): Int = when (e.coerceIn(0, 5)) { 0, 1, 2 -> e + 1; 3 -> 4; 4 -> 6; else -> 5 }

    fun eventTumble(pirate: Boolean, style: Int = 0): List<Int> = if (pirate) PE_TUMBLES[style.coerceIn(0, 3)] else TUMBLE_AE
}
