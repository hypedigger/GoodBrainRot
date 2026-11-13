package com.goodbrainrot.app.ui

import androidx.compose.ui.graphics.Color
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
// Trois idées portent le jeu :
//  1. L'ÉLAN : rouler proprement fait monter la vitesse (plancher -> plafond), un accident la fait retomber.
//  2. Le BOOST façon Burnout : une jauge qu'on remplit en prenant des risques (frôler, percuter par derrière,
//     rebondir sur les voitures, ramasser) et qu'on brûle en tenant le bouton ; chaque TAKEDOWN allonge la jauge.
//  3. Le RYTHME façon Mario Kart : la route est composée de MOTIFS dessinés à la main, avec des boîtes à objets.
// Règle de lecture pour un enfant : on peut rentrer dans ce qui roule DANS NOTRE SENS (petites voitures toujours,
// camions et bus seulement en boost) ; ce qui arrive EN FACE (phares) s'esquive ou se saute.
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

internal enum class VItem { FUEL, STAR, ACORN, MUSHROOM, BERRY, TURBO, PUDDLE, PASSENGER, CANNON, SUPERBOOST, HOLE, LOG, ITEMBOX, RAMP }

/** Objets tirés d'une boîte « ? ». [dur] : durée d'effet (s). */
internal enum class VWeapon(val tex: String, val dur: Float) {
    NONE("", 0f), BOMB("vv_bomb", 0.9f), LASER("vv_laser", 5f), FLAME("vv_flame", 6f), MAGNET("vv_magnet", 9f),
    SHIELD("vv_shield", 25f), CANNON("vv_cannon", 8f), STAR("vv_star", 0f), SUPER("vv_superboost", 0f)
}

/**
 * 0..3 étoiles d'une partie (même barème que l'écran de fin). Course : 1 étoile = arrivé, 2 et 3 selon le score
 * (pièces + série + takedowns + temps restant), seuils par difficulté calibrés par simulation (VroumBalanceTest).
 * Arcade : distance.
 */
internal val VROUM_STAR_SCORE = listOf(intArrayOf(6000, 12000), intArrayOf(18000, 28000), intArrayOf(22000, 34000))
internal val VROUM_STAR_DIST = listOf(intArrayOf(4000, 7000), intArrayOf(4500, 7500), intArrayOf(3500, 6500))
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
    VVehicle("car", com.goodbrainrot.app.R.string.vroum_v_car), VVehicle("monster", com.goodbrainrot.app.R.string.vroum_v_monster),
    VVehicle("firetruck", com.goodbrainrot.app.R.string.vroum_v_firetruck), VVehicle("moto", com.goodbrainrot.app.R.string.vroum_v_moto),
    VVehicle("tank", com.goodbrainrot.app.R.string.vroum_v_tank), VVehicle("dragon", com.goodbrainrot.app.R.string.vroum_v_dragon),
    VVehicle("lion", com.goodbrainrot.app.R.string.vroum_v_lion), VVehicle("trex", com.goodbrainrot.app.R.string.vroum_v_trex),
    VVehicle("rocket", com.goodbrainrot.app.R.string.vroum_v_rocket), VVehicle("tractor", com.goodbrainrot.app.R.string.vroum_v_tractor),
    VVehicle("excavator", com.goodbrainrot.app.R.string.vroum_v_excavator), VVehicle("bear", com.goodbrainrot.app.R.string.vroum_v_bear)
)
/** Familles d'animation / de son : créatures qui courent, dragon qui vole, fusée qui flotte. */
internal val WALKER_KEYS = setOf("lion", "trex", "bear")
internal val CREATURE_KEYS = setOf("dragon", "lion", "trex", "bear")
/** Trafic : 0-1 voitures, 2 camion, 3 bus, 4 van. Les LOURDS (camion, bus) ne se poussent qu'en boost. */
internal val ENEMY_KEYS = listOf("en_car1", "en_car2", "en_truck", "en_bus", "en_van")
internal val ONCOMING_KEYS = listOf("on_car1", "on_car2", "on_truck", "on_bus", "on_van")
internal val PASSENGER_KEYS = listOf("pass_rabbit", "pass_owl", "pass_fox")
internal val CHEER_KEYS = listOf("cheer_rabbit", "cheer_squirrel", "cheer_owl")

internal class VEnemy(var lane: Int, var z: Float, val kind: Int, var mps: Float) {
    var crushed = -1f; var bounce = 0f
    var threat = false; var hit = false; var passed = false   // frôlement : menace vue de près / touchée / déjà doublée
    var oncoming = false          // arrive EN FACE (phares) : on ne peut pas la percuter
    var heavy = false             // camion / bus : ne se pousse qu'en boost
    var boulder = false           // rocher qui dévale vers nous (jungle)
    var ghost = false             // voiture fantôme (manoir hanté) : on la traverse, ça rapporte
    // TAKEDOWN : la voiture est éjectée (fly = secondes depuis le choc ; -1 = elle roule)
    var fly = -1f; var flyX = 0f; var flyVx = 0f; var flyY = 0f; var spin = 0f
    // RIVAL : un autre bolide qui se bat avec nous
    var rival = false; var veh = 0
    var fx = lane.toFloat()       // voie continue (glissement d'une voie à l'autre)
    var target = lane             // voie visée
    var want = 20f                // distance devant nous qu'il cherche à tenir
    var aiCd = 1f; var warn = 0f; var stun = 0f
    var revenge = false           // il nous a dégagés : le sortir à son tour = REVANCHE
    val alive: Boolean get() = crushed < 0f && fly < 0f
}
internal class VPick(var lane: Int, var z: Float, val type: VItem, val variant: Int) {
    var t = 0f
    var used = false   // ressort déjà déclenché (il reste en place et oscille)
    // obstacle MOBILE (chat, rocher, crabe, vache…) : voie flottante qui traverse la route
    var fLane = -999f; var fDir = 0
    var magnet = false; var mx = 0f     // aimant : la pièce glisse vers le bolide (mx = décalage latéral en voies)
}
internal class VShot(var z: Float, val lane: Int)
internal class VPart(var x: Float, var y: Float, var z: Float, var vx: Float, var vy: Float, var life: Float, val maxLife: Float,
                     val r: Float, val g: Float, val b: Float, val size: Float, val floaty: Boolean = false, val streak: Float = 0f, var vz: Float = 0f)
internal class VText(var x: Float, var y: Float, var z: Float, @Volatile var text: String, var life: Float, @Volatile var color: Color, val big: Boolean = false) {
    @Volatile var sx = -1000f; @Volatile var sy = -1000f
    @Volatile var pop = 1f      // 1 → 0 : petit rebond d'échelle à l'apparition (et à chaque fusion de gains)
    var gain = 0                // > 0 : compteur de points (les gains rapprochés s'y additionnent)
    var style = 0               // 1 = message d'IMPACT (takedown, strike, revanche) : plus gros, éclat derrière
}
internal class VDecor(var z: Float, var lateral: Float, val kind: Int, val cheer: Int = -1, val flag: Boolean = false, val special: Int = 0 /* 1 = tribune de l'arrivée */) { var arms = 0f }

/**
 * Réglages par difficulté. [floor]/[ceil] : vitesse plancher / plafond (m/s) ; [ceilGrow] : gain de plafond en Arcade ;
 * [flowTime] : secondes de conduite propre pour passer de l'élan 0 à 1 ; [row] : secondes entre deux rangées d'un motif
 * (= temps de réaction laissé au joueur, constant quelle que soit la vitesse) ; [gap] : pause entre deux motifs ;
 * [bumpKeep] : part d'élan conservée après un accident ; [timeLimit] : chrono de la Course ; [drain] : essence/s en Arcade ;
 * [can] : essence rendue par un bidon ; [fuelEvery] : secondes entre deux bidons au début, + [fuelGrow] s par vague ;
 * [nearT] : fenêtre du frôlement (s avant l'impact) ; [rivals] : rivaux en piste ; [aggro] : agressivité des rivaux (0..1).
 */
internal class VTune(val floor: Float, val ceil: Float, val ceilGrow: Float, val flowTime: Float, val row: Float, val gap: Float,
                     val bumpKeep: Float, val timeLimit: Float, val drain: Float, val can: Float, val fuelEvery: Float, val fuelGrow: Float,
                     val nearT: Float, val rivals: Int, val aggro: Float)
internal val VTUNES = listOf(
    //     plancher plafond +arcade élan  rangée pause choc  chrono essence bidon tous-les +vague frôlé rivaux agress.
    VTune(15f,    23f,    4f,     13f,  0.62f, 1.30f, 0.60f, 150f, 1.00f, 35f, 16f, 4.0f, 0.80f, 1, 0.0f),   // Facile : on finit toujours
    VTune(18f,    29f,    8f,     24f,  0.50f, 0.80f, 0.30f,  66f, 1.35f, 35f, 17f, 2.5f, 0.55f, 2, 0.6f),   // Moyen
    VTune(21f,    35f,    10f,    30f,  0.42f, 0.50f, 0.20f,  52f, 1.70f, 32f, 14f, 2.0f, 0.45f, 3, 1.0f),   // Difficile
)

/**
 * Motif de route sur 3 voies, lu de la première rangée rencontrée à la dernière. Une rangée = 3 caractères (voie gauche,
 * centre, droite) : `.` rien · `o` pièce 10 · `O` pièce 20 · `B` pièce 30 · `C` petite voiture dans notre sens (à percuter
 * ou à écraser en sautant) · `W` camion/bus dans notre sens (mur, sauf en boost) · `X` véhicule EN FACE (à esquiver) ·
 * `T` turbo · `P` flaque · `H` trou · `L` tronc/barrière (à sauter) · `R` tremplin · `?` boîte à objet · `F` bidon ·
 * `M` passager ×2 · `*` étoile géante · `K` canons · `S` super boost.
 */
