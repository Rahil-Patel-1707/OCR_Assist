package com.example.ocr_assist.ui.base;

import android.os.Bundle;
import android.view.LayoutInflater;
import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.viewbinding.ViewBinding;

public abstract class BaseActivity<VB extends ViewBinding> extends AppCompatActivity {
    
    private VB binding;

    @NonNull
    protected abstract VB getViewBinding(@NonNull LayoutInflater inflater);

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = getViewBinding(getLayoutInflater());
        setContentView(binding.getRoot());
        
        setupWindowInsets();
        setupObservers();
        setupListeners();
    }

    protected void setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    protected VB getBinding() {
        return binding;
    }

    protected void setupObservers() {}
    
    protected void setupListeners() {}
}
