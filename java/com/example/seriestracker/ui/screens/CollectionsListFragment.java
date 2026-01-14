package com.example.seriestracker.ui.screens;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.PopupMenu;
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
import com.example.seriestracker.ui.adapters.CollectionAdapter;
import com.example.seriestracker.ui.viewmodels.SeriesViewModel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class CollectionsListFragment extends Fragment {

    private Fragment parentFragment;
    private SeriesViewModel viewModel;
    private RecyclerView collectionsRecyclerView;
    private CollectionAdapter collectionAdapter;
    private TextView collectionsCount;
    private TextView noCollectionsText;
    private TextView collectionsTitle;
    private List<Collection> allCollections = new ArrayList<>();
    private int currentSortOrder = 0;

    public CollectionsListFragment() {
        // Required empty public constructor
    }

    public void setParentFragment(Fragment parentFragment) {
        this.parentFragment = parentFragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_collections_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(requireActivity()).get(SeriesViewModel.class);

        initViews(view);
        setupRecyclerView();
        observeData();
    }

    private void initViews(View view) {
        collectionsRecyclerView = view.findViewById(R.id.collectionsRecyclerView);
        collectionsCount = view.findViewById(R.id.collectionsCount);
        noCollectionsText = view.findViewById(R.id.noCollectionsText);
        collectionsTitle = view.findViewById(R.id.collectionsTitle);
        ImageButton sortButton = view.findViewById(R.id.collectionsSortButton);

        // Установим заголовок
        collectionsTitle.setText("Мои коллекции");
        sortButton.setOnClickListener(v -> showSortMenu(v));

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

            @Override
            public void onFavoriteClick(Collection collection) {
                collection.setFavorite(!collection.isFavorite());
                viewModel.updateCollection(collection);

                Toast.makeText(getContext(),
                        collection.isFavorite() ? "Добавлено в избранное" : "Убрано из избранного",
                        Toast.LENGTH_SHORT).show();
            }
        });

        GridLayoutManager layoutManager = new GridLayoutManager(getContext(), 2);
        layoutManager.setOrientation(RecyclerView.VERTICAL);
        collectionsRecyclerView.setLayoutManager(layoutManager);
        collectionsRecyclerView.setAdapter(collectionAdapter);

        // ВСЁ! Никаких addOnScrollListener больше не нужно
    }

    private void observeData() {
        viewModel.getAllCollectionsWithSeriesCount().observe(getViewLifecycleOwner(), collections -> {
            if (collections != null && !collections.isEmpty()) {
                allCollections = collections;

                List<Collection> sortedCollections = getSortedCollections(allCollections);
                collectionAdapter.setCollections(sortedCollections);

                noCollectionsText.setVisibility(View.GONE);
                collectionsRecyclerView.setVisibility(View.VISIBLE);
            } else {
                noCollectionsText.setVisibility(View.VISIBLE);
                collectionsRecyclerView.setVisibility(View.GONE);
            }
        });

        viewModel.getAllCollections().observe(getViewLifecycleOwner(), allColls -> {
            if (allColls != null) {
                collectionsCount.setText(String.valueOf(allColls.size()));
            }
        });
    }

    private void showSortMenu(View view) {
        PopupMenu popup = new PopupMenu(getContext(), view);
        popup.getMenuInflater().inflate(R.menu.sort_collections_menu, popup.getMenu());

        // Отметим текущий пункт сортировки как выбранный
        Menu menu = popup.getMenu();
        switch (currentSortOrder) {
            case 0: // по имени А-Я
                menu.findItem(R.id.sort_by_name_asc).setChecked(true);
                break;
            case 1: // по имени Я-А
                menu.findItem(R.id.sort_by_name_desc).setChecked(true);
                break;
            case 2: // по количеству сериалов (возр.)
                menu.findItem(R.id.sort_by_count_asc).setChecked(true);
                break;
            case 3: // по количеству сериалов (убыв.)
                menu.findItem(R.id.sort_by_count_desc).setChecked(true);
                break;
        }

        popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                int itemId = item.getItemId();

                if (itemId == R.id.sort_by_name_asc) {
                    currentSortOrder = 0;
                    item.setChecked(true);
                } else if (itemId == R.id.sort_by_name_desc) {
                    currentSortOrder = 1;
                    item.setChecked(true);
                } else if (itemId == R.id.sort_by_count_asc) {
                    currentSortOrder = 2;
                    item.setChecked(true);
                } else if (itemId == R.id.sort_by_count_desc) {
                    currentSortOrder = 3;
                    item.setChecked(true);
                } else {
                    return false;
                }

                // Обновляем список с новой сортировкой
                updateCollectionList();

                return true;
            }
        });

        popup.show();
    }
    private List<Collection> getSortedCollections(List<Collection> collections) {
        List<Collection> sorted = new ArrayList<>(collections);

        switch (currentSortOrder) {
            case 0: // по имени А-Я
                Collections.sort(sorted, new Comparator<Collection>() {
                    @Override
                    public int compare(Collection c1, Collection c2) {
                        if (c1.isFavorite() != c2.isFavorite()) {
                            return c2.isFavorite() ? 1 : -1;
                        }
                        return c1.getName().compareToIgnoreCase(c2.getName());
                    }
                });
                break;
            case 1: // по имени Я-А
                Collections.sort(sorted, new Comparator<Collection>() {
                    @Override
                    public int compare(Collection c1, Collection c2) {
                        if (c1.isFavorite() != c2.isFavorite()) {
                            return c2.isFavorite() ? 1 : -1;
                        }
                        return c2.getName().compareToIgnoreCase(c1.getName());
                    }
                });
                break;
            case 2: // по количеству сериалов (возр.)
                Collections.sort(sorted, new Comparator<Collection>() {
                    @Override
                    public int compare(Collection c1, Collection c2) {
                        if (c1.isFavorite() != c2.isFavorite()) {
                            return c2.isFavorite() ? 1 : -1;
                        }
                        // Сравниваем по количеству сериалов (c1.seriesCount - c2.seriesCount)
                        int count1 = c1.getSeriesCount(); // Просто получаем значение
                        int count2 = c2.getSeriesCount(); // Просто получаем значение
                        return Integer.compare(count1, count2);
                    }
                });
                break;
            case 3: // по количеству сериалов (убыв.)
                Collections.sort(sorted, new Comparator<Collection>() {
                    @Override
                    public int compare(Collection c1, Collection c2) {
                        if (c1.isFavorite() != c2.isFavorite()) {
                            return c2.isFavorite() ? 1 : -1;
                        }
                        // Сравниваем по количеству сериалов (c2.seriesCount - c1.seriesCount)
                        int count1 = c1.getSeriesCount(); // Просто получаем значение
                        int count2 = c2.getSeriesCount(); // Просто получаем значение
                        return Integer.compare(count2, count1);
                    }
                });
                break;
        }

        return sorted;
    }

    private void updateCollectionList() {
        List<Collection> sortedCollections = getSortedCollections(allCollections);
        collectionAdapter.setCollections(sortedCollections);
    }
}