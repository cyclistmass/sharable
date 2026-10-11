package com.mauro.wgbg

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.key
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import wgbg.net.ClientView
import wgbg.net.Status
import kotlinx.coroutines.delay
import wgbg.ai.EasyBot
import wgbg.ai.GreedyBot
import wgbg.ai.Bot
import wgbg.engine.Action
import wgbg.engine.ActionBuffer
import wgbg.engine.ActionKind
import wgbg.engine.ActionType
import wgbg.engine.Board
import wgbg.engine.DiceDeck
import wgbg.engine.DiceMode
import wgbg.engine.GameConfig
import wgbg.engine.GameState
import wgbg.engine.Phase
import wgbg.engine.Piece
import wgbg.engine.Port
import wgbg.engine.Resource
import wgbg.engine.Rules
import kotlin.math.min
import kotlin.math.sqrt

class MainActivity : ComponentActivity() {
    /** Debug builds only: start with a given view, e.g. `am start ... --ef zoom 2 --ef tilt 0.8` (for screenshots). */
    private fun debugCamera(): Camera {
        if (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE == 0) return Camera.FIT
        if (intent.flags and android.content.Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) return Camera.FIT // stale extras from recents
        return Camera(intent.getFloatExtra("zoom", 1f).coerceIn(Camera.MIN_ZOOM, Camera.MAX_ZOOM), Offset.Zero,
            intent.getFloatExtra("tilt", 0f).coerceIn(0f, Camera.MAX_TILT))
    }

    /** Debug builds: moves sent by test drivers (`am broadcast -a com.mauro.wgbg.MOVE --es move TYPE:PAYLOAD`). */
    private val debugMoves = object : android.content.BroadcastReceiver() {
        override fun onReceive(c: android.content.Context, i: android.content.Intent) {
            val text = i.getStringExtra("move") ?: return
            val action = AgentHooks.parse(text)
            if (action == null) android.util.Log.w(AgentHooks.TAG, "bad move '$text' (want TYPE:PAYLOAD, e.g. ROLL:0)")
            else AgentHooks.sink?.invoke(action) ?: android.util.Log.w(AgentHooks.TAG, "no game is waiting for a move")
        }
    }

    override fun onDestroy() {
        if (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) runCatching { unregisterReceiver(debugMoves) }
        super.onDestroy()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashReport.install(this)
        if (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0)
            androidx.core.content.ContextCompat.registerReceiver(this, debugMoves, android.content.IntentFilter(AgentHooks.ACTION),
                androidx.core.content.ContextCompat.RECEIVER_EXPORTED)
        enableEdgeToEdge()
        // Full screen: draw under the camera cutout and hide the system bars (swipe to peek).
        window.attributes = window.attributes.apply {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(background = Color.Black, surface = Color.Black)) {
                // Debug builds: `--es start chivalry|base` jumps straight into a game (CI monkey tests).
                // Debug extras only count for a fresh launch: Android relaunches a task from recents (even after a
                // reboot) with its ORIGINAL intent, which once silently brought back autoplay in a hand-played game.
                val fromHistory = intent.flags and android.content.Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0
                val debug = applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0 && !fromHistory
                val start = if (debug) intent.getStringExtra("start") else null
                // Debug builds: `--es firebase emu` plays online against the Firebase emulators on the dev PC.
                val firebaseEmu = debug && intent.getStringExtra("firebase") == "emu"
                // Debug builds: `--ez autoplay true` lets a bot play your seat in online games (two-phone sims, #35).
                val autoplay = debug && intent.getBooleanExtra("autoplay", false)
                // Debug builds: `--ez autosetup true` lets a bot place your opening pieces (scripted UI tests).
                val autoSetup = debug && intent.getBooleanExtra("autosetup", false)
                // Debug builds: `--ez watch true` starts in Watch mode at Max speed (CI self-play, #46).
                val watch = debug && intent.getBooleanExtra("watch", false)
                // Debug builds: `--ez crash true` crashes on purpose to test the crash reporter.
                val crashTest = debug && intent.getBooleanExtra("crash", false)
                // Debug builds: `--ei players 3` starts with that many players (you plus bots), like a 3-player setup.
                val players = if (debug) intent.getIntExtra("players", 0) else 0
                // An invite link (wgbg://join/ROOM-42) opens the Join screen with that code filled in.
                val inviteCode = joinCodeFrom(intent.dataString)
                AppRoot(debugCamera(), start, firebaseEmu, autoplay, autoSetup, watch, crashTest, players, inviteCode)
            }
        }
    }
}

/** Painted tile faces (Mauro's ChatGPT art) by resource; the desert last. */
private val TILE_FACES = listOf(R.drawable.tile_timber, R.drawable.tile_clay, R.drawable.tile_fleece, R.drawable.tile_grain, R.drawable.tile_stone)
private val TILE_DESERT = R.drawable.tile_desert
/** The original painted faces with their coloured frame (Tile edges: Classic frame). */
private val TILE_FACES_FRAMED = listOf(R.drawable.tile_timber_framed, R.drawable.tile_clay_framed, R.drawable.tile_fleece_framed, R.drawable.tile_grain_framed, R.drawable.tile_stone_framed)
private val TILE_DESERT_FRAMED = R.drawable.tile_desert_framed

private val KNIGHTS_PIRATE = listOf(
    R.drawable.knight_pirate_l1_inactive, R.drawable.knight_pirate_l1_active, R.drawable.knight_pirate_l2_inactive,
    R.drawable.knight_pirate_l2_active, R.drawable.knight_pirate_l3_inactive, R.drawable.knight_pirate_l3_active,
)
private val KNIGHTS_NORMAL = listOf(
    R.drawable.knight_normal_l1_inactive, R.drawable.knight_normal_l1_active, R.drawable.knight_normal_l2_inactive,
    R.drawable.knight_normal_l2_active, R.drawable.knight_normal_l3_inactive, R.drawable.knight_normal_l3_active,
)

private val KNIGHT_CARDS_NORMAL = listOf(R.drawable.card_knight_normal_1, R.drawable.card_knight_normal_2, R.drawable.card_knight_normal_3)
private val KNIGHT_CARDS_PIRATE = listOf(R.drawable.card_knight_pirate_1, R.drawable.card_knight_pirate_2, R.drawable.card_knight_pirate_3)

/** How thick a tile stands, in hex sides (frame option B). */
private const val TILE_THICKNESS = 0.26f

private val TERRAIN_COLORS = mapOf(
    Resource.WOOD to Color(0xFF2E7D32),
    Resource.BRICK to Color(0xFFBF5B30),
    Resource.SHEEP to Color(0xFF9CCC65),
    Resource.WHEAT to Color(0xFFF2C94C),
    Resource.ORE to Color(0xFF6F84A3),
    Resource.NONE to Color(0xFFE8D9A8),
)

internal val PLAYER_COLORS = listOf(Color(0xFFD32F2F), Color(0xFF1E63C6), Color(0xFFF57C00), Color(0xFFF5F5F5))
internal val PLAYER_NAMES = listOf("Red", "Blue", "Orange", "White")
private val INK = Color(0xFF2B2118)

/** Natural light wood for number and port tokens; stained like the pieces but barely tinted. */
private val TOKEN_WOOD = Color(0xFFE9CFA0)
private val TOKEN_WOOD_INDEX = PLAYER_COLORS.size

/** Speed setting meaning "no delays at all": moves run back to back, games auto-restart. */
internal const val MAX_SPEED = 16

/**
 * Milliseconds the game waits after a move of [type] at [speed] (below Max). A roll always waits for its
 * dice to land on the panel, at every speed: otherwise the next turn's panel showed while they were in the air.
 */
internal fun pauseAfter(type: wgbg.engine.ActionKind, speed: Int): Long = when (type) {
    ActionType.END_TURN, ActionType.REJECT_TRADE -> 0L
    ActionType.ROLL -> maxOf(500L / speed, (DiceSprites.FALL_S * 1000 * DiceSprites.HANDOVER).toLong() + 150L)
    else -> 350L / speed
}

private fun Color.shade(f: Float) = lerp(this, Color.Black, f)
private fun Color.tint(f: Float) = lerp(this, Color.White, f)

private fun polygon(points: List<Offset>) = Path().apply {
    moveTo(points[0].x, points[0].y)
    for (i in 1 until points.size) lineTo(points[i].x, points[i].y)
    close()
}

/** Pips under a number token: how many dice combinations roll it (2/12 → 1, 6/8 → 5). */
private fun pips(n: Int) = 6 - kotlin.math.abs(7 - n)

private enum class Screen { START, LOBBY, GAME, JOIN, ONLINE_LOBBY, ONLINE_GAME }

/**
 * Game start (#3) -> the game, or online: host a room (lobby #19) or join one by code -> the online game (#7).
 * The in-game Exit goes back to game start (leaving the room).
 */
@Composable
fun AppRoot(initialCamera: Camera = Camera.FIT, start: String? = null, firebaseEmu: Boolean = false, autoplay: Boolean = false, autoSetup: Boolean = false, watch: Boolean = false,
            crashTest: Boolean = false, players: Int = 0, inviteCode: String? = null) {
    var screen by rememberSaveable { mutableStateOf(if (start != null) Screen.GAME else if (inviteCode != null) Screen.JOIN else Screen.START) }
    var setup by remember {
        val base = if (start == "chivalry") GameSetup().withRuleset(Ruleset.CHIVALRY) else GameSetup()
        // `--ei players N` (debug): you plus N-1 bots, the other seats empty.
        mutableStateOf(if (players in 2..GameSetup.MAX_SEATS) base.copy(seats = List(GameSetup.MAX_SEATS) { i ->
            when { i == 0 -> base.seats[0]; i < players -> SeatSetup(SeatKind.BOT); else -> SeatSetup(SeatKind.EMPTY) } }) else base)
    }
    var lobby by remember { mutableStateOf<Lobby?>(null) }
    var gameNo by rememberSaveable { mutableIntStateOf(0) }
    val context = LocalContext.current.applicationContext
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    // Created on first online use only: offline play never touches Firebase.
    val transport = remember { lazy { LobbyRepository.create(context, if (firebaseEmu) "10.0.2.2" else null) } }
    var session by remember { mutableStateOf<LobbySession?>(null) }
    var problem by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    // The room this phone sits in, kept across restarts: Android may kill a backgrounded app, and a player
    // must be able to get back into a live game (playtest: the app came back on the start screen, game lost).
    val savedRoom = remember { context.getSharedPreferences("online", android.content.Context.MODE_PRIVATE) }
    fun enterRoom(s: LobbySession) {
        session = s; savedRoom.edit().putString("room", s.id).putString("name", setup.name).putLong("at", System.currentTimeMillis()).apply()
        screen = Screen.ONLINE_LOBBY
    }
    fun failed(e: Throwable) { busy = false; problem = onlineProblem(e) }
    fun leaveRoom() {
        session?.let { s ->
            if (!s.client.view().let { it.room?.host == s.uid } && s.client.view().room?.status == Status.lobby) s.transport.leave(s.id)
            s.close()
        }
        savedRoom.edit().remove("room").apply()
        session = null; screen = Screen.START
    }
    // Back after the app was closed or killed while in a room: rejoin it (the game catches up from its moves).
    // Only a recent room (ROOM_REJOIN_MS): an old one is forgotten, because dropping into a long-dead room looks
    // like a game that never gets past the first roll (it waits on players who are gone).
    LaunchedEffect(Unit) {
        val code = savedRoom.getString("room", null) ?: return@LaunchedEffect
        if (start != null || session != null || inviteCode != null) return@LaunchedEffect
        if (!rejoinRecent(savedRoom.getLong("at", 0L), System.currentTimeMillis())) { savedRoom.edit().remove("room").apply(); return@LaunchedEffect }
        setup = setup.copy(name = savedRoom.getString("name", null) ?: setup.name)
        busy = true; problem = "Rejoining room $code…"
        LobbySession.rejoin(transport.value, setup, code).whenComplete { r, e ->
            scope.launch {
                busy = false
                if (e != null) { savedRoom.edit().remove("room").apply(); problem = "Couldn't rejoin $code: ${onlineProblem(e)}" }
                // A finished game isn't worth going back to.
                else if (r.client.view().room?.status == Status.done) { r.close(); savedRoom.edit().remove("room").apply(); problem = null }
                else { problem = null; enterRoom(r) }
            }
        }
    }
    // While in a room, keep its "last here" time fresh so a long game is still rejoined after Android closes the app.
    LaunchedEffect(screen) {
        while (screen == Screen.ONLINE_LOBBY || screen == Screen.ONLINE_GAME) {
            savedRoom.edit().putLong("at", System.currentTimeMillis()).apply()
            kotlinx.coroutines.delay(5 * 60 * 1000L)
        }
    }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { session?.close() } }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    androidx.compose.runtime.DisposableEffect(lifecycle) {
        // Back from the background (or a dropped connection): catch up from the last confirmed move.
        val o = androidx.lifecycle.LifecycleEventObserver { _, ev -> if (ev == androidx.lifecycle.Lifecycle.Event.ON_RESUME) session?.resume() }
        lifecycle.addObserver(o); onDispose { lifecycle.removeObserver(o) }
    }
    // After a crash: show the saved report first (QR, Send, Share), then carry on as normal.
    val ctx = LocalContext.current
    var crash by remember { mutableStateOf(CrashReport.pending(ctx)) }
    crash?.let { report -> CrashReportScreen(report) { crash = null }; return }
    // Debug builds: `--ez crash true` throws a test crash to exercise the reporter.
    if (crashTest) LaunchedEffect(Unit) { kotlinx.coroutines.delay(1500); throw IllegalStateException("test crash (debug --ez crash true)") }
    // The intro fly-over, the first time the app opens (never over a debug start or a rejoin).
    var intro by remember { mutableStateOf(start == null && !Intro.seen(ctx)) }
    if (intro && screen == Screen.START) { IntroScreen { intro = false }; return }
    Box(Modifier.fillMaxSize()) {
        // Which build this is, for testers (bumped with each major merge).
        if (screen == Screen.START) Text("v${appVersion()}", color = Color(0xFFD9B865), fontSize = 12.sp,
            modifier = Modifier.align(Alignment.BottomEnd).zIndex(1f).padding(10.dp).testTag("app-version"))
        when (screen) {
            Screen.START -> NewGameScreen(
                onStart = { setup = it; gameNo++; screen = Screen.GAME },
                initial = setup,
                onOpenLobby = { s ->
                    setup = s; busy = true; problem = null
                    LobbySession.host(transport.value, s, java.security.SecureRandom().nextLong()).whenComplete { r, e ->
                        scope.launch { if (e != null) failed(e) else { busy = false; enterRoom(r) } }
                    }
                },
                onJoinRoom = { s -> setup = s; problem = null; screen = Screen.JOIN },
            )
            Screen.JOIN -> {
                BackHandler { screen = Screen.START }
                JoinRoomScreen(setup.name, busy, initialCode = inviteCode ?: "", onJoin = { code, name ->
                    setup = setup.copy(name = name); busy = true; problem = null
                    joinFirstOpenSeat(transport.value, setup, code).whenComplete { r, e ->
                        scope.launch { if (e != null) failed(e) else { busy = false; enterRoom(r) } }
                    }
                }, onBack = { screen = Screen.START })
            }
            Screen.LOBBY -> lobby?.let { l ->
                BackHandler { screen = Screen.START }
                LobbyScreen(l, onChange = { lobby = it }, onStart = { setup = it; gameNo++; screen = Screen.GAME }, onSettings = { screen = Screen.START })
            } ?: run { screen = Screen.START }
            Screen.ONLINE_LOBBY -> session?.let { s ->
                BackHandler { leaveRoom() }
                OnlineLobbyScreen(s, onPlaying = { if (screen == Screen.ONLINE_LOBBY) { gameNo++; screen = Screen.ONLINE_GAME } },
                    onSettings = { leaveRoom() }, onError = { e -> scope.launch { problem = onlineProblem(e) } })
            } ?: run { screen = Screen.START }
            Screen.ONLINE_GAME -> session?.let { s ->
                key(gameNo) {
                    WatchGameScreen(Modifier.fillMaxSize().background(Color.Black), initialCamera, setup, onExit = { leaveRoom() }, online = s, autoplay = autoplay)
                }
            } ?: run { screen = Screen.START }
            Screen.GAME -> key(gameNo) {
                // Back asks "Leave this game?" inside the game screen (same as Exit).
                WatchGameScreen(Modifier.fillMaxSize().background(Color.Black), initialCamera, setup, onExit = { screen = Screen.START }, autoSetup = autoSetup, startWatching = watch)
            }
        }
        problem?.let { msg ->
            Row(Modifier.align(Alignment.BottomCenter).padding(12.dp).brassEdgedWood().padding(horizontal = 14.dp, vertical = 8.dp).testTag("online-problem"),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(msg, color = Color(0xFFFFB4A0), fontSize = 14.sp)
                WoodButton("OK", "online-problem-ok") { problem = null }
            }
        }
    }
}

