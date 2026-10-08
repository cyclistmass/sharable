package com.mauro.wgbg

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import wgbg.engine.Resource
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * Procedural terrain illustrations for the hex tiles, plus small resource icons for ports.
 *
 * Art is authored in hex-local units (hex center at the origin, side length 1, pointy-top)
 * as lists of cached [Path]s. Tile art is then baked once into an [ImageBitmap] per
 * (terrain, variant, pixel size) and clipped to the tile's top face, so drawing a tile
 * each frame is a single drawImage — the full-speed sim never rebuilds geometry.
 * Light comes from the upper left; the upper-left quadrant is kept clear for the number token.
 */
object TerrainArt {
    private const val VARIANTS = 3
    /** Tiles are inset to 93% in the board view; the art is clipped to the same hexagon. */
    private const val INSET = 0.93f

    private class Item(val path: Path, val color: Color, val stroke: Float = 0f, val brush: Brush? = null)

    private val ALL = listOf(Resource.WOOD, Resource.BRICK, Resource.SHEEP, Resource.WHEAT, Resource.ORE, Resource.NONE)

    private val templates: Map<Int, List<List<Item>>> by lazy {
        ALL.associateWith { r -> List(VARIANTS) { v -> build(r, Random(r * 97 + v * 13 + 7)) } }
    }

    private val icons: Map<Int, List<Item>> by lazy { ALL.associateWith { buildIcon(it) } }

    /**
     * Baked bitmaps are made at quantized sizes on a 15% ladder, always at or above the requested
     * size, then drawn scaled down to fit — so pinch-zooming only rebakes when it crosses a step,
     * never every frame. The two most recent steps stay cached so zooming back and forth is free.
     * Above [MAX_BAKE_PX] (deep zoom: few tiles on screen) the vector art is drawn directly instead,
     * which keeps it sharp without huge bitmaps.
     */
    private const val LADDER = 1.15
    private const val MAX_BAKE_PX = 340
    private val tileCache = LinkedHashMap<Int, HashMap<Int, ImageBitmap>>()
    private val portCache = LinkedHashMap<Int, HashMap<Int, ImageBitmap>>()

    private fun ladderPx(scale: Float): Int {
        val step = ceil(kotlin.math.ln(scale.coerceAtLeast(1f).toDouble()) / kotlin.math.ln(LADDER))
        return ceil(Math.pow(LADDER, step)).toInt()
    }

    private fun cacheFor(cache: LinkedHashMap<Int, HashMap<Int, ImageBitmap>>, px: Int): HashMap<Int, ImageBitmap> {
        cache[px]?.let { cache.remove(px); cache[px] = it; return it } // move to most-recent
        val fresh = HashMap<Int, ImageBitmap>()
        cache[px] = fresh
        while (cache.size > 2) cache.remove(cache.keys.first())
        return fresh
    }

    /** Draws the terrain art for [resource] on the tile centered at [center] (hex side = [scale] px). */
    fun draw(scope: DrawScope, resource: Int, hexIndex: Int, center: Offset, scale: Float) {
        val items = templates.getValue(resource)[hexIndex % VARIANTS]
        val px = ladderPx(scale)
        if (px > MAX_BAKE_PX) {
            scope.drawVector(items, center, scale, hexClip(center, INSET * scale))
            return
        }
        val img = cacheFor(tileCache, px).getOrPut(resource * 16 + hexIndex % VARIANTS) { bake(items, px.toFloat()) }
        scope.drawBaked(img, center, scale / px)
    }

    /**
     * Fills a circle of [radius] at [center] with [resource]'s tile art, for port tokens.
     * Cached separately from the board tiles so the two sizes don't evict each other.
     */
    fun drawPortFill(scope: DrawScope, resource: Int, center: Offset, radius: Float) {
        // Size the tile so its inscribed circle covers the token.
        val scale = radius / (0.8f * INSET)
        val clip = Path().apply { addOval(Rect(center.x - radius, center.y - radius, center.x + radius, center.y + radius)) }
        val items = templates.getValue(resource)[0]
        val px = ladderPx(scale)
        if (px > MAX_BAKE_PX) {
            scope.drawVector(items, center, scale, clip)
            return
        }
        val img = cacheFor(portCache, px).getOrPut(resource) { bake(items, px.toFloat()) }
        scope.clipPath(clip) { drawBaked(img, center, scale / px) }
    }

    private fun DrawScope.drawBaked(img: ImageBitmap, center: Offset, k: Float) {
        val w = img.width * k
        val h = img.height * k
        drawImage(
            img,
            dstOffset = IntOffset((center.x - w / 2f).roundToInt(), (center.y - h / 2f).roundToInt()),
            dstSize = IntSize(w.roundToInt(), h.roundToInt()),
            filterQuality = FilterQuality.Medium,
        )
    }

    private fun DrawScope.drawVector(items: List<Item>, center: Offset, scale: Float, clip: Path) {
        clipPath(clip) {
            withTransform({
                translate(center.x, center.y)
                scale(scale, scale, Offset.Zero)
            }) { drawItems(items) }
        }
    }

