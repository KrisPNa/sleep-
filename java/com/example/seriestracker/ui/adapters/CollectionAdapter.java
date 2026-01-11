package com.example.seriestracker.ui.adapters;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.lifecycle.LifecycleOwner;
import androidx.recyclerview.widget.RecyclerView;

import com.example.seriestracker.R;
import com.example.seriestracker.data.entities.Collection;
import com.example.seriestracker.ui.viewmodels.SeriesViewModel;
import com.google.android.material.card.MaterialCardView;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CollectionAdapter extends RecyclerView.Adapter<CollectionAdapter.CollectionViewHolder> {

    public interface OnCollectionClickListener {
        void onCollectionClick(Collection collection);
    }

    private List<Collection> collections;
    private final OnCollectionClickListener listener;
    private final SeriesViewModel viewModel;
    private final LifecycleOwner lifecycleOwner;
    private final Map<Long, Integer> seriesCountMap = new HashMap<>();

    public CollectionAdapter(OnCollectionClickListener listener,
                             SeriesViewModel viewModel,
                             LifecycleOwner lifecycleOwner) {
        this.listener = listener;
        this.viewModel = viewModel;
        this.lifecycleOwner = lifecycleOwner;
    }

    public void setCollections(List<Collection> collections) {
        this.collections = collections;

        if (collections != null) {
            for (Collection collection : collections) {
                viewModel.getSeriesCountInCollection(collection.getId())
                        .observe(lifecycleOwner, count -> {
                            if (count != null) {
                                seriesCountMap.put(collection.getId(), count);
                                notifyDataSetChanged();
                            }
                        });
            }
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public CollectionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_collection, parent, false);
        return new CollectionViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CollectionViewHolder holder, int position) {
        if (collections != null && position < collections.size()) {
            Collection collection = collections.get(position);
            Integer count = seriesCountMap.get(collection.getId());
            holder.bind(collection, count != null ? count : 0, listener);
        }
    }

    @Override
    public int getItemCount() {
        return collections != null ? collections.size() : 0;
    }

    static class CollectionViewHolder extends RecyclerView.ViewHolder {
        private final MaterialCardView cardView;
        private final TextView nameTextView;
        private final TextView countTextView;
        private final ImageView favoriteStar;
        private final View colorIndicator;

        public CollectionViewHolder(@NonNull View itemView) {
            super(itemView);
            cardView = itemView.findViewById(R.id.collectionCardView);
            nameTextView = itemView.findViewById(R.id.collectionNameTextView);
            countTextView = itemView.findViewById(R.id.seriesCountTextView);
            favoriteStar = itemView.findViewById(R.id.favoriteStar);
            colorIndicator = itemView.findViewById(R.id.colorIndicator);
        }

        public void bind(Collection collection, int seriesCount, OnCollectionClickListener listener) {
            nameTextView.setText(collection.getName());

            // Показываем звездочку если коллекция в избранном
            if (collection.isFavorite()) {
                favoriteStar.setVisibility(View.VISIBLE);
                favoriteStar.setColorFilter(itemView.getContext().getResources().getColor(R.color.favorite_star));
            } else {
                favoriteStar.setVisibility(View.GONE);
            }

            // Устанавливаем цвет коллекции
            String color = collection.getColor();
            if (color != null && !color.isEmpty()) {
                try {
                    // Устанавливаем цвет обводки карточки
                    cardView.setStrokeColor(Color.parseColor(color));

                    // Устанавливаем цвет индикатора
                    colorIndicator.setBackgroundColor(Color.parseColor(color));
                    colorIndicator.setVisibility(View.VISIBLE);
                } catch (Exception e) {
                    // Если цвет некорректный, используем цвет по умолчанию
                    cardView.setStrokeColor(itemView.getContext().getResources().getColor(R.color.primary_blue_light));
                    colorIndicator.setBackgroundColor(itemView.getContext().getResources().getColor(R.color.primary_blue));
                }
            } else {
                // Если цвет не установлен, используем цвет по умолчанию
                cardView.setStrokeColor(itemView.getContext().getResources().getColor(R.color.primary_blue_light));
                colorIndicator.setBackgroundColor(itemView.getContext().getResources().getColor(R.color.primary_blue));
            }

            // Правильное склонение слова "сериал"
            String countText = getCountText(seriesCount);
            countTextView.setText(countText);

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onCollectionClick(collection);
                }
            });
        }

        private String getCountText(int seriesCount) {
            if (seriesCount % 10 == 1 && seriesCount % 100 != 11) {
                return seriesCount + " сериал";
            } else if (seriesCount % 10 >= 2 && seriesCount % 10 <= 4 &&
                    (seriesCount % 100 < 10 || seriesCount % 100 >= 20)) {
                return seriesCount + " сериала";
            } else {
                return seriesCount + " сериалов";
            }
        }
    }
}