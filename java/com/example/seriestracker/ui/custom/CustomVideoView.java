package com.example.seriestracker.ui.custom;

import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaPlayer;
import android.net.Uri;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.VideoView;

import com.bumptech.glide.Glide;
import com.example.seriestracker.R;

import java.io.File;
import java.util.HashMap;

public class CustomVideoView extends FrameLayout {

    private VideoView videoView;
    private ImageView thumbnailView;
    private ProgressBar progressBar;
    private MediaPlayer.OnPreparedListener preparedListener;
    private MediaPlayer.OnInfoListener infoListener;
    private MediaPlayer.OnErrorListener errorListener;
    private MediaPlayer currentMediaPlayer;
    private float currentRotation = 0f; // Добавляем переменную для поворота

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

        // Добавляем отладочные слушатели по умолчанию
        videoView.setOnErrorListener(new MediaPlayer.OnErrorListener() {
            @Override
            public boolean onError(MediaPlayer mp, int what, int extra) {
                Log.e("CustomVideoView", "VideoView error - what: " + what + ", extra: " + extra);
                // Скрыть прогресс бар при ошибке
                progressBar.setVisibility(View.GONE);
                // Показать миниатюру обратно при ошибке
                thumbnailView.setVisibility(View.VISIBLE);
                videoView.setVisibility(View.GONE);
                if (errorListener != null) {
                    return errorListener.onError(mp, what, extra);
                }
                return false;
            }
        });

        videoView.setOnInfoListener(new MediaPlayer.OnInfoListener() {
            @Override
            public boolean onInfo(MediaPlayer mp, int what, int extra) {
                Log.d("CustomVideoView", "VideoView info - what: " + what + ", extra: " + extra);
                if (infoListener != null) {
                    return infoListener.onInfo(mp, what, extra);
                }
                return false;
            }
        });

        // Скрыть видео изначально
        videoView.setVisibility(View.GONE);
        thumbnailView.setVisibility(View.VISIBLE);
        progressBar.setVisibility(View.GONE);
    }

    public void setVideoURI(Uri uri) {
        if (uri == null) {
            Log.e("CustomVideoView", "setVideoURI called with null URI");
            return;
        }
        Log.d("CustomVideoView", "Setting video URI: " + uri.toString());

        // Проверяем, является ли URI файлом из внутреннего хранилища
        if ("file".equals(uri.getScheme())) {
            // Проверяем существование файла
            File file = new File(uri.getPath());
            if (!file.exists()) {
                Log.e("CustomVideoView", "Video file does not exist: " + uri.getPath());
                // Показываем ошибку
                thumbnailView.setImageResource(R.drawable.ic_baseline_error_24);
                thumbnailView.setVisibility(View.VISIBLE);
                videoView.setVisibility(View.GONE);
                return;
            }
        }
        // Сбрасываем обложку перед началом воспроизведения
        thumbnailView.setVisibility(View.GONE);
        videoView.setVisibility(View.VISIBLE);
        videoView.setVideoURI(uri);
        // Автоматически начинаем подготовку видео
        videoView.requestFocus();
    }

    // Метод для установки поворота
    public void setVideoRotation(float rotation) {
        this.currentRotation = rotation;
        videoView.setRotation(rotation);
        thumbnailView.setRotation(rotation);

        // После поворота нужно пересчитать центрирование
        if (currentMediaPlayer != null) {
            centerVideo(currentMediaPlayer);
        }
    }

    public float getVideoRotation() {
        return currentRotation;
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
            Log.d("CustomVideoView", "Video prepared - width: " + mp.getVideoWidth() + ", height: " + mp.getVideoHeight());
            currentMediaPlayer = mp;

            // Применяем текущий поворот
            videoView.setRotation(currentRotation);

            if (preparedListener != null) {
                preparedListener.onPrepared(mp);
            }
            // Показать видео, скрыть превью
            thumbnailView.setVisibility(View.GONE);
            videoView.setVisibility(View.VISIBLE);
            progressBar.setVisibility(View.GONE);

            // Центрировать видео с учетом поворота
            centerVideo(mp);
        });
    }

    public void setOnErrorListener(MediaPlayer.OnErrorListener listener) {
        this.errorListener = listener;
    }

    public void setOnInfoListener(MediaPlayer.OnInfoListener listener) {
        this.infoListener = listener;
    }

    private void centerVideo(MediaPlayer mp) {
        videoView.post(() -> {
            if (mp != null) {
            // Проверяем, что MediaPlayer находится в состоянии Prepared или Started
            // перед вызовом getVideoWidth() и getVideoHeight()
            try {
                int videoWidth = mp.getVideoWidth();
                int videoHeight = mp.getVideoHeight();
                Log.d("CustomVideoView", "Video dimensions - width: " + videoWidth + ", height: " + videoHeight);

                if (videoWidth > 0 && videoHeight > 0) {
                    // Учитываем поворот - если видео повернуто на 90 или 270 градусов,
                    // меняем ширину и высоту местами для правильного расчета
                    boolean isRotated90 = Math.abs(currentRotation % 180) == 90;
                    if (isRotated90) {
                        // Меняем ширину и высоту местами для повернутого видео
                        int temp = videoWidth;
                        videoWidth = videoHeight;
                        videoHeight = temp;
                    }

                    // Получаем размеры VideoView
                    int viewWidth = videoView.getWidth();
                    int viewHeight = videoView.getHeight();

                    Log.d("CustomVideoView", "View dimensions - width: " + viewWidth + ", height: " + viewHeight + ", isRotated90: " + isRotated90);

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

                    Log.d("CustomVideoView", "Video centered - padding: left=" + left + ", top=" + top + ", scale=" + scale);
                }
            } catch (IllegalStateException e) {
                Log.e("CustomVideoView", "MediaPlayer is not in the proper state to get video dimensions", e);
                // MediaPlayer может быть в неправильном состоянии, просто выходим без ошибки
                return;
            }
        }
    });
}

