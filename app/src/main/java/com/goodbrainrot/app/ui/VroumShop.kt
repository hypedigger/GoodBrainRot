package com.goodbrainrot.app.ui

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.goodbrainrot.app.R
import com.goodbrainrot.app.Settings
import kotlin.math.cos
import kotlin.math.sin

// ---------------------------------------------------------------------------------------------------------------------
// GARAGE À DÉBLOQUER : les pièces ramassées en course achètent des bolides, des peintures et des traînées de boost.
// Le cumul de pièces gagnées (jamais dépensé) donne le RANG de pilote, affiché sur le permis.
// ---------------------------------------------------------------------------------------------------------------------

/** Prix des bolides (0 = offert dès le départ). */
internal val VROUM_VEHICLE_PRICE = mapOf("car" to 0, "monster" to 0, "firetruck" to 0, "tractor" to 0, "moto" to 150, "excavator" to 250,
    "tank" to 350, "bear" to 450, "lion" to 600, "trex" to 800, "dragon" to 1000, "rocket" to 1300)

/** Peinture = rotation de teinte du sprite ([hue] en degrés) ; la pastille montre le résultat sur la couleur du bolide. */
internal class VPaint(val key: String, val hue: Float, val price: Int)
internal val VPAINTS = listOf(VPaint("std", 0f, 0), VPaint("p40", 40f, 100), VPaint("p95", 95f, 100), VPaint("p150", 150f, 100),
    VPaint("p205", 205f, 100), VPaint("p260", 260f, 100), VPaint("p315", 315f, 100))

/** Traînée de boost : dégradé des flammes ([rainbow] = arc-en-ciel animé). */
internal class VTrail(val key: String, val price: Int, val a: Color, val b: Color, val rainbow: Boolean = false)
internal val VTRAILS = listOf(
    VTrail("std", 0, Color(0xFF5A8CFF), Color(0xFFFFFFFF)), VTrail("fire", 150, Color(0xFFFF5A1F), Color(0xFFFFE066)), VTrail("toxic", 150, Color(0xFF39D353), Color(0xFFD8FF5A)),
    VTrail("candy", 150, Color(0xFFFF5DA2), Color(0xFFFFD1E8)), VTrail("gold", 250, Color(0xFFE8A200), Color(0xFFFFF3B0)), VTrail("rainbow", 500, Color(0xFFFF5252), Color(0xFF4FC3F7), rainbow = true))

/** Rangs de pilote : pièces gagnées au total pour y accéder. */
internal val VROUM_RANKS = listOf(0, 250, 800, 2000, 4500, 9000)
internal val VROUM_RANK_NAMES = listOf(R.string.vroum_rank_0, R.string.vroum_rank_1, R.string.vroum_rank_2, R.string.vroum_rank_3, R.string.vroum_rank_4, R.string.vroum_rank_5)
internal val VROUM_RANK_COLORS = listOf(Color(0xFF8FA3B8), Color(0xFF4FC3F7), Color(0xFF66D97A), Color(0xFFFF9052), Color(0xFFFF6FA8), Color(0xFFFFC94D))
internal fun vroumRank(total: Int): Int = VROUM_RANKS.indexOfLast { total >= it }.coerceAtLeast(0)

/** Pièces d'une course : celles ramassées + une prime de place (Course) ou de distance (Arcade). */
internal val VROUM_PLACE_COINS = intArrayOf(40, 28, 20, 14, 12, 10, 8, 6)
internal fun vroumRaceCoins(picked: Int, place: Int, endless: Boolean, dist: Int): Int =
    picked + (if (endless) dist / 150 else if (place > 0) VROUM_PLACE_COINS[(place - 1).coerceIn(0, 7)] else 0)

internal fun vroumOwnsVehicle(v: Int, owned: Set<String>): Boolean = (VROUM_VEHICLE_PRICE[VEHICLES[v].key] ?: 0) == 0 || "v_${VEHICLES[v].key}" in owned
internal fun vroumPaintOf(v: Int, look: Map<String, String>): VPaint = VPAINTS.firstOrNull { it.key == look["paint_${VEHICLES[v].key}"] } ?: VPAINTS[0]
internal fun vroumTrailIdx(look: Map<String, String>): Int = VTRAILS.indexOfFirst { it.key == look["trail"] }.coerceAtLeast(0)

