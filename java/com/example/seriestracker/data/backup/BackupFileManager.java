
package com.example.seriestracker.data.backup;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class BackupFileManager {
    private static final String TAG = "BackupFileManager";
    private static final int BUFFER_SIZE = 256 * 1024;
    private static final int FINGERPRINT_HEAD_BYTES = 32 * 1024;

    public enum BackupMediaKind {
        COVER,
        PHOTO,
        VIDEO,
        OTHER
    }

    public static class BackupSession {
        private final Map<String, String> fingerprintToPath = new HashMap<>();
    }

    private static final ThreadLocal<BackupSession> currentSession = new ThreadLocal<>();

    public static void beginBackupSession() {
        currentSession.set(new BackupSession());
    }

    public static void endBackupSession() {
        currentSession.remove();
    }

    private static String findDuplicateByFingerprint(String fingerprint) {
        BackupSession session = currentSession.get();
        if (session == null || fingerprint == null) {
            return null;
        }
        return session.fingerprintToPath.get(fingerprint);
    }

    private static void registerFingerprint(String fingerprint, String relativePath) {
        BackupSession session = currentSession.get();
        if (session != null && fingerprint != null && relativePath != null) {
            session.fingerprintToPath.put(fingerprint, relativePath);
        }
    }

    private static void copyStreams(InputStream inputStream, OutputStream outputStream) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        int bytesRead;
        while ((bytesRead = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, bytesRead);
        }
        outputStream.flush();
    }

    /**
     * Копирует файл из URI во временный каталог резервной копии
     */
    public static String copyFileToBackupDir(Context context, Uri sourceUri, String fileName, String backupDirPath) {
        return copyFileToBackupDir(context, sourceUri, fileName, backupDirPath, true); // по умолчанию всегда копируем
    }

    public static String copyFileToBackupDir(Context context, Uri sourceUri, String fileName, String backupDirPath, boolean alwaysCopy) {
        return copyFileToBackupDir(context, sourceUri, fileName, backupDirPath, BackupMediaKind.OTHER, alwaysCopy);
    }

    public static String copyFileToBackupDir(Context context, Uri sourceUri, String fileName, String backupDirPath,
                                           BackupMediaKind kind, boolean alwaysCopy) {
        try {
            File backupFilesDir = ensureBackupFilesDir(backupDirPath);
            if (backupFilesDir == null) {
                return null;
            }

            String finalFileName = resolveUniqueFileName(fileName, backupFilesDir, alwaysCopy);
            if (BackupMediaOptimizer.shouldCompress(kind, finalFileName)) {
                finalFileName = BackupMediaOptimizer.asJpegFileName(finalFileName);
            }
            File destinationFile = new File(backupFilesDir, finalFileName);

            try (InputStream inputStream = context.getContentResolver().openInputStream(sourceUri)) {
                if (inputStream == null) {
                    Log.e(TAG, "Input stream is null for URI: " + sourceUri);
                    return null;
                }

                if (writeToBackup(inputStream, null, destinationFile, kind, finalFileName)) {
                    return "files/" + finalFileName;
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Error copying file to backup: " + e.getMessage(), e);
        }
        return null;
    }

    /**
     * Копирует файл из внутреннего хранилища приложения во временный каталог резервной копии
     */
    public static String copyInternalFileToBackupDir(Context context, String sourcePath, String fileName, String backupDirPath) {
        return copyInternalFileToBackupDir(context, sourcePath, fileName, backupDirPath, BackupMediaKind.OTHER, false);
    }

    /**
     * Копирует файл из внутреннего хранилища приложения во временный каталог резервной копии
     * @param alwaysCopy если true, то файл будет скопирован даже если с таким именем уже существует файл в той же сессии копирования
     */
    public static String copyInternalFileToBackupDir(Context context, String sourcePath, String fileName,
                                                     String backupDirPath, boolean alwaysCopy) {
        return copyInternalFileToBackupDir(context, sourcePath, fileName, backupDirPath, BackupMediaKind.OTHER, alwaysCopy);
    }

    public static String copyInternalFileToBackupDir(Context context, String sourcePath, String fileName,
                                                     String backupDirPath, BackupMediaKind kind, boolean alwaysCopy) {
        try {
            File sourceFile = new File(sourcePath);
            if (!sourceFile.exists()) {
                Log.e(TAG, "Source file does not exist: " + sourcePath);
                return null;
            }

            File backupFilesDir = ensureBackupFilesDir(backupDirPath);
            if (backupFilesDir == null) {
                return null;
            }

            String requestedName = fileName;
            if (requestedName == null || requestedName.isEmpty()) {
                requestedName = extractOriginalFilenameFromPrefixed(sourceFile.getName());
            }

            String finalFileName = resolveUniqueFileName(requestedName, backupFilesDir, alwaysCopy);
            if (BackupMediaOptimizer.shouldCompress(kind, finalFileName)) {
                finalFileName = BackupMediaOptimizer.asJpegFileName(finalFileName);
            }
            File destinationFile = new File(backupFilesDir, finalFileName);

            if (writeToBackup(null, sourceFile, destinationFile, kind, finalFileName)) {
                return "files/" + finalFileName;
            }
        } catch (IOException e) {
            Log.e(TAG, "Error copying internal file to backup: " + e.getMessage(), e);
        }
        return null;
    }

    private static File ensureBackupFilesDir(String backupDirPath) {
        File backupFilesDir = new File(backupDirPath, "files");
        if (!backupFilesDir.exists() && !backupFilesDir.mkdirs()) {
            Log.e(TAG, "Failed to create backup files directory: " + backupFilesDir.getAbsolutePath());
            return null;
        }
        return backupFilesDir;
    }

    private static String resolveUniqueFileName(String fileName, File backupFilesDir, boolean alwaysCopy) {
        String finalFileName = fileName;
        File destinationFile = new File(backupFilesDir, finalFileName);

        int dotIndex = fileName.lastIndexOf('.');
        String nameWithoutExtension = dotIndex > 0 ? fileName.substring(0, dotIndex) : fileName;
        String extension = dotIndex > 0 ? fileName.substring(dotIndex) : "";

        if (alwaysCopy) {
            return nameWithoutExtension + " (backup_" + System.currentTimeMillis() + "_"
                    + UUID.randomUUID().toString().substring(0, 8) + ")" + extension;
        }

        int counter = 1;
        while (destinationFile.exists()) {
            finalFileName = nameWithoutExtension + " (" + counter + ")" + extension;
            destinationFile = new File(backupFilesDir, finalFileName);
            counter++;
        }
        return finalFileName;
    }

    private static boolean writeToBackup(InputStream inputStream, File sourceFile, File destinationFile,
                                         BackupMediaKind kind, String finalFileName) throws IOException {
        if (BackupMediaOptimizer.shouldCompress(kind, finalFileName)) {
            int maxDimension = BackupMediaOptimizer.maxDimensionFor(kind);
            if (sourceFile != null) {
                return BackupMediaOptimizer.writeOptimizedImage(
                        sourceFile, destinationFile, maxDimension, BackupMediaOptimizer.JPEG_QUALITY);
            }
            if (inputStream != null) {
                return BackupMediaOptimizer.writeOptimizedImage(
                        inputStream, destinationFile, maxDimension, BackupMediaOptimizer.JPEG_QUALITY);
            }
        }

        if (sourceFile != null) {
            BackupMediaOptimizer.fastCopy(sourceFile, destinationFile);
            return destinationFile.exists() && destinationFile.length() > 0;
        }

        if (inputStream != null) {
            try (OutputStream outputStream = new FileOutputStream(destinationFile)) {
                copyStreams(inputStream, outputStream);
            }
            return destinationFile.exists() && destinationFile.length() > 0;
        }
        return false;
    }

    /**
     * Восстанавливает файл из резервной копии во внутреннее хранилище приложения
     */
    public static String restoreFileFromBackup(Context context, String relativeFilePath, String backupDirPath) {
        return restoreFileFromBackup(context, relativeFilePath, backupDirPath, "media");
    }

    public static String restoreFileFromBackup(Context context, String relativeFilePath, String backupDirPath, String targetSubdir) {
        try {
            File sourceFile = new File(backupDirPath, relativeFilePath);
            if (!sourceFile.exists()) {
                Log.e(TAG, "Source file does not exist in backup: " + sourceFile.getAbsolutePath());
                return null;
            }

            File mediaDir = new File(context.getFilesDir(), targetSubdir);
            if (!mediaDir.exists()) {
                if (!mediaDir.mkdirs()) {
                    Log.e(TAG, "Failed to create media directory: " + mediaDir.getAbsolutePath());
                    return null;
                }
            }

            // Извлекаем оригинальное имя файла из относительного пути
            String fileName = extractOriginalFileName(relativeFilePath);

            // Сохраняем с оригинальным именем (без UUID префикса)
            File destinationFile = new File(mediaDir, fileName);

            // Если файл с таким именем уже существует, добавляем номер
            int counter = 1;
            String nameWithoutExtension = "";
            String extension = "";
            int dotIndex = fileName.lastIndexOf('.');

            if (dotIndex > 0) {
                nameWithoutExtension = fileName.substring(0, dotIndex);
                extension = fileName.substring(dotIndex);
            } else {
                nameWithoutExtension = fileName;
                extension = "";
            }

            // Проверяем и создаем уникальное имя, если файл уже существует
            while (destinationFile.exists()) {
                fileName = nameWithoutExtension + " (" + counter + ")" + extension;
                destinationFile = new File(mediaDir, fileName);
                counter++;
            }

            // Копируем файл
            BackupMediaOptimizer.fastCopy(sourceFile, destinationFile);

            Log.d(TAG, "Successfully restored file from backup: " + destinationFile.getAbsolutePath());
            return com.example.seriestracker.utils.MediaStorageHelper.toStoredUri(destinationFile);
        } catch (IOException e) {
            Log.e(TAG, "Error restoring file from backup: " + e.getMessage(), e);
            return null;
        }
    }
    /**
     * Извлекает оригинальное имя файла из пути в формате "files/filename.ext"
     */
    private static String extractOriginalFileName(String relativeFilePath) {
        if (relativeFilePath.startsWith("files/")) {
            String fileName = relativeFilePath.substring(6); // Убираем "files/"

            // Убираем номер в скобках, если он есть (например "photo (1).jpg")
            // Оставляем только оригинальное имя до первого пробела и скобки
            int bracketIndex = fileName.indexOf(" (");
            if (bracketIndex > 0) {
                // Проверяем, есть ли закрывающая скобка после номера
                int closeBracketIndex = fileName.indexOf(")", bracketIndex);
                if (closeBracketIndex > bracketIndex) {
                    // Извлекаем расширение
                    String extension = "";
                    int dotIndex = fileName.lastIndexOf('.');
                    if (dotIndex > closeBracketIndex) {
                        extension = fileName.substring(dotIndex);
                        return fileName.substring(0, bracketIndex) + extension;
                    } else {
                        return fileName.substring(0, bracketIndex);
                    }
                }
            }

            // Если нет скобок с номером, возвращаем как есть
            return fileName;
        }
        return relativeFilePath;
    }
    /**
     * Извлекает имя файла из пути в формате "files/filename.ext", возвращая только имя файла
     */
    private static String extractFileName(String relativeFilePath) {
        if (relativeFilePath.startsWith("files/")) {
            return relativeFilePath.substring(6); // Убираем "files/"
        }
        return relativeFilePath;
    }

    /**
     * Извлекает оригинальное имя файла из UUID-префиксированного имени файла
     */
    public static String extractOriginalFilenameFromPrefixed(String prefixedFilename) {
        if (prefixedFilename == null) {
            return null;
        }

        int underscoreIndex = prefixedFilename.indexOf('_');
        if (underscoreIndex > 0 && underscoreIndex < prefixedFilename.length() - 1) {
            // Возвращаем часть после первого подчеркивания (это оригинальное имя файла)
            return prefixedFilename.substring(underscoreIndex + 1);
        }
        // Если нет подчеркивания, возвращаем оригинальное имя
        return prefixedFilename;
    }

    public static String copyUriToBackupWithDedup(Context context, Uri sourceUri, String fileName, String backupDirPath) {
        return copyUriToBackupWithDedup(context, sourceUri, fileName, backupDirPath, BackupMediaKind.OTHER);
    }

    public static String copyUriToBackupWithDedup(Context context, Uri sourceUri, String fileName,
                                                  String backupDirPath, BackupMediaKind kind) {
        try {
            String fingerprint = computeUriFingerprint(context, sourceUri);
            String existingRelativePath = findDuplicateByFingerprint(fingerprint);
            if (existingRelativePath != null) {
                return existingRelativePath;
            }
            String copiedPath = copyFileToBackupDir(context, sourceUri, fileName, backupDirPath, kind, false);
            if (copiedPath != null) {
                registerFingerprint(fingerprint, copiedPath);
            }
            return copiedPath;
        } catch (IOException e) {
            Log.w(TAG, "Failed to compute URI fingerprint", e);
            return copyFileToBackupDir(context, sourceUri, fileName, backupDirPath, kind, false);
        }
    }

    public static String copyInternalFileToBackupWithDedup(Context context, String sourcePath, String fileName,
                                                           String backupDirPath) {
        return copyInternalFileToBackupWithDedup(context, sourcePath, fileName, backupDirPath, BackupMediaKind.OTHER);
    }

    public static String copyInternalFileToBackupWithDedup(Context context, String sourcePath, String fileName,
                                                           String backupDirPath, BackupMediaKind kind) {
        File sourceFile = new File(sourcePath);
        if (!sourceFile.exists()) {
            Log.e(TAG, "Source file does not exist: " + sourcePath);
            return null;
        }

        try {
            String fingerprint = computeFileFingerprint(sourceFile);
            String existingRelativePath = findDuplicateByFingerprint(fingerprint);
            if (existingRelativePath != null) {
                return existingRelativePath;
            }
            String copiedPath = copyInternalFileToBackupDir(context, sourcePath, fileName, backupDirPath, kind, false);
            if (copiedPath != null) {
                registerFingerprint(fingerprint, copiedPath);
            }
            return copiedPath;
        } catch (IOException e) {
            Log.w(TAG, "Failed to compute file fingerprint", e);
            return copyInternalFileToBackupDir(context, sourcePath, fileName, backupDirPath, kind, false);
        }
    }

    private static String computeFileFingerprint(File file) throws IOException {
        return file.length() + ":" + computeFastCrc32(file);
    }

    private static String computeUriFingerprint(Context context, Uri uri) throws IOException {
        long size = getUriFileSize(context, uri);
        try (InputStream inputStream = context.getContentResolver().openInputStream(uri)) {
            if (inputStream == null) {
                throw new IOException("Cannot open URI: " + uri);
            }
            return size + ":" + computeFastCrc32(inputStream);
        }
    }

    private static long computeFastCrc32(File file) throws IOException {
        try (FileInputStream inputStream = new FileInputStream(file)) {
            return computeFastCrc32(inputStream, file.length());
        }
    }

    private static long computeFastCrc32(InputStream inputStream) throws IOException {
        return computeFastCrc32(inputStream, -1);
    }

    private static long computeFastCrc32(InputStream inputStream, long knownSize) throws IOException {
        CRC32 crc32 = new CRC32();
        byte[] buffer = new byte[8192];
        int totalRead = 0;
        int read;
        while (totalRead < FINGERPRINT_HEAD_BYTES && (read = inputStream.read(buffer)) != -1) {
            crc32.update(buffer, 0, read);
            totalRead += read;
        }
        if (knownSize > 0) {
            crc32.update(Long.toString(knownSize).getBytes());
        }
        return crc32.getValue();
    }


    /**
     * Архивирует директорию резервной копии в ZIP файл
     */
    public static File createZipBackup(String sourceDirPath, String zipFilePath) {
        try {
            File sourceDir = new File(sourceDirPath);
            File zipFile = new File(zipFilePath);

            if (!zipFile.getParentFile().exists()) {
                zipFile.getParentFile().mkdirs();
            }

            try (ZipOutputStream zos = new ZipOutputStream(
                    new BufferedOutputStream(new FileOutputStream(zipFile), BUFFER_SIZE))) {
                zos.setLevel(Deflater.BEST_SPEED);
                addFileToZip(zos, sourceDir, sourceDir.getAbsolutePath());
            }

            return zipFile;
        } catch (Exception e) {
            Log.e("BackupFileManager", "Error creating ZIP backup: " + e.getMessage(), e);
            return null;
        }
    }

    /**
     * Добавляет файлы в ZIP архив рекурсивно
     */
    private static void addFileToZip(ZipOutputStream zos, File file, String basePath) throws IOException {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    addFileToZip(zos, child, basePath);
                }
            }
        } else {
            String entryName = file.getAbsolutePath().substring(basePath.length() + 1);
            if (shouldStoreWithoutCompression(entryName)) {
                addStoredFileToZip(zos, file, entryName);
            } else {
                ZipEntry entry = new ZipEntry(entryName);
                zos.putNextEntry(entry);
                try (FileInputStream fis = new FileInputStream(file)) {
                    copyStreams(fis, zos);
                }
                zos.closeEntry();
            }
        }
    }

    private static boolean shouldStoreWithoutCompression(String entryName) {
        String lower = entryName.toLowerCase(java.util.Locale.ROOT);
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png")
                || lower.endsWith(".webp") || lower.endsWith(".mp4") || lower.endsWith(".mkv")
                || lower.endsWith(".mov") || lower.endsWith(".avi") || lower.endsWith(".3gp")
                || lower.endsWith(".webm") || lower.endsWith(".wmv");
    }

    private static void addStoredFileToZip(ZipOutputStream zos, File file, String entryName) throws IOException {
        long size = file.length();
        CRC32 crc32 = new CRC32();
        byte[] buffer = new byte[BUFFER_SIZE];
        try (FileInputStream fis = new FileInputStream(file)) {
            int read;
            while ((read = fis.read(buffer)) != -1) {
                crc32.update(buffer, 0, read);
            }
        }

        ZipEntry entry = new ZipEntry(entryName);
        entry.setMethod(ZipEntry.STORED);
        entry.setSize(size);
        entry.setCompressedSize(size);
        entry.setCrc(crc32.getValue());
        zos.putNextEntry(entry);

        try (FileInputStream fis = new FileInputStream(file)) {
            copyStreams(fis, zos);
        }
        zos.closeEntry();
    }

    /**
     * Извлекает ZIP архив в директорию
     */
    /**
     * Извлекает ZIP архив в директорию
     */
    public static boolean extractZipBackup(String zipFilePath, String extractToDir) {
        try {
            File extractDir = new File(extractToDir);
            if (!extractDir.exists() && !extractDir.mkdirs()) {
                Log.e("BackupFileManager", "Failed to create extraction directory: " + extractDir.getAbsolutePath());
                return false;
            }

            try (ZipInputStream zis = new ZipInputStream(
                    new BufferedInputStream(new FileInputStream(zipFilePath), BUFFER_SIZE))) {

                ZipEntry entry;
                byte[] buffer = new byte[BUFFER_SIZE];
                String canonicalExtractDir = extractDir.getCanonicalPath();

                while ((entry = zis.getNextEntry()) != null) {
                    File file = new File(extractDir, entry.getName());
                    String canonicalDest = file.getCanonicalPath();
                    if (!canonicalDest.startsWith(canonicalExtractDir + File.separator)
                            && !canonicalDest.equals(canonicalExtractDir)) {
                        Log.w(TAG, "Skipping unsafe zip entry: " + entry.getName());
                        zis.closeEntry();
                        continue;
                    }

                    if (entry.isDirectory()) {
                        if (!file.exists() && !file.mkdirs()) {
                            Log.e("BackupFileManager", "Failed to create directory: " + file.getAbsolutePath());
                        }
                    } else {
                        File parent = file.getParentFile();
                        if (parent != null && !parent.exists() && !parent.mkdirs()) {
                            Log.e("BackupFileManager", "Failed to create parent directory: " + parent.getAbsolutePath());
                        }

                        try (FileOutputStream fos = new FileOutputStream(file)) {
                            copyStreams(zis, fos);
                        }
                    }
                    zis.closeEntry();
                }
            }

            return true;
        } catch (Exception e) {
            Log.e("BackupFileManager", "Error extracting ZIP backup: " + e.getMessage(), e);
            return false;
        }
    }

    /**
     * Проверяет, совпадает ли содержимое файла по URI с содержимым указанного файла
     */
    public static boolean isSameFileContent(Context context, Uri uri, File file) {
        try (InputStream uriStream = context.getContentResolver().openInputStream(uri);
             java.io.FileInputStream fileStream = new java.io.FileInputStream(file)) {

            if (uriStream == null) {
                return false;
            }

            // Сравниваем размеры файлов
            if (getFileSize(context, uri) != file.length()) {
                return false;
            }

            // Сравниваем содержимое побайтово
            byte[] uriBuffer = new byte[4096];
            byte[] fileBuffer = new byte[4096];

            int uriBytesRead, fileBytesRead;
            while ((uriBytesRead = uriStream.read(uriBuffer)) != -1) {
                fileBytesRead = fileStream.read(fileBuffer);

                if (uriBytesRead != fileBytesRead) {
                    return false;
                }

                // Сравниваем буферы
                for (int i = 0; i < uriBytesRead; i++) {
                    if (uriBuffer[i] != fileBuffer[i]) {
                        return false;
                    }
                }
            }

            return true;
        } catch (IOException e) {
            Log.w(TAG, "Error comparing file content: " + e.getMessage());
            // Если не можем сравнить, предполагаем, что файлы разные
            return false;
        }
    }

    /**
     * Проверяет, совпадает ли содержимое двух файлов
     */
    public static boolean areFilesContentEqual(File file1, File file2) {
        try (java.io.FileInputStream stream1 = new java.io.FileInputStream(file1);
             java.io.FileInputStream stream2 = new java.io.FileInputStream(file2)) {

            // Сравниваем размеры файлов
            if (file1.length() != file2.length()) {
                return false;
            }

            // Сравниваем содержимое побайтово
            byte[] buffer1 = new byte[4096];
            byte[] buffer2 = new byte[4096];

            int bytesRead1, bytesRead2;
            while ((bytesRead1 = stream1.read(buffer1)) != -1) {
                bytesRead2 = stream2.read(buffer2);

                if (bytesRead1 != bytesRead2) {
                    return false;
                }

                // Сравниваем буферы
                for (int i = 0; i < bytesRead1; i++) {
                    if (buffer1[i] != buffer2[i]) {
                        return false;
                    }
                }
            }

            return true;
        } catch (IOException e) {
            Log.w(TAG, "Error comparing file content: " + e.getMessage());
            // Если не можем сравнить, предполагаем, что файлы разные
            return false;
        }
    }

    /**
     * Получает размер файла по URI
     */
    private static long getUriFileSize(Context context, Uri uri) {
        if ("file".equals(uri.getScheme())) {
            File file = new File(uri.getPath());
            if (file.exists()) {
                return file.length();
            }
        }

        try (Cursor cursor = context.getContentResolver().query(
                uri, new String[]{OpenableColumns.SIZE}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                    return cursor.getLong(sizeIndex);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Error getting URI file size: " + e.getMessage());
        }
        return -1;
    }

    private static long getFileSize(Context context, Uri uri) {
        return getUriFileSize(context, uri);
    }
}