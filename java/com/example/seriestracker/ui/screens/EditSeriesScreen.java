
package com.example.seriestracker.ui.screens;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
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
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.drawable.DrawableCompat;
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
import com.example.seriestracker.ui.utils.WatchLinkMovementMethod;
import com.example.seriestracker.ui.utils.WatchLinkSearchDialog;
import com.example.seriestracker.ui.viewmodels.SeriesViewModel;
import com.example.seriestracker.utils.BrowserOpenHelper;
import com.example.seriestracker.utils.MediaStorageHelper;
import com.example.seriestracker.utils.WatchLinkTextHelper;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class EditSeriesScreen extends Fragment {

    private static final int PICK_SERIES_IMAGE_REQUEST = 1;
    private static final int PICK_MULTIPLE_MEDIA_REQUEST = 3;

    private SeriesViewModel viewModel;
    private Series series;

    // UI элементы
    private EditText titleEditText;
    private EditText notesEditText;
    private TextView notesTextView;
    private TextView watchUrlLabel;
    private EditText watchUrlEditText;
    private TextView watchUrlTextView;
    private TextView watchAtLabel;
    private View watchAtEditContainer;
    private EditText watchAtEditText;
    private ImageButton searchWatchAtButton;
    private TextView watchAtTextView;
    private com.google.android.material.button.MaterialButton playWatchButton;
    private EditText genreEditText;
    private EditText seasonsEditText;
    private EditText episodesEditText;
    private ImageView seriesImageView;
    private ImageView coverBlurBackground;
    private Button selectImageButton;
    private ImageButton deleteCoverButton;
    private ImageButton editButton;
    private Button saveButton;
    private Button deleteButton;
    private MaterialAutoCompleteTextView statusSpinner;
    private ImageButton favoriteButton;
    private boolean favoriteSelected = false;
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
    private boolean coverMarkedForRemoval;
    private long seriesId;
    private boolean readOnlyMode = true;
    private boolean exitAfterSave;

    private String originalTitle = "";
    private String originalWatchAt = "";
    private String originalWatchUrl = "";
    private String originalNotes = "";
    private String originalGenre = "";
    private String originalSeasons = "";
    private String originalEpisodes = "";
    private String originalStatus = "";
    private boolean originalFavorite;
    private Set<Long> originalCollectionIds = new HashSet<>();

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

        requireActivity().getOnBackPressedDispatcher().addCallback(
                getViewLifecycleOwner(),
                new OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        handleBackNavigation();
                    }
                });
    }

    private void initViews(View view) {
        titleEditText = view.findViewById(R.id.titleEditText);
        notesEditText = view.findViewById(R.id.notesEditText);
        notesTextView = view.findViewById(R.id.notesTextView);
        watchUrlLabel = view.findViewById(R.id.watchUrlLabel);
        watchUrlEditText = view.findViewById(R.id.watchUrlEditText);
        watchUrlTextView = view.findViewById(R.id.watchUrlTextView);
        watchAtLabel = view.findViewById(R.id.watchAtLabel);
        watchAtEditContainer = view.findViewById(R.id.watchAtEditContainer);
        watchAtEditText = view.findViewById(R.id.watchAtEditText);
        searchWatchAtButton = view.findViewById(R.id.searchWatchAtButton);
        watchAtTextView = view.findViewById(R.id.watchAtTextView);
        playWatchButton = view.findViewById(R.id.playWatchButton);
        genreEditText = view.findViewById(R.id.genreEditText);
        seasonsEditText = view.findViewById(R.id.seasonsEditText);
        episodesEditText = view.findViewById(R.id.episodesEditText);
        seriesImageView = view.findViewById(R.id.seriesImageView);
        coverBlurBackground = view.findViewById(R.id.coverBlurBackground);
        selectImageButton = view.findViewById(R.id.selectImageButton);
        deleteCoverButton = view.findViewById(R.id.deleteCoverButton);
        editButton = view.findViewById(R.id.editButton);
        saveButton = view.findViewById(R.id.saveButton);
        deleteButton = view.findViewById(R.id.deleteButton);
        statusSpinner = view.findViewById(R.id.statusSpinner);
        favoriteButton = view.findViewById(R.id.favoriteButton);
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
        readOnlyMode = readOnly;

        // Управление видимостью кнопок
        if (readOnly) {
            editButton.setVisibility(View.VISIBLE);
            saveButton.setVisibility(View.GONE);
            deleteButton.setVisibility(View.GONE);

            selectImageButton.setVisibility(View.GONE);
            addMediaButton.setVisibility(View.GONE);
            collectionsButton.setVisibility(View.GONE);

            // В режиме просмотра скрываем EditText и показываем TextView
            notesEditText.setVisibility(View.GONE);
            notesTextView.setVisibility(View.VISIBLE);

            watchUrlEditText.setVisibility(View.GONE);
            watchUrlLabel.setVisibility(View.VISIBLE);
            watchUrlTextView.setVisibility(View.VISIBLE);
            updateWatchUrlReadOnlyDisplay();

            if (watchAtEditContainer != null) {
                watchAtEditContainer.setVisibility(View.GONE);
            }
            if (watchAtEditText != null) {
                watchAtEditText.setVisibility(View.GONE);
            }
            if (watchAtLabel != null) {
                watchAtLabel.setVisibility(View.GONE);
            }
            if (watchAtTextView != null) {
                watchAtTextView.setVisibility(View.GONE);
            }
            updateWatchAtReadOnlyDisplay();
            if (searchWatchAtButton != null) {
                searchWatchAtButton.setVisibility(View.GONE);
            }

            // Копируем текст из EditText в TextView и делаем ссылки кликабельными
            String notesText = notesEditText.getText().toString();
            notesTextView.setText(notesText);
            Linkify.addLinks(notesTextView, Linkify.ALL);
            notesTextView.setMovementMethod(LinkMovementMethod.getInstance());
        } else {
            editButton.setVisibility(View.GONE);
            saveButton.setVisibility(View.VISIBLE);
            deleteButton.setVisibility(View.VISIBLE);

            selectImageButton.setVisibility(View.VISIBLE);
            addMediaButton.setVisibility(View.VISIBLE);
            updateCollectionsButtonVisibility();

            // В режиме редактирования показываем EditText и скрываем TextView
            notesEditText.setVisibility(View.VISIBLE);
            notesTextView.setVisibility(View.GONE);

            watchUrlLabel.setVisibility(View.VISIBLE);
            watchUrlEditText.setVisibility(View.VISIBLE);
            watchUrlTextView.setVisibility(View.GONE);

            if (watchAtLabel != null) {
                watchAtLabel.setVisibility(View.GONE);
            }
            if (watchAtEditContainer != null) {
                watchAtEditContainer.setVisibility(View.GONE);
            }
            if (watchAtEditText != null) {
                watchAtEditText.setVisibility(View.GONE);
            }
            if (watchAtTextView != null) {
                watchAtTextView.setVisibility(View.GONE);
            }
            if (searchWatchAtButton != null) {
                searchWatchAtButton.setVisibility(View.VISIBLE);
            }
        }

        updatePlayWatchButtonState();

        // Управление редактируемостью полей
        titleEditText.setEnabled(!readOnly);
        titleEditText.setFocusable(!readOnly);
        titleEditText.setFocusableInTouchMode(!readOnly);

        notesEditText.setEnabled(!readOnly);
        notesEditText.setFocusable(!readOnly);
        notesEditText.setFocusableInTouchMode(!readOnly);

        watchUrlEditText.setEnabled(!readOnly);
        watchUrlEditText.setFocusable(!readOnly);
        watchUrlEditText.setFocusableInTouchMode(!readOnly);

        watchAtEditText.setEnabled(!readOnly);
        watchAtEditText.setFocusable(!readOnly);
        watchAtEditText.setFocusableInTouchMode(!readOnly);

        genreEditText.setEnabled(!readOnly);
        genreEditText.setFocusable(!readOnly);
        genreEditText.setFocusableInTouchMode(!readOnly);
        styleExtraInfoField(genreEditText, readOnly);

        seasonsEditText.setEnabled(!readOnly);
        seasonsEditText.setFocusable(!readOnly);
        seasonsEditText.setFocusableInTouchMode(!readOnly);
        styleExtraInfoField(seasonsEditText, readOnly);

        episodesEditText.setEnabled(!readOnly);
        episodesEditText.setFocusable(!readOnly);
        episodesEditText.setFocusableInTouchMode(!readOnly);
        styleExtraInfoField(episodesEditText, readOnly);

        statusSpinner.setEnabled(!readOnly);
        statusSpinner.setAlpha(readOnly ? 0.92f : 1f);

        // Для RecyclerView медиафайлов нужно обновить адаптер
        if (mediaAdapter != null) {
            mediaAdapter.setEditMode(!readOnly);
        }

        updateCoverActionsVisibility();
    }

    /** В режиме правки — такая же обводка, как у заметок/ссылок. */
    private void styleExtraInfoField(EditText field, boolean readOnly) {
        if (field == null || !isAdded()) return;
        float density = getResources().getDisplayMetrics().density;
        if (readOnly) {
            field.setBackgroundResource(android.R.color.transparent);
            field.setPadding(0, field.getPaddingTop(), 0, field.getPaddingBottom());
        } else {
            field.setBackgroundResource(R.drawable.edittext_background);
            int padH = Math.round(12 * density);
            int padV = Math.round(8 * density);
            field.setPadding(padH, padV, padH, padV);
        }
    }

    private boolean hasCoverImage() {
        if (coverMarkedForRemoval) {
            return false;
        }
        if (selectedImageUri != null) {
            return true;
        }
        return series != null
                && series.getImageUri() != null
                && !series.getImageUri().isEmpty();
    }

    private void updateCoverActionsVisibility() {
        if (deleteCoverButton == null || seriesImageView == null) {
            return;
        }
        boolean hasCover = hasCoverImage();
        deleteCoverButton.setVisibility(!readOnlyMode && hasCover ? View.VISIBLE : View.GONE);
        seriesImageView.setClickable(hasCover);
        seriesImageView.setFocusable(hasCover);
    }

    private String getCurrentCoverUriValue() {
        if (selectedImageUri != null) {
            return selectedImageUri.toString();
        }
        if (series != null && series.getImageUri() != null && !series.getImageUri().isEmpty()) {
            return series.getImageUri();
        }
        return null;
    }

    private void openCoverViewer() {
        String imageUri = getCurrentCoverUriValue();
        if (imageUri == null) {
            return;
        }

        String title = series != null && series.getTitle() != null ? series.getTitle() : "";
        CoverViewerFragment viewerFragment = CoverViewerFragment.newInstance(imageUri, title);
        requireActivity().getSupportFragmentManager()
                .beginTransaction()
                .hide(this)
                .add(R.id.fragment_container, viewerFragment)
                .addToBackStack("cover_viewer")
                .commit();
    }

    private void showDeleteCoverDialog() {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.delete_cover)
                .setMessage(R.string.delete_cover_confirm)
                .setPositiveButton("Удалить", (dialog, which) -> removeCover())
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void removeCover() {
        coverMarkedForRemoval = true;
        selectedImageUri = null;
        clearCoverViews();
        updateCoverActionsVisibility();
        Toast.makeText(getContext(), "Обложка будет удалена после сохранения", Toast.LENGTH_SHORT).show();
    }

    private String lastLoadedCoverKey = "";

    private void loadCoverIntoViews(@Nullable Uri coverUri) {
        if (!isAdded() || seriesImageView == null) return;
        if (coverUri == null) {
            lastLoadedCoverKey = "";
            clearCoverViews();
            return;
        }
        String key = coverUri.toString();
        if (key.equals(lastLoadedCoverKey)
                && seriesImageView.getDrawable() != null
                && coverBlurBackground != null
                && coverBlurBackground.getDrawable() != null) {
            return;
        }
        lastLoadedCoverKey = key;

        Glide.with(this)
                .load(coverUri)
                .placeholder(R.drawable.ic_baseline_image_24)
                .override(360, 480)
                .centerCrop()
                .into(seriesImageView);

        if (coverBlurBackground != null) {
            Glide.with(this)
                    .load(coverUri)
                    .override(72, 96)
                    .centerCrop()
                    .into(coverBlurBackground);
            applyCoverBlurEffect();
            coverBlurBackground.setVisibility(View.VISIBLE);
        }
    }

    private void clearCoverViews() {
        lastLoadedCoverKey = "";
        if (seriesImageView != null) {
            seriesImageView.setImageResource(R.drawable.ic_baseline_image_24);
        }
        if (coverBlurBackground != null) {
            coverBlurBackground.setImageDrawable(null);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                coverBlurBackground.setRenderEffect(null);
            }
            coverBlurBackground.setVisibility(View.GONE);
        }
    }

    private void applyCoverBlurEffect() {
        if (coverBlurBackground == null) return;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            coverBlurBackground.setRenderEffect(
                    android.graphics.RenderEffect.createBlurEffect(
                            18f, 18f, android.graphics.Shader.TileMode.CLAMP));
        } else {
            coverBlurBackground.setScaleX(1.15f);
            coverBlurBackground.setScaleY(1.15f);
            coverBlurBackground.setAlpha(0.85f);
        }
    }

    private void updateCollectionsButtonVisibility() {
        boolean hasCollections = allCollections != null && !allCollections.isEmpty();
        collectionsTitle.setVisibility(hasCollections ? View.VISIBLE : View.GONE);
        selectedCollectionsText.setVisibility(hasCollections ? View.VISIBLE : View.GONE);
        collectionsButton.setVisibility(hasCollections && !readOnlyMode ? View.VISIBLE : View.GONE);
    }


    private void openMediaViewer(int position) {
        if (mediaFiles == null || mediaFiles.isEmpty() || position < 0 || position >= mediaFiles.size()) {
            return;
        }

        ArrayList<MediaFile> mediaList = new ArrayList<>(mediaFiles);
        MediaViewerFragment viewerFragment = MediaViewerFragment.newInstance(mediaList, position);

        requireActivity().getSupportFragmentManager()
                .beginTransaction()
                .hide(this)
                .add(R.id.fragment_container, viewerFragment)
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
            } else {
                allCollections.clear();
            }
            updateCollectionsButtonVisibility();
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

    private void updateWatchAtReadOnlyDisplay() {
        if (watchAtEditText == null) return;
        String watchAtText = watchAtEditText.getText().toString().trim();
        if (watchAtTextView == null) return;
        watchAtTextView.setText(watchAtText);
        Linkify.addLinks(watchAtTextView, Linkify.WEB_URLS);
        watchAtTextView.setMovementMethod(new WatchLinkMovementMethod(watchAtTextView,
                new WatchLinkMovementMethod.UrlHandler() {
                    @Override
                    public void onUrlClick(String url) {
                        BrowserOpenHelper.openUrl(requireContext(), url);
                    }

                    @Override
                    public void onUrlLongClick(String url) {
                        BrowserOpenHelper.showBrowserChooser(requireContext(), url);
                    }
                }));
        watchAtTextView.setLinksClickable(true);
        watchAtTextView.setLongClickable(true);
    }

    private void updateWatchUrlReadOnlyDisplay() {
        String watchUrlText = watchUrlEditText.getText().toString().trim();
        watchUrlTextView.setText(watchUrlText);
        Linkify.addLinks(watchUrlTextView, Linkify.WEB_URLS);
        watchUrlTextView.setMovementMethod(LinkMovementMethod.getInstance());
    }

    private void populateForm(Series series) {
        titleEditText.setText(series.getTitle());
        watchAtEditText.setText(series.getWatchAt());
        updateWatchAtReadOnlyDisplay();
        updatePlayWatchButtonState();
        watchUrlEditText.setText(series.getWatchUrl());
        updateWatchUrlReadOnlyDisplay();
        notesEditText.setText(series.getNotes());

        // Также устанавливаем текст в notesTextView для режима просмотра
        String notesText = series.getNotes();
        notesTextView.setText(notesText);
        Linkify.addLinks(notesTextView, Linkify.ALL);
        notesTextView.setMovementMethod(LinkMovementMethod.getInstance());

        genreEditText.setText(series.getGenre());

        if (series.getSeasons() > 0) {
            seasonsEditText.setText(String.valueOf(series.getSeasons()));
        }

        if (series.getEpisodes() > 0) {
            episodesEditText.setText(String.valueOf(series.getEpisodes()));
        }

        // Загрузка основного изображения сериала
        if (hasCoverImage()) {
            Uri coverUri = selectedImageUri != null
                    ? selectedImageUri
                    : MediaStorageHelper.resolveLoadUri(series.getImageUri());
            loadCoverIntoViews(coverUri);
        } else {
            clearCoverViews();
        }
        updateCoverActionsVisibility();

        // Установка статуса
        String status = series.getStatus();
        String displayText = getStatusDisplayText(status);
        statusSpinner.setText(displayText, false);
        applyStatusAppearance(status);

        // Избранное
        setFavoriteSelected(series.getIsFavorite());
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
        statusSpinner.setOnItemClickListener((parent, view, position, id) -> {
            String display = (String) parent.getItemAtPosition(position);
            applyStatusAppearance(getStatusValue(display));
        });
    }

    /** Контурная кнопка статуса — цвет как на карточке. */
    private void applyStatusAppearance(String statusValue) {
        if (statusSpinner == null) return;
        int outline = getStatusColor(statusValue);
        statusSpinner.setTextColor(outline);

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setColor(Color.TRANSPARENT);
        bg.setStroke(Math.round(1.5f * getResources().getDisplayMetrics().density), outline);
        bg.setCornerRadius(999f * getResources().getDisplayMetrics().density);
        statusSpinner.setBackground(bg);

        android.graphics.drawable.Drawable[] drawables = statusSpinner.getCompoundDrawablesRelative();
        if (drawables[0] != null) {
            DrawableCompat.setTint(drawables[0].mutate(), outline);
        }
    }

    private int getStatusColor(String status) {
        switch (status != null ? status : "") {
            case "watching":
                return Color.parseColor("#42A5F5");
            case "completed":
                return Color.parseColor("#66BB6A");
            case "dropped":
                return Color.parseColor("#EF5350");
            case "planned":
            default:
                return Color.parseColor("#7E57C2");
        }
    }

    private void setFavoriteSelected(boolean selected) {
        favoriteSelected = selected;
        if (favoriteButton == null) return;
        favoriteButton.setSelected(selected);
        favoriteButton.setImageResource(R.drawable.ic_bookmark_favorite_24);
        favoriteButton.setColorFilter(selected ? 0xFFC49A5A : 0xFF9AA3B5);
        favoriteButton.setAlpha(1f);
    }

    private boolean isFavoriteSelected() {
        return favoriteSelected;
    }

    private void setupEventListeners() {
        backButton.setOnClickListener(v -> handleBackNavigation());

        titleEditText.setOnLongClickListener(v -> {
            copyTitleToClipboard();
            return true;
        });

        if (searchWatchAtButton != null) {
            searchWatchAtButton.setOnClickListener(v -> searchWatchLinks());
        }
        if (playWatchButton != null) {
            playWatchButton.setOnClickListener(v -> openFirstWatchLink());
            playWatchButton.setOnLongClickListener(v -> {
                openFirstWatchLinkChooser();
                return true;
            });
        }
        if (watchAtEditText != null) {
            watchAtEditText.addTextChangedListener(new android.text.TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                    updatePlayWatchButtonState();
                }
                @Override public void afterTextChanged(android.text.Editable s) {}
            });
        }

        selectImageButton.setOnClickListener(v -> openSeriesImagePicker());
        deleteCoverButton.setOnClickListener(v -> showDeleteCoverDialog());
        seriesImageView.setOnClickListener(v -> openCoverViewer());

        statusSpinner.setOnClickListener(v -> {
            statusSpinner.showDropDown();
        });

        statusSpinner.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                statusSpinner.showDropDown();
            }
            return true;
        });

        if (favoriteButton != null) {
            favoriteButton.setOnClickListener(v -> {
                setFavoriteSelected(!isFavoriteSelected());
                if (readOnlyMode && series != null) {
                    series.setIsFavorite(isFavoriteSelected());
                    viewModel.toggleFavoriteStatus(series.getId(), isFavoriteSelected());
                }
            });
        }

        // Устанавливаем начальный режим - только для просмотра
        setReadOnlyMode(true);

        editButton.setOnClickListener(v -> {
            captureOriginalState();
            setReadOnlyMode(false);
        });
        collectionsButton.setOnClickListener(v -> {
            showCollectionsDialog();
        });

        addMediaButton.setOnClickListener(v -> openMultipleMediaPicker());

        saveButton.setOnClickListener(v -> {
            exitAfterSave = false;
            saveSeries();
        });

        deleteButton.setOnClickListener(v -> {
            if (series != null) {
                showDeleteConfirmationDialog();
            }
        });
    }

    private void searchWatchLinks() {
        String title = titleEditText.getText() != null
                ? titleEditText.getText().toString().trim()
                : "";
        WatchLinkSearchDialog.show(this, title, urls -> {
            String merged = WatchLinkTextHelper.mergeUrls(
                    watchAtEditText.getText().toString(), urls);
            watchAtEditText.setText(merged);
            updatePlayWatchButtonState();
            Toast.makeText(getContext(), R.string.watch_links_added, Toast.LENGTH_SHORT).show();
        });
    }

    private void openFirstWatchLink() {
        String url = firstWatchUrl();
        if (url == null || !isAdded() || getContext() == null) return;
        BrowserOpenHelper.openUrl(requireContext(), url);
    }

    private void openFirstWatchLinkChooser() {
        String url = firstWatchUrl();
        if (url == null || !isAdded() || getContext() == null) return;
        BrowserOpenHelper.showBrowserChooser(requireContext(), url);
    }

    @Nullable
    private String firstWatchUrl() {
        String raw = "";
        if (watchAtEditText != null && watchAtEditText.getText() != null) {
            raw = watchAtEditText.getText().toString();
        }
        if (raw.trim().isEmpty() && series != null && series.getWatchAt() != null) {
            raw = series.getWatchAt();
        }
        for (String part : raw.split("[\\n,;]+")) {
            String u = part.trim();
            if (u.isEmpty()) continue;
            if (!u.startsWith("http://") && !u.startsWith("https://")) {
                u = "https://" + u;
            }
            return u;
        }
        return null;
    }

    private void updatePlayWatchButtonState() {
        if (playWatchButton == null) return;
        boolean hasLink = firstWatchUrl() != null;
        playWatchButton.setEnabled(hasLink);
        playWatchButton.setAlpha(hasLink ? 1f : 0.38f);
    }

    private void handleBackNavigation() {
        if (!readOnlyMode && hasUnsavedChanges()) {
            showUnsavedChangesDialog();
            return;
        }
        requireActivity().getSupportFragmentManager().popBackStack();
    }

    private void showUnsavedChangesDialog() {
        if (!isAdded() || getContext() == null) {
            return;
        }
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.save_changes_title)
                .setMessage(R.string.save_changes_message)
                .setPositiveButton(R.string.save, (dialog, which) -> {
                    exitAfterSave = true;
                    saveSeries();
                })
                .setNegativeButton(R.string.discard, (dialog, which) ->
                        requireActivity().getSupportFragmentManager().popBackStack())
                .setNeutralButton(R.string.cancel, null)
                .show();
    }

    private void captureOriginalState() {
        originalTitle = textOf(titleEditText);
        originalWatchAt = textOf(watchAtEditText);
        originalWatchUrl = textOf(watchUrlEditText);
        originalNotes = textOf(notesEditText);
        originalGenre = textOf(genreEditText);
        originalSeasons = textOf(seasonsEditText);
        originalEpisodes = textOf(episodesEditText);
        originalStatus = statusSpinner != null ? statusSpinner.getText().toString().trim() : "";
        originalFavorite = isFavoriteSelected();
        originalCollectionIds = new HashSet<>(selectedCollectionsMap.keySet());
    }

    private boolean hasUnsavedChanges() {
        if (selectedImageUri != null || coverMarkedForRemoval) {
            return true;
        }
        if (!Objects.equals(originalTitle, textOf(titleEditText))
                || !Objects.equals(originalWatchAt, textOf(watchAtEditText))
                || !Objects.equals(originalWatchUrl, textOf(watchUrlEditText))
                || !Objects.equals(originalNotes, textOf(notesEditText))
                || !Objects.equals(originalGenre, textOf(genreEditText))
                || !Objects.equals(originalSeasons, textOf(seasonsEditText))
                || !Objects.equals(originalEpisodes, textOf(episodesEditText))) {
            return true;
        }
        String currentStatus = statusSpinner != null ? statusSpinner.getText().toString().trim() : "";
        if (!Objects.equals(originalStatus, currentStatus)) {
            return true;
        }
        boolean currentFavorite = isFavoriteSelected();
        if (originalFavorite != currentFavorite) {
            return true;
        }
        return !originalCollectionIds.equals(selectedCollectionsMap.keySet());
    }

    private static String textOf(EditText editText) {
        return editText != null ? editText.getText().toString().trim() : "";
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

    private void copyTitleToClipboard() {
        if (!isAdded() || getContext() == null) {
            return;
        }
        String title = titleEditText.getText().toString().trim();
        if (title.isEmpty()) {
            Toast.makeText(getContext(), "Нет названия для копирования", Toast.LENGTH_SHORT).show();
            return;
        }
        ClipboardManager clipboard = (ClipboardManager) requireContext()
                .getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("series_title", title));
            Toast.makeText(getContext(), R.string.title_copied, Toast.LENGTH_SHORT).show();
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
            exitAfterSave = false;
            return;
        }

        // Сохраняем ссылку на ViewModel локально
        SeriesViewModel localViewModel = viewModel;
        if (localViewModel == null) {
            exitAfterSave = false;
            return;
        }

        // Проверяем, изменилось ли название
        if (!newTitle.equals(series.getTitle())) {
            localViewModel.doesSeriesExistExcludeId(newTitle, series.getId())
                    .observe(getViewLifecycleOwner(), exists -> {
                if (!isAdded() || getContext() == null) return;

                if (exists != null && exists) {
                    Toast.makeText(getContext(),
                            "Сериал \"" + newTitle + "\" уже существует",
                            Toast.LENGTH_LONG).show();
                    titleEditText.setText(series.getTitle());
                    titleEditText.requestFocus();
                    exitAfterSave = false;
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
        series.setWatchAt(watchAtEditText.getText().toString().trim());
        series.setWatchUrl(watchUrlEditText.getText().toString().trim());
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
            String fileName = MediaStorageHelper.getDisplayName(requireContext(), selectedImageUri);
            String storedUri = MediaStorageHelper.copyCoverToInternalStorage(requireContext(), selectedImageUri, fileName);
            if (storedUri != null) {
                series.setImageUri(storedUri);
                // Сбрасываем облачный путь, чтобы SyncEngine заново залил обложку
                series.setCloudImagePath(null);
            } else {
                Toast.makeText(getContext(), "Не удалось сохранить обложку", Toast.LENGTH_SHORT).show();
                exitAfterSave = false;
                return;
            }
        } else if (coverMarkedForRemoval) {
            series.setImageUri(null);
            series.setCloudImagePath(null);
        }

        String selectedStatus = statusSpinner.getText().toString();
        series.setStatus(getStatusValue(selectedStatus));
        series.setIsWatched("Завершено".equals(selectedStatus));
        series.setIsFavorite(isFavoriteSelected());

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

        // 5. Показываем сообщение об успехе и возвращаемся в режим просмотра
        Toast.makeText(getContext(), "Сериал обновлен", Toast.LENGTH_SHORT).show();
        selectedImageUri = null;
        coverMarkedForRemoval = false;
        setReadOnlyMode(true);
        captureOriginalState();

        if (exitAfterSave) {
            exitAfterSave = false;
            requireActivity().getSupportFragmentManager().popBackStack();
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == getActivity().RESULT_OK) {
            if (requestCode == PICK_SERIES_IMAGE_REQUEST && data != null && data.getData() != null) {
                // Обработка выбора ОСНОВНОГО изображения сериала
                selectedImageUri = data.getData();
                coverMarkedForRemoval = false;
                if (selectedImageUri != null) {
                    loadCoverIntoViews(selectedImageUri);
                }
                updateCoverActionsVisibility();
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
            String storedUri = MediaStorageHelper.copyMediaToInternalStorage(requireContext(), uri, fileName);
            if (storedUri == null) {
                Toast.makeText(getContext(), "Не удалось сохранить файл: " + fileName, Toast.LENGTH_SHORT).show();
                return false;
            }

            MediaFile mediaFile = new MediaFile(seriesId, storedUri, fileType, fileName);
            String filePath = MediaStorageHelper.getInternalFilePath(requireContext(), storedUri);
            if (filePath != null) {
                mediaFile.setFilePath(filePath);
            }

            long fileSize = getFileSize(MediaStorageHelper.resolveLoadUri(storedUri));
            mediaFile.setFileSize(fileSize);
            viewModel.addMediaFile(mediaFile);
            return true;
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
        notesTextView = null;
        watchUrlLabel = null;
        watchUrlEditText = null;
        watchUrlTextView = null;
        watchAtLabel = null;
        watchAtEditContainer = null;
        watchAtEditText = null;
        searchWatchAtButton = null;
        watchAtTextView = null;
        playWatchButton = null;
        genreEditText = null;
        seasonsEditText = null;
        episodesEditText = null;
        seriesImageView = null;
        coverBlurBackground = null;
        deleteCoverButton = null;
        // ... остальные UI элементы ...
    }
}