package com.example.seriestracker.data.sync;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;

public final class AuthSessionStore {
    private static final String PREFS = "supabase_auth";
    private static final String KEY_ACCESS = "access_token";
    private static final String KEY_REFRESH = "refresh_token";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_EMAIL = "email";
    private static final String KEY_LOCAL_DATA_USER_ID = "local_data_user_id";
    private static final String KEY_MIGRATED_PREFIX = "migrated_";

    private final SharedPreferences prefs;

    public AuthSessionStore(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public void saveSession(String accessToken, String refreshToken, String userId, String email) {
        prefs.edit()
                .putString(KEY_ACCESS, accessToken)
                .putString(KEY_REFRESH, refreshToken)
                .putString(KEY_USER_ID, userId)
                .putString(KEY_EMAIL, email)
                .apply();
    }

    /** Выход: только токены. Локальная база и привязка к аккаунту остаются. */
    public void clear() {
        prefs.edit()
                .remove(KEY_ACCESS)
                .remove(KEY_REFRESH)
                .remove(KEY_USER_ID)
                .remove(KEY_EMAIL)
                .apply();
    }

    public boolean isLoggedIn() {
        String token = getAccessToken();
        return token != null && !token.isEmpty();
    }

    @Nullable
    public String getAccessToken() {
        return prefs.getString(KEY_ACCESS, null);
    }

    @Nullable
    public String getRefreshToken() {
        return prefs.getString(KEY_REFRESH, null);
    }

    @Nullable
    public String getUserId() {
        return prefs.getString(KEY_USER_ID, null);
    }

    @Nullable
    public String getEmail() {
        return prefs.getString(KEY_EMAIL, null);
    }

    /** Какому облачному аккаунту принадлежат данные в Room на этом устройстве. */
    @Nullable
    public String getLocalDataUserId() {
        return prefs.getString(KEY_LOCAL_DATA_USER_ID, null);
    }

    public void setLocalDataUserId(@Nullable String userId) {
        if (userId == null || userId.isEmpty()) {
            prefs.edit().remove(KEY_LOCAL_DATA_USER_ID).apply();
        } else {
            prefs.edit().putString(KEY_LOCAL_DATA_USER_ID, userId).apply();
        }
    }

    public boolean isMigratedForUser(@Nullable String userId) {
        if (userId == null || userId.isEmpty()) return false;
        return prefs.getBoolean(KEY_MIGRATED_PREFIX + userId, false);
    }

    public void setMigratedForUser(@Nullable String userId, boolean value) {
        if (userId == null || userId.isEmpty()) return;
        prefs.edit().putBoolean(KEY_MIGRATED_PREFIX + userId, value).apply();
    }

    /** @deprecated используй {@link #isMigratedForUser(String)} */
    @Deprecated
    public boolean isInitialMigrated() {
        return isMigratedForUser(getUserId());
    }

    /** @deprecated используй {@link #setMigratedForUser(String, boolean)} */
    @Deprecated
    public void setInitialMigrated(boolean value) {
        setMigratedForUser(getUserId(), value);
    }
}

