package com.example.ocr_assist.utils;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;

import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.nl.languageid.LanguageIdentification;
import com.google.mlkit.nl.languageid.LanguageIdentifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * TtsHelper wraps Android's TextToSpeech engine and provides robust,
 * multi-language, continuous queued playback with sentence-level progress tracking.
 */
public class TtsHelper {
    private static final String TAG = "TtsHelper";

    private TextToSpeech tts;
    private boolean isTtsReady = false;
    private boolean isSpeaking = false;
    private boolean isPaused = false;

    private final List<TtsSegment> segments = new ArrayList<>();
    private int currentIndex = 0;
    private String lastText = "";

    private final TtsListener listener;
    private final ExecutorService parserExecutor;
    private final Handler mainHandler;
    private final AtomicLong playToken = new AtomicLong(0);

    public interface TtsListener {
        void onProgress(int current, int total);
        void onComplete();
        void onError(String message);
        void onReady();
    }

    public TtsHelper(Context context, TtsListener listener) {
        this.listener = listener;
        this.parserExecutor = Executors.newSingleThreadExecutor();
        this.mainHandler = new Handler(Looper.getMainLooper());

        tts = new TextToSpeech(context.getApplicationContext(), status -> {
            if (status == TextToSpeech.SUCCESS) {
                isTtsReady = true;
                if (this.listener != null) this.listener.onReady();

                tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override
                    public void onStart(String id) {
                        if (id.startsWith("pause_")) return;
                        try {
                            currentIndex = Integer.parseInt(id);
                            if (TtsHelper.this.listener != null) {
                                mainHandler.post(() -> TtsHelper.this.listener.onProgress(currentIndex, segments.size()));
                            }
                        } catch (NumberFormatException ignored) {}
                    }

                    @Override
                    public void onDone(String id) {
                        if (id.startsWith("pause_")) return;
                        try {
                            int finishedId = Integer.parseInt(id);
                            // If this is the last segment and we are not paused, trigger complete
                            if (finishedId == segments.size() - 1 && isSpeaking && !isPaused) {
                                isSpeaking = false;
                                isPaused = false;
                                if (TtsHelper.this.listener != null) {
                                    mainHandler.post(TtsHelper.this.listener::onComplete);
                                }
                            }
                        } catch (NumberFormatException ignored) {}
                    }

                    @Override
                    public void onError(String id) {
                        if (id.startsWith("pause_")) return;
                        isSpeaking = false;
                        if (TtsHelper.this.listener != null) {
                            mainHandler.post(() -> TtsHelper.this.listener.onError("TTS playback error on segment " + id));
                        }
                    }
                });
            } else {
                if (this.listener != null) this.listener.onError("TTS initialisation failed (status=" + status + ").");
            }
        });
    }

    /**
     * Start reading {@code text} aloud. Dynamically parses languages per sentence.
     * Starts a background task to process the text.
     */
    public void play(String text, String fallbackLanguageCode) {
        if (!isTtsReady) {
            if (listener != null) listener.onError("TTS not ready yet. Please try again.");
            return;
        }
        if (text == null || text.trim().isEmpty()) {
            if (listener != null) listener.onError("No text to speak.");
            return;
        }

        if (text.equals(lastText) && !segments.isEmpty()) {
            // Text is unchanged; just restart from beginning if not paused
            isSpeaking = true;
            isPaused = false;
            currentIndex = 0;
            speakFromCurrentIndex();
            return;
        }

        lastText = text;
        tts.stop(); // Stop anything current
        isSpeaking = true;
        isPaused = false;
        
        final long currentToken = playToken.incrementAndGet();

        // Run parsing asynchronously to avoid blocking UI
        parserExecutor.execute(() -> {
            try {
                // 1. Preprocess: clean unwanted symbols/excessive space while keeping punctuation/linebreaks
                String cleanText = text.replaceAll("[*_\\{\\}\\[\\]]", "").replaceAll(" +", " ").trim();

                // 2. Split into sentences/chunks by punctuation and line breaks
                String[] parts = cleanText.split("(?<=[.!?])\\s+|\\n+");

                LanguageIdentifier identifier = LanguageIdentification.getClient();
                List<TtsSegment> newSegments = new ArrayList<>();
                String currentLang = fallbackLanguageCode != null ? fallbackLanguageCode : "en";

                for (int i = 0; i < parts.length; i++) {
                    if (playToken.get() != currentToken || !isSpeaking) return; // Abort if stopped

                    String part = parts[i].trim();
                    if (part.isEmpty()) continue;

                    // Detect Language synchronously on this background thread
                    String detected = "und";
                    try {
                        detected = Tasks.await(identifier.identifyLanguage(part));
                    } catch (Exception ignored) {}

                    if (!"und".equals(detected)) {
                        currentLang = detected;
                    }

                    // Configuration Logic: Pitch and Speed optimizations
                    float pitch = 1.0f;
                    float speed = 1.0f;
                    if (currentLang.startsWith("hi") || currentLang.startsWith("bn") || currentLang.startsWith("zh")) {
                        speed = 0.9f; // slightly slower for complex scripts to sound more natural
                    } else if (currentLang.startsWith("en")) {
                        speed = 1.0f;
                    }

                    boolean isLangSwitch = false;
                    if (!newSegments.isEmpty()) {
                        isLangSwitch = !newSegments.get(newSegments.size() - 1).languageCode.equals(currentLang);
                    }

                    newSegments.add(new TtsSegment(newSegments.size(), part, currentLang, pitch, speed, isLangSwitch));
                }

                if (playToken.get() != currentToken || !isSpeaking) return; // Abort if stopped

                segments.clear();
                segments.addAll(newSegments);
                currentIndex = 0;

                // Start playback on main thread
                mainHandler.post(() -> {
                    if (playToken.get() == currentToken && isSpeaking) {
                        speakFromCurrentIndex();
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "Error parsing TTS segments", e);
                mainHandler.post(() -> {
                    isSpeaking = false;
                    if (listener != null) listener.onError("Failed to parse text and auto-detect languages.");
                });
            }
        });
    }

    /** Pause currently queued speech. Allows resume later. */
    public void pause() {
        if (tts != null && isSpeaking) {
            tts.stop(); // flushes the queue
            isSpeaking = false;
            isPaused = true;
        }
    }

    /** Resumes playback from where it was paused (currentIndex). */
    public void resume() {
        if (isPaused && !segments.isEmpty()) {
            isPaused = false;
            isSpeaking = true;
            speakFromCurrentIndex();
        }
    }

    /** Stops completely and invalidates queue position. */
    public void stop() {
        playToken.incrementAndGet(); // Invalidate any running threads
        if (tts != null) {
            tts.stop();
        }
        isSpeaking = false;
        isPaused = false;
        currentIndex = 0;
        segments.clear();
        lastText = "";
        mainHandler.post(() -> {
            if (listener != null) listener.onComplete();
        });
    }

    public boolean isSpeaking() { return isSpeaking; }
    public boolean isPaused()   { return isPaused; }
    public boolean isReady()    { return isTtsReady; }

    public void destroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
            tts = null;
        }
        if (parserExecutor != null) {
            parserExecutor.shutdownNow();
        }
        isTtsReady = false;
    }

    // -------------------------------------------------------------------------
    // Private Queue Execution
    // -------------------------------------------------------------------------

    private void speakFromCurrentIndex() {
        if (!isSpeaking || tts == null || segments.isEmpty() || currentIndex >= segments.size()) return;

        tts.stop(); // Safety flush before enqueuing sequence

        for (int i = currentIndex; i < segments.size(); i++) {
            TtsSegment seg = segments.get(i);

            // 1. Language Setup with English Fallback
            Locale locale = new Locale(seg.languageCode);
            int langResult = tts.setLanguage(locale);
            if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts.setLanguage(Locale.ENGLISH); // Fallback if system missing the voice data
            }

            // 2. Adjust Pitch/Speed limits
            tts.setPitch(seg.pitch);
            tts.setSpeechRate(seg.speed);

            // 3. Optional Multi-Lang transition delay
            if (seg.isLanguageSwitch) {
                // adding a natural pause for context switching
                tts.playSilentUtterance(500, TextToSpeech.QUEUE_ADD, "pause_" + seg.id);
            }

            // 4. Submit to queue
            Bundle params = new Bundle();
            params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, String.valueOf(seg.id));
            tts.speak(seg.text, TextToSpeech.QUEUE_ADD, params, String.valueOf(seg.id));
        }
    }
}
