package com.example.seriestracker.ui.adapters;

import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.seriestracker.R;
import com.example.seriestracker.data.entities.MediaFile;
import com.example.seriestracker.utils.MediaStorageHelper;
import com.example.seriestracker.ui.custom.ZoomPanLayout;
import com.google.android.exoplayer2.ui.PlayerView;

import java.util.List;

public class MediaPagerAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_PHOTO = 0;
    private static final int TYPE_VIDEO = 1;

    public interface ZoomChangeListener {
        void onZoomChanged(boolean isZoomed);
    }

    private final List<MediaFile> mediaFiles;
    private ZoomChangeListener zoomChangeListener;

    public MediaPagerAdapter(List<MediaFile> mediaFiles) {
        this.mediaFiles = mediaFiles;
    }

    public void setZoomChangeListener(ZoomChangeListener listener) {
        this.zoomChangeListener = listener;
    }

    @Override
    public int getItemViewType(int position) {
        return "video".equals(mediaFiles.get(position).getFileType()) ? TYPE_VIDEO : TYPE_PHOTO;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_VIDEO) {
            View view = inflater.inflate(R.layout.item_media_page_video, parent, false);
            return new VideoViewHolder(view);
        }
        View view = inflater.inflate(R.layout.item_photo_page, parent, false);
        return new PhotoViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        MediaFile mediaFile = mediaFiles.get(position);
        if (holder instanceof PhotoViewHolder) {
            bindPhoto((PhotoViewHolder) holder, mediaFile);
        }
    }

    private void bindPhoto(PhotoViewHolder holder, MediaFile mediaFile) {
        holder.zoomPanLayout.resetZoom();
        notifyZoomChanged(false);

        Uri uri = MediaStorageHelper.resolveLoadUri(mediaFile.getFileUri());
        Glide.with(holder.photoView.getContext())
                .load(uri)
                .error(R.drawable.ic_baseline_image_24)
                .into(holder.photoView);
    }

    private void notifyZoomChanged(boolean isZoomed) {
        if (zoomChangeListener != null) {
            zoomChangeListener.onZoomChanged(isZoomed);
        }
    }

    @Override
    public void onViewRecycled(@NonNull RecyclerView.ViewHolder holder) {
        super.onViewRecycled(holder);
        if (holder instanceof VideoViewHolder) {
            ((VideoViewHolder) holder).zoomPanLayout.resetZoom();
        } else if (holder instanceof PhotoViewHolder) {
            ((PhotoViewHolder) holder).zoomPanLayout.resetZoom();
        }
    }

    @Override
    public int getItemCount() {
        return mediaFiles.size();
    }

    public PlayerView getPlayerViewAt(RecyclerView recyclerView, int position) {
        VideoViewHolder holder = getVideoViewHolderAt(recyclerView, position);
        return holder != null ? holder.playerView : null;
    }

    public ZoomPanLayout getZoomLayoutAt(RecyclerView recyclerView, int position) {
        RecyclerView.ViewHolder holder = recyclerView.findViewHolderForAdapterPosition(position);
        if (holder instanceof VideoViewHolder) {
            return ((VideoViewHolder) holder).zoomPanLayout;
        }
        if (holder instanceof PhotoViewHolder) {
            return ((PhotoViewHolder) holder).zoomPanLayout;
        }
        return null;
    }

    public void resetZoomAt(RecyclerView recyclerView, int position) {
        ZoomPanLayout zoomLayout = getZoomLayoutAt(recyclerView, position);
        if (zoomLayout != null) {
            zoomLayout.resetZoom();
        }
    }

    private VideoViewHolder getVideoViewHolderAt(RecyclerView recyclerView, int position) {
        RecyclerView.ViewHolder holder = recyclerView.findViewHolderForAdapterPosition(position);
        if (holder instanceof VideoViewHolder) {
            return (VideoViewHolder) holder;
        }
        return null;
    }

    static class PhotoViewHolder extends RecyclerView.ViewHolder {
        final ZoomPanLayout zoomPanLayout;
        final ImageView photoView;

        PhotoViewHolder(@NonNull View itemView) {
            super(itemView);
            zoomPanLayout = itemView.findViewById(R.id.photoZoomLayout);
            photoView = itemView.findViewById(R.id.photoView);
        }
    }

    static class VideoViewHolder extends RecyclerView.ViewHolder {
        final ZoomPanLayout zoomPanLayout;
        final PlayerView playerView;

        VideoViewHolder(@NonNull View itemView) {
            super(itemView);
            zoomPanLayout = itemView.findViewById(R.id.videoZoomLayout);
            playerView = itemView.findViewById(R.id.playerView);
        }
    }
}
