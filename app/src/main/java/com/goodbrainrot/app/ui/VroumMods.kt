package com.goodbrainrot.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.goodbrainrot.app.R
import com.goodbrainrot.app.Settings
import com.goodbrainrot.app.audio.SoundEngine

// ---------------------------------------------------------------------------------------------------------------------
// VARIANTES de course : CONTRE-LA-MONTRE (seul contre les temps bronze / argent / or), MIROIR (la route est inversée,
// débloqué par une coupe gagnée) et TURBO (tout va 18 % plus vite, débloqué par trois coupes gagnées).
// ---------------------------------------------------------------------------------------------------------------------

/** Variantes choisies pour la prochaine course (état de la session : rien n'est mémorisé d'un lancement à l'autre). */
internal object VroumOpts {
    var trial by mutableStateOf(false)
    var mirror by mutableStateOf(false)
    var turbo by mutableStateOf(false)
}

/** Nombre de coupes GAGNÉES (3 étoiles) à au moins une difficulté. */
internal fun vroumGoldCups(stars: Map<String, Int>): Int = CUPS.count { c -> (0..2).any { d -> (stars["cup_${c.key}_$d"] ?: 0) >= 3 } }

@Composable
private fun ModChip(icon: VIc, label: String, on: Boolean, unlocked: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(modifier.height(38.dp).alpha(if (unlocked) 1f else 0.5f).clip(shape)
        .background(if (on) Brush.verticalGradient(listOf(Color(0xFFFFD45A), Color(0xFFFF9F2E))) else Brush.verticalGradient(listOf(Color(0x33FFFFFF), Color(0x22FFFFFF))))
        .border(if (on) 2.dp else 1.dp, if (on) Color.White else Color.White.copy(alpha = 0.3f), shape)
        .clickableNoRipple { if (unlocked) { SoundEngine.playVroumUi(); onClick() } else SoundEngine.playVvTick(0.5f) }.padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        VIcon(if (unlocked) icon else VIc.LOCK, 17.dp, if (on) Color(0xFF2A2440) else Color.White)
        Box(Modifier.padding(start = 6.dp).weight(1f, fill = false)) { FitText(label, 14f, color = if (on) Color(0xFF2A2440) else Color.White, weight = FontWeight.Black) }
    }
}

/** Rangée des variantes ; [showTrial] : le contre-la-montre n'existe qu'en Course simple. */
@Composable
internal fun RaceMods(showTrial: Boolean, modifier: Modifier = Modifier) {
    val stars by Settings.vroumStars
    val gold = vroumGoldCups(stars)
    Column(modifier) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (showTrial) ModChip(VIc.CLOCK, stringResource(R.string.vroum_mod_trial), VroumOpts.trial, true, Modifier.weight(1.25f)) { VroumOpts.trial = !VroumOpts.trial }
            ModChip(VIc.MIRROR, stringResource(R.string.vroum_mod_mirror), VroumOpts.mirror, gold >= 1, Modifier.weight(1f)) { VroumOpts.mirror = !VroumOpts.mirror }
            ModChip(VIc.BOLT, stringResource(R.string.vroum_mod_turbo), VroumOpts.turbo, gold >= 3, Modifier.weight(1f)) { VroumOpts.turbo = !VroumOpts.turbo }
        }
        if (gold < 3) Text(stringResource(R.string.vroum_mod_hint), color = Color.White.copy(alpha = 0.7f), fontWeight = FontWeight.Medium, fontSize = 11.sp, textAlign = TextAlign.Center, maxLines = 1,
            modifier = Modifier.fillMaxWidth().padding(top = 3.dp), style = TextStyle(fontFamily = VROUM_FONT))
    }
}

internal fun vroumTime(t: Float): String { val s = t.coerceAtLeast(0f); val m = (s / 60f).toInt(); return "%d:%04.1f".format(java.util.Locale.US, m, s - m * 60) }
internal val MEDAL_COLORS = listOf(Color(0xFFD98A4E), Color(0xFFDDE4F0), Color(0xFFFFC94D))

/** Chrono du contre-la-montre : le temps qui tourne et la prochaine médaille encore à portée (or, puis argent, puis bronze). */
@Composable
internal fun TrialClock(g: VroumGame, frame: Int, modifier: Modifier = Modifier) {
    @Suppress("UNUSED_EXPRESSION") frame
    val t = if (g.cleared) g.finishTime else g.raceT
    val mt = g.medalTimes
    val next = (2 downTo 0).firstOrNull { t <= mt[it] }
    Row(modifier.clip(RoundedCornerShape(16.dp)).background(Color(0x88000000)).border(2.dp, (if (next != null) MEDAL_COLORS[next] else Color.White).copy(alpha = 0.75f), RoundedCornerShape(16.dp))
        .padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        VIcon(VIc.CLOCK, 20.dp)
        Column(Modifier.padding(start = 6.dp)) {
            Text(vroumTime(t), color = Color.White, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic, fontSize = 21.sp, lineHeight = 22.sp, maxLines = 1,
                style = TextStyle(fontFamily = VROUM_FONT, shadow = Shadow(Color(0xAA000000), Offset(0f, 2f), 4f)))
            // la médaille encore à portée et son temps, juste dessous
            if (next != null) Row(verticalAlignment = Alignment.CenterVertically) {
                VIcon(VIc.STAR, 11.dp, MEDAL_COLORS[next])
                Text(" " + vroumTime(mt[next]), color = MEDAL_COLORS[next], fontWeight = FontWeight.ExtraBold, fontSize = 11.sp, lineHeight = 12.sp, maxLines = 1, style = TextStyle(fontFamily = VROUM_FONT))
            }
        }
    }
}
