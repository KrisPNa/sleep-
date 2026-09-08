package com.example.seriestracker.utils;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.seriestracker.R;

import java.util.ArrayList;
import java.util.List;

public final class BrowserOpenHelper {

    private static final String PREFS_NAME = "browser_open_prefs";
    private static final String KEY_PREFERRED_PACKAGE = "preferred_browser_package";

    private BrowserOpenHelper() {
    }

    public static void openUrl(@NonNull Context context, @NonNull String url) {
        Uri uri = normalizeUri(url);
        if (uri == null) {
            Toast.makeText(context, R.string.invalid_watch_link, Toast.LENGTH_SHORT).show();
            return;
        }

        String preferred = getPreferredPackage(context);
        if (preferred != null && canHandle(context, uri, preferred)) {
            openWithPackage(context, uri, preferred);
            return;
        }

        openWithDefault(context, uri);
    }

    public static void showBrowserChooser(@NonNull Context context, @NonNull String url) {
        Uri uri = normalizeUri(url);
        if (uri == null) {
            Toast.makeText(context, R.string.invalid_watch_link, Toast.LENGTH_SHORT).show();
            return;
        }

        List<BrowserApp> browsers = queryBrowsers(context, uri);
        if (browsers.isEmpty()) {
            openWithDefault(context, uri);
            return;
        }

        if (browsers.size() == 1) {
            openWithPackage(context, uri, browsers.get(0).packageName);
            return;
        }

        String preferred = getPreferredPackage(context);
        final int[] selectedIndex = {0};
        for (int i = 0; i < browsers.size(); i++) {
            if (browsers.get(i).packageName.equals(preferred)) {
                selectedIndex[0] = i;
                break;
            }
        }

        View content = LayoutInflater.from(context).inflate(R.layout.dialog_browser_chooser, null, false);
        ListView listView = content.findViewById(R.id.browserListView);
        BrowserListAdapter adapter = new BrowserListAdapter(context, browsers, selectedIndex[0]);
        listView.setAdapter(adapter);
        listView.setOnItemClickListener((parent, view, position, id) -> {
            selectedIndex[0] = position;
            adapter.setSelectedIndex(position);
        });

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(R.string.open_with_browser)
                .setView(content)
                .setNegativeButton(R.string.open_browser_just_once, (d, which) -> {
                    openWithPackage(context, uri, browsers.get(selectedIndex[0]).packageName);
                })
                .setPositiveButton(R.string.open_browser_always, (d, which) -> {
                    String packageName = browsers.get(selectedIndex[0]).packageName;
                    setPreferredPackage(context, packageName);
                    openWithPackage(context, uri, packageName);
                })
                .create();
        dialog.show();

        // Ограничиваем высоту списка, чтобы диалог не уезжал за экран
        listView.post(() -> {
            int maxHeight = (int) (280 * context.getResources().getDisplayMetrics().density);
            int totalHeight = 0;
            for (int i = 0; i < adapter.getCount(); i++) {
                View item = adapter.getView(i, null, listView);
                item.measure(
                        View.MeasureSpec.makeMeasureSpec(listView.getWidth(), View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.UNSPECIFIED);
                totalHeight += item.getMeasuredHeight();
            }
            ViewGroup.LayoutParams params = listView.getLayoutParams();
            params.height = Math.min(totalHeight, maxHeight);
            listView.setLayoutParams(params);
        });
    }

    @Nullable
    private static Uri normalizeUri(@NonNull String url) {
        String trimmed = url.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (!trimmed.contains("://")) {
            trimmed = "https://" + trimmed;
        }
        Uri uri = Uri.parse(trimmed);
        String scheme = uri.getScheme();
        if (scheme == null) {
            return null;
        }
        String lower = scheme.toLowerCase();
        if (!"http".equals(lower) && !"https".equals(lower)) {
            return null;
        }
        return uri;
    }

    @NonNull
    private static List<BrowserApp> queryBrowsers(@NonNull Context context, @NonNull Uri uri) {
        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        intent.addCategory(Intent.CATEGORY_BROWSABLE);

        PackageManager pm = context.getPackageManager();
        List<ResolveInfo> resolveInfos = pm.queryIntentActivities(intent, PackageManager.MATCH_ALL);
        List<BrowserApp> browsers = new ArrayList<>();
        String selfPackage = context.getPackageName();

        for (ResolveInfo info : resolveInfos) {
            if (info.activityInfo == null) {
                continue;
            }
            String packageName = info.activityInfo.packageName;
            if (selfPackage.equals(packageName)) {
                continue;
            }
            CharSequence label = info.loadLabel(pm);
            Drawable icon = info.loadIcon(pm);
            browsers.add(new BrowserApp(
                    packageName,
                    info.activityInfo.name,
                    label != null ? label.toString() : packageName,
                    icon
            ));
        }
        return browsers;
    }

    private static boolean canHandle(@NonNull Context context, @NonNull Uri uri, @NonNull String packageName) {
        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        intent.addCategory(Intent.CATEGORY_BROWSABLE);
        intent.setPackage(packageName);
        return !context.getPackageManager()
                .queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
                .isEmpty();
    }

    private static void openWithPackage(@NonNull Context context, @NonNull Uri uri, @NonNull String packageName) {
        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        intent.addCategory(Intent.CATEGORY_BROWSABLE);
        intent.setPackage(packageName);
        if (!(context instanceof android.app.Activity)) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        try {
            context.startActivity(intent);
        } catch (Exception e) {
            clearPreferredPackage(context);
            openWithDefault(context, uri);
        }
    }

    private static void openWithDefault(@NonNull Context context, @NonNull Uri uri) {
        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        intent.addCategory(Intent.CATEGORY_BROWSABLE);
        if (!(context instanceof android.app.Activity)) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        try {
            context.startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(context, R.string.cannot_open_watch_link, Toast.LENGTH_SHORT).show();
        }
    }

    @Nullable
    private static String getPreferredPackage(@NonNull Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String value = prefs.getString(KEY_PREFERRED_PACKAGE, null);
        return value != null && !value.isEmpty() ? value : null;
    }

    private static void setPreferredPackage(@NonNull Context context, @NonNull String packageName) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_PREFERRED_PACKAGE, packageName)
                .apply();
    }

    private static void clearPreferredPackage(@NonNull Context context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_PREFERRED_PACKAGE)
                .apply();
    }

    private static final class BrowserApp {
        final String packageName;
        final String activityName;
        final String label;
        final Drawable icon;

        BrowserApp(String packageName, String activityName, String label, Drawable icon) {
            this.packageName = packageName;
            this.activityName = activityName;
            this.label = label;
            this.icon = icon;
        }
    }

    private static final class BrowserListAdapter extends BaseAdapter {
        private final Context context;
        private final List<BrowserApp> browsers;
        private int selectedIndex;

        BrowserListAdapter(Context context, List<BrowserApp> browsers, int selectedIndex) {
            this.context = context;
            this.browsers = browsers;
            this.selectedIndex = selectedIndex;
        }

        void setSelectedIndex(int index) {
            selectedIndex = index;
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return browsers.size();
        }

        @Override
        public BrowserApp getItem(int position) {
            return browsers.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view = convertView;
            if (view == null) {
                view = LayoutInflater.from(context).inflate(R.layout.item_browser_chooser, parent, false);
            }
            BrowserApp browser = browsers.get(position);
            ImageView iconView = view.findViewById(R.id.browserIcon);
            TextView nameView = view.findViewById(R.id.browserName);
            RadioButton radioButton = view.findViewById(R.id.browserRadio);

            iconView.setImageDrawable(browser.icon);
            nameView.setText(browser.label);
            radioButton.setChecked(position == selectedIndex);
            return view;
        }
    }
}
