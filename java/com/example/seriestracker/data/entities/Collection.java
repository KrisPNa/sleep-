package com.example.seriestracker.data.entities;

import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import androidx.room.TypeConverters;

import com.example.seriestracker.data.converters.ColorsConverter;

import java.util.Arrays;
import java.util.List;

@Entity(tableName = "collections", indices = {
        @Index(value = {"cloudId"}, unique = true)
})
@TypeConverters(ColorsConverter.class)
public class Collection {
    @PrimaryKey(autoGenerate = true)
    private long id;

    private String name;
    private long createdAt;
    private boolean isFavorite;
    private List<String> colors;
    private int seriesCount;

    private String cloudId;
    private long updatedAt;
    private boolean syncDirty;

    public static final String[] AVAILABLE_COLORS = {
            "#2196F3", "#FF4081", "#4CAF50", "#FF9800", "#9C27B0", "#795548",
            "#607D8B", "#E91E63", "#00BCD4", "#8BC34A", "#FF5722", "#673AB7",
            "#000000", "#DC143C", "#8B0000", "#F08080", "#FF69B4", "#C71585",
            "#FF4500", "#FFA500", "#FFFF00", "#BDB76B", "#E6E6FA", "#EE82EE",
            "#FF00FF", "#9370DB", "#8B008B", "#4B0082", "#000080", "#0000FF",
            "#00BFFF", "#008080", "#00CED1", "#00FFFF", "#7FFFD4", "#66CDAA",
            "#008B8B", "#8FBC8F", "#00FA9A", "#00FF00", "#228B22", "#006400",
            "#ADFF2F", "#2F4F4F", "#708090", "#696969"
    };

    public Collection() {
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = this.createdAt;
        this.isFavorite = false;
        this.colors = Arrays.asList(AVAILABLE_COLORS[0]);
        this.seriesCount = 0;
        this.syncDirty = true;
    }

    public Collection(String name) {
        this();
        this.name = name;
    }

    public Collection(String name, List<String> colors) {
        this();
        this.name = name;
        this.colors = colors;
    }

    public long getId() { return id; }
    public String getName() { return name; }
    public long getCreatedAt() { return createdAt; }
    public boolean isFavorite() { return isFavorite; }
    public List<String> getColors() { return colors; }
    public int getSeriesCount() { return seriesCount; }
    public String getCloudId() { return cloudId; }
    public long getUpdatedAt() { return updatedAt; }
    public boolean getSyncDirty() { return syncDirty; }

    public void setId(long id) { this.id = id; }
    public void setName(String name) { this.name = name; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
    public void setFavorite(boolean favorite) { isFavorite = favorite; }
    public void setColors(List<String> colors) { this.colors = colors; }
    public void setSeriesCount(int seriesCount) { this.seriesCount = seriesCount; }
    public void setCloudId(String cloudId) { this.cloudId = cloudId; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
    public void setSyncDirty(boolean syncDirty) { this.syncDirty = syncDirty; }

    public void markDirty() {
        this.updatedAt = System.currentTimeMillis();
        this.syncDirty = true;
    }
}
