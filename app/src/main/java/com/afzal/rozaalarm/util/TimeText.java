package com.afzal.rozaalarm.util;

import android.content.Context;
import android.text.format.DateFormat;

import androidx.annotation.NonNull;

import com.afzal.rozaalarm.R;
import com.afzal.rozaalarm.data.Alarm;

import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Formatting helpers shared by the UI and the notifications. */
public final class TimeText {

    private static final long MINUTE = 60_000L;
    private static final long HOUR = 60L * MINUTE;
    private static final long DAY = 24L * HOUR;

    private TimeText() {
    }

    /** Respects the user's 12/24 hour system setting. */
    @NonNull
    public static String time(@NonNull Context context, long millis) {
        return DateFormat.getTimeFormat(context).format(new Date(millis));
    }

    @NonNull
    public static String time(@NonNull Context context, int hour, int minute) {
        Calendar cal = Calendar.getInstance();
        Occurrences.applyTime(cal, hour, minute);
        return time(context, cal.getTimeInMillis());
    }

    /** Big clock face used on the alarm cards, e.g. {@code "3:00"}. */
    @NonNull
    public static String clockDigits(@NonNull Context context, int hour, int minute) {
        if (DateFormat.is24HourFormat(context)) {
            return String.format(Locale.getDefault(), "%02d:%02d", hour, minute);
        }
        int display = hour % 12;
        if (display == 0) {
            display = 12;
        }
        return String.format(Locale.getDefault(), "%d:%02d", display, minute);
    }

    /** {@code "AM"} / {@code "PM"}, or an empty string in 24 hour mode. */
    @NonNull
    public static String clockSuffix(@NonNull Context context, int hour) {
        if (DateFormat.is24HourFormat(context)) {
            return "";
        }
        return hour < 12 ? "AM" : "PM";
    }

    @NonNull
    public static String date(@NonNull Context context, long millis) {
        return DateFormat.format("EEE, d MMM", millis).toString();
    }

    @NonNull
    public static String fullDate(@NonNull Context context, long millis) {
        return DateFormat.format("EEEE, d MMMM yyyy", millis).toString();
    }

    @NonNull
    public static String dateTime(@NonNull Context context, long millis) {
        return date(context, millis) + " · " + time(context, millis);
    }

    /** "in 2d 5h" / "in 40 min" — used by the countdown card. */
    @NonNull
    public static String countdown(@NonNull Context context, long targetMillis, long nowMillis) {
        long delta = targetMillis - nowMillis;
        if (delta <= MINUTE) {
            return context.getString(R.string.countdown_now);
        }
        if (delta >= DAY) {
            long days = delta / DAY;
            long hours = (delta % DAY) / HOUR;
            return context.getString(R.string.countdown_days, (int) days, (int) hours);
        }
        if (delta >= HOUR) {
            long hours = delta / HOUR;
            long minutes = (delta % HOUR) / MINUTE;
            return context.getString(R.string.countdown_hours, (int) hours, (int) minutes);
        }
        return context.getString(R.string.countdown_minutes, (int) (delta / MINUTE));
    }

    /** One line describing the repeat rule, e.g. "White Days · 13, 14, 15 each month". */
    @NonNull
    public static String repeatSummary(@NonNull Context context, @NonNull Alarm alarm) {
        switch (alarm.repeatMode) {
            case Alarm.REPEAT_ONCE:
                return context.getString(R.string.summary_once,
                        date(context, Occurrences.onceInstant(alarm)));

            case Alarm.REPEAT_DAILY:
                return context.getString(R.string.summary_daily);

            case Alarm.REPEAT_WEEKLY:
                return context.getString(R.string.summary_weekly, weekDayNames(context, alarm));

            case Alarm.REPEAT_OCCASION: {
                Occasion occasion = Occasion.fromId(alarm.occasionId);
                return occasion == null
                        ? context.getString(R.string.occasion_pick)
                        : context.getString(R.string.summary_occasion,
                                OccasionText.name(context, occasion));
            }

            case Alarm.REPEAT_MONTHLY:
            default:
                if (alarm.isAyyamAlBeed()) {
                    return context.getString(alarm.calendarType == Alarm.CALENDAR_HIJRI
                            ? R.string.summary_ayyam_hijri
                            : R.string.summary_ayyam_gregorian);
                }
                String days = monthDayNames(context, alarm);
                return context.getString(alarm.calendarType == Alarm.CALENDAR_HIJRI
                        ? R.string.summary_monthly_hijri
                        : R.string.summary_monthly_gregorian, days);
        }
    }

    @NonNull
    public static String monthDayNames(@NonNull Context context, @NonNull Alarm alarm) {
        List<Integer> days = alarm.monthDayList();
        if (days.isEmpty()) {
            return context.getString(R.string.selected_days_none);
        }
        StringBuilder sb = new StringBuilder();
        String join = context.getString(R.string.summary_day_join);
        for (int i = 0; i < days.size(); i++) {
            if (i > 0) {
                sb.append(join);
            }
            sb.append(alarm.calendarType == Alarm.CALENDAR_HIJRI
                    ? String.valueOf(days.get(i))
                    : ordinal(days.get(i)));
        }
        return sb.toString();
    }

    @NonNull
    public static String weekDayNames(@NonNull Context context, @NonNull Alarm alarm) {
        List<Integer> days = alarm.weekDayList();
        if (days.isEmpty()) {
            return context.getString(R.string.summary_daily);
        }
        StringBuilder sb = new StringBuilder();
        String join = context.getString(R.string.summary_day_join);
        for (int i = 0; i < days.size(); i++) {
            if (i > 0) {
                sb.append(join);
            }
            sb.append(weekDayName(context, days.get(i)));
        }
        return sb.toString();
    }

    @NonNull
    public static String weekDayName(@NonNull Context context, int calendarDayOfWeek) {
        switch (calendarDayOfWeek) {
            case Calendar.SUNDAY: return context.getString(R.string.day_short_sun);
            case Calendar.MONDAY: return context.getString(R.string.day_short_mon);
            case Calendar.TUESDAY: return context.getString(R.string.day_short_tue);
            case Calendar.WEDNESDAY: return context.getString(R.string.day_short_wed);
            case Calendar.THURSDAY: return context.getString(R.string.day_short_thu);
            case Calendar.FRIDAY: return context.getString(R.string.day_short_fri);
            case Calendar.SATURDAY: default: return context.getString(R.string.day_short_sat);
        }
    }

    /** 1 -> "1st", 13 -> "13th", 22 -> "22nd". */
    @NonNull
    public static String ordinal(int value) {
        if (value >= 11 && value <= 13) {
            return value + "th";
        }
        switch (value % 10) {
            case 1: return value + "st";
            case 2: return value + "nd";
            case 3: return value + "rd";
            default: return value + "th";
        }
    }
}
