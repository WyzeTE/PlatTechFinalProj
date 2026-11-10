package com.example.dentalclinicappointmentapp;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// All database and authentication work lives here.
public class Repo {
    public interface Callback<T> { void on(T value); }

    // Firestore field names and status values, written once so they can't be mistyped.
    private static final String STATUS = "status";
    private static final String DENTIST = "dentist";
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

    // Creates the account, then saves the patient's name on it. The full name goes on the
    // Firebase profile (displayName) and first/middle/last are also kept in a users/{uid}
    // document. If saving the name fails the account still exists, so we don't block the signup.
    public static void registerWithEmail(String email, String password,
                                         String firstName, String middleName, String lastName,
                                         Runnable done, Callback<String> onError) {
        auth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(r -> {
                    FirebaseUser user = r.getUser();
                    if (user == null) { done.run(); return; }

                    String fullName = firstName + " " + middleName + " " + lastName;
                    user.updateProfile(new UserProfileChangeRequest.Builder()
                            .setDisplayName(fullName).build());

                    Map<String, Object> profile = new HashMap<>();
                    profile.put("firstName", firstName);
                    profile.put("middleName", middleName);
                    profile.put("lastName", lastName);
                    profile.put("fullName", fullName);
                    profile.put("email", email);
                    FirebaseFirestore.getInstance().collection("users").document(user.getUid())
                            .set(profile)
                            .addOnCompleteListener(t -> done.run());
                })
                .addOnFailureListener(e -> onError.on(e.getLocalizedMessage()));
    }

    // Sends Firebase's password reset email. Firebase may report success even when no account
    // exists for that email, so the screen words its message carefully.
    public static void sendPasswordReset(String email, Runnable done, Callback<String> onError) {
        auth.sendPasswordResetEmail(email)
                .addOnSuccessListener(v -> done.run())
                .addOnFailureListener(e -> onError.on(e.getLocalizedMessage()));
    }

