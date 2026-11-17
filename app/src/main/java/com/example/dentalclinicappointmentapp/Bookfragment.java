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
import com.google.android.material.datepicker.DateValidatorPointBackward;
import com.google.android.material.datepicker.CompositeDateValidator;
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
import java.util.Arrays;
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
    private boolean updatingChips = false;

    private Set<String> takenSlots = new HashSet<>();
    private ListenerRegistration takenReg;
    private ColorStateList defaultChipFill;
    private ColorStateList defaultChipStroke;

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

        for (Catalog.Service s : Catalog.SERVICES) serviceGroup.addView(makeChip(s.name));
        ((Chip) serviceGroup.getChildAt(0)).setChecked(true);
        serviceInfo.setText(Catalog.SERVICES.get(0).description);
        serviceGroup.setOnCheckedStateChangeListener((group, ids) ->
                serviceInfo.setText(selectedService().description));

        for (Catalog.Dentist d : Catalog.DENTISTS) dentistGroup.addView(makeChip(d.name));
        ((Chip) dentistGroup.getChildAt(0)).setChecked(true);
        dentistInfo.setText(Catalog.DENTISTS.get(0).specialty);

        for (String t : times) timeGroup.addView(makeChip(t));

        dateGroup.setOnCheckedStateChangeListener((group, ids) -> {
            if (updatingChips) return;
            customSelectedDate = "";
            loadTimes();
        });

        for (int i = 0; i < 5; i++) {
            LocalDate d = clinicToday().plusDays(i);
            days.add(d);
            dateGroup.addView(makeChip(d.getDayOfWeek().toString().substring(0, 3) + " " + d.getDayOfMonth()));
        }
        ((Chip) dateGroup.getChildAt(0)).setChecked(true);

        dentistGroup.setOnCheckedStateChangeListener((group, ids) -> {
            dentistInfo.setText(selectedDentist().specialty);
            loadTimes();
        });

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

            LocalDate picked = LocalDate.parse(selectedDate());
            if (!AppointmentDatePolicy.isAllowed(picked)) {
                Toast.makeText(requireContext(), "Please choose an appointment date within the next 90 days.", Toast.LENGTH_LONG).show();
                return;
            }

            String time = ((Chip) timeGroup.findViewById(timeId)).getText().toString();

            if (isPast(picked, time, LocalDateTime.now(ZoneId.systemDefault()))) {
                Toast.makeText(requireContext(), "That time has already passed. Please pick a later slot.", Toast.LENGTH_SHORT).show();
                applyTimeStates();
                return;
            }

            confirm.setEnabled(false);
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
                        loadTimes();
                    });
        });

        clock.postDelayed(clockTick, 30_000);
        return v;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (takenReg != null) takenReg.remove();
        takenReg = null;
        clock.removeCallbacks(clockTick);
    }

    private void showPopupDatePicker() {
        LocalDate today = AppointmentDatePolicy.today();
        LocalDate latest = AppointmentDatePolicy.latestDate();
        long startMillis = AppointmentDatePolicy.utcMillis(today);
        long endMillis = AppointmentDatePolicy.utcMillis(latest);

        CalendarConstraints constraints = new CalendarConstraints.Builder()
                .setStart(startMillis)
                .setEnd(endMillis)
                .setValidator(CompositeDateValidator.allOf(Arrays.asList(
                        DateValidatorPointForward.from(startMillis),
                        DateValidatorPointBackward.before(endMillis))))
                .build();

        MaterialDatePicker<Long> datePicker = MaterialDatePicker.Builder.datePicker()
                .setTitleText("Select appointment date")
                .setCalendarConstraints(constraints)
                .setSelection(startMillis)
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

        if (takenReg != null) takenReg.remove();
        takenSlots = new HashSet<>();
        applyTimeStates();

        takenReg = Repo.listenTakenTimes(date, dentist.name, taken -> {
            if (!isAdded()) return;
            if (!date.equals(selectedDate()) || dentist != selectedDentist()) return;
            takenSlots = taken;
            applyTimeStates();
        });
    }

    private void applyTimeStates() {
        if (timeGroup == null || timeGroup.getChildCount() == 0) return;

        LocalDate date = LocalDate.parse(selectedDate());
        LocalDateTime now = LocalDateTime.now(ZoneId.systemDefault());
        int danger = ContextCompat.getColor(requireContext(), R.color.danger);

        for (int i = 0; i < timeGroup.getChildCount(); i++) {
            Chip c = (Chip) timeGroup.getChildAt(i);
            if (c == null) continue;

            if (defaultChipFill == null) {
                defaultChipFill = c.getChipBackgroundColor();
                defaultChipStroke = c.getChipStrokeColor();
            }

            String time = c.getText().toString();
            boolean past = isPast(date, time, now);
            boolean booked = !past && takenSlots.contains(time);

            c.setEnabled(!past && !booked);
            if (!c.isEnabled() && c.isChecked()) c.setChecked(false);

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

    private static boolean isPast(LocalDate date, String time, LocalDateTime now) {
        LocalTime slot;
        try {
            slot = LocalTime.parse(time);
        } catch (DateTimeParseException e) {
            return false;
        }
        return !LocalDateTime.of(date, slot).isAfter(now);
    }

    private static LocalDate clinicToday() {
        return LocalDate.now(ZoneId.systemDefault());
    }

    private Chip makeChip(String text) {
        Chip c = (Chip) LayoutInflater.from(requireContext()).inflate(R.layout.itemchip, null);
        c.setId(View.generateViewId());
        c.setText(text);
        return c;
    }
}