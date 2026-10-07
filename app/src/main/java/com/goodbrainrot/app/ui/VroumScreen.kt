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
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.stringResource
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

private val V_PINK = Color(0xFFFF6FA8)
private val V_CYAN = Color(0xFF4FC3F7)
private val V_BLUE = Color(0xFF1E88E5)
private val V_GOLD = Color(0xFFFFC94D)
private val V_GREEN = Color(0xFF66D97A)
private val V_TEAL = Color(0xFF1FA983)
private val V_ORANGE = Color(0xFFFF9052)
private val V_RED = Color(0xFFFF5252)
private val V_INK = Color(0xFF2A2440)
private val V_CARD = Color(0xF2FFFFFF)

private enum class VPhase { SELECT, LEVEL, PLAYING, CLEAR, END }
internal enum class VItem { FUEL, STAR, ACORN, MUSHROOM, BERRY, TURBO, PUDDLE, PASSENGER, CANNON, SUPERBOOST, HOLE, LOG }

/** Couleur (r,g,b) pour les thèmes. */
internal class C3(val r: Float, val g: Float, val b: Float)
/**
 * Thème de circuit : ciel (4 bandes du haut vers l'horizon), astre, relief (montagnes ou skyline de gratte-ciel),
 * couleurs du sol / de la route, décor du bord de route (clé sprite + taille), objets bonus locaux,
 * particules d'ambiance (0 aucune, 1 lucioles, 2 braises, 3 pétales, 4 neige).
 */
internal class VTheme(
    val key: String, val nameRes: Int, val card: String,
    val sky: List<C3>, val sun: C3, val sunR: Float, val stripes: Boolean, val stars: Boolean, val night: Boolean,
    val mount1: C3, val mount2: C3, val skyline: Boolean, val hills: C3,
    val grass: C3, val grass2: C3, val road: C3, val road2: C3, val edge: C3, val fog: C3,
    val decor: List<Pair<String, Float>>, val picks: List<String>, val ambient: Int,
    // obstacles à sauter propres au thème (décalque au sol / billboard en travers de la voie)
    val holeTex: String = "vv_hole", val logTex: String = "vv_log"
)
internal val THEMES = listOf(
    VTheme("forest", R.string.vroum_play, "vv_title",
        listOf(C3(0.06f, 0.03f, 0.14f), C3(0.26f, 0.09f, 0.40f), C3(0.62f, 0.20f, 0.52f), C3(1.0f, 0.76f, 0.45f)), C3(1f, 0.75f, 0.5f), 0.46f, true, true, false,
        C3(0.36f, 0.20f, 0.50f), C3(0.27f, 0.14f, 0.40f), false, C3(0.09f, 0.23f, 0.13f),
        C3(0.19f, 0.48f, 0.21f), C3(0.16f, 0.43f, 0.19f), C3(0.24f, 0.21f, 0.33f), C3(0.27f, 0.24f, 0.37f), C3(0.88f, 0.28f, 0.25f), C3(1.0f, 0.76f, 0.55f),
        listOf("tree1" to 2.6f, "tree2" to 2.9f, "tree3" to 2.5f, "tree1" to 2.6f, "tree2" to 2.9f, "bush" to 1.1f, "rock" to 1.0f, "sign" to 0.9f),
        listOf("vv_acorn", "vv_mushroom", "vv_berry"), 0),
    VTheme("night", R.string.vroum_theme_night, "vv_night",
        listOf(C3(0.01f, 0.01f, 0.05f), C3(0.03f, 0.03f, 0.12f), C3(0.06f, 0.06f, 0.22f), C3(0.12f, 0.10f, 0.30f)), C3(0.92f, 0.94f, 1f), 0.22f, false, true, true,
        C3(0.10f, 0.08f, 0.20f), C3(0.07f, 0.06f, 0.15f), false, C3(0.04f, 0.09f, 0.06f),
        C3(0.07f, 0.19f, 0.10f), C3(0.06f, 0.16f, 0.09f), C3(0.14f, 0.13f, 0.22f), C3(0.16f, 0.15f, 0.25f), C3(0.75f, 0.25f, 0.22f), C3(0.08f, 0.07f, 0.18f),
        listOf("tree1" to 2.6f, "lamp" to 2.4f, "tree3" to 2.5f, "owl_tree" to 2.6f, "tree2" to 2.9f, "bush" to 1.1f, "lamp" to 2.4f, "rock" to 1.0f),
        listOf("vv_acorn", "vv_mushroom", "vv_berry"), 1),
    VTheme("volcano", R.string.vroum_theme_volcano, "vv_volcano",
        listOf(C3(0.10f, 0.02f, 0.03f), C3(0.30f, 0.06f, 0.05f), C3(0.65f, 0.18f, 0.06f), C3(1.0f, 0.55f, 0.15f)), C3(1f, 0.35f, 0.1f), 0.40f, true, false, false,
        C3(0.16f, 0.06f, 0.05f), C3(0.10f, 0.04f, 0.04f), false, C3(0.12f, 0.05f, 0.04f),
        C3(0.22f, 0.13f, 0.10f), C3(0.19f, 0.11f, 0.09f), C3(0.16f, 0.14f, 0.14f), C3(0.19f, 0.16f, 0.16f), C3(1.0f, 0.45f, 0.1f), C3(0.55f, 0.18f, 0.08f),
        listOf("vol_rock1" to 1.4f, "vol_rock2" to 1.6f, "vol_geyser" to 1.5f, "vol_tree" to 2.4f, "vol_crystal" to 1.2f, "vol_rock1" to 1.4f, "vol_sign" to 0.9f, "vol_rock2" to 1.6f),
        listOf("vv_vol_p1", "vv_vol_p2", "vv_vol_p3"), 2, holeTex = "vv_vol_lava", logTex = "vv_vol_ridge"),
    VTheme("tokyo", R.string.vroum_theme_tokyo, "vv_tokyo",
        listOf(C3(0.02f, 0.01f, 0.08f), C3(0.08f, 0.03f, 0.22f), C3(0.30f, 0.08f, 0.45f), C3(0.85f, 0.25f, 0.55f)), C3(0.95f, 0.95f, 1f), 0.20f, false, true, true,
        C3(0.09f, 0.05f, 0.18f), C3(0.06f, 0.03f, 0.13f), true, C3(0.08f, 0.06f, 0.14f),
        C3(0.17f, 0.17f, 0.21f), C3(0.15f, 0.15f, 0.19f), C3(0.11f, 0.11f, 0.15f), C3(0.13f, 0.13f, 0.17f), C3(0.2f, 0.9f, 1.0f), C3(0.30f, 0.10f, 0.40f),
        listOf("tk_bld1" to 4.2f, "tk_bld2" to 2.6f, "tk_bld3" to 4.0f, "tk_torii" to 2.4f, "tk_lantern" to 2.2f, "tk_sakura" to 2.6f, "tk_bld2" to 2.6f, "tk_lantern" to 2.2f),
        listOf("vv_tk_p1", "vv_tk_p2", "vv_tk_p3"), 3, logTex = "vv_tk_cat"),
    VTheme("snow", R.string.vroum_theme_snow, "vv_snow",
        listOf(C3(0.45f, 0.62f, 0.90f), C3(0.62f, 0.76f, 0.95f), C3(0.80f, 0.88f, 0.98f), C3(0.98f, 0.92f, 0.85f)), C3(1f, 0.97f, 0.85f), 0.26f, false, false, false,
        C3(0.86f, 0.90f, 0.98f), C3(0.72f, 0.80f, 0.94f), false, C3(0.90f, 0.94f, 1.0f),
        C3(0.90f, 0.94f, 1.0f), C3(0.84f, 0.90f, 0.98f), C3(0.30f, 0.32f, 0.40f), C3(0.33f, 0.35f, 0.43f), C3(0.35f, 0.65f, 0.95f), C3(0.85f, 0.90f, 1.0f),
        listOf("sn_pine" to 2.8f, "sn_snowman" to 1.5f, "sn_pine" to 2.8f, "sn_gift" to 1.4f, "sn_santa" to 1.9f, "sn_cabin" to 2.8f, "sn_reindeer" to 1.6f, "sn_pine" to 2.6f),
        listOf("vv_sn_p1", "vv_sn_p2", "vv_sn_p3"), 4, holeTex = "vv_sn_ice", logTex = "vv_sn_drift"),
)

/** 0..3 étoiles d'une partie (même barème que l'écran de fin). */
internal fun vroumStarCount(endless: Boolean, cleared: Boolean, score: Int, dist: Int): Int = when {
    endless -> when { dist >= 2500 -> 3; dist >= 1200 -> 2; else -> 1 }
    cleared -> when { score >= 1500 -> 3; score >= 700 -> 2; else -> 1 }
    else -> when { dist >= 1500 -> 2; dist >= 600 -> 1; else -> 0 }
}

