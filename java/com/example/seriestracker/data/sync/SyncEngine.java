package com.example.seriestracker.data.sync;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.example.seriestracker.data.SeriesDatabase;
import com.example.seriestracker.data.dao.SeriesDao;
import com.example.seriestracker.data.entities.Collection;
import com.example.seriestracker.data.entities.MediaFile;
import com.example.seriestracker.data.entities.Series;
import com.example.seriestracker.data.entities.SeriesCollectionCrossRef;
import com.example.seriestracker.data.watchlinks.WatchSearchSitesStore;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class SyncEngine {
    private static final String TAG = "SyncEngine";

    private static volatile SyncEngine instance;

    private final Context appContext;
    private final SupabaseApi api;
    private final SeriesDao dao;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final AtomicBoolean syncing = new AtomicBoolean(false);
    private final AtomicBoolean pending = new AtomicBoolean(false);
    private volatile Callback pendingCallback;
    private volatile boolean authSignUp = false;
    /** Быстрый pull без загрузки обложек/медиа (для pull-to-refresh). */
    private final AtomicBoolean quickRefresh = new AtomicBoolean(false);
    private static final long SYNC_DEBOUNCE_MS = 1_500L;
    private final Runnable debouncedSyncRunnable = () -> requestSyncImmediate(null);
    private volatile boolean skipHeavyDownloads = false;
    /** cloudId сериалов, удалённых локально — не восстанавливать при pull */
    private final Set<String> deletedSeriesCloudIds = ConcurrentHashMap.newKeySet();
    private final Set<String> deletedCollectionCloudIds = ConcurrentHashMap.newKeySet();
    private final PendingDeletionsStore pendingDeletes;

    public interface Callback {
        void onDone(boolean ok, @androidx.annotation.Nullable String error);
    }

    private SyncEngine(Context context) {
        this.appContext = context.getApplicationContext();
        this.api = new SupabaseApi(appContext);
        this.dao = SeriesDatabase.getDatabase(appContext).seriesDao();
        this.pendingDeletes = new PendingDeletionsStore(appContext);
        deletedSeriesCloudIds.addAll(pendingDeletes.getSeriesIds());
        deletedCollectionCloudIds.addAll(pendingDeletes.getCollectionIds());
    }

    public static SyncEngine getInstance(Context context) {
        if (instance == null) {
            synchronized (SyncEngine.class) {
                if (instance == null) {
                    instance = new SyncEngine(context);
                }
            }
        }
        return instance;
    }

    public SupabaseApi getApi() {
        return api;
    }

    public void requestSync() {
        requestSync(null);
    }

    /** Быстрое обновление с сервера (без медиа и загрузки обложек). */
    public void requestQuickRefresh(@androidx.annotation.Nullable Callback callback) {
        quickRefresh.set(true);
        requestSyncImmediate(callback);
    }

    public void requestSyncAfterAuth(boolean signUp, @androidx.annotation.Nullable Callback callback) {
        authSignUp = signUp;
        requestSyncImmediate(callback);
    }

    public void requestSync(@androidx.annotation.Nullable Callback callback) {
        if (callback != null) {
            requestSyncImmediate(callback);
            return;
        }
        // Склеиваем частые фоновые синки (toggle/авто), чтобы UI не дёргался
        mainHandler.removeCallbacks(debouncedSyncRunnable);
        mainHandler.postDelayed(debouncedSyncRunnable, SYNC_DEBOUNCE_MS);
    }

    private void requestSyncImmediate(@androidx.annotation.Nullable Callback callback) {
        if (callback != null) {
            pendingCallback = callback;
        }
        if (!api.getSession().isLoggedIn() || !api.hasValidConfig()) {
            Callback cb = pendingCallback;
            pendingCallback = null;
            if (cb != null) {
                mainHandler.post(() -> cb.onDone(false, "Нет сессии или ключа Supabase"));
            }
            return;
        }
        pending.set(true);
        executor.execute(() -> {
            if (!syncing.compareAndSet(false, true)) {
                return;
            }
            String error = null;
            boolean ok = true;
            try {
                while (pending.compareAndSet(true, false)) {
                    if (!api.refreshIfNeeded()) {
                        throw new IOException("Сессия истекла — войдите в аккаунт снова");
                    }
                    boolean quick = quickRefresh.getAndSet(false);
                    runSyncForCurrentAccount(quick);
                }
            } catch (Exception e) {
                Log.e(TAG, "sync failed", e);
                ok = false;
                error = e.getMessage();
                quickRefresh.set(false);
            } finally {
                syncing.set(false);
                if (pending.get()) {
                    requestSyncImmediate(null);
                } else {
                    Callback cb = pendingCallback;
                    pendingCallback = null;
                    if (cb != null) {
                        boolean finalOk = ok;
                        String finalError = error;
                        mainHandler.post(() -> cb.onDone(finalOk, finalError));
                    }
                }
            }
        });
    }

    private void runSyncForCurrentAccount(boolean quick) throws IOException {
        AuthSessionStore session = api.getSession();
        String userId = session.getUserId();
        if (userId == null || userId.isEmpty()) {
            throw new IOException("Нет user id");
        }

        String localOwner = session.getLocalDataUserId();
        boolean hasLocalData = hasLocalData();
        boolean signUp = authSignUp;
        authSignUp = false;

        if (localOwner == null || localOwner.isEmpty()) {
            session.setLocalDataUserId(userId);
            localOwner = userId;
        }

        if (!localOwner.equals(userId)) {
            // Другой аккаунт — локальные данные не переносим, только облако этого аккаунта
            wipeLocalData();
            clearPendingDeletesForAccountSwitch();
            session.setLocalDataUserId(userId);
            pullAll();
            syncSettings();
            session.setMigratedForUser(userId, true);
            return;
        }

        if (signUp && hasLocalData && !session.isMigratedForUser(userId)) {
            // Первая регистрация в приложении: сериалы с телефона → в новый аккаунт
            ensureCloudIdsForLocal();
            pullAll();
            pushAll();
            pullAll();
            syncSettings();
            session.setMigratedForUser(userId, true);
            return;
        }

        if (!session.isMigratedForUser(userId)) {
            // Вход в существующий аккаунт — не смешиваем с локальной библиотекой
            wipeLocalData();
            clearPendingDeletesForAccountSwitch();
            session.setLocalDataUserId(userId);
            pullAll();
            syncSettings();
            session.setMigratedForUser(userId, true);
            return;
        }

        ensureCloudIdsForLocal();
        flushPendingDeletes();
        if (quick) {
            pullQuick();
            return;
        }
        pushAll();
        pullAll();
        syncSettings();
    }

    /** Только коллекции, сериалы и связи — без медиа и скачивания картинок. */
    private void pullQuick() throws IOException {
        skipHeavyDownloads = true;
        try {
            for (JsonObject row : api.selectAll("collections")) {
                mergeCollection(row);
            }
            for (JsonObject row : api.selectAll("series")) {
                mergeSeries(row);
            }
            purgeDeletedSeriesLocals();
            purgeDeletedCollectionLocals();
            for (JsonObject row : api.selectAll("series_collection_cross_ref")) {
                mergeCrossRef(row);
            }
        } finally {
            skipHeavyDownloads = false;
        }
    }

    private boolean hasLocalData() {
        return !dao.getAllSeriesSync().isEmpty() || !dao.getAllCollectionsSync().isEmpty();
    }

    private void wipeLocalData() {
        dao.deleteAllData();
    }

    private void clearPendingDeletesForAccountSwitch() {
        pendingDeletes.clearAll();
        deletedSeriesCloudIds.clear();
        deletedCollectionCloudIds.clear();
    }

    public void deleteSeriesRemote(String cloudId) {
        if (cloudId == null || cloudId.isEmpty()) return;
        deletedSeriesCloudIds.add(cloudId);
        pendingDeletes.addSeries(cloudId);
        executor.execute(() -> {
            try {
                if (api.getSession().isLoggedIn()) {
                    if (!api.refreshIfNeeded()) {
                        throw new IOException("Сессия истекла");
                    }
                    deleteSeriesFromCloud(cloudId);
                }
            } catch (Exception e) {
                Log.w(TAG, "remote series delete failed (will retry on sync)", e);
            }
        });
    }

    public void deleteCollectionRemote(String cloudId) {
        if (cloudId == null || cloudId.isEmpty()) return;
        deletedCollectionCloudIds.add(cloudId);
        pendingDeletes.addCollection(cloudId);
        executor.execute(() -> {
            try {
                if (api.getSession().isLoggedIn()) {
                    if (!api.refreshIfNeeded()) {
                        throw new IOException("Сессия истекла");
                    }
                    deleteCollectionFromCloud(cloudId);
                }
            } catch (Exception e) {
                Log.w(TAG, "remote collection delete failed (will retry on sync)", e);
            }
        });
    }

    /** Перед pull: дожимаем удаления в Supabase, иначе сериалы вернутся. */
    private void flushPendingDeletes() {
        for (String cloudId : new java.util.HashSet<>(pendingDeletes.getSeriesIds())) {
            deletedSeriesCloudIds.add(cloudId);
            try {
                deleteSeriesFromCloud(cloudId);
            } catch (Exception e) {
                Log.w(TAG, "pending series delete retry failed: " + cloudId, e);
            }
        }
        for (String cloudId : new java.util.HashSet<>(pendingDeletes.getCollectionIds())) {
            deletedCollectionCloudIds.add(cloudId);
            try {
                deleteCollectionFromCloud(cloudId);
            } catch (Exception e) {
                Log.w(TAG, "pending collection delete retry failed: " + cloudId, e);
            }
        }
    }

    private void deleteSeriesFromCloud(String cloudId) throws IOException {
        // Сначала дети — на случай если в облаке нет ON DELETE CASCADE
        api.deleteMatching("media_files", "series_id=eq." + cloudId);
        api.deleteMatching("series_collection_cross_ref", "series_id=eq." + cloudId);
        api.deleteById("series", cloudId);
        if (api.existsById("series", cloudId)) {
            throw new IOException("Сериал всё ещё в облаке после DELETE: " + cloudId);
        }
        pendingDeletes.removeSeries(cloudId);
        // Оставляем в tombstone на время сессии — защита от гонки; при успехе pull уже не вернёт
        Log.i(TAG, "series deleted from cloud: " + cloudId);
    }

    private void deleteCollectionFromCloud(String cloudId) throws IOException {
        api.deleteMatching("series_collection_cross_ref", "collection_id=eq." + cloudId);
        api.deleteById("collections", cloudId);
        if (api.existsById("collections", cloudId)) {
            throw new IOException("Коллекция всё ещё в облаке после DELETE: " + cloudId);
        }
        pendingDeletes.removeCollection(cloudId);
        Log.i(TAG, "collection deleted from cloud: " + cloudId);
    }

    public void deleteMediaRemote(String cloudId) {
        executor.execute(() -> {
            try {
                if (cloudId != null && api.getSession().isLoggedIn()) {
                    api.deleteById("media_files", cloudId);
                }
            } catch (Exception e) {
                Log.w(TAG, "remote media delete failed", e);
            }
        });
    }

    public void deleteCrossRefRemote(String seriesCloudId, String collectionCloudId) {
        executor.execute(() -> {
            try {
                if (seriesCloudId != null && collectionCloudId != null && api.getSession().isLoggedIn()) {
                    api.deleteCrossRef(seriesCloudId, collectionCloudId);
                }
            } catch (Exception e) {
                Log.w(TAG, "remote crossref delete failed", e);
            }
        });
    }

    private void ensureCloudIdsForLocal() {
        for (Series s : dao.getAllSeriesSync()) {
            if (s.getCloudId() == null || s.getCloudId().isEmpty()) {
                s.setCloudId(UUID.randomUUID().toString());
                s.setSyncDirty(true);
                if (s.getUpdatedAt() <= 0) s.setUpdatedAt(System.currentTimeMillis());
                dao.updateSeries(s);
            }
        }
        for (Collection c : dao.getAllCollectionsSync()) {
            if (c.getCloudId() == null || c.getCloudId().isEmpty()) {
                c.setCloudId(UUID.randomUUID().toString());
                c.setSyncDirty(true);
                if (c.getUpdatedAt() <= 0) c.setUpdatedAt(System.currentTimeMillis());
                dao.updateCollection(c);
            }
        }
        for (MediaFile m : dao.getAllMediaFilesSync()) {
            if (m.getCloudId() == null || m.getCloudId().isEmpty()) {
                m.setCloudId(UUID.randomUUID().toString());
                m.setSyncDirty(true);
                if (m.getUpdatedAt() <= 0) m.setUpdatedAt(System.currentTimeMillis());
                dao.updateMediaFile(m);
            }
        }
    }

    private void markAllDirtyForInitialUpload() {
        for (Series s : dao.getAllSeriesSync()) {
            s.setSyncDirty(true);
            dao.updateSeries(s);
        }
        for (Collection c : dao.getAllCollectionsSync()) {
            c.setSyncDirty(true);
            dao.updateCollection(c);
        }
        for (MediaFile m : dao.getAllMediaFilesSync()) {
            m.setSyncDirty(true);
            dao.updateMediaFile(m);
        }
        for (SeriesCollectionCrossRef r : dao.getAllRelationsSync()) {
            r.setSyncDirty(true);
            dao.updateRelationSyncMeta(r.getSeriesId(), r.getCollectionId(), true, System.currentTimeMillis());
        }
    }

    private void pushAll() throws IOException {
        String userId = api.getSession().getUserId();

        for (Collection c : dao.getDirtyCollections()) {
            JsonObject row = new JsonObject();
            row.addProperty("id", c.getCloudId());
            row.addProperty("user_id", userId);
            row.addProperty("name", c.getName());
            row.addProperty("created_at", toIso(c.getCreatedAt()));
            row.addProperty("is_favorite", c.isFavorite());
            row.add("colors", toJsonColors(c.getColors()));
            row.addProperty("updated_at", toIso(c.getUpdatedAt()));
            api.upsert("collections", row);
            dao.updateCollectionSyncMeta(c.getId(), c.getCloudId(), false, c.getUpdatedAt());
        }

        for (Series s : dao.getDirtySeries()) {
            String imagePath = s.getCloudImagePath();
            boolean hasLocalCover = s.getImageUri() != null && !s.getImageUri().isEmpty();
            boolean needCoverUpload = hasLocalCover && (imagePath == null || imagePath.isEmpty());
            if (needCoverUpload) {
                try {
                    String ext = guessExt(s.getImageUri(), "jpg");
                    String path = userId + "/" + s.getCloudId() + "/cover." + ext;
                    api.uploadFile(path, Uri.parse(s.getImageUri()), "image/*");
                    imagePath = path;
                    s.setCloudImagePath(imagePath);
                } catch (Exception e) {
                    Log.w(TAG, "cover upload skipped: " + s.getTitle() + " — " + e.getMessage());
                }
            } else if (!hasLocalCover) {
                imagePath = null;
                s.setCloudImagePath(null);
            }
            JsonObject row = new JsonObject();
            row.addProperty("id", s.getCloudId());
            row.addProperty("user_id", userId);
            row.addProperty("title", s.getTitle());
            if (imagePath != null) row.addProperty("image_url", imagePath);
            else row.add("image_url", JsonNull.INSTANCE);
            row.addProperty("is_watched", s.getIsWatched());
            row.addProperty("notes", s.getNotes());
            row.addProperty("watch_url", s.getWatchUrl());
            row.addProperty("watch_at", s.getWatchAt());
            row.addProperty("created_at", toIso(s.getCreatedAt()));
            row.addProperty("description", s.getDescription());
            row.addProperty("status", s.getStatus() != null ? s.getStatus() : "planned");
            row.addProperty("is_favorite", s.getIsFavorite());
            row.addProperty("rating", s.getRating());
            row.addProperty("genre", s.getGenre());
            row.addProperty("seasons", s.getSeasons());
            row.addProperty("episodes", s.getEpisodes());
            row.addProperty("updated_at", toIso(s.getUpdatedAt()));
            api.upsert("series", row);
            dao.updateSeriesSyncMeta(s.getId(), s.getCloudId(), false, s.getUpdatedAt(), imagePath);
        }

        for (SeriesCollectionCrossRef r : dao.getDirtyRelations()) {
            Series s = dao.getSeriesByIdSync(r.getSeriesId());
            Collection c = null;
            for (Collection col : dao.getAllCollectionsSync()) {
                if (col.getId() == r.getCollectionId()) {
                    c = col;
                    break;
                }
            }
            if (s == null || c == null || s.getCloudId() == null || c.getCloudId() == null) continue;
            JsonObject row = new JsonObject();
            row.addProperty("series_id", s.getCloudId());
            row.addProperty("collection_id", c.getCloudId());
            row.addProperty("user_id", userId);
            row.addProperty("is_watched", r.getIsWatched());
            row.addProperty("updated_at", toIso(r.getUpdatedAt() > 0 ? r.getUpdatedAt() : System.currentTimeMillis()));
            api.upsert("series_collection_cross_ref", row);
            dao.updateRelationSyncMeta(r.getSeriesId(), r.getCollectionId(), false, r.getUpdatedAt());
        }

        for (MediaFile m : dao.getDirtyMediaFiles()) {
            Series s = dao.getSeriesByIdSync(m.getSeriesId());
            if (s == null || s.getCloudId() == null) continue;
            String storagePath = m.getStoragePath();
            if ((storagePath == null || storagePath.isEmpty())) {
                Uri uri = null;
                if (m.getFileUri() != null) uri = Uri.parse(m.getFileUri());
                else if (m.getFilePath() != null) uri = Uri.fromFile(new File(m.getFilePath()));
                if (uri != null) {
                    String safe = m.getFileName() != null ? m.getFileName().replaceAll("[^a-zA-Z0-9._-]", "_") : "file";
                    storagePath = userId + "/" + s.getCloudId() + "/" + m.getCloudId() + "_" + safe;
                    String type = m.getFileType() != null && m.getFileType().equals("video")
                            ? "video/*" : "image/*";
                    try {
                        api.uploadFile(storagePath, uri, type);
                        m.setStoragePath(storagePath);
                    } catch (OutOfMemoryError oom) {
                        Log.e(TAG, "OOM uploading media id=" + m.getId(), oom);
                        dao.updateMediaSyncMeta(m.getId(), m.getCloudId(), false, m.getUpdatedAt(), m.getStoragePath());
                        continue;
                    } catch (Exception e) {
                        Log.w(TAG, "media upload skipped id=" + m.getId() + ": " + e.getMessage());
                        // Слишком большой / битый файл — не ретраим бесконечно
                        if (e.getMessage() != null && e.getMessage().contains("слишком большой")) {
                            dao.updateMediaSyncMeta(m.getId(), m.getCloudId(), false, m.getUpdatedAt(), null);
                        }
                        continue;
                    }
                } else {
                    continue;
                }
            }
            JsonObject row = new JsonObject();
            row.addProperty("id", m.getCloudId());
            row.addProperty("user_id", userId);
            row.addProperty("series_id", s.getCloudId());
            row.addProperty("storage_path", storagePath);
            row.addProperty("file_type", m.getFileType() != null ? m.getFileType() : "image");
            row.addProperty("file_name", m.getFileName());
            row.addProperty("file_size", m.getFileSize());
            row.addProperty("created_at", toIso(m.getCreatedAt()));
            row.addProperty("description", m.getDescription());
            row.addProperty("updated_at", toIso(m.getUpdatedAt()));
            api.upsert("media_files", row);
            dao.updateMediaSyncMeta(m.getId(), m.getCloudId(), false, m.getUpdatedAt(), storagePath);
        }
    }

    private void pullAll() throws IOException {
        for (JsonObject row : api.selectAll("collections")) {
            mergeCollection(row);
        }
        for (JsonObject row : api.selectAll("series")) {
            mergeSeries(row);
        }
        // Убираем локальные копии, которые удалили, но sync успел вернуть
        purgeDeletedSeriesLocals();
        purgeDeletedCollectionLocals();
        for (JsonObject row : api.selectAll("series_collection_cross_ref")) {
            mergeCrossRef(row);
        }
        for (JsonObject row : api.selectAll("media_files")) {
            mergeMedia(row);
        }
    }

    private void purgeDeletedSeriesLocals() {
        for (String cloudId : deletedSeriesCloudIds) {
            Series local = dao.getSeriesByCloudId(cloudId);
            if (local != null) {
                dao.deleteSeries(local.getId());
            }
        }
    }

    private void purgeDeletedCollectionLocals() {
        for (String cloudId : deletedCollectionCloudIds) {
            Collection local = dao.getCollectionByCloudId(cloudId);
            if (local != null) {
                dao.deleteCollection(local.getId());
            }
        }
    }

    private void syncSettings() throws IOException {
        String local = WatchSearchSitesStore.getSitesText(appContext);
        String remote = api.loadSettingsSites();
        if (remote == null) {
            api.upsertSettings(local);
        } else if (!remote.equals(local)) {
            // Prefer non-empty newer: if local empty take remote; else push local then ok
            if (local == null || local.trim().isEmpty()) {
                WatchSearchSitesStore.setSitesText(appContext, remote);
            } else {
                api.upsertSettings(local);
            }
        }
    }

    private void mergeCollection(JsonObject row) {
        String cloudId = str(row, "id");
        if (cloudId != null && deletedCollectionCloudIds.contains(cloudId)) return;
        long remoteUpdated = parseIso(str(row, "updated_at"));
        Collection local = dao.getCollectionByCloudId(cloudId);
        if (local != null) {
            if (local.getSyncDirty() && local.getUpdatedAt() >= remoteUpdated) return;
            local.setName(str(row, "name"));
            local.setFavorite(bool(row, "is_favorite"));
            local.setColors(parseColors(row.get("colors")));
            local.setCreatedAt(parseIso(str(row, "created_at")));
            local.setUpdatedAt(remoteUpdated);
            local.setSyncDirty(false);
            dao.updateCollection(local);
        } else {
            Collection c = new Collection(str(row, "name"));
            c.setCloudId(cloudId);
            c.setFavorite(bool(row, "is_favorite"));
            c.setColors(parseColors(row.get("colors")));
            c.setCreatedAt(parseIso(str(row, "created_at")));
            c.setUpdatedAt(remoteUpdated);
            c.setSyncDirty(false);
            dao.insertCollectionSync(c);
        }
    }

    private void mergeSeries(JsonObject row) throws IOException {
        String cloudId = str(row, "id");
        if (cloudId != null && deletedSeriesCloudIds.contains(cloudId)) return;
        long remoteUpdated = parseIso(str(row, "updated_at"));
        Series local = dao.getSeriesByCloudId(cloudId);
        String imageUrl = str(row, "image_url");

        if (local != null) {
            if (local.getSyncDirty() && local.getUpdatedAt() >= remoteUpdated) return;
            applySeriesFields(local, row);
            local.setUpdatedAt(remoteUpdated);
            local.setSyncDirty(false);
            local.setCloudImagePath(imageUrl);
            if (!skipHeavyDownloads
                    && imageUrl != null
                    && (local.getImageUri() == null || local.getImageUri().isEmpty())) {
                local.setImageUri(downloadToCache(imageUrl, cloudId + "_cover"));
            }
            dao.updateSeries(local);
        } else {
            // title unique — if same title exists without cloudId, attach
            Series byTitle = dao.getSeriesByTitleIgnoreCase(str(row, "title"));
            if (byTitle != null && (byTitle.getCloudId() == null || byTitle.getCloudId().isEmpty())) {
                applySeriesFields(byTitle, row);
                byTitle.setCloudId(cloudId);
                byTitle.setUpdatedAt(remoteUpdated);
                byTitle.setSyncDirty(false);
                byTitle.setCloudImagePath(imageUrl);
                if (!skipHeavyDownloads
                        && imageUrl != null
                        && (byTitle.getImageUri() == null || byTitle.getImageUri().isEmpty())) {
                    byTitle.setImageUri(downloadToCache(imageUrl, cloudId + "_cover"));
                }
                dao.updateSeries(byTitle);
            } else if (byTitle == null) {
                Series s = new Series(str(row, "title"));
                applySeriesFields(s, row);
                s.setCloudId(cloudId);
                s.setUpdatedAt(remoteUpdated);
                s.setSyncDirty(false);
                s.setCloudImagePath(imageUrl);
                if (!skipHeavyDownloads && imageUrl != null) {
                    s.setImageUri(downloadToCache(imageUrl, cloudId + "_cover"));
                }
                dao.insertSeriesSync(s);
            }
        }
    }

    private void applySeriesFields(Series s, JsonObject row) {
        s.setTitle(str(row, "title"));
        s.setIsWatched(bool(row, "is_watched"));
        s.setNotes(str(row, "notes"));
        s.setWatchUrl(str(row, "watch_url"));
        s.setWatchAt(str(row, "watch_at"));
        s.setDescription(str(row, "description"));
        s.setStatus(str(row, "status") != null ? str(row, "status") : "planned");
        s.setIsFavorite(bool(row, "is_favorite"));
        s.setRating(row.has("rating") && !row.get("rating").isJsonNull() ? row.get("rating").getAsInt() : 0);
        s.setGenre(str(row, "genre"));
        s.setSeasons(row.has("seasons") && !row.get("seasons").isJsonNull() ? row.get("seasons").getAsInt() : 0);
        s.setEpisodes(row.has("episodes") && !row.get("episodes").isJsonNull() ? row.get("episodes").getAsInt() : 0);
        long created = parseIso(str(row, "created_at"));
        if (created > 0) s.setCreatedAt(created);
    }

    private void mergeCrossRef(JsonObject row) {
        String seriesCloud = str(row, "series_id");
        String collectionCloud = str(row, "collection_id");
        if (seriesCloud != null && deletedSeriesCloudIds.contains(seriesCloud)) return;
        if (collectionCloud != null && deletedCollectionCloudIds.contains(collectionCloud)) return;
        Series s = dao.getSeriesByCloudId(seriesCloud);
        Collection c = dao.getCollectionByCloudId(collectionCloud);
        if (s == null || c == null) return;
        SeriesCollectionCrossRef existing = dao.getCrossRef(s.getId(), c.getId());
        long remoteUpdated = parseIso(str(row, "updated_at"));
        if (existing != null) {
            if (existing.getSyncDirty() && existing.getUpdatedAt() >= remoteUpdated) return;
            existing.setIsWatched(bool(row, "is_watched"));
            existing.setUpdatedAt(remoteUpdated);
            existing.setSyncDirty(false);
            dao.updateRelationSyncMeta(s.getId(), c.getId(), false, remoteUpdated);
            // isWatched update via query already; also need watched field - use insert replace
            dao.deleteSeriesCollectionCrossRef(s.getId(), c.getId());
            SeriesCollectionCrossRef fresh = new SeriesCollectionCrossRef(s.getId(), c.getId());
            fresh.setIsWatched(bool(row, "is_watched"));
            fresh.setUpdatedAt(remoteUpdated);
            fresh.setSyncDirty(false);
            dao.insertCrossRefSync(fresh);
        } else {
            SeriesCollectionCrossRef ref = new SeriesCollectionCrossRef(s.getId(), c.getId());
            ref.setIsWatched(bool(row, "is_watched"));
            ref.setUpdatedAt(remoteUpdated);
            ref.setSyncDirty(false);
            dao.insertCrossRefSync(ref);
        }
    }

    private void mergeMedia(JsonObject row) throws IOException {
        String cloudId = str(row, "id");
        String seriesCloud = str(row, "series_id");
        if (seriesCloud != null && deletedSeriesCloudIds.contains(seriesCloud)) return;
        long remoteUpdated = parseIso(str(row, "updated_at"));
        MediaFile local = dao.getMediaFileByCloudId(cloudId);
        Series s = dao.getSeriesByCloudId(seriesCloud);
        if (s == null) return;
        String storagePath = str(row, "storage_path");

        if (local != null) {
            if (local.getSyncDirty() && local.getUpdatedAt() >= remoteUpdated) return;
            local.setFileType(str(row, "file_type"));
            local.setFileName(str(row, "file_name"));
            local.setFileSize(row.has("file_size") && !row.get("file_size").isJsonNull()
                    ? row.get("file_size").getAsLong() : 0);
            local.setDescription(str(row, "description"));
            local.setStoragePath(storagePath);
            local.setUpdatedAt(remoteUpdated);
            local.setSyncDirty(false);
            if (storagePath != null && (local.getFileUri() == null || local.getFileUri().isEmpty())) {
                String path = downloadToCache(storagePath, cloudId);
                local.setFileUri(path);
                local.setFilePath(path != null && path.startsWith("file:") ? Uri.parse(path).getPath() : path);
            }
            dao.updateMediaFile(local);
        } else {
            MediaFile m = new MediaFile();
            m.setCloudId(cloudId);
            m.setSeriesId(s.getId());
            m.setFileType(str(row, "file_type"));
            m.setFileName(str(row, "file_name"));
            m.setFileSize(row.has("file_size") && !row.get("file_size").isJsonNull()
                    ? row.get("file_size").getAsLong() : 0);
            m.setDescription(str(row, "description"));
            m.setStoragePath(storagePath);
            m.setCreatedAt(parseIso(str(row, "created_at")));
            m.setUpdatedAt(remoteUpdated);
            m.setSyncDirty(false);
            if (storagePath != null) {
                String path = downloadToCache(storagePath, cloudId);
                m.setFileUri(path);
                m.setFilePath(path != null && path.startsWith("file:") ? Uri.parse(path).getPath() : path);
            }
            dao.insertMediaFile(m);
        }
    }

    @androidx.annotation.Nullable
    private String downloadToCache(String storagePath, String nameHint) {
        try {
            File dir = new File(appContext.getFilesDir(), "cloud_cache");
            if (!dir.exists()) dir.mkdirs();
            String ext = guessExt(storagePath, "bin");
            File out = new File(dir, nameHint + "." + ext);
            api.downloadFileTo(storagePath, out);
            return Uri.fromFile(out).toString();
        } catch (Exception e) {
            Log.w(TAG, "download failed " + storagePath, e);
            return null;
        }
    }

    private static JsonArray toJsonColors(List<String> colors) {
        JsonArray arr = new JsonArray();
        if (colors != null) {
            for (String c : colors) arr.add(c);
        } else {
            arr.add("#2196F3");
        }
        return arr;
    }

    private static List<String> parseColors(JsonElement el) {
        java.util.ArrayList<String> list = new java.util.ArrayList<>();
        if (el == null || el.isJsonNull()) {
            list.add("#2196F3");
            return list;
        }
        if (el.isJsonArray()) {
            for (JsonElement e : el.getAsJsonArray()) list.add(e.getAsString());
        } else if (el.isJsonPrimitive()) {
            list.add(el.getAsString());
        }
        if (list.isEmpty()) list.add("#2196F3");
        return list;
    }

    private static String str(JsonObject o, String key) {
        if (!o.has(key) || o.get(key).isJsonNull()) return null;
        return o.get(key).getAsString();
    }

    private static boolean bool(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() && o.get(key).getAsBoolean();
    }

    private static String toIso(long millis) {
        if (millis <= 0) millis = System.currentTimeMillis();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        return sdf.format(millis);
    }

    private static long parseIso(String iso) {
        if (iso == null || iso.isEmpty()) return 0;
        String[] patterns = {
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                "yyyy-MM-dd'T'HH:mm:ss'Z'",
                "yyyy-MM-dd'T'HH:mm:ss.SSSSSSXXX",
                "yyyy-MM-dd'T'HH:mm:ssXXX"
        };
        for (String p : patterns) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat(p, Locale.US);
                sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
                return sdf.parse(iso).getTime();
            } catch (ParseException ignored) {
            }
        }
        try {
            // fallback: strip timezone fraction
            String cleaned = iso.replace("Z", "");
            if (cleaned.contains("+")) cleaned = cleaned.substring(0, cleaned.indexOf('+'));
            if (cleaned.contains(".")) {
                int dot = cleaned.indexOf('.');
                if (cleaned.length() > dot + 4) {
                    cleaned = cleaned.substring(0, Math.min(cleaned.length(), dot + 4));
                }
            }
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.US);
            sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
            return sdf.parse(cleaned).getTime();
        } catch (Exception e) {
            return System.currentTimeMillis();
        }
    }

    private static String guessExt(String path, String fallback) {
        if (path == null) return fallback;
        int q = path.indexOf('?');
        if (q >= 0) path = path.substring(0, q);
        int dot = path.lastIndexOf('.');
        if (dot >= 0 && dot < path.length() - 1) {
            String ext = path.substring(dot + 1);
            if (ext.length() <= 5) return ext;
        }
        return fallback;
    }
}
