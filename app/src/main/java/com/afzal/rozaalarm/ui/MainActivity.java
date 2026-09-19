package com.afzal.rozaalarm.ui;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.afzal.rozaalarm.R;
import com.afzal.rozaalarm.alarm.AlarmScheduler;
import com.afzal.rozaalarm.databinding.ActivityMainBinding;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * Hosts the three tabs — the alarms, the calendar and the history — and owns the permission
 * prompts, which belong to the app as a whole rather than to any one tab.
 */
public class MainActivity extends AppCompatActivity {

    /** Ask the Calendar tab to open on a particular Hijri month. */
    public static final String EXTRA_SHOW_HIJRI_YEAR = "com.afzal.rozaalarm.extra.HIJRI_YEAR";
    public static final String EXTRA_SHOW_HIJRI_MONTH = "com.afzal.rozaalarm.extra.HIJRI_MONTH";
    /** Days to arrive already selected, as {@code yyyyMMdd} keys. */
    public static final String EXTRA_SELECT_DAYS = "com.afzal.rozaalarm.extra.SELECT_DAYS";

    private static final String STATE_TAB = "selected_tab";

    private ActivityMainBinding binding;
    private int selectedTabId = R.id.tab_home;

    private final ActivityResultLauncher<String> notificationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(),
                    granted -> maybePromptForExactAlarms());

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.bottomNav.setOnItemSelectedListener(item -> {
            showTab(item.getItemId());
            return true;
        });
        binding.bottomNav.setOnItemReselectedListener(item -> {
            // Reselecting a tab should not rebuild it.
        });

        if (savedInstanceState != null) {
            selectedTabId = savedInstanceState.getInt(STATE_TAB, R.id.tab_home);
        }
        if (getIntent().hasExtra(EXTRA_SHOW_HIJRI_MONTH)) {
            selectedTabId = R.id.tab_calendar;
        }

        binding.bottomNav.setSelectedItemId(selectedTabId);
        showTab(selectedTabId);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent.hasExtra(EXTRA_SHOW_HIJRI_MONTH)) {
            binding.bottomNav.setSelectedItemId(R.id.tab_calendar);
            showTab(R.id.tab_calendar);
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_TAB, selectedTabId);
    }

    @Override
    protected void onResume() {
        super.onResume();
        requestPermissionsIfNeeded();
    }

    /** Jump to the Calendar tab as it stands. */
    public void showCalendar() {
        showCalendarMonth(-1, -1, null);
    }

    /**
     * Jump to the Calendar tab focused on one Hijri month, optionally with days already selected.
     *
     * <p>Used by the History tab to open a month, and by the home screen's fasting days, which
     * hand over the days of that fast so an alarm is always set from days you can see.</p>
     */
    public void showCalendarMonth(int hijriYear, int hijriMonth,
                                  @Nullable java.util.List<Integer> selectDayKeys) {
        Intent intent = getIntent();
        intent.putExtra(EXTRA_SHOW_HIJRI_YEAR, hijriYear);
        intent.putExtra(EXTRA_SHOW_HIJRI_MONTH, hijriMonth);
        if (selectDayKeys == null || selectDayKeys.isEmpty()) {
            intent.removeExtra(EXTRA_SELECT_DAYS);
        } else {
            int[] keys = new int[selectDayKeys.size()];
            for (int i = 0; i < keys.length; i++) {
                keys[i] = selectDayKeys.get(i);
            }
            intent.putExtra(EXTRA_SELECT_DAYS, keys);
        }

        // The calendar is rebuilt so it picks the request up even when the tab already exists.
        Fragment existing = getSupportFragmentManager().findFragmentByTag("tab:" + R.id.tab_calendar);
        if (existing != null) {
            getSupportFragmentManager().beginTransaction().remove(existing).commitNow();
        }
        binding.bottomNav.setSelectedItemId(R.id.tab_calendar);
        showTab(R.id.tab_calendar);
    }

    // ---- tabs ---------------------------------------------------------------------------------

    private void showTab(int itemId) {
        selectedTabId = itemId;
        String tag = "tab:" + itemId;

        Fragment existing = getSupportFragmentManager().findFragmentByTag(tag);
        if (existing != null && existing.isVisible()) {
            return;
        }

        Fragment fragment = existing != null ? existing : createFragment(itemId);
        getSupportFragmentManager().beginTransaction()
                .setCustomAnimations(R.anim.fade_in, R.anim.fade_out)
                .replace(R.id.tabContainer, fragment, tag)
                .commit();
    }

    @NonNull
    private Fragment createFragment(int itemId) {
        if (itemId == R.id.tab_history) {
            return new HistoryFragment();
        }
        if (itemId == R.id.tab_home) {
            return new HomeFragment();
        }
        int year = getIntent().getIntExtra(EXTRA_SHOW_HIJRI_YEAR, -1);
        int month = getIntent().getIntExtra(EXTRA_SHOW_HIJRI_MONTH, -1);
        int[] days = getIntent().getIntArrayExtra(EXTRA_SELECT_DAYS);
        // Consume the request so rotating the device does not jump back to that month.
        getIntent().removeExtra(EXTRA_SHOW_HIJRI_YEAR);
        getIntent().removeExtra(EXTRA_SHOW_HIJRI_MONTH);
        getIntent().removeExtra(EXTRA_SELECT_DAYS);
        return CalendarFragment.newInstance(year, month, days);
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
        if (com.afzal.rozaalarm.util.Prefs.wasBatteryPromptShown(this)
                || isIgnoringBatteryOptimizations(this)) {
            return;
        }
        com.afzal.rozaalarm.util.Prefs.setBatteryPromptShown(this);
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