    private fun hexClip(center: Offset, r: Float) = Path().apply {
        for (i in 0 until 6) {
            val a = (-90 + 60 * i) * PI / 180
            val x = center.x + r * cos(a).toFloat()
            val y = center.y + r * sin(a).toFloat()
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }

    /** Draws a small resource icon (or an anchor for [Resource.NONE]) of the given radius. */
    fun drawIcon(scope: DrawScope, resource: Int, center: Offset, radius: Float) {
        val items = icons[resource] ?: return
        scope.withTransform({
            translate(center.x, center.y)
            scale(radius, radius, Offset.Zero)
        }) { drawItems(items) }
    }

    private fun DrawScope.drawItems(items: List<Item>) {
        for (it in items) {
            val style = if (it.stroke > 0f) Stroke(it.stroke, cap = StrokeCap.Round, join = StrokeJoin.Round) else Fill
            if (it.brush != null) drawPath(it.path, it.brush, style = style) else drawPath(it.path, it.color, style = style)
        }
    }

    private fun bake(items: List<Item>, scale: Float): ImageBitmap {
        val w = ceil(1.8f * scale).toInt() + 2
        val h = ceil(2.0f * scale).toInt() + 2
        val img = ImageBitmap(w, h)
        val clip = Path().apply {
            for (i in 0 until 6) {
                val a = (-90 + 60 * i) * PI / 180
                val x = w / 2f + INSET * scale * cos(a).toFloat()
                val y = h / 2f + INSET * scale * sin(a).toFloat()
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(img), Size(w.toFloat(), h.toFloat())) {
            clipPath(clip) {
                withTransform({
                    translate(w / 2f, h / 2f)
                    scale(scale, scale, Offset.Zero)
                }) { drawItems(items) }
            }
        }
        return img
    }

    // ------------------------------------------------------------------ building blocks

    private fun build(resource: Int, rnd: Random): List<Item> = buildList {
        when (resource) {
            Resource.WOOD -> forest(rnd)
            Resource.BRICK -> clayHills(rnd)
            Resource.SHEEP -> pasture(rnd)
            Resource.WHEAT -> fields(rnd)
            Resource.ORE -> mountains(rnd)
            else -> desert(rnd)
        }
    }

    private fun o(x: Float, y: Float) = Offset(x, y)
    private fun Random.jitter(amount: Float) = (nextFloat() * 2 - 1) * amount

    private fun MutableList<Item>.fill(color: Color, pts: List<Offset>) = add(Item(poly(pts), color))
    private fun MutableList<Item>.line(color: Color, width: Float, pts: List<Offset>) = add(Item(polyline(pts), color, width))
    private fun MutableList<Item>.oval(color: Color, cx: Float, cy: Float, rx: Float, ry: Float) =
        add(Item(Path().apply { addOval(Rect(cx - rx, cy - ry, cx + rx, cy + ry)) }, color))

    /** Many same-colored shapes merged into one path: one draw call for e.g. all grass blades. */
    private fun MutableList<Item>.merged(color: Color, stroke: Float = 0f, block: Path.() -> Unit) =
        add(Item(Path().apply(block), color, stroke))

    private fun Path.ovalAt(cx: Float, cy: Float, rx: Float, ry: Float) = addOval(Rect(cx - rx, cy - ry, cx + rx, cy + ry))
    private fun Path.polyAt(pts: List<Offset>) {
        moveTo(pts[0].x, pts[0].y)
        for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y)
        close()
    }

    private fun poly(pts: List<Offset>) = Path().apply { polyAt(pts) }
    private fun polyline(pts: List<Offset>) = Path().apply {
        moveTo(pts[0].x, pts[0].y)
        for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y)
    }

    /** True inside the clearing kept for the number token (centered at (-0.36, -0.3)). */
    private fun inTokenClearing(x: Float, y: Float, r: Float = 0.36f): Boolean {
        val dx = x + 0.36f
        val dy = y + 0.3f
        return dx * dx + dy * dy < r * r
    }

    // ------------------------------------------------------------------ forest

    private fun MutableList<Item>.forest(rnd: Random) {
        val trunk = Color(0xFF4A2E14)
        var row = 0
        var y = -0.86f
        while (y < 1.0f) {
            val t = ((y + 0.86f) / 1.86f).coerceIn(0f, 1f) // 0 = back, 1 = front
            val litC = lerp(Color(0xFF2F7A35), Color(0xFF6BCB6F), t)
            val darkC = lerp(Color(0xFF0B3410), Color(0xFF1E6B24), t)
            val trees = ArrayList<Offset>()
            var x = -0.9f + (row % 2) * 0.1f + rnd.jitter(0.03f)
            while (x < 0.95f) {
                val p = o(x + rnd.jitter(0.03f), y + rnd.jitter(0.025f))
                if (!inTokenClearing(p.x, p.y, 0.33f)) trees += p
                x += 0.19f + rnd.nextFloat() * 0.04f
            }
            // Dark undergrowth along the row, behind the trunks.
            merged(lerp(Color(0xFF0E2A10), Color(0xFF1B4A1E), t)) {
                for (p in trees) ovalAt(p.x + 0.02f, p.y - 0.01f, 0.12f, 0.05f)
            }
            for (p in trees) {
                val s = 0.15f + 0.1f * t + rnd.nextFloat() * 0.05f
                line(trunk, 0.05f * s / 0.2f, listOf(p, o(p.x, p.y - 0.4f * s)))
                if (rnd.nextFloat() < 0.22f) {
                    // Round deciduous canopy.
                    val cy = p.y - 0.75f * s
                    oval(darkC, p.x + 0.06f * s, cy + 0.05f * s, 0.55f * s, 0.5f * s)
                    oval(lerp(darkC, litC, 0.6f), p.x - 0.04f * s, cy - 0.02f * s, 0.47f * s, 0.43f * s)
                    oval(litC.copy(alpha = 0.9f), p.x - 0.17f * s, cy - 0.16f * s, 0.22f * s, 0.18f * s)
                } else {
                    // Fir: three tiers (base y, half width, apex y) as multiples of s; left half lit.
                    for ((base, half, apex) in listOf(Triple(0.25f, 0.5f, 1.0f), Triple(0.6f, 0.4f, 1.35f), Triple(0.95f, 0.28f, 1.7f))) {
                        val by = p.y - base * s
                        val ay = p.y - apex * s
                        fill(litC, listOf(o(p.x - half * s, by), o(p.x, by + 0.07f * s), o(p.x, ay)))
                        fill(darkC, listOf(o(p.x, by + 0.07f * s), o(p.x + half * s, by), o(p.x, ay)))
                    }
                }
            }
            y += 0.15f
            row++
        }
    }

    // ------------------------------------------------------------------ clay pit

