package com.example.ui.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.Locale;

/**
 * Hardware-styled Crystal Acrylic Dual Analog VU Meter + Digital AVG dB Windows + Peak Hold + CLIP LED
 * + 32-Band Spectrum Analyzer (Peak Hold) + Interactive GAIN L / GAIN R Rotary Knobs + Mode Pill.
 */
public class DiscoVuGlassView extends View {
    public interface OnGainKnobsListener {
        void onGainKnobsChanged(float gainLDb, float gainRDb, boolean fromUser);
    }

    public interface OnQuickModeClickListener {
        void onQuickModeSelected(int quickMode);
    }

    public static final int QUICK_NONE = 0;
    public static final int QUICK_BASS_PRO = 1;
    public static final int QUICK_CLEAN_4K = 2;
    public static final int QUICK_CLUB = 3;

    private static final String[] SCALE_LABELS = {"-20", "-10", "-7", "-5", "-3", "0", "+1", "+2", "+3"};
    private static final float[] SCALE_ANGLES = {-46f, -34f, -22f, -11f, 0f, 12f, 24f, 35f, 46f};
    private static final int SPEC_COLS = 32;
    private static final int SPEC_ROWS = 4;

    private static final int TOUCH_NONE = 0;
    private static final int TOUCH_GAIN_L = 1;
    private static final int TOUCH_GAIN_R = 2;

    private final Paint outerGlassPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint outerRimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint innerBevelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint screwBodyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint screwRimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint screwSlotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dialBgLeftPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dialBgRightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dialBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dialGlassGlossPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint channelLabelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint scaleTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pkMarkerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pkNeedlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint needleGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint needlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint avgBoxBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint avgBoxBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint avgHeaderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint avgValuePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint clipHaloPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint clipLedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint clipTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Paint specTrayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint specTrayBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ledCellPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint peakCapPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint captionTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Paint pillBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pillBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint discoTitlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint discoSubPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint discoMetaPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Paint knobMetalPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint knobRimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint knobPointerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint knobGlowDotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint knobLabelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint knobValPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final RectF outerRect = new RectF();
    private final RectF innerRect = new RectF();
    private final RectF leftDialRect = new RectF();
    private final RectF rightDialRect = new RectF();
    private final RectF specTrayRect = new RectF();
    private final RectF pillRect = new RectF();
    private final RectF arcOval = new RectF();
    private final RectF avgBoxRect = new RectF();
    private final RectF cellRect = new RectF();
    private final RectF leftKnobHitRect = new RectF();
    private final RectF rightKnobHitRect = new RectF();
    private final RectF quickBtn1Rect = new RectF();
    private final RectF quickBtn2Rect = new RectF();
    private final RectF quickBtn3Rect = new RectF();
    private final Paint quickBtnFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint quickBtnBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint quickBtnTitlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint quickBtnSubPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path pkTrianglePath = new Path();

    private final float[] targetSpec = new float[SPEC_COLS];
    private final float[] currentSpec = new float[SPEC_COLS];
    private final float[] peakSpec = new float[SPEC_COLS];

    private boolean isPlaying = false;
    private float targetLeft = 0.06f;
    private float targetRight = 0.06f;
    private float currentLeft = 0.06f;
    private float currentRight = 0.06f;
    private float peakHoldLeft = 0.06f;
    private float peakHoldRight = 0.06f;

    private float leftAvgDb = -48.0f;
    private float rightAvgDb = -48.0f;
    private float clipFlashIntensity = 0f;
    private long lastClipTriggerMs = 0L;

    private int sampleRateHz = 44100;
    private int bitDepth = 24;
    private String modeTitle = "DISCO MODE";

    // Gain L / R knobs (-12.0dB .. +6.0dB, default 0.0dB)
    private float gainLDb = 0.0f;
    private float gainRDb = 0.0f;
    private int activeTouchKnob = TOUCH_NONE;
    private float lastTouchY = 0f;
    private long lastKnobTapMs = 0L;
    private int lastTapKnob = TOUCH_NONE;
    private OnGainKnobsListener gainKnobsListener;
    private OnQuickModeClickListener quickModeListener;
    private int activeQuickMode = QUICK_CLEAN_4K;
    private float liveBpm = 120.0f;
    private int pressedQuickMode = QUICK_NONE;
    private long quickPressStartMs = 0L;
    private StudioLedButtonHelper ledButtonHelper;

    private long lastSpectrumTimeMs = 0L;
    private long lastAnimFrameMs = 0L;
    private float phase = 0f;
    private float density = 1f;
    private long frameIntervalMs = 16L;
    private final Runnable frameRunnable = this::invalidate;

    public DiscoVuGlassView(Context context) {
        super(context);
        init(context);
    }

    public DiscoVuGlassView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public DiscoVuGlassView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        density = context.getResources().getDisplayMetrics().density;
        ledButtonHelper = new StudioLedButtonHelper(density);

        outerGlassPaint.setStyle(Paint.Style.FILL);
        outerRimPaint.setStyle(Paint.Style.STROKE);
        outerRimPaint.setStrokeWidth(2.0f * density);

        innerBevelPaint.setStyle(Paint.Style.STROKE);
        innerBevelPaint.setStrokeWidth(1.0f * density);
        innerBevelPaint.setColor(0x66E2E8F0);

        screwBodyPaint.setStyle(Paint.Style.FILL);
        screwBodyPaint.setColor(0xFF94A3B8);
        screwRimPaint.setStyle(Paint.Style.STROKE);
        screwRimPaint.setStrokeWidth(1.0f * density);
        screwRimPaint.setColor(0xFFE2E8F0);
        screwSlotPaint.setStyle(Paint.Style.STROKE);
        screwSlotPaint.setStrokeWidth(1.2f * density);
        screwSlotPaint.setColor(0xFF1E293B);

