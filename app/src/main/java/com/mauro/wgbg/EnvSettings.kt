package com.mauro.wgbg

import android.content.Context
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.draw.rotate
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.verticalScroll
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
class EnvSettings(private val store: FloatStore, private val viewBarsDefault: Boolean = false) {
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

    private val densityState = mutableFloatStateOf(store.get(DENSITY, DEFAULT).coerceIn(0f, 1f))

    /** One multiplier on all water objects (0 none, 0.5 as the four sliders say, 1 double). Saved. */
    var density: Float
        get() = densityState.floatValue
        set(v) { densityState.floatValue = v.coerceIn(0f, 1f); store.put(DENSITY, densityState.floatValue) }

    private val viewBarsState = mutableFloatStateOf(store.get(VIEW_BARS, if (viewBarsDefault) 1f else 0f))

    /** On-screen Zoom/Tilt bars: phones pinch and two-finger tilt instead; on by default on an emulator. Saved. */
    var viewBars: Boolean
        get() = viewBarsState.floatValue > 0.5f
        set(v) { viewBarsState.floatValue = if (v) 1f else 0f; store.put(VIEW_BARS, viewBarsState.floatValue) }

    private val tiles3dState = mutableFloatStateOf(store.get(TILES_3D, 0f))

    /** Board tiles: the painted faces (default) or the rendered 3D tiles (V3, a preview to try). Saved. */
    var tiles3d: Boolean
        get() = tiles3dState.floatValue > 0.5f
        set(v) { tiles3dState.floatValue = if (v) 1f else 0f; store.put(TILES_3D, tiles3dState.floatValue) }

    private val pirate = mutableFloatStateOf(store.get(THEME, 0f))

    /** Knight art: Normal (armoured knights) or Pirate themed (afro skeleton crew). Saved. */
    var pirateTheme: Boolean
        get() = pirate.floatValue > 0.5f
        set(v) { pirate.floatValue = if (v) 1f else 0f; store.put(THEME, pirate.floatValue) }

    enum class TileEdge(val label: String) { NONE("None"), THIN("Thin line"), CLASSIC("Classic frame") }
    private val edge = mutableFloatStateOf(store.get(EDGE, TileEdge.THIN.ordinal.toFloat()))

    /** How tile borders look: none, a thin dark line, or the original painted frame. Saved. */
    var tileEdge: TileEdge
        get() = TileEdge.entries.getOrElse(edge.floatValue.toInt()) { TileEdge.THIN }
        set(v) { edge.floatValue = v.ordinal.toFloat(); store.put(EDGE, edge.floatValue) }

    private val eventDie = mutableFloatStateOf(store.get(EVENT_DIE, 0f))

    /** Pirate event die art, an index into DiceArt.EVENT_STYLES (Mauro: all four as options). Saved. */
    var eventDieStyle: Int
        get() = eventDie.floatValue.toInt().coerceIn(0, DiceArt.EVENT_STYLES.size - 1)
        set(v) { eventDie.floatValue = v.coerceIn(0, DiceArt.EVENT_STYLES.size - 1).toFloat(); store.put(EVENT_DIE, eventDie.floatValue) }

    /** Back to the defaults (saved). */
    fun reset() { for (i in Item.entries) this[i] = DEFAULT; density = DEFAULT; pirateTheme = false; tileEdge = TileEdge.THIN; eventDieStyle = 0 }

    val clouds get() = this[Item.CLOUDS]
    val birds get() = this[Item.BIRDS]
    val boats get() = this[Item.BOATS]
    val waves get() = this[Item.WAVES]

    companion object {
        const val DEFAULT = 0.5f
        const val THEME = "theme-pirate"
        const val EDGE = "tile-edge"
        const val EVENT_DIE = "event-die-style"
        const val DENSITY = "env-density"
        const val VIEW_BARS = "view-bars"
        const val TILES_3D = "tiles-3d"

        /** True on an Android emulator (where there's no pinch), from the build's hardware and product names. */
        fun isEmulator(): Boolean {
            val b = android.os.Build.FINGERPRINT.orEmpty() + " " + android.os.Build.HARDWARE.orEmpty() + " " + android.os.Build.PRODUCT.orEmpty()
            return listOf("generic", "ranchu", "goldfish", "sdk_gphone", "emulator").any { it in b }
        }
    }
}

/**
 * The gear menu: every setting in one wood-and-brass panel. Changes save as they happen;
 * Reset puts scenery and sound back to their defaults.
 */
