package com.example.seriestracker.ui.utils;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

public final class ScrollToTopHelper {

    private ScrollToTopHelper() {
    }

    public static void setup(@NonNull RecyclerView recyclerView, @NonNull View scrollToTopButton) {
        scrollToTopButton.setVisibility(View.GONE);
        scrollToTopButton.setOnClickListener(v -> recyclerView.scrollToPosition(0));

        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                updateVisibility(recyclerView, scrollToTopButton);
            }
        });
    }

    private static void updateVisibility(RecyclerView recyclerView, View scrollToTopButton) {
        boolean show = recyclerView.canScrollVertically(-1);
        scrollToTopButton.setVisibility(show ? View.VISIBLE : View.GONE);
    }
}
