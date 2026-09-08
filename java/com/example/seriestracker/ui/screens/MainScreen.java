package com.example.seriestracker.ui.screens;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
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
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.example.seriestracker.R;
import com.example.seriestracker.data.entities.Collection;
import com.example.seriestracker.data.entities.Series;
import com.example.seriestracker.ui.adapters.CollectionAdapter;
import com.example.seriestracker.ui.adapters.MainPagerAdapter;
import com.example.seriestracker.ui.adapters.SectionHeaderAdapter;
import com.example.seriestracker.ui.adapters.SeriesAdapter;
import com.example.seriestracker.ui.utils.RecyclerViewPerf;
import com.example.seriestracker.ui.viewmodels.SeriesViewModel;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainScreen extends Fragment {

    private static final int SEARCH_DELAY_MS = 450;
    private static final String KEY_SEARCH_QUERY = "search_query";
    private static final String KEY_SEARCH_ACTIVE = "search_active";

    private SeriesViewModel viewModel;
    private Button createCollectionButton;
    private Button addSeriesButton;
    private ImageButton searchButton;
    private ImageButton settingsGearButton;
    private TextView welcomeText;
    private ViewPager2 viewPager;
    private TabLayout tabLayout;
    private View buttonsCardView;
    private View overlayBackground;
    private FrameLayout headerLayout;

    // Элементы для контекстного поиска
    private LinearLayout searchContainer;
    private EditText contextualSearchEditText;
    private ImageView closeSearchButton;

    // Элементы для отображения результатов поиска
    private View searchResultsContainer;
    private RecyclerView searchResultsRecyclerView;
    private TextView noSearchResultsText;

    // Адаптеры для результатов поиска
    private SectionHeaderAdapter collectionsHeaderAdapter;
    private SectionHeaderAdapter seriesHeaderAdapter;
    private CollectionAdapter collectionsSearchAdapter;
    private SeriesAdapter seriesSearchAdapter;
    private ConcatAdapter searchResultsAdapter;

    private List<Collection> lastDisplayedCollections = new ArrayList<>();
    private List<Series> lastDisplayedSeries = new ArrayList<>();

    private boolean isButtonsVisible = false;
    private boolean isContextualSearchActive = false;
    private boolean isRestoringSearch = false;
    private String preservedSearchQuery = "";

    private List<Collection> cachedCollections = new ArrayList<>();
    private List<Series> cachedSeries = new ArrayList<>();
    private List<SearchableCollection> searchableCollections = new ArrayList<>();
    private List<SearchableSeries> searchableSeries = new ArrayList<>();
    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private Runnable searchRunnable;
    private ExecutorService searchExecutor;
    private volatile long searchGeneration = 0;

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

        if (savedInstanceState != null) {
            preservedSearchQuery = savedInstanceState.getString(KEY_SEARCH_QUERY, "");
            isContextualSearchActive = savedInstanceState.getBoolean(KEY_SEARCH_ACTIVE, false);
        }

        viewModel = new ViewModelProvider(requireActivity()).get(SeriesViewModel.class);

        initViews(view);
        setupViewPagerAndTabs();
        setupEventListeners();
        observeSearchData();
        restoreSearchUiIfNeeded();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        saveSearchQueryFromUi();
        outState.putString(KEY_SEARCH_QUERY, preservedSearchQuery);
        outState.putBoolean(KEY_SEARCH_ACTIVE, isContextualSearchActive);
    }

    @Override
    public void onDestroyView() {
        saveSearchQueryFromUi();
        lastDisplayedCollections.clear();
        lastDisplayedSeries.clear();
        if (searchRunnable != null) {
            searchHandler.removeCallbacks(searchRunnable);
            searchRunnable = null;
        }
        if (searchExecutor != null) {
            searchExecutor.shutdownNow();
            searchExecutor = null;
        }
        super.onDestroyView();
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
            showSettingsGear();

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
            hideSettingsGear();
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

    private void showSettingsGear() {
        if (settingsGearButton == null) return;
        settingsGearButton.setVisibility(View.VISIBLE);
        settingsGearButton.animate().alpha(1f).setDuration(200).start();
    }

    private void hideSettingsGear() {
        if (settingsGearButton == null) return;
        settingsGearButton.animate()
                .alpha(0f)
                .setDuration(150)
                .withEndAction(() -> {
                    if (settingsGearButton != null) {
                        settingsGearButton.setVisibility(View.GONE);
                    }
                })
                .start();
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
        searchButton = view.findViewById(R.id.searchButton);
        settingsGearButton = view.findViewById(R.id.settingsGearButton);
        welcomeText = view.findViewById(R.id.welcomeText);
        viewPager = view.findViewById(R.id.viewPager);
        tabLayout = view.findViewById(R.id.tabLayout);
        buttonsCardView = view.findViewById(R.id.buttonsCardView);
        overlayBackground = view.findViewById(R.id.overlayBackground);
        headerLayout = (FrameLayout) view.findViewById(R.id.headerLayout);

        // Инициализация элементов контекстного поиска
        searchContainer = view.findViewById(R.id.searchContainer);
        contextualSearchEditText = view.findViewById(R.id.contextualSearchEditText);
        closeSearchButton = view.findViewById(R.id.closeSearchButton);

        // Инициализация элементов для отображения результатов поиска
        searchResultsContainer = view.findViewById(R.id.searchResultsContainer);
        searchResultsRecyclerView = view.findViewById(R.id.searchResultsRecyclerView);
        noSearchResultsText = view.findViewById(R.id.noSearchResultsText);

        // Изначально скрываем кнопки
        buttonsCardView.setVisibility(View.GONE);
        buttonsCardView.setAlpha(0f);
        overlayBackground.setVisibility(View.GONE);
        overlayBackground.setAlpha(0f);
        if (settingsGearButton != null) {
            settingsGearButton.setVisibility(View.GONE);
            settingsGearButton.setAlpha(0f);
        }
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
        if (settingsGearButton != null) {
            settingsGearButton.setOnClickListener(v -> {
                hideButtons();
                openBackupSettingsScreen();
            });
        }
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

        // Также скрываем кнопки при переключении табов
        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                hideButtons();
            }
        });
        // Обработка контекстного поиска
        setupContextualSearch();
    }

    private void setupContextualSearch() {
        searchExecutor = Executors.newSingleThreadExecutor();
        initializeSearchAdapters();

        contextualSearchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (isRestoringSearch) {
                    return;
                }
                preservedSearchQuery = s.toString();
                scheduleContextualSearch(s.toString());
            }
        });

        contextualSearchEditText.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                hideSearchKeyboard();
                return true;
            }
            return false;
        });

        closeSearchButton.setOnClickListener(v -> closeContextualSearch());
    }

    private void initializeSearchAdapters() {
        collectionsHeaderAdapter = new SectionHeaderAdapter("Коллекции");
        seriesHeaderAdapter = new SectionHeaderAdapter("Сериалы");

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
        seriesSearchAdapter.setInCollectionContext(false);
        seriesSearchAdapter.setOnSeriesMenuListener(new SeriesAdapter.OnSeriesMenuListener() {
            @Override
            public void onChangeStatus(Series series, String newStatus) {
                viewModel.updateSeriesStatus(series.getId(), newStatus);
            }

            @Override
            public void onDeleteAction(Series series) {
                new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                        .setTitle("Удалить сериал?")
                        .setMessage("«" + series.getTitle() + "» будет удалён.")
                        .setPositiveButton("Удалить", (d, w) -> viewModel.deleteSeries(series.getId()))
                        .setNegativeButton("Отмена", null)
                        .show();
            }
        });

        searchResultsAdapter = new ConcatAdapter(
                collectionsHeaderAdapter,
                collectionsSearchAdapter,
                seriesHeaderAdapter,
                seriesSearchAdapter
        );
        searchResultsRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        searchResultsRecyclerView.setAdapter(searchResultsAdapter);
        searchResultsRecyclerView.setItemViewCacheSize(20);
        searchResultsRecyclerView.setHasFixedSize(true);
        RecyclerViewPerf.tune(searchResultsRecyclerView, 20);
    }

    private void observeSearchData() {
        if (viewModel == null) {
            return;
        }
        viewModel.getAllCollectionsWithSeriesCount().observe(getViewLifecycleOwner(), collections -> {
            cachedCollections = collections != null ? new ArrayList<>(collections) : new ArrayList<>();
            rebuildSearchableCollections();
            maybeRefreshActiveSearch();
        });
        viewModel.getAllSeries().observe(getViewLifecycleOwner(), seriesList -> {
            cachedSeries = seriesList != null ? new ArrayList<>(seriesList) : new ArrayList<>();
            rebuildSearchableSeries();
            maybeRefreshActiveSearch();
        });
    }

    private void rebuildSearchableCollections() {
        searchableCollections = new ArrayList<>(cachedCollections.size());
        for (Collection collection : cachedCollections) {
            SearchableCollection item = new SearchableCollection();
            item.collection = collection;
            item.nameLower = collection.getName() != null
                    ? collection.getName().toLowerCase()
                    : "";
            searchableCollections.add(item);
        }
    }

    private void rebuildSearchableSeries() {
        searchableSeries = new ArrayList<>(cachedSeries.size());
        for (Series series : cachedSeries) {
            SearchableSeries item = new SearchableSeries();
            item.series = series;
            item.titleLower = series.getTitle() != null ? series.getTitle().toLowerCase() : "";
            item.notesLower = series.getNotes() != null ? series.getNotes().toLowerCase() : "";
            item.watchUrlLower = series.getWatchUrl() != null ? series.getWatchUrl().toLowerCase() : "";
            item.watchAtLower = series.getWatchAt() != null ? series.getWatchAt().toLowerCase() : "";
            item.genreLower = series.getGenre() != null ? series.getGenre().toLowerCase() : "";
            searchableSeries.add(item);
        }
    }

    private static final class SearchableCollection {
        Collection collection;
        String nameLower;
    }

    private static final class SearchableSeries {
        Series series;
        String titleLower;
        String notesLower;
        String watchUrlLower;
        String watchAtLower;
        String genreLower;
    }

    private void scheduleContextualSearch(String query) {
        if (searchRunnable != null) {
            searchHandler.removeCallbacks(searchRunnable);
        }
        final String searchQuery = query;
        searchRunnable = () -> runContextualSearch(searchQuery);
        searchHandler.postDelayed(searchRunnable, SEARCH_DELAY_MS);
    }

    private void runContextualSearch(String query) {
        String trimmedQuery = query.trim();
        if (trimmedQuery.isEmpty()) {
            hideSearchResults();
            return;
        }

        showSearchResults();
        final String lowerQuery = trimmedQuery.toLowerCase();
        final long generation = ++searchGeneration;

        if (searchExecutor == null || searchExecutor.isShutdown()) {
            return;
        }

        searchExecutor.execute(() -> {
            if (generation != searchGeneration) {
                return;
            }

            List<Collection> filteredCollections = new ArrayList<>();
            List<Series> filteredSeries = new ArrayList<>();
            List<SearchableCollection> collectionsSnapshot = searchableCollections;
            List<SearchableSeries> seriesSnapshot = searchableSeries;

            for (SearchableCollection item : collectionsSnapshot) {
                if (generation != searchGeneration) {
                    return;
                }
                if (item.nameLower.contains(lowerQuery)) {
                    filteredCollections.add(item.collection);
                }
            }

            for (SearchableSeries item : seriesSnapshot) {
                if (generation != searchGeneration) {
                    return;
                }
                if (item.titleLower.contains(lowerQuery)
                        || item.notesLower.contains(lowerQuery)
                        || item.watchUrlLower.contains(lowerQuery)
                        || item.watchAtLower.contains(lowerQuery)
                        || item.genreLower.contains(lowerQuery)) {
                    filteredSeries.add(item.series);
                }
            }

            searchHandler.post(() -> {
                if (!isAdded() || generation != searchGeneration || !isContextualSearchActive) {
                    return;
                }
                updateSearchResultsUi(filteredCollections, filteredSeries);
            });
        });
    }

    private void hideSearchKeyboard() {
        if (contextualSearchEditText != null) {
            contextualSearchEditText.clearFocus();
        }
        android.view.inputmethod.InputMethodManager imm =
                (android.view.inputmethod.InputMethodManager) requireContext()
                        .getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
        if (imm != null && contextualSearchEditText != null) {
            imm.hideSoftInputFromWindow(contextualSearchEditText.getWindowToken(), 0);
        }
    }

    private void saveSearchQueryFromUi() {
        if (contextualSearchEditText != null) {
            preservedSearchQuery = contextualSearchEditText.getText().toString();
        }
        if (!preservedSearchQuery.trim().isEmpty()) {
            isContextualSearchActive = true;
        }
    }

    private void restoreSearchUiIfNeeded() {
        if (!isContextualSearchActive || preservedSearchQuery.trim().isEmpty()) {
            return;
        }
        hideButtons();
        if (searchContainer != null) {
            searchContainer.clearAnimation();
            searchContainer.setVisibility(View.VISIBLE);
            searchContainer.setAlpha(1f);
        }
        lastDisplayedCollections.clear();
        lastDisplayedSeries.clear();
        isRestoringSearch = true;
        try {
            if (contextualSearchEditText != null) {
                contextualSearchEditText.setText(preservedSearchQuery);
                contextualSearchEditText.setSelection(preservedSearchQuery.length());
            }
        } finally {
            isRestoringSearch = false;
        }
        runContextualSearch(preservedSearchQuery);
    }

    private void maybeRefreshActiveSearch() {
        if (isContextualSearchActive && !preservedSearchQuery.trim().isEmpty()) {
            runContextualSearch(preservedSearchQuery);
        }
    }

    private void resetSearchUiImmediate() {
        isContextualSearchActive = false;
        preservedSearchQuery = "";
        searchGeneration++;
        if (searchRunnable != null) {
            searchHandler.removeCallbacks(searchRunnable);
            searchRunnable = null;
        }
        if (searchContainer != null) {
            searchContainer.clearAnimation();
            searchContainer.setVisibility(View.GONE);
            searchContainer.setAlpha(0f);
        }
        if (contextualSearchEditText != null) {
            contextualSearchEditText.setText("");
        }
        hideSearchResults();
        hideSearchKeyboard();
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
        resetSearchUiImmediate();
    }

    private void updateSearchResultsUi(List<Collection> filteredCollections, List<Series> filteredSeries) {
        if (getContext() == null) {
            return;
        }

        if (sameSearchResults(filteredCollections, filteredSeries)) {
            return;
        }

        lastDisplayedCollections = new ArrayList<>(filteredCollections);
        lastDisplayedSeries = new ArrayList<>(filteredSeries);

        collectionsHeaderAdapter.setVisible(!filteredCollections.isEmpty());
        seriesHeaderAdapter.setVisible(!filteredSeries.isEmpty());
        collectionsSearchAdapter.setCollections(filteredCollections);
        seriesSearchAdapter.setSeriesList(filteredSeries);

        boolean noResults = filteredCollections.isEmpty() && filteredSeries.isEmpty();
        noSearchResultsText.setVisibility(noResults ? View.VISIBLE : View.GONE);
        searchResultsRecyclerView.setVisibility(noResults ? View.GONE : View.VISIBLE);
    }

    private boolean sameSearchResults(List<Collection> collections, List<Series> series) {
        if (collections.size() != lastDisplayedCollections.size()
                || series.size() != lastDisplayedSeries.size()) {
            return false;
        }
        for (int i = 0; i < collections.size(); i++) {
            if (collections.get(i).getId() != lastDisplayedCollections.get(i).getId()) {
                return false;
            }
        }
        for (int i = 0; i < series.size(); i++) {
            if (series.get(i).getId() != lastDisplayedSeries.get(i).getId()) {
                return false;
            }
        }
        return true;
    }

    private void showSearchResults() {
        searchResultsContainer.setVisibility(View.VISIBLE);
        viewPager.setVisibility(View.GONE);
        tabLayout.setVisibility(View.GONE);
    }

    private void hideSearchResults() {
        if (searchResultsContainer == null) {
            return;
        }
        searchResultsContainer.setVisibility(View.GONE);
        if (viewPager != null) {
            viewPager.setVisibility(View.VISIBLE);
        }
        if (tabLayout != null) {
            tabLayout.setVisibility(View.VISIBLE);
        }

        lastDisplayedCollections.clear();
        lastDisplayedSeries.clear();
        if (collectionsHeaderAdapter != null) {
            collectionsHeaderAdapter.setVisible(false);
        }
        if (seriesHeaderAdapter != null) {
            seriesHeaderAdapter.setVisible(false);
        }
        if (collectionsSearchAdapter != null) {
            collectionsSearchAdapter.setCollections(new ArrayList<>());
        }
        if (seriesSearchAdapter != null) {
            seriesSearchAdapter.setSeriesList(new ArrayList<>());
        }
        if (noSearchResultsText != null) {
            noSearchResultsText.setVisibility(View.GONE);
        }
        if (searchResultsRecyclerView != null) {
            searchResultsRecyclerView.setVisibility(View.VISIBLE);
        }
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
                    "Ошибка открытия настроек",
                    Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }

    private void openEditSeriesScreen(Series series) {
        saveSearchQueryFromUi();
        EditSeriesScreen editScreen = EditSeriesScreen.newInstance(series.getId());
        requireActivity().getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, editScreen)
                .addToBackStack(null)
                .commit();
    }

    private void openCollectionDetailScreen(Collection collection) {
        saveSearchQueryFromUi();
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