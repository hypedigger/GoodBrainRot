package com.goodbrainrot.app.data

import androidx.annotation.StringRes
import com.goodbrainrot.app.R

/**
 * Pool d'animaux. `key` sert à retrouver les assets optionnels :
 * image  -> assets/animals/<key>.(webp|jpg|png)
 * son    -> assets/sounds/<key>.(ogg|mp3|wav)
 * En l'absence d'asset : fallback emoji pour l'image, TTS pour le son.
 */
data class Animal(
    val key: String,
    val emoji: String,
    @StringRes val nameRes: Int
)

object Animals {
    val ALL = listOf(
        Animal("cat", "🐱", R.string.animal_cat),
        Animal("dog", "🐶", R.string.animal_dog),
        Animal("cow", "🐮", R.string.animal_cow),
        Animal("horse", "🐴", R.string.animal_horse),
        Animal("sheep", "🐑", R.string.animal_sheep),
        Animal("goat", "🐐", R.string.animal_goat),
        Animal("pig", "🐷", R.string.animal_pig),
        Animal("hen", "🐔", R.string.animal_hen),
        Animal("rooster", "🐓", R.string.animal_rooster),
        Animal("duck", "🦆", R.string.animal_duck),
        Animal("bird", "🐦", R.string.animal_bird),
        Animal("owl", "🦉", R.string.animal_owl),
        Animal("lion", "🦁", R.string.animal_lion),
        Animal("tiger", "🐯", R.string.animal_tiger),
        Animal("elephant", "🐘", R.string.animal_elephant),
        Animal("monkey", "🐵", R.string.animal_monkey),
        Animal("bear", "🐻", R.string.animal_bear),
        Animal("wolf", "🐺", R.string.animal_wolf),
        Animal("frog", "🐸", R.string.animal_frog),
        Animal("rabbit", "🐰", R.string.animal_rabbit),
        Animal("mouse", "🐭", R.string.animal_mouse),
        Animal("snake", "🐍", R.string.animal_snake),
        Animal("bee", "🐝", R.string.animal_bee),
        Animal("dolphin", "🐬", R.string.animal_dolphin)
    )

    fun byKey(key: String): Animal? = ALL.firstOrNull { it.key == key }
}

/** Alphabets du quiz "Devine la lettre", selon la langue de l'app. */
object Alphabets {
    fun forLanguage(lang: String): List<String> = when (lang) {
        "ru" -> "АБВГДЕЁЖЗИЙКЛМНОПРСТУФХЦЧШЩЫЭЮЯ".map { it.toString() }
        "it" -> "ABCDEFGHILMNOPQRSTUVZ".map { it.toString() }
        "es" -> "ABCDEFGHIJKLMNÑOPQRSTUVWXYZ".map { it.toString() }
        else -> "ABCDEFGHIJKLMNOPQRSTUVWXYZ".map { it.toString() }
    }
}
