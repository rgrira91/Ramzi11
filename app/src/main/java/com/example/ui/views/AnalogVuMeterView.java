package com.example.ui.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class AnalogVuMeterView extends View {
    private static final String[] SCALE_LABELS = {"-20", "-10", "-7", "-5", "-3", "0", "+3"};
    private static final float[] SCALE_ANGLES = {-44f, -28f, -15f, -3f, 10f, 24f, 40f};

    private final Paint framePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint frameBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glassBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glassBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint screwPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint vuTitlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint needlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint needlePivotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF frameRect = new RectF();
    private final RectF glassRect = new RectF();
    private final RectF arcOval = new RectF();

    private float targetLevel = 0.08f;
    private float currentLevel = 0.08f;
    private boolean isPlaying = false;
    private float phase = 0f;
    private long lastLevelTime = 0L;
    private long lastAnimFrameMs = 0L;
    private float density = 1f;
    private long frameIntervalMs = 16L;
    private final Runnable frameRunnable = this::invalidate;

    public AnalogVuMeterView(Context context) {
        super(context);
        init(context);
    }

    public AnalogVuMeterView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public AnalogVuMeterView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        density = context.getResources().getDisplayMetrics().density;

        framePaint.setStyle(Paint.Style.FILL);
        framePaint.setColor(0xFFF6E9D2);

        frameBorderPaint.setStyle(Paint.Style.STROKE);
        frameBorderPaint.setStrokeWidth(1.5f * density);
        frameBorderPaint.setColor(0xFFD3B68A);

        glassBgPaint.setStyle(Paint.Style.FILL);

        glassBorderPaint.setStyle(Paint.Style.STROKE);
        glassBorderPaint.setStrokeWidth(2.2f * density);
        glassBorderPaint.setColor(0xFF6B492D);

        screwPaint.setStyle(Paint.Style.FILL);
        screwPaint.setColor(0xFFA07C53);

        tickPaint.setStyle(Paint.Style.STROKE);
        tickPaint.setStrokeCap(Paint.Cap.ROUND);

        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setTextSize(9.5f * density);
        labelPaint.setTypeface(Typeface.create(Typeface.SERIF, Typeface.BOLD));

        vuTitlePaint.setTextAlign(Paint.Align.CENTER);
        vuTitlePaint.setTextSize(15f * density);
        vuTitlePaint.setTypeface(Typeface.create(Typeface.SERIF, Typeface.BOLD));
        vuTitlePaint.setColor(0xFF422714);

        needlePaint.setStyle(Paint.Style.STROKE);
        needlePaint.setStrokeCap(Paint.Cap.ROUND);
        needlePaint.setStrokeWidth(2.6f * density);
        needlePaint.setColor(0xFFD13A1F);

        needlePivotPaint.setStyle(Paint.Style.FILL);
        needlePivotPaint.setColor(0xFF422714);
    }

    public void setTargetFps(int fps) {
        int clamped = Math.max(24, Math.min(60, fps));
        this.frameIntervalMs = Math.max(16L, Math.round(1000.0 / clamped));
    }

    private void scheduleNextFrame(long frameStartMs) {
        removeCallbacks(frameRunnable);
        long elapsed = SystemClock.uptimeMillis() - frameStartMs;
        long delay = Math.max(1L, frameIntervalMs - elapsed);
        postDelayed(frameRunnable, delay);
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

    public void updateLevel(float energy) {
        this.targetLevel = Math.max(0.05f, Math.min(1f, energy * 1.45f));
        this.lastLevelTime = SystemClock.uptimeMillis();
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
        glassBgPaint.setShader(new RadialGradient(
                w * 0.5f, h * 0.72f, Math.max(w, h) * 0.65f,
                new int[]{0xFFFFE59E, 0xFFF7C568, 0xFFDF9E3B},
                new float[]{0f, 0.55f, 1f},
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
            if (now - lastLevelTime > 380L) {
                phase += dt * 8.2f;
                float osc = 0.54f + 0.30f * (float) Math.sin(phase) * (float) Math.cos(phase * 0.61f);
                targetLevel = Math.max(0.18f, Math.min(0.92f, osc));
            }
        } else {
            targetLevel = 0.06f;
        }

        float diff = targetLevel - currentLevel;
        float rate = 1f - (float) Math.exp(-dt * (diff > 0f ? 18.0 : 8.0));
        currentLevel += diff * rate;

        float pad = 2f * density;
        frameRect.set(pad, pad, w - pad, h - pad);
        canvas.drawRoundRect(frameRect, 14f * density, 14f * density, framePaint);
        canvas.drawRoundRect(frameRect, 14f * density, 14f * density, frameBorderPaint);

        float inset = 10f * density;
        glassRect.set(inset, inset, w - inset, h - inset);
        canvas.drawRoundRect(glassRect, 10f * density, 10f * density, glassBgPaint);
        canvas.drawRoundRect(glassRect, 10f * density, 10f * density, glassBorderPaint);

        // 4 corner metallic screws
        float screwOffset = 5.5f * density;
        float screwR = 2.5f * density;
        canvas.drawCircle(screwOffset, screwOffset, screwR, screwPaint);
        canvas.drawCircle(w - screwOffset, screwOffset, screwR, screwPaint);
        canvas.drawCircle(screwOffset, h - screwOffset, screwR, screwPaint);
        canvas.drawCircle(w - screwOffset, h - screwOffset, screwR, screwPaint);

        float pivotX = w * 0.5f;
        float pivotY = h * 1.12f;
        float arcRadius = h * 0.82f;

        // Draw arc scale
        for (int i = 0; i < SCALE_LABELS.length; i++) {
            float angleDeg = SCALE_ANGLES[i];
            double rad = Math.toRadians(angleDeg - 90f);
            boolean isRedZone = i >= 5;
            tickPaint.setColor(isRedZone ? 0xFFC9321C : 0xFF3E2312);
            tickPaint.setStrokeWidth((isRedZone ? 2.2f : 1.5f) * density);

            float rOuter = arcRadius;
            float rInner = arcRadius - 7f * density;
            float x1 = pivotX + (float) Math.cos(rad) * rInner;
            float y1 = pivotY + (float) Math.sin(rad) * rInner;
            float x2 = pivotX + (float) Math.cos(rad) * rOuter;
            float y2 = pivotY + (float) Math.sin(rad) * rOuter;
            canvas.drawLine(x1, y1, x2, y2, tickPaint);

            float rText = arcRadius + 10f * density;
            float tx = pivotX + (float) Math.cos(rad) * rText;
            float ty = pivotY + (float) Math.sin(rad) * rText;
            labelPaint.setColor(isRedZone ? 0xFFC9321C : 0xFF3E2312);
            canvas.drawText(SCALE_LABELS[i], tx, ty, labelPaint);
        }

        // Red overload arc from 0 to +3
        tickPaint.setColor(0xFFC9321C);
        tickPaint.setStrokeWidth(2.4f * density);
        arcOval.set(pivotX - arcRadius, pivotY - arcRadius, pivotX + arcRadius, pivotY + arcRadius);
        canvas.drawArc(arcOval, -90f + 24f, 16f, false, tickPaint);
        tickPaint.setColor(0xFF3E2312);
        tickPaint.setStrokeWidth(1.5f * density);
        canvas.drawArc(arcOval, -90f - 44f, 68f, false, tickPaint);

        // "VU" center label
        canvas.drawText("VU", pivotX, h * 0.72f, vuTitlePaint);

        // Moving Needle
        float needleAngleDeg = -42f + currentLevel * 82f;
        double needleRad = Math.toRadians(needleAngleDeg - 90f);
        float needleLen = arcRadius + 4f * density;
        float nx = pivotX + (float) Math.cos(needleRad) * needleLen;
        float ny = pivotY + (float) Math.sin(needleRad) * needleLen;
        canvas.drawLine(pivotX, h - inset, nx, ny, needlePaint);
        canvas.drawCircle(pivotX, h - inset, 5f * density, needlePivotPaint);

        if ((isPlaying || Math.abs(diff) > 0.01f) && isShown()) {
            scheduleNextFrame(now);
        } else {
            lastAnimFrameMs = 0L;
        }
    }
}
