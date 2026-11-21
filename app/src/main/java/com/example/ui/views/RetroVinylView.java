package com.example.ui.views;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class RetroVinylView extends View {
    private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint vinylBasePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sheenPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint groovePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelFallbackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelArtPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint spindleOuterPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint spindleInnerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Paint armShadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint armBasePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint armTubePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint armHeadPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path armPath = new Path();
    private final RectF shellRect = new RectF();

    private Bitmap albumBitmap;
    private BitmapShader artShader;
    private final Matrix artMatrix = new Matrix();

    private boolean isPlaying = false;
    private boolean centerAligned = false;
    private float rotationDeg = 0f;
    private float armProgress = 0f; // 0 = parked, 1 = on record
    private long lastFrameTime = 0L;
    private float density = 1f;
    private float cachedPlatterRadius = 0f;
    private float cachedCx = 0f;
    private float cachedCy = 0f;
    private long frameIntervalMs = 16L;
    private final Runnable frameRunnable = this::invalidate;

    public RetroVinylView(Context context) {
        super(context);
        init(context);
    }

    public RetroVinylView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public RetroVinylView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        density = context.getResources().getDisplayMetrics().density;

        shadowPaint.setStyle(Paint.Style.FILL);
        shadowPaint.setColor(0x365C3317);

        vinylBasePaint.setStyle(Paint.Style.FILL);
        vinylBasePaint.setColor(0xFF1A1614);

        sheenPaint.setStyle(Paint.Style.FILL);

        groovePaint.setStyle(Paint.Style.STROKE);
        groovePaint.setStrokeWidth(0.9f * density);

        labelBorderPaint.setStyle(Paint.Style.STROKE);
        labelBorderPaint.setStrokeWidth(2f * density);
        labelBorderPaint.setColor(0xFFD69E56);

        labelFallbackPaint.setStyle(Paint.Style.FILL);
        labelFallbackPaint.setColor(0xFFC4551A);

        labelArtPaint.setStyle(Paint.Style.FILL);

        spindleOuterPaint.setStyle(Paint.Style.FILL);
        spindleOuterPaint.setColor(0xFFE0B46C);

        spindleInnerPaint.setStyle(Paint.Style.FILL);
        spindleInnerPaint.setColor(0xFF5C3A1E);

        armShadowPaint.setStyle(Paint.Style.STROKE);
        armShadowPaint.setStrokeCap(Paint.Cap.ROUND);
        armShadowPaint.setStrokeWidth(7f * density);
        armShadowPaint.setColor(0x354A250C);

        armBasePaint.setStyle(Paint.Style.FILL);
        armBasePaint.setColor(0xFFC6A06B);

        armTubePaint.setStyle(Paint.Style.STROKE);
        armTubePaint.setStrokeCap(Paint.Cap.ROUND);
        armTubePaint.setStrokeJoin(Paint.Join.ROUND);
        armTubePaint.setStrokeWidth(5f * density);
        armTubePaint.setColor(0xFFD9BE91);

        armHeadPaint.setStyle(Paint.Style.FILL);
        armHeadPaint.setColor(0xFFF3E2C4);
    }

    public void setTargetFps(int fps) {
        int clamped = Math.max(18, Math.min(60, fps));
        this.frameIntervalMs = Math.max(16L, Math.round(1000.0 / clamped));
    }

    public void setCenterAligned(boolean centered) {
        if (this.centerAligned != centered) {
            this.centerAligned = centered;
            int w = getWidth();
            int h = getHeight();
            if (w > 0 && h > 0) {
                recomputePlatterGeometry(w, h);
                invalidate();
            }
        }
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
        lastFrameTime = SystemClock.uptimeMillis();
        if (isShown()) {
            removeCallbacks(frameRunnable);
            postOnAnimation(frameRunnable);
        }
    }

    public void setAlbumBitmap(@Nullable Bitmap bitmap) {
        this.albumBitmap = bitmap;
        if (bitmap != null && !bitmap.isRecycled()) {
            artShader = new BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            labelArtPaint.setShader(artShader);
        } else {
            artShader = null;
            labelArtPaint.setShader(null);
        }
        if (isShown()) {
            invalidate();
        }
    }

    @Override
    protected void onVisibilityChanged(@NonNull View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (visibility == VISIBLE && (isPlaying || armProgress > 0f) && isShown()) {
            lastFrameTime = SystemClock.uptimeMillis();
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
        recomputePlatterGeometry(w, h);
    }

    private void recomputePlatterGeometry(int w, int h) {
        cachedPlatterRadius = Math.min(w * 0.43f, h * 0.45f);
        cachedCx = centerAligned
                ? Math.max(cachedPlatterRadius + 8f * density, w * 0.5f - cachedPlatterRadius * 0.14f)
                : cachedPlatterRadius + 8f * density;
        cachedCy = h * 0.52f;
        sheenPaint.setShader(new SweepGradient(
                cachedCx, cachedCy,
                new int[]{0x00FFFFFF, 0x26FFF5E4, 0x05FFFFFF, 0x00FFFFFF, 0x22FFF5E4, 0x00FFFFFF},
                new float[]{0f, 0.14f, 0.28f, 0.5f, 0.64f, 1f}
        ));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        long now = SystemClock.uptimeMillis();
        if (lastFrameTime == 0L) lastFrameTime = now;
        float dt = Math.min(0.05f, Math.max(0.001f, (now - lastFrameTime) / 1000f));
        lastFrameTime = now;

        boolean keepAnimating = false;
        if (isPlaying) {
            rotationDeg = (rotationDeg + dt * 52f) % 360f;
            if (armProgress < 1f) {
                armProgress = Math.min(1f, armProgress + dt * 3.8f);
            }
            keepAnimating = true;
        } else if (armProgress > 0f) {
            armProgress = Math.max(0f, armProgress - dt * 3.8f);
            keepAnimating = true;
        }

        float platterRadius = cachedPlatterRadius > 0f ? cachedPlatterRadius : Math.min(w * 0.43f, h * 0.45f);
        float cx = cachedCx > 0f ? cachedCx : platterRadius + 8f * density;
        float cy = cachedCy > 0f ? cachedCy : h * 0.52f;

        // Soft drop shadow
        canvas.drawCircle(cx + 2f * density, cy + 4f * density, platterRadius + 2f * density, shadowPaint);

        // Rotating vinyl platter
        canvas.save();
        canvas.rotate(rotationDeg, cx, cy);

        canvas.drawCircle(cx, cy, platterRadius, vinylBasePaint);
        canvas.drawCircle(cx, cy, platterRadius * 0.97f, sheenPaint);

        // Concentric vinyl micro-grooves
        float labelRadius = platterRadius * 0.37f;
        int grooveCount = 14;
        for (int i = 1; i <= grooveCount; i++) {
            float frac = (float) i / (grooveCount + 1);
            float r = labelRadius + 5f * density + frac * (platterRadius - labelRadius - 10f * density);
            groovePaint.setColor(i % 4 == 0 ? 0x33FFFFFF : 0x26000000);
            canvas.drawCircle(cx, cy, r, groovePaint);
        }

        // Center album label
        if (artShader != null && albumBitmap != null && !albumBitmap.isRecycled()) {
            float bw = albumBitmap.getWidth();
            float bh = albumBitmap.getHeight();
            float diameter = labelRadius * 2f;
            float scale = Math.max(diameter / bw, diameter / bh);
            artMatrix.reset();
            artMatrix.setScale(scale, scale);
            artMatrix.postTranslate(cx - (bw * scale) * 0.5f, cy - (bh * scale) * 0.5f);
            artShader.setLocalMatrix(artMatrix);
            canvas.drawCircle(cx, cy, labelRadius, labelArtPaint);
        } else {
            canvas.drawCircle(cx, cy, labelRadius, labelFallbackPaint);
        }
        canvas.drawCircle(cx, cy, labelRadius, labelBorderPaint);

        // Center brass spindle
        canvas.drawCircle(cx, cy, 6.5f * density, spindleOuterPaint);
        canvas.drawCircle(cx, cy, 2.5f * density, spindleInnerPaint);

        canvas.restore();

        // Articulated tonearm anchored at top-right of platter
        float pivotX = Math.min(w - 16f * density, cx + platterRadius * 0.94f);
        float pivotY = Math.max(16f * density, cy - platterRadius * 0.78f);

        // Pivot base
        canvas.drawCircle(pivotX + 1.5f * density, pivotY + 2f * density, 14f * density, shadowPaint);
        canvas.drawCircle(pivotX, pivotY, 13f * density, armBasePaint);
        canvas.drawCircle(pivotX, pivotY, 7f * density, spindleInnerPaint);

        float armAngleDeg = -8f + armProgress * 28f;
        canvas.save();
        canvas.rotate(armAngleDeg, pivotX, pivotY);

        float armLen = platterRadius * 0.95f;
        float elbowX = pivotX - armLen * 0.14f;
        float elbowY = pivotY + armLen * 0.58f;
        float tipX = pivotX - armLen * 0.36f;
        float tipY = pivotY + armLen * 0.86f;

        armPath.reset();
        armPath.moveTo(pivotX, pivotY);
        armPath.lineTo(elbowX, elbowY);
        armPath.lineTo(tipX, tipY);

        canvas.save();
        canvas.translate(2f * density, 3f * density);
        canvas.drawPath(armPath, armShadowPaint);
        canvas.restore();

        canvas.drawPath(armPath, armTubePaint);

        // Headshell cartridge
        canvas.save();
        canvas.translate(tipX, tipY);
        canvas.rotate(28f);
        shellRect.set(-6.5f * density, -4f * density, 6.5f * density, 14f * density);
        canvas.drawRoundRect(shellRect, 3f * density, 3f * density, armHeadPaint);
        canvas.restore();

        canvas.restore();

        if (keepAnimating && isShown()) {
            scheduleNextFrame(now);
        }
    }
}
