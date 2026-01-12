// File: MediaFile.java
package com.example.seriestracker.data.entities;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import static androidx.room.ForeignKey.CASCADE;

@Entity(
        tableName = "media_files",
        foreignKeys = @ForeignKey(
                entity = Series.class,
                parentColumns = "id",
                childColumns = "seriesId",
                onDelete = CASCADE
        ),
        indices = {@Index("seriesId")}
)
public class MediaFile {
    @PrimaryKey(autoGenerate = true)
    private long id;

    private long seriesId;
    private String fileUri;
    private String fileType; // "image" или "video"
    private String fileName;
    private String filePath;
    private long fileSize;
    private long createdAt;
    private String description;

    public MediaFile() {
        this.createdAt = System.currentTimeMillis();
    }

    public MediaFile(long seriesId, String fileUri, String fileType, String fileName) {
        this();
        this.seriesId = seriesId;
        this.fileUri = fileUri;
        this.fileType = fileType;
        this.fileName = fileName;
    }

    // Геттеры
    public long getId() { return id; }
    public long getSeriesId() { return seriesId; }
    public String getFileUri() { return fileUri; }
    public String getFileType() { return fileType; }
    public String getFileName() { return fileName; }
    public String getFilePath() { return filePath; }
    public long getFileSize() { return fileSize; }
    public long getCreatedAt() { return createdAt; }
    public String getDescription() { return description; }

    // Сеттеры
    public void setId(long id) { this.id = id; }
    public void setSeriesId(long seriesId) { this.seriesId = seriesId; }
    public void setFileUri(String fileUri) { this.fileUri = fileUri; }
    public void setFileType(String fileType) { this.fileType = fileType; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public void setFilePath(String filePath) { this.filePath = filePath; }
    public void setFileSize(long fileSize) { this.fileSize = fileSize; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
    public void setDescription(String description) { this.description = description; }
}