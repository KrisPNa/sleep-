package com.example.seriestracker.data.backup;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;
import android.util.Log;

import androidx.core.content.ContextCompat;

import com.example.seriestracker.data.entities.Collection;
import com.example.seriestracker.data.entities.Series;
import com.example.seriestracker.data.entities.SeriesCollectionCrossRef;
import com.example.seriestracker.data.entities.MediaFile;
import com.example.seriestracker.data.repository.SeriesRepository;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.lang.reflect.Type;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.content.ContentResolver;
import android.net.Uri;

import java.io.IOException;

import com.example.seriestracker.data.backup.BackupFileManager;

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
            List<MediaFile> mediaFiles = repository.getAllMediaFilesSync();

            if (collections == null || series == null || relations == null || mediaFiles == null) {
                Log.e(TAG, "Failed to get data for backup");
                return;
            }

            Log.d(TAG, "Data retrieved: " +
                    collections.size() + " collections, " +
                    series.size() + " series, " +
                    relations.size() + " relations, " +
                    mediaFiles.size() + " media files");
            // Создаем временную директорию для резервной копии
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                    .format(new Date());
            File tempBackupDir = new File(context.getCacheDir(), "temp_backup_" + timeStamp);
            if (!tempBackupDir.exists() && !tempBackupDir.mkdirs()) {
                Log.e(TAG, "Failed to create temporary backup directory");
                return;
            }

            // Обновляем пути к медиафайлам, копируя файлы в директорию резервной копии
            List<MediaFile> updatedMediaFiles = new ArrayList<>();
            for (MediaFile mediaFile : mediaFiles) {
                MediaFile updatedMediaFile = new MediaFile(
                        mediaFile.getSeriesId(),
                        mediaFile.getFileUri(),
                        mediaFile.getFileType(),
                        mediaFile.getFileName()
                );
                updatedMediaFile.setId(mediaFile.getId());
                updatedMediaFile.setFilePath(mediaFile.getFilePath());
                updatedMediaFile.setFileSize(mediaFile.getFileSize());
                updatedMediaFile.setCreatedAt(mediaFile.getCreatedAt());
                updatedMediaFile.setDescription(mediaFile.getDescription());

                if (mediaFile.getFileUri() != null) {
                    try {
                        Uri fileUri = Uri.parse(mediaFile.getFileUri());
                        String newRelativePath = BackupFileManager.copyFileToBackupDir(
                                context,
                                fileUri,
                                mediaFile.getFileName(),
                                tempBackupDir.getAbsolutePath()
                        );
                        if (newRelativePath != null) {
                            updatedMediaFile.setFileUri(newRelativePath); // Сохраняем относительный путь
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "Could not backup media file: " + mediaFile.getFileName(), e);
                        // Используем оригинальный URI, если не удалось скопировать файл
                        updatedMediaFile.setFileUri(mediaFile.getFileUri());
                    }
                }
                updatedMediaFiles.add(updatedMediaFile);
            }

            // Обновляем пути к изображениям обложек сериалов
            List<Series> updatedSeries = new ArrayList<>();
            for (Series seriesItem : series) {
                Series updatedSeriesItem = new Series();
                updatedSeriesItem.setId(seriesItem.getId());
                updatedSeriesItem.setTitle(seriesItem.getTitle());
                updatedSeriesItem.setIsWatched(seriesItem.getIsWatched());
                updatedSeriesItem.setNotes(seriesItem.getNotes());
                updatedSeriesItem.setCreatedAt(seriesItem.getCreatedAt());
                updatedSeriesItem.setStatus(seriesItem.getStatus());
                updatedSeriesItem.setIsFavorite(seriesItem.getIsFavorite());
                updatedSeriesItem.setRating(seriesItem.getRating());
                updatedSeriesItem.setGenre(seriesItem.getGenre());
                updatedSeriesItem.setSeasons(seriesItem.getSeasons());
                updatedSeriesItem.setEpisodes(seriesItem.getEpisodes());

                if (seriesItem.getImageUri() != null) {
                    try {
                        Uri imageUri = Uri.parse(seriesItem.getImageUri());
                        String newRelativePath = BackupFileManager.copyFileToBackupDir(
                                context,
                                imageUri,
                                "series_cover_" + seriesItem.getId() + ".jpg",
                                tempBackupDir.getAbsolutePath()
                        );
                        if (newRelativePath != null) {
                            updatedSeriesItem.setImageUri(newRelativePath); // Сохраняем относительный путь
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "Could not backup series cover: " + seriesItem.getTitle(), e);
                        // Используем оригинальный URI, если не удалось скопировать файл
                        updatedSeriesItem.setImageUri(seriesItem.getImageUri());
                    }
                } else {
                    updatedSeriesItem.setImageUri(seriesItem.getImageUri());
                }
                updatedSeries.add(updatedSeriesItem);
            }

            // Создаем объект бэкапа
            BackupData backupData = new BackupData();
            backupData.collections = collections;
            backupData.series = updatedSeries;
            backupData.relations = relations;
            backupData.mediaFiles = updatedMediaFiles;
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


            String fileName = "backup_" + timeStamp + ".json";
            File backupFile = new File(backupDir, fileName);

            // Сохраняем файл
            try (FileOutputStream fos = new FileOutputStream(backupFile);
                 OutputStreamWriter writer = new OutputStreamWriter(fos)) {
                writer.write(json);
                writer.flush();
            }

            // Move the files directory to permanent location
            File backupFilesDir = new File(tempBackupDir, "files");
            File targetFilesDir = new File(backupDir, "files"); // Use consistent folder name instead of timestamped

            if (backupFilesDir.exists()) {

                // Ensure target directory exists and clean it for fresh backup
                if (targetFilesDir.exists()) {
                    deleteDirectory(targetFilesDir);
                }
                if (!targetFilesDir.mkdirs()) {
                    Log.e(TAG, "Failed to create target files directory: " + targetFilesDir.getAbsolutePath());
                }
                // Copy files to the consistent "files" directory
                if (!backupFilesDir.renameTo(targetFilesDir)) {
                    // If renameTo doesn't work, try to copy files
                    copyDirectory(backupFilesDir, targetFilesDir);
                    deleteDirectory(backupFilesDir);
                }
            }

