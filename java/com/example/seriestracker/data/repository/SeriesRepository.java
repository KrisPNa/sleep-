package com.example.seriestracker.data.repository;

import android.app.Application;
import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.lifecycle.LiveData;

import com.example.seriestracker.data.SeriesDatabase;
import com.example.seriestracker.data.dao.SeriesDao;
import com.example.seriestracker.data.entities.Collection;
import com.example.seriestracker.data.entities.CollectionWithSeries;
import com.example.seriestracker.data.entities.MediaFile;
import com.example.seriestracker.data.entities.Series;
import com.example.seriestracker.data.entities.SeriesCollectionCrossRef;
import com.example.seriestracker.data.sync.SyncEngine;
import com.example.seriestracker.utils.WatchLinkTextHelper;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class SeriesRepository {
    public enum AppendNotesResult {
        ADDED,
        ALREADY_PRESENT,
        NOTHING_TO_APPEND,
        SERIES_NOT_FOUND
    }

    public static class AppendNotesOutcome {
        public final AppendNotesResult result;
        public final long seriesId;

        public AppendNotesOutcome(AppendNotesResult result, long seriesId) {
            this.result = result;
            this.seriesId = seriesId;
        }
    }

    public enum SetWatchUrlResult {
        UPDATED,
        ALREADY_PRESENT,
        EMPTY_URL,
        SERIES_NOT_FOUND
    }

    public static class SetWatchUrlOutcome {
        public final SetWatchUrlResult result;
        public final long seriesId;

        public SetWatchUrlOutcome(SetWatchUrlResult result, long seriesId) {
            this.result = result;
            this.seriesId = seriesId;
        }
    }

    public interface SetWatchUrlCallback {
        void onResult(SetWatchUrlOutcome outcome);
    }

    public static class UpdateExistingSeriesOutcome {
        public final long seriesId;
        public final boolean seriesFound;

        public UpdateExistingSeriesOutcome(long seriesId, boolean seriesFound) {
            this.seriesId = seriesId;
            this.seriesFound = seriesFound;
        }
    }

    public interface UpdateExistingSeriesCallback {
        void onResult(UpdateExistingSeriesOutcome outcome);
    }

    public interface AppendNotesCallback {
        void onResult(AppendNotesOutcome outcome);
    }

    public interface InsertSeriesCallback {
        void onSeriesInserted(long seriesId);
    }

    public interface InsertCollectionCallback {
        void onCollectionInserted(long collectionId);
    }

    private SeriesDao seriesDao;
    private Application application;
    private ExecutorService executor = Executors.newSingleThreadExecutor();
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    // Статическое поле для синглтона
    private static SeriesRepository instance;

    // Приватный конструктор
    public SeriesRepository(Application application) {
        this.application = application;
        SeriesDatabase database = SeriesDatabase.getDatabase(application);
        seriesDao = database.seriesDao();
        instance = this;
    }

    // Статический метод для получения экземпляра с Application
    public static synchronized SeriesRepository getInstance(Application application) {
        if (instance == null) {
            instance = new SeriesRepository(application);
        }
        return instance;
    }

    // Дополнительный метод для получения существующего экземпляра
    public static synchronized SeriesRepository getInstance() {
        if (instance == null) {
            throw new IllegalStateException("Repository не инициализирован. Сначала вызовите getInstance(Application)");
        }
        return instance;
    }

    private void prepareSeries(Series series) {
        if (series.getCloudId() == null || series.getCloudId().isEmpty()) {
            series.setCloudId(UUID.randomUUID().toString());
        }
        series.markDirty();
    }

    private void prepareCollection(Collection collection) {
        if (collection.getCloudId() == null || collection.getCloudId().isEmpty()) {
            collection.setCloudId(UUID.randomUUID().toString());
        }
        collection.markDirty();
    }

    private void prepareMedia(MediaFile mediaFile) {
        if (mediaFile.getCloudId() == null || mediaFile.getCloudId().isEmpty()) {
            mediaFile.setCloudId(UUID.randomUUID().toString());
        }
        mediaFile.markDirty();
    }

    private void triggerSync() {
        if (application == null) return;
        SyncEngine.getInstance(application).requestSync();
    }

    // === Коллекции ===
    public LiveData<List<Collection>> getAllCollections() {
        return seriesDao.getAllCollections();
    }


    public LiveData<List<Collection>> getAllCollectionsWithSeriesCount() {
        return seriesDao.getAllCollectionsWithSeriesCount();
    }

    public LiveData<Collection> getCollectionById(long collectionId) {
        return seriesDao.getCollectionById(collectionId);
    }

    public void insertCollection(Collection collection) {
        insertCollection(collection, null);
    }

    public void insertCollection(Collection collection, InsertCollectionCallback callback) {
        executor.execute(() -> {
            prepareCollection(collection);
            long collectionId = seriesDao.insertCollectionSync(collection);
            triggerSync();
            if (callback != null) {
                mainHandler.post(() -> callback.onCollectionInserted(collectionId));
            }
        });
    }

    public void deleteCollection(long collectionId) {
        executor.execute(() -> {
            Collection c = null;
            for (Collection col : seriesDao.getAllCollectionsSync()) {
                if (col.getId() == collectionId) {
                    c = col;
                    break;
                }
            }
            seriesDao.deleteCollection(collectionId);
            if (c != null && c.getCloudId() != null) {
                SyncEngine.getInstance(application).deleteCollectionRemote(c.getCloudId());
            }
        });
    }

    public void deleteCollection(Collection collection) {
        executor.execute(() -> {
            deleteAllSeriesCollectionRelationsForCollection(collection.getId());
            seriesDao.deleteCollection(collection.getId());
            if (collection.getCloudId() != null) {
                SyncEngine.getInstance(application).deleteCollectionRemote(collection.getCloudId());
            }
        });
    }

    public void deleteAllSeriesCollectionRelationsForCollection(long collectionId) {
        executor.execute(() -> {
            // Удаляем все связи сериалов с этой коллекцией
            seriesDao.deleteAllSeriesCollectionRelationsForCollection(collectionId);
            triggerSync();
        });
    }

    // === Сериалы ===
    public LiveData<List<Series>> getAllSeries() {
        return seriesDao.getAllSeries();
    }

    public LiveData<Series> getSeriesById(long seriesId) {
        return seriesDao.getSeriesById(seriesId);
    }

    public void insertSeries(Series series) {
        executor.execute(() -> {
            prepareSeries(series);
            seriesDao.insertSeries(series);
            triggerSync();
        });
    }

    public void updateSeries(Series series) {
        executor.execute(() -> {
            prepareSeries(series);
            seriesDao.updateSeries(series);
            triggerSync();
        });
    }

    public void deleteSeries(long seriesId) {
        executor.execute(() -> {
            Series s = seriesDao.getSeriesByIdSync(seriesId);
            // Явно чистим связи/медиа до удаления (на случай FK без cascade в старых БД)
            try {
                seriesDao.deleteAllMediaFilesForSeries(seriesId);
            } catch (Exception ignored) {
            }
            try {
                seriesDao.deleteAllSeriesCollectionRelationsForSeries(seriesId);
            } catch (Exception ignored) {
            }
            seriesDao.deleteSeries(seriesId);
            if (s != null && s.getCloudId() != null && !s.getCloudId().isEmpty()) {
                // Удаление из Supabase (+ повтор при следующем sync). Без полного pull сразу после.
                SyncEngine.getInstance(application).deleteSeriesRemote(s.getCloudId());
            }
        });
    }

    public void insertSeriesWithCollections(Series series, List<Long> collectionIds) {
        insertSeriesWithCollections(series, collectionIds, null);
    }

    public void insertSeriesWithCollections(Series series, List<Long> collectionIds,
                                            InsertSeriesCallback callback) {
        executor.execute(() -> {
            prepareSeries(series);
            long seriesId = seriesDao.insertSeries(series);
            if (collectionIds != null) {
                for (Long collectionId : collectionIds) {
                    SeriesCollectionCrossRef crossRef = new SeriesCollectionCrossRef(seriesId, collectionId);
                    crossRef.markDirty();
                    seriesDao.insertCrossRef(crossRef);
                }
            }
            triggerSync();
            if (callback != null) {
                mainHandler.post(() -> callback.onSeriesInserted(seriesId));
            }
        });
    }

    // === Статусы ===
    public void updateSeriesWatchedStatus(long seriesId, boolean isWatched) {
        executor.execute(() -> {
            seriesDao.updateSeriesWatchedStatus(seriesId, isWatched);
            seriesDao.updateCrossRefWatchedStatus(seriesId, isWatched);
            Series s = seriesDao.getSeriesByIdSync(seriesId);
            if (s != null) {
                prepareSeries(s);
                seriesDao.updateSeries(s);
            }
            triggerSync();
        });
    }

    public void updateSeriesFavoriteStatus(long seriesId, boolean isFavorite) {
        executor.execute(() -> {
            seriesDao.updateSeriesFavoriteStatus(seriesId, isFavorite);
            Series s = seriesDao.getSeriesByIdSync(seriesId);
            if (s != null) {
                prepareSeries(s);
                seriesDao.updateSeries(s);
            }
            triggerSync();
        });
    }

    public void updateSeriesStatus(long seriesId, String status) {
        executor.execute(() -> {
            seriesDao.updateSeriesStatus(seriesId, status);
            Series s = seriesDao.getSeriesByIdSync(seriesId);
            if (s != null) {
                prepareSeries(s);
                seriesDao.updateSeries(s);
            }
            triggerSync();
        });
    }

    // === Получение данных ===
    public LiveData<List<Series>> getSeriesInCollection(long collectionId) {
        return seriesDao.getSeriesInCollection(collectionId);
    }

    public LiveData<List<CollectionWithSeries>> getCollectionsWithSeries() {
        return seriesDao.getCollectionsWithSeries();
    }

    public void addSeriesToCollection(long seriesId, long collectionId) {
        executor.execute(() -> {
            // Проверяем, есть ли уже связь
            int count = seriesDao.isSeriesInCollection(seriesId, collectionId);
            if (count == 0) {
                SeriesCollectionCrossRef crossRef = new SeriesCollectionCrossRef(seriesId, collectionId);
                crossRef.markDirty();
                seriesDao.insertCrossRef(crossRef);
                triggerSync();
            }
        });
    }

    public void removeSeriesFromCollection(long seriesId, long collectionId) {
        executor.execute(() -> {
            Series s = seriesDao.getSeriesByIdSync(seriesId);
            Collection c = null;
            for (Collection col : seriesDao.getAllCollectionsSync()) {
                if (col.getId() == collectionId) {
                    c = col;
                    break;
                }
            }
            seriesDao.removeSeriesFromCollection(seriesId, collectionId);
            if (s != null && c != null && s.getCloudId() != null && c.getCloudId() != null) {
                SyncEngine.getInstance(application)
                        .deleteCrossRefRemote(s.getCloudId(), c.getCloudId());
            }
            triggerSync();
        });
    }

    public LiveData<List<Collection>> getCollectionsForSeries(long seriesId) {
        return seriesDao.getCollectionsForSeries(seriesId);
    }

    public LiveData<Integer> getSeriesCountInCollection(long collectionId) {
        return seriesDao.getSeriesCountInCollection(collectionId);
    }

    // === Методы для EditSeriesScreen ===
    public void insertSeriesCollectionCrossRef(SeriesCollectionCrossRef crossRef) {
        executor.execute(() -> seriesDao.insertCrossRef(crossRef));
    }

    public void deleteSeriesCollectionCrossRef(long seriesId, long collectionId) {
        executor.execute(() -> seriesDao.deleteSeriesCollectionCrossRef(seriesId, collectionId));
    }

    // Метод для получения связи (если нужен)
    public SeriesCollectionCrossRef getCrossRef(long seriesId, long collectionId) {
        // Внимание: этот метод не может быть вызван из основного потока!
        // Используйте его в executor.execute()
        return seriesDao.getCrossRef(seriesId, collectionId);
    }

    // === Методы проверки существования ===
    public LiveData<Boolean> doesCollectionExist(String collectionName) {
        return seriesDao.doesCollectionExist(collectionName);
    }

    public LiveData<Boolean> doesSeriesExist(String seriesTitle) {
        return seriesDao.doesSeriesExist(seriesTitle);
    }

    public LiveData<Boolean> doesSeriesExistExcludeId(String seriesTitle, long seriesId) {
        return seriesDao.doesSeriesExistExcludeId(seriesTitle, seriesId);
    }

    public void appendNotesToSeriesByTitle(String title, String notesToAppend, AppendNotesCallback callback) {
        executor.execute(() -> {
            AppendNotesOutcome outcome = appendNotesToSeriesByTitleInternal(title, notesToAppend);
            mainHandler.post(() -> callback.onResult(outcome));
        });
    }

    public void setWatchUrlOnSeriesByTitle(String title, String watchUrl, SetWatchUrlCallback callback) {
        executor.execute(() -> {
            SetWatchUrlOutcome outcome = setWatchUrlOnSeriesByTitleInternal(title, watchUrl);
            mainHandler.post(() -> callback.onResult(outcome));
        });
    }

    public void updateExistingSeriesByTitle(String title, String watchUrl, String notesToAppend,
                                            UpdateExistingSeriesCallback callback) {
        updateExistingSeriesByTitle(title, null, watchUrl, notesToAppend, callback);
    }

    public void updateExistingSeriesByTitle(String title, String watchAt, String watchUrl,
                                            String notesToAppend,
                                            UpdateExistingSeriesCallback callback) {
        executor.execute(() -> {
            UpdateExistingSeriesOutcome outcome =
                    updateExistingSeriesByTitleInternal(title, watchAt, watchUrl, notesToAppend);
            mainHandler.post(() -> callback.onResult(outcome));
        });
    }

    private UpdateExistingSeriesOutcome updateExistingSeriesByTitleInternal(String title,
                                                                            String watchAt,
                                                                            String watchUrl,
                                                                            String notesToAppend) {
        Series series = seriesDao.getSeriesByTitleIgnoreCase(title);
        if (series == null) {
            return new UpdateExistingSeriesOutcome(-1, false);
        }

        boolean changed = false;
        String trimmedWatchAt = watchAt == null ? "" : watchAt.trim();
        if (!trimmedWatchAt.isEmpty()) {
            String existingWatchAt = series.getWatchAt();
            String mergedWatchAt = WatchLinkTextHelper.mergeUrls(
                    existingWatchAt, WatchLinkTextHelper.splitUrls(trimmedWatchAt));
            if (!mergedWatchAt.equals(existingWatchAt != null ? existingWatchAt.trim() : "")) {
                series.setWatchAt(mergedWatchAt);
                changed = true;
            }
        }

        String trimmedUrl = watchUrl == null ? "" : watchUrl.trim();
        if (!trimmedUrl.isEmpty()) {
            String existingUrl = series.getWatchUrl();
            String mergedUrl = WatchLinkTextHelper.mergeUrls(
                    existingUrl, WatchLinkTextHelper.splitUrls(trimmedUrl));
            if (!mergedUrl.equals(existingUrl != null ? existingUrl.trim() : "")) {
                series.setWatchUrl(mergedUrl);
                changed = true;
            }
        }

        String trimmedNotes = notesToAppend == null ? "" : notesToAppend.trim();
        if (!trimmedNotes.isEmpty()) {
            String existingNotes = series.getNotes();
            if (existingNotes == null) {
                existingNotes = "";
            }
            if (!existingNotes.contains(trimmedNotes)) {
                String newNotes = existingNotes.isEmpty()
                        ? trimmedNotes
                        : existingNotes + "\n" + trimmedNotes;
                series.setNotes(newNotes);
                changed = true;
            }
        }

        if (changed) {
            seriesDao.updateSeries(series);
        }
        return new UpdateExistingSeriesOutcome(series.getId(), true);
    }

    private SetWatchUrlOutcome setWatchUrlOnSeriesByTitleInternal(String title, String watchUrl) {
        String trimmedUrl = watchUrl == null ? "" : watchUrl.trim();
        if (trimmedUrl.isEmpty()) {
            return new SetWatchUrlOutcome(SetWatchUrlResult.EMPTY_URL, -1);
        }

        Series series = seriesDao.getSeriesByTitleIgnoreCase(title);
        if (series == null) {
            return new SetWatchUrlOutcome(SetWatchUrlResult.SERIES_NOT_FOUND, -1);
        }

        long seriesId = series.getId();
        String existingUrl = series.getWatchUrl();
        String mergedUrl = WatchLinkTextHelper.mergeUrls(
                existingUrl, WatchLinkTextHelper.splitUrls(trimmedUrl));
        String normalizedExisting = existingUrl != null ? existingUrl.trim() : "";
        if (mergedUrl.equals(normalizedExisting)) {
            return new SetWatchUrlOutcome(SetWatchUrlResult.ALREADY_PRESENT, seriesId);
        }

        series.setWatchUrl(mergedUrl);
        seriesDao.updateSeries(series);
        return new SetWatchUrlOutcome(SetWatchUrlResult.UPDATED, seriesId);
    }

    private AppendNotesOutcome appendNotesToSeriesByTitleInternal(String title, String notesToAppend) {
        String trimmedAppend = notesToAppend == null ? "" : notesToAppend.trim();
        if (trimmedAppend.isEmpty()) {
            return new AppendNotesOutcome(AppendNotesResult.NOTHING_TO_APPEND, -1);
        }

        Series series = seriesDao.getSeriesByTitleIgnoreCase(title);
        if (series == null) {
            return new AppendNotesOutcome(AppendNotesResult.SERIES_NOT_FOUND, -1);
        }

        long seriesId = series.getId();
        String existingNotes = series.getNotes();
        if (existingNotes == null) {
            existingNotes = "";
        }

        if (existingNotes.contains(trimmedAppend)) {
            return new AppendNotesOutcome(AppendNotesResult.ALREADY_PRESENT, seriesId);
        }

        String newNotes = existingNotes.isEmpty()
                ? trimmedAppend
                : existingNotes + "\n" + trimmedAppend;
        series.setNotes(newNotes);
        prepareSeries(series);
        seriesDao.updateSeries(series);
        triggerSync();
        return new AppendNotesOutcome(AppendNotesResult.ADDED, seriesId);
    }

    // Обновление коллекции
    public void updateCollection(Collection collection) {
        executor.execute(() -> {
            prepareCollection(collection);
            seriesDao.updateCollection(collection);
            triggerSync();
        });
    }

    // === Методы для резервного копирования (синхронные версии) ===
    public List<Collection> getAllCollectionsSync() {
        try {
            Future<List<Collection>> future = executor.submit(() ->
                    seriesDao.getAllCollectionsSync()
            );
            return future.get();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public List<Series> getAllSeriesSync() {
        try {
            Future<List<Series>> future = executor.submit(() ->
                    seriesDao.getAllSeriesSync()
            );
            return future.get();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public List<SeriesCollectionCrossRef> getAllRelationsSync() {
        try {
            Future<List<SeriesCollectionCrossRef>> future = executor.submit(() ->
                    seriesDao.getAllRelationsSync()
            );
            return future.get();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public void deleteAllData() {
        executor.execute(() -> seriesDao.deleteAllData());
    }

    // === Синхронные методы для восстановления ===
    public long insertCollectionSync(Collection collection) {
        try {
            Future<Long> future = executor.submit(() ->
                    seriesDao.insertCollectionSync(collection)
            );
            return future.get();
        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        }
    }

    public long insertSeriesSync(Series series) {
        try {
            Future<Long> future = executor.submit(() ->
                    seriesDao.insertSeriesSync(series)
            );
            return future.get();
        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        }
    }

    public void insertCrossRefSync(SeriesCollectionCrossRef crossRef) {
        try {
            Future<?> future = executor.submit(() -> seriesDao.insertCrossRefSync(crossRef));
            future.get();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // === Метод для вставки связи ===
    public void insertCrossRef(SeriesCollectionCrossRef crossRef) {
        executor.execute(() -> seriesDao.insertCrossRef(crossRef));
    }

    public void deleteAllSeriesCollectionRelationsForSeries(long seriesId) {
        executor.execute(() -> {
            // Удаляем все связи сериала с коллекциями
            seriesDao.deleteAllSeriesCollectionRelationsForSeries(seriesId);
        });
    }

    public void updateSeriesCollections(long seriesId, List<Long> newCollectionIds) {
        executor.execute(() -> {
            try {
                // 1. Получаем текущие коллекции сериала
                List<SeriesCollectionCrossRef> currentRelations = seriesDao.getAllRelationsSync();
                List<Long> currentCollectionIds = new ArrayList<>();

                for (SeriesCollectionCrossRef relation : currentRelations) {
                    if (relation.getSeriesId() == seriesId) {
                        currentCollectionIds.add(relation.getCollectionId());
                    }
                }

                // 2. Определяем коллекции для удаления и добавления
                List<Long> collectionsToRemove = new ArrayList<>(currentCollectionIds);
                collectionsToRemove.removeAll(newCollectionIds);

                List<Long> collectionsToAdd = new ArrayList<>(newCollectionIds);
                collectionsToAdd.removeAll(currentCollectionIds);

                Series series = seriesDao.getSeriesByIdSync(seriesId);

                // 3. Удаляем старые связи (и в облаке)
                for (Long collectionId : collectionsToRemove) {
                    Collection col = null;
                    for (Collection c : seriesDao.getAllCollectionsSync()) {
                        if (c.getId() == collectionId) {
                            col = c;
                            break;
                        }
                    }
                    seriesDao.deleteSeriesCollectionCrossRef(seriesId, collectionId);
                    if (series != null && series.getCloudId() != null && col != null && col.getCloudId() != null) {
                        SyncEngine.getInstance(application)
                                .deleteCrossRefRemote(series.getCloudId(), col.getCloudId());
                    }
                }

                // 4. Добавляем новые связи
                for (Long collectionId : collectionsToAdd) {
                    SeriesCollectionCrossRef crossRef = new SeriesCollectionCrossRef(seriesId, collectionId);
                    crossRef.markDirty();
                    seriesDao.insertCrossRef(crossRef);
                }

                triggerSync();

            } catch (Exception e) {
                e.printStackTrace();
                // В случае ошибки просто заменяем все связи
                if (newCollectionIds != null) {
                    // Удаляем все текущие связи
                    seriesDao.deleteAllSeriesCollectionRelationsForSeries(seriesId);

                    // Добавляем новые связи
                    for (Long collectionId : newCollectionIds) {
                        SeriesCollectionCrossRef crossRef = new SeriesCollectionCrossRef(seriesId, collectionId);
                        crossRef.markDirty();
                        seriesDao.insertCrossRef(crossRef);
                    }
                }
                triggerSync();
            }
        });
    }

    public void replaceSeriesCollections(long seriesId, List<Long> newCollectionIds) {
        executor.execute(() -> {
            // 1. Удаляем ВСЕ текущие связи
            seriesDao.deleteAllSeriesCollectionRelationsForSeries(seriesId);

            // 2. Добавляем новые связи
            if (newCollectionIds != null && !newCollectionIds.isEmpty()) {
                for (Long collectionId : newCollectionIds) {
                    SeriesCollectionCrossRef crossRef = new SeriesCollectionCrossRef(seriesId, collectionId);
                    seriesDao.insertCrossRef(crossRef);
                }
            }
        });
    }

    // Добавьте эти методы в SeriesRepository:

    // === Медиафайлы ===
    public LiveData<List<MediaFile>> getMediaFilesForSeries(long seriesId) {
        return seriesDao.getMediaFilesForSeries(seriesId);
    }

    public void insertMediaFile(MediaFile mediaFile) {
        executor.execute(() -> {
            prepareMedia(mediaFile);
            seriesDao.insertMediaFile(mediaFile);
            triggerSync();
        });
    }

    public void deleteMediaFile(long mediaId) {
        executor.execute(() -> {
            // Сначала получаем информацию о медиафайле перед удалением
            MediaFile mediaFile = seriesDao.getMediaFileSync(mediaId);
            seriesDao.deleteMediaFile(mediaId);

            // Удаляем файл из внутреннего хранилища, если он был скопирован туда
            if (mediaFile != null && mediaFile.getFileUri() != null) {
                try {
                    Uri fileUri = Uri.parse(mediaFile.getFileUri());
                    if ("file".equals(fileUri.getScheme())) {
                        File file = new File(fileUri.getPath());
                        if (file.exists()) {
                            file.delete();
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            if (mediaFile != null && mediaFile.getCloudId() != null) {
                SyncEngine.getInstance(application).deleteMediaRemote(mediaFile.getCloudId());
            }
            triggerSync();
        });
    }

    public void deleteAllMediaFilesForSeries(long seriesId) {
        executor.execute(() -> {
            // Сначала получаем все медиафайлы для серии
            List<MediaFile> mediaFiles = seriesDao.getMediaFilesForSeriesSync(seriesId);
            seriesDao.deleteAllMediaFilesForSeries(seriesId);

            // Удаляем соответствующие файлы из внутреннего хранилища
            if (mediaFiles != null) {
                for (MediaFile mediaFile : mediaFiles) {
                    if (mediaFile.getFileUri() != null) {
                        try {
                            Uri fileUri = Uri.parse(mediaFile.getFileUri());
                            if ("file".equals(fileUri.getScheme())) {
                                File file = new File(fileUri.getPath());
                                if (file.exists()) {
                                    file.delete();
                                }
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        });
    }

    // Синхронные методы для резервного копирования
    public List<MediaFile> getMediaFilesForSeriesSync(long seriesId) {
        try {
            Future<List<MediaFile>> future = executor.submit(() ->
                    seriesDao.getMediaFilesForSeriesSync(seriesId)
            );
            return future.get();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public List<MediaFile> getAllMediaFilesSync() {
        try {
            Future<List<MediaFile>> future = executor.submit(() ->
                    seriesDao.getAllMediaFilesSync()
            );
            return future.get();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }


    public void addMultipleSeriesToCollection(List<Long> seriesIds, long collectionId) {
        executor.execute(() -> {
            for (Long seriesId : seriesIds) {
                // Проверяем, есть ли уже связь
                int count = seriesDao.isSeriesInCollection(seriesId, collectionId);
                if (count == 0) {
                    SeriesCollectionCrossRef crossRef = new SeriesCollectionCrossRef(seriesId, collectionId);
                    seriesDao.insertCrossRef(crossRef);
                }
            }
        });
    }

    public LiveData<Boolean> doesCollectionExistExcludeId(String collectionName, long collectionId) {
        return seriesDao.doesCollectionExistExcludeId(collectionName, collectionId);
    }

    // Синхронный метод для вставки медиафайла (для восстановления из резервной копии)
    public long insertMediaFileSync(MediaFile mediaFile) {
        try {
            Future<Long> future = executor.submit(() ->
                    seriesDao.insertMediaFile(mediaFile)
            );
            return future.get();
        } catch (Exception e) {
            Log.e("SeriesRepository", "Error inserting media file sync", e);
            return -1;
        }
    }

    public int insertMediaFilesSync(List<MediaFile> mediaFiles) {
        if (mediaFiles == null || mediaFiles.isEmpty()) {
            return 0;
        }
        try {
            Future<List<Long>> future = executor.submit(() ->
                    seriesDao.insertMediaFiles(mediaFiles)
            );
            List<Long> ids = future.get();
            return ids != null ? ids.size() : 0;
        } catch (Exception e) {
            Log.e("SeriesRepository", "Error inserting media files sync", e);
            return 0;
        }
    }
    // === Дополнительные методы для восстановления из резервной копии ===

    public Collection getCollectionByNameSync(String name) {
        try {
            Future<Collection> future = executor.submit(() ->
                    seriesDao.getCollectionByName(name)
            );
            return future.get();
        } catch (Exception e) {
            Log.e("SeriesRepository", "Error getting collection by name sync", e);
            return null;
        }
    }

    public Series getSeriesByTitleSync(String title) {
        try {
            Future<Series> future = executor.submit(() ->
                    seriesDao.getSeriesByTitle(title)
            );
            return future.get();
        } catch (Exception e) {
            Log.e("SeriesRepository", "Error getting series by title sync", e);
            return null;
        }
    }

    public Series getSeriesByTitleIgnoreCaseSync(String title) {
        try {
            Future<Series> future = executor.submit(() ->
                    seriesDao.getSeriesByTitleIgnoreCase(title)
            );
            return future.get();
        } catch (Exception e) {
            Log.e("SeriesRepository", "Error getting series by title ignore case sync", e);
            return null;
        }
    }

    public Series getSeriesByIdSync(long seriesId) {
        try {
            Future<Series> future = executor.submit(() ->
                    seriesDao.getSeriesByIdSync(seriesId)
            );
            return future.get();
        } catch (Exception e) {
            Log.e("SeriesRepository", "Error getting series by id sync", e);
            return null;
        }
    }

    public boolean hasMediaFileWithNameSync(long seriesId, String fileName) {
        try {
            Future<Boolean> future = executor.submit(() -> {
                List<MediaFile> mediaFiles = seriesDao.getMediaFilesForSeriesSync(seriesId);
                if (mediaFiles == null || fileName == null) {
                    return false;
                }
                for (MediaFile mediaFile : mediaFiles) {
                    if (fileName.equals(mediaFile.getFileName())) {
                        return true;
                    }
                }
                return false;
            });
            Boolean result = future.get();
            return result != null && result;
        } catch (Exception e) {
            Log.e("SeriesRepository", "Error checking media file name sync", e);
            return false;
        }
    }

    public boolean checkRelationExistsSync(long seriesId, long collectionId) {
        try {
            Future<Integer> future = executor.submit(() ->
                    seriesDao.isSeriesInCollection(seriesId, collectionId)
            );
            Integer count = future.get();
            return count != null && count > 0;
        } catch (Exception e) {
            Log.e("SeriesRepository", "Error checking relation exists sync", e);
            return false;
        }
    }

    public MediaFile getMediaFileByUriAndSeriesSync(String fileUri, long seriesId) {
        try {
            Future<MediaFile> future = executor.submit(() ->
                    seriesDao.getMediaFileByUriAndSeries(fileUri, seriesId)
            );
            return future.get();
        } catch (Exception e) {
            Log.e("SeriesRepository", "Error getting media file by URI and series sync", e);
            return null;
        }
    }

    public void updateSeriesSync(Series series) {
        try {
            Future<?> future = executor.submit(() -> seriesDao.updateSeries(series));
            future.get();
        } catch (Exception e) {
            Log.e("SeriesRepository", "Error updating series sync", e);
        }
    }
}