package com.mauro.wgbg

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import wgbg.engine.Board
import wgbg.engine.BoardGen
import wgbg.engine.Port
import wgbg.engine.Resource
import wgbg.engine.Rng
import kotlin.math.min
import kotlin.math.sqrt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                Scaffold { padding ->
                    var seed by remember { mutableLongStateOf(1L) }
                    val board = remember(seed) { BoardGen.random(Rng(seed), avoidAdjacentRed = true) }
                    BoardView(
                        board,
                        "Board #$seed · tap for a new one",
                        Modifier.fillMaxSize().padding(padding).clickable { seed++ },
                    )
                }
            }
        }
    }
}

private val TERRAIN_COLORS = mapOf(
    Resource.WOOD to Color(0xFF2E7D32),
    Resource.BRICK to Color(0xFFBF5B30),
    Resource.SHEEP to Color(0xFF9CCC65),
    Resource.WHEAT to Color(0xFFF2C94C),
    Resource.ORE to Color(0xFF8E8E9A),
    Resource.NONE to Color(0xFFE8D9A8),
)

/** Phase 1 preview: draws a generated board (terrain, number tokens, ports) from the engine. */
@Composable
fun BoardView(board: Board, caption: String, modifier: Modifier = Modifier) = Box(modifier) {
    val measurer = rememberTextMeasurer()
    Canvas(Modifier.fillMaxSize()) {
        val topo = board.topology
        // Lattice x units are sqrt(3)/2 of a hex side, y units are 1/2 a side.
        val xs = topo.vertexPos.map { it.x * sqrt(3f) / 2f }
        val ys = topo.vertexPos.map { it.y / 2f }
        val scale = 0.8f * min(size.width / (xs.max() - xs.min()), size.height / (ys.max() - ys.min()))
        fun at(v: Int) = Offset(center.x + xs[v] * scale, center.y + ys[v] * scale)

        for (h in 0 until board.hexCount) {
            val vs = topo.hexVertices[h]
            val path = Path().apply {
                moveTo(at(vs[0]).x, at(vs[0]).y)
                for (i in 1 until 6) lineTo(at(vs[i]).x, at(vs[i]).y)
                close()
            }
            drawPath(path, TERRAIN_COLORS.getValue(board.hexResource[h]))
            drawPath(path, Color(0xFF5B4A2F), style = Stroke(width = 3f))
            val n = board.hexNumber[h]
            if (n == 0) continue
            val c = Offset(vs.map { at(it).x }.average().toFloat(), vs.map { at(it).y }.average().toFloat())
            drawCircle(Color(0xFFFFF8E1), 0.32f * scale, c)
            val text = measurer.measure(
                n.toString(),
                TextStyle(
                    fontSize = (0.3f * scale / density).sp,
                    fontWeight = FontWeight.Bold,
                    color = if (n == 6 || n == 8) Color(0xFFC62828) else Color.Black,
                ),
            )
            drawText(text, topLeft = c - Offset(text.size.width / 2f, text.size.height / 2f))
        }
        for (p in board.ports) {
            val a = at(p.v1)
            val b = at(p.v2)
            val mid = Offset((a.x + b.x) / 2, (a.y + b.y) / 2)
            val out = mid + (mid - center) * 0.12f
            drawLine(Color(0xFF5B4A2F), a, out, strokeWidth = 4f)
            drawLine(Color(0xFF5B4A2F), b, out, strokeWidth = 4f)
            val color = if (p.type == Port.GENERIC) Color.White else TERRAIN_COLORS.getValue(p.type)
            drawCircle(color, 0.2f * scale, out)
            drawCircle(Color(0xFF5B4A2F), 0.2f * scale, out, style = Stroke(width = 3f))
        }
    }
    Text(caption, Modifier.padding(16.dp))
}
