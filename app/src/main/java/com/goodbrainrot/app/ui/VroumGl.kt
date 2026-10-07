package com.goodbrainrot.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

// ===========================================================================
// Vroum Vrouuuuuuum — rendu VRAI 3D (OpenGL ES 2.0) :
//  - route, sol et collines = géométrie 3D en mètres, caméra en perspective,
//    z-buffer réel (les sprites sont occultés par les collines),
//  - sprites 2D pixel-art = billboards texturés posés au sol (2 frames d'animation),
//  - brouillard de profondeur dans le shader, ciel synthwave + soleil + montagnes.
// Le modèle de jeu ([VroumGame]) est mis à jour sur le thread GL à chaque image.
// ===========================================================================

internal class VroumGlView(ctx: Context, game: VroumGame) : GLSurfaceView(ctx) {
    val renderer = VroumRenderer(ctx, game)
    init {
        setEGLContextClientVersion(2)
        setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY
    }
}

private class Tex(val id: Int, val uMax: Float, val vMax: Float, val aspect: Float /* h/w du sprite */)

internal class VroumRenderer(private val ctx: Context, private val game: VroumGame) : GLSurfaceView.Renderer {
    @Volatile var paused = false
    private var program = 0
    private var aPos = 0; private var aUV = 0; private var aCol = 0
    private var uMVP = 0; private var uTex = 0; private var uFog = 0; private var uFogColor = 0; private var uEye = 0
    private var white = 0
    private var disc: Tex? = null          // disque doux (ombres, particules, halos)
    private var ring: Tex? = null          // anneau doux (onde de choc d'atterrissage)
    private val tex = HashMap<String, Tex>()
    private val proj = FloatArray(16); private val view = FloatArray(16); private val mvp = FloatArray(16); private val ident = FloatArray(16)
    private var aspectRatio = 1f
    private var lastNs = 0L
    private var viewW = 1; private var viewH = 1

    private val MAX_VERTS = 20000
    private val fb: FloatBuffer = ByteBuffer.allocateDirect(MAX_VERTS * 9 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
    private var nVerts = 0
    private var fovExtra = 0f

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        program = buildProgram()
        aPos = GLES20.glGetAttribLocation(program, "aPos"); aUV = GLES20.glGetAttribLocation(program, "aUV"); aCol = GLES20.glGetAttribLocation(program, "aCol")
        uMVP = GLES20.glGetUniformLocation(program, "uMVP"); uTex = GLES20.glGetUniformLocation(program, "uTex")
        uFog = GLES20.glGetUniformLocation(program, "uFog"); uFogColor = GLES20.glGetUniformLocation(program, "uFogColor"); uEye = GLES20.glGetUniformLocation(program, "uEye")
        white = makeWhiteTexture()
        disc = Tex(makeSoftDisc(), 1f, 1f, 1f)
        ring = Tex(makeSoftRing(), 1f, 1f, 1f)
        tex.clear()
        val names = ArrayList<String>()
        for (v in VEHICLES) { names.add("vr_${v.key}"); names.add("vr_${v.key}_cannon") }
        for (e in ENEMY_KEYS) names.add("vr_$e")
        for (t in THEMES) { for (d in t.decor) names.add("vr_${d.first}"); names.addAll(t.picks); names.add(t.holeTex); names.add(t.logTex) }
        for (p in PASSENGER_KEYS) names.add("vv_$p")
        for (c in CHEER_KEYS) names.add("vv_$c")
        for (n in listOf("vv_fuel", "vv_star", "vv_turbo", "vv_puddle", "vv_flag", "vv_cannon", "vv_superboost", "vv_hole", "vv_log")) names.add(n)
        for (n in names.distinct()) loadTexture(n)?.let { tex[n] = it }
        Matrix.setIdentityM(ident, 0)
        GLES20.glEnable(GLES20.GL_BLEND)
        // Les bitmaps Android sont PRÉ-MULTIPLIÉS (GLUtils les envoie tels quels) : blending (1, 1-a).
        // Avec (a, 1-a) l'alpha était appliqué deux fois -> franges sombres et « cadre » gris autour
        // des sprites au loin (les mipmaps moyennent avec le noir transparent).
        GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glDisable(GLES20.GL_CULL_FACE)
        lastNs = 0L
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        viewW = width; viewH = height
        aspectRatio = width.toFloat() / max(1, height)
        // paysage (tablette) : champ vertical réduit -> la route occupe la largeur, le bolide reste gros
        game.wide = width > height
        Matrix.perspectiveM(proj, 0, if (game.wide) 40f else 64f, aspectRatio, 1.0f, 600f)   // (recalculée à chaque image avec le zoom de boost)
        // La caméra regarde vers +z : en repère main droite, +x pointe alors vers la GAUCHE de
        // l'écran. On miroir l'axe x de la projection pour que voie 0 = gauche, voie 2 = droite.
        Matrix.scaleM(proj, 0, -1f, 1f, 1f)
    }

    override fun onDrawFrame(gl: GL10?) {
        val now = System.nanoTime()
        val dt = if (lastNs == 0L) 0f else ((now - lastNs) / 1_000_000_000f).coerceIn(0f, 0.05f)
        lastNs = now
        if (!paused && dt > 0f) game.update(dt)

        val th = game.theme
        GLES20.glClearColor(th.sky[0].r, th.sky[0].g, th.sky[0].b, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        GLES20.glUseProgram(program)
        GLES20.glEnableVertexAttribArray(aPos); GLES20.glEnableVertexAttribArray(aUV); GLES20.glEnableVertexAttribArray(aCol)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0); GLES20.glUniform1i(uTex, 0)
        GLES20.glUniform3f(uFogColor, th.fog.r, th.fog.g, th.fog.b)
        // coup de zoom inverse pendant un boost : le champ s'élargit (sensation de vitesse), lissé
        val wantExtra = game.boostLevel * 7f
        fovExtra += (wantExtra - fovExtra) * min(1f, dt * 6f)
        Matrix.perspectiveM(proj, 0, (if (game.wide) 40f else 64f) + fovExtra, aspectRatio, 1.0f, 600f)
        Matrix.scaleM(proj, 0, -1f, 1f, 1f)

        // ---------- caméra : suit le sol (collines), regarde loin pour que le bolide reste bas ;
        //            pendant le décompte elle DESCEND en douceur depuis le ciel ----------
        // Caméra de poursuite HAUTE (≈ 8,5 m, inclinée ~12° vers le bas) : le bolide (18 m devant)
        // apparaît petit et bas à l'écran, la route et ce qui arrive occupent la vue.
        val s = game.dist
        val k = (game.introT / VroumGame.INTRO_DUR).coerceIn(0f, 1f)
        val ease = k * k
        val wide = game.wide
        val camZ = if (wide) -2f * ease else -8f * ease
        // portrait : caméra haute, regard plongeant -> le bolide est bas à l'écran (≈ 70 % de la hauteur)
        // paysage : caméra plus basse, plus proche, champ étroit -> la route remplit la largeur, le bolide reste gros
        val camY = game.groundY(s) + (if (wide) 5.2f + 5.0f * ease else 8.5f + 16.5f * ease)
        val camX = 0f
        val lookZ = if (wide) 46f else 60f
        val lookY = game.groundY(s + lookZ) - (if (wide) 2.0f + 3.0f * ease else 4.0f + 9.5f * ease)
        val lookX = game.xCenter(lookZ) * 0.6f
        val shk = game.shake * 0.004f
        val sx = if (shk > 0f) (Math.random().toFloat() - 0.5f) * shk else 0f
        val sy = if (shk > 0f) (Math.random().toFloat() - 0.5f) * shk else 0f
        Matrix.setLookAtM(view, 0, camX + sx, camY + sy, camZ, lookX, lookY, lookZ, 0f, 1f, 0f)
        Matrix.multiplyMM(mvp, 0, proj, 0, view, 0)
        GLES20.glUniform3f(uEye, camX, camY, camZ)

        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glUniformMatrix4fv(uMVP, 1, false, ident, 0)
        GLES20.glUniform1f(uFog, 0f)
        drawSky()

        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthFunc(GLES20.GL_LEQUAL)
        GLES20.glDepthMask(true)
        GLES20.glUniformMatrix4fv(uMVP, 1, false, mvp, 0)
        GLES20.glUniform1f(uFog, 1f)
        drawTerrain(s)

        GLES20.glDepthMask(false)
        drawSprites()
        GLES20.glDepthMask(true)

        projectTexts()
        game.frame++
    }

