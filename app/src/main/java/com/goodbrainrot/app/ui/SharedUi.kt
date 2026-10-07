package com.goodbrainrot.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.goodbrainrot.app.ui.theme.InkColor
import kotlinx.coroutines.launch

/**
 * Mode zombie : état global en mémoire (réinitialisé à la fermeture de l'app).
 * Activé par l'easter egg de l'icône d'accueil.
 */
object ZombieState {
    val active = mutableStateOf(false)
}

/** Clic sans halo de ripple (gardé sobre pour les jeux et icônes). */
fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier = composedClickable(onClick)

private fun Modifier.composedClickable(onClick: () -> Unit): Modifier = this.then(
    Modifier.clickable(
        interactionSource = MutableInteractionSourceHolder.source,
        indication = null,
        onClick = onClick
    )
)

private object MutableInteractionSourceHolder {
    val source = MutableInteractionSource()
}

/**
 * Flèche de retour pastel dans une pastille ronde, avec une petite animation
 * de rebond/rotation au clic. L'animation se joue mais n'attend pas avant de
 * déclencher [onBack] : la transition vers l'écran précédent reste immédiate.
 */
/**
 * Bouton retour « verre » façon iOS : pastille translucide (blanc laiteux + liseré clair + ombre douce),
 * chevron fin arrondi, ~40 dp (15-20 % plus petit que l'ancien). Utilisé PARTOUT dans l'app.
 */
@Composable
fun BackButton(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val scale = remember { Animatable(1f) }
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp)
    Box(
        modifier = modifier
            .padding(10.dp)
            .size(40.dp)
            .scale(scale.value)
            .shadow(6.dp, shape, clip = false, ambientColor = Color(0x33000000), spotColor = Color(0x44000000))
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xB8FFFFFF), Color(0x8CFFFFFF))))
            .border(1.dp, Color(0xCCFFFFFF), shape)
            .clickableNoRipple {
                onBack() // navigation immédiate
                scope.launch { scale.snapTo(0.86f); scale.animateTo(1f) }
            },
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.foundation.Canvas(modifier = Modifier.size(18.dp)) {
            val w = size.width; val h = size.height
            val p = androidx.compose.ui.graphics.Path()
            p.moveTo(w * 0.66f, h * 0.12f); p.lineTo(w * 0.30f, h * 0.5f); p.lineTo(w * 0.66f, h * 0.88f)
            drawPath(p, InkColor.copy(alpha = 0.9f), style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = w * 0.17f, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
        }
    }
}
