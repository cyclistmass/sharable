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
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.channels.Channel
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
        return Camera(intent.getFloatExtra("zoom", 1f).coerceIn(Camera.MIN_ZOOM, Camera.MAX_ZOOM), Offset.Zero,
            intent.getFloatExtra("tilt", 0f).coerceIn(0f, Camera.MAX_TILT))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
                AppRoot(debugCamera())
            }
        }
    }
}

/** Painted tile faces (Mauro's ChatGPT art) by resource; the desert last. */
private val TILE_FACES = listOf(R.drawable.tile_timber, R.drawable.tile_clay, R.drawable.tile_fleece, R.drawable.tile_grain, R.drawable.tile_stone)
private val TILE_DESERT = R.drawable.tile_desert

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
private const val MAX_SPEED = 16

/** How long a zoomed/tilted board waits untouched before gliding back to the full view. */
private const val RECOVER_AFTER_MS = 6000L

private fun Color.shade(f: Float) = lerp(this, Color.Black, f)
private fun Color.tint(f: Float) = lerp(this, Color.White, f)

private fun polygon(points: List<Offset>) = Path().apply {
    moveTo(points[0].x, points[0].y)
    for (i in 1 until points.size) lineTo(points[i].x, points[i].y)
    close()
}

/** Pips under a number token: how many dice combinations roll it (2/12 → 1, 6/8 → 5). */
private fun pips(n: Int) = 6 - kotlin.math.abs(7 - n)

private enum class Screen { START, LOBBY, GAME }

