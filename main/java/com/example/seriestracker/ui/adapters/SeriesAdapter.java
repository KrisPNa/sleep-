package com.example.seriestracker.ui.adapters;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.seriestracker.R;
import com.example.seriestracker.data.entities.Series;

import java.util.List;

public class SeriesAdapter extends RecyclerView.Adapter<SeriesAdapter.SeriesViewHolder> {

    public interface OnSeriesClickListener {
        void onSeriesClick(Series series);
        void onWatchedToggle(Series series, boolean isWatched);
        void onFavoriteToggle(Series series, boolean isFavorite);
    }

    private List<Series> seriesList;
    private final OnSeriesClickListener listener;

    public SeriesAdapter(OnSeriesClickListener listener) {
        this.listener = listener;
    }

    public void setSeriesList(List<Series> seriesList) {
        this.seriesList = seriesList;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public SeriesViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_series, parent, false);
        return new SeriesViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SeriesViewHolder holder, int position) {
        if (seriesList != null) {
            Series series = seriesList.get(position);
            holder.bind(series, listener);
        }
    }

    @Override
    public int getItemCount() {
        return seriesList != null ? seriesList.size() : 0;
    }

    static class SeriesViewHolder extends RecyclerView.ViewHolder {
        private final ImageView seriesImageView;
        private final TextView titleTextView;
        private final TextView notesTextView;
        private final TextView statusTextView;
        private final ImageButton favoriteButton;

        public SeriesViewHolder(@NonNull View itemView) {
            super(itemView);
            seriesImageView = itemView.findViewById(R.id.seriesImageView);
            titleTextView = itemView.findViewById(R.id.titleTextView);
            notesTextView = itemView.findViewById(R.id.notesTextView);
            statusTextView = itemView.findViewById(R.id.statusTextView);
            favoriteButton = itemView.findViewById(R.id.favoriteButton);
        }

        public void bind(Series series, OnSeriesClickListener listener) {
            titleTextView.setText(series.getTitle());

            // Кнопка избранного
            favoriteButton.setImageResource(series.getIsFavorite() ?
                    R.drawable.ic_baseline_star_24 : R.drawable.ic_baseline_star_border_24);

            // Устанавливаем цвет звезды
            if (series.getIsFavorite()) {
                favoriteButton.setColorFilter(itemView.getContext().getResources().getColor(R.color.favorite_star));
            } else {
                favoriteButton.setColorFilter(itemView.getContext().getResources().getColor(R.color.text_gray));
            }

            // Отображение статуса
            String statusText = getStatusText(series.getStatus());
            statusTextView.setText(statusText);

            // Установка цвета фона статуса
            int backgroundColor = getStatusBackgroundColor(series.getStatus());
            statusTextView.setBackgroundColor(backgroundColor);
            statusTextView.setTextColor(Color.BLACK); // ЧЕРНЫЙ текст для лучшей видимости

            if (series.getNotes() != null && !series.getNotes().isEmpty()) {
                notesTextView.setText(series.getNotes());
                notesTextView.setVisibility(View.VISIBLE);
            } else {
                notesTextView.setVisibility(View.GONE);
            }

            // Загрузка изображения
            if (series.getImageUri() != null && !series.getImageUri().isEmpty()) {
                Glide.with(itemView.getContext())
                        .load(series.getImageUri())
                        .placeholder(R.drawable.ic_baseline_image_24)
                        .into(seriesImageView);
            } else {
                seriesImageView.setImageResource(R.drawable.ic_baseline_image_24);
            }

            // Кнопка "Избранное"
            favoriteButton.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onFavoriteToggle(series, !series.getIsFavorite());
                }
            });

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onSeriesClick(series);
                }
            });
        }

        private String getStatusText(String status) {
            switch (status) {
                case "watching": return "Смотрю";
                case "completed": return "Завершено";
                case "dropped": return "Брошено";
                case "planned": return "Запланировано";
                default: return "Запланировано";
            }
        }

        private int getStatusBackgroundColor(String status) {
            switch (status) {
                case "watching": return Color.parseColor("#64B5F6"); // Светло-голубой
                case "completed": return Color.parseColor("#81C784"); // Светло-зеленый
                case "dropped": return Color.parseColor("#E57373"); // Светло-красный
                case "planned":
                default: return Color.parseColor("#FFB74D"); // Светло-оранжевый
            }
        }
    }
}