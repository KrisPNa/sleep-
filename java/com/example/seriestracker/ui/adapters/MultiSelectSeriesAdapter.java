
        package com.example.seriestracker.ui.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.seriestracker.R;
import com.example.seriestracker.data.entities.Series;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MultiSelectSeriesAdapter extends RecyclerView.Adapter<MultiSelectSeriesAdapter.SeriesViewHolder> {

    private List<Series> seriesList;
    private Set<Long> selectedSeriesIds;
    private OnSelectionChangeListener listener;

    public interface OnSelectionChangeListener {
        void onSelectionChanged();
    }

    public MultiSelectSeriesAdapter(List<Series> seriesList, Set<Long> initiallySelectedSeriesIds) {
        this.seriesList = seriesList != null ? seriesList : new ArrayList<>();
        this.selectedSeriesIds = initiallySelectedSeriesIds != null ? new HashSet<>(initiallySelectedSeriesIds) : new HashSet<>();
    }

    public void setOnSelectionChangeListener(OnSelectionChangeListener listener) {
        this.listener = listener;
    }

    public Set<Long> getSelectedSeriesIds() {
        return new HashSet<>(selectedSeriesIds);
    }

    public void setSelectedSeriesIds(Set<Long> selectedSeriesIds) {
        this.selectedSeriesIds = selectedSeriesIds != null ? new HashSet<>(selectedSeriesIds) : new HashSet<>();
        notifyDataSetChanged();
    }

    public void setSeriesList(List<Series> seriesList) {
        this.seriesList = seriesList != null ? seriesList : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public SeriesViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_selectable_series, parent, false);
        return new SeriesViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SeriesViewHolder holder, int position) {
        Series series = seriesList.get(position);
        holder.bind(series);
    }

    @Override
    public int getItemCount() {
        return seriesList.size();
    }

    class SeriesViewHolder extends RecyclerView.ViewHolder {
        private ImageView seriesImageView;
        private TextView seriesTitleTextView;
        private CheckBox checkBox;

        public SeriesViewHolder(@NonNull View itemView) {
            super(itemView);
            seriesImageView = itemView.findViewById(R.id.seriesImage);
            seriesTitleTextView = itemView.findViewById(R.id.seriesTitle);
            checkBox = itemView.findViewById(R.id.seriesCheckBox);
        }

        public void bind(Series series) {
            seriesTitleTextView.setText(series.getTitle());

            // Load image with Glide
            if (series.getImageUri() != null && !series.getImageUri().isEmpty()) {
                Glide.with(itemView.getContext())
                        .load(series.getImageUri())
                        .placeholder(R.drawable.placeholder_image) // assuming you have a placeholder
                        .error(R.drawable.placeholder_image)
                        .into(seriesImageView);
            } else {
                seriesImageView.setImageResource(R.drawable.placeholder_image);
            }

            // Set checkbox state based on selection
            checkBox.setChecked(selectedSeriesIds.contains(series.getId()));

            // Handle checkbox click
            checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    selectedSeriesIds.add(series.getId());
                } else {
                    selectedSeriesIds.remove(series.getId());
                }

                if (listener != null) {
                    listener.onSelectionChanged();
                }
            });

            // Also allow clicking on the entire item to toggle selection
            itemView.setOnClickListener(v -> {
                boolean isSelected = selectedSeriesIds.contains(series.getId());
                if (isSelected) {
                    selectedSeriesIds.remove(series.getId());
                } else {
                    selectedSeriesIds.add(series.getId());
                }

                checkBox.setChecked(!isSelected);

                if (listener != null) {
                    listener.onSelectionChanged();
                }
            });
        }
    }
}