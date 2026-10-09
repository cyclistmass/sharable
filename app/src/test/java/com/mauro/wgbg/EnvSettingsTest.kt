package com.mauro.wgbg

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import com.mauro.wgbg.EnvSettings.Item

class EnvSettingsTest {
    private class MapStore : FloatStore {
        val map = HashMap<String, Float>()
        override fun get(key: String, default: Float) = map[key] ?: default
        override fun put(key: String, value: Float) { map[key] = value }
    }

    @Test
    fun `every scenery slider starts at half`() {
        val env = EnvSettings(MapStore())
        for (i in Item.entries) assertEquals(0.5f, env[i])
    }

    @Test
    fun `sliders persist and are independent`() {
        val store = MapStore()
        EnvSettings(store).apply { this[Item.CLOUDS] = 0.9f; this[Item.BOATS] = 0f }
        val again = EnvSettings(store) // as after an app restart
        assertEquals(0.9f, again.clouds)
        assertEquals(0f, again.boats)
        assertEquals(0.5f, again.birds, "untouched sliders keep their default")
        again[Item.WAVES] = 1f
        assertEquals(0.9f, again.clouds, "one slider never moves another")
    }

    @Test
    fun `out of range values are clamped, including bad stored ones`() {
        val store = MapStore().apply { map["env-clouds"] = 7f; map["env-birds"] = -2f }
        val env = EnvSettings(store)
        assertEquals(1f, env.clouds)
        assertEquals(0f, env.birds)
    }

    @Test
    fun `volumes persist, are clamped and independent`() {
        val store = MapStore()
        SoundSettings(store).apply { musicVolume = 0.2f; soundVolume = 7f }
        val again = SoundSettings(store)
        assertEquals(0.2f, again.musicVolume)
        assertEquals(1f, again.soundVolume)
    }

    @Test
    fun `reset puts every setting back to its default`() {
        val store = MapStore()
        val env = EnvSettings(store).apply { this[Item.CLOUDS] = 1f; this[Item.WAVES] = 0f }
        val sound = SoundSettings(store).apply { musicVolume = 0f; soundVolume = 0.1f }
        env.reset(); sound.reset()
        for (i in Item.entries) assertEquals(EnvSettings.DEFAULT, env[i])
        assertEquals(SoundSettings.DEFAULT_MUSIC, sound.musicVolume)
        assertEquals(SoundSettings.DEFAULT_SOUNDS, sound.soundVolume)
        assertEquals(EnvSettings.DEFAULT, EnvSettings(store).clouds, "and the reset is saved")
    }
}
