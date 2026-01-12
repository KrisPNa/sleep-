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

import java.util.Arrays;
import java.util.List;

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
    private boolean isEditing = false;
    private long collectionId = -1;

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

        // Check if we're in editing mode
        Bundle args = getArguments();
        if (args != null) {
            isEditing = args.getBoolean("isEditing", false);
            collectionId = args.getLong("collectionId", -1);
        }
        // Инициализация адаптера цветов
        setupColorAdapter();
// Обновляем UI based on mode
        if (isEditing && collectionId != -1) {
            loadCollectionForEditing();
            saveButton.setText("Сохранить");
        } else {
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
        }

        // Обработчик кнопки назад
        backButton.setOnClickListener(v -> {
            requireActivity().getSupportFragmentManager().popBackStack();
        });
        saveButton.setOnClickListener(v -> {
            if (isEditing) {
                updateCollection();
            } else {
                createCollection();
            }
        });
    }

    private void setupColorAdapter() {
        colorAdapter = new ColorAdapter(requireContext(), new ColorAdapter.OnColorClickListener() {
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
                // Создаем коллекцию с выбранным цветом - ИСПРАВЛЕНО: используем createCollectionWithColor
                viewModel.createCollectionWithColor(collectionName, selectedColor);
                Toast.makeText(getContext(), "Коллекция создана!", Toast.LENGTH_SHORT).show();

                // Возвращаемся назад
                requireActivity().getSupportFragmentManager().popBackStack();
            }
        });
    }

    private void loadCollectionForEditing() {
        viewModel.getCollectionById(collectionId).observe(getViewLifecycleOwner(), collection -> {
            if (collection != null) {
                // Set the collection name in the EditText
                collectionNameEditText.setText(collection.getName());

                // Set selection in the EditText to the end
                collectionNameEditText.setSelection(collectionNameEditText.getText().length());

                // Get the first color from the collection's color list
                List<String> colors = collection.getColors();
                if (colors != null && !colors.isEmpty()) {
                    selectedColor = colors.get(0);

                    // Find the color name based on the selected color
                    String colorName = getColorName(selectedColor);
                    selectedColorName = colorName;

                    // Update the color adapter selection
                    if (colorAdapter != null) {
                        colorAdapter.setSelectedColor(selectedColor);
                    }
                } else {
                    // Fallback to default color if collection has no colors
                    selectedColor = Collection.AVAILABLE_COLORS[0];
                    selectedColorName = "Синий";
                }

                updatePreview();
            }
        });
    }

    private void updateCollection() {
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

        // First get the collection to update
        viewModel.getCollectionById(collectionId).observe(getViewLifecycleOwner(), collection -> {
            if (collection != null) {
                // Check if the new name already exists (excluding current collection)
                viewModel.doesCollectionExistExcludeId(collectionName, collectionId).observe(getViewLifecycleOwner(), exists -> {
                    isChecking = false;

                    if (exists != null && exists) {
                        Toast.makeText(getContext(),
                                "Коллекция \"" + collectionName + "\" уже существует",
                                Toast.LENGTH_LONG).show();
                        collectionNameEditText.requestFocus();
                    } else {
                        // Update the collection with the new name and selected color
                        collection.setName(collectionName);

                        // Create a list with the selected color
                        List<String> updatedColors = Arrays.asList(selectedColor);
                        collection.setColors(updatedColors);

                        viewModel.updateCollection(collection);
                        Toast.makeText(getContext(), "Коллекция обновлена!", Toast.LENGTH_SHORT).show();

                        // Return back to the previous screen
                        requireActivity().getSupportFragmentManager().popBackStack();
                    }
                });
            } else {
                isChecking = false;
                Toast.makeText(getContext(), "Ошибка: коллекция не найдена", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private String getColorName(String color) {
        // This maps the hex color to the corresponding name
        // We'll implement a basic mapping
        switch (color.toLowerCase()) {
            case "#2196f3": // primary blue
                return "Синий";
            case "#f44336": // red
                return "Красный";
            case "#4caf50": // green
                return "Зелёный";
            case "#ff9800": // orange
                return "Оранжевый";
            case "#9c27b0": // purple
                return "Фиолетовый";
            case "#795548": // brown
                return "Коричневый";
            case "#607d8b": // blue gray
                return "Серо-голубой";
            case "#e91e63": // pink
                return "Розовый";
            case "#00bcd4": // cyan
                return "Бирюзовый";
            case "#cddc39": // lime
                return "Лайм";
            case "#ffc107": // amber
                return "Янтарный";
            case "#ff5722": // deep orange
                return "Тёмно-оранжевый";
            case "#3f51b5": // indigo
                return "Индиго";
            case "#009688": // teal
                return "Бирюзово-зелёный";
            case "#8bc34a": // light green
                return "Светло-зелёный";
            case "#ffeb3b": // yellow
                return "Жёлтый";
            default:
                return "Синий"; // default fallback
        }
    }
}