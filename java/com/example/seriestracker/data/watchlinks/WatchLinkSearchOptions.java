package com.example.seriestracker.data.watchlinks;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Параметры поиска ссылок.
 * excludeUrls — уже показанные URL (режим «Найти ещё»), оптимизация та же, что у быстрого поиска.
 */
public final class WatchLinkSearchOptions {

    @NonNull
    public final Set<String> excludeUrls;

    private WatchLinkSearchOptions(@Nullable Set<String> excludeUrls) {
        this.excludeUrls = excludeUrls != null
                ? Collections.unmodifiableSet(new LinkedHashSet<>(excludeUrls))
                : Collections.emptySet();
    }

    @NonNull
    public static WatchLinkSearchOptions quick() {
        return new WatchLinkSearchOptions(null);
    }

    @NonNull
    public static WatchLinkSearchOptions findMore(@Nullable Set<String> alreadyShownUrls) {
        return new WatchLinkSearchOptions(alreadyShownUrls);
    }
}
