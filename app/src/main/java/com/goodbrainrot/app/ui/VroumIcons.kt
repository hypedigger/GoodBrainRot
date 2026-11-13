package com.goodbrainrot.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ---------------------------------------------------------------------------------------------------------------------
// PICTOGRAMMES de Vroum, dessinés (plus d'émojis : ils changent d'un téléphone à l'autre et cassent le style du jeu).
// Tous tiennent dans un carré ; [tint] colore le trait principal, les détails ont leurs couleurs propres.
// ---------------------------------------------------------------------------------------------------------------------

internal enum class VIc { FLAG, TROPHY, REPLAY, MAP, WHEEL, GHOST, BOLT, FUEL, CLOCK, LEFT, RIGHT, DOWN, CHECK, CROSS, COIN, LOCK, STAR }

private val IC_INK = Color(0xFF2A2440)

@Composable
internal fun VIcon(kind: VIc, size: Dp = 20.dp, tint: Color = Color.White, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) { drawVIcon(kind, tint) }
}

private fun DrawScope.poly(c: Color, vararg p: Float) {
    val s = size.minDimension
    val path = Path().apply { moveTo(p[0] * s, p[1] * s); var i = 2; while (i < p.size) { lineTo(p[i] * s, p[i + 1] * s); i += 2 }; close() }
    drawPath(path, c)
}

