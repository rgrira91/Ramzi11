package com.example.ui.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import androidx.annotation.Nullable;

public class RotaryKnobView extends View {
    public interface OnKnobChangedListener {
        void onValueChanged(int percent, boolean fromUser);
    }

    private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint facePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint indicatorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint valuePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private int percent = 50;
    private String knobLabel = "KNOB";
    private OnKnobChangedListener listener;
    private float density = 1f;
    private float lastTouchY = 0f;

    public RotaryKnobView(Context context) {
        super(context);
        init(context);
    }

    public RotaryKnobView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public RotaryKnobView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        density = context.getResources().getDisplayMetrics().density;

        shadowPaint.setStyle(Paint.Style.FILL);
        shadowPaint.setColor(0x30583214);

        rimPaint.setStyle(Paint.Style.STROKE);
        rimPaint.setStrokeWidth(2.2f * density);
        rimPaint.setColor(0xFFB58C59);

        facePaint.setStyle(Paint.Style.FILL);

        dotPaint.setStyle(Paint.Style.FILL);

        indicatorPaint.setStyle(Paint.Style.STROKE);
        indicatorPaint.setStrokeCap(Paint.Cap.ROUND);
        indicatorPaint.setStrokeWidth(2.8f * density);
        indicatorPaint.setColor(0xFF3E2312);

        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setTextSize(10f * density);
        labelPaint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        labelPaint.setColor(0xFF4B2E18);

        valuePaint.setTextAlign(Paint.Align.CENTER);
        valuePaint.setTextSize(9f * density);
        valuePaint.setColor(0xFF8C5327);
    }

    public void setKnobLabel(String label) {
        this.knobLabel = label != null ? label : "";
        invalidate();
    }

    public void setPercent(int percent) {
        this.percent = Math.max(0, Math.min(100, percent));
        invalidate();
    }

    public int getPercent() {
        return percent;
    }

    public void setOnKnobChangedListener(OnKnobChangedListener listener) {
        this.listener = listener;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w <= 0 || h <= 0) return;
        float cx = w * 0.5f;
        float cy = h * 0.40f;
        float r = Math.min(w, h) * 0.30f;
        facePaint.setShader(new LinearGradient(
                cx - r, cy - r, cx + r, cy + r,
                new int[]{0xFFFFF4DC, 0xFFE6C896, 0xFFB88D56},
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

        float cx = w * 0.5f;
        float cy = h * 0.40f;
        float radius = Math.min(w * 0.30f, h * 0.29f);

        // Scale dots around dial (-135 to +135 degrees)
        int dots = 9;
        float dotRing = radius + 6.5f * density;
        for (int i = 0; i < dots; i++) {
            float frac = (float) i / (dots - 1);
            float deg = -135f + frac * 270f;
            double rad = Math.toRadians(deg - 90f);
            float dx = cx + (float) Math.cos(rad) * dotRing;
            float dy = cy + (float) Math.sin(rad) * dotRing;
            boolean active = frac * 100f <= percent + 1f;
            dotPaint.setColor(active ? 0xFFC25216 : 0x668C6B4B);
            canvas.drawCircle(dx, dy, (active ? 2.1f : 1.5f) * density, dotPaint);
        }

        // Metallic knob body
        canvas.drawCircle(cx + 1.5f * density, cy + 3f * density, radius + 1f * density, shadowPaint);
        canvas.drawCircle(cx, cy, radius, facePaint);
        canvas.drawCircle(cx, cy, radius, rimPaint);

        // Indicator line
        float angleDeg = -135f + (percent / 100f) * 270f;
        double rad = Math.toRadians(angleDeg - 90f);
        float x1 = cx + (float) Math.cos(rad) * (radius * 0.25f);
        float y1 = cy + (float) Math.sin(rad) * (radius * 0.25f);
        float x2 = cx + (float) Math.cos(rad) * (radius * 0.82f);
        float y2 = cy + (float) Math.sin(rad) * (radius * 0.82f);
        canvas.drawLine(x1, y1, x2, y2, indicatorPaint);

        canvas.drawText(knobLabel, cx, h - 14f * density, labelPaint);
        canvas.drawText(percent + "%", cx, h - 3f * density, valuePaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            if (getParent() != null) {
                getParent().requestDisallowInterceptTouchEvent(true);
            }
            lastTouchY = event.getY();
            return true;
        } else if (action == MotionEvent.ACTION_MOVE) {
            float dy = lastTouchY - event.getY();
            lastTouchY = event.getY();
            int delta = Math.round(dy / (1.8f * density));
            if (delta != 0) {
                int next = Math.max(0, Math.min(100, percent + delta));
                if (next != percent) {
                    percent = next;
                    invalidate();
                    if (listener != null) {
                        listener.onValueChanged(percent, true);
                    }
                }
            }
            return true;
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            if (getParent() != null) {
                getParent().requestDisallowInterceptTouchEvent(false);
            }
            return true;
        }
        return super.onTouchEvent(event);
    }
}
