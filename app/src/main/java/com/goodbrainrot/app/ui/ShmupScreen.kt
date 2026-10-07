package com.goodbrainrot.app.ui

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.foundation.Image
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import com.goodbrainrot.app.applyImmersive
import com.goodbrainrot.app.audio.Sfx
import com.goodbrainrot.app.findActivity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.goodbrainrot.app.R
import com.goodbrainrot.app.audio.GameMusic
import com.goodbrainrot.app.audio.Haptics
import com.goodbrainrot.app.audio.SoundEngine
import com.goodbrainrot.app.data.HighScores
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

// ===========================================================================
//  CRIMSON SKY — shmup vertical pixel-art néon (homage Crimzon Clover)
//  Niveau 1 : vagues -> mini-boss -> boss robot. 3 modes (Easy/Normal/Hard).
// ===========================================================================

private const val SHMUP_KEY = "shmup"

// Polices pixel-art (arcade 90s) : Press Start 2P pour les titres/alertes (chunky),
// Silkscreen pour les compteurs/score (fin et net).
private val Arcade = FontFamily(Font(R.font.pressstart2p))
private val PixelHud = FontFamily(Font(R.font.silkscreen))
// Typeface pixel pour le Canvas natif (textes flottants « +10$ ») — assigné une fois au lancement.
private var shmupPixelTypeface: android.graphics.Typeface? = null

private val CRIM = Color(0xFFE53A5A)
private val CRIM_DARK = Color(0xFF8E1230)
private val CYAN = Color(0xFF6CE8FF)
private val CYAN_HOT = Color(0xFFCFFBFF)
private val PINK = Color(0xFFFF5DA2)
private val PURPLE = Color(0xFFB46BFF)
private val ORANGE = Color(0xFFFF8A3D)
private val GOLD = Color(0xFFFFE94A)
private val GREEN = Color(0xFF6BFF8F)
private val SPREAD_VIOLET = Color(0xFFB86CFF)   // boulettes du vaisseau SPREAD (violet)
// palettes d'ennemis par niveau
private val E_GRN = Color(0xFF5FD36A); private val E_LIME = Color(0xFFB6FF5C)
private val E_ICE = Color(0xFF6CC8FF); private val E_ICEW = Color(0xFFDDF6FF)
private val E_LAVA = Color(0xFFFF6A3D); private val E_LAVADK = Color(0xFF7A1E10); private val E_LYEL = Color(0xFFFFD24A)
private val E_VIO = Color(0xFFC05CFF); private val E_MAG = Color(0xFFFF4FA0)
private val FIRE_COLORS = listOf(Color.White, Color(0xFFFFE94A), Color(0xFFFF8A3D), Color(0xFFFF3B2A))

private enum class ShPhase { CINEMA, MODE, SHIP, INTRO, PLAYING, LEVEL_CLEAR, BOSS_DYING, SHIP_EXIT, STAGE_RESULT, CONGRATS, DEATH, GAME_OVER, NAME_ENTRY, SCORES }

private const val LAST_LEVEL = 5   // niveaux visibles ; le 6 est caché (run sans dégât)
private enum class ShMode(val label: String, val lives: Int, val bulletSpeed: Float,
                          val fireRate: Float, val hpMul: Float, val scored: Boolean) {
    EASY("FACILE", 99, 0.30f, 0.55f, 0.8f, false),
    NORMAL("NORMAL", 3, 0.44f, 1.0f, 1.0f, true),
    HARD("DIFFICILE", 3, 0.60f, 1.55f, 1.4f, true),
    SCORE("SCORE ATTACK", 3, 0.44f, 1.0f, 1.0f, true)   // 2 minutes chrono, vagues continues, pas de boss de fin
}
private const val SCORE_ATTACK_S = 120f

/** Les archétypes de vaisseau. BRAINROT = le logo de l'app (tir guidé mais faible). */
private enum class ShShip(val label: String, val sub: String, val accent: Color) {
    VULCAN("VULCAN", "équilibré · tir droit", CRIM),
    SPREAD("SPREAD", "large · couvre tout", GREEN),
    LASER("LASER", "concentré · perforant", CYAN),
    BEE("ABEILLE", "dards guidés · perçants", GOLD),
    BRAINROT("BRAINROT", "guidé · tir faible", Color(0xFFF4AFC9))
}

private enum class Kind { DRONE, INTERCEPTOR, MINIBOSS, BOSS }

// ---------------------------------------------------------------------------
// Entités (objets simples, mutés par la boucle de jeu)
// ---------------------------------------------------------------------------

private class Bullet(var x: Float, var y: Float, var vx: Float, var vy: Float,
                     val r: Float, val friendly: Boolean, val color: Color, val dmg: Int = 1,
                     val homing: Boolean = false, val pierce: Boolean = false, val dart: Boolean = false,
                     val fang: Boolean = false,    // fang = projectile en "dent de serpent" (boss 2)
                     val vortex: Boolean = false)  // vortex = projectile "trou noir/tourbillon" (gros, lent)

private class Enemy(
    var x: Float, var y: Float, var hp: Int, val maxHp: Int,
    val kind: Kind, val score: Int, val radius: Float
) {
    var t = 0f
    var vx = 0f
    var vy = 0f
    var baseX = x
    var fireCd = Random.nextFloat() * 1.2f + 0.4f
    var pattern = 0
    var patCd = 1.2f
    var hitFlash = 0f
    var entering = true
    var vulnerable = false  // VERROU : passe à true une fois assez visible, ne revient JAMAIS en arrière
    var trueLast = false   // true = boss final caché
    var canShoot = true    // dans un essaim, seule une fraction tire (moins de balles)
    val shade = Random.nextInt(3)   // variante de couleur (variété dans une même vague)
    val phase = Random.nextFloat() * 6.2832f   // déphasage d'oscillation : un essaim ne bouge pas d'un bloc
    val hoverFrac = 0.20f + Random.nextFloat() * 0.14f   // hauteur de vol stationnaire (intercepteurs), étalée
    var volley = 0         // nb de salves déjà tirées (les 2 premières sont adoucies)
    var heavy = false      // ennemi « lourd » : gros, beaucoup de PV, rare, lent
    var variant = 0        // pour les mini-boss : archétype unique (look + pattern)
    var xOff = 0f          // décalage horizontal du centre (mid-boss en paire)
    var spriteImg: ImageBitmap? = null  // sprite IA (mid-boss) : remplace le rendu procédural
    var ascending = false  // (obsolète) arrivait par le bas
    var side = false       // entre par un côté (partie haute) et traverse
    var cannon = false     // gros canon fixe qui tire tout droit vers le bas (destructible)
    var anchorY = 0f       // position verticale d'arrêt (canon)
    var phaseSeen = 0      // vague C : dernière phase de boss annoncée (0/1/2)
}

// --- Mini-boss : chaque apparition est unique (couleur + motif de tir + mouvement) ---
private val MINIBOSS_TINT = listOf(
    Color(0xFFB8FFE0), Color(0xFFD8B0FF), Color(0xFFB0E8FF), Color(0xFFFFB8CE), Color(0xFFC4FFB0),
    Color(0xFFFFE2A0), Color(0xFFFFC0A0), Color(0xFFA0D0FF), Color(0xFFE0FF90), Color(0xFFF0B0FF)
)
private val MINIBOSS_BULLET = listOf(
    ORANGE, Color(0xFFB56CFF), Color(0xFF66E0FF), PINK, Color(0xFF7CFF7C),
    Color(0xFFFFD24E), Color(0xFFFF6A4E), Color(0xFF4E9BFF), Color(0xFFB6FF3D), Color(0xFFE05CFF)
)
private fun miniBossTintOf(v: Int): Color = MINIBOSS_TINT[v % MINIBOSS_TINT.size]
private fun miniBossBulletOf(v: Int): Color = MINIBOSS_BULLET[v % MINIBOSS_BULLET.size]

private class Particle(var x: Float, var y: Float, var vx: Float, var vy: Float,
                       var life: Float, val maxLife: Float, val color: Color, val size: Float)

/** Éclat d'impact (étoile blanche brève au point de contact balle/ennemi). */
private class HitSpark(var x: Float, var y: Float, var life: Float = 0.14f)

/** Onde de choc / anneau de feu d'une explosion (grandit + s'estompe). */
private class Blast(var x: Float, var y: Float, val maxR: Float, val color: Color,
                    val dur: Float, var age: Float = 0f, val thick: Float = 1f)

/** Explosion en SPRITE IA (grandit un peu + s'estompe). img = sprite tiré au sort. */
private class SpriteBurst(var x: Float, var y: Float, val size: Float, val img: ImageBitmap,
                          val dur: Float, var age: Float = 0f)

private class FloatText(var x: Float, var y: Float, val text: String, var life: Float, val color: Color,
                        val powerup: Boolean = false,  // powerup = affiché via le sprite « POWER UP »
                        val coin: Boolean = false)     // coin = montant $ affiché via la police IA (glyphes)

private class PowerUp(var x: Float, var y: Float, var vy: Float) {
    var vx = 0f          // le tourbillon (blackhole) rebondit -> a une vitesse horizontale
    var magnet = false; var mega = false
    var giant = false      // item géant (×3 + invincible + rayons)
    var blackhole = false  // item trou noir (aspire tout + points)
}

// Obstacle destructible qui descend et bloque le passage (mur / bâtiment).
private enum class ObKind { WALL, BUILDING }
private class Obstacle(var x: Float, var y: Float, val halfW: Float, val halfH: Float,
                       var hp: Int, val maxHp: Int, val kind: ObKind) {
    var hitFlash = 0f
    var spriteImg: ImageBitmap? = null   // sprite destructible IA (rôle « bloc »), sinon briques procédurales
}

private class Coin(var x: Float, var y: Float, var vy: Float, var vx: Float, val value: Int, var t: Float = 0f) {
    var magnet = false   // aspirée vers le vaisseau (effet bombe)
    val forOption = Random.nextFloat() < 0.5f   // ~moitié des pièces ramassables par les ailiers
}

// Trésors : pièce d'argent 1 / pièce d'or 5 / rubis 10 / saphir 20 / lingot d'or 50 / diamant 100
private fun randomCoinValue(): Int {
    val r = Random.nextFloat()
    return when {
        r < 0.44f -> 1      // argent
        r < 0.72f -> 5      // or
        r < 0.87f -> 10     // rubis
        r < 0.955f -> 20    // saphir
        r < 0.99f -> 50     // lingot
        else -> 100         // diamant
    }
}
private fun coinColor(v: Int): Color = when (v) {
    1 -> Color(0xFFCBD2DE)      // pièce d'argent
    5 -> GOLD                   // pièce d'or
    10 -> Color(0xFFFF3B5C)     // rubis
    20 -> Color(0xFF3B7CFF)     // saphir
    50 -> Color(0xFFFFD24A)     // lingot d'or
    else -> Color(0xFF7BE6FF)   // diamant
}
private fun coinTier(v: Int): Int = when (v) { 1 -> 0; 5 -> 1; 10 -> 1; 20 -> 2; 50 -> 2; else -> 3 }

private class Star(var x: Float, var y: Float, val z: Float, val size: Float)

// ---------------------------------------------------------------------------
// État de jeu
// ---------------------------------------------------------------------------

