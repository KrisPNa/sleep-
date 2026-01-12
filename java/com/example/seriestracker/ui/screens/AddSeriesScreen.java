package com.example.seriestracker.ui.screens;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
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
import com.example.seriestracker.ui.viewmodels.SeriesViewModel;

import java.util.ArrayList;
import java.util.List;

public class AddSeriesScreen extends Fragment {

    private static final int PICK_IMAGE_REQUEST = 1;
    private static final int REQUEST_READ_EXTERNAL_STORAGE = 2;

    private SeriesViewModel viewModel;
    private EditText titleEditText;
    private EditText notesEditText;
    private ImageView seriesImageView;
    private Button selectImageButton;
    private Button saveButton;
    private LinearLayout collectionsLayout;
    private ImageButton backButton;

    private Uri selectedImageUri;
    private final List<Long> selectedCollectionIds = new ArrayList<>();
    private boolean isChecking = false; // Флаг для предотвращения повторных проверок

    public AddSeriesScreen() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_add_series, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(requireActivity()).get(SeriesViewModel.class);

        // Инициализация элементов
        initViews(view);

        // Устанавливаем фокус на первое поле
        titleEditText.requestFocus();

        // Загрузка коллекций
        loadCollections();

        // Настройка обработчиков событий
        setupEventListeners();
    }

    private void initViews(View view) {
        titleEditText = view.findViewById(R.id.titleEditText);
        notesEditText = view.findViewById(R.id.notesEditText);
        seriesImageView = view.findViewById(R.id.seriesImageView);
        selectImageButton = view.findViewById(R.id.selectImageButton);
        saveButton = view.findViewById(R.id.saveButton);
        collectionsLayout = view.findViewById(R.id.collectionsLayout);
        backButton = view.findViewById(R.id.backButton);
    }

    private void loadCollections() {
        viewModel.getAllCollections().observe(getViewLifecycleOwner(), collections -> {
            collectionsLayout.removeAllViews();
            selectedCollectionIds.clear();

            if (collections != null && !collections.isEmpty()) {
                for (Collection collection : collections) {
                    CheckBox checkBox = new CheckBox(requireContext());
                    checkBox.setText(collection.getName());
                    checkBox.setTag(collection.getId());
                    checkBox.setTextSize(16);
                    checkBox.setTextColor(getResources().getColor(R.color.text_dark));
                    checkBox.setPadding(16, 12, 16, 12);

                    checkBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                        @Override
                        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                            long collectionId = (long) buttonView.getTag();
                            if (isChecked) {
                                selectedCollectionIds.add(collectionId);
                            } else {
                                selectedCollectionIds.remove(collectionId);
                            }
                        }
                    });

                    collectionsLayout.addView(checkBox);
                }
            } else {
                // Если нет коллекций, показываем сообщение
                android.widget.TextView noCollectionsText = new android.widget.TextView(requireContext());
                noCollectionsText.setText("Нет доступных коллекций. Создайте коллекцию сначала.");
                noCollectionsText.setTextSize(14);
                noCollectionsText.setTextColor(getResources().getColor(R.color.text_gray));
                noCollectionsText.setPadding(16, 16, 16, 16);
                collectionsLayout.addView(noCollectionsText);
            }
        });
    }

    private void setupEventListeners() {
        // Кнопка назад
        if (backButton != null) {
            backButton.setOnClickListener(v -> {
                requireActivity().getSupportFragmentManager().popBackStack();
            });
        }

        // Выбор изображения
        selectImageButton.setOnClickListener(v -> {
            if (checkPermission()) {
                openImagePicker();
            } else {
                requestPermission();
            }
        });

        // Сохранение сериала
        saveButton.setOnClickListener(v -> saveSeries());
    }

    private void saveSeries() {
        String title = titleEditText.getText().toString().trim();
        String notes = notesEditText.getText().toString().trim();

        if (title.isEmpty()) {
            Toast.makeText(getContext(), "Введите название сериала", Toast.LENGTH_SHORT).show();
            titleEditText.requestFocus();
            return;
        }

        // Проверяем, не выполняется ли уже проверка
        if (isChecking) {
            return;
        }

        isChecking = true;

        // Проверяем, существует ли уже такой сериал
        viewModel.doesSeriesExist(title).observe(getViewLifecycleOwner(), exists -> {
            isChecking = false;

            if (exists != null && exists) { // exists это Boolean
                // Сериал уже существует
                Toast.makeText(getContext(),
                        "Сериал \"" + title + "\" уже существует",
                        Toast.LENGTH_LONG).show();
                titleEditText.requestFocus();
            } else {
                // Сериала нет, создаем
                String imageUri = selectedImageUri != null ? selectedImageUri.toString() : null;

                viewModel.addSeries(title, imageUri, selectedCollectionIds, notes);
                Toast.makeText(getContext(), "Сериал добавлен!", Toast.LENGTH_SHORT).show();

                // Возвращаемся на главный экран
                requireActivity().getSupportFragmentManager().popBackStack();
            }
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
        String[] permissions;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions = new String[]{Manifest.permission.READ_MEDIA_IMAGES};
        } else {
            permissions = new String[]{Manifest.permission.READ_EXTERNAL_STORAGE};
        }

        ActivityCompat.requestPermissions(requireActivity(), permissions,
                REQUEST_READ_EXTERNAL_STORAGE);
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
}