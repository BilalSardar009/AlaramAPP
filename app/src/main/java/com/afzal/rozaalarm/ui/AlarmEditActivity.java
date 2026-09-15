package com.afzal.rozaalarm.ui;

import android.app.Activity;
import android.content.Intent;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.afzal.rozaalarm.R;
import com.afzal.rozaalarm.data.Alarm;
import com.afzal.rozaalarm.data.AlarmRepository;
import com.afzal.rozaalarm.databinding.ActivityAlarmEditBinding;
import com.afzal.rozaalarm.util.HijriDates;
import com.afzal.rozaalarm.util.Occasion;
import com.afzal.rozaalarm.util.OccasionText;
import com.afzal.rozaalarm.util.Occurrences;
import com.afzal.rozaalarm.util.Prefs;
import com.afzal.rozaalarm.util.TimeText;
import com.google.android.material.chip.Chip;
import com.google.android.material.datepicker.CalendarConstraints;
import com.google.android.material.datepicker.DateValidatorPointForward;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/** Create or edit a single alarm, with a live preview of when it will actually ring. */
public class AlarmEditActivity extends AppCompatActivity {

    public static final String EXTRA_ALARM_ID = "com.afzal.rozaalarm.extra.EDIT_ALARM_ID";

    /** Opens a new alarm pre-set to a particular date, used by the calendar's "set an alarm". */
    public static final String EXTRA_PRESET_DATE = "com.afzal.rozaalarm.extra.PRESET_DATE";

    private static final int PREVIEW_COUNT = 4;

    private ActivityAlarmEditBinding binding;
    private AlarmRepository repository;

    /** The alarm being edited; edits are applied to this object and saved in one go. */
    private Alarm alarm = new Alarm();
    private boolean bindingValues;

