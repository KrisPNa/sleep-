package com.example.seriestracker.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.Update;

import com.example.seriestracker.data.entities.Collection;
import com.example.seriestracker.data.entities.CollectionWithSeries;
import com.example.seriestracker.data.entities.MediaFile;
import com.example.seriestracker.data.entities.Series;
import com.example.seriestracker.data.entities.SeriesCollectionCrossRef;

import java.util.List;

@Dao
public interface SeriesDao {

    // === Вставка данных ===
    @Insert
    long insertSeries(Series series);

    @Insert
    void insertCollection(Collection collection);

    @Insert
    void insertCrossRef(SeriesCollectionCrossRef crossRef);

    // === Синхронные методы вставки для восстановления ===
    @Insert
    long insertCollectionSync(Collection collection);

    @Insert
    long insertSeriesSync(Series series);

    @Insert
    void insertCrossRefSync(SeriesCollectionCrossRef crossRef);

    // === Обновление данных ===
    @Query("UPDATE series SET isWatched = :isWatched WHERE id = :seriesId")
    void updateSeriesWatchedStatus(long seriesId, boolean isWatched);

    @Query("UPDATE series_collection_cross_ref SET isWatched = :isWatched WHERE seriesId = :seriesId")
    void updateCrossRefWatchedStatus(long seriesId, boolean isWatched);

    @Query("UPDATE series SET isFavorite = :isFavorite WHERE id = :seriesId")
    void updateSeriesFavoriteStatus(long seriesId, boolean isFavorite);

    @Query("UPDATE collections SET isFavorite = :isFavorite WHERE id = :collectionId")
    void updateCollectionFavoriteStatus(long collectionId, boolean isFavorite);

    @Query("UPDATE series SET status = :status WHERE id = :seriesId")
    void updateSeriesStatus(long seriesId, String status);

    @Update
    void updateSeries(Series series);

    @Update
    void updateCollection(Collection collection);

    // === Удаление данных ===
    @Query("DELETE FROM series WHERE id = :seriesId")
    void deleteSeries(long seriesId);

    @Query("DELETE FROM collections WHERE id = :collectionId")
    void deleteCollection(long collectionId);

    @Query("DELETE FROM series_collection_cross_ref WHERE seriesId = :seriesId AND collectionId = :collectionId")
    void removeSeriesFromCollection(long seriesId, long collectionId);

    // === Новый метод для удаления всех связей коллекции ===
    @Query("DELETE FROM series_collection_cross_ref WHERE collectionId = :collectionId")
    void deleteAllSeriesCollectionRelationsForCollection(long collectionId);

    // === Удаление всех данных (для очистки перед восстановлением) ===
    @Transaction
    default void deleteAllData() {
        deleteAllRelations();
        deleteAllSeries();
        deleteAllCollections();
    }

    @Query("DELETE FROM series_collection_cross_ref")
    void deleteAllRelations();

    @Query("DELETE FROM series")
    void deleteAllSeries();

    @Query("DELETE FROM collections")
    void deleteAllCollections();

    // === Получение данных (LiveData) ===
    @Query("SELECT * FROM series ORDER BY isFavorite DESC, title COLLATE NOCASE ASC")
    LiveData<List<Series>> getAllSeries();

    @Query("SELECT * FROM collections ORDER BY isFavorite DESC, name COLLATE NOCASE ASC")
    LiveData<List<Collection>> getAllCollections();

    @Query("SELECT * FROM series WHERE id = :seriesId")
    LiveData<Series> getSeriesById(long seriesId);

    @Query("SELECT * FROM collections WHERE id = :collectionId")
    LiveData<Collection> getCollectionById(long collectionId);

    // Получение сериалов в конкретной коллекции
    @Transaction
    @Query("SELECT s.* FROM series s " +
            "INNER JOIN series_collection_cross_ref sc ON s.id = sc.seriesId " +
            "WHERE sc.collectionId = :collectionId " +
            "ORDER BY s.isFavorite DESC, s.title COLLATE NOCASE ASC")
    LiveData<List<Series>> getSeriesInCollection(long collectionId);