    // ------------------------------------------------------------------ ciel
    private fun drawSky() {
        val th = game.theme
        val hz = projectNdcY(game.xCenter(500f), game.groundY(game.dist + 500f), 500f)
        val cv = game.curve
        val t = game.elapsed
        begin()
        // dégradé en 4 bandes (couleurs du thème, du haut vers l'horizon)
        val s0 = th.sky[0]; val s1 = th.sky[1]; val s2 = th.sky[2]; val s3 = th.sky[3]
        val top = 1f; val h1 = hz + (1f - hz) * 0.70f; val h2 = hz + (1f - hz) * 0.38f; val h3 = hz + (1f - hz) * 0.14f
        quadNdc(-1f, 1f, h1, top, s1.r, s1.g, s1.b, s0.r, s0.g, s0.b)
        quadNdc(-1f, 1f, h2, h1, s2.r, s2.g, s2.b, s1.r, s1.g, s1.b)
        quadNdc(-1f, 1f, h3, h2, (s2.r + s3.r) / 2f, (s2.g + s3.g) / 2f, (s2.b + s3.b) / 2f, s2.r, s2.g, s2.b)
        quadNdc(-1f, 1f, hz, h3, s3.r, s3.g, s3.b, (s2.r + s3.r) / 2f, (s2.g + s3.g) / 2f, (s2.b + s3.b) / 2f)
        quadNdc(-1f, 1f, -1f, hz, th.grass.r, th.grass.g, th.grass.b, th.grass.r, th.grass.g, th.grass.b)
        // étoiles qui scintillent (thèmes nocturnes / synthwave)
        if (th.stars) for (i in 0 until (if (th.night) 110 else 70)) {
            val r1 = (((i + 1) * 7919) % 1000) / 1000f; val r2 = (((i + 3) * 104729) % 1000) / 1000f; val r3 = (((i + 5) * 1299709) % 1000) / 1000f
            val sx = -1f + 2f * r1 - cv * 0.03f; val sy = h2 + (1f - h2) * r2
            val tw = 0.55f + 0.45f * sin(t * (1.5f + r3 * 3f) + r3 * 20f)
            val sz = (0.003f + 0.004f * r3) * tw
            quadNdc(sx - sz / aspectRatio, sx + sz / aspectRatio, sy - sz, sy + sz, 1f, 0.95f, 0.85f, 1f, 0.95f, 0.85f)
        }
        // voiles de nuages fins (bandes translucides qui dérivent) : teinte = bande médiane éclaircie
        val cr = min(1f, s2.r + 0.3f); val cg = min(1f, s2.g + 0.3f); val cb = min(1f, s2.b + 0.3f)
        for (i in 0 until 5) {
            val yy = h2 + (h1 - h2) * (0.15f + 0.2f * i) ; val xx = ((t * 0.01f * (1 + i % 2) + i * 0.37f) % 2.4f) - 1.2f
            val hw = 0.35f + 0.1f * (i % 3); val hh = 0.012f + 0.006f * (i % 2)
            vertNdc(xx - hw, yy, cr, cg, cb); vertNdc(xx + hw, yy, cr, cg, cb); vertNdc(xx + hw * 0.7f, yy + hh, cr, cg, cb)
            vertNdc(xx - hw, yy, cr, cg, cb); vertNdc(xx + hw * 0.7f, yy + hh, cr, cg, cb); vertNdc(xx - hw * 0.6f, yy + hh, cr, cg, cb)
        }
        flush(white)
        // astre : soleil dégradé (rayé synthwave) ou lune/disque pâle
        val sunX = -cv * 0.25f; val sunR = th.sunR; val sunY = hz + sunR * (if (th.stripes) 0.30f else 1.1f)
        begin()
        val n = 40
        for (i in 0 until n) {
            val a0 = i * 2f * Math.PI.toFloat() / n; val a1 = (i + 1) * 2f * Math.PI.toFloat() / n
            val y0 = sunY + sin(a0) * sunR; val y1 = sunY + sin(a1) * sunR
            val k0 = ((y0 - (sunY - sunR)) / (2 * sunR)).coerceIn(0f, 1f); val k1 = ((y1 - (sunY - sunR)) / (2 * sunR)).coerceIn(0f, 1f)
            vertNdc(sunX, sunY, th.sun.r, th.sun.g, th.sun.b)
            if (th.stripes) {
                vertNdc(sunX + cos(a0) * sunR / aspectRatio, y0, th.sun.r, th.sun.g * (0.65f + 0.45f * k0), th.sun.b * (0.6f + 1f * k0))
                vertNdc(sunX + cos(a1) * sunR / aspectRatio, y1, th.sun.r, th.sun.g * (0.65f + 0.45f * k1), th.sun.b * (0.6f + 1f * k1))
            } else {
                vertNdc(sunX + cos(a0) * sunR / aspectRatio, y0, th.sun.r * 0.92f, th.sun.g * 0.92f, th.sun.b)
                vertNdc(sunX + cos(a1) * sunR / aspectRatio, y1, th.sun.r * 0.92f, th.sun.g * 0.92f, th.sun.b)
            }
        }
        flush(white)
        begin()
        if (th.stripes) {
            var yy = sunY - sunR * 0.05f; var band = sunR * 0.05f
            while (yy > sunY - sunR) { quadNdc(sunX - sunR / aspectRatio, sunX + sunR / aspectRatio, yy - band, yy, s2.r * 0.8f, s2.g * 0.8f, s2.b * 0.9f, s2.r * 0.8f, s2.g * 0.8f, s2.b * 0.9f); yy -= band * 2.3f; band *= 1.25f }
        }
        if (th.skyline) {
            // Tokyo : silhouettes de gratte-ciel avec fenêtres allumées (déterministes)
            var x = -1.1f; var i = 0
            while (x < 1.1f) {
                val w = 0.05f + (((i * 7919) % 100) / 100f) * 0.09f
                val hgt = 0.06f + (((i * 104729) % 100) / 100f) * 0.30f
                val bx = x - cv * 0.12f
                val (r, g, b) = if (i % 2 == 0) Triple(th.mount1.r, th.mount1.g, th.mount1.b) else Triple(th.mount2.r, th.mount2.g, th.mount2.b)
                quadNdc(bx, bx + w, hz - 0.02f, hz + hgt, r, g, b, r, g, b)
                // fenêtres
                var wy = hz + 0.02f; var row = 0
                while (wy < hz + hgt - 0.02f) {
                    var wx = bx + 0.008f; var col = 0
                    while (wx < bx + w - 0.012f) {
                        val on = (((i * 31 + row * 17 + col * 7) % 5) != 0)
                        if (on) { val neon = (i + col) % 3; val (nr, ng, nb) = when (neon) { 0 -> Triple(1f, 0.85f, 0.4f); 1 -> Triple(0.4f, 0.95f, 1f); else -> Triple(1f, 0.45f, 0.8f) }
                            quadNdc(wx, wx + 0.007f, wy, wy + 0.012f, nr, ng, nb, nr, ng, nb) }
                        wx += 0.016f; col++
                    }
                    wy += 0.024f; row++
                }
                x += w + 0.012f; i++
            }
        } else {
            for (layer in 0..1) {
                val amp = if (layer == 0) 0.30f else 0.19f
                val off = -cv * (if (layer == 0) 0.10f else 0.22f)
                val c = if (layer == 0) th.mount1 else th.mount2
                var x = -1.05f
                while (x < 1.05f) {
                    val x2 = x + 0.05f
                    val hh1 = hz + amp * (0.55f + 0.45f * sin((x + off) * 6.3f + layer) + 0.3f * sin((x + off) * 15f + 2f * layer))
                    val hh2 = hz + amp * (0.55f + 0.45f * sin((x2 + off) * 6.3f + layer) + 0.3f * sin((x2 + off) * 15f + 2f * layer))
                    vertNdc(x, hz - 0.02f, c.r, c.g, c.b); vertNdc(x2, hz - 0.02f, c.r, c.g, c.b); vertNdc(x2, hh2, c.r, c.g, c.b)
                    vertNdc(x, hz - 0.02f, c.r, c.g, c.b); vertNdc(x2, hh2, c.r, c.g, c.b); vertNdc(x, hh1, c.r, c.g, c.b)
                    x = x2
                }
            }
        }
        var x = -1.05f
        val hc = th.hills
        while (x < 1.05f) {
            val x2 = x + 0.03f
            val hh1 = hz + 0.035f * (0.6f + 0.4f * sin(x * 50f - cv * 4f)); val hh2 = hz + 0.035f * (0.6f + 0.4f * sin(x2 * 50f - cv * 4f))
            vertNdc(x, hz - 0.02f, hc.r, hc.g, hc.b); vertNdc(x2, hz - 0.02f, hc.r, hc.g, hc.b); vertNdc(x2, hh2, hc.r, hc.g, hc.b)
            vertNdc(x, hz - 0.02f, hc.r, hc.g, hc.b); vertNdc(x2, hh2, hc.r, hc.g, hc.b); vertNdc(x, hh1, hc.r, hc.g, hc.b)
            x = x2
        }
        flush(white)
    }

