package com.stickeruploader

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Scrive log sia su logcat che su file leggibile dall'utente.
 * File salvato in: /storage/emulated/0/Android/data/com.stickeruploader/files/sticker_log.txt
 * (accessibile da qualsiasi file manager)
 */
object AppLogger {

    private const val TAG = "StickerApp"
    private const val LOG_FILE_NAME = "sticker_log.txt"
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())

    private var logFile: File? = null

    fun init(context: Context) {
        // Usa externalFilesDir così il file è leggibile da file manager
        val dir = context.getExternalFilesDir(null) ?: context.filesDir
        logFile = File(dir, LOG_FILE_NAME)
        // Cancella log precedente all'avvio
        logFile?.delete()
        logFile?.createNewFile()

        val line = buildString {
            append("=".repeat(60)).append("\n")
            append("STICKER UPLOADER - LOG AVVIATO\n")
            append("Data: ${dateFormat.format(Date())}\n")
            append("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n")
            append("Dispositivo: ${Build.MANUFACTURER} ${Build.MODEL}\n")
            append("=".repeat(60)).append("\n")
        }
        writeToFile(line)
        Log.i(TAG, "Logger inizializzato: ${logFile?.absolutePath}")
    }

    fun getLogFilePath(): String = logFile?.absolutePath ?: "non disponibile"

    fun i(tag: String, msg: String) {
        Log.i(tag, msg)
        writeToFile("[I][$tag] $msg")
    }

    fun d(tag: String, msg: String) {
        Log.d(tag, msg)
        writeToFile("[D][$tag] $msg")
    }

    fun w(tag: String, msg: String) {
        Log.w(tag, msg)
        writeToFile("[W][$tag] $msg")
    }

    fun e(tag: String, msg: String, throwable: Throwable? = null) {
        Log.e(tag, msg, throwable)
        val line = buildString {
            append("[E][$tag] $msg")
            if (throwable != null) {
                append("\n  EXCEPTION: ${throwable.javaClass.simpleName}: ${throwable.message}")
                throwable.stackTrace.take(5).forEach { append("\n    at $it") }
            }
        }
        writeToFile(line)
    }

    fun separator(title: String = "") {
        val line = if (title.isEmpty()) "-".repeat(40) else "--- $title ---"
        writeToFile(line)
        Log.d(TAG, line)
    }

    private fun writeToFile(msg: String) {
        val file = logFile ?: return
        try {
            FileWriter(file, true).use { fw ->
                PrintWriter(fw).use { pw ->
                    val ts = dateFormat.format(Date())
                    pw.println("$ts $msg")
                }
            }
        } catch (_: Exception) { }
    }
}