// Optionally create a ZIP archive that contains both JSON and files
            String zipFileName = "backup_" + timeStamp + ".zip";
            File zipFile = new File(backupDir, zipFileName);

// Create a temporary directory to hold both JSON and files for archiving
            File combinedBackupDir = new File(context.getCacheDir(), "combined_backup_" + timeStamp);
            if (combinedBackupDir.exists()) {
                deleteDirectory(combinedBackupDir);
            }
            if (combinedBackupDir.mkdirs()) {
                // Copy the JSON file to the combined directory
                File jsonInCombined = new File(combinedBackupDir, backupFile.getName());
                try (java.io.FileInputStream fis = new java.io.FileInputStream(backupFile);
                     java.io.FileOutputStream fos = new java.io.FileOutputStream(jsonInCombined)) {
                    byte[] buffer = new byte[4096];
                    int length;
                    while ((length = fis.read(buffer)) > 0) {
                        fos.write(buffer, 0, length);
                    }
                }

                // Copy the files directory if it exists
                if (backupFilesDir.exists()) {
                    copyDirectory(backupFilesDir, new File(combinedBackupDir, "files"));
                } else if (targetFilesDir != null && targetFilesDir.exists()) { // Проверяем на null
                    copyDirectory(targetFilesDir, new File(combinedBackupDir, "files"));
                }
                // ... остальной код


                // Create ZIP archive
                File createdZip = BackupFileManager.createZipBackup(combinedBackupDir.getAbsolutePath(), zipFile.getAbsolutePath());
                if (createdZip != null) {
                    Log.d(TAG, "ZIP backup created successfully: " + zipFile.getAbsolutePath());
                } else {
                    Log.w(TAG, "Failed to create ZIP backup, continuing with regular backup");
                }

                // Clean up the temporary combined directory
                deleteDirectory(combinedBackupDir);
            }

            // Delete the temporary backup directory
            deleteDirectory(tempBackupDir);
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

                    (backupData.series != null ? backupData.series.size() : 0) + " series, " +
                    (backupData.mediaFiles != null ? backupData.mediaFiles.size() : 0) + " media files");

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
                    Series updatedSeries = new Series();
                    updatedSeries.setTitle(series.getTitle());
                    updatedSeries.setIsWatched(series.getIsWatched());
                    updatedSeries.setNotes(series.getNotes());
                    updatedSeries.setCreatedAt(series.getCreatedAt());
                    updatedSeries.setStatus(series.getStatus());
                    updatedSeries.setIsFavorite(series.getIsFavorite());
                    updatedSeries.setRating(series.getRating());
                    updatedSeries.setGenre(series.getGenre());
                    updatedSeries.setSeasons(series.getSeasons());
                    updatedSeries.setEpisodes(series.getEpisodes());

                    // Восстанавливаем файл обложки, если путь является относительным (означает, что это файл из резервной копии)
                    if (series.getImageUri() != null && series.getImageUri().startsWith("files/")) {
                        String restoredPath = BackupFileManager.restoreFileFromBackup(
                                context,
                                series.getImageUri(),
                                backupFile.getParent()
                        );
                        if (restoredPath != null) {
                            updatedSeries.setImageUri(restoredPath);
                        } else {
                            // Если не удалось восстановить файл, оставляем оригинальный путь
                            updatedSeries.setImageUri(series.getImageUri());
                        }
                    } else {
                        // Это обычный URI, оставляем без изменений
                        updatedSeries.setImageUri(series.getImageUri());
                    }

                    long oldId = series.getId();
                    // Сбрасываем ID для новой вставки
                    updatedSeries.setId(0);
                    long newId = repository.insertSeriesSync(updatedSeries);
                    if (newId > 0) {
                        seriesIdMap.put(oldId, newId);
                        Log.d(TAG, "Restored series: " + updatedSeries.getTitle() +
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

            // Восстанавливаем медиафайлы
            if (backupData.mediaFiles != null) {
                for (MediaFile mediaFile : backupData.mediaFiles) {
                    Long oldSeriesId = mediaFile.getSeriesId();
                    Long newSeriesId = seriesIdMap.get(oldSeriesId);

                    if (newSeriesId != null) {
                        // Если путь к файлу является относительным (означает, что это файл из резервной копии)
                        if (mediaFile.getFileUri() != null && mediaFile.getFileUri().startsWith("files/")) {
                            String restoredPath = BackupFileManager.restoreFileFromBackup(
                                    context,
                                    mediaFile.getFileUri(),
                                    backupFile.getParent()
                            );
                            if (restoredPath != null) {
                                mediaFile.setFileUri(restoredPath);
                                mediaFile.setFilePath(restoredPath);
                            } else {
                                // Если не удалось восстановить файл, оставляем оригинальный путь
                                mediaFile.setFileUri(mediaFile.getFileUri());
                            }
                        }
                        // Обновляем ID сериала для нового файла
                        mediaFile.setSeriesId(newSeriesId);
                        // Сбрасываем ID для новой вставки
                        mediaFile.setId(0);

                        long newMediaId = repository.insertMediaFileSync(mediaFile);
                        if (newMediaId > 0) {
                            Log.d(TAG, "Restored media file: " + mediaFile.getFileName() +
                                    " for series ID: " + newSeriesId);
                        }
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
     * Восстановление из резервной копии, используя URI, предоставленный через системный диалог
     */
    public boolean restoreFromUri(Uri backupUri) {
        // Check if this is a ZIP backup
        if (isZipBackupUri(backupUri)) {
            return restoreFromZipUri(backupUri);
        }

        // Handle JSON backup files
        try {
            Log.d(TAG, "Starting restore from URI: " + backupUri.toString());

            ContentResolver contentResolver = context.getContentResolver();

            // Создаем временную директорию для распаковки бэкапа
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                    .format(new Date());
            File tempRestoreDir = new File(context.getCacheDir(), "temp_restore_" + timeStamp);
            if (!tempRestoreDir.exists() && !tempRestoreDir.mkdirs()) {
                Log.e(TAG, "Failed to create temporary restore directory");
                return false;
            }
            // Читаем файл через ContentResolver
            StringBuilder jsonBuilder = new StringBuilder();
            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(contentResolver.openInputStream(backupUri)))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    jsonBuilder.append(line);
                }
            }

            String json = jsonBuilder.toString();
            Log.d(TAG, "Backup JSON length: " + json.length());

            // Парсим JSON
            Type type = new TypeToken<BackupData>() {}.getType();
            BackupData backupData = gson.fromJson(json, type);

            if (backupData == null) {
                Log.e(TAG, "Failed to parse backup file from URI");
                return false;
            }

            Log.d(TAG, "Parsed backup from URI: " +
                    (backupData.collections != null ? backupData.collections.size() : 0) + " collections, " +
                    (backupData.series != null ? backupData.series.size() : 0) + " series, " +
                    (backupData.mediaFiles != null ? backupData.mediaFiles.size() : 0) + " media files");

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
                        Log.d(TAG, "Restored collection from URI: " + collection.getName() +
                                " (old: " + oldId + ", new: " + newId + ")");
                    }
                }
            }

            // Восстанавливаем сериалы
            if (backupData.series != null) {
                for (Series series : backupData.series) {
                    Series updatedSeries = new Series();
                    updatedSeries.setTitle(series.getTitle());
                    updatedSeries.setIsWatched(series.getIsWatched());
                    updatedSeries.setNotes(series.getNotes());
                    updatedSeries.setCreatedAt(series.getCreatedAt());
                    updatedSeries.setStatus(series.getStatus());
                    updatedSeries.setIsFavorite(series.getIsFavorite());
                    updatedSeries.setRating(series.getRating());
                    updatedSeries.setGenre(series.getGenre());
                    updatedSeries.setSeasons(series.getSeasons());
                    updatedSeries.setEpisodes(series.getEpisodes());

                    // Восстанавливаем файл обложки, если путь является относительным (означает, что это файл из резервной копии)
                    if (series.getImageUri() != null && series.getImageUri().startsWith("files/")) {
                        String restoredPath = BackupFileManager.restoreFileFromBackup(
                                context,
                                series.getImageUri(),
                                tempRestoreDir.getAbsolutePath()
                        );
                        if (restoredPath != null) {
                            updatedSeries.setImageUri(restoredPath);
                        } else {
                            // Если не удалось восстановить файл, оставляем оригинальный путь
                            updatedSeries.setImageUri(series.getImageUri());
                        }
                    } else {
                        // Это обычный URI, оставляем без изменений
                        updatedSeries.setImageUri(series.getImageUri());
                    }

                    long oldId = series.getId();
                    // Сбрасываем ID для новой вставки
                    updatedSeries.setId(0);
                    long newId = repository.insertSeriesSync(updatedSeries);
                    if (newId > 0) {
                        seriesIdMap.put(oldId, newId);
                        Log.d(TAG, "Restored series from URI: " + updatedSeries.getTitle() +
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
                        Log.d(TAG, "Restored relation from URI: series " + newSeriesId +
                                " -> collection " + newCollectionId);
                    }
                }
            }

            // Восстанавливаем медиафайлы
            if (backupData.mediaFiles != null) {
                for (MediaFile mediaFile : backupData.mediaFiles) {
                    Long oldSeriesId = mediaFile.getSeriesId();
                    Long newSeriesId = seriesIdMap.get(oldSeriesId);

                    if (newSeriesId != null) {
                        // Если путь к файлу является относительным (означает, что это файл из резервной копии)
                        if (mediaFile.getFileUri() != null && mediaFile.getFileUri().startsWith("files/")) {
                            String restoredPath = BackupFileManager.restoreFileFromBackup(
                                    context,
                                    mediaFile.getFileUri(),
                                    tempRestoreDir.getAbsolutePath()
                            );
                            if (restoredPath != null) {
                                mediaFile.setFileUri(restoredPath);
                                mediaFile.setFilePath(restoredPath);
                            } else {
                                // Если не удалось восстановить файл, оставляем оригинальный путь
                                mediaFile.setFileUri(mediaFile.getFileUri());
                            }
                        }

                        // Обновляем ID сериала для нового файла
                        mediaFile.setSeriesId(newSeriesId);
                        // Сбрасываем ID для новой вставки
                        mediaFile.setId(0);

                        long newMediaId = repository.insertMediaFileSync(mediaFile);
                        if (newMediaId > 0) {
                            Log.d(TAG, "Restored media file from URI: " + mediaFile.getFileName() +
                                    " for series ID: " + newSeriesId);
                        }
                    }
                }
            }


            // Удаляем временную директорию
            deleteDirectory(tempRestoreDir);

            Log.i(TAG, "Restore from URI completed successfully");
            return true;

        } catch (Exception e) {
            Log.e(TAG, "Error restoring from backup URI", e);
            return false;
        }
    }
    /**
     * Получает директорию для резервных копий
     * По умолчанию: /storage/emulated/0/Download/SeriesTracker/backups для Android 10+
     * или /storage/emulated/0/Backup/SeriesTracker/backups для более ранних версий
     * Эта директория не удаляется при удалении приложения
     */
    private File getBackupDirectory() {
        // Проверяем наличие необходимых разрешений
        boolean hasWritePermission = ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED;

        // Для Android 10+ (API 29+) используем scoped storage
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Используем папку Download для бэкапов, так как это разрешено в scoped storage
            File backupDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "SeriesTracker");
            if (!backupDir.exists()) {
                backupDir.mkdirs();
            }

            if (backupDir.exists()) {
                return new File(backupDir, "backups");
            }
        }
        // Для старых версий Android, если есть разрешение
        else if (hasWritePermission) {
            // Используем общую директорию, которая остается после удаления приложения
            File externalStorageDir = Environment.getExternalStoragePublicDirectory("Backup");
            File appBackupDir = new File(externalStorageDir, "SeriesTracker");

            // Создаем директорию приложения в общей папке бэкапов
            if (!appBackupDir.exists()) {
                appBackupDir.mkdirs();
            }

            if (appBackupDir.exists()) {
                return new File(appBackupDir, "backups");
            }
        }

        // Fallback на стандартную папку приложения если нет доступа к общей директории
        File externalFilesDir = context.getExternalFilesDir(null);
        if (externalFilesDir != null) {
            return new File(externalFilesDir, "backups");
        }

        // Fallback на внутреннее хранилище
        return new File(context.getFilesDir(), "backups");
    }

    /**
     * Ищет резервные копии в основной папке и подпапках
     */
    public File[] getAvailableBackups() {
        List<File> backupFiles = new java.util.ArrayList<>();

        // Ищем в основной папке (в зависимости от версии Android)
        File backupDir = getBackupDirectory();
        if (backupDir.exists()) {
            findBackupFiles(backupDir, backupFiles);
        }

        // Также ищем в старой папке приложения для совместимости
        File oldBackupDir = getOldBackupDirectory();
        if (!backupDir.equals(oldBackupDir) && oldBackupDir.exists()) {
            findBackupFiles(oldBackupDir, backupFiles);
        }

        // Ищем в альтернативных папках для совместимости
        File alternativeBackupDir = getAlternativeBackupDirectory();
        if (!backupDir.equals(alternativeBackupDir) && !oldBackupDir.equals(alternativeBackupDir) && alternativeBackupDir.exists()) {
            findBackupFiles(alternativeBackupDir, backupFiles);
        }

        return backupFiles.toArray(new File[0]);
    }

    /**
     * Возвращает старую директорию резервных копий (внутри папки приложения)
     */
    private File getOldBackupDirectory() {
        File externalFilesDir = context.getExternalFilesDir(null);
        if (externalFilesDir != null) {
            return new File(externalFilesDir, "backups");
        }
        return new File(context.getFilesDir(), "backups");
    }

    /**
     * Возвращает альтернативную директорию для совместимости (старая Backup папка)
     */
    private File getAlternativeBackupDirectory() {
        // Для совместимости с предыдущими версиями
        File externalStorageDir = Environment.getExternalStoragePublicDirectory("Backup");
        File appBackupDir = new File(externalStorageDir, "SeriesTracker");
        if (appBackupDir.exists()) {
            return new File(appBackupDir, "backups");
        }
        return new File(context.getFilesDir(), "backups");
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
        public List<MediaFile> mediaFiles;
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

    /**
     * Получает путь к общей папке резервных копий
     */
    public String getCommonBackupPath() {
        // Проверяем наличие необходимых разрешений
        boolean hasWritePermission = ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED;

        // Для Android 10+ (API 29+) используем папку Downloads
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            File backupDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "SeriesTracker");
            return backupDir.getAbsolutePath();
        }
        // Для старых версий Android, если есть разрешение
        else if (hasWritePermission) {
            File externalStorageDir = Environment.getExternalStoragePublicDirectory("Backup");
            File appBackupDir = new File(externalStorageDir, "SeriesTracker");
            return appBackupDir.getAbsolutePath();
        }

        // Fallback на директорию приложения
        File externalFilesDir = context.getExternalFilesDir(null);
        if (externalFilesDir != null) {
            return new File(externalFilesDir, "backups").getAbsolutePath();
        }

        return new File(context.getFilesDir(), "backups").getAbsolutePath();
    }

    /**
     * Копирует содержимое одной директории в другую
     */
    private void copyDirectory(File sourceDir, File targetDir) {
        if (!targetDir.exists() && !targetDir.mkdirs()) {
            Log.e(TAG, "Failed to create target directory: " + targetDir.getAbsolutePath());
            return;
        }

        File[] files = sourceDir.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            if (file.isDirectory()) {
                File targetSubDir = new File(targetDir, file.getName());
                copyDirectory(file, targetSubDir);
            } else {
                File targetFile = new File(targetDir, file.getName());
                try {
                    java.nio.file.Files.copy(file.toPath(), targetFile.toPath(),
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                } catch (Exception e) {
                    Log.e(TAG, "Error copying file: " + file.getAbsolutePath() + " to " + targetFile.getAbsolutePath(), e);

                    // Резервный способ копирования
                    try (java.io.FileInputStream fis = new java.io.FileInputStream(file);
                         java.io.FileOutputStream fos = new java.io.FileOutputStream(targetFile)) {
                        byte[] buffer = new byte[4096];
                        int length;
                        while ((length = fis.read(buffer)) > 0) {
                            fos.write(buffer, 0, length);
                        }
                    } catch (Exception copyEx) {
                        Log.e(TAG, "Error copying file (fallback): " + file.getAbsolutePath(), copyEx);
                    }
                }
            }
        }
    }

    /**
     * Рекурсивно удаляет директорию и все её содержимое
     */
    private void deleteDirectory(File dir) {
        if (dir == null || !dir.exists()) {
            return;
        }

        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    deleteDirectory(file);
                } else {
                    file.delete();
                }
            }
        }

        dir.delete();
    }

    /**
     * Восстановление из ZIP резервной копии, используя URI, предоставленный через системный диалог
     */
    public boolean restoreFromZipUri(Uri backupUri) {
        try {
            Log.d(TAG, "Starting restore from ZIP URI: " + backupUri.toString());

            ContentResolver contentResolver = context.getContentResolver();

            // Create a temporary file to store the downloaded ZIP
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                    .format(new Date());
            File tempZipFile = new File(context.getCacheDir(), "temp_backup_" + timeStamp + ".zip");

            // Copy the ZIP file from URI to local temporary file
            try (java.io.InputStream inputStream = contentResolver.openInputStream(backupUri);
                 java.io.FileOutputStream outputStream = new java.io.FileOutputStream(tempZipFile)) {
                if (inputStream == null) {
                    Log.e(TAG, "Input stream is null for URI: " + backupUri);
                    return false;
                }

                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
                outputStream.flush();
            }

            // Create a temporary directory to extract the ZIP
            File extractDir = new File(context.getCacheDir(), "extracted_backup_" + timeStamp);
            if (!extractDir.exists() && !extractDir.mkdirs()) {
                Log.e(TAG, "Failed to create extraction directory");
                return false;
            }

            // Extract the ZIP file
            boolean extracted = BackupFileManager.extractZipBackup(tempZipFile.getAbsolutePath(), extractDir.getAbsolutePath());
            if (!extracted) {
                Log.e(TAG, "Failed to extract ZIP backup");
                return false;
            }

            // Find the JSON file in the extracted directory
            File[] jsonFiles = extractDir.listFiles((dir, name) -> name.endsWith(".json"));
            if (jsonFiles == null || jsonFiles.length == 0) {
                Log.e(TAG, "No JSON backup file found in ZIP archive");
                return false;
            }

            File jsonFile = jsonFiles[0]; // Take the first JSON file found

            // Read the JSON file
            StringBuilder jsonBuilder = new StringBuilder();
            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.FileReader(jsonFile))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    jsonBuilder.append(line);
                }
            }

            String json = jsonBuilder.toString();
            Log.d(TAG, "Backup JSON length: " + json.length());

            // Parse JSON
            Type type = new TypeToken<BackupData>() {}.getType();
            BackupData backupData = gson.fromJson(json, type);

            if (backupData == null) {
                Log.e(TAG, "Failed to parse backup file from ZIP");
                return false;
            }

            Log.d(TAG, "Parsed backup from ZIP: " +
                    (backupData.collections != null ? backupData.collections.size() : 0) + " collections, " +
                    (backupData.series != null ? backupData.series.size() : 0) + " series, " +
                    (backupData.mediaFiles != null ? backupData.mediaFiles.size() : 0) + " media files");

            // Clear current data
            repository.deleteAllData();

            // Maps for matching old and new IDs
            Map<Long, Long> collectionIdMap = new HashMap<>();
            Map<Long, Long> seriesIdMap = new HashMap<>();

            // Restore collections
            if (backupData.collections != null) {
                for (Collection collection : backupData.collections) {
                    long oldId = collection.getId();
                    // Reset ID for new insertion
                    collection.setId(0);
                    long newId = repository.insertCollectionSync(collection);
                    if (newId > 0) {
                        collectionIdMap.put(oldId, newId);
                        Log.d(TAG, "Restored collection from ZIP: " + collection.getName() +
                                " (old: " + oldId + ", new: " + newId + ")");
                    }
                }
            }

            // Restore series with cover images handling
            if (backupData.series != null) {
                for (Series series : backupData.series) {
                    Series updatedSeries = new Series();
                    updatedSeries.setTitle(series.getTitle());
                    updatedSeries.setIsWatched(series.getIsWatched());
                    updatedSeries.setNotes(series.getNotes());
                    updatedSeries.setCreatedAt(series.getCreatedAt());
                    updatedSeries.setStatus(series.getStatus());
                    updatedSeries.setIsFavorite(series.getIsFavorite());
                    updatedSeries.setRating(series.getRating());
                    updatedSeries.setGenre(series.getGenre());
                    updatedSeries.setSeasons(series.getSeasons());
                    updatedSeries.setEpisodes(series.getEpisodes());

                    // Restore cover image file if path is relative (means it's a file from the backup)
                    if (series.getImageUri() != null && series.getImageUri().startsWith("files/")) {
                        String restoredPath = BackupFileManager.restoreFileFromBackup(
                                context,
                                series.getImageUri(),
                                extractDir.getAbsolutePath()
                        );
                        if (restoredPath != null) {
                            updatedSeries.setImageUri(restoredPath);
                        } else {
                            // If unable to restore file, keep the original path
                            updatedSeries.setImageUri(series.getImageUri());
                        }
                    } else {
                        // This is a regular URI, keep as is
                        updatedSeries.setImageUri(series.getImageUri());
                    }

                    long oldId = series.getId();
                    // Reset ID for new insertion
                    updatedSeries.setId(0);
                    long newId = repository.insertSeriesSync(updatedSeries);
                    if (newId > 0) {
                        seriesIdMap.put(oldId, newId);
                        Log.d(TAG, "Restored series from ZIP: " + updatedSeries.getTitle() +
                                " (old: " + oldId + ", new: " + newId + ")");
                    }
                }
            }

            // Restore relations
            if (backupData.relations != null) {
                for (SeriesCollectionCrossRef relation : backupData.relations) {
                    Long newSeriesId = seriesIdMap.get(relation.getSeriesId());
                    Long newCollectionId = collectionIdMap.get(relation.getCollectionId());

                    if (newSeriesId != null && newCollectionId != null) {
                        SeriesCollectionCrossRef newRelation = new SeriesCollectionCrossRef(
                                newSeriesId, newCollectionId);
                        newRelation.setIsWatched(relation.getIsWatched());
                        repository.insertCrossRefSync(newRelation);
                        Log.d(TAG, "Restored relation from ZIP: series " + newSeriesId +
                                " -> collection " + newCollectionId);
                    }
                }
            }

            // Restore media files
            if (backupData.mediaFiles != null) {
                for (MediaFile mediaFile : backupData.mediaFiles) {
                    Long oldSeriesId = mediaFile.getSeriesId();
                    Long newSeriesId = seriesIdMap.get(oldSeriesId);

                    if (newSeriesId != null) {
                        // If file path is relative (means it's a file from the backup)
                        if (mediaFile.getFileUri() != null && mediaFile.getFileUri().startsWith("files/")) {
                            String restoredPath = BackupFileManager.restoreFileFromBackup(
                                    context,
                                    mediaFile.getFileUri(),
                                    extractDir.getAbsolutePath()
                            );
                            if (restoredPath != null) {
                                mediaFile.setFileUri(restoredPath);
                                mediaFile.setFilePath(restoredPath);
                            } else {
                                // If unable to restore file, keep the original path
                                mediaFile.setFileUri(mediaFile.getFileUri());
                            }
                        }

                        // Update series ID for the new file
                        mediaFile.setSeriesId(newSeriesId);
                        // Reset ID for new insertion
                        mediaFile.setId(0);

                        long newMediaId = repository.insertMediaFileSync(mediaFile);
                        if (newMediaId > 0) {
                            Log.d(TAG, "Restored media file from ZIP: " + mediaFile.getFileName() +
                                    " for series ID: " + newSeriesId);
                        }
                    }
                }
            }

            // Clean up temporary files
            tempZipFile.delete();
            deleteDirectory(extractDir);

            Log.i(TAG, "Restore from ZIP completed successfully");
            return true;

        } catch (Exception e) {
            Log.e(TAG, "Error restoring from ZIP backup URI", e);
            return false;
        }
    }

    /**
     * Проверяет, является ли файл ZIP архивом
     */
    public boolean isZipBackupFile(File backupFile) {
        return backupFile.getName().toLowerCase().endsWith(".zip");
    }

    /**
     * Проверяет, является ли файл ZIP архивом по URI
     */
    public boolean isZipBackupUri(Uri backupUri) {
        String uriString = backupUri.toString().toLowerCase();
        return uriString.endsWith(".zip") || uriString.contains(".zip");
    }
}