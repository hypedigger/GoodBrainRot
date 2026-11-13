package com.goodbrainrot.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.goodbrainrot.app.Haptics
import com.goodbrainrot.app.R
import com.goodbrainrot.app.Settings
import com.goodbrainrot.app.audio.GameMusic
import com.goodbrainrot.app.audio.Sfx
import com.goodbrainrot.app.audio.SoundEngine
import com.goodbrainrot.app.audio.Tts
import com.goodbrainrot.app.data.AnimalSoundPlayer
import com.goodbrainrot.app.data.Animals
import com.goodbrainrot.app.data.HighScores
import com.goodbrainrot.app.data.VehicleSounds
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

// ===========================================================================
// Vroum Vrouuuuuuum — jeu de voiture 2D/3D pour les 5-7 ans.
// Modèle de jeu en MÈTRES (z devant la caméra, x latéral, y hauteur), rendu VRAI 3D
// OpenGL ES dans VroumGl.kt, menus/HUD Compose au design « appli enfants » moderne.
// Flux : choix du bolide -> choix de la route -> décompte 3-2-1 -> partie.
// ===========================================================================


private enum class VPhase { MODE, SELECT, LEVEL, PLAYING, DUEL, CLEAR, END, CUP_END }

internal class VSprites(private val ctx: android.content.Context) {
    private fun load(name: String): ImageBitmap? {
        val id = ctx.resources.getIdentifier(name, "drawable", ctx.packageName)
        return if (id != 0) try { ImageBitmap.imageResource(ctx.resources, id) } catch (_: Exception) { null } else null
    }
    val vehicles = VEHICLES.map { load("vr_${it.key}") ?: load("vv_${it.key}") }
    /** bolides vus de FACE (garage, photo finish) ; null si le sprite n'existe pas encore */
    val fronts = VEHICLES.map { load("vf_${it.key}") }
    /** icônes des objets (case d'objet du HUD) */
    val items = VWeapon.values().associateWith { if (it.tex.isEmpty()) null else load(it.tex) }
    val title = load("vv_title"); val village = load("vv_village"); val empty = load("vv_empty")
    val cards = THEMES.map { load(it.card) }
    /** trophées des coupes du Championnat */
    val cups = CUPS.map { load("vv_cup_${it.key}") }
}

// ---------------------------------------------------------------------------
// Écran
// ---------------------------------------------------------------------------