private class ShmupGame(
    var w: Float, var h: Float, val mode: ShMode, val ship: ShShip,
    val level: Int = 1,
    // Vague B : largeur VISIBLE (écran). w est la largeur du MONDE (écran × 1.5 en jeu
    // normal) ; une caméra horizontale suit le vaisseau. viewW = w -> pas de caméra (démos).
    val viewW: Float = w,
    initLives: Int = -1, initPower: Int = 1, initBombs: Int = 3,
    initScore: Int = 0, initCoins: Int = 0, initPerfect: Boolean = true,
    val midBossSprites: List<ImageBitmap> = emptyList(),  // sprites IA de mid-boss pour ce stage
    val enemySprites: List<ImageBitmap> = emptyList(),    // sprites IA d'ennemis (piétaille) pour ce stage
    val cannonSprites: List<ImageBitmap> = emptyList(),   // sprites IA de destructibles (canons fixes)
    val explosionSprites: List<ImageBitmap> = emptyList() // sprites IA d'explosions (petites -> grosses)
) {
    val spriteBursts = ArrayList<SpriteBurst>()           // explosions en sprite en cours
    /** Nombre de sprites de mid-boss disponibles pour ce stage. */
    fun midBossSpriteCount(): Int = midBossSprites.size

    /** Associe un sprite IA d'ennemi (si dispo) à un grunt. */
    private fun tagEnemy(e: Enemy) { if (enemySprites.isNotEmpty()) e.spriteImg = enemySprites.random() }
    val u: Float get() = h / 100f

    // Difficulté qui monte avec le niveau
    val diffHp: Float get() = mode.hpMul * (1f + (level - 1) * 0.16f)
    // Mode enfant (FACILE) : boss et mini-boss +25 % de vie pour un combat un peu plus long.
    val bossHpMul: Float get() = if (mode == ShMode.EASY) 1.25f else 1f
    // Fraction des ennemis d'un essaim qui tirent (le reste ne fait que descendre) → moins de balles.
    val swarmShootFrac: Float get() = when (mode) { ShMode.EASY -> 0.16f; ShMode.NORMAL, ShMode.SCORE -> 0.24f; else -> 0.38f }
    // Délai avant qu'un ennemi commence à tirer après son apparition (laisse le temps d'attaquer).
    private val fireGrace = 1.8f
    val diffBullet: Float get() = mode.bulletSpeed * (1f + (level - 1) * 0.09f)
    val diffFire: Float get() = mode.fireRate * (1f + (level - 1) * 0.11f)

    // Thème visuel du niveau (fond + ennemis uniques)
    val theme = levelTheme(level)

    // joueur (état transmis d'un niveau au suivant)
    var px = w / 2f
    var py = h * 0.82f
    var lives = if (initLives >= 0) initLives else mode.lives
    var invuln = 1.4f
    var power = initPower
    var fireCd = 0f
    var shootSndCd = 0f
    var bombs = initBombs
    var perfect = initPerfect   // aucun dégât depuis le début du run
    var stageHitless = true     // aucun dégât sur CE stage (pour l'affichage PERFECT)
    var bombFlash = 0f
    var bombWave = -1f     // -1 = inactif, sinon 0..1 (onde de choc)
    var bombShield = 0f    // champ de force autour du vaisseau pendant l'explosion de bombe
    var giantT = 0f        // item géant : durée restante (s) de super-taille + rayons + invincibilité
    var giantRayCd = 0f    // cadence de dégâts des rayons du mode géant
    var giantSpawned = false // un seul item géant par stage
    var blackholeT = 0f    // effet visuel « trou noir » (secondes)
    // juice
    var hitStop = 0f       // micro-gel du jeu à l'impact
    var flash = 0f         // flash d'écran (couleur ci-dessous)
    var flashCol = Color.White
    var bossWarn = 0f      // bannière "WARNING" à l'arrivée d'un boss
    var bossWarnBoss = false // true = vrai boss (rouge + éclairs), false = mini-boss (orange)

    val bullets = ArrayList<Bullet>()
    val enemies = ArrayList<Enemy>()
    val eBullets = ArrayList<Bullet>()
    val particles = ArrayList<Particle>()
    val texts = ArrayList<FloatText>()
    val powerups = ArrayList<PowerUp>()
    val coins = ArrayList<Coin>()
    val obstacles = ArrayList<Obstacle>()
    val sparks = ArrayList<HitSpark>()  // éclats d'impact
    val blasts = ArrayList<Blast>()  // ondes de choc d'explosion
    var playerDead = false           // le vaisseau a explosé (game over)
    var smokeCd = 0f                 // émission de fumée quand il ne reste qu'un cœur
    val fullLife: Boolean get() = mode.scored && lives >= mode.lives
    val lowLife: Boolean get() = mode.scored && lives <= 1 && !playerDead
    val stars = ArrayList<Star>()   // champ d'étoiles (parallaxe)

    // Escorte : missile téléguidé (power 4) + vaisseaux-options (power 5 et 6) qui SUIVENT et tirent
    val trail = ArrayDeque<Offset>()      // historique de position du vaisseau (pour les ailiers)
    val optionPos = ArrayList<Offset>()   // positions actuelles des vaisseaux-options
    var optFireCd = 0f
    var missileCd = 0f
    var miniEscortCd = 3f   // renforts périodiques pendant un mini-boss
    var miniBossInLevel = 0 // n-ième mini-boss de CE niveau (variante dérivée du niveau)

    var score = initScore
    var coinCount = initCoins
    val coinsByValue = HashMap<Int, Int>()   // pièces ramassées CE stage, par valeur (pour le récap)
    var stageElapsed = 0f                     // durée du stage (secondes)
    var combo = 0
    var comboTimer = 0f
    var mult = 1
    var breakFlash = 0f

    var shake = 0f
    var warp = 1f          // facteur de vitesse des étoiles (boost à l'intro)
    var bgScroll = 0f      // défilement du décor parallax (silhouettes de relief)
    // Vague B : caméra horizontale (offset monde -> écran), lissée sur le vaisseau.
    // Initialisée déjà centrée sur lui (pas de panoramique parasite au lancement).
    var camX = (px - viewW / 2f).coerceIn(0f, (w - viewW).coerceAtLeast(0f))
    var gameOver = false
    var boss: Enemy? = null
    var bossDeathPos: Offset? = null
    var shipExiting = false
    var shipEntering = false   // vol d'arrivée (depuis le bas) en début de niveau
    var sweepItems = false     // aspiration/collecte des items pendant la mort du boss (hors PLAYING)

    // Séquence de mort du boss : il explose et « tombe » avant l'envol du vaisseau
    var deadBoss: Enemy? = null
    var bossFalling = false
    var deadBossVy = 0f
    var deadBossFireCd = 0f

    // Rayon laser continu (vaisseau LASER, façon DoDonPachi)
    var laserOn = false
    var laserT = 0f
    var laserHitY = 0f      // y où le rayon percute le 1er ennemi (0 = jusqu'en haut)
    var laserDmgCd = 0f
    // Demi-largeur du faisceau (statique, pas d'oscillation). Niveau MAX (8) : ~1/4 de l'écran.
    val laserHalf: Float get() = when {
        power >= 8 -> viewW * 0.12f
        power >= 6 -> u * 4.4f
        power >= 3 -> u * 2.9f
        power == 2 -> u * 2.1f
        else -> u * 1.5f
    }

    init {
        // Champ d'étoiles en 3 profondeurs (z) → parallaxe doux et constant
        repeat(150) {
            val z = Random.nextFloat() * 0.9f + 0.1f
            stars.add(Star(Random.nextFloat() * w, Random.nextFloat() * h, z, Random.nextFloat()))
        }
    }

    fun bossAlive(): Boolean = boss != null && boss!!.hp > 0

    fun movePlayer(dx: Float, dy: Float) {
        if (shipEntering) return           // contrôle bloqué pendant l'arrivée
        val m = u * 6f
        px = (px + dx).coerceIn(m, w - m)
        py = (py + dy).coerceIn(h * 0.18f, h - m)
    }

    /** Caméra : suit le vaisseau, clampée aux bords du monde, lissée.
     *  Pendant un (mini-)boss, elle est biaisée vers lui — sans jamais faire
     *  sortir le vaisseau de l'écran. */
    private fun updateCamera(dt: Float) {
        val maxCam = (w - viewW).coerceAtLeast(0f)
        if (maxCam <= 0f) { camX = 0f; return }
        var target = px - viewW / 2f
        val bossE = boss ?: enemies.firstOrNull { it.kind == Kind.MINIBOSS && it.hp > 0 }
        if (bossE != null) {
            target = (px + bossE.x) / 2f - viewW / 2f
            val m = u * 10f
            target = target.coerceIn(px + m - viewW, px - m)
        }
        target = target.coerceIn(0f, maxCam)
        camX += (target - camX) * (1f - kotlin.math.exp(-dt * 5f))
    }

    // --- mise à jour ambiance (toujours, même hors jeu) ---
    fun updateAmbient(dt: Float) {
        updateCamera(dt)
        val speed = h * (0.18f + 0.5f * (warp - 1f))
        for (s in stars) {
            s.y += (s.z * speed + s.z * 30f) * dt * warp
            if (s.y > h) { s.y = 0f; s.x = Random.nextFloat() * w }
        }
        if (warp > 1f) warp = max(1f, warp - dt * 3.5f)
        bgScroll += dt * warp
        // particules vivent aussi en overlay (explosions du boss final)
        updateParticles(dt)
        updateTexts(dt)
        if (shake > 0f) shake = max(0f, shake - dt * 60f)
        if (breakFlash > 0f) breakFlash = max(0f, breakFlash - dt)
        if (bombFlash > 0f) bombFlash = max(0f, bombFlash - dt)
        // onde de choc ralentie : l'animation dure ~1 s de plus (mais pas plus de dégâts)
        if (bombWave >= 0f) { bombWave += dt * 0.62f; if (bombWave > 1f) bombWave = -1f }
        if (bombShield > 0f) bombShield = max(0f, bombShield - dt)
        if (blackholeT > 0f) blackholeT = max(0f, blackholeT - dt)
        updateGiant(dt)
        if (flash > 0f) flash = max(0f, flash - dt * 3.2f)
        if (bossWarn > 0f) {
            bossWarn = max(0f, bossWarn - dt)
            // l'écran tremble tant que l'alerte est affichée (arrivée du boss)
            val intensity = if (bossWarnBoss) 13f else 8f
            shake = max(shake, intensity + intensity * 0.4f * sin(bossWarn * 34f).toFloat())
        }
        // boss en train d'exploser et de tomber (juste avant l'envol du vaisseau)
        if (bossFalling) {
            val e = deadBoss
            if (e != null) {
                deadBossVy += h * 0.14f * dt
                e.y += deadBossVy * dt
                e.x += sin(e.t * 2.5f) * u * 0.6f
                e.t += dt
                deadBossFireCd -= dt
                if (deadBossFireCd <= 0f) {
                    deadBossFireCd = 0.06f + Random.nextFloat() * 0.05f
                    spawnFireball(
                        e.x + (Random.nextFloat() - 0.5f) * u * 24f,
                        e.y + (Random.nextFloat() - 0.5f) * u * 20f, 12, u * 1.1f
                    )
                    shake = max(shake, 18f)
                }
            }
        }

        // aspiration des items pendant la mort du boss (game.update ne tourne pas hors PLAYING)
        if (sweepItems) { updateCoins(dt); updatePowerups(dt) }

        // arrivée du vaisseau (début de niveau) : remonte vite depuis le bas jusqu'à sa position.
        if (shipEntering) {
            warp = max(warp, 3.5f)
            val target = h * 0.82f
            py -= h * 1.5f * dt
            repeat(2) {
                particles.add(Particle(px + (Random.nextFloat() - 0.5f) * u * 4f, py + u * 6f,
                    (Random.nextFloat() - 0.5f) * u * 5f, h * 0.35f, 0.4f, 0.4f,
                    if (Random.nextFloat() < 0.5f) CYAN else Color.White, u * 1.5f))
            }
            if (py <= target) { py = target; shipEntering = false }
        }

        // envol du vaisseau (fin de jeu) : accélère vers le haut, sort de l'écran,
        // avec les étoiles qui filent (warp) comme à l'intro.
        if (shipExiting) {
            warp = max(warp, 5f)                 // maintient les traînées d'étoiles
            py -= h * 2.6f * dt
            // les ailiers suivent et sortent juste après, l'un après l'autre
            for (i in optionPos.indices) {
                optionPos[i] = Offset(px, optionPos[i].y - h * 2.6f * dt * (0.92f - i * 0.08f))
            }
            repeat(2) {
                particles.add(Particle(px + (Random.nextFloat() - 0.5f) * u * 4f, py + u * 6f,
                    (Random.nextFloat() - 0.5f) * u * 5f, h * 0.35f, 0.45f, 0.45f,
                    if (Random.nextFloat() < 0.5f) CYAN else Color.White, u * 1.5f))
            }
        }
    }

    private fun updateParticles(dt: Float) {
        val it = particles.iterator()
        while (it.hasNext()) {
            val p = it.next()
            p.x += p.vx * dt; p.y += p.vy * dt
            p.vx *= 0.96f; p.vy = p.vy * 0.96f + 30f * dt
            p.life -= dt
            if (p.life <= 0f) it.remove()
        }
        val si = sparks.iterator()
        while (si.hasNext()) { val s = si.next(); s.life -= dt; if (s.life <= 0f) si.remove() }
        val bi = blasts.iterator()
        while (bi.hasNext()) { val b = bi.next(); b.age += dt; if (b.age >= b.dur) bi.remove() }
        val sbi = spriteBursts.iterator()
        while (sbi.hasNext()) { val b = sbi.next(); b.age += dt; if (b.age >= b.dur) sbi.remove() }
    }

    private fun updateTexts(dt: Float) {
        val it = texts.iterator()
        while (it.hasNext()) {
            val t = it.next()
            t.y -= 26f * u * dt
            t.life -= dt
            if (t.life <= 0f) it.remove()
        }
    }

    // --- mise à jour gameplay ---
    fun update(dt: Float) {
        stageElapsed += dt
        if (invuln > 0f) invuln = max(0f, invuln - dt)
        // fumée du vaisseau abîmé (design) quand il ne reste qu'un cœur
        if (lowLife) {
            smokeCd -= dt
            if (smokeCd <= 0f) {
                smokeCd = 0.07f
                particles.add(Particle(px + (Random.nextFloat() - 0.5f) * u * 4f, py + u * 3f,
                    (Random.nextFloat() - 0.5f) * u * 3f, -h * 0.12f, 0.7f, 0.7f, Color(0xFF9AA0A6), u * 1.7f))
            }
        }

        // tir : le vaisseau LASER émet un rayon continu, les autres tirent des perles
        laserOn = false
        if (ship == ShShip.LASER) {
            updateLaser(dt)
        } else {
            fireCd -= dt
            shootSndCd -= dt
            if (fireCd <= 0f) {
                fire()
                fireCd = fireInterval()
                if (shootSndCd <= 0f) { SoundEngine.playShoot(); shootSndCd = 0.085f }
            }
        }

        // combo
        if (comboTimer > 0f) {
            comboTimer -= dt
            if (comboTimer <= 0f) { combo = 0; mult = 1 }
        }

        updatePlayerBullets(dt)
        updateEnemies(dt)
        updateEnemyBullets(dt)
        updatePowerups(dt)
        updateCoins(dt)
        updateObstacles(dt)
        updateEscort(dt)
    }

    /** Missile téléguidé (power 4+) et vaisseaux-options qui suivent le vaisseau (power 5/6). */
    private fun updateEscort(dt: Float) {
        // Vaisseaux-options (power 5 = 1, power 6 = 2) : ils SUIVENT le vaisseau (traînée) et TIRENT.
        trail.addFirst(Offset(px, py))
        while (trail.size > 90) trail.removeLast()
        val nOpt = when { power >= 6 -> 2; power >= 5 -> 1; else -> 0 }
        while (optionPos.size < nOpt) optionPos.add(Offset(px, py))
        while (optionPos.size > nOpt) optionPos.removeAt(optionPos.size - 1)
        for (i in optionPos.indices) {
            val idx = (18 * (i + 1)).coerceAtMost(trail.size - 1)
            if (idx >= 0) optionPos[i] = trail[idx]
        }
        if (nOpt > 0) {
            optFireCd -= dt
            if (optFireCd <= 0f) {
                optFireCd = 0.28f
                for (p in optionPos) bullets.add(Bullet(p.x, p.y - u * 3f, 0f, -h * 1.05f, u * 1.05f, true, Color(0xFF8CF2FF), 1))
            }
        }
        // missile téléguidé : tire même s'il n'y a pas d'ennemi (part tout droit, se guide s'il en apparaît)
        if (power >= 4) {
            missileCd -= dt
            if (missileCd <= 0f) {
                missileCd = 0.8f
                val dir = if (enemies.isEmpty()) 0f else if (Random.nextBoolean()) -1f else 1f
                bullets.add(Bullet(px, py - u * 4f, dir * h * 0.18f, -h * 0.55f, u * 1.7f, true, ORANGE, 3, homing = true))
            }
        }
    }

    /** Cadence de tir selon le vaisseau et le niveau d'arme. */
    private fun fireInterval(): Float = when (ship) {
        ShShip.BRAINROT -> if (power >= 2) 0.10f else 0.17f // le power-up accélère la cadence
        else -> 0.12f
    }

    private fun fire() {
        val cx = px
        val top = py - u * 5f
        when (ship) {
            // ------- VULCAN : tir droit ; niveau 8 = tire AUSSI vers l'arrière -------
            ShShip.VULCAN -> {
                val vy = -h * 1.15f
                val nFwd = when { power <= 1 -> 1; power == 2 -> 2; power <= 4 -> 3; else -> 5 }
                if (nFwd == 1) {
                    bullets.add(Bullet(cx, top, 0f, vy, u * 1.5f, true, CYAN))
                } else {
                    for (k in 0 until nFwd) {
                        val fx = cx + (k - (nFwd - 1) / 2f) * u * 2.4f
                        bullets.add(Bullet(fx, top, 0f, vy, u * 1.3f, true, CYAN))
                    }
                    if (power >= 3) bullets.add(Bullet(cx, top, 0f, vy, u * 1.7f, true, CYAN_HOT, 2)) // renfort central
                }
                if (power >= 8) {                                   // canons arrière
                    val by = h * 1.0f
                    bullets.add(Bullet(cx - u * 2.4f, py + u * 5f, 0f, by, u * 1.3f, true, CYAN))
                    bullets.add(Bullet(cx + u * 2.4f, py + u * 5f, 0f, by, u * 1.3f, true, CYAN))
                }
            }
            // ------- SPREAD : éventail ; niveau 8 = 180° complet -------
            ShShip.SPREAD -> {
                val sp = h * 1.05f
                if (power >= 8) {
                    val n = 15
                    for (k in 0 until n) {
                        val a = -Math.PI.toFloat() / 2f + (k / (n - 1f)) * Math.PI.toFloat() // -90°..+90°
                        bullets.add(Bullet(cx, py, sin(a) * sp, -cos(a) * sp, u * 1.1f, true, SPREAD_VIOLET))
                    }
                } else {
                    val wp = min(power, 6)
                    val n = 3 + (wp - 1) * 2                        // 3..13
                    val spread = 0.16f + 0.05f * min(wp, 3) + 0.035f * max(0, wp - 3)
                    for (k in 0 until n) {
                        val t = if (n == 1) 0f else (k / (n - 1f)) * 2f - 1f
                        val a = t * spread
                        bullets.add(Bullet(cx, top, sin(a) * sp, -cos(a) * sp, u * 1.15f, true, SPREAD_VIOLET))
                    }
                }
            }
            // ------- LASER : perforant ; niveau 8 = faisceau géant (~1/4 de l'écran) -------
            ShShip.LASER -> {
                val vy = -h * 1.4f
                when {
                    power >= 8 -> {
                        // faisceau GÉANT : bande pleine d'~1/4 de l'écran (traits qui se chevauchent)
                        val halfW = w * 0.125f
                        val nb = 11
                        for (k in 0 until nb) {
                            val fx = cx + (k / (nb - 1f) - 0.5f) * 2f * halfW
                            val edge = kotlin.math.abs(k / (nb - 1f) - 0.5f) * 2f     // 0 centre -> 1 bord
                            val col = if (edge < 0.5f) Color.White else CYAN_HOT
                            bullets.add(Bullet(fx, top, 0f, -h * 1.55f, u * 2.6f, true, col, if (edge < 0.4f) 5 else 3))
                        }
                    }
                    power >= 6 -> {
                        bullets.add(Bullet(cx, top, 0f, vy, u * 2.6f, true, Color.White, 5))
                        for (s in intArrayOf(-1, 1)) {
                            bullets.add(Bullet(cx + s * u * 2.4f, top, 0f, vy, u * 1.6f, true, CYAN_HOT, 3))
                            bullets.add(Bullet(cx + s * u * 4.6f, top, 0f, vy, u * 1.4f, true, CYAN_HOT, 2))
                        }
                    }
                    power >= 3 -> {
                        bullets.add(Bullet(cx, top, 0f, vy, u * 2.4f, true, Color.White, 4))
                        bullets.add(Bullet(cx - u * 2.6f, top, 0f, vy, u * 1.4f, true, CYAN_HOT, 2))
                        bullets.add(Bullet(cx + u * 2.6f, top, 0f, vy, u * 1.4f, true, CYAN_HOT, 2))
                    }
                    power == 2 -> {
                        bullets.add(Bullet(cx - u * 1.6f, top, 0f, vy, u * 1.6f, true, CYAN_HOT, 2))
                        bullets.add(Bullet(cx + u * 1.6f, top, 0f, vy, u * 1.6f, true, CYAN_HOT, 2))
                    }
                    else -> bullets.add(Bullet(cx, top, 0f, vy, u * 1.9f, true, CYAN_HOT, 2))
                }
            }
            // ------- ABEILLE : dards guidés qui TRANSPERCENT tout (mêmes dégâts que BRAINROT) -------
            ShShip.BEE -> {
                val sp = h * 1.55f
                val dmg = if (power >= 6) 3 else if (power >= 3) 2 else 1   // même barème que BRAINROT
                val n = if (power >= 8) 3 else if (power >= 5) 2 else 1
                for (k in 0 until n) {
                    val ox = if (n == 1) 0f else (k - (n - 1) / 2f) * u * 2.8f
                    bullets.add(Bullet(cx + ox, top, 0f, -sp, u * 1.5f, true, GOLD, dmg,
                        homing = true, pierce = true, dart = true))
                }
            }
            // ------- BRAINROT : boule guidée ; niveau 8 = RAFALE de 5 boules guidées -------
            ShShip.BRAINROT -> {
                val sp = h * (if (power >= 2) 1.35f else 0.95f)
                when {
                    power >= 8 -> for (k in -2..2)   // rafale de 5 boules guidées
                        bullets.add(Bullet(cx + k * u * 2f, top, k * h * 0.14f, -sp, u * 2.6f, true, PINK, 2, homing = true))
                    power >= 6 -> for (s in intArrayOf(-1, 1))
                        bullets.add(Bullet(cx + s * u * 2f, top, 0f, -sp, u * 3.0f, true, PINK, 3, homing = true))
                    power >= 3 -> bullets.add(Bullet(cx, top, 0f, -sp, u * 2.9f, true, PINK, 2, homing = true))
                    else -> bullets.add(Bullet(cx, top, 0f, -sp, u * 1.6f, true, PINK, 1, homing = true))
                }
            }
        }
    }

    /** Fraction du corps actuellement visible >= seuil option ? (calcul brut, par frame). */
    private fun entryVisibleNow(e: Enemy): Boolean {
        val hh = when (e.kind) {
            Kind.MINIBOSS -> e.radius * 1.1f * (e.spriteImg?.let { it.height.toFloat() / it.width } ?: 1f)
            Kind.DRONE, Kind.INTERCEPTOR -> e.radius * 1.5f
            else -> e.radius
        }
        val vis = ((min(e.y + hh, h) - max(e.y - hh, 0f)) / (2f * hh)).coerceIn(0f, 1f)
        return vis >= com.goodbrainrot.app.Settings.enemyEntryVuln.floatValue.coerceIn(0f, 1f)
    }

    /** Rayon laser continu (vaisseau LASER) : dégâts en continu à ce qu'il traverse. */
    private fun updateLaser(dt: Float) {
        laserOn = true
        laserT += dt
        val half = laserHalf
        // point d'impact = ennemi le plus proche au-dessus du vaisseau, dans l'axe
        var hitE: Enemy? = null
        for (e in enemies) {
            if (e.y < py && kotlin.math.abs(e.x - px) < half + e.radius * (if (e.kind == Kind.MINIBOSS || e.kind == Kind.BOSS) 1.05f else 0.6f)) {
                if (hitE == null || e.y > hitE!!.y) hitE = e
            }
        }
        // Point d'arret du rayon : pour un boss/mid-boss, on s'arrete a son BORD BAS (surface
        // face au vaisseau) et non a son centre, sinon le rayon "traverse" jusqu'au milieu.
        var hitY = hitE?.let { e ->
            if (e.kind == Kind.MINIBOSS || e.kind == Kind.BOSS) {
                val img = e.spriteImg
                val hw = e.radius * 1.1f
                e.y + (if (img != null) hw * img.height / img.width else hw)
            } else e.y
        } ?: 0f
        for (o in obstacles) {
            if (o.y < py && kotlin.math.abs(o.x - px) < half + o.halfW) {
                val edge = o.y + o.halfH
                if (edge > hitY) hitY = edge
            }
        }
        laserHitY = hitY
        // dégâts en continu (par petits paliers)
        laserDmgCd -= dt
        if (laserDmgCd <= 0f) {
            laserDmgCd = 0.045f
            val dmg = when (power) { 1 -> 1; 2 -> 2; else -> 3 }
            for (e in enemies.toList()) {
                if (e.y < py && e.vulnerable && kotlin.math.abs(e.x - px) < half + e.radius * (if (e.kind == Kind.MINIBOSS || e.kind == Kind.BOSS) 1.05f else 0.6f))
                    damageEnemy(e, dmg, px, e.y + e.radius * 0.7f)   // impact bas de l'ennemi, dans l'axe du rayon
            }
            for (o in obstacles.toList()) {
                if (o.y < py && kotlin.math.abs(o.x - px) < half + o.halfW) damageObstacle(o, dmg)
            }
            // niveau MAX : rayon ARRIÈRE qui touche les ennemis sous le vaisseau (dont ceux du bas)
            if (power >= 8) {
                for (e in enemies.toList()) {
                    if (e.y > py && e.vulnerable && kotlin.math.abs(e.x - px) < half + e.radius * (if (e.kind == Kind.MINIBOSS || e.kind == Kind.BOSS) 1.05f else 0.6f))
                        damageEnemy(e, dmg, px, e.y - e.radius * 0.7f)
                }
            }
            hitE?.let { spawnParticles(it.x, it.y, 4, Color.White, u * 1.4f) }
        }
        // bourdonnement doux et régulier
        shootSndCd -= dt
        if (shootSndCd <= 0f) { SoundEngine.playShoot(); shootSndCd = 0.13f }
    }

    /** Aspire tous les items (pièces + power-ups) vers le vaisseau — effet « bombe ». */
    fun magnetAllItems() {
        for (c in coins) c.magnet = true
        for (p in powerups) p.magnet = true
    }

    /** Démarre la mort spectaculaire du boss : il explose et tombe (voir updateAmbient). */
    fun beginBossFall() {
        bossFalling = true
        deadBossVy = h * 0.05f
        deadBossFireCd = 0f
        shake = 34f
        hitStop = 0f
        magnetAllItems()               // tous les items refluent vers le vaisseau
        sweepItems = true              // continue de les collecter même hors PLAYING
        Haptics.heavy()
        if (deadBoss == null) {
            val p = bossDeathPos ?: Offset(w / 2f, h * 0.22f)
            deadBoss = Enemy(p.x, p.y, 0, 1, Kind.BOSS, 0, u * 12f)
        }
    }

    /** Explosion finale géante, puis le boss disparaît. */
    fun endBossFall() {
        deadBoss?.let { bigExplosion(it.x, it.y, 2.2f) }
        bossFalling = false
        deadBoss = null
        shake = 60f
        flash = 0.85f; flashCol = Color.White
        Haptics.heavy()
    }

    /** Le vaisseau s'envole vers le haut de l'écran (façon intro). */
    fun beginShipExit() {
        shipExiting = true
        warp = 6f
    }

    /** Le vaisseau arrive rapidement depuis le bas de l'écran (début de niveau). */
    fun beginShipEnter() {
        shipEntering = true
        py = h + u * 12f     // hors écran, en bas
        px = w / 2f
        warp = 5f
    }

    /**
     * Bouton bombe. Si on a des bombes, on en consomme une. Sinon, en secours, si on a
     * les DEUX vaisseaux-options (power 6), on les fait exploser pour un effet de bombe.
     */
    fun tryBomb() {
        if (bombs > 0) { bombs--; doBombEffect(); return }
        if (power >= 6 && optionPos.size >= 2) {          // sacrifice des 2 vaisseaux-options
            for (p in optionPos) spawnFireball(p.x, p.y, 34, u * 1.5f)
            optionPos.clear()
            power = 4                                     // les deux vaisseaux disparaissent (missile conservé)
            doBombEffect()
        }
    }

    /** Effet visuel/gameplay d'une bombe : DÉTRUIT toutes les balles, gros dégâts, écran fou. */
    private fun doBombEffect() {
        bombFlash = 0.95f
        bombWave = 0f
        bombShield = 1.9f        // champ de force autour du vaisseau le temps de l'explosion
        shake = 58f              // gros tremblement
        flash = 0.7f; flashCol = CYAN_HOT
        Haptics.heavy()          // vibration forte (répétée pour l'effet "fou")
        hitStop = 0.10f
        SoundEngine.playBomb()
        SoundEngine.playExplosion(true)
        // DÉTRUIT toutes les balles ennemies (chacune éclate en étincelles)
        for (b in eBullets) spawnParticles(b.x, b.y, 2, CYAN, u * 0.5f)
        eBullets.clear()
        // anneaux de choc concentriques géants (spectacle)
        blasts.add(Blast(px, py, h * 0.7f, Color.White, 0.35f, thick = 2f))
        blasts.add(Blast(px, py, h * 1.0f, CYAN_HOT, 0.55f, thick = 2.5f))
        blasts.add(Blast(px, py, h * 1.35f, CYAN, 0.75f, thick = 1.8f))
        val snapshot = enemies.toList()
        for (e in snapshot) {
            if (e.kind == Kind.BOSS || e.kind == Kind.MINIBOSS) damageEnemy(e, max(1, e.maxHp / 5))
            else killEnemy(e)
        }
        for (o in obstacles.toList()) damageObstacle(o, max(1, o.maxHp / 2))
        magnetAllItems()          // aspire pièces + power-ups
        invuln = max(invuln, bombShield)
    }

    // ------------------------------------------------------------------
    // Obstacles (murs / bâtiments qui descendent et bloquent le passage)
    // ------------------------------------------------------------------

    /** Rangée d'obstacles avec un trou à franchir, arrivant par le haut. */
    fun spawnWall(kind: ObKind) {
        val cols = 7
        val cellW = w / cols
        val gap = Random.nextInt(0, cols - 1)   // trou de 2 cases consécutives
        val bw = cellW * 0.46f
        val bh = u * 6.5f
        val baseHp = when (kind) { ObKind.WALL -> 10; ObKind.BUILDING -> 16 }
        val hp = (baseHp * (0.7f + level * 0.15f)).toInt().coerceAtLeast(6)
        for (c in 0 until cols) {
            if (c == gap || c == gap + 1) continue
            val cx = cellW * (c + 0.5f)
            obstacles.add(Obstacle(cx, -bh, bw, bh, hp, hp, kind).also {
                // « Bons assets » : chaque bloc du mur prend un sprite destructible IA du niveau
                // (au lieu des briques procédurales moches).
                if (cannonSprites.isNotEmpty()) it.spriteImg = cannonSprites.random()
            })
        }
    }

    private fun damageObstacle(o: Obstacle, dmg: Int) {
        o.hp -= dmg
        o.hitFlash = 0.06f
        spawnParticles(o.x, o.y, 2, Color(0xFFB8B8C0), o.halfW * 0.4f)
        if (o.hp <= 0) {
            SoundEngine.playExplosion(false)
            spawnFireball(o.x, o.y, 22, o.halfW * 0.7f)
            shake = max(shake, 12f)
            // Taux de drop 0..1000% : partie entiere = pieces garanties, reste = probabilite.
            val dropRate = com.goodbrainrot.app.Settings.shmupCoinDrop.floatValue
            var drops = dropRate.toInt()
            if (Random.nextFloat() < (dropRate - drops)) drops++
            repeat(drops) {
                coins.add(Coin(o.x + (Random.nextFloat() - 0.5f) * o.halfW, o.y,
                    h * 0.14f, (Random.nextFloat() - 0.5f) * h * 0.09f, randomCoinValue()))
            }
            obstacles.remove(o)
        }
    }

    private fun updateObstacles(dt: Float) {
        if (obstacles.isEmpty()) return
        val playerR = u * 4.5f
        val it = obstacles.iterator()
        while (it.hasNext()) {
            val o = it.next()
            o.y += h * 0.15f * dt
            if (o.hitFlash > 0f) o.hitFlash = max(0f, o.hitFlash - dt)
            if (o.y - o.halfH > h) { it.remove(); continue }
            // collision joueur : perd une vie + rejeté vers le bas (les obstacles bloquent)
            if (invuln <= 0f &&
                kotlin.math.abs(px - o.x) < o.halfW + playerR &&
                kotlin.math.abs(py - o.y) < o.halfH + playerR) {
                py = o.y + o.halfH + playerR + u * 2f
                playerHit()
            }
        }
    }

    private fun updatePlayerBullets(dt: Float) {
        val it = bullets.iterator()
        while (it.hasNext()) {
            val b = it.next()
            // tir guidé (BRAINROT) : oriente la vitesse vers l'ennemi le plus proche
            if (b.homing && enemies.isNotEmpty()) {
                val tgt = enemies.minByOrNull { e -> hypot(e.x - b.x, e.y - b.y) }
                if (tgt != null) {
                    val pi = PI.toFloat()
                    val desired = atan2(tgt.y - b.y, tgt.x - b.x)
                    val cur = atan2(b.vy, b.vx)
                    var diff = desired - cur
                    if (diff > pi) diff -= 2 * pi
                    if (diff < -pi) diff += 2 * pi
                    val maxTurn = 4.0f * dt
                    val na = cur + diff.coerceIn(-maxTurn, maxTurn)
                    val spd = hypot(b.vx, b.vy)
                    b.vx = cos(na) * spd; b.vy = sin(na) * spd
                }
            }
            b.x += b.vx * dt; b.y += b.vy * dt
            if (b.y < -u * 4f || b.y > h + u * 4f || b.x < -u * 4f || b.x > w + u * 4f) { it.remove(); continue }
            var hit = false
            // copie : damageEnemy -> killEnemy peut retirer un ennemi (dards perçants qui
            // continuent d'itérer sinon -> ConcurrentModificationException / crash abeille).
            for (e in enemies.toList()) {
                if (e.hp <= 0) continue
                when (e.kind) {
                    Kind.DRONE, Kind.INTERCEPTOR -> {
                        // Hitbox élargie pour COÏNCIDER avec le sprite dessiné (le rayon seul était
                        // bien plus petit que le sprite -> impression d'invincibilité). Rectangle sur
                        // toute la largeur. Vulnérabilité d'entrée : touchable une fois assez visible
                        // (fraction réglable dans les options).
                        val hw = e.radius * 1.5f
                        if (!e.vulnerable) continue
                        if (abs(b.x - e.x) < hw + b.r && b.y > e.y - hw && b.y < e.y + hw + b.r) {
                            damageEnemy(e, b.dmg, b.x, b.y)
                            if (!b.pierce) { hit = true; break }
                        }
                    }
                    Kind.MINIBOSS -> {
                        // Hitbox RECTANGULAIRE calquee EXACTEMENT sur le sprite dessine (dw x dh) :
                        // un cercle debordait des mid-boss larges/courts -> tirs dans le vide.
                        val hw = e.radius * 1.1f                                   // = dw/2 (voir drawMiniBoss)
                        val img = e.spriteImg
                        val hh = if (img != null) hw * img.height / img.width else hw
                        // Invincibilite d'entree (meme option que la pietaille).
                        if (!e.vulnerable) continue
                        if (abs(b.x - e.x) < hw + b.r && abs(b.y - e.y) < hh + b.r) {
                            damageEnemy(e, b.dmg, b.x, b.y)
                            if (!b.pierce) { hit = true; break }
                        }
                    }
                    else -> {
                        if (e.vulnerable && hypot(b.x - e.x, b.y - e.y) < e.radius + b.r) {
                            damageEnemy(e, b.dmg, b.x, b.y)
                            if (!b.pierce) { hit = true; break }
                        }
                    }
                }
            }
            // obstacles (murs / bâtiments) : les balles les endommagent aussi
            if (!hit) for (o in obstacles.toList()) {
                if (kotlin.math.abs(b.x - o.x) < o.halfW + b.r && kotlin.math.abs(b.y - o.y) < o.halfH + b.r) {
                    damageObstacle(o, b.dmg)
                    if (!b.pierce) { hit = true; break }
                }
            }
            if (hit) it.remove()
        }
    }

    private fun enemyColor(e: Enemy): Color = when (e.kind) {
        Kind.DRONE -> theme.droneColor
        Kind.INTERCEPTOR -> theme.interColor
        else -> CRIM
    }

    private fun damageEnemy(e: Enemy, dmg: Int, hx: Float = e.x, hy: Float = e.y) {
        e.hp -= dmg
        e.hitFlash = 0.07f
        // joli impact : éclat-étoile blanc au point de contact + gerbe d'étincelles
        // dorées/blanches courtes qui repartent vers le bas (rebond de la balle)
        sparks.add(HitSpark(hx, hy))
        repeat(4) {
            val a = -PI.toFloat() / 2f + (Random.nextFloat() - 0.5f) * 2.4f
            val sp2 = (Random.nextFloat() * 0.35f + 0.25f) * h * 0.7f
            particles.add(Particle(hx, hy, cos(a) * sp2 * 0.4f, -sin(a) * sp2 * 0.35f,
                0.12f + Random.nextFloat() * 0.10f, 0.22f,
                if (Random.nextFloat() < 0.5f) Color.White else GOLD, u * 0.7f))
        }
        if (e.hp <= 0) killEnemy(e)
    }

    /** Explosion en SPRITE IA (taille selon l'ennemi), tiree au sort. Fallback: rien (procedural gere ailleurs). */
    private fun spawnBurst(x: Float, y: Float, size: Float) {
        if (explosionSprites.isEmpty()) return
        spriteBursts.add(SpriteBurst(x, y, size, explosionSprites.random(), 0.42f))
    }

    private fun killEnemy(e: Enemy) {
        e.hp = 0
        val big = e.kind == Kind.BOSS || e.kind == Kind.MINIBOSS
        SoundEngine.playExplosion(big)
        // Explosion en sprite IA (remplace/complete le procedural). Taille selon l'ennemi.
        spawnBurst(e.x, e.y, e.radius * (if (big) 5.5f else 3.2f))
        if (big) {
            bigExplosion(e.x, e.y, if (e.trueLast) 1.9f else if (e.kind == Kind.BOSS) 1.6f else 1.1f)
            hitStop = if (e.trueLast) 0.18f else 0.12f
            flash = if (e.trueLast) 0.6f else 0.45f; flashCol = Color.White
            Haptics.heavy()
        } else {
            // « juice » : boule de feu + double anneau de choc + éclat + étincelles dorées
            spawnFireball(e.x, e.y, 22, e.radius * 0.6f)
            blasts.add(Blast(e.x, e.y, e.radius * 3.0f, Color.White, 0.22f, thick = 1.2f))
            blasts.add(Blast(e.x, e.y, e.radius * 4.8f, Color(0xFFFF8A3D), 0.42f, thick = 1.6f))
            sparks.add(HitSpark(e.x, e.y))
            spawnParticles(e.x, e.y, 10, GOLD, e.radius * 0.5f)
            if (!e.heavy) shake = max(shake, 5f)   // petit kick d'écran
        }
        // score + combo
        combo++
        comboTimer = 3f
        val newMult = min(8, 1 + combo / 5)
        if (newMult > mult && newMult >= 4 && mult < 4) { breakFlash = 0.6f; SoundEngine.playBreak() }
        mult = newMult
        val gain = e.score * mult
        if (mode.scored) score += gain
        texts.add(FloatText(e.x, e.y, "+$gain", 0.9f, if (mult >= 4) GOLD else CYAN_HOT))
        // Le mini-boss lâche TOUJOURS 1 power-up ; les autres sont distribués par le
        // directeur au fil du niveau (plus de drop aléatoire des ennemis normaux).
        if (e.kind == Kind.MINIBOSS) {
            powerups.add(PowerUp(e.x.coerceIn(u * 9f, w - u * 9f), e.y, h * 0.18f))
        }
        // pièces à récupérer
        val nCoins = when (e.kind) { Kind.BOSS -> 14; Kind.MINIBOSS -> 6; else -> Random.nextInt(1, 3) }
        repeat(nCoins) {
            coins.add(Coin(
                (e.x + (Random.nextFloat() - 0.5f) * e.radius).coerceIn(u * 6f, w - u * 6f),
                e.y + (Random.nextFloat() - 0.5f) * e.radius * 0.5f,
                h * (0.12f + Random.nextFloat() * 0.12f),
                (Random.nextFloat() - 0.5f) * h * 0.08f,
                randomCoinValue()
            ))
        }
        enemies.remove(e)
        if (e.kind == Kind.BOSS) { boss = null; bossDeathPos = Offset(e.x, e.y); deadBoss = e }
        // Mort d'un mini-boss : on aspire toutes les PIÈCES (pas les power-ups), façon bombe.
        if (e.kind == Kind.MINIBOSS) magnetCoins()
    }

    /** Aspire toutes les pièces vers le vaisseau (les power-ups NON). */
    fun magnetCoins() { for (c in coins) c.magnet = true }

    /** Grosse explosion à une position (utilisé pour la mort du boss). */
    fun bigExplosionAt(x: Float, y: Float) {
        bigExplosion(x, y, 1.3f)
    }

    private fun updateEnemies(dt: Float) {
        // On itère sur une COPIE : un mini-boss/boss peut appeler des renforts (enemies.add) pendant
        // sa mise à jour, ce qui faisait planter l'itérateur (ConcurrentModificationException).
        val snapshot = ArrayList(enemies)
        val it = snapshot.iterator()
        while (it.hasNext()) {
            val e = it.next()
            if (!enemies.contains(e)) continue
            e.t += dt
            if (e.hitFlash > 0f) e.hitFlash = max(0f, e.hitFlash - dt)
            // VERROU d'invincibilite d'entree : une fois assez visible, l'ennemi reste vulnerable
            // meme s'il repart vers un bord.
            if (!e.vulnerable && entryVisibleNow(e)) e.vulnerable = true
            if (e.side) {
                // ennemi entré par un côté (partie haute) : traverse en descendant un peu, tire
                e.entering = false
                e.x += e.vx * dt
                e.y += e.vy * dt + sin(e.t * 1.6f) * u * 2f * dt
                maybeFireAimed(e, dt, 2.6f, 1)
                if (e.x < -e.radius - u * 3f || e.x > w + e.radius + u * 3f || e.y > h + e.radius) enemies.remove(e)
                continue
            }
            if (e.cannon) {
                // gros canon : descend jusqu'à son ancrage puis reste fixe, tire droit vers le bas
                if (e.y < e.anchorY) e.y = min(e.anchorY, e.y + h * 0.12f * dt)
                else {
                    e.patCd -= dt * diffFire
                    if (e.patCd <= 0f) {
                        e.patCd = 1.4f + Random.nextFloat() * 0.6f
                        val sp = h * (diffBullet + 0.05f)
                        for (k in -1..1) eBullets.add(Bullet(e.x + k * u * 2f, e.y + u * 5f, 0f, sp, u * 1.9f, false, ORANGE))
                    }
                }
                continue
            }
            when (e.kind) {
                Kind.DRONE -> {
                    e.y += h * 0.20f * dt
                    e.x = e.baseX + sin(e.t * 2.2f + e.phase) * u * 10f
                    if (e.y > u * 8f) e.entering = false
                    maybeFireAimed(e, dt, 3.0f, 1)
                }
                Kind.INTERCEPTOR -> {
                    if (e.heavy) {
                        // lourd : descend lentement en oscillant à peine, tir lourd (3 balles)
                        e.y += h * 0.06f * dt
                        e.x = e.baseX + sin(e.t * 0.7f + e.phase) * u * 8f
                        if (e.y > u * 10f) e.entering = false
                        maybeFireAimed(e, dt, 2.0f, 3)
                    } else {
                        if (e.entering) {
                            e.y += h * 0.34f * dt
                            if (e.y > h * e.hoverFrac) { e.entering = false; e.vy = 0f; e.t = 0f }
                        } else {
                            e.x = e.baseX + sin(e.t * 1.6f + e.phase) * u * 16f
                            e.y += sin(e.t * 0.8f + e.phase) * u * 4f * dt
                        }
                        maybeFireAimed(e, dt, 2.4f, 1)
                    }
                }
                Kind.MINIBOSS -> updateMiniBoss(e, dt)
                Kind.BOSS -> updateBoss(e, dt)
            }
            if (e.y > h + e.radius + u * 2f && (e.kind == Kind.DRONE || e.heavy)) enemies.remove(e)
        }
    }

    private fun maybeFireAimed(e: Enemy, dt: Float, baseCd: Float, count: Int) {
        if (!e.canShoot) return                       // la majorité de l'essaim ne tire pas
        if (e.t < fireGrace) return                   // délai après apparition (laisse attaquer)
        // Vague B : un ennemi HORS CHAMP (caméra) ne tire pas — pas de balle surprise.
        if (e.x < camX - u * 4f || e.x > camX + viewW + u * 4f) return
        e.fireCd -= dt * diffFire
        // garde-fou : sur un gros essaim, on ne noie pas l'écran de balles
        if (eBullets.size > 130) { if (e.fireCd <= 0f) e.fireCd = baseCd + Random.nextFloat(); return }
        if (e.fireCd <= 0f && e.y < h * 0.7f && e.y > 0f) {
            e.fireCd = baseCd + Random.nextFloat()
            val ang = atan2(py - e.y, px - e.x)
            val sp = h * diffBullet
            if (count == 1) {
                eBullets.add(Bullet(e.x, e.y, cos(ang) * sp, sin(ang) * sp, u * 1.6f, false, PINK))
            } else {
                for (k in -1..1) {
                    val a = ang + k * 0.22f
                    eBullets.add(Bullet(e.x, e.y, cos(a) * sp, sin(a) * sp, u * 1.6f, false, PINK))
                }
            }
        }
    }

    private fun updateMiniBoss(e: Enemy, dt: Float) {
        if (e.entering) {
            e.y += h * 0.18f * dt
            if (e.y >= h * 0.18f) { e.entering = false; e.t = 0f } // repart du centre, sans saut
            return
        }
        // renforts périodiques mais PEU nombreux (variété pendant le mini-boss)
        miniEscortCd -= dt
        if (miniEscortCd <= 0f) {
            miniEscortCd = 3.2f + Random.nextFloat() * 1.5f
            spawnEscort(if (Random.nextBoolean()) Kind.DRONE else Kind.INTERCEPTOR)
        }
        // Mouvement propre à l'archétype (variante unique). cx = centre (décalé pour les paires).
        val yBase = h * 0.18f
        val cx = w / 2f + e.xOff
        when (e.variant % 6) {
            0 -> e.x = cx + sin(e.t * 0.9f) * w * 0.32f                                   // balayage large
            1 -> e.x = cx + sin(e.t * 0.5f) * w * 0.20f                                   // lent et posé
            2 -> e.x = cx + sin(e.t * 0.6f) * w * 0.10f                                   // quasi centré
            3 -> { e.x = cx + sin(e.t * 1.25f) * w * 0.30f; e.y = yBase + sin(e.t * 2.5f) * h * 0.045f } // en 8
            4 -> { e.x = cx + sin(e.t * 1.1f) * w * 0.26f; e.y = yBase + (0.5f + 0.5f * sin(e.t * 0.8f)) * h * 0.06f } // plongeur
            else -> e.x = cx + sin(e.t * 0.75f) * w * 0.34f                               // ample
        }
        val col = miniBossBulletOf(e.variant)
        e.patCd -= dt * diffFire
        if (e.patCd <= 0f) {
            val warm = when (e.volley) { 0 -> 0.6f; 1 -> 0.8f; else -> 1f }
            val sp = h * (diffBullet + 0.05f) * warm
            // Motif de tir propre à l'archétype
            when (e.variant % 6) {
                0 -> { e.patCd = 1.05f; fireAimed(e, if (e.volley == 0) 2 else 4, 0.19f, sp, col) }   // éventail visé
                1 -> { e.patCd = 1.5f;  fireWall(e, sp, col) }                                        // mur à trou
                2 -> { e.patCd = 0.9f;  fireRing(e, if (e.volley == 0) 8 else 14, sp * 0.82f, col, spin = e.t) } // anneau tournant
                3 -> { e.patCd = 0.26f; fireSpiral(e, 2, sp, col) }                                   // spirale continue
                4 -> { e.patCd = 0.8f;  fireAimed(e, 5, 0.12f, sp * 1.1f, col) }                      // rafale serrée
                else -> { e.patCd = 1.0f; fireAimed(e, 3, 0.22f, sp, col)
                    if (e.volley % 3 == 2) fireVortex(e, sp, Color(0xFF9A5CFF)) } // mixte + tourbillon
            }
            e.volley++
        }
    }

    // --- Motifs de tir de boss réutilisables ---
    private fun fireRing(e: Enemy, n: Int, sp: Float, color: Color, spin: Float = 0f) {
        for (i in 0 until n) {
            val a = (i.toFloat() / n) * 2f * Math.PI.toFloat() + spin
            eBullets.add(Bullet(e.x, e.y, cos(a) * sp, sin(a) * sp, u * 1.6f, false, color))
        }
    }
    private fun fireSpiral(e: Enemy, arms: Int, sp: Float, color: Color) {
        val base = e.t * 4.5f
        for (k in 0 until arms) {
            val a = base + k * (2f * Math.PI.toFloat() / arms)
            eBullets.add(Bullet(e.x, e.y, cos(a) * sp, sin(a) * sp, u * 1.5f, false, color))
        }
    }
    private fun fireAimed(e: Enemy, count: Int, spread: Float, sp: Float, color: Color) {
        val ang = atan2(py - e.y, px - e.x)
        val half = count / 2
        for (k in -half..half) {
            val a = ang + k * spread
            eBullets.add(Bullet(e.x, e.y, cos(a) * sp, sin(a) * sp, u * 1.7f, false, color))
        }
    }
    /** Projectile « trou noir / tourbillon » : gros, lent, visé — un boss et un mid-boss l'utilisent. */
    private fun fireVortex(e: Enemy, sp: Float, color: Color) {
        val ang = atan2(py - e.y, px - e.x)
        eBullets.add(Bullet(e.x, e.y, cos(ang) * sp * 0.55f, sin(ang) * sp * 0.55f,
            u * 4.2f, false, color, dmg = 1, vortex = true))
    }
    /** Salve de « dents de serpent » (crochets), visée vers le joueur — boss du niveau 2. */
    private fun fireFangs(e: Enemy, count: Int, spread: Float, sp: Float, color: Color) {
        val ang = atan2(py - e.y, px - e.x)
        val half = count / 2
        for (k in -half..half) {
            val a = ang + k * spread
            eBullets.add(Bullet(e.x, e.y, cos(a) * sp, sin(a) * sp, u * 2.3f, false, color, dmg = 1, fang = true))
        }
    }
    /** Mur de balles descendant avec un trou à esquiver, aligné sur le joueur. */
    private fun fireWall(e: Enemy, sp: Float, color: Color) {
        val cols = 12
        val gap = ((px / w) * cols).toInt().coerceIn(1, cols - 3)
        for (c in 0 until cols) {
            if (c == gap || c == gap + 1) continue
            val bx = w * (c + 0.5f) / cols
            eBullets.add(Bullet(bx, e.y + u * 4f, 0f, sp * 0.85f, u * 1.6f, false, color))
        }
    }

    private fun updateBoss(e: Enemy, dt: Float) {
        if (e.entering) {
            e.y += h * 0.12f * dt
            if (e.y >= h * 0.17f) { e.entering = false; e.t = 0f } // repart du centre, sans saut
            return
        }
        val frac = e.hp.toFloat() / e.maxHp
        // Vague C : transition de phase (intact -> abîmé -> monstrueux) bien marquée :
        // gros flash + secousse + purge des balles (respiration), puis le boss s'énerve.
        val phaseNow = when { frac > 0.66f -> 0; frac > 0.33f -> 1; else -> 2 }
        if (phaseNow > e.phaseSeen) {
            e.phaseSeen = phaseNow
            shake = max(shake, 16f)
            flash = max(flash, 0.5f); flashCol = if (phaseNow == 2) PHASE_RED else ORANGE
            spawnFireball(e.x, e.y, 26, u * 22f)
            blasts.add(Blast(e.x, e.y, u * 30f, if (phaseNow == 2) PHASE_RED else ORANGE, 0.6f, thick = 1.8f))
            SoundEngine.playExplosion(true)
            eBullets.clear()                     // l'écran se vide : lisible + gratifiant
            e.patCd = max(e.patCd, 1.0f)         // petite pause avant la phase suivante
        }
        // le boss balaie plus vite à chaque phase
        e.x = w / 2f + sin(e.t * (0.5f + e.phaseSeen * 0.18f)) * w * 0.26f
        val rage = frac < 0.4f
        e.patCd -= dt * diffFire
        if (e.patCd > 0f) return
        if (eBullets.size > 420) { e.patCd = 0.25f; return } // garde-fou anti-surcharge
        // Montée en puissance : les 2 premières salves sont nettement adoucies
        // (balles plus lentes et moins nombreuses) → le boss « s'échauffe ».
        val warm = when (e.volley) { 0 -> 0.55f; 1 -> 0.78f; else -> 1f }
        fun wn(n: Int) = max(4, (n * warm).toInt())
        val sp = h * (diffBullet + 0.04f) * warm

        if (e.trueLast) { // boss final caché : tout à la fois
            e.patCd = if (rage) 0.55f else 0.8f
            fireRing(e, wn(if (rage) 30 else 22), sp, GOLD, spin = e.t)
            fireSpiral(e, 4, sp * 1.1f, CYAN_HOT)
            fireAimed(e, 3, 0.12f, sp * 1.15f, Color(0xFFFF3B3B))
            e.volley++
            return
        }

        // Un motif d'attaque distinct par niveau
        when (level) {
            1 -> { // salves visées ESPACÉES (1er boss = apprentissage), petit anneau en rage
                e.patCd = if (rage) 1.0f else 1.4f
                fireAimed(e, 3, 0.24f, sp * 0.9f, PINK)
                if (rage) fireRing(e, wn(10), sp * 0.8f, CRIM)
            }
            2 -> { // spirale toxique + salves de DENTS DE SERPENT visées
                e.patCd = if (e.volley < 2) 0.55f else if (rage) 0.16f else 0.24f
                fireSpiral(e, if (rage) 4 else 3, sp, E_GRN)
                // une salve de crochets sur ~1 volée sur 3 (plus dense en rage)
                if (e.volley % 3 == 0) fireFangs(e, if (rage) 5 else 3, 0.16f, sp * 1.15f, Color(0xFFEAF3B0))
            }
            3 -> { // murs de glace troués
                e.patCd = if (rage) 0.9f else 1.3f
                fireWall(e, sp * 0.9f, E_ICE)
                if (rage) fireAimed(e, 3, 0.10f, sp, E_ICEW)
            }
            4 -> { // larges rafales de lave rapides + tourbillon lent qui aspire l'attention
                e.patCd = if (rage) 0.5f else 0.8f
                fireAimed(e, wn(7), 0.13f, sp * 1.1f, E_LAVA)
                if (e.volley % 4 == 1) fireVortex(e, sp, Color(0xFF9A5CFF))
            }
            else -> { // niveau 5 : anneaux tournants + visé
                e.patCd = if (rage) 0.55f else 0.85f
                fireRing(e, wn(if (rage) 26 else 18), sp, E_VIO, spin = e.t * 1.5f)
                fireAimed(e, 3, 0.12f, sp * 1.1f, E_MAG)
            }
        }
        e.volley++
        // Phase critique (3e phase, < 25 % PV) : cadence accrue + anneau de rage supplémentaire.
        // Pas au niveau 1 (boss d'apprentissage) : il saturait l'écran, injouable.
        if (frac < 0.25f && level >= 2) {
            e.patCd *= 0.75f
            fireRing(e, 9, sp * 0.8f, PHASE_RED, spin = -e.t)
        }
    }

    private fun updateEnemyBullets(dt: Float) {
        var hit = false
        val it = eBullets.iterator()
        while (it.hasNext()) {
            val b = it.next()
            b.x += b.vx * dt; b.y += b.vy * dt
            if (b.y > h + u * 4f || b.y < -u * 6f || b.x < -u * 6f || b.x > w + u * 6f) { it.remove(); continue }
            if (invuln <= 0f && !hit && hypot(b.x - px, b.y - py) < b.r + u * 2.4f) {
                it.remove()
                hit = true // on gère l'impact APRÈS la boucle (playerHit modifie eBullets)
            }
        }
        if (hit) playerHit()
    }

    private fun updatePowerups(dt: Float) {
        val it = powerups.iterator()
        while (it.hasNext()) {
            val p = it.next()
            if (p.magnet) {
                val a = atan2(py - p.y, px - p.x)
                val sp = h * 1.5f
                p.x += cos(a) * sp * dt
                p.y += sin(a) * sp * dt
            } else if (p.blackhole) {
                // Le tourbillon REBONDIT sur les 4 bords (façon DVD) au lieu de tomber.
                p.x += p.vx * dt; p.y += p.vy * dt
                if (p.x < u * 4f) { p.x = u * 4f; p.vx = kotlin.math.abs(p.vx) }
                if (p.x > w - u * 4f) { p.x = w - u * 4f; p.vx = -kotlin.math.abs(p.vx) }
                if (p.y < u * 4f) { p.y = u * 4f; p.vy = kotlin.math.abs(p.vy) }
                if (p.y > h - u * 4f) { p.y = h - u * 4f; p.vy = -kotlin.math.abs(p.vy) }
            } else {
                p.y += p.vy * dt
            }
            if (!p.blackhole && p.y > h + u * 3f) { it.remove(); continue }
            val grab = (if (p.giant) u * 6f else u * 5f) * (if (giantT > 0f) 3f else 1f)
            if (hypot(p.x - px, p.y - py) < grab) {
                it.remove()
                when {
                    p.giant -> activateGiant()
                    p.blackhole -> activateBlackhole()
                    p.mega -> {
                        power = 8
                        SoundEngine.playPowerUp(); SoundEngine.playBreak()
                        texts.add(FloatText(px, py - u * 6f, "MAX POWER!", 1.3f, GOLD))
                        flash = 0.5f; flashCol = GOLD
                    }
                    else -> {
                        power = min(8, power + 1)
                        SoundEngine.playPowerUp()
                        texts.add(FloatText(px, py - u * 6f, "POWER UP!", 1.1f, GREEN, powerup = true))
                    }
                }
            }
        }
    }

    /** Item géant : le vaisseau triple de taille, devient invincible et tire des rayons (2 s). */
    private fun activateGiant() {
        giantT = 3.5f                       // 1,5 s de plus qu'avant
        giantRayCd = 0f
        invuln = max(invuln, 3.5f)
        SoundEngine.playPowerUp(); SoundEngine.playBreak()
        texts.add(FloatText(px, py - u * 7f, "GIANT!", 1.4f, CYAN_HOT))
        flash = 0.5f; flashCol = CYAN
        shake = max(shake, 18f)
    }

    /** Rayons + invincibilité pendant que l'item géant est actif (80 % de la largeur d'écran). */
    private fun updateGiant(dt: Float) {
        if (giantT <= 0f) return
        giantT = max(0f, giantT - dt)
        invuln = max(invuln, 0.12f)          // reste invincible tant que le mode est actif
        val bandHalf = w * 0.40f              // rayons couvrant 80 % de l'écran
        // efface les balles ennemies dans la bande
        eBullets.removeAll { abs(it.x - px) < bandHalf }
        giantRayCd -= dt
        if (giantRayCd <= 0f) {
            giantRayCd = 0.07f
            for (e in enemies.toList()) if (e.hp > 0 && abs(e.x - px) < bandHalf) damageEnemy(e, 3, e.x, e.y)
            for (o in obstacles.toList()) if (abs(o.x - px) < bandHalf) damageObstacle(o, 3)
        }
    }

    /** Item trou noir : aspire toutes les pièces + power-ups et donne un bonus de points. */
    private fun activateBlackhole() {
        for (c in coins) c.magnet = true
        for (p in powerups) p.magnet = true
        magnetAllItems()
        blackholeT = 0.9f
        if (mode.scored) score += 800
        SoundEngine.playBreak()
        texts.add(FloatText(px, py - u * 7f, "+800", 1.3f, GOLD))
        shake = max(shake, 14f)
    }

    /** Lâche l'item géant (un par stage, tombe du haut au centre). */
    fun dropGiant() {
        if (giantSpawned) return
        giantSpawned = true
        powerups.add(PowerUp(w / 2f, -u * 4f, h * 0.15f).also { it.giant = true })
    }

    /** Lâche un item trou noir (tombe du haut, position aléatoire). */
    fun dropBlackhole() {
        val x = (u * 9f + Random.nextFloat() * (w - u * 18f)).coerceIn(u * 9f, w - u * 9f)
        powerups.add(PowerUp(x, u * 8f, h * 0.22f).also {
            it.blackhole = true
            it.vx = (if (Random.nextBoolean()) 1f else -1f) * h * 0.20f   // rebondit en diagonale
        })
    }

    /** Ramasse une pièce : compteur, score, son, texte flottant + suivi par type (récap). */
    private fun collectCoin(c: Coin) {
        // Valeur réglable dans le menu configuration (multiplicateur des trésors).
        val gain = (c.value * com.goodbrainrot.app.Settings.shmupTreasureMult.floatValue).roundToInt().coerceAtLeast(1)
        coinCount += gain
        coinsByValue[c.value] = (coinsByValue[c.value] ?: 0) + 1
        if (mode.scored) score += gain
        SoundEngine.playCoin(coinTier(c.value))
        texts.add(FloatText(c.x, c.y - u * 2f, "$gain$", 0.9f, coinColor(c.value), coin = true))
    }

    private fun updateCoins(dt: Float) {
        val it = coins.iterator()
        while (it.hasNext()) {
            val c = it.next()
            c.t += dt
            if (c.magnet) {
                // aspirée vers le vaisseau (bombe) : fonce droit sur le joueur
                val a = atan2(py - c.y, px - c.x)
                val sp = h * 1.6f
                c.x += cos(a) * sp * dt
                c.y += sin(a) * sp * dt
            } else {
                c.x = (c.x + c.vx * dt).coerceIn(u * 6f, w - u * 6f)
                c.y += c.vy * dt
            }
            if (c.y > h + u * 3f) { it.remove(); continue }
            // Zone de ramassage AGRANDIE quand le vaisseau est géant (×3, cohérent avec sa taille).
            if (hypot(c.x - px, c.y - py) < u * 6f * (if (giantT > 0f) 3f else 1f)) {
                it.remove()
                collectCoin(c)
            }
        }
    }

    private fun playerHit() {
        SoundEngine.playPlayerHit()
        Haptics.hit()                   // vibration DISTINCTE (double coup) = « touché »
        perfect = false                 // dégât encaissé → run plus « parfait » (bloque le niveau caché)
        stageHitless = false            // ce stage n'est plus « PERFECT »
        shake = max(shake, 26f)         // l'écran tremble quand on est touché
        spawnParticles(px, py, 30, CYAN, u * 0.7f)
        shake = 26f
        hitStop = 0.10f
        flash = 0.5f; flashCol = Color(0xFFFF3B3B)
        invuln = 1.6f
        combo = 0; mult = 1; comboTimer = 0f
        // nettoie les balles proches (répit)
        eBullets.removeAll { hypot(it.x - px, it.y - py) < u * 26f }
        if (mode.scored) {
            lives--
            if (lives <= 0) gameOver = true
        }
    }

    private fun spawnParticles(x: Float, y: Float, count: Int, color: Color, spread: Float) {
        repeat(count) {
            val a = Random.nextFloat() * 2f * Math.PI.toFloat()
            val sp = (Random.nextFloat() * 0.5f + 0.2f) * h * 0.6f
            particles.add(Particle(x, y, cos(a) * sp, sin(a) * sp,
                Random.nextFloat() * 0.4f + 0.3f, 0.7f,
                if (Random.nextFloat() < 0.4f) GOLD else color,
                (Random.nextFloat() * 1.4f + 0.8f) * spread + u))
        }
    }

    /** Explosion « boule de feu » (blanc → jaune → orange → rouge) façon CAVE. */
    private fun spawnFireball(x: Float, y: Float, count: Int, spread: Float) {
        repeat(count) {
            val a = Random.nextFloat() * 2f * Math.PI.toFloat()
            val sp = (Random.nextFloat() * 0.6f + 0.12f) * h * 0.55f
            particles.add(Particle(x, y, cos(a) * sp, sin(a) * sp,
                Random.nextFloat() * 0.45f + 0.3f, 0.78f,
                FIRE_COLORS[Random.nextInt(FIRE_COLORS.size)],
                (Random.nextFloat() * 1.8f + 1.0f) * spread + u))
        }
    }

    /**
     * Grosse explosion spectaculaire (boss / vaisseau) : flash blanc, anneaux de choc,
     * boule de feu dense, éclats brûlants qui fusent, débris avec gravité, fumée qui monte.
     * [scale] ~1 = mini-boss, ~1.6 = boss, ~2 = boss final.
     */
    fun bigExplosion(x: Float, y: Float, scale: Float) {
        val base = u * 3.5f * scale
        // flash + anneaux de choc (blanc rapide, puis orange plus large)
        blasts.add(Blast(x, y, base * 3.6f, Color.White, 0.32f, thick = 0.9f))
        blasts.add(Blast(x, y, base * 5.4f, Color(0xFFFF8A3D), 0.55f, thick = 1.5f))
        blasts.add(Blast(x, y, base * 7.2f, Color(0xFFFF3B2A), 0.75f, thick = 1.0f))
        // cœur de feu dense
        spawnFireball(x, y, (34 * scale).toInt(), u * 1.2f * scale)
        // éclats brûlants qui fusent loin
        repeat((22 * scale).toInt()) {
            val a = Random.nextFloat() * 2f * Math.PI.toFloat()
            val sp = (Random.nextFloat() * 0.6f + 0.5f) * h * 0.9f
            particles.add(Particle(x, y, cos(a) * sp, sin(a) * sp,
                0.3f + Random.nextFloat() * 0.4f, 0.7f,
                if (Random.nextFloat() < 0.4f) Color.White else GOLD, u * (0.7f + Random.nextFloat())))
        }
        // débris sombres avec gravité
        repeat((10 * scale).toInt()) {
            val a = -PI.toFloat() / 2f + (Random.nextFloat() - 0.5f) * 3f
            val sp = (Random.nextFloat() * 0.5f + 0.4f) * h * 0.7f
            particles.add(Particle(x, y, cos(a) * sp, sin(a) * sp,
                0.7f + Random.nextFloat() * 0.5f, 1.2f,
                Color(0xFF2A1310), u * (1.4f + Random.nextFloat() * 1.6f) * scale))
        }
        // fumée qui monte lentement (gris, longue vie)
        repeat((8 * scale).toInt()) {
            particles.add(Particle(
                x + (Random.nextFloat() - 0.5f) * base * 2f,
                y + (Random.nextFloat() - 0.5f) * base,
                (Random.nextFloat() - 0.5f) * h * 0.08f, -h * (0.05f + Random.nextFloat() * 0.05f),
                1.0f + Random.nextFloat() * 0.8f, 1.8f,
                Color(0x66403A44), u * (3f + Random.nextFloat() * 3f) * scale))
        }
        shake = max(shake, 28f * scale)
    }

    /** Le vaisseau du joueur explose (game over) puis disparaît. */
    fun explodePlayer() {
        if (playerDead) return
        playerDead = true
        bigExplosion(px, py, 1.5f)
        SoundEngine.playExplosion(true)
        flash = 0.7f; flashCol = Color.White
        shake = 40f
        Haptics.heavy()
    }

    // --- spawns (appelés par le directeur) ---
    fun spawnLine(kind: Kind, count: Int) {
        val hp = (if (kind == Kind.INTERCEPTOR) 3 else 2)
        val score = if (kind == Kind.INTERCEPTOR) 150 else 100
        val r = u * 3.6f
        for (i in 0 until count) {
            val x = w * (i + 1f) / (count + 1f)
            val e = Enemy(x, -u * (6f + Random.nextFloat() * 10f),
                max(1, (hp * diffHp).toInt()), max(1, (hp * diffHp).toInt()), kind, score, r)
            e.baseX = x
            tagEnemy(e); enemies.add(e)
        }
    }

    /**
     * Gros essaim : [count] petits ennemis (jusqu'à ~100) répartis en grille au-dessus
     * de l'écran, qui déferlent en cascade. Satisfaisant à pulvériser.
     */
    fun spawnSwarm(kind: Kind, count: Int) {
        val hp = if (kind == Kind.INTERCEPTOR) 3 else 2
        val score = if (kind == Kind.INTERCEPTOR) 150 else 100
        val r = u * 3.6f
        val cols = when { count <= 10 -> count.coerceAtLeast(1); count <= 40 -> 8; else -> 10 }
        val spacing = w / (cols + 1f)
        val rowGap = u * 9.5f                       // plus d'écart vertical -> arrivée plus étalée
        val hpv = max(1, (hp * diffHp).toInt())
        for (i in 0 until count) {
            val col = i % cols
            val row = i / cols
            val jx = (Random.nextFloat() - 0.5f) * spacing * 0.45f
            val x = (spacing * (col + 1f) + jx).coerceIn(u * 5f, w - u * 5f)
            val y = -u * 8f - row * rowGap - Random.nextFloat() * u * 3f
            val e = Enemy(x, y, hpv, hpv, kind, score, r)
            e.baseX = x
            e.canShoot = Random.nextFloat() < swarmShootFrac   // seule une fraction tire
            tagEnemy(e); enemies.add(e)
        }
    }

    /** Essaim MIXTE (drones + intercepteurs mélangés) : plus de diversité visuelle. */
    fun spawnMixedSwarm(count: Int) {
        val cols = when { count <= 10 -> count.coerceAtLeast(1); count <= 40 -> 8; else -> 10 }
        val spacing = w / (cols + 1f)
        val rowGap = u * 9.5f
        for (i in 0 until count) {
            val kind = if (Random.nextFloat() < 0.68f) Kind.DRONE else Kind.INTERCEPTOR
            val hpv = max(1, ((if (kind == Kind.INTERCEPTOR) 3 else 2) * diffHp).toInt())
            val score = if (kind == Kind.INTERCEPTOR) 150 else 100
            val col = i % cols; val row = i / cols
            val jx = (Random.nextFloat() - 0.5f) * spacing * 0.45f
            val x = (spacing * (col + 1f) + jx).coerceIn(u * 5f, w - u * 5f)
            val y = -u * 8f - row * rowGap - Random.nextFloat() * u * 3f
            val e = Enemy(x, y, hpv, hpv, kind, score, u * 3.6f)
            e.baseX = x
            e.canShoot = Random.nextFloat() < swarmShootFrac
            tagEnemy(e); enemies.add(e)
        }
    }

    /** Formation en V/arc : la pointe (centre) entre en premier. */
    fun spawnArc(kind: Kind, count: Int) {
        val hp = max(1, ((if (kind == Kind.INTERCEPTOR) 3 else 2) * diffHp).toInt())
        val score = if (kind == Kind.INTERCEPTOR) 150 else 100
        for (i in 0 until count) {
            val frac = if (count <= 1) 0.5f else i.toFloat() / (count - 1)
            val x = w * (0.12f + 0.76f * frac)
            val dip = sin(frac * Math.PI.toFloat())          // 0 aux bords, 1 au centre
            val y = -u * 8f - (1f - dip) * u * 18f            // le centre plonge en premier (V)
            val e = Enemy(x, y, hp, hp, kind, score, u * 3.6f)
            e.baseX = x
            e.canShoot = Random.nextFloat() < swarmShootFrac
            tagEnemy(e); enemies.add(e)
        }
    }

    /** Nombre d'ennemis « piétaille » encore à l'écran (pour rythmer les vagues). */
    fun gruntCount(): Int = enemies.count { it.kind == Kind.DRONE || it.kind == Kind.INTERCEPTOR }

    /** Ennemis LOURDS : peu nombreux, gros, beaucoup de PV, descendent lentement en tirant. */
    fun spawnHeavies(count: Int) {
        val r = u * 6.5f
        val hp = max(1, (16 * diffHp).toInt())
        for (i in 0 until count.coerceAtMost(4)) {
            val x = w * (i + 1f) / (count + 1f)
            val e = Enemy(x, -u * (10f + i * 9f), hp, hp, Kind.INTERCEPTOR, 600, r)
            e.baseX = x; e.heavy = true; e.canShoot = true
            tagEnemy(e); enemies.add(e)
        }
    }

    /** Gros canons FIXES qui descendent puis se figent en haut et tirent tout droit vers le bas. */
    fun spawnCannons(count: Int) {
        val hp = max(1, (14 * diffHp).toInt())
        val n = count.coerceIn(1, 3)
        for (i in 0 until n) {
            val x = w * (i + 1f) / (n + 1f)
            val e = Enemy(x, -u * 8f, hp, hp, Kind.INTERCEPTOR, 500, u * 7f)
            e.baseX = x; e.cannon = true; e.canShoot = true
            e.anchorY = h * (0.12f + Random.nextFloat() * 0.14f)
            e.patCd = 1f + Random.nextFloat()
            if (cannonSprites.isNotEmpty()) e.spriteImg = cannonSprites.random()
            enemies.add(e)
        }
    }

    /** Petit groupe d'ennemis qui entrent par les CÔTÉS (moitié haute de l'écran).
     *  Vague B : monde élargi -> bande un peu plus profonde et jusqu'à 6 ennemis. */
    fun spawnFromSides(count: Int) {
        val hp = max(1, (2 * diffHp).toInt())
        for (i in 0 until count.coerceAtMost(6)) {
            val fromLeft = Random.nextBoolean()
            val y = h * (0.06f + Random.nextFloat() * 0.32f)
            val x = if (fromLeft) -u * 6f else w + u * 6f
            val e = Enemy(x, y, hp, hp, Kind.INTERCEPTOR, 120, u * 3.6f)
            e.baseX = x
            e.side = true
            e.vx = if (fromLeft) h * 0.10f else -h * 0.10f
            e.vy = h * 0.03f
            e.canShoot = Random.nextFloat() < 0.5f
            tagEnemy(e); enemies.add(e)
        }
    }

    /** Lâche un power-up qui descend depuis le haut (distribution pilotée par le directeur). */
    fun dropPowerUp() {
        val x = u * 9f + Random.nextFloat() * (w - u * 18f)
        powerups.add(PowerUp(x, -u * 4f, h * 0.2f))
    }

    /** Super power-up (après un continue) : met directement au niveau MAX quand ramassé. */
    fun dropMegaPowerUp() {
        powerups.add(PowerUp(w / 2f, -u * 4f, h * 0.16f).also { it.mega = true })
    }

    /** Renfort léger pendant un combat de mini-boss (peu nombreux). */
    fun spawnEscort(kind: Kind) {
        if (gruntCount() > 6) return
        val hp = max(1, ((if (kind == Kind.INTERCEPTOR) 3 else 2) * diffHp).toInt())
        val x = u * 8f + Random.nextFloat() * (w - u * 16f)
        val e = Enemy(x, -u * 6f, hp, hp, kind, if (kind == Kind.INTERCEPTOR) 150 else 100, u * 3.6f)
        e.baseX = x; e.canShoot = Random.nextFloat() < 0.4f
        tagEnemy(e); enemies.add(e)
    }

    fun spawnMiniBoss() {
        // +25 % de vie (retour utilisateur) + il doit avoir le temps de faire >= 2 attaques
        val hp = (110 * diffHp * bossHpMul).toInt()
        val idx = miniBossInLevel
        val v = (level - 1) * 2 + miniBossInLevel   // variante UNIQUE sur tout le run
        miniBossInLevel++
        // Echelle (option Taille mid-boss) bakee dans le rayon : rendu ET hitbox derivent du meme
        // rayon -> impossible qu'ils divergent. Regle l'echelle AVANT que le mid-boss apparaisse.
        val mbSc = com.goodbrainrot.app.Settings.midBossScaleLive.coerceIn(1f, 10f)
        val r = u * (8.2f + (v % 3) * 1.1f) * mbSc
        val e = Enemy(w / 2f, -u * 14f, hp, hp, Kind.MINIBOSS, 3000 + v * 200, r)
        e.variant = v
        if (midBossSprites.isNotEmpty()) e.spriteImg = midBossSprites[idx % midBossSprites.size]
        e.patCd = 0.6f   // première attaque rapide après l'entrée
        miniEscortCd = 3.5f
        enemies.add(e); boss = e
        bossWarn = 1.3f; bossWarnBoss = false
        Haptics.heavy()
        SoundEngine.playCaution()       // mini-boss = alarme CAUTION (distincte du WARNING boss)
    }

    /** Niveau bonus : DEUX mid-boss simultanés (placés symétriquement, un peu moins de PV chacun). */
    fun spawnMiniBossPair() {
        val mbSc = com.goodbrainrot.app.Settings.midBossScaleLive.coerceIn(1f, 10f)
        for (s in intArrayOf(-1, 1)) {
            val hp = (85 * diffHp * bossHpMul).toInt()
            val idx = miniBossInLevel; miniBossInLevel++
            val e = Enemy(w * (0.5f + s * 0.25f), -u * 14f, hp, hp, Kind.MINIBOSS, 3000, u * 7.2f * mbSc)
            e.variant = 2                       // archétype « quasi centré » (petite amplitude)
            e.xOff = s * w * 0.25f              // centre décalé -> pas de chevauchement
            if (midBossSprites.isNotEmpty()) e.spriteImg = midBossSprites[idx % midBossSprites.size]
            e.patCd = 0.6f + (if (s < 0) 0f else 0.35f)
            e.entering = true
            enemies.add(e)
        }
        boss = enemies.lastOrNull { it.kind == Kind.MINIBOSS }
        bossWarn = 1.3f; bossWarnBoss = false
        Haptics.heavy(); SoundEngine.playCaution()
    }

    /** Y a-t-il encore un mid-boss vivant (pour attendre la fin d'une paire) ? */
    fun miniBossAlive(): Boolean = enemies.any { it.kind == Kind.MINIBOSS && it.hp > 0 }

    fun spawnBoss() {
        // assez de vie pour tenir au-delà des 2 salves d'échauffement, même au laser
        val hp = (420 * diffHp * bossHpMul).toInt()
        val e = Enemy(w / 2f, -u * 22f, hp, hp, Kind.BOSS, 12000, u * 15f)
        e.patCd = 1.2f
        enemies.add(e); boss = e
        bossWarn = 1.3f; bossWarnBoss = true
        Haptics.heavy()
        SoundEngine.playBossWarning()
    }

    /** Boss final caché (niveau 6) : très résistant, patterns renforcés, look doré. */
    fun spawnTrueLastBoss() {
        val hp = (900 * diffHp * bossHpMul).toInt()
        val e = Enemy(w / 2f, -u * 28f, hp, hp, Kind.BOSS, 30000, u * 18f)
        e.patCd = 2f; e.trueLast = true
        enemies.add(e); boss = e
        bossWarn = 1.3f; bossWarnBoss = true
        Haptics.heavy()
        SoundEngine.playBossWarning()
    }
}

