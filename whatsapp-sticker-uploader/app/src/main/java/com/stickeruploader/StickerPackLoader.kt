package com.stickeruploader

import android.os.Environment
import com.stickeruploader.models.Sticker
import com.stickeruploader.models.StickerPack
import java.io.File
import java.io.RandomAccessFile

object StickerPackLoader {

    const val STICKERS_PER_PACK = 30
    private const val MAX_STATIC_SIZE_BYTES   = 100 * 1024L  // 100 KB (sticker statici)
    private const val MAX_ANIMATED_SIZE_BYTES = 500 * 1024L  // 500 KB (sticker animati)

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
            if (isAnimatedWebP(file)) {
                if (size <= MAX_ANIMATED_SIZE_BYTES) animatedFiles.add(file)
            } else {
                if (size <= MAX_STATIC_SIZE_BYTES) staticFiles.add(file)
            }
        }

        val packs = mutableListOf<StickerPack>()

        // Pack statici: "New Stiker 1", "New Stiker 2", ...
        staticFiles.chunked(STICKERS_PER_PACK).forEachIndexed { index, files ->
            if (files.size < 3) return@forEachIndexed
            val num = index + 1
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

        // Pack animati: "New Stiker Anim 1", "New Stiker Anim 2", ...
        animatedFiles.chunked(STICKERS_PER_PACK).forEachIndexed { index, files ->
            if (files.size < 3) return@forEachIndexed
            val num = index + 1
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

    fun isAnimatedWebP(file: File): Boolean {
        if (file.length() < 20) return false
        return try {
            RandomAccessFile(file, "r").use { raf ->
                val header = ByteArray(50)
                raf.read(header)
                for (i in 0 until header.size - 3) {
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
