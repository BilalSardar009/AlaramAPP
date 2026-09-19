package com.afzal.rozaalarm.ui;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.afzal.rozaalarm.R;
import com.afzal.rozaalarm.data.HijriCorrections;
import com.afzal.rozaalarm.util.HijriDates;

/**
 * "Set today's date": the user says what today's Islamic date really is and the app works out the
 * correction from there.
 *
 * <p>The app calculates with Umm al-Qura, the Saudi civil calendar. Local moon sighting routinely
 * lands a day or two away from it — in Pakistan, two days — so this is what makes every other date
 * in the app right.</p>
 *
 * <p>It is one list of whole dates rather than a day picker and a month picker. Every line is a
 * date the app can actually be set to, so there is nothing to get wrong and nothing it has to
 * refuse.</p>
 */
public final class HijriDateDialog {

    /** Called on the main thread once the correction is stored and the alarms re-registered. */
    public interface OnApplied {
        void onApplied(int offsetDays);
    }

    private HijriDateDialog() {
    }

    public static void show(@NonNull Context context, @NonNull HijriCorrections corrections,
                            @Nullable OnApplied onApplied) {
        long now = System.currentTimeMillis();

        final int span = HijriDates.MAX_OFFSET;
        final int count = span * 2 + 1;

        // One line per correction, earliest date first, so the list reads like a calendar.
        final String[] options = new String[count];
        final int[] offsets = new int[count];
        int checked = 0;
        for (int i = 0; i < count; i++) {
            int offset = i - span;
            offsets[i] = offset;
            String date = HijriDates.formatEnglish(now, offset)
                    + "\n" + HijriDates.formatUrdu(now, offset);
            options[i] = offset == HijriDates.globalOffset()
                    ? context.getString(R.string.hijri_set_option_current, date)
                    : date;
            if (offset == HijriDates.globalOffset()) {
                checked = i;
            }
        }

        ChoiceDialog.show(context, R.string.hijri_set_title,
                context.getString(R.string.hijri_set_message), options, checked,
                R.string.hijri_set_apply,
                index -> corrections.setGlobalOffset(offsets[index], () -> {
                    if (onApplied != null) {
                        onApplied.onApplied(HijriDates.globalOffset());
                    }
                }));
    }
}
