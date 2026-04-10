package com.example.ocr_assist.ui;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.Filter;
import android.widget.Filterable;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.ocr_assist.databinding.ItemLanguageBinding;

import java.util.ArrayList;
import java.util.List;

public class LanguageAdapter extends RecyclerView.Adapter<LanguageAdapter.LanguageViewHolder> implements Filterable {

    private final List<Language> originalList;
    private List<Language> filteredList;
    private final OnLanguageClickListener listener;

    public interface OnLanguageClickListener {
        void onLanguageClick(Language language);
    }

    public LanguageAdapter(List<Language> languages, OnLanguageClickListener listener) {
        this.originalList = languages;
        this.filteredList = new ArrayList<>(languages);
        this.listener = listener;
    }

    @NonNull
    @Override
    public LanguageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemLanguageBinding binding = ItemLanguageBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new LanguageViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull LanguageViewHolder holder, int position) {
        Language lang = filteredList.get(position);
        holder.binding.textLanguageName.setText(lang.getName());
        holder.binding.getRoot().setOnClickListener(v -> {
            if (listener != null) {
                listener.onLanguageClick(lang);
            }
        });
    }

    @Override
    public int getItemCount() {
        return filteredList.size();
    }

    @Override
    public Filter getFilter() {
        return new Filter() {
            @Override
            protected FilterResults performFiltering(CharSequence constraint) {
                String query = constraint != null ? constraint.toString().toLowerCase() : "";
                List<Language> result = new ArrayList<>();
                if (query.isEmpty()) {
                    result.addAll(originalList);
                } else {
                    for (Language lang : originalList) {
                        if (lang.getName().toLowerCase().contains(query)) {
                            result.add(lang);
                        }
                    }
                }
                FilterResults filterResults = new FilterResults();
                filterResults.values = result;
                return filterResults;
            }

            @Override
            @SuppressWarnings("unchecked")
            protected void publishResults(CharSequence constraint, FilterResults results) {
                filteredList = (List<Language>) results.values;
                notifyDataSetChanged();
            }
        };
    }

    static class LanguageViewHolder extends RecyclerView.ViewHolder {
        final ItemLanguageBinding binding;
        public LanguageViewHolder(ItemLanguageBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
