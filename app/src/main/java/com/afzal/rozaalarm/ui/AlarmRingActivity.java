package com.afzal.rozaalarm.ui;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;

import com.afzal.rozaalarm.R;
import com.afzal.rozaalarm.alarm.AlarmActionReceiver;
import com.afzal.rozaalarm.alarm.AlarmIntents;
import com.afzal.rozaalarm.alarm.AlarmService;
import com.afzal.rozaalarm.data.Alarm;
import com.afzal.rozaalarm.data.AlarmRepository;
import com.afzal.rozaalarm.databinding.ActivityAlarmRingBinding;
import com.afzal.rozaalarm.util.HijriDates;
import com.afzal.rozaalarm.util.Prefs;
import com.afzal.rozaalarm.util.TimeText;

/**
 * The full screen alarm. Shown over the lock screen through the notification's full screen intent,
 * it only presents the two choices that matter at 3am: snooze or dismiss.
 */
public class AlarmRingActivity extends AppCompatActivity {

    private static final long CLOCK_TICK_MS = 10_000L;

    private ActivityAlarmRingBinding binding;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private long alarmId = -1L;
    private int snoozeMinutes = 5;

    /** The service tells us when the alarm has stopped from somewhere else (notification, timeout). */
    private final BroadcastReceiver stoppedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            finishAndRemoveTask();
        }
    };

    private final Runnable clockTick = new Runnable() {
        @Override
        public void run() {
            updateClock();
            handler.postDelayed(this, CLOCK_TICK_MS);
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        showOverLockScreen();

        binding = ActivityAlarmRingBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        alarmId = getIntent().getLongExtra(AlarmIntents.EXTRA_ALARM_ID, AlarmService.ringingAlarmId());

        binding.ringMessageEnglish.setText(R.string.notif_alarm_body_en);
        binding.ringMessageUrdu.setText(R.string.notif_alarm_body_ur);
        binding.ringLabel.setText(R.string.ring_default_title);
        applyLanguageVisibility();

        updateClock();
        updateSnoozeLabel();
        loadAlarmDetails();
        runEntranceAnimation();

        binding.snoozeButton.setOnClickListener(v -> sendAction(AlarmIntents.ACTION_SNOOZE));
        binding.dismissButton.setOnClickListener(v -> sendAction(AlarmIntents.ACTION_DISMISS));

        registerStoppedReceiver();
        ignoreBackGesture();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        alarmId = intent.getLongExtra(AlarmIntents.EXTRA_ALARM_ID, alarmId);
        loadAlarmDetails();
    }

    @Override
    protected void onStart() {
        super.onStart();
        handler.removeCallbacks(clockTick);
        handler.postDelayed(clockTick, CLOCK_TICK_MS);
    }

    @Override
    protected void onStop() {
        handler.removeCallbacks(clockTick);
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        try {
            unregisterReceiver(stoppedReceiver);
        } catch (IllegalArgumentException ignored) {
            // Already unregistered.
        }
        super.onDestroy();
    }

    // ---- setup --------------------------------------------------------------------------------

    /**
     * Swallows back (button and predictive gesture alike) so the only ways out of a ringing alarm
     * are Snooze and Dismiss.
     */
    private void ignoreBackGesture() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                // Intentionally does nothing.
            }
        });
    }

    private void showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private void registerStoppedReceiver() {
        IntentFilter filter = new IntentFilter(AlarmService.ACTION_ALARM_STOPPED);
        ContextCompat.registerReceiver(this, stoppedReceiver, filter,
                ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    private void loadAlarmDetails() {
        if (alarmId < 0) {
            return;
        }
        AlarmRepository repository = AlarmRepository.get(this);
        repository.io().execute(() -> {
            Alarm alarm = repository.getByIdSync(alarmId);
            if (alarm == null) {
                return;
            }
            runOnUiThread(() -> {
                snoozeMinutes = Math.max(1, alarm.snoozeMinutes);
                updateSnoozeLabel();
                if (!alarm.label.trim().isEmpty()) {
                    binding.ringLabel.setText(alarm.label);
                }
            });
        });
    }

    private void applyLanguageVisibility() {
        int language = Prefs.notificationLanguage(this);
        binding.ringMessageEnglish.setVisibility(
                language == Prefs.LANG_URDU ? View.GONE : View.VISIBLE);
        binding.ringMessageUrdu.setVisibility(
                language == Prefs.LANG_ENGLISH ? View.GONE : View.VISIBLE);
    }

    // ---- display ------------------------------------------------------------------------------

    private void updateClock() {
        long now = System.currentTimeMillis();
        binding.ringClock.setText(TimeText.time(this, now));
        binding.ringDate.setText(TimeText.fullDate(this, now));
        int offset = Prefs.hijriOffset(this);
        binding.ringHijri.setText(HijriDates.formatEnglish(now, offset)
                + "  ·  " + HijriDates.formatUrdu(now, offset));
    }

    private void updateSnoozeLabel() {
        binding.snoozeButton.setText(getString(R.string.ring_snooze, snoozeMinutes));
    }

    private void runEntranceAnimation() {
        if (!Prefs.richAnimations(this)) {
            return;
        }
        binding.ringLogo.setScaleX(0.7f);
        binding.ringLogo.setScaleY(0.7f);
        binding.ringLogo.setAlpha(0f);
        binding.ringLogo.animate().alpha(1f).scaleX(1f).scaleY(1f)
                .setDuration(700L).setInterpolator(new DecelerateInterpolator()).start();

        View[] risers = {binding.ringLabel, binding.ringMessageEnglish, binding.ringMessageUrdu,
                binding.snoozeButton, binding.dismissButton};
        long delay = 180L;
        for (View view : risers) {
            view.setAlpha(0f);
            view.setTranslationY(26f);
            view.animate().alpha(1f).translationY(0f)
                    .setStartDelay(delay).setDuration(520L)
                    .setInterpolator(new AccelerateDecelerateInterpolator()).start();
            delay += 90L;
        }
    }

    // ---- actions ------------------------------------------------------------------------------

    private void sendAction(String action) {
        sendBroadcast(new Intent(this, AlarmActionReceiver.class)
                .setAction(action)
                .putExtra(AlarmIntents.EXTRA_ALARM_ID, alarmId));
        binding.snoozeButton.setEnabled(false);
        binding.dismissButton.setEnabled(false);
        // The service broadcasts ALARM_STOPPED, but close promptly either way.
        handler.postDelayed(this::finishAndRemoveTask, 320L);
    }
}