    /** Hills: design-canvas option D "Clay pit and kiln", ported from its SVG. */
    private fun MutableList<Item>.clayHills(rnd: Random) {
        val g = Svg()
        add(Item(g.path("M40,30 L480,30 L480,510 L40,510 Z"), Color.Unspecified, brush = Brush.linearGradient(
            0f to Color(0xFFD98A5A), 0.6f to Color(0xFFBF5B30), 1f to Color(0xFF94401F), start = g.p(40f, 30f), end = g.p(480f, 510f),
        )))
        val s = Svg(rnd.jitter(0.015f), rnd.jitter(0.01f))
        // Thin grassy rim along the very top.
        svgFill(s, 0xFF8DB360, "M40,30 L480,30 L480,92 Q400,110 330,94 Q260,78 190,98 Q120,114 40,98 Z")
        svgStroke(s, 0xFF6E8F45, 5f, "M40,98 Q120,114 190,98 Q260,78 330,94 Q400,110 480,92")
        // Rich light clay, then two rolling red hills.
        svgFill(s, 0xFFE3A070, "M40,150 Q110,104 200,140 Q290,100 370,132 Q430,112 480,124 L480,520 L40,520 Z")
        svgStroke(s, 0xFFF6C9A2, 5f, "M40,150 Q110,104 200,140 Q290,100 370,132 Q430,112 480,124")
        svgStroke(s, 0xFFC9673A, 3f, "M96,132 Q110,126 124,132 M300,118 Q316,112 332,118 M412,126 Q424,121 436,126")
        svgFill(s, 0xFFD98A5A, "M40,205 Q120,150 210,198 Q300,150 390,192 Q440,172 480,182 L480,520 L40,520 Z")
        svgStroke(s, 0xFFF0B88E, 5f, "M40,205 Q120,150 210,198 Q300,150 390,192 Q440,172 480,182")
        svgFill(s, 0xFFC9673A, "M40,262 Q150,212 262,258 Q372,214 480,248 L480,520 L40,520 Z")
        svgStroke(s, 0xFFE59A6A, 5f, "M40,262 Q150,212 262,258 Q372,214 480,248")
        // Grass tufts (the one that sat under the number token moved left).
        svgStroke(s, 0xFF6E8F45, 3f, "M92,166 l4,-10 l3,10 l4,-8 l2,8 M352,168 l4,-10 l3,10 l4,-8 l2,8 M84,236 l4,-10 l3,10 l4,-8 l2,8")
        // Terraced pit with water and a ladder, nudged down out from under the number token.
        val pit = Svg(s.dx, s.dy + 25f * Svg.K)
        svgOval(pit, 0xFFA84A24, 200f, 300f, 130f, 80f)
        svgOval(pit, 0xFFD77B4C, 200f, 292f, 130f, 74f)
        svgOval(pit, 0xFF94401F, 200f, 312f, 96f, 54f)
        svgOval(pit, 0xFFC9673A, 200f, 306f, 96f, 50f)
        svgOval(pit, 0xFF7A3518, 200f, 322f, 60f, 32f)
        svgOval(pit, 0xFFB05A30, 200f, 318f, 60f, 28f)
        svgOval(pit, 0xFF5B8FB0, 200f, 326f, 30f, 12f, alpha = 0.85f)
        svgStroke(pit, 0xFFFFFFFF, 2f, "M178,326 Q186,322 194,326 M206,328 Q214,324 222,328", alpha = 0.7f)
        svgStroke(pit, 0xFF5A3A1C, 3f, "M268,256 L252,312 M280,260 L264,316")
        svgStroke(pit, 0xFF8A5A2B, 3f, "M265,270 L277,273 M261,286 L273,289 M257,302 L269,305")
        // Beehive kiln with a fire glow, chimney and smoke.
        svgOval(s, 0xFF2B2D36, 370f, 352f, 62f, 14f, alpha = 0.25f)
        svgFill(s, 0xFFC9673A, "M316,350 Q316,250 370,240 Q424,250 424,350 Z")
        svgFill(s, 0xFF94401F, "M370,240 Q424,250 424,350 L380,350 Q392,280 370,240 Z")
        svgStroke(s, 0xFF7A3518, 2.5f, "M326,300 Q370,292 414,300 M320,326 Q370,318 420,326 M334,272 Q370,266 406,272")
        svgFill(s, 0xFF3A1A0C, "M352,350 L352,326 Q370,308 388,326 L388,350 Z")
        add(Item(s.path(circle(370f, 340f, 22f)), Color.Unspecified, brush = Brush.radialGradient(
            0f to Color(0xFFFFC861), 0.6f to Color(0xFFF07A2A), 1f to Color(0x00F07A2A), center = s.p(370f, 340f), radius = s.len(22f),
        )))
        svgRect(s, 0xFF7A3518, 362f, 222f, 16f, 22f)
        svgRect(s, 0xFF94401F, 358f, 218f, 24f, 7f)
        svgFill(s, 0xFFE8E4DD, circle(372f, 200f, 11f), alpha = 0.9f)
        svgFill(s, 0xFFEEEAE4, circle(384f, 182f, 14f), alpha = 0.8f)
        svgFill(s, 0xFFF4F1EC, circle(400f, 160f, 17f), alpha = 0.7f)
        // Brick stacks on pallets.
        svgRect(s, 0xFF8A5A2B, 96f, 430f, 74f, 8f)
        svgRect(s, 0xFFC9673A, 100f, 414f, 20f, 16f); svgRect(s, 0xFFB85C30, 122f, 414f, 20f, 16f); svgRect(s, 0xFFC9673A, 144f, 414f, 20f, 16f)
        svgRect(s, 0xFFD77B4C, 110f, 398f, 20f, 16f); svgRect(s, 0xFFC9673A, 132f, 398f, 20f, 16f)
        svgRect(s, 0xFFE09060, 121f, 382f, 20f, 16f)
        svgStroke(s, 0xFF6E2E14, 2f, "M100,414 h64 M110,398 h42")
        svgRect(s, 0xFF8A5A2B, 250f, 452f, 62f, 8f)
        svgRect(s, 0xFFC9673A, 254f, 436f, 18f, 16f); svgRect(s, 0xFFB85C30, 274f, 436f, 18f, 16f); svgRect(s, 0xFFC9673A, 294f, 436f, 16f, 16f)
        svgRect(s, 0xFFD77B4C, 263f, 420f, 18f, 16f); svgRect(s, 0xFFC9673A, 283f, 420f, 18f, 16f)
        svgStroke(s, 0xFF6E2E14, 2f, "M254,436 h56")
        // Wheelbarrow.
        svgPoly(s, 0xFF6B6F7E, "190,452 230,452 222,470 196,470")
        svgPoly(s, 0xFFC9673A, "194,446 226,446 230,452 190,452")
        svgFill(s, 0xFF2B2D36, circle(200f, 476f, 7f))
        svgStroke(s, 0xFF5A3A1C, 3f, "M226,462 L246,456")
    }

