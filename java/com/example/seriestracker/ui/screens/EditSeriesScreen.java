package com.example.seriestracker.ui.screens;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.bumptech.glide.Glide;
import com.example.seriestracker.R;
import com.example.seriestracker.data.entities.Collection;
import com.example.seriestracker.data.entities.Series;
import com.example.seriestracker.ui.viewmodels.SeriesViewModel;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;

import java.util.ArrayList;
import java.util.List;

public class EditSeriesScreen extends Fragment {

    private static final int PICK_IMAGE_REQUEST = 1;
    private static final int REQUEST_READ_EXTERNAL_STORAGE = 2;

    private SeriesViewModel viewModel;
    private Series series;

    // UI элементы
    private EditText titleEditText;
    private EditText notesEditText;
    private EditText genreEditText;
    private EditText seasonsEditText;
    private EditText episodesEditText;
    private ImageView seriesImageView;
    private Button selectImageButton;
    private Button saveButton;
    private Button deleteButton;
    private MaterialAutoCompleteTextView statusSpinner;
    private CheckBox favoriteCheckBox;
    private ImageButton backButton;
    private Button collectionsButton; // Кнопка выбора коллекций
    private TextView selectedCollectionsText; // Текст выбранных коллекций
    private TextView collectionsTitle;

    private List<Collection> allCollections = new ArrayList<>();
    private List<Collection> selectedCollections = new ArrayList<>();

    private Uri selectedImageUri;

    public EditSeriesScreen() {
        // Required empty public constructor
    }

    public static EditSeriesScreen newInstance(long seriesId) {
        EditSeriesScreen fragment = new EditSeriesScreen();
        Bundle args = new Bundle();
        args.putLong("seriesId", seriesId);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_edit_series, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(requireActivity()).get(SeriesViewModel.class);

        // Инициализация UI элементов
        initViews(view);

        // Загрузка данных сериала
        long seriesId = getArguments() != null ? getArguments().getLong("seriesId", -1) : -1;

        if (seriesId != -1) {
            viewModel.getSeriesById(seriesId).observe(getViewLifecycleOwner(), series -> {
                if (series != null) {
                    this.series = series;
                    populateForm(series);
                }
            });

            // Загружаем коллекции и отмечаем, к каким принадлежит сериал
            loadCollections(seriesId);
        }

        // Настройка статус-спиннера
        setupStatusSpinner();

        // Обработчики событий
        setupEventListeners();
    }

    private void initViews(View view) {
        titleEditText = view.findViewById(R.id.titleEditText);
        notesEditText = view.findViewById(R.id.notesEditText);
        genreEditText = view.findViewById(R.id.genreEditText);
        seasonsEditText = view.findViewById(R.id.seasonsEditText);
        episodesEditText = view.findViewById(R.id.episodesEditText);
        seriesImageView = view.findViewById(R.id.seriesImageView);
        selectImageButton = view.findViewById(R.id.selectImageButton);
        saveButton = view.findViewById(R.id.saveButton);
        deleteButton = view.findViewById(R.id.deleteButton);
        statusSpinner = view.findViewById(R.id.statusSpinner);
        favoriteCheckBox = view.findViewById(R.id.favoriteCheckBox);
        backButton = view.findViewById(R.id.backButton);
        collectionsButton = view.findViewById(R.id.collectionsButton); // Теперь кнопка есть в XML
        selectedCollectionsText = view.findViewById(R.id.selectedCollectionsText); // Текст есть в XML
        collectionsTitle = view.findViewById(R.id.collectionsTitle);
    }

    private void populateForm(Series series) {
        titleEditText.setText(series.getTitle());
        notesEditText.setText(series.getNotes());
        genreEditText.setText(series.getGenre());

        if (series.getSeasons() > 0) {
            seasonsEditText.setText(String.valueOf(series.getSeasons()));
        }

        if (series.getEpisodes() > 0) {
            episodesEditText.setText(String.valueOf(series.getEpisodes()));
        }

        // Загрузка изображения
        if (series.getImageUri() != null && !series.getImageUri().isEmpty()) {
            Glide.with(this)
                    .load(series.getImageUri())
                    .placeholder(R.drawable.ic_baseline_image_24)
                    .into(seriesImageView);
        }

        // Установка статуса
        String status = series.getStatus();
        String displayText = getStatusDisplayText(status);
        statusSpinner.setText(displayText, false);

        // Чекбокс избранного
        favoriteCheckBox.setChecked(series.getIsFavorite());
    }

