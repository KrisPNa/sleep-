package com.example.seriestracker;

import android.app.Application;
import android.content.SharedPreferences;

import com.example.seriestracker.data.backup.AutoBackupManager;
import com.example.seriestracker.data.repository.SeriesRepository;

public class SeriesTrackerApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        // Инициализация репозитория через синглтон или прямое создание
        SeriesRepository repository = initializeRepository();

        // Инициализация менеджера бэкапов
        AutoBackupManager backupManager = AutoBackupManager.getInstance(this, repository);

        // Проверяем наличие бэкапов при запуске приложения
        checkForBackupsOnStartup(backupManager, repository);
    }

    private SeriesRepository initializeRepository() {
        // Вариант 1: Если SeriesRepository имеет публичный конструктор
        try {
            return new SeriesRepository(this);
        } catch (Exception e) {
            // Вариант 2: Если конструктор приватный, используем статический метод
            try {
                // Проверяем, есть ли статический метод getInstance
                java.lang.reflect.Method method = SeriesRepository.class.getMethod("getInstance", Application.class);
                return (SeriesRepository) method.invoke(null, this);
            } catch (Exception ex) {
                // Вариант 3: Если нет подходящего метода, возвращаем null
                return null;
            }
        }
    }

    private void checkForBackupsOnStartup(AutoBackupManager backupManager, SeriesRepository repository) {
        if (repository == null || backupManager == null) {
            return; // Пропускаем проверку, если не удалось инициализировать
        }

        new Thread(() -> {
            try {
                Thread.sleep(3000); // Ждем инициализации

                // Проверяем, есть ли данные в БД
                if (repository.getAllCollectionsSync() == null ||
                        repository.getAllCollectionsSync().isEmpty()) {

                    // База пуста, проверяем наличие бэкапов
                    if (backupManager.hasBackups()) {
                        // Можно показать уведомление или диалог
                        // но лучше оставить это на экране резервного копирования
                    }
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }
}