    private fun MutableList<Item>.brickStack(at: Offset, k: Float) {
        val w = 0.2f * k
        val h = 0.09f * k
        val gap = 0.022f * k
        oval(Color.Black.copy(alpha = 0.25f), at.x + 1.5f * w + 0.05f * k, at.y + 0.03f * k, 1.75f * w, 0.07f * k)
        for ((row, count) in listOf(3, 2, 1).withIndex()) {
            val y = at.y - row * (h + gap)
            val x0 = at.x + row * (w + gap) / 2
            for (i in 0 until count) {
                val x = x0 + i * (w + gap)
                fill(Color(0xFFE9D8C0), listOf(o(x - gap / 2, y + gap / 2), o(x + w + gap / 2, y + gap / 2), o(x + w + gap / 2, y - h - gap / 2), o(x - gap / 2, y - h - gap / 2)))
                fill(Color(0xFFB0412A), listOf(o(x, y), o(x + w, y), o(x + w, y - h), o(x, y - h)))
                fill(Color(0xFFDE7352), listOf(o(x, y - h), o(x + w, y - h), o(x + w, y - 0.68f * h), o(x, y - 0.68f * h)))
                fill(Color(0xFF7E2A19), listOf(o(x + 0.82f * w, y), o(x + w, y), o(x + w, y - h), o(x + 0.82f * w, y - h)))
            }
        }
    }

    // ------------------------------------------------------------------ pasture with alpacas

    private fun MutableList<Item>.pasture(rnd: Random) {
        // Dense grass: darker and lighter blades, each layer merged into one path.
        for ((color, count) in listOf(Color(0xFF5E8F2E) to 170, Color(0xFFB8E07A) to 110)) {
            merged(color, 0.022f) {
                repeat(count) {
                    val x = rnd.nextFloat() * 1.8f - 0.9f
                    val y = rnd.nextFloat() * 1.9f - 0.95f
                    if (inTokenClearing(x, y, 0.3f)) return@repeat
                    moveTo(x - 0.03f, y - 0.06f); lineTo(x, y); lineTo(x + 0.035f, y - 0.075f)
                }
            }
        }
        // Wildflowers.
        for (color in listOf(Color(0xFFFFFFFF), Color(0xFFFFD54F), Color(0xFFE57399))) {
            merged(color) {
                repeat(9) {
                    val x = rnd.nextFloat() * 1.6f - 0.8f
                    val y = rnd.nextFloat() * 1.7f - 0.85f
                    if (!inTokenClearing(x, y, 0.32f)) ovalAt(x, y, 0.022f, 0.02f)
                }
            }
        }
        // Round hay bales.
        val bales = listOf(o(-0.45f, 0.55f), o(0.55f, -0.25f), o(-0.1f, 0.78f)).shuffled(rnd).take(2)
        for (b in bales.sortedBy { it.y }) hayBale(b, 0.9f + rnd.nextFloat() * 0.2f)
        // Alpacas, back to front.
        val spots = listOf(o(0.35f, -0.05f), o(0.5f, 0.45f), o(0.0f, 0.5f), o(0.25f, 0.82f), o(-0.3f, 0.3f))
            .shuffled(rnd).take(3).sortedBy { it.y }
        val coats = listOf(Color(0xFFFFFBF2), Color(0xFFF1DDB8), Color(0xFFC79B6D)).shuffled(rnd)
        spots.forEachIndexed { i, p -> alpaca(p, 1.3f + 0.12f * i, coats[i], faceLeft = rnd.nextBoolean()) }
    }

    private fun MutableList<Item>.hayBale(c: Offset, k: Float) {
        val r = 0.13f * k
        oval(Color.Black.copy(alpha = 0.22f), c.x + 0.05f * k, c.y + r * 0.85f, r * 1.3f, r * 0.35f)
        // Cylinder lying on its side: body, lit top, and the round face with a spiral.
        fill(Color(0xFFC8962F), listOf(o(c.x - 0.02f, c.y - r), o(c.x + 1.3f * r, c.y - r), o(c.x + 1.3f * r, c.y + r), o(c.x - 0.02f, c.y + r)))
        add(Item(Path().apply { ovalAt(c.x + 1.3f * r, c.y, 0.45f * r, r) }, Color(0xFFA9781F)))
        fill(Color(0xFFE8BD55), listOf(o(c.x - 0.02f, c.y - r), o(c.x + 1.3f * r, c.y - r), o(c.x + 1.3f * r, c.y - 0.55f * r), o(c.x - 0.02f, c.y - 0.55f * r)))
        oval(Color(0xFFF2CC66), c.x, c.y, 0.55f * r, r)
        add(Item(Path().apply {
            ovalAt(c.x, c.y, 0.38f * r, 0.7f * r); ovalAt(c.x, c.y, 0.2f * r, 0.38f * r)
        }, Color(0xFFB8862A), 0.018f * k))
    }

