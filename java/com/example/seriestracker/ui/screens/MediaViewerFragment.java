package com.example.seriestracker.ui.screens;

import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

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

        // Инициализация UI
        initViews(view);

        // Получаем данные
        if (getArguments() != null) {
            mediaFiles = (List<MediaFile>) getArguments().getSerializable(ARG_MEDIA_FILES);
            currentPosition = getArguments().getInt(ARG_POSITION, 0);
        }

        // Показываем текущий медиафайл
        showMedia(currentPosition);

        // Настройка жестов
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

        // Кнопка закрытия
        closeButton.setOnClickListener(v -> {
            if (customVideoView.isPlaying()) {
                customVideoView.stopPlayback();
            }
            requireActivity().getSupportFragmentManager().popBackStack();
        });

        // Кнопка воспроизведения/паузы
        playPauseButton.setOnClickListener(v -> {
            if (customVideoView.isPlaying()) {
                customVideoView.pause();
                playPauseButton.setImageResource(R.drawable.ic_baseline_play_arrow_24);
                isVideoPlaying = false;
            } else {
                customVideoView.start();
                playPauseButton.setImageResource(R.drawable.ic_baseline_pause_24);
                isVideoPlaying = true;
                hideControlsDelayed();
            }
        });

        // Настройка CustomVideoView
        customVideoView.setOnPreparedListener(mp -> {
            progressBar.setVisibility(View.GONE);
            playPauseButton.setVisibility(View.VISIBLE);
            playPauseButton.setImageResource(R.drawable.ic_baseline_play_arrow_24);
        });

        customVideoView.setOnCompletionListener(mp -> {
            playPauseButton.setImageResource(R.drawable.ic_baseline_replay_24);
            isVideoPlaying = false;
            showControls();
        });

        customVideoView.setOnTouchListener((v, event) -> {
            if (event.getAction() == android.view.MotionEvent.ACTION_UP) {
                toggleControls();
            }
            return true;
        });
    }

    private void showMedia(int position) {
        if (mediaFiles == null || mediaFiles.isEmpty() || position < 0 || position >= mediaFiles.size()) {
            return;
        }

        MediaFile mediaFile = mediaFiles.get(position);
        currentPosition = position;

        // Показываем имя файла
        fileNameTextView.setText(mediaFile.getFileName());

        if (mediaFile.getFileType().equals("video")) {
            // Показываем видео с обложкой
            imageView.setVisibility(View.GONE);
            customVideoView.setVisibility(View.VISIBLE);
            videoControls.setVisibility(View.VISIBLE);

            progressBar.setVisibility(View.VISIBLE);
            customVideoView.setVideoURI(Uri.parse(mediaFile.getFileUri()));

            // Устанавливаем обложку (используем первую обложку или саму видео ссылку)
            customVideoView.setThumbnail(mediaFile.getFileUri());

            // Не запускаем автоматически

        } else {
            // Показываем изображение
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
        if (customVideoView.isPlaying()) {
            customVideoView.pause();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (customVideoView.isPlaying()) {
            customVideoView.stopPlayback();
        }
        if (hideControlsRunnable != null) {
            handler.removeCallbacks(hideControlsRunnable);
        }
    }
}