internal data class VVehicle(val key: String, val nameRes: Int)
internal val VEHICLES = listOf(
    VVehicle("car", R.string.vroum_v_car), VVehicle("monster", R.string.vroum_v_monster),
    VVehicle("firetruck", R.string.vroum_v_firetruck), VVehicle("moto", R.string.vroum_v_moto),
    VVehicle("tank", R.string.vroum_v_tank), VVehicle("dragon", R.string.vroum_v_dragon),
    VVehicle("lion", R.string.vroum_v_lion), VVehicle("trex", R.string.vroum_v_trex),
    VVehicle("rocket", R.string.vroum_v_rocket), VVehicle("tractor", R.string.vroum_v_tractor),
    VVehicle("excavator", R.string.vroum_v_excavator), VVehicle("bear", R.string.vroum_v_bear)
)
/** Familles d'animation / de son : créatures qui courent, dragon qui vole, fusée qui flotte. */
internal val WALKER_KEYS = setOf("lion", "trex", "bear")
internal val CREATURE_KEYS = setOf("dragon", "lion", "trex", "bear")
internal val ENEMY_KEYS = listOf("en_car1", "en_car2", "en_truck", "en_bus", "en_van")
internal val PASSENGER_KEYS = listOf("pass_rabbit", "pass_owl", "pass_fox")
internal val CHEER_KEYS = listOf("cheer_rabbit", "cheer_squirrel", "cheer_owl")
internal class VEnemy(var lane: Int, var z: Float, val kind: Int, val mps: Float) { var crushed = -1f; var bounce = 0f }
internal class VPick(var lane: Int, var z: Float, val type: VItem, val variant: Int) { var t = 0f }
internal class VShot(var z: Float, val lane: Int)
internal class VPart(var x: Float, var y: Float, var z: Float, var vx: Float, var vy: Float, var life: Float, val maxLife: Float,
                     val r: Float, val g: Float, val b: Float, val size: Float, val floaty: Boolean = false)
internal class VText(var x: Float, var y: Float, val z: Float, val text: String, var life: Float, val color: Color, val big: Boolean = false) {
    @Volatile var sx = -1000f; @Volatile var sy = -1000f
}
internal class VDecor(var z: Float, var lateral: Float, val kind: Int, val cheer: Int = -1, val flag: Boolean = false) { var arms = 0f }

private class VSprites(private val ctx: android.content.Context) {
    private fun load(name: String): ImageBitmap? {
        val id = ctx.resources.getIdentifier(name, "drawable", ctx.packageName)
        return if (id != 0) try { ImageBitmap.imageResource(ctx.resources, id) } catch (_: Exception) { null } else null
    }
    val vehicles = VEHICLES.map { load("vr_${it.key}") ?: load("vv_${it.key}") }
    val title = load("vv_title"); val village = load("vv_village"); val empty = load("vv_empty")
    val cards = THEMES.map { load(it.card) }
}

// ---------------------------------------------------------------------------
// Modèle de jeu
// ---------------------------------------------------------------------------

/** [diff] : 0 = Facile (1 min 30 chrono, invincible, pas d'essence), 1 = Moyen (le vrai jeu), 2 = Difficile (rapide, essence qui file, chocs chers). */
internal class VroumGame(val endless: Boolean, val vehicle: Int, val diff: Int, val theme: VTheme, val trickLabel: String, val cannonLabel: String) {
    companion object {
        const val LANE_M = 3.2f
        const val Z_FAR = 300f
        const val PLAYER_Z = 18f
        const val LEVEL_LEN = 2000f
        const val INTRO_DUR = 3.6f
    }
    fun groundY(zw: Float): Float = 1.6f * sin(zw * 0.030f) + 0.8f * sin(zw * 0.071f + 1f)
    fun xCenter(z: Float): Float = curve * z * z * 0.0012f
    var curve = 0f; private var curveTarget = 0f; private var nextCurveAt = 120f

    var introT = INTRO_DUR          // décompte 3-2-1-GO (caméra qui descend)
    private var introStep = 3
    // ---- voies : 3 au départ ; en PAYSAGE (tablette) la route s'élargit à 5 à mi-course, 7 loin en sans fin
    @Volatile var wide = false      // écran plus large que haut (posé par le renderer)
    var lanes = 3
    var oldLanes = 3
    var widenStart = -1f            // z monde où la route commence à s'élargir (-1 = jamais)
    fun roadHalfFor(n: Int) = n * LANE_M / 2f + 0.9f
    fun roadHalfAt(zw: Float): Float {
        if (widenStart < 0f) return roadHalfFor(lanes)
        val k = ((zw - widenStart) / 30f).coerceIn(0f, 1f)
        return roadHalfFor(oldLanes) + (roadHalfFor(lanes) - roadHalfFor(oldLanes)) * k
    }
    fun lanesAt(zw: Float): Int = if (widenStart >= 0f && zw < widenStart) oldLanes else lanes
    /** largeur maximale possible pour ce niveau (décor posé hors de la route future) */
    val decorHalf: Float get() = roadHalfFor(if (wide) (if (endless) 7 else 5) else 3) + 0.4f
    /** voies accessibles au joueur : les nouvelles seulement une fois l'élargissement arrivé sous les roues */
    private val laneLo: Int get() = if (widenStart >= 0f && dist + PLAYER_Z < widenStart + 30f) (lanes - oldLanes) / 2 else 0
    private val laneHi: Int get() = if (widenStart >= 0f && dist + PLAYER_Z < widenStart + 30f) lanes - 1 - (lanes - oldLanes) / 2 else lanes - 1
    fun laneX(lane: Int) = (lane - (lanes - 1) / 2f) * LANE_M
    var lane = 1
    var lx = 0f
    var jumpT = -1f
    val JUMP_DUR = 0.9f
    var landedT = 0f                // écrasement à l'atterrissage (s restantes)
    var blastT = 0f                 // onde de choc d'atterrissage (s restantes)
    private var trickDone = false
    var giant = 0; var giantT = 0f
    var cannonT = 0f; private var cannonCd = 0f
    var invuln = 0f
    var slideT = 0f; var slideDir = 0
    var turboT = 0f
    var superT = 0f                 // SUPER boost (6 s, x2.1, musique spéciale)
    var jumpCd = 0f                 // délai entre deux sauts
    val JUMP_CD = 3.2f
    var passengerT = 0f; var passengerKind = -1
    var fuel = 100f
    // Course (défaut) : au CHRONO — arriver au village (2 000 m) avant la fin du temps ; Arcade (endless) : à l'ESSENCE
    val timeLimit = if (!endless) (when (diff) { 0 -> 130f; 2 -> 80f; else -> 105f }) else -1f
    var timeLeft = timeLimit
    var timeUp = false
    var score = 0; var combo = 0; var comboTimer = 0f
    val mult: Int get() = (1 + combo / 4).coerceAtMost(8)
    var dist = 0f
    var elapsed = 0f
    var nextFlag = 500f
    var sputter = -1f
    var over = false; var cleared = false
    var shake = 0f
    var hornCd = 0f
    var startGrace = 0.8f            // les premiers ennemis arrivent vite : pas de début « vide »
    @Volatile var frame = 0
    var leanDeg = 0f            // inclinaison latérale (changement de file), degrés
    private var prevLx = 0f
    private var fxCd = 0f

    val enemies = ArrayList<VEnemy>()
    val picks = ArrayList<VPick>()
    val shots = ArrayList<VShot>()
    val parts = ArrayList<VPart>()
    val texts = ArrayList<VText>()
    @Volatile var textSnap: List<VText> = emptyList()
    val decor = ArrayList<VDecor>()
    private var spawnCd = 0.3f
    private var fuelSince = 0f; private var starSince = 9f; private var passengerSince = 10f; private var cannonSince = 12f; private var cheerCd = 3f
    private var superSince = 14f; private var ambientCd = 0f
    private var hazardSince = 0f
    var jumpedLabel = "Sauté !"
    /** Obstacles à sauter (trous, troncs) : 2e moitié d'une Course, après 20 s en Arcade. */
    private val hazardsOn: Boolean get() = if (endless) elapsed > INTRO_DUR + 20f else dist > LEVEL_LEN * 0.5f
    val events = ConcurrentLinkedQueue<String>()
    private fun ev(s: String) { events.add(s) }

    init {
        // la route n'est pas vide au décompte : quelques objets et un ennemi déjà posés devant
        for (i in 0 until 5) picks.add(VPick(Random.nextInt(lanes), 60f + i * 22f, listOf(VItem.ACORN, VItem.MUSHROOM, VItem.BERRY, VItem.TURBO, VItem.ACORN)[i], Random.nextInt(3)))
        enemies.add(VEnemy(Random.nextInt(lanes), 130f, Random.nextInt(ENEMY_KEYS.size), 7f))
        var z = 8f
        while (z < Z_FAR + 40f) {
            for (side in listOf(-1f, 1f)) {
                val k = Random.nextInt(theme.decor.size)
                decor.add(VDecor(z + Random.nextFloat() * 6f, side * (roadHalfFor(3) + 1.5f + Random.nextFloat() * 7f), k))
            }
            z += 11f
        }
    }

    /** Passage à [n] voies : la route s'élargit 40 m devant, les entités existantes gardent leur position (ré-indexation). */
    private fun widenTo(n: Int) {
        val shift = (n - lanes) / 2
        oldLanes = lanes; lanes = n
        widenStart = dist + 40f
        lane += shift
        for (e in enemies) e.lane += shift
        for (p in picks) p.lane += shift
        shots.clear()
        // les arbres trop proches de la future route sont repoussés
        val half = roadHalfFor(n)
        for (d in decor) if (d.cheer < 0 && !d.flag && abs(d.lateral) < half + 1.2f) d.lateral = (if (d.lateral < 0) -1f else 1f) * (half + 1.2f + Random.nextFloat() * 4f)
        texts.add(VText(0f, 4.5f, 40f, "$n ${lanesLabel}", 2.0f, V_CYAN, big = true))
        ev("widen")
    }
    var lanesLabel = "voies"
    var superLabel = "SUPER BOOST!"

