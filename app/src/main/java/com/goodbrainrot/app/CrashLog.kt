package com.goodbrainrot.app

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Rapport de plantage LOCAL (aucun réseau) : la stack trace du dernier crash est écrite dans
 * filesDir/last_crash.txt et affichée dans l'espace parents (bouton Partager pour me l'envoyer).
 */
object CrashLog {
    private const val FILE = "last_crash.txt"

    fun install(context: Context) {
        val app = context.applicationContext
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                val sw = StringWriter(); e.printStackTrace(PrintWriter(sw))
                val stamp = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.FRANCE).format(Date())
                val txt = "GoodBrainRot v${BuildConfig.VERSION_NAME} — $stamp\nThread: ${t.name}\n\n$sw"
                File(app.filesDir, FILE).writeText(txt.take(20_000))
            } catch (_: Exception) { }
            prev?.uncaughtException(t, e)
        }
    }

    fun read(context: Context): String? = try {
        val f = File(context.filesDir, FILE)
        if (f.exists()) f.readText() else null
    } catch (_: Exception) { null }

    fun clear(context: Context) { try { File(context.filesDir, FILE).delete() } catch (_: Exception) { } }
}