// ---------------------------------------------------------------------------
// Écran principal
// ---------------------------------------------------------------------------

@Composable
fun ShmupScreen(onBack: () -> Unit) {
    var phase by remember { mutableStateOf(ShPhase.CINEMA) }
    var mode by remember { mutableStateOf(ShMode.NORMAL) }
    var ship by remember { mutableStateOf(ShShip.VULCAN) }
    var runToken by remember { mutableIntStateOf(0) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var frameTick by remember { mutableIntStateOf(0) }
    var clearBanner by remember { mutableStateOf(false) }   // "STAGE CLEAR" pendant l'envol
    var clearPerfect by remember { mutableStateOf(false) }
    var pendingLevel by remember { mutableIntStateOf(1) }   // niveau à lancer après le récap de stage
    var finalToCongrats by remember { mutableStateOf(false) } // récap final -> écran de remerciements
    var outroLogo by remember { mutableStateOf(false) }        // écran logo GoodBrainRot (après l'image de fin)
    var showOptions by remember { mutableStateOf(false) }   // overlay réglages (volumes)
    var pendingMega by remember { mutableStateOf(false) }    // super power-up à lâcher après un continue

    // HUD mirroré depuis le jeu
    var hudScore by remember { mutableIntStateOf(0) }
    var hudLives by remember { mutableIntStateOf(3) }
    var hudMult by remember { mutableIntStateOf(1) }
    var hudPower by remember { mutableIntStateOf(1) }
    var hudBombs by remember { mutableIntStateOf(3) }
    var hudCoins by remember { mutableIntStateOf(0) }
    var bossFrac by remember { mutableFloatStateOf(-1f) }
    var hudBossWarn by remember { mutableStateOf(false) }
    var hudBossWarnBoss by remember { mutableStateOf(false) }

    // Progression multi-niveaux : état transmis d'un niveau au suivant
    var level by remember { mutableIntStateOf(1) }
    var carryLives by remember { mutableIntStateOf(3) }
    var carryPower by remember { mutableIntStateOf(1) }
    var carryBombs by remember { mutableIntStateOf(3) }
    var carryScore by remember { mutableIntStateOf(0) }
    var carryCoins by remember { mutableIntStateOf(0) }
    var carryPerfect by remember { mutableStateOf(true) }
    var trueEnd by remember { mutableStateOf(false) } // vrai si le boss final caché a été battu
    var credits by remember { mutableIntStateOf(3) }   // crédits d'arcade (continues restants)
    var startLevel by remember { mutableIntStateOf(1) } // niveau de départ choisi (gain de temps)
    var scoreTimeLeft by remember { mutableFloatStateOf(SCORE_ATTACK_S) }   // chrono du mode SCORE ATTACK
    // la clé de tableau des scores dépend du mode (le score attack a le sien)
    val scoreKey = if (mode == ShMode.SCORE) HighScores.Games.SHMUP_TIMED else SHMUP_KEY

    // Remet l'état de run à zéro. [keepLevel] = on rejoue le niveau courant (continue).
    fun resetRunState(keepLevel: Boolean = false) {
        if (!keepLevel) level = startLevel
        carryLives = if (mode.scored) com.goodbrainrot.app.Settings.shmupLives.intValue else mode.lives
        carryPower = 1; carryBombs = 3
        carryScore = 0; carryCoins = 0            // le score repart TOUJOURS de zéro
        carryPerfect = if (keepLevel) false else true
        runToken++; phase = ShPhase.INTRO
    }
    // Nouvelle partie (depuis le menu) : recharge les 3 crédits, repart au niveau 1.
    fun startRun() { credits = 3; resetRunState() }
    // Continuer après une mort : consomme un crédit, REPREND au niveau où on était.
    fun continueRun() { if (credits > 0) { credits--; pendingMega = true; resetRunState(keepLevel = true) } }

    // Logo de l'app rendu en bitmap (pour le vaisseau BRAINROT dans le Canvas)
    val ctx = LocalContext.current
    if (shmupPixelTypeface == null) {
        shmupPixelTypeface = try { androidx.core.content.res.ResourcesCompat.getFont(ctx, R.font.silkscreen) } catch (_: Exception) { null }
    }
    val logo = remember {
        try {
            val d = androidx.appcompat.content.res.AppCompatResources.getDrawable(ctx, R.drawable.logo_brain)
            if (d == null) null else {
                val sz = 128
                val bmp = android.graphics.Bitmap.createBitmap(sz, sz, android.graphics.Bitmap.Config.ARGB_8888)
                val cv = android.graphics.Canvas(bmp)
                d.setBounds(0, 0, sz, sz)
                d.draw(cv)
                bmp.asImageBitmap()
            }
        } catch (_: Exception) { null }
    }

    // Sprites PNG (CC0 Kenney Pixel Shmup) — cachés par imageResource
    // Fonds de stage pixel-art (IA) pour les 6 stages.
    val stageBg = listOf(
        ImageBitmap.imageResource(R.drawable.cs_bg1),
        ImageBitmap.imageResource(R.drawable.cs_bg2),
        ImageBitmap.imageResource(R.drawable.cs_bg3),
        ImageBitmap.imageResource(R.drawable.cs_bg4),
        ImageBitmap.imageResource(R.drawable.cs_bg5),
        ImageBitmap.imageResource(R.drawable.cs_bg6)
    )
    // Projectiles ennemis IA (cs_proj_1..20) : chargés dynamiquement.
    val projIds = (1..20).mapNotNull { i ->
        val id = ctx.resources.getIdentifier("cs_proj_$i", "drawable", ctx.packageName)
        if (id != 0) id else null
    }
    val projSpritesList = projIds.map { ImageBitmap.imageResource(it) }
    // Explosions IA (cs_expl_1..12) + police pieces (cs_pf_0..10) : chargees dynamiquement.
    val explList = (1..12).mapNotNull { i ->
        val id = ctx.resources.getIdentifier("cs_expl_$i", "drawable", ctx.packageName)
        if (id != 0) id else null
    }.map { ImageBitmap.imageResource(it) }
    val coinFontList = (0..10).mapNotNull { i ->
        val id = ctx.resources.getIdentifier("cs_pf_$i", "drawable", ctx.packageName)
        if (id != 0) id else null
    }.map { ImageBitmap.imageResource(it) }
    // Vague C : variantes de phase des boss (boss_s{n}_p2 / _p3), nullables si pas générées.
    val bossP2List = (1..5).map { i ->
        val id = ctx.resources.getIdentifier("boss_s${i}_p2", "drawable", ctx.packageName)
        if (id != 0) ImageBitmap.imageResource(id) else null
    }
    val bossP3List = (1..5).map { i ->
        val id = ctx.resources.getIdentifier("boss_s${i}_p3", "drawable", ctx.packageName)
        if (id != 0) ImageBitmap.imageResource(id) else null
    }
    val sprites = ShSprites(
        ImageBitmap.imageResource(R.drawable.ship_red),
        ImageBitmap.imageResource(R.drawable.ship_green),
        ImageBitmap.imageResource(R.drawable.ship_blue),
        ImageBitmap.imageResource(R.drawable.ship_bee),
        ImageBitmap.imageResource(R.drawable.enemy_a),
        ImageBitmap.imageResource(R.drawable.enemy_b),
        ImageBitmap.imageResource(R.drawable.miniboss_s),
        ImageBitmap.imageResource(R.drawable.boss_s1),
        ImageBitmap.imageResource(R.drawable.boss_s2),
        ImageBitmap.imageResource(R.drawable.boss_s3),
        ImageBitmap.imageResource(R.drawable.boss_s4),
        ImageBitmap.imageResource(R.drawable.boss_s5),
        stageBg,
        itemPu = ImageBitmap.imageResource(R.drawable.cs_item_pu),
        itemBh = ImageBitmap.imageResource(R.drawable.cs_item_bh),
        coin = ImageBitmap.imageResource(R.drawable.cs_coin),
        gem = ImageBitmap.imageResource(R.drawable.cs_gem),
        treasures = mapOf(
            1 to ImageBitmap.imageResource(R.drawable.cs_tres1),
            5 to ImageBitmap.imageResource(R.drawable.cs_tres5),
            10 to ImageBitmap.imageResource(R.drawable.cs_tres10),
            20 to ImageBitmap.imageResource(R.drawable.cs_tres20),
            50 to ImageBitmap.imageResource(R.drawable.cs_tres50),
            100 to ImageBitmap.imageResource(R.drawable.cs_tres100)
        ),
        itemGiant = ImageBitmap.imageResource(R.drawable.cs_geant),
        itemBomb = ImageBitmap.imageResource(R.drawable.cs_bomb),
        putext = ImageBitmap.imageResource(R.drawable.cs_putext),
        fang = ImageBitmap.imageResource(R.drawable.cs_fang),
        itemMega = ImageBitmap.imageResource(R.drawable.cs_mpu),
        explosions = explList,
        coinFont = coinFontList,
        projSprites = projSpritesList,
        bossP2 = bossP2List,
        bossP3 = bossP3List
    )

    // Sprites IA (validés) du stage courant : mid-boss cs_mb_s{lvl}_{i}, ennemis cs_en_*, destructibles cs_ds_*
    fun spriteIdsFor(prefix: String, maxN: Int): List<Int> =
        (1..maxN).mapNotNull { i ->
            val id = ctx.resources.getIdentifier("${prefix}${level}_$i", "drawable", ctx.packageName)
            if (id != 0) id else null
        }
    val midBossIds = remember(level) { spriteIdsFor("cs_mb_s", 8) }
    val enemyIds = remember(level) { spriteIdsFor("cs_en_s", 16) }
    val cannonIds = remember(level) { spriteIdsFor("cs_ds_s", 6) }
    val midBossSprites = ArrayList<ImageBitmap>(midBossIds.size)
    for (mbId in midBossIds) midBossSprites.add(ImageBitmap.imageResource(mbId))
    val enemySprites = ArrayList<ImageBitmap>(enemyIds.size)
    for (enId in enemyIds) enemySprites.add(ImageBitmap.imageResource(enId))
    val cannonSprites = ArrayList<ImageBitmap>(cannonIds.size)
    for (dsId in cannonIds) cannonSprites.add(ImageBitmap.imageResource(dsId))

    val game = remember(runToken, canvasSize, mode, ship) {
        if (canvasSize == IntSize.Zero) null
        else ShmupGame(
            // Vague B : le monde fait 1.5 écran de large, la caméra suit le vaisseau.
            canvasSize.width * 1.5f, canvasSize.height.toFloat(), mode, ship,
            viewW = canvasSize.width.toFloat(),
            level = level, initLives = carryLives, initPower = carryPower, initBombs = carryBombs,
            initScore = carryScore, initCoins = carryCoins, initPerfect = carryPerfect,
            midBossSprites = midBossSprites, enemySprites = enemySprites, cannonSprites = cannonSprites,
            explosionSprites = explList
        )
    }

    DisposableEffect(Unit) { onDispose { GameMusic.stop() } }

    // Pause de la musique quand l'app passe en arrière-plan (le rendu se fige déjà tout seul).
    DisposableEffect(Unit) {
        val owner = ctx.findActivity() as? androidx.lifecycle.LifecycleOwner
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
            when (e) {
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> {
                    SoundEngine.appPaused = true      // coupe les SFX (le directeur tourne encore)
                    SoundEngine.stopAll()
                    GameMusic.pause()
                }
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> {
                    SoundEngine.appPaused = false
                    GameMusic.resume()
                }
                else -> {}
            }
        }
        owner?.lifecycle?.addObserver(obs)
        onDispose { SoundEngine.appPaused = false; owner?.lifecycle?.removeObserver(obs) }
    }

    // Plein écran immersif pendant Crimson Sky : les barres système sont masquées,
    // le fond sombre du jeu occupe donc tout l'écran (barre de notif + barre Android).
    DisposableEffect(Unit) {
        val act = ctx.findActivity()
        com.goodbrainrot.app.forceImmersive = true      // reste masqué même après un refocus
        act?.applyImmersive(true)
        onDispose {
            com.goodbrainrot.app.forceImmersive = false
            act?.applyImmersive(com.goodbrainrot.app.Settings.babyMode.value)
        }
    }

    // Boucle de rendu/physique
    LaunchedEffect(game, phase) {
        if (game == null) return@LaunchedEffect
        var last = 0L
        while (true) {
            val now = withFrameNanos { it }
            if (last != 0L) {
                val dt = ((now - last) / 1_000_000_000f).coerceIn(0f, 0.05f)
                game.updateAmbient(dt)
                if (phase == ShPhase.PLAYING) {
                    // SCORE ATTACK : chrono ; à 0 la partie s'arrête proprement sur le tableau des scores
                    if (mode == ShMode.SCORE) {
                        scoreTimeLeft -= dt
                        if (scoreTimeLeft <= 0f) {
                            scoreTimeLeft = 0f
                            GameMusic.stop(); SoundEngine.playVictory()
                            HighScores.record(scoreKey, hudScore)
                            phase = ShPhase.SCORES
                        }
                    }
                    // hit-stop : micro-gel du jeu à l'impact (le rendu continue)
                    if (game.hitStop > 0f) game.hitStop = max(0f, game.hitStop - dt)
                    else game.update(dt)
                    hudScore = game.score; hudLives = game.lives; hudMult = game.mult
                    hudPower = game.power; hudBombs = game.bombs; hudCoins = game.coinCount
                    hudBossWarn = game.bossWarn > 0f
                    hudBossWarnBoss = game.bossWarnBoss
                    bossFrac = game.boss?.let { it.hp.toFloat() / it.maxHp } ?: -1f
                    if (game.gameOver) {
                        GameMusic.stop()
                        game.explodePlayer()          // le vaisseau explose d'abord
                        phase = ShPhase.DEATH
                    }
                }
                frameTick++
            }
            last = now
        }
    }

    // Directeur : scénario du niveau courant (game.level), enchaînement des niveaux.
    LaunchedEffect(game, runToken) {
        if (game == null) return@LaunchedEffect
        val lvl = game.level
        while (phase != ShPhase.PLAYING) kotlinx.coroutines.delay(60)
        // ---- SCORE ATTACK : 2 minutes de vagues continues (mini-boss à mi-course), pas de boss de fin
        if (mode == ShMode.SCORE) {
            scoreTimeLeft = SCORE_ATTACK_S
            kotlinx.coroutines.delay(600)
            game.dropPowerUp(); game.dropPowerUp()
            var loop = 0
            while (phase == ShPhase.PLAYING && !game.gameOver) {
                when (loop % 4) {
                    0 -> game.spawnMixedSwarm(22 + (loop * 2).coerceAtMost(20))
                    1 -> game.spawnArc(Kind.INTERCEPTOR, 16 + loop.coerceAtMost(12))
                    2 -> game.spawnSwarm(Kind.DRONE, 24 + (loop * 2).coerceAtMost(24))
                    else -> game.spawnMixedSwarm(20 + loop.coerceAtMost(16))
                }
                if (loop % 3 == 1) game.dropPowerUp()
                if (loop == 6) { GameMusic.playBoss(ctx); game.spawnMiniBoss()
                    while (game.miniBossAlive() && !game.gameOver && phase == ShPhase.PLAYING) kotlinx.coroutines.delay(80)
                    if (phase == ShPhase.PLAYING) GameMusic.playStage(ctx, lvl) }
                var acc = 0f
                while (acc < 5500f && !game.gameOver && phase == ShPhase.PLAYING) {
                    if (game.gruntCount() <= 4) break
                    kotlinx.coroutines.delay(90); acc += 90f
                }
                kotlinx.coroutines.delay(500)
                loop++
            }
            return@LaunchedEffect
        }
        // Après un continue : super power-up (met au niveau MAX) juste après l'entrée.
        if (pendingMega) { pendingMega = false; kotlinx.coroutines.delay(500); game.dropMegaPowerUp() }
        suspend fun wait(s: Float) {
            val end = s * 1000; var acc = 0f
            while (acc < end) { if (game.gameOver) return; kotlinx.coroutines.delay(50); acc += 50 }
        }
        suspend fun untilBossDead() { while (game.bossAlive() && !game.gameOver) kotlinx.coroutines.delay(80) }
        // Attend que l'essaim soit presque nettoyé (ou expiration).
        suspend fun waitCleared(maxS: Float) {
            var acc = 0f
            while (acc < maxS * 1000 && !game.gameOver) {
                if (game.gruntCount() <= 4) break
                kotlinx.coroutines.delay(90); acc += 90
            }
        }
        // Gros essaim : déferle, on le laisse se faire pulvériser, puis une pause (plus d'air).
        suspend fun waveSwarm(kind: Kind, count: Int, maxWait: Float): Boolean {
            if (game.gameOver) return false
            game.spawnSwarm(kind, count)
            waitCleared(maxWait); wait(1.0f)
            return !game.gameOver
        }
        // Essaim mixte (drones + intercepteurs) — diversité.
        suspend fun waveMixed(count: Int, maxWait: Float): Boolean {
            if (game.gameOver) return false
            game.spawnMixedSwarm(count)
            waitCleared(maxWait); wait(1.0f)
            return !game.gameOver
        }
        // Formation en V/arc — diversité.
        suspend fun waveArc(kind: Kind, count: Int, maxWait: Float): Boolean {
            if (game.gameOver) return false
            game.spawnArc(kind, count)
            waitCleared(maxWait); wait(1.0f)
            return !game.gameOver
        }

        // Distribution des power-ups : juste assez pour atteindre le max AVANT le boss si
        // tout est ramassé. Les mini-boss en lâchent 1 chacun ; le reste est semé au fil du
        // niveau, et le dernier est forcé juste avant le boss.
        val miniDrops = when { lvl == 6 -> 0; lvl == 1 -> 1; else -> 2 }
        var pupBudget = (max(0, 8 - game.power - miniDrops) *
            com.goodbrainrot.app.Settings.shmupPowerupFreq.floatValue).roundToInt()
        fun tryPup() { if (pupBudget > 0) { game.dropPowerUp(); pupBudget-- } }
        suspend fun drainPupsBeforeBoss() {
            while (pupBudget > 0 && !game.gameOver) { game.dropPowerUp(); pupBudget--; wait(0.5f) }
        }

        // Tailles d'essaims qui grossissent avec le niveau (gros mais peu tirent).
        val small = 16 + lvl * 4                       // 20..36
        val big = (40 + lvl * 8).coerceAtMost(85)      // 48..85

        if (lvl == 6) {
            // Niveau caché : gauntlet dense + mid-boss par PAIRES, puis TRUE LAST BOSS
            if (!waveArc(Kind.INTERCEPTOR, 40, 6f)) return@LaunchedEffect
            tryPup()
            // Mid-boss deux par deux (rush du niveau bonus)
            val pairRushes = ((game.midBossSpriteCount() + 1) / 2).coerceIn(2, 4)
            repeat(pairRushes) {
                GameMusic.playBoss(ctx)
                game.spawnMiniBossPair()
                while (game.miniBossAlive() && !game.gameOver) kotlinx.coroutines.delay(80)
                if (game.gameOver) return@LaunchedEffect
                GameMusic.playStage(ctx, lvl); wait(1.0f)
            }
            if (!waveMixed(60, 7f)) return@LaunchedEffect
            tryPup()
            game.dropGiant()
            if (!waveSwarm(Kind.INTERCEPTOR, 85, 8f)) return@LaunchedEffect
            game.dropBlackhole()
            drainPupsBeforeBoss()
            GameMusic.playBoss(ctx)
            game.spawnTrueLastBoss(); untilBossDead()
        } else {
            // Acte 1 (formations variées : ligne, V, mixte)
            if (!waveSwarm(Kind.DRONE, small, 5f)) return@LaunchedEffect
            if (!waveArc(Kind.INTERCEPTOR, small, 5f)) return@LaunchedEffect       // formation en V
            tryPup()
            if (!waveMixed(small + 6, 6f)) return@LaunchedEffect                   // essaim mixte
            tryPup()
            // Niveau 2 : champ de murs à franchir/détruire
            if (lvl == 2) { game.spawnWall(ObKind.WALL); wait(3.2f); if (game.gameOver) return@LaunchedEffect
                            game.spawnWall(ObKind.WALL); wait(3.2f); if (game.gameOver) return@LaunchedEffect }
            if (!waveSwarm(Kind.DRONE, big, 8f)) return@LaunchedEffect           // gros essaim
            // 1er mini-boss
            GameMusic.playBoss(ctx)
            game.spawnMiniBoss(); untilBossDead(); if (game.gameOver) return@LaunchedEffect
            GameMusic.playStage(ctx, lvl); wait(1.2f)
            game.dropGiant()                     // item géant (un par stage)
            // Acte 2
            if (!waveMixed(small + 8, 6f)) return@LaunchedEffect
            tryPup()
            game.spawnFromSides(2 + lvl / 3)    // quelques ennemis par les côtés (haut)
            game.spawnCannons(1 + lvl / 3)      // gros canons fixes (tir vers le bas, destructibles)
            if (!waveArc(Kind.DRONE, small + 6, 6f)) return@LaunchedEffect        // formation en V
            // Niveau 4 : ligne de bâtiments à raser
            if (lvl == 4) { game.spawnWall(ObKind.BUILDING); wait(3.4f); if (game.gameOver) return@LaunchedEffect
                            game.spawnWall(ObKind.BUILDING); wait(3.4f); if (game.gameOver) return@LaunchedEffect }
            if (!waveSwarm(Kind.DRONE, big, 8f)) return@LaunchedEffect           // 2e gros essaim
            // 2e mini-boss à partir du niveau 2. Le stage 5 en enchaîne AUTANT que de sprites
            // de mid-boss IA disponibles (le 1er mini-boss ci-dessus compte déjà pour 1).
            // Autant de mid-boss que de sprites IA validés pour ce niveau (3), le 1er compte déjà.
            val extraMini = (game.midBossSpriteCount() - 1).coerceIn(0, 5)
            repeat(extraMini) {
                GameMusic.playBoss(ctx)
                game.spawnMiniBoss(); untilBossDead(); if (game.gameOver) return@LaunchedEffect
                GameMusic.playStage(ctx, lvl); wait(1.2f)
            }
            // Acte 3
            if (!waveArc(Kind.INTERCEPTOR, small, 5f)) return@LaunchedEffect
            // Escadron LOURD : peu d'ennemis mais coriaces (diversité de rythme)
            if (game.gameOver) return@LaunchedEffect
            game.spawnHeavies(2 + lvl / 2); waitCleared(9f); wait(1.0f)
            if (game.gameOver) return@LaunchedEffect
            game.dropBlackhole()                 // item trou noir (aspire tout + points)
            tryPup()
            game.spawnFromSides(2 + lvl / 2)    // ennemis par les côtés (acte 3)
            if (!waveMixed(small + 10, 6f)) return@LaunchedEffect
            if (lvl >= 3 && !waveSwarm(Kind.DRONE, big, 8f)) return@LaunchedEffect
            // dernier(s) power-up(s) juste avant le boss (max atteignable si tout ramassé)
            drainPupsBeforeBoss()
            // Boss
            GameMusic.playBoss(ctx)
            game.spawnBoss(); untilBossDead()
        }
        if (game.gameOver) return@LaunchedEffect

        // Boss battu : mort spectaculaire (il explose et TOMBE), commune à tous les niveaux.
        val isFinal = (lvl == 6) || (lvl == LAST_LEVEL && !game.perfect)
        game.eBullets.clear(); game.enemies.clear(); game.bullets.clear(); game.obstacles.clear()
        phase = ShPhase.BOSS_DYING
        game.beginBossFall()
        // explosion plus longue (retour utilisateur)
        val bursts = if (isFinal) 16 else 11
        repeat(bursts) { i ->
            if (i % 2 == 0) SoundEngine.playExplosion(true)
            kotlinx.coroutines.delay(190)
        }
        game.endBossFall()               // explosion finale géante
        SoundEngine.playExplosion(true)
        kotlinx.coroutines.delay(820)
        // attend que les pièces aspirées soient toutes ramassées avant l'envol
        run { var w = 0f; while (game.coins.isNotEmpty() && w < 2.0f) { kotlinx.coroutines.delay(60); w += 0.06f } }

        if (!isFinal) {
            carryLives = game.lives; carryPower = game.power; carryBombs = game.bombs
            carryScore = game.score; carryCoins = game.coinCount; carryPerfect = game.perfect
            // « STAGE CLEAR » s'affiche EN MÊME TEMPS que l'envol du vaisseau, et reste
            // jusqu'à ce qu'il ait quitté l'écran.
            clearPerfect = game.stageHitless
            clearBanner = true
            phase = ShPhase.SHIP_EXIT
            game.beginShipExit()
            SoundEngine.playVictory()
            SoundEngine.playWhoosh()
            if (clearPerfect) {
                repeat(3) { Haptics.heavy(); kotlinx.coroutines.delay(150) }  // 2-3 vibrations pour PERFECT
                kotlinx.coroutines.delay(1250)
            } else {
                kotlinx.coroutines.delay(1700)  // le temps que le vaisseau sorte de l'écran
            }
            clearBanner = false
            // Récap du stage : le directeur s'arrête ici, le joueur reprend via onContinue.
            pendingLevel = if (lvl == LAST_LEVEL) 6 else lvl + 1
            phase = ShPhase.STAGE_RESULT
            return@LaunchedEffect
        } else {
            // Fin du jeu : le vaisseau s'envole vers le haut et sort de l'écran (façon intro).
            trueEnd = (lvl == 6)
            GameMusic.stop()
            phase = ShPhase.SHIP_EXIT
            game.beginShipExit()
            SoundEngine.playWhoosh()
            kotlinx.coroutines.delay(1500)
            SoundEngine.playFanfare()
            carryScore = game.score      // score final pour le tableau
            // Récap de tout ce qui a été récupéré, PUIS l'écran de remerciements.
            finalToCongrats = true
            phase = ShPhase.STAGE_RESULT
        }
    }

    // Musique des menus / intro (hangar, sélection, écrans de fin)
    LaunchedEffect(phase) {
        when (phase) {
            ShPhase.CINEMA, ShPhase.MODE, ShPhase.SHIP,
            ShPhase.CONGRATS, ShPhase.NAME_ENTRY, ShPhase.SCORES -> GameMusic.playMenu(ctx)
            else -> {}
        }
    }

    // Transition fin d'intro -> jeu
    LaunchedEffect(phase, game) {
        if (phase == ShPhase.INTRO && game != null) {
            game.warp = 4.5f
            GameMusic.playStage(ctx, game.level)
            kotlinx.coroutines.delay(if (game.level == 1 || game.level == 6) 2700L else 1700L)
            game.beginShipEnter()          // le vaisseau arrive vite depuis le bas
            phase = ShPhase.PLAYING
        }
        if (phase == ShPhase.DEATH) {
            // laisse l'explosion du vaisseau se jouer, puis l'écran GAME OVER illustré
            kotlinx.coroutines.delay(1500)
            phase = ShPhase.GAME_OVER
        }
    }

    // Le jeu occupe TOUT l'écran (derrière la barre de notif + la barre Android) :
    // on annule le safeDrawingPadding du parent en étendant la mise en page des insets.
    val safeInsets = WindowInsets.safeDrawing
    Box(
        modifier = Modifier
            .layout { measurable, constraints ->
                val t = safeInsets.getTop(this)
                val b = safeInsets.getBottom(this)
                val l = safeInsets.getLeft(this, layoutDirection)
                val r = safeInsets.getRight(this, layoutDirection)
                val placeable = measurable.measure(
                    Constraints.fixed(constraints.maxWidth + l + r, constraints.maxHeight + t + b)
                )
                layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(-l, -t) }
            }
            .fillMaxSize()
            .background(Color(0xFF07060F))
            .onSizeChanged { canvasSize = it }
    ) {
        // --- Rendu du jeu ---
        if (game != null) {
            val tick = frameTick // déclenche le redraw
            val tapTimes = remember { ArrayList<Long>() }
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    // Clé sur runToken uniquement : le geste survit à la transition
                    // intro -> jeu (sinon le déplacement se fige jusqu'à un nouveau toucher).
                    .pointerInput(runToken) {
                        // Le vaisseau reste SOUS le doigt : on mémorise le décalage doigt->vaisseau au début
                        // du geste et on vise une position ABSOLUE écran (compensée par la caméra horizontale).
                        // Avant, on appliquait le delta du doigt en coordonnées monde : la caméra suivant le
                        // vaisseau, celui-ci « prenait du retard » sur le doigt et finissait à côté.
                        var offX = 0f; var offY = 0f
                        detectDragGestures(
                            onDragStart = { p -> offX = game.px - (p.x + game.camX); offY = game.py - p.y },
                            onDrag = { change, _ ->
                                change.consume()
                                val tx = change.position.x + game.camX + offX
                                val ty = change.position.y + offY
                                game.movePlayer(tx - game.px, ty - game.py)
                            }
                        )
                    }
                    // Triple-tap rapide = lâcher une bombe.
                    .pointerInput(runToken) {
                        detectTapGestures(onTap = {
                            val now = System.currentTimeMillis()
                            tapTimes.add(now)
                            tapTimes.removeAll { now - it > 450 }
                            if (tapTimes.size >= 2) { tapTimes.clear(); game.tryBomb() }
                        })
                    }
            ) {
                @Suppress("UNUSED_EXPRESSION") tick
                drawGame(game, phase, logo, sprites)
            }
        }

        // --- HUD ---
        if (phase == ShPhase.PLAYING || phase == ShPhase.GAME_OVER) {
            ShmupHud(hudScore, hudLives, hudMult, hudPower, hudBombs, hudCoins, level, mode, bossFrac)
        }

        // --- Overlays ---
        when (phase) {
            ShPhase.CINEMA -> CinematicIntro(onDone = { SoundEngine.playUi(); phase = ShPhase.MODE })
            ShPhase.MODE -> ModeSelect(
                onPick = { m -> SoundEngine.playUi(); mode = m; phase = ShPhase.SHIP },
                onBack = onBack,
                onOptions = { SoundEngine.playUi(); showOptions = true }
            )
            ShPhase.SHIP -> ShipSelect(
                startLevel = startLevel,
                onLevel = { lv -> SoundEngine.playUi(); startLevel = lv },
                sprites = sprites, logo = logo,
                onPick = { s -> SoundEngine.playUi(); ship = s; startRun() },
                onBack = { phase = ShPhase.MODE }
            )
            ShPhase.INTRO -> IntroOverlay(level)
            ShPhase.LEVEL_CLEAR -> CenterBanner(
                if (game?.stageHitless == true) "PERFECT" else "STAGE $level CLEAR",
                if (game?.stageHitless == true) GOLD else CYAN
            )
            ShPhase.STAGE_RESULT -> game?.let { g ->
                StageResult(
                    level = level.coerceIn(1, 6),   // niveau 6 -> écran de victoire du niveau 6
                    coinsByValue = g.coinsByValue,
                    timeSec = g.stageElapsed,
                    hitless = g.stageHitless,
                    score = g.score,
                    scored = mode.scored,
                    onContinue = {
                        SoundEngine.playUi()
                        if (finalToCongrats) {
                            // fin du jeu : après le récap, on passe aux remerciements
                            finalToCongrats = false
                            phase = ShPhase.CONGRATS
                        } else {
                            if (g.stageHitless && mode.scored) carryScore += 5000
                            level = pendingLevel; runToken++; phase = ShPhase.INTRO
                        }
                    }
                )
            }
            ShPhase.BOSS_DYING -> {}   // on laisse l'explosion à l'écran
            ShPhase.SHIP_EXIT -> if (clearBanner) CenterBanner(
                if (clearPerfect) "PERFECT" else "STAGE CLEAR",
                if (clearPerfect) GOLD else CYAN
            )
            ShPhase.CONGRATS -> {
                if (outroLogo) {
                    // Étape 2 : logo « un jeu GoodBrainRot » plein écran ; un clic -> high-scores.
                    OutroLogoScreen(onDone = {
                        outroLogo = false
                        if (mode.scored) HighScores.record(scoreKey, hudScore)   // enregistré avec la date, sans saisie de nom
                        phase = ShPhase.SCORES
                    })
                } else {
                    // Étape 1 : image de fin + remerciements ; un clic (CONTINUER) -> logo.
                    CongratsOverlay(
                        score = hudScore, scored = mode.scored, ship = ship, logo = logo, trueEnd = trueEnd,
                        onContinue = { SoundEngine.playUi(); outroLogo = true }
                    )
                }
            }
            ShPhase.DEATH -> {}   // le vaisseau explose à l'écran (rendu dans le Canvas)
            ShPhase.GAME_OVER -> GameOverOverlay(
                credits = credits,
                onContinue = { continueRun() },
                onQuit = {
                    if (mode.scored) HighScores.record(scoreKey, hudScore)
                    phase = ShPhase.SCORES
                }
            )
            ShPhase.NAME_ENTRY -> { phase = ShPhase.SCORES }   // (plus de saisie de nom)
            ShPhase.SCORES -> ScoreBoard(
                score = hudScore, scored = mode.scored, sprites = sprites, logo = logo, gameKey = scoreKey,
                onReplay = { phase = ShPhase.SHIP },   // rejouer = re-choisir son vaisseau
                onBack = onBack
            )
            ShPhase.PLAYING -> {
                // chrono du SCORE ATTACK, en haut au centre
                if (mode == ShMode.SCORE) {
                    val s = scoreTimeLeft.toInt().coerceAtLeast(0)
                    Text("⏱ %d:%02d".format(s / 60, s % 60), color = if (s <= 10) CRIM else GOLD, fontFamily = PixelHud,
                        fontWeight = FontWeight.ExtraBold, fontSize = 20.sp,
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = 44.dp))
                }
                // Retour EN JEU : revient au menu du shmup (choix du mode), ne quitte pas le jeu.
                ShBackButton(onBack = { GameMusic.stop(); runToken++; phase = ShPhase.MODE })
                if (game != null) BombButton(hudBombs) { game.tryBomb() }
                if (hudBossWarn) BossWarningBanner(hudBossWarnBoss)
            }
        }
        if (showOptions) OptionsOverlay(onClose = { SoundEngine.playUi(); showOptions = false })
    }
}

