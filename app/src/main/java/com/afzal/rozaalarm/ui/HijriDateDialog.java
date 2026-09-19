package com.afzal.rozaalarm.ui;

import android.content.Context;
import android.content.DialogInterface;
import android.view.LayoutInflater;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.afzal.rozaalarm.R;
import com.afzal.rozaalarm.data.HijriCorrections;
import com.afzal.rozaalarm.databinding.DialogSetHijriDateBinding;
import com.afzal.rozaalarm.util.HijriDates;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * "Set today's date": the user says what today's Islamic date really is on their own calendar and
 * the app works out the correction from there.
 *
 * <p>The calculation the app ships with is Umm al-Qura, and local moon sighting routinely differs
 * from it by a day or two, so this is the screen that makes every other date in the app right.</p>
 */
public final class HijriDateDialog {

    /** Called on the main thread once the correction has been stored and the alarms re-registered. */
    public interface OnApplied {
        void onApplied(int offsetDays);
    }

    private HijriDateDialog() {
    }

    public static void show(@NonNull Context context, @NonNull HijriCorrections corrections,
                            @Nullable OnApplied onApplied) {
        long now = System.currentTimeMillis();
        DialogSetHijriDateBinding binding =
                DialogSetHijriDateBinding.inflate(LayoutInflater.from(context));

        binding.setDateExplanation.setText(
                context.getString(R.string.hijri_set_message, HijriDates.formatEnglish(now)));

        final String[] days = new String[30];
        for (int i = 0; i < days.length; i++) {
            days[i] = String.valueOf(i + 1);
        }
        final String[] months = new String[HijriDates.MONTHS_EN.length];
        for (int i = 0; i < months.length; i++) {
            months[i] = HijriDates.MONTHS_EN[i] + "  ·  " + HijriDates.MONTHS_UR[i];
        }

        // Start on what the app believes today is, so a one-day correction is a one-tap change.
        final int[] chosen = {HijriDates.dayOfMonth(now), HijriDates.month(now)};

        binding.setDateDay.setSimpleItems(days);
        binding.setDateDay.setText(days[Math.max(0, Math.min(29, chosen[0] - 1))], false);
        binding.setDateMonth.setSimpleItems(months);
        binding.setDateMonth.setText(months[Math.max(0, Math.min(11, chosen[1]))], false);

        AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.hijri_set_title)
                .setView(binding.getRoot())
                .setPositiveButton(R.string.hijri_set_apply, null)
                .setNegativeButton(R.string.perm_later, null)
                .create();

        final Runnable refreshPreview = () -> {
            int offset = HijriCorrections.offsetForToday(chosen[1], chosen[0]);
            boolean reachable = offset != HijriCorrections.NO_SUCH_OFFSET;
            if (reachable) {
                binding.setDatePreview.setText(offset == 0
                        ? context.getString(R.string.hijri_set_preview_none)
                        : context.getString(R.string.hijri_set_preview, offset));
            } else {
                binding.setDatePreview.setText(context.getString(
                        R.string.hijri_correct_impossible, HijriDates.MAX_OFFSET));
            }
            android.widget.Button apply = dialog.getButton(DialogInterface.BUTTON_POSITIVE);
            if (apply != null) {
                apply.setEnabled(reachable);
            }
        };

        binding.setDateDay.setOnItemClickListener((parent, view, position, id) -> {
            chosen[0] = position + 1;
            refreshPreview.run();
        });
        binding.setDateMonth.setOnItemClickListener((parent, view, position, id) -> {
            chosen[1] = position;
            refreshPreview.run();
        });

        // The buttons only exist once the dialog is showing, so the first preview waits for that.
        dialog.setOnShowListener(d -> {
            refreshPreview.run();
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(v -> {
                int applied = corrections.correctToday(chosen[1], chosen[0], () -> {
                    if (onApplied != null) {
                        onApplied.onApplied(HijriDates.globalOffset());
                    }
                });
                if (applied != HijriCorrections.NO_SUCH_OFFSET) {
                    dialog.dismiss();
                }
            });
        });

        dialog.show();
    }
}