internal fun DrawScope.drawVIcon(kind: VIc, tint: Color) {
    val s = size.minDimension
    fun o(x: Float, y: Float) = Offset(x * s, y * s)
    val w = s * 0.11f
    when (kind) {
        VIc.FLAG -> {
            // drapeau à damier sur sa hampe
            drawLine(tint, o(0.16f, 0.08f), o(0.16f, 0.94f), w, StrokeCap.Round)
            val x0 = 0.22f; val y0 = 0.10f; val cw = 0.175f; val ch = 0.16f
            drawRoundRect(Color.White, o(x0, y0), Size(cw * 4 * s, ch * 3 * s), CornerRadius(s * 0.03f))
            for (r in 0..2) for (c in 0..3) if ((r + c) % 2 == 0) drawRect(IC_INK, o(x0 + c * cw, y0 + r * ch), Size(cw * s, ch * s))
        }
        VIc.TROPHY -> {
            val gold = if (tint == Color.White) V_GOLD else tint
            // anses
            drawArc(gold, 180f, 180f, false, o(0.06f, 0.14f), Size(0.30f * s, 0.34f * s), style = Stroke(w * 0.75f)); drawArc(gold, 90f, 90f, false, o(0.06f, 0.14f), Size(0.30f * s, 0.34f * s), style = Stroke(w * 0.75f))
            drawArc(gold, 180f, 180f, false, o(0.64f, 0.14f), Size(0.30f * s, 0.34f * s), style = Stroke(w * 0.75f)); drawArc(gold, 0f, 90f, false, o(0.64f, 0.14f), Size(0.30f * s, 0.34f * s), style = Stroke(w * 0.75f))
            // coupe
            val p = Path().apply { moveTo(0.24f * s, 0.10f * s); lineTo(0.76f * s, 0.10f * s); cubicTo(0.76f * s, 0.46f * s, 0.66f * s, 0.62f * s, 0.5f * s, 0.64f * s); cubicTo(0.34f * s, 0.62f * s, 0.24f * s, 0.46f * s, 0.24f * s, 0.10f * s); close() }
            drawPath(p, gold)
            drawRect(gold, o(0.44f, 0.62f), Size(0.12f * s, 0.16f * s))
            drawRoundRect(gold, o(0.27f, 0.77f), Size(0.46f * s, 0.14f * s), CornerRadius(s * 0.04f))
            drawLine(Color.White.copy(alpha = 0.7f), o(0.34f, 0.18f), o(0.36f, 0.40f), w * 0.5f, StrokeCap.Round)
        }
        VIc.REPLAY -> {
            // flèche qui tourne
            drawArc(tint, -50f, 290f, false, o(0.16f, 0.16f), Size(0.68f * s, 0.68f * s), style = Stroke(w * 1.15f, cap = StrokeCap.Round))
            poly(tint, 0.60f, 0.02f, 0.98f, 0.20f, 0.62f, 0.44f)
        }
        VIc.MAP -> {
            // repère de carte au-dessus d'une route en pointillés
            val pin = Path().apply { moveTo(0.5f * s, 0.66f * s); cubicTo(0.20f * s, 0.36f * s, 0.26f * s, 0.06f * s, 0.5f * s, 0.06f * s); cubicTo(0.74f * s, 0.06f * s, 0.80f * s, 0.36f * s, 0.5f * s, 0.66f * s); close() }
            drawPath(pin, tint); drawCircle(IC_INK.copy(alpha = 0.85f), 0.085f * s, o(0.5f, 0.29f))
            for (i in 0..3) drawLine(tint, o(0.10f + i * 0.23f, 0.86f), o(0.22f + i * 0.23f, 0.86f), w * 0.8f, StrokeCap.Round)
        }
        VIc.WHEEL -> {
            // volant
            drawCircle(tint, 0.40f * s, o(0.5f, 0.5f), style = Stroke(w * 1.15f))
            drawCircle(tint, 0.12f * s, o(0.5f, 0.52f))
            drawLine(tint, o(0.12f, 0.46f), o(0.88f, 0.46f), w, StrokeCap.Round)
            drawLine(tint, o(0.5f, 0.52f), o(0.5f, 0.88f), w, StrokeCap.Round)
        }
        VIc.GHOST -> {
            val p = Path().apply {
                moveTo(0.18f * s, 0.90f * s); lineTo(0.18f * s, 0.42f * s); cubicTo(0.18f * s, -0.02f * s, 0.82f * s, -0.02f * s, 0.82f * s, 0.42f * s); lineTo(0.82f * s, 0.90f * s)
                lineTo(0.66f * s, 0.76f * s); lineTo(0.5f * s, 0.90f * s); lineTo(0.34f * s, 0.76f * s); close()
            }
            drawPath(p, tint); drawCircle(IC_INK, 0.07f * s, o(0.38f, 0.42f)); drawCircle(IC_INK, 0.07f * s, o(0.62f, 0.42f))
        }
        VIc.BOLT -> poly(if (tint == Color.White) V_GOLD else tint, 0.60f, 0.02f, 0.18f, 0.56f, 0.46f, 0.56f, 0.36f, 0.98f, 0.82f, 0.40f, 0.54f, 0.40f)
        VIc.FUEL -> {
            // pompe à essence
            drawRoundRect(tint, o(0.14f, 0.10f), Size(0.48f * s, 0.80f * s), CornerRadius(s * 0.08f))
            drawRoundRect(IC_INK.copy(alpha = 0.8f), o(0.22f, 0.20f), Size(0.32f * s, 0.22f * s), CornerRadius(s * 0.04f))
            val h = Path().apply { moveTo(0.62f * s, 0.34f * s); lineTo(0.80f * s, 0.34f * s); lineTo(0.80f * s, 0.74f * s) }
            drawPath(h, tint, style = Stroke(w * 0.85f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawCircle(tint, 0.07f * s, o(0.80f, 0.78f))
            drawRoundRect(tint, o(0.08f, 0.86f), Size(0.60f * s, 0.08f * s), CornerRadius(s * 0.03f))
        }
        VIc.CLOCK -> {
            drawCircle(tint, 0.36f * s, o(0.5f, 0.56f), style = Stroke(w))
            drawLine(tint, o(0.5f, 0.56f), o(0.5f, 0.34f), w * 0.8f, StrokeCap.Round); drawLine(tint, o(0.5f, 0.56f), o(0.66f, 0.62f), w * 0.8f, StrokeCap.Round)
            drawRoundRect(tint, o(0.40f, 0.03f), Size(0.20f * s, 0.10f * s), CornerRadius(s * 0.03f))
        }
        VIc.LEFT, VIc.RIGHT, VIc.DOWN -> {
            val p = when (kind) {
                VIc.LEFT -> Path().apply { moveTo(0.64f * s, 0.14f * s); lineTo(0.28f * s, 0.5f * s); lineTo(0.64f * s, 0.86f * s) }
                VIc.RIGHT -> Path().apply { moveTo(0.36f * s, 0.14f * s); lineTo(0.72f * s, 0.5f * s); lineTo(0.36f * s, 0.86f * s) }
                else -> Path().apply { moveTo(0.14f * s, 0.36f * s); lineTo(0.5f * s, 0.72f * s); lineTo(0.86f * s, 0.36f * s) }
            }
            drawPath(p, tint, style = Stroke(w * 1.7f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        VIc.CHECK -> {
            drawCircle(Color(0xFF2FB56A), 0.46f * s, o(0.5f, 0.5f))
            drawPath(Path().apply { moveTo(0.27f * s, 0.52f * s); lineTo(0.44f * s, 0.68f * s); lineTo(0.74f * s, 0.34f * s) }, Color.White, style = Stroke(w * 1.2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        VIc.CROSS -> {
            drawCircle(Color(0xFFE04848), 0.46f * s, o(0.5f, 0.5f))
            drawLine(Color.White, o(0.32f, 0.32f), o(0.68f, 0.68f), w * 1.2f, StrokeCap.Round); drawLine(Color.White, o(0.68f, 0.32f), o(0.32f, 0.68f), w * 1.2f, StrokeCap.Round)
        }
        VIc.COIN -> {
            drawCircle(Color(0xFFE8A200), 0.46f * s, o(0.5f, 0.5f)); drawCircle(V_GOLD, 0.38f * s, o(0.5f, 0.5f))
            drawCircle(Color(0xFFE8A200), 0.26f * s, o(0.5f, 0.5f), style = Stroke(w * 0.5f))
            drawLine(Color.White.copy(alpha = 0.8f), o(0.34f, 0.30f), o(0.42f, 0.24f), w * 0.5f, StrokeCap.Round)
        }
        VIc.LOCK -> {
            drawArc(tint, 180f, 180f, false, o(0.28f, 0.10f), Size(0.44f * s, 0.52f * s), style = Stroke(w * 1.1f))
            drawRoundRect(tint, o(0.18f, 0.42f), Size(0.64f * s, 0.50f * s), CornerRadius(s * 0.10f))
            drawCircle(IC_INK.copy(alpha = 0.8f), 0.07f * s, o(0.5f, 0.62f)); drawLine(IC_INK.copy(alpha = 0.8f), o(0.5f, 0.64f), o(0.5f, 0.78f), w * 0.6f, StrokeCap.Round)
        }
        VIc.STAR -> {
            val gold = if (tint == Color.White) V_GOLD else tint
            val p = Path()
            for (i in 0 until 10) { val a = Math.toRadians((-90 + i * 36).toDouble()); val r = if (i % 2 == 0) 0.48f else 0.20f
                val x = (0.5f + r * Math.cos(a).toFloat()) * s; val y = (0.52f + r * Math.sin(a).toFloat()) * s; if (i == 0) p.moveTo(x, y) else p.lineTo(x, y) }
            p.close(); drawPath(p, gold)
        }
    }
}
