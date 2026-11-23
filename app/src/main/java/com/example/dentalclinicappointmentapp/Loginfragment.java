package com.example.dentalclinicappointmentapp;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Patterns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.content.Context;
import android.view.inputmethod.InputMethodManager;
import android.graphics.Rect;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import android.widget.ScrollView;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

public class Loginfragment extends Fragment {
    private boolean isLoginMode = true;

    private TextView authTitle;
    private TextView authSubtitle;
    private TextView toggleAuth;
    private TextView forgotPassword;
    private TextView passwordStrength;
    private View nameFields;
    private View confirmPasswordLayout;
    private View registerExtraFields;
    private ScrollView authScrollView;
    private TextInputEditText firstNameInput;
    private TextInputEditText middleNameInput;
    private TextInputEditText lastNameInput;
    private TextInputEditText emailInput;
    private TextInputEditText phoneInput;
    private TextInputEditText ageInput;
    private AutoCompleteTextView genderInput;
    private TextInputEditText passwordInput;
    private TextInputEditText confirmPasswordInput;
    private Button btnAuth;
    private ProgressBar progress;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup parent, @Nullable Bundle b) {
        View v = inf.inflate(R.layout.fragmentlogin, parent, false);
        authScrollView = v.findViewById(R.id.authScrollView);

        final int basePaddingBottom = v.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(v, (view, insets) -> {
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            int bottom = Math.max(ime.bottom, bars.bottom);
            view.setPadding(view.getPaddingLeft(), view.getPaddingTop(),
                    view.getPaddingRight(), basePaddingBottom + bottom);
            return insets;
        });

        authTitle = v.findViewById(R.id.authTitle);
        authSubtitle = v.findViewById(R.id.authSubtitle);
        toggleAuth = v.findViewById(R.id.toggleAuth);
        forgotPassword = v.findViewById(R.id.forgotPassword);
        passwordStrength = v.findViewById(R.id.passwordStrength);
        nameFields = v.findViewById(R.id.nameFields);
        confirmPasswordLayout = v.findViewById(R.id.confirmPasswordLayout);
        registerExtraFields = v.findViewById(R.id.registerExtraFields);
        firstNameInput = v.findViewById(R.id.firstNameInput);
        middleNameInput = v.findViewById(R.id.middleNameInput);
        lastNameInput = v.findViewById(R.id.lastNameInput);
        emailInput = v.findViewById(R.id.emailInput);
        phoneInput = v.findViewById(R.id.phoneInput);
        ageInput = v.findViewById(R.id.ageInput);
        genderInput = v.findViewById(R.id.genderInput);
        genderInput.setAdapter(new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_dropdown_item_1line, new String[]{"Male", "Female", "Prefer not to say"}));
        passwordInput = v.findViewById(R.id.passwordInput);
        confirmPasswordInput = v.findViewById(R.id.confirmPasswordInput);
        btnAuth = v.findViewById(R.id.btnAuth);
        progress = v.findViewById(R.id.progress);

        // Keep the focused input field visible when the keyboard opens.
        // Automatically scroll to the focused input when the keyboard opens.
        View.OnFocusChangeListener keepFieldVisible = (field, hasFocus) -> {
            if (!hasFocus) return;

            authScrollView.postDelayed(() -> {
                if (!isAdded() || field.getWindowToken() == null) {
                    return;
                }

                Rect fieldRect = new Rect(
                        0,
                        0,
                        field.getWidth(),
                        field.getHeight()
                );

                // Let the ScrollView and its parent handle the correct coordinates.
                field.requestRectangleOnScreen(fieldRect, true);

            }, 300);
        };

        firstNameInput.setOnFocusChangeListener(keepFieldVisible);
        middleNameInput.setOnFocusChangeListener(keepFieldVisible);
        lastNameInput.setOnFocusChangeListener(keepFieldVisible);
        emailInput.setOnFocusChangeListener(keepFieldVisible);
        phoneInput.setOnFocusChangeListener(keepFieldVisible);
        ageInput.setOnFocusChangeListener(keepFieldVisible);
        genderInput.setOnFocusChangeListener(keepFieldVisible);
        passwordInput.setOnFocusChangeListener(keepFieldVisible);
        confirmPasswordInput.setOnFocusChangeListener(keepFieldVisible);

        toggleAuth.setOnClickListener(x -> {
            // Hide the keyboard before changing the form height.
            InputMethodManager imm = (InputMethodManager)
                    requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);

            if (imm != null) {
                imm.hideSoftInputFromWindow(toggleAuth.getWindowToken(), 0);
            }

            toggleAuth.clearFocus();

            isLoginMode = !isLoginMode;
            updateMode();

            // Reset the scroll position after the fields have changed.
            authScrollView.post(() -> {
                authScrollView.scrollTo(0, 0);
                authScrollView.requestLayout();
            });
        });

        btnAuth.setOnClickListener(x -> performAuth());
        forgotPassword.setOnClickListener(x -> showForgotPasswordDialog());

        // Live weak/strong label while the patient types a new password.
        passwordInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) { }
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) { }
            @Override public void afterTextChanged(Editable s) { updateStrengthLabel(); }
        });

        updateMode();
        return v;
    }

    private void updateMode() {
        int registerOnly = isLoginMode ? View.GONE : View.VISIBLE;
        nameFields.setVisibility(registerOnly);
        registerExtraFields.setVisibility(registerOnly);
        confirmPasswordLayout.setVisibility(registerOnly);
        forgotPassword.setVisibility(isLoginMode ? View.VISIBLE : View.GONE);

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
        updateStrengthLabel();
    }

    private void updateStrengthLabel() {
        String password = textOf(passwordInput);
        // Only shown when registering and after the patient has started typing.
        if (isLoginMode || password.isEmpty()) {
            passwordStrength.setVisibility(View.GONE);
            return;
        }
        passwordStrength.setVisibility(View.VISIBLE);

        if (PasswordChecker.isStrong(password)) {
            passwordStrength.setText("Strong password");
            passwordStrength.setTextColor(ContextCompat.getColor(requireContext(), R.color.forest_green));
        } else {
            passwordStrength.setText("Weak password. Needs: " + PasswordChecker.missing(password));
            passwordStrength.setTextColor(ContextCompat.getColor(requireContext(), R.color.danger));
        }
    }

    private void performAuth() {
        String email = textOf(emailInput);
        String password = textOf(passwordInput);

        if (isLoginMode) {
            if (TextUtils.isEmpty(email) || TextUtils.isEmpty(password)) {
                toast("Please enter both email and password");
                return;
            }
            setLoading(true);
            Repo.signInWithEmail(email, password, this::onSuccess, this::onError);
            return;
        }

        // Register mode: check everything before touching Firebase.
        String first = textOf(firstNameInput);
        String middle = textOf(middleNameInput);
        String last = textOf(lastNameInput);

        if (first.isEmpty() || last.isEmpty()) {
            toast("Please enter your first and last name");
            return;
        }
        if (!isValidName(first) || !isValidName(last)
                || (!middle.isEmpty() && !isValidName(middle))) {
            toast("Names can only have letters, spaces, hyphens and apostrophes");
            return;
        }
        if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            toast("Please enter a valid email address");
            return;
        }
        String phone = textOf(phoneInput);
        if (!phone.matches("\\d{11}")) {
            toast("Phone number must contain exactly 11 digits");
            return;
        }
        int age;
        try {
            age = Integer.parseInt(textOf(ageInput));
        } catch (NumberFormatException e) {
            age = 0;
        }
        if (age < 1 || age > 120) {
            toast("Please enter a valid age");
            return;
        }
        String gender = genderInput.getText() == null ? "" : genderInput.getText().toString().trim();
        if (gender.isEmpty()) {
            toast("Please select your gender");
            return;
        }
        if (!PasswordChecker.isStrong(password)) {
            toast("Password is too weak. Needs: " + PasswordChecker.missing(password));
            return;
        }
        if (!password.equals(textOf(confirmPasswordInput))) {
            toast("Passwords do not match");
            return;
        }

        setLoading(true);
        Repo.registerWithEmail(email, password, first, middle, last, phone, age, gender, this::onSuccess, this::onError);
    }

    // Opens a small dialog asking for the email, then sends Firebase's reset link.
    private void showForgotPasswordDialog() {
        TextInputLayout layout = new TextInputLayout(requireContext());
        layout.setHint("Email address");
        TextInputEditText input = new TextInputEditText(layout.getContext());
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        input.setSingleLine(true);
        input.setText(textOf(emailInput));   // reuse what they already typed on the login screen
        layout.addView(input);

        FrameLayout box = new FrameLayout(requireContext());
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad / 2, pad, 0);
        box.addView(layout);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Reset password")
                .setMessage("Enter your account email and we'll send you a link to reset your password.")
                .setView(box)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Send link", (d, which) -> {
                    String email = textOf(input);
                    if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                        toast("Please enter a valid email address");
                        return;
                    }
                    setLoading(true);
                    Repo.sendPasswordReset(email, () -> {
                        if (!isAdded()) return;
                        setLoading(false);
                        new MaterialAlertDialogBuilder(requireContext())
                                .setTitle("Check your email")
                                .setMessage("If an account exists for " + email
                                        + ", a password reset link is on its way. Check your spam folder too.")
                                .setPositiveButton("OK", null)
                                .show();
                    }, msg -> {
                        if (!isAdded()) return;
                        setLoading(false);
                        Toast.makeText(requireContext(), "Could not send reset email: " + msg,
                                Toast.LENGTH_LONG).show();
                    });
                })
                .show();
    }

    // Letters from any language, plus spaces, hyphens, apostrophes and periods (e.g. "Dela Cruz", "O'Neil", "Jr.").
    private static boolean isValidName(String name) {
        return name.matches("[\\p{L} .'\\-]+");
    }

    // getText() can return null, so read it once and check that copy.
    private static String textOf(TextInputEditText field) {
        Editable text = field.getText();
        return text == null ? "" : text.toString().trim();
    }

    private void toast(String msg) {
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
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
        forgotPassword.setEnabled(!loading);
        firstNameInput.setEnabled(!loading);
        middleNameInput.setEnabled(!loading);
        lastNameInput.setEnabled(!loading);
        emailInput.setEnabled(!loading);
        phoneInput.setEnabled(!loading);
        ageInput.setEnabled(!loading);
        genderInput.setEnabled(!loading);
        passwordInput.setEnabled(!loading);
        confirmPasswordInput.setEnabled(!loading);
    }
}