/** Game start (#3) -> optional lobby (#19) -> the game; the in-game Exit goes back to game start. */
@Composable
fun AppRoot(initialCamera: Camera = Camera.FIT) {
    var screen by rememberSaveable { mutableStateOf(Screen.START) }
    var setup by remember { mutableStateOf(GameSetup()) }
    var lobby by remember { mutableStateOf<Lobby?>(null) }
    var gameNo by rememberSaveable { mutableIntStateOf(0) }
    when (screen) {
        Screen.START -> NewGameScreen(
            onStart = { setup = it; gameNo++; screen = Screen.GAME },
            initial = setup,
            onOpenLobby = { setup = it; lobby = Lobby.host(it); screen = Screen.LOBBY },
        )
        Screen.LOBBY -> lobby?.let { l ->
            BackHandler { screen = Screen.START }
            LobbyScreen(l, onChange = { lobby = it }, onStart = { setup = it; gameNo++; screen = Screen.GAME }, onSettings = { screen = Screen.START })
        } ?: run { screen = Screen.START }
        Screen.GAME -> key(gameNo) {
            // Back asks "Leave this game?" inside the game screen (same as Exit).
            WatchGameScreen(Modifier.fillMaxSize().background(Color.Black), initialCamera, setup, onExit = { screen = Screen.START })
        }
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
) {
    var seed by remember { mutableLongStateOf(System.nanoTime() and 0xffffffL) }
    // Play mode: you are seat 0 ("You"); Watch mode: bots in every seat at full speed.
    var human by remember { mutableStateOf(setup.ruleset == Ruleset.BASE) }
    val names = if (human) listOf("You") + PLAYER_NAMES.drop(1) else PLAYER_NAMES
    val seats = setup.players.size
    val state = remember(seed, seats) { Rules.newGame(setup.config(), seed) }
    // Each playing seat's bot: the level picked on the game-start screen (friends play as Greedy until #7;
    // Strong plays as Greedy until #6). In Watch mode seat 0 gets a Greedy bot too.
    val bots = remember(seed, seats) {
        setup.players.mapIndexed { i, seat ->
            val s = setup.seats[seat]
            val r = seed * 31 + i
            if (s.kind == SeatKind.BOT && s.bot == BotLevel.EASY) EasyBot(r) else GreedyBot(r)
        }
    }
    val log = remember(seed, seats) { mutableStateListOf<String>() }
    val tracker = remember(seed, seats, human) { TurnTracker(names) }
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
    // Scenery settings (density and motion), kept across restarts; the sea reads them once it supports them.
    val appContext = LocalContext.current.applicationContext
    val env = remember { EnvSettings(PrefsStore(appContext)) }
    val sound = remember { SoundSettings(PrefsStore(appContext)) }
    // One sea for the whole session: it depends only on the board's shape, and at Max speed a new
    // game starts several times a second, which would otherwise wipe splashes and clouds.
    val oceanRes = LocalContext.current.resources
    val cliff = ImageBitmap.imageResource(R.drawable.cliff_straight)
    val tileFaces = TILE_FACES.withIndex().associate { (r, id) -> r to ImageBitmap.imageResource(id) } + (Resource.NONE to ImageBitmap.imageResource(TILE_DESERT))
    val sea = remember { Sea(state.board).also { it.oceanBitmap = BitmapFactory.decodeResource(oceanRes, R.drawable.ocean_tile); it.boatColors = PLAYER_COLORS } }
    // Gesture handlers live across games (a restart would cancel a tap mid-gesture), so they
    // read the current game's sea and board through these.
    val seaNow by rememberUpdatedState(sea)
    val boardNow by rememberUpdatedState(state.board)
    var seaTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(sea) { // the sea keeps moving even when the game is paused
        var last = 0L
        while (true) withFrameNanos { now ->
            sea.clouds01 = env.clouds; sea.birds01 = env.birds; sea.boats01 = env.boats; sea.waves01 = env.waves
            if (last != 0L) sea.update(((now - last) / 1e9f).coerceAtMost(0.05f))
            last = now
            seaTick++
        }
    }

    LaunchedEffect(seed, seats, playing, speed, human) {
        val buf = ActionBuffer()
        while (humanMoves.tryReceive().isSuccess) { } // drop taps left over from another game
        suspend fun step(): ActionKind {
            Rules.legalActions(state, buf)
            val who = state.actingPlayer
            var action = if (human && who == 0) {
                tick++ // show the human what they can do
                var a: Action
                do a = humanMoves.receive() while (!Rules.isLegal(state, a))
                a
            } else bots[who].choose(state, buf)
            // Real dice: thrown by the OS's secure generator (or dealt from the deck) and recorded in the log.
            if (action.type == ActionType.ROLL) action = dice.next(withEvent = state.ck != null).toAction()
            // Answers to the human's offers, shown as marks by each name in the trade panel.
            if (human && state.currentPlayer == 0) when {
                action.type == ActionType.OFFER_TRADE || action.type == ActionType.END_TURN -> responses.clear()
                who != 0 && state.phase == Phase.DECIDE_TRADE -> when (action.type) {
                    ActionType.ACCEPT_TRADE -> responses[who] = "✓"
                    ActionType.REJECT_TRADE -> responses[who] = "✗"
                    ActionType.COUNTER_TRADE -> responses[who] = "⇄"
                    else -> {}
                }
                state.phase == Phase.DECIDE_COUNTER && action.type == ActionType.REJECT_TRADE -> responses[state.counterFrom] = "✗"
            }
            tracker.apply(state, who, action) { Rules.apply(state, action) }
            when (action.type) {
                ActionType.REJECT_TRADE, ActionType.ACCEPT_TRADE, ActionType.COUNTER_TRADE -> {}
                ActionType.END_TURN -> {}
                ActionType.ROLL -> log.add(0, "${names[who]} rolled ${state.lastRoll}")
                ActionType.OFFER_TRADE -> {}
                else -> log.add(0, "${names[who]}: ${describe(action.type)}")
            }
            if (log.size > 4) log.removeRange(4, log.size)
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
                val pause = when (type) {
                    ActionType.END_TURN, ActionType.REJECT_TRADE -> 0L
                    ActionType.ROLL -> 500L
                    else -> 350L
                }
                if (pause > 0) delay(pause / speed)
            }
        }
        if (state.isOver) {
            wins[if (state.winner >= 0) state.winner else 4]++
            if (state.winner >= 0) log.add(0, "${names[state.winner]} wins!")
            tick++
            if (speed == MAX_SPEED && playing && !human) seed++ // straight into the next game
        }
    }

    // The board fills the screen; controls live in an overlay panel toggled from the corner.
    val players = tick.let { (0 until state.numPlayers).map { PlayerInfo.of(state, it) } }
    var camera by remember { mutableStateOf(initialCamera) }
    var lastTouch by remember { mutableLongStateOf(0L) }
    // Self-healing view: if the board is left zoomed, panned or tilted with nobody touching it
    // (e.g. a stray gesture "lost" it), glide back to the full board after a few seconds.
    // Keyed on touches only: the glide itself changes the camera and must not restart the timer.
    LaunchedEffect(lastTouch) {
        if (camera == Camera.FIT) return@LaunchedEffect
        delay(RECOVER_AFTER_MS)
        val from = camera
        val steps = 30
        for (i in 1..steps) {
            val t = i / steps.toFloat()
            val e = 1 - (1 - t) * (1 - t) // ease out
            camera = Camera(from.zoom + (1f - from.zoom) * e, from.pan * (1 - e), from.tilt * (1 - e))
            withFrameNanos { }
        }
        camera = Camera.FIT
    }
    var panelOpen by remember { mutableStateOf(false) }
    var confirmExit by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }
    var hudTopRight by remember { mutableStateOf<Offset?>(null) }
    BackHandler { confirmExit = !confirmExit }
    // What the human may do right now (empty while bots move or in Watch mode).
    val legal = tick.let { if (human && !inventory && !state.isOver && state.actingPlayer == 0) Rules.legalActions(state) else emptyList() }
    val legalNow by rememberUpdatedState(legal)
    val targets = Targets.of(legal)
    val chosen = pending.filter { it in legal } // a stale pick (the turn moved on) is dropped
    Box(modifier) {
      Box(Modifier.fillMaxSize()) { // the menu floats over the board, so the ocean always fills the screen
      Box(Modifier.fillMaxSize()) {
        val measurer = rememberTextMeasurer()
        val resources = LocalContext.current.resources
        val wood = remember { WoodPieces(resources, PLAYER_COLORS + TOKEN_WOOD) }
        Canvas(
            Modifier.fillMaxSize()
                .clipToBounds() // never draw the board under the side panel
                .pointerInput(Unit) {
                    fun lattice(p: Offset) = Layout.fit(boardNow, size.width.toFloat(), size.height.toFloat(), camera).unproject(p)
                    var dragged: Sea.Cloud? = null
                    boardGestures(
                        grab = { p -> seaNow.cloudAt(lattice(p))?.also { dragged = it } != null },
                        onGrabMove = { p -> dragged?.let { seaNow.move(it, lattice(p)) } },
                        onGrabEnd = { g ->
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
                            lastTouch = System.nanoTime()
                            camera = camera.transform(centroid, pan, zoom, Size(size.width.toFloat(), size.height.toFloat()))
                        },
                        onTilt = { dy -> lastTouch = System.nanoTime(); camera = camera.tilted(dy, size.height.toFloat()) },
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
                            lastTouch = System.nanoTime()
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
                            !inventory && seaNow.isWater(p) -> seaNow.splash(p)
                        }
                    }, onDoubleTap = { at ->
                        lastTouch = System.nanoTime()
                        val sz = Size(size.width.toFloat(), size.height.toFloat())
                        camera = if (camera.zoom > 1.05f || camera.tilt > 0.01f) Camera.FIT
                        else camera.transform(at, Offset.Zero, Camera.DOUBLE_TAP_ZOOM / camera.zoom, sz)
                    })
                },
        ) {
            tick // redraw on every move
            seaTick // and on every sea frame
            if (inventory) drawInventory(measurer, wood) else {
                drawGame(state, measurer, wood, camera, sea, tileFaces, cliff)
                if (!targets.isEmpty) drawTargets(state.board, targets, camera, (kotlin.math.sin(seaTick / 9f) + 1f) / 2f)
                if (chosen.isNotEmpty()) drawTargets(state.board, Targets.of(chosen), camera, 1f, Color.White)
            }
        }
        // Inventory: the illustrated resource cards along the bottom.
        if (inventory) Row(Modifier.align(Alignment.BottomCenter).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((r, art) in CARD_ART.withIndex()) androidx.compose.foundation.Image(
                androidx.compose.ui.res.painterResource(art), RESOURCE_NAMES[r], Modifier.size(width = 76.dp, height = 110.dp))
        }
        if (!inventory) {
            val report = tick.let { if (speed == MAX_SPEED) tracker.last else tracker.current ?: tracker.last }
            val previous = tick.let { if (speed == MAX_SPEED) null else tracker.last.takeIf { it !== report } }
            // The roll's dice roll across the turn HUD and rest on its top-right corner until the next roll.
            val rolled = tick.let { tracker.current?.takeIf { it.rolled } ?: tracker.last?.takeIf { it.rolled } }
            Box(Modifier.align(Alignment.BottomStart).padding(8.dp).onGloballyPositioned { hudTopRight = it.boundsInParent().topRight }) {
                TurnHud(report, previous, names)
            }
            if (rolled != null) RollingDice(rolled.die1, rolled.die2, rollKey = rolled.turn * 8 + rolled.player, rest = hudTopRight,
                modifier = Modifier.fillMaxSize())
        }
        if (human && !inventory) {
            val send: (Action) -> Unit = { a -> pending = emptyList(); humanMoves.trySend(a) }
            val needsMe = tradeNeedsMe(state, 0)
            // The trade chest docks where the move buttons sit and takes their place while it is open.
            val chest = (tradeOpen && tradeButtonVisible(state)) || needsMe || (tradeMode(state, 0) == TradeMode.WAITING && state.currentPlayer == 0)
            if (chest) TradePanel(state, 0, legal, names, send, onClose = { tradeOpen = false }, responses = responses,
                modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp))
            else if (legal.isNotEmpty()) HumanControls(state, 0, legal, names, send, Modifier.align(Alignment.BottomEnd).padding(8.dp))
            if (chosen.isNotEmpty()) Column(
                Modifier.align(Alignment.TopCenter).padding(top = 8.dp).chest(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(confirmTitle(state, chosen[0]), color = Color(0xFFEDE6D8), fontWeight = FontWeight.Bold)
                confirmWarning(state, 0, chosen[0])?.let { Text(it, color = Color(0xFFFFB4A0), fontSize = 13.sp) }
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
                Text(when (state.winner) { -1 -> "Draw"; 0 -> "You win!"; else -> "${names[state.winner]} wins" },
                    color = Color(0xFFF1D58A), fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BrassButton("Play again", "play-again", Modifier.height(44.dp)) { seed++; playing = true }
                    WoodButton("Exit", "game-over-exit", onClick = onExit)
                }
            }
        }
      }
        if (panelOpen) CompositionLocalProvider(LocalContentColor provides Color(0xFFEDE6D8)) {
            Column(
                Modifier.align(Alignment.TopEnd).fillMaxHeight()
                    .padding(top = 116.dp, end = 8.dp, bottom = 8.dp) // below the Menu button and the gear
                    .width(340.dp)
                    .brassEdgedWood()
                    .verticalScroll(rememberScrollState())
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Turn ${players.first().turn}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    // Small and out of the way; it asks before leaving.
                    Text("Exit", color = Color(0xFFFFB4A0), fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.border(1.dp, Color(0xFF8A6A2A), RoundedCornerShape(10.dp))
                            .clickable { confirmExit = true }.padding(horizontal = 10.dp, vertical = 3.dp).testTag("menu-exit"))
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    WoodButton(if (playing) "Pause" else "Play", "menu-pause") { playing = !playing }
                    WoodButton(if (speed == MAX_SPEED) "Max" else "${speed}×", "menu-speed") { speed = if (speed == MAX_SPEED) 1 else speed * 2 }
                    WoodButton(if (human) "Watch bots" else "Play", "menu-watch") { human = !human; speed = if (human) 2 else MAX_SPEED; tradeOpen = false; seed++; playing = true }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    WoodButton(if (inventory) "Back to game" else "Inventory", "menu-inventory") { inventory = !inventory }
                    WoodButton("Fit", "menu-fit") { camera = Camera.FIT }
                    WoodButton(if (diceSetting == 0) "Dice: random" else "Dice: ${36 * diceSetting} deck · ${(dice as? DiceDeck)?.remaining ?: ""}", "menu-dice") {
                        diceSetting = (diceSetting + 1) % 5
                    }
                }
                PlayerPanel(players)
                Text("Wins: " + names.indices.joinToString("  ") { "${names[it]} ${wins[it]}" } + "  · draws ${wins[4]}",
                    style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                for (line in log) Text(line, style = MaterialTheme.typography.bodySmall, maxLines = 1)
            }
        }
      }
        BrassButton(if (panelOpen) "Hide" else "Menu", "menu-toggle", Modifier.align(Alignment.TopEnd).padding(8.dp).height(40.dp)) { panelOpen = !panelOpen }
        // The gear sits directly under the Menu button; its panel opens below it.
        GearButton(settingsOpen, { settingsOpen = !settingsOpen }, Modifier.align(Alignment.TopEnd).padding(top = 58.dp, end = 22.dp))
        if (settingsOpen) SettingsPanel(env, sound, onClose = { settingsOpen = false },
            Modifier.align(Alignment.TopEnd).padding(top = 116.dp, end = 8.dp))
        if (confirmExit) Column(
            Modifier.align(Alignment.Center).brassEdgedWood().padding(18.dp).testTag("exit-confirm"),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Leave this game?", color = Color(0xFFF1D58A), fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("It won't be saved.", color = Color(0xFFEDE6D8), fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                WoodButton("Keep playing", "exit-no") { confirmExit = false }
                ColorButton("Leave", Color(0xFFC0392B), "exit-yes") { confirmExit = false; onExit() }
            }
        }
        // The Trade button is always there in Play mode; it lights up when a trade waits on you.
        if (human && !inventory && tick.let { tradeButtonVisible(state) }) TradeToggle(
            open = tradeOpen || tradeNeedsMe(state, 0),
            attention = tick.let { tradeNeedsMe(state, 0) },
            onClick = { tradeOpen = !tradeOpen },
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 60.dp, end = if (panelOpen || settingsOpen) 376.dp else 84.dp),
        )
    }
}

