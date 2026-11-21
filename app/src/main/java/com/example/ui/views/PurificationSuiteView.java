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
import android.view.MotionEvent;
import android.view.View;
import androidx.annotation.Nullable;
import java.util.Locale;

/**
 * Hardware-styled SMART AUDIO • 4K PURIFIER rack console:
 *  - Header Capsule: SMART AUDIO • 4K PURIFIER + Anti-Crackle & 4K HD quick-toggle badges + Output indicator
 *  - Upper-Left Bay: 5 Segmented Neon EQ Columns (BASS, LOW MID, MID, HIGH MID, TREBLE) overlaid on
 *    a live 25-bar glowing green->yellow->orange->red harmonic spectrum display
 *  - Upper-Right Bay: Live COMPRESSOR dynamic multi-bar red/magenta meter + CLIP/PROTECT LED +
 *    interactive Ratio/Threshold drag zones & Gate/NR micro-toggles
 *  - Lower Deck: 4 Left Knobs (BASS+, CLARITY, WIDTH, LIMITER) | Center Vertical 2-Column Live
 *    BASS POWER & CLIP LED Ladder (0..100%) | 2 Right Knobs (HPF, LPF) + AUTO GAIN toggle
 *  - Footer Telemetry: CLEAN PROCESSING ACTIVE • 4K HD • BASS PWR: XX% (+X.XdB)
 */
public class PurificationSuiteView extends View {

    public interface SuiteListener {
        void onFaderChanged(int groupIndex, float gainDb);
        void onCompressorChanged(float ratio, float thresholdDb);
        void onNoiseGateToggled(boolean enabled);
        void onNoiseReductionToggled(boolean enabled);
        void onPurificationBassChanged(float boostDb);
        void onTrebleClarityChanged(float clarityDb);
        void onStereoWidthChanged(int widthPercent);
        void onLimiterChanged(float ceilingDb);
        void onAutoGainToggled(boolean enabled);
        void onHpfChanged(int hpfHz);
        void onLpfChanged(int lpfHz);

        default void onUltraHd4kToggled(boolean enabled) {}
        default void onAntiCrackleToggled(boolean enabled) {}
    }

    private static final String[] FADER_LABELS = {"BASS", "LOW MID", "MID", "HIGH MID", "TREBLE"};
    private static final int SPEC_BAR_COUNT = 25;
    private static final int COMP_BAR_COUNT = 16;
    private static final int FADER_LED_ROWS = 10;
    private static final int LADDER_LED_ROWS = 9;

    private static final int TARGET_NONE = -1;
    private static final int TARGET_FADER_BASE = 0; // 0..4
    private static final int TARGET_KNOB_RATIO = 10;
    private static final int TARGET_KNOB_THRESH = 11;
    private static final int TARGET_KNOB_BASS_BOOST = 12;
    private static final int TARGET_KNOB_TREBLE_CLARITY = 13;
    private static final int TARGET_KNOB_STEREO_WIDTH = 14;
    private static final int TARGET_KNOB_LIMITER = 15;
    private static final int TARGET_KNOB_HPF = 16;
    private static final int TARGET_KNOB_LPF = 17;
    private static final int TARGET_SW_NOISE_GATE = 20;
    private static final int TARGET_SW_NOISE_REDUCTION = 21;
    private static final int TARGET_SW_AUTO_GAIN = 22;
    private static final int TARGET_SW_4K_HD = 23;
    private static final int TARGET_SW_ANTI_CRACKLE = 24;

    private final Paint outerGlassPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint outerRimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint headerPillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint headerTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint badgeFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint badgeBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint badgeTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Paint subPanelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint subPanelBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Paint specBarPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ledSegmentPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint capBodyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint capBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint compBarPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Paint labelSmallPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint valueTinyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sectionTitlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Paint knobMetalPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint knobShadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint knobRimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint knobPointerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint knobDotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Paint ledPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ledHaloPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final RectF outerRect = new RectF();
    private final RectF topHeaderRect = new RectF();
    private final RectF faderBayRect = new RectF();
    private final RectF compBayRect = new RectF();
    private final RectF bottomBayRect = new RectF();
    private final RectF bassLadderBayRect = new RectF();
    private final RectF footerRect = new RectF();
    private final RectF tempRect = new RectF();

    private final RectF badgeAntiCrackleRect = new RectF();
    private final RectF badge4kHdRect = new RectF();

    private final RectF[] faderHitRects = new RectF[5];
    private final RectF ratioHitRect = new RectF();
    private final RectF threshHitRect = new RectF();
    private final RectF noiseGateHitRect = new RectF();
    private final RectF noiseRedHitRect = new RectF();
    private final RectF bassBoostHitRect = new RectF();
    private final RectF trebleClarityHitRect = new RectF();
    private final RectF stereoWidthHitRect = new RectF();
    private final RectF limiterHitRect = new RectF();
    private final RectF autoGainHitRect = new RectF();
    private final RectF hpfHitRect = new RectF();
    private final RectF lpfHitRect = new RectF();

    // DSP State
    private final float[] faderDb = {0f, 0f, 0f, 0f, 0f}; // -12..+12 dB
    private float compRatio = 3.0f;           // 1..8
    private float compThresholdDb = -12.0f;   // -30..0
    private boolean noiseGateOn = true;
    private boolean noiseReductionOn = true;
    private float bassBoostDb = 3.0f;         // 0..12
    private float trebleClarityDb = 2.5f;     // 0..12
    private int stereoWidthPercent = 120;     // 0..200
    private float limiterCeilingDb = -1.0f;   // -12..0
    private boolean autoGainOn = true;
    private int hpfHz = 80;                   // 20..250
    private int lpfHz = 18000;                // 8000..22000
    private boolean ultraHd4kOn = true;
    private boolean antiCrackleOn = true;
    private boolean speakerOutputMode = true;

    // Live audio telemetry
    private int bitDepth = 24;
    private float liveCompReductionDb = 0f;
    private float liveLimitingDb = 0f;
    private boolean limiterEngaged = false;
    private int liveBassPowerPercent = 0;
    private float liveBassPowerDb = -48.0f;
    private boolean isPlaying = false;

    private final float[] smoothSpecBars = new float[SPEC_BAR_COUNT];
    private final float[] smoothCompBars = new float[COMP_BAR_COUNT];

