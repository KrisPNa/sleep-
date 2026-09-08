package com.example.seriestracker.data.watchlinks;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Ищет карточки сериала на сайте.
 * 1) Сначала — по транслиту названия (латиница в URL/поиске).
 * 2) Если пусто — ищет страницы, где название сериала есть точно как в приложении.
 * Оптимизации: параллельные запросы, мало открытий страниц, ранний выход.
 */
public class ConfiguredSiteWatchLinkProvider implements WatchLinkProvider {

    private static final int MAX_RESULTS = 10;
    /** Достаточно совпадений, чтобы прекратить дальнейшие запросы. */
    private static final int EARLY_EXIT_COUNT = 3;
    /** Сколько карточек максимум открывать при точном поиске. */
    private static final int MAX_EXACT_PAGE_CHECKS = 8;
    private static final int PARALLELISM = 5;
    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int READ_TIMEOUT_MS = 5000;
    private static final int MAX_TRANSLIT_VARIANTS = 2;

    private static final Pattern ANCHOR_PATTERN = Pattern.compile(
            "<a\\s+[^>]*href\\s*=\\s*[\"']([^\"']+)[\"'][^>]*>(.*?)</a>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private static final Pattern TITLE_TAG_PATTERN = Pattern.compile(
            "(?is)<title[^>]*>(.*?)</title>");

    private static final Pattern H1_PATTERN = Pattern.compile(
            "(?is)<h1[^>]*>(.*?)</h1>");

    private final String siteUrl;
    private final String host;
    private final String displayName;

    public ConfiguredSiteWatchLinkProvider(@NonNull String siteUrl) {
        this.siteUrl = siteUrl;
        Uri uri = Uri.parse(siteUrl);
        String parsedHost = uri.getHost();
        this.host = parsedHost != null ? parsedHost.toLowerCase(Locale.US) : siteUrl;
        this.displayName = parsedHost != null ? parsedHost : siteUrl;
    }

    @NonNull
    @Override
    public String getDisplayName() {
        return displayName;
    }

    @NonNull
    @Override
    public List<WatchLinkCandidate> search(@NonNull String query) throws IOException {
        return search(query, WatchLinkSearchOptions.quick());
    }

