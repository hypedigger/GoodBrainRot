package com.goodbrainrot.app.audio

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** Vibrations (retour haptique) pour les impacts du jeu. Sans effet si l'appareil ne vibre pas. */
object Haptics {
    private var vib: Vibrator? = null

    fun init(context: Context) {
        vib = try {
            if (Build.VERSION.SDK_INT >= 31) {
                (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        } catch (_: Exception) { null }
    }

    private fun buzz(ms: Long, amplitude: Int) {
        val v = vib ?: return
        try {
            if (!v.hasVibrator()) return
            if (Build.VERSION.SDK_INT >= 26) {
                v.vibrate(VibrationEffect.createOneShot(ms, amplitude.coerceIn(1, 255)))
            } else {
                @Suppress("DEPRECATION") v.vibrate(ms)
            }
        } catch (_: Exception) {}
    }

    /** Motif de vibration (timings + amplitudes) pour un retour distinct. */
    private fun pattern(timings: LongArray, amplitudes: IntArray) {
        val v = vib ?: return
        try {
            if (!v.hasVibrator()) return
            if (Build.VERSION.SDK_INT >= 26) {
                v.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
            } else {
                @Suppress("DEPRECATION") v.vibrate(timings, -1)
            }
        } catch (_: Exception) {}
    }

    fun light() = buzz(18, 90)
    fun medium() = buzz(35, 160)
    fun heavy() = buzz(75, 255)

    /** Perte d'une vie : DEUX coups nets rapprochés (distinct des autres vibrations). */
    fun hit() = pattern(longArrayOf(0, 45, 55, 90), intArrayOf(0, 255, 0, 255))
}
