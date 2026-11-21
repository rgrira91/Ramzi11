package com.example.ui.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Vertical segmented capsule LED level meter (Left / Right) framing the player card.
 */
public class SideLedMeterView extends View {
    public static final int CHANNEL_LEFT = 0;
    public static final int CHANNEL_RIGHT = 1;

    private static final int SEGMENT_COUNT = 26;

    private final Paint shellBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shellBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shellHighlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ledOffPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ledOnPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ledGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint peakPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final RectF shellRect = new RectF();
    private final RectF segRect = new RectF();
    private final RectF glowRect = new RectF();

    public enum LedColorScheme {
        CLASSIC_VU,
        BLUE_CYAN,
        CYBERPUNK_YELLOW_CYAN
    }

    private LedColorScheme colorScheme = LedColorScheme.BLUE_CYAN;
    private int channel = CHANNEL_LEFT;
    private boolean isPlaying = false;
    private float targetLevel = 0.08f;
    private float currentLevel = 0.08f;
    private float peakLevel = 0.08f;
    private long lastSpectrumTimeMs = 0L;
    private long lastPeakTimeMs = 0L;
    private long lastAnimFrameMs = 0L;
    private float phase = 0f;
    private float density = 1f;
    private long frameIntervalMs = 16L;
    private final Runnable frameRunnable = this::invalidate;

    public SideLedMeterView(Context context) {
        super(context);
        init(context);
    }

    public SideLedMeterView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public SideLedMeterView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        density = context.getResources().getDisplayMetrics().density;

        shellBgPaint.setStyle(Paint.Style.FILL);
        shellBgPaint.setColor(0xA6141028);

        shellBorderPaint.setStyle(Paint.Style.STROKE);
        shellBorderPaint.setStrokeWidth(1.3f * density);
        shellBorderPaint.setColor(0x99FFFFFF);

        shellHighlightPaint.setStyle(Paint.Style.STROKE);
        shellHighlightPaint.setStrokeWidth(1.0f * density);
        shellHighlightPaint.setColor(0x44FFFFFF);

        ledOffPaint.setStyle(Paint.Style.FILL);
        ledOffPaint.setColor(0x28FFFFFF);