    @NonNull
    @Override
    public List<WatchLinkCandidate> search(@NonNull String query,
                                           @NonNull WatchLinkSearchOptions options)
            throws IOException {
        String trimmed = query.trim();
        List<String> latinVariants = RussianTransliterator.toLatinVariants(trimmed);
        List<String> latinSearchVariants = RussianTransliterator.toLatinSearchVariants(trimmed);
        if (latinSearchVariants.size() > MAX_TRANSLIT_VARIANTS) {
            latinSearchVariants = new ArrayList<>(latinSearchVariants.subList(0, MAX_TRANSLIT_VARIANTS));
        }

        final List<String> finalLatinSearchVariants = latinSearchVariants;
        final Set<String> excludeUrls = options.excludeUrls;
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CompletionService<List<WatchLinkCandidate>> completion =
                    new ExecutorCompletionService<>(pool);
            Future<List<WatchLinkCandidate>> translitFuture = completion.submit(
                    () -> searchByTransliteration(
                            trimmed, latinVariants, finalLatinSearchVariants,
                            EARLY_EXIT_COUNT, MAX_RESULTS, excludeUrls));
            Future<List<WatchLinkCandidate>> exactFuture = completion.submit(
                    () -> searchByExactTitle(
                            trimmed, EARLY_EXIT_COUNT, MAX_RESULTS,
                            MAX_EXACT_PAGE_CHECKS, excludeUrls));

            // Первый непустой (после исключения уже показанных) — как у быстрого поиска
            List<WatchLinkCandidate> emptyFallback = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                try {
                    Future<List<WatchLinkCandidate>> done = completion.take();
                    List<WatchLinkCandidate> result = done.get();
                    if (result != null && !result.isEmpty()) {
                        translitFuture.cancel(true);
                        exactFuture.cancel(true);
                        return result;
                    }
                } catch (Exception ignored) {
                }
            }
            return emptyFallback;
        } finally {
            pool.shutdownNow();
        }
    }

    @NonNull
    private List<WatchLinkCandidate> searchByTransliteration(@NonNull String query,
                                                             @NonNull List<String> latinVariants,
                                                             @NonNull List<String> latinSearchVariants,
                                                             int earlyExit,
                                                             int maxResults,
                                                             @NonNull Set<String> excludeUrls) {
        List<String> searchUrls = buildTranslitSearchUrls(latinSearchVariants);
        Map<String, String> htmlByUrl = fetchHtmlParallel(searchUrls);

        Map<String, ScoredCandidate> scored = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : htmlByUrl.entrySet()) {
            collectTranslitMatches(
                    entry.getValue(), query, latinVariants, entry.getKey(), scored, excludeUrls);
            if (countScoredAbove(scored, 3) >= earlyExit) {
                break;
            }
        }
        int limit = Math.min(maxResults, earlyExit + 2);
        return toRankedResults(scored, limit, 3);
    }

    @NonNull
    private List<WatchLinkCandidate> searchByExactTitle(@NonNull String query,
                                                        int earlyExit,
                                                        int maxResults,
                                                        int maxPageChecks,
                                                        @NonNull Set<String> excludeUrls) {
        String encoded;
        try {
            encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            return new ArrayList<>();
        }

        List<String> searchUrls = new ArrayList<>();
        searchUrls.add(siteUrl + "/?s=" + encoded);

        Map<String, WatchLinkCandidate> exact = new LinkedHashMap<>();
        List<String> pagesToCheck = new ArrayList<>();

        collectExactFromSearchPages(
                fetchHtmlParallel(searchUrls), query, exact, pagesToCheck,
                earlyExit, maxPageChecks, excludeUrls);

        if (exact.size() >= earlyExit) {
            return limitResults(exact, maxResults);
        }

        if (exact.isEmpty()) {
            List<String> fallbackSearch = new ArrayList<>();
            fallbackSearch.add(siteUrl + "/search?q=" + encoded);
            fallbackSearch.add(siteUrl + "/search/?s=" + encoded);
            collectExactFromSearchPages(
                    fetchHtmlParallel(fallbackSearch), query, exact, pagesToCheck,
                    earlyExit, maxPageChecks, excludeUrls);
            if (exact.size() >= earlyExit) {
                return limitResults(exact, maxResults);
            }
        }

        List<String> limitedPages = new ArrayList<>();
        for (String pageUrl : pagesToCheck) {
            if (exact.containsKey(pageUrl) || excludeUrls.contains(pageUrl)) {
                continue;
            }
            limitedPages.add(pageUrl);
            if (limitedPages.size() >= maxPageChecks) {
                break;
            }
        }

        if (!limitedPages.isEmpty() && exact.size() < earlyExit) {
            checkPagesParallel(limitedPages, query, exact, earlyExit, excludeUrls);
        }

        return limitResults(exact, maxResults);
    }

    private void collectExactFromSearchPages(@NonNull Map<String, String> htmlByUrl,
                                             @NonNull String query,
                                             @NonNull Map<String, WatchLinkCandidate> exact,
                                             @NonNull List<String> pagesToCheck,
                                             int earlyExit,
                                             int maxPageChecks,
                                             @NonNull Set<String> excludeUrls) {
        for (Map.Entry<String, String> entry : htmlByUrl.entrySet()) {
            String searchUrl = entry.getKey();
            Matcher matcher = ANCHOR_PATTERN.matcher(entry.getValue());
            while (matcher.find()) {
                String absolute = resolveUrl(searchUrl, matcher.group(1));
                if (absolute == null || !isSameHost(absolute) || isBarelyUsableUrl(absolute)) {
                    continue;
                }
                if (excludeUrls.contains(absolute)) {
                    continue;
                }

                String linkTitle = cleanHtml(matcher.group(2));
                if (matchesTitleAllowingContinuation(linkTitle, query)) {
                    exact.putIfAbsent(absolute, new WatchLinkCandidate(
                            isUsableTitle(linkTitle) ? linkTitle : query,
                            absolute,
                            displayName,
                            displayName + " · точное название"));
                    if (exact.size() >= earlyExit) {
                        return;
                    }
                    continue;
                }

                boolean likelyCard = !isNoiseUrl(absolute) && looksLikeSeriesCardUrl(absolute);
                boolean titleHint = containsExactPhrase(linkTitle, query);
                if (!pagesToCheck.contains(absolute)
                        && pagesToCheck.size() < maxPageChecks
                        && (likelyCard || titleHint)) {
                    if (titleHint) {
                        pagesToCheck.add(0, absolute);
                    } else {
                        pagesToCheck.add(absolute);
                    }
                }
            }
        }
    }

    private void checkPagesParallel(@NonNull List<String> pageUrls,
                                    @NonNull String query,
                                    @NonNull Map<String, WatchLinkCandidate> exact,
                                    int earlyExit,
                                    @NonNull Set<String> excludeUrls) {
        ExecutorService pool = Executors.newFixedThreadPool(PARALLELISM);
        try {
            CompletionService<WatchLinkCandidate> completion =
                    new ExecutorCompletionService<>(pool);
            int submitted = 0;
            for (String pageUrl : pageUrls) {
                if (exact.containsKey(pageUrl) || excludeUrls.contains(pageUrl)) {
                    continue;
                }
                final String url = pageUrl;
                completion.submit(() -> {
                    try {
                        String pageHtml = httpGet(url);
                        if (!pageContainsExactTitle(pageHtml, query)) {
                            return null;
                        }
                        String title = extractBestPageTitle(pageHtml, query);
                        return new WatchLinkCandidate(
                                title,
                                url,
                                displayName,
                                displayName + " · точное название");
                    } catch (IOException e) {
                        return null;
                    }
                });
                submitted++;
            }

            for (int i = 0; i < submitted; i++) {
                try {
                    WatchLinkCandidate candidate = completion.take().get();
                    if (candidate != null
                            && !excludeUrls.contains(candidate.getUrl())) {
                        exact.putIfAbsent(candidate.getUrl(), candidate);
                        if (exact.size() >= earlyExit) {
                            break;
                        }
                    }
                } catch (Exception ignored) {
                    // пропускаем сбой одного запроса
                }
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @NonNull
    private Map<String, String> fetchHtmlParallel(@NonNull List<String> urls) {
        Map<String, String> result = new LinkedHashMap<>();
        if (urls.isEmpty()) {
            return result;
        }
        if (urls.size() == 1) {
            try {
                result.put(urls.get(0), httpGet(urls.get(0)));
            } catch (IOException ignored) {
            }
            return result;
        }

        ExecutorService pool = Executors.newFixedThreadPool(Math.min(PARALLELISM, urls.size()));
        try {
            List<Future<?>> futures = new ArrayList<>();
            Map<String, String> concurrent = new ConcurrentHashMap<>();
            for (String url : urls) {
                futures.add(pool.submit(() -> {
                    try {
                        concurrent.put(url, httpGet(url));
                    } catch (IOException ignored) {
                    }
                }));
            }
            for (Future<?> future : futures) {
                try {
                    future.get();
                } catch (Exception ignored) {
                }
            }
            // Сохраняем порядок исходного списка
            for (String url : urls) {
                String html = concurrent.get(url);
                if (html != null) {
                    result.put(url, html);
                }
            }
        } finally {
            pool.shutdownNow();
        }
        return result;
    }

    private static int countScoredAbove(@NonNull Map<String, ScoredCandidate> scored, int minScore) {
        int count = 0;
        for (ScoredCandidate item : scored.values()) {
            if (item.score >= minScore) {
                count++;
            }
        }
        return count;
    }

    @NonNull
    private static List<WatchLinkCandidate> limitResults(
            @NonNull Map<String, WatchLinkCandidate> exact, int max) {
        List<WatchLinkCandidate> list = new ArrayList<>();
        for (WatchLinkCandidate candidate : exact.values()) {
            list.add(candidate);
            if (list.size() >= max) {
                break;
            }
        }
        return list;
    }

    @NonNull
    private List<String> buildTranslitSearchUrls(@NonNull List<String> latinSearchVariants) {
        List<String> searchUrls = new ArrayList<>();
        for (String variant : latinSearchVariants) {
            if (variant == null || variant.trim().isEmpty()) {
                continue;
            }
            try {
                String encodedLatin = URLEncoder.encode(variant, StandardCharsets.UTF_8.name());
                addUnique(searchUrls, siteUrl + "/?s=" + encodedLatin);
            } catch (Exception ignored) {
                // skip
            }
        }
        return searchUrls;
    }

    private static void addUnique(@NonNull List<String> list, @NonNull String value) {
        if (!list.contains(value)) {
            list.add(value);
        }
    }

    @NonNull
    private List<WatchLinkCandidate> toRankedResults(@NonNull Map<String, ScoredCandidate> scored,
                                                     int maxResults,
                                                     int minScore) {
        List<ScoredCandidate> ranked = new ArrayList<>(scored.values());
        ranked.sort((a, b) -> Integer.compare(b.score, a.score));

        List<WatchLinkCandidate> results = new ArrayList<>();
        for (ScoredCandidate item : ranked) {
            if (results.size() >= maxResults) {
                break;
            }
            if (item.score < minScore) {
                continue;
            }
            results.add(item.candidate);
        }
        return results;
    }

    private void collectTranslitMatches(@NonNull String html,
                                        @NonNull String query,
                                        @NonNull List<String> latinVariants,
                                        @NonNull String searchUrl,
                                        @NonNull Map<String, ScoredCandidate> out,
                                        @NonNull Set<String> excludeUrls) {
        Matcher matcher = ANCHOR_PATTERN.matcher(html);
        while (matcher.find()) {
            String absolute = resolveUrl(searchUrl, matcher.group(1));
            if (absolute == null || !isSameHost(absolute)) {
                continue;
            }
            if (excludeUrls.contains(absolute)) {
                continue;
            }
            if (isNoiseUrl(absolute) || !looksLikeSeriesCardUrl(absolute)) {
                continue;
            }

            String title = cleanHtml(matcher.group(2));
            if (!isUsableTitle(title)) {
                title = prettifySlug(extractSlug(absolute));
            }
            if (!isUsableTitle(title)) {
                title = query;
            }

            int score = scoreTransliterationMatch(title, absolute, latinVariants);
            if (score < 3) {
                continue;
            }

            ScoredCandidate existing = out.get(absolute);
            if (existing == null || score > existing.score) {
                out.put(absolute, new ScoredCandidate(
                        score,
                        new WatchLinkCandidate(title, absolute, displayName, displayName)));
            }
        }
    }

    /** Оценка только по совпадению с транслитом (не по русскому тексту). */
    private static int scoreTransliterationMatch(@NonNull String title,
                                                 @NonNull String url,
                                                 @NonNull List<String> latinVariants) {
        String slug = normalizeSlug(extractSlug(url));
        String normalizedTitle = normalizeForMatch(title);
        int best = 0;

        for (String latinQuery : latinVariants) {
            String normalizedLatin = normalizeForMatch(latinQuery);
            if (normalizedLatin.isEmpty()) {
                continue;
            }
            String latinSlug = normalizedLatin.replace(' ', '-');
            String latinCompact = normalizedLatin.replace(" ", "");

            if (!slug.isEmpty()) {
                if (slug.equals(latinSlug) || slug.equals(latinCompact)) {
                    best = Math.max(best, 8);
                } else if (slug.contains(latinSlug)
                        || latinSlug.contains(slug)
                        || slugContainsAllWords(slug, normalizedLatin)) {
                    best = Math.max(best, 6);
                } else if (slugContainsAnySignificantWord(slug, normalizedLatin)) {
                    best = Math.max(best, 3);
                }
            }

            if (normalizedTitle.equals(normalizedLatin)
                    || normalizedTitle.contains(normalizedLatin)
                    || normalizedLatin.contains(normalizedTitle)) {
                best = Math.max(best, 5);
            }
        }
        return best;
    }

    /**
     * Название сериала есть целиком где угодно в тексте:
     * в начале, середине или конце заголовка.
     * Допускаются год в скобках и любой текст вокруг.
     */
    private static boolean matchesTitleAllowingContinuation(@Nullable String text,
                                                            @NonNull String query) {
        if (text == null) {
            return false;
        }
        String cleaned = cleanHtml(text).trim();
        if (cleaned.isEmpty()) {
            return false;
        }
        return titlesEqualWithOptionalYear(cleaned, query)
                || containsExactPhrase(cleaned, query);
    }

    private static boolean titlesEqualWithOptionalYear(@NonNull String text, @NonNull String query) {
        String normalizedText = normalizeForMatch(text);
        String normalizedQuery = normalizeForMatch(query);
        if (normalizedQuery.isEmpty() || normalizedText.isEmpty()) {
            return false;
        }
        if (normalizedText.equals(normalizedQuery)) {
            return true;
        }
        String textBase = stripTrailingYear(normalizedText);
        String queryBase = stripTrailingYear(normalizedQuery);
        return !textBase.isEmpty() && textBase.equals(queryBase);
    }

    private static boolean pageContainsExactTitle(@NonNull String html, @NonNull String query) {
        String normalizedQuery = normalizeForMatch(query);
        if (normalizedQuery.isEmpty()) {
            return false;
        }

        String titleTag = firstGroup(TITLE_TAG_PATTERN, html);
        if (matchesTitleAllowingContinuation(titleTag, query)) {
            return true;
        }

        String h1 = firstGroup(H1_PATTERN, html);
        if (matchesTitleAllowingContinuation(h1, query)) {
            return true;
        }

        // В теле страницы — целая фраза названия где угодно
        return containsExactPhrase(cleanHtml(html), query);
    }

    private static boolean containsExactPhrase(@Nullable String text, @NonNull String query) {
        if (text == null) {
            return false;
        }
        String normalizedText = " " + normalizeForMatch(cleanHtml(text)) + " ";
        String normalizedQuery = normalizeForMatch(query);
        if (normalizedQuery.isEmpty()) {
            return false;
        }
        // Полное название где угодно: начало / середина / конец
        if (normalizedText.contains(" " + normalizedQuery + " ")) {
            return true;
        }

        String queryBase = stripTrailingYear(normalizedQuery);
        if (queryBase.isEmpty()) {
            return false;
        }
        if (normalizedText.contains(" " + queryBase + " ")) {
            return true;
        }
        // Название + год где угодно: «… название 2023 …»
        return Pattern.compile("\\s" + Pattern.quote(queryBase) + "\\s+\\d{4}\\s")
                .matcher(normalizedText)
                .find();
    }

    /**
     * Убирает год в конце: после normalize «Название (2023)» → «название 2023».
     */
    @NonNull
    private static String stripTrailingYear(@NonNull String normalized) {
        return normalized.replaceFirst("\\s+\\d{4}$", "").trim();
    }

    @NonNull
    private static String extractBestPageTitle(@NonNull String html, @NonNull String fallback) {
        String h1 = cleanHtml(firstGroup(H1_PATTERN, html));
        if (isUsableTitle(h1)) {
            return h1;
        }
        String titleTag = cleanHtml(firstGroup(TITLE_TAG_PATTERN, html));
        if (isUsableTitle(titleTag)) {
            // Часто "Название | Сайт" — берём часть до разделителя
            int sep = indexOfAny(titleTag, '|', '—', '-', '·');
            if (sep > 0) {
                String left = titleTag.substring(0, sep).trim();
                if (isUsableTitle(left)) {
                    return left;
                }
            }
            return titleTag;
        }
        return fallback;
    }

    private static int indexOfAny(@NonNull String text, char... chars) {
        int best = -1;
        for (char ch : chars) {
            int idx = text.indexOf(ch);
            if (idx >= 0 && (best < 0 || idx < best)) {
                best = idx;
            }
        }
        return best;
    }

    @Nullable
    private static String firstGroup(@NonNull Pattern pattern, @NonNull String html) {
        Matcher matcher = pattern.matcher(html);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    /** Минимум отсечения: javascript/mailto/пустые якоря. */
    private static boolean isBarelyUsableUrl(@NonNull String url) {
        String lower = url.toLowerCase(Locale.US);
        return lower.startsWith("javascript:")
                || lower.startsWith("mailto:")
                || lower.startsWith("tel:")
                || "#".equals(url.trim())
                || lower.endsWith("#");
    }

    private static boolean slugContainsAllWords(@NonNull String slug, @NonNull String latinQuery) {
        String[] words = latinQuery.split("\\s+");
        int needed = 0;
        int hits = 0;
        for (String word : words) {
            if (word.length() < 2) {
                continue;
            }
            needed++;
            if (slug.contains(word)) {
                hits++;
            }
        }
        return needed > 0 && hits == needed;
    }

    private static boolean slugContainsAnySignificantWord(@NonNull String slug,
                                                          @NonNull String latinQuery) {
        for (String word : latinQuery.split("\\s+")) {
            if (word.length() >= 3 && slug.contains(word)) {
                return true;
            }
        }
        return false;
    }

    private boolean isSameHost(@NonNull String url) {
        Uri uri = Uri.parse(url);
        String otherHost = uri.getHost();
        if (otherHost == null) {
            return false;
        }
        otherHost = otherHost.toLowerCase(Locale.US);
        return otherHost.equals(host)
                || otherHost.endsWith("." + host)
                || host.endsWith("." + otherHost);
    }

    private static boolean isNoiseUrl(@NonNull String url) {
        String lower = url.toLowerCase(Locale.US);
        return lower.contains("/wp-admin")
                || lower.contains("/wp-login")
                || lower.contains("/tag/")
                || lower.contains("/tags/")
                || lower.contains("/category/")
                || lower.contains("/author/")
                || lower.contains("/feed")
                || lower.contains("/comment")
                || lower.contains("replytocom")
                || lower.contains("#comment")
                || lower.contains("comment-page")
                || lower.contains("#respond")
                || lower.contains("#reply")
                || lower.contains("/discuss")
                || lower.contains("javascript:")
                || lower.contains("mailto:")
                || lower.contains("/page/")
                || lower.contains("?s=")
                || lower.contains("&s=")
                || lower.contains("/search")
                || lower.endsWith("#")
                || lower.matches("(?i)https?://[^/]+/?");
    }

    private static boolean looksLikeSeriesCardUrl(@NonNull String url) {
        Uri uri = Uri.parse(url);
        String path = uri.getPath();
        if (path == null || path.isEmpty() || "/".equals(path)) {
            return false;
        }
        String lowerPath = path.toLowerCase(Locale.US);
        if (lowerPath.contains("/comment")
                || lowerPath.contains("/trackback")
                || lowerPath.contains("/embed")) {
            return false;
        }

        String slug = extractSlug(url);
        if (slug.isEmpty() || slug.length() < 2) {
            return false;
        }
        // Нужны страницы вида /english-name — слаг с латиницей/цифрами.
        return slug.matches("(?i)[a-z0-9][a-z0-9\\-_]{1,}");
    }

    @NonNull
    private static String extractSlug(@NonNull String url) {
        Uri uri = Uri.parse(url);
        String path = uri.getPath();
        if (path == null || path.isEmpty()) {
            return "";
        }
        String trimmed = path;
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        int slash = trimmed.lastIndexOf('/');
        String slug = slash >= 0 ? trimmed.substring(slash + 1) : trimmed;
        // Иногда последний сегмент — число ID, берём предыдущий
        if (slug.matches("\\d+") && slash > 0) {
            String parent = trimmed.substring(0, slash);
            int prev = parent.lastIndexOf('/');
            slug = prev >= 0 ? parent.substring(prev + 1) : parent;
        }
        return slug;
    }

    @NonNull
    private static String normalizeSlug(@NonNull String slug) {
        return slug.toLowerCase(Locale.US)
                .replace('_', '-')
                .replaceAll("[^a-z0-9\\-]+", "-")
                .replaceAll("-{2,}", "-")
                .replaceAll("^-|-$", "");
    }

    @NonNull
    private static String prettifySlug(@NonNull String slug) {
        String normalized = normalizeSlug(slug).replace('-', ' ').trim();
        if (normalized.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (String part : normalized.split("\\s+")) {
            if (part.isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                builder.append(part.substring(1));
            }
        }
        return builder.toString();
    }

    private static boolean isUsableTitle(@NonNull String title) {
        if (title.trim().length() < 2) {
            return false;
        }
        String lower = title.toLowerCase(Locale.getDefault());
        return !(lower.equals("читать далее")
                || lower.equals("read more")
                || lower.equals("подробнее")
                || lower.equals("more")
                || lower.equals("link")
                || lower.contains("комментар")
                || lower.contains("comment")
                || lower.contains("leave a reply")
                || lower.contains("оставить ответ"));
    }

    @NonNull
    private static String normalizeForMatch(@NonNull String value) {
        return value.toLowerCase(Locale.getDefault())
                .replace('ё', 'е')
                .replaceAll("[^\\p{L}\\p{Nd}\\s\\-]+", " ")
                .replace('-', ' ')
                .replaceAll("\\s+", " ")
                .trim();
    }

    @Nullable
    private static String resolveUrl(@NonNull String baseUrl, @Nullable String href) {
        if (href == null) {
            return null;
        }
        String value = href.trim();
        if (value.isEmpty()
                || value.startsWith("#")
                || value.startsWith("javascript:")
                || value.startsWith("mailto:")) {
            return null;
        }
        try {
            URL resolved = new URL(new URL(baseUrl), value);
            String asString = resolved.toString();
            int hash = asString.indexOf('#');
            if (hash >= 0) {
                asString = asString.substring(0, hash);
            }
            Uri uri = Uri.parse(asString);
            if (uri.isHierarchical()) {
                Uri.Builder builder = uri.buildUpon().clearQuery();
                for (String name : uri.getQueryParameterNames()) {
                    if (name == null) {
                        continue;
                    }
                    String lowerName = name.toLowerCase(Locale.US);
                    if (lowerName.contains("reply")
                            || lowerName.contains("comment")
                            || "s".equals(lowerName)) {
                        continue;
                    }
                    for (String paramValue : uri.getQueryParameters(name)) {
                        builder.appendQueryParameter(name, paramValue);
                    }
                }
                asString = builder.build().toString();
            }
            return asString;
        } catch (Exception e) {
            return null;
        }
    }

    @NonNull
    private static String cleanHtml(@Nullable String html) {
        if (html == null) {
            return "";
        }
        String withoutTags = html.replaceAll("(?s)<[^>]*>", " ");
        return withoutTags
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replaceAll("\\s+", " ")
                .trim();
    }

    @NonNull
    private static String httpGet(@NonNull String urlString) throws IOException {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(urlString);
            connection = (HttpURLConnection) url.openConnection();
            connection.setInstanceFollowRedirects(true);
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setRequestProperty("Accept", "text/html,application/xhtml+xml");
            connection.setRequestProperty("User-Agent",
                    "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 "
                            + "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36");

            int code = connection.getResponseCode();
            InputStream stream = code >= 200 && code < 400
                    ? connection.getInputStream()
                    : connection.getErrorStream();
            if (stream == null) {
                throw new IOException("Пустой ответ (" + code + ")");
            }
            String body = readFully(stream);
            if (code < 200 || code >= 400) {
                throw new IOException("Ошибка сайта (" + code + ")");
            }
            return body;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    @NonNull
    private static String readFully(@NonNull InputStream stream) throws IOException {
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append('\n');
            }
        }
        return builder.toString();
    }

    private static final class ScoredCandidate {
        final int score;
        final WatchLinkCandidate candidate;

        ScoredCandidate(int score, WatchLinkCandidate candidate) {
            this.score = score;
            this.candidate = candidate;
        }
    }
}
