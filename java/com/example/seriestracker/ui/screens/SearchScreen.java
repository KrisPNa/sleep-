package com.example.seriestracker.ui.screens;

import android.os.Bundle;
import android.text.Editable;
import android.os.Handler;
import android.os.Looper;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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

    // Тип поиска: 0=Обычный (все), 1=Поиск в коллекциях, 2=Поиск в сериалах
    private int searchType = 0;
    // Фильтр: 0=Все, 1=Коллекции, 2=Сериалы
    private int currentFilter = 0;

    // Поисковый запрос
    private String currentQuery = "";


    // Начальный текст поиска (если передан из другого фрагмента)
    private String initialQuery = "";

    // Для дебаунса поиска
    private Handler searchHandler = new Handler(Looper.getMainLooper());
    private Runnable searchRunnable;
    private static final int SEARCH_DELAY_MS = 300; // 300ms задержка

    // Пул потоков для выполнения поиска
    private ExecutorService executor = Executors.newSingleThreadExecutor();

    // Константы для типа поиска
    public static final int SEARCH_TYPE_ALL = 0;
    public static final int SEARCH_TYPE_COLLECTIONS_ONLY = 1;
    public static final int SEARCH_TYPE_SERIES_ONLY = 2;
    // Для отслеживания последнего запроса во время поиска
    private volatile String lastProcessedQuery = "";

    public SearchScreen() {
        // Required empty public constructor
    }

    public static SearchScreen newInstance(int searchType) {
        SearchScreen fragment = new SearchScreen();
        Bundle args = new Bundle();
        args.putInt("search_type", searchType);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getArguments() != null) {
            searchType = getArguments().getInt("search_type", SEARCH_TYPE_ALL);
            initialQuery = getArguments().getString("initial_query", "");
        }
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


                // Отменяем предыдущий запланированный поиск
                if (searchRunnable != null) {
                    searchHandler.removeCallbacks(searchRunnable);
                }

                currentQuery = s.toString();

                // Запланировать новый поиск с задержкой
                searchRunnable = () -> performSearch();
                searchHandler.postDelayed(searchRunnable, SEARCH_DELAY_MS);
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // Кнопка очистки поиска
        clearSearchButton.setOnClickListener(v -> {
            searchEditText.setText("");
            searchEditText.requestFocus();
        });

        // Кнопка меню фильтрации - показываем только в обычном режиме
        if (searchType == SEARCH_TYPE_ALL) {
            filterMenuButton.setOnClickListener(v -> {
                showFilterMenu(v);
            });
        } else {
            // В контекстном режиме прячем кнопку фильтра
            filterMenuButton.setVisibility(View.GONE);
            // Также устанавливаем соответствующий текст для фильтра и фиксируем значение currentFilter
            if (searchType == SEARCH_TYPE_COLLECTIONS_ONLY) {
                currentFilter = 1; // Только коллекции
                currentFilterText.setText("Фильтр: Только коллекции");
            } else if (searchType == SEARCH_TYPE_SERIES_ONLY) {
                currentFilter = 2; // Только сериалы
                currentFilterText.setText("Фильтр: Только сериалы");
            }
        }

        // Фокусируемся на поле поиска при открытии и устанавливаем начальное значение
        searchEditText.setText(initialQuery);
        searchEditText.setSelection(initialQuery.length()); // Устанавливаем курсор в конец текста
        searchEditText.requestFocus();
        // Если был передан начальный запрос, запускаем поиск
        if (!initialQuery.isEmpty()) {
            currentQuery = initialQuery;
            performSearch();
        }
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

        // Выполняем поиск в фоновом потоке
        executor.execute(() -> {
            // Сохраняем текущий запрос как последний обрабатываемый
            lastProcessedQuery = query;

            List<Series> filteredSeries = new ArrayList<>();
            List<Collection> filteredCollections = new ArrayList<>();

            // Если запрос пустой, показываем все согласно типу поиска и фильтру
            if (query.isEmpty()) {
                if ((searchType == SEARCH_TYPE_ALL || searchType == SEARCH_TYPE_SERIES_ONLY) &&
                        (currentFilter == 0 || currentFilter == 2)) {
                    // Показываем все сериалы
                    filteredSeries.addAll(allSeries);
                }
                if ((searchType == SEARCH_TYPE_ALL || searchType == SEARCH_TYPE_COLLECTIONS_ONLY) &&
                        (currentFilter == 0 || currentFilter == 1)) {
                    // Показываем все коллекции
                    filteredCollections.addAll(allCollections);
                }
            } else {
                // Выполняем поиск по запросу
                if ((searchType == SEARCH_TYPE_ALL || searchType == SEARCH_TYPE_SERIES_ONLY) &&
                        (currentFilter == 0 || currentFilter == 2)) {
                    // Ищем в сериалах
                    for (Series series : allSeries) {
                        if (!lastProcessedQuery.equals(currentQuery)) {
                            // Запрос изменился во время поиска, прерываем
                            return;
                        }

                        if (matchesSeries(series, query)) {
                            filteredSeries.add(series);
                        }
                    }
                }

                if ((searchType == SEARCH_TYPE_ALL || searchType == SEARCH_TYPE_COLLECTIONS_ONLY) &&
                        (currentFilter == 0 || currentFilter == 1)) {
                    // Ищем в коллекциях
                    for (Collection collection : allCollections) {
                        if (!lastProcessedQuery.equals(currentQuery)) {
                            // Запрос изменился во время поиска, прерываем
                            return;
                        }

                        if (matchesCollection(collection, query)) {
                            filteredCollections.add(collection);
                        }
                    }
                }
            }
            // Проверяем, изменился ли запрос во время поиска
            if (!lastProcessedQuery.equals(currentQuery)) {
                // Запрос изменился, не обновляем UI
                return;
            }

            // Обновляем UI в основном потоке
            final List<Series> finalFilteredSeries = filteredSeries;
            final List<Collection> finalFilteredCollections = filteredCollections;

            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    updateUI(finalFilteredSeries, finalFilteredCollections, query);
                });
            }
        });
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
        // Сортируем сериалы, чтобы избранные были сверху
        if (!filteredSeries.isEmpty()) {
            java.util.Collections.sort(filteredSeries, (s1, s2) -> {
                if (s1.getIsFavorite() != s2.getIsFavorite()) {
                    return s2.getIsFavorite() ? 1 : -1;
                }
                return s1.getTitle().compareToIgnoreCase(s2.getTitle());
            });
        }

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

    @Override
    public void onResume() {
        super.onResume();
        // При возвращении в SearchScreen из других фрагментов (например, коллекции),
        // может потребоваться обновить данные или сбросить состояние
        // В данном случае, мы просто обновим поиск с текущим запросом
        performSearch();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Отменяем запланированные задачи поиска
        if (searchRunnable != null) {
            searchHandler.removeCallbacks(searchRunnable);
        }
        // Закрываем пул потоков
        if (executor != null) {
            executor.shutdown();
        }
    }
}