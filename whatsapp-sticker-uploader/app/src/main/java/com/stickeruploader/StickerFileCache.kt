package com.stickeruploader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Log
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

    // Il nome del tray file corrisponde a pack.trayImageFile = "${packId}_tray.webp"
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

        // FIX: usa il PRIMO sticker come sorgente per il tray (non pack.trayImageFile
        // che ora è un nome univoco come "my_sticker_pack_001_tray.webp")
        val trayFile = getCachedTrayFile(context, pack.identifier)
        if (!trayFile.exists() || trayFile.length() == 0L) {
            val traySourceFileName = pack.stickers.firstOrNull()?.imageFileName
            if (traySourceFileName != null) {
                val traySource = StickerPackLoader.getStickerFile(traySourceFileName)
                Log.d(TAG, "Generando tray 96x96 da: ${traySource.absolutePath}")
                createTrayImage(traySource, trayFile)
                Log.d(TAG, "Tray creata: ${trayFile.absolutePath} size=${trayFile.length()}")
            } else {
                Log.w(TAG, "Nessuno sticker nel pack, impossibile creare tray")
            }
        } else {
            Log.d(TAG, "Tray già presente: ${trayFile.absolutePath}")
        }

        if (failed > 0) {
            Log.w(TAG, "ATTENZIONE: $failed file non copiati - WhatsApp potrebbe rifiutare il pack")
        }
        return failed == 0
    }

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
            Log.e(TAG, "Errore creazione tray: ${e.message}")
        }
    }
}
