package com.mauro.wgbg

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/*
 * The critter engine (docs/design/critter-spec.md): every board creature that moves on its own is data (a
 * critter.json), and this one engine plays it. A line-for-line port of Crest's reference simulator
 * (wgbg-art/critter-sim, sim.js / board.js / patterns.js), so the app moves a critter exactly as the simulator that
 * proved its time shares: CritterTest replays the simulator's runs and checks them. Units are hex sides, seconds and
 * degrees; the board frame has y up (the simulator's), heading 0 = +x, positive turns left.
 */

/** One clip in the model: its length and whether it loops or holds its last pose. */
internal class CritterClip(val name: String, val seconds: Double, val loop: Boolean)

/** A keyframe inside a stage ([t] 0..1, cumulative motion), with an optional clip change and opacity. */
internal class CritterKey(val t: Double, val dx: Double, val dy: Double, val dz: Double, val hasAnim: Boolean, val anim: String?, val opacity: Double?)

/** One stage of behaviour; see "Modelled behaviour algorithm" in the critter spec. */
internal class CritterStage(
    val id: String, val share: Double, val seconds: DoubleArray, val delta: DoubleArray, val turn: Double, val anim: String?,
    val next: List<Pair<String, Double>>, val keys: List<CritterKey>?, val opacity: Double?, val guard: String?,
)

/** Where an anchored critter stays: a point in the board frame (y up) and the way it faces. */
internal class CritterAnchor(val x: Double, val y: Double, val heading: Double)

