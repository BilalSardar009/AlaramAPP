package com.afzal.rozaalarm.ui;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.afzal.rozaalarm.R;
import com.afzal.rozaalarm.data.Alarm;
import com.afzal.rozaalarm.data.AlarmRepository;
import com.afzal.rozaalarm.data.FastLog;
import com.afzal.rozaalarm.data.FastRepository;
import com.afzal.rozaalarm.data.HijriCorrections;
import com.afzal.rozaalarm.databinding.FragmentCalendarBinding;
import com.afzal.rozaalarm.ui.adapter.AlarmAdapter;
import com.afzal.rozaalarm.ui.adapter.CalendarDay;
import com.afzal.rozaalarm.ui.adapter.CalendarDayAdapter;
import com.afzal.rozaalarm.util.HijriDates;
import com.afzal.rozaalarm.util.Occasion;
import com.afzal.rozaalarm.util.OccasionText;
import com.afzal.rozaalarm.util.Occasions;
import com.afzal.rozaalarm.util.Occurrences;
import com.afzal.rozaalarm.util.Prefs;
import com.afzal.rozaalarm.util.TimeText;
import com.google.android.material.chip.Chip;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The whole app, bar the history: one Islamic month at a time, with the Gregorian date in the
 * corner of every cell.
 *
 * <p>Days are selected by tapping — any number of them — and the selection is what the buttons
 * underneath act on, so a run of fasts becomes one alarm rather than one alarm each. Alongside it
 * sit the fasting shortcuts (Ramadan, 9 and 10 Muharram, the White Days …) and the alarms that
 * have been set, so nothing needs a second screen.</p>
 */
