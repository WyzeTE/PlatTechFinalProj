package com.example.dentalclinicappointmentapp;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.firebase.firestore.ListenerRegistration;

public class Homefragment extends Fragment {
    private ListenerRegistration reg;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup parent, @Nullable Bundle b) {
        View v = inf.inflate(R.layout.fragmenthome, parent, false);
        View card = v.findViewById(R.id.card);
        View empty = v.findViewById(R.id.empty);
        TextView service = v.findViewById(R.id.service);
        TextView when = v.findViewById(R.id.when);

        v.findViewById(R.id.btnBook).setOnClickListener(x -> ((MainActivity) requireActivity()).go(R.id.tab_book));
        v.findViewById(R.id.btnQueue).setOnClickListener(x -> ((MainActivity) requireActivity()).go(R.id.tab_queue));
        View queueGrid = v.findViewById(R.id.btnQueueGrid);
        if (queueGrid != null) queueGrid.setOnClickListener(x -> ((MainActivity) requireActivity()).go(R.id.tab_queue));

        v.findViewById(R.id.btnServices).setOnClickListener(x -> ((MainActivity) requireActivity()).open(new Servicesfragment()));
        v.findViewById(R.id.btnDentists).setOnClickListener(x -> ((MainActivity) requireActivity()).open(new Dentistsfragment()));
        v.findViewById(R.id.btnAccount).setOnClickListener(x -> ((MainActivity) requireActivity()).open(new Accountfragment()));

        View btnNotif = v.findViewById(R.id.btnNotification);
        if (btnNotif != null) btnNotif.setOnClickListener(x -> ((MainActivity) requireActivity()).showNotifications());

        View btnLogout = v.findViewById(R.id.btnLogout);
        if (btnLogout != null) btnLogout.setOnClickListener(x -> ((MainActivity) requireActivity()).confirmLogout());

        reg = Repo.listenMine(list -> {
            Appointment next = Repo.firstActive(list);
            card.setVisibility(next == null ? View.GONE : View.VISIBLE);
            empty.setVisibility(next == null ? View.VISIBLE : View.GONE);
            if (next != null) {
                service.setText(next.service);
                String dentist = next.dentistOrEmpty();
                when.setText(next.date + ", " + next.time + " (" + next.status + ")"
                        + (dentist.isEmpty() ? "" : "\n" + dentist));
            }
        });
        return v;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (reg != null) reg.remove();   // stop listening when the screen is gone
    }
}