package com.goodbrainrot.app.ui

import androidx.compose.ui.graphics.Color
import com.goodbrainrot.app.R
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

// ===========================================================================
// Vroum Vrouuuuuuum — MODÈLE DE JEU (aucune dépendance Android : testable sur JVM,
// voir VroumBalanceTest). Unités : mètres et secondes ; z = devant la caméra.
//
// Idée centrale : l'ÉLAN. Rouler proprement fait monter la vitesse (plancher -> plafond),
// un choc la fait retomber. Tout le reste s'y branche : les pièces et les frôlements
// donnent de l'élan, le chrono se gagne en gardant l'élan, la route est composée de
// MOTIFS dessinés à la main (lignes de pièces, slaloms, murs à trou, tremplins…) posés
// selon une courbe d'intensité, au lieu d'objets tirés au hasard.
// ===========================================================================

internal val V_PINK = Color(0xFFFF6FA8)
internal val V_CYAN = Color(0xFF4FC3F7)
internal val V_BLUE = Color(0xFF1E88E5)
internal val V_GOLD = Color(0xFFFFC94D)
internal val V_GREEN = Color(0xFF66D97A)
internal val V_TEAL = Color(0xFF1FA983)
internal val V_ORANGE = Color(0xFFFF9052)
internal val V_RED = Color(0xFFFF5252)
internal val V_INK = Color(0xFF2A2440)
internal val V_CARD = Color(0xF2FFFFFF)

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
        listOf("vv_acorn", "vv_mushroom", "vv_berry"), 5),   // 5 = pluie (plus de flaques aussi)
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

/**
 * 0..3 étoiles d'une partie (même barème que l'écran de fin). Course : 1 étoile = arrivé, 2 et 3 selon le score
 * (pièces + série + temps restant), seuils par difficulté calibrés par simulation (VroumBalanceTest). Arcade : distance.
 */
