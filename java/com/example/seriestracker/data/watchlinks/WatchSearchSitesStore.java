package com.example.seriestracker.data.watchlinks;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Хранит сайты, по которым ищутся ссылки для поля «Смотреть».
 */
public final class WatchSearchSitesStore {

    private static final String PREFS_NAME = "watch_search_sites_prefs";
    private static final String KEY_SITES = "sites_text";

    private WatchSearchSitesStore() {
    }

    @NonNull
    public static String getSitesText(@NonNull Context context) {
        return prefs(context).getString(KEY_SITES, "");
    }

    public static void setSitesText(@NonNull Context context, @Nullable String text) {
        String value = text != null ? text : "";
        prefs(context).edit().putString(KEY_SITES, value).apply();
    }

    @NonNull
    public static List<String> getNormalizedSites(@NonNull Context context) {
        return parseSites(getSitesText(context));
    }

    @NonNull
    public static List<String> parseSites(@Nullable String text) {
        Set<String> unique = new LinkedHashSet<>();
        if (text == null || text.trim().isEmpty()) {
            return new ArrayList<>();
        }
        String[] lines = text.split("\\r?\\n");
        for (String line : lines) {
            String normalized = normalizeSiteUrl(line);
            if (normalized != null) {
                unique.add(normalized);
            }
        }
        return new ArrayList<>(unique);
    }

    @Nullable
    public static String normalizeSiteUrl(@Nullable String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        if (value.isEmpty()) {
            return null;
        }
        // Пропускаем комментарии
        if (value.startsWith("#")) {
            return null;
        }
        if (!value.matches("(?i)^https?://.+")) {
            value = "https://" + value;
        }
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        if (value.equalsIgnoreCase("http:") || value.equalsIgnoreCase("https:")
                || value.equalsIgnoreCase("http:/") || value.equalsIgnoreCase("https:/")
                || value.equalsIgnoreCase("http://") || value.equalsIgnoreCase("https://")) {
            return null;
        }
        return value;
    }

    @NonNull
    private static SharedPreferences prefs(@NonNull Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}
