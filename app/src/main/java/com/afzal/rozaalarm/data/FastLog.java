package com.afzal.rozaalarm.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import java.util.Calendar;

/**
 * One day of the fasting record.
 *
 * <p>A day is identified by {@link #dateKey}, a {@code yyyyMMdd} integer in the device's local
 * zone, which is unique — a day is either recorded or it is not. The Hijri date is stored
 * alongside it so the history keeps showing the date the user saw when they logged it, even if
 * they later change the Hijri correction.</p>
 */
@Entity(tableName = "fast_log", indices = {@Index(value = "dateKey", unique = true)})
public class FastLog {

    /** The fast was kept. */
    public static final int STATUS_KEPT = 0;
    /** The day was intended but the fast was not kept. */
    public static final int STATUS_MISSED = 1;

    @PrimaryKey(autoGenerate = true)
    public long id;

    /** Local calendar day as {@code yyyyMMdd}, e.g. {@code 20270313}. */
    public int dateKey;

    public int hijriYear;

    /** Zero based, 0 = Muharram. */
    public int hijriMonth;

    public int hijriDay;

    /** {@link #STATUS_KEPT} or {@link #STATUS_MISSED}. */
    public int status = STATUS_KEPT;

    /** The {@link com.afzal.rozaalarm.util.Occasion} id this fast was for, if any. */
    @Nullable
    public String occasionId;

    @Nullable
    public String note;

    public long loggedAt = System.currentTimeMillis();

    public FastLog() {
    }

    // ---- date key helpers ---------------------------------------------------------------------

    /** {@code yyyyMMdd} for the local day containing the instant. */
    @Ignore
    public static int dateKeyOf(long millis) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(millis);
        return dateKeyOf(cal);
    }

    @Ignore
    public static int dateKeyOf(@NonNull Calendar cal) {
        return cal.get(Calendar.YEAR) * 10000
                + (cal.get(Calendar.MONTH) + 1) * 100
                + cal.get(Calendar.DAY_OF_MONTH);
    }

    /** Local midnight of the day a {@code yyyyMMdd} key refers to. */
    @Ignore
    public static long millisOfDateKey(int dateKey) {
        Calendar cal = Calendar.getInstance();
        cal.clear();
        cal.set(dateKey / 10000, ((dateKey / 100) % 100) - 1, dateKey % 100);
        return cal.getTimeInMillis();
    }

    /** The {@code yyyyMM} part, used to group the history by Gregorian month. */
    @Ignore
    public static int yearMonthOf(int dateKey) {
        return dateKey / 100;
    }

    /** First and last {@code yyyyMMdd} keys of a Gregorian month. */
    @Ignore
    public static int firstDateKeyOfYearMonth(int yearMonth) {
        return yearMonth * 100 + 1;
    }

    @Ignore
    public static int lastDateKeyOfYearMonth(int yearMonth) {
        return yearMonth * 100 + 31;
    }
}
