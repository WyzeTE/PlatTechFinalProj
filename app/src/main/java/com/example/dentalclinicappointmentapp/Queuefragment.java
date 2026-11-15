package com.example.dentalclinicappointmentapp;

import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.card.MaterialCardView;
import com.google.firebase.firestore.ListenerRegistration;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;

public class Queuefragment extends Fragment {

    // Same look as the website: Q-001, Q-002, ...
    private static final String QUEUE_FORMAT = "Q-%03d";

    private ListenerRegistration mineReg;
    private ListenerRegistration servReg;
    private String servDate = "";
    private Appointment next;
    private int serving = 0;

    private View content;
    private MaterialCardView emptyQueueCard;
    private TextView subtitle;
    private TextView status;
    private TextView number;
    private TextView wait;
    private TextView nowServing;
    private TextView aheadText;
    private View cancelButton;
    private View rescheduleButton;

    // Progress tracker
    private static final int TODO = 0;
    private static final int DONE = 1;
    private static final int WAITING = 2;
    private static final int ERROR = 3;

    private final ImageView[] stepIcons = new ImageView[4];
    private final TextView[] stepTexts = new TextView[4];
    private final View[] stepLines = new View[3];

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inf,
            @Nullable ViewGroup parent,
            @Nullable Bundle b) {

        View v = inf.inflate(R.layout.fragmentqueue, parent, false);

        content = v.findViewById(R.id.content);
        emptyQueueCard = v.findViewById(R.id.emptyQueueCard);

        subtitle = v.findViewById(R.id.subtitle);
        status = v.findViewById(R.id.status);
        number = v.findViewById(R.id.number);
        wait = v.findViewById(R.id.wait);
        nowServing = v.findViewById(R.id.nowServing);
        aheadText = v.findViewById(R.id.ahead);

        cancelButton = v.findViewById(R.id.cancel);
        rescheduleButton = v.findViewById(R.id.reschedule);

        rescheduleButton.setOnClickListener(x -> {
            if (next != null) {
                RescheduleSheet.open(this, next);
            }
        });

        // Progress tracker views
        int[] iconIds = {
                R.id.step1Icon,
                R.id.step2Icon,
                R.id.step3Icon,
                R.id.step4Icon
        };

        int[] textIds = {
                R.id.step1Text,
                R.id.step2Text,
                R.id.step3Text,
                R.id.step4Text
        };

        int[] lineIds = {
                R.id.line1,
                R.id.line2,
                R.id.line3
        };

        for (int i = 0; i < 4; i++) {
            stepIcons[i] = v.findViewById(iconIds[i]);
            stepTexts[i] = v.findViewById(textIds[i]);
        }

        for (int i = 0; i < 3; i++) {
            stepLines[i] = v.findViewById(lineIds[i]);
        }

        // Cancel appointment
        cancelButton.setOnClickListener(x -> {
            if (next != null) {
                Repo.cancel(next.id);
            }
        });

        // Book appointment from empty state
        View btnBookEmpty = v.findViewById(R.id.btnBookQueueEmpty);

        if (btnBookEmpty != null) {
            btnBookEmpty.setOnClickListener(x ->
                    ((MainActivity) requireActivity()).go(R.id.tab_book));
        }

        // Notifications
        View btnNotif = v.findViewById(R.id.btnNotification);

        if (btnNotif != null) {
            btnNotif.setOnClickListener(x ->
                    ((MainActivity) requireActivity()).showNotifications());
        }

        // Listen for the patient's appointments
        mineReg = Repo.listenMine(list -> {

            next = Repo.firstActive(list);

            if (next == null) {
                next = recentRejection(list);
            }

            if (next == null) {
                // Nothing active anymore, stop watching now serving.
                if (servReg != null) {
                    servReg.remove();
                    servReg = null;
                }

                servDate = "";
                serving = 0;

            } else if (!next.date.equals(servDate)) {

                // Start watching now serving for this appointment's date.
                if (servReg != null) {
                    servReg.remove();
                }

                serving = 0;
                servDate = next.date;

                servReg = Repo.listenServing(servDate, n -> {
                    serving = n;
                    render();
                });
            }

            render();
        });

        return v;
    }

    private void render() {

        // No active appointment
        if (next == null) {

            content.setVisibility(View.GONE);

            if (emptyQueueCard != null) {
                emptyQueueCard.setVisibility(View.VISIBLE);
            }

            subtitle.setText("No active appointment");
            return;
        }

        // Active appointment exists
        content.setVisibility(View.VISIBLE);

        if (emptyQueueCard != null) {
            emptyQueueCard.setVisibility(View.GONE);
        }

        String dentist = next.dentistOrEmpty();

        subtitle.setText(
                next.date + ", " + next.service
                        + (dentist.isEmpty() ? "" : "\n" + dentist)
        );

        status.setText(
                next.status == null
                        ? ""
                        : next.status.toUpperCase(Locale.ROOT)
        );

        nowServing.setText(
                serving == 0
                        ? "-"
                        : String.format(Locale.US, QUEUE_FORMAT, serving)
        );

        if (isRejected(next)) {

            // Rejected requests have no queue number.
            number.setText("-");

            wait.setText(
                    "The clinic could not accept this request. Please book another time."
            );

            aheadText.setText("-");

            cancelButton.setVisibility(View.GONE);
            rescheduleButton.setVisibility(View.GONE);

        } else {

            number.setText(
                    String.format(Locale.US, QUEUE_FORMAT, next.queueNumber)
            );

            int ahead = Math.max(
                    0,
                    next.queueNumber - serving - 1
            );

            wait.setText(
                    "Estimated wait: about " + (ahead * 15) + " minutes"
            );

            aheadText.setText(ahead + " people");

            cancelButton.setVisibility(View.VISIBLE);

            // Rescheduling is unavailable once the patient is being served.
            rescheduleButton.setVisibility(
                    Repo.canReschedule(next.status)
                            ? View.VISIBLE
                            : View.GONE
            );
        }

        updateTracker(next.status);
    }

    private static boolean isRejected(Appointment a) {
        return a.status != null
                && a.status.trim().equalsIgnoreCase("rejected");
    }

    // Show the latest rejected appointment if it is dated today or later.
    private static Appointment recentRejection(List<Appointment> list) {

        String today = LocalDate.now(
                ZoneId.systemDefault()
        ).toString();

        for (int i = list.size() - 1; i >= 0; i--) {

            Appointment a = list.get(i);

            if (isRejected(a)
                    && a.date != null
                    && a.date.compareTo(today) >= 0) {

                return a;
            }
        }

        return null;
    }

    // Update the progress tracker according to appointment status.
    private void updateTracker(String rawStatus) {

        String st = rawStatus == null
                ? "pending"
                : rawStatus.trim().toLowerCase(Locale.ROOT);

        int s2 = WAITING;
        int s3 = TODO;
        int s4 = TODO;

        String step2Label = "Confirmed by Clinic";

        switch (st) {

            case "approved":
            case "confirmed":
                s2 = DONE;
                break;

            case "serving":
                s2 = DONE;
                s3 = DONE;
                break;

            case "completed":
            case "done":
                s2 = DONE;
                s3 = DONE;
                s4 = DONE;
                break;

            case "rejected":
                s2 = ERROR;
                step2Label = "Rejected by Clinic";
                break;

            case "cancelled":
                s2 = ERROR;
                step2Label = "Appointment Cancelled";
                break;

            default:
                break;
        }

        setStep(0, DONE);
        setStep(1, s2);
        setStep(2, s3);
        setStep(3, s4);

        stepTexts[1].setText(step2Label);

        setLine(0, s2);
        setLine(1, s3);
        setLine(2, s4);
    }

    private void setStep(int index, int state) {

        ImageView icon = stepIcons[index];
        TextView text = stepTexts[index];

        switch (state) {

            case DONE:
                icon.setImageResource(R.drawable.ic_check_circle);
                tint(icon, R.color.track_done);
                break;

            case WAITING:
                icon.setImageResource(R.drawable.ic_pending_circle);
                tint(icon, R.color.track_waiting);
                break;

            case ERROR:
                icon.setImageResource(R.drawable.ic_error_circle);
                tint(icon, R.color.track_error);
                break;

            default:
                icon.setImageResource(R.drawable.ic_circle_outline);
                ImageViewCompat.setImageTintList(icon, null);
                break;
        }

        boolean reached = state != TODO;

        text.setTypeface(
                null,
                reached ? Typeface.BOLD : Typeface.NORMAL
        );

        text.setTextColor(
                ContextCompat.getColor(
                        requireContext(),
                        state == ERROR
                                ? R.color.track_error
                                : reached
                                ? R.color.ink
                                : R.color.muted
                )
        );
    }

    private void setLine(int index, int stateBelow) {

        int color;

        switch (stateBelow) {

            case DONE:
                color = R.color.track_done;
                break;

            case WAITING:
                color = R.color.track_waiting;
                break;

            case ERROR:
                color = R.color.track_error;
                break;

            default:
                color = R.color.line;
                break;
        }

        stepLines[index].setBackgroundColor(
                ContextCompat.getColor(requireContext(), color)
        );
    }

    private void tint(ImageView icon, int colorRes) {

        ImageViewCompat.setImageTintList(
                icon,
                ColorStateList.valueOf(
                        ContextCompat.getColor(requireContext(), colorRes)
                )
        );
    }

    @Override
    public void onDestroyView() {

        super.onDestroyView();

        if (mineReg != null) {
            mineReg.remove();
            mineReg = null;
        }

        if (servReg != null) {
            servReg.remove();
            servReg = null;
        }
    }
}
