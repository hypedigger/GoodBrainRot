package com.goodbrainrot.app.ui.garden

import com.goodbrainrot.app.R
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

// ===========================================================================
// Jardinier Zen — modèle de jeu 3D : branches (cylindres), feuillage (cartes),
// découpe au swipe (distance écran segment/segment), débris qui tombent,
// 3 modes (Topiaire / Rush / Tranche), outils à 2 filières.
// Tout le monde est en mètres ; la mise à jour et le rendu tournent sur le thread GL.
// ===========================================================================

internal enum class GMode { ZEN, RUSH, SLICE }

/** Outil : bande de coupe (fraction de la largeur d'écran), puissance (rayon max coupé net, m), électrique = coupe continue. */
internal data class GTool(val id: Int, val electric: Boolean, val tier: Int, val nameRes: Int, val cost: Int,
                          val band: Float, val power: Float, val r: Float, val g: Float, val b: Float)
internal val TOOLS: List<GTool> = listOf(
    GTool(0, false, 0, R.string.gz_t_m0, 0,    0.030f, 0.05f,  0.85f, 0.85f, 0.9f),
    GTool(1, false, 1, R.string.gz_t_m1, 60,   0.036f, 0.07f,  0.9f, 0.3f, 0.3f),
    GTool(2, false, 2, R.string.gz_t_m2, 150,  0.044f, 0.10f,  0.95f, 0.55f, 0.15f),
    GTool(3, false, 3, R.string.gz_t_m3, 320,  0.060f, 0.18f,  0.3f, 0.7f, 0.35f),
    GTool(4, false, 4, R.string.gz_t_m4, 600,  0.082f, 0.45f,  0.35f, 0.55f, 0.85f),
    GTool(5, false, 5, R.string.gz_t_m5, 1000, 0.110f, 9f,     0.6f, 0.45f, 0.3f),
    GTool(10, true, 0, R.string.gz_t_e0, 120,  0.038f, 0.07f,  0.25f, 0.25f, 0.3f),
    GTool(11, true, 1, R.string.gz_t_e1, 220,  0.046f, 0.12f,  0.2f, 0.6f, 0.3f),
    GTool(12, true, 2, R.string.gz_t_e2, 400,  0.062f, 0.25f,  0.95f, 0.6f, 0.1f),
    GTool(13, true, 3, R.string.gz_t_e3, 700,  0.078f, 0.6f,   0.3f, 0.9f, 1f),
    GTool(14, true, 4, R.string.gz_t_e4, 1100, 0.098f, 1.5f,   0.9f, 0.25f, 0.2f),
    GTool(15, true, 5, R.string.gz_t_e5, 1800, 0.170f, 9f,     0.4f, 1f, 0.35f),
)
internal fun toolById(id: Int): GTool = TOOLS.firstOrNull { it.id == id } ?: TOOLS[0]

internal class Branch(val a: Vec3, var b: Vec3, val r: Float, val leafKind: Int, val parent: Branch?) {
    var alive = true
    var hp = 1f
    var outside = false
    val children = ArrayList<Branch>()
    var flower = false
    var nest = false
    var leafSize = 0.8f
    var structural = false   // tronc / charpente : pas coupable
    // Rush : pousse qui JAILLIT de la haie et grandit (grow 0→1 vers tip), puis reste pleine (full) avant d'être ratée
    var tip: Vec3? = null
    var grow = 0f
    var full = 0f
}
/** Touffe de feuillage du corps d'une plante taillée (Topiaire). */
internal class Tuft(val pos: Vec3, val kind: Int, val size: Float, val flower: Boolean, val shade: Float)
internal class Debris(var pos: Vec3, var vel: Vec3, val len: Float, val r: Float, val leafKind: Int, var dirX: Float, var dirY: Float, var dirZ: Float) {
    var life = 2.6f; var spin = 0f; var spinV = (Random.nextFloat() - 0.5f) * 8f
}
internal class LeafBurst(var pos: Vec3, var vel: Vec3, val kind: Int, var life: Float, val size: Float, val chip: Boolean = false)
internal class Bee(var pos: Vec3, var vel: Vec3, var t: Float = 0f)
internal class Flying(var pos: Vec3, var vel: Vec3, val len: Float, val r: Float, val leafKind: Int, var rot: Float, var rotV: Float, val bee: Boolean = false, val nest: Boolean = false,
                      val bamboo: Boolean = false, val cuts: Int = 0)   // cuts = nombre de coupes déjà subies (morceau re-tranchable)
/** Papillon / oiseau qui anime le fond (Topiaire). */
internal class Critter(var pos: Vec3, val bird: Boolean, var t: Float, val speed: Float, val radius: Float, val baseY: Float)
/** Texte flottant (monde) projeté à l'écran par le renderer. */
internal class GText(val pos: Vec3, val text: String, var life: Float, val r: Float, val g: Float, val b: Float, val big: Boolean = false) {
    @Volatile var sx = -1000f; @Volatile var sy = -1000f
}

/** [easy] (Tranche) : tout se coupe et rapporte, même les abeilles et les nids ; sinon mode classique : 3 vies, une abeille = une vie. */
internal class GardenGame(val mode: GMode, val tool: GTool, val seedDay: Int, val labels: Map<String, String> = emptyMap(), val easy: Boolean = false) {
    var lives = 3
    val DURATION = if (mode == GMode.RUSH) 180f else 60f   // Topiaire et Tranche : 1 minute (au-delà on s'ennuie)
    var timeLeft = DURATION
    var elapsed = 0f
    var score = 0; var combo = 0; var comboTimer = 0f
    val mult: Int get() = (1 + combo / 5).coerceAtMost(6)
    var over = false
    var shake = 0f
    var flash = 0f
    var timeScale = 1f            // ralenti (Tranche : triple coupe)
    // topiaire
    var totalOutside = 0; var cutOutside = 0; var cutInside = 0
    val precision: Int get() = if (totalOutside == 0) 100 else ((cutOutside * 100f / totalOutside) - cutInside * 5f).toInt().coerceIn(0, 100)
    var turntable = 0f
    var manualRotT = -10f            // dernier moment où le joueur a tourné la plante au doigt
    fun rotateBy(deg: Float) { turntable += deg; manualRotT = elapsed }
    var shapeKind = seedDay % 7
    var bushesDone = 0
    private var nextBushT = -1f
    var bushScale = 0f            // pop-in du nouveau buisson
    // rush
    var camZ = 0f; var speed = 5.5f
    var misses = 0
    var walkT = 0f
    // tranche
    val flying = ArrayList<Flying>()
    private var launchCd = 1.2f
    var lastHitCuts = 0           // pour le HUD : nombre de morceaux de la dernière coupe
    // vie du fond (Topiaire) : papillons, oiseaux, feuilles qui tombent
    val critters = ArrayList<Critter>()
    private var fallLeafCd = 1.5f

