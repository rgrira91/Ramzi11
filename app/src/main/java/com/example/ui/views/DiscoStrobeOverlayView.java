package com.example.ui.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Battery-aware Party / Strobe & Full-Screen Disco Club lighting backdrop synced with music beat hits.
 * Uses pre-allocated unit LinearGradient/RadialGradient shaders transformed via Matrix (0 allocations/frame).
 */
public class DiscoStrobeOverlayView extends View {

    private static final int[] SPOT_COLORS = {
            0xFFFF22B8, // Disco Neon Pink
            0xFF00F0FF, // Electric Cyan
            0xFFFFE600, // Strobe Gold
            0xFFA855F7, // Cosmic Violet
            0xFF10B981, // Neon Emerald
            0xFFFF5E3A  // Club Tangerine
    };

    private final Paint spotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint flashPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint beamPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sparklePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path beamPath = new Path();
    private final Matrix shaderMatrix = new Matrix();
    private final LinearGradient[] unitBeamShaders = new LinearGradient[SPOT_COLORS.length];
    private final RadialGradient[] unitSpotShaders = new RadialGradient[SPOT_COLORS.length];

    private boolean active = false;
    private boolean discoClubMode = false;
    private boolean strobeFlashesAllowed = true;
    private float targetBass = 0f;
    private float smoothBass = 0f;
    private float targetTreble = 0f;
    private float smoothTreble = 0f;
    private float flashPulse = 0f;
    private float ringPulse = 0f;
    private float phase = 0f;
    private long lastFrameMs = 0L;
    private long frameIntervalMs = 26L;
    private int spotColorIdx = 0;

    private final Runnable frameRunnable = this::invalidate;

    public DiscoStrobeOverlayView(Context context) {
        super(context);
        init();
    }

