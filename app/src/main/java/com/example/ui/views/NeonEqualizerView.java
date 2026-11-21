package com.example.ui.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import androidx.annotation.Nullable;
import com.example.audio.AudioFxEngine;

public class NeonEqualizerView extends View {
    public interface OnBandChangedListener {
        void onBandChanged(int bandIndex, float gainDb);
    }

    private static final int BAND_COUNT = AudioFxEngine.UI_BAND_COUNT;
    private static final String[] BAND_LABELS = {
            "31", "62", "125", "250", "500", "1K", "2K", "4K", "8K", "16K"
    };

    private static final int RESPONSE_POINTS = 64;

    private final float[] gainsDb = new float[BAND_COUNT];
    private final float[] responseCurveDb = new float[RESPONSE_POINTS];
    private boolean hasResponseCurve = false;
    private final int[] bandColors = new int[BAND_COUNT];
    private final Paint trackBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint centerLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint curveGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint curveStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint activeFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint activeGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thumbHaloPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thumbCorePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF tempRect = new RectF();
    private final Path curvePath = new Path();

    private Shader cachedHorizShader;
    private OnBandChangedListener listener;
    private int activeTouchBand = -1;
    private float density = 1f;
    private boolean eqEnabled = true;

    private int colorLow = 0xFF22D3EE;
    private int colorMid = 0xFFA855F7;
    private int colorHigh = 0xFFF43F9E;

    public NeonEqualizerView(Context context) {
        super(context);
        init(context);
    }

    public NeonEqualizerView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public NeonEqualizerView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        density = context.getResources().getDisplayMetrics().density;

        trackBgPaint.setStyle(Paint.Style.FILL);
        trackBgPaint.setColor(0x33FFFFFF);

        centerLinePaint.setStyle(Paint.Style.STROKE);
        centerLinePaint.setStrokeWidth(1f * density);
        centerLinePaint.setColor(0x38FFFFFF);

        curveGlowPaint.setStyle(Paint.Style.STROKE);
        curveGlowPaint.setStrokeCap(Paint.Cap.ROUND);
        curveGlowPaint.setStrokeJoin(Paint.Join.ROUND);
        curveGlowPaint.setStrokeWidth(4.5f * density);

        curveStrokePaint.setStyle(Paint.Style.STROKE);
        curveStrokePaint.setStrokeCap(Paint.Cap.ROUND);
        curveStrokePaint.setStrokeJoin(Paint.Join.ROUND);
        curveStrokePaint.setStrokeWidth(1.8f * density);

        activeFillPaint.setStyle(Paint.Style.FILL);
        activeGlowPaint.setStyle(Paint.Style.FILL);
        thumbHaloPaint.setStyle(Paint.Style.FILL);

        thumbCorePaint.setStyle(Paint.Style.FILL);
        thumbCorePaint.setColor(Color.WHITE);

        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setTextSize(10f * density);
        labelPaint.setColor(0xFFC4B5FD);

        dbPaint.setTextAlign(Paint.Align.CENTER);
        dbPaint.setTextSize(8.8f * density);
        dbPaint.setFakeBoldText(true);
        dbPaint.setColor(0xFFE2E8F0);

