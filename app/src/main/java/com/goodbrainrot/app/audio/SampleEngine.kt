package com.goodbrainrot.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool

/**
 * SoundPool **unique et partagé** par tous les samples (kits MPC, instruments).
 * Avant, chaque instrument créait son propre SoundPool : en changeant de guitare
 * on en empilait plusieurs et les ressources audio finissaient par s'épuiser
 * (les notes ne sortaient plus). Un seul pool de 32 voix règle ça.
 */
object SamplePool {
    private var pool: SoundPool? = null
    private val loaded = HashSet<Int>()

    private fun get(): SoundPool {
        pool?.let { return it }
        val p = SoundPool.Builder()
            .setMaxStreams(32)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .build()
        p.setOnLoadCompleteListener { _, id, status -> if (status == 0) loaded.add(id) }
        pool = p
        return p
    }

    fun isLoaded(id: Int): Boolean = id in loaded

    /** Charge un asset audio, renvoie son soundId (ou -1 en cas d'échec). */
    fun load(context: Context, assetPath: String): Int = try {
        val afd = context.assets.openFd(assetPath)
        val id = get().load(afd, 1)
        afd.close()
        id
    } catch (_: Exception) {
        -1
    }

    /** Joue un sample. Renvoie false si non chargé ou si aucune voix dispo. */
    fun play(id: Int, rate: Float = 1f, volume: Float = 1f): Boolean {
        if (id < 0 || id !in loaded) return false
        val p = pool ?: return false
        return p.play(id, volume, volume, 1, 0, rate.coerceIn(0.5f, 2.0f)) != 0
    }
}

/**
 * Joue de courts samples (kits MPC) depuis assets/kits/<kit>/<name>.*.
 * Si un sample manque ou n'est pas (encore) prêt, [play] renvoie false →
 * l'appelant se rabat sur la synthèse.
 */
object SampleEngine {
    private val ids = HashMap<String, Int>()      // "folder/name" -> soundId
    private val scanned = HashSet<String>()
    private val exts = listOf("ogg", "wav", "mp3", "m4a", "flac")

    fun preload(context: Context, folder: String): Int {
        if (folder in scanned) return ids.keys.count { it.startsWith("$folder/") }
        scanned.add(folder)
        val files = try { context.assets.list(folder) ?: emptyArray() } catch (_: Exception) { emptyArray() }
        var count = 0
        for (f in files) {
            val name = f.substringBeforeLast('.')
            val ext = f.substringAfterLast('.', "")
            if (ext.lowercase() !in exts) continue
            val id = SamplePool.load(context, "$folder/$f")
            if (id >= 0) { ids["$folder/$name"] = id; count++ }
        }
        return count
    }

    fun has(folder: String, name: String): Boolean = ids.containsKey("$folder/$name")

    /** Joue un sample ; false si absent ou pas prêt (→ repli synthèse). */
    fun play(folder: String, name: String): Boolean {
        val id = ids["$folder/$name"] ?: return false
        return SamplePool.play(id, 1f, 1f)
    }
}
