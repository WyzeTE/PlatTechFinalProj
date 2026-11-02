package com.example.dentalclinicappointmentapp;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// All database and authentication work lives here.
public class Repo {
    public interface Callback<T> { void on(T value); }

    // Firestore field names and status values, written once so they can't be mistyped.
    private static final String STATUS = "status";
    private static final String DATE = "date";
    private static final String TIME = "time";
    private static final String QUEUE_NUMBER = "queueNumber";
    private static final String USER_ID = "userId";
    private static final String STATUS_CANCELLED = "cancelled";
    private static final String STATUS_REJECTED = "rejected";
    private static final String STATUS_SERVING = "serving";

    private static final FirebaseAuth auth = FirebaseAuth.getInstance();
    private static final CollectionReference col =
            FirebaseFirestore.getInstance().collection("appointments");

    public static boolean isLoggedIn() {
        if (auth.getCurrentUser() != null && auth.getCurrentUser().isAnonymous()) {
            auth.signOut();
        }
        return auth.getCurrentUser() != null && !auth.getCurrentUser().isAnonymous();
    }

    public static String getUserEmail() {
        return (auth.getCurrentUser() != null && auth.getCurrentUser().getEmail() != null)
                ? auth.getCurrentUser().getEmail()
                : "Patient";
    }

    public static void signInWithEmail(String email, String password, Runnable done, Callback<String> onError) {
        auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(r -> done.run())
                .addOnFailureListener(e -> onError.on(e.getLocalizedMessage()));
    }

    public static void registerWithEmail(String email, String password, Runnable done, Callback<String> onError) {
        auth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(r -> done.run())
                .addOnFailureListener(e -> onError.on(e.getLocalizedMessage()));
    }

    public static void signOut() {
        auth.signOut();
    }

    // Default anonymous fallback if needed
    public static void signIn(Runnable done) {
        if (auth.getCurrentUser() != null) { done.run(); return; }
        auth.signInAnonymously().addOnSuccessListener(r -> done.run());
    }

    private static String uid() {
        return auth.getCurrentUser() == null ? "" : auth.getCurrentUser().getUid();
    }

    // Times already booked on a date, so the app can grey them out.
    public static void takenTimes(String date, Callback<Set<String>> cb) {
        col.whereEqualTo(DATE, date).get().addOnSuccessListener(snap -> {
            Set<String> taken = new HashSet<>();
            for (DocumentSnapshot d : snap.getDocuments()) {
                if (isActive(d.getString(STATUS))) {
                    taken.add(d.getString(TIME));
                }
            }
            cb.on(taken);
        });
    }

    // A booking's document id is date + time (e.g. "2026-11-09_1030"), so one slot can
    // only ever hold one document. The transaction checks that slot and writes the booking
    // in one step, so two people can't both grab the same time.
    // Queue number = highest number already given that day + 1 (same rule the admin
    // website uses when it approves a booking; cancelled ones are set to 0 there).
    public static void book(String name, String service, String date, String time,
                            Runnable done, Callback<String> onError) {
        DocumentReference ref = col.document(date + "_" + time.replace(":", ""));

        col.whereEqualTo(DATE, date).get().addOnSuccessListener(snap -> {
            int max = 0;
            for (DocumentSnapshot d : snap.getDocuments()) {
                Long n = d.getLong(QUEUE_NUMBER);
                if (n != null && n > max) max = n.intValue();
            }
            final int queueNumber = max + 1;

            FirebaseFirestore.getInstance().runTransaction(tx -> {
                DocumentSnapshot slot = tx.get(ref);
                if (slot.exists() && isActive(slot.getString(STATUS))) {
                    return Boolean.FALSE;   // somebody already holds this slot
                }
                Appointment a = new Appointment(ref.getId(), uid(), name, service, date, time, queueNumber, "pending");
                if (auth.getCurrentUser() != null && auth.getCurrentUser().getEmail() != null) {
                    a.email = auth.getCurrentUser().getEmail();
                }
                tx.set(ref, a);
                return Boolean.TRUE;
            }).addOnSuccessListener(booked -> {
                if (booked) done.run();
                else onError.on("That time was just booked by someone else. Please pick another slot.");
            }).addOnFailureListener(e -> onError.on("Could not book: " + e.getLocalizedMessage()));
        }).addOnFailureListener(e -> onError.on("Could not book: " + e.getLocalizedMessage()));
    }

    // A slot is "held" unless its booking was cancelled or rejected.
    private static boolean isActive(String status) {
        return status != null && !status.equalsIgnoreCase(STATUS_CANCELLED) && !status.equalsIgnoreCase(STATUS_REJECTED);
    }

    public static void cancel(String id) {
        col.document(id).update(STATUS, STATUS_CANCELLED);
    }

    private static List<Appointment> sorted(List<Appointment> list) {
        Collections.sort(list, (x, y) -> (x.date + x.time).compareTo(y.date + y.time));
        return list;
    }

    // One-time read of this user's appointments (no listener left running).
    public static void listMineOnce(Callback<List<Appointment>> cb) {
        col.whereEqualTo(USER_ID, uid()).get().addOnSuccessListener(snap ->
                cb.on(sorted(snap.toObjects(Appointment.class))));
    }

    // Live list of this user's appointments.
    public static ListenerRegistration listenMine(Callback<List<Appointment>> cb) {
        return col.whereEqualTo(USER_ID, uid()).addSnapshotListener((snap, e) -> {
            if (snap == null) return;
            cb.on(sorted(snap.toObjects(Appointment.class)));
        });
    }

    // Highest queue number currently marked "serving" on a date (0 if none).
    public static ListenerRegistration listenServing(String date, Callback<Integer> cb) {
        return col.whereEqualTo(DATE, date)
                .addSnapshotListener((snap, e) -> {
                    if (snap == null) return;
                    int max = 0;
                    for (DocumentSnapshot d : snap.getDocuments()) {
                        String st = d.getString(STATUS);
                        if (st != null && st.equalsIgnoreCase(STATUS_SERVING)) {
                            Long n = d.getLong(QUEUE_NUMBER);
                            if (n != null && n > max) max = n.intValue();
                        }
                    }
                    cb.on(max);
                });
    }

    // First appointment that is still active (not done or cancelled).
    public static Appointment firstActive(List<Appointment> list) {
        for (Appointment a : list) {
            if (a.status == null) continue;
            String st = a.status.toLowerCase();
            if (st.equals("pending") || st.equals("approved") || st.equals("confirmed") || st.equals(STATUS_SERVING)) return a;
        }
        return null;
    }
}