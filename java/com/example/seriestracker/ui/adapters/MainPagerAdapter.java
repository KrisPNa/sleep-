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

    public MainPagerAdapter(@NonNull FragmentActivity fragmentActivity) {
        super(fragmentActivity);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        switch (position) {
            case COLLECTIONS_FRAGMENT_POSITION:
                return new CollectionsListFragment();
            case SERIES_FRAGMENT_POSITION:
                return new AllSeriesScreen();
            default:
                return new CollectionsListFragment();
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