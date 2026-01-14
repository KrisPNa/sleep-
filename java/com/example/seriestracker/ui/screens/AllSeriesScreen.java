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
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.seriestracker.R;
import com.example.seriestracker.data.entities.Series;
import com.example.seriestracker.ui.adapters.SeriesAdapter;
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
                allSeries = seriesList;
                List<Series> sortedSeries = getSortedSeries(allSeries);
                // Обновляем адаптер
                seriesAdapter.setSeriesList(sortedSeries);
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

        // ВСЁ! Никаких addOnScrollListener больше не нужно
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
            case 4: // по статусу
                menu.findItem(R.id.sort_by_status).setChecked(true);
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
                } else if (itemId == R.id.sort_by_episodes_asc) {
                    currentSortOrder = 2;
                    item.setChecked(true);
                } else if (itemId == R.id.sort_by_episodes_desc) {
                    currentSortOrder = 3;
                    item.setChecked(true);
                } else if (itemId == R.id.sort_by_status) {
                    currentSortOrder = 4;
                    item.setChecked(true);
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
                        return s1.getTitle().compareToIgnoreCase(s2.getTitle());
                    }
                });
                break;
            case 1: // по имени Я-А
                Collections.sort(sorted, new Comparator<Series>() {
                    @Override
                    public int compare(Series s1, Series s2) {
                        return s2.getTitle().compareToIgnoreCase(s1.getTitle());
                    }
                });
                break;
            case 2: // по количеству серий (возр.)
                Collections.sort(sorted, new Comparator<Series>() {
                    @Override
                    public int compare(Series s1, Series s2) {
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
                        int episodes1 = s1.getEpisodes();
                        int episodes2 = s2.getEpisodes();
                        return Integer.compare(episodes2, episodes1);
                    }
                });
                break;
            case 4: // по статусу
                Collections.sort(sorted, new Comparator<Series>() {
                    @Override
                    public int compare(Series s1, Series s2) {
                        // Сначала сортируем по статусу
                        int statusComparison = getStatusPriority(s1.getStatus()).compareTo(getStatusPriority(s2.getStatus()));
                        if (statusComparison != 0) {
                            return statusComparison;
                        }
                        // Если статус одинаковый, сортируем по названию
                        return s1.getTitle().compareToIgnoreCase(s2.getTitle());
                    }

                    private Integer getStatusPriority(String status) {
                        switch (status) {
                            case "watching":
                                return 1;
                            case "planned":
                                return 2;
                            case "dropped":
                                return 3;
                            case "completed":
                                return 4;
                            default:
                                return 5; // для любых других значений
                        }
                    }
                });
                break;
        }

        return sorted;
    }

    private void updateSeriesList() {
        List<Series> sortedSeries = getSortedSeries(allSeries);
        seriesAdapter.setSeriesList(sortedSeries);
    }
    private void openEditSeriesScreen(Series series) {
        EditSeriesScreen editScreen = EditSeriesScreen.newInstance(series.getId());
        requireActivity().getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, editScreen)
                .addToBackStack(null)
                .commit();
    }
}