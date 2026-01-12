package com.example.seriestracker.ui.adapters;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.example.seriestracker.ui.screens.AllSeriesScreen;
import com.example.seriestracker.ui.screens.CollectionsListFragment; // Этот фрагмент мы создадим позже

public class MainPagerAdapter extends FragmentStateAdapter {

    public static final int COLLECTIONS_FRAGMENT_POSITION = 0;
    public static final int SERIES_FRAGMENT_POSITION = 1;

    private Fragment mainFragment;

    public MainPagerAdapter(@NonNull FragmentActivity fragmentActivity) {
        super(fragmentActivity);
    }
    public MainPagerAdapter(@NonNull Fragment fragment) {
        super(fragment);
        this.mainFragment = fragment;
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        switch (position) {
            case COLLECTIONS_FRAGMENT_POSITION:
                CollectionsListFragment collectionsFragment = new CollectionsListFragment();
                if (mainFragment != null) {
                    collectionsFragment.setParentFragment(mainFragment);
                }
                return collectionsFragment;
            case SERIES_FRAGMENT_POSITION:
                AllSeriesScreen seriesFragment = new AllSeriesScreen();
                if (mainFragment != null) {
                    seriesFragment.setParentFragment(mainFragment);
                }
                return seriesFragment;
            default:
                CollectionsListFragment defaultFragment = new CollectionsListFragment();
                if (mainFragment != null) {
                    defaultFragment.setParentFragment(mainFragment);
                }
                return defaultFragment;
        }
    }

    @Override
    public int getItemCount() {
        return 2; // Коллекции и сериалы
    }

    public CharSequence getPageTitle(int position) {
        switch (position) {
            case COLLECTIONS_FRAGMENT_POSITION:
                return "Мои коллекции";
            case SERIES_FRAGMENT_POSITION:
                return "Мои сериалы";
            default:
                return "";
        }
    }
}