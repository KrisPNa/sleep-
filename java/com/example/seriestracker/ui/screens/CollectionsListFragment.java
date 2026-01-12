package com.example.seriestracker.ui.screens;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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

public class CollectionsListFragment extends Fragment {

    private Fragment parentFragment;
    private SeriesViewModel viewModel;
    private RecyclerView collectionsRecyclerView;
    private CollectionAdapter collectionAdapter;
    private TextView collectionsCount;
    private TextView noCollectionsText;
    private TextView collectionsTitle;
    private List<Collection> allCollections = new ArrayList<>();

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

        // Установим заголовок
        collectionsTitle.setText("Мои коллекции");
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
        viewModel.getCollectionsWithSeries().observe(getViewLifecycleOwner(), collectionWithSeriesList -> {
            if (collectionWithSeriesList != null && !collectionWithSeriesList.isEmpty()) {
                List<Collection> collections = new ArrayList<>();
                for (CollectionWithSeries cws : collectionWithSeriesList) {
                    // Используем геттер вместо прямого доступа к полю
                    Collection collection = cws.getCollection();

                    // Получаем количество сериалов через ViewModel
                    viewModel.getSeriesCountInCollection(collection.getId()).observe(
                            getViewLifecycleOwner(), count -> {
                                if (count != null) {
                                    collection.setSeriesCount(count);
                                    collectionAdapter.notifyDataSetChanged();
                                }
                            });

                    collections.add(collection);
                }
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
}