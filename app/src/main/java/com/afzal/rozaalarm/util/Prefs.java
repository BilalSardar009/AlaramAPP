package com.afzal.rozaalarm.util;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

/** App wide settings, kept deliberately small and synchronous. */
public final class Prefs {

    public static final String FILE = "roza_prefs";

    /** Notification language modes. */
    public static final int LANG_BOTH = 0;
    public static final int LANG_ENGLISH = 1;
    public static final int LANG_URDU = 2;

    private static final String KEY_HIJRI_OFFSET = "hijri_offset";
    private static final String KEY_LANGUAGE = "notification_language";
    private static final String KEY_SEEDED = "seeded_defaults";
    private static final String KEY_BATTERY_PROMPTED = "battery_prompted";
    private static final String KEY_DEFAULT_SNOOZE = "default_snooze";

    private Prefs() {
    }

    @NonNull
    private static SharedPreferences prefs(@NonNull Context context) {
        return context.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    /**
     * Where the Hijri date starts before the user corrects it.
     *
     * <p>The calculation the app uses is Umm al-Qura, which is the Saudi civil calendar. Pakistan
     * goes by local moon sighting and has been running two days behind it, so the app would
     * otherwise open on the wrong date for the people it is built for. "Set today's date" on the
     * calendar screen replaces this with whatever their own calendar says.</p>
     */
    public static final int DEFAULT_HIJRI_OFFSET = -2;

    /** Days to shift the Hijri conversion by, to match the local moon sighting. */
    public static int hijriOffset(@NonNull Context context) {
        return prefs(context).getInt(KEY_HIJRI_OFFSET, DEFAULT_HIJRI_OFFSET);
    }

    public static void setHijriOffset(@NonNull Context context, int offsetDays) {
        prefs(context).edit()
                .putInt(KEY_HIJRI_OFFSET, clamp(offsetDays, -HijriDates.MAX_OFFSET,
                        HijriDates.MAX_OFFSET))
                .apply();
    }

    public static int notificationLanguage(@NonNull Context context) {
        return prefs(context).getInt(KEY_LANGUAGE, LANG_BOTH);
    }

    public static void setNotificationLanguage(@NonNull Context context, int mode) {
        prefs(context).edit().putInt(KEY_LANGUAGE, clamp(mode, LANG_BOTH, LANG_URDU)).apply();
    }

    public static boolean isSeeded(@NonNull Context context) {
        return prefs(context).getBoolean(KEY_SEEDED, false);
    }

    public static void setSeeded(@NonNull Context context) {
        prefs(context).edit().putBoolean(KEY_SEEDED, true).apply();
    }

    public static boolean wasBatteryPromptShown(@NonNull Context context) {
        return prefs(context).getBoolean(KEY_BATTERY_PROMPTED, false);
    }

    public static void setBatteryPromptShown(@NonNull Context context) {
        prefs(context).edit().putBoolean(KEY_BATTERY_PROMPTED, true).apply();
    }

    public static int defaultSnooze(@NonNull Context context) {
        return prefs(context).getInt(KEY_DEFAULT_SNOOZE, 5);
    }

    public static void setDefaultSnooze(@NonNull Context context, int minutes) {
        prefs(context).edit().putInt(KEY_DEFAULT_SNOOZE, clamp(minutes, 1, 60)).apply();
    }



    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
