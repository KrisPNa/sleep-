package com.example.seriestracker.ui.screens;

import android.Manifest;
import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
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
import com.example.seriestracker.data.prefs.ThemePreferences;
import com.example.seriestracker.data.repository.SeriesRepository;
import com.example.seriestracker.data.sync.AuthSessionStore;
import com.example.seriestracker.data.sync.SupabaseApi;
import com.example.seriestracker.data.sync.SyncEngine;
import com.example.seriestracker.data.watchlinks.WatchSearchSitesStore;
import com.example.seriestracker.ui.screens.AuthScreen;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class BackupSettingsScreen extends Fragment {

    private Switch autoBackupSwitch;
    private Switch darkThemeSwitch;
    private TextView lastBackupText;
    private Button createBackupBtn;
    private Button restoreBackupBtn;
    private TextView backupLocationText;
    private View progressBar;
    private TextView progressText;
    private EditText watchSearchSitesEdit;
    private Button saveWatchSearchSitesBtn;
    private TextView cloudAccountText;
    private Button cloudAccountBtn;

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
                            performMergeMediaRestoreFromUri(selectedFileUri);
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
        darkThemeSwitch = view.findViewById(R.id.dark_theme_switch);
        lastBackupText = view.findViewById(R.id.last_backup_text);
        createBackupBtn = view.findViewById(R.id.create_backup_btn);
        restoreBackupBtn = view.findViewById(R.id.restore_backup_btn);
        backupLocationText = view.findViewById(R.id.backup_location_text);
        progressBar = view.findViewById(R.id.progress_bar);
        progressText = view.findViewById(R.id.progress_text);
        watchSearchSitesEdit = view.findViewById(R.id.watch_search_sites_edit);
        saveWatchSearchSitesBtn = view.findViewById(R.id.save_watch_search_sites_btn);
        cloudAccountText = view.findViewById(R.id.cloud_account_text);
        cloudAccountBtn = view.findViewById(R.id.cloud_account_btn);
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
        syncDarkThemeSwitch();
        updateLastBackupInfo();
        updateBackupLocationInfo();
        if (watchSearchSitesEdit != null) {
            watchSearchSitesEdit.setText(WatchSearchSitesStore.getSitesText(requireContext()));
        }
        refreshCloudAccountUi();
    }

    /** Обновляет свитч без потери слушателя (onResume раньше его сбрасывал). */
    private void syncDarkThemeSwitch() {
        if (darkThemeSwitch == null || !isAdded()) return;
        boolean dark = ThemePreferences.isDark(requireContext());
        darkThemeSwitch.setOnCheckedChangeListener(null);
        darkThemeSwitch.setChecked(dark);
        attachDarkThemeListener();
    }

    private void attachDarkThemeListener() {
        if (darkThemeSwitch == null) return;
        darkThemeSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!isAdded()) return;
            if (ThemePreferences.isDark(requireContext()) == isChecked) return;
            ThemePreferences.setDark(requireActivity(), isChecked);
        });
    }

    private void refreshCloudAccountUi() {
        if (cloudAccountText == null || cloudAccountBtn == null) return;
        AuthSessionStore store = new AuthSessionStore(requireContext());
        if (store.isLoggedIn()) {
            cloudAccountText.setText("Аккаунт: " + store.getEmail());
            cloudAccountBtn.setText("Выйти из аккаунта");
        } else {
            cloudAccountText.setText("Не выполнен вход");
            cloudAccountBtn.setText("Войти в аккаунт");
        } 
    }

    private void setupClickListeners() {
        autoBackupSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            backupManager.setAutoBackupEnabled(isChecked);
        });

        attachDarkThemeListener();

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

        if (saveWatchSearchSitesBtn != null) {
            saveWatchSearchSitesBtn.setOnClickListener(v -> saveWatchSearchSites());
        }

        if (cloudAccountBtn != null) {
            cloudAccountBtn.setOnClickListener(v -> {
                AuthSessionStore store = new AuthSessionStore(requireContext());
                if (store.isLoggedIn()) {
                    SupabaseApi api = new SupabaseApi(requireContext());
                    String token = store.getAccessToken();
                    api.signOutLocal();
                    refreshCloudAccountUi();
                    Toast.makeText(requireContext(),
                            "Вышла из аккаунта. Локальные данные на телефоне остались.",
                            Toast.LENGTH_LONG).show();
                    api.signOutRemoteAsync(token);
                } else {
                    AuthScreen auth = new AuthScreen();
                    auth.setListener(loggedIn -> {
                        if (getActivity() != null) {
                            getActivity().getSupportFragmentManager().popBackStack();
                        }
                        refreshCloudAccountUi();
                    });
                    getParentFragmentManager().beginTransaction()
                            .replace(R.id.fragment_container, auth)
                            .addToBackStack(null)
                            .commit();
                }
            });
        }
    }

    private void saveWatchSearchSites() {
        if (watchSearchSitesEdit == null) {
            return;
        }
        String text = watchSearchSitesEdit.getText() != null
                ? watchSearchSitesEdit.getText().toString()
                : "";
        WatchSearchSitesStore.setSitesText(requireContext(), text);
        // Нормализуем отображение после сохранения
        java.util.List<String> sites = WatchSearchSitesStore.getNormalizedSites(requireContext());
        StringBuilder normalized = new StringBuilder();
        for (int i = 0; i < sites.size(); i++) {
            if (i > 0) {
                normalized.append('\n');
            }
            normalized.append(sites.get(i));
        }
        watchSearchSitesEdit.setText(normalized.toString());

        SyncEngine.getInstance(requireContext()).requestSync();

        if (sites.isEmpty()) {
            Toast.makeText(getContext(), R.string.watch_search_sites_empty, Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(getContext(), R.string.watch_search_sites_saved, Toast.LENGTH_SHORT).show();
        }
    }

    private boolean checkWritePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return true;
        }
        return ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestWritePermissions() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            permissionLauncher.launch(new String[]{
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_EXTERNAL_STORAGE
            });
        } else {
            createBackupWithPermission();
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
        showProgress("Подготовка...");
        backupManager.createManualBackup(
                (current, total, message) -> updateProgress(current, total, message),
                result -> {
                    hideProgress();
                    updateLastBackupInfo();
                    updateBackupLocationInfo();
                    if (result.success) {
                        Toast.makeText(getContext(), "✅ Резервная копия создана", Toast.LENGTH_SHORT).show();
                    } else {
                        String message = result.errorMessage != null
                                ? result.errorMessage
                                : "Не удалось создать резервную копию";
                        new AlertDialog.Builder(requireContext())
                                .setTitle("Ошибка резервного копирования")
                                .setMessage(message)
                                .setPositiveButton("OK", null)
                                .show();
                    }
                }
        );
    }

    private void showRestoreOptions() {
        showProgress("Поиск резервных копий...");
        new Thread(() -> {
            File[] backups = backupManager.getAvailableBackups();
            if (!isAdded()) {
                return;
            }
            requireActivity().runOnUiThread(() -> {
                hideProgress();
                showRestoreDialog(backups);
            });
        }).start();
    }

    private void showRestoreDialog(File[] backups) {

        String[] options;
        boolean hasLocalBackups = backups != null && backups.length > 0;

        if (hasLocalBackups) {
            options = new String[backups.length + 1];
            for (int i = 0; i < backups.length; i++) {
                options[i] = backups[i].getName();
            }
            options[backups.length] = "Выбрать файл резервной копии...";
        } else {
            options = new String[]{"Выбрать файл резервной копии..."};
        }

        new AlertDialog.Builder(requireContext())
                .setTitle("Выберите резервную копию")
                .setItems(options, (dialog, which) -> {
                    if (hasLocalBackups && which < backups.length) {
                        showRestoreTypeDialog(backups[which]);
                    } else {
                        selectBackupFile();
                    }
                })
                .show();
    }

    private void showRestoreTypeDialog(File backupFile) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Тип восстановления")
                .setItems(new CharSequence[]{
                        "Восстановить удалённые сериалы",
                        "Добавить фото/видео к существующим"
                }, (dialog, which) -> {
                    if (which == 0) {
                        performRestoreMissingSeries(backupFile);
                    } else {
                        performMergeMediaRestore(backupFile);
                    }
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void performRestoreMissingSeries(File backupFile) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Восстановление сериалов")
                .setMessage("Сериалы из копии, которых сейчас нет в приложении, будут добавлены обратно вместе с обложками, фото и видео.\n\nТекущие сериалы не удаляются.")
                .setPositiveButton("Восстановить", (dialog, which) -> {
                    runMergeRestore(() -> backupManager.restoreMissingSeriesFromFile(backupFile));
                })
                .setNegativeButton("Отмена", (dialog, which) -> dialog.dismiss())
                .show();
    }

    private void performMergeMediaRestore(File backupFile) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Восстановление из копии")
                .setMessage("Фото и обложки из копии будут добавлены к сериалам с совпадающими названиями.\n\nВсе текущие сериалы (включая новые) сохраняются — ничего не удаляется.")
                .setPositiveButton("Восстановить", (dialog, which) -> {
                    runMergeRestore(() -> backupManager.mergeMediaFromFile(backupFile));
                })
                .setNegativeButton("Отмена", (dialog, which) -> dialog.dismiss())
                .show();
    }

    private void performMergeMediaRestoreFromUri(Uri backupUri) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Тип восстановления")
                .setItems(new CharSequence[]{
                        "Восстановить удалённые сериалы",
                        "Добавить фото/видео к существующим"
                }, (dialog, which) -> {
                    if (which == 0) {
                        confirmRestoreMissingSeriesFromUri(backupUri);
                    } else {
                        confirmMergeMediaRestoreFromUri(backupUri);
                    }
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void confirmRestoreMissingSeriesFromUri(Uri backupUri) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Восстановление сериалов")
                .setMessage("Сериалы из копии, которых сейчас нет в приложении, будут добавлены обратно вместе с обложками, фото и видео.\n\nТекущие сериалы не удаляются.")
                .setPositiveButton("Восстановить", (dialog, which) -> {
                    runMergeRestore(() -> backupManager.restoreMissingSeriesFromUri(backupUri));
                })
                .setNegativeButton("Отмена", (dialog, which) -> dialog.dismiss())
                .show();
    }

    private void confirmMergeMediaRestoreFromUri(Uri backupUri) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Восстановление из копии")
                .setMessage("Фото и обложки из копии будут добавлены к сериалам с совпадающими названиями.\n\nВсе текущие сериалы (включая новые) сохраняются — ничего не удаляется.")
                .setPositiveButton("Восстановить", (dialog, which) -> {
                    runMergeRestore(() -> backupManager.mergeMediaFromUri(backupUri));
                })
                .setNegativeButton("Отмена", (dialog, which) -> dialog.dismiss())
                .show();
    }

    private void runMergeRestore(java.util.concurrent.Callable<AutoBackupManager.MergeMediaResult> task) {
        showProgress("Восстановление...");
        Activity activity = getActivity();
        if (activity == null) {
            hideProgress();
            return;
        }

        Handler mainHandler = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            AutoBackupManager.MergeMediaResult result;
            try {
                result = task.call();
            } catch (Exception e) {
                result = new AutoBackupManager.MergeMediaResult();
                result.success = false;
                result.errorMessage = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            }

            AutoBackupManager.MergeMediaResult finalResult = result;
            mainHandler.post(() -> {
                if (!isAdded()) {
                    return;
                }
                hideProgress();
                showMergeRestoreResult(finalResult);
            });
        }).start();
    }

    private void showMergeRestoreResult(AutoBackupManager.MergeMediaResult result) {
        if (!result.success) {
            new AlertDialog.Builder(requireContext())
                    .setTitle("Ошибка восстановления")
                    .setMessage(result.getSummaryMessage())
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }

        if (result.hasRestoredAnything()) {
            Toast.makeText(getContext(), "✅ " + result.getSummaryMessage(), Toast.LENGTH_LONG).show();
            return;
        }

        String title = result.missingSeriesRestore
                ? "Сериалы не восстановлены"
                : "Медиа не восстановлены";
        new AlertDialog.Builder(requireContext())
                .setTitle(title)
                .setMessage(result.getSummaryMessage())
                .setPositiveButton("OK", null)
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

    private void showProgress(String message) {
        if (progressBar != null) {
            progressBar.setVisibility(View.VISIBLE);
            if (progressBar instanceof android.widget.ProgressBar) {
                ((android.widget.ProgressBar) progressBar).setIndeterminate(false);
                ((android.widget.ProgressBar) progressBar).setProgress(0);
            }
        }
        if (progressText != null) {
            progressText.setVisibility(View.VISIBLE);
            progressText.setText(message);
        }
        if (createBackupBtn != null) {
            createBackupBtn.setEnabled(false);
        }
        if (restoreBackupBtn != null) {
            restoreBackupBtn.setEnabled(false);
        }
    }

    private void updateProgress(int current, int total, String message) {
        if (progressBar instanceof android.widget.ProgressBar) {
            android.widget.ProgressBar bar = (android.widget.ProgressBar) progressBar;
            bar.setIndeterminate(total <= 0);
            if (total > 0) {
                bar.setMax(total);
                bar.setProgress(Math.min(current, total));
            }
        }
        if (progressText != null && message != null) {
            progressText.setText(message);
        }
    }

    private void hideProgress() {
        if (progressBar != null) {
            progressBar.setVisibility(View.GONE);
        }
        if (progressText != null) {
            progressText.setVisibility(View.GONE);
        }
        if (createBackupBtn != null) {
            createBackupBtn.setEnabled(true);
        }
        if (restoreBackupBtn != null) {
            restoreBackupBtn.setEnabled(true);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        // Сохраняем сайты при уходе со страницы, даже без нажатия кнопки
        if (watchSearchSitesEdit != null && isAdded()) {
            WatchSearchSitesStore.setSitesText(
                    requireContext(),
                    watchSearchSitesEdit.getText() != null
                            ? watchSearchSitesEdit.getText().toString()
                            : "");
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        loadSettings();
    }
}