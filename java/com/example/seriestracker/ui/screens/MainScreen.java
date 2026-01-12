package com.example.seriestracker.ui.screens;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager2.widget.ViewPager2;

import com.example.seriestracker.R;
import com.example.seriestracker.ui.adapters.MainPagerAdapter;
import com.example.seriestracker.ui.viewmodels.SeriesViewModel;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

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
    private LinearLayout headerLayout;

    private boolean isButtonsVisible = false;

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
        headerLayout = view.findViewById(R.id.headerLayout);

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
        // Клик по заголовку - показ/скрытие кнопок
        welcomeText.setOnClickListener(v -> {
            toggleButtons();
        });

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

        searchButton.setOnClickListener(v -> {
            hideButtons(); // Скрываем кнопки при открытии поиска
            openSearchScreen();
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
            }
        });
    }

    private void openSearchScreen() {
        SearchScreen searchScreen = new SearchScreen();
        requireActivity().getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, searchScreen)
                .addToBackStack(null)
                .commit();
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
}