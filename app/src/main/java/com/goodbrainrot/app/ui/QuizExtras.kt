package com.goodbrainrot.app.ui

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import com.goodbrainrot.app.R
import com.goodbrainrot.app.audio.Instruments
import com.goodbrainrot.app.audio.SampleEngine
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// ===========================================================================
// Contenu des quiz Drapeaux / Métiers / Instruments à l'oreille.
// Les drapeaux sont DESSINÉS (proportions et couleurs officielles simplifiées) :
// pas d'image à télécharger, et pas de risque d'erreur de détail d'une IA.
// ===========================================================================

internal val FLAGS: List<Pair<String, Int>> = listOf(
    "fr" to R.string.flag_fr, "it" to R.string.flag_it, "be" to R.string.flag_be, "ie" to R.string.flag_ie,
    "de" to R.string.flag_de, "nl" to R.string.flag_nl, "ru" to R.string.flag_ru, "at" to R.string.flag_at,
    "pl" to R.string.flag_pl, "ua" to R.string.flag_ua, "es" to R.string.flag_es, "jp" to R.string.flag_jp,
    "ch" to R.string.flag_ch, "se" to R.string.flag_se, "dk" to R.string.flag_dk, "fi" to R.string.flag_fi,
    "no" to R.string.flag_no, "gr" to R.string.flag_gr, "br" to R.string.flag_br, "cn" to R.string.flag_cn,
    "us" to R.string.flag_us, "gb" to R.string.flag_gb, "in" to R.string.flag_in, "tr" to R.string.flag_tr,
)

/** Métiers : image assets/jobs/<clé>.jpg + nom. */
internal val JOBS: List<Pair<String, Int>> = listOf(
    "firefighter" to R.string.job_firefighter, "doctor" to R.string.job_doctor, "cook" to R.string.job_cook,
    "police" to R.string.job_police, "mailcarrier" to R.string.job_mailcarrier, "farmer" to R.string.job_farmer,
    "pilot" to R.string.job_pilot, "astronaut" to R.string.job_astronaut, "vet" to R.string.job_vet,
    "builder" to R.string.job_builder, "painter" to R.string.job_painter, "baker" to R.string.job_baker,
    "dentist" to R.string.job_dentist, "fisher" to R.string.job_fisher, "gardener" to R.string.job_gardener,
    "musician" to R.string.job_musician, "teacher" to R.string.job_teacher, "hairdresser" to R.string.job_hairdresser,
)

/** Instruments reconnaissables à l'oreille (échantillons déjà embarqués pour les instruments de l'app). */
internal data class EarInstrument(val key: String, val nameRes: Int, val iconRes: Int)
internal val EAR_INSTRUMENTS = listOf(
    EarInstrument("piano", R.string.activity_piano, R.drawable.ic_piano),
    EarInstrument("xylophone", R.string.activity_xylophone, R.drawable.ic_xylophone),
    EarInstrument("guitar", R.string.activity_guitar, R.drawable.ic_guitar),
    EarInstrument("drums", R.string.inst_drums, R.drawable.ic_drum),
)

/** Joue une petite phrase musicale caractéristique de chaque instrument. */
internal object EarInstrumentPlayer {
    private val handler = Handler(Looper.getMainLooper())
    private fun midiFreq(m: Int) = 440.0 * Math.pow(2.0, (m - 69) / 12.0)

    fun preload(ctx: Context) {
        Instruments.piano.preload(ctx); Instruments.xylophone.preload(ctx); Instruments.guitarSet("acoustic").preload(ctx)
        SampleEngine.preload(ctx, "kits/hiphop")
    }

    fun stop() = handler.removeCallbacksAndMessages(null)

    fun play(ctx: Context, key: String) {
        stop(); preload(ctx)
        when (key) {
            "piano" -> melody(listOf(60, 64, 67, 72), 330L) { Instruments.piano.play(midiFreq(it)) }
            "xylophone" -> melody(listOf(72, 76, 79, 84), 280L) { Instruments.xylophone.play(midiFreq(it)) }
            "guitar" -> melody(listOf(52, 56, 59, 64), 300L) { Instruments.guitarSet("acoustic").play(midiFreq(it)) }
            "drums" -> {
                val beat = listOf("kick", "hat", "snare", "hat", "kick", "kick", "snare", "crash")
                beat.forEachIndexed { i, n -> handler.postDelayed({ SampleEngine.play("kits/hiphop", n) }, i * 230L) }
            }
        }
    }

    private fun melody(notes: List<Int>, stepMs: Long, play: (Int) -> Unit) {
        notes.forEachIndexed { i, n -> handler.postDelayed({ play(n) }, i * stepMs) }
    }
}

