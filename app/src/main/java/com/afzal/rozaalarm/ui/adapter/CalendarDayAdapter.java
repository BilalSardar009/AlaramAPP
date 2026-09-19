package com.afzal.rozaalarm.ui.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.afzal.rozaalarm.R;
import com.afzal.rozaalarm.databinding.ItemCalendarDayBinding;
import com.afzal.rozaalarm.util.Occasion;
import com.afzal.rozaalarm.util.OccasionText;
import com.google.android.material.color.MaterialColors;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Draws the month grid: Hijri day large, Gregorian day small, plus fast, alarm and occasion
 * markers. Any number of days can be selected at once, so one alarm can cover a whole run.
 */
public class CalendarDayAdapter extends RecyclerView.Adapter<CalendarDayAdapter.DayViewHolder> {

    public interface Listener {
        /** The user tapped a day; it should be added to or removed from the selection. */
        void onDayToggled(@NonNull CalendarDay day);
    }

    private final List<CalendarDay> days = new ArrayList<>();
    private final Set<Integer> selectedKeys = new HashSet<>();
    private final Listener listener;

    public CalendarDayAdapter(@NonNull Listener listener) {
        this.listener = listener;
    }

    public void submit(@NonNull List<CalendarDay> newDays, @NonNull Set<Integer> selection) {
        days.clear();
        days.addAll(newDays);
        selectedKeys.clear();
        selectedKeys.addAll(selection);
        notifyDataSetChanged();
    }

    /** Redraws only the cells whose selected state actually changed. */
    public void updateSelection(@NonNull Set<Integer> selection) {
        Set<Integer> previous = new HashSet<>(selectedKeys);
        selectedKeys.clear();
        selectedKeys.addAll(selection);
        for (int i = 0; i < days.size(); i++) {
            CalendarDay day = days.get(i);
            if (day.blank) {
                continue;
            }
            if (previous.contains(day.dateKey) != selectedKeys.contains(day.dateKey)) {
                notifyItemChanged(i);
            }
        }
    }

    @NonNull
    @Override
    public DayViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new DayViewHolder(ItemCalendarDayBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull DayViewHolder holder, int position) {
        CalendarDay day = days.get(position);
        holder.bind(day, !day.blank && selectedKeys.contains(day.dateKey), listener);
    }

    @Override
    public int getItemCount() {
        return days.size();
    }

    static class DayViewHolder extends RecyclerView.ViewHolder {

        private final ItemCalendarDayBinding binding;

        DayViewHolder(@NonNull ItemCalendarDayBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(@NonNull CalendarDay day, boolean selected, @NonNull Listener listener) {
            Context context = binding.getRoot().getContext();

            if (day.blank) {
                binding.dayHijri.setText("");
                binding.dayGregorian.setText("");
                binding.dayHighlight.setVisibility(View.INVISIBLE);
                binding.dayDot.setVisibility(View.GONE);
                binding.dayLogged.setVisibility(View.GONE);
                binding.dayAlarm.setVisibility(View.GONE);
                binding.dayRoot.setClickable(false);
                binding.dayRoot.setOnClickListener(null);
                return;
            }

            binding.dayRoot.setClickable(true);
            binding.dayHijri.setText(String.valueOf(day.hijriDay));
            binding.dayGregorian.setText(String.valueOf(day.gregorianDay));

            int onSurface = MaterialColors.getColor(binding.getRoot(),
                    com.google.android.material.R.attr.colorOnSurface);
            int onPrimary = MaterialColors.getColor(binding.getRoot(),
                    com.google.android.material.R.attr.colorOnPrimary);

            if (selected) {
                binding.dayHighlight.setVisibility(View.VISIBLE);
                binding.dayHighlight.setBackgroundResource(R.drawable.bg_calendar_day_selected);
                binding.dayHighlight.getBackground().setTint(MaterialColors.getColor(
                        binding.getRoot(), com.google.android.material.R.attr.colorPrimary));
                binding.dayHijri.setTextColor(onPrimary);
                binding.dayGregorian.setTextColor(onPrimary);
            } else if (day.today) {
                binding.dayHighlight.setVisibility(View.VISIBLE);
                binding.dayHighlight.setBackgroundResource(R.drawable.bg_calendar_day_today);
                binding.dayHighlight.getBackground().setTint(MaterialColors.getColor(
                        binding.getRoot(), com.google.android.material.R.attr.colorPrimary));
                binding.dayHijri.setTextColor(onSurface);
                binding.dayGregorian.setTextColor(onSurface);
            } else {
                binding.dayHighlight.setVisibility(View.GONE);
                binding.dayHijri.setTextColor(onSurface);
                binding.dayGregorian.setTextColor(MaterialColors.getColor(binding.getRoot(),
                        com.google.android.material.R.attr.colorOnSurfaceVariant));
            }

            Occasion occasion = day.primaryOccasion();
            if (occasion == null) {
                binding.dayDot.setVisibility(View.GONE);
            } else {
                binding.dayDot.setVisibility(View.VISIBLE);
                binding.dayDot.getBackground().setTint(MaterialColors.getColor(
                        binding.getRoot(),
                        OccasionText.categoryColorAttr(occasion.category())));
            }

            if (day.logged) {
                binding.dayLogged.setVisibility(View.VISIBLE);
                binding.dayLogged.setImageResource(day.missed
                        ? R.drawable.ic_block : R.drawable.ic_check_circle);
                binding.dayLogged.setImageTintList(ContextCompat.getColorStateList(context,
                        day.missed ? R.color.danger : R.color.brand_green));
            } else {
                binding.dayLogged.setVisibility(View.GONE);
            }

            binding.dayAlarm.setVisibility(day.alarmed ? View.VISIBLE : View.GONE);

            binding.dayRoot.setOnClickListener(v -> listener.onDayToggled(day));
        }
    }
}
