package com.example.seriestracker.ui.screens;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.seriestracker.R;
import com.example.seriestracker.data.entities.Collection;
import com.example.seriestracker.data.entities.Series;
import com.example.seriestracker.data.prefs.ThemePreferences;
import com.example.seriestracker.ui.adapters.MultiSelectSeriesAdapter;
import com.example.seriestracker.ui.adapters.SeriesAdapter;
import com.example.seriestracker.ui.utils.RecyclerViewPerf;
import com.example.seriestracker.ui.viewmodels.SeriesViewModel;
import com.example.seriestracker.utils.CollectionCardColors;
import com.example.seriestracker.utils.MediaStorageHelper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class CollectionDetailScreen extends Fragment {

    private SeriesViewModel viewModel;
    private long collectionId;

    private TextView collectionNameTextView;
    private TextView seriesCountBadge;
    private RecyclerView seriesRecyclerView;
    private SeriesAdapter seriesAdapter;
    private ImageButton backButton;
    private View colorIndicator;
    private ImageButton favoriteButton;
    private ImageButton menuButton;

    private int savedFirstVisiblePosition = RecyclerView.NO_POSITION;
    private int savedFirstVisibleTop = 0;

    public CollectionDetailScreen() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getArguments() != null) {
            collectionId = getArguments().getLong("collectionId", -1);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_collection_detail, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(requireActivity()).get(SeriesViewModel.class);

        collectionNameTextView = view.findViewById(R.id.collectionNameTextView);
        seriesCountBadge = view.findViewById(R.id.seriesCountBadge);
        seriesRecyclerView = view.findViewById(R.id.seriesRecyclerView);
        backButton = view.findViewById(R.id.backButton);
        colorIndicator = view.findViewById(R.id.colorIndicator);
        favoriteButton = view.findViewById(R.id.favoriteButton);
        menuButton = view.findViewById(R.id.menuButton);


        // Обработчик кнопки назад
        if (backButton != null) {
            backButton.setOnClickListener(v -> {
                requireActivity().getSupportFragmentManager().popBackStack();
            });
        }

        // Обработчик кнопки избранного
        if (favoriteButton != null) {
            favoriteButton.setOnClickListener(v -> toggleFavorite());
        }

        // Обработчик кнопки меню
        if (menuButton != null) {
            menuButton.setOnClickListener(v -> showMenu());
        }


        // Настройка RecyclerView
        setupRecyclerView();

        // Загрузка данных
        if (collectionId != -1) {
            loadData();
        }
    }

    private void setupRecyclerView() {
        seriesAdapter = new SeriesAdapter(new SeriesAdapter.OnSeriesClickListener() {
            @Override
            public void onSeriesClick(Series series) {
                openEditSeriesScreen(series);
            }

            @Override
            public void onWatchedToggle(Series series, boolean isWatched) {
                viewModel.toggleWatchedStatus(series.getId(), isWatched);
                Toast.makeText(getContext(),
                        isWatched ? "Отмечено как просмотренное" : "Отмечено как непросмотренное",
                        Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onFavoriteToggle(Series series, boolean isFavorite) {
                viewModel.toggleFavoriteStatus(series.getId(), isFavorite);
                Toast.makeText(getContext(),
                        isFavorite ? "Добавлено в избранное" : "Убрано из избранное",
                        Toast.LENGTH_SHORT).show();
            }
        });

        seriesRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        seriesRecyclerView.setAdapter(seriesAdapter);
        seriesRecyclerView.setHasFixedSize(true);
        RecyclerViewPerf.tune(seriesRecyclerView, 20);

        seriesAdapter.setOnSeriesLongClickListener(null);
        seriesAdapter.setInCollectionContext(true);
        seriesAdapter.setOnSeriesMenuListener(new SeriesAdapter.OnSeriesMenuListener() {
            @Override
            public void onChangeStatus(Series series, String newStatus) {
                viewModel.updateSeriesStatus(series.getId(), newStatus);
                Toast.makeText(getContext(), "Статус обновлён", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onDeleteAction(Series series) {
                showRemoveFromCollectionDialog(series);
            }
        });
    }

    private void showRemoveFromCollectionDialog(Series series) {
        View contentView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_remove_from_collection, null, false);

        TextView titleText = contentView.findViewById(R.id.removeSeriesTitleText);
        ImageView coverImage = contentView.findViewById(R.id.removeSeriesCoverImage);
        titleText.setText(series.getTitle());

        if (series.getImageUri() != null && !series.getImageUri().isEmpty()) {
            Glide.with(this)
                    .load(MediaStorageHelper.resolveLoadUri(series.getImageUri()))
                    .placeholder(R.drawable.ic_baseline_image_24)
                    .error(R.drawable.ic_baseline_image_24)
                    .centerCrop()
                    .into(coverImage);
        } else {
            coverImage.setImageResource(R.drawable.ic_baseline_image_24);
        }

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setView(contentView)
                .setCancelable(true)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        contentView.findViewById(R.id.cancelRemoveButton).setOnClickListener(v -> dialog.dismiss());
        contentView.findViewById(R.id.removeFromCollectionButton).setOnClickListener(v -> {
            viewModel.removeSeriesFromCollection(series.getId(), collectionId);
            Toast.makeText(getContext(), R.string.removed_from_collection, Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });

        dialog.show();
    }

    private void loadData() {
        // Получаем сериалы в коллекции
        viewModel.getSeriesInCollection(collectionId).observe(getViewLifecycleOwner(), seriesList -> {
            if (seriesList != null) {
                saveRecyclerScrollState();
                seriesAdapter.setSeriesList(seriesList);
                restoreRecyclerScrollState();

                // Только обновляем количество в бейдже
                int count = seriesList.size();
                seriesCountBadge.setText(String.valueOf(count));
            }
        });

        // Получаем данные коллекции
        viewModel.getAllCollections().observe(getViewLifecycleOwner(), collections -> {
            if (collections != null) {
                for (Collection collection : collections) {
                    if (collection.getId() == collectionId) {
                        collectionNameTextView.setText(collection.getName());

                        // Устанавливаем цвет коллекции - ИСПРАВЛЕНО: используем getColors()
                        List<String> colors = collection.getColors();
                        if (colors != null && !colors.isEmpty()) {
                            try {
                                applyCollectionColors(Color.parseColor(colors.get(0)));
                            } catch (Exception e) {
                                setDefaultColors();
                            }
                        } else {
                            setDefaultColors();
                        }

                        // Обновляем состояние избранного
                        updateFavoriteIcon(collection.isFavorite());
                        break;
                    }
                }
            }
        });
    }

    private void applyCollectionColors(int mainColor) {
        boolean dark = ThemePreferences.isDark(requireContext());
        int[] gradient = dark
                ? CollectionCardColors.darkGradient(mainColor)
                : CollectionCardColors.lightGradient(mainColor);
        int accent = CollectionCardColors.brightestFromGradient(gradient);

        ViewGroup.LayoutParams lp = colorIndicator.getLayoutParams();
        lp.height = Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 6f, getResources().getDisplayMetrics()));
        colorIndicator.setLayoutParams(lp);
        colorIndicator.setBackgroundColor(accent);
        colorIndicator.setVisibility(View.VISIBLE);

        collectionNameTextView.setTextColor(accent);
    }

    private void setDefaultColors() {
        applyCollectionColors(getResources().getColor(R.color.primary_blue));
    }

    private void toggleFavorite() {
        // Получаем коллекцию и переключаем статус избранного
        viewModel.getAllCollections().observe(getViewLifecycleOwner(), collections -> {
            if (collections != null) {
                for (Collection collection : collections) {
                    if (collection.getId() == collectionId) {
                        boolean newFavoriteStatus = !collection.isFavorite();
                        collection.setFavorite(newFavoriteStatus);
                        viewModel.updateCollection(collection);

                        // Обновляем иконку
                        updateFavoriteIcon(newFavoriteStatus);

                        // Показываем тост
                        Toast.makeText(getContext(),
                                newFavoriteStatus ? "Добавлено в избранное" : "Убрано из избранного",
                                Toast.LENGTH_SHORT).show();

                        // Прерываем наблюдение после обновления
                        viewModel.getAllCollections().removeObservers(getViewLifecycleOwner());
                        break;
                    }
                }
            }
        });
    }

    private void updateFavoriteIcon(boolean isFavorite) {
        if (favoriteButton != null) {
            if (isFavorite) {
                favoriteButton.setImageResource(R.drawable.ic_baseline_star_24_filled);
            } else {
                favoriteButton.setImageResource(R.drawable.ic_baseline_star_border_24);
            }
        }
    }

    private void showMenu() {
        // Создаем PopupMenu для отображения опций
        View menuView = menuButton;
        if (menuView != null) {
            androidx.appcompat.widget.PopupMenu popup = new androidx.appcompat.widget.PopupMenu(
                    requireContext(), menuView);

            // Используем XML файл меню
            popup.getMenuInflater().inflate(R.menu.collection_actions_menu, popup.getMenu());

            popup.setOnMenuItemClickListener(item -> {
                int itemId = item.getItemId();
                if (itemId == R.id.action_add_series) { // Добавить сериал
                    showSelectSeriesDialog();
                    return true;
                } else if (itemId == R.id.action_edit) { // Редактировать
                    editCollection();
                    return true;
                } else if (itemId == R.id.action_delete) { // Удалить
                    deleteCollection();
                    return true;
                } else if (itemId == R.id.action_random) { // Случайный сериал
                    showRandomSeriesFromCollection();
                    return true;
                }
                return false;
            });

            popup.show();
        }
    }

    private void editCollection() {
        // Navigate to the edit collection screen
        CreateCollectionScreen editCollectionScreen = new CreateCollectionScreen();
        Bundle bundle = new Bundle();
        bundle.putLong("collectionId", collectionId);
        bundle.putBoolean("isEditing", true);
        editCollectionScreen.setArguments(bundle);

        requireActivity().getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, editCollectionScreen)
                .addToBackStack(null)
                .commit();
    }

    private void deleteCollection() {
        // Подтверждение удаления
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Удалить коллекцию")
                .setMessage("Вы уверены, что хотите удалить эту коллекцию? Все сериалы останутся в приложении.")
                .setPositiveButton("Удалить", (dialog, which) -> {
                    // Удаляем коллекцию
                    viewModel.deleteCollection(collectionId); // Используйте существующий метод
                    Toast.makeText(getContext(), "Коллекция удалена", Toast.LENGTH_SHORT).show();

                    // Возвращаемся назад
                    requireActivity().getSupportFragmentManager().popBackStack();
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void showSelectSeriesDialog() {
        android.app.Dialog dialog = new android.app.Dialog(requireContext());
        dialog.setContentView(R.layout.dialog_select_series);
        dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);

        RecyclerView selectSeriesRecyclerView = dialog.findViewById(R.id.selectSeriesRecyclerView);
        EditText searchSeriesEditText = dialog.findViewById(R.id.searchSeriesEditText);
        Button cancelButton = dialog.findViewById(R.id.cancelButton);
        Button addSelectedButton = dialog.findViewById(R.id.addSelectedButton);

        List<Series> availableSeries = new ArrayList<>();
        List<Series> cachedAllSeries = new ArrayList<>();
        List<Series> cachedSeriesInCollection = new ArrayList<>();
        MultiSelectSeriesAdapter[] adapterHolder = new MultiSelectSeriesAdapter[1];

        Runnable rebuildAvailableSeries = () -> {
            Set<Long> seriesInCollectionIds = new HashSet<>();
            for (Series series : cachedSeriesInCollection) {
                seriesInCollectionIds.add(series.getId());
            }

            availableSeries.clear();
            for (Series series : cachedAllSeries) {
                if (!seriesInCollectionIds.contains(series.getId())) {
                    availableSeries.add(series);
                }
            }

            List<Series> filtered = filterSeriesByTitle(
                    availableSeries, searchSeriesEditText.getText().toString());

            if (adapterHolder[0] == null) {
                MultiSelectSeriesAdapter adapter = new MultiSelectSeriesAdapter(filtered);
                adapterHolder[0] = adapter;
                selectSeriesRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
                selectSeriesRecyclerView.setAdapter(adapter);

                adapter.setOnSelectionChangeListener(selectedCount ->
                        updateAddSeriesButton(addSelectedButton, selectedCount));

                searchSeriesEditText.addTextChangedListener(new TextWatcher() {
                    @Override
                    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                    }

                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {
                        if (adapterHolder[0] != null) {
                            adapterHolder[0].setSeriesList(
                                    filterSeriesByTitle(availableSeries, s.toString()));
                        }
                    }

                    @Override
                    public void afterTextChanged(Editable s) {
                    }
                });

                cancelButton.setOnClickListener(v -> dialog.dismiss());

                addSelectedButton.setOnClickListener(v -> {
                    Set<Long> selectedIds = adapter.getSelectedSeriesIds();
                    if (!selectedIds.isEmpty()) {
                        List<Long> selectedList = new ArrayList<>(selectedIds);
                        viewModel.addMultipleSeriesToCollection(selectedList, collectionId);
                        Toast.makeText(getContext(),
                                getString(R.string.added_series_with_count,
                                        selectedList.size(),
                                        getSeriesCountWord(selectedList.size())),
                                Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                    }
                });

                updateAddSeriesButton(addSelectedButton, 0);
            } else {
                adapterHolder[0].setSeriesList(filtered);
            }
        };

        Observer<List<Series>> allSeriesObserver = allSeries -> {
            cachedAllSeries.clear();
            if (allSeries != null) {
                cachedAllSeries.addAll(allSeries);
            }
            rebuildAvailableSeries.run();
        };

        Observer<List<Series>> inCollectionObserver = seriesInCollection -> {
            cachedSeriesInCollection.clear();
            if (seriesInCollection != null) {
                cachedSeriesInCollection.addAll(seriesInCollection);
            }
            rebuildAvailableSeries.run();
        };

        viewModel.getAllSeries().observe(getViewLifecycleOwner(), allSeriesObserver);
        viewModel.getSeriesInCollection(collectionId).observe(getViewLifecycleOwner(), inCollectionObserver);

        dialog.setOnDismissListener(d -> {
            viewModel.getAllSeries().removeObserver(allSeriesObserver);
            viewModel.getSeriesInCollection(collectionId).removeObserver(inCollectionObserver);
        });

        dialog.show();
    }

    private void updateAddSeriesButton(Button button, int selectedCount) {
        if (selectedCount <= 0) {
            button.setText(R.string.add_series_none);
            button.setEnabled(false);
            return;
        }
        button.setEnabled(true);
        button.setText(getString(
                R.string.add_series_with_count,
                selectedCount,
                getSeriesCountWord(selectedCount)));
    }

    private String getSeriesCountWord(int count) {
        int mod10 = count % 10;
        int mod100 = count % 100;
        if (mod10 == 1 && mod100 != 11) {
            return getString(R.string.series_word_one);
        }
        if (mod10 >= 2 && mod10 <= 4 && (mod100 < 10 || mod100 >= 20)) {
            return getString(R.string.series_word_few);
        }
        return getString(R.string.series_word_many);
    }

    private List<Series> filterSeriesByTitle(List<Series> source, String query) {
        if (query == null || query.trim().isEmpty()) {
            return new ArrayList<>(source);
        }

        String lowerQuery = query.trim().toLowerCase(Locale.ROOT);
        List<Series> filtered = new ArrayList<>();
        for (Series series : source) {
            String title = series.getTitle();
            if (title != null && title.toLowerCase(Locale.ROOT).contains(lowerQuery)) {
                filtered.add(series);
            }
        }
        return filtered;
    }

    private void openEditSeriesScreen(Series series) {
        saveRecyclerScrollState();
        EditSeriesScreen editScreen = EditSeriesScreen.newInstance(series.getId());
        requireActivity().getSupportFragmentManager().beginTransaction()
                .hide(this)
                .add(R.id.fragment_container, editScreen)
                .addToBackStack(null)
                .commit();
    }

    private void saveRecyclerScrollState() {
        if (seriesRecyclerView == null) {
            return;
        }
        RecyclerView.LayoutManager layoutManager = seriesRecyclerView.getLayoutManager();
        if (!(layoutManager instanceof LinearLayoutManager)) {
            return;
        }
        LinearLayoutManager linearLayoutManager = (LinearLayoutManager) layoutManager;
        savedFirstVisiblePosition = linearLayoutManager.findFirstVisibleItemPosition();
        if (savedFirstVisiblePosition == RecyclerView.NO_POSITION) {
            savedFirstVisibleTop = 0;
            return;
        }
        View firstVisibleView = linearLayoutManager.findViewByPosition(savedFirstVisiblePosition);
        savedFirstVisibleTop = firstVisibleView != null ? firstVisibleView.getTop() : 0;
    }

    private void restoreRecyclerScrollState() {
        if (seriesRecyclerView == null || savedFirstVisiblePosition == RecyclerView.NO_POSITION) {
            return;
        }
        seriesRecyclerView.post(() -> {
            RecyclerView.LayoutManager layoutManager = seriesRecyclerView.getLayoutManager();
            if (!(layoutManager instanceof LinearLayoutManager)) {
                return;
            }
            LinearLayoutManager linearLayoutManager = (LinearLayoutManager) layoutManager;
            linearLayoutManager.scrollToPositionWithOffset(savedFirstVisiblePosition, savedFirstVisibleTop);
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        restoreRecyclerScrollState();
    }

    private void showRandomSeriesFromCollection() {
        // Get series in the current collection and pick one randomly
        viewModel.getSeriesInCollection(collectionId).observe(getViewLifecycleOwner(), seriesList -> {
            if (seriesList != null && !seriesList.isEmpty()) {
                // Generate a random index
                int randomIndex = (int) (Math.random() * seriesList.size());
                Series randomSeries = seriesList.get(randomIndex);

                // Show the random series by opening the edit screen
                openEditSeriesScreen(randomSeries);

                // Remove observer to prevent multiple calls
                viewModel.getSeriesInCollection(collectionId).removeObservers(getViewLifecycleOwner());
            } else {
                // No series in collection
                Toast.makeText(getContext(), "В коллекции нет сериалов", Toast.LENGTH_SHORT).show();
            }
        });
    }
}