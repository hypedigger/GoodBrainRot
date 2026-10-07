package com.goodbrainrot.app.ui.garden

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
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

// ===========================================================================
// Mini-moteur 3D OpenGL ES 2.0 pour « Jardinier Zen » : maillages éclairés
// (Lambert + ambiance), textures répétées, cartes alpha (feuillage), brouillard,
// caméra perspective avec projection / dé-projection écran <-> monde.
// ===========================================================================

internal class Vec3(var x: Float, var y: Float, var z: Float) {
    operator fun plus(o: Vec3) = Vec3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: Vec3) = Vec3(x - o.x, y - o.y, z - o.z)
    operator fun times(k: Float) = Vec3(x * k, y * k, z * k)
    fun len() = sqrt(x * x + y * y + z * z)
    fun norm(): Vec3 { val l = len(); return if (l < 1e-6f) Vec3(0f, 1f, 0f) else Vec3(x / l, y / l, z / l) }
    fun lerp(o: Vec3, t: Float) = Vec3(x + (o.x - x) * t, y + (o.y - y) * t, z + (o.z - z) * t)
    fun copy() = Vec3(x, y, z)
}

/** Maillage statique en VBO : pos(3) nrm(3) uv(2). */
internal class GlMesh(data: FloatArray) {
    val count = data.size / 8
    private var vbo = 0
    private val buf: FloatBuffer = ByteBuffer.allocateDirect(data.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().put(data).also { it.position(0) }
    fun upload() {
        if (vbo != 0) return
        val ids = IntArray(1); GLES20.glGenBuffers(1, ids, 0); vbo = ids[0]
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo)
        buf.position(0)
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, buf.capacity() * 4, buf, GLES20.GL_STATIC_DRAW)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
    }
    fun bind(aPos: Int, aNrm: Int, aUV: Int) {
        upload()
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo)
        GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, 32, 0)
        GLES20.glVertexAttribPointer(aNrm, 3, GLES20.GL_FLOAT, false, 32, 12)
        GLES20.glVertexAttribPointer(aUV, 2, GLES20.GL_FLOAT, false, 32, 24)
    }
    fun invalidate() { vbo = 0 }
}

/** Constructeurs de maillages (triangles, sens direct, normales lissées ou plates). */
internal object Meshes {
    private class B { val d = ArrayList<Float>(4096)
        fun v(x: Float, y: Float, z: Float, nx: Float, ny: Float, nz: Float, u: Float, w: Float) { d.add(x); d.add(y); d.add(z); d.add(nx); d.add(ny); d.add(nz); d.add(u); d.add(w) }
        fun build() = GlMesh(d.toFloatArray()) }

    /** Cylindre unité : rayon 1 en bas (y=0) et rayon [topScale] en haut (y=1). */
    fun cylinder(seg: Int = 12, topScale: Float = 1f, capped: Boolean = true): GlMesh {
        val b = B()
        for (i in 0 until seg) {
            val a0 = i * 2f * PI.toFloat() / seg; val a1 = (i + 1) * 2f * PI.toFloat() / seg
            val c0 = cos(a0); val s0 = sin(a0); val c1 = cos(a1); val s1 = sin(a1)
            val u0 = i.toFloat() / seg; val u1 = (i + 1f) / seg
            b.v(c0, 0f, s0, c0, 0f, s0, u0, 1f); b.v(c1, 0f, s1, c1, 0f, s1, u1, 1f); b.v(c1 * topScale, 1f, s1 * topScale, c1, 0f, s1, u1, 0f)
            b.v(c0, 0f, s0, c0, 0f, s0, u0, 1f); b.v(c1 * topScale, 1f, s1 * topScale, c1, 0f, s1, u1, 0f); b.v(c0 * topScale, 1f, s0 * topScale, c0, 0f, s0, u0, 0f)
            if (capped) {
                b.v(0f, 1f, 0f, 0f, 1f, 0f, 0.5f, 0.5f); b.v(c1 * topScale, 1f, s1 * topScale, 0f, 1f, 0f, 0.5f + c1 * 0.5f, 0.5f + s1 * 0.5f); b.v(c0 * topScale, 1f, s0 * topScale, 0f, 1f, 0f, 0.5f + c0 * 0.5f, 0.5f + s0 * 0.5f)
                b.v(0f, 0f, 0f, 0f, -1f, 0f, 0.5f, 0.5f); b.v(c0, 0f, s0, 0f, -1f, 0f, 0.5f + c0 * 0.5f, 0.5f + s0 * 0.5f); b.v(c1, 0f, s1, 0f, -1f, 0f, 0.5f + c1 * 0.5f, 0.5f + s1 * 0.5f)
            }
        }
        return b.build()
    }

