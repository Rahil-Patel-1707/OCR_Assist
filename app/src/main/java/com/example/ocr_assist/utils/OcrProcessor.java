package com.example.ocr_assist.utils;

import android.graphics.Rect;

import com.google.mlkit.nl.languageid.LanguageIdentification;
import com.google.mlkit.nl.languageid.LanguageIdentifier;
import com.google.mlkit.vision.text.Text;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * OcrProcessor converts raw ML Kit Text results into clean, structured,
 * reading-order strings.
 *
 * Two public entry points:
 *  1. process(Text)                      – single-script (legacy / live preview)
 *  2. processMerged(List<Text>, boolean) – multi-script merged from several recognizers
 *
 * The multi-script path de-duplicates overlapping blocks across recognizer outputs
 * so repeated detections of the same region are eliminated.
 * When showLabels=true, each line is prefixed with a language tag like [EN] [HI] [ZH].
 */
public class OcrProcessor {

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Process a single ML Kit Text result (Latin recognizer).
     * Used for live-camera frames where speed is critical.
     */
    public static String process(Text result) {
        if (result == null || result.getTextBlocks().isEmpty()) return "";
        List<Text.TextBlock> blocks = new ArrayList<>(result.getTextBlocks());
        return renderBlocks(groupIntoColumns(blocks), false, null);
    }

    /**
     * Process and merge results from multiple ML Kit recognizers.
     * De-duplicates overlapping text blocks; optionally prepends language labels.
     *
     * @param results     List of Text objects from each recognizer (may overlap)
     * @param showLabels  If true, adds a [LA] tag before each line
     */
    public static String processMerged(List<Text> results, boolean showLabels) {
        if (results == null || results.isEmpty()) return "";

        // Gather ALL text blocks from every recognizer
        List<Text.TextBlock> all = new ArrayList<>();
        for (Text t : results) {
            if (t != null) all.addAll(t.getTextBlocks());
        }
        if (all.isEmpty()) return "";

        // De-duplicate: if two blocks overlap significantly, keep the one with more text
        List<Text.TextBlock> deduplicated = deduplicateBlocks(all);

        // Build language-label map (block text → detected language tag)
        Map<Text.TextBlock, String> langLabels = showLabels
                ? detectLanguages(deduplicated)
                : null;

        return renderBlocks(groupIntoColumns(deduplicated), showLabels, langLabels);
    }

    // =========================================================================
    // De-duplication
    // =========================================================================

    /**
     * Two blocks are considered duplicates when their bounding boxes overlap
     * by more than 60% of the smaller box's area.  We keep the block with
     * the longer text (more detail from that recognizer).
     */
    private static List<Text.TextBlock> deduplicateBlocks(List<Text.TextBlock> blocks) {
        List<Text.TextBlock> result = new ArrayList<>();

        for (Text.TextBlock candidate : blocks) {
            Rect cBox = candidate.getBoundingBox();
            if (cBox == null) continue;

            boolean absorbed = false;
            for (int i = 0; i < result.size(); i++) {
                Text.TextBlock existing = result.get(i);
                Rect eBox = existing.getBoundingBox();
                if (eBox == null) continue;

                float overlap = overlapRatio(cBox, eBox);
                if (overlap > 0.60f) {
                    // Keep the richer block (more characters)
                    if (candidate.getText().length() > existing.getText().length()) {
                        result.set(i, candidate);
                    }
                    absorbed = true;
                    break;
                }
            }
            if (!absorbed) result.add(candidate);
        }
        return result;
    }

    /** Returns the fraction of the smaller box that is covered by the intersection. */
    private static float overlapRatio(Rect a, Rect b) {
        int interLeft   = Math.max(a.left, b.left);
        int interTop    = Math.max(a.top, b.top);
        int interRight  = Math.min(a.right, b.right);
        int interBottom = Math.min(a.bottom, b.bottom);
        if (interRight <= interLeft || interBottom <= interTop) return 0f;
        float interArea = (float) (interRight - interLeft) * (interBottom - interTop);
        float areaA = (float) a.width() * a.height();
        float areaB = (float) b.width() * b.height();
        float smaller = Math.min(areaA, areaB);
        return smaller > 0 ? interArea / smaller : 0f;
    }

    // =========================================================================
    // Column grouping (unchanged from original)
    // =========================================================================

