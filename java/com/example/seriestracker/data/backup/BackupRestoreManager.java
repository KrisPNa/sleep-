package com.example.seriestracker.data.backup;

import android.content.Context;
import android.util.Log;

import com.example.seriestracker.data.entities.Collection;
import com.example.seriestracker.data.entities.Series;
import com.example.seriestracker.data.entities.SeriesCollectionCrossRef;
import com.example.seriestracker.data.repository.SeriesRepository;
import com.google.gson.Gson;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Упрощенный менеджер восстановления данных
 */
public class BackupRestoreManager {
    private static final String TAG = "BackupRestoreManager";

    private final Context context;
    private final SeriesRepository repository;
    private final Gson gson;
    private final ExecutorService executor;

    public interface RestoreCallback {
        void onSuccess(String message);
        void onError(String error);
        void onProgress(int progress, String message);
    }

    public BackupRestoreManager(Context context, SeriesRepository repository) {
        this.context = context.getApplicationContext();
        this.repository = repository;
        this.gson = new Gson();
        this.executor = Executors.newSingleThreadExecutor();
    }

    /**
     * Восстанавливает данные из файла резервной копии
     */
    public void restoreFromFile(File backupFile, RestoreCallback callback) {
        executor.execute(() -> {
            try {
                callback.onProgress(10, "Чтение файла...");

                // Читаем файл
                StringBuilder jsonBuilder = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new FileReader(backupFile))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        jsonBuilder.append(line);
                    }
                }

                callback.onProgress(30, "Проверка данных...");

                // Конвертируем из JSON
                String json = jsonBuilder.toString();
                AutoBackupManager.BackupData backupData = gson.fromJson(json, AutoBackupManager.BackupData.class);

                if (backupData == null) {
                    callback.onError("Неверный формат файла");
                    return;
                }

                callback.onProgress(50, "Восстановление коллекций...");

                // Восстанавливаем коллекции
                if (backupData.collections != null) {
                    for (Collection collection : backupData.collections) {
                        repository.insertCollection(collection);
                    }
                }

                callback.onProgress(70, "Восстановление сериалов...");

                // Восстанавливаем сериалы
                if (backupData.series != null) {
                    for (Series series : backupData.series) {
                        repository.insertSeries(series);
                    }
                }

                callback.onProgress(90, "Восстановление связей...");

                // Восстанавливаем связи
                if (backupData.relations != null) {
                    for (SeriesCollectionCrossRef relation : backupData.relations) {
                        repository.insertCrossRef(relation);
                    }
                }

                callback.onProgress(100, "Восстановление завершено!");
                callback.onSuccess("Данные успешно восстановлены");

            } catch (Exception e) {
                Log.e(TAG, "Error restoring from backup", e);
                callback.onError("Ошибка: " + e.getMessage());
            }
        });
    }

    /**
     * Восстанавливает данные из последней автоматической резервной копии
     */
    public void restoreFromLatestBackup(RestoreCallback callback) {
        AutoBackupManager backupManager = AutoBackupManager.getInstance(context, repository);
        File latestBackup = backupManager.getLatestBackup();

        if (latestBackup != null && latestBackup.exists()) {
            restoreFromFile(latestBackup, callback);
        } else {
            callback.onError("Резервная копия не найдена");
        }
    }

    public void cleanup() {
        executor.shutdown();
    }
}