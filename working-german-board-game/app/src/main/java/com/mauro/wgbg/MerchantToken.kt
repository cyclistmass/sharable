package com.mauro.wgbg

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke

/**
 * Where the Age of Chivalry Merchant sits on its tile, in hex sides from the centre (y down, before the tilt squash):
 * lower LEFT, mirroring the number plate's lower right, so it never covers the number (#112).
 */
internal val MERCHANT_SPOT = Offset(-0.36f, 0.30f)
internal const val MERCHANT_RADIUS = 0.2f

/**
 * The Merchant (#112: "play merchant, no see token"): a worn-brass coin lying on the tile, ringed in the holder's
 * colour, with a coin purse on it. [unit] is one hex side in px; [squash] flattens it with the board's tilt.
 */
internal fun DrawScope.drawMerchantToken(center: Offset, unit: Float, squash: Float, owner: Color) {
    val r = MERCHANT_RADIUS * unit
    fun oval(c: Offset, rx: Float, ry: Float) = c - Offset(rx, ry) to Size(rx * 2, ry * 2)
    // Shadow, the holder's ring, then the coin.
    oval(center + Offset(0f, r * 0.18f * squash), r * 1.08f, r * 1.08f * squash).let { (o, s) -> drawOval(Color.Black.copy(alpha = 0.35f), o, s) }
    oval(center, r, r * squash).let { (o, s) -> drawOval(owner, o, s) }
    oval(center, r * 0.8f, r * 0.8f * squash).let { (o, s) ->
        drawOval(Brush.radialGradient(listOf(Color(0xFFD9B66A), Color(0xFF9C7A3C), Color(0xFF6E5427)), center - Offset(r * 0.25f, r * 0.25f * squash), r * 1.1f), o, s)
        drawOval(Color(0xFF4A3818), o, s, style = Stroke(r * 0.07f))
    }
    // The purse: a tied sack, dark engraved into the brass.
    val k = r * 0.42f
    val sack = Path().apply {
        moveTo(center.x - k * 0.35f, center.y - k * 0.55f * squash)
        lineTo(center.x + k * 0.35f, center.y - k * 0.55f * squash)
        quadraticTo(center.x + k * 1.05f, center.y + k * 0.2f * squash, center.x + k * 0.7f, center.y + k * 0.75f * squash)
        lineTo(center.x - k * 0.7f, center.y + k * 0.75f * squash)
        quadraticTo(center.x - k * 1.05f, center.y + k * 0.2f * squash, center.x - k * 0.35f, center.y - k * 0.55f * squash)
        close()
    }
    drawPath(sack, Color(0xFF3B2A12))
    oval(center - Offset(0f, k * 0.72f * squash), k * 0.42f, k * 0.2f * squash).let { (o, s) -> drawOval(Color(0xFF3B2A12), o, s) }
}
