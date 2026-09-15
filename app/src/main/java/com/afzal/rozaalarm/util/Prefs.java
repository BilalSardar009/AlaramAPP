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
    private static final String KEY_ANIMATIONS = "rich_animations";

    private Prefs() {
    }

    @NonNull
    private static SharedPreferences prefs(@NonNull Context context) {
        return context.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    /** Days to shift the Hijri conversion by, to match the local moon sighting (-2 … +2). */
    public static int hijriOffset(@NonNull Context context) {
        return prefs(context).getInt(KEY_HIJRI_OFFSET, 0);
    }

    public static void setHijriOffset(@NonNull Context context, int offsetDays) {
        prefs(context).edit().putInt(KEY_HIJRI_OFFSET, clamp(offsetDays, -2, 2)).apply();
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

    public static boolean richAnimations(@NonNull Context context) {
        return prefs(context).getBoolean(KEY_ANIMATIONS, true);
    }

    public static void setRichAnimations(@NonNull Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_ANIMATIONS, enabled).apply();
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
