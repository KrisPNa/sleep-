package com.example.seriestracker.ui.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.seriestracker.R;
import com.example.seriestracker.data.watchlinks.WatchLinkCandidate;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class WatchLinkCandidatesAdapter
        extends RecyclerView.Adapter<WatchLinkCandidatesAdapter.CandidateViewHolder> {

    public interface Listener {
        void onSelectionChanged(int selectedCount);

        void onOpenLink(@NonNull WatchLinkCandidate candidate);
    }

    private final List<WatchLinkCandidate> items = new ArrayList<>();
    private final Set<String> selectedUrls = new LinkedHashSet<>();
    private Listener listener;

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void setItems(@NonNull List<WatchLinkCandidate> candidates) {
        items.clear();
        items.addAll(candidates);
        selectedUrls.retainAll(extractUrls(candidates));
        notifyDataSetChanged();
        notifySelectionChanged();
    }

    /** Добавляет только новые URL, не сбрасывая уже выбранные. */
    public int addNewItems(@NonNull List<WatchLinkCandidate> candidates) {
        int added = 0;
        Set<String> existing = extractUrls(items);
        for (WatchLinkCandidate candidate : candidates) {
            if (candidate.getUrl() == null || candidate.getUrl().trim().isEmpty()) {
                continue;
            }
            String url = candidate.getUrl().trim();
            if (existing.contains(url)) {
                continue;
            }
            existing.add(url);
            items.add(candidate);
            added++;
        }
        if (added > 0) {
            notifyItemRangeInserted(items.size() - added, added);
        }
        return added;
    }

    @NonNull
    public List<String> getCurrentUrls() {
        List<String> urls = new ArrayList<>();
        for (WatchLinkCandidate item : items) {
            if (item.getUrl() != null && !item.getUrl().trim().isEmpty()) {
                urls.add(item.getUrl().trim());
            }
        }
        return urls;
    }

    @NonNull
    public List<String> getSelectedUrls() {
        return new ArrayList<>(selectedUrls);
    }

    public int getSelectedCount() {
        return selectedUrls.size();
    }

    @NonNull
    @Override
    public CandidateViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_watch_link_candidate, parent, false);
        return new CandidateViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CandidateViewHolder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private void setSelected(@NonNull String url, boolean selected) {
        if (selected) {
            selectedUrls.add(url);
        } else {
            selectedUrls.remove(url);
        }
        notifySelectionChanged();
    }

    private void notifySelectionChanged() {
        if (listener != null) {
            listener.onSelectionChanged(selectedUrls.size());
        }
    }

    @NonNull
    private static Set<String> extractUrls(@NonNull List<WatchLinkCandidate> candidates) {
        Set<String> urls = new LinkedHashSet<>();
        for (WatchLinkCandidate candidate : candidates) {
            urls.add(candidate.getUrl());
        }
        return urls;
    }

    class CandidateViewHolder extends RecyclerView.ViewHolder {
        private final CheckBox checkBox;
        private final TextView titleText;
        private final TextView subtitleText;
        private final ImageButton openButton;
        private CompoundButton.OnCheckedChangeListener checkedChangeListener;

        CandidateViewHolder(@NonNull View itemView) {
            super(itemView);
            checkBox = itemView.findViewById(R.id.watchLinkCheckBox);
            titleText = itemView.findViewById(R.id.watchLinkTitleText);
            subtitleText = itemView.findViewById(R.id.watchLinkSubtitleText);
            openButton = itemView.findViewById(R.id.watchLinkOpenButton);
        }

        void bind(@NonNull WatchLinkCandidate candidate) {
            titleText.setText(candidate.getTitle());
            String subtitle = candidate.getSubtitle();
            if (subtitle == null || subtitle.isEmpty()) {
                subtitleText.setText(candidate.getSourceName());
            } else {
                subtitleText.setText(subtitle);
            }

            checkBox.setOnCheckedChangeListener(null);
            checkBox.setChecked(selectedUrls.contains(candidate.getUrl()));
            checkedChangeListener = (buttonView, isChecked) ->
                    setSelected(candidate.getUrl(), isChecked);
            checkBox.setOnCheckedChangeListener(checkedChangeListener);

            itemView.setOnClickListener(v -> checkBox.setChecked(!checkBox.isChecked()));
            openButton.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onOpenLink(candidate);
                }
            });
        }
    }
}
