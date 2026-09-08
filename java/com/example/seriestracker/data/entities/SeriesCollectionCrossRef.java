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
    private long seriesId;
    private long collectionId;
    private boolean isWatched;
    private long updatedAt;
    private boolean syncDirty;

    public SeriesCollectionCrossRef(long seriesId, long collectionId) {
        this.seriesId = seriesId;
        this.collectionId = collectionId;
        this.isWatched = false;
        this.updatedAt = System.currentTimeMillis();
        this.syncDirty = true;
    }

    public long getSeriesId() { return seriesId; }
    public long getCollectionId() { return collectionId; }
    public boolean getIsWatched() { return isWatched; }
    public long getUpdatedAt() { return updatedAt; }
    public boolean getSyncDirty() { return syncDirty; }

    public void setSeriesId(long seriesId) { this.seriesId = seriesId; }
    public void setCollectionId(long collectionId) { this.collectionId = collectionId; }
    public void setIsWatched(boolean watched) { this.isWatched = watched; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
    public void setSyncDirty(boolean syncDirty) { this.syncDirty = syncDirty; }

    public void markDirty() {
        this.updatedAt = System.currentTimeMillis();
        this.syncDirty = true;
    }
}