public void setOnCompletionListener(MediaPlayer.OnCompletionListener listener) {
    videoView.setOnCompletionListener(listener);
}

public void start() {
    Log.d("CustomVideoView", "Starting video playback");
    videoView.start();
}

public void pause() {
    Log.d("CustomVideoView", "Pausing video playback");
    videoView.pause();
}

public void stopPlayback() {
    Log.d("CustomVideoView", "Stopping video playback");
    videoView.stopPlayback();
    currentMediaPlayer = null;
    // Показываем превью после остановки
    showThumbnail();
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
    videoView.stopPlayback();
}

public void hideThumbnail() {
    thumbnailView.setVisibility(View.GONE);
    videoView.setVisibility(View.VISIBLE);
}

public void setOnTouchListener(OnTouchListener listener) {
    videoView.setOnTouchListener(listener);
    thumbnailView.setOnTouchListener(listener);
}

// Метод для установки превью из видео файла (если нужно)
public void setVideoThumbnail(String videoUri) {
    // Запускаем в фоновом потоке, чтобы не блокировать UI
    new Thread(() -> {
        try {
            Log.d("CustomVideoView", "Setting video thumbnail from URI: " + videoUri);

            // Используем MediaMetadataRetriever для получения превью
            android.media.MediaMetadataRetriever retriever = new android.media.MediaMetadataRetriever();
            retriever.setDataSource(videoUri, new HashMap<String, String>());

            // Получаем превью на 1 секунде
            Bitmap bitmap = retriever.getFrameAtTime(1000000); // 1 секунда в микросекундах

            if (bitmap != null) {
                // Возвращаемся в UI поток для обновления ImageView
                post(() -> {
                    thumbnailView.setImageBitmap(bitmap);
                    // Применяем поворот к превью
                    thumbnailView.setRotation(currentRotation);
                    Log.d("CustomVideoView", "Video thumbnail loaded successfully");
                });
            } else {
                // Если не удалось получить превью, показываем иконку видео
                post(() -> {
                    thumbnailView.setImageResource(R.drawable.ic_baseline_videocam_24);
                    // Применяем поворот к превью
                    thumbnailView.setRotation(currentRotation);
                    Log.d("CustomVideoView", "Video thumbnail not available, using default icon");
                });
            }

            retriever.release();
        } catch (Exception e) {
            Log.e("CustomVideoView", "Error loading video thumbnail: " + e.getMessage(), e);
            post(() -> {
                thumbnailView.setImageResource(R.drawable.ic_baseline_videocam_24);
                thumbnailView.setRotation(currentRotation);
            });
        }
    }).start();
}

// Альтернативный метод с использованием URI
public void setVideoThumbnail(Uri videoUri) {
    if (videoUri == null) return;
    setVideoThumbnail(videoUri.toString());
}

// Метод для сброса состояния
public void reset() {
    stopPlayback();
    showThumbnail();
    progressBar.setVisibility(View.GONE);
    currentMediaPlayer = null;
    currentRotation = 0f;
    videoView.setRotation(0f);
    thumbnailView.setRotation(0f);
    videoView.setPadding(0, 0, 0, 0);
}
}
