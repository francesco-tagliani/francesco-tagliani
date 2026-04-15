package com.stickeruploader

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.stickeruploader.models.StickerPack
import java.io.FileNotFoundException

class StickerContentProvider : ContentProvider() {

    companion object {
        const val AUTHORITY = "com.stickeruploader.stickercontentprovider"

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

        // Nomi colonne ESATTI dal codice sorgente ufficiale WhatsApp/stickers
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
        StickerPackLoader.loadAllPacks()
    }

    override fun onCreate(): Boolean = true

    override fun getType(uri: Uri): String? {
        return when (URI_MATCHER.match(uri)) {
            METADATA -> "vnd.android.cursor.dir/vnd.$AUTHORITY.metadata"
            METADATA_CODE_FOR_SINGLE -> "vnd.android.cursor.item/vnd.$AUTHORITY.metadata"
            STICKERS -> "vnd.android.cursor.dir/vnd.$AUTHORITY.stickers"
            STICKERS_ASSET -> "image/webp"
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
        return when (URI_MATCHER.match(uri)) {
            METADATA -> getAllPacksMetadata(uri)
            METADATA_CODE_FOR_SINGLE -> {
                val packId = uri.lastPathSegment ?: return null
                getSinglePackMetadata(packId, uri)
            }
            STICKERS -> {
                val packId = uri.lastPathSegment ?: return null
                getStickersForPack(packId, uri)
            }
            else -> null
        }
    }

    /**
     * openAssetFile: metodo usato dall'implementazione ufficiale WhatsApp.
     * Serve i file dalla directory privata dell'app (filesDir/externalFilesDir)
     * per garantire accesso quando il ContentProvider è avviato da WhatsApp via IPC.
     * File su /storage/emulated/0/Android/media/com.whatsapp/ NON sono accessibili
     * al ContentProvider quando chiamato da WhatsApp (scoped storage Android 10+).
     */
    override fun openAssetFile(uri: Uri, mode: String): AssetFileDescriptor? {
        val segments = uri.pathSegments
        // URI deve avere esattamente 3 segmenti: stickers_asset/{packId}/{filename}
        if (segments.size != 3) return null

        val packId = segments[1]
        val fileName = segments[2]

        if (packId.isBlank() || fileName.isBlank()) return null

        val ctx = context ?: return null

        val pack = packsCache.find { it.identifier == packId } ?: return null

        val isTrayImage = pack.trayImageFile == fileName

        val file = if (isTrayImage) {
            StickerFileCache.getCachedTrayFile(ctx, packId)
        } else {
            StickerFileCache.getCachedStickerFile(ctx, fileName)
        }

        if (!file.exists() || file.length() == 0L) return null

        return try {
            val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            AssetFileDescriptor(pfd, 0, AssetFileDescriptor.UNKNOWN_LENGTH)
        } catch (e: FileNotFoundException) {
            null
        }
    }

    private fun getAllPacksMetadata(uri: Uri): Cursor {
        val cursor = MatrixCursor(METADATA_COLUMNS)
        for (pack in packsCache) {
            cursor.addRow(packToRow(pack))
        }
        context?.let { cursor.setNotificationUri(it.contentResolver, uri) }
        return cursor
    }

    private fun getSinglePackMetadata(packId: String, uri: Uri): Cursor {
        val cursor = MatrixCursor(METADATA_COLUMNS)
        val pack = packsCache.find { it.identifier == packId }
        if (pack != null) cursor.addRow(packToRow(pack))
        context?.let { cursor.setNotificationUri(it.contentResolver, uri) }
        return cursor
    }

    private fun getStickersForPack(packId: String, uri: Uri): Cursor {
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
        }
        context?.let { cursor.setNotificationUri(it.contentResolver, uri) }
        return cursor
    }

    private fun packToRow(pack: StickerPack): Array<Any?> {
        return arrayOf(
            pack.identifier,
            pack.name,
            pack.publisher,
            pack.trayImageFile,
            "",   // android_play_store_link
            "",   // ios_app_download_link
            "",   // sticker_pack_publisher_email
            "",   // sticker_pack_publisher_website
            "",   // sticker_pack_privacy_policy_website
            "",   // sticker_pack_license_agreement_website
            "1",  // image_data_version
            0,    // whatsapp_will_not_cache_stickers
            0     // animated_sticker_pack
        )
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?): Int = 0
}
