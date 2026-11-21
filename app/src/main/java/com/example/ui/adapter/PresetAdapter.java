package com.example.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import com.example.R;
import com.example.model.EqPreset;
import com.example.ui.theme.DynamicThemeRenderer;
import com.example.ui.theme.NeonPalette;
import java.util.Arrays;
import java.util.List;

public class PresetAdapter extends RecyclerView.Adapter<PresetAdapter.PresetViewHolder> {
    public interface OnPresetClickListener {
        void onPresetSelected(EqPreset preset);
    }

    private final List<EqPreset> presets = Arrays.asList(EqPreset.values());
    private final OnPresetClickListener listener;
    private EqPreset selectedPreset = EqPreset.ELECTRONIC;
    private NeonPalette activePalette = NeonPalette.DEFAULT;
    private com.example.ui.theme.UiStyleMode uiStyleMode = com.example.ui.theme.UiStyleMode.NEON_COSMIC;

    public PresetAdapter(OnPresetClickListener listener) {
        this.listener = listener;
    }

    public void setSelectedPreset(EqPreset preset) {
        this.selectedPreset = preset;
        notifyDataSetChanged();
    }

    public void setThemePalette(NeonPalette palette) {
        this.activePalette = palette != null ? palette : NeonPalette.DEFAULT;
        notifyDataSetChanged();
    }

    public void setUiStyleMode(com.example.ui.theme.UiStyleMode mode) {
        this.uiStyleMode = mode != null ? mode : com.example.ui.theme.UiStyleMode.NEON_COSMIC;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public PresetViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_eq_preset, parent, false);
        return new PresetViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PresetViewHolder holder, int position) {
        EqPreset preset = presets.get(position);
        boolean isSelected = preset == selectedPreset;
        float density = holder.itemView.getResources().getDisplayMetrics().density;

        holder.iconView.setImageResource(preset.getIconRes());
        holder.labelView.setText(preset.getTitleRes());

        if (uiStyleMode == com.example.ui.theme.UiStyleMode.RETRO_VINYL) {
            android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
            bg.setCornerRadius(14f * density);
            if (isSelected) {
                bg.setColor(0xFFFDE5B4);
                bg.setStroke(Math.max(1, Math.round(1.5f * density)), 0xFFC25216);
                holder.iconView.setColorFilter(0xFFC25216);
                holder.labelView.setTextColor(0xFF2D1B10);
            } else {
                bg.setColor(0xFFF3E4C8);
                bg.setStroke(Math.max(1, Math.round(1f * density)), 0xFFD9BE91);
                holder.iconView.setColorFilter(0xFF7A5638);
                holder.labelView.setTextColor(0xFF5A371E);
            }
            holder.tileRoot.setBackground(bg);
        } else if (uiStyleMode == com.example.ui.theme.UiStyleMode.LIQUID_GLASS) {
            android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
            bg.setCornerRadius(14f * density);
            if (isSelected) {
                bg.setColor(0xDDEDE9FE);
                bg.setStroke(Math.max(1, Math.round(1.5f * density)), 0xFF7C3AED);
                holder.iconView.setColorFilter(0xFF6D28D9);
                holder.labelView.setTextColor(0xFF1E1B4B);
            } else {
                bg.setColor(0xAAFFFFFF);
                bg.setStroke(Math.max(1, Math.round(1f * density)), 0xCCFFFFFF);
                holder.iconView.setColorFilter(0xFF4F46E5);
                holder.labelView.setTextColor(0xFF312E81);
            }
            holder.tileRoot.setBackground(bg);
        } else {
            if (isSelected) {
                DynamicThemeRenderer.applySelectedTileBackground(holder.tileRoot, activePalette, density, 14f);
                holder.iconView.setColorFilter(0xFFFFFFFF);
            } else {
                holder.tileRoot.setBackgroundResource(R.drawable.bg_preset_tile_normal);
                holder.iconView.clearColorFilter();
            }
            holder.labelView.setTextColor(isSelected
                    ? 0xFFFFFFFF
                    : ContextCompat.getColor(holder.itemView.getContext(), R.color.text_secondary));
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onPresetSelected(preset);
            }
        });
    }

    @Override
    public int getItemCount() {
        return presets.size();
    }

    static class PresetViewHolder extends RecyclerView.ViewHolder {
        final View tileRoot;
        final ImageView iconView;
        final TextView labelView;

        PresetViewHolder(@NonNull View itemView) {
            super(itemView);
            tileRoot = itemView.findViewById(R.id.preset_tile_root);
            iconView = itemView.findViewById(R.id.preset_icon);
            labelView = itemView.findViewById(R.id.preset_label);
        }
    }
}
