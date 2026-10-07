package com.goodbrainrot.app

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** Petites vibrations de retour, douces, pour les touches et les bonnes réponses. */
object Haptics {
    private fun vibrator(context: Context): Vibrator? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    } catch (_: Exception) {
        null
    }

    /** Tap léger (touche de piano, bulle, étoile). */
    fun tap(context: Context) {
        try {
            vibrator(context)?.vibrate(VibrationEffect.createOneShot(15, 80))
        } catch (_: Exception) {
        }
    }

    /** Double impulsion joyeuse (bonne réponse). */
    fun success(context: Context) {
        try {
            vibrator(context)?.vibrate(
                VibrationEffect.createWaveform(longArrayOf(0, 30, 80, 50), intArrayOf(0, 120, 0, 180), -1)
            )
        } catch (_: Exception) {
        }
    }
}
