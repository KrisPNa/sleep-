package com.example.seriestracker.data.entities;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.Index;

@Entity(tableName = "series", indices = {@Index(value = {"title"}, unique = true)})
public class Series {
    @PrimaryKey(autoGenerate = true)
    private long id;

    private String title;
    private String imageUri;
    private boolean isWatched;
    private String notes;
    private long createdAt;

    // Новые поля
    private String status;
    private boolean isFavorite;
    private int rating;
    private String genre;
    private int seasons;
    private int episodes;


    public Series() {
        this.createdAt = System.currentTimeMillis();
        this.status = "planned";
        this.isFavorite = false;
        this.isWatched = false;
        this.rating = 0;
        this.seasons = 0;
        this.episodes = 0;
    }

    public Series(String title) {
        this();
        this.title = title;
    }

    // Геттеры
    public long getId() { return id; }
    public String getTitle() { return title; }
    public String getImageUri() { return imageUri; }
    public boolean getIsWatched() { return isWatched; }
    public String getNotes() { return notes; }
    public long getCreatedAt() { return createdAt; }
    public String getStatus() { return status; }
    public boolean getIsFavorite() { return isFavorite; }
    public int getRating() { return rating; }
    public String getGenre() { return genre; }
    public int getSeasons() { return seasons; }
    public int getEpisodes() { return episodes; }

    // Сеттеры
    public void setId(long id) { this.id = id; }
    public void setTitle(String title) { this.title = title; }
    public void setImageUri(String imageUri) { this.imageUri = imageUri; }
    public void setIsWatched(boolean watched) { this.isWatched = watched; }
    public void setNotes(String notes) { this.notes = notes; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
    public void setStatus(String status) { this.status = status; }
    public void setIsFavorite(boolean favorite) { this.isFavorite = favorite; }
    public void setRating(int rating) { this.rating = rating; }
    public void setGenre(String genre) { this.genre = genre; }
    public void setSeasons(int seasons) { this.seasons = seasons; }
    public void setEpisodes(int episodes) { this.episodes = episodes; }
}