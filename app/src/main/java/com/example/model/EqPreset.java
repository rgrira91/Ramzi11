package com.example.model;

import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;
import com.example.R;

public enum EqPreset {
    FLAT(
            "flat",
            R.string.preset_flat,
            R.drawable.ic_preset_flat,
            new float[]{0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f},
            0,
            100
    ),
    POP(
            "pop",
            R.string.preset_pop,
            R.drawable.ic_preset_pop,
            new float[]{-1.5f, 1.0f, 3.0f, 4.5f, 4.0f, 1.5f, -1.0f, 2.0f, 3.5f, 4.0f},
            300,
            110
    ),
    ROCK(
            "rock",
            R.string.preset_rock,
            R.drawable.ic_preset_rock,
            new float[]{5.5f, 4.5f, 2.5f, -1.0f, -3.0f, -1.5f, 2.5f, 4.5f, 6.0f, 6.5f},
            500,
            125
    ),
    ELECTRONIC(
            "electronic",
            R.string.preset_electronic,
            R.drawable.ic_preset_electronic,
            new float[]{7.0f, 6.0f, 3.5f, 1.0f, -2.0f, 1.5f, 3.5f, 5.5f, 7.5f, 7.0f},
            700,
            140
    ),
    HIP_HOP(
            "hip_hop",
            R.string.preset_hip_hop,
            R.drawable.ic_preset_hiphop,
            new float[]{9.5f, 8.5f, 6.0f, 2.5f, -1.0f, 0.5f, 2.0f, 3.5f, 4.5f, 4.0f},
            850,
            155
    ),
    JAZZ(
            "jazz",
            R.string.preset_jazz,
            R.drawable.ic_preset_jazz,
            new float[]{3.5f, 3.0f, 1.5f, 2.0f, -1.5f, -1.0f, 1.5f, 3.5f, 4.5f, 4.0f},
            250,
            110
    ),
    CLASSICAL(
            "classical",
            R.string.preset_classical,
            R.drawable.ic_preset_classical,
            new float[]{4.0f, 3.0f, 1.5f, 0.5f, -1.5f, -1.0f, 1.5f, 3.5f, 5.0f, 5.5f},
            150,
            105
    ),
    BASS_BOOST(
            "bass_boost",
            R.string.preset_bass_boost,
            R.drawable.ic_preset_bass,
            new float[]{12.0f, 12.0f, 8.5f, 4.5f, 1.0f, 0f, 0f, 1.0f, 2.0f, 2.5f},
            1000,
            175
    ),
    TREBLE_BOOST(
            "treble_boost",
            R.string.preset_treble_boost,
            R.drawable.ic_preset_treble,
            new float[]{-1.0f, 0f, 0f, 0.5f, 1.5f, 3.0f, 5.5f, 8.0f, 10.5f, 12.0f},
            0,
            95
    ),
    VOCAL(
            "vocal",
            R.string.preset_vocal,
            R.drawable.ic_preset_vocal,
            new float[]{-2.5f, -1.5f, 0f, 2.0f, 5.5f, 7.0f, 6.0f, 3.5f, 1.5f, 0.5f},
            0,
            95
    ),
    PARTY(
            "party",
            R.string.preset_party,
            R.drawable.ic_preset_party,
            new float[]{8.5f, 7.5f, 4.5f, 1.0f, -1.5f, 1.0f, 3.5f, 6.0f, 7.5f, 8.0f},
            850,
            150
    ),
    CUSTOM(
            "custom",
            R.string.preset_custom,
            R.drawable.ic_equalizer,
            new float[]{0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f},
            500,
            125
    );

    private final String key;
    private final int titleRes;
    private final int iconRes;
    private final float[] gainsDb;
    private final int bassBoostStrength;
    private final int defaultBassSliderPercent;

    EqPreset(
            String key,
            @StringRes int titleRes,
            @DrawableRes int iconRes,
            float[] gainsDb,
            int bassBoostStrength,
            int defaultBassSliderPercent) {
        this.key = key;
        this.titleRes = titleRes;
        this.iconRes = iconRes;
        this.gainsDb = gainsDb;
        this.bassBoostStrength = bassBoostStrength;
        this.defaultBassSliderPercent = defaultBassSliderPercent;
    }

    public String getKey() { return key; }
    @StringRes public int getTitleRes() { return titleRes; }
    @DrawableRes public int getIconRes() { return iconRes; }
    public float[] getGainsDbCopy() { return gainsDb.clone(); }
    public int getBassBoostStrength() { return bassBoostStrength; }
    public int getDefaultBassSliderPercent() { return defaultBassSliderPercent; }

    public static EqPreset fromKey(String key) {
        for (EqPreset preset : values()) {
            if (preset.key.equals(key)) {
                return preset;
            }
        }
        return ELECTRONIC;
    }
}
