package com.stickeruploader

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
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

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: StickerPackAdapter
    private val stickerPacks = mutableListOf<StickerPack>()

    // Codice risultato per aggiunta pack a WhatsApp
    private val ADD_PACK_REQUEST_CODE = 200

    // Launcher per richiesta permessi
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.values.any { it }
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

        setupRecyclerView()
        setupButtons()
        checkPermissionsAndLoad()
    }

    private fun setupRecyclerView() {
        adapter = StickerPackAdapter(stickerPacks) { pack ->
            addPackToWhatsApp(pack)
        }
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
    }

    private fun setupButtons() {
        // Pulsante "Aggiungi TUTTI i pack"
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

        // Pulsante ricarica
        binding.btnReload.setOnClickListener {
            loadStickers()
        }
    }

    private fun checkPermissionsAndLoad() {
        val permissionsNeeded = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+: usa READ_MEDIA_IMAGES
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
                != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.READ_MEDIA_IMAGES)
            }
        } else {
            // Android 12 e inferiori: usa READ_EXTERNAL_STORAGE
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        if (permissionsNeeded.isEmpty()) {
            loadStickers()
        } else {
            permissionLauncher.launch(permissionsNeeded.toTypedArray())
        }
    }

    private fun loadStickers() {
        binding.progressBar.visibility = View.VISIBLE
        binding.tvStatus.text = "Caricamento sticker in corso..."
        binding.recyclerView.visibility = View.GONE

        // Carica in background
        Thread {
            val packs = StickerPackLoader.loadAllPacks()
            runOnUiThread {
                binding.progressBar.visibility = View.GONE
                stickerPacks.clear()
                stickerPacks.addAll(packs)
                adapter.notifyDataSetChanged()
                binding.recyclerView.visibility = View.VISIBLE

                if (packs.isEmpty()) {
                    val path = StickerPackLoader.STICKER_DIR.absolutePath
                    binding.tvStatus.text = "Nessun file .webp trovato in:\n$path\n\nAssicurati che la cartella esista e contenga file .webp"
                    binding.btnAddAll.isEnabled = false
                } else {
                    val totalStickers = packs.sumOf { it.stickers.size }
                    binding.tvStatus.text = "Trovati $totalStickers sticker in ${packs.size} pack"
                    binding.btnAddAll.isEnabled = true
                }
            }
        }.start()
    }

    /**
     * Aggiunge un singolo pack a WhatsApp tramite l'API ufficiale (Intent).
     */
    private fun addPackToWhatsApp(pack: StickerPack) {
        val intent = Intent().apply {
            action = "com.whatsapp.intent.action.ENABLE_STICKER_PACK"
            putExtra("sticker_pack_id", pack.identifier)
            putExtra("sticker_pack_authority", StickerContentProvider.AUTHORITY)
            putExtra("sticker_pack_name", pack.name)
        }

        try {
            startActivityForResult(intent, ADD_PACK_REQUEST_CODE)
            // Salva quale pack stiamo aggiungendo per gestire il risultato
            pendingPackId = pack.identifier
        } catch (e: Exception) {
            Toast.makeText(
                this,
                "WhatsApp non trovato o errore: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private var pendingPackId: String? = null
    private var batchPacksToAdd: List<StickerPack> = emptyList()
    private var batchCurrentIndex: Int = 0

    /**
     * Aggiunge i pack uno alla volta (l'utente deve confermare ogni pack in WhatsApp).
     */
    private fun addAllPacksSequentially(packs: List<StickerPack>, startIndex: Int) {
        if (startIndex >= packs.size) {
            Toast.makeText(this, "Tutti i pack sono stati aggiunti!", Toast.LENGTH_SHORT).show()
            return
        }
        batchPacksToAdd = packs
        batchCurrentIndex = startIndex
        addPackToWhatsApp(packs[startIndex])
    }

    @Deprecated("Needed for WhatsApp sticker API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == ADD_PACK_REQUEST_CODE) {
            val packId = pendingPackId
            if (resultCode == RESULT_OK && packId != null) {
                adapter.markAsAdded(packId)
                // Se siamo in modalità "aggiungi tutti", continua con il prossimo
                if (batchPacksToAdd.isNotEmpty()) {
                    batchCurrentIndex++
                    if (batchCurrentIndex < batchPacksToAdd.size) {
                        // Piccola pausa prima del prossimo pack
                        binding.recyclerView.postDelayed({
                            addAllPacksSequentially(batchPacksToAdd, batchCurrentIndex)
                        }, 500)
                    } else {
                        batchPacksToAdd = emptyList()
                        Toast.makeText(this, "Tutti i pack aggiunti con successo!", Toast.LENGTH_SHORT).show()
                    }
                }
            } else if (resultCode == RESULT_CANCELED) {
                Toast.makeText(this, "Aggiunta annullata dall'utente", Toast.LENGTH_SHORT).show()
                batchPacksToAdd = emptyList()
            }
            pendingPackId = null
        }
    }

    private fun showPermissionDeniedDialog() {
        AlertDialog.Builder(this)
            .setTitle("Permesso necessario")
            .setMessage("L'app ha bisogno del permesso per leggere i file dalla memoria del dispositivo.\n\nVai in Impostazioni → App → Sticker Uploader → Permessi e abilita l'accesso alla memoria.")
            .setPositiveButton("Riprova") { _, _ -> checkPermissionsAndLoad() }
            .setNegativeButton("Annulla") { _, _ ->
                binding.tvStatus.text = "Permesso negato. L'app non può leggere gli sticker."
            }
            .show()
    }
}
