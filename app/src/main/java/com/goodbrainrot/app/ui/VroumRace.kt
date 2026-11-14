package com.goodbrainrot.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.goodbrainrot.app.R
import kotlin.math.abs
import kotlin.math.sin

// ---------------------------------------------------------------------------------------------------------------------
// Course à POSITIONS : pastille de place en course, podium d'arrivée, tableau de classement (course et championnat).
// ---------------------------------------------------------------------------------------------------------------------

/** « 1er », « 2nd », « 3º »… : (nombre, suffixe) dans la langue de l'appli. */
internal fun vroumOrdinal(n: Int, lang: String): Pair<String, String> = when (lang) {
    "fr" -> "$n" to (if (n == 1) "er" else "e")
    "it" -> "$n" to "º"
    "es" -> "$n" to ".º"
    "ru" -> "$n" to "-й"
    else -> "$n" to (when { n % 100 in 11..13 -> "th"; n % 10 == 1 -> "st"; n % 10 == 2 -> "nd"; n % 10 == 3 -> "rd"; else -> "th" })
}

@Composable
internal fun vroumLang(): String = LocalConfiguration.current.locales[0].language

/** Couleur d'une place : or, argent, bronze, puis blanc. */
internal fun placeColor(place: Int): Color = when (place) { 1 -> V_GOLD; 2 -> Color(0xFFDDE4F0); 3 -> Color(0xFFFFA566); else -> Color.White }

/** Note d'une coupe selon le rang final au classement : vainqueur = 3 étoiles, podium = 2, sinon 1. */
internal fun cupRankStars(rank: Int): Int = when { rank <= 1 -> 3; rank <= 3 -> 2; else -> 1 }

/** Une ligne de classement : [gain] = points de la course qui vient de finir (0 = on ne l'affiche pas). */
internal class VStandRow(val veh: Int, val me: Boolean, val gain: Int, val total: Int, val rival: Boolean = false)

/**
 * Classement du championnat après une course : [pts] = totaux AVANT la course (indices 0..6 = concurrents par force,
 * 7 = joueur) ; [results] = ordre d'arrivée. Renvoie les lignes triées (le joueur gagne les égalités) et met [pts] à jour.
 */
internal fun cupStandings(pts: IntArray, cast: IntArray, vehicle: Int, results: List<VResult>?): List<VStandRow> {
    val gain = IntArray(8)
    results?.forEachIndexed { i, r -> val k = if (r.me) 7 else r.rank; if (k in 0..7) gain[k] = VROUM_POINTS.getOrElse(i) { 0 } }
    for (k in 0..7) pts[k] += gain[k]
    val rows = ArrayList<VStandRow>()
    for (k in 0 until minOf(7, cast.size)) rows.add(VStandRow(cast[k], false, gain[k], pts[k], rival = k == 0))
    rows.add(VStandRow(vehicle, true, gain[7], pts[7]))
    return rows.sortedWith(compareByDescending<VStandRow> { it.total }.thenByDescending { it.me })
}

/** Pastille de place en course : « 3e / 8 ». Elle saute et se teinte (vert = on double, rouge = on est doublé). */
@Composable
internal fun PlaceBadge(g: VroumGame, frame: Int, modifier: Modifier = Modifier) {
    @Suppress("UNUSED_EXPRESSION") frame
    val place = g.place.coerceAtLeast(1)
    val (num, suf) = vroumOrdinal(place, vroumLang())
    val pop = g.placePop
    val base = placeColor(place)
    val tint = if (g.placeDir > 0) Color(0xFF1FA85A) else Color(0xFFD63A3A)
    val sh = Shadow(Color(0xAA000000), Offset(0f, 3f), 6f)
    Row(modifier.graphicsLayer { val k = 1f + 0.42f * pop; scaleX = k; scaleY = k; transformOrigin = TransformOrigin(0f, 0f) }
        .clip(RoundedCornerShape(16.dp)).background(lerp(Color(0x88000000), tint, (pop * 0.9f).coerceIn(0f, 1f)))
        .border(2.dp, base.copy(alpha = 0.75f), RoundedCornerShape(16.dp)).padding(start = 12.dp, end = 12.dp),
        verticalAlignment = Alignment.Bottom) {
        Text(num, color = base, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic, fontSize = 34.sp, lineHeight = 40.sp, maxLines = 1, style = TextStyle(fontFamily = VROUM_FONT, shadow = sh))
        Text(suf, color = base, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic, fontSize = 17.sp, maxLines = 1, modifier = Modifier.padding(bottom = 7.dp), style = TextStyle(fontFamily = VROUM_FONT, shadow = sh))
        Text("  / ${g.fieldSize}", color = Color.White.copy(alpha = 0.75f), fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, maxLines = 1, modifier = Modifier.padding(bottom = 8.dp), style = TextStyle(fontFamily = VROUM_FONT))
    }
}