/** Glowing rings where the human can build or move the bandit; [pulse] 0..1 breathes the glow. */
private fun DrawScope.drawTargets(board: Board, t: Targets, camera: Camera, pulse: Float, glow: Color = Color(0xFFFFE08A)) {
    if (size.minDimension < 2f) return
    val l = Layout.fit(board, size.width, size.height, camera)
    if (!l.scale.isFinite() || l.scale <= 0f) return
    val a = 0.55f + 0.4f * pulse
    for (v in t.vertices) {
        val p = vertexLattice(board, v)
        val c = l.project(p.x, p.y)
        val r = 0.17f * l.scale * l.kAt(p.y)
        drawCircle(glow.copy(alpha = 0.3f * a), r * 1.5f, c)
        drawCircle(glow.copy(alpha = a), r, c, style = Stroke(r * 0.3f))
    }
    for (e in t.edges) {
        val ends = board.topology.edgeVertices[e]
        val p0 = vertexLattice(board, ends[0])
        val p1 = vertexLattice(board, ends[1])
        val a0 = l.project(p0.x + (p1.x - p0.x) * 0.22f, p0.y + (p1.y - p0.y) * 0.22f)
        val a1 = l.project(p1.x + (p0.x - p1.x) * 0.22f, p1.y + (p0.y - p1.y) * 0.22f)
        val w = 0.11f * l.scale * l.kAt((p0.y + p1.y) / 2)
        drawLine(glow.copy(alpha = 0.45f * a), a0, a1, w * 2.6f, StrokeCap.Round)
        drawLine(glow.copy(alpha = 0.6f + 0.4f * a), a0, a1, w * 1.1f, StrokeCap.Round)
    }
    for (h in t.hexes) {
        val p = hexLattice(board, h)
        val c = l.project(p.x, p.y)
        val r = 0.62f * l.scale * l.kAt(p.y)
        drawCircle(glow.copy(alpha = 0.18f * a), r, c)
        drawCircle(glow.copy(alpha = a), r, c, style = Stroke(r * 0.07f))
    }
}

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
private data class PlayerInfo(
    val seat: Int, val turn: Int, val active: Boolean, val vp: Int, val cards: Int, val dev: Int,
    val road: Boolean, val army: Boolean, val winner: Boolean, val hand: List<Int>,
) {
    companion object {
        fun of(s: GameState, p: Int) = PlayerInfo(
            seat = p, turn = s.turn, active = !s.isOver && p == s.currentPlayer,
            vp = s.victoryPoints(p), cards = s.handSize(p), dev = s.devCount(p),
            road = s.longestRoadOwner == p, army = s.largestArmyOwner == p, winner = s.winner == p,
            hand = List(RESOURCE_NAMES.size) { s.hand(p, it) },
        )
    }
}

