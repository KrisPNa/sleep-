package com.example.seriestracker.data.watchlinks;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.util.List;

public interface WatchLinkProvider {

    @NonNull
    String getDisplayName();

    @NonNull
    List<WatchLinkCandidate> search(@NonNull String query) throws IOException;

    @NonNull
    default List<WatchLinkCandidate> search(@NonNull String query,
                                            @NonNull WatchLinkSearchOptions options)
            throws IOException {
        return search(query);
    }
}
