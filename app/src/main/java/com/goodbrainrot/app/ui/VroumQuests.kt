package com.goodbrainrot.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.goodbrainrot.app.R
import com.goodbrainrot.app.Settings
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

// ---------------------------------------------------------------------------------------------------------------------
// MISSIONS (trois par circuit), COFFRE surprise de fin de coupe et ALBUM d'autocollants.
// ---------------------------------------------------------------------------------------------------------------------

internal enum class VMis { PODIUM, WIN, TAKEDOWNS, CLEAN, DRAFT, BOUNCE, NEAR, ROCKET, COINS, COMBO }
internal class VMission(val kind: VMis, val n: Int)
/** pièces offertes par mission réussie */
internal const val MISSION_COINS = 30

/** Les trois missions d'un circuit : une de place, une de takedowns, une d'adresse (elle tourne d'un circuit à l'autre). */
internal fun vroumMissions(themeIdx: Int): List<VMission> = listOf(
    VMission(if (themeIdx % 4 == 3) VMis.WIN else VMis.PODIUM, 0),
    VMission(VMis.TAKEDOWNS, 30 + 8 * (themeIdx % 3)),
    listOf(VMission(VMis.COINS, 60), VMission(VMis.DRAFT, 2), VMission(VMis.BOUNCE, 3), VMission(VMis.CLEAN, 0), VMission(VMis.NEAR, 2),
        VMission(VMis.ROCKET, 0), VMission(VMis.COMBO, 60))[themeIdx % 7])

internal fun missionDone(m: VMission, g: VroumGame, place: Int): Boolean = when (m.kind) {
    VMis.PODIUM -> place in 1..3
    VMis.WIN -> place == 1
    VMis.TAKEDOWNS -> g.takedowns >= m.n
    VMis.CLEAN -> g.bumpCount == 0
    VMis.DRAFT -> g.draftCount >= m.n
    VMis.BOUNCE -> g.bestStomp >= m.n
    VMis.NEAR -> g.nearCount >= m.n
    VMis.ROCKET -> g.rocketStart
    VMis.COINS -> g.coins >= m.n
    VMis.COMBO -> g.bestCombo >= m.n
}

@Composable
internal fun missionText(m: VMission): String = when (m.kind) {
    VMis.PODIUM -> stringResource(R.string.vroum_mis_podium)
    VMis.WIN -> stringResource(R.string.vroum_mis_win)
    VMis.TAKEDOWNS -> stringResource(R.string.vroum_mis_takedowns, m.n)
    VMis.CLEAN -> stringResource(R.string.vroum_mis_clean)
    VMis.DRAFT -> stringResource(R.string.vroum_mis_draft, m.n)
    VMis.BOUNCE -> stringResource(R.string.vroum_mis_bounce, m.n)
    VMis.NEAR -> stringResource(R.string.vroum_mis_near, m.n)
    VMis.ROCKET -> stringResource(R.string.vroum_mis_rocket)
    VMis.COINS -> stringResource(R.string.vroum_mis_coins, m.n)
    VMis.COMBO -> stringResource(R.string.vroum_mis_combo, m.n)
}

/** Les trois missions du circuit, cochées ou non ; celles réussies à l'instant ([fresh]) sautent et affichent leur prime. */
@Composable
internal fun MissionList(themeIdx: Int, modifier: Modifier = Modifier, fresh: List<Int> = emptyList(), t: Float = 99f, ink: Color = Color.White) {
    val done by Settings.vroumMissions
    val key = THEMES[themeIdx.coerceIn(0, THEMES.size - 1)].key
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        vroumMissions(themeIdx).forEachIndexed { i, m ->
            val ok = "${key}_$i" in done
            val isNew = i in fresh
            val pop = if (isNew) 1f + 0.3f * (1f - abs(((t - 1.3f - i * 0.35f) / 0.25f).coerceIn(-1f, 1f))) else 1f
            Row(Modifier.height(19.dp).scale(pop), verticalAlignment = Alignment.CenterVertically) {
                if (ok) VIcon(VIc.CHECK, 15.dp) else Box(Modifier.size(15.dp).clip(CircleShape).border(2.dp, ink.copy(alpha = 0.45f), CircleShape))
                Text("  " + missionText(m), color = ink.copy(alpha = if (ok) 1f else 0.8f), fontWeight = if (ok) FontWeight.ExtraBold else FontWeight.Medium, fontSize = 13.sp, maxLines = 1,
                    style = TextStyle(fontFamily = VROUM_FONT))
                if (isNew) Text("   +$MISSION_COINS", color = if (ink == Color.White) V_GOLD else Color(0xFFB87900), fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic, fontSize = 13.sp, maxLines = 1,
                    style = TextStyle(fontFamily = VROUM_FONT))
            }
        }
    }
}