/** A compiled critter.json: pattern defaults merged, ready to run. */
internal class CritterSpec(
    val id: String, val name: String, val length: Double, val layer: String, val altitude: DoubleArray, val zone: String,
    val clearOfCoast: Double, val speed: Double, val turnRate: Double, val clips: Map<String, CritterClip>,
    val stages: List<CritterStage>, val count: IntArray, val chance: Double,
    /** How it meets the board: `avoid` (default), `pass-through` or `hold`; see "Board contact" in the spec. */
    val boardContact: String = "avoid",
    /** Set for a critter that stays in one place and only plays its stages' animations (the `anchored` pattern). */
    val anchor: CritterAnchor? = null,
) {
    /** The band it draws in at height [z]: its own `layer`, or with `auto` the band that height falls in. */
    fun layerAt(z: Double): String = if (layer != "auto") layer else when {
        z < -BOARD_DEPTH -> "under"
        z < -SURFACE_BAND -> "underwater"
        z <= SURFACE_BAND -> "surface"
        z <= SKY_FROM -> "air"
        else -> "sky"
    }

    companion object {
        /** The board (sea and land) fills z from -[BOARD_DEPTH] to 0 at sea; land tiles rise to [LAND_TOP]. Hex units. */
        const val BOARD_DEPTH = 1.0
        const val LAND_TOP = 0.6
        /** Within this of sea level a critter is on the surface; above [SKY_FROM] it is in the sky. */
        const val SURFACE_BAND = 0.05
        const val SKY_FROM = 2.0
        val CONTACTS = listOf("avoid", "pass-through", "hold")

        /** Parses and compiles a critter.json. */
        fun parse(json: String): CritterSpec = compile(MiniJson.parse(json) as Map<*, *>)

        private fun nums(v: Any?) = (v as List<*>).map { (it as Number).toDouble() }.toDoubleArray()

        fun compile(c: Map<*, *>): CritterSpec {
            val behavior = c["behavior"] as Map<*, *>
            val overrides = (behavior["stages"] as List<*>?).orEmpty().map { it as Map<*, *> }
            val defaults = CRITTER_PATTERNS[behavior["pattern"]]
            // Pattern defaults by stage id; a supplied field replaces the whole field. New ids follow in order.
            val merged = if (defaults != null) defaults.map { d -> overrides.firstOrNull { it["id"] == d["id"] }?.let { d + it } ?: d } +
                overrides.filter { o -> defaults.none { it["id"] == o["id"] } }
            else overrides.also { require(it.isNotEmpty()) { "Pattern '${behavior["pattern"]}' has no defaults; give every stage" } }
            val stages = merged.map { s ->
                CritterStage(
                    id = s["id"] as String, share = (s["share"] as Number).toDouble(), seconds = nums(s["seconds"]), delta = nums(s["delta"]),
                    turn = (s["turn"] as Number?)?.toDouble() ?: 0.0, anim = s["anim"] as String?,
                    next = (s["next"] as Map<*, *>).map { (k, w) -> k as String to (w as Number).toDouble() },
                    keys = (s["keys"] as List<*>?)?.map { k -> k as Map<*, *>
                        CritterKey((k["t"] as Number).toDouble(), (k["dx"] as Number).toDouble(), (k["dy"] as Number).toDouble(), (k["dz"] as Number).toDouble(),
                            k.containsKey("anim"), k["anim"] as String?, (k["opacity"] as Number?)?.toDouble()) },
                    opacity = (s["opacity"] as Number?)?.toDouble(), guard = s["when"] as String?,
                )
            }
            val ids = stages.map { it.id }.toSet()
            require(ids.size == stages.size) { "duplicate stage ids" }
            require(kotlin.math.abs(stages.sumOf { it.share } - 100.0) < 1e-6) { "stage shares must add up to 100" }
            for (s in stages) for ((n, _) in s.next) require(n in ids) { "${s.id}.next: unknown stage '$n'" }
            val movement = c["movement"] as Map<*, *>
            val clips = (c["animations"] as List<*>).map { it as Map<*, *> }
                .associate { a -> a["name"] as String to CritterClip(a["name"] as String, (a["seconds"] as Number).toDouble(), a["mode"] == "loop") }
            for (s in stages) s.anim?.let { require(it.substringBefore(':') in clips) { "${s.id}.anim: unknown clip '$it'" } }
            val contact = c["board_contact"] as String? ?: "avoid"
            require(contact in CONTACTS) { "board_contact '$contact' is not one of $CONTACTS" }
            // An anchor is "board-centre" or a point [x, y] in the game's lattice (y down), with an optional heading.
            val anchor = (c["anchor"] as Map<*, *>?)?.let { a ->
                val at = a["at"]
                val (ax, ay) = if (at == "board-centre") 0.0 to 0.0 else nums(at).let { it[0] to -it[1] }
                CritterAnchor(ax, ay, (a["heading"] as Number?)?.toDouble() ?: 0.0)
            }
            if (anchor != null) require(stages.all { s -> s.delta.all { it == 0.0 } && s.turn == 0.0 && s.keys == null }) { "an anchored critter's stages don't move it" }
            return CritterSpec(
                id = c["id"] as String, name = c["name"] as String, length = (c["length"] as Number).toDouble(), layer = c["layer"] as String,
                altitude = nums(c["altitude"]), zone = c["zone"] as String? ?: "anywhere", clearOfCoast = (c["clear_of_coast"] as Number?)?.toDouble() ?: 0.0,
                speed = (movement["speed"] as Number).toDouble(), turnRate = (movement["turn"] as Number).toDouble(), clips = clips, stages = stages,
                count = nums(c["count"] ?: listOf(1.0, 1.0)).map { it.toInt() }.toIntArray(), chance = (c["chance"] as Number?)?.toDouble() ?: 1.0,
                boardContact = contact, anchor = anchor,
            )
        }
    }
}