        dialBgLeftPaint.setStyle(Paint.Style.FILL);
        dialBgRightPaint.setStyle(Paint.Style.FILL);

        dialBorderPaint.setStyle(Paint.Style.STROKE);
        dialBorderPaint.setStrokeWidth(1.3f * density);
        dialBorderPaint.setColor(0xAAFFFFFF);

        dialGlassGlossPaint.setStyle(Paint.Style.FILL);

        channelLabelPaint.setColor(0x99FFFFFF);
        channelLabelPaint.setTextSize(15.5f * density);
        channelLabelPaint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));

        tickPaint.setStyle(Paint.Style.STROKE);
        tickPaint.setStrokeCap(Paint.Cap.ROUND);

        scaleTextPaint.setTextAlign(Paint.Align.CENTER);
        scaleTextPaint.setTextSize(7.8f * density);
        scaleTextPaint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));

        pkMarkerPaint.setStyle(Paint.Style.FILL);
        pkMarkerPaint.setColor(0xFFF59E0B);
        pkMarkerPaint.setTextAlign(Paint.Align.CENTER);
        pkMarkerPaint.setTextSize(6.5f * density);
        pkMarkerPaint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));

        pkNeedlePaint.setStyle(Paint.Style.STROKE);
        pkNeedlePaint.setStrokeCap(Paint.Cap.ROUND);
        pkNeedlePaint.setStrokeWidth(1.1f * density);
        pkNeedlePaint.setColor(0xCCFBBF24);

        needleGlowPaint.setStyle(Paint.Style.STROKE);
        needleGlowPaint.setStrokeCap(Paint.Cap.ROUND);
        needleGlowPaint.setStrokeWidth(4.0f * density);

        needlePaint.setStyle(Paint.Style.STROKE);
        needlePaint.setStrokeCap(Paint.Cap.ROUND);
        needlePaint.setStrokeWidth(1.7f * density);
        needlePaint.setColor(0xFFFFFFFF);

        avgBoxBgPaint.setStyle(Paint.Style.FILL);
        avgBoxBgPaint.setColor(0xEE070913);

        avgBoxBorderPaint.setStyle(Paint.Style.STROKE);
        avgBoxBorderPaint.setStrokeWidth(1.1f * density);
        avgBoxBorderPaint.setColor(0xAA94A3B8);

        avgHeaderPaint.setTextAlign(Paint.Align.CENTER);
        avgHeaderPaint.setColor(0xFF94A3B8);
        avgHeaderPaint.setTextSize(5.8f * density);
        avgHeaderPaint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        avgHeaderPaint.setLetterSpacing(0.08f);

        avgValuePaint.setTextAlign(Paint.Align.CENTER);
        avgValuePaint.setColor(0xFFF8FAFC);
        avgValuePaint.setTextSize(9.8f * density);
        avgValuePaint.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));

        clipHaloPaint.setStyle(Paint.Style.FILL);
        clipLedPaint.setStyle(Paint.Style.FILL);
        clipTextPaint.setTextAlign(Paint.Align.CENTER);
        clipTextPaint.setTextSize(6.6f * density);
        clipTextPaint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));

        specTrayPaint.setStyle(Paint.Style.FILL);
        specTrayPaint.setColor(0xAA060914);

        specTrayBorderPaint.setStyle(Paint.Style.STROKE);
        specTrayBorderPaint.setStrokeWidth(1.1f * density);
        specTrayBorderPaint.setColor(0x88CBD5E1);

        ledCellPaint.setStyle(Paint.Style.FILL);
        peakCapPaint.setStyle(Paint.Style.FILL);
        peakCapPaint.setColor(0xFFF8FAFC);

        captionTextPaint.setTextAlign(Paint.Align.CENTER);
        captionTextPaint.setColor(0xCC94A3B8);
        captionTextPaint.setTextSize(6.4f * density);
        captionTextPaint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        captionTextPaint.setLetterSpacing(0.08f);

        pillBgPaint.setStyle(Paint.Style.FILL);
        pillBgPaint.setColor(0xC8141526);

        pillBorderPaint.setStyle(Paint.Style.STROKE);
        pillBorderPaint.setStrokeWidth(1.2f * density);
        pillBorderPaint.setColor(0xBBFFFFFF);

        discoTitlePaint.setTextAlign(Paint.Align.CENTER);
        discoTitlePaint.setColor(0xFFFFFFFF);
        discoTitlePaint.setTextSize(12.2f * density);
        discoTitlePaint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        discoTitlePaint.setLetterSpacing(0.11f);

        discoSubPaint.setTextAlign(Paint.Align.CENTER);
        discoSubPaint.setColor(0xFFE2E8F0);
        discoSubPaint.setTextSize(7.6f * density);
        discoSubPaint.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));

        discoMetaPaint.setTextAlign(Paint.Align.CENTER);
        discoMetaPaint.setColor(0xAA94A3B8);
        discoMetaPaint.setTextSize(6.2f * density);
        discoMetaPaint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        discoMetaPaint.setLetterSpacing(0.06f);

        knobMetalPaint.setStyle(Paint.Style.FILL);
        knobRimPaint.setStyle(Paint.Style.STROKE);
        knobRimPaint.setStrokeWidth(1.3f * density);
        knobRimPaint.setColor(0xDDE2E8F0);

        knobPointerPaint.setStyle(Paint.Style.STROKE);
        knobPointerPaint.setStrokeCap(Paint.Cap.ROUND);
        knobPointerPaint.setStrokeWidth(2.0f * density);
        knobPointerPaint.setColor(0xFF0F172A);

        knobGlowDotPaint.setStyle(Paint.Style.FILL);

        knobLabelPaint.setTextAlign(Paint.Align.CENTER);
        knobLabelPaint.setColor(0xFFE2E8F0);
        knobLabelPaint.setTextSize(6.8f * density);
        knobLabelPaint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        knobLabelPaint.setLetterSpacing(0.06f);

        knobValPaint.setTextAlign(Paint.Align.CENTER);
        knobValPaint.setTextSize(6.3f * density);
        knobValPaint.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));

        quickBtnFillPaint.setStyle(Paint.Style.FILL);
        quickBtnBorderPaint.setStyle(Paint.Style.STROKE);
        quickBtnBorderPaint.setStrokeWidth(1.35f * density);
        quickBtnTitlePaint.setTextAlign(Paint.Align.CENTER);
        quickBtnTitlePaint.setTextSize(8.4f * density);
        quickBtnTitlePaint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        quickBtnTitlePaint.setLetterSpacing(0.05f);
        quickBtnSubPaint.setTextAlign(Paint.Align.CENTER);
        quickBtnSubPaint.setTextSize(6.4f * density);
        quickBtnSubPaint.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));

        for (int i = 0; i < SPEC_COLS; i++) {
            targetSpec[i] = 0.08f;
            currentSpec[i] = 0.08f;
            peakSpec[i] = 0.10f;
        }
    }

    public void setTargetFps(int fps) {
        int clamped = Math.max(18, Math.min(60, fps));
        this.frameIntervalMs = Math.max(16L, Math.round(1000.0 / clamped));
    }

    public void setSampleRateHz(int sampleRateHz) {
        this.sampleRateHz = sampleRateHz > 0 ? sampleRateHz : 44100;
        if (isShown()) invalidate();
    }

    public void setBitDepth(int bitDepth) {
        this.bitDepth = bitDepth > 0 ? bitDepth : 24;
        if (isShown()) invalidate();
    }

    public void setModeTitle(@Nullable String title) {
        this.modeTitle = (title != null && !title.trim().isEmpty())
                ? title.trim().toUpperCase(Locale.US)
                : "DISCO MODE";
        if (isShown()) invalidate();
    }

    public void setGainKnobs(float gainLDb, float gainRDb) {
        this.gainLDb = Math.max(-12f, Math.min(6f, gainLDb));
        this.gainRDb = Math.max(-12f, Math.min(6f, gainRDb));
        if (isShown()) invalidate();
    }

    public float getGainLDb() { return gainLDb; }
    public float getGainRDb() { return gainRDb; }

    public void setOnGainKnobsListener(OnGainKnobsListener listener) {
        this.gainKnobsListener = listener;
    }

    public void setOnQuickModeClickListener(OnQuickModeClickListener listener) {
        this.quickModeListener = listener;
    }

    public void setQuickModeState(int activeQuickMode, float liveBpm) {
        boolean changed = this.activeQuickMode != activeQuickMode
                || (liveBpm >= 50f && Math.abs(this.liveBpm - liveBpm) >= 0.5f);
        this.activeQuickMode = activeQuickMode;
        if (liveBpm >= 50f && liveBpm <= 220f) {
            this.liveBpm = liveBpm;
        }
        if (changed && isShown()) {
            postInvalidateOnAnimation();
        }
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

    public void updateMeterTelemetry(float leftAvgDb, float rightAvgDb, boolean clipDetected, int bitDepth) {
        this.leftAvgDb = leftAvgDb;
        this.rightAvgDb = rightAvgDb;
        if (bitDepth > 0) this.bitDepth = bitDepth;
        if (clipDetected) {
            this.lastClipTriggerMs = SystemClock.uptimeMillis();
            this.clipFlashIntensity = 1.0f;
        }
    }

    public void updateStereoSpectrum(
            @Nullable float[] bars,
            float energy,
            float leftLevel,
            float rightLevel,
            int sampleRateHz) {
        float estBass = energy * 0.9f;
        if (bars != null && bars.length >= 6) {
            float sum = 0f;
            for (int i = 0; i < 6; i++) sum += bars[i];
            estBass = sum / 6f;
        }
        updateStereoSpectrum(bars, energy, leftLevel, rightLevel, estBass, sampleRateHz);
    }

    public void updateStereoSpectrum(
            @Nullable float[] bars,
            float energy,
            float leftLevel,
            float rightLevel,
            float bassEnergy,
            int sampleRateHz) {
        if (sampleRateHz > 0) {
            this.sampleRateHz = sampleRateHz;
        }
        float bassKick = Math.max(0f, Math.min(1f, bassEnergy)) * 0.42f;
        this.targetLeft = Math.max(0.04f, Math.min(1f, leftLevel * 0.85f + bassKick));
        this.targetRight = Math.max(0.04f, Math.min(1f, rightLevel * 0.85f + bassKick));

        if (this.targetRight >= 0.94f || this.targetLeft >= 0.94f) {
            lastClipTriggerMs = SystemClock.uptimeMillis();
            clipFlashIntensity = 1.0f;
        }

        if (bars != null && bars.length > 0) {
            for (int c = 0; c < SPEC_COLS; c++) {
                int idx = Math.min(bars.length - 1, Math.round((c / (float) (SPEC_COLS - 1)) * (bars.length - 1)));
                float colBassLift = c < 10 ? bassKick * 0.34f : 0f;
                targetSpec[c] = Math.max(0.05f, Math.min(1f, bars[idx] * 1.18f + colBassLift));
            }
        } else {
            for (int c = 0; c < SPEC_COLS; c++) {
                targetSpec[c] = Math.max(0.05f, Math.min(1f, energy + bassKick));
            }
        }
        lastSpectrumTimeMs = SystemClock.uptimeMillis();
        if (isShown() && getVisibility() == VISIBLE) {
            postInvalidateOnAnimation();
        }
    }

    public void updateSpectrum(@Nullable float[] bars, float energy) {
        float balanced = Math.max(0.05f, Math.min(1f, energy * 1.35f));
        targetLeft = balanced;
        targetRight = balanced;
        if (bars != null && bars.length > 0) {
            for (int c = 0; c < SPEC_COLS; c++) {
                int idx = Math.min(bars.length - 1, Math.round((c / (float) (SPEC_COLS - 1)) * (bars.length - 1)));
                targetSpec[c] = Math.max(0.06f, Math.min(1f, bars[idx] * 1.18f));
            }
        }
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

        outerGlassPaint.setShader(new LinearGradient(
                0, 0, w, h,
                new int[]{0xC81E243B, 0xD80D1120, 0xC82B1938},
                null,
                Shader.TileMode.CLAMP
        ));
        outerRimPaint.setShader(new LinearGradient(
                0, 0, w, h,
                new int[]{0xFF67E8F9, 0xFFF8FAFC, 0xFFF472B6, 0xFFA78BFA},
                null,
                Shader.TileMode.CLAMP
        ));

        float dialY = h * 0.36f;
        float dialR = Math.max(w * 0.34f, h * 0.38f);
        dialBgLeftPaint.setShader(new RadialGradient(
                w * 0.27f, dialY, dialR,
                new int[]{0xE038BDF8, 0xBF0284C7, 0xAA0F172A},
                new float[]{0f, 0.56f, 1f},
                Shader.TileMode.CLAMP
        ));
        dialBgRightPaint.setShader(new RadialGradient(
                w * 0.73f, dialY, dialR,
                new int[]{0xE0F43F5E, 0xBFBE185D, 0xAA1E112A},
                new float[]{0f, 0.56f, 1f},
                Shader.TileMode.CLAMP
        ));
        dialGlassGlossPaint.setShader(new LinearGradient(
                0, 0, 0, h * 0.42f,
                new int[]{0x38FFFFFF, 0x05FFFFFF, 0x00FFFFFF},
                new float[]{0f, 0.45f, 1f},
                Shader.TileMode.CLAMP
        ));

        knobMetalPaint.setShader(new LinearGradient(
                0, 0, 26f * density, 26f * density,
                new int[]{0xFFF1F5F9, 0xFF94A3B8, 0xFF475569},
                null,
                Shader.TileMode.MIRROR
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
                float osc = 0.46f + 0.22f * (float) Math.sin(phase) + 0.12f * (float) Math.cos(phase * 1.6f);
                targetLeft = Math.max(0.14f, Math.min(0.88f, osc));
                targetRight = Math.max(0.14f, Math.min(0.88f, osc + 0.05f));
                for (int c = 0; c < SPEC_COLS; c++) {
                    float env = (float) Math.sin(((c + 1f) / (SPEC_COLS + 1f)) * Math.PI);
                    float wave = 0.5f + 0.5f * (float) Math.sin(phase * 1.3f + c * 0.34f);
                    targetSpec[c] = Math.max(0.10f, Math.min(0.92f, 0.18f + 0.65f * env * wave));
                }
            }
        } else {
            targetLeft = 0.04f;
            targetRight = 0.04f;
            leftAvgDb = -48.0f;
            rightAvgDb = -48.0f;
            for (int c = 0; c < SPEC_COLS; c++) {
                targetSpec[c] = 0.05f;
            }
        }

        float lDiff = targetLeft - currentLeft;
        float rDiff = targetRight - currentRight;
        currentLeft += lDiff * (1f - (float) Math.exp(-dt * (lDiff > 0f ? 26.0 : 12.0)));
        currentRight += rDiff * (1f - (float) Math.exp(-dt * (rDiff > 0f ? 26.0 : 12.0)));

        // Peak hold decay for L & R analog dials
        if (currentLeft >= peakHoldLeft) {
            peakHoldLeft = currentLeft;
        } else {
            peakHoldLeft = Math.max(currentLeft, peakHoldLeft - dt * 0.24f);
        }
        if (currentRight >= peakHoldRight) {
            peakHoldRight = currentRight;
        } else {
            peakHoldRight = Math.max(currentRight, peakHoldRight - dt * 0.24f);
        }

        // CLIP LED smooth decay
        if (now - lastClipTriggerMs < 240L) {
            clipFlashIntensity = 1.0f;
        } else {
            clipFlashIntensity = Math.max(0f, clipFlashIntensity - dt * 2.8f);
        }

        // 32-band bars + floating peak holds
        for (int c = 0; c < SPEC_COLS; c++) {
            float sd = targetSpec[c] - currentSpec[c];
            currentSpec[c] += sd * (1f - (float) Math.exp(-dt * (sd > 0f ? 28.0 : 14.0)));
            if (currentSpec[c] >= peakSpec[c]) {
                peakSpec[c] = currentSpec[c];
            } else {
                peakSpec[c] = Math.max(currentSpec[c], peakSpec[c] - dt * 0.34f);
            }
        }

        // 1. Acrylic Chrome Outer Rack Housing + 4 Corner Screws
        float pad = 2.5f * density;
        outerRect.set(pad, pad, w - pad, h - pad);
        float outerCorner = 20f * density;
        canvas.drawRoundRect(outerRect, outerCorner, outerCorner, outerGlassPaint);
        canvas.drawRoundRect(outerRect, outerCorner, outerCorner, outerRimPaint);

        innerRect.set(pad + 4.5f * density, pad + 4.5f * density, w - pad - 4.5f * density, h - pad - 4.5f * density);
        canvas.drawRoundRect(innerRect, 16f * density, 16f * density, innerBevelPaint);

        float screwInset = 11f * density;
        drawCornerScrew(canvas, screwInset, screwInset, 32f);
        drawCornerScrew(canvas, w - screwInset, screwInset, -18f);
        drawCornerScrew(canvas, screwInset, h - screwInset, 72f);
        drawCornerScrew(canvas, w - screwInset, h - screwInset, 12f);

        // 2. Left & Right Illuminated Glass VU Dials
        float contentPad = 12f * density;
        float dialGap = 8f * density;
        float dialTop = pad + 11f * density;
        float dialBottom = h * 0.56f;
        float dialW = (w - contentPad * 2f - dialGap) * 0.5f;

        leftDialRect.set(contentPad, dialTop, contentPad + dialW, dialBottom);
        rightDialRect.set(contentPad + dialW + dialGap, dialTop, w - contentPad, dialBottom);

        float lDbDisp = isPlaying ? (leftAvgDb > -45f ? leftAvgDb : (-22f + currentLeft * 25f)) : -48.0f;
        float rDbDisp = isPlaying ? (rightAvgDb > -45f ? rightAvgDb : (-22f + currentRight * 25f)) : -48.0f;
        drawVuDial(canvas, leftDialRect, "L", currentLeft, peakHoldLeft, lDbDisp, false);
        drawVuDial(canvas, rightDialRect, "R", currentRight, peakHoldRight, rDbDisp, true);

        // 3. 32-Band Spectrum Analyzer + Peak Hold
        float trayTop = dialBottom + 7f * density;
        float trayBottom = h * 0.72f;
        specTrayRect.set(contentPad, trayTop, w - contentPad, trayBottom);
        canvas.drawRoundRect(specTrayRect, 7f * density, 7f * density, specTrayPaint);
        canvas.drawRoundRect(specTrayRect, 7f * density, 7f * density, specTrayBorderPaint);
        drawHorizontalSpectrum(canvas, specTrayRect);

        canvas.drawText("32-BAND SPECTRUM ANALYZER • PEAK HOLD", w * 0.5f, trayBottom + 8.5f * density, captionTextPaint);

        // 4. Bottom Flanks: GAIN L Knob + 3 Neon Quick-Mode Buttons (BASS PRO | CLEAN 4K | CLUB BPM) + GAIN R Knob
        float bottomTop = trayBottom + 13f * density;
        float bottomBottom = h - pad - 6f * density;
        float centerY = (bottomTop + bottomBottom) * 0.5f;

        float knobZoneW = Math.min(w * 0.20f, 66f * density);
        float pillW = Math.min(w - (contentPad + knobZoneW) * 2f - 6f * density, 224f * density);
        float pillLeft = (w - pillW) * 0.5f;
        pillRect.set(pillLeft, bottomTop, pillLeft + pillW, bottomBottom);
        float pillRadius = 11f * density;
        canvas.drawRoundRect(pillRect, pillRadius, pillRadius, pillBgPaint);
        canvas.drawRoundRect(pillRect, pillRadius, pillRadius, pillBorderPaint);

        float btnGap = 4.5f * density;
        float btnPad = 5.0f * density;
        float btnW = (pillW - btnPad * 2f - btnGap * 2f) / 3f;
        float btnTop = pillRect.top + 4.5f * density;
        float btnBot = pillRect.bottom - 4.5f * density;
        float btnCorner = 7.5f * density;

        quickBtn1Rect.set(pillLeft + btnPad, btnTop, pillLeft + btnPad + btnW, btnBot);
        quickBtn2Rect.set(quickBtn1Rect.right + btnGap, btnTop, quickBtn1Rect.right + btnGap + btnW, btnBot);
        quickBtn3Rect.set(quickBtn2Rect.right + btnGap, btnTop, quickBtn2Rect.right + btnGap + btnW, btnBot);

        float pressFlash1 = (pressedQuickMode == QUICK_BASS_PRO) ? Math.max(0f, 1f - (now - quickPressStartMs) / 240f) : 0f;
        float pressFlash2 = (pressedQuickMode == QUICK_CLEAN_4K) ? Math.max(0f, 1f - (now - quickPressStartMs) / 240f) : 0f;
        float pressFlash3 = (pressedQuickMode == QUICK_CLUB) ? Math.max(0f, 1f - (now - quickPressStartMs) / 240f) : 0f;
        float beatGlow = isPlaying ? Math.min(1f, (currentLeft + currentRight) * 0.65f) : 0.15f;

        drawQuickModePill(canvas, quickBtn1Rect, btnCorner,
                "BASS", "PRO", StudioLedButtonHelper.STUDIO_RED,
                activeQuickMode == QUICK_BASS_PRO, beatGlow, pressFlash1);
        drawQuickModePill(canvas, quickBtn2Rect, btnCorner,
                "CLEAN", "4K HD", StudioLedButtonHelper.STUDIO_GREEN,
                activeQuickMode == QUICK_CLEAN_4K, beatGlow, pressFlash2);
        String bpmLabel = String.format(Locale.US, "%d BPM", Math.round(liveBpm));
        drawQuickModePill(canvas, quickBtn3Rect, btnCorner,
                "CLUB", bpmLabel, StudioLedButtonHelper.STUDIO_YELLOW,
                activeQuickMode == QUICK_CLUB, beatGlow, pressFlash3);

        float knobR = Math.min(13.5f * density, (bottomBottom - bottomTop) * 0.34f);
        float leftKnobCx = contentPad + knobZoneW * 0.5f;
        float rightKnobCx = w - contentPad - knobZoneW * 0.5f;
        float knobCy = centerY + 1.5f * density;

        leftKnobHitRect.set(leftKnobCx - 30f * density, bottomTop - 8f * density,
                leftKnobCx + 30f * density, bottomBottom + 6f * density);
        rightKnobHitRect.set(rightKnobCx - 30f * density, bottomTop - 8f * density,
                rightKnobCx + 30f * density, bottomBottom + 6f * density);

        drawRotaryGainKnob(canvas, leftKnobCx, knobCy, knobR, "GAIN L", gainLDb, 0xFF38BDF8);
        drawRotaryGainKnob(canvas, rightKnobCx, knobCy, knobR, "GAIN R", gainRDb, 0xFFF43F9E);

        boolean stillAnimating = isPlaying
                || Math.abs(lDiff) > 0.01f
                || Math.abs(rDiff) > 0.01f
                || clipFlashIntensity > 0.01f
                || pressFlash1 > 0.01f || pressFlash2 > 0.01f || pressFlash3 > 0.01f
                || peakHoldLeft > currentLeft + 0.01f
                || peakHoldRight > currentRight + 0.01f;
        if (stillAnimating && isShown()) {
            scheduleNextFrame(now);
        } else {
            lastAnimFrameMs = 0L;
        }
    }

    private void drawCornerScrew(Canvas canvas, float cx, float cy, float angleDeg) {
        float r = 3.6f * density;
        canvas.drawCircle(cx, cy, r, screwBodyPaint);
        canvas.drawCircle(cx, cy, r, screwRimPaint);
        double rad = Math.toRadians(angleDeg);
        float dx = (float) Math.cos(rad) * (r * 0.65f);
        float dy = (float) Math.sin(rad) * (r * 0.65f);
        canvas.drawLine(cx - dx, cy - dy, cx + dx, cy + dy, screwSlotPaint);
    }

    private void drawVuDial(
            Canvas canvas,
            RectF rect,
            String chLabel,
            float level,
            float peakHold,
            float avgDb,
            boolean isRight) {
        float corner = 12f * density;
        canvas.drawRoundRect(rect, corner, corner, isRight ? dialBgRightPaint : dialBgLeftPaint);
        canvas.drawRoundRect(rect, corner, corner, dialBorderPaint);

        // Top gloss reflection strip inside dial
        canvas.save();
        canvas.clipRect(rect.left + 2, rect.top + 2, rect.right - 2, rect.top + rect.height() * 0.42f);
        canvas.drawRoundRect(rect, corner, corner, dialGlassGlossPaint);
        canvas.restore();

        // Channel label top-left
        channelLabelPaint.setTextAlign(Paint.Align.LEFT);
        canvas.drawText(chLabel, rect.left + 8f * density, rect.top + 16f * density, channelLabelPaint);

        // Red CLIP LED in top-right of Right Dial
        if (isRight) {
            float ledCx = rect.right - 15f * density;
            float ledCy = rect.top + 11f * density;
            float baseGlowAlpha = 0.25f + 0.75f * clipFlashIntensity;
            int glowColor = lerpColor(0x33EF4444, 0xFFFF2E4D, baseGlowAlpha);
            clipHaloPaint.setColor(withAlpha(glowColor, Math.round(40 + 180 * clipFlashIntensity)));
            canvas.drawCircle(ledCx, ledCy, (4.5f + 4.5f * clipFlashIntensity) * density, clipHaloPaint);

            clipLedPaint.setColor(clipFlashIntensity > 0.2f ? 0xFFFF3B5C : 0xFF7F1D1D);
            canvas.drawCircle(ledCx, ledCy, 3.2f * density, clipLedPaint);

            clipTextPaint.setColor(clipFlashIntensity > 0.2f ? 0xFFFF8FA3 : 0xAAFCA5A5);
            canvas.drawText("CLIP", ledCx, ledCy + 9.5f * density, clipTextPaint);
        }

        float cx = rect.centerX();
        float dialH = rect.height();
        float pivotY = rect.bottom - 14f * density;
        float arcR = Math.min(rect.width() * 0.42f, dialH * 0.64f);

        arcOval.set(cx - arcR, pivotY - arcR, cx + arcR, pivotY + arcR);

        // Scale base arc + hot zone
        tickPaint.setColor(0xD8FFFFFF);
        tickPaint.setStrokeWidth(1.4f * density);
        canvas.drawArc(arcOval, -90f - 46f, 58f, false, tickPaint);

        tickPaint.setColor(isRight ? 0xFFFF5C8D : 0xFF38BDF8);
        tickPaint.setStrokeWidth(2.2f * density);
        canvas.drawArc(arcOval, -90f + 12f, 34f, false, tickPaint);

        // Minor intermediate ticks
        for (int s = 0; s < SCALE_ANGLES.length - 1; s++) {
            float midDeg = (SCALE_ANGLES[s] + SCALE_ANGLES[s + 1]) * 0.5f;
            double mRad = Math.toRadians(midDeg - 90f);
            tickPaint.setColor(0x77FFFFFF);
            tickPaint.setStrokeWidth(0.9f * density);
            float rIn = arcR - 3.5f * density;
            float rOut = arcR + 0.5f * density;
            canvas.drawLine(
                    cx + (float) Math.cos(mRad) * rIn,
                    pivotY + (float) Math.sin(mRad) * rIn,
                    cx + (float) Math.cos(mRad) * rOut,
                    pivotY + (float) Math.sin(mRad) * rOut,
                    tickPaint
            );
        }

        // Major ticks + labels
        for (int i = 0; i < SCALE_LABELS.length; i++) {
            float deg = SCALE_ANGLES[i];
            double rad = Math.toRadians(deg - 90f);
            boolean hot = i >= 6;
            tickPaint.setColor(hot ? (isRight ? 0xFFFF6B8B : 0xFF7DD3FC) : 0xEBFFFFFF);
            tickPaint.setStrokeWidth((hot ? 1.8f : 1.2f) * density);

            float rIn = arcR - 5.5f * density;
            float rOut = arcR + 1.5f * density;
            canvas.drawLine(
                    cx + (float) Math.cos(rad) * rIn,
                    pivotY + (float) Math.sin(rad) * rIn,
                    cx + (float) Math.cos(rad) * rOut,
                    pivotY + (float) Math.sin(rad) * rOut,
                    tickPaint
            );

            float rTxt = arcR + 9.5f * density;
            scaleTextPaint.setColor(hot ? (isRight ? 0xFFFF8FAB : 0xFFBAE6FD) : 0xEEFFFFFF);
            canvas.drawText(
                    SCALE_LABELS[i],
                    cx + (float) Math.cos(rad) * rTxt,
                    pivotY + (float) Math.sin(rad) * rTxt + 2.5f * density,
                    scaleTextPaint
            );
        }

        // PK Peak Hold Marker triangle + hairline peak needle
        float pkDeg = -46f + Math.max(0f, Math.min(1f, peakHold)) * 92f;
        double pkRad = Math.toRadians(pkDeg - 90f);
        float pkR = arcR - 1.5f * density;
        float pkTipX = cx + (float) Math.cos(pkRad) * (pkR + 1f * density);
        float pkTipY = pivotY + (float) Math.sin(pkRad) * (pkR + 1f * density);

        if (isPlaying && peakHold > 0.08f) {
            canvas.drawLine(cx, pivotY, pkTipX, pkTipY, pkNeedlePaint);
        }

        // Amber PK indicator below 0-VU zone
        canvas.drawText("PK", cx, rect.top + dialH * 0.44f, pkMarkerPaint);

        // Moving Needle
        float needleDeg = -46f + Math.max(0f, Math.min(1f, level)) * 92f;
        double needleRad = Math.toRadians(needleDeg - 90f);
        float needleLen = arcR + 3.5f * density;
        float nx = cx + (float) Math.cos(needleRad) * needleLen;
        float ny = pivotY + (float) Math.sin(needleRad) * needleLen;

        needleGlowPaint.setColor(isRight ? 0x66FF4D8D : 0x6638BDF8);
        canvas.drawLine(cx, pivotY, nx, ny, needleGlowPaint);
        canvas.drawLine(cx, pivotY, nx, ny, needlePaint);

        // Amber Center Triangle above AVG box
        float triY = rect.bottom - 24f * density;
        pkTrianglePath.reset();
        pkTrianglePath.moveTo(cx, triY - 4.5f * density);
        pkTrianglePath.lineTo(cx - 3.2f * density, triY + 1.5f * density);
        pkTrianglePath.lineTo(cx + 3.2f * density, triY + 1.5f * density);
        pkTrianglePath.close();
        canvas.drawPath(pkTrianglePath, pkMarkerPaint);

        // Digital AVG dB Readout Box at Pivot Base
        float boxW = Math.min(rect.width() * 0.56f, 68f * density);
        float boxH = 19.5f * density;
        avgBoxRect.set(cx - boxW * 0.5f, rect.bottom - boxH - 1.5f * density,
                cx + boxW * 0.5f, rect.bottom - 1.5f * density);
        canvas.drawRoundRect(avgBoxRect, 6f * density, 6f * density, avgBoxBgPaint);
        canvas.drawRoundRect(avgBoxRect, 6f * density, 6f * density, avgBoxBorderPaint);

        canvas.drawText("AVG", cx, avgBoxRect.top + 7f * density, avgHeaderPaint);
        String dbStr = avgDb <= -40f
                ? "--.- dB"
                : String.format(Locale.US, "%+.1f dB", avgDb).replace("+", "");
        avgValuePaint.setColor(avgDb >= 0f ? 0xFFFF6B8B : (isRight ? 0xFFFBCFE8 : 0xFFBAE6FD));
        canvas.drawText(dbStr, cx, avgBoxRect.bottom - 3.5f * density, avgValuePaint);
    }

    private void drawHorizontalSpectrum(Canvas canvas, RectF tray) {
        float padX = 7f * density;
        float padY = 4.5f * density;
        float availW = tray.width() - padX * 2f;
        float availH = tray.height() - padY * 2f;
        float colGap = 2.1f * density;
        float rowGap = 1.6f * density;
        float colW = Math.max(1.5f * density, (availW - colGap * (SPEC_COLS - 1)) / SPEC_COLS);
        float rowH = Math.max(1.5f * density, (availH - rowGap * (SPEC_ROWS - 1)) / SPEC_ROWS);

        for (int c = 0; c < SPEC_COLS; c++) {
            float x = tray.left + padX + c * (colW + colGap);
            float normX = (float) c / (SPEC_COLS - 1);
            int colColor = spectrumGradientColor(normX);
            int litRows = Math.max(isPlaying ? 1 : 0, Math.round(currentSpec[c] * SPEC_ROWS));

            for (int r = 0; r < SPEC_ROWS; r++) {
                float bottom = tray.bottom - padY - r * (rowH + rowGap);
                float top = bottom - rowH;
                cellRect.set(x, top, x + colW, bottom);
                if (r < litRows) {
                    ledCellPaint.setColor(colColor);
                } else {
                    ledCellPaint.setColor(withAlpha(colColor, 0x22));
                }
                canvas.drawRoundRect(cellRect, 1.4f * density, 1.4f * density, ledCellPaint);
            }

            // Floating Peak Hold cap per band
            if (isPlaying && peakSpec[c] > 0.08f) {
                float peakY = tray.bottom - padY - peakSpec[c] * availH;
                peakY = Math.max(tray.top + padY, Math.min(tray.bottom - padY - 1.5f * density, peakY));
                peakCapPaint.setColor(withAlpha(colColor, 0xEE));
                canvas.drawRoundRect(x, peakY, x + colW, peakY + 1.5f * density, 1f * density, 1f * density, peakCapPaint);
            }
        }
    }

    private void drawRotaryGainKnob(
            Canvas canvas,
            float cx,
            float cy,
            float radius,
            String label,
            float dbVal,
            int accentColor) {
        canvas.drawText(label, cx, cy - radius - 4.5f * density, knobLabelPaint);

        // Glowing position LED dots underneath/around knob
        float norm = (dbVal + 12f) / 18f; // -12..+6 -> 0..1
        int dots = 7;
        float dotRing = radius + 4.0f * density;
        for (int i = 0; i < dots; i++) {
            float f = (float) i / (dots - 1);
            float deg = -135f + f * 270f;
            double rad = Math.toRadians(deg - 90f);
            float dx = cx + (float) Math.cos(rad) * dotRing;
            float dy = cy + (float) Math.sin(rad) * dotRing;
            boolean active = f <= norm + 0.02f;
            knobGlowDotPaint.setColor(active ? accentColor : 0x4464748B);
            canvas.drawCircle(dx, dy, (active ? 1.5f : 1.1f) * density, knobGlowDotPaint);
        }

        // Brushed Metal Cap
        canvas.drawCircle(cx, cy + 1.5f * density, radius + 1.2f * density, avgBoxBgPaint);
        canvas.drawCircle(cx, cy, radius, knobMetalPaint);
        knobRimPaint.setColor(withAlpha(accentColor, 0xCC));
        canvas.drawCircle(cx, cy, radius, knobRimPaint);

        // Indicator Notch
        float angleDeg = -135f + norm * 270f;
        double rad = Math.toRadians(angleDeg - 90f);
        canvas.drawLine(
                cx + (float) Math.cos(rad) * (radius * 0.28f),
                cy + (float) Math.sin(rad) * (radius * 0.28f),
                cx + (float) Math.cos(rad) * (radius * 0.85f),
                cy + (float) Math.sin(rad) * (radius * 0.85f),
                knobPointerPaint
        );

        knobValPaint.setColor(accentColor);
        String valStr = Math.abs(dbVal) < 0.09f
                ? "0.0dB"
                : String.format(Locale.US, "%+.1fdB", dbVal);
        canvas.drawText(valStr, cx, cy + radius + 9.5f * density, knobValPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        float x = event.getX();
        float y = event.getY();

        if (action == MotionEvent.ACTION_DOWN) {
            if (quickBtn1Rect.contains(x, y)) {
                pressedQuickMode = QUICK_BASS_PRO;
                quickPressStartMs = SystemClock.uptimeMillis();
                invalidate();
                if (quickModeListener != null) quickModeListener.onQuickModeSelected(QUICK_BASS_PRO);
                return true;
            } else if (quickBtn2Rect.contains(x, y)) {
                pressedQuickMode = QUICK_CLEAN_4K;
                quickPressStartMs = SystemClock.uptimeMillis();
                invalidate();
                if (quickModeListener != null) quickModeListener.onQuickModeSelected(QUICK_CLEAN_4K);
                return true;
            } else if (quickBtn3Rect.contains(x, y)) {
                pressedQuickMode = QUICK_CLUB;
                quickPressStartMs = SystemClock.uptimeMillis();
                invalidate();
                if (quickModeListener != null) quickModeListener.onQuickModeSelected(QUICK_CLUB);
                return true;
            }
            if (leftKnobHitRect.contains(x, y)) {
                activeTouchKnob = TOUCH_GAIN_L;
            } else if (rightKnobHitRect.contains(x, y)) {
                activeTouchKnob = TOUCH_GAIN_R;
            } else {
                activeTouchKnob = TOUCH_NONE;
            }

            if (activeTouchKnob != TOUCH_NONE) {
                long now = SystemClock.uptimeMillis();
                if (lastTapKnob == activeTouchKnob && (now - lastKnobTapMs) < 300L) {
                    if (activeTouchKnob == TOUCH_GAIN_L) gainLDb = 0.0f;
                    else gainRDb = 0.0f;
                    invalidate();
                    if (gainKnobsListener != null) {
                        gainKnobsListener.onGainKnobsChanged(gainLDb, gainRDb, true);
                    }
                    lastKnobTapMs = 0L;
                } else {
                    lastTapKnob = activeTouchKnob;
                    lastKnobTapMs = now;
                }
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
                lastTouchY = y;
                return true;
            }
        } else if (action == MotionEvent.ACTION_MOVE && activeTouchKnob != TOUCH_NONE) {
            float dy = lastTouchY - y;
            lastTouchY = y;
            float deltaDb = dy / (10f * density);
            if (Math.abs(deltaDb) > 0.02f) {
                if (activeTouchKnob == TOUCH_GAIN_L) {
                    gainLDb = Math.max(-12f, Math.min(6f, gainLDb + deltaDb));
                } else {
                    gainRDb = Math.max(-12f, Math.min(6f, gainRDb + deltaDb));
                }
                invalidate();
                if (gainKnobsListener != null) {
                    gainKnobsListener.onGainKnobsChanged(gainLDb, gainRDb, true);
                }
            }
            return true;
        } else if ((action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) && activeTouchKnob != TOUCH_NONE) {
            activeTouchKnob = TOUCH_NONE;
            if (getParent() != null) {
                getParent().requestDisallowInterceptTouchEvent(false);
            }
            return true;
        }
        return super.onTouchEvent(event);
    }

    private void drawQuickModePill(
            Canvas canvas,
            RectF rect,
            float corner,
            String title,
            String subtitle,
            int accentColor,
            boolean active,
            float beatGlow,
            float pressFlash) {
        if (ledButtonHelper != null) {
            ledButtonHelper.drawTwoLineButton(
                    canvas,
                    rect,
                    corner,
                    density,
                    title,
                    subtitle,
                    8.6f * density,
                    6.4f * density,
                    accentColor,
                    active,
                    true,
                    beatGlow,
                    pressFlash
            );
        }
    }

    private static int spectrumGradientColor(float t) {
        if (t < 0.25f) {
            return lerpColor(0xFF22C55E, 0xFF84CC16, t / 0.25f);
        } else if (t < 0.52f) {
            return lerpColor(0xFF84CC16, 0xFFFACC15, (t - 0.25f) / 0.27f);
        } else if (t < 0.78f) {
            return lerpColor(0xFFFACC15, 0xFFFB923C, (t - 0.52f) / 0.26f);
        } else {
            return lerpColor(0xFFFB923C, 0xFFEF4444, (t - 0.78f) / 0.22f);
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