    private void loadCollections(long seriesId) {
        // Загружаем все коллекции
        viewModel.getAllCollections().observe(getViewLifecycleOwner(), collections -> {
            if (collections != null && !collections.isEmpty()) {
                collectionsTitle.setVisibility(View.VISIBLE);
                collectionsButton.setVisibility(View.VISIBLE);
                selectedCollectionsText.setVisibility(View.VISIBLE);

                allCollections = collections;
                selectedCollections.clear();

                // Загружаем коллекции, к которым принадлежит этот сериал
                viewModel.getCollectionsForSeries(seriesId).observe(getViewLifecycleOwner(),
                        seriesCollections -> {
                            if (seriesCollections != null) {
                                selectedCollections.addAll(seriesCollections);
                            }
                            updateSelectedCollectionsText();
                        });
            } else {
                collectionsTitle.setVisibility(View.GONE);
                collectionsButton.setVisibility(View.GONE);
                selectedCollectionsText.setVisibility(View.GONE);
            }
        });
    }

    private void updateSelectedCollectionsText() {
        if (selectedCollections.isEmpty()) {
            selectedCollectionsText.setText("Не выбрано");
        } else {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < selectedCollections.size(); i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(selectedCollections.get(i).getName());
            }
            selectedCollectionsText.setText(sb.toString());
        }
    }

    private void showCollectionsDialog() {
        if (allCollections.isEmpty()) {
            Toast.makeText(getContext(), "Нет доступных коллекций", Toast.LENGTH_SHORT).show();
            return;
        }

        // ПЕРЕД открытием диалога заново загружаем ТЕКУЩИЕ коллекции сериала
        viewModel.getCollectionsForSeries(series.getId()).observe(getViewLifecycleOwner(), currentCollections -> {
            // Очищаем выбранные коллекции и заполняем заново
            selectedCollections.clear();
            if (currentCollections != null) {
                selectedCollections.addAll(currentCollections);
            }

            // ТЕПЕРЬ показываем диалог с правильными галочками
            showCollectionsDialogInternal();
        });
    }

    private void showCollectionsDialogInternal() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle("Выберите коллекции");

        // Создаем массив для чекбоксов
        String[] collectionNames = new String[allCollections.size()];
        boolean[] checkedItems = new boolean[allCollections.size()];

        for (int i = 0; i < allCollections.size(); i++) {
            Collection collection = allCollections.get(i);
            collectionNames[i] = collection.getName();

            // Проверяем, выбрана ли коллекция в selectedCollections
            boolean isChecked = false;
            for (Collection selected : selectedCollections) {
                if (selected.getId() == collection.getId()) {
                    isChecked = true;
                    break;
                }
            }
            checkedItems[i] = isChecked;
        }

        builder.setMultiChoiceItems(collectionNames, checkedItems, (dialog, which, isChecked) -> {
            Collection collection = allCollections.get(which);
            if (isChecked) {
                if (!selectedCollections.contains(collection)) {
                    selectedCollections.add(collection);
                }
            } else {
                selectedCollections.remove(collection);
            }
            updateSelectedCollectionsText();
        });

        builder.setPositiveButton("Готово", (dialog, which) -> {
            dialog.dismiss();
            // После закрытия диалога показываем обновленный текст
            updateSelectedCollectionsText();
        });

        builder.setNegativeButton("Отмена", (dialog, which) -> {
            dialog.dismiss();
            // При отмене возвращаемся к исходному состоянию
            loadCollectionsForSeries();
        });

        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void loadCollectionsForSeries() {
        if (series == null) return;

        // Загружаем текущие коллекции сериала
        viewModel.getCollectionsForSeries(series.getId()).observe(getViewLifecycleOwner(),
                seriesCollections -> {
                    if (seriesCollections != null) {
                        selectedCollections.clear();
                        selectedCollections.addAll(seriesCollections);
                        updateSelectedCollectionsText();
                    }
                });
    }

    private void setupStatusSpinner() {
        List<String> statuses = new ArrayList<>();
        statuses.add("Смотрю");
        statuses.add("Завершено");
        statuses.add("Брошено");
        statuses.add("Запланировано");

        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_dropdown_item_1line, statuses);
        statusSpinner.setAdapter(adapter);
        statusSpinner.setThreshold(1);
    }

    private void setupEventListeners() {
        backButton.setOnClickListener(v -> requireActivity().getSupportFragmentManager().popBackStack());

        selectImageButton.setOnClickListener(v -> {
            if (checkPermission()) {
                openImagePicker();
            } else {
                requestPermission();
            }
        });

        // Делаем AutoCompleteTextView кликабельным
        statusSpinner.setOnClickListener(v -> {
            statusSpinner.showDropDown();
        });

        statusSpinner.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                statusSpinner.showDropDown();
            }
            return true;
        });

        // Обработчик для кнопки выбора коллекций
        collectionsButton.setOnClickListener(v -> {
            showCollectionsDialog(); // Используем новый метод
        });

        saveButton.setOnClickListener(v -> saveSeries());

        // Исправленный обработчик для кнопки удаления с диалогом подтверждения
        deleteButton.setOnClickListener(v -> {
            if (series != null) {
                showDeleteConfirmationDialog();
            }
        });
    }

    // Метод для показа диалога подтверждения удаления
    private void showDeleteConfirmationDialog() {
        if (series == null) return;

        new AlertDialog.Builder(requireContext())
                .setTitle("Удаление сериала")
                .setMessage("Вы уверены, что хотите удалить сериал \"" + series.getTitle() + "\"?\n")
                .setPositiveButton("Удалить", (dialog, which) -> {
                    deleteSeries();
                })
                .setNegativeButton("Отмена", (dialog, which) -> {
                    dialog.dismiss();
                })
                .show();
    }

    // Метод для удаления сериала
    private void deleteSeries() {
        if (series != null) {
            viewModel.deleteSeries(series.getId());
            Toast.makeText(getContext(), "Сериал удален", Toast.LENGTH_SHORT).show();
            requireActivity().getSupportFragmentManager().popBackStack();
        }
    }

    private void saveSeries() {
        if (series == null) return;

        String newTitle = titleEditText.getText().toString().trim();
        if (newTitle.isEmpty()) {
            Toast.makeText(getContext(), "Введите название сериала", Toast.LENGTH_SHORT).show();
            return;
        }

        // Проверяем, изменилось ли название
        if (!newTitle.equals(series.getTitle())) {
            // Название изменилось, проверяем, нет ли другого сериала с таким названием
            viewModel.doesSeriesExist(newTitle).observe(getViewLifecycleOwner(), exists -> {
                if (exists != null && exists) {
                    // Сериал с таким названием уже существует
                    Toast.makeText(getContext(),
                            "Сериал \"" + newTitle + "\" уже существует",
                            Toast.LENGTH_LONG).show();
                    titleEditText.setText(series.getTitle()); // Возвращаем старое название
                    titleEditText.requestFocus();
                } else {
                    // Название свободно, обновляем сериал
                    updateSeriesData(newTitle);
                }
            });
        } else {
            // Название не изменилось, просто обновляем
            updateSeriesData(newTitle);
        }
    }

    // Новый метод для обновления данных сериала
    private void updateSeriesData(String title) {
        // 1. Обновляем данные сериала
        series.setTitle(title);
        series.setNotes(notesEditText.getText().toString().trim());
        series.setGenre(genreEditText.getText().toString().trim());

        // Обновляем количество сезонов
        try {
            series.setSeasons(Integer.parseInt(seasonsEditText.getText().toString()));
        } catch (NumberFormatException e) {
            series.setSeasons(0);
        }

        // Обновляем количество серий
        try {
            series.setEpisodes(Integer.parseInt(episodesEditText.getText().toString()));
        } catch (NumberFormatException e) {
            series.setEpisodes(0);
        }

        // Обновляем изображение (если выбрано новое)
        if (selectedImageUri != null) {
            series.setImageUri(selectedImageUri.toString());
        }

        // Обновляем статус
        String selectedStatus = statusSpinner.getText().toString();
        series.setStatus(getStatusValue(selectedStatus));

        // Автоматически отмечаем как просмотренное, если статус "Завершено"
        series.setIsWatched("Завершено".equals(selectedStatus));

        // Обновляем избранное
        series.setIsFavorite(favoriteCheckBox.isChecked());

        // 2. Сохраняем сериал в БД
        viewModel.updateSeries(series);

        // 3. Создаем список ID выбранных коллекций
        List<Long> selectedCollectionIds = new ArrayList<>();
        for (Collection collection : selectedCollections) {
            selectedCollectionIds.add(collection.getId());
        }

        // 4. ПРОСТО ЗАМЕНЯЕМ все связи сериала
        viewModel.replaceSeriesCollections(series.getId(), selectedCollectionIds);

        // 5. Показываем сообщение об успехе
        Toast.makeText(getContext(), "Сериал обновлен", Toast.LENGTH_SHORT).show();

        // 6. Возвращаемся назад
        requireActivity().getSupportFragmentManager().popBackStack();
    }

    private void updateCollectionRelationships() {
        if (series == null) return;

        // Используем CountDownLatch или просто удаляем все связи и добавляем новые
        // Самый простой и надежный способ: сначала удаляем ВСЕ связи этого сериала,
        // затем добавляем выбранные

        // Получаем все коллекции для этого сериала
        viewModel.getCollectionsForSeries(series.getId()).observe(getViewLifecycleOwner(), currentCollections -> {
            // Этот код выполнится когда будут загружены текущие коллекции
            if (currentCollections != null) {
                // Сначала удаляем ВСЕ текущие связи
                for (Collection collection : currentCollections) {
                    viewModel.removeSeriesFromCollection(series.getId(), collection.getId());
                }

                // Теперь добавляем ВСЕ выбранные коллекции
                for (Collection collection : selectedCollections) {
                    viewModel.addSeriesToCollection(series.getId(), collection.getId());
                }
            } else {
                // Если нет текущих коллекций, просто добавляем выбранные
                for (Collection collection : selectedCollections) {
                    viewModel.addSeriesToCollection(series.getId(), collection.getId());
                }
            }

            // После обновления связей показываем Toast
            Toast.makeText(getContext(), "Коллекции обновлены", Toast.LENGTH_SHORT).show();
        });
    }

    private boolean checkPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(requireContext(),
                    Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED;
        } else {
            return ContextCompat.checkSelfPermission(requireContext(),
                    Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        }
    }

    private void requestPermission() {
        if (shouldShowRequestPermissionRationale(Manifest.permission.READ_EXTERNAL_STORAGE) ||
                shouldShowRequestPermissionRationale(Manifest.permission.READ_MEDIA_IMAGES)) {

            new AlertDialog.Builder(requireContext())
                    .setTitle("Нужно разрешение")
                    .setMessage("Чтобы выбрать изображение для сериала, нужно разрешение на доступ к галерее")
                    .setPositiveButton("OK", (dialog, which) -> {
                        String[] permissions;
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            permissions = new String[]{Manifest.permission.READ_MEDIA_IMAGES};
                        } else {
                            permissions = new String[]{Manifest.permission.READ_EXTERNAL_STORAGE};
                        }
                        requestPermissions(permissions, REQUEST_READ_EXTERNAL_STORAGE);
                    })
                    .setNegativeButton("Отмена", null)
                    .show();
        } else {
            String[] permissions;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissions = new String[]{Manifest.permission.READ_MEDIA_IMAGES};
            } else {
                permissions = new String[]{Manifest.permission.READ_EXTERNAL_STORAGE};
            }
            requestPermissions(permissions, REQUEST_READ_EXTERNAL_STORAGE);
        }
    }

    private void openImagePicker() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(intent, PICK_IMAGE_REQUEST);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_READ_EXTERNAL_STORAGE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openImagePicker();
            } else {
                Toast.makeText(getContext(), "Нужно разрешение для выбора изображения",
                        Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == getActivity().RESULT_OK && data != null) {
            selectedImageUri = data.getData();
            if (selectedImageUri != null) {
                Glide.with(this)
                        .load(selectedImageUri)
                        .placeholder(R.drawable.ic_baseline_image_24)
                        .into(seriesImageView);
            }
        }
    }

    private String getStatusDisplayText(String statusValue) {
        switch (statusValue) {
            case "watching": return "Смотрю";
            case "completed": return "Завершено";
            case "dropped": return "Брошено";
            case "planned": return "Запланировано";
            default: return "Запланировано";
        }
    }

    private String getStatusValue(String displayText) {
        switch (displayText) {
            case "Смотрю": return "watching";
            case "Завершено": return "completed";
            case "Брошено": return "dropped";
            case "Запланировано": return "planned";
            default: return "planned";
        }
    }
}