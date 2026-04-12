package com.example.ocr_assist.ui;
import com.example.ocr_assist.model.Language;


import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.example.ocr_assist.databinding.ActivityTranslationBinding;
import com.example.ocr_assist.ui.base.BaseActivity;
import com.google.mlkit.common.model.DownloadConditions;
import com.google.mlkit.nl.languageid.LanguageIdentification;
import com.google.mlkit.nl.languageid.LanguageIdentifier;
import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.nl.translate.Translation;
import com.google.mlkit.nl.translate.Translator;
import com.google.mlkit.nl.translate.TranslatorOptions;

import java.util.HashMap;
import java.util.Map;

public class TranslationActivity extends BaseActivity<ActivityTranslationBinding> {

    private static final String TAG = "TranslationActivity";
    private LanguageIdentifier languageIdentifier;
    private Translator currentTranslator;

    private final Map<String, String> targetLanguages = new HashMap<>();
    private String[] languageNames;

    @NonNull
    @Override
    protected ActivityTranslationBinding getViewBinding(@NonNull LayoutInflater inflater) {
        return ActivityTranslationBinding.inflate(inflater);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getBinding().topAppBar.setNavigationOnClickListener(v -> finish());

        languageIdentifier = LanguageIdentification.getClient();

        setupTargetLanguages();

        getBinding().btnTranslate.setOnClickListener(v -> {
            String sourceText = getBinding().editSourceText.getText().toString().trim();
            if (sourceText.isEmpty()) {
                Toast.makeText(this, "Please enter text to translate", Toast.LENGTH_SHORT).show();
                return;
            }
            identifyAndTranslate(sourceText);
        });
    }

    private void setupTargetLanguages() {
        // Simplified mapping for the spinner
        targetLanguages.put("Spanish", TranslateLanguage.SPANISH);
        targetLanguages.put("French", TranslateLanguage.FRENCH);
        targetLanguages.put("German", TranslateLanguage.GERMAN);
        targetLanguages.put("Italian", TranslateLanguage.ITALIAN);
        targetLanguages.put("Japanese", TranslateLanguage.JAPANESE);
        targetLanguages.put("Chinese", TranslateLanguage.CHINESE);
        targetLanguages.put("English", TranslateLanguage.ENGLISH);

        languageNames = targetLanguages.keySet().toArray(new String[0]);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, languageNames);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        getBinding().spinnerTargetLanguage.setAdapter(adapter);
    }

    private void identifyAndTranslate(String text) {
        getBinding().btnTranslate.setEnabled(false);
        getBinding().textDetectedLanguage.setText("Detecting language...");

        languageIdentifier.identifyLanguage(text)
                .addOnSuccessListener(languageCode -> {
                    if (languageCode.equals("und")) {
                        getBinding().textDetectedLanguage.setText("Language could not be identified.");
                        getBinding().btnTranslate.setEnabled(true);
                    } else {
                        getBinding().textDetectedLanguage.setText("Detected Language: " + languageCode);
                        translateText(text, languageCode);
                    }
                })
                .addOnFailureListener(e -> {
                    getBinding().textDetectedLanguage.setText("Language detection failed.");
                    Log.e(TAG, "Language identification error", e);
                    getBinding().btnTranslate.setEnabled(true);
                });
    }

    private void translateText(String text, String sourceLanguageCode) {
        String selectedLanguageName = (String) getBinding().spinnerTargetLanguage.getSelectedItem();
        String targetLanguageCode = targetLanguages.get(selectedLanguageName);

        if (sourceLanguageCode.equals(targetLanguageCode)) {
            getBinding().textTranslated.setText(text);
            getBinding().btnTranslate.setEnabled(true);
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

        getBinding().progressBar.setVisibility(View.VISIBLE);
        getBinding().textProgress.setVisibility(View.VISIBLE);
        getBinding().textProgress.setText("Downloading/Checking language models...");

        DownloadConditions conditions = new DownloadConditions.Builder().build();
        currentTranslator.downloadModelIfNeeded(conditions)
                .addOnSuccessListener(unused -> {
                    getBinding().textProgress.setText("Translating...");
                    currentTranslator.translate(text)
                            .addOnSuccessListener(translatedText -> {
                                getBinding().textTranslated.setText(translatedText);
                                resetUi();
                            })
                            .addOnFailureListener(e -> {
                                Toast.makeText(this, "Translation failed", Toast.LENGTH_SHORT).show();
                                Log.e(TAG, "Translation error", e);
                                resetUi();
                            });
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Model download failed", Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "Model download error", e);
                    resetUi();
                });
    }

    private void resetUi() {
        getBinding().progressBar.setVisibility(View.GONE);
        getBinding().textProgress.setVisibility(View.GONE);
        getBinding().btnTranslate.setEnabled(true);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (currentTranslator != null) {
            currentTranslator.close();
        }
        if (languageIdentifier != null) {
            languageIdentifier.close();
        }
    }
}