/** Joins room [code] in its first open seat (seats 1-3, in order). */
internal fun joinFirstOpenSeat(transport: wgbg.net.GameTransport, setup: GameSetup, code: String, seat: Int = 1): java.util.concurrent.CompletableFuture<LobbySession> =
    LobbySession.join(transport, setup, code, seat).handle { s, e -> s to e }.thenCompose { (s, e) ->
        // Only "this seat can't be taken" (the transport's checks) moves on to the next seat; anything else
        // (no such room, permission, network) is the real answer and is reported as it is.
        val seatRefused = e != null && generateSequence(e) { it.cause }.last() is IllegalArgumentException
        if (e != null) runCatching { android.util.Log.w("WGBG-ONLINE", "join $code seat $seat failed", e) } // no Log in JVM tests
        when {
            e == null -> java.util.concurrent.CompletableFuture.completedFuture(s)
            !seatRefused -> java.util.concurrent.CompletableFuture.failedFuture(e)
            seat < GameSetup.MAX_SEATS - 1 -> joinFirstOpenSeat(transport, setup, code, seat + 1)
            // No seat to take: maybe it's because you already sit there (back after the app was closed).
            else -> LobbySession.rejoin(transport, setup, code)
        }
    }

private const val ROOM_MISSING = "No room with that code"

/** A short, human message for a failed online step. */
internal fun onlineProblem(e: Throwable): String {
    val c = generateSequence(e) { it.cause }.last()
    val m = c.message.orEmpty()
    return when {
        "Unknown room" in m -> ROOM_MISSING
        "PERMISSION_DENIED" in m || "permission" in m.lowercase() -> "The server said no (permission denied)"
        "UNAVAILABLE" in m || "offline" in m.lowercase() || c is java.io.IOException -> "Can't reach the server. Check the connection."
        "full or has started" in m -> m
        m.isNotBlank() -> m.take(120)
        else -> c.javaClass.simpleName
    }
}

/**
 * The game. With a [setup] you play seat 0 against bots (friends play as bots until online play
 * lands, #7); the menu can switch to watching the bots. Age of Chivalry is watch-only until its
 * screens exist (#4).
 */
