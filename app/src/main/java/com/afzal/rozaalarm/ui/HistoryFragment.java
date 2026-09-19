package com.afzal.rozaalarm.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.afzal.rozaalarm.R;
import com.afzal.rozaalarm.data.FastLog;
import com.afzal.rozaalarm.data.FastRepository;
import com.afzal.rozaalarm.databinding.FragmentHistoryBinding;
import com.afzal.rozaalarm.ui.adapter.HistoryMonthAdapter;
import com.afzal.rozaalarm.ui.adapter.MonthGroup;
import com.afzal.rozaalarm.util.HijriDates;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The record of fasts kept, counted by month. Tapping a month opens the exact dates; the button
 * inside jumps to that month on the calendar.
 */
public class HistoryFragment extends Fragment implements HistoryMonthAdapter.Listener {

    private FragmentHistoryBinding binding;
    private HistoryMonthAdapter adapter;

    private final List<FastLog> allLogs = new ArrayList<>();
    private boolean groupByHijri = true;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentHistoryBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        adapter = new HistoryMonthAdapter(this);
        binding.monthList.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.monthList.setAdapter(adapter);
        binding.monthList.setNestedScrollingEnabled(false);

        binding.groupToggle.check(R.id.groupHijri);
        binding.groupToggle.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) {
                return;
            }
            groupByHijri = checkedId == R.id.groupHijri;
            rebuild();
        });

        FastRepository.get(requireContext()).observeAll()
                .observe(getViewLifecycleOwner(), logs -> {
                    allLogs.clear();
                    allLogs.addAll(logs);
                    rebuild();
                });
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }

    // ---- building -----------------------------------------------------------------------------

    private void rebuild() {
        if (binding == null) {
            return;
        }

        List<FastLog> kept = new ArrayList<>();
        Set<Integer> keptKeys = new HashSet<>();
        for (FastLog log : allLogs) {
            if (log.status == FastLog.STATUS_KEPT) {
                kept.add(log);
                keptKeys.add(log.dateKey);
            }
        }

        updateSummary(kept, keptKeys);

        // A LinkedHashMap keeps insertion order stable before the explicit sort below.
        Map<Integer, MonthGroup> byMonth = new LinkedHashMap<>();
        for (FastLog log : kept) {
            final int year;
            final int month;
            if (groupByHijri) {
                year = log.hijriYear;
                month = log.hijriMonth;
            } else {
                year = log.dateKey / 10000;
                month = (log.dateKey / 100) % 100;
            }
            int key = year * 12 + month;
            MonthGroup group = byMonth.get(key);
            if (group == null) {
                group = new MonthGroup(year, month, groupByHijri);
                byMonth.put(key, group);
            }
            group.logs.add(log);
        }

        List<MonthGroup> groups = new ArrayList<>(byMonth.values());
        Collections.sort(groups, (a, b) -> Integer.compare(b.sortKey(), a.sortKey()));
        adapter.submit(groups);

        boolean empty = groups.isEmpty();
        binding.historyEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        binding.monthList.setVisibility(empty ? View.GONE : View.VISIBLE);
        binding.groupToggle.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    private void updateSummary(@NonNull List<FastLog> kept, @NonNull Set<Integer> keptKeys) {
        binding.statTotal.setText(String.valueOf(kept.size()));

        int thisHijriYear = HijriDates.year(System.currentTimeMillis());
        int inYear = 0;
        for (FastLog log : kept) {
            if (log.hijriYear == thisHijriYear) {
                inYear++;
            }
        }
        binding.statYear.setText(String.valueOf(inYear));
        binding.statStreak.setText(String.valueOf(FastRepository.streakFrom(keptKeys)));
    }

    @Override
    public void onOpenInCalendar(@NonNull MonthGroup group) {
        if (!group.hijri) {
            return;
        }
        if (requireActivity() instanceof MainActivity) {
            ((MainActivity) requireActivity()).showCalendarMonth(group.year, group.month, null);
        }
    }
}