@Composable
fun VroumScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val spr = remember { VSprites(ctx) }
    var phase by remember { mutableStateOf(VPhase.MODE) }
    // menu façon Mario Kart : mode de jeu, nombre de joueurs, bolide(s), puis coupe ou circuit
    var mode by remember { mutableStateOf(VMode.CUP) }
    var players by remember { mutableIntStateOf(1) }
    var garageStep by remember { mutableIntStateOf(1) }      // 1 = bolide du joueur 1, 2 = bolide du joueur 2
    var vehicle2 by remember { mutableIntStateOf(1) }
    // championnat : coupe en cours, n° de course (0..3), étoiles et scores des courses déjà faites
    var cupIdx by remember { mutableIntStateOf(0) }
    var cupRace by remember { mutableIntStateOf(0) }
    val cupStars = remember { IntArray(4) }; val cupScores = remember { IntArray(4) }
    // peloton de la coupe : les mêmes 7 concurrents sur les 4 courses (du plus fort au plus faible), points par place
    var cupCast by remember { mutableStateOf(IntArray(0)) }
    val cupPts = remember { IntArray(8) }                     // 0..6 = concurrents, 7 = joueur
    var cupRows by remember { mutableStateOf<List<VStandRow>>(emptyList()) }
    var vehicle by remember { mutableIntStateOf(0) }
    var endless by remember { mutableStateOf(false) }
    var duelMode by remember { mutableStateOf(false) }     // mode 2 joueurs sur écran partagé
    var diff by remember { mutableIntStateOf(1) }          // 0 facile, 1 moyen, 2 difficile
    // tutoriel de la première partie : 0 changer de voie, 1 sauter, 2 bonus/dangers, -1 fini
    var tutoStep by remember { mutableIntStateOf(if (Settings.vroumTutoSeen.value) -1 else 0) }
    var themeIdx by remember { mutableIntStateOf(0) }      // circuit : forêt, nuit, volcan, Tokyo, neige
    var runToken by remember { mutableIntStateOf(0) }
    var tick by remember { mutableIntStateOf(0) }
    var countdown by remember { mutableIntStateOf(-1) }    // 3,2,1 puis 0 = GO affiché brièvement, -1 = rien
    val synthRes = remember { ctx.resources.getIdentifier("vv_synth", "raw", ctx.packageName) }
    val frenzyRes = remember { ctx.resources.getIdentifier("vv_frenzy", "raw", ctx.packageName) }
    val boostRes = remember { ctx.resources.getIdentifier("vv_boost", "raw", ctx.packageName).let { if (it != 0) it else ctx.resources.getIdentifier("vv_frenzy", "raw", ctx.packageName) } }
    val gameKey = if (endless) HighScores.Games.VROUM_ENDLESS else HighScores.Games.VROUM
    val controller = remember(runToken, gameKey) { ArcadeController(gameKey, 0, withLevels = false, speedRamp = false) }
    val trickLabel = stringResource(R.string.vroum_trick); val cannonLabel = stringResource(R.string.vroum_cannons)
    val lanesLabel = stringResource(R.string.vroum_lanes); val superLabel = stringResource(R.string.vroum_superboost)
    val jumpedLabel = stringResource(R.string.vroum_jumped)
    val nearLabel = stringResource(R.string.vroum_near); val maxLabel = stringResource(R.string.vroum_flow_max)
    val vLabels = listOf(R.string.vroum_takedown, R.string.vroum_strike, R.string.vroum_revenge, R.string.vroum_stomp, R.string.vroum_crash, R.string.vroum_shove,
        R.string.vroum_ghost, R.string.vroum_wind, R.string.vroum_w_bomb, R.string.vroum_w_laser, R.string.vroum_w_flame, R.string.vroum_w_magnet, R.string.vroum_w_shield, R.string.vroum_rocket, R.string.vroum_finish, R.string.vroum_draft, R.string.vroum_final_stretch).map { stringResource(it) }
    // tous les libellés traduits d'une partie (solo comme duel)
    val applyLabels: (VroumGame) -> Unit = { g ->
        g.lanesLabel = lanesLabel; g.superLabel = superLabel; g.jumpedLabel = jumpedLabel; g.nearLabel = nearLabel; g.maxLabel = maxLabel
        g.takedownLabel = vLabels[0]; g.strikeLabel = vLabels[1]; g.revengeLabel = vLabels[2]; g.stompLabel = vLabels[3]; g.crashLabel = vLabels[4]
        g.shoveLabel = vLabels[5]; g.ghostLabel = vLabels[6]; g.windLabel = vLabels[7]; g.weaponLabels = vLabels.subList(8, 13); g.rocketLabel = vLabels[13]
        g.finishLabel = vLabels[14]; g.draftLabel = vLabels[15]; g.stretchLabel = vLabels[16]
    }
    val game = remember(runToken) {
        if (phase != VPhase.PLAYING) null
        else VroumGame(endless, vehicle, diff, THEMES[themeIdx], trickLabel, cannonLabel,
            cast = if (mode == VMode.CUP && cupCast.size == 7) cupCast else null,
            // le RIVAL de la coupe colle à notre score : il pousse si on le distance au classement, il lève le pied s'il s'envole
            rivalBoost = if (mode == VMode.CUP) 1f + ((cupPts[7] - cupPts[0]) / 8f).coerceIn(-1f, 1f) * 0.025f else 1f).also { g ->
            applyLabels(g)
            // charge le fantôme pour ce circuit / cette difficulté : meilleur temps en Course, distance max en Arcade
            Settings.vroumGhost(if (endless) "${THEMES[themeIdx].key}_a$diff" else "${THEMES[themeIdx].key}_$diff")?.let { s ->
                val p = s.split(";")
                if (p.size >= 3) {
                    g.ghostVeh = p[0].toIntOrNull() ?: -1
                    g.ghostTime = p[1].toFloatOrNull() ?: 0f
                    val pts = p[2].split(",")
                    val d = IntArray(pts.size); val l = IntArray(pts.size)
                    pts.forEachIndexed { i, t -> val q = t.split(":"); d[i] = q[0].toIntOrNull() ?: 0; l[i] = q.getOrNull(1)?.toIntOrNull() ?: 1 }
                    if (d.size > 8) { g.ghostD = d; g.ghostL = l }
                }
            }
        }
    }
    val glView = remember(game) { game?.let { VroumGlView(ctx, it) } }

    DisposableEffect(Unit) { onDispose { GameMusic.stop(); VehicleSounds.stop(); AnimalSoundPlayer.stop() } }
    DisposableEffect(glView) { onDispose { glView?.onPause() } }
    // économie de batterie : met le thread GL en pause quand l'app passe en arrière-plan (écran éteint, home…)
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(glView, lifecycleOwner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
            when (e) {
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> glView?.onPause()
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> glView?.onResume()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }
    LaunchedEffect(phase) {
        glView?.renderer?.paused = phase != VPhase.PLAYING
        if (phase != VPhase.PLAYING) GameMusic.setSpeed(1f)
        // menus : synthwave ; course à 2 joueurs : la musique du circuit, comme en solo
        val duelRes = if (phase == VPhase.DUEL) ctx.resources.getIdentifier(vroumMusicKey(THEMES[themeIdx].key), "raw", ctx.packageName) else 0
        if (phase != VPhase.PLAYING) { val r = if (duelRes != 0) duelRes else synthRes; if (r != 0) GameMusic.playRes(ctx, r) }
    }

    val cheers = listOf(R.string.vroum_cheer1, R.string.vroum_cheer2, R.string.vroum_cheer3)
    // Résultat de la partie (écran enfant : étoiles + confettis, pas de saisie de nom façon arcade)
    var resScore by remember { mutableIntStateOf(0) }
    var resDist by remember { mutableIntStateOf(0) }
    var resCleared by remember { mutableStateOf(false) }
    var resRecord by remember { mutableStateOf(false) }
    var resTimeUp by remember { mutableStateOf(false) }
    var resTakedowns by remember { mutableIntStateOf(0) }
    var resPlace by remember { mutableIntStateOf(0) }
    var resResults by remember { mutableStateOf<List<VResult>>(emptyList()) }
    fun startCupField() {
        cupCast = VEHICLES.indices.filter { it != vehicle }.shuffled(Random(cupIdx * 131L + vehicle * 7L + diff)).take(7).toIntArray()
        cupPts.fill(0); cupRows = emptyList()
    }
    fun finishRun(g: VroumGame, cleared: Boolean) {
        // la distance affichée s'arrête à la ligne (le bolide roule encore jusqu'aux tribunes après l'avoir franchie)
        resScore = g.score; resDist = if (cleared) VroumGame.LEVEL_LEN.toInt() else g.dist.toInt(); resCleared = cleared; resTimeUp = g.timeUp; resTakedowns = g.takedowns
        val raced = cleared && g.racers.isNotEmpty()
        resPlace = if (raced) g.place else 0; resResults = if (raced) g.raceResults() else emptyList()
        val best = HighScores.best(gameKey)
        // le mode Facile (invincible) n'entre pas au tableau des records
        resRecord = g.diff >= 1 && g.score > 0 && g.score > best
        if (g.diff >= 1) HighScores.record(gameKey, g.score)
        // étoiles mémorisées par circuit + mode + difficulté (affichées sur les cartes)
        Settings.setVroumStars("${g.theme.key}_${if (g.endless) "a" else "c"}${g.diff}", vroumStarCount(g.endless, cleared, g.score, g.dist.toInt(), g.diff, resPlace))
        // fantôme : en Course ma trace remplace l'ancienne si je finis plus vite ; en Arcade si je vais plus loin
        if (!g.endless && cleared) {
            val key = "${g.theme.key}_${g.diff}"
            val (d, l, time) = g.ghostRecording()
            val prevTime = Settings.vroumGhost(key)?.split(";")?.getOrNull(1)?.toFloatOrNull()
            if (d.size > 8 && (prevTime == null || time < prevTime))
                Settings.setVroumGhost(key, "${g.vehicle};${"%.2f".format(java.util.Locale.US, time)};" + d.indices.joinToString(",") { "${d[it]}:${l[it]}" })
        } else if (g.endless) {
            val key = "${g.theme.key}_a${g.diff}"
            val (d, l, _) = g.ghostRecording()
            val prevDist = Settings.vroumGhost(key)?.split(";")?.getOrNull(1)?.toFloatOrNull()
            if (d.size > 8 && (prevDist == null || g.dist > prevDist))
                Settings.setVroumGhost(key, "${g.vehicle};${"%.1f".format(java.util.Locale.US, g.dist)};" + d.indices.joinToString(",") { "${d[it]}:${l[it]}" })
        }
        if (tutoStep >= 0) { tutoStep = -1; Settings.setVroumTutoSeen(true) }
        if (mode == VMode.CUP) {
            cupStars[cupRace] = vroumStarCount(false, cleared, g.score, g.dist.toInt(), g.diff, resPlace); cupScores[cupRace] = g.score
            cupRows = cupStandings(cupPts, cupCast, vehicle, resResults)
        }
        phase = VPhase.END
    }
    fun hornFor(v: Int) {
        val fallback = { SoundEngine.playRealHorn(if (v == 1 || v == 4) 0.7f else 1f); Unit }
        when (v) {
            0 -> VehicleSounds.play(ctx, "car", 1500L, fallback)
            2 -> VehicleSounds.play(ctx, "fire_truck", 2200L, fallback)
            3 -> VehicleSounds.play(ctx, "motorcycle", 1600L, fallback)
            4, 9 -> VehicleSounds.play(ctx, "tractor", 1600L, fallback)
            10 -> if (!Sfx.play("vv_beep")) VehicleSounds.play(ctx, "tractor", 1600L, fallback)   // pelleteuse : bip-bip de recul
            11 -> if (!Sfx.play("vv_bear")) Animals.byKey("bear")?.let { AnimalSoundPlayer.play(ctx, it, sayNameAfter = false) } ?: fallback()
            8 -> VehicleSounds.play(ctx, "plane", 1800L, fallback)
            6 -> if (!SoundEngine.playRoar()) fallback()
            5, 7 -> if (!SoundEngine.playRoar()) Animals.byKey("elephant")?.let { AnimalSoundPlayer.play(ctx, it, sayNameAfter = false) } ?: fallback()
            else -> fallback()
        }
    }

    // dernière bulle du tutoriel (bonus / dangers) : disparaît toute seule après 4 s
    LaunchedEffect(tutoStep) {
        if (tutoStep == 2) { delay(7000); if (tutoStep == 2) { tutoStep = -1; Settings.setVroumTutoSeen(true) } }
    }
    LaunchedEffect(game) {
        val g = game ?: return@LaunchedEffect
        countdown = 3
        GameMusic.setSpeed(1f)
        // chaque famille de circuits a sa musique (repli sur la synthwave si le fichier manque)
        val raceRes = ctx.resources.getIdentifier(vroumMusicKey(g.theme.key), "raw", ctx.packageName).let { if (it != 0) it else synthRes }
        if (raceRes != 0) GameMusic.playRes(ctx, raceRes)
        SoundEngine.playVvTick(0.8f)
        var engine = 0
        var engineCd = 0f
        var fxCd = 0f
        var roar = 0
        var sndCd = 0f
        try {
            while (true) {
                withFrameNanos { }
                while (true) {
                    val e = g.events.poll() ?: break
                    when (e) {
                        "count" -> { countdown = kotlin.math.ceil(g.introT.toDouble()).toInt().coerceIn(1, 3); SoundEngine.playVvTick(0.8f + (3 - countdown) * 0.15f) }
                        // moteur en boucle seulement pour les engins à moteur (pas pour le dragon, le lion, le T-Rex)
                        "go" -> { countdown = 0; SoundEngine.playVvGo(); if (VEHICLES[g.vehicle].key !in CREATURE_KEYS) engine = SoundEngine.startEngine() }
                        "lane" -> { if (tutoStep == 0) tutoStep = 1 }   // le tutoriel avance quand l'enfant a changé de voie
                        "widen" -> { SoundEngine.playVvSuccess(); Haptics.tap(ctx) }
                        "jump" -> { SoundEngine.playJump(); if (tutoStep == 1) tutoStep = 2 }
                        "land" -> SoundEngine.playLand()
                        "landBlast" -> { SoundEngine.playBoom(); SoundEngine.playVvYahoo(); Haptics.tap(ctx) }
                        "trick" -> SoundEngine.playVvCoin()
                        // frôlement : petit « ding » aigu + vibration courte ; élan au maximum : fanfare brève
                        "near" -> { SoundEngine.playVvTick(1.5f); SoundEngine.playVvCoin(); Haptics.tap(ctx) }
                        "flowMax" -> SoundEngine.playVvSuccess()
                        // ---- Burnout : takedowns, boost, rivaux
                        // (pas plus d'un bruit de tôle toutes les 90 ms : le laser ou une bombe en déclenchent des rafales)
                        "takedown", "zap" -> if (sndCd <= 0f) { sndCd = 0.09f; SoundEngine.playCrush(); if (e == "takedown") Haptics.tap(ctx) }
                        "takedownBig" -> { SoundEngine.playCrush(); SoundEngine.playBoom(); Haptics.tap(ctx) }
                        "strike" -> { SoundEngine.playBoom(); SoundEngine.playVvCoin(); Haptics.tap(ctx) }
                        "stomp" -> SoundEngine.playCrush()
                        "bounce" -> { SoundEngine.playJump(); SoundEngine.playVvTick(1.0f + 0.12f * g.stompChain.coerceAtMost(8)); Haptics.tap(ctx) }
                        "revenge" -> { SoundEngine.playVvYahoo(); SoundEngine.playVvFanfare() }
                        "segUp" -> SoundEngine.playVvSuccess()
                        "rocketStart" -> { SoundEngine.playTurbo(); SoundEngine.playVvYahoo(); Haptics.success(ctx) }
                        "boostFull" -> SoundEngine.playVvTick(1.4f)
                        // le moteur HURLE tant qu'on tient le boost (vrai enregistrement de voiture de course, en boucle)
                        "boostOn" -> { SoundEngine.playTurbo(); Haptics.tap(ctx); Sfx.stopStream(roar); roar = Sfx.loop("vv_boostloop", 0.9f, 0.95f) }
                        "boostOff" -> { Sfx.stopStream(roar); roar = 0 }
                        "bumpSoft", "rivalHit" -> { SoundEngine.playBump(); Haptics.tap(ctx) }
                        "shove" -> { SoundEngine.playCrush(); Haptics.tap(ctx) }
                        "rivalIn" -> SoundEngine.playRealHorn(1.25f)
                        "rivalWarn" -> { SoundEngine.playVvTick(0.6f); SoundEngine.playRealHorn(0.8f) }
                        "ghost" -> SoundEngine.playVvCoin()
                        // ---- Positions : on double / on est doublé, aspiration, dernière ligne droite
                        "posUp" -> { SoundEngine.playVvTick(1.7f); SoundEngine.playVvCoin(); Haptics.tap(ctx) }
                        "posDown" -> SoundEngine.playVvTick(0.45f)
                        "draft" -> { SoundEngine.playTurbo(); Haptics.tap(ctx) }
                        "finalStretch" -> { GameMusic.setSpeed(1.13f); Sfx.play("vv_applause", 0.75f, 1.05f); SoundEngine.playVvFanfare() }
                        // ---- Mario Kart : boîtes à objets et armes
                        "box" -> SoundEngine.playPick(3)
                        "ramp" -> SoundEngine.playJump()
                        "bombArm" -> SoundEngine.playJump()
                        "bomb" -> { SoundEngine.playBoom(); SoundEngine.playCrush(); SoundEngine.playVvYahoo(); Haptics.success(ctx) }
                        "laser", "flame" -> SoundEngine.playTurbo()
                        "magnet", "shield" -> SoundEngine.playVvSuccess()
                        "shieldPop" -> { SoundEngine.playSplash(); SoundEngine.playVvTick(0.7f) }
                        "windWarn" -> SoundEngine.playVvTick(0.7f)
                        "wind" -> SoundEngine.playSplash()
                        "horn" -> hornFor(g.vehicle)
                        "bump" -> { SoundEngine.playBump(); Haptics.tap(ctx) }
                        "crush" -> { SoundEngine.playCrush(); Haptics.tap(ctx) }
                        "shot" -> SoundEngine.playShot()
                        "shotHit" -> SoundEngine.playCrush()
                        "cannon" -> SoundEngine.playVvSuccess()
                        "splash" -> SoundEngine.playSplash()
                        "turbo" -> SoundEngine.playTurbo()
                        "superboost" -> { SoundEngine.playTurbo(); SoundEngine.playVvYahoo(); Haptics.tap(ctx); if (boostRes != 0) GameMusic.playRes(ctx, boostRes) }
                        "boostEnd" -> { if (g.giant > 0 && frenzyRes != 0) GameMusic.playRes(ctx, frenzyRes) else if (raceRes != 0) GameMusic.playRes(ctx, raceRes) }
                        "fuel" -> SoundEngine.playFuel()
                        "pick1" -> SoundEngine.playPick(1)
                        "pick2" -> SoundEngine.playPick(2)
                        "pick3" -> SoundEngine.playPick(3)
                        "passenger" -> SoundEngine.playVvSuccess()
                        "giant1", "giant2" -> { SoundEngine.playStarPick(); if (frenzyRes != 0) GameMusic.playRes(ctx, frenzyRes) }
                        "giantEnd" -> { SoundEngine.playBoom(); if (g.superT > 0f && boostRes != 0) GameMusic.playRes(ctx, boostRes) else if (raceRes != 0) GameMusic.playRes(ctx, raceRes) }
                        "flag" -> { SoundEngine.playVvFanfare(); Tts.speak(ctx.getString(cheers.random())) }
                        "cheer" -> Tts.speak(ctx.getString(cheers.random()))
                        "sputter" -> { SoundEngine.playStall(); Sfx.stopStream(engine); engine = 0 }
                        "finishLine" -> { GameMusic.setSpeed(1f); Sfx.play("vv_applause", 1f, 1f); Sfx.play("vv_fanfare2", 0.9f, 1f); Settings.setVroumEndlessUnlocked(true)
                                          Sfx.stopStream(engine); engine = 0; Sfx.stopStream(roar); roar = 0; Haptics.success(ctx); Tts.speak(ctx.getString(cheers.random())) }
                        "firework" -> SoundEngine.playVvTick(0.55f + kotlin.random.Random.nextFloat() * 0.3f)
                        "clear" -> finishRun(g, true)
                        "over" -> { Sfx.play("vv_applause", 0.6f, 0.9f); finishRun(g, false) }
                        "timeup" -> { SoundEngine.playVvGameOver(); Sfx.play("vv_applause", 0.6f, 0.9f); Sfx.stopStream(engine); engine = 0; finishRun(g, false) }
                    }
                }
                if (countdown == 0 && g.introT <= 0f && g.elapsed > VroumGame.INTRO_DUR + 1.1f) countdown = -1
                engineCd -= 1f / 60f
                if (engine != 0 && engineCd <= 0f) { engineCd = 0.15f; Sfx.setStreamRate(engine, 0.75f + g.mps / 34f + (if (g.turboT > 0f) 0.15f else 0f) + (if (g.boosting) 0.4f else 0f)) }
                // roulette de la boîte à objet : tic-tic-tic de plus en plus aigu ; laser : petit tir répété
                if (roar != 0) Sfx.setStreamRate(roar, (0.85f + g.mps / 110f + 0.08f * g.boostSeg).coerceIn(0.8f, 1.6f))
                fxCd -= 1f / 60f; sndCd -= 1f / 60f
                if (fxCd <= 0f) {
                    if (g.roulette > 0f) { fxCd = 0.085f; SoundEngine.playVvTick(1.0f + (1f - g.roulette) * 0.9f) }
                    else if (g.weapon == VWeapon.LASER) { fxCd = 0.2f; SoundEngine.playShot() }
                }   // le moteur monte dans les tours avec l'élan
                tick++
                if (phase != VPhase.PLAYING) break
            }
        } finally { Sfx.stopStream(engine); Sfx.stopStream(roar) }
    }

    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalTextStyle provides androidx.compose.material3.LocalTextStyle.current.copy(fontFamily = VROUM_FONT)) {
    Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF7FD4FF), Color(0xFFB39DFF))))) {
        when (phase) {
            VPhase.MODE -> ModeSelectScreen(spr, mode, players,
                onMode = { SoundEngine.playVroumUi(); mode = it; if (it != VMode.RACE) players = 1 },
                onPlayers = { SoundEngine.playVroumUi(); players = it }, onBack = onBack,
                onNext = { SoundEngine.playVroumUi(); endless = mode == VMode.ARCADE; duelMode = mode == VMode.RACE && players == 2; garageStep = 1; phase = VPhase.SELECT })
            VPhase.SELECT -> androidx.compose.runtime.key(garageStep) {
                if (garageStep == 1) GarageScreen(spr, vehicle, VEHICLE_COLORS, onBack = { phase = VPhase.MODE }, onPick = { vehicle = it; hornFor(it) },
                    onNext = { SoundEngine.playVroumUi(); if (duelMode) { if (vehicle2 == vehicle) vehicle2 = (vehicle + 1) % VEHICLES.size; garageStep = 2 } else phase = VPhase.LEVEL },
                    title = if (duelMode) stringResource(R.string.vroum_player_pick, 1) else null)
                else GarageScreen(spr, vehicle2, VEHICLE_COLORS, onBack = { garageStep = 1 }, onPick = { vehicle2 = it; hornFor(it) },
                    onNext = { SoundEngine.playVroumUi(); phase = VPhase.LEVEL }, title = stringResource(R.string.vroum_player_pick, 2))
            }
            VPhase.LEVEL -> if (mode == VMode.CUP) CupSelectScreen(spr, vehicle, cupIdx, diff, onDiff = { SoundEngine.playVroumUi(); diff = it }, onBack = { phase = VPhase.SELECT },
                onPlay = { c -> SoundEngine.playVroumUi(); cupIdx = c; cupRace = 0; cupStars.fill(0); cupScores.fill(0); startCupField(); themeIdx = cupThemeIdx(CUPS[c], 0); phase = VPhase.PLAYING; runToken++ })
            else RaceSetupScreen(spr, vehicle, themeIdx, diff, endless, duelMode, onDiff = { SoundEngine.playVroumUi(); diff = it },
                onEndless = { }, onDuel = { }, onBack = { garageStep = if (duelMode) 2 else 1; phase = VPhase.SELECT },
                onPlay = { t -> SoundEngine.playVroumUi(); themeIdx = t; phase = if (duelMode) VPhase.DUEL else VPhase.PLAYING; runToken++ })
            VPhase.DUEL -> DuelScreen(themeIdx, vehicle, vehicle2, spr, applyLabels, onBack = { phase = VPhase.LEVEL })
            VPhase.CUP_END -> CupResultScreen(spr, cupIdx, cupStars, cupScores, cupRows.map { VStandRow(it.veh, it.me, 0, it.total, it.rival) },
                onAgain = { SoundEngine.playVroumUi(); cupRace = 0; cupStars.fill(0); cupScores.fill(0); startCupField(); themeIdx = cupThemeIdx(CUPS[cupIdx], 0); phase = VPhase.PLAYING; runToken++ },
                onOther = { SoundEngine.playVroumUi(); phase = VPhase.LEVEL })
            VPhase.PLAYING -> {
                val g = game
                if (g != null && glView != null) {
                    // le temps que la 3D s'affiche (une fraction de seconde), on voit l'illustration du circuit plutôt qu'un fond vide
                    spr.cards.getOrNull(themeIdx)?.let { Image(BitmapPainter(it), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                    AndroidView(factory = { glView.also { it.onResume() } }, modifier = Modifier.fillMaxSize())
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(runToken) {
                                detectTapGestures(onTap = { pos ->
                                    val w = size.width; val h = size.height
                                    val onCar = pos.y > h * 0.62f && pos.y < h * 0.80f && abs(pos.x - w / 2f) < w * 0.10f
                                    if (onCar) glView.queueEvent { g.horn() }
                                    else if (pos.x < w / 2f) glView.queueEvent { g.moveLane(-1) } else glView.queueEvent { g.moveLane(1) }
                                })
                            }
                            .pointerInput(runToken) {
                                var acc = 0f; var fired = false
                                detectHorizontalDragGestures(
                                    onDragStart = { acc = 0f; fired = false },
                                    onHorizontalDrag = { change, dx ->
                                        change.consume(); acc += dx
                                        if (!fired && abs(acc) > size.width * 0.08f) { fired = true; val d = if (acc > 0) 1 else -1; glView.queueEvent { g.moveLane(d) } }
                                    }
                                )
                            }
                    )
                    @Suppress("UNUSED_EXPRESSION") tick
                    val speedPath = remember { androidx.compose.ui.graphics.Path() }
                    val fx = remember(g) { VroumFx(ctx) }
                    Canvas(Modifier.fillMaxSize()) {
                        @Suppress("UNUSED_EXPRESSION") tick
                        // LIGNES DE VITESSE : des pointes claires fuient du point de fuite vers les bords de l'écran. Discrètes à
                        // pleine vitesse, franches pendant un boost (bleutées), arc-en-ciel pendant le super boost.
                        run {
                            val lvl = g.boostLevel
                            val k = if (g.over || g.inIntro) 0f else if (lvl == 2) 1f else if (lvl == 1) 0.8f else ((g.mps - 25f) / 16f).coerceIn(0f, 0.4f)
                            if (k > 0.04f) {
                                val cx = size.width / 2f; val cy = size.height * (if (g.wide) 0.36f else 0.43f)
                                val rMax = kotlin.math.hypot(size.width / 2f, size.height - cy)
                                val n = (14 + 30 * k).toInt()
                                speedPath.rewind()
                                for (i in 0 until n) {
                                    val h1 = ((i * 7919 + 13) % 1000) / 1000f; val h2 = ((i * 104729 + 7) % 1000) / 1000f
                                    val ph = (g.elapsed * (1.6f + 1.8f * h1) * (0.7f + 0.6f * k) + h2) % 1f
                                    val ang = (i + h2 * 0.8f) / n * 6.2832f
                                    // rien au-dessus de la route devant nous (un cône vers le bas reste dégagé pour bien lire le trafic)
                                    val ca = cos(ang); val sa = sin(ang)
                                    if (sa > 0.80f) continue
                                    val r0 = rMax * (0.42f + 0.50f * ph); val len = rMax * (0.07f + 0.16f * k) * (0.5f + ph)
                                    val wd = size.height / 100f * (0.28f + 0.50f * h1) * (0.6f + ph)
                                    val x0 = cx + ca * r0; val y0 = cy + sa * r0; val x1 = cx + ca * (r0 + len); val y1 = cy + sa * (r0 + len)
                                    speedPath.moveTo(x0, y0); speedPath.lineTo(x1 - sa * wd, y1 + ca * wd); speedPath.lineTo(x1 + sa * wd, y1 - ca * wd); speedPath.close()
                                }
                                val col = if (lvl == 2) { val (r, gg, b) = g.rainbow(g.elapsed * 0.9f); Color(0.6f + 0.4f * r, 0.6f + 0.4f * gg, 0.6f + 0.4f * b) } else if (lvl == 1) Color(0xFFCFEBFF) else Color.White
                                clipRect { drawPath(speedPath, col.copy(alpha = 0.20f + 0.45f * k)) }   // (sinon les pointes débordent sur les barres système)
                            }
                        }
                        val u = size.height / 100f
                        // messages « arcade » + comètes vers la carte de score (en haut à droite)
                        fx.draw(this, g, Offset(size.width - 58.dp.toPx(), 38.dp.toPx()))
                        if (g.giant > 0) drawRect(V_GOLD.copy(alpha = 0.25f + 0.2f * sin(g.elapsed * 10f)), size = size, style = Stroke(u * 1.6f))
                        // éclair blanc de la bombe ; voile de sable pendant la tempête ; rafale de vent annoncée
                        if (g.flash > 0f) drawRect(Color.White.copy(alpha = (g.flash * 0.85f).coerceIn(0f, 1f)), size = size)
                        if (g.storm > 0.05f) drawRect(Color(0xFFE8C98A).copy(alpha = 0.30f * g.storm), size = size)
                        if (g.windT > 0f) for (i in 0 until 9) { val yy = size.height * (0.2f + 0.07f * i); val ph = ((g.elapsed * 2.2f + i * 0.31f) % 1f)
                            val x0 = if (g.windDir > 0) size.width * (ph - 0.2f) else size.width * (1.2f - ph)
                            drawLine(Color.White.copy(alpha = 0.55f), Offset(x0, yy), Offset(x0 + g.windDir * size.width * 0.18f, yy), strokeWidth = u * 0.5f, cap = StrokeCap.Round) }
                        if (g.cannonT > 0f) drawRect(V_CYAN.copy(alpha = 0.15f + 0.1f * sin(g.elapsed * 14f)), size = size, style = Stroke(u * 1.2f))
                    }
                    BoostOverlay(g, tick)
                    VroumHud(g, tick, fx)
                    LaneHints(Modifier.align(Alignment.BottomCenter))
                    // BOOST (à tenir) à gauche, SAUT à droite ; la jauge de boost juste au-dessus
                    // (en paysage : dans le coin bas-droit, pour ne pas cacher le bolide au centre)
                    Column(Modifier.align(if (g.wide) Alignment.BottomEnd else Alignment.BottomCenter).padding(bottom = 20.dp, end = if (g.wide) 100.dp else 0.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        BoostBar(g, tick, Modifier.padding(bottom = 10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(26.dp), verticalAlignment = Alignment.CenterVertically) {
                            BoostButton(g, tick) { g.tapBoost() }
                            JumpButton(cooldown = (g.jumpCd / g.JUMP_CD).coerceIn(0f, 1f), frame = tick) { glView.queueEvent { g.jump() } }
                        }
                    }
                    ItemSlot(g, spr.items, tick, Modifier.align(Alignment.CenterStart).padding(start = 12.dp).offset(y = if (g.wide) 14.dp else (-40).dp))
                    TakedownChip(g, tick, Modifier.align(Alignment.CenterEnd).padding(end = 12.dp).offset(y = if (g.wide) (-20).dp else (-40).dp))
                    if (countdown >= 0) CountdownOverlay(countdown, hint = !g.duel, rocket = g.rocketStart)
                    // tutoriel de la toute première partie (3 bulles, avance avec les gestes de l'enfant)
                    if (tutoStep >= 0 && countdown < 0) VroumTutoOverlay(tutoStep)
                }
                BackButton(onBack = { GameMusic.stop(); phase = VPhase.LEVEL }, modifier = Modifier.align(Alignment.TopStart))
            }
            VPhase.CLEAR, VPhase.END -> {
                ResultScreen(spr, cleared = resCleared, score = resScore, dist = resDist, record = resRecord, endless = endless, diff = diff, timeUp = resTimeUp, takedowns = resTakedowns,
                    place = resPlace, results = resResults, standings = if (mode == VMode.CUP && cupRows.isNotEmpty()) cupRows else null,
                    vehicleImg = spr.vehicles.getOrNull(vehicle), frontImg = spr.fronts.getOrNull(vehicle), best = HighScores.best(gameKey), bg = if (themeIdx == 0) spr.village else spr.cards.getOrNull(themeIdx),
                    onReplay = {
                        SoundEngine.playVroumUi()
                        if (mode != VMode.CUP) { phase = VPhase.PLAYING; runToken++ }
                        else if (cupRace < 3) { cupRace++; themeIdx = cupThemeIdx(CUPS[cupIdx], cupRace); phase = VPhase.PLAYING; runToken++ }
                        else { Settings.setVroumStars("cup_${CUPS[cupIdx].key}_$diff", if (cupRows.isNotEmpty()) cupRankStars(cupRows.indexOfFirst { it.me } + 1) else cupGlobalStars(cupStars.sum())); phase = VPhase.CUP_END }
                    },
                    onOther = { SoundEngine.playVroumUi(); phase = VPhase.LEVEL },
                    onVehicle = { SoundEngine.playVroumUi(); garageStep = 1; phase = VPhase.SELECT },
                    cupLine = if (mode == VMode.CUP) stringResource(CUPS[cupIdx].nameRes) + "  ·  " + stringResource(R.string.vroum_cup_race, cupRace + 1) else null,
                    nextLabel = if (mode != VMode.CUP) null else if (cupRace < 3) stringResource(R.string.vroum_next_race) else stringResource(R.string.vroum_cup_results))
            }
        }
    }
    }   // (police Rubik : CompositionLocalProvider)
}

// ---------------------------------------------------------------------------
// Composants UI (design « appli enfants » : arrondis, dégradés doux, ombres)
// ---------------------------------------------------------------------------

/** Bouton secondaire « verre » (translucide, liseré clair), texte sur UNE ligne. */
@Composable
private fun GlassButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val press = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    Box(
        modifier = modifier.scale(press.value).clip(RoundedCornerShape(24.dp))
            .background(Color(0x40FFFFFF)).border(1.5.dp, Color(0x99FFFFFF), RoundedCornerShape(24.dp))
            .pointerInput(Unit) { detectTapGestures(onPress = { scope.launch { press.snapTo(0.94f); press.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 600f)) }; onClick(); tryAwaitRelease() }) }
            .padding(vertical = 13.dp, horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) { FitText(label, 15f) }
}