@Composable
fun WatchGameScreen(
    modifier: Modifier = Modifier,
    initialCamera: Camera = Camera.FIT,
    setup: GameSetup = GameSetup(),
    onExit: () -> Unit = {},
    /** An online room (#7): its moves drive the game, yours go through it, and the host's phone plays its bots. */
    online: LobbySession? = null,
    /** Debug: a bot plays your seat in an online game (two-phone simulations). */
    autoplay: Boolean = false,
    /** Debug: a bot places your opening homesteads and roads, then you play. */
    autoSetup: Boolean = false,
    /** Debug: start in Watch mode at Max speed (bots in every seat, games back to back). */
    startWatching: Boolean = false,
) {
    var seed by remember { mutableLongStateOf(System.nanoTime() and 0xffffffL) }
    // Who sits where in an online room (frozen once it starts); null offline.
    val net = remember(online) { online?.let { s -> s.client.view().room?.let { OnlineSeats(it, s.uid) } } }
    // Play mode: you are seat 0 ("You"); Watch mode: bots in every seat at full speed. Online: your seat.
    var human by remember { mutableStateOf(!startWatching) }
    val me = net?.me ?: if (human) 0 else -1
    val names = net?.names ?: if (human) listOf("You") + PLAYER_NAMES.drop(1) else PLAYER_NAMES
    val seats = net?.players?.size ?: setup.players.size
    // Online, a rejected move rolls the room back: the screen then rebuilds its game from the room's moves.
    var epoch by remember { mutableIntStateOf(0) }
    val state = remember(seed, seats, epoch) { if (net != null) Rules.newGame(net.config, net.seed) else Rules.newGame(setup.config(), seed) }
    var onlineView by remember { mutableStateOf<ClientView?>(null) }
    // Each playing seat's bot: the level picked on the game-start screen (friends play as Greedy until #7;
    // Strong plays as Greedy until #6). In Watch mode seat 0 gets a Greedy bot too.
    val bots = remember(seed, seats) {
        if (net != null) List(seats) { i -> if (net.bot(i) == BotLevel.EASY) EasyBot(net.seed * 31 + i) else GreedyBot(net.seed * 31 + i) }
        else setup.players.mapIndexed { i, seat ->
            val s = setup.seats[seat]
            val r = seed * 31 + i
            if (s.kind == SeatKind.BOT && s.bot == BotLevel.EASY) EasyBot(r) else GreedyBot(r)
        }
    }
    val log = remember(seed, seats) { mutableStateListOf<String>() }
    val tracker = remember(seed, seats, human, epoch) { TurnTracker(names) }
    // Moves from the human's taps and buttons; the game loop checks each against the rules.
    val humanMoves = remember { Channel<Action>(Channel.UNLIMITED) }
    var tradeOpen by remember { mutableStateOf(false) }
    // A tapped board move waits here for Confirm (or a second tap on the same spot).
    var pending by remember { mutableStateOf<List<Action>>(emptyList()) }
    val responses = remember(seed, seats) { mutableStateMapOf<Int, String>() }
    // Dice setting cycles: random, then deck stacks of 1-4 x 36 cards (asked per game once the new-game screen exists).
    var diceSetting by remember { mutableIntStateOf(setup.decks) } // 0 = random, n = n decks
    val dice = remember(diceSetting) { if (diceSetting == 0) DiceDeck.of(DiceMode.RANDOM) else DiceDeck.of(DiceMode.DECK, diceSetting) }
    var tick by remember { mutableIntStateOf(0) }
    var playing by remember { mutableStateOf(true) }
    var speed by remember { mutableIntStateOf(if (human) 2 else MAX_SPEED) }
    val wins = remember { mutableStateListOf(0, 0, 0, 0, 0) } // per seat, last slot = draws
    var inventory by remember { mutableStateOf(false) }
    /** Watching bots: stop on the finished board instead of going straight into the next game (#13). */
    var oneGame by remember { mutableStateOf(false) }
    var compareTiles by remember { mutableStateOf(false) }
    // Scenery settings (density and motion), kept across restarts; the sea reads them once it supports them.
    val appContext = LocalContext.current.applicationContext
    val env = remember { EnvSettings(PrefsStore(appContext), viewBarsDefault = EnvSettings.isEmulator()) }
    val sound = remember { SoundSettings(PrefsStore(appContext)) }
    // One sea for the whole session: it depends only on the board's shape, and at Max speed a new
    // game starts several times a second, which would otherwise wipe splashes and clouds.
    val oceanRes = LocalContext.current.resources
    val cliff = listOf(R.drawable.cliff_top_1, R.drawable.cliff_top_2, R.drawable.cliff_top_3).map { ImageBitmap.imageResource(it) }
    val raiders = ImageBitmap.imageResource(R.drawable.raiders_ship)
    // Retain the list; cover a screen-wide sprite, including inventory and zoom.
    val screen = androidx.compose.ui.platform.LocalConfiguration.current
    // Decoded in the background: until all 8 tilt steps are ready the drawn robber stands in (never a partial set).
    val banditSprites = rememberDiceFrames(BANDIT_SPRITES, maxOf(screen.screenWidthDp, screen.screenHeightDp)).takeIf { it.size == BANDIT_SPRITES.size }
    // Knight art per theme: index (level - 1) * 2 + (active ? 1 : 0).
    val knightsPirate = KNIGHTS_PIRATE.map { ImageBitmap.imageResource(it) }
    val knightsNormal = KNIGHTS_NORMAL.map { ImageBitmap.imageResource(it) }
    val plainFaces = TILE_FACES.withIndex().associate { (r, id) -> r to ImageBitmap.imageResource(id) } + (Resource.NONE to ImageBitmap.imageResource(TILE_DESERT))
    val framedFaces = TILE_FACES_FRAMED.withIndex().associate { (r, id) -> r to ImageBitmap.imageResource(id) } + (Resource.NONE to ImageBitmap.imageResource(TILE_DESERT_FRAMED))
    val tileFaces = if (env.tileEdge == EnvSettings.TileEdge.CLASSIC) framedFaces else plainFaces
    val tileLine = env.tileEdge == EnvSettings.TileEdge.THIN
    // Rendered buildings (#41) for the chosen theme; Age of Chivalry pieces only load in those games.
    val buildings = remember(env.pirateTheme, state.ck != null) { BuildingSprites(oceanRes, env.pirateTheme, state.ck != null) }
    val gullFlap = ImageBitmap.imageResource(R.drawable.gull_flap)
    val gullGlide = ImageBitmap.imageResource(R.drawable.gull_glide)
    val gullShadow = ImageBitmap.imageResource(R.drawable.gull_shadow)
    val boatWake = ImageBitmap.imageResource(R.drawable.boat_wake)
    // Boat sails in the players' colours (Red, Blue, Orange, White), then the extra two.
    val boatSprites = listOf(R.drawable.boat_red, R.drawable.boat_blue, R.drawable.boat_orange, R.drawable.boat_white, R.drawable.boat_green, R.drawable.boat_brown)
        .map { ImageBitmap.imageResource(it) }
    val sea = remember { Sea(state.board).also { it.oceanBitmap = BitmapFactory.decodeResource(oceanRes, R.drawable.ocean_tile); it.boatColors = PLAYER_COLORS } }
    sea.gullFlap = gullFlap; sea.gullGlide = gullGlide; sea.gullShadow = gullShadow; sea.boatWake = boatWake; sea.boatSprites = boatSprites
    // Gesture handlers live across games (a restart would cancel a tap mid-gesture), so they
    // read the current game's sea and board through these.
    val seaNow by rememberUpdatedState(sea)
    val boardNow by rememberUpdatedState(state.board)
    val stateNow by rememberUpdatedState(state)
    // A short note when a board tap did nothing for a reason worth saying (shown in place of the prompt).
    var tapHint by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(tapHint) { if (tapHint != null) { delay(2500); tapHint = null } }
    var seaTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(sea) { // the sea keeps moving even when the game is paused
        var last = 0L
        while (true) withFrameNanos { now ->
            sea.clouds01 = env.clouds; sea.birds01 = env.birds; sea.boats01 = env.boats; sea.waves01 = env.waves; sea.density01 = env.density
            // Clamp both ends: on some phones (Pixel 9/10, Android 17) the frame clock can step back a little,
            // and a negative step crashed the gulls' turn limit (crash reports 2026-10-11).
            if (last != 0L) sea.update(((now - last) / 1e9f).coerceIn(0f, 0.05f))
            last = now
            seaTick++
        }
    }

    // Answers to your trade offers, shown as marks by each name in the trade panel.
    fun noteResponse(who: Int, action: Action) {
        if (me >= 0 && state.currentPlayer == me) when {
            action.type == ActionType.OFFER_TRADE || action.type == ActionType.END_TURN -> responses.clear()
            who != me && state.phase == Phase.DECIDE_TRADE -> when (action.type) {
                ActionType.ACCEPT_TRADE -> responses[who] = "✓"
                ActionType.REJECT_TRADE -> responses[who] = "✗"
                ActionType.COUNTER_TRADE -> responses[who] = "⇄"
                else -> {}
            }
            state.phase == Phase.DECIDE_COUNTER && action.type == ActionType.REJECT_TRADE -> responses[state.counterFrom] = "✗"
        }
    }
    fun logMove(who: Int, action: Action) {
        CrashReport.note("turn ${state.turn} ${state.phase} p$who ${action.type}:${action.payload}")
        when (action.type) {
            ActionType.REJECT_TRADE, ActionType.ACCEPT_TRADE, ActionType.COUNTER_TRADE, ActionType.END_TURN, ActionType.OFFER_TRADE -> {}
            ActionType.ROLL -> log.add(0, "${names[who]} rolled ${state.lastRoll}")
            else -> log.add(0, "${names[who]}: ${describe(action.type)}")
        }
        if (log.size > 4) log.removeRange(4, log.size)
    }

    LaunchedEffect(seed, seats, playing, speed, human) {
        if (online != null) return@LaunchedEffect // the room drives an online game (below)
        val buf = ActionBuffer()
        while (humanMoves.tryReceive().isSuccess) { } // drop taps left over from another game
        suspend fun step(): ActionKind {
            Rules.legalActions(state, buf)
            val who = state.actingPlayer
            val placing = state.phase == Phase.BUILD_INITIAL_SETTLEMENT || state.phase == Phase.BUILD_INITIAL_ROAD
            var action = if (who == me && !(autoSetup && placing)) {
                tick++ // show the human what they can do
                var a: Action
                do a = humanMoves.receive() while (!Rules.isLegal(state, a))
                a
            } else bots[who].choose(state, buf)
            // Real dice: thrown by the OS's secure generator (or dealt from the deck) and recorded in the log.
            if (action.type == ActionType.ROLL) action = dice.next(withEvent = state.ck != null).toAction()
            noteResponse(who, action)
            tracker.apply(state, who, action) { Rules.apply(state, action) }
            logMove(who, action)
            return action.type
        }
        if (speed == MAX_SPEED) {
            // Full speed: as many moves as fit in ~12 ms, then let the frame draw.
            while (playing && !state.isOver) {
                withFrameNanos { }
                val start = System.nanoTime()
                while (!state.isOver && System.nanoTime() - start < 12_000_000) step()
                tick++
            }
        } else {
            while (playing && !state.isOver) {
                val type = step()
                tick++
                if (pauseAfter(type, speed) > 0) delay(pauseAfter(type, speed))
            }
        }
        if (state.isOver) {
            wins[if (state.winner >= 0) state.winner else 4]++
            // One line per finished game: CI self-play counts these and checks every game ends (#46).
            runCatching { android.util.Log.i("WGBG-GAME", "over ruleset=${if (state.ck != null) "chivalry" else "base"} winner=${state.winner} turns=${state.turn} hash=${state.stateHash()}") }
            if (state.winner >= 0) log.add(0, "${names[state.winner]} wins!")
            tick++
            if (speed == MAX_SPEED && playing && !human && !oneGame) seed++ // straight into the next game
        }
    }

    // Online: the room's move stream drives the game. New moves (anyone's, including this phone's own
    // predictions) are replayed here so the turn panel and dice see every move; your taps and the host's
    // bots go out through the client. A changed history (a rejected prediction) rebuilds from the room.
    LaunchedEffect(online, epoch) {
        val client = online?.client ?: return@LaunchedEffect
        val n = net ?: return@LaunchedEffect
        while (humanMoves.tryReceive().isSuccess) { }
        val views = Channel<ClientView>(Channel.CONFLATED)
        val sub = client.watch { views.trySend(it) }
        val applied = mutableListOf<Action>()
        val buf = ActionBuffer()
        fun send(who: Int, a: Action): Boolean = runCatching {
            // Real dice: drawn from the room's history (random, or its shared deck), so every phone agrees.
            client.act(n.seatOf(who), if (a.type == ActionType.ROLL) client.nextRoll() else a); true
        }.getOrElse { false }
        try {
            var v = views.receive()
            while (true) {
                onlineView = v
                if (v.actions.size < applied.size || v.actions.subList(0, applied.size) != applied) {
                    runCatching { android.util.Log.w("WGBG-MOVES", "${online.id} history changed at ${applied.size}: rebuilding") }
                    epoch++; return@LaunchedEffect
                }
                var rolled = false
                for (a in v.actions.subList(applied.size, v.actions.size)) {
                    val who = state.actingPlayer
                    // Every phone logs the same move list; comparing two phones' logs pins down a desync.
                    runCatching { android.util.Log.i("WGBG-MOVES", "${online.id} #${applied.size} p$who ${names[who]} ${a.type}:${a.payload}${if (applied.size > v.confirmedSeq) " (mine, unconfirmed)" else ""}") }
                    noteResponse(who, a)
                    tracker.apply(state, who, a) { Rules.apply(state, a) }
                    logMove(who, a)
                    applied += a
                    rolled = a.type == ActionType.ROLL
                }
                tick++
                if (state.isOver) {
                    if (n.isHost && v.room?.status == Status.playing) {
                        online.transport.finish(online.id)
                    }
                    // Every phone logs the same fingerprint for the same game (checked by the two-phone sims).
                    android.util.Log.i("WGBG-ONLINE", "game over room=${online.id} moves=${applied.size} winner=${state.winner} hash=${state.stateHash()}")
                    v = views.receive(); continue
                }
                val who = state.actingPlayer
                v = when {
                    who == n.me && !autoplay -> select {
                        views.onReceive { it }
                        humanMoves.onReceive { a -> if (Rules.isLegal(state, a) && send(who, a)) views.receive() else v }
                    }
                    (n.isHost && n.bot(who) != null) || (autoplay && who == n.me) -> {
                        // The host's phone plays the room's bots, at a watchable pace (dice land first).
                        delay(if (rolled) pauseAfter(ActionType.ROLL, 1) else 450L)
                        val fresh = views.tryReceive().getOrNull()
                        if (fresh != null) fresh else {
                            Rules.legalActions(state, buf)
                            if (send(who, bots[who].choose(state, buf))) views.receive() else v
                        }
                    }
                    else -> views.receive()
                }
            }
        } finally { sub.close() }
    }

    // The board fills the screen; controls live in an overlay panel toggled from the corner.
    val players = tick.let { (0 until state.numPlayers).map { PlayerInfo.of(state, it, me) } }
    var camera by remember { mutableStateOf(initialCamera) }
    // The view stays where the player puts it (Mauro, 2026-10-10: it used to glide back to the full board
    // after 6 s untouched). Menu > Fit and a double-tap still reset it; Camera.clamped keeps it on screen.
    var boardSize by remember { mutableStateOf(Size.Zero) }
    var boardOrigin by remember { mutableStateOf(Offset.Zero) }
    // Each player's supply can be slid around the water (#52); a double-tap on it puts it back. Kept per game.
    var pileOffsets by remember(seed, seats) { mutableStateOf(List(seats) { Offset.Zero }) }
    val viewer = me.coerceAtLeast(0)
    val viewerNow by rememberUpdatedState(viewer)
    val pileOffsetsNow by rememberUpdatedState(pileOffsets)
    var panelOpen by remember { mutableStateOf(false) }
    var confirmExit by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }
    var hudTopRight by remember { mutableStateOf<Offset?>(null) }
    var diceFlying by remember { mutableStateOf(false) }
    var diceSlots by remember { mutableStateOf<Pair<androidx.compose.ui.geometry.Rect, androidx.compose.ui.geometry.Rect>?>(null) }
    var eventSlot by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    // The turn panel fits under the player list (taller in Age of Chivalry), so neither hides the dice.
    var stripBottom by remember { mutableStateOf(0f) }
    var screenHeight by remember { mutableStateOf(0f) }
    val turnHudHeight = with(LocalDensity.current) {
        if (screenHeight <= 0f) 210.dp else ((screenHeight - stripBottom).toDp() - 16.dp).coerceIn(110.dp, 210.dp)
    }
    // Back closes whatever is open on top first (Settings, the menu, the Inventory), then asks about leaving.
    BackHandler {
        when {
            settingsOpen -> settingsOpen = false
            panelOpen -> panelOpen = false
            inventory -> inventory = false
            else -> confirmExit = !confirmExit
        }
    }
    // What the human may do right now (empty while bots move or in Watch mode).
    val legal = tick.let { if (me >= 0 && !inventory && !state.isOver && state.actingPlayer == me) Rules.legalActions(state) else emptyList() }
    val legalNow by rememberUpdatedState(legal)
    // Debug agent hooks (#46): announce each moment the local player must act, and take moves by broadcast.
    val debuggable = LocalContext.current.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0
    if (debuggable) {
        LaunchedEffect(tick, legal.size, boardSize, camera, boardOrigin) {
            if (legal.isEmpty()) return@LaunchedEffect
            // Screen pixel of each board move, for the touch test: the board's layout now, plus its place on screen.
            val sz = boardSize
            val l = if (sz.minDimension > 0f) Layout.fit(state.board, sz.width, sz.height, camera) else null
            AgentHooks.announce(online?.id ?: "local", state, legal, prompt(state, legal, names),
                l?.let { lay -> { a: Action -> spotOf(state.board, a)?.let { lay.project(it.x, it.y) + boardOrigin } } })
        }
        androidx.compose.runtime.DisposableEffect(Unit) {
            val mine: (Action) -> Unit = { a -> humanMoves.trySend(a) }
            AgentHooks.sink = mine
            // Only clear our own: a relaunch builds the new game before the old one is disposed.
            onDispose { if (AgentHooks.sink === mine) AgentHooks.sink = null }
        }
    }
    val targets = Targets.of(legal)
    val chosen = pending.filter { it in legal } // a stale pick (the turn moved on) is dropped
    // Turn and trade alerts (#44): a tick when it becomes your move, a chime when an offer asks for cards you
    // hold, and an offer you can't pay is declined for you after a short countdown so turns keep flowing.
    val hostView = androidx.compose.ui.platform.LocalView.current
    val sounds = remember { GameSounds(appContext) }
    androidx.compose.runtime.DisposableEffect(sounds) { onDispose { sounds.close() } }
    val myMoveKey = tick.let { if (me >= 0 && !state.isOver && state.actingPlayer == me) listOf(state.turn, state.phase) else null }
    LaunchedEffect(myMoveKey) { if (myMoveKey != null) hostView.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY) }
    val offerKey = tick.let { if (me >= 0 && tradeMode(state, me) == TradeMode.RESPOND) listOf(state.turn, state.currentPlayer, state.tradeOffer.contentHashCode()) else null }
    var autoNo by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(offerKey) {
        autoNo = null
        if (offerKey == null) return@LaunchedEffect
        hostView.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
        if (canPayOffer(state, me)) { sounds.tradeChime(sound.soundVolume); return@LaunchedEffect }
        for (left in AUTO_NO_SECONDS downTo 1) { autoNo = left; delay(1000) }
        autoNo = null
        humanMoves.trySend(Action.REJECT_TRADE)   // checked against the rules like a tap; ignored if the offer moved on
    }
    // The trade chest: open when you opened it, or when a trade waits on you (or you wait on answers to yours).
    val chestOpen = me >= 0 && !inventory && tick.let {
        (tradeOpen && tradeButtonVisible(state)) || tradeNeedsMe(state, me) || (tradeMode(state, me) == TradeMode.WAITING && state.currentPlayer == me)
    }
    Box(modifier.onGloballyPositioned { screenHeight = it.size.height.toFloat() }) {
      Box(Modifier.fillMaxSize()) { // the menu floats over the board, so the ocean always fills the screen
      Box(Modifier.fillMaxSize()) {
        // The board sits beside the player strip and turn panel (TurnHud is 360 dp wide, plus its 8 dp margins).
        Layout.leftInset = if (inventory) 0f else with(LocalDensity.current) { 376.dp.toPx() }
        val measurer = rememberTextMeasurer()
        val resources = LocalContext.current.resources
        val wood = remember { WoodPieces(resources, PLAYER_COLORS + TOKEN_WOOD) }
        // The board's still parts are recorded once and replayed every sea frame (see BoardPart).
        // Offscreen: the GPU keeps each layer's finished pixels, so an unchanged layer costs one image copy.
        val boardLayer = rememberGraphicsLayer().apply { compositingStrategy = CompositingStrategy.Offscreen }
        val boardLayerKey = remember { arrayOfNulls<BoardLayerKey>(1) }
        val waterLayer = rememberGraphicsLayer().apply { compositingStrategy = CompositingStrategy.Offscreen }
        val waterLayerKey = remember { arrayOfNulls<WaterLayerKey>(1) }
        Canvas(
            Modifier.fillMaxSize()
                .onSizeChanged { boardSize = Size(it.width.toFloat(), it.height.toFloat()) }
                .onGloballyPositioned { boardOrigin = it.positionInWindow() }
                .clipToBounds() // never draw the board under the side panel
                .pointerInput(Unit) {
                    fun lattice(p: Offset) = Layout.fit(boardNow, size.width.toFloat(), size.height.toFloat(), camera).unproject(p)
                    var dragged: Sea.Cloud? = null
                    // Supplies (#52): a press on one grabs it; it follows the finger, keeping where it was held.
                    var pile = -1
                    var pileGrip = Offset.Zero
                    var lastPileTap = 0L
                    var lastPileSeat = -1
                    fun pileAt(p: Offset): Int = supplyAt(stateNow, viewerNow, pileOffsetsNow,
                        Layout.fit(boardNow, size.width.toFloat(), size.height.toFloat(), camera), p, 48.dp.toPx())
                    boardGestures(
                        grab = { p ->
                            val seat = pileAt(p)
                            if (seat >= 0) {
                                pile = seat
                                pileGrip = lattice(p) - supplyCenter(stateNow, seat, viewerNow, pileOffsetsNow)
                                hostView.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                                true
                            } else seaNow.cloudAt(lattice(p))?.also { dragged = it } != null
                        },
                        onGrabMove = { p ->
                            if (pile >= 0) {
                                val home = supplyCenter(stateNow, pile, viewerNow, emptyList())
                                val want = lattice(p) - pileGrip - home
                                pileOffsets = pileOffsetsNow.mapIndexed { i, o -> if (i == pile) want else o }
                            } else dragged?.let { seaNow.move(it, lattice(p)) }
                        },
                        onGrabEnd = { g ->
                            if (pile >= 0) {
                                val tap = g.travel < viewConfiguration.touchSlop && g.durationMs < 300
                                val now = System.currentTimeMillis()
                                if (tap && pile == lastPileSeat && now - lastPileTap < 350) {
                                    // Double-tap in the middle of a pile: back to its place in front of its owner.
                                    pileOffsets = pileOffsetsNow.mapIndexed { i, o -> if (i == pile) Offset.Zero else o }
                                    hostView.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                                    lastPileTap = 0L
                                } else {
                                    if (!tap) hostView.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                                    lastPileTap = if (tap) now else 0L
                                    lastPileSeat = pile
                                }
                                pile = -1
                            }
                            dragged?.let { c ->
                                seaNow.release(c)
                                // Screen px/s to lattice units/s, sampled over 0.1 s so zoom and tilt are accounted for.
                                fun toLattice(v: Offset) = (lattice(g.at + v * 0.1f) - lattice(g.at)) * 10f
                                val release = toLattice(g.velocity)
                                when (CloudFeel.classify(g.travel, g.durationMs, g.velocity.getDistance(), viewConfiguration.touchSlop)) {
                                    CloudFeel.Gesture.TAP -> seaNow.poof(c, lattice(g.start))
                                    CloudFeel.Gesture.NUDGE -> {
                                        val avg = (g.at - g.start) / (g.durationMs.coerceAtLeast(16) / 1000f)
                                        toLattice(avg).let { seaNow.push(c, it.x * CloudFeel.NUDGE_GAIN, it.y * CloudFeel.NUDGE_GAIN) }
                                    }
                                    CloudFeel.Gesture.FLICK -> seaNow.fling(c, release.x * CloudFeel.FLICK_RETAIN, release.y * CloudFeel.FLICK_RETAIN)
                                    CloudFeel.Gesture.DRAG -> seaNow.fling(c, release.x * CloudFeel.DRAG_RELEASE, release.y * CloudFeel.DRAG_RELEASE)
                                }
                            }
                            dragged = null
                        },
                        onTransform = { centroid, pan, zoom ->
                            camera = camera.transform(centroid, pan, zoom, Size(size.width.toFloat(), size.height.toFloat()))
                        },
                        onTilt = { dy -> camera = camera.tilted(dy, size.height.toFloat()) },
                    )
                }
                .pointerInput(Unit) {
                    // Mouse wheel (emulator, tablets with a mouse): wheel zooms, Shift+wheel tilts.
                    awaitPointerEventScope {
                        while (true) {
                            val e = awaitPointerEvent()
                            if (e.type != PointerEventType.Scroll) continue
                            val c = e.changes.firstOrNull() ?: continue
                            val dy = c.scrollDelta.y
                            camera = if (e.keyboardModifiers.isShiftPressed) camera.tilted(-dy * 40f, size.height.toFloat())
                            else camera.transform(c.position, Offset.Zero, if (dy < 0) 1.12f else 1f / 1.12f, Size(size.width.toFloat(), size.height.toFloat()))
                            e.changes.forEach { it.consume() }
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { at ->
                        val p = Layout.fit(boardNow, size.width.toFloat(), size.height.toFloat(), camera).unproject(at)
                        val moves = if (inventory) emptyList() else movesAt(boardNow, legalNow, p)
                        when {
                            moves.isNotEmpty() && moves == pending && moves.size == 1 -> { pending = emptyList(); humanMoves.trySend(moves[0]) }
                            moves.isNotEmpty() -> pending = moves
                            pending.isNotEmpty() -> pending = emptyList() // tapping elsewhere cancels
                            !inventory && boardTapHint(boardNow, legalNow, stateNow.robberHex, p) != null -> tapHint = boardTapHint(boardNow, legalNow, stateNow.robberHex, p)
                            !inventory && seaNow.isWater(p) -> seaNow.splash(p)
                        }
                    }, onDoubleTap = { at ->
                        val sz = Size(size.width.toFloat(), size.height.toFloat())
                        // A double-tap on a supply resets that pile (handled with its drag); the view stays.
                        if (supplyAt(stateNow, viewerNow, pileOffsetsNow, Layout.fit(boardNow, sz.width, sz.height, camera), at, 48.dp.toPx()) >= 0) return@detectTapGestures
                        camera = if (camera.zoom > 1.05f || camera.tilt > 0.01f || camera.pan.getDistance() > 1f) Camera.FIT
                        else camera.transform(at, Offset.Zero, Camera.DOUBLE_TAP_ZOOM / camera.zoom, sz)
                    })
                },
        ) {
            tick // redraw on every move
            if (!inventory) seaTick // and on every sea frame (the Inventory screen holds still)
            if (inventory) drawInventory(measurer, wood, banditSprites, tileFaces, tileLine,
                InventoryArt(if (env.pirateTheme) knightsPirate else knightsNormal, raiders, cliff, boatSprites, gullGlide, buildings),
                bottomInset = INVENTORY_CARDS_DP.dp.toPx()) else {
                val knights = if (env.pirateTheme) knightsPirate else knightsNormal
                val layerDensity = androidx.compose.ui.unit.Density(density, fontScale)
                val layerSize = androidx.compose.ui.unit.IntSize(size.width.toInt(), size.height.toInt())
                val waterKey = WaterLayerKey(camera, size, (sea.time * 6f).toInt())
                if (waterLayerKey[0] != waterKey) {
                    waterLayer.record(layerDensity, layoutDirection, layerSize) { drawGame(state, measurer, wood, camera, sea, tileFaces, cliff, tileLine, knights, raiders, BoardPart.WATER, banditSprites) }
                    waterLayerKey[0] = waterKey
                }
                drawLayer(waterLayer)
                drawGame(state, measurer, wood, camera, sea, tileFaces, cliff, tileLine, knights, raiders, BoardPart.UNDER, banditSprites)
                // Re-record the still board only when something on it can have changed: a move, the
                // camera, the canvas size or a look setting. Otherwise the last recording is replayed.
                val key = BoardLayerKey(System.identityHashCode(state), tick, camera, size, tileLine, knights, tileFaces, cliff, viewer, pileOffsets, buildings, banditSprites != null)
                if (boardLayerKey[0] != key) {
                    // The explicit-density overload gives the layer its own draw scope; recording through
                    // this Canvas's scope makes the two scopes ask each other for density forever.
                    boardLayer.record(layerDensity, layoutDirection, layerSize) { drawGame(state, measurer, wood, camera, sea, tileFaces, cliff, tileLine, knights, raiders, BoardPart.STILL, banditSprites, viewer, pileOffsets, buildings) }
                    boardLayerKey[0] = key
                }
                drawLayer(boardLayer)
                drawGame(state, measurer, wood, camera, sea, tileFaces, cliff, tileLine, knights, raiders, BoardPart.OVER, banditSprites)
                if (!targets.isEmpty) drawTargets(state.board, targets, camera, targetPulse(sea.time), targetGlow(me))
                if (chosen.isNotEmpty()) drawTargets(state.board, Targets.of(chosen), camera, 1f, Color.White)
            }
        }
        // Inventory: the illustrated resource cards along the bottom, then the dice faces.
        if (inventory) Row(Modifier.align(Alignment.BottomCenter).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((r, art) in CARD_ART.withIndex()) androidx.compose.foundation.Image(
                androidx.compose.ui.res.painterResource(art), RESOURCE_NAMES[r], Modifier.size(width = 76.dp, height = 110.dp))
            // Knight cards in the chosen theme (Normal / Pirate themed).
            for (lv in 1..3) androidx.compose.foundation.Image(
                androidx.compose.ui.res.painterResource(if (env.pirateTheme) KNIGHT_CARDS_PIRATE[lv - 1] else KNIGHT_CARDS_NORMAL[lv - 1]),
                "Knight level $lv", Modifier.size(width = 76.dp, height = 110.dp))
            // Dice in the chosen theme: yellow 1-6, red 1-6, the event die's six faces.
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                val rows = listOf((1..6).map { DiceArt.face(false, env.pirateTheme, it) }, (1..6).map { DiceArt.face(true, env.pirateTheme, it) },
                    (0..5).map { DiceArt.eventFace(env.pirateTheme, it, env.eventDieStyle) })
                for (row in rows) Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    for (id in row) androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(id), null, Modifier.size(32.dp))
                }
            }
        }
        if (!inventory) {
            val report = tick.let { if (speed == MAX_SPEED) tracker.last else turnHeader(tracker, state) }
            val previous = tick.let { if (speed == MAX_SPEED) null else (if (report === tracker.current) tracker.last else tracker.current ?: tracker.last).takeIf { it !== report } }
            // The roll's dice roll across the turn HUD and rest on its top-right corner until the next roll.
            val rolled = tick.let { tracker.current?.takeIf { it.rolled } ?: tracker.last?.takeIf { it.rolled } }
            // While the trade chest is open it docks here, over the turn panel, so the board stays in view.
            if (!chestOpen) Box(Modifier.align(Alignment.BottomStart).padding(8.dp).onGloballyPositioned { hudTopRight = it.boundsInParent().topRight }) {
                TurnHud(report, previous, names, onDiceSlots = { a, b -> diceSlots = a to b }, showDice = !diceFlying, pirate = env.pirateTheme, eventStyle = env.eventDieStyle,
                    eventDie = state.ck != null, onEventSlot = { eventSlot = it }, height = turnHudHeight)
            }
            if (rolled != null) RollingDice(rolled.die1, rolled.die2, rollKey = rolled.turn * 8 + rolled.player, rest = hudTopRight, slots = diceSlots, pirate = env.pirateTheme, eventStyle = env.eventDieStyle, event = rolled.event, eventSlot = eventSlot, onFlying = { diceFlying = it },
                modifier = Modifier.fillMaxSize())
        }
        // Board A: players top-left; in Play mode the prompt across the top and your hand along the bottom.
        // The game state is mutated in place, so these get `tick` to redraw after every move.
        if (!inventory) PlayerStrip(state, me, names, tick, Modifier.align(Alignment.TopStart).padding(8.dp).onGloballyPositioned { stripBottom = it.boundsInParent().bottom },
            roll = tick.let { tracker.current?.takeIf { it.rolled } ?: tracker.last?.takeIf { it.rolled } })
        if (net != null && !inventory) onlineView?.let { v ->
            Column(Modifier.align(Alignment.TopCenter).padding(top = 52.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                OnlineConnectionStatus(v)
            }
        }
        if (me >= 0 && !inventory) tick.let {
            TurnPrompt(tapHint ?: prompt(state, legal, names), Modifier.align(Alignment.TopCenter).padding(top = 8.dp))
            if (!tradeOpen && !tradeNeedsMe(state, me)) HandRow(state, me, tick,
                // Between the left panel and the move buttons (playtest: progress cards slid under End turn).
                Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(start = 384.dp, end = 196.dp, bottom = 6.dp))
        }
        if (me >= 0 && !inventory) {
            val send: (Action) -> Unit = { a -> pending = emptyList(); humanMoves.trySend(a) }
            val needsMe = tradeNeedsMe(state, me)
            // The trade chest docks where the move buttons sit and takes their place while it is open.
            if (chestOpen) TradePanel(state, me, legal, names, send, onClose = { tradeOpen = false }, responses = responses, version = tick, autoNoSeconds = autoNo,
                modifier = Modifier.align(Alignment.BottomStart).padding(8.dp))
            else if (legal.isNotEmpty()) HumanControls(state, me, legal, names, send, Modifier.align(Alignment.BottomEnd).padding(8.dp))
            if (chosen.isNotEmpty()) Column(
                Modifier.align(Alignment.TopCenter).padding(top = 8.dp).chest(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(confirmTitle(state, chosen[0]), color = Color(0xFFEDE6D8), fontWeight = FontWeight.Bold)
                confirmWarning(state, me, chosen[0])?.let { Text(it, color = Color(0xFFFFB4A0), fontSize = 13.sp) }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (a in chosen) ColorButton(confirmLabel(state, a, names), Color(0xFF3E9B4F), "confirm") { send(a) }
                    ColorButton("Cancel", Color(0xFFC0392B), "cancel") { pending = emptyList() }
                }
            }
            if (tick.let { state.isOver }) Column(
                Modifier.align(Alignment.Center).chest(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(when (state.winner) { -1 -> "Draw"; me -> "You win!"; else -> "${names[state.winner]} wins" },
                    color = Color(0xFFF1D58A), fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (online == null) BrassButton("Play again", "play-again", Modifier.height(44.dp)) { seed++; playing = true }
                    WoodButton("Exit", "game-over-exit", onClick = onExit)
                }
            }
        }
      }
        if (panelOpen) CompositionLocalProvider(LocalContentColor provides Color(0xFFEDE6D8)) {
            Column(
                Modifier.align(Alignment.TopEnd).fillMaxHeight()
                    .padding(top = 60.dp, end = 8.dp, bottom = 8.dp)
                    .width(340.dp)
                    .brassEdgedWood()
                    .verticalScroll(rememberScrollState())
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GearButton(settingsOpen, { settingsOpen = true; panelOpen = false }, Modifier.size(44.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Turn ${players.first().turn}  ·  v${appVersion()}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    // Small and out of the way; it asks before leaving.
                    Text("Exit", color = Color(0xFFFFB4A0), fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.border(1.dp, Color(0xFF8A6A2A), RoundedCornerShape(10.dp))
                            .clickable { confirmExit = true }.padding(horizontal = 10.dp, vertical = 3.dp).testTag("menu-exit"))
                }
                if (online != null) Text("Online · room ${online.id}", fontWeight = FontWeight.Bold, modifier = Modifier.testTag("menu-room"))
                if (online == null) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    WoodButton(if (playing) "Pause" else "Play", "menu-pause") { playing = !playing }
                    WoodButton(if (speed == MAX_SPEED) "Max" else "${speed}×", "menu-speed") { speed = if (speed == MAX_SPEED) 1 else speed * 2 }
                    WoodButton(if (human) "Watch bots" else "Play", "menu-watch") { human = !human; speed = if (human) 2 else MAX_SPEED; tradeOpen = false; seed++; playing = true }
                    if (!human) WoodButton(if (oneGame) "One game" else "Nonstop", "menu-one-game") {
                        oneGame = !oneGame
                        if (!oneGame && state.isOver) seed++
                    }
                    if (!human && oneGame && tick.let { state.isOver }) WoodButton("Next game", "menu-next-game") { seed++ }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    WoodButton(if (inventory) "Back to game" else "Inventory", "menu-inventory") { inventory = !inventory }
                    WoodButton("Tiles: compare", "menu-tile-compare") { compareTiles = true; panelOpen = false }
                    WoodButton("Fit", "menu-fit") { camera = Camera.FIT }
                    if (online == null) WoodButton(if (diceSetting == 0) "Dice: random" else "Dice: ${36 * diceSetting} deck · ${(dice as? DiceDeck)?.remaining ?: ""}", "menu-dice") {
                        diceSetting = (diceSetting + 1) % 5
                    }
                }
                PlayerPanel(players, names)
                Text("Wins: " + names.indices.joinToString("  ") { "${names[it]} ${wins[it]}" } + "  · draws ${wins[4]}",
                    style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                for (line in log) Text(line, style = MaterialTheme.typography.bodySmall, maxLines = 1)
            }
        }
      }
        // Debug autoplay must never be invisible: a bot playing your seat looked like a desync in a playtest.
        if (autoplay || autoSetup) Text(if (autoplay) "AUTOPLAY (debug): a bot plays your seat" else "AUTOSETUP (debug)",
            color = Color(0xFFFFB4A0), fontSize = 12.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 14.dp, end = 120.dp).background(Color(0xCC2A1B10), RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp, vertical = 3.dp).testTag("debug-badge"))
        BrassButton(if (panelOpen) "Hide" else "Menu", "menu-toggle", Modifier.align(Alignment.TopEnd).padding(8.dp).height(40.dp)) { panelOpen = !panelOpen }
        if (compareTiles) { BackHandler { compareTiles = false }; TileCompare(onBack = { compareTiles = false }) }
        // Settings open from the gear inside the Menu, so the board stays clean.
        if (settingsOpen) SettingsPanel(env, sound, onClose = { settingsOpen = false },
            Modifier.align(Alignment.TopEnd).padding(top = 60.dp, end = 8.dp))
        if (confirmExit) Column(
            Modifier.align(Alignment.Center).brassEdgedWood().padding(18.dp).testTag("exit-confirm"),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Leave this game?", color = Color(0xFFF1D58A), fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(if (online != null) "You'll leave the room; the others can't continue without you yet." else "It won't be saved.", color = Color(0xFFEDE6D8), fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                WoodButton("Keep playing", "exit-no") { confirmExit = false }
                ColorButton("Leave", Color(0xFFC0392B), "exit-yes") { confirmExit = false; onExit() }
            }
        }
        // The Trade button is always there in Play mode; it lights up when a trade waits on you.
        // Zoom and Tilt bars on the right edge (pinch and two-finger drag do the same).
        if (env.viewBars && !inventory && !panelOpen && !settingsOpen && boardSize.minDimension > 0f) ViewBarsPanel(camera,
            onZoom = { z -> camera = camera.transform(Offset(boardSize.width / 2f, boardSize.height / 2f), Offset.Zero, z / camera.zoom, boardSize) },
            onTilt = { t -> camera = camera.copy(tilt = t) },
            // Upper right under Trade, short, clear of the move buttons that stack up from the bottom right.
            Modifier.align(Alignment.TopEnd).padding(top = 104.dp, end = 8.dp))
        if (me >= 0 && !inventory && tick.let { tradeButtonVisible(state) }) TradeToggle(
            open = tradeOpen || tradeNeedsMe(state, me),
            attention = tick.let { tradeNeedsMe(state, me) },
            onClick = { tradeOpen = !tradeOpen },
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 60.dp, end = if (panelOpen || settingsOpen) 376.dp else 8.dp),
        )
    }
}

