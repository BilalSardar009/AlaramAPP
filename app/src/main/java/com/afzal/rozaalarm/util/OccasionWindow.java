package com.afzal.rozaalarm.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.afzal.rozaalarm.data.FastLog;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * Where a fasting occasion falls next, as somewhere to take the calendar to.
 *
 * <p>Tapping a fast on the home screen does not make an alarm on the spot — it opens the calendar
 * on the month that fast lands in with its days already selected, so the days can be seen in
 * context and changed before an alarm is set.</p>
 */
public final class OccasionWindow {

    /** A Hijri month, and the days of it this occasion covers. */
    public static final class Window {
        public final int hijriYear;
        /** Zero based, 0 = Muharram. */
        public final int hijriMonth;
        /** Local {@code yyyyMMdd} days, ascending. */
        @NonNull
        public final List<Integer> dateKeys;

        Window(int hijriYear, int hijriMonth, @NonNull List<Integer> dateKeys) {
            this.hijriYear = hijriYear;
            this.hijriMonth = hijriMonth;
            this.dateKeys = dateKeys;
        }
    }

    /** A month past a year is further than any occasion can be, Hijri drift included. */
    private static final int MAX_LOOKAHEAD_DAYS = 400;

    private OccasionWindow() {
    }

    /**
     * The next run of days this occasion covers, or {@code null} if it somehow falls outside the
     * next year.
     *
     * <p>The search starts today rather than tomorrow: a fast that began this morning is still the
     * one to show, and selecting today is not an error — it simply has no alarm left to ring.</p>
     */
    @Nullable
    public static Window next(@NonNull Occasion occasion, long fromMillis) {
        Calendar cursor = Calendar.getInstance();
        cursor.setTimeInMillis(HijriDates.midnight(fromMillis));

        for (int i = 0; i <= MAX_LOOKAHEAD_DAYS; i++) {
            Calendar day = (Calendar) cursor.clone();
            day.add(Calendar.DAY_OF_MONTH, i);
            HijriDates.Snapshot hijri = HijriDates.snapshot(day.getTimeInMillis());
            if (Occasions.covers(occasion, hijri.month, hijri.day)) {
                return wholeMonthRun(occasion, hijri.year, hijri.month);
            }
        }
        return null;
    }

    /**
     * Every day of one Hijri month that the occasion covers.
     *
     * <p>The days come from the Hijri coordinates rather than from a second conversion, so they
     * cannot disagree with the cells the calendar will draw them in.</p>
     */
    @NonNull
    private static Window wholeMonthRun(@NonNull Occasion occasion, int hijriYear, int hijriMonth) {
        long monthStart = HijriDates.gregorianMidnightOfMonthStart(hijriYear, hijriMonth);
        int monthLength = HijriDates.monthLengthOf(hijriYear, hijriMonth);

        List<Integer> keys = new ArrayList<>();
        for (int day = 1; day <= monthLength; day++) {
            if (!Occasions.covers(occasion, hijriMonth, day)) {
                continue;
            }
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(monthStart);
            cal.add(Calendar.DAY_OF_MONTH, day - 1);
            keys.add(FastLog.dateKeyOf(cal));
        }
        return new Window(hijriYear, hijriMonth, keys);
    }
}
