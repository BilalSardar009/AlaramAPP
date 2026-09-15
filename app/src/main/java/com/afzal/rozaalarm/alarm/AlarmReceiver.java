package com.afzal.rozaalarm.alarm;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.core.content.ContextCompat;

import com.afzal.rozaalarm.data.Alarm;
import com.afzal.rozaalarm.data.AlarmRepository;
import com.afzal.rozaalarm.util.Notifications;
import com.afzal.rozaalarm.util.Occurrences;
import com.afzal.rozaalarm.util.Prefs;

/** Entry point for both the alarm itself and its advance reminder. */
public class AlarmReceiver extends BroadcastReceiver {

    private static final String TAG = "AlarmReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) {
            return;
        }
        final String action = intent.getAction();
        final long alarmId = intent.getLongExtra(AlarmIntents.EXTRA_ALARM_ID, -1L);
        if (alarmId < 0) {
            return;
        }

        final Context appContext = context.getApplicationContext();
        final PendingResult pendingResult = goAsync();

        AlarmRepository repository = AlarmRepository.get(appContext);
        repository.io().execute(() -> {
            try {
                Alarm alarm = repository.getByIdSync(alarmId);
                if (alarm == null || !alarm.enabled) {
                    return;
                }
                if (AlarmIntents.ACTION_FIRE_ALARM.equals(action)) {
                    fireAlarm(appContext, repository, alarm);
                } else if (AlarmIntents.ACTION_FIRE_REMINDER.equals(action)) {
                    fireReminder(appContext, alarm);
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to handle " + action, e);
            } finally {
                pendingResult.finish();
            }
        });
    }

    private void fireAlarm(Context context, AlarmRepository repository, Alarm alarm) {
        alarm.lastTriggeredAt = System.currentTimeMillis();

        if (alarm.repeatMode == Alarm.REPEAT_ONCE) {
            // A one-off alarm has done its job; keep the row so the user can re-enable it.
            alarm.enabled = false;
        }
        repository.updateSync(alarm);

        ContextCompat.startForegroundService(context, AlarmService.startIntent(context, alarm));

        if (alarm.enabled) {
            AlarmScheduler.scheduleAfterFiring(context, alarm);
        } else {
            AlarmScheduler.cancel(context, alarm);
        }
    }

    private void fireReminder(Context context, Alarm alarm) {
        long occurrence = Occurrences.next(
                alarm, System.currentTimeMillis(), Prefs.hijriOffset(context));
        if (occurrence != Occurrences.NONE) {
            Notifications.showReminder(context, alarm, occurrence);
        }
        // Register the reminder for the following occurrence.
        AlarmScheduler.schedule(context, alarm);
    }
}
