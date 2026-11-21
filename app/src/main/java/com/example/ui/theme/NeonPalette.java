package com.example.ui.theme;

import android.graphics.Color;

public class NeonPalette {
    public static final NeonPalette DEFAULT = new NeonPalette(
            0xFFF43F9E, // primaryNeon (magenta/pink)
            0xFF22D3EE, // secondaryNeon (electric cyan)
            0xFFA855F7, // tertiaryGlow (neon violet)
            0xFF1A093A, // canvasStart
            0xFF120829, // canvasCenter
            0xFF070312, // canvasEnd
            0xEB240F4A, // cardGlassStart
            0xEB170B38, // cardGlassCenter
            0xEB0D0824, // cardGlassEnd
            0xD6160B33, // subCardFill
            0x4D9D4EDD, // subCardStroke
            0xFF4C1D95, // playCenterStart
            0xFF2A145E  // playCenterEnd
    );

    public final int primaryNeon;
    public final int secondaryNeon;
    public final int tertiaryGlow;
    public final int canvasStart;
    public final int canvasCenter;
    public final int canvasEnd;
    public final int cardGlassStart;
    public final int cardGlassCenter;
    public final int cardGlassEnd;
    public final int subCardFill;
    public final int subCardStroke;
    public final int playCenterStart;
    public final int playCenterEnd;

    public NeonPalette(
            int primaryNeon,
            int secondaryNeon,
            int tertiaryGlow,
            int canvasStart,
            int canvasCenter,
            int canvasEnd,
            int cardGlassStart,
            int cardGlassCenter,
            int cardGlassEnd,
            int subCardFill,
            int subCardStroke,
            int playCenterStart,
            int playCenterEnd) {
        this.primaryNeon = primaryNeon;
        this.secondaryNeon = secondaryNeon;
        this.tertiaryGlow = tertiaryGlow;
        this.canvasStart = canvasStart;
        this.canvasCenter = canvasCenter;
        this.canvasEnd = canvasEnd;
        this.cardGlassStart = cardGlassStart;
        this.cardGlassCenter = cardGlassCenter;
        this.cardGlassEnd = cardGlassEnd;
        this.subCardFill = subCardFill;
        this.subCardStroke = subCardStroke;
        this.playCenterStart = playCenterStart;
        this.playCenterEnd = playCenterEnd;
    }

    public static NeonPalette lerp(NeonPalette from, NeonPalette to, float fraction) {
        if (from == null) return to != null ? to : DEFAULT;
        if (to == null) return from;
        float t = Math.max(0f, Math.min(1f, fraction));
        if (t <= 0f) return from;
        if (t >= 1f) return to;
        return new NeonPalette(
                lerpColor(from.primaryNeon, to.primaryNeon, t),
                lerpColor(from.secondaryNeon, to.secondaryNeon, t),
                lerpColor(from.tertiaryGlow, to.tertiaryGlow, t),
                lerpColor(from.canvasStart, to.canvasStart, t),
                lerpColor(from.canvasCenter, to.canvasCenter, t),
                lerpColor(from.canvasEnd, to.canvasEnd, t),
                lerpColor(from.cardGlassStart, to.cardGlassStart, t),
                lerpColor(from.cardGlassCenter, to.cardGlassCenter, t),
                lerpColor(from.cardGlassEnd, to.cardGlassEnd, t),
                lerpColor(from.subCardFill, to.subCardFill, t),
                lerpColor(from.subCardStroke, to.subCardStroke, t),
                lerpColor(from.playCenterStart, to.playCenterStart, t),
                lerpColor(from.playCenterEnd, to.playCenterEnd, t)
        );
    }

    public static int lerpColor(int c1, int c2, float t) {
        float inv = 1f - t;
        int a = Math.round(Color.alpha(c1) * inv + Color.alpha(c2) * t);
        int r = Math.round(Color.red(c1) * inv + Color.red(c2) * t);
        int g = Math.round(Color.green(c1) * inv + Color.green(c2) * t);
        int b = Math.round(Color.blue(c1) * inv + Color.blue(c2) * t);
        return Color.argb(a, r, g, b);
    }

    public static int withAlpha(int color, int alphaByte) {
        return (color & 0x00FFFFFF) | ((alphaByte & 0xFF) << 24);
    }
}
