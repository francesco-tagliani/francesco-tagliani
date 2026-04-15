package com.stickeruploader

import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.stickeruploader.databinding.ItemStickerPackBinding
import com.stickeruploader.models.StickerPack

class StickerPackAdapter(
    private val packs: List<StickerPack>,
    private val onAddClick: (StickerPack) -> Unit
) : RecyclerView.Adapter<StickerPackAdapter.ViewHolder>() {

    companion object {
        private const val TAG = "StickerPackAdapter"
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemStickerPackBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding, onAddClick)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val pack = packs[position]
        Log.d(TAG, "onBindViewHolder() posizione=$position, pack=${pack.identifier}")
        holder.bind(pack)
    }

    override fun getItemCount(): Int = packs.size

    class ViewHolder(
        private val binding: ItemStickerPackBinding,
        private val onAddClick: (StickerPack) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(pack: StickerPack) {
            Log.d(TAG, "bind() pack=${pack.identifier}")

            binding.tvPackName.text = pack.name
            binding.tvPackId.text = "ID: ${pack.identifier}"
            binding.tvPackInfo.text = "${pack.stickers.size} sticker"

            binding.btnAdd.setOnClickListener {
                Log.d(TAG, "btnAdd cliccato per ${pack.identifier}")
                onAddClick(pack)
            }
        }
    }
}