    // ---- FANTÔME : rejoue le meilleur temps de ce circuit (Course). Échantillons (dist en dm, voie) toutes les 0,25 s.
    var ghostD: IntArray? = null; var ghostL: IntArray? = null
    var ghostVeh = -1; var ghostTime = 0f
    val raceT: Float get() = (elapsed - INTRO_DUR).coerceAtLeast(0f)
    fun ghostDist(): Float {
        val d = ghostD ?: return -1f
        val t = raceT / 0.25f; val i = t.toInt()
        if (i >= d.size - 1) return d.last() / 10f
        val f = t - i; return (d[i] * (1f - f) + d[i + 1] * f) / 10f
    }
    fun ghostLane(): Int { val l = ghostL ?: return 1; val i = (raceT / 0.25f).toInt(); return if (i < l.size) l[i] else l.last() }
    val ghostFrac: Float get() = if (ghostD == null) -1f else (ghostDist() / LEVEL_LEN).coerceIn(0f, 1f)
    // enregistrement de MA course (pour devenir le prochain fantôme si je bats le temps)
    private val recD = ArrayList<Int>(); private val recL = ArrayList<Int>(); private var recAcc = 0f
    fun ghostRecording(): Triple<IntArray, IntArray, Float> = Triple(recD.toIntArray(), recL.toIntArray(), raceT)
    /** Couleur arc-en-ciel cyclique. */
    fun rainbow(t: Float): Triple<Float, Float, Float> {
        val h = ((t % 1f) + 1f) % 1f * 6f; val i = h.toInt(); val f = h - i
        return when (i) { 0 -> Triple(1f, f, 0f); 1 -> Triple(1f - f, 1f, 0f); 2 -> Triple(0f, 1f, f); 3 -> Triple(0f, 1f - f, 1f); 4 -> Triple(f, 0f, 1f); else -> Triple(1f, 0f, 1f - f) }
    }

    val inIntro: Boolean get() = introT > 0f
    val mps: Float get() {
        if (sputter >= 0f) return max(0f, 18f * (1f - sputter / 2.6f))
        val base = when (diff) {
            0 -> 16f
            2 -> 24f + min(10f, dist / 500f)
            else -> if (endless) 18f + min(14f, dist / 600f) else 18f
        }
        val intro = if (inIntro) 0.25f + 0.75f * (1f - (introT / INTRO_DUR).coerceIn(0f, 1f)) else 1f
        return base * intro * (if (superT > 0f) 2.1f else if (turboT > 0f) 1.7f else 1f)
    }
    /** Intensité du boost pour les effets (0 rien, 1 turbo, 2 super). */
    val boostLevel: Int get() = if (superT > 0f) 2 else if (turboT > 0f) 1 else 0
    val speedFactor: Float get() = mps / 18f

    fun coveredLanes(): IntRange = when (giant) {
        0 -> lane..lane
        1 -> if (lane < lanes - 1) lane..lane + 1 else lane - 1..lane
        else -> if (lanes <= 3) 0..2 else (lane - 1).coerceAtLeast(0)..(lane + 1).coerceAtMost(lanes - 1)
    }
    private val targetLateral: Float get() { val r = coveredLanes(); return (r.first + r.last) / 2f - (lanes - 1) / 2f }
    val jumping: Boolean get() = jumpT >= 0f
    val jumpFrac: Float get() = if (jumping) sin(PI.toFloat() * (jumpT / JUMP_DUR).coerceIn(0f, 1f)) else 0f
    private val airborne: Boolean get() = jumping && jumpT > JUMP_DUR * 0.14f && jumpT < JUMP_DUR * 0.86f

    fun moveLane(d: Int) {
        if (over || sputter >= 0f || inIntro) return
        val n = (lane + d).coerceIn(laneLo, laneHi)
        if (n != lane) { lane = n; ev("lane") }
    }
    fun jump() { if (over || jumping || sputter >= 0f || inIntro || jumpCd > 0f) return; jumpT = 0f; jumpCd = JUMP_CD; trickDone = false; ev("jump") }
    fun horn() { if (hornCd <= 0f) { hornCd = 1.2f; ev("horn") } }