        recomputeBandColors();
    }

    public void setPaletteColors(int secondaryNeon, int tertiaryGlow, int primaryNeon) {
        if (this.colorLow == secondaryNeon
                && this.colorMid == tertiaryGlow
                && this.colorHigh == primaryNeon) {
            return;
        }
        this.colorLow = secondaryNeon;
        this.colorMid = tertiaryGlow;
        this.colorHigh = primaryNeon;
        recomputeBandColors();
        rebuildHorizShader(getWidth());
        if (isShown()) {
            invalidate();
        }
    }

    public void setOnBandChangedListener(OnBandChangedListener listener) {
        this.listener = listener;
    }

    public void setBandsDb(float[] newGains) {
        if (newGains == null) return;
        int len = Math.min(BAND_COUNT, newGains.length);
        System.arraycopy(newGains, 0, gainsDb, 0, len);
        invalidate();
    }

    public void setEqEnabled(boolean enabled) {
        if (this.eqEnabled == enabled) return;
        this.eqEnabled = enabled;
        invalidate();
    }

    public void setResponseCurveDb(float[] dbPoints) {
        if (dbPoints == null || dbPoints.length == 0) return;
        int len = Math.min(RESPONSE_POINTS, dbPoints.length);
        for (int i = 0; i < RESPONSE_POINTS; i++) {
            float srcIdx = (float) i / Math.max(1, RESPONSE_POINTS - 1) * (len - 1);
            int idx0 = Math.min(len - 1, (int) Math.floor(srcIdx));
            int idx1 = Math.min(len - 1, idx0 + 1);
            float frac = srcIdx - idx0;
            responseCurveDb[i] = dbPoints[idx0] * (1f - frac) + dbPoints[idx1] * frac;
        }
        hasResponseCurve = true;
        invalidate();
    }

    private void recomputeBandColors() {
        for (int i = 0; i < BAND_COUNT; i++) {
            float t = (float) i / (BAND_COUNT - 1);
            bandColors[i] = t < 0.5f
                    ? blendColor(colorLow, colorMid, t * 2f)
                    : blendColor(colorMid, colorHigh, (t - 0.5f) * 2f);
        }
    }

    private void rebuildHorizShader(int w) {
        if (w <= 0) {
            cachedHorizShader = null;
            return;
        }
        cachedHorizShader = new LinearGradient(
                0f, 0f, w, 0f,
                new int[]{colorLow, colorMid, colorHigh},
                new float[]{0f, 0.5f, 1f},
                Shader.TileMode.CLAMP
        );
    }

    private static int blendColor(int c1, int c2, float ratio) {
        float inv = 1f - ratio;
        int a = Math.round(Color.alpha(c1) * inv + Color.alpha(c2) * ratio);
        int r = Math.round(Color.red(c1) * inv + Color.red(c2) * ratio);
        int g = Math.round(Color.green(c1) * inv + Color.green(c2) * ratio);
        int b = Math.round(Color.blue(c1) * inv + Color.blue(c2) * ratio);
        return Color.argb(a, r, g, b);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        rebuildHorizShader(w);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        float padX = 10f * density;
        float topPad = 24f * density;
        float labelArea = 22f * density;
        float bottomTrackY = h - labelArea - 8f * density;
        float trackHeight = Math.max(40f * density, bottomTrackY - topPad);
        float colWidth = (w - padX * 2f) / BAND_COUNT;
        float trackWidth = 4.5f * density;
        float thumbRadius = 7.2f * density;
        float haloRadius = 12.8f * density;

        // 0 dB center horizontal reference line
        float zeroY = topPad + trackHeight * 0.5f;
        canvas.drawLine(padX, zeroY, w - padX, zeroY, centerLinePaint);

        // Live combined DSP biquad Frequency Response Curve behind sliders
        if (hasResponseCurve) {
            float firstColCx = padX + colWidth * 0.5f;
            float lastColCx = padX + (BAND_COUNT - 1) * colWidth + colWidth * 0.5f;
            float curveSpanX = Math.max(1f, lastColCx - firstColCx);
            curvePath.reset();
            for (int i = 0; i < RESPONSE_POINTS; i++) {
                float normX = (float) i / (RESPONSE_POINTS - 1);
                float x = firstColCx + normX * curveSpanX;
                float clampedDb = Math.max(AudioFxEngine.MIN_GAIN_DB, Math.min(AudioFxEngine.MAX_GAIN_DB, responseCurveDb[i]));
                float normY = (clampedDb - AudioFxEngine.MIN_GAIN_DB)
                        / (AudioFxEngine.MAX_GAIN_DB - AudioFxEngine.MIN_GAIN_DB);
                float y = bottomTrackY - normY * trackHeight;
                if (i == 0) {
                    curvePath.moveTo(x, y);
                } else {
                    curvePath.lineTo(x, y);
                }
            }
            if (eqEnabled) {
                curveGlowPaint.setColor((colorLow & 0x00FFFFFF) | 0x35000000);
                curveStrokePaint.setColor((colorLow & 0x00FFFFFF) | 0xCC000000);
            } else {
                curveGlowPaint.setColor(0x206B7280);
                curveStrokePaint.setColor(0x886B7280);
            }
            canvas.drawPath(curvePath, curveGlowPaint);
            canvas.drawPath(curvePath, curveStrokePaint);
        }

        activeFillPaint.setShader(eqEnabled ? cachedHorizShader : null);
        if (!eqEnabled) {
            activeFillPaint.setColor(0xFF6B7280);
        }

        for (int i = 0; i < BAND_COUNT; i++) {
            float cx = padX + i * colWidth + colWidth * 0.5f;
            float norm = (gainsDb[i] - AudioFxEngine.MIN_GAIN_DB)
                    / (AudioFxEngine.MAX_GAIN_DB - AudioFxEngine.MIN_GAIN_DB);
            norm = Math.max(0f, Math.min(1f, norm));
            float thumbY = bottomTrackY - norm * trackHeight;

            // dB readout above every slider
            int roundedDb = Math.round(gainsDb[i]);
            String dbText;
            if (activeTouchBand == i) {
                dbText = String.format(java.util.Locale.US, "%+.1f", gainsDb[i]);
            } else if (roundedDb > 0) {
                dbText = "+" + roundedDb;
            } else {
                dbText = String.valueOf(roundedDb);
            }
            int color = eqEnabled ? bandColors[i] : 0xFF6B7280;
            dbPaint.setColor(activeTouchBand == i ? Color.WHITE : (eqEnabled ? blendColor(color, Color.WHITE, 0.35f) : 0xFF94A3B8));
            canvas.drawText(dbText, cx, 14.5f * density, dbPaint);

            // 1. Background rail
            tempRect.set(cx - trackWidth * 0.5f, topPad, cx + trackWidth * 0.5f, bottomTrackY);
            canvas.drawRoundRect(tempRect, trackWidth, trackWidth, trackBgPaint);

            // 2. Active neon column between 0dB center line and thumb (or from bottom up)
            activeGlowPaint.setColor((color & 0x00FFFFFF) | 0x38000000);
            tempRect.set(cx - trackWidth * 1.15f, thumbY, cx + trackWidth * 1.15f, bottomTrackY);
            canvas.drawRoundRect(tempRect, trackWidth, trackWidth, activeGlowPaint);

            tempRect.set(cx - trackWidth * 0.55f, thumbY, cx + trackWidth * 0.55f, bottomTrackY);
            canvas.drawRoundRect(tempRect, trackWidth, trackWidth, activeFillPaint);

            // 3. Luminous neon thumb + outer halo
            float activeHaloR = (activeTouchBand == i) ? haloRadius * 1.25f : haloRadius;
            thumbHaloPaint.setColor(eqEnabled ? ((color & 0x00FFFFFF) | 0x2E000000) : 0x226B7280);
            canvas.drawCircle(cx, thumbY, activeHaloR * 1.25f, thumbHaloPaint);
            thumbHaloPaint.setColor(eqEnabled ? ((color & 0x00FFFFFF) | 0x5E000000) : 0x446B7280);
            canvas.drawCircle(cx, thumbY, activeHaloR, thumbHaloPaint);
            canvas.drawCircle(cx, thumbY, thumbRadius, thumbCorePaint);

            // 4. Frequency label
            labelPaint.setColor(activeTouchBand == i ? Color.WHITE : 0xFFC4B5FD);
            canvas.drawText(BAND_LABELS[i], cx, h - 5f * density, labelPaint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return false;

        float padX = 10f * density;
        float topPad = 24f * density;
        float labelArea = 22f * density;
        float bottomTrackY = h - labelArea - 8f * density;
        float trackHeight = Math.max(40f * density, bottomTrackY - topPad);
        float colWidth = (w - padX * 2f) / BAND_COUNT;

        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            if (getParent() != null) {
                getParent().requestDisallowInterceptTouchEvent(true);
            }
            int idx = (int) ((event.getX() - padX) / colWidth);
            activeTouchBand = Math.max(0, Math.min(BAND_COUNT - 1, idx));
            updateBandFromY(activeTouchBand, event.getY(), topPad, trackHeight, bottomTrackY);
            return true;
        } else if (action == MotionEvent.ACTION_MOVE) {
            if (activeTouchBand >= 0) {
                updateBandFromY(activeTouchBand, event.getY(), topPad, trackHeight, bottomTrackY);
            }
            return true;
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            if (getParent() != null) {
                getParent().requestDisallowInterceptTouchEvent(false);
            }
            activeTouchBand = -1;
            invalidate();
            return true;
        }
        return super.onTouchEvent(event);
    }

    private void updateBandFromY(int bandIndex, float y, float topPad, float trackHeight, float bottomTrackY) {
        float clampedY = Math.max(topPad, Math.min(bottomTrackY, y));
        float norm = (bottomTrackY - clampedY) / trackHeight;
        float newGain = AudioFxEngine.MIN_GAIN_DB + norm * (AudioFxEngine.MAX_GAIN_DB - AudioFxEngine.MIN_GAIN_DB);
        gainsDb[bandIndex] = newGain;
        invalidate();
        if (listener != null) {
            listener.onBandChanged(bandIndex, newGain);
        }
    }
}
