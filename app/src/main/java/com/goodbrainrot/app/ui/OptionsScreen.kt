package com.goodbrainrot.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.RadioButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.goodbrainrot.app.R
import com.goodbrainrot.app.Settings
import com.goodbrainrot.app.data.HighScores
import com.goodbrainrot.app.data.Stats
import com.goodbrainrot.app.ui.theme.InkColor
import com.goodbrainrot.app.ui.theme.PastelRainbow
import java.util.Locale

private data class TabSpec(val key: String, val labelRes: Int)

private val TABS = listOf(
    TabSpec(OptionsTab.GENERAL, R.string.tab_general),
    TabSpec(OptionsTab.PIANO, R.string.tab_instruments),
    TabSpec(OptionsTab.SOUNDS, R.string.activity_animal_sounds),
    TabSpec(OptionsTab.QUIZ, R.string.tab_quiz),
    TabSpec(OptionsTab.ARCADE, R.string.tab_arcade)
)

private val LANGUAGES = listOf(
    "fr" to R.string.lang_fr,
    "en" to R.string.lang_en,
    "it" to R.string.lang_it,
    "es" to R.string.lang_es,
    "ru" to R.string.lang_ru
)

@Composable
fun OptionsScreen(initialTab: String, onBack: () -> Unit) {
    var selectedTab by rememberSaveable {
        mutableIntStateOf(TABS.indexOfFirst { it.key == initialTab }.coerceAtLeast(0))
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
        ) {
            BackButton(onBack = onBack)
            Text(
                text = stringResource(R.string.options),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
        }

        ScrollableTabRow(selectedTabIndex = selectedTab, edgePadding = 8.dp) {
            TABS.forEachIndexed { index, tab ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(stringResource(tab.labelRes)) }
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            when (TABS[selectedTab].key) {
                OptionsTab.GENERAL -> GeneralTab()
                OptionsTab.PIANO -> PianoTab()
                OptionsTab.SOUNDS -> SoundsTab()
                OptionsTab.QUIZ -> QuizTab()
                OptionsTab.ARCADE -> ArcadeTab()
            }
        }
    }
}

@Composable
private fun GeneralTab() {
    val currentLang = remember {
        val locales = AppCompatDelegate.getApplicationLocales()
        (if (!locales.isEmpty) locales[0]?.language else null)
            ?: Locale.getDefault().language
    }

    Text(
        text = stringResource(R.string.language_label),
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold
    )
    LANGUAGES.forEach { (tag, labelRes) ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .selectable(
                    selected = currentLang == tag,
                    onClick = {
                        // Recrée l'activité avec la nouvelle langue (persistée automatiquement)
                        AppCompatDelegate.setApplicationLocales(
                            LocaleListCompat.forLanguageTags(tag)
                        )
                    }
                )
                .padding(vertical = 4.dp)
        ) {
            RadioButton(
                selected = currentLang == tag,
                onClick = {
                    AppCompatDelegate.setApplicationLocales(
                        LocaleListCompat.forLanguageTags(tag)
                    )
                }
            )
            LanguageFlag(tag, modifier = Modifier.padding(end = 10.dp))
            Text(text = stringResource(labelRes), fontSize = 18.sp)
        }
    }

    Spacer(modifier = Modifier.height(24.dp))
    HorizontalDivider()
    Spacer(modifier = Modifier.height(24.dp))

    // Rappel du fonctionnement du baby mode (l'interrupteur est sur l'accueil)
    IconHeader(R.drawable.ic_baby, stringResource(R.string.baby_mode_label))
    Text(
        text = stringResource(R.string.baby_mode_desc),
        fontSize = 14.sp,
        lineHeight = 20.sp,
        modifier = Modifier.padding(top = 4.dp)
    )

    // Numéro de version en bas de l'onglet Général
    val ctx = LocalContext.current
    val versionName = remember {
        try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName } catch (_: Exception) { "" }
    }
    Spacer(modifier = Modifier.height(28.dp))
    Text(
        text = "GoodBrainRot — version $versionName\nbuild du " + com.goodbrainrot.app.BuildConfig.BUILD_TIME,
        fontSize = 13.sp,
        color = androidx.compose.ui.graphics.Color(0xFF9E8FB0),
        modifier = Modifier.fillMaxWidth(),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center
    )
    Spacer(modifier = Modifier.height(10.dp))
    Text(
        text = "Crimson Sky — sprites : Foozle « Void/Kla'ed » (CC0) ; " +
               "boss « Jerom » et « Space War Man » (Emcee Flesher / Daniel Cook), CC-BY 3.0. " +
               "Effets sonores : Kenney (CC0).",
        fontSize = 11.sp,
        color = androidx.compose.ui.graphics.Color(0xFFB0A4C0),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center
    )
}

