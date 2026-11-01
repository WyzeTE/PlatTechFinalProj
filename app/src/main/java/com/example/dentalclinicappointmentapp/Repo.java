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

// All database work lives here, so the screens never touch Firebase directly.
public class Repo {
    public interface Callback<T> { void on(T value); }

    private static final FirebaseAuth auth = FirebaseAuth.getInstance();
    private static final CollectionReference col =
            FirebaseFirestore.getInstance().collection("appointments");

    // Anonymous sign-in: every phone gets its own userId without a login screen.
    public static void signIn(Runnable done) {
        if (auth.getCurrentUser() != null) { done.run(); return; }
        auth.signInAnonymously().addOnSuccessListener(r -> done.run());
    }

    private static String uid() {
        return auth.getCurrentUser() == null ? "" : auth.getCurrentUser().getUid();
    }

    // Times already booked on a date, so the app can grey them out.
    public static void takenTimes(String date, Callback<Set<String>> cb) {
        col.whereEqualTo("date", date).get().addOnSuccessListener(snap -> {
            Set<String> taken = new HashSet<>();
            for (DocumentSnapshot d : snap.getDocuments()) {
                if (!"cancelled".equals(d.getString("status"))) taken.add(d.getString("time"));
            }
            cb.on(taken);
        });
    }

    // Queue number = number of bookings that day + 1.
    // Fine for a school project; a busy clinic should use a Firestore transaction.
    public static void book(String name, String service, String date, String time, Runnable done) {
        col.whereEqualTo("date", date).get().addOnSuccessListener(snap -> {
            DocumentReference ref = col.document();
            Appointment a = new Appointment(ref.getId(), uid(), name, service, date, time, snap.size() + 1, "pending");
            ref.set(a).addOnSuccessListener(v -> done.run());
        });
    }

    public static void cancel(String id) {
        col.document(id).update("status", "cancelled");
    }

    // Live list of this user's appointments. Fires again whenever the admin changes a status.
    public static ListenerRegistration listenMine(Callback<List<Appointment>> cb) {
        return col.whereEqualTo("userId", uid()).addSnapshotListener((snap, e) -> {
            if (snap == null) return;
            List<Appointment> list = snap.toObjects(Appointment.class);
            Collections.sort(list, (x, y) -> (x.date + x.time).compareTo(y.date + y.time));
            cb.on(list);
        });
    }

    // Highest queue number currently marked "serving" on a date (0 if none).
    public static ListenerRegistration listenServing(String date, Callback<Integer> cb) {
        return col.whereEqualTo("date", date).whereEqualTo("status", "serving")
                .addSnapshotListener((snap, e) -> {
                    if (snap == null) return;
                    int max = 0;
                    for (DocumentSnapshot d : snap.getDocuments()) {
                        Long n = d.getLong("queueNumber");
                        if (n != null && n > max) max = n.intValue();
                    }
                    cb.on(max);
                });
    }

    // First appointment that is still active (not done or cancelled).
    public static Appointment firstActive(List<Appointment> list) {
        for (Appointment a : list) {
            if (a.status.equals("pending") || a.status.equals("confirmed") || a.status.equals("serving")) return a;
        }
        return null;
    }
}
