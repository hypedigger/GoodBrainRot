package com.goodbrainrot.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import com.goodbrainrot.app.Haptics
import com.goodbrainrot.app.QuizType
import com.goodbrainrot.app.R
import com.goodbrainrot.app.Settings
import com.goodbrainrot.app.audio.SoundEngine
import com.goodbrainrot.app.audio.Tts
import com.goodbrainrot.app.data.Alphabets
import com.goodbrainrot.app.data.Animal
import com.goodbrainrot.app.data.AnimalSoundPlayer
import com.goodbrainrot.app.data.Animals
import com.goodbrainrot.app.data.ContentCategory
import com.goodbrainrot.app.data.ContentItem
import com.goodbrainrot.app.data.ContentRepository
import com.goodbrainrot.app.data.MamaPairs
import com.goodbrainrot.app.data.Stats
import com.goodbrainrot.app.data.animalsWithSound
import com.goodbrainrot.app.data.loadBabyImage
import com.goodbrainrot.app.ui.theme.DeepRainbow
import com.goodbrainrot.app.ui.theme.InkColor
import com.goodbrainrot.app.ui.theme.PastelRainbow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

// ---------------------------------------------------------------------------
// Modèle
// ---------------------------------------------------------------------------

enum class ShapeKind { CIRCLE, SQUARE, TRIANGLE, STAR, HEART, MOON, RECTANGLE, HEXAGON, OVAL }

private sealed class QuizDisplay {
    data class AnimalImage(val animal: Animal) : QuizDisplay()
    data class BigText(val text: String, val color: Color) : QuizDisplay()
    data class IconImage(val res: Int) : QuizDisplay()   // lettre/chiffre en image IA
    data class IconRow(val res: List<Int>) : QuizDisplay()   // nombre à plusieurs chiffres (images IA côte à côte)
    data class ColorTile(val color: Color) : QuizDisplay()
    data class ShapeIcon(val kind: ShapeKind, val color: Color) : QuizDisplay()
    data class Content(val item: ContentItem) : QuizDisplay()
    data class Flag(val code: String) : QuizDisplay()
    data class AssetImage(val path: String) : QuizDisplay()   // image d'assets (métiers)
}

private fun drawableId(context: android.content.Context, name: String): Int =
    context.resources.getIdentifier(name, "drawable", context.packageName)

private fun numberDisplay(context: android.content.Context, n: Int): QuizDisplay {
    // nombres à plusieurs chiffres (10) : on juxtapose les chiffres générés, jamais la police de l'appli
    val digits = "$n".map { drawableId(context, "num_$it") }
    return when {
        digits.size == 1 && digits[0] != 0 -> QuizDisplay.IconImage(digits[0])
        digits.isNotEmpty() && digits.all { it != 0 } -> QuizDisplay.IconRow(digits)
        else -> QuizDisplay.BigText("$n", DeepRainbow[n % DeepRainbow.size])
    }
}

private fun letterDisplay(context: android.content.Context, letter: String, index: Int): QuizDisplay {
    val res = if (letter.length == 1 && letter[0] in 'A'..'Z') drawableId(context, "let_" + letter.lowercase()) else 0
    return if (res != 0) QuizDisplay.IconImage(res) else QuizDisplay.BigText(letter, DeepRainbow[index % DeepRainbow.size])
}

private data class QuizItem(
    val id: String,
    val spoken: String,
    val display: QuizDisplay,
    val animal: Animal? = null
)

private data class Round(
    val target: QuizItem,
    val choices: List<QuizItem>,
    val promptCount: Int = 0,
    val promptEmoji: String = "",
    val promptSubjectNameRes: Int = 0,
    val promptPhraseRes: Int = 0,
    val promptBabyKey: String? = null,
    val promptBabyNameRes: Int = 0
)

/** Tournures variées pour « Compte » (synonymes de « combien de … »). */
private val COUNT_PHRASES = listOf(
    R.string.count_q1, R.string.count_q2, R.string.count_q3, R.string.count_q4
)

private val QUIZ_COLOR_ITEMS = listOf(
    Triple("red", R.string.color_red, Color(0xFFE53935)),
    Triple("blue", R.string.color_blue, Color(0xFF1E88E5)),
    Triple("yellow", R.string.color_yellow, Color(0xFFFDD835)),
    Triple("green", R.string.color_green, Color(0xFF43A047)),
    Triple("orange", R.string.color_orange, Color(0xFFFB8C00)),
    Triple("purple", R.string.color_purple, Color(0xFF8E24AA)),
    Triple("pink", R.string.color_pink, Color(0xFFEC407A)),
    Triple("brown", R.string.color_brown, Color(0xFF6D4C41))
)