    private float density = 1f;
    private int activeTarget = TARGET_NONE;
    private float lastTouchY = 0f;
    private long lastTapMs = 0L;
    private int lastTapTarget = TARGET_NONE;
    private int lastPressedToggle = TARGET_NONE;
    private long togglePressStartMs = 0L;
    private StudioLedButtonHelper ledButtonHelper;
    private SuiteListener listener;

    public PurificationSuiteView(Context context) {
        super(context);
        init(context);
    }

    public PurificationSuiteView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public PurificationSuiteView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        density = context.getResources().getDisplayMetrics().density;
        ledButtonHelper = new StudioLedButtonHelper(density);
        for (int i = 0; i < 5; i++) faderHitRects[i] = new RectF();
        for (int i = 0; i < SPEC_BAR_COUNT; i++) smoothSpecBars[i] = 0.20f;
        for (int i = 0; i < COMP_BAR_COUNT; i++) smoothCompBars[i] = 0.25f;

        outerGlassPaint.setStyle(Paint.Style.FILL);
        outerRimPaint.setStyle(Paint.Style.STROKE);
        outerRimPaint.setStrokeWidth(2.0f * density);

        headerPillPaint.setStyle(Paint.Style.FILL);
        headerPillPaint.setColor(0xDE080D1B);

        headerTextPaint.setTextAlign(Paint.Align.CENTER);
        headerTextPaint.setColor(0xFFE2E8F0);
        headerTextPaint.setTextSize(8.0f * density);
        headerTextPaint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        headerTextPaint.setLetterSpacing(0.08f);

