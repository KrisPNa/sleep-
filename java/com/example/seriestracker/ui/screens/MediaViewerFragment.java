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
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
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

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MediaViewerFragment extends Fragment {

    private static final String ARG_MEDIA_FILES = "media_files";
    private static final String ARG_POSITION = "position";

    private FrameLayout videoContainer;
    private CustomVideoView customVideoView;
    private ImageView imageView;
    private ImageButton closeButton;
    private ImageButton playPauseButton;
    private ImageButton rewindButton;
    private ImageButton fastForwardButton;
    private ImageButton rotateButton;
    private ProgressBar progressBar;
    private TextView fileNameTextView;
    private LinearLayout videoControls;
    private SeekBar seekBar;

    private List<MediaFile> mediaFiles;
    private int currentPosition;
    private boolean isVideoPlaying = false;
    private Handler handler = new Handler();
    private Runnable updateSeekBarRunnable;
    private Runnable hideControlsRunnable;

    private OnSwipeTouchListener swipeListener;

    // Переменная для управления поворотом
    private float rotationAngle = 0f;

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
        videoContainer = view.findViewById(R.id.videoContainer);
        customVideoView = view.findViewById(R.id.customVideoView);
        imageView = view.findViewById(R.id.imageView);
        closeButton = view.findViewById(R.id.closeButton);
        playPauseButton = view.findViewById(R.id.playPauseButton);
        rewindButton = view.findViewById(R.id.rewindButton);
        fastForwardButton = view.findViewById(R.id.fastForwardButton);
        rotateButton = view.findViewById(R.id.rotateButton);
        progressBar = view.findViewById(R.id.progressBar);
        fileNameTextView = view.findViewById(R.id.fileNameTextView);
        videoControls = view.findViewById(R.id.videoControls);
        seekBar = view.findViewById(R.id.seekBar);

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
                removeUpdateSeekBarCallback();
            } else {
                if (customVideoView.getCurrentMediaPlayer() != null) {
                    customVideoView.start();
                    playPauseButton.setImageResource(R.drawable.ic_baseline_pause_24);
                    isVideoPlaying = true;
                    hideControlsDelayed();
                    startUpdateSeekBarCallback();
                } else {
                    Toast.makeText(getContext(), "Видео загружается...", Toast.LENGTH_SHORT).show();
                }
            }
        });

        rewindButton.setOnClickListener(v -> {
            if (customVideoView.getCurrentMediaPlayer() != null) {
                int currentPosition = customVideoView.getCurrentPosition();
                int newPosition = Math.max(0, currentPosition - 10000);
                customVideoView.seekTo(newPosition);
                seekBar.setProgress(newPosition);
                if (!customVideoView.isPlaying()) {
                    showControls();
                }
            }
        });

        fastForwardButton.setOnClickListener(v -> {
            if (customVideoView.getCurrentMediaPlayer() != null) {
                int currentPosition = customVideoView.getCurrentPosition();
                int duration = customVideoView.getDuration();
                int newPosition = Math.min(duration, currentPosition + 10000);
                customVideoView.seekTo(newPosition);
                seekBar.setProgress(newPosition);
                if (!customVideoView.isPlaying()) {
                    showControls();
                }
            }
        });

        // Обработчик для кнопки поворота
        rotateButton.setOnClickListener(v -> {
            rotateMedia();
        });

        // Обработчик для SeekBar
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && customVideoView.getCurrentMediaPlayer() != null) {
                    customVideoView.seekTo(progress);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                if (customVideoView.isPlaying()) {
                    customVideoView.pause();
                }
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if (!customVideoView.isPlaying() && isVideoPlaying) {
                    customVideoView.start();
                }
            }
        });

        customVideoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                Log.d("MediaViewer", "Video prepared successfully - duration: " + mp.getDuration() + "ms");
                progressBar.setVisibility(View.GONE);
                customVideoView.setProgressBarVisible(false);
                playPauseButton.setVisibility(View.VISIBLE);
                rewindButton.setVisibility(View.VISIBLE);
                fastForwardButton.setVisibility(View.VISIBLE);
                playPauseButton.setImageResource(R.drawable.ic_baseline_play_arrow_24);

                // Настройка SeekBar для видео
                seekBar.setVisibility(View.VISIBLE);
                seekBar.setMax(mp.getDuration());
                seekBar.setProgress(0);

                isVideoPlaying = false;
                showControls();

                // Применяем текущий угол поворота к видео
                applyRotation();
                startUpdateSeekBarCallback();
            }
        });

        customVideoView.setOnErrorListener(new MediaPlayer.OnErrorListener() {
            @Override
            public boolean onError(MediaPlayer mp, int what, int extra) {
                Log.e("MediaViewer", "Video error - what: " + what + ", extra: " + extra);
                progressBar.setVisibility(View.GONE);
                customVideoView.setProgressBarVisible(false);
                playPauseButton.setVisibility(View.VISIBLE);
                rewindButton.setVisibility(View.VISIBLE);
                fastForwardButton.setVisibility(View.VISIBLE);
                playPauseButton.setImageResource(R.drawable.ic_baseline_play_arrow_24);

                // Показываем SeekBar даже при ошибке видео
                seekBar.setVisibility(View.VISIBLE);

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
                removeUpdateSeekBarCallback();
            }
        });

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

        // Обработчик касаний для VideoView
        customVideoView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (swipeListener != null) {
                    boolean handledByGesture = swipeListener.onTouch(v, event);
                    if (!handledByGesture && event.getAction() == MotionEvent.ACTION_UP) {
                        toggleControls();
                    }
                    return true;
                }

                if (event.getAction() == MotionEvent.ACTION_UP) {
                    toggleControls();
                }
                return true;
            }
        });
    }

    private void rotateMedia() {
        // Изменяем угол поворота
        rotationAngle += 90f;
        if (rotationAngle >= 360f) {
            rotationAngle = 0f;
        }

        // Применяем поворот
        applyRotation();

        Log.d("MediaViewer", "Rotated to: " + rotationAngle + " degrees");
    }

    private void applyRotation() {
        if (customVideoView.getVisibility() == View.VISIBLE) {
            // Используем метод из CustomVideoView для поворота видео
            customVideoView.setVideoRotation(rotationAngle);

        } else if (imageView.getVisibility() == View.VISIBLE) {
            // Поворачиваем изображение
            imageView.setRotation(rotationAngle);

            // Устанавливаем режим масштабирования для изображений
            imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        }
    }

    private void setupGestures(View view) {
        swipeListener = new OnSwipeTouchListener(requireContext()) {
            @Override
            public void onSwipeRight() {
                if (currentPosition > 0) {
                    Log.d("MediaViewer", "Swipe right - going to previous media");
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
                    if (customVideoView.isPlaying()) {
                        customVideoView.pause();
                    }
                    showMedia(currentPosition + 1);
                }
            }

            @Override
            public void onSwipeBottom() {
                if (customVideoView.isPlaying()) {
                    customVideoView.stopPlayback();
                }
                requireActivity().getSupportFragmentManager().popBackStack();
            }

            public void onDoubleTapPerformed() {
                if (customVideoView.getVisibility() == View.VISIBLE) {
                    if (customVideoView.isPlaying()) {
                        customVideoView.pause();
                        playPauseButton.setImageResource(R.drawable.ic_baseline_play_arrow_24);
                        isVideoPlaying = false;
                        showControls();
                    } else {
                        if (customVideoView.getCurrentMediaPlayer() != null) {
                            customVideoView.start();
                            playPauseButton.setImageResource(R.drawable.ic_baseline_pause_24);
                            isVideoPlaying = true;
                            hideControlsDelayed();
                        }
                    }
                }
            }
        };

        view.setOnTouchListener(swipeListener);
    }

    private void showMedia(int position) {
        if (mediaFiles == null || mediaFiles.isEmpty() || position < 0 || position >= mediaFiles.size()) {
            return;
        }

        removeUpdateSeekBarCallback();

        MediaFile mediaFile = mediaFiles.get(position);
        currentPosition = position;

        fileNameTextView.setText(mediaFile.getFileName());
        Log.d("MediaViewer", "Showing media at position " + position + ": " + mediaFile.getFileName());

        // Сброс параметров перед показом нового медиа
        resetViewParameters();

        if (mediaFile.getFileType().equals("video")) {
            // Показываем видео
            imageView.setVisibility(View.GONE);
            customVideoView.setVisibility(View.VISIBLE);
            videoControls.setVisibility(View.VISIBLE);

            // Настраиваем элементы управления для видео
            rotateButton.setVisibility(View.VISIBLE);
            progressBar.setVisibility(View.VISIBLE);
            playPauseButton.setVisibility(View.GONE);
            rewindButton.setVisibility(View.GONE);
            fastForwardButton.setVisibility(View.GONE);
            seekBar.setVisibility(View.GONE);

            String videoUri = mediaFile.getFileUri();
            Log.d("MediaViewer", "Video URI: " + videoUri);

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

                    if (customVideoView.isPlaying()) {
                        customVideoView.stopPlayback();
                    }

                    customVideoView.setProgressBarVisible(true);
                    customVideoView.setVideoThumbnail(videoUri);

                    if ("file".equals(uri.getScheme())) {
                        File file = new File(uri.getPath());
                        if (!file.exists()) {
                            Log.e("MediaViewer", "Video file does not exist: " + uri.getPath());
                            Toast.makeText(getContext(), "Файл видео не найден", Toast.LENGTH_SHORT).show();
                            progressBar.setVisibility(View.GONE);
                            return;
                        }
                    }

                    customVideoView.setVideoURI(uri);

                    // Применяем текущий поворот
                    applyRotation();

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
            // Показываем изображение
            Log.d("MediaViewer", "Showing image");
            customVideoView.setVisibility(View.GONE);
            videoControls.setVisibility(View.VISIBLE);

            // Настраиваем элементы управления для изображения
            playPauseButton.setVisibility(View.GONE);
            rewindButton.setVisibility(View.GONE);
            fastForwardButton.setVisibility(View.GONE);
            rotateButton.setVisibility(View.VISIBLE);
            seekBar.setVisibility(View.GONE);
            imageView.setVisibility(View.VISIBLE);

            try {
                Glide.with(this)
                        .load(Uri.parse(mediaFile.getFileUri()))
                        .error(R.drawable.ic_baseline_image_24)
                        .into(imageView);

                // Применяем текущий поворот
                applyRotation();

            } catch (Exception e) {
                imageView.setImageResource(R.drawable.ic_baseline_image_24);
                Log.e("MediaViewer", "Error loading image: " + e.getMessage(), e);
                applyRotation();
            }
        }
    }

    private void resetViewParameters() {
        // Сбрасываем параметры изображения
        ViewGroup.LayoutParams imageParams = imageView.getLayoutParams();
        imageParams.width = ViewGroup.LayoutParams.MATCH_PARENT;
        imageParams.height = ViewGroup.LayoutParams.MATCH_PARENT;
        imageView.setLayoutParams(imageParams);
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);

        // Сбрасываем поворот для CustomVideoView (он сам управляет своим состоянием)
        // Не вызываем setScaleType для CustomVideoView - его не существует
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

        handler.postDelayed(hideControlsRunnable, 3000);
    }

    private void startUpdateSeekBarCallback() {
        if (updateSeekBarRunnable == null) {
            updateSeekBarRunnable = new Runnable() {
                @Override
                public void run() {
                    if (customVideoView != null && customVideoView.getCurrentMediaPlayer() != null && isVideoPlaying) {
                        int currentPosition = customVideoView.getCurrentPosition();
                        seekBar.setProgress(currentPosition);
                        handler.postDelayed(this, 1000);
                    }
                }
            };
        }

        removeUpdateSeekBarCallback();
        handler.post(updateSeekBarRunnable);
    }

    private void removeUpdateSeekBarCallback() {
        if (updateSeekBarRunnable != null) {
            handler.removeCallbacks(updateSeekBarRunnable);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        Log.d("MediaViewer", "Fragment paused");
        if (customVideoView.isPlaying()) {
            customVideoView.pause();
        }
        removeUpdateSeekBarCallback();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        Log.d("MediaViewer", "Fragment view destroyed");
        if (customVideoView.isPlaying()) {
            customVideoView.stopPlayback();
        }
        removeUpdateSeekBarCallback();

        if (hideControlsRunnable != null) {
            handler.removeCallbacks(hideControlsRunnable);
        }
    }
}