    val branches = ArrayList<Branch>()
    val tufts = ArrayList<Tuft>()      // (déclaré AVANT init : buildBush l'utilise)
    val debris = ArrayList<Debris>()
    val leaves = ArrayList<LeafBurst>()
    val bees = ArrayList<Bee>()
    val texts = ArrayList<GText>()
    @Volatile var textSnap: List<GText> = emptyList()
    val events = ConcurrentLinkedQueue<String>()
    val swipe = ConcurrentLinkedQueue<FloatArray>()
    private val trail = ArrayList<FloatArray>()
    @Volatile var fingerDown = false
    @Volatile var fingerX = -1f; @Volatile var fingerY = -1f
    private fun ev(s: String) { events.add(s) }
    private fun label(k: String, def: String) = labels[k] ?: def

    init {
        when (mode) {
            GMode.ZEN -> {
                buildBush(Random(seedDay * 31 + 7)); bushScale = 1f
                for (i in 0 until 4) critters.add(Critter(Vec3(0f, 1.5f + i * 0.4f, 0f), bird = false, t = i * 1.7f, speed = 0.35f + i * 0.08f, radius = 2.6f + i * 0.5f, baseY = 1.4f + i * 0.35f))
                critters.add(Critter(Vec3(-30f, 9f, -20f), bird = true, t = 0f, speed = 6f, radius = 0f, baseY = 9f))
            }
            GMode.RUSH -> { }
            GMode.SLICE -> { }
        }
    }

    // --------------------------------------------------------------- génération (Topiaire)
    // Une VRAIE plante taillée : un corps dense de feuillage qui a déjà la forme (boule, cube, cône,
    // statue de lapin) et, qui dépassent nettement de la surface, des POUSSES fines et mortes (orange)
    // à couper. Le corps n'est pas coupable : ce qui se coupe, c'est ce qui dépasse.
    private class Prim(val kind: Int, val c: Vec3, val r: Float, val c2: Vec3 = c, val hx: Float = 0f, val hy: Float = 0f, val hz: Float = 0f, val r0: Float = 0f, val y1: Float = 0f)
    private fun prims(kind: Int): List<Prim> = when (kind) {
        0 -> listOf(Prim(0, Vec3(0f, 1.95f, 0f), 1.15f))                                         // boule
        1 -> listOf(Prim(1, Vec3(0f, 1.8f, 0f), 0f, hx = 1.0f, hy = 1.0f, hz = 1.0f))               // cube
        2 -> listOf(Prim(2, Vec3(0f, 0.7f, 0f), 0f, r0 = 1.3f, y1 = 3.5f))                          // cône
        3 -> listOf(Prim(0, Vec3(0f, 1.35f, 0f), 0.95f), Prim(0, Vec3(0f, 2.5f, 0.15f), 0.58f),  // lapin : corps, tête
                    Prim(3, Vec3(-0.27f, 2.95f, 0.1f), 0.17f, c2 = Vec3(-0.38f, 3.75f, 0.05f)), Prim(3, Vec3(0.27f, 2.95f, 0.1f), 0.17f, c2 = Vec3(0.38f, 3.75f, 0.05f)),  // oreilles
                    Prim(0, Vec3(0f, 1.5f, -0.9f), 0.27f))                                     // queue
        4 -> {  // étoile à 5 branches (dans le plan vertical : elle tourne avec la plante)
            val c = Vec3(0f, 2.25f, 0f)
            listOf(Prim(0, c, 0.55f)) + (0 until 5).map { i ->
                val a = Math.toRadians(90.0 + i * 72.0)
                Prim(3, c, 0.34f, c2 = Vec3(cos(a).toFloat() * 1.2f, 2.25f + sin(a).toFloat() * 1.2f, 0f))
            }
        }
        5 -> listOf(Prim(0, Vec3(-0.44f, 2.8f, 0f), 0.6f), Prim(0, Vec3(0.44f, 2.8f, 0f), 0.6f),   // cœur : 2 lobes
                    Prim(3, Vec3(-0.4f, 2.7f, 0f), 0.45f, c2 = Vec3(0f, 1.5f, 0f)), Prim(3, Vec3(0.4f, 2.7f, 0f), 0.45f, c2 = Vec3(0f, 1.5f, 0f)))  // pointe
        else -> listOf(Prim(0, Vec3(0f, 1.5f, 0f), 0.8f), Prim(0, Vec3(0f, 2.55f, 0.4f), 0.48f),   // canard : corps, tête
                       Prim(3, Vec3(0f, 2.5f, 0.75f), 0.16f, c2 = Vec3(0f, 2.4f, 1.2f)),           // bec
                       Prim(3, Vec3(0f, 1.75f, -0.7f), 0.26f, c2 = Vec3(0f, 2.15f, -1.1f)))        // queue relevée
    }
    private fun trunkTop(kind: Int) = when (kind) { 0 -> 0.9f; 1 -> 0.85f; 2 -> 0.75f; 3 -> 0.5f; 4 -> 1.6f; 5 -> 1.4f; else -> 0.7f }
    private fun insidePrim(p: Prim, q: Vec3): Boolean = when (p.kind) {
        0 -> (q - p.c).len() < p.r
        1 -> abs(q.x - p.c.x) < p.hx && abs(q.y - p.c.y) < p.hy && abs(q.z - p.c.z) < p.hz
        2 -> { val k = ((p.y1 - q.y) / (p.y1 - p.c.y)).coerceIn(0f, 1f); q.y in p.c.y..p.y1 && sqrt(q.x * q.x + q.z * q.z) < p.r0 * k }
        else -> { val ab = p.c2 - p.c; val t = (((q - p.c).x * ab.x + (q - p.c).y * ab.y + (q - p.c).z * ab.z) / (ab.len() * ab.len())).coerceIn(0f, 1f); (q - (p.c + ab * t)).len() < p.r }
    }
    /** Point sur la surface d'une primitive + normale sortante. */
    private fun surface(p: Prim, rnd: Random): Pair<Vec3, Vec3> {
        fun rdir(): Vec3 { while (true) { val v = Vec3(rnd.nextFloat() * 2 - 1, rnd.nextFloat() * 2 - 1, rnd.nextFloat() * 2 - 1); val l = v.len(); if (l in 0.15f..1f) return v * (1f / l) } }
        return when (p.kind) {
            0 -> { val d = rdir(); Pair(p.c + d * p.r, d) }
            1 -> {
                val f = rnd.nextInt(5)   // pas la face du dessous
                val u = rnd.nextFloat() * 2 - 1; val v = rnd.nextFloat() * 2 - 1
                when (f) {
                    0 -> Pair(Vec3(p.c.x + p.hx, p.c.y + v * p.hy, p.c.z + u * p.hz), Vec3(1f, 0f, 0f))
                    1 -> Pair(Vec3(p.c.x - p.hx, p.c.y + v * p.hy, p.c.z + u * p.hz), Vec3(-1f, 0f, 0f))
                    2 -> Pair(Vec3(p.c.x + u * p.hx, p.c.y + v * p.hy, p.c.z + p.hz), Vec3(0f, 0f, 1f))
                    3 -> Pair(Vec3(p.c.x + u * p.hx, p.c.y + v * p.hy, p.c.z - p.hz), Vec3(0f, 0f, -1f))
                    else -> Pair(Vec3(p.c.x + u * p.hx, p.c.y + p.hy, p.c.z + v * p.hz), Vec3(0f, 1f, 0f))
                }
            }
            2 -> { val t = 1f - sqrt(rnd.nextFloat()); val a = rnd.nextFloat() * 6.2832f; val h = p.y1 - p.c.y
                   val rr = p.r0 * (1f - t); Pair(Vec3(cos(a) * rr, p.c.y + t * h, sin(a) * rr), Vec3(cos(a) * h, p.r0, sin(a) * h).norm()) }
            else -> { val ab = p.c2 - p.c; val t = rnd.nextFloat(); val axis = ab.norm()
                      val ref = if (abs(axis.y) < 0.9f) Vec3(0f, 1f, 0f) else Vec3(1f, 0f, 0f)
                      val u = Vec3(axis.y * ref.z - axis.z * ref.y, axis.z * ref.x - axis.x * ref.z, axis.x * ref.y - axis.y * ref.x).norm()
                      val v = Vec3(axis.y * u.z - axis.z * u.y, axis.z * u.x - axis.x * u.z, axis.x * u.y - axis.y * u.x)
                      val a = rnd.nextFloat() * 6.2832f; val n = (u * cos(a) + v * sin(a)).norm()
                      Pair(p.c + ab * t + n * p.r, n) }
        }
    }
    private fun buildBush(rnd: Random) {
        branches.clear(); tufts.clear(); totalOutside = 0; cutOutside = 0; cutInside = 0
        val ps = prims(shapeKind)
        val top = trunkTop(shapeKind)
        val trunk = Branch(Vec3(0f, 0f, 0f), Vec3(0f, top, 0f), 0.14f, -1, null); trunk.structural = true
        branches.add(trunk)
        // corps : touffes de feuilles sur la surface (union des primitives)
        val nT = if (shapeKind == 3 || shapeKind >= 4) 420 else 340   // formes composées : plus de touffes pour combler
        var made = 0; var guard = 0
        while (made < nT && guard++ < nT * 6) {
            val p = ps[rnd.nextInt(ps.size)]
            val (q, _) = surface(p, rnd)
            if (ps.any { it !== p && insidePrim(it, q) }) continue
            if (q.y < 0.35f) continue
            // fleurs : jamais sur les statues (lapin, canard) ; le cœur en a PLUS (roses)
            val flowerP = when (shapeKind) { 3, 6 -> 0f; 5 -> 0.22f; else -> 0.09f }
            tufts.add(Tuft(q, rnd.nextInt(2), 0.30f + rnd.nextFloat() * 0.14f, flower = rnd.nextFloat() < flowerP, shade = 0.86f + rnd.nextFloat() * 0.2f))
            made++
        }
        // pousses mortes qui DÉPASSENT : fines, 45-85 cm, orientées vers l'extérieur
        val nS = 20 + rnd.nextInt(7)
        made = 0; guard = 0
        while (made < nS && guard++ < nS * 8) {
            val p = ps[rnd.nextInt(ps.size)]
            val (q, n) = surface(p, rnd)
            if (ps.any { it !== p && insidePrim(it, q) } || q.y < 0.6f) continue
            if (branches.any { !it.structural && (it.a - q).len() < 0.42f }) continue      // pas deux pousses collées
            val dir = (n + Vec3((rnd.nextFloat() - 0.5f) * 0.5f, 0.15f + (rnd.nextFloat() - 0.5f) * 0.4f, (rnd.nextFloat() - 0.5f) * 0.5f)).norm()
            val len = 0.5f + rnd.nextFloat() * 0.45f
            val a = q - n * 0.12f
            val s = Branch(a, a + dir * len, 0.016f + rnd.nextFloat() * 0.012f, rnd.nextInt(2), null)   // fines (3 à 5 cm)
            s.outside = true; s.leafSize = 0.20f + rnd.nextFloat() * 0.08f
            branches.add(s); totalOutside++
            if (rnd.nextFloat() < 0.45f) {   // brindille secondaire
                val at = s.a.lerp(s.b, 0.55f + rnd.nextFloat() * 0.25f)
                val d2 = (dir + Vec3((rnd.nextFloat() - 0.5f) * 1.2f, 0.3f, (rnd.nextFloat() - 0.5f) * 1.2f)).norm()
                val c = Branch(at, at + d2 * (0.22f + rnd.nextFloat() * 0.15f), s.r * 0.7f, rnd.nextInt(2), s)
                c.outside = true; c.leafSize = 0.17f; s.children.add(c); branches.add(c); totalOutside++
            }
            made++
        }
    }
    val outsideLeft: Int get() = totalOutside - cutOutside