    // --------------------------------------------------------------- terrain
    private fun drawTerrain(s: Float) {
        val SEG = 5f
        val lane = VroumGame.LANE_M
        begin()
        val first = floor(s / SEG).toInt()
        for (i in first - 2..first + 64) {
            val zw0 = i * SEG; val zw1 = zw0 + SEG
            val z0 = zw0 - s; val z1 = zw1 - s
            if (z1 < -12f) continue
            // largeur de route par segment (élargissement progressif 3 -> 5 -> 7 voies en paysage)
            val half0 = game.roadHalfAt(zw0); val half1 = game.roadHalfAt(zw1); val half = half0
            val nl = game.lanesAt(zw1)
            val y0 = game.groundY(zw0); val y1 = game.groundY(zw1)
            val cx0 = game.xCenter(z0); val cx1 = game.xCenter(z1)
            val even = (i and 1) == 0
            val slope = ((y1 - y0) / SEG).coerceIn(-0.3f, 0.3f)
            val sh = 1f + slope * 0.8f
            val th = game.theme
            val gc = if (even) th.grass else th.grass2; val rc = if (even) th.road else th.road2
            val (gr, gg, gb) = Triple(gc.r * sh, gc.g * sh, gc.b * sh)
            val (rr, rg, rb) = Triple(rc.r * sh, rc.g * sh, rc.b * sh)
            quad3(cx0 - 90f, y0 + 1.2f, z0, cx0 - half0, y0, z0, cx1 - half1, y1, z1, cx1 - 90f, y1 + 1.2f, z1, gr, gg, gb)
            quad3(cx0 + half0, y0, z0, cx0 + 90f, y0 + 1.2f, z0, cx1 + 90f, y1 + 1.2f, z1, cx1 + half1, y1, z1, gr, gg, gb)
            quad3(cx0 - half0, y0, z0, cx0 + half0, y0, z0, cx1 + half1, y1, z1, cx1 - half1, y1, z1, rr, rg, rb)
            val (br, bg, bb) = if (even) Triple(0.96f, 0.94f, 0.90f) else Triple(th.edge.r, th.edge.g, th.edge.b)
            quad3(cx0 - half0, y0 + 0.03f, z0, cx0 - half0 + 0.5f, y0 + 0.03f, z0, cx1 - half1 + 0.5f, y1 + 0.03f, z1, cx1 - half1, y1 + 0.03f, z1, br, bg, bb)
            quad3(cx0 + half0 - 0.5f, y0 + 0.03f, z0, cx0 + half0, y0 + 0.03f, z0, cx1 + half1, y1 + 0.03f, z1, cx1 + half1 - 0.5f, y1 + 0.03f, z1, br, bg, bb)
            if (even && half1 - half0 < 0.01f) for (k in 1 until nl) {
                val off = (k - nl / 2f) * lane
                val lx0 = cx0 + off; val lx1 = cx1 + off
                quad3(lx0 - 0.09f, y0 + 0.035f, z0, lx0 + 0.09f, y0 + 0.035f, z0, lx1 + 0.09f, y1 + 0.035f, z1, lx1 - 0.09f, y1 + 0.035f, z1, 0.96f, 0.94f, 0.90f)
            }
            @Suppress("UNUSED_VARIABLE") val unusedHalf = half
            if (nVerts > MAX_VERTS - 200) { flush(white); begin() }
        }
        flush(white)
    }

