package com.example.seriestracker.ui.adapters;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.seriestracker.R;
import com.example.seriestracker.data.entities.Series;
import com.example.seriestracker.utils.MediaStorageHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class SeriesAdapter extends RecyclerView.Adapter<SeriesAdapter.SeriesViewHolder> {

    private static final Object PAYLOAD_META = new Object();
    private static final Map<String, GradientDrawable> STATUS_BADGES = new HashMap<>();

    public interface OnSeriesClickListener {
        void onSeriesClick(Series series);
        void onWatchedToggle(Series series, boolean isWatched);
        void onFavoriteToggle(Series series, boolean isFavorite);
    }

    public interface OnSeriesMenuListener {
        void onChangeStatus(Series series, String newStatus);
        void onDeleteAction(Series series);
    }

    public interface OnSeriesLongClickListener {
        void onSeriesLongClick(Series series);
    }

    private final List<Series> seriesList = new ArrayList<>();
    private final OnSeriesClickListener listener;
    private OnSeriesLongClickListener longClickListener;
    private OnSeriesMenuListener menuListener;
    private boolean inCollectionContext;

    public SeriesAdapter(OnSeriesClickListener listener) {
        this.listener = listener;
        setHasStableIds(true);
    }

    public void setOnSeriesLongClickListener(OnSeriesLongClickListener longClickListener) {
        this.longClickListener = longClickListener;
    }

    public void setOnSeriesMenuListener(OnSeriesMenuListener menuListener) {
        this.menuListener = menuListener;
    }

    public void setInCollectionContext(boolean inCollectionContext) {
        this.inCollectionContext = inCollectionContext;
    }

    public void setSeriesList(List<Series> newList) {
        List<Series> incoming = newList != null ? new ArrayList<>(newList) : new ArrayList<>();
        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return seriesList.size();
            }

            @Override
            public int getNewListSize() {
                return incoming.size();
            }

            @Override
            public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                return seriesList.get(oldItemPosition).getId()
                        == incoming.get(newItemPosition).getId();
            }

            @Override
            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                Series a = seriesList.get(oldItemPosition);
                Series b = incoming.get(newItemPosition);
                return Objects.equals(a.getTitle(), b.getTitle())
                        && Objects.equals(a.getStatus(), b.getStatus())
                        && Objects.equals(a.getNotes(), b.getNotes())
                        && Objects.equals(a.getWatchAt(), b.getWatchAt())
                        && Objects.equals(a.getImageUri(), b.getImageUri())
                        && a.getIsFavorite() == b.getIsFavorite()
                        && a.getIsWatched() == b.getIsWatched();
            }

            @Nullable
            @Override
            public Object getChangePayload(int oldItemPosition, int newItemPosition) {
                Series a = seriesList.get(oldItemPosition);
                Series b = incoming.get(newItemPosition);
                // Обложка та же — можно обновить только метаданные без Glide
                if (Objects.equals(a.getImageUri(), b.getImageUri())) {
                    return PAYLOAD_META;
                }
                return null;
            }
        }, false);

        seriesList.clear();
        seriesList.addAll(incoming);
        diff.dispatchUpdatesTo(this);
    }

    @Override
    public long getItemId(int position) {
        return seriesList.get(position).getId();
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
        holder.bind(seriesList.get(position), listener, longClickListener, menuListener,
                inCollectionContext, false);
    }

    @Override
    public void onBindViewHolder(@NonNull SeriesViewHolder holder, int position,
                                 @NonNull List<Object> payloads) {
        if (!payloads.isEmpty() && payloads.contains(PAYLOAD_META)) {
            holder.bind(seriesList.get(position), listener, longClickListener, menuListener,
                    inCollectionContext, true);
            return;
        }
        super.onBindViewHolder(holder, position, payloads);
    }

    @Override
    public int getItemCount() {
        return seriesList.size();
    }

    @Override
    public void onViewRecycled(@NonNull SeriesViewHolder holder) {
        super.onViewRecycled(holder);
        Glide.with(holder.itemView.getContext()).clear(holder.seriesImageView);
    }

    static class SeriesViewHolder extends RecyclerView.ViewHolder {
        private final ImageView seriesImageView;
        private final TextView titleTextView;
        private final TextView notesTextView;
        private final TextView statusTextView;
        private final ImageView watchAtIndicator;
        private final ImageView notesIndicator;
        private final ImageView favoriteBookmark;
        private final ImageButton menuButton;

        public SeriesViewHolder(@NonNull View itemView) {
            super(itemView);
            seriesImageView = itemView.findViewById(R.id.seriesImageView);
            titleTextView = itemView.findViewById(R.id.titleTextView);
            notesTextView = itemView.findViewById(R.id.notesTextView);
            statusTextView = itemView.findViewById(R.id.statusTextView);
            watchAtIndicator = itemView.findViewById(R.id.watchAtIndicator);
            notesIndicator = itemView.findViewById(R.id.notesIndicator);
            favoriteBookmark = itemView.findViewById(R.id.favoriteBookmark);
            menuButton = itemView.findViewById(R.id.menuButton);
        }

        public void bind(Series series, OnSeriesClickListener listener,
                         OnSeriesLongClickListener longClickListener,
                         OnSeriesMenuListener menuListener,
                         boolean inCollectionContext,
                         boolean metaOnly) {
            titleTextView.setText(series.getTitle());

            boolean hasWatchLink = series.getWatchAt() != null
                    && !series.getWatchAt().trim().isEmpty();
            watchAtIndicator.setVisibility(hasWatchLink ? View.VISIBLE : View.GONE);

            boolean hasNotes = series.getNotes() != null && !series.getNotes().trim().isEmpty();
            notesIndicator.setVisibility(hasNotes ? View.VISIBLE : View.GONE);

            if (favoriteBookmark != null) {
                favoriteBookmark.setVisibility(series.getIsFavorite() ? View.VISIBLE : View.GONE);
            }

            statusTextView.setText(getStatusBadgeText(series.getStatus()));
            applyStatusBadge(statusTextView, series.getStatus());

            if (hasNotes) {
                notesTextView.setText(series.getNotes().trim());
                notesTextView.setVisibility(View.VISIBLE);
            } else {
                notesTextView.setVisibility(View.GONE);
            }

            if (!metaOnly) {
                if (series.getImageUri() != null && !series.getImageUri().isEmpty()) {
                    Glide.with(itemView.getContext())
                            .load(MediaStorageHelper.resolveLoadUri(series.getImageUri()))
                            .placeholder(R.drawable.ic_baseline_image_24)
                            .override(176, 236)
                            .centerCrop()
                            .into(seriesImageView);
                } else {
                    Glide.with(itemView.getContext()).clear(seriesImageView);
                    seriesImageView.setImageResource(R.drawable.ic_baseline_image_24);
                }
            }

            menuButton.setOnClickListener(v ->
                    showCardMenu(v, series, listener, menuListener, inCollectionContext));

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onSeriesClick(series);
                }
            });

            itemView.setOnLongClickListener(v -> {
                if (longClickListener != null) {
                    longClickListener.onSeriesLongClick(series);
                    return true;
                }
                return false;
            });
        }

        private void showCardMenu(View anchor, Series series,
                                  OnSeriesClickListener listener,
                                  OnSeriesMenuListener menuListener,
                                  boolean inCollectionContext) {
            PopupMenu popup = new PopupMenu(anchor.getContext(), anchor);
            String favLabel = series.getIsFavorite()
                    ? "Удалить из избранного"
                    : "Добавить в избранное";
            popup.getMenu().add(0, 1, 0, favLabel);
            popup.getMenu().add(0, 2, 1, "Сменить статус");
            popup.getMenu().add(0, 3, 2,
                    inCollectionContext ? "Удалить из коллекции" : "Удалить сериал");

            popup.setOnMenuItemClickListener(item -> {
                int id = item.getItemId();
                if (id == 1) {
                    if (listener != null) {
                        listener.onFavoriteToggle(series, !series.getIsFavorite());
                    }
                    return true;
                }
                if (id == 2) {
                    showStatusPicker(series, menuListener);
                    return true;
                }
                if (id == 3) {
                    if (menuListener != null) {
                        menuListener.onDeleteAction(series);
                    }
                    return true;
                }
                return false;
            });
            popup.show();
        }

        private void showStatusPicker(Series series, OnSeriesMenuListener menuListener) {
            if (menuListener == null) return;
            final String[] labels = {"Запланировано", "Смотрю", "Завершено", "Брошено"};
            final String[] values = {"planned", "watching", "completed", "dropped"};
            int checked = 0;
            String current = series.getStatus() != null ? series.getStatus() : "planned";
            for (int i = 0; i < values.length; i++) {
                if (values[i].equals(current)) {
                    checked = i;
                    break;
                }
            }
            new AlertDialog.Builder(itemView.getContext())
                    .setTitle("Статус")
                    .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                        menuListener.onChangeStatus(series, values[which]);
                        dialog.dismiss();
                    })
                    .setNegativeButton("Отмена", null)
                    .show();
        }

        private String getStatusBadgeText(String status) {
            switch (status != null ? status : "") {
                case "watching": return "смотрю";
                case "completed": return "завершено";
                case "dropped": return "брошено";
                case "planned":
                default: return "в планах";
            }
        }

        private void applyStatusBadge(TextView view, String status) {
            String key = status != null ? status : "planned";
            GradientDrawable cached = STATUS_BADGES.get(key);
            if (cached == null) {
                int color;
                switch (key) {
                    case "watching":
                        color = Color.parseColor("#B842A5F5");
                        break;
                    case "completed":
                        color = Color.parseColor("#B866BB6A");
                        break;
                    case "dropped":
                        color = Color.parseColor("#B8EF5350");
                        break;
                    case "planned":
                    default:
                        color = Color.parseColor("#B87E57C2");
                        break;
                }
                cached = new GradientDrawable();
                cached.setColor(color);
                STATUS_BADGES.put(key, cached);
            }
            view.setBackground(cached.getConstantState() != null
                    ? cached.getConstantState().newDrawable().mutate()
                    : cached);
            view.setTextColor(Color.WHITE);
        }
    }
}
