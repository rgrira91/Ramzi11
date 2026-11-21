package com.example.ui.theme;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.StringRes;
import com.example.R;

public class UiStylePreferences {
    private static final String PREFS_NAME = "neon_wave_ui_style_prefs";
    private static final String KEY_ACTIVE_STYLE = "active_ui_style";
    private static final String KEY_FLOATING_COLLAPSED = "floating_mini_collapsed";
    private static final String KEY_SHOW_EQ_CUSTOM = "show_eq_in_custom_styles";
    private static final String KEY_VISUAL_METERS_ENABLED = "visual_meters_enabled";
    private static final String KEY_VISUAL_METER_MODE = "visual_meter_mode";
    private static final String KEY_COLOR_PULSE_BUTTONS = "color_pulse_buttons_enabled";
    private static final String KEY_GLASS_NAV_BAR = "glass_nav_bar_enabled";

    public enum VisualMeterMode {
        DISCO_VU("disco_vu", R.string.mode_disco_vu_short, R.string.meter_mode_disco_vu),
        DISCO_MODE("disco_mode", R.string.mode_disco_short, R.string.meter_mode_disco),
        SIDE_LED("side_led", R.string.mode_side_led_short, R.string.meter_mode_side_led),
        STUDIO("studio", R.string.mode_studio_short, R.string.meter_mode_studio),
        VINYL("vinyl", R.string.mode_vinyl_short, R.string.meter_mode_vinyl),
        CYBERPUNK("cyberpunk", R.string.mode_cyberpunk_short, R.string.meter_mode_cyberpunk),
        PARTY_STROBE("party_strobe", R.string.mode_party_short, R.string.meter_mode_party),
        CHILL_OCEAN("chill_ocean", R.string.mode_ocean_short, R.string.meter_mode_ocean),
        FIRE("fire", R.string.mode_fire_short, R.string.meter_mode_fire);

        public final String key;
        @StringRes public final int shortTitleRes;
        @StringRes public final int switchTitleRes;

        VisualMeterMode(String key, @StringRes int shortTitleRes, @StringRes int switchTitleRes) {
            this.key = key;
            this.shortTitleRes = shortTitleRes;
            this.switchTitleRes = switchTitleRes;
        }

        public static VisualMeterMode fromKey(String key) {
            if (key != null) {
                for (VisualMeterMode mode : values()) {
                    if (mode.key.equals(key)) return mode;
                }
            }
            return DISCO_VU;
        }

        public VisualMeterMode next() {
            VisualMeterMode[] all = values();
            return all[(ordinal() + 1) % all.length];
        }

