package com.example.dentalclinicappointmentapp;

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;


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

        // Tapping the tab you are already on closes any screen opened on top of it
        // (Dentists, Services, My Account) and goes back to the tab itself.
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
    // The logout is written to the activity log first (see Repo.signOut), then we leave.
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
        // A tab or the login screen replaces everything, including any screen opened with open().
        getSupportFragmentManager().popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
        getSupportFragmentManager().beginTransaction().replace(R.id.container, f).commit();
    }

    // Opens a screen on top of the current tab (Dentists, Services, My Account).
    // The phone's back button, or the arrow in the screen's header, returns to the tab.
    public void open(Fragment f) {
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.container, f)
                .addToBackStack(null)
                .commit();
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