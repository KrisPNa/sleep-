package com.example.seriestracker.ui.screens;

import android.graphics.Canvas;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.seriestracker.R;
import com.example.seriestracker.data.entities.Collection;
import com.example.seriestracker.data.entities.Series;
import com.example.seriestracker.ui.adapters.MultiSelectSeriesAdapter;
import com.example.seriestracker.ui.adapters.SeriesAdapter;
import com.example.seriestracker.ui.viewmodels.SeriesViewModel;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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
    }

    private void loadData() {
        // Получаем сериалы в коллекции
        viewModel.getSeriesInCollection(collectionId).observe(getViewLifecycleOwner(), seriesList -> {
            if (seriesList != null) {
                seriesAdapter.setSeriesList(seriesList);

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
                                // Берем первый цвет из списка для индикатора
                                String firstColor = colors.get(0);

                                // Устанавливаем цвет индикатора
                                colorIndicator.setBackgroundColor(Color.parseColor(firstColor));
                                colorIndicator.setVisibility(View.VISIBLE);

                                // Меняем цвет заголовка на цвет коллекции
                                collectionNameTextView.setTextColor(Color.parseColor(firstColor));

                                // Если есть несколько цветов, можно создать градиент (опционально)
                                if (colors.size() > 1) {
                                    // Здесь можно создать и установить градиент
                                    // Например, с помощью GradientDrawable
                                }

                            } catch (Exception e) {
                                // Если цвет некорректный, используем цвет по умолчанию
                                setDefaultColors();
                            }
                        } else {
                            // Если цвет не установлен, используем цвет по умолчанию
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

    private void setDefaultColors() {
        colorIndicator.setBackgroundColor(getResources().getColor(R.color.primary_blue));
        colorIndicator.setVisibility(View.VISIBLE);
        collectionNameTextView.setTextColor(getResources().getColor(R.color.text_dark));
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
        Button cancelButton = dialog.findViewById(R.id.cancelButton);
        Button addSelectedButton = dialog.findViewById(R.id.addSelectedButton);

        // Get all series that are not already in this collection
        viewModel.getAllSeries().observe(this, allSeries -> {
            if (allSeries != null) {
                // Get series that are already in this collection
                viewModel.getSeriesInCollection(collectionId).observe(this, seriesInCollection -> {
                    if (seriesInCollection != null) {
                        // Find series that are NOT in this collection
                        Set<Long> seriesInCollectionIds = new HashSet<>();
                        for (Series series : seriesInCollection) {
                            seriesInCollectionIds.add(series.getId());
                        }

                        List<Series> availableSeries = new ArrayList<>();
                        for (Series series : allSeries) {
                            if (!seriesInCollectionIds.contains(series.getId())) {
                                availableSeries.add(series);
                            }
                        }

                        // Initialize adapter with available series
                        Set<Long> initiallySelected = new HashSet<>(); // Initially none selected
                        MultiSelectSeriesAdapter adapter = new MultiSelectSeriesAdapter(availableSeries, initiallySelected);
                        selectSeriesRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
                        selectSeriesRecyclerView.setAdapter(adapter);

                        // Handle selection changes
                        adapter.setOnSelectionChangeListener(() -> {
                            // Update button state based on selection
                            Set<Long> selectedIds = adapter.getSelectedSeriesIds();
                            addSelectedButton.setEnabled(!selectedIds.isEmpty());
                        });

                        // Set up buttons
                        cancelButton.setOnClickListener(v -> dialog.dismiss());

                        addSelectedButton.setOnClickListener(v -> {
                            Set<Long> selectedIds = adapter.getSelectedSeriesIds();
                            if (!selectedIds.isEmpty()) {
                                List<Long> selectedList = new ArrayList<>(selectedIds);
                                viewModel.addMultipleSeriesToCollection(selectedList, collectionId);

                                Toast.makeText(getContext(),
                                        "Добавлено " + selectedList.size() + " сериалов",
                                        Toast.LENGTH_SHORT).show();

                                dialog.dismiss();

                                // Refresh the series list in the current collection
                                loadData();
                            }
                        });

                        addSelectedButton.setEnabled(false); // Initially disabled
                    }
                });
            }
        });

        dialog.show();
    }

    private void openEditSeriesScreen(Series series) {
        EditSeriesScreen editScreen = EditSeriesScreen.newInstance(series.getId());
        requireActivity().getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, editScreen)
                .addToBackStack(null)
                .commit();


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