// ---- Autocollants ------------------------------------------------------------------------------------------------

/** Autocollants des coffres (noms de drawables) : bolides, public, passagers, animaux qui traversent, ressorts des circuits. */
internal fun vroumStickerPool(): List<String> =
    (VEHICLES.map { "vf_${it.key}" } + CHEER_KEYS.map { "vv_$it" } + PASSENGER_KEYS.map { "vv_$it" } +
        THEMES.flatMap { it.crossers }.filter { it.startsWith("vv_") } + THEMES.map { vroumSpringTex(it) }).distinct()
internal val VROUM_STICKER_TOTAL: Int get() = THEMES.size + CUPS.size + vroumStickerPool().size

/** Contenu d'un coffre : [sticker] = drawable de l'autocollant gagné, sinon [coins] pièces. */
internal class VChest(val sticker: String?, val coins: Int)

/** Tire le coffre de fin de coupe et l'ENCAISSE tout de suite (rien n'est perdu si on quitte l'écran). */
internal fun rollChest(): VChest {
    val have = Settings.vroumStickers.value
    val missing = vroumStickerPool().filter { "s_$it" !in have }
    return if (missing.isNotEmpty() && Random.nextFloat() < 0.75f) { val s = missing.random(); Settings.addVroumSticker("s_$s"); VChest(s, 0) }
    else { val n = listOf(80, 100, 120, 150, 200).random(); Settings.addVroumCoins(n); VChest(null, n) }
}

/** Un autocollant : l'image sur sa pastille blanche légèrement de travers ; pas encore gagné = silhouette et « ? ». */
@Composable
internal fun StickerCell(img: ImageBitmap?, owned: Boolean, modifier: Modifier = Modifier, crop: Boolean = false, tilt: Float = 0f) {
    Box(modifier.aspectRatio(1f).graphicsLayer { rotationZ = if (owned) tilt else 0f }.shadow(if (owned) 5.dp else 0.dp, RoundedCornerShape(16.dp), clip = false)
        .clip(RoundedCornerShape(16.dp)).background(if (owned) Color.White else Color(0x33FFFFFF)).padding(if (crop) 4.dp else 6.dp), contentAlignment = Alignment.Center) {
        if (img != null) Image(BitmapPainter(img), null, Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)), contentScale = if (crop) ContentScale.Crop else ContentScale.Fit,
            colorFilter = if (owned) null else ColorFilter.tint(Color(0xF0181230), BlendMode.SrcAtop))
        if (!owned) Text("?", color = Color.White.copy(alpha = 0.75f), fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic, fontSize = 22.sp, style = TextStyle(fontFamily = VROUM_FONT))
    }
}

