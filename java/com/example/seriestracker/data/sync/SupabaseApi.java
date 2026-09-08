package com.example.seriestracker.data.sync;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.Nullable;

import com.example.seriestracker.BuildConfig;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class SupabaseApi {
    private static final String TAG = "SupabaseApi";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final String baseUrl;
    private final String anonKey;
    private final AuthSessionStore session;
    private final OkHttpClient client;
    private final OkHttpClient authClient;
    private final Gson gson = new Gson();
    private final Context appContext;

    public SupabaseApi(Context context) {
        this.appContext = context.getApplicationContext();
        this.session = new AuthSessionStore(appContext);
        this.baseUrl = trimSlash(BuildConfig.SUPABASE_URL);
        this.anonKey = BuildConfig.SUPABASE_ANON_KEY;
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build();
        this.authClient = new OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(12, TimeUnit.SECONDS)
                .writeTimeout(12, TimeUnit.SECONDS)
                .build();
    }

    public AuthSessionStore getSession() {
        return session;
    }

    public boolean hasValidConfig() {
        return anonKey != null
                && !anonKey.isEmpty()
                && !anonKey.contains("REPLACE_WITH");
    }

    public void signUp(String email, String password) throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("email", email);
        body.addProperty("password", password);
        Request request = new Request.Builder()
                .url(baseUrl + "/auth/v1/signup")
                .addHeader("apikey", anonKey)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(body.toString(), JSON))
                .build();
        try (Response response = authClient.newCall(request).execute()) {
            String raw = bodyOrEmpty(response);
            if (!response.isSuccessful()) {
                throw new IOException(extractError(raw, response.code()));
            }
            applyAuthPayload(raw, email);
        }
    }

    public void signIn(String email, String password) throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("email", email);
        body.addProperty("password", password);
        Request request = new Request.Builder()
                .url(baseUrl + "/auth/v1/token?grant_type=password")
                .addHeader("apikey", anonKey)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(body.toString(), JSON))
                .build();
        try (Response response = authClient.newCall(request).execute()) {
            String raw = bodyOrEmpty(response);
            if (!response.isSuccessful()) {
                throw new IOException(extractError(raw, response.code()));
            }
            applyAuthPayload(raw, email);
        }
    }

    public void resetPassword(String email) throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("email", email);
        Request request = new Request.Builder()
                .url(baseUrl + "/auth/v1/recover")
                .addHeader("apikey", anonKey)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(body.toString(), JSON))
                .build();
        try (Response response = authClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException(extractError(bodyOrEmpty(response), response.code()));
            }
        }
    }

    /** Мгновенный выход — только локально. */
    public void signOutLocal() {
        session.clear();
    }

    /** Серверный logout в фоне; UI не ждёт. */
    public void signOutRemoteAsync(@Nullable String accessToken) {
        if (accessToken == null || accessToken.isEmpty()) return;
        new Thread(() -> {
            Request request = new Request.Builder()
                    .url(baseUrl + "/auth/v1/logout")
                    .addHeader("apikey", anonKey)
                    .addHeader("Authorization", "Bearer " + accessToken)
                    .post(RequestBody.create("", JSON))
                    .build();
            try {
                authClient.newCall(request).execute().close();
            } catch (Exception e) {
                Log.w(TAG, "logout failed", e);
            }
        }, "supabase-logout").start();
    }

    public void signOut() {
        String token = session.getAccessToken();
        signOutLocal();
        signOutRemoteAsync(token);
    }

    public boolean refreshIfNeeded() {
        String refresh = session.getRefreshToken();
        if (refresh == null || refresh.isEmpty()) return session.isLoggedIn();
        try {
            JsonObject body = new JsonObject();
            body.addProperty("refresh_token", refresh);
            Request request = new Request.Builder()
                    .url(baseUrl + "/auth/v1/token?grant_type=refresh_token")
                    .addHeader("apikey", anonKey)
                    .addHeader("Content-Type", "application/json")
                    .post(RequestBody.create(body.toString(), JSON))
                    .build();
            try (Response response = client.newCall(request).execute()) {
                String raw = bodyOrEmpty(response);
                if (!response.isSuccessful()) return false;
                applyAuthPayload(raw, session.getEmail());
                return true;
            }
        } catch (IOException e) {
            Log.w(TAG, "refresh failed", e);
            return false;
        }
    }

    public List<JsonObject> selectAll(String table) throws IOException {
        Request request = authedGet(baseUrl + "/rest/v1/" + table + "?select=*");
        try (Response response = client.newCall(request).execute()) {
            String raw = bodyOrEmpty(response);
            if (!response.isSuccessful()) throw new IOException(extractError(raw, response.code()));
            JsonArray arr = JsonParser.parseString(raw).getAsJsonArray();
            List<JsonObject> list = new ArrayList<>();
            for (JsonElement el : arr) list.add(el.getAsJsonObject());
            return list;
        }
    }

    public void upsert(String table, JsonObject row) throws IOException {
        JsonArray arr = new JsonArray();
        arr.add(row);
        Request request = new Request.Builder()
                .url(baseUrl + "/rest/v1/" + table)
                .addHeader("apikey", anonKey)
                .addHeader("Authorization", "Bearer " + requireToken())
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "resolution=merge-duplicates,return=minimal")
                .post(RequestBody.create(arr.toString(), JSON))
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException(extractError(bodyOrEmpty(response), response.code()));
            }
        }
    }

    public void deleteById(String table, String id) throws IOException {
        deleteMatching(table, "id=eq." + id);
    }

    /** Удаление по PostgREST-фильтру, напр. {@code series_id=eq.<uuid>}. */
    public void deleteMatching(String table, String filter) throws IOException {
        Request request = new Request.Builder()
                .url(baseUrl + "/rest/v1/" + table + "?" + filter)
                .addHeader("apikey", anonKey)
                .addHeader("Authorization", "Bearer " + requireToken())
                .delete()
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException(extractError(bodyOrEmpty(response), response.code()));
            }
        }
    }

    public void deleteCrossRef(String seriesId, String collectionId) throws IOException {
        deleteMatching("series_collection_cross_ref",
                "series_id=eq." + seriesId + "&collection_id=eq." + collectionId);
    }

    /** true, если строка с таким id ещё есть (с учётом RLS). */
    public boolean existsById(String table, String id) throws IOException {
        Request request = authedGet(baseUrl + "/rest/v1/" + table
                + "?id=eq." + id + "&select=id&limit=1");
        try (Response response = client.newCall(request).execute()) {
            String raw = bodyOrEmpty(response);
            if (!response.isSuccessful()) {
                throw new IOException(extractError(raw, response.code()));
            }
            JsonArray arr = JsonParser.parseString(raw).getAsJsonArray();
            return arr.size() > 0;
        }
    }

    public void upsertSettings(String watchSearchSites) throws IOException {
        JsonObject row = new JsonObject();
        row.addProperty("user_id", session.getUserId());
        row.addProperty("watch_search_sites", watchSearchSites == null ? "" : watchSearchSites);
        upsert("user_settings", row);
    }

    @Nullable
    public String loadSettingsSites() throws IOException {
        String uid = session.getUserId();
        Request request = authedGet(baseUrl + "/rest/v1/user_settings?user_id=eq." + uid + "&select=*");
        try (Response response = client.newCall(request).execute()) {
            String raw = bodyOrEmpty(response);
            if (!response.isSuccessful()) throw new IOException(extractError(raw, response.code()));
            JsonArray arr = JsonParser.parseString(raw).getAsJsonArray();
            if (arr.size() == 0) return null;
            JsonObject obj = arr.get(0).getAsJsonObject();
            return obj.has("watch_search_sites") && !obj.get("watch_search_sites").isJsonNull()
                    ? obj.get("watch_search_sites").getAsString()
                    : "";
        }
    }

    /** Max upload size (~80MB) to avoid OOM / endless uploads of huge videos. */
    public static final long MAX_UPLOAD_BYTES = 80L * 1024L * 1024L;

    public String uploadFile(String storagePath, Uri uri, String contentType) throws IOException {
        File file = resolveToUploadFile(uri);
        try {
            return uploadFile(storagePath, file, contentType);
        } finally {
            // delete only temp copies we created
            if (file != null && file.getName().startsWith("upload_tmp_")) {
                // kept in cache dir; best-effort cleanup
                //noinspection ResultOfMethodCallIgnored
                file.delete();
            }
        }
    }

    public String uploadFile(String storagePath, File file, String contentType) throws IOException {
        if (file == null || !file.exists() || !file.isFile()) {
            throw new IOException("Файл не найден");
        }
        long size = file.length();
        if (size <= 0) {
            throw new IOException("Пустой файл");
        }
        if (size > MAX_UPLOAD_BYTES) {
            throw new IOException("Файл слишком большой для облака (" + (size / (1024 * 1024)) + " МБ, лимит 80 МБ)");
        }
        MediaType mediaType = MediaType.parse(contentType != null ? contentType : "application/octet-stream");
        RequestBody body = RequestBody.create(file, mediaType);
        Request request = new Request.Builder()
                .url(baseUrl + "/storage/v1/object/media/" + storagePath)
                .addHeader("apikey", anonKey)
                .addHeader("Authorization", "Bearer " + requireToken())
                .addHeader("x-upsert", "true")
                .put(body)
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException(extractError(bodyOrEmpty(response), response.code()));
            }
        }
        return storagePath;
    }

    public void downloadFileTo(String storagePath, File destination) throws IOException {
        Request request = new Request.Builder()
                .url(baseUrl + "/storage/v1/object/media/" + storagePath)
                .addHeader("apikey", anonKey)
                .addHeader("Authorization", "Bearer " + requireToken())
                .get()
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException(extractError(bodyOrEmpty(response), response.code()));
            }
            ResponseBody body = response.body();
            if (body == null) throw new IOException("empty body");
            try (InputStream in = body.byteStream();
                 FileOutputStream out = new FileOutputStream(destination)) {
                byte[] buffer = new byte[8192];
                long total = 0;
                int n;
                while ((n = in.read(buffer)) >= 0) {
                    total += n;
                    if (total > MAX_UPLOAD_BYTES) {
                        throw new IOException("Скачиваемый файл слишком большой");
                    }
                    out.write(buffer, 0, n);
                }
            }
        }
    }

    /** @deprecated Prefer {@link #downloadFileTo(String, File)} to avoid OOM. */
    public byte[] downloadFile(String storagePath) throws IOException {
        File tmp = File.createTempFile("dl_", ".bin", appContext.getCacheDir());
        try {
            downloadFileTo(storagePath, tmp);
            if (tmp.length() > MAX_UPLOAD_BYTES) {
                throw new IOException("Файл слишком большой");
            }
            return java.nio.file.Files.readAllBytes(tmp.toPath());
        } finally {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
        }
    }

    private File resolveToUploadFile(Uri uri) throws IOException {
        if (uri == null) throw new IOException("uri is null");
        if ("file".equals(uri.getScheme()) && uri.getPath() != null) {
            return new File(uri.getPath());
        }
        // content:// or other — stream copy to temp (not whole RAM)
        File tmp = new File(appContext.getCacheDir(), "upload_tmp_" + System.currentTimeMillis());
        try (InputStream in = appContext.getContentResolver().openInputStream(uri);
             FileOutputStream out = new FileOutputStream(tmp)) {
            if (in == null) throw new IOException("Cannot open " + uri);
            byte[] buffer = new byte[8192];
            long total = 0;
            int n;
            while ((n = in.read(buffer)) >= 0) {
                total += n;
                if (total > MAX_UPLOAD_BYTES) {
                    //noinspection ResultOfMethodCallIgnored
                    tmp.delete();
                    throw new IOException("Файл слишком большой для облака (лимит 80 МБ)");
                }
                out.write(buffer, 0, n);
            }
        }
        return tmp;
    }

    private Request authedGet(String url) {
        return new Request.Builder()
                .url(url)
                .addHeader("apikey", anonKey)
                .addHeader("Authorization", "Bearer " + requireToken())
                .get()
                .build();
    }

    private String requireToken() {
        String token = session.getAccessToken();
        if (token == null || token.isEmpty()) {
            throw new IllegalStateException("Не выполнен вход");
        }
        return token;
    }

    private void applyAuthPayload(String raw, @Nullable String fallbackEmail) {
        JsonObject root = JsonParser.parseString(raw).getAsJsonObject();
        String access = root.has("access_token") ? root.get("access_token").getAsString() : null;
        String refresh = root.has("refresh_token") ? root.get("refresh_token").getAsString() : null;
        String email = fallbackEmail;
        String userId = null;
        if (root.has("user") && root.get("user").isJsonObject()) {
            JsonObject user = root.getAsJsonObject("user");
            if (user.has("id")) userId = user.get("id").getAsString();
            if (user.has("email")) email = user.get("email").getAsString();
        }
        if (access != null && userId != null) {
            session.saveSession(access, refresh, userId, email);
        } else if (root.has("id")) {
            // signup sometimes returns user object at root without tokens
            Log.i(TAG, "Signup created user; sign-in may be required");
        }
    }

    private static String bodyOrEmpty(Response response) throws IOException {
        ResponseBody body = response.body();
        return body == null ? "" : body.string();
    }

    private static String extractError(String raw, int code) {
        try {
            JsonObject obj = JsonParser.parseString(raw).getAsJsonObject();
            if (obj.has("msg")) return obj.get("msg").getAsString();
            if (obj.has("error_description")) return obj.get("error_description").getAsString();
            if (obj.has("message")) return obj.get("message").getAsString();
            if (obj.has("error")) return obj.get("error").getAsString();
        } catch (Exception ignored) {
        }
        return "HTTP " + code + ": " + raw;
    }

    private static String trimSlash(String url) {
        if (url == null) return "";
        while (url.endsWith("/")) url = url.substring(0, url.length() - 1);
        return url;
    }
}
