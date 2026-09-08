package com.example.seriestracker.ui.utils;

import android.os.Handler;
import android.os.Looper;
import android.text.Layout;
import android.text.Spannable;
import android.text.method.LinkMovementMethod;
import android.text.style.URLSpan;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.widget.TextView;

import androidx.annotation.Nullable;

/**
 * Обычный клик открывает ссылку, долгое нажатие — выбор браузера.
 */
public final class WatchLinkMovementMethod extends LinkMovementMethod {

    public interface UrlHandler {
        void onUrlClick(String url);

        void onUrlLongClick(String url);
    }

    private final UrlHandler urlHandler;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final int longPressTimeout;

    @Nullable
    private Runnable pendingLongPress;
    private boolean longPressTriggered;
    @Nullable
    private String pressedUrl;

    public WatchLinkMovementMethod(TextView textView, UrlHandler urlHandler) {
        this.urlHandler = urlHandler;
        this.longPressTimeout = ViewConfiguration.get(textView.getContext()).getLongPressTimeout();
    }

    @Override
    public boolean onTouchEvent(TextView widget, Spannable buffer, MotionEvent event) {
        int action = event.getAction();
        URLSpan span = findUrlSpan(widget, buffer, event);

        if (action == MotionEvent.ACTION_DOWN) {
            cancelLongPress();
            longPressTriggered = false;
            pressedUrl = span != null ? span.getURL() : null;
            if (pressedUrl != null) {
                final String url = pressedUrl;
                pendingLongPress = () -> {
                    longPressTriggered = true;
                    widget.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                    urlHandler.onUrlLongClick(url);
                };
                handler.postDelayed(pendingLongPress, longPressTimeout);
                return true;
            }
        } else if (action == MotionEvent.ACTION_MOVE) {
            if (pressedUrl != null) {
                URLSpan current = findUrlSpan(widget, buffer, event);
                if (current == null || !pressedUrl.equals(current.getURL())) {
                    cancelLongPress();
                    pressedUrl = null;
                }
                return true;
            }
        } else if (action == MotionEvent.ACTION_UP) {
            cancelLongPress();
            if (pressedUrl != null) {
                if (!longPressTriggered) {
                    urlHandler.onUrlClick(pressedUrl);
                }
                pressedUrl = null;
                return true;
            }
        } else if (action == MotionEvent.ACTION_CANCEL) {
            cancelLongPress();
            pressedUrl = null;
            longPressTriggered = false;
        }

        return super.onTouchEvent(widget, buffer, event);
    }

    private void cancelLongPress() {
        if (pendingLongPress != null) {
            handler.removeCallbacks(pendingLongPress);
            pendingLongPress = null;
        }
    }

    @Nullable
    private static URLSpan findUrlSpan(TextView widget, Spannable buffer, MotionEvent event) {
        int x = (int) event.getX() - widget.getTotalPaddingLeft() + widget.getScrollX();
        int y = (int) event.getY() - widget.getTotalPaddingTop() + widget.getScrollY();

        Layout layout = widget.getLayout();
        if (layout == null) {
            return null;
        }

        int line = layout.getLineForVertical(y);
        int off = layout.getOffsetForHorizontal(line, x);
        URLSpan[] spans = buffer.getSpans(off, off, URLSpan.class);
        if (spans.length == 0) {
            return null;
        }
        return spans[0];
    }
}
