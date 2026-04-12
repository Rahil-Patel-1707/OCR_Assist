package com.example.ocr_assist.ui;
import com.example.ocr_assist.model.Language;
import com.example.ocr_assist.adapter.LanguageAdapter;


import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.ocr_assist.databinding.BottomSheetLanguageSelectionBinding;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.mlkit.nl.translate.TranslateLanguage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class LanguageSelectionBottomSheet extends BottomSheetDialogFragment {

    private BottomSheetLanguageSelectionBinding binding;
    private LanguageAdapter adapter;
    private OnLanguageSelectedListener listener;

    public interface OnLanguageSelectedListener {
        void onLanguageSelected(String languageCode, String languageName);
    }

    public static LanguageSelectionBottomSheet newInstance() {
        return new LanguageSelectionBottomSheet();
    }

    public void setLanguageSelectedListener(OnLanguageSelectedListener listener) {
        this.listener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = BottomSheetLanguageSelectionBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        List<Language> languages = getSupportedLanguages();
        adapter = new LanguageAdapter(languages, lang -> {
            if (listener != null) {
                listener.onLanguageSelected(lang.getCode(), lang.getName());
            }
            dismiss();
        });

        binding.recyclerViewLanguages.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.recyclerViewLanguages.setAdapter(adapter);

        binding.editSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                adapter.getFilter().filter(s);
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private List<Language> getSupportedLanguages() {
        List<Language> list = new ArrayList<>();
        List<String> codes = TranslateLanguage.getAllLanguages();
        for (String code : codes) {
            Locale locale = new Locale(code);
            String name = locale.getDisplayLanguage(locale);
            if (name != null && !name.isEmpty()) {
                name = name.substring(0, 1).toUpperCase(locale) + name.substring(1);
            } else {
                name = code;
            }
            list.add(new Language(code, name));
        }

        Collections.sort(list, (l1, l2) -> l1.getName().compareToIgnoreCase(l2.getName()));
        return list;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}



