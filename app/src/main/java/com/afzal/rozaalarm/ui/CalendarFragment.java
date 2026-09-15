package com.afzal.rozaalarm.ui;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.afzal.rozaalarm.R;
import com.afzal.rozaalarm.data.FastLog;
import com.afzal.rozaalarm.data.FastRepository;
import com.afzal.rozaalarm.data.HijriCorrections;
import com.afzal.rozaalarm.databinding.FragmentCalendarBinding;
import com.afzal.rozaalarm.ui.adapter.CalendarDay;
import com.afzal.rozaalarm.ui.adapter.CalendarDayAdapter;
import com.afzal.rozaalarm.util.HijriDates;
import com.afzal.rozaalarm.util.Occasion;
import com.afzal.rozaalarm.util.OccasionText;
import com.afzal.rozaalarm.util.Occasions;
import com.afzal.rozaalarm.util.TimeText;
import com.google.android.material.chip.Chip;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The Islamic calendar: one Hijri month at a time, every day marked with the fasts that fall on
 * it, and each day openable to record a fast or read what the day is.
 */
public class CalendarFragment extends Fragment implements CalendarDayAdapter.Listener {

    private static final String ARG_YEAR = "hijri_year";
    private static final String ARG_MONTH = "hijri_month";

    private static final int COLUMNS = 7;

    private FragmentCalendarBinding binding;
    private CalendarDayAdapter adapter;
    private FastRepository fastRepository;
    private HijriCorrections corrections;

    private int shownYear;
    private int shownMonth;

    private final List<CalendarDay> gridDays = new ArrayList<>();
    private final Set<Integer> keptDateKeys = new HashSet<>();
    private final Set<Integer> missedDateKeys = new HashSet<>();

    @Nullable
    private CalendarDay selectedDay;

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
        corrections = HijriCorrections.get(requireContext());

        long now = System.currentTimeMillis();
        int argYear = getArguments() == null ? -1 : getArguments().getInt(ARG_YEAR, -1);
        int argMonth = getArguments() == null ? -1 : getArguments().getInt(ARG_MONTH, -1);
        shownYear = argYear > 0 ? argYear : HijriDates.year(now);
        shownMonth = argMonth >= 0 && argMonth <= 11 ? argMonth : HijriDates.month(now);

        adapter = new CalendarDayAdapter(this);
        binding.dayGrid.setLayoutManager(new GridLayoutManager(requireContext(), COLUMNS));
        binding.dayGrid.setAdapter(adapter);
        binding.dayGrid.setItemAnimator(null);

        buildWeekdayHeader();
        buildLegend();

