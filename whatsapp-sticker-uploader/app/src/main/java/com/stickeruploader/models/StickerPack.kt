package com.stickeruploader.models

data class StickerPack(
    val identifier: String,
    val name: String,
    val publisher: String,
    val trayImageFile: String,
    val stickers: List<Sticker>,
    val isAnimated: Boolean = false,
    var isAddedToWhatsApp: Boolean = false
)
