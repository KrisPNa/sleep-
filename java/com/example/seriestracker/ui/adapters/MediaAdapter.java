package com.example.seriestracker.ui.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.seriestracker.R;
import com.example.seriestracker.data.entities.MediaFile;

import java.util.List;

public class MediaAdapter extends RecyclerView.Adapter<MediaAdapter.MediaViewHolder> {

    public interface OnMediaClickListener {
        void onMediaClick(MediaFile mediaFile, int position);
        void onMediaDelete(MediaFile mediaFile);
    }

    private List<MediaFile> mediaFiles;
    private final OnMediaClickListener listener;
    private boolean editMode = false; // По умолчанию режим просмотра (без возможности удаления)

    public MediaAdapter(OnMediaClickListener listener) {
        this.listener = listener;
    }

    public void setMediaFiles(List<MediaFile> mediaFiles) {
        this.mediaFiles = mediaFiles;
        notifyDataSetChanged();
    }

    public void setEditMode(boolean editMode) {
        this.editMode = editMode;
        notifyDataSetChanged(); // Обновляем весь список, чтобы применить изменения
    }

    @NonNull
    @Override
    public MediaViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_media_grid, parent, false);
        return new MediaViewHolder(view, this); // Передаем адаптер в ViewHolder
    }

    @Override
    public void onBindViewHolder(@NonNull MediaViewHolder holder, int position) {
        if (mediaFiles != null) {
            MediaFile mediaFile = mediaFiles.get(position);
            holder.bind(mediaFile, position, listener);
        }
    }

    @Override
    public int getItemCount() {
        return mediaFiles != null ? mediaFiles.size() : 0;
    }

    // Внутренний (не статический) класс ViewHolder
    class MediaViewHolder extends RecyclerView.ViewHolder {
        private final ImageView mediaImageView;
        private final ImageView videoIconView;
        private final ImageButton deleteButton;

        public MediaViewHolder(@NonNull View itemView, MediaAdapter adapter) {
            super(itemView);
            mediaImageView = itemView.findViewById(R.id.mediaImageView);
            videoIconView = itemView.findViewById(R.id.videoIconView);
            deleteButton = itemView.findViewById(R.id.deleteButton);
        }

        public void bind(MediaFile mediaFile, int position, OnMediaClickListener listener) {
            // Загружаем превью
            if (mediaFile.getFileUri() != null && !mediaFile.getFileUri().isEmpty()) {
                Glide.with(itemView.getContext())
                        .load(mediaFile.getFileUri())
                        .placeholder(R.drawable.ic_baseline_image_24)
                        .centerCrop()
                        .into(mediaImageView);
            } else {
                mediaImageView.setImageResource(R.drawable.ic_baseline_image_24);
            }

            // Показываем иконку видео только для видеофайлов
            if (mediaFile.getFileType() != null && mediaFile.getFileType().equals("video")) {
                videoIconView.setVisibility(View.VISIBLE);
            } else {
                videoIconView.setVisibility(View.GONE);
            }

            // Обработчик клика на элемент
            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onMediaClick(mediaFile, position);
                }
            });

            // Кнопка удаления - используем editMode из внешнего класса
            if (editMode) { // Теперь это работает, так как ViewHolder не статический
                deleteButton.setVisibility(View.VISIBLE);
                deleteButton.setOnClickListener(v -> {
                    if (listener != null) {
                        listener.onMediaDelete(mediaFile);
                    }
                });
            } else {
                deleteButton.setVisibility(View.GONE);
            }
        }
    }
}