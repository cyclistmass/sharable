package com.mauro.wgbg

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Tiles: compare (#32). Every terrain side by side in each candidate look, so Mauro can pick what we keep:
 * today's painted faces (what the board draws), Codex's A1 relief, and the V2 3D renders at flat, mid and max tilt.
 */
private val COMPARE_TERRAINS = listOf("Timber", "Clay", "Fleece", "Grain", "Stone", "Desert")
private val COMPARE_ROWS = listOf(
    "Painted (now)" to listOf(R.drawable.tile_timber, R.drawable.tile_clay, R.drawable.tile_fleece, R.drawable.tile_grain, R.drawable.tile_stone, R.drawable.tile_desert),
    "A1 relief" to listOf(R.drawable.tile_a1_relief_timber, R.drawable.tile_a1_relief_clay, R.drawable.tile_a1_relief_fleece, R.drawable.tile_a1_relief_grain, R.drawable.tile_a1_relief_stone, R.drawable.tile_a1_relief_desert),
    "V2 3D · flat" to listOf(R.drawable.tile3d_v2_timber_flat, R.drawable.tile3d_v2_clay_flat, R.drawable.tile3d_v2_fleece_flat, R.drawable.tile3d_v2_grain_flat, R.drawable.tile3d_v2_stone_flat, R.drawable.tile3d_v2_desert_flat),
    "V2 3D · mid tilt" to listOf(R.drawable.tile3d_v2_timber_mid, R.drawable.tile3d_v2_clay_mid, R.drawable.tile3d_v2_fleece_mid, R.drawable.tile3d_v2_grain_mid, R.drawable.tile3d_v2_stone_mid, R.drawable.tile3d_v2_desert_mid),
    "V2 3D · max tilt" to listOf(R.drawable.tile3d_v2_timber_max, R.drawable.tile3d_v2_clay_max, R.drawable.tile3d_v2_fleece_max, R.drawable.tile3d_v2_grain_max, R.drawable.tile3d_v2_stone_max, R.drawable.tile3d_v2_desert_max),
    "V3 3D · mid tilt (preview)" to listOf(R.drawable.t3d_timber_tilt_4, R.drawable.t3d_clay_tilt_4, R.drawable.t3d_fleece_tilt_4, R.drawable.t3d_grain_tilt_4, R.drawable.t3d_stone_tilt_4, R.drawable.t3d_desert_tilt_4),
)

@Composable
fun TileCompare(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().background(Color(0xFF0B0907)).testTag("tile-compare")) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Tiles: compare", style = Wood.title(20.sp, Color(0xFFF1D58A)))
                Text("pick what we keep (#32)", style = Wood.sans(12.sp, color = Wood.INK_MUTED))
                BrassButton("Back", "tile-compare-back", Modifier.height(32.dp)) { onBack() }
            }
            Row(Modifier.padding(start = 96.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (name in COMPARE_TERRAINS) Text(name, Modifier.width(108.dp), style = Wood.sans(12.sp, FontWeight.Bold), maxLines = 1)
            }
            for ((label, tiles) in COMPARE_ROWS) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(label, Modifier.width(92.dp), style = Wood.sans(11.sp, FontWeight.Bold, Wood.INK_MUTED))
                for ((i, id) in tiles.withIndex()) Image(painterResource(id), "$label {COMPARE_TERRAINS[i]}", Modifier.size(width = 108.dp, height = 62.dp),
                    contentScale = ContentScale.Fit)
            }
        }
    }
}