@Composable
private fun PillButton(label: String, colors: List<Color>, enabled: Boolean = true, wide: Boolean = false, onClick: () -> Unit) {
    val press = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    Box(
        modifier = (if (wide) Modifier.fillMaxWidth() else Modifier)
            .scale(press.value)
            .shadow(if (enabled) 10.dp else 0.dp, RoundedCornerShape(30.dp), clip = false)
            .clip(RoundedCornerShape(30.dp))
            .background(if (enabled) Brush.horizontalGradient(colors) else Brush.horizontalGradient(listOf(Color(0xFFBBBBCC), Color(0xFFA0A0B5))))
            .pointerInput(enabled) {
                detectTapGestures(onPress = {
                    if (!enabled) return@detectTapGestures
                    scope.launch { press.snapTo(0.93f); press.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 600f)) }
                    onClick(); tryAwaitRelease()
                })
            }
            .padding(horizontal = 22.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        FitText(label, 20f)
    }
}

/** Bouton SAUT moderne : disque dégradé bleu, flèche blanche arrondie, ombre, rebond à l'appui.
 *  [cooldown] 1 → 0 : bouton gris désactivé avec un arc qui se recharge. */
@Composable
private fun JumpButton(modifier: Modifier = Modifier, cooldown: Float = 0f, frame: Int = 0, onJump: () -> Unit) {
    @Suppress("UNUSED_EXPRESSION") frame
    val press = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    val bob = rememberInfiniteTransition(label = "jumpBob").animateFloat(
        initialValue = 0f, targetValue = 1f, animationSpec = infiniteRepeatable(tween(650, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "bob"
    ).value
    val ready = cooldown <= 0f
    Box(
        modifier = modifier
            .size(92.dp)
            .scale(press.value)
            .shadow(if (ready) 14.dp else 4.dp, CircleShape, clip = false)
            .clip(CircleShape)
            .background(if (ready) Brush.verticalGradient(listOf(Color(0xFF6ED3FF), V_BLUE)) else Brush.verticalGradient(listOf(Color(0xAA8A93A8), Color(0xAA5C6478))))
            .border(4.dp, Color.White.copy(alpha = if (ready) 0.9f else 0.5f), CircleShape)
            .pointerInput(ready) {
                detectTapGestures(onPress = {
                    if (ready) { onJump(); scope.launch { press.snapTo(0.84f); press.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = 520f)) } }
                    tryAwaitRelease()
                })
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width; val h = size.height
            val oy = if (ready) -bob * h * 0.05f else 0f
            val col = if (ready) Color.White else Color.White.copy(alpha = 0.55f)
            val p = Path()
            p.moveTo(w * 0.5f, h * 0.24f + oy); p.lineTo(w * 0.24f, h * 0.52f + oy); p.moveTo(w * 0.5f, h * 0.24f + oy); p.lineTo(w * 0.76f, h * 0.52f + oy)
            p.moveTo(w * 0.5f, h * 0.26f + oy); p.lineTo(w * 0.5f, h * 0.72f + oy)
            drawPath(p, col, style = Stroke(width = w * 0.11f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawRoundRect(col.copy(alpha = 0.55f), Offset(w * 0.32f, h * 0.80f), Size(w * 0.36f * (1f - 0.25f * bob), h * 0.05f), CornerRadius(w, w))
            // recharge : arc qui se remplit dans le sens horaire
            if (!ready) drawArc(Color.White, -90f, 360f * (1f - cooldown), false, Offset(w * 0.06f, h * 0.06f), Size(w * 0.88f, h * 0.88f), style = Stroke(width = w * 0.06f, cap = StrokeCap.Round))
        }
    }
}

/** Effet « on va très vite » pendant un boost : traits de vitesse radiaux depuis le centre, vignette colorée,
 *  bandeau SUPER BOOST arc-en-ciel pour le super. */
@Composable
private fun BoostOverlay(g: VroumGame, frame: Int) {
    @Suppress("UNUSED_EXPRESSION") frame
    val lvl = g.boostLevel
    if (lvl == 0) return
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width; val h = size.height; val cx = w / 2f; val cy = h * 0.42f
        val t = g.elapsed
        val n = if (lvl == 2) 34 else if (g.boosting) 32 else 20
        val boostCol = if (g.boosting) Color(0xFFBFE2FF) else V_CYAN
        for (i in 0 until n) {
            val a = (i / n.toFloat()) * 6.2832f + (i % 3) * 0.13f
            val flick = ((t * 22f + i * 1.7f) % 1f)
            val r0 = (0.28f + 0.5f * flick) * h; val r1 = r0 + (0.10f + 0.12f * (1f - flick)) * h
            val col = if (lvl == 2) { val (r, gg, b) = g.rainbow(t * 2f + i * 0.07f); Color(r, gg, b) } else boostCol
            drawLine(col.copy(alpha = 0.55f * (1f - flick)), Offset(cx + cos(a) * r0, cy + sin(a) * r0), Offset(cx + cos(a) * r1, cy + sin(a) * r1), strokeWidth = if (lvl == 2) 7f else 5f, cap = StrokeCap.Round)
        }
        val vig = if (lvl == 2) { val (r, gg, b) = g.rainbow(t * 1.5f); Color(r, gg, b) } else if (g.boosting) Color(0xFF3F8CFF) else V_CYAN
        drawRect(Brush.radialGradient(listOf(Color.Transparent, vig.copy(alpha = if (lvl == 2) 0.35f else 0.22f)), center = Offset(cx, cy), radius = h * 0.75f), size = size)
    }
}

/** Repères tactiles gauche/droite en bas d'écran (translucides) : « toute la moitié gauche = gauche ». */
@Composable
private fun LaneHints(modifier: Modifier = Modifier) {
    val pulse = rememberInfiniteTransition(label = "hint").animateFloat(
        initialValue = 0.45f, targetValue = 0.8f, animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "p"
    ).value
    Row(modifier = modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 34.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Chevron(left = true, alpha = pulse); Chevron(left = false, alpha = pulse)
    }
}

@Composable
private fun Chevron(left: Boolean, alpha: Float) {
    // (fond sombre translucide : le repère se voit aussi sur les circuits très clairs — neige, nuages, plage)
    Box(Modifier.size(64.dp).alpha(alpha).clip(RoundedCornerShape(22.dp)).background(Color(0xFF1B1630).copy(alpha = 0.42f)), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(34.dp)) {
            val w = size.width; val h = size.height
            val p = Path()
            if (left) { p.moveTo(w * 0.68f, h * 0.15f); p.lineTo(w * 0.32f, h * 0.5f); p.lineTo(w * 0.68f, h * 0.85f) }
            else { p.moveTo(w * 0.32f, h * 0.15f); p.lineTo(w * 0.68f, h * 0.5f); p.lineTo(w * 0.32f, h * 0.85f) }
            drawPath(p, Color.White, style = Stroke(width = w * 0.16f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

/** 3 - 2 - 1 - GO ! : chiffre qui apparaît en grand et se dégonfle. */
@Composable
private fun CountdownOverlay(count: Int, hint: Boolean = false, rocket: Boolean = false) {
    // (un départ canon réussi remplace le « GO! » par son message, qui « saute » à son tour)
    val boosted = rocket && count == 0
    val scale = remember(count, boosted) { Animatable(1.6f) }
    LaunchedEffect(count, boosted) { scale.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 400f)) }
    val label = if (count > 0) "$count" else if (boosted) stringResource(R.string.vroum_rocket) else stringResource(R.string.vroum_go)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            label, color = if (count > 0) Color.White else if (boosted) V_CYAN else V_GOLD, fontWeight = FontWeight.ExtraBold,
            fontStyle = if (boosted) FontStyle.Italic else FontStyle.Normal,
            fontSize = if (count > 0) 120.sp else if (boosted) 40.sp else 64.sp, textAlign = TextAlign.Center, maxLines = 1,
            style = androidx.compose.ui.text.TextStyle(fontFamily = VROUM_FONT, shadow = androidx.compose.ui.graphics.Shadow(Color(0xCC000000), Offset(0f, 5f), 12f)),
            modifier = Modifier.scale(scale.value)
        )
        // l'astuce du départ canon, glissée sous le « 1 » (les grands la lisent, les petits tombent dessus par hasard)
        if (hint && count == 1) Text(stringResource(R.string.vroum_rocket_hint), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1,
            modifier = Modifier.align(Alignment.Center).offset(y = 92.dp).clip(RoundedCornerShape(12.dp)).background(Color(0x77000000)).padding(horizontal = 12.dp, vertical = 5.dp))
    }
}

/**
 * Écran de fin « appli enfants » : illustration, bolide qui rebondit, 1 à 3 étoiles qui apparaissent
 * une par une, score qui défile, pluie de confettis, gros bouton « Encore ! ».
 */
@Composable
private fun ResultScreen(spr: VSprites, cleared: Boolean, score: Int, dist: Int, record: Boolean, endless: Boolean, diff: Int, timeUp: Boolean, takedowns: Int, vehicleImg: ImageBitmap?, frontImg: ImageBitmap?, best: Int, bg: ImageBitmap?,
                         onReplay: () -> Unit, onOther: () -> Unit, onVehicle: () -> Unit, cupLine: String? = null, nextLabel: String? = null,
                         place: Int = 0, results: List<VResult> = emptyList(), standings: List<VStandRow>? = null) {
    val stars = vroumStarCount(endless, cleared, score, dist, diff, place)
    var t by remember { mutableStateOf(0f) }
    LaunchedEffect(Unit) { var last = 0L; while (t < 30f) { val now = withFrameNanos { it }; if (last != 0L) t += (now - last) / 1e9f; last = now } }
    val shownScore = (score * (t / 1.4f).coerceIn(0f, 1f)).toInt()
    val confetti = remember { List(70) { i -> floatArrayOf(Random.nextFloat(), -Random.nextFloat() * 1.2f, 0.25f + Random.nextFloat() * 0.35f, Random.nextFloat() * 6.28f, i % 5f) } }
    val cols = listOf(V_PINK, V_CYAN, V_GOLD, V_GREEN, V_ORANGE)
    Box(Modifier.fillMaxSize()) {
        Illustration(if (cleared) (bg ?: spr.village) else spr.empty)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x66000000), Color(0x22000000), Color(0xAA1A1440)))))
        if (cleared || stars > 0) Canvas(Modifier.fillMaxSize()) {
            for (c in confetti) {
                val y = ((c[1] + t * c[2]) % 1.3f) * size.height
                val x = (c[0] + 0.03f * sin(t * 2f + c[3])) * size.width
                val col = cols[c[4].toInt()]
                rotate(t * 200f + c[3] * 57f, Offset(x, y)) { drawRoundRect(col, topLeft = Offset(x - 9f, y - 5f), size = Size(18f, 10f), cornerRadius = CornerRadius(3f, 3f)) }
            }
        }
        Column(Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.weight(0.5f))
            // Course à positions : la PLACE en très gros (or, argent, bronze) ; sinon le titre habituel
            if (place > 0) {
                val (num, suf) = vroumOrdinal(place, vroumLang())
                val pop = 1f + 0.6f * (1f - (t / 0.3f).coerceIn(0f, 1f))
                val sh = androidx.compose.ui.graphics.Shadow(Color(0xAA000000), Offset(0f, 5f), 12f)
                Row(Modifier.scale(pop), verticalAlignment = Alignment.Bottom) {
                    Text(num, color = placeColor(place), fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic, fontSize = 68.sp, lineHeight = 72.sp, style = androidx.compose.ui.text.TextStyle(fontFamily = VROUM_FONT, shadow = sh))
                    Text(suf, color = placeColor(place), fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic, fontSize = 30.sp, modifier = Modifier.padding(bottom = 14.dp), style = androidx.compose.ui.text.TextStyle(fontFamily = VROUM_FONT, shadow = sh))
                }
            } else
            Text(stringResource(if (cleared) R.string.vroum_stage_clear else if (timeUp) R.string.gz_time_up else R.string.vroum_out_of_fuel),
                color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp, textAlign = TextAlign.Center, lineHeight = 30.sp,
                style = androidx.compose.ui.text.TextStyle(fontFamily = VROUM_FONT, shadow = androidx.compose.ui.graphics.Shadow(Color(0x66000000), Offset(0f, 4f), 10f)),
                modifier = Modifier.fillMaxWidth(0.9f))
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                for (i in 0 until 3) {
                    val on = i < stars && t > 0.4f + i * 0.35f
                    val pop = if (on) (1f + 0.5f * (1f - ((t - (0.4f + i * 0.35f)) / 0.25f).coerceIn(0f, 1f))) else 1f
                    Text("★", color = if (on) V_GOLD else Color.White.copy(alpha = 0.28f), fontSize = (if (i == 1) 54 else 42).sp, modifier = Modifier.scale(pop).padding(horizontal = 4.dp),
                        style = androidx.compose.ui.text.TextStyle(fontFamily = VROUM_FONT, shadow = androidx.compose.ui.graphics.Shadow(Color(0x55000000), Offset(0f, 3f), 6f)))
                }
            }
            // championnat : rappel de la coupe et du numéro de la course
            if (cupLine != null) Box(Modifier.padding(top = 6.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xAA000000)).padding(horizontal = 14.dp, vertical = 4.dp)) {
                FitText(cupLine, 15f, color = V_GOLD, weight = FontWeight.Black, italic = true)
            }
            if (place > 0 && results.size >= 3) {
                Podium(spr, results, t, Modifier.padding(top = 6.dp, bottom = 10.dp).fillMaxWidth(0.84f))
            } else if (cleared && frontImg != null) {
                // PHOTO FINISH : un polaroïd qui claque à l'écran, le bolide vu de face sous le damier d'arrivée
                val pop = 1f + 0.4f * (1f - (t / 0.30f).coerceIn(0f, 1f))
                Box(Modifier.padding(top = 8.dp, bottom = 16.dp).fillMaxWidth(0.60f).scale(pop).graphicsLayer { rotationZ = -4f + sin(t * 1.3f) * 1.2f }
                    .shadow(16.dp, RoundedCornerShape(10.dp), clip = false).clip(RoundedCornerShape(10.dp)).background(Color.White)
                    .padding(start = 9.dp, end = 9.dp, top = 9.dp, bottom = 5.dp)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.fillMaxWidth().aspectRatio(1.12f).clip(RoundedCornerShape(4.dp)).background(Color(0xFF6B5B95))) {
                            (bg ?: spr.village)?.let { Image(painter = BitmapPainter(it), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.5f to Color.Transparent, 1f to Color(0x99000000))))
                            Canvas(Modifier.fillMaxWidth().height(16.dp)) {
                                val c = size.height / 2f; var x = 0f; var i = 0
                                while (x < size.width) { for (r in 0..1) drawRect(if ((i + r) % 2 == 0) Color.White else Color(0xFF1B1B1B), Offset(x, r * c), Size(c, c)); x += c; i++ }
                            }
                            Image(painter = BitmapPainter(frontImg), contentDescription = null, contentScale = ContentScale.Fit,
                                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(0.74f).aspectRatio(1f).offset(y = (4f - abs(sin(t * 4f)) * 6f).dp))
                        }
                        Text("PHOTO FINISH", color = V_INK, fontWeight = FontWeight.Black, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, fontSize = 15.sp,
                            letterSpacing = 2.sp, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            } else vehicleImg?.let {
                val bob = abs(sin(t * 4f)) * 8f
                Image(painter = BitmapPainter(it), contentDescription = null, modifier = Modifier.size(104.dp).padding(top = bob.dp), contentScale = ContentScale.Fit)
            }
            Spacer(Modifier.height(6.dp))
            // carte de score « verre » : une seule colonne, tout centré, largeur fixe
            if (standings != null) StandingsCard(spr, standings, t, Modifier.padding(bottom = 10.dp).fillMaxWidth(0.9f))
            else Box(Modifier.fillMaxWidth(0.82f).shadow(12.dp, RoundedCornerShape(28.dp), clip = false).clip(RoundedCornerShape(28.dp))
                .background(Brush.verticalGradient(listOf(Color(0xF7FFFFFF), Color(0xE6F4F1FF)))).padding(horizontal = 22.dp, vertical = if (place > 0) 8.dp else 16.dp)) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$shownScore", color = V_INK, fontWeight = FontWeight.ExtraBold, fontSize = (if (place > 0) 34 else 44).sp, lineHeight = (if (place > 0) 36 else 46).sp)
                    FitText("🏁 " + stringResource(R.string.vroum_distance, dist) + (if (takedowns > 0) "   ·   " + stringResource(R.string.vroum_res_takedowns, takedowns) else ""),
                        15f, color = V_INK.copy(alpha = 0.65f), weight = FontWeight.Bold)
                    if (record && t > 1.5f) Box(Modifier.padding(top = 8.dp).clip(RoundedCornerShape(14.dp)).background(Brush.horizontalGradient(listOf(V_PINK, V_ORANGE))).padding(horizontal = 14.dp, vertical = 5.dp)) {
                        Text("🏆 " + stringResource(R.string.new_high_score), color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, maxLines = 1)
                    } else if (best > 0) Text("🏆 $best", color = V_INK.copy(alpha = 0.55f), fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }
            Spacer(Modifier.weight(1f))
            // actions : un bouton principal plein, deux boutons « verre » de même taille dessous (texte sur une ligne)
            if (nextLabel != null) {
                // championnat : un seul bouton, on enchaîne (course suivante, puis bilan de la coupe)
                Box(Modifier.fillMaxWidth(0.82f)) { PillButton(nextLabel + "  ▶", listOf(V_PINK, V_ORANGE), wide = true, onClick = onReplay) }
                Spacer(Modifier.height(if (standings != null) 12.dp else 58.dp))
            } else {
            Box(Modifier.fillMaxWidth(0.82f)) { PillButton("🔁  " + stringResource(R.string.replay), listOf(V_GREEN, V_TEAL), wide = true, onClick = onReplay) }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(0.82f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassButton("🗺  " + stringResource(R.string.vroum_route), Modifier.weight(1f), onClick = onOther)
                GlassButton("🚗  " + stringResource(R.string.vroum_vehicle), Modifier.weight(1f), onClick = onVehicle)
            }
            }
            Spacer(Modifier.height(6.dp))
        }
        if (cleared && frontImg != null && place == 0 && t < 0.45f) Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.9f * (1f - t / 0.45f))))
    }
}

