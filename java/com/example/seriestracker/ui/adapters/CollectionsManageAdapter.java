package com.example.seriestracker.ui.adapters;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import com.example.seriestracker.R;
import com.example.seriestracker.data.entities.Collection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CollectionsManageAdapter extends RecyclerView.Adapter<CollectionsManageAdapter.ViewHolder> {

    private List<Collection> collections = new ArrayList<>();
    private Map<Long, Integer> seriesCountMap = new HashMap<>();
    private final OnCollectionActionListener listener;
    private Context context;

    public interface OnCollectionActionListener {
        void onEditClick(Collection collection);
        void onDeleteClick(Collection collection);
        void onFavoriteClick(Collection collection);
    }

    public CollectionsManageAdapter(OnCollectionActionListener listener) {
        this.listener = listener;
    }

    public void setCollections(List<Collection> collections) {
        this.collections = collections;
        notifyDataSetChanged();
    }

    public void updateSeriesCount(long collectionId, int count) {
        seriesCountMap.put(collectionId, count);
        for (int i = 0; i < collections.size(); i++) {
            if (collections.get(i).getId() == collectionId) {
                notifyItemChanged(i);
                break;
            }
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        context = parent.getContext();
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_collection_manage, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Collection collection = collections.get(position);

        // Устанавливаем цвет коллекции - ИСПРАВЛЕНО: используем getColors() вместо getColor()
        List<String> colors = collection.getColors();
        if (colors != null && !colors.isEmpty()) {
            try {
                // Берем первый цвет из списка для индикатора
                String firstColor = colors.get(0);
                holder.colorIndicator.setBackgroundColor(Color.parseColor(firstColor));
                holder.colorIndicator.setVisibility(View.VISIBLE);

                // Если есть несколько цветов, можно показать градиент (опционально)
                if (colors.size() > 1) {
                    // Можно добавить градиент, если хотите
                    // Например: создать GradientDrawable с углом 45 градусов
                }
            } catch (Exception e) {
                // Если цвет некорректный, используем цвет по умолчанию
                holder.colorIndicator.setBackgroundColor(context.getResources().getColor(R.color.primary_blue));
                holder.colorIndicator.setVisibility(View.VISIBLE);
            }
        } else {
            // Если цвет не установлен, используем цвет по умолчанию
            holder.colorIndicator.setBackgroundColor(context.getResources().getColor(R.color.primary_blue));
            holder.colorIndicator.setVisibility(View.VISIBLE);
        }

        holder.collectionNameTextView.setText(collection.getName());

        Integer count = seriesCountMap.get(collection.getId());
        holder.seriesCountTextView.setText(count != null ? String.valueOf(count) : "0");

        // Обновляем состояние звезды избранного
        updateFavoriteIcon(holder.favoriteButton, collection.isFavorite());

        // Настройка кнопки избранного
        holder.favoriteButton.setOnClickListener(v -> {
            // Меняем состояние избранного
            collection.setFavorite(!collection.isFavorite());
            updateFavoriteIcon(holder.favoriteButton, collection.isFavorite());

            // Уведомляем слушателя
            if (listener != null) {
                listener.onFavoriteClick(collection);
            }
        });

        // Настройка выпадающего меню
        holder.menuButton.setOnClickListener(v -> {
            showPopupMenu(v, collection, holder.getAdapterPosition());
        });
    }

    private void updateFavoriteIcon(ImageView favoriteButton, boolean isFavorite) {
        if (isFavorite) {
            favoriteButton.setImageResource(R.drawable.ic_baseline_star_24);
            favoriteButton.setContentDescription("Убрать из избранного");
            favoriteButton.setColorFilter(context.getResources().getColor(R.color.favorite_star));
        } else {
            favoriteButton.setImageResource(R.drawable.ic_baseline_star_border_24);
            favoriteButton.setContentDescription("Добавить в избранное");
            favoriteButton.setColorFilter(context.getResources().getColor(R.color.text_gray));
        }
    }

    private void showPopupMenu(View view, Collection collection, int position) {
        PopupMenu popupMenu = new PopupMenu(context, view);
        popupMenu.inflate(R.menu.collection_actions_menu);

        popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                int itemId = item.getItemId();

                if (itemId == R.id.action_edit) {
                    if (listener != null) {
                        listener.onEditClick(collection);
                    }
                    return true;
                } else if (itemId == R.id.action_delete) {
                    if (listener != null) {
                        listener.onDeleteClick(collection);
                    }
                    return true;
                }
                return false;
            }
        });

        popupMenu.show();
    }

    @Override
    public int getItemCount() {
        return collections.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        CardView collectionCardView;
        TextView collectionNameTextView;
        TextView seriesCountTextView;
        ImageView favoriteButton;
        ImageButton menuButton;
        View colorIndicator;

        ViewHolder(View itemView) {
            super(itemView);
            collectionCardView = itemView.findViewById(R.id.collectionCardView);
            collectionNameTextView = itemView.findViewById(R.id.collectionNameTextView);
            seriesCountTextView = itemView.findViewById(R.id.seriesCountTextView);
            favoriteButton = itemView.findViewById(R.id.favoriteButton);
            menuButton = itemView.findViewById(R.id.menuButton);
            colorIndicator = itemView.findViewById(R.id.colorIndicator);
        }
    }
}