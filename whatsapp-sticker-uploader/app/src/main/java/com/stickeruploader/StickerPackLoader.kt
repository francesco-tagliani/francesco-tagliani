package com.stickeruploader

import android.os.Environment
import com.stickeruploader.models.Sticker
import com.stickeruploader.models.StickerPack
import java.io.File

object StickerPackLoader {

    const val STICKERS_PER_PACK = 30

    /**
     * Percorso della cartella WhatsApp Stickers nella memoria interna del dispositivo.
     * Su Android moderni: /storage/emulated/0/Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Stickers
     */
    val STICKER_DIR: File by lazy {
        File(
            Environment.getExternalStorageDirectory(),
            "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Stickers"
        )
    }

    /**
     * Carica tutti i file WebP dalla cartella sticker e li raggruppa in pack da 30.
     * Ritorna la lista di StickerPack.
     */
    fun loadAllPacks(): List<StickerPack> {
        if (!STICKER_DIR.exists() || !STICKER_DIR.isDirectory) {
            return emptyList()
        }

        // Prendi tutti i file .webp ordinati per nome
        val stickerFiles = STICKER_DIR.listFiles { file ->
            file.isFile && file.name.lowercase().endsWith(".webp")
        }?.sortedBy { it.name } ?: emptyList()

        if (stickerFiles.isEmpty()) return emptyList()

        // Raggruppa in chunk da STICKERS_PER_PACK
        val chunks = stickerFiles.chunked(STICKERS_PER_PACK)

        return chunks.mapIndexed { index, files ->
            val packNumber = index + 1
            val stickers = files.map { file ->
                Sticker(
                    imageFileName = file.name,
                    emojis = listOf("😀")
                )
            }
            StickerPack(
                identifier = "my_sticker_pack_%03d".format(packNumber),
                name = "I miei Sticker - Pack $packNumber",
                publisher = "Il mio dispositivo",
                trayImageFile = files.first().name,
                stickers = stickers
            )
        }
    }

    /**
     * Ritorna il File fisico per uno sticker dato il nome del file.
     */
    fun getStickerFile(fileName: String): File {
        return File(STICKER_DIR, fileName)
    }
}
