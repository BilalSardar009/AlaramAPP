package com.afzal.rozaalarm.ui.adapter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.afzal.rozaalarm.util.Occasion;

import java.util.Collections;
import java.util.List;

/** One cell of the month grid. Blank cells pad the row before the first day of the month. */
public class CalendarDay {

    public final boolean blank;
    public final int hijriDay;
    public final int gregorianDay;
    public final long millis;
    /** Local {@code yyyyMMdd} of this day — the identity used for selection and for alarms. */
    public final int dateKey;
    public final boolean today;

    @NonNull
    public final List<Occasion> occasions;

    /** True when a fast has been recorded for this day. */
    public boolean logged;

    /** True when the record for this day is a missed fast rather than a kept one. */
    public boolean missed;

    /** True when an alarm is set to ring on this day. */
    public boolean alarmed;

    private CalendarDay() {
        this.blank = true;
        this.hijriDay = 0;
        this.gregorianDay = 0;
        this.millis = 0L;
        this.dateKey = 0;
        this.today = false;
        this.occasions = Collections.emptyList();
    }

    public CalendarDay(int hijriDay, int gregorianDay, long millis, int dateKey, boolean today,
                       @NonNull List<Occasion> occasions) {
        this.blank = false;
        this.hijriDay = hijriDay;
        this.gregorianDay = gregorianDay;
        this.millis = millis;
        this.dateKey = dateKey;
        this.today = today;
        this.occasions = occasions;
    }

    @NonNull
    public static CalendarDay blank() {
        return new CalendarDay();
    }

    /** The occasion whose colour the day is marked with, or {@code null} for an ordinary day. */
    @Nullable
    public Occasion primaryOccasion() {
        return occasions.isEmpty() ? null : occasions.get(0);
    }

    public boolean isForbidden() {
        for (Occasion occasion : occasions) {
            if (occasion.isForbidden()) {
                return true;
            }
        }
        return false;
    }
}
