
package com.example.seriestracker.data.backup;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Build;
import android.os.Environment;
import android.provider.OpenableColumns;
import android.util.Log;
import java.util.UUID;

import androidx.core.content.ContextCompat;

import com.example.seriestracker.data.entities.Collection;
import com.example.seriestracker.data.entities.Series;
import com.example.seriestracker.data.entities.SeriesCollectionCrossRef;
import com.example.seriestracker.data.entities.MediaFile;
import com.example.seriestracker.data.repository.SeriesRepository;
import com.example.seriestracker.data.watchlinks.WatchSearchSitesStore;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.MalformedJsonException;

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
import com.example.seriestracker.utils.MediaStorageHelper;

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

    private static final int MAX_BACKUP_FILES = 5;

    public interface BackupProgressListener {
        void onProgress(int current, int total, String message);
    }

    public interface BackupCompleteListener {
        void onComplete(BackupResult result);
    }

    public static class BackupResult {
        public boolean success;
        public String errorMessage;
        public String backupPath;
    }

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
                createBackup(null);
            } catch (Exception e) {
                Log.e(TAG, "Auto backup failed", e);
            }
        });
    }

    public void createManualBackup() {
        createManualBackup(null, null);
    }

    public void createManualBackup(Runnable onComplete) {
        createManualBackup(null, result -> {
            if (onComplete != null) {
                onComplete.run();
            }
        });
    }

    public void createManualBackup(BackupProgressListener progressListener, BackupCompleteListener onComplete) {
        executor.execute(() -> {
            BackupResult result = createBackup(progressListener);
            if (onComplete != null) {
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> onComplete.onComplete(result));
            }
        });
    }

    private BackupResult createBackup(BackupProgressListener progressListener) {
        BackupResult result = new BackupResult();
        BackupFileManager.beginBackupSession();
        File tempBackupDir = null;

        try {
            Log.d(TAG, "Creating backup...");
            reportProgress(progressListener, 0, 100, "Подготовка данных...");

            List<Collection> collections = repository.getAllCollectionsSync();
            List<Series> series = repository.getAllSeriesSync();
            List<SeriesCollectionCrossRef> relations = repository.getAllRelationsSync();
            List<MediaFile> mediaFiles = repository.getAllMediaFilesSync();

            if (collections == null || series == null || relations == null || mediaFiles == null) {
                result.errorMessage = "Не удалось загрузить данные из базы";
                return result;
            }

            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                    .format(new Date());
            tempBackupDir = new File(context.getCacheDir(), "temp_backup_" + timeStamp);
            if (!tempBackupDir.exists() && !tempBackupDir.mkdirs()) {
                result.errorMessage = "Не удалось создать временную папку";
                return result;
            }

            int totalItems = mediaFiles.size() + series.size();
            int processedItems = 0;

            List<MediaFile> updatedMediaFiles = new ArrayList<>();
            for (MediaFile mediaFile : mediaFiles) {
                processedItems++;
                reportProgress(progressListener, processedItems, totalItems,
                        "Медиа: " + (mediaFile.getFileName() != null ? mediaFile.getFileName() : "файл"));
                updatedMediaFiles.add(copyMediaFileForBackup(mediaFile, tempBackupDir));
            }

            List<Series> updatedSeries = new ArrayList<>();
            for (Series seriesItem : series) {
                processedItems++;
                reportProgress(progressListener, processedItems, totalItems,
                        "Сериал: " + seriesItem.getTitle());
                updatedSeries.add(copySeriesCoverForBackup(seriesItem, tempBackupDir));
            }

            BackupData backupData = new BackupData();
            backupData.collections = collections;
            backupData.series = updatedSeries;
            backupData.relations = relations;
            backupData.mediaFiles = updatedMediaFiles;
            backupData.watchSearchSites = WatchSearchSitesStore.getSitesText(context);
            backupData.timestamp = System.currentTimeMillis();
            backupData.version = 2;

            reportProgress(progressListener, totalItems, totalItems, "Сохранение JSON...");
            String jsonFileName = "backup_" + timeStamp + ".json";
            File jsonFile = new File(tempBackupDir, jsonFileName);
            try (FileOutputStream fos = new FileOutputStream(jsonFile);
                 OutputStreamWriter writer = new OutputStreamWriter(fos, java.nio.charset.StandardCharsets.UTF_8)) {
                writer.write(gson.toJson(backupData));
                writer.flush();
            }

            File backupDir = getBackupDirectory();
            if (!backupDir.exists() && !backupDir.mkdirs()) {
                result.errorMessage = "Не удалось создать папку резервных копий";
                return result;
            }

            reportProgress(progressListener, totalItems, totalItems, "Создание ZIP-архива...");
            String zipFileName = "backup_" + timeStamp + ".zip";
            File zipFile = new File(backupDir, zipFileName);
            File createdZip = BackupFileManager.createZipBackup(tempBackupDir.getAbsolutePath(), zipFile.getAbsolutePath());
            if (createdZip == null || !createdZip.exists() || createdZip.length() == 0) {
                result.errorMessage = "Не удалось создать ZIP-архив";
                return result;
            }

            pruneOldBackups(backupDir);
            prefs.edit().putLong(KEY_LAST_AUTO_BACKUP, System.currentTimeMillis()).apply();

            result.success = true;
            result.backupPath = createdZip.getAbsolutePath();
            Log.i(TAG, "Backup created successfully: " + result.backupPath);
            return result;
        } catch (Exception e) {
            Log.e(TAG, "Error creating backup", e);
            result.errorMessage = e.getMessage() != null ? e.getMessage() : "Неизвестная ошибка";
            return result;
        } finally {
            BackupFileManager.endBackupSession();
            if (tempBackupDir != null) {
                deleteDirectory(tempBackupDir);
            }
        }
    }

    private MediaFile copyMediaFileForBackup(MediaFile mediaFile, File tempBackupDir) {
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
                String internalPath = MediaStorageHelper.getInternalFilePath(context, mediaFile.getFileUri());
                String newRelativePath;
                BackupFileManager.BackupMediaKind kind = "video".equals(mediaFile.getFileType())
                        ? BackupFileManager.BackupMediaKind.VIDEO
                        : BackupFileManager.BackupMediaKind.PHOTO;
                if (internalPath != null) {
                    newRelativePath = BackupFileManager.copyInternalFileToBackupWithDedup(
                            context, internalPath, mediaFile.getFileName(), tempBackupDir.getAbsolutePath(), kind);
                } else {
                    Uri fileUri = MediaStorageHelper.resolveLoadUri(mediaFile.getFileUri());
                    newRelativePath = BackupFileManager.copyUriToBackupWithDedup(
                            context, fileUri, mediaFile.getFileName(), tempBackupDir.getAbsolutePath(), kind);
                }
                if (newRelativePath != null) {
                    updatedMediaFile.setFileUri(newRelativePath);
                }
            } catch (Exception e) {
                Log.w(TAG, "Could not backup media file: " + mediaFile.getFileName(), e);
            }
        }
        return updatedMediaFile;
    }

    private Series copySeriesCoverForBackup(Series seriesItem, File tempBackupDir) {
        Series updatedSeriesItem = new Series();
        updatedSeriesItem.setId(seriesItem.getId());
        updatedSeriesItem.setTitle(seriesItem.getTitle());
        updatedSeriesItem.setIsWatched(seriesItem.getIsWatched());
        updatedSeriesItem.setNotes(seriesItem.getNotes());
        updatedSeriesItem.setWatchUrl(seriesItem.getWatchUrl());
        updatedSeriesItem.setWatchAt(seriesItem.getWatchAt());
        updatedSeriesItem.setCreatedAt(seriesItem.getCreatedAt());
        updatedSeriesItem.setStatus(seriesItem.getStatus());
        updatedSeriesItem.setIsFavorite(seriesItem.getIsFavorite());
        updatedSeriesItem.setRating(seriesItem.getRating());
        updatedSeriesItem.setGenre(seriesItem.getGenre());
        updatedSeriesItem.setSeasons(seriesItem.getSeasons());
        updatedSeriesItem.setEpisodes(seriesItem.getEpisodes());

        if (seriesItem.getImageUri() != null) {
            try {
                String internalPath = MediaStorageHelper.getInternalFilePath(context, seriesItem.getImageUri());
                String newRelativePath;
                if (internalPath != null) {
                    newRelativePath = BackupFileManager.copyInternalFileToBackupWithDedup(
                            context, internalPath, "series_cover_" + seriesItem.getId() + ".jpg",
                            tempBackupDir.getAbsolutePath(), BackupFileManager.BackupMediaKind.COVER);
                } else {
                    Uri imageUri = MediaStorageHelper.resolveLoadUri(seriesItem.getImageUri());
                    String originalFileName = getFileNameFromUri(context, imageUri);
                    if (originalFileName == null || originalFileName.isEmpty()) {
                        originalFileName = "series_cover_" + seriesItem.getId() + ".jpg";
                    }
                    newRelativePath = BackupFileManager.copyUriToBackupWithDedup(
                            context, imageUri, originalFileName, tempBackupDir.getAbsolutePath(),
                            BackupFileManager.BackupMediaKind.COVER);
                }
                if (newRelativePath != null) {
                    updatedSeriesItem.setImageUri(newRelativePath);
                } else {
                    updatedSeriesItem.setImageUri(seriesItem.getImageUri());
                }
            } catch (Exception e) {
                Log.w(TAG, "Could not backup series cover: " + seriesItem.getTitle(), e);
                updatedSeriesItem.setImageUri(seriesItem.getImageUri());
            }
        } else {
            updatedSeriesItem.setImageUri(seriesItem.getImageUri());
        }
        return updatedSeriesItem;
    }

    private void reportProgress(BackupProgressListener listener, int current, int total, String message) {
        if (listener == null) {
            return;
        }
        new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                listener.onProgress(current, total, message));
    }

    private void pruneOldBackups(File backupDir) {
        File[] zipBackups = backupDir.listFiles((dir, name) -> name.endsWith(".zip"));
        if (zipBackups == null || zipBackups.length <= MAX_BACKUP_FILES) {
            return;
        }

        java.util.Arrays.sort(zipBackups, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        for (int i = MAX_BACKUP_FILES; i < zipBackups.length; i++) {
            File oldZip = zipBackups[i];
            if (!oldZip.delete()) {
                Log.w(TAG, "Failed to delete old backup: " + oldZip.getAbsolutePath());
            }
            File oldJson = new File(oldZip.getParent(), oldZip.getName().replace(".zip", ".json"));
            if (oldJson.exists()) {
                oldJson.delete();
            }
            String timestamp = oldZip.getName().replace("backup_", "").replace(".zip", "");
            File oldFilesDir = new File(backupDir, "files_" + timestamp);
            if (oldFilesDir.exists()) {
                deleteDirectory(oldFilesDir);
            }
        }
    }

    public boolean restoreFromFile(File backupFile) {
        if (backupFile == null || !backupFile.exists()) {
            return false;
        }

        if (backupFile.getName().endsWith(".zip")) {
            return restoreFromZipFile(backupFile);
        }

        if (backupFile.getName().endsWith(".json")) {
            File zipFile = new File(backupFile.getParent(), backupFile.getName().replace(".json", ".zip"));
            if (zipFile.exists()) {
                return restoreFromZipFile(zipFile);
            }

            File baseDir = resolveJsonBackupBaseDir(backupFile);
            boolean isTempBaseDir = baseDir.getAbsolutePath().startsWith(context.getCacheDir().getAbsolutePath())
                    && baseDir.getName().startsWith("restore_base_");
            try {
                return restoreFromJsonFile(backupFile, baseDir);
            } finally {
                if (isTempBaseDir) {
                    deleteDirectory(baseDir);
                }
            }
        }

        return false;
    }

    private File resolveJsonBackupBaseDir(File jsonFile) {
        File parent = jsonFile.getParentFile();
        File filesDir = new File(parent, "files");
        if (filesDir.exists()) {
            return parent;
        }

        String name = jsonFile.getName();
        if (name.startsWith("backup_") && name.endsWith(".json")) {
            String timestamp = name.substring("backup_".length(), name.length() - ".json".length());
            File filesTimestampDir = new File(parent, "files_" + timestamp);
            if (filesTimestampDir.exists()) {
                File tempBase = new File(context.getCacheDir(), "restore_base_" + timestamp);
                File tempFilesDir = new File(tempBase, "files");
                deleteDirectory(tempBase);
                if (tempFilesDir.mkdirs()) {
                    copyDirectory(filesTimestampDir, tempFilesDir);
                    return tempBase;
                }
            }
        }

        return parent;
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
                    updatedSeries.setWatchUrl(series.getWatchUrl());
                    updatedSeries.setWatchAt(series.getWatchAt());
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
                                tempRestoreDir.getAbsolutePath(),
                                "covers"
                        );
                        if (restoredPath != null) {
                            updatedSeries.setImageUri(MediaStorageHelper.normalizeStoredValue(restoredPath));
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
                                mediaFile.setFileUri(MediaStorageHelper.normalizeStoredValue(restoredPath));
                                mediaFile.setFilePath(MediaStorageHelper.getInternalFilePath(context, restoredPath));
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

            restoreWatchSearchSitesFromBackup(backupData);

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
                } else if (file.getName().endsWith(".zip")) {
                    backupFiles.add(file);
                } else if (file.getName().endsWith(".json")) {
                    File zipFile = new File(file.getParent(), file.getName().replace(".json", ".zip"));
                    if (!zipFile.exists()) {
                        backupFiles.add(file);
                    }
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
        /** Сайты для поиска поля «Смотреть» (по одной ссылке на строку). */
        public String watchSearchSites;
        public long timestamp;
        public int version;
    }

    private void restoreWatchSearchSitesFromBackup(BackupData backupData) {
        if (backupData == null || backupData.watchSearchSites == null) {
            return;
        }
        WatchSearchSitesStore.setSitesText(context, backupData.watchSearchSites);
        Log.d(TAG, "Restored watch search sites settings");
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

                // Для избежания конфликта имен генерируем уникальное имя при необходимости
                File finalTargetFile = targetFile;
                if (targetFile.exists()) {
                    String nameWithoutExtension = "";
                    String extension = "";
                    String fileName = file.getName();
                    int dotIndex = fileName.lastIndexOf('.');
                    if (dotIndex > 0) {
                        nameWithoutExtension = fileName.substring(0, dotIndex);
                        extension = fileName.substring(dotIndex);
                    } else {
                        nameWithoutExtension = fileName;
                        extension = "";
                    }

                    String uniqueFileName = nameWithoutExtension + "_" + java.util.UUID.randomUUID().toString() + extension;
                    finalTargetFile = new File(targetDir, uniqueFileName);
                }

                try {
                    java.nio.file.Files.copy(file.toPath(), finalTargetFile.toPath(),
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                } catch (Exception e) {
                    Log.e(TAG, "Error copying file: " + file.getAbsolutePath() + " to " + finalTargetFile.getAbsolutePath(), e);

                    // Резервный способ копирования
                    try (java.io.FileInputStream fis = new java.io.FileInputStream(file);
                         java.io.FileOutputStream fos = new java.io.FileOutputStream(finalTargetFile)) {
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
     * Проверяет, совпадает ли содержимое двух файлов
     */
    private boolean areFilesContentEqual(File file1, File file2) {
        try (java.io.FileInputStream stream1 = new java.io.FileInputStream(file1);
             java.io.FileInputStream stream2 = new java.io.FileInputStream(file2)) {

            // Сравниваем размеры файлов
            if (file1.length() != file2.length()) {
                return false;
            }

            // Сравниваем содержимое побайтово
            byte[] buffer1 = new byte[4096];
            byte[] buffer2 = new byte[4096];

            int bytesRead1, bytesRead2;
            while ((bytesRead1 = stream1.read(buffer1)) != -1) {
                bytesRead2 = stream2.read(buffer2);

                if (bytesRead1 != bytesRead2) {
                    return false;
                }

                // Сравниваем буферы
                for (int i = 0; i < bytesRead1; i++) {
                    if (buffer1[i] != buffer2[i]) {
                        return false;
                    }
                }
            }

            return true;
        } catch (Exception e) {
            Log.w(TAG, "Error comparing file content: " + e.getMessage());
            // Если не можем сравнить, предполагаем, что файлы разные
            return false;
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

            // Создаем временный файл для хранения загруженного ZIP
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                    .format(new Date());
            File tempZipFile = new File(context.getCacheDir(), "temp_backup_" + timeStamp + ".zip");

            // Копируем ZIP файл из URI во временный локальный файл
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

            // Создаем временную директорию для извлечения ZIP
            File extractDir = new File(context.getCacheDir(), "extracted_backup_" + timeStamp);
            if (!extractDir.exists() && !extractDir.mkdirs()) {
                Log.e(TAG, "Failed to create extraction directory");
                return false;
            }

            // Извлекаем ZIP файл
            boolean extracted = BackupFileManager.extractZipBackup(tempZipFile.getAbsolutePath(), extractDir.getAbsolutePath());
            if (!extracted) {
                Log.e(TAG, "Failed to extract ZIP backup");
                return false;
            }

            // Находим JSON файл в извлеченной директории
            File[] jsonFiles = extractDir.listFiles((dir, name) -> name.endsWith(".json"));
            if (jsonFiles == null || jsonFiles.length == 0) {
                Log.e(TAG, "No JSON backup file found in ZIP archive");
                return false;
            }

            File jsonFile = jsonFiles[0]; // Берем первый найденный JSON файл

            // Теперь используем метод восстановления из JSON файла с указанием правильной директории
            boolean success = restoreFromJsonFile(jsonFile, extractDir);

            // Очищаем временные файлы
            tempZipFile.delete();
            deleteDirectory(extractDir);

            return success;

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

    public static class MergeMediaResult {
        public boolean success;
        public int restoredMediaCount;
        public int restoredCoverCount;
        public int restoredSeriesCount;
        public int matchedSeriesCount;
        public int skippedDuplicateCount;
        public int skippedSeriesNotFoundCount;
        public int skippedExistingSeriesCount;
        public int failedMediaCount;
        public int failedCoverCount;
        public int failedSeriesCount;
        public int backupSeriesCount;
        public int coversInBackupCount;
        public String errorMessage;
        public boolean missingSeriesRestore;

        public boolean hasRestoredAnything() {
            return restoredMediaCount > 0 || restoredCoverCount > 0 || restoredSeriesCount > 0;
        }

        public String getSummaryMessage() {
            if (errorMessage != null && !errorMessage.isEmpty()) {
                return errorMessage;
            }
            if (missingSeriesRestore) {
                if (restoredSeriesCount > 0) {
                    return "Восстановлено сериалов: " + restoredSeriesCount
                            + ", фото/видео: " + restoredMediaCount + ".";
                }
                if (backupSeriesCount == 0) {
                    return "В резервной копии нет сериалов.";
                }
                return "Все сериалы из копии уже есть в приложении ("
                        + backupSeriesCount + " в копии). Удалённые сериалы не найдены.";
            }
            if (!hasRestoredAnything()) {
                if (matchedSeriesCount == 0) {
                    return "Совпадений по названию не найдено. В копии: "
                            + backupSeriesCount + " сериалов.\n\n"
                            + "Этот режим только добавляет медиа к уже существующим сериалам. "
                            + "Чтобы вернуть удалённый сериал, выберите «Восстановить удалённые сериалы».";
                }
                return "Совпало сериалов: " + matchedSeriesCount
                        + ", но файлы не восстановлены (ошибок: "
                        + (failedMediaCount + failedCoverCount) + ").";
            }
            return "Восстановлено: " + restoredMediaCount + " фото/видео, "
                    + restoredCoverCount + " обложек. Совпало сериалов: " + matchedSeriesCount;
        }
    }

    private static class PreparedBackup {
        BackupData data;
        File mediaBaseDir;
        File tempDirToCleanup;
    }

    /**
     * Восстанавливает только фото, видео и обложки из резервной копии,
     * не удаляя текущие сериалы и не добавляя новые из копии.
     */
    public MergeMediaResult mergeMediaFromFile(File backupFile) {
        MergeMediaResult result = new MergeMediaResult();
        PreparedBackup prepared = null;
        try {
            prepared = prepareBackupFile(backupFile);
            if (prepared == null || prepared.data == null) {
                result.errorMessage = "Не удалось прочитать резервную копию";
                return result;
            }
            mergeMediaFromBackupData(prepared.data, prepared.mediaBaseDir, result);
            result.success = true;
        } catch (Exception e) {
            Log.e(TAG, "Error merging media from backup file", e);
            result.errorMessage = formatBackupError(e);
            result.success = false;
        } finally {
            if (prepared != null && prepared.tempDirToCleanup != null) {
                deleteDirectory(prepared.tempDirToCleanup);
            }
        }
        return result;
    }

    /**
     * Восстанавливает сериалы из резервной копии, которых сейчас нет в приложении.
     * Существующие сериалы не изменяются и не удаляются.
     */
    public MergeMediaResult restoreMissingSeriesFromFile(File backupFile) {
        MergeMediaResult result = new MergeMediaResult();
        result.missingSeriesRestore = true;
        PreparedBackup prepared = null;
        try {
            prepared = prepareBackupFile(backupFile, true);
            if (prepared == null || prepared.data == null) {
                result.errorMessage = "Не удалось прочитать резервную копию";
                return result;
            }
            restoreMissingSeriesFromBackupData(prepared.data, prepared.mediaBaseDir, result);
            result.success = true;
        } catch (Exception e) {
            Log.e(TAG, "Error restoring missing series from backup file", e);
            result.errorMessage = formatBackupError(e);
            result.success = false;
        } finally {
            if (prepared != null && prepared.tempDirToCleanup != null) {
                deleteDirectory(prepared.tempDirToCleanup);
            }
        }
        return result;
    }

    public MergeMediaResult restoreMissingSeriesFromUri(Uri backupUri) {
        MergeMediaResult result = new MergeMediaResult();
        result.missingSeriesRestore = true;
        File tempFile = null;
        try {
            tempFile = new File(context.getCacheDir(), "picked_restore_" + System.currentTimeMillis());
            try (InputStream inputStream = context.getContentResolver().openInputStream(backupUri);
                 FileOutputStream outputStream = new FileOutputStream(tempFile)) {
                if (inputStream == null) {
                    result.errorMessage = "Не удалось открыть выбранный файл";
                    return result;
                }
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
                outputStream.flush();
            }

            File backupFile = tempFile;
            if (isZipFile(tempFile)) {
                backupFile = new File(tempFile.getAbsolutePath() + ".zip");
                if (!tempFile.renameTo(backupFile)) {
                    backupFile = tempFile;
                } else {
                    tempFile = backupFile;
                }
            } else if (!isZipBackupUri(backupUri)) {
                File jsonFile = new File(tempFile.getAbsolutePath() + ".json");
                if (tempFile.renameTo(jsonFile)) {
                    tempFile = jsonFile;
                    backupFile = jsonFile;
                }
            }

            return restoreMissingSeriesFromFile(backupFile);
        } catch (Exception e) {
            Log.e(TAG, "Error restoring missing series from backup URI", e);
            result.errorMessage = formatBackupError(e);
            result.success = false;
            return result;
        } finally {
            if (tempFile != null && tempFile.exists()) {
                tempFile.delete();
            }
        }
    }

    public MergeMediaResult mergeMediaFromUri(Uri backupUri) {
        MergeMediaResult result = new MergeMediaResult();
        File tempFile = null;
        try {
            tempFile = new File(context.getCacheDir(), "picked_merge_" + System.currentTimeMillis());
            try (InputStream inputStream = context.getContentResolver().openInputStream(backupUri);
                 FileOutputStream outputStream = new FileOutputStream(tempFile)) {
                if (inputStream == null) {
                    result.errorMessage = "Не удалось открыть выбранный файл";
                    return result;
                }
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
                outputStream.flush();
            }

            File backupFile = tempFile;
            if (isZipFile(tempFile)) {
                backupFile = new File(tempFile.getAbsolutePath() + ".zip");
                if (!tempFile.renameTo(backupFile)) {
                    backupFile = tempFile;
                } else {
                    tempFile = backupFile;
                }
            } else if (!isZipBackupUri(backupUri)) {
                File jsonFile = new File(tempFile.getAbsolutePath() + ".json");
                if (tempFile.renameTo(jsonFile)) {
                    tempFile = jsonFile;
                    backupFile = jsonFile;
                }
            }

            return mergeMediaFromFile(backupFile);
        } catch (Exception e) {
            Log.e(TAG, "Error merging media from backup URI", e);
            result.errorMessage = formatBackupError(e);
            result.success = false;
            return result;
        } finally {
            if (tempFile != null && tempFile.exists()) {
                tempFile.delete();
            }
        }
    }

    private boolean isZipFile(File file) {
        if (file == null || !file.exists() || file.length() < 2) {
            return false;
        }
        if (file.getName().toLowerCase().endsWith(".zip")) {
            return true;
        }
        try (java.io.FileInputStream inputStream = new java.io.FileInputStream(file)) {
            int first = inputStream.read();
            int second = inputStream.read();
            return first == 'P' && second == 'K';
        } catch (IOException e) {
            Log.w(TAG, "Could not inspect backup file header", e);
            return false;
        }
    }

    private PreparedBackup prepareBackupFile(File backupFile) throws IOException {
        return prepareBackupFile(backupFile, false);
    }

    private PreparedBackup prepareBackupFile(File backupFile, boolean fullData) throws IOException {
        if (backupFile == null || !backupFile.exists()) {
            return null;
        }

        if (isZipBackupFile(backupFile) || isZipFile(backupFile)) {
            File extractDir = new File(context.getCacheDir(), "temp_merge_" + System.currentTimeMillis());
            if (!BackupFileManager.extractZipBackup(backupFile.getAbsolutePath(), extractDir.getAbsolutePath())) {
                deleteDirectory(extractDir);
                return null;
            }

            File jsonFile = findBackupJsonFile(extractDir);
            if (jsonFile == null) {
                deleteDirectory(extractDir);
                return null;
            }

            PreparedBackup prepared = new PreparedBackup();
            prepared.data = fullData ? parseBackupJsonFile(jsonFile) : parseBackupJsonForMerge(jsonFile);
            prepared.mediaBaseDir = extractDir;
            prepared.tempDirToCleanup = extractDir;
            return prepared;
        }

        if (backupFile.getName().endsWith(".json")) {
            File zipFile = new File(backupFile.getParent(), backupFile.getName().replace(".json", ".zip"));
            if (zipFile.exists()) {
                return prepareBackupFile(zipFile, fullData);
            }

            PreparedBackup prepared = new PreparedBackup();
            prepared.data = fullData ? parseBackupJsonFile(backupFile) : parseBackupJsonForMerge(backupFile);
            prepared.mediaBaseDir = resolveJsonBackupBaseDir(backupFile);
            if (prepared.mediaBaseDir.getName().startsWith("restore_base_")) {
                prepared.tempDirToCleanup = prepared.mediaBaseDir;
            }
            return prepared;
        }

        return null;
    }

    private File findBackupJsonFile(File directory) {
        if (directory == null || !directory.exists()) {
            return null;
        }

        File[] files = directory.listFiles();
        if (files == null) {
            return null;
        }

        for (File file : files) {
            if (file.isFile() && file.getName().endsWith(".json")) {
                return file;
            }
        }

        for (File file : files) {
            if (file.isDirectory()) {
                File nested = findBackupJsonFile(file);
                if (nested != null) {
                    return nested;
                }
            }
        }

        return null;
    }

    private BackupData parseBackupJsonForMerge(File jsonFile) throws IOException {
        if (isZipFile(jsonFile)) {
            throw new IOException("Выбран ZIP-файл. Используйте файл backup_....zip напрямую.");
        }

        String json = readTextFile(jsonFile);
        BackupData data = new BackupData();
        data.series = new ArrayList<>();
        data.mediaFiles = new ArrayList<>();

        try {
            JsonObject root = parseJsonRoot(json);

            if (root.has("series") && root.get("series").isJsonArray()) {
                for (JsonElement element : root.getAsJsonArray("series")) {
                    if (!element.isJsonObject()) {
                        continue;
                    }
                    JsonObject obj = element.getAsJsonObject();
                    Series series = new Series();
                    if (obj.has("id")) {
                        series.setId(obj.get("id").getAsLong());
                    }
                    if (obj.has("title") && !obj.get("title").isJsonNull()) {
                        series.setTitle(obj.get("title").getAsString());
                    }
                    if (obj.has("imageUri") && !obj.get("imageUri").isJsonNull()) {
                        series.setImageUri(obj.get("imageUri").getAsString());
                    }
                    if (series.getTitle() != null && !series.getTitle().isEmpty()) {
                        data.series.add(series);
                    }
                }
            }

            if (root.has("mediaFiles") && root.get("mediaFiles").isJsonArray()) {
                for (JsonElement element : root.getAsJsonArray("mediaFiles")) {
                    if (!element.isJsonObject()) {
                        continue;
                    }
                    JsonObject obj = element.getAsJsonObject();
                    MediaFile mediaFile = new MediaFile();
                    if (obj.has("id")) {
                        mediaFile.setId(obj.get("id").getAsLong());
                    }
                    if (obj.has("seriesId")) {
                        mediaFile.setSeriesId(obj.get("seriesId").getAsLong());
                    }
                    if (obj.has("fileUri") && !obj.get("fileUri").isJsonNull()) {
                        mediaFile.setFileUri(obj.get("fileUri").getAsString());
                    }
                    if (obj.has("fileType") && !obj.get("fileType").isJsonNull()) {
                        mediaFile.setFileType(obj.get("fileType").getAsString());
                    }
                    if (obj.has("fileName") && !obj.get("fileName").isJsonNull()) {
                        mediaFile.setFileName(obj.get("fileName").getAsString());
                    }
                    if (obj.has("fileSize")) {
                        mediaFile.setFileSize(obj.get("fileSize").getAsLong());
                    }
                    if (obj.has("createdAt")) {
                        mediaFile.setCreatedAt(obj.get("createdAt").getAsLong());
                    }
                    if (obj.has("description") && !obj.get("description").isJsonNull()) {
                        mediaFile.setDescription(obj.get("description").getAsString());
                    }
                    data.mediaFiles.add(mediaFile);
                }
            }
        } catch (Exception e) {
            throw new IOException(formatBackupError(e), e);
        }

        return data;
    }

    private JsonObject parseJsonRoot(String json) throws IOException {
        String trimmed = json == null ? "" : json.trim();
        if (trimmed.startsWith("\uFEFF")) {
            trimmed = trimmed.substring(1).trim();
        }
        if (trimmed.isEmpty()) {
            throw new IOException("Файл резервной копии пуст");
        }
        if (trimmed.startsWith("PK")) {
            throw new IOException("Это ZIP-архив, а не JSON. Выберите файл backup_....zip");
        }

        try {
            return JsonParser.parseString(trimmed).getAsJsonObject();
        } catch (Exception strictError) {
            try {
                JsonReader reader = new JsonReader(new java.io.StringReader(trimmed));
                reader.setLenient(true);
                JsonElement element = JsonParser.parseReader(reader);
                if (!element.isJsonObject()) {
                    throw new IOException("Неверный формат резервной копии");
                }
                return element.getAsJsonObject();
            } catch (Exception lenientError) {
                if (strictError.getMessage() != null) {
                    throw new IOException(formatBackupError(strictError), strictError);
                }
                throw new IOException(formatBackupError(lenientError), lenientError);
            }
        }
    }

    private String readTextFile(File file) throws IOException {
        StringBuilder jsonBuilder = new StringBuilder();
        try (java.io.InputStreamReader reader = new java.io.InputStreamReader(
                new java.io.FileInputStream(file), java.nio.charset.StandardCharsets.UTF_8);
             java.io.BufferedReader bufferedReader = new java.io.BufferedReader(reader)) {
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                jsonBuilder.append(line);
            }
        }
        return jsonBuilder.toString();
    }

    private String formatBackupError(Exception e) {
        if (e instanceof MalformedJsonException) {
            return "Файл повреждён или это не резервная копия. Выберите файл backup_....zip из папки SeriesTracker/backups.";
        }

        String message = e.getMessage();
        if (message == null && e.getCause() != null) {
            message = e.getCause().getMessage();
        }
        if (message != null && message.contains("MalformedJsonException")) {
            return "Файл повреждён или это не резервная копия. Выберите файл backup_....zip из папки SeriesTracker/backups.";
        }
        if (message != null && message.startsWith("Expected")) {
            return "Файл копии повреждён или имеет неподдерживаемый формат. Попробуйте выбрать .zip файл.";
        }
        if (message != null && !message.isEmpty()) {
            return message;
        }
        return "Неизвестная ошибка при чтении копии";
    }

    private BackupData parseBackupJsonFile(File jsonFile) throws IOException {
        String json = readTextFile(jsonFile);

        Type type = new TypeToken<BackupData>() {}.getType();
        return gson.fromJson(json, type);
    }

    private void restoreMissingSeriesFromBackupData(BackupData backupData, File mediaBaseDir, MergeMediaResult result) {
        result.backupSeriesCount = backupData.series != null ? backupData.series.size() : 0;

        java.util.Set<String> existingTitles = new java.util.HashSet<>();
        List<Series> currentSeries = repository.getAllSeriesSync();
        if (currentSeries != null) {
            for (Series current : currentSeries) {
                String normalizedTitle = normalizeTitle(current.getTitle());
                if (!normalizedTitle.isEmpty()) {
                    existingTitles.add(normalizedTitle);
                }
            }
        }

        Map<Long, Long> collectionIdMap = resolveCollectionIdMap(backupData.collections);
        Map<Long, Long> restoredSeriesIdMap = new HashMap<>();

        if (backupData.series != null) {
            for (Series series : backupData.series) {
                String normalizedTitle = normalizeTitle(series.getTitle());
                if (normalizedTitle.isEmpty()) {
                    continue;
                }
                if (existingTitles.contains(normalizedTitle)) {
                    result.skippedExistingSeriesCount++;
                    continue;
                }

                Series newSeries = cloneSeriesFromBackup(series);
                if (series.getImageUri() != null && series.getImageUri().startsWith("files/")) {
                    String restoredPath = restoreFileFromBackup(series.getImageUri(), mediaBaseDir, "covers");
                    if (restoredPath != null) {
                        newSeries.setImageUri(restoredPath);
                    }
                }

                newSeries.setId(0);
                long newId = repository.insertSeriesSync(newSeries);
                if (newId > 0) {
                    restoredSeriesIdMap.put(series.getId(), newId);
                    existingTitles.add(normalizedTitle);
                    result.restoredSeriesCount++;
                    Log.d(TAG, "Restored missing series: " + newSeries.getTitle() + " (new id: " + newId + ")");
                } else {
                    result.failedSeriesCount++;
                }
            }
        }

        if (backupData.relations != null) {
            for (SeriesCollectionCrossRef relation : backupData.relations) {
                Long newSeriesId = restoredSeriesIdMap.get(relation.getSeriesId());
                Long newCollectionId = collectionIdMap.get(relation.getCollectionId());
                if (newSeriesId != null && newCollectionId != null) {
                    SeriesCollectionCrossRef newRelation = new SeriesCollectionCrossRef(newSeriesId, newCollectionId);
                    newRelation.setIsWatched(relation.getIsWatched());
                    repository.insertCrossRefSync(newRelation);
                }
            }
        }

        if (backupData.mediaFiles != null) {
            List<MediaFile> mediaToInsert = new ArrayList<>();
            for (MediaFile mediaFile : backupData.mediaFiles) {
                Long newSeriesId = restoredSeriesIdMap.get(mediaFile.getSeriesId());
                if (newSeriesId == null) {
                    continue;
                }

                String storedUri = mediaFile.getFileUri();
                if (storedUri != null && storedUri.startsWith("files/")) {
                    String restoredPath = restoreFileFromBackup(storedUri, mediaBaseDir, "media");
                    if (restoredPath == null) {
                        result.failedMediaCount++;
                        continue;
                    }
                    storedUri = restoredPath;
                } else {
                    result.failedMediaCount++;
                    continue;
                }

                MediaFile newMediaFile = new MediaFile(
                        newSeriesId,
                        storedUri,
                        mediaFile.getFileType(),
                        mediaFile.getFileName()
                );
                newMediaFile.setFilePath(MediaStorageHelper.getInternalFilePath(context, storedUri));
                newMediaFile.setFileSize(mediaFile.getFileSize());
                newMediaFile.setCreatedAt(mediaFile.getCreatedAt());
                newMediaFile.setDescription(mediaFile.getDescription());
                mediaToInsert.add(newMediaFile);
            }

            int inserted = repository.insertMediaFilesSync(mediaToInsert);
            result.restoredMediaCount += inserted;
            result.failedMediaCount += Math.max(0, mediaToInsert.size() - inserted);
        }

        restoreWatchSearchSitesFromBackup(backupData);
    }

    private Series cloneSeriesFromBackup(Series source) {
        Series target = new Series();
        target.setTitle(source.getTitle());
        target.setImageUri(source.getImageUri());
        target.setIsWatched(source.getIsWatched());
        target.setNotes(source.getNotes());
        target.setWatchUrl(source.getWatchUrl());
        target.setWatchAt(source.getWatchAt());
        target.setDescription(source.getDescription());
        target.setCreatedAt(source.getCreatedAt() > 0 ? source.getCreatedAt() : System.currentTimeMillis());
        target.setStatus(source.getStatus() != null ? source.getStatus() : "planned");
        target.setIsFavorite(source.getIsFavorite());
        target.setRating(source.getRating());
        target.setGenre(source.getGenre());
        target.setSeasons(source.getSeasons());
        target.setEpisodes(source.getEpisodes());
        return target;
    }

    private Map<Long, Long> resolveCollectionIdMap(List<Collection> backupCollections) {
        Map<Long, Long> collectionIdMap = new HashMap<>();
        if (backupCollections == null || backupCollections.isEmpty()) {
            return collectionIdMap;
        }

        Map<String, Long> currentByName = new HashMap<>();
        List<Collection> currentCollections = repository.getAllCollectionsSync();
        if (currentCollections != null) {
            for (Collection current : currentCollections) {
                String normalizedName = normalizeTitle(current.getName());
                if (!normalizedName.isEmpty() && !currentByName.containsKey(normalizedName)) {
                    currentByName.put(normalizedName, current.getId());
                }
            }
        }

        for (Collection backupCollection : backupCollections) {
            String normalizedName = normalizeTitle(backupCollection.getName());
            if (normalizedName.isEmpty()) {
                continue;
            }

            Long currentId = currentByName.get(normalizedName);
            if (currentId != null) {
                collectionIdMap.put(backupCollection.getId(), currentId);
                continue;
            }

            Collection newCollection = new Collection();
            newCollection.setName(backupCollection.getName());
            newCollection.setCreatedAt(backupCollection.getCreatedAt());
            newCollection.setFavorite(backupCollection.isFavorite());
            newCollection.setColors(backupCollection.getColors());
            newCollection.setId(0);

            long newId = repository.insertCollectionSync(newCollection);
            if (newId > 0) {
                collectionIdMap.put(backupCollection.getId(), newId);
                currentByName.put(normalizedName, newId);
            }
        }

        return collectionIdMap;
    }

    private void mergeMediaFromBackupData(BackupData backupData, File mediaBaseDir, MergeMediaResult result) {
        result.backupSeriesCount = backupData.series != null ? backupData.series.size() : 0;
        result.coversInBackupCount = countCoversInBackup(backupData.series);

        Map<Long, Long> seriesIdMap = buildMergeSeriesIdMap(backupData.series);
        result.matchedSeriesCount = seriesIdMap.size();
        Log.d(TAG, "Merge media: backup series=" + result.backupSeriesCount
                + ", covers in backup=" + result.coversInBackupCount
                + ", matched=" + result.matchedSeriesCount);

        if (backupData.series != null) {
            for (Series series : backupData.series) {
                Long currentSeriesId = seriesIdMap.get(series.getId());
                if (currentSeriesId == null) {
                    continue;
                }

                if (series.getImageUri() != null && series.getImageUri().startsWith("files/")) {
                    Series existingSeries = repository.getSeriesByIdSync(currentSeriesId);
                    if (existingSeries != null) {
                        String restoredPath = restoreFileFromBackup(series.getImageUri(), mediaBaseDir, "covers");
                        if (restoredPath != null) {
                            existingSeries.setImageUri(restoredPath);
                            repository.updateSeriesSync(existingSeries);
                            result.restoredCoverCount++;
                        } else {
                            result.failedCoverCount++;
                            Log.w(TAG, "Failed to restore cover for series: " + series.getTitle()
                                    + ", path: " + series.getImageUri());
                        }
                    }
                }
            }
        }

        if (backupData.mediaFiles != null) {
            for (MediaFile mediaFile : backupData.mediaFiles) {
                Long currentSeriesId = seriesIdMap.get(mediaFile.getSeriesId());
                if (currentSeriesId == null) {
                    result.skippedSeriesNotFoundCount++;
                    continue;
                }

                if (repository.hasMediaFileWithNameSync(currentSeriesId, mediaFile.getFileName())) {
                    result.skippedDuplicateCount++;
                    continue;
                }

                String storedUri = mediaFile.getFileUri();
                if (storedUri != null && storedUri.startsWith("files/")) {
                    String restoredPath = restoreFileFromBackup(storedUri, mediaBaseDir, "media");
                    if (restoredPath == null) {
                        result.failedMediaCount++;
                        continue;
                    }
                    storedUri = restoredPath;
                } else {
                    result.failedMediaCount++;
                    continue;
                }

                MediaFile newMediaFile = new MediaFile(
                        currentSeriesId,
                        storedUri,
                        mediaFile.getFileType(),
                        mediaFile.getFileName()
                );
                newMediaFile.setFilePath(MediaStorageHelper.getInternalFilePath(context, storedUri));
                newMediaFile.setFileSize(mediaFile.getFileSize());
                newMediaFile.setCreatedAt(mediaFile.getCreatedAt());
                newMediaFile.setDescription(mediaFile.getDescription());

                if (repository.insertMediaFileSync(newMediaFile) > 0) {
                    result.restoredMediaCount++;
                } else {
                    result.failedMediaCount++;
                }
            }
        }
    }

    private int countCoversInBackup(List<Series> backupSeries) {
        if (backupSeries == null) {
            return 0;
        }
        int count = 0;
        for (Series series : backupSeries) {
            if (series.getImageUri() != null && series.getImageUri().startsWith("files/")) {
                count++;
            }
        }
        return count;
    }

    private Map<Long, Long> buildMergeSeriesIdMap(List<Series> backupSeries) {
        Map<Long, Long> seriesIdMap = new HashMap<>();
        if (backupSeries == null) {
            return seriesIdMap;
        }

        List<Series> currentSeries = repository.getAllSeriesSync();
        Map<String, Long> currentByTitle = new HashMap<>();
        if (currentSeries != null) {
            for (Series current : currentSeries) {
                String normalizedTitle = normalizeTitle(current.getTitle());
                if (!normalizedTitle.isEmpty() && !currentByTitle.containsKey(normalizedTitle)) {
                    currentByTitle.put(normalizedTitle, current.getId());
                }
            }
        }

        for (Series series : backupSeries) {
            String normalizedTitle = normalizeTitle(series.getTitle());
            if (!normalizedTitle.isEmpty()) {
                Long currentId = currentByTitle.get(normalizedTitle);
                if (currentId != null) {
                    seriesIdMap.put(series.getId(), currentId);
                }
            }
        }
        return seriesIdMap;
    }

    private String normalizeTitle(String title) {
        if (title == null) {
            return "";
        }
        return title.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Восстановление из всех доступных резервных копий
     */
    public boolean restoreFromAllBackups() {
        try {
            Log.d(TAG, "Starting restore from all available backups");

            File[] backupFiles = getAvailableBackups();
            if (backupFiles == null || backupFiles.length == 0) {
                Log.e(TAG, "No backup files found");
                return false;
            }

            // Сортируем файлы по времени модификации (от старых к новым)
            java.util.Arrays.sort(backupFiles, (f1, f2) -> Long.compare(f1.lastModified(), f2.lastModified()));

            // Очищаем текущие данные
            repository.deleteAllData();

            // Мапы для соответствия старых и новых ID
            Map<Long, Long> collectionIdMap = new HashMap<>();
            Map<Long, Long> seriesIdMap = new HashMap<>();

            boolean success = true;

            // Обрабатываем каждую резервную копию
            for (File backupFile : backupFiles) {
                Log.d(TAG, "Processing backup file: " + backupFile.getName());

                if (isZipBackupFile(backupFile)) {
                    // Временно извлекаем ZIP для обработки
                    String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                            .format(new Date());
                    File extractDir = new File(context.getCacheDir(), "temp_extract_" + timeStamp);

                    boolean extracted = BackupFileManager.extractZipBackup(backupFile.getAbsolutePath(), extractDir.getAbsolutePath());
                    if (!extracted) {
                        Log.e(TAG, "Failed to extract ZIP backup: " + backupFile.getName());
                        continue;
                    }

                    // Находим JSON файл в извлеченной папке
                    File[] jsonFiles = extractDir.listFiles((dir, name) -> name.endsWith(".json"));
                    if (jsonFiles != null && jsonFiles.length > 0) {
                        success &= processBackupFile(jsonFiles[0], collectionIdMap, seriesIdMap);
                    }

                    // Удаляем временную папку
                    deleteDirectory(extractDir);
                } else {
                    success &= processBackupFile(backupFile, collectionIdMap, seriesIdMap);
                }
            }

            Log.i(TAG, "Restore from all backups completed");
            return success;

        } catch (Exception e) {
            Log.e(TAG, "Error restoring from all backups", e);
            return false;
        }
    }

    /**
     * Обработка одного файла резервной копии и объединение с существующими данными
     */
    private boolean processBackupFile(File backupFile, Map<Long, Long> collectionIdMap, Map<Long, Long> seriesIdMap) {
        File mediaBaseDir = resolveJsonBackupBaseDir(backupFile);
        boolean cleanupTemp = mediaBaseDir.getName().startsWith("restore_base_");
        try {
            Log.d(TAG, "Processing backup file: " + backupFile.getAbsolutePath());

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
                Log.e(TAG, "Failed to parse backup file: " + backupFile.getName());
                return false;
            }

            Log.d(TAG, "Parsed backup: " +
                    (backupData.collections != null ? backupData.collections.size() : 0) + " collections, " +
                    (backupData.series != null ? backupData.series.size() : 0) + " series, " +
                    (backupData.mediaFiles != null ? backupData.mediaFiles.size() : 0) + " media files");

            // Восстанавливаем коллекции
            if (backupData.collections != null) {
                for (Collection collectionData : backupData.collections) {
                    // Проверяем, не существует ли коллекция с таким же названием
                    Collection existingCollection = repository.getCollectionByNameSync(collectionData.getName());
                    if (existingCollection != null) {
                        // Коллекция с таким именем уже существует, обновляем мапу
                        collectionIdMap.put(collectionData.getId(), existingCollection.getId());
                        Log.d(TAG, "Collection already exists: " + collectionData.getName());
                    } else {
                        long oldId = collectionData.getId();

                        // Создаем новый объект Collection с правильными методами
                        Collection newCollection = new Collection();
                        newCollection.setName(collectionData.getName());
                        newCollection.setCreatedAt(collectionData.getCreatedAt());
                        newCollection.setFavorite(collectionData.isFavorite()); // Используем isFavorite()
                        newCollection.setColors(collectionData.getColors());

                        // Сбрасываем ID для новой вставки
                        newCollection.setId(0);
                        long newId = repository.insertCollectionSync(newCollection);
                        if (newId > 0) {
                            collectionIdMap.put(oldId, newId);
                            Log.d(TAG, "Restored collection: " + newCollection.getName() +
                                    " (old: " + oldId + ", new: " + newId + ")");
                        }
                    }
                }
            }

            // Восстанавливаем сериалы
            if (backupData.series != null) {
                for (Series series : backupData.series) {
                    // Проверяем, не существует ли серия с таким же названием
                    Series existingSeries = repository.getSeriesByTitleIgnoreCaseSync(series.getTitle());
                    if (existingSeries != null) {
                        // Серия с таким названием уже существует, обновляем мапу
                        seriesIdMap.put(series.getId(), existingSeries.getId());
                        Log.d(TAG, "Series already exists: " + series.getTitle());

                        // Восстанавливаем файл обложки, если путь является относительным (означает, что это файл из резервной копии)
                        if (series.getImageUri() != null && series.getImageUri().startsWith("files/")) {
                            String restoredPath = BackupFileManager.restoreFileFromBackup(
                                    context,
                                    series.getImageUri(),
                                    mediaBaseDir.getAbsolutePath(),
                                    "covers"
                            );
                            if (restoredPath != null) {
                                // Обновляем URI обложки для существующей серии
                                existingSeries.setImageUri(MediaStorageHelper.normalizeStoredValue(restoredPath));
                                repository.updateSeriesSync(existingSeries);
                            }
                        }
                    } else {
                        Series updatedSeries = new Series();
                        updatedSeries.setTitle(series.getTitle());
                        updatedSeries.setIsWatched(series.getIsWatched());
                        updatedSeries.setNotes(series.getNotes());
                        updatedSeries.setWatchUrl(series.getWatchUrl());
                        updatedSeries.setWatchAt(series.getWatchAt());
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
                                    mediaBaseDir.getAbsolutePath(),
                                    "covers"
                            );
                            if (restoredPath != null) {
                                updatedSeries.setImageUri(MediaStorageHelper.normalizeStoredValue(restoredPath));
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
            }

            // Восстанавливаем связи
            if (backupData.relations != null) {
                for (SeriesCollectionCrossRef relation : backupData.relations) {
                    Long newSeriesId = seriesIdMap.get(relation.getSeriesId());
                    Long newCollectionId = collectionIdMap.get(relation.getCollectionId());

                    if (newSeriesId != null && newCollectionId != null) {
                        // Проверяем, не существует ли связь
                        boolean relationExists = repository.checkRelationExistsSync(newSeriesId, newCollectionId);
                        if (!relationExists) {
                            SeriesCollectionCrossRef newRelation = new SeriesCollectionCrossRef(
                                    newSeriesId, newCollectionId);
                            newRelation.setIsWatched(relation.getIsWatched());
                            repository.insertCrossRefSync(newRelation);
                            Log.d(TAG, "Restored relation: series " + newSeriesId +
                                    " -> collection " + newCollectionId);
                        } else {
                            Log.d(TAG, "Relation already exists: series " + newSeriesId +
                                    " -> collection " + newCollectionId);
                        }
                    }
                }
            }

            // Восстанавливаем медиафайлы
            if (backupData.mediaFiles != null) {
                for (MediaFile mediaFile : backupData.mediaFiles) {
                    Long oldSeriesId = mediaFile.getSeriesId();
                    Long newSeriesId = seriesIdMap.get(oldSeriesId);

                    if (newSeriesId != null) {
                        // Проверяем, не существует ли медиафайл с таким же именем
                        if (repository.hasMediaFileWithNameSync(newSeriesId, mediaFile.getFileName())) {
                            Log.d(TAG, "Media file already exists: " + mediaFile.getFileName() +
                                    " for series ID: " + newSeriesId);
                        } else {
                            // Если путь к файлу является относительным (означает, что это файл из резервной копии)
                            if (mediaFile.getFileUri() != null && mediaFile.getFileUri().startsWith("files/")) {
                                String restoredPath = BackupFileManager.restoreFileFromBackup(
                                        context,
                                        mediaFile.getFileUri(),
                                        mediaBaseDir.getAbsolutePath()
                                );
                                if (restoredPath != null) {
                                    mediaFile.setFileUri(MediaStorageHelper.normalizeStoredValue(restoredPath));
                                    mediaFile.setFilePath(MediaStorageHelper.getInternalFilePath(context, restoredPath));
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
            }

            return true;

        } catch (Exception e) {
            Log.e(TAG, "Error processing backup file: " + backupFile.getName(), e);
            return false;
        } finally {
            if (cleanupTemp) {
                deleteDirectory(mediaBaseDir);
            }
        }
    }

    /**
     * Получает имя файла из URI
     */
    private String getFileNameFromUri(Context context, Uri uri) {
        if (uri == null) {
            return null;
        }

        String fileName = null;

        // Для файлов из внешнего хранилища может быть специальный столбец
        if ("content".equalsIgnoreCase(uri.getScheme())) {
            try {
                // Попробуем получить имя файла через ContentResolver
                try (android.database.Cursor cursor = context.getContentResolver().query(
                        uri,
                        new String[]{android.provider.OpenableColumns.DISPLAY_NAME},
                        null,
                        null,
                        null)) {
                    if (cursor != null && cursor.moveToFirst()) {
                        int columnIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                        if (columnIndex >= 0) {
                            fileName = cursor.getString(columnIndex);
                            Log.d(TAG, "Got file name from cursor: " + fileName);
                        }
                    }
                }

                // Если не получилось через cursor, попробуем из пути
                if (fileName == null || fileName.isEmpty()) {
                    java.util.List<String> pathSegments = uri.getPathSegments();
                    if (pathSegments != null && !pathSegments.isEmpty()) {
                        // Берем последний сегмент пути
                        String lastSegment = pathSegments.get(pathSegments.size() - 1);

                        // Пробуем найти имя файла в последнем сегменте
                        if (lastSegment.contains("/")) {
                            fileName = lastSegment.substring(lastSegment.lastIndexOf("/") + 1);
                        } else {
                            fileName = lastSegment;
                        }

                        Log.d(TAG, "Got file name from path segments: " + fileName);
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Could not get file name from URI via cursor: " + e.getMessage());
            }
        } else if ("file".equalsIgnoreCase(uri.getScheme())) {
            fileName = new File(uri.getPath()).getName();
            Log.d(TAG, "Got file name from file URI: " + fileName);
        }

        // Если все еще не получили имя файла, используем дефолтное
        if (fileName == null || fileName.isEmpty()) {
            fileName = "file_" + System.currentTimeMillis() + ".dat";
            Log.w(TAG, "Using default file name: " + fileName);
        }

        return fileName;
    }
    /**
     * Создает объединенную резервную копию из всех доступных резервных копий
     */
    public File createConsolidatedBackup() {
        try {
            Log.d(TAG, "Starting creation of consolidated backup");

            File[] backupFiles = getAvailableBackups();
            if (backupFiles == null || backupFiles.length == 0) {
                Log.e(TAG, "No backup files found to consolidate");
                return null;
            }

            // Сортируем файлы по времени модификации (от старых к новым)
            java.util.Arrays.sort(backupFiles, (f1, f2) -> Long.compare(f1.lastModified(), f2.lastModified()));

            // Создаем объект объединенных данных
            BackupData consolidatedData = new BackupData();
            consolidatedData.collections = new ArrayList<>();
            consolidatedData.series = new ArrayList<>();
            consolidatedData.relations = new ArrayList<>();
            consolidatedData.mediaFiles = new ArrayList<>();
            consolidatedData.watchSearchSites = WatchSearchSitesStore.getSitesText(context);
            consolidatedData.timestamp = System.currentTimeMillis();
            consolidatedData.version = 2;

            // Мапы для соответствия старых и новых ID
            Map<Long, Long> collectionIdMap = new HashMap<>();
            Map<Long, Long> seriesIdMap = new HashMap<>();

            // Временная директория для объединенных файлов
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                    .format(new Date());
            File tempBackupDir = new File(context.getCacheDir(), "consolidated_backup_" + timeStamp);
            if (!tempBackupDir.exists() && !tempBackupDir.mkdirs()) {
                Log.e(TAG, "Failed to create temporary consolidated backup directory");
                return null;
            }

            File tempFilesDir = new File(tempBackupDir, "files");
            if (!tempFilesDir.exists() && !tempFilesDir.mkdirs()) {
                Log.e(TAG, "Failed to create temporary files directory");
                return null;
            }

            // Обрабатываем каждую резервную копию
            for (File backupFile : backupFiles) {
                Log.d(TAG, "Processing backup file for consolidation: " + backupFile.getName());

                if (isZipBackupFile(backupFile)) {
                    // Временно извлекаем ZIP для обработки
                    File extractDir = new File(context.getCacheDir(), "temp_extract_" + System.currentTimeMillis());

                    boolean extracted = BackupFileManager.extractZipBackup(backupFile.getAbsolutePath(), extractDir.getAbsolutePath());
                    if (!extracted) {
                        Log.e(TAG, "Failed to extract ZIP backup: " + backupFile.getName());
                        continue;
                    }

                    // Находим JSON файл в извлеченной папке
                    File[] jsonFiles = extractDir.listFiles((dir, name) -> name.endsWith(".json"));
                    if (jsonFiles != null && jsonFiles.length > 0) {
                        processBackupFileForConsolidation(jsonFiles[0], consolidatedData, collectionIdMap, seriesIdMap, tempFilesDir);
                    }

                    // Удаляем временную папку
                    deleteDirectory(extractDir);
                } else {
                    processBackupFileForConsolidation(backupFile, consolidatedData, collectionIdMap, seriesIdMap, tempFilesDir);
                }
            }

            // Создаем директорию для сохранения объединенной резервной копии
            File backupDir = getBackupDirectory();
            if (!backupDir.exists() && !backupDir.mkdirs()) {
                Log.e(TAG, "Failed to create backup directory");
                return null;
            }

            // Сохраняем объединенные данные в JSON
            String fileName = "consolidated_backup_" + timeStamp + ".json";
            File consolidatedJsonFile = new File(backupDir, fileName);

            try (FileOutputStream fos = new FileOutputStream(consolidatedJsonFile);
                 OutputStreamWriter writer = new OutputStreamWriter(fos)) {
                writer.write(gson.toJson(consolidatedData));
                writer.flush();
            }

            // Создаем ZIP архив, содержащий JSON и файлы
            String zipFileName = "consolidated_backup_" + timeStamp + ".zip";
            File zipFile = new File(backupDir, zipFileName);

            // Создаем временную директорию для архивации
            File combinedBackupDir = new File(context.getCacheDir(), "combined_consolidated_" + timeStamp);
            if (combinedBackupDir.exists()) {
                deleteDirectory(combinedBackupDir);
            }
            if (combinedBackupDir.mkdirs()) {
                // Копируем JSON файл в комбинированную директорию
                File jsonInCombined = new File(combinedBackupDir, consolidatedJsonFile.getName());
                try (java.io.FileInputStream fis = new java.io.FileInputStream(consolidatedJsonFile);
                     java.io.FileOutputStream fos = new java.io.FileOutputStream(jsonInCombined)) {
                    byte[] buffer = new byte[4096];
                    int length;
                    while ((length = fis.read(buffer)) > 0) {
                        fos.write(buffer, 0, length);
                    }
                }

                // Копируем директорию файлов, если она существует
                if (tempFilesDir.exists()) {
                    copyDirectory(tempFilesDir, new File(combinedBackupDir, "files"));
                }

                // Создаем ZIP архив
                File createdZip = BackupFileManager.createZipBackup(combinedBackupDir.getAbsolutePath(), zipFile.getAbsolutePath());
                if (createdZip != null) {
                    Log.d(TAG, "Consolidated ZIP backup created successfully: " + zipFile.getAbsolutePath());
                } else {
                    Log.w(TAG, "Failed to create consolidated ZIP backup");
                    return null;
                }

                // Очищаем временную директорию
                deleteDirectory(combinedBackupDir);
            }

            // Удаляем временные файлы
            deleteDirectory(tempBackupDir);

            Log.i(TAG, "Consolidated backup created successfully: " + zipFile.getAbsolutePath());
            return zipFile;

        } catch (Exception e) {
            Log.e(TAG, "Error creating consolidated backup", e);
            return null;
        }
    }

    /**
     * Обработка одного файла резервной копии для объединения
     */
    private void processBackupFileForConsolidation(File backupFile, BackupData consolidatedData,
                                                   Map<Long, Long> collectionIdMap, Map<Long, Long> seriesIdMap,
                                                   File targetFilesDir) {
        try {
            Log.d(TAG, "Processing backup file for consolidation: " + backupFile.getAbsolutePath());

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
                Log.e(TAG, "Failed to parse backup file: " + backupFile.getName());
                return;
            }

            Log.d(TAG, "Parsed backup: " +
                    (backupData.collections != null ? backupData.collections.size() : 0) + " collections, " +
                    (backupData.series != null ? backupData.series.size() : 0) + " series, " +
                    (backupData.mediaFiles != null ? backupData.mediaFiles.size() : 0) + " media files");

            if (backupData.watchSearchSites != null) {
                consolidatedData.watchSearchSites = backupData.watchSearchSites;
            }

            // Обрабатываем коллекции
            if (backupData.collections != null) {
                for (Collection collection : backupData.collections) {
                    // Проверяем, не существует ли коллекция с таким же названием
                    boolean exists = false;
                    for (Collection existingCollection : consolidatedData.collections) {
                        if (existingCollection.getName().equals(collection.getName())) {
                            exists = true;
                            break;
                        }
                    }

                    if (!exists) {
                        // Добавляем коллекцию и обновляем мапу ID
                        Collection newCollection = new Collection();
                        newCollection.setName(collection.getName());
                        newCollection.setCreatedAt(collection.getCreatedAt());
                        newCollection.setFavorite(collection.isFavorite());
                        newCollection.setId(0); // Сбрасываем ID

                        long oldId = collection.getId();
                        consolidatedData.collections.add(newCollection);
                        collectionIdMap.put(oldId, (long) consolidatedData.collections.size()); // Используем индекс как временный ID
                    }
                }
            }

            // Обрабатываем сериалы
            if (backupData.series != null) {
                for (Series series : backupData.series) {
                    // Проверяем, не существует ли серия с таким же названием
                    boolean exists = false;
                    for (Series existingSeries : consolidatedData.series) {
                        if (existingSeries.getTitle().equals(series.getTitle())) {
                            exists = true;
                            // Обновляем обложку, если она отличается и новая не пустая
                            if (series.getImageUri() != null && !series.getImageUri().isEmpty()
                                    && (existingSeries.getImageUri() == null || existingSeries.getImageUri().isEmpty())) {
                                existingSeries.setImageUri(series.getImageUri());
                            }
                            break;
                        }
                    }

                    if (!exists) {
                        Series newSeries = new Series();
                        newSeries.setTitle(series.getTitle());
                        newSeries.setIsWatched(series.getIsWatched());
                        newSeries.setNotes(series.getNotes());
                        newSeries.setWatchUrl(series.getWatchUrl());
                        newSeries.setWatchAt(series.getWatchAt());
                        newSeries.setCreatedAt(series.getCreatedAt());
                        newSeries.setStatus(series.getStatus());
                        newSeries.setIsFavorite(series.getIsFavorite());
                        newSeries.setRating(series.getRating());
                        newSeries.setGenre(series.getGenre());
                        newSeries.setSeasons(series.getSeasons());
                        newSeries.setEpisodes(series.getEpisodes());

                        // Обрабатываем файл обложки, если путь является относительным
                        if (series.getImageUri() != null && series.getImageUri().startsWith("files/")) {
                            // Копируем файл в целевую директорию
                            File sourceFile = new File(backupFile.getParent(), series.getImageUri());
                            if (sourceFile.exists()) {
                                // Копируем файл в целевую директорию
                                File destFile = new File(targetFilesDir, sourceFile.getName());

                                // Проверяем, существует ли файл с таким же именем
                                if (destFile.exists()) {
                                    // Проверяем, совпадает ли содержимое файлов
                                    if (areFilesContentEqual(sourceFile, destFile)) {
                                        // Файл с тем же содержимым уже существует, используем существующий путь
                                        newSeries.setImageUri("files/" + destFile.getName());
                                    } else {
                                        // Создаем уникальное имя
                                        String nameWithoutExtension = "";
                                        String extension = "";
                                        int dotIndex = destFile.getName().lastIndexOf('.');
                                        if (dotIndex > 0) {
                                            nameWithoutExtension = destFile.getName().substring(0, dotIndex);
                                            extension = destFile.getName().substring(dotIndex);
                                        } else {
                                            nameWithoutExtension = destFile.getName();
                                            extension = "";
                                        }

                                        String uniqueFileName = nameWithoutExtension + "_" + java.util.UUID.randomUUID().toString() + extension;
                                        File uniqueDestFile = new File(targetFilesDir, uniqueFileName);

                                        java.nio.file.Files.copy(sourceFile.toPath(), uniqueDestFile.toPath(),
                                                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                                        newSeries.setImageUri("files/" + uniqueDestFile.getName());
                                    }
                                } else {
                                    // Копируем файл
                                    java.nio.file.Files.copy(sourceFile.toPath(), destFile.toPath(),
                                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                                    newSeries.setImageUri("files/" + destFile.getName());
                                }
                            } else {
                                // Если файл не существует, оставляем оригинальный путь
                                newSeries.setImageUri(series.getImageUri());
                            }
                        } else {
                            // Это обычный URI, оставляем без изменений
                            newSeries.setImageUri(series.getImageUri());
                        }

                        newSeries.setId(0); // Сбрасываем ID

                        long oldId = series.getId();
                        consolidatedData.series.add(newSeries);
                        seriesIdMap.put(oldId, (long) consolidatedData.series.size()); // Используем индекс как временный ID
                    }
                }
            }

            // Обрабатываем связи
            if (backupData.relations != null) {
                for (SeriesCollectionCrossRef relation : backupData.relations) {
                    Long mappedSeriesId = seriesIdMap.get(relation.getSeriesId());
                    Long mappedCollectionId = collectionIdMap.get(relation.getCollectionId());

                    if (mappedSeriesId != null && mappedCollectionId != null) {
                        // Проверяем, не существует ли связь
                        boolean relationExists = false;
                        for (SeriesCollectionCrossRef existingRelation : consolidatedData.relations) {
                            if (existingRelation.getSeriesId() == mappedSeriesId &&
                                    existingRelation.getCollectionId() == mappedCollectionId) {
                                relationExists = true;
                                break;
                            }
                        }

                        if (!relationExists) {
                            SeriesCollectionCrossRef newRelation = new SeriesCollectionCrossRef(
                                    mappedSeriesId, mappedCollectionId);
                            newRelation.setIsWatched(relation.getIsWatched());
                            consolidatedData.relations.add(newRelation);
                        }
                    }
                }
            }

            // Обрабатываем медиафайлы
            if (backupData.mediaFiles != null) {
                for (MediaFile mediaFile : backupData.mediaFiles) {
                    Long oldSeriesId = mediaFile.getSeriesId();
                    Long mappedSeriesId = seriesIdMap.get(oldSeriesId);

                    if (mappedSeriesId != null) {
                        // Проверяем, не существует ли медиафайл с таким же URI и серией
                        boolean exists = false;
                        for (MediaFile existingMediaFile : consolidatedData.mediaFiles) {
                            if (existingMediaFile.getFileUri() != null && mediaFile.getFileUri() != null &&
                                    existingMediaFile.getFileUri().equals(mediaFile.getFileUri()) &&
                                    existingMediaFile.getSeriesId() == mappedSeriesId) {
                                exists = true;
                                break;
                            }
                        }

                        if (!exists) {
                            MediaFile newMediaFile = new MediaFile(
                                    mappedSeriesId,
                                    mediaFile.getFileUri(),
                                    mediaFile.getFileType(),
                                    mediaFile.getFileName()
                            );
                            newMediaFile.setFilePath(mediaFile.getFilePath());
                            newMediaFile.setFileSize(mediaFile.getFileSize());
                            newMediaFile.setCreatedAt(mediaFile.getCreatedAt());
                            newMediaFile.setDescription(mediaFile.getDescription());
                            newMediaFile.setId(0); // Сбрасываем ID

                            // Обрабатываем файл, если путь является относительным
                            if (mediaFile.getFileUri() != null && mediaFile.getFileUri().startsWith("files/")) {
                                File sourceFile = new File(backupFile.getParent(), mediaFile.getFileUri());
                                if (sourceFile.exists()) {
                                    // Копируем файл в целевую директорию
                                    File destFile = new File(targetFilesDir, sourceFile.getName());

                                    // Проверяем, существует ли файл с таким же именем
                                    if (destFile.exists()) {
                                        // Проверяем, совпадает ли содержимое файлов
                                        if (areFilesContentEqual(sourceFile, destFile)) {
                                            // Файл с тем же содержимым уже существует, используем существующий путь
                                            newMediaFile.setFileUri("files/" + destFile.getName());
                                        } else {
                                            // Создаем уникальное имя
                                            String nameWithoutExtension = "";
                                            String extension = "";
                                            int dotIndex = destFile.getName().lastIndexOf('.');
                                            if (dotIndex > 0) {
                                                nameWithoutExtension = destFile.getName().substring(0, dotIndex);
                                                extension = destFile.getName().substring(dotIndex);
                                            } else {
                                                nameWithoutExtension = destFile.getName();
                                                extension = "";
                                            }

                                            String uniqueFileName = nameWithoutExtension + "_" + java.util.UUID.randomUUID().toString() + extension;
                                            File uniqueDestFile = new File(targetFilesDir, uniqueFileName);

                                            java.nio.file.Files.copy(sourceFile.toPath(), uniqueDestFile.toPath(),
                                                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                                            newMediaFile.setFileUri("files/" + uniqueDestFile.getName());
                                        }
                                    } else {
                                        // Копируем файл
                                        java.nio.file.Files.copy(sourceFile.toPath(), destFile.toPath(),
                                                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                                        newMediaFile.setFileUri("files/" + destFile.getName());
                                    }
                                } else {
                                    // Если файл не существует, оставляем оригинальный путь
                                    newMediaFile.setFileUri(mediaFile.getFileUri());
                                }
                            }

                            consolidatedData.mediaFiles.add(newMediaFile);
                        }
                    }
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "Error processing backup file for consolidation: " + backupFile.getName(), e);
        }
    }

    /**
     * Восстановление из ZIP файла
     */
    public boolean restoreFromZipFile(File zipFile) {
        try {
            Log.d(TAG, "Starting restore from ZIP file: " + zipFile.getAbsolutePath());

            // Создаем временную директорию для извлечения ZIP
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                    .format(new Date());
            File extractDir = new File(context.getCacheDir(), "extracted_backup_" + timeStamp);
            if (!extractDir.exists() && !extractDir.mkdirs()) {
                Log.e(TAG, "Failed to create extraction directory");
                return false;
            }

            // Извлекаем ZIP файл
            boolean extracted = BackupFileManager.extractZipBackup(zipFile.getAbsolutePath(), extractDir.getAbsolutePath());
            if (!extracted) {
                Log.e(TAG, "Failed to extract ZIP backup");
                return false;
            }

            // Находим JSON файл в извлеченной директории
            File[] jsonFiles = extractDir.listFiles((dir, name) -> name.endsWith(".json"));
            if (jsonFiles == null || jsonFiles.length == 0) {
                Log.e(TAG, "No JSON backup file found in ZIP archive");
                return false;
            }

            File jsonFile = jsonFiles[0];

            // Теперь используем метод восстановления из JSON файла с указанием правильной директории
            boolean success = restoreFromJsonFile(jsonFile, extractDir);

            // Удаляем временную директорию
            deleteDirectory(extractDir);

            return success;

        } catch (Exception e) {
            Log.e(TAG, "Error restoring from ZIP file", e);
            return false;
        }
    }

    /**
     * Восстановление из JSON файла с указанием базовой директории для файлов
     */
    private boolean restoreFromJsonFile(File jsonFile, File baseDir) {
        try {
            Log.d(TAG, "Starting restore from JSON file with base dir: " + baseDir.getAbsolutePath());

            // Читаем файл
            java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.FileReader(jsonFile));
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
                    updatedSeries.setWatchUrl(series.getWatchUrl());
                    updatedSeries.setWatchAt(series.getWatchAt());
                    updatedSeries.setCreatedAt(series.getCreatedAt());
                    updatedSeries.setStatus(series.getStatus());
                    updatedSeries.setIsFavorite(series.getIsFavorite());
                    updatedSeries.setRating(series.getRating());
                    updatedSeries.setGenre(series.getGenre());
                    updatedSeries.setSeasons(series.getSeasons());
                    updatedSeries.setEpisodes(series.getEpisodes());

                    // Восстанавливаем файл обложки, если путь является относительным
                    if (series.getImageUri() != null && series.getImageUri().startsWith("files/")) {
                        String restoredPath = restoreFileFromBackup(series.getImageUri(), baseDir, "covers");
                        if (restoredPath != null) {
                            updatedSeries.setImageUri(MediaStorageHelper.normalizeStoredValue(restoredPath));
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
                        // Если путь к файлу является относительным
                        if (mediaFile.getFileUri() != null && mediaFile.getFileUri().startsWith("files/")) {
                            String restoredPath = restoreFileFromBackup(mediaFile.getFileUri(), baseDir);
                            if (restoredPath != null) {
                                mediaFile.setFileUri(MediaStorageHelper.normalizeStoredValue(restoredPath));
                                mediaFile.setFilePath(MediaStorageHelper.getInternalFilePath(context, restoredPath));
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

            restoreWatchSearchSitesFromBackup(backupData);

            Log.i(TAG, "Restore completed successfully");
            return true;

        } catch (Exception e) {
            Log.e(TAG, "Error restoring from backup", e);
            return false;
        }
    }

    /**
     * Восстанавливает файл из распакованного ZIP архива
     */
    private String restoreFileFromBackup(String relativeFilePath, File extractDir) {
        return restoreFileFromBackup(relativeFilePath, extractDir, "media");
    }

    private String restoreFileFromBackup(String relativeFilePath, File extractDir, String targetSubdir) {
        try {
            File sourceFile = null;

            File directPath = new File(extractDir, relativeFilePath);
            if (directPath.exists()) {
                sourceFile = directPath;
            }

            String fileName = extractFileName(relativeFilePath);

            if (sourceFile == null) {
                File filesDir = new File(extractDir, "files");
                if (filesDir.exists()) {
                    sourceFile = new File(filesDir, fileName);
                }
            }

            if (sourceFile == null || !sourceFile.exists()) {
                sourceFile = findFileRecursively(extractDir, fileName);
            }

            if (sourceFile == null || !sourceFile.exists()) {
                Log.e(TAG, "Source file does not exist in extracted backup: " + fileName
                        + " (relative: " + relativeFilePath + ")");
                return null;
            }

            // Создаем подкаталог для медиафайлов во внутреннем хранилище приложения
            File mediaDir = new File(context.getFilesDir(), targetSubdir);
            if (!mediaDir.exists()) {
                if (!mediaDir.mkdirs()) {
                    Log.e(TAG, "Failed to create media directory: " + mediaDir.getAbsolutePath());
                    return null;
                }
            }

            // Генерируем уникальное имя файла
            String uniqueFileName = UUID.randomUUID().toString() + "_" + fileName;
            File destinationFile = new File(mediaDir, uniqueFileName);

            BackupMediaOptimizer.fastCopy(sourceFile, destinationFile);

            Log.d(TAG, "Successfully restored file from backup: " + destinationFile.getAbsolutePath());
            return MediaStorageHelper.toStoredUri(destinationFile);
        } catch (IOException e) {
            Log.e(TAG, "Error restoring file from backup: " + e.getMessage(), e);
            return null;
        }
    }

    /**
     * Рекурсивно ищет файл в директории
     */
    private File findFileRecursively(File directory, String fileName) {
        if (directory == null || !directory.exists()) {
            return null;
        }

        File[] files = directory.listFiles();
        if (files == null) {
            return null;
        }

        for (File file : files) {
            if (file.isDirectory()) {
                File found = findFileRecursively(file, fileName);
                if (found != null) {
                    return found;
                }
            } else if (file.getName().equals(fileName) || file.getName().endsWith(fileName)) {
                return file;
            }
        }
        return null;
    }

    /**
     * Извлекает имя файла из пути в формате "files/filename.ext"
     */
    private String extractFileName(String relativeFilePath) {
        if (relativeFilePath.startsWith("files/")) {
            return relativeFilePath.substring(6); // Убираем "files/"
        }
        return relativeFilePath;
    }
}