    // Rush = haie FRONTALE (façon « tape-taupe ») : des pousses jaillissent de la haie face à toi et grandissent ;
    // coupe-les avant qu'elles ne soient pleines (raté sinon), évite les nids. Le rythme monte avec le temps.
    private var rushSpawnCd = 0.6f
    var lastCutT = -10f
    private fun spawnRushShoot() {
        val x = -1.3f + Random.nextFloat() * 2.6f
        val y = 0.8f + Random.nextFloat() * 3.0f
        if (branches.any { !it.structural && abs(it.a.x - x) < 0.6f && abs(it.a.y - y) < 0.6f }) return
        val dir = Vec3((Random.nextFloat() - 0.5f) * 1.3f, 0.2f + Random.nextFloat() * 0.6f, 1f).norm()
        val len = 0.9f + Random.nextFloat() * 0.5f
        val s = Branch(Vec3(x, y, 0.04f), Vec3(x, y, 0.06f), 0.05f + Random.nextFloat() * 0.025f, Random.nextInt(2), null)
        s.tip = s.a + dir * len
        s.nest = Random.nextFloat() < 0.12f
        s.flower = !s.nest && Random.nextFloat() < 0.25f
        s.leafSize = if (s.nest) 0.6f else 0.55f
        branches.add(s)
    }

    private fun launchFlying() {
        val x = (Random.nextFloat() - 0.5f) * 6.5f
        val vx = -x * 0.3f + (Random.nextFloat() - 0.5f) * 1.2f
        val vy = 9.0f + Random.nextFloat() * 2.2f          // apogée 4,3 à 6,6 m : haut de l'écran SANS passer sous le HUD
        val isBee = Random.nextFloat() < 0.14f
        val bamboo = !isBee && Random.nextFloat() < 0.28f
        // bambou : plus long, plus fin, tourne moins vite (plus facile à re-trancher en 3-4 morceaux)
        flying.add(Flying(Vec3(x, -1.5f, 0f), Vec3(vx, vy + (if (bamboo) 0.8f else 0f), 0f),
            if (bamboo) 1.6f + Random.nextFloat() * 0.8f else 0.9f + Random.nextFloat() * 1.0f, if (bamboo) 0.06f else 0.06f + Random.nextFloat() * 0.08f,
            Random.nextInt(2), Random.nextFloat() * 360f, (Random.nextFloat() - 0.5f) * (if (bamboo) 120f else 240f), bee = isBee, nest = !isBee && !bamboo && Random.nextFloat() < 0.08f, bamboo = bamboo))
    }

