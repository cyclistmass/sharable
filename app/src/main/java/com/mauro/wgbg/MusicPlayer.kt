package com.mauro.wgbg

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer

/** One tune in the set list (#17, #58): its title and its recording in res/raw (tools/make_sounds.py). */
data class Song(val title: String, val res: Int)

/**
 * Plays the set list round and round at the Music volume (#58). Volume 0 stops it; [pause] and [resume] follow the app
 * going to the background and back; [hold] is the player's own Pause button, which stays paused across both. [play]
 * jumps to a song, [next] skips. Any player error just stops the music: it never takes the game down.
 */
class MusicPlayer(private val context: Context, private val songs: List<Song>) : AutoCloseable {
    private var player: MediaPlayer? = null
    private var index = 0
    private var volume = 0f
    private var paused = false
    private var held = false
    /** Told the index of each song as it starts. */
    var onSong: (Int) -> Unit = {}
    /** The song playing now, or null. */
    var playing: Song? = null; private set

    fun setVolume(v: Float) {
        volume = v.coerceIn(0f, 1f)
        if (volume <= 0f) stop() else { player?.setVolume(musicLevel(volume), musicLevel(volume)); if (player == null && !paused && !held) start() }
    }

    /** The Pause button: [on] pauses where it is, off carries on from there. */
    fun hold(on: Boolean) {
        held = on
        if (on) runCatching { player?.pause() } else if (!paused && volume > 0f) { if (player == null) start() else runCatching { player?.start() } }
    }

    /** Plays song [i] of the set list from the start (and lets go of Pause). */
    fun play(i: Int) {
        if (songs.isEmpty()) return
        stop(); index = ((i % songs.size) + songs.size) % songs.size; held = false
        if (!paused && volume > 0f) start()
    }

    /** Skips to the next song. */
    fun next() = play(index + 1)

    fun pause() { paused = true; runCatching { player?.pause() }; log("pause") }
    fun resume() {
        paused = false
        if (held) return
        if (volume > 0f) { if (player == null) start() else runCatching { player?.start() }.onFailure { log("resume failed: $it") } }
        log("resume volume=$volume player=${player != null}")
    }
    private fun log(m: String) { runCatching { android.util.Log.i("WGBG-MUSIC", m) } }

    private fun start() {
        if (songs.isEmpty()) return
        index %= songs.size
        val song = songs[index]
        runCatching {
            val p = MediaPlayer.create(context, song.res, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build(), 0) ?: return
            p.setVolume(musicLevel(volume), musicLevel(volume))
            p.setOnCompletionListener { it.release(); player = null; index++; if (!paused && !held && volume > 0f) start() }
            p.setOnErrorListener { mp, _, _ -> mp.release(); player = null; playing = null; true }
            p.start(); player = p; playing = song; onSong(index)
        }
    }

    private fun stop() { runCatching { player?.release() }; player = null; playing = null }

    override fun close() = stop()
}

/**
 * What the player hears for a Music slider value: half of it, −6 dB (Mauro, 2026-10-13: "about half current scale,
 * it was loud"). The tune files stay full-scale so this is the one place music level is set.
 */
internal const val MUSIC_GAIN = 0.5f
internal fun musicLevel(slider: Float) = slider.coerceIn(0f, 1f) * MUSIC_GAIN
