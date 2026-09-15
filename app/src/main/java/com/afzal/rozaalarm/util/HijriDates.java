package com.afzal.rozaalarm.util;

import android.icu.util.IslamicCalendar;
import android.icu.util.ULocale;

import androidx.annotation.NonNull;

import java.util.Date;

/**
 * Thin helper around {@link IslamicCalendar} (Umm al-Qura) so the rest of the app can ask simple
 * questions such as "which Hijri day is this instant?".
 *
 * <p>Moon sighting differs from region to region, so every lookup takes a user configurable offset
 * in days which is applied before the conversion.</p>
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

    private static final long DAY_MILLIS = 24L * 60L * 60L * 1000L;

    private HijriDates() {
    }

    @NonNull
    private static IslamicCalendar calendarFor(long millis, int offsetDays) {
        // The no-arg constructor picks up the device time zone; Umm al-Qura is the civil calendar
        // used across most of the Muslim world and matches the printed Hijri calendars people own.
        IslamicCalendar calendar = new IslamicCalendar(ULocale.getDefault());
        calendar.setCalculationType(IslamicCalendar.CalculationType.ISLAMIC_UMALQURA);
        calendar.setTime(new Date(millis + offsetDays * DAY_MILLIS));
        return calendar;
    }

    /** Hijri day of month (1-30) for the given instant. */
    public static int dayOfMonth(long millis, int offsetDays) {
        return calendarFor(millis, offsetDays).get(IslamicCalendar.DAY_OF_MONTH);
    }

    /** Hijri month index, 0 = Muharram. */
    public static int month(long millis, int offsetDays) {
        return calendarFor(millis, offsetDays).get(IslamicCalendar.MONTH);
    }

    public static int year(long millis, int offsetDays) {
        return calendarFor(millis, offsetDays).get(IslamicCalendar.YEAR);
    }

    /** Number of days in the Hijri month containing the given instant (29 or 30). */
    public static int monthLength(long millis, int offsetDays) {
        return calendarFor(millis, offsetDays).getActualMaximum(IslamicCalendar.DAY_OF_MONTH);
    }

    /** e.g. {@code "14 Ramadan 1447 AH"}. */
    @NonNull
    public static String formatEnglish(long millis, int offsetDays) {
        IslamicCalendar calendar = calendarFor(millis, offsetDays);
        int month = clampMonth(calendar.get(IslamicCalendar.MONTH));
        return calendar.get(IslamicCalendar.DAY_OF_MONTH) + " " + MONTHS_EN[month]
                + " " + calendar.get(IslamicCalendar.YEAR) + " AH";
    }

    /** e.g. {@code "۱۴ رمضان ۱۴۴۷ھ"} written with Urdu digits. */
    @NonNull
    public static String formatUrdu(long millis, int offsetDays) {
        IslamicCalendar calendar = calendarFor(millis, offsetDays);
        int month = clampMonth(calendar.get(IslamicCalendar.MONTH));
        return toUrduDigits(calendar.get(IslamicCalendar.DAY_OF_MONTH)) + " " + MONTHS_UR[month]
                + " " + toUrduDigits(calendar.get(IslamicCalendar.YEAR)) + "ھ";
    }

    @NonNull
    public static String monthNameEnglish(long millis, int offsetDays) {
        return MONTHS_EN[clampMonth(month(millis, offsetDays))];
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

    private static int clampMonth(int month) {
        return Math.max(0, Math.min(MONTHS_EN.length - 1, month));
    }
}
