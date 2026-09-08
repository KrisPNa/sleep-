package com.example.seriestracker.ui.screens;

import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.seriestracker.R;
import com.example.seriestracker.data.sync.AuthSessionStore;
import com.example.seriestracker.data.sync.SupabaseApi;
import com.example.seriestracker.data.sync.SyncEngine;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AuthScreen extends Fragment {

    public interface Listener {
        void onAuthFinished(boolean loggedIn);
    }

    private static final int COLOR_ERROR = Color.parseColor("#B00020");
    private static final int COLOR_OK = Color.parseColor("#2E7D32");

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private EditText emailEdit;
    private EditText passwordEdit;
    private TextView errorText;
    private ProgressBar progressBar;
    private Listener listener;

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_auth, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        emailEdit = view.findViewById(R.id.authEmail);
        passwordEdit = view.findViewById(R.id.authPassword);
        errorText = view.findViewById(R.id.authError);
        progressBar = view.findViewById(R.id.authProgress);
        Button signInBtn = view.findViewById(R.id.authSignInBtn);
        Button signUpBtn = view.findViewById(R.id.authSignUpBtn);
        Button resetBtn = view.findViewById(R.id.authResetBtn);
        Button skipBtn = view.findViewById(R.id.authSkipBtn);

        signInBtn.setOnClickListener(v -> runAuth(false));
        signUpBtn.setOnClickListener(v -> runAuth(true));
        resetBtn.setOnClickListener(v -> runResetPassword());
        skipBtn.setOnClickListener(v -> finish(false));
    }

    private void runAuth(boolean signUp) {
        String email = emailEdit.getText() != null ? emailEdit.getText().toString().trim() : "";
        String password = passwordEdit.getText() != null ? passwordEdit.getText().toString() : "";
        errorText.setText("");
        errorText.setTextColor(COLOR_ERROR);
        if (email.isEmpty() || password.isEmpty()) {
            errorText.setText("Введи email и пароль");
            return;
        }
        setBusy(true);
        executor.execute(() -> {
            try {
                SupabaseApi api = new SupabaseApi(requireContext());
                if (!api.hasValidConfig()) {
                    throw new IllegalStateException(
                            "Задай SUPABASE_ANON_KEY в local.properties");
                }
                if (signUp) {
                    api.signUp(email, password);
                    if (!api.getSession().isLoggedIn()) {
                        api.signIn(email, password);
                    }
                } else {
                    api.signIn(email, password);
                }
                mainHandler.post(() -> {
                    if (!isAdded()) return;
                    setBusy(false);
                    Toast.makeText(requireContext(),
                            "Вход выполнен. Синхронизация идёт в фоне…",
                            Toast.LENGTH_SHORT).show();
                    finish(true);
                });
                SyncEngine.getInstance(requireContext()).requestSyncAfterAuth(signUp, null);
            } catch (Exception e) {
                mainHandler.post(() -> {
                    if (!isAdded()) return;
                    setBusy(false);
                    errorText.setTextColor(COLOR_ERROR);
                    errorText.setText(e.getMessage() != null ? e.getMessage() : String.valueOf(e));
                });
            }
        });
    }

    private void runResetPassword() {
        String email = emailEdit.getText() != null ? emailEdit.getText().toString().trim() : "";
        errorText.setText("");
        errorText.setTextColor(COLOR_ERROR);
        if (email.isEmpty()) {
            errorText.setText("Введи email для сброса пароля");
            return;
        }
        setBusy(true);
        executor.execute(() -> {
            try {
                SupabaseApi api = new SupabaseApi(requireContext());
                if (!api.hasValidConfig()) {
                    throw new IllegalStateException(
                            "Задай SUPABASE_ANON_KEY в local.properties");
                }
                api.resetPassword(email);
                mainHandler.post(() -> {
                    if (!isAdded()) return;
                    setBusy(false);
                    errorText.setTextColor(COLOR_OK);
                    errorText.setText("Письмо для сброса пароля отправлено на " + email);
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    if (!isAdded()) return;
                    setBusy(false);
                    errorText.setTextColor(COLOR_ERROR);
                    errorText.setText(e.getMessage() != null ? e.getMessage() : String.valueOf(e));
                });
            }
        });
    }

    private void setBusy(boolean busy) {
        progressBar.setVisibility(busy ? View.VISIBLE : View.GONE);
        View root = getView();
        if (root == null) return;
        root.findViewById(R.id.authSignInBtn).setEnabled(!busy);
        root.findViewById(R.id.authSignUpBtn).setEnabled(!busy);
        root.findViewById(R.id.authResetBtn).setEnabled(!busy);
        root.findViewById(R.id.authSkipBtn).setEnabled(!busy);
    }

    private void finish(boolean loggedIn) {
        if (listener != null) {
            listener.onAuthFinished(loggedIn);
        }
    }

    public static boolean shouldShowAuth(android.content.Context context) {
        AuthSessionStore store = new AuthSessionStore(context);
        return !store.isLoggedIn();
    }
}
