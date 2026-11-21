package com.example.ui.theme;

import androidx.annotation.StringRes;
import com.example.R;

public enum UiStyleMode {
    NEON_COSMIC(
            "neon_cosmic",
            R.string.ui_style_neon_cosmic,
            R.string.ui_style_neon_cosmic_short,
            R.string.ui_style_neon_cosmic_desc,
            true
    ),
    FLOATING_MINI(
            "floating_mini",
            R.string.ui_style_floating_mini,
            R.string.ui_style_floating_mini_short,
            R.string.ui_style_floating_mini_desc,
            true
    ),
    RETRO_VINYL(
            "retro_vinyl",
            R.string.ui_style_retro_vinyl,
            R.string.ui_style_retro_vinyl_short,
            R.string.ui_style_retro_vinyl_desc,
            false
    ),
    LIQUID_GLASS(
            "liquid_glass",
            R.string.ui_style_liquid_glass,
            R.string.ui_style_liquid_glass_short,
            R.string.ui_style_liquid_glass_desc,
            false
    );

    public final String key;
    @StringRes public final int titleRes;
    @StringRes public final int shortBadgeRes;
    @StringRes public final int descRes;
    public final boolean isDarkCanvas;

    UiStyleMode(String key, @StringRes int titleRes, @StringRes int shortBadgeRes,
                @StringRes int descRes, boolean isDarkCanvas) {
        this.key = key;
        this.titleRes = titleRes;
        this.shortBadgeRes = shortBadgeRes;
        this.descRes = descRes;
        this.isDarkCanvas = isDarkCanvas;
    }

    public static UiStyleMode fromKey(String key) {
        if (key == null) return NEON_COSMIC;
        for (UiStyleMode mode : values()) {
            if (mode.key.equals(key)) {
                return mode;
            }
        }
        return NEON_COSMIC;
    }
}
