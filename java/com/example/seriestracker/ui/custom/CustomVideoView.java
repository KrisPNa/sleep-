package com.example.seriestracker.ui.custom;

import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaPlayer;
import android.net.Uri;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.VideoView;

import com.bumptech.glide.Glide;
import com.example.seriestracker.R;

import java.util.HashMap;

public class CustomVideoView extends FrameLayout {

    private VideoView videoView;
    private ImageView thumbnailView;
    private ProgressBar progressBar;
    private MediaPlayer.OnPreparedListener preparedListener;
    private MediaPlayer currentMediaPlayer;

    public CustomVideoView(Context context) {
        super(context);
        init();
    }

    public CustomVideoView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public CustomVideoView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        inflate(getContext(), R.layout.layout_custom_video_view, this);

        videoView = findViewById(R.id.videoView);
        thumbnailView = findViewById(R.id.thumbnailView);
        progressBar = findViewById(R.id.progressBar);

        // Скрыть видео изначально
        videoView.setVisibility(View.GONE);
        thumbnailView.setVisibility(View.VISIBLE);
        progressBar.setVisibility(View.GONE);
    }

    public void setVideoURI(Uri uri) {
        videoView.setVideoURI(uri);
    }

    public void setThumbnail(String imageUri) {
        if (imageUri != null && !imageUri.isEmpty()) {
            Glide.with(getContext())
                    .load(imageUri)
                    .into(thumbnailView);
        }
    }

    public void setOnPreparedListener(MediaPlayer.OnPreparedListener listener) {
        this.preparedListener = listener;
        videoView.setOnPreparedListener(mp -> {
            currentMediaPlayer = mp;
            if (preparedListener != null) {
                preparedListener.onPrepared(mp);
            }
            // Показать видео, скрыть превью
            thumbnailView.setVisibility(View.GONE);
            videoView.setVisibility(View.VISIBLE);
            progressBar.setVisibility(View.GONE);

            // Центрировать видео
            centerVideo(mp);
        });
    }

    private void centerVideo() {
        videoView.post(() -> {
            MediaPlayer mp = videoView.getCurrentMediaPlayer();
            if (mp != null) {
                // Получаем размеры видео
                int videoWidth = mp.getVideoWidth();
                int videoHeight = mp.getVideoHeight();

                if (videoWidth > 0 && videoHeight > 0) {
                    // Получаем размеры VideoView
                    int viewWidth = videoView.getWidth();
                    int viewHeight = videoView.getHeight();

                    // Вычисляем масштаб
                    float widthRatio = (float) viewWidth / videoWidth;
                    float heightRatio = (float) viewHeight / videoHeight;
                    float scale = Math.min(widthRatio, heightRatio);

                    // Вычисляем новые размеры
                    int newWidth = (int) (videoWidth * scale);
                    int newHeight = (int) (videoHeight * scale);

                    // Центрируем
                    int left = (viewWidth - newWidth) / 2;
                    int top = (viewHeight - newHeight) / 2;

                    // Устанавливаем отступы
                    videoView.setPadding(left, top, left, top);
                }
            }
        });
    }

    public void setOnCompletionListener(MediaPlayer.OnCompletionListener listener) {
        videoView.setOnCompletionListener(listener);
    }

    public void start() {
        videoView.start();
    }

    public void pause() {
        videoView.pause();
    }

    public void stopPlayback() {
        videoView.stopPlayback();
        currentMediaPlayer = null;
    }

    public boolean isPlaying() {
        return videoView.isPlaying();
    }


    public MediaPlayer getCurrentMediaPlayer() {
        return currentMediaPlayer;
    }

    public void seekTo(int position) {
        videoView.seekTo(position);
    }

    public int getCurrentPosition() {
        return videoView.getCurrentPosition();
    }

    public int getDuration() {
        return videoView.getDuration();
    }

    public void setProgressBarVisible(boolean visible) {
        progressBar.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    public void showThumbnail() {
        thumbnailView.setVisibility(View.VISIBLE);
        videoView.setVisibility(View.GONE);
    }

    public void hideThumbnail() {
        thumbnailView.setVisibility(View.GONE);
        videoView.setVisibility(View.VISIBLE);
    }

    public void setOnTouchListener(OnTouchListener listener) {
        videoView.setOnTouchListener(listener);
        thumbnailView.setOnTouchListener(listener);
    }

    private void setVideoThumbnail(String videoUri, ImageView thumbnailView) {
        try {
            // Используем MediaMetadataRetriever для получения превью
            android.media.MediaMetadataRetriever retriever = new android.media.MediaMetadataRetriever();
            retriever.setDataSource(videoUri, new HashMap<String, String>());

            // Получаем превью на 1 секунде
            Bitmap bitmap = retriever.getFrameAtTime(1000000); // 1 секунда в микросекундах

            if (bitmap != null) {
                thumbnailView.setImageBitmap(bitmap);
            } else {
                // Если не удалось получить превью, показываем иконку видео
                thumbnailView.setImageResource(R.drawable.ic_baseline_videocam_24);
            }

            retriever.release();
        } catch (Exception e) {
            e.printStackTrace();
            thumbnailView.setImageResource(R.drawable.ic_baseline_videocam_24);
        }
    }
}