    fun update(dt: Float) {
        elapsed += dt
        if (hornCd > 0f) hornCd -= dt
        if (shake > 0f) shake = max(0f, shake - dt * 60f)
        if (invuln > 0f) invuln -= dt
        if (landedT > 0f) landedT -= dt
        if (blastT > 0f) blastT -= dt
        if (introT > 0f) {
            introT -= dt
            val step = kotlin.math.ceil(introT.toDouble()).toInt()
            if (step < introStep && step >= 1) { introStep = step; ev("count") }
            if (introT <= 0f) { introT = 0f; ev("go") }
        } else if (startGrace > 0f) startGrace -= dt
        val sp = mps
        val dz = sp * dt
        if (!over && sputter < 0f) dist += dz
        // trace de la course (fantôme) : un point (dist, voie) toutes les 0,25 s après le départ
        if (!endless && !inIntro && !over && !cleared) {
            recAcc += dt
            while (recAcc >= 0.25f) { recAcc -= 0.25f; recD.add((dist * 10f).toInt()); recL.add(lane) }
        }

        if (dist > nextCurveAt) {
            nextCurveAt = dist + 220f + Random.nextFloat() * 260f
            curveTarget = if (Random.nextFloat() < 0.35f) 0f else (Random.nextFloat() * 1.6f - 0.8f)
        }
        curve += (curveTarget - curve) * min(1f, dt * 0.5f)

        var target = targetLateral
        if (slideT > 0f) { slideT -= dt; target += slideDir * 0.22f * sin(slideT * 18f) }
        lx += (target - lx) * min(1f, dt * 9f)
        // inclinaison proportionnelle à la vitesse latérale (lissée)
        val lxVel = if (dt > 0f) (lx - prevLx) / dt else 0f
        prevLx = lx
        leanDeg += ((lxVel * 9f).coerceIn(-16f, 16f) - leanDeg) * min(1f, dt * 8f)
        // effets continus : poussière derrière les roues / fumée / flammes de la fusée
        fxCd -= dt
        if (fxCd <= 0f && sp > 2f && !over && parts.size < 140) {
            fxCd = 0.06f
            val x = lx * LANE_M; val gy = groundY(dist + PLAYER_Z)
            when (vehicle) {
                8 -> parts.add(VPart(x + (Random.nextFloat() - 0.5f) * 0.4f, gy + 0.3f, PLAYER_Z - 0.4f, (Random.nextFloat() - 0.5f) * 1.5f, -1f, 0.25f, 0.3f, 0.35f, 0.75f, 1f, 0.22f))
                5, 6, 7, 11 -> if (Random.nextFloat() < 0.6f) parts.add(VPart(x + (Random.nextFloat() - 0.5f) * 1.6f, gy + 0.1f, PLAYER_Z - 0.8f, (Random.nextFloat() - 0.5f) * 1.2f, 1.2f + Random.nextFloat(), 0.4f, 0.5f, 0.6f, 0.5f, 0.35f, 0.16f))
                else -> {
                    parts.add(VPart(x + (if (Random.nextBoolean()) -0.9f else 0.9f), gy + 0.05f, PLAYER_Z - 0.9f, (Random.nextFloat() - 0.5f) * 1.5f, 0.8f + Random.nextFloat(), 0.35f, 0.45f, 0.62f, 0.55f, 0.42f, 0.14f))
                    if (Random.nextFloat() < 0.35f) parts.add(VPart(x + 0.5f, gy + 0.7f, PLAYER_Z - 1.0f, 0.3f, 1.4f, 0.5f, 0.6f, 0.75f, 0.75f, 0.78f, 0.2f))
                }
            }
        }

        if (jumping) { jumpT += dt; if (jumpT >= JUMP_DUR) { jumpT = -1f; land() } }
        if (jumpCd > 0f) jumpCd -= dt
        if (turboT > 0f) turboT -= dt
        if (superT > 0f) { superT -= dt; if (superT <= 0f) { superT = 0f; ev("boostEnd") } }
        // traînée de feu pendant un boost (turbo : cyan/orange, super : arc-en-ciel)
        if (boostLevel > 0 && !over && parts.size < 170 && sp > 2f) {
            val x = lx * LANE_M; val gy = groundY(dist + PLAYER_Z)
            repeat(if (boostLevel == 2) 3 else 2) {
                val k = Random.nextFloat()
                val (cr, cg, cb) = if (boostLevel == 2) rainbow(elapsed * 3f + k) else Triple(0.3f + 0.7f * k, 0.6f + 0.3f * k, 1f)
                parts.add(VPart(x + (Random.nextFloat() - 0.5f) * 1.4f, gy + 0.3f + Random.nextFloat() * 0.8f, PLAYER_Z - 1.2f, (Random.nextFloat() - 0.5f) * 2f, 1.5f + Random.nextFloat() * 2f, 0.3f + Random.nextFloat() * 0.25f, 0.5f, cr, cg, cb, 0.3f + Random.nextFloat() * 0.35f, floaty = true))
            }
        }
        // particules d'ambiance du thème
        ambientCd -= dt
        if (ambientCd <= 0f && parts.size < 150 && theme.ambient != 0) {
            ambientCd = if (theme.ambient == 4) 0.03f else 0.08f
            val ax = (Random.nextFloat() - 0.5f) * 30f; val az = 10f + Random.nextFloat() * 90f
            val gy = groundY(dist + az)
            when (theme.ambient) {
                1 -> parts.add(VPart(ax, gy + 0.4f + Random.nextFloat() * 2f, az, (Random.nextFloat() - 0.5f) * 0.6f, (Random.nextFloat() - 0.3f) * 0.5f, 2.5f + Random.nextFloat() * 2f, 4f, 1f, 0.95f, 0.45f, 0.14f, floaty = true))
                2 -> parts.add(VPart(ax, gy + Random.nextFloat() * 1.5f, az, (Random.nextFloat() - 0.5f) * 0.8f, 1.2f + Random.nextFloat() * 1.5f, 1.8f + Random.nextFloat() * 1.5f, 3f, 1f, 0.55f + 0.3f * Random.nextFloat(), 0.15f, 0.12f, floaty = true))
                3 -> parts.add(VPart(ax, gy + 5f + Random.nextFloat() * 4f, az, 1.2f + Random.nextFloat(), -0.8f - Random.nextFloat() * 0.6f, 4f, 4f, 1f, 0.72f, 0.82f, 0.22f, floaty = true))
                4 -> parts.add(VPart(ax, gy + 6f + Random.nextFloat() * 5f, az, (Random.nextFloat() - 0.5f) * 0.8f, -1.2f - Random.nextFloat() * 1.2f, 5f, 5f, 1f, 1f, 1f, 0.16f + Random.nextFloat() * 0.12f, floaty = true))
            }
        }
        if (passengerT > 0f) { passengerT -= dt; if (passengerT <= 0f) passengerKind = -1 }
        if (giantT > 0f) { giantT -= dt; if (giantT <= 0f) endGiant() }
        if (comboTimer > 0f) { comboTimer -= dt; if (comboTimer <= 0f) combo = 0 }
        if (cannonT > 0f) {
            cannonT -= dt; cannonCd -= dt
            if (cannonCd <= 0f && !over) {
                cannonCd = 0.16f
                for (l in coveredLanes()) shots.add(VShot(PLAYER_Z + 1.5f, l))
                ev("shot")
            }
        }

        if (!over && sputter < 0f && !cleared && !inIntro) {
            if (timeLimit > 0f) {
                timeLeft -= dt
                if (timeLeft <= 0f) { timeLeft = 0f; timeUp = true; over = true; ev("timeup") }
            } else if (diff != 0) {
                fuel -= dt * (if (diff == 2) 1.9f else 1.35f) * (if (turboT > 0f) 1.3f else 1f)
                if (fuel <= 0f) { fuel = 0f; sputter = 0f; ev("sputter"); if (giant > 0) endGiant() }
            }
        }
        if (sputter >= 0f) { sputter += dt; if (sputter > 2.8f && !over) { over = true; ev("over") } }
        if (!endless && !cleared && dist >= LEVEL_LEN) { cleared = true; ev("clear") }
        // paysage : 3 voies -> 5 à mi-course (700 m en sans fin), -> 7 loin en sans fin
        if (wide && !over && !cleared) {
            if (lanes == 3 && dist >= (if (endless) 700f else LEVEL_LEN / 2f)) widenTo(5)
            else if (lanes == 5 && endless && dist >= 2500f) widenTo(7)
        }

        if (dist >= nextFlag && !cleared) {
            decor.add(VDecor(120f, -(decorHalf + 0.8f), 0, flag = true)); decor.add(VDecor(120f, decorHalf + 0.8f, 0, flag = true))
            texts.add(VText(0f, 4.5f, 30f, "${nextFlag.toInt()} m", 1.8f, V_GOLD, big = true))
            for (d in decor) if (d.cheer >= 0) d.arms = 2.2f
            ev("flag"); nextFlag += 500f
        }

        if (!over && sputter < 0f && !cleared && !inIntro) {
            spawnCd -= dt * speedFactor
            fuelSince += dt; starSince += dt; passengerSince += dt; cannonSince += dt; superSince += dt; hazardSince += dt; cheerCd -= dt
            // cadence soutenue dès le départ (~1 chose toutes les 0,7 s à 18 m/s), puis un peu plus dense encore
            if (spawnCd <= 0f) { spawnCd = (if (endless) 0.62f else 0.72f) - min(0.2f, dist / 8000f); spawnOne() }
            if (cheerCd <= 0f) {
                cheerCd = 2.5f + Random.nextFloat() * 3f
                val side = if (Random.nextBoolean()) -1f else 1f
                decor.add(VDecor(Z_FAR, side * (decorHalf + 1.0f), -1, cheer = Random.nextInt(CHEER_KEYS.size)))
            }
        }

        // tirs de canons : avancent, détruisent ennemis et flaques
        val sit = shots.iterator()
        while (sit.hasNext()) {
            val sh = sit.next(); sh.z += 70f * dt
            if (sh.z > 220f) { sit.remove(); continue }
            var hit = false
            for (e in enemies) if (e.crushed < 0f && e.lane == sh.lane && abs(e.z - sh.z) < 2.5f) {
                e.crushed = 0f; combo++; comboTimer = 2.6f
                addScore(50, laneX(e.lane), e.z, V_ORANGE); burst(laneX(e.lane), e.z, 1f, 0.6f, 0.2f, 12)
                ev("shotHit"); hit = true; break
            }
            if (!hit) { val pit0 = picks.iterator(); while (pit0.hasNext()) { val p = pit0.next(); if (p.type == VItem.PUDDLE && p.lane == sh.lane && abs(p.z - sh.z) < 2.5f) { pit0.remove(); burst(laneX(p.lane), p.z, 0.55f, 0.35f, 0.17f, 8); hit = true; break } } }
            if (hit) sit.remove()
        }

        val eit = enemies.iterator()
        while (eit.hasNext()) {
            val e = eit.next()
            e.z -= (sp - e.mps) * dt
            if (e.bounce > 0f) { e.bounce -= dt; e.z += 14f * dt }
            if (e.crushed >= 0f) { e.crushed += dt; if (e.crushed > 0.55f) { eit.remove(); continue } }
            if (e.z < 0.5f || e.z > Z_FAR + 60f) { eit.remove(); continue }
            if (e.crushed < 0f && e.z > PLAYER_Z - 1.6f && e.z < PLAYER_Z + 2.0f && e.lane in coveredLanes()) {
                if (giant > 0) crush(e)
                else if (airborne) { if (!trickDone) { trickDone = true; addScore(25, lx * LANE_M, PLAYER_Z, V_CYAN); texts.add(VText(lx * LANE_M, 4f, PLAYER_Z, trickLabel, 1.0f, V_CYAN, big = true)); ev("trick") } }
                else if (invuln <= 0f) bump(e)
            }
        }
        val pit = picks.iterator()
        while (pit.hasNext()) {
            val p = pit.next()
            p.z -= dz; p.t += dt
            if (p.z < 0.5f) { pit.remove(); continue }
            if (p.z > PLAYER_Z - 1.4f && p.z < PLAYER_Z + 1.6f && p.lane in coveredLanes()) {
                val ground = p.type == VItem.PUDDLE || p.type == VItem.TURBO
                if (ground && airborne) continue
                collect(p); pit.remove()
            }
        }
        val dit = decor.iterator()
        while (dit.hasNext()) {
            val d = dit.next(); d.z -= dz
            if (d.arms > 0f) d.arms -= dt
            if (d.z < -3f) { if (d.kind >= 0 && d.cheer < 0 && !d.flag) d.z += Z_FAR + 40f + Random.nextFloat() * 8f else dit.remove() }
        }
        val prt = parts.iterator()
        while (prt.hasNext()) {
            val p = prt.next(); p.life -= dt
            p.x += p.vx * dt; p.y += p.vy * dt; if (!p.floaty) p.vy -= 9f * dt; p.z -= dz * (if (p.floaty) 1f else 0.2f)
            if (p.life <= 0f) prt.remove()
        }
        val tit = texts.iterator()
        while (tit.hasNext()) { val t = tit.next(); t.life -= dt; t.y += 1.2f * dt; if (t.life <= 0f) tit.remove() }
        textSnap = if (texts.isEmpty()) emptyList() else ArrayList(texts)
    }

    /** Atterrissage façon Mario Kart : écrasement, onde de choc qui détruit tout autour (sans points). */
    private fun land() {
        landedT = 0.25f; blastT = 0.5f; shake = max(shake, 14f); invuln = max(invuln, 0.6f)
        var any = false
        for (e in enemies) if (e.crushed < 0f && abs(e.z - PLAYER_Z) < 10f) { e.crushed = 0f; any = true; burst(laneX(e.lane), e.z, 1f, 0.85f, 0.4f, 8) }
        val pit = picks.iterator()
        while (pit.hasNext()) { val p = pit.next(); if (p.type == VItem.PUDDLE && abs(p.z - PLAYER_Z) < 10f) { pit.remove(); burst(laneX(p.lane), p.z, 0.55f, 0.35f, 0.17f, 6) } }
        // nuage de poussière bas et large (pas de « boule de feu ») : particules terre + herbe qui roulent
        val x = lx * LANE_M
        if (parts.size < 150) repeat(26) {
            val a = Random.nextFloat() * 2f * PI.toFloat(); val s = 3f + Random.nextFloat() * 5f
            val gy = groundY(dist + PLAYER_Z) + 0.15f
            val k = Random.nextFloat()
            parts.add(VPart(x + cos(a) * 0.8f, gy, PLAYER_Z + sin(a) * 0.8f, cos(a) * s, 0.8f + Random.nextFloat() * 1.6f, 0.45f + Random.nextFloat() * 0.35f, 0.8f,
                0.55f + 0.2f * k, 0.45f + 0.25f * k, 0.3f + 0.15f * k, 0.35f + Random.nextFloat() * 0.35f))
        }
        ev(if (any) "landBlast" else "land")
    }

