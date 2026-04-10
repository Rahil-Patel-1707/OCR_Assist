package com.example.ocr_assist.utils;

import android.content.Context;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions;
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions;
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions;
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * MultiScriptOcrHelper runs ALL supported ML Kit text recognizers against a
 * single image in parallel and merges their results.  Merging is done by
 * de-duplicating text blocks that point to identical bounding-box areas across
 * recognizer outputs, keeping the version with the richer character set.
 *
 * Supported scripts: Latin, Chinese, Devanagari (Hindi + others), Japanese, Korean.
 *
 * Usage (background thread only — Tasks.await() is a blocking call):
 *   String text = MultiScriptOcrHelper.process(image, showLabels);
 */
public class MultiScriptOcrHelper {

    // Reusable recognizers — create once, reuse across calls.
    private static TextRecognizer latinRecognizer;
    private static TextRecognizer chineseRecognizer;
    private static TextRecognizer devanagariRecognizer;
    private static TextRecognizer japaneseRecognizer;
    private static TextRecognizer koreanRecognizer;

    /** Call once in your Activity/Fragment onCreate() to pre-warm all engines. */
    public static void init() {
        latinRecognizer      = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        chineseRecognizer    = TextRecognition.getClient(new ChineseTextRecognizerOptions.Builder().build());
        devanagariRecognizer = TextRecognition.getClient(new DevanagariTextRecognizerOptions.Builder().build());
        japaneseRecognizer   = TextRecognition.getClient(new JapaneseTextRecognizerOptions.Builder().build());
        koreanRecognizer     = TextRecognition.getClient(new KoreanTextRecognizerOptions.Builder().build());
    }

    /**
     * Process an image using all five ML Kit recognizers and return the merged,
     * structured text string.
     *
     * @param image       ML Kit InputImage
     * @param showLabels  If true, prepend a language tag [EN/HI/ZH/...] to each line
     * @return Formatted multi-line string preserving original scripts & reading order
     * @throws ExecutionException   if any recognizer task fails
     * @throws InterruptedException if the calling thread is interrupted while waiting
     */
    public static String process(InputImage image, boolean showLabels)
            throws ExecutionException, InterruptedException {

        ensureInit();

        // Fire all recognizers simultaneously
        Task<Text> latinTask      = latinRecognizer.process(image);
        Task<Text> chineseTask    = chineseRecognizer.process(image);
        Task<Text> devanagariTask = devanagariRecognizer.process(image);
        Task<Text> japaneseTask   = japaneseRecognizer.process(image);
        Task<Text> koreanTask     = koreanRecognizer.process(image);

        // Block until all complete (this is expected to run on a background thread)
        Tasks.await(Tasks.whenAll(latinTask, chineseTask, devanagariTask, japaneseTask, koreanTask));

        // Collect Text results safely (tasks may have failed silently)
        List<Text> allResults = new ArrayList<>();
        if (latinTask.isSuccessful()      && latinTask.getResult()      != null) allResults.add(latinTask.getResult());
        if (chineseTask.isSuccessful()    && chineseTask.getResult()    != null) allResults.add(chineseTask.getResult());
        if (devanagariTask.isSuccessful() && devanagariTask.getResult() != null) allResults.add(devanagariTask.getResult());
        if (japaneseTask.isSuccessful()   && japaneseTask.getResult()   != null) allResults.add(japaneseTask.getResult());
        if (koreanTask.isSuccessful()     && koreanTask.getResult()     != null) allResults.add(koreanTask.getResult());

        return OcrProcessor.processMerged(allResults, showLabels);
    }

    /**
     * Synchronous single-recognizer shortcut (Latin-only).
     * Use this from the live camera analysis path where calling ALL recognizers
     * per frame would be too slow.  Switch to full multi-script processing for
     * captured stills in capturePhoto().
     */
    public static TextRecognizer getLatinRecognizer() {
        ensureInit();
        return latinRecognizer;
    }

    /** Close all recognizers — call in onDestroy(). */
    public static void close() {
        if (latinRecognizer      != null) { latinRecognizer.close();      latinRecognizer      = null; }
        if (chineseRecognizer    != null) { chineseRecognizer.close();    chineseRecognizer    = null; }
        if (devanagariRecognizer != null) { devanagariRecognizer.close(); devanagariRecognizer = null; }
        if (japaneseRecognizer   != null) { japaneseRecognizer.close();   japaneseRecognizer   = null; }
        if (koreanRecognizer     != null) { koreanRecognizer.close();     koreanRecognizer     = null; }
    }

    private static void ensureInit() {
        if (latinRecognizer == null) init();
    }
}
