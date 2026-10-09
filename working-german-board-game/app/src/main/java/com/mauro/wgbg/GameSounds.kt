package com.mauro.wgbg

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool

/** Short game sounds (#44). Our own synthesised effects; volume follows Settings > Sounds. */
class GameSounds(context: Context) : AutoCloseable {
    private val pool = SoundPool.Builder().setMaxStreams(2).setAudioAttributes(
        AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build(),
    ).build()
    private val chime = pool.load(context, R.raw.trade_chime, 1)

    /** A trade offer asks for cards you hold. */
    fun tradeChime(volume: Float) { if (volume > 0f) pool.play(chime, volume, volume, 1, 0, 1f) }

    override fun close() = pool.release()
}