/** Glowing rings where the human can build or move the bandit; [pulse] 0..1 breathes the glow. */
/**
 * Where you can build or move the bandit (Mauro: "larger, brighter, in the player's colour but a little lighter,
 * blinking in a non-annoying way"). [glow] is the lightened player colour; [pulse] 0..1 is a slow breath: the
 * marks never fade below 60% and swell a little, rather than flash. A thin dark outline keeps them readable on
 * light tiles (and for White).
 */
private fun DrawScope.drawTargets(board: Board, t: Targets, camera: Camera, pulse: Float, glow: Color = Color(0xFFFFE08A)) {
    if (size.minDimension < 2f) return
    val l = Layout.fit(board, size.width, size.height, camera)
    if (!l.scale.isFinite() || l.scale <= 0f) return
    val a = 0.6f + 0.4f * pulse
    val swell = 1f + 0.1f * pulse
    val ink = Color(0xFF1A120B)
    for (v in t.vertices) {
        val p = vertexLattice(board, v)
        val c = l.project(p.x, p.y)
        val r = BUILD_MARK_R * l.scale * l.kAt(p.y) * swell
        drawCircle(glow.copy(alpha = 0.32f * a), r * 1.6f, c)                       // soft halo
        drawCircle(glow.copy(alpha = 0.55f + 0.35f * a), r, c)                       // lit centre
        drawCircle(ink.copy(alpha = 0.55f), r, c, style = Stroke(r * 0.12f))         // outline
        drawCircle(Color.White.copy(alpha = 0.7f * a), r * 0.82f, c, style = Stroke(r * 0.1f))
    }
    for (e in t.edges) {
        val ends = board.topology.edgeVertices[e]
        val p0 = vertexLattice(board, ends[0])
        val p1 = vertexLattice(board, ends[1])
        val a0 = l.project(p0.x + (p1.x - p0.x) * 0.2f, p0.y + (p1.y - p0.y) * 0.2f)
        val a1 = l.project(p1.x + (p0.x - p1.x) * 0.2f, p1.y + (p0.y - p1.y) * 0.2f)
        val w = BUILD_MARK_W * l.scale * l.kAt((p0.y + p1.y) / 2) * swell
        drawLine(glow.copy(alpha = 0.35f * a), a0, a1, w * 2.8f, StrokeCap.Round)    // soft halo
        drawLine(ink.copy(alpha = 0.55f), a0, a1, w * 1.35f, StrokeCap.Round)        // outline
        drawLine(glow.copy(alpha = 0.65f + 0.35f * a), a0, a1, w * 1.05f, StrokeCap.Round)
    }
    for (h in t.hexes) {
        val p = hexLattice(board, h)
        val c = l.project(p.x, p.y)
        val r = 0.62f * l.scale * l.kAt(p.y) * swell
        drawCircle(glow.copy(alpha = 0.22f * a), r, c)
        drawCircle(ink.copy(alpha = 0.5f), r, c, style = Stroke(r * 0.1f))
        drawCircle(glow.copy(alpha = a), r, c, style = Stroke(r * 0.07f))
    }
}

