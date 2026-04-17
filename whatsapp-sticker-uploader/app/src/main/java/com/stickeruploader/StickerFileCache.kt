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
        Log.d(TAG, "preparePack() per ${pack.identifier} - solo tray (sticker serviti direttamente)")

        val trayDir = getTrayBaseDir(context)
        trayDir.mkdirs()

        val trayFile = getCachedTrayFile(context, pack.identifier)
        if (!trayFile.exists() || trayFile.length() == 0L) {
            Log.d(TAG, "Generando tray: ${trayFile.absolutePath}")
            createTrayFromDrawable(context, trayFile)
            Log.d(TAG, "Tray creata: size=${trayFile.length()}")
        } else {
            Log.d(TAG, "Tray già presente: ${trayFile.absolutePath}")
        }

        return trayFile.exists() && trayFile.length() > 0L
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
