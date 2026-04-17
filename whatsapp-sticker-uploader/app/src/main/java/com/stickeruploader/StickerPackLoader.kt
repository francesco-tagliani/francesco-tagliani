package com.stickeruploader

import android.os.Environment
import com.stickeruploader.models.Sticker
import com.stickeruploader.models.StickerPack
import java.io.File
import java.io.RandomAccessFile

object StickerPackLoader {

    const val STICKERS_PER_PACK        = 30
    const val MIN_STICKERS_PER_PACK    = 3
    private const val MAX_ANIMATED_SIZE_BYTES  = 500 * 1024L
    private const val MAX_DURATION_MS          = 10_000   // WhatsApp spec: max 10s total
    private const val MIN_FRAME_DURATION_MS    = 8        // WhatsApp spec: min 8ms per frame

    var currentPacks: List<StickerPack> = emptyList()
        private set

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
            if (!info.animated) continue

            val sizeOk      = size <= MAX_ANIMATED_SIZE_BYTES
            val dimsOk      = info.width == 512 && info.height == 512
            val loopOk      = info.loopCount == 0
            // totalDurationMs == -1 → nessun ANMF trovato (file malformato) → scarta
            val durationOk  = info.totalDurationMs in 1..MAX_DURATION_MS
            // minFrameDurationMs == -1 → non calcolato (nessun ANMF); altrimenti ogni frame ≥ 8ms
            val frameMinOk  = info.minFrameDurationMs < 0 || info.minFrameDurationMs >= MIN_FRAME_DURATION_MS

            if (sizeOk && dimsOk && loopOk && durationOk && frameMinOk) {
                animatedFiles.add(file)
                validCount++
            } else {
                invalidCount++
                AppLogger.w("StickerPackLoader",
                    "Sticker scartato: ${file.name} " +
                    "[size=${size / 1024}KB ok=$sizeOk, " +
                    "${info.width}x${info.height} ok=$dimsOk, " +
                    "loop=${info.loopCount} ok=$loopOk, " +
                    "totDur=${info.totalDurationMs}ms ok=$durationOk, " +
                    "minFrame=${info.minFrameDurationMs}ms ok=$frameMinOk]")
            }
        }

        lastValidCount   = validCount
        lastInvalidCount = invalidCount

        val packs = mutableListOf<StickerPack>()
        val packSize = stickersPerPack.coerceIn(MIN_STICKERS_PER_PACK, STICKERS_PER_PACK)

        animatedFiles.chunked(packSize).forEachIndexed { index, files ->
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

    // ─────────────────────────────────────────────────────────────────────────
    // WebP binary parser: legge VP8X (animazione, dimensioni), ANIM (loop),
    // e somma le durate di tutti i chunk ANMF per il totale animazione.
    //
    // Struttura RIFF/WEBP:
    //   [0-3]   RIFF  [4-7] fileSize-8  [8-11] WEBP
    //   [12+]   chunks: [4 FourCC][4 size LE][payload]
    //
    // VP8X payload:  [0] flags (bit1=ANIM)  [4-6] width-1  [7-9] height-1
    // ANIM payload:  [0-3] bgColor  [4-5] loopCount
    // ANMF payload:  [0-5] frameXY  [6-11] frameDims  [12-14] duration ms  [15] flags
    // ─────────────────────────────────────────────────────────────────────────

    private data class WebPInfo(
        val animated: Boolean,
        val width: Int,
        val height: Int,
        val loopCount: Int = -1,
        val totalDurationMs: Int = -1,
        val minFrameDurationMs: Int = -1
    )

    private fun parseWebP(file: File): WebPInfo {
        val unknown  = WebPInfo(false, -1, -1)
        val fileSize = file.length()
        if (fileSize < 12) return unknown

        return try {
            RandomAccessFile(file, "r").use { raf ->
                // Verifica firma RIFF/WEBP
                val riff = ByteArray(12)
                if (raf.read(riff) < 12) return@use unknown
                if (riff[0] != 'R'.code.toByte() || riff[1] != 'I'.code.toByte() ||
                    riff[2] != 'F'.code.toByte() || riff[3] != 'F'.code.toByte()) return@use unknown
                if (riff[8] != 'W'.code.toByte() || riff[9] != 'E'.code.toByte() ||
                    riff[10] != 'B'.code.toByte() || riff[11] != 'P'.code.toByte()) return@use unknown

                var offset           = 12L
                var animated         = false
                var width            = -1
                var height           = -1
                var loopCount        = -1
                var totalDurationMs  = 0
                var minFrameDurationMs = Int.MAX_VALUE
                var anmfCount        = 0
                val hdr              = ByteArray(8)

                while (offset + 8 <= fileSize) {
                    raf.seek(offset)
                    if (raf.read(hdr) < 8) break

                    val fourCC    = String(hdr, 0, 4, Charsets.US_ASCII)
                    val chunkSize = le32(hdr, 4)
                    if (chunkSize < 0) break

                    when (fourCC) {
                        "VP8X" -> {
                            val p = ByteArray(10)
                            if (raf.read(p) >= 10) {
                                animated = (p[0].toInt() and 0xFF and 0x02) != 0
                                width    = le24(p, 4) + 1
                                height   = le24(p, 7) + 1
                            }
                        }
                        "ANIM" -> {
                            if (chunkSize >= 6) {
                                val p = ByteArray(6)
                                if (raf.read(p) >= 6) loopCount = le16(p, 4)
                            }
                        }
                        "ANMF" -> {
                            if (chunkSize >= 16) {
                                val p = ByteArray(16)
                                if (raf.read(p) >= 16) {
                                    val frameDur = le24(p, 12)
                                    totalDurationMs += frameDur
                                    if (frameDur < minFrameDurationMs) minFrameDurationMs = frameDur
                                    anmfCount++
                                }
                            }
                        }
                    }

                    val advance = 8L + chunkSize + (chunkSize and 1)
                    if (advance <= 8L) break   // protezione loop infinito
                    offset += advance
                }

                WebPInfo(
                    animated           = animated,
                    width              = width,
                    height             = height,
                    loopCount          = loopCount,
                    totalDurationMs    = if (anmfCount > 0) totalDurationMs else -1,
                    minFrameDurationMs = if (anmfCount > 0) minFrameDurationMs else -1
                )
            }
        } catch (e: Exception) { unknown }
    }

    private fun le32(b: ByteArray, o: Int): Int =
        (b[o].toInt() and 0xFF) or ((b[o+1].toInt() and 0xFF) shl 8) or
        ((b[o+2].toInt() and 0xFF) shl 16) or ((b[o+3].toInt() and 0xFF) shl 24)

    private fun le24(b: ByteArray, o: Int): Int =
        (b[o].toInt() and 0xFF) or ((b[o+1].toInt() and 0xFF) shl 8) or
        ((b[o+2].toInt() and 0xFF) shl 16)

    private fun le16(b: ByteArray, o: Int): Int =
        (b[o].toInt() and 0xFF) or ((b[o+1].toInt() and 0xFF) shl 8)

    fun isAnimatedWebP(file: File): Boolean = parseWebP(file).animated
}