private val QUIZ_SHAPE_ITEMS = listOf(
    ShapeKind.CIRCLE to R.string.shape_circle,
    ShapeKind.SQUARE to R.string.shape_square,
    ShapeKind.TRIANGLE to R.string.shape_triangle,
    ShapeKind.STAR to R.string.shape_star,
    ShapeKind.HEART to R.string.shape_heart,
    ShapeKind.MOON to R.string.shape_moon,
    ShapeKind.RECTANGLE to R.string.shape_rectangle,
    ShapeKind.HEXAGON to R.string.shape_hexagon,
    ShapeKind.OVAL to R.string.shape_oval
)

/** Sujets « comptables » (animaux + véhicules) avec emoji + nom. */
private val COUNT_SUBJECTS: List<Pair<String, Int>> = listOf(
    "🐤" to R.string.animal_hen, "🐰" to R.string.animal_rabbit,
    "🐱" to R.string.animal_cat, "🐶" to R.string.animal_dog,
    "🐟" to R.string.animal_dolphin, "🚗" to R.string.vehicle_car,
    "🚌" to R.string.vehicle_bus, "🚀" to R.string.vehicle_rocket,
    "⭐" to R.string.shape_star, "🍎" to R.string.color_red
)

/** Icône IA de chaque sujet de comptage (remplace l'emoji répété). */
private val COUNT_ICONS: Map<String, Int> = mapOf(
    "🐤" to R.drawable.ic_chick, "🐰" to R.drawable.ic_rabbit,
    "🐱" to R.drawable.ic_cat, "🐶" to R.drawable.ic_dog,
    "🐟" to R.drawable.ic_dolphin, "🚗" to R.drawable.ic_car,
    "🚌" to R.drawable.ic_bus, "🚀" to R.drawable.ic_rocket,
    "⭐" to R.drawable.ic_star, "🍎" to R.drawable.ic_apple
)

/** Petits des paires maman/bébé : key d'animal -> nom localisé du bébé. */
private val BABY_NAME_RES: Map<String, Int> = mapOf(
    "cat" to R.string.baby_cat, "dog" to R.string.baby_dog, "hen" to R.string.baby_hen,
    "cow" to R.string.baby_cow, "horse" to R.string.baby_horse, "sheep" to R.string.baby_sheep,
    "duck" to R.string.baby_duck, "frog" to R.string.baby_frog
)

private fun buildPool(context: android.content.Context, type: QuizType): List<QuizItem> =
    when (type) {
        QuizType.ANIMAL, QuizType.MAMA -> Animals.ALL.map { animal ->
            QuizItem(animal.key, context.getString(animal.nameRes),
                QuizDisplay.AnimalImage(animal), animal)
        }
        QuizType.SOUND -> {
            val withSound = animalsWithSound(context)
            Animals.ALL.filter { it.key in withSound }.map { animal ->
                QuizItem(animal.key, context.getString(animal.nameRes),
                    QuizDisplay.AnimalImage(animal), animal)
            }
        }
        QuizType.NUMBER -> (0..9).map { n ->
            QuizItem("n$n", "$n", numberDisplay(context, n))
        }
        QuizType.COUNT -> (1..10).map { n ->
            QuizItem("c$n", "$n", numberDisplay(context, n))
        }
        QuizType.LETTER -> {
            val lang = Tts.currentLocale().language
            Alphabets.forLanguage(lang).mapIndexed { index, letter ->
                QuizItem("l$letter", letter, letterDisplay(context, letter, index))
            }
        }
        QuizType.COLOR -> QUIZ_COLOR_ITEMS.map { (key, nameRes, color) ->
            QuizItem("col_$key", context.getString(nameRes), QuizDisplay.ColorTile(color))
        }
        QuizType.SHAPE -> QUIZ_SHAPE_ITEMS.mapIndexed { index, (kind, nameRes) ->
            QuizItem("sh_$kind", context.getString(nameRes),
                QuizDisplay.ShapeIcon(kind, DeepRainbow[index % DeepRainbow.size]))
        }
        QuizType.IMG_ANIMAL, QuizType.IMG_VEHICLE, QuizType.IMG_DINO, QuizType.IMG_MIXED -> {
            val categories = when (type) {
                QuizType.IMG_ANIMAL -> listOf(ContentCategory.ANIMAL)
                QuizType.IMG_VEHICLE -> listOf(ContentCategory.VEHICLE)
                QuizType.IMG_DINO -> listOf(ContentCategory.DINO)
                else -> listOf(ContentCategory.ANIMAL, ContentCategory.VEHICLE, ContentCategory.DINO)
            }
            // Trié du plus classique au plus exotique (fenêtre gérée à l'appel)
            ContentRepository.rankedGuessPool(context, categories).map { item ->
                QuizItem(item.key, item.displayName(context), QuizDisplay.Content(item))
            }
        }
        QuizType.FLAG -> loadFlagPool(context).map { (code, name) -> QuizItem("fl_$code", name, QuizDisplay.Flag(code)) }
        QuizType.JOB -> JOBS.map { (key, nameRes) -> QuizItem("job_$key", context.getString(nameRes), QuizDisplay.AssetImage("jobs/$key.jpg")) }
        QuizType.INSTRUMENT -> EAR_INSTRUMENTS.map { QuizItem("in_${it.key}", context.getString(it.nameRes), QuizDisplay.IconImage(it.iconRes)) }
    }