@Composable
private fun Illustration(img: ImageBitmap?) {
    if (img != null) {
        Image(painter = BitmapPainter(img), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x33000000), Color(0x99000000)))))
    }
}

/** Écran 1 : choix du bolide — tout tient sur un écran (5 rangées de 2 cartes). */
/** Tutoriel de la toute première partie : 3 bulles qui avancent avec les gestes de l'enfant. */
@Composable
private fun VroumTutoOverlay(step: Int) {
    val txt = when (step) { 0 -> stringResource(R.string.vroum_tuto_lane); 1 -> stringResource(R.string.vroum_tuto_jump); else -> stringResource(R.string.vroum_tuto_boost) }
    val icon = when (step) { 0 -> "👈      👉"; 1 -> "⬇️"; else -> "🍄 ⛽ 💨  ✅      🕳 🪵  ❌" }
    val pulse by rememberInfiniteTransition(label = "tuto").animateFloat(0.96f, 1.05f, infiniteRepeatable(tween(500), RepeatMode.Reverse), label = "tutoP")
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.align(if (step == 1) Alignment.BottomCenter else Alignment.Center).padding(bottom = if (step == 1) 130.dp else 0.dp).scale(pulse),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(Modifier.clip(RoundedCornerShape(22.dp)).background(Color(0xDD241A45)).border(2.dp, V_GOLD, RoundedCornerShape(22.dp)).padding(horizontal = 20.dp, vertical = 12.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(txt, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, textAlign = TextAlign.Center)
                    Text(icon, fontSize = 24.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }
            if (step == 1) Text("⬇", color = V_GOLD, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

// ---------------------------------------------------------------------------
// Mode 2 JOUEURS : écran partagé (gauche|droite en paysage, haut|bas en portrait).
// Deux parties invincibles, sans chrono ni fantôme : premier arrivé au village !
// ---------------------------------------------------------------------------
@Composable
private fun DuelScreen(themeIdx: Int, vehicle: Int, vehicle2: Int, spr: VSprites, labels: (VroumGame) -> Unit, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var runToken by remember { mutableIntStateOf(0) }
    var winner by remember { mutableIntStateOf(0) }   // 0 = course en cours, sinon 1 ou 2
    var tick by remember { mutableIntStateOf(0) }
    val trickLabel = stringResource(R.string.vroum_trick); val cannonLabel = stringResource(R.string.vroum_cannons)
    val veh2 = vehicle2      // chacun a choisi son bolide au garage
    // même graine : les deux joueurs ont exactement la même route, c'est la conduite qui fait la différence
    val seed = remember(runToken) { Random.nextLong() }
    val g1 = remember(runToken) { VroumGame(false, vehicle, 0, THEMES[themeIdx], trickLabel, cannonLabel, duel = true, seed = seed).also(labels) }
    val g2 = remember(runToken) { VroumGame(false, veh2, 0, THEMES[themeIdx], trickLabel, cannonLabel, duel = true, seed = seed).also(labels) }
    val v1 = remember(g1) { VroumGlView(ctx, g1) }
    val v2 = remember(g2) { VroumGlView(ctx, g2) }
    DisposableEffect(v1, v2) { onDispose { v1.onPause(); v2.onPause() } }
    LaunchedEffect(g1, g2) {
        SoundEngine.playVvTick(0.8f)
        var sndCd = 0f
        while (winner == 0) {
            withFrameNanos { }
            tick++
            // quelques sons partagés (une seule fois, pas un par moitié) + purge des files d'événements
            sndCd -= 1f / 60f
            for (gi in 0..1) { val gg = if (gi == 0) g1 else g2
                while (true) { val e = gg.events.poll() ?: break
                    when (e) {
                        "count" -> if (gi == 0) SoundEngine.playVvTick(1f)
                        "go" -> if (gi == 0) SoundEngine.playVvGo()
                        "jump", "bounce", "ramp" -> SoundEngine.playJump()
                        "land" -> if (gi == 0) SoundEngine.playLand()
                        "turbo", "superboost", "boostOn", "rocketStart" -> SoundEngine.playTurbo()
                        "takedown", "takedownBig", "strike", "zap", "stomp", "shove" -> if (sndCd <= 0f) { sndCd = 0.1f; SoundEngine.playCrush() }
                        "bump", "bumpSoft", "rivalHit" -> SoundEngine.playBump()
                        "bomb" -> SoundEngine.playBoom()
                    }
                } }
            if (g1.cleared || g2.cleared) {
                winner = if (g1.cleared) 1 else 2
                Sfx.play("vv_fanfare2"); Sfx.play("vv_applause")
            }
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val landscape = maxWidth > maxHeight
        if (landscape) Row(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxHeight()) { DuelHalf(1, g1, v1, tick, spr) }
            Box(Modifier.width(3.dp).fillMaxHeight().background(Color.White.copy(alpha = 0.6f)))
            Box(Modifier.weight(1f).fillMaxHeight()) { DuelHalf(2, g2, v2, tick, spr) }
        } else Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth()) { DuelHalf(2, g2, v2, tick, spr) }
            Box(Modifier.fillMaxWidth().height(3.dp).background(Color.White.copy(alpha = 0.6f)))
            Box(Modifier.weight(1f).fillMaxWidth()) { DuelHalf(1, g1, v1, tick, spr) }
        }
        BackButton(onBack = onBack, modifier = Modifier.align(Alignment.TopStart))
        if (winner != 0) Box(Modifier.fillMaxSize().background(Color(0xB3000000)), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("🏆", fontSize = 60.sp)
                Text(stringResource(R.string.vroum_duel_wins, winner), color = V_GOLD, fontWeight = FontWeight.ExtraBold, fontSize = 28.sp, textAlign = TextAlign.Center,
                    style = androidx.compose.ui.text.TextStyle(fontFamily = VROUM_FONT, shadow = androidx.compose.ui.graphics.Shadow(Color(0xAA000000), Offset(0f, 2f), 8f)))
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    GlassButton("🔁  " + stringResource(R.string.replay)) { SoundEngine.playVroumUi(); winner = 0; runToken++ }
                    GlassButton("🗺  " + stringResource(R.string.vroum_route)) { SoundEngine.playVroumUi(); onBack() }
                }
            }
        }
    }
}

