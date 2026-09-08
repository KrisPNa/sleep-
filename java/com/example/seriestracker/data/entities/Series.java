package com.example.seriestracker.data.entities;

import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "series", indices = {
        @Index(value = {"title"}, unique = true),
        @Index(value = {"cloudId"}, unique = true)
})
public class Series {
    @PrimaryKey(autoGenerate = true)
    private long id;

    private String title;
    private String imageUri;
    private boolean isWatched;
    private String notes;
    private String watchUrl;
    private String watchAt;
    private long createdAt;

    private String description;
    private String status;
    private boolean isFavorite;
    private int rating;
    private String genre;
    private int seasons;
    private int episodes;

    private String cloudId;
    private long updatedAt;
    private boolean syncDirty;
    private String cloudImagePath;

    public Series() {
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = this.createdAt;
        this.status = "planned";
        this.isFavorite = false;
        this.isWatched = false;
        this.rating = 0;
        this.seasons = 0;
        this.episodes = 0;
        this.syncDirty = true;
    }

    public Series(String title) {
        this();
        this.title = title;
    }

    public long getId() { return id; }
    public String getTitle() { return title; }
    public String getImageUri() { return imageUri; }
    public boolean getIsWatched() { return isWatched; }
    public String getNotes() { return notes; }
    public String getWatchUrl() { return watchUrl; }
    public String getWatchAt() { return watchAt; }
    public String getDescription() { return description; }
    public long getCreatedAt() { return createdAt; }
    public String getStatus() { return status; }
    public boolean getIsFavorite() { return isFavorite; }
    public int getRating() { return rating; }
    public String getGenre() { return genre; }
    public int getSeasons() { return seasons; }
    public int getEpisodes() { return episodes; }
    public String getCloudId() { return cloudId; }
    public long getUpdatedAt() { return updatedAt; }
    public boolean getSyncDirty() { return syncDirty; }
    public String getCloudImagePath() { return cloudImagePath; }

    public void setId(long id) { this.id = id; }
    public void setTitle(String title) { this.title = title; }
    public void setImageUri(String imageUri) { this.imageUri = imageUri; }
    public void setIsWatched(boolean watched) { this.isWatched = watched; }
    public void setNotes(String notes) { this.notes = notes; }
    public void setWatchUrl(String watchUrl) { this.watchUrl = watchUrl; }
    public void setWatchAt(String watchAt) { this.watchAt = watchAt; }
    public void setDescription(String description) { this.description = description; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
    public void setStatus(String status) { this.status = status; }
    public void setIsFavorite(boolean favorite) { this.isFavorite = favorite; }
    public void setRating(int rating) { this.rating = rating; }
    public void setGenre(String genre) { this.genre = genre; }
    public void setSeasons(int seasons) { this.seasons = seasons; }
    public void setEpisodes(int episodes) { this.episodes = episodes; }
    public void setCloudId(String cloudId) { this.cloudId = cloudId; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
    public void setSyncDirty(boolean syncDirty) { this.syncDirty = syncDirty; }
    public void setCloudImagePath(String cloudImagePath) { this.cloudImagePath = cloudImagePath; }

    public void markDirty() {
        this.updatedAt = System.currentTimeMillis();
        this.syncDirty = true;
    }
}