internal class VPat(val tier: Int, vararg val rows: String) {
    /** une rangée entièrement bouchée ne se passe qu'en sautant */
    val needsJump: Boolean = rows.any { r -> r.all { it in "HLWX" } }
}
internal object VPatterns {
    val tiers: List<List<VPat>> = listOf(
        // 0 — respiration : des pièces à suivre, des voitures à percuter, rien à craindre
        listOf(
            VPat(0, ".o.", ".o.", ".o.", ".o.", ".O."),
            VPat(0, "o..", ".o.", "..o", ".o.", "o..", ".O."),
            VPat(0, "o.o", "...", "o.o", "...", ".B."),
            VPat(0, "..o", "..o", ".o.", "o..", "o..", "O.."),
            VPat(0, ".o.", ".T.", "...", ".o.", ".o.", ".O."),
            VPat(0, ".C.", "...", ".C.", "...", ".C.", "...", ".B."),          // file de voitures : on fonce dedans ou on rebondit dessus
            VPat(0, "o.o", ".C.", "o.o", "...", ".O."),
        ),
        // 1 — un seul danger à la fois, les pièces montrent le chemin
        listOf(
            VPat(1, ".o.", ".o.", "o..", "oX.", "o..", ".o.", ".O."),
            VPat(1, "o.o", "...", "W.W", ".o.", ".o.", ".B."),
            VPat(1, "..o", "..o", ".oW", "To.", "...", "o.."),
            VPat(1, ".C.", ".B.", "...", "o.o"),
            VPat(1, "X..", "...", ".o.", ".o.", "..X", ".O."),
            VPat(1, ".o.", ".H.", ".O.", "..."),
            VPat(1, "C..", "...", ".C.", "...", "..C", "...", "..B"),          // escalier de voitures : trois rebonds en diagonale
            VPat(1, ".R.", ".o.", ".O.", ".o.", "..."),                        // tremplin, pièces en l'air
            VPat(1, "C.C", "...", ".C.", "...", "C.C", ".O."),
        ),
        // 2 — il faut enchaîner deux décisions
        listOf(
            VPat(2, "X..", "...", "..X", "...", ".X.", "...", "o.o"),
            VPat(2, "WW.", "..o", "...", ".WW", "o..", "...", ".O."),
            VPat(2, ".Co", "..o", ".Co", "..o", ".Co", "..O"),
            VPat(2, "W.W", ".T.", "...", ".o.", ".o.", "X.X", ".B."),
            VPat(2, ".P.", "o.o", "X..", "..o", "..B"),
            VPat(2, "H.X", ".o.", ".o.", "X.H", ".O."),
            VPat(2, "C..", "B..", "...", "..X", "..B", "..."),
            VPat(2, ".C.", "...", "C.C", "...", ".C.", "...", "C.C", ".B."),  // festin de takedowns
            VPat(2, "R.X", "o..", "O..", "...", "X.R", "..o", "..O"),
        ),
        // 3 — sauts obligatoires, murs qui alternent
        listOf(
            VPat(3, ".o.", "LLL", ".O.", "...", "X.X", ".o."),
            VPat(3, "WW.", "...", ".WW", "...", "WW.", "..B"),
            VPat(3, "WLW", "...", ".B.", "..."),
            VPat(3, "X.P", "...", "P.X", "...", ".X.", "o.o"),
            VPat(3, "HH.", "..o", ".HH", "o..", "...", ".T."),
            VPat(3, ".X.", "X..", "..X", ".X.", "...", "B.B"),
            VPat(3, "C.C", "...", ".W.", "...", "C.C", "...", ".X.", "B.B"),
            VPat(3, "XCX", "...", "XCX", "...", "XCX", ".B."),                 // couloir : rebondir au centre entre deux files en face
        ),
        // 4 — le sommet : tout s'enchaîne sans pause
        listOf(
            VPat(4, "XW.", ".WX", "WX.", "..O", "..."),
            VPat(4, "X.X", "...", ".W.", "...", "X.X", ".T.", "...", "WLW", ".B."),
            VPat(4, ".o.", "HXH", ".O.", "...", "X.X"),
            VPat(4, "X..", ".X.", "..X", "...", "..X", ".X.", "X..", "B.."),
            VPat(4, "P.P", ".X.", "...", "W.W", ".P.", "...", ".B."),
            VPat(4, "XCX", "...", "CXC", "...", "XCX", "...", "CXC", ".B."),
        ),
    )
    // Moments forts (un bonus + la route qui va avec)
    val x2 = VPat(1, ".o.", ".M.", "...", "o.o", ".O.", "oCo", ".O.", "B.B", ".O.", "oCo", ".B.")
    val star = VPat(2, ".o.", ".o.", ".*.", "...", "CW.", "...", ".WC", "...", "C.W", "...", "XC.", "...", ".WX", "...", "C.C", "...", "WX.", "o.o")
    val cannon = VPat(2, ".K.", "...", ".C.", "...", ".W.", "C.C", "...", ".X.", "...", "W.W", ".C.", "...", ".X.", "...")
    val sprint = VPat(1, ".o.", ".S.", ".o.", ".o.", "CoC", ".o.", ".o.", ".O.", "WoW", ".o.", ".o.", ".B.")
    val fuel = listOf(VPat(1, ".F.", "...", "o.o"), VPat(1, "..F", "X..", "..."), VPat(2, "W.W", ".F.", "..."), VPat(3, "X.F", "...", ".WW"))
    // Boîtes à objets : il en passe une rangée toutes les ~9 secondes
    val boxes = listOf(VPat(0, ".?.", "...", ".o."), VPat(0, "?.?", "...", ".o."), VPat(0, ".o.", "?C?", "...", ".O."), VPat(0, "..?", ".o.", "o..", "?.."))
    // Spécificités de circuit
    val ramps = listOf(VPat(1, ".R.", ".o.", ".O.", ".o.", "..."), VPat(1, "R.R", "o.o", "O.O", "...", ".B."), VPat(2, "RXR", "o.o", "O.O", "...", ".?."), VPat(2, ".R.", ".o.", "XOX", ".o.", "..."))
    val gates = listOf(VPat(2, ".o.", "LLL", ".O.", "...", "..."), VPat(2, "o.o", "LLL", ".o.", "...", ".o.", "...", ".o.", "...", "LLL", ".B."),
        VPat(3, ".o.", "...", "LLL", ".o.", "LLL", ".o.", "LLL", ".B.", "..."))      // trois portiques serrés : on atterrit SUR le premier et on rebondit de l'un à l'autre
}

/**
 * [diff] : 0 = Facile (les accidents ne font que ralentir, le boost part tout seul), 1 = Moyen, 2 = Difficile.
 * [duel] : course à deux, ni chrono ni fantôme. [seed] : graine de la route (la même pour les deux joueurs d'un duel).
 */