/** Build-mark sizes in hex units: corner radius and edge half-width (Mauro, 2026-10-10: 25% smaller than 0.26 / 0.15). */
internal const val BUILD_MARK_R = 0.195f
internal const val BUILD_MARK_W = 0.1125f

/** Your build marks' colour: your player colour, lightened toward white. */
internal fun targetGlow(player: Int): Color = PLAYER_COLORS[player.coerceIn(0, PLAYER_COLORS.size - 1)].let { lerp(it, Color.White, 0.45f) }

/** The marks' slow breath, 0..1, one cycle every [period] seconds. */
internal fun targetPulse(seconds: Float, period: Float = 1.8f): Float = (kotlin.math.sin(seconds * 2f * Math.PI.toFloat() / period) + 1f) / 2f

/**
 * Board gestures: one finger pans, pinch zooms, and two fingers dragged up or down together
 * (spread barely changing) tilt the table. The kind is decided once per gesture, after
 * the fingers have moved past touch slop, so a pinch never turns into a tilt halfway.
 */
private suspend fun PointerInputScope.boardGestures(
    onTransform: (centroid: Offset, pan: Offset, zoom: Float) -> Unit,
    onTilt: (dy: Float) -> Unit,
    grab: (Offset) -> Boolean = { false },
    onGrabMove: (Offset) -> Unit = {},
    onGrabEnd: (GrabEnd) -> Unit = {},
) = awaitEachGesture {
    val first = awaitFirstDown(requireUnconsumed = false)
    // A single finger landing on a cloud drags that cloud (a tap without moving poofs it).
    if (grab(first.position)) {
        var moved = false
        var travel = 0f
        var last = first.position
        var lastTime = first.uptimeMillis
        // Release velocity (px/s) so a thrown cloud keeps going.
        val tracker = VelocityTracker().apply { addPosition(first.uptimeMillis, first.position) }
        do {
            val event = awaitPointerEvent()
            val c = event.changes.firstOrNull { it.id == first.id } ?: break
            travel += (c.position - c.previousPosition).getDistance()
            if (travel > viewConfiguration.touchSlop) moved = true
            if (moved) onGrabMove(c.position)
            tracker.addPosition(c.uptimeMillis, c.position)
            last = c.position
            lastTime = c.uptimeMillis
            c.consume()
        } while (c.pressed)
        val v = tracker.calculateVelocity()
        onGrabEnd(GrabEnd(first.position, last, travel, lastTime - first.uptimeMillis, Offset(v.x, v.y)))
        return@awaitEachGesture
    }
    var mode = 0 // 0 undecided, 1 transform, 2 tilt
    var travelY = 0f
    var travelX = 0f
    var spread = 1f
    do {
        val event = awaitPointerEvent()
        val down = event.changes.count { it.pressed }
        if (event.changes.any { it.isConsumed }) break
        val zoom = event.calculateZoom()
        val pan = event.calculatePan()
        if (mode == 0) {
            travelX += pan.x
            travelY += pan.y
            spread *= zoom
            val moved = kotlin.math.hypot(travelX, travelY)
            if (moved > viewConfiguration.touchSlop || kotlin.math.abs(spread - 1f) > 0.05f) {
                mode = if (down >= 2 && kotlin.math.abs(travelY) > 2f * kotlin.math.abs(travelX) &&
                    kotlin.math.abs(spread - 1f) < 0.04f
                ) 2 else 1
            }
        }
        when (mode) {
            1 -> onTransform(event.calculateCentroid(), pan, zoom)
            2 -> if (down >= 2) onTilt(pan.y)
        }
        if (mode != 0) event.changes.forEach { if (it.positionChanged()) it.consume() }
    } while (event.changes.any { it.pressed })
}

internal fun describe(t: ActionKind) = when (t) {
    ActionType.BUILD_SETTLEMENT -> "built a homestead"
    ActionType.BUILD_CITY -> "built a town"
    ActionType.BUILD_ROAD -> "built a road"
    ActionType.BUY_DEVELOPMENT_CARD -> "bought a Fortune card"
    ActionType.PLAY_KNIGHT_CARD -> "played a Sentinel"
    ActionType.PLAY_YEAR_OF_PLENTY -> "Windfall"
    ActionType.PLAY_MONOPOLY -> "Corner the Market"
    ActionType.PLAY_ROAD_BUILDING -> "played Trailblazers"
    ActionType.MARITIME_TRADE -> "traded with the bank"
    ActionType.MOVE_ROBBER -> "moved the bandit"
    ActionType.DISCARD -> "discarded"
    ActionType.CONFIRM_TRADE -> "traded"
    ActionType.COUNTER_TRADE -> "made a counter-offer"
    else -> t.toString().lowercase().replace('_', ' ')
}

/** Immutable per-player snapshot so the score panel recomposes when anything changes. */
internal data class PlayerInfo(
    val seat: Int, val turn: Int, val active: Boolean, val vp: Int, val cards: Int, val dev: Int,
    val road: Boolean, val army: Boolean, val winner: Boolean, val hand: List<Int>,
) {
    companion object {
        /**
         * Player [p] as [me] may see them: another player's hidden Landmark points and the kinds of cards in
         * their hand stay secret while you play (hand = empty, only the count shows). Watching bots (me = -1)
         * shows everything.
         */
        fun of(s: GameState, p: Int, me: Int = -1): PlayerInfo {
            val secret = me >= 0 && p != me && !s.isOver
            return PlayerInfo(
                seat = p, turn = s.turn, active = !s.isOver && p == s.currentPlayer,
                vp = if (secret) s.publicVictoryPoints(p) else s.victoryPoints(p), cards = s.handSize(p), dev = s.devCount(p),
                road = s.longestRoadOwner == p, army = s.largestArmyOwner == p, winner = s.winner == p,
                hand = if (secret) emptyList() else List(RESOURCE_NAMES.size) { s.hand(p, it) },
            )
        }
    }
}

