package com.afzal.rozaalarm.util;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

/**
 * Builds the English / Urdu notification copy.
 *
 * <p>The app interface itself stays in English; only the things that wake a person up at three in
 * the morning are written in both languages, controlled by {@link Prefs#notificationLanguage}.</p>
 */
public final class Bilingual {

    private Bilingual() {
    }

    /** Short pieces (titles, buttons) joined on one line: {@code "Dismiss • بند کریں"}. */
    @NonNull
    public static String inline(@NonNull Context context, @StringRes int enRes,
                                @StringRes int urRes) {
        return inline(context, context.getString(enRes), context.getString(urRes));
    }

    @NonNull
    public static String inline(@NonNull Context context, @NonNull String english,
                                @NonNull String urdu) {
        switch (Prefs.notificationLanguage(context)) {
            case Prefs.LANG_ENGLISH:
                return english;
            case Prefs.LANG_URDU:
                return urdu;
            case Prefs.LANG_BOTH:
            default:
                return english + " • " + urdu;
        }
    }

    /** Longer copy (notification bodies) stacked on two lines. */
    @NonNull
    public static String stacked(@NonNull Context context, @NonNull String english,
                                 @NonNull String urdu) {
        switch (Prefs.notificationLanguage(context)) {
            case Prefs.LANG_ENGLISH:
                return english;
            case Prefs.LANG_URDU:
                return urdu;
            case Prefs.LANG_BOTH:
            default:
                return english + "\n" + urdu;
        }
    }

    @NonNull
    public static String stacked(@NonNull Context context, @StringRes int enRes,
                                 @StringRes int urRes) {
        return stacked(context, context.getString(enRes), context.getString(urRes));
    }

    public static boolean includesUrdu(@NonNull Context context) {
        return Prefs.notificationLanguage(context) != Prefs.LANG_ENGLISH;
    }

    public static boolean includesEnglish(@NonNull Context context) {
        return Prefs.notificationLanguage(context) != Prefs.LANG_URDU;
    }
}