    // --------------------------------------------------------------- mise à jour
    fun update(dt0: Float, r: GardenRenderer) {
        if (over) return
        if (timeScale < 1f) timeScale = min(1f, timeScale + dt0 * 1.6f)
        val dt = dt0 * timeScale
        elapsed += dt0
        timeLeft -= dt0
        if (shake > 0f) shake = max(0f, shake - dt0 * 40f)
        if (flash > 0f) flash = max(0f, flash - dt0 * 3f)
        if (comboTimer > 0f) { comboTimer -= dt0; if (comboTimer <= 0f) combo = 0 }
        when (mode) {
            GMode.ZEN -> {
                // rotation auto SEULEMENT si le joueur n'a pas tourné la plante récemment (glisser en bas de l'écran)
                if (elapsed - manualRotT > 4f) turntable += dt * 9f
                if (bushScale < 1f) bushScale = min(1f, bushScale + dt * 2.2f)
                // vie du fond : papillons qui voltigent autour du buisson, oiseau qui traverse, feuilles qui tombent
                for (c in critters) {
                    c.t += dt * c.speed
                    if (c.bird) { c.pos.x += dt * c.speed; c.pos.y = c.baseY + sin(c.t * 2f) * 0.8f; if (c.pos.x > 32f) { c.pos.x = -32f; c.pos.z = -14f - Random.nextFloat() * 12f } }
                    else { c.pos.x = cos(c.t) * c.radius; c.pos.z = sin(c.t) * c.radius; c.pos.y = c.baseY + sin(c.t * 5f) * 0.25f + cos(c.t * 3.3f) * 0.15f }
                }
                fallLeafCd -= dt
                if (fallLeafCd <= 0f && leaves.size < 60) {
                    fallLeafCd = 0.9f + Random.nextFloat() * 1.4f
                    val a = Random.nextFloat() * 6.28f; val d = 8f + Random.nextFloat() * 8f
                    leaves.add(LeafBurst(Vec3(cos(a) * d, 5f + Random.nextFloat() * 3f, sin(a) * d), Vec3((Random.nextFloat() - 0.5f) * 0.8f, 0.6f, (Random.nextFloat() - 0.5f) * 0.8f), Random.nextInt(2), 6f, 0.28f))
                }
                if (nextBushT >= 0f) {
                    nextBushT -= dt
                    if (nextBushT <= 0f) {
                        nextBushT = -1f; bushesDone++
                        shapeKind = (shapeKind + 1) % 7
                        buildBush(Random(seedDay * 31 + 7 + bushesDone * 101)); bushScale = 0.05f
                        ev("newbush")
                    }
                } else if (totalOutside > 0 && cutOutside >= (totalOutside * 0.9f).toInt()) {   // 90 % du mort suffit : on enchaîne
                    val bonus = 100 + (timeLeft * 1.5f).toInt()
                    score += bonus
                    texts.add(GText(Vec3(0f, 3.6f, 0f), label("bushdone", "Parfait !") + "  +$bonus", 1.8f, 1f, 0.85f, 0.3f, big = true))
                    ev("done"); nextBushT = 1.4f
                }
            }
            GMode.RUSH -> {
                speed = min(2.3f, 1f + elapsed * 0.0072f)           // facteur de rythme x1 → x2.3 en 3 min
                rushSpawnCd -= dt
                val alive = branches.count { it.alive && !it.structural }
                if (rushSpawnCd <= 0f && alive < 7) { rushSpawnCd = 1.05f / speed; spawnRushShoot() }
                val it = branches.iterator()
                while (it.hasNext()) {
                    val b = it.next()
                    if (b.structural) continue
                    if (!b.alive) { b.full += dt; if (b.full > 0.5f) it.remove(); continue }   // souche coupée : disparaît vite
                    val tip = b.tip ?: continue
                    if (b.grow < 1f) { b.grow = min(1f, b.grow + dt * speed / 2.4f); b.b = b.a.lerp(tip, b.grow) }
                    else {
                        b.full += dt
                        if (b.full > 1.0f) {   // trop tard : la pousse se rétracte, c'est un raté (sauf pour un nid)
                            if (!b.nest) { misses++; combo = 0; flash = 0.45f; ev("miss"); texts.add(GText(tip.copy(), label("missed", "Raté !"), 0.9f, 1f, 0.5f, 0.45f)) }
                            it.remove()
                        }
                    }
                }
            }
            GMode.SLICE -> {
                launchCd -= dt
                if (launchCd <= 0f) { launchCd = max(0.5f, 1.3f - elapsed * 0.004f); launchFlying(); if (Random.nextFloat() < 0.35f) launchFlying(); if (elapsed > 40f && Random.nextFloat() < 0.25f) launchFlying() }
                val it = flying.iterator()
                while (it.hasNext()) {
                    val f = it.next()
                    f.vel.y -= 9.5f * dt; f.pos.x += f.vel.x * dt; f.pos.y += f.vel.y * dt; f.rot += f.rotV * dt
                    if (f.pos.y < -3.5f) { if (!f.bee && !f.nest && f.cuts == 0) combo = 0; it.remove() }
                }
            }
        }
        val dit = debris.iterator()
        while (dit.hasNext()) {
            val d = dit.next(); d.life -= dt
            d.vel.y -= 9.5f * dt; d.pos.x += d.vel.x * dt; d.pos.y += d.vel.y * dt; d.pos.z += d.vel.z * dt; d.spin += d.spinV * dt
            if (d.pos.y < -0.3f) { d.pos.y = -0.3f; d.vel.y = -d.vel.y * 0.25f; d.vel.x *= 0.6f; d.vel.z *= 0.6f; d.spinV *= 0.5f }
            if (d.life <= 0f) dit.remove()
        }
        val lit = leaves.iterator()
        while (lit.hasNext()) {
            val l = lit.next(); l.life -= dt
            l.vel.y -= (if (l.chip) 9f else 2.5f) * dt; l.vel.x *= 0.98f; l.pos.x += l.vel.x * dt; l.pos.y += l.vel.y * dt; l.pos.z += l.vel.z * dt
            if (l.life <= 0f) lit.remove()
        }
        val bit = bees.iterator()
        while (bit.hasNext()) {
            val b = bit.next(); b.t += dt
            b.pos.x += b.vel.x * dt; b.pos.y += (b.vel.y + sin(b.t * 9f) * 0.8f) * dt; b.pos.z += b.vel.z * dt
            if (b.t > 4f) bit.remove()
        }
        val tit = texts.iterator()
        while (tit.hasNext()) { val t = tit.next(); t.life -= dt0; t.pos.y += 0.6f * dt0; if (t.life <= 0f) tit.remove() }
        textSnap = if (texts.isEmpty()) emptyList() else ArrayList(texts)
        processSwipe(r)
        if (timeLeft <= 0f) { timeLeft = 0f; over = true; ev("timeup") }
    }

    // --------------------------------------------------------------- découpe
    private val pa = FloatArray(2); private val pb = FloatArray(2)
    private fun processSwipe(r: GardenRenderer) {
        var hits = 0
        while (true) {
            val pt = swipe.poll() ?: break
            if (pt[2] < 0.5f) { trail.clear(); continue }
            trail.add(pt)
            if (trail.size < 2) continue
            val p0 = trail[trail.size - 2]; val p1 = trail[trail.size - 1]
            val band = tool.band * r.viewW
            hits += cutWith(p0[0], p0[1], p1[0], p1[1], band, r)
            if (trail.size > 24) trail.removeAt(0)
        }
        if (hits > 0 && mode != GMode.ZEN) { combo += hits; comboTimer = 2.2f }
        if (hits >= 3 && mode == GMode.SLICE) { timeScale = 0.3f; ev("multi"); texts.add(GText(Vec3(0f, 3.2f, 0f), label("combo", "COMBO") + " x$hits", 1.2f, 1f, 0.85f, 0.3f, big = true)) }
        if (hits > 0) lastHitCuts = hits
    }

