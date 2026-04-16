package com.stickeruploader

import android.graphics.BitmapFactory
import android.os.Environment
import com.stickeruploader.models.Sticker
import com.stickeruploader.models.StickerPack
import java.io.File
import java.io.RandomAccessFile

object StickerPackLoader {

    const val STICKERS_PER_PACK          = 30  // statici: 30 per pack
    private const val ANIMATED_PER_PACK  = 1   // animati: 1 per pack
    private const val MAX_STATIC_SIZE_BYTES   = 100 * 1024L  // 100 KB
    private const val MAX_ANIMATED_SIZE_BYTES = 500 * 1024L  // 500 KB

    val STICKER_DIR: File by lazy {
        File(
            Environment.getExternalStorageDirectory(),
            "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Stickers"
        )
    }

    fun loadAllPacks(): List<StickerPack> {
        if (!STICKER_DIR.exists() || !STICKER_DIR.isDirectory) return emptyList()

        val allFiles = STICKER_DIR.listFiles { file ->
            file.isFile && file.name.lowercase().endsWith(".webp")
        }?.sortedBy { it.name } ?: emptyList()

        val staticFiles   = mutableListOf<File>()
        val animatedFiles = mutableListOf<File>()

        for (file in allFiles) {
            val size = file.length()
            if (size <= 0L) continue

            val animated = isAnimatedWebP(file)

            if (animated) {
                if (size <= MAX_ANIMATED_SIZE_BYTES) animatedFiles.add(file)
            } else {
                if (size <= MAX_STATIC_SIZE_BYTES && is512x512(file)) staticFiles.add(file)
            }
        }

        val packs = mutableListOf<StickerPack>()

        staticFiles.chunked(STICKERS_PER_PACK).forEachIndexed { index, files ->
            if (files.size < 3) return@forEachIndexed
            val num    = index + 1
            val packId = "my_sticker_pack_%03d".format(num)
            packs.add(StickerPack(
                identifier    = packId,
                name          = "New Stiker $num",
                publisher     = "Il mio dispositivo",
                trayImageFile = "${packId}_tray.webp",
                stickers      = files.map { Sticker(it.name, listOf("😀")) },
                isAnimated    = false
            ))
        }

        animatedFiles.chunked(ANIMATED_PER_PACK).forEachIndexed { index, files ->
            if (files.isEmpty()) return@forEachIndexed
            val num    = index + 1
            val packId = "my_anim_pack_%03d".format(num)
            packs.add(StickerPack(
                identifier    = packId,
                name          = "New Stiker Anim $num",
                publisher     = "Il mio dispositivo",
                trayImageFile = "${packId}_tray.webp",
                stickers      = files.map { Sticker(it.name, listOf("😀")) },
                isAnimated    = true
            ))
        }

        return packs
    }

    fun getStickerFile(fileName: String): File = File(STICKER_DIR, fileName)

    /**
     * Controlla se il file è esattamente 512x512 pixel.
     * Usa inJustDecodeBounds per leggere solo l'header, senza caricare l'immagine in RAM.
     */
    private fun is512x512(file: File): Boolean {
        return try {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, opts)
            opts.outWidth == 512 && opts.outHeight == 512
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Rileva WebP animati controllando:
     * 1. Il flag animazione nel chunk VP8X (metodo preciso)
     * 2. La presenza del chunk ANIM (fallback)
     */
    fun isAnimatedWebP(file: File): Boolean {
        if (file.length() < 20) return false
        return try {
            RandomAccessFile(file, "r").use { raf ->
                val header = ByteArray(64)
                val read = raf.read(header)
                if (read < 12) return false

                // Verifica firma RIFF...WEBP
                if (header[0] != 'R'.code.toByte() || header[1] != 'I'.code.toByte() ||
                    header[2] != 'F'.code.toByte() || header[3] != 'F'.code.toByte()) return false
                if (read < 12) return false
                if (header[8] != 'W'.code.toByte() || header[9] != 'E'.code.toByte() ||
                    header[10] != 'B'.code.toByte() || header[11] != 'P'.code.toByte()) return false

                // Metodo 1: VP8X con flag animazione (bit 1 del byte flags a offset 20)
                if (read >= 21 &&
                    header[12] == 'V'.code.toByte() && header[13] == 'P'.code.toByte() &&
                    header[14] == '8'.code.toByte() && header[15] == 'X'.code.toByte()) {
                    val flags = header[20].toInt() and 0xFF
                    if ((flags and 0x02) != 0) return true
                }

                // Metodo 2: cerca il chunk ANIM in tutto l'header letto
                for (i in 0..read - 4) {
                    if (header[i]   == 'A'.code.toByte() &&
                        header[i+1] == 'N'.code.toByte() &&
                        header[i+2] == 'I'.code.toByte() &&
                        header[i+3] == 'M'.code.toByte()) return true
                }
                false
            }
        } catch (e: Exception) { false }
    }
}
