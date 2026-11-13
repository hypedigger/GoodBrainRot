package com.goodbrainrot.app.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
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
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.min
import com.goodbrainrot.app.R
import com.goodbrainrot.app.Settings
import com.goodbrainrot.app.audio.SoundEngine
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sin

// ===========================================================================
// Vroum Vrouuuuuuum — MENUS (Garage puis Course), façon jeu de console :
// un seul sujet par écran, le choix en GRAND au centre (carrousel à glisser), des boutons épais
// qui s'enfoncent, un fond sombre animé pour faire ressortir les couleurs, un gros GO en bas.
// ===========================================================================

private val M_NIGHT = Color(0xFF120C2E)
private val M_GLASS = Color(0x33FFFFFF)
private val DIFF_COLORS = listOf(V_GREEN to V_TEAL, V_CYAN to V_BLUE, V_ORANGE to V_RED)
private val TITLE_SHADOW = Shadow(Color(0x99000000), Offset(0f, 4f), 10f)

/** Fond des menus : illustration floue et assombrie (fondu quand on change), bandes diagonales qui défilent. */
@Composable
private fun MenuBackdrop(art: ImageBitmap?, glow: Color) {
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF1A1247), Color(0xFF2E1A66))))) {
        Crossfade(targetState = art, animationSpec = tween(400), label = "bg") { a ->
            if (a != null) Image(BitmapPainter(a), null, Modifier.fillMaxSize().blur(22.dp), contentScale = ContentScale.Crop, alpha = 0.6f)
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(M_NIGHT.copy(alpha = 0.78f), M_NIGHT.copy(alpha = 0.35f), M_NIGHT.copy(alpha = 0.92f)))))
        val t = rememberInfiniteTransition(label = "stripes").animateFloat(0f, 1f, infiniteRepeatable(tween(14000, easing = LinearEasing)), label = "t").value
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width; val h = size.height; val band = w * 0.22f; val shift = t * band * 2f
            var x = -h - band * 2f + shift
            while (x < w + band) {
                val p = Path().apply { moveTo(x, h); lineTo(x + band, h); lineTo(x + band + h * 0.55f, 0f); lineTo(x + h * 0.55f, 0f); close() }
                drawPath(p, Color.White.copy(alpha = 0.035f))
                x += band * 2f
            }
            drawRect(Brush.radialGradient(listOf(glow.copy(alpha = 0.28f), Color.Transparent), center = Offset(w / 2f, h * 0.42f), radius = w * 0.75f))
        }
    }
}

/** Bouton « console » : épais, avec une tranche sombre dessous ; il s'enfonce quand on appuie. */
@Composable
internal fun ChunkyButton(label: String, top: Color, bottom: Color, modifier: Modifier = Modifier, enabled: Boolean = true,
                          height: Dp = 64.dp, fontSize: TextUnit = 24.sp, onClick: () -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    val dy by animateDpAsState(if (pressed) 6.dp else 0.dp, spring(stiffness = 1200f), label = "press")
    val shape = RoundedCornerShape(24.dp)
    Box(modifier.height(height + 7.dp).alpha(if (enabled) 1f else 0.45f).pointerInput(enabled) {
        detectTapGestures(onPress = { if (enabled) { pressed = true; val ok = tryAwaitRelease(); pressed = false; if (ok) onClick() } })
    }) {
        Box(Modifier.fillMaxWidth().height(height).offset(y = 7.dp).clip(shape).background(lerpColor(bottom, Color.Black, 0.45f)))
        Box(Modifier.fillMaxWidth().height(height).offset(y = dy).clip(shape).background(Brush.verticalGradient(listOf(top, bottom)))
            .border(2.dp, Color.White.copy(alpha = 0.45f), shape), contentAlignment = Alignment.Center) {
            // reflet en haut du bouton
            Box(Modifier.fillMaxWidth(0.92f).height(height * 0.42f).align(Alignment.TopCenter).padding(top = 5.dp).clip(RoundedCornerShape(18.dp))
                .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.28f), Color.Transparent))))
            FitText(label, fontSize.value, weight = FontWeight.Black, italic = true, spacing = 1.5f, shadow = Shadow(Color(0x66000000), Offset(0f, 3f), 5f))
        }
    }
}

private fun lerpColor(a: Color, b: Color, t: Float) = Color(a.red + (b.red - a.red) * t, a.green + (b.green - a.green) * t, a.blue + (b.blue - a.blue) * t, 1f)

@Composable
private fun MenuHeader(title: String, step: Int, onBack: () -> Unit, trailing: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        BackButton(onBack = onBack)
        Column(Modifier.weight(1f)) {
            FitText(title.uppercase(), 25f, weight = FontWeight.Black, italic = true, spacing = 1f, shadow = TITLE_SHADOW)
            // étapes : ● ○  /  ● ●
            Row(Modifier.padding(top = 3.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                for (i in 1..3) Box(Modifier.size(width = if (i == step) 26.dp else 10.dp, height = 5.dp).clip(CircleShape).background(if (i <= step) V_GOLD else Color.White.copy(alpha = 0.3f)))
            }
        }
        trailing()
    }
}

