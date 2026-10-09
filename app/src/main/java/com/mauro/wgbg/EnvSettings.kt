package com.mauro.wgbg

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/** Where settings are kept: SharedPreferences on device, a map in tests. */
interface FloatStore {
    fun get(key: String, default: Float): Float
    fun put(key: String, value: Float)
}

class PrefsStore(context: Context) : FloatStore {
    private val prefs = context.getSharedPreferences("wgbg-settings", Context.MODE_PRIVATE)
    override fun get(key: String, default: Float) = prefs.getFloat(key, default)
    override fun put(key: String, value: Float) = prefs.edit().putFloat(key, value).apply()
}

/**
 * Environmental Density (how many decorations: clouds, birds, boats, whitecaps, water effects)
 * and Environmental Motion (how strongly passive things move: waves, drift, bobbing, wind).
 * Both run 0..1, are independent of each other and of the game, and persist across restarts.
 */
class EnvSettings(private val store: FloatStore) {
    private var d by mutableFloatStateOf(store.get(DENSITY, DEFAULT_DENSITY).coerceIn(0f, 1f))
    private var m by mutableFloatStateOf(store.get(MOTION, DEFAULT_MOTION).coerceIn(0f, 1f))

    /** Clamped to 0..1 and saved on every change. */
    var density: Float
        get() = d
        set(v) { d = v.coerceIn(0f, 1f); store.put(DENSITY, d) }
    var motion: Float
        get() = m
        set(v) { m = v.coerceIn(0f, 1f); store.put(MOTION, m) }

    /** The preset the density matches, if any. */
    val preset: Preset? get() = Preset.entries.firstOrNull { kotlin.math.abs(it.density - density) < 0.005f }

    enum class Preset(val label: String, val density: Float) { MINIMAL("Minimal", 0.20f), BALANCED("Balanced", 0.45f), RICH("Rich", 0.85f) }

    companion object {
        const val DENSITY = "env-density"
        const val MOTION = "env-motion"
        const val DEFAULT_DENSITY = 0.45f
        const val DEFAULT_MOTION = 0.50f
    }
}

/** Sliders and presets for the menu panel. */
@Composable
fun EnvControls(env: EnvSettings) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Scenery", fontSize = 13.sp)
            for (p in EnvSettings.Preset.entries)
                Choice(p.label, selected = env.preset == p, tag = "preset-${p.name}", modifier = Modifier.height(34.dp), size = 12.sp) { env.density = p.density }
        }
        Labeled("Density ${(env.density * 100).roundToInt()}%") {
            Slider(env.density, { env.density = it }, Modifier.width(200.dp).testTag("env-density"), colors = brassSlider())
        }
        Labeled("Motion ${(env.motion * 100).roundToInt()}%") {
            Slider(env.motion, { env.motion = it }, Modifier.width(200.dp).testTag("env-motion"), colors = brassSlider())
        }
    }
}

@Composable
private fun Labeled(label: String, content: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 12.sp, modifier = Modifier.width(96.dp))
        content()
    }
}

/** Sliders in the motif: brass thumb and track over dark wood. */
@Composable
private fun brassSlider() = androidx.compose.material3.SliderDefaults.colors(
    thumbColor = androidx.compose.ui.graphics.Color(0xFFD9B865),
    activeTrackColor = androidx.compose.ui.graphics.Color(0xFFC9A04A),
    inactiveTrackColor = androidx.compose.ui.graphics.Color(0xFF3A2414),
)
