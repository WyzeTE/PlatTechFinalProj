package com.example.dentalclinicappointmentapp;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

// "Meet Our Dentists": one card per dentist in Catalog (photo, name, specialty, short note).
public class Dentistsfragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup parent, @Nullable Bundle b) {
        View v = inf.inflate(R.layout.fragmentdentists, parent, false);
        v.findViewById(R.id.btnBack).setOnClickListener(x -> getParentFragmentManager().popBackStack());

        LinearLayout list = v.findViewById(R.id.list);
        for (Catalog.Dentist d : Catalog.DENTISTS) {
            View card = inf.inflate(R.layout.itemdentist, list, false);
            ((ImageView) card.findViewById(R.id.photo)).setImageResource(d.photo);
            ((TextView) card.findViewById(R.id.name)).setText(d.name);
            ((TextView) card.findViewById(R.id.specialty)).setText(d.specialty);
            ((TextView) card.findViewById(R.id.blurb)).setText(d.blurb);
            list.addView(card);
        }
        return v;
    }
}