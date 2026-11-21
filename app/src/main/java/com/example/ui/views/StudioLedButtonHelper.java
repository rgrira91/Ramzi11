package com.example.ui.views;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import androidx.annotation.Nullable;

/**
 * Renders physical studio hardware backlit LED buttons with multi-stage outer chassis light spill,
 * hot incandescent RadialGradient core illumination, specular cap bevel, and crisp laser-etched legend.
 */
public final class StudioLedButtonHelper {

    public static final int STUDIO_GREEN = 0xFF00E676;
    public static final int STUDIO_RED = 0xFFFF1744;
    public static final int STUDIO_YELLOW = 0xFFFFD600;
    public static final int STUDIO_AMBER = 0xFFFF9100;
    public static final int STUDIO_CYAN = 0xFF00E5FF;
    public static final int STUDIO_MAGENTA = 0xFFE040FB;

    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint specPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint titlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint subPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ledHaloPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ledCorePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF scratchRect = new RectF();

    public StudioLedButtonHelper(float density) {
        glowPaint.setStyle(Paint.Style.FILL);
        fillPaint.setStyle(Paint.Style.FILL);
        fillPaint.setColor(0xFFFFFFFF);
        rimPaint.setStyle(Paint.Style.STROKE);
        specPaint.setStyle(Paint.Style.STROKE);
        specPaint.setColor(0xFFFFFFFF);

        titlePaint.setTextAlign(Paint.Align.CENTER);
        titlePaint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));

        subPaint.setTextAlign(Paint.Align.CENTER);
        subPaint.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));

        ledHaloPaint.setStyle(Paint.Style.FILL);
        ledCorePaint.setStyle(Paint.Style.FILL);
    }

    public void drawSingleLineButton(
            Canvas canvas,
            RectF rect,
            float cornerRadius,
            float density,
            String label,
            float textSizeSpDp,
            int accentColor,
            boolean active,
            boolean withLed,
            int ledColor,
            float pulseIntensity,
            float pressFlash) {
        drawButtonInternal(
                canvas, rect, cornerRadius, density,
                label, null, textSizeSpDp, 0f,
                accentColor, active, withLed, ledColor,
                pulseIntensity, pressFlash
        );
    }

    public void drawTwoLineButton(
            Canvas canvas,
            RectF rect,
            float cornerRadius,
            float density,
            String title,
            String subtitle,
            float titleSizePx,
            float subSizePx,
            int accentColor,
            boolean active,
            boolean withLed,
            float pulseIntensity,
            float pressFlash) {
        drawButtonInternal(
                canvas, rect, cornerRadius, density,
                title, subtitle, titleSizePx, subSizePx,
                accentColor, active, withLed, accentColor,
                pulseIntensity, pressFlash
        );
    }

    private void drawButtonInternal(
            Canvas canvas,
            RectF rect,
            float cornerRadius,
            float density,
            String title,
            @Nullable String subtitle,
            float titleSizePx,
            float subSizePx,
            int accentColor,
            boolean active,
            boolean withLed,
            int ledColor,
            float pulseIntensity,
            float pressFlash) {
        if (rect.width() <= 1f || rect.height() <= 1f) return;

        float flash = Math.max(0f, Math.min(1f, pressFlash));
        float pulse = Math.max(0f, Math.min(1f, pulseIntensity));
        float lightPower = active ? Math.min(1.25f, 0.92f + 0.18f * pulse + 0.25f * flash) : flash * 0.85f;
        boolean lit = active || flash > 0.08f;

        if (lit) {
            // 1. Real Studio Outer Light Spill onto surrounding dark chassis (3 concentric bloom stages)
            float bloomScale = 0.75f + 0.35f * lightPower;
            int outerAlpha = Math.min(130, Math.round(62f * bloomScale));
            int midAlpha = Math.min(185, Math.round(115f * bloomScale));
            int tightAlpha = Math.min(220, Math.round(165f * bloomScale));

            expandRect(rect, 4.6f * density * bloomScale, scratchRect);
            glowPaint.setColor(withAlpha(accentColor, outerAlpha));
            canvas.drawRoundRect(scratchRect, cornerRadius + 4.2f * density, cornerRadius + 4.2f * density, glowPaint);

            expandRect(rect, 2.5f * density * bloomScale, scratchRect);
            glowPaint.setColor(withAlpha(accentColor, midAlpha));
            canvas.drawRoundRect(scratchRect, cornerRadius + 2.3f * density, cornerRadius + 2.3f * density, glowPaint);

            expandRect(rect, 1.0f * density, scratchRect);
            glowPaint.setColor(withAlpha(accentColor, tightAlpha));
            canvas.drawRoundRect(scratchRect, cornerRadius + 0.9f * density, cornerRadius + 0.9f * density, glowPaint);

            // 2. Backlit Translucent Silicone Keycap: Hot LED Filament Core -> Saturated Vivid Color -> Rich Rim
            float gradRadius = Math.max(rect.width(), rect.height()) * 0.72f;
            int coreColor = lerpColor(accentColor, 0xFFFFFFFF, Math.min(0.76f, 0.42f + 0.26f * flash + 0.08f * pulse));
            int midColor = lerpColor(accentColor, 0xFFFFFFFF, 0.14f);
            int edgeColor = lerpColor(accentColor, 0xFF04070E, 0.26f);

            fillPaint.setShader(new RadialGradient(
                    rect.centerX(),
                    rect.centerY() - rect.height() * 0.08f,
                    Math.max(4f, gradRadius),
                    new int[]{coreColor, midColor, edgeColor},
                    new float[]{0f, 0.56f, 1f},
                    Shader.TileMode.CLAMP
            ));
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, fillPaint);
            fillPaint.setShader(null);

            // 3. Upper Specular Polycarbonate Highlight + Crisp Glowing Bezel Rim
            float inset = 1.2f * density;
            scratchRect.set(rect.left + inset, rect.top + inset, rect.right - inset, rect.bottom - inset);
            specPaint.setStrokeWidth(1.0f * density);
            specPaint.setShader(new LinearGradient(
                    0, scratchRect.top, 0, scratchRect.bottom,
                    new int[]{0xAAFFFFFF, 0x18FFFFFF, 0x00000000},
                    new float[]{0f, 0.35f, 1f},
                    Shader.TileMode.CLAMP
            ));
            canvas.drawRoundRect(scratchRect, Math.max(2f, cornerRadius - inset), Math.max(2f, cornerRadius - inset), specPaint);
            specPaint.setShader(null);

            rimPaint.setStrokeWidth((1.55f + 0.45f * flash) * density);
            rimPaint.setColor(lerpColor(accentColor, 0xFFFFFFFF, 0.50f));
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, rimPaint);
        } else {
            // Unlit Smoked Dark Hardware Keycap (high contrast so activated buttons pop like real LEDs!)
            fillPaint.setShader(new LinearGradient(
                    0, rect.top, 0, rect.bottom,
                    new int[]{0xEC141B2D, 0xF0090D18},
                    null,
                    Shader.TileMode.CLAMP
            ));
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, fillPaint);
            fillPaint.setShader(null);

            rimPaint.setStrokeWidth(1.1f * density);
            rimPaint.setColor(withAlpha(accentColor, 0x6E));
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, rimPaint);
        }

        // 4. Optional Physical Hardware LED Jewel Indicator
        float textCenterX = rect.centerX();
        if (withLed) {
            boolean twoLine = subtitle != null && !subtitle.isEmpty();
            float ledR = twoLine
                    ? Math.min(2.1f * density, rect.height() * 0.09f)
                    : Math.min(2.6f * density, rect.height() * 0.14f);
            float ledX = twoLine
                    ? (rect.left + Math.max(6.2f * density, cornerRadius * 0.72f))
                    : (rect.left + Math.max(7.5f * density, rect.height() * 0.34f));
            float ledY = twoLine
                    ? (rect.top + Math.max(6.0f * density, cornerRadius * 0.70f))
                    : rect.centerY();
            if (active || flash > 0.15f) {
                ledHaloPaint.setColor(withAlpha(ledColor, 0xCC));
                canvas.drawCircle(ledX, ledY, ledR * 2.25f, ledHaloPaint);
                ledCorePaint.setColor(ledColor);
                canvas.drawCircle(ledX, ledY, ledR, ledCorePaint);
                ledCorePaint.setColor(0xFFFFFFFF);
                canvas.drawCircle(ledX, ledY, ledR * 0.55f, ledCorePaint);
            } else {
                ledCorePaint.setColor(0xFF1E293B);
                canvas.drawCircle(ledX, ledY, ledR * 0.92f, ledCorePaint);
                ledHaloPaint.setColor(withAlpha(ledColor, 0x55));
                canvas.drawCircle(ledX, ledY, ledR * 0.52f, ledHaloPaint);
            }
            if (!twoLine) {
                textCenterX += ledR * 1.45f;
            }
        }

        // 5. Laser-Etched Legend (High-Contrast against glowing LED cap)
        boolean brightCap = lit && perceivedLuminance(lerpColor(accentColor, 0xFFFFFFFF, 0.28f)) > 0.60f;
        int mainTextColor;
        int subTextColor;
        if (lit) {
            mainTextColor = brightCap ? 0xFF070C14 : 0xFFFFFFFF;
            subTextColor = brightCap ? 0xE60B1320 : 0xF5FFFFFF;
        } else {
            mainTextColor = lerpColor(accentColor, 0xFFE2E8F0, 0.45f);
            subTextColor = withAlpha(accentColor, 0xD4);
        }

        titlePaint.setTextSize(titleSizePx);
        titlePaint.setColor(mainTextColor);
        if (lit && !brightCap) {
            titlePaint.setShadowLayer(2.2f * density, 0f, 0.8f * density, 0xE804070E);
        } else {
            titlePaint.clearShadowLayer();
        }

        if (subtitle == null || subtitle.isEmpty()) {
            float textY = rect.centerY() - (titlePaint.ascent() + titlePaint.descent()) * 0.5f;
            canvas.drawText(title, textCenterX, textY, titlePaint);
        } else {
            subPaint.setTextSize(subSizePx);
            subPaint.setColor(subTextColor);
            if (lit && !brightCap) {
                subPaint.setShadowLayer(1.8f * density, 0f, 0.7f * density, 0xE004070E);
            } else {
                subPaint.clearShadowLayer();
            }
            float cy = rect.centerY();
            canvas.drawText(title, rect.centerX(), cy - 1.2f * density, titlePaint);
            canvas.drawText(subtitle, rect.centerX(), cy + subSizePx + 1.2f * density, subPaint);
            subPaint.clearShadowLayer();
        }
        titlePaint.clearShadowLayer();
    }

    private static void expandRect(RectF src, float delta, RectF dst) {
        dst.set(src.left - delta, src.top - delta, src.right + delta, src.bottom + delta);
    }

    private static float perceivedLuminance(int color) {
        int r = (color >>> 16) & 0xFF;
        int g = (color >>> 8) & 0xFF;
        int b = color & 0xFF;
        return (0.299f * r + 0.587f * g + 0.114f * b) / 255f;
    }

    public static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }

    public static int lerpColor(int a, int b, float t) {
        float c = Math.max(0f, Math.min(1f, t));
        int aa = (a >>> 24) & 0xFF;
        int ar = (a >>> 16) & 0xFF;
        int ag = (a >>> 8) & 0xFF;
        int ab = a & 0xFF;
        int ba = (b >>> 24) & 0xFF;
        int br = (b >>> 16) & 0xFF;
        int bg = (b >>> 8) & 0xFF;
        int bb = b & 0xFF;
        return ((aa + Math.round((ba - aa) * c)) << 24)
                | ((ar + Math.round((br - ar) * c)) << 16)
                | ((ag + Math.round((bg - ag) * c)) << 8)
                | (ab + Math.round((bb - ab) * c));
    }
}
