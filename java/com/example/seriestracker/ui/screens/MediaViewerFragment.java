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

import java.io.File;
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

    private OnSwipeTouchListener swipeListener;

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
                if (customVideoView.getCurrentMediaPlayer() != null) {
                    customVideoView.start();
                    playPauseButton.setImageResource(R.drawable.ic_baseline_pause_24);
                    isVideoPlaying = true;
                    hideControlsDelayed();
                } else {
                    Toast.makeText(getContext(), "Видео загружается...", Toast.LENGTH_SHORT).show();
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
                playPauseButton.setImageResource(R.drawable.ic_baseline_play_arrow_24);
                isVideoPlaying = false;
                showControls();
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

        // Настраиваем обработчик касаний для VideoView
        customVideoView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                // Сначала передаем событие жестовому детектору
                if (swipeListener != null) {
                    boolean handledByGesture = swipeListener.onTouch(v, event);

                    // Если жесты не обработали событие, то обрабатываем касания
                    if (!handledByGesture && event.getAction() == MotionEvent.ACTION_UP) {
                        toggleControls();
                    }
                    return true; // Всегда возвращаем true, чтобы получать все события
                }

                // Если жестовый детектор не инициализирован, обрабатываем касания
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    toggleControls();
                }
                return true;
            }
        });
    }

    private void setupGestures(View view) {
        // Создаем анонимный класс OnSwipeTouchListener
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

            // УБИРАЕМ @Override - это НЕ переопределение, а реализация абстрактного метода
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

        // Устанавливаем слушатель для всей view
        view.setOnTouchListener(swipeListener);
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
            playPauseButton.setVisibility(View.GONE);

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
            Log.d("MediaViewer", "Showing image");
            customVideoView.setVisibility(View.GONE);
            videoControls.setVisibility(View.GONE);
            imageView.setVisibility(View.VISIBLE);

            try {
                Glide.with(this)
                        .load(Uri.parse(mediaFile.getFileUri()))
                        .error(R.drawable.ic_baseline_image_24)
                        .into(imageView);
            } catch (Exception e) {
                imageView.setImageResource(R.drawable.ic_baseline_image_24);
                Log.e("MediaViewer", "Error loading image: " + e.getMessage(), e);
            }
        }
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