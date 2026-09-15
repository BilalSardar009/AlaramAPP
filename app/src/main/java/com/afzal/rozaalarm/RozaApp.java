package com.afzal.rozaalarm;

import android.app.Application;

import androidx.appcompat.app.AppCompatDelegate;

import com.afzal.rozaalarm.alarm.AlarmScheduler;
import com.afzal.rozaalarm.data.Alarm;
import com.afzal.rozaalarm.data.AlarmRepository;
import com.afzal.rozaalarm.data.HijriCorrections;
import com.afzal.rozaalarm.util.Notifications;
import com.afzal.rozaalarm.util.Occasion;
import com.afzal.rozaalarm.util.Prefs;

public class RozaApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        // One calm light theme, regardless of the system setting.
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);

        Notifications.createChannels(this);

        AlarmRepository repository = AlarmRepository.get(this);
        repository.io().execute(() -> {
            // Hijri rules resolve against the user's corrections, so load them first.
            HijriCorrections.get(this).reloadSync();

            if (!Prefs.isSeeded(this)) {
                seedWhiteDaysAlarm(repository);
                Prefs.setSeeded(this);
            }
            // Cheap safety net: makes sure the registrations match the database on every launch.
            AlarmScheduler.rescheduleAll(this);
        });
    }

    /**
     * First launch starts with the alarm this app exists for: Ayyam al-Beed, the 13th, 14th and
     * 15th of every Islamic month, with a reminder the evening before. It is stored as an occasion
     * rule, so it follows the Hijri calendar and never lands on a day fasting is not permitted.
     */
    private void seedWhiteDaysAlarm(AlarmRepository repository) {
        Alarm alarm = new Alarm();
        alarm.label = getString(R.string.seed_alarm_label);
        alarm.hour = 3;
        alarm.minute = 0;
        alarm.repeatMode = Alarm.REPEAT_OCCASION;
        alarm.occasionId = Occasion.WHITE_DAYS.id();
        alarm.calendarType = Alarm.CALENDAR_HIJRI;
        alarm.setMonthDayList(java.util.Arrays.asList(13, 14, 15));
        alarm.skipForbiddenDays = true;
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
