package com.mauro.wgbg

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import wgbg.ai.GreedyBot
import wgbg.engine.ActionBuffer
import wgbg.engine.ActionType
import wgbg.engine.Rules
import wgbg.net.Status
import java.util.concurrent.TimeUnit

/**
 * Online play end to end in CI (#35): two Firebase clients in one app (two named FirebaseApps, two anonymous
 * users) host and join a room on the Firebase emulators (our real firestore.rules), then play a whole game through
 * GameClient with bots deciding each side's moves. Both must end with the same state. Needs the emulators running
 * on the host machine: firebase emulators:start --only firestore,auth --project demo-wgbg (reached at 10.0.2.2).
 */
@RunWith(AndroidJUnit4::class)
class OnlineTwoClientTest {
    private val args = InstrumentationRegistry.getArguments()
    private val host = args.getString("firebaseHost") ?: "10.0.2.2"
    /** The emulators' project (CI: demo-wgbg; a dev PC may run them as workinggermangame). */
    private val project = args.getString("firebaseProject") ?: "demo-wgbg"

    private fun transport(name: String): FirestoreTransport {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val options = FirebaseOptions.Builder().setProjectId(project).setApplicationId("1:1:android:1")
            .setApiKey("emulator-only-not-a-real-key").build()
        val app = FirebaseApp.getApps(ctx).firstOrNull { it.name == name } ?: FirebaseApp.initializeApp(ctx, options, name)
        val db = FirebaseFirestore.getInstance(app).also { runCatching { it.useEmulator(host, 8080) } }
        val auth = FirebaseAuth.getInstance(app).also { runCatching { it.useEmulator(host, 9099) } }
        return FirestoreTransport(auth, db)
    }

    @Test(timeout = 600_000)
    fun twoClientsHostJoinPlayAndAgree() {
        val a = transport("host-client"); val b = transport("guest-client")
        val code = "TIDE-" + (10 + (System.nanoTime() % 89).toInt().let { if (it < 0) -it else it })
        val hostSession = LobbySession.host(a, GameSetup(name = "Host").withSeat(1, SeatKind.FRIEND), seed = 4242L, code = code).get(60, TimeUnit.SECONDS)
        val guestSession = joinFirstOpenSeat(b, GameSetup(name = "Guest"), code).get(60, TimeUnit.SECONDS)

        // Both ready (each edits only its own seat), then the host starts.
        fun waitFor(what: String, ok: () -> Boolean) {
            val end = System.currentTimeMillis() + 60_000
            while (!ok()) { check(System.currentTimeMillis() < end) { "timed out waiting for $what" }; Thread.sleep(50) }
        }
        waitFor("guest seated on the host's side") { hostSession.client.view().room?.seats?.any { it.uid == guestSession.uid } == true }
        val hostSeats = hostSession.client.view().room!!.seats
        a.editLobby(code, hostSeats.mapIndexed { i, s -> if (i == 0) s.copy(ready = true) else s }, hostSeats).get(30, TimeUnit.SECONDS)
        waitFor("host ready on the guest's side") { guestSession.client.view().room?.seats?.get(0)?.ready == true }
        val guestSeats = guestSession.client.view().room!!.seats
        b.editLobby(code, guestSeats.map { if (it.uid == guestSession.uid) it.copy(ready = true) else it }, guestSeats).get(30, TimeUnit.SECONDS)
        waitFor("guest ready on the host's side") { hostSession.client.view().room!!.seats.all { it.uid == null || it.ready } }
        a.start(code).get(30, TimeUnit.SECONDS)
        waitFor("game started on both") {
            hostSession.client.view().room?.status == Status.playing && guestSession.client.view().room?.status == Status.playing &&
                hostSession.client.view().state != null && guestSession.client.view().state != null
        }

        // Play: whoever's seat is acting moves (the host also moves for the bot seats), through GameClient.
        val seats = OnlineSeats(hostSession.client.view().room!!, hostSession.uid)
        val guestSeat = OnlineSeats(guestSession.client.view().room!!, guestSession.uid)
        val bots = List(seats.players.size) { GreedyBot(77L + it) }
        val buf = ActionBuffer()
        var moves = 0
        while (true) {
            val hv = hostSession.client.view(); val gv = guestSession.client.view()
            val s = hv.state!!
            if (s.isOver || moves >= 1500) break
            // Let both sides catch up before the next move (no predictions in flight, same confirmed log).
            if (hv.pendingCount > 0 || gv.pendingCount > 0 || hv.confirmedSeq != gv.confirmedSeq) { Thread.sleep(5); continue }
            val who = s.actingPlayer
            val session = if (who == guestSeat.me) guestSession else hostSession
            Rules.legalActions(s, buf)
            var action = bots[who].choose(s, buf)
            if (action.type == ActionType.ROLL) action = session.client.nextRoll()
            session.client.act(seats.seatOf(who), action)
            moves++
        }
        waitFor("both confirmed") {
            val hv = hostSession.client.view(); val gv = guestSession.client.view()
            hv.pendingCount == 0 && gv.pendingCount == 0 && hv.confirmedSeq == gv.confirmedSeq
        }
        val hostState = hostSession.client.view().state!!; val guestState = guestSession.client.view().state!!
        assertTrue("game should progress ($moves moves)", moves > 50)
        assertEquals("both phones must agree", hostState.stateHash(), guestState.stateHash())
        hostSession.close(); guestSession.close()
    }
}
