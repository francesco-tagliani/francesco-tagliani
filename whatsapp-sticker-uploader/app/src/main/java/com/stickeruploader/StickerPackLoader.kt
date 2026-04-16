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
            file.isFile && file.name.lowercase().endsWith(".webp")
        }?.sortedBy { it.name } ?: emptyList()

        // Filtra file non validi per WhatsApp
        val validFiles = allFiles.filter { file ->
            val size = file.length()
            size > 0L && size <= MAX_STICKER_SIZE_BYTES && !isAnimatedWebP(file)
        }

        if (validFiles.isEmpty()) return emptyList()

        val chunks = validFiles.chunked(STICKERS_PER_PACK)

        return chunks.mapIndexedNotNull { index, files ->
            if (files.size < 3) return@mapIndexedNotNull null

            val packNumber = index + 1
            val packId = "my_sticker_pack_%03d".format(packNumber)
            val stickers = files.map { file ->
                Sticker(imageFileName = file.name, emojis = listOf("😀"))
            }
            StickerPack(
                identifier = packId,
                name = "New Stiker $packNumber",
                publisher = "Il mio dispositivo",
                // FIX: usa un nome UNIVOCO per il tray che non coincide con nessuno sticker
                trayImageFile = "${packId}_tray.webp",
                stickers = stickers
            )
        }
    }

    fun getStickerFile(fileName: String): File = File(STICKER_DIR, fileName)

    private fun isAnimatedWebP(file: File): Boolean {
        if (file.length() < 20) return false
        return try {
            RandomAccessFile(file, "r").use { raf ->
                val header = ByteArray(50)
                raf.read(header)
                for (i in 0 until header.size - 3) {
                    if (header[i] == 'A'.code.toByte() &&
                        header[i+1] == 'N'.code.toByte() &&
                        header[i+2] == 'I'.code.toByte() &&
                        header[i+3] == 'M'.code.toByte()) return true
                }
                false
            }
        } catch (e: Exception) { false }
    }
}
