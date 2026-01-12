package com.example.seriestracker.ui.screens;

import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.example.seriestracker.R;
import com.example.seriestracker.data.entities.MediaFile;
import com.example.seriestracker.ui.custom.CustomVideoView;

import java.util.ArrayList;
import java.util.List;

public class MediaViewerFragment extends Fragment {

    private static final String ARG_MEDIA_FILES = "media_files";
    private static final String ARG_POSITION = "position";

    private CustomVideoView customVideoView;
    private ImageView imageView;
    private ImageButton closeButton;
    private ImageButton playPauseButton;
    private ProgressBar progressBar;
    private TextView fileNameTextView;
    private LinearLayout videoControls;

    private List<MediaFile> mediaFiles;
    private int currentPosition;
    private boolean isVideoPlaying = false;
    private Handler handler = new Handler();
    private Runnable hideControlsRunnable;

    public static MediaViewerFragment newInstance(ArrayList<MediaFile> mediaFiles, int position) {
        MediaViewerFragment fragment = new MediaViewerFragment();
        Bundle args = new Bundle();
        args.putSerializable(ARG_MEDIA_FILES, mediaFiles);
        args.putInt(ARG_POSITION, position);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_media_viewer, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        initViews(view);

        if (getArguments() != null) {
            mediaFiles = (List<MediaFile>) getArguments().getSerializable(ARG_MEDIA_FILES);
            currentPosition = getArguments().getInt(ARG_POSITION, 0);
        }

        showMedia(currentPosition);
        setupGestures(view);
    }

