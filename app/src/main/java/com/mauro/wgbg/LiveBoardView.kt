package com.mauro.wgbg

import android.content.Context
import android.util.Log
import android.view.Choreographer
import android.view.Surface
import android.view.TextureView
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.filament.*
import com.google.android.filament.android.DisplayHelper
import com.google.android.filament.android.UiHelper
import com.google.android.filament.gltfio.*
import com.google.android.filament.utils.Utils
import java.nio.ByteBuffer
import kotlin.math.*

@Composable
internal fun LiveBoardView(placements: List<LivePlacement>, projection: LiveProjection?, modifier: Modifier,
    onFailure: (String) -> Unit) {
    val holder = remember { arrayOfNulls<LiveBoardRenderer>(1) }
    val latestFailure by rememberUpdatedState(onFailure)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    AndroidView(modifier = modifier, factory = { context ->
        TextureView(context).also { texture ->
            try { holder[0] = LiveBoardRenderer(texture, context) { latestFailure(it) } }
            catch (e: Exception) { Log.e("WGBG-3D", "Initialisation failed", e); latestFailure(e.message ?: "Initialisation failed") }
        }
    }, update = {
        holder[0]?.let { renderer ->
            renderer.placements = placements
            renderer.projection = projection
            renderer.running = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        }
    })
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ ->
            holder[0]?.running = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); holder[0]?.close(); holder[0] = null }
    }
}

