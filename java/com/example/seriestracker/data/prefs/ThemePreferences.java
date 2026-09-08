package com.example.seriestracker.data.prefs;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

/** Светлая / тёмная тема приложения. */
public final class ThemePreferences {
    private static final String PREFS = "app_appearance";
    private static final String KEY_DARK = "dark_theme";

    private ThemePreferences() {
    }

    public static boolean isDark(Context context) {
        return prefs(context).getBoolean(KEY_DARK, false);
    }

    public static void setDark(Context context, boolean dark) {
        prefs(context).edit().putBoolean(KEY_DARK, dark).apply();
        int mode = dark
                ? AppCompatDelegate.MODE_NIGHT_YES
                : AppCompatDelegate.MODE_NIGHT_NO;
        AppCompatDelegate.setDefaultNightMode(mode);

        Activity activity = unwrapActivity(context);
        if (activity instanceof AppCompatActivity) {
            ((AppCompatActivity) activity).getDelegate().setLocalNightMode(mode);
        } else if (activity != null) {
            activity.recreate();
        }
    }

    public static void apply(Context context) {
        int mode = isDark(context)
                ? AppCompatDelegate.MODE_NIGHT_YES
                : AppCompatDelegate.MODE_NIGHT_NO;
        AppCompatDelegate.setDefaultNightMode(mode);
    }

    private static Activity unwrapActivity(Context context) {
        Context current = context;
        while (current instanceof android.content.ContextWrapper) {
            if (current instanceof Activity) {
                return (Activity) current;
            }
            current = ((android.content.ContextWrapper) current).getBaseContext();
        }
        return null;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