        badgeFillPaint.setStyle(Paint.Style.FILL);
        badgeBorderPaint.setStyle(Paint.Style.STROKE);
        badgeBorderPaint.setStrokeWidth(1.0f * density);
        badgeTextPaint.setTextAlign(Paint.Align.CENTER);
        badgeTextPaint.setTextSize(6.2f * density);
        badgeTextPaint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));

        subPanelPaint.setStyle(Paint.Style.FILL);
        subPanelPaint.setColor(0xB4070B16);

        subPanelBorderPaint.setStyle(Paint.Style.STROKE);
        subPanelBorderPaint.setStrokeWidth(1.0f * density);
        subPanelBorderPaint.setColor(0x5594A3B8);

        specBarPaint.setStyle(Paint.Style.FILL);
        ledSegmentPaint.setStyle(Paint.Style.FILL);
        capBodyPaint.setStyle(Paint.Style.FILL);
        capBorderPaint.setStyle(Paint.Style.STROKE);
        capBorderPaint.setStrokeWidth(0.9f * density);
        capBorderPaint.setColor(0xEEFFFFFF);
        compBarPaint.setStyle(Paint.Style.FILL);

        labelSmallPaint.setTextAlign(Paint.Align.CENTER);
        labelSmallPaint.setColor(0xFFE2E8F0);
        labelSmallPaint.setTextSize(6.3f * density);
        labelSmallPaint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        labelSmallPaint.setLetterSpacing(0.03f);

        valueTinyPaint.setTextAlign(Paint.Align.CENTER);
        valueTinyPaint.setColor(0xFF94A3B8);
        valueTinyPaint.setTextSize(6.0f * density);
        valueTinyPaint.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));

        sectionTitlePaint.setTextAlign(Paint.Align.CENTER);
        sectionTitlePaint.setColor(0xFFF1F5F9);
        sectionTitlePaint.setTextSize(7.2f * density);
        sectionTitlePaint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        sectionTitlePaint.setLetterSpacing(0.08f);

        knobShadowPaint.setStyle(Paint.Style.FILL);
        knobShadowPaint.setColor(0x9903050C);
        knobMetalPaint.setStyle(Paint.Style.FILL);
        knobRimPaint.setStyle(Paint.Style.STROKE);
        knobRimPaint.setStrokeWidth(1.2f * density);
        knobPointerPaint.setStyle(Paint.Style.STROKE);
        knobPointerPaint.setStrokeCap(Paint.Cap.ROUND);
        knobPointerPaint.setStrokeWidth(1.8f * density);
        knobPointerPaint.setColor(0xFF0F172A);
        knobDotPaint.setStyle(Paint.Style.FILL);

        ledPaint.setStyle(Paint.Style.FILL);
        ledHaloPaint.setStyle(Paint.Style.FILL);
    }

    public void setSuiteListener(SuiteListener listener) {
        this.listener = listener;
    }

    public void setPlaying(boolean playing) {
        this.isPlaying = playing;
        if (isShown()) postInvalidateOnAnimation();
    }

    public void syncFromEngine(
            float[] fiveBandsDb,
            float compRatio,
            float compThreshDb,
            boolean noiseGateOn,
            boolean noiseReductionOn,
            float bassBoostDb,
            float trebleClarityDb,
            int stereoWidthPercent,
            float limiterCeilingDb,
            boolean autoGainOn,
            int hpfHz,
            int lpfHz,
            int bitDepth) {
        if (fiveBandsDb != null) {
            for (int i = 0; i < Math.min(5, fiveBandsDb.length); i++) {
                this.faderDb[i] = Math.max(-12f, Math.min(12f, fiveBandsDb[i]));
            }
        }
        this.compRatio = Math.max(1f, Math.min(8f, compRatio));
        this.compThresholdDb = Math.max(-30f, Math.min(0f, compThreshDb));
        this.noiseGateOn = noiseGateOn;
        this.noiseReductionOn = noiseReductionOn;
        this.bassBoostDb = Math.max(0f, Math.min(12f, bassBoostDb));
        this.trebleClarityDb = Math.max(0f, Math.min(12f, trebleClarityDb));
        this.stereoWidthPercent = Math.max(0, Math.min(200, stereoWidthPercent));
        this.limiterCeilingDb = Math.max(-12f, Math.min(0f, limiterCeilingDb));
        this.autoGainOn = autoGainOn;
        this.hpfHz = Math.max(20, Math.min(250, hpfHz));
        this.lpfHz = Math.max(8000, Math.min(22000, lpfHz));
        if (bitDepth > 0) this.bitDepth = bitDepth;
        invalidate();
    }

    public void syncExtendedPurifierState(
            boolean ultraHd4kOn,
            boolean antiCrackleOn,
            boolean speakerOutputMode) {
        this.ultraHd4kOn = ultraHd4kOn;
        this.antiCrackleOn = antiCrackleOn;
        this.speakerOutputMode = speakerOutputMode;
        invalidate();
    }

    public void updateLiveStatus(float compReductionDb, boolean limiterEngaged, int bitDepth) {
        updateLiveStatus(compReductionDb, 0f, limiterEngaged, bitDepth,
                liveBassPowerPercent, liveBassPowerDb, null);
    }

    public void updateLiveStatus(
            float compReductionDb,
            float limitingDb,
            boolean limiterEngaged,
            int bitDepth,
            int bassPowerPercent,
            float bassPowerDb,
            @Nullable float[] bars) {
        this.liveCompReductionDb = Math.max(0f, compReductionDb);
        this.liveLimitingDb = Math.max(0f, limitingDb);
        this.limiterEngaged = limiterEngaged;
        if (bitDepth > 0) this.bitDepth = bitDepth;
        this.liveBassPowerPercent = Math.max(0, Math.min(100, bassPowerPercent));
        this.liveBassPowerDb = bassPowerDb;

        if (bars != null && bars.length > 0) {
            this.isPlaying = true;
            for (int i = 0; i < SPEC_BAR_COUNT; i++) {
                int srcIdx = Math.min(bars.length - 1, Math.round((i / (float) (SPEC_BAR_COUNT - 1)) * (bars.length - 1)));
                float target = Math.max(0.08f, Math.min(1f, bars[srcIdx] * 1.08f));
                smoothSpecBars[i] += (target - smoothSpecBars[i]) * (target > smoothSpecBars[i] ? 0.55f : 0.25f);
            }
            float compSquash = Math.min(0.45f, compReductionDb * 0.04f);
            for (int i = 0; i < COMP_BAR_COUNT; i++) {
                int srcIdx = Math.min(bars.length - 1, Math.round((i / (float) (COMP_BAR_COUNT - 1)) * (bars.length - 1)));
                float target = Math.max(0.12f, Math.min(1f, (bars[srcIdx] * 0.92f + 0.14f) * (1f - compSquash * 0.55f)));
                smoothCompBars[i] += (target - smoothCompBars[i]) * (target > smoothCompBars[i] ? 0.52f : 0.22f);
            }
        }
        if (isShown()) {
            postInvalidateOnAnimation();
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w <= 0 || h <= 0) return;

        outerGlassPaint.setShader(new LinearGradient(
                0, 0, w, h,
                new int[]{0xDC13192C, 0xEA090D18, 0xDC22122D},
                null,
                Shader.TileMode.CLAMP
        ));
        outerRimPaint.setShader(new LinearGradient(
                0, 0, w, h,
                new int[]{0xFF67E8F9, 0xFFE2E8F0, 0xFFF472B6, 0xFF38BDF8},
                null,
                Shader.TileMode.CLAMP
        ));
        knobMetalPaint.setShader(new LinearGradient(
                0, 0, 20f * density, 20f * density,
                new int[]{0xFFF8FAFC, 0xFF94A3B8, 0xFF475569},
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

        float pad = 2.5f * density;
        outerRect.set(pad, pad, w - pad, h - pad);
        canvas.drawRoundRect(outerRect, 18f * density, 18f * density, outerGlassPaint);
        canvas.drawRoundRect(outerRect, 18f * density, 18f * density, outerRimPaint);

        // 1. Top Header Strip: ZERO-CLIP badge | SMART AUDIO • 4K PURIFIER | 4K HD badge
        float innerPad = 9f * density;
        float topH = 21f * density;
        topHeaderRect.set(innerPad, pad + 5f * density, w - innerPad, pad + 5f * density + topH);
        canvas.drawRoundRect(topHeaderRect, 8f * density, 8f * density, headerPillPaint);
        canvas.drawRoundRect(topHeaderRect, 8f * density, 8f * density, subPanelBorderPaint);

        float badgeW = Math.min(62f * density, topHeaderRect.width() * 0.21f);
        float badgePad = 4f * density;
        badgeAntiCrackleRect.set(topHeaderRect.left + badgePad, topHeaderRect.top + 3f * density,
                topHeaderRect.left + badgePad + badgeW, topHeaderRect.bottom - 3f * density);
        badge4kHdRect.set(topHeaderRect.right - badgePad - badgeW, topHeaderRect.top + 3f * density,
                topHeaderRect.right - badgePad, topHeaderRect.bottom - 3f * density);

        drawHeaderBadge(canvas, badgeAntiCrackleRect,
                antiCrackleOn ? "NO-CRACKLE" : "RAW PEAK",
                antiCrackleOn ? StudioLedButtonHelper.STUDIO_GREEN : StudioLedButtonHelper.STUDIO_RED,
                true,
                TARGET_SW_ANTI_CRACKLE);
        drawHeaderBadge(canvas, badge4kHdRect,
                ultraHd4kOn ? "4K HD ON" : "4K OFF",
                StudioLedButtonHelper.STUDIO_YELLOW,
                ultraHd4kOn,
                TARGET_SW_4K_HD);

        headerTextPaint.setColor(0xFFE2E8F0);
        String title = speakerOutputMode
                ? "SMART AUDIO • 4K SPK"
                : "SMART AUDIO • 4K HP";
        canvas.drawText(title, w * 0.5f, topHeaderRect.centerY() + 2.8f * density, headerTextPaint);

        // 2. Footer Telemetry Pill
        float footerH = 18f * density;
        footerRect.set(innerPad, h - pad - footerH - 4f * density, w - innerPad, h - pad - 4f * density);

        float contentTop = topHeaderRect.bottom + 5f * density;
        float contentBottom = footerRect.top - 5f * density;
        float contentH = Math.max(96f * density, contentBottom - contentTop);
        float topRowBottom = contentTop + contentH * 0.55f;

        // Upper-Left: 5-Band Spectrum & Segmented Faders | Upper-Right: Compressor Meter
        float splitX = innerPad + (w - innerPad * 2f) * 0.55f;
        faderBayRect.set(innerPad, contentTop, splitX - 3f * density, topRowBottom);
        compBayRect.set(splitX + 3f * density, contentTop, w - innerPad, topRowBottom);
        bottomBayRect.set(innerPad, topRowBottom + 5f * density, w - innerPad, contentBottom);

        canvas.drawRoundRect(faderBayRect, 10f * density, 10f * density, subPanelPaint);
        canvas.drawRoundRect(faderBayRect, 10f * density, 10f * density, subPanelBorderPaint);

        canvas.drawRoundRect(compBayRect, 10f * density, 10f * density, subPanelPaint);
        canvas.drawRoundRect(compBayRect, 10f * density, 10f * density, subPanelBorderPaint);

        canvas.drawRoundRect(bottomBayRect, 10f * density, 10f * density, subPanelPaint);
        canvas.drawRoundRect(bottomBayRect, 10f * density, 10f * density, subPanelBorderPaint);

        drawUpperLeftEqAndSpectrum(canvas, faderBayRect);
        drawUpperRightCompressor(canvas, compBayRect);
        drawLowerControlDeck(canvas, bottomBayRect);

        // 3. Footer Status Pill
        canvas.drawRoundRect(footerRect, 7f * density, 7f * density, headerPillPaint);
        canvas.drawRoundRect(footerRect, 7f * density, 7f * density, subPanelBorderPaint);
        String bassPwrStr = liveBassPowerPercent > 0
                ? String.format(Locale.US, "BASS PWR: %d%%", liveBassPowerPercent)
                : "BASS PWR: --";
        String footerText;
        if (antiCrackleOn && (limiterEngaged || liveLimitingDb > 0.3f)) {
            footerText = String.format(Locale.US, "ANTI-CRACKLE PROTECT (-%.1fdB) • %s • %dbit",
                    Math.max(0.2f, liveLimitingDb), bassPwrStr, bitDepth);
            headerTextPaint.setColor(0xFF4ADE80);
        } else if (limiterEngaged) {
            footerText = String.format(Locale.US, "PEAK CLIP DETECTED • %s • %dbit", bassPwrStr, bitDepth);
            headerTextPaint.setColor(0xFFFCA5A5);
        } else {
            String hdTag = ultraHd4kOn ? "4K HD" : "STD";
            footerText = String.format(Locale.US, "CLEAN PROCESSING ACTIVE • %s • %s • %dbit",
                    hdTag, bassPwrStr, bitDepth);
            headerTextPaint.setColor(ultraHd4kOn ? 0xFFFACC15 : 0xFF94A3B8);
        }
        canvas.drawText(footerText, w * 0.5f, footerRect.centerY() + 2.8f * density, headerTextPaint);

        if (lastPressedToggle != TARGET_NONE && (SystemClock.uptimeMillis() - togglePressStartMs) < 230L && isShown()) {
            postInvalidateOnAnimation();
        }
    }

    private void drawHeaderBadge(Canvas canvas, RectF rect, String text, int color, boolean active, int targetId) {
        if (ledButtonHelper != null) {
            long elapsed = SystemClock.uptimeMillis() - togglePressStartMs;
            float flash = (lastPressedToggle == targetId && elapsed < 230L)
                    ? Math.max(0f, 1f - elapsed / 230f)
                    : 0f;
            float pulse = isPlaying ? Math.min(1f, liveBassPowerPercent / 100f) : 0.1f;
            float r = rect.height() * 0.45f;
            ledButtonHelper.drawSingleLineButton(
                    canvas,
                    rect,
                    r,
                    density,
                    text,
                    6.3f * density,
                    color,
                    active,
                    true,
                    color,
                    pulse,
                    flash
            );
        }
    }

    private void drawUpperLeftEqAndSpectrum(Canvas canvas, RectF bay) {
        float padX = 7f * density;
        float topY = bay.top + 12.5f * density;
        float meterTop = bay.top + 17f * density;
        float meterBottom = bay.bottom - 13f * density;
        float meterH = Math.max(22f * density, meterBottom - meterTop);
        float availW = bay.width() - padX * 2f;

        // Background glowing 25-bar harmonic spectrum wall (green -> lime -> yellow -> orange)
        float specGap = 1.6f * density;
        float specBarW = Math.max(2f * density, (availW - specGap * (SPEC_BAR_COUNT - 1)) / SPEC_BAR_COUNT);
        for (int i = 0; i < SPEC_BAR_COUNT; i++) {
            float normX = (float) i / (SPEC_BAR_COUNT - 1);
            int barColor = greenToRedColor(normX * 0.85f);
            float x = bay.left + padX + i * (specBarW + specGap);
            float level = isPlaying ? smoothSpecBars[i] : (0.18f + 0.10f * (float) Math.sin(i * 0.45f));
            float barTop = meterBottom - level * meterH * 0.72f;
            specBarPaint.setColor(withAlpha(barColor, 0x62));
            tempRect.set(x, barTop, x + specBarW, meterBottom);
            canvas.drawRoundRect(tempRect, 1.4f * density, 1.4f * density, specBarPaint);
        }

        // 5 Foreground Segmented LED Fader Columns (BASS .. TREBLE)
        float colW = availW / 5f;
        float segGap = 1.4f * density;
        float segH = Math.max(2.0f * density, (meterH - segGap * (FADER_LED_ROWS - 1)) / FADER_LED_ROWS);
        float ledColW = Math.min(8.5f * density, colW * 0.34f);

        for (int i = 0; i < 5; i++) {
            float cx = bay.left + padX + colW * (i + 0.5f);
            faderHitRects[i].set(cx - colW * 0.48f, bay.top + 2f * density, cx + colW * 0.48f, bay.bottom - 2f * density);

            labelSmallPaint.setColor(0xFFE2E8F0);
            canvas.drawText(FADER_LABELS[i], cx, topY, labelSmallPaint);

            float norm = (faderDb[i] + 12f) / 24f; // 0..1
            int litSegments = Math.max(1, Math.round(norm * FADER_LED_ROWS));

            for (int r = 0; r < FADER_LED_ROWS; r++) {
                float segBot = meterBottom - r * (segH + segGap);
                float segTop = segBot - segH;
                float frac = (float) r / (FADER_LED_ROWS - 1);
                int segColor = greenToRedColor(frac);
                boolean lit = r < litSegments;
                ledSegmentPaint.setColor(lit ? segColor : 0x33334155);
                tempRect.set(cx - ledColW * 0.5f, segTop, cx + ledColW * 0.5f, segBot);
                canvas.drawRoundRect(tempRect, 1.2f * density, 1.2f * density, ledSegmentPaint);
            }

            // Luminous Fader Cap Bar at current position
            float capY = meterBottom - norm * meterH;
            capBodyPaint.setColor(0xFFF8FAFC);
            tempRect.set(cx - ledColW * 0.78f, capY - 1.6f * density, cx + ledColW * 0.78f, capY + 1.6f * density);
            canvas.drawRoundRect(tempRect, 1.4f * density, 1.4f * density, capBodyPaint);

            // Live dB label underneath
            int valColor = Math.abs(faderDb[i]) > 0.2f
                    ? (faderDb[i] > 0f ? 0xFF4ADE80 : 0xFF38BDF8)
                    : 0xFF94A3B8;
            valueTinyPaint.setColor(valColor);
            String dbStr = Math.abs(faderDb[i]) < 0.2f
                    ? "0dB"
                    : String.format(Locale.US, "%+.0fdB", faderDb[i]);
            canvas.drawText(dbStr, cx, bay.bottom - 3.5f * density, valueTinyPaint);
        }
    }

    private void drawUpperRightCompressor(Canvas canvas, RectF bay) {
        float cx = bay.centerX();
        sectionTitlePaint.setColor(0xFFF1F5F9);
        canvas.drawText("COMPRESSOR", cx - 10f * density, bay.top + 12f * density, sectionTitlePaint);

        // Top-right CLIP / PROTECT LED
        boolean hotClip = limiterEngaged || liveLimitingDb > 0.35f;
        float clipCx = bay.right - 16f * density;
        float clipCy = bay.top + 9.5f * density;
        if (hotClip) {
            ledHaloPaint.setColor(0x99FF2E4D);
            canvas.drawCircle(clipCx, clipCy, 6.5f * density, ledHaloPaint);
        }
        ledPaint.setColor(hotClip ? 0xFFFF3B5C : 0xFF7F1D1D);
        canvas.drawCircle(clipCx, clipCy, 2.7f * density, ledPaint);
        valueTinyPaint.setColor(hotClip ? 0xFFFF8FAB : 0xAAE11D48);
        canvas.drawText("CLIP", clipCx - 14f * density, clipCy + 2.2f * density, valueTinyPaint);

        // Dynamic Red/Orange/Magenta Vertical Compressor Multi-Bars
        float padX = 7f * density;
        float barAreaTop = bay.top + 17f * density;
        float barAreaBot = bay.bottom - 29f * density;
        float barAreaH = Math.max(16f * density, barAreaBot - barAreaTop);
        float availW = bay.width() - padX * 2f;
        float barGap = 2.0f * density;
        float barW = Math.max(2f * density, (availW - barGap * (COMP_BAR_COUNT - 1)) / COMP_BAR_COUNT);

        for (int i = 0; i < COMP_BAR_COUNT; i++) {
            float x = bay.left + padX + i * (barW + barGap);
            float t = (float) i / (COMP_BAR_COUNT - 1);
            int barColor = lerpColor(0xFFFB923C, 0xFFEC4899, t);
            float hFrac = isPlaying ? smoothCompBars[i] : (0.25f + 0.14f * (float) Math.sin(i * 0.55f));
            float top = barAreaBot - hFrac * barAreaH;
            compBarPaint.setColor(withAlpha(barColor, 0xEC));
            tempRect.set(x, top, x + barW, barAreaBot);
            canvas.drawRoundRect(tempRect, 1.5f * density, 1.5f * density, compBarPaint);
        }

        // Hit zones inside bay for Ratio (left half) & Threshold (right half) + Bottom micro pills (GATE | NR)
        float midX = bay.centerX();
        float bottomPillTop = bay.bottom - 17.5f * density;
        ratioHitRect.set(bay.left, bay.top + 14f * density, midX, bottomPillTop);
        threshHitRect.set(midX, bay.top + 14f * density, bay.right, bottomPillTop);

        // Clean Ratio / Thresh readout positioned cleanly between bars and toggle buttons
        valueTinyPaint.setColor(0xFFFDE68A);
        String compStats = liveCompReductionDb > 0.4f
                ? String.format(Locale.US, "%.0f:1 • %.0fdB (-%.1fdB)", compRatio, compThresholdDb, liveCompReductionDb)
                : String.format(Locale.US, "RATIO %.0f:1 • THR %.0fdB", compRatio, compThresholdDb);
        canvas.drawText(compStats, cx, barAreaBot + 8.5f * density, valueTinyPaint);

        // Bottom two compact toggles: GATE (Green) & DE-NOISE (Yellow)
        float pillW = (bay.width() - padX * 2f - 4f * density) * 0.5f;
        noiseGateHitRect.set(bay.left + padX, bottomPillTop, bay.left + padX + pillW, bay.bottom - 3f * density);
        noiseRedHitRect.set(noiseGateHitRect.right + 4f * density, bottomPillTop, bay.right - padX, bay.bottom - 3f * density);

        drawHeaderBadge(canvas, noiseGateHitRect, noiseGateOn ? "GATE ON" : "GATE OFF",
                StudioLedButtonHelper.STUDIO_GREEN, noiseGateOn, TARGET_SW_NOISE_GATE);
        drawHeaderBadge(canvas, noiseRedHitRect, noiseReductionOn ? "DE-NOISE" : "NR OFF",
                StudioLedButtonHelper.STUDIO_YELLOW, noiseReductionOn, TARGET_SW_NOISE_REDUCTION);
    }

    private void drawLowerControlDeck(Canvas canvas, RectF bay) {
        // Layout matching image:
        // Left (4 knobs): BASS+, CLARITY, WIDTH, LIMITER
        // Center: Vertical 2-column BASS POWER + CLIP LED Ladder (0..100%)
        // Right (2 knobs + Auto Gain): HPF, LPF, AUTO GAIN
        float padX = 5f * density;
        float ladderW = Math.min(36f * density, bay.width() * 0.125f);
        float leftZoneW = (bay.width() - padX * 2f - ladderW - 6f * density) * 0.58f;
        float rightZoneW = (bay.width() - padX * 2f - ladderW - 6f * density) * 0.42f;

        float leftStart = bay.left + padX;
        float ladderLeft = leftStart + leftZoneW + 3f * density;
        float rightStart = ladderLeft + ladderW + 3f * density;

        float cy = bay.top + bay.height() * 0.40f;
        float lColW = leftZoneW / 4f;
        float knobR = Math.min(11.5f * density, Math.min(lColW * 0.36f, bay.height() * 0.20f));

        // 4 Left Knobs
        float c0 = leftStart + lColW * 0.5f;
        float c1 = c0 + lColW;
        float c2 = c1 + lColW;
        float c3 = c2 + lColW;
        bassBoostHitRect.set(c0 - lColW * 0.5f, bay.top, c0 + lColW * 0.5f, bay.bottom);
        trebleClarityHitRect.set(c1 - lColW * 0.5f, bay.top, c1 + lColW * 0.5f, bay.bottom);
        stereoWidthHitRect.set(c2 - lColW * 0.5f, bay.top, c2 + lColW * 0.5f, bay.bottom);
        limiterHitRect.set(c3 - lColW * 0.5f, bay.top, c3 + lColW * 0.5f, bay.bottom);

        drawMiniKnob(canvas, c0, cy, knobR, bassBoostDb / 12f,
                "BASS +", String.format(Locale.US, "+%.1fdB", bassBoostDb), 0xFF38BDF8);
        drawMiniKnob(canvas, c1, cy, knobR, trebleClarityDb / 12f,
                "CLARITY", String.format(Locale.US, "+%.1fdB", trebleClarityDb), 0xFFD946EF);
        drawMiniKnob(canvas, c2, cy, knobR, stereoWidthPercent / 200f,
                "WIDTH", stereoWidthPercent + "%", 0xFF22D3EE);
        int limColor = limiterEngaged ? 0xFFFF2E4D : 0xFFFB7185;
        drawMiniKnob(canvas, c3, cy, knobR, (limiterCeilingDb + 12f) / 12f,
                "LIMITER", String.format(Locale.US, "%.1fdB", limiterCeilingDb), limColor);

        // Center Vertical 2-Column Live BASS POWER & CLIP LED Ladder
        bassLadderBayRect.set(ladderLeft, bay.top + 3f * density, ladderLeft + ladderW, bay.bottom - 3f * density);
        drawCenterBassPowerLadder(canvas, bassLadderBayRect);

        // Right Section: HPF Knob + LPF Knob + AUTO GAIN Pill Toggle below/above
        float rColW = rightZoneW / 2f;
        float c5 = rightStart + rColW * 0.5f;
        float c6 = c5 + rColW;
        float autoPillH = 14f * density;
        float knobTopEnd = bay.bottom - autoPillH - 4f * density;

        hpfHitRect.set(rightStart, bay.top, rightStart + rColW, knobTopEnd);
        lpfHitRect.set(rightStart + rColW, bay.top, rightStart + rightZoneW, knobTopEnd);
        autoGainHitRect.set(rightStart + 3f * density, knobTopEnd + 1f * density,
                rightStart + rightZoneW - 3f * density, bay.bottom - 3f * density);

        float rKnobCy = bay.top + (knobTopEnd - bay.top) * 0.36f;
        float rKnobR = Math.min(knobR, (knobTopEnd - bay.top) * 0.25f);
        drawMiniKnob(canvas, c5, rKnobCy, rKnobR, (hpfHz - 20f) / 230f,
                "HPF", hpfHz + "Hz", 0xFF67E8F9);
        drawMiniKnob(canvas, c6, rKnobCy, rKnobR, (lpfHz - 8000f) / 14000f,
                "LPF", String.format(Locale.US, "%.0fkHz", lpfHz / 1000f), 0xFFA78BFA);

        drawHeaderBadge(canvas, autoGainHitRect,
                autoGainOn ? "AUTO GAIN ON" : "AUTO GAIN OFF",
                StudioLedButtonHelper.STUDIO_GREEN, autoGainOn, TARGET_SW_AUTO_GAIN);
    }

    private void drawCenterBassPowerLadder(Canvas canvas, RectF rect) {
        boolean bassClip = limiterEngaged || liveBassPowerPercent >= 90;
        valueTinyPaint.setColor(bassClip ? 0xFFFF6B8B : 0xFFFDE68A);
        canvas.drawText(bassClip ? "CLIP" : "BASS", rect.centerX(), rect.top + 7.5f * density, valueTinyPaint);

        float ledTop = rect.top + 11f * density;
        float ledBot = rect.bottom - 10.5f * density;
        float ledH = Math.max(16f * density, ledBot - ledTop);
        float rowGap = 1.4f * density;
        float rowH = Math.max(1.8f * density, (ledH - rowGap * (LADDER_LED_ROWS - 1)) / LADDER_LED_ROWS);

        float colW = Math.min(9.5f * density, (rect.width() - 6f * density) * 0.5f);
        float colGap = 2.5f * density;
        float leftX = rect.centerX() - colGap * 0.5f - colW;
        float rightX = rect.centerX() + colGap * 0.5f;

        float normPower = isPlaying ? (liveBassPowerPercent / 100f) : 0f;
        int litRows = Math.round(normPower * LADDER_LED_ROWS);

        for (int r = 0; r < LADDER_LED_ROWS; r++) {
            float bot = ledBot - r * (rowH + rowGap);
            float top = bot - rowH;
            float frac = (float) r / (LADDER_LED_ROWS - 1);
            int color = greenToRedColor(frac);
            boolean lit = r < litRows;
            ledSegmentPaint.setColor(lit ? color : withAlpha(color, 0x26));

            tempRect.set(leftX, top, leftX + colW, bot);
            canvas.drawRoundRect(tempRect, 1.0f * density, 1.0f * density, ledSegmentPaint);
            tempRect.set(rightX, top, rightX + colW, bot);
            canvas.drawRoundRect(tempRect, 1.0f * density, 1.0f * density, ledSegmentPaint);
        }

        valueTinyPaint.setColor(normPower > 0.65f ? 0xFFFB923C : 0xFF67E8F9);
        canvas.drawText(liveBassPowerPercent + "%", rect.centerX(), rect.bottom - 1.5f * density, valueTinyPaint);
    }

    private void drawMiniKnob(
            Canvas canvas,
            float cx,
            float cy,
            float radius,
            float norm,
            String label,
            String valText,
            int accentColor) {
        float clamped = Math.max(0f, Math.min(1f, norm));
        int dots = 7;
        float dotRing = radius + 3.2f * density;
        for (int i = 0; i < dots; i++) {
            float f = (float) i / (dots - 1);
            float deg = -135f + f * 270f;
            double rad = Math.toRadians(deg - 90f);
            float dx = cx + (float) Math.cos(rad) * dotRing;
            float dy = cy + (float) Math.sin(rad) * dotRing;
            boolean active = f <= clamped + 0.02f;
            knobDotPaint.setColor(active ? accentColor : 0x4464748B);
            canvas.drawCircle(dx, dy, (active ? 1.3f : 0.95f) * density, knobDotPaint);
        }

        canvas.drawCircle(cx, cy + 1.2f * density, radius + 0.9f * density, knobShadowPaint);
        canvas.drawCircle(cx, cy, radius, knobMetalPaint);
        knobRimPaint.setColor(withAlpha(accentColor, 0xAA));
        canvas.drawCircle(cx, cy, radius, knobRimPaint);

        float deg = -135f + clamped * 270f;
        double rad = Math.toRadians(deg - 90f);
        canvas.drawLine(
                cx + (float) Math.cos(rad) * (radius * 0.25f),
                cy + (float) Math.sin(rad) * (radius * 0.25f),
                cx + (float) Math.cos(rad) * (radius * 0.84f),
                cy + (float) Math.sin(rad) * (radius * 0.84f),
                knobPointerPaint
        );

        labelSmallPaint.setColor(0xFFE2E8F0);
        canvas.drawText(label, cx, cy + radius + 9.5f * density, labelSmallPaint);
        valueTinyPaint.setColor(accentColor);
        canvas.drawText(valText, cx, cy + radius + 17.0f * density, valueTinyPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        float x = event.getX();
        float y = event.getY();

        if (action == MotionEvent.ACTION_DOWN) {
            activeTarget = hitTestTarget(x, y);
            if (activeTarget == TARGET_NONE) {
                return super.onTouchEvent(event);
            }
            if (getParent() != null) {
                getParent().requestDisallowInterceptTouchEvent(true);
            }
            lastTouchY = y;

            // Toggles respond immediately on tap with studio flash burst
            if (activeTarget == TARGET_SW_NOISE_GATE) {
                lastPressedToggle = TARGET_SW_NOISE_GATE;
                togglePressStartMs = SystemClock.uptimeMillis();
                noiseGateOn = !noiseGateOn;
                invalidate();
                if (listener != null) listener.onNoiseGateToggled(noiseGateOn);
                activeTarget = TARGET_NONE;
                return true;
            } else if (activeTarget == TARGET_SW_NOISE_REDUCTION) {
                lastPressedToggle = TARGET_SW_NOISE_REDUCTION;
                togglePressStartMs = SystemClock.uptimeMillis();
                noiseReductionOn = !noiseReductionOn;
                invalidate();
                if (listener != null) listener.onNoiseReductionToggled(noiseReductionOn);
                activeTarget = TARGET_NONE;
                return true;
            } else if (activeTarget == TARGET_SW_AUTO_GAIN) {
                lastPressedToggle = TARGET_SW_AUTO_GAIN;
                togglePressStartMs = SystemClock.uptimeMillis();
                autoGainOn = !autoGainOn;
                invalidate();
                if (listener != null) listener.onAutoGainToggled(autoGainOn);
                activeTarget = TARGET_NONE;
                return true;
            } else if (activeTarget == TARGET_SW_4K_HD) {
                lastPressedToggle = TARGET_SW_4K_HD;
                togglePressStartMs = SystemClock.uptimeMillis();
                ultraHd4kOn = !ultraHd4kOn;
                invalidate();
                if (listener != null) listener.onUltraHd4kToggled(ultraHd4kOn);
                activeTarget = TARGET_NONE;
                return true;
            } else if (activeTarget == TARGET_SW_ANTI_CRACKLE) {
                lastPressedToggle = TARGET_SW_ANTI_CRACKLE;
                togglePressStartMs = SystemClock.uptimeMillis();
                antiCrackleOn = !antiCrackleOn;
                invalidate();
                if (listener != null) listener.onAntiCrackleToggled(antiCrackleOn);
                activeTarget = TARGET_NONE;
                return true;
            }

            // Double-tap reset for knobs/faders
            long now = SystemClock.uptimeMillis();
            if (lastTapTarget == activeTarget && (now - lastTapMs) < 300L) {
                resetTargetToDefault(activeTarget);
                lastTapMs = 0L;
            } else {
                lastTapTarget = activeTarget;
                lastTapMs = now;
            }
            if (activeTarget >= TARGET_FADER_BASE && activeTarget < TARGET_FADER_BASE + 5) {
                updateFaderFromY(activeTarget - TARGET_FADER_BASE, y);
            }
            return true;
        } else if (action == MotionEvent.ACTION_MOVE && activeTarget != TARGET_NONE) {
            if (activeTarget >= TARGET_FADER_BASE && activeTarget < TARGET_FADER_BASE + 5) {
                updateFaderFromY(activeTarget - TARGET_FADER_BASE, y);
            } else {
                float dy = lastTouchY - y;
                lastTouchY = y;
                if (Math.abs(dy) >= 0.6f) {
                    applyDragDelta(activeTarget, dy);
                }
            }
            return true;
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            activeTarget = TARGET_NONE;
            if (getParent() != null) {
                getParent().requestDisallowInterceptTouchEvent(false);
            }
            return true;
        }
        return super.onTouchEvent(event);
    }

    private int hitTestTarget(float x, float y) {
        if (badgeAntiCrackleRect.contains(x, y)) return TARGET_SW_ANTI_CRACKLE;
        if (badge4kHdRect.contains(x, y)) return TARGET_SW_4K_HD;
        for (int i = 0; i < 5; i++) {
            if (faderHitRects[i].contains(x, y)) return TARGET_FADER_BASE + i;
        }
        if (noiseGateHitRect.contains(x, y)) return TARGET_SW_NOISE_GATE;
        if (noiseRedHitRect.contains(x, y)) return TARGET_SW_NOISE_REDUCTION;
        if (ratioHitRect.contains(x, y)) return TARGET_KNOB_RATIO;
        if (threshHitRect.contains(x, y)) return TARGET_KNOB_THRESH;
        if (bassBoostHitRect.contains(x, y)) return TARGET_KNOB_BASS_BOOST;
        if (trebleClarityHitRect.contains(x, y)) return TARGET_KNOB_TREBLE_CLARITY;
        if (stereoWidthHitRect.contains(x, y)) return TARGET_KNOB_STEREO_WIDTH;
        if (limiterHitRect.contains(x, y)) return TARGET_KNOB_LIMITER;
        if (autoGainHitRect.contains(x, y)) return TARGET_SW_AUTO_GAIN;
        if (hpfHitRect.contains(x, y)) return TARGET_KNOB_HPF;
        if (lpfHitRect.contains(x, y)) return TARGET_KNOB_LPF;
        return TARGET_NONE;
    }

    private void updateFaderFromY(int idx, float y) {
        float meterTop = faderBayRect.top + 17f * density;
        float meterBottom = faderBayRect.bottom - 13f * density;
        float norm = 1f - ((y - meterTop) / Math.max(1f, meterBottom - meterTop));
        norm = Math.max(0f, Math.min(1f, norm));
        float newDb = Math.round((-12f + norm * 24f) * 2f) / 2f;
        if (Math.abs(faderDb[idx] - newDb) > 0.05f) {
            faderDb[idx] = newDb;
            invalidate();
            if (listener != null) listener.onFaderChanged(idx, newDb);
        }
    }

    private void applyDragDelta(int target, float dy) {
        float normDelta = dy / (110f * density);
        switch (target) {
            case TARGET_KNOB_RATIO:
                compRatio = Math.max(1f, Math.min(8f, compRatio + normDelta * 7f));
                invalidate();
                if (listener != null) listener.onCompressorChanged(compRatio, compThresholdDb);
                break;
            case TARGET_KNOB_THRESH:
                compThresholdDb = Math.max(-30f, Math.min(0f, compThresholdDb + normDelta * 30f));
                invalidate();
                if (listener != null) listener.onCompressorChanged(compRatio, compThresholdDb);
                break;
            case TARGET_KNOB_BASS_BOOST:
                bassBoostDb = Math.max(0f, Math.min(12f, bassBoostDb + normDelta * 12f));
                invalidate();
                if (listener != null) listener.onPurificationBassChanged(bassBoostDb);
                break;
            case TARGET_KNOB_TREBLE_CLARITY:
                trebleClarityDb = Math.max(0f, Math.min(12f, trebleClarityDb + normDelta * 12f));
                invalidate();
                if (listener != null) listener.onTrebleClarityChanged(trebleClarityDb);
                break;
            case TARGET_KNOB_STEREO_WIDTH:
                stereoWidthPercent = Math.max(0, Math.min(200, stereoWidthPercent + Math.round(normDelta * 200f)));
                invalidate();
                if (listener != null) listener.onStereoWidthChanged(stereoWidthPercent);
                break;
            case TARGET_KNOB_LIMITER:
                limiterCeilingDb = Math.max(-12f, Math.min(0f, limiterCeilingDb + normDelta * 12f));
                invalidate();
                if (listener != null) listener.onLimiterChanged(limiterCeilingDb);
                break;
            case TARGET_KNOB_HPF:
                hpfHz = Math.max(20, Math.min(250, hpfHz + Math.round(normDelta * 230f)));
                invalidate();
                if (listener != null) listener.onHpfChanged(hpfHz);
                break;
            case TARGET_KNOB_LPF:
                lpfHz = Math.max(8000, Math.min(22000, lpfHz + Math.round(normDelta * 14000f)));
                invalidate();
                if (listener != null) listener.onLpfChanged(lpfHz);
                break;
            default:
                break;
        }
    }

    private void resetTargetToDefault(int target) {
        if (target >= TARGET_FADER_BASE && target < TARGET_FADER_BASE + 5) {
            int idx = target - TARGET_FADER_BASE;
            faderDb[idx] = 0f;
            invalidate();
            if (listener != null) listener.onFaderChanged(idx, 0f);
            return;
        }
        switch (target) {
            case TARGET_KNOB_RATIO:
                compRatio = 3.0f;
                invalidate();
                if (listener != null) listener.onCompressorChanged(compRatio, compThresholdDb);
                break;
            case TARGET_KNOB_THRESH:
                compThresholdDb = -12.0f;
                invalidate();
                if (listener != null) listener.onCompressorChanged(compRatio, compThresholdDb);
                break;
            case TARGET_KNOB_BASS_BOOST:
                bassBoostDb = 3.0f;
                invalidate();
                if (listener != null) listener.onPurificationBassChanged(bassBoostDb);
                break;
            case TARGET_KNOB_TREBLE_CLARITY:
                trebleClarityDb = 2.5f;
                invalidate();
                if (listener != null) listener.onTrebleClarityChanged(trebleClarityDb);
                break;
            case TARGET_KNOB_STEREO_WIDTH:
                stereoWidthPercent = 120;
                invalidate();
                if (listener != null) listener.onStereoWidthChanged(stereoWidthPercent);
                break;
            case TARGET_KNOB_LIMITER:
                limiterCeilingDb = -1.0f;
                invalidate();
                if (listener != null) listener.onLimiterChanged(limiterCeilingDb);
                break;
            case TARGET_KNOB_HPF:
                hpfHz = 80;
                invalidate();
                if (listener != null) listener.onHpfChanged(hpfHz);
                break;
            case TARGET_KNOB_LPF:
                lpfHz = 18000;
                invalidate();
                if (listener != null) listener.onLpfChanged(lpfHz);
                break;
            default:
                break;
        }
    }

    private static int greenToRedColor(float t) {
        if (t < 0.35f) {
            return lerpColor(0xFF22C55E, 0xFF84CC16, t / 0.35f);
        } else if (t < 0.65f) {
            return lerpColor(0xFF84CC16, 0xFFFACC15, (t - 0.35f) / 0.30f);
        } else if (t < 0.85f) {
            return lerpColor(0xFFFACC15, 0xFFFB923C, (t - 0.65f) / 0.20f);
        } else {
            return lerpColor(0xFFFB923C, 0xFFEF4444, (t - 0.85f) / 0.15f);
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