    private fun cutWith(x0: Float, y0: Float, x1: Float, y1: Float, band: Float, r: GardenRenderer): Int {
        var n = 0
        val camZ0 = 0f
        val snapshot = ArrayList(branches)
        for (b in snapshot) {
            if (!b.alive) continue
            if (mode == GMode.ZEN && (b.structural || bushScale < 1f)) continue
            if (mode == GMode.RUSH && (b.structural || b.grow < 0.2f)) continue
            val aw = worldOf(b.a); val bw = worldOf(b.b)
            if (!r.project(aw, pa) || !r.project(bw, pb)) continue
            // Rush : la pousse pointe vers la caméra (segment court à l'écran) -> zone de coupe élargie autour de la touffe
            val extra = if (mode == GMode.RUSH) r.viewW * 0.08f else b.r * 400f / max(1f, aw.z - camZ0 + 1f)
            val t = segSegParam(x0, y0, x1, y1, pa[0], pa[1], pb[0], pb[1], band + extra)
            if (t < 0f) continue
            if (tool.power >= b.r || tool.electric && tool.power * 1.6f >= b.r) { cutBranch(b, t.coerceIn(0.1f, 0.9f)); n++ }
            else {
                b.hp -= (tool.power / b.r) * 0.55f
                ev("chew")
                if (b.hp <= 0f) { cutBranch(b, t.coerceIn(0.1f, 0.9f)); n++ }
            }
            if (!tool.electric && n > 0 && tool.tier < 4) break
        }
        if (mode == GMode.SLICE) {
            val fs = ArrayList(flying)
            for (f in fs) {
                val hx = cos(Math.toRadians(f.rot.toDouble())).toFloat() * f.len / 2f; val hy = sin(Math.toRadians(f.rot.toDouble())).toFloat() * f.len / 2f
                val a = Vec3(f.pos.x - hx, f.pos.y - hy, 0f); val b2 = Vec3(f.pos.x + hx, f.pos.y + hy, 0f)
                if (!r.project(a, pa) || !r.project(b2, pb)) continue
                val t = segSegParam(x0, y0, x1, y1, pa[0], pa[1], pb[0], pb[1], band + (if (f.bee) 30f else 14f))
                if (t < 0f) continue
                flying.remove(f)
                if (easy && (f.bee || f.nest)) {
                    // Facile : tout rapporte, même les abeilles et les nids
                    val pts = (if (f.bee) 20 else 30) * mult; score += pts; n++
                    texts.add(GText(f.pos.copy(), "+$pts " + (if (f.bee) "🐝" else "🍯"), 0.9f, 1f, 0.9f, 0.4f)); burstChips(f.pos, 5); ev("cut")
                }
                else if (f.bee) { lives--; combo = 0; flash = 0.9f; shake = 14f; ev("sting"); texts.add(GText(f.pos.copy(), "💔", 1.1f, 1f, 0.4f, 0.4f, big = true)); if (lives <= 0) { over = true; ev("dead") } }
                else if (f.nest) { score = max(0, score - 25); combo = 0; flash = 0.6f; ev("nest"); texts.add(GText(f.pos.copy(), "-25", 1f, 1f, 0.4f, 0.4f)); for (i in 0..3) bees.add(Bee(f.pos.copy(), Vec3((Random.nextFloat() - 0.5f) * 4f, 1f + Random.nextFloat(), -1f))) }
                else {
                    // points : 15 (bambou 20), et chaque re-coupe d'un morceau rapporte plus (x2, x3…) -> enchaîner vite paie
                    val pts = (if (f.bamboo) 20 else 15) * (f.cuts + 1) * mult
                    score += pts; n++
                    texts.add(GText(f.pos.copy(), if (f.cuts > 0) "+$pts ✂${f.cuts + 1}" else "+$pts", 0.9f, 1f, 0.95f, 0.5f))
                    val dir = Vec3(hx, hy, 0f).norm()
                    val cutAt = a.lerp(b2, t)
                    val half1 = (cutAt - a).len(); val half2 = (b2 - cutAt).len()
                    // les deux morceaux RESTENT EN VOL (re-tranchables) tant qu'ils sont assez longs, sinon tombent en débris
                    fun piece(center: Vec3, len: Float, push: Float) {
                        val v = Vec3(f.vel.x + dir.x * push + (Random.nextFloat() - 0.5f), max(2.5f, f.vel.y * 0.55f) + 2.2f, 0f)
                        if (len > 0.42f && f.cuts < 3) flying.add(Flying(center, v, len, f.r, f.leafKind, f.rot, f.rotV * 1.6f + (Random.nextFloat() - 0.5f) * 200f, bamboo = f.bamboo, cuts = f.cuts + 1))
                        else debris.add(Debris(center, Vec3(v.x, v.y * 0.4f, 0f), len, f.r, if (f.bamboo) -1 else f.leafKind, dir.x, dir.y, 0f))
                    }
                    piece(a.lerp(cutAt, 0.5f), half1, -2.5f); piece(cutAt.lerp(b2, 0.5f), half2, 2.5f)
                    burstLeaves(f.pos, if (f.bamboo) 2 else f.leafKind, if (f.bamboo) 4 else 8); burstChips(f.pos, if (f.bamboo) 8 else 6)
                    shake = max(shake, 3f)
                    ev(if (f.bamboo) "bamboo" else "cut")
                }
            }
        }
        return n
    }

    private fun worldOf(p: Vec3): Vec3 = if (mode == GMode.ZEN) rotY(p, turntable) else p

    fun rotY(p: Vec3, deg: Float): Vec3 {
        val a = Math.toRadians(deg.toDouble()); val c = cos(a).toFloat(); val s = sin(a).toFloat()
        return Vec3(p.x * c + p.z * s, p.y, -p.x * s + p.z * c)
    }

