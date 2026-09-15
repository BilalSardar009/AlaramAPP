package com.afzal.rozaalarm.alarm;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.afzal.rozaalarm.data.Alarm;
import com.afzal.rozaalarm.data.AlarmRepository;
import com.afzal.rozaalarm.util.Notifications;

/** Handles the Snooze / Dismiss buttons, from both the notification and the ring screen. */
public class AlarmActionReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) {
            return;
        }
        final String action = intent.getAction();
        final long alarmId = intent.getLongExtra(AlarmIntents.EXTRA_ALARM_ID, -1L);
        final Context appContext = context.getApplicationContext();

        // Stop the noise straight away — the database work can finish afterwards.
        AlarmService.stop(appContext);
        if (alarmId >= 0) {
            Notifications.cancel(appContext, AlarmIntents.ringingNotificationId(alarmId));
        }

        if (alarmId < 0) {
            return;
        }

        final PendingResult pendingResult = goAsync();
        AlarmRepository repository = AlarmRepository.get(appContext);
        repository.io().execute(() -> {
            try {
                Alarm alarm = repository.getByIdSync(alarmId);
                if (alarm == null) {
                    return;
                }
                if (AlarmIntents.ACTION_SNOOZE.equals(action)) {
                    long triggerAt = System.currentTimeMillis()
                            + Math.max(1, alarm.snoozeMinutes) * 60_000L;
                    AlarmScheduler.scheduleSnooze(appContext, alarm, triggerAt);
                    Notifications.showSnoozed(appContext, alarm, triggerAt);
                } else if (AlarmIntents.ACTION_DISMISS.equals(action)) {
                    // The next occurrence was already registered when the alarm fired; re-running
                    // the scheduler keeps things correct if the user edited the alarm meanwhile.
                    AlarmScheduler.scheduleAfterFiring(appContext, alarm);
                }
            } finally {
                pendingResult.finish();
            }
        });
    }
}
