package com.afzal.rozaalarm.alarm;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.afzal.rozaalarm.data.Alarm;
import com.afzal.rozaalarm.util.Notifications;

/**
 * Foreground service that actually makes the noise.
 *
 * <p>It owns the ringtone, the vibration pattern and the wake lock, so the ringing survives the
 * user swiping the full screen activity away, and stops cleanly from the notification actions.</p>
 */
public class AlarmService extends Service {

    private static final String TAG = "AlarmService";

    /** Broadcast (app-internal) telling any open ring screen that the alarm is over. */
    public static final String ACTION_ALARM_STOPPED = "com.afzal.rozaalarm.action.ALARM_STOPPED";

    /** Alarms give up after ten minutes so a sleeping phone is not drained. */
    private static final long AUTO_STOP_MILLIS = 10L * 60L * 1000L;

    /** The tone fades in over half a minute instead of starting at full blast. */
    private static final long FADE_IN_MILLIS = 30_000L;
    private static final long FADE_STEP_MILLIS = 600L;
    private static final float FADE_START_VOLUME = 0.15f;

    private static final String EXTRA_LABEL = "extra_label";
    private static final String EXTRA_VIBRATE = "extra_vibrate";
    private static final String EXTRA_TONE = "extra_tone";
    private static final String EXTRA_SNOOZE = "extra_snooze";

    private static volatile long ringingAlarmId = -1L;

    private final Handler handler = new Handler(Looper.getMainLooper());

    @Nullable
    private MediaPlayer player;
    @Nullable
    private Vibrator vibrator;
    @Nullable
    private PowerManager.WakeLock wakeLock;
    @Nullable
    private Alarm ringingAlarm;

    private float currentVolume = FADE_START_VOLUME;

    private final Runnable fadeInStep = new Runnable() {
        @Override
        public void run() {
            if (player == null) {
                return;
            }
            currentVolume = Math.min(1f,
                    currentVolume + (1f - FADE_START_VOLUME) * FADE_STEP_MILLIS / FADE_IN_MILLIS);
            try {
                player.setVolume(currentVolume, currentVolume);
            } catch (IllegalStateException ignored) {
                return;
            }
            if (currentVolume < 1f) {
                handler.postDelayed(this, FADE_STEP_MILLIS);
            }
        }
    };

    private final Runnable autoStop = new Runnable() {
        @Override
        public void run() {
            if (ringingAlarm != null) {
                Notifications.showMissed(AlarmService.this, ringingAlarm);
            }
            stopEverything();
        }
    };

    // ---- public API ---------------------------------------------------------------------------

    /** Builds the intent used to start this service for a given alarm. */
    @NonNull
    public static Intent startIntent(@NonNull Context context, @NonNull Alarm alarm) {
        return new Intent(context, AlarmService.class)
                .putExtra(AlarmIntents.EXTRA_ALARM_ID, alarm.id)
                .putExtra(EXTRA_LABEL, alarm.label)
                .putExtra(EXTRA_VIBRATE, alarm.vibrate)
                .putExtra(EXTRA_TONE, alarm.toneUri)
                .putExtra(EXTRA_SNOOZE, alarm.snoozeMinutes);
    }

    public static void stop(@NonNull Context context) {
        Intent intent = new Intent(context, AlarmService.class)
                .setAction(AlarmIntents.ACTION_STOP_SERVICE);
        try {
            // Routed through onStartCommand so the service can clean up and announce the stop.
            context.startService(intent);
        } catch (IllegalStateException | SecurityException e) {
            // Background start restrictions can refuse that; killing the service outright still
            // silences the alarm because onDestroy releases the player, vibrator and wake lock.
            Log.w(TAG, "Could not deliver the stop command, stopping the service directly", e);
            context.stopService(intent);
        }
    }

    /** The id of the alarm currently ringing, or -1. */
    public static long ringingAlarmId() {
        return ringingAlarmId;
    }

    public static boolean isRinging() {
        return ringingAlarmId >= 0;
    }

    // ---- lifecycle ----------------------------------------------------------------------------

    @Override
    public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        if (intent == null || AlarmIntents.ACTION_STOP_SERVICE.equals(intent.getAction())) {
            stopEverything();
            return START_NOT_STICKY;
        }

        Alarm alarm = alarmFromIntent(intent);
        ringingAlarm = alarm;
        ringingAlarmId = alarm.id;

        // startForeground has to happen immediately, so every field the notification needs is
        // carried in the intent rather than read back from the database here.
        startForeground(AlarmIntents.ringingNotificationId(alarm.id),
                Notifications.buildRinging(this, alarm));

        acquireWakeLock();
        startPlayback(alarm);
        startVibration(alarm);