/** Matrice 4×5 de rotation de teinte (même tableau pour android.graphics.ColorMatrix et pour Compose). */
internal fun hueMatrix(deg: Float): FloatArray {
    val a = Math.toRadians(deg.toDouble()); val c = cos(a).toFloat(); val s = sin(a).toFloat()
    val lr = 0.213f; val lg = 0.715f; val lb = 0.072f
    return floatArrayOf(
        lr + c * (1 - lr) + s * (-lr), lg + c * (-lg) + s * (-lg), lb + c * (-lb) + s * (1 - lb), 0f, 0f,
        lr + c * (-lr) + s * 0.143f, lg + c * (1 - lg) + s * 0.140f, lb + c * (-lb) + s * (-0.283f), 0f, 0f,
        lr + c * (-lr) + s * (-(1 - lr)), lg + c * (-lg) + s * lg, lb + c * (1 - lb) + s * lb, 0f, 0f,
        0f, 0f, 0f, 1f, 0f)
}
internal fun hueShift(col: Color, deg: Float): Color {
    if (deg == 0f) return col
    val m = hueMatrix(deg)
    fun ch(i: Int) = (m[i * 5] * col.red + m[i * 5 + 1] * col.green + m[i * 5 + 2] * col.blue).coerceIn(0f, 1f)
    return Color(ch(0), ch(1), ch(2), col.alpha)
}
internal fun paintFilter(p: VPaint): ColorFilter? = if (p.hue == 0f) null else ColorFilter.colorMatrix(ColorMatrix(hueMatrix(p.hue)))

/** Couleur dominante d'un sprite (moyenne des pixels saturés) : sert de base aux pastilles de peinture. */
internal fun dominantColor(img: androidx.compose.ui.graphics.ImageBitmap?): Color {
    if (img == null) return Color(0xFFB0B0C0)
    val b = img.asAndroidBitmap(); var r = 0f; var g = 0f; var bl = 0f; var n = 0f
    val hsv = FloatArray(3)
    var y = 0
    while (y < b.height) {
        var x = 0
        while (x < b.width) {
            val p = b.getPixel(x, y)
            if ((p ushr 24) > 200) { android.graphics.Color.colorToHSV(p, hsv); if (hsv[1] > 0.4f && hsv[2] > 0.35f) { val w = hsv[1]; r += ((p shr 16) and 255) * w; g += ((p shr 8) and 255) * w; bl += (p and 255) * w; n += w } }
            x += 5
        }
        y += 5
    }
    return if (n < 1f) Color(0xFFB0B0C0) else Color(r / n / 255f, g / n / 255f, bl / n / 255f)
}

/** Porte-monnaie : pastille « pièce + montant ». */
@Composable
internal fun CoinChip(amount: Int, modifier: Modifier = Modifier, plus: Boolean = false, dark: Boolean = true) {
    Row(modifier.clip(RoundedCornerShape(16.dp)).background(if (dark) Color(0x99000000) else Color(0x22000000)).padding(start = 6.dp, end = 11.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        VIcon(VIc.COIN, 20.dp)
        Text((if (plus) " +" else " ") + amount, color = if (dark) V_GOLD else Color(0xFFB87900), fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic, fontSize = 17.sp, maxLines = 1,
            style = TextStyle(fontFamily = VROUM_FONT))
    }
}

/**
 * PERMIS DE PILOTE : carte illustrée (bolide favori, rang, progression vers le rang suivant, pièces en poche).
 */
@Composable
internal fun LicenceCard(spr: VSprites, vehicle: Int, modifier: Modifier = Modifier, height: Dp = 74.dp) {
    val total by Settings.vroumCoinsTotal
    val coins by Settings.vroumCoins
    val look by Settings.vroumLook
    val rank = vroumRank(total)
    val col = VROUM_RANK_COLORS[rank]
    val next = VROUM_RANKS.getOrNull(rank + 1)
    val frac = if (next == null) 1f else ((total - VROUM_RANKS[rank]).toFloat() / (next - VROUM_RANKS[rank])).coerceIn(0f, 1f)
    Row(modifier.height(height).shadow(8.dp, RoundedCornerShape(20.dp), clip = false).clip(RoundedCornerShape(20.dp))
        .background(Brush.horizontalGradient(listOf(Color(0xF2FFFFFF), Color(0xE6EDE8FF)))).border(2.5.dp, col, RoundedCornerShape(20.dp)).padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically) {
        // photo d'identité : le bolide sur fond de la couleur du rang
        Box(Modifier.fillMaxHeight().width(height - 14.dp).clip(RoundedCornerShape(14.dp)).background(Brush.verticalGradient(listOf(col, col.copy(alpha = 0.55f)))), contentAlignment = Alignment.Center) {
            (spr.fronts.getOrNull(vehicle) ?: spr.vehicles.getOrNull(vehicle))?.let {
                Image(BitmapPainter(it), null, Modifier.padding(4.dp), contentScale = ContentScale.Fit, colorFilter = paintFilter(vroumPaintOf(vehicle, look)))
            }
        }
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Text(stringResource(R.string.vroum_licence).uppercase(), color = V_INK.copy(alpha = 0.5f), fontWeight = FontWeight.Black, fontSize = 9.sp, letterSpacing = 1.5.sp, maxLines = 1, style = TextStyle(fontFamily = VROUM_FONT))
            FitText(stringResource(VROUM_RANK_NAMES[rank]), 19f, color = V_INK, weight = FontWeight.Black, italic = true)
            Box(Modifier.padding(top = 3.dp).fillMaxWidth().height(7.dp).clip(CircleShape).background(Color(0x22000000))) {
                Box(Modifier.fillMaxWidth(frac.coerceAtLeast(0.03f)).fillMaxHeight().clip(CircleShape).background(Brush.horizontalGradient(listOf(col, V_GOLD))))
            }
        }
        // les cinq étoiles du rang
        Column(Modifier.padding(start = 10.dp), horizontalAlignment = Alignment.End) {
            Row { for (k in 1..5) VIcon(VIc.STAR, 11.dp, if (k <= rank) Color(0xFFE8A200) else Color(0x33000000)) }
            CoinChip(coins, Modifier.padding(top = 5.dp), dark = false)
        }
    }
}