    /** A fluffy alpaca standing at [p] (feet), facing left or right. */
    private fun MutableList<Item>.alpaca(p: Offset, k: Float, coat: Color, faceLeft: Boolean) {
        val d = if (faceLeft) -1f else 1f
        val shade = lerp(coat, Color(0xFF5A4630), 0.28f)
        val hi = lerp(coat, Color.White, 0.6f)
        val dark = Color(0xFF3A2C22)
        fun x(dx: Float) = p.x + d * dx * k
        fun y(dy: Float) = p.y + dy * k
        oval(Color.Black.copy(alpha = 0.22f), p.x, y(0.01f), 0.17f * k, 0.045f * k)
        // Thin legs with fluffy tops.
        for (lx in listOf(-0.09f, -0.04f, 0.05f, 0.1f)) line(dark, 0.022f * k, listOf(o(x(lx), y(-0.1f)), o(x(lx), y(0f))))
        // Scalloped cloud body: shade layer, coat layer, highlight puffs.
        val body = listOf(
            o(-0.1f, -0.15f), o(-0.04f, -0.18f), o(0.03f, -0.18f), o(0.1f, -0.15f),
            o(-0.12f, -0.11f), o(-0.05f, -0.1f), o(0.03f, -0.1f), o(0.11f, -0.1f),
        )
        merged(shade) { for (q in body) ovalAt(x(q.x) + 0.012f * k, y(q.y) + 0.014f * k, 0.055f * k, 0.05f * k) }
        merged(coat) { for (q in body) ovalAt(x(q.x), y(q.y), 0.05f * k, 0.046f * k) }
        // Fluffy tail.
        oval(coat, x(-0.15f), y(-0.16f), 0.03f * k, 0.035f * k)
        // Long upright fluffy neck at the front.
        merged(shade) { for (i in 0..4) ovalAt(x(0.1f) + 0.01f * k, y(-0.2f - i * 0.05f) + 0.01f * k, 0.04f * k, 0.035f * k) }
        merged(coat) { for (i in 0..4) ovalAt(x(0.1f), y(-0.2f - i * 0.05f), 0.036f * k, 0.032f * k) }
        merged(hi) {
            for (q in listOf(o(-0.07f, -0.2f), o(0.0f, -0.21f))) ovalAt(x(q.x), y(q.y), 0.025f * k, 0.016f * k)
            ovalAt(x(0.085f), y(-0.36f), 0.012f * k, 0.035f * k)
        }
        // Head with snout, banana ears and a puffy topknot.
        oval(coat, x(0.13f), y(-0.43f), 0.045f * k, 0.035f * k)
        oval(lerp(coat, dark, 0.15f), x(0.17f), y(-0.415f), 0.03f * k, 0.022f * k)
        oval(dark, x(0.195f), y(-0.42f), 0.008f * k, 0.007f * k)
        oval(dark, x(0.135f), y(-0.445f), 0.008f * k, 0.008f * k)
        add(Item(Path().apply {
            moveTo(x(0.1f), y(-0.46f)); quadraticTo(x(0.07f), y(-0.52f), x(0.095f), y(-0.55f))
            moveTo(x(0.135f), y(-0.465f)); quadraticTo(x(0.125f), y(-0.53f), x(0.15f), y(-0.56f))
        }, shade, 0.018f * k))
        merged(hi) {
            for (q in listOf(o(0.1f, -0.47f), o(0.125f, -0.48f), o(0.112f, -0.495f), o(0.14f, -0.47f))) ovalAt(x(q.x), y(q.y), 0.02f * k, 0.018f * k)
        }
    }

    // ------------------------------------------------------------------ wheat fields

    private fun MutableList<Item>.fields(rnd: Random) {
        val furrow = Color(0xFFC99A2E)
        val bright = Color(0xFFFFE08A)
        val grain = Color(0xFF8C6418)
        val offset = rnd.jitter(0.08f)
        merged(furrow, 0.07f) {
            for (i in -7..7) { val d = i * 0.24f + offset; moveTo(-1.2f, -0.66f + d * 1.15f); lineTo(1.2f, 0.66f + d * 1.15f) }
        }
        merged(bright, 0.025f) {
            for (i in -7..7) { val d = i * 0.24f + offset; moveTo(-1.2f, -0.72f + d * 1.15f); lineTo(1.2f, 0.6f + d * 1.15f) }
        }
        merged(grain, 0.022f) {
            for (i in -7..7) {
                val d = i * 0.24f + offset
                var t = 0.05f + (i and 1) * 0.06f
                while (t < 1f) {
                    val gx = -1.2f + 2.4f * t
                    val gy = -0.66f + d * 1.15f + 1.32f * t - 0.03f
                    if (!inTokenClearing(gx, gy, 0.3f)) { moveTo(gx - 0.02f, gy + 0.03f); lineTo(gx + 0.01f, gy - 0.04f) }
                    t += 0.11f
                }
            }
        }
        // Haystack.
        val hx = 0.4f + rnd.jitter(0.06f)
        val hy = 0.34f + rnd.jitter(0.06f)
        oval(Color.Black.copy(alpha = 0.25f), hx + 0.05f, hy + 0.01f, 0.25f, 0.07f)
        add(Item(Path().apply { moveTo(hx - 0.23f, hy); cubicTo(hx - 0.21f, hy - 0.45f, hx + 0.21f, hy - 0.45f, hx + 0.23f, hy); close() }, Color(0xFFE6B84A)))
        add(Item(Path().apply { moveTo(hx + 0.02f, hy); cubicTo(hx + 0.05f, hy - 0.32f, hx + 0.19f, hy - 0.3f, hx + 0.23f, hy); close() }, Color(0xFFB98A28)))
        add(Item(Path().apply { moveTo(hx - 0.17f, hy - 0.08f); cubicTo(hx - 0.15f, hy - 0.31f, hx - 0.04f, hy - 0.34f, hx, hy - 0.33f) }, Color(0xFFFFE9A6), 0.035f))
        merged(grain, 0.015f) { for (n in 0..3) { moveTo(hx - 0.15f + n * 0.1f, hy - 0.02f); lineTo(hx - 0.1f + n * 0.08f, hy - 0.2f) } }
    }

    // ------------------------------------------------------------------ mountains

    /** Mountains: design-canvas option A2.1 "Bigger crowd", ported from its SVG and spread 13% wider, in the A2.3 blue-slate palette. */
    private fun MutableList<Item>.mountains(rnd: Random) {
        val g = Svg()
        add(Item(g.path("M40,30 L480,30 L480,510 L40,510 Z"), Color.Unspecified, brush = Brush.linearGradient(
            0f to Color(0xFFD3DEEB), 0.5f to Color(0xFF97A9BE), 1f to Color(0xFF6A7F7A), start = g.p(40f, 30f), end = g.p(480f, 510f),
        )))
        val jx = rnd.jitter(0.02f)
        val jy = rnd.jitter(0.015f)
        // The range is spread 13% wider than the design around the tile's center so it fills the width.
        val s = Svg(jx, jy, xScale = 1.13f)
        // A crowd of 22 peaks, back (hazy) to front; each is lit face, shaded face, then two-tone snow.
        for ((color, pts) in PEAK_CROWD) svgPoly(s, color, pts)
        // Pines at the foot of the crowd.
        for ((n, xy) in listOf(136f to 396f, 150f to 404f, 358f to 404f, 372f to 412f).withIndex()) {
            val (x, y) = xy
            val front = n % 2 == 0
            svgPoly(s, if (front) 0xFF24503E else 0xFF1E4636, "${x - 8},${y + 24} $x,$y ${x + 8},${y + 24}")
            svgPoly(s, if (front) 0xFF16392C else 0xFF123226, "$x,$y ${x + 8},${y + 24} $x,${y + 24}")
        }
        // One cloud, upper right (clear of the number token).
        val c = Svg(jx + rnd.jitter(0.04f), jy)
        svgOval(c, 0xFF4A5E80, 372f, 176f, 58f, 12f, alpha = 0.3f)
        svgOval(c, 0xFFFFFFFF, 350f, 160f, 30f, 20f)
        svgOval(c, 0xFFFFFFFF, 380f, 150f, 34f, 26f)
        svgOval(c, 0xFFFFFFFF, 410f, 162f, 26f, 18f)
        svgOval(c, 0xFFDCE6F3, 380f, 170f, 56f, 12f)
    }

