package com.example.dentalclinicappointmentapp;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class Bookfragment extends Fragment {
    private final String[] services = {"Check-up", "Cleaning", "Filling", "Braces"};
    private final String[] times = {
            "09:00", "09:30", "10:00", "10:30", "11:00", "11:30", "13:00", "13:30",
            "14:00", "14:30", "15:00", "15:30", "16:00", "16:30", "17:00"};
    private final List<LocalDate> days = new ArrayList<>();

    private ChipGroup serviceGroup, dateGroup, timeGroup;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup parent, @Nullable Bundle b) {
        View v = inf.inflate(R.layout.fragmentbook, parent, false);
        serviceGroup = v.findViewById(R.id.serviceGroup);
        dateGroup = v.findViewById(R.id.dateGroup);
        timeGroup = v.findViewById(R.id.timeGroup);
        TextInputEditText nameInput = v.findViewById(R.id.name);

        for (String s : services) serviceGroup.addView(makeChip(s));
        ((Chip) serviceGroup.getChildAt(0)).setChecked(true);

        for (String t : times) timeGroup.addView(makeChip(t));

        // Reload the taken times whenever the chosen date changes.
        dateGroup.setOnCheckedStateChangeListener((group, ids) -> loadTimes());
        for (int i = 0; i < 5; i++) {
            LocalDate d = LocalDate.now().plusDays(i);
            days.add(d);
            dateGroup.addView(makeChip(d.getDayOfWeek().toString().substring(0, 3) + " " + d.getDayOfMonth()));
        }
        ((Chip) dateGroup.getChildAt(0)).setChecked(true);

        v.findViewById(R.id.confirm).setOnClickListener(x -> {
            String name = nameInput.getText().toString().trim();
            int timeId = timeGroup.getCheckedChipId();
            if (name.isEmpty() || timeId == View.NO_ID) {
                Toast.makeText(requireContext(), "Enter your name and pick a time", Toast.LENGTH_SHORT).show();
                return;
            }
            String time = ((Chip) timeGroup.findViewById(timeId)).getText().toString();
            String service = ((Chip) serviceGroup.findViewById(serviceGroup.getCheckedChipId())).getText().toString();
            Repo.book(name, service, selectedDate(), time,
                    () -> ((MainActivity) requireActivity()).go(R.id.tab_queue));
        });
        return v;
    }

    private String selectedDate() {
        int index = dateGroup.indexOfChild(dateGroup.findViewById(dateGroup.getCheckedChipId()));
        return days.get(Math.max(index, 0)).toString();
    }

    private void loadTimes() {
        Repo.takenTimes(selectedDate(), taken -> {
            if (!isAdded()) return;   // screen may have closed while loading
            for (int i = 0; i < timeGroup.getChildCount(); i++) {
                Chip c = (Chip) timeGroup.getChildAt(i);
                if (c != null) {
                    c.setEnabled(!taken.contains(c.getText().toString()));
                }
            }
        });
    }

    private Chip makeChip(String text) {
        Chip c = (Chip) LayoutInflater.from(requireContext()).inflate(R.layout.itemchip, null);
        c.setId(View.generateViewId());   // ChipGroup needs an id on every chip
        c.setText(text);
        return c;
    }
}