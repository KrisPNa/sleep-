import java.io.*;
import java.nio.file.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import java.util.List;
import java.util.ArrayList;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

public class BackupFileManager {
    private String backupDirectory;
    private Gson gson;
    
    public BackupFileManager(String backupDirectory) {
        this.backupDirectory = backupDirectory;
        this.gson = new Gson();
        // Create backup directory if it doesn't exist
        try {
            Files.createDirectories(Paths.get(backupDirectory));
        } catch (IOException e) {
            System.err.println("Error creating backup directory: " + e.getMessage());
        }
    }
    
    public void createBackup(MediaItem item, String backupName) throws IOException {
        Path backupDirPath = Paths.get(backupDirectory);
        Path zipFilePath = backupDirPath.resolve(backupName + ".zip");
        
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFilePath.toFile()))) {
            
            // Serialize the MediaItem to JSON
            String jsonContent = gson.toJson(item);
            
            // Add JSON metadata to ZIP
            ZipEntry jsonEntry = new ZipEntry("metadata.json");
            zos.putNextEntry(jsonEntry);
            zos.write(jsonContent.getBytes("UTF-8"));
            zos.closeEntry();
            
            // Process and add all media files to the ZIP
            addMediaFilesToZip(zos, item);
        }
    }
    
    private void addMediaFilesToZip(ZipOutputStream zos, MediaItem item) throws IOException {
        // Add cover image if it exists
        if (item.getCoverImage() != null && !item.getCoverImage().isEmpty()) {
            Path coverPath = Paths.get(item.getCoverImage());
            if (Files.exists(coverPath)) {
                ZipEntry coverEntry = new ZipEntry("media/" + coverPath.getFileName().toString());
                zos.putNextEntry(coverEntry);
                Files.copy(coverPath, zos);
                zos.closeEntry();
                
                // Update the cover path to be relative for restoration
                item.setCoverImage("media/" + coverPath.getFileName().toString());
            }
        }
        
        // Add video files if they exist
        if (item.getVideoFiles() != null) {
            for (int i = 0; i < item.getVideoFiles().size(); i++) {
                String videoPathStr = item.getVideoFiles().get(i);
                if (videoPathStr != null && !videoPathStr.isEmpty()) {
                    Path videoPath = Paths.get(videoPathStr);
                    if (Files.exists(videoPath)) {
                        ZipEntry videoEntry = new ZipEntry("media/" + videoPath.getFileName().toString());
                        zos.putNextEntry(videoEntry);
                        Files.copy(videoPath, zos);
                        zos.closeEntry();
                        
                        // Update the video path to be relative for restoration
                        item.getVideoFiles().set(i, "media/" + videoPath.getFileName().toString());
                    }
                }
            }
        }
        
        // Add photo files if they exist
        if (item.getPhotoFiles() != null) {
            for (int i = 0; i < item.getPhotoFiles().size(); i++) {
                String photoPathStr = item.getPhotoFiles().get(i);
                if (photoPathStr != null && !photoPathStr.isEmpty()) {
                    Path photoPath = Paths.get(photoPathStr);
                    if (Files.exists(photoPath)) {
                        ZipEntry photoEntry = new ZipEntry("media/" + photoPath.getFileName().toString());
                        zos.putNextEntry(photoEntry);
                        Files.copy(photoPath, zos);
                        zos.closeEntry();
                        
                        // Update the photo path to be relative for restoration
                        item.getPhotoFiles().set(i, "media/" + photoPath.getFileName().toString());
                    }
                }
            }
        }
    }
    
    public MediaItem restoreFromBackup(String backupName) throws IOException {
        Path backupDirPath = Paths.get(backupDirectory);
        Path zipFilePath = backupDirPath.resolve(backupName + ".zip");
        
        if (!Files.exists(zipFilePath)) {
            throw new FileNotFoundException("Backup file does not exist: " + zipFilePath);
        }
        
        Path tempDir = Files.createTempDirectory("restore_");
        try {
            // Extract ZIP to temporary directory
            extractZip(zipFilePath, tempDir);
            
            // Read the metadata
            Path metadataPath = tempDir.resolve("metadata.json");
            if (!Files.exists(metadataPath)) {
                throw new IOException("Metadata file not found in backup");
            }
            
            String jsonContent = new String(Files.readAllBytes(metadataPath));
            MediaItem restoredItem = gson.fromJson(jsonContent, MediaItem.class);
            
            // Fix paths to point to extracted media files
            fixRestoredPaths(restoredItem, tempDir);
            
            return restoredItem;
        } finally {
            // Clean up temporary directory
            deleteRecursively(tempDir);
        }
    }
    
    private void extractZip(Path zipFilePath, Path destDir) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFilePath.toFile()))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path entryPath = destDir.resolve(entry.getName()).normalize();
                
                // Security check to prevent path traversal
                if (!entryPath.startsWith(destDir)) {
                    throw new IOException("Entry is outside of the target dir: " + entry.getName());
                }
                
                if (entry.isDirectory()) {
                    Files.createDirectories(entryPath);
                } else {
                    Path parent = entryPath.getParent();
                    if (parent != null) {
                        Files.createDirectories(parent);
                    }
                    try (FileOutputStream fos = new FileOutputStream(entryPath.toFile())) {
                        byte[] buffer = new byte[1024];
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                    }
                }
                zis.closeEntry();
            }
        }
    }
    
    private void fixRestoredPaths(MediaItem item, Path tempDir) throws IOException {
        // Fix cover image path
        if (item.getCoverImage() != null && !item.getCoverImage().isEmpty()) {
            Path relativeCoverPath = Paths.get(item.getCoverImage());
            Path extractedCoverPath = tempDir.resolve(relativeCoverPath).normalize();
            
            if (Files.exists(extractedCoverPath)) {
                // Copy to final destination and update path
                String fileName = extractedCoverPath.getFileName().toString();
                Path finalPath = Paths.get(item.getCoverImage()).getFileName(); // Just filename from original
                Path targetPath = getFinalMediaPath(fileName);
                
                Files.createDirectories(targetPath.getParent());
                Files.copy(extractedCoverPath, targetPath, StandardCopyOption.REPLACE_EXISTING);
                item.setCoverImage(targetPath.toString());
            }
        }
        
        // Fix video file paths
        if (item.getVideoFiles() != null) {
            for (int i = 0; i < item.getVideoFiles().size(); i++) {
                String relativeVideoPath = item.getVideoFiles().get(i);
                if (relativeVideoPath != null && !relativeVideoPath.isEmpty()) {
                    Path extractedVideoPath = tempDir.resolve(Paths.get(relativeVideoPath)).normalize();
                    
                    if (Files.exists(extractedVideoPath)) {
                        String fileName = extractedVideoPath.getFileName().toString();
                        Path targetPath = getFinalMediaPath(fileName);
                        
                        Files.createDirectories(targetPath.getParent());
                        Files.copy(extractedVideoPath, targetPath, StandardCopyOption.REPLACE_EXISTING);
                        item.getVideoFiles().set(i, targetPath.toString());
                    }
                }
            }
        }
        
        // Fix photo file paths
        if (item.getPhotoFiles() != null) {
            for (int i = 0; i < item.getPhotoFiles().size(); i++) {
                String relativePhotoPath = item.getPhotoFiles().get(i);
                if (relativePhotoPath != null && !relativePhotoPath.isEmpty()) {
                    Path extractedPhotoPath = tempDir.resolve(Paths.get(relativePhotoPath)).normalize();
                    
                    if (Files.exists(extractedPhotoPath)) {
                        String fileName = extractedPhotoPath.getFileName().toString();
                        Path targetPath = getFinalMediaPath(fileName);
                        
                        Files.createDirectories(targetPath.getParent());
                        Files.copy(extractedPhotoPath, targetPath, StandardCopyOption.REPLACE_EXISTING);
                        item.getPhotoFiles().set(i, targetPath.toString());
                    }
                }
            }
        }
    }
    
    private Path getFinalMediaPath(String fileName) {
        // Determine where to place the restored media file
        // This could be configurable based on your application structure
        return Paths.get(System.getProperty("user.home"), "RestoredMedia", fileName);
    }
    
    private void deleteRecursively(Path path) throws IOException {
        if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            try (DirectoryStream<Path> entries = Files.newDirectoryStream(path)) {
                for (Path entry : entries) {
                    deleteRecursively(entry);
                }
            }
        }
        Files.deleteIfExists(path);
    }
    
    public List<String> listAvailableBackups() throws IOException {
        List<String> backups = new ArrayList<>();
        Path backupDirPath = Paths.get(backupDirectory);
        
        if (!Files.exists(backupDirPath)) {
            return backups;
        }
        
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(backupDirPath, "*.zip")) {
            for (Path path : stream) {
                String fileName = path.getFileName().toString();
                backups.add(fileName.substring(0, fileName.length() - 4)); // Remove .zip extension
            }
        }
        
        return backups;
    }
}