    public DiscoStrobeOverlayView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public DiscoStrobeOverlayView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        spotPaint.setStyle(Paint.Style.FILL);
        flashPaint.setStyle(Paint.Style.FILL);
        beamPaint.setStyle(Paint.Style.FILL);
        ringPaint.setStyle(Paint.Style.STROKE);
        sparklePaint.setStyle(Paint.Style.FILL);
        for (int i = 0; i < SPOT_COLORS.length; i++) {
            int rgb = SPOT_COLORS[i] & 0x00FFFFFF;
            unitBeamShaders[i] = new LinearGradient(
                    0f, 0f, 0f, 1f,
                    rgb | 0xFF000000, rgb, Shader.TileMode.CLAMP);
            unitSpotShaders[i] = new RadialGradient(
                    0f, 0f, 1f,
                    rgb | 0xFF000000, rgb, Shader.TileMode.CLAMP);
        }
    }

    public void setTargetFps(int fps) {
        int clamped = Math.max(16, Math.min(48, fps));
        this.frameIntervalMs = Math.max(20L, Math.round(1000.0 / clamped));
    }

    public void setStrobeFlashesAllowed(boolean allowed) {
        this.strobeFlashesAllowed = allowed;
    }

    public void setDiscoClubMode(boolean discoClubMode) {
        this.discoClubMode = discoClubMode;
        invalidate();
    }

    public void setActive(boolean active) {
        if (this.active == active) return;
        this.active = active;
        lastFrameMs = 0L;
        if (active && isShown()) {
            removeCallbacks(frameRunnable);
            postOnAnimation(frameRunnable);
        } else {
            removeCallbacks(frameRunnable);
        }
    }

    public void onBeatSample(float energy, float bassEnergy) {
        onBeatSample(energy, bassEnergy, energy);
    }

    public void onBeatSample(float energy, float bassEnergy, float trebleEnergy) {
        float thresholdDelta = discoClubMode ? 0.11f : 0.16f;
        float thresholdFloor = discoClubMode ? 0.34f : 0.42f;
        if (bassEnergy > targetBass + thresholdDelta && bassEnergy > thresholdFloor) {
            flashPulse = Math.min(1f, bassEnergy * (discoClubMode ? 1.15f : 1.0f));
            ringPulse = 0.05f;
            spotColorIdx = (spotColorIdx + 1) % SPOT_COLORS.length;
        }
        this.targetBass = Math.max(0f, Math.min(1f, bassEnergy * 0.75f + energy * 0.25f));
        this.targetTreble = Math.max(0f, Math.min(1f, trebleEnergy));
    }

    @Override
    protected void onVisibilityChanged(@NonNull View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (visibility == VISIBLE && active && isShown()) {
            lastFrameMs = 0L;
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
        if (lastFrameMs == 0L) lastFrameMs = now;
        float dt = Math.min(0.06f, Math.max(0.004f, (now - lastFrameMs) / 1000f));
        lastFrameMs = now;

        float speed = discoClubMode ? (2.3f + smoothBass * 3.8f) : (1.8f + smoothBass * 3.0f);
        phase = (phase + dt * speed) % 62.83f;
        smoothBass += (targetBass - smoothBass) * (1f - (float) Math.exp(-dt * 14.0));
        smoothTreble += (targetTreble - smoothTreble) * (1f - (float) Math.exp(-dt * 12.0));
        flashPulse = Math.max(0f, flashPulse - dt * (discoClubMode ? 3.1f : 3.8f));
        if (ringPulse > 0f) {
            ringPulse += dt * 1.9f;
            if (ringPulse > 1.05f) ringPulse = 0f;
        }

        // 1. Ambient flashing beat wash across entire screen
        if (strobeFlashesAllowed && flashPulse > 0.04f) {
            int flashCol = SPOT_COLORS[spotColorIdx];
            int maxAlpha = discoClubMode ? 88 : 68;
            int alpha = Math.min(maxAlpha, Math.round(flashPulse * maxAlpha));
            flashPaint.setColor((flashCol & 0x00FFFFFF) | (alpha << 24));
            canvas.drawRect(0f, 0f, w, h, flashPaint);
        }

        // 2. Disco Laser Cones sweeping down from corners in Disco Mode (0 allocations)
        if (discoClubMode) {
            float beamScaleH = Math.max(10f, h * 0.94f);
            int bAlpha = Math.min(95, Math.round((0.14f + 0.30f * smoothBass) * 255f));
            beamPaint.setAlpha(bAlpha);
            for (int b = 0; b < 4; b++) {
                float originX = (b % 2 == 0) ? w * 0.08f : w * 0.92f;
                float originY = -20f;
                float sweepAngle = (float) Math.sin(phase * (0.65f + b * 0.14f) + b * 1.3f);
                float targetX = w * 0.5f + sweepAngle * w * 0.52f;
                float spread = w * (0.14f + 0.12f * smoothBass);
                int cIdx = (spotColorIdx + b) % SPOT_COLORS.length;

                LinearGradient beamShader = unitBeamShaders[cIdx];
                shaderMatrix.reset();
                shaderMatrix.setScale(1f, beamScaleH);
                shaderMatrix.postTranslate(0f, originY);
                beamShader.setLocalMatrix(shaderMatrix);
                beamPaint.setShader(beamShader);
                beamPaint.setAlpha(bAlpha);

                beamPath.reset();
                beamPath.moveTo(originX, originY);
                beamPath.lineTo(targetX - spread, h);
                beamPath.lineTo(targetX + spread, h);
                beamPath.close();
                canvas.drawPath(beamPath, beamPaint);
            }
        }

        // 3. Sweeping radial disco spotlights (0 allocations)
        float baseR = Math.max(w, h) * (discoClubMode ? 0.48f : 0.42f);
        float spotRadius = Math.max(10f, baseR * (0.85f + smoothBass * 0.38f));
        int spotAlpha = Math.min(130, Math.round((0.18f + 0.36f * smoothBass) * 255f));
        int spotCount = discoClubMode ? 4 : 3;
        for (int i = 0; i < spotCount; i++) {
            float cx = w * (0.5f + 0.40f * (float) Math.sin(phase * 0.72f + i * 1.75f));
            float cy = h * (0.24f + 0.34f * (float) Math.cos(phase * 0.58f + i * 1.45f));
            int cIdx = (spotColorIdx + i) % SPOT_COLORS.length;

            RadialGradient spotShader = unitSpotShaders[cIdx];
            shaderMatrix.reset();
            shaderMatrix.setScale(spotRadius, spotRadius);
            shaderMatrix.postTranslate(cx, cy);
            spotShader.setLocalMatrix(shaderMatrix);
            spotPaint.setShader(spotShader);
            spotPaint.setAlpha(spotAlpha);

            canvas.drawCircle(cx, cy, baseR * 1.15f, spotPaint);
        }

        // 4. Expanding rhythmic shockwave ring + mirror-ball sparkles on high-frequency hits
        if (discoClubMode) {
            if (ringPulse > 0.02f) {
                float maxRingR = Math.max(w, h) * 0.55f;
                float r = ringPulse * maxRingR;
                int rCol = SPOT_COLORS[(spotColorIdx + 2) % SPOT_COLORS.length];
                int rAlpha = Math.max(0, Math.min(165, Math.round((1f - ringPulse) * 165f)));
                ringPaint.setColor((rCol & 0x00FFFFFF) | (rAlpha << 24));
                ringPaint.setStrokeWidth(4f + 8f * (1f - ringPulse));
                canvas.drawCircle(w * 0.5f, h * 0.36f, r, ringPaint);
            }
            if (strobeFlashesAllowed) {
                for (int s = 0; s < 10; s++) {
                    float twinkle = 0.5f + 0.5f * (float) Math.sin(phase * 2.4f + s * 1.9f);
                    if (twinkle < 0.35f) continue;
                    float sx = w * (0.08f + 0.84f * ((s * 37 % 100) / 100f));
                    float sy = h * (0.06f + 0.82f * ((s * 53 % 100) / 100f));
                    int sCol = SPOT_COLORS[(spotColorIdx + s) % SPOT_COLORS.length];
                    int sAlpha = Math.min(190, Math.round(twinkle * (0.35f + 0.65f * smoothTreble) * 190f));
                    sparklePaint.setColor((sCol & 0x00FFFFFF) | (sAlpha << 24));
                    float radius = 2.5f + 4.5f * twinkle * (0.4f + smoothTreble);
                    canvas.drawCircle(sx, sy, radius, sparklePaint);
                }
            }
        }

        if (active && isShown()) {
            removeCallbacks(frameRunnable);
            long delay = Math.max(12L, frameIntervalMs - (SystemClock.uptimeMillis() - now));
            postDelayed(frameRunnable, delay);
        }
    }
}