/** Drapeau dessiné au format 3:2, coins arrondis, centré dans [modifier]. */
@Composable
internal fun FlagView(code: String, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        var fw = size.width; var fh = fw / 1.5f
        if (fh > size.height) { fh = size.height; fw = fh * 1.5f }
        val ox = (size.width - fw) / 2f; val oy = (size.height - fh) / 2f
        val outline = Path().apply { addRoundRect(RoundRect(ox, oy, ox + fw, oy + fh, CornerRadius(fh * 0.07f))) }
        clipPath(outline) { drawFlag(code, ox, oy, fw, fh) }
        drawPath(outline, Color(0x33000000), style = Stroke(width = 3f))
    }
}

private fun DrawScope.vStripes(x: Float, y: Float, w: Float, h: Float, c1: Color, c2: Color, c3: Color) {
    val c = listOf(c1, c2, c3)
    val sw = w / c.size; c.forEachIndexed { i, col -> drawRect(col, Offset(x + i * sw, y), Size(sw + 1f, h)) }
}
private fun DrawScope.hStripes(x: Float, y: Float, w: Float, h: Float, weights: List<Float>, colors: List<Color>) {
    val total = weights.sum(); var cy = y
    for (i in colors.indices) { val sh = h * weights[i] / total; drawRect(colors[i], Offset(x, cy), Size(w, sh + 1f)); cy += sh }
}
private fun starPath(cx: Float, cy: Float, r: Float, rotDeg: Float = -90f): Path {
    val p = Path(); val ri = r * 0.382f
    for (k in 0 until 10) {
        val rr = if (k % 2 == 0) r else ri
        val a = Math.toRadians(rotDeg + k * 36.0)
        val px = cx + (rr * cos(a)).toFloat(); val py = cy + (rr * sin(a)).toFloat()
        if (k == 0) p.moveTo(px, py) else p.lineTo(px, py)
    }
    p.close(); return p
}
/** Croix nordique : barre verticale à [vx] (fraction de la largeur), barre horizontale centrée, épaisseur [t] (fraction de la hauteur). */
private fun DrawScope.nordicCross(x: Float, y: Float, w: Float, h: Float, vx: Float, t: Float, col: Color) {
    val th = h * t
    drawRect(col, Offset(x + w * vx - th / 2f, y), Size(th, h))
    drawRect(col, Offset(x, y + h / 2f - th / 2f), Size(w, th))
}

