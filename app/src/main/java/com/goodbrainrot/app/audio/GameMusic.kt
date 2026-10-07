package com.goodbrainrot.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import com.goodbrainrot.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Musique de fond adaptative du shmup « Crimson Sky ».
 *
 * Chaque stage a son propre thème métal (généré hors-ligne, style Guilty Gear XX)
 * embarqué en OGG dans res/raw, et un thème de boss commun prend le relais pendant
 * les affrontements. Les OGG sont décodés une fois en PCM puis joués en boucle
 * matérielle (AudioTrack MODE_STATIC + loop points) → boucle sans couture, coût CPU
 * nul une fois lancée. Les transitions stage <-> boss se font en fondu enchaîné.
 */
object GameMusic {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private class Loop(val pcm: ShortArray, val sr: Int, val ch: Int)

    private var appCtx: Context? = null
    private val cache = HashMap<Int, Loop>()

    private var track: AudioTrack? = null
    @Volatile private var currentRes = 0
    @Volatile private var wantRes = 0
    private var swapJob: kotlinx.coroutines.Job? = null

    private const val VOL = 0.6f
    @Volatile var musicVolume = 0.85f     // réglable dans les options (0..1)
    private val vol: Float get() = VOL * musicVolume

    private fun stageRes(level: Int): Int = when (level) {
        1 -> R.raw.cs_stage1
        2 -> R.raw.cs_stage2
        3 -> R.raw.cs_stage3
        4 -> R.raw.cs_stage4
        5 -> R.raw.cs_stage5
        else -> R.raw.cs_stage6   // niveau 6 caché
    }

    fun init(ctx: Context) { if (appCtx == null) appCtx = ctx.applicationContext }

    /** Applique immédiatement le volume musique courant à la piste en cours. */
    fun applyVolume() { try { track?.setVolume(vol) } catch (_: Exception) {} }

    /** Démarre / bascule sur le thème du stage donné. */
    fun playStage(ctx: Context, level: Int) { init(ctx); switchTo(stageRes(level)) }

    /** Bascule sur le thème de boss (relais pendant les affrontements). */
    fun playBoss(ctx: Context) { init(ctx); switchTo(R.raw.cs_boss) }

    /** Thème des menus / intro (hangar, sélection). */
    fun playMenu(ctx: Context) { init(ctx); switchTo(R.raw.cs_stage6) }

    /** Piste quelconque de res/raw (autres jeux : Vroum Vrouuuuuuum). */
    fun playRes(ctx: Context, resId: Int) { init(ctx); switchTo(resId) }

    @Volatile private var pausedRes = 0
    @Volatile private var suspended = false   // app en arrière-plan : interdit tout démarrage

    /** App en arrière-plan : on ARRÊTE réellement la musique et on BLOQUE tout redémarrage
     *  (le directeur du jeu continue de tourner en tâche de fond et pourrait rappeler
     *  playStage/playBoss). */
    fun pause() {
        suspended = true
        pausedRes = if (wantRes != 0) wantRes else currentRes
        swapJob?.cancel()
        val t = track; track = null; currentRes = 0; wantRes = 0
        try { t?.pause() } catch (_: Exception) {}
        try { t?.stop() } catch (_: Exception) {}
        try { t?.release() } catch (_: Exception) {}
    }

    /** Retour au premier plan : on relance le dernier morceau voulu. */
    fun resume() {
        suspended = false
        val r = pausedRes
        pausedRes = 0
        if (r != 0) switchTo(r)
    }

    fun stop() {
        wantRes = 0
        swapJob?.cancel()
        val t = track; track = null; currentRes = 0
        scope.launch {
            try { t?.let { fade(it, it.getVolumeSafe(), 0f, 180) } } catch (_: Exception) {}
            try { t?.stop() } catch (_: Exception) {}
            try { t?.release() } catch (_: Exception) {}
        }
    }

    // ------------------------------------------------------------------

