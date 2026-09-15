package com.afzal.rozaalarm.data;

/** Projection row: how many fasts were kept in one month. */
public class MonthTally {

    /** Hijri year, or the Gregorian year when grouping by the Gregorian calendar. */
    public int year;

    /** Zero based Hijri month, or 1-12 when grouping by the Gregorian calendar. */
    public int month;

    public int kept;

    public MonthTally() {
    }
}