    private fun cutBranch(b: Branch, t: Float) {
        val cut = b.a.lerp(b.b, t)
        val wCut = worldOf(cut); val wB = worldOf(b.b)
        val dir = (wB - wCut).norm()
        debris.add(Debris(wCut.lerp(wB, 0.5f), Vec3(dir.x * 1.2f + (Random.nextFloat() - 0.5f), 1.5f, dir.z * 1.2f + 0.5f), (wB - wCut).len(), b.r, b.leafKind, dir.x, dir.y, dir.z))
        burstLeaves(wB, if (b.leafKind < 0) 0 else b.leafKind, 5); burstChips(wCut, 4)
        b.b = cut; b.alive = false
        fun detach(c: Branch) { if (!c.alive) return; c.alive = false
            val wa = worldOf(c.a); val wb = worldOf(c.b); val d = (wb - wa).norm()
            debris.add(Debris(wa.lerp(wb, 0.5f), Vec3(d.x + (Random.nextFloat() - 0.5f) * 2f, 1f + Random.nextFloat(), d.z), (wb - wa).len(), c.r, c.leafKind, d.x, d.y, d.z))
            for (cc in c.children) detach(cc) }
        for (c in b.children) detach(c)
        shake = max(shake, 4f)
        when (mode) {
            GMode.ZEN -> {
                var goodN = 0; var badN = 0
                fun account(x: Branch) { if (x.outside) { cutOutside++; goodN++ } else { cutInside++; badN++ }; for (c in x.children) account(c) }
                account(b)
                if (badN == 0) { combo++; comboTimer = 3f; val pts = 10 * goodN * mult; score += pts; texts.add(GText(wCut.copy(), "+$pts", 0.9f, 0.6f, 1f, 0.6f)); ev("cut") }
                else { score = max(0, score - 15 * badN); combo = 0; texts.add(GText(wCut.copy(), "-${15 * badN}", 0.9f, 1f, 0.45f, 0.45f)); ev("oops") }
            }
            GMode.RUSH -> {
                lastCutT = elapsed
                if (b.nest) { score = max(0, score - 25); combo = 0; flash = 0.6f; ev("nest"); texts.add(GText(wB.copy(), "-25", 1f, 1f, 0.4f, 0.4f)); for (i in 0..3) bees.add(Bee(wB.copy(), Vec3((Random.nextFloat() - 0.5f) * 3f, 0.8f + Random.nextFloat(), 0.25f))) }
                else {
                    val quick = b.grow < 0.7f
                    val pts = ((if (b.flower) 25 else 15) + (if (quick) 10 else 0)) * mult; score += pts
                    texts.add(GText(wB.copy(), (if (quick) label("fast", "Rapide !") + " " else "") + "+$pts", 0.9f, 1f, 0.95f, 0.5f)); ev("cut")
                }
            }
            GMode.SLICE -> {}
        }
    }

    private fun burstLeaves(p: Vec3, kind: Int, n: Int) {
        repeat(n) { leaves.add(LeafBurst(p.copy(), Vec3((Random.nextFloat() - 0.5f) * 3f, 1f + Random.nextFloat() * 2f, (Random.nextFloat() - 0.5f) * 3f), kind, 1.2f + Random.nextFloat(), 0.22f + Random.nextFloat() * 0.18f)) }
    }
    private fun burstChips(p: Vec3, n: Int) {
        repeat(n) { leaves.add(LeafBurst(p.copy(), Vec3((Random.nextFloat() - 0.5f) * 4f, 2f + Random.nextFloat() * 3f, (Random.nextFloat() - 0.5f) * 3f), 0, 0.5f + Random.nextFloat() * 0.4f, 0.06f + Random.nextFloat() * 0.05f, chip = true)) }
    }

    private fun segSegParam(x0: Float, y0: Float, x1: Float, y1: Float, ax: Float, ay: Float, bx: Float, by: Float, band: Float): Float {
        var best = Float.MAX_VALUE; var bestT = -1f
        val steps = 6
        for (i in 0..steps) {
            val s = i.toFloat() / steps
            val px = x0 + (x1 - x0) * s; val py = y0 + (y1 - y0) * s
            val dx = bx - ax; val dy = by - ay; val l2 = dx * dx + dy * dy
            val t = if (l2 < 1e-3f) 0f else (((px - ax) * dx + (py - ay) * dy) / l2).coerceIn(0f, 1f)
            val cx = ax + dx * t; val cy = ay + dy * t
            val d = sqrt((px - cx) * (px - cx) + (py - cy) * (py - cy))
            if (d < best) { best = d; bestT = t }
        }
        return if (best <= band) bestT else -1f
    }

