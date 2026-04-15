package com.stickeruploader.models;

import java.util.List;

public class Sticker {
    public final String imageFileName;
    public final List<String> emojis;
    public final String accessibilityText;

    public Sticker(String imageFileName, List<String> emojis, String accessibilityText) {
        this.imageFileName = imageFileName;
        this.emojis = emojis;
        this.accessibilityText = accessibilityText;
    }
}
