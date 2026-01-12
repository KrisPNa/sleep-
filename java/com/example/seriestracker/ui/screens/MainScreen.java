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

    private void initViews(View view) {
        createCollectionButton = view.findViewById(R.id.createCollectionButton);
        addSeriesButton = view.findViewById(R.id.addSeriesButton);
        backupSettingsButton = view.findViewById(R.id.backupSettingsButton);
        searchButton = view.findViewById(R.id.searchButton);
        welcomeText = view.findViewById(R.id.welcomeText);
        viewPager = view.findViewById(R.id.viewPager);
        tabLayout = view.findViewById(R.id.tabLayout);
    }

    private void setupViewPagerAndTabs() {
        // Initialize the adapter
        MainPagerAdapter adapter = new MainPagerAdapter(requireActivity());
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

        searchButton.setOnClickListener(v -> {
            openSearchScreen();
        });

        backupSettingsButton.setOnClickListener(v -> {
            openBackupSettingsScreen();
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