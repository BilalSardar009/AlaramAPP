package com.afzal.rozaalarm.util;

import android.icu.util.IslamicCalendar;
import android.icu.util.ULocale;

import androidx.annotation.NonNull;

import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Gregorian ⇄ Hijri conversion, using {@link IslamicCalendar} with the Umm al-Qura calculation.
 *
 * <p>Printed calendars and local moon sighting do not always agree with the calculated date, so
 * every conversion goes through a resolved offset built from two things:</p>
 * <ol>
 *     <li>a global correction the user sets once ("today is actually the 14th"), and</li>
 *     <li>optional per-month corrections, for when a single month — usually Ramadan or Shawwal —
 *         is announced a day earlier or later than calculated.</li>
 * </ol>
 *
 * <p>The offsets are held in memory and refreshed by {@link #configure}; conversion happens on the
 * scheduler's background threads as well as the UI thread, so it must not touch the database.</p>
 */
public final class HijriDates {

    public static final String[] MONTHS_EN = {
            "Muharram", "Safar", "Rabi' al-Awwal", "Rabi' al-Thani", "Jumada al-Ula",
            "Jumada al-Akhirah", "Rajab", "Sha'ban", "Ramadan", "Shawwal",
            "Dhu al-Qi'dah", "Dhu al-Hijjah"
    };

    public static final String[] MONTHS_UR = {
            "محرم", "صفر", "ربیع الاول", "ربیع الثانی", "جمادی الاول",
            "جمادی الثانی", "رجب", "شعبان", "رمضان", "شوال",
            "ذوالقعدہ", "ذوالحجہ"
    };

    /** The furthest a correction may move a date, in either direction. */
    public static final int MAX_OFFSET = 3;

    private static final long DAY_MILLIS = 24L * 60L * 60L * 1000L;

    private static volatile int globalOffset = 0;

    /** Keyed by {@link #monthKey}; values are extra days on top of {@link #globalOffset}. */
    @NonNull
    private static volatile Map<Integer, Integer> monthOffsets = Collections.emptyMap();

    private HijriDates() {
    }

    // ---- configuration ------------------------------------------------------------------------

    /** Replaces the corrections used by every conversion from here on. */
    public static void configure(int newGlobalOffset, @NonNull Map<Integer, Integer> newMonthOffsets) {
        globalOffset = clampOffset(newGlobalOffset);
        Map<Integer, Integer> copy = new HashMap<>();
        for (Map.Entry<Integer, Integer> entry : newMonthOffsets.entrySet()) {
            copy.put(entry.getKey(), clampOffset(entry.getValue()));
        }
        monthOffsets = Collections.unmodifiableMap(copy);
    }

    public static int globalOffset() {
        return globalOffset;
    }

    /** Stable key for a Hijri month, used by the corrections map and the database. */
    public static int monthKey(int hijriYear, int hijriMonth) {
        return hijriYear * 12 + hijriMonth;
    }

    public static int yearOfKey(int key) {
        return key / 12;
    }

    public static int monthOfKey(int key) {
        return key % 12;
    }

    /** The correction applied to one specific Hijri month, on top of the global one. */
    public static int monthOffset(int hijriYear, int hijriMonth) {
        Integer value = monthOffsets.get(monthKey(hijriYear, hijriMonth));
        return value == null ? 0 : value;
    }

    public static boolean hasMonthOffset(int hijriYear, int hijriMonth) {
        return monthOffsets.containsKey(monthKey(hijriYear, hijriMonth));
    }

    /**
     * The total offset in effect for an instant: the global correction, plus any correction for
     * the Hijri month that instant lands in.
     */
    public static int resolvedOffset(long millis) {
        IslamicCalendar rough = rawCalendar(millis, globalOffset);
        Integer extra = monthOffsets.get(
                monthKey(rough.get(IslamicCalendar.YEAR), rough.get(IslamicCalendar.MONTH)));
        return extra == null ? globalOffset : globalOffset + extra;
    }

    /** Every Hijri field of one instant, read from a single conversion. */
    public static final class Snapshot {
        public final int year;
        /** Zero based, 0 = Muharram. */
        public final int month;
        public final int day;
        /** Days in the month this instant falls in, 29 or 30. */
        public final int monthLength;

        Snapshot(int year, int month, int day, int monthLength) {
            this.year = year;
            this.month = month;
            this.day = day;
            this.monthLength = monthLength;
        }
    }

    /**
     * Reads the whole Hijri date at once. The scheduler walks hundreds of candidate days, so
     * asking for the fields one at a time would convert the same instant four times over.
     */
    @NonNull
    public static Snapshot snapshot(long millis) {
        IslamicCalendar calendar = rawCalendar(millis, resolvedOffset(millis));
        return new Snapshot(
                calendar.get(IslamicCalendar.YEAR),
                calendar.get(IslamicCalendar.MONTH),
                calendar.get(IslamicCalendar.DAY_OF_MONTH),
                calendar.getActualMaximum(IslamicCalendar.DAY_OF_MONTH));
    }

    // ---- Gregorian -> Hijri -------------------------------------------------------------------

    /** Hijri day of month (1-30). */
    public static int dayOfMonth(long millis) {
        return dayOfMonth(millis, resolvedOffset(millis));
    }

    /** Hijri month index, 0 = Muharram. */
    public static int month(long millis) {
        return month(millis, resolvedOffset(millis));
    }

    public static int year(long millis) {
        return year(millis, resolvedOffset(millis));
    }

    /** Number of days in the Hijri month containing the instant (29 or 30). */
    public static int monthLength(long millis) {
        return monthLength(millis, resolvedOffset(millis));
    }

    @NonNull
    public static String formatEnglish(long millis) {
        return formatEnglish(millis, resolvedOffset(millis));
    }

    @NonNull
    public static String formatUrdu(long millis) {
        return formatUrdu(millis, resolvedOffset(millis));
    }

    // ---- explicit-offset forms, used by tests and by the correction screen ---------------------

    public static int dayOfMonth(long millis, int offsetDays) {
        return rawCalendar(millis, offsetDays).get(IslamicCalendar.DAY_OF_MONTH);
    }

    public static int month(long millis, int offsetDays) {
        return rawCalendar(millis, offsetDays).get(IslamicCalendar.MONTH);
    }

    public static int year(long millis, int offsetDays) {
        return rawCalendar(millis, offsetDays).get(IslamicCalendar.YEAR);
    }

    public static int monthLength(long millis, int offsetDays) {
        return rawCalendar(millis, offsetDays).getActualMaximum(IslamicCalendar.DAY_OF_MONTH);
    }

    /** e.g. {@code "14 Ramadan 1447 AH"}. */
    @NonNull
    public static String formatEnglish(long millis, int offsetDays) {
        IslamicCalendar calendar = rawCalendar(millis, offsetDays);
        return calendar.get(IslamicCalendar.DAY_OF_MONTH)
                + " " + monthNameEnglish(calendar.get(IslamicCalendar.MONTH))
                + " " + calendar.get(IslamicCalendar.YEAR) + " AH";
    }

    /** e.g. {@code "۱۴ رمضان ۱۴۴۷ھ"} written with Urdu digits. */
    @NonNull
    public static String formatUrdu(long millis, int offsetDays) {
        IslamicCalendar calendar = rawCalendar(millis, offsetDays);
        return toUrduDigits(calendar.get(IslamicCalendar.DAY_OF_MONTH))
                + " " + monthNameUrdu(calendar.get(IslamicCalendar.MONTH))
                + " " + toUrduDigits(calendar.get(IslamicCalendar.YEAR)) + "ھ";
    }

    // ---- Hijri -> Gregorian -------------------------------------------------------------------

    /**
     * Local midnight of the Gregorian day on which the given Hijri date falls, with the month's
     * corrections applied.
     */
    public static long gregorianMidnightOf(int hijriYear, int hijriMonth, int hijriDay) {
        IslamicCalendar calendar = newCalendar();
        calendar.clear();
        calendar.set(hijriYear, hijriMonth, Math.max(1, hijriDay));
        long icuMillis = calendar.getTimeInMillis();

        int offset = globalOffset + monthOffset(hijriYear, hijriMonth);
        return midnight(icuMillis - offset * DAY_MILLIS);
    }

    /** Local midnight of the first day of a Hijri month. */
    public static long gregorianMidnightOfMonthStart(int hijriYear, int hijriMonth) {
        return gregorianMidnightOf(hijriYear, hijriMonth, 1);
    }

    /** Length of a Hijri month identified by year and month rather than by an instant. */
    public static int monthLengthOf(int hijriYear, int hijriMonth) {
        IslamicCalendar calendar = newCalendar();
        calendar.clear();
        calendar.set(hijriYear, hijriMonth, 1);
        return calendar.getActualMaximum(IslamicCalendar.DAY_OF_MONTH);
    }

    // ---- names and digits ---------------------------------------------------------------------

    @NonNull
    public static String monthNameEnglish(int hijriMonth) {
        return MONTHS_EN[clampMonth(hijriMonth)];
    }

    @NonNull
    public static String monthNameUrdu(int hijriMonth) {
        return MONTHS_UR[clampMonth(hijriMonth)];
    }

    /** e.g. {@code "Ramadan 1447 AH"}. */
    @NonNull
    public static String monthTitleEnglish(int hijriYear, int hijriMonth) {
        return monthNameEnglish(hijriMonth) + " " + hijriYear + " AH";
    }

    @NonNull
    public static String monthTitleUrdu(int hijriYear, int hijriMonth) {
        return monthNameUrdu(hijriMonth) + " " + toUrduDigits(hijriYear) + "ھ";
    }

    /** Converts Western digits to the Eastern Arabic-Indic digits used in Urdu typography. */
    @NonNull
    public static String toUrduDigits(int value) {
        final char[] urduDigits = {'۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹'};
        String plain = String.valueOf(value);
        StringBuilder sb = new StringBuilder(plain.length());
        for (int i = 0; i < plain.length(); i++) {
            char c = plain.charAt(i);
            sb.append(c >= '0' && c <= '9' ? urduDigits[c - '0'] : c);
        }
        return sb.toString();
    }

    // ---- internals ----------------------------------------------------------------------------

    @NonNull
    private static IslamicCalendar newCalendar() {
        // Umm al-Qura is the civil calendar in use across most of the Muslim world and matches the
        // printed Hijri calendars people keep at home.
        IslamicCalendar calendar = new IslamicCalendar(ULocale.getDefault());
        calendar.setCalculationType(IslamicCalendar.CalculationType.ISLAMIC_UMALQURA);
        return calendar;
    }

    @NonNull
    private static IslamicCalendar rawCalendar(long millis, int offsetDays) {
        IslamicCalendar calendar = newCalendar();
        calendar.setTime(new Date(millis + offsetDays * DAY_MILLIS));
        return calendar;
    }

    /** Start of the local day containing the instant. */
    public static long midnight(long millis) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(millis);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    public static int clampOffset(int offsetDays) {
        return Math.max(-MAX_OFFSET, Math.min(MAX_OFFSET, offsetDays));
    }

    private static int clampMonth(int month) {
        return Math.max(0, Math.min(MONTHS_EN.length - 1, month));
    }
}
