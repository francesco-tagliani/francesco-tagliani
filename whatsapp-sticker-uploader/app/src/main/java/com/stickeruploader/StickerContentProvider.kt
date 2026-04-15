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
 * ContentProvider per sticker WhatsApp.
 * Basato sul codice ufficiale: https://github.com/WhatsApp/stickers
 *
 * Serve file da filesDir (directory privata app), unico storage accessibile
 * al ContentProvider quando chiamato da WhatsApp via IPC cross-process.
 */
class StickerContentProvider : ContentProvider() {

    companion object {
        private const val TAG = "StickerContentProvider"
        const val AUTHORITY = "com.stickeruploader.stickercontentprovider"

        // URI paths (uguali al codice ufficiale WhatsApp)
        private const val METADATA = "metadata"
        private const val STICKERS = "stickers"
        private const val STICKERS_ASSET = "stickers_asset"

        private const val METADATA_CODE = 1
        private const val METADATA_CODE_FOR_SINGLE_PACK = 2
        private const val STICKERS_CODE = 3
        private const val STICKERS_ASSET_CODE = 4
        private const val STICKER_PACK_TRAY_ICON_CODE = 5

        // Nomi colonne ESATTI richiesti da WhatsApp (dal codice ufficiale)
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

        private val URI_MATCHER = UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI(AUTHORITY, METADATA, METADATA_CODE)
            addURI(AUTHORITY, "$METADATA/*", METADATA_CODE_FOR_SINGLE_PACK)
            addURI(AUTHORITY, "$STICKERS/*", STICKERS_CODE)
            addURI(AUTHORITY, "$STICKERS_ASSET/*/*", STICKERS_ASSET_CODE)
        }
    }

    private val packsCache: List<StickerPack> by lazy {
        Log.d(TAG, "Caricamento pack in cache lazy")
        val packs = StickerPackLoader.loadAllPacks()
        Log.d(TAG, "Cache: ${packs.size} pack")

        // Registra URI per ogni singolo sticker (come fa il codice ufficiale WhatsApp)
        for (pack in packs) {
            URI_MATCHER.addURI(AUTHORITY, "$STICKERS_ASSET/${pack.identifier}/${pack.trayImageFile}", STICKER_PACK_TRAY_ICON_CODE)
            for (sticker in pack.stickers) {
                URI_MATCHER.addURI(AUTHORITY, "$STICKERS_ASSET/${pack.identifier}/${sticker.imageFileName}", STICKERS_ASSET_CODE)
            }
        }
        packs
    }

    override fun onCreate(): Boolean {
        Log.d(TAG, "onCreate()")
        return true
    }

    override fun getType(uri: Uri): String? {
        return when (URI_MATCHER.match(uri)) {
            METADATA_CODE -> "vnd.android.cursor.dir/vnd.$AUTHORITY.$METADATA"
            METADATA_CODE_FOR_SINGLE_PACK -> "vnd.android.cursor.item/vnd.$AUTHORITY.$METADATA"
            STICKERS_CODE -> "vnd.android.cursor.dir/vnd.$AUTHORITY.$STICKERS"
            STICKERS_ASSET_CODE -> "image/webp"
            STICKER_PACK_TRAY_ICON_CODE -> "image/webp"
            else -> null
        }
    }

    override fun query(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?
    ): Cursor? {
        Log.d(TAG, "query($uri)")
        return when (URI_MATCHER.match(uri)) {
            METADATA_CODE -> {
                Log.d(TAG, "  -> metadata tutti i pack (${packsCache.size})")
                getMetadataCursor(packsCache, uri)
            }
            METADATA_CODE_FOR_SINGLE_PACK -> {
                val packId = uri.lastPathSegment ?: return null
                Log.d(TAG, "  -> metadata singolo: $packId")
                getMetadataCursor(packsCache.filter { it.identifier == packId }, uri)
            }
            STICKERS_CODE -> {
                val packId = uri.lastPathSegment ?: return null
                Log.d(TAG, "  -> stickers per: $packId")
                getStickersCursor(packId, uri)
            }
            else -> {
                Log.w(TAG, "  -> URI non riconosciuta: $uri")
                null
            }
        }
    }

    override fun openAssetFile(uri: Uri, mode: String): AssetFileDescriptor? {
        Log.d(TAG, "openAssetFile($uri)")

        val matchCode = URI_MATCHER.match(uri)
        if (matchCode != STICKERS_ASSET_CODE && matchCode != STICKER_PACK_TRAY_ICON_CODE) {
            Log.w(TAG, "  -> match non valido: $matchCode")
            return null
        }

        val segments = uri.pathSegments
        if (segments.size < 3) {
            Log.w(TAG, "  -> URI malformata: solo ${segments.size} segmenti")
            return null
        }

        val packId = segments[1]
        val fileName = segments[2]
        Log.d(TAG, "  -> packId=$packId, fileName=$fileName")

        val ctx = context ?: run {
            Log.e(TAG, "  -> Context null!")
            return null
        }

        val pack = packsCache.find { it.identifier == packId } ?: run {
            Log.w(TAG, "  -> Pack non trovato: $packId")
            return null
        }

        // Sceglie il file giusto: tray image o sticker
        val file = if (pack.trayImageFile == fileName) {
            StickerFileCache.getCachedTrayFile(ctx, packId)
        } else {
            StickerFileCache.getCachedStickerFile(ctx, fileName)
        }

        Log.d(TAG, "  -> cercando: ${file.absolutePath}")
        Log.d(TAG, "  -> esiste=${file.exists()}, size=${file.length()}")

        if (!file.exists() || file.length() == 0L) {
            Log.w(TAG, "  -> File non trovato o vuoto!")
            return null
        }

        return try {
            val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            Log.d(TAG, "  -> Aperto con successo")
            AssetFileDescriptor(pfd, 0, AssetFileDescriptor.UNKNOWN_LENGTH)
        } catch (e: FileNotFoundException) {
            Log.e(TAG, "  -> FileNotFoundException: ${e.message}")
            null
        }
    }

    private fun getMetadataCursor(packs: List<StickerPack>, uri: Uri): Cursor {
        val cursor = MatrixCursor(METADATA_COLUMNS)
        for (pack in packs) {
            cursor.addRow(arrayOf(
                pack.identifier,
                pack.name,
                pack.publisher,
                pack.trayImageFile,
                "",   // android_play_store_link
                "",   // ios_app_download_link
                "",   // publisher_email
                "",   // publisher_website
                "",   // privacy_policy_website
                "",   // license_agreement_website
                "1",  // image_data_version
                0,    // whatsapp_will_not_cache_stickers
                0     // animated_sticker_pack
            ))
        }
        context?.let { cursor.setNotificationUri(it.contentResolver, uri) }
        return cursor
    }

    private fun getStickersCursor(packId: String, uri: Uri): Cursor {
        val cursor = MatrixCursor(STICKER_COLUMNS)
        val pack = packsCache.find { it.identifier == packId }
        if (pack != null) {
            for (sticker in pack.stickers) {
                cursor.addRow(arrayOf(
                    sticker.imageFileName,
                    sticker.emojis.joinToString(","),
                    ""
                ))
            }
            Log.d(TAG, "getStickersCursor: ${pack.stickers.size} sticker per $packId")
        } else {
            Log.w(TAG, "getStickersCursor: pack $packId non trovato")
        }
        context?.let { cursor.setNotificationUri(it.contentResolver, uri) }
        return cursor
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?): Int = 0
}
