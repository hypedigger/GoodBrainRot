package com.goodbrainrot.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.goodbrainrot.app.R
import com.goodbrainrot.app.audio.Tts
import com.goodbrainrot.app.data.Alphabets
import com.goodbrainrot.app.data.ContentCategory
import com.goodbrainrot.app.data.ContentRepository
import com.goodbrainrot.app.data.Profiles
import com.goodbrainrot.app.data.Stats
import com.goodbrainrot.app.ui.theme.InkColor
import com.goodbrainrot.app.ui.theme.PastelRainbow

private data class ParentCat(val labelRes: Int, val key: String, val route: String, val total: (android.content.Context) -> Int)

private val PARENT_CATS = listOf(
    ParentCat(R.string.activity_guess_animal, "animals", Routes.QUIZ_IMG_ANIMAL) {
        ContentRepository.guessPool(it, listOf(ContentCategory.ANIMAL)).size
    },
    ParentCat(R.string.activity_guess_vehicle, "vehicles", Routes.QUIZ_IMG_VEHICLE) {
        ContentRepository.guessPool(it, listOf(ContentCategory.VEHICLE)).size
    },
    ParentCat(R.string.activity_guess_dino, "dinos", Routes.QUIZ_IMG_DINO) {
        ContentRepository.guessPool(it, listOf(ContentCategory.DINO)).size
    },
    ParentCat(R.string.activity_guess_number, "number", Routes.QUIZ_NUMBER) { 10 },
    ParentCat(R.string.activity_guess_letter, "letter", Routes.QUIZ_LETTER) {
        Alphabets.forLanguage(Tts.currentLocale().language).size
    },
    ParentCat(R.string.activity_color, "color", Routes.QUIZ_COLOR) { 8 },
    ParentCat(R.string.activity_shape, "shape", Routes.QUIZ_SHAPE) { 6 },
    ParentCat(R.string.activity_count, "count", Routes.QUIZ_COUNT) { 6 }
)

private fun fmtDuration(seconds: Long): String {
    val m = seconds / 60
    val h = m / 60
    return if (h > 0) "${h}h${"%02d".format(m % 60)}" else "$m min"
}

/** Résumé d'apprentissage d'une catégorie, pour la synthèse forts / à retravailler. */
private data class CatSummary(
    val labelRes: Int,
    val colorIndex: Int,
    val route: String,
    val learned: Int,
    val total: Int,
    val correct: Int,
    val wrong: Int
) {
    val answered: Int get() = correct + wrong
    val accuracy: Int get() = if (answered > 0) correct * 100 / answered else 0
    val masteryRatio: Float get() = if (total > 0) learned.toFloat() / total else 0f
}

/**
 * Espace parents : suivi du temps d'écran et de la progression (items maîtrisés
 * par catégorie). Chaque ligne lance l'activité correspondante.
 */
@Composable
fun ParentScreen(onBack: () -> Unit, onOpenActivity: (String) -> Unit) {
    val context = LocalContext.current
    val v = Stats.version.intValue
    @Suppress("UNUSED_EXPRESSION") v

    val last7 = remember(v) { Stats.timeLastDays(7) }
    val total = remember(v) { Stats.totalTimeSeconds() }
    val todaySec = last7.lastOrNull()?.second ?: 0L
    val progress14 = remember(v) { Stats.learnedTotalLastDays(14) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            BackButton(onBack = onBack)
            Text(
                text = "👨‍👩‍👧 " + stringResource(R.string.parents_title),
                fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = InkColor,
                modifier = Modifier.padding(start = 4.dp)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            val pv = Profiles.version.value
            @Suppress("UNUSED_EXPRESSION") pv
            val child = remember(pv) { Profiles.current() }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp)
            ) {
                Text(child.avatar, fontSize = 36.sp)
                Column(modifier = Modifier.padding(start = 10.dp)) {
                    Text(child.name, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = InkColor)
                    Text(
                        text = stringResource(R.string.profile_level_short) + " " + Profiles.level(child.xp) +
                            "   ·   " + Profiles.xpInLevel(child.xp) + " / " + Profiles.xpPerLevel() + " XP",
                        fontSize = 14.sp, color = InkColor
                    )
                }
            }
            HorizontalDivider()
            Spacer(modifier = Modifier.height(12.dp))

            Text(stringResource(R.string.parents_screen_time), fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(
                text = stringResource(R.string.parents_today) + " : " + fmtDuration(todaySec) +
                    "   ·   " + stringResource(R.string.parents_total) + " : " + fmtDuration(total),
                fontSize = 15.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
            )
            WeekBars(last7)

            Spacer(modifier = Modifier.height(20.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(12.dp))

            // ---- Courbe de progression d'apprentissage (14 jours) ----
            val learnedNow = progress14.lastOrNull()?.second ?: 0
            Text(stringResource(R.string.parents_progress_curve), fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(
                text = stringResource(R.string.parents_mastered_total) + " : " + learnedNow,
                fontSize = 15.sp, modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
            )
            ProgressCurve(progress14.map { it.second })

            Spacer(modifier = Modifier.height(20.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(12.dp))

            // ---- Détail par catégorie + synthèse forts / à retravailler ----
            val summaries = PARENT_CATS.mapIndexed { i, cat ->
                CatSummary(
                    labelRes = cat.labelRes,
                    colorIndex = i,
                    route = cat.route,
                    learned = Stats.learnedCount(cat.key),
                    total = remember(cat.key) { cat.total(context) },
                    correct = Stats.correctCount(cat.key),
                    wrong = Stats.wrongCount(cat.key)
                )
            }

            Synthesis(summaries)

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(12.dp))

            Text(stringResource(R.string.parents_progress), fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(
                text = stringResource(R.string.parents_tap_hint),
                fontSize = 12.sp, color = InkColor, modifier = Modifier.padding(bottom = 4.dp)
            )
            summaries.forEach { s ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onOpenActivity(s.route) }
                        .padding(vertical = 8.dp, horizontal = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(PastelRainbow[s.colorIndex % PastelRainbow.size])
                    )
                    Text(
                        text = stringResource(s.labelRes),
                        fontSize = 15.sp, fontWeight = FontWeight.Bold, color = InkColor,
                        modifier = Modifier.padding(start = 8.dp).weight(1f)
                    )
                    Text(
                        text = "${s.learned} / ${s.total}" + if (s.answered > 0) "   ·   ${s.accuracy}%" else "",
                        fontSize = 14.sp, color = InkColor
                    )
                    Text(text = " ▶", fontSize = 13.sp, color = InkColor)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFFE89AA0))
                    .clickable { Stats.resetAll() }
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.foundation.Image(
                        painter = androidx.compose.ui.res.painterResource(R.drawable.ic_trash),
                        contentDescription = null,
                        modifier = Modifier.padding(end = 6.dp).size(18.dp)
                    )
                    Text(
                        text = stringResource(R.string.parents_reset),
                        color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp
                    )
                }
            }
        }
    }
}

