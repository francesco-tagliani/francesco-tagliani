package com.stickeruploader

import android.graphics.BitmapFactory
import android.os.Environment
import com.stickeruploader.models.Sticker
import com.stickeruploader.models.StickerPack
import java.io.File
import java.io.RandomAccessFile

object StickerPackLoader {

    const val STICKERS_PER_PACK        = 30
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

            val info = parseWebP(file)

            if (info.animated) {
                // Dimensioni lette direttamente dall'header VP8X (non si usa BitmapFactory)
                if (size <= MAX_ANIMATED_SIZE_BYTES && info.width == 512 && info.height == 512) {
                    animatedFiles.add(file)
                }
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

        animatedFiles.chunked(STICKERS_PER_PACK).forEachIndexed { index, files ->
            if (files.size < 3) return@forEachIndexed
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
     * Legge l'header WebP e restituisce: se è animato, larghezza e altezza del canvas.
     * Per i WebP animati con chunk VP8X, le dimensioni vengono lette direttamente dall'header
     * (BitmapFactory non restituisce dimensioni corrette per WebP animati).
     *
     * Struttura VP8X (offset dal byte 0 del file):
     *   [12-15] = "VP8X"
     *   [16-19] = chunk size (10 byte, little-endian)
     *   [20]    = flags (bit 1 = 0x02 → animazione)
     *   [21-23] = riservato
     *   [24-26] = Canvas Width  - 1 (24-bit little-endian)
     *   [27-29] = Canvas Height - 1 (24-bit little-endian)
     */
    private data class WebPInfo(val animated: Boolean, val width: Int, val height: Int)

    private fun parseWebP(file: File): WebPInfo {
        val unknown = WebPInfo(false, -1, -1)
        if (file.length() < 30) return unknown
        return try {
            RandomAccessFile(file, "r").use { raf ->
                val header = ByteArray(256)
                val read = raf.read(header)
                if (read < 12) return unknown

                // Firma RIFF...WEBP
                if (header[0] != 'R'.code.toByte() || header[1] != 'I'.code.toByte() ||
                    header[2] != 'F'.code.toByte() || header[3] != 'F'.code.toByte()) return unknown
                if (header[8] != 'W'.code.toByte() || header[9] != 'E'.code.toByte() ||
                    header[10] != 'B'.code.toByte() || header[11] != 'P'.code.toByte()) return unknown

                // Chunk VP8X presente: legge flag e dimensioni canvas
                if (read >= 30 &&
                    header[12] == 'V'.code.toByte() && header[13] == 'P'.code.toByte() &&
                    header[14] == '8'.code.toByte() && header[15] == 'X'.code.toByte()) {

                    val flags = header[20].toInt() and 0xFF
                    val animated = (flags and 0x02) != 0  // ANIMATION_FLAG per spec WebP / libwebp

                    // Canvas Width - 1 e Canvas Height - 1 (24-bit little-endian)
                    val w = ((header[24].toInt() and 0xFF))       or
                            ((header[25].toInt() and 0xFF) shl 8) or
                            ((header[26].toInt() and 0xFF) shl 16)
                    val h = ((header[27].toInt() and 0xFF))       or
                            ((header[28].toInt() and 0xFF) shl 8) or
                            ((header[29].toInt() and 0xFF) shl 16)

                    return WebPInfo(animated, w + 1, h + 1)
                }

                // Nessun chunk VP8X: cerca chunk ANIM come fallback (formato animato senza VP8X)
                for (i in 0..read - 4) {
                    if (header[i]   == 'A'.code.toByte() &&
                        header[i+1] == 'N'.code.toByte() &&
                        header[i+2] == 'I'.code.toByte() &&
                        header[i+3] == 'M'.code.toByte()) {
                        // ANIM trovato ma senza VP8X: dimensioni sconosciute, include se BitmapFactory riesce
                        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeFile(file.absolutePath, opts)
                        val bw = if (opts.outWidth > 0) opts.outWidth else 512
                        val bh = if (opts.outHeight > 0) opts.outHeight else 512
                        return WebPInfo(true, bw, bh)
                    }
                }

                unknown
            }
        } catch (e: Exception) { unknown }
    }

    fun isAnimatedWebP(file: File): Boolean = parseWebP(file).animated
}
