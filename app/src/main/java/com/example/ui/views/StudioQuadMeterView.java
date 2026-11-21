package com.example.ui.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.Locale;

/**
 * Studio 4-Channel Monitor Console:
 * Dark grey chassis with Green + Yellow precision VU meters for L, R, Bass, and Treble.
 */
public class StudioQuadMeterView extends View {

    private static final String[] CHANNEL_NAMES = {"L CH", "R CH", "BASS", "TREBLE"};
    private static final int SEGMENT_COUNT = 12;

    private final Paint chassisPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint chassisBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cellBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cellBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arcBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arcActivePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint needlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint needleGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pivotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint titlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint barSegPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final RectF outerRect = new RectF();
    private final RectF cellRect = new RectF();
    private final RectF arcRect = new RectF();
    private final RectF barRect = new RectF();

    private final float[] targets = {0.06f, 0.06f, 0.06f, 0.06f};
    private final float[] currents = {0.06f, 0.06f, 0.06f, 0.06f};

    private boolean isPlaying = false;
    private long lastSpectrumTimeMs = 0L;
    private long lastAnimFrameMs = 0L;
    private float phase = 0f;
    private float density = 1f;
    private long frameIntervalMs = 20L;
    private final Runnable frameRunnable = this::invalidate;

    public StudioQuadMeterView(Context context) {
        super(context);
        init(context);
    }

    public StudioQuadMeterView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public StudioQuadMeterView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        density = context.getResources().getDisplayMetrics().density;

        chassisPaint.setStyle(Paint.Style.FILL);
        chassisBorderPaint.setStyle(Paint.Style.STROKE);
        chassisBorderPaint.setStrokeWidth(1.4f * density);
        chassisBorderPaint.setColor(0xFF4B5563);

        cellBgPaint.setStyle(Paint.Style.FILL);
        cellBgPaint.setColor(0xFF181C22);

        cellBorderPaint.setStyle(Paint.Style.STROKE);
        cellBorderPaint.setStrokeWidth(1.1f * density);
        cellBorderPaint.setColor(0xFF374151);

        arcBgPaint.setStyle(Paint.Style.STROKE);
        arcBgPaint.setStrokeWidth(3.6f * density);
        arcBgPaint.setStrokeCap(Paint.Cap.ROUND);
        arcBgPaint.setColor(0xFF2D333D);

        arcActivePaint.setStyle(Paint.Style.STROKE);
        arcActivePaint.setStrokeWidth(3.6f * density);
        arcActivePaint.setStrokeCap(Paint.Cap.ROUND);

        needleGlowPaint.setStyle(Paint.Style.STROKE);
        needleGlowPaint.setStrokeWidth(4.0f * density);
        needleGlowPaint.setStrokeCap(Paint.Cap.ROUND);

        needlePaint.setStyle(Paint.Style.STROKE);
        needlePaint.setStrokeWidth(1.8f * density);
        needlePaint.setStrokeCap(Paint.Cap.ROUND);
        needlePaint.setColor(0xFFFDE047);

        pivotPaint.setStyle(Paint.Style.FILL);
        pivotPaint.setColor(0xFF374151);

        titlePaint.setColor(0xFF9CA3AF);
        titlePaint.setTextSize(10f * density);
        titlePaint.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        titlePaint.setLetterSpacing(0.12f);

