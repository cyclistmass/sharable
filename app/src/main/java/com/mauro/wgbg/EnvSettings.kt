package com.mauro.wgbg

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.clickable
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
 * Scenery amounts, each 0..1 and saved across restarts: how many clouds, birds and harbour
 * boats show, and how busy the waves are. Pure decoration; the game never reads them.
 */
class EnvSettings(private val store: FloatStore) {
    enum class Item(val label: String, val key: String) {
        CLOUDS("Clouds", "env-clouds"), BIRDS("Birds", "env-birds"), BOATS("Boats", "env-boats"), WAVES("Waves", "env-waves")
    }

    private val values = Item.entries.associateWith { mutableFloatStateOf(store.get(it.key, DEFAULT).coerceIn(0f, 1f)) }

    operator fun get(item: Item): Float = values.getValue(item).floatValue

    /** Clamped to 0..1 and saved on every change. */
    operator fun set(item: Item, v: Float) {
        val c = v.coerceIn(0f, 1f)
        values.getValue(item).floatValue = c
        store.put(item.key, c)
    }

    val clouds get() = this[Item.CLOUDS]
    val birds get() = this[Item.BIRDS]
    val boats get() = this[Item.BOATS]
    val waves get() = this[Item.WAVES]

    companion object {
        const val DEFAULT = 0.5f
    }
}

/** Scenery in the menu: a header with a ^ that opens and closes four sliders. */
@Composable
fun EnvControls(env: EnvSettings) {
    var open by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            Modifier.clickable { open = !open }.testTag("scenery-toggle").height(36.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Scenery", fontSize = 14.sp)
            // ^ points up when the sliders are open (tap to close), down when closed.
            Text(if (open) "^" else "v", fontSize = 18.sp, color = Color(0xFFD9B865), modifier = Modifier.width(24.dp))
        }
        if (open) for (item in EnvSettings.Item.entries) Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${item.label} ${(env[item] * 100).roundToInt()}%", fontSize = 12.sp, modifier = Modifier.width(96.dp))
            Slider(env[item], { env[item] = it }, Modifier.width(200.dp).testTag("env-${item.name.lowercase()}"), colors = brassSlider())
        }
    }
}

/** Sliders in the motif: brass thumb and track over dark wood. */
@Composable
private fun brassSlider() = androidx.compose.material3.SliderDefaults.colors(
    thumbColor = Color(0xFFD9B865),
    activeTrackColor = Color(0xFFC9A04A),
    inactiveTrackColor = Color(0xFF3A2414),
)
