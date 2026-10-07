package com.goodbrainrot.app.data

import com.goodbrainrot.app.R
import com.goodbrainrot.app.audio.GuitarTimbre
import kotlin.math.pow

/**
 * Type de guitare : un emoji, un libellé, un timbre de synthèse et un accordage
 * (note la plus grave + intervalles entre cordes, répétés pour gérer un nombre
 * de cordes variable). L'accordage n'a pas vocation à être « exact » : il vise
 * un rendu agréable pour des enfants.
 */
data class GuitarType(
    val id: String,
    val labelRes: Int,
    val emoji: String,
    val timbre: GuitarTimbre,
    val baseMidi: Int,
    val intervals: List<Int>,
    val sampleSet: String,    // échantillons réalistes : acoustic/electric/nylon/bass
    val neckColor: Long       // couleur du manche selon le style
) {
    fun stringFrequencies(count: Int): List<Double> {
        val out = ArrayList<Double>(count)
        var midi = baseMidi
        for (i in 0 until count) {
            out.add(440.0 * 2.0.pow((midi - 69) / 12.0))
            midi += intervals[i % intervals.size]
        }
        return out // grave -> aigu
    }
}

object GuitarTypes {
    private val GTR = listOf(5, 5, 5, 4, 5) // accordage type guitare (quartes + tierce)

    val ALL = listOf(
        GuitarType("folk", R.string.guitar_folk, "🎸", GuitarTimbre.FOLK, 40, GTR, "acoustic", 0xFF6B4A2B),
        GuitarType("pop", R.string.guitar_pop, "🎵", GuitarTimbre.POP, 40, GTR, "acoustic", 0xFF8A5A3A),
        GuitarType("blues", R.string.guitar_blues, "🎶", GuitarTimbre.BLUES, 40, GTR, "electric", 0xFF2B3A6B),
        GuitarType("rock", R.string.guitar_rock, "🤘", GuitarTimbre.ROCK, 40, GTR, "electric", 0xFF3A2B4A),
        GuitarType("metal", R.string.guitar_metal, "🔥", GuitarTimbre.METAL, 35, GTR, "electric", 0xFF2A1518),
        GuitarType("psyche", R.string.guitar_psyche, "🌈", GuitarTimbre.PSYCHE, 40, GTR, "electric", 0xFF5A2B5A),
        GuitarType("ukulele", R.string.guitar_ukulele, "🌺", GuitarTimbre.UKULELE, 60, listOf(4, 3, 5), "nylon", 0xFF9A7B4A),
        GuitarType("bass", R.string.guitar_bass, "🎻", GuitarTimbre.BASS, 28, listOf(5, 5, 5), "bass", 0xFF1E2A2A)
    )

    fun byIndex(i: Int): GuitarType = ALL[i.coerceIn(0, ALL.size - 1)]
}
