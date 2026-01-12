package com.example.seriestracker.data.entities;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;

import static androidx.room.ForeignKey.CASCADE;

@Entity(
        tableName = "series_collection_cross_ref",
        primaryKeys = {"seriesId", "collectionId"},
        foreignKeys = {
                @ForeignKey(
                        entity = Series.class,
                        parentColumns = "id",
                        childColumns = "seriesId",
                        onDelete = CASCADE
                ),
                @ForeignKey(
                        entity = Collection.class,
                        parentColumns = "id",
                        childColumns = "collectionId",
                        onDelete = CASCADE
                )
        },
        indices = {
                @Index("seriesId"),
                @Index("collectionId")
        }
)
public class SeriesCollectionCrossRef {
    private long seriesId;      // Должно совпадать с childColumns в ForeignKey
    private long collectionId;  // Должно совпадать с childColumns в ForeignKey
    private boolean isWatched;

    public SeriesCollectionCrossRef(long seriesId, long collectionId) {
        this.seriesId = seriesId;
        this.collectionId = collectionId;
        this.isWatched = false;
    }

    // Геттеры - ВАЖНО: имена должны совпадать с полями!
    public long getSeriesId() { return seriesId; }
    public long getCollectionId() { return collectionId; }
    public boolean getIsWatched() { return isWatched; }

    // Сеттеры
    public void setSeriesId(long seriesId) { this.seriesId = seriesId; }
    public void setCollectionId(long collectionId) { this.collectionId = collectionId; }
    public void setIsWatched(boolean watched) { this.isWatched = watched; }
}