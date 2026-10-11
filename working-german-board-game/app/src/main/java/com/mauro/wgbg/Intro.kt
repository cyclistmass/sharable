package com.mauro.wgbg

import android.content.Context
import android.net.Uri
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * The intro fly-over (#54, Codex render, Mauro's OK 2026-10-10): a gull rides beside a sloop, rises over the
 * island and ends on the board. Plays the first time the app opens; a tap skips it; Settings can replay it.
 */
object Intro {
    private const val PREFS = "intro"
    private const val SEEN = "seen"

    fun seen(context: Context): Boolean = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(SEEN, false)
    fun markSeen(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(SEEN, true).apply()
}

/** Full-screen intro video; [onDone] when it ends, is tapped, or can't play. */
@Composable
fun IntroScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val finish = remember { var done = false; { if (!done) { done = true; Intro.markSeen(context); onDone() } } }
    Box(
        Modifier.fillMaxSize().background(Color.Black).testTag("intro")
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { finish() },
        contentAlignment = Alignment.Center,
    ) {
        AndroidView(
            factory = { ctx ->
                VideoView(ctx).apply {
                    setVideoURI(Uri.parse("android.resource://${ctx.packageName}/${R.raw.intro_flyover}"))
                    setOnCompletionListener { finish() }
                    setOnErrorListener { _, _, _ -> finish(); true }
                    // Taps on the video itself also skip.
                    setOnTouchListener { v, e -> if (e.action == android.view.MotionEvent.ACTION_UP) { v.performClick(); finish() }; true }
                    start()
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        Text("Tap to skip", color = Color(0xCCF1D58A), fontSize = 13.sp,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp).testTag("intro-skip"))
    }
}
