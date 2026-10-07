package com.goodbrainrot.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.goodbrainrot.app.Haptics
import com.goodbrainrot.app.audio.DrumKind
import com.goodbrainrot.app.audio.SampleEngine
import com.goodbrainrot.app.audio.SoundEngine

private data class Pad(val emoji: String, val sample: String, val kind: DrumKind, val color: Color)

private val PadColorsNeon = listOf(
    Color(0xFFFF5DA2), Color(0xFF49E0FF), Color(0xFF6BFF8F), Color(0xFFFFE94A)
)

private fun c(i: Int) = PadColorsNeon[i % PadColorsNeon.size]

// 20 pads (5x4) — kit hip-hop (boom-bap / 808)
private fun hiphopPads() = listOf(
    Pad("🥁", "kick", DrumKind.KICK, c(0)), Pad("👏", "clap", DrumKind.CLAP, c(1)),
    Pad("🎩", "hat", DrumKind.HAT, c(2)), Pad("🔓", "openhat", DrumKind.OPENHAT, c(3)),
    Pad("🥁", "snare", DrumKind.SNARE, c(1)), Pad("🟫", "rim", DrumKind.RIM, c(2)),
    Pad("🛢️", "tom", DrumKind.TOM, c(3)), Pad("🔔", "cowbell", DrumKind.COWBELL, c(0)),
    Pad("🔊", "808", DrumKind.BASS, c(2)), Pad("🥁", "kick", DrumKind.KICK, c(3)),
    Pad("👏", "clap", DrumKind.CLAP, c(0)), Pad("🎩", "hat", DrumKind.HAT, c(1)),
    Pad("🎹", "stab", DrumKind.STAB, c(3)), Pad("✨", "blip", DrumKind.BLIP, c(0)),
    Pad("🥽", "ride", DrumKind.RIDE, c(1)), Pad("💥", "crash", DrumKind.CRASH, c(2)),
    Pad("🎶", "shaker", DrumKind.SHAKER, c(1)), Pad("🪵", "woodblock", DrumKind.WOODBLOCK, c(2)),
    Pad("🥢", "claves", DrumKind.CLAVES, c(3)), Pad("🔓", "openhat", DrumKind.OPENHAT, c(0))
)

// 20 pads — kit electro / techno
private fun electroPads() = listOf(
    Pad("🥁", "kick", DrumKind.KICK, c(1)), Pad("👏", "clap", DrumKind.CLAP, c(2)),
    Pad("🎩", "hat", DrumKind.HAT, c(3)), Pad("🔓", "openhat", DrumKind.OPENHAT, c(0)),
    Pad("🔊", "808", DrumKind.BASS, c(2)), Pad("🥁", "snare", DrumKind.SNARE, c(3)),
    Pad("⚡", "zap", DrumKind.ZAP, c(0)), Pad("🌊", "sweep", DrumKind.SWEEP, c(1)),
    Pad("🔫", "laser", DrumKind.LASER, c(2)), Pad("🎹", "stab", DrumKind.STAB, c(3)),
    Pad("🔔", "cowbell", DrumKind.COWBELL, c(0)), Pad("✨", "blip", DrumKind.BLIP, c(1)),
    Pad("🥁", "kick", DrumKind.KICK, c(2)), Pad("👏", "clap", DrumKind.CLAP, c(3)),
    Pad("💥", "crash", DrumKind.CRASH, c(0)), Pad("🎩", "hat", DrumKind.HAT, c(1)),
    Pad("💿", "vinyl", DrumKind.VINYL, c(2)), Pad("⚡", "zap", DrumKind.ZAP, c(3)),
    Pad("🌀", "sweep", DrumKind.SWEEP, c(0)), Pad("🔫", "laser", DrumKind.LASER, c(1))
)

/**
 * MPC : grille 4×4 de pads remplissant tout l'écran. Joue un sample du kit s'il
 * est présent (assets/kits/<kit>/), sinon la batterie 808/909 synthétisée.
 */
@Composable
fun MpcScreen(kit: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val folder = "kits/$kit"
    val pads = remember(kit) { if (kit == "electro") electroPads() else hiphopPads() }

    androidx.compose.runtime.LaunchedEffect(kit) { SampleEngine.preload(context, folder) }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF121225))) {
        Box(modifier = Modifier.fillMaxWidth()) {
            BackButton(onBack = onBack)
        }
        // Grille 4×4 qui remplit la hauteur restante
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            for (rowStart in 0 until pads.size step 4) {
                Row(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    for (col in 0 until 4) {
                        val idx = rowStart + col
                        val pad = pads[idx]
                        PadButton(
                            pad = pad,
                            color = padColorFor(idx),
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        ) {
                            Haptics.tap(context)
                            if (!SampleEngine.play(folder, pad.sample)) {
                                SoundEngine.playDrum(pad.kind)
                            }
                        }
                    }
                }
            }
        }
    }
}

// Couleur unique par pad (20 teintes) pour différencier même les emojis répétés.
private fun padColorFor(index: Int): Color {
    val hue = (index * 360f / 20f) % 360f
    return Color.hsv(hue, 0.55f, 0.95f)
}

@Composable
private fun PadButton(pad: Pad, color: Color, modifier: Modifier = Modifier, onHit: () -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .scale(if (pressed) 0.92f else 1f)
            .clip(RoundedCornerShape(16.dp))
            .background(color.copy(alpha = if (pressed) 1f else 0.85f))
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    pressed = true
                    onHit()
                    tryAwaitRelease()
                    pressed = false
                })
            },
        contentAlignment = Alignment.Center
    ) {
        // Icône IA du sample (ic_pad_<sample>) ; fallback emoji si absente.
        val context = LocalContext.current
        val iconRes = remember(pad.sample) {
            context.resources.getIdentifier("ic_pad_${pad.sample}", "drawable", context.packageName)
        }
        if (iconRes != 0) {
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(iconRes),
                contentDescription = pad.sample,
                modifier = Modifier.fillMaxSize(0.55f)
            )
        } else {
            Text(text = pad.emoji, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        }
    }
}
