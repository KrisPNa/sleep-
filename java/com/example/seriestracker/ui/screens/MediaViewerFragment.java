package com.example.seriestracker.ui.screens;

import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.example.seriestracker.R;
import com.example.seriestracker.data.entities.MediaFile;
import com.example.seriestracker.ui.adapters.MediaPagerAdapter;
import com.example.seriestracker.ui.custom.SwipeToDismissLayout;
import com.example.seriestracker.ui.custom.ZoomPanLayout;
import com.example.seriestracker.utils.MediaStorageHelper;
import com.example.seriestracker.utils.SystemUiHelper;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.ui.PlayerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MediaViewerFragment extends Fragment {

    private static final String ARG_MEDIA_FILES = "media_files";
    private static final String ARG_POSITION = "position";
    private static final long SKIP_MS = 10_000L;
    private static final long HIDE_CONTROLS_DELAY_MS = 3_000L;
    private static final long PROGRESS_UPDATE_MS = 500L;

    private SwipeToDismissLayout swipeDismissLayout;
    private ViewPager2 mediaViewPager;
    private ProgressBar bufferingProgress;
    private LinearLayout topBar;
    private LinearLayout bottomBar;
    private LinearLayout seekBarRow;
    private TextView titleTextView;
    private TextView pageIndicatorText;
    private TextView currentTimeText;
    private TextView totalTimeText;
    private SeekBar seekBar;
    private ImageButton closeButton;
    private ImageButton resetZoomButton;
    private ImageButton playPauseButton;
    private ImageButton rewindButton;
    private ImageButton forwardButton;

    private MediaPagerAdapter pagerAdapter;
    private List<MediaFile> mediaFiles;
    private int currentPosition;
    private ExoPlayer player;
    private PlayerView attachedPlayerView;
    private boolean controlsVisible = true;
    private boolean userSeeking;
    private boolean isMediaZoomed;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable hideControlsRunnable;
    private Runnable progressRunnable;

    public static MediaViewerFragment newInstance(ArrayList<MediaFile> mediaFiles, int position) {
        MediaViewerFragment fragment = new MediaViewerFragment();
        Bundle args = new Bundle();
        args.putSerializable(ARG_MEDIA_FILES, mediaFiles);
        args.putInt(ARG_POSITION, position);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_media_viewer, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        bindViews(view);
        readArguments();
        setupPlayer();
        setupControls();
        setupPager();
    }

    private void bindViews(View view) {
        swipeDismissLayout = view.findViewById(R.id.swipeDismissLayout);
        mediaViewPager = view.findViewById(R.id.mediaViewPager);
        bufferingProgress = view.findViewById(R.id.bufferingProgress);
        topBar = view.findViewById(R.id.topBar);
        bottomBar = view.findViewById(R.id.bottomBar);
        seekBarRow = view.findViewById(R.id.seekBarRow);
        titleTextView = view.findViewById(R.id.titleTextView);
        pageIndicatorText = view.findViewById(R.id.pageIndicatorText);
        currentTimeText = view.findViewById(R.id.currentTimeText);
        totalTimeText = view.findViewById(R.id.totalTimeText);
        seekBar = view.findViewById(R.id.seekBar);
        closeButton = view.findViewById(R.id.closeButton);
        resetZoomButton = view.findViewById(R.id.resetZoomButton);
        playPauseButton = view.findViewById(R.id.playPauseButton);
        rewindButton = view.findViewById(R.id.rewindButton);
        forwardButton = view.findViewById(R.id.forwardButton);
    }

    @SuppressWarnings("unchecked")
    private void readArguments() {
        Bundle args = getArguments();
        if (args != null) {
            mediaFiles = (List<MediaFile>) args.getSerializable(ARG_MEDIA_FILES);
            currentPosition = args.getInt(ARG_POSITION, 0);
        }
        if (mediaFiles == null) {
            mediaFiles = new ArrayList<>();
        }
        if (currentPosition < 0 || currentPosition >= mediaFiles.size()) {
            currentPosition = 0;
        }
    }

    private void setupPager() {
        swipeDismissLayout.setOnDismissListener(this::closeViewer);

        pagerAdapter = new MediaPagerAdapter(mediaFiles);
        pagerAdapter.setZoomChangeListener(this::updateZoomState);
        mediaViewPager.setAdapter(pagerAdapter);
        mediaViewPager.setOffscreenPageLimit(1);
        mediaViewPager.setCurrentItem(currentPosition, false);

        mediaViewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                if (position != currentPosition) {
                    showPage(position, false);
                }
            }
        });

        mediaViewPager.post(() -> showPage(currentPosition, true));
    }

    private void setupPlayer() {
        player = new ExoPlayer.Builder(requireContext()).build();
        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int playbackState) {
                bufferingProgress.setVisibility(
                        playbackState == Player.STATE_BUFFERING ? View.VISIBLE : View.GONE);
                updatePlayPauseIcon();
                if (playbackState == Player.STATE_READY) {
                    updateDurationUi();
                }
                if (playbackState == Player.STATE_ENDED) {
                    showControls();
                    updateImmersiveMode(false);
                    playPauseButton.setImageResource(R.drawable.ic_baseline_replay_24);
                }
            }

            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                updatePlayPauseIcon();
                updateImmersiveMode(isPlaying);
                if (isPlaying) {
                    scheduleHideControls();
                    startProgressUpdates();
                } else {
                    cancelHideControls();
                    stopProgressUpdates();
                }
            }

            @Override
            public void onPlayerError(@NonNull PlaybackException error) {
                Toast.makeText(getContext(), "Ошибка воспроизведения видео", Toast.LENGTH_SHORT).show();
                showControls();
            }
        });
    }

    private void setupControls() {
        closeButton.setOnClickListener(v -> closeViewer());
        resetZoomButton.setOnClickListener(v -> resetCurrentZoom());
        playPauseButton.setOnClickListener(v -> togglePlayPause());
        rewindButton.setOnClickListener(v -> {
            if (player != null) {
                player.seekTo(Math.max(0, player.getCurrentPosition() - SKIP_MS));
            }
        });
        forwardButton.setOnClickListener(v -> {
            if (player != null && player.getDuration() > 0) {
                player.seekTo(Math.min(player.getDuration(), player.getCurrentPosition() + SKIP_MS));
            }
        });
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    currentTimeText.setText(formatTime(progress));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                userSeeking = true;
                cancelHideControls();
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if (player != null) {
                    player.seekTo(seekBar.getProgress());
                }
                userSeeking = false;
                if (player != null && player.isPlaying()) {
                    scheduleHideControls();
                }
            }
        });
    }

    private void showPage(int position, boolean initial) {
        currentPosition = position;
        MediaFile mediaFile = mediaFiles.get(position);
        titleTextView.setText(mediaFile.getFileName());
        pageIndicatorText.setText(getString(R.string.photo_page_indicator, position + 1, mediaFiles.size()));

        detachPlayerFromView();

        if ("video".equals(mediaFile.getFileType())) {
            mediaViewPager.post(() -> playVideoAt(position));
            updateZoomState(false);
            showControls();
        } else {
            stopVideoPlayback();
            updateImmersiveMode(false);
            mediaViewPager.post(() -> setupPhotoAt(position));
            updateZoomState(false);
            showControls();
        }
    }

    private void setupPhotoAt(int position) {
        RecyclerView recyclerView = (RecyclerView) mediaViewPager.getChildAt(0);
        ZoomPanLayout zoomLayout = pagerAdapter.getZoomLayoutAt(recyclerView, position);
        if (zoomLayout == null) {
            mediaViewPager.post(() -> setupPhotoAt(position));
            return;
        }

        zoomLayout.resetZoom();
        zoomLayout.setOnZoomChangeListener(this::updateZoomState);
        zoomLayout.setOnTapListener(new ZoomPanLayout.OnTapListener() {
            @Override
            public void onSingleTap() {
                toggleControls();
            }

            @Override
            public void onDoubleTap() {
                // Для фото двойной тап не нужен — только pinch/pan как у видео
            }
        });
    }

    private void updateZoomState(boolean isZoomed) {
        isMediaZoomed = isZoomed;
        mediaViewPager.setUserInputEnabled(!isZoomed);
        swipeDismissLayout.setDismissEnabled(!isZoomed);
        applyControlsVisibility();
    }

    private void resetCurrentZoom() {
        RecyclerView recyclerView = (RecyclerView) mediaViewPager.getChildAt(0);
        if (recyclerView != null && pagerAdapter != null) {
            pagerAdapter.resetZoomAt(recyclerView, currentPosition);
        }
    }

    private void updateImmersiveMode(boolean playing) {
        if (getActivity() == null) {
            return;
        }
        if (playing && isCurrentPageVideo()) {
            SystemUiHelper.enterImmersiveMode(getActivity().getWindow());
        } else {
            SystemUiHelper.exitImmersiveMode(getActivity().getWindow());
        }
    }

    private void playVideoAt(int position) {
        MediaFile mediaFile = mediaFiles.get(position);
        if (!"video".equals(mediaFile.getFileType())) {
            return;
        }

        RecyclerView recyclerView = (RecyclerView) mediaViewPager.getChildAt(0);
        PlayerView playerView = pagerAdapter.getPlayerViewAt(recyclerView, position);
        ZoomPanLayout zoomLayout = pagerAdapter.getZoomLayoutAt(recyclerView, position);
        if (playerView == null || zoomLayout == null) {
            mediaViewPager.post(() -> playVideoAt(position));
            return;
        }

        zoomLayout.resetZoom();
        zoomLayout.setOnZoomChangeListener(this::updateZoomState);
        zoomLayout.setOnTapListener(new ZoomPanLayout.OnTapListener() {
            @Override
            public void onSingleTap() {
                toggleControls();
            }

            @Override
            public void onDoubleTap() {
                togglePlayPause();
            }
        });

        attachPlayerToView(playerView);

        Uri uri = MediaStorageHelper.resolveLoadUri(mediaFile.getFileUri());
        if (uri == null) {
            Toast.makeText(getContext(), "Неверный URI видео", Toast.LENGTH_SHORT).show();
            return;
        }

        player.setMediaItem(MediaItem.fromUri(uri));
        player.prepare();
        player.play();
        bottomBar.setVisibility(controlsVisible ? View.VISIBLE : View.GONE);
    }

    private void attachPlayerToView(PlayerView playerView) {
        if (attachedPlayerView == playerView) {
            return;
        }
        detachPlayerFromView();
        attachedPlayerView = playerView;
        attachedPlayerView.setPlayer(player);
    }

    private void detachPlayerFromView() {
        if (attachedPlayerView != null) {
            attachedPlayerView.setPlayer(null);
            attachedPlayerView = null;
        }
    }

    private void stopVideoPlayback() {
        if (player != null) {
            player.stop();
            player.clearMediaItems();
        }
    }

    private void togglePlayPause() {
        if (player == null || !isCurrentPageVideo()) {
            return;
        }
        if (player.getPlaybackState() == Player.STATE_ENDED) {
            player.seekTo(0);
            player.play();
            return;
        }
        if (player.isPlaying()) {
            player.pause();
            showControls();
        } else {
            player.play();
        }
    }

    private boolean isCurrentPageVideo() {
        return currentPosition >= 0
                && currentPosition < mediaFiles.size()
                && "video".equals(mediaFiles.get(currentPosition).getFileType());
    }

    private void updatePlayPauseIcon() {
        if (player == null) {
            return;
        }
        if (player.getPlaybackState() == Player.STATE_ENDED) {
            playPauseButton.setImageResource(R.drawable.ic_baseline_replay_24);
        } else if (player.isPlaying()) {
            playPauseButton.setImageResource(R.drawable.ic_baseline_pause_24);
        } else {
            playPauseButton.setImageResource(R.drawable.ic_baseline_play_arrow_24);
        }
    }

    private void updateDurationUi() {
        if (player == null) {
            return;
        }
        long duration = player.getDuration();
        if (duration > 0) {
            seekBar.setMax((int) duration);
            totalTimeText.setText(formatTime(duration));
        }
        currentTimeText.setText(formatTime(player.getCurrentPosition()));
        if (!userSeeking) {
            seekBar.setProgress((int) player.getCurrentPosition());
        }
    }

    private void startProgressUpdates() {
        stopProgressUpdates();
        progressRunnable = new Runnable() {
            @Override
            public void run() {
                updateDurationUi();
                handler.postDelayed(this, PROGRESS_UPDATE_MS);
            }
        };
        handler.post(progressRunnable);
    }

    private void stopProgressUpdates() {
        if (progressRunnable != null) {
            handler.removeCallbacks(progressRunnable);
            progressRunnable = null;
        }
    }

    private void toggleControls() {
        controlsVisible = !controlsVisible;
        applyControlsVisibility();
        if (controlsVisible && player != null && player.isPlaying()) {
            scheduleHideControls();
        }
    }

    private void showControls() {
        controlsVisible = true;
        applyControlsVisibility();
    }

    private void applyControlsVisibility() {
        int controlsVisibility = controlsVisible ? View.VISIBLE : View.GONE;
        topBar.setVisibility(controlsVisibility);
        resetZoomButton.setVisibility(isMediaZoomed ? View.VISIBLE : View.GONE);

        if (isCurrentPageVideo()) {
            if (controlsVisible) {
                bottomBar.setVisibility(View.VISIBLE);
                seekBarRow.setVisibility(View.VISIBLE);
                rewindButton.setVisibility(View.VISIBLE);
                playPauseButton.setVisibility(View.VISIBLE);
                forwardButton.setVisibility(View.VISIBLE);
            } else {
                bottomBar.setVisibility(View.GONE);
            }
        } else if (isMediaZoomed) {
            bottomBar.setVisibility(View.VISIBLE);
            seekBarRow.setVisibility(View.GONE);
            rewindButton.setVisibility(View.GONE);
            playPauseButton.setVisibility(View.GONE);
            forwardButton.setVisibility(View.GONE);
        } else {
            bottomBar.setVisibility(View.GONE);
        }
    }

    private void scheduleHideControls() {
        if (!isCurrentPageVideo()) {
            return;
        }
        cancelHideControls();
        hideControlsRunnable = () -> {
            if (player != null && player.isPlaying()) {
                controlsVisible = false;
                applyControlsVisibility();
            }
        };
        handler.postDelayed(hideControlsRunnable, HIDE_CONTROLS_DELAY_MS);
    }

    private void cancelHideControls() {
        if (hideControlsRunnable != null) {
            handler.removeCallbacks(hideControlsRunnable);
            hideControlsRunnable = null;
        }
    }

    private void closeViewer() {
        updateImmersiveMode(false);
        requireActivity().getSupportFragmentManager().popBackStack();
    }

    private static String formatTime(long millis) {
        if (millis < 0) {
            millis = 0;
        }
        long totalSeconds = millis / 1000;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds);
    }

    @Override
    public void onPause() {
        super.onPause();
        if (player != null) {
            player.pause();
        }
    }

    @Override
    public void onDestroyView() {
        updateImmersiveMode(false);
        stopProgressUpdates();
        cancelHideControls();
        detachPlayerFromView();
        if (player != null) {
            player.release();
            player = null;
        }
        if (mediaViewPager != null) {
            mediaViewPager.setAdapter(null);
        }
        super.onDestroyView();
    }
}
