package com.example.dentalclinicappointmentapp;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.fragment.app.Fragment;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.datepicker.CalendarConstraints;
import com.google.android.material.datepicker.DateValidatorPointForward;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.firestore.ListenerRegistration;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Bookfragment extends Fragment {
    private final String[] times = {
            "09:00", "09:30", "10:00", "10:30", "11:00", "11:30", "13:00", "13:30",
            "14:00", "14:30", "15:00", "15:30", "16:00", "16:30", "17:00"};
    private final List<LocalDate> days = new ArrayList<>();

    private ChipGroup serviceGroup;
    private ChipGroup dentistGroup;
    private ChipGroup dateGroup;
    private ChipGroup timeGroup;
    private TextInputEditText datePickerInput;
    private String customSelectedDate = "";
    private boolean updatingChips = false;   // true while WE change the date chips in code

    // Slots already booked for the chosen date and dentist (by the app or the website).
    private Set<String> takenSlots = new HashSet<>();
    private ListenerRegistration takenReg;
    // Normal chip colours, saved so a chip can go back to them after being shown as "booked".
    private ColorStateList defaultChipFill;
    private ColorStateList defaultChipStroke;
    // Re-checks the clock every 30 seconds so a slot fades out when its time passes.
    private final Handler clock = new Handler(Looper.getMainLooper());
    private final Runnable clockTick = new Runnable() {
        @Override public void run() {
            if (!isAdded()) return;
            applyTimeStates();
            clock.postDelayed(this, 30_000);
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup parent, @Nullable Bundle b) {
        View v = inf.inflate(R.layout.fragmentbook, parent, false);
        serviceGroup = v.findViewById(R.id.serviceGroup);
        dentistGroup = v.findViewById(R.id.dentistGroup);
        TextView serviceInfo = v.findViewById(R.id.serviceInfo);
        TextView dentistInfo = v.findViewById(R.id.dentistInfo);
        dateGroup = v.findViewById(R.id.dateGroup);
        timeGroup = v.findViewById(R.id.timeGroup);
        datePickerInput = v.findViewById(R.id.datePickerInput);
        TextInputEditText nameInput = v.findViewById(R.id.name);

        View btnNotif = v.findViewById(R.id.btnNotification);
        if (btnNotif != null) btnNotif.setOnClickListener(x -> ((MainActivity) requireActivity()).showNotifications());

        // Visit type and dentist, same choices as the website. Both are filled in before the
        // date chips below, because checking a date chip loads the time slots for the dentist.
        for (Catalog.Service s : Catalog.SERVICES) serviceGroup.addView(makeChip(s.name));
        ((Chip) serviceGroup.getChildAt(0)).setChecked(true);
        serviceInfo.setText(Catalog.SERVICES.get(0).description);
        serviceGroup.setOnCheckedStateChangeListener((group, ids) ->
                serviceInfo.setText(selectedService().description));

        for (Catalog.Dentist d : Catalog.DENTISTS) dentistGroup.addView(makeChip(d.name));
        ((Chip) dentistGroup.getChildAt(0)).setChecked(true);
        dentistInfo.setText(Catalog.DENTISTS.get(0).specialty);

        for (String t : times) timeGroup.addView(makeChip(t));

        // Quick date chips (starting from today)
        dateGroup.setOnCheckedStateChangeListener((group, ids) -> {
            if (updatingChips) return;   // clearCheck() from the popup must not wipe the picked date
            customSelectedDate = "";
            loadTimes();
        });
        for (int i = 0; i < 5; i++) {
            LocalDate d = clinicToday().plusDays(i);
            days.add(d);
            dateGroup.addView(makeChip(d.getDayOfWeek().toString().substring(0, 3) + " " + d.getDayOfMonth()));
        }
        ((Chip) dateGroup.getChildAt(0)).setChecked(true);

        // A different dentist has a different schedule, so refresh the taken time slots.
        // Set after the first load above so it can't fire before the date chips exist.
        dentistGroup.setOnCheckedStateChangeListener((group, ids) -> {
            dentistInfo.setText(selectedDentist().specialty);
            loadTimes();
        });

        // Popup Material DatePicker
        if (datePickerInput != null) {
            LocalDate today = clinicToday();
            datePickerInput.setText(today.getDayOfWeek().toString().substring(0, 3) + ", " + today.getMonth().toString().substring(0, 3) + " " + today.getDayOfMonth());
            customSelectedDate = today.toString();

            datePickerInput.setOnClickListener(x -> showPopupDatePicker());
        }

        View confirm = v.findViewById(R.id.confirm);
        confirm.setOnClickListener(x -> {
            String name = nameInput.getText() != null ? nameInput.getText().toString().trim() : "";
            int timeId = timeGroup.getCheckedChipId();
            if (name.isEmpty() || timeId == View.NO_ID) {
                Toast.makeText(requireContext(), "Enter your name and pick a time slot", Toast.LENGTH_SHORT).show();
                return;
            }

            // selectedDate() always gives an ISO date (it comes from LocalDate.toString()), so parsing is safe.
            LocalDate picked = LocalDate.parse(selectedDate());
            if (picked.isBefore(clinicToday())) {
                Toast.makeText(requireContext(), "Selected date has already passed. Please select a future date.", Toast.LENGTH_SHORT).show();
                return;
            }

            String time = ((Chip) timeGroup.findViewById(timeId)).getText().toString();

            // The slot may have passed while the screen was open, so check again right now.
            if (isPast(picked, time, LocalDateTime.now(ZoneId.systemDefault()))) {
                Toast.makeText(requireContext(), "That time has already passed. Please pick a later slot.", Toast.LENGTH_SHORT).show();
                applyTimeStates();
                return;
            }

            confirm.setEnabled(false);   // stop double taps while saving
            Repo.book(name, selectedService().name, selectedDentist(), selectedDate(), time,
                    () -> {
                        if (!isAdded()) return;
                        confirm.setEnabled(true);
                        ((MainActivity) requireActivity()).go(R.id.tab_queue);
                    },
                    msg -> {
                        if (!isAdded()) return;
                        confirm.setEnabled(true);
                        Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show();
                        loadTimes();   // refresh so the taken slot greys out
                    });
        });

        clock.postDelayed(clockTick, 30_000);
        return v;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (takenReg != null) takenReg.remove();   // stop listening when the screen is gone
        takenReg = null;
        clock.removeCallbacks(clockTick);
    }

    private void showPopupDatePicker() {
        CalendarConstraints constraints = new CalendarConstraints.Builder()
                .setValidator(DateValidatorPointForward.now())
                .build();

        MaterialDatePicker<Long> datePicker = MaterialDatePicker.Builder.datePicker()
                .setTitleText("Select appointment date")
                .setCalendarConstraints(constraints)
                .setSelection(MaterialDatePicker.todayInUtcMilliseconds())
                .build();

        datePicker.addOnPositiveButtonClickListener(selection -> {
            Instant instant = Instant.ofEpochMilli(selection);
            LocalDate selected = instant.atZone(ZoneId.of("UTC")).toLocalDate();
            customSelectedDate = selected.toString();
            if (datePickerInput != null) {
                datePickerInput.setText(selected.getDayOfWeek().toString().substring(0, 3) + ", " + selected.getMonth().toString().substring(0, 3) + " " + selected.getDayOfMonth());
            }
            if (dateGroup != null) {
                updatingChips = true;
                dateGroup.clearCheck();
                updatingChips = false;
            }
            loadTimes();
        });

        datePicker.show(getParentFragmentManager(), "DATE_PICKER");
    }

    private String selectedDate() {
        if (!customSelectedDate.isEmpty()) {
            return customSelectedDate;
        }
        int index = dateGroup.indexOfChild(dateGroup.findViewById(dateGroup.getCheckedChipId()));
        return days.get(Math.max(index, 0)).toString();
    }

    private Catalog.Service selectedService() {
        int index = serviceGroup.indexOfChild(serviceGroup.findViewById(serviceGroup.getCheckedChipId()));
        return Catalog.SERVICES.get(Math.max(index, 0));
    }

    private Catalog.Dentist selectedDentist() {
        int index = dentistGroup.indexOfChild(dentistGroup.findViewById(dentistGroup.getCheckedChipId()));
        return Catalog.DENTISTS.get(Math.max(index, 0));
    }

    private void loadTimes() {
        final String date = selectedDate();
        final Catalog.Dentist dentist = selectedDentist();

        // Stop listening to the previous date/dentist, then show the clock-based state straight away.
        if (takenReg != null) takenReg.remove();
        takenSlots = new HashSet<>();
        applyTimeStates();

        // Live list of booked slots. This runs again whenever someone books (app or website).
        takenReg = Repo.listenTakenTimes(date, dentist.name, taken -> {
            if (!isAdded()) return;   // screen may have closed while loading
            // user already switched date or dentist, so ignore this old answer
            if (!date.equals(selectedDate()) || dentist != selectedDentist()) return;
            takenSlots = taken;
            applyTimeStates();
        });
    }

    // Sets every time chip to one of three looks:
    //   available: normal and tappable
    //   past:      faded and not tappable (the time has already gone by)
    //   booked:    red and not tappable (someone else already has that slot)
    // A slot that is both past and booked is shown as past.
    private void applyTimeStates() {
        if (timeGroup == null || timeGroup.getChildCount() == 0) return;

        LocalDate date = LocalDate.parse(selectedDate());
        LocalDateTime now = LocalDateTime.now(ZoneId.systemDefault());
        int danger = ContextCompat.getColor(requireContext(), R.color.danger);

        for (int i = 0; i < timeGroup.getChildCount(); i++) {
            Chip c = (Chip) timeGroup.getChildAt(i);
            if (c == null) continue;

            // Save the normal colours from the first chip, before anything gets restyled.
            if (defaultChipFill == null) {
                defaultChipFill = c.getChipBackgroundColor();
                defaultChipStroke = c.getChipStrokeColor();
            }

            String time = c.getText().toString();
            boolean past = isPast(date, time, now);
            boolean booked = !past && takenSlots.contains(time);

            c.setEnabled(!past && !booked);
            if (!c.isEnabled() && c.isChecked()) c.setChecked(false);   // never keep an unavailable slot selected

            if (booked) {
                c.setChipBackgroundColor(ColorStateList.valueOf(ColorUtils.setAlphaComponent(danger, 40)));
                c.setChipStrokeColor(ColorStateList.valueOf(danger));
                c.setContentDescription(time + ", already booked");
            } else {
                if (defaultChipFill != null) c.setChipBackgroundColor(defaultChipFill);
                if (defaultChipStroke != null) c.setChipStrokeColor(defaultChipStroke);
                c.setContentDescription(past ? time + ", time has passed" : time);
            }
        }
    }

    // True when the slot's start time is now or earlier, in the phone's own time zone.
    private static boolean isPast(LocalDate date, String time, LocalDateTime now) {
        LocalTime slot;
        try {
            slot = LocalTime.parse(time);   // chips read like "09:30"
        } catch (DateTimeParseException e) {
            return false;
        }
        return !LocalDateTime.of(date, slot).isAfter(now);
    }

    // "Today" in the phone's own time zone.
    private static LocalDate clinicToday() {
        return LocalDate.now(ZoneId.systemDefault());
    }

    private Chip makeChip(String text) {
        Chip c = (Chip) LayoutInflater.from(requireContext()).inflate(R.layout.itemchip, null);
        c.setId(View.generateViewId());   // ChipGroup needs an id on every chip
        c.setText(text);
        return c;
    }
}