package com.example.seriestracker.ui.screens;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView; // <-- ДОБАВИТЬ этот импорт
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.seriestracker.R;
import com.example.seriestracker.data.entities.Series;
import com.example.seriestracker.ui.adapters.SeriesAdapter;
import com.example.seriestracker.ui.viewmodels.SeriesViewModel;

import java.util.List;

public class AllSeriesScreen extends Fragment {

    private Fragment parentFragment;
    private SeriesViewModel viewModel;
    private RecyclerView seriesRecyclerView;
    private SeriesAdapter seriesAdapter;
    private ImageButton backButton;
    private TextView seriesCountBadge; // <-- ДОБАВИТЬ эту переменную

    public AllSeriesScreen() {
        // Required empty public constructor
    }

    public void setParentFragment(Fragment parentFragment) {
        this.parentFragment = parentFragment;
    }
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_all_series, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(requireActivity()).get(SeriesViewModel.class);

        seriesRecyclerView = view.findViewById(R.id.allSeriesRecyclerView);
        backButton = view.findViewById(R.id.backButton);
        seriesCountBadge = view.findViewById(R.id.seriesCountBadge); // <-- ИНИЦИАЛИЗИРОВАТЬ здесь

        // Обработчик кнопки назад
        if (backButton != null) {
            backButton.setOnClickListener(v -> {
                requireActivity().getSupportFragmentManager().popBackStack();
            });
        }

        // Настройка RecyclerView
        setupRecyclerView();

        // Загрузка всех сериалов
        viewModel.getAllSeries().observe(getViewLifecycleOwner(), seriesList -> {
            if (seriesList != null && !seriesList.isEmpty()) {
                // Обновляем адаптер
                seriesAdapter.setSeriesList(seriesList);
                // ОБНОВЛЯЕМ СЧЕТЧИК
                seriesCountBadge.setText(String.valueOf(seriesList.size()));
            } else {
                // Если список пустой, показываем 0
                seriesCountBadge.setText("0");
            }
        });
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

        // ПЕРЕМЕЩЕННЫЙ КОД: Добавляем слушатель прокрутки для скрытия/показа кнопок
        seriesRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            private int scrollThreshold = 20; // Минимальное расстояние прокрутки для срабатывания

            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);

                if (parentFragment instanceof MainScreen) {
                    MainScreen mainScreen = (MainScreen) parentFragment;
                    // Управляем поведением карточки с кнопками
                    if (Math.abs(dy) > scrollThreshold) { // Проверяем, достаточно ли велика прокрутка
                        if (dy > 0) {
                            // Прокрутка вниз - скрываем кнопки
                            mainScreen.hideButtons();
                        } else if (dy < 0) {
                            // Прокрутка вверх - показываем кнопки
                            mainScreen.showButtons();
                        }
                    }
                }
            }
        });
    }


    private void openEditSeriesScreen(Series series) {
        EditSeriesScreen editScreen = EditSeriesScreen.newInstance(series.getId());
        requireActivity().getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, editScreen)
                .addToBackStack(null)
                .commit();
    }
}