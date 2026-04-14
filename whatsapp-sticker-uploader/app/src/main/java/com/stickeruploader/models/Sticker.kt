package com.stickeruploader.models

data class Sticker(
    val imageFileName: String,
    val emojis: List<String> = listOf("😀")
)
