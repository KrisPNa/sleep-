package com.example.seriestracker.ui.adapters;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.example.seriestracker.R;
import com.example.seriestracker.data.entities.Collection;
import com.example.seriestracker.data.prefs.ThemePreferences;
import com.example.seriestracker.utils.CollectionCardColors;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class CollectionAdapter extends RecyclerView.Adapter<CollectionAdapter.CollectionViewHolder> {

    public interface OnCollectionClickListener {
        void onCollectionClick(Collection collection);
        void onFavoriteClick(Collection collection);
    }

    private final List<Collection> collections = new ArrayList<>();
    private final OnCollectionClickListener listener;
    private Boolean cachedDark;
    private Float cornerRadiusPx;
    private Integer strokeWidthPx;

    public CollectionAdapter(OnCollectionClickListener listener) {
        this.listener = listener;
        setHasStableIds(true);
    }

    public void setCollections(List<Collection> newList) {
        List<Collection> incoming = newList != null ? new ArrayList<>(newList) : new ArrayList<>();
        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return collections.size();
            }

            @Override
            public int getNewListSize() {
                return incoming.size();
            }

            @Override
            public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                return collections.get(oldItemPosition).getId()
                        == incoming.get(newItemPosition).getId();
            }

            @Override
            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                Collection a = collections.get(oldItemPosition);
                Collection b = incoming.get(newItemPosition);
                return Objects.equals(a.getName(), b.getName())
                        && a.isFavorite() == b.isFavorite()
                        && a.getSeriesCount() == b.getSeriesCount()
                        && Objects.equals(a.getColors(), b.getColors());
            }
        }, false);

        collections.clear();
        collections.addAll(incoming);
        diff.dispatchUpdatesTo(this);
    }

    @Override
    public long getItemId(int position) {
        return collections.get(position).getId();
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
        holder.bind(collections.get(position));
    }

    @Override
    public int getItemCount() {
        return collections.size();
    }

    /** Сброс кэша темы (после переключения light/dark). */
    public void clearThemeCache() {
        cachedDark = null;
        notifyDataSetChanged();
    }

    class CollectionViewHolder extends RecyclerView.ViewHolder {
        private final View colorIndicator;
        private final View cardContent;
        private final TextView nameTextView;
        private final ImageView favoriteIcon;
        private final TextView seriesCountTextView;
        private final MaterialCardView cardView;
        private Collection bound;
        private int lastMainColor = Integer.MIN_VALUE;
        private Boolean lastDark;

        public CollectionViewHolder(@NonNull View itemView) {
            super(itemView);
            colorIndicator = itemView.findViewById(R.id.colorIndicator);
            cardContent = itemView.findViewById(R.id.collectionCardContent);
            nameTextView = itemView.findViewById(R.id.collectionNameTextView);
            favoriteIcon = itemView.findViewById(R.id.favoriteStar);
            seriesCountTextView = itemView.findViewById(R.id.seriesCountTextView);
            cardView = itemView.findViewById(R.id.collectionCardView);

            cardView.setOnClickListener(v -> {
                if (listener != null && bound != null) {
                    listener.onCollectionClick(bound);
                }
            });
            favoriteIcon.setOnClickListener(v -> {
                if (listener != null && bound != null) {
                    listener.onFavoriteClick(bound);
                }
            });
        }

        public void bind(Collection collection) {
            bound = collection;
            Context context = itemView.getContext();
            nameTextView.setText(collection.getName());

            boolean dark = resolveDark(context);
            int mainColor = parseMainColor(collection);

            if (lastDark == null || lastDark != dark || lastMainColor != mainColor) {
                applyCard(mainColor, dark);
                lastDark = dark;
                lastMainColor = mainColor;
            }

            boolean fav = collection.isFavorite();
            if (fav) {
                if (favoriteIcon.getVisibility() != View.VISIBLE) {
                    favoriteIcon.setVisibility(View.VISIBLE);
                }
                favoriteIcon.setImageResource(R.drawable.ic_baseline_star_24_filled);
                favoriteIcon.setColorFilter(0xFFFFEB3B);
            } else if (favoriteIcon.getVisibility() != View.GONE) {
                favoriteIcon.setVisibility(View.GONE);
                favoriteIcon.clearColorFilter();
            }

            seriesCountTextView.setText(seriesCountLabel(collection.getSeriesCount()));
        }

        private void applyCard(int mainColor, boolean dark) {
            float radius = cornerRadius();
            int[] colors = dark
                    ? CollectionCardColors.darkGradient(mainColor)
                    : CollectionCardColors.lightGradient(mainColor);
            GradientDrawable bg = CollectionCardColors.verticalGradient(colors, radius);

            // Цвет «дна» карточки = нижний стоп градиента: если слой карты
            // чуть выглянет, не будет белой вспышки.
            int bottom = colors[colors.length - 1];
            cardView.setRadius(radius);
            cardView.setCardElevation(0f);
            cardView.setUseCompatPadding(false);
            cardView.setPreventCornerOverlap(false);
            cardView.setCardBackgroundColor(bottom);
            cardContent.setBackground(bg);

            if (dark) {
                cardView.setStrokeWidth(strokeWidth());
                cardView.setStrokeColor(Color.argb(90,
                        Color.red(colors[0]), Color.green(colors[0]), Color.blue(colors[0])));
                nameTextView.setTextColor(0xFFF4F7FB);
                seriesCountTextView.setTextColor(0x8CFFFFFF);
                colorIndicator.setBackgroundColor(0x38FFFFFF);
            } else {
                cardView.setStrokeWidth(0);
                nameTextView.setTextColor(0xFFF5F8FA);
                seriesCountTextView.setTextColor(0xE6F5F8FA);
                colorIndicator.setBackgroundColor(0x59000000);
            }
        }

        private boolean resolveDark(Context context) {
            if (cachedDark == null) {
                cachedDark = ThemePreferences.isDark(context);
            }
            return cachedDark;
        }

        private float cornerRadius() {
            if (cornerRadiusPx == null) {
                cornerRadiusPx = TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP, 16f,
                        itemView.getResources().getDisplayMetrics());
            }
            return cornerRadiusPx;
        }

        private int strokeWidth() {
            if (strokeWidthPx == null) {
                strokeWidthPx = Math.round(TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP, 1f,
                        itemView.getResources().getDisplayMetrics()));
            }
            return strokeWidthPx;
        }

        private int parseMainColor(Collection collection) {
            try {
                if (collection.getColors() != null && !collection.getColors().isEmpty()) {
                    return Color.parseColor(collection.getColors().get(0));
                }
            } catch (Exception ignored) {
            }
            return 0xFF64B5F6;
        }
    }

    static String seriesCountLabel(int count) {
        int n = Math.abs(count) % 100;
        int n1 = n % 10;
        if (n > 10 && n < 20) {
            return count + " сериалов";
        }
        if (n1 == 1) {
            return count + " сериал";
        }
        if (n1 >= 2 && n1 <= 4) {
            return count + " сериала";
        }
        return count + " сериалов";
    }
}
