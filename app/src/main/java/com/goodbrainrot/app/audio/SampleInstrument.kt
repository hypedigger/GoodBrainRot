package com.goodbrainrot.app.audio

import android.content.Context
import kotlin.math.abs
import kotlin.math.pow

/**
 * Instrument joué à partir d'échantillons accordés (piano/guitare/xylophone).
 * Les fichiers d'assets sont nommés par leur note MIDI : assets/instruments/<inst>/<midi>.(mp3|ogg|wav).
 * Pour jouer une fréquence quelconque, on prend l'échantillon le plus proche et on
 * ajuste la hauteur (rate de SoundPool, ±1 octave max).
 *
 * Optionnel : si aucun échantillon n'est présent, [play] renvoie false et
 * l'appelant se rabat sur la synthèse ([SoundEngine]).
 */
class SampleInstrument(private val folder: String) {
    private data class Sample(val midi: Int, val freq: Double, val soundId: Int)

    private val samples = ArrayList<Sample>()
    private var scanned = false
    private val exts = listOf("ogg", "mp3", "wav", "m4a", "flac")

    private fun midiToFreq(m: Int) = 440.0 * 2.0.pow((m - 69) / 12.0)

    fun preload(context: Context): Int {
        if (scanned) return samples.size
        scanned = true
        val files = try {
            context.assets.list("instruments/$folder") ?: emptyArray()
        } catch (_: Exception) { emptyArray() }
        if (files.isEmpty()) return 0
        for (f in files) {
            val name = f.substringBeforeLast('.')
            val ext = f.substringAfterLast('.', "")
            val midi = name.toIntOrNull() ?: continue
            if (ext.lowercase() !in exts) continue
            val id = SamplePool.load(context, "instruments/$folder/$f")
            if (id >= 0) samples.add(Sample(midi, midiToFreq(midi), id))
        }
        samples.sortBy { it.midi }
        return samples.size
    }

    val isAvailable: Boolean get() = samples.isNotEmpty()

    /** Joue la fréquence [freq] ; renvoie false si indisponible (→ repli synthèse). */
    fun play(freq: Double, volume: Float = 1f): Boolean {
        if (samples.isEmpty()) return false
        // échantillon le plus proche déjà chargé
        val s = samples.filter { SamplePool.isLoaded(it.soundId) }.minByOrNull { abs(it.freq - freq) } ?: return false
        val rate = (freq / s.freq).toFloat()
        if (rate > 1.65f || rate < 0.6f) return false  // trop éloigné → repli synthèse
        return SamplePool.play(s.soundId, rate, volume)
    }
}

/** Registre partagé des instruments à échantillons (chargés une seule fois). */
object Instruments {
    val piano = SampleInstrument("piano")
    val xylophone = SampleInstrument("xylophone")
    private val sets = HashMap<String, SampleInstrument>()

    /** Set de guitare/basse (acoustic, electric, nylon, bass). */
    fun guitarSet(set: String): SampleInstrument =
        sets.getOrPut(set) { SampleInstrument(set) }
}
