package com.goodbrainrot.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.goodbrainrot.app.Haptics
import com.goodbrainrot.app.Settings
import com.goodbrainrot.app.audio.SoundEngine
import com.goodbrainrot.app.data.HighScores
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private val BubblePastels = listOf(
    Color(0xFFFFB3BA), Color(0xFFFFD8B1), Color(0xFFBAFFC9),
    Color(0xFFB3D9FF), Color(0xFFD7BAFF), Color(0xFFFFBAE1)
)
private val RainbowRing = Brush.sweepGradient(
    listOf(
        Color(0xFFFF8A8A), Color(0xFFFFD27F), Color(0xFFFFF59E), Color(0xFFA8F0A8),
        Color(0xFF9AD7FF), Color(0xFFC9A8FF), Color(0xFFFF8A8A)
    )
)

// Couleurs de fond zen par niveau de difficulté (3 stops chacun)
private val BabyBgColors = Triple(Color(0xFFCDF4CD), Color(0xFFE8F9E8), Color(0xFFD4F3EA))
private val ChildBgColors = Triple(Color(0xFFCDE4F4), Color(0xFFEAF4FD), Color(0xFFD4E8FF))
private val ExpertBgColors = Triple(Color(0xFFF4CDCD), Color(0xFFFDF4EA), Color(0xFFF4D4F0))

/**
 * Éclate les bulles — version pastel zen : fond animé selon le niveau,
 * son d'éclatement doux, sélecteur de niveau, timer/score/records,
 * et grosses bulles « licorne » qui éclatent en arc-en-ciel.
 */
@Composable
fun BubblesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val controller = rememberArcadeController(
        gameKey = HighScores.Games.BUBBLES,
        durationSec = Settings.bubblesDuration.intValue,
        withLevels = true,
        speedRamp = false
    )
    var unicornBurst by remember { mutableIntStateOf(0) }

    // Fond animé zen : gradient qui pulse doucement selon le niveau
    val infiniteTransition = rememberInfiniteTransition(label = "bubbleBg")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = InfiniteRepeatableSpec(tween(5500, easing = LinearEasing), RepeatMode.Reverse),
        label = "bgPhase"
    )
    val (c1, c2, c3) = when (controller.level) {
        ArcadeLevel.BABY -> BabyBgColors
        ArcadeLevel.CHILD -> ChildBgColors
        ArcadeLevel.EXPERT -> ExpertBgColors
    }
    val animatedBg = Brush.verticalGradient(
        listOf(
            lerp(c1, c2, phase),
            lerp(c2, c3, phase * 0.65f + 0.18f),
            lerp(c3, c1, phase)
        )
    )

    ArcadeShell(
        controller = controller,
        palette = PastelPalette,
        onBack = onBack,
        backgroundBrush = animatedBg
    ) {
        ArcadePlayfield(
            controller = controller,
            movesUp = true,
            concurrent = 7,
            giantChance = 0.14f,
            onPop = { isGiant ->
                Haptics.tap(context)
                controller.pops++
                if (isGiant) {
                    SoundEngine.playMagic()
                    controller.addPoints(5)
                    unicornBurst++
                } else {
                    SoundEngine.playBubblePop()
                    controller.addPoints(1)
                }
            }
        ) { _, variant, _, isGiant ->
            if (isGiant) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(RainbowRing, alpha = 0.55f)
                            .border(4.dp, Color.White.copy(alpha = 0.9f), CircleShape)
                    )
                    Text("🦄", fontSize = 40.sp)
                }
            } else {
                val c = BubblePastels[variant % BubblePastels.size]
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                listOf(Color.White.copy(alpha = 0.6f), c.copy(alpha = 0.45f), c.copy(alpha = 0.2f))
                            )
                        )
                        .border(3.dp, Color.White.copy(alpha = 0.85f), CircleShape)
                )
            }
        }

        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            UnicornBurst(trigger = unicornBurst)
        }
    }
}

