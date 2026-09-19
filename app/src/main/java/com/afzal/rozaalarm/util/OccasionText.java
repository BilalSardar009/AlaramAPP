package com.afzal.rozaalarm.util;

import android.content.Context;

import androidx.annotation.AttrRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import com.afzal.rozaalarm.R;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Names, descriptions, icons and colours for the {@link Occasion} values, in English and Urdu. */
public final class OccasionText {

    private OccasionText() {
    }

    @StringRes
    public static int nameEn(@NonNull Occasion occasion) {
        switch (occasion) {
            case RAMADAN: return R.string.occ_ramadan_en;
            case ARAFAH: return R.string.occ_arafah_en;
            case ASHURA: return R.string.occ_ashura_en;
            case TASUA: return R.string.occ_tasua_en;
            case MUHARRAM_9_10: return R.string.occ_muharram_9_10_en;
            case DHUL_HIJJAH_FIRST_NINE: return R.string.occ_dhul_hijjah_nine_en;
            case MUHARRAM: return R.string.occ_muharram_en;
            case SHABAN_MID: return R.string.occ_shaban_mid_en;
            case SHAWWAL_SIX: return R.string.occ_shawwal_six_en;
            case WHITE_DAYS: return R.string.occ_white_days_en;
            case MONDAY_THURSDAY: return R.string.occ_mon_thu_en;
            case EID_AL_FITR: return R.string.occ_eid_fitr_en;
            case EID_AL_ADHA: return R.string.occ_eid_adha_en;
            case TASHREEQ: default: return R.string.occ_tashreeq_en;
        }
    }

    @StringRes
    public static int nameUr(@NonNull Occasion occasion) {
        switch (occasion) {
            case RAMADAN: return R.string.occ_ramadan_ur;
            case ARAFAH: return R.string.occ_arafah_ur;
            case ASHURA: return R.string.occ_ashura_ur;
            case TASUA: return R.string.occ_tasua_ur;
            case MUHARRAM_9_10: return R.string.occ_muharram_9_10_ur;
            case DHUL_HIJJAH_FIRST_NINE: return R.string.occ_dhul_hijjah_nine_ur;
            case MUHARRAM: return R.string.occ_muharram_ur;
            case SHABAN_MID: return R.string.occ_shaban_mid_ur;
            case SHAWWAL_SIX: return R.string.occ_shawwal_six_ur;
            case WHITE_DAYS: return R.string.occ_white_days_ur;
            case MONDAY_THURSDAY: return R.string.occ_mon_thu_ur;
            case EID_AL_FITR: return R.string.occ_eid_fitr_ur;
            case EID_AL_ADHA: return R.string.occ_eid_adha_ur;
            case TASHREEQ: default: return R.string.occ_tashreeq_ur;
        }
    }

    @StringRes
    public static int noteEn(@NonNull Occasion occasion) {
        switch (occasion) {
            case RAMADAN: return R.string.occ_ramadan_note_en;
            case ARAFAH: return R.string.occ_arafah_note_en;
            case ASHURA: return R.string.occ_ashura_note_en;
            case TASUA: return R.string.occ_tasua_note_en;
            case MUHARRAM_9_10: return R.string.occ_muharram_9_10_note_en;
            case DHUL_HIJJAH_FIRST_NINE: return R.string.occ_dhul_hijjah_nine_note_en;
            case MUHARRAM: return R.string.occ_muharram_note_en;
            case SHABAN_MID: return R.string.occ_shaban_mid_note_en;
            case SHAWWAL_SIX: return R.string.occ_shawwal_six_note_en;
            case WHITE_DAYS: return R.string.occ_white_days_note_en;
            case MONDAY_THURSDAY: return R.string.occ_mon_thu_note_en;
            default: return R.string.occ_forbidden_note_en;
        }
    }

