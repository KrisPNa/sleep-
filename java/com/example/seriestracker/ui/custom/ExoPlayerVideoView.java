package com.example.seriestracker.ui.custom;

import android.content.Context;
import android.net.Uri;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;

import com.bumptech.glide.Glide;
import com.example.seriestracker.R;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.ui.PlayerView;

public class ExoPlayerVideoView extends FrameLayout {

    private PlayerView playerView;
    private ImageView thumbnailView;
    private ProgressBar progressBar;
    private SimpleExoPlayer player;

    public ExoPlayerVideoView(Context context) {
        super(context);
        init();
    }

    public ExoPlayerVideoView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ExoPlayerVideoView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        inflate(getContext(), R.layout.layout_exoplayer_video_view, this);

        playerView = findViewById(R.id.playerView);
        thumbnailView = findViewById(R.id.thumbnailView);
        progressBar = findViewById(R.id.progressBar);

        // Инициализируем ExoPlayer
        initializePlayer();

        // Скрыть видео изначально
        playerView.setVisibility(View.GONE);
        thumbnailView.setVisibility(View.VISIBLE);
        progressBar.setVisibility(View.GONE);
    }

    private void initializePlayer() {
        player = new SimpleExoPlayer.Builder(getContext()).build();
        playerView.setPlayer(player);

        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int playbackState) {
                if (playbackState == Player.STATE_READY) {
                    // Видео готово к воспроизведению
                    thumbnailView.setVisibility(View.GONE);
                    playerView.setVisibility(View.VISIBLE);
                    progressBar.setVisibility(View.GONE);
                } else if (playbackState == Player.STATE_BUFFERING) {
                    progressBar.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onPlayWhenReadyChanged(boolean playWhenReady, int reason) {
                // Обработка изменения состояния воспроизведения
            }
        });
    }

    public void setVideoURI(Uri uri) {
        if (player != null) {
            MediaItem mediaItem = MediaItem.fromUri(uri);
            player.setMediaItem(mediaItem);
            player.prepare();
        }
    }

    public void setThumbnail(String imageUri) {
        if (imageUri != null && !imageUri.isEmpty()) {
            Glide.with(getContext())
                    .load(imageUri)
                    .into(thumbnailView);
        } else {
            thumbnailView.setImageResource(R.drawable.ic_baseline_videocam_24);
        }
    }

    public void start() {
        if (player != null) {
            player.setPlayWhenReady(true);
        }
    }

    public void pause() {
        if (player != null) {
            player.setPlayWhenReady(false);
        }
    }

    public void stopPlayback() {
        if (player != null) {
            player.stop();
            player.release();
            player = null;
        }
    }

    public boolean isPlaying() {
        return player != null && player.isPlaying();
    }

    public void seekTo(long position) {
        if (player != null) {
            player.seekTo(position);
        }
    }

    public long getCurrentPosition() {
        return player != null ? player.getCurrentPosition() : 0;
    }

    public long getDuration() {
        return player != null ? player.getDuration() : 0;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stopPlayback();
    }
}