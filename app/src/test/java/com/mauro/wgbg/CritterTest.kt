package com.mauro.wgbg

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * The app's critter engine against Crest's reference simulator (wgbg-art/critter-sim): the same spec and seed must give
 * the simulator's hour, stage by stage (its result.json files, schema critter-sim-result/1, are the fixtures).
 */
class CritterTest {
    private fun res(name: String) = File(javaClass.getResource("/critter-sim/$name")!!.toURI()).readText()
    private val whale = File("src/main/assets/critters/whale/critter.json").readText()
    /** Every module the app ships, read from the assets folder the way the app reads it. */
    private val modules by lazy { CritterModules.load({ File(ASSETS, it).list()?.toList().orEmpty() }, { File(ASSETS, it).inputStream() }, keepBroken = true) }
    private val whaleModule by lazy { modules.single { it.id == "whale" } }

    /** Runs [spec] for [minutes] like `node sim.js --seed`, then checks it against the simulator's result. */
    private fun replay(spec: String, seed: Long, result: String, minutes: Int = 60) {
        val c = CritterSpec.parse(spec)
        val r = CritterRunner(c, seed)
        val steps = minutes * 600
        for (i in 0 until steps) r.step(allowNext = i < steps - 1)
        val want = MiniJson.parse(res(result)) as Map<*, *>
        for (st in want["stages"] as List<*>) {
            st as Map<*, *>
            val id = st["id"] as String
            assertEquals((st["visits"] as Number).toInt(), r.visits[id], "$result: visits to $id")
            (st["measured_share"] as Number?)?.let { assertEquals(it.toDouble(), 100 * r.time.getValue(id) / r.accounted, 1e-4, "$result: share of $id") }
        }
        assertEquals((want["max_speed"] as Number).toDouble(), r.maxSpeed, 1e-4, "$result: top speed")
        assertEquals((want["max_turn_rate"] as Number).toDouble(), r.maxTurnRate, 1e-4, "$result: top turn rate")
        if (c.zone == "open-sea") assertEquals((want["min_body_coast_clearance"] as Number).toDouble(), r.minBodyCoastClearance, 1e-4, "$result: closest to the coast")
    }

    @Test
    fun `the whale's hour matches the reference simulator, seed 1 and 2`() {
        replay(whale, 1, "whale.result.json")
        replay(whale, 2, "whale-seed2.result.json")
    }

    @Test
    fun `the space whale (lurker pattern, anywhere) matches too`() {
        replay(res("space_whale.critter.json"), 1, "space_whale.result.json")
    }

    @Test
    fun `the whale keeps its time budget, its limits and clear of the coast`() {
        val c = CritterSpec.parse(whale)
        val r = CritterRunner(c, 7)
        repeat(36000) { r.step() }
        for (s in c.stages) if (s.share > 0) assertEquals(s.share, 100 * r.time.getValue(s.id) / r.accounted, 3.0, "share of ${s.id}")
        assertTrue(r.maxSpeed <= c.speed + 1e-8)
        assertTrue(r.maxTurnRate <= c.turnRate + 1e-8)
        assertTrue(r.minBodyCoastClearance >= c.clearOfCoast - 1e-8, "never noses into the coast: ${r.minBodyCoastClearance}")
    }

    @Test
    fun `pattern defaults merge by stage id and the JSON reader keeps order`() {
        val c = CritterSpec.parse("""{"id":"w","name":"W","length":1,"layer":"surface","altitude":[-1,0],"zone":"open-sea","clear_of_coast":0.5,
            "movement":{"speed":0.2,"turn":30},"animations":[{"name":"swim","seconds":4,"mode":"loop"},{"name":"beat","seconds":2,"mode":"once"},
            {"name":"blow","seconds":1,"mode":"once"},{"name":"dive","seconds":3,"mode":"once"},{"name":"surface","seconds":3,"mode":"once"}],
            "behavior":{"pattern":"surface-breather","stages":[{"id":"blow","seconds":[1,2]}]}}""")
        assertEquals(listOf("surface", "cruise", "turn", "blow", "beat", "low", "dive", "hidden"), c.stages.map { it.id })
        val blow = c.stages.first { it.id == "blow" }
        assertEquals(1.0, blow.seconds[0]); assertEquals("blow:d50", blow.anim)   // the rest from the pattern
        assertEquals(listOf("cruise" to 2.0, "blow" to 1.0), blow.next)
    }

