package com.goodbrainrot.app

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.ContextWrapper
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

/**
 * Plein écran immersif : les barres système ne reviennent que par swipe, temporairement.
 * L'app est toujours edge-to-edge ; hors baby mode, les barres sont visibles et le
 * contenu s'en écarte via safeDrawingPadding() côté Compose.
 */
/** Forçage immersif ponctuel (ex. plein écran du shmup) : prioritaire sur le mode bébé. */
var forceImmersive = false

fun Activity.applyImmersive(enabled: Boolean) {
    WindowCompat.setDecorFitsSystemWindows(window, false)
    val controller = WindowInsetsControllerCompat(window, window.decorView)
    if (enabled) {
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    } else {
        controller.show(WindowInsetsCompat.Type.systemBars())
    }
}

fun Activity.isScreenPinned(): Boolean {
    val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    return am.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE
}

/**
 * Baby mode = plein écran + épinglage d'écran Android (startLockTask).
 * Seule sortie : le geste système « swiper du bas vers le haut et maintenir ».
 */
fun Activity.applyBabyMode(enabled: Boolean) {
    applyImmersive(enabled)
    try {
        if (enabled && !isScreenPinned()) {
            startLockTask()
        } else if (!enabled && isScreenPinned()) {
            stopLockTask()
        }
    } catch (_: Exception) {
        // L'épinglage peut être refusé/indisponible : l'app reste utilisable.
    }
}
