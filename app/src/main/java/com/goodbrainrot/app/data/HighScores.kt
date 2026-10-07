package com.goodbrainrot.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateOf

/** Une entrée de tableau des scores : libellé (date du jour) + score. */
data class ScoreEntry(val name: String, val score: Int)

/**
 * Tableaux des meilleurs scores (top 10) par jeu, persistés en SharedPreferences.
 * Format stocké : "AAA:123|BOB:99|..." (au plus 10 entrées triées décroissant).
 */
object HighScores {
    private const val MAX = 10
    private lateinit var sp: SharedPreferences

    // Bump pour forcer la recomposition après une mise à jour
    val version = mutableStateOf(0)

    fun init(context: Context) {
        if (::sp.isInitialized) return
        sp = context.applicationContext.getSharedPreferences("goodbrainrot_scores", Context.MODE_PRIVATE)
    }

    fun top(game: String): List<ScoreEntry> {
        val raw = sp.getString(game, "") ?: ""
        if (raw.isBlank()) return emptyList()
        return raw.split("|").mapNotNull { token ->
            val parts = token.split(":")
            if (parts.size == 2) parts[1].toIntOrNull()?.let { ScoreEntry(parts[0], it) } else null
        }.sortedByDescending { it.score }.take(MAX)
    }

    /** Le score entrerait-il dans le top 10 ? */
    fun qualifies(game: String, score: Int): Boolean {
        if (score <= 0) return false
        val list = top(game)
        return list.size < MAX || score > (list.lastOrNull()?.score ?: 0)
    }

    /** Meilleur score actuel (0 si vide). */
    fun best(game: String): Int = top(game).firstOrNull()?.score ?: 0

    /** Enregistre un score daté (plus de saisie de nom : le « nom » est la date du jour, ex. 06/09/26). */
    fun add(game: String, name: String, score: Int) {
        val label = name.replace(":", "").replace("|", "").ifBlank { today() }
        val updated = (top(game) + ScoreEntry(label, score))
            .sortedByDescending { it.score }
            .take(MAX)
        save(game, updated)
    }
    /** Enregistre le score avec la date du jour s'il entre dans le top ; renvoie true si c'est le nouveau meilleur. */
    fun record(game: String, score: Int): Boolean {
        val isBest = score > best(game)
        if (qualifies(game, score)) add(game, today(), score)
        return isBest && score > 0
    }
    fun today(): String = java.text.SimpleDateFormat("dd/MM/yy", java.util.Locale.getDefault()).format(java.util.Date())

    fun reset(game: String) = save(game, emptyList())

    fun resetAll() {
        sp.edit().clear().apply()
        version.value++
    }

    private fun save(game: String, list: List<ScoreEntry>) {
        val raw = list.joinToString("|") { "${it.name}:${it.score}" }
        sp.edit().putString(game, raw).apply()
        version.value++
    }

    object Games {
        const val BUBBLES = "bubbles"
        const val STARS = "stars"
        const val SHMUP = "shmup"
        const val SHMUP_TIMED = "shmup_timed"
        const val VROUM = "vroum"
        const val VROUM_ENDLESS = "vroum_endless"
        const val GARDEN_ZEN = "garden_zen"
        const val GARDEN_RUSH = "garden_rush"
        const val GARDEN_SLICE = "garden_slice"
        val ALL = listOf(BUBBLES, STARS, SHMUP, SHMUP_TIMED, VROUM, VROUM_ENDLESS, GARDEN_ZEN, GARDEN_RUSH, GARDEN_SLICE)
    }
}
