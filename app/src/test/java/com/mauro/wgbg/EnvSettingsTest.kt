package com.mauro.wgbg

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class EnvSettingsTest {
    private class MapStore : FloatStore {
        val map = HashMap<String, Float>()
        override fun get(key: String, default: Float) = map[key] ?: default
        override fun put(key: String, value: Float) { map[key] = value }
    }

    @Test
    fun `defaults are Balanced density and half motion`() {
        val env = EnvSettings(MapStore())
        assertEquals(0.45f, env.density)
        assertEquals(0.5f, env.motion)
        assertEquals(EnvSettings.Preset.BALANCED, env.preset)
    }

    @Test
    fun `settings persist and are independent`() {
        val store = MapStore()
        EnvSettings(store).apply { density = 0.85f; motion = 0f }
        val again = EnvSettings(store) // as after an app restart
        assertEquals(0.85f, again.density)
        assertEquals(0f, again.motion)
        assertEquals(EnvSettings.Preset.RICH, again.preset)
        again.motion = 1f
        assertEquals(0.85f, again.density, "motion never changes density")
    }

    @Test
    fun `out of range values are clamped, including bad stored ones`() {
        val store = MapStore().apply { map[EnvSettings.DENSITY] = 7f; map[EnvSettings.MOTION] = -2f }
        val env = EnvSettings(store)
        assertEquals(1f, env.density)
        assertEquals(0f, env.motion)
        env.density = 0.33f
        assertNull(env.preset)
    }
}
