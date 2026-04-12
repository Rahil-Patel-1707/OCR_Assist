package com.example.ocr_assist.translation;
import com.example.ocr_assist.model.Language;


import android.util.Log;

import com.google.mlkit.common.model.DownloadConditions;
import com.google.mlkit.nl.languageid.LanguageIdentification;
import com.google.mlkit.nl.languageid.LanguageIdentifier;
import com.google.mlkit.nl.translate.Translation;
import com.google.mlkit.nl.translate.Translator;
import com.google.mlkit.nl.translate.TranslatorOptions;

public class TranslationHelper {
    private static final String TAG = "TranslationHelper";

    private final LanguageIdentifier languageIdentifier;
    private Translator currentTranslator;

    public interface TranslationCallback {
        void onTranslationProgress(String message);
        void onTranslationSuccess(String translatedText, String detectedLanguage);
        void onTranslationFailure(Exception e);
    }

    public TranslationHelper() {
        this.languageIdentifier = LanguageIdentification.getClient();
    }

    public void translate(String text, String targetLanguageCode, TranslationCallback callback) {
        if (text == null || text.trim().isEmpty()) {
            callback.onTranslationFailure(new Exception("Text is empty"));
            return;
        }

        callback.onTranslationProgress("Detecting language...");
        languageIdentifier.identifyLanguage(text)
                .addOnSuccessListener(languageCode -> {
                    if (languageCode.equals("und")) {
                        callback.onTranslationFailure(new Exception("Could not identify language"));
                    } else {
                        performTranslation(text, languageCode, targetLanguageCode, callback);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Language identification failed", e);
                    callback.onTranslationFailure(e);
                });
    }

    private void performTranslation(String text, String sourceLanguageCode, String targetLanguageCode, TranslationCallback callback) {
        if (sourceLanguageCode.equals(targetLanguageCode)) {
            callback.onTranslationSuccess(text, sourceLanguageCode);
            return;
        }

        TranslatorOptions options = new TranslatorOptions.Builder()
                .setSourceLanguage(sourceLanguageCode)
                .setTargetLanguage(targetLanguageCode)
                .build();

        if (currentTranslator != null) {
            currentTranslator.close();
        }

        currentTranslator = Translation.getClient(options);

        callback.onTranslationProgress("Downloading language models if needed...");
        DownloadConditions conditions = new DownloadConditions.Builder().build();

        currentTranslator.downloadModelIfNeeded(conditions)
                .addOnSuccessListener(unused -> {
                    callback.onTranslationProgress("Translating structured text...");
                    translateLineByLine(text, sourceLanguageCode, callback);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Model download failed", e);
                    callback.onTranslationFailure(e);
                });
    }

    private void translateLineByLine(String text, String sourceLanguageCode, TranslationCallback callback) {
        String[] lines = text.split("\n", -1);
        if (lines.length == 0) {
            callback.onTranslationSuccess("", sourceLanguageCode);
            return;
        }

        final String[] translatedLines = new String[lines.length];
        final java.util.concurrent.atomic.AtomicInteger completedCount = new java.util.concurrent.atomic.AtomicInteger(0);
        final java.util.concurrent.atomic.AtomicBoolean hasError = new java.util.concurrent.atomic.AtomicBoolean(false);

        for (int i = 0; i < lines.length; i++) {
            final int index = i;
            String line = lines[i];

            if (line.trim().isEmpty()) {
                translatedLines[index] = line;
                if (completedCount.incrementAndGet() == lines.length && !hasError.get()) {
                    finishTranslation(translatedLines, sourceLanguageCode, callback);
                }
            } else {
                currentTranslator.translate(line)
                        .addOnSuccessListener(translatedText -> {
                            translatedLines[index] = translatedText;
                            if (completedCount.incrementAndGet() == lines.length && !hasError.get()) {
                                finishTranslation(translatedLines, sourceLanguageCode, callback);
                            }
                        })
                        .addOnFailureListener(e -> {
                            if (hasError.compareAndSet(false, true)) {
                                callback.onTranslationFailure(e);
                            }
                        });
            }
        }
    }

    private void finishTranslation(String[] translatedLines, String sourceLanguageCode, TranslationCallback callback) {
        StringBuilder sb = new StringBuilder();
        for (int j = 0; j < translatedLines.length; j++) {
            sb.append(translatedLines[j]);
            if (j < translatedLines.length - 1) {
                sb.append("\n");
            }
        }
        callback.onTranslationSuccess(sb.toString(), sourceLanguageCode);
    }

    public void close() {
        if (languageIdentifier != null) {
            languageIdentifier.close();
        }
        if (currentTranslator != null) {
            currentTranslator.close();
        }
    }
}



