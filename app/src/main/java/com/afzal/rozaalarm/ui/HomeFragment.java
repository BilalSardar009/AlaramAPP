package com.afzal.rozaalarm.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.afzal.rozaalarm.R;
import com.afzal.rozaalarm.data.Alarm;
import com.afzal.rozaalarm.data.AlarmRepository;
import com.afzal.rozaalarm.databinding.FragmentHomeBinding;
import com.afzal.rozaalarm.ui.adapter.AlarmAdapter;
import com.afzal.rozaalarm.util.HijriDates;
import com.afzal.rozaalarm.util.Occasion;
import com.afzal.rozaalarm.util.OccasionText;
import com.afzal.rozaalarm.util.OccasionWindow;
import com.afzal.rozaalarm.util.Occurrences;
import com.afzal.rozaalarm.util.TimeText;
import com.google.android.material.chip.Chip;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;

import java.util.ArrayList;
import java.util.List;

/**
 * The home screen: today's date, the alarm that rings next, the fasting days, and every alarm
 * that has been set.
 *
 * <p>Nothing is created here. Tapping a fasting day opens the calendar on the month it falls in
 * with its days already selected, so an alarm is always made from days you can see.</p>
 */
public class HomeFragment extends Fragment implements AlarmAdapter.Listener {

    private static final long TICK_INTERVAL_MS = 30_000L;

    private FragmentHomeBinding binding;
    private AlarmAdapter adapter;
    private AlarmRepository repository;

    private final Handler ticker = new Handler(Looper.getMainLooper());
    private final List<Alarm> currentAlarms = new ArrayList<>();

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            updateHeader();
            ticker.postDelayed(this, TICK_INTERVAL_MS);
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        repository = AlarmRepository.get(requireContext());

        binding.toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_settings) {
                startActivity(new Intent(requireContext(), SettingsActivity.class));
                requireActivity().overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
                return true;
            }
            return false;
        });

        adapter = new AlarmAdapter(this);
        binding.alarmList.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.alarmList.setAdapter(adapter);
        binding.alarmList.setItemAnimator(null);

        binding.openCalendarButton.setOnClickListener(v -> openCalendar(null));
        buildFastChips();

        repository.observeAll().observe(getViewLifecycleOwner(), alarms -> {
            currentAlarms.clear();
            currentAlarms.addAll(alarms);
            adapter.submitList(new ArrayList<>(alarms));
            binding.alarmsEmpty.setVisibility(alarms.isEmpty() ? View.VISIBLE : View.GONE);
            binding.alarmList.setVisibility(alarms.isEmpty() ? View.GONE : View.VISIBLE);
            updateHeader();
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        updateHeader();
        ticker.removeCallbacks(tick);
        ticker.postDelayed(tick, TICK_INTERVAL_MS);
    }

    @Override
    public void onPause() {
        ticker.removeCallbacks(tick);
        super.onPause();
    }

    @Override
    public void onDestroyView() {
        ticker.removeCallbacksAndMessages(null);
        binding = null;
        super.onDestroyView();
    }

    // ---- header -------------------------------------------------------------------------------

    private void updateHeader() {
        if (binding == null) {
            return;
        }
        long now = System.currentTimeMillis();

        binding.todayHijri.setText(HijriDates.formatEnglish(now));
        binding.todayHijriUrdu.setText(HijriDates.formatUrdu(now));
        binding.todayGregorian.setText(TimeText.fullDate(requireContext(), now));

        Alarm soonest = null;
        long soonestTime = Long.MAX_VALUE;
        for (Alarm alarm : currentAlarms) {
            if (!alarm.enabled) {
                continue;
            }
            long next = Occurrences.next(alarm, now);
            if (next != Occurrences.NONE && next < soonestTime) {
                soonestTime = next;
                soonest = alarm;
            }
        }

        if (soonest == null) {
            binding.nextAlarmLabel.setText(R.string.next_alarm_none);
            binding.nextAlarmWhen.setText(R.string.next_alarm_none_hint);
            return;
        }
        binding.nextAlarmLabel.setText(soonest.label.trim().isEmpty()
                ? getString(R.string.default_alarm_label) : soonest.label);
        binding.nextAlarmWhen.setText(TimeText.dateTime(requireContext(), soonestTime)
                + "  ·  " + TimeText.countdown(requireContext(), soonestTime, now));
    }

    // ---- the fasting days ---------------------------------------------------------------------

    private void buildFastChips() {
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
            chip.setOnClickListener(v -> openCalendar(occasion));
            binding.shortcutChips.addView(chip);
        }
    }

    /** Opens the calendar, on this fast's next month with its days selected when one is given. */
    private void openCalendar(@Nullable Occasion occasion) {
        if (!(requireActivity() instanceof MainActivity)) {
            return;
        }
        MainActivity host = (MainActivity) requireActivity();
        if (occasion == null) {
            host.showCalendar();
            return;
        }
        OccasionWindow.Window window =
                OccasionWindow.next(occasion, System.currentTimeMillis());
        if (window == null) {
            showSnack(getString(R.string.preview_none));
            return;
        }
        host.showCalendarMonth(window.hijriYear, window.hijriMonth, window.dateKeys);
    }

    // ---- the alarm list -----------------------------------------------------------------------

    @Override
    public void onAlarmClicked(@NonNull Alarm alarm) {
        MaterialTimePicker picker = new MaterialTimePicker.Builder()
                .setTimeFormat(DateFormat.is24HourFormat(requireContext())
                        ? TimeFormat.CLOCK_24H : TimeFormat.CLOCK_12H)
                .setHour(alarm.hour)
                .setMinute(alarm.minute)
                .setTitleText(R.string.section_time)
                .build();
        picker.addOnPositiveButtonClickListener(v -> {
            Alarm edited = Alarm.editableCopy(alarm);
            edited.hour = picker.getHour();
            edited.minute = picker.getMinute();
            repository.save(edited, id -> runOnUi(() -> {
                long next = Occurrences.next(edited, System.currentTimeMillis());
                showSnack(next == Occurrences.NONE
                        ? getString(R.string.preview_none)
                        : getString(R.string.saved_toast,
                                TimeText.dateTime(requireContext(), next)));
            }));
        });
        picker.show(getChildFragmentManager(), "alarm_time");
    }

    @Override
    public void onAlarmToggled(@NonNull Alarm alarm, boolean enabled) {
        repository.setEnabled(alarm.id, enabled, null);
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
                    repository.save(renamed, null);
                })
                .setNegativeButton(R.string.perm_later, null)
                .show();
    }

    private void deleteWithUndo(@NonNull Alarm alarm) {
        repository.delete(alarm, null);
        if (binding == null) {
            return;
        }
        Snackbar.make(binding.homeRoot, R.string.alarm_deleted, Snackbar.LENGTH_LONG)
                .setAction(R.string.action_undo, v -> repository.save(Alarm.copyOf(alarm), null))
                .show();
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
        Snackbar.make(binding.homeRoot, message, Snackbar.LENGTH_SHORT).show();
    }
}
