package com.example.seriestracker.ui.screens;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.example.seriestracker.R;
import com.example.seriestracker.data.entities.Collection;
import com.example.seriestracker.data.entities.Series;
import com.example.seriestracker.ui.adapters.CollectionAdapter;
import com.example.seriestracker.ui.adapters.MainPagerAdapter;
import com.example.seriestracker.ui.adapters.SeriesAdapter;
import com.example.seriestracker.ui.viewmodels.SeriesViewModel;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainScreen extends Fragment {

    private SeriesViewModel viewModel;
    private Button createCollectionButton;
    private Button addSeriesButton;
    private Button backupSettingsButton;
    private ImageButton searchButton;
    private TextView welcomeText;
    private ViewPager2 viewPager;
    private TabLayout tabLayout;
    private View buttonsCardView;
    private View overlayBackground;
    private FrameLayout headerLayout;

    // Элементы для контекстного поиска
    private LinearLayout searchContainer;
    private EditText contextualSearchEditText;
    private ImageView clearContextualSearchButton;

    // Элементы для отображения результатов поиска
    private LinearLayout searchResultsContainer;
    private TextView collectionsSearchTitle;
    private RecyclerView collectionsSearchRecyclerView;
    private TextView seriesSearchTitle;
    private RecyclerView seriesSearchRecyclerView;
    private TextView noSearchResultsText;

    // Адаптеры для результатов поиска
    private CollectionAdapter collectionsSearchAdapter;
    private SeriesAdapter seriesSearchAdapter;

    private boolean isButtonsVisible = false;
    private boolean isContextualSearchActive = false;

    public MainScreen() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_main_screen, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(requireActivity()).get(SeriesViewModel.class);

        initViews(view);
        setupViewPagerAndTabs();
        setupEventListeners();
    }

    @Override
    public void onResume() {
        super.onResume();
        // При возвращении в MainScreen из других фрагментов, если был активен контекстный поиск,
        // нужно его закрыть, чтобы не оставаться на экране поиска
        if (isContextualSearchActive) {
            closeContextualSearch();
        }
    }

    private void toggleButtons() {
        if (isButtonsVisible) {
            hideButtons();
        } else {
            showButtons();
        }
    }

    public void showButtons() {
        if (buttonsCardView != null && overlayBackground != null) {
            // Сначала делаем видимыми
            buttonsCardView.setVisibility(View.VISIBLE);
            overlayBackground.setVisibility(View.VISIBLE);

            // Затем анимация появления
            buttonsCardView.animate()
                    .alpha(1f)
                    .scaleY(1f)
                    .scaleX(1f)
                    .setDuration(200)
                    .start();

            overlayBackground.animate()
                    .alpha(1f)
                    .setDuration(200)
                    .start();

            isButtonsVisible = true;
        }
    }

    public void hideButtons() {
        if (buttonsCardView != null && overlayBackground != null) {
            // Анимация исчезновения
            buttonsCardView.animate()
                    .alpha(0f)
                    .scaleY(0.9f)
                    .scaleX(0.9f)
                    .setDuration(200)
                    .withEndAction(() -> {
                        // После анимации скрываем
                        buttonsCardView.setVisibility(View.GONE);
                        overlayBackground.setVisibility(View.GONE);
                    })
                    .start();

            overlayBackground.animate()
                    .alpha(0f)
                    .setDuration(200)
                    .start();

            isButtonsVisible = false;
        }
    }

    // Эти методы теперь не нужны для скроллинга, но оставляем на всякий случай
    public void hideButtonsFromScroll() {

    }

    public void showButtonsFromScroll() {
        // Не показываем кнопки при скроллинге вверх
    }

    private void initViews(View view) {
        createCollectionButton = view.findViewById(R.id.createCollectionButton);
        addSeriesButton = view.findViewById(R.id.addSeriesButton);
        backupSettingsButton = view.findViewById(R.id.backupSettingsButton);
        searchButton = view.findViewById(R.id.searchButton);
        welcomeText = view.findViewById(R.id.welcomeText);
        viewPager = view.findViewById(R.id.viewPager);
        tabLayout = view.findViewById(R.id.tabLayout);
        buttonsCardView = view.findViewById(R.id.buttonsCardView);
        overlayBackground = view.findViewById(R.id.overlayBackground);
        headerLayout = (FrameLayout) view.findViewById(R.id.headerLayout);

        // Инициализация элементов контекстного поиска
        searchContainer = view.findViewById(R.id.searchContainer);
        contextualSearchEditText = view.findViewById(R.id.contextualSearchEditText);
        clearContextualSearchButton = view.findViewById(R.id.clearContextualSearchButton);

        // Инициализация элементов для отображения результатов поиска
        searchResultsContainer = view.findViewById(R.id.searchResultsContainer);
        collectionsSearchTitle = view.findViewById(R.id.collectionsSearchTitle);
        collectionsSearchRecyclerView = view.findViewById(R.id.collectionsSearchRecyclerView);
        seriesSearchTitle = view.findViewById(R.id.seriesSearchTitle);
        seriesSearchRecyclerView = view.findViewById(R.id.seriesSearchRecyclerView);
        noSearchResultsText = view.findViewById(R.id.noSearchResultsText);

        // Изначально скрываем кнопки
        buttonsCardView.setVisibility(View.GONE);
        buttonsCardView.setAlpha(0f);
        overlayBackground.setVisibility(View.GONE);
        overlayBackground.setAlpha(0f);
    }

    private void setupViewPagerAndTabs() {
        // Initialize the adapter
        MainPagerAdapter adapter = new MainPagerAdapter(this);
        viewPager.setAdapter(adapter);

        // Connect TabLayout with ViewPager2
        TabLayoutMediator mediator = new TabLayoutMediator(tabLayout, viewPager,
                (tab, position) -> {
                    if (position == 0) {
                        tab.setText("Коллекции");
                    } else {
                        tab.setText("Сериалы");
                    }
                });
        mediator.attach();
    }

    private void setupEventListeners() {

        // Клик по затемненному фону - скрытие кнопок
        overlayBackground.setOnClickListener(v -> {
            hideButtons();
        });

        createCollectionButton.setOnClickListener(v -> {
            hideButtons(); // Скрываем кнопки при нажатии на них
            CreateCollectionScreen createCollectionScreen = new CreateCollectionScreen();
            requireActivity().getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, createCollectionScreen)
                    .addToBackStack(null)
                    .commit();
        });

        addSeriesButton.setOnClickListener(v -> {
            hideButtons(); // Скрываем кнопки при нажатии на них
            AddSeriesScreen addSeriesScreen = new AddSeriesScreen();
            requireActivity().getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, addSeriesScreen)
                    .addToBackStack(null)
                    .commit();
        });

        // Обработчик клика для текста "Нет, лакорн смотреть" - показывает/скрывает кнопки
        welcomeText.setOnClickListener(v -> {
            toggleButtons();
        });
        searchButton.setOnClickListener(v -> {
            hideButtons(); // Скрываем кнопки при открытии поиска
            // Открываем контекстный поиск вместо полноэкранного
            if (isContextualSearchActive) {
                // Если активен контекстный поиск, закрываем его
                closeContextualSearch();
            } else {
                // Открываем контекстный поиск
                openContextualSearch();
            }
        });

        backupSettingsButton.setOnClickListener(v -> {
            hideButtons(); // Скрываем кнопки при открытии настроек
            openBackupSettingsScreen();
        });

        // Также скрываем кнопки при переключении табов
        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                hideButtons();
                // При переключении вкладок, если активен контекстный поиск, закрываем его
                if (isContextualSearchActive) {
                    closeContextualSearch();
                }
            }
        });
        // Обработка контекстного поиска
        setupContextualSearch();
    }

    private void setupContextualSearch() {
        // Инициализируем адаптеры для результатов поиска
        initializeSearchAdapters();

        // Добавляем слушатель для текстового поля контекстного поиска
        contextualSearchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // Показываем/скрываем кнопку очистки
                clearContextualSearchButton.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
            }

            @Override
            public void afterTextChanged(Editable s) {
                // Выполняем поиск в зависимости от текущей вкладки
                performContextualSearch(s.toString());
            }
        });

        // Кнопка очистки контекстного поиска
        clearContextualSearchButton.setOnClickListener(v -> {
            contextualSearchEditText.setText("");
            contextualSearchEditText.requestFocus();
        });
    }

    private void initializeSearchAdapters() {
        // Настройка адаптера для поиска коллекций
        collectionsSearchAdapter = new CollectionAdapter(new CollectionAdapter.OnCollectionClickListener() {
            @Override
            public void onCollectionClick(Collection collection) {
                // Открытие деталей коллекции
                openCollectionDetailScreen(collection);
            }

            @Override
            public void onFavoriteClick(Collection collection) {
                // Переключение состояния избранного
                collection.setFavorite(!collection.isFavorite());
                viewModel.updateCollection(collection);

                Toast.makeText(getContext(),
                        collection.isFavorite() ? "Добавлено в избранное" : "Убрано из избранного",
                        Toast.LENGTH_SHORT).show();
            }
        });

        collectionsSearchRecyclerView.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.VERTICAL, false));
        collectionsSearchRecyclerView.setAdapter(collectionsSearchAdapter);

        // Настройка адаптера для поиска сериалов
        seriesSearchAdapter = new SeriesAdapter(new SeriesAdapter.OnSeriesClickListener() {
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

        seriesSearchRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        seriesSearchRecyclerView.setAdapter(seriesSearchAdapter);
    }

    private void openContextualSearch() {
        isContextualSearchActive = true;
        searchContainer.setVisibility(View.VISIBLE);

        // Анимация появления
        searchContainer.animate()
                .alpha(1f)
                .setDuration(200)
                .start();

        // Показываем клавиатуру и устанавливаем фокус
        contextualSearchEditText.requestFocus();
        android.view.inputmethod.InputMethodManager imm =
                (android.view.inputmethod.InputMethodManager) getContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(contextualSearchEditText, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
        }
    }

    private void closeContextualSearch() {
        isContextualSearchActive = false;

        // Анимация исчезновения
        searchContainer.animate()
                .alpha(0f)
                .setDuration(200)
                .withEndAction(() -> {
                    searchContainer.setVisibility(View.GONE);
                    contextualSearchEditText.setText("");
                })
                .start();

        // Скрываем клавиатуру
        android.view.inputmethod.InputMethodManager imm =
                (android.view.inputmethod.InputMethodManager) getContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(contextualSearchEditText.getWindowToken(), 0);
        }

        // Сбрасываем флаг
        // Метод resetSearchFlag() не существует, убрал вызов

        // Скрываем результаты поиска и показываем нормальный интерфейс
        hideSearchResults();
    }

    private void performContextualSearch(String query) {
        if (query.trim().isEmpty()) {
            // Если запрос пустой, скрываем результаты поиска и показываем нормальный интерфейс
            hideSearchResults();
            return;
        }

        // Показываем контейнер с результатами поиска
        showSearchResults();

        // Получаем текущую позицию в ViewPager
        int currentPosition = viewPager.getCurrentItem();

        // Выполняем поиск в отдельном потоке, чтобы не нагружать основной поток
        viewModel.getAllCollections().observe(this, collections -> {
            viewModel.getAllSeries().observe(this, seriesList -> {
                // Оба списка получены, теперь выполняем фильтрацию

                List<Collection> filteredCollections = new ArrayList<>();
                List<Series> filteredSeries = new ArrayList<>();

                // Если находимся на вкладке коллекций - показываем ТОЛЬКО коллекции
                if (currentPosition == MainPagerAdapter.COLLECTIONS_FRAGMENT_POSITION) {
                    if (collections != null) {
                        // Сначала фильтруем коллекции
                        for (Collection collection : collections) {
                            if (matchesCollection(collection, query)) {
                                filteredCollections.add(collection);
                            }
                        }

                        // Используем тот же метод, что и в CollectionDetailScreen
                        // Получаем количество сериалов для каждой коллекции
                        for (Collection collection : filteredCollections) {
                            viewModel.getSeriesInCollection(collection.getId()).observe(this, seriesInCollection -> {
                                if (seriesInCollection != null) {
                                    collection.setSeriesCount(seriesInCollection.size());
                                    collectionsSearchAdapter.notifyDataSetChanged(); // Обновить отображение
                                }
                            });
                        }
                    }
                    // На вкладке коллекций НЕ показываем сериалы вообще
                    filteredSeries = new ArrayList<>();

                    // Обновляем UI
                    if (getContext() != null) {
                        collectionsSearchAdapter.setCollections(filteredCollections);
                        seriesSearchAdapter.setSeriesList(filteredSeries);

                        // Показываем/скрываем заголовки и RecyclerView
                        collectionsSearchTitle.setVisibility(filteredCollections.isEmpty() ? View.GONE : View.VISIBLE);
                        collectionsSearchRecyclerView.setVisibility(filteredCollections.isEmpty() ? View.GONE : View.VISIBLE);

                        // Скрываем заголовок и список сериалов (так как мы на вкладке коллекций)
                        seriesSearchTitle.setVisibility(View.GONE);
                        seriesSearchRecyclerView.setVisibility(View.GONE);

                        // Показываем сообщение "Ничего не найдено", если коллекций не найдено
                        noSearchResultsText.setVisibility(filteredCollections.isEmpty() ? View.VISIBLE : View.GONE);
                    }
                }
                // Если находимся на вкладке сериалов - показываем ТОЛЬКО сериалы
                else if (currentPosition == MainPagerAdapter.SERIES_FRAGMENT_POSITION) {
                    if (seriesList != null) {
                        for (Series series : seriesList) {
                            if (matchesSeries(series, query)) {
                                filteredSeries.add(series);
                            }
                        }
                    }
                    // На вкладке сериалов НЕ показываем коллекции вообще
                    filteredCollections = new ArrayList<>();

                    // Обновляем UI
                    if (getContext() != null) {
                        collectionsSearchAdapter.setCollections(filteredCollections);
                        seriesSearchAdapter.setSeriesList(filteredSeries);

                        // Показываем/скрываем заголовки и RecyclerView
                        seriesSearchTitle.setVisibility(filteredSeries.isEmpty() ? View.GONE : View.VISIBLE);
                        seriesSearchRecyclerView.setVisibility(filteredSeries.isEmpty() ? View.GONE : View.VISIBLE);

                        // Скрываем заголовок и список коллекций (так как мы на вкладке сериалов)
                        collectionsSearchTitle.setVisibility(View.GONE);
                        collectionsSearchRecyclerView.setVisibility(View.GONE);

                        // Показываем сообщение "Ничего не найдено", если сериалов не найдено
                        noSearchResultsText.setVisibility(filteredSeries.isEmpty() ? View.VISIBLE : View.GONE);
                    }
                }
            });
        });
    }

    private boolean matchesSeries(Series series, String query) {
        String lowerQuery = query.toLowerCase();
        if (series.getTitle() != null && series.getTitle().toLowerCase().contains(lowerQuery)) {
            return true;
        }
        if (series.getNotes() != null && series.getNotes().toLowerCase().contains(lowerQuery)) {
            return true;
        }
        if (series.getGenre() != null && series.getGenre().toLowerCase().contains(lowerQuery)) {
            return true;
        }
        return false;
    }

    private boolean matchesCollection(Collection collection, String query) {
        return collection.getName() != null &&
                collection.getName().toLowerCase().contains(query.toLowerCase());
    }

    private void showSearchResults() {
        searchResultsContainer.setVisibility(View.VISIBLE);
        viewPager.setVisibility(View.GONE);
        tabLayout.setVisibility(View.GONE);
        backupSettingsButton.setVisibility(View.GONE);
    }

    private void hideSearchResults() {
        searchResultsContainer.setVisibility(View.GONE);
        viewPager.setVisibility(View.VISIBLE);
        tabLayout.setVisibility(View.VISIBLE);
        backupSettingsButton.setVisibility(View.VISIBLE);

        // Очищаем адаптеры
        collectionsSearchAdapter.setCollections(new ArrayList<>());
        seriesSearchAdapter.setSeriesList(new ArrayList<>());
    }

    private void checkAndShowNoResults(boolean noResults) {
        noSearchResultsText.setVisibility(noResults ? View.VISIBLE : View.GONE);
    }

    private void openBackupSettingsScreen() {
        try {
            BackupSettingsScreen backupScreen = new BackupSettingsScreen();
            requireActivity().getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, backupScreen)
                    .addToBackStack(null)
                    .commit();
        } catch (Exception e) {
            Toast.makeText(getContext(),
                    "Ошибка открытия экрана резервного копирования",
                    Toast.LENGTH_SHORT).show();
            e.printStackTrace();
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