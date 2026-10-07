package com.goodbrainrot.app.ui

import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.goodbrainrot.app.R
import com.goodbrainrot.app.Settings
import com.goodbrainrot.app.applyBabyMode
import com.goodbrainrot.app.audio.SoundEngine
import com.goodbrainrot.app.findActivity
import com.goodbrainrot.app.ui.theme.CreepsterFamily
import com.goodbrainrot.app.ui.theme.DeepRainbow
import com.goodbrainrot.app.ui.theme.PastelRainbow
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

@Composable
fun HomeScreen(onOpenActivity: (String) -> Unit, onOpenOptions: () -> Unit) {
    val babyMode by Settings.babyMode
    val activity = LocalContext.current.findActivity()
    var showProfile by remember { mutableStateOf(false) }

    if (showProfile) ProfileDialog(onDismiss = { showProfile = false })

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        ) {
            HoldGate(
                requireHold = babyMode,
                hint = stringResource(R.string.parent_gate_hint),
                onActivate = onOpenOptions,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(48.dp)
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_gear),
                    contentDescription = null,
                    modifier = Modifier.size(34.dp)
                )
            }
            BrainHeader(modifier = Modifier.align(Alignment.Center))

            // Espace parents (partie à part) : icône à droite du titre
            HoldGate(
                requireHold = babyMode,
                hint = stringResource(R.string.parent_gate_hint),
                onActivate = { onOpenActivity(Routes.PARENTS) },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .size(48.dp)
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_chart),
                    contentDescription = null,
                    modifier = Modifier.size(34.dp)
                )
            }
        }

        ProfileBar(
            onClick = { showProfile = true },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp)
        )

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 150.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            itemsIndexed(TOP_HUBS) { index, entry ->
                MenuCard(entry, PastelRainbow[index % PastelRainbow.size]) { onOpenActivity(entry.route) }
            }
        }

        // Interrupteur baby mode (tap pour activer, appui long 3 s pour désactiver)
        HoldGate(
            requireHold = babyMode,
            hint = stringResource(R.string.baby_mode_off_hint),
            onActivate = {
                val newValue = !Settings.babyMode.value
                Settings.setBabyMode(newValue)
                activity?.applyBabyMode(newValue)
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
        ) {
            val zombie by ZombieState.active
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(if (zombie) R.drawable.zombie_brain else R.drawable.ic_baby),
                    contentDescription = null,
                    modifier = Modifier.padding(end = 6.dp).size(26.dp)
                )
                Text(
                    text = stringResource(R.string.baby_mode_label),
                    fontSize = 18.sp
                )
                Switch(
                    checked = babyMode,
                    onCheckedChange = null,
                    modifier = Modifier.padding(start = 12.dp)
                )
            }
        }

        // Numéro de version, sur l'écran de démarrage (pour vérifier la version installée).
        Text(
            text = "v${com.goodbrainrot.app.BuildConfig.VERSION_NAME}",
            fontSize = 12.sp,
            color = Color(0xFF9A9A9A),
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 6.dp)
        )
    }
}

/** Logo + titre, avec l'easter egg « zombie » au bout de 10 tapes. */
@Composable
private fun BrainHeader(modifier: Modifier = Modifier) {
    val zombie by ZombieState.active
    var taps by remember { mutableIntStateOf(0) }
    val shrink by animateFloatAsState(
        targetValue = if (zombie) 1f else (1f - taps * 0.07f).coerceAtLeast(0.25f),
        label = "shrink"
    )

    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        if (!zombie) {
            Image(
                painter = painterResource(R.drawable.logo_brain),
                contentDescription = null,
                modifier = Modifier
                    .size(56.dp)
                    .scale(shrink)
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = {
                            SoundEngine.playHit()
                            taps++
                            if (taps >= 10) ZombieState.active.value = true
                        })
                    }
            )
            Text(
                text = buildAnnotatedString {
                    "GoodBrainRot".forEachIndexed { index, char ->
                        withStyle(SpanStyle(color = DeepRainbow[index % DeepRainbow.size])) {
                            append(char)
                        }
                    }
                },
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold
            )
        } else {
            Image(
                painter = painterResource(R.drawable.zombie_brain),
                contentDescription = null,
                modifier = Modifier
                    .size(60.dp)
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = {
                            SoundEngine.playFart((0 until SoundEngine.FART_VARIANTS).random())
                        })
                    }
            )
            Text(
                text = "BrainRot",
                fontFamily = CreepsterFamily,
                fontSize = 40.sp,
                color = Color(0xFF8FB36B)
            )
            // Numéro de version, gris, discret, sous le titre
            Text(
                text = "v${com.goodbrainrot.app.BuildConfig.VERSION_NAME}",
                fontSize = 11.sp,
                color = Color(0xFF9A9A9A)
            )
        }
    }
}

/**
 * Zone cliquable avec « parent gate » optionnel : si [requireHold] est vrai,
 * il faut maintenir l'appui 3 secondes pour déclencher [onActivate] ; un tap
 * court affiche seulement [hint]. Sinon, simple tap.
 */
@Composable
fun HoldGate(
    requireHold: Boolean,
    hint: String,
    onActivate: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    Box(
        modifier = modifier.pointerInput(requireHold) {
            detectTapGestures(
                onPress = {
                    if (requireHold) {
                        var heldLongEnough = false
                        try {
                            withTimeout(3000) { tryAwaitRelease() }
                        } catch (e: TimeoutCancellationException) {
                            heldLongEnough = true
                        }
                        if (heldLongEnough) onActivate()
                        else Toast.makeText(context, hint, Toast.LENGTH_SHORT).show()
                    }
                },
                onTap = { if (!requireHold) onActivate() }
            )
        },
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}