/** Pluie d'étoiles — version néon arcade : chute accélérée, niveaux, records, super étoile. */
@Composable
fun StarsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    // Étoiles IA validées (cs_star_1..N) — remplacent les étoiles procédurales, tailles variées.
    val starRes = remember {
        (1..40).mapNotNull { i ->
            val id = context.resources.getIdentifier("cs_star_$i", "drawable", context.packageName)
            if (id != 0) id else null
        }
    }
    val controller = rememberArcadeController(
        gameKey = HighScores.Games.STARS,
        durationSec = Settings.starsDuration.intValue,
        withLevels = true,
        speedRamp = true
    )

    // Super étoile : 1 ou 2 apparitions par partie, dans des slots aléatoires
    var superStarSlot by remember { mutableStateOf(-1) }
    var superBurstTrigger by remember { mutableIntStateOf(0) }

    LaunchedEffect(controller.startToken) {
        if (controller.phase != ArcadePhase.PLAYING) return@LaunchedEffect
        val totalMs = controller.durationSec * 1000L
        val count = if (Random.nextBoolean()) 1 else 2
        val times = (1..count)
            .map { Random.nextLong(totalMs / 5, totalMs * 4 / 5) }
            .sorted()
        for (t in times) {
            kotlinx.coroutines.delay(t)
            if (controller.phase == ArcadePhase.PLAYING) {
                superStarSlot = Random.nextInt(0, 6)
                kotlinx.coroutines.delay(9000) // disponible 9 secondes
                if (superStarSlot >= 0) superStarSlot = -1
            }
        }
    }

    ArcadeShell(controller = controller, palette = NeonPalette, onBack = onBack) {
        ArcadePlayfield(
            controller = controller,
            movesUp = false,
            concurrent = 6,
            giantChance = 0f,
            superSlot = superStarSlot,
            sizeMul = if (starRes.isNotEmpty()) 1.9f else 1f,   // étoiles IA ~2x plus grandes
            onPop = {
                Haptics.tap(context)
                SoundEngine.playChime()
                controller.pops++
                controller.addPoints(1)
            },
            onSuperPop = {
                Haptics.success(context)
                SoundEngine.playSuperStar()
                controller.banishAll(18)
                superStarSlot = -1
                superBurstTrigger++
            }
        ) { slot, variant, _, _ ->
            val colors = listOf(NeonYellow, NeonCyan, NeonPink, NeonGreen)
            if (slot == superStarSlot) {
                SuperStarView()
            } else if (starRes.isNotEmpty()) {
                // Étoiles IA (validées) à TAILLES VARIÉES, centrées dans le slot.
                val res = starRes[(variant * 3 + slot) % starRes.size]
                val frac = 0.82f + ((variant * 7 + slot * 2) % 4) * 0.045f   // remplit bien le slot (agrandi)
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = androidx.compose.ui.Alignment.Center
                ) {
                    androidx.compose.foundation.Image(
                        painter = androidx.compose.ui.res.painterResource(res),
                        contentDescription = null,
                        contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(frac)
                    )
                }
            } else {
                ShapeView(
                    kind = ShapeKind.STAR,
                    color = colors[variant % colors.size],
                    backgroundColor = Color.Transparent,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Feu d'artifice quand on tape la super étoile
        FireworksBurst(trigger = superBurstTrigger)
    }
}

/** Étoile spéciale : grosse, arc-en-ciel scintillante, halo + contour blanc. */
@Composable
private fun SuperStarView() {
    val infiniteTransition = rememberInfiniteTransition(label = "superStar")
    val hueShift by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = InfiniteRepeatableSpec(tween(900, easing = LinearEasing), RepeatMode.Restart),
        label = "hue"
    )
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.9f, targetValue = 1.25f,
        animationSpec = InfiniteRepeatableSpec(tween(330, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulse"
    )
    val starColor = Color.hsv(hueShift % 360f, 0.9f, 1f)
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        // Halo lumineux
        Box(
            modifier = Modifier
                .fillMaxSize(pulse)
                .clip(CircleShape)
                .background(starColor.copy(alpha = 0.30f))
        )
        // Contour blanc épais
        ShapeView(
            kind = ShapeKind.STAR, color = Color.White, backgroundColor = Color.Transparent,
            modifier = Modifier.fillMaxSize(0.98f * pulse)
        )
        // Étoile colorée par-dessus
        ShapeView(
            kind = ShapeKind.STAR, color = starColor, backgroundColor = Color.Transparent,
            modifier = Modifier.fillMaxSize(0.78f * pulse)
        )
    }
}