/** Réglages du shmup : volumes musique / tirs / objets ramassés. */
@Composable
private fun OptionsOverlay(onClose: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xE6070610)).clickableNoRipple { },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(0.86f).fillMaxHeight(0.9f)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("OPTIONS", color = CYAN, fontFamily = Arcade, fontSize = 22.sp,
                modifier = Modifier.padding(top = 20.dp, bottom = 14.dp))
            // Onglets : MUSIC (volumes) / GAME (gameplay) — libellés en anglais.
            var tab by remember { mutableStateOf(0) }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                OptTab("MUSIC", tab == 0) { tab = 0 }
                Spacer(Modifier.width(10.dp))
                OptTab("GAME", tab == 1) { tab = 1 }
            }
            Spacer(Modifier.height(14.dp))
            if (tab == 0) {
                VolumeSlider("MUSIC", com.goodbrainrot.app.Settings.musicVolume.floatValue, GOLD) {
                    com.goodbrainrot.app.Settings.setMusicVolume(it)
                }
                VolumeSlider("SHOTS", com.goodbrainrot.app.Settings.shotVolume.floatValue, CYAN) {
                    com.goodbrainrot.app.Settings.setShotVolume(it)
                }
                VolumeSlider("EXPLOSIONS", com.goodbrainrot.app.Settings.explosionVolume.floatValue, CRIM) {
                    com.goodbrainrot.app.Settings.setExplosionVolume(it)
                }
                VolumeSlider("PICKUPS", com.goodbrainrot.app.Settings.pickupVolume.floatValue, GREEN) {
                    com.goodbrainrot.app.Settings.setPickupVolume(it)
                }
            } else {
                VolumeSlider("DECOR BRIGHTNESS", com.goodbrainrot.app.Settings.bgIntensity.floatValue, Color(0xFFB98CFF)) {
                    com.goodbrainrot.app.Settings.setBgIntensity(it)
                }
                // Mid-boss size : 100% .. 1000%
                val mbs = com.goodbrainrot.app.Settings.midBossScale.floatValue
                TuneSlider("MID-BOSS SIZE", "${(mbs * 100).toInt()}%", mbs, 1f..10f, 0, ORANGE) {
                    com.goodbrainrot.app.Settings.setMidBossScale(it)
                }
                // Starting lives (scored mode) : 1 .. 9
                val lv = com.goodbrainrot.app.Settings.shmupLives.intValue
                TuneSlider("STARTING LIVES", "$lv", lv.toFloat(), 1f..9f, 8, CRIM) {
                    com.goodbrainrot.app.Settings.setShmupLives(it.roundToInt().coerceIn(1, 9))
                }
                // Treasure value : ×0.25 .. ×5
                val tm = com.goodbrainrot.app.Settings.shmupTreasureMult.floatValue
                TuneSlider("TREASURE VALUE", "x${"%.2f".format(tm)}", tm, 0.25f..5f, 0, GOLD) {
                    com.goodbrainrot.app.Settings.setShmupTreasureMult((it * 20f).roundToInt() / 20f)
                }
                // Power-up rate : ×0.25 .. ×3
                val pf = com.goodbrainrot.app.Settings.shmupPowerupFreq.floatValue
                TuneSlider("POWER-UP RATE", "x${"%.2f".format(pf)}", pf, 0.25f..3f, 0, CYAN) {
                    com.goodbrainrot.app.Settings.setShmupPowerupFreq((it * 20f).roundToInt() / 20f)
                }
                // Coin drop rate : 0 .. 1000% (au-dela de 100%, plusieurs pieces par destructible)
                val cd = com.goodbrainrot.app.Settings.shmupCoinDrop.floatValue
                TuneSlider("COIN DROP RATE", "${(cd * 100).roundToInt()}%", cd, 0f..10f, 0, GREEN) {
                    com.goodbrainrot.app.Settings.setShmupCoinDrop(it)
                }
                // Enemy vulnerable once this fraction of its sprite is on-screen (entry)
                val ev = com.goodbrainrot.app.Settings.enemyEntryVuln.floatValue
                TuneSlider("ENEMY VULNERABLE AT", "${(ev * 100).roundToInt()}%", ev, 0f..1f, 0, ORANGE) {
                    com.goodbrainrot.app.Settings.setEnemyEntryVuln(it)
                }
            }

            Spacer(Modifier.height(18.dp))
            ModeSmallButton("CLOSE", PINK, onClose)
        }
    }
}

/** Onglet du menu OPTIONS en jeu (MUSIC / GAME). */
@Composable
private fun OptTab(label: String, on: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (on) CYAN.copy(alpha = 0.22f) else Color(0x14FFFFFF))
            .border(2.dp, if (on) CYAN else Color(0x44FFFFFF), RoundedCornerShape(8.dp))
            .clickableNoRipple { SoundEngine.playUi(); onClick() }
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        Text(label, color = if (on) CYAN else Color(0xCCFFFFFF), fontFamily = Arcade, fontSize = 14.sp)
    }
}

/** Slider générique du menu configuration : libellé + valeur formatée à droite. */
@Composable
private fun TuneSlider(label: String, valueText: String, value: Float, range: ClosedFloatingPointRange<Float>,
                       steps: Int, color: Color, onChange: (Float) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = color, fontFamily = PixelHud, fontSize = 16.sp)
            Text(valueText, color = Color.White, fontFamily = PixelHud, fontSize = 16.sp)
        }
        androidx.compose.material3.Slider(
            value = value, onValueChange = onChange, valueRange = range, steps = steps,
            colors = androidx.compose.material3.SliderDefaults.colors(
                thumbColor = color, activeTrackColor = color, inactiveTrackColor = color.copy(alpha = 0.25f)
            )
        )
    }
}

@Composable
private fun VolumeSlider(label: String, value: Float, color: Color, onChange: (Float) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = color, fontFamily = PixelHud, fontSize = 16.sp)
            Text("${(value * 100).toInt()}%", color = Color.White, fontFamily = PixelHud, fontSize = 16.sp)
        }
        androidx.compose.material3.Slider(
            value = value, onValueChange = onChange, valueRange = 0f..1f,
            colors = androidx.compose.material3.SliderDefaults.colors(
                thumbColor = color, activeTrackColor = color, inactiveTrackColor = color.copy(alpha = 0.25f)
            )
        )
    }
}

// ---------------------------------------------------------------------------
// Rendu Canvas
// ---------------------------------------------------------------------------

