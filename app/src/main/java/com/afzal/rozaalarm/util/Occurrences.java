package com.afzal.rozaalarm.util;

import androidx.annotation.NonNull;

import com.afzal.rozaalarm.data.Alarm;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * Works out when an {@link Alarm} fires next.
 *
 * <p>Every repeat rule is evaluated with the same day-by-day walk forward from "now": for each
 * candidate day we build the alarm instant and ask the rule whether that day counts. It is a few
 * hundred cheap iterations at worst and it keeps Gregorian months, Hijri months, leap years and
 * DST handling in one place instead of spread across per-rule arithmetic.</p>
 *
 * <p>Hijri corrections are resolved inside {@link HijriDates}, so nothing here has to thread an
 * offset through.</p>
 */
public final class Occurrences {

    /** Nothing found / alarm already expired. */
    public static final long NONE = -1L;

    /**
     * A monthly rule always hits within a year. Occasion rules such as Arafah happen once a Hijri
     * year, so the window has to clear a full year plus the Gregorian/Hijri drift.
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

        if (alarm.repeatMode == Alarm.REPEAT_ONCE) {
            long once = onceInstant(alarm);
            if (once > fromMillis && !isSkipped(alarm, once)) {
                result.add(once);
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
     * further reminders (reminders disabled, or a one-off whose reminder time has passed).
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
        // A one-off has no run, and every day of a daily alarm would count as mid-run, which would
        // silently cancel its reminders instead of delivering the ones the user asked for.
        if (alarm.repeatMode == Alarm.REPEAT_ONCE || alarm.repeatMode == Alarm.REPEAT_DAILY) {
            return false;
        }
        Calendar previous = Calendar.getInstance();
        previous.setTimeInMillis(occurrenceMillis);
        previous.add(Calendar.DAY_OF_MONTH, -1);
        applyTime(previous, alarm.hour, alarm.minute);
        return matchesDay(alarm, previous) && !isSkipped(alarm, previous.getTimeInMillis());
    }

    /**
     * Days on which fasting is not permitted — Eid al-Fitr, Eid al-Adha and the days of Tashreeq.
     * This is what stops a "13th, 14th, 15th" Hijri rule from ringing on 13 Dhul-Hijjah.
     */
    public static boolean isSkipped(@NonNull Alarm alarm, long instantMillis) {
        return alarm.skipForbiddenDays && Occasions.isFastingForbidden(instantMillis);
    }

    /** Does the calendar day of {@code day} satisfy the alarm's repeat rule? */
    public static boolean matchesDay(@NonNull Alarm alarm, @NonNull Calendar day) {
        switch (alarm.repeatMode) {
            case Alarm.REPEAT_DAILY:
                return true;

            case Alarm.REPEAT_WEEKLY: {
                List<Integer> weekDays = alarm.weekDayList();
                // An empty selection behaves like "every day" rather than "never".
                return weekDays.isEmpty() || weekDays.contains(day.get(Calendar.DAY_OF_WEEK));
            }

            case Alarm.REPEAT_OCCASION: {
                Occasion occasion = Occasion.fromId(alarm.occasionId);
                return occasion != null && Occasions.matches(occasion, day.getTimeInMillis());
            }

            case Alarm.REPEAT_MONTHLY: {
                List<Integer> wanted = alarm.monthDayList();
                if (wanted.isEmpty()) {
                    return false;
                }
                long millis = day.getTimeInMillis();
                final int dayOfMonth;
                final int lastDayOfMonth;
                if (alarm.calendarType == Alarm.CALENDAR_HIJRI) {
                    HijriDates.Snapshot hijri = HijriDates.snapshot(millis);
                    dayOfMonth = hijri.day;
                    lastDayOfMonth = hijri.monthLength;
                } else {
                    dayOfMonth = day.get(Calendar.DAY_OF_MONTH);
                    lastDayOfMonth = day.getActualMaximum(Calendar.DAY_OF_MONTH);
                }
                if (wanted.contains(dayOfMonth)) {
                    return true;
                }
                // "31st" in a 30 day month falls back to the last day when clamping is on.
                if (alarm.clampToMonthEnd && dayOfMonth == lastDayOfMonth) {
                    for (int wantedDay : wanted) {
                        if (wantedDay > lastDayOfMonth) {
                            return true;
                        }
                    }
                }
                return false;
            }

            case Alarm.REPEAT_ONCE:
            default:
                return false;
        }
    }

    /** The single instant a {@link Alarm#REPEAT_ONCE} alarm fires. */
    public static long onceInstant(@NonNull Alarm alarm) {
        Calendar cal = Calendar.getInstance();
        if (alarm.onceDateMillis > 0L) {
            cal.setTimeInMillis(alarm.onceDateMillis);
        }
        applyTime(cal, alarm.hour, alarm.minute);
        return cal.getTimeInMillis();
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