/** Propositions par palier (1 Facile, 2 Moyen, 3 Difficile) ; les drapeaux vont jusqu'à 3 colonnes × 5 lignes. */
internal val QUIZ_TIER_CHOICES = listOf(4, 6, 8)
private val FLAG_TIER_CHOICES = listOf(4, 9, 15)
internal fun quizTierOf(choices: Int): Int = when { choices <= 4 -> 1; choices <= 6 -> 2; else -> 3 }
private fun currentQuizTier(): Int =
    if (Settings.quizAutoDifficulty.value) {
        val level = com.goodbrainrot.app.data.Profiles.level(com.goodbrainrot.app.data.Profiles.current().xp)
        when { level <= 2 -> 1; level <= 5 -> 2; else -> 3 }
    } else quizTierOf(Settings.quizChoices.intValue)
private fun choicesFor(type: QuizType, tier: Int): Int =
    (if (type == QuizType.FLAG) FLAG_TIER_CHOICES else QUIZ_TIER_CHOICES)[(tier - 1).coerceIn(0, 2)]

private fun isImageType(type: QuizType) = type in listOf(
    QuizType.IMG_ANIMAL, QuizType.IMG_VEHICLE, QuizType.IMG_DINO, QuizType.IMG_MIXED
)

/** Catégorie utilisée pour les stats / la maîtrise. */
private fun categoryKey(type: QuizType): String = when (type) {
    QuizType.IMG_ANIMAL -> "animals"
    QuizType.IMG_VEHICLE -> "vehicles"
    QuizType.IMG_DINO -> "dinos"
    QuizType.IMG_MIXED -> "mixed"
    else -> type.name.lowercase()
}

private fun buildRound(
    type: QuizType,
    pool: List<QuizItem>,
    choiceCount: Int,
    usedIds: Set<String>,
    catKey: String,
    focus: Set<String> = emptySet()   // mode révision : cibles prises parmi ces items
): Round {
    // Cible jamais répétée dans la série, pondérée par la répétition espacée
    // (boîtes de Leitner) : un item mal maîtrisé (boîte basse) a beaucoup plus
    // de chances de ressortir qu'un item déjà acquis (boîte haute).
    fun pickTarget(): QuizItem {
        val base = if (focus.isEmpty()) pool else pool.filter { it.id in focus }.ifEmpty { pool }
        val candidates = base.filter { it.id !in usedIds }.ifEmpty { base }
        val weights = candidates.map { (Stats.MAX_BOX + 1 - Stats.box(catKey, it.id)).coerceAtLeast(1) }
        val total = weights.sum()
        var r = (0 until total).random()
        for (i in candidates.indices) {
            r -= weights[i]
            if (r < 0) return candidates[i]
        }
        return candidates.last()
    }

    return when (type) {
        QuizType.COUNT -> {
            val target = pickTarget()
            val others = (pool - target).shuffled().take(choiceCount - 1)
            val subject = COUNT_SUBJECTS.random()
            Round(
                target = target,
                choices = (others + target).shuffled(),
                promptCount = target.spoken.toInt(),
                promptEmoji = subject.first,
                promptSubjectNameRes = subject.second,
                promptPhraseRes = COUNT_PHRASES.random()
            )
        }
        QuizType.MAMA -> {
            val keys = MamaPairs.KEYS.filter { it !in usedIds }.ifEmpty { MamaPairs.KEYS }
            val targetKey = keys.random()
            val target = pool.first { it.id == targetKey }
            val others = (pool - target).shuffled().take(choiceCount - 1)
            Round(
                target = target,
                choices = (others + target).shuffled(),
                promptBabyKey = targetKey,
                promptBabyNameRes = BABY_NAME_RES[targetKey] ?: 0
            )
        }
        else -> {
            val target = pickTarget()
            val others = (pool - target).shuffled().take(choiceCount - 1)
            Round(target = target, choices = (others + target).shuffled())
        }
    }
}

private fun speakTarget(context: android.content.Context, type: QuizType, round: Round) {
    when (type) {
        QuizType.SOUND -> round.target.animal?.let {
            AnimalSoundPlayer.play(context, it, sayNameAfter = false, speakIfMissing = false)
        }
        // instrument à l'oreille : on joue une petite phrase, sans dire le nom (c'est la réponse)
        QuizType.INSTRUMENT -> EarInstrumentPlayer.play(context, round.target.id.removePrefix("in_"))
        QuizType.COUNT -> {
            val phrase = if (round.promptPhraseRes != 0) round.promptPhraseRes else R.string.count_q1
            Tts.speak(context.getString(phrase, context.getString(round.promptSubjectNameRes)))
        }
        QuizType.MAMA -> Tts.speak(
            context.getString(R.string.find_mama_named, context.getString(round.promptBabyNameRes))
        )
        else -> Tts.speak(round.target.spoken)
    }
}

