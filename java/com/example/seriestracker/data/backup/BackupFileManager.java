
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

            // Используем оригинальное имя файла
            String finalFileName = fileName;
            File destinationFile = new File(backupFilesDir, finalFileName);

            // Если файл с таким именем уже существует, добавляем номер в конце
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
                finalFileName = nameWithoutExtension + " (" + counter + ")" + extension;
                destinationFile = new File(backupFilesDir, finalFileName);
                counter++;
            }

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
                return "files/" + finalFileName; // Возвращаем относительный путь
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

            // Если имя файла передано как пустое или null, извлекаем оригинальное имя из пути к исходному файлу
            String finalFileName;
            if (fileName == null || fileName.isEmpty()) {
                // Извлекаем оригинальное имя файла из UUID-префиксированного имени в пути
                String sourceFileName = sourceFile.getName();
                finalFileName = extractOriginalFilenameFromPrefixed(sourceFileName);
            } else {
                finalFileName = fileName;
            }

            File destinationFile = new File(backupFilesDir, finalFileName);

            // Если файл с таким именем уже существует, добавляем номер в конце
            int counter = 1;
            String nameWithoutExtension = "";
            String extension = "";
            int dotIndex = finalFileName.lastIndexOf('.');

            if (dotIndex > 0) {
                nameWithoutExtension = finalFileName.substring(0, dotIndex);
                extension = finalFileName.substring(dotIndex);
            } else {
                nameWithoutExtension = finalFileName;
                extension = "";
            }

            // Проверяем и создаем уникальное имя, если файл уже существует
            while (destinationFile.exists()) {
                finalFileName = nameWithoutExtension + " (" + counter + ")" + extension;
                destinationFile = new File(backupFilesDir, finalFileName);
                counter++;
            }

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
                return "files/" + finalFileName; // Возвращаем относительный путь
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

    /**
     * Проверяет, совпадает ли содержимое файла по URI с содержимым указанного файла
     */
    private static boolean isSameFileContent(Context context, Uri uri, File file) {
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
    private static boolean areFilesContentEqual(Context context, File file1, File file2) {
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
    private static long getFileSize(Context context, Uri uri) {
        try (InputStream inputStream = context.getContentResolver().openInputStream(uri)) {
            if (inputStream != null) {
                return inputStream.available();
            }
        } catch (IOException e) {
            Log.w(TAG, "Error getting file size: " + e.getMessage());
        }
        return -1;
    }
}