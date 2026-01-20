package com.example.seriestracker.data.backup;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

public class BackupFileManager {
    private static final String TAG = "BackupFileManager";

    /**
     * Копирует файл из URI во временный каталог резервной копии
     */
    public static String copyFileToBackupDir(Context context, Uri sourceUri, String fileName, String backupDirPath) {
        try {
            // Создаем подкаталог для файлов в директории резервной копии
            File backupFilesDir = new File(backupDirPath, "files");
            if (!backupFilesDir.exists()) {
                if (!backupFilesDir.mkdirs()) {
                    Log.e(TAG, "Failed to create backup files directory: " + backupFilesDir.getAbsolutePath());
                    return null;
                }
            }

            // Создаем уникальное имя файла, чтобы избежать конфликта имен
            String uniqueFileName = UUID.randomUUID().toString() + "_" + fileName;
            File destinationFile = new File(backupFilesDir, uniqueFileName);

            // Копируем файл
            try (InputStream inputStream = context.getContentResolver().openInputStream(sourceUri);
                 OutputStream outputStream = new FileOutputStream(destinationFile)) {

                if (inputStream == null) {
                    Log.e(TAG, "Input stream is null for URI: " + sourceUri);
                    return null;
                }

                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
                outputStream.flush();

                Log.d(TAG, "Successfully copied file to backup: " + destinationFile.getAbsolutePath());
                return "files/" + uniqueFileName; // Возвращаем относительный путь
            }
        } catch (IOException e) {
            Log.e(TAG, "Error copying file to backup: " + e.getMessage(), e);
            return null;
        }
    }

    /**
     * Копирует файл из внутреннего хранилища приложения во временный каталог резервной копии
     */
    public static String copyInternalFileToBackupDir(Context context, String sourcePath, String fileName, String backupDirPath) {
        try {
            File sourceFile = new File(sourcePath);
            if (!sourceFile.exists()) {
                Log.e(TAG, "Source file does not exist: " + sourcePath);
                return null;
            }

            // Создаем подкаталог для файлов в директории резервной копии
            File backupFilesDir = new File(backupDirPath, "files");
            if (!backupFilesDir.exists()) {
                if (!backupFilesDir.mkdirs()) {
                    Log.e(TAG, "Failed to create backup files directory: " + backupFilesDir.getAbsolutePath());
                    return null;
                }
            }

            // Создаем уникальное имя файла, чтобы избежать конфликта имен
            String uniqueFileName = UUID.randomUUID().toString() + "_" + fileName;
            File destinationFile = new File(backupFilesDir, uniqueFileName);

            // Копируем файл
            try (FileInputStream inputStream = new FileInputStream(sourceFile);
                 FileOutputStream outputStream = new FileOutputStream(destinationFile)) {

                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
                outputStream.flush();

                Log.d(TAG, "Successfully copied internal file to backup: " + destinationFile.getAbsolutePath());
                return "files/" + uniqueFileName; // Возвращаем относительный путь
            }
        } catch (IOException e) {
            Log.e(TAG, "Error copying internal file to backup: " + e.getMessage(), e);
            return null;
        }
    }

    /**
     * Восстанавливает файл из резервной копии во внутреннее хранилище приложения
     */
    public static String restoreFileFromBackup(Context context, String relativeFilePath, String backupDirPath) {
        try {
            File sourceFile = new File(backupDirPath, relativeFilePath);
            if (!sourceFile.exists()) {
                Log.e(TAG, "Source file does not exist in backup: " + sourceFile.getAbsolutePath());
                return null;
            }

            // Создаем подкаталог для медиафайлов во внутреннем хранилище приложения
            File mediaDir = new File(context.getFilesDir(), "media");
            if (!mediaDir.exists()) {
                if (!mediaDir.mkdirs()) {
                    Log.e(TAG, "Failed to create media directory: " + mediaDir.getAbsolutePath());
                    return null;
                }
            }

            // Извлекаем оригинальное имя файла из относительного пути
            String originalFileName = extractOriginalFileName(relativeFilePath);

            // Генерируем уникальное имя файла
            String uniqueFileName = UUID.randomUUID().toString() + "_" + originalFileName;
            File destinationFile = new File(mediaDir, uniqueFileName);

            // Копируем файл
            try (FileInputStream inputStream = new FileInputStream(sourceFile);
                 FileOutputStream outputStream = new FileOutputStream(destinationFile)) {

                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
                outputStream.flush();

                Log.d(TAG, "Successfully restored file from backup: " + destinationFile.getAbsolutePath());
                return destinationFile.getAbsolutePath();
            }
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
            int lastUnderscore = fileName.indexOf('_');
            if (lastUnderscore > 0 && lastUnderscore < fileName.length() - 1) {
                // Восстанавливаем оригинальное имя файла, пропуская UUID префикс
                return fileName.substring(lastUnderscore + 1);
            }
            return fileName;
        }
        return relativeFilePath;
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

            try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(
                    new java.io.FileOutputStream(zipFile))) {

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
    private static void addFileToZip(java.util.zip.ZipOutputStream zos, File file, String basePath) throws Exception {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    addFileToZip(zos, child, basePath);
                }
            }
        } else {
            String entryName = file.getAbsolutePath().substring(basePath.length() + 1);
            java.util.zip.ZipEntry entry = new java.util.zip.ZipEntry(entryName);
            zos.putNextEntry(entry);

            try (java.io.FileInputStream fis = new java.io.FileInputStream(file)) {
                byte[] buffer = new byte[4096];
                int length;
                while ((length = fis.read(buffer)) > 0) {
                    zos.write(buffer, 0, length);
                }
            }
            zos.closeEntry();
        }
    }

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

            try (java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(
                    new java.io.FileInputStream(zipFilePath))) {

                java.util.zip.ZipEntry entry;
                byte[] buffer = new byte[4096];

                while ((entry = zis.getNextEntry()) != null) {
                    File file = new File(extractDir, entry.getName());

                    if (entry.isDirectory()) {
                        if (!file.exists() && !file.mkdirs()) {
                            Log.e("BackupFileManager", "Failed to create directory: " + file.getAbsolutePath());
                        }
                    } else {
                        File parent = file.getParentFile();
                        if (parent != null && !parent.exists() && !parent.mkdirs()) {
                            Log.e("BackupFileManager", "Failed to create parent directory: " + parent.getAbsolutePath());
                        }

                        try (java.io.FileOutputStream fos = new java.io.FileOutputStream(file)) {
                            int length;
                            while ((length = zis.read(buffer)) > 0) {
                                fos.write(buffer, 0, length);
                            }
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
}