@Composable
private fun PianoTab() {
    // Son synthétisé vs échantillons réalistes (vaut pour piano/guitare/xylophone)
    val realistic by Settings.realisticInstruments
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Switch(checked = realistic, onCheckedChange = { Settings.setRealisticInstruments(it) })
        Text(
            text = stringResource(R.string.sound_realistic),
            fontSize = 18.sp,
            modifier = Modifier.padding(start = 12.dp)
        )
    }
    Text(
        text = stringResource(R.string.sound_realistic_hint),
        fontSize = 13.sp,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
    )
    HorizontalDivider()
    Spacer(modifier = Modifier.height(8.dp))

    Text(
        text = "📱 " + stringResource(R.string.piano_portrait),
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold
    )
    Stepper(
        label = stringResource(R.string.piano_rows_label),
        value = Settings.pianoRowsPortrait.intValue,
        range = 1..5,
        onChange = { Settings.setPianoRowsPortrait(it) }
    )
    Stepper(
        label = stringResource(R.string.piano_keys_per_row_label),
        value = Settings.pianoKeysPerRowPortrait.intValue,
        range = 3..10,
        onChange = { Settings.setPianoKeysPerRowPortrait(it) }
    )

    Spacer(modifier = Modifier.height(16.dp))
    HorizontalDivider()
    Spacer(modifier = Modifier.height(16.dp))

    Text(
        text = "📲 " + stringResource(R.string.piano_landscape),
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold
    )
    Stepper(
        label = stringResource(R.string.piano_rows_label),
        value = Settings.pianoRowsLandscape.intValue,
        range = 1..3,
        onChange = { Settings.setPianoRowsLandscape(it) }
    )
    Stepper(
        label = stringResource(R.string.piano_keys_per_row_label),
        value = Settings.pianoKeysPerRowLandscape.intValue,
        range = 5..15,
        onChange = { Settings.setPianoKeysPerRowLandscape(it) }
    )

    Spacer(modifier = Modifier.height(16.dp))
    HorizontalDivider()
    Spacer(modifier = Modifier.height(16.dp))

    IconHeader(R.drawable.ic_guitar, stringResource(R.string.activity_guitar))
    Stepper(
        label = stringResource(R.string.guitar_strings_label),
        value = Settings.guitarStrings.intValue,
        range = 4..6,
        onChange = { Settings.setGuitarStrings(it) }
    )
    Stepper(
        label = stringResource(R.string.guitar_frets_label),
        value = Settings.guitarFrets.intValue,
        range = 6..20,
        onChange = { Settings.setGuitarFrets(it) }
    )
}

@Composable
private fun SoundsTab() {
    val sayName by Settings.sayAnimalName
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Switch(
            checked = sayName,
            onCheckedChange = { Settings.setSayAnimalName(it) }
        )
        Text(
            text = stringResource(R.string.animal_sounds_say_name_label),
            fontSize = 18.sp,
            modifier = Modifier.padding(start = 12.dp)
        )
    }
}

/** Réglages communs à tous les quiz : affichage, choix, longueur de série, points. */
@Composable
private fun QuizTab() {
    // Photo vs emoji/dessin pour les animaux/véhicules/dinos
    val usePhotos by Settings.useContentPhotos
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Switch(checked = usePhotos, onCheckedChange = { Settings.setUseContentPhotos(it) })
        Text(
            text = stringResource(
                if (usePhotos) R.string.display_photo else R.string.display_emoji
            ),
            fontSize = 18.sp,
            modifier = Modifier.padding(start = 12.dp)
        )
    }
    Text(
        text = stringResource(R.string.display_hint),
        fontSize = 13.sp,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
    )
    HorizontalDivider()
    Spacer(modifier = Modifier.height(8.dp))

    // Difficulté auto : le nombre de choix suit le niveau de l'enfant.
    val autoDiff by Settings.quizAutoDifficulty
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Switch(checked = autoDiff, onCheckedChange = { Settings.setQuizAutoDifficulty(it) })
        Text(
            text = stringResource(R.string.quiz_auto_difficulty_label),
            fontSize = 18.sp,
            modifier = Modifier.padding(start = 12.dp)
        )
    }
    if (!autoDiff) {
        Stepper(
            label = stringResource(R.string.quiz_choices_label),
            value = Settings.quizChoices.intValue,
            range = 4..10,
            onChange = { Settings.setQuizChoices(it) }
        )
    }
    Stepper(
        label = stringResource(R.string.quiz_questions_label),
        value = Settings.quizQuestions.intValue,
        range = 3..20,
        onChange = { Settings.setQuizQuestions(it) }
    )
    Stepper(
        label = stringResource(R.string.quiz_points_correct_label),
        value = Settings.quizPointsCorrect.intValue,
        range = 1..10,
        onChange = { Settings.setQuizPointsCorrect(it) }
    )
    Stepper(
        label = stringResource(R.string.quiz_points_wrong_label),
        value = Settings.quizPointsWrong.intValue,
        range = 0..5,
        onChange = { Settings.setQuizPointsWrong(it) }
    )
}