    fun sphere(lat: Int = 10, lon: Int = 14): GlMesh {
        val b = B()
        for (i in 0 until lat) {
            val t0 = PI.toFloat() * i / lat; val t1 = PI.toFloat() * (i + 1) / lat
            for (j in 0 until lon) {
                val p0 = 2f * PI.toFloat() * j / lon; val p1 = 2f * PI.toFloat() * (j + 1) / lon
                fun pt(t: Float, p: Float) = floatArrayOf(sin(t) * cos(p), cos(t), sin(t) * sin(p))
                val a = pt(t0, p0); val c = pt(t0, p1); val d = pt(t1, p1); val e = pt(t1, p0)
                fun put(q: FloatArray, u: Float, w: Float) = b.v(q[0], q[1], q[2], q[0], q[1], q[2], u, w)
                put(a, j / lon.toFloat(), i / lat.toFloat()); put(e, j / lon.toFloat(), (i + 1f) / lat); put(d, (j + 1f) / lon, (i + 1f) / lat)
                put(a, j / lon.toFloat(), i / lat.toFloat()); put(d, (j + 1f) / lon, (i + 1f) / lat); put(c, (j + 1f) / lon, i / lat.toFloat())
            }
        }
        return b.build()
    }

    /** Boîte centrée, dimensions 1. */
    fun box(): GlMesh {
        val b = B()
        fun face(nx: Float, ny: Float, nz: Float, ux: Float, uy: Float, uz: Float, vx: Float, vy: Float, vz: Float) {
            val cx = nx * 0.5f; val cy = ny * 0.5f; val cz = nz * 0.5f
            fun p(s: Float, t: Float) = floatArrayOf(cx + ux * s * 0.5f + vx * t * 0.5f, cy + uy * s * 0.5f + vy * t * 0.5f, cz + uz * s * 0.5f + vz * t * 0.5f)
            val a = p(-1f, -1f); val c = p(1f, -1f); val d = p(1f, 1f); val e = p(-1f, 1f)
            b.v(a[0], a[1], a[2], nx, ny, nz, 0f, 1f); b.v(c[0], c[1], c[2], nx, ny, nz, 1f, 1f); b.v(d[0], d[1], d[2], nx, ny, nz, 1f, 0f)
            b.v(a[0], a[1], a[2], nx, ny, nz, 0f, 1f); b.v(d[0], d[1], d[2], nx, ny, nz, 1f, 0f); b.v(e[0], e[1], e[2], nx, ny, nz, 0f, 0f)
        }
        face(0f, 0f, 1f, 1f, 0f, 0f, 0f, 1f, 0f); face(0f, 0f, -1f, -1f, 0f, 0f, 0f, 1f, 0f)
        face(1f, 0f, 0f, 0f, 0f, -1f, 0f, 1f, 0f); face(-1f, 0f, 0f, 0f, 0f, 1f, 0f, 1f, 0f)
        face(0f, 1f, 0f, 1f, 0f, 0f, 0f, 0f, -1f); face(0f, -1f, 0f, 1f, 0f, 0f, 0f, 0f, 1f)
        return b.build()
    }

    /** Carte verticale unité (largeur 1 centrée, hauteur 1 depuis y=0), face +z. */
    fun card(): GlMesh {
        val b = B()
        b.v(-0.5f, 0f, 0f, 0f, 0f, 1f, 0f, 1f); b.v(0.5f, 0f, 0f, 0f, 0f, 1f, 1f, 1f); b.v(0.5f, 1f, 0f, 0f, 0f, 1f, 1f, 0f)
        b.v(-0.5f, 0f, 0f, 0f, 0f, 1f, 0f, 1f); b.v(0.5f, 1f, 0f, 0f, 0f, 1f, 1f, 0f); b.v(-0.5f, 1f, 0f, 0f, 0f, 1f, 0f, 0f)
        return b.build()
    }

