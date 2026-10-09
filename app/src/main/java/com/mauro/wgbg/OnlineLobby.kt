package com.mauro.wgbg

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import wgbg.net.*
import java.util.concurrent.CompletableFuture

/** AppRoot owns this session and closes it when leaving the room. */
class LobbySession(val transport: GameTransport, val setup: GameSetup, val id: String, val uid: String) : AutoCloseable {
    val client = GameClient(transport, id, uid).also { it.start() }
    fun resume() = client.resume()
    override fun close() = client.close()
    fun model(room: Room): Lobby = Lobby(id, setup.copy(decks = room.decks,
        ruleset = Ruleset.entries.first { it.expansion == room.config.expansion }, points = room.config.vpToWin),
        room.seats.map { LobbySeat(it.name.takeIf { _ -> it.uid != null }, it.bot?.let(BotLevel::valueOf), it.ready, open = it.open) },
        me = room.seats.indexOfFirst { it.uid == uid }.coerceAtLeast(0), host = room.host == uid)
    fun onChange(lobby: Lobby, room: Room): CompletableFuture<Unit> = transport.editLobby(id,
        lobby.seats.mapIndexed { i, s -> room.seats[i].copy(bot = s.bot?.name, ready = s.ready) }, room.seats)
    companion object {
        fun host(transport: GameTransport, setup: GameSetup, seed: Long, code: String = Lobby.newCode()): CompletableFuture<LobbySession> =
            transport.signIn().thenCompose { uid ->
                val lobby = Lobby.host(setup, code)
                val seats = lobby.seats.mapIndexed { i, s -> Seat(if (i == 0) uid else null, s.name ?: "", s.bot?.name, s.open, s.ready) }
                transport.create(Room(code, uid, setup.config(), seed, seats, decks = setup.decks))
                    .thenApply { LobbySession(transport, setup, code, uid) }
            }
        fun join(transport: GameTransport, setup: GameSetup, code: String, seat: Int): CompletableFuture<LobbySession> =
            transport.signIn().thenCompose { uid -> transport.join(code, seat, setup.name).thenApply { LobbySession(transport, setup, code, uid) } }
    }
}

/** Existing tabletop screen with the transport behind its onChange seam. */
@Composable
fun OnlineLobbyScreen(session: LobbySession, onPlaying: (ClientView) -> Unit, onSettings: () -> Unit,
    onError: (Throwable) -> Unit, modifier: Modifier = Modifier) {
    var view by remember(session) { mutableStateOf(session.client.view()) }
    val playing by rememberUpdatedState(onPlaying); val error by rememberUpdatedState(onError)
    DisposableEffect(session) {
        val sub = session.client.watch {
            view = it
            it.error?.let(error)
            if (it.room?.status == Status.playing) playing(it)
        }
        onDispose { sub.close() }
    }
    view.room?.let { room -> LobbyScreen(session.model(room),
        { session.onChange(it, room).whenComplete { _, e -> if (e != null) error(e) } },
        { session.transport.start(session.id).whenComplete { _, e -> if (e != null) error(e) } }, onSettings, modifier,
        reconnecting = view.reconnecting) }
}

/** Place beside the game HUD; never disables gameplay or opens a blocking dialog. */
@Composable
fun OnlineConnectionStatus(view: ClientView, modifier: Modifier = Modifier) {
    if (view.reconnecting) androidx.compose.material3.Text("Reconnecting…", style = Wood.sans(12.sp, color = Wood.INK_MUTED), modifier = modifier)
}
