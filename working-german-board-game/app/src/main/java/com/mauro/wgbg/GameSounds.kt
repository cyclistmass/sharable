package com.mauro.wgbg

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** Short game sounds (#44, #57). Our own synthesised effects (tools/make_sounds.py); volume follows Settings > Sounds. */
class GameSounds(context: Context) : AutoCloseable {
    private val pool = SoundPool.Builder().setMaxStreams(3).setAudioAttributes(
        AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build(),
    ).build()
    private val chime = pool.load(context, R.raw.trade_chime, 1)
    private val dice = pool.load(context, R.raw.dice_roll, 1)
    private val clonk = pool.load(context, R.raw.raiders_clonk, 1)

    /** A trade offer asks for cards you hold. */
    fun tradeChime(volume: Float) { if (volume > 0f) pool.play(chime, volume, volume, 1, 0, 1f) }

    /** Dice shaken and thrown onto the table (every roll; plays with Animations off too). */
    fun diceRoll(volume: Float) { if (volume > 0f) pool.play(dice, volume, volume, 1, 0, 1f) }

    /** The Raiders' ship lands face up on the event die: a light wooden clonk, a little after the dice settle. */
    fun raidersClonk(volume: Float) { if (volume > 0f) pool.play(clonk, volume * 0.8f, volume * 0.8f, 1, 0, 1f) }

    override fun close() = pool.release()
}

/** Haptics you feel through the phone (#57). */
object Haptics {
    /** The Raiders' ship came up: a light wooden clonk you feel. */
    fun raidersClonk(context: Context) = play(context, CLONK_TIMINGS, CLONK_AMPLITUDES)

    /**
     * The dice rattle, in step with the dice sound: quick soft clicks while they're shaken, then each die's bounces on
     * the table, getting softer and closer together.
     */
    val DICE_TIMINGS = longArrayOf(0, 12, 30, 10, 28, 12, 32, 10, 26, 12, 30, 10, 70, 28, 95, 22, 62, 16, 40, 12, 26, 9)
    val DICE_AMPLITUDES = intArrayOf(0, 70, 0, 60, 0, 75, 0, 60, 0, 70, 0, 55, 0, 255, 0, 170, 0, 110, 0, 70, 0, 45)

    /** The Raiders' ship on the event die: one solid knock and a faint bounce, like a wooden die landing (Mauro). */
    val CLONK_TIMINGS = longArrayOf(0, 38, 90, 12)   // Mauro: 10 ms longer
    val CLONK_AMPLITUDES = intArrayOf(0, 200, 0, 60)

    private fun vibrator(context: Context): Vibrator? =
        if (Build.VERSION.SDK_INT >= 31) (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        else @Suppress("DEPRECATION") (context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)

    /** Your own roll: the rattle (a plain buzz on phones without strength control). */
    fun diceRattle(context: Context) = play(context, DICE_TIMINGS, DICE_AMPLITUDES)

    private fun play(context: Context, timings: LongArray, amplitudes: IntArray) {
        val v = vibrator(context)?.takeIf { it.hasVibrator() } ?: return
        runCatching {
            v.vibrate(if (v.hasAmplitudeControl()) VibrationEffect.createWaveform(timings, amplitudes, -1)
                else VibrationEffect.createWaveform(timings, -1))
        }
    }
}
