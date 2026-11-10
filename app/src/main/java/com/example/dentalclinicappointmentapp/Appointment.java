package com.example.dentalclinicappointmentapp;

import com.google.firebase.firestore.Exclude;

// One document in the "appointments" collection.
// Public fields plus an empty constructor let Firestore fill this class automatically.
public class Appointment {
    public String id = "";
    public String userId = "";
    public String patientName = "";
    public String name = ""; // Interoperability fallback field for web
    public String service = "";
    public String dentist = "";
    public String email = "";
    public String date = "";      // ISO date, e.g. "2026-11-09"
    public String time = "";      // "10:30"
    public String status = "pending"; // pending, approved/confirmed, serving, done/completed, cancelled, rejected
    public int queueNumber = 0;

    public Appointment() {}

    public Appointment(String id, String userId, String patientName, String service, String dentist,
                       String date, String time, int queueNumber, String status) {
        this.id = id;
        this.userId = userId;
        this.patientName = patientName;
        this.name = patientName;
        this.service = service;
        this.dentist = dentist;
        this.date = date;
        this.time = time;
        this.queueNumber = queueNumber;
        this.status = status;
    }

    // Dentist's name, or "" for older bookings made before dentists could be chosen.
    // (Not named getX, so Firestore does not save it as a field.)
    public String dentistOrEmpty() {
        return dentist == null ? "" : dentist;
    }

    @Exclude   // helper for the screens only; keeps Firestore from saving a "displayName" field
    public String getDisplayName() {
        if (patientName != null && !patientName.trim().isEmpty()) return patientName;
        if (name != null && !name.trim().isEmpty()) return name;
        return "Patient";
    }
}