/**
 * PODIUM : les trois premiers montent sur leurs marches l'un après l'autre (3e, 2e, puis le vainqueur qui saute de joie).
 * Le bolide du joueur porte une flèche dorée. [t] = secondes depuis l'ouverture de l'écran.
 */
@Composable
internal fun Podium(spr: VSprites, results: List<VResult>, t: Float, modifier: Modifier = Modifier) {
    val slots = listOf(Triple(1, 0.66f, 0.75f), Triple(0, 1f, 1.2f), Triple(2, 0.44f, 0.3f))      // (rang, hauteur de marche, instant d'apparition)
    val steps = listOf(listOf(Color(0xFFFFE27A), Color(0xFFE8A200)), listOf(Color(0xFFF2F5FA), Color(0xFF9AA6B8)), listOf(Color(0xFFFFB98A), Color(0xFFC0652B)))
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.Bottom) {
        for ((idx, h, at) in slots) {
            val r = results.getOrNull(idx)
            val k = ((t - at) / 0.3f).coerceIn(0f, 1f)
            val joy = if (k >= 1f) abs(sin((t - at) * (if (idx == 0) 5.2f else 3f))) * (if (idx == 0) 12f else 4f) else 0f
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                if (r != null) {
                    Box(Modifier.graphicsLayer { alpha = k; translationY = (-(1f - k) * 46f - joy) * density; val s = 0.7f + 0.3f * k; scaleX = s; scaleY = s }, contentAlignment = Alignment.TopCenter) {
                        val img = spr.fronts.getOrNull(r.veh) ?: spr.vehicles.getOrNull(r.veh)
                        img?.let { Image(BitmapPainter(it), null, Modifier.padding(top = if (r.me) 12.dp else 0.dp).size(if (idx == 0) 84.dp else 68.dp), contentScale = ContentScale.Fit,
                            colorFilter = if (r.me) paintFilter(vroumPaintOf(r.veh, com.goodbrainrot.app.Settings.vroumLook.value)) else null) }
                        // flèche dorée au-dessus du joueur
                        if (r.me) Canvas(Modifier.size(22.dp, 16.dp)) {
                            val p = Path().apply { moveTo(0f, 0f); lineTo(size.width, 0f); lineTo(size.width / 2f, size.height); close() }
                            drawPath(p, Color(0x99000000)); drawPath(Path().apply { moveTo(3f, 2f); lineTo(size.width - 3f, 2f); lineTo(size.width / 2f, size.height - 4f); close() }, V_GOLD)
                        }
                    }
                }
                Box(Modifier.fillMaxWidth().height((62f * h).dp).shadow(6.dp, RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp), clip = false)
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)).background(Brush.verticalGradient(steps[idx])), contentAlignment = Alignment.Center) {
                    Text("${idx + 1}", color = Color.White, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic, fontSize = (if (idx == 0) 34 else 26).sp,
                        style = TextStyle(fontFamily = VROUM_FONT, shadow = Shadow(Color(0x77000000), Offset(0f, 3f), 5f)))
                }
            }
        }
    }
}

/**
 * Tableau de classement « verre » : place, bolide, nom, points gagnés (apparaissent après [gainAt] s) et total.
 * La ligne du joueur est surlignée ; le rival de la coupe porte une étiquette.
 */
