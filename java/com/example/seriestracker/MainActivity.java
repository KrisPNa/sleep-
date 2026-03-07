
package com.example.seriestracker;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import android.content.Intent;
import android.os.Bundle;

// Если MainScreen в другом пакете
import com.example.seriestracker.ui.screens.MainScreen;
import com.example.seriestracker.ui.screens.AddSeriesScreen;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // ВАЖНО: устанавливаем тему до setContentView
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Проверяем, было ли приложение открыто через действие "Поделиться"
        Intent intent = getIntent();
        String action = intent.getAction();
        String type = intent.getType();

        if (Intent.ACTION_SEND.equals(action) && "text/plain".equals(type)) {
            // Обработка полученного текста
            String sharedText = intent.getStringExtra(Intent.EXTRA_TEXT);

            // Создаем фрагмент AddSeriesScreen и передаем текст
            AddSeriesScreen addSeriesScreen = new AddSeriesScreen();
            Bundle args = new Bundle();
            if (sharedText != null) {
                args.putString("shared_text", sharedText);
            }
            addSeriesScreen.setArguments(args);

            // Загружаем фрагмент создания сериала
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, addSeriesScreen)
                    .commit();
        } else {
            // Обычный запуск - загружаем главный экран
            if (savedInstanceState == null) {
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, new MainScreen())
                        .commit();
            }
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Обработка нового интента (если приложение уже запущено)
        setIntent(intent);

        String action = intent.getAction();
        String type = intent.getType();

        if (Intent.ACTION_SEND.equals(action) && "text/plain".equals(type)) {
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
    }

}