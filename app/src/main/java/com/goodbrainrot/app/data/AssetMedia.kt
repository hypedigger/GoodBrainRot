package com.goodbrainrot.app.data

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.goodbrainrot.app.audio.Tts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val IMAGE_EXTENSIONS = listOf("webp", "jpg", "jpeg", "png")
private val SOUND_EXTENSIONS = listOf("ogg", "mp3", "wav", "m4a")

/** Charge l'image d'un animal depuis assets/animals/, ou null (→ fallback emoji). */
fun loadAnimalImage(context: Context, key: String): ImageBitmap? =
    loadAssetImage(context, "animals", key)

/** Photo du bébé animal (jeu « Maman et bébé ») depuis assets/babies/. */
fun loadBabyImage(context: Context, key: String): ImageBitmap? =
    loadAssetImage(context, "babies", key)

/** Image de contenu générique (animaux/véhicules/dinos) depuis assets/<folder>/. */
fun loadContentImage(context: Context, folder: String, key: String): ImageBitmap? =
    loadAssetImage(context, folder, key)

private fun loadAssetImage(context: Context, folder: String, key: String): ImageBitmap? {
    for (ext in IMAGE_EXTENSIONS) {
        try {
            context.assets.open("$folder/$key.$ext").use { input ->
                BitmapFactory.decodeStream(input)?.let { return it.asImageBitmap() }
            }
        } catch (_: Exception) {
        }
    }
    return null
}

/** Liste des clés d'animaux qui ont un vrai enregistrement dans les assets. */
fun animalsWithSound(context: Context): Set<String> = try {
    (context.assets.list("sounds") ?: emptyArray())
        .map { it.substringBeforeLast('.') }
        .toSet()
} catch (_: Exception) {
    emptySet()
}

/**
 * Joue l'enregistrement de l'animal depuis assets/sounds/ ; si absent,
 * fallback : la synthèse vocale dit le nom de l'animal.
 */
object AnimalSoundPlayer {
    private const val MAX_DURATION_MS = 6000L
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var current: MediaPlayer? = null
    private var stopJob: Job? = null

    fun play(context: Context, animal: Animal, sayNameAfter: Boolean) {
        stop()
        val name = context.getString(animal.nameRes)
        for (ext in SOUND_EXTENSIONS) {
            try {
                val afd = context.assets.openFd("sounds/${animal.key}.$ext")
                val mp = MediaPlayer()
                mp.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                afd.close()
                mp.setOnCompletionListener {
                    stop()
                    if (sayNameAfter) Tts.speak(name)
                }
                mp.setOnErrorListener { _, _, _ -> stop(); true }
                mp.prepare()
                mp.start()
                current = mp
                stopJob = scope.launch {
                    delay(MAX_DURATION_MS)
                    if (current === mp) {
                        stop()
                        if (sayNameAfter) Tts.speak(name)
                    }
                }
                return
            } catch (_: Exception) {
            }
        }
        Tts.speak(name)
    }

    fun stop() {
        stopJob?.cancel()
        stopJob = null
        val mp = current ?: return
        current = null
        try { mp.stop() } catch (_: Exception) { }
        try { mp.release() } catch (_: Exception) { }
    }
}