private val WHITE = Color.White
private fun DrawScope.drawFlag(code: String, x: Float, y: Float, w: Float, h: Float) {
    fun hs(a: Color, b: Color, c: Color? = null) { val cs = listOfNotNull(a, b, c); hStripes(x, y, w, h, List(cs.size) { 1f }, cs) }
    when (code) {
        "fr" -> vStripes(x, y, w, h, Color(0xFF002654), WHITE, Color(0xFFCE1126))
        "it" -> vStripes(x, y, w, h, Color(0xFF009246), WHITE, Color(0xFFCE2B37))
        "be" -> vStripes(x, y, w, h, Color(0xFF000000), Color(0xFFFAE042), Color(0xFFED2939))
        "ie" -> vStripes(x, y, w, h, Color(0xFF169B62), WHITE, Color(0xFFFF883E))
        "de" -> hs(Color(0xFF000000), Color(0xFFDD0000), Color(0xFFFFCE00))
        "nl" -> hs(Color(0xFFAE1C28), WHITE, Color(0xFF21468B))
        "ru" -> hs(WHITE, Color(0xFF0039A6), Color(0xFFD52B1E))
        "at" -> hs(Color(0xFFED2939), WHITE, Color(0xFFED2939))
        "pl" -> hs(WHITE, Color(0xFFDC143C))
        "ua" -> hs(Color(0xFF0057B7), Color(0xFFFFD700))
        "es" -> hStripes(x, y, w, h, listOf(1f, 2f, 1f), listOf(Color(0xFFAA151B), Color(0xFFF1BF00), Color(0xFFAA151B)))
        "jp" -> { drawRect(WHITE, Offset(x, y), Size(w, h)); drawCircle(Color(0xFFBC002D), h * 0.3f, Offset(x + w / 2f, y + h / 2f)) }
        "ch" -> {
            drawRect(Color(0xFFDA291C), Offset(x, y), Size(w, h))
            val s = h * 0.62f; val t = s * 0.3f; val cx = x + w / 2f; val cy = y + h / 2f
            drawRect(WHITE, Offset(cx - t / 2f, cy - s / 2f), Size(t, s)); drawRect(WHITE, Offset(cx - s / 2f, cy - t / 2f), Size(s, t))
        }
        "se" -> { drawRect(Color(0xFF006AA7), Offset(x, y), Size(w, h)); nordicCross(x, y, w, h, 0.375f, 0.2f, Color(0xFFFECC00)) }
        "dk" -> { drawRect(Color(0xFFC8102E), Offset(x, y), Size(w, h)); nordicCross(x, y, w, h, 0.38f, 0.143f, WHITE) }
        "fi" -> { drawRect(WHITE, Offset(x, y), Size(w, h)); nordicCross(x, y, w, h, 0.36f, 0.273f, Color(0xFF002F6C)) }
        "no" -> {
            drawRect(Color(0xFFBA0C2F), Offset(x, y), Size(w, h))
            nordicCross(x, y, w, h, 0.364f, 0.25f, WHITE); nordicCross(x, y, w, h, 0.364f, 0.125f, Color(0xFF00205B))
        }
        "gr" -> {
            val blue = Color(0xFF0D5EAF)
            hStripes(x, y, w, h, List(9) { 1f }, List(9) { if (it % 2 == 0) blue else WHITE })
            val cs = h * 5f / 9f; val st = h / 9f
            drawRect(blue, Offset(x, y), Size(cs, cs))
            drawRect(WHITE, Offset(x + cs / 2f - st / 2f, y), Size(st, cs)); drawRect(WHITE, Offset(x, y + cs / 2f - st / 2f), Size(cs, st))
        }
        "br" -> {
            drawRect(Color(0xFF009C3B), Offset(x, y), Size(w, h))
            val d = Path().apply { moveTo(x + w * 0.085f, y + h / 2f); lineTo(x + w / 2f, y + h * 0.12f); lineTo(x + w * 0.915f, y + h / 2f); lineTo(x + w / 2f, y + h * 0.88f); close() }
            drawPath(d, Color(0xFFFFDF00))
            val c = Offset(x + w / 2f, y + h / 2f); val r = h * 0.25f
            drawCircle(Color(0xFF002776), r, c)
            // bande blanche courbe : limitée au disque bleu
            val disc = Path().apply { addOval(androidx.compose.ui.geometry.Rect(c, r)) }
            clipPath(disc) { drawArc(WHITE, 180f, 180f, false, Offset(c.x - h * 0.5f, c.y - h * 0.08f), Size(h, h), style = Stroke(h * 0.04f)) }
        }
        "cn" -> {
            drawRect(Color(0xFFEE1C25), Offset(x, y), Size(w, h))
            val u = h / 20f; val yel = Color(0xFFFFDE00)
            drawPath(starPath(x + 5 * u, y + 5 * u, 3 * u), yel)
            for ((sx, sy) in listOf(10f to 2f, 12f to 4f, 12f to 7f, 10f to 9f)) drawPath(starPath(x + sx * u, y + sy * u, u, -90f + sx * 7f), yel)
        }
        "us" -> {
            val red = Color(0xFFB22234)
            hStripes(x, y, w, h, List(13) { 1f }, List(13) { if (it % 2 == 0) red else WHITE })
            val cw = w * 0.4f; val ch = h * 7f / 13f
            drawRect(Color(0xFF3C3B6E), Offset(x, y), Size(cw, ch))
            val r = ch / 9f * 0.42f
            for (row in 0 until 9) {
                val n = if (row % 2 == 0) 6 else 5
                for (i in 0 until n) {
                    val col = if (row % 2 == 0) i * 2 + 1 else i * 2 + 2
                    drawPath(starPath(x + cw * col / 12f, y + ch * (row + 1) / 10f, r), WHITE)
                }
            }
        }
        "gb" -> {
            val blue = Color(0xFF012169); val red = Color(0xFFC8102E)
            drawRect(blue, Offset(x, y), Size(w, h))
            drawLine(WHITE, Offset(x, y), Offset(x + w, y + h), h * 0.2f); drawLine(WHITE, Offset(x + w, y), Offset(x, y + h), h * 0.2f)
            drawLine(red, Offset(x, y), Offset(x + w, y + h), h * 0.066f); drawLine(red, Offset(x + w, y), Offset(x, y + h), h * 0.066f)
            drawRect(WHITE, Offset(x + w / 2f - h / 6f, y), Size(h / 3f, h)); drawRect(WHITE, Offset(x, y + h / 2f - h / 6f), Size(w, h / 3f))
            drawRect(red, Offset(x + w / 2f - h * 0.1f, y), Size(h * 0.2f, h)); drawRect(red, Offset(x, y + h / 2f - h * 0.1f), Size(w, h * 0.2f))
        }
        "in" -> {
            hs(Color(0xFFFF9933), WHITE, Color(0xFF138808))
            val navy = Color(0xFF000080); val c = Offset(x + w / 2f, y + h / 2f); val r = h / 3f * 0.44f
            drawCircle(navy, r, c, style = Stroke(h * 0.02f)); drawCircle(navy, r * 0.18f, c)
            for (k in 0 until 24) { val a = k * PI / 12.0; drawLine(navy, c, Offset(c.x + (r * cos(a)).toFloat(), c.y + (r * sin(a)).toFloat()), h * 0.008f) }
        }
        "tr" -> {
            val red = Color(0xFFE30A17)
            drawRect(red, Offset(x, y), Size(w, h))
            drawCircle(WHITE, h * 0.25f, Offset(x + h * 0.5f, y + h / 2f))
            drawCircle(red, h * 0.2f, Offset(x + h * 0.5625f, y + h / 2f))
            drawPath(starPath(x + h * 0.86f, y + h / 2f, h * 0.125f, 180f), WHITE)
        }
        else -> drawRect(Color.LightGray, Offset(x, y), Size(w, h))
    }
}