private fun DrawScope.drawGame(g: ShmupGame, phase: ShPhase, logo: ImageBitmap?, spr: ShSprites) {
    val w = size.width; val h = size.height
    // --- Fond du niveau : dégradé sombre + poussière d'étoiles NETTE (pixels) ---
    drawRect(Brush.verticalGradient(g.theme.bg), size = size)
    // décor pixel-art IA du stage (pâli/transparent pour la lisibilité), s'il existe
    val bgImg = spr.stageBg.getOrNull(g.level - 1)
    val hasBg = bgImg != null
    if (bgImg != null) {
        // Luminosité du décor réglable dans les options (0..1) : plus haut = fond plus lumineux.
        val bgI = com.goodbrainrot.app.Settings.bgIntensity.floatValue.coerceIn(0f, 1f)
        val bgA = (0.22f + bgI * 0.75f).coerceIn(0.1f, 1f)
        val veilA = (0.55f - bgI * 0.5f).coerceIn(0.03f, 0.6f)
        // Vague B : image légèrement plus large que l'écran + parallax horizontal doux
        // piloté par la caméra (impression de monde large sans découvrir de bord).
        val iw = w * 1.12f
        val ih = iw * bgImg.height / bgImg.width
        val maxCam = (g.w - g.viewW).coerceAtLeast(1f)
        val x0 = -(iw - w) * (g.camX / maxCam).coerceIn(0f, 1f)
        // Le niveau COMMENCE en BAS de l'image (bas de l'image collé au bas de l'écran), puis on
        // remonte la carte. UNE seule image (pas de tuile, pas de miroir), pan clampé : aucune couture.
        val maxPan = (ih - h).coerceAtLeast(0f)
        val pan = (g.bgScroll * 0.05f * h).coerceIn(0f, maxPan)
        val y0 = pan - (ih - h)      // pan=0 -> bas de l'image en bas de l'écran ; pan=max -> haut en haut
        drawImage(
            image = bgImg,
            srcOffset = IntOffset.Zero, srcSize = IntSize(bgImg.width, bgImg.height),
            dstOffset = IntOffset(x0.toInt(), y0.toInt()), dstSize = IntSize(iw.toInt(), ih.toInt()),
            alpha = bgA, filterQuality = FilterQuality.None
        )
        // voile sombre par-dessus pour garder les balles/ennemis lisibles (diminue si décor lumineux)
        drawRect(Color(0xFF070610).copy(alpha = veilA), size = size)
    }
    for (s in g.stars) {
        val sz = ((s.size * 1.3f + 0.5f) * g.u * 0.45f)
        val a = 0.16f + s.z * 0.45f
        // Vague B : parallax horizontal des étoiles selon la profondeur (caméra)
        val sx = s.x - g.camX * (0.15f + 0.25f * s.z)
        if (g.warp > 1.3f) {
            drawRect(g.theme.star.copy(alpha = a * 0.5f),
                topLeft = Offset(sx, s.y), size = Size(sz, sz * (2f + (g.warp - 1f) * 5f)))
        } else {
            drawRect(g.theme.star.copy(alpha = a), topLeft = Offset(sx, s.y), size = Size(sz, sz))
        }
    }
    // décor de relief en parallax (uniquement si pas de fond image IA) — faible contraste
    if (!hasBg) drawParallaxTerrain(g)
    // vague D : habillage 3D discret (flancs uniquement), par-dessus le fond IA
    draw3DRelief(g)

    val shx = if (g.shake > 0f) (Random.nextFloat() - 0.5f) * g.shake else 0f
    val shy = if (g.shake > 0f) (Random.nextFloat() - 0.5f) * g.shake else 0f

    // Vague B : tout l'espace MONDE est décalé par la caméra horizontale
    translate(shx - g.camX, shy) {
        // pièces à récupérer (dessous)
        for (c in g.coins) drawCoin(c.x, c.y, g.u, c.t, c.value, coinImg = spr.coin, gemImg = spr.gem,
            treImg = spr.treasures[c.value])

        // powerups (au-dessus des pièces)
        for (p in g.powerups) drawPowerUp(p.x, p.y, g.u, p.mega, g.stageElapsed, p.giant, p.blackhole,
            puImg = spr.itemPu, bhImg = spr.itemBh, giantImg = spr.itemGiant, megaImg = spr.itemMega)

        // obstacles (murs / bâtiments) à détruire ou esquiver
        for (o in g.obstacles) drawObstacle(o, g.u)

        // balles ennemies : sprites IA (projectiles validés) orientés vers la trajectoire ;
        // les projectiles « signature » des boss (dents de serpent / tourbillon) restent procéduraux.
        val ps = spr.projSprites
        for (b in g.eBullets) {
            when {
                b.vortex -> drawVortex(b.x, b.y, b.r, b.color, g.stageElapsed)
                b.fang && spr.fang != null -> {
                    val fimg = spr.fang
                    val fang = atan2(b.vy, b.vx) * 180f / PI.toFloat()   // sprite pointe est -> aligne sur la vitesse
                    val dw = b.r * 5.5f; val dh = dw * fimg.height / fimg.width
                    rotate(fang, pivot = Offset(b.x, b.y)) {
                        drawImage(image = fimg, srcOffset = IntOffset.Zero,
                            srcSize = IntSize(fimg.width, fimg.height),
                            dstOffset = IntOffset((b.x - dw / 2f).toInt(), (b.y - dh / 2f).toInt()),
                            dstSize = IntSize(dw.toInt(), dh.toInt()), filterQuality = FilterQuality.None)
                    }
                }
                b.fang -> drawFang(b.x, b.y, b.r, b.color, atan2(b.vy, b.vx))
                ps.isNotEmpty() -> {
                    // Sprite choisi par couleur d'ennemi (+ niveau) : stable par type d'ennemi.
                    val idx = ((b.color.hashCode() and 0x7fffffff) + g.level * 7) % ps.size
                    val ang = atan2(b.vy, b.vx) * 180f / PI.toFloat() + 90f
                    rotate(ang, pivot = Offset(b.x, b.y)) {
                        drawSpriteImg(ps[idx], b.x, b.y, b.r * 2.8f, rot180 = false, tint = null, flash = false)
                    }
                }
                else -> drawPixelBead(b.x, b.y, b.r * 0.9f, b.color)
            }
        }

        // ennemis (sprites PNG, retournés vers le bas, teintés par le niveau)
        for (e in g.enemies) {
            if (e.cannon) { drawCannon(e, g.u, spr); continue }
            // Sprite IA d'ennemi (validé) : orienté vers le bas, couleurs d'origine (pas de teinte).
            // IMPORTANT : ce raccourci ne concerne QUE la pietaille (DRONE/INTERCEPTOR). Les mid-boss
            // et boss ont AUSSI un spriteImg mais doivent passer par drawMiniBoss/drawBoss (echelle,
            // barre de vie, phases). Sinon ils etaient dessines a taille fixe (10.5u) = tout petits.
            val eimg = e.spriteImg
            if (eimg != null && e.kind != Kind.MINIBOSS && e.kind != Kind.BOSS) {
                val szU = if (e.kind == Kind.INTERCEPTOR && e.heavy) 17f else 8.8f
                if (e.heavy) {
                    drawCircle(Color(0xFFFF3B2A).copy(alpha = 0.16f), radius = g.u * 9f, center = Offset(e.x, e.y))
                    val frac = (e.hp.toFloat() / e.maxHp).coerceIn(0f, 1f)
                    drawRect(Color(0xAA000000), topLeft = Offset(e.x - g.u * 6f, e.y - g.u * 9.5f), size = Size(g.u * 12f, g.u * 1.2f))
                    drawRect(if (frac > 0.4f) ORANGE else CRIM,
                        topLeft = Offset(e.x - g.u * 6f, e.y - g.u * 9.5f), size = Size(g.u * 12f * frac, g.u * 1.2f))
                }
                drawSpriteImg(eimg, e.x, e.y, g.u * szU, rot180 = false, tint = null, flash = e.hitFlash > 0f)
                continue
            }
            when (e.kind) {
                Kind.DRONE -> drawSpriteImg(spr.enemyA, e.x, e.y, g.u * 9f, rot180 = true,
                    tint = tintVariant(g.theme.droneColor, e.shade), flash = e.hitFlash > 0f)
                Kind.INTERCEPTOR -> if (e.heavy) {
                    // ennemi lourd : gros, blindé sombre, halo d'avertissement + barre de PV
                    drawCircle(Color(0xFFFF3B2A).copy(alpha = 0.16f), radius = g.u * 9f, center = Offset(e.x, e.y))
                    drawSpriteImg(spr.enemyB, e.x, e.y, g.u * 16f, rot180 = true,
                        tint = Color(0xFF8A5030), flash = e.hitFlash > 0f)
                    val frac = (e.hp.toFloat() / e.maxHp).coerceIn(0f, 1f)
                    drawRect(Color(0xAA000000), topLeft = Offset(e.x - g.u * 6f, e.y - g.u * 8.5f), size = Size(g.u * 12f, g.u * 1.2f))
                    drawRect(if (frac > 0.4f) ORANGE else CRIM,
                        topLeft = Offset(e.x - g.u * 6f, e.y - g.u * 8.5f), size = Size(g.u * 12f * frac, g.u * 1.2f))
                } else drawSpriteImg(spr.enemyB, e.x, e.y, g.u * 9f, rot180 = true,
                    tint = tintVariant(g.theme.interColor, e.shade), flash = e.hitFlash > 0f)
                Kind.MINIBOSS -> drawMiniBoss(e, g.u, spr, camX = g.camX)
                Kind.BOSS -> drawBoss(e, g.u, spr, g.level, camX = g.camX)
            }
        }

        // boss qui explose et tombe (séquence de fin), sans barre de vie
        if ((phase == ShPhase.BOSS_DYING) && g.bossFalling) {
            g.deadBoss?.let { drawBoss(it, g.u, spr, g.level, showBar = false) }
        }

        // rayon laser continu (vaisseau LASER) — façon DoDonPachi
        if (g.ship == ShShip.LASER && g.laserOn && phase == ShPhase.PLAYING) {
            drawLaserBeam(g)
            // les balles ennemies restent visibles PAR-DESSUS le laser (sinon on ne voit
            // plus d'où elles arrivent quand le faisceau est large)
            for (b in g.eBullets) when {
                b.vortex -> drawVortex(b.x, b.y, b.r, b.color, g.stageElapsed)
                b.fang && spr.fang != null -> {
                    val fimg = spr.fang
                    val fang = atan2(b.vy, b.vx) * 180f / PI.toFloat()   // sprite pointe est -> aligne sur la vitesse
                    val dw = b.r * 5.5f; val dh = dw * fimg.height / fimg.width
                    rotate(fang, pivot = Offset(b.x, b.y)) {
                        drawImage(image = fimg, srcOffset = IntOffset.Zero,
                            srcSize = IntSize(fimg.width, fimg.height),
                            dstOffset = IntOffset((b.x - dw / 2f).toInt(), (b.y - dh / 2f).toInt()),
                            dstSize = IntSize(dw.toInt(), dh.toInt()), filterQuality = FilterQuality.None)
                    }
                }
                b.fang -> drawFang(b.x, b.y, b.r, b.color, atan2(b.vy, b.vx))
                else -> drawPixelBead(b.x, b.y, b.r * 0.9f, b.color)
            }
        }

        // balles joueur : petites perles/aiguilles nettes
        for (b in g.bullets) {
            if (b.dart) {
                // dard allongé, orienté dans le sens du déplacement (queue lumineuse)
                val ang = atan2(b.vy, b.vx)
                rotate(degrees = ang * 180f / PI.toFloat() + 90f, pivot = Offset(b.x, b.y)) {
                    drawRect(b.color.copy(alpha = 0.35f), topLeft = Offset(b.x - b.r * 0.9f, b.y - b.r * 0.5f), size = Size(b.r * 1.8f, b.r * 5f))
                    drawRect(b.color, topLeft = Offset(b.x - b.r * 0.55f, b.y - b.r * 3f), size = Size(b.r * 1.1f, b.r * 5f))
                    drawRect(Color.White, topLeft = Offset(b.x - b.r * 0.25f, b.y - b.r * 3f), size = Size(b.r * 0.5f, b.r * 4f))
                }
            } else {
                drawBead(b.x, b.y, b.r * 0.85f, b.color)
            }
        }

        // joueur (clignote si invulnérable ; caché s'il a explosé ; reste visible pendant boss/envol)
        if (!g.playerDead && (phase == ShPhase.PLAYING || phase == ShPhase.INTRO ||
            phase == ShPhase.BOSS_DYING || phase == ShPhase.SHIP_EXIT || phase == ShPhase.DEATH)) {
            // DESIGN : bouclier de force quand le vaisseau est à pleine vie
            if (g.fullLife) {
                val tt = g.stageElapsed
                val rr = g.u * 8.6f
                drawCircle(CYAN.copy(alpha = 0.09f), radius = rr * 1.08f, center = Offset(g.px, g.py))
                drawCircle(CYAN_HOT.copy(alpha = 0.28f + 0.12f * sin(tt * 4f)), radius = rr,
                    center = Offset(g.px, g.py),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = g.u * 0.7f))
                for (k in 0..2) {
                    val a = tt * 1.6f + k * 2.094f
                    drawCircle(Color.White.copy(alpha = 0.5f), radius = g.u * 0.7f,
                        center = Offset(g.px + cos(a) * rr, g.py + sin(a) * rr))
                }
            }
            // MODE GÉANT : rayons sur 80 % de la largeur + vaisseau ×3, invincible
            if (g.giantT > 0f) {
                val ga = (g.giantT / 2f).coerceIn(0f, 1f)
                val bw = size.width * 0.8f
                drawRect(CYAN.copy(alpha = 0.10f * ga), topLeft = Offset(g.px - bw / 2f, 0f), size = Size(bw, size.height))
                // stries de rayon animées
                for (k in 0..5) {
                    val rx = g.px - bw / 2f + bw * ((k + (g.stageElapsed * 0.7f) % 1f) / 6f)
                    drawRect(CYAN_HOT.copy(alpha = 0.22f * ga), topLeft = Offset(rx, 0f), size = Size(g.u * 1.4f, size.height))
                }
                drawRect(Color.White.copy(alpha = 0.10f * ga), topLeft = Offset(g.px - bw * 0.18f, 0f), size = Size(bw * 0.36f, size.height))
            }
            val giantScale = if (g.giantT > 0f) 3f else 1f
            val blink = g.invuln <= 0f || g.giantT > 0f || ((g.invuln * 12f).toInt() % 2 == 0)
            // DESIGN : à 1 cœur, le vaisseau clignote en rouge
            val redBlink = g.lowLife && ((g.stageElapsed * 6f).toInt() % 2 == 0)
            if (blink) {
                if (g.ship == ShShip.BRAINROT && logo != null) {
                    val s = g.u * 15f * giantScale
                    drawImage(
                        image = logo,
                        srcOffset = IntOffset.Zero,
                        srcSize = IntSize(logo.width, logo.height),
                        dstOffset = IntOffset((g.px - s / 2f).toInt(), (g.py - s / 2f).toInt()),
                        dstSize = IntSize(s.toInt(), s.toInt())
                    )
                    if (redBlink) drawCircle(Color(0x66FF3030), radius = s * 0.42f, center = Offset(g.px, g.py))
                } else {
                    drawSpriteImg(playerImg(spr, g.ship), g.px, g.py, g.u * 13f * giantScale, rot180 = false,
                        tint = if (redBlink) Color(0xFFFF4040) else null, flash = false)
                }
            }
            // vaisseaux-options (power 5/6) : petits ailiers qui suivent, halo cyan
            for (p in g.optionPos) {
                drawCircle(CYAN.copy(alpha = 0.28f), radius = g.u * 4.2f, center = p)
                drawSpriteImg(playerImg(spr, g.ship), p.x, p.y, g.u * 7f, rot180 = false, tint = null, flash = false)
            }
        }

        // particules : pixels nets (braises)
        for (p in g.particles) {
            val a = (p.life / p.maxLife).coerceIn(0f, 1f)
            drawRect(p.color.copy(alpha = a),
                topLeft = Offset(p.x - p.size / 2, p.y - p.size / 2), size = Size(p.size, p.size))
        }

        // éclats d'impact : étoile blanche à 4 branches qui rétrécit vite
        for (s in g.sparks) {
            val a = (s.life / 0.14f).coerceIn(0f, 1f)
            val r = g.u * (1.2f + 2.6f * a)
            drawRect(Color.White.copy(alpha = a), topLeft = Offset(s.x - r, s.y - g.u * 0.35f), size = Size(r * 2f, g.u * 0.7f))
            drawRect(Color.White.copy(alpha = a), topLeft = Offset(s.x - g.u * 0.35f, s.y - r), size = Size(g.u * 0.7f, r * 2f))
            drawCircle(GOLD.copy(alpha = a * 0.6f), radius = r * 0.5f, center = Offset(s.x, s.y))
        }

        // ondes de choc d'explosion (anneaux qui grandissent et s'estompent)
        for (b in g.blasts) {
            val p = (b.age / b.dur).coerceIn(0f, 1f)
            val r = b.maxR * (1f - (1f - p) * (1f - p))   // ease-out
            val a = (1f - p)
            drawCircle(b.color.copy(alpha = a * 0.8f), radius = r,
                center = Offset(b.x, b.y),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = g.u * (1.4f + 2.6f * b.thick) * (1f - p * 0.6f)))
            if (b.color == Color.White) {   // flash blanc plein au tout début
                drawCircle(Color.White.copy(alpha = a * a * 0.5f), radius = r * 0.7f, center = Offset(b.x, b.y))
            }
        }
        // explosions en SPRITE IA (grandissent un peu + s'estompent)
        for (b in g.spriteBursts) {
            val p = (b.age / b.dur).coerceIn(0f, 1f)
            val sz = b.size * (0.7f + 0.5f * p)          // grandit legerement
            val a = (1f - p * p)                          // s'estompe
            val img = b.img
            val dh = sz * img.height / img.width
            drawImage(
                image = img, srcOffset = IntOffset.Zero, srcSize = IntSize(img.width, img.height),
                dstOffset = IntOffset((b.x - sz / 2f).toInt(), (b.y - dh / 2f).toInt()),
                dstSize = IntSize(sz.toInt(), dh.toInt()),
                alpha = a.coerceIn(0f, 1f), filterQuality = FilterQuality.None
            )
        }
    }

    // textes flottants (score, combos) et effets liés au vaisseau : coordonnées MONDE
    translate(-g.camX, 0f) {
    drawIntoCanvas2(g)
    // « POWER UP » : sprite IA flottant (au lieu du texte procédural)
    spr.putext?.let { pu ->
        for (t in g.texts) {
            if (!t.powerup) continue
            val a = (t.life / 1.1f).coerceIn(0f, 1f)
            val wsp = g.u * 28f
            val hsp = wsp * pu.height / pu.width
            drawImage(
                image = pu,
                srcOffset = IntOffset.Zero, srcSize = IntSize(pu.width, pu.height),
                dstOffset = IntOffset((t.x - wsp / 2f).toInt(), (t.y - hsp / 2f).toInt()),
                dstSize = IntSize(wsp.toInt(), hsp.toInt()),
                alpha = a, filterQuality = FilterQuality.None
            )
        }
    }
    // Montants de pieces (+N$) : rendus avec la POLICE IA validee (glyphes 0..9 + $), sinon texte.
    val cf = spr.coinFont
    if (cf.size >= 11) {
        val gh = g.u * 4.2f
        for (t in g.texts) {
            if (!t.coin) continue
            val a = (t.life / 0.9f).coerceIn(0f, 1f)
            // largeur totale
            val widths = t.text.map { ch ->
                val gi = if (ch == '$') 10 else ch - '0'
                if (gi in 0..10) { val im = cf[gi]; gh * im.width / im.height } else gh * 0.5f
            }
            val total = widths.sum()
            var gx = t.x - total / 2f
            for ((i, ch) in t.text.withIndex()) {
                val gi = if (ch == '$') 10 else ch - '0'
                if (gi in 0..10) {
                    val im = cf[gi]; val gw = widths[i]
                    drawImage(
                        image = im, srcOffset = IntOffset.Zero, srcSize = IntSize(im.width, im.height),
                        dstOffset = IntOffset(gx.toInt(), (t.y - gh / 2f).toInt()),
                        dstSize = IntSize(gw.toInt(), gh.toInt()),
                        alpha = a, filterQuality = FilterQuality.None
                    )
                }
                gx += widths[i]
            }
        }
    }

    // bombe : onde de choc (centrée sur le vaisseau, espace monde)
    if (g.bombWave >= 0f) {
        val maxR = hypot(w, h)
        val r = g.bombWave * maxR
        val a = (1f - g.bombWave)
        drawCircle(CYAN_HOT.copy(alpha = a * 0.5f), radius = r,
            center = Offset(g.px, g.py), style = androidx.compose.ui.graphics.drawscope.Stroke(width = g.u * 3f))
        drawCircle(Color.White.copy(alpha = a * 0.35f), radius = r * 0.82f,
            center = Offset(g.px, g.py), style = androidx.compose.ui.graphics.drawscope.Stroke(width = g.u * 1.6f))
    }
    // champ de force autour du vaisseau pendant l'explosion de la bombe
    if (g.bombShield > 0f) {
        val a = (g.bombShield / 1.7f).coerceIn(0f, 1f)
        val r = g.u * 9.5f * (1f + 0.06f * sin(g.bombShield * 22f))
        drawCircle(CYAN.copy(alpha = 0.14f * a), radius = r, center = Offset(g.px, g.py))
        drawCircle(CYAN_HOT.copy(alpha = 0.6f * a), radius = r,
            center = Offset(g.px, g.py), style = androidx.compose.ui.graphics.drawscope.Stroke(width = g.u * 1f))
        drawCircle(Color.White.copy(alpha = 0.45f * a), radius = r * 0.8f,
            center = Offset(g.px, g.py), style = androidx.compose.ui.graphics.drawscope.Stroke(width = g.u * 0.5f))
    }
    }   // fin espace monde (textes flottants + effets vaisseau)

    // flash blanc de bombe (plein écran)
    if (g.bombFlash > 0f) {
        drawRect(Color.White.copy(alpha = g.bombFlash * 0.55f), size = size)
    }
    // flash BREAK (plein écran)
    if (g.breakFlash > 0f) {
        drawRect(GOLD.copy(alpha = g.breakFlash * 0.25f), size = size)
    }
    // flash d'impact (boss détruit = blanc, joueur touché = rouge)
    if (g.flash > 0f) {
        drawRect(g.flashCol.copy(alpha = (g.flash * 0.55f).coerceAtMost(0.55f)), size = size)
    }
}

/** Sprite du vaisseau choisi (BRAINROT est dessiné via le logo, fallback ici). */
private fun shipSprite(ship: ShShip): Sprite = when (ship) {
    ShShip.VULCAN -> SPR_SHIP
    ShShip.SPREAD -> SPR_SHIP_SPREAD
    ShShip.LASER -> SPR_SHIP_LASER
    ShShip.BEE -> SPR_SHIP_SPREAD
    ShShip.BRAINROT -> SPR_SHIP
}

private fun DrawScope.drawIntoCanvas2(g: ShmupGame) {
    if (g.texts.isEmpty()) return
    drawIntoCanvas { c ->
        val paint = android.graphics.Paint().apply {
            isAntiAlias = false                       // pixel net (pas d'anticrénelage)
            textAlign = android.graphics.Paint.Align.CENTER
            typeface = shmupPixelTypeface             // fonte pixel-art (Silkscreen)
        }
        for (t in g.texts) {
            if (t.powerup || t.coin) continue          // rendus via sprite/glyphes (voir drawGame)
            val a = (t.life / 0.9f).coerceIn(0f, 1f)
            paint.textSize = g.u * 4.6f
            paint.color = android.graphics.Color.argb(
                (a * 255).toInt(),
                (t.color.red * 255).toInt(), (t.color.green * 255).toInt(), (t.color.blue * 255).toInt()
            )
            c.nativeCanvas.drawText(t.text, t.x, t.y, paint)
        }
    }
}

private fun DrawScope.drawCircleGlow(x: Float, y: Float, r: Float, color: Color) {
    drawCircle(color.copy(alpha = 0.22f), radius = r * 2.4f, center = Offset(x, y))
    drawCircle(color, radius = r, center = Offset(x, y))
    drawCircle(Color.White.copy(alpha = 0.85f), radius = r * 0.45f, center = Offset(x, y))
}

// Pièce en pixel-art : rebord sombre, face en dégradé (lumière en haut-gauche),
// pip central embossé. Rendue avec une largeur qui oscille → effet « pièce qui tourne ».
private val PS_COIN = PSprite(listOf(
    "...#####...",
    ".##HHLLD##.",
    ".#HHLLMMD#.",
    "#HHLLMMMDD#",
    "#HLLMMMMDD#",
    "#HLLMDMMDD#",
    "#HLLMMMMDD#",
    "#HDLMMMMDD#",
    ".#DDLMMDD#.",
    ".##DDDDD##.",
    "...#####..."
))

// Gemme taillée « brillant » : couronne claire (table), ceinture large, pavillon en pointe.
// W = éclat blanc, H reflet, L clair, M teinte, D ombre, # contour.
private val PS_GEM = PSprite(listOf(
    "...#####...",
    "..#WLHLW#..",
    ".#HLLWLLH#.",
    "#HLLMWMLLD#",
    "#LLMMWMMLD#",
    ".#LMMWMMD#.",
    ".#DLMWMLD#.",
    "..#DLWLD#..",
    "..#DDMDD#..",
    "...#DLD#...",
    "....#D#...."
))

// Lingot d'or brillant (trapèze empilé).
private val PS_INGOT = PSprite(listOf(
    "...#####...",
    "..#HHHHH#..",
    ".#HLLLLLD#.",
    "#HLLMMMLLD#",
    "#LMMMMMMMD#",
    "#DDDDDDDDD#",
    ".#########."
))

private fun DrawScope.drawCoin(x: Float, y: Float, u: Float, t: Float, value: Int,
                              coinImg: ImageBitmap? = null, gemImg: ImageBitmap? = null,
                              treImg: ImageBitmap? = null) {
    val base = coinColor(value)
    // Sprites IA validés : un design DÉDIÉ par valeur (treImg) sinon pièce/gemme générique, taille par tier.
    val img = treImg ?: if (value <= 5) coinImg else gemImg
    if (img != null) {
        val sz = u * (5.2f + when (value) { 1 -> 0f; 5 -> 0.6f; 10 -> 1.0f; 20 -> 1.4f; 50 -> 1.9f; else -> 2.5f })
        drawCircle(base.copy(alpha = 0.22f), radius = sz * 0.55f, center = Offset(x, y))
        drawSpriteImg(img, x, y + sin(t * 4f) * u * 0.4f, sz, rot180 = false, tint = null, flash = false)
        return
    }
    when (value) {
        1, 5 -> drawSpinCoin(x, y, u, t, base)                    // pièces (argent / or)
        10, 20 -> drawGem(x, y, u, t, base, big = false)          // rubis / saphir
        50 -> drawIngot(x, y, u, t)                               // lingot d'or
        else -> drawGem(x, y, u, t, base, big = true)             // diamant
    }
}

/** Pièce pixel-art qui tourne (largeur qui oscille). */
private fun DrawScope.drawSpinCoin(x: Float, y: Float, u: Float, t: Float, base: Color) {
    val flip = kotlin.math.abs(sin(t * 5f)).coerceAtLeast(0.14f)
    val sp = PS_COIN
    val py = u * 0.52f
    val pxu = py * flip
    drawCircle(base.copy(alpha = 0.24f), radius = sp.h * py * 0.62f, center = Offset(x, y))
    val ox = x - sp.w * pxu / 2f
    val oy = y - sp.h * py / 2f
    for (r in 0 until sp.h) {
        val row = sp.rows[r]
        for (c in 0 until sp.w) {
            val ch = if (c < row.length) row[c] else '.'
            if (ch == '.') continue
            val col = shadeColor(ch, base) ?: continue
            drawRect(col, topLeft = Offset(ox + c * pxu, oy + r * py), size = Size(pxu + 0.7f, py + 0.7f))
        }
    }
    if (flip > 0.62f) {
        drawRect(Color.White.copy(alpha = 0.85f),
            topLeft = Offset(x - pxu * 1.1f, y - py * 2.6f), size = Size(pxu * 1.0f, py * 5.2f))
    }
}

/** Gemme taillée (rubis/saphir/diamant) : vrai sprite pixel-art facetté brillant + scintillement. */
private fun DrawScope.drawGem(x: Float, y: Float, u: Float, t: Float, base: Color, big: Boolean) {
    val sp = PS_GEM
    val flip = 0.5f + 0.5f * kotlin.math.abs(sin(t * 3.2f))     // rotation = largeur qui oscille
    val pixY = u * (if (big) 0.62f else 0.52f)
    val pixX = pixY * (0.55f + 0.45f * flip)
    // halo
    drawCircle(base.copy(alpha = if (big) 0.40f else 0.28f), radius = sp.h * pixY * 0.62f, center = Offset(x, y))
    val ox = x - sp.w * pixX / 2f
    val oy = y - sp.h * pixY / 2f
    for (r in 0 until sp.h) {
        val row = sp.rows[r]
        for (c in 0 until sp.w) {
            val ch = if (c < row.length) row[c] else '.'
            if (ch == '.') continue
            val col = shadeColor(ch, base) ?: continue
            drawRect(col, topLeft = Offset(ox + c * pixX, oy + r * pixY), size = Size(pixX + 0.7f, pixY + 0.7f))
        }
    }
    // scintillement en croix (éclat) synchronisé
    val gl = 0.5f + 0.5f * kotlin.math.abs(sin(t * 3.2f + 1.1f))
    if (gl > 0.72f) {
        val s = sp.h * pixY * 0.55f * gl
        drawRect(Color.White.copy(alpha = gl), topLeft = Offset(x - s * 0.06f, y - s), size = Size(s * 0.12f, s * 2f))
        drawRect(Color.White.copy(alpha = gl), topLeft = Offset(x - s, y - s * 0.06f), size = Size(s * 2f, s * 0.12f))
    }
}

/** Lingot d'or : vrai sprite pixel-art brillant qui oscille doucement + reflet qui balaie. */
private fun DrawScope.drawIngot(x: Float, y: Float, u: Float, t: Float) {
    val sp = PS_INGOT
    val pix = u * 0.62f
    drawCircle(GOLD.copy(alpha = 0.36f), radius = sp.w * pix * 0.6f, center = Offset(x, y))
    rotate(degrees = sin(t * 3f) * 4f, pivot = Offset(x, y)) {
        val ox = x - sp.w * pix / 2f
        val oy = y - sp.h * pix / 2f
        for (r in 0 until sp.h) {
            val row = sp.rows[r]
            for (c in 0 until sp.w) {
                val ch = if (c < row.length) row[c] else '.'
                if (ch == '.') continue
                val col = shadeColor(ch, GOLD) ?: continue
                drawRect(col, topLeft = Offset(ox + c * pix, oy + r * pix), size = Size(pix + 0.7f, pix + 0.7f))
            }
        }
        // reflet vertical qui balaie la barre
        val sweep = ox + (0.2f + 0.6f * (0.5f + 0.5f * sin(t * 2.5f))) * sp.w * pix
        drawRect(Color.White.copy(alpha = 0.7f), topLeft = Offset(sweep, oy + pix), size = Size(pix * 0.9f, sp.h * pix - pix * 2f))
    }
}

/**
 * Vague D — habillage 3D discret : crags (stages impairs) ou tours (stages pairs) qui
 * « survolent » depuis l'horizon vers le bas avec grossissement en perspective.
 * Contraintes (DA Crimson Sky) : uniquement sur les FLANCS (jamais sous le vaisseau),
 * très faible contraste, compatible avec les fonds image IA. La face latérale plus
 * sombre donne le volume ; l'alpha suit le réglage de luminosité du décor.
 */
private fun DrawScope.draw3DRelief(g: ShmupGame) {
    val w = size.width; val h = size.height
    val stage = g.level
    val building = stage % 2 == 0
    val bgI = com.goodbrainrot.app.Settings.bgIntensity.floatValue.coerceIn(0f, 1f)
    val baseA = 0.05f + 0.07f * (1f - bgI * 0.5f)   // décor lumineux -> relief encore plus discret
    val horizonY = h * 0.30f
    val col = g.theme.star
    val n = 5
    for (side in 0..1) {
        val dir = if (side == 0) -1f else 1f
        for (i in 0 until n) {
            val r1 = (((i + 1) * 48271 + side * 9349 + stage * 7919) and 0xffff) / 65535f
            val r2 = (((i + 7) * 16807 + side * 4241 + stage * 3571) and 0xffff) / 65535f
            val speed = 0.05f + 0.05f * r1
            val t = ((g.bgScroll * speed + i.toFloat() / n + r1) % 1f + 1f) % 1f
            val prog = t * t * (0.4f + 0.6f * t)                 // lent à l'horizon, rapide en bas
            val s = 0.12f + 0.88f * prog                          // échelle perspective
            val cx = w / 2f
            val x = cx + dir * w * (0.20f + 0.30f * r2) * (0.35f + 0.65f * prog)
            val y = horizonY + (h * 1.18f - horizonY) * prog
            val bw = w * 0.11f * (0.7f + 0.6f * r1) * s
            val bh = h * 0.17f * (0.8f + 0.8f * r2) * s
            val a = (baseA * (0.35f + 0.65f * prog)).coerceAtMost(0.16f)
            val front = col.copy(alpha = a)
            val flank = col.copy(alpha = a * 0.45f)
            val sideW = bw * 0.30f * prog                         // face latérale visible côté centre
            if (building) {
                drawRect(front, topLeft = Offset(x - bw / 2f, y - bh), size = Size(bw, bh))
                val inner = x - dir * bw / 2f
                val p = Path()
                p.moveTo(inner, y - bh)
                p.lineTo(inner - dir * sideW, y - bh + bh * 0.06f)
                p.lineTo(inner - dir * sideW, y + bh * 0.06f)
                p.lineTo(inner, y)
                p.close()
                drawPath(p, flank)
                // arête de toit à peine plus claire pour accrocher la perspective
                drawRect(col.copy(alpha = a * 1.4f), topLeft = Offset(x - bw / 2f, y - bh), size = Size(bw, bh * 0.045f))
            } else {
                val p = Path()
                p.moveTo(x - bw / 2f, y)
                p.lineTo(x, y - bh)
                p.lineTo(x + bw / 2f, y)
                p.close()
                drawPath(p, front)
                val f = Path()                                    // versant côté centre, plus sombre
                f.moveTo(x, y - bh)
                f.lineTo(x - dir * bw / 2f, y)
                f.lineTo(x - dir * (bw / 2f + sideW), y + bh * 0.05f)
                f.close()
                drawPath(f, flank)
            }
        }
    }
}

/**
 * Décor de relief en parallax : 2-3 couches de silhouettes (montagnes ou bâtiments selon
 * le stage) qui défilent vers le bas, à TRÈS faible contraste pour ne jamais masquer
 * les balles/ennemis. Purement procédural (pas d'image).
 */
private fun DrawScope.drawParallaxTerrain(g: ShmupGame) {
    val w = size.width; val h = size.height
    val stage = g.level
    val building = stage % 2 == 0
    val cols = 9
    val cw = w / cols
    for (layer in 0..1) {
        val speed = if (layer == 0) 0.045f else 0.09f
        val period = h * 0.62f
        val baseOff = (g.bgScroll * speed * h) % period
        val col = g.theme.star.copy(alpha = if (layer == 0) 0.055f else 0.10f)
        for (rep in 0..2) {
            val topY = baseOff + (rep - 1) * period
            if (building) {
                for (c in 0 until cols) {
                    val rnd = (((c + 3) * 92837 + layer * 131 + stage * 71) and 0xffff) / 65535f
                    val bh = period * (0.22f + rnd * 0.5f)
                    drawRect(col, topLeft = Offset(c * cw + cw * 0.12f, topY - bh), size = Size(cw * 0.76f, bh))
                    // quelques "fenêtres" à peine visibles
                    drawRect(col.copy(alpha = col.alpha * 1.6f),
                        topLeft = Offset(c * cw + cw * 0.34f, topY - bh * 0.7f), size = Size(cw * 0.12f, bh * 0.5f))
                }
            } else {
                val path = Path()
                path.moveTo(0f, topY + period)
                path.lineTo(0f, topY)
                for (c in 0..cols) {
                    val rnd = (((c + 1) * 92837 + layer * 131 + stage * 71) and 0xffff) / 65535f
                    val peak = topY - period * (0.18f + rnd * 0.42f)
                    path.lineTo(c * cw, peak)
                    if (c < cols) path.lineTo((c + 0.5f) * cw, topY - period * 0.04f)
                }
                path.lineTo(w, topY); path.lineTo(w, topY + period); path.close()
                drawPath(path, col)
            }
        }
    }
}

