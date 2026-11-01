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

import java.util.Locale;

public class Queuefragment extends Fragment {
    private ListenerRegistration mineReg, servReg;
    private String servDate = "";
    private Appointment next;
    private int serving = 0;

    private View content;
    private TextView subtitle, status, number, wait, nowServing, aheadText;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup parent, @Nullable Bundle b) {
        View v = inf.inflate(R.layout.fragmentqueue, parent, false);
        content = v.findViewById(R.id.content);
        subtitle = v.findViewById(R.id.subtitle);
        status = v.findViewById(R.id.status);
        number = v.findViewById(R.id.number);
        wait = v.findViewById(R.id.wait);
        nowServing = v.findViewById(R.id.nowServing);
        aheadText = v.findViewById(R.id.ahead);

        v.findViewById(R.id.cancel).setOnClickListener(x -> {
            if (next != null) Repo.cancel(next.id);
        });

        mineReg = Repo.listenMine(list -> {
            next = Repo.firstActive(list);
            // Start watching "now serving" for this appointment's date (once per date).
            if (next != null && !next.date.equals(servDate)) {
                if (servReg != null) servReg.remove();
                servDate = next.date;
                servReg = Repo.listenServing(servDate, n -> { serving = n; render(); });
            }
            render();
        });
        return v;
    }

    private void render() {
        if (next == null) {
            content.setVisibility(View.GONE);
            subtitle.setText("No active appointment");
            return;
        }
        content.setVisibility(View.VISIBLE);
        subtitle.setText(next.date + ", " + next.service);
        status.setText(next.status.toUpperCase(Locale.ROOT));
        number.setText(String.format(Locale.US, "A-%03d", next.queueNumber));

        int ahead = Math.max(0, next.queueNumber - serving - 1);
        // Assumes about 15 minutes per patient. Change it to match the clinic.
        wait.setText("Estimated wait: about " + (ahead * 15) + " minutes");
        nowServing.setText(serving == 0 ? "-" : String.format(Locale.US, "A-%03d", serving));
        aheadText.setText(ahead + " people");
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (mineReg != null) mineReg.remove();
        if (servReg != null) servReg.remove();
    }
}