    /** Option A2.1 "Bigger crowd": 22 peaks in five rows, back (hazy) to front, as SVG polygons. */
    private val PEAK_CROWD: List<Pair<Long, String>> = listOf(
        0xFFB4C4D8 to "118,200 161,120 166,200", 0xFF7186A6 to "161,120 204,200 166,200",
        0xFFF2F7FD to "161,120 143,154 148,151 152,160 157,152 162,159 163,144", 0xFFC9D8EC to "161,120 163,144 165,157 169,149 174,155 180,152",
        0xFFB4C4D8 to "161,204 208,115 214,204", 0xFF7186A6 to "208,115 255,204 214,204",
        0xFFF2F7FD to "208,115 188,152 194,149 199,159 204,150 209,157 210,141", 0xFFC9D8EC to "208,115 210,141 213,156 218,147 222,154 229,150",
        0xFFB4C4D8 to "225,202 269,120 275,202", 0xFF7186A6 to "269,120 314,202 275,202",
        0xFFF2F7FD to "269,120 251,154 256,151 260,161 266,153 270,159 271,144", 0xFFC9D8EC to "269,120 271,144 274,157 278,149 283,156 289,153",
        0xFFB4C4D8 to "268,200 310,109 315,200", 0xFF7186A6 to "310,109 351,200 315,200",
        0xFFF2F7FD to "310,109 292,148 297,144 301,155 306,146 311,153 311,137", 0xFFC9D8EC to "310,109 311,137 314,151 318,142 322,149 328,146",
        0xFFB4C4D8 to "319,209 365,128 371,209", 0xFF7186A6 to "365,128 412,209 371,209",
        0xFFF2F7FD to "365,128 346,162 351,159 356,168 362,160 366,167 367,152", 0xFFC9D8EC to "365,128 367,152 370,165 375,157 379,163 386,160",
        0xFFA9BCD3 to "65,257 117,151 123,257", 0xFF4A5E80 to "117,151 169,257 123,257",
        0xFFFFFFFF to "117,151 95,195 101,191 107,204 113,193 118,202 119,183", 0xFFB9CBE3 to "117,151 119,183 122,199 127,189 133,197 140,193",
        0xFFA9BCD3 to "130,250 181,147 187,250", 0xFF4A5E80 to "181,147 232,250 187,250",
        0xFFFFFFFF to "181,147 160,190 166,186 171,199 177,188 182,197 183,178", 0xFFB9CBE3 to "181,147 183,178 186,195 191,184 196,193 203,188",
        0xFFA9BCD3 to "180,251 230,158 236,251", 0xFF4A5E80 to "230,158 280,251 236,251",
        0xFFFFFFFF to "230,158 209,197 215,193 220,204 226,195 231,203 232,186", 0xFFB9CBE3 to "230,158 232,186 235,201 240,191 245,199 252,195",
        0xFFA9BCD3 to "247,259 297,163 303,259", 0xFF4A5E80 to "297,163 347,259 303,259",
        0xFFFFFFFF to "297,163 276,203 282,199 287,211 293,201 298,209 299,192", 0xFFB9CBE3 to "297,163 299,192 302,207 307,197 312,205 319,201",
        0xFFA9BCD3 to "298,256 352,150 358,256", 0xFF4A5E80 to "352,150 405,256 358,256",
        0xFFFFFFFF to "352,150 329,195 336,190 341,203 347,193 353,201 354,182", 0xFFB9CBE3 to "352,150 354,182 357,199 362,188 367,197 375,193",
        0xFFA9BCD3 to "349,254 400,155 406,254", 0xFF4A5E80 to "400,155 451,254 406,254",
        0xFFFFFFFF to "400,155 379,197 385,193 390,205 396,195 401,203 402,185", 0xFFB9CBE3 to "400,155 402,185 405,201 410,191 415,199 422,195",
        0xFFA9BCD3 to "78,308 135,203 141,308", 0xFF4A5E80 to "135,203 191,308 141,308",
        0xFFFFFFFF to "135,203 111,247 118,243 123,255 130,245 136,253 137,234", 0xFFB9CBE3 to "135,203 137,234 140,251 146,241 152,249 159,245",
        0xFFA9BCD3 to "140,312 193,198 199,312", 0xFF4A5E80 to "193,198 245,312 199,312",
        0xFFFFFFFF to "193,198 171,246 177,241 182,255 189,243 194,252 195,232", 0xFFB9CBE3 to "193,198 195,232 198,250 203,239 208,248 216,243",
        0xFFA9BCD3 to "204,308 262,201 268,308", 0xFF4A5E80 to "262,201 320,308 268,308",
        0xFFFFFFFF to "262,201 237,246 244,241 250,254 257,244 263,252 264,233", 0xFFB9CBE3 to "262,201 264,233 267,250 273,239 279,248 287,244",
        0xFFA9BCD3 to "269,306 324,197 330,306", 0xFF4A5E80 to "324,197 379,306 330,306",
        0xFFFFFFFF to "324,197 300,243 307,239 313,252 319,241 325,250 326,230", 0xFFB9CBE3 to "324,197 326,230 329,248 335,237 340,245 348,241",
        0xFFA9BCD3 to "330,313 383,199 389,313", 0xFF4A5E80 to "383,199 435,313 389,313",
        0xFFFFFFFF to "383,199 361,247 367,242 372,256 378,245 384,254 385,233", 0xFFB9CBE3 to "383,199 385,233 388,251 393,240 398,249 406,245",
        0xFFB3C5DB to "124,360 176,248 183,360", 0xFF3B4D6E to "176,248 229,360 183,360",
        0xFFFFFFFF to "176,248 154,295 161,291 166,304 172,293 178,302 179,282", 0xFFB2C6E0 to "176,248 179,282 182,300 187,288 192,297 200,293",
        0xFFB3C5DB to "185,366 239,252 246,366", 0xFF3B4D6E to "239,252 293,366 246,366",
        0xFFFFFFFF to "239,252 217,300 223,296 228,309 235,298 240,307 241,287", 0xFFB2C6E0 to "239,252 241,287 245,305 250,293 255,303 263,298",
        0xFFB3C5DB to "242,365 295,244 301,365", 0xFF3B4D6E to "295,244 347,365 301,365",
        0xFFFFFFFF to "295,244 273,295 279,290 284,304 291,292 296,302 297,280", 0xFFB2C6E0 to "295,244 297,280 300,299 305,287 311,297 318,292",
        0xFFB3C5DB to "308,370 366,245 373,370", 0xFF3B4D6E to "366,245 424,370 373,370",
        0xFFFFFFFF to "366,245 342,298 349,293 355,308 362,295 367,305 369,283", 0xFFB2C6E0 to "366,245 369,283 372,303 378,290 384,300 392,295",
        0xFFB3C5DB to "156,417 212,308 219,417", 0xFF3B4D6E to "212,308 268,417 219,417",
        0xFFFFFFFF to "212,308 189,354 196,350 201,363 208,352 213,361 215,341", 0xFFB2C6E0 to "212,308 215,341 218,358 223,348 229,356 237,352",
        0xFFB3C5DB to "235,425 289,316 295,425", 0xFF3B4D6E to "289,316 342,425 295,425",
        0xFFFFFFFF to "289,316 266,362 272,357 278,370 284,359 290,368 291,348", 0xFFB2C6E0 to "289,316 291,348 294,366 299,355 305,364 312,359",
    )