// ---------------------------------------------------------------------------
// Écran
// ---------------------------------------------------------------------------

/**
 * Moteur de quiz générique. Une série dure N questions (configurable) avec un
 * score : +X par bonne réponse, −Y par mauvaise. À la fin : notation animée
 * D / C / B / A / AA (bronze) / AAA (argent) / S (or), puis nouvelle série.
 */
@Composable
fun QuizScreen(type: QuizType, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pool = remember(type) { buildPool(context, type) }
    // Nombre de propositions : palier Facile / Moyen / Difficile, choisi dans les options
    // ou (mode auto) déduit du niveau global de l'enfant.
    val choiceCount = choicesFor(type, currentQuizTier()).coerceIn(2, pool.size)
    val totalQuestions = Settings.quizQuestions.intValue
    val pointsCorrect = Settings.quizPointsCorrect.intValue
    val pointsWrong = Settings.quizPointsWrong.intValue

    var roundId by remember { mutableIntStateOf(0) }
    var questionNumber by remember { mutableIntStateOf(1) }
    var score by remember { mutableIntStateOf(0) }
    var sessionDone by remember { mutableStateOf(false) }
    var confettiTrigger by remember { mutableIntStateOf(0) }
    // Niveau atteint à afficher en grand quand l'enfant vient de monter (0 = rien)
    var levelUpTo by remember { mutableIntStateOf(0) }
    var sessionToken by remember { mutableIntStateOf(0) }

    val catKey = remember(type) { categoryKey(type) }
    // Mode révision : la série ne porte que sur les items déjà vus mais encore fragiles
    var review by remember { mutableStateOf(false) }
    val reviewIds = remember(sessionToken) { Stats.reviewKeys(catKey).filter { id -> pool.any { it.id == id } }.toSet() }
    // Items déjà demandés dans la série en cours (remis à zéro à chaque nouvelle série)
    val usedTargets = remember(sessionToken) { mutableStateListOf<String>() }
    val round = remember(roundId) {
        // Pour les jeux d'images : fenêtre qui s'élargit du classique vers l'exotique
        // à mesure que l'enfant maîtrise des items (en révision : tout le contenu).
        // Drapeaux : même principe sur 196 pays, en partant des plus connus.
        val effectivePool = if ((isImageType(type) || type == QuizType.FLAG) && pool.isNotEmpty() && !review) {
            val base = if (type == QuizType.FLAG) 16 else 8
            val n = (base + 2 * Stats.learnedCount(catKey)).coerceIn(choiceCount, pool.size)
            pool.take(n)
        } else pool
        val r = buildRound(type, effectivePool, choiceCount, usedTargets.toSet(), catKey, if (review) reviewIds else emptySet())
        usedTargets.add(r.target.id)
        r
    }
    val hiddenIds = remember(roundId) { mutableStateListOf<String>() }
    var solved by remember(roundId) { mutableStateOf(false) }

    DisposableEffect(Unit) {
        if (type == QuizType.INSTRUMENT) EarInstrumentPlayer.preload(context)
        onDispose { AnimalSoundPlayer.stop(); EarInstrumentPlayer.stop() }
    }

    LaunchedEffect(roundId, sessionDone) {
        if (sessionDone) return@LaunchedEffect
        delay(500)
        speakTarget(context, type, round)
        delay(8000)
        if (!solved) speakTarget(context, type, round) // dernier rappel
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                BackButton(onBack = onBack)
                // bouton Révision : visible dès qu'il y a au moins 2 items fragiles à retravailler
                if ((reviewIds.size >= 2 || review) && type != QuizType.MAMA) {
                    Box(
                        Modifier.clip(RoundedCornerShape(16.dp))
                            .background(if (review) Color(0xFFFFC93D) else Color(0x22000000))
                            .clickable {
                                review = !review
                                score = 0; questionNumber = 1; sessionDone = false
                                sessionToken++; roundId++
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("🔁 " + stringResource(R.string.quiz_review) + if (review) "" else " (${reviewIds.size})",
                            fontSize = 14.sp, fontWeight = FontWeight.Bold, color = InkColor, maxLines = 1)
                    }
                }
                Text(
                    text = "⭐ $score    $questionNumber/$totalQuestions",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = InkColor,
                    modifier = Modifier.padding(end = 12.dp)
                )
            }

            if (review) Text(stringResource(R.string.quiz_review_hint), fontSize = 13.sp, color = InkColor.copy(alpha = 0.7f),
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 4.dp))
            QuizPrompt(type, round, modifier = Modifier.fillMaxWidth())

            // Grille qui TIENT dans l'écran, centrée : on choisit le nombre de colonnes qui donne
            // les plus grandes cases possible pour le nombre de propositions (jamais de défilement).
            androidx.compose.foundation.layout.BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                val gap = 12.dp
                val tileAspect = if (type == QuizType.FLAG) 1.25f else 1f   // largeur / hauteur d'une case
                val n = round.choices.size
                var cols = 2; var tileW = 0.dp
                // au moins 2 colonnes dès 3 propositions : une seule colonne ressemble à une liste, pas à une grille de jeu
                // drapeaux à 9+ propositions : 3 colonnes (3 × 3, 3 × 5), format demandé
                val minCols = when { type == QuizType.FLAG && n >= 9 -> 3; n >= 3 -> 2; else -> 1 }
                for (c in minCols..4) {
                    val rows = (n + c - 1) / c
                    val byW = (maxWidth - gap * (c - 1)) / c
                    val byH = (maxHeight - gap * (rows - 1)) / rows * tileAspect
                    val w = minOf(byW, byH)
                    if (w > tileW + 1.dp) { tileW = w; cols = c }
                }
                val tileH = tileW / tileAspect
                Column(verticalArrangement = Arrangement.spacedBy(gap), horizontalAlignment = Alignment.CenterHorizontally) {
                    round.choices.chunked(cols).forEach { rowItems ->
                        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                            rowItems.forEach { item -> androidx.compose.runtime.key(item.id) { Box(Modifier.size(tileW, tileH)) {
                    val isTarget = item.id == round.target.id
                    val hidden = item.id in hiddenIds
                    val tileColor = when (type) {
                        QuizType.COLOR -> (item.display as QuizDisplay.ColorTile).color
                        QuizType.SHAPE, QuizType.FLAG, QuizType.JOB, QuizType.IMG_DINO -> Color.White
                        else -> PastelRainbow[round.choices.indexOf(item) % PastelRainbow.size]
                    }
                    val scale by animateFloatAsState(
                        targetValue = if (solved && isTarget) 1.12f else 1f, label = "winScale"
                    )
                    val itemAlpha by animateFloatAsState(
                        targetValue = if (hidden) 0f else 1f, label = "hideAlpha"
                    )
                    // Rotation « flip » déclenchée au clic (surtout pour les lettres)
                    val spin = remember(item.id, roundId) { Animatable(0f) }

                    fun onChoice() {
                        if (solved || hidden || sessionDone) return
                        if (type == QuizType.LETTER) {
                            scope.launch { spin.snapTo(0f); spin.animateTo(360f, tween(450)) }
                        }
                        if (item.animal != null && (type == QuizType.ANIMAL || type == QuizType.MAMA)) {
                            AnimalSoundPlayer.play(context, item.animal, sayNameAfter = false)
                        }
                        if (isTarget) {
                            solved = true
                            score += pointsCorrect
                            Stats.recordAnswer(catKey, round.target.id, true)
                            val profiles = com.goodbrainrot.app.data.Profiles
                            val levelBefore = profiles.level(profiles.current().xp)
                            profiles.addXp(pointsCorrect)
                            val levelAfter = profiles.level(profiles.current().xp)
                            if (levelAfter > levelBefore) levelUpTo = levelAfter
                            confettiTrigger++
                            Haptics.success(context)
                            SoundEngine.playVictory()
                            scope.launch {
                                delay(1600)
                                if (questionNumber >= totalQuestions) {
                                    sessionDone = true
                                    SoundEngine.playFanfare()
                                } else {
                                    questionNumber++; roundId++
                                }
                            }
                        } else {
                            score = max(0, score - pointsWrong)
                            Stats.recordAnswer(catKey, round.target.id, false)
                            SoundEngine.playError()
                            hiddenIds.add(item.id)
                            scope.launch {
                                delay(800)
                                if (!solved) speakTarget(context, type, round)
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = scale; scaleY = scale
                                rotationY = spin.value
                                cameraDistance = 14f * density
                            }
                            .alpha(itemAlpha)
                    ) {
                        Card(
                            onClick = { onChoice() },
                            modifier = Modifier.fillMaxSize(),
                            shape = RoundedCornerShape(20.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                            colors = CardDefaults.cardColors(containerColor = tileColor)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .then(
                                        if (type == QuizType.SHAPE)
                                            Modifier.border(2.dp, PastelRainbow[round.choices.indexOf(item) % PastelRainbow.size], RoundedCornerShape(20.dp))
                                        else Modifier
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                QuizTileContent(type, item.display, tileColor)
                                if (solved && isTarget) Text(text = "🎉", fontSize = 44.sp)
                            }
                        }
                    }
                            } } }
                        }
                    }
                }
            }

            QuizBottomBar(type, round, onSpeak = { speakTarget(context, type, round) })
        }

        ConfettiBurst(trigger = confettiTrigger, modifier = Modifier.align(Alignment.Center))

        if (levelUpTo > 0) {
            LevelUpOverlay(level = levelUpTo, onDone = { levelUpTo = 0 })
        }

        if (sessionDone) {
            GradeOverlay(
                score = score,
                maxScore = totalQuestions * pointsCorrect,
                onRestart = {
                    score = 0; questionNumber = 1; sessionDone = false
                    sessionToken++; roundId++
                }
            )
        }
    }
}

