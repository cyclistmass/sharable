package com.mauro.wgbg

import android.content.Context
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.draw.rotate
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.Image
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

    /** Back to the defaults (saved). */
    fun reset() { for (i in Item.entries) this[i] = DEFAULT }

    val clouds get() = this[Item.CLOUDS]
    val birds get() = this[Item.BIRDS]
    val boats get() = this[Item.BOATS]
    val waves get() = this[Item.WAVES]

    companion object {
        const val DEFAULT = 0.5f
    }
}

/**
 * The gear menu: every setting in one wood-and-brass panel. Changes save as they happen;
 * Reset puts scenery and sound back to their defaults.
 */
@Composable
fun SettingsPanel(env: EnvSettings, sound: SoundSettings, onClose: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.width(360.dp).brassEdgedWood().padding(16.dp).testTag("settings-panel"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Settings", fontSize = 18.sp, color = Color(0xFFF1D58A), modifier = Modifier.weight(1f))
            ResetButton { env.reset(); sound.reset() }
            Spacer(Modifier.width(8.dp))
            WoodButton("Done", "settings-done", onClick = onClose)
        }
        Text("Scenery", fontSize = 13.sp, color = Color(0xFFD9B865))
        for (item in EnvSettings.Item.entries) SliderRow(item.label, env[item], "env-${item.name.lowercase()}") { env[item] = it }
        Text("Sound", fontSize = 13.sp, color = Color(0xFFD9B865))
        SliderRow("Music", sound.musicVolume, "vol-music") { sound.musicVolume = it }
        SliderRow("Sounds", sound.soundVolume, "vol-sounds") { sound.soundVolume = it }
        Text("Songs", fontSize = 13.sp, color = Color(0xFFD9B865))
        if (SoundSettings.SONGS.isEmpty()) Text("No songs yet: the sea-shanty set is on the way (#17)", fontSize = 12.sp, color = Color(0xFFCDBFA6))
    }
}

@Composable
private fun SliderRow(label: String, value: Float, tag: String, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("$label ${(value * 100).roundToInt()}%", fontSize = 12.sp, color = Color(0xFFEDE6D8), modifier = Modifier.width(100.dp))
        Slider(value, onChange, Modifier.width(220.dp).height(32.dp).testTag(tag), colors = brassSlider())
    }
}

/** The brass gear button (Mauro's art): darker while pressed, and it turns a little each tap. */
@Composable
fun GearButton(open: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val turn by animateFloatAsState(if (open) 60f else 0f, label = "gear-turn")
    Image(
        painterResource(if (pressed) R.drawable.settings_gear_pressed else R.drawable.settings_gear), "Settings",
        modifier.size(52.dp).rotate(turn).clickable(interactionSource = source, indication = null, onClick = onClick).testTag("gear"),
    )
}

/** Sliders in the motif: brass thumb and track over dark wood. */
@Composable
private fun brassSlider() = androidx.compose.material3.SliderDefaults.colors(
    thumbColor = Color(0xFFD9B865),
    activeTrackColor = Color(0xFFC9A04A),
    inactiveTrackColor = Color(0xFF3A2414),
)

/** Music and game-sound volumes (0..1), saved across restarts; the soundtrack (#17) will play through these. */
class SoundSettings(private val store: FloatStore) {
    private val music = mutableFloatStateOf(store.get(MUSIC, DEFAULT_MUSIC).coerceIn(0f, 1f))
    private val sounds = mutableFloatStateOf(store.get(SOUNDS, DEFAULT_SOUNDS).coerceIn(0f, 1f))
    var musicVolume: Float
        get() = music.floatValue
        set(v) { music.floatValue = v.coerceIn(0f, 1f); store.put(MUSIC, music.floatValue) }
    var soundVolume: Float
        get() = sounds.floatValue
        set(v) { sounds.floatValue = v.coerceIn(0f, 1f); store.put(SOUNDS, sounds.floatValue) }

    /** Back to the defaults (saved). */
    fun reset() { musicVolume = DEFAULT_MUSIC; soundVolume = DEFAULT_SOUNDS }

    companion object {
        const val DEFAULT_MUSIC = 0.6f
        const val DEFAULT_SOUNDS = 0.8f
        const val MUSIC = "vol-music"
        const val SOUNDS = "vol-sounds"
        /** The planned set list (#17); empty until the recordings exist. */
        val SONGS: List<String> = emptyList()
    }
}

/** Sound in the menu: a header with a ^ that opens Music and Game sounds sliders and the song list. */
@Composable
fun SoundControls(sound: SoundSettings) {
    var open by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            Modifier.clickable { open = !open }.testTag("sound-toggle").height(36.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Sound", fontSize = 14.sp)
            Text(if (open) "^" else "v", fontSize = 18.sp, color = Color(0xFFD9B865), modifier = Modifier.width(24.dp))
        }
        if (open) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Music ${(sound.musicVolume * 100).roundToInt()}%", fontSize = 12.sp, modifier = Modifier.width(96.dp))
                Slider(sound.musicVolume, { sound.musicVolume = it }, Modifier.width(200.dp).testTag("vol-music"), colors = brassSlider())
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Sounds ${(sound.soundVolume * 100).roundToInt()}%", fontSize = 12.sp, modifier = Modifier.width(96.dp))
                Slider(sound.soundVolume, { sound.soundVolume = it }, Modifier.width(200.dp).testTag("vol-sounds"), colors = brassSlider())
            }
            Text("Songs", fontSize = 12.sp)
            if (SoundSettings.SONGS.isEmpty()) Text("No songs yet: the sea-shanty set is on the way (#17)", fontSize = 12.sp, color = Color(0xFFCDBFA6))
        }
    }
}

/** Reset to defaults: the brass circular-arrow icon (Mauro's art), darker while pressed. */
@Composable
private fun ResetButton(onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Image(
        painterResource(if (pressed) R.drawable.reset_arrow_pressed else R.drawable.reset_arrow), "Reset to defaults",
        Modifier.size(44.dp).clickable(interactionSource = source, indication = null, onClick = onClick).testTag("settings-reset"),
    )
}
