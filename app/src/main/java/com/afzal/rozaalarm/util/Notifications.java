package com.afzal.rozaalarm.util;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.afzal.rozaalarm.R;
import com.afzal.rozaalarm.alarm.AlarmActionReceiver;
import com.afzal.rozaalarm.alarm.AlarmIntents;
import com.afzal.rozaalarm.data.Alarm;
import com.afzal.rozaalarm.ui.AlarmRingActivity;
import com.afzal.rozaalarm.ui.MainActivity;

/** Creates the channels and builds every notification the app posts, in English and Urdu. */
public final class Notifications {

    public static final String CHANNEL_ALARM = "roza_alarm_channel";
    public static final String CHANNEL_REMINDER = "roza_reminder_channel";

    private Notifications() {
    }

    public static void createChannels(@NonNull Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) {
            return;
        }

        // The alarm channel stays silent: the foreground service owns the ringtone so it can be
        // stopped, snoozed and looped independently of the notification.
        NotificationChannel alarmChannel = new NotificationChannel(
                CHANNEL_ALARM,
                context.getString(R.string.channel_alarm_name),
                NotificationManager.IMPORTANCE_HIGH);
        alarmChannel.setDescription(context.getString(R.string.channel_alarm_desc));
        alarmChannel.setSound(null, null);
        alarmChannel.enableVibration(false);
        alarmChannel.setBypassDnd(true);
        alarmChannel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        alarmChannel.setShowBadge(true);

