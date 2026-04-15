package com.stickeruploader

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.stickeruploader.databinding.ActivityMainBinding
import com.stickeruploader.models.StickerPack

class MainActivity : AppCompatActivity() {

    private companion object {
        const val TAG = "MainActivity"
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: StickerPackAdapter
    private val stickerPacks = mutableListOf<StickerPack>()
    private var waitingForManageStoragePermission = false

    private val ADD_PACK_REQUEST_CODE = 200

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            loadStickers()
        } else {
            showPermissionDeniedDialog()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Inizializza logger su file - salva tutto in externalFilesDir/sticker_log.txt
        AppLogger.init(this)
        AppLogger.separator("APP AVVIATA")
        AppLogger.i(TAG, "Android ${Build.VERSION.RELEASE} API ${Build.VERSION.SDK_INT}")
        AppLogger.i(TAG, "Log file: ${AppLogger.getLogFilePath()}")

        // Mostra il path del log all'utente
        binding.tvStatus.text = "Log: ${AppLogger.getLogFilePath()}"

        setupRecyclerView()
        setupButtons()
        checkPermissionsAndLoad()
    }

    override fun onResume() {
        super.onResume()
        // Controlla se MANAGE_EXTERNAL_STORAGE è stato concesso dopo il ritorno dalle Impostazioni
        if (waitingForManageStoragePermission &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            Environment.isExternalStorageManager()) {
            waitingForManageStoragePermission = false
            loadStickers()
        }
    }

    private fun setupRecyclerView() {
        adapter = StickerPackAdapter(stickerPacks) { pack ->
            addPackToWhatsApp(pack)
        }
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
    }

