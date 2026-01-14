package com.example.seriestracker.ui.screens;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ContentUris;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
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
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.seriestracker.R;
import com.example.seriestracker.data.entities.Collection;
import com.example.seriestracker.data.entities.MediaFile;
import com.example.seriestracker.data.entities.Series;
import com.example.seriestracker.ui.adapters.MediaAdapter;
import com.example.seriestracker.ui.viewmodels.SeriesViewModel;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class EditSeriesScreen extends Fragment {

    // Константы для выбора изображения сериала
    private static final int PICK_SERIES_IMAGE_REQUEST = 1;
    private static final int REQUEST_SERIES_IMAGE_PERMISSION = 2;

    // Константы для добавления медиафайлов (МНОЖЕСТВЕННЫЙ ВЫБОР)
    private static final int PICK_MULTIPLE_MEDIA_REQUEST = 3;
    private static final int REQUEST_MEDIA_PERMISSION = 4;

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
    private Button editButton;
    private Button saveButton;
    private Button deleteButton;
    private MaterialAutoCompleteTextView statusSpinner;
    private CheckBox favoriteCheckBox;
    private ImageButton backButton;
    private Button collectionsButton;
    private TextView selectedCollectionsText;
    private TextView collectionsTitle;

    // Элементы для медиафайлов
    private Button addMediaButton;
    private RecyclerView mediaRecyclerView;
    private MediaAdapter mediaAdapter;
    private List<MediaFile> mediaFiles = new ArrayList<>();

    private List<Collection> allCollections = new ArrayList<>();
    private Map<Long, Collection> selectedCollectionsMap = new HashMap<>();

    private Uri selectedImageUri;
    private long seriesId;

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
        seriesId = getArguments() != null ? getArguments().getLong("seriesId", -1) : -1;

        if (seriesId != -1) {
            loadSeriesData(seriesId);
            loadAllCollections();
            loadSeriesCollections(seriesId);
            loadMediaFiles(seriesId);
        }

        // Настройка статус-спиннера
        setupStatusSpinner();

        // Настройка RecyclerView для медиафайлов
        setupMediaRecyclerView();

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
        editButton = view.findViewById(R.id.editButton);
        saveButton = view.findViewById(R.id.saveButton);
        deleteButton = view.findViewById(R.id.deleteButton);
        statusSpinner = view.findViewById(R.id.statusSpinner);
        favoriteCheckBox = view.findViewById(R.id.favoriteCheckBox);
        backButton = view.findViewById(R.id.backButton);
        collectionsButton = view.findViewById(R.id.collectionsButton);
        selectedCollectionsText = view.findViewById(R.id.selectedCollectionsText);
        collectionsTitle = view.findViewById(R.id.collectionsTitle);

        // Элементы для медиафайлов
        addMediaButton = view.findViewById(R.id.addMediaButton);
        mediaRecyclerView = view.findViewById(R.id.mediaRecyclerView);
    }

    private void setupMediaRecyclerView() {
        mediaAdapter = new MediaAdapter(new MediaAdapter.OnMediaClickListener() {
            @Override
            public void onMediaClick(MediaFile mediaFile, int position) {
                // Будем реализовывать в следующем шаге
                openMediaViewer(position);
            }

            @Override
            public void onMediaDelete(MediaFile mediaFile) {
                showDeleteMediaDialog(mediaFile);
            }
        });

        // ИЗМЕНИТЕ ЭТУ СТРОКУ: используйте GridLayoutManager
        GridLayoutManager gridLayoutManager = new GridLayoutManager(getContext(), 3); // 3 колонки
        mediaRecyclerView.setLayoutManager(gridLayoutManager);
        mediaRecyclerView.setAdapter(mediaAdapter);
    }
    private void setReadOnlyMode(boolean readOnly) {
        // Управление видимостью кнопок
        if (readOnly) {
            editButton.setVisibility(View.VISIBLE);
            saveButton.setVisibility(View.GONE);
            deleteButton.setVisibility(View.GONE);
        } else {
            editButton.setVisibility(View.GONE);
            saveButton.setVisibility(View.VISIBLE);
            deleteButton.setVisibility(View.VISIBLE);
        }

        // Управление редактируемостью полей
        titleEditText.setEnabled(!readOnly);
        titleEditText.setFocusable(!readOnly);
        titleEditText.setFocusableInTouchMode(!readOnly);

        notesEditText.setEnabled(!readOnly);
        notesEditText.setFocusable(!readOnly);
        notesEditText.setFocusableInTouchMode(!readOnly);

        genreEditText.setEnabled(!readOnly);
        genreEditText.setFocusable(!readOnly);
        genreEditText.setFocusableInTouchMode(!readOnly);

        seasonsEditText.setEnabled(!readOnly);
        seasonsEditText.setFocusable(!readOnly);
        seasonsEditText.setFocusableInTouchMode(!readOnly);

        episodesEditText.setEnabled(!readOnly);
        episodesEditText.setFocusable(!readOnly);
        episodesEditText.setFocusableInTouchMode(!readOnly);

        statusSpinner.setEnabled(!readOnly);
        favoriteCheckBox.setEnabled(!readOnly);

        selectImageButton.setEnabled(!readOnly);
        collectionsButton.setEnabled(!readOnly);
        addMediaButton.setEnabled(!readOnly);

        // Для RecyclerView медиафайлов нужно обновить адаптер
        if (mediaAdapter != null) {
            mediaAdapter.setEditMode(!readOnly);
        }
    }


    private void openMediaViewer(int position) {
        if (mediaFiles == null || mediaFiles.isEmpty()) {
            return;
        }

        // Создаем копию списка для передачи во фрагмент
        ArrayList<MediaFile> mediaList = new ArrayList<>(mediaFiles);

        // Создаем и показываем фрагмент просмотрщика
        MediaViewerFragment viewerFragment = MediaViewerFragment.newInstance(mediaList, position);

        requireActivity().getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, viewerFragment)
                .addToBackStack("media_viewer")
                .commit();
    }

    private void loadSeriesData(long seriesId) {
        viewModel.getSeriesById(seriesId).observe(getViewLifecycleOwner(), series -> {
            if (series != null) {
                this.series = series;
                populateForm(series);
            }
        });
    }

    private void loadAllCollections() {
        viewModel.getAllCollections().observe(getViewLifecycleOwner(), collections -> {
            if (collections != null && !collections.isEmpty()) {
                allCollections = collections;
                collectionsTitle.setVisibility(View.VISIBLE);
                collectionsButton.setVisibility(View.VISIBLE);
                selectedCollectionsText.setVisibility(View.VISIBLE);
            } else {
                allCollections.clear();
                collectionsTitle.setVisibility(View.GONE);
                collectionsButton.setVisibility(View.GONE);
                selectedCollectionsText.setVisibility(View.GONE);
            }
        });
    }

    private void loadSeriesCollections(long seriesId) {
        viewModel.getCollectionsForSeries(seriesId).observe(getViewLifecycleOwner(), seriesCollections -> {
            if (seriesCollections != null) {
                selectedCollectionsMap.clear();
                for (Collection collection : seriesCollections) {
                    selectedCollectionsMap.put(collection.getId(), collection);
                }
                updateSelectedCollectionsText();
            }
        });
    }

    private void loadMediaFiles(long seriesId) {
        viewModel.getMediaFilesForSeries(seriesId).observe(getViewLifecycleOwner(), files -> {
            if (files != null) {
                mediaFiles = files;
                mediaAdapter.setMediaFiles(files);
            }
        });
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

        // Загрузка основного изображения сериала
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

    private void updateSelectedCollectionsText() {
        if (selectedCollectionsMap.isEmpty()) {
            selectedCollectionsText.setText("Не выбрано");
        } else {
            StringBuilder sb = new StringBuilder();
            boolean first = true;
            for (Collection collection : selectedCollectionsMap.values()) {
                if (!first) {
                    sb.append(", ");
                }
                sb.append(collection.getName());
                first = false;
            }
            selectedCollectionsText.setText(sb.toString());
        }
    }

    private void showCollectionsDialog() {
        if (allCollections.isEmpty()) {
            Toast.makeText(getContext(), "Нет доступных коллекций", Toast.LENGTH_SHORT).show();
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle("Выберите коллекции");

        // Создаем массивы для диалога
        String[] collectionNames = new String[allCollections.size()];
        boolean[] checkedItems = new boolean[allCollections.size()];

        for (int i = 0; i < allCollections.size(); i++) {
            Collection collection = allCollections.get(i);
            collectionNames[i] = collection.getName();
            // Проверяем, выбрана ли коллекция
            checkedItems[i] = selectedCollectionsMap.containsKey(collection.getId());
        }

        // Создаем временную копию выбранных коллекций для работы в диалоге
        Map<Long, Collection> tempSelectedMap = new HashMap<>(selectedCollectionsMap);

        builder.setMultiChoiceItems(collectionNames, checkedItems, (dialog, which, isChecked) -> {
            Collection collection = allCollections.get(which);
            if (isChecked) {
                tempSelectedMap.put(collection.getId(), collection);
            } else {
                tempSelectedMap.remove(collection.getId());
            }
        });

        builder.setPositiveButton("Готово", (dialog, which) -> {
            // Обновляем основной список выбранных коллекций
            selectedCollectionsMap.clear();
            selectedCollectionsMap.putAll(tempSelectedMap);
            updateSelectedCollectionsText();
            dialog.dismiss();
        });

        builder.setNegativeButton("Отмена", (dialog, which) -> {
            dialog.dismiss();
        });

        AlertDialog dialog = builder.create();
        dialog.show();
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

        // Обработчик для выбора ОСНОВНОГО изображения сериала
        selectImageButton.setOnClickListener(v -> {
            if (checkSeriesImagePermission()) {
                openSeriesImagePicker();
            } else {
                requestSeriesImagePermission();
            }
        });

        statusSpinner.setOnClickListener(v -> {
            statusSpinner.showDropDown();
        });

        statusSpinner.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                statusSpinner.showDropDown();
            }
            return true;
        });

        // Устанавливаем начальный режим - только для просмотра
        setReadOnlyMode(true);

        editButton.setOnClickListener(v -> {
            setReadOnlyMode(false); // Переключаем в режим редактирования
        });
        collectionsButton.setOnClickListener(v -> {
            showCollectionsDialog();
        });

        // Обработчик для добавления МНОЖЕСТВЕННЫХ МЕДИАФАЙЛОВ
        addMediaButton.setOnClickListener(v -> {
            if (checkMediaPermission()) {
                openMultipleMediaPicker();
            } else {
                requestMediaPermission();
            }
        });

        saveButton.setOnClickListener(v -> saveSeries());

        deleteButton.setOnClickListener(v -> {
            if (series != null) {
                showDeleteConfirmationDialog();
            }
        });
    }

    private void openMultipleMediaPicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*"); // Все типы файлов
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[] {
                "image/*", // Изображения
                "video/*"  // Видео
        });
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true); // Разрешаем множественный выбор
        intent.addCategory(Intent.CATEGORY_OPENABLE);

        try {
            startActivityForResult(Intent.createChooser(intent, "Выберите файлы"), PICK_MULTIPLE_MEDIA_REQUEST);
        } catch (Exception e) {
            Toast.makeText(getContext(), "Не удалось открыть файловый менеджер", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean checkMediaPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(requireContext(),
                    Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(requireContext(),
                            Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED;
        } else {
            return ContextCompat.checkSelfPermission(requireContext(),
                    Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        }
    }

    private void requestMediaPermission() {
        String[] permissions;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions = new String[]{
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VIDEO
            };
        } else {
            permissions = new String[]{Manifest.permission.READ_EXTERNAL_STORAGE};
        }

        if (shouldShowRequestPermissionRationale(Manifest.permission.READ_EXTERNAL_STORAGE)) {
            new AlertDialog.Builder(requireContext())
                    .setTitle("Нужно разрешение")
                    .setMessage("Для выбора медиафайлов необходимо разрешение на доступ к галерее")
                    .setPositiveButton("OK", (dialog, which) -> {
                        requestPermissions(permissions, REQUEST_MEDIA_PERMISSION);
                    })
                    .setNegativeButton("Отмена", null)
                    .show();
        } else {
            requestPermissions(permissions, REQUEST_MEDIA_PERMISSION);
        }
    }

    private boolean checkSeriesImagePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(requireContext(),
                    Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED;
        } else {
            return ContextCompat.checkSelfPermission(requireContext(),
                    Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        }
    }

    private void requestSeriesImagePermission() {
        String[] permissions;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions = new String[]{Manifest.permission.READ_MEDIA_IMAGES};
        } else {
            permissions = new String[]{Manifest.permission.READ_EXTERNAL_STORAGE};
        }

        if (shouldShowRequestPermissionRationale(Manifest.permission.READ_EXTERNAL_STORAGE)) {
            new AlertDialog.Builder(requireContext())
                    .setTitle("Нужно разрешение")
                    .setMessage("Для выбора изображения сериала необходимо разрешение на доступ к галерее")
                    .setPositiveButton("OK", (dialog, which) -> {
                        requestPermissions(permissions, REQUEST_SERIES_IMAGE_PERMISSION);
                    })
                    .setNegativeButton("Отмена", null)
                    .show();
        } else {
            requestPermissions(permissions, REQUEST_SERIES_IMAGE_PERMISSION);
        }
    }

    private void openSeriesImagePicker() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(intent, PICK_SERIES_IMAGE_REQUEST);
    }

    private void openMediaFile(MediaFile mediaFile) {
        if (mediaFile == null || mediaFile.getFileUri() == null) {
            Toast.makeText(getContext(), "Не удалось открыть файл", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(Intent.ACTION_VIEW);
        Uri uri = Uri.parse(mediaFile.getFileUri());

        String mimeType = null;
        if (mediaFile.getFileType().equals("video")) {
            mimeType = "video/*";
        } else if (mediaFile.getFileType().equals("image")) {
            mimeType = "image/*";
        }

        if (mimeType != null) {
            intent.setDataAndType(uri, mimeType);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            try {
                startActivity(intent);
            } catch (Exception e) {
                Toast.makeText(getContext(),
                        "Не найдено приложение для открытия этого файла",
                        Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void showDeleteMediaDialog(MediaFile mediaFile) {
        if (mediaFile == null) return;

        new AlertDialog.Builder(requireContext())
                .setTitle("Удаление файла")
                .setMessage("Вы уверены, что хотите удалить файл \"" +
                        (mediaFile.getFileName() != null ? mediaFile.getFileName() : "файл") + "\"?")
                .setPositiveButton("Удалить", (dialog, which) -> {
                    viewModel.deleteMediaFile(mediaFile.getId());
                    Toast.makeText(getContext(), "Файл удален", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void showDeleteConfirmationDialog() {
        if (series == null) return;

        new AlertDialog.Builder(requireContext())
                .setTitle("Удаление сериала")
                .setMessage("Вы уверены, что хотите удалить сериал \"" + series.getTitle() + "\"?")
                .setPositiveButton("Удалить", (dialog, which) -> {
                    deleteSeries();
                })
                .setNegativeButton("Отмена", (dialog, which) -> {
                    dialog.dismiss();
                })
                .show();
    }

    private void deleteSeries() {
        if (series != null) {
            viewModel.deleteSeries(series.getId());
            Toast.makeText(getContext(), "Сериал удален", Toast.LENGTH_SHORT).show();
            requireActivity().getSupportFragmentManager().popBackStack();
        }
    }

    private void saveSeries() {
        if (series == null || !isAdded()) return; // Проверка isAdded()

        String newTitle = titleEditText.getText().toString().trim();
        if (newTitle.isEmpty()) {
            Toast.makeText(getContext(), "Введите название сериала", Toast.LENGTH_SHORT).show();
            return;
        }

        // Сохраняем ссылку на ViewModel локально
        SeriesViewModel localViewModel = viewModel;
        if (localViewModel == null) return;

        // Проверяем, изменилось ли название
        if (!newTitle.equals(series.getTitle())) {
            // Создаем временный LiveData наблюдатель
            localViewModel.doesSeriesExist(newTitle).observe(getViewLifecycleOwner(), exists -> {
                if (!isAdded() || getContext() == null) return; // Проверка

                if (exists != null && exists) {
                    Toast.makeText(getContext(),
                            "Сериал \"" + newTitle + "\" уже существует",
                            Toast.LENGTH_LONG).show();
                    titleEditText.setText(series.getTitle());
                    titleEditText.requestFocus();
                } else {
                    updateSeriesData(newTitle);
                }
            });
        } else {
            updateSeriesData(newTitle);
        }
    }

    private void updateSeriesData(String title) {
        if (!isAdded() || getContext() == null || series == null) return;

        // 1. Обновляем данные сериала
        series.setTitle(title);
        series.setNotes(notesEditText.getText().toString().trim());
        series.setGenre(genreEditText.getText().toString().trim());

        try {
            series.setSeasons(Integer.parseInt(seasonsEditText.getText().toString()));
        } catch (NumberFormatException e) {
            series.setSeasons(0);
        }

        try {
            series.setEpisodes(Integer.parseInt(episodesEditText.getText().toString()));
        } catch (NumberFormatException e) {
            series.setEpisodes(0);
        }

        if (selectedImageUri != null) {
            series.setImageUri(selectedImageUri.toString());
        }

        String selectedStatus = statusSpinner.getText().toString();
        series.setStatus(getStatusValue(selectedStatus));
        series.setIsWatched("Завершено".equals(selectedStatus));
        series.setIsFavorite(favoriteCheckBox.isChecked());

        // 2. Сохраняем сериал в БД
        if (viewModel != null) {
            viewModel.updateSeries(series);
        }

        // 3. Создаем список ID выбранных коллекций
        List<Long> selectedCollectionIds = new ArrayList<>();
        for (Long collectionId : selectedCollectionsMap.keySet()) {
            selectedCollectionIds.add(collectionId);
        }

        // 4. Обновляем связи с коллекциями
        if (viewModel != null) {
            viewModel.updateSeriesCollections(series.getId(), selectedCollectionIds);
        }

        // 5. Показываем сообщение об успехе
        Toast.makeText(getContext(), "Сериал обновлен", Toast.LENGTH_SHORT).show();

        // 6. Возвращаемся назад БЕЗ ЗАДЕРЖКИ
        if (isAdded() && getActivity() != null) {
            requireActivity().runOnUiThread(() -> {
                if (isAdded()) {
                    requireActivity().getSupportFragmentManager().popBackStack();
                }
            });
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_SERIES_IMAGE_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openSeriesImagePicker();
            } else {
                Toast.makeText(getContext(), "Нужно разрешение для выбора изображения сериала",
                        Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == REQUEST_MEDIA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Разрешение получено, можно открыть выбор файлов
                openMultipleMediaPicker();
            } else {
                Toast.makeText(getContext(), "Нужно разрешение для выбора медиафайлов",
                        Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == getActivity().RESULT_OK) {
            if (requestCode == PICK_SERIES_IMAGE_REQUEST && data != null && data.getData() != null) {
                // Обработка выбора ОСНОВНОГО изображения сериала
                selectedImageUri = data.getData();
                if (selectedImageUri != null) {
                    Glide.with(this)
                            .load(selectedImageUri)
                            .placeholder(R.drawable.ic_baseline_image_24)
                            .into(seriesImageView);
                }
                Toast.makeText(getContext(), "Изображение сериала обновлено", Toast.LENGTH_SHORT).show();

            } else if (requestCode == PICK_MULTIPLE_MEDIA_REQUEST && data != null) {
                // Обработка множественного выбора медиафайлов
                handleMultipleMediaSelection(data);
            }
        }
    }

    private void handleMultipleMediaSelection(Intent data) {
        List<Uri> selectedUris = new ArrayList<>();

        // Проверяем, выбрал ли пользователь несколько файлов
        if (data.getClipData() != null) {
            // Множественный выбор
            int count = data.getClipData().getItemCount();
            for (int i = 0; i < count; i++) {
                Uri fileUri = data.getClipData().getItemAt(i).getUri();
                selectedUris.add(fileUri);
            }
        } else if (data.getData() != null) {
            // Одиночный выбор (для обратной совместимости)
            selectedUris.add(data.getData());
        }

        if (selectedUris.isEmpty()) {
            Toast.makeText(getContext(), "Файлы не выбраны", Toast.LENGTH_SHORT).show();
            return;
        }

        // Обрабатываем каждый выбранный файл
        int successCount = 0;
        int errorCount = 0;

        for (Uri uri : selectedUris) {
            try {
                String fileType = determineFileType(uri);
                if (addMediaFile(uri, fileType)) {
                    successCount++;
                } else {
                    errorCount++;
                }
            } catch (Exception e) {
                errorCount++;
                e.printStackTrace();
            }
        }

        // Показываем результат
        if (successCount > 0) {
            String message = "Добавлено файлов: " + successCount;
            if (errorCount > 0) {
                message += ", ошибок: " + errorCount;
            }
            Toast.makeText(getContext(), message, Toast.LENGTH_LONG).show();
        } else if (errorCount > 0) {
            Toast.makeText(getContext(), "Не удалось добавить файлы", Toast.LENGTH_SHORT).show();
        }
    }

    private String determineFileType(Uri uri) {
        String mimeType = requireContext().getContentResolver().getType(uri);

        if (mimeType != null) {
            if (mimeType.startsWith("image/")) {
                return "image";
            } else if (mimeType.startsWith("video/")) {
                return "video";
            }
        }

        // Попробуем определить по расширению
        String uriString = uri.toString().toLowerCase();
        if (uriString.contains(".jpg") || uriString.contains(".jpeg") ||
                uriString.contains(".png") || uriString.contains(".gif") ||
                uriString.contains(".webp") || uriString.contains(".bmp") ||
                uriString.contains(".heic") || uriString.contains(".heif")) {
            return "image";
        } else if (uriString.contains(".mp4") || uriString.contains(".avi") ||
                uriString.contains(".mkv") || uriString.contains(".mov") ||
                uriString.contains(".wmv") || uriString.contains(".flv") ||
                uriString.contains(".3gp") || uriString.contains(".mpeg") ||
                uriString.contains(".mpg")) {
            return "video";
        }

        return "file"; // Неизвестный тип
    }

    private boolean addMediaFile(Uri uri, String fileType) {
        try {
            String fileName = getFileName(uri);

            // Копируем файл в внутреннее хранилище приложения
            Uri copiedUri = copyFileToInternalStorage(uri, fileName);
            if (copiedUri != null) {
                MediaFile mediaFile = new MediaFile(seriesId,
                        copiedUri.toString(),
                        fileType,
                        fileName);

                // Получаем путь к файлу
                String filePath = getRealPathFromURI(copiedUri);
                if (filePath != null) {
                    mediaFile.setFilePath(filePath);
                }

                // Получаем размер файла
                long fileSize = getFileSize(copiedUri);
                mediaFile.setFileSize(fileSize);

                // Добавляем медиафайл в коллекцию
                viewModel.addMediaFile(mediaFile);
                return true;
            } else {
                // Если не удалось скопировать, сохраняем оригинальный URI как fallback
                MediaFile mediaFile = new MediaFile(seriesId,
                        uri.toString(),
                        fileType,
                        fileName);


                // Получаем путь к файлу
                String filePath = getRealPathFromURI(uri);
                if (filePath != null) {
                    mediaFile.setFilePath(filePath);
                }

                // Получаем размер файла
                long fileSize = getFileSize(uri);
                mediaFile.setFileSize(fileSize);

                // Добавляем медиафайл в коллекцию
                viewModel.addMediaFile(mediaFile);
                return true;
            }

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private String getFileName(Uri uri) {
        String fileName = null;

        if (uri.getScheme().equals("content")) {
            try (Cursor cursor = requireContext().getContentResolver()
                    .query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (nameIndex != -1) {
                        fileName = cursor.getString(nameIndex);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        if (fileName == null) {
            fileName = uri.getPath();
            if (fileName != null) {
                int cut = fileName.lastIndexOf('/');
                if (cut != -1) {
                    fileName = fileName.substring(cut + 1);
                }
            }
        }

        // Если все еще null, генерируем имя
        if (fileName == null) {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault());
            fileName = "file_" + sdf.format(new Date());

            // Добавляем расширение если можем определить тип
            String fileType = determineFileType(uri);
            if (fileType.equals("image")) {
                fileName += ".jpg";
            } else if (fileType.equals("video")) {
                fileName += ".mp4";
            }
        }

        return fileName;
    }

    /**
     * Копирует файл из внешнего источника во внутреннее хранилище приложения
     */
    private Uri copyFileToInternalStorage(Uri sourceUri, String fileName) {
        try {
            // Создаем подкаталог для медиафайлов внутри внутреннего хранилища
            File mediaDir = new File(requireContext().getFilesDir(), "media");
            if (!mediaDir.exists()) {
                mediaDir.mkdirs();
            }

            // Создаем уникальное имя файла
            String uniqueFileName = generateUniqueFileName(fileName, mediaDir);
            File destinationFile = new File(mediaDir, uniqueFileName);

            // Копируем содержимое из источника в назначение
            try (InputStream inputStream = requireContext().getContentResolver().openInputStream(sourceUri);
                 FileOutputStream outputStream = new FileOutputStream(destinationFile)) {

                if (inputStream == null) {
                    return null;
                }

                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
                outputStream.flush();

                // Возвращаем URI для внутреннего файла
                return Uri.fromFile(destinationFile);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Генерирует уникальное имя файла, если файл с таким именем уже существует
     */
    private String generateUniqueFileName(String originalFileName, File directory) {
        String nameWithoutExtension = originalFileName;
        String extension = "";

        int dotIndex = originalFileName.lastIndexOf('.');
        if (dotIndex > 0) {
            nameWithoutExtension = originalFileName.substring(0, dotIndex);
            extension = originalFileName.substring(dotIndex);
        }

        String uniqueFileName = originalFileName;
        int counter = 1;
        File testFile = new File(directory, uniqueFileName);

        while (testFile.exists()) {
            uniqueFileName = nameWithoutExtension + "_" + counter + extension;
            testFile = new File(directory, uniqueFileName);
            counter++;
        }

        return uniqueFileName;
    }
    private long getFileSize(Uri uri) {
        long size = 0;

        if (uri.getScheme().equals("content")) {
            try (Cursor cursor = requireContext().getContentResolver()
                    .query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
                    if (sizeIndex != -1) {
                        size = cursor.getLong(sizeIndex);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return size;
    }

    private String getRealPathFromURI(Uri uri) {
        if (uri == null) return null;

        final String scheme = uri.getScheme();

        if (scheme == null) {
            return uri.getPath();
        }

        if (scheme.equals("file")) {
            return uri.getPath();
        }

        if (scheme.equals("content")) {
            if (DocumentsContract.isDocumentUri(requireContext(), uri)) {
                if (isExternalStorageDocument(uri)) {
                    final String docId = DocumentsContract.getDocumentId(uri);
                    final String[] split = docId.split(":");
                    final String type = split[0];

                    if ("primary".equalsIgnoreCase(type)) {
                        return android.os.Environment.getExternalStorageDirectory() + "/" + split[1];
                    }
                } else if (isDownloadsDocument(uri)) {
                    final String id = DocumentsContract.getDocumentId(uri);
                    final Uri contentUri = ContentUris.withAppendedId(
                            Uri.parse("content://downloads/public_downloads"), Long.parseLong(id));

                    return getDataColumn(contentUri, null, null);
                } else if (isMediaDocument(uri)) {
                    final String docId = DocumentsContract.getDocumentId(uri);
                    final String[] split = docId.split(":");
                    final String type = split[0];

                    Uri contentUri = null;
                    switch (type) {
                        case "image":
                            contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                            break;
                        case "video":
                            contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
                            break;
                        case "audio":
                            contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                            break;
                    }

                    final String selection = "_id=?";
                    final String[] selectionArgs = new String[]{split[1]};

                    return getDataColumn(contentUri, selection, selectionArgs);
                }
            } else if ("content".equalsIgnoreCase(scheme)) {
                return getDataColumn(uri, null, null);
            }
        }

        return null;
    }

    private String getDataColumn(Uri uri, String selection, String[] selectionArgs) {
        String path = null;
        final String column = MediaStore.MediaColumns.DATA;
        final String[] projection = {column};

        try (Cursor cursor = requireContext().getContentResolver().query(
                uri, projection, selection, selectionArgs, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int columnIndex = cursor.getColumnIndexOrThrow(column);
                path = cursor.getString(columnIndex);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return path;
    }

    private boolean isExternalStorageDocument(Uri uri) {
        return "com.android.externalstorage.documents".equals(uri.getAuthority());
    }

    private boolean isDownloadsDocument(Uri uri) {
        return "com.android.providers.downloads.documents".equals(uri.getAuthority());
    }

    private boolean isMediaDocument(Uri uri) {
        return "com.android.providers.media.documents".equals(uri.getAuthority());
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

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Очищаем все наблюдатели
        if (viewModel != null && series != null) {
            viewModel.getSeriesById(series.getId()).removeObservers(getViewLifecycleOwner());
            viewModel.getAllCollections().removeObservers(getViewLifecycleOwner());
            viewModel.getCollectionsForSeries(series.getId()).removeObservers(getViewLifecycleOwner());
            viewModel.getMediaFilesForSeries(series.getId()).removeObservers(getViewLifecycleOwner());
            viewModel.doesSeriesExist("").removeObservers(getViewLifecycleOwner());
        }

        // Очищаем ссылки на UI элементы
        titleEditText = null;
        notesEditText = null;
        genreEditText = null;
        seasonsEditText = null;
        episodesEditText = null;
        seriesImageView = null;
        // ... остальные UI элементы ...
    }
}