/** Vrai feu d'artifice dessiné (sans emoji) quand la super étoile est touchée. */
@Composable
fun FireworksBurst(trigger: Int, modifier: Modifier = Modifier) {
    if (trigger == 0) return
    data class P(val angle: Float, val speed: Float, val color: Color, val radius: Float)
    val colors = listOf(NeonYellow, NeonPink, NeonCyan, NeonGreen, Color.White, Color(0xFFFFA64A))
    val parts = remember(trigger) {
        List(60) {
            P(
                angle = Random.nextFloat() * (2f * Math.PI.toFloat()),
                speed = Random.nextFloat() * 0.8f + 0.35f,
                color = colors.random(),
                radius = Random.nextFloat() * 4f + 3f
            )
        }
    }
    val progress = remember(trigger) { Animatable(0f) }
    LaunchedEffect(trigger) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(1500, easing = LinearEasing))
    }
    if (progress.value < 1f) {
        Canvas(modifier = modifier.fillMaxSize()) {
            val t = progress.value
            val cx = size.width / 2f
            val cy = size.height / 2f
            val reach = size.minDimension * 0.62f
            parts.forEach { p ->
                val r = p.speed * t * reach
                val x = cx + (cos(p.angle.toDouble()) * r).toFloat()
                val y = cy + (sin(p.angle.toDouble()) * r).toFloat() + 0.5f * t * t * size.height * 0.22f
                drawCircle(
                    color = p.color.copy(alpha = (1f - t).coerceIn(0f, 1f)),
                    radius = p.radius * (1.3f - t).coerceAtLeast(0.2f),
                    center = Offset(x, y)
                )
            }
        }
    }
}

/** Coquille commune : fond/HUD selon palette, et bascule entre les phases. */
@Composable
private fun ArcadeShell(
    controller: ArcadeController,
    palette: ArcadePalette,
    onBack: () -> Unit,
    backgroundBrush: Brush? = null,
    playfield: @Composable () -> Unit
) {
    val bg = backgroundBrush ?: palette.background
    Box(modifier = Modifier.fillMaxSize().background(bg)) {
        when (controller.phase) {
            ArcadePhase.LEVEL_SELECT -> LevelSelect(controller, palette, onBack)
            ArcadePhase.PLAYING -> {
                playfield()
                ArcadeTimer(controller)
                FlashOverlay(controller, palette)
                Column(modifier = Modifier.fillMaxSize()) {
                    ArcadeHud(controller, palette)
                }
                BackButton(onBack = onBack, modifier = Modifier.align(Alignment.TopStart))
            }
            ArcadePhase.NAME_ENTRY -> { controller.phase = ArcadePhase.GAME_OVER }   // (plus de saisie de nom)
            ArcadePhase.GAME_OVER -> GameOverOverlay(
                controller = controller,
                palette = palette,
                onReplay = {
                    if (controller.withLevels) controller.phase = ArcadePhase.LEVEL_SELECT
                    else controller.startGame(ArcadeLevel.CHILD)
                },
                onBack = onBack
            )
        }
    }
}

@Composable
private fun ArcadePlayfield(
    controller: ArcadeController,
    movesUp: Boolean,
    concurrent: Int,
    giantChance: Float,
    superSlot: Int = -1,
    sizeMul: Float = 1f,
    onPop: (isGiant: Boolean) -> Unit,
    onSuperPop: (() -> Unit)? = null,
    itemContent: @Composable (slot: Int, variant: Int, sizePx: Int, isGiant: Boolean) -> Unit
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val fieldW = maxWidth
        val fieldH = maxHeight
        repeat(concurrent) { slot ->
            ArcadeSlot(
                controller, slot, fieldW, fieldH, movesUp, giantChance,
                isSuper = slot == superSlot,
                sizeMul = sizeMul,
                onPop = onPop,
                onSuperPop = onSuperPop,
                itemContent = itemContent
            )
        }
    }
}

