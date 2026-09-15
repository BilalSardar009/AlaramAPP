package com.afzal.rozaalarm.ui;

import android.content.Intent;
import android.graphics.Canvas;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.afzal.rozaalarm.R;
import com.afzal.rozaalarm.data.Alarm;
import com.afzal.rozaalarm.data.AlarmRepository;
import com.afzal.rozaalarm.databinding.FragmentAlarmsBinding;
import com.afzal.rozaalarm.ui.adapter.AlarmAdapter;
import com.afzal.rozaalarm.util.HijriDates;
import com.afzal.rozaalarm.util.Occurrences;
import com.afzal.rozaalarm.util.TimeText;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.List;

/** The alarms tab: a hero with today's dates and the next firing, plus the list of alarms. */
public class AlarmsFragment extends Fragment implements AlarmAdapter.Listener {

    private static final long TICK_INTERVAL_MS = 30_000L;

    private FragmentAlarmsBinding binding;
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

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentAlarmsBinding.inflate(inflater, container, false);
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
        attachSwipeToDelete();
        keepFabOutOfTheWay();

        binding.addAlarmFab.setOnClickListener(v -> openEditor(0L));
        binding.nextAlarmCard.setOnClickListener(v -> scrollToNextAlarm());

        repository.observeAll().observe(getViewLifecycleOwner(), alarms -> {
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
    public void onResume() {
        super.onResume();
        updateHero();
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

    // ---- hero ---------------------------------------------------------------------------------

    private void updateHero() {
        if (binding == null) {
            return;
        }
        long now = System.currentTimeMillis();

        binding.todayDate.setText(TimeText.fullDate(requireContext(), now));
        binding.todayHijri.setText(
                HijriDates.formatEnglish(now) + "   ·   " + HijriDates.formatUrdu(now));

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
            binding.nextAlarmCountdown.setText(R.string.next_alarm_none_hint);
        } else {
            String label = soonest.label.trim().isEmpty()
                    ? getString(R.string.default_alarm_label)
                    : soonest.label;
            binding.nextAlarmLabel.setText(
                    label + " · " + TimeText.dateTime(requireContext(), soonestTime));
            binding.nextAlarmCountdown.setText(
                    TimeText.countdown(requireContext(), soonestTime, now));
        }
    }

    private void scrollToNextAlarm() {
        long now = System.currentTimeMillis();
        int bestIndex = -1;
        long bestTime = Long.MAX_VALUE;
        for (int i = 0; i < currentAlarms.size(); i++) {
            Alarm alarm = currentAlarms.get(i);
            if (!alarm.enabled) {
                continue;
            }
            long next = Occurrences.next(alarm, now);
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
        repository.setEnabled(alarm.id, enabled, id -> {
            if (isAdded()) {
                requireActivity().runOnUiThread(this::updateHero);
            }
        });
    }

    @Override
    public void onAlarmLongClicked(@NonNull Alarm alarm) {
        String[] options = {
                getString(R.string.action_edit),
                getString(R.string.action_duplicate),
                getString(R.string.action_delete)
        };
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.alarm_options_title)
                .setItems(options, (dialog, which) -> {
                    switch (which) {
                        case 0: openEditor(alarm.id); break;
                        case 1: duplicate(alarm); break;
                        case 2: deleteWithUndo(alarm); break;
                        default: break;
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
        Snackbar.make(binding.alarmsRoot, R.string.alarm_deleted, Snackbar.LENGTH_LONG)
                .setAnchorView(binding.addAlarmFab)
                .setAction(R.string.action_undo, v -> repository.save(Alarm.copyOf(alarm), null))
                .show();
    }

    private void openEditor(long alarmId) {
        startActivity(new Intent(requireContext(), AlarmEditActivity.class)
                .putExtra(AlarmEditActivity.EXTRA_ALARM_ID, alarmId));
        requireActivity().overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
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
                    public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
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
                if (binding == null) {
                    return;
                }
                if (dy > 8 && binding.addAlarmFab.isExtended()) {
                    binding.addAlarmFab.shrink();
                } else if (dy < -8 && !binding.addAlarmFab.isExtended()) {
                    binding.addAlarmFab.extend();
                }
            }
        });
    }
}
