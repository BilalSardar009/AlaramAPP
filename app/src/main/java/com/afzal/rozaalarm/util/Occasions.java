package com.afzal.rozaalarm.util;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Works out which fasting occasions fall on a given day.
 *
 * <p>{@link #forHijriDay} is deliberately pure — it takes the Hijri date as numbers rather than an
 * instant — so the rules can be unit tested on a plain JVM without the ICU calendar.</p>
 */
public final class Occasions {

    // Zero based month indices, matching android.icu.util.IslamicCalendar.
    public static final int MUHARRAM = 0;
    public static final int SAFAR = 1;
    public static final int RABI_AL_AWWAL = 2;
    public static final int RABI_AL_THANI = 3;
    public static final int JUMADA_AL_ULA = 4;
    public static final int JUMADA_AL_AKHIRAH = 5;
    public static final int RAJAB = 6;
    public static final int SHABAN = 7;
    public static final int RAMADAN = 8;
    public static final int SHAWWAL = 9;
    public static final int DHUL_QIDAH = 10;
    public static final int DHUL_HIJJAH = 11;

    private Occasions() {
    }

    /**
     * Every occasion falling on one day, most significant first.
     *
     * <p>Mondays and Thursdays are deliberately not reported. They would mark roughly nine days a
     * month, which drowns out the days that actually are special; a weekly alarm says the same
     * thing more plainly.</p>
     *
     * @param hijriMonth zero based Hijri month, {@link #MUHARRAM} … {@link #DHUL_HIJJAH}
     * @param hijriDay   Hijri day of month, 1-30
     */
    @NonNull
    public static List<Occasion> forHijriDay(int hijriMonth, int hijriDay) {
        List<Occasion> found = new ArrayList<>(3);

        // ---- days on which fasting is not permitted -------------------------------------------
        if (hijriMonth == SHAWWAL && hijriDay == 1) {
            found.add(Occasion.EID_AL_FITR);
        }
        if (hijriMonth == DHUL_HIJJAH && hijriDay == 10) {
            found.add(Occasion.EID_AL_ADHA);
        }
        if (hijriMonth == DHUL_HIJJAH && hijriDay >= 11 && hijriDay <= 13) {
            found.add(Occasion.TASHREEQ);
        }
        if (!found.isEmpty()) {
            // Nothing voluntary is listed alongside a day fasting is not permitted on. This is why
            // 13 Dhul-Hijjah is not offered as a White Day.
            return Collections.unmodifiableList(found);
        }

        // ---- obligatory -----------------------------------------------------------------------
        if (hijriMonth == RAMADAN) {
            // Ramadan supersedes every voluntary fast, so it is reported on its own.
            found.add(Occasion.RAMADAN);
            return Collections.unmodifiableList(found);
        }

        // ---- emphasised voluntary fasts -------------------------------------------------------
        if (hijriMonth == DHUL_HIJJAH && hijriDay == 9) {
            found.add(Occasion.ARAFAH);
        }
        if (hijriMonth == MUHARRAM && hijriDay == 10) {
            found.add(Occasion.ASHURA);
        }
        if (hijriMonth == MUHARRAM && hijriDay == 9) {
            found.add(Occasion.TASUA);
        }

        // ---- the rest -------------------------------------------------------------------------
        if (hijriMonth == DHUL_HIJJAH && hijriDay >= 1 && hijriDay <= 9) {
            found.add(Occasion.DHUL_HIJJAH_FIRST_NINE);
        }
        if (hijriMonth == SHABAN && hijriDay == 15) {
            found.add(Occasion.SHABAN_MID);
        }
        if (hijriMonth == SHAWWAL && hijriDay >= 2 && hijriDay <= 7) {
            found.add(Occasion.SHAWWAL_SIX);
        }
        if (hijriDay >= 13 && hijriDay <= 15) {
            found.add(Occasion.WHITE_DAYS);
        }
        // Fasting anywhere in Muharram is recommended, but it is the least specific label of the
        // lot, so it sits below the named days — the 13th of Muharram reads as a White Day.
        if (hijriMonth == MUHARRAM) {
            found.add(Occasion.MUHARRAM);
        }

        return Collections.unmodifiableList(found);
    }

    /** Convenience wrapper that converts the instant first. */
    @NonNull
    public static List<Occasion> forInstant(long millis) {
        HijriDates.Snapshot hijri = HijriDates.snapshot(millis);
        return forHijriDay(hijri.month, hijri.day);
    }

    /** The occasion a day should be labelled with, or {@code null} for an ordinary day. */
    @androidx.annotation.Nullable
    public static Occasion primaryForInstant(long millis) {
        List<Occasion> all = forInstant(millis);
        return all.isEmpty() ? null : all.get(0);
    }

    /** True when the given day is Eid al-Fitr, Eid al-Adha or one of the days of Tashreeq. */
    public static boolean isFastingForbidden(long millis) {
        for (Occasion occasion : forInstant(millis)) {
            if (occasion.isForbidden()) {
                return true;
            }
        }
        return false;
    }

    /** True when {@code millis} falls on the given occasion. */
    public static boolean matches(@NonNull Occasion occasion, long millis) {
        return forInstant(millis).contains(occasion);
    }
}