/** Flèche ronde translucide (précédent / suivant du carrousel). */
@Composable
private fun ArrowButton(left: Boolean, enabled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(modifier.size(46.dp).alpha(if (enabled) 1f else 0.25f).clip(CircleShape).background(M_GLASS).border(1.5.dp, Color.White.copy(alpha = 0.5f), CircleShape)
        .clickableNoRipple { if (enabled) onClick() }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(20.dp)) {
            val w = size.width; val h = size.height
            val p = Path().apply {
                if (left) { moveTo(w * 0.66f, h * 0.12f); lineTo(w * 0.28f, h * 0.5f); lineTo(w * 0.66f, h * 0.88f) }
                else { moveTo(w * 0.34f, h * 0.12f); lineTo(w * 0.72f, h * 0.5f); lineTo(w * 0.34f, h * 0.88f) }
            }
            drawPath(p, Color.White, style = Stroke(w * 0.2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

private fun PagerState.offsetOf(page: Int): Float = abs((currentPage - page) + currentPageOffsetFraction)

// ---------------------------------------------------------------------------
// Écran 1 — GARAGE : le bolide en grand sur son podium, on glisse pour en changer
// ---------------------------------------------------------------------------

@Composable
internal fun GarageScreen(spr: VSprites, vehicle: Int, colors: List<Color>, onBack: () -> Unit, onPick: (Int) -> Unit, onNext: () -> Unit, title: String? = null) {
    val pager = rememberPagerState(initialPage = vehicle) { VEHICLES.size }
    val strip = rememberLazyListState()
    val scope = rememberCoroutineScope()
    // on entend le klaxon du bolide sur lequel on s'arrête
    LaunchedEffect(pager.settledPage) { if (pager.settledPage != vehicle) onPick(pager.settledPage) }
    LaunchedEffect(pager.currentPage) { strip.animateScrollToItem((pager.currentPage - 2).coerceAtLeast(0)) }
    val cur = pager.currentPage
    val bob = rememberInfiniteTransition(label = "bob").animateFloat(0f, 6.2832f, infiniteRepeatable(tween(2600, easing = LinearEasing)), label = "b").value

    // le bolide en grand sur son podium (carrousel) + flèches
    val hero: @Composable (Modifier) -> Unit = { m ->
        Box(m) {
            HorizontalPager(state = pager, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 56.dp)) { page ->
                val off = pager.offsetOf(page).coerceIn(0f, 1f)
                val img = spr.fronts.getOrNull(page) ?: spr.vehicles.getOrNull(page)
                BoxWithConstraints(Modifier.fillMaxSize().clickableNoRipple { if (page == pager.currentPage) onPick(page) else scope.launch { pager.animateScrollToPage(page) } },
                    contentAlignment = Alignment.Center) {
                    // carré le plus grand qui tient : limité par la largeur en portrait, par la hauteur en paysage
                    val side = if (maxWidth < maxHeight) maxWidth else maxHeight
                    // podium : disque lumineux à la couleur du bolide + ombre au sol
                    Canvas(Modifier.size(side).scale(1f - 0.25f * off).alpha(1f - 0.6f * off)) {
                        val c = Offset(size.width / 2f, size.height * 0.80f)
                        drawOval(Brush.radialGradient(listOf(colors[page].copy(alpha = 0.55f), Color.Transparent), center = c, radius = size.width * 0.52f),
                            topLeft = Offset(c.x - size.width * 0.52f, c.y - size.width * 0.13f), size = Size(size.width * 1.04f, size.width * 0.26f))
                        drawOval(Color.White.copy(alpha = 0.16f), topLeft = Offset(c.x - size.width * 0.40f, c.y - size.width * 0.075f), size = Size(size.width * 0.80f, size.width * 0.15f))
                        drawOval(Color.Black.copy(alpha = 0.35f), topLeft = Offset(c.x - size.width * 0.26f, c.y - size.width * 0.035f), size = Size(size.width * 0.52f, size.width * 0.07f))
                    }
                    if (img != null) Image(BitmapPainter(img), null, contentScale = ContentScale.Fit,
                        modifier = Modifier.size(side * 0.86f).scale(1f - 0.32f * off).alpha(1f - 0.55f * off)
                            .offset(y = if (page == cur) (sin(bob) * 5f - 8f).dp else (-8).dp))
                }
            }
            ArrowButton(true, cur > 0, Modifier.align(Alignment.CenterStart).padding(start = 10.dp)) { scope.launch { pager.animateScrollToPage(cur - 1) } }
            ArrowButton(false, cur < VEHICLES.size - 1, Modifier.align(Alignment.CenterEnd).padding(end = 10.dp)) { scope.launch { pager.animateScrollToPage(cur + 1) } }
        }
    }
    // nom du bolide, en grand, souligné à sa couleur
    val name: @Composable () -> Unit = {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            FitText(stringResource(VEHICLES[cur].nameRes).uppercase(), 32f, weight = FontWeight.Black, italic = true, spacing = 1f, shadow = TITLE_SHADOW)
            Box(Modifier.padding(top = 4.dp).size(width = 86.dp, height = 6.dp).clip(CircleShape).background(Brush.horizontalGradient(listOf(colors[cur], V_GOLD))))
            Text("${cur + 1} / ${VEHICLES.size}", color = Color.White.copy(alpha = 0.6f), fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
        }
    }
    // tous les bolides d'un coup d'œil : on tape pour y aller directement
    val thumbs: @Composable () -> Unit = {
        LazyRow(state = strip, modifier = Modifier.fillMaxWidth().padding(top = 10.dp), contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            items(VEHICLES.size) { i ->
                val sel = i == cur
                val sc by animateFloatAsState(if (sel) 1.12f else 1f, spring(dampingRatio = 0.55f), label = "thumb")
                Box(Modifier.size(62.dp).scale(sc).clip(RoundedCornerShape(18.dp))
                    .background(if (sel) Brush.verticalGradient(listOf(colors[i], lerpColor(colors[i], Color.Black, 0.35f))) else Brush.verticalGradient(listOf(M_GLASS, M_GLASS)))
                    .border(if (sel) 3.dp else 1.dp, if (sel) Color.White else Color.White.copy(alpha = 0.25f), RoundedCornerShape(18.dp))
                    .clickableNoRipple { scope.launch { pager.animateScrollToPage(i) } }, contentAlignment = Alignment.Center) {
                    (spr.fronts.getOrNull(i) ?: spr.vehicles.getOrNull(i))?.let { Image(BitmapPainter(it), null, Modifier.fillMaxSize().padding(7.dp), contentScale = ContentScale.Fit) }
                }
            }
        }
    }
    Box(Modifier.fillMaxSize()) {
        MenuBackdrop(spr.title, colors[cur])
        BoxWithConstraints(Modifier.fillMaxSize()) {
            if (maxWidth > maxHeight) Column(Modifier.fillMaxSize()) {
                // paysage : le bolide à gauche, le reste à droite
                MenuHeader(title ?: stringResource(R.string.vroum_choose), 2, onBack)
                Row(Modifier.fillMaxSize()) {
                    hero(Modifier.weight(1.1f).fillMaxHeight())
                    Column(Modifier.weight(1f).fillMaxHeight().padding(end = 8.dp), verticalArrangement = Arrangement.Center) {
                        name(); thumbs()
                        ChunkyButton(stringResource(R.string.vroum_next).uppercase() + "  ▶", V_GREEN, V_TEAL, Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, top = 16.dp, bottom = 10.dp), onClick = onNext)
                    }
                }
            } else Column(Modifier.fillMaxSize()) {
                MenuHeader(title ?: stringResource(R.string.vroum_choose), 2, onBack)
                hero(Modifier.fillMaxWidth().weight(1f))
                name(); thumbs()
                ChunkyButton(stringResource(R.string.vroum_next).uppercase() + "  ▶", V_GREEN, V_TEAL, Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, top = 16.dp, bottom = 18.dp), onClick = onNext)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Écran 2 — COURSE : mode, circuit (carrousel), difficulté, GO
// ---------------------------------------------------------------------------

/** Pictogrammes dessinés (pas d'emoji) : 0 drapeau à damier, 1 infini, 2 deux joueurs, 3 cadenas. */
private fun DrawScope.modeIcon(kind: Int, col: Color) {
    val w = size.width; val h = size.height
    when (kind) {
        0 -> {
            drawLine(col, Offset(w * 0.2f, h * 0.1f), Offset(w * 0.2f, h * 0.95f), w * 0.09f, StrokeCap.Round)
            val cw = w * 0.2f; val ch = h * 0.17f
            for (r in 0..2) for (c in 0..2) if ((r + c) % 2 == 0) drawRect(col, Offset(w * 0.27f + c * cw, h * 0.12f + r * ch), Size(cw, ch))
            drawRect(col, Offset(w * 0.27f, h * 0.12f), Size(cw * 3f, ch * 3f), style = Stroke(w * 0.04f))
        }
        1 -> {
            val s = Stroke(w * 0.12f, cap = StrokeCap.Round)
            drawCircle(col, w * 0.2f, Offset(w * 0.28f, h * 0.5f), style = s); drawCircle(col, w * 0.2f, Offset(w * 0.72f, h * 0.5f), style = s)
        }
        2 -> for (x in listOf(0.3f, 0.7f)) {
            drawCircle(col, w * 0.14f, Offset(w * x, h * 0.3f))
            drawRoundRect(col, Offset(w * (x - 0.19f), h * 0.5f), Size(w * 0.38f, h * 0.42f), CornerRadius(w * 0.16f, w * 0.16f))
        }
        4 -> {   // coupe : bol, deux anses, pied, socle
            drawArc(col, 0f, 180f, true, Offset(w * 0.24f, h * -0.22f), Size(w * 0.52f, h * 0.84f))
            drawArc(col, 90f, 180f, false, Offset(w * 0.06f, h * 0.12f), Size(w * 0.3f, h * 0.3f), style = Stroke(w * 0.08f))
            drawArc(col, -90f, 180f, false, Offset(w * 0.64f, h * 0.12f), Size(w * 0.3f, h * 0.3f), style = Stroke(w * 0.08f))
            drawRect(col, Offset(w * 0.44f, h * 0.58f), Size(w * 0.12f, h * 0.2f))
            drawRoundRect(col, Offset(w * 0.26f, h * 0.78f), Size(w * 0.48f, h * 0.16f), CornerRadius(w * 0.05f, w * 0.05f))
        }
        else -> {
            drawArc(col, 180f, 180f, false, Offset(w * 0.3f, h * 0.12f), Size(w * 0.4f, h * 0.5f), style = Stroke(w * 0.11f, cap = StrokeCap.Round))
            drawRoundRect(col, Offset(w * 0.18f, h * 0.42f), Size(w * 0.64f, h * 0.5f), CornerRadius(w * 0.1f, w * 0.1f))
        }
    }
}

@Composable
private fun ModeTab(kind: Int, label: String, selected: Boolean, locked: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val sc by animateFloatAsState(if (selected) 1f else 0.94f, spring(dampingRatio = 0.6f), label = "mode")
    val shape = RoundedCornerShape(20.dp)
    Column(modifier.scale(sc).clip(shape)
        .background(if (selected) Brush.verticalGradient(listOf(V_GOLD, V_ORANGE)) else Brush.verticalGradient(listOf(M_GLASS, Color(0x1AFFFFFF))))
        .border(if (selected) 2.5.dp else 1.dp, if (selected) Color.White else Color.White.copy(alpha = 0.22f), shape)
        .clickableNoRipple(onClick).padding(vertical = 9.dp, horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        val col = if (selected) M_NIGHT else Color.White.copy(alpha = if (locked) 0.45f else 0.92f)
        Canvas(Modifier.size(26.dp)) { modeIcon(if (locked) 3 else kind, col) }
        Text(label, color = col, fontWeight = FontWeight.Black, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
    }
}

@Composable
private fun StarRow(stars: Int, size: TextUnit, dim: Color = Color.White.copy(alpha = 0.3f)) {
    Row { for (k in 0 until 3) Text("★", color = if (k < stars) V_GOLD else dim, fontSize = size, style = TextStyle(fontFamily = VROUM_FONT, shadow = Shadow(Color(0x77000000), Offset(0f, 1.5f), 3f))) }
}

/** Bouton de difficulté : 1, 2 ou 3 barres allumées (langage universel des jeux), le nom, les étoiles déjà gagnées ici. */
@Composable
private fun DiffButton(level: Int, label: String, selected: Boolean, stars: Int, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val (c1, c2) = DIFF_COLORS[level]
    val sc by animateFloatAsState(if (selected) 1.04f else 0.96f, spring(dampingRatio = 0.6f), label = "diff")
    val shape = RoundedCornerShape(20.dp)
    Column(modifier.scale(sc).alpha(if (enabled) 1f else 0.3f).clip(shape)
        .background(if (selected) Brush.verticalGradient(listOf(c1, c2)) else Brush.verticalGradient(listOf(M_GLASS, Color(0x1AFFFFFF))))
        .border(if (selected) 2.5.dp else 1.dp, if (selected) Color.White else Color.White.copy(alpha = 0.22f), shape)
        .clickableNoRipple { if (enabled) onClick() }.padding(vertical = 9.dp, horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(Modifier.size(width = 30.dp, height = 20.dp)) {
            val bw = size.width / 4.2f
            for (i in 0..2) {
                val bh = size.height * (0.45f + 0.275f * i)
                drawRoundRect(if (i <= level) (if (selected) Color.White else c1) else Color.White.copy(alpha = 0.22f),
                    Offset(i * bw * 1.6f, size.height - bh), Size(bw, bh), CornerRadius(bw * 0.3f, bw * 0.3f))
            }
        }
        Text(label, color = Color.White, fontWeight = FontWeight.Black, fontSize = 14.sp, maxLines = 1, modifier = Modifier.padding(top = 3.dp))
        if (stars >= 0) StarRow(stars, 12.sp) else Text(" ", fontSize = 12.sp)
    }
}

/** Record du circuit pour ce mode et cette difficulté, tiré du fantôme enregistré (temps en Course, distance en Arcade). */
private fun bestLabel(themeKey: String, endless: Boolean, diff: Int): String? {
    val v = Settings.vroumGhost(if (endless) "${themeKey}_a$diff" else "${themeKey}_$diff")?.split(";")?.getOrNull(1)?.toFloatOrNull() ?: return null
    return if (endless) "${v.toInt()} m" else "%d:%04.1f".format(java.util.Locale.US, (v / 60f).toInt(), v % 60f)
}

@Composable
private fun CircuitPager(spr: VSprites, pager: PagerState, endless: Boolean, duel: Boolean, diff: Int, starsMap: Map<String, Int>, modifier: Modifier) {
    val scope = rememberCoroutineScope()
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxWidth().weight(1f), contentPadding = PaddingValues(horizontal = 44.dp), pageSpacing = 14.dp) { page ->
            val off = pager.offsetOf(page).coerceIn(0f, 1f)
            val th = THEMES[page]
            val shape = RoundedCornerShape(30.dp)
            Box(Modifier.fillMaxSize().padding(vertical = 6.dp).scale(1f - 0.08f * off).alpha(1f - 0.45f * off)
                .shadow(18.dp, shape, clip = false).clip(shape).background(Color(0xFF3B2F6B))
                .border(3.dp, if (off < 0.5f) Color.White else Color.White.copy(alpha = 0.25f), shape)
                .clickableNoRipple { if (page != pager.currentPage) scope.launch { pager.animateScrollToPage(page) } }) {
                (spr.cards.getOrNull(page) ?: spr.title)?.let { Image(BitmapPainter(it), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color(0xE6000000))))
                Column(Modifier.align(Alignment.BottomStart).padding(18.dp)) {
                    Text(stringResource(th.nameRes).uppercase(), color = Color.White, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic, fontSize = 25.sp, lineHeight = 27.sp,
                        letterSpacing = 0.5.sp, style = TextStyle(fontFamily = VROUM_FONT, shadow = TITLE_SHADOW))
                    // la spécificité du circuit, en une phrase
                    if (th.tip != 0) Box(Modifier.padding(top = 4.dp).clip(RoundedCornerShape(10.dp)).background(Color(0x99000000)).padding(horizontal = 9.dp, vertical = 3.dp)) {
                        Text(stringResource(th.tip), color = V_GOLD, fontWeight = FontWeight.Bold, fontSize = 13.sp, lineHeight = 15.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                    if (!duel) Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        StarRow(starsMap["${th.key}_${if (endless) "a" else "c"}$diff"] ?: 0, 22.sp)
                        bestLabel(th.key, endless, diff)?.let { rec ->
                            Box(Modifier.padding(start = 10.dp).clip(RoundedCornerShape(10.dp)).background(Color(0x66000000)).padding(horizontal = 9.dp, vertical = 3.dp)) {
                                Text(stringResource(R.string.vroum_best) + "  " + rec, color = V_GOLD, fontWeight = FontWeight.Black, fontSize = 13.sp, maxLines = 1)
                            }
                        }
                    }
                }
                Box(Modifier.align(Alignment.TopEnd).padding(12.dp).clip(RoundedCornerShape(12.dp)).background(Color(0x77000000)).padding(horizontal = 10.dp, vertical = 3.dp)) {
                    Text("${page + 1} / ${THEMES.size}", color = Color.White, fontWeight = FontWeight.Black, fontSize = 13.sp)
                }
            }
        }
        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (i in THEMES.indices) Box(Modifier.size(width = if (i == pager.currentPage) 18.dp else 6.dp, height = 7.dp).clip(CircleShape)
                .background(if (i == pager.currentPage) V_GOLD else Color.White.copy(alpha = 0.35f)).clickableNoRipple { scope.launch { pager.animateScrollToPage(i) } })
        }
    }
}

@Composable
private fun RaceOptions(diff: Int, endless: Boolean, duel: Boolean, themeKey: String, starsMap: Map<String, Int>, unlocked: Boolean,
                        onDiff: (Int) -> Unit, onEndless: (Boolean) -> Unit, onDuel: (Boolean) -> Unit, modifier: Modifier) {
    Column(modifier) {
        // une phrase qui dit ce qu'on va faire (hauteur fixe : rien ne saute quand on change de mode)
        Box(Modifier.fillMaxWidth().height(40.dp).padding(top = 5.dp), contentAlignment = Alignment.Center) {
            Text(if (duel) stringResource(R.string.vroum_duel_hint) else if (endless) stringResource(R.string.vroum_mode_arcade_hint)
                 else if (!unlocked) stringResource(R.string.vroum_mode_race_hint) + "  " + stringResource(R.string.vroum_endless_locked) else stringResource(R.string.vroum_mode_race_hint),
                color = Color.White.copy(alpha = 0.85f), fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 14.sp, textAlign = TextAlign.Center, maxLines = 4, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(6.dp))
        val labels = listOf(R.string.vroum_diff_easy, R.string.vroum_diff_medium, R.string.vroum_diff_hard)
        val descs = listOf(R.string.vroum_diff_easy_desc, R.string.vroum_diff_medium_desc, R.string.vroum_diff_hard_desc)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (i in 0..2) DiffButton(i, stringResource(labels[i]), i == diff && !duel, if (duel) -1 else (starsMap["${themeKey}_${if (endless) "a" else "c"}$i"] ?: -1), !duel, Modifier.weight(1f)) { onDiff(i) }
        }
        Text(if (duel) " " else stringResource(descs[diff]), color = Color.White.copy(alpha = 0.85f), fontWeight = FontWeight.Medium, fontSize = 12.sp, textAlign = TextAlign.Center, maxLines = 1,
            modifier = Modifier.fillMaxWidth().padding(top = 5.dp))
    }
}

@Composable
internal fun RaceSetupScreen(spr: VSprites, vehicle: Int, themeIdx: Int, diff: Int, endless: Boolean, duel: Boolean,
                             onDiff: (Int) -> Unit, onEndless: (Boolean) -> Unit, onDuel: (Boolean) -> Unit, onBack: () -> Unit, onPlay: (Int) -> Unit) {
    val unlocked by Settings.vroumEndlessUnlocked
    val starsMap by Settings.vroumStars
    val pager = rememberPagerState(initialPage = themeIdx.coerceIn(0, THEMES.size - 1)) { THEMES.size }
    val cur = pager.currentPage
    val pulse = rememberInfiniteTransition(label = "go").animateFloat(1f, 1.035f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "p").value
    Box(Modifier.fillMaxSize()) {
        MenuBackdrop(spr.cards.getOrNull(cur) ?: spr.title, Color(0xFF7B4DFF))
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val landscape = maxWidth > maxHeight
            val header: @Composable () -> Unit = {
                MenuHeader(stringResource(R.string.vroum_pick_level), 3, onBack) {
                    // rappel du bolide choisi : on tape dessus pour revenir au garage
                    (spr.fronts.getOrNull(vehicle) ?: spr.vehicles.getOrNull(vehicle))?.let {
                        Box(Modifier.size(50.dp).clip(CircleShape).background(M_GLASS).border(2.dp, Color.White.copy(alpha = 0.6f), CircleShape).clickableNoRipple(onBack), contentAlignment = Alignment.Center) {
                            Image(BitmapPainter(it), null, Modifier.fillMaxSize().padding(6.dp), contentScale = ContentScale.Fit)
                        }
                    }
                }
            }
            val go: @Composable (Modifier) -> Unit = { m ->
                ChunkyButton(stringResource(R.string.vroum_go), V_PINK, V_ORANGE, m.scale(pulse), height = 72.dp, fontSize = 32.sp) { onPlay(cur) }
            }
            if (landscape) Column(Modifier.fillMaxSize()) {
                header()
                Row(Modifier.fillMaxSize().padding(start = 12.dp, end = 20.dp, bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    CircuitPager(spr, pager, endless, duel, diff, starsMap, Modifier.weight(1.15f).fillMaxHeight())
                    Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Center) {
                        RaceOptions(diff, endless, duel, THEMES[cur].key, starsMap, unlocked, onDiff, onEndless, onDuel, Modifier.fillMaxWidth())
                        Spacer(Modifier.height(12.dp))
                        go(Modifier.fillMaxWidth())
                    }
                }
            } else Column(Modifier.fillMaxSize()) {
                header()
                Spacer(Modifier.height(10.dp))
                CircuitPager(spr, pager, endless, duel, diff, starsMap, Modifier.fillMaxWidth().weight(1f))
                RaceOptions(diff, endless, duel, THEMES[cur].key, starsMap, unlocked, onDiff, onEndless, onDuel, Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp))
                go(Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, top = 12.dp, bottom = 18.dp))
            }
        }
    }
}

// ===========================================================================
// MENU façon Mario Kart : 1) MODE de jeu (Championnat, Course, Arcade) puis nombre de joueurs quand le mode le
// permet · 2) bolide · 3) coupe (Championnat) ou circuit (Course, Arcade).
// CHAMPIONNAT : 5 coupes de 4 courses enchaînées ; chaque course donne 0 à 3 étoiles, la coupe une NOTE GLOBALE.
// ===========================================================================

internal enum class VMode { CUP, RACE, ARCADE }

internal class VCup(val key: String, val nameRes: Int, val themes: List<String>, val color: Color)

internal val CUPS = listOf(
    VCup("acorn", R.string.vroum_cup_acorn, listOf("forest", "night", "farm", "beach"), Color(0xFF58C46A)),
    VCup("sun", R.string.vroum_cup_sun, listOf("desert", "jungle", "dino", "volcano"), Color(0xFFFF8A3D)),
    VCup("snow", R.string.vroum_cup_snow, listOf("snow", "ice", "sky", "candy"), Color(0xFF5CC8FF)),
    VCup("neon", R.string.vroum_cup_neon, listOf("city", "tokyo", "cyber", "works"), Color(0xFFFF5DA2)),
    VCup("star", R.string.vroum_cup_star, listOf("ocean", "spooky", "space", "circuit"), Color(0xFFFFC83D)),
)

/** Indice (dans THEMES) du circuit n° [race] (0..3) de la coupe. */
internal fun cupThemeIdx(cup: VCup, race: Int): Int = THEMES.indexOfFirst { it.key == cup.themes[race] }.coerceAtLeast(0)

/** Note globale d'une coupe : la somme des étoiles des 4 courses (0..12) donne 0 à 3 étoiles. */
internal fun cupGlobalStars(sum: Int): Int = when { sum >= 11 -> 3; sum >= 8 -> 2; sum >= 4 -> 1; else -> 0 }

/** Grande carte de mode : pictogramme dessiné, nom en gros, une phrase qui dit ce qu'on va faire. */
@Composable
private fun ModeCard(kind: Int, title: String, desc: String, selected: Boolean, locked: Boolean, color: Color, modifier: Modifier, onClick: () -> Unit) {
    val sc by animateFloatAsState(if (selected) 1f else 0.95f, spring(dampingRatio = 0.6f), label = "modeCard")
    val shape = RoundedCornerShape(26.dp)
    Row(modifier.scale(sc).alpha(if (locked) 0.6f else 1f).shadow(if (selected) 16.dp else 0.dp, shape, clip = false).clip(shape)
        .background(if (selected) Brush.horizontalGradient(listOf(color, lerpColor(color, M_NIGHT, 0.45f))) else Brush.horizontalGradient(listOf(M_GLASS, Color(0x1AFFFFFF))))
        .border(if (selected) 3.dp else 1.dp, if (selected) Color.White else Color.White.copy(alpha = 0.22f), shape)
        .clickableNoRipple(onClick).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(62.dp).clip(CircleShape).background(if (selected) Color.White.copy(alpha = 0.95f) else Color.White.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(36.dp)) { modeIcon(if (locked) 3 else kind, if (selected) lerpColor(color, M_NIGHT, 0.35f) else Color.White.copy(alpha = 0.9f)) }
        }
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            FitText(title.uppercase(), 25f, weight = FontWeight.Black, italic = true, spacing = 0.5f, shadow = TITLE_SHADOW)
            Text(desc, color = Color.White.copy(alpha = 0.9f), fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 15.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Bouton « 1 joueur » / « 2 joueurs ». */
@Composable
private fun PlayersButton(n: Int, label: String, selected: Boolean, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Row(modifier.alpha(if (enabled) 1f else 0.3f).clip(shape)
        .background(if (selected) Brush.verticalGradient(listOf(V_CYAN, V_BLUE)) else Brush.verticalGradient(listOf(M_GLASS, Color(0x1AFFFFFF))))
        .border(if (selected) 2.5.dp else 1.dp, if (selected) Color.White else Color.White.copy(alpha = 0.22f), shape)
        .clickableNoRipple { if (enabled) onClick() }.padding(vertical = 11.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(width = if (n == 2) 34.dp else 20.dp, height = 24.dp)) {
            val h = size.height; val u = if (n == 2) size.width / 2f else size.width
            for (i in 0 until n) { val cx = u * (i + 0.5f)
                drawCircle(Color.White, h * 0.2f, Offset(cx, h * 0.24f))
                drawRoundRect(Color.White, Offset(cx - u * 0.36f, h * 0.5f), Size(u * 0.72f, h * 0.5f), CornerRadius(u * 0.3f, u * 0.3f)) }
        }
        Text(label, color = Color.White, fontWeight = FontWeight.Black, fontSize = 16.sp, maxLines = 1, modifier = Modifier.padding(start = 9.dp))
    }
}

@Composable
internal fun ModeSelectScreen(spr: VSprites, mode: VMode, players: Int, onMode: (VMode) -> Unit, onPlayers: (Int) -> Unit, onBack: () -> Unit, onNext: () -> Unit) {
    val unlocked by Settings.vroumEndlessUnlocked
    val glow = when (mode) { VMode.CUP -> V_GOLD; VMode.RACE -> V_PINK; VMode.ARCADE -> V_CYAN }
    Box(Modifier.fillMaxSize()) {
        MenuBackdrop(spr.title, glow)
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val landscape = maxWidth > maxHeight
            val cards: @Composable (Modifier) -> Unit = { m ->
                Column(m, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ModeCard(4, stringResource(R.string.vroum_cup), stringResource(R.string.vroum_cup_hint), mode == VMode.CUP, false, Color(0xFFFFB300), Modifier.fillMaxWidth().weight(1f)) { onMode(VMode.CUP) }
                    ModeCard(0, stringResource(R.string.vroum_race), stringResource(R.string.vroum_mode_race_hint), mode == VMode.RACE, false, V_PINK, Modifier.fillMaxWidth().weight(1f)) { onMode(VMode.RACE) }
                    ModeCard(1, stringResource(R.string.vroum_endless), if (unlocked) stringResource(R.string.vroum_mode_arcade_hint) else stringResource(R.string.vroum_endless_locked),
                        mode == VMode.ARCADE, !unlocked, Color(0xFF39A8FF), Modifier.fillMaxWidth().weight(1f)) { if (unlocked) onMode(VMode.ARCADE) else SoundEngine.playVroumUi() }
                }
            }
            val bottom: @Composable (Modifier) -> Unit = { m ->
                Column(m) {
                    // deux joueurs : seulement en Course (écran partagé)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PlayersButton(1, stringResource(R.string.vroum_players_1), players == 1 || mode != VMode.RACE, true, Modifier.weight(1f)) { onPlayers(1) }
                        PlayersButton(2, stringResource(R.string.vroum_duel), players == 2 && mode == VMode.RACE, mode == VMode.RACE, Modifier.weight(1f)) { onPlayers(2) }
                    }
                    ChunkyButton(stringResource(R.string.vroum_next).uppercase() + "  ▶", V_GREEN, V_TEAL, Modifier.fillMaxWidth().padding(top = 14.dp), onClick = onNext)
                }
            }
            if (landscape) Column(Modifier.fillMaxSize()) {
                MenuHeader(stringResource(R.string.vroum_pick_mode), 1, onBack)
                Row(Modifier.fillMaxSize().padding(start = 16.dp, end = 20.dp, bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    cards(Modifier.weight(1.2f).fillMaxHeight())
                    Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) { bottom(Modifier.fillMaxWidth()) }
                }
            } else Column(Modifier.fillMaxSize()) {
                MenuHeader(stringResource(R.string.vroum_pick_mode), 1, onBack)
                cards(Modifier.fillMaxWidth().weight(1f).padding(start = 16.dp, end = 16.dp, top = 14.dp))
                bottom(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 18.dp))
            }
        }
    }
}

/** Vignette d'un circuit dans une carte de coupe (image + nom, et ses étoiles si on en donne). */
@Composable
private fun CupRaceThumb(spr: VSprites, themeIdx: Int, n: Int, stars: Int, modifier: Modifier) {
    val shape = RoundedCornerShape(14.dp)
    Box(modifier.clip(shape).background(Color(0xFF3B2F6B)).border(1.5.dp, Color.White.copy(alpha = 0.55f), shape)) {
        spr.cards.getOrNull(themeIdx)?.let { Image(BitmapPainter(it), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.35f to Color.Transparent, 1f to Color(0xE6000000))))
        Box(Modifier.align(Alignment.TopStart).padding(5.dp).size(22.dp).clip(CircleShape).background(Color(0xCC000000)), contentAlignment = Alignment.Center) {
            Text("$n", color = Color.White, fontWeight = FontWeight.Black, fontSize = 12.sp)
        }
        Column(Modifier.align(Alignment.BottomStart).padding(horizontal = 7.dp, vertical = 5.dp)) {
            if (stars >= 0) StarRow(stars, 13.sp)
            FitText(stringResource(THEMES[themeIdx].nameRes), 12f, weight = FontWeight.Black)
        }
    }
}