    @StringRes
    public static int noteUr(@NonNull Occasion occasion) {
        switch (occasion) {
            case RAMADAN: return R.string.occ_ramadan_note_ur;
            case ARAFAH: return R.string.occ_arafah_note_ur;
            case ASHURA: return R.string.occ_ashura_note_ur;
            case TASUA: return R.string.occ_tasua_note_ur;
            case MUHARRAM_9_10: return R.string.occ_muharram_9_10_note_ur;
            case DHUL_HIJJAH_FIRST_NINE: return R.string.occ_dhul_hijjah_nine_note_ur;
            case MUHARRAM: return R.string.occ_muharram_note_ur;
            case SHABAN_MID: return R.string.occ_shaban_mid_note_ur;
            case SHAWWAL_SIX: return R.string.occ_shawwal_six_note_ur;
            case WHITE_DAYS: return R.string.occ_white_days_note_ur;
            case MONDAY_THURSDAY: return R.string.occ_mon_thu_note_ur;
            default: return R.string.occ_forbidden_note_ur;
        }
    }

    /** Short English name, the one used on chips and in alarm summaries. */
    @NonNull
    public static String name(@NonNull Context context, @NonNull Occasion occasion) {
        return context.getString(nameEn(occasion));
    }

    /** "English • اردو", for notifications. */
    @NonNull
    public static String bilingualName(@NonNull Context context, @NonNull Occasion occasion) {
        return Bilingual.inline(context, nameEn(occasion), nameUr(occasion));
    }

    @NonNull
    public static String note(@NonNull Context context, @NonNull Occasion occasion) {
        return context.getString(noteEn(occasion));
    }

    @StringRes
    public static int categoryLabel(@NonNull Occasion occasion) {
        switch (occasion.category()) {
            case OBLIGATORY: return R.string.occ_category_obligatory;
            case EMPHASISED: return R.string.occ_category_emphasised;
            case FORBIDDEN: return R.string.occ_category_forbidden;
            case RECOMMENDED: default: return R.string.occ_category_recommended;
        }
    }

    /** Theme attribute holding the colour a day of this category is drawn in. */
    @AttrRes
    public static int categoryColorAttr(@NonNull Occasion.Category category) {
        switch (category) {
            case OBLIGATORY: return com.google.android.material.R.attr.colorPrimary;
            case EMPHASISED: return com.google.android.material.R.attr.colorSecondary;
            case FORBIDDEN: return com.google.android.material.R.attr.colorError;
            case RECOMMENDED: default: return com.google.android.material.R.attr.colorTertiary;
        }
    }

    @DrawableRes
    public static int icon(@NonNull Occasion occasion) {
        switch (occasion) {
            case RAMADAN: return R.drawable.ic_lantern;
            case ARAFAH:
            case DHUL_HIJJAH_FIRST_NINE: return R.drawable.ic_mosque;
            case ASHURA:
            case TASUA:
            case MUHARRAM_9_10:
            case MUHARRAM: return R.drawable.ic_crescent;
            case SHAWWAL_SIX: return R.drawable.ic_fasting_plate;
            case WHITE_DAYS:
            case SHABAN_MID: return R.drawable.ic_moon_stars;
            case MONDAY_THURSDAY: return R.drawable.ic_repeat;
            default: return R.drawable.ic_close;
        }
    }

    /**
     * The fasting shortcuts offered on the calendar screen, most asked-for first.
     *
     * <p>The order is written out rather than taken from {@link Occasion#values()} so the list
     * reads the way people think of the year — Ramadan, then the Muharram fasts, then the rest —
     * instead of the order the enum happens to be declared in.</p>
     */
    @NonNull
    public static List<Occasion> schedulable() {
        List<Occasion> ordered = Arrays.asList(
                Occasion.RAMADAN,
                Occasion.MUHARRAM_9_10,
                Occasion.ASHURA,
                Occasion.TASUA,
                Occasion.ARAFAH,
                Occasion.WHITE_DAYS,
                Occasion.SHAWWAL_SIX,
                Occasion.DHUL_HIJJAH_FIRST_NINE,
                Occasion.SHABAN_MID,
                Occasion.MUHARRAM);

        List<Occasion> out = new ArrayList<>(ordered.size());
        for (Occasion occasion : ordered) {
            if (occasion.isSchedulable()) {
                out.add(occasion);
            }
        }
        // Anything added to the enum later still shows up, just at the end of the row.
        for (Occasion occasion : Occasion.values()) {
            if (occasion.isSchedulable() && !out.contains(occasion)) {
                out.add(occasion);
            }
        }
        return out;
    }
}
