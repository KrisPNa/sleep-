package com.example.seriestracker.ui.screens;

import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.example.seriestracker.R;
import com.example.seriestracker.ui.custom.SwipeToDismissLayout;
import com.example.seriestracker.ui.custom.ZoomPanLayout;
import com.example.seriestracker.utils.MediaStorageHelper;

public class CoverViewerFragment extends Fragment {

    private static final String ARG_IMAGE_URI = "image_uri";
    private static final String ARG_TITLE = "title";

    private SwipeToDismissLayout swipeDismissLayout;
    private ZoomPanLayout coverZoomLayout;
    private ImageView coverImageView;
    private LinearLayout topBar;
    private LinearLayout bottomBar;
    private ImageButton closeButton;
    private ImageButton resetZoomButton;
    private TextView titleTextView;

    private boolean controlsVisible = true;
    private boolean isZoomed;

    public static CoverViewerFragment newInstance(String imageUri, String title) {
        CoverViewerFragment fragment = new CoverViewerFragment();
        Bundle args = new Bundle();
        args.putString(ARG_IMAGE_URI, imageUri);
        args.putString(ARG_TITLE, title != null ? title : "");
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_cover_viewer, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        bindViews(view);
        setupControls();
        loadCover();
    }

    private void bindViews(View view) {
        swipeDismissLayout = view.findViewById(R.id.swipeDismissLayout);
        coverZoomLayout = view.findViewById(R.id.coverZoomLayout);
        coverImageView = view.findViewById(R.id.coverImageView);
        topBar = view.findViewById(R.id.topBar);
        bottomBar = view.findViewById(R.id.bottomBar);
        closeButton = view.findViewById(R.id.closeButton);
        resetZoomButton = view.findViewById(R.id.resetZoomButton);
        titleTextView = view.findViewById(R.id.titleTextView);
    }

    private void setupControls() {
        swipeDismissLayout.setOnDismissListener(this::closeViewer);
        closeButton.setOnClickListener(v -> closeViewer());
        resetZoomButton.setOnClickListener(v -> coverZoomLayout.resetZoom());

        coverZoomLayout.setOnZoomChangeListener(zoomed -> {
            isZoomed = zoomed;
            swipeDismissLayout.setDismissEnabled(!zoomed);
            applyControlsVisibility();
        });
        coverZoomLayout.setOnTapListener(new ZoomPanLayout.OnTapListener() {
            @Override
            public void onSingleTap() {
                toggleControls();
            }

            @Override
            public void onDoubleTap() {
                // Pinch/pan only, like media photos.
            }
        });

        Bundle args = getArguments();
        if (args != null) {
            titleTextView.setText(args.getString(ARG_TITLE, ""));
        }
    }

    private void loadCover() {
        Bundle args = getArguments();
        if (args == null) {
            return;
        }

        String imageUriValue = args.getString(ARG_IMAGE_URI);
        Uri uri = imageUriValue != null && (imageUriValue.startsWith("content://")
                || imageUriValue.startsWith("file://"))
                ? Uri.parse(imageUriValue)
                : MediaStorageHelper.resolveLoadUri(imageUriValue);

        if (uri == null) {
            closeViewer();
            return;
        }

        Glide.with(this)
                .load(uri)
                .error(R.drawable.ic_baseline_image_24)
                .into(coverImageView);
    }

    private void toggleControls() {
        controlsVisible = !controlsVisible;
        applyControlsVisibility();
    }

    private void applyControlsVisibility() {
        int controlsVisibility = controlsVisible ? View.VISIBLE : View.GONE;
        topBar.setVisibility(controlsVisibility);
        resetZoomButton.setVisibility(isZoomed ? View.VISIBLE : View.GONE);
        bottomBar.setVisibility(isZoomed ? View.VISIBLE : View.GONE);
    }

    private void closeViewer() {
        requireActivity().getSupportFragmentManager().popBackStack();
    }
}
