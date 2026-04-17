package com.stickeruploader

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.stickeruploader.databinding.ItemStickerPackBinding
import com.stickeruploader.models.StickerPack

sealed class PackItem {
    data class Header(val title: String) : PackItem()
    data class Pack(val stickerPack: StickerPack) : PackItem()
}

class StickerPackAdapter(
    private val items: MutableList<PackItem> = mutableListOf(),
    private val onAddClick: (StickerPack) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_PACK   = 1
    }

    inner class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitle: TextView = view.findViewById(R.id.tvSectionHeader)
    }

    inner class PackViewHolder(private val binding: ItemStickerPackBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(pack: StickerPack) {
            binding.tvPackName.text = if (pack.isAnimated) "🎬 ${pack.name}" else pack.name
            binding.tvPackInfo.text = "${pack.stickers.size} sticker${if (pack.isAnimated) " (animati)" else ""}"
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

    override fun getItemViewType(position: Int) = when (items[position]) {
        is PackItem.Header -> TYPE_HEADER
        is PackItem.Pack   -> TYPE_PACK
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == TYPE_HEADER) {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_section_header, parent, false)
            HeaderViewHolder(view)
        } else {
            val binding = ItemStickerPackBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            PackViewHolder(binding)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is PackItem.Header -> (holder as HeaderViewHolder).tvTitle.text = item.title
            is PackItem.Pack   -> (holder as PackViewHolder).bind(item.stickerPack)
        }
    }

    override fun getItemCount() = items.size

    fun updateItems(newItems: List<PackItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    fun getPacks(): List<StickerPack> =
        items.filterIsInstance<PackItem.Pack>().map { it.stickerPack }

    fun markAsAdded(packId: String) {
        val index = items.indexOfFirst {
            it is PackItem.Pack && it.stickerPack.identifier == packId
        }
        if (index >= 0) {
            (items[index] as PackItem.Pack).stickerPack.isAddedToWhatsApp = true
            notifyItemChanged(index)
        }
    }
}
