package com.afzal.rozaalarm.ui.adapter;

import androidx.annotation.NonNull;

import com.afzal.rozaalarm.data.FastLog;

import java.util.ArrayList;
import java.util.List;

/** The fasts kept in one month, either Hijri or Gregorian depending on how the list is grouped. */
public class MonthGroup {

    /** Hijri year, or Gregorian year. */
    public final int year;

    /** Zero based Hijri month, or 1-12 for Gregorian. */
    public final int month;

    public final boolean hijri;

    @NonNull
    public final List<FastLog> logs = new ArrayList<>();

    public MonthGroup(int year, int month, boolean hijri) {
        this.year = year;
        this.month = month;
        this.hijri = hijri;
    }

    public int count() {
        return logs.size();
    }

    /** Sort key that puts the newest month first. */
    public int sortKey() {
        return year * 12 + month;
    }
}