/** Pastille de couleur du garage (peinture ou traînée) : sélectionnée = cerclée ; verrouillée = cadenas + prix dessous. */
@Composable
internal fun Swatch(brush: Brush, selected: Boolean, owned: Boolean, price: Int, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickableNoRipple(onClick)) {
        Box(Modifier.size(34.dp).clip(CircleShape).background(if (selected) Color.White else Color.White.copy(alpha = 0.25f)).padding(if (selected) 3.dp else 1.5.dp).clip(CircleShape).background(brush)
            .alpha(1f), contentAlignment = Alignment.Center) {
            if (!owned) { Box(Modifier.size(34.dp).background(Color(0x33000000))); VIcon(VIc.LOCK, 15.dp) }
        }
        Text(if (owned) " " else "$price", color = V_GOLD, fontWeight = FontWeight.Black, fontSize = 10.sp, maxLines = 1,
            style = TextStyle(fontFamily = VROUM_FONT, shadow = Shadow(Color(0xAA000000), Offset(0f, 1f), 2f)))
    }
}

/** Les deux rangées de personnalisation du garage (peintures du bolide courant, traînées de boost). */
@Composable
internal fun GarageLook(vehicle: Int, baseColor: Color, onBought: () -> Unit, onRefused: () -> Unit, modifier: Modifier = Modifier) {
    val owned by Settings.vroumOwned
    val look by Settings.vroumLook
    val coins by Settings.vroumCoins
    val paint = vroumPaintOf(vehicle, look)
    val trail = vroumTrailIdx(look)
    fun pick(key: String, price: Int, free: Boolean, apply: () -> Unit) {
        if (free || key in owned) { apply(); onBought() }
        else if (coins >= price && Settings.buyVroum(key, price)) { apply(); onBought() }
        else onRefused()
    }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            for (p in VPAINTS) { val c = hueShift(baseColor, p.hue)
                Swatch(Brush.verticalGradient(listOf(c, hueShift(baseColor.copy(red = baseColor.red * 0.6f, green = baseColor.green * 0.6f, blue = baseColor.blue * 0.6f), p.hue))),
                    p.key == paint.key, p.price == 0 || "paint_${p.key}" in owned, p.price) { pick("paint_${p.key}", p.price, p.price == 0) { Settings.setVroumLook("paint_${VEHICLES[vehicle].key}", p.key) } } }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            VTRAILS.forEachIndexed { i, t ->
                Swatch(if (t.rainbow) Brush.sweepGradient(listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)) else Brush.verticalGradient(listOf(t.b, t.a)),
                    i == trail, t.price == 0 || "trail_${t.key}" in owned, t.price) { pick("trail_${t.key}", t.price, t.price == 0) { Settings.setVroumLook("trail", t.key) } }
            }
        }
    }
}
