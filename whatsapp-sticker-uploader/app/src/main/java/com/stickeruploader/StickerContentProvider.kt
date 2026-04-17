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

class StickerContentProvider : ContentProvider() {

    companion object {
        private const val TAG = "StickerContentProvider"
        const val AUTHORITY = "com.stickeruploader.stickercontentprovider"

        private const val METADATA = "metadata"
        private const val STICKERS = "stickers"
        private const val STICKERS_ASSET = "stickers_asset"

        private const val METADATA_CODE = 1
        private const val METADATA_CODE_FOR_SINGLE_PACK = 2
        private const val STICKERS_CODE = 3
        private const val STICKERS_ASSET_CODE = 4

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

    private val packsCache: List<StickerPack>
        get() {
            val packs = StickerPackLoader.currentPacks
            if (packs.isNotEmpty()) return packs
            Log.d(TAG, "currentPacks vuoto, ricarico")
            return StickerPackLoader.loadAllPacks()
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
                Log.w(TAG, "  -> URI non riconosciuta: $uri (code=${URI_MATCHER.match(uri)})")
                null
            }
        }
    }

    // FIX: override openFile() oltre a openAssetFile() - WhatsApp può chiamare entrambi
    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        Log.d(TAG, "openFile($uri)")
        return openAssetFile(uri, mode)?.parcelFileDescriptor
    }

    override fun openAssetFile(uri: Uri, mode: String): AssetFileDescriptor? {
        Log.d(TAG, "openAssetFile($uri)")

        if (URI_MATCHER.match(uri) != STICKERS_ASSET_CODE) {
            Log.w(TAG, "  -> URI non valida per asset: $uri")
            return null
        }

        val segments = uri.pathSegments
        if (segments.size < 3) {
            Log.w(TAG, "  -> URI malformata: ${segments.size} segmenti")
            return null
        }

        val packId = segments[1]
        val fileName = segments[2]
        Log.d(TAG, "  -> packId=$packId, fileName=$fileName")

        val ctx = context ?: return null

        val pack = packsCache.find { it.identifier == packId } ?: run {
            Log.w(TAG, "  -> Pack '$packId' non trovato in cache (${packsCache.size} pack)")
            return null
        }

        // FIX: trayImageFile è ora un nome univoco (es. "my_sticker_pack_001_tray.webp")
        // che non coincide mai con i nomi degli sticker (es. "IMG001.webp")
        // → tray e sticker vengono sempre serviti correttamente
        val file = if (pack.trayImageFile == fileName) {
            // Richiesta del tray icon (96x96) - generato in filesDir
            StickerFileCache.getCachedTrayFile(ctx, packId)
        } else {
            // Richiesta di uno sticker - servito direttamente dalla cartella WhatsApp (nessuna copia)
            StickerPackLoader.getStickerFile(fileName)
        }

        Log.d(TAG, "  -> file: ${file.absolutePath}, exists=${file.exists()}, size=${file.length()}")

        if (!file.exists() || file.length() == 0L) {
            Log.w(TAG, "  -> FILE NON TROVATO O VUOTO: ${file.absolutePath}")
            return null
        }

        return try {
            val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            Log.d(TAG, "  -> OK, servito")
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
                pack.trayImageFile,   // nome univoco es. "my_sticker_pack_001_tray.webp"
                "",  // android_play_store_link
                "",  // ios_app_download_link
                "",  // publisher_email
                "",  // publisher_website
                "",  // privacy_policy_website
                "",  // license_agreement_website
                "1", // image_data_version
                0,   // whatsapp_will_not_cache_stickers
                if (pack.isAnimated) 1 else 0  // animated_sticker_pack
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
            Log.w(TAG, "getStickersCursor: pack '$packId' non trovato")
        }
        context?.let { cursor.setNotificationUri(it.contentResolver, uri) }
        return cursor
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?): Int = 0
}
