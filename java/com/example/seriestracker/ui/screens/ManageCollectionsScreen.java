package com.example.seriestracker.ui.screens;

import android.app.AlertDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.seriestracker.R;
import com.example.seriestracker.data.entities.Collection;
import com.example.seriestracker.ui.adapters.CollectionsManageAdapter;
import com.example.seriestracker.ui.adapters.ColorAdapter;
import com.example.seriestracker.ui.viewmodels.SeriesViewModel;

import java.util.Arrays;
import java.util.List;

public class ManageCollectionsScreen extends Fragment {

    private SeriesViewModel viewModel;
    private RecyclerView collectionsRecyclerView;
    private CollectionsManageAdapter adapter;
    private TextView collectionsCountTextView;
    private TextView noCollectionsTextView;
    private boolean isCheckingExistence = false;

    // Переменная для хранения выбранного цвета в диалоге
    private String selectedColor;

    public ManageCollectionsScreen() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_manage_collections, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(requireActivity()).get(SeriesViewModel.class);

        collectionsRecyclerView = view.findViewById(R.id.collectionsRecyclerView);
        collectionsCountTextView = view.findViewById(R.id.collectionsCountTextView);
        noCollectionsTextView = view.findViewById(R.id.noCollectionsTextView);

        view.findViewById(R.id.backButton).setOnClickListener(v -> {
            requireActivity().getSupportFragmentManager().popBackStack();
        });