        binding.prevMonth.setOnClickListener(v -> stepMonth(-1));
        binding.nextMonth.setOnClickListener(v -> stepMonth(1));
        binding.toolbar.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_today) {
                goToToday();
                return true;
            }
            if (id == R.id.action_adjust_month) {
                showMonthAdjustDialog();
                return true;
            }
            return false;
        });

        binding.recordButton.setOnClickListener(v -> toggleSelectedDay(FastLog.STATUS_KEPT));
        binding.missedButton.setOnClickListener(v -> toggleSelectedDay(FastLog.STATUS_MISSED));
        binding.alarmButton.setOnClickListener(v -> openAlarmForSelectedDay());

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
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
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
        selectedDay = null;
        rebuildMonth();
    }

    private void goToToday() {
        long now = System.currentTimeMillis();
        shownYear = HijriDates.year(now);
        shownMonth = HijriDates.month(now);
        selectedDay = null;
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

        int selectIndex = RecyclerView.NO_POSITION;
        for (int day = 1; day <= monthLength; day++) {
            Calendar dayCal = Calendar.getInstance();
            dayCal.setTimeInMillis(monthStart);
            dayCal.add(Calendar.DAY_OF_MONTH, day - 1);
            long millis = dayCal.getTimeInMillis();
            int dateKey = FastLog.dateKeyOf(dayCal);

            // The occasions come from the grid's own Hijri coordinates rather than from a second
            // conversion, so the labels can never disagree with the cell they sit in.
            List<Occasion> occasions = Occasions.forHijriDay(
                    shownMonth, day, dayCal.get(Calendar.DAY_OF_WEEK));

            CalendarDay cell = new CalendarDay(day, dayCal.get(Calendar.DAY_OF_MONTH), millis,
                    dateKey == todayKey, occasions);
            cell.logged = keptDateKeys.contains(dateKey) || missedDateKeys.contains(dateKey);
            cell.missed = missedDateKeys.contains(dateKey);
            gridDays.add(cell);

            boolean isSelectionTarget = selectedDay == null
                    ? cell.today
                    : FastLog.dateKeyOf(selectedDay.millis) == dateKey;
            if (isSelectionTarget) {
                selectIndex = gridDays.size() - 1;
            }
        }

        // Landing on a month that has no "today" opens on its first day.
        if (selectIndex == RecyclerView.NO_POSITION && !gridDays.isEmpty()) {
            selectIndex = lead;
        }

        adapter.submit(new ArrayList<>(gridDays), selectIndex);
        selectedDay = selectIndex >= 0 && selectIndex < gridDays.size()
                ? gridDays.get(selectIndex) : null;

        updateMonthHeader(monthStart, monthLength);
        updateSelectedDayCard();
    }

    private void updateMonthHeader(long monthStart, int monthLength) {
        binding.monthTitle.setText(HijriDates.monthTitleEnglish(shownYear, shownMonth));
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
    public void onDaySelected(@NonNull CalendarDay day) {
        selectedDay = day;
        updateSelectedDayCard();
    }

    private void updateSelectedDayCard() {
        if (binding == null || selectedDay == null) {
            return;
        }
        CalendarDay day = selectedDay;

        binding.selectedGregorian.setText(TimeText.fullDate(requireContext(), day.millis));
        binding.selectedHijri.setText(
                day.hijriDay + " " + HijriDates.monthNameEnglish(shownMonth) + " " + shownYear
                        + " AH   ·   " + HijriDates.toUrduDigits(day.hijriDay) + " "
                        + HijriDates.monthNameUrdu(shownMonth) + " "
                        + HijriDates.toUrduDigits(shownYear) + "ھ");

        binding.occasionChips.removeAllViews();
        for (Occasion occasion : day.occasions) {
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

        Occasion primary = day.primaryOccasion();
        if (primary == null) {
            binding.occasionNote.setText(R.string.calendar_no_occasion);
        } else {
            binding.occasionNote.setText(getString(OccasionText.noteEn(primary))
                    + "  ·  " + getString(OccasionText.noteUr(primary)));
        }

        binding.forbiddenWarning.setVisibility(day.isForbidden() ? View.VISIBLE : View.GONE);

        boolean recorded = day.logged && !day.missed;
        binding.recordButton.setText(recorded
                ? R.string.day_sheet_remove : R.string.day_sheet_record);
        binding.recordButton.setIconResource(recorded
                ? R.drawable.ic_close : R.drawable.ic_check);
        binding.missedButton.setText(day.missed
                ? R.string.day_sheet_remove : R.string.day_sheet_missed);
        // Fasting is not permitted on Eid or the days of Tashreeq, so recording one is not offered.
        binding.recordButton.setEnabled(!day.isForbidden());
        binding.missedButton.setEnabled(!day.isForbidden());
    }

    private void toggleSelectedDay(int status) {
        if (selectedDay == null) {
            return;
        }
        final CalendarDay day = selectedDay;
        boolean alreadyThisStatus = day.logged
                && (status == FastLog.STATUS_MISSED) == day.missed;

        if (alreadyThisStatus) {
            fastRepository.remove(day.millis, () -> showSnack(getString(R.string.fast_removed)));
        } else {
            fastRepository.log(day.millis, status, null, null, () -> showSnack(
                    status == FastLog.STATUS_MISSED
                            ? getString(R.string.day_sheet_missed_recorded)
                            : getString(R.string.fast_logged,
                                    TimeText.date(requireContext(), day.millis))));
        }
    }

    private void openAlarmForSelectedDay() {
        if (selectedDay == null) {
            return;
        }
        startActivity(new Intent(requireContext(), AlarmEditActivity.class)
                .putExtra(AlarmEditActivity.EXTRA_ALARM_ID, 0L)
                .putExtra(AlarmEditActivity.EXTRA_PRESET_DATE, selectedDay.millis));
        requireActivity().overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
    }

    private void showSnack(@NonNull String message) {
        if (binding == null || !isAdded()) {
            return;
        }
        Snackbar.make(binding.calendarRoot, message, Snackbar.LENGTH_SHORT).show();
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

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.calendar_adjust_month_title,
                        HijriDates.monthTitleEnglish(year, month)))
                .setMessage(R.string.calendar_adjust_month_message)
                .setSingleChoiceItems(options, current, (dialog, which) -> {
                    dialog.dismiss();
                    corrections.setMonthOffset(year, month, which - span, () -> {
                        if (isAdded()) {
                            rebuildMonth();
                        }
                    });
                })
                .setNegativeButton(R.string.perm_later, null)
                .show();
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
        binding.legendRow.removeAllViews();
        addLegendItem(R.string.calendar_legend_obligatory, Occasion.Category.OBLIGATORY);
        addLegendItem(R.string.calendar_legend_recommended, Occasion.Category.RECOMMENDED);
        addLegendItem(R.string.calendar_legend_forbidden, Occasion.Category.FORBIDDEN);
    }

    private void addLegendItem(int labelRes, @NonNull Occasion.Category category) {
        LinearLayout item = new LinearLayout(requireContext());
        item.setOrientation(LinearLayout.HORIZONTAL);
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        View dot = new View(requireContext());
        dot.setBackgroundResource(R.drawable.bg_dot);
        dot.getBackground().setTint(MaterialColors.getColor(binding.getRoot(),
                OccasionText.categoryColorAttr(category)));
        int size = Math.round(getResources().getDisplayMetrics().density * 7);
        LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(size, size);
        dotParams.rightMargin = size;
        dot.setLayoutParams(dotParams);

        TextView label = new TextView(requireContext());
        label.setText(labelRes);
        label.setTextSize(10f);
        label.setTextColor(MaterialColors.getColor(binding.getRoot(),
                com.google.android.material.R.attr.colorOnSurfaceVariant));

        item.addView(dot);
        item.addView(label);
        binding.legendRow.addView(item);
    }
}