        NotificationChannel reminderChannel = new NotificationChannel(
                CHANNEL_REMINDER,
                context.getString(R.string.channel_reminder_name),
                NotificationManager.IMPORTANCE_HIGH);
        reminderChannel.setDescription(context.getString(R.string.channel_reminder_desc));
        reminderChannel.enableVibration(true);
        reminderChannel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);

        manager.createNotificationChannel(alarmChannel);
        manager.createNotificationChannel(reminderChannel);
    }

    // ---- ringing ------------------------------------------------------------------------------

    /** The full screen notification shown while an alarm is actually ringing. */
    @NonNull
    public static Notification buildRinging(@NonNull Context context, @NonNull Alarm alarm) {
        Intent fullScreen = new Intent(context, AlarmRingActivity.class)
                .putExtra(AlarmIntents.EXTRA_ALARM_ID, alarm.id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent fullScreenIntent = PendingIntent.getActivity(
                context, AlarmIntents.fullScreenRequestCode(alarm.id), fullScreen, immutable());

        PendingIntent snooze = PendingIntent.getBroadcast(
                context,
                AlarmIntents.snoozeRequestCode(alarm.id),
                new Intent(context, AlarmActionReceiver.class)
                        .setAction(AlarmIntents.ACTION_SNOOZE)
                        .putExtra(AlarmIntents.EXTRA_ALARM_ID, alarm.id),
                immutable());

        PendingIntent dismiss = PendingIntent.getBroadcast(
                context,
                AlarmIntents.dismissRequestCode(alarm.id),
                new Intent(context, AlarmActionReceiver.class)
                        .setAction(AlarmIntents.ACTION_DISMISS)
                        .putExtra(AlarmIntents.EXTRA_ALARM_ID, alarm.id),
                immutable());

        String title = alarm.label.trim().isEmpty()
                ? Bilingual.inline(context, R.string.notif_alarm_title_en, R.string.notif_alarm_title_ur)
                : alarm.label;
        String body = Bilingual.stacked(context,
                R.string.notif_alarm_body_en, R.string.notif_alarm_body_ur);

        return new NotificationCompat.Builder(context, CHANNEL_ALARM)
                .setSmallIcon(R.drawable.ic_notification_alarm)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setColor(ContextCompat.getColor(context, R.color.brand_gold))
                .setColorized(true)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setAutoCancel(false)
                .setOnlyAlertOnce(true)
                .setFullScreenIntent(fullScreenIntent, true)
                .setContentIntent(fullScreenIntent)
                .addAction(R.drawable.ic_snooze,
                        Bilingual.inline(context, R.string.notif_action_snooze_en,
                                R.string.notif_action_snooze_ur), snooze)
                .addAction(R.drawable.ic_close,
                        Bilingual.inline(context, R.string.notif_action_dismiss_en,
                                R.string.notif_action_dismiss_ur), dismiss)
                .build();
    }

    // ---- advance reminder ---------------------------------------------------------------------

    /** The "your fast is tomorrow" heads-up, posted the evening before. */
    public static void showReminder(@NonNull Context context, @NonNull Alarm alarm,
                                    long occurrenceMillis) {
        String label = alarm.label.trim().isEmpty()
                ? context.getString(R.string.default_alarm_label)
                : alarm.label;
        String timeText = TimeText.time(context, occurrenceMillis);
        int daysAway = (int) Math.max(1, Occurrences.daysBetween(
                System.currentTimeMillis(), occurrenceMillis) + 1);

        final String titleEn;
        final String titleUr;
        final String bodyEn;
        final String bodyUr;
        if (alarm.preReminderDaysBefore <= 1 || daysAway <= 1) {
            titleEn = context.getString(R.string.notif_reminder_title_en);
            titleUr = context.getString(R.string.notif_reminder_title_ur);
            bodyEn = context.getString(R.string.notif_reminder_body_en, label, timeText);
            bodyUr = context.getString(R.string.notif_reminder_body_ur, label, timeText);
        } else {
            titleEn = context.getString(R.string.notif_reminder_title_days_en);
            titleUr = context.getString(R.string.notif_reminder_title_days_ur);
            bodyEn = context.getString(R.string.notif_reminder_body_days_en, label, daysAway, timeText);
            bodyUr = context.getString(R.string.notif_reminder_body_days_ur, label, daysAway, timeText);
        }

        int offset = Prefs.hijriOffset(context);
        String hijriEn = context.getString(R.string.notif_reminder_hijri_en,
                HijriDates.formatEnglish(occurrenceMillis, offset));
        String hijriUr = context.getString(R.string.notif_reminder_hijri_ur,
                HijriDates.formatUrdu(occurrenceMillis, offset));

        String body = Bilingual.stacked(context, bodyEn, bodyUr)
                + "\n" + Bilingual.stacked(context, hijriEn, hijriUr);

        PendingIntent content = PendingIntent.getActivity(
                context,
                AlarmIntents.reminderContentRequestCode(alarm.id),
                new Intent(context, MainActivity.class)
                        .putExtra(AlarmIntents.EXTRA_ALARM_ID, alarm.id)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP),
                immutable());

        Notification notification = new NotificationCompat.Builder(context, CHANNEL_REMINDER)
                .setSmallIcon(R.drawable.ic_notification_moon)
                .setContentTitle(Bilingual.inline(context, titleEn, titleUr))
                .setContentText(Bilingual.stacked(context, bodyEn, bodyUr))
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setColor(ContextCompat.getColor(context, R.color.brand_green))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setContentIntent(content)
                .setAutoCancel(true)
                .build();

        post(context, AlarmIntents.reminderNotificationId(alarm.id), notification);
    }

    // ---- snoozed / missed ---------------------------------------------------------------------

    public static void showSnoozed(@NonNull Context context, @NonNull Alarm alarm,
                                   long nextRingMillis) {
        String timeText = TimeText.time(context, nextRingMillis);
        Notification notification = new NotificationCompat.Builder(context, CHANNEL_REMINDER)
                .setSmallIcon(R.drawable.ic_snooze)
                .setContentTitle(Bilingual.inline(context, R.string.notif_snoozed_title_en,
                        R.string.notif_snoozed_title_ur))
                .setContentText(Bilingual.stacked(context,
                        context.getString(R.string.notif_snoozed_body_en, timeText),
                        context.getString(R.string.notif_snoozed_body_ur, timeText)))
                .setColor(ContextCompat.getColor(context, R.color.brand_gold))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setTimeoutAfter(Math.max(60_000L, nextRingMillis - System.currentTimeMillis()))
                .build();
        post(context, AlarmIntents.snoozedNotificationId(alarm.id), notification);
    }

    public static void showMissed(@NonNull Context context, @NonNull Alarm alarm) {
        String label = alarm.label.trim().isEmpty()
                ? context.getString(R.string.default_alarm_label)
                : alarm.label;
        Notification notification = new NotificationCompat.Builder(context, CHANNEL_REMINDER)
                .setSmallIcon(R.drawable.ic_notification_alarm)
                .setContentTitle(Bilingual.inline(context, R.string.notif_missed_title_en,
                        R.string.notif_missed_title_ur))
                .setContentText(Bilingual.stacked(context,
                        context.getString(R.string.notif_missed_body_en, label),
                        context.getString(R.string.notif_missed_body_ur, label)))
                .setColor(ContextCompat.getColor(context, R.color.brand_gold))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .build();
        post(context, AlarmIntents.missedNotificationId(alarm.id), notification);
    }

    // ---- plumbing -----------------------------------------------------------------------------

    public static void cancel(@NonNull Context context, int notificationId) {
        NotificationManagerCompat.from(context).cancel(notificationId);
    }

    private static void post(@NonNull Context context, int id, @NonNull Notification notification) {
        try {
            NotificationManagerCompat.from(context).notify(id, notification);
        } catch (SecurityException ignored) {
            // POST_NOTIFICATIONS was revoked between scheduling and firing; the full screen
            // activity still runs, so there is nothing to recover here.
        }
    }

    private static int immutable() {
        return PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
    }
}
