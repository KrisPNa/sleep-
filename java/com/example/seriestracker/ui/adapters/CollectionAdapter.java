package com.example.seriestracker.ui.adapters;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.seriestracker.R;
import com.example.seriestracker.data.entities.Collection;
import com.example.seriestracker.utils.GradientUtils;
import com.google.android.material.card.MaterialCardView;

import java.util.List;

public class CollectionAdapter extends RecyclerView.Adapter<CollectionAdapter.CollectionViewHolder> {

    public interface OnCollectionClickListener {
        void onCollectionClick(Collection collection);
        void onFavoriteClick(Collection collection);
    }

    private List<Collection> collections;
    private final OnCollectionClickListener listener;

    public CollectionAdapter(OnCollectionClickListener listener) {
        this.listener = listener;
    }

    public void setCollections(List<Collection> collections) {
        this.collections = collections;
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
        Collection collection = collections.get(position);
        holder.bind(collection, listener);
    }

    @Override
    public int getItemCount() {
        return collections == null ? 0 : collections.size();
    }

    static class CollectionViewHolder extends RecyclerView.ViewHolder {
        private final View colorIndicator;
        private final TextView nameTextView;
        private final ImageView favoriteIcon;
        private final TextView seriesCountTextView;
        private final MaterialCardView cardView;

        public CollectionViewHolder(@NonNull View itemView) {
            super(itemView);

            // Находим элементы по ID из вашего макета
            colorIndicator = itemView.findViewById(R.id.colorIndicator);
            nameTextView = itemView.findViewById(R.id.collectionNameTextView);
            favoriteIcon = itemView.findViewById(R.id.favoriteStar); // Используем ID из вашего макета
            seriesCountTextView = itemView.findViewById(R.id.seriesCountTextView);
            cardView = itemView.findViewById(R.id.collectionCardView);
        }

        public void bind(Collection collection, OnCollectionClickListener listener) {
            // Устанавливаем название коллекции
            nameTextView.setText(collection.getName());

            // Получаем основной цвет коллекции (первый цвет из списка)
            String mainColor = getMainColor(collection);

            // Устанавливаем цвет для ободка карточки
            try {
                int strokeColor = Color.parseColor(mainColor);
                cardView.setStrokeColor(strokeColor);
                cardView.setStrokeWidth(3); // Толщина ободка в пикселях
            } catch (Exception e) {
                // Если цвет некорректен, используем цвет по умолчанию
                int defaultColor = itemView.getContext().getResources()
                        .getColor(R.color.primary_blue_light);
                cardView.setStrokeColor(defaultColor);
                cardView.setStrokeWidth(3);
            }

            // Устанавливаем цвет/градиент для полоски (colorIndicator)
            if (collection.getColors() != null && !collection.getColors().isEmpty()) {
                // Создаем градиентный индикатор
                GradientDrawable gradient = GradientUtils.createRectGradient(collection.getColors());
                if (gradient != null) {
                    gradient.setCornerRadius(2f); // Для тонкой линии
                    colorIndicator.setBackground(gradient);
                } else {
                    // Если градиент не удалось создать, используем первый цвет
                    try {
                        colorIndicator.setBackgroundColor(
                                android.graphics.Color.parseColor(collection.getColors().get(0))
                        );
                    } catch (Exception e) {
                        colorIndicator.setBackgroundColor(0xFF2196F3); // Синий по умолчанию
                    }
                }
            } else {
                colorIndicator.setBackgroundColor(0xFF2196F3); // Синий по умолчанию
            }

            // Показываем/скрываем иконку избранного
            if (collection.isFavorite()) {
                favoriteIcon.setVisibility(View.VISIBLE);
                favoriteIcon.setImageResource(R.drawable.ic_baseline_star_24_filled); // Звезда заполненная
            } else {
                favoriteIcon.setVisibility(View.GONE); // Скрываем если не избранное
            }

            // Устанавливаем количество сериалов
            if (collection.getSeriesCount() > 0) {
                seriesCountTextView.setText(collection.getSeriesCount() + " сериалов");
            } else {
                seriesCountTextView.setText("0 сериалов");
            }

            // Обработка клика на всей карточке
            cardView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onCollectionClick(collection);
                }
            });

            // Обработка клика на иконке избранного
            favoriteIcon.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onFavoriteClick(collection);
                }
            });
        }

        private String getMainColor(Collection collection) {
            // Получаем первый цвет из списка цветов коллекции
            if (collection.getColors() != null && !collection.getColors().isEmpty()) {
                return collection.getColors().get(0);
            }
            // Цвет по умолчанию
            return "#" + Integer.toHexString(itemView.getContext()
                    .getResources().getColor(R.color.primary_blue_light)).substring(2);
        }
    }
}