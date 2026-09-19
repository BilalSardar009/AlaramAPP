package com.afzal.rozaalarm.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A single alarm definition.
 *
 * <p>There are only two kinds of alarm, and both come from the calendar screen: one that follows a
 * named fasting occasion ({@link #REPEAT_OCCASION} — Ramadan, Ashura, the White Days …) and one
 * that rings on a set of dates the user picked off the grid ({@link #REPEAT_DATES}).</p>
 */
@Entity(tableName = "alarms")
public class Alarm {

    // ---- repeat modes -------------------------------------------------------------------------
    /**
     * Retired. Version 1 stored a single date here; {@link AppDatabase#MIGRATION_2_3} rewrites
     * those rows as {@link #REPEAT_DATES}, so nothing reads this value any more.
     */
    public static final int REPEAT_ONCE = 0;

    /** Fires on every day matching a named Islamic occasion (Ramadan, Arafah, Ashura …). */
    public static final int REPEAT_OCCASION = 4;

    /** Fires on each of the specific days listed in {@link #dateKeys}. */
    public static final int REPEAT_DATES = 5;

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
    public int repeatMode = REPEAT_OCCASION;

    /**
     * The {@link com.afzal.rozaalarm.util.Occasion} id this alarm follows, for
     * {@link #REPEAT_OCCASION}. Null for every other repeat mode.
     */
    @Nullable
    public String occasionId = null;

    /**
     * Comma separated {@code yyyyMMdd} days for {@link #REPEAT_DATES}, e.g.
     * {@code "20260921,20260922"}. These are local calendar days, so the Hijri date they show on
     * the calendar stays put even if the Hijri correction changes later.
     */
    @NonNull
    @ColumnInfo(defaultValue = "")
    public String dateKeys = "";

    /**
     * Skip Eid al-Fitr, Eid al-Adha and the days of Tashreeq, on which fasting is not permitted.
     * This is what keeps a White Days alarm from ringing on 13 Dhu al-Hijjah.
     */
    public boolean skipForbiddenDays = true;

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

    // ---- retired columns ----------------------------------------------------------------------
    // The daily, weekly and monthly rules were removed in version 3. Their columns stay on the
    // entity because the table still has them: dropping a column means rebuilding the table, and
    // the fasting history lives in the same database. Nothing reads them.

    /** @deprecated retired with the monthly rule; kept only so the table schema still matches. */
    @Deprecated
    public int calendarType = CALENDAR_GREGORIAN;

    /** @deprecated retired with the monthly rule; kept only so the table schema still matches. */
    @Deprecated
    @NonNull
    public String monthDays = "";

    /** @deprecated retired with the weekly rule; kept only so the table schema still matches. */
    @Deprecated
    @NonNull
    public String weekDays = "";

    /** @deprecated retired with the monthly rule; kept only so the table schema still matches. */
    @Deprecated
    public boolean clampToMonthEnd = true;

    /** @deprecated retired with the one-off rule; kept only so the table schema still matches. */
    @Deprecated
    public long onceDateMillis = 0L;

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
        copy.occasionId = source.occasionId;
        copy.dateKeys = source.dateKeys;
        copy.skipForbiddenDays = source.skipForbiddenDays;
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

    /**
     * A copy that is still the same row, for editing an alarm the list is currently showing.
     *
     * <p>The list is diffed against what it already holds, so editing the object it is holding
     * would leave nothing for the diff to notice and the row would keep showing the old time.</p>
     */
    @Ignore
    @NonNull
    public static Alarm editableCopy(@NonNull Alarm source) {
        Alarm copy = copyOf(source);
        copy.id = source.id;
        copy.createdAt = source.createdAt;
        copy.lastTriggeredAt = source.lastTriggeredAt;
        return copy;
    }

    /** The {@code yyyyMMdd} days this alarm rings on, ascending. */
    @Ignore
    @NonNull
    public List<Integer> dateKeyList() {
        return parseCsv(dateKeys, 10000101, 99991231);
    }

    @Ignore
    public void setDateKeyList(@NonNull List<Integer> keys) {
        dateKeys = joinCsv(keys);
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
