package com.mauro.wgbg

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer

/** One tune in the set list (#17, #58): its title and its recording in res/raw (tools/make_sounds.py). */
data class Song(val title: String, val res: Int)

/**
 * Plays the set list round and round at the Music volume (#58). Volume 0 stops it; [pause] and [resume] follow the app
 * going to the background and back. Any player error just stops the music: it never takes the game down.
 */
class MusicPlayer(private val context: Context, private val songs: List<Song>) : AutoCloseable {
    private var player: MediaPlayer? = null
    private var index = 0
    private var volume = 0f
    private var paused = false
    /** The song playing now, or null. */
    var playing: Song? = null; private set

    fun setVolume(v: Float) {
        volume = v.coerceIn(0f, 1f)
        if (volume <= 0f) stop() else { player?.setVolume(volume, volume); if (player == null && !paused) start() }
    }

    fun pause() { paused = true; runCatching { player?.pause() }; log("pause") }
    fun resume() {
        paused = false
        if (volume > 0f) { if (player == null) start() else runCatching { player?.start() }.onFailure { log("resume failed: $it") } }
        log("resume volume=$volume player=${player != null}")
    }
    private fun log(m: String) { runCatching { android.util.Log.i("WGBG-MUSIC", m) } }

    private fun start() {
        if (songs.isEmpty()) return
        val song = songs[index % songs.size]
        runCatching {
            val p = MediaPlayer.create(context, song.res, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build(), 0) ?: return
            p.setVolume(volume, volume)
            p.setOnCompletionListener { it.release(); player = null; index++; if (!paused && volume > 0f) start() }
            p.setOnErrorListener { mp, _, _ -> mp.release(); player = null; playing = null; true }
            p.start(); player = p; playing = song
        }
    }

    private fun stop() { runCatching { player?.release() }; player = null; playing = null }

    override fun close() = stop()
}
