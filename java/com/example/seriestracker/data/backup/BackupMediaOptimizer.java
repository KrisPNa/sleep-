package com.example.seriestracker.data.backup;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.util.Locale;

final class BackupMediaOptimizer {

    static final int COVER_MAX_DIMENSION = 720;
    static final int PHOTO_MAX_DIMENSION = 1440;
    static final int JPEG_QUALITY = 82;
    private static final long SKIP_COMPRESS_BELOW_BYTES = 250_000L;

    private BackupMediaOptimizer() {
    }

    static boolean isImageFileName(String fileName) {
        if (fileName == null) {
            return false;
        }
        String lower = fileName.toLowerCase(Locale.ROOT);
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png")
                || lower.endsWith(".webp") || lower.endsWith(".heic") || lower.endsWith(".heif")
                || lower.endsWith(".bmp");
    }

    static boolean isVideoFileName(String fileName) {
        if (fileName == null) {
            return false;
        }
        String lower = fileName.toLowerCase(Locale.ROOT);
        return lower.endsWith(".mp4") || lower.endsWith(".mkv") || lower.endsWith(".mov")
                || lower.endsWith(".avi") || lower.endsWith(".wmv") || lower.endsWith(".3gp")
                || lower.endsWith(".webm");
    }

    static boolean shouldCompress(BackupFileManager.BackupMediaKind kind, String fileName) {
        if (kind != BackupFileManager.BackupMediaKind.COVER
                && kind != BackupFileManager.BackupMediaKind.PHOTO) {
            return false;
        }
        return isImageFileName(fileName);
    }

    static int maxDimensionFor(BackupFileManager.BackupMediaKind kind) {
        return kind == BackupFileManager.BackupMediaKind.COVER
                ? COVER_MAX_DIMENSION
                : PHOTO_MAX_DIMENSION;
    }

    static boolean writeOptimizedImage(File source, File destination, int maxDimension, int quality)
            throws IOException {
        if (source.length() <= SKIP_COMPRESS_BELOW_BYTES) {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(source.getAbsolutePath(), bounds);
            if (bounds.outWidth > 0 && bounds.outWidth <= maxDimension && bounds.outHeight <= maxDimension) {
                fastCopy(source, destination);
                return destination.exists() && destination.length() > 0;
            }
        }

        Bitmap bitmap = decodeSampledBitmap(source.getAbsolutePath(), maxDimension);
        if (bitmap == null) {
            return false;
        }

        Bitmap scaled = scaleDownIfNeeded(bitmap, maxDimension);
        if (scaled != bitmap) {
            bitmap.recycle();
            bitmap = scaled;
        }

        File parent = destination.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            bitmap.recycle();
            return false;
        }

        try (FileOutputStream outputStream = new FileOutputStream(destination)) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream);
            outputStream.flush();
        } finally {
            bitmap.recycle();
        }

        return destination.exists() && destination.length() > 0;
    }

    static boolean writeOptimizedImage(InputStream inputStream, File destination, int maxDimension, int quality)
            throws IOException {
        byte[] bytes = readAllBytes(inputStream);
        if (bytes.length == 0) {
            return false;
        }

        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return false;
        }

        if (bytes.length <= SKIP_COMPRESS_BELOW_BYTES
                && bounds.outWidth <= maxDimension
                && bounds.outHeight <= maxDimension) {
            try (FileOutputStream outputStream = new FileOutputStream(destination)) {
                outputStream.write(bytes);
                outputStream.flush();
            }
            return destination.exists() && destination.length() > 0;
        }

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxDimension);
        Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
        if (bitmap == null) {
            return false;
        }

        Bitmap scaled = scaleDownIfNeeded(bitmap, maxDimension);
        if (scaled != bitmap) {
            bitmap.recycle();
            bitmap = scaled;
        }

        try (FileOutputStream outputStream = new FileOutputStream(destination)) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream);
            outputStream.flush();
        } finally {
            bitmap.recycle();
        }

        return destination.exists() && destination.length() > 0;
    }

    static void fastCopy(File source, File destination) throws IOException {
        File parent = destination.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Failed to create directory: " + parent.getAbsolutePath());
        }

        try (FileInputStream inputStream = new FileInputStream(source);
             FileOutputStream outputStream = new FileOutputStream(destination);
             FileChannel inChannel = inputStream.getChannel();
             FileChannel outChannel = outputStream.getChannel()) {
            long size = inChannel.size();
            long position = 0;
            while (position < size) {
                position += outChannel.transferFrom(inChannel, position, size - position);
            }
            outputStream.flush();
        }
    }

    static String asJpegFileName(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return "image.jpg";
        }
        int dotIndex = fileName.lastIndexOf('.');
        String base = dotIndex > 0 ? fileName.substring(0, dotIndex) : fileName;
        return base + ".jpg";
    }

    private static Bitmap decodeSampledBitmap(String path, int maxDimension) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return null;
        }

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxDimension);
        return BitmapFactory.decodeFile(path, options);
    }

    private static Bitmap scaleDownIfNeeded(Bitmap bitmap, int maxDimension) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        float largestSide = Math.max(width, height);
        if (largestSide <= maxDimension) {
            return bitmap;
        }

        float scale = maxDimension / largestSide;
        Matrix matrix = new Matrix();
        matrix.postScale(scale, scale);
        return Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, true);
    }

    private static int calculateInSampleSize(int width, int height, int maxDimension) {
        int inSampleSize = 1;
        int largest = Math.max(width, height);
        while (largest / inSampleSize > maxDimension * 2) {
            inSampleSize *= 2;
        }
        return Math.max(1, inSampleSize);
    }

    private static byte[] readAllBytes(InputStream inputStream) throws IOException {
        byte[] buffer = new byte[8192];
        int read;
        java.io.ByteArrayOutputStream outputStream = new java.io.ByteArrayOutputStream();
        while ((read = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, read);
        }
        return outputStream.toByteArray();
    }
}
