package com.example.ocr_assist.ui;

import android.net.Uri;

public class BatchOcrResult {
    private Uri imageUri;
    private String extractedText;
    private String translatedText;
    private boolean isSpeaking = false; // Track per-item active TTS state

    public BatchOcrResult(Uri imageUri, String extractedText) {
        this.imageUri = imageUri;
        this.extractedText = extractedText;
        this.translatedText = "";
    }

    public Uri getImageUri() {
        return imageUri;
    }

    public String getExtractedText() {
        return extractedText;
    }

    public String getTranslatedText() {
        return translatedText;
    }

    public void setTranslatedText(String translatedText) {
        this.translatedText = translatedText;
    }

    public boolean isSpeaking() {
        return isSpeaking;
    }

    public void setSpeaking(boolean speaking) {
        this.isSpeaking = speaking;
    }
}