public class CalendarFragment extends Fragment
        implements CalendarDayAdapter.Listener, AlarmAdapter.Listener {

    private static final String ARG_YEAR = "hijri_year";
    private static final String ARG_MONTH = "hijri_month";

    private static final int COLUMNS = 7;

    /** How far either side of this Hijri year the year dropdown reaches. */
    private static final int YEARS_BACK = 3;
    private static final int YEARS_FORWARD = 20;

    private FragmentCalendarBinding binding;
    private CalendarDayAdapter dayAdapter;
    private AlarmAdapter alarmAdapter;
    private FastRepository fastRepository;
    private AlarmRepository alarmRepository;
    private HijriCorrections corrections;

    private int shownYear;
    private int shownMonth;

    /** Set while the dropdowns are being filled in, so their listeners do not fire back. */
    private boolean bindingPickers;

    /** The Hijri year the first entry of the year dropdown stands for. */
    private int yearListBase;

    private final List<CalendarDay> gridDays = new ArrayList<>();
    private final Set<Integer> keptDateKeys = new HashSet<>();
    private final Set<Integer> missedDateKeys = new HashSet<>();
    private final List<Alarm> currentAlarms = new ArrayList<>();

    /** The days the buttons act on, as {@code yyyyMMdd} keys. Kept across month changes. */
    private final Set<Integer> selectedKeys = new LinkedHashSet<>();

    /** True until the first month has been drawn, so today is selected only once. */
    private boolean firstBuild = true;

    @NonNull
    public static CalendarFragment newInstance(int hijriYear, int hijriMonth) {
        CalendarFragment fragment = new CalendarFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_YEAR, hijriYear);
        args.putInt(ARG_MONTH, hijriMonth);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentCalendarBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        fastRepository = FastRepository.get(requireContext());
        alarmRepository = AlarmRepository.get(requireContext());
        corrections = HijriCorrections.get(requireContext());

        long now = System.currentTimeMillis();
        int argYear = getArguments() == null ? -1 : getArguments().getInt(ARG_YEAR, -1);
        int argMonth = getArguments() == null ? -1 : getArguments().getInt(ARG_MONTH, -1);
        shownYear = argYear > 0 ? argYear : HijriDates.year(now);
        shownMonth = argMonth >= 0 && argMonth <= 11 ? argMonth : HijriDates.month(now);

        dayAdapter = new CalendarDayAdapter(this);
        binding.dayGrid.setLayoutManager(new GridLayoutManager(requireContext(), COLUMNS));
        binding.dayGrid.setAdapter(dayAdapter);
        binding.dayGrid.setItemAnimator(null);

        alarmAdapter = new AlarmAdapter(this);
        binding.alarmList.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.alarmList.setAdapter(alarmAdapter);
        binding.alarmList.setItemAnimator(null);

        buildWeekdayHeader();
        buildLegend();
        buildShortcutChips();
        wireControls();

        // Rebuilding on every change keeps the ticks in step with the History tab.
        fastRepository.observeAll().observe(getViewLifecycleOwner(), logs -> {
            keptDateKeys.clear();
            missedDateKeys.clear();
            for (FastLog log : logs) {
                if (log.status == FastLog.STATUS_MISSED) {
                    missedDateKeys.add(log.dateKey);
                } else {
                    keptDateKeys.add(log.dateKey);
                }
            }
            rebuildMonth();
        });

        alarmRepository.observeAll().observe(getViewLifecycleOwner(), alarms -> {
            currentAlarms.clear();
            currentAlarms.addAll(alarms);
            alarmAdapter.submitList(new ArrayList<>(alarms));
            binding.alarmsEmpty.setVisibility(alarms.isEmpty() ? View.VISIBLE : View.GONE);
            binding.alarmList.setVisibility(alarms.isEmpty() ? View.GONE : View.VISIBLE);
            rebuildMonth();
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        updateTodayStrip();
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }

    // ---- chrome -------------------------------------------------------------------------------

    private void wireControls() {
        binding.prevMonth.setOnClickListener(v -> stepMonth(-1));
        binding.nextMonth.setOnClickListener(v -> stepMonth(1));
        binding.todayButton.setOnClickListener(v -> goToToday());
        binding.setDateButton.setOnClickListener(v -> showSetDateDialog());

        // The month list never changes, so it is filled in once rather than on every redraw.
        bindingPickers = true;
        binding.monthPicker.setSimpleItems(HijriDates.MONTHS_EN.clone());
        int baseYear = HijriDates.year(System.currentTimeMillis()) - YEARS_BACK;
        String[] years = new String[YEARS_BACK + YEARS_FORWARD + 1];
        for (int i = 0; i < years.length; i++) {
            years[i] = String.valueOf(baseYear + i);
        }
        yearListBase = baseYear;
        binding.yearPicker.setSimpleItems(years);
        bindingPickers = false;

        binding.toolbar.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_set_date) {
                showSetDateDialog();
                return true;
            }
            if (id == R.id.action_adjust_month) {
                showMonthAdjustDialog();
                return true;
            }
            if (id == R.id.action_settings) {
                startActivity(new Intent(requireContext(), SettingsActivity.class));
                requireActivity().overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
                return true;
            }
            return false;
        });

        binding.monthPicker.setOnItemClickListener((parent, view, position, id) -> {
            if (bindingPickers) {
                return;
            }
            shownMonth = position;
            rebuildMonth();
        });
        binding.yearPicker.setOnItemClickListener((parent, view, position, id) -> {
            if (bindingPickers) {
                return;
            }
            shownYear = yearListBase + position;
            rebuildMonth();
        });

        binding.alarmButton.setOnClickListener(v -> setAlarmForSelection());
        binding.recordButton.setOnClickListener(v -> recordSelection(FastLog.STATUS_KEPT));
        binding.missedButton.setOnClickListener(v -> recordSelection(FastLog.STATUS_MISSED));
        binding.clearButton.setOnClickListener(v -> {
            selectedKeys.clear();
            dayAdapter.updateSelection(selectedKeys);
            updateSelectionCard();
        });
    }

    private void updateTodayStrip() {
        if (binding == null) {
            return;
        }
        long now = System.currentTimeMillis();
        binding.todayHijri.setText(HijriDates.formatEnglish(now));
        binding.todayHijriUrdu.setText(HijriDates.formatUrdu(now));
        binding.todayGregorian.setText(TimeText.fullDate(requireContext(), now));
    }

    // ---- grid ---------------------------------------------------------------------------------

    private void stepMonth(int delta) {
        int absolute = shownYear * 12 + shownMonth + delta;
        shownYear = absolute / 12;
        shownMonth = absolute % 12;
        if (shownMonth < 0) {
            shownMonth += 12;
            shownYear -= 1;
        }
        rebuildMonth();
    }

    private void goToToday() {
        long now = System.currentTimeMillis();
        shownYear = HijriDates.year(now);
        shownMonth = HijriDates.month(now);
        selectedKeys.clear();
        selectedKeys.add(FastLog.dateKeyOf(now));
        rebuildMonth();
    }

    private void rebuildMonth() {
        if (binding == null) {
            return;
        }

        long monthStart = HijriDates.gregorianMidnightOfMonthStart(shownYear, shownMonth);
        int monthLength = HijriDates.monthLengthOf(shownYear, shownMonth);
        int todayKey = FastLog.dateKeyOf(System.currentTimeMillis());

        gridDays.clear();

        Calendar cursor = Calendar.getInstance();
        cursor.setTimeInMillis(monthStart);

        int firstDayOfWeek = cursor.getFirstDayOfWeek();
        int lead = ((cursor.get(Calendar.DAY_OF_WEEK) - firstDayOfWeek) + 7) % 7;
        for (int i = 0; i < lead; i++) {
            gridDays.add(CalendarDay.blank());
        }

        for (int day = 1; day <= monthLength; day++) {
            Calendar dayCal = Calendar.getInstance();
            dayCal.setTimeInMillis(monthStart);
            dayCal.add(Calendar.DAY_OF_MONTH, day - 1);
            long millis = dayCal.getTimeInMillis();
            int dateKey = FastLog.dateKeyOf(dayCal);

            // The occasions come from the grid's own Hijri coordinates rather than from a second
            // conversion, so the labels can never disagree with the cell they sit in.
            List<Occasion> occasions = Occasions.forHijriDay(shownMonth, day);

            CalendarDay cell = new CalendarDay(day, dayCal.get(Calendar.DAY_OF_MONTH), millis,
                    dateKey, dateKey == todayKey, occasions);
            cell.logged = keptDateKeys.contains(dateKey) || missedDateKeys.contains(dateKey);
            cell.missed = missedDateKeys.contains(dateKey);
            cell.alarmed = hasAlarmOn(cell);
            gridDays.add(cell);
        }

        // The very first month opens on today, so the card underneath has something to say.
        if (firstBuild) {
            firstBuild = false;
            for (CalendarDay cell : gridDays) {
                if (!cell.blank && cell.today) {
                    selectedKeys.add(cell.dateKey);
                }
            }
        }

        dayAdapter.submit(new ArrayList<>(gridDays), selectedKeys);

        updateMonthHeader(monthStart, monthLength);
        updateTodayStrip();
        updateSelectionCard();
    }

    /** True when an enabled alarm is going to ring on this day. */
    private boolean hasAlarmOn(@NonNull CalendarDay day) {
        for (Alarm alarm : currentAlarms) {
            if (!alarm.enabled) {
                continue;
            }
            if (alarm.skipForbiddenDays && day.isForbidden()) {
                continue;
            }
            if (alarm.repeatMode == Alarm.REPEAT_DATES) {
                if (alarm.dateKeyList().contains(day.dateKey)) {
                    return true;
                }
                continue;
            }
            Occasion occasion = Occasion.fromId(alarm.occasionId);
            if (occasion != null && Occasions.covers(occasion, shownMonth, day.hijriDay)) {
                return true;
            }
        }
        return false;
    }

    private void updateMonthHeader(long monthStart, int monthLength) {
        bindingPickers = true;
        binding.monthPicker.setText(HijriDates.monthNameEnglish(shownMonth), false);
        binding.yearPicker.setText(String.valueOf(shownYear), false);
        bindingPickers = false;

        binding.monthTitleUrdu.setText(HijriDates.monthTitleUrdu(shownYear, shownMonth));

        Calendar end = Calendar.getInstance();
        end.setTimeInMillis(monthStart);
        end.add(Calendar.DAY_OF_MONTH, monthLength - 1);
        binding.monthSpan.setText(getString(R.string.calendar_gregorian_span,
                TimeText.date(requireContext(), monthStart),
                TimeText.date(requireContext(), end.getTimeInMillis())));

        int monthOffset = HijriDates.monthOffset(shownYear, shownMonth);
        if (monthOffset == 0) {
            binding.monthAdjusted.setVisibility(View.GONE);
        } else {
            binding.monthAdjusted.setVisibility(View.VISIBLE);
            binding.monthAdjusted.setText(
                    getString(R.string.calendar_month_corrected, monthOffset));
        }
    }

    // ---- selection ----------------------------------------------------------------------------

    @Override
    public void onDayToggled(@NonNull CalendarDay day) {
        if (!selectedKeys.remove(day.dateKey)) {
            selectedKeys.add(day.dateKey);
        }
        dayAdapter.updateSelection(selectedKeys);
        updateSelectionCard();
    }

    /** The selected day currently on screen, or {@code null} when several or none are selected. */
    @Nullable
    private CalendarDay soleSelectedDay() {
        if (selectedKeys.size() != 1) {
            return null;
        }
        int key = selectedKeys.iterator().next();
        for (CalendarDay day : gridDays) {
            if (!day.blank && day.dateKey == key) {
                return day;
            }
        }
        return null;
    }

    private void updateSelectionCard() {
        if (binding == null) {
            return;
        }
        int count = selectedKeys.size();
        CalendarDay single = soleSelectedDay();

        boolean anySelected = count > 0;
        binding.alarmButton.setEnabled(anySelected);
        binding.clearButton.setEnabled(anySelected);

        if (count == 0) {
            binding.selectionTitle.setText(R.string.calendar_selection_none);
            binding.selectionSubtitle.setText(R.string.calendar_selection_none_hint);
            binding.occasionChips.removeAllViews();
            binding.occasionNote.setVisibility(View.GONE);
            binding.forbiddenWarning.setVisibility(View.GONE);
            binding.alarmButton.setText(R.string.calendar_set_alarm);
            binding.recordButton.setText(R.string.day_sheet_record);
            binding.recordButton.setEnabled(false);
            binding.missedButton.setText(R.string.day_sheet_missed);
            binding.missedButton.setEnabled(false);
            return;
        }

        if (single != null) {
            binding.selectionTitle.setText(TimeText.fullDate(requireContext(), single.millis));
            binding.selectionSubtitle.setText(
                    single.hijriDay + " " + HijriDates.monthNameEnglish(shownMonth) + " "
                            + shownYear + " AH   ·   " + HijriDates.toUrduDigits(single.hijriDay)
                            + " " + HijriDates.monthNameUrdu(shownMonth) + " "
                            + HijriDates.toUrduDigits(shownYear) + "ھ");
        } else {
            binding.selectionTitle.setText(getResources().getQuantityString(
                    R.plurals.selected_days_count, count, count));
            binding.selectionSubtitle.setText(selectionRangeText());
        }

        buildOccasionChips(single);

        boolean allForbidden = true;
        boolean anyForbidden = false;
        for (int key : selectedKeys) {
            if (Occasions.isFastingForbidden(FastLog.millisOfDateKey(key))) {
                anyForbidden = true;
            } else {
                allForbidden = false;
            }
        }
        binding.forbiddenWarning.setVisibility(anyForbidden ? View.VISIBLE : View.GONE);
        binding.forbiddenWarning.setText(single != null || !anyForbidden
                ? getString(R.string.day_sheet_forbidden_warning)
                : getString(R.string.calendar_some_forbidden));

        binding.alarmButton.setText(count == 1
                ? getString(R.string.calendar_set_alarm)
                : getString(R.string.calendar_set_alarm_for, count));

        // One day keeps the old toggle: tapping again takes the record back off.
        boolean recordedKept = single != null && single.logged && !single.missed;
        boolean recordedMissed = single != null && single.missed;
        binding.recordButton.setText(recordedKept
                ? getString(R.string.day_sheet_remove)
                : count == 1
                        ? getString(R.string.day_sheet_record)
                        : getResources().getQuantityString(R.plurals.record_days_count,
                                count, count));
        binding.missedButton.setText(recordedMissed
                ? getString(R.string.day_sheet_remove) : getString(R.string.day_sheet_missed));

        // Fasting is not permitted on Eid or the days of Tashreeq, so recording one is not offered.
        binding.recordButton.setEnabled(!allForbidden);
        binding.missedButton.setEnabled(!allForbidden);
    }

    @NonNull
    private String selectionRangeText() {
        List<Integer> keys = new ArrayList<>(selectedKeys);
        java.util.Collections.sort(keys);
        return getString(R.string.calendar_selection_span,
                TimeText.date(requireContext(), FastLog.millisOfDateKey(keys.get(0))),
                TimeText.date(requireContext(),
                        FastLog.millisOfDateKey(keys.get(keys.size() - 1))));
    }

    private void buildOccasionChips(@Nullable CalendarDay single) {
        binding.occasionChips.removeAllViews();
        if (single == null) {
            binding.occasionNote.setVisibility(View.GONE);
            return;
        }
        for (Occasion occasion : single.occasions) {
            Chip chip = new Chip(requireContext(), null,
                    com.google.android.material.R.attr.chipStyle);
            chip.setText(OccasionText.name(requireContext(), occasion));
            chip.setChipIconResource(OccasionText.icon(occasion));
            chip.setChipIconTint(android.content.res.ColorStateList.valueOf(
                    MaterialColors.getColor(binding.getRoot(),
                            OccasionText.categoryColorAttr(occasion.category()))));
            chip.setCheckable(false);
            chip.setClickable(false);
            chip.setEnsureMinTouchTargetSize(false);
            binding.occasionChips.addView(chip);
        }

        binding.occasionNote.setVisibility(View.VISIBLE);
        Occasion primary = single.primaryOccasion();
        if (primary == null) {
            binding.occasionNote.setText(R.string.calendar_no_occasion);
        } else {
            binding.occasionNote.setText(getString(OccasionText.noteEn(primary))
                    + "  ·  " + getString(OccasionText.noteUr(primary)));
        }
    }

    // ---- recording ----------------------------------------------------------------------------

    private void recordSelection(int status) {
        if (selectedKeys.isEmpty()) {
            return;
        }
        CalendarDay single = soleSelectedDay();
        if (single != null && single.logged
                && (status == FastLog.STATUS_MISSED) == single.missed) {
            fastRepository.remove(single.millis, () -> showSnack(getString(R.string.fast_removed)));
            return;
        }

        int written = 0;
        for (int key : selectedKeys) {
            long millis = FastLog.millisOfDateKey(key);
            if (Occasions.isFastingForbidden(millis)) {
                continue;
            }
            fastRepository.log(millis, status, null, null, null);
            written++;
        }
        if (written == 0) {
            showSnack(getString(R.string.day_sheet_forbidden_warning));
            return;
        }
        showSnack(status == FastLog.STATUS_MISSED
                ? getResources().getQuantityString(R.plurals.marked_missed_count, written, written)
                : getResources().getQuantityString(R.plurals.recorded_count, written, written));
    }

    // ---- alarms -------------------------------------------------------------------------------

    private void setAlarmForSelection() {
        if (selectedKeys.isEmpty()) {
            return;
        }
        final List<Integer> keys = new ArrayList<>(selectedKeys);
        java.util.Collections.sort(keys);

        pickTime(3, 0, R.string.calendar_set_alarm, (hour, minute) -> {
            Alarm alarm = new Alarm();
            alarm.repeatMode = Alarm.REPEAT_DATES;
            alarm.setDateKeyList(keys);
            alarm.hour = hour;
            alarm.minute = minute;
            alarm.snoozeMinutes = Prefs.defaultSnooze(requireContext());
            alarm.label = keys.size() == 1
                    ? getString(R.string.alarm_label_single_day, TimeText.date(requireContext(),
                            FastLog.millisOfDateKey(keys.get(0))))
                    : getResources().getQuantityString(R.plurals.alarm_label_days,
                            keys.size(), keys.size());

            long next = Occurrences.next(alarm, System.currentTimeMillis());
            if (next == Occurrences.NONE) {
                showSnack(getString(R.string.error_pick_future_date));
                return;
            }
            String when = TimeText.dateTime(requireContext(), next);
            alarmRepository.save(alarm, id -> runOnUi(() -> {
                selectedKeys.clear();
                dayAdapter.updateSelection(selectedKeys);
                updateSelectionCard();
                showSnack(getString(R.string.saved_toast, when));
            }));
        });
    }

    private void buildShortcutChips() {
        binding.shortcutChips.removeAllViews();
        for (Occasion occasion : OccasionText.schedulable()) {
            Chip chip = new Chip(requireContext(), null,
                    com.google.android.material.R.attr.chipStyle);
            chip.setText(OccasionText.name(requireContext(), occasion));
            chip.setChipIconResource(OccasionText.icon(occasion));
            chip.setChipIconTint(android.content.res.ColorStateList.valueOf(
                    MaterialColors.getColor(binding.getRoot(),
                            OccasionText.categoryColorAttr(occasion.category()))));
            chip.setCheckable(false);
            chip.setOnClickListener(v -> setAlarmForOccasion(occasion));
            binding.shortcutChips.addView(chip);
        }
    }

    /**
     * Sets, or re-times, the alarm for one fasting occasion. Tapping the same shortcut twice moves
     * the alarm it already made rather than stacking a second one on top of it.
     */
    private void setAlarmForOccasion(@NonNull Occasion occasion) {
        Alarm existing = null;
        for (Alarm alarm : currentAlarms) {
            if (alarm.repeatMode == Alarm.REPEAT_OCCASION
                    && occasion.id().equals(alarm.occasionId)) {
                existing = alarm;
                break;
            }
        }
        final boolean isNew = existing == null;
        final Alarm target = isNew ? new Alarm() : Alarm.editableCopy(existing);

        pickTime(isNew ? 3 : target.hour, isNew ? 0 : target.minute,
                OccasionText.nameEn(occasion), (hour, minute) -> {
                    target.repeatMode = Alarm.REPEAT_OCCASION;
                    target.occasionId = occasion.id();
                    target.hour = hour;
                    target.minute = minute;
                    target.enabled = true;
                    if (isNew) {
                        target.label = OccasionText.name(requireContext(), occasion);
                        target.snoozeMinutes = Prefs.defaultSnooze(requireContext());
                    }

                    long next = Occurrences.next(target, System.currentTimeMillis());
                    if (next == Occurrences.NONE) {
                        showSnack(getString(R.string.preview_none));
                        return;
                    }
                    String when = TimeText.dateTime(requireContext(), next);
                    alarmRepository.save(target,
                            id -> runOnUi(() -> showSnack(getString(R.string.saved_toast, when))));
                });
    }

    @Override
    public void onAlarmClicked(@NonNull Alarm alarm) {
        pickTime(alarm.hour, alarm.minute, R.string.section_time, (hour, minute) -> {
            Alarm edited = Alarm.editableCopy(alarm);
            edited.hour = hour;
            edited.minute = minute;
            alarmRepository.save(edited, id -> runOnUi(() -> {
                long next = Occurrences.next(edited, System.currentTimeMillis());
                showSnack(next == Occurrences.NONE
                        ? getString(R.string.preview_none)
                        : getString(R.string.saved_toast,
                                TimeText.dateTime(requireContext(), next)));
            }));
        });
    }

    @Override
    public void onAlarmToggled(@NonNull Alarm alarm, boolean enabled) {
        alarmRepository.setEnabled(alarm.id, enabled, null);
    }

    @Override
    public void onAlarmLongClicked(@NonNull Alarm alarm) {
        String[] options = {
                getString(R.string.action_change_time),
                getString(R.string.action_rename),
                getString(R.string.action_delete)
        };
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.alarm_options_title)
                .setItems(options, (dialog, which) -> {
                    switch (which) {
                        case 0: onAlarmClicked(alarm); break;
                        case 1: renameAlarm(alarm); break;
                        default: deleteWithUndo(alarm); break;
                    }
                })
                .show();
    }

    private void renameAlarm(@NonNull Alarm alarm) {
        EditText input = new EditText(requireContext());
        input.setSingleLine(true);
        input.setHint(R.string.hint_alarm_label);
        input.setText(alarm.label);
        input.setSelection(alarm.label.length());

        int padding = Math.round(getResources().getDisplayMetrics().density * 24);
        LinearLayout wrapper = new LinearLayout(requireContext());
        wrapper.setPadding(padding, padding / 2, padding, 0);
        wrapper.addView(input, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.action_rename)
                .setView(wrapper)
                .setPositiveButton(R.string.action_save, (dialog, which) -> {
                    String typed = input.getText().toString().trim();
                    Alarm renamed = Alarm.editableCopy(alarm);
                    renamed.label = typed.isEmpty()
                            ? getString(R.string.default_alarm_label) : typed;
                    alarmRepository.save(renamed, null);
                })
                .setNegativeButton(R.string.perm_later, null)
                .show();
    }

    private void deleteWithUndo(@NonNull Alarm alarm) {
        alarmRepository.delete(alarm, null);
        if (binding == null) {
            return;
        }
        Snackbar.make(binding.calendarRoot, R.string.alarm_deleted, Snackbar.LENGTH_LONG)
                .setAction(R.string.action_undo,
                        v -> alarmRepository.save(Alarm.copyOf(alarm), null))
                .show();
    }

    // ---- pickers ------------------------------------------------------------------------------

    private interface TimeChosen {
        void onChosen(int hour, int minute);
    }

    private void pickTime(int hour, int minute, int titleRes, @NonNull TimeChosen callback) {
        MaterialTimePicker picker = new MaterialTimePicker.Builder()
                .setTimeFormat(DateFormat.is24HourFormat(requireContext())
                        ? TimeFormat.CLOCK_24H : TimeFormat.CLOCK_12H)
                .setHour(hour)
                .setMinute(minute)
                .setTitleText(titleRes)
                .build();
        picker.addOnPositiveButtonClickListener(
                v -> callback.onChosen(picker.getHour(), picker.getMinute()));
        picker.show(getChildFragmentManager(), "pick_time");
    }

    private void showSetDateDialog() {
        HijriDateDialog.show(requireContext(), corrections, offset -> {
            if (!isAdded() || binding == null) {
                return;
            }
            goToToday();
            showSnack(getString(R.string.hijri_set_applied,
                    HijriDates.formatEnglish(System.currentTimeMillis())));
        });
    }

    // ---- month adjustment ---------------------------------------------------------------------

    private void showMonthAdjustDialog() {
        final int span = HijriDates.MAX_OFFSET;
        final int optionCount = span * 2 + 1;
        String[] options = new String[optionCount];
        for (int i = 0; i < optionCount; i++) {
            int value = i - span;
            options[i] = value == 0
                    ? getString(R.string.calendar_adjust_none)
                    : getString(R.string.settings_hijri_offset_value, value);
        }
        int current = HijriDates.monthOffset(shownYear, shownMonth) + span;
        final int year = shownYear;
        final int month = shownMonth;

        ChoiceDialog.show(requireContext(), R.string.calendar_adjust_month,
                getString(R.string.calendar_adjust_month_message,
                        HijriDates.monthTitleEnglish(year, month)),
                options, current, R.string.hijri_set_apply,
                index -> corrections.setMonthOffset(year, month, index - span, () -> {
                    if (isAdded()) {
                        rebuildMonth();
                    }
                }));
    }

    // ---- helpers ------------------------------------------------------------------------------

    private void runOnUi(@NonNull Runnable action) {
        if (!isAdded()) {
            return;
        }
        requireActivity().runOnUiThread(() -> {
            if (isAdded() && binding != null) {
                action.run();
            }
        });
    }

    private void showSnack(@NonNull String message) {
        if (binding == null || !isAdded()) {
            return;
        }
        Snackbar.make(binding.calendarRoot, message, Snackbar.LENGTH_SHORT).show();
    }

    // ---- static chrome ------------------------------------------------------------------------

    private void buildWeekdayHeader() {
        binding.weekdayHeader.removeAllViews();
        int firstDayOfWeek = Calendar.getInstance().getFirstDayOfWeek();
        for (int i = 0; i < COLUMNS; i++) {
            int dayOfWeek = ((firstDayOfWeek - 1 + i) % 7) + 1;
            TextView label = new TextView(requireContext());
            label.setText(TimeText.weekDayName(requireContext(), dayOfWeek));
            label.setGravity(Gravity.CENTER);
            label.setTextSize(11f);
            label.setTypeface(label.getTypeface(), Typeface.BOLD);
            label.setTextColor(MaterialColors.getColor(binding.getRoot(),
                    com.google.android.material.R.attr.colorOnSurfaceVariant));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            label.setLayoutParams(params);
            binding.weekdayHeader.addView(label);
        }
    }

    private void buildLegend() {
        binding.legendColours.removeAllViews();
        addLegendDot(R.string.calendar_legend_obligatory, Occasion.Category.OBLIGATORY);
        addLegendDot(R.string.calendar_legend_recommended, Occasion.Category.RECOMMENDED);
        addLegendDot(R.string.calendar_legend_forbidden, Occasion.Category.FORBIDDEN);

        // The marks on a day mean nothing on their own, so each one is spelled out.
        binding.legendMarkers.removeAllViews();
        addLegendIcon(R.drawable.ic_check_circle, R.color.brand_green,
                R.string.calendar_legend_kept);
        addLegendIcon(R.drawable.ic_block, R.color.danger, R.string.calendar_legend_missed);
        addLegendIcon(R.drawable.ic_alarm, R.color.brand_gold, R.string.calendar_legend_alarm);
    }

    private void addLegendDot(int labelRes, @NonNull Occasion.Category category) {
        View dot = new View(requireContext());
        dot.setBackgroundResource(R.drawable.bg_dot);
        dot.getBackground().setTint(MaterialColors.getColor(binding.getRoot(),
                OccasionText.categoryColorAttr(category)));
        int size = Math.round(getResources().getDisplayMetrics().density * 8);
        LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(size, size);
        dotParams.rightMargin = size;
        dot.setLayoutParams(dotParams);
        binding.legendColours.addView(legendItem(dot, labelRes));
    }

    private void addLegendIcon(int iconRes, int tintRes, int labelRes) {
        android.widget.ImageView icon = new android.widget.ImageView(requireContext());
        icon.setImageResource(iconRes);
        icon.setImageTintList(
                androidx.core.content.ContextCompat.getColorStateList(requireContext(), tintRes));
        int size = Math.round(getResources().getDisplayMetrics().density * 14);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(size, size);
        iconParams.rightMargin = Math.round(getResources().getDisplayMetrics().density * 5);
        icon.setLayoutParams(iconParams);
        binding.legendMarkers.addView(legendItem(icon, labelRes));
    }

    @NonNull
    private LinearLayout legendItem(@NonNull View mark, int labelRes) {
        LinearLayout item = new LinearLayout(requireContext());
        item.setOrientation(LinearLayout.HORIZONTAL);
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView label = new TextView(requireContext());
        label.setText(labelRes);
        label.setTextSize(10f);
        label.setMaxLines(1);
        label.setEllipsize(android.text.TextUtils.TruncateAt.END);
        label.setTextColor(MaterialColors.getColor(binding.getRoot(),
                com.google.android.material.R.attr.colorOnSurfaceVariant));

        item.addView(mark);
        item.addView(label);
        return item;
    }
}
