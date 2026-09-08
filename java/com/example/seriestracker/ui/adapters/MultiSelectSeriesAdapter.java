
package com.example.seriestracker.ui.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.seriestracker.R;
import com.example.seriestracker.data.entities.Series;
import com.example.seriestracker.utils.MediaStorageHelper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MultiSelectSeriesAdapter extends RecyclerView.Adapter<MultiSelectSeriesAdapter.SeriesViewHolder> {

    private List<Series> seriesList;
    private final Set<Long> selectedSeriesIds = new HashSet<>();
    private OnSelectionChangeListener listener;

    public interface OnSelectionChangeListener {
        void onSelectionChanged(int selectedCount);
    }

    public MultiSelectSeriesAdapter(List<Series> seriesList) {
        this.seriesList = seriesList != null ? seriesList : new ArrayList<>();
    }

    public void setOnSelectionChangeListener(OnSelectionChangeListener listener) {
        this.listener = listener;
    }

    public Set<Long> getSelectedSeriesIds() {
        return new HashSet<>(selectedSeriesIds);
    }

    public int getSelectedCount() {
        return selectedSeriesIds.size();
    }

    public void clearSelection() {
        if (selectedSeriesIds.isEmpty()) {
            return;
        }
        selectedSeriesIds.clear();
        notifyDataSetChanged();
        notifySelectionChanged();
    }

    public void setSeriesList(List<Series> seriesList) {
        this.seriesList = seriesList != null ? seriesList : new ArrayList<>();
        notifyDataSetChanged();
    }

    private void setSelected(long seriesId, boolean selected) {
        if (selected) {
            selectedSeriesIds.add(seriesId);
        } else {
            selectedSeriesIds.remove(seriesId);
        }
        notifySelectionChanged();
    }

    private void notifySelectionChanged() {
        if (listener != null) {
            listener.onSelectionChanged(selectedSeriesIds.size());
        }
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
        holder.bind(seriesList.get(position));
    }

    @Override
    public int getItemCount() {
        return seriesList.size();
    }

    class SeriesViewHolder extends RecyclerView.ViewHolder {
        private final ImageView seriesImageView;
        private final TextView seriesTitleTextView;
        private final CheckBox checkBox;
        private CompoundButton.OnCheckedChangeListener checkedChangeListener;

        SeriesViewHolder(@NonNull View itemView) {
            super(itemView);
            seriesImageView = itemView.findViewById(R.id.seriesImage);
            seriesTitleTextView = itemView.findViewById(R.id.seriesTitle);
            checkBox = itemView.findViewById(R.id.seriesCheckBox);
        }

        void bind(Series series) {
            seriesTitleTextView.setText(series.getTitle());

            if (series.getImageUri() != null && !series.getImageUri().isEmpty()) {
                Glide.with(itemView.getContext())
                        .load(MediaStorageHelper.resolveLoadUri(series.getImageUri()))
                        .placeholder(R.drawable.ic_baseline_image_24)
                        .error(R.drawable.ic_baseline_image_24)
                        .into(seriesImageView);
            } else {
                seriesImageView.setImageResource(R.drawable.ic_baseline_image_24);
            }

            checkBox.setOnCheckedChangeListener(null);
            checkBox.setChecked(selectedSeriesIds.contains(series.getId()));

            checkedChangeListener = (buttonView, isChecked) ->
                    setSelected(series.getId(), isChecked);
            checkBox.setOnCheckedChangeListener(checkedChangeListener);

            itemView.setOnClickListener(v -> checkBox.setChecked(!checkBox.isChecked()));
        }
    }
}