        setupRecyclerView();
        loadData();
    }

    private void setupRecyclerView() {
        adapter = new CollectionsManageAdapter(new CollectionsManageAdapter.OnCollectionActionListener() {
            @Override
            public void onEditClick(Collection collection) {
                showEditDialog(collection);
            }

            @Override
            public void onDeleteClick(Collection collection) {
                showDeleteDialog(collection);
            }

            @Override
            public void onFavoriteClick(Collection collection) {
                // Обновляем коллекцию в базе данных
                viewModel.updateCollection(collection);
                String message = collection.isFavorite() ?
                        "Коллекция добавлена в избранное" :
                        "Коллекция убрана из избранного";
                Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
            }
        });

        collectionsRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        collectionsRecyclerView.setAdapter(adapter);
    }

    private void loadData() {
        viewModel.getAllCollections().observe(getViewLifecycleOwner(), collections -> {
            if (collections != null && !collections.isEmpty()) {
                adapter.setCollections(collections);
                collectionsCountTextView.setText(String.valueOf(collections.size()));

                collectionsRecyclerView.setVisibility(View.VISIBLE);
                noCollectionsTextView.setVisibility(View.GONE);

                // Получаем количество сериалов для каждой коллекции
                for (Collection collection : collections) {
                    viewModel.getSeriesCountInCollection(collection.getId()).observe(
                            getViewLifecycleOwner(), count -> {
                                adapter.updateSeriesCount(collection.getId(), count != null ? count : 0);
                            });
                }
            } else {
                collectionsRecyclerView.setVisibility(View.GONE);
                noCollectionsTextView.setVisibility(View.VISIBLE);
                collectionsCountTextView.setText("0");
            }
        });
    }

    private void showEditDialog(Collection collection) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle("Редактировать коллекцию");

        // Загружаем макет диалога
        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_edit_collection, null);
        EditText editText = dialogView.findViewById(R.id.editCollectionNameEditText);
        RecyclerView colorRecyclerView = dialogView.findViewById(R.id.colorRecyclerViewDialog);
        View selectedColorView = dialogView.findViewById(R.id.selectedColorViewDialog);
        TextView selectedColorName = dialogView.findViewById(R.id.selectedColorNameDialog);

        // Устанавливаем текущие значения
        editText.setText(collection.getName());

        // Настройка выбора цвета
        List<String> colors = collection.getColors();
        String currentColor = (colors != null && !colors.isEmpty()) ? colors.get(0) : Collection.AVAILABLE_COLORS[0];
        setupColorPickerDialog(colorRecyclerView, selectedColorView, selectedColorName, currentColor);

        builder.setView(dialogView);

        // Затем в методе showEditDialog:
        builder.setPositiveButton("Сохранить", (dialog, which) -> {
            String newName = editText.getText().toString().trim();
            String finalSelectedColor = selectedColor;

            if (!newName.isEmpty()) {
                if (newName.equals(collection.getName())) {
                    // Если название не изменилось, только обновляем цвет
                    List<String> newColors = Arrays.asList(finalSelectedColor);
                    collection.setColors(newColors);
                    viewModel.updateCollection(collection);
                    Toast.makeText(getContext(), "Цвет коллекции обновлен", Toast.LENGTH_SHORT).show();
                } else {
                    // Используем одноразового наблюдателя
                    isCheckingExistence = true;
                    viewModel.doesCollectionExistExcludeId(newName, collection.getId())
                            .observe(getViewLifecycleOwner(), new Observer<Boolean>() {
                                @Override
                                public void onChanged(Boolean exists) {
                                    if (!isCheckingExistence) return;

                                    if (exists != null && exists) {
                                        // Коллекция уже существует
                                        Toast.makeText(getContext(),
                                                "Коллекция \"" + newName + "\" уже существует",
                                                Toast.LENGTH_LONG).show();
                                    } else {
                                        // Обновляем коллекцию
                                        List<String> newColors = Arrays.asList(finalSelectedColor);
                                        collection.setColors(newColors);
                                        collection.setName(newName);
                                        viewModel.updateCollection(collection);
                                        Toast.makeText(getContext(), "Коллекция обновлена", Toast.LENGTH_SHORT).show();
                                    }

                                    // Удаляем наблюдателя после использования
                                    isCheckingExistence = false;
                                }
                            });
                }
            }
        });

        builder.setNegativeButton("Отмена", (dialog, which) -> dialog.dismiss());

        // Создаем и показываем диалог
        AlertDialog dialog = builder.create();
        dialog.show();

        // Опционально: меняем цвет кнопок
        Button positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        if (positiveButton != null) {
            positiveButton.setTextColor(getResources().getColor(R.color.primary_blue));
        }

        Button negativeButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
        if (negativeButton != null) {
            negativeButton.setTextColor(getResources().getColor(R.color.text_gray));
        }
    }

    private void setupColorPickerDialog(RecyclerView colorRecyclerView, View selectedColorView,
                                        TextView selectedColorName, String currentColor) {
        // Устанавливаем текущий цвет коллекции
        selectedColor = currentColor;
        if (selectedColor == null || selectedColor.isEmpty()) {
            selectedColor = Collection.AVAILABLE_COLORS[0];
        }

        // Используем массив для обхода проблемы final/effectively final
        final ColorAdapter[] adapterHolder = new ColorAdapter[1];

        ColorAdapter colorAdapter = new ColorAdapter(getContext(), new ColorAdapter.OnColorClickListener() {
            @Override
            public void onColorClick(String color, String colorName) {
                selectedColor = color;
                // Используем holder для доступа к адаптеру
                if (adapterHolder[0] != null) {
                    adapterHolder[0].setSelectedColor(color);
                }
                updateSelectedColorDisplay(selectedColorView, selectedColorName, color, colorName);
            }
        });

        adapterHolder[0] = colorAdapter;

        GridLayoutManager layoutManager = new GridLayoutManager(getContext(), 6);
        colorRecyclerView.setLayoutManager(layoutManager);
        colorRecyclerView.setAdapter(colorAdapter);

        // Устанавливаем выбранный цвет
        colorAdapter.setSelectedColor(selectedColor);

        // Обновляем отображение начального цвета
        updateSelectedColorDisplay(selectedColorView, selectedColorName, selectedColor,
                colorAdapter.getSelectedColorName());
    }

    private void updateSelectedColorDisplay(View colorView, TextView colorNameView, String color, String colorName) {
        try {
            colorView.setBackgroundColor(Color.parseColor(color));
        } catch (Exception e) {
            colorView.setBackgroundColor(getResources().getColor(R.color.primary_blue));
        }
        colorNameView.setText(colorName != null ? colorName : "Синий");
    }

    private void showDeleteDialog(Collection collection) {
        new AlertDialog.Builder(getContext())
                .setTitle("Удаление коллекции")
                .setMessage("Вы уверены, что хотите удалить коллекцию \"" + collection.getName() + "\"?")
                .setPositiveButton("Удалить", (dialog, which) -> {
                    viewModel.deleteCollection(collection);
                    Toast.makeText(getContext(), "Коллекция удалена", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Отмена", null)
                .show();
    }
}