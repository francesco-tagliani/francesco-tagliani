package com.stickeruploader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.stickeruploader.models.StickerPack
import java.io.File
import java.io.FileOutputStream

object StickerFileCache {

    private const val TAG = "StickerFileCache"
    private const val STICKERS_DIR = "stickers"
    private const val TRAY_DIR = "tray_images"

    fun getStickersBaseDir(context: Context): File =
        File(context.filesDir, STICKERS_DIR).also { it.mkdirs() }

    fun getTrayBaseDir(context: Context): File =
        File(context.filesDir, TRAY_DIR).also { it.mkdirs() }

    fun getCachedStickerFile(context: Context, fileName: String): File =
        File(getStickersBaseDir(context), fileName)

    fun getCachedTrayFile(context: Context, packId: String): File =
        File(getTrayBaseDir(context), "${packId}_tray.webp")

    fun preparePack(context: Context, pack: StickerPack): Boolean {
        Log.d(TAG, "preparePack() per ${pack.identifier}, ${pack.stickers.size} sticker")

        val stickersDir = getStickersBaseDir(context)
        val trayDir = getTrayBaseDir(context)
        stickersDir.mkdirs()
        trayDir.mkdirs()

        var copied = 0
        var failed = 0
        for (sticker in pack.stickers) {
            val source = StickerPackLoader.getStickerFile(sticker.imageFileName)
            val dest = File(stickersDir, sticker.imageFileName)
            if (dest.exists() && dest.length() > 0L) continue
            if (!source.exists()) {
                Log.w(TAG, "Sorgente mancante: ${source.absolutePath}")
                failed++
                continue
            }
            try {
                source.copyTo(dest, overwrite = true)
                copied++
            } catch (e: Exception) {
                Log.e(TAG, "Errore copia ${sticker.imageFileName}: ${e.message}")
                failed++
            }
        }
        Log.d(TAG, "Copia sticker: $copied copiati, $failed falliti su ${pack.stickers.size}")

        // Usa l'icona solare embedded come tray per tutti i pack
        val trayFile = getCachedTrayFile(context, pack.identifier)
        if (!trayFile.exists() || trayFile.length() == 0L) {
            Log.d(TAG, "Generando tray con icona solare: ${trayFile.absolutePath}")
            createTrayFromDrawable(context, trayFile)
            Log.d(TAG, "Tray creata: size=${trayFile.length()}")
        } else {
            Log.d(TAG, "Tray già presente: ${trayFile.absolutePath}")
        }

        if (failed > 0) {
            Log.w(TAG, "ATTENZIONE: $failed file non copiati - WhatsApp potrebbe rifiutare il pack")
        }
        return failed == 0
    }

    private fun createTrayFromDrawable(context: Context, dest: File) {
        dest.parentFile?.mkdirs()
        try {
            val drawable = ContextCompat.getDrawable(context, R.drawable.ic_tray_sun) ?: run {
                Log.e(TAG, "Drawable ic_tray_sun non trovato")
                return
            }
            val bitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            // Sfondo bianco
            canvas.drawColor(android.graphics.Color.WHITE)
            drawable.setBounds(0, 0, 96, 96)
            drawable.draw(canvas)
            saveBitmapAsWebp(bitmap, dest)
            bitmap.recycle()
        } catch (e: Exception) {
            Log.e(TAG, "Errore creazione tray da drawable: ${e.message}")
        }
    }

    private fun saveBitmapAsWebp(bitmap: Bitmap, dest: File) {
        FileOutputStream(dest).use { fos ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSLESS, 100, fos)
            } else {
                @Suppress("DEPRECATION")
                bitmap.compress(Bitmap.CompressFormat.WEBP, 100, fos)
            }
        }
    }
}
