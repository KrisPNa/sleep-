package com.example.seriestracker.ui.screens;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.seriestracker.R;
import com.example.seriestracker.data.entities.Collection;
import com.example.seriestracker.data.entities.CollectionWithSeries;
import com.example.seriestracker.ui.adapters.CollectionAdapter;
import com.example.seriestracker.ui.viewmodels.SeriesViewModel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class MainScreen extends Fragment {

    private SeriesViewModel viewModel;
    private Button createCollectionButton;
    private Button addSeriesButton;
    private Button allSeriesButton;
    private Button allCollectionButton;
    private Button backupSettingsButton; // НОВАЯ КНОПКА
    private ImageButton searchButton;
    private RecyclerView collectionsRecyclerView;
    private CollectionAdapter collectionAdapter;
    private TextView collectionsCount;
    private TextView noCollectionsText;
    private TextView welcomeText;

    private List<Collection> allCollections = new ArrayList<>();

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
        setupRecyclerView();
        setupEventListeners();
        observeData();
    }

    private void initViews(View view) {
        createCollectionButton = view.findViewById(R.id.createCollectionButton);
        addSeriesButton = view.findViewById(R.id.addSeriesButton);
        allSeriesButton = view.findViewById(R.id.allSeriesButton);
        allCollectionButton = view.findViewById(R.id.allCollection);
        backupSettingsButton = view.findViewById(R.id.backupSettingsButton); // Инициализация
        searchButton = view.findViewById(R.id.searchButton);
        collectionsRecyclerView = view.findViewById(R.id.collectionsRecyclerView);
        collectionsCount = view.findViewById(R.id.collectionsCount);
        noCollectionsText = view.findViewById(R.id.noCollectionsText);
        welcomeText = view.findViewById(R.id.welcomeText);
    }

    private void setupRecyclerView() {
        collectionAdapter = new CollectionAdapter(new CollectionAdapter.OnCollectionClickListener() {
            @Override
            public void onCollectionClick(Collection collection) {
                CollectionDetailScreen detailScreen = new CollectionDetailScreen();
                Bundle bundle = new Bundle();
                bundle.putLong("collectionId", collection.getId());
                detailScreen.setArguments(bundle);

                requireActivity().getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, detailScreen)
                        .addToBackStack(null)
                        .commit();
            }
        }, viewModel, getViewLifecycleOwner());

        GridLayoutManager layoutManager = new GridLayoutManager(getContext(), 2);
        layoutManager.setOrientation(RecyclerView.VERTICAL);
        collectionsRecyclerView.setLayoutManager(layoutManager);
        collectionsRecyclerView.setAdapter(collectionAdapter);
    }

    private void setupEventListeners() {
        createCollectionButton.setOnClickListener(v -> {
            CreateCollectionScreen createCollectionScreen = new CreateCollectionScreen();
            requireActivity().getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, createCollectionScreen)
                    .addToBackStack(null)
                    .commit();
        });

        addSeriesButton.setOnClickListener(v -> {
            AddSeriesScreen addSeriesScreen = new AddSeriesScreen();
            requireActivity().getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, addSeriesScreen)
                    .addToBackStack(null)
                    .commit();
        });

        allSeriesButton.setOnClickListener(v -> {
            AllSeriesScreen allSeriesScreen = new AllSeriesScreen();
            requireActivity().getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, allSeriesScreen)
                    .addToBackStack(null)
                    .commit();
        });

        allCollectionButton.setOnClickListener(v -> {
            openManageCollectionsScreen();
        });

        // Кнопка поиска
        searchButton.setOnClickListener(v -> {
            openSearchScreen();
        });

        // НОВАЯ КНОПКА - Резервное копирование
        backupSettingsButton.setOnClickListener(v -> {
            openBackupSettingsScreen();
        });
    }

    private void observeData() {
        viewModel.getCollectionsWithSeries().observe(getViewLifecycleOwner(), collectionWithSeries -> {
            if (collectionWithSeries != null && !collectionWithSeries.isEmpty()) {
                List<Collection> collections = new ArrayList<>();
                for (CollectionWithSeries cws : collectionWithSeries) {
                    collections.add(cws.getCollection());
                }
                allCollections = collections;

                // Сортируем коллекции
                List<Collection> sortedCollections = getSortedCollections(allCollections);
                collectionAdapter.setCollections(sortedCollections);

                noCollectionsText.setVisibility(View.GONE);
                collectionsRecyclerView.setVisibility(View.VISIBLE);
            } else {
                noCollectionsText.setVisibility(View.VISIBLE);
                collectionsRecyclerView.setVisibility(View.GONE);
            }
        });

        viewModel.getAllCollections().observe(getViewLifecycleOwner(), collections -> {
            if (collections != null) {
                collectionsCount.setText(String.valueOf(collections.size()));
            }
        });
    }

    private List<Collection> getSortedCollections(List<Collection> collections) {
        List<Collection> sorted = new ArrayList<>(collections);
        Collections.sort(sorted, new Comparator<Collection>() {
            @Override
            public int compare(Collection c1, Collection c2) {
                if (c1.isFavorite() != c2.isFavorite()) {
                    return c2.isFavorite() ? 1 : -1;
                }
                return c1.getName().compareToIgnoreCase(c2.getName());
            }
        });
        return sorted;
    }

    private void openManageCollectionsScreen() {
        try {
            ManageCollectionsScreen manageCollectionsScreen = new ManageCollectionsScreen();
            requireActivity().getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, manageCollectionsScreen)
                    .addToBackStack("manage_collections")
                    .commit();
        } catch (Exception e) {
            Toast.makeText(getContext(),
                    "Экран управления коллекциями еще не реализован",
                    Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }

    private void openSearchScreen() {
        SearchScreen searchScreen = new SearchScreen();
        requireActivity().getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, searchScreen)
                .addToBackStack(null)
                .commit();
    }

    // В MainScreen.java добавьте:

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


}