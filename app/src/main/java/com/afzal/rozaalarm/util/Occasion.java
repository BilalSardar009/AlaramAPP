package com.afzal.rozaalarm.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * The fasting days of the Islamic year that this app knows about.
 *
 * <p>Each constant carries a stable {@link #id} used in the database and in alarm rules, so the
 * enum can be reordered or extended without breaking saved data.</p>
 */
public enum Occasion {

    /** The month of Ramadan — fasting is obligatory for every day of it. */
    RAMADAN("ramadan", Category.OBLIGATORY),

    /** 9 Dhul-Hijjah, the Day of Arafah. */
    ARAFAH("arafah", Category.EMPHASISED),

    /** 10 Muharram, the Day of Ashura. */
    ASHURA("ashura", Category.EMPHASISED),

    /** 9 Muharram, fasted together with Ashura. */
    TASUA("tasua", Category.RECOMMENDED),

    /** The first nine days of Dhul-Hijjah. */
    DHUL_HIJJAH_FIRST_NINE("dhul_hijjah_nine", Category.RECOMMENDED),

    /** Voluntary fasting through the month of Muharram. */
    MUHARRAM("muharram", Category.RECOMMENDED),

    /** 15 Sha'ban. */
    SHABAN_MID("shaban_mid", Category.RECOMMENDED),

    /** The six days of Shawwal, customarily the 2nd to the 7th. */
    SHAWWAL_SIX("shawwal_six", Category.RECOMMENDED),

    /** Ayyam al-Beed — the 13th, 14th and 15th of every Islamic month. */
    WHITE_DAYS("white_days", Category.RECOMMENDED),

    /** Mondays and Thursdays. */
    MONDAY_THURSDAY("mon_thu", Category.RECOMMENDED),

    /** 1 Shawwal — fasting is not permitted. */
    EID_AL_FITR("eid_fitr", Category.FORBIDDEN),

    /** 10 Dhul-Hijjah — fasting is not permitted. */
    EID_AL_ADHA("eid_adha", Category.FORBIDDEN),

    /** 11, 12 and 13 Dhul-Hijjah, the days of Tashreeq — fasting is not permitted. */
    TASHREEQ("tashreeq", Category.FORBIDDEN);

    /** How the day is treated, which also drives its colour on the calendar. */
    public enum Category {
        /** Fasting is obligatory. */
        OBLIGATORY,
        /** A strongly emphasised voluntary fast. */
        EMPHASISED,
        /** A voluntary fast. */
        RECOMMENDED,
        /** A day on which fasting is not permitted. */
        FORBIDDEN
    }

    private final String id;
    private final Category category;

    Occasion(String id, Category category) {
        this.id = id;
        this.category = category;
    }

    @NonNull
    public String id() {
        return id;
    }

    @NonNull
    public Category category() {
        return category;
    }

    public boolean isForbidden() {
        return category == Category.FORBIDDEN;
    }

    /** True when this occasion can be used as the repeat rule of an alarm. */
    public boolean isSchedulable() {
        return !isForbidden();
    }

    @Nullable
    public static Occasion fromId(@Nullable String id) {
        if (id == null) {
            return null;
        }
        for (Occasion occasion : values()) {
            if (occasion.id.equals(id)) {
                return occasion;
            }
        }
        return null;
    }
}
