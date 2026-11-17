package com.example.dentalclinicappointmentapp;

import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class MainActivity extends AppCompatActivity {
    private BottomNavigationView nav;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Keep the app content inside the system window and resize it above the keyboard.
        WindowCompat.setDecorFitsSystemWindows(getWindow(), true);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

        setContentView(R.layout.activity_main);
        nav = findViewById(R.id.nav);

        nav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.tab_home) show(new Homefragment());
            else if (id == R.id.tab_book) show(new Bookfragment());
            else show(new Queuefragment());
            return true;
        });

        nav.setOnItemReselectedListener(item ->
                getSupportFragmentManager().popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE));

        if (Repo.isLoggedIn()) {
            showMainTabs();
        } else {
            nav.setVisibility(View.GONE);
            show(new Loginfragment());
        }
    }

    public void onAuthSuccess() {
        showMainTabs();
    }

    public void confirmLogout() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Log out?")
                .setMessage("Your appointments stay saved. Log in again anytime, here or on the clinic website.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Log out", (dialog, which) -> logout())
                .show();
    }

    public void logout() {
        Repo.signOut(() -> {
            if (isFinishing() || isDestroyed()) return;
            nav.setVisibility(View.GONE);
            show(new Loginfragment());
            Toast.makeText(this, "You have been logged out", Toast.LENGTH_SHORT).show();
        });
    }

    public void showMainTabs() {
        nav.setVisibility(View.VISIBLE);
        nav.getMenu().findItem(R.id.tab_home).setChecked(true);
        show(new Homefragment());
    }

    public void go(int tabId) {
        nav.setSelectedItemId(tabId);
    }

    private void show(Fragment f) {
        getSupportFragmentManager().popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
        getSupportFragmentManager().beginTransaction().replace(R.id.container, f).commit();
    }

    public void open(Fragment f) {
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.container, f)
                .addToBackStack(null)
                .commit();
    }

    public void showNotifications() {
        Repo.listMineOnce(list -> {
            if (isFinishing() || isDestroyed()) return;
            StringBuilder sb = new StringBuilder();

            if (list == null || list.isEmpty()) {
                sb.append("No active or past appointment notifications.");
            } else {
                for (Appointment a : list) {
                    sb.append("• ").append(a.service)
                            .append(a.dentistOrEmpty().isEmpty() ? "" : " with " + a.dentistOrEmpty())
                            .append(" (").append(a.date).append(") - Status: ")
                            .append(a.status == null ? "UNKNOWN" : a.status.toUpperCase()).append("\n\n");
                }
            }

            new MaterialAlertDialogBuilder(this)
                    .setTitle("Appointment Notifications 🔔")
                    .setMessage(sb.toString().trim())
                    .setPositiveButton("OK", (dialog, which) -> dialog.dismiss())
                    .show();
        });
    }
}