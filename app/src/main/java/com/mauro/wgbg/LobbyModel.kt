package com.mauro.wgbg

import kotlin.random.Random

// ------------------------------------------------------------------ lobby state (UI-free, unit tested)
// The Firebase transport (#7) will feed this; until then the lobby runs on stubbed data.

/** Where invite links point. Placeholder until #7 picks the real domain and App Link. */
const val JOIN_BASE = "https://wgbg.example/join/"

/** One lobby seat: open, a person who joined, or a bot the host placed. */
data class LobbySeat(
    val name: String? = null,
    val bot: BotLevel? = null,
    val ready: Boolean = false,
    val photo: String? = null,
) {
    val open get() = name == null && bot == null
    val isBot get() = bot != null
    /** Bots are always ready; open seats never are. */
    val isReady get() = isBot || (!open && ready)
    val label get() = name ?: bot?.let { "${it.label} bot" } ?: "Open seat"
}

/** A room: its code, the host's settings, and four seats (seat 0 is the host). */
data class Lobby(
    val code: String,
    val setup: GameSetup,
    val seats: List<LobbySeat>,
    /** Which seat this phone is. */
    val me: Int = 0,
) {
    init {
        require(seats.size == GameSetup.MAX_SEATS)
        require(me in seats.indices)
    }

    val link get() = JOIN_BASE + code
    val isHost get() = me == 0
    val filled get() = seats.count { !it.open }

    /** Start needs the ruleset's player count, everyone seated ready, and the host's settings valid. */
    val canStart get() = isHost && filled in setup.ruleset.players && seats.all { it.open || it.isReady } && setup.problems.isEmpty()

    fun toggleReady() = if (seats[me].open) this else update(me) { it.copy(ready = !it.ready) }

    /** Host only: a bot takes an open seat, or changes level. */
    fun placeBot(i: Int, level: BotLevel) =
        if (!isHost || i == 0 || !(seats[i].open || seats[i].isBot)) this else update(i) { LobbySeat(bot = level) }

    /** Host only: frees a bot's seat (people leave on their own). */
    fun removeBot(i: Int) = if (!isHost || !seats[i].isBot) this else update(i) { LobbySeat() }

    /** A person takes the first open seat; returns null when the room is full. */
    fun join(name: String, photo: String? = null): Lobby? {
        val i = seats.indexOfFirst { it.open }.takeIf { it > 0 } ?: return null
        return update(i) { LobbySeat(name = name, photo = photo) }
    }

    /** The settings the game will start with: lobby seats decide who is a friend, bot or empty. */
    fun gameSetup(): GameSetup = setup.copy(
        seats = seats.mapIndexed { i, s ->
            when {
                i == 0 -> SeatSetup(SeatKind.FRIEND)
                s.isBot -> SeatSetup(SeatKind.BOT, s.bot!!)
                s.open -> SeatSetup(SeatKind.EMPTY)
                else -> SeatSetup(SeatKind.FRIEND)
            }
        },
    )

    private fun update(i: Int, f: (LobbySeat) -> LobbySeat) = copy(seats = seats.toMutableList().also { it[i] = f(it[i]) })

    companion object {
        /** No 0/O, 1/I/L: codes get read aloud and typed from screenshots. */
        const val CODE_CHARS = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
        const val CODE_LENGTH = 5

        fun newCode(random: Random = Random.Default) = String(CharArray(CODE_LENGTH) { CODE_CHARS[random.nextInt(CODE_CHARS.length)] })

        fun isValidCode(code: String) = code.length == CODE_LENGTH && code.all { it in CODE_CHARS }

        /** A fresh room from the game-start settings: you in seat 0, the chosen bots placed, friend seats open. */
        fun host(setup: GameSetup, code: String = newCode()) = Lobby(
            code = code,
            setup = setup,
            seats = setup.seats.mapIndexed { i, s ->
                when {
                    i == 0 -> LobbySeat(name = setup.name, photo = setup.photo)
                    s.kind == SeatKind.BOT -> LobbySeat(bot = s.bot)
                    else -> LobbySeat()
                }
            },
        )

        /** Stub data for the lobby until #7: two friends joined, one ready. */
        fun sample() = host(GameSetup(name = "Mauro").withSeat(1, SeatKind.FRIEND).withSeat(2, SeatKind.FRIEND), code = "K7QX4")
            .let { it.join("Justin")!!.join("Erin")!! }
            .let { it.copy(seats = it.seats.toMutableList().also { s -> s[1] = s[1].copy(ready = true) }) }
    }
}