private val PS_MEGA = PSprite(listOf(
    "...WWWW...",
    "..W#YY#W..",
    ".W#YWWY#W.",
    "W#YW##WY#W",
    "W#YW##WY#W",
    "W#YWWWWY#W",
    ".W#YYYY#W.",
    "..W#YY#W..",
    "...WWWW..."
))

/** Gros canon fixe (destructible) qui tire vers le bas. */
private fun DrawScope.drawCannon(e: Enemy, u: Float, spr: ShSprites) {
    val x = e.x; val y = e.y
    val flash = e.hitFlash > 0f
    // Destructible IA (validé) : dessine le sprite + barre de PV, sinon le canon procédural.
    val cimg = e.spriteImg
    if (cimg != null) {
        drawSpriteImg(cimg, x, y, u * 14f, rot180 = false, tint = null, flash = flash)
        val fr = (e.hp.toFloat() / e.maxHp).coerceIn(0f, 1f)
        drawRect(Color(0xAA000000), topLeft = Offset(x - u * 6f, y - u * 8f), size = Size(u * 12f, u * 1.1f))
        drawRect(if (fr > 0.4f) ORANGE else CRIM, topLeft = Offset(x - u * 6f, y - u * 8f), size = Size(u * 12f * fr, u * 1.1f))
        return
    }
    val base = if (flash) Color.White else Color(0xFF3A3F4A)
    val baseL = if (flash) Color.White else Color(0xFF556070)
    drawCircle(base, radius = u * 6f, center = Offset(x, y - u * 1.5f))
    drawRect(baseL, topLeft = Offset(x - u * 6f, y - u * 3f), size = Size(u * 12f, u * 3f))
    drawRect(if (flash) Color.White else Color(0xFF23272F), topLeft = Offset(x - u * 2f, y - u * 1f), size = Size(u * 4f, u * 7f)) // tube
    drawRect(Color(0xFFFF7A2A), topLeft = Offset(x - u * 2f, y + u * 5.5f), size = Size(u * 4f, u * 1.3f)) // bouche
    drawCircle(Color(0xFF8A94A6), radius = u * 0.7f, center = Offset(x - u * 3.5f, y - u * 1.5f))
    drawCircle(Color(0xFF8A94A6), radius = u * 0.7f, center = Offset(x + u * 3.5f, y - u * 1.5f))
    val frac = (e.hp.toFloat() / e.maxHp).coerceIn(0f, 1f)
    drawRect(Color(0xAA000000), topLeft = Offset(x - u * 6f, y - u * 7f), size = Size(u * 12f, u * 1.1f))
    drawRect(if (frac > 0.4f) ORANGE else CRIM, topLeft = Offset(x - u * 6f, y - u * 7f), size = Size(u * 12f * frac, u * 1.1f))
}

private fun DrawScope.drawPowerUp(x: Float, y: Float, u: Float, mega: Boolean = false, t: Float = 0f,
                                  giant: Boolean = false, blackhole: Boolean = false,
                                  puImg: ImageBitmap? = null, bhImg: ImageBitmap? = null,
                                  giantImg: ImageBitmap? = null, megaImg: ImageBitmap? = null) {
    if (blackhole) {
        // Tourbillon (trou noir) : sprite IA qui tourne, sinon spirale procédurale.
        if (bhImg != null) {
            val pl = 1f + 0.08f * sin(t * 6f)
            drawCircle(Color(0xFF7A3CE0).copy(alpha = 0.22f), radius = u * 7.5f * pl, center = Offset(x, y))
            rotate(t * 110f, pivot = Offset(x, y)) {
                drawSpriteImg(bhImg, x, y, u * 11f * pl, rot180 = false, tint = null, flash = false)
            }
            return
        }
        // Trou noir : disque noir cerné d'un anneau qui tourne (spirale d'accrétion violette/cyan).
        val pulse = 1f + 0.08f * sin(t * 6f)
        for (k in 5 downTo 1) {
            val rr = u * (2.2f + k * 1.4f) * pulse
            val a = 0.10f + 0.10f * k
            drawCircle(Color(0xFF7A3CE0).copy(alpha = a * 0.5f), radius = rr, center = Offset(x, y),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = u * 0.8f))
        }
        // bras de spirale
        for (k in 0..3) {
            val a = t * 4f + k * 1.57f
            val rr = u * 5.2f
            drawCircle(CYAN_HOT.copy(alpha = 0.7f), radius = u * 0.7f,
                center = Offset(x + cos(a) * rr, y + sin(a) * rr))
        }
        drawCircle(Color(0xFF120018), radius = u * 3.4f * pulse, center = Offset(x, y))
        drawCircle(Color(0xFF3A0A5A), radius = u * 3.4f * pulse, center = Offset(x, y),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = u * 0.7f))
        return
    }
    if (giant) {
        val pulse = 1f + 0.16f * sin(t * 8f)
        // Sprite IA validé (halo + flèches d'expansion conservés), sinon capsule procédurale.
        if (giantImg != null) {
            drawCircle(CYAN.copy(alpha = 0.28f), radius = u * 9f * pulse, center = Offset(x, y))
            drawCircle(Color.White.copy(alpha = 0.20f), radius = u * 6.5f * pulse, center = Offset(x, y))
            drawSpriteImg(giantImg, x, y, u * 11f * pulse, rot180 = false, tint = null, flash = false)
            for (k in 0..3) {
                val a = t * 2.4f + k * 1.57f
                val d = u * (6.5f + 1.2f * sin(t * 8f))
                drawRect(CYAN.copy(alpha = 0.85f),
                    topLeft = Offset(x + cos(a) * d - u * 0.5f, y + sin(a) * d - u * 0.5f), size = Size(u, u))
            }
            return
        }
        // Item géant : capsule cyan/blanche « + » qui pulse fort, halo large + flèches d'expansion.
        drawCircle(CYAN.copy(alpha = 0.28f), radius = u * 9f * pulse, center = Offset(x, y))
        drawCircle(Color.White.copy(alpha = 0.20f), radius = u * 6.5f * pulse, center = Offset(x, y))
        val s = u * 5.4f * pulse
        drawRect(CYAN_HOT, topLeft = Offset(x - s / 2, y - s / 2), size = Size(s, s))
        drawRect(Color.White, topLeft = Offset(x - s * 0.16f, y - s * 0.46f), size = Size(s * 0.32f, s * 0.92f))
        drawRect(Color.White, topLeft = Offset(x - s * 0.46f, y - s * 0.16f), size = Size(s * 0.92f, s * 0.32f))
        // flèches d'expansion aux 4 coins (indiquent « grandir »)
        for (k in 0..3) {
            val a = t * 2.4f + k * 1.57f
            val d = u * (6.5f + 1.2f * sin(t * 8f))
            drawRect(CYAN.copy(alpha = 0.85f),
                topLeft = Offset(x + cos(a) * d - u * 0.5f, y + sin(a) * d - u * 0.5f), size = Size(u, u))
        }
        return
    }
    if (mega) {
        // MÉGA power-up : capsule pixel-art dorée qui pulse, halo arc-en-ciel + étincelles.
        val pulse = 1f + 0.12f * sin(t * 7f)
        val rainbow = Color.hsv((t * 90f) % 360f, 0.7f, 1f)
        drawCircle(rainbow.copy(alpha = 0.30f), radius = u * 8.5f * pulse, center = Offset(x, y))
        drawCircle(Color.White.copy(alpha = 0.25f), radius = u * 6f * pulse, center = Offset(x, y))
        // Sprite IA valide (halo arc-en-ciel conserve), sinon capsule procedurale.
        if (megaImg != null) {
            drawSpriteImg(megaImg, x, y, u * 11f * pulse, rot180 = false, tint = null, flash = false)
            return
        }
        val sp = PS_MEGA
        val pix = u * 1.1f * pulse
        val ox = x - sp.w * pix / 2f; val oy = y - sp.h * pix / 2f
        for (r in 0 until sp.h) {
            val row = sp.rows[r]
            for (c in 0 until sp.w) {
                val ch = if (c < row.length) row[c] else '.'
                if (ch == '.') continue
                val col = when (ch) {
                    'W' -> rainbow                       // contour arc-en-ciel animé
                    '#' -> Color(0xFF5A3A00)
                    'Y' -> GOLD
                    else -> Color.White
                }
                drawRect(col, topLeft = Offset(ox + c * pix, oy + r * pix), size = Size(pix + 0.7f, pix + 0.7f))
            }
        }
        // étincelles qui tournent
        for (k in 0..3) {
            val a = t * 3f + k * 1.57f
            drawRect(Color.White, topLeft = Offset(x + cos(a) * u * 6f - u * 0.4f, y + sin(a) * u * 6f - u * 0.4f), size = Size(u * 0.8f, u * 0.8f))
        }
        return
    }
    // Power-up : sprite IA validé (halo cyan pulsé), sinon cube vert « + ».
    if (puImg != null) {
        val pl = 1f + 0.10f * sin(t * 5f)
        drawCircle(CYAN.copy(alpha = 0.22f), radius = u * 6.8f * pl, center = Offset(x, y))
        drawSpriteImg(puImg, x, y, u * 10f * pl, rot180 = false, tint = null, flash = false)
        return
    }
    val s = u * 4f
    drawCircle(GREEN.copy(alpha = 0.28f), radius = s * 1.4f, center = Offset(x, y))
    drawRect(GREEN, topLeft = Offset(x - s / 2, y - s / 2), size = Size(s, s))
    drawRect(Color(0xFF0A3015), topLeft = Offset(x - s * 0.18f, y - s * 0.42f), size = Size(s * 0.36f, s * 0.84f))
    drawRect(Color(0xFF0A3015), topLeft = Offset(x - s * 0.42f, y - s * 0.18f), size = Size(s * 0.84f, s * 0.36f))
}

/** Obstacle : mur de briques ou bâtiment (fenêtres) — pixel-art, s'assombrit en s'abîmant. */
private fun DrawScope.drawObstacle(o: Obstacle, u: Float) {
    val x = o.x; val y = o.y
    val flash = o.hitFlash > 0f
    val dmg = 1f - (o.hp.toFloat() / o.maxHp).coerceIn(0f, 1f)   // 0 intact -> 1 en ruine
    // Sprite destructible IA (rôle « bloc ») : tuilé verticalement, teinte de dégâts. Sinon briques.
    val sImg = o.spriteImg
    if (sImg != null) {
        val left = x - o.halfW; val top = y - o.halfH
        val fw = o.halfW * 2f; val fh = o.halfH * 2f
        val n = max(1, (fh / fw).roundToInt())
        val cell = fh / n
        val filter = when {
            flash -> ColorFilter.tint(Color.White, BlendMode.SrcAtop)
            dmg > 0.05f -> ColorFilter.tint(Color(0xFF000000).copy(alpha = dmg * 0.5f), BlendMode.SrcAtop)
            else -> null
        }
        for (i in 0 until n) {
            drawImage(
                image = sImg,
                srcOffset = IntOffset.Zero, srcSize = IntSize(sImg.width, sImg.height),
                dstOffset = IntOffset(left.toInt(), (top + i * cell).toInt()),
                dstSize = IntSize(fw.toInt(), cell.toInt() + 1),
                colorFilter = filter, filterQuality = FilterQuality.None
            )
        }
        return
    }
    fun w(c: Color) = if (flash) Color.White else c
    val brick = o.kind == ObKind.WALL
    val base = if (brick) Color(0xFF9A5A34) else Color(0xFF5C6472)
    // fissuré = teinte qui fonce avec les dégâts
    val body = w(base.darken(dmg * 0.5f))
    val outline = w(base.darken(0.6f))
    val left = x - o.halfW; val top = y - o.halfH; val ww = o.halfW * 2; val hh = o.halfH * 2
    drawRect(outline, topLeft = Offset(left, top), size = Size(ww, hh))
    drawRect(body, topLeft = Offset(left + u * 0.5f, top + u * 0.5f), size = Size(ww - u, hh - u))
    // reflet haut
    drawRect(w(base.lighten(0.28f)), topLeft = Offset(left + u * 0.5f, top + u * 0.5f), size = Size(ww - u, u * 0.9f))
    if (brick) {
        // rangées de briques (joints sombres)
        var ry = top + u * 2f; var row = 0
        while (ry < top + hh - u) {
            drawRect(Color(0x55000000), topLeft = Offset(left + u * 0.5f, ry), size = Size(ww - u, u * 0.4f))
            val off = if (row % 2 == 0) 0f else o.halfW * 0.5f
            var rx = left + off
            while (rx < left + ww) {
                drawRect(Color(0x44000000), topLeft = Offset(rx, ry), size = Size(u * 0.4f, u * 2f))
                rx += o.halfW
            }
            ry += u * 2f; row++
        }
    } else {
        // fenêtres du bâtiment (allumées / éteintes)
        val cols = 2; val rows = 4
        val mw = ww * 0.7f; val mh = hh * 0.8f
        val gx = left + (ww - mw) / 2f; val gy = top + (hh - mh) / 2f
        val cw = mw / cols; val ch = mh / rows
        for (cc in 0 until cols) for (rr in 0 until rows) {
            val lit = ((cc + rr * 7 + o.x.toInt()) % 3) != 0
            val wc = if (lit) Color(0xFFFFE08A) else Color(0xFF20242E)
            drawRect(w(wc).copy(alpha = if (flash) 1f else 0.9f),
                topLeft = Offset(gx + cc * cw + cw * 0.18f, gy + rr * ch + ch * 0.18f),
                size = Size(cw * 0.64f, ch * 0.64f))
        }
    }
    // fissures quand bien abîmé
    if (dmg > 0.5f) {
        drawRect(Color(0xAA000000), topLeft = Offset(x - u * 0.3f, top + u), size = Size(u * 0.6f, hh - u * 2f))
        drawRect(Color(0xAA000000), topLeft = Offset(left + u * 2f, y), size = Size(ww - u * 4f, u * 0.5f))
    }
}

private fun DrawScope.drawFlame(x: Float, y: Float, u: Float) {
    val flick = Random.nextFloat() * u * 2f
    // panache lissé (dégradé jaune -> orange -> transparent)
    drawCircle(Color(0x66FF8A3D), radius = u * 2f, center = Offset(x, y + u * 4.5f + flick))
    drawCircle(Color(0xEEFFE94A), radius = u * 1.1f, center = Offset(x, y + u * 4f))
    drawCircle(Color.White.copy(alpha = 0.8f), radius = u * 0.5f, center = Offset(x, y + u * 3.6f))
}

// ---------------------------------------------------------------------------
// Rendu lissé façon shmup CAVE (vaisseaux / ennemis vectoriels ombrés)
// ---------------------------------------------------------------------------

private fun Color.lighten(f: Float) = Color(red + (1f - red) * f, green + (1f - green) * f, blue + (1f - blue) * f, alpha)
private fun Color.darken(f: Float) = Color(red * (1f - f), green * (1f - f), blue * (1f - f), alpha)
private fun Color.mix(o: Color, t: Float) =
    Color(red + (o.red - red) * t, green + (o.green - green) * t, blue + (o.blue - blue) * t, 1f)

/** 3 variantes de teinte pour les ennemis d'une même vague (variété visuelle). */
private fun tintVariant(base: Color, shade: Int): Color = when (shade) {
    0 -> base
    1 -> base.lighten(0.30f)
    else -> base.mix(ORANGE, 0.38f)
}

/** Plaque de blindage biseautée (contour sombre, face, reflet haut, ombre bas) — look pixel-art. */
private fun DrawScope.bevel(cx: Float, cy: Float, hw: Float, hh: Float, base: Color, b: Float) {
    drawRect(base.darken(0.72f), topLeft = Offset(cx - hw, cy - hh), size = Size(hw * 2, hh * 2))
    drawRect(base, topLeft = Offset(cx - hw + b, cy - hh + b), size = Size(hw * 2 - b * 2, hh * 2 - b * 2))
    drawRect(base.lighten(0.38f), topLeft = Offset(cx - hw + b, cy - hh + b), size = Size(hw * 2 - b * 2, b * 1.3f))
    drawRect(base.darken(0.42f), topLeft = Offset(cx - hw + b, cy + hh - b * 2.3f), size = Size(hw * 2 - b * 2, b * 1.3f))
}

// Silhouettes (nez en haut, y négatif). x,y alternés en unités.
private val CRAFT_WIDE = floatArrayOf(
    0f, -9f, 2.5f, -3f, 2.5f, 1f, 8.5f, 4f, 3f, 4.5f, 3f, 8f, 0f, 6f, -3f, 8f, -3f, 4.5f, -8.5f, 4f, -2.5f, 1f, -2.5f, -3f
)
private val CRAFT_SHARP = floatArrayOf(
    0f, -10f, 1.6f, -3f, 2f, 1f, 6.5f, 5f, 2.5f, 5.5f, 2.5f, 8f, 0f, 6.5f, -2.5f, 8f, -2.5f, 5.5f, -6.5f, 5f, -2f, 1f, -1.6f, -3f
)

/** Chasseur vectoriel ombré. pointUp=false pour un ennemi (nez vers le bas). */
private fun DrawScope.drawCraft(cx: Float, cy: Float, s: Float, base: Color, canopy: Color,
                                pointUp: Boolean, sharp: Boolean, flash: Boolean = false, engine: Boolean = true) {
    val pts = if (sharp) CRAFT_SHARP else CRAFT_WIDE
    val d = if (pointUp) 1f else -1f
    val path = Path()
    var i = 0
    while (i < pts.size) {
        val x = cx + pts[i] * s
        val y = cy + d * pts[i + 1] * s
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        i += 2
    }
    path.close()
    drawCircle(base.copy(alpha = 0.18f), radius = s * 9f, center = Offset(cx, cy))
    val body = if (flash) Color.White else base
    val topY = cy - d * s * 10f
    val botY = cy + d * s * 8f
    drawPath(path, Brush.linearGradient(
        listOf(body.lighten(0.4f), body, body.darken(0.5f)),
        start = Offset(cx, topY), end = Offset(cx, botY)
    ), style = Fill)
    drawPath(path, (if (flash) Color.White else base.darken(0.65f)).copy(alpha = 0.9f), style = Stroke(width = s * 0.55f))
    if (!flash) {
        val cyp = cy - d * s * 2.5f
        drawCircle(canopy.darken(0.15f), radius = s * 2.1f, center = Offset(cx, cyp))
        drawCircle(canopy.lighten(0.55f), radius = s * 1.0f, center = Offset(cx - s * 0.5f, cyp - s * 0.5f))
    }
    if (engine) {
        val ey = cy + d * s * 7.8f
        drawCircle(Color(0x66FF8A3D), radius = s * 2.4f + Random.nextFloat() * s, center = Offset(cx, ey))
        drawCircle(Color(0xFFFFF3B0), radius = s * 1.1f, center = Offset(cx, ey))
    }
}

/** Couleur du vaisseau joueur (teinte de base du sprite ombré). */
private fun shipColors(ship: ShShip): Color = when (ship) {
    ShShip.VULCAN -> CRIM
    ShShip.SPREAD -> GREEN
    ShShip.LASER -> CYAN
    ShShip.BEE -> GOLD
    ShShip.BRAINROT -> Color(0xFFF4AFC9)
}

// ---------------------------------------------------------------------------
// Rendu pixel-art NET et détaillé (façon CAVE 97/98) : sprites ombrés à partir
// d'une couleur de base + balles « perles ». Aucun flou/bloom.
// ---------------------------------------------------------------------------

private fun shadeColor(ch: Char, base: Color): Color? = when (ch) {
    '#' -> base.darken(0.80f)   // contour quasi noir teinté
    'D' -> base.darken(0.45f)   // ombre
    'M' -> base                 // teinte
    'L' -> base.lighten(0.30f)  // clair
    'H' -> base.lighten(0.62f)  // reflet
    'C' -> CYAN                 // verrière
    'c' -> CYAN.darken(0.40f)
    'O' -> ORANGE               // réacteur
    'Y' -> GOLD
    'W' -> Color.White
    else -> null
}

private class PSprite(val rows: List<String>) { val w = rows.maxOf { it.length }; val h = rows.size }

private fun DrawScope.drawShaded(sp: PSprite, cx: Float, cy: Float, pixel: Float, base: Color, flash: Boolean) {
    val ox = cx - sp.w * pixel / 2f
    val oy = cy - sp.h * pixel / 2f
    for (r in 0 until sp.h) {
        val row = sp.rows[r]
        for (c in 0 until sp.w) {
            val ch = if (c < row.length) row[c] else '.'
            if (ch == '.') continue
            val color = if (flash) Color.White else (shadeColor(ch, base) ?: continue)
            drawRect(color, topLeft = Offset(ox + c * pixel, oy + r * pixel), size = Size(pixel + 0.7f, pixel + 0.7f))
        }
    }
}

// --- Sprites PNG (assets CC0 Kenney « Pixel Shmup ») ---
private class ShSprites(
    val red: ImageBitmap, val green: ImageBitmap, val blue: ImageBitmap, val bee: ImageBitmap,
    val enemyA: ImageBitmap, val enemyB: ImageBitmap,
    // Boss : sprites générés (assortis aux illustrations d'inter-stage)
    val mini: ImageBitmap,
    val b1: ImageBitmap, val b2: ImageBitmap, val b3: ImageBitmap,
    val b4: ImageBitmap, val b5: ImageBitmap,
    val stageBg: List<ImageBitmap?> = emptyList(),
    // Items IA validés (nullable) : power-up, tourbillon (trou noir), pièce, gemme
    val itemPu: ImageBitmap? = null, val itemBh: ImageBitmap? = null,
    val coin: ImageBitmap? = null, val gem: ImageBitmap? = null,
    // Trésors par valeur (1/5/10/20/50/100) : sprite dédié si dispo
    val treasures: Map<Int, ImageBitmap> = emptyMap(),
    // Item géant (grossissement) et bombe : sprites IA validés (nullable)
    val itemGiant: ImageBitmap? = null, val itemBomb: ImageBitmap? = null,
    // Texte « POWER UP » (sprite IA flottant au ramassage d'un power-up)
    val putext: ImageBitmap? = null,
    // Croc de serpent (projectile du boss serpent) : sprite oriente vers la droite (est) par defaut,
    // pivote en jeu selon la direction de tir.
    val fang: ImageBitmap? = null,
    // Power-up MAX (full power) : sprite IA valide.
    val itemMega: ImageBitmap? = null,
    // Explosions IA (petites -> grosses) : tirees au sort a chaque explosion.
    val explosions: List<ImageBitmap> = emptyList(),
    // Police des montants de pieces : glyphes 0..9 puis $ (indices 0..10).
    val coinFont: List<ImageBitmap> = emptyList(),
    // Projectiles ennemis (sprites IA) : assignés aléatoirement aux ennemis pour la variété
    val projSprites: List<ImageBitmap> = emptyList(),
    // Vague C : variantes de phase des boss (index 0..4 = stage 1..5), nullables.
    // p2 = abîmé (33-66 % PV), p3 = monstrueux (< 33 % PV).
    val bossP2: List<ImageBitmap?> = emptyList(),
    val bossP3: List<ImageBitmap?> = emptyList()
)

private fun playerDrawableRes(ship: ShShip): Int = when (ship) {
    ShShip.VULCAN -> R.drawable.ship_red
    ShShip.SPREAD -> R.drawable.ship_green
    ShShip.BEE -> R.drawable.ship_bee
    else -> R.drawable.ship_blue
}

private fun playerImg(s: ShSprites, ship: ShShip): ImageBitmap = when (ship) {
    ShShip.VULCAN -> s.red
    ShShip.SPREAD -> s.green
    ShShip.BEE -> s.bee
    else -> s.blue
}

/** Dessine un sprite PNG (pixel net) centré, avec rotation 180° optionnelle et teinte. */
private fun DrawScope.drawSpriteImg(img: ImageBitmap, cx: Float, cy: Float, dst: Float,
                                    rot180: Boolean, tint: Color?, flash: Boolean) {
    val half = dst / 2f
    val filter = when {
        flash -> ColorFilter.tint(Color.White, BlendMode.SrcAtop)
        tint != null -> ColorFilter.tint(tint, BlendMode.Modulate)
        else -> null
    }
    val body = {
        drawImage(
            image = img,
            srcOffset = IntOffset.Zero, srcSize = IntSize(img.width, img.height),
            dstOffset = IntOffset((cx - half).toInt(), (cy - half).toInt()),
            dstSize = IntSize(dst.toInt(), dst.toInt()),
            colorFilter = filter, filterQuality = FilterQuality.None
        )
    }
    if (rot180) rotate(180f, pivot = Offset(cx, cy)) { body() } else body()
}

/** Balle « perle » nette : contour sombre + corps saturé + reflet blanc (signature CAVE). */
private fun DrawScope.drawBead(x: Float, y: Float, r: Float, color: Color) {
    drawCircle(color.darken(0.55f), radius = r * 1.15f, center = Offset(x, y))
    drawCircle(color, radius = r, center = Offset(x, y))
    drawCircle(Color.White, radius = r * 0.42f, center = Offset(x - r * 0.28f, y - r * 0.28f))
}

/**
 * Boule ennemie en PIXEL ART : orbe construite sur une grille de cellules carrées
 * (pas d'anticrénelage), bord assombri + reflet blanc en haut-gauche. Look CAVE/16-bit.
 */
private fun DrawScope.drawPixelBead(x: Float, y: Float, r: Float, color: Color) {
    // Orbe pixel-art DÉTAILLÉE : grille 11×11 (rayon 5), dégradé sphérique + reflet spéculaire +
    // liseré lumineux (rim light) en bas-droite, pour un rendu net mais riche (façon CAVE).
    val n = 5
    val cell = (r / n * 1.28f).coerceAtLeast(1.2f)
    val dark = color.darken(0.55f)
    val edgeCol = color.darken(0.28f)
    val rim = color.lighten(0.5f)
    val hi = Color.White
    val edge2 = (n + 0.4f) * (n + 0.4f)
    for (gy in -n..n) for (gx in -n..n) {
        val d2 = (gx * gx + gy * gy).toFloat()
        if (d2 > edge2) continue
        val d = kotlin.math.sqrt(d2)
        // vecteur "lumière" en haut-gauche : plus on s'en approche, plus c'est clair
        val lit = (-(gx + gy)).toFloat()
        val col = when {
            gx <= -1 && gy <= -2 && d2 <= 5f -> hi                     // point spéculaire net
            d >= n - 0.2f -> edgeCol                                   // contour
            gx >= 1 && gy >= 1 && d >= n - 1.4f -> rim                 // rim light bas-droite
            lit >= 3.5f -> color.lighten(0.34f)
            lit >= 1.5f -> color.lighten(0.16f)
            lit <= -3.5f -> dark
            lit <= -1.5f -> color.darken(0.22f)
            else -> color
        }
        drawRect(col, topLeft = Offset(x + gx * cell - cell / 2f, y + gy * cell - cell / 2f),
            size = Size(cell + 0.55f, cell + 0.55f))
    }
}

/** Projectile « trou noir / tourbillon » : spirale d'accrétion qui tourne (rappelle l'ancien item). */
private fun DrawScope.drawVortex(x: Float, y: Float, r: Float, color: Color, t: Float) {
    val pulse = 1f + 0.1f * sin(t * 8f)
    drawCircle(color.copy(alpha = 0.18f), radius = r * 1.7f * pulse, center = Offset(x, y))
    for (k in 0..2) {
        val a = t * 6f + k * 2.094f
        val rr = r * 0.95f
        drawCircle(color.lighten(0.25f), radius = r * 0.42f, center = Offset(x + cos(a) * rr, y + sin(a) * rr))
        drawCircle(Color.White.copy(alpha = 0.85f), radius = r * 0.2f, center = Offset(x + cos(a) * rr, y + sin(a) * rr))
    }
    drawCircle(Color(0xFF120018), radius = r * 0.66f * pulse, center = Offset(x, y))
    drawCircle(color.lighten(0.35f), radius = r * 0.66f * pulse, center = Offset(x, y),
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = r * 0.16f))
}

/** Projectile « dent de serpent » (crochet incurvé pointu), orienté dans le sens du déplacement. */
private fun DrawScope.drawFang(x: Float, y: Float, r: Float, color: Color, angle: Float) {
    rotate(degrees = angle * 180f / PI.toFloat() + 90f, pivot = Offset(x, y)) {
        val wdt = r * 1.15f; val hgt = r * 2.8f
        // corps du crochet (contour sombre)
        val body = androidx.compose.ui.graphics.Path().apply {
            moveTo(x - wdt, y + hgt * 0.42f)
            quadraticBezierTo(x - wdt * 0.35f, y - hgt * 0.25f, x, y - hgt * 0.62f)   // flanc gauche -> pointe
            quadraticBezierTo(x + wdt * 0.55f, y - hgt * 0.1f, x + wdt, y + hgt * 0.42f) // flanc droit
            close()
        }
        drawPath(body, color.darken(0.45f))
        // émail (face claire) + reflet
        val face = androidx.compose.ui.graphics.Path().apply {
            moveTo(x - wdt * 0.62f, y + hgt * 0.34f)
            quadraticBezierTo(x - wdt * 0.2f, y - hgt * 0.18f, x - wdt * 0.02f, y - hgt * 0.5f)
            quadraticBezierTo(x + wdt * 0.3f, y - hgt * 0.05f, x + wdt * 0.5f, y + hgt * 0.34f)
            close()
        }
        drawPath(face, Color.White)
        drawPath(face, color.copy(alpha = 0.28f))
    }
}

// --- Sprites détaillés (top-lit : reflets en haut-gauche, ombres en bas-droite) ---

private val PS_SHIP = PSprite(listOf(
    "       WW       ",
    "      #MM#      ",
    "      #HM#      ",
    "     #MLLM#     ",
    "     #MCCM#     ",
    "    #MHCCMM#    ",
    "    #MLCCLM#    ",
    "   #MHLCCLMD#   ",
    "   #MMLCCLMD#   ",
    "  #MHMLCCLMMD#  ",
    "  #MHMMCCMMMD#  ",
    " #MDMMMCCMMMDD# ",
    "#MDMMMMMMMMMMDD#",
    "##DMMMMMMMMMMD##",
    " ##DMMMMMMMMD## ",
    "  #DMMDMMDMMD#  ",
    "   #DOO..OOD#   ",
    "    YO....OY    "
))

private val PS_DRONE = PSprite(listOf(
    "..#MMMMM#..",
    ".#MHMMMDM#.",
    "#MHMMMMMDM#",
    "#MMMWWWMMM#",
    "#MMWCcCWMM#",
    "#DMMWWWMMD#",
    ".#DMMMMMD#.",
    "..#DMMMD#..",
    "...#MDM#...",
    "....#D#...."
))

private val PS_INTER = PSprite(listOf(
    "...#MMM#...",
    "..#MHMDM#..",
    ".#MMMMMMM#.",
    ".#MMWCWMM#.",
    "#MMMWWWMMM#",
    "#DM#MMM#MD#",
    "..#MMMMM#..",
    "...#MMM#...",
    "..#D#.#D#..",
    ".#D#...#D#."
))

// --- bosses procéduraux (blocs pixel) ---

// --- Palette phases : la carrosserie chauffe (bleu/froid -> orange -> rouge critique) ---
private val PHASE_RED = Color(0xFFFF2A1E)
private val EMBER = Color(0xFFFFD24A)
private val SCORCH = Color(0xCC120309)

/**
 * Boss/mini-boss dessiné à partir d'un vrai sprite pixel-art, avec 3 phases visibles :
 * teinte qui chauffe (intact → orange → rouge) + dégâts progressifs (brûlures, fissure,
 * cœur exposé, braises, flicker), point faible, halo, barre de vie.
 */
