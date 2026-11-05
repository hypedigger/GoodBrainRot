package com.goodbrainrot.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.goodbrainrot.app.R
import com.goodbrainrot.app.Settings
import com.goodbrainrot.app.data.HighScores
import com.goodbrainrot.app.ui.theme.InkColor

// ===========================================================================
// Mur des trophées : étoiles de chaque circuit Vroum (Course / Arcade × 3 difficultés),
// records datés de chaque jeu, plus belles tailles de Topiaire (meilleure précision par forme).
// ===========================================================================

private val T_GOLD = Color(0xFFFFB300)
private val T_CARD = Color(0xF2FFFFFF)
private val DIFF_GLYPHS = listOf("🙂", "😎", "🔥")
private val SHAPE_GLYPHS = listOf("⚪", "🟩", "🔺", "🐰", "⭐", "❤️", "🦆")

@Composable
fun TrophiesScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val stars by Settings.vroumStars
    val prec by Settings.gardenPrecision
    @Suppress("UNUSED_EXPRESSION") HighScores.version.value
    val totalStars = stars.values.sum()
    val maxStars = THEMES.size * 2 * 3 * 3
    Column(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFFFFF4C9), Color(0xFFFFDDEB), Color(0xFFDCD6FF))))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BackButton(onBack = onBack)
            Image(painterResource(R.drawable.ic_trophy), contentDescription = null, modifier = Modifier.size(46.dp))
            Text(stringResource(R.string.trophies_title), color = InkColor, fontWeight = FontWeight.ExtraBold, fontSize = 28.sp, modifier = Modifier.padding(start = 8.dp))
        }
        // compteur géant d'étoiles
        Box(Modifier.fillMaxWidth().padding(top = 10.dp).shadow(6.dp, RoundedCornerShape(24.dp), clip = false).clip(RoundedCornerShape(24.dp))
            .background(Brush.horizontalGradient(listOf(Color(0xFFFFD54F), Color(0xFFFF9F43)))).padding(vertical = 14.dp), contentAlignment = Alignment.Center) {
            Text("★ $totalStars / $maxStars", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 32.sp)
        }

        SectionTitle(stringResource(R.string.trophies_stars))
        for (t in THEMES) {
            val cardId = remember(t.card) { ctx.resources.getIdentifier(t.card, "drawable", ctx.packageName) }
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp).clip(RoundedCornerShape(20.dp)).background(T_CARD).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                if (cardId != 0) Image(painterResource(cardId), contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.size(width = 74.dp, height = 56.dp).clip(RoundedCornerShape(14.dp)))
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Text(stringResource(t.nameRes), color = InkColor, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 4.dp)) {
                        for ((mode, label) in listOf("c" to R.string.vroum_race, "a" to R.string.vroum_endless)) {
                            Column {
                                Text(stringResource(label), color = InkColor.copy(alpha = 0.6f), fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                for (d in 0..2) {
                                    val s = stars["${t.key}_$mode$d"] ?: 0
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(DIFF_GLYPHS[d], fontSize = 10.sp)
                                        for (k in 0 until 3) Text("★", color = if (k < s) T_GOLD else InkColor.copy(alpha = 0.18f), fontSize = 14.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        SectionTitle(stringResource(R.string.trophies_records))
        GameRecords()

        SectionTitle(stringResource(R.string.trophies_topiary))
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(T_CARD).padding(vertical = 10.dp, horizontal = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            SHAPE_GLYPHS.forEachIndexed { i, g ->
                val p = prec[i] ?: 0
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(44.dp)) {
                    Text(g, fontSize = 24.sp)
                    Text(if (p > 0) "$p%" else "—", color = if (p >= 95) T_GOLD else InkColor.copy(alpha = if (p > 0) 0.85f else 0.35f), fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, maxLines = 1)
                    Text(when { p >= 95 -> "🥇"; p >= 80 -> "🥈"; p > 0 -> "🥉"; else -> " " }, fontSize = 14.sp)
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** Records datés de chaque jeu et mode (meilleur score + date). [compact] : version espace parents. */
@Composable
internal fun GameRecords(compact: Boolean = false) {
    @Suppress("UNUSED_EXPRESSION") HighScores.version.value
    Column {
        val vroum = stringResource(R.string.activity_vroum); val garden = stringResource(R.string.activity_garden); val sky = stringResource(R.string.activity_shmup)
        val records = listOf(
            Triple(HighScores.Games.SHMUP, sky, R.drawable.ic_shmup),
            Triple(HighScores.Games.SHMUP_TIMED, "$sky · Score Attack", R.drawable.ic_shmup),
            Triple(HighScores.Games.SHMUP_BOSSRUSH, "$sky · Boss Rush", R.drawable.ic_shmup),
            Triple(HighScores.Games.VROUM, vroum + " · " + stringResource(R.string.vroum_race), R.drawable.ic_vroum),
            Triple(HighScores.Games.VROUM_ENDLESS, vroum + " · " + stringResource(R.string.vroum_endless), R.drawable.ic_vroum),
            Triple(HighScores.Games.GARDEN_ZEN, garden + " · " + stringResource(R.string.gz_mode_zen), R.drawable.ic_garden),
            Triple(HighScores.Games.GARDEN_RUSH, garden + " · " + stringResource(R.string.gz_mode_rush), R.drawable.ic_garden),
            Triple(HighScores.Games.GARDEN_SLICE, garden + " · " + stringResource(R.string.gz_mode_slice), R.drawable.ic_garden),
            Triple(HighScores.Games.BUBBLES, stringResource(R.string.activity_bubbles), R.drawable.ic_bubbles),
            Triple(HighScores.Games.STARS, stringResource(R.string.activity_stars), R.drawable.ic_star),
        ).mapNotNull { (key, name, icon) -> HighScores.top(key).firstOrNull()?.takeIf { it.score > 0 }?.let { Triple(name, icon, it) } }
        if (records.isEmpty()) Text(stringResource(R.string.trophies_none), color = InkColor.copy(alpha = 0.7f), fontSize = 15.sp, modifier = Modifier.padding(8.dp))
        for ((name, icon, best) in records) {
            Row(Modifier.fillMaxWidth().padding(vertical = if (compact) 2.dp else 4.dp).clip(RoundedCornerShape(18.dp)).background(T_CARD)
                .padding(horizontal = 12.dp, vertical = if (compact) 5.dp else 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(icon), contentDescription = null, modifier = Modifier.size(if (compact) 26.dp else 36.dp))
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Text(name, color = InkColor, fontWeight = FontWeight.Bold, fontSize = if (compact) 13.sp else 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("🥇 " + best.name, color = InkColor.copy(alpha = 0.6f), fontSize = 12.sp, maxLines = 1)
                }
                Text("${best.score}", color = T_GOLD, fontWeight = FontWeight.ExtraBold, fontSize = if (compact) 16.sp else 20.sp)
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, color = InkColor, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp, modifier = Modifier.padding(top = 18.dp, bottom = 6.dp, start = 4.dp))
}