    // --------------------------------------------------------------- rendu (thread GL)
    private val proj2 = FloatArray(2)
    fun render(r: GardenRenderer) {
        when (mode) {
            GMode.ZEN -> { r.eye = Vec3(0f, 2.7f, 10.5f); r.target = Vec3(0f, 1.75f, 0f); r.fov = 46f; r.fogStart = 30f; r.fogEnd = 120f }
            GMode.RUSH -> { r.eye = Vec3(0f, 2.35f, 6.4f); r.target = Vec3(0f, 2.3f, 0f); r.fov = 58f; r.fogStart = 30f; r.fogEnd = 120f }
            // zone de jeu PLEIN ÉCRAN : caméra plus haute, champ plus large, les branches montent jusqu'en haut
            GMode.SLICE -> { r.eye = Vec3(0f, 3.6f, 8.5f); r.target = Vec3(0f, 3.1f, 0f); r.fov = 62f; r.fogStart = 40f; r.fogEnd = 140f }
        }
        r.fogColor[0] = 0.86f; r.fogColor[1] = 0.80f; r.fogColor[2] = 0.88f   // même teinte que le ciel : pas de bande blanche à l'horizon
        val sh = shake * 0.004f
        if (sh > 0f) { r.eye.x += (Random.nextFloat() - 0.5f) * sh; r.eye.y += (Random.nextFloat() - 0.5f) * sh }
        r.setCamera()
        r.drawSky()
        drawSunAndClouds(r)
        // sol
        r.identity(); r.translate(0f, if (mode == GMode.RUSH) 0f else -0.02f, if (mode == GMode.RUSH) camZ else 0f); r.scale(160f, 1f, 160f)
        r.draw(r.ground, "gz_grass", 1f, 1f, 1f, uvScale = 1f)
        when (mode) {
            GMode.ZEN -> {
                r.identity(); r.scale(1.1f, 0.35f, 1.1f); r.draw(r.cyl, "gz_soil", 0.8f, 0.75f, 0.7f)
                // --- jardin autour : allée de terre circulaire, barrière blanche basse en arc au fond,
                //     massifs, cabane, brouette, banc, souche, pots, arbres variés à plusieurs distances ---
                r.identity(); r.translate(0f, -0.03f, 0f); r.scale(3.4f, 0.035f, 3.4f); r.draw(r.cyl, "gz_soil", 0.95f, 0.9f, 0.85f)
                fun ring(a: Float, d: Float, y: Float = 0f) = Vec3(sin(Math.toRadians(a.toDouble())).toFloat() * d, y, cos(Math.toRadians(a.toDouble())).toFloat() * d)
                for (i in -3..3) card(r, ring(180f + i * 13f, 11.5f), "gz_fence", 2.6f, 1.05f)
                for (i in -3..3 step 2) card(r, ring(180f + i * 13f + 6f, 10.6f), "gz_flowerbed", 2.2f, 0.85f)
                card(r, ring(150f, 14f), "gz_shed", 4.2f, 4.2f)
                card(r, ring(215f, 12.5f), "gz_bench", 2.2f, 1.2f)
                card(r, ring(120f, 9f), "gz_wheelbarrow", 1.8f, 1.2f)
                card(r, ring(245f, 8.5f), "gz_stump", 1.2f, 0.8f)
                card(r, ring(95f, 6.5f), "gz_pot", 0.8f, 0.9f); card(r, ring(265f, 6.2f), "gz_watering", 0.8f, 0.7f); card(r, ring(60f, 7f), "gz_pot", 0.7f, 0.8f)
                for (i in 0 until 9) { val a = i * 40f + 15f; val d = 16f + (i % 3) * 3f
                    val big = i % 3 != 1
                    card(r, ring(a, d), if (big) "gz_tree_a" else "gz_tree_b", if (big) 7f else 4f, if (big) 7f else 9f) }
                for (i in 0 until 5) card(r, ring(i * 72f + 40f, 24f), "gz_tree_a", 9f, 9f)
                // la plante : corps de feuillage dense (déjà en forme) + pousses mortes qui dépassent
                drawBranches(r)
                val sc = bushScale
                for (t in tufts) {
                    val p = worldOf(t.pos) * sc; val sz = t.size * sc
                    r.modelBillboard(p - Vec3(0f, sz * 0.5f, 0f), sz, sz)
                    if (t.flower) r.draw(r.card, "gz_flower${t.kind + 1}", 1f, 1f, 1f) else r.draw(r.card, "gz_leaf${t.kind + 1}", t.shade, t.shade, t.shade)
                }
                // papillons et oiseau
                r.depthMask(false)
                for (c in critters) {
                    if (c.bird) { r.modelBillboard(c.pos, 0.9f, 0.6f); r.rotate(sin(c.t * 9f) * 15f, 0f, 0f, 1f); r.draw(r.card, "gz_bird", 1f, 1f, 1f, fog = false) }
                    else { r.modelBillboard(c.pos, 0.28f, 0.28f); r.rotate(sin(c.t * 14f) * 35f, 0f, 1f, 0f); r.draw(r.card, "gz_butterfly", 1f, 1f, 1f) }
                }
                r.depthMask(true)
            }
            GMode.RUSH -> {
                // grande haie FACE à la caméra (les pousses en jaillissent), arbres derrière, allée de terre devant
                // (uvScale < 1 : grandes feuilles de buis lisibles ; à petite échelle la texture ressemblait à une pelouse)
                r.identity(); r.translate(0f, 0f, 0f); r.scale(4.2f, 4.9f, 1f); r.draw(r.wall, "gz_hedge", 0.9f, 0.95f, 0.85f, uvScale = 0.45f)
                r.identity(); r.translate(0f, 4.9f, -0.5f); r.rotate(-90f, 1f, 0f, 0f); r.scale(4.2f, 1f, 1f); r.draw(r.wall, "gz_hedge", 0.75f, 0.85f, 0.7f, uvScale = 0.45f)
                r.identity(); r.translate(0f, 0.01f, 3f); r.scale(6f, 1f, 6f); r.draw(r.ground, "gz_soil", 1f, 1f, 1f, uvScale = 0.3f)
                card(r, Vec3(-3.2f, 0f, -2.5f), "gz_tree_a", 6f, 6f); card(r, Vec3(3.4f, 0f, -3f), "gz_tree_b", 3.6f, 7.5f)
                card(r, Vec3(-2.6f, 0f, 0.6f), "gz_flowerbed", 1.8f, 0.8f); card(r, Vec3(2.7f, 0f, 0.6f), "gz_pot", 0.7f, 0.8f)
                drawBranches(r)
                // le jardinier, petit, en bas à gauche, de dos face à la haie ; il donne un coup de cisaille à chaque coupe
                val frame = if (elapsed - lastCutT < 0.22f) "gz_gardener_b" else "gz_gardener_a"
                r.depthMask(false)
                r.identity(); r.translate(0.95f, 0.02f, 1.6f); r.rotate(-90f, 1f, 0f, 0f); r.scale(0.55f, 0.24f, 1f); r.translate(0f, -0.5f, 0f)
                r.draw(r.card, "gz_disc", 0f, 0f, 0f, a = 0.4f)
                r.depthMask(true)
                card(r, Vec3(0.95f, 0f, 1.6f), frame, 0.46f, 0.8f)
            }
            GMode.SLICE -> {
                for (i in -3..3) { r.identity(); r.translate(i * 6f, 0f, -9f); r.scale(6f, 3.0f, 1f); r.draw(r.wall, "gz_hedge", 1f, 1f, 1f) }
                for (i in -2..2) card(r, Vec3(i * 5.5f - 2f, 0f, -8.6f), if (i % 2 == 0) "gz_tree_a" else "gz_tree_b", if (i % 2 == 0) 6f else 3.8f, if (i % 2 == 0) 6f else 8f)
                card(r, Vec3(-9f, 0f, -14f), "gz_shed", 4.5f, 4.5f); card(r, Vec3(10f, 0f, -16f), "gz_tree_a", 8f, 8f)
                for (i in -3..3) card(r, Vec3(i * 2.2f, 0f, -5.5f), "gz_fence", 2.3f, 1.0f)
                for (i in -2..2) card(r, Vec3(i * 3.2f + 1f, 0f, -4.6f), "gz_flowerbed", 2.4f, 0.9f)
                card(r, Vec3(5.5f, 0f, -3.5f), "gz_bench", 1.8f, 1.0f); card(r, Vec3(-5.5f, 0f, -3.8f), "gz_stump", 1.2f, 0.8f)
                for (f in flying) {
                    if (f.bee) { r.modelBillboard(f.pos - Vec3(0f, 0.45f, 0f), 0.9f, 0.9f); r.draw(r.card, "gz_bee", 1f, 1f, 1f); continue }
                    val d = Vec3(cos(Math.toRadians(f.rot.toDouble())).toFloat(), sin(Math.toRadians(f.rot.toDouble())).toFloat(), 0f)
                    val a = f.pos - d * (f.len / 2f); val b = f.pos + d * (f.len / 2f)
                    r.modelSegment(a, b, f.r); r.draw(r.cyl, if (f.bamboo) "gz_bamboo" else "gz_bark", 1f, 1f, 1f, uvScale = if (f.bamboo) 1f else 1f)
                    if (f.cuts == 0 || f.bamboo) {
                        val leafTex = when { f.nest -> "gz_nest"; f.bamboo -> "gz_leaf_bamboo"; else -> "gz_leaf${f.leafKind + 1}" }
                        r.modelBillboard(b - Vec3(0f, 0.4f, 0f), if (f.bamboo) 0.9f else 0.8f, if (f.bamboo) 0.9f else 0.8f); r.draw(r.card, leafTex, 1f, 1f, 1f)
                    }
                }
            }
        }
        for (d in debris) {
            val dir = Vec3(d.dirX, d.dirY, d.dirZ).norm()
            val half = dir * (d.len / 2f)
            r.identity(); r.translate(d.pos.x, d.pos.y, d.pos.z); r.rotate(d.spin * 57f, 1f, 0f, 0.3f); r.translate(-d.pos.x, -d.pos.y, -d.pos.z)
            val m = FloatArray(16); r.getModel(m)
            r.modelSegment(d.pos - half, d.pos + half, d.r)
            val seg = FloatArray(16); r.getModel(seg)
            val out = FloatArray(16); android.opengl.Matrix.multiplyMM(out, 0, m, 0, seg, 0); r.setModel(out)
            r.draw(r.cyl, "gz_bark", 1f, 1f, 1f, a = (d.life / 0.6f).coerceIn(0f, 1f))
            if (d.leafKind >= 0) { r.modelBillboard(d.pos + half - Vec3(0f, 0.35f, 0f), 0.7f, 0.7f); r.draw(r.card, "gz_leaf${d.leafKind + 1}", 1f, 1f, 1f, a = (d.life / 0.6f).coerceIn(0f, 1f)) }
        }
        r.depthMask(false)
        for (l in leaves) {
            if (l.chip) { r.modelBillboard(l.pos, l.size, l.size); r.rotate(l.life * 400f, 0f, 0f, 1f); r.draw(r.box, "gz_bark", 1f, 0.9f, 0.7f, a = (l.life / 0.4f).coerceIn(0f, 1f)) }
            else { r.modelBillboard(l.pos, l.size, l.size); r.rotate(l.life * 200f, 0f, 0f, 1f); r.draw(r.card, "gz_leaf${(l.kind % 2) + 1}", 1f, 1f, 1f, a = (l.life / 0.8f).coerceIn(0f, 1f)) }
        }
        for (b in bees) { val bs = if (mode == GMode.RUSH) 0.32f else 0.6f; r.modelBillboard(b.pos, bs, bs); r.draw(r.card, "gz_bee", 1f, 1f, 1f) }
        r.depthMask(true)
        drawToolCursor(r)
        // projection des textes flottants
        for (t in texts) { if (r.project(t.pos, proj2)) { t.sx = proj2[0]; t.sy = proj2[1] } }
    }