@Composable
private fun ArcadeSlot(
    controller: ArcadeController,
    slot: Int,
    fieldW: androidx.compose.ui.unit.Dp,
    fieldH: androidx.compose.ui.unit.Dp,
    movesUp: Boolean,
    giantChance: Float,
    isSuper: Boolean,
    sizeMul: Float = 1f,
    onPop: (isGiant: Boolean) -> Unit,
    onSuperPop: (() -> Unit)?,
    itemContent: @Composable (slot: Int, variant: Int, sizePx: Int, isGiant: Boolean) -> Unit
) {
    var token by remember { mutableIntStateOf(0) }
    var xFraction by remember { mutableStateOf(Random.nextFloat()) }
    var sizeDp by remember { mutableIntStateOf(80) }
    var variant by remember { mutableIntStateOf(0) }
    var giant by remember { mutableStateOf(false) }
    val progress = remember { Animatable(0f) }

    LaunchedEffect(token, controller.startToken, controller.phase) {
        if (controller.phase != ArcadePhase.PLAYING) return@LaunchedEffect
        if (token == 0) kotlinx.coroutines.delay(slot * 350L)
        giant = Random.nextFloat() < giantChance
        xFraction = Random.nextFloat()
        sizeDp = ((if (giant) Random.nextInt(150, 200) else Random.nextInt(64, 116)) * sizeMul).toInt()
        variant = Random.nextInt(0, 100)
        val base = Random.nextInt(4200, 7800)
        val dur = (base / controller.speedFactor()).toInt().coerceAtLeast(700)
        progress.snapTo(0f)
        progress.animateTo(1f, tween(dur, easing = LinearEasing))
        token++
    }

    // Si banishToken change, forcer la réinitialisation du slot
    LaunchedEffect(controller.banishToken) {
        if (controller.banishToken > 0 && controller.phase == ArcadePhase.PLAYING) {
            progress.snapTo(1f)
            token++
        }
    }

    if (controller.phase != ArcadePhase.PLAYING) return

    // La super étoile est nettement plus grande et bien distincte
    val effectiveSizeDp = if (isSuper) 150 else sizeDp

    val travel = fieldH + effectiveSizeDp.dp * 2
    val y = if (movesUp) fieldH - travel * progress.value
    else -effectiveSizeDp.dp + travel * progress.value

    Box(
        modifier = Modifier
            .offset(x = (fieldW - effectiveSizeDp.dp) * xFraction, y = y)
            .size(effectiveSizeDp.dp)
            .pointerInput(token, isSuper) {
                detectTapGestures(onPress = {
                    if (isSuper) onSuperPop?.invoke() else onPop(giant)
                    token++
                })
            }
    ) {
        itemContent(slot, variant, effectiveSizeDp, giant)
    }
}

/** Petite explosion d'arc-en-ciel et de licornes pour les grosses bulles. */
@Composable
fun UnicornBurst(trigger: Int, modifier: Modifier = Modifier) {
    if (trigger == 0) return
    data class P(val emoji: String, val vx: Float, val vy: Float, val size: Int)
    val parts = remember(trigger) {
        List(14) {
            val a = Random.nextDouble(0.0, 2 * Math.PI)
            val sp = Random.nextDouble(0.6, 1.1)
            P(
                listOf("🦄", "🌈", "✨", "💖", "⭐").random(),
                (cos(a) * sp).toFloat(), (sin(a) * sp - 0.5).toFloat(),
                Random.nextInt(26, 46)
            )
        }
    }
    val progress = remember(trigger) { Animatable(0f) }
    LaunchedEffect(trigger) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(1300, easing = LinearEasing))
    }
    if (progress.value < 1f) {
        Box(modifier = modifier) {
            parts.forEach { p ->
                val t = progress.value
                val x = p.vx * t * 360
                val y = p.vy * t * 360 + 300 * t * t
                Text(p.emoji, fontSize = p.size.sp,
                    modifier = Modifier.offset(x.dp, y.dp).alpha(1f - t * t))
            }
        }
    }
}
