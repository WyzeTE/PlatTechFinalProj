package com.example.dentalclinicappointmentapp;

import java.time.LocalDate;
import java.time.ZoneId;

/** Shared appointment booking window used by booking and rescheduling. */
public final class AppointmentDatePolicy {
    public static final int MAX_DAYS_AHEAD = 90;

    private AppointmentDatePolicy() { }

    public static LocalDate today() {
        return LocalDate.now(ZoneId.systemDefault());
    }

    public static LocalDate latestDate() {
        return today().plusDays(MAX_DAYS_AHEAD);
    }

    public static boolean isAllowed(LocalDate date) {
        return date != null && !date.isBefore(today()) && !date.isAfter(latestDate());
    }

    public static long utcMillis(LocalDate date) {
        return date.atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli();
    }
}