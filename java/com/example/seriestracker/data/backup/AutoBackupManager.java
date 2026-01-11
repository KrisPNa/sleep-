package com.example.seriestracker.data.backup;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import android.util.Log;

import com.example.seriestracker.data.entities.Collection;
import com.example.seriestracker.data.entities.Series;
import com.example.seriestracker.data.entities.SeriesCollectionCrossRef;
import com.example.seriestracker.data.repository.SeriesRepository;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.lang.reflect.Type;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AutoBackupManager {
    private static final String TAG = "AutoBackupManager";
    private static final String PREFS_NAME = "auto_backup_prefs";
    private static final String KEY_LAST_AUTO_BACKUP = "last_auto_backup";
    private static final String KEY_AUTO_BACKUP_ENABLED = "auto_backup_enabled";
    private static final String KEY_FIRST_LAUNCH = "first_launch";

    private final Context context;
    private final SeriesRepository repository;
    private final Gson gson;
    private final ExecutorService executor;
    private final SharedPreferences prefs;

    private static AutoBackupManager instance;

    public static synchronized AutoBackupManager getInstance(Context context, SeriesRepository repository) {
        if (instance == null) {
            instance = new AutoBackupManager(context, repository);
        }
        return instance;
    }

    private AutoBackupManager(Context context, SeriesRepository repository) {
        this.context = context.getApplicationContext();
        this.repository = repository;
        this.gson = new Gson();
        this.executor = Executors.newSingleThreadExecutor();
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        // Помечаем первый запуск, если нужно
        checkFirstLaunch();
    }

    private void checkFirstLaunch() {
        boolean isFirstLaunch = prefs.getBoolean(KEY_FIRST_LAUNCH, true);
        if (isFirstLaunch) {
            // Отключаем авто-бэкап по умолчанию
            setAutoBackupEnabled(false);
            prefs.edit().putBoolean(KEY_FIRST_LAUNCH, false).apply();
        }
    }

    /**
     * Вызывается ТОЛЬКО при создании или изменении данных (не при удалении)
     */
    public void onDataCreatedOrUpdated(String changeType, String dataType) {
        if (!isAutoBackupEnabled() || "delete".equals(changeType)) {
            return; // Не делаем бэкап при удалении
        }

        executor.execute(() -> {
            try {
                createBackup();
            } catch (Exception e) {
                Log.e(TAG, "Auto backup failed", e);
            }
        });
    }

    public void createManualBackup() {
        executor.execute(() -> {
            try {
                createBackup();
            } catch (Exception e) {
                Log.e(TAG, "Manual backup failed", e);
            }
        });
    }

    private void createBackup() {
        try {
            Log.d(TAG, "Creating backup...");

            // Получаем данные
            List<Collection> collections = repository.getAllCollectionsSync();
            List<Series> series = repository.getAllSeriesSync();
            List<SeriesCollectionCrossRef> relations = repository.getAllRelationsSync();

            if (collections == null || series == null || relations == null) {
                Log.e(TAG, "Failed to get data for backup");
                return;
            }

            Log.d(TAG, "Data retrieved: " + collections.size() + " collections, " +
                    series.size() + " series, " + relations.size() + " relations");

            // Создаем объект бэкапа
            BackupData backupData = new BackupData();
            backupData.collections = collections;
            backupData.series = series;
            backupData.relations = relations;
            backupData.timestamp = System.currentTimeMillis();
            backupData.version = 1;

            // Конвертируем в JSON
            String json = gson.toJson(backupData);

            // Создаем директорию
            File backupDir = getBackupDirectory();
            if (!backupDir.exists() && !backupDir.mkdirs()) {
                Log.e(TAG, "Failed to create backup directory");
                return;
            }

            // Создаем файл
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                    .format(new Date());
            String fileName = "backup_" + timeStamp + ".json";
            File backupFile = new File(backupDir, fileName);

            // Сохраняем файл
            try (FileOutputStream fos = new FileOutputStream(backupFile);
                 OutputStreamWriter writer = new OutputStreamWriter(fos)) {
                writer.write(json);
                writer.flush();
            }

            // Сохраняем время бэкапа
            prefs.edit().putLong(KEY_LAST_AUTO_BACKUP, System.currentTimeMillis()).apply();

            Log.i(TAG, "Backup created successfully: " + backupFile.getAbsolutePath());

        } catch (Exception e) {
            Log.e(TAG, "Error creating backup", e);
        }
    }

    public boolean restoreFromFile(File backupFile) {
        try {
            Log.d(TAG, "Starting restore from: " + backupFile.getAbsolutePath());

            // Читаем файл
            java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.FileReader(backupFile));
            StringBuilder jsonBuilder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                jsonBuilder.append(line);
            }
            reader.close();

            String json = jsonBuilder.toString();
            Log.d(TAG, "Backup JSON length: " + json.length());

            // Парсим JSON
            Type type = new TypeToken<BackupData>() {}.getType();
            BackupData backupData = gson.fromJson(json, type);

            if (backupData == null) {
                Log.e(TAG, "Failed to parse backup file");
                return false;
            }

            Log.d(TAG, "Parsed backup: " +
                    (backupData.collections != null ? backupData.collections.size() : 0) + " collections, " +
                    (backupData.series != null ? backupData.series.size() : 0) + " series");

            // Очищаем текущие данные
            repository.deleteAllData();

            // Мапы для соответствия старых и новых ID
            Map<Long, Long> collectionIdMap = new HashMap<>();
            Map<Long, Long> seriesIdMap = new HashMap<>();

            // Восстанавливаем коллекции
            if (backupData.collections != null) {
                for (Collection collection : backupData.collections) {
                    long oldId = collection.getId();
                    // Сбрасываем ID для новой вставки
                    collection.setId(0);
                    long newId = repository.insertCollectionSync(collection);
                    if (newId > 0) {
                        collectionIdMap.put(oldId, newId);
                        Log.d(TAG, "Restored collection: " + collection.getName() +
                                " (old: " + oldId + ", new: " + newId + ")");
                    }
                }
            }

            // Восстанавливаем сериалы
            if (backupData.series != null) {
                for (Series series : backupData.series) {
                    long oldId = series.getId();
                    // Сбрасываем ID для новой вставки
                    series.setId(0);
                    long newId = repository.insertSeriesSync(series);
                    if (newId > 0) {
                        seriesIdMap.put(oldId, newId);
                        Log.d(TAG, "Restored series: " + series.getTitle() +
                                " (old: " + oldId + ", new: " + newId + ")");
                    }
                }
            }

            // Восстанавливаем связи
            if (backupData.relations != null) {
                for (SeriesCollectionCrossRef relation : backupData.relations) {
                    Long newSeriesId = seriesIdMap.get(relation.getSeriesId());
                    Long newCollectionId = collectionIdMap.get(relation.getCollectionId());

                    if (newSeriesId != null && newCollectionId != null) {
                        SeriesCollectionCrossRef newRelation = new SeriesCollectionCrossRef(
                                newSeriesId, newCollectionId);
                        newRelation.setIsWatched(relation.getIsWatched());
                        repository.insertCrossRefSync(newRelation);
                        Log.d(TAG, "Restored relation: series " + newSeriesId +
                                " -> collection " + newCollectionId);
                    }
                }
            }

            Log.i(TAG, "Restore completed successfully");
            return true;

        } catch (Exception e) {
            Log.e(TAG, "Error restoring from backup", e);
            return false;
        }
    }

    /**
     * Получает директорию для резервных копий
     * По умолчанию: /storage/emulated/0/Android/data/com.example.seriestracker/files/backups
     */
    private File getBackupDirectory() {
        // Используем стандартную папку приложения
        File externalFilesDir = context.getExternalFilesDir(null);
        if (externalFilesDir != null) {
            return new File(externalFilesDir, "backups");
        }

        // Fallback на внутреннее хранилище
        return new File(context.getFilesDir(), "backups");
    }

    /**
     * Ищет резервные копии в стандартной папке и подпапках
     */
    public File[] getAvailableBackups() {
        File backupDir = getBackupDirectory();
        List<File> backupFiles = new java.util.ArrayList<>();

        if (backupDir.exists()) {
            // Ищем во всех поддиректориях
            findBackupFiles(backupDir, backupFiles);
        }

        return backupFiles.toArray(new File[0]);
    }

    private void findBackupFiles(File directory, List<File> backupFiles) {
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    findBackupFiles(file, backupFiles);
                } else if (file.getName().endsWith(".json")) {
                    backupFiles.add(file);
                }
            }
        }
    }

    public File getLatestBackup() {
        File[] backups = getAvailableBackups();
        if (backups != null && backups.length > 0) {
            File latest = backups[0];
            for (File backup : backups) {
                if (backup.lastModified() > latest.lastModified()) {
                    latest = backup;
                }
            }
            return latest;
        }
        return null;
    }

    public void setAutoBackupEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_AUTO_BACKUP_ENABLED, enabled).apply();
    }

    public boolean isAutoBackupEnabled() {
        return prefs.getBoolean(KEY_AUTO_BACKUP_ENABLED, false); // По умолчанию выключено
    }

    public long getLastAutoBackupTime() {
        return prefs.getLong(KEY_LAST_AUTO_BACKUP, 0);
    }

    public int getBackupCount() {
        File[] backups = getAvailableBackups();
        return backups != null ? backups.length : 0;
    }

    /**
     * Проверяет, есть ли резервные копии в стандартной папке
     */
    public boolean hasBackups() {
        File[] backups = getAvailableBackups();
        return backups != null && backups.length > 0;
    }

    public void cleanup() {
        executor.shutdown();
    }

    public static class BackupData {
        public List<Collection> collections;
        public List<Series> series;
        public List<SeriesCollectionCrossRef> relations;
        public long timestamp;
        public int version;
    }

    /**
     * Получает стандартный путь для резервных копий
     */
    public String getDefaultBackupPath() {
        File backupDir = getBackupDirectory();
        return backupDir.getAbsolutePath();
    }

    /**
     * Проверяет доступность стандартного пути
     */
    public boolean isDefaultPathAccessible() {
        File backupDir = getBackupDirectory();
        try {
            // Пробуем создать тестовый файл
            File testFile = new File(backupDir, "test.tmp");
            if (!backupDir.exists()) {
                backupDir.mkdirs();
            }
            boolean created = testFile.createNewFile();
            if (created) {
                testFile.delete();
                return true;
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }
}