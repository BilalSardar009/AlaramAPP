package com.afzal.rozaalarm.ui.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.afzal.rozaalarm.R;
import com.afzal.rozaalarm.data.FastLog;
import com.afzal.rozaalarm.databinding.ItemHistoryMonthBinding;
import com.afzal.rozaalarm.util.HijriDates;
import com.afzal.rozaalarm.util.Occasion;
import com.afzal.rozaalarm.util.OccasionText;
import com.afzal.rozaalarm.util.TimeText;

import java.util.ArrayList;
import java.util.List;

/** One row per month, expanding to show exactly which days were fasted. */
public class HistoryMonthAdapter extends RecyclerView.Adapter<HistoryMonthAdapter.MonthViewHolder> {

    public interface Listener {
        void onOpenInCalendar(@NonNull MonthGroup group);
    }

    private final List<MonthGroup> groups = new ArrayList<>();
    private final Listener listener;

    private int expandedPosition = RecyclerView.NO_POSITION;

    public HistoryMonthAdapter(@NonNull Listener listener) {
        this.listener = listener;
    }

    public void submit(@NonNull List<MonthGroup> newGroups) {
        groups.clear();
        groups.addAll(newGroups);
        expandedPosition = RecyclerView.NO_POSITION;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public MonthViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new MonthViewHolder(ItemHistoryMonthBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull MonthViewHolder holder, int position) {
        holder.bind(groups.get(position), position == expandedPosition, this, listener);
    }

    @Override
    public int getItemCount() {
        return groups.size();
    }

    void toggleExpanded(int position) {
        int previous = expandedPosition;
        expandedPosition = previous == position ? RecyclerView.NO_POSITION : position;
        if (previous != RecyclerView.NO_POSITION) {
            notifyItemChanged(previous);
        }
        if (expandedPosition != RecyclerView.NO_POSITION) {
            notifyItemChanged(expandedPosition);
        }
    }

    static class MonthViewHolder extends RecyclerView.ViewHolder {

        private final ItemHistoryMonthBinding binding;

        MonthViewHolder(@NonNull ItemHistoryMonthBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(@NonNull MonthGroup group, boolean expanded,
                  @NonNull HistoryMonthAdapter adapter, @NonNull Listener listener) {
            Context context = binding.getRoot().getContext();

            binding.monthCount.setText(String.valueOf(group.count()));
            binding.monthName.setText(titleOf(context, group));
            binding.monthSubtitle.setText(subtitleOf(context, group));

            binding.expandArea.setVisibility(expanded ? View.VISIBLE : View.GONE);
            binding.expandIcon.setImageResource(
                    expanded ? R.drawable.ic_expand_less : R.drawable.ic_expand_more);

            if (expanded) {
                binding.monthDates.setText(datesOf(context, group));
            }

            binding.monthCard.setOnClickListener(
                    v -> adapter.toggleExpanded(getBindingAdapterPosition()));
            binding.openInCalendar.setOnClickListener(v -> listener.onOpenInCalendar(group));
            // Only Hijri groups map onto a single calendar month.
            binding.openInCalendar.setVisibility(group.hijri ? View.VISIBLE : View.GONE);
        }

        @NonNull
        private String titleOf(@NonNull Context context, @NonNull MonthGroup group) {
            if (group.hijri) {
                return HijriDates.monthTitleEnglish(group.year, group.month);
            }
            // Month is 1-12 here; build an instant in it just to format the name.
            java.util.Calendar cal = java.util.Calendar.getInstance();
            cal.clear();
            cal.set(group.year, group.month - 1, 1);
            return android.text.format.DateFormat
                    .format("MMMM yyyy", cal.getTimeInMillis()).toString();
        }

        @NonNull
        private String subtitleOf(@NonNull Context context, @NonNull MonthGroup group) {
            String countText = context.getResources().getQuantityString(
                    R.plurals.fasts_count, group.count(), group.count());
            if (group.hijri) {
                return HijriDates.monthTitleUrdu(group.year, group.month) + "  ·  " + countText;
            }
            return countText;
        }

        @NonNull
        private String datesOf(@NonNull Context context, @NonNull MonthGroup group) {
            StringBuilder sb = new StringBuilder();
            List<FastLog> logs = new ArrayList<>(group.logs);
            java.util.Collections.sort(logs, (a, b) -> Integer.compare(a.dateKey, b.dateKey));
            for (FastLog log : logs) {
                if (sb.length() > 0) {
                    sb.append('\n');
                }
                long millis = FastLog.millisOfDateKey(log.dateKey);
                sb.append(TimeText.date(context, millis))
                        .append("  ·  ")
                        .append(log.hijriDay)
                        .append(' ')
                        .append(HijriDates.monthNameEnglish(log.hijriMonth));

                Occasion occasion = Occasion.fromId(log.occasionId);
                if (occasion != null) {
                    sb.append("  ·  ").append(OccasionText.name(context, occasion));
                }
                if (log.status == FastLog.STATUS_MISSED) {
                    sb.append("  (").append(context.getString(R.string.day_sheet_missed_recorded))
                            .append(')');
                }
            }
            return sb.toString();
        }
    }
}