/**
 * Petite célébration de montée de niveau : badge « Niveau N ! » qui rebondit
 * au centre de l'écran puis disparaît tout seul (~2 s), sans bloquer le jeu.
 */
@Composable
private fun LevelUpOverlay(level: Int, onDone: () -> Unit) {
    val scale = remember { Animatable(0f) }
    val alpha = remember { Animatable(1f) }
    LaunchedEffect(level) {
        SoundEngine.playFanfare()
        scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
        delay(1400)
        alpha.animateTo(0f, tween(350))
        onDone()
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .alpha(alpha.value),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .graphicsLayer { scaleX = scale.value; scaleY = scale.value }
                .clip(RoundedCornerShape(28.dp))
                .background(Color(0xEEFFF3C2))
                .border(4.dp, Color(0xFFFFC93D), RoundedCornerShape(28.dp))
                .padding(horizontal = 36.dp, vertical = 24.dp)
        ) {
            Text(text = "🎉⭐🎉", fontSize = 40.sp)
            Text(
                text = stringResource(R.string.level_up_label, level),
                fontSize = 34.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF8A6D00)
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Invite (au-dessus de la grille) et bandeau bas (texte de la consigne)
// ---------------------------------------------------------------------------

@Composable
private fun QuizPrompt(type: QuizType, round: Round, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    when (type) {
        QuizType.COUNT -> Box(modifier = modifier.height(96.dp), contentAlignment = Alignment.Center) {
            val iconRes = COUNT_ICONS[round.promptEmoji] ?: 0
            if (iconRes != 0) {
                // Rangées d'icônes IA (max 5 par ligne)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    (1..round.promptCount).chunked(5).forEach { chunk ->
                        Row {
                            chunk.forEach { _ ->
                                androidx.compose.foundation.Image(
                                    painter = androidx.compose.ui.res.painterResource(iconRes),
                                    contentDescription = null,
                                    modifier = Modifier.padding(2.dp).size(40.dp)
                                )
                            }
                        }
                    }
                }
            } else {
                Text(
                    text = (1..round.promptCount).joinToString(" ") { round.promptEmoji },
                    fontSize = 44.sp
                )
            }
        }
        QuizType.MAMA -> Box(modifier = modifier.height(110.dp), contentAlignment = Alignment.Center) {
            val babyKey = round.promptBabyKey
            val bitmap = remember(babyKey) { babyKey?.let { loadBabyImage(context, it) } }
            if (bitmap != null) {
                androidx.compose.foundation.Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    modifier = Modifier.size(100.dp).clip(CircleShape),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )
            } else {
                val animal = babyKey?.let { k -> Animals.byKey(k) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (animal != null) {
                        AnimalVisual(animal, modifier = Modifier.size(72.dp), emojiSize = 48)
                    } else {
                        Text(text = "🐣", fontSize = 48.sp)
                    }
                    androidx.compose.foundation.Image(
                        painter = androidx.compose.ui.res.painterResource(R.drawable.ic_bottle),
                        contentDescription = null,
                        modifier = Modifier.padding(start = 6.dp).size(44.dp)
                    )
                }
            }
        }
        else -> {}
    }
}