    private final ActivityResultLauncher<Intent> tonePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null) {
                    return;
                }
                Uri picked = result.getData().getParcelableExtra(
                        RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
                alarm.toneUri = picked == null ? null : picked.toString();
                updateToneRow();
            });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAlarmEditBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        repository = AlarmRepository.get(this);
        binding.toolbar.setNavigationOnClickListener(v -> finishWithTransition());

        buildMonthDayChips();
        buildWeekDayChips();
        buildOccasionChips();
        wireListeners();

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                finishWithTransition();
            }
        });

        long alarmId = getIntent().getLongExtra(EXTRA_ALARM_ID, 0L);
        if (alarmId > 0L) {
            loadExisting(alarmId);
        } else {
            prepareNewAlarm();
        }
    }

    // ---- loading ------------------------------------------------------------------------------

    private void prepareNewAlarm() {
        alarm = new Alarm();
        alarm.label = getString(R.string.default_alarm_label);
        alarm.snoozeMinutes = Prefs.defaultSnooze(this);
        long presetDate = getIntent().getLongExtra(EXTRA_PRESET_DATE, 0L);
        if (presetDate > 0L) {
            alarm.repeatMode = Alarm.REPEAT_ONCE;
            alarm.onceDateMillis = presetDate;
        } else {
            alarm.onceDateMillis = System.currentTimeMillis();
        }
        binding.toolbar.setTitle(R.string.title_new_alarm);
        bindAlarmToUi();
    }

    private void loadExisting(long alarmId) {
        repository.io().execute(() -> {
            Alarm loaded = repository.getByIdSync(alarmId);
            runOnUiThread(() -> {
                if (loaded == null) {
                    prepareNewAlarm();
                    return;
                }
                alarm = loaded;
                binding.toolbar.setTitle(R.string.title_edit_alarm);
                bindAlarmToUi();
            });
        });
    }

    // ---- chip construction --------------------------------------------------------------------

    private void buildMonthDayChips() {
        ViewGroup group = binding.monthDayChips;
        group.removeAllViews();
        for (int day = 1; day <= 31; day++) {
            Chip chip = new Chip(this, null, com.google.android.material.R.attr.chipStyle);
            chip.setId(View.generateViewId());
            chip.setTag(day);
            chip.setText(String.valueOf(day));
            chip.setCheckable(true);
            chip.setCheckedIconVisible(false);
            chip.setEnsureMinTouchTargetSize(false);
            chip.setOnCheckedChangeListener((button, checked) -> {
                if (!bindingValues) {
                    alarm.setMonthDayList(readCheckedTags(binding.monthDayChips));
                    refreshPreview();
                }
            });
            group.addView(chip);
        }
    }

    private void buildWeekDayChips() {
        ViewGroup group = binding.weekDayChips;
        group.removeAllViews();
        // Start the week on the device's own first day so the row reads naturally.
        int firstDay = Calendar.getInstance().getFirstDayOfWeek();
        for (int i = 0; i < 7; i++) {
            int dayOfWeek = ((firstDay - 1 + i) % 7) + 1;
            Chip chip = new Chip(this, null, com.google.android.material.R.attr.chipStyle);
            chip.setId(View.generateViewId());
            chip.setTag(dayOfWeek);
            chip.setText(TimeText.weekDayName(this, dayOfWeek));
            chip.setCheckable(true);
            chip.setCheckedIconVisible(false);
            chip.setOnCheckedChangeListener((button, checked) -> {
                if (!bindingValues) {
                    alarm.setWeekDayList(readCheckedTags(binding.weekDayChips));
                    refreshPreview();
                }
            });
            group.addView(chip);
        }
    }

    private void buildOccasionChips() {
        ViewGroup group = binding.occasionChips;
        group.removeAllViews();
        for (Occasion occasion : OccasionText.schedulable()) {
            Chip chip = new Chip(this, null, com.google.android.material.R.attr.chipStyle);
            chip.setId(View.generateViewId());
            chip.setTag(occasion.id());
            chip.setText(OccasionText.name(this, occasion));
            chip.setChipIconResource(OccasionText.icon(occasion));
            chip.setCheckable(true);
            chip.setCheckedIconVisible(false);
            chip.setOnCheckedChangeListener((button, checked) -> {
                if (bindingValues || !checked) {
                    return;
                }
                alarm.occasionId = (String) button.getTag();
                updateOccasionNote();
                refreshPreview();
            });
            group.addView(chip);
        }
    }

    private void updateOccasionNote() {
        Occasion occasion = Occasion.fromId(alarm.occasionId);
        if (occasion == null) {
            binding.occasionNote.setText(R.string.occasion_pick);
            return;
        }
        binding.occasionNote.setText(getString(OccasionText.noteEn(occasion))
                + "  ·  " + getString(OccasionText.noteUr(occasion)));
    }

    @NonNull
    private List<Integer> readCheckedTags(@NonNull ViewGroup group) {
        List<Integer> values = new ArrayList<>();
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child instanceof Chip && ((Chip) child).isChecked()) {
                values.add((Integer) child.getTag());
            }
        }
        return values;
    }

    private void applyCheckedTags(@NonNull ViewGroup group, @NonNull List<Integer> values) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child instanceof Chip) {
                ((Chip) child).setChecked(values.contains((Integer) child.getTag()));
            }
        }
    }

    // ---- listeners ----------------------------------------------------------------------------

    private void wireListeners() {
        binding.timeCard.setOnClickListener(v -> pickAlarmTime());
        binding.reminderTimeRow.setOnClickListener(v -> pickReminderTime());
        binding.onceDateCard.setOnClickListener(v -> pickOnceDate());
        binding.toneRow.setOnClickListener(v -> pickTone());
        binding.saveButton.setOnClickListener(v -> save());
        binding.advancedToggle.setOnClickListener(v -> toggleAdvanced());

        binding.repeatChips.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (bindingValues || checkedIds.isEmpty()) {
                return;
            }
            int id = checkedIds.get(0);
            if (id == R.id.repeatOnce) {
                alarm.repeatMode = Alarm.REPEAT_ONCE;
            } else if (id == R.id.repeatDaily) {
                alarm.repeatMode = Alarm.REPEAT_DAILY;
            } else if (id == R.id.repeatWeekly) {
                alarm.repeatMode = Alarm.REPEAT_WEEKLY;
            } else if (id == R.id.repeatOccasion) {
                alarm.repeatMode = Alarm.REPEAT_OCCASION;
                if (Occasion.fromId(alarm.occasionId) == null) {
                    // Default to the fast this app is named for.
                    alarm.occasionId = Occasion.WHITE_DAYS.id();
                    applyCheckedOccasion();
                }
                updateOccasionNote();
            } else {
                alarm.repeatMode = Alarm.REPEAT_MONTHLY;
            }
            applyModeVisibility();
            refreshPreview();
        });

        binding.calendarToggle.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (bindingValues || !isChecked) {
                return;
            }
            alarm.calendarType = checkedId == R.id.calendarHijri
                    ? Alarm.CALENDAR_HIJRI
                    : Alarm.CALENDAR_GREGORIAN;
            updateCalendarHint();
            refreshPreview();
        });

        CompoundButton.OnCheckedChangeListener simpleToggle = (button, checked) -> {
            if (bindingValues) {
                return;
            }
            int id = button.getId();
            if (id == R.id.clampSwitch) {
                alarm.clampToMonthEnd = checked;
            } else if (id == R.id.reminderSwitch) {
                alarm.preReminderEnabled = checked;
                binding.reminderOptions.setVisibility(checked ? View.VISIBLE : View.GONE);
            } else if (id == R.id.reminderFirstDaySwitch) {
                alarm.preReminderFirstDayOnly = checked;
            } else if (id == R.id.vibrateSwitch) {
                alarm.vibrate = checked;
            } else if (id == R.id.skipForbiddenSwitch) {
                alarm.skipForbiddenDays = checked;
            }
            refreshPreview();
        };
        binding.clampSwitch.setOnCheckedChangeListener(simpleToggle);
        binding.reminderSwitch.setOnCheckedChangeListener(simpleToggle);
        binding.reminderFirstDaySwitch.setOnCheckedChangeListener(simpleToggle);
        binding.vibrateSwitch.setOnCheckedChangeListener(simpleToggle);
        binding.skipForbiddenSwitch.setOnCheckedChangeListener(simpleToggle);

        binding.reminderDaysSlider.addOnChangeListener((slider, value, fromUser) -> {
            alarm.preReminderDaysBefore = (int) value;
            updateReminderDaysLabel();
            if (!bindingValues) {
                refreshPreview();
            }
        });

        binding.snoozeSlider.addOnChangeListener((slider, value, fromUser) -> {
            alarm.snoozeMinutes = (int) value;
            updateSnoozeLabel();
        });
    }


    // ---- binding ------------------------------------------------------------------------------

    /** Shows or hides the settings most people never need to touch. */
    private void toggleAdvanced() {
        boolean showing = binding.advancedSection.getVisibility() == View.VISIBLE;
        binding.advancedSection.setVisibility(showing ? View.GONE : View.VISIBLE);
        binding.advancedToggle.setText(showing ? R.string.more_options : R.string.fewer_options);
        binding.advancedToggle.setIconResource(
                showing ? R.drawable.ic_expand_more : R.drawable.ic_expand_less);
    }

    private void bindAlarmToUi() {
        bindingValues = true;

        binding.labelInput.setText(alarm.label);
        updateTimeDisplay();

        int repeatChipId;
        switch (alarm.repeatMode) {
            case Alarm.REPEAT_ONCE: repeatChipId = R.id.repeatOnce; break;
            case Alarm.REPEAT_DAILY: repeatChipId = R.id.repeatDaily; break;
            case Alarm.REPEAT_WEEKLY: repeatChipId = R.id.repeatWeekly; break;
            case Alarm.REPEAT_OCCASION: repeatChipId = R.id.repeatOccasion; break;
            default: repeatChipId = R.id.repeatMonthly; break;
        }
        binding.repeatChips.check(repeatChipId);
        binding.calendarToggle.check(alarm.calendarType == Alarm.CALENDAR_HIJRI
                ? R.id.calendarHijri : R.id.calendarGregorian);

        applyCheckedTags(binding.monthDayChips, alarm.monthDayList());
        applyCheckedTags(binding.weekDayChips, alarm.weekDayList());

        applyCheckedOccasion();

        binding.clampSwitch.setChecked(alarm.clampToMonthEnd);
        binding.vibrateSwitch.setChecked(alarm.vibrate);
        binding.skipForbiddenSwitch.setChecked(alarm.skipForbiddenDays);

        binding.reminderSwitch.setChecked(alarm.preReminderEnabled);
        binding.reminderOptions.setVisibility(alarm.preReminderEnabled ? View.VISIBLE : View.GONE);
        binding.reminderFirstDaySwitch.setChecked(alarm.preReminderFirstDayOnly);
        binding.reminderDaysSlider.setValue(clamp(alarm.preReminderDaysBefore, 1, 7));
        binding.snoozeSlider.setValue(clamp(alarm.snoozeMinutes, 1, 30));

        updateReminderDaysLabel();
        updateSnoozeLabel();
        updateReminderTimeValue();
        updateOnceDateText();
        updateToneRow();
        updateCalendarHint();
        updateOccasionNote();
        applyModeVisibility();

        bindingValues = false;
        refreshPreview();
    }

    private void applyCheckedOccasion() {
        ViewGroup group = binding.occasionChips;
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child instanceof Chip) {
                ((Chip) child).setChecked(child.getTag().equals(alarm.occasionId));
            }
        }
    }

    private void applyModeVisibility() {
        binding.monthlySection.setVisibility(
                alarm.repeatMode == Alarm.REPEAT_MONTHLY ? View.VISIBLE : View.GONE);
        binding.weeklySection.setVisibility(
                alarm.repeatMode == Alarm.REPEAT_WEEKLY ? View.VISIBLE : View.GONE);
        binding.onceSection.setVisibility(
                alarm.repeatMode == Alarm.REPEAT_ONCE ? View.VISIBLE : View.GONE);
        binding.occasionSection.setVisibility(
                alarm.repeatMode == Alarm.REPEAT_OCCASION ? View.VISIBLE : View.GONE);
    }

    private void updateTimeDisplay() {
        binding.timeDigits.setText(TimeText.clockDigits(this, alarm.hour, alarm.minute));
        String meridiem = TimeText.clockSuffix(this, alarm.hour);
        binding.timeMeridiem.setText(meridiem);
        binding.timeMeridiem.setVisibility(meridiem.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void updateReminderDaysLabel() {
        String days = getResources().getQuantityString(R.plurals.days_count,
                alarm.preReminderDaysBefore, alarm.preReminderDaysBefore);
        binding.reminderDaysLabel.setText(
                getString(R.string.reminder_days_before) + ": " + days);
    }

    private void updateSnoozeLabel() {
        String minutes = getResources().getQuantityString(R.plurals.minutes_count,
                alarm.snoozeMinutes, alarm.snoozeMinutes);
        binding.snoozeLabel.setText(getString(R.string.option_snooze) + ": " + minutes);
    }

    private void updateReminderTimeValue() {
        binding.reminderTimeValue.setText(
                TimeText.time(this, alarm.preReminderHour, alarm.preReminderMinute));
    }

    private void updateOnceDateText() {
        long millis = alarm.onceDateMillis > 0 ? alarm.onceDateMillis : System.currentTimeMillis();
        binding.onceDateText.setText(TimeText.fullDate(this, millis));
    }

    private void updateCalendarHint() {
        binding.calendarHint.setText(alarm.calendarType == Alarm.CALENDAR_HIJRI
                ? R.string.calendar_hijri_hint
                : R.string.calendar_gregorian_hint);
    }

    private void updateToneRow() {
        if (TextUtils.isEmpty(alarm.toneUri)) {
            binding.toneValue.setText(R.string.option_tone_default);
            return;
        }
        String title = null;
        try {
            android.media.Ringtone ringtone =
                    RingtoneManager.getRingtone(this, Uri.parse(alarm.toneUri));
            if (ringtone != null) {
                title = ringtone.getTitle(this);
            }
        } catch (Exception ignored) {
            // A tone can disappear when its storage is unmounted; fall back to the default label.
        }
        binding.toneValue.setText(TextUtils.isEmpty(title)
                ? getString(R.string.option_tone_default) : title);
    }

    /** Recomputes the "this will ring on…" card from the current form state. */
    private void refreshPreview() {
        readLabelFromInput();
        long now = System.currentTimeMillis();

        List<Long> upcoming = Occurrences.nextMany(alarm, now, PREVIEW_COUNT);
        if (upcoming.isEmpty()) {
            binding.previewList.setText(R.string.preview_none);
            binding.previewReminder.setVisibility(View.GONE);
            return;
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < upcoming.size(); i++) {
            if (i > 0) {
                sb.append('\n');
            }
            long occurrence = upcoming.get(i);
            sb.append(TimeText.dateTime(this, occurrence));
            boolean showHijri = alarm.repeatMode == Alarm.REPEAT_OCCASION
                    || (alarm.repeatMode == Alarm.REPEAT_MONTHLY
                    && alarm.calendarType == Alarm.CALENDAR_HIJRI);
            if (showHijri) {
                sb.append("   (").append(HijriDates.formatEnglish(occurrence)).append(')');
            }
        }
        binding.previewList.setText(sb.toString());

        long reminder = Occurrences.nextReminder(alarm, now);
        if (reminder == Occurrences.NONE) {
            binding.previewReminder.setVisibility(alarm.preReminderEnabled
                    ? View.VISIBLE : View.GONE);
            binding.previewReminder.setText(R.string.reminder_next_none);
        } else {
            binding.previewReminder.setVisibility(View.VISIBLE);
            binding.previewReminder.setText(getString(R.string.reminder_next_preview,
                    TimeText.dateTime(this, reminder)));
        }
    }

    private void readLabelFromInput() {
        CharSequence typed = binding.labelInput.getText();
        alarm.label = typed == null ? "" : typed.toString().trim();
    }

    // ---- pickers ------------------------------------------------------------------------------

    private void pickAlarmTime() {
        MaterialTimePicker picker = new MaterialTimePicker.Builder()
                .setTimeFormat(DateFormat.is24HourFormat(this)
                        ? TimeFormat.CLOCK_24H : TimeFormat.CLOCK_12H)
                .setHour(alarm.hour)
                .setMinute(alarm.minute)
                .setTitleText(R.string.section_time)
                .build();
        picker.addOnPositiveButtonClickListener(v -> {
            alarm.hour = picker.getHour();
            alarm.minute = picker.getMinute();
            updateTimeDisplay();
            refreshPreview();
        });
        picker.show(getSupportFragmentManager(), "alarm_time");
    }

    private void pickReminderTime() {
        MaterialTimePicker picker = new MaterialTimePicker.Builder()
                .setTimeFormat(DateFormat.is24HourFormat(this)
                        ? TimeFormat.CLOCK_24H : TimeFormat.CLOCK_12H)
                .setHour(alarm.preReminderHour)
                .setMinute(alarm.preReminderMinute)
                .setTitleText(R.string.reminder_time)
                .build();
        picker.addOnPositiveButtonClickListener(v -> {
            alarm.preReminderHour = picker.getHour();
            alarm.preReminderMinute = picker.getMinute();
            updateReminderTimeValue();
            refreshPreview();
        });
        picker.show(getSupportFragmentManager(), "reminder_time");
    }

    private void pickOnceDate() {
        CalendarConstraints constraints = new CalendarConstraints.Builder()
                .setValidator(DateValidatorPointForward.now())
                .build();
        long selection = toUtcMidnight(alarm.onceDateMillis > 0
                ? alarm.onceDateMillis : System.currentTimeMillis());
        MaterialDatePicker<Long> picker = MaterialDatePicker.Builder.datePicker()
                .setTitleText(R.string.pick_date)
                .setCalendarConstraints(constraints)
                .setSelection(selection)
                .build();
        picker.addOnPositiveButtonClickListener(utcMillis -> {
            alarm.onceDateMillis = toLocalDate(utcMillis);
            updateOnceDateText();
            refreshPreview();
        });
        picker.show(getSupportFragmentManager(), "once_date");
    }

    /** The local calendar day of an instant, expressed as the UTC midnight the picker expects. */
    private long toUtcMidnight(long localMillis) {
        Calendar local = Calendar.getInstance();
        local.setTimeInMillis(localMillis);
        Calendar utc = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"));
        utc.clear();
        utc.set(local.get(Calendar.YEAR), local.get(Calendar.MONTH),
                local.get(Calendar.DAY_OF_MONTH));
        return utc.getTimeInMillis();
    }

    /**
     * The Material date picker hands back midnight UTC; re-read the calendar fields in the local
     * zone so "13 November" stays the 13th regardless of the offset.
     */
    private long toLocalDate(long utcMillis) {
        Calendar utc = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"));
        utc.setTimeInMillis(utcMillis);
        Calendar local = Calendar.getInstance();
        local.set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH));
        Occurrences.applyTime(local, alarm.hour, alarm.minute);
        return local.getTimeInMillis();
    }

    private void pickTone() {
        Intent intent = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, getString(R.string.option_tone))
                .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI,
                        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM));
        if (!TextUtils.isEmpty(alarm.toneUri)) {
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(alarm.toneUri));
        }
        try {
            tonePickerLauncher.launch(intent);
        } catch (Exception ignored) {
            Snackbar.make(binding.editRoot, R.string.option_tone_default, Snackbar.LENGTH_SHORT)
                    .show();
        }
    }

    // ---- saving -------------------------------------------------------------------------------

    private void save() {
        readLabelFromInput();
        if (alarm.label.isEmpty()) {
            alarm.label = getString(R.string.default_alarm_label);
        }

        if (alarm.repeatMode == Alarm.REPEAT_MONTHLY && alarm.monthDayList().isEmpty()) {
            showError(R.string.error_pick_days);
            return;
        }
        if (alarm.repeatMode == Alarm.REPEAT_WEEKLY && alarm.weekDayList().isEmpty()) {
            showError(R.string.error_pick_days);
            return;
        }
        if (alarm.repeatMode == Alarm.REPEAT_OCCASION
                && Occasion.fromId(alarm.occasionId) == null) {
            showError(R.string.occasion_pick);
            return;
        }

        long next = Occurrences.next(alarm, System.currentTimeMillis());
        if (next == Occurrences.NONE) {
            showError(alarm.repeatMode == Alarm.REPEAT_ONCE
                    ? R.string.error_pick_future_date : R.string.preview_none);
            return;
        }

        alarm.enabled = true;
        String whenText = TimeText.dateTime(this, next);
        repository.save(alarm, id -> runOnUiThread(() -> {
            Snackbar.make(binding.editRoot,
                    getString(R.string.saved_toast, whenText), Snackbar.LENGTH_SHORT).show();
            binding.saveButton.postDelayed(this::finishWithTransition, 450L);
        }));
    }

    private void showError(int messageRes) {
        Snackbar.make(binding.editRoot, messageRes, Snackbar.LENGTH_LONG)
                .setAnchorView(binding.saveButton)
                .show();
    }

    private void finishWithTransition() {
        finish();
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
    }

    private static float clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
