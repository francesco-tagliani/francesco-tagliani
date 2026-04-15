package com.stickeruploader;

import android.os.Environment;
import android.util.Log;

import com.stickeruploader.models.Sticker;
import com.stickeruploader.models.StickerPack;

import java.io.File;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Loader che legge sticker dalla memoria esterna (WhatsApp Media folder)
 * Basato sul pattern ufficiale WhatsApp di sticker packs
 */
public class StickerPackLoader {

    private static final String TAG = "StickerPackLoader";
    private static final int STICKERS_PER_PACK = 30;
    private static final long MAX_STICKER_SIZE_BYTES = 100 * 1024L; // 100KB

    public static final File STICKER_DIR = new File(
            Environment.getExternalStorageDirectory(),
            "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Stickers"
    );

    public static List<StickerPack> loadAllPacks() {
        Log.d(TAG, "loadAllPacks() - caricando da: " + STICKER_DIR.getAbsolutePath());

        if (!STICKER_DIR.exists() || !STICKER_DIR.isDirectory()) {
            Log.w(TAG, "Directory non trovata: " + STICKER_DIR.getAbsolutePath());
            return new ArrayList<>();
        }

        // Leggi tutti i file .webp
        File[] files = STICKER_DIR.listFiles((dir, name) ->
                name.toLowerCase().endsWith(".webp")
        );

        if (files == null || files.length == 0) {
            Log.w(TAG, "Nessun file WebP trovato");
            return new ArrayList<>();
        }

        // Ordina per nome
        List<File> sortedFiles = new ArrayList<>(Arrays.asList(files));
        sortedFiles.sort((a, b) -> a.getName().compareTo(b.getName()));

        Log.d(TAG, "File trovati: " + sortedFiles.size());

        // Filtra file non validi
        List<File> validFiles = new ArrayList<>();
        for (File f : sortedFiles) {
            long size = f.length();
            // File deve essere > 0 e <= 100KB e non animato
            if (size > 0 && size <= MAX_STICKER_SIZE_BYTES && !isAnimatedWebP(f)) {
                validFiles.add(f);
                Log.d(TAG, "✅ File valido: " + f.getName() + " (" + size + " bytes)");
            } else {
                Log.d(TAG, "❌ File scartato: " + f.getName() + " (size=" + size + ", animated=" + isAnimatedWebP(f) + ")");
            }
        }

        Log.d(TAG, "File validi: " + validFiles.size());

        if (validFiles.isEmpty()) {
            return new ArrayList<>();
        }

        // Raggruppa in pack di 30 sticker
        List<StickerPack> packs = new ArrayList<>();
        for (int i = 0; i < validFiles.size(); i += STICKERS_PER_PACK) {
            int endIndex = Math.min(i + STICKERS_PER_PACK, validFiles.size());
            List<File> packFiles = validFiles.subList(i, endIndex);

            // WhatsApp richiede minimo 3 sticker per pack
            if (packFiles.size() < 3) {
                Log.d(TAG, "Pack scartato: meno di 3 sticker");
                continue;
            }

            int packNumber = packs.size() + 1;
            String packId = String.format("my_sticker_pack_%03d", packNumber);
            String packName = "New Stiker " + packNumber;

            // Crea lista sticker
            List<Sticker> stickers = new ArrayList<>();
            for (File f : packFiles) {
                stickers.add(new Sticker(f.getName(), Arrays.asList("😀"), ""));
            }

            // Primo file come tray image
            String trayImageFile = packFiles.get(0).getName();

            StickerPack pack = new StickerPack(
                    packId,
                    packName,
                    "Il mio dispositivo",
                    trayImageFile,
                    "",
                    "",
                    "",
                    "",
                    "1",
                    false,
                    false,
                    stickers
            );

            packs.add(pack);
            Log.d(TAG, "✅ Pack creato: " + packId + " con " + stickers.size() + " sticker");
        }

        Log.d(TAG, "Totale pack: " + packs.size());
        return packs;
    }

    /**
     * Rileva se un file WebP è animato controllando l'header
     */
    private static boolean isAnimatedWebP(File file) {
        if (file.length() < 20) return false;

        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            byte[] header = new byte[50];
            raf.read(header);

            // Cerca "ANIM" nei primi 50 byte
            for (int i = 0; i < header.length - 3; i++) {
                if (header[i] == 65 &&     // 'A'
                    header[i+1] == 78 &&   // 'N'
                    header[i+2] == 73 &&   // 'I'
                    header[i+3] == 77) {   // 'M'
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            Log.e(TAG, "Errore checking WebP animato: " + e.getMessage());
            return false;
        }
    }

    public static File getStickerFile(String fileName) {
        return new File(STICKER_DIR, fileName);
    }
}
