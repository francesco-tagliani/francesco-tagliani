package com.stickeruploader;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.stickeruploader.models.StickerPack;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    private static final String TAG = "MainActivity";

    private ListView listView;
    private TextView tvStatus;
    private Button btnReload;
    private Button btnAddAll;

    private List<StickerPack> allPacks = new ArrayList<>();
    private Executor executor = Executors.newSingleThreadExecutor();
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate() chiamato");

        setContentView(R.layout.activity_main);

        listView = findViewById(R.id.list);
        tvStatus = findViewById(R.id.tvStatus);
        btnReload = findViewById(R.id.btnReload);
        btnAddAll = findViewById(R.id.btnAddAll);

        setupButtons();
        checkWhatsAppInstalled();
        loadStickers();
    }

    private void setupButtons() {
        Log.d(TAG, "setupButtons()");

        btnReload.setOnClickListener(v -> {
            Log.d(TAG, "btnReload cliccato");
            loadStickers();
        });

        btnAddAll.setOnClickListener(v -> {
            Log.d(TAG, "btnAddAll cliccato - aggiungendo " + allPacks.size() + " pack");
            for (StickerPack pack : allPacks) {
                addPackToWhatsApp(pack);
            }
        });

        listView.setOnItemClickListener((parent, view, position, id) -> {
            if (position < allPacks.size()) {
                StickerPack pack = allPacks.get(position);
                Log.d(TAG, "Pack cliccato: " + pack.identifier);
                addPackToWhatsApp(pack);
            }
        });
    }

    private void checkWhatsAppInstalled() {
        Log.d(TAG, "checkWhatsAppInstalled()");
        boolean installed = isPackageInstalled("com.whatsapp") || isPackageInstalled("com.whatsapp.w4b");
        Log.d(TAG, "WhatsApp installato: " + installed);

        if (!installed) {
            showError("WhatsApp non è installato");
        }
    }

    private boolean isPackageInstalled(String packageName) {
        try {
            getPackageManager().getPackageInfo(packageName, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private void loadStickers() {
        Log.d(TAG, "loadStickers() iniziato");
        tvStatus.setText("Caricamento sticker...");
        btnReload.setEnabled(false);
        btnAddAll.setEnabled(false);

        executor.execute(() -> {
            try {
                Log.d(TAG, "Caricando pack da " + StickerPackLoader.STICKER_DIR.getAbsolutePath());
                List<StickerPack> packs = StickerPackLoader.loadAllPacks();
                Log.d(TAG, "Caricati " + packs.size() + " pack");

                allPacks = packs;

                mainHandler.post(() -> {
                    if (packs.isEmpty()) {
                        Log.w(TAG, "Nessun pack trovato");
                        showError("Nessuno sticker trovato");
                    } else {
                        updateUI(packs);
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Errore caricamento", e);
                mainHandler.post(() -> showError("Errore: " + e.getMessage()));
            }
        });
    }

    private void updateUI(List<StickerPack> packs) {
        Log.d(TAG, "updateUI() con " + packs.size() + " pack");
        tvStatus.setText(packs.size() + " pack trovati");
        btnReload.setEnabled(true);
        btnAddAll.setEnabled(true);

        List<String> packNames = new ArrayList<>();
        for (StickerPack pack : packs) {
            packNames.add(pack.name + " (" + pack.stickers.size() + " sticker)");
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, packNames);
        listView.setAdapter(adapter);
    }

    private void addPackToWhatsApp(StickerPack pack) {
        Log.d(TAG, "addPackToWhatsApp() per " + pack.identifier);
        tvStatus.setText("Copiamento file di '" + pack.name + "'...");

        executor.execute(() -> {
            try {
                Log.d(TAG, "Copiando file del pack " + pack.identifier);
                copyPackFilesToAppStorage(pack);

                Log.d(TAG, "File copiati, inviando intent a WhatsApp");
                mainHandler.post(() -> sendWhatsAppIntent(pack));
            } catch (Exception e) {
                Log.e(TAG, "Errore copia file", e);
                mainHandler.post(() -> showError("Errore: " + e.getMessage()));
            }
        });
    }

    private void copyPackFilesToAppStorage(StickerPack pack) {
        Log.d(TAG, "copyPackFilesToAppStorage() per " + pack.identifier);

        File stickersDir = new File(getFilesDir(), "stickers");
        File trayDir = new File(getFilesDir(), "tray_images");

        if (!stickersDir.exists()) {
            stickersDir.mkdirs();
            Log.d(TAG, "Directory creata: " + stickersDir.getAbsolutePath());
        }
        if (!trayDir.exists()) {
            trayDir.mkdirs();
            Log.d(TAG, "Directory creata: " + trayDir.getAbsolutePath());
        }

        // Copia sticker files
        for (int i = 0; i < pack.stickers.size(); i++) {
            com.stickeruploader.models.Sticker sticker = pack.stickers.get(i);
            File sourceFile = new File(StickerPackLoader.STICKER_DIR, sticker.imageFileName);
            File destFile = new File(stickersDir, sticker.imageFileName);

            if (!destFile.exists() && sourceFile.exists()) {
                Log.d(TAG, "Copiando sticker: " + sticker.imageFileName);
                try {
                    sourceFile.renameTo(destFile) || copyFile(sourceFile, destFile);
                    Log.d(TAG, "Copiato: " + destFile.getAbsolutePath());
                } catch (Exception e) {
                    Log.e(TAG, "Errore copia file: " + e.getMessage());
                }
            }
        }

        // Copia tray image
        File sourceTrayFile = new File(StickerPackLoader.STICKER_DIR, pack.trayImageFile);
        File destTrayFile = new File(trayDir, pack.identifier + "_tray.webp");

        if (!destTrayFile.exists() && sourceTrayFile.exists()) {
            Log.d(TAG, "Copiando tray image: " + pack.trayImageFile);
            try {
                sourceTrayFile.renameTo(destTrayFile) || copyFile(sourceTrayFile, destTrayFile);
                Log.d(TAG, "Copiato tray: " + destTrayFile.getAbsolutePath());
            } catch (Exception e) {
                Log.e(TAG, "Errore copia tray: " + e.getMessage());
            }
        }

        Log.d(TAG, "copyPackFilesToAppStorage() completato per " + pack.identifier);
    }

    private boolean copyFile(File source, File dest) throws Exception {
        try (java.io.FileInputStream input = new java.io.FileInputStream(source);
             java.io.FileOutputStream output = new java.io.FileOutputStream(dest)) {
            byte[] buffer = new byte[8192];
            int length;
            while ((length = input.read(buffer)) > 0) {
                output.write(buffer, 0, length);
            }
            return true;
        }
    }

    private void sendWhatsAppIntent(StickerPack pack) {
        Log.d(TAG, "sendWhatsAppIntent() per " + pack.identifier);

        Intent intent = new Intent();
        intent.setAction("com.whatsapp.intent.action.ENABLE_STICKER_PACK");
        intent.putExtra("sticker_pack_id", pack.identifier);
        intent.putExtra("sticker_pack_authority", StickerContentProvider.AUTHORITY);
        intent.putExtra("sticker_pack_name", pack.name);
        intent.putExtra("sticker_pack_publisher", pack.publisher);
        intent.putExtra("sticker_pack_animated_emojis", false);
        intent.setPackage("com.whatsapp");

        Log.d(TAG, "Intent: " + intent.getAction());
        Log.d(TAG, "  pack_id=" + pack.identifier);
        Log.d(TAG, "  authority=" + StickerContentProvider.AUTHORITY);

        try {
            startActivity(intent);
            Log.d(TAG, "Intent inviato con successo");
            tvStatus.setText("Intent inviato a WhatsApp");
        } catch (Exception e) {
            Log.e(TAG, "Errore invio intent", e);
            showError("Errore: " + e.getMessage());
        }
    }

    private void showError(String message) {
        Log.e(TAG, "Errore: " + message);
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        tvStatus.setText("Errore: " + message);
    }
}
