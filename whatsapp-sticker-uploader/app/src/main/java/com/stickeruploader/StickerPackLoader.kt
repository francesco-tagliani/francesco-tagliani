package com.stickeruploader

import android.os.Environment
import com.stickeruploader.models.Sticker
import com.stickeruploader.models.StickerPack
import java.io.File
import java.io.RandomAccessFile

object StickerPackLoader {

    const val STICKERS_PER_PACK = 30
    private const val MAX_STICKER_SIZE_BYTES = 100 * 1024L  // 100KB max per WhatsApp

    val STICKER_DIR: File by lazy {
        File(
            Environment.getExternalStorageDirectory(),
            "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Stickers"
        )
    }

    fun loadAllPacks(): List<StickerPack> {
        if (!STICKER_DIR.exists() || !STICKER_DIR.isDirectory) {
            return emptyList()
        }

        val allFiles = STICKER_DIR.listFiles { file ->
            file.isFile && file.name.toLowerCase().endsWith(".webp")
        }?.sortedBy { it.name } ?: emptyList()

        // Filtra file non validi per WhatsApp
        val validFiles = allFiles.filter { file ->
            val size = file.length()
            // File deve essere > 0 e <= 100KB
            size > 0L && size <= MAX_STICKER_SIZE_BYTES && !isAnimatedWebP(file)
        }

        if (validFiles.isEmpty()) return emptyList()

        val chunks = validFiles.chunked(STICKERS_PER_PACK)

        return chunks.mapIndexedNotNull { index, files ->
            // WhatsApp richiede minimo 3 sticker per pack
            if (files.size < 3) return@mapIndexedNotNull null

            val packNumber = index + 1
            val stickers = files.map { file ->
                Sticker(
                    imageFileName = file.name,
                    emojis = listOf("😀")
                )
            }
            StickerPack(
                identifier = "my_sticker_pack_%03d".format(packNumber),
                name = "New Stiker $packNumber",
                publisher = "Il mio dispositivo",
                trayImageFile = files.first().name,
                stickers = stickers
            )
        }
    }

    fun getStickerFile(fileName: String): File {
        return File(STICKER_DIR, fileName)
    }

    /**
     * Rileva se un file WebP è animato controllando l'header del file.
     * I WebP animati contengono il chunk "ANIM" nell'header.
     * WhatsApp rifiuta sticker animati se animated_sticker_pack=0.
     */
    private fun isAnimatedWebP(file: File): Boolean {
        if (file.length() < 20) return false
        return try {
            RandomAccessFile(file, "r").use { raf ->
                val header = ByteArray(50)
                raf.read(header)
                // Cerca "ANIM" nei primi 50 byte
                for (i in 0 until header.size - 3) {
                    if (header[i] == 65.toByte() &&  // 'A'
                        header[i+1] == 78.toByte() && // 'N'
                        header[i+2] == 73.toByte() && // 'I'
                        header[i+3] == 77.toByte()) { // 'M'
                        return true
                    }
                }
                false
            }
        } catch (e: Exception) {
            false
        }
    }
}