    private fun card(r: GardenRenderer, p: Vec3, tex: String, w: Float, h: Float) {
        if (!r.hasTex(tex)) return
        r.modelBillboard(p, w, h); r.draw(r.card, tex, 1f, 1f, 1f)
    }

    private fun drawSunAndClouds(r: GardenRenderer) {
        r.depthMask(false)
        val e = r.eye
        // soleil doux + nuages qui dérivent lentement, loin devant la caméra
        val fwd = (r.target - e).norm()
        val side = Vec3(fwd.z, 0f, -fwd.x)
        val sunP = e + fwd * 200f + side * 55f + Vec3(0f, 62f, 0f)
        // (la carte part de son pied : on abaisse la position de la moitié de la hauteur, pas de translate après scale)
        r.modelBillboard(sunP - Vec3(0f, 19f, 0f), 38f, 38f); r.draw(r.card, "gz_disc", 1f, 0.97f, 0.78f, a = 0.95f, fog = false, unlit = true)
        r.modelBillboard(sunP - Vec3(0f, 30f, 0f), 60f, 60f); r.draw(r.card, "gz_disc", 1f, 0.95f, 0.75f, a = 0.14f, fog = false, unlit = true)
        for (i in 0 until 7) {
            val drift = (elapsed * 0.7f + i * 41f) % 240f - 120f
            val h = 20f + (i % 2) * 6f
            val p = e + fwd * (170f + (i % 2) * 25f) + side * drift + Vec3(0f, 40f + (i % 3) * 14f - h / 2f, 0f)
            r.modelBillboard(p, 44f + (i % 3) * 14f, h); r.draw(r.card, "gz_cloud", 1f, 1f, 1f, a = 0.9f, fog = false, unlit = true)
        }
        r.depthMask(true)
    }

    private fun drawBranches(r: GardenRenderer) {
        val sc = if (mode == GMode.ZEN) bushScale else 1f
        for (b in branches) {
            if (!b.alive && b.parent == null) continue
            if (mode == GMode.RUSH && (b.b - b.a).len() < 0.02f) continue
            val a = worldOf(b.a) * sc; val bb = worldOf(b.b) * sc
            r.modelSegment(a, bb, b.r * sc)
            val dead = mode == GMode.ZEN && b.outside && b.alive
            if (dead) r.draw(r.cyl, "gz_bark", 0.85f, 0.72f, 0.6f)
            else r.draw(r.cyl, "gz_bark", if (b.alive) 1f else 0.9f, if (b.alive) 1f else 0.8f, if (b.alive) 1f else 0.7f)
            if (!b.alive) { r.modelBillboard(bb - Vec3(0f, b.r * 1.1f, 0f), b.r * 2.2f, b.r * 2.2f); r.draw(r.card, "gz_rings", 1f, 1f, 1f); continue }
            if (b.leafKind >= 0 || b.flower || b.nest) {
                val tex = when { b.nest -> "gz_nest"; b.flower -> "gz_flower${(b.leafKind % 2) + 1}"; else -> "gz_leaf${(b.leafKind % 2) + 1}" }
                val sz = (if (b.nest) 0.7f else b.leafSize) * sc
                r.modelBillboard(bb - Vec3(0f, sz * 0.45f, 0f), sz, sz)
                // topiaire : les branches mortes ont un feuillage ORANGE/brun bien distinct du vert
                if (mode == GMode.ZEN && b.outside) r.draw(r.card, tex, 1.45f, 0.62f, 0.12f) else r.draw(r.card, tex, 1f, 1f, 1f)
            }
        }
    }

    /** L'outil suit le doigt (projeté à ~3 m devant la caméra). */
    private fun drawToolCursor(r: GardenRenderer) {
        if (fingerX < 0f) return
        val dir = r.unproject(fingerX, fingerY)
        val p = r.eye + dir * 3.2f
        val t = tool
        val s = 0.18f + t.tier * 0.05f
        val open = if (fingerDown) 0.15f else 0.55f
        r.depthMask(false)
        if (!t.electric) {
            for (side in listOf(-1f, 1f)) {
                r.identity(); r.translate(p.x, p.y, p.z); r.rotate(side * open * 40f, 0f, 0f, 1f); r.translate(0f, s * 1.2f, 0f); r.scale(s * 0.22f, s * 2.4f, s * 0.08f)
                r.draw(r.box, "gz_metal", 0.95f, 0.95f, 1f, fog = false)
                r.identity(); r.translate(p.x, p.y, p.z); r.rotate(side * open * 40f, 0f, 0f, 1f); r.translate(0f, -s * 0.9f, 0f); r.scale(s * 0.4f, s * 1.6f, s * 0.3f)
                r.draw(r.box, null, t.r, t.g, t.b, fog = false)
            }
        } else {
            val vib = if (fingerDown) sin(elapsed * 90f) * 0.03f else 0f
            r.identity(); r.translate(p.x, p.y - s * 0.6f, p.z); r.scale(s * 1.2f, s * 1.0f, s * 0.8f); r.draw(r.box, null, t.r, t.g, t.b, fog = false)
            r.identity(); r.translate(p.x + vib, p.y + s * 1.0f, p.z); r.scale(s * 0.35f, s * 2.6f, s * 0.12f); r.draw(r.box, "gz_metal", 0.9f, 0.9f, 0.95f, fog = false)
            if (t.tier >= 3) { r.identity(); r.translate(p.x, p.y + s * 1.0f, p.z); r.scale(s * 1.6f, s * 3.2f, s * 1.6f)
                r.draw(r.sph, null, if (t.tier == 5) 0.4f else 0.5f, 1f, if (t.tier == 5) 0.3f else 1f, a = if (fingerDown) 0.35f else 0.15f, fog = false, unlit = true) }
        }
        r.depthMask(true)
    }
}