    /** Plan horizontal centré (taille 1) avec UV répétés [tile] fois, normale +y. */
    fun plane(tile: Float): GlMesh {
        val b = B()
        b.v(-0.5f, 0f, -0.5f, 0f, 1f, 0f, 0f, 0f); b.v(-0.5f, 0f, 0.5f, 0f, 1f, 0f, 0f, tile); b.v(0.5f, 0f, 0.5f, 0f, 1f, 0f, tile, tile)
        b.v(-0.5f, 0f, -0.5f, 0f, 1f, 0f, 0f, 0f); b.v(0.5f, 0f, 0.5f, 0f, 1f, 0f, tile, tile); b.v(0.5f, 0f, -0.5f, 0f, 1f, 0f, tile, 0f)
        return b.build()
    }

    /** Mur vertical (largeur 1 en x, hauteur 1 en y, face +z) avec UV répétés. */
    fun wall(tileX: Float, tileY: Float): GlMesh {
        val b = B()
        b.v(-0.5f, 0f, 0f, 0f, 0f, 1f, 0f, tileY); b.v(0.5f, 0f, 0f, 0f, 0f, 1f, tileX, tileY); b.v(0.5f, 1f, 0f, 0f, 0f, 1f, tileX, 0f)
        b.v(-0.5f, 0f, 0f, 0f, 0f, 1f, 0f, tileY); b.v(0.5f, 1f, 0f, 0f, 0f, 1f, tileX, 0f); b.v(-0.5f, 1f, 0f, 0f, 0f, 1f, 0f, 0f)
        return b.build()
    }
}

internal class GardenGlView(ctx: Context, val renderer: GardenRenderer) : GLSurfaceView(ctx) {
    init {
        setEGLContextClientVersion(2)
        setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY
    }
}

/**
 * Renderer générique : la scène est fournie par [onFrame] (appelé sur le thread GL avec dt),
 * qui utilise les primitives de dessin ci-dessous.
 */
