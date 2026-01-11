package com.example.seriestracker;

import android.app.Application;
import android.content.SharedPreferences;

import com.example.seriestracker.data.backup.AutoBackupManager;
import com.example.seriestracker.data.repository.SeriesRepository;

public class SeriesTrackerApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        // Инициализация менеджера бэкапов
        SeriesRepository repository = new SeriesRepository(this);
        AutoBackupManager backupManager = AutoBackupManager.getInstance(this, repository);

        // Проверяем наличие бэкапов при запуске приложения
        checkForBackupsOnStartup(backupManager, repository);
    }

    private void checkForBackupsOnStartup(AutoBackupManager backupManager, SeriesRepository repository) {
        new Thread(() -> {
            try {
                Thread.sleep(3000); // Ждем инициализации

                // Проверяем, есть ли данные в БД
                if (repository.getAllCollectionsSync() == null ||
                        repository.getAllCollectionsSync().isEmpty()) {

                    // База пуста, проверяем наличие бэкапов
                    if (backupManager.hasBackups()) {
                        // Можно показать уведомление или диалог
                        // но лучше оставить это на экране резервного копирования
                    }
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }
}