package com.example.seriestracker.ui.screens;

import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.seriestracker.R;
import com.example.seriestracker.data.entities.Collection;
import com.example.seriestracker.ui.adapters.ColorAdapter;
import com.example.seriestracker.ui.viewmodels.SeriesViewModel;
import com.google.android.material.card.MaterialCardView;

public class CreateCollectionScreen extends Fragment {

    private SeriesViewModel viewModel;
    private EditText collectionNameEditText;
    private Button saveButton;
    private ImageButton backButton;
    private RecyclerView colorRecyclerView;
    private MaterialCardView colorPreviewCard;
    private TextView previewNameText;
    private TextView previewColorText;

    private ColorAdapter colorAdapter;
    private String selectedColor;
    private String selectedColorName;
    private boolean isChecking = false;

    public CreateCollectionScreen() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_create_collection, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(requireActivity()).get(SeriesViewModel.class);

        collectionNameEditText = view.findViewById(R.id.collectionNameEditText);
        saveButton = view.findViewById(R.id.saveButton);
        backButton = view.findViewById(R.id.backButton);
        colorRecyclerView = view.findViewById(R.id.colorRecyclerView);
        colorPreviewCard = view.findViewById(R.id.colorPreviewCard);
        previewNameText = view.findViewById(R.id.previewNameText);
        previewColorText = view.findViewById(R.id.previewColorText);

        // Инициализация адаптера цветов
        setupColorAdapter();

        // Устанавливаем фокус на поле ввода
        collectionNameEditText.requestFocus();

        // Обновляем предпросмотр при вводе текста
        collectionNameEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updatePreview();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // Обработчик кнопки назад
        backButton.setOnClickListener(v -> {
            requireActivity().getSupportFragmentManager().popBackStack();
        });

        saveButton.setOnClickListener(v -> createCollection());
    }

    private void setupColorAdapter() {
        colorAdapter = new ColorAdapter(new ColorAdapter.OnColorClickListener() {
            @Override
            public void onColorClick(String color, String colorName) {
                selectedColor = color;
                selectedColorName = colorName;
                updatePreview();
            }
        });

        GridLayoutManager layoutManager = new GridLayoutManager(getContext(), 6);
        layoutManager.setOrientation(RecyclerView.VERTICAL);
        colorRecyclerView.setLayoutManager(layoutManager);
        colorRecyclerView.setAdapter(colorAdapter);

        // Устанавливаем первый цвет по умолчанию
        selectedColor = Collection.AVAILABLE_COLORS[0];
        selectedColorName = "Синий";
        updatePreview();
    }
    private void updatePreview() {
        // Обновляем название в предпросмотре
        String name = collectionNameEditText.getText().toString().trim();
        if (!name.isEmpty()) {
            previewNameText.setText(name);
        } else {
            previewNameText.setText("Название коллекции");
        }

        // Обновляем текст цвета
        previewColorText.setText(selectedColorName);

        // Обновляем цвет обводки
        colorPreviewCard.setStrokeColor(Color.parseColor(selectedColor));
    }

    private void createCollection() {
        String collectionName = collectionNameEditText.getText().toString().trim();

        if (collectionName.isEmpty()) {
            Toast.makeText(getContext(), "Введите название коллекции", Toast.LENGTH_SHORT).show();
            collectionNameEditText.requestFocus();
            return;
        }

        if (isChecking) {
            return;
        }

        isChecking = true;

        // Проверяем, существует ли уже такая коллекция
        viewModel.doesCollectionExist(collectionName).observe(getViewLifecycleOwner(), exists -> {
            isChecking = false;

            if (exists != null && exists) {
                Toast.makeText(getContext(),
                        "Коллекция \"" + collectionName + "\" уже существует",
                        Toast.LENGTH_LONG).show();
                collectionNameEditText.requestFocus();
            } else {
                // Создаем коллекцию с выбранным цветом
                viewModel.createCollection(collectionName, selectedColor);
                Toast.makeText(getContext(), "Коллекция создана!", Toast.LENGTH_SHORT).show();

                // Возвращаемся назад
                requireActivity().getSupportFragmentManager().popBackStack();
            }
        });
    }
}