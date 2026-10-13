package wgbg.net

import wgbg.engine.*
import java.util.concurrent.CompletableFuture
import java.security.SecureRandom

data class Seat(val uid: String? = null, val name: String = "", val bot: String? = null, val open: Boolean = true, val ready: Boolean = false)
enum class Status { lobby, playing, done }
data class Room(val id: String, val host: String, val config: GameConfig, val seed: Long,
    val seats: List<Seat>, val status: Status = Status.lobby, val nextSeq: Long = 0,
    val decks: Int = 0) {
    init { require(seats.size == 4); require(seats[0].uid == host) }
    val players get() = seats.indices.filter { seats[it].uid != null || seats[it].bot != null }
}
data class Move(val seq: Long, val seat: Int, val uid: String, val action: String)
data class Snapshot(val room: Room, val moves: List<Move>)
data class MoveUpdate(val room: Room, val moves: List<Move>, val fromCache: Boolean = false)
fun interface Subscription { fun close() }
fun moveId(seq: Long) = seq.toString().padStart(10, '0')

/**
 * [name] as it should sit in a room where [taken] are already seated: trimmed, and when someone there already has it
 * (ignoring case), numbered "Erin 2", "Erin 3"... so two players never share a name on the board or in the history.
 */
fun uniqueSeatName(name: String, taken: List<String>): String {
    val base = name.trim()
    val used = taken.map { it.trim().lowercase() }.toSet()
    if (base.lowercase() !in used) return base
    return generateSequence(2) { it + 1 }.map { "$base $it" }.first { it.lowercase() !in used }
}

/** Methods complete exceptionally for conflicts/denials. Callbacks may run off the UI thread. */
interface GameTransport {
    fun signIn(): CompletableFuture<String>
    fun create(room: Room): CompletableFuture<Room>
    fun listOpen(): CompletableFuture<List<Room>>
    fun join(id: String, seat: Int, name: String): CompletableFuture<Unit>
    fun leave(id: String): CompletableFuture<Unit>
    fun editLobby(id: String, seats: List<Seat>, expectedSeats: List<Seat>? = null): CompletableFuture<Unit>
    fun start(id: String): CompletableFuture<Unit>
    fun finish(id: String): CompletableFuture<Unit>
    fun submit(id: String, seq: Long, seat: Int, action: String): CompletableFuture<Unit>
    fun observe(id: String, onSnapshot: (Snapshot) -> Unit, onError: (Throwable) -> Unit): Subscription
    fun observeMoves(id: String, afterSeq: Long, onUpdate: (MoveUpdate) -> Unit, onError: (Throwable) -> Unit): Subscription {
        var last = afterSeq
        return observe(id, { s ->
            val delta = s.moves.filter { it.seq > last }
            if (delta.isNotEmpty()) last = delta.maxOf { it.seq }
            onUpdate(MoveUpdate(s.room, delta))
        }, onError)
    }
}

/** v1 deliberately uses type names, not enum ordinals, on the wire. */
object MoveCodec {
    fun encode(a: Action): String { require(Action.isKnownType(a.typeId)); return "1:${a.type}:${a.payload}" }
    fun decode(text: String): Action? = runCatching {
        val p = text.split(':'); require(p.size == 3 && p[0] == "1")
        val kind = (ActionType.entries + CkActionType.entries).first { it.toString() == p[1] }
        val payload = p[2].toLong(); require(payload in 0..Action.PAYLOAD_MASK && p[2] == payload.toString())
        Action.of(kind, payload)
    }.getOrNull()
}

/** Incremental ordered replay. Invalid entries consume their sequence, but never mutate state. */
class SyncDriver {
    private var identity: Room? = null
    private val prefix = mutableListOf<Move>()
    private var game: Game? = null
    private val buffered = java.util.TreeMap<Long, Move>()
    val confirmedSeq: Long get() = prefix.size.toLong() - 1
    fun move(seq: Long): Move? = prefix.getOrNull(seq.toInt())
    val state: GameState? get() = game?.state?.copy()
    val actions: List<Action> get() = game?.actions ?: emptyList()
    val rejected = mutableListOf<Long>()
    /** Hot path: only process newly received entries; retain gaps until their predecessor arrives. */
    fun acceptDelta(room: Room, moves: List<Move>) {
        if (room.status == Status.lobby) return
        val key = room.copy(nextSeq = 0, status = Status.playing)
        if (identity != key) {
            identity = key; prefix.clear(); buffered.clear(); rejected.clear()
            game = Game(room.config.copy(numPlayers = room.players.size), room.seed)
        }
        moves.filter { it.seq > confirmedSeq }.forEach { buffered[it.seq] = it }
        while (true) {
            val m = buffered.remove(confirmedSeq + 1) ?: break
            applyConfirmed(room, m)
        }
    }
    private fun applyConfirmed(room: Room, m: Move) {
        val g = game!!; val a = MoveCodec.decode(m.action)
        val seat = room.seats.getOrNull(m.seat)
        val owner = seat?.uid ?: if (seat?.bot != null) room.host else null
        if (a == null || owner != m.uid || room.players.indexOf(m.seat) != Rules.mover(g.state, a) || !Rules.isLegal(g.state, a)) rejected.add(m.seq)
        else g.apply(a)
        prefix.add(m)
    }
    fun accept(snapshot: Snapshot) {
        val room = snapshot.room
        if (room.status == Status.lobby) return
        val ordered = snapshot.moves.sortedBy { it.seq }
        val key = room.copy(nextSeq = 0, status = Status.playing)
        if (identity != key || ordered.take(prefix.size) != prefix) {
            identity = key; prefix.clear(); buffered.clear(); rejected.clear()
            game = Game(room.config.copy(numPlayers = room.players.size), room.seed)
        }
        val g = game!!
        for (m in ordered.drop(prefix.size)) {
            if (m.seq != prefix.size.toLong()) break // wait for a missing snapshot entry
            applyConfirmed(room, m)
        }
    }
    /** Draw dice from accepted history, so stacked decks survive phone changes/reconnects. */
    fun nextRoll(decks: Int, rng: SecureRandom = SecureRandom()): Action {
        val s = game?.state ?: error("Game has not started")
        return rollFromHistory(s, actions, decks, rng)
    }
}

fun rollFromHistory(s: GameState, actions: List<Action>, decks: Int, rng: SecureRandom = SecureRandom()): Action {
    require(Rules.isLegal(s, Action.ROLL))
    val roll = if (decks == 0) TrueRandomDice(rng).next(s.config.citiesAndKnights) else {
        val drawn = actions.filter { it.type == ActionType.ROLL }.map {
            require(it.payload != 0L) { "Deck games require explicit dice in every roll" }
            DiceDeck.cardOf(it.a, it.b)
        }
        DiceDeck.nextFromLog(drawn, decks, rng = rng).copy(event = if (s.config.citiesAndKnights) rng.nextInt(6) else -1)
    }
    return roll.toAction()
}
