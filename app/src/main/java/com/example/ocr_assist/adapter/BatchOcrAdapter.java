package com.example.ocr_assist.adapter;
import com.example.ocr_assist.model.BatchOcrResult;


import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.ocr_assist.databinding.ItemBatchOcrResultBinding;

import java.util.ArrayList;
import java.util.List;

public class BatchOcrAdapter extends RecyclerView.Adapter<BatchOcrAdapter.ResultViewHolder> {

    private final List<BatchOcrResult> results = new ArrayList<>();
    private final OnTranslateClickListener translateListener;
    private final OnListenClickListener listenListener;
    private final OnStopClickListener stopListener;

    public interface OnTranslateClickListener {
        void onTranslateClick(int position, BatchOcrResult result);
    }

    public interface OnListenClickListener {
        void onListenClick(int position, String textToRead);
    }

    /** Separate interface so Stop never calls the Speak path. */
    public interface OnStopClickListener {
        void onStopClick(int position);
    }

    public BatchOcrAdapter(OnTranslateClickListener translateListener,
                           OnListenClickListener listenListener,
                           OnStopClickListener stopListener) {
        this.translateListener = translateListener;
        this.listenListener   = listenListener;
        this.stopListener     = stopListener;
    }

    // -------------------------------------------------------------------------
    // Data helpers
    // -------------------------------------------------------------------------

    public void addResult(BatchOcrResult result) {
        results.add(result);
        notifyItemInserted(results.size() - 1);
    }

    public void updateTranslation(int position, String translatedText) {
        if (position >= 0 && position < results.size()) {
            results.get(position).setTranslatedText(translatedText);
            notifyItemChanged(position);
        }
    }

    public void showTranslationProgress(int position) {
        if (position >= 0 && position < results.size()) {
            results.get(position).setTranslatedText("translating_in_progress...");
            notifyItemChanged(position);
        }
    }

    public void clearResults() {
        int size = results.size();
        results.clear();
        notifyItemRangeRemoved(0, size);
    }

    /**
     * Toggle the speaking state for a single item and refresh only that row,
     * using a payload so partial bind skips expensive full-bind work.
     */
    public void setSpeakingState(int position, boolean isSpeaking) {
        if (position >= 0 && position < results.size()) {
            results.get(position).setSpeaking(isSpeaking);
            notifyItemChanged(position, "speaking_state");
        }
    }

    // -------------------------------------------------------------------------
    // RecyclerView overrides
    // -------------------------------------------------------------------------

