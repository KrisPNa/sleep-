package com.example.seriestracker.data.sync;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** Очередь удалённых cloudId, пока не подтверждено удаление из Supabase. */
public final class PendingDeletionsStore {
    private static final String PREFS = "sync_pending_deletes";
    private static final String KEY_SERIES = "series";
    private static final String KEY_COLLECTIONS = "collections";

    private final SharedPreferences prefs;

    public PendingDeletionsStore(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public Set<String> getSeriesIds() {
        return read(KEY_SERIES);
    }

    public Set<String> getCollectionIds() {
        return read(KEY_COLLECTIONS);
    }

    public void addSeries(String cloudId) {
        add(KEY_SERIES, cloudId);
    }

    public void removeSeries(String cloudId) {
        remove(KEY_SERIES, cloudId);
    }

    public void addCollection(String cloudId) {
        add(KEY_COLLECTIONS, cloudId);
    }

    public void removeCollection(String cloudId) {
        remove(KEY_COLLECTIONS, cloudId);
    }

    public void clearAll() {
        prefs.edit().remove(KEY_SERIES).remove(KEY_COLLECTIONS).apply();
    }

    private Set<String> read(String key) {
        Set<String> raw = prefs.getStringSet(key, null);
        if (raw == null || raw.isEmpty()) return Collections.emptySet();
        return new HashSet<>(raw);
    }

    private void add(String key, String id) {
        if (id == null || id.isEmpty()) return;
        Set<String> next = new HashSet<>(read(key));
        if (!next.add(id)) return;
        prefs.edit().putStringSet(key, next).apply();
    }

    private void remove(String key, String id) {
        if (id == null || id.isEmpty()) return;
        Set<String> next = new HashSet<>(read(key));
        if (!next.remove(id)) return;
        if (next.isEmpty()) {
            prefs.edit().remove(key).apply();
        } else {
            prefs.edit().putStringSet(key, next).apply();
        }
    }
}
