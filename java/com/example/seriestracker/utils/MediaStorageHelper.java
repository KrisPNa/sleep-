package com.example.seriestracker.utils;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public final class MediaStorageHelper {

    private static final String MEDIA_SUBDIR = "media";
    private static final String COVERS_SUBDIR = "covers";

    private MediaStorageHelper() {
    }

    public static String copyCoverToInternalStorage(Context context, Uri sourceUri, String preferredFileName) {
        return copyUriToInternalStorage(context, sourceUri, preferredFileName, COVERS_SUBDIR);
    }

    public static String copyMediaToInternalStorage(Context context, Uri sourceUri, String preferredFileName) {
        return copyUriToInternalStorage(context, sourceUri, preferredFileName, MEDIA_SUBDIR);
    }

    public static String copyUriToInternalStorage(Context context, Uri sourceUri, String preferredFileName, String subdirectory) {
        if (context == null || sourceUri == null) {
            return null;
        }

        try {
            File targetDir = new File(context.getFilesDir(), subdirectory);
            if (!targetDir.exists() && !targetDir.mkdirs()) {
                return null;
            }

            String fileName = preferredFileName;
            if (fileName == null || fileName.isEmpty()) {
                fileName = "file_" + System.currentTimeMillis() + ".jpg";
            }

            String uniqueFileName = generateUniqueFileName(fileName, targetDir);
            File destinationFile = new File(targetDir, uniqueFileName);

            try (InputStream inputStream = context.getContentResolver().openInputStream(sourceUri);
                 FileOutputStream outputStream = new FileOutputStream(destinationFile)) {
                if (inputStream == null) {
                    return null;
                }

                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
                outputStream.flush();
            }

            return toStoredUri(destinationFile);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static Uri resolveLoadUri(String storedValue) {
        if (storedValue == null || storedValue.isEmpty()) {
            return null;
        }
        if (storedValue.startsWith("content://") || storedValue.startsWith("file://")) {
            return Uri.parse(storedValue);
        }
        return Uri.fromFile(new File(storedValue));
    }

    public static File getInternalFile(Context context, String storedValue) {
        if (storedValue == null || storedValue.isEmpty()) {
            return null;
        }

        if (storedValue.startsWith("file://")) {
            String path = Uri.parse(storedValue).getPath();
            return path != null ? new File(path) : null;
        }

        if (storedValue.startsWith(context.getFilesDir().getAbsolutePath())) {
            return new File(storedValue);
        }

        File directFile = new File(storedValue);
        if (directFile.isAbsolute() && directFile.exists()) {
            return directFile;
        }

        return null;
    }

    public static boolean isAppInternalFile(Context context, String storedValue) {
        File file = getInternalFile(context, storedValue);
        if (file == null) {
            return false;
        }
        String filesDir = context.getFilesDir().getAbsolutePath();
        return file.getAbsolutePath().startsWith(filesDir);
    }

    public static String getInternalFilePath(Context context, String storedValue) {
        File file = getInternalFile(context, storedValue);
        return file != null ? file.getAbsolutePath() : null;
    }

    public static String toStoredUri(File file) {
        return Uri.fromFile(file).toString();
    }

    public static String normalizeStoredValue(String storedValue) {
        if (storedValue == null || storedValue.isEmpty()) {
            return storedValue;
        }
        if (storedValue.startsWith("content://") || storedValue.startsWith("file://")) {
            return storedValue;
        }
        return toStoredUri(new File(storedValue));
    }

    public static String getDisplayName(Context context, Uri uri) {
        String fileName = null;

        if ("content".equals(uri.getScheme())) {
            try (Cursor cursor = context.getContentResolver()
                    .query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (nameIndex != -1) {
                        fileName = cursor.getString(nameIndex);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        if (fileName == null) {
            fileName = uri.getLastPathSegment();
        }

        if (fileName == null || fileName.isEmpty()) {
            fileName = "file_" + System.currentTimeMillis();
        }

        return fileName;
    }

    private static String generateUniqueFileName(String originalFileName, File directory) {
        String nameWithoutExtension = originalFileName;
        String extension = "";

        int dotIndex = originalFileName.lastIndexOf('.');
        if (dotIndex > 0) {
            nameWithoutExtension = originalFileName.substring(0, dotIndex);
            extension = originalFileName.substring(dotIndex);
        }

        String candidate = originalFileName;
        int counter = 1;
        while (new File(directory, candidate).exists()) {
            candidate = nameWithoutExtension + "_" + counter + extension;
            counter++;
        }
        return candidate;
    }
}
