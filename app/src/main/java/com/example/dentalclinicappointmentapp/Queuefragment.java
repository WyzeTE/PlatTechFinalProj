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
    private View emptyQueue;
    private TextView subtitle;
    private TextView status;
    private TextView number;
    private TextView wait;
    private TextView nowServing;
    private TextView aheadText;
    private View cancelButton;
    private View rescheduleButton;

    // Progress tracker: four steps, with a connecting line after steps 1, 2 and 3.
    private static final int TODO = 0;      // not reached yet (grey)
    private static final int DONE = 1;      // went through correctly (light green)
    private static final int WAITING = 2;   // waiting on the clinic (yellow)
    private static final int ERROR = 3;     // something went wrong (red)
    private final ImageView[] stepIcons = new ImageView[4];
    private final TextView[] stepTexts = new TextView[4];
    private final View[] stepLines = new View[3];

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup parent, @Nullable Bundle b) {
        View v = inf.inflate(R.layout.fragmentqueue, parent, false);
        content = v.findViewById(R.id.content);
        emptyQueue = v.findViewById(R.id.emptyQueue);
        subtitle = v.findViewById(R.id.subtitle);
        status = v.findViewById(R.id.status);
        number = v.findViewById(R.id.number);
        wait = v.findViewById(R.id.wait);
        nowServing = v.findViewById(R.id.nowServing);
        aheadText = v.findViewById(R.id.ahead);
        cancelButton = v.findViewById(R.id.cancel);
        rescheduleButton = v.findViewById(R.id.reschedule);
        rescheduleButton.setOnClickListener(x -> {
            if (next != null) RescheduleSheet.open(this, next);
        });

        int[] iconIds = {R.id.step1Icon, R.id.step2Icon, R.id.step3Icon, R.id.step4Icon};
        int[] textIds = {R.id.step1Text, R.id.step2Text, R.id.step3Text, R.id.step4Text};
        int[] lineIds = {R.id.line1, R.id.line2, R.id.line3};
        for (int i = 0; i < 4; i++) {
            stepIcons[i] = v.findViewById(iconIds[i]);
            stepTexts[i] = v.findViewById(textIds[i]);
        }
        for (int i = 0; i < 3; i++) stepLines[i] = v.findViewById(lineIds[i]);

        cancelButton.setOnClickListener(x -> {
            if (next != null) Repo.cancel(next.id);
        });

        View btnBookEmpty = v.findViewById(R.id.btnBookQueueEmpty);
        if (btnBookEmpty != null) {
            btnBookEmpty.setOnClickListener(x -> ((MainActivity) requireActivity()).go(R.id.tab_book));
        }

        View btnNotif = v.findViewById(R.id.btnNotification);
        if (btnNotif != null) btnNotif.setOnClickListener(x -> ((MainActivity) requireActivity()).showNotifications());

        mineReg = Repo.listenMine(list -> {
            next = Repo.firstActive(list);
            if (next == null) next = recentRejection(list);   // so a rejected request can show in red
            if (next == null) {
                // Nothing active anymore, so stop watching "now serving".
                if (servReg != null) { servReg.remove(); servReg = null; }
                servDate = "";
                serving = 0;
            } else if (!next.date.equals(servDate)) {
                // Start watching "now serving" for this appointment's date (once per date).
                if (servReg != null) servReg.remove();
                serving = 0;
                servDate = next.date;
                servReg = Repo.listenServing(servDate, n -> { serving = n; render(); });
            }
            render();
        });
        return v;
    }

    private void render() {
        if (next == null) {
            content.setVisibility(View.GONE);
            if (emptyQueue != null) emptyQueue.setVisibility(View.VISIBLE);
            subtitle.setText("No active appointment");
            return;
        }
        content.setVisibility(View.VISIBLE);
        if (emptyQueue != null) emptyQueue.setVisibility(View.GONE);
        String dentist = next.dentistOrEmpty();
        subtitle.setText(next.date + ", " + next.service + (dentist.isEmpty() ? "" : "\n" + dentist));
        status.setText(next.status == null ? "" : next.status.toUpperCase(Locale.ROOT));
        nowServing.setText(serving == 0 ? "-" : String.format(Locale.US, QUEUE_FORMAT, serving));

        if (isRejected(next)) {
            // A rejected request has no queue spot, so there is no number or wait to show.
            number.setText("-");
            wait.setText("The clinic could not accept this request. Please book another time.");
            aheadText.setText("-");
            cancelButton.setVisibility(View.GONE);
            rescheduleButton.setVisibility(View.GONE);
        } else {
            number.setText(String.format(Locale.US, QUEUE_FORMAT, next.queueNumber));
            int ahead = Math.max(0, next.queueNumber - serving - 1);
            wait.setText("Estimated wait: about " + (ahead * 15) + " minutes");
            aheadText.setText(ahead + " people");
            cancelButton.setVisibility(View.VISIBLE);
            // Not offered once the patient is already being served.
            rescheduleButton.setVisibility(Repo.canReschedule(next.status) ? View.VISIBLE : View.GONE);
        }
        updateTracker(next.status);
    }

    private static boolean isRejected(Appointment a) {
        return a.status != null && a.status.trim().equalsIgnoreCase("rejected");
    }

    // Repo.firstActive skips rejected requests, which would leave the patient with a blank
    // screen and no explanation. So when nothing is active, show the latest rejected request
    // that is dated today or later. It goes away once its date passes or a new booking is made.
    private static Appointment recentRejection(List<Appointment> list) {
        String today = LocalDate.now(ZoneId.systemDefault()).toString();
        for (int i = list.size() - 1; i >= 0; i--) {   // the list is sorted oldest to newest
            Appointment a = list.get(i);
            if (isRejected(a) && a.date != null && a.date.compareTo(today) >= 0) return a;
        }
        return null;
    }

    // Lights up the progress tracker from the appointment's status. Steps only turn green when
    // the clinic has really done that step (the status is saved by the admin website):
    //   pending   -> Submitted green, Confirmed yellow (still waiting for the admin)
    //   approved  -> Submitted and Confirmed green
    //   serving   -> up to Now Serving green (the admin called this patient)
    //   completed -> all four green
    //   rejected  -> Confirmed step turns red
    private void updateTracker(String rawStatus) {
        String st = rawStatus == null ? "pending" : rawStatus.trim().toLowerCase(Locale.ROOT);
        int s2 = WAITING, s3 = TODO, s4 = TODO;
        String step2Label = "Confirmed by Clinic";

        switch (st) {
            case "approved":
            case "confirmed":
                s2 = DONE;
                break;
            case "serving":
                s2 = DONE; s3 = DONE;
                break;
            case "completed":
            case "done":
                s2 = DONE; s3 = DONE; s4 = DONE;
                break;
            case "rejected":
                s2 = ERROR;
                step2Label = "Rejected by Clinic";
                break;
            case "cancelled":
                s2 = ERROR;
                step2Label = "Appointment Cancelled";
                break;
            default:   // "pending", or any status we don't know yet
                break;
        }

        setStep(0, DONE);   // the request exists, so it was submitted
        setStep(1, s2);
        setStep(2, s3);
        setStep(3, s4);
        stepTexts[1].setText(step2Label);

        // Each connecting line takes the colour of the step below it.
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
                ImageViewCompat.setImageTintList(icon, null);   // back to the plain grey outline
                break;
        }

        boolean reached = state != TODO;
        text.setTypeface(null, reached ? Typeface.BOLD : Typeface.NORMAL);
        text.setTextColor(ContextCompat.getColor(requireContext(),
                state == ERROR ? R.color.track_error : reached ? R.color.ink : R.color.muted));
    }

    private void setLine(int index, int stateBelow) {
        int color;
        switch (stateBelow) {
            case DONE:    color = R.color.track_done; break;
            case WAITING: color = R.color.track_waiting; break;
            case ERROR:   color = R.color.track_error; break;
            default:      color = R.color.line; break;
        }
        stepLines[index].setBackgroundColor(ContextCompat.getColor(requireContext(), color));
    }

    private void tint(ImageView icon, int colorRes) {
        ImageViewCompat.setImageTintList(icon,
                ColorStateList.valueOf(ContextCompat.getColor(requireContext(), colorRes)));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (mineReg != null) mineReg.remove();
        if (servReg != null) servReg.remove();
    }
}