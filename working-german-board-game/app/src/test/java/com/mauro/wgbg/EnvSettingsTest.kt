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
}
