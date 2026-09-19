package com.afzal.rozaalarm.ui.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.afzal.rozaalarm.R;
import com.afzal.rozaalarm.alarm.AlarmScheduler;
import com.afzal.rozaalarm.data.Alarm;
import com.afzal.rozaalarm.databinding.ItemAlarmBinding;
import com.afzal.rozaalarm.util.Occurrences;
import com.afzal.rozaalarm.util.TimeText;

/** Renders the alarm cards on the home screen. */
public class AlarmAdapter extends ListAdapter<Alarm, AlarmAdapter.AlarmViewHolder> {

    public interface Listener {
        void onAlarmClicked(@NonNull Alarm alarm);

        void onAlarmToggled(@NonNull Alarm alarm, boolean enabled);

        void onAlarmLongClicked(@NonNull Alarm alarm);
    }

    private static final DiffUtil.ItemCallback<Alarm> DIFF = new DiffUtil.ItemCallback<Alarm>() {
        @Override
        public boolean areItemsTheSame(@NonNull Alarm oldItem, @NonNull Alarm newItem) {
            return oldItem.id == newItem.id;
        }

        @Override
        public boolean areContentsTheSame(@NonNull Alarm oldItem, @NonNull Alarm newItem) {
            return oldItem.enabled == newItem.enabled
                    && oldItem.hour == newItem.hour
                    && oldItem.minute == newItem.minute
                    && oldItem.repeatMode == newItem.repeatMode
                    && oldItem.dateKeys.equals(newItem.dateKeys)
                    && objectsEqual(oldItem.occasionId, newItem.occasionId)
                    && oldItem.label.equals(newItem.label)
                    && oldItem.preReminderEnabled == newItem.preReminderEnabled
                    && oldItem.preReminderDaysBefore == newItem.preReminderDaysBefore
                    && oldItem.preReminderHour == newItem.preReminderHour
                    && oldItem.preReminderMinute == newItem.preReminderMinute;
        }
    };

    private static boolean objectsEqual(@Nullable String first, @Nullable String second) {
        return first == null ? second == null : first.equals(second);
    }

    private final Listener listener;

    public AlarmAdapter(@NonNull Listener listener) {
        super(DIFF);
        this.listener = listener;
        setHasStableIds(true);
    }

    @Override
    public long getItemId(int position) {
        return getItem(position).id;
    }

    @NonNull
    @Override
    public AlarmViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemAlarmBinding binding = ItemAlarmBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new AlarmViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull AlarmViewHolder holder, int position) {
        holder.bind(getItem(position), listener);
    }

    @NonNull
    public Alarm alarmAt(int position) {
        return getItem(position);
    }

    static class AlarmViewHolder extends RecyclerView.ViewHolder {

        private final ItemAlarmBinding binding;

        AlarmViewHolder(@NonNull ItemAlarmBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(@NonNull Alarm alarm, @NonNull Listener listener) {
            Context context = binding.getRoot().getContext();

            binding.alarmTime.setText(TimeText.clockDigits(context, alarm.hour, alarm.minute));
            String meridiem = TimeText.clockSuffix(context, alarm.hour);
            binding.alarmMeridiem.setText(meridiem);
            binding.alarmMeridiem.setVisibility(meridiem.isEmpty() ? View.GONE : View.VISIBLE);

            binding.alarmLabel.setText(alarm.label.trim().isEmpty()
                    ? context.getString(R.string.default_alarm_label)
                    : alarm.label);
            binding.alarmRepeat.setText(TimeText.repeatSummary(context, alarm));

            long next = AlarmScheduler.nextTrigger(context, alarm);
            if (!alarm.enabled) {
                binding.alarmNext.setText(R.string.alarm_turned_off);
            } else if (next == Occurrences.NONE) {
                binding.alarmNext.setText(R.string.alarm_expired);
            } else {
                binding.alarmNext.setText(context.getString(R.string.alarm_scheduled_for,
                        TimeText.dateTime(context, next)));
            }

            if (alarm.preReminderEnabled && alarm.enabled) {
                binding.reminderChip.setVisibility(View.VISIBLE);
                String daysText = context.getResources().getQuantityString(
                        R.plurals.days_count,
                        alarm.preReminderDaysBefore,
                        alarm.preReminderDaysBefore);
                binding.reminderChip.setText(context.getString(R.string.chip_reminder, daysText,
                        TimeText.time(context, alarm.preReminderHour, alarm.preReminderMinute)));
            } else {
                binding.reminderChip.setVisibility(View.GONE);
            }

            // Dim the whole card when the alarm is off so the active ones stand out.
            float contentAlpha = alarm.enabled ? 1f : 0.55f;
            binding.alarmTime.setAlpha(contentAlpha);
            binding.alarmMeridiem.setAlpha(contentAlpha);
            binding.alarmLabel.setAlpha(contentAlpha);
            binding.alarmRepeat.setAlpha(contentAlpha);
            binding.alarmNext.setAlpha(contentAlpha);
            binding.activeSpine.setVisibility(alarm.enabled ? View.VISIBLE : View.INVISIBLE);

            binding.alarmSwitch.setOnCheckedChangeListener(null);
            binding.alarmSwitch.setChecked(alarm.enabled);
            binding.alarmSwitch.setOnCheckedChangeListener(
                    (button, isChecked) -> listener.onAlarmToggled(alarm, isChecked));

            binding.alarmCard.setOnClickListener(v -> listener.onAlarmClicked(alarm));
            binding.alarmCard.setOnLongClickListener(v -> {
                listener.onAlarmLongClicked(alarm);
                return true;
            });
        }
    }
}