        labelPaint.setColor(0xFF22C55E);
        labelPaint.setTextSize(11f * density);
        labelPaint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));

        dbPaint.setColor(0xFFFACC15);
        dbPaint.setTextAlign(Paint.Align.RIGHT);
        dbPaint.setTextSize(9.5f * density);
        dbPaint.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));

        barSegPaint.setStyle(Paint.Style.FILL);
    }

    public void setTargetFps(int fps) {
        int clamped = Math.max(20, Math.min(60, fps));
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

    public void updateFourChannels(float leftLevel, float rightLevel, float bassEnergy, float trebleEnergy) {
        targets[0] = Math.max(0.04f, Math.min(1f, leftLevel));
        targets[1] = Math.max(0.04f, Math.min(1f, rightLevel));
        targets[2] = Math.max(0.04f, Math.min(1f, bassEnergy * 1.22f));
        targets[3] = Math.max(0.04f, Math.min(1f, trebleEnergy * 1.25f));
        lastSpectrumTimeMs = SystemClock.uptimeMillis();
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
        chassisPaint.setShader(new LinearGradient(
                0f, 0f, w, h,
                new int[]{0xFF252A32, 0xFF171A20, 0xFF1F232B},
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
                phase += dt * 4.5f;
                for (int i = 0; i < 4; i++) {
                    targets[i] = 0.32f + 0.25f * (float) Math.sin(phase + i * 0.9f);
                }
            }
        } else {
            for (int i = 0; i < 4; i++) {
                targets[i] = 0.04f;
            }
        }

        boolean needsMore = isPlaying;
        for (int i = 0; i < 4; i++) {
            float diff = targets[i] - currents[i];
            float rate = 1f - (float) Math.exp(-dt * (diff > 0f ? 28.0 : 12.5));
            currents[i] += diff * rate;
            if (Math.abs(diff) > 0.008f) needsMore = true;
        }

        float pad = 2f * density;
        outerRect.set(pad, pad, w - pad, h - pad);
        canvas.drawRoundRect(outerRect, 18f * density, 18f * density, chassisPaint);
        canvas.drawRoundRect(outerRect, 18f * density, 18f * density, chassisBorderPaint);

        float contentPad = 10f * density;
        canvas.drawText("STUDIO 4-CH MONITOR • L / R / BASS / TREBLE", contentPad + 2f * density, pad + 17f * density, titlePaint);

        float gridTop = pad + 24f * density;
        float gridBottom = h - pad - 8f * density;
        float gap = 8f * density;
        float cellW = (w - contentPad * 2f - gap) * 0.5f;
        float cellH = (gridBottom - gridTop - gap) * 0.5f;

        for (int idx = 0; idx < 4; idx++) {
            int col = idx % 2;
            int row = idx / 2;
            float left = contentPad + col * (cellW + gap);
            float top = gridTop + row * (cellH + gap);
            cellRect.set(left, top, left + cellW, top + cellH);
            drawStudioMeterCell(canvas, cellRect, CHANNEL_NAMES[idx], currents[idx], idx >= 2);
        }

        if (needsMore && isShown()) {
            scheduleNextFrame(now);
        } else {
            lastAnimFrameMs = 0L;
        }
    }

    private void drawStudioMeterCell(Canvas canvas, RectF r, String label, float level, boolean warmAccent) {
        canvas.drawRoundRect(r, 12f * density, 12f * density, cellBgPaint);
        canvas.drawRoundRect(r, 12f * density, 12f * density, cellBorderPaint);

        float pad = 8f * density;
        labelPaint.setColor(warmAccent ? 0xFFFACC15 : 0xFF22C55E);
        canvas.drawText(label, r.left + pad, r.top + 15f * density, labelPaint);

        int db = isPlaying ? Math.round(-40f + level * 43f) : -48;
        canvas.drawText(String.format(Locale.US, "%ddB", db), r.right - pad, r.top + 15f * density, dbPaint);

        // Compact analog arc meter in middle of cell
        float cx = r.centerX();
        float arcR = Math.min(r.width() * 0.34f, r.height() * 0.48f);
        float pivotY = r.bottom - 18f * density;
        arcRect.set(cx - arcR, pivotY - arcR, cx + arcR, pivotY + arcR);

        canvas.drawArc(arcRect, -142f, 104f, false, arcBgPaint);
        int activeColor = level > 0.78f ? 0xFFFACC15 : (warmAccent ? 0xFF84CC16 : 0xFF22C55E);
        arcActivePaint.setColor(activeColor);
        canvas.drawArc(arcRect, -142f, 104f * Math.max(0.03f, level), false, arcActivePaint);

        double deg = -52.0 + level * 104.0;
        double rad = Math.toRadians(deg - 90.0);
        float tipX = cx + (float) Math.cos(rad) * (arcR - 1f * density);
        float tipY = pivotY + (float) Math.sin(rad) * (arcR - 1f * density);
        needleGlowPaint.setColor((activeColor & 0x00FFFFFF) | 0x45000000);
        canvas.drawLine(cx, pivotY, tipX, tipY, needleGlowPaint);
        canvas.drawLine(cx, pivotY, tipX, tipY, needlePaint);
        canvas.drawCircle(cx, pivotY, 4.5f * density, pivotPaint);

        // Crisp segmented LED strip at bottom of cell (Green -> Yellow)
        float barLeft = r.left + pad;
        float barRight = r.right - pad;
        float barTop = r.bottom - 11f * density;
        float barBottom = r.bottom - 5.5f * density;
        float segGap = 2f * density;
        float segW = Math.max(2f, ((barRight - barLeft) - segGap * (SEGMENT_COUNT - 1)) / SEGMENT_COUNT);
        int litCount = Math.round(level * SEGMENT_COUNT);

        for (int s = 0; s < SEGMENT_COUNT; s++) {
            float sx = barLeft + s * (segW + segGap);
            barRect.set(sx, barTop, sx + segW, barBottom);
            int segCol = s >= 8 ? 0xFFFACC15 : (s >= 5 ? 0xFF84CC16 : 0xFF22C55E);
            barSegPaint.setColor(s < litCount ? segCol : ((segCol & 0x00FFFFFF) | 0x26000000));
            canvas.drawRoundRect(barRect, 1.5f * density, 1.5f * density, barSegPaint);
        }
    }
}