/** Une moitié d'écran du duel : vue GL, contrôles tactiles, bandeau de progression et décompte. */
@Composable
private fun DuelHalf(player: Int, g: VroumGame, v: VroumGlView, tick: Int, spr: VSprites) {
    @Suppress("UNUSED_EXPRESSION") tick
    val col = if (player == 1) V_CYAN else V_PINK
    val ctx = LocalContext.current
    val fx = remember(g) { VroumFx(ctx) }
    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = { v.also { it.onResume() } }, modifier = Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().pointerInput(g) {
            detectTapGestures(onTap = { pos -> if (pos.x < size.width / 2f) v.queueEvent { g.moveLane(-1) } else v.queueEvent { g.moveLane(1) } })
        })
        // bandeau joueur + progression vers l'arrivée
        Row(Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(top = 8.dp, start = 62.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.clip(RoundedCornerShape(12.dp)).background(col).padding(horizontal = 10.dp, vertical = 3.dp)) {
                Text(stringResource(R.string.vroum_player_n, player), color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, maxLines = 1)
            }
            Spacer(Modifier.width(8.dp))
            Box(Modifier.weight(1f).height(10.dp)) {
                Box(Modifier.fillMaxSize().clip(RoundedCornerShape(5.dp)).background(Color(0x66000000)))
                Box(Modifier.fillMaxWidth((g.dist / VroumGame.LEVEL_LEN).coerceIn(0.02f, 1f)).fillMaxHeight().clip(RoundedCornerShape(5.dp)).background(col))
            }
            Text(" 🏁", fontSize = 14.sp)
        }
        // mêmes messages « arcade » qu'en solo (takedowns, rebonds…) et éclair de la bombe
        Canvas(Modifier.fillMaxSize()) {
            @Suppress("UNUSED_EXPRESSION") tick
            fx.draw(this, g, null)
            if (g.flash > 0f) drawRect(Color.White.copy(alpha = (g.flash * 0.85f).coerceIn(0f, 1f)), size = size)
        }
        // commandes : BOOST (une tape = un segment) et SAUT, la jauge au-dessus ; objet à gauche, takedowns à droite
        Column(Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            BoostBar(g, tick, Modifier.scale(0.78f))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                BoostButton(g, tick, Modifier.scale(0.82f)) { g.tapBoost() }
                JumpButton(modifier = Modifier.scale(0.9f), cooldown = (g.jumpCd / g.JUMP_CD).coerceIn(0f, 1f), frame = tick) { v.queueEvent { g.jump() } }
            }
        }
        ItemSlot(g, spr.items, tick, Modifier.align(Alignment.CenterStart).padding(start = 8.dp).scale(0.8f))
        TakedownChip(g, tick, Modifier.align(Alignment.CenterEnd).padding(end = 8.dp))
        if (g.introT > 0f) Text("${kotlin.math.ceil(g.introT.toDouble()).toInt().coerceIn(1, 3)}", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 60.sp,
            modifier = Modifier.align(Alignment.Center), style = androidx.compose.ui.text.TextStyle(fontFamily = VROUM_FONT, shadow = androidx.compose.ui.graphics.Shadow(Color(0xAA000000), Offset(0f, 3f), 8f)))
    }
}

