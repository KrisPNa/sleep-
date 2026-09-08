package com.example.seriestracker;

import android.app.Application;

import com.example.seriestracker.data.backup.AutoBackupManager;
import com.example.seriestracker.data.repository.SeriesRepository;
import com.example.seriestracker.data.sync.AuthSessionStore;
import com.example.seriestracker.data.sync.AutoSyncController;
import com.example.seriestracker.data.sync.SyncEngine;
import com.example.seriestracker.data.sync.SyncWorker;

public class SeriesTrackerApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        com.example.seriestracker.data.prefs.ThemePreferences.apply(this);

        SeriesRepository.getInstance(this);
        AutoBackupManager.getInstance(this, SeriesRepository.getInstance(this));

        // Автосинк: при открытии / в фоне по расписанию / каждые ~45с на экране
        AutoSyncController.install(this);
        SyncWorker.schedule(this);

        AuthSessionStore store = new AuthSessionStore(this);
        if (store.isLoggedIn()) {
            SyncEngine.getInstance(this).requestSync();
        }
    }
}