    // ------------------------------------------------------------------ SVG-authored tiles

    /**
     * Maps design-canvas SVG coordinates (hex centered at (260,270), circumradius 230) into tile
     * units, sized so the SVG's hex outline lands exactly on the inset clip: the board crops the
     * art the same way the design did. [dx]/[dy] shift the whole composition (per-variant jitter);
     * [xScale] widens it around the center.
     */
    private class Svg(val dx: Float = 0f, val dy: Float = 0f, val xScale: Float = 1f) {
        fun x(v: Float) = (v - 260f) * K * xScale + dx
        fun y(v: Float) = (v - 270f) * K + dy
        fun p(px: Float, py: Float) = Offset(x(px), y(py))
        fun len(v: Float) = v * K

        companion object {
            const val K = INSET / 230f
        }
    }

    private val SVG_TOKEN = Regex("[MmLlHhVvQqZz]|-?(?:\\d+\\.?\\d*|\\.\\d+)")

    /** Parses the SVG path subset the designs use: M L H V Q Z, absolute and relative. */
    private fun Svg.path(d: String): Path {
        val t = SVG_TOKEN.findAll(d).map { it.value }.toList()
        val path = Path()
        var i = 0
        var cmd = 'M'
        var cx = 0f
        var cy = 0f
        var sx = 0f
        var sy = 0f
        fun num() = t[i++].toFloat()
        while (i < t.size) {
            if (t[i][0].isLetter()) cmd = t[i++][0]
            val rel = cmd.isLowerCase()
            when (cmd.uppercaseChar()) {
                'M' -> {
                    cx = num() + if (rel) cx else 0f
                    cy = num() + if (rel) cy else 0f
                    sx = cx; sy = cy
                    path.moveTo(x(cx), y(cy))
                    cmd = if (rel) 'l' else 'L' // further pairs are implicit line-tos
                }
                'L' -> {
                    cx = num() + if (rel) cx else 0f
                    cy = num() + if (rel) cy else 0f
                    path.lineTo(x(cx), y(cy))
                }
                'H' -> { cx = num() + if (rel) cx else 0f; path.lineTo(x(cx), y(cy)) }
                'V' -> { cy = num() + if (rel) cy else 0f; path.lineTo(x(cx), y(cy)) }
                'Q' -> {
                    val x1 = num() + if (rel) cx else 0f
                    val y1 = num() + if (rel) cy else 0f
                    val x2 = num() + if (rel) cx else 0f
                    val y2 = num() + if (rel) cy else 0f
                    path.quadraticTo(x(x1), y(y1), x(x2), y(y2))
                    cx = x2; cy = y2
                }
                'Z' -> { path.close(); cx = sx; cy = sy }
            }
        }
        return path
    }

    /** A circle as an SVG path (four quadratic arcs; close enough at tile sizes). */
    private fun circle(cx: Float, cy: Float, r: Float) =
        "M${cx - r},$cy Q${cx - r},${cy - r} $cx,${cy - r} Q${cx + r},${cy - r} ${cx + r},$cy " +
            "Q${cx + r},${cy + r} $cx,${cy + r} Q${cx - r},${cy + r} ${cx - r},$cy Z"

    private fun MutableList<Item>.svgFill(s: Svg, color: Long, d: String, alpha: Float = 1f) =
        add(Item(s.path(d), Color(color).copy(alpha = alpha)))

    private fun MutableList<Item>.svgStroke(s: Svg, color: Long, width: Float, d: String, alpha: Float = 1f) =
        add(Item(s.path(d), Color(color).copy(alpha = alpha), s.len(width)))

    private fun MutableList<Item>.svgPoly(s: Svg, color: Long, points: String, alpha: Float = 1f) =
        svgFill(s, color, "M" + points.trim().replace(" ", " L") + " Z", alpha)

    private fun MutableList<Item>.svgRect(s: Svg, color: Long, x: Float, y: Float, w: Float, h: Float) =
        svgPoly(s, color, "$x,$y ${x + w},$y ${x + w},${y + h} $x,${y + h}")

    private fun MutableList<Item>.svgOval(s: Svg, color: Long, cx: Float, cy: Float, rx: Float, ry: Float, alpha: Float = 1f) =
        oval(Color(color).copy(alpha = alpha), s.x(cx), s.y(cy), s.len(rx), s.len(ry))

    // ------------------------------------------------------------------ desert

