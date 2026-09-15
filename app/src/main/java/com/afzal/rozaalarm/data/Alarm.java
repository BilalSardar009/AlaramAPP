package com.afzal.rozaalarm.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A single alarm definition.
 *
 * <p>An alarm is described by a time of day plus a repeat rule. For monthly rules the days are
 * stored as a comma separated list of days-of-month (e.g. {@code "13,14,15"} for Ayyam al-Beed)
 * and may be interpreted against either the Gregorian or the Hijri calendar.</p>
 */
@Entity(tableName = "alarms")
public class Alarm {

    // ---- repeat modes -------------------------------------------------------------------------
    public static final int REPEAT_ONCE = 0;
    public static final int REPEAT_DAILY = 1;
    public static final int REPEAT_WEEKLY = 2;
    public static final int REPEAT_MONTHLY = 3;
    /** Fires on every day matching a named Islamic occasion (Ramadan, Arafah, Ashura …). */
    public static final int REPEAT_OCCASION = 4;

    // ---- calendar systems ---------------------------------------------------------------------
    public static final int CALENDAR_GREGORIAN = 0;
    public static final int CALENDAR_HIJRI = 1;

    @PrimaryKey(autoGenerate = true)
    public long id;

    /** User facing name, e.g. "Ayyam al-Beed Sehri". */
    @NonNull
    public String label = "";

    /** Hour of day, 0-23. */
    public int hour = 3;

    /** Minute of hour, 0-59. */
    public int minute = 0;

    /** One of the {@code REPEAT_*} constants. */
    public int repeatMode = REPEAT_MONTHLY;

    /** One of the {@code CALENDAR_*} constants. Only meaningful for {@link #REPEAT_MONTHLY}. */
    public int calendarType = CALENDAR_GREGORIAN;

    /** Comma separated days of month for {@link #REPEAT_MONTHLY}, e.g. "13,14,15". */
    @NonNull
    public String monthDays = "13,14,15";

    /**
     * Comma separated days of week for {@link #REPEAT_WEEKLY} using
     * {@link java.util.Calendar#DAY_OF_WEEK} values (1 = Sunday … 7 = Saturday).
     */
    @NonNull
    public String weekDays = "";

    /**
     * When a selected day does not exist in a short month (e.g. the 31st in February) fire on the
     * last day of that month instead of skipping it.
     */
    public boolean clampToMonthEnd = true;

    /**
     * The {@link com.afzal.rozaalarm.util.Occasion} id this alarm follows, for
     * {@link #REPEAT_OCCASION}. Null for every other repeat mode.
     */
    @Nullable
    public String occasionId = null;

    /**
     * Skip Eid al-Fitr, Eid al-Adha and the days of Tashreeq, on which fasting is not permitted.
     * This is what keeps a "13th, 14th, 15th" rule from ringing on 13 Dhul-Hijjah.
     */
    public boolean skipForbiddenDays = true;

    /** Epoch millis of the chosen date for {@link #REPEAT_ONCE}; the time of day comes from
     *  {@link #hour}/{@link #minute}. */
    public long onceDateMillis = 0L;

    public boolean enabled = true;

    public boolean vibrate = true;

    /** Ringtone to play, or {@code null} for the system default alarm tone. */
    @Nullable
    public String toneUri = null;

    public int snoozeMinutes = 5;

    // ---- advance reminder ---------------------------------------------------------------------
    /** Post a heads-up reminder some days before the alarm actually rings. */
    public boolean preReminderEnabled = true;

    /** How many days before the alarm the reminder is posted (1 = the evening before). */
    public int preReminderDaysBefore = 1;

    public int preReminderHour = 20;

    public int preReminderMinute = 0;

    /**
     * When several selected days run back-to-back (13th, 14th, 15th) only remind before the first
     * day of the run instead of every single evening.
     */
    public boolean preReminderFirstDayOnly = true;

    public long createdAt = System.currentTimeMillis();

    /** Epoch millis this alarm last rang, or 0. */
    public long lastTriggeredAt = 0L;

    public Alarm() {
    }

    // ---- helpers ------------------------------------------------------------------------------

    /** A detached copy. {@code id} is left at 0 so saving it inserts a new row. */
    @Ignore
    @NonNull
    public static Alarm copyOf(@NonNull Alarm source) {
        Alarm copy = new Alarm();
        copy.label = source.label;
        copy.hour = source.hour;
        copy.minute = source.minute;
        copy.repeatMode = source.repeatMode;
        copy.calendarType = source.calendarType;
        copy.monthDays = source.monthDays;
        copy.weekDays = source.weekDays;
        copy.clampToMonthEnd = source.clampToMonthEnd;
        copy.occasionId = source.occasionId;
        copy.skipForbiddenDays = source.skipForbiddenDays;
        copy.onceDateMillis = source.onceDateMillis;
        copy.enabled = source.enabled;
        copy.vibrate = source.vibrate;
        copy.toneUri = source.toneUri;
        copy.snoozeMinutes = source.snoozeMinutes;
        copy.preReminderEnabled = source.preReminderEnabled;
        copy.preReminderDaysBefore = source.preReminderDaysBefore;
        copy.preReminderHour = source.preReminderHour;
        copy.preReminderMinute = source.preReminderMinute;
        copy.preReminderFirstDayOnly = source.preReminderFirstDayOnly;
        copy.createdAt = System.currentTimeMillis();
        return copy;
    }

    @Ignore
    @NonNull
    public List<Integer> monthDayList() {
        return parseCsv(monthDays, 1, 31);
    }

    @Ignore
    @NonNull
    public List<Integer> weekDayList() {
        return parseCsv(weekDays, 1, 7);
    }

    @Ignore
    public void setMonthDayList(@NonNull List<Integer> days) {
        monthDays = joinCsv(days);
    }

    @Ignore
    public void setWeekDayList(@NonNull List<Integer> days) {
        weekDays = joinCsv(days);
    }

    /** True when this alarm is the classic "white days" (13th, 14th, 15th) fasting rule. */
    @Ignore
    public boolean isAyyamAlBeed() {
        List<Integer> days = monthDayList();
        return repeatMode == REPEAT_MONTHLY
                && days.size() == 3
                && days.contains(13) && days.contains(14) && days.contains(15);
    }

    @Ignore
    @NonNull
    public static List<Integer> parseCsv(@Nullable String csv, int min, int max) {
        List<Integer> out = new ArrayList<>();
        if (csv == null || csv.trim().isEmpty()) {
            return out;
        }
        for (String part : csv.split(",")) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                int value = Integer.parseInt(trimmed);
                if (value >= min && value <= max && !out.contains(value)) {
                    out.add(value);
                }
            } catch (NumberFormatException ignored) {
                // Skip malformed entries rather than losing the whole alarm.
            }
        }
        Collections.sort(out);
        return out;
    }

    @Ignore
    @NonNull
    public static String joinCsv(@NonNull List<Integer> values) {
        List<Integer> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < sorted.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(sorted.get(i));
        }
        return sb.toString();
    }
}
