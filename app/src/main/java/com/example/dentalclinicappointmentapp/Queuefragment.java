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
    private ListenerRegistration mineReg;
    private ListenerRegistration servReg;
    private String servDate = "";
    private Appointment next;
    private int serving = 0;

    private View content;
    private View emptyQueue;
    private TextView subtitle;
    private TextView status;
    private TextView number;
    private TextView wait;
    private TextView nowServing;
    private TextView aheadText;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup parent, @Nullable Bundle b) {
        View v = inf.inflate(R.layout.fragmentqueue, parent, false);
        content = v.findViewById(R.id.content);
        emptyQueue = v.findViewById(R.id.emptyQueue);
        subtitle = v.findViewById(R.id.subtitle);
        status = v.findViewById(R.id.status);
        number = v.findViewById(R.id.number);
        wait = v.findViewById(R.id.wait);
        nowServing = v.findViewById(R.id.nowServing);
        aheadText = v.findViewById(R.id.ahead);

        v.findViewById(R.id.cancel).setOnClickListener(x -> {
            if (next != null) Repo.cancel(next.id);
        });

        View btnBookEmpty = v.findViewById(R.id.btnBookQueueEmpty);
        if (btnBookEmpty != null) {
            btnBookEmpty.setOnClickListener(x -> ((MainActivity) requireActivity()).go(R.id.tab_book));
        }

        View btnNotif = v.findViewById(R.id.btnNotification);
        if (btnNotif != null) btnNotif.setOnClickListener(x -> ((MainActivity) requireActivity()).showNotifications());

        mineReg = Repo.listenMine(list -> {
            next = Repo.firstActive(list);
            if (next == null) {
                // Nothing active anymore, so stop watching "now serving".
                if (servReg != null) { servReg.remove(); servReg = null; }
                servDate = "";
                serving = 0;
            } else if (!next.date.equals(servDate)) {
                // Start watching "now serving" for this appointment's date (once per date).
                if (servReg != null) servReg.remove();
                serving = 0;
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
            if (emptyQueue != null) emptyQueue.setVisibility(View.VISIBLE);
            subtitle.setText("No active appointment");
            return;
        }
        content.setVisibility(View.VISIBLE);
        if (emptyQueue != null) emptyQueue.setVisibility(View.GONE);
        subtitle.setText(next.date + ", " + next.service);
        status.setText(next.status == null ? "" : next.status.toUpperCase(Locale.ROOT));
        number.setText(String.format(Locale.US, "A-%03d", next.queueNumber));

        int ahead = Math.max(0, next.queueNumber - serving - 1);
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