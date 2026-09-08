package com.example.seriestracker.ui.custom;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;

public class SwipeToDismissLayout extends FrameLayout {

    public interface OnDismissListener {
        void onDismiss();
    }

    private OnDismissListener dismissListener;
    private boolean dismissEnabled = true;
    private float startRawX;
    private float startRawY;
    private float lastRawY;
    private boolean dragging;
    private final int touchSlop;

    public SwipeToDismissLayout(Context context) {
        super(context);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
    }

    public SwipeToDismissLayout(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
    }

    public SwipeToDismissLayout(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
    }

    public void setOnDismissListener(OnDismissListener listener) {
        this.dismissListener = listener;
    }

    public void setDismissEnabled(boolean enabled) {
        this.dismissEnabled = enabled;
        if (!enabled) {
            resetDrag();
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (!dismissEnabled || dismissListener == null) {
            return false;
        }

        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                startRawX = ev.getRawX();
                startRawY = ev.getRawY();
                lastRawY = startRawY;
                dragging = false;
                break;
            case MotionEvent.ACTION_MOVE:
                float dx = ev.getRawX() - startRawX;
                float dy = ev.getRawY() - startRawY;
                if (dy > touchSlop && dy > Math.abs(dx) * 1.2f) {
                    dragging = true;
                    return true;
                }
                break;
            default:
                break;
        }
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!dismissEnabled || dismissListener == null) {
            return false;
        }

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                startRawX = event.getRawX();
                startRawY = event.getRawY();
                lastRawY = startRawY;
                return true;
            case MotionEvent.ACTION_MOVE:
                float dy = event.getRawY() - startRawY;
                if (dy > 0) {
                    setTranslationY(dy);
                    float progress = Math.min(1f, dy / (getHeight() * 0.5f));
                    setAlpha(1f - progress * 0.4f);
                }
                lastRawY = event.getRawY();
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                float totalDy = event.getRawY() - startRawY;
                float dismissThreshold = Math.max(getHeight() * 0.12f, touchSlop * 4f);
                if (totalDy > dismissThreshold) {
                    if (dismissListener != null) {
                        dismissListener.onDismiss();
                    }
                } else {
                    animate().translationY(0f).alpha(1f).setDuration(180).start();
                }
                dragging = false;
                return true;
            default:
                break;
        }
        return super.onTouchEvent(event);
    }

    private void resetDrag() {
        dragging = false;
        setTranslationY(0f);
        setAlpha(1f);
    }
}