/**
 * Courbe de progression : total cumulé d'items maîtrisés sur la période.
 * Ligne lissée par segments avec petits points aux relevés quotidiens.
 */
@Composable
private fun ProgressCurve(values: List<Int>) {
    val lineColor = Color(0xFF7FBF7F)
    val dotColor = Color(0xFF4E944F)
    val axis = Color(0xFFBFAFD0)
    val maxV = (values.maxOrNull() ?: 0).coerceAtLeast(1)
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .padding(top = 8.dp)
    ) {
        val n = values.size
        if (n == 0) return@Canvas
        val baseY = size.height - 14f
        val topPad = 8f
        val usableH = baseY - topPad
        val stepX = if (n > 1) size.width / (n - 1) else 0f
        fun px(i: Int) = if (n > 1) i * stepX else size.width / 2f
        fun py(value: Int) = baseY - (value.toFloat() / maxV) * usableH

        drawRect(axis, Offset(0f, baseY), Size(size.width, 2f))
        for (i in 0 until n - 1) {
            drawLine(
                color = lineColor,
                start = Offset(px(i), py(values[i])),
                end = Offset(px(i + 1), py(values[i + 1])),
                strokeWidth = 6f,
                cap = StrokeCap.Round
            )
        }
        for (i in 0 until n) {
            drawCircle(dotColor, radius = 5f, center = Offset(px(i), py(values[i])))
        }
    }
}

/**
 * Synthèse « points forts / à retravailler » déduite des réussites par
 * catégorie. Une catégorie est un point fort si la précision est élevée et le
 * jeu suffisamment pratiqué ; à retravailler si la précision reste basse.
 */
@Composable
private fun Synthesis(summaries: List<CatSummary>) {
    val played = summaries.filter { it.answered > 0 }

    Text(stringResource(R.string.parents_synthesis), fontSize = 18.sp, fontWeight = FontWeight.Bold)
    if (played.isEmpty()) {
        Text(
            text = stringResource(R.string.parents_no_data),
            fontSize = 14.sp, color = InkColor, modifier = Modifier.padding(top = 4.dp)
        )
        return
    }

    val strengths = played
        .filter { it.answered >= 3 && (it.accuracy >= 75 || it.masteryRatio >= 0.6f) }
        .sortedWith(compareByDescending<CatSummary> { it.accuracy }.thenByDescending { it.masteryRatio })
        .take(3)
    val toWork = played
        .filter { it.answered >= 3 && it.accuracy < 60 }
        .sortedBy { it.accuracy }
        .take(3)

    SynthesisBlock(R.drawable.ic_muscle, R.string.parents_strengths, strengths, Color(0xFF4E944F))
    SynthesisBlock(R.drawable.ic_target, R.string.parents_to_work, toWork, Color(0xFFCC7A2B))
}

@Composable
private fun SynthesisBlock(iconRes: Int, titleRes: Int, items: List<CatSummary>, accent: Color) {
    Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = stringResource(titleRes),
            fontSize = 15.sp, fontWeight = FontWeight.Bold, color = accent,
            modifier = Modifier.padding(start = 6.dp)
        )
    }
    if (items.isEmpty()) {
        Text(
            text = "—",
            fontSize = 14.sp, color = InkColor, modifier = Modifier.padding(start = 22.dp, top = 2.dp)
        )
    } else {
        items.forEach { s ->
            Text(
                text = "• " + stringResource(s.labelRes) + "  (" + s.accuracy + "%)",
                fontSize = 14.sp, color = InkColor,
                modifier = Modifier.padding(start = 22.dp, top = 2.dp)
            )
        }
    }
}

/** Histogramme du temps des 7 derniers jours. */
@Composable
private fun WeekBars(days: List<Pair<String, Long>>) {
    val maxSec = (days.maxOfOrNull { it.second } ?: 0L).coerceAtLeast(1L)
    val barColor = Color(0xFFB3D9FF)
    val axis = Color(0xFFBFAFD0)
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .padding(top = 8.dp)
    ) {
        val n = days.size
        val gap = size.width * 0.03f
        val bw = (size.width - gap * (n + 1)) / n
        val baseY = size.height - 18f
        days.forEachIndexed { i, (_, sec) ->
            val h = (sec.toFloat() / maxSec) * (baseY - 6f)
            val x = gap + i * (bw + gap)
            drawRect(color = barColor, topLeft = Offset(x, baseY - h), size = Size(bw, h))
        }
        drawRect(axis, Offset(0f, baseY), Size(size.width, 2f))
    }
}
