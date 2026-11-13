package com.goodbrainrot.app.ui

import android.content.Context
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.core.content.res.ResourcesCompat
import com.goodbrainrot.app.R
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

// ===========================================================================
// Vroum — habillage « jeu d'arcade » : police Rubik (OFL), messages à contour épais et dégradé qui claquent à
// l'apparition, éclat d'impact derrière les takedowns, et zone de score qui encaisse les gros gains (comètes dorées
// qui filent vers le compteur, compteur qui roule et qui gonfle). Les « +N » flottants ne sont plus affichés.
// ===========================================================================

/** Police de tout l'écran Vroum (menus, compteurs, messages). */
internal val VROUM_FONT = FontFamily(
    Font(R.font.rubik_medium, FontWeight.Normal), Font(R.font.rubik_medium, FontWeight.Medium),
    Font(R.font.rubik_bold, FontWeight.SemiBold), Font(R.font.rubik_bold, FontWeight.Bold),
    Font(R.font.rubik_black, FontWeight.ExtraBold), Font(R.font.rubik_black, FontWeight.Black),
    Font(R.font.rubik_black_italic, FontWeight.Bold, FontStyle.Italic), Font(R.font.rubik_black_italic, FontWeight.Black, FontStyle.Italic),
)

private const val INK = 0xFF24103C.toInt()          // contour : violet nuit
private const val GAIN_BIG = 60                     // à partir de ce gain, des comètes partent vers le score

internal class VroumFx(ctx: Context) {
    private val face: Typeface = ResourcesCompat.getFont(ctx, R.font.rubik_black_italic) ?: Typeface.DEFAULT_BOLD
    private val stroke = Paint().apply { isAntiAlias = true; textAlign = Paint.Align.CENTER; typeface = face; style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND; color = INK }
    private val fill = Paint().apply { isAntiAlias = true; textAlign = Paint.Align.CENTER; typeface = face; style = Paint.Style.FILL }
    private val shape = Paint().apply { isAntiAlias = true }
    private val burst = android.graphics.Path()
    private val boxes = FloatArray(24 * 4)
    private val mtx = Matrix()
    // dégradés verticaux (clair en haut → couleur en bas), un par couleur de message, de hauteur 1 puis mis à l'échelle
    private val gradKeys = IntArray(12); private val grads = arrayOfNulls<LinearGradient>(12); private var gradN = 0

    // ---- score : valeur affichée (elle roule vers la vraie), « coup de poing » et lueur de la carte, comètes en vol
    var shown = 0f; private set
    var punch = 0f; private set
    var glow = 0f; private set
    private var target = 0f
    private var lastScore = 0
    private var lastNs = 0L
    private val cx = FloatArray(16); private val cy = FloatArray(16); private val ct = FloatArray(16); private val camt = FloatArray(16); private val cbend = FloatArray(16)
    private var seed = 1
    init { ct.fill(-1f) }      // −1 = case libre

    fun reset(score: Int = 0) { shown = score.toFloat(); target = shown; lastScore = score; punch = 0f; glow = 0f; ct.fill(-1f); lastNs = 0L }

