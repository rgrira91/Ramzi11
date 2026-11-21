package com.example.ui.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class NeonWaveformView extends View {
    public enum VisualMode {
        SPECTRUM_WAVE("Spectrum + Waves"),
        MIRROR_PULSE("Mirror Pulse"),
        NEON_RIBBONS("Neon Ribbons"),
        HEARTBEAT("Cyberpunk Heartbeat"),
        OCEAN_WAVE("Chill Ocean Wave"),
        FIRE_FLAME("Fire Flame");

        public final String displayName;
        VisualMode(String displayName) {
            this.displayName = displayName;
        }
    }

    private static final int BAR_COUNT = 42;

    private final float[] targetBars = new float[BAR_COUNT];
    private final float[] displayBars = new float[BAR_COUNT];

    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint barGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint reflectionPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Paint waveGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint waveCorePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint waveSecondaryPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint waveFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Path wavePath1 = new Path();
    private final Path wavePath2 = new Path();
    private final Path waveFillPath = new Path();
    private final RectF barRect = new RectF();

    private VisualMode visualMode = VisualMode.SPECTRUM_WAVE;
    private boolean isPlaying = false;
    private boolean hasRealSpectrum = false;
    private long lastSpectrumTimestamp = 0L;
    private long lastAnimFrameMs = 0L;
    private float phase = 0f;
    private float liveEnergy = 0.35f;
    private float liveBass = 0f;
    private int widthPx = 0;
    private int heightPx = 0;

    private int colorPrimary = 0xFFF43F9E;
    private int colorSecondary = 0xFF22D3EE;
    private int colorTertiary = 0xFFA855F7;
    private long frameIntervalMs = 16L;
    private final Runnable frameRunnable = this::invalidate;

    private static final int PARTICLE_COUNT = 14;
    private int maxParticleCount = PARTICLE_COUNT;
    private final float[] partX = new float[PARTICLE_COUNT];
    private final float[] partY = new float[PARTICLE_COUNT];
    private final float[] partSpeed = new float[PARTICLE_COUNT];
    private final float[] partRadius = new float[PARTICLE_COUNT];
    private final float[] partPhase = new float[PARTICLE_COUNT];
    private final Paint particlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public NeonWaveformView(Context context) {
        super(context);
        init();
    }

    public NeonWaveformView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public NeonWaveformView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        barPaint.setStyle(Paint.Style.FILL);
        barGlowPaint.setStyle(Paint.Style.FILL);
        reflectionPaint.setStyle(Paint.Style.FILL);

        waveGlowPaint.setStyle(Paint.Style.STROKE);
        waveGlowPaint.setStrokeWidth(10f);
        waveGlowPaint.setStrokeCap(Paint.Cap.ROUND);

        waveCorePaint.setStyle(Paint.Style.STROKE);
        waveCorePaint.setStrokeWidth(2.8f);
        waveCorePaint.setStrokeCap(Paint.Cap.ROUND);

        waveSecondaryPaint.setStyle(Paint.Style.STROKE);
        waveSecondaryPaint.setStrokeWidth(2.2f);
        waveSecondaryPaint.setStrokeCap(Paint.Cap.ROUND);

        waveFillPaint.setStyle(Paint.Style.FILL);

        for (int i = 0; i < BAR_COUNT; i++) {
            float env = (float) Math.sin((double) i / (BAR_COUNT - 1) * Math.PI);
            targetBars[i] = 0.08f + 0.14f * env;
            displayBars[i] = targetBars[i];
        }

        particlePaint.setStyle(Paint.Style.FILL);
        for (int i = 0; i < PARTICLE_COUNT; i++) {
            partX[i] = ((i * 73 + 17) % 100) / 100f;
            partY[i] = ((i * 37 + 11) % 100) / 100f;
            partSpeed[i] = 0.12f + ((i * 19) % 10) * 0.018f;
            partRadius[i] = 2.0f + (i % 4) * 1.1f;
            partPhase[i] = i * 0.65f;
        }
    }

    public void setPaletteColors(int primaryNeon, int secondaryNeon, int tertiaryGlow) {
        if (this.colorPrimary == primaryNeon
                && this.colorSecondary == secondaryNeon
                && this.colorTertiary == tertiaryGlow) {
            return;
        }
        this.colorPrimary = primaryNeon;
        this.colorSecondary = secondaryNeon;
        this.colorTertiary = tertiaryGlow;
        if (widthPx > 0 && heightPx > 0) {
            rebuildShaders(widthPx, heightPx);
            if (isShown()) {
                invalidate();
            }
        }
    }

    public VisualMode cycleVisualMode() {
        VisualMode[] modes = VisualMode.values();
        visualMode = modes[(visualMode.ordinal() + 1) % modes.length];
        invalidate();
        return visualMode;
    }

    public void setVisualMode(VisualMode mode) {
        if (mode != null && this.visualMode != mode) {
            this.visualMode = mode;
            invalidate();
        }
    }

    public VisualMode getVisualMode() {
        return visualMode;
    }

    public void setTargetFps(int fps) {
        int clamped = Math.max(18, Math.min(60, fps));
        this.frameIntervalMs = Math.max(16L, Math.round(1000.0 / clamped));
    }

    public void setMaxParticleCount(int count) {
        this.maxParticleCount = Math.max(0, Math.min(PARTICLE_COUNT, count));
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

    public void updateSpectrum(float[] bars, float energy) {
        float estBass = 0f;
        if (bars != null && bars.length >= 6) {
            for (int i = 0; i < 6; i++) estBass += bars[i];
            estBass /= 6f;
        } else {
            estBass = energy;
        }
        updateStereoWaveform(bars, energy, estBass);
    }

    public void updateStereoWaveform(float[] bars, float energy, float bassEnergy) {
        if (bars == null) return;
        float clampedBass = Math.max(0f, Math.min(1.4f, bassEnergy));
        this.liveBass = Math.max(clampedBass, this.liveBass * 0.72f + clampedBass * 0.28f);
        float bassKickScale = 1.0f + Math.min(0.45f, clampedBass * 0.38f);
        int len = Math.min(BAR_COUNT, bars.length);
        for (int i = 0; i < len; i++) {
            // Boost bars cleanly on bass hits while keeping shape
            float weight = i < 12 ? bassKickScale : (1.0f + Math.min(0.22f, clampedBass * 0.18f));
            targetBars[i] = Math.max(0.06f, Math.min(1f, bars[i] * weight));
        }
        this.liveEnergy = Math.max(0.15f, Math.min(1.25f, energy * 1.65f + clampedBass * 0.45f));
        this.hasRealSpectrum = true;
        this.lastSpectrumTimestamp = SystemClock.uptimeMillis();
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
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (isPlaying && isShown()) {
            lastAnimFrameMs = 0L;
            removeCallbacks(frameRunnable);
            postOnAnimation(frameRunnable);
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
        widthPx = w;
        heightPx = h;
        if (w <= 0 || h <= 0) return;
        rebuildShaders(w, h);
    }

    private void rebuildShaders(int w, int h) {
        int pBright = com.example.ui.theme.NeonPalette.lerpColor(colorPrimary, 0xFFFFFFFF, 0.35f);
        int sBright = com.example.ui.theme.NeonPalette.lerpColor(colorSecondary, 0xFFFFFFFF, 0.35f);
        int pGlow = com.example.ui.theme.NeonPalette.withAlpha(colorPrimary, 0x42);
        int sGlow = com.example.ui.theme.NeonPalette.withAlpha(colorSecondary, 0x38);
        int tGlow = com.example.ui.theme.NeonPalette.withAlpha(colorTertiary, 0x3D);

        Shader spectrumShader = new LinearGradient(
                0f, 0f, w, 0f,
                new int[]{colorTertiary, colorPrimary, pBright, colorTertiary, colorSecondary},
                new float[]{0f, 0.30f, 0.55f, 0.78f, 1f},
                Shader.TileMode.CLAMP
        );
        barPaint.setShader(spectrumShader);

        barGlowPaint.setShader(new LinearGradient(
                0f, 0f, w, 0f,
                new int[]{tGlow, pGlow, pGlow, sGlow},
                new float[]{0f, 0.35f, 0.70f, 1f},
                Shader.TileMode.CLAMP
        ));

        reflectionPaint.setShader(new LinearGradient(
                0f, h * 0.65f, 0f, h,
                new int[]{com.example.ui.theme.NeonPalette.withAlpha(colorPrimary, 0x40),
                        com.example.ui.theme.NeonPalette.withAlpha(colorSecondary, 0x00)},
                null,
                Shader.TileMode.CLAMP
        ));

        waveGlowPaint.setShader(new LinearGradient(
                0f, 0f, w, 0f,
                new int[]{
                        com.example.ui.theme.NeonPalette.withAlpha(colorPrimary, 0x48),
                        com.example.ui.theme.NeonPalette.withAlpha(pBright, 0x55),
                        com.example.ui.theme.NeonPalette.withAlpha(sBright, 0x4C),
                        com.example.ui.theme.NeonPalette.withAlpha(colorSecondary, 0x44)
                },
                new float[]{0f, 0.4f, 0.75f, 1f},
                Shader.TileMode.CLAMP
        ));
        waveCorePaint.setShader(new LinearGradient(
                0f, 0f, w, 0f,
                new int[]{pBright, 0xFFFFFFFF, sBright},
                new float[]{0f, 0.5f, 1f},
                Shader.TileMode.CLAMP
        ));
        waveSecondaryPaint.setShader(new LinearGradient(
                0f, 0f, w, 0f,
                new int[]{com.example.ui.theme.NeonPalette.withAlpha(colorTertiary, 0xAA),
                        com.example.ui.theme.NeonPalette.withAlpha(colorPrimary, 0xCC),
                        com.example.ui.theme.NeonPalette.withAlpha(colorSecondary, 0xAA)},
                null,
                Shader.TileMode.CLAMP
        ));
        waveFillPaint.setShader(new LinearGradient(
                0f, h * 0.35f, 0f, h * 0.88f,
                new int[]{com.example.ui.theme.NeonPalette.withAlpha(colorPrimary, 0x4D),
                        com.example.ui.theme.NeonPalette.withAlpha(colorTertiary, 0x22),
                        0x00120829},
                null,
                Shader.TileMode.CLAMP
        ));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (widthPx <= 0 || heightPx <= 0) return;

        long now = SystemClock.uptimeMillis();
        if (lastAnimFrameMs == 0L) lastAnimFrameMs = now;
        float dt = Math.min(0.05f, Math.max(0.001f, (now - lastAnimFrameMs) / 1000f));
        lastAnimFrameMs = now;

        boolean useProcedural = !hasRealSpectrum || (now - lastSpectrumTimestamp > 900L);

        if (isPlaying) {
            phase += dt * 4.8f;
            if (useProcedural) {
                liveEnergy = 0.52f + 0.24f * (float) Math.sin(phase * 0.7f);
                for (int i = 0; i < BAR_COUNT; i++) {
                    double xNorm = (double) i / (BAR_COUNT - 1);
                    double env = Math.sin(xNorm * Math.PI);
                    double p1 = Math.abs(Math.sin(phase * 1.4 + i * 0.36));
                    double p2 = Math.abs(Math.cos(phase * 0.9 - i * 0.21));
                    targetBars[i] = (float) Math.max(0.08, Math.min(0.95, env * (0.28 + 0.65 * p1 * p2)));
                }
            }
        } else {
            liveEnergy += (0.18f - liveEnergy) * (1f - (float) Math.exp(-dt * 5.0));
            for (int i = 0; i < BAR_COUNT; i++) {
                float env = (float) Math.sin((double) i / (BAR_COUNT - 1) * Math.PI);
                targetBars[i] = 0.07f + 0.12f * env;
            }
        }

        float upRate = 1f - (float) Math.exp(-dt * 26.0);
        float downRate = 1f - (float) Math.exp(-dt * 12.5);
        boolean needsMoreFrames = isPlaying;
        for (int i = 0; i < BAR_COUNT; i++) {
            float diff = targetBars[i] - displayBars[i];
            displayBars[i] += diff * (diff > 0f ? upRate : downRate);
            if (Math.abs(diff) > 0.005f) {
                needsMoreFrames = true;
            }
        }

        // Draw animated floating beat-reactive neon bokeh sparks behind waveform
        if ((isPlaying || liveEnergy > 0.22f) && maxParticleCount > 0) {
            float boost = 1.0f + liveEnergy * 1.8f + liveBass * 0.6f;
            for (int i = 0; i < maxParticleCount; i++) {
                if (isPlaying) {
                    partY[i] -= dt * partSpeed[i] * boost;
                    if (partY[i] < -0.05f) {
                        partY[i] = 1.05f;
                        partX[i] = ((i * 53 + (int) (phase * 17f)) % 100) / 100f;
                    }
                }
                float px = partX[i] * widthPx + (float) Math.sin(phase * 0.9f + partPhase[i]) * 8f;
                float py = partY[i] * heightPx;
                int baseColor = (i % 3 == 0) ? colorSecondary : ((i % 3 == 1) ? colorPrimary : colorTertiary);
                int alpha = Math.round((0.18f + 0.42f * liveEnergy) * 255f * (0.5f + 0.5f * (float) Math.sin(phase * 1.5f + partPhase[i])));
                particlePaint.setColor(com.example.ui.theme.NeonPalette.withAlpha(baseColor, Math.max(18, Math.min(180, alpha))));
                canvas.drawCircle(px, py, partRadius[i] * (0.85f + liveEnergy * 0.75f + liveBass * 0.25f), particlePaint);
            }
        }

        float padX = widthPx * 0.03f;
        float usableW = widthPx - padX * 2f;
        float slotW = usableW / BAR_COUNT;
        float barW = Math.max(3f, slotW * 0.46f);
        float haloPad = Math.max(2f, barW * 0.45f);
        float radius = barW * 0.5f;
        float bassAmpScale = 1.0f + Math.min(0.65f, liveBass * 0.55f);

        if (visualMode == VisualMode.MIRROR_PULSE) {
            float centerY = heightPx * 0.5f;
            float maxHalf = heightPx * 0.42f * Math.min(1.15f, bassAmpScale);
            for (int i = 0; i < BAR_COUNT; i++) {
                float cx = padX + i * slotW + slotW * 0.5f;
                float halfH = Math.max(barW, Math.min(heightPx * 0.46f, displayBars[i] * maxHalf));
                barRect.set(cx - barW * 0.5f - haloPad, centerY - halfH - haloPad * 0.5f,
                        cx + barW * 0.5f + haloPad, centerY + halfH + haloPad * 0.5f);
                canvas.drawRoundRect(barRect, radius + haloPad, radius + haloPad, barGlowPaint);

                barRect.set(cx - barW * 0.5f, centerY - halfH, cx + barW * 0.5f, centerY + halfH);
                canvas.drawRoundRect(barRect, radius, radius, barPaint);
            }
            canvas.drawLine(padX, centerY, padX + usableW, centerY, waveCorePaint);
        } else if (visualMode == VisualMode.HEARTBEAT) {
            // Cyberpunk sharp ECG / oscilloscope pulse wave
            float centerY = heightPx * 0.54f;
            float baselineY = heightPx * 0.86f;
            wavePath1.reset();
            wavePath2.reset();
            waveFillPath.reset();

            int steps = 72;
            float ecgMaxAmp = heightPx * (0.18f + 0.20f * liveEnergy) * bassAmpScale;
            for (int s = 0; s <= steps; s++) {
                float t = (float) s / steps;
                float x = padX + t * usableW;
                int barIndex = Math.min(BAR_COUNT - 1, Math.round(t * (BAR_COUNT - 1)));
                float barLvl = displayBars[barIndex];

                // Repeating sharp QRS-like spike cycle driven by beat phase
                float beatCycle = ((t * 3.2f - phase * 0.28f) % 1.0f + 1.0f) % 1.0f;
                float spike;
                if (beatCycle > 0.36f && beatCycle < 0.42f) {
                    spike = -0.35f;
                } else if (beatCycle >= 0.42f && beatCycle < 0.48f) {
                    spike = 1.25f;
                } else if (beatCycle >= 0.48f && beatCycle < 0.54f) {
                    spike = -0.68f;
                } else if (beatCycle >= 0.58f && beatCycle < 0.68f) {
                    spike = 0.28f * (float) Math.sin((beatCycle - 0.58f) / 0.10f * Math.PI);
                } else {
                    spike = 0.08f * (float) Math.sin(t * 28f + phase * 2f);
                }
                float sharpBar = ((barIndex % 2 == 0) ? 1f : -0.75f) * barLvl * 0.42f;
                float y1 = Math.max(heightPx * 0.08f, Math.min(heightPx * 0.90f,
                        centerY - (spike * (0.45f + 0.75f * barLvl) + sharpBar) * ecgMaxAmp));
                float y2 = centerY + (float) Math.sin(t * 18f - phase * 1.6f) * heightPx * 0.08f * (0.4f + barLvl);

                if (s == 0) {
                    wavePath1.moveTo(x, y1);
                    wavePath2.moveTo(x, y2);
                    waveFillPath.moveTo(x, baselineY);
                    waveFillPath.lineTo(x, y1);
                } else {
                    wavePath1.lineTo(x, y1);
                    wavePath2.lineTo(x, y2);
                    waveFillPath.lineTo(x, y1);
                }
            }
            waveFillPath.lineTo(padX + usableW, baselineY);
            waveFillPath.close();

            canvas.drawLine(padX, centerY, padX + usableW, centerY, reflectionPaint);
            canvas.drawPath(waveFillPath, waveFillPaint);
            canvas.drawPath(wavePath2, waveSecondaryPaint);
            canvas.drawPath(wavePath1, waveGlowPaint);
            canvas.drawPath(wavePath1, waveCorePaint);
        } else if (visualMode == VisualMode.OCEAN_WAVE) {
            // Chill / Ocean calm multi-layered smooth wave
            float baselineY = heightPx * 0.84f;
            float centerY = heightPx * 0.54f;
            wavePath1.reset();
            wavePath2.reset();
            waveFillPath.reset();

            int steps = 64;
            float amp1 = heightPx * (0.10f + 0.13f * liveEnergy) * Math.min(1.35f, bassAmpScale);
            float amp2 = heightPx * (0.07f + 0.10f * liveEnergy);
            float slowPhase = phase * 0.46f;

            for (int s = 0; s <= steps; s++) {
                float t = (float) s / steps;
                float x = padX + t * usableW;
                float env = (float) Math.sin(t * Math.PI);
                int barIndex = Math.min(BAR_COUNT - 1, Math.round(t * (BAR_COUNT - 1)));
                float swell = displayBars[barIndex] * heightPx * 0.08f;

                float y1 = centerY - ((float) Math.sin(t * Math.PI * 2.4 + slowPhase) * amp1 + swell) * (0.35f + 0.65f * env);
                float y2 = centerY + ((float) Math.sin(t * Math.PI * 1.8 - slowPhase * 0.85f) * amp2) * (0.35f + 0.65f * env);

                if (s == 0) {
                    wavePath1.moveTo(x, y1);
                    wavePath2.moveTo(x, y2);
                    waveFillPath.moveTo(x, baselineY);
                    waveFillPath.lineTo(x, y1);
                } else {
                    wavePath1.lineTo(x, y1);
                    wavePath2.lineTo(x, y2);
                    waveFillPath.lineTo(x, y1);
                }
            }
            waveFillPath.lineTo(padX + usableW, baselineY);
            waveFillPath.close();

            canvas.drawPath(waveFillPath, waveFillPaint);
            canvas.drawPath(wavePath2, waveSecondaryPaint);
            canvas.drawPath(wavePath1, waveGlowPaint);
            canvas.drawPath(wavePath1, waveCorePaint);
        } else if (visualMode == VisualMode.FIRE_FLAME) {
            // Fire Mode: rising pointed flame tongues with hot core glow
            float baselineY = heightPx * 0.86f;
            float maxFlameH = heightPx * 0.72f * Math.min(1.22f, bassAmpScale);

            for (int i = 0; i < BAR_COUNT; i++) {
                float cx = padX + i * slotW + slotW * 0.5f;
                float flicker = 0.78f + 0.22f * (float) Math.sin(phase * 2.6f + i * 0.73f);
                float tongueH = Math.max(barW * 1.4f, Math.min(heightPx * 0.78f, displayBars[i] * maxFlameH * flicker));
                float top = baselineY - tongueH;

                barRect.set(cx - barW * 0.65f - haloPad, top - haloPad * 0.7f,
                        cx + barW * 0.65f + haloPad, baselineY);
                canvas.drawRoundRect(barRect, barW, barW, barGlowPaint);

                barRect.set(cx - barW * 0.48f, top, cx + barW * 0.48f, baselineY);
                canvas.drawRoundRect(barRect, barW * 0.48f, barW * 0.48f, barPaint);
            }

            wavePath1.reset();
            wavePath2.reset();
            waveFillPath.reset();
            int steps = 60;
            for (int s = 0; s <= steps; s++) {
                float t = (float) s / steps;
                float x = padX + t * usableW;
                int barIndex = Math.min(BAR_COUNT - 1, Math.round(t * (BAR_COUNT - 1)));
                float flameWave = Math.abs((float) Math.sin(t * Math.PI * 6.0 + phase * 1.9f));
                float peak = displayBars[barIndex] * maxFlameH * (0.48f + 0.52f * flameWave);
                float y1 = Math.max(heightPx * 0.08f, baselineY - peak);
                float y2 = Math.max(heightPx * 0.16f, baselineY - peak * 0.62f);

                if (s == 0) {
                    wavePath1.moveTo(x, y1);
                    wavePath2.moveTo(x, y2);
                    waveFillPath.moveTo(x, baselineY);
                    waveFillPath.lineTo(x, y1);
                } else {
                    wavePath1.lineTo(x, y1);
                    wavePath2.lineTo(x, y2);
                    waveFillPath.lineTo(x, y1);
                }
            }
            waveFillPath.lineTo(padX + usableW, baselineY);
            waveFillPath.close();

            canvas.drawPath(waveFillPath, waveFillPaint);
            canvas.drawPath(wavePath2, waveSecondaryPaint);
            canvas.drawPath(wavePath1, waveGlowPaint);
            canvas.drawPath(wavePath1, waveCorePaint);
        } else {
            float baselineY = visualMode == VisualMode.NEON_RIBBONS ? heightPx * 0.56f : heightPx * 0.68f;
            if (visualMode == VisualMode.SPECTRUM_WAVE) {
                float maxBarHeight = heightPx * 0.56f * Math.min(1.15f, bassAmpScale);
                for (int i = 0; i < BAR_COUNT; i++) {
                    float cx = padX + i * slotW + slotW * 0.5f;
                    float barH = Math.max(barW * 1.2f, Math.min(heightPx * 0.64f, displayBars[i] * maxBarHeight));
                    float top = baselineY - barH;

                    barRect.set(cx - barW * 0.5f - haloPad, top - haloPad * 0.5f,
                            cx + barW * 0.5f + haloPad, baselineY);
                    canvas.drawRoundRect(barRect, radius + haloPad, radius + haloPad, barGlowPaint);

                    barRect.set(cx - barW * 0.5f, top, cx + barW * 0.5f, baselineY);
                    canvas.drawRoundRect(barRect, radius, radius, barPaint);

                    float reflH = barH * 0.30f;
                    barRect.set(cx - barW * 0.45f, baselineY + 2f, cx + barW * 0.45f, baselineY + 2f + reflH);
                    canvas.drawRoundRect(barRect, radius, radius, reflectionPaint);
                }
            }

            wavePath1.reset();
            wavePath2.reset();
            waveFillPath.reset();

            int steps = 56;
            float mult = (visualMode == VisualMode.NEON_RIBBONS ? 1.55f : 1.0f) * bassAmpScale;
            float amp1 = heightPx * (0.09f + 0.12f * liveEnergy) * mult;
            float amp2 = heightPx * (0.06f + 0.09f * liveEnergy) * mult;
            float waveCenterY = baselineY - heightPx * 0.08f;

            for (int s = 0; s <= steps; s++) {
                float t = (float) s / steps;
                float x = padX + t * usableW;
                float taper = (float) Math.sin(t * Math.PI);
                int barIndex = Math.min(BAR_COUNT - 1, Math.round(t * (BAR_COUNT - 1)));
                float localBoost = displayBars[barIndex] * heightPx * 0.11f;

                float y1 = waveCenterY - (float) Math.sin(t * Math.PI * 3.5 + phase) * amp1 * taper - localBoost * 0.55f;
                float y2 = waveCenterY + (float) Math.cos(t * Math.PI * 2.8 - phase * 1.15f) * amp2 * taper;

                if (s == 0) {
                    wavePath1.moveTo(x, y1);
                    wavePath2.moveTo(x, y2);
                    waveFillPath.moveTo(x, baselineY);
                    waveFillPath.lineTo(x, y1);
                } else {
                    wavePath1.lineTo(x, y1);
                    wavePath2.lineTo(x, y2);
                    waveFillPath.lineTo(x, y1);
                }
            }
            waveFillPath.lineTo(padX + usableW, baselineY);
            waveFillPath.close();

            canvas.drawPath(waveFillPath, waveFillPaint);
            canvas.drawPath(wavePath2, waveSecondaryPaint);
            canvas.drawPath(wavePath1, waveGlowPaint);
            canvas.drawPath(wavePath1, waveCorePaint);
        }

        if ((needsMoreFrames || isPlaying) && isShown()) {
            scheduleNextFrame(now);
        } else {
            lastAnimFrameMs = 0L;
        }
    }
}
