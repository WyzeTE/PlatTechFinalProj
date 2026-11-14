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

// Lists the services in Catalog, the same ones the Book tab offers.
public class Servicesfragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup parent, @Nullable Bundle b) {
        View v = inf.inflate(R.layout.fragmentservices, parent, false);
        v.findViewById(R.id.btnBack).setOnClickListener(x -> getParentFragmentManager().popBackStack());

        LinearLayout list = v.findViewById(R.id.list);
        for (Catalog.Service s : Catalog.SERVICES) {
            View card = inf.inflate(R.layout.itemservice, list, false);
            ((TextView) card.findViewById(R.id.name)).setText(s.name);
            ((TextView) card.findViewById(R.id.description)).setText(s.description);
            list.addView(card);
        }
        return v;
    }
}