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

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class SeriesRepository {
    private SeriesDao seriesDao;
    private ExecutorService executor = Executors.newSingleThreadExecutor();
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    // Статическое поле для синглтона
    private static SeriesRepository instance;

    // Приватный конструктор
    public SeriesRepository(Application application) {
        SeriesDatabase database = SeriesDatabase.getDatabase(application);
        seriesDao = database.seriesDao();
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
        executor.execute(() -> seriesDao.insertCollection(collection));
    }

    public void deleteCollection(long collectionId) {
        executor.execute(() -> seriesDao.deleteCollection(collectionId));
    }

    public void deleteCollection(Collection collection) {
        executor.execute(() -> {
            // Удаляем сначала все связи
            deleteAllSeriesCollectionRelationsForCollection(collection.getId());
            // Затем удаляем саму коллекцию
            seriesDao.deleteCollection(collection.getId());
        });
    }

    public void deleteAllSeriesCollectionRelationsForCollection(long collectionId) {
        executor.execute(() -> {
            // Удаляем все связи сериалов с этой коллекцией
            seriesDao.deleteAllSeriesCollectionRelationsForCollection(collectionId);
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
        executor.execute(() -> seriesDao.insertSeries(series));
    }

    public void updateSeries(Series series) {
        executor.execute(() -> seriesDao.updateSeries(series));
    }

    public void deleteSeries(long seriesId) {
        executor.execute(() -> seriesDao.deleteSeries(seriesId));
    }

    public void insertSeriesWithCollections(Series series, List<Long> collectionIds) {
        executor.execute(() -> {
            long seriesId = seriesDao.insertSeries(series);
            if (collectionIds != null) {
                for (Long collectionId : collectionIds) {
                    SeriesCollectionCrossRef crossRef = new SeriesCollectionCrossRef(seriesId, collectionId);
                    seriesDao.insertCrossRef(crossRef);
                }
            }
        });
    }

    // === Статусы ===
    public void updateSeriesWatchedStatus(long seriesId, boolean isWatched) {
        executor.execute(() -> {
            seriesDao.updateSeriesWatchedStatus(seriesId, isWatched);
            seriesDao.updateCrossRefWatchedStatus(seriesId, isWatched);
        });
    }

    public void updateSeriesFavoriteStatus(long seriesId, boolean isFavorite) {
        executor.execute(() -> seriesDao.updateSeriesFavoriteStatus(seriesId, isFavorite));
    }

    public void updateSeriesStatus(long seriesId, String status) {
        executor.execute(() -> seriesDao.updateSeriesStatus(seriesId, status));
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
                seriesDao.insertCrossRef(crossRef);
            }
        });
    }

    public void removeSeriesFromCollection(long seriesId, long collectionId) {
        executor.execute(() -> seriesDao.removeSeriesFromCollection(seriesId, collectionId));
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

    // Обновление коллекции
    public void updateCollection(Collection collection) {
        executor.execute(() -> seriesDao.updateCollection(collection));
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
        executor.execute(() -> seriesDao.insertCrossRefSync(crossRef));
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

                // 3. Удаляем старые связи
                for (Long collectionId : collectionsToRemove) {
                    seriesDao.deleteSeriesCollectionCrossRef(seriesId, collectionId);
                }

                // 4. Добавляем новые связи
                for (Long collectionId : collectionsToAdd) {
                    SeriesCollectionCrossRef crossRef = new SeriesCollectionCrossRef(seriesId, collectionId);
                    seriesDao.insertCrossRef(crossRef);
                }

            } catch (Exception e) {
                e.printStackTrace();
                // В случае ошибки просто заменяем все связи
                if (newCollectionIds != null) {
                    // Удаляем все текущие связи
                    seriesDao.deleteAllSeriesCollectionRelationsForSeries(seriesId);

                    // Добавляем новые связи
                    for (Long collectionId : newCollectionIds) {
                        SeriesCollectionCrossRef crossRef = new SeriesCollectionCrossRef(seriesId, collectionId);
                        seriesDao.insertCrossRef(crossRef);
                    }
                }
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
        executor.execute(() -> seriesDao.insertMediaFile(mediaFile));
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
        executor.execute(() -> seriesDao.updateSeries(series));
    }
}