@Composable
private fun PlayerPanel(players: List<PlayerInfo>) {
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
                Text(PLAYER_NAMES[pl.seat], Modifier.width(58.dp), fontWeight = if (pl.active) FontWeight.Bold else FontWeight.Normal)
                Text("${pl.vp} VP", Modifier.width(54.dp), maxLines = 1, fontWeight = FontWeight.Bold)
                MiniHand(pl.hand, pl.dev, Modifier.weight(1f))
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
    private fun hexLattice(h: Int): Offset {
        val vs = board.topology.hexVertices[h]
        return Offset(vs.map { xs[it] }.average().toFloat(), vs.map { ys[it] }.average().toFloat())
    }
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
        fun fit(board: Board, width: Float, height: Float, camera: Camera = Camera.FIT): Layout {
            val b = extent(board)
            val scale = min((width - 2 * BORDER_PX) / b.width, (height - 2 * BORDER_PX) / b.height)
            val mid = Offset(width / 2f, height / 2f)
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
            var maxY = ys.max() + 0.14f * 1.6f // tile thickness and drop shadow
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

    /** At zoom z the fitted board spans z screens, so it may slide by at most (z - 1) / 2 of a screen. */
    fun clamped(size: Size): Camera {
        val mx = (zoom - 1f) * size.width / 2f
        val my = (zoom - 1f) * size.height / 2f
        return copy(pan = Offset(pan.x.coerceIn(-mx, mx), pan.y.coerceIn(-my, my)))
    }

    /** Tilts by a vertical two-finger drag of [dy] px: dragging up tips the board away. */
    fun tilted(dy: Float, height: Float) =
        if (!dy.isFinite() || height <= 0f) this else copy(tilt = (tilt - dy / height * 1.6f).coerceIn(0f, MAX_TILT))

    companion object {
        const val MIN_ZOOM = 1f
        const val MAX_ZOOM = 4f
        const val DOUBLE_TAP_ZOOM = 2.5f
        /** Steepest table tilt, in radians (about 55 degrees). */
        const val MAX_TILT = 0.96f
        val FIT = Camera()
    }
}

private fun DrawScope.drawGame(s: GameState, measurer: TextMeasurer, wood: WoodPieces, camera: Camera, sea: Sea? = null, faces: Map<Int, ImageBitmap>? = null, cliff: ImageBitmap? = null) {
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
    sea?.drawWater(this, toScreen, scale * l.kAt(0f), l.squash, wood.brush(TOKEN_WOOD_INDEX))

    if (cliff != null) drawCliffs(board, l, cliff)
    // Painter's order: top rows first so a lower tile's face covers the side wall above it.
    for (h in (0 until board.hexCount).sortedBy { l.rowKey(it) }) {
        val c = l.hexCenter(h)
        // Zoomed in, skip tiles that are entirely off screen.
        val m = 1.2f * scale
        if (c.x < -m || c.y < -m || c.x > size.width + m || c.y > size.height + m) continue
        drawTile(board.hexResource[h], board.hexNumber[h], h, c, scale * l.hexK(h), measurer, wood, l.squash, lift, faces?.get(board.hexResource[h]))
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

    // Roads.
    for (e in 0 until topo.edgeCount) {
        val owner = s.edgeOwner[e]
        if (owner < 0) continue
        val (v1, v2) = topo.edgeVertices[e].let { it[0] to it[1] }
        val a = l.at(v1)
        val b = l.at(v2)
        with(wood) { road(a, b, owner, scale * (l.k(v1) + l.k(v2)) / 2f * stand) }
    }

    // Robber.
    if (s.robberHex >= 0) {
        val rs = scale * l.hexK(s.robberHex) * stand
        drawRobber(l.hexCenter(s.robberHex) + Offset(0.15f * rs, 0.15f * rs * l.squash), rs)
    }

    // Settlements and cities.
    for (v in 0 until topo.vertexCount) {
        val owner = s.vertexOwner[v]
        if (owner < 0) continue
        with(wood) {
            val ps = scale * l.k(v) * stand
            if (s.vertexBuilding[v] == Piece.CITY) city(l.at(v), owner, ps) else settlement(l.at(v), owner, ps)
        }
    }
    sea?.drawSky(this, toScreen, scale * l.kAt(0f))
}

/** One raised terrain tile (hex side = [scale] px) with its number token. */
private fun DrawScope.drawTile(
    resource: Int, number: Int, variant: Int, c: Offset, scale: Float, measurer: TextMeasurer, wood: WoodPieces,
    squash: Float = 1f, lift: Float = 1f, face: ImageBitmap? = null,
) {
    // Inset corners so each tile reads as a separate piece. Corner order: clockwise from top.
    // The top face lies on the table, so tilting squashes it; the side walls are upright.
    val top = List(6) { i ->
        val a = Math.toRadians(-90.0 + 60 * i)
        c + Offset((kotlin.math.cos(a) * scale * 0.93f).toFloat(), (kotlin.math.sin(a) * scale * 0.93f * squash).toFloat())
    }
    val down = Offset(0f, 0.14f * scale * lift) // visible tile thickness
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
        if (number != 0) drawNumberToken(number, c + Offset(-0.36f * scale, -0.3f * scale * squash), scale, measurer, wood)
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
    if (number != 0) drawNumberToken(number, c + Offset(-0.36f * scale, -0.3f * scale * squash), scale, measurer, wood)
}

private fun DrawScope.drawRobber(c: Offset, scale: Float) {
    val r = 0.13f * scale
    drawOval(Color.Black.copy(alpha = 0.3f), c + Offset(-1.3f * r, 1.6f * r), androidx.compose.ui.geometry.Size(2.6f * r, 0.9f * r))
    drawPath(polygon(listOf(c + Offset(-r, 2f * r), c + Offset(r, 2f * r), c + Offset(0.5f * r, 0f), c + Offset(-0.5f * r, 0f))), Color(0xFF3A3A3A))
    drawCircle(Brush.radialGradient(listOf(Color(0xFF777777), Color(0xFF2A2A2A)), center = c + Offset(-0.3f * r, -0.4f * r), radius = r), 0.75f * r, c)
}

/** Every game object side by side, for eyeballing the art. */
private fun DrawScope.drawInventory(measurer: TextMeasurer, wood: WoodPieces) {
    if (size.minDimension < 2f) return
    val scale = min(size.width / 13.5f, size.height / 9.6f)
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
        drawTile(res, n, i, c, scale, measurer, wood)
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
        with(wood) {
            road(Offset(x - 0.9f * scale, y + 0.2f * scale), Offset(x - 0.1f * scale, y - 0.4f * scale), p, scale * 1.3f)
            settlement(Offset(x + 0.5f * scale, y), p, scale * 1.3f)
            city(Offset(x + 1.35f * scale, y), p, scale * 1.3f)
        }
        label(PLAYER_NAMES[p], Offset(x + 0.3f * scale, y + 0.65f * scale))
    }
    val robberAt = Offset(left + 5 * 2.05f * scale + 0.9f * scale, 6.4f * scale)
    drawRobber(robberAt, scale * 1.5f)
    // Row 4: what doesn't exist yet.
    label("Cards (resource, development, progress, commodities): not designed yet", Offset(size.width / 2f, 8.4f * scale))
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

private fun DrawScope.drawNumberToken(n: Int, t: Offset, scale: Float, measurer: TextMeasurer, wood: WoodPieces) {
    val r = 0.3f * scale
    drawCircle(Color.Black.copy(alpha = 0.3f), r, t + Offset(0.05f * scale, 0.07f * scale))
    drawCircle(TOKEN_WOOD.shade(0.35f), r, t + Offset(0f, 0.04f * scale)) // token edge
    drawCircle(wood.brush(TOKEN_WOOD_INDEX), r, t)
    drawCircle(
        Brush.radialGradient(
            listOf(Color.White.copy(alpha = 0.4f), Color.Transparent),
            center = t + Offset(-0.35f * r, -0.35f * r),
            radius = 1.3f * r,
        ),
        r, t,
    )
    drawCircle(TOKEN_WOOD.shade(0.45f), r, t, style = Stroke(0.02f * scale))
    val ink = if (n == 6 || n == 8) Color(0xFFC62828) else INK
    val text = measurer.measure(
        n.toString(),
        TextStyle(fontSize = (0.264f * scale / density).sp, fontWeight = FontWeight.Bold, color = ink),
    )
    drawText(text, topLeft = t - Offset(text.size.width / 2f, text.size.height * 0.6f))
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
private fun DrawScope.drawCliffs(board: Board, l: Layout, cliff: ImageBitmap) {
    val topo = board.topology
    for ((v1, v2) in topo.edgeVertices) {
        val shared = topo.vertexHexes[v1].filter { it in topo.vertexHexes[v2] }
        if (shared.size != 1) continue                       // a coastline edge has land on one side only
        var a = l.at(v1); var b = l.at(v2)
        val c = l.hexCenter(shared[0])
        val mid = (a + b) / 2f
        val n = (mid - c).let { it / it.getDistance().coerceAtLeast(1e-3f) }
        // Order the ends so the strip's "down" (its rock face and surf) points out to sea.
        val d0 = b - a
        if (-d0.y * n.x + d0.x * n.y < 0) { val t = a; a = b; b = t }
        val d = b - a
        val len = d.getDistance()
        if (len < 2f) continue
        val angle = Math.toDegrees(kotlin.math.atan2(d.y, d.x).toDouble()).toFloat()
        val h = len * cliff.height / cliff.width * 1.8f   // taller than the art's ratio so the rock face reads at board size
        withTransform({ translate(a.x, a.y); rotate(angle, pivot = Offset.Zero) }) {
            // Slightly longer than the edge so neighbours overlap at the corners; the lip starts under the tile.
            drawImage(cliff, dstOffset = IntOffset((-0.06f * len).toInt(), (-0.18f * h).toInt()),
                dstSize = IntSize((1.12f * len).toInt(), h.toInt()), filterQuality = FilterQuality.Medium)
        }
    }
}
