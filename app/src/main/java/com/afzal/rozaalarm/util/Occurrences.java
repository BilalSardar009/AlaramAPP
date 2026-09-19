package com.afzal.rozaalarm.util;

import androidx.annotation.NonNull;

import com.afzal.rozaalarm.data.Alarm;
import com.afzal.rozaalarm.data.FastLog;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * Works out when an {@link Alarm} fires next.
 *
 * <p>An occasion rule is evaluated with a day-by-day walk forward from "now": for each candidate
 * day we build the alarm instant and ask whether that day is one of the occasion's. It is a few
 * hundred cheap iterations at worst and it keeps Hijri months, leap years and DST handling in one
 * place instead of spread across per-rule arithmetic. A date rule needs no walk at all — the days
 * are already written down.</p>
 *
 * <p>Hijri corrections are resolved inside {@link HijriDates}, so nothing here has to thread an
 * offset through.</p>
 */
public final class Occurrences {

    /** Nothing found / alarm already expired. */
    public static final long NONE = -1L;

    /**
     * Occasion rules such as Arafah happen once a Hijri year, so the window has to clear a full
     * year plus the Gregorian/Hijri drift.
     */
    private static final int MAX_LOOKAHEAD_DAYS = 400;

    private static final long DAY_MILLIS = 24L * 60L * 60L * 1000L;

    private Occurrences() {
    }

    /** @return epoch millis of the first firing strictly after {@code fromMillis}, or {@link #NONE}. */
    public static long next(@NonNull Alarm alarm, long fromMillis) {
        List<Long> found = nextMany(alarm, fromMillis, 1);
        return found.isEmpty() ? NONE : found.get(0);
    }

    /** @return up to {@code count} upcoming firings, in ascending order. */
    @NonNull
    public static List<Long> nextMany(@NonNull Alarm alarm, long fromMillis, int count) {
        List<Long> result = new ArrayList<>();
        if (count <= 0) {
            return result;
        }

        // A date alarm is answered from its own list. Walking the calendar would cap it at the
        // lookahead window, and the calendar lets you pick a day years out.
        if (alarm.repeatMode == Alarm.REPEAT_DATES) {
            for (int dateKey : alarm.dateKeyList()) {
                long instant = instantOfDateKey(dateKey, alarm.hour, alarm.minute);
                if (instant > fromMillis && !isSkipped(alarm, instant)) {
                    result.add(instant);
                    if (result.size() >= count) {
                        break;
                    }
                }
            }
            return result;
        }

        Calendar base = Calendar.getInstance();
        base.setTimeInMillis(fromMillis);

        for (int i = 0; i <= MAX_LOOKAHEAD_DAYS && result.size() < count; i++) {
            Calendar day = (Calendar) base.clone();
            day.add(Calendar.DAY_OF_MONTH, i);
            applyTime(day, alarm.hour, alarm.minute);
            long instant = day.getTimeInMillis();
            if (instant <= fromMillis) {
                continue;
            }
            if (matchesDay(alarm, day) && !isSkipped(alarm, instant)) {
                result.add(instant);
            }
        }
        return result;
    }

    /**
     * The next moment an advance reminder should be posted, or {@link #NONE} when the alarm has no
     * further reminders (reminders disabled, or every remaining day already past its reminder).
     */
    public static long nextReminder(@NonNull Alarm alarm, long fromMillis) {
        if (!alarm.preReminderEnabled) {
            return NONE;
        }
        List<Long> upcoming = nextMany(alarm, fromMillis, 8);
        for (long occurrence : upcoming) {
            if (alarm.preReminderFirstDayOnly && startsMidRun(alarm, occurrence)) {
                continue;
            }
            long reminder = reminderInstantFor(alarm, occurrence);
            if (reminder > fromMillis) {
                return reminder;
            }
        }
        return NONE;
    }

    /** The reminder instant belonging to one specific firing. */
    public static long reminderInstantFor(@NonNull Alarm alarm, long occurrenceMillis) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(occurrenceMillis);
        cal.add(Calendar.DAY_OF_MONTH, -Math.max(0, alarm.preReminderDaysBefore));
        applyTime(cal, alarm.preReminderHour, alarm.preReminderMinute);
        return cal.getTimeInMillis();
    }

    /** True when the day before {@code occurrenceMillis} is itself a firing day of this alarm. */
    public static boolean startsMidRun(@NonNull Alarm alarm, long occurrenceMillis) {
        Calendar previous = Calendar.getInstance();
        previous.setTimeInMillis(occurrenceMillis);
        previous.add(Calendar.DAY_OF_MONTH, -1);
        applyTime(previous, alarm.hour, alarm.minute);
        return matchesDay(alarm, previous) && !isSkipped(alarm, previous.getTimeInMillis());
    }

    /**
     * Days on which fasting is not permitted — Eid al-Fitr, Eid al-Adha and the days of Tashreeq.
     * This is what stops a White Days alarm from ringing on 13 Dhu al-Hijjah.
     */
    public static boolean isSkipped(@NonNull Alarm alarm, long instantMillis) {
        return alarm.skipForbiddenDays && Occasions.isFastingForbidden(instantMillis);
    }

    /** Does the calendar day of {@code day} satisfy the alarm's repeat rule? */
    public static boolean matchesDay(@NonNull Alarm alarm, @NonNull Calendar day) {
        switch (alarm.repeatMode) {
            case Alarm.REPEAT_OCCASION: {
                Occasion occasion = Occasion.fromId(alarm.occasionId);
                if (occasion == null) {
                    return false;
                }
                if (occasion == Occasion.MONDAY_THURSDAY) {
                    // Retired from the shortcut list, but alarms saved against it still ring.
                    int weekday = day.get(Calendar.DAY_OF_WEEK);
                    return weekday == Calendar.MONDAY || weekday == Calendar.THURSDAY;
                }
                return Occasions.matches(occasion, day.getTimeInMillis());
            }

            case Alarm.REPEAT_DATES:
                return alarm.dateKeyList().contains(FastLog.dateKeyOf(day));

            default:
                return false;
        }
    }

    /** Local midnight of a {@code yyyyMMdd} day, moved to the alarm's time of day. */
    public static long instantOfDateKey(int dateKey, int hour, int minute) {
        Calendar cal = Calendar.getInstance();
        cal.clear();
        cal.set(dateKey / 10000, ((dateKey / 100) % 100) - 1, dateKey % 100);
        applyTime(cal, hour, minute);
        return cal.getTimeInMillis();
    }

    /** True when two instants fall on the same local calendar day. */
    public static boolean isSameLocalDay(long first, long second) {
        Calendar a = Calendar.getInstance();
        a.setTimeInMillis(first);
        Calendar b = Calendar.getInstance();
        b.setTimeInMillis(second);
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR)
                && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
    }

    public static void applyTime(@NonNull Calendar cal, int hour, int minute) {
        cal.set(Calendar.HOUR_OF_DAY, hour);
        cal.set(Calendar.MINUTE, minute);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
    }

    /** Whole days between two instants, rounded down. */
    public static long daysBetween(long fromMillis, long toMillis) {
        return (toMillis - fromMillis) / DAY_MILLIS;
    }
}