@Composable
private fun PlayerPanel(players: List<PlayerInfo>, names: List<String>) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        for (pl in players) {
            Row(
                Modifier.fillMaxWidth()
                    // The player whose turn it is gets a brass-rimmed highlight.
                    .then(if (pl.active) Modifier.background(Color(0x33C9A04A), RoundedCornerShape(8.dp))
                        .border(1.dp, Color(0xFFC9A04A), RoundedCornerShape(8.dp)) else Modifier)
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(12.dp).background(PLAYER_COLORS[pl.seat], CircleShape))
                Spacer(Modifier.width(6.dp))
                Text(names[pl.seat], Modifier.width(58.dp), maxLines = 1, fontWeight = if (pl.active) FontWeight.Bold else FontWeight.Normal)
                Text("${pl.vp} VP", Modifier.width(54.dp), maxLines = 1, fontWeight = FontWeight.Bold)
                MiniHand(pl.hand, pl.dev, Modifier.weight(1f), facedown = if (pl.hand.isEmpty()) pl.cards else 0)
                val badges = listOfNotNull("Hwy".takeIf { pl.road }, "Watch".takeIf { pl.army }, "WIN".takeIf { pl.winner })
                Text(badges.joinToString(" "), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/**
 * Board geometry in canvas pixels. Flat, lattice point (x, y) lands at [center] + (x, y) * [scale].
 * Tilted by [tilt] radians, the table recedes: y is compressed by cos(tilt) and a mild perspective
 * makes far rows (top of the screen) smaller and near rows larger. Positions are projected;
 * things that stand up (pieces, tokens, the robber) stay upright and are sized by [k].
 */
private class Layout(val board: Board, val center: Offset, val scale: Float, val tilt: Float = 0f) {
    private val xs = board.topology.vertexPos.map { it.x * sqrt(3f) / 2f }
    private val ys = board.topology.vertexPos.map { it.y / 2f }
    /** Vertical compression of things lying flat on the table. */
    val squash = kotlin.math.cos(tilt)
    private val persp = PERSPECTIVE * kotlin.math.sin(tilt)

    /** Perspective size factor for a lattice row at lattice y [ly] (board spans roughly -4..4). */
    fun kAt(ly: Float) = 1f + persp * (ly * squash) / 4.2f

    fun project(lx: Float, ly: Float): Offset {
        val f = kAt(ly)
        return Offset(center.x + lx * scale * f, center.y + ly * squash * scale * f)
    }

    /** Screen point back to lattice (inverse of [project]; perspective solved by two refinements). */
    fun unproject(p: Offset): Offset {
        var ly = (p.y - center.y) / (squash * scale)
        repeat(2) { ly = (p.y - center.y) / (squash * scale * kAt(ly)) }
        return Offset((p.x - center.x) / (scale * kAt(ly)), ly)
    }

    fun at(v: Int) = project(xs[v], ys[v])
    fun k(v: Int) = kAt(ys[v])
    // Hex centres in lattice units, worked out once per layout (they were re-averaged on every call).
    private val hexCentres = Array(board.hexCount) { h ->
        val vs = board.topology.hexVertices[h]
        var sx = 0f; var sy = 0f
        for (v in vs) { sx += xs[v]; sy += ys[v] }
        Offset(sx / vs.size, sy / vs.size)
    }
    private fun hexLattice(h: Int): Offset = hexCentres[h]
    fun hexCenter(h: Int) = hexLattice(h).let { project(it.x, it.y) }
    fun hexK(h: Int) = kAt(hexLattice(h).y)
    fun rowKey(h: Int) = board.topology.hexVertices[h].sumOf { ys[it].toDouble() }

    companion object {
        /** How much bigger the nearest row is than the farthest at full tilt. */
        const val PERSPECTIVE = 0.22f

        /** Gap kept between the board (ports included) and the screen edge at fit zoom. */
        const val BORDER_PX = 3f

        /**
         * Fits everything that is drawn (tiles with their thickness and shadow, port water,
         * piers and tokens) inside the canvas minus [BORDER_PX], then applies the [camera].
         */
        // The last fitted layout: the board passes ask for the same one several times a frame, and it only
        // changes with the board, the canvas size or the camera (all drawing and gestures are on the main thread).
        private var lastFit: Layout? = null
        private var lastFitKey: Any? = null

        /**
         * Pixels at the canvas's left edge covered by the player strip and turn panel (set by the game screen).
         * The board fits in the space beside them, so no tile or corner hides under a panel.
         */
        var leftInset = 0f

        fun fit(board: Board, width: Float, height: Float, camera: Camera = Camera.FIT): Layout {
            val key = FitKey(System.identityHashCode(board), width, height, camera, leftInset)
            lastFit?.let { if (it.board === board && key == lastFitKey) return it }
            return computeFit(board, width, height, camera).also { lastFit = it; lastFitKey = key }
        }

        private data class FitKey(val board: Int, val width: Float, val height: Float, val camera: Camera, val left: Float)

        private fun computeFit(board: Board, width: Float, height: Float, camera: Camera): Layout {
            val b = extent(board)
            val left = leftInset.coerceIn(0f, width / 2f)
            val scale = min((width - left - 2 * BORDER_PX) / b.width, (height - 2 * BORDER_PX) / b.height)
            val mid = Offset(left + (width - left) / 2f, height / 2f)
            val fitOrigin = mid - b.center * scale
            return Layout(board, camera.map(fitOrigin, mid), scale * camera.zoom, camera.tilt)
        }

        /** Bounding box of the drawn board in lattice units (hex side = 1). */
        private fun extent(board: Board): androidx.compose.ui.geometry.Rect {
            val xs = board.topology.vertexPos.map { it.x * sqrt(3f) / 2f }
            val ys = board.topology.vertexPos.map { it.y / 2f }
            var minX = xs.min()
            var maxX = xs.max()
            var minY = ys.min()
            var maxY = ys.max() + TILE_THICKNESS * 1.6f // tile thickness and drop shadow
            for (p in board.ports) {
                val mid = Offset((xs[p.v1] + xs[p.v2]) / 2, (ys[p.v1] + ys[p.v2]) / 2)
                val n = mid / mid.getDistance()
                val water = mid + n * 0.5f // water oval: +-0.6 x +-0.42
                val token = mid + n * 0.58f // token radius 0.3 plus its drop shadow
                minX = minOf(minX, water.x - 0.6f, token.x - 0.3f)
                maxX = maxOf(maxX, water.x + 0.6f, token.x + 0.37f)
                minY = minOf(minY, water.y - 0.42f, token.y - 0.3f)
                maxY = maxOf(maxY, water.y + 0.42f, token.y + 0.37f)
            }
            // The framed round sea (issue #1) surrounds everything.
            val r = Sea.SEA_R + Sea.RIM + 0.1f
            minX = minOf(minX, -r); maxX = maxOf(maxX, r); minY = minOf(minY, -r); maxY = maxOf(maxY, r + 0.18f)
            return androidx.compose.ui.geometry.Rect(minX, minY, maxX, maxY)
        }
    }
}

/**
 * Zoom and pan applied on top of the fitted board: a fit-view point x is shown at
 * canvasCenter + (x - canvasCenter) * zoom + pan.
 */
data class Camera(val zoom: Float = 1f, val pan: Offset = Offset.Zero, val tilt: Float = 0f) {
    fun map(x: Offset, canvasCenter: Offset) = canvasCenter + (x - canvasCenter) * zoom + pan

    /** Zooms by [factor] keeping [focus] still, then pans by [drag]; zoom clamped to [MIN_ZOOM]..[MAX_ZOOM]. */
    fun transform(focus: Offset, drag: Offset, factor: Float, size: Size): Camera {
        // Gesture math can hand us NaN or infinity (e.g. a pinch whose fingers start on the same
        // spot); NaN slips through coerceIn and would poison the camera, so drop such input.
        if (!factor.isFinite() || factor <= 0f || !focus.x.isFinite() || !focus.y.isFinite() ||
            !drag.x.isFinite() || !drag.y.isFinite() || size.minDimension <= 0f) return this
        val z = (zoom * factor).coerceIn(MIN_ZOOM, MAX_ZOOM)
        val k = z / zoom
        val f = focus - Offset(size.width / 2f, size.height / 2f)
        return Camera(z, (pan - f) * k + f + drag, tilt).clamped(size)
    }

    /**
     * At zoom z the fitted board spans z screens, so it may slide by (z - 1) / 2 of a screen, plus [PAN_EXTRA]
     * of a (zoomed) screen more so the players' supplies on the water round the board can be reached (#52).
     */
    fun clamped(size: Size): Camera {
        val mx = ((zoom - 1f) / 2f + PAN_EXTRA * zoom) * size.width
        val my = ((zoom - 1f) / 2f + PAN_EXTRA * zoom) * size.height
        return copy(pan = Offset(pan.x.coerceIn(-mx, mx), pan.y.coerceIn(-my, my)))
    }

    /** Tilts by a vertical two-finger drag of [dy] px: dragging up tips the board away. */
    fun tilted(dy: Float, height: Float) =
        if (!dy.isFinite() || height <= 0f) this else copy(tilt = (tilt - dy / height * 1.6f).coerceIn(0f, MAX_TILT))

    companion object {
        const val MIN_ZOOM = 1f
        const val MAX_ZOOM = 4f
        const val DOUBLE_TAP_ZOOM = 2.5f
        /** Extra pan beyond the board, in screens at the current zoom, to see the supplies off its edges. */
        const val PAN_EXTRA = 0.4f
        /** Steepest table tilt, in radians (about 55 degrees). */
        const val MAX_TILT = 0.96f
        val FIT = Camera()
    }
}

/**
 * The board is drawn in passes so its slow parts needn't be repainted on every sea frame:
 * [WATER] the painted sea, which only drifts a few px a second (cached, refreshed ~6 times a second),
 * [UNDER] what moves on the water every frame (glints, wavelets, boats, splashes), [STILL] everything
 * that only changes with a move, the camera or a setting (wood, cliffs, tiles, numbers, ports, roads
 * and pieces, cached), then [OVER] the things that move on their own (the Raiders' ship, birds and
 * clouds). [ALL] draws the lot in one go.
 */
internal enum class BoardPart { WATER, UNDER, STILL, OVER, ALL }

/** What the cached [BoardPart.WATER] layer depends on: the camera, the size, and the sea's clock in 1/6 s steps. */
private data class WaterLayerKey(val camera: Camera, val size: Size, val step: Int)

/** Everything the [BoardPart.STILL] recording depends on; when it changes the layer is re-recorded. */
private data class BoardLayerKey(
    val game: Int, val tick: Int, val camera: Camera, val size: Size, val tileLine: Boolean,
    val knights: List<ImageBitmap>?, val faces: Map<Int, ImageBitmap>?, val cliff: List<ImageBitmap>?,
    val viewer: Int = 0, val piles: List<Offset> = emptyList(), val buildings: BuildingSprites? = null,
    val bandit: Boolean = false,
)

private fun DrawScope.drawGame(s: GameState, measurer: TextMeasurer, wood: WoodPieces, camera: Camera, sea: Sea? = null, faces: Map<Int, ImageBitmap>? = null, cliff: List<ImageBitmap>? = null, tileLine: Boolean = false, knights: List<ImageBitmap>? = null, raiders: ImageBitmap? = null, part: BoardPart = BoardPart.ALL, bandit: List<ImageBitmap>? = null,
                                viewer: Int = 0, pileOffsets: List<Offset> = emptyList(), buildings: BuildingSprites? = null) {
    if (size.minDimension < 2f) return // not laid out yet (or squeezed to nothing): gradients need real sizes
    val board = s.board
    val l = Layout.fit(board, size.width, size.height, camera)
    val scale = l.scale
    if (!scale.isFinite() || scale <= 0f) return
    val topo = board.topology
    // Tipped toward the viewer, upright things show more of their height.
    val lift = 1f + 1.2f * kotlin.math.sin(l.tilt)
    val stand = 1f + 0.15f * kotlin.math.sin(l.tilt)
    val toScreen = { x: Float, y: Float -> l.project(x, y) }
    when (part) {
        BoardPart.WATER -> { sea?.drawWater(this, toScreen, scale * l.kAt(0f), l.squash, wood.brush(TOKEN_WOOD_INDEX), Sea.WaterPart.BASE); return }
        BoardPart.UNDER -> sea?.drawWater(this, toScreen, scale * l.kAt(0f), l.squash, wood.brush(TOKEN_WOOD_INDEX), Sea.WaterPart.LIVE)
        BoardPart.ALL -> sea?.drawWater(this, toScreen, scale * l.kAt(0f), l.squash, wood.brush(TOKEN_WOOD_INDEX))
        else -> {}
    }
    fun over() {
        // Age of Chivalry: the Raiders' ship circles closer as the track advances (#37).
        // It bobs with the sea's clock, so it rides above the still board rather than inside it.
        val ckState = s.ck
        if (ckState != null && raiders != null) {
            sea?.observeRaiders(ckState.barbarianPos, ckState.barbarianAttacks)
            val clock = sea?.time ?: 0f
            val track = sea?.raidersTrack ?: ckState.barbarianPos.toFloat()
            val landR = sea?.landR ?: board.topology.vertexPos.maxOf { kotlin.math.hypot(it.x * sqrt(3f) / 2f, it.y / 2f) }
            val pose = raidersPose(track, landR, clock)
            val at = l.project(pose.x, pose.y)
            val size = 1.15f * scale * l.kAt(pose.y)
            val right = kotlin.math.cos(pose.heading) >= 0f
            sea?.boatWake?.let { wake ->
                val stern = l.project(pose.x - kotlin.math.cos(pose.heading) * 0.35f, pose.y - kotlin.math.sin(pose.heading) * 0.35f)
                withTransform({ rotate(Math.toDegrees(pose.heading.toDouble()).toFloat(), stern) }) {
                    drawImage(wake, dstOffset = IntOffset((stern.x - size * 0.6f).toInt(), (stern.y - size * 0.15f).toInt()),
                        dstSize = IntSize((size * 0.7f).toInt(), (size * 0.3f).toInt()), alpha = 0.4f)
                }
            }
            withTransform({ if (!right) scale(-1f, 1f, at) }) {
                drawImage(raiders, dstOffset = IntOffset((at.x - size / 2).toInt(), (at.y - size / 2).toInt()), dstSize = IntSize(size.toInt(), size.toInt()),
                    filterQuality = FilterQuality.Medium)
            }
        }
        sea?.drawSky(this, toScreen, scale * l.kAt(0f))
    }
    if (part == BoardPart.OVER) { over(); return }
    if (part == BoardPart.UNDER) return

    drawTrough(board, l, scale, wood)
    if (cliff != null) drawCliffs(board, l, cliff)
    // Painter's order: top rows first so a lower tile's face covers the side wall above it.
    for (h in (0 until board.hexCount).sortedBy { l.rowKey(it) }) {
        val c = l.hexCenter(h)
        // Zoomed in, skip tiles that are entirely off screen.
        val m = 1.2f * scale
        if (c.x < -m || c.y < -m || c.x > size.width + m || c.y > size.height + m) continue
        drawTile(board.hexResource[h], board.hexNumber[h], h, c, scale * l.hexK(h), measurer, wood, l.squash, lift, faces?.get(board.hexResource[h]), tileLine)
    }

    // Ports: water patch and wooden piers first, then the raised trade-rate tokens.
    val ports = board.ports.map { p ->
        val a = l.at(p.v1)
        val b = l.at(p.v2)
        val mid = Offset((a.x + b.x) / 2, (a.y + b.y) / 2)
        val n = (mid - l.center).let { it / it.getDistance() }
        val ps = scale * (l.k(p.v1) + l.k(p.v2)) / 2f
        PortGeom(p.type, a, b, mid, n, mid + Offset(n.x, n.y * l.squash) * (0.58f * ps), ps)
    }
    for (pg in ports) drawPortWater(pg, pg.scale, l.squash)
    for (pg in ports) drawPortPiersAndToken(pg, pg.scale, measurer, wood)

    // Roads: rendered sprites (two per edge, overlapping at the middle, per the art report) or drawn planks.
    for (e in 0 until topo.edgeCount) {
        val owner = s.edgeOwner[e]
        if (owner < 0) continue
        val (v1, v2) = topo.edgeVertices[e].let { it[0] to it[1] }
        if (buildings != null) {
            val x1 = topo.vertexPos[v1].x * sqrt(3f) / 2f; val y1 = topo.vertexPos[v1].y / 2f
            val x2 = topo.vertexPos[v2].x * sqrt(3f) / 2f; val y2 = topo.vertexPos[v2].y / 2f
            val len = kotlin.math.hypot(x2 - x1, y2 - y1).coerceAtLeast(1e-3f)
            val ux = (x2 - x1) / len; val uy = (y2 - y1) / len
            val sprite = buildings.roadFor(x2 - x1, y2 - y1)
            for (side in listOf(-1f, 1f)) {
                val x = (x1 + x2) / 2f + side * ux * ROAD_SPREAD
                val y = (y1 + y2) / 2f + side * uy * ROAD_SPREAD
                drawPieceSprite(sprite, l.project(x, y), scale * l.kAt(y) * ROAD_SCALE, l.tilt, PLAYER_COLORS[owner])
            }
            continue
        }
        val a = l.at(v1)
        val b = l.at(v2)
        with(wood) { road(a, b, owner, scale * (l.k(v1) + l.k(v2)) / 2f * stand) }
    }

    // Robber.
    // Age of Chivalry knights: the theme's token at each corner, on a ring in the owner's colour.
    val ck = s.ck
    if (ck != null && knights != null) for (v in 0 until board.topology.vertexCount) {
        val owner = ck.knightOwner[v]
        if (owner < 0) continue
        val level = ck.knightLevel[v].coerceIn(1, 3)
        val img = knights[(level - 1) * 2 + if (ck.isActive(v)) 1 else 0]
        val at = l.at(v)
        val size = 0.62f * scale * l.k(v)
        drawOval(PLAYER_COLORS[owner], at + Offset(-size * 0.42f, -size * 0.12f), Size(size * 0.84f, size * 0.3f * l.squash))
        drawOval(Color.Black.copy(alpha = 0.5f), at + Offset(-size * 0.42f, -size * 0.12f), Size(size * 0.84f, size * 0.3f * l.squash), style = Stroke(size * 0.03f))
        drawImage(img, dstOffset = IntOffset((at.x - size / 2).toInt(), (at.y - size * 0.92f).toInt()), dstSize = IntSize(size.toInt(), size.toInt()),
            filterQuality = FilterQuality.Medium)
    }
    if (s.robberHex >= 0) {
        if (bandit != null) {
            // Mauro: the bandit stands ON the number plate and hides it (drawn after the tiles and their tokens).
            val hs = scale * l.hexK(s.robberHex)
            drawBandit(bandit, l.hexCenter(s.robberHex) + Offset(NUMBER_TOKEN_X * hs, NUMBER_TOKEN_Y * hs * l.squash), hs, l.tilt)
        } else {
            val rs = scale * l.hexK(s.robberHex) * stand
            drawRobber(l.hexCenter(s.robberHex) + Offset(0.15f * rs, 0.15f * rs * l.squash), rs)
        }
    }

    // Settlements and cities.
    for (v in 0 until topo.vertexCount) {
        val owner = s.vertexOwner[v]
        if (owner < 0) continue
        if (buildings != null) {
            // Rendered: Ramparts first, then the town, or the Capital that replaces it on a metropolis.
            // Mauro, 2026-10-10: "make the houses twice as large, I can't see the buildings".
            val at = l.at(v); val unit = scale * l.k(v) * BUILDING_SCALE; val color = PLAYER_COLORS[owner]
            val ckNow = s.ck
            if (ckNow != null && ckNow.wall[v] == 1) buildings.ramparts?.let { drawPieceSprite(it, at, unit, l.tilt, color) }
            val metro = ckNow?.metroVertex?.indexOf(v) ?: -1
            val piece = when {
                metro >= 0 -> buildings.capital(metro) ?: buildings.town
                s.vertexBuilding[v] == Piece.CITY -> buildings.town
                else -> buildings.homestead
            }
            drawPieceSprite(piece, at, unit, l.tilt, color)
            continue
        }
        with(wood) {
            val ps = scale * l.k(v) * stand
            if (s.vertexBuilding[v] == Piece.CITY) city(l.at(v), owner, ps) else settlement(l.at(v), owner, ps)
        }
    }
    drawSupplies(s, l, wood, stand, viewer, pileOffsets, buildings)
    if (part == BoardPart.ALL) over()
}

/**
 * The seat whose supply is under screen point [p], or -1: on its rows, or within [minPx] (a thumb's width) of
 * its middle, whichever is larger.
 */
private fun supplyAt(s: GameState, viewer: Int, offsets: List<Offset>, l: Layout, p: Offset, minPx: Float): Int {
    val q = l.unproject(p)
    for (seat in 0 until s.numPlayers) {
        val c = supplyCenter(s, seat, viewer, offsets)
        val a = Supply.seatAngle(seat, viewer, s.numPlayers)
        if (Supply.hit(q.x, q.y, c.x, c.y, a) || (l.project(c.x, c.y) - p).getDistance() < minPx) return seat
    }
    return -1
}

/** The installed app's version name (app/build.gradle.kts versionName), e.g. "0.2.0". */
@Composable
internal fun appVersion(): String {
    val ctx = LocalContext.current
    return remember { runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "?" }
}

/** How long after you were last in an online room the app still rejoins it on launch. */
internal const val ROOM_REJOIN_MS = 2 * 60 * 60 * 1000L

/** True when a room saved at [savedAt] (ms) is recent enough to rejoin at [now]; unknown or future times are not. */
internal fun rejoinRecent(savedAt: Long, now: Long): Boolean = savedAt in 1..now && now - savedAt <= ROOM_REJOIN_MS

/** Island radius for the off-board supplies (#52), from the board's outermost corner. */
internal fun supplyLandR(board: Board): Float =
    board.topology.vertexPos.maxOf { kotlin.math.hypot(it.x * sqrt(3f) / 2f, it.y / 2f) }

/** Centre of [seat]'s supply in lattice units, after the player's drag [offsets]. */
internal fun supplyCenter(s: GameState, seat: Int, viewer: Int, offsets: List<Offset>): Offset {
    val (x, y) = Supply.home(seat, viewer, s.numPlayers, supplyLandR(s.board))
    val off = offsets.getOrNull(seat) ?: Offset.Zero
    return Offset(x + off.x, y + off.y)
}

/**
 * Every player's unplayed pieces on the table off the board, at board scale (#52): neat rows in front of
 * each seat, the viewer's at the bottom, houses turned to face their owner. Drawn back to front.
 */
private fun DrawScope.drawSupplies(s: GameState, l: Layout, wood: WoodPieces, stand: Float, viewer: Int, offsets: List<Offset>, buildings: BuildingSprites? = null) {
    val n = s.numPlayers
    val me = viewer.coerceIn(0, n - 1)
    val all = ArrayList<Pair<Int, Supply.Spot>>()
    for (p in 0 until n) {
        val c = supplyCenter(s, p, me, offsets)
        val spots = Supply.layout(Rules.piecesLeft(s, p, Piece.SETTLEMENT), Rules.piecesLeft(s, p, Piece.CITY),
            Rules.piecesLeft(s, p, Piece.ROAD), Supply.seatAngle(p, me, n), c.x, c.y)
        for (sp in spots) all += p to sp
    }
    all.sortBy { it.second.y }
    for ((p, sp) in all) {
        val at = l.project(sp.x, sp.y)
        if (buildings != null) {
            val a = Supply.seatAngle(p, me, n)
            val unit = l.scale * l.kAt(sp.y)
            when (sp.kind) {
                // The upright road sprite turned to lie along the owner's row.
                Supply.Kind.ROAD -> drawPieceSprite(buildings.roadV, at, unit * ROAD_SCALE, l.tilt, PLAYER_COLORS[p], Math.toDegrees(a.toDouble()).toFloat())
                Supply.Kind.TOWN -> drawPieceSprite(buildings.town, at, unit * BUILDING_SCALE, l.tilt, PLAYER_COLORS[p], faceOwnerDegrees(a))
                Supply.Kind.HOMESTEAD -> drawPieceSprite(buildings.homestead, at, unit * BUILDING_SCALE, l.tilt, PLAYER_COLORS[p], faceOwnerDegrees(a))
            }
            continue
        }
        val ps = l.scale * l.kAt(sp.y) * stand
        with(wood) {
            if (sp.kind == Supply.Kind.ROAD) road(l.project(sp.x - sp.dx, sp.y - sp.dy), l.project(sp.x + sp.dx, sp.y + sp.dy), p, ps)
            else {
                val deg = Math.toDegrees((Supply.seatAngle(p, me, n) - Math.PI.toFloat() / 2f).toDouble()).toFloat()
                withTransform({ rotate(deg, at) }) { if (sp.kind == Supply.Kind.TOWN) city(at, p, ps) else settlement(at, p, ps) }
            }
        }
    }
}

/** One raised terrain tile (hex side = [scale] px) with its number token. */
private fun DrawScope.drawTile(
    resource: Int, number: Int, variant: Int, c: Offset, scale: Float, measurer: TextMeasurer, wood: WoodPieces,
    squash: Float = 1f, lift: Float = 1f, face: ImageBitmap? = null, line: Boolean = false,
) {
    // Inset corners so each tile reads as a separate piece. Corner order: clockwise from top.
    // The top face lies on the table, so tilting squashes it; the side walls are upright.
    val top = List(6) { i ->
        val a = Math.toRadians(-90.0 + 60 * i)
        c + Offset((kotlin.math.cos(a) * scale * 0.93f).toFloat(), (kotlin.math.sin(a) * scale * 0.93f * squash).toFloat())
    }
    val down = Offset(0f, TILE_THICKNESS * scale * lift) // visible tile thickness (frame option B: about twice the original)
    val base = TERRAIN_COLORS.getValue(resource)

    drawPath(polygon(top.map { it + down * 1.6f }), Color.Black.copy(alpha = 0.18f)) // soft drop shadow
    // Side walls of the three lower edges, darker the further they face from the light.
    for ((i, f) in listOf(1 to 0.45f, 2 to 0.55f, 3 to 0.4f)) {
        val a = top[i]
        val b = top[i + 1]
        drawPath(polygon(listOf(a, b, b + down, a + down)), base.shade(f))
    }
    if (face != null) {
        // The painted face fills the hex top (pointy-top, sqrt(3) R wide, 2 R tall; tilting squashes it).
        val r = scale * 0.93f
        val w = sqrt(3f) * r
        val hgt = 2 * r * squash
        drawImage(face, dstOffset = IntOffset((c.x - w / 2).toInt(), (c.y - hgt / 2).toInt()), dstSize = IntSize(w.toInt(), hgt.toInt()),
            filterQuality = FilterQuality.Medium)
        // Tile edges: Thin line = a fine dark outline in a deeper shade of the terrain.
        if (line) drawPath(polygon(top), base.shade(0.55f).copy(alpha = 0.75f), style = Stroke(0.025f * scale))
        if (number != 0) drawNumberToken(number, c + Offset(NUMBER_TOKEN_X * scale, NUMBER_TOKEN_Y * scale * squash), scale, measurer, wood)
        return
    }
    // Top face lit from the upper left.
    drawPath(
        polygon(top),
        Brush.linearGradient(
            listOf(base.tint(0.25f), base, base.shade(0.18f)),
            start = c + Offset(-scale, -scale),
            end = c + Offset(scale, scale),
        ),
    )
    if (squash < 0.999f) {
        withTransform({ scale(1f, squash, c) }) { TerrainArt.draw(this, resource, variant, c, scale) }
    } else {
        TerrainArt.draw(this, resource, variant, c, scale)
    }
    // Bevel: bright upper-left edges, dark lower-right edges.
    val bevel = 0.05f * scale
    for ((i, color) in listOf(
        4 to base.tint(0.55f), 5 to base.tint(0.55f), 0 to base.tint(0.3f),
        1 to base.shade(0.3f), 2 to base.shade(0.4f), 3 to base.shade(0.2f),
    )) {
        drawLine(color, top[i], top[(i + 1) % 6], strokeWidth = bevel, cap = StrokeCap.Round)
    }
    if (number != 0) drawNumberToken(number, c + Offset(NUMBER_TOKEN_X * scale, NUMBER_TOKEN_Y * scale * squash), scale, measurer, wood)
}

/** Bandit V1 "skeleton corsair" (Codex 3D model via mauro-7a, #32): one sprite per tilt step 0.96*i/7. */
internal val BANDIT_SPRITES = listOf(R.drawable.bandit_tilt_0, R.drawable.bandit_tilt_1, R.drawable.bandit_tilt_2, R.drawable.bandit_tilt_3,
    R.drawable.bandit_tilt_4, R.drawable.bandit_tilt_5, R.drawable.bandit_tilt_6, R.drawable.bandit_tilt_7)

/** Which two tilt sprites to show for board [tilt], and how much of the second (0..1). */
internal fun banditSteps(tilt: Float): Triple<Int, Int, Float> {
    val pos = (tilt / Camera.MAX_TILT * 7f).coerceIn(0f, 7f)
    val i = pos.toInt().coerceAtMost(6)
    return Triple(i, i + 1, pos - i)
}

/**
 * The bandit standing on the number plate centred at [at]: its sprite is 2.65 hex sides across with the plate
 * centre at the image centre (ART_SPEC "Bandit"); neighbouring tilt steps cross-fade as the board tilts.
 */
private fun DrawScope.drawBandit(sprites: List<ImageBitmap>, at: Offset, hexScale: Float, tilt: Float) {
    val side = 2.65f * hexScale
    val dst = IntOffset((at.x - side / 2).toInt(), (at.y - side / 2).toInt())
    val pos = (tilt / Camera.MAX_TILT * 7f).coerceIn(0f, 7f)
    val a = pos.toInt().coerceAtMost(6)
    val b = a + 1
    val f = pos - a
    fun draw(img: ImageBitmap, alpha: Float) = drawImage(img, IntOffset.Zero, IntSize(img.width, img.height), dst,
        IntSize(side.toInt(), side.toInt()), alpha = alpha, filterQuality = FilterQuality.Medium)
    draw(sprites.getOrNull(a) ?: return, 1f)
    if (f > 0.02f) sprites.getOrNull(b)?.let { draw(it, f) }
}

private fun DrawScope.drawRobber(c: Offset, scale: Float) {
    val r = 0.13f * scale
    drawOval(Color.Black.copy(alpha = 0.3f), c + Offset(-1.3f * r, 1.6f * r), androidx.compose.ui.geometry.Size(2.6f * r, 0.9f * r))
    drawPath(polygon(listOf(c + Offset(-r, 2f * r), c + Offset(r, 2f * r), c + Offset(0.5f * r, 0f), c + Offset(-0.5f * r, 0f))), Color(0xFF3A3A3A))
    drawCircle(Brush.radialGradient(listOf(Color(0xFF777777), Color(0xFF2A2A2A)), center = c + Offset(-0.3f * r, -0.4f * r), radius = r), 0.75f * r, c)
}

/** Bitmap art the Inventory lays out beside the drawn pieces (knights in the current theme). */
private class InventoryArt(val knights: List<ImageBitmap>, val raiders: ImageBitmap, val cliffs: List<ImageBitmap>, val boats: List<ImageBitmap>,
                           val gull: ImageBitmap, val buildings: BuildingSprites? = null)

/** Height of the Inventory's bottom row of cards and dice (a Compose overlay); the canvas fits above it. */
private const val INVENTORY_CARDS_DP = 126

/** Every game object side by side, for eyeballing the art. */
private fun DrawScope.drawInventory(measurer: TextMeasurer, wood: WoodPieces, bandit: List<ImageBitmap>? = null,
                                    faces: Map<Int, ImageBitmap>? = null, tileLine: Boolean = false, art: InventoryArt? = null, bottomInset: Float = 0f) {
    if (size.minDimension < 2f) return
    val scale = min(size.width / 13.5f, (size.height - bottomInset) / (if (art != null) 9.7f else 9.6f))
    fun label(text: String, at: Offset) {
        val t = measurer.measure(text, TextStyle(fontSize = (0.2f * scale / density).sp, color = Color(0xFFE8E2D6)))
        drawText(t, topLeft = at - Offset(t.size.width / 2f, 0f))
    }
    val left = size.width / 2f - 5 * 2.05f * scale / 2f
    // Row 1: the six terrains with sample numbers.
    val terrains = listOf(Resource.WOOD to 5, Resource.BRICK to 6, Resource.SHEEP to 8, Resource.WHEAT to 9, Resource.ORE to 10, Resource.NONE to 0)
    val names = listOf("Forest", "Hills", "Pasture", "Fields", "Mountains", "Desert")
    terrains.forEachIndexed { i, (res, n) ->
        val c = Offset(left + i * 2.05f * scale, 1.25f * scale)
        // The same faces and edge setting as the board (it showed the old procedural art before).
        drawTile(res, n, i, c, scale, measurer, wood, face = faces?.get(res), line = tileLine)
        label(names[i], c + Offset(0f, 1.2f * scale))
    }
    // Row 2: port tokens and the robber.
    val ports = listOf(Port.GENERIC, Resource.WOOD, Resource.BRICK, Resource.SHEEP, Resource.WHEAT, Resource.ORE)
    ports.forEachIndexed { i, type ->
        val c = Offset(left + i * 2.05f * scale, 4.0f * scale)
        drawPortToken(type, c, scale * 1.4f, measurer, wood)
        label(if (type == Port.GENERIC) "3:1 port" else "2:1 port", c + Offset(0f, 0.6f * scale))
    }
    // Row 3: each player's road, settlement and city, plus the robber.
    for (p in PLAYER_COLORS.indices) {
        val x = left - 0.4f * scale + p * 2.9f * scale
        val y = 6.6f * scale
        val sprites = art?.buildings
        // The rendered pieces as the board shows them (slightly tilted), or the drawn ones as a fallback.
        if (sprites != null) {
            val unit = scale * 2.2f
            drawPieceSprite(sprites.roadP30, Offset(x - 0.5f * scale, y), unit, 0.55f, PLAYER_COLORS[p])
            drawPieceSprite(sprites.homestead, Offset(x + 0.5f * scale, y), unit, 0.55f, PLAYER_COLORS[p])
            drawPieceSprite(sprites.town, Offset(x + 1.35f * scale, y), unit, 0.55f, PLAYER_COLORS[p])
        } else with(wood) {
            road(Offset(x - 0.9f * scale, y + 0.2f * scale), Offset(x - 0.1f * scale, y - 0.4f * scale), p, scale * 1.3f)
            settlement(Offset(x + 0.5f * scale, y), p, scale * 1.3f)
            city(Offset(x + 1.35f * scale, y), p, scale * 1.3f)
        }
        label(PLAYER_NAMES[p], Offset(x + 0.3f * scale, y + 0.65f * scale))
    }
    val robberAt = Offset(left + 5 * 2.05f * scale + 1.3f * scale, 7.0f * scale)
    if (bandit != null) drawBandit(bandit, robberAt - Offset(0f, 0.6f * scale), scale * 1.8f, 0.55f) else drawRobber(robberAt, scale * 1.5f)
    if (art == null) return
    /** Draws [b] [w] scale-units wide, centred on [c] (height from its aspect). */
    fun image(b: ImageBitmap, c: Offset, w: Float) {
        val pw = w * scale; val ph = pw * b.height / b.width
        drawImage(b, dstOffset = IntOffset((c.x - pw / 2).toInt(), (c.y - ph / 2).toInt()), dstSize = IntSize(pw.toInt(), ph.toInt()), filterQuality = FilterQuality.Medium)
    }
    // Row 4: Age of Chivalry knights (levels 1-3, idle and active), the Raiders' ship, a cliff strip, boats and a gull.
    val y4 = 8.75f * scale
    art.knights.forEachIndexed { i, k ->
        val c = Offset(left - 0.6f * scale + i * 0.95f * scale, y4)
        image(k, c, 0.9f)
        label("L${i / 2 + 1}${if (i % 2 == 1) " on" else ""}", c + Offset(0f, 0.5f * scale))
    }
    val shipAt = Offset(left + 5.3f * scale, y4)
    image(art.raiders, shipAt, 1.1f); label("Raiders", shipAt + Offset(0f, 0.6f * scale))
    val cliffAt = Offset(left + 7.4f * scale, y4 - 0.15f * scale)
    image(art.cliffs[0], cliffAt, 2.4f); label("Cliff", cliffAt + Offset(0f, 0.45f * scale))
    art.boats.take(4).forEachIndexed { i, bt -> image(bt, Offset(left + 9.2f * scale + (i % 2) * 0.7f * scale, y4 - 0.35f * scale + (i / 2) * 0.6f * scale), 0.7f) }
    label("Boats", Offset(left + 9.55f * scale, y4 + 0.6f * scale))
    val gullAt = Offset(left + 10.7f * scale, y4)
    image(art.gull, gullAt, 0.8f); label("Gull", gullAt + Offset(0f, 0.5f * scale))
}

private class PortGeom(val type: Int, val a: Offset, val b: Offset, val mid: Offset, val out: Offset, val token: Offset, val scale: Float)

private val WATER = Color(0xFF4FA3D9)

private fun DrawScope.drawPortWater(pg: PortGeom, scale: Float, squash: Float = 1f, showBoat: Boolean = true) {
    val c = pg.mid + Offset(pg.out.x, pg.out.y * squash) * (0.5f * scale)
    // The water lies flat on the table, so tilting squashes it.
    // Harbor shallows: a soft lighter patch on the sea rather than a separate pond.
    drawOval(Brush.radialGradient(listOf(Color(0x664F9FC8), Color(0x003F8FB8)), center = c, radius = 0.6f * scale),
        c - Offset(0.6f * scale, 0.42f * scale * squash), Size(1.2f * scale, 0.84f * scale * squash))
    // Wave arcs.
    val waves = Path()
    for ((dx, dy) in listOf(-0.4f to 0.12f, 0.36f to -0.14f, 0.34f to 0.2f, -0.3f to -0.22f, 0.0f to 0.3f)) {
        val w = c + Offset(dx * scale, dy * scale)
        waves.moveTo(w.x - 0.07f * scale, w.y)
        waves.quadraticTo(w.x - 0.035f * scale, w.y - 0.04f * scale, w.x, w.y)
        waves.quadraticTo(w.x + 0.035f * scale, w.y - 0.04f * scale, w.x + 0.07f * scale, w.y)
    }
    drawPath(waves, Color.White.copy(alpha = 0.8f), style = Stroke(0.018f * scale, cap = StrokeCap.Round))
    // A little sailboat off to the side.
    if (!showBoat) return
    val perp = Offset(-pg.out.y, pg.out.x)
    val boat = pg.mid + pg.out * (0.62f * scale) + perp * (0.42f * scale)
    val u = 0.09f * scale
    drawPath(polygon(listOf(boat + Offset(-1.2f * u, 0f), boat + Offset(1.2f * u, 0f), boat + Offset(0.8f * u, 0.5f * u), boat + Offset(-0.8f * u, 0.5f * u))), Color(0xFF6B3E1F))
    drawLine(Color(0xFF3B2412), boat, boat + Offset(0f, -1.9f * u), strokeWidth = 0.15f * u)
    drawPath(polygon(listOf(boat + Offset(0.08f * u, -1.8f * u), boat + Offset(0.08f * u, -0.15f * u), boat + Offset(1.1f * u, -0.15f * u))), Color.White)
    drawPath(polygon(listOf(boat + Offset(-0.08f * u, -1.5f * u), boat + Offset(-0.08f * u, -0.15f * u), boat + Offset(-0.8f * u, -0.15f * u))), Color(0xFFE8E2D0))
}

private fun DrawScope.drawPortPiersAndToken(pg: PortGeom, scale: Float, measurer: TextMeasurer, wood: WoodPieces) {
    // Wooden plank walkways from each coast intersection to the token.
    for (v in listOf(pg.a, pg.b)) {
        // 15% longer than the original 0.12..0.75 span, extended at the coast end so the token stays put.
        val from = v + (pg.token - v) * 0.0255f
        val to = pg.token + (v - pg.token) * 0.25f
        val dir = (to - from).let { it / it.getDistance() }
        val across = Offset(-dir.y, dir.x)
        val half = 0.0715f * scale
        val deck = listOf(from + across * half, to + across * half, to - across * half, from - across * half)
        val side = polygon(deck.map { it + Offset(0f, 0.035f * scale) })
        drawPath(side, wood.brush(TOKEN_WOOD_INDEX))
        drawPath(side, Color.Black.copy(alpha = 0.4f))
        drawPath(polygon(deck), wood.brush(TOKEN_WOOD_INDEX))
        val planks = Path()
        val len = (to - from).getDistance()
        var t = 0.05f * scale
        while (t < len) {
            val m = from + dir * t
            planks.moveTo((m + across * half).x, (m + across * half).y)
            planks.lineTo((m - across * half).x, (m - across * half).y)
            t += 0.05f * scale
        }
        drawPath(planks, Color(0xFF6E4520).copy(alpha = 0.75f), style = Stroke(0.01f * scale))
        // Dark pilings under the walkway, with a ring of foam where each meets the water.
        var k = 0.16f * scale
        while (k < len - 0.05f * scale) {
            for (sgn in listOf(1f, -1f)) {
                val p = from + dir * k + across * (half * 0.85f * sgn) + Offset(0f, 0.05f * scale)
                drawOval(Color.White.copy(alpha = 0.35f), p + Offset(-0.03f * scale, 0.02f * scale), Size(0.06f * scale, 0.025f * scale))
                drawRect(Color(0xFF2E1B0C), p + Offset(-0.012f * scale, -0.01f * scale), Size(0.024f * scale, 0.04f * scale))
            }
            k += 0.22f * scale
        }
    }
    drawPortToken(pg.type, pg.token, scale, measurer, wood)
}

/** Raised trade-rate token: wood with an anchor for 3:1, terrain art for 2:1. */
private fun DrawScope.drawPortToken(type: Int, t: Offset, scale: Float, measurer: TextMeasurer, wood: WoodPieces) {
    // Raised token in the number-token style. Resource ports are filled with their terrain's tile art.
    val r = 0.3f * scale
    val generic = type == Port.GENERIC
    val face = if (generic) TOKEN_WOOD else TERRAIN_COLORS.getValue(type)
    drawCircle(Color.Black.copy(alpha = 0.3f), r, t + Offset(0.05f * scale, 0.07f * scale))
    drawCircle(face.shade(0.3f), r, t + Offset(0f, 0.04f * scale)) // rim
    if (generic) {
        drawCircle(wood.brush(TOKEN_WOOD_INDEX), r, t)
        drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.35f), Color.Transparent), center = t + Offset(-0.4f * r, -0.4f * r), radius = 1.2f * r), r, t)
    } else {
        TerrainArt.drawPortFill(this, type, t, r)
        // Same upper-left light as the tiles.
        drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.3f), Color.Transparent), center = t + Offset(-0.4f * r, -0.4f * r), radius = 1.2f * r), r, t)
    }
    drawCircle(face.shade(0.4f), r, t, style = Stroke(0.025f * scale))
    val text = measurer.measure(
        if (generic) "3:1" else "2:1",
        TextStyle(fontSize = (0.17f * scale / density).sp, fontWeight = FontWeight.Bold, color = INK),
    )
    val labelTop = t + Offset(-text.size.width / 2f, -0.92f * r)
    if (!generic) {
        // Cream label so the rate reads over busy art.
        val pad = 0.03f * scale
        drawRoundRect(
            Color(0xFFFFFBEF).copy(alpha = 0.92f),
            topLeft = labelTop - Offset(pad, 0f),
            size = Size(text.size.width + 2 * pad, text.size.height.toFloat()),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(text.size.height / 2f),
        )
    }
    drawText(text, topLeft = labelTop)
    if (generic) TerrainArt.drawIcon(this, Resource.NONE, t + Offset(0f, 0.38f * r), 0.54f * r)
}

