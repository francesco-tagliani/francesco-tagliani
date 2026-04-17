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
import android.widget.SeekBar
import com.stickeruploader.databinding.ActivityMainBinding
import com.stickeruploader.models.StickerPack

class MainActivity : AppCompatActivity() {

    private companion object {
        const val TAG = "MainActivity"
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: StickerPackAdapter
    private var stickersPerPack = StickerPackLoader.STICKERS_PER_PACK
    private var waitingForManageStoragePermission = false

    private val ADD_PACK_REQUEST_CODE = 200

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) loadStickers() else showPermissionDeniedDialog()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        AppLogger.init(this)
        AppLogger.separator("APP AVVIATA")
        AppLogger.i(TAG, "Android ${Build.VERSION.RELEASE} API ${Build.VERSION.SDK_INT}")

        binding.tvStatus.text = "Log: ${AppLogger.getLogFilePath()}"

        setupSlider()
        setupRecyclerView()
        setupButtons()
        checkPermissionsAndLoad()
    }

    override fun onResume() {
        super.onResume()
        if (waitingForManageStoragePermission &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            Environment.isExternalStorageManager()) {
            waitingForManageStoragePermission = false
            loadStickers()
        }
    }

    private fun setupSlider() {
        // SeekBar: progress 0..27 → valore reale 3..30
        binding.seekBarPackSize.progress = stickersPerPack - 3
        binding.tvPackSize.text = "Sticker per pacchetto: $stickersPerPack"

        binding.seekBarPackSize.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                stickersPerPack = progress + 3
                binding.tvPackSize.text = "Sticker per pacchetto: $stickersPerPack"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {
                loadStickers()
            }
        })
    }

    private fun setupRecyclerView() {
        adapter = StickerPackAdapter(onAddClick = { pack -> addPackToWhatsApp(pack) })
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
    }

    private fun setupButtons() {
        binding.btnAddAll.setOnClickListener {
            val remaining = adapter.getPacks().filter { !it.isAddedToWhatsApp }
            if (remaining.isEmpty()) {
                Toast.makeText(this, "Tutti i pack sono già stati aggiunti!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            AlertDialog.Builder(this)
                .setTitle("Aggiungi tutti i pack")
                .setMessage("Stai per aggiungere ${remaining.size} pack a WhatsApp.\n\nPer ogni pack apparirà una finestra di conferma WhatsApp.\n\nVuoi continuare?")
                .setPositiveButton("Sì, inizia") { _, _ -> addAllPacksSequentially(remaining, 0) }
                .setNegativeButton("Annulla", null)
                .show()
        }

        binding.btnReload.setOnClickListener { checkPermissionsAndLoad() }

        binding.btnShareLog.setOnClickListener { shareLog() }
    }

    private fun shareLog() {
        val path = AppLogger.getLogFilePath()
        val file = java.io.File(path)
        if (!file.exists()) {
            Toast.makeText(this, "File di log non trovato", Toast.LENGTH_SHORT).show()
            return
        }
        val text = try { file.readText() } catch (e: Exception) {
            Toast.makeText(this, "Errore lettura log: ${e.message}", Toast.LENGTH_LONG).show()
            return
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "StickerUploader Log")
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(intent, "Condividi log"))
    }

    private fun checkPermissionsAndLoad() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                showManageStorageDialog(); return
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
                permissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE); return
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
                    startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = Uri.fromParts("package", packageName, null)
                    })
                } catch (e: Exception) {
                    startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                }
            }
            .setNegativeButton("Annulla") { _, _ ->
                binding.tvStatus.text = "Permesso necessario. Premi 'Ricarica' dopo averlo concesso."
            }
            .show()
    }

    private fun loadStickers() {
        binding.progressBar.visibility = View.VISIBLE
        binding.tvStatus.text = "Validazione sticker animati in corso..."
        binding.recyclerView.visibility = View.GONE

        Thread {
            AppLogger.separator("CARICAMENTO STICKER ANIMATI (${stickersPerPack} per pack)")
            val packs = StickerPackLoader.loadAllPacks(stickersPerPack)
            val validCount = StickerPackLoader.lastValidCount
            val invalidCount = StickerPackLoader.lastInvalidCount
            AppLogger.i(TAG, "Pack animati caricati: ${packs.size} (✅$validCount sticker validi, ❌$invalidCount scartati)")

            val items = mutableListOf<PackItem>()
            if (packs.isNotEmpty()) {
                items.add(PackItem.Header("🎬 Animati — ${packs.size} pack, ${packs.sumOf { it.stickers.size }} sticker"))
                packs.forEach { items.add(PackItem.Pack(it)) }
            }

            runOnUiThread {
                binding.progressBar.visibility = View.GONE
                adapter.updateItems(items)
                binding.recyclerView.visibility = View.VISIBLE

                if (packs.isEmpty()) {
                    binding.tvStatus.text = "Nessuno sticker animato valido trovato in:\n${StickerPackLoader.STICKER_DIR.absolutePath}\n\n✅ Validi: $validCount · ❌ Scartati (loop≠0 o dim≠512px): $invalidCount"
                    binding.btnAddAll.isEnabled = false
                } else {
                    val total = packs.sumOf { it.stickers.size }
                    binding.tvStatus.text = "${packs.size} pack animati · $total sticker · ✅$validCount validi · ❌$invalidCount scartati"
                    binding.btnAddAll.isEnabled = true
                }
            }
        }.start()
    }

    private fun addPackToWhatsApp(pack: StickerPack) {
        binding.progressBar.visibility = View.VISIBLE
        binding.tvStatus.text = "Preparazione ${pack.name}..."

        Thread {
            AppLogger.separator("AGGIUNTA PACK: ${pack.identifier}")
            try {
                StickerFileCache.preparePack(applicationContext, pack)
            } catch (e: Exception) {
                AppLogger.e(TAG, "Errore preparazione pack", e)
            }

            runOnUiThread {
                binding.progressBar.visibility = View.GONE
                val intent = Intent().apply {
                    action = "com.whatsapp.intent.action.ENABLE_STICKER_PACK"
                    putExtra("sticker_pack_id", pack.identifier)
                    putExtra("sticker_pack_authority", StickerContentProvider.AUTHORITY)
                    putExtra("sticker_pack_name", pack.name)
                }
                try {
                    @Suppress("DEPRECATION")
                    startActivityForResult(intent, ADD_PACK_REQUEST_CODE)
                    pendingPackId = pack.identifier
                } catch (e: Exception) {
                    AppLogger.e(TAG, "Errore invio intent", e)
                    Toast.makeText(this, "Errore: ${e.message}", Toast.LENGTH_LONG).show()
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
        if (requestCode != ADD_PACK_REQUEST_CODE) return

        val packId = pendingPackId
        AppLogger.i(TAG, "Risultato WhatsApp: resultCode=$resultCode, packId=$packId")

        if (resultCode == RESULT_OK && packId != null) {
            AppLogger.i(TAG, "✅ Pack aggiunto")
            adapter.markAsAdded(packId)
            if (batchPacksToAdd.isNotEmpty()) {
                batchCurrentIndex++
                if (batchCurrentIndex < batchPacksToAdd.size) {
                    binding.recyclerView.postDelayed({
                        addAllPacksSequentially(batchPacksToAdd, batchCurrentIndex)
                    }, 500)
                } else {
                    batchPacksToAdd = emptyList()
                    Toast.makeText(this, "Tutti i pack aggiunti!", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            AppLogger.w(TAG, "❌ Pack NON aggiunto - resultCode=$resultCode")
            data?.extras?.keySet()?.forEach { key ->
                AppLogger.w(TAG, "  extra[$key] = ${data.extras?.get(key)}")
            }
            if (resultCode == RESULT_CANCELED) {
                Toast.makeText(this, "Aggiunta annullata", Toast.LENGTH_SHORT).show()
                batchPacksToAdd = emptyList()
            }
        }
        pendingPackId = null
    }

    private fun showPermissionDeniedDialog() {
        AlertDialog.Builder(this)
            .setTitle("Permesso necessario")
            .setMessage("L'app ha bisogno del permesso storage per leggere gli sticker.")
            .setPositiveButton("Riprova") { _, _ -> checkPermissionsAndLoad() }
            .setNegativeButton("Annulla") { _, _ -> binding.tvStatus.text = "Permesso negato." }
            .show()
    }
}
