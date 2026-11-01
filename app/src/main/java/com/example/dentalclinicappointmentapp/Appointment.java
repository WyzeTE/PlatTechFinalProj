package com.example.dentalclinicappointmentapp;

// One document in the "appointments" collection.
// Public fields plus an empty constructor let Firestore fill this class automatically.
public class Appointment {
    public String id = "", userId = "", patientName = "", service = "";
    public String date = "";      // ISO date, e.g. "2026-11-09"
    public String time = "";      // "10:30"
    public String status = "pending"; // pending, confirmed, serving, done, cancelled
    public int queueNumber = 0;

    public Appointment() {}

    public Appointment(String id, String userId, String patientName, String service,
                       String date, String time, int queueNumber, String status) {
        this.id = id; this.userId = userId; this.patientName = patientName;
        this.service = service; this.date = date; this.time = time;
        this.queueNumber = queueNumber; this.status = status;
    }
}
