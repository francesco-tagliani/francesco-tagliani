package com.stickeruploader

import android.graphics.BitmapFactory
import android.os.Environment
import com.stickeruploader.models.Sticker
import com.stickeruploader.models.StickerPack
import java.io.File
import java.io.RandomAccessFile

object StickerPackLoader {

    const val STICKERS_PER_PACK        = 30
    const val MIN_STICKERS_PER_PACK    = 3
    private const val MAX_ANIMATED_SIZE_BYTES = 500 * 1024L

    // Pack correnti - aggiornati ad ogni loadAllPacks(), usati dal ContentProvider
    var currentPacks: List<StickerPack> = emptyList()
        private set

    // Statistiche ultima validazione
    var lastValidCount: Int = 0
        private set
    var lastInvalidCount: Int = 0
        private set

    val STICKER_DIR: File by lazy {
        File(
            Environment.getExternalStorageDirectory(),
            "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Stickers"
        )
    }

    fun loadAllPacks(stickersPerPack: Int = STICKERS_PER_PACK): List<StickerPack> {
        if (!STICKER_DIR.exists() || !STICKER_DIR.isDirectory) return emptyList()

        val allFiles = STICKER_DIR.listFiles { file ->
            file.isFile && file.name.lowercase().endsWith(".webp")
        }?.sortedBy { it.name } ?: emptyList()

        var validCount = 0
        var invalidCount = 0

        val animatedFiles = mutableListOf<File>()

        for (file in allFiles) {
            val size = file.length()
            if (size <= 0L) continue

            val info = parseWebP(file)

            if (info.animated) {
                // Valida ogni sticker individualmente secondo i requisiti WhatsApp:
                // - dimensioni 512x512
                // - dimensione ≤ 500KB
                // - loop count = 0 (loop infinito)
                val sizeOk = size <= MAX_ANIMATED_SIZE_BYTES
                val dimsOk = info.width == 512 && info.height == 512
                val loopOk = info.loopCount == 0

                if (sizeOk && dimsOk && loopOk) {
                    animatedFiles.add(file)
                    validCount++
                } else {
                    invalidCount++
                    AppLogger.w("StickerPackLoader",
                        "Sticker scartato: ${file.name} " +
                        "[size=${size/1024}KB ok=$sizeOk, " +
                        "${info.width}x${info.height} ok=$dimsOk, " +
                        "loop=${info.loopCount} ok=$loopOk]")
                }
            }
        }

        lastValidCount = validCount
        lastInvalidCount = invalidCount

        val packs = mutableListOf<StickerPack>()
        val size = stickersPerPack.coerceIn(MIN_STICKERS_PER_PACK, STICKERS_PER_PACK)

        animatedFiles.chunked(size).forEachIndexed { index, files ->
            if (files.size < MIN_STICKERS_PER_PACK) return@forEachIndexed
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

        currentPacks = packs
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
     * Legge l'header WebP e restituisce: se è animato, larghezza e altezza del canvas, loop count.
     *
     * Struttura (offset dal byte 0):
     *   [0-3]   = "RIFF"
     *   [8-11]  = "WEBP"
     *   [12-15] = "VP8X"  (se presente)
     *   [20]    = flags (bit 0x02 = animazione)
     *   [24-26] = Canvas Width  - 1 (24-bit LE)
     *   [27-29] = Canvas Height - 1 (24-bit LE)
     *   [30-33] = "ANIM"  (se animato)
     *   [38-41] = background color
     *   [42-43] = loop count (0 = infinito, richiesto da WhatsApp)
     */
    private data class WebPInfo(val animated: Boolean, val width: Int, val height: Int, val loopCount: Int = -1)

    private fun parseWebP(file: File): WebPInfo {
        val unknown = WebPInfo(false, -1, -1)
        if (file.length() < 30) return unknown
        return try {
            RandomAccessFile(file, "r").use { raf ->
                val header = ByteArray(256)
                val read = raf.read(header)

                if (read < 12) return@use unknown

                // Firma RIFF...WEBP
                if (header[0] != 'R'.code.toByte() || header[1] != 'I'.code.toByte() ||
                    header[2] != 'F'.code.toByte() || header[3] != 'F'.code.toByte()) return@use unknown
                if (header[8] != 'W'.code.toByte() || header[9] != 'E'.code.toByte() ||
                    header[10] != 'B'.code.toByte() || header[11] != 'P'.code.toByte()) return@use unknown

                // Chunk VP8X presente: legge flag, dimensioni canvas e loop count
                if (read >= 30 &&
                    header[12] == 'V'.code.toByte() && header[13] == 'P'.code.toByte() &&
                    header[14] == '8'.code.toByte() && header[15] == 'X'.code.toByte()) {

                    val flags = header[20].toInt() and 0xFF
                    val animated = (flags and 0x02) != 0

                    val w = (header[24].toInt() and 0xFF) or
                            ((header[25].toInt() and 0xFF) shl 8) or
                            ((header[26].toInt() and 0xFF) shl 16)
                    val h = (header[27].toInt() and 0xFF) or
                            ((header[28].toInt() and 0xFF) shl 8) or
                            ((header[29].toInt() and 0xFF) shl 16)

                    // Legge loop count dal chunk ANIM (offset 30, se abbastanza dati)
                    var loopCount = -1
                    if (animated && read >= 44 &&
                        header[30] == 'A'.code.toByte() && header[31] == 'N'.code.toByte() &&
                        header[32] == 'I'.code.toByte() && header[33] == 'M'.code.toByte()) {
                        loopCount = (header[42].toInt() and 0xFF) or
                                    ((header[43].toInt() and 0xFF) shl 8)
                    }

                    return@use WebPInfo(animated, w + 1, h + 1, loopCount)
                }

                // Nessun VP8X: cerca ANIM come fallback
                var animResult: WebPInfo = unknown
                for (i in 0..read - 4) {
                    if (header[i]   == 'A'.code.toByte() &&
                        header[i+1] == 'N'.code.toByte() &&
                        header[i+2] == 'I'.code.toByte() &&
                        header[i+3] == 'M'.code.toByte()) {
                        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeFile(file.absolutePath, opts)
                        val bw = if (opts.outWidth > 0) opts.outWidth else 512
                        val bh = if (opts.outHeight > 0) opts.outHeight else 512
                        animResult = WebPInfo(true, bw, bh, -1)
                        break
                    }
                }
                animResult
            }
        } catch (e: Exception) { unknown }
    }

    fun isAnimatedWebP(file: File): Boolean = parseWebP(file).animated
}
