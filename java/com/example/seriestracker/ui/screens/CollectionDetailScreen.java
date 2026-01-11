package com.example.seriestracker.ui.screens;

import android.graphics.Color;
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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.seriestracker.R;
import com.example.seriestracker.data.entities.Collection;
import com.example.seriestracker.data.entities.Series;
import com.example.seriestracker.ui.adapters.SeriesAdapter;
import com.example.seriestracker.ui.viewmodels.SeriesViewModel;

import java.util.List;

public class CollectionDetailScreen extends Fragment {

    private SeriesViewModel viewModel;
    private long collectionId;

    private TextView collectionNameTextView;
    private TextView seriesCountTextView;
    private TextView seriesCountBadge;
    private RecyclerView seriesRecyclerView;
    private SeriesAdapter seriesAdapter;
    private ImageButton backButton;
    private View colorIndicator;

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
        seriesCountTextView = view.findViewById(R.id.seriesCountTextView);
        seriesCountBadge = view.findViewById(R.id.seriesCountBadge);
        seriesRecyclerView = view.findViewById(R.id.seriesRecyclerView);
        backButton = view.findViewById(R.id.backButton);
        colorIndicator = view.findViewById(R.id.colorIndicator);

        // Обработчик кнопки назад
        if (backButton != null) {
            backButton.setOnClickListener(v -> {
                requireActivity().getSupportFragmentManager().popBackStack();
            });
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
                        isFavorite ? "Добавлено в избранное" : "Убрано из избранного",
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

                // Обновляем количество в большом счетчике
                int count = seriesList.size();
                seriesCountTextView.setText(String.valueOf(count));

                // Обновляем количество в бейдже
                seriesCountBadge.setText(String.valueOf(count));
            }
        });

        // Получаем данные коллекции
        viewModel.getAllCollections().observe(getViewLifecycleOwner(), collections -> {
            if (collections != null) {
                for (Collection collection : collections) {
                    if (collection.getId() == collectionId) {
                        collectionNameTextView.setText(collection.getName());

                        // Устанавливаем цвет коллекции
                        String color = collection.getColor();
                        if (color != null && !color.isEmpty()) {
                            try {
                                // Устанавливаем цвет индикатора
                                colorIndicator.setBackgroundColor(Color.parseColor(color));
                                colorIndicator.setVisibility(View.VISIBLE);

                                // Меняем цвет заголовка на цвет коллекции
                                collectionNameTextView.setTextColor(Color.parseColor(color));

                                // Меняем цвет счетчика на цвет коллекции
                                seriesCountTextView.setTextColor(Color.parseColor(color));
                            } catch (Exception e) {
                                // Если цвет некорректный, используем цвет по умолчанию
                                setDefaultColors();
                            }
                        } else {
                            // Если цвет не установлен, используем цвет по умолчанию
                            setDefaultColors();
                        }
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
        seriesCountTextView.setTextColor(getResources().getColor(R.color.primary_blue));
    }

    private void openEditSeriesScreen(Series series) {
        EditSeriesScreen editScreen = EditSeriesScreen.newInstance(series.getId());
        requireActivity().getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, editScreen)
                .addToBackStack(null)
                .commit();
    }
}