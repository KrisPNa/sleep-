package com.example.seriestracker;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import android.os.Bundle;

// Если MainScreen в другом пакете
import com.example.seriestracker.ui.screens.MainScreen;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // ВАЖНО: устанавливаем тему до setContentView
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Загружаем главный фрагмент
        if (savedInstanceState == null) {
            // Используем полное имя класса или импорт
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, new MainScreen())
                    .commit();
        }
    }

}