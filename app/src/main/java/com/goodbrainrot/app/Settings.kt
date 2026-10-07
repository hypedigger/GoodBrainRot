package com.goodbrainrot.app

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import com.goodbrainrot.app.audio.GameMusic
import com.goodbrainrot.app.audio.SoundEngine

/**
 * Préférences de l'application, exposées en états Compose pour que les écrans
 * se recomposent quand une option change. Persistance : SharedPreferences.
 */
object Settings {
    private lateinit var sp: SharedPreferences
    private var initialized = false

    val babyMode = mutableStateOf(false)
    val pianoRowsPortrait = mutableIntStateOf(2)
    val pianoKeysPerRowPortrait = mutableIntStateOf(8)
    val pianoRowsLandscape = mutableIntStateOf(1)
    val pianoKeysPerRowLandscape = mutableIntStateOf(15)
    val sayAnimalName = mutableStateOf(true)
    // Affichage des animaux/contenus : true = photo, false = emoji/dessin
    val useContentPhotos = mutableStateOf(true)
    // Sons d'instruments : true = échantillons réalistes (si présents), false = synthèse
    val realisticInstruments = mutableStateOf(true)

    // Volumes du shmup Crimson Sky (0..1) : musique / tirs / explosions / objets ramassés
    val musicVolume = mutableFloatStateOf(0.85f)
    val shotVolume = mutableFloatStateOf(0.85f)
    val explosionVolume = mutableFloatStateOf(0.85f)
    val pickupVolume = mutableFloatStateOf(0.55f)
    // Intensité du décor de niveau (0..1) : plus haut = fond plus lumineux/visible
    val bgIntensity = mutableFloatStateOf(0.6f)
    // Taille des mid-boss (1.0 = 100% .. 10.0 = 1000%)
    val midBossScale = mutableFloatStateOf(1.0f)
    // Copie SIMPLE (hors Compose) lue par le rendu Canvas du mid-boss : evite toute subtilite de
    // snapshot en DrawScope. Toujours synchronisee avec midBossScale.
    @Volatile @JvmField var midBossScaleLive: Float = 1.0f

    // --- Réglages de gameplay Crimson Sky (menu configuration) ---
    // Vies de départ en mode scoré (1..9)
    val shmupLives = mutableIntStateOf(3)
    // Multiplicateur de la valeur des trésors/pièces (0.25 .. 5.0)
    val shmupTreasureMult = mutableFloatStateOf(1.0f)
    // Fréquence des power-ups semés dans le niveau (0.25 .. 3.0)
    val shmupPowerupFreq = mutableFloatStateOf(1.0f)
    // Probabilité qu'un destructible lâche une pièce (0 .. 1)
    val shmupCoinDrop = mutableFloatStateOf(0.5f)
    // Fraction du sprite ennemi qui doit être visible à l'entrée avant qu'il soit vulnérable (0..1)
    val enemyEntryVuln = mutableFloatStateOf(0.35f)

    // Vroum Vrouuuuuuum : mode sans fin débloqué une fois le niveau 1 terminé
    val vroumEndlessUnlocked = mutableStateOf(false)
    // Étoiles par circuit : clé "<thème>_<c|a><difficulté>" (c = Course, a = Arcade) -> 0..3, on garde le meilleur
    val vroumStars = mutableStateOf<Map<String, Int>>(emptyMap())

    // Jardinier Zen : pièces, paliers d'outils débloqués (manuel / électrique), outil actif, streak
    val gardenCoins = mutableIntStateOf(0)
    val gardenManualTier = mutableIntStateOf(0)      // 0..5 = dernier palier acheté (0 = coupe-ongles)
    val gardenElectricTier = mutableIntStateOf(-1)   // -1 = filière pas encore ouverte
    val gardenActiveTool = mutableIntStateOf(0)      // 0..5 manuel, 10..15 électrique
    val gardenStreak = mutableIntStateOf(0)
    val gardenLastDay = mutableStateOf("")           // yyyy-MM-dd de la dernière partie
    val gardenDailyBonusDay = mutableStateOf("")     // jour où le bonus x2 a déjà été pris

