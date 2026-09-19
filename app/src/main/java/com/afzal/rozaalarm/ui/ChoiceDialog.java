package com.afzal.rozaalarm.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.RadioButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.afzal.rozaalarm.R;
import com.afzal.rozaalarm.databinding.DialogChoiceBinding;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * A dialog that explains itself and then offers a list to choose from.
 *
 * <p>{@code AlertDialog} cannot do both: {@code setMessage} and {@code setSingleChoiceItems}
 * compete for the same slot and the message wins, so a dialog built with both shows the
 * explanation and no list at all. Everything here goes through one custom view instead, which also
 * lets an option run to two lines — the Islamic date in English above the same date in Urdu.</p>
 */
public final class ChoiceDialog {

    public interface OnChosen {
        void onChosen(int index);
    }

    private ChoiceDialog() {
    }

    /**
     * @param options    one entry per choice; a {@code \n} in an entry becomes a second line
     * @param checked    the index selected when the dialog opens, or -1 for none
     * @param onChosen   called with the chosen index when the user confirms
     */
    public static void show(@NonNull Context context, @StringRes int titleRes,
                            @Nullable CharSequence message, @NonNull String[] options,
                            int checked, @StringRes int confirmRes,
                            @NonNull OnChosen onChosen) {
        DialogChoiceBinding binding = DialogChoiceBinding.inflate(LayoutInflater.from(context));

        if (message == null || message.length() == 0) {
            binding.choiceMessage.setVisibility(View.GONE);
        } else {
            binding.choiceMessage.setText(message);
        }

        final int[] chosen = {checked};
        LayoutInflater inflater = LayoutInflater.from(context);
        for (int i = 0; i < options.length; i++) {
            RadioButton button = (RadioButton) inflater.inflate(
                    R.layout.item_choice_option, binding.choiceOptions, false);
            button.setId(View.generateViewId());
            button.setText(options[i]);
            button.setChecked(i == checked);
            final int index = i;
            button.setOnClickListener(v -> chosen[0] = index);
            binding.choiceOptions.addView(button);
        }

        new MaterialAlertDialogBuilder(context)
                .setTitle(titleRes)
                .setView(binding.getRoot())
                .setPositiveButton(confirmRes, (dialog, which) -> {
                    if (chosen[0] >= 0 && chosen[0] < options.length) {
                        onChosen.onChosen(chosen[0]);
                    }
                })
                .setNegativeButton(R.string.perm_later, null)
                .show();
    }
}