    private fun setupButtons() {
        binding.btnAddAll.setOnClickListener {
            val remaining = stickerPacks.filter { !it.isAddedToWhatsApp }
            if (remaining.isEmpty()) {
                Toast.makeText(this, "Tutti i pack sono già stati aggiunti!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            AlertDialog.Builder(this)
                .setTitle("Aggiungi tutti i pack")
                .setMessage("Stai per aggiungere ${remaining.size} pack a WhatsApp (${remaining.sumOf { it.stickers.size }} sticker totali).\n\nPer ogni pack apparirà una finestra di conferma WhatsApp.\n\nVuoi continuare?")
                .setPositiveButton("Sì, inizia") { _, _ ->
                    addAllPacksSequentially(remaining, 0)
                }
                .setNegativeButton("Annulla", null)
                .show()
        }

        binding.btnReload.setOnClickListener {
            checkPermissionsAndLoad()
        }
    }

    private fun checkPermissionsAndLoad() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+: serve MANAGE_EXTERNAL_STORAGE per leggere Android/media/com.whatsapp/
            if (!Environment.isExternalStorageManager()) {
                showManageStorageDialog()
                return
            }
        } else {
            // Android 10 e inferiori: READ_EXTERNAL_STORAGE è sufficiente
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
                permissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                return
            }
        }
        loadStickers()
    }

    private fun showManageStorageDialog() {
        AlertDialog.Builder(this)
            .setTitle("Accesso ai file richiesto")
            .setMessage("Per leggere gli sticker WhatsApp da:\n\n${StickerPackLoader.STICKER_DIR.absolutePath}\n\nserve il permesso 'Accesso a tutti i file'.\n\nTocca OK → attiva l'interruttore per Sticker Uploader → torna all'app.")
            .setPositiveButton("Apri Impostazioni") { _, _ ->
                waitingForManageStoragePermission = true
                try {
                    startActivity(
                        Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                            data = Uri.fromParts("package", packageName, null)
                        }
                    )
                } catch (e: Exception) {
                    startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                }
            }
            .setNegativeButton("Annulla") { _, _ ->
                binding.tvStatus.text =
                    "Permesso necessario.\nPremi 'Ricarica' dopo averlo concesso in Impostazioni → App → Sticker Uploader → Accesso speciale → Accesso a tutti i file."
            }
            .show()
    }

    private fun loadStickers() {
        binding.progressBar.visibility = View.VISIBLE
        binding.tvStatus.text = "Caricamento sticker in corso..."
        binding.recyclerView.visibility = View.GONE

        Thread {
            AppLogger.separator("CARICAMENTO STICKER")
            AppLogger.i(TAG, "Directory sorgente: ${StickerPackLoader.STICKER_DIR.absolutePath}")
            AppLogger.i(TAG, "Directory esiste: ${StickerPackLoader.STICKER_DIR.exists()}")
            AppLogger.i(TAG, "filesDir: ${filesDir.absolutePath}")

            val packs = StickerPackLoader.loadAllPacks()

            AppLogger.i(TAG, "Pack caricati: ${packs.size}")
            packs.forEach { pack ->
                AppLogger.i(TAG, "  Pack '${pack.name}' (${pack.identifier}): ${pack.stickers.size} sticker")
            }

            runOnUiThread {
                binding.progressBar.visibility = View.GONE
                stickerPacks.clear()
                stickerPacks.addAll(packs)
                adapter.notifyDataSetChanged()
                binding.recyclerView.visibility = View.VISIBLE

                if (packs.isEmpty()) {
                    val path = StickerPackLoader.STICKER_DIR.absolutePath
                    val msg = "Nessun file .webp trovato in:\n$path"
                    AppLogger.w(TAG, msg)
                    binding.tvStatus.text = "$msg\n\nLog: ${AppLogger.getLogFilePath()}"
                    binding.btnAddAll.isEnabled = false
                } else {
                    val totalStickers = packs.sumOf { it.stickers.size }
                    val msg = "Trovati $totalStickers sticker in ${packs.size} pack"
                    AppLogger.i(TAG, msg)
                    binding.tvStatus.text = "$msg\nLog: ${AppLogger.getLogFilePath()}"
                    binding.btnAddAll.isEnabled = true
                }
            }
        }.start()
    }

    private fun addPackToWhatsApp(pack: StickerPack) {
        binding.progressBar.visibility = View.VISIBLE
        binding.tvStatus.text = "Preparazione ${pack.name}..."

        Thread {
            AppLogger.separator("AGGIUNTA PACK A WHATSAPP: ${pack.identifier}")
            AppLogger.i(TAG, "Nome: ${pack.name}")
            AppLogger.i(TAG, "Sticker: ${pack.stickers.size}")
            AppLogger.i(TAG, "Tray image: ${pack.trayImageFile}")
            AppLogger.i(TAG, "Authority: ${StickerContentProvider.AUTHORITY}")

            try {
                StickerFileCache.preparePack(applicationContext, pack)
                AppLogger.i(TAG, "File copiati in filesDir con successo")

                // Verifica che i file siano stati copiati
                val stickersDir = java.io.File(filesDir, "stickers")
                val trayDir = java.io.File(filesDir, "tray_images")
                AppLogger.i(TAG, "stickersDir: ${stickersDir.absolutePath}, exists=${stickersDir.exists()}, files=${stickersDir.listFiles()?.size ?: 0}")
                AppLogger.i(TAG, "trayDir: ${trayDir.absolutePath}, exists=${trayDir.exists()}")

                val trayFile = java.io.File(trayDir, "${pack.identifier}_tray.webp")
                AppLogger.i(TAG, "tray file: ${trayFile.absolutePath}, exists=${trayFile.exists()}, size=${trayFile.length()}")

            } catch (e: Exception) {
                AppLogger.e(TAG, "Errore preparazione pack", e)
            }

            runOnUiThread {
                binding.progressBar.visibility = View.GONE
                val total = stickerPacks.sumOf { it.stickers.size }
                binding.tvStatus.text = "Trovati $total sticker in ${stickerPacks.size} pack\nLog: ${AppLogger.getLogFilePath()}"

                val intent = Intent().apply {
                    action = "com.whatsapp.intent.action.ENABLE_STICKER_PACK"
                    putExtra("sticker_pack_id", pack.identifier)
                    putExtra("sticker_pack_authority", StickerContentProvider.AUTHORITY)
                    putExtra("sticker_pack_name", pack.name)
                }
                AppLogger.i(TAG, "Invio intent WhatsApp: action=${intent.action}")
                AppLogger.i(TAG, "  sticker_pack_id=${pack.identifier}")
                AppLogger.i(TAG, "  sticker_pack_authority=${StickerContentProvider.AUTHORITY}")
                AppLogger.i(TAG, "  sticker_pack_name=${pack.name}")

                try {
                    @Suppress("DEPRECATION")
                    startActivityForResult(intent, ADD_PACK_REQUEST_CODE)
                    pendingPackId = pack.identifier
                    AppLogger.i(TAG, "Intent inviato - WhatsApp dovrebbe aprirsi")
                } catch (e: Exception) {
                    AppLogger.e(TAG, "Errore invio intent", e)
                    Toast.makeText(this, "WhatsApp non trovato o errore: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private var pendingPackId: String? = null
    private var batchPacksToAdd: List<StickerPack> = emptyList()
    private var batchCurrentIndex: Int = 0

    private fun addAllPacksSequentially(packs: List<StickerPack>, startIndex: Int) {
        if (startIndex >= packs.size) {
            Toast.makeText(this, "Tutti i pack sono stati aggiunti!", Toast.LENGTH_SHORT).show()
            return
        }
        batchPacksToAdd = packs
        batchCurrentIndex = startIndex
        addPackToWhatsApp(packs[startIndex])
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == ADD_PACK_REQUEST_CODE) {
            val packId = pendingPackId
            AppLogger.separator("RISULTATO DA WHATSAPP")
            AppLogger.i(TAG, "resultCode=$resultCode (OK=${RESULT_OK}, CANCELED=${RESULT_CANCELED})")
            AppLogger.i(TAG, "packId=$packId")
            if (resultCode == RESULT_OK) AppLogger.i(TAG, "✅ Pack aggiunto con successo!")
            else AppLogger.w(TAG, "❌ Pack NON aggiunto - resultCode=$resultCode")

            if (resultCode == RESULT_OK && packId != null) {
                adapter.markAsAdded(packId)
                if (batchPacksToAdd.isNotEmpty()) {
                    batchCurrentIndex++
                    if (batchCurrentIndex < batchPacksToAdd.size) {
                        binding.recyclerView.postDelayed({
                            addAllPacksSequentially(batchPacksToAdd, batchCurrentIndex)
                        }, 500)
                    } else {
                        batchPacksToAdd = emptyList()
                        Toast.makeText(this, "Tutti i pack aggiunti con successo!", Toast.LENGTH_SHORT).show()
                    }
                }
            } else if (resultCode == RESULT_CANCELED) {
                Toast.makeText(this, "Aggiunta annullata", Toast.LENGTH_SHORT).show()
                batchPacksToAdd = emptyList()
            }
            pendingPackId = null
        }
    }

    private fun showPermissionDeniedDialog() {
        AlertDialog.Builder(this)
            .setTitle("Permesso necessario")
            .setMessage("L'app ha bisogno del permesso storage per leggere gli sticker.")
            .setPositiveButton("Riprova") { _, _ -> checkPermissionsAndLoad() }
            .setNegativeButton("Annulla") { _, _ ->
                binding.tvStatus.text = "Permesso negato."
            }
            .show()
    }
}