@Composable
private fun QuizBottomBar(type: QuizType, round: Round, onSpeak: () -> Unit) {
    when (type) {
        QuizType.SOUND, QuizType.INSTRUMENT -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
                    .clickable { onSpeak() },
                contentAlignment = Alignment.Center
            ) {
                Text(text = "🔊", fontSize = 52.sp)
            }
        }
        else -> {
            val label: String? = when (type) {
                QuizType.ANIMAL, QuizType.COLOR, QuizType.SHAPE, QuizType.FLAG, QuizType.JOB,
                QuizType.IMG_ANIMAL, QuizType.IMG_VEHICLE, QuizType.IMG_DINO, QuizType.IMG_MIXED ->
                    round.target.spoken
                QuizType.MAMA -> stringResource(R.string.find_mama_named, stringResource(round.promptBabyNameRes))
                else -> null
            }
            if (label != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                        .clickable { onSpeak() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = InkColor
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Contenu des cases
// ---------------------------------------------------------------------------

@Composable
private fun QuizTileContent(type: QuizType, display: QuizDisplay, tileColor: Color) {
    when (display) {
        is QuizDisplay.AnimalImage -> AnimalVisual(
            animal = display.animal, modifier = Modifier.fillMaxSize(), emojiSize = 64,
            photoScale = androidx.compose.ui.layout.ContentScale.Crop
        )
        is QuizDisplay.BigText -> Text(
            text = display.text, fontSize = 72.sp,
            fontWeight = FontWeight.ExtraBold, color = display.color
        )
        is QuizDisplay.IconImage -> androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.painterResource(display.res),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(0.82f)
        )
        is QuizDisplay.ColorTile -> {
            // La case EST la couleur : juste un petit reflet pour le relief
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            listOf(Color.White.copy(alpha = 0.25f), Color.Transparent)
                        )
                    )
            )
        }
        is QuizDisplay.ShapeIcon -> ShapeView(
            kind = display.kind, color = display.color,
            backgroundColor = Color.White, modifier = Modifier.fillMaxSize(0.7f)
        )
        is QuizDisplay.Content -> ContentVisual(
            item = display.item, modifier = Modifier.fillMaxSize(), emojiSize = 64,
            photoScale = androidx.compose.ui.layout.ContentScale.Crop
        )
        is QuizDisplay.IconRow -> DigitRow(display.res)
        is QuizDisplay.Flag -> FlagView(display.code, modifier = Modifier.fillMaxSize(0.88f))
        is QuizDisplay.AssetImage -> {
            val context = LocalContext.current
            val bmp = remember(display.path) {
                try { context.assets.open(display.path).use { android.graphics.BitmapFactory.decodeStream(it) }?.asImageBitmap() } catch (_: Exception) { null }
            }
            if (bmp != null) androidx.compose.foundation.Image(bitmap = bmp, contentDescription = null, modifier = Modifier.fillMaxSize(),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop)
        }
    }
}

