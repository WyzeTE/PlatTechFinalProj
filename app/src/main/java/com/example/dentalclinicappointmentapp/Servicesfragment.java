package com.example.dentalclinicappointmentapp;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

// Displays the services listed in Catalog.
public class Servicesfragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inf,
            @Nullable ViewGroup parent,
            @Nullable Bundle b) {

        View v = inf.inflate(R.layout.fragmentservices, parent, false);

        // Back button
        View btnBack = v.findViewById(R.id.btnBack);

        btnBack.setOnClickListener(x ->
                getParentFragmentManager().popBackStack()
        );

        // Service list
        LinearLayout list = v.findViewById(R.id.list);

        // Prevent duplicate cards if the view is recreated
        list.removeAllViews();

        // Load services from Catalog
        for (Catalog.Service s : Catalog.SERVICES) {

            View card = inf.inflate(
                    R.layout.itemservice,
                    list,
                    false
            );

            TextView name = card.findViewById(R.id.name);
            TextView description = card.findViewById(R.id.description);

            name.setText(s.name);
            description.setText(s.description);

            list.addView(card);
        }

        return v;
    }
}