const fs = require('node:fs');
const cp = require('node:child_process');
const path = require('node:path');
const temp = fs.mkdtempSync(path.join(require('node:os').tmpdir(), 'wgbg-patches-'));
const dir = 'app/src/main/java/com/mauro/wgbg/';
fs.mkdirSync('perf-patches',{recursive:true});
function patch(name, header, edits) {
 const original = fs.readFileSync(dir+name,'utf8').replaceAll('\r\n','\n');
 let result=original;
 for(const [from,to] of edits) {if(!result.includes(from))throw Error(name+': source changed');result=result.replace(from,to);}
 const a=path.join(temp,'a.kt'), b=path.join(temp,'b.kt'); fs.writeFileSync(a,original);fs.writeFileSync(b,result);
 let diff;try{diff=cp.execFileSync('git',['diff','--no-index','--no-ext-diff','--',a,b],{encoding:'utf8',stdio:['ignore','pipe','pipe']});}catch(e){if(e.status!==1)throw e;diff=e.stdout;}
 diff=diff.replace(/^diff --git .*$/m,'diff --git a/'+dir+name+' b/'+dir+name).replace(/^--- .*$/m,'--- a/'+dir+name).replace(/^\+\+\+ .*$/m,'+++ b/'+dir+name);
 fs.writeFileSync('perf-patches/'+name+'.patch',header.split('\n').map(x=>'# '+x).join('\n')+'\n'+diff);
}
patch('WoodPieces.kt', 'Slow: repeated wood decode per stain; polygon Paths and edge Stroke during board recording.\nWhy: brushes/shaders are already cached, but source decode and temporary geometry are not.\nExpected win: one source decode instead of N; reusable polygon path and outline stroke.\nRisk: scratch path requires synchronous, non-reentrant render-thread use. Vertex lists remain.\nStill-layer caching already limits this cost to board re-records, not every sea frame.', [
 ['private val stains: List<Brush> = colors.map { stain(res, it) }', 'private val stains: List<Brush> = run {\n        val grain = BitmapFactory.decodeResource(res, R.drawable.wood_grain, BitmapFactory.Options().apply { inScaled = false })\n        try { colors.map { stain(grain, it) } } finally { grain.recycle() }\n    }\n    private val polygon = Path() // Synchronous draw calls only; never retain in a scene.\n    private val edgeStroke = Stroke(width = 1.4f, join = StrokeJoin.Round)'],
 ['style = Stroke(width = 1.4f, join = StrokeJoin.Round)', 'style = edgeStroke'],
 ['private fun poly(pts: List<Offset>) = Path().apply {','private fun poly(pts: List<Offset>) = polygon.apply {\n        reset()'],
 ['private fun stain(res: Resources, color: Color): Brush {\n            val grain = BitmapFactory.decodeResource(res, R.drawable.wood_grain, BitmapFactory.Options().apply { inScaled = false })','private fun stain(grain: Bitmap, color: Color): Brush {']
]);
patch('RollingDice.kt', 'Slow: sprite sheet decode before any roll; Pair<Offset, Float> per die/frame in non-slot path.\nWhy: cached imageResource runs before the early return; at() boxes its return values.\nExpected win: defer two sheets; remove two result pairs and boxed values per fallback frame.\nRisk: first throw still decodes synchronously; async prewarm remains an owner integration task.\nAnimatable reads already occur in Canvas draw: no parent 60 Hz recomposition fix needed.', [
 ['    val ivory = ImageBitmap.imageResource(R.drawable.dice_ivory)\n    val red = ImageBitmap.imageResource(R.drawable.dice_red)\n',''],
 ['    if (die1 <= 0 || rest == null) return','    if (die1 <= 0 || rest == null) return\n    val ivory = ImageBitmap.imageResource(R.drawable.dice_ivory)\n    val red = ImageBitmap.imageResource(R.drawable.dice_red)'],
 ['fun at(p: Float, lead: Float): Pair<Offset, Float> {','fun drawAt(sheet: ImageBitmap, face: Int, p: Float, lead: Float, frameSpin: Int, spin: Float) {'],
 ['return pos to (1f + 0.35f * hop)','drawDie(sheet, DiceSprites.frame(p, face, frameSpin), pos, side * (1f + 0.35f * hop), spin)'],
 ['        val (a, ka) = at(p, 0f)\n        drawDie(red, DiceSprites.frame(p, die1, 3), a, side * ka, (1 - p) * 420f)\n        val (b, kb) = at(q, side * 1.12f)\n        drawDie(ivory, DiceSprites.frame(q, die2, 9), b, side * kb, (1 - q) * -480f)', '        drawAt(red, die1, p, 0f, 3, (1 - p) * 420f)\n        drawAt(ivory, die2, q, side * 1.12f, 9, (1 - q) * -480f)']
]);
patch('Hud.kt','Slow: hidden Die3d still decodes a face; outline Stroke constructed on redraw.\nWhy: imageResource precedes draw-only visibility check.\nExpected win: avoid hidden-face decode; retain one invariant stroke.\nRisk: new visible faces still decode synchronously. Pad/layout preserved.\nNo sea-frame state read found in this composable.',[
 ['val face = androidx.compose.ui.graphics.ImageBitmap.imageResource(diceFace(red, pirate, value))','val face = if (visible) androidx.compose.ui.graphics.ImageBitmap.imageResource(diceFace(red, pirate, value)) else null\n    val outline = androidx.compose.runtime.remember { androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f) }'],
 ['style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f)','style = outline'],
 ['if (!visible) return@Canvas','if (face == null) return@Canvas']
]);
patch('Tabletop.kt','Slow: full-size texture decode then resize; wash brush/list recreated on redraw.\nWhy: drawBehind builds objects in the frame callback.\nExpected win: sampled intermediate decode, cached gradient on table redraws.\nRisk: sampling slightly changes resize filtering; evaluate texture parity on device.\nRequires RenderAssets.kt; owner can extend the same cache pattern to brass panels.',[
 ['import android.graphics.Bitmap\nimport android.graphics.BitmapFactory\n',''],
 ['import androidx.compose.ui.draw.drawBehind','import androidx.compose.ui.draw.drawBehind\nimport androidx.compose.ui.draw.drawWithCache'],
 ['        val src = BitmapFactory.decodeResource(context.resources, res, BitmapFactory.Options().apply { inScaled = false })\n        val scaled = Bitmap.createScaledBitmap(src, px, (px.toLong() * src.height / src.width).toInt().coerceAtLeast(1), true)','        val scaled = decodeRenderTexture(context.resources, res, px)'],
 ['    return drawBehind {\n        drawRect(wood)\n        drawRect(Brush.verticalGradient(listOf(Color(0xCC140B05), Color(0xE6080402))))\n    }','    return drawWithCache {\n        val wash = Brush.verticalGradient(listOf(Color(0xCC140B05), Color(0xE6080402)))\n        onDrawBehind {\n            drawRect(wood)\n            drawRect(wash)\n        }\n    }']
]);
patch('TradePanel.kt','Slow: chest gradients/lists/Stroke on each redraw.\nWhy: these depend on size/density only, but are created inside drawBehind.\nExpected win: size-aware cached gradients and outline; primitive corner loops.\nRisk: cache must invalidate on size/density changes. Geometry preserved.\nKEEP tick: it is the engine move version. No sea-frame composition leak found.',[
 ['import androidx.compose.ui.draw.drawBehind','import androidx.compose.ui.draw.drawWithCache'],
 ['    .drawBehind {\n        val r = CornerRadius(14.dp.toPx())\n        drawRoundRect(Brush.verticalGradient(listOf(WOOD_MID, WOOD_DARK, WOOD_MID, WOOD_DARK)), cornerRadius = r)','    .drawWithCache {\n        val r = CornerRadius(14.dp.toPx())\n        val wood = Brush.verticalGradient(listOf(WOOD_MID, WOOD_DARK, WOOD_MID, WOOD_DARK))\n        val brass = Brush.linearGradient(listOf(BRASS_DARK, BRASS_LIGHT, BRASS, BRASS_DARK), Offset.Zero, Offset(size.width, size.height))\n        val rim = Stroke(4.dp.toPx())\n        val c = 18.dp.toPx()\n        val corner = CornerRadius(4.dp.toPx())\n        val rivet = 2.5.dp.toPx()\n        onDrawBehind {\n        drawRoundRect(wood, cornerRadius = r)'],
 ['        val brass = Brush.linearGradient(listOf(BRASS_DARK, BRASS_LIGHT, BRASS, BRASS_DARK), Offset.Zero, Offset(size.width, size.height))\n        drawRoundRect(brass, cornerRadius = r, style = Stroke(4.dp.toPx()))','        drawRoundRect(brass, cornerRadius = r, style = rim)'],
 ['        val c = 18.dp.toPx()\n        for (x in listOf(0f, size.width - c)) for (y in listOf(0f, size.height - c)) {\n            drawRoundRect(brass, Offset(x, y), Size(c, c), CornerRadius(4.dp.toPx()))\n            drawCircle(BRASS_DARK, 2.5.dp.toPx(), Offset(x + c / 2, y + c / 2))\n        }\n    }','        for (ix in 0..1) for (iy in 0..1) {\n            val x = if (ix == 0) 0f else size.width - c\n            val y = if (iy == 0) 0f else size.height - c\n            drawRoundRect(brass, Offset(x, y), Size(c, c), corner)\n            drawCircle(BRASS_DARK, rivet, Offset(x + c / 2, y + c / 2))\n        }\n        }\n    }']
]);
console.log('Created 5 proposals; owner source files were read only.');