    private fun spawnOne() {
        val lane = Random.nextInt(lanes)
        val z = 160f
        val wantEnemy = startGrace <= 0f && Random.nextFloat() < (when (diff) { 0 -> 0.34f; 2 -> 0.62f; else -> 0.5f })
        if (wantEnemy) {
            val near = enemies.count { abs(it.z - z) < 18f }
            if (near <= 1 && enemies.none { it.lane == lane && abs(it.z - z) < 26f }) {
                enemies.add(VEnemy(lane, z, Random.nextInt(ENEMY_KEYS.size), 6f + Random.nextFloat() * 5f)); return
            }
        }
        val type = when {
            fuel < 30f && fuelSince > 3.5f -> VItem.FUEL
            starSince > 16f && Random.nextFloat() < 0.35f -> VItem.STAR
            cannonSince > 20f && cannonT <= 0f && Random.nextFloat() < 0.35f -> VItem.CANNON
            superSince > 22f && superT <= 0f && Random.nextFloat() < 0.4f -> VItem.SUPERBOOST
            hazardsOn && hazardSince > (if (diff == 2) 2.5f else 4f) && Random.nextFloat() < 0.45f -> { hazardSince = 0f; if (Random.nextBoolean()) VItem.HOLE else VItem.LOG }
            passengerKind < 0 && passengerSince > 18f && Random.nextFloat() < 0.4f -> VItem.PASSENGER
            else -> {
                val r = Random.nextFloat()
                when {
                    r < 0.26f -> VItem.ACORN
                    r < 0.42f -> VItem.MUSHROOM
                    r < 0.54f -> VItem.BERRY
                    r < 0.66f && fuelSince > 6f -> VItem.FUEL
                    r < 0.88f -> VItem.TURBO           // boosts nettement plus fréquents
                    else -> VItem.PUDDLE
                }
            }
        }
        if (type == VItem.FUEL) fuelSince = 0f
        if (type == VItem.STAR) starSince = 0f
        if (type == VItem.SUPERBOOST) superSince = 0f
        if (type == VItem.CANNON) cannonSince = 0f
        if (type == VItem.PASSENGER) passengerSince = 0f
        if (picks.none { it.lane == lane && abs(it.z - z) < 12f }) picks.add(VPick(lane, z, type, Random.nextInt(3)))
    }

    private fun addScore(n: Int, x: Float, z: Float, col: Color) {
        val v = n * mult * (if (passengerKind >= 0) 2 else 1)
        score += v
        texts.add(VText(x, 2.2f, z, "+$v", 0.9f, col))
    }

    private fun collect(p: VPick) {
        val x = laneX(p.lane); val z = p.z
        when (p.type) {
            VItem.FUEL -> { fuel = min(100f, fuel + 35f); ev("fuel"); burst(x, z, 1f, 0.25f, 0.25f, 10); texts.add(VText(x, 2.2f, z, "+35", 0.9f, V_GREEN)) }
            VItem.STAR -> { startGiant(); addScore(50, x, z, V_GOLD); burst(x, z, 1f, 0.82f, 0.3f, 24) }
            VItem.CANNON -> { cannonT = 8f; cannonCd = 0f; ev("cannon"); texts.add(VText(x, 2.6f, z, cannonLabel, 1.2f, V_CYAN, big = true)); burst(x, z, 0.3f, 0.85f, 1f, 16) }
            VItem.ACORN -> { addScore(10, x, z, V_GOLD); ev("pick1"); burst(x, z, 0.75f, 0.5f, 0.25f, 6) }
            VItem.MUSHROOM -> { addScore(20, x, z, V_GOLD); ev("pick2"); burst(x, z, 1f, 0.25f, 0.25f, 6) }
            VItem.BERRY -> { addScore(30, x, z, V_GOLD); ev("pick3"); burst(x, z, 0.42f, 0.49f, 1f, 6) }
            VItem.TURBO -> { turboT = max(turboT, 3f); ev("turbo"); texts.add(VText(x, 2.2f, z, "TURBO!", 1.0f, V_CYAN)); shake = max(shake, 6f) }
            VItem.SUPERBOOST -> { superT = 6f; turboT = max(turboT, 6f); ev("superboost"); texts.add(VText(x, 3.0f, z, superLabel, 1.6f, V_GOLD, big = true)); burst(x, z, 1f, 0.85f, 0.2f, 30); shake = max(shake, 12f); invuln = max(invuln, 6f) }
            VItem.HOLE, VItem.LOG -> {
                if (airborne || jumping) { addScore(20, x, z, V_CYAN); texts.add(VText(x, 3.2f, z, jumpedLabel, 1.0f, V_CYAN, big = true)); ev("trick") }
                else if (giant > 0) { burst(x, z, 0.6f, 0.4f, 0.25f, 12); ev("crush") }
                else if (invuln <= 0f) {
                    // tombé dans le trou / heurté le tronc : gros cahot, on perd du temps (Course) ou de l'essence (Arcade)
                    if (diff != 0) { if (timeLimit > 0f) timeLeft = max(0.5f, timeLeft - (if (diff == 2) 5f else 3f)) else fuel = max(0f, fuel - (if (diff == 2) 25f else 15f)); combo = 0; comboTimer = 0f }
                    invuln = 1.2f; shake = max(shake, 14f); slideT = 0.5f; slideDir = if (Random.nextBoolean()) 1 else -1
                    burst(x, z, 0.5f, 0.4f, 0.3f, 14); ev("bump")
                }
            }
            VItem.PUDDLE -> if (giant == 0) {
                slideT = 0.7f; slideDir = if (lane <= laneLo) 1 else if (lane >= laneHi) -1 else (if (Random.nextBoolean()) 1 else -1)
                lane = (lane + slideDir).coerceIn(laneLo, laneHi)
                ev("splash"); burst(x, z, 0.55f, 0.35f, 0.17f, 10)
            }
            VItem.PASSENGER -> {
                passengerKind = p.variant % PASSENGER_KEYS.size; passengerT = 10f
                ev("passenger"); texts.add(VText(x, 2.4f, z, "x2!", 1.1f, V_PINK, big = true)); burst(x, z, 1f, 0.36f, 0.64f, 12)
            }
        }
    }

    private fun startGiant() {
        if (giant == 0) { giant = 1; giantT = 8f; ev("giant1") } else { giant = 2; giantT = max(giantT, 6f) + 3f; ev("giant2") }
        invuln = max(invuln, 0.5f); shake = max(shake, 10f)
    }
    private fun endGiant() {
        if (giant == 0) return
        giant = 0; giantT = 0f
        val x = lx * LANE_M
        burst(x, PLAYER_Z, 1f, 0.82f, 0.3f, 40); burst(x, PLAYER_Z, 1f, 0.36f, 0.64f, 30); burst(x, PLAYER_Z, 0.29f, 0.88f, 1f, 30)
        shake = 16f; invuln = 1.2f
        ev("giantEnd")
    }
    private fun crush(e: VEnemy) {
        e.crushed = 0f; combo++; comboTimer = 2.6f
        addScore(100, laneX(e.lane), e.z, V_PINK); burst(laneX(e.lane), e.z, 1f, 0.82f, 0.3f, 14)
        shake = max(shake, 7f); ev("crush")
        if (combo % 5 == 0) { ev("cheer"); for (d in decor) if (d.cheer >= 0) d.arms = 1.8f }
    }
    private fun bump(e: VEnemy) {
        // Facile : invincible -> simple rebond, rien perdu. Sinon : du TEMPS en Course, de l'ESSENCE en Arcade.
        if (diff != 0) {
            if (timeLimit > 0f) timeLeft = max(0.5f, timeLeft - (if (diff == 2) 5f else 3f))
            else fuel = max(0f, fuel - (if (diff == 2) 25f else 15f))
            combo = 0; comboTimer = 0f
        }
        invuln = 1.1f; e.bounce = 0.6f
        shake = max(shake, 9f)
        burst(laneX(e.lane), e.z, 1f, 1f, 1f, 8)
        ev("bump")
    }
    private fun burst(x: Float, z: Float, r: Float, g: Float, b: Float, n: Int) {
        if (parts.size > 160) return
        val gy = groundY(dist + z) + 1f
        repeat(n) {
            val a = Random.nextFloat() * 2f * PI.toFloat(); val s = 2f + Random.nextFloat() * 4f
            parts.add(VPart(x, gy, z, cos(a) * s, abs(sin(a)) * s + 2f, 0.5f + Random.nextFloat() * 0.4f, 0.9f, r, g, b, 0.18f + Random.nextFloat() * 0.2f))
        }
    }
}

// ---------------------------------------------------------------------------
// Écran
// ---------------------------------------------------------------------------

