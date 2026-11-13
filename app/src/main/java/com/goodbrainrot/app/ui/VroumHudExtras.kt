package com.goodbrainrot.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.goodbrainrot.app.R
import kotlin.math.sin

// ===========================================================================
// Vroum — éléments de jeu façon Burnout / Mario Kart : bouton BOOST à tenir, jauge à segments
// qui s'allonge avec les takedowns, case d'objet (roulette puis minuterie), compteur de takedowns.
// ===========================================================================

private val BOOST_BLUE = Color(0xFF39A8FF)
private val BOOST_HOT = Color(0xFFFF7A1A)

/** Bouton BOOST : on le TIENT pour brûler la jauge. Gris quand elle est vide, il palpite quand elle est pleine. */
@Composable
internal fun BoostButton(g: VroumGame, frame: Int, modifier: Modifier = Modifier, onHold: (Boolean) -> Unit) {
    @Suppress("UNUSED_EXPRESSION") frame
    val ready = g.boost > 0.12f
    val on = g.boosting
    val full = g.boost >= g.boostSeg - 0.01f
    val sc = if (on) 0.92f else if (full) 1f + 0.05f * sin(g.elapsed * 9f) else 1f
    Box(modifier.size(92.dp).scale(sc).shadow(if (ready) 14.dp else 4.dp, CircleShape, clip = false).clip(CircleShape)
        .background(if (on) Brush.verticalGradient(listOf(Color(0xFFFFD36B), BOOST_HOT, Color(0xFFE0301E)))
                    else if (ready) Brush.verticalGradient(listOf(Color(0xFFFFB04A), Color(0xFFF2541B)))
                    else Brush.verticalGradient(listOf(Color(0xAA8A93A8), Color(0xAA5C6478))))
        .border(4.dp, Color.White.copy(alpha = if (ready) 0.9f else 0.5f), CircleShape)
        .pointerInput(Unit) { detectTapGestures(onPress = { onHold(true); tryAwaitRelease(); onHold(false) }) },
        contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width; val h = size.height
            val col = if (ready) Color.White else Color.White.copy(alpha = 0.55f)
            // flamme stylisée
            val fl = if (on) 1f + 0.08f * sin(g.elapsed * 40f) else 1f
            val p = Path().apply {
                moveTo(w * 0.50f, h * (0.16f / fl)); cubicTo(w * 0.78f, h * 0.36f, w * 0.74f, h * 0.60f, w * 0.60f, h * 0.70f)
                cubicTo(w * 0.62f, h * 0.60f, w * 0.56f, h * 0.52f, w * 0.50f, h * 0.48f)
                cubicTo(w * 0.44f, h * 0.56f, w * 0.40f, h * 0.62f, w * 0.42f, h * 0.70f)
                cubicTo(w * 0.24f, h * 0.60f, w * 0.26f, h * 0.38f, w * 0.50f, h * (0.16f / fl)); close()
            }
            drawPath(p, col)
            // anneau = contenu de la jauge
            drawArc(Color.White.copy(alpha = 0.25f), -90f, 360f, false, Offset(w * 0.06f, h * 0.06f), Size(w * 0.88f, h * 0.88f), style = Stroke(w * 0.05f))
            drawArc(if (on) Color(0xFFFFF3B0) else Color.White, -90f, 360f * (g.boost / g.boostSeg).coerceIn(0f, 1f), false, Offset(w * 0.06f, h * 0.06f), Size(w * 0.88f, h * 0.88f),
                style = Stroke(w * 0.06f, cap = StrokeCap.Round))
        }
        Text(stringResource(R.string.vroum_boost), color = if (ready) Color.White else Color.White.copy(alpha = 0.6f), fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic,
            fontSize = 13.sp, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 13.dp))
    }
}

