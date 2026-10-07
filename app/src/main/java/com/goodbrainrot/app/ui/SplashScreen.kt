package com.goodbrainrot.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.goodbrainrot.app.R
import com.goodbrainrot.app.audio.SoundEngine
import com.goodbrainrot.app.ui.theme.DeepRainbow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

/**
 * Écran d'ouverture « waouh » : le cerveau surgit avec un rebond et un halo
 * arc-en-ciel pulsant, des étincelles scintillent autour, et le titre
 * s'écrit lettre par lettre — le tout accompagné d'un carillon cristallin.
 */
@Composable
fun SplashScreen(onDone: () -> Unit) {
    val title = "GoodBrainRot"
    val scale = remember { Animatable(0.25f) }
    val titleProgress = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        SoundEngine.playMagic()
        launch {
            scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = 220f))
        }
        titleProgress.animateTo(1f, tween(900, delayMillis = 380, easing = FastOutSlowInEasing))
        delay(1050)
        onDone()
    }

    val infinite = rememberInfiniteTransition(label = "splash")
    val pulse by infinite.animateFloat(
        0.94f, 1.06f,
        InfiniteRepeatableSpec(tween(1100, easing = LinearEasing), RepeatMode.Reverse), label = "pulse"
    )
    val twinkle by infinite.animateFloat(
        0f, 1f,
        InfiniteRepeatableSpec(tween(1700, easing = LinearEasing), RepeatMode.Restart), label = "twinkle"
    )
    val wiggle by infinite.animateFloat(
        -4f, 4f,
        InfiniteRepeatableSpec(tween(1500, easing = LinearEasing), RepeatMode.Reverse), label = "wiggle"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(Color(0xFFFFF6FC), Color(0xFFEFF4FF), Color(0xFFF6EEFF)))
            ),
        contentAlignment = Alignment.Center
    ) {
        // Étincelles scintillantes réparties autour du centre
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            SPARKS.forEachIndexed { i, s ->
                val ph = (twinkle + s.phase) % 1f
                val a = (sin(ph * 2f * PI.toFloat())).coerceAtLeast(0f) * s.maxAlpha
                if (a <= 0.02f) return@forEachIndexed
                val x = cx + s.dx * size.width
                val y = cy + s.dy * size.height
                val r = s.size * size.minDimension * (0.5f + 0.5f * ph)
                // petite étoile à 4 branches
                drawRect(s.color.copy(alpha = a),
                    topLeft = Offset(x - r * 0.15f, y - r), size = androidx.compose.ui.geometry.Size(r * 0.3f, r * 2f))
                drawRect(s.color.copy(alpha = a),
                    topLeft = Offset(x - r, y - r * 0.15f), size = androidx.compose.ui.geometry.Size(r * 2f, r * 0.3f))
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.Center) {
                // halo arc-en-ciel pulsant derrière le cerveau
                Box(
                    modifier = Modifier
                        .size(260.dp)
                        .scale(scale.value * pulse)
                        .graphicsLayer { alpha = 0.55f }
                        .background(
                            Brush.radialGradient(
                                listOf(Color(0x66FF5DA2), Color(0x5549E0FF), Color(0x00FFFFFF))
                            ),
                            shape = androidx.compose.foundation.shape.CircleShape
                        )
                )
                Image(
                    painter = painterResource(R.drawable.logo_brain),
                    contentDescription = null,
                    modifier = Modifier
                        .size(220.dp)
                        .scale(scale.value)
                        .rotate(wiggle * (2f - scale.value))
                )
            }
            Text(
                text = buildAnnotatedString {
                    title.forEachIndexed { index, char ->
                        withStyle(SpanStyle(color = DeepRainbow[index % DeepRainbow.size])) {
                            append(char)
                        }
                    }
                },
                fontSize = 40.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier
                    .padding(top = 10.dp)
                    .graphicsLayer {
                        // le titre apparaît en fondu + petite montée
                        val p = titleProgress.value
                        alpha = p
                        translationY = (1f - p) * 40f
                        val s = 0.85f + 0.15f * p
                        scaleX = s; scaleY = s
                    }
            )
        }
    }
}

private class Spark(val dx: Float, val dy: Float, val size: Float, val phase: Float, val maxAlpha: Float, val color: Color)

private val SPARKS = listOf(
    Spark(-0.34f, -0.22f, 0.020f, 0.0f, 0.9f, Color(0xFFFFC247)),
    Spark(0.32f, -0.26f, 0.016f, 0.3f, 0.8f, Color(0xFF49E0FF)),
    Spark(-0.28f, 0.20f, 0.014f, 0.55f, 0.8f, Color(0xFFFF5DA2)),
    Spark(0.30f, 0.24f, 0.018f, 0.15f, 0.9f, Color(0xFF6BFF8F)),
    Spark(-0.12f, -0.34f, 0.012f, 0.7f, 0.7f, Color(0xFFB46BFF)),
    Spark(0.16f, 0.34f, 0.013f, 0.45f, 0.7f, Color(0xFFFFC247)),
    Spark(0.40f, 0.02f, 0.011f, 0.85f, 0.6f, Color(0xFF49E0FF)),
    Spark(-0.40f, 0.04f, 0.011f, 0.25f, 0.6f, Color(0xFFFF5DA2))
)
