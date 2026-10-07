package com.goodbrainrot.app.data

import android.content.Context
import android.media.MediaPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Sons réels de véhicules : assets/vehicle_sounds/&lt;key&gt;/ (ogg, mp3, wav).
 * Plusieurs fichiers possibles par véhicule → un est choisi **au hasard** à
 * chaque sélection. Si aucun fichier n'est présent, on se rabat sur le son
 * synthétisé ([onMissing]).
 */
object VehicleSounds {
    private const val MAX_MS = 6000L
    private val exts = listOf("ogg", "mp3", "wav", "m4a")
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var current: MediaPlayer? = null
    private var stopJob: Job? = null

    private fun variants(context: Context, key: String): List<String> = try {
        (context.assets.list("vehicle_sounds/$key") ?: emptyArray())
            .filter { it.substringAfterLast('.', "").lowercase() in exts }
    } catch (_: Exception) {
        emptyList()
    }

    fun play(context: Context, key: String, onMissing: () -> Unit) = play(context, key, MAX_MS, onMissing)

    /** Variante avec durée max (klaxons courts du jeu de voiture). */
    fun play(context: Context, key: String, maxMs: Long, onMissing: () -> Unit) {
        stop()
        val files = variants(context, key)
        if (files.isEmpty()) { onMissing(); return }
        val file = files.random()
        try {
            val afd = context.assets.openFd("vehicle_sounds/$key/$file")
            val mp = MediaPlayer()
            mp.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            afd.close()
            mp.setOnCompletionListener { stop() }
            mp.setOnErrorListener { _, _, _ -> stop(); true }
            mp.prepare()
            mp.start()
            current = mp
            stopJob = scope.launch {
                delay(maxMs)
                if (current === mp) stop()
            }
        } catch (_: Exception) {
            onMissing()
        }
    }

    fun stop() {
        stopJob?.cancel(); stopJob = null
        val mp = current ?: return
        current = null
        try { mp.stop() } catch (_: Exception) {}
        try { mp.release() } catch (_: Exception) {}
    }
}
