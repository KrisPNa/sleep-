package com.example.seriestracker.utils;

import android.util.Patterns;

import java.util.regex.Matcher;

public final class ShareTextHelper {

    private ShareTextHelper() {
    }

    public static String extractWatchUrl(String sharedText) {
        if (sharedText == null) {
            return "";
        }

        String trimmed = sharedText.trim();
        if (trimmed.isEmpty()) {
            return "";
        }

        Matcher matcher = Patterns.WEB_URL.matcher(trimmed);
        String foundUrl = null;
        while (matcher.find()) {
            foundUrl = matcher.group();
        }
        return foundUrl != null ? foundUrl : trimmed;
    }
}
