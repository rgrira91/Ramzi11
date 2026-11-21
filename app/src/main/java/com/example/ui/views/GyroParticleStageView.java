package com.example.ui.views;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Camera;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;
import androidx.annotation.Nullable;

/**
 * Fullscreen zero-allocation 3D Crystal Album Cover & Beat-Reactive Particle Stage.
 * Tilts album art in 3D perspective using gyroscope / rotation telemetry and pauses
 * its animation loop completely when playback stops or view is hidden/detached.
 */
public class GyroParticleStageView extends View {

    private static final int MAX_PARTICLES = 28;

    private final Paint bgGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint particlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint artPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint artFallbackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glassFramePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sheenPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Camera camera3d = new Camera();
    private final Matrix matrix3d = new Matrix();
    private final Matrix shaderMatrix = new Matrix();
    private final Matrix artMatrix = new Matrix();
    private final RectF cardRect = new RectF();
    private final RectF shadowRect = new RectF();
    private final Path clipPath = new Path();

    private RadialGradient unitAuroraShader;
    private LinearGradient unitSheenShader;
    private LinearGradient unitCardFallbackShader;
    @Nullable
    private BitmapShader artShader;
    @Nullable
    private Bitmap albumBitmap;

    // Pre-allocated particle pool (0 per-frame GC)
    private final float[] px = new float[MAX_PARTICLES];
    private final float[] py = new float[MAX_PARTICLES];
    private final float[] vx = new float[MAX_PARTICLES];
    private final float[] vy = new float[MAX_PARTICLES];
    private final float[] pSize = new float[MAX_PARTICLES];
    private final float[] pPhase = new float[MAX_PARTICLES];
    private final int[] pColorSlot = new int[MAX_PARTICLES];
    private int maxActiveParticles = 16;

    private int primaryNeon = 0xFFEC4899;
    private int secondaryNeon = 0xFF22D3EE;
    private int tertiaryGlow = 0xFFA855F7;

    private float targetPitch = 0f;
    private float targetRoll = 0f;
    private float smoothPitch = 0f;
    private float smoothRoll = 0f;

    private float audioEnergy = 0f;
    private float bassEnergy = 0f;
    private float liveBpm = 120f;
    private float beatPulse = 0f;
    private float ambientPhase = 0f;
    private boolean playing = false;

    private float density = 1f;
    private long frameIntervalMs = 33L;
    private long lastStepMs = 0L;
    private final Handler frameHandler = new Handler(Looper.getMainLooper());
    private boolean loopScheduled = false;

    private final Runnable tickRunnable = new Runnable() {
        @Override
        public void run() {
            loopScheduled = false;
            if (!shouldAnimate()) {
                return;
            }
            stepSimulation();
            invalidate();
            scheduleNextFrame();
        }
    };

    public GyroParticleStageView(Context context) {
        super(context);
        init(context);
    }

    public GyroParticleStageView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public GyroParticleStageView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        density = context.getResources().getDisplayMetrics().density;

        bgGlowPaint.setStyle(Paint.Style.FILL);
        ringPaint.setStyle(Paint.Style.STROKE);
        particlePaint.setStyle(Paint.Style.FILL);
        glassFramePaint.setStyle(Paint.Style.STROKE);
        glassFramePaint.setStrokeWidth(2.0f * density);
        sheenPaint.setStyle(Paint.Style.FILL);
        shadowPaint.setStyle(Paint.Style.FILL);
        shadowPaint.setColor(0x99000000);

        unitAuroraShader = new RadialGradient(
                0f, 0f, 1f,
                new int[]{0x88EC4899, 0x4422D3EE, 0x00050714},
                new float[]{0f, 0.45f, 1f},
                Shader.TileMode.CLAMP
        );
        unitSheenShader = new LinearGradient(
                -0.6f, -0.6f, 0.6f, 0.6f,
                new int[]{0x55FFFFFF, 0x14BAE6FD, 0x00FFFFFF},
                new float[]{0f, 0.36f, 0.78f},
                Shader.TileMode.CLAMP
        );
        unitCardFallbackShader = new LinearGradient(
                -0.5f, -0.5f, 0.5f, 0.5f,
                new int[]{0xFF28184B, 0xFF14193D, 0xFF0A0D20},
                new float[]{0f, 0.52f, 1f},
                Shader.TileMode.CLAMP
        );