        public NeonPalette getPalette() {
            switch (this) {
                case DISCO_VU:
                    // Purple + Pink + Blue Neon
                    return new NeonPalette(
                            0xFFEC4899, 0xFF38BDF8, 0xFFA855F7,
                            0xFF1F0A3D, 0xFF14072A, 0xFF070214,
                            0xEB2A104E, 0xEB1A0A39, 0xEB0E0624,
                            0xD61A0B36, 0x55EC4899, 0xFF6D28D9, 0xFF3B0764);
                case DISCO_MODE:
                    // Vivid Full-Screen Club Rainbow Neon
                    return new NeonPalette(
                            0xFFFF22B8, 0xFF00F0FF, 0xFFA855F7,
                            0xFF28084D, 0xFF190636, 0xFF090218,
                            0xEB350C5E, 0xEB210844, 0xEB110428,
                            0xD6220942, 0x6BFF22B8, 0xFFD946EF, 0xFF4338CA);
                case SIDE_LED:
                    // Blue + Cyan
                    return new NeonPalette(
                            0xFF3B82F6, 0xFF22D3EE, 0xFF0EA5E9,
                            0xFF091B3A, 0xFF07122B, 0xFF030816,
                            0xEB0C244A, 0xEB081938, 0xEB051024,
                            0xD60B1D3B, 0x5522D3EE, 0xFF1D4ED8, 0xFF0F2958);
                case STUDIO:
                    // Dark Grey + Green + Yellow
                    return new NeonPalette(
                            0xFF22C55E, 0xFFFACC15, 0xFF84CC16,
                            0xFF1F242A, 0xFF161A1F, 0xFF0B0D10,
                            0xEB252A31, 0xEB1B1F25, 0xEB12151A,
                            0xD61D2228, 0x55FACC15, 0xFF15803D, 0xFF14532D);
                case VINYL:
                    // Brown + Gold + Black
                    return new NeonPalette(
                            0xFFD4AF37, 0xFFF59E0B, 0xFF92400E,
                            0xFF2C1810, 0xFF1A0E09, 0xFF0A0503,
                            0xEB351F14, 0xEB24140D, 0xEB130A06,
                            0xD626150E, 0x55D4AF37, 0xFF78350F, 0xFF451A03);
                case CYBERPUNK:
                    // Yellow + Cyan
                    return new NeonPalette(
                            0xFFFACC15, 0xFF22D3EE, 0xFFFDE047,
                            0xFF1C1B08, 0xFF0E161E, 0xFF050A0E,
                            0xEB22230E, 0xEB111C26, 0xEB081016,
                            0xD6161C22, 0x5EFACC15, 0xFFA16207, 0xFF0E7490);
                case PARTY_STROBE:
                    // Vivid Multi-Neon Strobe
                    return new NeonPalette(
                            0xFFFF2EF5, 0xFF00F5D4, 0xFFFFE600,
                            0xFF2A0845, 0xFF180833, 0xFF080214,
                            0xEB310C54, 0xEB1D083B, 0xEB0E0421,
                            0xD6200A3D, 0x66FF2EF5, 0xFFC026D3, 0xFF4C1D95);
                case CHILL_OCEAN:
                    // Blue + Green Calm Ocean
                    return new NeonPalette(
                            0xFF10B981, 0xFF38BDF8, 0xFF0D9488,
                            0xFF062338, 0xFF06282A, 0xFF031216,
                            0xEB0A2E42, 0xEB08272D, 0xEB05171C,
                            0xD608242C, 0x5210B981, 0xFF0369A1, 0xFF065F46);
                case FIRE:
                default:
                    // Red + Orange + Yellow Flame
                    return new NeonPalette(
                            0xFFEF4444, 0xFFFACC15, 0xFFF97316,
                            0xFF330C08, 0xFF210805, 0xFF0F0302,
                            0xEB3D110A, 0xEB280B06, 0xEB150503,
                            0xD62C0D08, 0x60F97316, 0xFFB91C1C, 0xFF7C2D12);
            }
        }
    }

    private final SharedPreferences prefs;

    public UiStylePreferences(Context context) {
        this.prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public UiStyleMode getActiveStyle() {
        return UiStyleMode.fromKey(prefs.getString(KEY_ACTIVE_STYLE, UiStyleMode.NEON_COSMIC.key));
    }

    public void setActiveStyle(UiStyleMode mode) {
        if (mode == null) mode = UiStyleMode.NEON_COSMIC;
        prefs.edit().putString(KEY_ACTIVE_STYLE, mode.key).apply();
    }

    public boolean isFloatingMiniCollapsed() {
        return prefs.getBoolean(KEY_FLOATING_COLLAPSED, false);
    }

    public void setFloatingMiniCollapsed(boolean collapsed) {
        prefs.edit().putBoolean(KEY_FLOATING_COLLAPSED, collapsed).apply();
    }

    public boolean isShowEqInCustomStyles() {
        return prefs.getBoolean(KEY_SHOW_EQ_CUSTOM, true);
    }

    public void setShowEqInCustomStyles(boolean show) {
        prefs.edit().putBoolean(KEY_SHOW_EQ_CUSTOM, show).apply();
    }

    public boolean isVisualMetersEnabled() {
        return prefs.getBoolean(KEY_VISUAL_METERS_ENABLED, true);
    }

    public void setVisualMetersEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_VISUAL_METERS_ENABLED, enabled).apply();
    }

    public VisualMeterMode getVisualMeterMode() {
        return VisualMeterMode.fromKey(prefs.getString(KEY_VISUAL_METER_MODE, VisualMeterMode.DISCO_VU.key));
    }

    public void setVisualMeterMode(VisualMeterMode mode) {
        if (mode == null) mode = VisualMeterMode.DISCO_VU;
        prefs.edit().putString(KEY_VISUAL_METER_MODE, mode.key).apply();
    }

    public boolean isColorPulseButtonsEnabled() {
        return prefs.getBoolean(KEY_COLOR_PULSE_BUTTONS, true);
    }

    public void setColorPulseButtonsEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_COLOR_PULSE_BUTTONS, enabled).apply();
    }

    public boolean isGlassNavBarEnabled() {
        return prefs.getBoolean(KEY_GLASS_NAV_BAR, true);
    }

    public void setGlassNavBarEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_GLASS_NAV_BAR, enabled).apply();
    }
}
