package wgbg.net

import wgbg.engine.*
import java.util.concurrent.CompletableFuture

data class ClientView(val room: Room?, val state: GameState?, val confirmedSeq: Long,
    val pendingCount: Int, val reconnecting: Boolean, val error: Throwable? = null,
    /** Every applied action in order (confirmed, then this phone's predictions): [state] is their replay.
     *  The game screen replays new ones itself to build its turn reports; a shorter or changed list means a rollback. */
    val actions: List<Action> = emptyList())

/** Own for the lifetime of a room, across screens. UI receives local state before any I/O starts. */
class GameClient(val transport: GameTransport, val id: String, val uid: String,
    private val timing: (Long) -> Unit = {}) : AutoCloseable {
    private val driver = SyncDriver()
    private var room: Room? = null
    private var displayed: GameState? = null
    private val pending = mutableListOf<Move>()
    private val observers = mutableListOf<(ClientView) -> Unit>()
    private var subscription: Subscription? = null
    private var generation = 0L
    private var closed = false
    private var reconnecting = true
    private var error: Throwable? = null
    private data class Outgoing(val move: Move, val generation: Long, val result: CompletableFuture<Unit>)
    private val outgoing = java.util.ArrayDeque<Outgoing>()
    private var publishing = 0
    private var flushing = false
    val localApplyNanos = mutableListOf<Long>()
    @Synchronized fun view() = ClientView(room, displayed?.copy(), driver.confirmedSeq, pending.size, reconnecting, error,
        if (displayed == null) emptyList() else driver.actions + pending.map { MoveCodec.decode(it.action)!! })
    @Synchronized fun watch(observer: (ClientView) -> Unit): Subscription {
        observers.add(observer); observer(view())
        return Subscription { synchronized(this) { observers.remove(observer) } }
    }
    private fun publish() { val v = view(); observers.toList().forEach { it(v) } }
    @Synchronized fun start() { check(!closed); if (subscription == null) attach() }
    private fun attach() {
        subscription = transport.observeMoves(id, driver.confirmedSeq, ::receive, ::failedListener)
    }
    @Synchronized private fun receive(update: MoveUpdate) {
        if (closed) return
        room = update.room; reconnecting = update.fromCache; error = null
        driver.acceptDelta(update.room, update.moves)
        var conflict = false
        while (pending.isNotEmpty() && pending.first().seq <= driver.confirmedSeq) {
            val first = pending.removeAt(0)
            if (driver.move(first.seq) != first || first.seq in driver.rejected) conflict = true
        }
        if (conflict) { pending.clear(); generation++ }
        displayed = driver.state
        for (m in pending.toList()) {
            val a = MoveCodec.decode(m.action)!!
            if (!Rules.isLegal(displayed!!, a)) { pending.clear(); generation++; displayed = driver.state; break }
            Rules.applyChecked(displayed!!, a)
        }
        publish()
    }
    @Synchronized private fun failedListener(e: Throwable) {
        if (closed) return
        reconnecting = true; error = e; publish()
    }
    /** Called on lifecycle resume/network restoration. SDK listeners also reconnect automatically. */
    @Synchronized fun resume() {
        if (closed) return
        reconnecting = true; subscription?.close(); subscription = null; publish(); attach()
    }
    @Synchronized fun act(seat: Int, action: Action): CompletableFuture<Unit> {
        val t0 = System.nanoTime()
        check(!closed); val r = room ?: error("Room not loaded"); val s = displayed ?: error("Game not started")
        val owner = r.seats.getOrNull(seat)?.let { it.uid ?: if (it.bot != null) r.host else null }
        require(r.status == Status.playing && owner == uid && r.players.indexOf(seat) == s.actingPlayer)
        require(Action.isKnownType(action.typeId) && Rules.isLegal(s, action))
        val seq = driver.confirmedSeq + pending.size + 1
        val move = Move(seq, seat, uid, MoveCodec.encode(action))
        Rules.applyChecked(s, action); pending.add(move); error = null
        val result = CompletableFuture<Unit>()
        outgoing.add(Outgoing(move, generation, result))
        publishing++
        try { publish() } finally { publishing-- } // rendering precedes any I/O
        val elapsed = System.nanoTime() - t0; localApplyNanos.add(elapsed); timing(elapsed)
        flush()
        return result
    }
    private fun flush() {
        if (publishing != 0 || flushing) return
        flushing = true
        try {
            while (outgoing.isNotEmpty()) {
                val next = outgoing.removeFirst()
                if (closed || next.generation != generation) {
                    next.result.completeExceptionally(IllegalStateException("Prediction superseded")); continue
                }
                val m = next.move
                val write = try { transport.submit(id, m.seq, m.seat, m.action) } catch (e: Exception) {
                    CompletableFuture<Unit>().also { it.completeExceptionally(e) }
                }
                write.whenComplete { _, e -> synchronized(this) {
                    if (e == null) next.result.complete(Unit) else {
                        if (!closed && generation == next.generation) {
                            generation++; pending.clear(); displayed = driver.state; error = e; publish(); resume()
                        }
                        next.result.completeExceptionally(e)
                    }
                } }
            }
        } finally { flushing = false }
    }
    @Synchronized fun nextRoll(): Action {
        val r = room ?: error("Room not loaded")
        return rollFromHistory(displayed ?: error("Game not started"), driver.actions + pending.map { MoveCodec.decode(it.action)!! }, r.decks)
    }
    @Synchronized override fun close() { closed = true; subscription?.close(); subscription = null; observers.clear() }
}