internal class VroumGame(val endless: Boolean, val vehicle: Int, val diff: Int, val theme: VTheme, val trickLabel: String, val cannonLabel: String,
                         val duel: Boolean = false, seed: Long = Random.nextLong()) {
    companion object {
        const val LANE_M = 3.2f
        const val Z_FAR = 300f
        const val PLAYER_Z = 18f
        const val LEVEL_LEN = 2000f
        const val INTRO_DUR = 3.6f
        const val FINISH_DUR = 2.7f       // secondes entre la ligne franchie et l'écran de résultat
        const val END_WALL = 46f          // la route s'arrête là, derrière la ligne : tribunes et mur de pneus
        const val ROCKET_WINDOW = 0.7f    // départ canon : on appuie sur BOOST dans cette fenêtre avant le GO…
        const val ROCKET_LATE = 0.3f      // … ou juste après (ceux qui réagissent au « GO! »)
        const val COMBO_T = 3.0f
        const val BOOST_SEG_S = 2.2f       // secondes de boost par segment de jauge
        const val MAX_SEG = 4
    }
    val tune = VTUNES[diff.coerceIn(0, 2)]
    val trait = theme.trait
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
    fun laneXf(lane: Float) = (lane - (lanes - 1) / 2f) * LANE_M
    var lane = 1
    var lx = 0f

    // ---- SAUT : plus de délai d'attente. Retomber SUR un véhicule ou un obstacle fait REBONDIR (chaîne) ; retomber
    //      sur la route met fin à la chaîne et impose une courte pause avant le saut suivant.
    var jumpT = -1f
    /** durée d'un saut = deux rangées de motif : une file de voitures espacées d'une rangée sur deux s'enchaîne pile */
    private val baseJump = 2f * tune.row / 0.85f * trait.jumpMul
    var JUMP_DUR = baseJump
    val JUMP_CD = 1.1f
    var jumpCd = 0f
    var stompChain = 0              // rebonds enchaînés sans toucher la route
    var bestStomp = 0
    var landedT = 0f                // écrasement à l'atterrissage (s restantes)
    var blastT = 0f                 // onde de choc d'atterrissage (s restantes)
    private var trickDone = false
    private var rampJump = false

    var giant = 0; var giantT = 0f
    var cannonT = 0f; private var cannonCd = 0f
    var invuln = 0f
    var slideT = 0f; var slideDir = 0
    var turboT = 0f
    var superT = 0f                 // SUPER boost (6 s, musique spéciale, invincible)
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
    /** multiplicateur de points : il monte avec la SÉRIE (pièces, frôlements, takedowns, rebonds), un accident la casse */
    val mult: Int get() = when { combo >= 40 -> 5; combo >= 24 -> 4; combo >= 12 -> 3; combo >= 5 -> 2; else -> 1 }
    var nearCount = 0; var bumpCount = 0; var takedowns = 0; var revenges = 0
    var dist = 0f
    var elapsed = 0f
    var nextFlag = 500f
    var sputter = -1f
    var over = false; var cleared = false
    // ---- ARRIVÉE (Course) : la ligne est un vrai lieu, à LEVEL_LEN. finishT ≥ 0 = ligne franchie, on freine devant le public.
    var finishT = -1f; private var clearSent = false; private var finishDecor = false; private var fireCd = 0f
    var finishLabel = "ARRIVÉE !"
    /** distance (devant le bolide si > PLAYER_Z) de la ligne d'arrivée, dans le repère de la caméra */
    val finishZ: Float get() = PLAYER_Z + LEVEL_LEN - dist
    var shake = 0f
    var hornCd = 0f
    var hitStop = 0f                // micro-gel à l'impact (s) : le coup « claque »
    var flash = 0f                  // éclair blanc d'écran (bombe)
    @Volatile var frame = 0
    var leanDeg = 0f            // inclinaison latérale (changement de file), degrés
    private var prevLx = 0f
    private var fxCd = 0f

    // ---- ÉLAN : 0..1. Monte tout seul quand on roule proprement, plus vite avec les pièces et les frôlements ; chute sur un accident.
    var flow = 0f
    private var spd = tune.floor            // vitesse réelle lissée (m/s)
    private var flowMaxSaid = false
    private val ceilNow: Float get() = (tune.ceil + (if (endless) min(tune.ceilGrow, dist / 700f) else 0f)) * trait.ceilMul
    /** vitesse de croisière (hors bonus) que donne l'élan actuel */
    val cruise: Float get() = tune.floor + (ceilNow - tune.floor) * flow

    // ---- BOOST (façon Burnout) : jauge en segments. On la remplit en prenant des risques, on la brûle en tenant le bouton.
    //      Deux takedowns ajoutent un segment (jusqu'à 4) ; un accident en retire un.
    var boost = 0.5f                // contenu, en segments
    var boostSeg = 1                // capacité : 1..4 segments
    private var segProgress = 0     // takedowns depuis le dernier segment gagné
    // UNE TAPE = UN SEGMENT : chaque tape sur BOOST brûle un segment de jauge (ou ce qu'il en reste), sans rien tenir.
    @Volatile private var boostTaps = 0                          // tapes reçues de l'écran (fil UI)
    private var seenTaps = 0
    private var burnLeft = 0f                                    // jauge qu'il reste à brûler pour les tapes en cours
    fun tapBoost() { boostTaps++ }
    private var startPress = -1f                                 // départ canon : instant (introT) de la dernière tape
    private var rocketLateT = 0f                                // … et délai de grâce après le GO
    var rocketStart = false; var rocketLabel = "DÉPART CANON !"
    var boosting = false
    private val boostMul: Float get() = if (superT > 0f) 1.7f else if (boosting) 1.45f else if (turboT > 0f) 1.35f else if (giant > 0) 1.3f else 1f
    /** en boost (ou turbo) on défonce aussi les camions et les bus */
    private val powered: Boolean get() = boosting || turboT > 0f

    // ---- OBJETS (boîtes « ? ») : la roulette tourne puis l'objet part tout seul
    var roulette = 0f               // > 0 : la roulette tourne encore (s)
    var rouletteItem = VWeapon.NONE
    var weapon = VWeapon.NONE; var weaponT = 0f
    var shieldT = 0f
    private var weaponTick = 0f
    private var nextBoxAt = 0f

    val enemies = ArrayList<VEnemy>()
    val picks = ArrayList<VPick>()
    val shots = ArrayList<VShot>()
    val parts = ArrayList<VPart>()
    val texts = ArrayList<VText>()
    @Volatile var textSnap: List<VText> = emptyList()
    val decor = ArrayList<VDecor>()
    private var cheerCd = 3f
    private var ambientCd = 0f
    var jumpedLabel = "Sauté !"; var nearLabel = "Frôlé !"; var maxLabel = "À fond !"
    var takedownLabel = "TAKEDOWN !"; var strikeLabel = "STRIKE !"; var revengeLabel = "REVANCHE !"; var stompLabel = "REBOND"
    var crashLabel = "Aïe !"; var shoveLabel = "Poussé !"; var ghostLabel = "Bouh !"; var windLabel = "Vent !"
    var weaponLabels = listOf("BOMBE !", "LASER !", "FLAMMES !", "AIMANT !", "BOUCLIER !")
    val events = ConcurrentLinkedQueue<String>()
    private fun ev(s: String) { events.add(s) }

    // ---- Spécificités de circuit
    var windT = -1f; var windDir = 0; private var nextWindAt = 14f     // rafale : annoncée (windT > 0) puis elle pousse d'une voie
    /** tempête de sable : 0..1, réduit la visibilité par vagues */
    val storm: Float get() = if (!trait.sandstorm) 0f else (0.5f + 0.5f * sin(elapsed * 0.35f)).let { k -> ((k - 0.55f) / 0.45f).coerceIn(0f, 1f) }
    private var laneDelay = 0f; private var laneQueued = 0             // glace : le changement de voie part avec un temps de retard

    // ---- CHEF D'ORCHESTRE : pose les motifs devant le joueur (planD = mètres de route déjà composés)
    private val rng = Random(seed)
    // (le décompte fait parcourir ~2,3 s de route à vitesse plancher : le premier motif est posé au-delà, on ne
    //  passe donc jamais à côté d'un objet qu'on ne pouvait pas encore attraper)
    private var planD = tune.floor * INTRO_DUR * 0.75f + 14f
    private var lastPat: VPat? = null
    private var lastJumpAt = -999f          // distance du dernier saut obligatoire posé
    private var didX2 = false; private var didStar = false; private var didCannon = false; private var didSprint = false
    private var cyclePiece = -1             // Arcade : dernier cycle dont le moment fort est posé
    private var nextFuelAt = 0f             // Arcade : distance du prochain bidon
    private var nextTraitAt = 220f          // prochain motif propre au circuit (tremplins, portiques…)
    private var rivalCd = 6f

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
        nextBoxAt = tune.floor * 5f
        plan()      // la route n'est pas vide au décompte
    }

    /** Passage à [n] voies : la route s'élargit 40 m devant, les entités existantes gardent leur position (ré-indexation). */
    private fun widenTo(n: Int) {
        val shift = (n - lanes) / 2
        oldLanes = lanes; lanes = n
        widenStart = dist + 40f
        lane += shift
        for (e in enemies) { e.lane += shift; e.fx += shift; e.target += shift }
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
    /** Intensité du boost pour les effets (0 rien, 1 turbo ou boost, 2 super). */
    val boostLevel: Int get() = if (superT > 0f) 2 else if (turboT > 0f || boosting) 1 else 0
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
    private val airborne: Boolean get() = jumping && jumpT > JUMP_DUR * 0.12f && jumpT < JUMP_DUR * 0.88f
    /** phase de descente : si un véhicule ou un obstacle passe dessous, on rebondit dessus */
    private val descending: Boolean get() = jumping && jumpT > JUMP_DUR * 0.62f

    fun moveLane(d: Int) {
        if (over || sputter >= 0f || inIntro) return
        if (trait.ice && !jumping) { laneQueued = d; if (laneDelay <= 0f) laneDelay = 0.22f; return }   // glace : ça part avec un temps de retard
        doMoveLane(d)
    }
    private fun doMoveLane(d: Int) {
        val n = (lane + d).coerceIn(laneLo, laneHi)
        if (n == lane) return
        // COUP D'ÉPAULE : un rival est à côté de nous dans la voie visée -> on le pousse
        if (!jumping) for (e in enemies) if (e.rival && e.alive && e.lane == n && abs(e.z - PLAYER_Z) < 3.4f) shove(e, d)
        lane = n; ev("lane")
    }
    fun jump() {
        if (over || jumping || sputter >= 0f || inIntro || jumpCd > 0f) return
        jumpT = 0f; JUMP_DUR = baseJump; trickDone = false; rampJump = false; ev("jump")
    }
    fun horn() { if (hornCd <= 0f) { hornCd = 1.2f; ev("horn") } }

    private fun gainFlow(v: Float) { flow = min(1f, flow + v) }
    /** Accident : on garde [keep] de l'élan et la vitesse retombe d'un coup (c'est ÇA la punition, visible et ressentie). */
    private fun loseFlow(keep: Float) { flow *= keep; spd *= 0.5f + 0.5f * keep }
    private fun chain(n: Int = 1) { combo += n; comboTimer = COMBO_T; if (combo > bestCombo) bestCombo = combo }
    private fun breakChain() { combo = if (diff == 0) combo / 2 else 0; comboTimer = if (combo > 0) COMBO_T else 0f }
    private fun gainBoost(v: Float) { val was = boost; boost = min(boostSeg.toFloat(), boost + v); if (was < boostSeg && boost >= boostSeg) ev("boostFull") }

    fun update(dtRaw: Float) {
        // micro-gel à l'impact : le temps ralentit très fort pendant quelques images
        var dt = dtRaw
        if (hitStop > 0f) { hitStop -= dtRaw; dt = dtRaw * 0.12f }
        elapsed += dt
        if (hornCd > 0f) hornCd -= dt
        if (shake > 0f) shake = max(0f, shake - dtRaw * 60f)
        if (flash > 0f) flash = max(0f, flash - dtRaw * 2.2f)
        if (invuln > 0f) invuln -= dt
        if (landedT > 0f) landedT -= dt
        if (blastT > 0f) blastT -= dt
        val newTaps = boostTaps - seenTaps; seenTaps += newTaps
        if (introT > 0f) {
            // DÉPART CANON : BOOST enfoncé dans la dernière demi-seconde avant le GO (un simple tap suffit ; tenu depuis
            // trop longtemps = rien, sans punition)
            if (newTaps > 0) startPress = introT
            introT -= dt
            val step = kotlin.math.ceil(introT.toDouble()).toInt()
            if (step < introStep && step >= 1) { introStep = step; ev("count") }
            if (introT <= 0f) {
                introT = 0f; ev("go")
                if (startPress > 0f && startPress <= ROCKET_WINDOW) { grantRocketStart(); burnLeft = 1f } else rocketLateT = ROCKET_LATE
            }
        } else if (rocketLateT > 0f) {
            rocketLateT -= dt
            if (newTaps > 0) { rocketLateT = 0f; grantRocketStart() }
        }
        val racing = !over && sputter < 0f && !cleared && !inIntro
        // élan : monte tout seul ; la vitesse rejoint la croisière (accélère vite, ralentit doucement après un boost)
        if (racing) {
            gainFlow(dt / tune.flowTime)
            if (flow >= 1f && !flowMaxSaid) { flowMaxSaid = true; texts.add(VText(lx * LANE_M, 4.2f, PLAYER_Z, maxLabel, 1.3f, V_GOLD, big = true)); ev("flowMax") }
            if (flow < 0.7f) flowMaxSaid = false
        }
        // boost : une tape brûle UN segment (plusieurs tapes s'additionnent) ; en Facile il part tout seul, jauge pleine
        if (racing) {
            if (newTaps > 0 && boost > 0.12f) burnLeft = min(boost, burnLeft + newTaps)
            if (diff == 0 && boost >= boostSeg - 0.001f) burnLeft = boost
            if (burnLeft > 0f && !boosting) { boosting = true; shake = max(shake, 8f); ev("boostOn") }
            if (boosting) {
                val d = dt / BOOST_SEG_S
                boost -= d; burnLeft -= d
                shake = max(shake, 2.5f)            // la caméra tremble tant qu'on brûle le boost
                if (boost <= 0f || burnLeft <= 0f) { boost = max(0f, boost); burnLeft = 0f; boosting = false; ev("boostOff") }
            }
        } else if (boosting) { boosting = false; burnLeft = 0f; ev("boostOff") }
        val target = if (cleared) 0f else cruise * boostMul        // ligne franchie : on freine et on s'arrête devant le public
        spd += (target - spd) * min(1f, dt * (if (cleared) 1.55f else if (target > spd) 3.4f else 1.5f))
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

        // glace : le changement de voie demandé part après un court délai, et le bolide glisse plus longtemps
        if (laneDelay > 0f) { laneDelay -= dt; if (laneDelay <= 0f && laneQueued != 0) { doMoveLane(laneQueued); laneQueued = 0 } }
        var tl = targetLateral
        if (slideT > 0f) { slideT -= dt; tl += slideDir * 0.22f * sin(slideT * 18f) }
        lx += (tl - lx) * min(1f, dt * (if (trait.ice) 5f else 9f))
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

        if (jumping) {
            jumpT += dt
            if (descending && !over) tryStomp()
            if (jumping && jumpT >= JUMP_DUR) { jumpT = -1f; land() }
        }
        if (jumpCd > 0f) jumpCd -= dt
        if (turboT > 0f) turboT -= dt
        if (superT > 0f) { superT -= dt; if (superT <= 0f) { superT = 0f; ev("boostEnd") } }
        if (shieldT > 0f) shieldT -= dt
        // traînée de feu pendant un boost : flammes bleues (boost tenu), cyan/orange (turbo), arc-en-ciel (super)
        if (boostLevel > 0 && !over && parts.size < 190 && sp > 2f) {
            val x = lx * LANE_M; val gy = groundY(dist + PLAYER_Z)
            repeat(if (boostLevel == 2 || boosting) 3 else 2) {
                val k = Random.nextFloat()
                val (cr, cg, cb) = if (boostLevel == 2) rainbow(elapsed * 3f + k) else if (boosting) Triple(0.35f + 0.65f * k, 0.55f + 0.4f * k, 1f) else Triple(0.3f + 0.7f * k, 0.6f + 0.3f * k, 1f)
                parts.add(VPart(x + (Random.nextFloat() - 0.5f) * 1.4f, gy + 0.3f + Random.nextFloat() * 0.8f, PLAYER_Z - 1.2f, (Random.nextFloat() - 0.5f) * 2f, 1.5f + Random.nextFloat() * 2f, 0.3f + Random.nextFloat() * 0.25f, 0.5f, cr, cg, cb, 0.3f + Random.nextFloat() * 0.35f, floaty = true))
            }
        }
        ambient(dt)
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
        if (racing) weapons(dt)

        if (racing) {
            if (timeLimit > 0f) {
                timeLeft -= dt
                if (timeLeft <= 0f) { timeLeft = 0f; timeUp = true; over = true; ev("timeup") }
            } else if (fuelOn) {
                fuel -= dt * tune.drain * (1f + 0.1f * (dist / 900f).toInt())      // le moteur consomme plus à chaque vague
                if (fuel <= 0f) { fuel = 0f; sputter = 0f; ev("sputter"); if (giant > 0) endGiant() }
            }
            // rafales de vent (royaume des nuages) : annoncées 1,3 s avant, puis elles poussent d'une voie
            if (trait.wind) {
                if (windT > 0f) { windT -= dt; if (windT <= 0f) { windT = -1f; if (!jumping && giant == 0) { val n = (lane + windDir).coerceIn(laneLo, laneHi); if (n != lane) { lane = n; slideT = 0.5f; slideDir = windDir; ev("wind") } } } }
                else if (raceT > nextWindAt) { nextWindAt = raceT + 9f + Random.nextFloat() * 7f; windT = 1.3f; windDir = if (Random.nextBoolean()) 1 else -1
                    texts.add(VText(0f, 5f, 34f, (if (windDir < 0) "◀◀ " else "") + windLabel + (if (windDir > 0) " ▶▶" else ""), 1.3f, V_CYAN, big = true)); ev("windWarn") }
            }
        }
        if (sputter >= 0f) { sputter += dt; if (sputter > 2.8f && !over) { over = true; ev("over") } }
        if (!endless && !cleared && dist >= LEVEL_LEN) {
            cleared = true; finishT = 0f
            if (timeLimit > 0f) { timeBonus = (timeLeft * 40f).toInt() * (diff + 1); score += timeBonus }
            ev("finishLine")
            texts.add(VText(0f, 4.4f, PLAYER_Z + 4f, finishLabel, 2.4f, V_GOLD, big = true).also { it.style = 1 })
            for (d in decor) if (d.cheer >= 0) d.arms = 99f
            // le trafic encore devant nous s'efface dans un nuage (personne ne fonce dans les tribunes)
            val eit = enemies.iterator()
            while (eit.hasNext()) { val e = eit.next(); if (e.z > PLAYER_Z + 6f) { burst(laneX(e.lane.coerceIn(0, lanes - 1)), e.z, 1f, 1f, 1f, 5); eit.remove() } }
            picks.clear()
            confetti(60)
        }
        // séquence d'arrivée : confettis et feux d'artifice pendant que le bolide s'arrête, puis le résultat
        if (finishT >= 0f) {
            finishT += dtRaw
            fireCd -= dtRaw
            if (fireCd <= 0f) { fireCd = 0.24f; firework(); confetti(8) }
            if (finishT > FINISH_DUR && !clearSent) { clearSent = true; ev("clear") }
        }
        // l'aire d'arrivée s'installe dès qu'elle entre dans le champ : spectateurs serrés, tribunes derrière eux
        if (!endless && !finishDecor && finishZ < Z_FAR + 15f) {
            finishDecor = true
            val zl = finishZ
            var z = zl - 110f
            while (z < zl + END_WALL - 3f) {
                for (side in intArrayOf(-1, 1)) decor.add(VDecor(z + Random.nextFloat() * 2f, side * (decorHalf + 1.0f + Random.nextFloat() * 1.4f), -1, cheer = Random.nextInt(CHEER_KEYS.size)).also { it.arms = 99f })
                z += 4.5f
            }
            z = zl - 96f
            while (z < zl + END_WALL - 8f) { for (side in intArrayOf(-1, 1)) decor.add(VDecor(z, side * (decorHalf + 7.2f), -1, special = 1)); z += 12.5f }
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
            // rivaux : il en arrive un par derrière de temps en temps, jusqu'au nombre voulu
            rivalCd -= dt
            if (rivalCd <= 0f && !duel) {
                rivalCd = 5f + Random.nextFloat() * 5f
                if (enemies.count { it.rival && it.alive } < tune.rivals + trait.rivalsExtra && (endless || dist < LEVEL_LEN - 250f)) spawnRival()
            }
        }

        // tirs de canons : avancent, détruisent tout ce qui roule et les flaques
        val sit = shots.iterator()
        while (sit.hasNext()) {
            val sh = sit.next(); sh.z += 70f * dt
            if (sh.z > 220f) { sit.remove(); continue }
            var hit = false
            for (e in enemies) if (e.alive && !e.ghost && e.lane == sh.lane && abs(e.z - sh.z) < 2.5f) { takedown(e, 3); ev("shotHit"); hit = true; break }
            if (!hit) { val pit0 = picks.iterator(); while (pit0.hasNext()) { val p = pit0.next(); if ((p.type == VItem.PUDDLE || p.type == VItem.LOG) && p.lane == sh.lane && abs(p.z - sh.z) < 2.5f) { pit0.remove(); burst(laneX(p.lane), p.z, 0.55f, 0.35f, 0.17f, 8); hit = true; break } } }
            if (hit) sit.remove()
        }

        updateEnemies(dt, sp, racing)

        val pit = picks.iterator()
        while (pit.hasNext()) {
            val p = pit.next()
            p.z -= dz; p.t += dt
            // obstacle mobile (chat, rocher, crabe, vache…) : il attend sur le bas-côté puis traverse quand il est à ~2,4 s du joueur
            if (p.fDir != 0) {
                if ((p.z - PLAYER_Z) < max(1f, sp) * 2.4f) p.fLane += p.fDir * dt * lanes / 3.2f
                p.lane = Math.round(p.fLane)
                if (p.fLane < -0.95f || p.fLane > lanes - 0.05f) { pit.remove(); continue }
            }
            // aimant : les pièces à portée glissent vers nous et sont ramassées quelle que soit leur voie
            if (p.magnet) p.mx += ((lx + (lanes - 1) / 2f) - (p.lane + p.mx)) * min(1f, dt * 7f)
            if (p.z < 0.5f) { pit.remove(); continue }
            if (p.z > PLAYER_Z - 1.4f && p.z < PLAYER_Z + 1.6f && (p.lane in coveredLanes() || p.magnet)) {
                val ground = p.type == VItem.PUDDLE || p.type == VItem.TURBO || p.type == VItem.RAMP
                if (ground && airborne) continue
                if (p.type == VItem.RAMP) { if (!p.used) { p.used = true; p.t = 0f; collect(p) }; continue }   // le ressort reste et oscille
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
            p.x += p.vx * dt; p.y += p.vy * dt; if (!p.floaty) p.vy -= 9f * dt; p.z += p.vz * dt - dz * (if (p.floaty) 1f else 0.2f)
            if (p.life <= 0f) prt.remove()
        }
        val tit = texts.iterator()
        while (tit.hasNext()) { val t = tit.next(); t.life -= dt; t.y += 1.2f * dt; if (t.pop > 0f) t.pop = max(0f, t.pop - dt * 4.5f); if (t.life <= 0f) tit.remove() }
        textSnap = if (texts.isEmpty()) emptyList() else ArrayList(texts)
    }

    private fun ambient(dt: Float) {
        ambientCd -= dt
        if (ambientCd > 0f || parts.size >= 150 || theme.ambient == 0) return
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
                repeat(4) {
                    val rz = 12f + Random.nextFloat() * 40f
                    parts.add(VPart((Random.nextFloat() - 0.5f) * 16f, groundY(dist + rz) + 2f + Random.nextFloat() * 6f, rz, 1.2f, -16f, 0.6f, 0.6f, 0.75f, 0.85f, 1f, 0.04f, floaty = true, streak = 16f))
                }
                if (Random.nextFloat() < 0.25f) parts.add(VPart(ax, gy + 0.4f + Random.nextFloat() * 2f, az, (Random.nextFloat() - 0.5f) * 0.6f, (Random.nextFloat() - 0.3f) * 0.5f, 2.5f + Random.nextFloat() * 2f, 4f, 1f, 0.95f, 0.45f, 0.14f, floaty = true))
            }
            // bulles qui montent (océan)
            6 -> parts.add(VPart(ax, gy + Random.nextFloat() * 1.5f, az, (Random.nextFloat() - 0.5f) * 0.5f, 1.4f + Random.nextFloat() * 1.4f, 3f, 3f, 0.75f, 0.92f, 1f, 0.16f + Random.nextFloat() * 0.2f, floaty = true))
            // sable qui file en travers (désert), plus dense pendant la tempête
            7 -> repeat(if (storm > 0.3f) 3 else 1) { parts.add(VPart(-16f + Random.nextFloat() * 6f, gy + 0.3f + Random.nextFloat() * 3f, 10f + Random.nextFloat() * 70f, 14f + Random.nextFloat() * 8f, 0f, 1.6f, 1.6f, 0.95f, 0.8f, 0.5f, 0.10f, floaty = true, streak = 5f)) }
            // étoiles filantes / poussière d'étoiles (espace, cyber)
            8 -> parts.add(VPart(ax, gy + 2f + Random.nextFloat() * 8f, az, (Random.nextFloat() - 0.5f) * 1.5f, (Random.nextFloat() - 0.5f) * 0.6f, 2.5f, 2.5f, 0.7f + 0.3f * Random.nextFloat(), 0.85f, 1f, 0.12f, floaty = true))
            // confettis de sucre (bonbons) / feuilles (ferme, jungle)
            9 -> { val (cr, cg, cb) = rainbow(Random.nextFloat()); parts.add(VPart(ax, gy + 5f + Random.nextFloat() * 4f, az, 0.8f * (Random.nextFloat() - 0.5f), -1.1f - Random.nextFloat(), 4f, 4f, cr, cg, cb, 0.16f, floaty = true)) }
        }
    }

    // -----------------------------------------------------------------------
    // Trafic, rivaux, takedowns
    // -----------------------------------------------------------------------

    private fun updateEnemies(dt: Float, sp: Float, racing: Boolean) {
        val cover = coveredLanes()
        val eit = enemies.iterator()
        while (eit.hasNext()) {
            val e = eit.next()
            // voiture éjectée : elle part devant en tournoyant, percute éventuellement les suivantes (STRIKE), puis sort de la route
            if (e.fly >= 0f) {
                e.fly += dt
                e.z += (12f - 26f * e.fly) * dt
                e.flyX += e.flyVx * dt; e.flyY = max(0f, sin(PI.toFloat() * (e.fly / 0.9f).coerceIn(0f, 1f)) * 3.2f); e.spin += dt * 520f * (if (e.flyVx >= 0f) 1f else -1f)
                if (e.fly < 0.55f) for (o in enemies) if (o !== e && o.alive && !o.ghost && !o.oncoming && o.lane == e.lane && abs(o.z - e.z) < 3f) { takedown(o, 4); break }
                if (e.fly > 1.25f) eit.remove()
                continue
            }
            if (e.rival) updateRival(e, dt, sp)
            e.z -= (sp - e.mps) * dt
            if (e.bounce > 0f) { e.bounce -= dt; e.z += 14f * dt }
            if (e.crushed >= 0f) { e.crushed += dt; if (e.crushed > 0.55f) { eit.remove(); continue } }
            if (e.z < (if (e.rival) -14f else 0.5f) || e.z > Z_FAR + 500f) { eit.remove(); continue }
            if (e.crushed >= 0f) continue
            if (e.z > PLAYER_Z - 1.6f && e.z < PLAYER_Z + 2.0f && e.lane in cover) {
                when {
                    e.ghost -> if (!e.hit) { e.hit = true; chain(); gainBoost(0.2f); addScore(40, laneX(e.lane), e.z, V_CYAN); texts.add(VText(lx * LANE_M, 3.4f, PLAYER_Z, ghostLabel, 1.0f, V_CYAN, big = true)); ev("ghost") }
                    giant > 0 || superT > 0f -> takedown(e, 1)
                    airborne -> if (!trickDone) { trickDone = true; chain(); gainFlow(0.04f); gainBoost(0.12f); addScore(25, lx * LANE_M, PLAYER_Z, V_CYAN); texts.add(VText(lx * LANE_M, 4f, PLAYER_Z, trickLabel, 1.0f, V_CYAN, big = true)); ev("trick") }
                    invuln > 0f -> {}
                    e.oncoming || e.boulder -> crash(e)
                    e.rival -> if (powered) takedown(e, 1) else { e.stun = 0.5f; e.bounce = 0.5f; flow *= 0.92f; shake = max(shake, 5f); invuln = 0.35f; ev("bumpSoft") }
                    !e.heavy || powered -> takedown(e, if (powered) 1 else 0)
                    else -> crash(e)
                }
                if (!e.alive) continue
            }
            // FRÔLEMENT : j'étais dans SA voie à moins de nearT secondes de l'impact, et je l'ai laissée passer sans la toucher
            if (!e.hit && !e.passed && !e.rival && !e.ghost) {
                val gapZ = e.z - PLAYER_Z; val closing = sp - e.mps
                val dangerous = e.oncoming || e.boulder || (e.heavy && !powered)
                if (dangerous && gapZ > 2f && closing > 1f && giant == 0 && !jumping && invuln <= 0f && e.lane in cover && (gapZ - 2f) / closing < tune.nearT) e.threat = true
                if (gapZ < -1.6f) { e.passed = true; if (e.threat && racing && !jumping) nearMiss(e) }
            }
        }
    }

    /** Rival : il tient sa distance devant nous (élastique), change de voie, et parfois nous serre (annoncé par un « ! »). */
    private fun updateRival(e: VEnemy, dt: Float, sp: Float) {
        val off = e.z - PLAYER_Z
        if (e.stun > 0f) { e.stun -= dt; e.mps = sp - 3f }
        else {
            e.mps = (sp + (e.want - off) * 0.9f).coerceIn(sp - 10f, sp + 11f)
            e.aiCd -= dt
            if (e.aiCd <= 0f) {
                e.aiCd = 1.3f + rng.nextFloat() * 1.8f
                e.want = listOf(7f, 14f, 24f, 36f, 50f)[rng.nextInt(5)]
                // évite ce qui est juste devant lui, sinon flâne d'une voie
                val blocked = enemies.any { it !== e && it.alive && !it.rival && it.lane == e.target && it.z > e.z && it.z - e.z < 22f }
                if (blocked || rng.nextFloat() < 0.45f) e.target = (e.target + (if (rng.nextBoolean()) 1 else -1)).coerceIn(0, lanes - 1)
                // il nous serre : il est à côté de nous, il prévient (« ! ») puis se rabat sur notre voie
                if (tune.aggro > 0f && abs(off) < 6f && abs(e.lane - lane) == 1 && rng.nextFloat() < tune.aggro * 0.55f) { e.warn = 0.9f; e.want = 0f; ev("rivalWarn") }
            }
            if (e.warn > 0f) { e.warn -= dt; if (e.warn <= 0f && abs(off) < 4.5f && abs(e.lane - lane) == 1 && !jumping && giant == 0) rivalHits(e) }
        }
        e.fx += (e.target - e.fx).coerceIn(-dt * 3.2f, dt * 3.2f)
        e.lane = Math.round(e.fx).coerceIn(0, lanes - 1)
    }

    /** Le rival nous rentre dedans par le côté : on est dégagés d'une voie, on perd de l'élan… et il devient une cible de REVANCHE. */
    private fun rivalHits(e: VEnemy) {
        if (invuln > 0f || superT > 0f) return
        if (shieldT > 0f) { popShield(); e.stun = 1.2f; return }
        val d = if (e.lane < lane) 1 else -1
        e.target = lane; e.revenge = true
        val n = (lane + d).coerceIn(laneLo, laneHi)
        if (n != lane) { lane = n; slideT = 0.5f; slideDir = d }
        if (diff != 0) { loseFlow(0.75f); boost = max(0f, boost - 0.4f) } else flow *= 0.9f
        invuln = 0.9f; shake = max(shake, 11f); hitStop = 0.05f
        texts.add(VText(lx * LANE_M, 3.6f, PLAYER_Z, crashLabel, 1.0f, V_RED, big = true))
        ev("rivalHit")
    }

    /** Coup d'épaule : on se rabat sur un rival qui roule à côté. Au bord de la route (ou déjà sonné) il sort : TAKEDOWN. */
    private fun shove(e: VEnemy, d: Int) {
        val out = e.lane + d < 0 || e.lane + d > lanes - 1
        if (out || e.stun > 0f) { e.flyVx = d * 11f; takedown(e, 5) }
        else {
            e.target = e.lane + d; e.fx = e.lane + d * 0.6f; e.stun = 1.3f; e.warn = 0f
            chain(); gainBoost(0.3f); addScore(40, laneX(e.lane), e.z, V_ORANGE)
            texts.add(VText(laneX(e.lane), 3.2f, e.z, shoveLabel, 0.9f, V_ORANGE, big = true))
            shake = max(shake, 7f); ev("shove")
        }
    }

    private fun spawnRival() {
        val e = VEnemy(listOf(0, lanes - 1).filter { it != lane }.let { if (it.isEmpty()) 0 else it[rng.nextInt(it.size)] }, -10f, 0, mps + 8f)
        e.rival = true
        e.veh = (vehicle + 1 + rng.nextInt(VEHICLES.size - 1)) % VEHICLES.size
        e.fx = e.lane.toFloat(); e.target = e.lane; e.want = 24f; e.aiCd = 2.5f
        enemies.add(e); ev("rivalIn")
    }

    /**
     * TAKEDOWN. [how] : 0 percutée par derrière · 1 en boost / géant · 2 écrasée en sautant · 3 arme · 4 STRIKE (touchée
     * par une voiture éjectée) · 5 coup d'épaule. La voiture est éjectée (ou aplatie), la jauge de boost se remplit et
     * s'allonge (deux takedowns = un segment de plus), la série monte.
     */
    private var tdChain = 0; private var tdChainAt = -9f; private var lastImpact: VText? = null
    private fun takedown(e: VEnemy, how: Int) {
        if (!e.alive) return
        takedowns++
        val x = laneX(e.lane)
        val big = e.rival || e.heavy
        if (how == 2) e.crushed = 0f
        else { e.fly = 0f; if (e.flyVx == 0f) e.flyVx = (if (x - lx * LANE_M >= 0f) 1f else -1f) * (5f + Random.nextFloat() * 5f) * (if (how == 4 || how == 3) 1.3f else 1f) }
        chain(if (big) 2 else 1)
        var pts = when (how) { 0 -> 40; 4 -> 80; 5 -> 120; else -> 60 } * (if (big) 2 else 1)
        // l'annonce : TAKEDOWN (percutée, coup d'épaule, arme), STRIKE (ricochet), REVANCHE ; l'écrasement a déjà son « REBOND ».
        // Les takedowns qui s'enchaînent en moins de 2,5 s font monter un compteur « ×n » sur le MÊME message.
        var label = if (how == 4) strikeLabel else if (how == 2) "" else takedownLabel
        if (e.rival && e.revenge) { pts *= 2; label = revengeLabel; revenges++; gainBoost(1f); ev("revenge") }
        addScore(pts, x, e.z, if (how == 4) V_GOLD else V_PINK)
        if (label.isNotEmpty()) {
            tdChain = if (elapsed - tdChainAt < 2.5f) tdChain + 1 else 1
            tdChainAt = elapsed
            val shownLabel = if (tdChain > 1 && label != revengeLabel) "$label ×$tdChain" else label
            val last = lastImpact
            if (last != null && last.life > 0.3f && label != revengeLabel) { last.text = shownLabel; last.life = 1.15f; last.pop = 1f; last.x = lx * LANE_M; last.y = 4.4f }
            else lastImpact = VText(lx * LANE_M, 4.4f, PLAYER_Z + 4f, shownLabel, 1.15f, if (label == revengeLabel) V_RED else V_GOLD, big = true).also { it.style = 1; texts.add(it) }
        }
        // percuter sans boost rapporte peu ; en boost, en rebond, en strike ou d'un coup d'épaule, beaucoup plus
        gainBoost(when (how) { 0 -> 0.08f; 1 -> 0.20f; 2 -> 0.22f; 3 -> 0.04f; 4 -> 0.25f; else -> 0.45f } * (if (big) 1.5f else 1f))
        gainFlow(0.03f)
        // la jauge s'allonge : un segment de plus tous les deux takedowns « francs » (pas ceux des armes)
        if (how == 1 || how == 2 || how == 4 || how == 5) { segProgress += if (e.rival) 3 else 1; if (segProgress >= 3 && boostSeg < MAX_SEG) { segProgress = 0; boostSeg++; ev("segUp") } }
        // premier contact au ralenti : ça claque
        if (how == 0 || how == 1 || how == 5) { hitStop = max(hitStop, if (big) 0.09f else 0.05f); if (how == 0 && !powered) { flow *= 0.90f; spd *= 0.95f } }
        shake = max(shake, if (big) 12f else 7f)
        burst(x, e.z, 1f, 0.75f, 0.3f, if (big) 18 else 10)
        if (parts.size < 170) repeat(6) { parts.add(VPart(x, groundY(dist + e.z) + 1f, e.z, (Random.nextFloat() - 0.5f) * 8f, 3f + Random.nextFloat() * 4f, 0.5f, 0.5f, 0.3f, 0.3f, 0.32f, 0.22f)) }
        ev(if (how == 4) "strike" else if (how == 2) "stomp" else if (how == 3) "zap" else if (big) "takedownBig" else "takedown")
        if (combo % 6 == 0) { ev("cheer"); for (d in decor) if (d.cheer >= 0) d.arms = 1.8f }
    }

    /** ACCIDENT (véhicule en face, camion sans boost, rocher) : l'élan retombe, la jauge perd un segment, la série casse. */
    private fun crash(e: VEnemy) {
        if (shieldT > 0f) { popShield(); e.hit = true; takedown(e, 3); return }
        bumpCount++
        e.hit = true
        if (fuelOn && diff != 0) fuel = max(0f, fuel - (if (diff == 2) 25f else 15f))
        breakChain(); loseFlow(tune.bumpKeep)
        if (diff != 0 && boostSeg > 1) { boostSeg--; segProgress = 0 }
        boost = min(boost, boostSeg.toFloat()) * (if (diff == 0) 1f else 0.5f)
        invuln = 1.1f; if (!e.oncoming && !e.boulder) e.bounce = 0.6f
        shake = max(shake, 13f); hitStop = max(hitStop, 0.07f)
        burst(laneX(e.lane), e.z, 1f, 1f, 1f, 10)
        texts.add(VText(lx * LANE_M, 3.4f, PLAYER_Z, crashLabel, 0.9f, V_RED, big = true))
        ev("bump")
    }

    private fun popShield() {
        shieldT = 0f; invuln = 0.8f; shake = max(shake, 8f)
        burst(lx * LANE_M, PLAYER_Z, 0.4f, 0.8f, 1f, 22); ev("shieldPop")
    }

    // -----------------------------------------------------------------------
    // Saut : rebonds en chaîne
    // -----------------------------------------------------------------------

    /** En descente : s'il y a un véhicule ou un obstacle sous le bolide, on REBONDIT dessus (et on l'écrase). */
    private fun tryStomp() {
        val cover = coveredLanes()
        var hitX = 0f; var hitZ = 0f; var found = false
        for (e in enemies) if (e.alive && !e.ghost && e.lane in cover && abs(e.z - PLAYER_Z) < 3.3f) { takedown(e, 2); hitX = laneX(e.lane); hitZ = e.z; found = true; break }
        if (!found) {
            val pit = picks.iterator()
            while (pit.hasNext()) { val p = pit.next(); if (p.type == VItem.LOG && p.lane in cover && abs(p.z - PLAYER_Z) < 3.0f) { pit.remove(); hitX = laneX(p.lane); hitZ = p.z; found = true; burst(hitX, hitZ, 0.6f, 0.45f, 0.3f, 12); break } }
        }
        if (!found) return
        stompChain++; if (stompChain > bestStomp) bestStomp = stompChain
        jumpT = 0f; JUMP_DUR = baseJump; rampJump = false; trickDone = false; jumpCd = 0f
        chain(stompChain); gainBoost(0.18f + 0.07f * min(stompChain, 5)); gainFlow(0.04f)
        addScore(30 * stompChain, hitX, hitZ, V_CYAN)
        texts.add(VText(lx * LANE_M, 4.6f, PLAYER_Z, if (stompChain > 1) "$stompLabel ×$stompChain" else stompLabel, 1.0f, V_CYAN, big = true))
        shake = max(shake, 6f); hitStop = max(hitStop, 0.04f)
        ev("bounce")
    }

    /** Retour sur la route : fin de la chaîne. Après 3 rebonds ou plus, l'atterrissage fait une onde de choc qui nettoie tout autour. */
    private fun land() {
        landedT = 0.25f; shake = max(shake, 8f); invuln = max(invuln, 0.35f)
        jumpCd = if (rampJump) 0.2f else JUMP_CD
        val pound = stompChain >= 3
        stompChain = 0
        var any = false
        if (pound) {
            blastT = 0.5f; shake = max(shake, 16f)
            for (e in ArrayList(enemies)) if (e.alive && !e.ghost && abs(e.z - PLAYER_Z) < 14f) { takedown(e, 3); any = true }
        }
        // nuage de poussière bas et large : particules terre + herbe qui roulent
        val x = lx * LANE_M
        if (parts.size < 150) repeat(if (pound) 26 else 10) {
            val a = Random.nextFloat() * 2f * PI.toFloat(); val s = 3f + Random.nextFloat() * 5f
            val gy = groundY(dist + PLAYER_Z) + 0.15f
            val k = Random.nextFloat()
            parts.add(VPart(x + cos(a) * 0.8f, gy, PLAYER_Z + sin(a) * 0.8f, cos(a) * s, 0.8f + Random.nextFloat() * 1.6f, 0.45f + Random.nextFloat() * 0.35f, 0.8f,
                0.55f + 0.2f * k, 0.45f + 0.25f * k, 0.3f + 0.15f * k, 0.35f + Random.nextFloat() * 0.35f))
        }
        ev(if (any) "landBlast" else "land")
    }

    // -----------------------------------------------------------------------
    // Objets
    // -----------------------------------------------------------------------

    private fun openBox() {
        if (roulette > 0f) return
        roulette = 1.0f
        val pool = arrayListOf(VWeapon.BOMB, VWeapon.LASER, VWeapon.FLAME, VWeapon.MAGNET, VWeapon.SHIELD, VWeapon.CANNON, VWeapon.BOMB, VWeapon.LASER, VWeapon.FLAME, VWeapon.MAGNET)
        if (giant == 0) pool.add(VWeapon.STAR)
        if (superT <= 0f) pool.add(VWeapon.SUPER)
        pool.remove(weapon)
        rouletteItem = pool[Random.nextInt(pool.size)]
        ev("roulette")
    }

    private fun useItem(w: VWeapon) {
        val x = lx * LANE_M
        when (w) {
            VWeapon.STAR -> startGiant()
            VWeapon.SUPER -> superBoost(x, PLAYER_Z)
            VWeapon.CANNON -> { cannonT = w.dur; cannonCd = 0f; ev("cannon") }
            VWeapon.SHIELD -> { shieldT = w.dur; ev("shield") }
            VWeapon.NONE -> {}
            else -> { weapon = w; weaponT = w.dur; weaponTick = 0f; ev(when (w) { VWeapon.BOMB -> "bombArm"; VWeapon.LASER -> "laser"; VWeapon.FLAME -> "flame"; else -> "magnet" }) }
        }
        val name = when (w) { VWeapon.BOMB -> weaponLabels[0]; VWeapon.LASER -> weaponLabels[1]; VWeapon.FLAME -> weaponLabels[2]; VWeapon.MAGNET -> weaponLabels[3]; VWeapon.SHIELD -> weaponLabels[4]; VWeapon.CANNON -> cannonLabel; else -> "" }
        if (name.isNotEmpty()) texts.add(VText(x, 3.8f, PLAYER_Z, name, 1.2f, V_GOLD, big = true))
    }

    private fun weapons(dt: Float) {
        if (roulette > 0f) { roulette -= dt; if (roulette <= 0f) { roulette = 0f; useItem(rouletteItem) } }
        if (weapon == VWeapon.NONE) return
        weaponT -= dt; weaponTick -= dt
        val cover = coveredLanes()
        val x = lx * LANE_M; val gy = groundY(dist + PLAYER_Z)
        when (weapon) {
            // la bombe part devant, explose au bout de sa course et balaie tout ce qui roule à l'écran
            VWeapon.BOMB -> if (weaponT <= 0f) {
                flash = 1f; shake = max(shake, 22f); hitStop = 0.1f
                for (e in ArrayList(enemies)) if (e.alive && !e.ghost && e.z > PLAYER_Z - 6f && e.z < 190f) takedown(e, 3)
                val pit = picks.iterator()
                while (pit.hasNext()) { val p = pit.next(); if ((p.type == VItem.LOG || p.type == VItem.PUDDLE || p.type == VItem.HOLE) && p.z < 190f) { pit.remove(); burst(laneX(p.lane), p.z, 0.6f, 0.5f, 0.35f, 6) } }
                repeat(3) { burst(x + (it - 1) * 4f, PLAYER_Z + 30f + it * 14f, 1f, 0.6f, 0.2f, 30) }
                ev("bomb")
            }
            // rayon continu dans notre voie : tout ce qui s'y trouve est pulvérisé, loin devant
            VWeapon.LASER -> if (weaponTick <= 0f) {
                weaponTick = 0.08f
                for (e in ArrayList(enemies)) if (e.alive && !e.ghost && e.lane in cover && e.z > PLAYER_Z && e.z < PLAYER_Z + 170f) takedown(e, 3)
                val pit = picks.iterator()
                while (pit.hasNext()) { val p = pit.next(); if (p.type == VItem.LOG && p.lane in cover && p.z > PLAYER_Z && p.z < PLAYER_Z + 170f) { pit.remove(); burst(laneX(p.lane), p.z, 0.4f, 0.9f, 1f, 8) } }
            }
            // lance-flammes : large (les voies voisines aussi) mais court
            VWeapon.FLAME -> {
                if (parts.size < 190) repeat(4) {
                    val k = Random.nextFloat()
                    parts.add(VPart(x + (Random.nextFloat() - 0.5f) * 1.2f, gy + 0.9f, PLAYER_Z + 1.5f, (Random.nextFloat() - 0.5f) * 9f, 0.5f + Random.nextFloat() * 2f, 0.35f + Random.nextFloat() * 0.3f, 0.65f,
                        1f, 0.45f + 0.5f * k, 0.1f + 0.2f * k, 0.5f + Random.nextFloat() * 0.6f, floaty = true, vz = 45f + Random.nextFloat() * 25f))
                }
                if (weaponTick <= 0f) {
                    weaponTick = 0.1f
                    for (e in ArrayList(enemies)) if (e.alive && !e.ghost && e.lane >= cover.first - 1 && e.lane <= cover.last + 1 && e.z > PLAYER_Z && e.z < PLAYER_Z + 42f) takedown(e, 3)
                    val pit = picks.iterator()
                    while (pit.hasNext()) { val p = pit.next(); if (p.type == VItem.LOG && p.lane >= cover.first - 1 && p.lane <= cover.last + 1 && p.z > PLAYER_Z && p.z < PLAYER_Z + 42f) { pit.remove(); burst(laneX(p.lane), p.z, 1f, 0.5f, 0.2f, 8) } }
                }
            }
            // aimant : toutes les pièces à portée viennent à nous
            VWeapon.MAGNET -> for (p in picks) if (!p.magnet && p.z < PLAYER_Z + 55f && (p.type == VItem.ACORN || p.type == VItem.MUSHROOM || p.type == VItem.BERRY || p.type == VItem.FUEL)) p.magnet = true
            else -> {}
        }
        if (weaponT <= 0f) { weapon = VWeapon.NONE; ev("weaponEnd") }
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
        val v = max(tune.floor, cruise)
        var tier: Int
        if (!endless) {
            val p = at / LEVEL_LEN
            if (!didX2 && p >= 0.20f) { didX2 = true; return VPatterns.x2 }
            if (!didStar && p >= 0.40f) { didStar = true; return VPatterns.star }
            if (!didCannon && p >= 0.64f) { didCannon = true; return VPatterns.cannon }
            if (!didSprint && p >= 0.85f) { didSprint = true; return VPatterns.sprint }
            if (didSprint) return pick(0)                     // ligne droite finale : pièces et voitures à percuter
            tier = base + when { p < 0.08f -> 0; p < 0.20f -> 1; p < 0.40f -> 2; p < 0.52f -> 1; p < 0.64f -> 2; else -> 3 }
        } else {
            val cycle = (at / 900f).toInt(); val q = (at % 900f) / 900f
            if (fuelOn && at >= nextFuelAt) {
                // les bidons s'espacent à chaque vague : c'est ce qui finit par arrêter la partie
                nextFuelAt = at + v * (tune.fuelEvery + tune.fuelGrow * cycle)
                val t = (base + 1 + cycle / 2).coerceIn(1, 3)
                return VPatterns.fuel.filter { it.tier <= t }.let { it[rng.nextInt(it.size)] }
            }
            if (q >= 0.82f && cyclePiece < cycle) {
                cyclePiece = cycle
                return when (cycle % 4) { 0 -> VPatterns.x2; 1 -> VPatterns.star; 2 -> VPatterns.cannon; else -> VPatterns.sprint }
            }
            tier = base + 1 + cycle / 2 + (if (q in 0.45f..0.82f) 1 else 0) - (if (q < 0.10f) 1 else 0)
        }
        // une boîte à objets toutes les ~9 secondes de route
        if (at >= nextBoxAt) { nextBoxAt = at + v * (8f + rng.nextFloat() * 3f); return VPatterns.boxes[rng.nextInt(VPatterns.boxes.size)] }
        // la spécificité du circuit revient régulièrement (tremplins, portiques laser)
        if (at >= nextTraitAt && (trait.ramps > 0f || trait.gates)) {
            nextTraitAt = at + v * (if (trait.gates) 6f else 5f / trait.ramps.coerceAtLeast(0.2f) * 0.4f + 4f)
            val list = if (trait.gates) VPatterns.gates else VPatterns.ramps
            val ok = list.filter { !it.needsJump || (at - lastJumpAt > v * 2.4f) }.filter { it.tier <= (tier + 1).coerceAtLeast(1) || trait.gates }
            if (ok.isNotEmpty()) return ok[rng.nextInt(ok.size)]
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
            val jumpOk = !p.needsJump || (diff > 0 && at - lastJumpAt > max(tune.floor, cruise) * 3.2f)
            val hazardOk = trait.hazards || p.rows.none { r -> r.any { it in "HLP" } }
            if (p !== lastPat && jumpOk && hazardOk) return p
        }
        return VPatterns.tiers[0][rng.nextInt(VPatterns.tiers[0].size)]
    }

    /**
     * Pose le motif [pat] à [d0] mètres devant le joueur. Les rangées sont espacées en SECONDES (tune.row) converties à la
     * vitesse de croisière [v] : le temps de réaction reste le même quand on va plus vite. Ce qui roule est posé là où
     * on le rejoindra au même instant que sa rangée (plus près pour ce qui roule dans notre sens, plus loin pour ce qui
     * arrive en face).
     */
    private fun place(pat: VPat, d0: Float, v: Float) {
        val mirror = rng.nextBoolean()
        val off = if (lanes > 3) rng.nextInt(lanes - 2) else 0
        val stepM = tune.row * v
        val ve = 6.5f + rng.nextFloat() * 1.5f                 // même vitesse pour tout le motif : les murs restent des murs
        val vo = 9f + rng.nextFloat() * 3f                     // vitesse de ce qui arrive en face
        for ((r, row) in pat.rows.withIndex()) {
            val d = d0 + r * stepM
            var crosser = false
            for (c in 0..2) {
                var ch = row[if (mirror) 2 - c else c]
                if (ch == '.') continue
                // Moyen / Difficile : une part des voitures « à percuter » arrive en face ou devient un camion, tant qu'il reste deux passages dans la rangée
                if (ch == 'C' && diff > 0 && row.count { it !in "XWHL" } >= 2) { val k = rng.nextFloat(); if (k < 0.35f * diff) ch = 'X' else if (k < 0.45f * diff) ch = 'W' }
                val ln = off + c
                // spécificités de circuit : pas de trafic en face sous l'océan, plus de voitures en ville…
                if (ch == 'X' && rng.nextFloat() >= trait.oncomingMul) ch = 'C'
                if (ch == 'C' && trait.trafficMul < 1f && rng.nextFloat() >= trait.trafficMul) continue
                if (ch == 'C' || ch == 'W') {
                    val kind = if (ch == 'W') (if (rng.nextBoolean()) 2 else 3) else listOf(0, 1, 4)[rng.nextInt(3)]
                    val e = VEnemy(ln, PLAYER_Z + d * (v - ve) / v, kind, ve)
                    e.heavy = ch == 'W'; e.ghost = trait.ghosts && rng.nextFloat() < 0.4f
                    enemies.add(e); continue
                }
                if (ch == 'X') {
                    val e = VEnemy(ln, PLAYER_Z + d * (v + vo) / v, rng.nextInt(ONCOMING_KEYS.size), -vo)
                    e.oncoming = true; e.heavy = e.kind == 2 || e.kind == 3
                    if (trait.boulders && rng.nextFloat() < 0.6f) { e.oncoming = false; e.boulder = true }
                    enemies.add(e); continue
                }
                val type = when (ch) {
                    'o' -> VItem.ACORN; 'O' -> VItem.MUSHROOM; 'B' -> VItem.BERRY
                    'T' -> VItem.TURBO; 'P' -> VItem.PUDDLE; 'H' -> VItem.HOLE; 'L' -> VItem.LOG
                    'R' -> VItem.RAMP; '?' -> VItem.ITEMBOX
                    'F' -> if (fuelOn) VItem.FUEL else VItem.MUSHROOM
                    'M' -> VItem.PASSENGER; '*' -> VItem.STAR; 'K' -> VItem.CANNON; 'S' -> VItem.SUPERBOOST
                    else -> VItem.ACORN
                }
                val p = VPick(ln, PLAYER_Z + d, type, rng.nextInt(3))
                // circuits à animaux / objets qui TRAVERSENT : le « tronc » devient un traverseur (un par rangée, ou un troupeau)
                if (type == VItem.LOG && theme.crossers.isNotEmpty() && rng.nextFloat() < 0.75f) {
                    if (crosser) continue
                    crosser = true
                    val dir = if (rng.nextBoolean()) 1 else -1
                    for (k in 0 until trait.herd) {
                        val q = if (k == 0) p else VPick(ln, PLAYER_Z + d + k * 5.5f, type, rng.nextInt(3))
                        q.fDir = dir; q.fLane = if (dir > 0) -0.9f - k * 0.5f else lanes - 0.1f + k * 0.5f
                        q.lane = Math.round(q.fLane)
                        if (k > 0) picks.add(q)
                    }
                }
                picks.add(p)
            }
        }
        // nuit pluvieuse : des flaques en plus
        if (trait.puddles > 0f) for ((r, row) in pat.rows.withIndex()) if (row.count { it == '.' } >= 2 && row.none { it in "HLWXR?" } && rng.nextFloat() < trait.puddles) {
            val free = (0..2).filter { row[it] == '.' }; picks.add(VPick(off + free[rng.nextInt(free.size)], PLAYER_Z + d0 + r * stepM, VItem.PUDDLE, 0))
        }
        // ville : du trafic en plus dans les cases vides
        if (trait.trafficMul > 1f) for ((r, row) in pat.rows.withIndex()) if (row == "..." && rng.nextFloat() < trait.trafficMul - 1f) {
            enemies.add(VEnemy(off + rng.nextInt(3), PLAYER_Z + (d0 + r * stepM) * (v - ve) / v, listOf(0, 1, 4)[rng.nextInt(3)], ve))
        }
        // route élargie (paysage) : un peu de vie dans les voies hors du motif
        if (lanes > 3) for (ln in 0 until lanes) {
            if (ln in off..off + 2) continue
            val r = rng.nextFloat(); val row = rng.nextInt(pat.rows.size); val d = d0 + row * stepM
            if (r < 0.35f) enemies.add(VEnemy(ln, PLAYER_Z + d * (v - ve) / v, listOf(0, 1, 4)[rng.nextInt(3)], ve))
            else if (r < 0.75f) for (k in 0..2) picks.add(VPick(ln, PLAYER_Z + d + k * stepM, if (k == 2) VItem.MUSHROOM else VItem.ACORN, rng.nextInt(3)))
        }
    }

    // -----------------------------------------------------------------------
    // Événements de jeu
    // -----------------------------------------------------------------------

    private fun grantRocketStart() {
        rocketStart = true
        turboT = max(turboT, 2.6f); gainFlow(0.35f); gainBoost(0.6f); shake = max(shake, 10f)
        ev("rocketStart")   // (le message remplace le « GO! » à l'écran : voir CountdownOverlay)
    }

    private fun addScore(n: Int, x: Float, z: Float, col: Color) {
        val v = n * mult * (if (passengerKind >= 0) 2 else 1)
        score += v
        // les gains qui s'enchaînent (pièces en file, takedowns en série) alimentent le MÊME compteur, qui grossit
        val last = lastGain
        if (last != null && last.life > 0.45f) {
            last.gain += v; last.text = "+${last.gain}"; last.life = 0.9f; last.color = col
            last.x = x; last.y = 2.2f; last.z = z; last.pop = 1f
        } else lastGain = VText(x, 2.2f, z, "+$v", 0.9f, col).also { it.gain = v; texts.add(it) }
    }
    private var lastGain: VText? = null

    private fun nearMiss(e: VEnemy) {
        nearCount++
        chain(2); gainFlow(0.10f); gainBoost(if (e.oncoming || e.boulder) 0.40f else 0.25f)
        addScore(if (e.oncoming) 50 else 30, laneX(e.lane), PLAYER_Z + 2f, V_PINK)
        texts.add(VText(lx * LANE_M, 3.6f, PLAYER_Z, nearLabel, 1.0f, V_PINK, big = true))
        ev("near")
    }

    private fun superBoost(x: Float, z: Float) {
        superT = 6f; turboT = max(turboT, 6f); flow = 1f; boost = boostSeg.toFloat()
        ev("superboost"); texts.add(VText(x, 3.0f, z, superLabel, 1.6f, V_GOLD, big = true)); burst(x, z, 1f, 0.85f, 0.2f, 30); shake = max(shake, 12f); invuln = max(invuln, 6f)
    }

    private fun coin(n: Int, flowGain: Float, x: Float, z: Float, evName: String, r: Float, g: Float, b: Float) {
        chain(); gainFlow(flowGain); gainBoost(0.004f * n / 10f)
        addScore(n * trait.coinMul, x, z, V_GOLD); ev(evName); burst(x, z, r, g, b, 6)
    }

    private fun collect(p: VPick) {
        val x = if (p.magnet) lx * LANE_M else laneX(p.lane); val z = p.z
        when (p.type) {
            VItem.FUEL -> { fuel = min(100f, fuel + tune.can); ev("fuel"); burst(x, z, 1f, 0.25f, 0.25f, 10); texts.add(VText(x, 2.2f, z, "+${tune.can.toInt()}", 0.9f, V_GREEN)) }
            VItem.STAR -> { startGiant(); addScore(50, x, z, V_GOLD); burst(x, z, 1f, 0.82f, 0.3f, 24) }
            VItem.CANNON -> { cannonT = 8f; cannonCd = 0f; ev("cannon"); texts.add(VText(x, 2.6f, z, cannonLabel, 1.2f, V_CYAN, big = true)); burst(x, z, 0.3f, 0.85f, 1f, 16) }
            // chaque pièce prolonge la série, donne un peu d'élan et un rien de boost : suivre une ligne de pièces, c'est accélérer
            VItem.ACORN -> coin(10, 0.008f, x, z, "pick1", 0.75f, 0.5f, 0.25f)
            VItem.MUSHROOM -> coin(20, 0.012f, x, z, "pick2", 1f, 0.25f, 0.25f)
            VItem.BERRY -> coin(30, 0.02f, x, z, "pick3", 0.42f, 0.49f, 1f)
            VItem.TURBO -> { turboT = max(turboT, 2.2f); gainFlow(0.12f); gainBoost(0.25f); ev("turbo"); texts.add(VText(x, 2.2f, z, "TURBO!", 1.0f, V_CYAN)); shake = max(shake, 6f) }
            VItem.SUPERBOOST -> superBoost(x, z)
            VItem.ITEMBOX -> { openBox(); gainBoost(0.10f); burst(x, z, 1f, 0.9f, 0.4f, 16); ev("box") }
            // tremplin : grand saut gratuit (plus long), sans pause derrière
            VItem.RAMP -> if (!jumping) { jumpT = 0f; JUMP_DUR = baseJump * 1.3f; trickDone = false; rampJump = true; gainBoost(0.15f); ev("ramp") }
            VItem.HOLE, VItem.LOG -> {
                if (airborne || jumping) { chain(); gainFlow(0.05f); gainBoost(0.15f); addScore(20, x, z, V_CYAN); texts.add(VText(x, 3.2f, z, jumpedLabel, 1.0f, V_CYAN, big = true)); ev("trick") }
                else if (giant > 0 || superT > 0f) { burst(x, z, 0.6f, 0.4f, 0.25f, 12); ev("crush") }
                else if (invuln <= 0f) {
                    if (shieldT > 0f) { popShield(); return }
                    // tombé dans le trou / heurté l'obstacle : gros cahot, l'élan retombe (et de l'essence en Arcade)
                    bumpCount++
                    if (fuelOn && diff != 0) fuel = max(0f, fuel - (if (diff == 2) 25f else 15f))
                    breakChain(); loseFlow(tune.bumpKeep)
                    if (diff != 0 && boostSeg > 1) { boostSeg--; segProgress = 0 }
                    boost = min(boost, boostSeg.toFloat()) * (if (diff == 0) 1f else 0.5f)
                    invuln = 1.2f; shake = max(shake, 14f); slideT = 0.5f; slideDir = if (Random.nextBoolean()) 1 else -1
                    burst(x, z, 0.5f, 0.4f, 0.3f, 14); ev("bump")
                }
            }
            VItem.PUDDLE -> if (giant == 0 && superT <= 0f) {
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
    /** Confettis : petits carrés de couleur qui tombent doucement sur la route, devant nous. */
    private fun confetti(n: Int) {
        val half = roadHalfFor(lanes) + 3f
        repeat(n) {
            if (parts.size > 280) return
            val c = rainbow(Random.nextFloat())
            val z = PLAYER_Z - 4f + Random.nextFloat() * 46f
            parts.add(VPart((Random.nextFloat() - 0.5f) * 2f * half, groundY(dist + z) + 5f + Random.nextFloat() * 5f, z, (Random.nextFloat() - 0.5f) * 2.5f, -1.6f - Random.nextFloat() * 1.8f,
                2.4f, 2.4f, 0.55f + 0.45f * c.first, 0.55f + 0.45f * c.second, 0.55f + 0.45f * c.third, 0.34f, floaty = true))
        }
    }
    /** Feu d'artifice : une gerbe de couleur qui éclate haut dans le ciel, au-dessus de l'aire d'arrivée. */
    private fun firework() {
        if (parts.size > 260) return
        val c = rainbow(Random.nextFloat())
        val z = PLAYER_Z + 22f + Random.nextFloat() * 40f
        val x = (Random.nextFloat() - 0.5f) * 30f; val y = groundY(dist + z) + 11f + Random.nextFloat() * 7f
        for (i in 0 until 16) { val a = i * 0.3927f; val v = 5f + Random.nextFloat() * 3f
            parts.add(VPart(x, y, z, cos(a) * v, sin(a) * v + 2f, 0.9f, 0.9f, 0.6f + 0.4f * c.first, 0.6f + 0.4f * c.second, 0.6f + 0.4f * c.third, 0.55f)) }
        ev("firework")
    }

    private fun burst(x: Float, z: Float, r: Float, g: Float, b: Float, n: Int) {
        if (parts.size > 170) return
        val gy = groundY(dist + z) + 1f
        repeat(n) {
            val a = Random.nextFloat() * 2f * PI.toFloat(); val s = 2f + Random.nextFloat() * 4f
            parts.add(VPart(x, gy, z, cos(a) * s, abs(sin(a)) * s + 2f, 0.5f + Random.nextFloat() * 0.4f, 0.9f, r, g, b, 0.18f + Random.nextFloat() * 0.2f))
        }
    }
}