/**
 * Number token radius in hex sides. Mauro (2026-10-09): "both a little": 10% smaller than the first 0.30, so the
 * tile scenery shows round it; Codex's tile art clears a matching 0.31 circle at the same centre.
 */
internal const val NUMBER_TOKEN_R = 0.27f

/** Number size on the plate, in hex units (the font size for a side-1 hex); Mauro: bigger, ~60% over the first 0.264. */
internal const val NUMBER_TEXT = 0.46f

/** Homesteads, towns, Ramparts and Capitals drawn this many times the art spec's size (roads stay as they are). */
internal const val BUILDING_SCALE = 2.4f

/** Roads drawn at this share of the art-spec size (Mauro: narrower), two per edge, centred ROAD_SPREAD from the middle. */
internal const val ROAD_SCALE = 0.8f
internal const val ROAD_SPREAD = 0.2f

/**
 * Where the number token sits on a tile, in hex sides from its centre (y down, before the tilt squash).
 * Mauro (2026-10-10): lower RIGHT, on the clearing every tile keeps for it (docs/design/art-spec.md).
 * History: top left (-0.36, -0.3), then top right (0.36, -0.3).
 */
internal const val NUMBER_TOKEN_X = 0.36f
internal const val NUMBER_TOKEN_Y = 0.3f