@Composable
internal fun CupSelectScreen(spr: VSprites, vehicle: Int, cupIdx: Int, diff: Int, onDiff: (Int) -> Unit, onBack: () -> Unit, onPlay: (Int) -> Unit) {
    val starsMap by Settings.vroumStars
    val pager = rememberPagerState(initialPage = cupIdx.coerceIn(0, CUPS.size - 1)) { CUPS.size }
    val scope = rememberCoroutineScope()
    val cur = pager.currentPage
    val pulse = rememberInfiniteTransition(label = "goCup").animateFloat(1f, 1.035f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "p").value
    val bob = rememberInfiniteTransition(label = "cupBob").animateFloat(0f, 6.2832f, infiniteRepeatable(tween(2600, easing = LinearEasing)), label = "b").value
    Box(Modifier.fillMaxSize()) {
        MenuBackdrop(spr.cards.getOrNull(cupThemeIdx(CUPS[cur], 0)) ?: spr.title, CUPS[cur].color)
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val landscape = maxWidth > maxHeight
            val header: @Composable () -> Unit = {
                MenuHeader(stringResource(R.string.vroum_pick_cup), 3, onBack) {
                    (spr.fronts.getOrNull(vehicle) ?: spr.vehicles.getOrNull(vehicle))?.let {
                        Box(Modifier.size(50.dp).clip(CircleShape).background(M_GLASS).border(2.dp, Color.White.copy(alpha = 0.6f), CircleShape).clickableNoRipple(onBack), contentAlignment = Alignment.Center) {
                            Image(BitmapPainter(it), null, Modifier.fillMaxSize().padding(6.dp), contentScale = ContentScale.Fit)
                        }
                    }
                }
            }
            val cups: @Composable (Modifier) -> Unit = { m ->
                Column(m, horizontalAlignment = Alignment.CenterHorizontally) {
                    HorizontalPager(state = pager, modifier = Modifier.fillMaxWidth().weight(1f), contentPadding = PaddingValues(horizontal = 40.dp), pageSpacing = 14.dp) { page ->
                        val off = pager.offsetOf(page).coerceIn(0f, 1f)
                        val cup = CUPS[page]
                        val shape = RoundedCornerShape(30.dp)
                        Column(Modifier.fillMaxSize().padding(vertical = 6.dp).scale(1f - 0.08f * off).alpha(1f - 0.45f * off).shadow(18.dp, shape, clip = false).clip(shape)
                            .background(Brush.verticalGradient(listOf(lerpColor(cup.color, M_NIGHT, 0.25f), lerpColor(cup.color, M_NIGHT, 0.78f))))
                            .border(3.dp, if (off < 0.5f) Color.White else Color.White.copy(alpha = 0.25f), shape)
                            .clickableNoRipple { if (page != pager.currentPage) scope.launch { pager.animateScrollToPage(page) } }.padding(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally) {
                            // le trophée, le nom de la coupe, la note globale déjà obtenue à cette difficulté
                            Row(Modifier.fillMaxWidth().weight(0.9f), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.weight(0.8f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                                    spr.cups.getOrNull(page)?.let { Image(BitmapPainter(it), null, Modifier.fillMaxSize().offset(y = if (page == cur) (sin(bob) * 4f).dp else 0.dp), contentScale = ContentScale.Fit) }
                                }
                                Column(Modifier.weight(1.2f).padding(start = 8.dp)) {
                                    Text(stringResource(cup.nameRes).uppercase(), color = Color.White, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic, fontSize = 24.sp, lineHeight = 26.sp,
                                        maxLines = 2, style = TextStyle(fontFamily = VROUM_FONT, shadow = TITLE_SHADOW))
                                    StarRow(starsMap["cup_${cup.key}_$diff"] ?: 0, 28.sp)
                                }
                            }
                            // ses quatre courses, dans l'ordre
                            Column(Modifier.fillMaxWidth().weight(1.5f).padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                for (r in 0..1) Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    for (c in 0..1) { val n = r * 2 + c; CupRaceThumb(spr, cupThemeIdx(cup, n), n + 1, -1, Modifier.weight(1f).fillMaxHeight()) }
                                }
                            }
                        }
                    }
                    Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        for (i in CUPS.indices) Box(Modifier.size(width = if (i == cur) 20.dp else 8.dp, height = 7.dp).clip(CircleShape)
                            .background(if (i == cur) V_GOLD else Color.White.copy(alpha = 0.35f)).clickableNoRipple { scope.launch { pager.animateScrollToPage(i) } })
                    }
                }
            }
            val options: @Composable (Modifier) -> Unit = { m ->
                Column(m) {
                    val labels = listOf(R.string.vroum_diff_easy, R.string.vroum_diff_medium, R.string.vroum_diff_hard)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (i in 0..2) DiffButton(i, stringResource(labels[i]), i == diff, starsMap["cup_${CUPS[cur].key}_$i"] ?: -1, true, Modifier.weight(1f)) { onDiff(i) }
                    }
                    Text(stringResource(R.string.vroum_cup_hint), color = Color.White.copy(alpha = 0.85f), fontWeight = FontWeight.Medium, fontSize = 12.sp, textAlign = TextAlign.Center, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth().padding(top = 5.dp))
                    ChunkyButton(stringResource(R.string.vroum_go), V_PINK, V_ORANGE, Modifier.fillMaxWidth().padding(top = 10.dp).scale(pulse), height = 72.dp, fontSize = 32.sp) { onPlay(cur) }
                }
            }
            if (landscape) Column(Modifier.fillMaxSize()) {
                header()
                Row(Modifier.fillMaxSize().padding(start = 12.dp, end = 20.dp, bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    cups(Modifier.weight(1.15f).fillMaxHeight())
                    Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) { options(Modifier.fillMaxWidth()) }
                }
            } else Column(Modifier.fillMaxSize()) {
                header()
                Spacer(Modifier.height(8.dp))
                cups(Modifier.fillMaxWidth().weight(1f))
                options(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 18.dp))
            }
        }
    }
}

