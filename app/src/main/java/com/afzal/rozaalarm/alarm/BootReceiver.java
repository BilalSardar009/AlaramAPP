package com.afzal.rozaalarm.alarm;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.afzal.rozaalarm.util.Notifications;

/**
 * Alarm registrations do not survive a reboot, an app update or a clock change, so rebuild them
 * whenever the system tells us something moved.
 */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) {
            return;
        }
        switch (intent.getAction()) {
            case Intent.ACTION_BOOT_COMPLETED:
            case Intent.ACTION_LOCKED_BOOT_COMPLETED:
            case Intent.ACTION_MY_PACKAGE_REPLACED:
            case Intent.ACTION_TIME_CHANGED:
            case Intent.ACTION_TIMEZONE_CHANGED:
                Context appContext = context.getApplicationContext();
                Notifications.createChannels(appContext);
                AlarmScheduler.rescheduleAllAsync(appContext);
                break;
            default:
                break;
        }
    }
}
