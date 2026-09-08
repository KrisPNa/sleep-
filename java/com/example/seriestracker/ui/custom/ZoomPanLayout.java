package com.example.seriestracker.ui.custom;

import android.content.Context;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;

public class ZoomPanLayout extends FrameLayout {

    public interface OnZoomChangeListener {
        void onZoomChanged(boolean isZoomed);
    }

    public interface OnTapListener {
        void onSingleTap();

        void onDoubleTap();
    }

    private static final float MIN_SCALE = 1f;
    private static final float MAX_SCALE = 5f;

    private View contentView;
    private float scale = MIN_SCALE;
    private float translationX;
    private float translationY;

    private float lastTouchX;
    private float lastTouchY;
    private boolean isPanning;

    private ScaleGestureDetector scaleDetector;
    private GestureDetector gestureDetector;

    private OnZoomChangeListener zoomChangeListener;
    private OnTapListener tapListener;

    public ZoomPanLayout(Context context) {
        super(context);
        init(context);
    }

    public ZoomPanLayout(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public ZoomPanLayout(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        scaleDetector = new ScaleGestureDetector(context,
                new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    @Override
                    public boolean onScale(ScaleGestureDetector detector) {
                        float prevScale = scale;
                        float newScale = scale * detector.getScaleFactor();
                        newScale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, newScale));

                        float focusX = detector.getFocusX();
                        float focusY = detector.getFocusY();
                        float factor = newScale / prevScale;
                        translationX = focusX - (focusX - translationX) * factor;
                        translationY = focusY - (focusY - translationY) * factor;

                        scale = newScale;
                        constrainTranslation();
                        applyTransform();
                        notifyZoom();
                        return true;
                    }
                });

        gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                if (tapListener != null) {
                    tapListener.onSingleTap();
                }
                return true;
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                if (tapListener != null) {
                    tapListener.onDoubleTap();
                }
                return true;
            }
        });
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        if (getChildCount() > 0) {
            contentView = getChildAt(0);
        }
    }

    public void setOnZoomChangeListener(OnZoomChangeListener listener) {
        this.zoomChangeListener = listener;
    }

    public void setOnTapListener(OnTapListener listener) {
        this.tapListener = listener;
    }

    public void resetZoom() {
        scale = MIN_SCALE;
        translationX = 0f;
        translationY = 0f;
        applyTransform();
        notifyZoom();
    }

    public boolean isZoomed() {
        return scale > MIN_SCALE + 0.01f;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (isZoomed() || ev.getPointerCount() > 1) {
            requestParentDisallowIntercept(true);
            return true;
        }
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getPointerCount() >= 2) {
            requestParentDisallowIntercept(true);
        }

        scaleDetector.onTouchEvent(event);
        gestureDetector.onTouchEvent(event);

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                lastTouchX = event.getX();
                lastTouchY = event.getY();
                isPanning = isZoomed();
                if (isPanning) {
                    requestParentDisallowIntercept(true);
                }
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
                requestParentDisallowIntercept(true);
                break;
            case MotionEvent.ACTION_MOVE:
                if (isPanning && !scaleDetector.isInProgress() && event.getPointerCount() == 1) {
                    translationX += event.getX() - lastTouchX;
                    translationY += event.getY() - lastTouchY;
                    constrainTranslation();
                    applyTransform();
                    lastTouchX = event.getX();
                    lastTouchY = event.getY();
                    requestParentDisallowIntercept(true);
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                isPanning = false;
                if (scale <= MIN_SCALE + 0.01f) {
                    resetZoom();
                }
                break;
            default:
                break;
        }
        return true;
    }

    private void requestParentDisallowIntercept(boolean disallow) {
        if (getParent() != null) {
            getParent().requestDisallowInterceptTouchEvent(disallow);
        }
    }

    private void constrainTranslation() {
        if (contentView == null || getWidth() == 0 || getHeight() == 0) {
            return;
        }

        float contentWidth = getWidth() * scale;
        float contentHeight = getHeight() * scale;

        if (contentWidth <= getWidth()) {
            translationX = (getWidth() - contentWidth) / 2f;
        } else {
            float minTx = getWidth() - contentWidth;
            translationX = Math.max(minTx, Math.min(0f, translationX));
        }

        if (contentHeight <= getHeight()) {
            translationY = (getHeight() - contentHeight) / 2f;
        } else {
            float minTy = getHeight() - contentHeight;
            translationY = Math.max(minTy, Math.min(0f, translationY));
        }
    }

    private void applyTransform() {
        if (contentView == null) {
            return;
        }
        contentView.setPivotX(0f);
        contentView.setPivotY(0f);
        contentView.setScaleX(scale);
        contentView.setScaleY(scale);
        contentView.setTranslationX(translationX);
        contentView.setTranslationY(translationY);
    }

    private void notifyZoom() {
        if (zoomChangeListener != null) {
            zoomChangeListener.onZoomChanged(isZoomed());
        }
    }
}
