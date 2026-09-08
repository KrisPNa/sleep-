package com.example.seriestracker.ui.screens;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.PopupMenu;


import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.seriestracker.R;
import com.example.seriestracker.data.entities.Series;
import com.example.seriestracker.ui.adapters.SeriesAdapter;
import com.example.seriestracker.ui.utils.RecyclerViewPerf;
import com.example.seriestracker.ui.utils.ScrollToTopHelper;
import com.example.seriestracker.ui.viewmodels.SeriesViewModel;


import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class AllSeriesScreen extends Fragment {

    private Fragment parentFragment;
    private SeriesViewModel viewModel;
    private RecyclerView seriesRecyclerView;
    private SeriesAdapter seriesAdapter;
    private ImageButton backButton;
    private TextView seriesCountBadge;
    private List<Series> allSeries = new ArrayList<>();
    private int currentSortOrder = 0;
    private String statusFilter = null;

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
        seriesCountBadge = view.findViewById(R.id.seriesCountBadge);
        ImageButton sortButton = view.findViewById(R.id.seriesSortButton);

        // Обработчик кнопки назад
        if (backButton != null) {
            backButton.setOnClickListener(v -> {
                requireActivity().getSupportFragmentManager().popBackStack();
            });
        }

        // Настройка кнопки сортировки
        sortButton.setOnClickListener(v -> showSortMenu(v));
        // Настройка RecyclerView
        setupRecyclerView();

        // Загрузка всех сериалов
        viewModel.getAllSeries().observe(getViewLifecycleOwner(), seriesList -> {
            if (seriesList != null) {
                allSeries = new ArrayList<>(seriesList);
                applySeriesListToUi();
            } else {
                allSeries = new ArrayList<>();
                seriesAdapter.setSeriesList(allSeries);
                seriesCountBadge.setText("0");
            }
        });
    }

    private void applySeriesListToUi() {
        List<Series> displayedSeries = prepareDisplayedSeries(allSeries);
        seriesAdapter.setSeriesList(displayedSeries);
        seriesCountBadge.setText(String.valueOf(displayedSeries.size()));
    }

    private List<Series> prepareDisplayedSeries(List<Series> source) {
        List<Series> series = new ArrayList<>(source);
        if (statusFilter != null) {
            List<Series> filtered = new ArrayList<>();
            for (Series item : series) {
                if (statusFilter.equals(item.getStatus())) {
                    filtered.add(item);
                }
            }
            series = filtered;
        }
        return getSortedSeries(series);
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
        seriesRecyclerView.setHasFixedSize(true);
        RecyclerViewPerf.tune(seriesRecyclerView, 20);

        seriesAdapter.setInCollectionContext(false);
        seriesAdapter.setOnSeriesMenuListener(new SeriesAdapter.OnSeriesMenuListener() {
            @Override
            public void onChangeStatus(Series series, String newStatus) {
                viewModel.updateSeriesStatus(series.getId(), newStatus);
                Toast.makeText(getContext(), "Статус обновлён", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onDeleteAction(Series series) {
                new AlertDialog.Builder(requireContext())
                        .setTitle("Удалить сериал?")
                        .setMessage("«" + series.getTitle() + "» будет удалён без возможности восстановления.")
                        .setPositiveButton("Удалить", (d, w) -> {
                            viewModel.deleteSeries(series.getId());
                            Toast.makeText(getContext(), "Сериал удалён", Toast.LENGTH_SHORT).show();
                        })
                        .setNegativeButton("Отмена", null)
                        .show();
            }
        });

        ImageButton scrollToTopButton = requireView().findViewById(R.id.scrollToTopButton);
        ScrollToTopHelper.setup(seriesRecyclerView, scrollToTopButton);
    }

    private void showSortMenu(View view) {
        PopupMenu popup = new PopupMenu(getContext(), view);
        popup.getMenuInflater().inflate(R.menu.sort_series_menu, popup.getMenu());

        // Отметим текущий пункт сортировки как выбранный
        Menu menu = popup.getMenu();
        switch (currentSortOrder) {
            case 0: // по имени А-Я
                menu.findItem(R.id.sort_by_name_asc).setChecked(true);
                break;
            case 1: // по имени Я-А
                menu.findItem(R.id.sort_by_name_desc).setChecked(true);
                break;
            case 2: // по количеству серий (возр.)
                menu.findItem(R.id.sort_by_episodes_asc).setChecked(true);
                break;
            case 3: // по количеству серий (убыв.)
                menu.findItem(R.id.sort_by_episodes_desc).setChecked(true);
                break;
            case 4: // по статусу "Смотрю"
                menu.findItem(R.id.sort_by_status_watching).setChecked(true);
                break;
            case 5: // по статусу "Брошено"
                menu.findItem(R.id.sort_by_status_dropped).setChecked(true);
                break;
            case 6: // по статусу "Планирую"
                menu.findItem(R.id.sort_by_status_planned).setChecked(true);
                break;
            case 7: // по статусу "Посмотрел"
                menu.findItem(R.id.sort_by_status_completed).setChecked(true);
                break;
        }

        popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                int itemId = item.getItemId();

                if (itemId == R.id.sort_by_name_asc) {
                    currentSortOrder = 0;
                    item.setChecked(true);
                    statusFilter = null;
                } else if (itemId == R.id.sort_by_name_desc) {
                    currentSortOrder = 1;
                    item.setChecked(true);
                    statusFilter = null;
                } else if (itemId == R.id.sort_by_episodes_asc) {
                    currentSortOrder = 2;
                    item.setChecked(true);
                    statusFilter = null;
                } else if (itemId == R.id.sort_by_episodes_desc) {
                    currentSortOrder = 3;
                    item.setChecked(true);
                    statusFilter = null;
                } else if (itemId == R.id.sort_by_status_watching) {
                    currentSortOrder = 4;
                    item.setChecked(true);
                    statusFilter = "watching";
                } else if (itemId == R.id.sort_by_status_dropped) {
                    currentSortOrder = 5;
                    item.setChecked(true);
                    statusFilter = "dropped";
                } else if (itemId == R.id.sort_by_status_planned) {
                    currentSortOrder = 6;
                    item.setChecked(true);
                    statusFilter = "planned";
                } else if (itemId == R.id.sort_by_status_completed) {
                    currentSortOrder = 7;
                    item.setChecked(true);
                    statusFilter = "completed";
                } else {
                    return false;
                }

                // Обновляем список с новой сортировкой
                updateSeriesList();

                return true;
            }
        });

        popup.show();
    }

    private List<Series> getSortedSeries(List<Series> seriesList) {
        List<Series> sorted = new ArrayList<>(seriesList);

        switch (currentSortOrder) {
            case 0: // по имени А-Я
                Collections.sort(sorted, new Comparator<Series>() {
                    @Override
                    public int compare(Series s1, Series s2) {
                        // Сначала проверяем избранное
                        if (s1.getIsFavorite() != s2.getIsFavorite()) {
                            return s2.getIsFavorite() ? 1 : -1;
                        }
                        return s1.getTitle().compareToIgnoreCase(s2.getTitle());
                    }
                });
                break;
            case 1: // по имени Я-А
                Collections.sort(sorted, new Comparator<Series>() {
                    @Override
                    public int compare(Series s1, Series s2) {
                        // Сначала проверяем избранное
                        if (s1.getIsFavorite() != s2.getIsFavorite()) {
                            return s2.getIsFavorite() ? 1 : -1;
                        }
                        return s2.getTitle().compareToIgnoreCase(s1.getTitle());
                    }
                });
                break;
            case 2: // по количеству серий (возр.)
                Collections.sort(sorted, new Comparator<Series>() {
                    @Override
                    public int compare(Series s1, Series s2) {
                        // Сначала проверяем избранное
                        if (s1.getIsFavorite() != s2.getIsFavorite()) {
                            return s2.getIsFavorite() ? 1 : -1;
                        }
                        int episodes1 = s1.getEpisodes();
                        int episodes2 = s2.getEpisodes();
                        return Integer.compare(episodes1, episodes2);
                    }
                });
                break;
            case 3: // по количеству серий (убыв.)
                Collections.sort(sorted, new Comparator<Series>() {
                    @Override
                    public int compare(Series s1, Series s2) {
                        // Сначала проверяем избранное
                        if (s1.getIsFavorite() != s2.getIsFavorite()) {
                            return s2.getIsFavorite() ? 1 : -1;
                        }
                        int episodes1 = s1.getEpisodes();
                        int episodes2 = s2.getEpisodes();
                        return Integer.compare(episodes2, episodes1);
                    }
                });
                break;
            case 4: // только «Смотрю»
            case 5: // только «Брошено»
            case 6: // только «Запланировано»
            case 7: // только «Завершено»
                Collections.sort(sorted, new Comparator<Series>() {
                    @Override
                    public int compare(Series s1, Series s2) {
                        if (s1.getIsFavorite() != s2.getIsFavorite()) {
                            return s2.getIsFavorite() ? 1 : -1;
                        }
                        return s1.getTitle().compareToIgnoreCase(s2.getTitle());
                    }
                });
                break;
        }

        return sorted;
    }

    private void updateSeriesList() {
        applySeriesListToUi();
    }
    private void openEditSeriesScreen(Series series) {
        EditSeriesScreen editScreen = EditSeriesScreen.newInstance(series.getId());
        requireActivity().getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, editScreen)
                .addToBackStack(null)
                .commit();
    }
}