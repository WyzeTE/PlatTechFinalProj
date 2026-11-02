package com.example.dentalclinicappointmentapp;

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

    public Appointment(String id, String userId, String patientName, String service,
                       String date, String time, int queueNumber, String status) {
        this.id = id;
        this.userId = userId;
        this.patientName = patientName;
        this.name = patientName;
        this.service = service;
        this.date = date;
        this.time = time;
        this.queueNumber = queueNumber;
        this.status = status;
    }

    public String getDisplayName() {
        if (patientName != null && !patientName.trim().isEmpty()) return patientName;
        if (name != null && !name.trim().isEmpty()) return name;
        return "Patient";
    }
}