/**
 * Nombre à plusieurs chiffres (10) avec les chiffres générés : chaque image est rognée à son contour visible
 * (les PNG ont de larges marges transparentes), les chiffres sont accolés, à la MÊME hauteur qu'un chiffre seul ;
 * on ne réduit que si l'ensemble ne tient pas dans la case avec une petite marge.
 */
@Composable
private fun DigitRow(res: List<Int>) {
    val context = LocalContext.current
    val digits = remember(res) {
        res.map { id ->
            val bmp = android.graphics.BitmapFactory.decodeResource(context.resources, id)
            var x0 = bmp.width; var y0 = bmp.height; var x1 = 0; var y1 = 0
            val px = IntArray(bmp.width * bmp.height); bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
            for (y in 0 until bmp.height) for (x in 0 until bmp.width) if ((px[y * bmp.width + x] ushr 24) > 24) {
                if (x < x0) x0 = x; if (x > x1) x1 = x; if (y < y0) y0 = y; if (y > y1) y1 = y
            }
            Triple(bmp.asImageBitmap(), androidx.compose.ui.unit.IntOffset(x0, y0), androidx.compose.ui.unit.IntSize(x1 - x0 + 1, y1 - y0 + 1))
        }
    }
    Canvas(Modifier.fillMaxSize()) {
        val (img0, _, sz0) = digits.first()
        // hauteur visible d'un chiffre seul affiché à 82 % de la case (comme les autres réponses)
        val single = size.minDimension * 0.82f
        var h = single * sz0.height / img0.height
        val gap = h * 0.04f
        var totalW = digits.sumOf { (_, _, s) -> (s.width * h / s.height).toDouble() }.toFloat() + gap * (digits.size - 1)
        val maxW = size.width * 0.9f
        if (totalW > maxW) { val k = maxW / totalW; h *= k; totalW = maxW }
        var x = (size.width - totalW) / 2f
        val y = (size.height - h) / 2f
        for ((img, off, s) in digits) {
            val w = s.width * h / s.height
            drawImage(img, srcOffset = off, srcSize = s,
                dstOffset = androidx.compose.ui.unit.IntOffset(x.toInt(), y.toInt()),
                dstSize = androidx.compose.ui.unit.IntSize(w.toInt(), h.toInt()))
            x += w + gap
        }
    }
}

