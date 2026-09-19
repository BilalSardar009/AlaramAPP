package com.afzal.rozaalarm.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.afzal.rozaalarm.BuildConfig;
import com.afzal.rozaalarm.R;
import com.afzal.rozaalarm.alarm.AlarmScheduler;
import com.afzal.rozaalarm.data.HijriCorrections;
import com.afzal.rozaalarm.databinding.ActivitySettingsBinding;
import com.afzal.rozaalarm.util.HijriDates;
import com.afzal.rozaalarm.util.Prefs;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;


/** App preferences: notification language, Hijri adjustment, and the reliability permissions. */
public class SettingsActivity extends AppCompatActivity {

    private ActivitySettingsBinding binding;
    private HijriCorrections corrections;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        corrections = HijriCorrections.get(this);
        binding.toolbar.setNavigationOnClickListener(v -> finish());
        binding.versionText.setText(getString(R.string.settings_version, BuildConfig.VERSION_NAME));

        binding.languageRow.setOnClickListener(v -> showLanguageDialog());
        binding.exactAlarmRow.setOnClickListener(v -> openExactAlarmSettings());
        binding.batteryRow.setOnClickListener(v -> openBatterySettings());
        binding.notificationPermissionRow.setOnClickListener(v -> openAppNotificationSettings());
        binding.correctTodayRow.setOnClickListener(v -> showCorrectTodayDialog());
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateLanguageValue();
        updateHijriPreview();
        updatePermissionRows();
    }

    // ---- language -----------------------------------------------------------------------------

    private void showLanguageDialog() {
        String[] options = {
                getString(R.string.settings_language_both),
                getString(R.string.settings_language_english),
                getString(R.string.settings_language_urdu)
        };
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.settings_language)
                .setSingleChoiceItems(options, Prefs.notificationLanguage(this),
                        (dialog, which) -> {
                            Prefs.setNotificationLanguage(this, which);
                            updateLanguageValue();
                            dialog.dismiss();
                        })
                .setNegativeButton(R.string.perm_later, null)
                .show();
    }

    private void updateLanguageValue() {
        final int labelRes;
        switch (Prefs.notificationLanguage(this)) {
            case Prefs.LANG_ENGLISH:
                labelRes = R.string.settings_language_english;
                break;
            case Prefs.LANG_URDU:
                labelRes = R.string.settings_language_urdu;
                break;
            case Prefs.LANG_BOTH:
            default:
                labelRes = R.string.settings_language_both;
                break;
        }
        binding.languageValue.setText(labelRes);
    }

    // ---- hijri --------------------------------------------------------------------------------

    private void updateHijriPreview() {
        long now = System.currentTimeMillis();
        binding.hijriTodayEnglish.setText(
                getString(R.string.settings_hijri_today, HijriDates.formatEnglish(now)));
        binding.hijriTodayUrdu.setText(HijriDates.formatUrdu(now));
    }

    /** Asks what today's date really is, and derives the global correction from the answer. */
    private void showCorrectTodayDialog() {
        HijriDateDialog.show(this, corrections, offset -> {
            updateHijriPreview();
            Snackbar.make(binding.settingsRoot,
                    getString(R.string.hijri_set_applied,
                            HijriDates.formatEnglish(System.currentTimeMillis())),
                    Snackbar.LENGTH_LONG).show();
        });
    }

    // ---- permission rows ----------------------------------------------------------------------

    private void updatePermissionRows() {
        boolean exact = AlarmScheduler.canScheduleExact(this);
        binding.exactAlarmValue.setText(exact
                ? R.string.settings_granted : R.string.settings_not_granted);

        boolean battery = MainActivity.isIgnoringBatteryOptimizations(this);
        binding.batteryValue.setText(battery
                ? R.string.settings_granted : R.string.settings_not_granted);

        binding.notificationPermissionValue.setText(hasNotificationPermission()
                ? R.string.settings_granted : R.string.settings_not_granted);
    }

    private boolean hasNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return true;
        }
        return ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return;
        }
        try {
            startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    Uri.parse("package:" + getPackageName())));
        } catch (Exception ignored) {
            openAppDetails();
        }
    }

    private void openBatterySettings() {
        try {
            startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:" + getPackageName())));
        } catch (Exception ignored) {
            openAppDetails();
        }
    }

    private void openAppNotificationSettings() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()));
                return;
            }
        } catch (Exception ignored) {
            // Fall through to the generic app details page.
        }
        openAppDetails();
    }

    private void openAppDetails() {
        startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + getPackageName())));
    }
}