/** ALBUM : cartes postales des circuits (3 missions réussies), trophées (coupes gagnées), surprises (coffres). */
@Composable
internal fun AlbumScreen(spr: VSprites, onBack: () -> Unit) {
    val have by Settings.vroumStickers
    val pool = remember { vroumStickerPool() }
    Box(Modifier.fillMaxSize()) {
        MenuBackdrop(spr.title, Color(0xFFFF8AC0))
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                BackButton(onBack = onBack)
                Box(Modifier.weight(1f)) { FitText(stringResource(R.string.vroum_album).uppercase(), 24f, weight = FontWeight.Black, italic = true, spacing = 1f, shadow = Shadow(Color(0x99000000), Offset(0f, 4f), 10f)) }
                Text("${have.size} / $VROUM_STICKER_TOTAL", color = V_GOLD, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic, fontSize = 18.sp, modifier = Modifier.padding(start = 8.dp),
                    style = TextStyle(fontFamily = VROUM_FONT, shadow = Shadow(Color(0x99000000), Offset(0f, 2f), 4f)))
            }
            val title: @Composable (Int, Int, Int, Int) -> Unit = { res, how, n, total ->
                Column(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(res).uppercase(), color = Color.White, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic, fontSize = 16.sp, letterSpacing = 1.sp,
                        style = TextStyle(fontFamily = VROUM_FONT, shadow = Shadow(Color(0x99000000), Offset(0f, 2f), 4f)))
                    Text("   $n / $total", color = Color.White.copy(alpha = 0.7f), fontWeight = FontWeight.Bold, fontSize = 13.sp, style = TextStyle(fontFamily = VROUM_FONT))
                }
                // comment gagner les autocollants de cette rubrique
                Text(stringResource(how), color = Color.White.copy(alpha = 0.8f), fontWeight = FontWeight.Medium, fontSize = 12.sp, style = TextStyle(fontFamily = VROUM_FONT))
                }
            }
            LazyVerticalGrid(GridCells.Adaptive(68.dp), Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item(span = { GridItemSpan(maxLineSpan) }) { title(R.string.vroum_album_circuits, R.string.vroum_album_circuits_how, THEMES.count { "c_${it.key}" in have }, THEMES.size) }
                items(THEMES.size) { i -> StickerCell(spr.cards.getOrNull(i), "c_${THEMES[i].key}" in have, crop = true, tilt = if (i % 2 == 0) -3f else 3f) }
                item(span = { GridItemSpan(maxLineSpan) }) { title(R.string.vroum_album_cups, R.string.vroum_album_cups_how, CUPS.count { "t_${it.key}" in have }, CUPS.size) }
                items(CUPS.size) { i -> StickerCell(spr.cups.getOrNull(i), "t_${CUPS[i].key}" in have, tilt = if (i % 2 == 0) 3f else -3f) }
                item(span = { GridItemSpan(maxLineSpan) }) { title(R.string.vroum_album_surprise, R.string.vroum_album_surprise_how, pool.count { "s_$it" in have }, pool.size) }
                items(pool.size) { i -> StickerCell(spr.sticker(pool[i]), "s_${pool[i]}" in have, tilt = if (i % 3 == 0) -4f else if (i % 3 == 1) 2f else 4f) }
            }
        }
    }
}

// ---- Coffre surprise ---------------------------------------------------------------------------------------------

/**
 * COFFRE de fin de coupe : il tremble, on le touche, il s'ouvre dans une gerbe de rayons et montre sa surprise
 * (un autocollant de l'album ou un tas de pièces). Un second toucher referme la scène.
 */
