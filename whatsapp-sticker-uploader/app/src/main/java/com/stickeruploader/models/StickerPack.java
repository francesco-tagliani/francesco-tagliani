package com.stickeruploader.models;

import java.util.List;

public class StickerPack {
    public final String identifier;
    public final String name;
    public final String publisher;
    public final String trayImageFile;
    public final String publisherEmail;
    public final String publisherWebsite;
    public final String privacyPolicyWebsite;
    public final String licenseAgreementWebsite;
    public final String imageDataVersion;
    public final boolean avoidCache;
    public final boolean animatedStickerPack;
    public final List<Sticker> stickers;

    public StickerPack(
            String identifier,
            String name,
            String publisher,
            String trayImageFile,
            String publisherEmail,
            String publisherWebsite,
            String privacyPolicyWebsite,
            String licenseAgreementWebsite,
            String imageDataVersion,
            boolean avoidCache,
            boolean animatedStickerPack,
            List<Sticker> stickers) {
        this.identifier = identifier;
        this.name = name;
        this.publisher = publisher;
        this.trayImageFile = trayImageFile;
        this.publisherEmail = publisherEmail;
        this.publisherWebsite = publisherWebsite;
        this.privacyPolicyWebsite = privacyPolicyWebsite;
        this.licenseAgreementWebsite = licenseAgreementWebsite;
        this.imageDataVersion = imageDataVersion;
        this.avoidCache = avoidCache;
        this.animatedStickerPack = animatedStickerPack;
        this.stickers = stickers;
    }
}