internal class GardenRenderer(private val ctx: Context) : GLSurfaceView.Renderer {
    var onFrame: (GardenRenderer, Float) -> Unit = { _, _ -> }
    @Volatile var paused = false
    var viewW = 1; var viewH = 1
    private var program = 0
    private var aPos = 0; private var aNrm = 0; private var aUV = 0
    private var uMVP = 0; private var uModel = 0; private var uColor = 0; private var uTex = 0; private var uLight = 0
    private var uFog = 0; private var uFogColor = 0; private var uEye = 0; private var uAmbient = 0; private var uUvScale = 0; private var uUvOff = 0
    private var white = 0
    private val tex = HashMap<String, Int>()
    private val proj = FloatArray(16); private val view = FloatArray(16); private val vp = FloatArray(16)
    private val mvp = FloatArray(16); private val model = FloatArray(16); private val tmpM = FloatArray(16); private val invVp = FloatArray(16)
    private var lastNs = 0L
    var eye = Vec3(0f, 3f, 8f); var target = Vec3(0f, 1.5f, 0f)
    var fov = 55f
    var fogColor = floatArrayOf(0.85f, 0.92f, 1f); var fogStart = 40f; var fogEnd = 140f
    var lightDir = floatArrayOf(0.4f, 0.8f, 0.45f)
    var clearColor = floatArrayOf(0.62f, 0.80f, 0.95f)
    // maillages de base partagés
    lateinit var cyl: GlMesh; lateinit var cone: GlMesh; lateinit var sph: GlMesh; lateinit var box: GlMesh; lateinit var card: GlMesh
    lateinit var ground: GlMesh; lateinit var wall: GlMesh
    private val meshes = ArrayList<GlMesh>()
    var ready = false

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        program = buildProgram()
        aPos = GLES20.glGetAttribLocation(program, "aPos"); aNrm = GLES20.glGetAttribLocation(program, "aNrm"); aUV = GLES20.glGetAttribLocation(program, "aUV")
        uMVP = loc("uMVP"); uModel = loc("uModel"); uColor = loc("uColor"); uTex = loc("uTex"); uLight = loc("uLight")
        uFog = loc("uFog"); uFogColor = loc("uFogColor"); uEye = loc("uEye"); uAmbient = loc("uAmbient"); uUvScale = loc("uUvScale"); uUvOff = loc("uUvOff")
        white = makeWhite()
        tex.clear()
        for (n in listOf("gz_bark", "gz_grass", "gz_hedge", "gz_soil", "gz_rings", "gz_metal", "gz_sky", "gz_bamboo"))
            loadTexture(n, repeat = n != "gz_sky" && n != "gz_rings")?.let { tex[n] = it }
        for (n in listOf("gz_leaf1", "gz_leaf2", "gz_leaf3", "gz_flower1", "gz_flower2", "gz_bee", "gz_nest", "gz_bird",
                         "gz_gardener_a", "gz_gardener_b", "gz_tree_a", "gz_tree_b", "gz_fence", "gz_flowerbed", "gz_butterfly", "gz_cloud", "gz_bench", "gz_stump",
                         "gz_leaf_bamboo", "gz_shed", "gz_wheelbarrow", "gz_watering", "gz_pot"))
            loadTexture(n, repeat = false)?.let { tex[n] = it }
        tex["gz_disc"] = makeSoftDisc()
        cyl = Meshes.cylinder(12, 1f); cone = Meshes.cylinder(12, 0.02f); sph = Meshes.sphere(); box = Meshes.box(); card = Meshes.card()
        ground = Meshes.plane(24f); wall = Meshes.wall(6f, 2f)
        for (m in listOf(cyl, cone, sph, box, card, ground, wall)) { m.invalidate(); m.upload() }
        for (m in meshes) { m.invalidate(); m.upload() }
        GLES20.glEnable(GLES20.GL_DEPTH_TEST); GLES20.glDepthFunc(GLES20.GL_LEQUAL)
        // bitmaps Android = pré-multipliés -> blending (1, 1-a) ; sinon franges grises sur les cartes (nuages…)
        GLES20.glEnable(GLES20.GL_BLEND); GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glDisable(GLES20.GL_CULL_FACE)   // plans/cartes vus des deux côtés ; le z-buffer suffit
        lastNs = 0L; ready = true
    }
    private fun loc(n: String) = GLES20.glGetUniformLocation(program, n)

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height); viewW = width; viewH = height
    }

    override fun onDrawFrame(gl: GL10?) {
        val now = System.nanoTime()
        val dt = if (lastNs == 0L) 0f else ((now - lastNs) / 1_000_000_000f).coerceIn(0f, 0.05f)
        lastNs = now
        GLES20.glClearColor(clearColor[0], clearColor[1], clearColor[2], 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        GLES20.glUseProgram(program)
        GLES20.glEnableVertexAttribArray(aPos); GLES20.glEnableVertexAttribArray(aNrm); GLES20.glEnableVertexAttribArray(aUV)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0); GLES20.glUniform1i(uTex, 0)
        val l = Vec3(lightDir[0], lightDir[1], lightDir[2]).norm()
        GLES20.glUniform3f(uLight, l.x, l.y, l.z)
        GLES20.glUniform3f(uFogColor, fogColor[0], fogColor[1], fogColor[2])
        GLES20.glUniform2f(uFog, fogStart, fogEnd)
        GLES20.glUniform1f(uAmbient, 0.42f)
        onFrame(this, if (paused) 0f else dt)
    }

    // ---------------------------------------------------------------- caméra
    fun setCamera() {
        Matrix.perspectiveM(proj, 0, fov, viewW.toFloat() / max(1, viewH), 0.2f, 400f)
        Matrix.setLookAtM(view, 0, eye.x, eye.y, eye.z, target.x, target.y, target.z, 0f, 1f, 0f)
        Matrix.multiplyMM(vp, 0, proj, 0, view, 0)
        Matrix.invertM(invVp, 0, vp, 0)
        GLES20.glUniform3f(uEye, eye.x, eye.y, eye.z)
    }
    private val t4 = FloatArray(4); private val r4 = FloatArray(4)
    /** Monde -> écran (pixels). Renvoie null si derrière la caméra. */
    fun project(p: Vec3, out: FloatArray): Boolean {
        t4[0] = p.x; t4[1] = p.y; t4[2] = p.z; t4[3] = 1f
        Matrix.multiplyMV(r4, 0, vp, 0, t4, 0)
        if (r4[3] <= 1e-4f) return false
        out[0] = (r4[0] / r4[3] * 0.5f + 0.5f) * viewW; out[1] = (0.5f - r4[1] / r4[3] * 0.5f) * viewH
        return true
    }
    /** Écran -> rayon monde (origine = œil, direction normalisée). */
    fun unproject(sx: Float, sy: Float): Vec3 {
        val nx = sx / viewW * 2f - 1f; val ny = 1f - sy / viewH * 2f
        t4[0] = nx; t4[1] = ny; t4[2] = 1f; t4[3] = 1f
        Matrix.multiplyMV(r4, 0, invVp, 0, t4, 0)
        val far = Vec3(r4[0] / r4[3], r4[1] / r4[3], r4[2] / r4[3])
        return (far - eye).norm()
    }

    // ---------------------------------------------------------------- dessin
    fun texId(name: String): Int = tex[name] ?: white
    fun hasTex(name: String) = tex.containsKey(name)

    /** Dessine [mesh] avec la matrice modèle courante ([model]), teinte, texture. */
    fun draw(mesh: GlMesh, texName: String?, r: Float, g: Float, b: Float, a: Float = 1f, fog: Boolean = true, uvScale: Float = 1f, uvOffY: Float = 0f,
             @Suppress("UNUSED_PARAMETER") cull: Boolean = true, unlit: Boolean = false) {
        Matrix.multiplyMM(mvp, 0, vp, 0, model, 0)
        GLES20.glUniformMatrix4fv(uMVP, 1, false, mvp, 0)
        GLES20.glUniformMatrix4fv(uModel, 1, false, model, 0)
        GLES20.glUniform4f(uColor, r, g, b, a)
        GLES20.glUniform2f(uUvScale, uvScale, uvScale); GLES20.glUniform2f(uUvOff, 0f, uvOffY)
        if (!fog) GLES20.glUniform2f(uFog, 9999f, 10000f) else GLES20.glUniform2f(uFog, fogStart, fogEnd)
        if (unlit) { GLES20.glUniform1f(uAmbient, 1f); GLES20.glUniform3f(uLight, 0f, 0f, 0f) }
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, if (texName == null) white else texId(texName))
        mesh.bind(aPos, aNrm, aUV)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, mesh.count)
        if (unlit) { GLES20.glUniform1f(uAmbient, 0.42f); val l = Vec3(lightDir[0], lightDir[1], lightDir[2]).norm(); GLES20.glUniform3f(uLight, l.x, l.y, l.z) }
    }
    fun identity() = Matrix.setIdentityM(model, 0)
    fun translate(x: Float, y: Float, z: Float) = Matrix.translateM(model, 0, x, y, z)
    fun scale(x: Float, y: Float, z: Float) = Matrix.scaleM(model, 0, x, y, z)
    fun rotate(deg: Float, x: Float, y: Float, z: Float) = Matrix.rotateM(model, 0, deg, x, y, z)
    fun setModel(m: FloatArray) = System.arraycopy(m, 0, model, 0, 16)
    fun getModel(out: FloatArray) = System.arraycopy(model, 0, out, 0, 16)

    /** Oriente le repère pour qu'y aille de [a] vers [b] (cylindre = branche). */
    fun modelSegment(a: Vec3, b: Vec3, radius: Float, radiusTop: Float = radius) {
        val d = b - a; val len = d.len()
        identity(); translate(a.x, a.y, a.z)
        val dn = d.norm()
        // rotation qui amène (0,1,0) sur dn
        val axX = dn.z; val axZ = -dn.x
        val axLen = sqrt(axX * axX + axZ * axZ)
        if (axLen > 1e-5f) {
            val ang = Math.toDegrees(kotlin.math.acos(dn.y.coerceIn(-1f, 1f)).toDouble()).toFloat()
            rotate(ang, axX / axLen, 0f, axZ / axLen)
        } else if (dn.y < 0f) rotate(180f, 1f, 0f, 0f)
        scale(radius, len, radius)
        @Suppress("UNUSED_VARIABLE") val rt = radiusTop
    }

    /** Carte (billboard) verticale face caméra en [p], largeur w, hauteur h. */
    fun modelBillboard(p: Vec3, w: Float, h: Float) {
        identity(); translate(p.x, p.y, p.z)
        val dx = eye.x - p.x; val dz = eye.z - p.z
        val yaw = Math.toDegrees(kotlin.math.atan2(dx.toDouble(), dz.toDouble())).toFloat()
        rotate(yaw, 0f, 1f, 0f)
        scale(w, h, 1f)
    }

    /** Ciel : grand dôme (sphère inversée) texturé, suit la caméra. */
    fun drawSky() {
        GLES20.glDepthMask(false)
        identity(); translate(eye.x, eye.y - 20f, eye.z); scale(300f, 300f, 300f)
        draw(sph, if (hasTex("gz_sky")) "gz_sky" else null, if (hasTex("gz_sky")) 1f else 0.62f, if (hasTex("gz_sky")) 1f else 0.80f, if (hasTex("gz_sky")) 1f else 0.95f, 1f, fog = false, unlit = true)
        GLES20.glDepthMask(true)
    }
    fun depthMask(on: Boolean) = GLES20.glDepthMask(on)

    fun register(m: GlMesh) { meshes.add(m) }

    // ---------------------------------------------------------------- GL utils
    private fun buildProgram(): Int {
        val vs = """
            uniform mat4 uMVP; uniform mat4 uModel; uniform vec3 uEye; uniform vec2 uUvScale; uniform vec2 uUvOff;
            attribute vec3 aPos; attribute vec3 aNrm; attribute vec2 aUV;
            varying vec3 vNrm; varying vec2 vUV; varying vec3 vWp;
            void main() {
                gl_Position = uMVP * vec4(aPos, 1.0);
                vec4 wp = uModel * vec4(aPos, 1.0);
                vNrm = normalize(mat3(uModel) * aNrm);
                vUV = aUV * uUvScale + uUvOff;
                vWp = wp.xyz;
            }
        """.trimIndent()
        val fs = """
            precision mediump float;
            uniform sampler2D uTex; uniform vec4 uColor; uniform vec3 uLight; uniform vec2 uFog; uniform vec3 uFogColor; uniform float uAmbient; uniform vec3 uEye;
            varying vec3 vNrm; varying vec2 vUV; varying vec3 vWp;
            void main() {
                vec4 t = texture2D(uTex, vUV);
                float a = t.a * uColor.a;
                if (a < 0.08) discard;
                vec3 c = t.rgb * uColor.rgb * uColor.a;      // pré-multiplié
                float d = max(dot(normalize(vNrm), uLight), 0.0);
                float rim = 0.12 * (1.0 - abs(dot(normalize(vNrm), vec3(0.0, 0.0, 1.0))));
                vec3 lit = c * (uAmbient + 0.72 * d + rim);
                // brouillard PAR PIXEL (les grands plans n'ont que 4 sommets : par sommet, tout le sol serait « au loin »)
                float dist = length(vWp - uEye);
                float f = clamp((dist - uFog.x) / (uFog.y - uFog.x), 0.0, 1.0);
                gl_FragColor = vec4(mix(lit, uFogColor * a, f), a);
            }
        """.trimIndent()
        val v = compile(GLES20.GL_VERTEX_SHADER, vs); val f = compile(GLES20.GL_FRAGMENT_SHADER, fs)
        val p = GLES20.glCreateProgram(); GLES20.glAttachShader(p, v); GLES20.glAttachShader(p, f); GLES20.glLinkProgram(p)
        return p
    }
    private fun compile(type: Int, src: String): Int { val s = GLES20.glCreateShader(type); GLES20.glShaderSource(s, src); GLES20.glCompileShader(s); return s }
    private fun makeWhite(): Int {
        val ids = IntArray(1); GLES20.glGenTextures(1, ids, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, ids[0])
        val bmp = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888); bmp.eraseColor(-1)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp, 0); bmp.recycle()
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST)
        return ids[0]
    }
    /** Disque blanc à bord doux (ombres, soleil, halos). */
    private fun makeSoftDisc(): Int {
        val n = 128
        val bmp = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
        val px = IntArray(n * n)
        for (y in 0 until n) for (x in 0 until n) {
            val dx = (x + 0.5f) / n - 0.5f; val dy = (y + 0.5f) / n - 0.5f
            val d = sqrt(dx * dx + dy * dy) * 2f
            val a = ((1f - d) / 0.35f).coerceIn(0f, 1f)
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
    private fun loadTexture(name: String, repeat: Boolean): Int? {
        val id = ctx.resources.getIdentifier(name, "drawable", ctx.packageName)
        if (id == 0) return null
        val opts = BitmapFactory.Options().apply { inScaled = false; inPreferredConfig = Bitmap.Config.ARGB_8888 }
        val src = BitmapFactory.decodeResource(ctx.resources, id, opts) ?: return null
        var pot = 64; while (pot < max(src.width, src.height)) pot *= 2
        val bmp = if (src.width == pot && src.height == pot) src else Bitmap.createScaledBitmap(src, pot, pot, true)
        val ids = IntArray(1); GLES20.glGenTextures(1, ids, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, ids[0])
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp, 0)
        GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR_MIPMAP_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        val wrap = if (repeat) GLES20.GL_REPEAT else GLES20.GL_CLAMP_TO_EDGE
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, wrap)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, wrap)
        if (bmp !== src) bmp.recycle(); src.recycle()
        return ids[0]
    }
}
