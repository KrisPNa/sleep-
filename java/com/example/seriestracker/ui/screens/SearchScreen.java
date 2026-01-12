package com.example.seriestracker.ui.screens;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.PopupMenu;
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
import com.example.seriestracker.ui.adapters.CollectionAdapter;
import com.example.seriestracker.ui.adapters.SeriesAdapter;
import com.example.seriestracker.ui.viewmodels.SeriesViewModel;

import java.util.ArrayList;
import java.util.List;

public class SearchScreen extends Fragment {

    private SeriesViewModel viewModel;

    // UI элементы
    private ImageButton backButton;
    private EditText searchEditText;
    private ImageView clearSearchButton;
    private ImageButton filterMenuButton;
    private TextView currentFilterText;
    private TextView seriesTitle;
    private TextView collectionsTitle;
    private RecyclerView seriesRecyclerView;
    private RecyclerView collectionsRecyclerView;
    private TextView noResultsText;

    // Адаптеры
    private SeriesAdapter seriesAdapter;
    private CollectionAdapter collectionAdapter;

    // Данные
    private List<Series> allSeries = new ArrayList<>();
    private List<Collection> allCollections = new ArrayList<>();

    // Фильтр: 0=Все, 1=Коллекции, 2=Сериалы
    private int currentFilter = 0;

    // Поисковый запрос
    private String currentQuery = "";

    public SearchScreen() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_search, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(requireActivity()).get(SeriesViewModel.class);