/** All engine access is on the main thread, as required by Filament's Android API. */
internal class LiveBoardRenderer(texture: TextureView, private val context: Context, private val fail: (String) -> Unit) :
    Choreographer.FrameCallback, AutoCloseable {
    companion object {
        init { Utils.init(); Gltfio.init() }
    }
    private val engine = Engine.create()
    private val renderer = engine.createRenderer()
    private val scene = engine.createScene()
    private val cameraEntity = EntityManager.get().create()
    private val camera = engine.createCamera(cameraEntity)
    private val view = engine.createView()
    private val provider = UbershaderProvider(engine)
    private val loader = AssetLoader(engine, provider, EntityManager.get())
    private val resources = ResourceLoader(engine, true)
    private val display = DisplayHelper(context)
    private val ui = UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK)
    private var swapChain: SwapChain? = null
    private var width = 0
    private var height = 0
    private var closed = false
    private var scheduled = false
    var placements = emptyList<LivePlacement>()
    var projection: LiveProjection? = null
    private var shown = emptyList<LivePlacement>()
    private var active = mutableListOf<FilamentInstance>()
    private data class Model(val asset: FilamentAsset, val first: FilamentInstance)
    private val models = mutableMapOf<String, Model>()
    private val pools = mutableMapOf<Pair<String, Int>, MutableList<FilamentInstance>>()
    private val tints = mutableMapOf<Pair<String, Int>, MaterialInstance>()
    private val lights = mutableListOf<Int>()
    private val fill = IndirectLight.Builder().irradiance(1, floatArrayOf(0.7f, 0.76f, 0.85f)).intensity(25_000f).build(engine)
    private val sky = Skybox.Builder().color(0.055f, 0.12f, 0.17f, 1f).build(engine)
    private val intervals = mutableListOf<Double>()
    private val submit = mutableListOf<Double>()
    private var lastRendered = 0L
    private var windowStart = 0L
    private var skipped = 0
    private var timedProjection: LiveProjection? = null
    var running = false
        set(value) {
            field = value
            if (value && !closed && !scheduled) { scheduled = true; Choreographer.getInstance().postFrameCallback(this) }
            if (!value) { Choreographer.getInstance().removeFrameCallback(this); scheduled = false; resetTiming() }
        }

    init {
        view.scene = scene; view.camera = camera
        view.isPostProcessingEnabled = true
        view.antiAliasing = View.AntiAliasing.FXAA
        view.dynamicResolutionOptions = View.DynamicResolutionOptions().apply { enabled = false }
        camera.setExposure(16f, 1f / 125f, 100f)
        scene.indirectLight = fill; scene.skybox = sky
        addLight(-0.5f, -0.8f, -0.6f, 80_000f, 1f, 0.91f, 0.78f)
        addLight(0.5f, -0.4f, 0.4f, 12_000f, 0.75f, 0.85f, 1f)
        ui.renderCallback = object : UiHelper.RendererCallback {
            override fun onNativeWindowChanged(surface: Surface) {
                swapChain?.let { engine.destroySwapChain(it) }
                swapChain = engine.createSwapChain(surface)
                display.attach(renderer, texture.display)
            }
            override fun onDetachedFromSurface() {
                display.detach()
                swapChain?.let { engine.destroySwapChain(it); engine.flushAndWait() }
                swapChain = null
            }
            override fun onResized(w: Int, h: Int) {
                width = w; height = h; view.viewport = Viewport(0, 0, w, h)
                engine.flushAndWait()
            }
        }
        ui.attachTo(texture)
    }

    private fun addLight(x: Float, y: Float, z: Float, intensity: Float, r: Float, g: Float, b: Float) {
        val entity = EntityManager.get().create()
        LightManager.Builder(LightManager.Type.DIRECTIONAL).direction(x, y, z).color(r, g, b)
            .intensity(intensity).castShadows(false).build(engine, entity)
        lights.add(entity); scene.addEntity(entity)
    }

    private fun model(name: String): Model = models.getOrPut(name) {
        val bytes = if (name.startsWith("digit_")) liveNumberGlb(context, name.removePrefix("digit_").toInt()) else {
            val data = context.assets.open("filament/$name.glb").use { it.readBytes() }
            ByteBuffer.allocateDirect(data.size).apply { put(data); flip() }
        }
        val instances = arrayOfNulls<FilamentInstance>(1)
        val asset = requireNotNull(loader.createInstancedAsset(bytes, instances)) { "Cannot load $name" }
        resources.loadResources(asset)
        // Keep source data for later createInstance calls; destruction frees it once the view closes.
        Model(asset, requireNotNull(instances[0]))
    }

    private fun instance(p: LivePlacement, index: Int): FilamentInstance {
        val key = p.model to p.owner
        val pool = pools.getOrPut(key) { mutableListOf() }
        if (index < pool.size) return pool[index]
        val m = model(p.model)
        val alreadyUsed = pools.any { (k, v) -> k.first == p.model && v.isNotEmpty() }
        val result = if (!alreadyUsed) m.first else requireNotNull(loader.createInstance(m.asset))
        val rm = engine.renderableManager
        for (entity in result.entities) {
            val renderable = rm.getInstance(entity)
            if (renderable == 0) continue
            rm.setCastShadows(renderable, false); rm.setReceiveShadows(renderable, false)
            for (primitive in 0 until rm.getPrimitiveCount(renderable)) {
                val material = rm.getMaterialInstanceAt(renderable, primitive)
                if (p.owner >= 0 && woodTint(material.name)) {
                    val tint = tints.getOrPut(key) {
                        MaterialInstance.duplicate(material, "${p.model}-player-${p.owner}").apply {
                            val color = PLAYER_COLORS[p.owner]
                            setParameter("baseColorFactor", Colors.RgbaType.SRGB, color.red, color.green, color.blue, 1f)
                        }
                    }
                    rm.setMaterialInstanceAt(renderable, primitive, tint)
                }
            }
        }
        pool.add(result)
        return result
    }

    private fun updateScene() {
        if (placements == shown) return
        for (i in active) scene.removeEntities(i.entities)
        active.clear()
        val counts = mutableMapOf<Pair<String, Int>, Int>()
        for (p in placements) {
            val key = p.model to p.owner
            val index = counts.getOrDefault(key, 0); counts[key] = index + 1
            val i = instance(p, index)
            val c = cos(p.heading) * p.scale; val s = sin(p.heading) * p.scale
            val matrix = floatArrayOf(c, 0f, -s, 0f, 0f, p.scale, 0f, 0f, s, 0f, c, 0f,
                p.position.x, p.height, p.position.y, 1f)
            engine.transformManager.setTransform(engine.transformManager.getInstance(i.root), matrix)
            scene.addEntities(i.entities); active.add(i)
        }
        shown = placements
        resetTiming()
        Log.i("WGBG-3D", "scene models=${models.size} placements=${shown.size} renderables=${scene.renderableCount}")
    }

    private fun updateCamera(p: LiveProjection) {
        if (timedProjection != p) { resetTiming(); timedProjection = p }
        val st = sin(p.tilt).toDouble(); val ct = cos(p.tilt).toDouble()
        val sr = sin(p.rotation).toDouble(); val cr = cos(p.rotation).toDouble()
        camera.lookAt(20.0 * st * sr, 20.0 * ct, 20.0 * st * cr, 0.0, 0.0, 0.0, -ct * sr, st, -ct * cr)
        camera.setProjection(com.google.android.filament.Camera.Projection.ORTHO,
            -p.center.x.toDouble() / p.scale, (width - p.center.x).toDouble() / p.scale,
            -(height - p.center.y).toDouble() / p.scale, p.center.y.toDouble() / p.scale, 0.1, 100.0)
        // Keep digits upright under twist, while retaining their horizontal plane and real depth.
        for ((index, placement) in shown.withIndex()) if (placement.model.startsWith("digit_")) {
            val c = cos(p.rotation); val s = sin(p.rotation)
            engine.transformManager.setTransform(engine.transformManager.getInstance(active[index].root),
                floatArrayOf(c, 0f, s, 0f, 0f, 1f, 0f, 0f, -s, 0f, c, 0f,
                    placement.position.x, placement.height, placement.position.y, 1f))
        }
    }

    override fun doFrame(frameTimeNanos: Long) {
        scheduled = false
        if (!running || closed) return
        scheduled = true; Choreographer.getInstance().postFrameCallback(this)
        val p = projection ?: return
        if (!ui.isReadyToRender || width == 0 || height == 0) return
        try {
            updateScene(); updateCamera(p)
            val start = System.nanoTime()
            if (renderer.beginFrame(requireNotNull(swapChain), frameTimeNanos)) {
                renderer.render(view); renderer.endFrame()
                val now = System.nanoTime()
                if (lastRendered != 0L) intervals.add((now - lastRendered) / 1e6)
                lastRendered = now
                submit.add((now - start) / 1e6)
                if (windowStart == 0L) windowStart = now
                if (now - windowStart >= 5_000_000_000L) reportTiming(now, p)
            } else skipped++
        } catch (e: Exception) {
            running = false
            Log.e("WGBG-3D", "Rendering failed; reverting to sprites", e)
            fail(e.message ?: "Rendering failed")
        }
    }

    private fun reportTiming(now: Long, p: LiveProjection) {
        fun percentile(values: List<Double>, q: Double): Double = values.sorted()[(q * (values.size - 1)).roundToInt()]
        if (intervals.isNotEmpty()) Log.i("WGBG-3D-PERF", "frames=${intervals.size} elapsedMs=${(now - windowStart) / 1e6} " +
            "fps=${intervals.size * 1e9 / (now - windowStart)} p50Ms=${percentile(intervals, .50)} p95Ms=${percentile(intervals, .95)} " +
            "submitP95Ms=${percentile(submit, .95)} skipped=$skipped tilt=${p.tilt} rotation=${p.rotation} " +
            "lod=${shown.firstOrNull { it.model.startsWith("tile_") }?.model?.takeLast(1)} pirate=${shown.any { it.model.startsWith("pirate_") }} " +
            "placements=${shown.size} renderables=${scene.renderableCount}")
        if (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0 && intervals.isNotEmpty()) {
            val sample = org.json.JSONObject().apply {
                put("elapsedMs", (now - windowStart) / 1e6); put("intervalMs", org.json.JSONArray(intervals))
                put("submitMs", org.json.JSONArray(submit)); put("skipped", skipped)
                put("tilt", p.tilt); put("rotation", p.rotation)
                put("lod", shown.first { it.model.startsWith("tile_") }.model.takeLast(1).toInt())
                put("pirate", shown.any { it.model.startsWith("pirate_") })
                put("placements", shown.size); put("renderables", scene.renderableCount)
            }
            context.cacheDir.resolve("live-board-frames.jsonl").appendText(sample.toString() + "\n")
        }
        resetTiming()
    }

    private fun resetTiming() { intervals.clear(); submit.clear(); windowStart = 0; lastRendered = 0; skipped = 0 }

    override fun close() {
        if (closed) return
        running = false
        ui.detach()
        closed = true
        scene.removeEntities(lights.toIntArray())
        for (i in active) scene.removeEntities(i.entities)
        for (m in models.values) loader.destroyAsset(m.asset)
        for (m in tints.values) engine.destroyMaterialInstance(m)
        resources.destroy(); loader.destroy(); provider.destroyMaterials(); provider.destroy()
        for (l in lights) { engine.destroyEntity(l); EntityManager.get().destroy(l) }
        engine.destroyIndirectLight(fill); engine.destroySkybox(sky)
        engine.destroyView(view); engine.destroyScene(scene); engine.destroyRenderer(renderer)
        engine.destroyCameraComponent(cameraEntity); EntityManager.get().destroy(cameraEntity)
        engine.destroy()
    }
}
