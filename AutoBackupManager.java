import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class AutoBackupManager {
    private BackupFileManager backupFileManager;
    private Timer autoBackupTimer;
    private int autoBackupIntervalMinutes;
    private boolean autoBackupEnabled;
    private String baseBackupName;
    private String backupDirectory;

    public AutoBackupManager(String backupDirectory, String baseBackupName) {
        this.backupFileManager = new BackupFileManager(backupDirectory);
        this.backupDirectory = backupDirectory;
        this.baseBackupName = baseBackupName;
        this.autoBackupEnabled = false;
        this.autoBackupIntervalMinutes = 60; // Default to 1 hour
    }

    public void startAutoBackup(int intervalMinutes, MediaItem itemToBackup) {
        if (intervalMinutes <= 0) {
            System.out.println("Invalid interval. Using default 60 minutes.");
            intervalMinutes = 60;
        }
        
        this.autoBackupIntervalMinutes = intervalMinutes;
        
        if (autoBackupTimer != null) {
            stopAutoBackup();
        }
        
        autoBackupTimer = new Timer(true); // Daemon timer
        autoBackupEnabled = true;
        
        TimerTask backupTask = new TimerTask() {
            @Override
            public void run() {
                if (autoBackupEnabled) {
                    performAutoBackup(itemToBackup);
                }
            }
        };
        
        // Schedule the first backup immediately, then repeat at the specified interval
        autoBackupTimer.scheduleAtFixedRate(backupTask, 0, intervalMinutes * 60 * 1000L);
        
        System.out.println("Auto backup started. Interval: " + intervalMinutes + " minutes.");
    }

    public void stopAutoBackup() {
        if (autoBackupTimer != null) {
            autoBackupTimer.cancel();
            autoBackupTimer = null;
        }
        autoBackupEnabled = false;
        System.out.println("Auto backup stopped.");
    }

    private void performAutoBackup(MediaItem itemToBackup) {
        try {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
            String backupName = baseBackupName + "_" + timestamp;
            
            backupFileManager.createBackup(itemToBackup, backupName);
            System.out.println("Auto backup completed: " + backupName);
            
            // Optionally, keep only the last N backups to manage disk space
            cleanupOldBackups(10); // Keep only the last 10 backups
            
        } catch (IOException e) {
            System.err.println("Auto backup failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void setAutoBackupInterval(int minutes) {
        if (minutes > 0) {
            this.autoBackupIntervalMinutes = minutes;
            
            // Restart the timer with the new interval if auto backup is enabled
            if (autoBackupEnabled) {
                MediaItem currentItem = getCurrentItemForBackup(); // This would need to be implemented based on your application
                startAutoBackup(minutes, currentItem);
            }
        }
    }

    public int getAutoBackupInterval() {
        return this.autoBackupIntervalMinutes;
    }

    public boolean isAutoBackupEnabled() {
        return this.autoBackupEnabled;
    }

    public List<String> getAvailableBackups() throws IOException {
        return backupFileManager.listAvailableBackups();
    }

    public MediaItem restoreBackup(String backupName) throws IOException {
        return backupFileManager.restoreFromBackup(backupName);
    }

    private void cleanupOldBackups(int maxBackups) {
        try {
            List<String> allBackups = backupFileManager.listAvailableBackups();
            
            // Sort backups by date (assuming they have timestamps in their names)
            // This is a simplified approach - you might want more sophisticated sorting
            allBackups.sort((a, b) -> {
                // Extract timestamps and compare
                String timestampA = extractTimestamp(a);
                String timestampB = extractTimestamp(b);
                return timestampB.compareTo(timestampA); // Descending order (newest first)
            });
            
            // Remove oldest backups beyond the limit
            for (int i = maxBackups; i < allBackups.size(); i++) {
                String backupToRemove = allBackups.get(i);
                removeBackup(backupToRemove);
                System.out.println("Removed old backup: " + backupToRemove);
            }
            
        } catch (IOException e) {
            System.err.println("Error cleaning up old backups: " + e.getMessage());
        }
    }

    private String extractTimestamp(String backupName) {
        // Extract timestamp from backup name (format: basename_timestamp)
        int lastUnderscoreIndex = backupName.lastIndexOf('_');
        if (lastUnderscoreIndex != -1 && lastUnderscoreIndex < backupName.length() - 1) {
            return backupName.substring(lastUnderscoreIndex + 1);
        }
        return backupName; // Return full name if no timestamp found
    }

    public void removeBackup(String backupName) {
        try {
            java.nio.file.Path backupPath = java.nio.file.Paths.get(this.backupDirectory, backupName + ".zip");
            java.nio.file.Files.deleteIfExists(backupPath);
        } catch (Exception e) {
            System.err.println("Error removing backup: " + e.getMessage());
        }
    }

    // This method needs to be implemented based on how your application manages the current MediaItem
    private MediaItem getCurrentItemForBackup() {
        // This is a placeholder - implement according to your application's architecture
        // It should return the current MediaItem that needs to be backed up
        return null;
    }

    public void enableAutoBackup(boolean enable) {
        if (enable && !autoBackupEnabled) {
            // Need to provide the item to backup - this would come from your application
            System.out.println("Call startAutoBackup() with the item to begin auto backup.");
        } else if (!enable && autoBackupEnabled) {
            stopAutoBackup();
        }
    }
}