    // Réglages communs à tous les quiz
    // Difficulté auto : le nombre de choix suit le niveau de l'enfant (Profiles.level)
    val quizAutoDifficulty = mutableStateOf(true)
    val quizChoices = mutableIntStateOf(6)
    val quizQuestions = mutableIntStateOf(10)
    val quizPointsCorrect = mutableIntStateOf(5)
    val quizPointsWrong = mutableIntStateOf(1)

    // Jeux d'arcade : durée d'une partie en secondes (10..180)
    val bubblesDuration = mutableIntStateOf(30)
    val starsDuration = mutableIntStateOf(30)

    // Guitare : type (index dans GuitarTypes.ALL) + cordes + frettes (manche)
    val guitarTypeIndex = mutableIntStateOf(0)
    val guitarStrings = mutableIntStateOf(6)
    val guitarFrets = mutableIntStateOf(12)

    fun init(context: Context) {
        if (initialized) return
        initialized = true
        sp = context.applicationContext.getSharedPreferences("goodbrainrot", Context.MODE_PRIVATE)
        babyMode.value = sp.getBoolean("baby_mode", false)
        pianoRowsPortrait.intValue = sp.getInt("piano_rows_portrait", 2)
        pianoKeysPerRowPortrait.intValue = sp.getInt("piano_keys_portrait", 8)
        pianoRowsLandscape.intValue = sp.getInt("piano_rows_landscape", 1)
        pianoKeysPerRowLandscape.intValue = sp.getInt("piano_keys_landscape", 15)
        sayAnimalName.value = sp.getBoolean("say_animal_name", true)
        useContentPhotos.value = sp.getBoolean("use_content_photos", true)
        realisticInstruments.value = sp.getBoolean("realistic_instruments", true)
        musicVolume.floatValue = sp.getFloat("vol_music", 0.85f)
        shotVolume.floatValue = sp.getFloat("vol_shot", 0.85f)
        explosionVolume.floatValue = sp.getFloat("vol_explosion", 0.85f)
        pickupVolume.floatValue = sp.getFloat("vol_pickup", 0.55f)
        bgIntensity.floatValue = sp.getFloat("bg_intensity", 0.6f)
        midBossScale.floatValue = sp.getFloat("midboss_scale", 1.0f)
        midBossScaleLive = midBossScale.floatValue
        shmupLives.intValue = sp.getInt("shmup_lives", 3)
        shmupTreasureMult.floatValue = sp.getFloat("shmup_treasure_mult", 1.0f)
        shmupPowerupFreq.floatValue = sp.getFloat("shmup_powerup_freq", 1.0f)
        shmupCoinDrop.floatValue = sp.getFloat("shmup_coin_drop", 0.5f)
        enemyEntryVuln.floatValue = sp.getFloat("enemy_entry_vuln", 0.35f)
        applyShmupVolumes()
        vroumEndlessUnlocked.value = sp.getBoolean("vroum_endless", false)
        vroumStars.value = (sp.getString("vroum_stars", "") ?: "").split("|").mapNotNull { e ->
            val p = e.split(":"); if (p.size == 2) p[1].toIntOrNull()?.let { p[0] to it } else null
        }.toMap()
        gardenCoins.intValue = sp.getInt("garden_coins", 0)
        gardenManualTier.intValue = sp.getInt("garden_manual", 0)
        gardenElectricTier.intValue = sp.getInt("garden_electric", -1)
        gardenActiveTool.intValue = sp.getInt("garden_tool", 0)
        gardenStreak.intValue = sp.getInt("garden_streak", 0)
        gardenLastDay.value = sp.getString("garden_last_day", "") ?: ""
        gardenDailyBonusDay.value = sp.getString("garden_bonus_day", "") ?: ""
        quizAutoDifficulty.value = sp.getBoolean("quiz_auto_difficulty", true)
        quizChoices.intValue = sp.getInt("quiz_choices", 6)
        quizQuestions.intValue = sp.getInt("quiz_questions", 10)
        quizPointsCorrect.intValue = sp.getInt("quiz_points_correct", 5)
        quizPointsWrong.intValue = sp.getInt("quiz_points_wrong", 1)
        bubblesDuration.intValue = sp.getInt("bubbles_duration", 30)
        starsDuration.intValue = sp.getInt("stars_duration", 30)
        guitarTypeIndex.intValue = sp.getInt("guitar_type", 0)
        guitarStrings.intValue = sp.getInt("guitar_strings", 6)
        guitarFrets.intValue = sp.getInt("guitar_frets", 12)
    }

