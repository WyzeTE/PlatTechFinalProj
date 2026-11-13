package com.example.dentalclinicappointmentapp;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.textfield.TextInputEditText;

// My Account: edit name, phone, age and gender, and optionally change the password.
// Same fields and rules as the website's Edit Profile. The email is shown but not changed here.
public class Accountfragment extends Fragment {
    private static final String[] GENDERS = {"Male", "Female", "Prefer not to say"};

    private TextView avatar, accountName, passwordStrength;
    private TextInputEditText firstName, middleName, lastName, phone, age, email, newPassword, confirmPassword;
    private AutoCompleteTextView gender;
    private Button btnSave;
    private ProgressBar progress;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup parent, @Nullable Bundle b) {
        View v = inf.inflate(R.layout.fragmentaccount, parent, false);
        v.findViewById(R.id.btnBack).setOnClickListener(x -> getParentFragmentManager().popBackStack());

        avatar = v.findViewById(R.id.avatar);
        accountName = v.findViewById(R.id.accountName);
        passwordStrength = v.findViewById(R.id.passwordStrength);
        firstName = v.findViewById(R.id.firstNameInput);
        middleName = v.findViewById(R.id.middleNameInput);
        lastName = v.findViewById(R.id.lastNameInput);
        phone = v.findViewById(R.id.phoneInput);
        age = v.findViewById(R.id.ageInput);
        gender = v.findViewById(R.id.genderInput);
        email = v.findViewById(R.id.emailInput);
        newPassword = v.findViewById(R.id.newPasswordInput);
        confirmPassword = v.findViewById(R.id.confirmPasswordInput);
        btnSave = v.findViewById(R.id.btnSave);
        progress = v.findViewById(R.id.progress);

        gender.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_dropdown_item_1line, GENDERS));

        newPassword.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) { }
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) { }
            @Override public void afterTextChanged(Editable s) { updateStrengthLabel(); }
        });

        btnSave.setOnClickListener(x -> save());

        setHeader(Repo.getDisplayName(), Repo.getUserEmail());
        setLoading(true);
        Repo.loadProfile(p -> {
            if (!isAdded()) return;
            setLoading(false);
            firstName.setText(p.firstName);
            middleName.setText(p.middleName);
            lastName.setText(p.lastName);
            phone.setText(p.phone);
            age.setText(p.age > 0 ? String.valueOf(p.age) : "");
            gender.setText(p.gender, false);   // false = don't pop the list open
            email.setText(p.email);
        });
        return v;
    }

    private void setHeader(String name, String fallback) {
        String shown = name == null || name.trim().isEmpty() ? fallback : name.trim();
        accountName.setText(shown);
        avatar.setText(shown.isEmpty() ? "?" : shown.substring(0, 1).toUpperCase());
    }

    private void updateStrengthLabel() {
        String password = textOf(newPassword);
        if (password.isEmpty()) {
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

    private void save() {
        Repo.Profile p = new Repo.Profile();
        p.firstName = textOf(firstName);
        p.middleName = textOf(middleName);
        p.lastName = textOf(lastName);
        p.phone = textOf(phone);
        p.gender = gender.getText() == null ? "" : gender.getText().toString().trim();

        if (p.firstName.isEmpty() || p.lastName.isEmpty()) {
            toast("Please enter your first and last name");
            return;
        }
        if (!isValidName(p.firstName) || !isValidName(p.lastName)
                || (!p.middleName.isEmpty() && !isValidName(p.middleName))) {
            toast("Names can only have letters, spaces, hyphens and apostrophes");
            return;
        }
        if (!p.phone.matches("\\d{11}")) {
            toast("Phone number must contain exactly 11 digits");
            return;
        }
        int ageValue;
        try {
            ageValue = Integer.parseInt(textOf(age));
        } catch (NumberFormatException e) {
            ageValue = 0;
        }
        if (ageValue < 1 || ageValue > 120) {
            toast("Please enter a valid age");
            return;
        }
        p.age = ageValue;
        if (p.gender.isEmpty()) {
            toast("Please select your gender");
            return;
        }

        String password = textOf(newPassword);
        if (!password.isEmpty()) {
            if (!PasswordChecker.isStrong(password)) {
                toast("Password is too weak. Needs: " + PasswordChecker.missing(password));
                return;
            }
            if (!password.equals(textOf(confirmPassword))) {
                toast("Passwords do not match");
                return;
            }
        }

        setLoading(true);
        Repo.saveProfile(p, password, () -> {
            if (!isAdded()) return;
            setLoading(false);
            newPassword.setText("");
            confirmPassword.setText("");
            setHeader(Repo.fullName(p.firstName, p.middleName, p.lastName), Repo.getUserEmail());
            toast("Your profile has been updated successfully");
        }, msg -> {
            if (!isAdded()) return;
            setLoading(false);
            Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show();
        });
    }

    private static boolean isValidName(String name) {
        return name.matches("[\\p{L} .'\\-]+");
    }

    private static String textOf(TextInputEditText field) {
        Editable text = field.getText();
        return text == null ? "" : text.toString().trim();
    }

    private void toast(String msg) {
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
    }

    private void setLoading(boolean loading) {
        progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnSave.setEnabled(!loading);
    }
}