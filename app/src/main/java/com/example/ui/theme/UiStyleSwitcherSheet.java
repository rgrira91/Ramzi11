package com.example.ui.theme;

import android.app.Activity;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;
import com.example.R;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;

public final class UiStyleSwitcherSheet {

    public interface OnStudioActionListener {
        void onPreviewStyle(UiStyleMode mode);
        void onAssignStyle(UiStyleMode mode);
        void onToggleFloatingCollapsed(boolean collapsed);
        void onToggleShowEqDeck(boolean showEq);
        void onOpenAudioFxStudio();
    }

    private UiStyleSwitcherSheet() {}

    public static void show(
            Activity activity,
            UiStyleMode initialAssignedMode,
            boolean floatingCollapsed,
            boolean showEqDeck,
            OnStudioActionListener listener) {
        BottomSheetDialog dialog = new BottomSheetDialog(activity, R.style.NeonBottomSheetTheme);
        View view = activity.getLayoutInflater().inflate(R.layout.dialog_ui_switcher, null);
        dialog.setContentView(view);

        View cardNeon = view.findViewById(R.id.ui_card_neon);
        View cardFloating = view.findViewById(R.id.ui_card_floating);
        View cardRetro = view.findViewById(R.id.ui_card_retro);
        View cardLiquid = view.findViewById(R.id.ui_card_liquid);

        ImageView checkNeon = view.findViewById(R.id.ui_check_neon);
        ImageView checkFloating = view.findViewById(R.id.ui_check_floating);
        ImageView checkRetro = view.findViewById(R.id.ui_check_retro);
        ImageView checkLiquid = view.findViewById(R.id.ui_check_liquid);

        View rowFloatingToggle = view.findViewById(R.id.row_toggle_floating_mini);
        MaterialSwitch switchFloatingCollapsed = view.findViewById(R.id.switch_floating_collapsed);
        MaterialSwitch switchShowEqDeck = view.findViewById(R.id.switch_show_eq_deck);
        MaterialButton btnAudioFx = view.findViewById(R.id.btn_open_audio_studio);
        MaterialButton btnAssign = view.findViewById(R.id.btn_assign_ui_style);

        final UiStyleMode[] selectedHolder = {initialAssignedMode != null ? initialAssignedMode : UiStyleMode.NEON_COSMIC};
        final boolean[] committed = {false};

        Runnable renderSelection = () -> {
            UiStyleMode sel = selectedHolder[0];
            cardNeon.setBackgroundResource(sel == UiStyleMode.NEON_COSMIC
                    ? R.drawable.bg_ui_card_selected : R.drawable.bg_ui_card_normal);
            cardFloating.setBackgroundResource(sel == UiStyleMode.FLOATING_MINI
                    ? R.drawable.bg_ui_card_selected : R.drawable.bg_ui_card_normal);
            cardRetro.setBackgroundResource(sel == UiStyleMode.RETRO_VINYL
                    ? R.drawable.bg_ui_card_selected : R.drawable.bg_ui_card_normal);
            cardLiquid.setBackgroundResource(sel == UiStyleMode.LIQUID_GLASS
                    ? R.drawable.bg_ui_card_selected : R.drawable.bg_ui_card_normal);

            checkNeon.setVisibility(sel == UiStyleMode.NEON_COSMIC ? View.VISIBLE : View.GONE);
            checkFloating.setVisibility(sel == UiStyleMode.FLOATING_MINI ? View.VISIBLE : View.GONE);
            checkRetro.setVisibility(sel == UiStyleMode.RETRO_VINYL ? View.VISIBLE : View.GONE);
            checkLiquid.setVisibility(sel == UiStyleMode.LIQUID_GLASS ? View.VISIBLE : View.GONE);

            rowFloatingToggle.setVisibility(sel == UiStyleMode.FLOATING_MINI ? View.VISIBLE : View.GONE);
        };

        renderSelection.run();

        cardNeon.setOnClickListener(v -> {
            selectedHolder[0] = UiStyleMode.NEON_COSMIC;
            renderSelection.run();
            listener.onPreviewStyle(selectedHolder[0]);
        });
        cardFloating.setOnClickListener(v -> {
            selectedHolder[0] = UiStyleMode.FLOATING_MINI;
            renderSelection.run();
            listener.onPreviewStyle(selectedHolder[0]);
        });
        cardRetro.setOnClickListener(v -> {
            selectedHolder[0] = UiStyleMode.RETRO_VINYL;
            renderSelection.run();
            listener.onPreviewStyle(selectedHolder[0]);
        });
        cardLiquid.setOnClickListener(v -> {
            selectedHolder[0] = UiStyleMode.LIQUID_GLASS;
            renderSelection.run();
            listener.onPreviewStyle(selectedHolder[0]);
        });

        switchFloatingCollapsed.setChecked(floatingCollapsed);
        switchFloatingCollapsed.setOnCheckedChangeListener((b, isChecked) ->
                listener.onToggleFloatingCollapsed(isChecked));

        switchShowEqDeck.setChecked(showEqDeck);
        switchShowEqDeck.setOnCheckedChangeListener((b, isChecked) ->
                listener.onToggleShowEqDeck(isChecked));

        btnAudioFx.setOnClickListener(v -> {
            committed[0] = true;
            listener.onAssignStyle(selectedHolder[0]);
            dialog.dismiss();
            listener.onOpenAudioFxStudio();
        });

        btnAssign.setOnClickListener(v -> {
            committed[0] = true;
            listener.onAssignStyle(selectedHolder[0]);
            Toast.makeText(
                    activity,
                    activity.getString(R.string.ui_interface_assigned_toast,
                            activity.getString(selectedHolder[0].titleRes)),
                    Toast.LENGTH_SHORT
            ).show();
            dialog.dismiss();
        });

        dialog.setOnDismissListener(d -> {
            if (!committed[0] && selectedHolder[0] != initialAssignedMode) {
                listener.onPreviewStyle(initialAssignedMode);
            }
        });

        dialog.show();
    }
}
