package com.stickeruploader

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.stickeruploader.models.StickerPack
import java.io.FileNotFoundException

/**
 * ContentProvider richiesto dall'API ufficiale WhatsApp per la gestione degli sticker pack.
 *
 * URI supportati:
 *   content://AUTHORITY/metadata               → lista di tutti i pack
 *   content://AUTHORITY/metadata/{pack_id}     → metadati di un singolo pack
 *   content://AUTHORITY/stickers/{pack_id}     → sticker di un pack
 *   content://AUTHORITY/stickers_asset/{pack_id}/{filename} → file immagine sticker
 */
class StickerContentProvider : ContentProvider() {

    companion object {
        const val AUTHORITY = "com.stickeruploader.stickercontentprovider"

        // Codici URI matcher
        private const val METADATA = 1
        private const val METADATA_CODE_FOR_SINGLE = 2
        private const val STICKERS = 3
        private const val STICKERS_ASSET = 4

        private val URI_MATCHER = UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI(AUTHORITY, "metadata", METADATA)
            addURI(AUTHORITY, "metadata/*", METADATA_CODE_FOR_SINGLE)
            addURI(AUTHORITY, "stickers/*", STICKERS)
            addURI(AUTHORITY, "stickers_asset/*/*", STICKERS_ASSET)
        }

        // Colonne richieste da WhatsApp per i metadati del pack
        private val METADATA_COLUMNS = arrayOf(
            "sticker_pack_identifier",
            "sticker_pack_name",
            "sticker_pack_publisher",
            "sticker_pack_icon",
            "android_play_store_link",
            "ios_app_store_link",
            "sticker_pack_privacy_policy_website",
            "sticker_pack_license_agreement",
            "image_data_version",
            "avoid_cache",
            "animated_sticker_pack"
        )

        // Colonne richieste da WhatsApp per i singoli sticker
        private val STICKER_COLUMNS = arrayOf(
            "sticker_file_name",
            "sticker_emoji"
        )
    }

    // Cache dei pack caricati (caricati una volta sola al primo accesso)
    private val packsCache: List<StickerPack> by lazy {
        StickerPackLoader.loadAllPacks()
    }

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?
    ): Cursor? {
        return when (URI_MATCHER.match(uri)) {
            METADATA -> getAllPacksMetadata()
            METADATA_CODE_FOR_SINGLE -> {
                val packId = uri.lastPathSegment ?: return null
                getSinglePackMetadata(packId)
            }
            STICKERS -> {
                val packId = uri.lastPathSegment ?: return null
                getStickersForPack(packId)
            }
            else -> throw IllegalArgumentException("URI sconosciuto: $uri")
        }
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        return when (URI_MATCHER.match(uri)) {
            STICKERS_ASSET -> {
                // uri.pathSegments = ["stickers_asset", "pack_id", "filename.webp"]
                val segments = uri.pathSegments
                if (segments.size < 3) throw FileNotFoundException("URI non valido: $uri")
                val fileName = segments[2]
                val file = StickerPackLoader.getStickerFile(fileName)
                if (!file.exists()) throw FileNotFoundException("File non trovato: ${file.absolutePath}")
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            }
            else -> throw FileNotFoundException("URI non supportato: $uri")
        }
    }

    // --- Metodi interni ---

    private fun getAllPacksMetadata(): Cursor {
        val cursor = MatrixCursor(METADATA_COLUMNS)
        for (pack in packsCache) {
            cursor.addRow(packToRow(pack))
        }
        return cursor
    }

    private fun getSinglePackMetadata(packId: String): Cursor {
        val cursor = MatrixCursor(METADATA_COLUMNS)
        val pack = packsCache.find { it.identifier == packId }
        if (pack != null) {
            cursor.addRow(packToRow(pack))
        }
        return cursor
    }

    private fun getStickersForPack(packId: String): Cursor {
        val cursor = MatrixCursor(STICKER_COLUMNS)
        val pack = packsCache.find { it.identifier == packId } ?: return cursor
        for (sticker in pack.stickers) {
            cursor.addRow(arrayOf(
                sticker.imageFileName,
                sticker.emojis.joinToString(",")
            ))
        }
        return cursor
    }

    private fun packToRow(pack: StickerPack): Array<Any?> {
        return arrayOf(
            pack.identifier,
            pack.name,
            pack.publisher,
            pack.trayImageFile,
            "",   // android_play_store_link
            "",   // ios_app_store_link
            "",   // privacy_policy
            "",   // license
            "1",  // image_data_version
            0,    // avoid_cache
            0     // animated_sticker_pack
        )
    }

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?): Int = 0
}