    fun setBabyMode(value: Boolean) {
        babyMode.value = value
        sp.edit().putBoolean("baby_mode", value).apply()
    }

    /**
     * Pousse les volumes du shmup vers les moteurs audio.
     * Les SFX suivent une courbe quadratique (v²) : un réglage bas est VRAIMENT faible, pour que
     * la musique reste au-dessus des tirs/explosions/objets même quand ceux-ci sont à 10 %.
     */
    fun applyShmupVolumes() {
        GameMusic.musicVolume = musicVolume.floatValue
        GameMusic.applyVolume()
        SoundEngine.shotVolume = shotVolume.floatValue * shotVolume.floatValue
        SoundEngine.explosionVolume = explosionVolume.floatValue * explosionVolume.floatValue
        SoundEngine.pickupVolume = pickupVolume.floatValue * pickupVolume.floatValue
    }

    fun setMusicVolume(v: Float) {
        musicVolume.floatValue = v; sp.edit().putFloat("vol_music", v).apply(); applyShmupVolumes()
    }
    fun setShotVolume(v: Float) {
        shotVolume.floatValue = v; sp.edit().putFloat("vol_shot", v).apply(); applyShmupVolumes()
    }
    fun setExplosionVolume(v: Float) {
        explosionVolume.floatValue = v; sp.edit().putFloat("vol_explosion", v).apply(); applyShmupVolumes()
    }
    fun setPickupVolume(v: Float) {
        pickupVolume.floatValue = v; sp.edit().putFloat("vol_pickup", v).apply(); applyShmupVolumes()
    }
    fun setBgIntensity(v: Float) {
        bgIntensity.floatValue = v; sp.edit().putFloat("bg_intensity", v).apply()
    }
    fun setMidBossScale(v: Float) {
        midBossScale.floatValue = v; midBossScaleLive = v; sp.edit().putFloat("midboss_scale", v).apply()
    }
    fun setShmupLives(v: Int) {
        shmupLives.intValue = v; sp.edit().putInt("shmup_lives", v).apply()
    }
    fun setShmupTreasureMult(v: Float) {
        shmupTreasureMult.floatValue = v; sp.edit().putFloat("shmup_treasure_mult", v).apply()
    }
    fun setShmupPowerupFreq(v: Float) {
        shmupPowerupFreq.floatValue = v; sp.edit().putFloat("shmup_powerup_freq", v).apply()
    }
    fun setShmupCoinDrop(v: Float) {
        shmupCoinDrop.floatValue = v; sp.edit().putFloat("shmup_coin_drop", v).apply()
    }
    fun setEnemyEntryVuln(v: Float) {
        enemyEntryVuln.floatValue = v; sp.edit().putFloat("enemy_entry_vuln", v).apply()
    }

    fun setPianoRowsPortrait(value: Int) {
        pianoRowsPortrait.intValue = value
        sp.edit().putInt("piano_rows_portrait", value).apply()
    }

    fun setPianoKeysPerRowPortrait(value: Int) {
        pianoKeysPerRowPortrait.intValue = value
        sp.edit().putInt("piano_keys_portrait", value).apply()
    }

    fun setPianoRowsLandscape(value: Int) {
        pianoRowsLandscape.intValue = value
        sp.edit().putInt("piano_rows_landscape", value).apply()
    }

    fun setPianoKeysPerRowLandscape(value: Int) {
        pianoKeysPerRowLandscape.intValue = value
        sp.edit().putInt("piano_keys_landscape", value).apply()
    }

    fun setSayAnimalName(value: Boolean) {
        sayAnimalName.value = value
        sp.edit().putBoolean("say_animal_name", value).apply()
    }

    fun setUseContentPhotos(value: Boolean) {
        useContentPhotos.value = value
        sp.edit().putBoolean("use_content_photos", value).apply()
    }