        seedParticles();
    }

    private void seedParticles() {
        for (int i = 0; i < MAX_PARTICLES; i++) {
            px[i] = ((i * 37) % 100) / 100f;
            py[i] = ((i * 61) % 100) / 100f;
            float angle = (float) (i * 0.73);
            vx[i] = (float) Math.cos(angle) * 0.0018f;
            vy[i] = -0.0014f - ((i % 5) * 0.00055f);
            pSize[i] = (1.8f + (i % 4) * 1.15f) * density;
            pPhase[i] = (i * 0.65f);
            pColorSlot[i] = i % 3;
        }
    }

    public void setMaxParticleCount(int count) {
        this.maxActiveParticles = Math.max(0, Math.min(MAX_PARTICLES, count));
        invalidate();
    }

    public void setTargetFps(int fps) {
        int clamped = Math.max(15, Math.min(60, fps));
        this.frameIntervalMs = Math.max(16L, Math.round(1000.0 / clamped));
    }

    public void setPaletteColors(int primary, int secondary, int tertiary) {
        this.primaryNeon = primary;
        this.secondaryNeon = secondary;
        this.tertiaryGlow = tertiary;
        invalidate();
    }

    public void setAlbumBitmap(@Nullable Bitmap bitmap) {
        if (bitmap == this.albumBitmap) return;
        this.albumBitmap = bitmap;
        if (bitmap != null && !bitmap.isRecycled()) {
            artShader = new BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            artPaint.setShader(artShader);
        } else {
            artShader = null;
            artPaint.setShader(null);
        }
        invalidate();
    }

    public void setGyroTilt(float pitchNorm, float rollNorm) {
        this.targetPitch = Math.max(-1f, Math.min(1f, pitchNorm));
        this.targetRoll = Math.max(-1f, Math.min(1f, rollNorm));
        if (!loopScheduled && shouldAnimate()) {
            scheduleNextFrame();
        } else if (!playing) {
            smoothPitch += (targetPitch - smoothPitch) * 0.35f;
            smoothRoll += (targetRoll - smoothRoll) * 0.35f;
            invalidate();
        }
    }

    public void updateAudioTelemetry(float energy, float bass, float bpm) {
        this.audioEnergy = Math.max(0f, Math.min(1f, energy));
        this.bassEnergy = Math.max(0f, Math.min(1f, bass));
        if (bpm >= 50f && bpm <= 220f) {
            this.liveBpm = bpm;
        }
        if (bass > beatPulse) {
            beatPulse = bass;
        }
    }

    public void setPlaying(boolean playing) {
        if (this.playing == playing) return;
        this.playing = playing;
        if (playing) {
            lastStepMs = SystemClock.uptimeMillis();
            scheduleNextFrame();
        } else {
            cancelFrameLoop();
            invalidate();
        }
    }

    private boolean shouldAnimate() {
        return isAttachedToWindow() && getVisibility() == VISIBLE && playing;
    }

    private void scheduleNextFrame() {
        if (loopScheduled || !shouldAnimate()) return;
        loopScheduled = true;
        frameHandler.postDelayed(tickRunnable, frameIntervalMs);
    }

    private void cancelFrameLoop() {
        loopScheduled = false;
        frameHandler.removeCallbacks(tickRunnable);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (shouldAnimate()) {
            lastStepMs = SystemClock.uptimeMillis();
            scheduleNextFrame();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        cancelFrameLoop();
        super.onDetachedFromWindow();
    }

    @Override
    public void setVisibility(int visibility) {
        super.setVisibility(visibility);
        if (visibility != VISIBLE) {
            cancelFrameLoop();
        } else if (shouldAnimate()) {
            lastStepMs = SystemClock.uptimeMillis();
            scheduleNextFrame();
        }
    }

    private void stepSimulation() {
        long now = SystemClock.uptimeMillis();
        float dtScale = lastStepMs == 0L ? 1f : Math.min(2.5f, Math.max(0.4f, (now - lastStepMs) / 33f));
        lastStepMs = now;

        smoothPitch += (targetPitch - smoothPitch) * 0.18f * dtScale;
        smoothRoll += (targetRoll - smoothRoll) * 0.18f * dtScale;
        beatPulse = Math.max(bassEnergy * 0.7f, beatPulse - 0.045f * dtScale);
        ambientPhase += (0.025f + (liveBpm / 120f) * 0.012f) * dtScale;
        if (ambientPhase > 628.3185f) {
            ambientPhase -= 628.3185f;
        }

        float speedBoost = 1f + audioEnergy * 2.2f + beatPulse * 1.4f;
        for (int i = 0; i < maxActiveParticles; i++) {
            px[i] += vx[i] * speedBoost * dtScale + smoothRoll * 0.0012f;
            py[i] += vy[i] * speedBoost * dtScale + smoothPitch * 0.0008f;
            pPhase[i] += 0.06f * dtScale;
            if (py[i] < -0.05f) {
                py[i] = 1.05f;
                px[i] = (px[i] + 0.37f) % 1.0f;
            } else if (py[i] > 1.05f) {
                py[i] = -0.05f;
            }
            if (px[i] < -0.05f) px[i] = 1.05f;
            else if (px[i] > 1.05f) px[i] = -0.05f;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        float cx = w * 0.5f;
        float cy = h * 0.41f;
        float coverHalf = Math.min(w * 0.34f, h * 0.22f);
        float radiusBase = Math.max(w, h) * (0.48f + beatPulse * 0.14f);

        // 1. Beat-reactive Cosmic Aurora Halo
        shaderMatrix.reset();
        shaderMatrix.setScale(radiusBase, radiusBase);
        shaderMatrix.postTranslate(cx + smoothRoll * 22f * density, cy + smoothPitch * 18f * density);
        unitAuroraShader.setLocalMatrix(shaderMatrix);
        bgGlowPaint.setShader(unitAuroraShader);
        bgGlowPaint.setAlpha((int) (135 + 110 * Math.max(audioEnergy, beatPulse)));
        canvas.drawCircle(cx, cy, radiusBase, bgGlowPaint);

        // 2. Orbital Pulse Rings
        float orbitR1 = coverHalf * (1.28f + beatPulse * 0.14f);
        ringPaint.setStrokeWidth(1.4f * density);
        ringPaint.setColor(secondaryNeon);
        ringPaint.setAlpha((int) (60 + beatPulse * 110));
        canvas.drawCircle(cx, cy, orbitR1, ringPaint);

        float orbitR2 = coverHalf * (1.52f + audioEnergy * 0.18f);
        ringPaint.setStrokeWidth(1.0f * density);
        ringPaint.setColor(primaryNeon);
        ringPaint.setAlpha((int) (40 + audioEnergy * 95));
        canvas.drawCircle(cx, cy, orbitR2, ringPaint);

        // 3. Zero-Alloc Floating Neon Particles
        for (int i = 0; i < maxActiveParticles; i++) {
            float x = px[i] * w;
            float y = py[i] * h;
            float twinkle = 0.55f + 0.45f * (float) Math.sin(pPhase[i]);
            int col = pColorSlot[i] == 0 ? secondaryNeon : (pColorSlot[i] == 1 ? primaryNeon : tertiaryGlow);
            particlePaint.setColor(col);
            particlePaint.setAlpha((int) ((85 + 150 * beatPulse) * twinkle));
            canvas.drawCircle(x, y, pSize[i] * (0.85f + beatPulse * 0.45f), particlePaint);
        }

        // 4. 3D Perspective-Tilted Crystal Album Cover
        float scale = 1f + beatPulse * 0.045f;
        float halfSize = coverHalf * scale;
        float corner = 24f * density;

        float autoDriftPitch = playing ? (float) Math.sin(ambientPhase) * 3.5f : 0f;
        float autoDriftRoll = playing ? (float) Math.cos(ambientPhase * 0.7f) * 4.5f : 0f;
        float rotX = -smoothPitch * 14f + autoDriftPitch;
        float rotY = smoothRoll * 14f + autoDriftRoll;

        // Projected shadow under floating glass card
        shadowRect.set(
                cx - halfSize * 0.94f - smoothRoll * 14f * density,
                cy - halfSize * 0.90f + 16f * density + smoothPitch * 10f * density,
                cx + halfSize * 0.94f - smoothRoll * 14f * density,
                cy + halfSize * 0.96f + 16f * density + smoothPitch * 10f * density
        );
        canvas.drawRoundRect(shadowRect, corner, corner, shadowPaint);

        canvas.save();
        camera3d.save();
        camera3d.setLocation(0f, 0f, -18f);
        camera3d.rotateX(rotX);
        camera3d.rotateY(rotY);
        camera3d.getMatrix(matrix3d);
        camera3d.restore();

        matrix3d.preTranslate(-cx, -cy);
        matrix3d.postTranslate(cx, cy);
        canvas.concat(matrix3d);

        cardRect.set(cx - halfSize, cy - halfSize, cx + halfSize, cy + halfSize);
        clipPath.reset();
        clipPath.addRoundRect(cardRect, corner, corner, Path.Direction.CW);

        canvas.save();
        canvas.clipPath(clipPath);
        if (albumBitmap != null && !albumBitmap.isRecycled() && artShader != null) {
            float bw = Math.max(1f, albumBitmap.getWidth());
            float bh = Math.max(1f, albumBitmap.getHeight());
            float side = halfSize * 2f;
            float imgScale = Math.max(side / bw, side / bh);
            artMatrix.reset();
            artMatrix.setScale(imgScale, imgScale);
            artMatrix.postTranslate(
                    cx - (bw * imgScale) * 0.5f,
                    cy - (bh * imgScale) * 0.5f
            );
            artShader.setLocalMatrix(artMatrix);
            canvas.drawRect(cardRect, artPaint);
        } else {
            shaderMatrix.reset();
            shaderMatrix.setScale(halfSize * 2f, halfSize * 2f);
            shaderMatrix.postTranslate(cx, cy);
            unitCardFallbackShader.setLocalMatrix(shaderMatrix);
            artFallbackPaint.setShader(unitCardFallbackShader);
            canvas.drawRect(cardRect, artFallbackPaint);

            ringPaint.setColor(secondaryNeon);
            ringPaint.setAlpha(140);
            ringPaint.setStrokeWidth(2.4f * density);
            canvas.drawCircle(cx, cy, halfSize * 0.44f, ringPaint);
            ringPaint.setColor(primaryNeon);
            canvas.drawCircle(cx, cy, halfSize * 0.16f, ringPaint);
        }

        // Specular Holographic Glass Sheen moving opposite to tilt
        shaderMatrix.reset();
        shaderMatrix.setScale(halfSize * 2.2f, halfSize * 2.2f);
        shaderMatrix.postTranslate(
                cx + smoothRoll * halfSize * 0.45f,
                cy + smoothPitch * halfSize * 0.45f
        );
        unitSheenShader.setLocalMatrix(shaderMatrix);
        sheenPaint.setShader(unitSheenShader);
        sheenPaint.setAlpha((int) (120 + beatPulse * 65));
        canvas.drawRect(cardRect, sheenPaint);
        canvas.restore();

        // Outer neon crystal rim
        glassFramePaint.setColor(Color.argb(
                Math.min(255, (int) (160 + beatPulse * 85)),
                Color.red(secondaryNeon),
                Color.green(secondaryNeon),
                Color.blue(secondaryNeon)
        ));
        canvas.drawRoundRect(cardRect, corner, corner, glassFramePaint);
        canvas.restore();
    }
}