    // --------------------------------------------------------------- sprites
    /** Sprite 3D : billboard vertical (par défaut) ou décalque À PLAT sur le sol (ombres, flaques, flèches, onde de choc). */
    private class Bill(val z: Float, val x: Float, val y: Float, val w: Float, val tex: Tex?, val alpha: Float, val r: Float, val g: Float, val b: Float,
                       val squash: Float = 1f, val stretch: Float = 1f, val flat: Boolean = false, val rot: Float = 0f,
                       // « rig » : morceau du sprite (fractions 0..1, x gauche→droite, y bas→haut), cisaillements horizontaux
                       // des bords haut/bas et décalages verticaux des bords gauche/droit — permet jambes, ailes, suspension
                       val fx0: Float = 0f, val fx1: Float = 1f, val fy0: Float = 0f, val fy1: Float = 1f,
                       val shTop: Float = 0f, val shBot: Float = 0f, val dyL: Float = 0f, val dyR: Float = 0f)
    private val bills = ArrayList<Bill>(400)

    private fun drawSprites() {
        bills.clear()
        val g = game
        val s = g.dist
        fun ground(z: Float) = g.groundY(s + z)
        for (d in g.decor) {
            if (d.z < 1f || d.z > VroumGame.Z_FAR + 20f) continue
            val far = ((VroumGame.Z_FAR + 20f - d.z) / 60f).coerceIn(0f, 1f)
            val x = g.xCenter(d.z) + d.lateral
            when {
                d.flag -> { val t = tex["vv_flag"]; bills.add(Bill(d.z, x, ground(d.z), 2.4f, t, 1f, 1f, if (t == null) 0.8f else 1f, if (t == null) 0.2f else 1f)) }
                d.cheer >= 0 -> {
                    val bounce = if (d.arms > 0f) abs(sin(d.arms * 14f)) * 0.5f else 0f
                    val t = tex["vv_" + CHEER_KEYS[d.cheer]]
                    bills.add(Bill(d.z, x, ground(d.z) + bounce, if (d.arms > 0f) 2.0f else 1.7f, t, 1f, if (t == null) 0.85f else 1f, if (t == null) 0.7f else 1f, if (t == null) 0.5f else 1f))
                }
                else -> { val dk = g.theme.decor[d.kind % g.theme.decor.size]; val t = tex["vr_" + dk.first]
                    // nuit : le décor non lumineux est assombri (les lampes, enseignes et lanternes restent vives)
                    val lit = dk.first.startsWith("lamp") || dk.first.startsWith("tk_") || dk.first == "owl_tree"
                    val sh = if (g.theme.night && !lit) 0.5f else 1f
                    bills.add(Bill(d.z, x, ground(d.z), dk.second * 3.2f, t, far, if (t == null) 0.19f else sh, if (t == null) 0.48f else sh, if (t == null) 0.21f else sh * 1.1f)) }
            }
        }
        for (p in g.picks) {
            if (p.z < 1f || p.z > VroumGame.Z_FAR) continue
            val x = g.xCenter(p.z) + g.laneX(p.lane)
            val gy = ground(p.z)
            when (p.type) {
                VItem.HOLE -> {
                    // nid-de-poule / lave / glace (texture du thème) : décalque au sol + anneau rouge, à SAUTER
                    val pulse = 0.5f + 0.5f * sin(g.elapsed * 6f + p.z)
                    bills.add(Bill(p.z + 0.01f, x, gy, 3.8f + 0.4f * pulse, ring, 0.4f + 0.3f * pulse, 1f, 0.2f, 0.15f, squash = 0.9f, flat = true))
                    bills.add(Bill(p.z, x, gy, 2.7f, tex[g.theme.holeTex] ?: tex["vv_hole"], 1f, 1f, 1f, 1f, squash = 0.9f, flat = true))
                }
                VItem.LOG -> {
                    // tronc / congère / chat (texture du thème) en travers de la voie : halo rouge + billboard, à SAUTER
                    val pulse = 0.5f + 0.5f * sin(g.elapsed * 6f + p.z)
                    bills.add(Bill(p.z + 0.02f, x, gy, 4.0f + 0.5f * pulse, disc, 0.3f + 0.2f * pulse, 1f, 0.15f, 0.1f, squash = 0.9f, flat = true))
                    bills.add(Bill(p.z, x, gy, 2.9f, disc, 0.4f, 0f, 0f, 0f, squash = 0.7f, flat = true))
                    val t = tex[g.theme.logTex] ?: tex["vv_log"]
                    bills.add(Bill(p.z - 0.01f, x, gy, 2.9f, t, 1f, if (t == null) 0.45f else 1f, if (t == null) 0.3f else 1f, if (t == null) 0.2f else 1f, squash = if (t == null) 0.35f else 1f))
                }
                VItem.PUDDLE -> {
                    // piège : anneau rouge qui pulse autour de la flaque
                    val pulse = 0.5f + 0.5f * sin(g.elapsed * 6f + p.z)
                    bills.add(Bill(p.z + 0.01f, x, gy, 3.6f + 0.4f * pulse, ring, 0.35f + 0.25f * pulse, 1f, 0.2f, 0.15f, squash = 0.9f, flat = true))
                    bills.add(Bill(p.z, x, gy, 2.6f, tex["vv_puddle"], 1f, 1f, 1f, 1f, squash = 0.9f, flat = true))
                }
                VItem.TURBO -> { val pulse = 0.85f + 0.15f * sin(p.t * 8f)
                    bills.add(Bill(p.z + 0.01f, x, gy, 3.4f * pulse, disc, 0.35f, 0.3f, 0.85f, 1f, squash = 1.2f, flat = true))
                    bills.add(Bill(p.z, x, gy, 2.4f * pulse, tex["vv_turbo"], 1f, 1f, 1f, 1f, squash = 1.2f, flat = true)) }
                VItem.PASSENGER -> { val hop = abs(sin(p.t * 6f)) * 0.35f
                    val t = tex["vv_" + PASSENGER_KEYS[p.variant % PASSENGER_KEYS.size]]
                    bills.add(Bill(p.z + 0.01f, x, gy, 2.6f + 0.3f * sin(p.t * 5f), disc, 0.4f, 0.4f, 1f, 0.5f, squash = 0.6f, flat = true))
                    bills.add(Bill(p.z, x, gy + hop, 1.6f, t, 1f, 1f, if (t == null) 0.36f else 1f, if (t == null) 0.64f else 1f)) }
                else -> {
                    val bob = 0.35f + 0.18f * sin(p.t * 5f)
                    val sz = if (p.type == VItem.STAR) 2.2f else if (p.type == VItem.CANNON || p.type == VItem.SUPERBOOST) 2.2f else 1.5f
                    val tn = when (p.type) { VItem.FUEL -> "vv_fuel"; VItem.STAR -> "vv_star"; VItem.ACORN -> g.theme.picks[0]; VItem.MUSHROOM -> g.theme.picks[1]
                                             VItem.CANNON -> "vv_cannon"; VItem.SUPERBOOST -> "vv_superboost"; else -> g.theme.picks[2] }
                    val t = tex[tn]
                    // BONUS : halo VERT/doré qui pulse au sol (à l'opposé du rouge des obstacles) ; super boost : arc-en-ciel + étincelles
                    val glow = 0.5f + 0.5f * sin(p.t * 5f)
                    val (gr, gg, gb) = when (p.type) { VItem.FUEL -> Triple(0.3f, 1f, 0.4f); VItem.STAR -> Triple(1f, 0.85f, 0.3f); VItem.CANNON -> Triple(0.3f, 0.85f, 1f)
                                                       VItem.SUPERBOOST -> g.rainbow(p.t * 1.5f); else -> Triple(0.45f, 1f, 0.45f) }
                    if (p.type == VItem.SUPERBOOST) for (k in 0 until 6) { val a = p.t * 3f + k * 1.05f; val (sr, sg, sb) = g.rainbow(p.t + k * 0.17f)
                        bills.add(Bill(p.z + 0.02f, x + cos(a) * 1.4f, gy + bob + 0.9f + sin(a * 1.3f) * 0.7f, 0.35f, disc, 0.9f, sr, sg, sb)) }
                    bills.add(Bill(p.z + 0.01f, x, gy, sz * (1.6f + 0.25f * glow), disc, 0.3f + 0.2f * glow, gr, gg, gb, squash = 0.6f, flat = true))
                    bills.add(Bill(p.z, x, gy, sz * 0.9f, disc, 0.3f, 0f, 0f, 0f, squash = 0.5f, flat = true))
                    if (p.type == VItem.STAR) bills.add(Bill(p.z + 0.01f, x, gy + bob - 0.3f, sz * 1.5f, null, 0.22f + 0.1f * sin(p.t * 10f), 1f, 0.82f, 0.3f))
                    if (p.type == VItem.CANNON) bills.add(Bill(p.z + 0.01f, x, gy + bob - 0.3f, sz * 1.4f, null, 0.22f + 0.1f * sin(p.t * 10f), 0.3f, 0.85f, 1f))
                    val (cr, cg, cb) = if (t != null) Triple(1f, 1f, 1f) else when (p.type) { VItem.FUEL -> Triple(1f, 0.25f, 0.25f); VItem.CANNON -> Triple(0.45f, 0.5f, 0.6f); else -> Triple(1f, 0.82f, 0.3f) }
                    bills.add(Bill(p.z - 0.01f, x, gy + bob, sz, t, 1f, cr, cg, cb))
                }
            }
        }
        for (e in g.enemies) {
            if (e.z < 1f || e.z > VroumGame.Z_FAR) continue
            val x = g.xCenter(e.z) + g.laneX(e.lane)
            val gy = ground(e.z)
            val t = tex["vr_" + ENEMY_KEYS[e.kind]]
            if (e.crushed >= 0f) {
                val k = (e.crushed / 0.55f).coerceIn(0f, 1f)
                bills.add(Bill(e.z, x, gy, 2.8f * (1f + 0.35f * k), t, 1f - k * 0.8f, 0.5f, 0.5f, 0.55f, squash = (1f - 0.8f * k).coerceAtLeast(0.15f)))
            } else {
                // OBSTACLE : halo ROUGE qui pulse au sol (lisible pour un enfant de 5 ans) + ombre
                val pulse = 0.5f + 0.5f * sin(g.elapsed * 6f + e.z)
                bills.add(Bill(e.z + 0.02f, x, gy, 4.2f + 0.5f * pulse, disc, 0.32f + 0.18f * pulse, 1f, 0.15f, 0.1f, squash = 0.9f, flat = true))
                bills.add(Bill(e.z, x, gy, 2.8f, disc, 0.45f, 0f, 0f, 0f, squash = 0.9f, flat = true))
                bills.add(Bill(e.z - 0.01f, x, gy, 2.8f, t, 1f, 1f, 1f, 1f))
            }
        }
        // tirs des canons
        for (sh in g.shots) {
            val x = g.xCenter(sh.z) + g.laneX(sh.lane)
            bills.add(Bill(sh.z, x, ground(sh.z) + 0.6f, 0.9f, null, 0.9f, 1f, 0.95f, 0.4f, squash = 0.5f))
            bills.add(Bill(sh.z + 0.01f, x, ground(sh.z) + 0.35f, 1.6f, null, 0.35f, 1f, 0.6f, 0.2f, squash = 0.8f))
        }
        // onde de choc d'atterrissage : deux ANNEAUX fins qui s'élargissent (décalque au sol) + voile de poussière
        if (g.blastT > 0f) {
            val k = 1f - g.blastT / 0.5f
            val x = g.xCenter(VroumGame.PLAYER_Z) + g.lx * VroumGame.LANE_M
            val gy = ground(VroumGame.PLAYER_Z)
            bills.add(Bill(VroumGame.PLAYER_Z, x, gy, 3f + 24f * k, ring, 0.8f * (1f - k), 1f, 1f, 1f, squash = 0.9f, flat = true))
            bills.add(Bill(VroumGame.PLAYER_Z + 0.02f, x, gy, 1.5f + 15f * (k * k), ring, 0.6f * (1f - k), 1f, 0.9f, 0.6f, squash = 0.9f, flat = true))
            bills.add(Bill(VroumGame.PLAYER_Z + 0.03f, x, gy, 4f + 10f * k, disc, 0.25f * (1f - k), 0.75f, 0.62f, 0.45f, squash = 0.9f, flat = true))
        }
        // fantôme du meilleur temps (Course) : le même bolide en spectre bleuté semi-transparent
        if (g.ghostD != null && !g.endless) {
            val gz = g.ghostDist() - s + VroumGame.PLAYER_Z
            if (gz > 1.5f && gz < VroumGame.Z_FAR) {
                val gx = g.xCenter(gz) + g.laneX(g.ghostLane().coerceIn(0, g.lanes - 1))
                val gkey = VEHICLES.getOrNull(g.ghostVeh)?.key
                val gt = if (gkey != null) tex["vr_$gkey"] else null
                val gy2 = ground(gz)
                bills.add(Bill(gz, gx, gy2, 2.15f, disc, 0.22f, 0.2f, 0.4f, 0.8f, squash = 0.9f, flat = true))
                bills.add(Bill(gz - 0.015f, gx, gy2 + 0.06f + sin(g.elapsed * 4f) * 0.05f, 2.15f, gt, 0.45f, 0.6f, 0.85f, 1f))
            }
        }
        // joueur
        run {
            val z = VroumGame.PLAYER_Z
            val cover = g.coveredLanes()
            val jf = g.jumpFrac
            // squash & stretch : écrasé au décollage / à l'atterrissage, étiré en l'air
            var sq = 1f; var wf = 1f
            if (g.jumping) { val jt = g.jumpT / g.JUMP_DUR; sq = if (jt < 0.15f) 0.85f + jt * 2f else 1f + 0.18f * jf; wf = 2f - sq }
            else if (g.landedT > 0f) { val lt = g.landedT / 0.25f; sq = 0.72f + 0.28f * (1f - lt); wf = 1.25f - 0.25f * (1f - lt) }
            val pw = 2.15f * (cover.last - cover.first + 1) * wf
            val x = g.xCenter(z) + g.lx * VroumGame.LANE_M
            val gy = ground(z)
            val lift = jf * 3.2f
            val vkey = VEHICLES[g.vehicle].key
            val walker = vkey in WALKER_KEYS                  // lion, t-rex, ours : foulée (jambes)
            val flyer = vkey == "dragon"                      // dragon : battement d'ailes
            val hover = vkey == "rocket"                      // fusée : flotte
            val moving = !g.jumping && g.mps > 1f
            val sf = g.speedFactor.coerceAtLeast(0.4f)
            // phase d'animation : avance avec la distance parcourue (les pas suivent la vitesse réelle)
            val ph = g.dist * (if (walker) 1.35f else if (flyer) 0.9f else 4f)
            val bob = when {
                hover -> 0.25f + sin(g.elapsed * 3f) * 0.10f
                !moving -> 0f
                walker -> abs(sin(ph)) * 0.14f
                flyer -> sin(ph * 0.5f) * 0.12f + 0.12f
                else -> abs(sin(ph)) * 0.025f + sin(g.elapsed * 47f) * 0.008f   // suspension + vibration moteur
            }
            // inclinaison : changement de file (lean) + tangage du galop
            val lean = -g.leanDeg * 0.9f + (if (walker && moving) sin(ph * 2f) * 2.2f else 0f)
            bills.add(Bill(z, x, gy, pw * (1f - 0.3f * jf), disc, 0.5f, 0f, 0f, 0f, squash = 0.9f, flat = true))
            val blink = g.invuln <= 0f || ((g.invuln * 14f).toInt() % 2 == 0)
            if (blink) {
                val key = VEHICLES[g.vehicle].key
                val cannonTex = if (g.cannonT > 0f) tex["vr_${key}_cannon"] else null
                val vt = cannonTex ?: tex["vr_$key"]
                val (cr, cg, cb) = if (vt == null) VEHICLE_RGB[g.vehicle % VEHICLE_RGB.size] else Triple(1f, 1f, 1f)
                // nuit : phares (nappe lumineuse au sol devant le bolide)
                if (g.theme.night) bills.add(Bill(z + 5.5f, x, gy, 5.2f, disc, 0.32f, 1f, 0.95f, 0.72f, squash = 1f, stretch = 2.4f, flat = true))
                if (g.giant > 0) {
                    val pulse = 0.25f + 0.15f * sin(g.elapsed * 12f)
                    bills.add(Bill(z + 0.02f, x, gy + lift - 0.3f, pw * 1.5f, null, pulse, 1f, 0.82f, 0.3f, squash = 1f))
                }
                val by = gy + lift + bob
                val stride = if (moving) sin(ph) else 0f
                when {
                    walker -> {
                        // jambes (bas du sprite) cisaillées en opposition de phase : la jambe gauche avance quand la droite recule
                        // (les deux pièces se chevauchent au centre pour ne jamais laisser de fente à l'entrejambe)
                        val legH = 0.34f
                        val amp = pw * 0.06f * stride
                        bills.add(Bill(z - 0.02f, x, by, pw, vt, 1f, cr, cg, cb, squash = sq, rot = lean, fx0 = 0f, fx1 = 0.6f, fy0 = 0f, fy1 = legH, shBot = amp))
                        bills.add(Bill(z - 0.02f, x, by, pw, vt, 1f, cr, cg, cb, squash = sq, rot = lean, fx0 = 0.4f, fx1 = 1f, fy0 = 0f, fy1 = legH, shBot = -amp))
                        // corps : léger tangage (le haut se penche dans le sens de la foulée)
                        bills.add(Bill(z - 0.02f, x, by, pw, vt, 1f, cr, cg, cb, squash = sq, rot = lean, fx0 = 0f, fx1 = 1f, fy0 = legH, fy1 = 1f, shTop = pw * 0.03f * sin(ph * 2f)))
                    }
                    flyer -> {
                        // ailes : les bords extérieurs montent/descendent (cisaillement vertical), le corps reste en place
                        val flap = sin(g.elapsed * 9f) * pw * 0.16f
                        bills.add(Bill(z - 0.02f, x, by, pw, vt, 1f, cr, cg, cb, squash = sq, rot = lean, fx0 = 0f, fx1 = 0.36f, dyL = flap))
                        bills.add(Bill(z - 0.02f, x, by, pw, vt, 1f, cr, cg, cb, squash = sq, rot = lean, fx0 = 0.64f, fx1 = 1f, dyR = flap))
                        bills.add(Bill(z - 0.02f, x, by, pw, vt, 1f, cr, cg, cb, squash = sq, rot = lean, fx0 = 0.36f, fx1 = 0.64f))
                    }
                    hover -> bills.add(Bill(z - 0.02f, x, by, pw, vt, 1f, cr, cg, cb, squash = sq, rot = lean + sin(g.elapsed * 2f) * 2f))
                    else -> {
                        // véhicules à roues : train de roulement (bas) qui encaisse les bosses en roulis, caisse qui suit en amorti
                        val wheelH = 0.30f
                        val roll = if (moving) sin(ph * 0.7f) * pw * 0.012f else 0f
                        bills.add(Bill(z - 0.02f, x, by - bob * 0.6f, pw, vt, 1f, cr, cg, cb, squash = sq, rot = lean, fx0 = 0f, fx1 = 1f, fy0 = 0f, fy1 = wheelH, dyL = roll, dyR = -roll))
                        bills.add(Bill(z - 0.02f, x, by, pw, vt, 1f, cr, cg, cb, squash = sq, rot = lean, fx0 = 0f, fx1 = 1f, fy0 = wheelH, fy1 = 1f, shTop = -g.leanDeg * pw * 0.004f))
                    }
                }
                if (g.cannonT > 0f) {
                    // sprite du bolide AVEC canons si dispo (sinon deux tubes) + lueurs de tir au bout
                    for (side in listOf(-1f, 1f)) {
                        if (cannonTex == null) bills.add(Bill(z - 0.5f, x + side * pw * 0.36f, gy + lift + pw * 0.45f, 0.35f, null, 1f, 0.35f, 0.38f, 0.45f, stretch = 3.5f))
                        bills.add(Bill(z - 0.6f, x + side * pw * 0.40f, gy + lift + pw * 0.62f, 0.55f, disc, 0.6f + 0.4f * sin(g.elapsed * 30f), 1f, 0.9f, 0.4f))
                    }
                }
                if (g.passengerKind >= 0) {
                    val hop = abs(sin(g.elapsed * 9f)) * 0.15f
                    val pt = tex["vv_" + PASSENGER_KEYS[g.passengerKind]]
                    bills.add(Bill(z - 0.05f, x, gy + lift + pw * 0.62f * sq + hop, pw * 0.36f, pt, 1f, 1f, 0.36f, 0.64f))
                }
                if (g.boostLevel > 0) for (i in 0..3) {
                    val (tr, tg, tb) = if (g.boostLevel == 2) g.rainbow(g.elapsed * 2f + i * 0.25f) else Triple(0.29f, 0.88f, 1f)
                    bills.add(Bill(z + 0.5f + i * 0.4f, x + (i - 1.5f) * pw * 0.22f, gy + 0.2f, 0.25f, null, 0.5f, tr, tg, tb, stretch = if (g.boostLevel == 2) 9f else 6f))
                }
                // traînée d'étoiles en l'air (façon Mario Kart)
                if (g.jumping && jf > 0.3f) for (i in 0..2) bills.add(Bill(z + 1.2f + i * 0.9f, x + (i - 1f) * 0.6f, gy + lift * (0.7f - i * 0.15f) + 0.5f, 0.45f, null, 0.6f - i * 0.15f, 1f, 0.9f, 0.3f))
            }
        }
        for (p in g.parts) {
            val a = (p.life / p.maxLife).coerceIn(0f, 1f)
            bills.add(Bill(p.z, p.x, p.y, p.size, disc, a, p.r, p.g, p.b))
        }
        bills.sortByDescending { it.z }
        var cur: Int = -1
        begin()
        for (b in bills) {
            val tid = b.tex?.id ?: white
            if (tid != cur) { if (nVerts > 0) flush(cur); begin(); cur = tid }
            billboard(b)
            if (nVerts > MAX_VERTS - 12) { flush(cur); begin() }
        }
        if (nVerts > 0) flush(cur)
    }