/** Réglages des jeux d'arcade : durée des parties + remise à zéro des scores. */
@Composable
private fun ArcadeTab() {
    IconHeader(R.drawable.ic_bubbles, stringResource(R.string.activity_bubbles))
    Stepper(
        label = stringResource(R.string.game_duration_label),
        value = Settings.bubblesDuration.intValue,
        range = 10..180,
        step = 10,
        onChange = { Settings.setBubblesDuration(it) }
    )

    Spacer(modifier = Modifier.height(16.dp))
    HorizontalDivider()
    Spacer(modifier = Modifier.height(16.dp))

    IconHeader(R.drawable.ic_star, stringResource(R.string.activity_stars))
    Stepper(
        label = stringResource(R.string.game_duration_label),
        value = Settings.starsDuration.intValue,
        range = 10..180,
        step = 10,
        onChange = { Settings.setStarsDuration(it) }
    )

    Spacer(modifier = Modifier.height(24.dp))
    HorizontalDivider()
    Spacer(modifier = Modifier.height(16.dp))

    val v = HighScores.version.value // recomposition après reset
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFFE89AA0))
            .clickableNoRipple {
                @Suppress("UNUSED_EXPRESSION") v
                HighScores.resetAll()
            }
            .padding(horizontal = 24.dp, vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(R.drawable.ic_trash),
                contentDescription = null,
                modifier = Modifier.padding(end = 6.dp).size(18.dp)
            )
            Text(
                text = stringResource(R.string.reset_scores),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        }
    }
    Text(
        text = stringResource(R.string.best, HighScores.best(HighScores.Games.BUBBLES)) +
            "  ·  " + stringResource(R.string.best, HighScores.best(HighScores.Games.STARS)),
        fontSize = 13.sp,
        modifier = Modifier.padding(top = 8.dp)
    )
}

/** En-tête de section avec icône IA (remplace les anciens emoji). */
@Composable
private fun IconHeader(iconRes: Int, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.padding(end = 8.dp).size(24.dp)
        )
        Text(text = text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

/** Petit drapeau pastel dessiné pour chaque langue. */
@Composable
private fun LanguageFlag(tag: String, modifier: Modifier = Modifier) {
    val blue = Color(0xFF8AB0DE)
    val red = Color(0xFFE8989E)
    val white = Color(0xFFFBF7FA)
    val green = Color(0xFF9BD3A0)
    val yellow = Color(0xFFF5E08C)
    Box(
        modifier = modifier
            .size(width = 34.dp, height = 24.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(white)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            fun vBand(i: Int, c: Color) = drawRect(c, Offset(w / 3 * i, 0f), Size(w / 3, h))
            fun hBand(top: Float, bot: Float, c: Color) =
                drawRect(c, Offset(0f, h * top), Size(w, h * (bot - top)))
            when (tag) {
                "fr" -> { vBand(0, blue); vBand(1, white); vBand(2, red) }
                "it" -> { vBand(0, green); vBand(1, white); vBand(2, red) }
                "ru" -> { hBand(0f, 1f / 3, white); hBand(1f / 3, 2f / 3, blue); hBand(2f / 3, 1f, red) }
                "es" -> { hBand(0f, 0.28f, red); hBand(0.28f, 0.72f, yellow); hBand(0.72f, 1f, red) }
                "en" -> {
                    // Croix de Saint-Georges (rouge sur blanc)
                    drawRect(red, Offset(w * 0.42f, 0f), Size(w * 0.16f, h))
                    drawRect(red, Offset(0f, h * 0.38f), Size(w, h * 0.24f))
                }
            }
        }
    }
}