internal val VROUM_STAR_SCORE = listOf(intArrayOf(4000, 7000), intArrayOf(4500, 8000), intArrayOf(5000, 9000))
internal val VROUM_STAR_DIST = listOf(intArrayOf(3000, 5500), intArrayOf(3000, 5500), intArrayOf(2200, 4500))
internal fun vroumStarCount(endless: Boolean, cleared: Boolean, score: Int, dist: Int, diff: Int = 1): Int {
    val d = diff.coerceIn(0, 2)
    return when {
        endless -> when { dist >= VROUM_STAR_DIST[d][1] -> 3; dist >= VROUM_STAR_DIST[d][0] -> 2; else -> 1 }
        cleared -> when { score >= VROUM_STAR_SCORE[d][1] -> 3; score >= VROUM_STAR_SCORE[d][0] -> 2; else -> 1 }
        else -> if (dist >= 1000) 1 else 0
    }
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
internal class VEnemy(var lane: Int, var z: Float, val kind: Int, val mps: Float) {
    var crushed = -1f; var bounce = 0f
    var threat = false; var hit = false; var passed = false   // frôlement : menace vue de près / touchée / déjà doublée
}
internal class VPick(var lane: Int, var z: Float, val type: VItem, val variant: Int) {
    var t = 0f
    // obstacle MOBILE (chat de Tokyo, rocher du Volcan) : voie flottante qui traverse la route
    var fLane = -999f; var fDir = 0
}
internal class VShot(var z: Float, val lane: Int)
internal class VPart(var x: Float, var y: Float, var z: Float, var vx: Float, var vy: Float, var life: Float, val maxLife: Float,
                     val r: Float, val g: Float, val b: Float, val size: Float, val floaty: Boolean = false, val streak: Float = 0f)
internal class VText(var x: Float, var y: Float, val z: Float, val text: String, var life: Float, val color: Color, val big: Boolean = false) {
    @Volatile var sx = -1000f; @Volatile var sy = -1000f
}
internal class VDecor(var z: Float, var lateral: Float, val kind: Int, val cheer: Int = -1, val flag: Boolean = false) { var arms = 0f }

/**
 * Réglages par difficulté. [floor]/[ceil] : vitesse plancher / plafond (m/s) ; [ceilGrow] : gain de plafond en Arcade ;
 * [flowTime] : secondes de conduite propre pour passer de l'élan 0 à 1 ; [row] : secondes entre deux rangées d'un motif
 * (= temps de réaction laissé au joueur, constant quelle que soit la vitesse) ; [gap] : pause entre deux motifs ;
 * [bumpKeep] : part d'élan conservée après un choc ; [timeLimit] : chrono de la Course ; [drain] : essence/s en Arcade ;
 * [can] : essence rendue par un bidon ; [fuelEvery] : secondes entre deux bidons au début, + [fuelGrow] s par vague ; [nearT] :
 * fenêtre du frôlement (s avant l'impact).
 */
internal class VTune(val floor: Float, val ceil: Float, val ceilGrow: Float, val flowTime: Float, val row: Float, val gap: Float,
                     val bumpKeep: Float, val timeLimit: Float, val drain: Float, val can: Float, val fuelEvery: Float, val fuelGrow: Float, val nearT: Float)
internal val VTUNES = listOf(
    //     plancher plafond +arcade élan  rangée pause choc  chrono essence bidon tous-les +vague frôlé
    VTune(15f,    23f,    4f,     13f,  0.62f, 1.40f, 0.60f, 150f, 1.00f, 35f, 16f, 4.0f, 0.80f),   // Facile : on finit toujours
    VTune(18f,    29f,    8f,     24f,  0.50f, 0.85f, 0.30f,  74f, 1.35f, 35f, 17f, 2.5f, 0.55f),   // Moyen
    VTune(21f,    35f,    10f,    30f,  0.42f, 0.55f, 0.20f,  60f, 1.70f, 32f, 14f, 2.0f, 0.45f),   // Difficile
)

/**
 * Motif de route sur 3 voies, lu de la première rangée rencontrée à la dernière. Une rangée = 3 caractères (voie gauche,
 * centre, droite) : `.` rien · `o` pièce 10 · `O` pièce 20 · `B` pièce 30 · `C` voiture · `T` turbo · `P` flaque ·
 * `H` trou · `L` tronc (à sauter) · `F` bidon · `M` passager ×2 · `*` étoile géante · `K` canons · `S` super boost.
 */
internal class VPat(val tier: Int, vararg val rows: String) {
    /** une rangée entièrement bouchée ne se passe qu'en sautant */
    val needsJump: Boolean = rows.any { r -> r.all { it in "HLC" } }
}
internal object VPatterns {
    val tiers: List<List<VPat>> = listOf(
        // 0 — respiration : des pièces à suivre, rien à craindre
        listOf(
            VPat(0, ".o.", ".o.", ".o.", ".o.", ".O."),
            VPat(0, "o..", ".o.", "..o", ".o.", "o..", ".O."),
            VPat(0, "o.o", "...", "o.o", "...", ".B."),
            VPat(0, "..o", "..o", ".o.", "o..", "o..", "O.."),
            VPat(0, ".o.", ".T.", "...", ".o.", ".o.", ".O."),
        ),
        // 1 — un seul danger à la fois, les pièces montrent le chemin
        listOf(
            VPat(1, ".o.", ".o.", "o..", "oC.", "o..", ".o.", ".O."),
            VPat(1, "o.o", "...", "C.C", ".o.", ".o.", ".B."),
            VPat(1, "..o", "..o", ".oC", "To.", "...", "o.."),
            VPat(1, ".C.", ".B.", "...", "o.o"),
            VPat(1, "C..", "...", ".o.", ".o.", "..C", ".O."),
            VPat(1, ".o.", ".H.", ".O.", "..."),
        ),
        // 2 — il faut enchaîner deux décisions
        listOf(
            VPat(2, "C..", "...", "..C", "...", ".C.", "...", "o.o"),
            VPat(2, "CC.", "..o", "...", ".CC", "o..", "...", ".O."),
            VPat(2, ".Co", "..o", ".Co", "..o", ".Co", "..O"),
            VPat(2, "C.C", ".T.", "...", ".o.", ".o.", "C.C", ".B."),
            VPat(2, ".P.", "o.o", "C..", "..o", "..B"),
            VPat(2, "H.C", ".o.", ".o.", "C.H", ".O."),
            VPat(2, "C..", "B..", "...", "..C", "..B", "..."),
        ),
        // 3 — sauts obligatoires, murs qui alternent
        listOf(
            VPat(3, ".o.", "LLL", ".O.", "...", "C.C", ".o."),
            VPat(3, "CC.", "...", ".CC", "...", "CC.", "..B"),
            VPat(3, "CLC", "...", ".B.", "..."),
            VPat(3, "C.P", "...", "P.C", "...", ".C.", "o.o"),
            VPat(3, "HH.", "..o", ".HH", "o..", "...", ".T."),
            VPat(3, ".C.", "C..", "..C", ".C.", "...", "B.B"),
        ),
        // 4 — le sommet : tout s'enchaîne sans pause
        listOf(
            VPat(4, "CC.", ".CC", "CC.", "..O", "..."),
            VPat(4, "C.C", "...", ".C.", "...", "C.C", ".T.", "...", "CLC", ".B."),
            VPat(4, ".o.", "HCH", ".O.", "...", "C.C"),
            VPat(4, "C..", ".C.", "..C", "...", "..C", ".C.", "C..", "B.."),
            VPat(4, "P.P", ".C.", "...", "C.C", ".P.", "...", ".B."),
        ),
    )
    // Moments forts (un bonus + la route qui va avec)
    val x2 = VPat(1, ".o.", ".M.", "...", "o.o", ".O.", "o.o", ".O.", "B.B", ".O.", "o.o", ".B.")
    val star = VPat(2, ".o.", ".o.", ".*.", "...", "CC.", "...", ".CC", "...", "C.C", "...", "CC.", "...", ".CC", "...", "C.C", "...", "CC.", "o.o")
    val cannon = VPat(2, ".K.", "...", ".C.", "...", ".C.", "C.C", "...", ".C.", "...", "C.C", ".C.", "...", ".C.", "...")
    val sprint = VPat(1, ".o.", ".S.", ".o.", ".o.", "CoC", ".o.", ".o.", ".O.", "CoC", ".o.", ".o.", ".B.")
    val fuel = listOf(VPat(1, ".F.", "...", "o.o"), VPat(1, "..F", "C..", "..."), VPat(2, "C.C", ".F.", "..."), VPat(3, "C.F", "...", ".CC"))
}

/**
 * [diff] : 0 = Facile (les chocs ne font que ralentir), 1 = Moyen, 2 = Difficile. [duel] : course à deux, ni chrono ni
 * fantôme. [seed] : graine de la route (la même pour les deux joueurs d'un duel).
 */
internal class VroumGame(val endless: Boolean, val vehicle: Int, val diff: Int, val theme: VTheme, val trickLabel: String, val cannonLabel: String,
                         val duel: Boolean = false, seed: Long = Random.nextLong()) {
    companion object {
        const val LANE_M = 3.2f
        const val Z_FAR = 300f
        const val PLAYER_Z = 18f
        const val LEVEL_LEN = 2000f
        const val INTRO_DUR = 3.6f
        const val COMBO_T = 3.0f
    }
    val tune = VTUNES[diff.coerceIn(0, 2)]
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
    var superT = 0f                 // SUPER boost (6 s, musique spéciale, invincible)
    var jumpCd = 0f                 // délai entre deux sauts
    val JUMP_CD = 3.2f
    var passengerT = 0f; var passengerKind = -1
    var fuel = 100f
    /** l'essence compte (Arcade) ; en Course c'est le chrono */
    val fuelOn: Boolean get() = endless
    // Course (défaut) : au CHRONO — arriver au village (2 000 m) avant la fin du temps ; Arcade (endless) : à l'ESSENCE
    val timeLimit = if (!endless && !duel) tune.timeLimit else -1f
    var timeLeft = timeLimit
    var timeUp = false
    var timeBonus = 0               // points du temps restant, ajoutés au score à l'arrivée
    var score = 0; var combo = 0; var comboTimer = 0f; var bestCombo = 0
    /** multiplicateur de points : il monte avec la SÉRIE (pièces, frôlements, voitures écrasées), un choc la casse */
    val mult: Int get() = when { combo >= 40 -> 5; combo >= 24 -> 4; combo >= 12 -> 3; combo >= 5 -> 2; else -> 1 }
    var nearCount = 0; var bumpCount = 0
    var dist = 0f
    var elapsed = 0f
    var nextFlag = 500f
    var sputter = -1f
    var over = false; var cleared = false
    var shake = 0f
    var hornCd = 0f
    @Volatile var frame = 0
    var leanDeg = 0f            // inclinaison latérale (changement de file), degrés
    private var prevLx = 0f
    private var fxCd = 0f

    // ---- ÉLAN : 0..1. Monte tout seul quand on roule proprement, plus vite avec les pièces et les frôlements ; chute sur un choc.
    var flow = 0f
    private var spd = tune.floor            // vitesse réelle lissée (m/s)
    private var flowMaxSaid = false
    private val ceilNow: Float get() = tune.ceil + (if (endless) min(tune.ceilGrow, dist / 700f) else 0f)
    /** vitesse de croisière (hors bonus) que donne l'élan actuel */
    val cruise: Float get() = tune.floor + (ceilNow - tune.floor) * flow
    private val boostMul: Float get() = if (superT > 0f) 1.7f else if (turboT > 0f) 1.35f else if (giant > 0) 1.3f else 1f

    val enemies = ArrayList<VEnemy>()
    val picks = ArrayList<VPick>()
    val shots = ArrayList<VShot>()
    val parts = ArrayList<VPart>()
    val texts = ArrayList<VText>()
    @Volatile var textSnap: List<VText> = emptyList()
    val decor = ArrayList<VDecor>()
    private var cheerCd = 3f
    private var ambientCd = 0f
    var jumpedLabel = "Sauté !"
    var nearLabel = "Frôlé !"
    var maxLabel = "À fond !"
    val events = ConcurrentLinkedQueue<String>()
    private fun ev(s: String) { events.add(s) }

    // ---- CHEF D'ORCHESTRE : pose les motifs devant le joueur (planD = mètres de route déjà composés)
    private val rng = Random(seed)
    private var planD = tune.floor * 1.6f
    private var lastPat: VPat? = null
    private var lastJumpAt = -999f          // distance du dernier saut obligatoire posé
    private var didX2 = false; private var didStar = false; private var didCannon = false; private var didSprint = false
    private var cyclePiece = -1             // Arcade : dernier cycle dont le moment fort est posé
    private var nextFuelAt = 0f             // Arcade : distance du prochain bidon

    init {
        var z = 8f
        while (z < Z_FAR + 40f) {
            for (side in listOf(-1f, 1f)) {
                val k = Random.nextInt(theme.decor.size)
                decor.add(VDecor(z + Random.nextFloat() * 6f, side * (roadHalfFor(3) + 1.5f + Random.nextFloat() * 7f), k))
            }
            z += 11f
        }
        nextFuelAt = tune.floor * tune.fuelEvery
        plan()      // la route n'est pas vide au décompte
    }

    /** Passage à [n] voies : la route s'élargit 40 m devant, les entités existantes gardent leur position (ré-indexation). */
    private fun widenTo(n: Int) {
        val shift = (n - lanes) / 2
        oldLanes = lanes; lanes = n
        widenStart = dist + 40f
        lane += shift
        for (e in enemies) e.lane += shift
        for (p in picks) { p.lane += shift; if (p.fDir != 0) p.fLane += shift }
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
        if (sputter >= 0f) return max(0f, spd * (1f - sputter / 2.6f))
        val intro = if (inIntro) 0.25f + 0.75f * (1f - (introT / INTRO_DUR).coerceIn(0f, 1f)) else 1f
        return spd * intro
    }
    /** Intensité du boost pour les effets (0 rien, 1 turbo, 2 super). */
    val boostLevel: Int get() = if (superT > 0f) 2 else if (turboT > 0f) 1 else 0
    val speedFactor: Float get() = mps / 18f
    val kmh: Int get() = (mps * 3.6f / 5f).toInt() * 5

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

    private fun gainFlow(v: Float) { flow = min(1f, flow + v) }
    /** Choc : on garde [keep] de l'élan et la vitesse retombe d'un coup (c'est ÇA la punition, visible et ressentie). */
    private fun loseFlow(keep: Float) { flow *= keep; spd *= 0.5f + 0.5f * keep }
    private fun chain(n: Int = 1) { combo += n; comboTimer = COMBO_T; if (combo > bestCombo) bestCombo = combo }
    private fun breakChain() { combo = if (diff == 0) combo / 2 else 0; comboTimer = if (combo > 0) COMBO_T else 0f }

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
        }
        val racing = !over && sputter < 0f && !cleared && !inIntro
        // élan : monte tout seul ; la vitesse rejoint la croisière (accélère vite, ralentit doucement après un boost)
        if (racing) {
            gainFlow(dt / tune.flowTime)
            if (flow >= 1f && !flowMaxSaid) { flowMaxSaid = true; texts.add(VText(lx * LANE_M, 4.2f, PLAYER_Z, maxLabel, 1.3f, V_GOLD, big = true)); ev("flowMax") }
            if (flow < 0.7f) flowMaxSaid = false
        }
        val target = cruise * boostMul
        spd += (target - spd) * min(1f, dt * (if (target > spd) 3f else 1.5f))
        val sp = mps
        val dz = sp * dt
        if (!over && sputter < 0f) dist += dz
        // trace de la course (fantôme) : un point (dist, voie) toutes les 0,25 s après le départ (Course ET Arcade)
        if (!duel && !inIntro && !over && !cleared) {
            recAcc += dt
            while (recAcc >= 0.25f) { recAcc -= 0.25f; recD.add((dist * 10f).toInt()); recL.add(lane) }
        }

        if (dist > nextCurveAt) {
            nextCurveAt = dist + 220f + Random.nextFloat() * 260f
            curveTarget = if (Random.nextFloat() < 0.35f) 0f else (Random.nextFloat() * 1.6f - 0.8f)
        }
        curve += (curveTarget - curve) * min(1f, dt * 0.5f)

        var tl = targetLateral
        if (slideT > 0f) { slideT -= dt; tl += slideDir * 0.22f * sin(slideT * 18f) }
        lx += (tl - lx) * min(1f, dt * 9f)
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
                // neige avec RAFALES de vent : dérive latérale qui oscille lentement
                4 -> parts.add(VPart(ax, gy + 6f + Random.nextFloat() * 5f, az, sin(elapsed * 0.7f) * 2.4f + (Random.nextFloat() - 0.5f) * 0.8f, -1.2f - Random.nextFloat() * 1.2f, 5f, 5f, 1f, 1f, 1f, 0.16f + Random.nextFloat() * 0.12f, floaty = true))
                // pluie (forêt de nuit) : gouttes fines et rapides
                5 -> {
                    // gouttes = traînées étirées, surtout près de la caméra pour qu'on les voie bien
                    repeat(4) {
                        val rz = 12f + Random.nextFloat() * 40f
                        parts.add(VPart((Random.nextFloat() - 0.5f) * 16f, groundY(dist + rz) + 2f + Random.nextFloat() * 6f, rz, 1.2f, -16f, 0.6f, 0.6f, 0.75f, 0.85f, 1f, 0.04f, floaty = true, streak = 16f))
                    }
                    // quelques lucioles restent sous la pluie
                    if (Random.nextFloat() < 0.25f) parts.add(VPart(ax, gy + 0.4f + Random.nextFloat() * 2f, az, (Random.nextFloat() - 0.5f) * 0.6f, (Random.nextFloat() - 0.3f) * 0.5f, 2.5f + Random.nextFloat() * 2f, 4f, 1f, 0.95f, 0.45f, 0.14f, floaty = true))
                }
            }
        }
        if (passengerT > 0f) { passengerT -= dt; if (passengerT <= 0f) passengerKind = -1 }
        if (giantT > 0f) { giantT -= dt; if (giantT <= 0f) endGiant() }
        // la série s'essouffle si on ne ramasse plus rien (elle est divisée par deux, pas remise à zéro)
        if (comboTimer > 0f) { comboTimer -= dt; if (comboTimer <= 0f) { combo /= 2; comboTimer = if (combo > 0) COMBO_T else 0f } }
        if (cannonT > 0f) {
            cannonT -= dt; cannonCd -= dt
            if (cannonCd <= 0f && !over) {
                cannonCd = 0.16f
                for (l in coveredLanes()) shots.add(VShot(PLAYER_Z + 1.5f, l))
                ev("shot")
            }
        }

        if (racing) {
            if (timeLimit > 0f) {
                timeLeft -= dt
                if (timeLeft <= 0f) { timeLeft = 0f; timeUp = true; over = true; ev("timeup") }
            } else if (fuelOn) {
                fuel -= dt * tune.drain * (1f + 0.1f * (dist / 900f).toInt())      // le moteur consomme plus à chaque vague
                if (fuel <= 0f) { fuel = 0f; sputter = 0f; ev("sputter"); if (giant > 0) endGiant() }
            }
        }
        if (sputter >= 0f) { sputter += dt; if (sputter > 2.8f && !over) { over = true; ev("over") } }
        if (!endless && !cleared && dist >= LEVEL_LEN) {
            cleared = true
            if (timeLimit > 0f) { timeBonus = (timeLeft * 40f).toInt() * (diff + 1); score += timeBonus }
            ev("clear")
        }
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

        if (racing || inIntro) {
            planD -= dz
            plan()
        }
        if (racing) {
            cheerCd -= dt
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
                e.crushed = 0f; chain()
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
            if (e.z < 0.5f || e.z > Z_FAR + 400f) { eit.remove(); continue }
            if (e.crushed < 0f && e.z > PLAYER_Z - 1.6f && e.z < PLAYER_Z + 2.0f && e.lane in coveredLanes()) {
                if (giant > 0) crush(e)
                else if (airborne) { if (!trickDone) { trickDone = true; chain(); gainFlow(0.04f); addScore(25, lx * LANE_M, PLAYER_Z, V_CYAN); texts.add(VText(lx * LANE_M, 4f, PLAYER_Z, trickLabel, 1.0f, V_CYAN, big = true)); ev("trick") } }
                else if (invuln <= 0f) bump(e)
            }
            // FRÔLEMENT : j'étais dans SA voie à moins de nearT secondes de l'impact, et je l'ai doublée sans la toucher
            if (e.crushed < 0f && !e.hit && !e.passed) {
                val gapZ = e.z - PLAYER_Z; val closing = sp - e.mps
                if (gapZ > 2f && closing > 1f && giant == 0 && !jumping && invuln <= 0f && e.lane in coveredLanes() && (gapZ - 2f) / closing < tune.nearT) e.threat = true
                if (gapZ < -1.6f) { e.passed = true; if (e.threat && racing && !jumping) nearMiss(e) }
            }
        }
        val pit = picks.iterator()
        while (pit.hasNext()) {
            val p = pit.next()
            p.z -= dz; p.t += dt
            // obstacle mobile (chat / rocher) : il attend sur le bas-côté puis traverse quand il est à ~2,4 s du joueur
            if (p.fDir != 0) {
                if ((p.z - PLAYER_Z) < max(1f, sp) * 2.4f) p.fLane += p.fDir * dt * lanes / 3.2f
                p.lane = Math.round(p.fLane)
                if (p.fLane < -0.95f || p.fLane > lanes - 0.05f) { pit.remove(); continue }
            }
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

    // -----------------------------------------------------------------------
    // Composition de la route
    // -----------------------------------------------------------------------

    /** Remplit la route jusqu'à l'horizon : assez loin pour que rien n'apparaisse d'un coup (voitures posées à 180 m+). */
    private fun plan() {
        val v = max(tune.floor, cruise)
        val horizon = min(15f * v, Z_FAR - 32f)
        var guard = 0
        while (planD < horizon && guard++ < 6) {
            val at = dist + planD
            if (!endless && at > LEVEL_LEN + 10f) { planD = horizon; break }
            val pat = nextPattern(at)
            place(pat, planD, v)
            lastPat = pat
            if (pat.needsJump) lastJumpAt = at
            planD += pat.rows.size * tune.row * v + tune.gap * (0.75f + rng.nextFloat() * 0.5f) * v
        }
    }

    /** Intensité 0..4 selon la position : montée, moment fort, respiration… (Course : scénario ; Arcade : vagues de 900 m). */
    private fun nextPattern(at: Float): VPat {
        val base = diff - 1                                   // Facile -1, Moyen 0, Difficile +1
        var tier: Int
        if (!endless) {
            val p = at / LEVEL_LEN
            if (!didX2 && p >= 0.20f) { didX2 = true; return VPatterns.x2 }
            if (!didStar && p >= 0.40f) { didStar = true; return VPatterns.star }
            if (!didCannon && p >= 0.64f) { didCannon = true; return VPatterns.cannon }
            if (!didSprint && p >= 0.85f) { didSprint = true; return VPatterns.sprint }
            if (didSprint) return pick(0)                     // ligne droite finale : que des pièces
            tier = base + when { p < 0.08f -> 0; p < 0.20f -> 1; p < 0.40f -> 2; p < 0.52f -> 1; p < 0.64f -> 2; else -> 3 }
        } else {
            val cycle = (at / 900f).toInt(); val q = (at % 900f) / 900f
            if (fuelOn && at >= nextFuelAt) {
                // les bidons s'espacent à chaque vague : c'est ce qui finit par arrêter la partie
                nextFuelAt = at + max(tune.floor, cruise) * (tune.fuelEvery + tune.fuelGrow * cycle)
                val t = (base + 1 + cycle / 2).coerceIn(1, 3)
                return VPatterns.fuel.filter { it.tier <= t }.let { it[rng.nextInt(it.size)] }
            }
            if (q >= 0.82f && cyclePiece < cycle) {
                cyclePiece = cycle
                return when (cycle % 4) { 0 -> VPatterns.x2; 1 -> VPatterns.star; 2 -> VPatterns.cannon; else -> VPatterns.sprint }
            }
            tier = base + 1 + cycle / 2 + (if (q in 0.45f..0.82f) 1 else 0) - (if (q < 0.10f) 1 else 0)
        }
        return pick(tier.coerceIn(0, 4))
    }

    private fun pick(tier: Int): VPat {
        // 70 % du palier voulu, 30 % du palier juste en dessous (pour souffler) ; jamais deux fois le même motif
        repeat(8) {
            val t = if (tier > 0 && rng.nextFloat() < 0.3f) tier - 1 else tier
            val list = VPatterns.tiers[t]
            val p = list[rng.nextInt(list.size)]
            val at = dist + planD
            val jumpOk = !p.needsJump || (diff > 0 && at - lastJumpAt > max(tune.floor, cruise) * 5.5f)
            if (p !== lastPat && jumpOk) return p
        }
        return VPatterns.tiers[0][rng.nextInt(VPatterns.tiers[0].size)]
    }

    /**
     * Pose le motif [pat] à [d0] mètres devant le joueur. Les rangées sont espacées en SECONDES (tune.row) converties à la
     * vitesse de croisière [v] : le temps de réaction reste le même quand on va plus vite. Les voitures roulent (plus
     * lentement que nous) : on les pose plus près, là où elles seront rejointes au même instant que leur rangée.
     */
    private fun place(pat: VPat, d0: Float, v: Float) {
        val mirror = rng.nextBoolean()
        val off = if (lanes > 3) rng.nextInt(lanes - 2) else 0
        val stepM = tune.row * v
        val ve = 6.5f + rng.nextFloat() * 1.5f                 // même vitesse pour tout le motif : les murs restent des murs
        val crossTheme = theme.key == "tokyo" || theme.key == "volcano"
        for ((r, row) in pat.rows.withIndex()) {
            val d = d0 + r * stepM
            var crosser = false
            for (c in 0..2) {
                val ch = row[if (mirror) 2 - c else c]
                if (ch == '.') continue
                val ln = off + c
                if (ch == 'C') { enemies.add(VEnemy(ln, PLAYER_Z + d * (v - ve) / v, rng.nextInt(ENEMY_KEYS.size), ve)); continue }
                val type = when (ch) {
                    'o' -> VItem.ACORN; 'O' -> VItem.MUSHROOM; 'B' -> VItem.BERRY
                    'T' -> VItem.TURBO; 'P' -> VItem.PUDDLE; 'H' -> VItem.HOLE; 'L' -> VItem.LOG
                    'F' -> if (fuelOn) VItem.FUEL else VItem.MUSHROOM
                    'M' -> VItem.PASSENGER; '*' -> VItem.STAR; 'K' -> VItem.CANNON; 'S' -> VItem.SUPERBOOST
                    else -> VItem.ACORN
                }
                val p = VPick(ln, PLAYER_Z + d, type, rng.nextInt(3))
                // à Tokyo (chat) et au Volcan (rocher), le « tronc » TRAVERSE la route : un seul par rangée
                if (type == VItem.LOG && crossTheme) {
                    if (crosser) continue
                    crosser = true
                    p.fDir = if (rng.nextBoolean()) 1 else -1
                    p.fLane = if (p.fDir > 0) -0.9f else lanes - 0.1f
                    p.lane = Math.round(p.fLane)
                }
                picks.add(p)
            }
        }
        // route élargie (paysage) : un peu de vie dans les voies hors du motif
        if (lanes > 3) for (ln in 0 until lanes) {
            if (ln in off..off + 2) continue
            val r = rng.nextFloat(); val row = rng.nextInt(pat.rows.size); val d = d0 + row * stepM
            if (r < 0.35f) enemies.add(VEnemy(ln, PLAYER_Z + d * (v - ve) / v, rng.nextInt(ENEMY_KEYS.size), ve))
            else if (r < 0.75f) for (k in 0..2) picks.add(VPick(ln, PLAYER_Z + d + k * stepM, if (k == 2) VItem.MUSHROOM else VItem.ACORN, rng.nextInt(3)))
        }
    }

    // -----------------------------------------------------------------------
    // Événements de jeu
    // -----------------------------------------------------------------------

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

    private fun addScore(n: Int, x: Float, z: Float, col: Color) {
        val v = n * mult * (if (passengerKind >= 0) 2 else 1)
        score += v
        texts.add(VText(x, 2.2f, z, "+$v", 0.9f, col))
    }

    private fun nearMiss(e: VEnemy) {
        nearCount++
        chain(2); gainFlow(0.10f)
        addScore(30, laneX(e.lane), PLAYER_Z + 2f, V_PINK)
        texts.add(VText(lx * LANE_M, 3.6f, PLAYER_Z, nearLabel, 1.0f, V_PINK, big = true))
        ev("near")
    }

    private fun collect(p: VPick) {
        val x = laneX(p.lane); val z = p.z
        when (p.type) {
            VItem.FUEL -> { fuel = min(100f, fuel + tune.can); ev("fuel"); burst(x, z, 1f, 0.25f, 0.25f, 10); texts.add(VText(x, 2.2f, z, "+${tune.can.toInt()}", 0.9f, V_GREEN)) }
            VItem.STAR -> { startGiant(); addScore(50, x, z, V_GOLD); burst(x, z, 1f, 0.82f, 0.3f, 24) }
            VItem.CANNON -> { cannonT = 8f; cannonCd = 0f; ev("cannon"); texts.add(VText(x, 2.6f, z, cannonLabel, 1.2f, V_CYAN, big = true)); burst(x, z, 0.3f, 0.85f, 1f, 16) }
            // chaque pièce prolonge la série et donne un peu d'élan : suivre une ligne de pièces, c'est accélérer
            VItem.ACORN -> { chain(); gainFlow(0.008f); addScore(10, x, z, V_GOLD); ev("pick1"); burst(x, z, 0.75f, 0.5f, 0.25f, 6) }
            VItem.MUSHROOM -> { chain(); gainFlow(0.012f); addScore(20, x, z, V_GOLD); ev("pick2"); burst(x, z, 1f, 0.25f, 0.25f, 6) }
            VItem.BERRY -> { chain(); gainFlow(0.02f); addScore(30, x, z, V_GOLD); ev("pick3"); burst(x, z, 0.42f, 0.49f, 1f, 6) }
            VItem.TURBO -> { turboT = max(turboT, 3f); gainFlow(0.12f); ev("turbo"); texts.add(VText(x, 2.2f, z, "TURBO!", 1.0f, V_CYAN)); shake = max(shake, 6f) }
            VItem.SUPERBOOST -> { superT = 6f; turboT = max(turboT, 6f); flow = 1f; ev("superboost"); texts.add(VText(x, 3.0f, z, superLabel, 1.6f, V_GOLD, big = true)); burst(x, z, 1f, 0.85f, 0.2f, 30); shake = max(shake, 12f); invuln = max(invuln, 6f) }
            VItem.HOLE, VItem.LOG -> {
                if (airborne || jumping) { chain(); gainFlow(0.05f); addScore(20, x, z, V_CYAN); texts.add(VText(x, 3.2f, z, jumpedLabel, 1.0f, V_CYAN, big = true)); ev("trick") }
                else if (giant > 0) { burst(x, z, 0.6f, 0.4f, 0.25f, 12); ev("crush") }
                else if (invuln <= 0f) {
                    // tombé dans le trou / heurté le tronc : gros cahot, l'élan retombe (et de l'essence en Arcade)
                    bumpCount++
                    if (fuelOn && diff != 0) fuel = max(0f, fuel - (if (diff == 2) 25f else 15f))
                    breakChain(); loseFlow(tune.bumpKeep)
                    invuln = 1.2f; shake = max(shake, 14f); slideT = 0.5f; slideDir = if (Random.nextBoolean()) 1 else -1
                    burst(x, z, 0.5f, 0.4f, 0.3f, 14); ev("bump")
                }
            }
            VItem.PUDDLE -> if (giant == 0) {
                slideT = 0.7f; slideDir = if (lane <= laneLo) 1 else if (lane >= laneHi) -1 else (if (Random.nextBoolean()) 1 else -1)
                lane = (lane + slideDir).coerceIn(laneLo, laneHi)
                flow = max(0f, flow - 0.10f)
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
        e.crushed = 0f; chain()
        addScore(50, laneX(e.lane), e.z, V_PINK); burst(laneX(e.lane), e.z, 1f, 0.82f, 0.3f, 14)
        shake = max(shake, 7f); ev("crush")
        if (combo % 5 == 0) { ev("cheer"); for (d in decor) if (d.cheer >= 0) d.arms = 1.8f }
    }
    private fun bump(e: VEnemy) {
        // La punition, c'est l'élan perdu (donc du temps en Course). En Arcade s'ajoute de l'essence, sauf en Facile.
        bumpCount++
        e.hit = true
        if (fuelOn && diff != 0) fuel = max(0f, fuel - (if (diff == 2) 25f else 15f))
        breakChain(); loseFlow(tune.bumpKeep)
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
