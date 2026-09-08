package com.example.seriestracker.data.sync;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ProcessLifecycleOwner;

/**
 * Автосинхронизация без кнопки:
 * — при открытии приложения
 * — каждые ~2 мин, пока приложение на экране
 */
public final class AutoSyncController implements DefaultLifecycleObserver {
    private static final String TAG = "AutoSync";
    private static final long FOREGROUND_INTERVAL_MS = 120_000L;

    private static volatile AutoSyncController instance;

    private final Context appContext;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean started;
    private boolean foreground;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!foreground) return;
            AuthSessionStore store = new AuthSessionStore(appContext);
            if (store.isLoggedIn()) {
                Log.d(TAG, "periodic auto-sync");
                SyncEngine.getInstance(appContext).requestSync();
            }
            handler.postDelayed(this, FOREGROUND_INTERVAL_MS);
        }
    };

    private AutoSyncController(Context context) {
        this.appContext = context.getApplicationContext();
    }

    public static void install(Context context) {
        if (instance == null) {
            synchronized (AutoSyncController.class) {
                if (instance == null) {
                    instance = new AutoSyncController(context);
                    instance.start();
                }
            }
        }
    }

    private void start() {
        if (started) return;
        started = true;
        ProcessLifecycleOwner.get().getLifecycle().addObserver(this);
    }

    @Override
    public void onStart(@NonNull LifecycleOwner owner) {
        foreground = true;
        AuthSessionStore store = new AuthSessionStore(appContext);
        if (store.isLoggedIn()) {
            SyncEngine.getInstance(appContext).requestSync();
        }
        handler.removeCallbacks(tick);
        handler.postDelayed(tick, FOREGROUND_INTERVAL_MS);
    }

    @Override
    public void onStop(@NonNull LifecycleOwner owner) {
        foreground = false;
        handler.removeCallbacks(tick);
        // Одноразовая синхронизация при уходе в фон, чтобы отправить dirty
        AuthSessionStore store = new AuthSessionStore(appContext);
        if (store.isLoggedIn()) {
            SyncEngine.getInstance(appContext).requestSync();
        }
    }
}
