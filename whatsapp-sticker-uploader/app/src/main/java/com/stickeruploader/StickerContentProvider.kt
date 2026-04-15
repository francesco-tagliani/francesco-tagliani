package com.stickeruploader

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.database.MatrixCursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import com.stickeruploader.models.StickerPack
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream

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

        // Nomi esatti colonne richiesti da WhatsApp (da codice ufficiale WhatsApp/stickers)
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

        // Nomi esatti colonne sticker (da codice ufficiale WhatsApp/stickers)
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
            else -> throw IllegalArgumentException("URI sconosciuto: $uri")
        }
    }

    override fun openAssetFile(uri: Uri, mode: String): AssetFileDescriptor? {
        return when (URI_MATCHER.match(uri)) {
            STICKERS_ASSET -> {
                val segments = uri.pathSegments
                if (segments.size < 3) throw FileNotFoundException("URI non valido: $uri")
                val packId = segments[1]
                val fileName = segments[2]

                val sourceFile = StickerPackLoader.getStickerFile(fileName)
                if (!sourceFile.exists()) throw FileNotFoundException("File non trovato: ${sourceFile.absolutePath}")

                val pack = packsCache.find { it.identifier == packId }
                val isTrayImage = pack?.trayImageFile == fileName

                val file = if (isTrayImage) {
                    getOrCreateTrayImage(packId, sourceFile)
                } else {
                    sourceFile
                }

                val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                AssetFileDescriptor(pfd, 0, file.length())
            }
            else -> throw FileNotFoundException("URI non supportato: $uri")
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
        val pack = packsCache.find { it.identifier == packId } ?: run {
            context?.let { cursor.setNotificationUri(it.contentResolver, uri) }
            return cursor
        }
        for (sticker in pack.stickers) {
            cursor.addRow(arrayOf(
                sticker.imageFileName,
                sticker.emojis.joinToString(","),
                ""
            ))
        }
        context?.let { cursor.setNotificationUri(it.contentResolver, uri) }
        return cursor
    }

    private fun packToRow(pack: StickerPack): Array<Any?> {
        return arrayOf(
            pack.identifier,           // sticker_pack_identifier
            pack.name,                 // sticker_pack_name
            pack.publisher,            // sticker_pack_publisher
            pack.trayImageFile,        // sticker_pack_icon (nome file, non URI)
            "",                        // android_play_store_link
            "",                        // ios_app_download_link
            "",                        // sticker_pack_publisher_email
            "",                        // sticker_pack_publisher_website
            "",                        // sticker_pack_privacy_policy_website
            "",                        // sticker_pack_license_agreement_website
            "1",                       // image_data_version (stringa non-null non-empty)
            0,                         // whatsapp_will_not_cache_stickers (int 0/1)
            0                          // animated_sticker_pack (int 0/1)
        )
    }

    private fun getOrCreateTrayImage(packId: String, sourceFile: File): File {
        val ctx = context ?: return sourceFile
        val trayDir = File(ctx.cacheDir, "tray_images")
        trayDir.mkdirs()
        val trayFile = File(trayDir, "${packId}_tray.webp")

        if (trayFile.exists() && trayFile.length() > 0) return trayFile

        return try {
            val bitmap = BitmapFactory.decodeFile(sourceFile.absolutePath) ?: return sourceFile
            val scaled = Bitmap.createScaledBitmap(bitmap, 96, 96, true)
            bitmap.recycle()
            FileOutputStream(trayFile).use { fos ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    scaled.compress(Bitmap.CompressFormat.WEBP_LOSSY, 90, fos)
                } else {
                    @Suppress("DEPRECATION")
                    scaled.compress(Bitmap.CompressFormat.WEBP, 90, fos)
                }
            }
            scaled.recycle()
            trayFile
        } catch (e: Exception) {
            sourceFile
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?): Int = 0
}