@Composable
fun VroumScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val spr = remember { VSprites(ctx) }
    var phase by remember { mutableStateOf(VPhase.SELECT) }
    var vehicle by remember { mutableIntStateOf(0) }
    var endless by remember { mutableStateOf(false) }
    var diff by remember { mutableIntStateOf(1) }          // 0 facile, 1 moyen, 2 difficile
    var themeIdx by remember { mutableIntStateOf(0) }      // circuit : forêt, nuit, volcan, Tokyo, neige
    var runToken by remember { mutableIntStateOf(0) }
    var tick by remember { mutableIntStateOf(0) }
    var countdown by remember { mutableIntStateOf(0) }     // 3,2,1 puis 0 = GO affiché brièvement, -1 = rien
    val synthRes = remember { ctx.resources.getIdentifier("vv_synth", "raw", ctx.packageName) }
    val frenzyRes = remember { ctx.resources.getIdentifier("vv_frenzy", "raw", ctx.packageName) }
    val boostRes = remember { ctx.resources.getIdentifier("vv_boost", "raw", ctx.packageName).let { if (it != 0) it else ctx.resources.getIdentifier("vv_frenzy", "raw", ctx.packageName) } }
    val gameKey = if (endless) HighScores.Games.VROUM_ENDLESS else HighScores.Games.VROUM
    val controller = remember(runToken, gameKey) { ArcadeController(gameKey, 0, withLevels = false, speedRamp = false) }
    val trickLabel = stringResource(R.string.vroum_trick); val cannonLabel = stringResource(R.string.vroum_cannons)
    val lanesLabel = stringResource(R.string.vroum_lanes); val superLabel = stringResource(R.string.vroum_superboost)
    val jumpedLabel = stringResource(R.string.vroum_jumped)
    val game = remember(runToken) {
        if (phase != VPhase.PLAYING) null
        else VroumGame(endless, vehicle, diff, THEMES[themeIdx], trickLabel, cannonLabel).also { g ->
            g.lanesLabel = lanesLabel; g.superLabel = superLabel; g.jumpedLabel = jumpedLabel
            // charge le fantôme du meilleur temps pour ce circuit / cette difficulté (Course seulement)
            if (!endless) Settings.vroumGhost("${THEMES[themeIdx].key}_$diff")?.let { s ->
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
    LaunchedEffect(phase) {
        glView?.renderer?.paused = phase != VPhase.PLAYING
        if (phase != VPhase.PLAYING && synthRes != 0) GameMusic.playRes(ctx, synthRes)
    }

    val cheers = listOf(R.string.vroum_cheer1, R.string.vroum_cheer2, R.string.vroum_cheer3)
    // Résultat de la partie (écran enfant : étoiles + confettis, pas de saisie de nom façon arcade)
    var resScore by remember { mutableIntStateOf(0) }
    var resDist by remember { mutableIntStateOf(0) }
    var resCleared by remember { mutableStateOf(false) }
    var resRecord by remember { mutableStateOf(false) }
    var resTimeUp by remember { mutableStateOf(false) }
    fun finishRun(g: VroumGame, cleared: Boolean) {
        resScore = g.score; resDist = g.dist.toInt(); resCleared = cleared; resTimeUp = g.timeUp
        val best = HighScores.best(gameKey)
        // le mode Facile (invincible) n'entre pas au tableau des records
        resRecord = g.diff >= 1 && g.score > 0 && g.score > best
        if (g.diff >= 1) HighScores.record(gameKey, g.score)
        // étoiles mémorisées par circuit + mode + difficulté (affichées sur les cartes)
        Settings.setVroumStars("${g.theme.key}_${if (g.endless) "a" else "c"}${g.diff}", vroumStarCount(g.endless, cleared, g.score, g.dist.toInt()))
        // fantôme : si la course est finie plus vite que le fantôme en place, ma trace le remplace
        if (!g.endless && cleared) {
            val key = "${g.theme.key}_${g.diff}"
            val (d, l, time) = g.ghostRecording()
            val prevTime = Settings.vroumGhost(key)?.split(";")?.getOrNull(1)?.toFloatOrNull()
            if (d.size > 8 && (prevTime == null || time < prevTime))
                Settings.setVroumGhost(key, "${g.vehicle};${"%.2f".format(java.util.Locale.US, time)};" + d.indices.joinToString(",") { "${d[it]}:${l[it]}" })
        }
        phase = VPhase.END
    }
    fun hornFor(v: Int) {
        val fallback = { SoundEngine.playRealHorn(if (v == 1 || v == 4) 0.7f else 1f); Unit }
        when (v) {
            0 -> VehicleSounds.play(ctx, "car", 1500L, fallback)
            2 -> VehicleSounds.play(ctx, "fire_truck", 2200L, fallback)
            3 -> VehicleSounds.play(ctx, "motorcycle", 1600L, fallback)
            4, 9, 10 -> VehicleSounds.play(ctx, "tractor", 1600L, fallback)
            11 -> if (!SoundEngine.playRoar()) Animals.byKey("bear")?.let { AnimalSoundPlayer.play(ctx, it, sayNameAfter = false) } ?: fallback()
            8 -> VehicleSounds.play(ctx, "plane", 1800L, fallback)
            6 -> if (!SoundEngine.playRoar()) fallback()
            5, 7 -> if (!SoundEngine.playRoar()) Animals.byKey("elephant")?.let { AnimalSoundPlayer.play(ctx, it, sayNameAfter = false) } ?: fallback()
            else -> fallback()
        }
    }

    LaunchedEffect(game) {
        val g = game ?: return@LaunchedEffect
        countdown = 3
        if (synthRes != 0) GameMusic.playRes(ctx, synthRes)
        SoundEngine.playVvTick(0.8f)
        var engine = 0
        var engineCd = 0f
        try {
            while (true) {
                withFrameNanos { }
                while (true) {
                    val e = g.events.poll() ?: break
                    when (e) {
                        "count" -> { countdown = kotlin.math.ceil(g.introT.toDouble()).toInt().coerceIn(1, 3); SoundEngine.playVvTick(0.8f + (3 - countdown) * 0.15f) }
                        // moteur en boucle seulement pour les engins à moteur (pas pour le dragon, le lion, le T-Rex)
                        "go" -> { countdown = 0; SoundEngine.playVvGo(); if (VEHICLES[g.vehicle].key !in CREATURE_KEYS) engine = SoundEngine.startEngine() }
                        "lane" -> {}   // plus de « tic » à chaque changement de file (fatigant)
                        "widen" -> { SoundEngine.playVvSuccess(); Haptics.tap(ctx) }
                        "jump" -> SoundEngine.playJump()
                        "land" -> SoundEngine.playLand()
                        "landBlast" -> { SoundEngine.playBoom(); SoundEngine.playVvYahoo(); Haptics.tap(ctx) }
                        "trick" -> SoundEngine.playVvCoin()
                        "horn" -> hornFor(g.vehicle)
                        "bump" -> { SoundEngine.playBump(); Haptics.tap(ctx) }
                        "crush" -> { SoundEngine.playCrush(); Haptics.tap(ctx) }
                        "shot" -> SoundEngine.playShot()
                        "shotHit" -> SoundEngine.playCrush()
                        "cannon" -> SoundEngine.playVvSuccess()
                        "splash" -> SoundEngine.playSplash()
                        "turbo" -> SoundEngine.playTurbo()
                        "superboost" -> { SoundEngine.playTurbo(); SoundEngine.playVvYahoo(); Haptics.tap(ctx); if (boostRes != 0) GameMusic.playRes(ctx, boostRes) }
                        "boostEnd" -> { if (g.giant > 0 && frenzyRes != 0) GameMusic.playRes(ctx, frenzyRes) else if (synthRes != 0) GameMusic.playRes(ctx, synthRes) }
                        "fuel" -> SoundEngine.playFuel()
                        "pick1" -> SoundEngine.playPick(1)
                        "pick2" -> SoundEngine.playPick(2)
                        "pick3" -> SoundEngine.playPick(3)
                        "passenger" -> SoundEngine.playVvSuccess()
                        "giant1", "giant2" -> { SoundEngine.playStarPick(); if (frenzyRes != 0) GameMusic.playRes(ctx, frenzyRes) }
                        "giantEnd" -> { SoundEngine.playBoom(); if (g.superT > 0f && boostRes != 0) GameMusic.playRes(ctx, boostRes) else if (synthRes != 0) GameMusic.playRes(ctx, synthRes) }
                        "flag" -> { SoundEngine.playVvFanfare(); Tts.speak(ctx.getString(cheers.random())) }
                        "cheer" -> Tts.speak(ctx.getString(cheers.random()))
                        "sputter" -> { SoundEngine.playStall(); Sfx.stopStream(engine); engine = 0 }
                        "clear" -> { Sfx.play("vv_applause", 1f, 1f); Sfx.play("vv_fanfare2", 0.9f, 1f); Settings.setVroumEndlessUnlocked(true)
                                     Sfx.stopStream(engine); engine = 0; finishRun(g, true) }
                        "over" -> { Sfx.play("vv_applause", 0.6f, 0.9f); finishRun(g, false) }
                        "timeup" -> { SoundEngine.playVvGameOver(); Sfx.play("vv_applause", 0.6f, 0.9f); Sfx.stopStream(engine); engine = 0; finishRun(g, false) }
                    }
                }
                if (countdown == 0 && g.introT <= 0f && g.elapsed > VroumGame.INTRO_DUR + 1.1f) countdown = -1
                engineCd -= 1f / 60f
                if (engine != 0 && engineCd <= 0f) { engineCd = 0.15f; Sfx.setStreamRate(engine, 0.85f + 0.55f * g.speedFactor + (if (g.turboT > 0f) 0.3f else 0f)) }
                tick++
                if (phase != VPhase.PLAYING) break
            }
        } finally { Sfx.stopStream(engine) }
    }

    Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF7FD4FF), Color(0xFFB39DFF))))) {
        when (phase) {
            VPhase.SELECT -> SelectScreen(spr, vehicle, onBack = onBack, onPick = { vehicle = it; hornFor(it) },
                onNext = { SoundEngine.playVroumUi(); phase = VPhase.LEVEL })
            VPhase.LEVEL -> LevelScreen(spr, diff, endless, onDiff = { SoundEngine.playVroumUi(); diff = it }, onEndless = { SoundEngine.playVroumUi(); endless = it }, onBack = { phase = VPhase.SELECT },
                onPlay = { t -> SoundEngine.playVroumUi(); themeIdx = t; phase = VPhase.PLAYING; runToken++ })
            VPhase.PLAYING -> {
                val g = game
                if (g != null && glView != null) {
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
                    Canvas(Modifier.fillMaxSize()) {
                        @Suppress("UNUSED_EXPRESSION") tick
                        val snap = g.textSnap
                        val u = size.height / 100f
                        if (snap.isNotEmpty()) drawIntoCanvas { c ->
                            val paint = android.graphics.Paint().apply { isAntiAlias = true; textAlign = android.graphics.Paint.Align.CENTER; isFakeBoldText = true; setShadowLayer(6f, 0f, 3f, android.graphics.Color.argb(160, 0, 0, 0)) }
                            for (t in snap) {
                                if (t.sx < -500f) continue
                                val a = (t.life / 0.9f).coerceIn(0f, 1f)
                                paint.textSize = u * (if (t.big) 6.5f else 4f)
                                paint.color = android.graphics.Color.argb((a * 255).toInt(), (t.color.red * 255).toInt(), (t.color.green * 255).toInt(), (t.color.blue * 255).toInt())
                                c.nativeCanvas.drawText(t.text, t.sx, t.sy, paint)
                            }
                        }
                        if (g.giant > 0) drawRect(V_GOLD.copy(alpha = 0.25f + 0.2f * sin(g.elapsed * 10f)), size = size, style = Stroke(u * 1.6f))
                        if (g.cannonT > 0f) drawRect(V_CYAN.copy(alpha = 0.15f + 0.1f * sin(g.elapsed * 14f)), size = size, style = Stroke(u * 1.2f))
                    }
                    BoostOverlay(g, tick)
                    VroumHud(g, tick)
                    LaneHints(Modifier.align(Alignment.BottomCenter))
                    JumpButton(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 22.dp), cooldown = (g.jumpCd / g.JUMP_CD).coerceIn(0f, 1f), frame = tick) { glView.queueEvent { g.jump() } }
                    if (countdown >= 0) CountdownOverlay(countdown)
                }
                BackButton(onBack = { GameMusic.stop(); phase = VPhase.LEVEL }, modifier = Modifier.align(Alignment.TopStart))
            }
            VPhase.CLEAR, VPhase.END -> {
                ResultScreen(spr, cleared = resCleared, score = resScore, dist = resDist, record = resRecord, endless = endless, diff = diff, timeUp = resTimeUp,
                    vehicleImg = spr.vehicles.getOrNull(vehicle), best = HighScores.best(gameKey), bg = if (themeIdx == 0) spr.village else spr.cards.getOrNull(themeIdx),
                    onReplay = { SoundEngine.playVroumUi(); phase = VPhase.PLAYING; runToken++ },
                    onOther = { SoundEngine.playVroumUi(); phase = VPhase.LEVEL },
                    onVehicle = { SoundEngine.playVroumUi(); phase = VPhase.SELECT })
            }
        }
    }
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
    ) { Text(label, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, maxLines = 1, softWrap = false) }
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
            .padding(horizontal = 34.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, maxLines = 1, softWrap = false)
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
        val n = if (lvl == 2) 34 else 20
        for (i in 0 until n) {
            val a = (i / n.toFloat()) * 6.2832f + (i % 3) * 0.13f
            val flick = ((t * 22f + i * 1.7f) % 1f)
            val r0 = (0.28f + 0.5f * flick) * h; val r1 = r0 + (0.10f + 0.12f * (1f - flick)) * h
            val col = if (lvl == 2) { val (r, gg, b) = g.rainbow(t * 2f + i * 0.07f); Color(r, gg, b) } else V_CYAN
            drawLine(col.copy(alpha = 0.55f * (1f - flick)), Offset(cx + cos(a) * r0, cy + sin(a) * r0), Offset(cx + cos(a) * r1, cy + sin(a) * r1), strokeWidth = if (lvl == 2) 7f else 5f, cap = StrokeCap.Round)
        }
        val vig = if (lvl == 2) { val (r, gg, b) = g.rainbow(t * 1.5f); Color(r, gg, b) } else V_CYAN
        drawRect(Brush.radialGradient(listOf(Color.Transparent, vig.copy(alpha = if (lvl == 2) 0.35f else 0.22f)), center = Offset(cx, cy), radius = h * 0.75f), size = size)
    }
}