private fun DrawScope.drawNumberToken(n: Int, t: Offset, scale: Float, measurer: TextMeasurer, wood: WoodPieces) {
    val r = NUMBER_TOKEN_R * scale
    val k = NUMBER_TOKEN_R / 0.3f          // everything on the token shrinks with it
    drawCircle(Color.Black.copy(alpha = 0.3f), r, t + Offset(0.05f * k * scale, 0.07f * k * scale))
    drawCircle(TOKEN_WOOD.shade(0.35f), r, t + Offset(0f, 0.04f * k * scale)) // token edge
    drawCircle(wood.brush(TOKEN_WOOD_INDEX), r, t)
    drawCircle(
        Brush.radialGradient(
            listOf(Color.White.copy(alpha = 0.4f), Color.Transparent),
            center = t + Offset(-0.35f * r, -0.35f * r),
            radius = 1.3f * r,
        ),
        r, t,
    )
    drawCircle(TOKEN_WOOD.shade(0.45f), r, t, style = Stroke(0.02f * k * scale))
    val ink = if (n == 6 || n == 8) Color(0xFFC62828) else INK
    val text = measurer.measure(
        n.toString(),
        // Mauro, 2026-10-10: bigger numbers, same disc (0.264 -> 0.34 hex units, about 30% larger).
        TextStyle(fontSize = (NUMBER_TEXT * k * scale / density).sp, fontWeight = FontWeight.Bold, color = ink),
    )
    drawText(text, topLeft = t - Offset(text.size.width / 2f, text.size.height * 0.66f))
    val dots = pips(n)
    val gap = 0.12f * r
    for (d in 0 until dots) {
        drawCircle(ink, 0.05f * r, Offset(t.x + (d - (dots - 1) / 2f) * gap, t.y + 0.55f * r))
    }
}


/** A finished touch on a cloud: where it started and ended (px), path length, duration and release velocity (px/s). */
private class GrabEnd(val start: Offset, val at: Offset, val travel: Float, val durationMs: Long, val velocity: Offset)

/**
 * Cliffs around the island (Mauro's ChatGPT art): the straight cliff strip laid along every coastline
 * edge, grass lip tucked under the tile, rock face and surf hanging out over the sea.
 */
private fun DrawScope.drawCliffs(board: Board, l: Layout, cliffs: List<ImageBitmap>) {
    // Top-down cliff strips (Codex art via Pip) lie flat on the table: each is mapped through the
    // board projection, so it tilts and foreshortens exactly like the tiles.
    val topo = board.topology
    val xs = { v: Int -> topo.vertexPos[v].x * sqrt(3f) / 2f }
    val ys = { v: Int -> topo.vertexPos[v].y / 2f }
    val mtx = androidx.compose.ui.graphics.Matrix()
    var k = 0
    for ((v1, v2) in topo.edgeVertices) {
        val shared = topo.vertexHexes[v1].filter { it in topo.vertexHexes[v2] }
        if (shared.size != 1) continue                       // a coastline edge has land on one side only
        val h = shared[0]
        val hc = topo.hexVertices[h].let { vs -> Offset(vs.map(xs).average().toFloat(), vs.map(ys).average().toFloat()) }
        var ax = xs(v1); var ay = ys(v1); var bx = xs(v2); var by = ys(v2)
        val mx = (ax + bx) / 2 - hc.x; val my = (ay + by) / 2 - hc.y
        // Order the ends so the strip's bottom (surf) faces out to sea.
        if (-(by - ay) * mx + (bx - ax) * my < 0) { val tx = ax; val ty = ay; ax = bx; ay = by; bx = tx; by = ty }
        val img = cliffs[k++ % cliffs.size]
        // Lattice frame: along the edge (1.16x, overlapping neighbours) and outward (strip depth).
        val len = 1.16f
        val depth = 0.55f
        val nl = kotlin.math.hypot(mx, my); val nx = mx / nl; val ny = my / nl
        val ox = ax + (ax - bx) * 0.08f - nx * depth * 0.12f
        val oy = ay + (ay - by) * 0.08f - ny * depth * 0.12f
        val o = l.project(ox, oy)
        val ue = l.project(ox + (bx - ax) * len, oy + (by - ay) * len) - o
        val un = l.project(ox + nx * depth, oy + ny * depth) - o
        mtx.reset()
        mtx.values[0] = ue.x / img.width; mtx.values[1] = ue.y / img.width
        mtx.values[4] = un.x / img.height; mtx.values[5] = un.y / img.height
        mtx.values[12] = o.x; mtx.values[13] = o.y
        withTransform({ transform(mtx) }) { drawImage(img, filterQuality = FilterQuality.Medium) }
    }
}

/** Continuous sea-clock orbit, with track changes eased by Sea before drawing in OVER. */
internal data class RaidersPose(val x: Float, val y: Float, val heading: Float)

internal fun raidersPose(track: Float, landR: Float, time: Float): RaidersPose {
    val f = (track / wgbg.engine.CkPiece.BARBARIAN_TRACK).coerceIn(0f, 1f)
    val near = landR + 0.6f
    // Compact enough to fit beside the panels, inside the open-sea bounds with sprite clearance.
    val rx = near + (minOf(6.2f, Sea.OPEN_RX - 0.7f) - near) * (1f - f)
    val ry = near + (Sea.OPEN_RY - 0.4f - near) * (1f - f)
    val a = time * 0.025f
    return RaidersPose(kotlin.math.cos(a) * rx, kotlin.math.sin(a) * ry,
        kotlin.math.atan2(kotlin.math.cos(a) * ry, -kotlin.math.sin(a) * rx))
}

internal fun raidersAt(pos: Int, board: Board, time: Float): Pair<Float, Float> {
    val landR = board.topology.vertexPos.maxOf { kotlin.math.hypot(it.x * sqrt(3f) / 2f, it.y / 2f) }
    val p = raidersPose(pos.toFloat(), landR, time)
    return p.x to p.y
}
/**
 * The report the turn panel shows: the tracker's current one if it belongs to whoever's turn it is,
 * otherwise a fresh header for the new turn (the tracker only opens it on that player's first move).
 */
internal fun turnHeader(tracker: TurnTracker, s: GameState): TurnReport? {
    val cur = tracker.current
    if (s.isOver) return cur ?: tracker.last
    // Setup: whoever places now, even before their first piece (the placing order snakes back).
    if (s.phase == wgbg.engine.Phase.BUILD_INITIAL_SETTLEMENT || s.phase == wgbg.engine.Phase.BUILD_INITIAL_ROAD)
        return if (cur != null && cur.player == s.actingPlayer) cur else TurnReport(s.actingPlayer, s.turn)
    return if (cur != null && cur.player == s.currentPlayer && cur.turn == s.turn) cur else TurnReport(s.currentPlayer, s.turn)
}

/**
 * The road trough: a sunken dark-wood channel between the tiles (Mauro), where the roads sit.
 * Drawn under the tiles; their 0.93 inset leaves the channel showing between neighbours.
 */
private fun DrawScope.drawTrough(board: Board, l: Layout, scale: Float, wood: WoodPieces) {
    val troughWood = Color(0xFF3A2414)
    for (h in 0 until board.hexCount) {
        val c = l.hexCenter(h)
        val r = scale * l.hexK(h)
        val hex = List(6) { i ->
            val a = Math.toRadians(-90.0 + 60 * i)
            c + Offset((kotlin.math.cos(a) * r * 1.04f).toFloat(), (kotlin.math.sin(a) * r * 1.04f * l.squash).toFloat())
        }
        drawPath(polygon(hex), wood.brush(TOKEN_WOOD_INDEX))
        drawPath(polygon(hex), troughWood.copy(alpha = 0.82f))
    }
}

/**
 * Debug-only hooks for test drivers (agents, CI self-play, #46). A driver reads `WGBG-TURN` lines from logcat
 * (one each time the local player must act: the prompt and the legal moves) and answers with
 * `adb shell am broadcast -a com.mauro.wgbg.MOVE -p com.mauro.wgbg --es move TYPE:PAYLOAD`. A move goes
 * through the same checks as a tap; ROLL:0 throws real dice.
 */
object AgentHooks {
    const val TAG = "WGBG-TURN"
    const val ACTION = "com.mauro.wgbg.MOVE"
    @Volatile var sink: ((Action) -> Unit)? = null

    /** "TYPE:PAYLOAD" (or the online codec's "1:TYPE:PAYLOAD") as a move, or null. */
    fun parse(text: String): Action? = wgbg.net.MoveCodec.decode(if (text.count { it == ':' } == 1) "1:$text" else text)

    /**
     * One line a driver can act on: where, the phase, what the screen asks, and every legal move; board moves also
     * get `spots=TYPE:PAYLOAD@x,y;...`, the screen pixel to tap for each (the touch test taps those, #46).
     */
    fun line(where: String, s: GameState, legal: List<Action>, prompt: String, spot: ((Action) -> Offset?)? = null): String {
        val base = "$where turn=${s.turn} phase=${s.phase} player=${s.actingPlayer} prompt=\"$prompt\" legal=" +
            legal.joinToString(",") { "${it.type}:${it.payload}" }
        if (spot == null) return base
        val spots = legal.mapNotNull { a -> spot(a)?.let { "${a.type}:${a.payload}@${it.x.toInt()},${it.y.toInt()}" } }
        return if (spots.isEmpty()) base else "$base spots=" + spots.joinToString(";")
    }

    fun announce(where: String, s: GameState, legal: List<Action>, prompt: String, spot: ((Action) -> Offset?)? = null) {
        runCatching { android.util.Log.i(TAG, line(where, s, legal, prompt, spot)) }
    }
}
