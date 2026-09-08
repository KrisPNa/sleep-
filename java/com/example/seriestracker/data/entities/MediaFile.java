package com.example.seriestracker.data.entities;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import static androidx.room.ForeignKey.CASCADE;

import java.io.Serializable;

@Entity(
        tableName = "media_files",
        foreignKeys = @ForeignKey(
                entity = Series.class,
                parentColumns = "id",
                childColumns = "seriesId",
                onDelete = CASCADE
        ),
        indices = {@Index("seriesId"), @Index(value = {"cloudId"}, unique = true)}
)
public class MediaFile implements Serializable {
    private static final long serialVersionUID = 1L;
    @PrimaryKey(autoGenerate = true)
    private long id;

    private long seriesId;
    private String fileUri;
    private String fileType;
    private String fileName;
    private String filePath;
    private long fileSize;
    private long createdAt;
    private String description;

    private String cloudId;
    private long updatedAt;
    private boolean syncDirty;
    private String storagePath;

    public MediaFile() {
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = this.createdAt;
        this.syncDirty = true;
    }

    public MediaFile(long seriesId, String fileUri, String fileType, String fileName) {
        this();
        this.seriesId = seriesId;
        this.fileUri = fileUri;
        this.fileType = fileType;
        this.fileName = fileName;
    }

    public long getId() { return id; }
    public long getSeriesId() { return seriesId; }
    public String getFileUri() { return fileUri; }
    public String getFileType() { return fileType; }
    public String getFileName() { return fileName; }
    public String getFilePath() { return filePath; }
    public long getFileSize() { return fileSize; }
    public long getCreatedAt() { return createdAt; }
    public String getDescription() { return description; }
    public String getCloudId() { return cloudId; }
    public long getUpdatedAt() { return updatedAt; }
    public boolean getSyncDirty() { return syncDirty; }
    public String getStoragePath() { return storagePath; }

    public void setId(long id) { this.id = id; }
    public void setSeriesId(long seriesId) { this.seriesId = seriesId; }
    public void setFileUri(String fileUri) { this.fileUri = fileUri; }
    public void setFileType(String fileType) { this.fileType = fileType; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public void setFilePath(String filePath) { this.filePath = filePath; }
    public void setFileSize(long fileSize) { this.fileSize = fileSize; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
    public void setDescription(String description) { this.description = description; }
    public void setCloudId(String cloudId) { this.cloudId = cloudId; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
    public void setSyncDirty(boolean syncDirty) { this.syncDirty = syncDirty; }
    public void setStoragePath(String storagePath) { this.storagePath = storagePath; }

    public void markDirty() {
        this.updatedAt = System.currentTimeMillis();
        this.syncDirty = true;
    }
}
