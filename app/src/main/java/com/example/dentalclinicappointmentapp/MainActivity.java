package com.example.dentalclinicappointmentapp;

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;

public class MainActivity extends AppCompatActivity {
    private BottomNavigationView nav;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        nav = findViewById(R.id.nav);

        nav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.tab_home) show(new Homefragment());
            else if (id == R.id.tab_book) show(new Bookfragment());
            else show(new Queuefragment());
            return true;
        });

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

    // Asks first, so a stray tap doesn't log the patient out.
    public void confirmLogout() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Log out?")
                .setMessage("Your appointments stay saved. Log in again anytime, here or on the clinic website.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Log out", (dialog, which) -> logout())
                .show();
    }

    // Signs out of Firebase Auth (the same accounts the website uses) and goes back to login.
    public void logout() {
        Repo.signOut();
        nav.setVisibility(View.GONE);
        show(new Loginfragment());
        Toast.makeText(this, "You have been logged out", Toast.LENGTH_SHORT).show();
    }

    public void showMainTabs() {
        nav.setVisibility(View.VISIBLE);
        // Tick Home and show it directly. After a log out the Home tab is still the selected one,
        // so setSelectedItemId alone would not reliably swap the login screen out.
        nav.getMenu().findItem(R.id.tab_home).setChecked(true);
        show(new Homefragment());
    }

    // Fragments call this to jump to another tab.
    public void go(int tabId) {
        nav.setSelectedItemId(tabId);
    }

    private void show(Fragment f) {
        getSupportFragmentManager().beginTransaction().replace(R.id.container, f).commit();
    }

    // Show Notification updates dialog
    public void showNotifications() {
        Repo.listMineOnce(list -> {
            if (isFinishing() || isDestroyed()) return;
            StringBuilder sb = new StringBuilder();
            if (list == null || list.isEmpty()) {
                sb.append("No active or past appointment notifications.");
            } else {
                for (Appointment a : list) {
                    sb.append("• ").append(a.service)
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