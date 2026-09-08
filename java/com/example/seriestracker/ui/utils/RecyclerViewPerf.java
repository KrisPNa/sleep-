package com.example.seriestracker.ui.utils;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SimpleItemAnimator;

/** Общие настройки RecyclerView против дёрганья и лишней работы. */
public final class RecyclerViewPerf {

    private RecyclerViewPerf() {
    }

    public static void tune(@NonNull RecyclerView recyclerView) {
        tune(recyclerView, 12);
    }

    public static void tune(@NonNull RecyclerView recyclerView, int cacheSize) {
        recyclerView.setItemViewCacheSize(Math.max(4, cacheSize));
        RecyclerView.ItemAnimator animator = recyclerView.getItemAnimator();
        if (animator instanceof SimpleItemAnimator) {
            ((SimpleItemAnimator) animator).setSupportsChangeAnimations(false);
        }
    }
}
