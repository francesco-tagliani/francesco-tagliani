/*
 * Basato sul codice ufficiale WhatsApp/stickers
 * Adattato per leggere file dalla memoria esterna
 */

package com.stickeruploader;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import com.stickeruploader.models.Sticker;
import com.stickeruploader.models.StickerPack;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class StickerContentProvider extends ContentProvider {

    private static final String TAG = "StickerContentProvider";

    // Colonne per metadati (ESATTAMENTE come WhatsApp richiede)
    public static final String STICKER_PACK_IDENTIFIER = "sticker_pack_identifier";
    public static final String STICKER_PACK_NAME = "sticker_pack_name";
    public static final String STICKER_PACK_PUBLISHER = "sticker_pack_publisher";
    public static final String STICKER_PACK_ICON = "sticker_pack_icon";
    public static final String ANDROID_PLAY_STORE_LINK = "android_play_store_link";
    public static final String IOS_APP_DOWNLOAD_LINK = "ios_app_download_link";
    public static final String PUBLISHER_EMAIL = "sticker_pack_publisher_email";
    public static final String PUBLISHER_WEBSITE = "sticker_pack_publisher_website";
    public static final String PRIVACY_POLICY_WEBSITE = "sticker_pack_privacy_policy_website";
    public static final String LICENSE_AGREEMENT_WEBSITE = "sticker_pack_license_agreement_website";
    public static final String IMAGE_DATA_VERSION = "image_data_version";
    public static final String AVOID_CACHE = "whatsapp_will_not_cache_stickers";
    public static final String ANIMATED_STICKER_PACK = "animated_sticker_pack";

    // Colonne per sticker
    public static final String STICKER_FILE_NAME = "sticker_file_name";
    public static final String STICKER_EMOJI = "sticker_emoji";
    public static final String STICKER_ACCESSIBILITY_TEXT = "sticker_accessibility_text";

    public static final String AUTHORITY = "com.stickeruploader.stickercontentprovider";
    private static final String METADATA = "metadata";
    private static final String STICKERS = "stickers";
    private static final String STICKERS_ASSET = "stickers_asset";

    private static final int METADATA_CODE = 1;
    private static final int METADATA_CODE_FOR_SINGLE_PACK = 2;
    private static final int STICKERS_CODE = 3;
    private static final int STICKERS_ASSET_CODE = 4;
    private static final int STICKER_PACK_TRAY_ICON_CODE = 5;

    private static final UriMatcher MATCHER = new UriMatcher(UriMatcher.NO_MATCH);
    private List<StickerPack> stickerPackList;

    @Override
    public boolean onCreate() {
        Log.d(TAG, "onCreate() chiamato");

        MATCHER.addURI(AUTHORITY, METADATA, METADATA_CODE);
        MATCHER.addURI(AUTHORITY, METADATA + "/*", METADATA_CODE_FOR_SINGLE_PACK);
        MATCHER.addURI(AUTHORITY, STICKERS + "/*", STICKERS_CODE);
        MATCHER.addURI(AUTHORITY, STICKERS_ASSET + "/*/*", STICKERS_ASSET_CODE);

        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        Log.d(TAG, "query(" + uri + ")");

        final int code = MATCHER.match(uri);
        switch (code) {
            case METADATA_CODE:
                Log.d(TAG, "  -> metadata (tutti i pack)");
                return getMetadataForAllPacks(uri);
            case METADATA_CODE_FOR_SINGLE_PACK:
                String packId = uri.getLastPathSegment();
                Log.d(TAG, "  -> metadata singolo: " + packId);
                return getMetadataForSinglePack(uri, packId);
            case STICKERS_CODE:
                packId = uri.getLastPathSegment();
                Log.d(TAG, "  -> stickers per pack: " + packId);
                return getStickersForPack(uri, packId);
            default:
                Log.w(TAG, "  -> URI non riconosciuta!");
                return null;
        }
    }

    @Override
    public String getType(Uri uri) {
        final int code = MATCHER.match(uri);
        switch (code) {
            case METADATA_CODE:
                return "vnd.android.cursor.dir/vnd." + AUTHORITY + "." + METADATA;
            case METADATA_CODE_FOR_SINGLE_PACK:
                return "vnd.android.cursor.item/vnd." + AUTHORITY + "." + METADATA;
            case STICKERS_CODE:
                return "vnd.android.cursor.dir/vnd." + AUTHORITY + "." + STICKERS;
            case STICKERS_ASSET_CODE:
                return "image/webp";
            case STICKER_PACK_TRAY_ICON_CODE:
                return "image/webp";
            default:
                return null;
        }
    }

    @Override
    public AssetFileDescriptor openAssetFile(Uri uri, String mode) {
        Log.d(TAG, "openAssetFile(" + uri + ")");

        final int code = MATCHER.match(uri);
        if (code == STICKERS_ASSET_CODE || code == STICKER_PACK_TRAY_ICON_CODE) {
            return getImageAsset(uri);
        }
        return null;
    }

    private AssetFileDescriptor getImageAsset(Uri uri) {
        Log.d(TAG, "getImageAsset(" + uri + ")");

        List<String> pathSegments = uri.getPathSegments();
        if (pathSegments.size() < 3) {
            Log.w(TAG, "  -> URI malformata!");
            return null;
        }

        String packId = pathSegments.get(1);
        String fileName = pathSegments.get(2);
        Log.d(TAG, "  -> packId=" + packId + ", fileName=" + fileName);

        // Cerca il pack
        StickerPack pack = null;
        for (StickerPack p : getStickerPackList()) {
            if (p.identifier.equals(packId)) {
                pack = p;
                break;
            }
        }

        if (pack == null) {
            Log.w(TAG, "  -> Pack non trovato: " + packId);
            return null;
        }

        // Cerca il file nel filesystem
        Context ctx = getContext();
        if (ctx == null) {
            Log.e(TAG, "  -> Context è null!");
            return null;
        }

        // Primo cerchiamo nei file privati copiati (da MainActivity)
        File stickersDir = new File(ctx.getFilesDir(), "stickers");
        File trayDir = new File(ctx.getFilesDir(), "tray_images");

        File file = null;
        if (pack.trayImageFile.equals(fileName)) {
            file = new File(trayDir, packId + "_tray.webp");
            Log.d(TAG, "  -> Cercando tray image: " + file.getAbsolutePath());
        } else {
            file = new File(stickersDir, fileName);
            Log.d(TAG, "  -> Cercando sticker: " + file.getAbsolutePath());
        }

        if (!file.exists()) {
            Log.w(TAG, "  -> File non trovato: " + file.getAbsolutePath());
            // Fallback: cerca nel percorso originale (WhatsApp Media)
            file = new File(StickerPackLoader.STICKER_DIR, fileName);
            Log.d(TAG, "  -> Trying fallback: " + file.getAbsolutePath());
            if (!file.exists()) {
                Log.w(TAG, "  -> Neanche il fallback esiste!");
                return null;
            }
        }

        Log.d(TAG, "  -> File trovato! Size: " + file.length() + " bytes");

        try {
            ParcelFileDescriptor pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
            Log.d(TAG, "  -> ParcelFileDescriptor creato con successo");
            return new AssetFileDescriptor(pfd, 0, AssetFileDescriptor.UNKNOWN_LENGTH);
        } catch (FileNotFoundException e) {
            Log.e(TAG, "  -> FileNotFoundException: " + e.getMessage());
            return null;
        }
    }

    private Cursor getMetadataForAllPacks(Uri uri) {
        Log.d(TAG, "getMetadataForAllPacks()");
        return getMetadataCursor(getStickerPackList());
    }

    private Cursor getMetadataForSinglePack(Uri uri, String packId) {
        Log.d(TAG, "getMetadataForSinglePack(" + packId + ")");

        for (StickerPack pack : getStickerPackList()) {
            if (pack.identifier.equals(packId)) {
                return getMetadataCursor(Collections.singletonList(pack));
            }
        }

        return getMetadataCursor(new ArrayList<>());
    }

    private Cursor getMetadataCursor(List<StickerPack> packs) {
        MatrixCursor cursor = new MatrixCursor(new String[]{
                STICKER_PACK_IDENTIFIER,
                STICKER_PACK_NAME,
                STICKER_PACK_PUBLISHER,
                STICKER_PACK_ICON,
                ANDROID_PLAY_STORE_LINK,
                IOS_APP_DOWNLOAD_LINK,
                PUBLISHER_EMAIL,
                PUBLISHER_WEBSITE,
                PRIVACY_POLICY_WEBSITE,
                LICENSE_AGREEMENT_WEBSITE,
                IMAGE_DATA_VERSION,
                AVOID_CACHE,
                ANIMATED_STICKER_PACK,
        });

        for (StickerPack pack : packs) {
            MatrixCursor.RowBuilder row = cursor.newRow();
            row.add(pack.identifier);
            row.add(pack.name);
            row.add(pack.publisher);
            row.add(pack.trayImageFile);
            row.add("");
            row.add("");
            row.add(pack.publisherEmail);
            row.add(pack.publisherWebsite);
            row.add(pack.privacyPolicyWebsite);
            row.add(pack.licenseAgreementWebsite);
            row.add(pack.imageDataVersion);
            row.add(pack.avoidCache ? 1 : 0);
            row.add(pack.animatedStickerPack ? 1 : 0);
        }

        cursor.setNotificationUri(Objects.requireNonNull(getContext()).getContentResolver(), uri);
        return cursor;
    }

    private Cursor getStickersForPack(Uri uri, String packId) {
        MatrixCursor cursor = new MatrixCursor(new String[]{
                STICKER_FILE_NAME,
                STICKER_EMOJI,
                STICKER_ACCESSIBILITY_TEXT,
        });

        for (StickerPack pack : getStickerPackList()) {
            if (pack.identifier.equals(packId)) {
                Log.d(TAG, "Found pack " + packId + " with " + pack.stickers.size() + " stickers");
                for (Sticker sticker : pack.stickers) {
                    MatrixCursor.RowBuilder row = cursor.newRow();
                    row.add(sticker.imageFileName);
                    row.add(String.join(",", sticker.emojis));
                    row.add(sticker.accessibilityText);
                }
                break;
            }
        }

        cursor.setNotificationUri(Objects.requireNonNull(getContext()).getContentResolver(), uri);
        return cursor;
    }

    private synchronized List<StickerPack> getStickerPackList() {
        if (stickerPackList == null) {
            Log.d(TAG, "Loading sticker pack list...");
            stickerPackList = StickerPackLoader.loadAllPacks();
            Log.d(TAG, "Loaded " + stickerPackList.size() + " packs");
        }
        return stickerPackList;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
