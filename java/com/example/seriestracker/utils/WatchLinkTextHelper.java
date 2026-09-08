package com.example.seriestracker.utils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class WatchLinkTextHelper {

    private WatchLinkTextHelper() {
    }

    @NonNull
    public static String mergeUrls(@Nullable String existingText, @NonNull List<String> urlsToAdd) {
        Set<String> unique = new LinkedHashSet<>();
        for (String part : splitUrls(existingText)) {
            unique.add(part);
        }
        for (String url : urlsToAdd) {
            if (url == null) {
                continue;
            }
            String trimmed = url.trim();
            if (!trimmed.isEmpty()) {
                unique.add(trimmed);
            }
        }
        return joinUrls(new ArrayList<>(unique));
    }

    @NonNull
    public static List<String> splitUrls(@Nullable String text) {
        List<String> urls = new ArrayList<>();
        if (text == null || text.trim().isEmpty()) {
            return urls;
        }
        String[] parts = text.split("\\r?\\n|,|;");
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                urls.add(trimmed);
            }
        }
        return urls;
    }

    @NonNull
    public static String joinUrls(@NonNull List<String> urls) {
        StringBuilder builder = new StringBuilder();
        for (String url : urls) {
            if (url == null || url.trim().isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(url.trim());
        }
        return builder.toString();
    }
}
