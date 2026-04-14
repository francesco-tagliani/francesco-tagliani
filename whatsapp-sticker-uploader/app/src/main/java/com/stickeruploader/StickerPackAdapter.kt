package com.stickeruploader

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.stickeruploader.databinding.ItemStickerPackBinding
import com.stickeruploader.models.StickerPack

class StickerPackAdapter(
    private val packs: List<StickerPack>,
    private val onAddClick: (StickerPack) -> Unit
) : RecyclerView.Adapter<StickerPackAdapter.ViewHolder>() {

    inner class ViewHolder(private val binding: ItemStickerPackBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(pack: StickerPack) {
            binding.tvPackName.text = pack.name
            binding.tvPackInfo.text = "${pack.stickers.size} sticker"
            binding.tvPackId.text = pack.identifier

            if (pack.isAddedToWhatsApp) {
                binding.btnAdd.text = "Aggiunto ✓"
                binding.btnAdd.isEnabled = false
            } else {
                binding.btnAdd.text = "Aggiungi a WhatsApp"
                binding.btnAdd.isEnabled = true
                binding.btnAdd.setOnClickListener { onAddClick(pack) }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemStickerPackBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(packs[position])
    }

    override fun getItemCount(): Int = packs.size

    fun markAsAdded(packId: String) {
        val index = packs.indexOfFirst { it.identifier == packId }
        if (index >= 0) {
            packs[index].isAddedToWhatsApp = true
            notifyItemChanged(index)
        }
    }
}