/** Ready-made stage sets (patterns.js). A critter names one and overrides only what differs. */
internal val CRITTER_PATTERNS: Map<String, List<Map<*, *>>> = mapOf(
    "surface-breather" to """[
      {"id":"surface","share":5,"seconds":[3,4],"delta":[0.3,0,0.3],"anim":"surface","next":{"cruise":1}},
      {"id":"cruise","share":40,"seconds":[8,20],"delta":[1.6,0,0],"anim":"swim:d50","next":{"turn":2,"blow":2,"beat":1,"low":2,"dive":1}},
      {"id":"turn","share":10,"seconds":[4,8],"delta":[0.6,0,0],"turn":60,"anim":"swim:d50","next":{"cruise":1,"turn":1}},
      {"id":"blow","share":10,"seconds":[2,5],"delta":[0.1,0,0],"anim":"blow:d50","next":{"cruise":2,"blow":1}},
      {"id":"beat","share":10,"seconds":[2,2],"delta":[0.4,0,0],"anim":"beat:d50","next":{"cruise":1,"dive":1,"beat":1}},
      {"id":"low","share":20,"seconds":[5,12],"delta":[0.8,0,-0.1],"anim":"swim:d25","next":{"cruise":1,"dive":1,"low":1}},
      {"id":"dive","share":5,"seconds":[3,4],"delta":[0.4,0,-0.6],"anim":"dive","next":{"hidden":1}},
      {"id":"hidden","share":0,"seconds":[30,90],"delta":[3,0,0],"anim":null,"next":{"surface":1}}]""",
    "lurker" to """[
      {"id":"fade-in","share":10,"seconds":[12,18],"delta":[0.9,0,0],"anim":"glide","next":{"pass":1},
        "keys":[{"t":0,"dx":0,"dy":0,"dz":0,"opacity":0},{"t":1,"dx":0.9,"dy":0,"dz":0,"opacity":1}]},
      {"id":"pass","share":70,"seconds":[100,110],"delta":[11.2,0,0],"anim":"glide","next":{"fade-out":1}},
      {"id":"fade-out","share":10,"seconds":[12,18],"delta":[0.9,0,0],"anim":"glide","next":{"turnaround":1},
        "keys":[{"t":0,"dx":0,"dy":0,"dz":0,"opacity":1},{"t":1,"dx":0.9,"dy":0,"dz":0,"opacity":0}]},
      {"id":"turnaround","share":10,"seconds":[12,18],"delta":[0,0,0],"turn":180,"anim":"glide","opacity":0,"next":{"fade-in":1}}]""",
    // Stays where its anchor puts it and only lives: rests, now and then looks about or stirs (the space turtle).
    "anchored" to """[
      {"id":"rest","share":65,"seconds":[8,20],"delta":[0,0,0],"anim":"idle","next":{"rest":1,"look":1,"stir":1}},
      {"id":"look","share":20,"seconds":[4,8],"delta":[0,0,0],"anim":"look","next":{"rest":2,"stir":1}},
      {"id":"stir","share":15,"seconds":[3,6],"delta":[0,0,0],"anim":"stir","next":{"rest":1}}]""",
).mapValues { (_, j) -> (MiniJson.parse(j) as List<*>).map { it as Map<*, *> } }

/** JavaScript's Math.hypot as V8 computes it (scaled, Kahan-summed), so the port rounds exactly like the simulator. */
internal fun jsHypot(vararg v: Double): Double {
    var m = 0.0
    for (x in v) { val a = kotlin.math.abs(x); if (a.isNaN()) return Double.NaN; if (a > m) m = a }
    if (m == 0.0) return 0.0
    if (m.isInfinite()) return Double.POSITIVE_INFINITY
    var sum = 0.0; var comp = 0.0
    for (x in v) { val n = kotlin.math.abs(x) / m; val summand = n * n - comp; val pre = sum + summand; comp = (pre - sum) - summand; sum = pre }
    return sqrt(sum) * m
}

/** The simulator's seeded random stream (mulberry32): the same seed gives the same numbers as sim.js. */
internal class CritterRandom(seed: Long) {
    private var a = seed.toInt()
    fun next(): Double {
        a += 0x6D2B79F5
        var t = a
        t = (t xor (t ushr 15)) * (t or 1)
        t = t xor (t + (t xor (t ushr 7)) * (t or 61))
        return ((t xor (t ushr 14)).toLong() and 0xffffffffL) / 4294967296.0
    }
}

internal class CritterPoint(val x: Double, val y: Double)
private class Edge(val a: CritterPoint, val b: CritterPoint)

