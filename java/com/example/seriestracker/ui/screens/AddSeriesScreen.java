
package com.example.seriestracker.ui.screens;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.bumptech.glide.Glide;
import com.example.seriestracker.R;
import com.example.seriestracker.data.entities.Collection;
import com.example.seriestracker.ui.utils.WatchLinkSearchDialog;
import com.example.seriestracker.ui.viewmodels.SeriesViewModel;
import com.example.seriestracker.utils.MediaStorageHelper;
import com.example.seriestracker.utils.ShareTextHelper;
import com.example.seriestracker.utils.WatchLinkTextHelper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class AddSeriesScreen extends Fragment {

    private static final int PICK_IMAGE_REQUEST = 1;
    private static final String ARG_SHARED_TEXT = "shared_text";

    private SeriesViewModel viewModel;
    private EditText titleEditText;
    private EditText watchAtEditText;
    private View searchWatchAtButton;
    private EditText watchUrlEditText;
    private EditText notesEditText;
    private ImageView seriesImageView;
    private Button selectImageButton;
    private Button saveButton;
    private LinearLayout collectionsLayout;
    private LinearLayout collectionsHeader;
    private LinearLayout collectionsExpandedContent;
    private EditText collectionsSearchEditText;
    private TextView collectionsHeaderTitle;
    private TextView collectionsHeaderSubtitle;
    private ImageView collectionsExpandIcon;
    private ImageButton backButton;

    private Uri selectedImageUri;
    private final Set<Long> selectedCollectionIds = new HashSet<>();
    private final Map<Long, Collection> allCollectionsById = new LinkedHashMap<>();
    private boolean collectionsExpanded = false;
    private boolean isChecking = false; // Флаг для предотвращения повторных проверок
    private String sharedText; // Текст, полученный через функцию "Поделиться"

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

        // Получаем текст, если он был передан через функцию "Поделиться"
        if (getArguments() != null) {
            sharedText = getArguments().getString(ARG_SHARED_TEXT);
        }

        // Инициализация элементов
        initViews(view);

        // Если есть текст из функции "Поделиться", вставляем его в поле ссылки
        if (sharedText != null && !sharedText.isEmpty()) {
            watchUrlEditText.setText(ShareTextHelper.extractWatchUrl(sharedText));
        }

        // Устанавливаем фокус на первое поле
        titleEditText.requestFocus();

        // Загрузка коллекций
        loadCollections();

        // Настройка обработчиков событий
        setupEventListeners();
    }

    private void initViews(View view) {
        titleEditText = view.findViewById(R.id.titleEditText);
        watchAtEditText = view.findViewById(R.id.watchAtEditText);
        searchWatchAtButton = view.findViewById(R.id.searchWatchAtButton);
        watchUrlEditText = view.findViewById(R.id.watchUrlEditText);
        notesEditText = view.findViewById(R.id.notesEditText);
        seriesImageView = view.findViewById(R.id.seriesImageView);
        selectImageButton = view.findViewById(R.id.selectImageButton);
        saveButton = view.findViewById(R.id.saveButton);
        collectionsLayout = view.findViewById(R.id.collectionsLayout);
        collectionsHeader = view.findViewById(R.id.collectionsHeader);
        collectionsExpandedContent = view.findViewById(R.id.collectionsExpandedContent);
        collectionsSearchEditText = view.findViewById(R.id.collectionsSearchEditText);
        collectionsHeaderTitle = view.findViewById(R.id.collectionsHeaderTitle);
        collectionsHeaderSubtitle = view.findViewById(R.id.collectionsHeaderSubtitle);
        collectionsExpandIcon = view.findViewById(R.id.collectionsExpandIcon);
        backButton = view.findViewById(R.id.backButton);
    }

    private void loadCollections() {
        viewModel.getAllCollections().observe(getViewLifecycleOwner(), collections -> {
            allCollectionsById.clear();
            if (collections != null) {
                for (Collection collection : collections) {
                    allCollectionsById.put(collection.getId(), collection);
                }
            }
            selectedCollectionIds.retainAll(allCollectionsById.keySet());
            renderCollectionsList();
            updateCollectionsHeader();
        });
    }

    private void setupEventListeners() {
        // Кнопка назад
        if (backButton != null) {
            backButton.setOnClickListener(v -> {
                requireActivity().getSupportFragmentManager().popBackStack();
            });
        }

        selectImageButton.setOnClickListener(v -> openImagePicker());

        if (searchWatchAtButton != null) {
            searchWatchAtButton.setOnClickListener(v -> searchWatchLinks());
        }

        collectionsHeader.setOnClickListener(v -> toggleCollectionsExpanded());

        collectionsSearchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                renderCollectionsList();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        // Сохранение сериала
        saveButton.setOnClickListener(v -> saveSeries());
    }

    private void toggleCollectionsExpanded() {
        collectionsExpanded = !collectionsExpanded;
        collectionsExpandedContent.setVisibility(collectionsExpanded ? View.VISIBLE : View.GONE);
        collectionsExpandIcon.setImageResource(collectionsExpanded
                ? R.drawable.ic_baseline_keyboard_arrow_up_24
                : R.drawable.ic_baseline_keyboard_arrow_down_24);
        collectionsExpandIcon.setContentDescription(collectionsExpanded
                ? "Свернуть список коллекций"
                : "Развернуть список коллекций");
        if (collectionsExpanded) {
            renderCollectionsList();
        }
    }

    private void renderCollectionsList() {
        collectionsLayout.removeAllViews();

        if (allCollectionsById.isEmpty()) {
            TextView emptyText = new TextView(requireContext());
            emptyText.setText("Нет доступных коллекций. Создайте коллекцию сначала.");
            emptyText.setTextSize(14);
            emptyText.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_gray));
            emptyText.setPadding(16, 16, 16, 16);
            collectionsLayout.addView(emptyText);
            return;
        }

        String query = collectionsSearchEditText.getText() != null
                ? collectionsSearchEditText.getText().toString().trim().toLowerCase(Locale.getDefault())
                : "";

        boolean hasVisible = false;
        for (Collection collection : allCollectionsById.values()) {
            String name = collection.getName() != null ? collection.getName() : "";
            if (!query.isEmpty() && !name.toLowerCase(Locale.getDefault()).contains(query)) {
                continue;
            }

            hasVisible = true;
            CheckBox checkBox = new CheckBox(requireContext());
            checkBox.setText(name);
            checkBox.setTag(collection.getId());
            checkBox.setTextSize(16);
            checkBox.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_dark));
            checkBox.setPadding(16, 12, 16, 12);
            checkBox.setChecked(selectedCollectionIds.contains(collection.getId()));
            checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                long collectionId = (long) buttonView.getTag();
                if (isChecked) {
                    selectedCollectionIds.add(collectionId);
                } else {
                    selectedCollectionIds.remove(collectionId);
                }
                updateCollectionsHeader();
            });
            collectionsLayout.addView(checkBox);
        }

        if (!hasVisible) {
            TextView emptySearchText = new TextView(requireContext());
            emptySearchText.setText("Ничего не найдено");
            emptySearchText.setTextSize(14);
            emptySearchText.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_gray));
            emptySearchText.setPadding(16, 16, 16, 16);
            collectionsLayout.addView(emptySearchText);
        }
    }

    private void updateCollectionsHeader() {
        if (allCollectionsById.isEmpty()) {
            collectionsHeaderTitle.setText("Коллекции");
            collectionsHeaderSubtitle.setText("Нет доступных коллекций");
            return;
        }

        int selectedCount = selectedCollectionIds.size();
        if (selectedCount == 0) {
            collectionsHeaderTitle.setText("Выбрать коллекции");
            collectionsHeaderSubtitle.setText("Не выбрано");
            return;
        }

        collectionsHeaderTitle.setText("Выбрано: " + selectedCount);
        StringBuilder names = new StringBuilder();
        for (Collection collection : allCollectionsById.values()) {
            if (!selectedCollectionIds.contains(collection.getId())) {
                continue;
            }
            if (names.length() > 0) {
                names.append(", ");
            }
            names.append(collection.getName());
        }
        collectionsHeaderSubtitle.setText(names.toString());
    }

    private void searchWatchLinks() {
        String title = titleEditText.getText() != null
                ? titleEditText.getText().toString().trim()
                : "";
        WatchLinkSearchDialog.show(this, title, urls -> {
            String merged = WatchLinkTextHelper.mergeUrls(
                    watchAtEditText.getText().toString(), urls);
            watchAtEditText.setText(merged);
            Toast.makeText(getContext(), R.string.watch_links_added, Toast.LENGTH_SHORT).show();
        });
    }

    private void updateExistingSeries(String title, String watchAt, String watchUrl, String notes) {
        viewModel.updateExistingSeries(title, watchAt, watchUrl, notes).observe(getViewLifecycleOwner(), outcome -> {
            if (outcome == null) {
                return;
            }

            if (outcome.seriesFound) {
                Toast.makeText(getContext(),
                        "Сериал \"" + title + "\" обновлён",
                        Toast.LENGTH_LONG).show();
                openSeriesScreen(outcome.seriesId);
            } else {
                Toast.makeText(getContext(),
                        "Сериал \"" + title + "\" уже существует",
                        Toast.LENGTH_LONG).show();
                titleEditText.requestFocus();
            }
        });
    }

    private void openSeriesScreen(long seriesId) {
        androidx.fragment.app.FragmentManager fragmentManager =
                requireActivity().getSupportFragmentManager();
        fragmentManager.popBackStack(null,
                androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE);

        fragmentManager.beginTransaction()
                .replace(R.id.fragment_container, new MainScreen())
                .commitNow();

        EditSeriesScreen editScreen = EditSeriesScreen.newInstance(seriesId);
        fragmentManager.beginTransaction()
                .replace(R.id.fragment_container, editScreen)
                .addToBackStack(null)
                .commit();
    }

    private void saveSeries() {
        String title = titleEditText.getText().toString().trim();
        String watchAt = watchAtEditText.getText().toString().trim();
        String watchUrl = watchUrlEditText.getText().toString().trim();
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

            if (exists != null && exists) {
                if (watchAt.isEmpty() && watchUrl.isEmpty() && notes.isEmpty()) {
                    Toast.makeText(getContext(),
                            "Сериал \"" + title + "\" уже существует",
                            Toast.LENGTH_LONG).show();
                    titleEditText.requestFocus();
                } else {
                    updateExistingSeries(title, watchAt, watchUrl, notes);
                }
            } else {
                // Сериала нет, создаем
                String imageUri = null;
                if (selectedImageUri != null) {
                    String fileName = MediaStorageHelper.getDisplayName(requireContext(), selectedImageUri);
                    imageUri = MediaStorageHelper.copyCoverToInternalStorage(requireContext(), selectedImageUri, fileName);
                    if (imageUri == null) {
                        Toast.makeText(getContext(), "Не удалось сохранить обложку", Toast.LENGTH_SHORT).show();
                        return;
                    }
                }

                List<Long> collectionIds = new ArrayList<>(selectedCollectionIds);
                viewModel.addSeries(title, imageUri, collectionIds, notes, watchAt, watchUrl, seriesId -> {
                    if (!isAdded()) {
                        return;
                    }
                    Toast.makeText(getContext(), "Сериал добавлен!", Toast.LENGTH_SHORT).show();
                    openSeriesScreen(seriesId);
                });
            }
        });
    }

    private void openImagePicker() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(intent, PICK_IMAGE_REQUEST);
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