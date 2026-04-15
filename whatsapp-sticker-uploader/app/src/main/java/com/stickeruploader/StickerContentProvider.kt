package com.stickeruploader

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import com.stickeruploader.models.StickerPack
import java.io.FileNotFoundException

/**
 * ContentProvider pulito e semplice per WhatsApp sticker.
 * Serve file da filesDir dell'app (cartella privata, sempre accessibile).
 *
 * Log: scrive ogni operazione per il debug
 */
class StickerContentProvider : ContentProvider() {

    companion object {
        const val AUTHORITY = "com.stickeruploader.stickercontentprovider"
        private const val TAG = "StickerProvider"

        private const val METADATA = 1
        private const val METADATA_SINGLE = 2
        private const val STICKERS = 3
        private const val STICKERS_ASSET = 4

        private val URI_MATCHER = UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI(AUTHORITY, "metadata", METADATA)
            addURI(AUTHORITY, "metadata/*", METADATA_SINGLE)
            addURI(AUTHORITY, "stickers/*", STICKERS)
            addURI(AUTHORITY, "stickers_asset/*/*", STICKERS_ASSET)
        }

        private val METADATA_COLUMNS = arrayOf(
            "sticker_pack_identifier",
            "sticker_pack_name",
            "sticker_pack_publisher",
            "sticker_pack_icon",
            "android_play_store_link",
            "ios_app_download_link",
            "sticker_pack_publisher_email",
            "sticker_pack_publisher_website",
            "sticker_pack_privacy_policy_website",
            "sticker_pack_license_agreement_website",
            "image_data_version",
            "whatsapp_will_not_cache_stickers",
            "animated_sticker_pack"
        )

        private val STICKER_COLUMNS = arrayOf(
            "sticker_file_name",
            "sticker_emoji",
            "sticker_accessibility_text"
        )
    }

    private val packsCache: List<StickerPack> by lazy {
        Log.d(TAG, "Caricando pack dalla cache lazy")
        val packs = StickerPackLoader.loadAllPacks()
        Log.d(TAG, "Cache caricata: ${packs.size} pack")
        packs
    }

    override fun onCreate(): Boolean {
        Log.d(TAG, "onCreate() chiamato")
        return true
    }

    override fun getType(uri: Uri): String? {
        val type = when (URI_MATCHER.match(uri)) {
            METADATA, METADATA_SINGLE -> "vnd.android.cursor.dir/vnd.$AUTHORITY.metadata"
            STICKERS -> "vnd.android.cursor.dir/vnd.$AUTHORITY.stickers"
            STICKERS_ASSET -> "image/webp"
            else -> null
        }
        Log.d(TAG, "getType($uri) = $type")
        return type
    }

    override fun query(uri: Uri, projection: Array<String>?, selection: String?, selectionArgs: Array<String>?, sortOrder: String?): Cursor? {
        Log.d(TAG, "query($uri)")
        return when (URI_MATCHER.match(uri)) {
            METADATA -> {
                Log.d(TAG, "  -> metadata (tutti i pack)")
                getAllPacksMetadata()
            }
            METADATA_SINGLE -> {
                val packId = uri.lastPathSegment
                Log.d(TAG, "  -> metadata singolo: $packId")
                getSinglePackMetadata(packId ?: "")
            }
            STICKERS -> {
                val packId = uri.lastPathSegment
                Log.d(TAG, "  -> stickers per pack: $packId")
                getStickersForPack(packId ?: "")
            }
            else -> {
                Log.w(TAG, "  -> URI non riconosciuta!")
                null
            }
        }
    }

    /**
     * openAssetFile: metodo usato da WhatsApp per aprire i file sticker.
     * Serve i file dalla directory privata dell'app (filesDir).
     */
    override fun openAssetFile(uri: Uri, mode: String): AssetFileDescriptor? {
        Log.d(TAG, "openAssetFile($uri)")

        val segments = uri.pathSegments
        if (segments.size != 3) {
            Log.w(TAG, "  -> URI malformata: ${segments.size} segmenti invece di 3")
            return null
        }

        val packId = segments[1]
        val fileName = segments[2]
        Log.d(TAG, "  -> packId=$packId, fileName=$fileName")

        val ctx = context
        if (ctx == null) {
            Log.e(TAG, "  -> Context è null!")
            return null
        }

        // Cerca il pack
        val pack = packsCache.find { it.identifier == packId }
        if (pack == null) {
            Log.w(TAG, "  -> Pack non trovato: $packId")
            return null
        }

        // Decide se è tray image o sticker
        val isTrayImage = pack.trayImageFile == fileName
        Log.d(TAG, "  -> isTrayImage=$isTrayImage")

        // Costruisce il path del file nella directory privata
        val file = if (isTrayImage) {
            java.io.File(ctx.filesDir, "tray_images/${packId}_tray.webp")
        } else {
            java.io.File(ctx.filesDir, "stickers/$fileName")
        }

        Log.d(TAG, "  -> cercando file: ${file.absolutePath}")
        Log.d(TAG, "  -> esiste=${file.exists()}, size=${file.length()}")

        if (!file.exists() || file.length() == 0L) {
            Log.w(TAG, "  -> File non trovato o vuoto!")
            return null
        }

        return try {
            val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            Log.d(TAG, "  -> File aperto con successo, size=${file.length()}")
            AssetFileDescriptor(pfd, 0, AssetFileDescriptor.UNKNOWN_LENGTH)
        } catch (e: FileNotFoundException) {
            Log.e(TAG, "  -> FileNotFoundException: ${e.message}")
            null
        }
    }

    private fun getAllPacksMetadata(): Cursor {
        val cursor = MatrixCursor(METADATA_COLUMNS)
        Log.d(TAG, "getAllPacksMetadata: ${packsCache.size} pack")
        for (pack in packsCache) {
            cursor.addRow(packToRow(pack))
        }
        return cursor
    }

    private fun getSinglePackMetadata(packId: String): Cursor {
        val cursor = MatrixCursor(METADATA_COLUMNS)
        val pack = packsCache.find { it.identifier == packId }
        Log.d(TAG, "getSinglePackMetadata($packId): ${if (pack != null) "trovato" else "non trovato"}")
        if (pack != null) {
            cursor.addRow(packToRow(pack))
        }
        return cursor
    }

    private fun getStickersForPack(packId: String): Cursor {
        val cursor = MatrixCursor(STICKER_COLUMNS)
        val pack = packsCache.find { it.identifier == packId }
        Log.d(TAG, "getStickersForPack($packId): ${pack?.stickers?.size ?: 0} sticker")
        if (pack != null) {
            for (sticker in pack.stickers) {
                cursor.addRow(arrayOf(
                    sticker.imageFileName,
                    sticker.emojis.joinToString(","),
                    ""
                ))
            }
        }
        return cursor
    }

    private fun packToRow(pack: StickerPack): Array<Any?> {
        return arrayOf(
            pack.identifier,
            pack.name,
            pack.publisher,
            pack.trayImageFile,
            "",
            "",
            "",
            "",
            "",
            "",
            "1",
            0,
            0
        )
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?): Int = 0
}
