package com.stickeruploader

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.stickeruploader.databinding.ActivityMainBinding
import com.stickeruploader.models.StickerPack
import java.io.File
import java.util.concurrent.Executor
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private lateinit var binding: ActivityMainBinding
    private val executor: Executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    private var allPacks: List<StickerPack> = emptyList()
    private lateinit var adapter: StickerPackAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate() chiamato")

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupButtons()
        checkWhatsAppInstalled()

        loadStickers()
    }

    private fun setupRecyclerView() {
        Log.d(TAG, "setupRecyclerView() chiamato")
        adapter = StickerPackAdapter(emptyList()) { pack ->
            addPackToWhatsApp(pack)
        }
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
    }

    private fun setupButtons() {
        Log.d(TAG, "setupButtons() chiamato")

        binding.btnReload.setOnClickListener {
            Log.d(TAG, "btnReload cliccato")
            loadStickers()
        }

        binding.btnAddAll.setOnClickListener {
            Log.d(TAG, "btnAddAll cliccato, aggiungendo ${allPacks.size} pack")
            for (pack in allPacks) {
                addPackToWhatsApp(pack)
            }
        }
    }

    private fun checkWhatsAppInstalled() {
        Log.d(TAG, "checkWhatsAppInstalled() chiamato")
        val isWhatsAppInstalled = isPackageInstalled("com.whatsapp") || isPackageInstalled("com.whatsapp.w4b")
        Log.d(TAG, "WhatsApp installato: $isWhatsAppInstalled")

        if (!isWhatsAppInstalled) {
            showError("WhatsApp non è installato")
        }
    }

    private fun isPackageInstalled(packageName: String): Boolean {
        return try {
            packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    private fun loadStickers() {
        Log.d(TAG, "loadStickers() iniziato")
        binding.progressBar.visibility = View.VISIBLE
        binding.recyclerView.visibility = View.GONE
        binding.tvStatus.text = "Caricamento sticker..."

        executor.execute {
            try {
                Log.d(TAG, "loadStickers() caricando pack...")
                val packs = StickerPackLoader.loadAllPacks()
                Log.d(TAG, "loadStickers() caricati ${packs.size} pack")

                allPacks = packs

                mainHandler.post {
                    if (packs.isEmpty()) {
                        Log.w(TAG, "Nessun pack trovato")
                        showError("Nessuno sticker trovato in ${StickerPackLoader.STICKER_DIR.absolutePath}")
                    } else {
                        Log.d(TAG, "Aggiornando UI con ${packs.size} pack")
                        updateUI(packs)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Errore caricamento sticker", e)
                mainHandler.post {
                    showError("Errore: ${e.message}")
                }
            }
        }
    }

    private fun updateUI(packs: List<StickerPack>) {
        Log.d(TAG, "updateUI() con ${packs.size} pack")
        binding.progressBar.visibility = View.GONE
        binding.recyclerView.visibility = View.VISIBLE
        binding.tvStatus.text = "${packs.size} pack trovati"
        binding.btnAddAll.isEnabled = true

        adapter = StickerPackAdapter(packs) { pack ->
            addPackToWhatsApp(pack)
        }
        binding.recyclerView.adapter = adapter
    }

    private fun addPackToWhatsApp(pack: StickerPack) {
        Log.d(TAG, "addPackToWhatsApp() per pack: ${pack.identifier}")
        binding.tvStatus.text = "Copiamento file di '${pack.name}'..."
        binding.progressBar.visibility = View.VISIBLE

        executor.execute {
            try {
                Log.d(TAG, "Copiamento file del pack ${pack.identifier}")
                copyPackFilesToAppStorage(pack)

                Log.d(TAG, "File copiati, inviando intent a WhatsApp")
                mainHandler.post {
                    sendWhatsAppIntent(pack)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Errore durante copia file", e)
                mainHandler.post {
                    showError("Errore durante copia file: ${e.message}")
                    binding.progressBar.visibility = View.GONE
                }
            }
        }
    }

    /**
     * Copia tutti i file del pack dalla cartella sorgente alla filesDir dell'app.
     * ContentProvider può servire file solo da filesDir, non da shared storage.
     */
    private fun copyPackFilesToAppStorage(pack: StickerPack) {
        Log.d(TAG, "copyPackFilesToAppStorage() per ${pack.identifier}")

        // Crea le directory nel filesDir se non esistono
        val stickersDir = File(filesDir, "stickers")
        val trayDir = File(filesDir, "tray_images")

        if (!stickersDir.exists()) {
            stickersDir.mkdirs()
            Log.d(TAG, "Directory creata: ${stickersDir.absolutePath}")
        }
        if (!trayDir.exists()) {
            trayDir.mkdirs()
            Log.d(TAG, "Directory creata: ${trayDir.absolutePath}")
        }

        // Copia i file sticker
        for (sticker in pack.stickers) {
            val sourceFile = File(StickerPackLoader.STICKER_DIR, sticker.imageFileName)
            val destFile = File(stickersDir, sticker.imageFileName)

            if (!destFile.exists()) {
                Log.d(TAG, "Copiando sticker: ${sticker.imageFileName}")
                sourceFile.copyTo(destFile, overwrite = true)
                Log.d(TAG, "Copiato: ${destFile.absolutePath} (${destFile.length()} bytes)")
            } else {
                Log.d(TAG, "Sticker già esiste: ${destFile.absolutePath}")
            }
        }

        // Copia tray image (primo sticker come icona del pack)
        val trayImageName = pack.trayImageFile
        val sourceTrayFile = File(StickerPackLoader.STICKER_DIR, trayImageName)
        val destTrayFile = File(trayDir, "${pack.identifier}_tray.webp")

        if (sourceTrayFile.exists()) {
            if (!destTrayFile.exists()) {
                Log.d(TAG, "Copiando tray image: $trayImageName")
                sourceTrayFile.copyTo(destTrayFile, overwrite = true)
                Log.d(TAG, "Copiato tray: ${destTrayFile.absolutePath} (${destTrayFile.length()} bytes)")
            } else {
                Log.d(TAG, "Tray image già esiste: ${destTrayFile.absolutePath}")
            }
        } else {
            Log.w(TAG, "Tray image non trovato: ${sourceTrayFile.absolutePath}")
        }

        Log.d(TAG, "copyPackFilesToAppStorage() completato per ${pack.identifier}")
    }

    private fun sendWhatsAppIntent(pack: StickerPack) {
        Log.d(TAG, "sendWhatsAppIntent() per ${pack.identifier}")

        val intent = Intent().apply {
            action = "com.whatsapp.intent.action.ENABLE_STICKER_PACK"
            putExtra("sticker_pack_id", pack.identifier)
            putExtra("sticker_pack_authority", StickerContentProvider.AUTHORITY)
            putExtra("sticker_pack_name", pack.name)
            putExtra("sticker_pack_publisher", pack.publisher)
            putExtra("sticker_pack_animated_emojis", false)
            putExtra("sticker_pack_preview_emoji_in_chat", "")
            setPackage("com.whatsapp")
        }

        Log.d(TAG, "Intent: action=${intent.action}, package=${intent.`package`}")
        Log.d(TAG, "  sticker_pack_id=${pack.identifier}")
        Log.d(TAG, "  sticker_pack_authority=${StickerContentProvider.AUTHORITY}")
        Log.d(TAG, "  sticker_pack_name=${pack.name}")

        try {
            startActivity(intent)
            Log.d(TAG, "Intent inviato con successo")
            binding.progressBar.visibility = View.GONE
            binding.tvStatus.text = "Intent inviato a WhatsApp per ${pack.name}"
        } catch (e: Exception) {
            Log.e(TAG, "Errore invio intent", e)
            showError("Errore invio intent: ${e.message}")
            binding.progressBar.visibility = View.GONE
        }
    }

    private fun showError(message: String) {
        Log.e(TAG, "Errore mostrato: $message")
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        binding.tvStatus.text = "Errore: $message"
        binding.progressBar.visibility = View.GONE
    }
}