        initViews(view);
        setupRecyclerViews();
        setupEventListeners();
        loadData();
    }

    private void initViews(View view) {
        backButton = view.findViewById(R.id.backButton);
        searchEditText = view.findViewById(R.id.searchEditText);
        clearSearchButton = view.findViewById(R.id.clearSearchButton);
        filterMenuButton = view.findViewById(R.id.filterMenuButton);
        currentFilterText = view.findViewById(R.id.currentFilterText);
        seriesTitle = view.findViewById(R.id.seriesTitle);
        collectionsTitle = view.findViewById(R.id.collectionsTitle);
        seriesRecyclerView = view.findViewById(R.id.seriesRecyclerView);
        collectionsRecyclerView = view.findViewById(R.id.collectionsRecyclerView);
        noResultsText = view.findViewById(R.id.noResultsText);
    }

    private void setupRecyclerViews() {
        // Настройка адаптера для сериалов
        seriesAdapter = new SeriesAdapter(new SeriesAdapter.OnSeriesClickListener() {
            @Override
            public void onSeriesClick(Series series) {
                openEditSeriesScreen(series);
            }

            @Override
            public void onWatchedToggle(Series series, boolean isWatched) {
                viewModel.toggleWatchedStatus(series.getId(), isWatched);
            }

            @Override
            public void onFavoriteToggle(Series series, boolean isFavorite) {
                viewModel.toggleFavoriteStatus(series.getId(), isFavorite);
            }
        });

        seriesRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        seriesRecyclerView.setAdapter(seriesAdapter);

        // Настройка адаптера для коллекций - ИСПРАВЛЕНО: добавлен onFavoriteClick
        collectionAdapter = new CollectionAdapter(new CollectionAdapter.OnCollectionClickListener() {
            @Override
            public void onCollectionClick(Collection collection) {
                openCollectionDetailScreen(collection);
            }

            @Override
            public void onFavoriteClick(Collection collection) {
                // Переключаем состояние избранного
                collection.setFavorite(!collection.isFavorite());
                viewModel.updateCollection(collection);

                Toast.makeText(getContext(),
                        collection.isFavorite() ? "Добавлено в избранное" : "Убрано из избранного",
                        Toast.LENGTH_SHORT).show();
            }
        });

        collectionsRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        collectionsRecyclerView.setAdapter(collectionAdapter);
    }

    private void setupEventListeners() {
        // Кнопка назад
        backButton.setOnClickListener(v -> {
            requireActivity().getSupportFragmentManager().popBackStack();
        });

        // Обработчик текста поиска
        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // Показываем/скрываем кнопку очистки
                clearSearchButton.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
                currentQuery = s.toString();
                performSearch();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // Кнопка очистки поиска
        clearSearchButton.setOnClickListener(v -> {
            searchEditText.setText("");
            searchEditText.requestFocus();
        });

        // Кнопка меню фильтрации
        filterMenuButton.setOnClickListener(v -> {
            showFilterMenu(v);
        });

        // Фокусируемся на поле поиска при открытии
        searchEditText.requestFocus();
    }

    private void showFilterMenu(View view) {
        PopupMenu popupMenu = new PopupMenu(requireContext(), view);
        popupMenu.getMenuInflater().inflate(R.menu.filter_menu, popupMenu.getMenu());

        // Устанавливаем галочку на текущем фильтре
        switch (currentFilter) {
            case 0:
                popupMenu.getMenu().findItem(R.id.filter_all).setChecked(true);
                break;
            case 1:
                popupMenu.getMenu().findItem(R.id.filter_collections).setChecked(true);
                break;
            case 2:
                popupMenu.getMenu().findItem(R.id.filter_series).setChecked(true);
                break;
        }

        popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                int itemId = item.getItemId();

                if (itemId == R.id.filter_all) {
                    currentFilter = 0;
                    currentFilterText.setText("Фильтр: Все");
                } else if (itemId == R.id.filter_collections) {
                    currentFilter = 1;
                    currentFilterText.setText("Фильтр: Только коллекции");
                } else if (itemId == R.id.filter_series) {
                    currentFilter = 2;
                    currentFilterText.setText("Фильтр: Только сериалы");
                }

                item.setChecked(true);
                performSearch();
                return true;
            }
        });

        popupMenu.show();
    }

    private void loadData() {
        // Загружаем все сериалы
        viewModel.getAllSeries().observe(getViewLifecycleOwner(), seriesList -> {
            if (seriesList != null) {
                allSeries = seriesList;
                performSearch();
            }
        });

        // Загружаем все коллекции
        viewModel.getAllCollections().observe(getViewLifecycleOwner(), collections -> {
            if (collections != null) {
                allCollections = collections;
                performSearch();
            }
        });
    }

    private void performSearch() {
        String query = currentQuery.toLowerCase().trim();

        List<Series> filteredSeries = new ArrayList<>();
        List<Collection> filteredCollections = new ArrayList<>();

        // Если запрос пустой, показываем все согласно фильтру
        if (query.isEmpty()) {
            if (currentFilter == 0 || currentFilter == 2) {
                // Показываем все сериалы
                filteredSeries.addAll(allSeries);
            }
            if (currentFilter == 0 || currentFilter == 1) {
                // Показываем все коллекции
                filteredCollections.addAll(allCollections);
            }
        } else {
            // Выполняем поиск по запросу
            if (currentFilter == 0 || currentFilter == 2) {
                // Ищем в сериалах
                for (Series series : allSeries) {
                    if (matchesSeries(series, query)) {
                        filteredSeries.add(series);
                    }
                }
            }

            if (currentFilter == 0 || currentFilter == 1) {
                // Ищем в коллекциях
                for (Collection collection : allCollections) {
                    if (matchesCollection(collection, query)) {
                        filteredCollections.add(collection);
                    }
                }
            }
        }

        // Обновляем UI
        updateUI(filteredSeries, filteredCollections, query);
    }

    private boolean matchesSeries(Series series, String query) {
        if (series.getTitle() != null && series.getTitle().toLowerCase().contains(query)) {
            return true;
        }
        if (series.getNotes() != null && series.getNotes().toLowerCase().contains(query)) {
            return true;
        }
        if (series.getGenre() != null && series.getGenre().toLowerCase().contains(query)) {
            return true;
        }
        return false;
    }

    private boolean matchesCollection(Collection collection, String query) {
        return collection.getName() != null &&
                collection.getName().toLowerCase().contains(query);
    }

    private void updateUI(List<Series> filteredSeries, List<Collection> filteredCollections, String query) {
        // Обновляем сериалы
        if (!filteredSeries.isEmpty()) {
            seriesTitle.setVisibility(View.VISIBLE);
            seriesRecyclerView.setVisibility(View.VISIBLE);
            seriesAdapter.setSeriesList(filteredSeries);
        } else {
            seriesTitle.setVisibility(View.GONE);
            seriesRecyclerView.setVisibility(View.GONE);
        }

        // Обновляем коллекции
        if (!filteredCollections.isEmpty()) {
            collectionsTitle.setVisibility(View.VISIBLE);
            collectionsRecyclerView.setVisibility(View.VISIBLE);
            collectionAdapter.setCollections(filteredCollections);
        } else {
            collectionsTitle.setVisibility(View.GONE);
            collectionsRecyclerView.setVisibility(View.GONE);
        }

        // Показываем сообщение "нет результатов"
        if (filteredSeries.isEmpty() && filteredCollections.isEmpty()) {
            if (query.isEmpty()) {
                noResultsText.setText("Введите запрос для поиска");
            } else {
                noResultsText.setText("Ничего не найдено по запросу: \"" + query + "\"");
            }
            noResultsText.setVisibility(View.VISIBLE);
        } else {
            noResultsText.setVisibility(View.GONE);
        }
    }

    private void openEditSeriesScreen(Series series) {
        EditSeriesScreen editScreen = EditSeriesScreen.newInstance(series.getId());
        requireActivity().getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, editScreen)
                .addToBackStack(null)
                .commit();
    }

    private void openCollectionDetailScreen(Collection collection) {
        CollectionDetailScreen detailScreen = new CollectionDetailScreen();
        Bundle bundle = new Bundle();
        bundle.putLong("collectionId", collection.getId());
        detailScreen.setArguments(bundle);

        requireActivity().getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, detailScreen)
                .addToBackStack(null)
                .commit();
    }
}