    private fun billboard(b: Bill) {
        val t = b.tex
        val w = b.w
        val h = if (t != null) w * t.aspect * b.squash else w * b.squash
        val hw = w / 2f
        val x0 = b.x - hw; val x1 = b.x + hw
        val uMax = t?.uMax ?: 1f; val vMax = t?.vMax ?: 1f
        if (b.flat) {
            val hd = (if (t == null) h * b.stretch else h) / 2f * 1.6f
            val y = b.y + 0.04f
            val z0 = b.z - hd; val z1 = b.z + hd
            vert(x0, y, z0, 0f, vMax, b.r, b.g, b.b, b.alpha); vert(x1, y, z0, uMax, vMax, b.r, b.g, b.b, b.alpha); vert(x1, y, z1, uMax, 0f, b.r, b.g, b.b, b.alpha)
            vert(x0, y, z0, 0f, vMax, b.r, b.g, b.b, b.alpha); vert(x1, y, z1, uMax, 0f, b.r, b.g, b.b, b.alpha); vert(x0, y, z1, 0f, 0f, b.r, b.g, b.b, b.alpha)
            return
        }
        val hh = if (t == null) h * b.stretch else h
        val isPiece = b.fx0 != 0f || b.fx1 != 1f || b.fy0 != 0f || b.fy1 != 1f || b.shTop != 0f || b.shBot != 0f || b.dyL != 0f || b.dyR != 0f
        if (b.rot != 0f || isPiece) {
            // morceau du sprite (jambes, ailes…) déformé, puis rotation dans le plan du sprite autour du bas-centre
            val a = Math.toRadians(b.rot.toDouble()); val c = cos(a).toFloat(); val sn = sin(a).toFloat()
            fun rx(dx: Float, dy: Float) = b.x + dx * c - dy * sn
            fun ry(dx: Float, dy: Float) = b.y + dx * sn + dy * c
            val lx = -hw + w * b.fx0; val rxx = -hw + w * b.fx1
            val yb = hh * b.fy0; val yt = hh * b.fy1
            val u0 = uMax * b.fx0; val u1 = uMax * b.fx1
            val vTop = vMax * (1f - b.fy1); val vBot = vMax * (1f - b.fy0)
            // coins : bas-gauche, bas-droit, haut-droit, haut-gauche (cisaillements inclus)
            val blx = lx + b.shBot; val bly = yb + b.dyL
            val brx = rxx + b.shBot; val bry = yb + b.dyR
            val trx = rxx + b.shTop; val tryy = yt + b.dyR
            val tlx = lx + b.shTop; val tly = yt + b.dyL
            vert(rx(blx, bly), ry(blx, bly), b.z, u0, vBot, b.r, b.g, b.b, b.alpha); vert(rx(brx, bry), ry(brx, bry), b.z, u1, vBot, b.r, b.g, b.b, b.alpha); vert(rx(trx, tryy), ry(trx, tryy), b.z, u1, vTop, b.r, b.g, b.b, b.alpha)
            vert(rx(blx, bly), ry(blx, bly), b.z, u0, vBot, b.r, b.g, b.b, b.alpha); vert(rx(trx, tryy), ry(trx, tryy), b.z, u1, vTop, b.r, b.g, b.b, b.alpha); vert(rx(tlx, tly), ry(tlx, tly), b.z, u0, vTop, b.r, b.g, b.b, b.alpha)
            return
        }
        val y0 = b.y; val y1 = b.y + hh
        vert(x0, y0, b.z, 0f, vMax, b.r, b.g, b.b, b.alpha); vert(x1, y0, b.z, uMax, vMax, b.r, b.g, b.b, b.alpha); vert(x1, y1, b.z, uMax, 0f, b.r, b.g, b.b, b.alpha)
        vert(x0, y0, b.z, 0f, vMax, b.r, b.g, b.b, b.alpha); vert(x1, y1, b.z, uMax, 0f, b.r, b.g, b.b, b.alpha); vert(x0, y1, b.z, 0f, 0f, b.r, b.g, b.b, b.alpha)
    }