    // Получение всех коллекций с их сериалами
    @Transaction
    @Query("SELECT * FROM collections ORDER BY isFavorite DESC, name COLLATE NOCASE ASC")
    LiveData<List<CollectionWithSeries>> getCollectionsWithSeries();

    // Проверка существует ли связь
    @Query("SELECT COUNT(*) FROM series_collection_cross_ref WHERE seriesId = :seriesId AND collectionId = :collectionId")
    int isSeriesInCollection(long seriesId, long collectionId);

    // Получение коллекций для конкретного сериала
    @Query("SELECT c.* FROM collections c " +
            "INNER JOIN series_collection_cross_ref sc ON c.id = sc.collectionId " +
            "WHERE sc.seriesId = :seriesId")
    LiveData<List<Collection>> getCollectionsForSeries(long seriesId);

    // Подсчет сериалов в коллекции
    @Query("SELECT COUNT(*) FROM series_collection_cross_ref WHERE collectionId = :collectionId")
    LiveData<Integer> getSeriesCountInCollection(long collectionId);

    // Новые методы для EditSeriesScreen
    @Query("SELECT * FROM series_collection_cross_ref WHERE seriesId = :seriesId AND collectionId = :collectionId")
    SeriesCollectionCrossRef getCrossRef(long seriesId, long collectionId);

    @Insert
    void insertSeriesCollectionCrossRef(SeriesCollectionCrossRef crossRef);

    @Query("DELETE FROM series_collection_cross_ref WHERE seriesId = :seriesId AND collectionId = :collectionId")
    void deleteSeriesCollectionCrossRef(long seriesId, long collectionId);

    // Проверка существования коллекции
    @Query("SELECT COUNT(*) > 0 FROM collections WHERE LOWER(name) = LOWER(:collectionName)")
    LiveData<Boolean> doesCollectionExist(String collectionName);

    @Query("SELECT COUNT(*) > 0 FROM series WHERE LOWER(title) = LOWER(:seriesTitle)")
    LiveData<Boolean> doesSeriesExist(String seriesTitle);

    // === Синхронные методы для резервного копирования ===
    @Query("SELECT * FROM collections")
    List<Collection> getAllCollectionsSync();

    @Query("SELECT * FROM series")
    List<Series> getAllSeriesSync();

    @Query("SELECT * FROM series_collection_cross_ref")
    List<SeriesCollectionCrossRef> getAllRelationsSync();

    @Query("DELETE FROM series_collection_cross_ref WHERE seriesId = :seriesId")
    void deleteAllSeriesCollectionRelationsForSeries(long seriesId);


    // Проверка существования коллекции с исключением текущего ID (для редактирования)
    @Query("SELECT COUNT(*) > 0 FROM collections WHERE LOWER(name) = LOWER(:collectionName) AND id != :collectionId")
    LiveData<Boolean> doesCollectionExistExcludeId(String collectionName, long collectionId);
// В SeriesDao.java добавьте:


    // === Медиафайлы ===
    @Insert
    long insertMediaFile(MediaFile mediaFile);

    @Query("DELETE FROM media_files WHERE id = :mediaId")
    void deleteMediaFile(long mediaId);

    @Query("DELETE FROM media_files WHERE seriesId = :seriesId")
    void deleteAllMediaFilesForSeries(long seriesId);

    @Query("SELECT * FROM media_files WHERE seriesId = :seriesId ORDER BY createdAt DESC")
    LiveData<List<MediaFile>> getMediaFilesForSeries(long seriesId);

    @Query("SELECT * FROM media_files WHERE id = :mediaId")
    LiveData<MediaFile> getMediaFileById(long mediaId);

    // Синхронные методы для резервного копирования
    @Query("SELECT * FROM media_files WHERE seriesId = :seriesId")
    List<MediaFile> getMediaFilesForSeriesSync(long seriesId);

    @Query("SELECT * FROM media_files")
    List<MediaFile> getAllMediaFilesSync();
}