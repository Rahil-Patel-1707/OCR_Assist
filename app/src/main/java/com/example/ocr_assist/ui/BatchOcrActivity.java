package com.example.ocr_assist.ui;

import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;

import com.example.ocr_assist.databinding.ActivityBatchOcrBinding;
import com.example.ocr_assist.ui.base.BaseActivity;
import com.example.ocr_assist.utils.TranslationHelper;
import com.example.ocr_assist.utils.MultiScriptOcrHelper;
import com.example.ocr_assist.utils.TtsHelper;
import com.google.mlkit.nl.languageid.LanguageIdentification;
import com.google.mlkit.nl.languageid.LanguageIdentifier;
import com.google.mlkit.vision.common.InputImage;


import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BatchOcrActivity extends BaseActivity<ActivityBatchOcrBinding> {

    private static final String TAG = "BatchOcrActivity";

    // OCR & translation
    private BatchOcrAdapter adapter;
    private ExecutorService ocrExecutor;
    private TranslationHelper translationHelper;

    // TTS — initialised ONCE in onCreate, shared across all list items
    private TtsHelper ttsHelper;
    private boolean ttsReady = false;
    private int speakingPosition = -1;          // which item is currently being spoken
    private String detectedLangCode = "en";      // updated by ML Kit language ID

    private final ActivityResultLauncher<String> pickMultipleImages =
            registerForActivityResult(new ActivityResultContracts.GetMultipleContents(), uris -> {
                if (uris != null && !uris.isEmpty()) {
                    processImages(uris);
                } else {
                    Toast.makeText(this, "No images selected", Toast.LENGTH_SHORT).show();
                }
            });

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------
    @NonNull
    @Override
    protected ActivityBatchOcrBinding getViewBinding(@NonNull LayoutInflater inflater) {
        return ActivityBatchOcrBinding.inflate(inflater);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Toolbar
        getBinding().topAppBar.setNavigationOnClickListener(v -> finish());

        // Adapter
        adapter = new BatchOcrAdapter(
                this::onTranslateClick,
                this::onListenClick,
                position -> stopCurrentSpeech()   // Stop lambda: immediately halts TTS
        );
        getBinding().recyclerView.setAdapter(adapter);

        // Translation
        translationHelper = new TranslationHelper();

        // Sequential executor — prevents memory spikes when loading many images
        ocrExecutor = Executors.newSingleThreadExecutor();

        // ── TTS: warm up the engine now so it is ready when the user taps Listen
        initTts();

        // Pre-warm all script OCR recognizers
        MultiScriptOcrHelper.init();

        getBinding().btnSelectImages.setOnClickListener(v -> pickMultipleImages.launch("image/*"));
    }

    /**
     * Initialise TTS once. The onReady() callback marks the engine as ready.
     * All speak() calls inside onListenClick are guarded by ttsReady.
     */
    private void initTts() {
        ttsHelper = new TtsHelper(this, new TtsHelper.TtsListener() {

            @Override
            public void onReady() {
                ttsReady = true;
                // No UI change needed here; listen buttons are always visible
            }

            @Override
            public void onProgress(int current, int total) {
                // Could display progress in the list item — not required for MVP
            }

            @Override
            public void onComplete() {
                // Speech finished naturally → reset the button of the item that was speaking
                runOnUiThread(() -> {
                    if (speakingPosition >= 0) {
                        adapter.setSpeakingState(speakingPosition, false);
                        speakingPosition = -1;
                    }
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    Toast.makeText(BatchOcrActivity.this, message, Toast.LENGTH_SHORT).show();
                    if (speakingPosition >= 0) {
                        adapter.setSpeakingState(speakingPosition, false);
                        speakingPosition = -1;
                    }
                });
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Stop speaking when the user leaves the screen
        stopCurrentSpeech();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (ocrExecutor != null) ocrExecutor.shutdown();
        MultiScriptOcrHelper.close();
        if (translationHelper != null) translationHelper.close();
        if (ttsHelper != null) ttsHelper.destroy();
    }

    // -------------------------------------------------------------------------
    // TTS helpers
    // -------------------------------------------------------------------------

    /**
     * Called when the user taps the Listen button on any list item.
     * Behaviour:
     *  - If this item is already speaking → stop it (toggle off).
     *  - If a different item was speaking → stop that first, then start this one.
     *  - If nothing was speaking → detect language and start.
     */
    private void onListenClick(int position, String textToRead) {
        if (!ttsReady || ttsHelper == null) {
            Toast.makeText(this, "TTS engine is still initialising. Please wait.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (textToRead == null || textToRead.trim().isEmpty() || textToRead.equals("No text found")) {
            Toast.makeText(this, "No text available to read.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (ttsHelper.isSpeaking() && speakingPosition == position) {
            // Tapping the same item again → stop
            stopCurrentSpeech();
        } else {
            // Stop any previously speaking item, then start this one
            stopCurrentSpeech();

            speakingPosition = position;
            adapter.setSpeakingState(position, true);

            // Auto-detect language, then speak
            LanguageIdentifier identifier = LanguageIdentification.getClient();
            identifier.identifyLanguage(textToRead)
                    .addOnSuccessListener(langCode -> {
                        if (!"und".equals(langCode)) detectedLangCode = langCode;
                        ttsHelper.play(textToRead, detectedLangCode);
                    })
                    .addOnFailureListener(e -> ttsHelper.play(textToRead, detectedLangCode));
        }
    }

    /** Stop speech and reset the speaking item's state. */
    private void stopCurrentSpeech() {
        if (ttsHelper != null) ttsHelper.stop();
        if (speakingPosition >= 0) {
            adapter.setSpeakingState(speakingPosition, false);
            speakingPosition = -1;
        }
    }

    // -------------------------------------------------------------------------
    // Translation
    // -------------------------------------------------------------------------
    private void onTranslateClick(int position, BatchOcrResult result) {
        String textToTranslate = result.getExtractedText();
        if (textToTranslate == null || textToTranslate.isEmpty() || textToTranslate.equals("No text found")) {
            Toast.makeText(this, "No valid text to translate", Toast.LENGTH_SHORT).show();
            return;
        }

        LanguageSelectionBottomSheet bottomSheet = LanguageSelectionBottomSheet.newInstance();
        bottomSheet.setLanguageSelectedListener((languageCode, languageName) ->
                translateTextForItem(position, textToTranslate, languageCode)
        );
        bottomSheet.show(getSupportFragmentManager(), "LanguageSelectionBottomSheet");
    }

    private void translateTextForItem(int position, String text, String targetCode) {
        adapter.showTranslationProgress(position);

        translationHelper.translate(text, targetCode, new TranslationHelper.TranslationCallback() {
            @Override public void onTranslationProgress(String message) {}

            @Override
            public void onTranslationSuccess(String translatedText, String detectedLanguage) {
                runOnUiThread(() ->
                        adapter.updateTranslation(position,
                                "Translated (" + detectedLanguage + "): \n" + translatedText)
                );
            }

            @Override
            public void onTranslationFailure(Exception e) {
                runOnUiThread(() -> {
                    adapter.updateTranslation(position, "");
                    Toast.makeText(BatchOcrActivity.this, "Translation failed for item", Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    // -------------------------------------------------------------------------
    // Image processing
    // -------------------------------------------------------------------------
    private void processImages(List<Uri> uris) {
        adapter.clearResults();

        getBinding().progressBar.setVisibility(View.VISIBLE);
        getBinding().textProgress.setVisibility(View.VISIBLE);
        getBinding().btnSelectImages.setEnabled(false);

        ocrExecutor.execute(() -> {
            for (int i = 0; i < uris.size(); i++) {
                final Uri imageUri = uris.get(i);
                final int idx = i + 1;
                final int total = uris.size();

                runOnUiThread(() -> getBinding().textProgress.setText("Processing " + idx + " of " + total));

                try {
                    InputImage image = InputImage.fromFilePath(this, imageUri);
                    
                    // Use all-script parallel recognition for maximum language coverage
                    String processedText = MultiScriptOcrHelper.process(image, false);
                    final BatchOcrResult result = new BatchOcrResult(imageUri, processedText);
                    runOnUiThread(() -> adapter.addResult(result));

                } catch (IOException | InterruptedException | java.util.concurrent.ExecutionException e) {
                    Log.e(TAG, "Failed to process image: " + imageUri, e);
                }
            }

            runOnUiThread(() -> {
                getBinding().progressBar.setVisibility(View.GONE);
                getBinding().textProgress.setVisibility(View.GONE);
                getBinding().btnSelectImages.setEnabled(true);
                Toast.makeText(this, "Batch processing complete", Toast.LENGTH_SHORT).show();
            });
        });
    }
}