    private void initViews(View view) {
        customVideoView = view.findViewById(R.id.customVideoView);
        imageView = view.findViewById(R.id.imageView);
        closeButton = view.findViewById(R.id.closeButton);
        playPauseButton = view.findViewById(R.id.playPauseButton);
        progressBar = view.findViewById(R.id.progressBar);
        fileNameTextView = view.findViewById(R.id.fileNameTextView);
        videoControls = view.findViewById(R.id.videoControls);

        closeButton.setOnClickListener(v -> {
            if (customVideoView.isPlaying()) {
                customVideoView.stopPlayback();
            }
            requireActivity().getSupportFragmentManager().popBackStack();
        });

        playPauseButton.setOnClickListener(v -> {
            if (customVideoView.isPlaying()) {
                customVideoView.pause();
                playPauseButton.setImageResource(R.drawable.ic_baseline_play_arrow_24);
                isVideoPlaying = false;
                showControls();
            } else {
                // Проверяем, подготовлено ли видео
                if (customVideoView.getCurrentMediaPlayer() != null) {
                    customVideoView.start();
                    playPauseButton.setImageResource(R.drawable.ic_baseline_pause_24);
                    isVideoPlaying = true;
                    hideControlsDelayed();
                } else {
                    // Видео еще не подготовлено
                    Toast.makeText(getContext(), "Видео загружается...", Toast.LENGTH_SHORT).show();
                }
            }
        });

        // Настраиваем слушатели
        customVideoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                Log.d("MediaViewer", "Video prepared successfully - duration: " + mp.getDuration() + "ms");
                progressBar.setVisibility(View.GONE);
                customVideoView.setProgressBarVisible(false);
                playPauseButton.setVisibility(View.VISIBLE);
                playPauseButton.setImageResource(R.drawable.ic_baseline_play_arrow_24);

                // Автоматически запускаем воспроизведение
                customVideoView.start();
                playPauseButton.setImageResource(R.drawable.ic_baseline_pause_24);
                isVideoPlaying = true;

                // Скрываем контролы через 3 секунды
                hideControlsDelayed();
            }
        });

        customVideoView.setOnErrorListener(new MediaPlayer.OnErrorListener() {
            @Override
            public boolean onError(MediaPlayer mp, int what, int extra) {
                Log.e("MediaViewer", "Video error - what: " + what + ", extra: " + extra);
                progressBar.setVisibility(View.GONE);
                customVideoView.setProgressBarVisible(false);
                playPauseButton.setVisibility(View.VISIBLE);
                playPauseButton.setImageResource(R.drawable.ic_baseline_play_arrow_24);

                // Показываем сообщение об ошибке
                String errorMsg = "Ошибка воспроизведения видео";
                if (what == MediaPlayer.MEDIA_ERROR_UNKNOWN) {
                    errorMsg = "Неизвестная ошибка видео";
                } else if (what == MediaPlayer.MEDIA_ERROR_SERVER_DIED) {
                    errorMsg = "Сервер видео не отвечает";
                }
                Toast.makeText(getContext(), errorMsg, Toast.LENGTH_SHORT).show();
                return true;
            }
        });

        customVideoView.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                Log.d("MediaViewer", "Video completed");
                playPauseButton.setImageResource(R.drawable.ic_baseline_replay_24);
                isVideoPlaying = false;
                showControls();
            }
        });

        // Добавляем слушатель информации о видео
        customVideoView.setOnInfoListener(new MediaPlayer.OnInfoListener() {
            @Override
            public boolean onInfo(MediaPlayer mp, int what, int extra) {
                Log.d("MediaViewer", "Video info - what: " + what + ", extra: " + extra);
                switch (what) {
                    case MediaPlayer.MEDIA_INFO_BUFFERING_START:
                        Log.d("MediaViewer", "Buffering started");
                        progressBar.setVisibility(View.VISIBLE);
                        return true;
                    case MediaPlayer.MEDIA_INFO_BUFFERING_END:
                        Log.d("MediaViewer", "Buffering ended");
                        progressBar.setVisibility(View.GONE);
                        return true;
                    case MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START:
                        Log.d("MediaViewer", "Video rendering started");
                        progressBar.setVisibility(View.GONE);
                        return true;
                    case MediaPlayer.MEDIA_INFO_VIDEO_TRACK_LAGGING:
                        Log.d("MediaViewer", "Video track lagging");
                        return true;
                }
                return false;
            }
        });

        // Обработка касания видео
        customVideoView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    toggleControls();
                }
                return true;
            }
        });
    }

    private void showMedia(int position) {
        if (mediaFiles == null || mediaFiles.isEmpty() || position < 0 || position >= mediaFiles.size()) {
            return;
        }

        MediaFile mediaFile = mediaFiles.get(position);
        currentPosition = position;

        fileNameTextView.setText(mediaFile.getFileName());
        Log.d("MediaViewer", "Showing media at position " + position + ": " + mediaFile.getFileName());

        if (mediaFile.getFileType().equals("video")) {
            imageView.setVisibility(View.GONE);
            customVideoView.setVisibility(View.VISIBLE);
            videoControls.setVisibility(View.VISIBLE);

            progressBar.setVisibility(View.VISIBLE);
            playPauseButton.setVisibility(View.GONE); // Скрываем пока не готово

            // Логируем URI для отладки
            String videoUri = mediaFile.getFileUri();
            Log.d("MediaViewer", "Video URI: " + videoUri);

            // Проверяем URI
            if (videoUri == null || videoUri.isEmpty()) {
                Log.e("MediaViewer", "Video URI is null or empty");
                Toast.makeText(getContext(), "Некорректный URI видео", Toast.LENGTH_SHORT).show();
                progressBar.setVisibility(View.GONE);
                return;
            }

            Uri uri = Uri.parse(videoUri);
            if (uri != null) {
                try {
                    Log.d("MediaViewer", "Setting video URI: " + uri.toString());

                    // Сначала сбрасываем предыдущее видео
                    if (customVideoView.isPlaying()) {
                        customVideoView.stopPlayback();
                    }

                    // Сбрасываем состояние
                    customVideoView.showThumbnail();

                    // Устанавливаем URI
                    customVideoView.setVideoURI(uri);

                    // Устанавливаем обложку
                    customVideoView.setVideoThumbnail(videoUri);

                    // Показываем прогресс
                    customVideoView.setProgressBarVisible(true);

                } catch (Exception e) {
                    Log.e("MediaViewer", "Error setting video URI: " + e.getMessage(), e);
                    Toast.makeText(getContext(), "Ошибка загрузки видео: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    progressBar.setVisibility(View.GONE);
                }
            } else {
                Log.e("MediaViewer", "Invalid video URI");
                Toast.makeText(getContext(), "Неверный URI видео", Toast.LENGTH_SHORT).show();
                progressBar.setVisibility(View.GONE);
            }

        } else {
            // Показ изображения
            Log.d("MediaViewer", "Showing image");
            customVideoView.setVisibility(View.GONE);
            videoControls.setVisibility(View.GONE);
            imageView.setVisibility(View.VISIBLE);

            Glide.with(this)
                    .load(Uri.parse(mediaFile.getFileUri()))
                    .into(imageView);
        }
    }

    private void setupGestures(View view) {
        // Жест свайпа вправо (предыдущий файл)
        view.setOnTouchListener(new OnSwipeTouchListener(requireContext()) {
            @Override
            public void onSwipeRight() {
                if (currentPosition > 0) {
                    Log.d("MediaViewer", "Swipe right - going to previous media");
                    // Останавливаем текущее видео
                    if (customVideoView.isPlaying()) {
                        customVideoView.pause();
                    }
                    showMedia(currentPosition - 1);
                }
            }

            @Override
            public void onSwipeLeft() {
                if (currentPosition < mediaFiles.size() - 1) {
                    Log.d("MediaViewer", "Swipe left - going to next media");
                    // Останавливаем текущее видео
                    if (customVideoView.isPlaying()) {
                        customVideoView.pause();
                    }
                    showMedia(currentPosition + 1);
                }
            }

            @Override
            public void onSwipeTop() {
                // Можно добавить другие жесты
            }

            @Override
            public void onSwipeBottom() {
                // Можно добавить другие жесты
            }
        });
    }

    private void toggleControls() {
        Log.d("MediaViewer", "Toggle controls - currently visible: " + (videoControls.getVisibility() == View.VISIBLE));
        if (videoControls.getVisibility() == View.VISIBLE) {
            hideControls();
        } else {
            showControls();
            if (isVideoPlaying) {
                hideControlsDelayed();
            }
        }
    }

    private void showControls() {
        videoControls.setVisibility(View.VISIBLE);
        fileNameTextView.setVisibility(View.VISIBLE);
        closeButton.setVisibility(View.VISIBLE);

        if (hideControlsRunnable != null) {
            handler.removeCallbacks(hideControlsRunnable);
        }
    }

    private void hideControls() {
        videoControls.setVisibility(View.GONE);
        fileNameTextView.setVisibility(View.GONE);
        closeButton.setVisibility(View.GONE);
    }

    private void hideControlsDelayed() {
        if (hideControlsRunnable != null) {
            handler.removeCallbacks(hideControlsRunnable);
        }

        hideControlsRunnable = () -> {
            if (isVideoPlaying) {
                hideControls();
            }
        };

        handler.postDelayed(hideControlsRunnable, 3000); // Скрыть через 3 секунды
    }

    @Override
    public void onPause() {
        super.onPause();
        Log.d("MediaViewer", "Fragment paused");
        if (customVideoView.isPlaying()) {
            customVideoView.pause();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        Log.d("MediaViewer", "Fragment view destroyed");
        if (customVideoView.isPlaying()) {
            customVideoView.stopPlayback();
        }
        if (hideControlsRunnable != null) {
            handler.removeCallbacks(hideControlsRunnable);
        }
    }
}