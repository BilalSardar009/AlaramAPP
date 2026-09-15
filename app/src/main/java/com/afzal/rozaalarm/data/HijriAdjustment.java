package com.afzal.rozaalarm.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * A user correction for one Hijri month, for when a month — usually Ramadan or Shawwal — is
 * announced a day earlier or later than the calculated Umm al-Qura date.
 */
@Entity(tableName = "hijri_adjustments")
public class HijriAdjustment {

    /** {@code hijriYear * 12 + hijriMonth}, see {@code HijriDates.monthKey}. */
    @PrimaryKey
    public int monthKey;

    /** Days to shift this month by, on top of the global correction. */
    public int offsetDays;

    public HijriAdjustment() {
    }

    public HijriAdjustment(int monthKey, int offsetDays) {
        this.monthKey = monthKey;
        this.offsetDays = offsetDays;
    }
}
