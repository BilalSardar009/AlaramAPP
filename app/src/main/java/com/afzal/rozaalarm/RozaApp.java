package com.afzal.rozaalarm;

import android.app.Application;

import androidx.appcompat.app.AppCompatDelegate;

import com.afzal.rozaalarm.alarm.AlarmScheduler;
import com.afzal.rozaalarm.data.Alarm;
import com.afzal.rozaalarm.data.AlarmRepository;
import com.afzal.rozaalarm.util.Notifications;
import com.afzal.rozaalarm.util.Prefs;

public class RozaApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        // The palette is designed for both schemes; follow whatever the phone is set to.
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);

        Notifications.createChannels(this);

        AlarmRepository repository = AlarmRepository.get(this);
        repository.io().execute(() -> {
            if (!Prefs.isSeeded(this)) {
                seedWhiteDaysAlarm(repository);
                Prefs.setSeeded(this);
            }
            // Cheap safety net: makes sure the registrations match the database on every launch.
            AlarmScheduler.rescheduleAll(this);
        });
    }

    /**
     * First launch starts with the alarm this app exists for: the White Days (13th, 14th and 15th)
     * of every month, with a reminder the evening before.
     */
    private void seedWhiteDaysAlarm(AlarmRepository repository) {
        Alarm alarm = new Alarm();
        alarm.label = getString(R.string.seed_alarm_label);
        alarm.hour = 3;
        alarm.minute = 0;
        alarm.repeatMode = Alarm.REPEAT_MONTHLY;
        alarm.calendarType = Alarm.CALENDAR_GREGORIAN;
        alarm.setMonthDayList(java.util.Arrays.asList(13, 14, 15));
        alarm.enabled = true;
        alarm.vibrate = true;
        alarm.snoozeMinutes = 5;
        alarm.preReminderEnabled = true;
        alarm.preReminderDaysBefore = 1;
        alarm.preReminderHour = 20;
        alarm.preReminderMinute = 0;
        alarm.preReminderFirstDayOnly = true;

        repository.insertSync(alarm);
        AlarmScheduler.schedule(this, alarm);
    }
}