@Composable
private fun VroumHud(g: VroumGame, frame: Int, fx: VroumFx? = null) {
    @Suppress("UNUSED_EXPRESSION") frame   // force la recomposition à chaque image
    // Une seule rangée alignée sur le bouton retour (56 dp de haut, 10 dp de marge) : jauge d'essence
    // explicite (pompe + %), puis score / distance dans une même carte à droite.
    Column(Modifier.fillMaxWidth().padding(start = 64.dp, end = 12.dp, top = 10.dp)) {
        Row(Modifier.fillMaxWidth().height(56.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            if (g.racers.isNotEmpty()) PlaceBadge(g, frame, Modifier.height(48.dp))
            else if (g.timeLimit > 0f) {
                val s = g.timeLeft.toInt().coerceAtLeast(0)
                Gauge(g.timeLeft / g.timeLimit, "⏱", "%d:%02d".format(s / 60, s % 60), V_CYAN, blink = s <= 10 && (g.elapsed * 4f).toInt() % 2 == 0, modifier = Modifier.width(150.dp).height(44.dp))
            } else FuelGauge(g.fuel, g.elapsed, modifier = Modifier.width(150.dp).height(44.dp))
            // la carte de score ENCAISSE les gros gains : elle gonfle, s'illumine, et le compteur roule jusqu'au total
            val punch = fx?.punch ?: 0f; val glow = fx?.glow ?: 0f
            Box(Modifier.graphicsLayer { val k = 1f + 0.34f * punch; scaleX = k; scaleY = k; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(1f, 0f) }
                .clip(RoundedCornerShape(16.dp)).background(androidx.compose.ui.graphics.lerp(Color(0x77000000), Color(0xEEFFB300), (glow * 0.85f).coerceIn(0f, 1f)))
                .border(2.dp, V_GOLD.copy(alpha = (0.25f + glow).coerceIn(0f, 1f)), RoundedCornerShape(16.dp)).padding(horizontal = 12.dp, vertical = 5.dp)) {
                Column(horizontalAlignment = Alignment.End) {
                    Text("${(fx?.shown ?: g.score.toFloat()).toInt()}", color = androidx.compose.ui.graphics.lerp(V_GOLD, Color.White, (glow * 1.2f).coerceIn(0f, 1f)), fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic, fontSize = 24.sp)
                    Text("🏁 " + stringResource(R.string.vroum_distance, g.dist.toInt()), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
        // frise d'arrivée : départ → 🏁, le bolide avance dessus ; les 300 derniers mètres clignotent « Arrivée bientôt ! »
        if (!g.endless) {
            val frac = (g.dist / VroumGame.LEVEL_LEN).coerceIn(0f, 1f)
            val left = (VroumGame.LEVEL_LEN - g.dist).toInt().coerceAtLeast(0)
            Row(Modifier.fillMaxWidth(if (g.wide) 0.46f else 1f).padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f).height(14.dp)) {
                    Box(Modifier.fillMaxWidth().height(6.dp).align(Alignment.CenterStart).clip(RoundedCornerShape(3.dp)).background(Color(0x66000000)))
                    Box(Modifier.fillMaxWidth(frac).height(6.dp).align(Alignment.CenterStart).clip(RoundedCornerShape(3.dp)).background(Brush.horizontalGradient(listOf(V_GREEN, V_GOLD))))
                    // 4 jalons (500 m)
                    for (k in 1..3) Box(Modifier.fillMaxWidth(k / 4f)) { Box(Modifier.align(Alignment.CenterEnd).size(4.dp, 12.dp).background(Color.White.copy(alpha = 0.7f))) }
                    RacerDots(g, frame)
                    // fantôme du meilleur temps : son avancée sur la même frise
                    if (g.ghostFrac >= 0f) Box(Modifier.fillMaxWidth(g.ghostFrac.coerceAtLeast(0.02f))) { Text("👻", fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterEnd).offset(x = 6.dp, y = (-1).dp).alpha(0.85f)) }
                    Box(Modifier.fillMaxWidth(frac.coerceAtLeast(0.02f))) { Text("🚗", fontSize = 13.sp, modifier = Modifier.align(Alignment.CenterEnd).offset(x = 6.dp, y = (-1).dp)) }
                }
                Text(" 🏁", fontSize = 15.sp)
                Box(Modifier.padding(start = 4.dp).clip(RoundedCornerShape(10.dp)).background(Color(0x66000000)).padding(horizontal = 8.dp, vertical = 2.dp)) {
                    Text(if (left <= 300 && (g.elapsed * 3f).toInt() % 2 == 0) stringResource(R.string.vroum_finish_soon) else stringResource(R.string.vroum_distance, left),
                        color = if (left <= 300) V_GOLD else Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, maxLines = 1)
                }
            }
        }
        // ÉLAN : compteur de vitesse (km/h) + jauge qui se remplit quand on roule proprement et se vide sur un choc ;
        // à droite la SÉRIE (multiplicateur de points) quand elle est lancée.
        Row(Modifier.fillMaxWidth(if (g.wide) 0.46f else 1f).padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            val hot = g.flow >= 0.999f
            Row(Modifier.width(150.dp).height(30.dp).clip(RoundedCornerShape(15.dp)).background(Color(0x77000000)).padding(start = 8.dp, end = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("⚡", fontSize = 14.sp, modifier = Modifier.padding(end = 5.dp))
                Box(Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(5.dp)).background(Color(0x55FFFFFF))) {
                    Box(Modifier.fillMaxWidth(g.flow.coerceIn(0.02f, 1f)).fillMaxHeight().background(
                        if (hot && (g.elapsed * 6f).toInt() % 2 == 0) Brush.horizontalGradient(listOf(Color.White, V_GOLD)) else Brush.horizontalGradient(listOf(V_CYAN, V_GOLD, V_ORANGE))))
                }
                Text("${g.kmh}", color = if (hot) V_GOLD else Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, modifier = Modifier.padding(start = 6.dp))
            }
            if (g.combo > 0) {
                Box(Modifier.padding(start = 8.dp).clip(RoundedCornerShape(14.dp)).background(if (g.mult > 1) V_PINK else Color(0x77000000)).padding(horizontal = 10.dp, vertical = 2.dp)) {
                    Text("x${g.mult}", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = (15 + g.mult).sp)
                }
                Box(Modifier.padding(start = 6.dp).weight(1f).height(10.dp).clip(RoundedCornerShape(5.dp)).background(Color(0x55000000))) {
                    Box(Modifier.fillMaxWidth((g.comboTimer / VroumGame.COMBO_T).coerceIn(0f, 1f)).height(10.dp).background(Brush.horizontalGradient(listOf(V_PINK, V_GOLD))))
                }
                Text(" ${g.combo}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }
}

/** Jauge d'essence explicite : pompe ⛽ + barre colorée (vert → orange → rouge) + pourcentage ; clignote quand c'est bas. */
@Composable
private fun FuelGauge(fuel: Float, t: Float, modifier: Modifier) {
    val f = (fuel / 100f).coerceIn(0f, 1f)
    val col = when { f > 0.5f -> V_GREEN; f > 0.25f -> V_ORANGE; else -> V_RED }
    Gauge(f, "⛽", "${(f * 100).toInt()}%", col, blink = f <= 0.25f && ((t * 4f).toInt() % 2 == 0), modifier = modifier)
}

/** Jauge générique (icône + barre + valeur) : essence, ou chrono en mode Facile. */
@Composable
private fun Gauge(frac: Float, icon: String, label: String, col: Color, blink: Boolean, modifier: Modifier) {
    Row(modifier.clip(RoundedCornerShape(22.dp)).background(Color(0x77000000)).padding(start = 8.dp, end = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(icon, fontSize = 20.sp, modifier = Modifier.padding(end = 6.dp))
        Box(Modifier.weight(1f).height(16.dp).clip(RoundedCornerShape(8.dp)).background(Color(0x55FFFFFF))) {
            Box(Modifier.fillMaxWidth(frac.coerceIn(0f, 1f)).fillMaxHeight().background(if (blink) Color.White else col))
        }
        Text(label, color = if (blink) V_RED else Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, modifier = Modifier.padding(start = 6.dp))
    }
}

private val VEHICLE_COLORS = listOf(V_RED, Color(0xFF3A78C9), Color(0xFFE23A3A), V_GOLD, Color(0xFF4E944F), Color(0xFF9A5CFF), Color(0xFFFFB300), Color(0xFF6BBF3A), V_PINK, V_ORANGE, Color(0xFFF2C21B), Color(0xFF8D5A3C))