/** Repères tactiles gauche/droite en bas d'écran (translucides) : « toute la moitié gauche = gauche ». */
@Composable
private fun LaneHints(modifier: Modifier = Modifier) {
    val pulse = rememberInfiniteTransition(label = "hint").animateFloat(
        initialValue = 0.28f, targetValue = 0.55f, animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "p"
    ).value
    Row(modifier = modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 34.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Chevron(left = true, alpha = pulse); Chevron(left = false, alpha = pulse)
    }
}

@Composable
private fun Chevron(left: Boolean, alpha: Float) {
    Box(Modifier.size(64.dp).alpha(alpha).clip(RoundedCornerShape(22.dp)).background(Color.White.copy(alpha = 0.35f)), contentAlignment = Alignment.Center) {
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
private fun CountdownOverlay(count: Int) {
    val scale = remember(count) { Animatable(1.6f) }
    LaunchedEffect(count) { scale.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 400f)) }
    val label = if (count > 0) "$count" else stringResource(R.string.vroum_go)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            label, color = if (count > 0) Color.White else V_GOLD, fontWeight = FontWeight.ExtraBold,
            fontSize = if (count > 0) 120.sp else 64.sp, textAlign = TextAlign.Center,
            modifier = Modifier.scale(scale.value).shadow(0.dp)
        )
    }
}

/**
 * Écran de fin « appli enfants » : illustration, bolide qui rebondit, 1 à 3 étoiles qui apparaissent
 * une par une, score qui défile, pluie de confettis, gros bouton « Encore ! ».
 */