private fun DrawScope.drawImageBoss(img: ImageBitmap, e: Enemy, u: Float, dstU: Float,
                                    rot180: Boolean, goldTint: Boolean, showBar: Boolean,
                                    tint: Color? = null, camX: Float = 0f,
                                    imgDamaged: ImageBitmap? = null, imgMonster: ImageBitmap? = null) {
    val x = e.x; val y = e.y
    val frac = (e.hp.toFloat() / e.maxHp).coerceIn(0f, 1f)
    val phase = when { frac > 0.66f -> 0; frac > 0.33f -> 1; else -> 2 }
    val crit = phase == 2
    // Vague C : sprite IA de phase si disponible (intact -> abîmé -> monstrueux)
    val phaseImg = when (phase) { 2 -> imgMonster ?: imgDamaged; 1 -> imgDamaged; else -> null }
    val useImg = phaseImg ?: img
    // Flash d'impact en STROBE léger (sinon, sous le laser continu, le boss restait
    // tout blanc en permanence) : semi-transparent + clignotement.
    val flash = (e.hitFlash > 0f && ((e.t * 24).toInt() % 3 == 0)) || (crit && Random.nextFloat() < 0.10f)
    val dw = u * dstU
    val dh = dw * useImg.height / useImg.width
    // halo qui chauffe avec la phase
    val aura = if (goldTint) GOLD else when (phase) { 0 -> CYAN; 1 -> ORANGE; else -> PHASE_RED }
    drawCircle(aura.copy(alpha = 0.10f + phase * 0.05f), radius = dw * 0.6f, center = Offset(x, y))
    // teinte : intact → chauffe (seulement si PAS de sprite de phase dédié) ;
    // or pour le true last ; éclair BLANC DOUX à l'impact
    val filter = when {
        flash -> ColorFilter.tint(Color(0x9BFFFFFF), BlendMode.SrcAtop)
        goldTint -> ColorFilter.tint(Color(0xFFF2C64E), BlendMode.Modulate)
        phaseImg != null -> null   // le sprite de phase porte déjà les dégâts
        phase == 2 -> ColorFilter.tint(Color(0xFFFF8866), BlendMode.Modulate)
        phase == 1 -> ColorFilter.tint(Color(0xFFFFC8A8), BlendMode.Modulate)
        tint != null -> ColorFilter.tint(tint, BlendMode.Modulate)   // teinte de variante (mini-boss)
        else -> null
    }
    val body = {
        drawImage(
            image = useImg,
            srcOffset = IntOffset.Zero, srcSize = IntSize(useImg.width, useImg.height),
            dstOffset = IntOffset((x - dw / 2f).toInt(), (y - dh / 2f).toInt()),
            dstSize = IntSize(dw.toInt(), dh.toInt()),
            colorFilter = filter, filterQuality = FilterQuality.None
        )
    }
    if (rot180) rotate(180f, pivot = Offset(x, y)) { body() } else body()
    // Dégâts : plus de « carré noir » de brûlure plaqué sur le sprite (l'utilisateur le voyait comme un bug
    // d'affichage). En phase critique : seulement de petites braises qui scintillent + fumée douce.
    if (crit) {
        drawCircle(Color(0xFF444444).copy(alpha = 0.25f + 0.1f * sin(e.t * 6f)), radius = dw * 0.16f, center = Offset(x - dw * 0.12f, y - dh * 0.35f - (e.t * 9f % 1f) * dh * 0.2f))
        repeat(3) {
            val ex = x + (Random.nextFloat() - 0.5f) * dw * 0.7f
            val ey = y + (Random.nextFloat() - 0.5f) * dh * 0.6f
            drawRect(EMBER, topLeft = Offset(ex, ey), size = Size(dw * 0.025f, dw * 0.025f))
        }
    }
    // Barre de vie ancrée à l'ÉCRAN : on annule le décalage caméra (vague B).
    if (showBar) translate(camX, 0f) { drawBossBar(e, u) }
}

private fun DrawScope.drawMiniBoss(e: Enemy, u: Float, spr: ShSprites, camX: Float = 0f) {
    // Echelle LIVE (option Taille mid-boss 100..1000%) : lue chaque frame -> le mid-boss a l'ecran
    // se redimensionne des qu'on change le reglage.
    // L'echelle est deja bakee dans e.radius au spawn -> le rendu suit le rayon (sprite recadre
    // qui remplit le cadre => la creature visible = la taille attendue).
    val img = e.spriteImg
    if (img != null) {
        drawImageBoss(img, e, u, dstU = e.radius * 2.2f / u, rot180 = false,
            goldTint = false, showBar = true, tint = null, camX = camX)
    } else {
        drawImageBoss(spr.mini, e, u, dstU = e.radius * 2.2f / u, rot180 = false,
            goldTint = false, showBar = true, tint = miniBossTintOf(e.variant), camX = camX)
    }
}

private fun DrawScope.drawBoss(e: Enemy, u0: Float, spr: ShSprites, level: Int, showBar: Boolean = true, camX: Float = 0f) {
    // Un sprite par niveau, assorti à l'illustration d'inter-stage (nez déjà vers le bas).
    val idx = level.coerceIn(1, 5) - 1
    val p2 = spr.bossP2.getOrNull(idx)
    val p3 = spr.bossP3.getOrNull(idx)
    if (e.trueLast) {
        drawImageBoss(spr.b5, e, u0 * 1.2f, 40f, rot180 = false, goldTint = true, showBar = showBar,
            camX = camX, imgDamaged = spr.bossP2.getOrNull(4), imgMonster = spr.bossP3.getOrNull(4))
        return
    }
    val img = when (level) { 1 -> spr.b1; 2 -> spr.b2; 3 -> spr.b3; 4 -> spr.b4; else -> spr.b5 }
    drawImageBoss(img, e, u0, 36f, rot180 = false, goldTint = false, showBar = showBar,
        camX = camX, imgDamaged = p2, imgMonster = p3)
}

/** Rayon laser continu façon DoDonPachi : cœur blanc, corps cyan, halo, flare et impact. */
private fun DrawScope.drawLaserBeam(g: ShmupGame) {
    val px = g.px
    val u = g.u
    val noseY = g.py - u * 5f
    val topY = g.laserHitY                 // s'arrête sur l'ennemi touché (0 = haut de l'écran)
    // Faisceau LARGE, LISSE et légèrement ONDULANT (façon DoDonPachi) : glow translucide
    // (les balles ennemies restent visibles au travers) + cœur brillant.
    val half = g.laserHalf
    // Ondulation UNIQUEMENT au niveau max, SYMÉTRIQUE (le faisceau s'élargit/rétrécit au
    // même endroit, centre fixe) et légère (~25 % de l'amplitude précédente).
    val wave = g.power >= 8
    val wamp = 0.13f
    val freq = 0.010f
    val ph = g.laserT * 2.0f
    fun wf(yy: Float): Float = if (wave) (1f + wamp * sin(yy * freq + ph)) else 1f
    fun ribbon(y0: Float, y1: Float, baseHw: Float): androidx.compose.ui.graphics.Path {
        val p = androidx.compose.ui.graphics.Path()
        val n = 46
        for (i in 0..n) {
            val yy = y0 + (y1 - y0) * i / n
            if (i == 0) p.moveTo(px - baseHw * wf(yy), yy) else p.lineTo(px - baseHw * wf(yy), yy)
        }
        for (i in n downTo 0) {
            val yy = y0 + (y1 - y0) * i / n
            p.lineTo(px + baseHw * wf(yy), yy)
        }
        p.close(); return p
    }
    fun beam(y0: Float, y1: Float) {
        if (y1 <= y0) return
        drawPath(ribbon(y0, y1, half * 1.9f), CYAN.copy(alpha = 0.16f))    // halo externe translucide
        drawPath(ribbon(y0, y1, half * 1.15f), CYAN.copy(alpha = 0.42f))  // glow
        drawPath(ribbon(y0, y1, half * 0.68f), CYAN_HOT.copy(alpha = 0.9f)) // corps
        drawPath(ribbon(y0, y1, half * 0.30f), Color.White)               // cœur brillant
    }
    if (topY < noseY) beam(topY, noseY)                    // vers le HAUT
    if (g.power >= 8) beam(g.py + u * 4f, size.height)     // vers le BAS (niveau max)
    // gerbe d'impact douce
    if (g.laserHitY > 0f) {
        val pulse = 1f + 0.2f * sin(g.laserT * 18f)
        drawCircle(CYAN.copy(alpha = 0.45f), radius = half * 1.7f * pulse, center = Offset(px, topY))
        drawCircle(Color.White, radius = half * 0.85f * pulse, center = Offset(px, topY))
    }
    // muzzle flare au nez
    drawCircle(CYAN.copy(alpha = 0.4f), radius = half * 1.4f, center = Offset(px, noseY))
    drawCircle(Color.White, radius = half * 0.7f, center = Offset(px, noseY))
}

private fun DrawScope.drawBossBar(e: Enemy, u: Float) {
    val frac = (e.hp.toFloat() / e.maxHp).coerceIn(0f, 1f)
    val barW = size.width * 0.86f
    val left = (size.width - barW) / 2f
    val top = u * 4f
    drawRect(Color(0xCC120512), topLeft = Offset(left - 4, top - 4), size = Size(barW + 8, u * 2.6f + 8))
    drawRect(Color(0xFF40121F), topLeft = Offset(left, top), size = Size(barW, u * 2.6f))
    val c = if (frac > 0.5f) CRIM else if (frac > 0.25f) ORANGE else GOLD
    drawRect(c, topLeft = Offset(left, top), size = Size(barW * frac, u * 2.6f))
}

// ---------------------------------------------------------------------------
// Sprites pixel (grille de caractères)
// ---------------------------------------------------------------------------

private class Sprite(val rows: List<String>, val palette: Map<Char, Color>) {
    val w = rows.maxOf { it.length }
    val h = rows.size
}

private fun DrawScope.drawSprite(sp: Sprite, cx: Float, cy: Float, pixel: Float, glow: Color, flash: Boolean) {
    drawCircle(glow.copy(alpha = 0.16f), radius = max(sp.w, sp.h) * pixel * 0.6f, center = Offset(cx, cy))
    val ox = cx - sp.w * pixel / 2f
    val oy = cy - sp.h * pixel / 2f
    for (r in 0 until sp.h) {
        val row = sp.rows[r]
        for (c in 0 until sp.w) {
            val ch = if (c < row.length) row[c] else '.'
            if (ch == '.') continue
            val color = if (flash) Color.White else (sp.palette[ch] ?: continue)
            drawRect(color, topLeft = Offset(ox + c * pixel, oy + r * pixel), size = Size(pixel + 0.6f, pixel + 0.6f))
        }
    }
}

/** Vaisseau « héros » détaillé (affiché en grand sur l'écran de félicitations). */
private val SPR_HERO = Sprite(
    listOf(
        "       WW       ",
        "      KRRK      ",
        "      KRRK      ",
        "     KRLLRK     ",
        "     KRCCRK     ",
        "    KRRCCRRK    ",
        "    KRLCCLRK    ",
        "   KRRLCCLRRK   ",
        "   KRRLCCLRRK   ",
        "  KRRRLCCLRRRK  ",
        "  KRRRRCCRRRRK  ",
        " KRrRRRCCRRRrRK ",
        "KRrRRRRRRRRRRrRK",
        "KKrRRRRRRRRRRrKK",
        " KKrRRRRRRRRrKK ",
        "  KGRRrRRrRRGK  ",
        "   KGOO  OOGK   ",
        "    YO    OY    "
    ),
    mapOf(
        'K' to Color(0xFF240610), 'R' to CRIM, 'r' to Color(0xFF9E2238),
        'L' to Color(0xFFFF89A2), 'C' to CYAN, 'W' to Color.White,
        'O' to ORANGE, 'Y' to GOLD, 'G' to Color(0xFF3A3550)
    )
)

private val SPR_SHIP = Sprite(
    listOf(
        "....A....",
        "...AAA...",
        "...BCB...",
        "..BBBBB..",
        ".BBBCBBB.",
        "BBBBBBBBB",
        "BB.BBB.BB",
        "D..BBB..D",
        "....D...."
    ),
    mapOf('A' to CYAN_HOT, 'B' to CRIM, 'C' to CYAN, 'D' to ORANGE)
)

// SPREAD : ailes larges, vert → couvre l'écran
private val SPR_SHIP_SPREAD = Sprite(
    listOf(
        "....A....",
        "...ACA...",
        "..GGCGG..",
        ".GGGGGGG.",
        "GGGGGGGGG",
        "GG.GGG.GG",
        "G.G.G.G.G",
        "...G.G...",
        "..E...E.."
    ),
    mapOf('A' to CYAN_HOT, 'G' to GREEN, 'C' to Color.White, 'E' to ORANGE)
)

// LASER : fuselage étroit et pointu, cyan → tir concentré
private val SPR_SHIP_LASER = Sprite(
    listOf(
        "....A....",
        "....A....",
        "...ACA...",
        "...BCB...",
        "..BBCBB..",
        "..BB.BB..",
        ".B.BBB.B.",
        "...B.B...",
        "....E...."
    ),
    mapOf('A' to CYAN_HOT, 'B' to CYAN, 'C' to Color.White, 'E' to ORANGE)
)

private val SPR_DRONE = Sprite(
    listOf(
        "PP...PP",
        "PPP.PPP",
        ".PPPPP.",
        ".PWWWP.",
        "..PPP..",
        "..P.P..",
        ".P...P."
    ),
    mapOf('P' to PURPLE, 'W' to PINK)
)

private val SPR_INTER = Sprite(
    listOf(
        "...R...",
        "..RRR..",
        ".RRYRR.",
        "RRRRRRR",
        "RR.R.RR",
        ".R.R.R.",
        ".R...R."
    ),
    mapOf('R' to ORANGE, 'Y' to GOLD)
)

// --- Ennemis uniques par niveau (2 à 5). Le niveau 1 réutilise SPR_DRONE/SPR_INTER. ---

private val SPR_DRONE_2 = Sprite(listOf(
    ".G.G.G.", "GGGGGGG", ".GGWGG.", "GGWWWGG", ".GGWGG.", "G.GGG.G", ".G...G."
), mapOf('G' to E_GRN, 'W' to E_LIME))
private val SPR_INTER_2 = Sprite(listOf(
    "..W.W..", ".WGGGW.", "WGGGGGW", "WGGYGGW", ".WGGGW.", "..WGW..", "...W..."
), mapOf('G' to E_GRN, 'W' to E_LIME, 'Y' to GOLD))

private val SPR_DRONE_3 = Sprite(listOf(
    "...B...", "..BBB..", ".BBWBB.", "BBWWWBB", ".BBWBB.", "..BBB..", "...B..."
), mapOf('B' to E_ICE, 'W' to E_ICEW))
private val SPR_INTER_3 = Sprite(listOf(
    "...B...", "..BWB..", ".BWWWB.", "BBWBWBB", ".B.B.B.", ".B...B.", "B.....B"
), mapOf('B' to E_ICE, 'W' to E_ICEW))

private val SPR_DRONE_4 = Sprite(listOf(
    "R.RRR.R", ".RRRRR.", "RRWWWRR", "RWYYYWR", "RRWWWRR", ".RRRRR.", "R..R..R"
), mapOf('R' to E_LAVA, 'W' to E_LAVADK, 'Y' to E_LYEL))
private val SPR_INTER_4 = Sprite(listOf(
    "...Y...", "..YRY..", ".YRRRY.", "RRRYRRR", "R.RRR.R", ".R.R.R.", "R..R..R"
), mapOf('R' to E_LAVA, 'Y' to E_LYEL))

private val SPR_DRONE_5 = Sprite(listOf(
    ".V.V.V.", "VVVVVVV", "VVMWMVV", "VMWWWMV", "VVMWMVV", ".VVVVV.", "..V.V.."
), mapOf('V' to E_VIO, 'M' to E_MAG, 'W' to Color.White))
private val SPR_INTER_5 = Sprite(listOf(
    "M.....M", "MM...MM", "MVV.VVM", "MVVWVVM", "MVV.VVM", ".MVVVM.", "..M.M.."
), mapOf('V' to E_VIO, 'M' to E_MAG, 'W' to Color.White))

/** Thème visuel d'un niveau : fond, étoiles et ennemis propres. */
private class LevelTheme(
    val bg: List<Color>, val star: Color,
    val drone: Sprite, val inter: Sprite,
    val droneColor: Color, val interColor: Color
)

private val LEVEL_THEMES = listOf(
    // 1 — Crépuscule cramoisi
    LevelTheme(listOf(Color(0xFF1A0716), Color(0xFF2A0A22), Color(0xFF090512)), Color.White,
        SPR_DRONE, SPR_INTER, PURPLE, ORANGE),
    // 2 — Jungle toxique
    LevelTheme(listOf(Color(0xFF07160C), Color(0xFF0C2A16), Color(0xFF04120A)), E_LIME,
        SPR_DRONE_2, SPR_INTER_2, E_GRN, E_LIME),
    // 3 — Glacier
    LevelTheme(listOf(Color(0xFF07101E), Color(0xFF0C1E3A), Color(0xFF040A16)), E_ICEW,
        SPR_DRONE_3, SPR_INTER_3, E_ICE, E_ICE),
    // 4 — Magma
    LevelTheme(listOf(Color(0xFF1E0A06), Color(0xFF320E08), Color(0xFF120503)), Color(0xFFFFC28A),
        SPR_DRONE_4, SPR_INTER_4, E_LAVA, E_LAVA),
    // 5 — Vide violet
    LevelTheme(listOf(Color(0xFF120620), Color(0xFF1E0A34), Color(0xFF08040F)), Color(0xFFE6B8FF),
        SPR_DRONE_5, SPR_INTER_5, E_VIO, E_MAG),
    // 6 — Doré (caché) : recycle les ennemis, thème or/noir
    LevelTheme(listOf(Color(0xFF141006), Color(0xFF241A06), Color(0xFF0A0803)), Color(0xFFFFE9A0),
        SPR_DRONE_5, SPR_INTER_4, GOLD, E_LYEL)
)

private fun levelTheme(level: Int): LevelTheme = LEVEL_THEMES[(level - 1).coerceIn(0, LEVEL_THEMES.size - 1)]

// ---------------------------------------------------------------------------
// HUD & overlays (Compose)
// ---------------------------------------------------------------------------

@Composable
private fun ShmupHud(score: Int, lives: Int, mult: Int, power: Int, bombs: Int, coins: Int, level: Int, mode: ShMode, bossFrac: Float) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = if (bossFrac >= 0f) 34.dp else 8.dp, start = 14.dp, end = 14.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(if (level >= 6) "STAGE *" else "STAGE $level/$LAST_LEVEL",
                    color = CYAN, fontFamily = Arcade, fontSize = 9.sp)
                Text("%07d".format(score), color = Color.White, fontFamily = Arcade, fontSize = 20.sp,
                    modifier = Modifier.padding(top = 3.dp))
                Text("$ $coins", color = GOLD, fontFamily = Arcade, fontSize = 12.sp,
                    modifier = Modifier.padding(top = 3.dp))
            }
            Column(horizontalAlignment = Alignment.End) {
                if (mode.scored) {
                    Text("${mult}X", color = if (mult >= 4) GOLD else PINK, fontFamily = Arcade, fontSize = 22.sp)
                }
                Text(
                    text = if (mode.scored) "♥".repeat(lives.coerceIn(0, 5)) else "∞",
                    color = CRIM, fontSize = 18.sp, modifier = Modifier.padding(top = 4.dp)
                )
                // Barre de POWER segmentée (8 crans) — plus propre que « PWR IIII »
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    Text("PWR", color = GREEN, fontFamily = Arcade, fontSize = 8.sp)
                    Canvas(modifier = Modifier.padding(start = 5.dp).size(width = 74.dp, height = 9.dp)) {
                        val seg = 8; val gap = size.width * 0.035f
                        val sw = (size.width - gap * (seg - 1)) / seg
                        for (i in 0 until seg) {
                            val on = i < power.coerceIn(0, 8)
                            drawRect(if (on) GREEN else Color(0x33FFFFFF),
                                topLeft = Offset(i * (sw + gap), 0f), size = Size(sw, size.height))
                            if (on) drawRect(Color.White.copy(alpha = 0.5f),
                                topLeft = Offset(i * (sw + gap), 0f), size = Size(sw, size.height * 0.3f))
                        }
                    }
                }
            }
        }
    }
}

/**
 * Bombes en bas à gauche : UNE icône (carré à bords arrondis, sans compteur) par bombe disponible.
 * Toucher une icône déclenche une bombe. À zéro bombe, un emplacement grisé reste tapotable
 * (secours : sacrifice des vaisseaux-options au power 6).
 */
@Composable
private fun BombButton(bombs: Int, onBomb: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 10.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val n = bombs.coerceIn(0, 6)
            if (n == 0) {
                BombTile(active = false, onBomb = onBomb)
            } else {
                repeat(n) { BombTile(active = true, onBomb = onBomb) }
            }
        }
    }
}

/** Une case bombe : carré à bords arrondis, icône bombe pixel-art, sans texte. */
@Composable
private fun BombTile(active: Boolean, onBomb: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(shape)
            .background(if (active) GOLD.copy(alpha = 0.20f) else Color(0x16FFFFFF))
            .border(2.dp, if (active) GOLD else Color(0x44FFFFFF), shape)
            .clickableNoRipple { onBomb() },
        contentAlignment = Alignment.Center
    ) {
        // Sprite IA validé de la bombe (grisé/estompé si inactif).
        Image(
            painter = painterResource(R.drawable.cs_bomb),
            contentDescription = null,
            modifier = Modifier.size(34.dp),
            alpha = if (active) 1f else 0.4f,
            colorFilter = if (active) null else ColorFilter.tint(Color(0xFF8A8A8A), BlendMode.Modulate)
        )
    }
}

/** Bombe d'avion (aerial bomb) dessinée : ailettes en haut, corps + nez pointu, bande jaune. */
private fun DrawScope.drawAircraftBomb(active: Boolean) {
    val s = size.minDimension
    val body = if (active) Color(0xFF6E7A42) else Color(0xFF5A5A5A)
    val bodyD = if (active) Color(0xFF3E4622) else Color(0xFF3A3A3A)
    val fin = if (active) Color(0xFF8B995A) else Color(0xFF6A6A6A)
    // ailettes (haut)
    val finPath = Path().apply {
        moveTo(s * 0.22f, s * 0.05f); lineTo(s * 0.78f, s * 0.05f)
        lineTo(s * 0.63f, s * 0.32f); lineTo(s * 0.37f, s * 0.32f); close()
    }
    drawPath(finPath, fin)
    drawRect(bodyD, topLeft = Offset(s * 0.42f, s * 0.06f), size = Size(s * 0.035f, s * 0.26f))
    drawRect(bodyD, topLeft = Offset(s * 0.55f, s * 0.06f), size = Size(s * 0.035f, s * 0.26f))
    // corps + nez pointu
    val bodyPath = Path().apply {
        moveTo(s * 0.36f, s * 0.28f); lineTo(s * 0.64f, s * 0.28f)
        lineTo(s * 0.64f, s * 0.66f)
        quadraticBezierTo(s * 0.64f, s * 0.90f, s * 0.50f, s * 0.95f)
        quadraticBezierTo(s * 0.36f, s * 0.90f, s * 0.36f, s * 0.66f)
        close()
    }
    drawPath(bodyPath, body)
    drawRect(GOLD, topLeft = Offset(s * 0.36f, s * 0.42f), size = Size(s * 0.28f, s * 0.07f))     // bande jaune
    drawRect(Color.White.copy(alpha = 0.5f), topLeft = Offset(s * 0.40f, s * 0.30f), size = Size(s * 0.035f, s * 0.34f)) // reflet
}

/**
 * Séquence d'intro cinématique : mascotte (« un jeu GoodBrainRot ») → key visual
 * « Soleil rouge » en zoom lent → titre CRIMSON SKY qui claque avec flash + boom.
 * Toucher l'écran : passe à l'étape suivante / entre dans le menu.
 */
@Composable
private fun CinematicIntro(onDone: () -> Unit) {
    // Démarre au KEY VISUAL (l'écran « un jeu GoodBrainRot » est désormais montré à la FIN,
    // sur l'écran de remerciements, plutôt qu'au lancement).
    var step by remember { mutableIntStateOf(1) }   // 1 visuel, 2 titre
    var flash by remember { mutableFloatStateOf(0f) }

    // avancement automatique + impact sonore au titre
    LaunchedEffect(step) {
        when (step) {
            0 -> { kotlinx.coroutines.delay(2100); step = 1 }
            1 -> { kotlinx.coroutines.delay(2400); step = 2 }
            2 -> {
                Sfx.play("explosion_big", 1f, 0.85f)
                Haptics.heavy()
                flash = 1f
                while (flash > 0f) { kotlinx.coroutines.delay(16); flash -= 0.06f }
            }
        }
    }

    // zoom lent « Ken Burns » du key visual
    val inf = androidx.compose.animation.core.rememberInfiniteTransition(label = "kb")
    val zoom by inf.animateFloat(
        1.03f, 1.16f,
        androidx.compose.animation.core.InfiniteRepeatableSpec(
            androidx.compose.animation.core.tween(7000, easing = androidx.compose.animation.core.LinearEasing),
            androidx.compose.animation.core.RepeatMode.Reverse
        ), label = "z"
    )
    // slam du titre (gros -> taille normale avec rebond)
    val titleScale by animateFloatAsState(
        targetValue = if (step >= 2) 1f else 3.4f,
        animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.38f, stiffness = 380f),
        label = "ts"
    )
    // clignotement du « touche pour commencer »
    val blink by inf.animateFloat(
        0.25f, 1f,
        androidx.compose.animation.core.InfiniteRepeatableSpec(
            androidx.compose.animation.core.tween(600, easing = androidx.compose.animation.core.LinearEasing),
            androidx.compose.animation.core.RepeatMode.Reverse
        ), label = "bl"
    )
    // fondu d'apparition de la mascotte
    val mascotIn by animateFloatAsState(if (step == 0) 1f else 0f,
        androidx.compose.animation.core.tween(500), label = "mi")

    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black)
            .clickableNoRipple { if (step < 2) step = 2 else onDone() }
    ) {
        if (step >= 1) {
            Image(
                painter = painterResource(R.drawable.cs_intro),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = zoom; scaleY = zoom }
            )
            // vignettage léger pour asseoir le titre
            Box(modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color(0x66000000), Color.Transparent, Color(0x88000000)))))
        }
        if (step == 0 && mascotIn > 0.01f) {
            Column(
                modifier = Modifier.fillMaxSize().graphicsLayer { alpha = mascotIn },
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(
                    painter = painterResource(R.drawable.cs_mascot),
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth(0.55f).clip(RoundedCornerShape(24.dp))
                )
                Text("un jeu", color = Color(0xFF9E8FB0), fontFamily = PixelHud,
                    fontSize = 15.sp, modifier = Modifier.padding(top = 20.dp))
                Text("GOODBRAINROT", color = Color(0xFFF4AFC9), fontFamily = PixelHud,
                    fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, letterSpacing = 3.sp)
            }
        }
        if (step >= 2) {
            Column(
                modifier = Modifier.fillMaxSize().padding(bottom = 70.dp),
                verticalArrangement = Arrangement.Bottom,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.graphicsLayer { scaleX = titleScale; scaleY = titleScale }
                ) {
                    // logo pixel-art 3D : dégradé métal chaud
                    ChromeText("CRIMSON",
                        listOf(Color(0xFFFFE9B0), Color(0xFFFF5A7A), CRIM, Color(0xFF64040F)),
                        34.sp, 2.sp)
                    Spacer(Modifier.height(8.dp))
                    ChromeText("SKY",
                        listOf(Color.White, Color(0xFFFFE08A), GOLD, Color(0xFF8A5400)),
                        34.sp, 8.sp)
                }
                // Numéro de version en gris, discret, sous le titre
                Text("v${com.goodbrainrot.app.BuildConfig.VERSION_NAME}", color = Color(0xFF8A8494),
                    fontFamily = PixelHud, fontSize = 11.sp, modifier = Modifier.padding(top = 10.dp))
                Text("TOUCHE POUR COMMENCER", color = CYAN.copy(alpha = blink),
                    fontFamily = PixelHud, fontSize = 18.sp, modifier = Modifier.padding(top = 30.dp))
            }
        }
        if (flash > 0f) {
            Box(modifier = Modifier.fillMaxSize().background(Color.White.copy(alpha = flash.coerceIn(0f, 1f))))
        }
    }
}

/**
 * Titre pixel-art 90s en fausse 3D : police d'arcade (Press Start 2P), extrudée
 * vers le bas-droite (empilement de copies sombres) + face en dégradé métallique.
 */
@Composable
private fun ChromeText(text: String, grad: List<Color>, fs: androidx.compose.ui.unit.TextUnit,
                       ls: androidx.compose.ui.unit.TextUnit) {
    Box(contentAlignment = Alignment.Center) {
        // profondeur 3D : copies décalées, de la plus lointaine (foncée) à la plus proche
        val depth = 7
        for (k in depth downTo 1) {
            val d = k.toFloat()
            val shade = 0.28f + (depth - k) * 0.045f   // les couches proches un peu plus claires
            Text(text, color = Color(0f, 0f, 0f, 1f).copy(red = 0.16f * shade, green = 0.02f * shade, blue = 0.06f * shade),
                fontFamily = Arcade, fontSize = fs, letterSpacing = ls, maxLines = 1, softWrap = false,
                modifier = Modifier.offset(x = (d * 0.7f).dp, y = (d * 1.0f).dp))
        }
        // face métallique en dégradé
        Text(text, fontFamily = Arcade, fontSize = fs, letterSpacing = ls, maxLines = 1, softWrap = false,
            style = androidx.compose.ui.text.TextStyle(
                brush = Brush.verticalGradient(grad)))
    }
}

/** Fond de menu : le hangar illustré + voile sombre pour la lisibilité de l'UI. */
@Composable
private fun MenuBackdrop() {
    Image(
        painter = painterResource(R.drawable.cs_menu),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize()
    )
    Box(modifier = Modifier.fillMaxSize().background(
        Brush.verticalGradient(listOf(Color(0xB007060F), Color(0x7007060F), Color(0xC907060F)))))
}

/** Écran GAME OVER illustré (épave sous la pluie). */
@Composable
private fun GameOverOverlay(credits: Int, onContinue: () -> Unit, onQuit: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(R.drawable.cs_gameover),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(modifier = Modifier.fillMaxSize().background(Color(0x99000000)))
        Column(
            modifier = Modifier.fillMaxSize().padding(28.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ChromeText("GAME OVER",
                listOf(Color(0xFFFFC9C9), Color(0xFFFF5A5A), CRIM, Color(0xFF4A0308)),
                26.sp, 2.sp)
            Spacer(Modifier.height(28.dp))
            // crédits (façon borne d'arcade)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("CRÉDIT ", color = Color.White, fontFamily = PixelHud,
                    fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("$credits", color = if (credits > 0) GOLD else Color(0xFFFF5A5A),
                    fontFamily = PixelHud, fontWeight = FontWeight.Black, fontSize = 22.sp)
            }
            Spacer(Modifier.height(22.dp))
            if (credits > 0) {
                ModeSmallButton("CONTINUER  ▶", GREEN, onContinue)
                Spacer(Modifier.height(10.dp))
                ModeSmallButton("ABANDONNER", PINK, onQuit)
            } else {
                Text("PLUS DE CRÉDIT", color = Color(0xFFFF7A7A), fontFamily = PixelHud,
                    fontSize = 14.sp, modifier = Modifier.padding(bottom = 14.dp))
                ModeSmallButton("TERMINER", CYAN, onQuit)
            }
        }
    }
}

@Composable
private fun ShipSelect(startLevel: Int, onLevel: (Int) -> Unit, sprites: ShSprites, logo: ImageBitmap?,
                       onPick: (ShShip) -> Unit, onBack: () -> Unit) {
    var selected by remember { mutableStateOf<ShShip?>(null) }
    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF07060F))) {
        MenuBackdrop()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(30.dp))
            Text("CHOISIS TON VAISSEAU", color = CRIM, fontFamily = PixelHud,
                fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 8.dp))
            // Sélecteur de niveau de départ (compact, une seule rangée)
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 10.dp)
            ) {
                Text("NIV.", color = CYAN, fontFamily = PixelHud, fontSize = 12.sp,
                    modifier = Modifier.padding(end = 2.dp))
                for (lv in 1..LAST_LEVEL) {
                    val on = lv == startLevel
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (on) GOLD.copy(alpha = 0.28f) else Color(0x1AFFFFFF))
                            .border(2.dp, if (on) GOLD else Color(0x55FFFFFF), RoundedCornerShape(8.dp))
                            .clickableNoRipple { onLevel(lv) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("$lv", color = if (on) GOLD else Color(0xCCFFFFFF),
                            fontFamily = PixelHud, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
                // Niveau secret (le 6 caché : boss final véritable)
                val secOn = startLevel == 6
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (secOn) CRIM.copy(alpha = 0.34f) else Color(0x1AFFFFFF))
                        .border(2.dp, if (secOn) CRIM else Color(0x55FF6A8A), RoundedCornerShape(8.dp))
                        .clickableNoRipple { onLevel(6) },
                    contentAlignment = Alignment.Center
                ) {
                    Text("★", color = if (secOn) Color(0xFFFF6A8A) else Color(0x99FF6A8A), fontSize = 17.sp)
                }
            }
            // Vaisseaux : liste verticale compacte (affichage d'avant, tailles réduites -> tient sans scroll)
            ShShip.values().forEach { sh ->
                ShipButton(sh, selected == sh) { SoundEngine.playUi(); selected = sh }
            }
            val s = selected
            if (s != null) {
                Spacer(Modifier.height(10.dp))
                // Vraie démo du jeu avec le vaisseau choisi (vrais projectiles), remplit le bas de l'écran
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .border(2.dp, s.accent.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                ) {
                    AttractDemo(sprites, logo, forcedShip = s, forcedLevel = 1, initPower = 3,
                        muteAudio = true, shipYFrac = 0.66f)
                    Text("DÉMO", color = s.accent, fontFamily = PixelHud, fontSize = 12.sp,
                        modifier = Modifier.align(Alignment.TopStart).padding(8.dp))
                }
                Spacer(Modifier.height(12.dp))
                ModeSmallButton("SÉLECTIONNER  ▶", s.accent) { onPick(s) }
                Spacer(Modifier.height(16.dp))
            } else {
                Spacer(Modifier.weight(1f))
                Text("Touche un vaisseau pour voir son arme", color = Color(0x99FFFFFF),
                    fontFamily = PixelHud, fontSize = 13.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.weight(1f))
            }
        }
        ShBackButton(onBack = onBack)
    }
}

