package com.goodbrainrot.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/** Page de soutien du créateur (don libre). */
const val KOFI_URL = "https://ko-fi.com/hypedigger"

/**
 * Règle d'affichage du rappel de soutien : au 10e lancement, puis tous les 100 lancements,
 * jamais si l'utilisateur a coché « Ne plus me le demander ». Fonction pure (testée en JVM).
 */
fun shouldShowSupportPrompt(usageCount: Int, optedOut: Boolean): Boolean {
    if (optedOut) return false
    return usageCount == 10 || (usageCount >= 100 && usageCount % 100 == 0)
}

/** Ouvre la page Ko-fi dans le navigateur ; sans navigateur (ou écran épinglé), un toast donne l'adresse. */
fun openKofi(context: Context) {
    try {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(KOFI_URL)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (_: Exception) {
        Toast.makeText(context, context.getString(R.string.support_no_browser), Toast.LENGTH_LONG).show()
    }
}