        handler.removeCallbacks(autoStop);
        handler.postDelayed(autoStop, AUTO_STOP_MILLIS);

        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        releasePlayer();
        stopVibration();
        releaseWakeLock();
        ringingAlarmId = -1L;
        ringingAlarm = null;
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // ---- internals ----------------------------------------------------------------------------

    @NonNull
    private Alarm alarmFromIntent(@NonNull Intent intent) {
        Alarm alarm = new Alarm();
        alarm.id = intent.getLongExtra(AlarmIntents.EXTRA_ALARM_ID, 0L);
        String label = intent.getStringExtra(EXTRA_LABEL);
        alarm.label = label == null ? "" : label;
        alarm.vibrate = intent.getBooleanExtra(EXTRA_VIBRATE, true);
        alarm.toneUri = intent.getStringExtra(EXTRA_TONE);
        alarm.snoozeMinutes = intent.getIntExtra(EXTRA_SNOOZE, 5);
        return alarm;
    }

    private void startPlayback(@NonNull Alarm alarm) {
        releasePlayer();
        Uri tone = resolveTone(alarm);
        if (tone == null) {
            return;
        }
        try {
            MediaPlayer mediaPlayer = new MediaPlayer();
            mediaPlayer.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build());
            mediaPlayer.setDataSource(this, tone);
            mediaPlayer.setLooping(true);
            mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                Log.w(TAG, "Alarm tone failed (" + what + "/" + extra + ")");
                return true;
            });
            mediaPlayer.prepare();
            currentVolume = FADE_START_VOLUME;
            mediaPlayer.setVolume(currentVolume, currentVolume);
            mediaPlayer.start();
            player = mediaPlayer;
            handler.postDelayed(fadeInStep, FADE_STEP_MILLIS);
            raiseAlarmStreamIfSilent();
        } catch (Exception e) {
            Log.e(TAG, "Unable to play the alarm tone", e);
            releasePlayer();
        }
    }

    @Nullable
    private Uri resolveTone(@NonNull Alarm alarm) {
        if (!TextUtils.isEmpty(alarm.toneUri)) {
            try {
                return Uri.parse(alarm.toneUri);
            } catch (Exception ignored) {
                // Fall through to the system default.
            }
        }
        Uri tone = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        if (tone == null) {
            tone = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
        }
        return tone;
    }

    /** An alarm nobody can hear is not an alarm; nudge the alarm stream up if it is muted. */
    private void raiseAlarmStreamIfSilent() {
        AudioManager audio = getSystemService(AudioManager.class);
        if (audio == null) {
            return;
        }
        try {
            if (audio.getStreamVolume(AudioManager.STREAM_ALARM) == 0) {
                int target = Math.max(1, audio.getStreamMaxVolume(AudioManager.STREAM_ALARM) / 2);
                audio.setStreamVolume(AudioManager.STREAM_ALARM, target, 0);
            }
        } catch (SecurityException ignored) {
            // Do Not Disturb policy can block this; the vibration still fires.
        }
    }

    private void startVibration(@NonNull Alarm alarm) {
        if (!alarm.vibrate) {
            return;
        }
        Vibrator device = obtainVibrator();
        if (device == null || !device.hasVibrator()) {
            return;
        }
        long[] pattern = {0L, 500L, 800L, 500L, 1600L};
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            device.vibrate(VibrationEffect.createWaveform(pattern, 0));
        } else {
            device.vibrate(pattern, 0);
        }
        vibrator = device;
    }

    @Nullable
    private Vibrator obtainVibrator() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            VibratorManager manager = getSystemService(VibratorManager.class);
            return manager == null ? null : manager.getDefaultVibrator();
        }
        return getSystemService(Vibrator.class);
    }

    private void stopVibration() {
        if (vibrator != null) {
            vibrator.cancel();
            vibrator = null;
        }
    }

    private void acquireWakeLock() {
        if (wakeLock != null) {
            return;
        }
        PowerManager power = getSystemService(PowerManager.class);
        if (power == null) {
            return;
        }
        wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "rozaalarm:ringing");
        wakeLock.setReferenceCounted(false);
        wakeLock.acquire(AUTO_STOP_MILLIS + 30_000L);
    }

    private void releaseWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
        wakeLock = null;
    }

    private void releasePlayer() {
        handler.removeCallbacks(fadeInStep);
        if (player != null) {
            try {
                player.stop();
            } catch (IllegalStateException ignored) {
                // Already stopped.
            }
            player.release();
            player = null;
        }
    }

    private void stopEverything() {
        long id = ringingAlarmId;
        releasePlayer();
        stopVibration();
        if (id >= 0) {
            Notifications.cancel(this, AlarmIntents.ringingNotificationId(id));
        }
        sendBroadcast(new Intent(ACTION_ALARM_STOPPED).setPackage(getPackageName()));
        stopForeground(Service.STOP_FOREGROUND_REMOVE);
        stopSelf();
    }
}