@Composable
fun SettingsPanel(env: EnvSettings, sound: SoundSettings, onClose: () -> Unit, modifier: Modifier = Modifier) {
    // Scrolls when the screen is too short for every setting (landscape phones).
    Column(modifier.width(360.dp).brassEdgedWood().verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(16.dp).testTag("settings-panel"),
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Settings", fontSize = 18.sp, color = Color(0xFFF1D58A), modifier = Modifier.weight(1f))
            ResetButton { env.reset(); sound.reset() }
            Spacer(Modifier.width(8.dp))
            WoodButton("Done", "settings-done", onClick = onClose)
        }
        Text("Scenery", fontSize = 13.sp, color = Color(0xFFD9B865))
        // The four kinds on the left; the upright Density bar beside them scales them all together.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                for (item in EnvSettings.Item.entries)
                    SliderRow(item.label, env[item], "env-${item.name.lowercase()}", sliderWidth = 170.dp) { env[item] = it }
            }
            BrassBar("Density", env.density, "env-density", height = 120.dp) { env.density = it }
        }
        Text("Theme", fontSize = 13.sp, color = Color(0xFFD9B865))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Choice("Normal", selected = !env.pirateTheme, tag = "theme-normal", modifier = Modifier.height(36.dp).width(110.dp), size = 13.sp) { env.pirateTheme = false }
            Choice("Pirate themed", selected = env.pirateTheme, tag = "theme-pirate", modifier = Modifier.height(36.dp).width(140.dp), size = 13.sp) { env.pirateTheme = true }
        }
        Text("Event die (Pirate theme)", fontSize = 13.sp, color = Color(0xFFD9B865))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (pair in DiceArt.EVENT_STYLES.indices.chunked(2)) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (i in pair) Choice(DiceArt.EVENT_STYLES[i], selected = env.eventDieStyle == i, tag = "event-die-$i",
                        modifier = Modifier.height(36.dp).width(122.dp), size = 12.sp) { env.eventDieStyle = i; env.pirateTheme = true }
                }
            }
            // A preview of the chosen style's ship face.
            androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(DiceArt.eventFace(true, 0, env.eventDieStyle)), null, Modifier.size(56.dp))
        }
        Text("Zoom and Tilt bars (for emulators and mice; phones pinch)", fontSize = 13.sp, color = Color(0xFFD9B865))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Choice("Show", selected = env.viewBars, tag = "view-bars-on", modifier = Modifier.height(36.dp).width(110.dp), size = 13.sp) { env.viewBars = true }
            Choice("Hide", selected = !env.viewBars, tag = "view-bars-off", modifier = Modifier.height(36.dp).width(110.dp), size = 13.sp) { env.viewBars = false }
        }
        Text("Tiles", fontSize = 13.sp, color = Color(0xFFD9B865))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Choice("Painted", selected = !env.tiles3d, tag = "tiles-painted", modifier = Modifier.height(36.dp).width(110.dp), size = 13.sp) { env.tiles3d = false }
            Choice("3D (preview)", selected = env.tiles3d, tag = "tiles-3d", modifier = Modifier.height(36.dp).width(130.dp), size = 13.sp) { env.tiles3d = true }
        }
        Text("Intro", fontSize = 13.sp, color = Color(0xFFD9B865))
        val ctx = androidx.compose.ui.platform.LocalContext.current
        var replay by remember { mutableStateOf(false) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Choice("Play the intro again", selected = replay, tag = "intro-replay", modifier = Modifier.height(36.dp).width(170.dp), size = 13.sp) {
                ctx.getSharedPreferences("intro", android.content.Context.MODE_PRIVATE).edit().putBoolean("seen", false).apply(); replay = true
            }
            if (replay) Text("Plays next time you open the app", fontSize = 12.sp, color = Color(0xFFEDE6D8))
        }
        Text("Tile edges", fontSize = 13.sp, color = Color(0xFFD9B865))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (e in EnvSettings.TileEdge.entries)
                Choice(e.label, selected = env.tileEdge == e, tag = "edge-${e.name.lowercase()}", modifier = Modifier.height(36.dp).width(110.dp), size = 13.sp) { env.tileEdge = e }
        }
        Text("Sound", fontSize = 13.sp, color = Color(0xFFD9B865))
        SliderRow("Music", sound.musicVolume, "vol-music") { sound.musicVolume = it }
        SliderRow("Sounds", sound.soundVolume, "vol-sounds") { sound.soundVolume = it }
        Text("Songs", fontSize = 13.sp, color = Color(0xFFD9B865))
        if (SoundSettings.SONGS.isEmpty()) Text("No songs yet: the sea-shanty set is on the way (#17)", fontSize = 12.sp, color = Color(0xFFCDBFA6))
    }
}

@Composable
private fun SliderRow(label: String, value: Float, tag: String, sliderWidth: androidx.compose.ui.unit.Dp = 220.dp, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("$label ${(value * 100).roundToInt()}%", fontSize = 12.sp, color = Color(0xFFEDE6D8), modifier = Modifier.width(100.dp))
        Slider(value, onChange, Modifier.width(sliderWidth).height(32.dp).testTag(tag), colors = brassSlider())
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
        modifier.rotate(turn).clickable(interactionSource = source, indication = null, onClick = onClick).testTag("gear"),
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
