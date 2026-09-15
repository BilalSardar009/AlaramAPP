package com.afzal.rozaalarm.alarm;

/** Intent actions, extras and PendingIntent request codes used across the alarm plumbing. */
public final class AlarmIntents {

    public static final String ACTION_FIRE_ALARM = "com.afzal.rozaalarm.action.FIRE_ALARM";
    public static final String ACTION_FIRE_REMINDER = "com.afzal.rozaalarm.action.FIRE_REMINDER";
    public static final String ACTION_SNOOZE = "com.afzal.rozaalarm.action.SNOOZE";
    public static final String ACTION_DISMISS = "com.afzal.rozaalarm.action.DISMISS";
    public static final String ACTION_STOP_SERVICE = "com.afzal.rozaalarm.action.STOP_SERVICE";

    public static final String EXTRA_ALARM_ID = "com.afzal.rozaalarm.extra.ALARM_ID";
    public static final String EXTRA_OCCURRENCE = "com.afzal.rozaalarm.extra.OCCURRENCE";

    // Each alarm owns a small block of request codes so its PendingIntents never collide.
    private static final int BLOCK = 10;
    private static final int CODE_ALARM = 1;
    private static final int CODE_REMINDER = 2;
    private static final int CODE_CONTENT = 3;
    private static final int CODE_FULL_SCREEN = 4;
    private static final int CODE_SNOOZE = 5;
    private static final int CODE_DISMISS = 6;
    private static final int CODE_REMINDER_CONTENT = 7;

    // Notification id bases, kept far apart from the request codes.
    private static final int NOTIF_RINGING = 100_000;
    private static final int NOTIF_REMINDER = 200_000;
    private static final int NOTIF_SNOOZED = 300_000;
    private static final int NOTIF_MISSED = 400_000;

    private AlarmIntents() {
    }

    public static int alarmRequestCode(long alarmId) {
        return requestCode(alarmId, CODE_ALARM);
    }

    public static int reminderRequestCode(long alarmId) {
        return requestCode(alarmId, CODE_REMINDER);
    }

    public static int contentRequestCode(long alarmId) {
        return requestCode(alarmId, CODE_CONTENT);
    }

    public static int fullScreenRequestCode(long alarmId) {
        return requestCode(alarmId, CODE_FULL_SCREEN);
    }

    public static int snoozeRequestCode(long alarmId) {
        return requestCode(alarmId, CODE_SNOOZE);
    }

    public static int dismissRequestCode(long alarmId) {
        return requestCode(alarmId, CODE_DISMISS);
    }

    public static int reminderContentRequestCode(long alarmId) {
        return requestCode(alarmId, CODE_REMINDER_CONTENT);
    }

    public static int ringingNotificationId(long alarmId) {
        return (int) (NOTIF_RINGING + (alarmId % 50_000));
    }

    public static int reminderNotificationId(long alarmId) {
        return (int) (NOTIF_REMINDER + (alarmId % 50_000));
    }

    public static int snoozedNotificationId(long alarmId) {
        return (int) (NOTIF_SNOOZED + (alarmId % 50_000));
    }

    public static int missedNotificationId(long alarmId) {
        return (int) (NOTIF_MISSED + (alarmId % 50_000));
    }

    private static int requestCode(long alarmId, int slot) {
        return (int) ((alarmId % 100_000L) * BLOCK + slot);
    }
}