    private fun switchTo(resId: Int) {
        // en arrière-plan : on mémorise seulement le morceau voulu, sans rien démarrer
        if (suspended) { pausedRes = resId; return }
        if (resId == wantRes && track != null) return
        wantRes = resId
        swapJob?.cancel()
        swapJob = scope.launch {
            val loop = try { cache.getOrPut(resId) { decode(appCtx!!, resId) } }
                       catch (_: Exception) { return@launch }
            if (wantRes != resId) return@launch
            val next = try { buildTrack(loop) } catch (_: Exception) { return@launch }
            val prev = track
            track = next
            currentRes = resId
            try {
                next.setVolume(0f)
                next.play()
                // fondu enchaîné
                val steps = 12
                for (i in 1..steps) {
                    if (wantRes != resId) break
                    val k = i.toFloat() / steps
                    next.setVolume(vol * k)
                    prev?.setVolume(vol * (1f - k))
                    delay(22)
                }
                next.setVolume(if (wantRes == resId) vol else 0f)
            } catch (_: Exception) {}
            try { prev?.stop() } catch (_: Exception) {}
            try { prev?.release() } catch (_: Exception) {}
        }
    }

    private fun buildTrack(loop: Loop): AudioTrack {
        val mask = if (loop.ch >= 2) AudioFormat.CHANNEL_OUT_STEREO else AudioFormat.CHANNEL_OUT_MONO
        val t = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(loop.sr)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(mask)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(loop.pcm.size * 2)
            .build()
        t.write(loop.pcm, 0, loop.pcm.size)
        val frames = loop.pcm.size / loop.ch
        t.setLoopPoints(0, frames, -1) // -1 = boucle infinie
        return t
    }

    private suspend fun fade(t: AudioTrack, from: Float, to: Float, ms: Long) {
        val steps = 8
        for (i in 1..steps) {
            val k = i.toFloat() / steps
            t.setVolume(from + (to - from) * k)
            delay(ms / steps)
        }
    }

    private fun AudioTrack.getVolumeSafe(): Float = vol

    /** Décode un OGG de res/raw en PCM 16-bit entrelacé. */
    private fun decode(ctx: Context, resId: Int): Loop {
        val afd = ctx.resources.openRawResourceFd(resId)
        val ex = MediaExtractor()
        ex.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
        afd.close()
        var trackIdx = -1
        var fmt: MediaFormat? = null
        for (i in 0 until ex.trackCount) {
            val f = ex.getTrackFormat(i)
            if (f.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) { trackIdx = i; fmt = f; break }
        }
        ex.selectTrack(trackIdx)
        val mime = fmt!!.getString(MediaFormat.KEY_MIME)!!
        val codec = MediaCodec.createDecoderByType(mime)
        codec.configure(fmt, null, null, 0)
        codec.start()
        val out = ByteArrayOutputStream()
        val info = MediaCodec.BufferInfo()
        var sr = fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        var ch = fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        var inEos = false; var outEos = false
        while (!outEos) {
            if (!inEos) {
                val inIdx = codec.dequeueInputBuffer(10000)
                if (inIdx >= 0) {
                    val ib = codec.getInputBuffer(inIdx)!!
                    val sz = ex.readSampleData(ib, 0)
                    if (sz < 0) {
                        codec.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inEos = true
                    } else {
                        codec.queueInputBuffer(inIdx, 0, sz, ex.sampleTime, 0)
                        ex.advance()
                    }
                }
            }
            val outIdx = codec.dequeueOutputBuffer(info, 10000)
            if (outIdx >= 0) {
                if (info.size > 0) {
                    val ob = codec.getOutputBuffer(outIdx)!!
                    ob.position(info.offset)
                    ob.limit(info.offset + info.size)
                    val chunk = ByteArray(info.size)
                    ob.get(chunk)
                    out.write(chunk)
                }
                codec.releaseOutputBuffer(outIdx, false)
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outEos = true
            } else if (outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                val of = codec.outputFormat
                sr = of.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                ch = of.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            }
        }
        codec.stop(); codec.release(); ex.release()
        val bytes = out.toByteArray()
        val shorts = ShortArray(bytes.size / 2)
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts)
        return Loop(shorts, sr, if (ch < 1) 1 else ch)
    }
}
