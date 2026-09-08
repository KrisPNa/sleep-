package com.example.seriestracker;

import androidx.appcompat.app.AppCompatActivity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import com.example.seriestracker.data.prefs.ThemePreferences;
import com.example.seriestracker.data.sync.AuthSessionStore;
import com.example.seriestracker.data.sync.SyncEngine;
import com.example.seriestracker.ui.screens.AuthScreen;
import com.example.seriestracker.ui.screens.MainScreen;
import com.example.seriestracker.ui.screens.AddSeriesScreen;

public class MainActivity extends AppCompatActivity implements AuthScreen.Listener {

    @Override
    protected void attachBaseContext(Context newBase) {
        ThemePreferences.apply(newBase);
        super.attachBaseContext(newBase);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemePreferences.apply(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Intent intent = getIntent();
        String action = intent.getAction();
        String type = intent.getType();

        if (Intent.ACTION_SEND.equals(action) && "text/plain".equals(type)) {
            openShare(intent);
            return;
        }

        if (savedInstanceState == null) {
            AuthSessionStore store = new AuthSessionStore(this);
            if (store.isLoggedIn()) {
                SyncEngine.getInstance(this).requestSync();
                openMain();
            } else {
                AuthScreen auth = new AuthScreen();
                auth.setListener(this);
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, auth)
                        .commit();
            }
        }
    }

    @Override
    public void onAuthFinished(boolean loggedIn) {
        openMain();
    }

    private void openMain() {
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, new MainScreen())
                .commit();
    }

    private void openShare(Intent intent) {
        String sharedText = intent.getStringExtra(Intent.EXTRA_TEXT);
        AddSeriesScreen addSeriesScreen = new AddSeriesScreen();
        Bundle args = new Bundle();
        if (sharedText != null) {
            args.putString("shared_text", sharedText);
        }
        addSeriesScreen.setArguments(args);
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, addSeriesScreen)
                .commit();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Синк при каждом onResume убран — его делает AutoSyncController при старте
        // и фоновый debounce, иначе списки дёргаются при возврате с экрана сериала.
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        String action = intent.getAction();
        String type = intent.getType();
        if (Intent.ACTION_SEND.equals(action) && "text/plain".equals(type)) {
            openShare(intent);
        }
    }
}
