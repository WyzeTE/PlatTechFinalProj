package com.example.dentalclinicappointmentapp;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;

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

        // Sign in first, then open the Home tab.
        Repo.signIn(() -> nav.setSelectedItemId(R.id.tab_home));
    }

    // Fragments call this to jump to another tab.
    public void go(int tabId) {
        nav.setSelectedItemId(tabId);
    }

    private void show(Fragment f) {
        getSupportFragmentManager().beginTransaction().replace(R.id.container, f).commit();
    }
}