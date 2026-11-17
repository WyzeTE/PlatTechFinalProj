package com.example.dentalclinicappointmentapp;

import android.content.Context;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
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
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class RescheduleSheet extends BottomSheetDialogFragment {
    private static final String[] TIMES = {
            "09:00", "09:30", "10:00", "10:30", "11:00", "11:30", "13:00", "13:30",
            "14:00", "14:30", "15:00", "15:30", "16:00", "16:30", "17:00"};

    private static final DateTimeFormatter SHOWN =
            DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", Locale.US);

    private Appointment old;
    private Catalog.Dentist dentist;
    private LocalDate date;

    private ChipGroup timeGroup;
    private TextInputEditText dateInput;
    private Button confirm;

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

    private static Catalog.Dentist dentistFor(Appointment a) {
        String name = a.dentistOrEmpty().trim();
        for (Catalog.Dentist d : Catalog.DENTISTS) {
            if (d.name.equalsIgnoreCase(name)) return d;
        }
        return null;
    }

    public static void open(Fragment host, Appointment a) {
        if (dentistFor(a) == null) {
            Toast.makeText(host.requireContext(),
                    "This appointment has no dentist saved. Please cancel it and book again.",
                    Toast.LENGTH_LONG).show();
            return;
        }

        Bundle b = new Bundle();
        b.putString("id", a.id);
        b.putString("userId", a.userId);
        b.putString("name", a.patientName == null || a.patientName.isEmpty() ? a.name : a.patientName);
        b.putString("service", a.service);
        b.putString("dentist", a.dentist);
        b.putString("email", a.email);
        b.putString("date", a.date);
        b.putString("time", a.time);

        RescheduleSheet sheet = new RescheduleSheet();
        sheet.setArguments(b);
        sheet.show(host.getParentFragmentManager(), "RESCHEDULE");
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup parent, @Nullable Bundle saved) {
        View v = inf.inflate(R.layout.sheet_reschedule, parent, false);

        Bundle b = requireArguments();
        old = new Appointment(
                b.getString("id", ""),
                b.getString("userId", ""),
                b.getString("name", ""),
                b.getString("service", ""),
                b.getString("dentist", ""),
                b.getString("date", ""),
                b.getString("time", ""),
                0,
                "pending"
        );
        old.email = b.getString("email", "");
        dentist = dentistFor(old);

        timeGroup = v.findViewById(R.id.timeGroup);
        dateInput = v.findViewById(R.id.dateInput);
        confirm = v.findViewById(R.id.confirmReschedule);

        ((TextView) v.findViewById(R.id.currentInfo)).setText(
                "Now: " + old.date + ", " + old.time + "\n" + old.service + " with " + old.dentistOrEmpty());

        LocalDate today = AppointmentDatePolicy.today();
        LocalDate latest = AppointmentDatePolicy.latestDate();

        try {
            date = LocalDate.parse(old.date);
        } catch (DateTimeParseException e) {
            date = today;
        }

        if (date.isBefore(today) || date.isAfter(latest)) {
            date = today;
        }

        dateInput.setText(date.format(SHOWN));
        dateInput.setOnClickListener(x -> showDatePicker());

        for (String t : TIMES) {
            Chip c = (Chip) LayoutInflater.from(requireContext()).inflate(R.layout.itemchip, null);
            c.setId(View.generateViewId());
            c.setText(t);
            timeGroup.addView(c);
        }

        confirm.setOnClickListener(x -> confirmReschedule());

        loadTimes();
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

    private void showDatePicker() {
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

        MaterialDatePicker<Long> picker = MaterialDatePicker.Builder.datePicker()
                .setTitleText("Select new date")
                .setCalendarConstraints(constraints)
                .setSelection(date.atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli())
                .build();

        picker.addOnPositiveButtonClickListener(selection -> {
            date = Instant.ofEpochMilli(selection).atZone(ZoneId.of("UTC")).toLocalDate();
            dateInput.setText(date.format(SHOWN));
            loadTimes();
        });

        picker.show(getParentFragmentManager(), "RESCHEDULE_DATE");
    }

    private void confirmReschedule() {
        int id = timeGroup.getCheckedChipId();

        if (!AppointmentDatePolicy.isAllowed(date)) {
            Toast.makeText(requireContext(),
                    "Please choose a new date within the next 90 days.",
                    Toast.LENGTH_LONG).show();
            return;
        }

        if (id == View.NO_ID) {
            Toast.makeText(requireContext(), "Pick a new time slot", Toast.LENGTH_SHORT).show();
            return;
        }

        String time = ((Chip) timeGroup.findViewById(id)).getText().toString();

        if (isPast(date, time, LocalDateTime.now(ZoneId.systemDefault()))) {
            Toast.makeText(requireContext(),
                    "That time has already passed. Please pick a later slot.",
                    Toast.LENGTH_SHORT).show();
            applyTimeStates();
            return;
        }

        final Context app = requireContext().getApplicationContext();
        confirm.setEnabled(false);

        Repo.reschedule(old, dentist, date.toString(), time,
                () -> {
                    Toast.makeText(app,
                            "Rescheduled. Waiting for the clinic to confirm the new time.",
                            Toast.LENGTH_LONG).show();
                    if (isAdded()) dismiss();
                },
                msg -> {
                    Toast.makeText(app, msg, Toast.LENGTH_LONG).show();
                    if (!isAdded()) return;
                    confirm.setEnabled(true);
                    loadTimes();
                });
    }

    private void loadTimes() {
        final LocalDate chosen = date;

        if (takenReg != null) takenReg.remove();
        takenSlots = new HashSet<>();
        applyTimeStates();

        takenReg = Repo.listenTakenTimes(chosen.toString(), dentist.name, taken -> {
            if (!isAdded() || !chosen.equals(date)) return;
            takenSlots = taken;
            applyTimeStates();
        });
    }

    private void applyTimeStates() {
        if (timeGroup == null || timeGroup.getChildCount() == 0) return;

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
}