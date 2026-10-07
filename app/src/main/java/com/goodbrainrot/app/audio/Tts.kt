package com.goodbrainrot.app.audio

import android.content.Context
import android.speech.tts.TextToSpeech
import androidx.appcompat.app.AppCompatDelegate
import java.util.Locale

/**
 * Synthèse vocale Android, alignée sur la langue choisie dans l'app
 * (per-app locales via AppCompatDelegate).
 */
object Tts {
    private var tts: TextToSpeech? = null
    private var ready = false

    fun init(context: Context) {
        if (tts != null) return
        tts = TextToSpeech(context.applicationContext) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) applyLocale()
        }
    }

    fun currentLocale(): Locale {
        val locales = AppCompatDelegate.getApplicationLocales()
        return if (!locales.isEmpty) locales[0]!! else Locale.getDefault()
    }

    private fun applyLocale() {
        if (!ready) return
        try {
            tts?.language = currentLocale()
        } catch (_: Exception) {
        }
    }

    fun speak(text: String) {
        if (!ready) return
        applyLocale()
        tts?.setSpeechRate(0.85f)
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "goodbrainrot")
    }

    fun shutdown() {
        tts?.shutdown()
        tts = null
        ready = false
    }
}