        ledOnPaint.setStyle(Paint.Style.FILL);
        ledGlowPaint.setStyle(Paint.Style.FILL);
        peakPaint.setStyle(Paint.Style.FILL);
    }

    public void setChannel(int channel) {
        this.channel = channel == CHANNEL_RIGHT ? CHANNEL_RIGHT : CHANNEL_LEFT;
    }

    public void setLedColorScheme(@NonNull LedColorScheme scheme) {
        if (this.colorScheme != scheme) {
            this.colorScheme = scheme;
            if (isShown()) invalidate();
        }
    }

    public void setBlueCyanMode(boolean enabled) {
        setLedColorScheme(enabled ? LedColorScheme.BLUE_CYAN : LedColorScheme.CLASSIC_VU);
    }

    public void setTargetFps(int fps) {
        int clamped = Math.max(18, Math.min(60, fps));
        this.frameIntervalMs = Math.max(16L, Math.round(1000.0 / clamped));
    }

    public void setPlaying(boolean playing) {
        if (this.isPlaying == playing) return;
        this.isPlaying = playing;
        lastAnimFrameMs = 0L;
        if (playing && isShown()) {
            removeCallbacks(frameRunnable);
            postOnAnimation(frameRunnable);
        }
    }

    public void updateStereoLevel(float channelLevel) {
        this.targetLevel = Math.max(0.05f, Math.min(1f, channelLevel));
        this.lastSpectrumTimeMs = SystemClock.uptimeMillis();
        if (isShown() && getVisibility() == VISIBLE) {
            postInvalidateOnAnimation();
        }
    }

    public void updateSpectrum(@Nullable float[] bars, float energy) {
        float chEnergy = energy;
        if (bars != null && bars.length > 0) {
            float sum = 0f;
            for (float b : bars) {
                sum += b;
            }
            chEnergy = (sum / bars.length) * 0.5f + energy * 0.5f;
        }
        this.targetLevel = Math.max(0.05f, Math.min(1f, chEnergy * 1.35f));
        this.lastSpectrumTimeMs = SystemClock.uptimeMillis();
        if (isShown() && getVisibility() == VISIBLE) {
            postInvalidateOnAnimation();
        }
    }

    private void scheduleNextFrame(long frameStartMs) {
        removeCallbacks(frameRunnable);
        long elapsed = SystemClock.uptimeMillis() - frameStartMs;
        long delay = Math.max(1L, frameIntervalMs - elapsed);
        postDelayed(frameRunnable, delay);
    }

    @Override
    protected void onVisibilityChanged(@NonNull View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (visibility == VISIBLE && isPlaying && isShown()) {
            lastAnimFrameMs = 0L;
            removeCallbacks(frameRunnable);
            postOnAnimation(frameRunnable);
        } else if (visibility != VISIBLE) {
            removeCallbacks(frameRunnable);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(frameRunnable);
        super.onDetachedFromWindow();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w <= 0 || h <= 0) return;
        shellBgPaint.setShader(new LinearGradient(
                0, 0, w, h,
                new int[]{0x99262244, 0xBB120D24, 0x99201B38},
                null,
                Shader.TileMode.CLAMP
        ));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        long now = SystemClock.uptimeMillis();
        if (lastAnimFrameMs == 0L) lastAnimFrameMs = now;
        float dt = Math.min(0.05f, Math.max(0.001f, (now - lastAnimFrameMs) / 1000f));
        lastAnimFrameMs = now;

        if (isPlaying) {
            if (now - lastSpectrumTimeMs > 900L) {
                phase += dt * 4.8f;
                float osc = 0.45f
                        + 0.22f * (float) Math.sin(phase)
                        + 0.12f * (float) Math.cos(phase * 1.6f);
                targetLevel = Math.max(0.14f, Math.min(0.88f, osc));
            }
        } else {
            targetLevel = 0.04f;
        }

        float diff = targetLevel - currentLevel;
        float rate = 1f - (float) Math.exp(-dt * (diff > 0f ? 28.0 : 13.5));
        currentLevel += diff * rate;

        if (currentLevel >= peakLevel) {
            peakLevel = currentLevel;
            lastPeakTimeMs = now;
        } else if (now - lastPeakTimeMs > 340L) {
            peakLevel = Math.max(currentLevel, peakLevel - dt * 0.55f);
        }

        float pad = 1.2f * density;
        float radius = (w - pad * 2f) * 0.5f;
        shellRect.set(pad, pad, w - pad, h - pad);
        canvas.drawRoundRect(shellRect, radius, radius, shellBgPaint);
        canvas.drawRoundRect(shellRect, radius, radius, shellBorderPaint);

        // Inner vertical glass highlight on left rim
        canvas.drawLine(
                shellRect.left + 1.8f * density,
                shellRect.top + radius,
                shellRect.left + 1.8f * density,
                shellRect.bottom - radius,
                shellHighlightPaint
        );

        float innerPadX = 3.2f * density;
        float innerPadY = 7.5f * density;
        float segLeft = pad + innerPadX;
        float segRight = w - pad - innerPadX;
        float usableH = Math.max(20f, (h - pad * 2f) - innerPadY * 2f);
        float gap = 1.8f * density;
        float segH = Math.max(2f * density, (usableH - gap * (SEGMENT_COUNT - 1)) / SEGMENT_COUNT);
        float segRadius = segH * 0.45f;

        int activeCount = Math.round(currentLevel * SEGMENT_COUNT);
        int peakIndex = Math.min(SEGMENT_COUNT - 1, Math.max(0, Math.round(peakLevel * (SEGMENT_COUNT - 1))));

        for (int i = 0; i < SEGMENT_COUNT; i++) {
            float norm = (float) i / (SEGMENT_COUNT - 1);
            float bottom = (h - pad - innerPadY) - i * (segH + gap);
            float top = bottom - segH;
            segRect.set(segLeft, top, segRight, bottom);

            int ledColor = colorForSegment(norm, colorScheme);
            if (i < activeCount) {
                ledGlowPaint.setColor(withAlpha(ledColor, 0x45));
                glowRect.set(segRect.left - 1.2f * density, segRect.top - 0.6f * density,
                        segRect.right + 1.2f * density, segRect.bottom + 0.6f * density);
                canvas.drawRoundRect(glowRect, segRadius, segRadius, ledGlowPaint);

                ledOnPaint.setColor(ledColor);
                canvas.drawRoundRect(segRect, segRadius, segRadius, ledOnPaint);
            } else if (i == peakIndex && isPlaying && peakLevel > 0.08f) {
                peakPaint.setColor(ledColor);
                canvas.drawRoundRect(segRect, segRadius, segRadius, peakPaint);
            } else {
                canvas.drawRoundRect(segRect, segRadius, segRadius, ledOffPaint);
            }
        }

        if ((isPlaying || Math.abs(diff) > 0.01f || peakLevel > currentLevel + 0.01f) && isShown()) {
            scheduleNextFrame(now);
        } else {
            lastAnimFrameMs = 0L;
        }
    }

    private static int colorForSegment(float norm, LedColorScheme scheme) {
        if (scheme == LedColorScheme.BLUE_CYAN) {
            if (norm < 0.50f) {
                return lerpColor(0xFF2563EB, 0xFF3B82F6, norm / 0.50f);
            } else if (norm < 0.85f) {
                return lerpColor(0xFF3B82F6, 0xFF06B6D4, (norm - 0.50f) / 0.35f);
            } else {
                return lerpColor(0xFF06B6D4, 0xFF22D3EE, (norm - 0.85f) / 0.15f);
            }
        }
        if (scheme == LedColorScheme.CYBERPUNK_YELLOW_CYAN) {
            if (norm < 0.55f) {
                return lerpColor(0xFF06B6D4, 0xFF22D3EE, norm / 0.55f);
            } else if (norm < 0.85f) {
                return lerpColor(0xFF22D3EE, 0xFFFACC15, (norm - 0.55f) / 0.30f);
            } else {
                return lerpColor(0xFFFACC15, 0xFFFEF08A, (norm - 0.85f) / 0.15f);
            }
        }
        if (norm < 0.48f) {
            return lerpColor(0xFF22C55E, 0xFF84CC16, norm / 0.48f);
        } else if (norm < 0.72f) {
            return lerpColor(0xFF84CC16, 0xFFFACC15, (norm - 0.48f) / 0.24f);
        } else if (norm < 0.87f) {
            return lerpColor(0xFFFACC15, 0xFFF97316, (norm - 0.72f) / 0.15f);
        } else {
            return lerpColor(0xFFF97316, 0xFFEF4444, (norm - 0.87f) / 0.13f);
        }
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }

    private static int lerpColor(int a, int b, float t) {
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
