package com.afzal.rozaalarm.alarm;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import com.afzal.rozaalarm.data.Alarm;
import com.afzal.rozaalarm.data.AlarmRepository;
import com.afzal.rozaalarm.ui.MainActivity;
import com.afzal.rozaalarm.util.Occurrences;

import java.util.List;

/**
 * Owns every interaction with {@link AlarmManager}.
 *
 * <p>Each alarm registers up to two pending intents: the alarm itself (scheduled with
 * {@code setAlarmClock} so the system treats it as a user-visible alarm and exempts it from Doze)
 * and its advance reminder.</p>
 */
public final class AlarmScheduler {

    private static final String TAG = "AlarmScheduler";

    private AlarmScheduler() {
    }

    /**
     * After an alarm fires we search from slightly in the future, so the occurrence that has just
     * rung can never be picked up again by rounding.
     */
    private static final long POST_FIRE_MARGIN_MILLIS = 60_000L;

    /** Registers (or re-registers) both the alarm and its reminder. */
    public static void schedule(@NonNull Context context, @NonNull Alarm alarm) {
        schedule(context, alarm, System.currentTimeMillis());
    }

    /** Re-registers an alarm that has just rung, skipping the occurrence that fired. */
    public static void scheduleAfterFiring(@NonNull Context context, @NonNull Alarm alarm) {
        schedule(context, alarm, System.currentTimeMillis() + POST_FIRE_MARGIN_MILLIS);
    }

    /** Registers (or re-registers) both the alarm and its reminder, searching from a given time. */
    public static void schedule(@NonNull Context context, @NonNull Alarm alarm, long fromMillis) {
        cancel(context, alarm);
        if (!alarm.enabled) {
            return;
        }

        long now = fromMillis;

        long nextAlarm = Occurrences.next(alarm, now);
        if (nextAlarm != Occurrences.NONE) {
            setExact(context, nextAlarm, alarmPendingIntent(context, alarm.id), true);
        }

        long nextReminder = Occurrences.nextReminder(alarm, now);
        if (nextReminder != Occurrences.NONE) {
            setExact(context, nextReminder, reminderPendingIntent(context, alarm.id), false);
        }
    }

    public static void cancel(@NonNull Context context, @NonNull Alarm alarm) {
        cancel(context, alarm.id);
    }

    public static void cancel(@NonNull Context context, long alarmId) {
        AlarmManager manager = context.getSystemService(AlarmManager.class);
        if (manager == null) {
            return;
        }
        manager.cancel(alarmPendingIntent(context, alarmId));
        manager.cancel(reminderPendingIntent(context, alarmId));
    }

    /** Re-arms an alarm a few minutes out after the user hit snooze. */
    public static void scheduleSnooze(@NonNull Context context, @NonNull Alarm alarm,
                                      long triggerAtMillis) {
        setExact(context, triggerAtMillis, alarmPendingIntent(context, alarm.id), true);
    }

    /** Rebuilds every registration — used after boot, a time zone change or an app update. */
    @WorkerThread
    public static void rescheduleAll(@NonNull Context context) {
        AlarmRepository repository = AlarmRepository.get(context);
        List<Alarm> alarms = repository.getAllSync();
        for (Alarm alarm : alarms) {
            if (alarm.enabled) {
                schedule(context, alarm);
            } else {
                cancel(context, alarm);
            }
        }
        Log.i(TAG, "Rescheduled " + alarms.size() + " alarm(s)");
    }

    /** Posts the reschedule onto the repository's background executor. */
    public static void rescheduleAllAsync(@NonNull Context context) {
        Context appContext = context.getApplicationContext();
        AlarmRepository.get(appContext).io().execute(() -> rescheduleAll(appContext));
    }

    /** The next time this alarm will ring, or {@link Occurrences#NONE}. */
    public static long nextTrigger(@NonNull Context context, @NonNull Alarm alarm) {
        return Occurrences.next(alarm, System.currentTimeMillis());
    }

    /** The next reminder instant, or {@link Occurrences#NONE}. */
    public static long nextReminder(@NonNull Context context, @NonNull Alarm alarm) {
        return Occurrences.nextReminder(alarm, System.currentTimeMillis());
    }

    /** False on Android 12+ when the user has revoked the "alarms &amp; reminders" permission. */
    public static boolean canScheduleExact(@NonNull Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return true;
        }
        AlarmManager manager = context.getSystemService(AlarmManager.class);
        return manager != null && manager.canScheduleExactAlarms();
    }

    // ---- internals ----------------------------------------------------------------------------

    private static void setExact(@NonNull Context context, long triggerAtMillis,
                                 @NonNull PendingIntent operation, boolean asAlarmClock) {
        AlarmManager manager = context.getSystemService(AlarmManager.class);
        if (manager == null) {
            return;
        }
        try {
            if (asAlarmClock && canScheduleExact(context)) {
                // setAlarmClock puts the alarm in the status bar and survives Doze.
                PendingIntent show = PendingIntent.getActivity(
                        context,
                        0,
                        new Intent(context, MainActivity.class)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                manager.setAlarmClock(
                        new AlarmManager.AlarmClockInfo(triggerAtMillis, show), operation);
            } else if (canScheduleExact(context)) {
                manager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP, triggerAtMillis, operation);
            } else {
                // Without the exact-alarm permission the best we can do is an inexact window;
                // the UI nudges the user to grant it.
                manager.setWindow(AlarmManager.RTC_WAKEUP, triggerAtMillis, 60_000L, operation);
            }
        } catch (SecurityException e) {
            Log.w(TAG, "Exact alarm refused, falling back to an inexact window", e);
            manager.setWindow(AlarmManager.RTC_WAKEUP, triggerAtMillis, 60_000L, operation);
        }
    }

    @NonNull
    private static PendingIntent alarmPendingIntent(@NonNull Context context, long alarmId) {
        return PendingIntent.getBroadcast(
                context,
                AlarmIntents.alarmRequestCode(alarmId),
                intentFor(context, AlarmIntents.ACTION_FIRE_ALARM, alarmId),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    @NonNull
    private static PendingIntent reminderPendingIntent(@NonNull Context context, long alarmId) {
        return PendingIntent.getBroadcast(
                context,
                AlarmIntents.reminderRequestCode(alarmId),
                intentFor(context, AlarmIntents.ACTION_FIRE_REMINDER, alarmId),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    @NonNull
    private static Intent intentFor(@NonNull Context context, @NonNull String action,
                                    long alarmId) {
        return new Intent(context, AlarmReceiver.class)
                .setAction(action)
                .putExtra(AlarmIntents.EXTRA_ALARM_ID, alarmId)
                // A distinct data URI keeps the alarm and reminder PendingIntents from being
                // treated as equal by the system.
                .setData(android.net.Uri.parse("rozaalarm://" + action + "/" + alarmId));
    }

}
