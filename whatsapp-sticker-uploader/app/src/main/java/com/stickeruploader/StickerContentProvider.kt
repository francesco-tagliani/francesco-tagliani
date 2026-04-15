package com.stickeruploader

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
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

        private val METADATA_COLUMNS = arrayOf(
            "_id",
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

        private val STICKER_COLUMNS = arrayOf(
            "_id",
            "sticker_file_name",
            "sticker_emoji"
        )
    }

    private val packsCache: List<StickerPack> by lazy {
        StickerPackLoader.loadAllPacks()
    }

    override fun onCreate(): Boolean = true

    override fun getType(uri: Uri): String? {
        return when (URI_MATCHER.match(uri)) {
            STICKERS, METADATA, METADATA_CODE_FOR_SINGLE ->
                "vnd.android.cursor.dir/vnd.com.whatsapp.sticker"
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
                val segments = uri.pathSegments
                if (segments.size < 3) throw FileNotFoundException("URI non valido: $uri")
                val packId = segments[1]
                val fileName = segments[2]

                val sourceFile = StickerPackLoader.getStickerFile(fileName)
                if (!sourceFile.exists()) throw FileNotFoundException("File non trovato: ${sourceFile.absolutePath}")

                // Se è la tray image (icona del pack) la ridimensioniamo a 96x96
                val pack = packsCache.find { it.identifier == packId }
                val isTrayImage = pack?.trayImageFile == fileName

                if (isTrayImage) {
                    val trayFile = getOrCreateTrayImage(packId, sourceFile)
                    ParcelFileDescriptor.open(trayFile, ParcelFileDescriptor.MODE_READ_ONLY)
                } else {
                    ParcelFileDescriptor.open(sourceFile, ParcelFileDescriptor.MODE_READ_ONLY)
                }
            }
            else -> throw FileNotFoundException("URI non supportato: $uri")
        }
    }

    /**
     * Genera (e mette in cache) una tray image 96x96 dal primo sticker del pack.
     * WhatsApp richiede esattamente 96x96 pixel per l'icona del pack.
     */
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

    private fun getAllPacksMetadata(): Cursor {
        val cursor = MatrixCursor(METADATA_COLUMNS)
        for ((index, pack) in packsCache.withIndex()) {
            cursor.addRow(packToRow(index.toLong(), pack))
        }
        return cursor
    }

    private fun getSinglePackMetadata(packId: String): Cursor {
        val cursor = MatrixCursor(METADATA_COLUMNS)
        val pack = packsCache.find { it.identifier == packId }
        if (pack != null) {
            val index = packsCache.indexOf(pack).toLong()
            cursor.addRow(packToRow(index, pack))
        }
        return cursor
    }

    private fun getStickersForPack(packId: String): Cursor {
        val cursor = MatrixCursor(STICKER_COLUMNS)
        val pack = packsCache.find { it.identifier == packId } ?: return cursor
        for ((index, sticker) in pack.stickers.withIndex()) {
            cursor.addRow(arrayOf(
                index.toLong(),
                sticker.imageFileName,
                sticker.emojis.joinToString(",")
            ))
        }
        return cursor
    }

    private fun packToRow(id: Long, pack: StickerPack): Array<Any?> {
        return arrayOf(
            id,
            pack.identifier,
            pack.name,
            pack.publisher,
            pack.trayImageFile,
            "", "", "", "",
            "1",
            0,
            0
        )
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?): Int = 0
}