/** Jauge de boost : 1 à 4 segments (elle s'allonge avec les takedowns), bleue, incandescente quand on brûle. */
@Composable
internal fun BoostBar(g: VroumGame, frame: Int, modifier: Modifier = Modifier) {
    @Suppress("UNUSED_EXPRESSION") frame
    Row(modifier.clip(RoundedCornerShape(14.dp)).background(Color(0x88000000)).padding(horizontal = 8.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(width = (54 * VroumGame.MAX_SEG + 7 * (VroumGame.MAX_SEG - 1)).dp, height = 20.dp)) {
            val n = VroumGame.MAX_SEG; val gap = size.height * 0.36f
            val sw = (size.width - gap * (n - 1)) / n
            for (i in 0 until n) {
                val x = i * (sw + gap)
                val owned = i < g.boostSeg
                // parallélogrammes inclinés, façon compteur de course
                fun para(x0: Float, wd: Float, col: Brush, a: Float = 1f) {
                    val k = size.height * 0.35f
                    val p = Path().apply { moveTo(x0 + k, 0f); lineTo(x0 + wd + k, 0f); lineTo(x0 + wd, size.height); lineTo(x0, size.height); close() }
                    drawPath(p, col, alpha = a)
                }
                para(x - size.height * 0.17f, sw, Brush.verticalGradient(listOf(Color.White, Color.White)), if (owned) 0.22f else 0.07f)
                val f = (g.boost - i).coerceIn(0f, 1f)
                if (owned && f > 0f) para(x - size.height * 0.17f, sw * f,
                    if (g.boosting) Brush.horizontalGradient(listOf(Color(0xFFFFE08A), BOOST_HOT)) else Brush.horizontalGradient(listOf(Color(0xFF8FD3FF), BOOST_BLUE)))
            }
        }
        if (g.boostSeg > 1) Text(" ×${g.boostSeg}", color = if (g.boosting) BOOST_HOT else Color.White, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic, fontSize = 15.sp)
    }
}

/** Case d'objet : la roulette défile, puis l'objet actif reste affiché avec le temps qu'il lui reste. */
@Composable
internal fun ItemSlot(g: VroumGame, icons: Map<VWeapon, ImageBitmap?>, frame: Int, modifier: Modifier = Modifier) {
    @Suppress("UNUSED_EXPRESSION") frame
    val pool = listOf(VWeapon.BOMB, VWeapon.LASER, VWeapon.FLAME, VWeapon.MAGNET, VWeapon.SHIELD, VWeapon.CANNON, VWeapon.STAR, VWeapon.SUPER)
    val spinning = g.roulette > 0f
    val (shown, frac) = when {
        spinning -> pool[((g.elapsed * 16f).toInt()) % pool.size] to 1f
        g.weapon != VWeapon.NONE -> g.weapon to (g.weaponT / g.weapon.dur).coerceIn(0f, 1f)
        g.cannonT > 0f -> VWeapon.CANNON to (g.cannonT / 8f).coerceIn(0f, 1f)
        g.shieldT > 0f -> VWeapon.SHIELD to (g.shieldT / VWeapon.SHIELD.dur).coerceIn(0f, 1f)
        else -> VWeapon.NONE to 0f
    }
    if (shown == VWeapon.NONE) return
    Box(modifier.size(66.dp).shadow(10.dp, RoundedCornerShape(18.dp), clip = false).clip(RoundedCornerShape(18.dp))
        .background(Brush.verticalGradient(listOf(Color(0xF0FFFFFF), Color(0xE6E9E4FF))))
        .border(3.dp, if (spinning) V_GOLD else V_CYAN, RoundedCornerShape(18.dp)), contentAlignment = Alignment.Center) {
        icons[shown]?.let { Image(BitmapPainter(it), null, Modifier.fillMaxSize().padding(9.dp), contentScale = ContentScale.Fit) }
        if (!spinning) Canvas(Modifier.fillMaxSize()) {
            drawRoundRect(Color(0x33000000), Offset(size.width * 0.1f, size.height * 0.86f), Size(size.width * 0.8f, size.height * 0.07f), CornerRadius(8f, 8f))
            drawRoundRect(V_CYAN, Offset(size.width * 0.1f, size.height * 0.86f), Size(size.width * 0.8f * frac, size.height * 0.07f), CornerRadius(8f, 8f))
        }
    }
}

/** Compteur de takedowns (il grossit un instant à chaque nouveau takedown). */
@Composable
internal fun TakedownChip(g: VroumGame, frame: Int, modifier: Modifier = Modifier) {
    @Suppress("UNUSED_EXPRESSION") frame
    if (g.takedowns <= 0) return
    Row(modifier.clip(RoundedCornerShape(14.dp)).background(Color(0x88000000)).padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(18.dp)) {
            // éclat d'impact
            val c = Offset(size.width / 2f, size.height / 2f)
            for (i in 0 until 8) { val a = i * 0.7854f; val r = size.width * (if (i % 2 == 0) 0.5f else 0.3f)
                drawLine(V_GOLD, c, Offset(c.x + kotlin.math.cos(a) * r, c.y + sin(a) * r), strokeWidth = size.width * 0.14f, cap = StrokeCap.Round) }
        }
        Text(" ${g.takedowns}", color = Color.White, fontWeight = FontWeight.Black, fontSize = 17.sp)
    }
}
