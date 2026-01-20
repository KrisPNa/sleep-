package com.example.seriestracker.ui.screens;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;


import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.seriestracker.R;
import com.example.seriestracker.data.backup.AutoBackupManager;
import com.example.seriestracker.data.repository.SeriesRepository;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class BackupSettingsScreen extends Fragment {

    private Switch autoBackupSwitch;
    private TextView lastBackupText;
    private Button createBackupBtn;
    private Button restoreBackupBtn;
    private TextView backupLocationText;
    private View progressBar;

    private ImageButton backButton;
    private SeriesRepository repository;
    private AutoBackupManager backupManager;

    private ActivityResultLauncher<String[]> permissionLauncher;
    private ActivityResultLauncher<Intent> filePickerLauncher;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        permissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                result -> {
                    boolean allGranted = true;
                    for (Boolean granted : result.values()) {
                        if (!granted) {
                            allGranted = false;
                            break;
                        }
                    }

                    if (!allGranted) {
                        showPermissionDeniedDialog();
                    } else {
                        createBackupWithPermission();
                    }
                }
        );


        filePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        Uri selectedFileUri = result.getData().getData();
                        if (selectedFileUri != null) {
                            performRestoreFromUri(selectedFileUri);
                        }
                    }
                }
        );
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_backup_settings, container, false);

        initViews(view);
        setupRepository();
        setupBackupManager();
        loadSettings();
        setupClickListeners();

        return view;
    }

    private void initViews(View view) {
        autoBackupSwitch = view.findViewById(R.id.auto_backup_switch);
        lastBackupText = view.findViewById(R.id.last_backup_text);
        createBackupBtn = view.findViewById(R.id.create_backup_btn);
        restoreBackupBtn = view.findViewById(R.id.restore_backup_btn);
        backupLocationText = view.findViewById(R.id.backup_location_text);
        progressBar = view.findViewById(R.id.progress_bar);
        backButton = view.findViewById(R.id.backButton);

    }

    private void setupRepository() {
        // Получаем Application из Context
        android.app.Application application = (android.app.Application) requireContext().getApplicationContext();
        repository = SeriesRepository.getInstance(application);
    }

    private void setupBackupManager() {
        // То же самое для AutoBackupManager
        android.app.Application application = (android.app.Application) requireContext().getApplicationContext();
        backupManager = AutoBackupManager.getInstance(application, repository);
    }

    private void loadSettings() {
        autoBackupSwitch.setChecked(backupManager.isAutoBackupEnabled());
        updateLastBackupInfo();
        updateBackupLocationInfo();
    }

    private void setupClickListeners() {
        autoBackupSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            backupManager.setAutoBackupEnabled(isChecked);
        });

        createBackupBtn.setOnClickListener(v -> {
            if (checkWritePermissions()) {
                createBackupWithPermission();
            } else {
                requestWritePermissions();
            }
        });

        restoreBackupBtn.setOnClickListener(v -> {
            showRestoreOptions();
        });

        backButton.setOnClickListener(v -> {
            // Возвращаемся на предыдущий экран
            if (getActivity() != null) {
                getActivity().getSupportFragmentManager().popBackStack();
            }
        });
    }

    private boolean checkWritePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return true;
        }
        return ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestWritePermissions() {
        if (shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            showPermissionRationaleDialog();
        } else {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                permissionLauncher.launch(new String[]{
                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        Manifest.permission.READ_EXTERNAL_STORAGE
                });
            } else {
                createBackupWithPermission();
            }
        }
    }

    private void showPermissionRationaleDialog() {
        new AlertDialog.Builder(requireContext())
                .setTitle("Необходимо разрешение")
                .setMessage("Приложению требуется доступ к внешнему хранилищу для создания резервных копий. Это позволит сохранять данные даже после удаления приложения.")
                .setPositiveButton("ОК", (dialog, which) -> {
                    permissionLauncher.launch(new String[]{
                            Manifest.permission.WRITE_EXTERNAL_STORAGE,
                            Manifest.permission.READ_EXTERNAL_STORAGE
                    });
                })
                .setNegativeButton("Отмена", (dialog, which) -> dialog.dismiss())
                .show();
    }

    private void showPermissionDeniedDialog() {
        new AlertDialog.Builder(requireContext())
                .setTitle("Разрешение отклонено")
                .setMessage("Без разрешения на запись во внешнее хранилище невозможно создать резервную копию, которая будет доступна после удаления приложения. Вы можете предоставить разрешение в настройках приложения.")
                .setPositiveButton("Настройки", (dialog, which) -> {
                    openAppSettings();
                })
                .setNegativeButton("Отмена", (dialog, which) -> dialog.dismiss())
                .show();
    }

    private void openAppSettings() {
        Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        Uri uri = Uri.fromParts("package", requireContext().getPackageName(), null);
        intent.setData(uri);
        startActivity(intent);
    }

    private void createBackupWithPermission() {
        showProgress();
        backupManager.createManualBackup();

        requireActivity().runOnUiThread(() -> {
            hideProgress();
            updateLastBackupInfo();
            updateBackupLocationInfo();
            Toast.makeText(getContext(), "✅ Резервная копия создана", Toast.LENGTH_SHORT).show();
        });
    }

    private void showRestoreOptions() {
        File[] backups = backupManager.getAvailableBackups();

        // Подготовим варианты восстановления
        String[] options;
        boolean hasLocalBackups = backups != null && backups.length > 0;

        if (hasLocalBackups) {
            options = new String[backups.length + 1]; // +1 для опции выбора файла
            // Копируем названия локальных бэкапов
            for (int i = 0; i < backups.length; i++) {
                options[i] = backups[i].getName();
            }
            options[backups.length] = "Выбрать файл резервной копии...";
        } else {
            options = new String[]{"Выбрать файл резервной копии..."};
        }

        new AlertDialog.Builder(requireContext())
                .setTitle("Выберите способ восстановления")
                .setItems(options, (dialog, which) -> {
                    if (hasLocalBackups && which < backups.length) {
                        // Выбрали локальный бэкап
                        File selectedBackup = backups[which];
                        performRestore(selectedBackup);
                    } else {
                        // Выбрали опцию выбора файла
                        selectBackupFile();
                    }
                })
                .show();
    }

    private void performRestore(File backupFile) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Восстановление данных")
                .setMessage("Вы уверены, что хотите восстановить данные из резервной копии? Это заменит все текущие данные.")
                .setPositiveButton("Восстановить", (dialog, which) -> {
                    showProgress();

                    Thread restoreThread = new Thread(() -> {
                        boolean success = backupManager.restoreFromFile(backupFile);

                        requireActivity().runOnUiThread(() -> {
                            hideProgress();
                            if (success) {
                                Toast.makeText(getContext(), "✅ Данные восстановлены", Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(getContext(), "❌ Ошибка восстановления", Toast.LENGTH_SHORT).show();
                            }
                        });
                    });

                    restoreThread.start();
                })
                .setNegativeButton("Отмена", (dialog, which) -> dialog.dismiss())
                .show();
    }

    private void selectBackupFile() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*"); // Любой тип файла
        intent.addCategory(Intent.CATEGORY_OPENABLE);

        // Проверяем, можно ли открыть системный диалог выбора файлов
        if (intent.resolveActivity(requireContext().getPackageManager()) != null) {
            filePickerLauncher.launch(Intent.createChooser(intent, "Выберите файл резервной копии"));
        } else {
            Toast.makeText(getContext(), "❌ Не найдено приложение для выбора файлов", Toast.LENGTH_SHORT).show();
        }
    }



    private void performRestoreFromUri(Uri backupUri) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Восстановление данных")
                .setMessage("Вы уверены, что хотите восстановить данные из выбранного файла? Это заменит все текущие данные.")
                .setPositiveButton("Восстановить", (dialog, which) -> {
                    showProgress();

                    Thread restoreThread = new Thread(() -> {
                        boolean success = backupManager.restoreFromUri(backupUri);

                        requireActivity().runOnUiThread(() -> {
                            hideProgress();
                            if (success) {
                                Toast.makeText(getContext(), "✅ Данные восстановлены", Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(getContext(), "❌ Ошибка восстановления", Toast.LENGTH_SHORT).show();
                            }
                        });
                    });

                    restoreThread.start();
                })
                .setNegativeButton("Отмена", (dialog, which) -> dialog.dismiss())
                .show();
    }

    private void updateLastBackupInfo() {
        long lastBackupTime = backupManager.getLastAutoBackupTime();
        if (lastBackupTime > 0) {
            lastBackupText.setText("Последний бэкап: " + new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
                    .format(new Date(lastBackupTime)));
        } else {
            lastBackupText.setText("Бэкапов нет");
        }
    }

    private void updateBackupLocationInfo() {
        String backupLocation = backupManager.getDefaultBackupPath();
        backupLocationText.setText("Папка бэкапов: " + backupLocation);
    }

    private void showProgress() {
        if (progressBar != null) {
            progressBar.setVisibility(View.VISIBLE);
        }
        if (createBackupBtn != null) {
            createBackupBtn.setEnabled(false);
        }
        if (restoreBackupBtn != null) {
            restoreBackupBtn.setEnabled(false);
        }
    }

    private void hideProgress() {
        if (progressBar != null) {
            progressBar.setVisibility(View.GONE);
        }
        if (createBackupBtn != null) {
            createBackupBtn.setEnabled(true);
        }
        if (restoreBackupBtn != null) {
            restoreBackupBtn.setEnabled(true);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        loadSettings();
    }
}