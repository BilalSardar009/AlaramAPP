package com.afzal.rozaalarm.ui;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.afzal.rozaalarm.R;
import com.afzal.rozaalarm.alarm.AlarmScheduler;
import com.afzal.rozaalarm.data.Alarm;
import com.afzal.rozaalarm.data.AlarmRepository;
import com.afzal.rozaalarm.databinding.ActivityMainBinding;
import com.afzal.rozaalarm.ui.adapter.AlarmAdapter;
import com.afzal.rozaalarm.util.HijriDates;
import com.afzal.rozaalarm.util.Occurrences;
import com.afzal.rozaalarm.util.Prefs;
import com.afzal.rozaalarm.util.TimeText;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.List;

/** Home screen: the hero header with today's dates plus the list of alarms. */
public class MainActivity extends AppCompatActivity implements AlarmAdapter.Listener {

    private static final long TICK_INTERVAL_MS = 30_000L;

    private ActivityMainBinding binding;
    private AlarmAdapter adapter;
    private AlarmRepository repository;

    private final Handler ticker = new Handler(Looper.getMainLooper());
    private final List<Alarm> currentAlarms = new ArrayList<>();
    private boolean firstListLoad = true;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            updateHero();
            ticker.postDelayed(this, TICK_INTERVAL_MS);
        }
    };

    private final ActivityResultLauncher<String> notificationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(),
                    granted -> maybePromptForExactAlarms());

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        repository = AlarmRepository.get(this);

        binding.toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_settings) {
                startActivity(new Intent(this, SettingsActivity.class));
                overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
                return true;
            }
            return false;
        });

        adapter = new AlarmAdapter(this);
        binding.alarmList.setLayoutManager(new LinearLayoutManager(this));
        binding.alarmList.setAdapter(adapter);
        binding.alarmList.setHasFixedSize(false);
        attachSwipeToDelete();
        keepFabOutOfTheWay();

        binding.addAlarmFab.setOnClickListener(v -> openEditor(0L));
        binding.nextAlarmCard.setOnClickListener(v -> scrollToNextAlarm());

        repository.observeAll().observe(this, alarms -> {
            currentAlarms.clear();
            currentAlarms.addAll(alarms);
            adapter.submitList(new ArrayList<>(alarms));
            binding.emptyState.setVisibility(alarms.isEmpty() ? View.VISIBLE : View.GONE);
            binding.alarmList.setVisibility(alarms.isEmpty() ? View.GONE : View.VISIBLE);
            if (firstListLoad && !alarms.isEmpty()) {
                firstListLoad = false;
                binding.alarmList.scheduleLayoutAnimation();
            }
            updateHero();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateHero();
        ticker.removeCallbacks(tick);
        ticker.postDelayed(tick, TICK_INTERVAL_MS);
        requestPermissionsIfNeeded();
    }

    @Override
    protected void onPause() {
        ticker.removeCallbacks(tick);
        super.onPause();
    }

    // ---- hero ---------------------------------------------------------------------------------

    private void updateHero() {
        long now = System.currentTimeMillis();
        int hijriOffset = Prefs.hijriOffset(this);

        binding.todayDate.setText(TimeText.fullDate(this, now));
        binding.todayHijri.setText(HijriDates.formatEnglish(now, hijriOffset)
                + "   ·   " + HijriDates.formatUrdu(now, hijriOffset));

        Alarm soonest = null;
        long soonestTime = Long.MAX_VALUE;
        for (Alarm alarm : currentAlarms) {
            if (!alarm.enabled) {
                continue;
            }
            long next = Occurrences.next(alarm, now, hijriOffset);
            if (next != Occurrences.NONE && next < soonestTime) {
                soonestTime = next;
                soonest = alarm;
            }
        }

        if (soonest == null) {
            binding.nextAlarmLabel.setText(R.string.next_alarm_none);
            binding.nextAlarmCountdown.setText(R.string.next_alarm_none_hint);
        } else {
            String label = soonest.label.trim().isEmpty()
                    ? getString(R.string.default_alarm_label)
                    : soonest.label;
            binding.nextAlarmLabel.setText(label + " · " + TimeText.dateTime(this, soonestTime));
            binding.nextAlarmCountdown.setText(TimeText.countdown(this, soonestTime, now));
        }
    }

    private void scrollToNextAlarm() {
        long now = System.currentTimeMillis();
        int hijriOffset = Prefs.hijriOffset(this);
        int bestIndex = -1;
        long bestTime = Long.MAX_VALUE;
        for (int i = 0; i < currentAlarms.size(); i++) {
            Alarm alarm = currentAlarms.get(i);
            if (!alarm.enabled) {
                continue;
            }
            long next = Occurrences.next(alarm, now, hijriOffset);
            if (next != Occurrences.NONE && next < bestTime) {
                bestTime = next;
                bestIndex = i;
            }
        }
        if (bestIndex >= 0) {
            binding.appBar.setExpanded(false, true);
            binding.alarmList.smoothScrollToPosition(bestIndex);
        }
    }

    // ---- list interaction ---------------------------------------------------------------------

    @Override
    public void onAlarmClicked(@NonNull Alarm alarm) {
        openEditor(alarm.id);
    }

    @Override
    public void onAlarmToggled(@NonNull Alarm alarm, boolean enabled) {
        repository.setEnabled(alarm.id, enabled, id -> runOnUiThread(this::updateHero));
    }

    @Override
    public void onAlarmLongClicked(@NonNull Alarm alarm) {
        String[] options = {
                getString(R.string.action_edit),
                getString(R.string.action_duplicate),
                getString(R.string.action_delete)
        };
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.alarm_options_title)
                .setItems(options, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            openEditor(alarm.id);
                            break;
                        case 1:
                            duplicate(alarm);
                            break;
                        case 2:
                            deleteWithUndo(alarm);
                            break;
                        default:
                            break;
                    }
                })
                .show();
    }

    private void duplicate(@NonNull Alarm alarm) {
        Alarm copy = Alarm.copyOf(alarm);
        copy.label = getString(R.string.duplicate_suffix, alarm.label.trim().isEmpty()
                ? getString(R.string.default_alarm_label) : alarm.label);
        repository.save(copy, null);
    }

    private void deleteWithUndo(@NonNull Alarm alarm) {
        repository.delete(alarm, null);
        Snackbar.make(binding.mainRoot, R.string.alarm_deleted, Snackbar.LENGTH_LONG)
                .setAnchorView(binding.addAlarmFab)
                .setAction(R.string.action_undo, v -> repository.save(Alarm.copyOf(alarm), null))
                .show();
    }

    private void openEditor(long alarmId) {
        startActivity(new Intent(this, AlarmEditActivity.class)
                .putExtra(AlarmEditActivity.EXTRA_ALARM_ID, alarmId));
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
    }

    private void attachSwipeToDelete() {
        ItemTouchHelper helper = new ItemTouchHelper(
                new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.START | ItemTouchHelper.END) {
                    @Override
                    public boolean onMove(@NonNull RecyclerView recyclerView,
                                          @NonNull RecyclerView.ViewHolder viewHolder,
                                          @NonNull RecyclerView.ViewHolder target) {
                        return false;
                    }

                    @Override
                    public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder,
                                         int direction) {
                        int position = viewHolder.getBindingAdapterPosition();
                        if (position != RecyclerView.NO_POSITION) {
                            deleteWithUndo(adapter.alarmAt(position));
                        }
                    }

                    @Override
                    public void onChildDraw(@NonNull Canvas canvas,
                                            @NonNull RecyclerView recyclerView,
                                            @NonNull RecyclerView.ViewHolder viewHolder,
                                            float dX, float dY, int actionState,
                                            boolean isCurrentlyActive) {
                        // Fade the card out as it slides away instead of leaving a hard edge.
                        float width = Math.max(1f, viewHolder.itemView.getWidth());
                        viewHolder.itemView.setAlpha(1f - Math.min(1f, Math.abs(dX) / width));
                        super.onChildDraw(canvas, recyclerView, viewHolder, dX, dY, actionState,
                                isCurrentlyActive);
                    }

                    @Override
                    public void clearView(@NonNull RecyclerView recyclerView,
                                          @NonNull RecyclerView.ViewHolder viewHolder) {
                        viewHolder.itemView.setAlpha(1f);
                        super.clearView(recyclerView, viewHolder);
                    }
                });
        helper.attachToRecyclerView(binding.alarmList);
    }

    /** Collapses the extended FAB to a plain icon while the list is scrolling down. */
    private void keepFabOutOfTheWay() {
        binding.alarmList.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (dy > 8 && binding.addAlarmFab.isExtended()) {
                    binding.addAlarmFab.shrink();
                } else if (dy < -8 && !binding.addAlarmFab.isExtended()) {
                    binding.addAlarmFab.extend();
                }
            }
        });
    }

    // ---- permissions --------------------------------------------------------------------------

    private void requestPermissionsIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            return;
        }
        maybePromptForExactAlarms();
    }

    private void maybePromptForExactAlarms() {
        if (!AlarmScheduler.canScheduleExact(this)) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.perm_title)
                    .setMessage(R.string.perm_exact_message)
                    .setPositiveButton(R.string.perm_grant, (d, w) -> openExactAlarmSettings())
                    .setNegativeButton(R.string.perm_later, null)
                    .show();
            return;
        }
        maybePromptForBattery();
    }

    private void openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return;
        }
        try {
            startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    Uri.parse("package:" + getPackageName())));
        } catch (Exception ignored) {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName())));
        }
    }

    private void maybePromptForBattery() {
        if (Prefs.wasBatteryPromptShown(this) || isIgnoringBatteryOptimizations(this)) {
            return;
        }
        Prefs.setBatteryPromptShown(this);
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.perm_title)
                .setMessage(R.string.perm_battery_message)
                .setPositiveButton(R.string.perm_grant, (d, w) -> openBatterySettings())
                .setNegativeButton(R.string.perm_later, null)
                .show();
    }

    private void openBatterySettings() {
        try {
            startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:" + getPackageName())));
        } catch (Exception ignored) {
            startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
        }
    }

    static boolean isIgnoringBatteryOptimizations(@NonNull Context context) {
        PowerManager power = context.getSystemService(PowerManager.class);
        return power != null && power.isIgnoringBatteryOptimizations(context.getPackageName());
    }
}