    private fun MutableList<Item>.desert(rnd: Random) {
        val shadow = Color(0xFFCDB880)
        val light = Color(0xFFF6EBC8)
        val ridge = Color(0xFFB59D5E)
        for ((i, y) in listOf(-0.35f, 0.1f, 0.55f, 0.95f).withIndex()) {
            val sway = rnd.jitter(0.1f) + if (i % 2 == 0) 0.15f else -0.1f
            add(Item(Path().apply {
                moveTo(-1.2f, y + 0.1f)
                cubicTo(-0.5f, y - 0.25f + sway, 0.2f, y + 0.2f, 1.2f, y - 0.1f)
                lineTo(1.2f, y + 0.12f)
                cubicTo(0.2f, y + 0.35f, -0.5f, y + sway * 0.3f, -1.2f, y + 0.28f)
                close()
            }, shadow))
            add(Item(Path().apply { moveTo(-1.2f, y + 0.1f); cubicTo(-0.5f, y - 0.25f + sway, 0.2f, y + 0.2f, 1.2f, y - 0.1f) }, ridge, 0.03f))
            add(Item(Path().apply { moveTo(-1.2f, y + 0.04f); cubicTo(-0.5f, y - 0.31f + sway, 0.2f, y + 0.14f, 1.2f, y - 0.16f) }, light, 0.03f))
        }
        cactus(o(0.42f + rnd.jitter(0.05f), 0.45f), 1f)
        cactus(o(0.05f + rnd.jitter(0.05f), 0.72f), 0.65f)
        // Bleached rock.
        oval(Color(0xFFBCA678), -0.35f, 0.55f, 0.07f, 0.035f)
        oval(Color(0xFFE9DDBB), -0.37f, 0.54f, 0.05f, 0.022f)
    }

    private fun MutableList<Item>.cactus(p: Offset, k: Float) {
        val green = Color(0xFF3E8E41)
        val lit = Color(0xFF7CC47F)
        val dark = Color(0xFF256628)
        oval(Color.Black.copy(alpha = 0.22f), p.x + 0.06f * k, p.y, 0.14f * k, 0.04f * k)
        val w = 0.06f * k
        val armL = listOf(o(p.x - 0.03f * k, p.y - 0.2f * k), o(p.x - 0.15f * k, p.y - 0.2f * k), o(p.x - 0.15f * k, p.y - 0.33f * k))
        val armR = listOf(o(p.x + 0.03f * k, p.y - 0.26f * k), o(p.x + 0.14f * k, p.y - 0.26f * k), o(p.x + 0.14f * k, p.y - 0.4f * k))
        line(dark, 0.075f * k, armL); line(dark, 0.075f * k, armR)
        line(green, 0.05f * k, armL); line(green, 0.05f * k, armR)
        line(dark, 2.1f * w, listOf(o(p.x, p.y), o(p.x, p.y - 0.5f * k)))
        line(green, 1.6f * w, listOf(o(p.x, p.y), o(p.x, p.y - 0.5f * k)))
        line(lit, 0.5f * w, listOf(o(p.x - 0.35f * w, p.y - 0.03f * k), o(p.x - 0.35f * w, p.y - 0.47f * k)))
    }

    // ------------------------------------------------------------------ port icons (unit radius ~1)

    private fun buildIcon(resource: Int): List<Item> = buildList {
        when (resource) {
            Resource.WOOD -> {
                line(Color(0xFF4A2E14), 0.16f, listOf(o(0f, 0.85f), o(0f, 0.45f)))
                for ((base, half, apex) in listOf(Triple(0.55f, 0.6f, -0.1f), Triple(0.15f, 0.48f, -0.5f), Triple(-0.25f, 0.34f, -0.9f))) {
                    fill(Color(0xFF5DBB63), listOf(o(-half, base), o(0f, base + 0.08f), o(0f, apex)))
                    fill(Color(0xFF175A1D), listOf(o(0f, base + 0.08f), o(half, base), o(0f, apex)))
                }
            }
            Resource.BRICK -> brickStack(o(-0.68f, 0.45f), 2.0f)
            Resource.SHEEP -> alpaca(o(-0.12f, 0.95f), 3.2f, Color(0xFFFFFBF2), faceLeft = false)
            Resource.WHEAT -> {
                val stalk = Color(0xFFB98A28)
                for (a in listOf(-0.35f, 0f, 0.35f)) line(stalk, 0.07f, listOf(o(0f, 0.85f), o(a * 1.4f, -0.45f)))
                for (a in listOf(-0.35f, 0f, 0.35f)) {
                    val tip = o(a * 1.4f, -0.45f)
                    for (n in 0..3) {
                        val c = tip + o(-a * 0.25f * n, 0.17f * n)
                        oval(Color(0xFFE6B84A), c.x - 0.08f, c.y, 0.08f, 0.11f)
                        oval(Color(0xFFF7D774), c.x + 0.08f, c.y, 0.08f, 0.11f)
                    }
                }
                line(Color(0xFF8C6418), 0.12f, listOf(o(-0.25f, 0.45f), o(0.25f, 0.45f)))
            }
            Resource.ORE -> {
                fill(Color(0xFFC3C3CF), listOf(o(-0.85f, 0.7f), o(0.05f, 0.7f), o(-0.05f, -0.75f)))
                fill(Color(0xFF4A4A57), listOf(o(0.05f, 0.7f), o(0.85f, 0.7f), o(-0.05f, -0.75f)))
                fill(Color.White, listOf(o(-0.05f, -0.75f), o(-0.38f, -0.22f), o(-0.1f, -0.3f), o(0.02f, -0.18f)))
                fill(Color(0xFFCBD5E6), listOf(o(-0.05f, -0.75f), o(0.02f, -0.18f), o(0.3f, -0.25f)))
            }
            else -> {
                // Anchor for generic 3:1 ports.
                val ink = Color(0xFF2B3A55)
                line(ink, 0.14f, listOf(o(0f, -0.6f), o(0f, 0.7f)))
                line(ink, 0.14f, listOf(o(-0.35f, -0.25f), o(0.35f, -0.25f)))
                add(Item(Path().apply { moveTo(-0.6f, 0.2f); quadraticTo(-0.5f, 0.8f, 0f, 0.75f); quadraticTo(0.5f, 0.8f, 0.6f, 0.2f) }, ink, 0.14f))
                add(Item(Path().apply { addOval(Rect(-0.17f, -0.9f, 0.17f, -0.56f)) }, ink, 0.1f))
            }
        }
    }
}