@Composable
internal fun ChestOverlay(spr: VSprites, chest: VChest, onOpen: () -> Unit, onDone: () -> Unit) {
    var t by remember { mutableStateOf(0f) }
    var openAt by remember { mutableStateOf(-1f) }
    LaunchedEffect(Unit) { val t0 = withFrameNanos { it }; while (true) { val now = withFrameNanos { it }; t = (now - t0) / 1e9f } }
    val k = if (openAt < 0f) 0f else ((t - openAt) / 0.45f).coerceIn(0f, 1f)
    Box(Modifier.fillMaxSize().background(Color(0xD9120C2E)).clickableNoRipple { if (openAt < 0f) { openAt = t; onOpen() } else if (t - openAt > 0.9f) onDone() }, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(250.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val s = size.minDimension; val c = Offset(s / 2f, s * 0.60f)
                    // rayons dorés qui tournent une fois le coffre ouvert
                    if (k > 0f) for (i in 0 until 12) rotate(i * 30f + t * 40f, c) {
                        drawPath(Path().apply { moveTo(c.x, c.y); lineTo(c.x - s * 0.06f, c.y - s * 0.62f * k); lineTo(c.x + s * 0.06f, c.y - s * 0.62f * k); close() }, V_GOLD.copy(alpha = 0.35f * k))
                    }
                    val shake = if (openAt < 0f) sin(t * 26f) * (if ((t % 1.6f) < 0.5f) 4f else 0f) else 0f
                    rotate(shake, c) {
                        val w = s * 0.56f; val h = s * 0.30f; val x0 = c.x - w / 2f; val y0 = c.y
                        // caisse
                        drawRoundRect(Color(0xFF7A3E12), Offset(x0, y0), Size(w, h), CornerRadius(s * 0.035f))
                        drawRoundRect(Color(0xFFB5651D), Offset(x0 + s * 0.02f, y0 + s * 0.02f), Size(w - s * 0.04f, h - s * 0.04f), CornerRadius(s * 0.025f))
                        for (bx in listOf(0.14f, 0.80f)) drawRect(V_GOLD, Offset(x0 + w * bx, y0), Size(w * 0.06f, h))
                        // intérieur lumineux quand il s'ouvre
                        if (k > 0f) drawOval(Brush.radialGradient(listOf(Color.White, V_GOLD.copy(alpha = 0f)), center = Offset(c.x, y0), radius = w * 0.6f), Offset(x0, y0 - h * 0.5f * k), Size(w, h * k))
                        // couvercle : il bascule vers l'arrière
                        val lidH = s * 0.17f
                        withTransform({ translate(0f, -lidH * 0.95f * k); scale(1f, 1f - 0.6f * k, Offset(c.x, y0)) }) {
                            val lid = Path().apply { moveTo(x0, y0); lineTo(x0, y0 - lidH * 0.55f); cubicTo(x0, y0 - lidH * 1.3f, x0 + w, y0 - lidH * 1.3f, x0 + w, y0 - lidH * 0.55f); lineTo(x0 + w, y0); close() }
                            drawPath(lid, Color(0xFF7A3E12))
                            val lid2 = Path().apply { moveTo(x0 + s * 0.02f, y0 - s * 0.01f); lineTo(x0 + s * 0.02f, y0 - lidH * 0.55f); cubicTo(x0 + s * 0.02f, y0 - lidH * 1.15f, x0 + w - s * 0.02f, y0 - lidH * 1.15f, x0 + w - s * 0.02f, y0 - lidH * 0.55f); lineTo(x0 + w - s * 0.02f, y0 - s * 0.01f); close() }
                            drawPath(lid2, Color(0xFFD07A28))
                            for (bx in listOf(0.14f, 0.80f)) drawRect(V_GOLD, Offset(x0 + w * bx, y0 - lidH * 0.98f), Size(w * 0.06f, lidH * 0.98f))
                            drawRect(V_GOLD, Offset(x0, y0 - s * 0.022f), Size(w, s * 0.022f))
                        }
                        // serrure
                        if (k < 0.5f) { drawRoundRect(V_GOLD, Offset(c.x - s * 0.045f, y0 - s * 0.03f), Size(s * 0.09f, s * 0.11f), CornerRadius(s * 0.02f)); drawCircle(Color(0xFF5A2A08), s * 0.016f, Offset(c.x, y0 + s * 0.02f)) }
                    }
                }
                // la surprise sort du coffre
                if (k > 0f) Box(Modifier.graphicsLayer { translationY = (-70f * k) * density; val sc = 0.3f + 0.7f * k + 0.05f * sin(t * 4f); scaleX = sc; scaleY = sc; alpha = k }) {
                    if (chest.sticker != null) StickerCell(spr.sticker(chest.sticker), true, Modifier.size(120.dp), tilt = -5f)
                    else Row(Modifier.clip(RoundedCornerShape(26.dp)).background(Color(0xEE1A1238)).border(3.dp, V_GOLD, RoundedCornerShape(26.dp)).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        VIcon(VIc.COIN, 40.dp)
                        Text("  +${chest.coins}", color = V_GOLD, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic, fontSize = 38.sp, style = TextStyle(fontFamily = VROUM_FONT))
                    }
                }
            }
            val label = if (openAt < 0f) stringResource(R.string.vroum_chest_tap) else if (chest.sticker != null) stringResource(R.string.vroum_chest_sticker) else stringResource(R.string.vroum_chest_coins)
            Box(Modifier.fillMaxWidth(0.86f).scale(if (openAt < 0f) 1f + 0.04f * sin(t * 5f) else 1f), contentAlignment = Alignment.Center) {
                FitText(label.uppercase(), 26f, color = if (openAt < 0f) Color.White else V_GOLD, weight = FontWeight.Black, italic = true, spacing = 1f, shadow = Shadow(Color(0xAA000000), Offset(0f, 4f), 8f))
            }
        }
    }
}
