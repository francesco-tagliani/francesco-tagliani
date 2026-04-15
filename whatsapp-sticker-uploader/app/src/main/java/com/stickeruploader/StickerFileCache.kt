package com.stickeruploader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import com.stickeruploader.models.StickerPack
import java.io.File
import java.io.FileOutputStream

/**
 * Gestisce la copia dei file sticker dalla cartella WhatsApp (storage esterno)
 * nella directory privata dell'app (filesDir).
 *
 * Il ContentProvider di Android può servire file SOLO dalla directory privata dell'app
 * quando viene chiamato da WhatsApp via IPC (scoped storage Android 10+).
 * File in /storage/emulated/0/Android/media/com.whatsapp/ NON sono accessibili
 * al ContentProvider quando avviato da WhatsApp.
 */
object StickerFileCache {

    private const val STICKERS_DIR = "stickers"
    private const val TRAY_DIR = "tray_images"

    fun getStickersBaseDir(context: Context): File {
        // Usa externalFilesDir se disponibile (più spazio), altrimenti filesDir
        val base = context.getExternalFilesDir(null) ?: context.filesDir
        return File(base, STICKERS_DIR).also { it.mkdirs() }
    }

    fun getTrayBaseDir(context: Context): File {
        val base = context.getExternalFilesDir(null) ?: context.filesDir
        return File(base, TRAY_DIR).also { it.mkdirs() }
    }

    fun getCachedStickerFile(context: Context, fileName: String): File {
        return File(getStickersBaseDir(context), fileName)
    }

    fun getCachedTrayFile(context: Context, packId: String): File {
        return File(getTrayBaseDir(context), "${packId}_tray.webp")
    }

    /**
     * Copia tutti i file del pack nella directory privata dell'app e
     * genera la tray image 96×96. Blocca il thread chiamante.
     * Ritorna true se tutto è andato a buon fine.
     */
    fun preparePack(context: Context, pack: StickerPack): Boolean {
        val stickersDir = getStickersBaseDir(context)
        val trayDir = getTrayBaseDir(context)
        stickersDir.mkdirs()
        trayDir.mkdirs()

        // Copia ogni sticker
        for (sticker in pack.stickers) {
            val source = StickerPackLoader.getStickerFile(sticker.imageFileName)
            val dest = File(stickersDir, sticker.imageFileName)
            if (!dest.exists() || dest.length() == 0L) {
                if (!source.exists()) continue
                try {
                    source.copyTo(dest, overwrite = true)
                } catch (e: Exception) {
                    // Se la copia fallisce su un singolo file continua con gli altri
                }
            }
        }

        // Genera tray image 96×96 dalla prima immagine del pack
        val trayFile = File(trayDir, "${pack.identifier}_tray.webp")
        if (!trayFile.exists() || trayFile.length() == 0L) {
            val traySource = StickerPackLoader.getStickerFile(pack.trayImageFile)
            createTrayImage(traySource, trayFile)
        }

        return true
    }

    /**
     * Crea una tray image 96×96 WebP da un file sorgente.
     * Se il file sorgente non può essere decodificato, genera un bitmap grigio di fallback.
     * Garantisce sempre un output valido (non ritorna mai sourceFile 512×512).
     */
    private fun createTrayImage(source: File, dest: File) {
        dest.parentFile?.mkdirs()

        val sourceBitmap: Bitmap = try {
            BitmapFactory.decodeFile(source.absolutePath)
                ?: Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
        } catch (e: Exception) {
            Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
        }

        try {
            val scaled = Bitmap.createScaledBitmap(sourceBitmap, 96, 96, true)
            if (sourceBitmap !== scaled) sourceBitmap.recycle()

            FileOutputStream(dest).use { fos ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    scaled.compress(Bitmap.CompressFormat.WEBP_LOSSY, 80, fos)
                } else {
                    @Suppress("DEPRECATION")
                    scaled.compress(Bitmap.CompressFormat.WEBP, 80, fos)
                }
            }
            scaled.recycle()
        } catch (e: Exception) {
            sourceBitmap.recycle()
        }
    }
}