/**
 * The board as the critter engine sees it (board.js): pointy-top hexes of side 1, land out to axial radius
 * [landRadius], sea out to [boardRadius]; the real crenellated coast and outer edge, no circle approximations.
 */
internal class CritterBoard(val landRadius: Int = 2, val boardRadius: Int = 5, private val spawnRange: Double = 11.0,
                            /** Which sea cells (by centre) belong to the board; the simulator keeps them all. */
                            private val keep: (Double, Double) -> Boolean = { _, _ -> true }) {
    private class Cell(val x: Double, val y: Double, val land: Boolean, val points: List<CritterPoint>)

    private fun hexes(radius: Int) = buildList {
        for (q in -radius..radius) for (r in -radius..radius) {
            val ring = max(kotlin.math.abs(q), max(kotlin.math.abs(r), kotlin.math.abs(q + r)))
            if (ring > radius) continue
            val x = SQRT3D * (q + r / 2.0); val y = 1.5 * r
            add(Cell(x, y, ring <= landRadius, List(6) { i -> CritterPoint(x + StrictMath.cos((30 + 60 * i) * Math.PI / 180), y + StrictMath.sin((30 + 60 * i) * Math.PI / 180)) }))
        }
    }

    private fun boundary(cells: List<Cell>): List<Edge> {
        val edges = LinkedHashMap<String, Edge>()
        fun key(p: CritterPoint) = "%.7f,%.7f".format(java.util.Locale.ROOT, p.x, p.y).replace("-0.0000000", "0.0000000")
        for (h in cells) for (i in 0 until 6) {
            val a = h.points[i]; val b = h.points[(i + 1) % 6]
            val id = listOf(key(a), key(b)).sorted().joinToString("|")
            if (edges.remove(id) == null) edges[id] = Edge(a, b)
        }
        return edges.values.toList()
    }

    private val cells = hexes(boardRadius).filter { it.land || keep(it.x, it.y) }
    private val coast = boundary(cells.filter { it.land })
    private val outer = boundary(cells)
    private val offOuter = boundary(hexes(boardRadius + 1))

    /** Land hex centres, for checking this board against the game's. */
    val landCentres: List<CritterPoint> get() = cells.filter { it.land }.map { CritterPoint(it.x, it.y) }

    private fun inside(p: CritterPoint, edges: List<Edge>): Boolean {
        var yes = false
        for (e in edges) { val a = e.a; val b = e.b
            if ((a.y > p.y) != (b.y > p.y) && p.x < (b.x - a.x) * (p.y - a.y) / (b.y - a.y) + a.x) yes = !yes }
        return yes
    }

    private fun pointSegment(p: CritterPoint, a: CritterPoint, b: CritterPoint): Double {
        val dx = b.x - a.x; val dy = b.y - a.y; val den = dx * dx + dy * dy
        val u = if (den != 0.0) max(0.0, min(1.0, ((p.x - a.x) * dx + (p.y - a.y) * dy) / den)) else 0.0
        return jsHypot(p.x - a.x - u * dx, p.y - a.y - u * dy)
    }

    private fun edgeDistance(p: CritterPoint, edges: List<Edge>): Double { var d = Double.POSITIVE_INFINITY; for (e in edges) d = min(d, pointSegment(p, e.a, e.b)); return d }

    private fun segmentDistance(a: CritterPoint, b: CritterPoint, c: CritterPoint, d: CritterPoint): Double {
        fun cross(p: CritterPoint, q: CritterPoint, r: CritterPoint) = (q.x - p.x) * (r.y - p.y) - (q.y - p.y) * (r.x - p.x)
        if (cross(a, b, c) * cross(a, b, d) < 0 && cross(c, d, a) * cross(c, d, b) < 0) return 0.0
        return min(min(pointSegment(a, c, d), pointSegment(b, c, d)), min(pointSegment(c, a, b), pointSegment(d, a, b)))
    }

    fun onLand(p: CritterPoint) = inside(p, coast)
    fun landDistance(p: CritterPoint) = edgeDistance(p, coast)

    /** How far [p] is inside [c]'s zone, keeping its body (a disk of half its length) clear; negative = outside. */
    fun clearance(p: CritterPoint, c: CritterSpec): Double {
        val radius = c.length / 2
        val landD = edgeDistance(p, coast); val boardD = edgeDistance(p, outer)
        val land = (if (inside(p, coast)) -1 else 1) * landD
        val board = (if (inside(p, outer)) 1 else -1) * boardD
        return when (c.zone) {
            "open-sea" -> min(land - radius - c.clearOfCoast, board - radius)
            "coast" -> min(min(land - radius, 2 - (land - radius)), board - radius)
            "over-land" -> -land - radius
            "anywhere" -> board - radius
            "off-board" -> min(-board - radius, (if (inside(p, offOuter)) 1 else -1) * edgeDistance(p, offOuter) - radius)
            else -> error("Unknown zone ${c.zone}")
        }
    }

    /** The whole swept centre segment stays inside the zone (clearance is 1-Lipschitz, so the midpoint bounds it). */
    fun safeSegment(a: CritterPoint, b: CritterPoint, c: CritterSpec): Boolean =
        clearance(CritterPoint((a.x + b.x) / 2, (a.y + b.y) / 2), c) - jsHypot(a.x - b.x, a.y - b.y) / 2 >= 1e-7

    fun sweptCoastDistance(a: CritterPoint, b: CritterPoint): Double = coast.minOf { segmentDistance(a, b, it.a, it.b) }

    /** Where a critter starts: the most open spot of its zone (deterministic, like the simulator). */
    fun spawn(c: CritterSpec): DoubleArray {
        if (c.zone == "anywhere") {
            if (clearance(CritterPoint(-6.5, 0.0), c) > 0.25) return doubleArrayOf(-6.5, 0.0, 0.0)
            var x = -9.0
            while (x <= 0) { if (clearance(CritterPoint(x, 0.0), c) > 0.25) return doubleArrayOf(x, 0.0, 0.0); x += 0.05 }
        }
        var best: DoubleArray? = null; var score = Double.NEGATIVE_INFINITY
        var x = -spawnRange
        while (x <= spawnRange) {
            var y = -spawnRange
            while (y <= spawnRange) {
                val v = clearance(CritterPoint(x, y), c)
                if (v > score) { score = v; best = doubleArrayOf(x, y, StrictMath.atan2(y, x) * 180 / Math.PI + 90) }
                y += 0.2
            }
            x += 0.2
        }
        require(score >= 0.02) { "No safe spawn for zone '${c.zone}', length ${c.length}" }
        return best!!
    }

    companion object {
        private val SQRT3D = sqrt(3.0)
        /** The standard island: 19 land hexes and three rings of sea (the reference simulator's board). */
        val STANDARD by lazy { CritterBoard() }
        /**
         * The standard island with the open water in [view] (the game's lattice, y down: the sea between the side panel
         * and the zoom bars at fit zoom): sea hexes whose centre is within [margin] of the view, so a whale has room to
         * swim on a narrow screen and only ever slips partly out of sight.
         */
        fun forView(view: androidx.compose.ui.geometry.Rect, margin: Double = VIEW_MARGIN) = CritterBoard(2, 10, 18.0) { x, yUp ->
            x >= view.left - margin && x <= view.right + margin && -yUp >= view.top - margin && -yUp <= view.bottom + margin
        }
        const val VIEW_MARGIN = 1.0
    }
}