/** Fin de coupe : le trophée, les quatre courses avec leurs étoiles, le total, et la NOTE GLOBALE qui s'affiche en grand. */
@Composable
internal fun CupResultScreen(spr: VSprites, cupIdx: Int, stars: IntArray, scores: IntArray, onAgain: () -> Unit, onOther: () -> Unit) {
    val cup = CUPS[cupIdx]
    val sum = stars.sum(); val global = cupGlobalStars(sum)
    var t by remember { mutableStateOf(0f) }
    LaunchedEffect(Unit) { val t0 = withFrameNanos { it }; while (true) { val now = withFrameNanos { it }; t = (now - t0) / 1e9f } }
    Box(Modifier.fillMaxSize()) {
        MenuBackdrop(spr.cards.getOrNull(cupThemeIdx(cup, 3)) ?: spr.title, cup.color)
        // confettis tant que la note est bonne
        if (global > 0) Canvas(Modifier.fillMaxSize()) {
            for (i in 0 until 46) {
                val h1 = ((i * 7919 + 13) % 1000) / 1000f; val h2 = ((i * 104729 + 7) % 1000) / 1000f
                val y = ((t * (0.10f + 0.12f * h2) + h1) % 1f) * size.height
                val x = (h2 + 0.04f * sin(t * 2f + i)) * size.width
                val c = listOf(V_GOLD, V_PINK, V_CYAN, V_GREEN, V_ORANGE)[i % 5]
                rotate(t * 120f + i * 37f, Offset(x, y)) { drawRect(c, Offset(x - 7f, y - 4f), Size(14f, 8f)) }
            }
        }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val landscape = maxWidth > maxHeight
            val head: @Composable (Modifier) -> Unit = { m ->
                Column(m, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    FitText(stringResource(cup.nameRes).uppercase(), 30f, weight = FontWeight.Black, italic = true, shadow = TITLE_SHADOW)
                    spr.cups.getOrNull(cupIdx)?.let {
                        Image(BitmapPainter(it), null, Modifier.weight(1f).aspectRatio(1f, matchHeightConstraintsFirst = true).scale(0.9f + 0.1f * min(1f, t * 2f)).offset(y = (sin(t * 2.4f) * 5f).dp), contentScale = ContentScale.Fit)
                    }
                    // la note globale : trois grandes étoiles qui s'allument l'une après l'autre
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (k in 0 until 3) { val on = k < global && t > 0.7f + k * 0.45f
                            val sc = if (on) 1f + 0.5f * (1f - ((t - 0.7f - k * 0.45f) / 0.25f).coerceIn(0f, 1f)) else 0.85f
                            Text("★", color = if (on) V_GOLD else Color.White.copy(alpha = 0.25f), fontSize = 58.sp, modifier = Modifier.scale(sc),
                                style = TextStyle(fontFamily = VROUM_FONT, shadow = Shadow(Color(0x99000000), Offset(0f, 4f), 8f))) }
                    }
                    Text(stringResource(R.string.vroum_cup_total) + "  ${scores.sum()}", color = Color.White, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic, fontSize = 22.sp,
                        style = TextStyle(fontFamily = VROUM_FONT, shadow = TITLE_SHADOW))
                }
            }
            val races: @Composable (Modifier) -> Unit = { m ->
                Column(m, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (r in 0..1) Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (c in 0..1) { val n = r * 2 + c; CupRaceThumb(spr, cupThemeIdx(cup, n), n + 1, stars.getOrElse(n) { 0 }, Modifier.weight(1f).fillMaxHeight()) }
                    }
                }
            }
            val buttons: @Composable (Modifier) -> Unit = { m ->
                Column(m) {
                    ChunkyButton(stringResource(R.string.vroum_cup_again), V_GREEN, V_TEAL, Modifier.fillMaxWidth(), fontSize = 21.sp, onClick = onAgain)
                    ChunkyButton(stringResource(R.string.vroum_cup_other), Color(0xFF7B6BD6), Color(0xFF4A3D9A), Modifier.fillMaxWidth().padding(top = 10.dp), height = 54.dp, fontSize = 18.sp, onClick = onOther)
                }
            }
            if (landscape) Row(Modifier.fillMaxSize().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                head(Modifier.weight(1f).fillMaxHeight())
                Column(Modifier.weight(1f).fillMaxHeight()) { races(Modifier.fillMaxWidth().weight(1f)); buttons(Modifier.fillMaxWidth().padding(top = 12.dp)) }
            } else Column(Modifier.fillMaxSize().padding(start = 18.dp, end = 18.dp, top = 22.dp, bottom = 18.dp)) {
                head(Modifier.fillMaxWidth().weight(1.25f))
                races(Modifier.fillMaxWidth().weight(1f).padding(top = 12.dp))
                buttons(Modifier.fillMaxWidth().padding(top = 14.dp))
            }
        }
    }
}

/** Texte sur UNE ligne, jamais coupé : la taille part de [maxSp] et rétrécit tant que le texte déborde (« CHOISIS TON
 *  BOLIDE », « Résultats de la coupe ▶ »… tiennent aussi sur un petit téléphone et dans les langues longues). */
@Composable
internal fun FitText(text: String, maxSp: Float, color: Color = Color.White, weight: FontWeight = FontWeight.ExtraBold, italic: Boolean = false,
                     spacing: Float = 0f, shadow: Shadow? = null, modifier: Modifier = Modifier) {
    var sp by remember(text, maxSp) { mutableStateOf(maxSp) }
    var fits by remember(text, maxSp) { mutableStateOf(false) }
    Text(text, color = color, fontWeight = weight, fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal, fontSize = sp.sp,
        letterSpacing = (spacing * sp / maxSp).sp, maxLines = 1, softWrap = false, style = TextStyle(fontFamily = VROUM_FONT, shadow = shadow),
        modifier = modifier.alpha(if (fits) 1f else 0f),
        onTextLayout = { if (it.hasVisualOverflow && sp > maxSp * 0.5f) sp *= 0.93f else fits = true })
}