    fun setRealisticInstruments(value: Boolean) {
        realisticInstruments.value = value
        sp.edit().putBoolean("realistic_instruments", value).apply()
    }

    /** Mémorise le meilleur nombre d'étoiles pour un circuit (ne régresse jamais). */
    fun setVroumStars(key: String, stars: Int) {
        if (stars <= (vroumStars.value[key] ?: 0)) return
        val m = vroumStars.value.toMutableMap(); m[key] = stars; vroumStars.value = m
        sp.edit().putString("vroum_stars", m.entries.joinToString("|") { "${it.key}:${it.value}" }).apply()
    }
    /** Fantôme du meilleur temps d'une Course : "véhicule;temps;d0:l0,d1:l1,..." (dist en dm, voie, un échantillon / 0,25 s). */
    fun vroumGhost(key: String): String? = sp.getString("vroum_ghost_$key", null)
    fun setVroumGhost(key: String, v: String) = sp.edit().putString("vroum_ghost_$key", v).apply()

    fun setGardenCoins(v: Int) { gardenCoins.intValue = v; sp.edit().putInt("garden_coins", v).apply() }
    fun setGardenManualTier(v: Int) { gardenManualTier.intValue = v; sp.edit().putInt("garden_manual", v).apply() }
    fun setGardenElectricTier(v: Int) { gardenElectricTier.intValue = v; sp.edit().putInt("garden_electric", v).apply() }
    fun setGardenActiveTool(v: Int) { gardenActiveTool.intValue = v; sp.edit().putInt("garden_tool", v).apply() }
    fun setGardenStreak(v: Int, day: String) {
        gardenStreak.intValue = v; gardenLastDay.value = day
        sp.edit().putInt("garden_streak", v).putString("garden_last_day", day).apply()
    }
    fun setGardenDailyBonusDay(day: String) { gardenDailyBonusDay.value = day; sp.edit().putString("garden_bonus_day", day).apply() }

    fun setVroumEndlessUnlocked(value: Boolean) {
        vroumEndlessUnlocked.value = value
        sp.edit().putBoolean("vroum_endless", value).apply()
    }

    fun setQuizAutoDifficulty(value: Boolean) {
        quizAutoDifficulty.value = value
        sp.edit().putBoolean("quiz_auto_difficulty", value).apply()
    }

    fun setQuizChoices(value: Int) {
        quizChoices.intValue = value
        sp.edit().putInt("quiz_choices", value).apply()
    }

    fun setQuizQuestions(value: Int) {
        quizQuestions.intValue = value
        sp.edit().putInt("quiz_questions", value).apply()
    }

    fun setQuizPointsCorrect(value: Int) {
        quizPointsCorrect.intValue = value
        sp.edit().putInt("quiz_points_correct", value).apply()
    }

    fun setQuizPointsWrong(value: Int) {
        quizPointsWrong.intValue = value
        sp.edit().putInt("quiz_points_wrong", value).apply()
    }

    fun setBubblesDuration(value: Int) {
        bubblesDuration.intValue = value
        sp.edit().putInt("bubbles_duration", value).apply()
    }

    fun setStarsDuration(value: Int) {
        starsDuration.intValue = value
        sp.edit().putInt("stars_duration", value).apply()
    }

    fun setGuitarTypeIndex(value: Int) {
        guitarTypeIndex.intValue = value
        sp.edit().putInt("guitar_type", value).apply()
    }

    fun setGuitarStrings(value: Int) {
        guitarStrings.intValue = value
        sp.edit().putInt("guitar_strings", value).apply()
    }

    fun setGuitarFrets(value: Int) {
        guitarFrets.intValue = value
        sp.edit().putInt("guitar_frets", value).apply()
    }
}

/** Types de quiz gérés par le moteur générique. */
enum class QuizType {
    ANIMAL, NUMBER, LETTER, COLOR, SHAPE, COUNT, SOUND, MAMA,
    // Quiz « devine » à base d'images de contenu
    IMG_ANIMAL, IMG_VEHICLE, IMG_DINO, IMG_MIXED
}
