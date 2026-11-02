package com.example.dentalclinicappointmentapp;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.textfield.TextInputEditText;

public class Loginfragment extends Fragment {
    private boolean isLoginMode = true;

    private TextView authTitle;
    private TextView authSubtitle;
    private TextView toggleAuth;
    private TextInputEditText emailInput;
    private TextInputEditText passwordInput;
    private Button btnAuth;
    private ProgressBar progress;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup parent, @Nullable Bundle b) {
        View v = inf.inflate(R.layout.fragmentlogin, parent, false);

        authTitle = v.findViewById(R.id.authTitle);
        authSubtitle = v.findViewById(R.id.authSubtitle);
        toggleAuth = v.findViewById(R.id.toggleAuth);
        emailInput = v.findViewById(R.id.emailInput);
        passwordInput = v.findViewById(R.id.passwordInput);
        btnAuth = v.findViewById(R.id.btnAuth);
        progress = v.findViewById(R.id.progress);

        toggleAuth.setOnClickListener(x -> {
            isLoginMode = !isLoginMode;
            updateMode();
        });

        btnAuth.setOnClickListener(x -> performAuth());

        return v;
    }

    private void updateMode() {
        if (isLoginMode) {
            authTitle.setText("Welcome back");
            authSubtitle.setText("Log in to manage your dental appointments");
            btnAuth.setText("Log In");
            toggleAuth.setText("Don't have an account? Register");
        } else {
            authTitle.setText("Create Account");
            authSubtitle.setText("Sign up to book and manage your dental appointments");
            btnAuth.setText("Register");
            toggleAuth.setText("Already have an account? Log In");
        }
    }

    private void performAuth() {
        String email = textOf(emailInput);
        String password = textOf(passwordInput);

        if (TextUtils.isEmpty(email) || TextUtils.isEmpty(password)) {
            Toast.makeText(requireContext(), "Please enter both email and password", Toast.LENGTH_SHORT).show();
            return;
        }

        if (password.length() < 6) {
            Toast.makeText(requireContext(), "Password must be at least 6 characters", Toast.LENGTH_SHORT).show();
            return;
        }

        setLoading(true);

        if (isLoginMode) {
            Repo.signInWithEmail(email, password, this::onSuccess, this::onError);
        } else {
            Repo.registerWithEmail(email, password, this::onSuccess, this::onError);
        }
    }

    // getText() can return null, so read it once and check that copy.
    private static String textOf(TextInputEditText field) {
        Editable text = field.getText();
        return text == null ? "" : text.toString().trim();
    }

    private void onSuccess() {
        if (!isAdded()) return;
        setLoading(false);
        Toast.makeText(requireContext(), isLoginMode ? "Welcome back!" : "Account created successfully!", Toast.LENGTH_SHORT).show();
        ((MainActivity) requireActivity()).onAuthSuccess();
    }

    private void onError(String errorMsg) {
        if (!isAdded()) return;
        setLoading(false);
        Toast.makeText(requireContext(), "Auth failed: " + errorMsg, Toast.LENGTH_LONG).show();
    }

    private void setLoading(boolean loading) {
        progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnAuth.setEnabled(!loading);
        toggleAuth.setEnabled(!loading);
        emailInput.setEnabled(!loading);
        passwordInput.setEnabled(!loading);
    }
}