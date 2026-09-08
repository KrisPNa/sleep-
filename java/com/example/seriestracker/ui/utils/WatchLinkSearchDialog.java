package com.example.seriestracker.ui.utils;

import android.app.Dialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.seriestracker.R;
import com.example.seriestracker.data.watchlinks.WatchLinkCandidate;
import com.example.seriestracker.data.watchlinks.WatchLinkSearchOptions;
import com.example.seriestracker.data.watchlinks.WatchLinkSearchService;
import com.example.seriestracker.ui.adapters.WatchLinkCandidatesAdapter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class WatchLinkSearchDialog {

    public interface SelectionListener {
        void onLinksSelected(@NonNull List<String> urls);
    }

    private WatchLinkSearchDialog() {
    }

    public static void show(@NonNull Fragment fragment,
                            @Nullable String seriesTitle,
                            @NonNull SelectionListener listener) {
        if (!fragment.isAdded() || fragment.getContext() == null) {
            return;
        }

        String query = seriesTitle != null ? seriesTitle.trim() : "";
        if (query.isEmpty()) {
            Toast.makeText(fragment.requireContext(),
                    R.string.search_watch_links_empty_title,
                    Toast.LENGTH_SHORT).show();
            return;
        }

        Dialog dialog = new Dialog(fragment.requireContext());
        dialog.setContentView(R.layout.dialog_select_watch_links);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT);
        }

        TextView subtitleView = dialog.findViewById(R.id.watchLinkDialogSubtitle);
        RecyclerView recyclerView = dialog.findViewById(R.id.watchLinkRecyclerView);
        ProgressBar progressBar = dialog.findViewById(R.id.watchLinkProgressBar);
        TextView emptyText = dialog.findViewById(R.id.watchLinkEmptyText);
        Button cancelButton = dialog.findViewById(R.id.watchLinkCancelButton);
        Button findMoreButton = dialog.findViewById(R.id.watchLinkFindMoreButton);
        Button confirmButton = dialog.findViewById(R.id.watchLinkConfirmButton);

        subtitleView.setText(fragment.getString(R.string.search_watch_links_for, query));

        WatchLinkCandidatesAdapter adapter = new WatchLinkCandidatesAdapter();
        recyclerView.setLayoutManager(new LinearLayoutManager(fragment.requireContext()));
        recyclerView.setAdapter(adapter);

        Runnable updateConfirmButton = () -> {
            int count = adapter.getSelectedCount();
            confirmButton.setEnabled(count > 0);
            if (count > 0) {
                confirmButton.setText(fragment.getString(
                        R.string.confirm_watch_links_with_count, count));
            } else {
                confirmButton.setText(R.string.confirm_watch_links_none);
            }
        };

        adapter.setListener(new WatchLinkCandidatesAdapter.Listener() {
            @Override
            public void onSelectionChanged(int selectedCount) {
                updateConfirmButton.run();
            }

            @Override
            public void onOpenLink(@NonNull WatchLinkCandidate candidate) {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(candidate.getUrl()));
                try {
                    fragment.startActivity(intent);
                } catch (Exception e) {
                    Toast.makeText(fragment.requireContext(),
                            R.string.search_watch_links_error,
                            Toast.LENGTH_SHORT).show();
                }
            }
        });
        updateConfirmButton.run();

        AtomicBoolean dismissed = new AtomicBoolean(false);
        AtomicBoolean searching = new AtomicBoolean(false);

        ExecutorService executor = Executors.newSingleThreadExecutor();
        Handler mainHandler = new Handler(Looper.getMainLooper());
        WatchLinkSearchService searchService =
                WatchLinkSearchService.createFromSettings(fragment.requireContext());

        dialog.setOnDismissListener(d -> {
            dismissed.set(true);
            executor.shutdownNow();
        });

        WatchLinkSearchService.ProgressListener progressListener =
                new WatchLinkSearchService.ProgressListener() {
                    @Override
                    public void onPartialResults(@NonNull List<WatchLinkCandidate> resultsSoFar) {
                        mainHandler.post(() -> {
                            if (!dialog.isShowing() || dismissed.get()) {
                                return;
                            }
                            adapter.addNewItems(new ArrayList<>(resultsSoFar));
                            if (adapter.getItemCount() > 0) {
                                emptyText.setVisibility(View.GONE);
                                progressBar.setVisibility(View.VISIBLE);
                            }
                            updateConfirmButton.run();
                        });
                    }

                    @Override
                    public void onComplete(@NonNull List<WatchLinkCandidate> finalResults) {
                        mainHandler.post(() -> {
                            if (!dialog.isShowing() || dismissed.get()) {
                                return;
                            }
                            searching.set(false);
                            adapter.addNewItems(new ArrayList<>(finalResults));
                            progressBar.setVisibility(View.GONE);
                            findMoreButton.setVisibility(View.VISIBLE);
                            findMoreButton.setEnabled(true);
                            if (adapter.getItemCount() == 0) {
                                emptyText.setText(R.string.search_watch_links_not_found);
                                emptyText.setVisibility(View.VISIBLE);
                            } else {
                                emptyText.setVisibility(View.GONE);
                            }
                            updateConfirmButton.run();
                        });
                    }

                    @Override
                    public void onError(@NonNull String message) {
                        mainHandler.post(() -> {
                            if (!dialog.isShowing() || dismissed.get()) {
                                return;
                            }
                            searching.set(false);
                            progressBar.setVisibility(View.GONE);
                            findMoreButton.setVisibility(View.VISIBLE);
                            findMoreButton.setEnabled(true);
                            if (adapter.getItemCount() == 0) {
                                if (message.trim().isEmpty()) {
                                    emptyText.setText(R.string.search_watch_links_error);
                                } else {
                                    emptyText.setText(message);
                                }
                                emptyText.setVisibility(View.VISIBLE);
                            } else {
                                emptyText.setVisibility(View.GONE);
                                Toast.makeText(fragment.requireContext(),
                                        message, Toast.LENGTH_SHORT).show();
                            }
                            updateConfirmButton.run();
                        });
                    }
                };

        cancelButton.setOnClickListener(v -> dialog.dismiss());
        confirmButton.setOnClickListener(v -> {
            List<String> selected = adapter.getSelectedUrls();
            if (!selected.isEmpty()) {
                listener.onLinksSelected(selected);
                dialog.dismiss();
            }
        });

        findMoreButton.setOnClickListener(v -> {
            if (!searching.compareAndSet(false, true)) {
                return;
            }
            findMoreButton.setEnabled(false);
            progressBar.setVisibility(View.VISIBLE);
            emptyText.setVisibility(View.GONE);
            Toast.makeText(fragment.requireContext(),
                    R.string.search_watch_links_finding_more,
                    Toast.LENGTH_SHORT).show();
            List<String> alreadyShown = adapter.getCurrentUrls();
            int countBefore = adapter.getItemCount();
            executor.execute(() -> {
                searchService.search(
                        query,
                        WatchLinkSearchOptions.findMore(new HashSet<>(alreadyShown)),
                        new WatchLinkSearchService.ProgressListener() {
                            @Override
                            public void onPartialResults(
                                    @NonNull List<WatchLinkCandidate> resultsSoFar) {
                                progressListener.onPartialResults(resultsSoFar);
                            }

                            @Override
                            public void onComplete(
                                    @NonNull List<WatchLinkCandidate> finalResults) {
                                mainHandler.post(() -> {
                                    if (!dialog.isShowing() || dismissed.get()) {
                                        return;
                                    }
                                    searching.set(false);
                                    int added = adapter.addNewItems(
                                            new ArrayList<>(finalResults));
                                    progressBar.setVisibility(View.GONE);
                                    findMoreButton.setVisibility(View.VISIBLE);
                                    findMoreButton.setEnabled(true);
                                    emptyText.setVisibility(View.GONE);
                                    if (added == 0) {
                                        Toast.makeText(fragment.requireContext(),
                                                R.string.search_watch_links_no_more,
                                                Toast.LENGTH_SHORT).show();
                                    } else if (adapter.getItemCount() > countBefore) {
                                        recyclerView.smoothScrollToPosition(
                                                adapter.getItemCount() - 1);
                                    }
                                    updateConfirmButton.run();
                                });
                            }

                            @Override
                            public void onError(@NonNull String message) {
                                progressListener.onError(message);
                            }
                        });
            });
        });

        // Первый быстрый поиск
        searching.set(true);
        findMoreButton.setVisibility(View.GONE);
        progressBar.setVisibility(View.VISIBLE);
        emptyText.setVisibility(View.VISIBLE);
        emptyText.setText(R.string.search_watch_links_loading);
        executor.execute(() -> searchService.search(
                query, WatchLinkSearchOptions.quick(), progressListener));

        dialog.show();
    }
}
