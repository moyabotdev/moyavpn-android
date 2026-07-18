package com.moyavpn.app.util

import android.content.Context
import android.content.Intent
import android.os.Build
import java.io.File

/**
 * Diagnose-Log fuer den „Log teilen“-Knopf in den Einstellungen.
 *
 * Sammelt zwei Quellen und oeffnet den System-Teilen-Dialog (als Text):
 *  1. xrays eigenes Fehlerlog (filesDir/xray.log, von [XrayConfigBuilder] auf debug),
 *  2. den App-Logcat (nur Eintraege des eigenen Prozesses — Android laesst Apps seit
 *     4.1 ohnehin nur ihre eigenen Zeilen lesen).
 *
 * Bewusst als EXTRA_TEXT statt Datei-Anhang: kein FileProvider noetig, der Nutzer kann
 * den Text direkt in den Chat einfuegen. Auf ~64 KB gedeckelt (Intent-Limit).
 */
object DiagLog {

    // Dateiname aus XrayConfigBuilder.LOG_FILE (direct-Flavor) — hier hart, weil main
    // flavor-neutral bleiben muss.
    private const val XRAY_LOG = "xray.log"
    private const val MAX_CHARS = 64_000

    fun share(context: Context) {
        val sb = StringBuilder()
        sb.append("== MoyaVPN Diagnose ==\n")
        sb.append("Geraet: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE}\n\n")

        // 1) xray-Log (das Entscheidende bei Reality-Problemen)
        sb.append("--- xray.log ---\n")
        val xrayLog = File(context.filesDir, XRAY_LOG)
        if (xrayLog.exists()) {
            sb.append(tail(runCatching { xrayLog.readText() }.getOrDefault(""), 600))
        } else {
            sb.append("(nicht vorhanden — XRay-Verbindung noch nicht gestartet)\n")
        }

        // 2) App-Logcat
        sb.append("\n--- logcat ---\n")
        sb.append(tail(readLogcat(), 400))

        var text = sb.toString()
        if (text.length > MAX_CHARS) text = text.substring(text.length - MAX_CHARS)

        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "MoyaVPN Diagnose-Log")
            putExtra(Intent.EXTRA_TEXT, text)
        }
        val chooser = Intent.createChooser(send, "Log teilen").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    private fun readLogcat(): String = try {
        val p = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-v", "time"))
        p.inputStream.bufferedReader().use { it.readText() }
    } catch (e: Exception) {
        "logcat nicht lesbar: ${e.message}"
    }

    /** Letzte [lines] Zeilen (das Interessante steht am Ende). */
    private fun tail(s: String, lines: Int): String {
        val all = s.split('\n')
        return if (all.size <= lines) s else all.subList(all.size - lines, all.size).joinToString("\n")
    }
}