    @Test
    fun `the critter board is the game's standard island (land hexes coincide)`() {
        val b = wgbg.engine.Rules.newGame(wgbg.engine.GameConfig(), 1).board
        val game = (0 until b.hexCount).map { hexLattice(b, it) }
        val sim = CritterBoard.STANDARD.landCentres
        assertEquals(game.size, sim.size)
        // The game's lattice has y down; the critter board has y up.
        for (p in sim) assertTrue(game.any { kotlin.math.abs(it.x - p.x) < 1e-3 && kotlin.math.abs(it.y + p.y) < 1e-3 }, "land at ${p.x},${p.y}")
    }

    @Test
    fun `the random stream and hypot match JavaScript's`() {
        // node -e "const {rng}=require('./sim');const r=rng(1);console.log(r(),r(),r())"
        val r = CritterRandom(1)
        assertEquals(0.6270739405881613, r.next(), 0.0)
        assertEquals(0.002735721180215478, r.next(), 0.0)
        assertEquals(0.5274470399599522, r.next(), 0.0)
        assertEquals(0.37416573867739417, jsHypot(0.1, 0.2, 0.3), 0.0)
        assertEquals(3.0, jsHypot(3.0, 0.0))
        assertEquals(5.0, jsHypot(3.0, 4.0))
    }

    @Test
    fun `the whale's frames follow its clips, and it hides under water`() {
        val c = whaleModule.spec
        fun at(anim: String?, t: Double, opacity: Double = 1.0) = whaleModule.frameFor(CritterPose(0.0, 0.0, 0.0, 0.0, "x", anim, opacity, t))
        fun name(f: CritterFrame?) = f?.pose
        assertEquals("d50", name(at("swim:d50", 1.0)))
        assertEquals("d25", name(at("swim:d25", 3.9)))
        assertEquals("d50_beat00", name(at("beat:d50", 0.0)))
        assertEquals("d50_beat07", name(at("beat:d50", 2.0)))              // a once clip holds its last frame
        assertEquals("d50_blow00", name(at("blow:d50", 0.1)))
        assertEquals("d50_blow02", name(at("blow:d50", 1.4)))
        assertEquals(null, at(null, 0.0))                                   // hidden
        assertTrue(at("surface", 0.0)!!.alpha < 0.05f && at("surface", 3.0)!!.alpha > 0.99f, "surfacing fades in")
        assertTrue(at("dive", 0.0)!!.alpha > 0.99f && at("dive", 3.0)!!.alpha < 0.05f, "diving fades out")
        assertEquals("d25", name(at("surface", 0.5))); assertEquals("d50", name(at("surface", 2.5)))   // stand-ins: rises d25 to d50
        assertEquals(0.5f, at("swim:d50", 1.0, opacity = 0.5)!!.alpha, 1e-6f)
        // Every pose the spec plays has a frame.
        for (st in c.stages) st.anim?.let { assertTrue(at(it, 0.5) != null, "${st.id} $it") }
    }

    @Test
    fun `whale headings - east is 0, up the screen is 2, counter-clockwise`() {
        val w = whaleModule
        assertEquals(0, w.headingIndex(0f, 1f))
        assertEquals(2, w.headingIndex(-(Math.PI / 2).toFloat(), 1f))     // lattice y down: moving up the screen
        assertEquals(4, w.headingIndex(Math.PI.toFloat(), 1f))
        assertEquals(6, w.headingIndex((Math.PI / 2).toFloat(), 1f))
        assertEquals((0..7).toList(), (0..7).map { w.tiltIndex(it) }, "8 tilts map one to one")
    }

    @Test
    fun `the whale is rolled once per game from the board, the same on every phone`() {
        val c = whaleModule
        val seeds = (1L..400L)
        val with = seeds.count { SeaCritter.forGame(listOf(c), it, 19).isNotEmpty() }
        assertEquals(0.6, with / 400.0, 0.08, "about the spec's 60% of games")
        val a = SeaCritter.forGame(listOf(c), 12345, 19); val b = SeaCritter.forGame(listOf(c), 12345, 19)
        assertEquals(a.size, b.size)
        if (a.isNotEmpty()) { repeat(50) { a[0].update(0.1f, VIEW); b[0].update(0.1f, VIEW) }; assertEquals(a[0].at, b[0].at) }
        assertTrue(SeaCritter.forGame(listOf(c), 12345, 30).isEmpty(), "not on a bigger island yet")
        val g = wgbg.engine.Rules.newGame(wgbg.engine.GameConfig(), 3)
        assertEquals(boardSeed(g), boardSeed(g.copy()))
        assertTrue(boardSeed(g) != boardSeed(wgbg.engine.Rules.newGame(wgbg.engine.GameConfig(), 4)))
    }

