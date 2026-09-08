package com.example.seriestracker.data.watchlinks;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class WatchLinkCandidate {

    private final String title;
    private final String url;
    private final String sourceName;
    @Nullable
    private final String subtitle;

    public WatchLinkCandidate(@NonNull String title,
                              @NonNull String url,
                              @NonNull String sourceName,
                              @Nullable String subtitle) {
        this.title = title;
        this.url = url;
        this.sourceName = sourceName;
        this.subtitle = subtitle;
    }

    @NonNull
    public String getTitle() {
        return title;
    }

    @NonNull
    public String getUrl() {
        return url;
    }

    @NonNull
    public String getSourceName() {
        return sourceName;
    }

    @Nullable
    public String getSubtitle() {
        return subtitle;
    }

    @NonNull
    public String getSelectionKey() {
        return url;
    }
}
