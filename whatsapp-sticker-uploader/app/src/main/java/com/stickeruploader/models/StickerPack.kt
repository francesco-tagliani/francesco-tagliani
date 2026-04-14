package com.stickeruploader.models

data class StickerPack(
    val identifier: String,        // es. "pack_001"
    val name: String,              // es. "Sticker Pack 1"
    val publisher: String,
    val trayImageFile: String,     // nome file dell'icona del pack (primo sticker)
    val stickers: List<Sticker>,
    var isAddedToWhatsApp: Boolean = false
)