@Composable
private fun ResultScreen(spr: VSprites, cleared: Boolean, score: Int, dist: Int, record: Boolean, endless: Boolean, diff: Int, timeUp: Boolean, vehicleImg: ImageBitmap?, best: Int, bg: ImageBitmap?,
                         onReplay: () -> Unit, onOther: () -> Unit, onVehicle: () -> Unit) {
    val stars = vroumStarCount(endless, cleared, score, dist)
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
            // titre : compact, ombre douce, largeur bornée (jamais coupé)
            Text(stringResource(if (cleared) R.string.vroum_stage_clear else if (timeUp) R.string.gz_time_up else R.string.vroum_out_of_fuel),
                color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp, textAlign = TextAlign.Center, lineHeight = 30.sp,
                style = androidx.compose.ui.text.TextStyle(shadow = androidx.compose.ui.graphics.Shadow(Color(0x66000000), Offset(0f, 4f), 10f)),
                modifier = Modifier.fillMaxWidth(0.9f))
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                for (i in 0 until 3) {
                    val on = i < stars && t > 0.4f + i * 0.35f
                    val pop = if (on) (1f + 0.5f * (1f - ((t - (0.4f + i * 0.35f)) / 0.25f).coerceIn(0f, 1f))) else 1f
                    Text("★", color = if (on) V_GOLD else Color.White.copy(alpha = 0.28f), fontSize = (if (i == 1) 54 else 42).sp, modifier = Modifier.scale(pop).padding(horizontal = 4.dp),
                        style = androidx.compose.ui.text.TextStyle(shadow = androidx.compose.ui.graphics.Shadow(Color(0x55000000), Offset(0f, 3f), 6f)))
                }
            }
            vehicleImg?.let {
                val bob = abs(sin(t * 4f)) * 8f
                Image(painter = BitmapPainter(it), contentDescription = null, modifier = Modifier.size(104.dp).padding(top = bob.dp), contentScale = ContentScale.Fit)
            }
            Spacer(Modifier.height(6.dp))
            // carte de score « verre » : une seule colonne, tout centré, largeur fixe
            Box(Modifier.fillMaxWidth(0.82f).shadow(12.dp, RoundedCornerShape(28.dp), clip = false).clip(RoundedCornerShape(28.dp))
                .background(Brush.verticalGradient(listOf(Color(0xF7FFFFFF), Color(0xE6F4F1FF)))).padding(horizontal = 22.dp, vertical = 16.dp)) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$shownScore", color = V_INK, fontWeight = FontWeight.ExtraBold, fontSize = 44.sp, lineHeight = 46.sp)
                    Text("🏁 " + stringResource(R.string.vroum_distance, dist), color = V_INK.copy(alpha = 0.65f), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    if (record && t > 1.5f) Box(Modifier.padding(top = 8.dp).clip(RoundedCornerShape(14.dp)).background(Brush.horizontalGradient(listOf(V_PINK, V_ORANGE))).padding(horizontal = 14.dp, vertical = 5.dp)) {
                        Text("🏆 " + stringResource(R.string.new_high_score), color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, maxLines = 1)
                    } else if (best > 0) Text("🏆 $best", color = V_INK.copy(alpha = 0.55f), fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }
            Spacer(Modifier.weight(1f))
            // actions : un bouton principal plein, deux boutons « verre » de même taille dessous (texte sur une ligne)
            Box(Modifier.fillMaxWidth(0.82f)) { PillButton("🔁  " + stringResource(R.string.replay), listOf(V_GREEN, V_TEAL), wide = true, onClick = onReplay) }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(0.82f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassButton("🗺  " + stringResource(R.string.vroum_route), Modifier.weight(1f), onClick = onOther)
                GlassButton("🚗  " + stringResource(R.string.vroum_vehicle), Modifier.weight(1f), onClick = onVehicle)
            }
            Spacer(Modifier.height(6.dp))
        }
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
@Composable
private fun SelectScreen(spr: VSprites, vehicle: Int, onBack: () -> Unit, onPick: (Int) -> Unit, onNext: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().height(150.dp)) {
            spr.title?.let { Image(painter = BitmapPainter(it), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xAA7FD4FF)))))
            BackButton(onBack = onBack, modifier = Modifier.align(Alignment.TopStart))
            Column(Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Vroum Vrouuuuuuum", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp)
                Text(stringResource(R.string.vroum_choose), color = Color.White.copy(alpha = 0.95f), fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
        Column(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 14.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (row in 0 until (VEHICLES.size + 1) / 2) {
                Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (col in 0..1) {
                        val i = row * 2 + col
                        val sel = i == vehicle
                        val sc by androidx.compose.animation.core.animateFloatAsState(if (sel) 1.04f else 1f, label = "sel")
                        Box(
                            modifier = Modifier
                                .weight(1f).fillMaxHeight()
                                .scale(sc)
                                .shadow(if (sel) 10.dp else 3.dp, RoundedCornerShape(20.dp), clip = false)
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (sel) Brush.verticalGradient(listOf(Color(0xFFFFF4C2), Color(0xFFFFE08A))) else Brush.verticalGradient(listOf(V_CARD, Color(0xE6F3F0FF))))
                                .border(if (sel) 3.dp else 0.dp, if (sel) V_GOLD else Color.Transparent, RoundedCornerShape(20.dp))
                                .clickableNoRipple { onPick(i) }
                        ) {
                            Row(Modifier.fillMaxSize().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                val img = spr.vehicles[i]
                                if (img != null) Image(painter = BitmapPainter(img), contentDescription = null, modifier = Modifier.size(58.dp), contentScale = ContentScale.Fit)
                                else Box(Modifier.size(58.dp).clip(CircleShape).background(VEHICLE_COLORS[i]))
                                Text(stringResource(VEHICLES[i].nameRes), color = V_INK, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                                    modifier = Modifier.padding(start = 8.dp), lineHeight = 16.sp)
                            }
                        }
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().padding(bottom = 18.dp, top = 6.dp), contentAlignment = Alignment.Center) {
            PillButton(stringResource(R.string.vroum_next), listOf(V_GREEN, V_TEAL), onClick = onNext)
        }
    }
}

/** Écran 2 : choix de la route. */
@Composable
private fun LevelScreen(spr: VSprites, diff: Int, endless: Boolean, onDiff: (Int) -> Unit, onEndless: (Boolean) -> Unit, onBack: () -> Unit, onPlay: (Int) -> Unit) {
    val unlocked by Settings.vroumEndlessUnlocked
    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BackButton(onBack = onBack)
            Text(stringResource(R.string.vroum_pick_level), color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp)
        }
        Spacer(Modifier.height(10.dp))
        // Difficulté : Facile (chrono, invincible) / Moyen / Difficile — comme dans les autres jeux
        Text(stringResource(R.string.vroum_difficulty), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val labels = listOf(R.string.vroum_diff_easy, R.string.vroum_diff_medium, R.string.vroum_diff_hard)
            val descs = listOf(R.string.vroum_diff_easy_desc, R.string.vroum_diff_medium_desc, R.string.vroum_diff_hard_desc)
            val glyphs = listOf("🙂", "😎", "🔥")
            val cols = listOf(V_GREEN, V_CYAN, V_RED)
            for (i in 0..2) {
                val sel = i == diff
                Column(
                    Modifier.weight(1f).shadow(if (sel) 10.dp else 4.dp, RoundedCornerShape(18.dp), clip = false).clip(RoundedCornerShape(18.dp))
                        .background(if (sel) Color(0xFFFFE9A8) else V_CARD)
                        .border(if (sel) 3.dp else 0.dp, if (sel) V_GOLD else Color.Transparent, RoundedCornerShape(18.dp))
                        .clickableNoRipple { onDiff(i) }.padding(horizontal = 8.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(Modifier.size(34.dp).clip(CircleShape).background(cols[i]), contentAlignment = Alignment.Center) { Text(glyphs[i], fontSize = 18.sp) }
                    Text(stringResource(labels[i]), color = V_INK, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, modifier = Modifier.padding(top = 4.dp))
                    Text(stringResource(descs[i]), color = V_INK.copy(alpha = 0.7f), fontSize = 11.sp, textAlign = TextAlign.Center, lineHeight = 13.sp)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        // Mode : Course (2 000 m jusqu'au village) ou Sans fin (débloqué après une première arrivée)
        Text(stringResource(R.string.vroum_mode), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color(0x55FFFFFF)).padding(4.dp)) {
            for ((i, lab) in listOf(R.string.vroum_race, R.string.vroum_endless).withIndex()) {
                val sel = (i == 1) == endless; val ok = i == 0 || unlocked
                Box(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(if (sel) Color.White else Color.Transparent).clickableNoRipple { if (ok) onEndless(i == 1) }.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                    Text((if (i == 1 && !unlocked) "🔒 " else "") + stringResource(lab), color = if (sel) V_INK else Color.White.copy(alpha = if (ok) 1f else 0.6f), fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, maxLines = 1)
                }
            }
        }
        Text(if (!unlocked) stringResource(R.string.vroum_endless_locked) else stringResource(if (endless) R.string.vroum_mode_arcade_hint else R.string.vroum_mode_race_hint),
            color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp, modifier = Modifier.padding(start = 6.dp, top = 4.dp))
        Spacer(Modifier.height(12.dp))
        // Circuits : 5 thèmes en grille de 2 colonnes
        val starsMap by Settings.vroumStars
        val rows = THEMES.indices.chunked(2)
        for (r in rows) {
            Row(Modifier.fillMaxWidth().height(150.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                for (i in r) LevelCard(stringResource(THEMES[i].nameRes), spr.cards.getOrNull(i) ?: spr.title, true, null,
                    Modifier.weight(1f).fillMaxHeight(), stars = starsMap["${THEMES[i].key}_${if (endless) "a" else "c"}$diff"] ?: -1) { onPlay(i) }
                if (r.size == 1) Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
        }
        val best = HighScores.best(if (endless) HighScores.Games.VROUM_ENDLESS else HighScores.Games.VROUM)
        if (best > 0) Text("🏆 $best", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun LevelCard(label: String, img: ImageBitmap?, enabled: Boolean, lockedText: String?, modifier: Modifier, stars: Int = -1, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(12.dp, RoundedCornerShape(26.dp), clip = false)
            .clip(RoundedCornerShape(26.dp))
            .background(Color(0xFF6B5B95))
            .clickableNoRipple { if (enabled) onClick() }
    ) {
        img?.let { Image(painter = BitmapPainter(it), contentDescription = null, modifier = Modifier.fillMaxSize().alpha(if (enabled) 1f else 0.4f), contentScale = ContentScale.Crop) }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xB3000000)))))
        // étoiles gagnées sur ce circuit (mode + difficulté sélectionnés)
        if (stars >= 0) Row(Modifier.align(Alignment.TopEnd).padding(8.dp).clip(RoundedCornerShape(12.dp)).background(Color(0x66000000)).padding(horizontal = 8.dp, vertical = 2.dp)) {
            for (k in 0 until 3) Text("★", color = if (k < stars) V_GOLD else Color.White.copy(alpha = 0.35f), fontSize = 14.sp,
                style = androidx.compose.ui.text.TextStyle(shadow = androidx.compose.ui.graphics.Shadow(Color(0x88000000), Offset(0f, 1f), 3f)))
        }
        Column(Modifier.align(Alignment.BottomStart).padding(14.dp)) {
            Text(label, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp, lineHeight = 21.sp,
                style = androidx.compose.ui.text.TextStyle(shadow = androidx.compose.ui.graphics.Shadow(Color(0x88000000), Offset(0f, 2f), 6f)))
            if (lockedText != null) Text("🔒 $lockedText", color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
        }
    }
}

@Composable
private fun VroumHud(g: VroumGame, frame: Int) {
    @Suppress("UNUSED_EXPRESSION") frame   // force la recomposition à chaque image
    // Une seule rangée alignée sur le bouton retour (56 dp de haut, 10 dp de marge) : jauge d'essence
    // explicite (pompe + %), puis score / distance dans une même carte à droite.
    Column(Modifier.fillMaxWidth().padding(start = 64.dp, end = 12.dp, top = 10.dp)) {
        Row(Modifier.fillMaxWidth().height(56.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            if (g.timeLimit > 0f) {
                val s = g.timeLeft.toInt().coerceAtLeast(0)
                Gauge(g.timeLeft / g.timeLimit, "⏱", "%d:%02d".format(s / 60, s % 60), V_CYAN, blink = s <= 10 && (g.elapsed * 4f).toInt() % 2 == 0, modifier = Modifier.width(150.dp).height(44.dp))
            } else FuelGauge(g.fuel, g.elapsed, modifier = Modifier.width(150.dp).height(44.dp))
            Box(Modifier.clip(RoundedCornerShape(16.dp)).background(Color(0x77000000)).padding(horizontal = 12.dp, vertical = 5.dp)) {
                Column(horizontalAlignment = Alignment.End) {
                    Text("${g.score}", color = V_GOLD, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
                    Text("🏁 " + stringResource(R.string.vroum_distance, g.dist.toInt()), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
        // frise d'arrivée : départ → 🏁, le bolide avance dessus ; les 300 derniers mètres clignotent « Arrivée bientôt ! »
        if (!g.endless) {
            val frac = (g.dist / VroumGame.LEVEL_LEN).coerceIn(0f, 1f)
            val left = (VroumGame.LEVEL_LEN - g.dist).toInt().coerceAtLeast(0)
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f).height(14.dp)) {
                    Box(Modifier.fillMaxWidth().height(6.dp).align(Alignment.CenterStart).clip(RoundedCornerShape(3.dp)).background(Color(0x66000000)))
                    Box(Modifier.fillMaxWidth(frac).height(6.dp).align(Alignment.CenterStart).clip(RoundedCornerShape(3.dp)).background(Brush.horizontalGradient(listOf(V_GREEN, V_GOLD))))
                    // 4 jalons (500 m)
                    for (k in 1..3) Box(Modifier.fillMaxWidth(k / 4f)) { Box(Modifier.align(Alignment.CenterEnd).size(4.dp, 12.dp).background(Color.White.copy(alpha = 0.7f))) }
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
        if (g.combo > 0) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.clip(RoundedCornerShape(14.dp)).background(V_PINK).padding(horizontal = 10.dp, vertical = 2.dp)) {
                    Text("x${g.mult}", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                }
                Box(Modifier.padding(start = 8.dp).weight(1f).height(12.dp).clip(RoundedCornerShape(6.dp)).background(Color(0x55000000))) {
                    Box(Modifier.fillMaxWidth((g.comboTimer / 2.6f).coerceIn(0f, 1f)).height(12.dp).background(Brush.horizontalGradient(listOf(V_PINK, V_GOLD))))
                }
                Text(" ${g.combo}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
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