    @Test
    fun `in the open water in view the whale stays in view, clear of the coast, and keeps its budget`() {
        val c = CritterSpec.parse(whale)
        for (seed in listOf(1L, 961528515L, 1339055781L)) {
            val r = CritterRunner(c, seed, CritterBoard.forView(VIEW))
            var shown = 0; var inView = 0; var lowY = 99.0; var highY = -99.0
            repeat(36000) {
                r.step()
                if (r.pose().anim != null) { shown++
                    // Its centre is in view (the body may slip partly past an edge); lattice y is the board's -y.
                    if (r.x >= VIEW.left && r.x <= VIEW.right && -r.y >= VIEW.top && -r.y <= VIEW.bottom) inView++
                    lowY = minOf(lowY, -r.y); highY = maxOf(highY, -r.y) }
            }
            assertTrue(shown in 14400..25200, "seed $seed: up about half the time, as the spec budgets ($shown)")
            assertEquals(shown, inView, "seed $seed: always in view when surfaced")
            assertTrue(highY - lowY > 6, "seed $seed: swims the length of the strip, not treading water (${"%.1f".format(lowY)}..${"%.1f".format(highY)})")
            assertTrue(r.minBodyCoastClearance >= c.clearOfCoast - 1e-8, "seed $seed: clear of the coast")
            for (s in c.stages) if (s.share > 0) assertEquals(s.share, 100 * r.time.getValue(s.id) / r.accounted, 3.0, "seed $seed: share of ${s.id}")
        }
    }

    @Test
    fun `no open water in view means no whale, not a crash`() {
        val w = SeaCritter(whaleModule, 3)
        w.update(1f, androidx.compose.ui.geometry.Rect(-1f, -1f, 1f, 1f))   // only the middle of the island
        assertEquals(null, w.runner); assertEquals(null, w.pose())
    }

    @Test
    fun `the start search runs where it is launched (off the UI thread in the app), and the next frame picks it up`() {
        val jobs = ArrayList<() -> Unit>()
        val w = SeaCritter(whaleModule, 3) { jobs += it }
        w.update(0.1f, VIEW)
        assertEquals(null, w.runner, "the frame doesn't wait for the search")
        assertEquals(1, jobs.size)
        jobs.single()()
        w.update(0.1f, VIEW)
        assertTrue(w.runner != null && w.pose() != null)
        w.update(0.1f, VIEW); assertEquals(1, jobs.size, "searched once")
    }

    @Test
    fun `every shipped critter module loads and is complete`() {
        assertTrue(modules.isNotEmpty())
        for (m in modules) assertEquals(emptyList<String>(), m.problems(m.id), "critters/${m.id}")
        val w = whaleModule
        assertEquals(22, w.files.size, "the whale's 22 poses"); assertEquals(1408, w.files.values.sumOf { it.size })
        assertEquals("surface", w.spec.layer); assertEquals(2.16f, w.frames.span, 1e-6f)
    }

    @Test
    fun `a broken module is caught, and the app leaves it out`() {
        val json = whale.replace("\"layer\": \"surface\"", "\"layer\": \"lagoon\"")
        val names = whaleModule.files.flatMap { (p, hs) -> hs.map { (h, t) -> "${p}_h${h}_t$t.webp" } }
        val m = CritterModules.module("orca", names.filter { !it.startsWith("d50_blow01_h3") }, json)
        val bad = m.problems("orca")
        assertTrue(bad.any { "folder's name" in it }, "$bad"); assertTrue(bad.any { "lagoon" in it }, "$bad")
        assertTrue(bad.any { it.startsWith("d50_blow01: 8 of 64") }, "$bad")
        val noBlow = CritterModules.module("whale", names.filter { "blow" !in it }, whale)
        assertEquals("d50", noBlow.frameFor(CritterPose(0.0, 0.0, 0.0, 0.0, "blow", "blow:d50", 1.0, 0.5))?.pose, "no run of its own: it holds the pose")
        val noStandIn = CritterModules.module("whale", names, whale.replace(", \"dive\":{\"frames\":[\"d50\",\"d25\"], \"fade\":\"out\"}", ""))
        assertTrue(noStandIn.problems("whale").any { it.startsWith("dive: 'dive' has no frames") }, "a clip with no frames and no stand-in")
        // The app's loader skips it (the folder must hold a critter.json to count at all).
        val files = mapOf("critters" to listOf("whale", "notes.txt"), "critters/whale" to listOf("critter.json"), "critters/whale/frames" to emptyList<String>())
        assertEquals(0, CritterModules.load({ files[it].orEmpty() }, { json.byteInputStream() }).size)
    }

    private companion object {
        val ASSETS = File("src/main/assets")
        /** The sea in view at fit zoom on the Pixel 8 emulator, between the side panel and the zoom bars (as logged). */
        val VIEW = androidx.compose.ui.geometry.Rect(-7.8f, -6.1f, 4.6f, 5.8f)
    }
}
