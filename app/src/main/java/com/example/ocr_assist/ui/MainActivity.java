package com.example.ocr_assist.ui;

import android.view.LayoutInflater;
import androidx.annotation.NonNull;

import com.example.ocr_assist.databinding.ActivityMainBinding;
import com.example.ocr_assist.ui.base.BaseActivity;

public class MainActivity extends BaseActivity<ActivityMainBinding> {

    @NonNull
    @Override
    protected ActivityMainBinding getViewBinding(@NonNull LayoutInflater inflater) {
        return ActivityMainBinding.inflate(inflater);
    }

    @Override
    protected void setupObservers() {
        super.setupObservers();
        // Setup LiveData observers here
    }

    @Override
    protected void setupListeners() {
        super.setupListeners();
        
        getBinding().cardRealTimeOcr.setOnClickListener(v -> {
            startActivity(new android.content.Intent(this, RealTimeOcrActivity.class));
        });

        getBinding().cardBatchOcr.setOnClickListener(v -> {
            startActivity(new android.content.Intent(this, BatchOcrActivity.class));
        });

    }
}