    // The full name saved at signup, or "" for older accounts that have none.
    public static String getDisplayName() {
        return (auth.getCurrentUser() != null && auth.getCurrentUser().getDisplayName() != null)
                ? auth.getCurrentUser().getDisplayName()
                : "";
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

    // Times already booked with one dentist on a date, so the app can grey them out.
    // Each dentist has their own schedule. Older bookings with no dentist saved block the
    // slot for everyone, to stay on the safe side.
    // The website saves to this same "appointments" collection, so its bookings show up here too.
    public static void takenTimes(String date, String dentist, Callback<Set<String>> cb) {
        col.whereEqualTo(DATE, date).get().addOnSuccessListener(snap -> cb.on(takenFrom(snap, dentist)));
    }

    // Same as takenTimes, but stays live: if the website (or another phone) books a slot while
    // the patient is looking at the screen, the callback runs again with the new list.
    // The caller must remove() the returned registration when it stops caring.
    public static ListenerRegistration listenTakenTimes(String date, String dentist, Callback<Set<String>> cb) {
        return col.whereEqualTo(DATE, date).addSnapshotListener((snap, e) -> {
            if (snap == null) return;
            cb.on(takenFrom(snap, dentist));
        });
    }

    private static Set<String> takenFrom(QuerySnapshot snap, String dentist) {
        Set<String> taken = new HashSet<>();
        for (DocumentSnapshot d : snap.getDocuments()) {
            if (sameDentist(d.getString(DENTIST), dentist) && isActive(d.getString(STATUS))) {
                taken.add(slotOf(d.getString(TIME)));
            }
        }
        return taken;
    }

    private static boolean sameDentist(String booked, String dentist) {
        return booked == null || booked.trim().isEmpty() || booked.trim().equalsIgnoreCase(dentist);
    }

    private static final Pattern TIME_PATTERN = Pattern.compile("^\\s*(\\d{1,2}):(\\d{2})");

    // The website lets staff type any time (like 10:15), but the app books in 30 minute slots.
    // This turns a saved time into the slot it falls in ("10:15" -> "10:00", "9:40" -> "09:30"),
    // so a website booking always greys out the right chip.
    public static String slotOf(String time) {
        if (time == null) return "";
        Matcher m = TIME_PATTERN.matcher(time);
        if (!m.find()) return time.trim();
        int hour = Integer.parseInt(m.group(1));
        int minute = Integer.parseInt(m.group(2)) < 30 ? 0 : 30;
        return String.format(Locale.US, "%02d:%02d", hour, minute);
    }

    // A booking's document id is date + time + dentist (e.g. "2026-11-09_1030_d2"), so one
    // dentist's slot can only ever hold one document. The transaction checks that slot and
    // writes the booking in one step, so two people can't both grab the same dentist and time.
    // Queue number = highest number already given that day + 1 (same rule the admin
    // website uses when it approves a booking; cancelled ones are set to 0 there).
    public static void book(String name, String service, Catalog.Dentist dentist, String date, String time,
                            Runnable done, Callback<String> onError) {
        DocumentReference ref = col.document(date + "_" + time.replace(":", "") + "_d" + dentist.id);

        col.whereEqualTo(DATE, date).get().addOnSuccessListener(snap -> {
            int max = 0;
            for (DocumentSnapshot d : snap.getDocuments()) {
                // The website saves bookings under random document ids, so the slot's own id
                // below would not catch them. Check every booking of the day instead.
                if (isActive(d.getString(STATUS)) && sameDentist(d.getString(DENTIST), dentist.name)
                        && slotOf(time).equals(slotOf(d.getString(TIME)))) {
                    onError.on("That time was just booked with this dentist. Please pick another slot.");
                    return;
                }
                Long n = d.getLong(QUEUE_NUMBER);
                if (n != null && n > max) max = n.intValue();
            }
            final int queueNumber = max + 1;

            FirebaseFirestore.getInstance().runTransaction(tx -> {
                DocumentSnapshot slot = tx.get(ref);
                if (slot.exists() && isActive(slot.getString(STATUS))) {
                    return Boolean.FALSE;   // somebody already holds this slot
                }
                Appointment a = new Appointment(ref.getId(), uid(), name, service, dentist.name, date, time, queueNumber, "pending");
                if (auth.getCurrentUser() != null && auth.getCurrentUser().getEmail() != null) {
                    a.email = auth.getCurrentUser().getEmail();
                }
                tx.set(ref, a);
                return Boolean.TRUE;
            }).addOnSuccessListener(booked -> {
                if (booked) done.run();
                else onError.on("That time was just booked with this dentist. Please pick another slot.");
            }).addOnFailureListener(e -> onError.on("Could not book: " + e.getLocalizedMessage()));
        }).addOnFailureListener(e -> onError.on("Could not book: " + e.getLocalizedMessage()));
    }

    // A slot is "held" unless its booking was cancelled or rejected.
    private static boolean isActive(String status) {
        return status != null && !status.equalsIgnoreCase(STATUS_CANCELLED) && !status.equalsIgnoreCase(STATUS_REJECTED);
    }

    // Pending and approved appointments can be moved. Once the patient is being served (or it is
    // done, cancelled or rejected) there is nothing left to reschedule.
    public static boolean canReschedule(String status) {
        if (status == null) return false;
        String st = status.trim().toLowerCase(Locale.ROOT);
        return st.equals("pending") || st.equals("approved") || st.equals("confirmed");
    }

    // Moves an appointment to a new date/time with the same dentist.
    // A booking's document id is its slot (date + time + dentist), so a moved booking needs a new
    // document: the new one is written as "pending" (the clinic has to confirm the new time) and
    // the old one is marked cancelled, the same way a normal cancel does it. Both happen in one
    // transaction, so the patient can never end up with both or neither.
    public static void reschedule(Appointment old, Catalog.Dentist dentist, String newDate, String newTime,
                                  Runnable done, Callback<String> onError) {
        final String taken = "That time was just booked with this dentist. Please pick another slot.";

        if (old.id == null || old.id.isEmpty()) {
            onError.on("This appointment can't be rescheduled. Please cancel it and book again.");
            return;
        }
        if (newDate.equals(old.date) && slotOf(newTime).equals(slotOf(old.time))) {
            onError.on("That is your current time. Please pick a different one.");
            return;
        }

        DocumentReference oldRef = col.document(old.id);
        DocumentReference newRef = col.document(newDate + "_" + newTime.replace(":", "") + "_d" + dentist.id);

        // Same check as book(): look at every booking that day (the website's included).
        col.whereEqualTo(DATE, newDate).get().addOnSuccessListener(snap -> {
            int max = 0;
            for (DocumentSnapshot d : snap.getDocuments()) {
                boolean mine = d.getId().equals(old.id);
                if (!mine && isActive(d.getString(STATUS)) && sameDentist(d.getString(DENTIST), dentist.name)
                        && slotOf(newTime).equals(slotOf(d.getString(TIME)))) {
                    onError.on(taken);
                    return;
                }
                Long n = d.getLong(QUEUE_NUMBER);
                if (n != null && n > max) max = n.intValue();
            }
            final int queueNumber = max + 1;

            FirebaseFirestore.getInstance().runTransaction(tx -> {
                DocumentSnapshot current = tx.get(oldRef);
                if (!current.exists() || !canReschedule(current.getString(STATUS))) {
                    return "This appointment can no longer be rescheduled.";
                }
                DocumentSnapshot slot = tx.get(newRef);
                if (slot.exists() && isActive(slot.getString(STATUS))) return taken;

                String name = old.patientName == null || old.patientName.isEmpty() ? old.name : old.patientName;
                Appointment moved = new Appointment(newRef.getId(),
                        old.userId == null || old.userId.isEmpty() ? uid() : old.userId,
                        name, old.service, dentist.name, newDate, newTime, queueNumber, "pending");
                moved.email = old.email;

                tx.set(newRef, moved);
                tx.update(oldRef, STATUS, STATUS_CANCELLED, QUEUE_NUMBER, 0);
                return null;   // null means it worked
            }).addOnSuccessListener(problem -> {
                if (problem == null) done.run();
                else onError.on(problem);
            }).addOnFailureListener(e -> onError.on("Could not reschedule: " + e.getLocalizedMessage()));
        }).addOnFailureListener(e -> onError.on("Could not reschedule: " + e.getLocalizedMessage()));
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