    private fun gradFor(argb: Int): LinearGradient {
        for (i in 0 until gradN) if (gradKeys[i] == argb) return grads[i]!!
        val r = (argb shr 16) and 255; val g = (argb shr 8) and 255; val b = argb and 255
        fun mix(c: Int, k: Float) = (c + (255 - c) * k).toInt()
        val top = (0xFF shl 24) or (mix(r, 0.72f) shl 16) or (mix(g, 0.72f) shl 8) or mix(b, 0.72f)
        val bot = (0xFF shl 24) or ((r * 0.92f).toInt() shl 16) or ((g * 0.80f).toInt() shl 8) or (b * 0.80f).toInt()
        val sh = LinearGradient(0f, 0f, 0f, 1f, intArrayOf(top, argb or (0xFF shl 24), bot), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        if (gradN < gradKeys.size) { gradKeys[gradN] = argb; grads[gradN] = sh; gradN++ }
        return sh
    }

    /** À appeler une fois par image depuis le Canvas du jeu. [scoreAt] = centre de la carte de score (null : pas de comètes). */
    fun draw(scope: DrawScope, g: VroumGame, scoreAt: Offset?) = with(scope) {
        val now = System.nanoTime()
        val dt = if (lastNs == 0L) 0.016f else ((now - lastNs) / 1e9f).coerceIn(0f, 0.05f)
        lastNs = now
        val u = size.height / 100f
        val snap = g.textSnap

        // ---- gains de score : petits = le compteur avance ; gros = des comètes partent de l'action vers le compteur
        val d = g.score - lastScore
        if (d < 0) reset(g.score)
        else if (d > 0) {
            lastScore = g.score
            if (d >= GAIN_BIG && scoreAt != null) {
                var sx = size.width / 2f; var sy = size.height * 0.60f
                for (i in snap.indices.reversed()) { val t = snap[i]; if (t.gain > 0 && t.sx > -500f) { sx = t.sx; sy = t.sy; break } }
                val n = (2 + d / 90).coerceAtMost(7)
                var left = n
                for (i in ct.indices) {
                    if (left == 0) break
                    if (ct[i] >= 0f) continue
                    seed = seed * 1103515245 + 12345
                    val rnd = ((seed ushr 8) and 1023) / 1023f
                    cx[i] = sx + (rnd - 0.5f) * u * 9f; cy[i] = sy + (rnd - 0.5f) * u * 5f
                    ct[i] = 0.0001f - (n - left) * 0.045f                // départs échelonnés (valeur négative = attente)
                    if (ct[i] < 0f) ct[i] = -1f - (n - left) * 0.045f      // (codée sous −1 pour la distinguer d'une case libre)
                    camt[i] = d.toFloat() / n; cbend[i] = (rnd - 0.5f) * 2f
                    left--
                }
                if (left > 0) target += d.toFloat() * left / n       // plus de place : le reste est compté tout de suite
            } else { target += d; punch = max(punch, 0.22f) }
        }
        // comètes en vol
        if (scoreAt != null) for (i in ct.indices) {
            val t0 = ct[i]
            if (t0 == -1f) continue
            if (t0 < -1f) { ct[i] = if (t0 + dt >= -1f) 0.0001f else t0 + dt; continue }      // en attente de départ
            val t = t0 + dt / 0.42f
            if (t >= 1f) { ct[i] = -1f; target += camt[i]; punch = min(1f, punch + 0.55f); glow = 1f; continue }
            ct[i] = t
            val e = t * t * (3f - 2f * t)
            val mx = (cx[i] + scoreAt.x) / 2f + cbend[i] * u * 14f; val my = min(cy[i], scoreAt.y) - u * 6f
            for (k in 0 until 5) {                                        // tête + traînée
                val tt = (e - k * 0.05f).coerceAtLeast(0f); val it = 1f - tt
                val x = it * it * cx[i] + 2f * it * tt * mx + tt * tt * scoreAt.x
                val y = it * it * cy[i] + 2f * it * tt * my + tt * tt * scoreAt.y
                drawCircle(if (k == 0) Color.White else V_GOLD, u * (1.25f - k * 0.2f), Offset(x, y), alpha = 1f - k * 0.18f)
            }
        }
        // le compteur roule vers la vraie valeur, le coup de poing retombe
        val gap = target - shown
        shown = if (gap < 1.5f) target else shown + gap * min(1f, dt * 9f)
        punch = max(0f, punch - dt * 2.6f); glow = max(0f, glow - dt * 1.8f)
        // éclat autour de la carte de score quand une comète arrive
        if (scoreAt != null && glow > 0.02f) {
            val r0 = u * (5f + 5f * (1f - glow)); val r1 = r0 + u * 3.2f * glow
            for (k in 0 until 10) { val a = k * 0.6283f + 0.3f
                drawLine(V_GOLD.copy(alpha = glow), Offset(scoreAt.x + cos(a) * r0 * 1.5f, scoreAt.y + sin(a) * r0), Offset(scoreAt.x + cos(a) * r1 * 1.5f, scoreAt.y + sin(a) * r1), strokeWidth = u * 0.5f) }
        }

        // ---- messages flottants (les compteurs de points « +N » ne sont plus affichés)
        if (snap.isEmpty()) return@with
        drawIntoCanvas { c ->
            val nc = c.nativeCanvas
            var nb = 0
            for (i in snap.indices.reversed()) {                           // du plus récent au plus ancien
                val t = snap[i]
                if (t.sx < -500f || t.gain > 0) continue
                val impact = t.style == 1
                val label = t.text
                val a = (t.life / 0.22f).coerceIn(0f, 1f)                   // opaque, puis fondu final très court
                val pop = t.pop * t.pop
                var ts = u * (if (impact) 6.6f else if (t.big) 5.6f else 4.4f)
                fill.textSize = ts
                var hw = fill.measureText(label) / 2f
                val maxW = size.width * (if (impact) 0.62f else 0.78f)
                if (hw * 2f > maxW) { ts *= maxW / (hw * 2f); fill.textSize = ts; hw = fill.measureText(label) / 2f }
                // les messages d'impact ont une place FIXE dans le ciel, sous les jauges : ils ne cachent jamais la route
                val tx = if (impact) size.width / 2f else if (hw * 2f + u * 3f < size.width) t.sx.coerceIn(hw + u * 1.5f, size.width - hw - u * 1.5f) else size.width / 2f
                var y = if (impact) size.height * (if (size.width > size.height) 0.30f else 0.285f) else t.sy; var tries = 0
                while (tries < 6) {
                    var hit = false
                    for (b in 0 until nb) { val o = b * 4
                        if (tx - hw < boxes[o + 2] && tx + hw > boxes[o] && y - ts < boxes[o + 3] && y > boxes[o + 1]) { y = boxes[o + 1] - u * 0.8f; hit = true } }
                    if (!hit) break
                    tries++
                }
                if (nb < 24) { val o = nb * 4; boxes[o] = tx - hw; boxes[o + 1] = y - ts; boxes[o + 2] = tx + hw; boxes[o + 3] = y; nb++ }
                val alpha = (a * 255).toInt()
                // il arrive en grand et se tasse (sans jamais dépasser de l'écran)
                val sc = min(1f + (if (impact) 0.75f else 0.5f) * pop, size.width * 0.94f / (hw * 2f * (if (impact) 1.22f else 1f)))
                nc.save()
                nc.rotate(if (impact) -7f else if (t.big) -4f else 0f, tx, y - ts * 0.35f)
                nc.scale(sc, sc, tx, y - ts * 0.35f)
                if (impact) {                                             // éclat d'impact façon bande dessinée, derrière le mot
                    val cyb = y - ts * 0.36f; val rx = hw * 1.20f; val ry = ts * 1.15f
                    burst.rewind()
                    val n = 22; val spin = g.elapsed * 0.6f
                    for (k in 0 until n) { val ang = k * 6.2832f / n + spin; val r = if (k % 2 == 0) 1f else 0.72f
                        val px = tx + cos(ang) * rx * r; val py = cyb + sin(ang) * ry * r
                        if (k == 0) burst.moveTo(px, py) else burst.lineTo(px, py) }
                    burst.close()
                    shape.style = Paint.Style.STROKE; shape.strokeWidth = ts * 0.14f; shape.strokeJoin = Paint.Join.ROUND; shape.color = INK; shape.alpha = alpha; nc.drawPath(burst, shape)
                    shape.style = Paint.Style.FILL; shape.color = 0xFFFFF4B8.toInt(); shape.alpha = (alpha * 0.96f).toInt(); nc.drawPath(burst, shape)
                }
                stroke.textSize = ts; stroke.strokeWidth = ts * 0.24f; stroke.alpha = alpha
                nc.drawText(label, tx, y + ts * 0.09f, stroke)                // ombre portée (même encre, décalée)
                nc.drawText(label, tx, y, stroke)                             // contour
                val argb = (if (impact) Color(0xFFFF7A1A) else t.color).toArgb()
                val sh = gradFor(argb)
                mtx.reset(); mtx.setScale(1f, ts * 0.86f); mtx.postTranslate(0f, y - ts * 0.80f); sh.setLocalMatrix(mtx)
                fill.shader = sh; fill.alpha = alpha
                nc.drawText(label, tx, y, fill)
                fill.shader = null
                nc.restore()
            }
        }
    }
}