@Composable
internal fun StandingsCard(spr: VSprites, rows: List<VStandRow>, t: Float, modifier: Modifier = Modifier, rowH: Dp = 25.dp, gainAt: Float = 1.7f) {
    Column(modifier.shadow(12.dp, RoundedCornerShape(24.dp), clip = false).clip(RoundedCornerShape(24.dp))
        .background(Brush.verticalGradient(listOf(Color(0xF7FFFFFF), Color(0xE6F4F1FF)))).padding(horizontal = 10.dp, vertical = 9.dp)) {
        rows.forEachIndexed { i, r ->
            val show = ((t - 0.15f - i * 0.07f) / 0.25f).coerceIn(0f, 1f)
            Row(Modifier.fillMaxWidth().height(rowH).graphicsLayer { alpha = show; translationX = (1f - show) * 40f * density }
                .clip(RoundedCornerShape(10.dp)).background(if (r.me) Brush.horizontalGradient(listOf(V_PINK, V_ORANGE)) else Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent)))
                .padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                val ink = if (r.me) Color.White else V_INK
                Text("${i + 1}", color = if (r.me) Color.White else if (i < 3) Color(0xFFD08A00) else V_INK.copy(alpha = 0.55f), fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic,
                    fontSize = 16.sp, maxLines = 1, modifier = Modifier.width(20.dp), style = TextStyle(fontFamily = VROUM_FONT))
                spr.vehicles.getOrNull(r.veh)?.let { Image(BitmapPainter(it), null, Modifier.padding(end = 6.dp).fillMaxHeight().width(rowH), contentScale = ContentScale.Fit,
                    colorFilter = if (r.me) paintFilter(vroumPaintOf(r.veh, com.goodbrainrot.app.Settings.vroumLook.value)) else null) }
                Box(Modifier.weight(1f)) { FitText(stringResource(VEHICLES[r.veh].nameRes), 14f, color = ink, weight = FontWeight.ExtraBold) }
                if (r.rival) Box(Modifier.padding(horizontal = 4.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFFD63A3A)).padding(horizontal = 5.dp)) {
                    Text(stringResource(R.string.vroum_rival), color = Color.White, fontWeight = FontWeight.Black, fontSize = 9.sp, maxLines = 1, style = TextStyle(fontFamily = VROUM_FONT))
                }
                if (r.gain > 0) Text("+${r.gain}", color = if (r.me) Color.White else Color(0xFF1FA85A), fontWeight = FontWeight.Black, fontSize = 13.sp, maxLines = 1,
                    modifier = Modifier.padding(end = 6.dp).graphicsLayer { alpha = ((t - gainAt) / 0.3f).coerceIn(0f, 1f) }, style = TextStyle(fontFamily = VROUM_FONT))
                Text("${r.total}", color = ink, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic, fontSize = 16.sp, maxLines = 1, style = TextStyle(fontFamily = VROUM_FONT))
            }
        }
    }
}

/** Petits repères des concurrents sur la frise d'arrivée (un point par bolide, le meneur en doré). */
@Composable
internal fun RacerDots(g: VroumGame, frame: Int) {
    @Suppress("UNUSED_EXPRESSION") frame
    // contre-la-montre : les trois allures bronze / argent / or avancent sur la frise
    if (g.trial) { val mt = g.medalTimes
        for (i in 0..2) Box(Modifier.fillMaxWidth((g.raceT / mt[i]).coerceIn(0.02f, 1f)).fillMaxHeight()) {
            Box(Modifier.align(Alignment.CenterEnd).size(9.dp).clip(RoundedCornerShape(5.dp)).background(MEDAL_COLORS[i])) } }
    for (r in g.racers) {
        val f = (r.d / VroumGame.LEVEL_LEN).coerceIn(0.02f, 1f)
        Box(Modifier.fillMaxWidth(f).fillMaxHeight()) {
            Box(Modifier.align(Alignment.CenterEnd).size(7.dp).clip(RoundedCornerShape(4.dp)).background(if (r.d > g.dist) Color(0xFFFF8A4C) else Color.White.copy(alpha = 0.75f)))
        }
    }
}
