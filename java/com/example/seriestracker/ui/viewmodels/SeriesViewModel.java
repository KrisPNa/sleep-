
        package com.example.seriestracker.ui.viewmodels;

import android.app.Application;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.example.seriestracker.data.entities.Collection;
import com.example.seriestracker.data.entities.CollectionWithSeries;
import com.example.seriestracker.data.entities.MediaFile;
import com.example.seriestracker.data.entities.Series;
import com.example.seriestracker.data.entities.SeriesCollectionCrossRef;
import com.example.seriestracker.data.repository.SeriesRepository;

import java.util.Arrays;
import java.util.List;

public class SeriesViewModel extends AndroidViewModel {
    private SeriesRepository repository;
    private LiveData<List<Series>> allSeries;
    private LiveData<List<Collection>> allCollections;
    private LiveData<List<CollectionWithSeries>> collectionsWithSeries;

    public SeriesViewModel(Application application) {
        super(application);
        repository = new SeriesRepository(application);
        allSeries = repository.getAllSeries();
        allCollections = repository.getAllCollections();
        collectionsWithSeries = repository.getCollectionsWithSeries();
    }

    // === Коллекции ===
    public void createCollection(String name) {
        // Создаем коллекцию с цветом по умолчанию (первый цвет из AVAILABLE_COLORS)
        List<String> defaultColors = Arrays.asList(Collection.AVAILABLE_COLORS[0]);
        Collection collection = new Collection(name, defaultColors);
        repository.insertCollection(collection);
    }

    // === Создание коллекции с одним цветом ===
    public void createCollectionWithColor(String name, String color) {
        // Создаем список с одним цветом
        List<String> colors = Arrays.asList(color);
        Collection collection = new Collection(name, colors);
        repository.insertCollection(collection);
    }

    // === Создание коллекции с несколькими цветами ===
    public void createCollectionWithColors(String name, List<String> colors) {
        Collection collection = new Collection(name, colors);
        repository.insertCollection(collection);
    }

    public LiveData<List<Collection>> getAllCollections() {
        return allCollections;
    }

    public void deleteCollection(long collectionId) {
        repository.deleteCollection(collectionId);
    }

    // === Новые методы для управления коллекциями ===
    public void updateCollection(Collection collection) {
        repository.updateCollection(collection);
    }

    public void deleteCollection(Collection collection) {
        repository.deleteCollection(collection);
    }

    public LiveData<Collection> getCollectionById(long collectionId) {
        return repository.getCollectionById(collectionId);
    }

    // === Сериалы ===
    public LiveData<List<Series>> getAllSeries() {
        return allSeries;
    }

    public LiveData<Series> getSeriesById(long seriesId) {
        return repository.getSeriesById(seriesId);
    }

    public void addSeries(String title, String imageUri, List<Long> collectionIds, String notes) {
        Series series = new Series();
        series.setTitle(title);
        series.setImageUri(imageUri);
        series.setNotes(notes);
        series.setIsWatched(false);
        series.setStatus("planned");
        series.setIsFavorite(false);
        series.setSeasons(0);
        series.setEpisodes(0);
        series.setGenre("");

        repository.insertSeriesWithCollections(series, collectionIds);
    }



    public LiveData<Boolean> doesCollectionExistExcludeId(String collectionName, long collectionId) {
        return repository.doesCollectionExistExcludeId(collectionName, collectionId);
    }

    public void updateSeries(Series series) {
        repository.updateSeries(series);
    }

    public void deleteSeries(long seriesId) {
        repository.deleteSeries(seriesId);
    }

    // === Статусы ===
    public void toggleWatchedStatus(long seriesId, boolean isWatched) {
        repository.updateSeriesWatchedStatus(seriesId, isWatched);
    }

    public void toggleFavoriteStatus(long seriesId, boolean isFavorite) {
        repository.updateSeriesFavoriteStatus(seriesId, isFavorite);
    }

    public void updateSeriesStatus(long seriesId, String status) {
        repository.updateSeriesStatus(seriesId, status);
    }

    // === Коллекции с сериалами ===
    public LiveData<List<CollectionWithSeries>> getCollectionsWithSeries() {
        return collectionsWithSeries;
    }

    // === Сериалы в коллекции ===
    public LiveData<List<Series>> getSeriesInCollection(long collectionId) {
        return repository.getSeriesInCollection(collectionId);
    }

    public void addSeriesToCollection(long seriesId, long collectionId) {
        repository.addSeriesToCollection(seriesId, collectionId);
    }

    public void removeSeriesFromCollection(long seriesId, long collectionId) {
        repository.removeSeriesFromCollection(seriesId, collectionId);
    }

    // === Коллекции для сериала ===
    public LiveData<List<Collection>> getCollectionsForSeries(long seriesId) {
        return repository.getCollectionsForSeries(seriesId);
    }

    // === Количество сериалов в коллекции ===
    public LiveData<Integer> getSeriesCountInCollection(long collectionId) {
        return repository.getSeriesCountInCollection(collectionId);
    }

    // === Методы для управления связями (для EditSeriesScreen) ===
    public void insertSeriesCollectionCrossRef(SeriesCollectionCrossRef crossRef) {
        repository.insertSeriesCollectionCrossRef(crossRef);
    }

    public void deleteSeriesCollectionCrossRef(long seriesId, long collectionId) {
        repository.deleteSeriesCollectionCrossRef(seriesId, collectionId);
    }

    // === Метод для удаления всех связей коллекции ===
    public void deleteAllSeriesCollectionRelationsForCollection(long collectionId) {
        repository.deleteAllSeriesCollectionRelationsForCollection(collectionId);
    }

    // === Методы проверки существования ===
    public LiveData<Boolean> doesCollectionExist(String collectionName) {
        return repository.doesCollectionExist(collectionName);
    }

    public LiveData<Boolean> doesSeriesExist(String seriesTitle) {
        return repository.doesSeriesExist(seriesTitle);
    }

    public void deleteAllSeriesCollectionRelationsForSeries(long seriesId) {
        repository.deleteAllSeriesCollectionRelationsForSeries(seriesId);
    }

    public void updateSeriesCollections(long seriesId, List<Long> newCollectionIds) {
        repository.updateSeriesCollections(seriesId, newCollectionIds);
    }

    public void replaceSeriesCollections(long seriesId, List<Long> collectionIds) {
        repository.replaceSeriesCollections(seriesId, collectionIds);
    }

    // Добавьте в SeriesViewModel:
    private LiveData<List<MediaFile>> mediaFiles;

    // === Медиафайлы ===
    public LiveData<List<MediaFile>> getMediaFilesForSeries(long seriesId) {
        return repository.getMediaFilesForSeries(seriesId);
    }

    public void addMediaFile(MediaFile mediaFile) {
        repository.insertMediaFile(mediaFile);
    }

    public void deleteMediaFile(long mediaId) {
        repository.deleteMediaFile(mediaId);
    }

    public void deleteAllMediaFilesForSeries(long seriesId) {
        repository.deleteAllMediaFilesForSeries(seriesId);
    }

}