/** Aperçu « déploiement » : le vaisseau monte et tire, du power-up de base au maximum. */
@Composable
private fun ShipDeployPreview(ship: ShShip) {
    val inf = androidx.compose.animation.core.rememberInfiniteTransition(label = "dep")
    val t by inf.animateFloat(0f, 1f, androidx.compose.animation.core.infiniteRepeatable(
        androidx.compose.animation.core.tween(5200, easing = androidx.compose.animation.core.LinearEasing),
        androidx.compose.animation.core.RepeatMode.Restart), label = "t")
    val stream by inf.animateFloat(0f, 1f, androidx.compose.animation.core.infiniteRepeatable(
        androidx.compose.animation.core.tween(650, easing = androidx.compose.animation.core.LinearEasing),
        androidx.compose.animation.core.RepeatMode.Restart), label = "s")
    val power = (1 + (t * 8).toInt()).coerceIn(1, 8)
    // BRAINROT : le logo est un vecteur (non chargeable en ImageBitmap) → proxy dessiné.
    val img = if (ship == ShShip.BRAINROT) null else ImageBitmap.imageResource(playerDrawableRes(ship))
    Box(
        modifier = Modifier
            .fillMaxSize()
            .heightIn(min = 150.dp)
            .clip(RoundedCornerShape(10.dp))
            .border(2.dp, ship.accent.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
            .background(Color(0xCC04070E))
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val u = size.minDimension / 20f
            // le vaisseau « déploie » : monte depuis le bas puis flotte
            val rise = (t * 3f).coerceIn(0f, 1f)
            val shipY = size.height * (1.05f - 0.32f * rise) + sin(t * 12f) * size.height * 0.012f
            drawPreviewWeapon(ship, power, cx, shipY, u, stream, size.height)
            val s = size.minDimension * 0.30f
            if (img != null) drawSpriteImg(img, cx, shipY, s, false, null, false)
            else {
                // proxy « cerveau » rose pour BRAINROT
                drawCircle(PINK, radius = s * 0.42f, center = Offset(cx, shipY))
                drawCircle(PINK.copy(alpha = 0.6f), radius = s * 0.30f, center = Offset(cx - s * 0.16f, shipY - s * 0.08f))
                drawCircle(Color.White.copy(alpha = 0.5f), radius = s * 0.12f, center = Offset(cx - s * 0.14f, shipY - s * 0.12f))
            }
        }
        Text("PWR " + "I".repeat(power), color = ship.accent, fontFamily = PixelHud, fontSize = 15.sp,
            modifier = Modifier.align(Alignment.TopStart).padding(10.dp))
        Text(ship.sub, color = Color(0xCCFFFFFF), fontFamily = PixelHud, fontSize = 13.sp,
            modifier = Modifier.align(Alignment.TopEnd).padding(10.dp))
    }
}

/** Dessine un tir représentatif du vaisseau au niveau [power] (pour l'aperçu). */
private fun DrawScope.drawPreviewWeapon(ship: ShShip, power: Int, cx: Float, shipY: Float, u: Float, stream: Float, hgt: Float) {
    val top = shipY - u * 4f
    fun shot(x: Float, y0: Float, col: Color, r: Float) {
        // colonne de projectiles qui défile vers le haut
        var y = y0 - stream * u * 6f
        while (y > 0f) { drawCircle(col, radius = r, center = Offset(x, y)); y -= u * 6f }
    }
    when (ship) {
        ShShip.VULCAN -> {
            val n = when { power <= 1 -> 1; power == 2 -> 2; power <= 4 -> 3; else -> 5 }
            for (k in 0 until n) shot(cx + (k - (n - 1) / 2f) * u * 2.6f, top, CYAN, u * 1.2f)
            if (power >= 8) { shot(cx - u * 2.4f, shipY + u * 5f, CYAN, u * 1.1f); shot(cx + u * 2.4f, shipY + u * 5f, CYAN, u * 1.1f) }
        }
        ShShip.SPREAD -> {
            val n = if (power >= 8) 13 else 3 + (min(power, 6) - 1) * 2
            val spanDeg = if (power >= 8) 180f else 30f + power * 8f
            for (k in 0 until n) {
                val frac = if (n == 1) 0.5f else k / (n - 1f)
                val ang = Math.toRadians(((frac - 0.5f) * spanDeg).toDouble()).toFloat()
                val dist = u * 6f + stream * u * 5f
                for (m in 0..3) {
                    val d = dist + m * u * 6f
                    drawCircle(GREEN, radius = u * 1.0f, center = Offset(cx + sin(ang) * d, top - cos(ang) * d))
                }
            }
        }
        ShShip.LASER -> {
            val half = if (power >= 8) (hgt * 0.11f + hgt * 0.03f * sin(stream * 20f)) else u * (1.4f + power * 0.4f)
            drawRect(CYAN.copy(alpha = 0.22f), topLeft = Offset(cx - half * 2.2f, 0f), size = Size(half * 4.4f, top))
            drawRect(CYAN.copy(alpha = 0.75f), topLeft = Offset(cx - half, 0f), size = Size(half * 2f, top))
            drawRect(Color.White, topLeft = Offset(cx - half * 0.4f, 0f), size = Size(half * 0.8f, top))
            if (power >= 8) drawRect(CYAN.copy(alpha = 0.6f), topLeft = Offset(cx - half * 0.6f, shipY + u * 4f), size = Size(half * 1.2f, hgt - (shipY + u * 4f)))
        }
        ShShip.BEE -> {
            val n = if (power >= 8) 3 else if (power >= 5) 2 else 1
            for (k in 0 until n) {
                val x = cx + (if (n == 1) 0f else (k - (n - 1) / 2f) * u * 2.8f)
                var y = top - stream * u * 8f
                while (y > 0f) {
                    drawRect(GOLD, topLeft = Offset(x - u * 0.5f, y - u * 2.5f), size = Size(u, u * 3.2f))
                    drawRect(Color.White, topLeft = Offset(x - u * 0.2f, y - u * 2.5f), size = Size(u * 0.4f, u * 3.2f))
                    y -= u * 8f
                }
            }
        }
        ShShip.BRAINROT -> {
            val balls = if (power >= 8) 2 else if (power >= 6) 2 else 1
            val r = if (power >= 8) u * 4.2f else if (power >= 3) u * 2.6f else u * 1.6f
            for (k in 0 until balls) {
                val x = cx + (if (balls == 1) 0f else (k - 0.5f) * u * 3f)
                var y = top - stream * u * 8f
                while (y > 0f) { drawCircle(PINK.copy(alpha = 0.85f), radius = r, center = Offset(x, y)); drawCircle(Color.White.copy(alpha = 0.5f), radius = r * 0.4f, center = Offset(x, y)); y -= u * 12f }
            }
        }
    }
    // muzzle flash (discret)
    drawCircle(Color.White.copy(alpha = 0.35f), radius = u * 0.9f, center = Offset(cx, top))
}

/** Tuile de vaisseau compacte (rangée horizontale sans scroll) : icône + nom court. */
@Composable
private fun ShipTile(ship: ShShip, selected: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(if (selected) 3.dp else 2.dp, ship.accent, RoundedCornerShape(8.dp))
            .background(ship.accent.copy(alpha = if (selected) 0.26f else 0.10f))
            .clickableNoRipple(onClick)
            .padding(vertical = 8.dp, horizontal = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF0B1A12))
        ) {
            if (ship == ShShip.BRAINROT) {
                Image(
                    painter = painterResource(R.drawable.logo_brain),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(6.dp)
                )
            } else {
                val img = ImageBitmap.imageResource(playerDrawableRes(ship))
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawSpriteImg(img, size.width / 2f, size.height / 2f, size.minDimension * 0.82f, false, null, false)
                }
            }
        }
        Text(ship.label, color = ship.accent, fontFamily = PixelHud, fontWeight = FontWeight.ExtraBold,
            fontSize = 11.sp, maxLines = 1, textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp))
    }
}

/** Option de vaisseau (liste verticale) : icône + nom + description, version COMPACTE (sans scroll). */
@Composable
private fun ShipButton(ship: ShShip, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .padding(vertical = 4.dp)
            .fillMaxWidth()
            .border(if (selected) 3.dp else 2.dp, ship.accent, RoundedCornerShape(8.dp))
            .background(ship.accent.copy(alpha = if (selected) 0.24f else 0.10f))
            .clickableNoRipple(onClick)
            .padding(vertical = 8.dp, horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(Color(0xFF0B1A12))
        ) {
            if (ship == ShShip.BRAINROT) {
                Image(
                    painter = painterResource(R.drawable.logo_brain),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(7.dp)
                )
            } else {
                val img = ImageBitmap.imageResource(playerDrawableRes(ship))
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawSpriteImg(img, size.width / 2f, size.height / 2f, size.minDimension * 0.82f, false, null, false)
                }
            }
        }
        Column(modifier = Modifier.padding(start = 14.dp)) {
            Text(ship.label, color = ship.accent, fontFamily = PixelHud,
                fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, maxLines = 1)
            Text(ship.sub, color = ship.accent.copy(alpha = 0.72f), fontFamily = PixelHud,
                fontSize = 11.sp, maxLines = 1)
        }
    }
}

@Composable
private fun ModeSelect(onPick: (ShMode) -> Unit, onBack: () -> Unit, onOptions: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF07060F))) {
        MenuBackdrop()
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ChromeText("CRIMSON SKY",
                listOf(Color(0xFFFFE9B0), Color(0xFFFF5A7A), CRIM, Color(0xFF64040F)),
                22.sp, 1.sp)
            Text("5 NIVEAUX + 1 SECRET", color = CYAN, fontFamily = PixelHud, fontSize = 16.sp,
                modifier = Modifier.padding(top = 12.dp, bottom = 30.dp))
            ModeButton("FACILE", "vies infinies", GREEN) { onPick(ShMode.EASY) }
            ModeButton("NORMAL", "le vrai jeu", CYAN) { onPick(ShMode.NORMAL) }
            ModeButton("DIFFICILE", "danmaku intense", PINK) { onPick(ShMode.HARD) }
            ModeButton("SCORE ATTACK", "2 minutes chrono", GOLD) { onPick(ShMode.SCORE) }
            Text("HI-SCORE  ${"%07d".format(HighScores.best(SHMUP_KEY))}",
                color = GOLD, fontFamily = PixelHud, fontSize = 16.sp,
                modifier = Modifier.padding(top = 22.dp, bottom = 14.dp))
            ModeSmallButton("OPTIONS", CYAN, onOptions)
        }
        ShBackButton(onBack = onBack)
    }
}

@Composable
private fun ModeButton(title: String, sub: String, color: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .padding(vertical = 7.dp)
            .fillMaxWidth()
            .border(2.dp, color, RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.10f))
            .clickableNoRipple(onClick)
            .padding(vertical = 14.dp, horizontal = 22.dp)
    ) {
        Column {
            Text(title, color = color, fontFamily = PixelHud,
                fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
            Text(sub, color = color.copy(alpha = 0.65f), fontFamily = PixelHud, fontSize = 12.sp)
        }
    }
}

@Composable
private fun IntroOverlay(level: Int) {
    val hidden = level >= 6
    // Illustration cinématique du stage (générée) + voile pour le texte
    val art = when (level) {
        1 -> R.drawable.cs_stage1; 2 -> R.drawable.cs_stage2; 3 -> R.drawable.cs_stage3
        4 -> R.drawable.cs_stage4; 5 -> R.drawable.cs_stage5; else -> R.drawable.cs_stage6
    }
    // Illustrations désormais au format portrait du téléphone → plein écran (Crop).
    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF07060F)), contentAlignment = Alignment.Center) {
        Image(
            painter = painterResource(art),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(modifier = Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color(0x99000000), Color(0x22000000), Color(0x99000000)))))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (hidden) "STAGE *" else "STAGE $level", color = Color.White, fontFamily = Arcade,
                fontSize = 20.sp,
                style = androidx.compose.ui.text.TextStyle(
                    shadow = androidx.compose.ui.graphics.Shadow(Color.Black, blurRadius = 12f)))
            Spacer(Modifier.height(12.dp))
            ChromeText(if (hidden) "TRUE LAST BOSS" else "CRIMSON SKY",
                if (hidden) listOf(Color.White, GOLD, Color(0xFF8A5400))
                else listOf(Color(0xFFFFE9B0), Color(0xFFFF5A7A), CRIM, Color(0xFF64040F)),
                if (hidden) 15.sp else 22.sp, 1.sp)
            if (hidden) {
                Text("NO DAMAGE... SO FAR", color = CYAN, fontFamily = PixelHud,
                    fontSize = 18.sp, modifier = Modifier.padding(top = 20.dp),
                    style = androidx.compose.ui.text.TextStyle(
                        shadow = androidx.compose.ui.graphics.Shadow(Color.Black, blurRadius = 10f)))
            } else {
                // GET READY : sprite IA validé.
                Image(painter = painterResource(R.drawable.cs_getready), contentDescription = null,
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxWidth(0.7f).padding(top = 20.dp))
            }
        }
    }
}

/** Bannière clignotante « WARNING » à l'arrivée d'un boss. */
@Composable
private fun BossWarningBanner(isBoss: Boolean) {
    // Juste le pixel-art (DANGER vrai boss / WARNING mini-boss) qui clignote — sans cadre rouge.
    val inf = androidx.compose.animation.core.rememberInfiniteTransition(label = "warn")
    val blink by inf.animateFloat(
        0.35f, 1f,
        androidx.compose.animation.core.InfiniteRepeatableSpec(
            androidx.compose.animation.core.tween(300, easing = androidx.compose.animation.core.LinearEasing),
            androidx.compose.animation.core.RepeatMode.Reverse
        ), label = "b"
    )
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Image(
            painter = painterResource(if (isBoss) R.drawable.cs_danger else R.drawable.cs_warning),
            contentDescription = null,
            contentScale = ContentScale.FillWidth,
            modifier = Modifier.fillMaxWidth()
                .graphicsLayer { alpha = 0.5f + 0.5f * blink }
        )
    }
}

/** Petite icône de trésor (pièce/gemme/lingot) qui tourne, pour le récap. */
@Composable
private fun SpinningTreasure(value: Int) {
    val inf = androidx.compose.animation.core.rememberInfiniteTransition(label = "tr")
    val t by inf.animateFloat(
        0f, 6.2832f,
        androidx.compose.animation.core.infiniteRepeatable(
            androidx.compose.animation.core.tween(1400, easing = androidx.compose.animation.core.LinearEasing),
            androidx.compose.animation.core.RepeatMode.Restart
        ), label = "t"
    )
    Canvas(modifier = Modifier.size(22.dp)) {
        drawCoin(size.width / 2f, size.height / 2f, size.minDimension / 7.5f, t, value)
    }
}

/** Récap de fin de stage : trésors ramassés par type + temps + bonus « aucun dégât ». */
@Composable
private fun StageResult(
    level: Int, coinsByValue: Map<Int, Int>, timeSec: Float, hitless: Boolean,
    score: Int, scored: Boolean, onContinue: () -> Unit
) {
    val order = listOf(1 to "ARGENT", 5 to "OR", 10 to "RUBIS", 20 to "SAPHIR", 50 to "LINGOT", 100 to "DIAMANT")
    val mm = (timeSec / 60f).toInt(); val ss = (timeSec % 60f).toInt()
    val bonus = if (hitless && scored) 5000 else 0
    val rows = order.mapNotNull { (v, name) -> val c = coinsByValue[v] ?: 0; if (c > 0) Triple(v, name, c) else null }
    // compteurs animés (montent de 0, une ligne après l'autre)
    val shown = remember { androidx.compose.runtime.mutableStateListOf<Int>().apply { repeat(rows.size) { add(0) } } }
    var showExtra by remember { mutableStateOf(false) }
    var skip by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        loop@ for (i in rows.indices) {
            if (skip) break@loop
            val target = rows[i].third
            val steps = target.coerceIn(1, 14)
            for (s in 1..steps) {
                if (skip) break@loop
                shown[i] = target * s / steps
                SoundEngine.playTick()          // un « tic » à chaque incrément du compteur
                kotlinx.coroutines.delay(60)
            }
            shown[i] = target
            SoundEngine.playCoin(coinTier(rows[i].first))
            kotlinx.coroutines.delay(200)
        }
        for (i in rows.indices) shown[i] = rows[i].third   // tout révélé
        showExtra = true
    }
    val totalShown = rows.indices.sumOf { rows[it].first * shown[it] }
    val winRes = when (level.coerceIn(1, 6)) {
        1 -> R.drawable.cs_win1; 2 -> R.drawable.cs_win2; 3 -> R.drawable.cs_win3
        4 -> R.drawable.cs_win4; 5 -> R.drawable.cs_win5; else -> R.drawable.cs_win6
    }
    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xFF07060F))
            .clickableNoRipple { if (!showExtra) skip = true else onContinue() },
        contentAlignment = Alignment.Center
    ) {
        // fond « victoire » illustré (par niveau) + voile sombre pour la lisibilité du récap
        Image(
            painter = androidx.compose.ui.res.painterResource(winRes),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        // ~80% de luminosité : voile LÉGER (le décor de victoire reste bien visible)
        Box(modifier = Modifier.fillMaxSize().background(Color(0x33070610)))
        // Panneau encadré « waouh » : titre chromé, cadre doré, fond sombre translucide pour la lisibilité
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .width(360.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(Color(0xB00B0A18))   // plus transparent : on voit l'illustration derrière
                .border(3.dp, GOLD.copy(alpha = 0.65f), RoundedCornerShape(22.dp))
                .padding(horizontal = 22.dp, vertical = 24.dp)
        ) {
            Text("STAGE $level", color = CYAN, fontFamily = Arcade, fontSize = 17.sp,
                modifier = Modifier.padding(bottom = 4.dp))
            ChromeText("CLEAR", listOf(Color.White, Color(0xFFFFE08A), GOLD, Color(0xFF8A5400)), 34.sp, 4.sp)
            Spacer(Modifier.height(18.dp))
            rows.forEachIndexed { i, (value, name, _) ->
                Row(
                    modifier = Modifier.width(300.dp).padding(vertical = 3.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.width(150.dp)) {
                        SpinningTreasure(value)
                        Spacer(Modifier.width(8.dp))
                        Text(name, color = coinColor(value), fontFamily = PixelHud, fontSize = 16.sp)
                    }
                    Text("x${shown[i]}", color = Color.White, fontFamily = PixelHud, fontSize = 16.sp)
                    Text("$${value * shown[i]}", color = coinColor(value), fontFamily = PixelHud, fontSize = 16.sp)
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(modifier = Modifier.width(300.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("TRÉSORS", color = GOLD, fontFamily = PixelHud, fontSize = 16.sp)
                Text("$$totalShown", color = GOLD, fontFamily = PixelHud, fontSize = 16.sp)
            }
            // Section « extra » TOUJOURS présente (place réservée) mais invisible tant que le compteur
            // n'a pas fini -> pas de décalage du cadre entre le comptage et l'état final.
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.graphicsLayer { alpha = if (showExtra) 1f else 0f }
            ) {
                Row(modifier = Modifier.width(300.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("TEMPS", color = CYAN, fontFamily = PixelHud, fontSize = 16.sp)
                    Text("%d:%02d".format(mm, ss), color = CYAN, fontFamily = PixelHud, fontSize = 16.sp)
                }
                if (bonus > 0) {
                    Row(
                        modifier = Modifier.width(300.dp).padding(top = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("AUCUN DÉGÂT", color = GOLD, fontFamily = PixelHud, fontSize = 16.sp)
                        Text("+$bonus", color = GOLD, fontFamily = PixelHud, fontSize = 16.sp)
                    }
                }
                if (scored) {
                    Spacer(Modifier.height(14.dp))
                    Text("SCORE  ${"%07d".format(score + bonus)}", color = Color.White,
                        fontFamily = PixelHud, fontSize = 20.sp)
                }
                Spacer(Modifier.height(24.dp))
                Text("TAPOTER POUR CONTINUER", color = Color(0xAAFFFFFF), fontFamily = PixelHud, fontSize = 13.sp)
            }
        }
    }
}

/** Bouton retour : le même bouton « verre » que dans toute l'app (en haut à gauche). */
@Composable
private fun ShBackButton(onBack: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        BackButton(onBack = { SoundEngine.playUi(); onBack() }, modifier = Modifier.align(Alignment.TopStart))
    }
}

@Composable
private fun CenterBanner(text: String, color: Color) {
    // Sprite IA valide pour PERFECT et STAGE CLEAR (sinon texte).
    val sprite = when {
        text == "PERFECT" -> R.drawable.cs_perfect
        text.contains("CLEAR") -> R.drawable.cs_stgclr
        else -> 0
    }
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (sprite != 0) {
            Image(painter = painterResource(sprite), contentDescription = null,
                contentScale = ContentScale.FillWidth,
                modifier = Modifier.fillMaxWidth(0.9f))
        } else {
            Text(text, color = color, fontFamily = PixelHud,
                fontWeight = FontWeight.ExtraBold, fontSize = 44.sp, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun NameEntry(score: Int, onDone: (String) -> Unit) {
    val letters = remember { mutableStateOf(charArrayOf('A', 'A', 'A')) }
    Box(modifier = Modifier.fillMaxSize().background(Color(0xE607060F)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("NEW HIGH SCORE", color = GOLD, fontFamily = PixelHud,
                fontWeight = FontWeight.Bold, fontSize = 24.sp)
            Text("%07d".format(score), color = CYAN, fontFamily = PixelHud,
                fontWeight = FontWeight.Bold, fontSize = 36.sp, modifier = Modifier.padding(bottom = 16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                for (i in 0..2) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("▲", color = CYAN, fontSize = 28.sp, modifier = Modifier
                            .clickableNoRipple {
                                val c = letters.value.copyOf()
                                c[i] = if (c[i] == 'Z') 'A' else c[i] + 1; letters.value = c
                            }.padding(8.dp))
                        Text(letters.value[i].toString(), color = GOLD, fontFamily = PixelHud,
                            fontWeight = FontWeight.Bold, fontSize = 46.sp, modifier = Modifier.width(44.dp),
                            textAlign = TextAlign.Center)
                        Text("▼", color = CYAN, fontSize = 28.sp, modifier = Modifier
                            .clickableNoRipple {
                                val c = letters.value.copyOf()
                                c[i] = if (c[i] == 'A') 'Z' else c[i] - 1; letters.value = c
                            }.padding(8.dp))
                    }
                }
            }
            Box(
                modifier = Modifier
                    .padding(top = 24.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(CYAN)
                    .clickableNoRipple { onDone(String(letters.value)) }
                    .padding(horizontal = 44.dp, vertical = 12.dp)
            ) {
                Text("OK", color = Color(0xFF07060F), fontFamily = PixelHud,
                    fontWeight = FontWeight.ExtraBold, fontSize = 24.sp)
            }
        }
    }
}

@Composable
private fun ScoreBoard(score: Int, scored: Boolean, sprites: ShSprites, logo: ImageBitmap?,
                       gameKey: String = SHMUP_KEY, onReplay: () -> Unit, onBack: () -> Unit) {
    @Suppress("UNUSED_EXPRESSION") HighScores.version.value
    val scores = remember(HighScores.version.value, gameKey) { HighScores.top(gameKey) }
    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF07060F)), contentAlignment = Alignment.TopCenter) {
        // Démo « attract mode » qui se joue toute seule en fond, puis voile sombre pour la lisibilité.
        AttractDemo(sprites, logo)
        Box(modifier = Modifier.fillMaxSize().background(Color(0xC807060F)))
        Column(
            modifier = Modifier.fillMaxSize().padding(top = 54.dp, start = 24.dp, end = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("HI-SCORES", color = CRIM, fontFamily = PixelHud,
                fontWeight = FontWeight.ExtraBold, fontSize = 30.sp)
            if (scored) {
                Text("VOTRE SCORE  ${"%07d".format(score)}", color = CYAN,
                    fontFamily = PixelHud, fontSize = 16.sp, modifier = Modifier.padding(vertical = 10.dp))
            }
            scores.forEachIndexed { i, e ->
                Text("%2d  %-8s  %07d".format(i + 1, e.name, e.score),
                    color = if (i == 0) GOLD else Color.White, fontFamily = PixelHud,
                    fontWeight = if (i == 0) FontWeight.Bold else FontWeight.Normal, fontSize = 18.sp)
            }
            Row(modifier = Modifier.padding(top = 28.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                ModeSmallButton("REJOUER", GREEN, onReplay)
                ModeSmallButton("MENU", CYAN, onBack)
            }
        }
    }
}

/**
 * Démo « attract mode » : une partie se joue toute seule en fond (vaisseau invincible auto-piloté
 * qui esquive et tire, vagues qui déferlent). Son coupé le temps de la démo.
 */
@Composable
private fun AttractDemo(sprites: ShSprites, logo: ImageBitmap?,
                        forcedShip: ShShip? = null, forcedLevel: Int? = null,
                        initPower: Int = 4, muteAudio: Boolean = true, shipYFrac: Float = 0.80f) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    val demoLevel = remember(forcedLevel) { forcedLevel ?: (1..LAST_LEVEL).random() }
    val demoShip = remember(forcedShip) { forcedShip ?: ShShip.values().filter { it != ShShip.BRAINROT }.random() }
    val game = remember(size, demoShip, demoLevel) {
        if (size == IntSize.Zero) null
        else ShmupGame(size.width.toFloat(), size.height.toFloat(), ShMode.EASY, demoShip,
            level = demoLevel, initPower = initPower)
    }
    var tick by remember { mutableIntStateOf(0) }
    if (muteAudio) DisposableEffect(Unit) {        // coupe les SFX pendant la démo
        SoundEngine.muted = true
        onDispose { SoundEngine.muted = false }
    }
    LaunchedEffect(game) {
        if (game == null) return@LaunchedEffect
        var last = 0L; var t = 0f; var waveCd = 0.4f; var powCd = 5f
        while (true) {
            val now = withFrameNanos { it }
            if (last != 0L) {
                val dt = ((now - last) / 1_000_000_000f).coerceIn(0f, 0.05f)
                t += dt
                val w = game.w; val h = game.h
                // Invincible SANS clignotement : on garde invuln à 0 (le vaisseau reste DESSINÉ) et on
                // efface les balles trop proches (bouclier) pour que la démo ne meure jamais.
                game.invuln = 0f
                game.eBullets.removeAll { hypot(it.x - game.px, it.y - game.py) < game.u * 20f }
                // autopilote : balaie horizontalement + esquive la balle la plus proche
                var tx = w * 0.5f + sin(t * 0.9f) * w * 0.30f + sin(t * 2.3f) * w * 0.07f
                for (b in game.eBullets) {
                    val dx = game.px - b.x; val dy = b.y - game.py
                    if (dy > -h * 0.05f && dy < h * 0.20f && abs(dx) < w * 0.14f)
                        tx += if (dx >= 0f) w * 5f * dt else -w * 5f * dt
                }
                val ty = h * shipYFrac
                val nx = (game.px + (tx - game.px) * min(1f, dt * 6f)).coerceIn(w * 0.08f, w * 0.92f)
                game.movePlayer(nx - game.px, ty - game.py)
                // même boucle que le jeu : l'ambiance (particules, textes, étoiles, hit-stop) vieillit aussi,
                // sinon explosions et « +100 » s'empilent à l'infini dans la fenêtre de démo
                game.updateAmbient(dt)
                if (game.hitStop > 0f) game.hitStop = max(0f, game.hitStop - dt) else game.update(dt)
                if (game.gameOver) game.gameOver = false
                waveCd -= dt
                if (waveCd <= 0f && game.enemies.size < 22) {
                    waveCd = 1.5f + (0..6).random() * 0.1f
                    val k = if ((0..1).random() == 0) Kind.DRONE else Kind.INTERCEPTOR
                    game.spawnSwarm(k, 8 + (0..8).random())
                }
                powCd -= dt
                if (powCd <= 0f) { powCd = 7f; game.dropPowerUp() }
                tick++
            }
            last = now
        }
    }
    Box(Modifier.fillMaxSize().onSizeChanged { size = it }) {
        if (game != null) {
            Canvas(Modifier.fillMaxSize()) {
                @Suppress("UNUSED_EXPRESSION") tick
                drawGame(game, ShPhase.PLAYING, logo, sprites)
            }
        }
    }
}

@Composable
private fun ModeSmallButton(label: String, color: Color, onClick: () -> Unit) {
    // Bouton PIXEL ART : coins carrés, biseau (reflet haut/gauche + ombre bas/droite),
    // cadre sombre épais. Petit enfoncement au clic.
    val scope = rememberCoroutineScope()
    val press = remember { androidx.compose.animation.core.Animatable(0f) }
    Box(
        modifier = Modifier
            .background(Color(0xFF0A0912))                        // cadre sombre (pixel)
            .padding(3.dp)
            .background(color.darken(0.45f))                     // ombre bas/droite
            .padding(start = 0.dp, top = 0.dp, end = 3.dp, bottom = 3.dp)
            .background(color.lighten(0.30f))                    // reflet haut/gauche
            .padding(start = 3.dp, top = 3.dp, end = 0.dp, bottom = 0.dp)
            .background(color)                                   // face
            .clickableNoRipple {
                onClick()
                scope.launch { press.snapTo(1f); press.animateTo(0f,
                    androidx.compose.animation.core.tween(120)) }
            }
            .padding(horizontal = 22.dp, vertical = 12.dp)
            .offset(y = (press.value * 2f).dp)
    ) {
        Text(label, color = Color(0xFF07060F), fontFamily = PixelHud,
            fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
    }
}

/** Écran logo « un jeu GoodBrainRot » (après l'image de fin) : un clic pour passer aux scores. */
@Composable
private fun OutroLogoScreen(onDone: () -> Unit) {
    val inf = androidx.compose.animation.core.rememberInfiniteTransition(label = "outro")
    val blink by inf.animateFloat(0.3f, 1f, androidx.compose.animation.core.infiniteRepeatable(
        androidx.compose.animation.core.tween(700), androidx.compose.animation.core.RepeatMode.Reverse), label = "b")
    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xFF07060F)).clickableNoRipple(onDone),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(R.drawable.cs_mascot),
                contentDescription = null,
                modifier = Modifier.fillMaxWidth(0.5f).clip(RoundedCornerShape(24.dp))
            )
            Text("un jeu", color = Color(0xFF9E8FB0), fontFamily = PixelHud, fontSize = 15.sp,
                modifier = Modifier.padding(top = 18.dp))
            Text("GOODBRAINROT", color = Color(0xFFF4AFC9), fontFamily = PixelHud,
                fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, letterSpacing = 3.sp)
            Text("TOUCHE POUR CONTINUER", color = CYAN.copy(alpha = blink), fontFamily = PixelHud,
                fontSize = 14.sp, modifier = Modifier.padding(top = 44.dp))
        }
    }
}

/** Écran de félicitations de fin de niveau : mise en page soignée + vaisseau pixel-art détaillé. */
@Composable
private fun CongratsOverlay(score: Int, scored: Boolean, ship: ShShip, logo: ImageBitmap?, trueEnd: Boolean, onContinue: () -> Unit) {
    val heroImg = if (ship != ShShip.BRAINROT) ImageBitmap.imageResource(playerDrawableRes(ship)) else null
    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xFF07060F)),
        contentAlignment = Alignment.Center
    ) {
        // Illustration de victoire (lever de soleil doré) derrière la carte
        Image(
            painter = painterResource(R.drawable.cs_victory),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(modifier = Modifier.fillMaxSize().background(Color(0x7A07060F)))
        Column(
            modifier = Modifier
                .padding(28.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(Color(0x14FFFFFF))
                .border(2.dp, GOLD.copy(alpha = 0.6f), RoundedCornerShape(22.dp))
                .padding(vertical = 28.dp, horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(if (trueEnd) "✦ TRUE END ✦" else "★ FÉLICITATIONS ★", color = GOLD, fontFamily = PixelHud,
                fontWeight = FontWeight.ExtraBold, fontSize = if (trueEnd) 32.sp else 30.sp, textAlign = TextAlign.Center)
            Text(if (trueEnd) "TU AS VAINCU LE VRAI BOSS FINAL" else "TOUS LES NIVEAUX TERMINÉS",
                color = CYAN, fontFamily = PixelHud,
                fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp, bottom = 18.dp))

            // Vaisseau pixel-art détaillé (héros), sur un panneau sombre
            Box(
                modifier = Modifier
                    .size(220.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Brush.radialGradient(listOf(Color(0xFF241033), Color(0xFF0B0616))))
                    .border(1.dp, ship.accent.copy(alpha = 0.5f), RoundedCornerShape(18.dp))
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val pixel = size.minDimension / 22f
                    val cx = size.width / 2f
                    val cy = size.height * 0.46f
                    if (ship == ShShip.BRAINROT && logo != null) {
                        val s = pixel * 20f
                        drawImage(
                            image = logo,
                            srcOffset = IntOffset.Zero,
                            srcSize = IntSize(logo.width, logo.height),
                            dstOffset = IntOffset((cx - s / 2f).toInt(), (cy - s / 2f).toInt()),
                            dstSize = IntSize(s.toInt(), s.toInt())
                        )
                    } else if (heroImg != null) {
                        drawSpriteImg(heroImg, cx, cy, size.minDimension * 0.7f, false, null, false)
                    }
                }
            }

            if (scored) {
                Text("SCORE  ${"%07d".format(score)}", color = Color.White, fontFamily = PixelHud,
                    fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.padding(top = 18.dp))
            }
            Text("Merci d'avoir joué à Crimson Sky !", color = Color(0xFFB8A8D0),
                fontFamily = PixelHud, fontSize = 12.sp, textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp))

            Box(modifier = Modifier.padding(top = 22.dp)) {
                ModeSmallButton("CONTINUER", GREEN, onContinue)
            }
        }
    }
}
