package com.example.ocr_assist.ui;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.content.ContextCompat;

import com.example.ocr_assist.databinding.ActivityRealTimeOcrBinding;
import com.example.ocr_assist.ui.base.BaseActivity;
import com.example.ocr_assist.utils.TranslationHelper;
import com.example.ocr_assist.utils.MultiScriptOcrHelper;
import com.example.ocr_assist.utils.TtsHelper;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.nl.languageid.LanguageIdentification;
import com.google.mlkit.nl.languageid.LanguageIdentifier;
import com.google.mlkit.vision.common.InputImage;


import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RealTimeOcrActivity extends BaseActivity<ActivityRealTimeOcrBinding> {

    private static final String TAG = "RealTimeOcrActivity";

    // Camera & OCR
    private ExecutorService cameraExecutor;

    private ImageCapture imageCapture;
    private ProcessCameraProvider cameraProvider;

    // Translation
    private TranslationHelper translationHelper;

    // TTS — initialised ONCE in onCreate, used everywhere
    private TtsHelper ttsHelper;
    private boolean ttsReady = false;   // guard: engine ready?
    private String detectedLangCode = "en";  // updated by ML Kit language ID

    // -------------------------------------------------------------------------
    // Permission launcher
    // -------------------------------------------------------------------------
    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    startCamera();
                } else {
                    Toast.makeText(this, "Camera permission required.", Toast.LENGTH_SHORT).show();
                    finish();
                }
            });

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------
    @NonNull
    @Override
    protected ActivityRealTimeOcrBinding getViewBinding(@NonNull LayoutInflater inflater) {
        return ActivityRealTimeOcrBinding.inflate(inflater);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        cameraExecutor = Executors.newSingleThreadExecutor();
       // textRecognizer = MultiScriptOcrHelper.getLatinRecognizer(); // for live frames only
        translationHelper = new TranslationHelper();

        // Pre-warm ALL script recognizers so they are ready when the user captures a photo
        MultiScriptOcrHelper.init();

        // ── TTS: initialise once here so it is warm by the time the user taps Listen
        initTts();

        setupButtons();

        if (allPermissionsGranted()) {
            startCamera();
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    /**
     * Initialise the TTS engine.  The onReady() callback marks the engine as
     * ready; all speak() calls are guarded by ttsReady.
     */
    private void initTts() {
        ttsHelper = new TtsHelper(this, new TtsHelper.TtsListener() {
            @Override
            public void onReady() {
                ttsReady = true;
                // Update button to "ready" state on UI thread
                runOnUiThread(() ->
                    getBinding().btnListen.setEnabled(true)
                );
            }

            @Override
            public void onProgress(int current, int total) {
                // Optional: could show sentence progress here
            }

            @Override
            public void onComplete() {
                // Reset Listen button icon to "play" state
                runOnUiThread(() ->
                    getBinding().btnListen.setText("Listen")
                );
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    Toast.makeText(RealTimeOcrActivity.this, message, Toast.LENGTH_SHORT).show();
                    getBinding().btnListen.setText("Listen");
                });
            }
        });

        // Disable until ready (will be re-enabled in onReady callback)
        getBinding().btnListen.setEnabled(false);
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Stop speaking when leaving the screen
        if (ttsHelper != null) {
            ttsHelper.stop();
            getBinding().btnListen.setText("Listen");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();
       // textRecognizer.close();
        MultiScriptOcrHelper.close();
        if (translationHelper != null) translationHelper.close();
        if (ttsHelper != null) ttsHelper.destroy();
    }

    // -------------------------------------------------------------------------
    // Button wiring
    // -------------------------------------------------------------------------
    private void setupButtons() {
        // Capture
        getBinding().btnCapture.setOnClickListener(v -> capturePhoto());

        // Retake – back to live camera
        getBinding().btnRetake.setOnClickListener(v -> {
            // Stop any active TTS so it doesn't keep reading stale text
            if (ttsHelper != null) {
                ttsHelper.stop();
                getBinding().btnListen.setText("Listen");
            }
            getBinding().cardResult.setVisibility(View.GONE);
            getBinding().textFinalResult.setVisibility(View.GONE);
            getBinding().textTranslatedResult.setVisibility(View.GONE);
            getBinding().btnCapture.setVisibility(View.VISIBLE);
            getBinding().textLiveOverlay.setVisibility(View.VISIBLE);
            getBinding().textLiveOverlay.setText("");
            startCamera();
        });

        // Translate
        getBinding().btnTranslate.setOnClickListener(v -> {
            String text = getBinding().textFinalResult.getText().toString();
            if (text.isEmpty() || text.equals("No text detected.") || text.equals("Failed to extract text.")) {
                Toast.makeText(this, "No valid text to translate", Toast.LENGTH_SHORT).show();
                return;
            }
            showLanguageSelectionDialog(text);
        });

        // Copy
        getBinding().btnCopy.setOnClickListener(v -> {
            String text = getBinding().textFinalResult.getText().toString();
            if (!text.isEmpty()) {
                ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("OCR Result", text);
                clipboard.setPrimaryClip(clip);
                Toast.makeText(this, "Text copied to clipboard", Toast.LENGTH_SHORT).show();
            }
        });

        // Share
        getBinding().btnShare.setOnClickListener(v -> {
            String text = getBinding().textFinalResult.getText().toString();
            String translated = getBinding().textTranslatedResult.getText().toString();
            if (getBinding().textTranslatedResult.getVisibility() == View.VISIBLE) {
                text += "\n\n" + translated;
            }
            if (!text.isEmpty()) {
                Intent sendIntent = new Intent();
                sendIntent.setAction(Intent.ACTION_SEND);
                sendIntent.putExtra(Intent.EXTRA_TEXT, text);
                sendIntent.setType("text/plain");
                startActivity(Intent.createChooser(sendIntent, "Share OCR Result"));
            }
        });

        // ── Listen / Stop toggle ──────────────────────────────────────────────
        getBinding().btnListen.setOnClickListener(v -> {
            if (!ttsReady || ttsHelper == null) {
                Toast.makeText(this, "TTS engine is still initialising. Please wait.", Toast.LENGTH_SHORT).show();
                return;
            }

            if (ttsHelper.isSpeaking()) {
                // Currently speaking → Stop
                ttsHelper.stop();
                getBinding().btnListen.setText("Listen");
            } else {
                // Not speaking → determine text and start
                String textToSpeak = resolveTextToSpeak();
                if (textToSpeak.isEmpty()) {
                    Toast.makeText(this, "No text available to read.", Toast.LENGTH_SHORT).show();
                    return;
                }
                getBinding().btnListen.setText("Stop");
                // Auto-detect language then speak
                speakWithLanguageDetection(textToSpeak);
            }
        });
    }

    /**
     * Returns the best available text: translated text (stripped of label)
     * when visible, otherwise the raw OCR result.
     */
    private String resolveTextToSpeak() {
        if (getBinding().textTranslatedResult.getVisibility() == View.VISIBLE) {
            String raw = getBinding().textTranslatedResult.getText().toString();
            // Strip the "Translated (xx): \n" prefix if present
            int newline = raw.indexOf('\n');
            if (newline >= 0 && newline < raw.length() - 1) {
                return raw.substring(newline + 1).trim();
            }
            return raw.trim();
        }
        return getBinding().textFinalResult.getText().toString().trim();
    }

    /**
     * Detect language of {@code text} with ML Kit then call ttsHelper.play().
     * Falls back to the last detected language (or English) on failure.
     */
    private void speakWithLanguageDetection(String text) {
        LanguageIdentifier identifier = LanguageIdentification.getClient();
        identifier.identifyLanguage(text)
                .addOnSuccessListener(langCode -> {
                    if (!"und".equals(langCode)) {
                        detectedLangCode = langCode;
                    }
                    ttsHelper.play(text, detectedLangCode);
                })
                .addOnFailureListener(e -> {
                    // Use last known / default language
                    ttsHelper.play(text, detectedLangCode);
                });
    }

    // -------------------------------------------------------------------------
    // Translation
    // -------------------------------------------------------------------------
    private void showLanguageSelectionDialog(String textToTranslate) {
        LanguageSelectionBottomSheet bottomSheet = LanguageSelectionBottomSheet.newInstance();
        bottomSheet.setLanguageSelectedListener((languageCode, languageName) ->
                translateText(textToTranslate, languageCode)
        );
        bottomSheet.show(getSupportFragmentManager(), "LanguageSelectionBottomSheet");
    }

    private void translateText(String text, String targetCode) {
        getBinding().progressProcessing.setVisibility(View.VISIBLE);
        getBinding().textTranslatedResult.setVisibility(View.GONE);
        getBinding().btnTranslate.setEnabled(false);

        translationHelper.translate(text, targetCode, new TranslationHelper.TranslationCallback() {
            @Override public void onTranslationProgress(String message) {}

            @Override
            public void onTranslationSuccess(String translatedText, String detectedLanguage) {
                runOnUiThread(() -> {
                    getBinding().progressProcessing.setVisibility(View.GONE);
                    getBinding().btnTranslate.setEnabled(true);
                    getBinding().textTranslatedResult.setVisibility(View.VISIBLE);
                    getBinding().textTranslatedResult.setText(
                            "Translated (" + detectedLanguage + "): \n" + translatedText);
                });
            }

            @Override
            public void onTranslationFailure(Exception e) {
                runOnUiThread(() -> {
                    getBinding().progressProcessing.setVisibility(View.GONE);
                    getBinding().btnTranslate.setEnabled(true);
                    Toast.makeText(RealTimeOcrActivity.this, "Translation failed", Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    // -------------------------------------------------------------------------
    // Camera
    // -------------------------------------------------------------------------
    private boolean allPermissionsGranted() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try {
                cameraProvider = future.get();

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(getBinding().viewFinder.getSurfaceProvider());

                imageCapture = new ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build();

                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();
                imageAnalysis.setAnalyzer(cameraExecutor, this::processLiveImageProxy);

                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA,
                        preview, imageCapture, imageAnalysis);

            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "Use case binding failed", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void capturePhoto() {
        if (imageCapture == null) return;

        // Flash animation
        getBinding().flashView.setVisibility(View.VISIBLE);
        getBinding().flashView.setAlpha(1f);
        getBinding().flashView.animate().alpha(0f).setDuration(300)
                .withEndAction(() -> getBinding().flashView.setVisibility(View.GONE)).start();

        // UI state: hide capture button, show result card loading
        getBinding().btnCapture.setVisibility(View.GONE);
        getBinding().textLiveOverlay.setVisibility(View.GONE);
        getBinding().cardResult.setVisibility(View.VISIBLE);
        getBinding().progressProcessing.setVisibility(View.VISIBLE);
        getBinding().textFinalResult.setVisibility(View.GONE);
        getBinding().textTranslatedResult.setVisibility(View.GONE);

        imageCapture.takePicture(ContextCompat.getMainExecutor(this),
                new ImageCapture.OnImageCapturedCallback() {

            @OptIn(markerClass = ExperimentalGetImage.class)
            @Override
            public void onCaptureSuccess(@NonNull ImageProxy imageProxy) {
                if (cameraProvider != null) cameraProvider.unbindAll();

                if (imageProxy.getImage() != null) {
                    InputImage image = InputImage.fromMediaImage(
                            imageProxy.getImage(),
                            imageProxy.getImageInfo().getRotationDegrees());

                    cameraExecutor.execute(() -> {
                        try {
                            String processed = MultiScriptOcrHelper.process(image, false);
                            runOnUiThread(() -> {
                                imageProxy.close();
                                getBinding().progressProcessing.setVisibility(View.GONE);
                                getBinding().textFinalResult.setVisibility(View.VISIBLE);
                                getBinding().textFinalResult.setText(
                                        processed.isEmpty() ? "No text detected." : processed);
                            });
                        } catch (Exception e) {
                            runOnUiThread(() -> {
                                imageProxy.close();
                                getBinding().progressProcessing.setVisibility(View.GONE);
                                getBinding().textFinalResult.setVisibility(View.VISIBLE);
                                getBinding().textFinalResult.setText("Failed to extract text.");
                            });
                        }
                    });
                } else {
                    imageProxy.close();
                }
            }

            @Override
            public void onError(@NonNull ImageCaptureException exception) {
                Log.e(TAG, "Photo capture failed: " + exception.getMessage(), exception);
                getBinding().progressProcessing.setVisibility(View.GONE);
                Toast.makeText(RealTimeOcrActivity.this, "Capture Failed", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @OptIn(markerClass = ExperimentalGetImage.class)
    private void processLiveImageProxy(ImageProxy imageProxy) {
        if (imageProxy.getImage() == null) { imageProxy.close(); return; }

        InputImage image = InputImage.fromMediaImage(
                imageProxy.getImage(), imageProxy.getImageInfo().getRotationDegrees());

    }
}