    @NonNull
    @Override
    public ResultViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemBatchOcrResultBinding binding = ItemBatchOcrResultBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ResultViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ResultViewHolder holder, int position) {
        holder.bind(results.get(position), position, translateListener, listenListener, stopListener);
    }

    /** Partial-bind: only update the listen button when payload == "speaking_state". */
    @Override
    public void onBindViewHolder(@NonNull ResultViewHolder holder, int position,
                                 @NonNull List<Object> payloads) {
        if (!payloads.isEmpty() && "speaking_state".equals(payloads.get(0))) {
            holder.updateListenButton(results.get(position), position, listenListener, stopListener);
        } else {
            super.onBindViewHolder(holder, position, payloads);
        }
    }

    @Override
    public int getItemCount() { return results.size(); }

    // -------------------------------------------------------------------------
    // ViewHolder
    // -------------------------------------------------------------------------

    static class ResultViewHolder extends RecyclerView.ViewHolder {
        private final ItemBatchOcrResultBinding binding;

        public ResultViewHolder(ItemBatchOcrResultBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(BatchOcrResult result, int position,
                         OnTranslateClickListener translateListener,
                         OnListenClickListener listenListener,
                         OnStopClickListener stopListener) {

            binding.imageThumbnail.setImageURI(result.getImageUri());

            // ── OCR text ──────────────────────────────────────────────────────
            if (result.getExtractedText().isEmpty()) {
                binding.textResult.setText("No text found");
                binding.btnTranslate.setEnabled(false);
            } else {
                binding.textResult.setText(result.getExtractedText());
                binding.btnTranslate.setEnabled(true);
            }

            // ── Translation state ─────────────────────────────────────────────
            if (result.getTranslatedText().equals("translating_in_progress...")) {
                binding.progressTranslation.setVisibility(View.VISIBLE);
                binding.textTranslated.setVisibility(View.GONE);
                binding.btnTranslate.setEnabled(false);
            } else if (!result.getTranslatedText().isEmpty()) {
                binding.progressTranslation.setVisibility(View.GONE);
                binding.textTranslated.setVisibility(View.VISIBLE);
                binding.textTranslated.setText(result.getTranslatedText());
                binding.btnTranslate.setEnabled(true);
            } else {
                binding.progressTranslation.setVisibility(View.GONE);
                binding.textTranslated.setVisibility(View.GONE);
            }

            // ── Button listeners ──────────────────────────────────────────────
            binding.btnTranslate.setOnClickListener(v -> {
                if (translateListener != null)
                    translateListener.onTranslateClick(position, result);
            });

            binding.btnCopy.setOnClickListener(v -> {
                String text = binding.textResult.getText().toString();
                if (binding.textTranslated.getVisibility() == View.VISIBLE)
                    text += "\n\n" + binding.textTranslated.getText().toString();
                if (!text.isEmpty() && !text.equals("No text found")) {
                    android.content.ClipboardManager cb =
                            (android.content.ClipboardManager)
                                    v.getContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE);
                    cb.setPrimaryClip(android.content.ClipData.newPlainText("OCR", text));
                    android.widget.Toast.makeText(v.getContext(), "Copied to clipboard",
                            android.widget.Toast.LENGTH_SHORT).show();
                }
            });

            binding.btnShare.setOnClickListener(v -> {
                String text = binding.textResult.getText().toString();
                if (binding.textTranslated.getVisibility() == View.VISIBLE)
                    text += "\n\n" + binding.textTranslated.getText().toString();
                if (!text.isEmpty() && !text.equals("No text found")) {
                    android.content.Intent intent = new android.content.Intent(
                            android.content.Intent.ACTION_SEND);
                    intent.setType("text/plain");
                    intent.putExtra(android.content.Intent.EXTRA_TEXT, text);
                    v.getContext().startActivity(
                            android.content.Intent.createChooser(intent, "Share OCR Result"));
                }
            });

            // ── Listen / Stop toggle ──────────────────────────────────────────
            updateListenButton(result, position, listenListener, stopListener);
        }

        /**
         * Applies the correct visual state and click handler to btnListen based on
         * whether TTS is currently active for this item.
         *
         * When idle  → green "Listen" + speaker icon
         * When active → red  "Stop"   + pause  icon
         */
        void updateListenButton(BatchOcrResult result, int position,
                                OnListenClickListener listenListener,
                                OnStopClickListener stopListener) {

            // Capture a stable copy so lambda closures are never stale
            final int pos = position;

            // Resolve the theme primary colour once, reliably
            android.util.TypedValue tv = new android.util.TypedValue();
            binding.btnListen.getContext().getTheme()
                    .resolveAttribute(android.R.attr.colorPrimary, tv, true);
            int primaryColor = tv.data;

            if (result.isSpeaking()) {
                // ── STOP state ────────────────────────────────────────────────
                int stopColor = Color.parseColor("#F44336");
                binding.btnListen.setText("Stop");
                binding.btnListen.setIconResource(android.R.drawable.ic_media_pause);
                binding.btnListen.setIconTint(ColorStateList.valueOf(stopColor));  // explicit, never null
                binding.btnListen.setTextColor(stopColor);
                binding.btnListen.setOnClickListener(v -> {
                    if (stopListener != null) stopListener.onStopClick(pos);
                });
            } else {
                // ── LISTEN state ──────────────────────────────────────────────
                binding.btnListen.setText("Listen");
                binding.btnListen.setIconResource(android.R.drawable.ic_lock_silent_mode_off);
                binding.btnListen.setIconTint(ColorStateList.valueOf(primaryColor)); // explicit reset
                binding.btnListen.setTextColor(primaryColor);
                binding.btnListen.setOnClickListener(v -> {
                    String textToRead =
                            binding.textTranslated.getVisibility() == View.VISIBLE
                                    ? binding.textTranslated.getText().toString()
                                            .replaceFirst("Translated \\(.*?\\): \\n", "")
                                    : binding.textResult.getText().toString();

                    if (listenListener != null
                            && !textToRead.isEmpty()
                            && !textToRead.equals("No text found")) {
                        listenListener.onListenClick(pos, textToRead);
                    }
                });
            }
        }
    }
}



