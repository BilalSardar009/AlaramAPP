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
import java.util.List;

/** Draws the month grid: Hijri day large, Gregorian day small, plus fast and occasion markers. */
public class CalendarDayAdapter extends RecyclerView.Adapter<CalendarDayAdapter.DayViewHolder> {

    public interface Listener {
        void onDaySelected(@NonNull CalendarDay day);
    }

    private final List<CalendarDay> days = new ArrayList<>();
    private final Listener listener;

    private int selectedPosition = RecyclerView.NO_POSITION;

    public CalendarDayAdapter(@NonNull Listener listener) {
        this.listener = listener;
    }

    public void submit(@NonNull List<CalendarDay> newDays, int selectIndex) {
        days.clear();
        days.addAll(newDays);
        selectedPosition = selectIndex;
        notifyDataSetChanged();
    }

    /** Selects a cell by grid position, redrawing only the two affected cells. */
    public void select(int position) {
        int previous = selectedPosition;
        selectedPosition = position;
        if (previous != RecyclerView.NO_POSITION) {
            notifyItemChanged(previous);
        }
        if (position != RecyclerView.NO_POSITION) {
            notifyItemChanged(position);
        }
    }

    public int selectedPosition() {
        return selectedPosition;
    }

    @NonNull
    @Override
    public DayViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new DayViewHolder(ItemCalendarDayBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull DayViewHolder holder, int position) {
        holder.bind(days.get(position), position == selectedPosition, listener, this);
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

        void bind(@NonNull CalendarDay day, boolean selected, @NonNull Listener listener,
                  @NonNull CalendarDayAdapter adapter) {
            Context context = binding.getRoot().getContext();

            if (day.blank) {
                binding.dayHijri.setText("");
                binding.dayGregorian.setText("");
                binding.dayHighlight.setVisibility(View.INVISIBLE);
                binding.dayDot.setVisibility(View.GONE);
                binding.dayLogged.setVisibility(View.GONE);
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

            binding.dayRoot.setOnClickListener(v -> {
                adapter.select(getBindingAdapterPosition());
                listener.onDaySelected(day);
            });
        }
    }
}