    // ------------------------------------------------------------ projection
    private val tmp4 = FloatArray(4); private val res4 = FloatArray(4)
    private fun projectNdcY(x: Float, y: Float, z: Float): Float {
        tmp4[0] = x; tmp4[1] = y; tmp4[2] = z; tmp4[3] = 1f
        Matrix.multiplyMV(res4, 0, mvp, 0, tmp4, 0)
        return if (abs(res4[3]) < 1e-5f) 0f else res4[1] / res4[3]
    }
    private fun projectTexts() {
        val s = game.dist
        for (t in game.texts) {
            tmp4[0] = game.xCenter(t.z) + t.x; tmp4[1] = game.groundY(s + t.z) + t.y; tmp4[2] = t.z; tmp4[3] = 1f
            Matrix.multiplyMV(res4, 0, mvp, 0, tmp4, 0)
            if (res4[3] > 1e-4f) {
                t.sx = (res4[0] / res4[3] * 0.5f + 0.5f) * viewW
                t.sy = (0.5f - res4[1] / res4[3] * 0.5f) * viewH
            }
        }
    }

    // ------------------------------------------------------------ buffer
    private fun begin() { fb.clear(); nVerts = 0 }
    private fun vert(x: Float, y: Float, z: Float, u: Float, v: Float, r: Float, g: Float, b: Float, a: Float) {
        if (nVerts >= MAX_VERTS) return
        fb.put(x).put(y).put(z).put(u).put(v).put(r).put(g).put(b).put(a); nVerts++
    }
    private fun vertNdc(x: Float, y: Float, r: Float, g: Float, b: Float) = vert(x, y, 0f, 0f, 0f, r, g, b, 1f)
    private fun quadNdc(x0: Float, x1: Float, y0: Float, y1: Float, r0: Float, g0: Float, b0: Float, r1: Float, g1: Float, b1: Float) {
        vertNdc(x0, y0, r0, g0, b0); vertNdc(x1, y0, r0, g0, b0); vertNdc(x1, y1, r1, g1, b1)
        vertNdc(x0, y0, r0, g0, b0); vertNdc(x1, y1, r1, g1, b1); vertNdc(x0, y1, r1, g1, b1)
    }
    private fun quad3(x0: Float, y0: Float, z0: Float, x1: Float, y1: Float, z1: Float, x2: Float, y2: Float, z2: Float, x3: Float, y3: Float, z3: Float, r: Float, g: Float, b: Float) {
        vert(x0, y0, z0, 0f, 0f, r, g, b, 1f); vert(x1, y1, z1, 0f, 0f, r, g, b, 1f); vert(x2, y2, z2, 0f, 0f, r, g, b, 1f)
        vert(x0, y0, z0, 0f, 0f, r, g, b, 1f); vert(x2, y2, z2, 0f, 0f, r, g, b, 1f); vert(x3, y3, z3, 0f, 0f, r, g, b, 1f)
    }
    private fun flush(texId: Int) {
        if (nVerts == 0) return
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId)
        val stride = 9 * 4
        fb.position(0); GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, stride, fb)
        fb.position(3); GLES20.glVertexAttribPointer(aUV, 2, GLES20.GL_FLOAT, false, stride, fb)
        fb.position(5); GLES20.glVertexAttribPointer(aCol, 4, GLES20.GL_FLOAT, false, stride, fb)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, nVerts)
        nVerts = 0
    }

    // ------------------------------------------------------------ GL utils
    private fun buildProgram(): Int {
        val vs = """
            uniform mat4 uMVP; uniform vec3 uEye;
            attribute vec3 aPos; attribute vec2 aUV; attribute vec4 aCol;
            varying vec2 vUV; varying vec4 vCol; varying float vDist;
            void main() { gl_Position = uMVP * vec4(aPos, 1.0); vUV = aUV; vCol = aCol; vDist = length(aPos - uEye); }
        """.trimIndent()
        val fs = """
            precision mediump float;
            uniform sampler2D uTex; uniform float uFog; uniform vec3 uFogColor;
            varying vec2 vUV; varying vec4 vCol; varying float vDist;
            void main() {
                // texture pré-multipliée x teinte (rgb*a) : sortie pré-multipliée pour le blending (1, 1-a)
                vec4 t = texture2D(uTex, vUV);
                float a = t.a * vCol.a;
                if (a < 0.03) discard;
                vec3 rgb = t.rgb * vCol.rgb * vCol.a;
                float f = clamp((vDist - 90.0) / 230.0, 0.0, 1.0) * uFog;
                float fade = 1.0 - 0.35 * f;
                gl_FragColor = vec4(mix(rgb, uFogColor * a, f) * fade, a * fade);
            }
        """.trimIndent()
        val v = compile(GLES20.GL_VERTEX_SHADER, vs); val f = compile(GLES20.GL_FRAGMENT_SHADER, fs)
        val p = GLES20.glCreateProgram()
        GLES20.glAttachShader(p, v); GLES20.glAttachShader(p, f); GLES20.glLinkProgram(p)
        return p
    }
    private fun compile(type: Int, src: String): Int { val s = GLES20.glCreateShader(type); GLES20.glShaderSource(s, src); GLES20.glCompileShader(s); return s }
    /** Anneau fin à bords doux (onde de choc). */
    private fun makeSoftRing(): Int {
        val n = 128
        val bmp = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
        val px = IntArray(n * n)
        for (y in 0 until n) for (x in 0 until n) {
            val dx = (x + 0.5f) / n - 0.5f; val dy = (y + 0.5f) / n - 0.5f
            val d = kotlin.math.sqrt(dx * dx + dy * dy) * 2f
            val a = (1f - abs(d - 0.82f) / 0.16f).coerceIn(0f, 1f)
            val aa = (a * a * 255f).toInt()
            px[y * n + x] = (aa shl 24) or 0xFFFFFF
        }
        bmp.setPixels(px, 0, n, 0, 0, n, n)
        val ids = IntArray(1); GLES20.glGenTextures(1, ids, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, ids[0])
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp, 0); bmp.recycle()
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        return ids[0]
    }
    /** Disque à bord doux (alpha radial) : ombres portées, particules, halos. */
    private fun makeSoftDisc(): Int {
        val n = 64
        val bmp = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
        val px = IntArray(n * n)
        for (y in 0 until n) for (x in 0 until n) {
            val dx = (x + 0.5f) / n * 2f - 1f; val dy = (y + 0.5f) / n * 2f - 1f
            val d = kotlin.math.sqrt(dx * dx + dy * dy)
            val a = ((1f - d) / 0.45f).coerceIn(0f, 1f)
            val aa = (a * a * (3f - 2f * a) * 255f).toInt()
            px[y * n + x] = (aa shl 24) or 0xFFFFFF
        }
        bmp.setPixels(px, 0, n, 0, 0, n, n)
        val ids = IntArray(1); GLES20.glGenTextures(1, ids, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, ids[0])
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp, 0); bmp.recycle()
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        return ids[0]
    }
    private fun makeWhiteTexture(): Int {
        val ids = IntArray(1); GLES20.glGenTextures(1, ids, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, ids[0])
        val bmp = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888); bmp.eraseColor(-1)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp, 0); bmp.recycle()
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST)
        return ids[0]
    }
    private fun loadTexture(name: String): Tex? {
        val id = ctx.resources.getIdentifier(name, "drawable", ctx.packageName)
        if (id == 0) return null
        val opts = BitmapFactory.Options().apply { inScaled = false; inPreferredConfig = Bitmap.Config.ARGB_8888 }
        val src = BitmapFactory.decodeResource(ctx.resources, id, opts) ?: return null
        var pot = 64; while (pot < max(src.width, src.height)) pot *= 2
        val padded = Bitmap.createBitmap(pot, pot, Bitmap.Config.ARGB_8888)
        Canvas(padded).drawBitmap(src, 0f, 0f, null)
        val ids = IntArray(1); GLES20.glGenTextures(1, ids, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, ids[0])
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, padded, 0)
        GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR_MIPMAP_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        val t = Tex(ids[0], src.width.toFloat() / pot, src.height.toFloat() / pot, src.height.toFloat() / src.width)
        padded.recycle(); src.recycle()
        return t
    }
}

internal val VEHICLE_RGB = listOf(
    Triple(1f, 0.23f, 0.23f), Triple(0.23f, 0.47f, 0.79f), Triple(0.89f, 0.23f, 0.23f), Triple(1f, 0.82f, 0.31f), Triple(0.31f, 0.58f, 0.31f),
    Triple(0.6f, 0.36f, 1f), Triple(1f, 0.75f, 0.2f), Triple(0.42f, 0.75f, 0.23f), Triple(1f, 0.36f, 0.64f), Triple(1f, 0.54f, 0.24f),
    Triple(0.95f, 0.76f, 0.1f), Triple(0.55f, 0.35f, 0.24f)
)
