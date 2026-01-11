package com.example.seriestracker.ui.screens;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.seriestracker.R;
import com.example.seriestracker.data.backup.AutoBackupManager;
import com.example.seriestracker.data.repository.SeriesRepository;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class BackupSettingsScreen extends Fragment {

    private AutoBackupManager backupManager;
    private ProgressBar progressBar;
    private TextView progressTextView;
    private TextView lastBackupTextView;
    private TextView backupCountTextView;
    private TextView backupLocationTextView;
    private Switch autoBackupSwitch;
    private Button restoreButton;
    private Button manualBackupButton;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            SeriesRepository repository = new SeriesRepository(requireActivity().getApplication());
            backupManager = AutoBackupManager.getInstance(requireContext(), repository);

            // Проверяем наличие бэкапов при первом открытии
            checkForExistingBackups();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void checkForExistingBackups() {
        if (backupManager != null && backupManager.hasBackups()) {
            // Показываем диалог о восстановлении при первом запуске
            showRestorePrompt();
        }
    }

    private void showRestorePrompt() {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("Найдены резервные копии");
        builder.setMessage("Обнаружены сохраненные данные. Хотите восстановить их сейчас?");

        builder.setPositiveButton("Восстановить", (dialog, which) -> {
            restoreFromLatestBackup();
        });

        builder.setNegativeButton("Позже", (dialog, which) -> {
            dialog.dismiss();
        });

        builder.setNeutralButton("Не показывать снова", (dialog, which) -> {
            // Сохраняем настройку
        });

        builder.show();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_backup_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Находим все View
        ImageButton backButton = view.findViewById(R.id.backButton);
        lastBackupTextView = view.findViewById(R.id.lastBackupTextView);
        backupCountTextView = view.findViewById(R.id.backupCountTextView);
        backupLocationTextView = view.findViewById(R.id.backupLocationTextView);
        progressBar = view.findViewById(R.id.progressBar);
        progressTextView = view.findViewById(R.id.progressTextView);
        autoBackupSwitch = view.findViewById(R.id.autoBackupSwitch);
        restoreButton = view.findViewById(R.id.restoreButton);
        manualBackupButton = view.findViewById(R.id.manualBackupButton);

        // Кнопка назад
        backButton.setOnClickListener(v -> requireActivity().getSupportFragmentManager().popBackStack());

        // Настройка переключателя
        if (backupManager != null) {
            autoBackupSwitch.setChecked(backupManager.isAutoBackupEnabled());
            autoBackupSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                backupManager.setAutoBackupEnabled(isChecked);
                Toast.makeText(getContext(),
                        isChecked ? "Авто-резерв включен" : "Авто-резерв выключен",
                        Toast.LENGTH_SHORT).show();
            });
        }

        // Обновляем информацию
        updateBackupInfo();

        // Создание ручного бэкапа
        manualBackupButton.setOnClickListener(v -> {
            if (backupManager != null) {
                showProgress("Создание резервной копии...");
                new Thread(() -> {
                    try {
                        backupManager.createManualBackup();
                        Thread.sleep(1000); // Даем время на создание

                        requireActivity().runOnUiThread(() -> {
                            hideProgress();
                            updateBackupInfo();
                            Toast.makeText(getContext(),
                                    "✅ Резервная копия создана!", Toast.LENGTH_SHORT).show();
                        });
                    } catch (Exception e) {
                        requireActivity().runOnUiThread(() -> {
                            hideProgress();
                            Toast.makeText(getContext(),
                                    "❌ Ошибка: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        });
                    }
                }).start();
            }
        });

        // Восстановление
        restoreButton.setOnClickListener(v -> {
            restoreFromLatestBackup();
        });
    }

    private void updateBackupInfo() {
        if (backupManager != null && lastBackupTextView != null) {
            try {
                // Время последнего бэкапа
                long lastBackupTime = backupManager.getLastAutoBackupTime();
                if (lastBackupTime > 0) {
                    String date = new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
                            .format(new Date(lastBackupTime));
                    lastBackupTextView.setText("Последний бэкап: " + date);
                } else {
                    lastBackupTextView.setText("Бэкапы еще не создавались");
                }

                // Количество бэкапов
                int backupCount = backupManager.getBackupCount();
                backupCountTextView.setText("Всего бэкапов: " + backupCount);

                // Местоположение
                File[] backups = backupManager.getAvailableBackups();
                if (backups != null && backups.length > 0) {
                    String path = backups[0].getParentFile().getAbsolutePath();
                    backupLocationTextView.setText("Папка: " + path);
                } else {
                    backupLocationTextView.setText("Папка: /Android/data/com.example.seriestracker/files/backups");
                }

                // Активация кнопки восстановления
                File latestBackup = backupManager.getLatestBackup();
                restoreButton.setEnabled(latestBackup != null && latestBackup.exists());

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void restoreFromLatestBackup() {
        if (backupManager != null) {
            AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
            builder.setTitle("Восстановление данных");
            builder.setMessage("Это перезапишет все текущие данные. Продолжить?");

            builder.setPositiveButton("Восстановить", (dialog, which) -> {
                performRestore();
            });

            builder.setNegativeButton("Отмена", (dialog, which) -> {
                dialog.dismiss();
            });

            builder.show();
        }
    }

    private void performRestore() {
        showProgress("Восстановление данных...");
        new Thread(() -> {
            try {
                File latestBackup = backupManager.getLatestBackup();
                if (latestBackup != null && latestBackup.exists()) {
                    boolean success = backupManager.restoreFromFile(latestBackup);

                    requireActivity().runOnUiThread(() -> {
                        hideProgress();
                        if (success) {
                            Toast.makeText(getContext(),
                                    "✅ Данные успешно восстановлены!", Toast.LENGTH_LONG).show();
                            updateBackupInfo();
                        } else {
                            Toast.makeText(getContext(),
                                    "❌ Ошибка при восстановлении", Toast.LENGTH_LONG).show();
                        }
                    });
                } else {
                    requireActivity().runOnUiThread(() -> {
                        hideProgress();
                        Toast.makeText(getContext(),
                                "❌ Резервная копия не найдена", Toast.LENGTH_LONG).show();
                    });
                }
            } catch (Exception e) {
                requireActivity().runOnUiThread(() -> {
                    hideProgress();
                    Toast.makeText(getContext(),
                            "❌ Ошибка: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private void showProgress(String message) {
        progressBar.setVisibility(View.VISIBLE);
        progressTextView.setText(message);
        progressTextView.setVisibility(View.VISIBLE);
        manualBackupButton.setEnabled(false);
        restoreButton.setEnabled(false);
    }

    private void hideProgress() {
        progressBar.setVisibility(View.GONE);
        progressTextView.setVisibility(View.GONE);
        manualBackupButton.setEnabled(true);
        updateBackupInfo(); // Обновляем статус кнопки восстановления
    }

    @Override
    public void onResume() {
        super.onResume();
        // Обновляем информацию при возвращении на экран
        updateBackupInfo();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (backupManager != null) {
            backupManager.cleanup();
        }
    }
}