/** What the guards can see: boats and the Raiders' ship (board frame), and night. */
internal class CritterContext(val night: Boolean = false, val boats: List<CritterPoint> = emptyList(), val raiders: List<CritterPoint> = emptyList())

/** Where a critter is and what it shows, at one moment. [anim] is "clip[:pose]" or null (unseen). */
internal class CritterPose(val x: Double, val y: Double, val z: Double, val heading: Double, val stage: String, val anim: String?,
                           val opacity: Double, val animTime: Double)

/** Plays one critter (sim.js `simulate`, one tick at a time): [step] advances 0.1 s; [pose] is the current look. */
internal class CritterRunner(val spec: CritterSpec, seed: Long, val board: CritterBoard = CritterBoard.STANDARD,
                             var context: CritterContext = CritterContext()) {
    private val random = CritterRandom(seed)
    private val byId = spec.stages.associateBy { it.id }
    val time = LinkedHashMap<String, Double>().apply { for (s in spec.stages) put(s.id, 0.0) }
    val visits = LinkedHashMap<String, Int>().apply { for (s in spec.stages) put(s.id, 0) }
    var accounted = 0.0; private set
    var x = 0.0; private set
    var y = 0.0; private set
    var z = 0.0; private set
    var heading = 0.0; private set
    lateinit var stage: CritterStage; private set
    private var ticks = 0
    private var elapsed = 0
    private var animSeconds = 0.0

    /** Safety numbers since the start, as the simulator reports them. */
    var minBodyCoastClearance = Double.POSITIVE_INFINITY; private set
    var maxSpeed = 0.0; private set
    var maxTurnRate = 0.0; private set

    init {
        val a = spec.anchor
        val s0 = if (a != null) doubleArrayOf(a.x, a.y, a.heading) else board.spawn(spec)
        x = s0[0]; y = s0[1]; heading = s0[2]; z = (spec.altitude[0] + spec.altitude[1]) / 2
        enter(spec.stages.firstOrNull { guard(it) } ?: error("No stage has an eligible entry guard"))
    }

    private fun guard(s: CritterStage): Boolean = when (s.guard) {
        null -> true
        "near-coast" -> board.landDistance(CritterPoint(x, y)) - spec.length / 2 <= 2
        "far-from-boats" -> context.boats.all { jsHypot(x - it.x, y - it.y) > 3 }
        "raiders-near" -> context.raiders.any { jsHypot(x - it.x, y - it.y) <= 3 }
        "night" -> context.night
        else -> error("Unsupported guard '${s.guard}'")
    }

    private fun debt(s: CritterStage) = s.share * accounted / 100 - time.getValue(s.id)

    private fun enter(s: CritterStage) {
        stage = s
        val lo = ceil(s.seconds[0] * 10 - 1e-9); val hi = floor(s.seconds[1] * 10 + 1e-9)
        // Random lengths, gently biased toward paying back the stage's time debt.
        val bias = if (s.share > 0) clampD(debt(s) * 2, -(hi - lo) / 2, (hi - lo) / 2) else 0.0
        ticks = clampD(jsRound(lo + (hi - lo) * random.next() + bias), lo, hi).toInt()
        elapsed = 0; animSeconds = 0.0; visits[s.id] = visits.getValue(s.id) + 1
    }

    private fun next() {
        val candidates = stage.next.map { (id, w) -> byId.getValue(id) to w }.filter { guard(it.first) }
        if (candidates.isEmpty()) { elapsed = 0; return }   // every guard closed: play this stage again until one opens
        // Exponential tilt toward stages behind their share, along declared edges only.
        val scores = candidates.map { (s, w) -> StrictMath.log(w) + if (s.share > 0) debt(s) / 4 else 0.0 }
        val top = scores.max()
        val weights = scores.map { StrictMath.exp(max(-700.0, it - top)) }
        var pick = random.next() * weights.fold(0.0) { a, b -> a + b }
        var index = weights.size - 1
        for (i in weights.indices) { pick -= weights[i]; if (pick < 0) { index = i; break } }
        enter(candidates[index].first)
    }

    private class StagePose(val v: DoubleArray, val anim: String?, val opacity: Double)

    private fun pose(s: CritterStage, u: Double): StagePose {
        val keys = s.keys ?: return StagePose(DoubleArray(3) { s.delta[it] * u }, s.anim, if (s.anim == null) 0.0 else (s.opacity ?: 1.0))
        var i = 0
        while (i < keys.size - 2 && u >= keys[i + 1].t) i++
        val a = keys[i]; val b = keys[i + 1]; val f = clampD((u - a.t) / (b.t - a.t), 0.0, 1.0)
        var anim = s.anim
        var opacity = s.opacity ?: (if (s.anim == null) 0.0 else 1.0)
        for (k in keys) { if (k.t > u + 1e-12) break; if (k.hasAnim) anim = k.anim }
        val ok = keys.filter { it.opacity != null }
        if (ok.isNotEmpty()) {
            var left = ok.first(); var right = ok.last()
            for (k in ok) { if (k.t <= u) left = k; if (k.t >= u) { right = k; break } }
            opacity = if (left.t == right.t) left.opacity!! else left.opacity!! + (right.opacity!! - left.opacity!!) * clampD((u - left.t) / (right.t - left.t), 0.0, 1.0)
        }
        return StagePose(doubleArrayOf(a.dx + (b.dx - a.dx) * f, a.dy + (b.dy - a.dy) * f, a.dz + (b.dz - a.dz) * f), anim, if (anim == null) 0.0 else opacity)
    }

    /** The current look: where it is, the clip and frame pose it shows, how far into the clip, how opaque. */
    fun pose(): CritterPose {
        val p = pose(stage, elapsed.toDouble() / ticks)
        val clip = p.anim?.let { spec.clips[it.substringBefore(':')] }
        val t = when { clip == null -> 0.0; clip.loop -> animSeconds % clip.seconds; else -> min(animSeconds, clip.seconds) }
        // `pass-through`: where it is inside the board it shows faintly, as if seen through it.
        val opacity = if (spec.boardContact == "pass-through" && insideBoard(CritterPoint(x, y), z)) p.opacity * PASS_THROUGH_OPACITY else p.opacity
        return CritterPose(x, y, z, heading, stage.id, p.anim, opacity, t)
    }

    /** One 0.1 s tick. [allowNext] false skips the stage change at the very end, as the simulator's last tick does. */
    fun step(allowNext: Boolean = true) {
        val a = pose(stage, elapsed.toDouble() / ticks); val b = pose(stage, (elapsed + 1).toDouble() / ticks)
        val bx = x; val by = y; val bz = z; val bh = heading
        move(DoubleArray(3) { b.v[it] - a.v[it] }, stage, ticks * DT)
        time[stage.id] = time.getValue(stage.id) + DT
        if (stage.share > 0) accounted += DT
        minBodyCoastClearance = min(minBodyCoastClearance, board.sweptCoastDistance(CritterPoint(bx, by), CritterPoint(x, y)) - spec.length / 2)
        maxSpeed = max(maxSpeed, jsHypot(x - bx, y - by, z - bz) / DT)
        maxTurnRate = max(maxTurnRate, kotlin.math.abs(wrap(heading - bh)) / DT)
        elapsed++
        animSeconds = if (a.anim == b.anim) animSeconds + DT else 0.0
        if (elapsed == ticks && allowNext) next()
    }

    /** True when height [z] at [p] would be inside the board: under land, between its underside and the tile tops. */
    fun insideBoard(p: CritterPoint, z: Double) = z > -CritterSpec.BOARD_DEPTH && z < CritterSpec.LAND_TOP && board.onLand(p)

    private fun move(local: DoubleArray, s: CritterStage, duration: Double) {
        if (spec.anchor != null) return   // anchored: it plays its animations where it is
        var dx = local[0]; var dy = local[1]; var dz = local[2]
        val magnitude = jsHypot(dx, dy, dz); val limit = spec.speed * DT
        if (magnitude > limit) { val f = limit / magnitude; dx *= f; dy *= f; dz *= f }
        dz = clampD(z + dz, spec.altitude[0], spec.altitude[1]) - z
        val distance = jsHypot(dx, dy); val offset = StrictMath.atan2(dy, dx) / RAD
        val requestedTurn = s.turn * DT / duration; val maxTurn = spec.turnRate * DT
        var change = clampD(requestedTurn, -maxTurn, maxTurn)
        val here0 = CritterPoint(x, y)
        if (distance > 1e-12) {
            val direction = (heading + change + offset) * RAD
            // Start steering a quarter-turn of travel ahead, plus a margin.
            val look = clampD(spec.speed * (90 / max(spec.turnRate, 1.0) + 0.5) + 0.15, 0.25, 1.5)
            val probe = CritterPoint(x + StrictMath.cos(direction) * look, y + StrictMath.sin(direction) * look)
            val here = board.clearance(here0, spec)
            if (board.clearance(probe, spec) < 0.12 || here < 0.08) {
                var best = Double.NEGATIVE_INFINITY; var desired = heading + offset
                var a = -180
                while (a < 180) {
                    val angle = (heading + offset + a) * RAD
                    val target = CritterPoint(x + StrictMath.cos(angle) * look, y + StrictMath.sin(angle) * look)
                    val near = CritterPoint(x + StrictMath.cos(angle) * 0.15, y + StrictMath.sin(angle) * 0.15)
                    val score = board.clearance(target, spec) + (if (here < 0.12) 12 else 2) * board.clearance(near, spec) - 0.15 * kotlin.math.abs(a) / 180
                    if (score > best) { best = score; desired = heading + a }
                    a += 10
                }
                change = clampD(wrap(desired - heading), -maxTurn, maxTurn)
            }
        }
        val newHeading = wrap(heading + change)
        // The midpoint heading carries the step's local motion into the board frame.
        val angle = (heading + change / 2) * RAD
        val vx = dx * StrictMath.cos(angle) - dy * StrictMath.sin(angle); val vy = dx * StrictMath.sin(angle) + dy * StrictMath.cos(angle)
        fun candidate(f: Double) = CritterPoint(x + vx * f, y + vy * f)
        var factor = 1.0
        // Slow down before running out of room; turn on the spot if it must.
        if (!board.safeSegment(here0, candidate(1.0), spec)) {
            var lo = 0.0; var hi = 1.0
            repeat(24) { val mid = (lo + hi) / 2; if (board.safeSegment(here0, candidate(mid), spec)) lo = mid else hi = mid }
            factor = lo
        }
        var to = candidate(factor)
        // `avoid`: never into the board. Short of the coast it rises over the tiles (or sinks under the board,
        // whichever is nearer) before it goes on; the step goes on that climb instead.
        if (spec.boardContact == "avoid" && insideBoard(to, z + dz)) {
            val clear = if (z >= (CritterSpec.LAND_TOP - CritterSpec.BOARD_DEPTH) / 2) CritterSpec.LAND_TOP else -CritterSpec.BOARD_DEPTH - 1e-6
            dz = clampD(clear - z, -spec.speed * DT, spec.speed * DT)
            if (insideBoard(to, z + dz)) to = here0   // still short of clear: hold position while it climbs (or sinks)
            else {
                // Clear now: the climb and the step share one step's worth of speed.
                val h = jsHypot(to.x - here0.x, to.y - here0.y); val room = sqrt(max(0.0, (spec.speed * DT).let { it * it } - dz * dz))
                if (h > room) to = CritterPoint(here0.x + (to.x - here0.x) * room / h, here0.y + (to.y - here0.y) * room / h)
            }
        }
        x = to.x; y = to.y; z += dz; heading = newHeading
    }

    companion object {
        const val DT = 0.1
        /** How visible a `pass-through` critter is while inside the board. */
        const val PASS_THROUGH_OPACITY = 0.35
        private const val RAD = Math.PI / 180
        fun clampD(v: Double, lo: Double, hi: Double) = max(lo, min(hi, v))
        fun wrap(a: Double) = ((a + 180) % 360 + 360) % 360 - 180
        /** JavaScript's Math.round: halves go up. */
        fun jsRound(v: Double) = floor(v + 0.5)
    }
}
