package com.example.seriestracker.data.watchlinks;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Ищет ссылки по сайтам из настроек.
 * Отдаёт результаты прогрессивно; отменяет остальные сайты после нескольких
 * новых совпадений (в т.ч. в режиме «Найти ещё»).
 */
public class WatchLinkSearchService {

    private static final int SITE_PARALLELISM = 4;
    private static final int EARLY_EXIT_COUNT = 3;
    private static final int SITE_TIMEOUT_SECONDS = 45;

    public interface ProgressListener {
        /** Новые/накопленные результаты (уже без дублей по URL). */
        void onPartialResults(@NonNull List<WatchLinkCandidate> resultsSoFar);

        void onComplete(@NonNull List<WatchLinkCandidate> finalResults);

        void onError(@NonNull String message);
    }

    private final List<WatchLinkProvider> providers;

    public WatchLinkSearchService(@NonNull List<WatchLinkProvider> providers) {
        this.providers = new ArrayList<>(providers);
    }

    @NonNull
    public static WatchLinkSearchService createFromSettings(@NonNull Context context) {
        List<String> sites = WatchSearchSitesStore.getNormalizedSites(context);
        List<WatchLinkProvider> providers = new ArrayList<>();
        for (String site : sites) {
            providers.add(new ConfiguredSiteWatchLinkProvider(site));
        }
        return new WatchLinkSearchService(providers);
    }

    @NonNull
    public List<WatchLinkCandidate> search(@NonNull String query) throws IOException {
        final List<WatchLinkCandidate>[] holder = new List[]{null};
        final String[] error = new String[]{null};
        search(query, WatchLinkSearchOptions.quick(), new ProgressListener() {
            @Override
            public void onPartialResults(@NonNull List<WatchLinkCandidate> resultsSoFar) {
            }

            @Override
            public void onComplete(@NonNull List<WatchLinkCandidate> finalResults) {
                holder[0] = finalResults;
            }

            @Override
            public void onError(@NonNull String message) {
                error[0] = message;
            }
        });
        if (error[0] != null) {
            throw new IOException(error[0]);
        }
        return holder[0] != null ? holder[0] : new ArrayList<>();
    }

    public void search(@NonNull String query, @NonNull ProgressListener listener) {
        search(query, WatchLinkSearchOptions.quick(), listener);
    }

    public void search(@NonNull String query,
                       @NonNull WatchLinkSearchOptions options,
                       @NonNull ProgressListener listener) {
        String trimmed = query.trim();
        if (trimmed.isEmpty()) {
            listener.onError("Укажите название сериала");
            return;
        }
        if (providers.isEmpty()) {
            listener.onError("Добавьте сайты для поиска в настройках резервного копирования");
            return;
        }

        Map<String, WatchLinkCandidate> uniqueByUrl = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();
        AtomicBoolean enough = new AtomicBoolean(false);

        ExecutorService pool = Executors.newFixedThreadPool(
                Math.min(SITE_PARALLELISM, Math.max(1, providers.size())));
        try {
            ExecutorCompletionService<ProviderResult> completion =
                    new ExecutorCompletionService<>(pool);
            List<Future<ProviderResult>> futures = new ArrayList<>();

            for (WatchLinkProvider provider : providers) {
                Callable<ProviderResult> task = () -> {
                    if (enough.get() || Thread.currentThread().isInterrupted()) {
                        throw new CancellationException();
                    }
                    try {
                        List<WatchLinkCandidate> found = provider.search(trimmed, options);
                        if (enough.get() || Thread.currentThread().isInterrupted()) {
                            throw new CancellationException();
                        }
                        return ProviderResult.ok(found);
                    } catch (CancellationException e) {
                        throw e;
                    } catch (IOException e) {
                        return ProviderResult.fail(
                                provider.getDisplayName() + ": " + e.getMessage());
                    }
                };
                futures.add(completion.submit(task));
            }

            int remaining = futures.size();
            while (remaining > 0) {
                try {
                    Future<ProviderResult> done = completion.poll(SITE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                    if (done == null) {
                        break;
                    }
                    remaining--;
                    ProviderResult result;
                    try {
                        result = done.get();
                    } catch (CancellationException e) {
                        continue;
                    } catch (Exception e) {
                        if (e.getCause() instanceof CancellationException) {
                            continue;
                        }
                        errors.add(e.getMessage() != null ? e.getMessage() : "Ошибка поиска");
                        continue;
                    }

                    if (result.error != null) {
                        errors.add(result.error);
                        continue;
                    }

                    boolean added = false;
                    for (WatchLinkCandidate candidate : result.candidates) {
                        if (candidate.getUrl() == null || candidate.getUrl().trim().isEmpty()) {
                            continue;
                        }
                        String url = candidate.getUrl().trim();
                        if (options.excludeUrls.contains(url)) {
                            continue;
                        }
                        if (uniqueByUrl.putIfAbsent(url, candidate) == null) {
                            added = true;
                        }
                    }
                    if (added) {
                        listener.onPartialResults(new ArrayList<>(uniqueByUrl.values()));
                    }

                    if (uniqueByUrl.size() >= EARLY_EXIT_COUNT) {
                        enough.set(true);
                        cancelAll(futures);
                        break;
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    enough.set(true);
                    cancelAll(futures);
                    break;
                }
            }
        } finally {
            pool.shutdownNow();
        }

        List<WatchLinkCandidate> finalResults = new ArrayList<>(uniqueByUrl.values());
        if (finalResults.isEmpty() && !errors.isEmpty() && options.excludeUrls.isEmpty()) {
            listener.onError(errors.get(0));
            return;
        }
        listener.onComplete(finalResults);
    }

    private static void cancelAll(@NonNull List<Future<ProviderResult>> futures) {
        for (Future<ProviderResult> future : futures) {
            future.cancel(true);
        }
    }

    private static final class ProviderResult {
        final List<WatchLinkCandidate> candidates;
        final String error;

        private ProviderResult(@Nullable List<WatchLinkCandidate> candidates, @Nullable String error) {
            this.candidates = candidates != null ? candidates : new ArrayList<>();
            this.error = error;
        }

        static ProviderResult ok(List<WatchLinkCandidate> candidates) {
            return new ProviderResult(candidates, null);
        }

        static ProviderResult fail(String error) {
            return new ProviderResult(null, error);
        }
    }
}