    private static List<List<Text.TextBlock>> groupIntoColumns(List<Text.TextBlock> blocks) {
        // Sort left→right first
        Collections.sort(blocks,
                Comparator.comparingInt(b -> b.getBoundingBox() != null ? b.getBoundingBox().left : 0));

        List<List<Text.TextBlock>> columns = new ArrayList<>();
        for (Text.TextBlock block : blocks) {
            Rect blockBox = block.getBoundingBox();
            if (blockBox == null) continue;

            boolean placed = false;
            for (List<Text.TextBlock> col : columns) {
                Rect colBox = col.get(0).getBoundingBox();
                if (colBox != null) {
                    int overlap = Math.max(0,
                            Math.min(colBox.right, blockBox.right) - Math.max(colBox.left, blockBox.left));
                    if (overlap > 0) {
                        col.add(block);
                        placed = true;
                        break;
                    }
                }
            }
            if (!placed) {
                List<Text.TextBlock> col = new ArrayList<>();
                col.add(block);
                columns.add(col);
            }
        }

        // Sort columns themselves left→right
        Collections.sort(columns, (c1, c2) -> {
            int l1 = c1.get(0).getBoundingBox() != null ? c1.get(0).getBoundingBox().left : 0;
            int l2 = c2.get(0).getBoundingBox() != null ? c2.get(0).getBoundingBox().left : 0;
            return Integer.compare(l1, l2);
        });
        return columns;
    }

    // =========================================================================
    // Rendering
    // =========================================================================

    private static String renderBlocks(List<List<Text.TextBlock>> columns,
                                       boolean showLabels,
                                       Map<Text.TextBlock, String> langLabels) {
        StringBuilder sb = new StringBuilder();

        for (List<Text.TextBlock> column : columns) {
            // Top-to-bottom within each column
            Collections.sort(column,
                    Comparator.comparingInt(b -> b.getBoundingBox() != null ? b.getBoundingBox().top : 0));

            for (Text.TextBlock block : column) {
                String label = (showLabels && langLabels != null)
                        ? langLabels.getOrDefault(block, "") : "";

                List<Text.Line> lines = new ArrayList<>(block.getLines());
                Collections.sort(lines,
                        Comparator.comparingInt(l -> l.getBoundingBox() != null ? l.getBoundingBox().top : 0));

                for (Text.Line line : lines) {
                    List<Text.Element> elements = new ArrayList<>(line.getElements());
                    Collections.sort(elements,
                            Comparator.comparingInt(e -> e.getBoundingBox() != null ? e.getBoundingBox().left : 0));

                    StringBuilder lineBuilder = new StringBuilder();
                    if (!label.isEmpty()) lineBuilder.append(label).append(" ");

                    for (Text.Element el : elements) {
                        String word = el.getText().trim();
                        if (!word.isEmpty()) lineBuilder.append(word).append(" ");
                    }

                    String formatted = lineBuilder.toString().trim();
                    if (!formatted.isEmpty()) sb.append(formatted).append("\n");
                }
                // Paragraph break between blocks
                sb.append("\n");
            }
        }
        return sb.toString().trim();
    }

    // =========================================================================
    // Language detection (synchronous via await — run on background thread)
    // =========================================================================

    /**
     * For each text block, synchronously detect the dominant language using
     * ML Kit Language Identification and return a BCP-47 tag label like [EN].
     *
     * This must be called from a background thread.
     */
    private static Map<Text.TextBlock, String> detectLanguages(List<Text.TextBlock> blocks) {
        Map<Text.TextBlock, String> labels = new HashMap<>();
        LanguageIdentifier identifier = LanguageIdentification.getClient();

        for (Text.TextBlock block : blocks) {
            String text = block.getText();
            if (text.trim().isEmpty()) continue;

            try {
                String langCode = com.google.android.gms.tasks.Tasks.await(
                        identifier.identifyLanguage(text));
                if (!"und".equals(langCode)) {
                    // Convert BCP-47 code to upper-case locale display tag e.g. "hi" → "[HI]"
                    String tag = "[" + langCode.toUpperCase(Locale.ENGLISH) + "]";
                    labels.put(block, tag);
                }
            } catch (Exception ignored) {
                // Language detection failure is non-fatal — skip label for this block
            }
        }
        identifier.close();
        return labels;
    }
}
