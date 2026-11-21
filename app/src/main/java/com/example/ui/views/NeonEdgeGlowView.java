package com.example.ui.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Lightweight beat-reactive neon perimeter frame that pulses along screen edges with sub-bass hits.
 */
public class NeonEdgeGlowView extends View {

    private final Paint outerGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint innerStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF frameRect = new RectF();

    private boolean active = false;
    private boolean rainbowCycle = false;
    private float huePhase = 0f;
    private float targetBass = 0f;
    private float smoothBass = 0f;
    private int primaryColor = 0xFFEC4899;
    private int secondaryColor = 0xFF22D3EE;
    private float density = 1f;
    private long lastAnimMs = 0L;
    private long frameIntervalMs = 26L;
    private final Runnable frameRunnable = this::invalidate;

    public NeonEdgeGlowView(Context context) {
        super(context);
        init(context);
    }

    public NeonEdgeGlowView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public NeonEdgeGlowView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        density = context.getResources().getDisplayMetrics().density;
        outerGlowPaint.setStyle(Paint.Style.STROKE);
        outerGlowPaint.setStrokeWidth(11f * density);
        innerStrokePaint.setStyle(Paint.Style.STROKE);
        innerStrokePaint.setStrokeWidth(2.6f * density);
    }

    public void setEdgeColors(int primary, int secondary) {
        this.primaryColor = primary;
        this.secondaryColor = secondary;
    }

    public void setRainbowCycle(boolean rainbow) {
        this.rainbowCycle = rainbow;
    }

    public void setTargetFps(int fps) {
        int clamped = Math.max(14, Math.min(48, fps));
        this.frameIntervalMs = Math.max(20L, Math.round(1000.0 / clamped));
    }

    public void setActive(boolean active) {
        if (this.active == active) return;
        this.active = active;
        lastAnimMs = 0L;
        if (active && isShown()) {
            removeCallbacks(frameRunnable);
            postOnAnimation(frameRunnable);
        } else {
            removeCallbacks(frameRunnable);
            invalidate();
        }
    }

    public void onBassSample(float bassEnergy) {
        this.targetBass = Math.max(0f, Math.min(1f, bassEnergy));
    }

    @Override
    protected void onVisibilityChanged(@NonNull View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (visibility == VISIBLE && active && isShown()) {
            lastAnimMs = 0L;
            removeCallbacks(frameRunnable);
            postOnAnimation(frameRunnable);
        } else {
            removeCallbacks(frameRunnable);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(frameRunnable);
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!active) return;
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        long now = SystemClock.uptimeMillis();
        if (lastAnimMs == 0L) lastAnimMs = now;
        float dt = Math.min(0.06f, Math.max(0.004f, (now - lastAnimMs) / 1000f));
        lastAnimMs = now;

        float rate = 1f - (float) Math.exp(-dt * (targetBass > smoothBass ? 24.0 : 9.0));
        smoothBass += (targetBass - smoothBass) * rate;
        if (rainbowCycle) {
            huePhase = (huePhase + dt * (95f + smoothBass * 180f)) % 360f;
        }

        if (smoothBass > 0.04f || rainbowCycle) {
            float effective = rainbowCycle ? Math.max(0.28f, smoothBass) : smoothBass;
            int outerAlpha = Math.min(165, Math.round((0.14f + 0.45f * effective) * 255f));
            int innerAlpha = Math.min(235, Math.round((0.28f + 0.65f * effective) * 255f));
            int col;
            if (rainbowCycle) {
                col = com.example.ui.theme.ReactiveButtonAnimator.sampleCycleColor(huePhase, false);
            } else {
                col = smoothBass > 0.62f ? primaryColor : secondaryColor;
            }

            outerGlowPaint.setStrokeWidth(((rainbowCycle ? 8f : 6f) + 12f * effective) * density);
            outerGlowPaint.setColor((col & 0x00FFFFFF) | (outerAlpha << 24));
            innerStrokePaint.setColor((col & 0x00FFFFFF) | (innerAlpha << 24));

            float pad = 3f * density;
            float r = 20f * density;
            frameRect.set(pad, pad, w - pad, h - pad);
            canvas.drawRoundRect(frameRect, r, r, outerGlowPaint);
            canvas.drawRoundRect(frameRect, r, r, innerStrokePaint);
        }

        if (active && isShown()) {
            removeCallbacks(frameRunnable);
            long delay = Math.max(16L, frameIntervalMs - (SystemClock.uptimeMillis() - now));
            postDelayed(frameRunnable, delay);
        }
    }
}