/** Dessin vectoriel des formes du quiz. */
@Composable
fun ShapeView(kind: ShapeKind, color: Color, backgroundColor: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.minDimension
        val c = center
        when (kind) {
            ShapeKind.CIRCLE -> drawCircle(color, w * 0.42f, c)
            ShapeKind.SQUARE -> drawRect(
                color,
                topLeft = Offset(c.x - w * 0.38f, c.y - w * 0.38f),
                size = Size(w * 0.76f, w * 0.76f)
            )
            ShapeKind.TRIANGLE -> {
                val path = Path().apply {
                    moveTo(c.x, c.y - w * 0.42f)
                    lineTo(c.x - w * 0.44f, c.y + w * 0.34f)
                    lineTo(c.x + w * 0.44f, c.y + w * 0.34f)
                    close()
                }
                drawPath(path, color)
            }
            ShapeKind.STAR -> {
                val path = Path()
                for (k in 0 until 10) {
                    val r = if (k % 2 == 0) w * 0.45f else w * 0.19f
                    val angle = -Math.PI / 2 + k * Math.PI / 5
                    val x = c.x + (r * cos(angle)).toFloat()
                    val y = c.y + (r * sin(angle)).toFloat()
                    if (k == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close()
                drawPath(path, color)
            }
            ShapeKind.HEART -> {
                val s = w * 0.85f
                val path = Path().apply {
                    moveTo(c.x, c.y + s * 0.36f)
                    cubicTo(c.x - s * 0.62f, c.y - s * 0.08f, c.x - s * 0.32f, c.y - s * 0.55f, c.x, c.y - s * 0.18f)
                    cubicTo(c.x + s * 0.32f, c.y - s * 0.55f, c.x + s * 0.62f, c.y - s * 0.08f, c.x, c.y + s * 0.36f)
                    close()
                }
                drawPath(path, color)
            }
            ShapeKind.MOON -> {
                drawCircle(color, w * 0.42f, c)
                drawCircle(backgroundColor, w * 0.36f, Offset(c.x + w * 0.2f, c.y - w * 0.12f))
            }
            ShapeKind.RECTANGLE -> drawRect(
                color,
                topLeft = Offset(c.x - w * 0.44f, c.y - w * 0.27f),
                size = Size(w * 0.88f, w * 0.54f)
            )
            ShapeKind.HEXAGON -> {
                val path = Path()
                for (k in 0 until 6) {
                    val angle = -Math.PI / 2 + k * Math.PI / 3
                    val x = c.x + (w * 0.43f * cos(angle)).toFloat()
                    val y = c.y + (w * 0.43f * sin(angle)).toFloat()
                    if (k == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close()
                drawPath(path, color)
            }
            ShapeKind.OVAL -> drawOval(
                color,
                topLeft = Offset(c.x - w * 0.44f, c.y - w * 0.27f),
                size = Size(w * 0.88f, w * 0.54f)
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Confettis + écran de notation
// ---------------------------------------------------------------------------

private data class ConfettiParticle(val emoji: String, val vx: Float, val vy: Float, val size: Int)

@Composable
fun ConfettiBurst(trigger: Int, modifier: Modifier = Modifier) {
    if (trigger == 0) return
    val particles = remember(trigger) {
        List(16) {
            val angle = Random.nextDouble(0.0, 2 * Math.PI)
            val speed = Random.nextDouble(0.5, 1.0)
            ConfettiParticle(
                emoji = listOf("🎉", "⭐", "✨", "💛", "💙", "💚").random(),
                vx = (cos(angle) * speed).toFloat(),
                vy = (sin(angle) * speed - 0.7).toFloat(),
                size = Random.nextInt(18, 34)
            )
        }
    }
    val progress = remember(trigger) { Animatable(0f) }
    LaunchedEffect(trigger) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(durationMillis = 1000, easing = LinearEasing))
    }
    if (progress.value < 1f) {
        Box(modifier = modifier) {
            particles.forEach { p ->
                val t = progress.value
                val x = p.vx * t * 320
                val y = p.vy * t * 320 + 380 * t * t
                Text(
                    text = p.emoji, fontSize = p.size.sp,
                    modifier = Modifier.offset(x.dp, y.dp).alpha(1f - t * t)
                )
            }
        }
    }
}

private data class Grade(val label: String, val color: Color)

private fun gradeFor(score: Int, maxScore: Int): Grade {
    val ratio = if (maxScore <= 0) 0f else score.toFloat() / maxScore
    return when {
        ratio >= 0.999f -> Grade("S", Color(0xFFFFD700))
        ratio >= 0.90f -> Grade("AAA", Color(0xFFB8C4CC))
        ratio >= 0.80f -> Grade("AA", Color(0xFFCD7F32))
        ratio >= 0.70f -> Grade("A", Color(0xFF4CAF50))
        ratio >= 0.55f -> Grade("B", Color(0xFF2196F3))
        ratio >= 0.40f -> Grade("C", Color(0xFFFF9800))
        else -> Grade("D", Color(0xFF9E9E9E))
    }
}

@Composable
private fun GradeOverlay(score: Int, maxScore: Int, onRestart: () -> Unit) {
    val grade = remember(score, maxScore) { gradeFor(score, maxScore) }
    val scale = remember { Animatable(0f) }
    var burst by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
        repeat(3) { burst++; delay(700) }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x99000000))
            .clickable { onRestart() },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = grade.label, fontSize = 130.sp, fontWeight = FontWeight.ExtraBold,
                color = grade.color,
                modifier = Modifier.graphicsLayer {
                    scaleX = scale.value; scaleY = scale.value
                    rotationZ = (1f - scale.value) * -20f
                }
            )
            Text(
                text = "⭐ $score / $maxScore", fontSize = 28.sp,
                fontWeight = FontWeight.Bold, color = Color.White,
                modifier = Modifier.padding(top = 8.dp)
            )
            Text(text = "👆", fontSize = 36.sp, modifier = Modifier.padding(top = 24.dp))
        }
        ConfettiBurst(trigger = burst, modifier = Modifier.align(Alignment.Center))
    }
}
