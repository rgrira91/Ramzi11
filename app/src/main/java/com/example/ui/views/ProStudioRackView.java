package com.example.ui.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import androidx.annotation.Nullable;
import com.example.audio.AudioSpectrumTapProcessor;
import com.example.audio.NeonDspAudioProcessor;
import java.util.Locale;

/**
 * Hardware-styled Crystal Acrylic Pro Audio Mastering & Performance Rack (5 Tabs, Zero-Alloc Render):
 *  - 2-Row Header: DSP LIVE/BYPASS, SNAP A/B (tap switch / long-press save), Live BPM • Key badge,
 *      3D STAGE launch pill, and 5 Studio Tabs (SCOPE, 3D RTA, TUBE/MB, DJ/PADS, KAOSS/KAR)
 *  - Tab 0 (SCOPE): Circular XY Goniometer Vectorscope, Phase Correlation, LUFS & Crest, EQ/Filter Curve
 *  - Tab 1 (3D RTA): 3D Perspective Thermal Neon Waterfall Spectrogram + Dominant Musical Note/Hz + BPM/Key
 *  - Tab 2 (TUBE/MB): Dual Vacuum Tubes, 3-Band Multiband Comp w/ live GR meters, De-Esser & Schroeder Reverb
 *  - Tab 3 (DJ/PADS): DJ Filter, Tempo, Pitch, Vinyl Brake, A-B Loop, BPM Beat Stutter (1/4, 1/8, 1/16) & 4 FX Pads
 *  - Tab 4 (KAOSS/KAR): XY Neon Kaoss Touch Pad (Filter x Reverb), Vocal Remover (Bass Preserve),
 *      Harman Target Curve, Reference Match EQ (Capture/Match) & Harmonic Mix Next Track
 */
public class ProStudioRackView extends View {

    public interface RackListener {
        void onMasterBypassChanged(boolean bypass);
        void onSnapshotSelected(int slot);
        void onSnapshotSaveRequested(int slot);
        void onTubeEnabledChanged(boolean enabled);
        void onTubeDriveChanged(int percent);
        void onTapeWarmthChanged(int percent);
        void onCrossfeedChanged(boolean enabled);
        void onReverbEnabledChanged(boolean enabled);
        void onReverbParamsChanged(int roomPercent, int dampPercent, int mixPercent);
        void onDjFilterChanged(int filterPercent);
        void onTempoChanged(float tempo);
        void onPitchChanged(float pitch);
        void onVinylBrakeToggled(boolean enabled);
        void onLoopSetARequested();
        void onLoopSetBOrClearRequested();
        void onPerformancePadChanged(int padMode);

        // New Pro Studio Features
        void onMultibandEnabledChanged(boolean enabled);
        void onMultibandDriveChanged(int percent);
        void onDeEsserChanged(int percent);
        void onVocalRemoverChanged(int percent);
        void onHarmanTargetChanged(boolean enabled);
        void onCaptureRefProfileRequested();
        void onRefMatchToggled(boolean enabled);
        void onStutterDivChanged(int stutterDiv);
        void onKaossXyChanged(int filterPercent, int reverbMixPercent, boolean reverbActive);
        void onHarmonicNextTrackRequested();
        void onOpenStage3dRequested();
    }

    public static final int TAB_SCOPE = 0;
    public static final int TAB_WATERFALL = 1;
    public static final int TAB_TUBE_REVERB = 2;
    public static final int TAB_DJ_PADS = 3;
    public static final int TAB_KAOSS_KAR = 4;

    private static final int MAX_WATERFALL_ROWS = 30;
    private static final int WATERFALL_COLS = 32;
    private static final int KAOSS_TRAIL_LEN = 14;

    private static final int TARGET_NONE = -1;
    private static final int TARGET_BYPASS = 1;
    private static final int TARGET_SNAP_A = 2;
    private static final int TARGET_SNAP_B = 3;
    private static final int TARGET_STAGE_3D = 4;
    private static final int TARGET_TAB_0 = 10;
    private static final int TARGET_TAB_1 = 11;
    private static final int TARGET_TAB_2 = 12;
    private static final int TARGET_TAB_3 = 13;
    private static final int TARGET_TAB_4 = 14;

    // Tab 2 (TUBE / MB / REV)
    private static final int TARGET_SW_TUBE = 20;
    private static final int TARGET_KNOB_DRIVE = 21;
    private static final int TARGET_KNOB_WARMTH = 22;
    private static final int TARGET_SW_CROSSFEED = 23;
    private static final int TARGET_SW_MULTIBAND = 24;
    private static final int TARGET_KNOB_MB_DRIVE = 25;
    private static final int TARGET_KNOB_DE_ESS = 26;
    private static final int TARGET_SW_REVERB = 27;
    private static final int TARGET_KNOB_ROOM = 28;
    private static final int TARGET_KNOB_DAMP = 29;
    private static final int TARGET_KNOB_MIX = 30;

    // Tab 3 (DJ / STUTTER / PADS)
    private static final int TARGET_KNOB_DJ_FILTER = 40;
    private static final int TARGET_KNOB_TEMPO = 41;
    private static final int TARGET_KNOB_PITCH = 42;
    private static final int TARGET_SW_VINYL_BRAKE = 43;
    private static final int TARGET_LOOP_A = 44;
    private static final int TARGET_LOOP_B = 45;
    private static final int TARGET_STUTTER_0 = 46;
    private static final int TARGET_STUTTER_1 = 47;
    private static final int TARGET_STUTTER_2 = 48;
    private static final int TARGET_STUTTER_3 = 49;
    private static final int TARGET_PAD_1 = 50;
    private static final int TARGET_PAD_2 = 51;
    private static final int TARGET_PAD_3 = 52;
    private static final int TARGET_PAD_4 = 53;

    // Tab 4 (KAOSS / KARAOKE / HARMAN / MATCH)
    private static final int TARGET_KAOSS_PAD = 60;
    private static final int TARGET_KNOB_VOCAL_REM = 61;
    private static final int TARGET_SW_HARMAN = 62;
    private static final int TARGET_BTN_CAPTURE_REF = 63;
    private static final int TARGET_SW_MATCH_EQ = 64;
    private static final int TARGET_BTN_HARMONIC_NEXT = 65;

    private final Paint outerGlassPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint outerRimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint subPanelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint subPanelBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pillBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pillBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pillTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelSmallPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint valueTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ledPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ledHaloPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Paint scopeGridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint scopeTracePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint scopeGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint curveStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint curveFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Paint tubeGlassPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tubeBasePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tubeFilamentPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Paint knobShadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint knobMetalPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint knobRimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint knobPointerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint knobDotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint waterfallPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint meterFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint kaossTrailPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    // Pre-allocated shaders & matrices (0 per-frame GC allocations)
    private RadialGradient unitKnobChromeShader;
    private RadialGradient unitTubeGlowShader;
    private final Matrix shaderMatrix = new Matrix();

    private final RectF outerRect = new RectF();
    private final RectF headerRow1Rect = new RectF();
    private final RectF headerRow2Rect = new RectF();
    private final RectF contentRect = new RectF();
    private final RectF footerRect = new RectF();
    private final RectF tempRect = new RectF();

    private final RectF hitBypass = new RectF();
    private final RectF hitSnapA = new RectF();
    private final RectF hitSnapB = new RectF();
    private final RectF hitBpmKeyBadge = new RectF();
    private final RectF hitStage3d = new RectF();
    private final RectF[] hitTabs = {
            new RectF(), new RectF(), new RectF(), new RectF(), new RectF()
    };

    // Tab 2 hit rects (Tube/Tape | Multiband/De-Ess | Reverb)
    private final RectF hitSwTube = new RectF();
    private final RectF hitKnobDrive = new RectF();
    private final RectF hitKnobWarmth = new RectF();
    private final RectF hitSwCrossfeed = new RectF();
    private final RectF hitSwMultiband = new RectF();
    private final RectF hitKnobMbDrive = new RectF();
    private final RectF hitKnobDeEsser = new RectF();
    private final RectF hitSwReverb = new RectF();
    private final RectF hitKnobRoom = new RectF();
    private final RectF hitKnobDamp = new RectF();
    private final RectF hitKnobMix = new RectF();

    // Tab 3 hit rects (DJ Filter/Tempo/Pitch, Stutter, 4 FX Pads)
    private final RectF hitKnobDjFilter = new RectF();
    private final RectF hitKnobTempo = new RectF();
    private final RectF hitKnobPitch = new RectF();
    private final RectF hitSwVinylBrake = new RectF();
    private final RectF hitLoopA = new RectF();
    private final RectF hitLoopB = new RectF();
    private final RectF[] hitStutter = {new RectF(), new RectF(), new RectF(), new RectF()};
    private final RectF[] hitPads = {new RectF(), new RectF(), new RectF(), new RectF()};

    // Tab 4 hit rects (Kaoss XY Pad + Vocal Rem + Harman + Ref Match + Harmonic Next)
    private final RectF hitKaossPad = new RectF();
    private final RectF hitKnobVocalRem = new RectF();
    private final RectF hitSwHarman = new RectF();
    private final RectF hitBtnCaptureRef = new RectF();
    private final RectF hitSwMatchEq = new RectF();
    private final RectF hitBtnHarmonicNext = new RectF();

    private final Path scopePath = new Path();
    private final Path curvePath = new Path();
    private final Path curveFillPath = new Path();
    private final Path waterfallPath = new Path();

    // Pre-allocated 3D Waterfall history matrix & peak hold
    private final float[][] waterfallHistory = new float[MAX_WATERFALL_ROWS][WATERFALL_COLS];
    private final float[] waterfallPeakHold = new float[WATERFALL_COLS];
    private int waterfallWriteRow = 0;
    private int activeWaterfallRows = 22;

    // Pre-allocated Kaoss touch trail (normalized 0..1)
    private final float[] kaossTrailX = new float[KAOSS_TRAIL_LEN];
    private final float[] kaossTrailY = new float[KAOSS_TRAIL_LEN];
    private int kaossTrailCount = 0;

    // Live Telemetry
    private final float[] xyLeft = new float[AudioSpectrumTapProcessor.XY_SAMPLE_COUNT];
    private final float[] xyRight = new float[AudioSpectrumTapProcessor.XY_SAMPLE_COUNT];
    private final float[] responseCurveDb = new float[48];
    private float phaseCorrelation = 1.0f;
    private float lufsShortTerm = -48.0f;
    private float crestFactorDb = 0.0f;
    private float liveTubeGlow = 0.0f;
    private float beatEnergy = 0.0f;
    private float liveBpm = 120.0f;
    private String detectedKeyLabel = "--";
    private String detectedCamelot = "--";
    private float dominantFreqHz = 0.0f;
    private String dominantNoteName = "--";
    private float mbLowGrDb = 0f;
    private float mbMidGrDb = 0f;
    private float mbHighGrDb = 0f;

    // Control State
    private int activeTab = TAB_SCOPE;
    private boolean masterBypass = false;
    private int activeSnapshot = 0;
    private boolean tubeEnabled = false;
    private int tubeDrivePercent = 35;
    private int tapeWarmthPercent = 25;
    private boolean crossfeedEnabled = false;
    private boolean reverbEnabled = false;
    private int reverbRoomPercent = 42;
    private int reverbDampPercent = 35;
    private int reverbMixPercent = 24;
    private int djFilterPercent = 0;
    private float tempoValue = 1.0f;
    private float pitchValue = 1.0f;
    private boolean vinylBrakeEnabled = true;
    private long abLoopStartMs = -1L;
    private long abLoopEndMs = -1L;
    private int activePadMode = NeonDspAudioProcessor.PAD_NONE;

    // New Studio Controls State
    private boolean multibandEnabled = false;
    private int multibandDrivePercent = 45;
    private int deEsserPercent = 0;
    private int vocalRemoverPercent = 0;
    private boolean harmanTargetEnabled = false;
    private boolean hasRefProfile = false;
    private boolean refMatchEnabled = false;
    private int stutterDiv = 0;

    private int accentLow = 0xFF22D3EE;
    private int accentMid = 0xFFA855F7;
    private int accentHigh = 0xFFF43F9E;

    private float density = 1f;
    private long targetFrameIntervalMs = 33L;
    private long lastTelemetryInvalidateMs = 0L;
    private int activeTarget = TARGET_NONE;
    private float lastTouchY = 0f;
    private long lastTapMs = 0L;
    private int lastTapTarget = TARGET_NONE;
    private boolean longPressTriggered = false;
    private int pressedLedTarget = TARGET_NONE;
    private long pressedLedStartMs = 0L;
    private StudioLedButtonHelper ledButtonHelper;
    private final Handler touchHandler = new Handler(Looper.getMainLooper());
    private final Runnable snapLongPressRunnable = new Runnable() {
        @Override
        public void run() {
            if (activeTarget == TARGET_SNAP_A || activeTarget == TARGET_SNAP_B) {
                longPressTriggered = true;
                int slot = activeTarget == TARGET_SNAP_A ? 0 : 1;
                performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                if (listener != null) {
                    listener.onSnapshotSaveRequested(slot);
                }
                invalidate();
            }
        }
    };

    private RackListener listener;

    public ProStudioRackView(Context context) {
        super(context);
        init(context);
    }

    public ProStudioRackView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public ProStudioRackView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        density = context.getResources().getDisplayMetrics().density;
        ledButtonHelper = new StudioLedButtonHelper(density);

        outerGlassPaint.setStyle(Paint.Style.FILL);
        outerRimPaint.setStyle(Paint.Style.STROKE);
        outerRimPaint.setStrokeWidth(1.4f * density);

        subPanelPaint.setStyle(Paint.Style.FILL);
        subPanelPaint.setColor(0xFF0A0D1C);

        subPanelBorderPaint.setStyle(Paint.Style.STROKE);
        subPanelBorderPaint.setStrokeWidth(1f * density);
        subPanelBorderPaint.setColor(0x36BAE6FD);

        pillBgPaint.setStyle(Paint.Style.FILL);
        pillBorderPaint.setStyle(Paint.Style.STROKE);
        pillBorderPaint.setStrokeWidth(1.1f * density);

        pillTextPaint.setTextAlign(Paint.Align.CENTER);
        pillTextPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        pillTextPaint.setTextSize(8.5f * density);

        labelSmallPaint.setTextAlign(Paint.Align.CENTER);
        labelSmallPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        labelSmallPaint.setTextSize(7.8f * density);
        labelSmallPaint.setColor(0xFFCBD5E1);

        valueTextPaint.setTextAlign(Paint.Align.CENTER);
        valueTextPaint.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        valueTextPaint.setTextSize(9.2f * density);
        valueTextPaint.setColor(0xFFFFFFFF);

        ledPaint.setStyle(Paint.Style.FILL);
        ledHaloPaint.setStyle(Paint.Style.FILL);

        scopeGridPaint.setStyle(Paint.Style.STROKE);
        scopeGridPaint.setStrokeWidth(0.9f * density);
        scopeGridPaint.setColor(0x3364748B);

        scopeGlowPaint.setStyle(Paint.Style.STROKE);
        scopeGlowPaint.setStrokeCap(Paint.Cap.ROUND);
        scopeGlowPaint.setStrokeJoin(Paint.Join.ROUND);
        scopeGlowPaint.setStrokeWidth(3.4f * density);

        scopeTracePaint.setStyle(Paint.Style.STROKE);
        scopeTracePaint.setStrokeCap(Paint.Cap.ROUND);
        scopeTracePaint.setStrokeJoin(Paint.Join.ROUND);
        scopeTracePaint.setStrokeWidth(1.6f * density);

        curveStrokePaint.setStyle(Paint.Style.STROKE);
        curveStrokePaint.setStrokeCap(Paint.Cap.ROUND);
        curveStrokePaint.setStrokeWidth(1.8f * density);
        curveFillPaint.setStyle(Paint.Style.FILL);

        tubeGlassPaint.setStyle(Paint.Style.FILL);
        tubeBasePaint.setStyle(Paint.Style.FILL);
        tubeBasePaint.setColor(0xFF475569);
        tubeFilamentPaint.setStyle(Paint.Style.FILL);

        knobShadowPaint.setStyle(Paint.Style.FILL);
        knobShadowPaint.setColor(0x66000000);
        knobMetalPaint.setStyle(Paint.Style.FILL);
        knobRimPaint.setStyle(Paint.Style.STROKE);
        knobRimPaint.setStrokeWidth(1.1f * density);
        knobRimPaint.setColor(0x99E2E8F0);
        knobPointerPaint.setStyle(Paint.Style.STROKE);
        knobPointerPaint.setStrokeCap(Paint.Cap.ROUND);
        knobPointerPaint.setStrokeWidth(2.1f * density);
        knobPointerPaint.setColor(0xFFFFFFFF);
        knobDotPaint.setStyle(Paint.Style.FILL);

        waterfallPaint.setStyle(Paint.Style.FILL);
        meterFillPaint.setStyle(Paint.Style.FILL);
        kaossTrailPaint.setStyle(Paint.Style.STROKE);
        kaossTrailPaint.setStrokeCap(Paint.Cap.ROUND);

        unitKnobChromeShader = new RadialGradient(
                -0.3f, -0.3f, 1.4f,
                new int[]{0xFF64748B, 0xFF334155, 0xFF0F172A},
                new float[]{0f, 0.55f, 1f},
                Shader.TileMode.CLAMP
        );
        unitTubeGlowShader = new RadialGradient(
                0f, 0f, 1f,
                0xFFFFA532, 0x00FF7A00,
                Shader.TileMode.CLAMP
        );
    }

    public void setRackListener(RackListener listener) {
        this.listener = listener;
    }

    public void setPaletteColors(int secondaryNeon, int tertiaryGlow, int primaryNeon) {
        this.accentLow = secondaryNeon;
        this.accentMid = tertiaryGlow;
        this.accentHigh = primaryNeon;
        invalidate();
    }

    public void setTargetFps(int fps) {
        int clamped = Math.max(15, Math.min(60, fps));
        this.targetFrameIntervalMs = Math.max(16L, Math.round(1000.0 / clamped));
    }

    public void setWaterfallHistoryRows(int rows) {
        this.activeWaterfallRows = Math.max(10, Math.min(MAX_WATERFALL_ROWS, rows));
    }

    public int getActiveTab() {
        return activeTab;
    }

    public void setActiveTab(int tab) {
        int clamped = Math.max(TAB_SCOPE, Math.min(TAB_KAOSS_KAR, tab));
        if (this.activeTab != clamped) {
            this.activeTab = clamped;
            invalidate();
        }
    }

    public void syncState(
            boolean masterBypass,
            int activeSnapshot,
            boolean tubeEnabled,
            int tubeDrivePercent,
            int tapeWarmthPercent,
            boolean crossfeedEnabled,
            boolean reverbEnabled,
            int reverbRoomPercent,
            int reverbDampPercent,
            int reverbMixPercent,
            int djFilterPercent,
            float tempoValue,
            float pitchValue,
            boolean vinylBrakeEnabled,
            long abLoopStartMs,
            long abLoopEndMs,
            int activePadMode) {
        this.masterBypass = masterBypass;
        this.activeSnapshot = activeSnapshot == 1 ? 1 : 0;
        this.tubeEnabled = tubeEnabled;
        this.tubeDrivePercent = tubeDrivePercent;
        this.tapeWarmthPercent = tapeWarmthPercent;
        this.crossfeedEnabled = crossfeedEnabled;
        this.reverbEnabled = reverbEnabled;
        this.reverbRoomPercent = reverbRoomPercent;
        this.reverbDampPercent = reverbDampPercent;
        this.reverbMixPercent = reverbMixPercent;
        this.djFilterPercent = djFilterPercent;
        this.tempoValue = tempoValue;
        this.pitchValue = pitchValue;
        this.vinylBrakeEnabled = vinylBrakeEnabled;
        this.abLoopStartMs = abLoopStartMs;
        this.abLoopEndMs = abLoopEndMs;
        this.activePadMode = activePadMode;
        invalidate();
    }

    public void syncAdvancedState(
            boolean multibandEnabled,
            int multibandDrivePercent,
            int deEsserPercent,
            int vocalRemoverPercent,
            boolean harmanTargetEnabled,
            boolean hasRefProfile,
            boolean refMatchEnabled,
            int stutterDiv) {
        this.multibandEnabled = multibandEnabled;
        this.multibandDrivePercent = multibandDrivePercent;
        this.deEsserPercent = deEsserPercent;
        this.vocalRemoverPercent = vocalRemoverPercent;
        this.harmanTargetEnabled = harmanTargetEnabled;
        this.hasRefProfile = hasRefProfile;
        this.refMatchEnabled = refMatchEnabled;
        this.stutterDiv = stutterDiv;
        invalidate();
    }

    public void updateLiveTelemetry(
            float[] xyL,
            float[] xyR,
            float phaseCorr,
            float lufs,
            float crestDb,
            float tubeGlow,
            float beat,
            float[] responseDb) {
        updateLiveTelemetry(
                xyL, xyR, phaseCorr, lufs, crestDb, tubeGlow, beat, responseDb,
                null, liveBpm, detectedKeyLabel, detectedCamelot,
                dominantFreqHz, dominantNoteName, mbLowGrDb, mbMidGrDb, mbHighGrDb);
    }

    public void updateLiveTelemetry(
            float[] xyL,
            float[] xyR,
            float phaseCorr,
            float lufs,
            float crestDb,
            float tubeGlow,
            float beat,
            float[] responseDb,
            @Nullable float[] spectrumBars,
            float bpm,
            @Nullable String keyLabel,
            @Nullable String camelot,
            float domFreqHz,
            @Nullable String domNoteName,
            float lowGrDb,
            float midGrDb,
            float highGrDb) {
        if (xyL != null && xyR != null) {
            int len = Math.min(this.xyLeft.length, Math.min(xyL.length, xyR.length));
            System.arraycopy(xyL, 0, this.xyLeft, 0, len);
            System.arraycopy(xyR, 0, this.xyRight, 0, len);
        }
        if (responseDb != null && responseDb.length > 0) {
            int len = Math.min(this.responseCurveDb.length, responseDb.length);
            for (int i = 0; i < this.responseCurveDb.length; i++) {
                float src = (float) i / Math.max(1, this.responseCurveDb.length - 1) * (len - 1);
                int idx0 = Math.min(len - 1, (int) Math.floor(src));
                int idx1 = Math.min(len - 1, idx0 + 1);
                float frac = src - idx0;
                this.responseCurveDb[i] = responseDb[idx0] * (1f - frac) + responseDb[idx1] * frac;
            }
        }
        if (spectrumBars != null && spectrumBars.length > 0) {
            waterfallWriteRow = (waterfallWriteRow + 1) % MAX_WATERFALL_ROWS;
            float[] row = waterfallHistory[waterfallWriteRow];
            for (int c = 0; c < WATERFALL_COLS; c++) {
                int srcIdx = Math.min(spectrumBars.length - 1, (c * spectrumBars.length) / WATERFALL_COLS);
                float v = Math.max(0f, Math.min(1f, spectrumBars[srcIdx]));
                row[c] = v;
                if (v >= waterfallPeakHold[c]) {
                    waterfallPeakHold[c] = v;
                } else {
                    waterfallPeakHold[c] = Math.max(0f, waterfallPeakHold[c] - 0.018f);
                }
            }
        }
        this.phaseCorrelation = phaseCorr;
        this.lufsShortTerm = lufs;
        this.crestFactorDb = crestDb;
        this.liveTubeGlow = tubeGlow;
        this.beatEnergy = beat;
        this.liveBpm = bpm;
        if (keyLabel != null) this.detectedKeyLabel = keyLabel;
        if (camelot != null) this.detectedCamelot = camelot;
        this.dominantFreqHz = domFreqHz;
        if (domNoteName != null) this.dominantNoteName = domNoteName;
        this.mbLowGrDb = lowGrDb;
        this.mbMidGrDb = midGrDb;
        this.mbHighGrDb = highGrDb;

        long now = SystemClock.uptimeMillis();
        if (now - lastTelemetryInvalidateMs >= targetFrameIntervalMs) {
            lastTelemetryInvalidateMs = now;
            invalidate();
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w <= 0 || h <= 0) return;

        float pad = 4f * density;
        outerRect.set(pad, pad, w - pad, h - pad);

        outerGlassPaint.setShader(new LinearGradient(
                outerRect.left, outerRect.top, outerRect.right, outerRect.bottom,
                new int[]{0xFF1A1E3B, 0xFF0E1228, 0xFF141830},
                new float[]{0f, 0.52f, 1f},
                Shader.TileMode.CLAMP
        ));
        outerRimPaint.setShader(new LinearGradient(
                outerRect.left, outerRect.top, outerRect.right, outerRect.bottom,
                new int[]{0xAABAE6FD, 0x77F472B6, 0xAA67E8F9},
                new float[]{0f, 0.5f, 1f},
                Shader.TileMode.CLAMP
        ));

        float inPad = 9f * density;
        float row1H = 22f * density;
        float row2H = 22f * density;
        float footH = 16f * density;
        headerRow1Rect.set(outerRect.left + inPad, outerRect.top + 7f * density,
                outerRect.right - inPad, outerRect.top + 7f * density + row1H);
        headerRow2Rect.set(outerRect.left + inPad, headerRow1Rect.bottom + 4f * density,
                outerRect.right - inPad, headerRow1Rect.bottom + 4f * density + row2H);
        footerRect.set(outerRect.left + inPad, outerRect.bottom - 5f * density - footH,
                outerRect.right - inPad, outerRect.bottom - 5f * density);
        contentRect.set(outerRect.left + inPad, headerRow2Rect.bottom + 6f * density,
                outerRect.right - inPad, footerRect.top - 5f * density);

        layoutHeaderPills();
        layoutTabHitRects();
    }

    private void layoutHeaderPills() {
        float w1 = headerRow1Rect.width();
        float y1a = headerRow1Rect.top;
        float y1b = headerRow1Rect.bottom;
        float gap = 4.5f * density;

        float bypassW = w1 * 0.22f;
        float snapW = w1 * 0.145f;
        float stageW = w1 * 0.21f;
        float x = headerRow1Rect.left;
        hitBypass.set(x, y1a, x + bypassW, y1b);
        x += bypassW + gap;
        hitSnapA.set(x, y1a, x + snapW, y1b);
        x += snapW + gap * 0.7f;
        hitSnapB.set(x, y1a, x + snapW, y1b);
        x += snapW + gap;
        float bpmRight = headerRow1Rect.right - stageW - gap;
        hitBpmKeyBadge.set(x, y1a, bpmRight, y1b);
        hitStage3d.set(bpmRight + gap, y1a, headerRow1Rect.right, y1b);

        float w2 = headerRow2Rect.width();
        float y2a = headerRow2Rect.top;
        float y2b = headerRow2Rect.bottom;
        float tabGap = 4f * density;
        float tabW = (w2 - tabGap * 4f) / 5f;
        for (int i = 0; i < 5; i++) {
            float tx = headerRow2Rect.left + i * (tabW + tabGap);
            hitTabs[i].set(tx, y2a, tx + tabW, y2b);
        }
    }

    private void layoutTabHitRects() {
        float cl = contentRect.left;
        float ct = contentRect.top;
        float cw = contentRect.width();
        float ch = contentRect.height();

        // Tab 2 (TUBE/MB/REV): 3 Bays -> Tube (35%) | Multiband+DeEss (35%) | Reverb (27%)
        float gap = 5f * density;
        float tubeR = cl + cw * 0.35f;
        float mbL = tubeR + gap;
        float mbR = mbL + cw * 0.35f;
        float revL = mbR + gap;
        float revR = contentRect.right;

        float swH = 22f * density;
        float swY1 = contentRect.bottom - swH - 4f * density;
        float tubeW = tubeR - cl;
        hitSwTube.set(cl + 4f * density, swY1, cl + tubeW * 0.48f, swY1 + swH);
        hitSwCrossfeed.set(cl + tubeW * 0.52f, swY1, tubeR - 4f * density, swY1 + swH);
        float tubeKnobsTop = ct + ch * 0.38f;
        hitKnobDrive.set(cl + 2f * density, tubeKnobsTop, cl + tubeW * 0.5f, swY1 - 2f * density);
        hitKnobWarmth.set(cl + tubeW * 0.5f, tubeKnobsTop, tubeR - 2f * density, swY1 - 2f * density);

        float mbW = mbR - mbL;
        hitSwMultiband.set(mbL + 5f * density, ct + 4f * density, mbR - 5f * density, ct + 25f * density);
        float mbKnobsTop = ct + 27f * density;
        float mbKnobsBot = contentRect.bottom - 34f * density;
        hitKnobMbDrive.set(mbL + 2f * density, mbKnobsTop, mbL + mbW * 0.5f, mbKnobsBot);
        hitKnobDeEsser.set(mbL + mbW * 0.5f, mbKnobsTop, mbR - 2f * density, mbKnobsBot);

        hitSwReverb.set(revL + 5f * density, ct + 4f * density, revR - 5f * density, ct + 25f * density);
        float revW = revR - revL;
        float revRow1Top = ct + 27f * density;
        float revRowMid = ct + 27f * density + (contentRect.bottom - (ct + 27f * density)) * 0.50f;
        hitKnobRoom.set(revL + 2f * density, revRow1Top, revL + revW * 0.5f, revRowMid);
        hitKnobDamp.set(revL + revW * 0.5f, revRow1Top, revR - 2f * density, revRowMid);
        hitKnobMix.set(revL + revW * 0.16f, revRowMid, revR - revW * 0.16f, contentRect.bottom - 2f * density);

        // Tab 3 (DJ/PADS): Top 54% Knobs+Loop, Mid 19% Stutter Row, Bottom 23% Pads
        float djTopBot = ct + ch * 0.54f;
        float knobAreaW = cw * 0.65f;
        float djKnobW = knobAreaW / 3f;
        hitKnobDjFilter.set(cl, ct, cl + djKnobW, djTopBot);
        hitKnobTempo.set(cl + djKnobW, ct, cl + djKnobW * 2f, djTopBot);
        hitKnobPitch.set(cl + djKnobW * 2f, ct, cl + knobAreaW, djTopBot);

        float rightColL = cl + knobAreaW + 5f * density;
        float rightMidY = ct + (djTopBot - ct) * 0.48f;
        hitSwVinylBrake.set(rightColL, ct + 3f * density, contentRect.right, rightMidY - 3f * density);
        float loopW = (contentRect.right - rightColL - 4f * density) * 0.5f;
        hitLoopA.set(rightColL, rightMidY + 2f * density, rightColL + loopW, djTopBot - 3f * density);
        hitLoopB.set(rightColL + loopW + 4f * density, rightMidY + 2f * density, contentRect.right, djTopBot - 3f * density);

        float stutTop = djTopBot + 4f * density;
        float stutBot = stutTop + Math.max(22f * density, ch * 0.17f);
        float stutLabelW = cw * 0.24f;
        float stutBtnW = (cw - stutLabelW - 4f * density * 4f) / 4f;
        for (int i = 0; i < 4; i++) {
            float sx = cl + stutLabelW + i * (stutBtnW + 4f * density);
            hitStutter[i].set(sx, stutTop, sx + stutBtnW, stutBot);
        }

        float padTop = stutBot + 5f * density;
        float padBot = contentRect.bottom;
        float padGap = 6f * density;
        float padW = (cw - padGap * 3f) / 4f;
        for (int i = 0; i < 4; i++) {
            float px = cl + i * (padW + padGap);
            hitPads[i].set(px, padTop, px + padW, padBot);
        }

        // Tab 4 (KAOSS/KARAOKE): Left 46% XY Kaoss Pad, Right 51% Vocal Rem + Calibration & Harmonic Mix
        float kaossR = cl + cw * 0.46f;
        hitKaossPad.set(cl, ct, kaossR, contentRect.bottom);

        float karL = kaossR + 7f * density;
        float karW = contentRect.right - karL;
        float topSplitBot = ct + ch * 0.68f;
        float knobColR = karL + karW * 0.45f;
        hitKnobVocalRem.set(karL + 2f * density, ct + 4f * density, knobColR, topSplitBot - 4f * density);

        float btnColL = knobColR + 5f * density;
        float btnH = (topSplitBot - ct - 14f * density) / 3f;
        hitSwHarman.set(btnColL, ct + 4f * density, contentRect.right - 4f * density, ct + 4f * density + btnH);
        hitBtnCaptureRef.set(btnColL, hitSwHarman.bottom + 4f * density, contentRect.right - 4f * density, hitSwHarman.bottom + 4f * density + btnH);
        hitSwMatchEq.set(btnColL, hitBtnCaptureRef.bottom + 4f * density, contentRect.right - 4f * density, topSplitBot - 4f * density);

        hitBtnHarmonicNext.set(karL + 4f * density, topSplitBot + 4f * density,
                contentRect.right - 4f * density, contentRect.bottom - 4f * density);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (getWidth() <= 0 || getHeight() <= 0) return;

        float radius = 16f * density;
        canvas.drawRoundRect(outerRect, radius, radius, outerGlassPaint);
        canvas.drawRoundRect(outerRect, radius, radius, outerRimPaint);

        drawHeaderBar(canvas);

        switch (activeTab) {
            case TAB_WATERFALL:
                drawWaterfallTab(canvas);
                break;
            case TAB_TUBE_REVERB:
                drawTubeMultibandTab(canvas);
                break;
            case TAB_DJ_PADS:
                drawDjPadsTab(canvas);
                break;
            case TAB_KAOSS_KAR:
                drawKaossKaraokeTab(canvas);
                break;
            case TAB_SCOPE:
            default:
                drawScopeTab(canvas);
                break;
        }

        drawFooterStrip(canvas);

        if (pressedLedTarget != TARGET_NONE && (SystemClock.uptimeMillis() - pressedLedStartMs) < 230L && isShown()) {
            postInvalidateOnAnimation();
        }
    }

    private float getPressFlashFor(int targetId) {
        if (pressedLedTarget != targetId) return 0f;
        long elapsed = SystemClock.uptimeMillis() - pressedLedStartMs;
        return elapsed < 230L ? Math.max(0f, 1f - elapsed / 230f) : 0f;
    }

    private void drawHeaderBar(Canvas canvas) {
        // Row 1: BYPASS (Green=DSP LIVE, Red=BYPASS) | SNAP A (Cyan) | SNAP B (Yellow) | BPM • KEY | 3D STAGE (Magenta)
        int bypassCol = masterBypass ? StudioLedButtonHelper.STUDIO_RED : StudioLedButtonHelper.STUDIO_GREEN;
        drawStudioPill(canvas, hitBypass, masterBypass ? "BYPASS" : "DSP LIVE",
                bypassCol, true, true, bypassCol, TARGET_BYPASS);

        boolean aActive = activeSnapshot == 0;
        drawStudioPill(canvas, hitSnapA, "SNAP A",
                StudioLedButtonHelper.STUDIO_CYAN, aActive, false, StudioLedButtonHelper.STUDIO_CYAN, TARGET_SNAP_A);

        boolean bActive = activeSnapshot == 1;
        drawStudioPill(canvas, hitSnapB, "SNAP B",
                StudioLedButtonHelper.STUDIO_YELLOW, bActive, false, StudioLedButtonHelper.STUDIO_YELLOW, TARGET_SNAP_B);

        String bpmText = String.format(Locale.US, "%d BPM • %s",
                Math.round(liveBpm),
                "--".equals(detectedCamelot) ? "--" : detectedCamelot);
        drawPill(canvas, hitBpmKeyBadge, bpmText,
                0x280F172A, 0x6638BDF8, 0xFFBAE6FD, true,
                beatEnergy > 0.45f ? StudioLedButtonHelper.STUDIO_CYAN : 0xFF334155);

        drawStudioPill(canvas, hitStage3d, "3D STAGE",
                StudioLedButtonHelper.STUDIO_MAGENTA, true, false, StudioLedButtonHelper.STUDIO_MAGENTA, TARGET_STAGE_3D);

        // Row 2: 5 Studio Tabs (Cyan, Green, Yellow, Red, Magenta)
        String[] titles = {"SCOPE", "3D RTA", "TUBE/MB", "DJ/PADS", "KAOSS/KAR"};
        int[] activeColors = {
                StudioLedButtonHelper.STUDIO_CYAN,
                StudioLedButtonHelper.STUDIO_GREEN,
                StudioLedButtonHelper.STUDIO_YELLOW,
                StudioLedButtonHelper.STUDIO_RED,
                StudioLedButtonHelper.STUDIO_MAGENTA
        };
        for (int i = 0; i < 5; i++) {
            boolean sel = activeTab == i;
            drawStudioPill(canvas, hitTabs[i], titles[i],
                    activeColors[i], sel, false, activeColors[i], TARGET_TAB_0 + i);
        }
    }

    private void drawStudioPill(
            Canvas canvas,
            RectF rect,
            String text,
            int accentColor,
            boolean active,
            boolean withLed,
            int ledColor,
            int targetId) {
        if (ledButtonHelper != null) {
            ledButtonHelper.drawSingleLineButton(
                    canvas,
                    rect,
                    rect.height() * 0.45f,
                    density,
                    text,
                    8.3f * density,
                    accentColor,
                    active,
                    withLed,
                    ledColor,
                    beatEnergy,
                    getPressFlashFor(targetId)
            );
        }
    }

    private void drawPill(
            Canvas canvas,
            RectF rect,
            String text,
            int fillColor,
            int borderColor,
            int textColor,
            boolean withLed,
            int ledColor) {
        float r = rect.height() * 0.45f;
        pillBgPaint.setColor(fillColor);
        canvas.drawRoundRect(rect, r, r, pillBgPaint);
        pillBorderPaint.setColor(borderColor);
        canvas.drawRoundRect(rect, r, r, pillBorderPaint);

        float textCx = rect.centerX();
        if (withLed) {
            float ledX = rect.left + Math.min(8f * density, rect.height() * 0.36f);
            float ledY = rect.centerY();
            ledHaloPaint.setColor((ledColor & 0x00FFFFFF) | 0x88000000);
            canvas.drawCircle(ledX, ledY, 4.6f * density, ledHaloPaint);
            ledPaint.setColor(ledColor);
            canvas.drawCircle(ledX, ledY, 2.3f * density, ledPaint);
            textCx += 4.2f * density;
        }
        pillTextPaint.setColor(textColor);
        float textY = rect.centerY() - (pillTextPaint.ascent() + pillTextPaint.descent()) * 0.5f;
        canvas.drawText(text, textCx, textY, pillTextPaint);
    }

    private void drawScopeTab(Canvas canvas) {
        float cl = contentRect.left;
        float ct = contentRect.top;
        float cw = contentRect.width();
        float ch = contentRect.height();

        float leftBoxR = cl + cw * 0.47f;
        tempRect.set(cl, ct, leftBoxR, contentRect.bottom);
        canvas.drawRoundRect(tempRect, 12f * density, 12f * density, subPanelPaint);
        canvas.drawRoundRect(tempRect, 12f * density, 12f * density, subPanelBorderPaint);

        float scx = tempRect.centerX();
        float scy = tempRect.centerY() + 3f * density;
        float radius = Math.min(tempRect.width(), tempRect.height()) * 0.38f;

        labelSmallPaint.setColor(0xFF94A3B8);
        canvas.drawText("STEREO GONIOMETER (XY)", scx, tempRect.top + 12f * density, labelSmallPaint);

        canvas.drawCircle(scx, scy, radius, scopeGridPaint);
        canvas.drawCircle(scx, scy, radius * 0.55f, scopeGridPaint);
        float diag = radius * 0.7071f;
        canvas.drawLine(scx - diag, scy - diag, scx + diag, scy + diag, scopeGridPaint);
        canvas.drawLine(scx + diag, scy - diag, scx - diag, scy + diag, scopeGridPaint);
        canvas.drawLine(scx, scy - radius, scx, scy + radius, scopeGridPaint);
        canvas.drawLine(scx - radius, scy, scx + radius, scy, scopeGridPaint);

        labelSmallPaint.setColor(0xFF38BDF8);
        canvas.drawText("L", scx - diag - 6f * density, scy - diag + 4f * density, labelSmallPaint);
        labelSmallPaint.setColor(0xFFF472B6);
        canvas.drawText("R", scx + diag + 6f * density, scy - diag + 4f * density, labelSmallPaint);

        scopePath.reset();
        for (int i = 0; i < xyLeft.length; i++) {
            float l = xyLeft[i];
            float r = xyRight[i];
            float sx = Math.max(-1f, Math.min(1f, (l - r) * 0.707f)) * radius * 0.92f;
            float sy = -Math.max(-1f, Math.min(1f, (l + r) * 0.707f)) * radius * 0.92f;
            if (i == 0) {
                scopePath.moveTo(scx + sx, scy + sy);
            } else {
                scopePath.lineTo(scx + sx, scy + sy);
            }
        }
        int traceColor = masterBypass ? 0xFF94A3B8 : accentLow;
        scopeGlowPaint.setColor((traceColor & 0x00FFFFFF) | 0x44000000);
        scopeTracePaint.setColor(traceColor);
        canvas.drawPath(scopePath, scopeGlowPaint);
        canvas.drawPath(scopePath, scopeTracePaint);

        float rightL = leftBoxR + 7f * density;
        float rightR = contentRect.right;
        float rightW = rightR - rightL;

        float phaseBot = ct + ch * 0.25f;
        tempRect.set(rightL, ct, rightR, phaseBot);
        canvas.drawRoundRect(tempRect, 9f * density, 9f * density, subPanelPaint);
        canvas.drawRoundRect(tempRect, 9f * density, 9f * density, subPanelBorderPaint);

        float corr = Math.max(-1f, Math.min(1f, phaseCorrelation));
        int corrColor = corr >= 0.2f ? 0xFF34D399 : (corr >= 0f ? 0xFFFBBF24 : 0xFFFB7185);
        labelSmallPaint.setColor(0xFF94A3B8);
        canvas.drawText(String.format(Locale.US, "PHASE CORR  %+.2f", corr),
                tempRect.centerX(), tempRect.top + 11.5f * density, labelSmallPaint);

        float barL = tempRect.left + 12f * density;
        float barR = tempRect.right - 12f * density;
        float barY = tempRect.bottom - 9f * density;
        scopeGridPaint.setStrokeWidth(3f * density);
        canvas.drawLine(barL, barY, barR, barY, scopeGridPaint);
        scopeGridPaint.setStrokeWidth(0.9f * density);
        float midX = (barL + barR) * 0.5f;
        float corrX = midX + corr * (barR - barL) * 0.5f;
        ledHaloPaint.setColor((corrColor & 0x00FFFFFF) | 0x55000000);
        canvas.drawCircle(corrX, barY, 6.5f * density, ledHaloPaint);
        ledPaint.setColor(corrColor);
        canvas.drawCircle(corrX, barY, 3.3f * density, ledPaint);

        float statsTop = phaseBot + 5f * density;
        float statsBot = ct + ch * 0.55f;
        float halfW = (rightW - 5f * density) * 0.5f;

        tempRect.set(rightL, statsTop, rightL + halfW, statsBot);
        canvas.drawRoundRect(tempRect, 9f * density, 9f * density, subPanelPaint);
        canvas.drawRoundRect(tempRect, 9f * density, 9f * density, subPanelBorderPaint);
        labelSmallPaint.setColor(0xFF94A3B8);
        canvas.drawText("LUFS SHORT", tempRect.centerX(), tempRect.top + 12f * density, labelSmallPaint);
        valueTextPaint.setColor(accentLow);
        canvas.drawText(String.format(Locale.US, "%.1f", lufsShortTerm),
                tempRect.centerX(), tempRect.bottom - 8f * density, valueTextPaint);

        tempRect.set(rightL + halfW + 5f * density, statsTop, rightR, statsBot);
        canvas.drawRoundRect(tempRect, 9f * density, 9f * density, subPanelPaint);
        canvas.drawRoundRect(tempRect, 9f * density, 9f * density, subPanelBorderPaint);
        labelSmallPaint.setColor(0xFF94A3B8);
        canvas.drawText("CREST DR", tempRect.centerX(), tempRect.top + 12f * density, labelSmallPaint);
        valueTextPaint.setColor(accentHigh);
        canvas.drawText(String.format(Locale.US, "%.1f dB", crestFactorDb),
                tempRect.centerX(), tempRect.bottom - 8f * density, valueTextPaint);

        float curveTop = statsBot + 5f * density;
        tempRect.set(rightL, curveTop, rightR, contentRect.bottom);
        canvas.drawRoundRect(tempRect, 9f * density, 9f * density, subPanelPaint);
        canvas.drawRoundRect(tempRect, 9f * density, 9f * density, subPanelBorderPaint);

        labelSmallPaint.setColor(harmanTargetEnabled ? 0xFF34D399 : 0xFF94A3B8);
        canvas.drawText(harmanTargetEnabled ? "EQ CURVE + HARMAN" : "EQ + FILTER CURVE",
                tempRect.centerX(), tempRect.top + 11.5f * density, labelSmallPaint);

        float plotL = tempRect.left + 8f * density;
        float plotR = tempRect.right - 8f * density;
        float plotT = tempRect.top + 16f * density;
        float plotB = tempRect.bottom - 5f * density;
        float zeroY = (plotT + plotB) * 0.5f;
        canvas.drawLine(plotL, zeroY, plotR, zeroY, scopeGridPaint);

        curvePath.reset();
        curveFillPath.reset();
        float spanX = Math.max(1f, plotR - plotL);
        float spanY = Math.max(10f, plotB - plotT);
        for (int i = 0; i < responseCurveDb.length; i++) {
            float nx = (float) i / Math.max(1, responseCurveDb.length - 1);
            float px = plotL + nx * spanX;
            float clampedDb = Math.max(-15f, Math.min(15f, responseCurveDb[i]));
            float ny = (clampedDb + 15f) / 30f;
            float py = plotB - ny * spanY;
            if (i == 0) {
                curvePath.moveTo(px, py);
                curveFillPath.moveTo(px, zeroY);
                curveFillPath.lineTo(px, py);
            } else {
                curvePath.lineTo(px, py);
                curveFillPath.lineTo(px, py);
            }
        }
        curveFillPath.lineTo(plotR, zeroY);
        curveFillPath.close();

        int curveCol = masterBypass ? 0xFF64748B : accentLow;
        curveFillPaint.setColor((curveCol & 0x00FFFFFF) | 0x26000000);
        curveStrokePaint.setColor(curveCol);
        canvas.drawPath(curveFillPath, curveFillPaint);
        canvas.drawPath(curvePath, curveStrokePaint);
    }

    private void drawWaterfallTab(Canvas canvas) {
        float cl = contentRect.left;
        float ct = contentRect.top;
        float cw = contentRect.width();
        float ch = contentRect.height();

        // Left: 3D Perspective Thermal Neon Waterfall (65% width)
        float wfR = cl + cw * 0.65f;
        tempRect.set(cl, ct, wfR, contentRect.bottom);
        canvas.drawRoundRect(tempRect, 12f * density, 12f * density, subPanelPaint);
        canvas.drawRoundRect(tempRect, 12f * density, 12f * density, subPanelBorderPaint);

        labelSmallPaint.setColor(0xFF7DD3FC);
        canvas.drawText("3D WATERFALL SPECTROGRAM + PEAK HOLD", tempRect.centerX(), tempRect.top + 11.5f * density, labelSmallPaint);

        float plotL = tempRect.left + 8f * density;
        float plotR = tempRect.right - 8f * density;
        float plotT = tempRect.top + 17f * density;
        float plotB = tempRect.bottom - 7f * density;
        float plotW = Math.max(20f, plotR - plotL);
        float plotH = Math.max(20f, plotB - plotT);
        float centerX = (plotL + plotR) * 0.5f;

        int rows = activeWaterfallRows;
        float maxAmpPx = plotH * 0.28f;
        for (int r = rows - 1; r >= 0; r--) {
            int histIdx = (waterfallWriteRow - r + MAX_WATERFALL_ROWS) % MAX_WATERFALL_ROWS;
            float[] rowBars = waterfallHistory[histIdx];
            float depth = (float) r / Math.max(1, rows - 1); // 0 = front/latest, 1 = back/oldest
            float baseY = plotB - depth * (plotH * 0.72f);
            float scaleW = 1.0f - depth * 0.36f;
            float rowW = plotW * scaleW;
            float startX = centerX - rowW * 0.5f;
            int alpha = Math.max(28, Math.round((1f - depth * 0.78f) * 235f));

            waterfallPath.reset();
            for (int c = 0; c < WATERFALL_COLS; c++) {
                float tx = startX + (c / (float) (WATERFALL_COLS - 1)) * rowW;
                float val = rowBars[c];
                float ty = baseY - val * maxAmpPx * (1f - depth * 0.45f);
                if (c == 0) {
                    waterfallPath.moveTo(tx, baseY);
                    waterfallPath.lineTo(tx, ty);
                } else {
                    waterfallPath.lineTo(tx, ty);
                }
            }
            waterfallPath.lineTo(startX + rowW, baseY);
            waterfallPath.close();

            int thermalCol = sampleWaterfallThermalColor(1f - depth, rowBars[WATERFALL_COLS / 4]);
            waterfallPaint.setStyle(Paint.Style.FILL);
            waterfallPaint.setColor((thermalCol & 0x00FFFFFF) | ((alpha / 2) << 24));
            canvas.drawPath(waterfallPath, waterfallPaint);

            waterfallPaint.setStyle(Paint.Style.STROKE);
            waterfallPaint.setStrokeWidth((1.5f - depth * 0.7f) * density);
            waterfallPaint.setColor((thermalCol & 0x00FFFFFF) | (alpha << 24));
            canvas.drawPath(waterfallPath, waterfallPaint);
        }
        waterfallPaint.setStyle(Paint.Style.FILL);

        // Front peak-hold glowing dots
        float frontSlotW = plotW / WATERFALL_COLS;
        for (int c = 0; c < WATERFALL_COLS; c++) {
            float px = plotL + (c + 0.5f) * frontSlotW;
            float py = plotB - waterfallPeakHold[c] * maxAmpPx;
            ledPaint.setColor(waterfallPeakHold[c] > 0.72f ? 0xFFFACC15 : accentLow);
            canvas.drawCircle(px, py, 1.5f * density, ledPaint);
        }

        // Right Column: Musical Note/Hz + BPM + Harmonic Key
        float rL = wfR + 6f * density;
        float rR = contentRect.right;
        float cardH = (ch - 10f * density) / 3f;

        tempRect.set(rL, ct, rR, ct + cardH);
        canvas.drawRoundRect(tempRect, 9f * density, 9f * density, subPanelPaint);
        canvas.drawRoundRect(tempRect, 9f * density, 9f * density, subPanelBorderPaint);
        labelSmallPaint.setColor(0xFF94A3B8);
        canvas.drawText("DOMINANT NOTE", tempRect.centerX(), tempRect.top + 11.5f * density, labelSmallPaint);
        valueTextPaint.setColor(accentLow);
        String noteStr = dominantFreqHz > 16f
                ? String.format(Locale.US, "%s • %dHz", dominantNoteName, Math.round(dominantFreqHz))
                : "--";
        canvas.drawText(noteStr, tempRect.centerX(), tempRect.bottom - 9f * density, valueTextPaint);

        tempRect.set(rL, tempRect.bottom + 5f * density, rR, tempRect.bottom + 5f * density + cardH);
        canvas.drawRoundRect(tempRect, 9f * density, 9f * density, subPanelPaint);
        canvas.drawRoundRect(tempRect, 9f * density, 9f * density, subPanelBorderPaint);
        labelSmallPaint.setColor(0xFF94A3B8);
        canvas.drawText("TEMPO TRACKER", tempRect.centerX(), tempRect.top + 11.5f * density, labelSmallPaint);
        valueTextPaint.setColor(0xFFFACC15);
        canvas.drawText(String.format(Locale.US, "%d BPM", Math.round(liveBpm)),
                tempRect.centerX(), tempRect.bottom - 9f * density, valueTextPaint);

        tempRect.set(rL, tempRect.bottom + 5f * density, rR, contentRect.bottom);
        canvas.drawRoundRect(tempRect, 9f * density, 9f * density, subPanelPaint);
        canvas.drawRoundRect(tempRect, 9f * density, 9f * density, subPanelBorderPaint);
        labelSmallPaint.setColor(0xFF94A3B8);
        canvas.drawText("KEY • CAMELOT", tempRect.centerX(), tempRect.top + 11.5f * density, labelSmallPaint);
        valueTextPaint.setColor(accentHigh);
        String keyTxt = "--".equals(detectedKeyLabel) ? "--" : (detectedCamelot + " • " + detectedKeyLabel);
        canvas.drawText(keyTxt, tempRect.centerX(), tempRect.bottom - 9f * density, valueTextPaint);
    }

    private int sampleWaterfallThermalColor(float freshness, float energy) {
        if (energy > 0.78f && freshness > 0.75f) return 0xFFFACC15; // Gold peak
        if (freshness > 0.65f) return accentHigh;
        if (freshness > 0.35f) return accentMid;
        return accentLow;
    }

    private void drawTubeMultibandTab(Canvas canvas) {
        float ct = contentRect.top;
        float gap = 5f * density;
        float tubeR = contentRect.left + contentRect.width() * 0.35f;
        float mbL = tubeR + gap;
        float mbR = mbL + contentRect.width() * 0.35f;
        float revL = mbR + gap;

        // 1. Left Bay: Analog Tube & Tape
        tempRect.set(contentRect.left, ct, tubeR, contentRect.bottom);
        canvas.drawRoundRect(tempRect, 11f * density, 11f * density, subPanelPaint);
        canvas.drawRoundRect(tempRect, 11f * density, 11f * density, subPanelBorderPaint);

        labelSmallPaint.setColor(0xFFFBBF24);
        canvas.drawText("TUBE & TAPE", tempRect.centerX(), tempRect.top + 11f * density, labelSmallPaint);

        float glowLevel = tubeEnabled && !masterBypass
                ? Math.min(1f, 0.30f + (tubeDrivePercent / 100f) * 0.42f + liveTubeGlow * 0.45f)
                : 0.05f;
        float tubeCenterY = ct + contentRect.height() * 0.23f;
        drawVacuumTube(canvas, tempRect.left + tempRect.width() * 0.32f, tubeCenterY, glowLevel);
        drawVacuumTube(canvas, tempRect.left + tempRect.width() * 0.68f, tubeCenterY, glowLevel);

        drawRotaryKnob(canvas, hitKnobDrive, "DRIVE",
                tubeDrivePercent + "%", tubeDrivePercent / 100f,
                tubeEnabled ? 0xFFF59E0B : 0xFF64748B, activeTarget == TARGET_KNOB_DRIVE);
        drawRotaryKnob(canvas, hitKnobWarmth, "WARM",
                tapeWarmthPercent + "%", tapeWarmthPercent / 100f,
                0xFFFB7185, activeTarget == TARGET_KNOB_WARMTH);

        drawStudioPill(canvas, hitSwTube, "TUBE",
                StudioLedButtonHelper.STUDIO_YELLOW, tubeEnabled, true, StudioLedButtonHelper.STUDIO_YELLOW, TARGET_SW_TUBE);
        drawStudioPill(canvas, hitSwCrossfeed, "X-FEED",
                StudioLedButtonHelper.STUDIO_CYAN, crossfeedEnabled, true, StudioLedButtonHelper.STUDIO_CYAN, TARGET_SW_CROSSFEED);

        // 2. Middle Bay: 3-Band Multiband Comp + De-Esser + GR Meters
        tempRect.set(mbL, ct, mbR, contentRect.bottom);
        canvas.drawRoundRect(tempRect, 11f * density, 11f * density, subPanelPaint);
        canvas.drawRoundRect(tempRect, 11f * density, 11f * density, subPanelBorderPaint);

        drawStudioPill(canvas, hitSwMultiband, multibandEnabled ? "3-BAND MB ON" : "3-BAND MB",
                StudioLedButtonHelper.STUDIO_GREEN, multibandEnabled, true, StudioLedButtonHelper.STUDIO_GREEN, TARGET_SW_MULTIBAND);

        drawRotaryKnob(canvas, hitKnobMbDrive, "MB DRV",
                multibandDrivePercent + "%", multibandDrivePercent / 100f,
                multibandEnabled ? 0xFF34D399 : 0xFF64748B, activeTarget == TARGET_KNOB_MB_DRIVE);
        drawRotaryKnob(canvas, hitKnobDeEsser, "DE-ESS",
                deEsserPercent + "%", deEsserPercent / 100f,
                deEsserPercent > 0 ? accentLow : 0xFF64748B, activeTarget == TARGET_KNOB_DE_ESS);

        // 3 Live GR Meters (LOW, MID, HIGH)
        float grTop = contentRect.bottom - 30f * density;
        float grPad = 7f * density;
        float grBarW = (mbR - mbL - grPad * 4f) / 3f;
        drawGrMeter(canvas, mbL + grPad, grTop, grBarW, "LOW", mbLowGrDb, 0xFF38BDF8);
        drawGrMeter(canvas, mbL + grPad * 2f + grBarW, grTop, grBarW, "MID", mbMidGrDb, 0xFFA855F7);
        drawGrMeter(canvas, mbL + grPad * 3f + grBarW * 2f, grTop, grBarW, "HI", mbHighGrDb, 0xFFF43F9E);

        // 3. Right Bay: Studio Schroeder Reverb
        tempRect.set(revL, ct, contentRect.right, contentRect.bottom);
        canvas.drawRoundRect(tempRect, 11f * density, 11f * density, subPanelPaint);
        canvas.drawRoundRect(tempRect, 11f * density, 11f * density, subPanelBorderPaint);

        drawStudioPill(canvas, hitSwReverb, reverbEnabled ? "REVERB ON" : "REVERB",
                StudioLedButtonHelper.STUDIO_MAGENTA, reverbEnabled, true, StudioLedButtonHelper.STUDIO_MAGENTA, TARGET_SW_REVERB);

        int revColor = reverbEnabled ? accentMid : 0xFF64748B;
        drawRotaryKnob(canvas, hitKnobRoom, "ROOM",
                reverbRoomPercent + "%", reverbRoomPercent / 100f, revColor, activeTarget == TARGET_KNOB_ROOM);
        drawRotaryKnob(canvas, hitKnobDamp, "DAMP",
                reverbDampPercent + "%", reverbDampPercent / 100f, revColor, activeTarget == TARGET_KNOB_DAMP);
        drawRotaryKnob(canvas, hitKnobMix, "MIX",
                reverbMixPercent + "%", reverbMixPercent / 100f, revColor, activeTarget == TARGET_KNOB_MIX);
    }

    private void drawGrMeter(Canvas canvas, float x, float top, float w, String label, float grDb, int col) {
        labelSmallPaint.setColor(0xFF94A3B8);
        canvas.drawText(label, x + w * 0.5f, top + 8f * density, labelSmallPaint);
        float barTop = top + 12f * density;
        float barH = 6.5f * density;
        tempRect.set(x, barTop, x + w, barTop + barH);
        pillBgPaint.setColor(0xFF1E293B);
        canvas.drawRoundRect(tempRect, 3f * density, 3f * density, pillBgPaint);

        float norm = Math.max(0f, Math.min(1f, grDb / 12f));
        if (norm > 0.02f) {
            tempRect.set(x, barTop, x + w * norm, barTop + barH);
            meterFillPaint.setColor(col);
            canvas.drawRoundRect(tempRect, 3f * density, 3f * density, meterFillPaint);
        }
        valueTextPaint.setTextSize(6.8f * density);
        valueTextPaint.setColor(grDb > 0.2f ? col : 0xFF64748B);
        canvas.drawText(String.format(Locale.US, "-%.1f", grDb), x + w * 0.5f, barTop + 15f * density, valueTextPaint);
        valueTextPaint.setTextSize(9.2f * density);
    }

    private void drawVacuumTube(Canvas canvas, float cx, float cy, float glowIntensity) {
        float w = 18f * density;
        float h = 28f * density;
        float glassTop = cy - h * 0.45f;
        float glassBot = cy + h * 0.25f;

        if (glowIntensity > 0.08f) {
            int alpha = Math.min(220, Math.round(glowIntensity * 190f));
            float r = w * 1.15f;
            shaderMatrix.reset();
            shaderMatrix.setScale(r, r);
            shaderMatrix.postTranslate(cx, cy - 2f * density);
            unitTubeGlowShader.setLocalMatrix(shaderMatrix);
            tubeFilamentPaint.setShader(unitTubeGlowShader);
            tubeFilamentPaint.setAlpha(alpha);
            canvas.drawCircle(cx, cy - 2f * density, r, tubeFilamentPaint);
            tubeFilamentPaint.setShader(null);
            tubeFilamentPaint.setAlpha(255);
        }

        tempRect.set(cx - w * 0.5f, glassTop, cx + w * 0.5f, glassBot);
        tubeGlassPaint.setColor(0x55BAE6FD);
        canvas.drawRoundRect(tempRect, w * 0.48f, w * 0.48f, tubeGlassPaint);
        canvas.drawRoundRect(tempRect, w * 0.48f, w * 0.48f, subPanelBorderPaint);

        int filamentAlpha = Math.max(40, Math.min(255, Math.round(glowIntensity * 255f)));
        tubeFilamentPaint.setColor(Color.argb(filamentAlpha, 255, 190, 70));
        canvas.drawRoundRect(cx - 2.0f * density, glassTop + 5f * density,
                cx + 2.0f * density, glassBot - 3f * density, 2f * density, 2f * density, tubeFilamentPaint);

        tempRect.set(cx - w * 0.55f, glassBot - 2f * density, cx + w * 0.55f, cy + h * 0.45f);
        canvas.drawRoundRect(tempRect, 3f * density, 3f * density, tubeBasePaint);
    }

    private void drawDjPadsTab(Canvas canvas) {
        float topPanelBot = hitStutter[0].top - 4f * density;
        tempRect.set(contentRect.left, contentRect.top, contentRect.right, topPanelBot);
        canvas.drawRoundRect(tempRect, 11f * density, 11f * density, subPanelPaint);
        canvas.drawRoundRect(tempRect, 11f * density, 11f * density, subPanelBorderPaint);

        String filterVal = djFilterPercent == 0 ? "FLAT"
                : (djFilterPercent < 0 ? "LPF " + Math.abs(djFilterPercent) : "HPF +" + djFilterPercent);
        float filterNorm = (djFilterPercent + 100f) / 200f;
        int filterCol = djFilterPercent < 0 ? accentLow : (djFilterPercent > 0 ? accentHigh : 0xFF94A3B8);
        drawRotaryKnob(canvas, hitKnobDjFilter, "DJ FILT", filterVal, filterNorm,
                filterCol, activeTarget == TARGET_KNOB_DJ_FILTER);

        float tempoNorm = (tempoValue - 0.50f) / 1.00f;
        drawRotaryKnob(canvas, hitKnobTempo, "TEMPO",
                String.format(Locale.US, "%.2fx", tempoValue), tempoNorm,
                accentLow, activeTarget == TARGET_KNOB_TEMPO);

        float pitchNorm = (pitchValue - 0.75f) / 0.60f;
        drawRotaryKnob(canvas, hitKnobPitch, "PITCH",
                String.format(Locale.US, "%.2fx", pitchValue), pitchNorm,
                accentMid, activeTarget == TARGET_KNOB_PITCH);

        drawStudioPill(canvas, hitSwVinylBrake, "VINYL BRAKE",
                StudioLedButtonHelper.STUDIO_RED, vinylBrakeEnabled, true, StudioLedButtonHelper.STUDIO_RED, TARGET_SW_VINYL_BRAKE);

        boolean aSet = abLoopStartMs >= 0L;
        boolean bSet = abLoopEndMs > abLoopStartMs && aSet;
        drawStudioPill(canvas, hitLoopA, aSet ? "A SET" : "LOOP A",
                StudioLedButtonHelper.STUDIO_GREEN, aSet, false, StudioLedButtonHelper.STUDIO_GREEN, TARGET_LOOP_A);
        drawStudioPill(canvas, hitLoopB, bSet ? "CLR A-B" : "LOOP B",
                StudioLedButtonHelper.STUDIO_RED, bSet, false, StudioLedButtonHelper.STUDIO_RED, TARGET_LOOP_B);

        // Stutter Repeat Row
        labelSmallPaint.setColor(0xFFFACC15);
        canvas.drawText("BEAT STUTTER", contentRect.left + contentRect.width() * 0.115f,
                hitStutter[0].centerY() + 3f * density, labelSmallPaint);
        String[] stutLabels = {"OFF", "1/4", "1/8", "1/16"};
        for (int i = 0; i < 4; i++) {
            boolean active = stutterDiv == i;
            int col = i == 0 ? StudioLedButtonHelper.STUDIO_RED : StudioLedButtonHelper.STUDIO_YELLOW;
            drawStudioPill(canvas, hitStutter[i], stutLabels[i],
                    col, i == 0 ? (stutterDiv == 0) : active, false, col, TARGET_STUTTER_0 + i);
        }

        // 4 Backlit Silicone Performance Pads (Red, Green, Yellow, Cyan — classic studio hardware triad!)
        String[] padTitles = {"BASS KILL", "VOCAL", "NIGHT", "LO-FI"};
        String[] padSubs = {"HPF 180Hz", "CENTER +6", "WARM COMP", "AM RADIO"};
        int[] padColors = {
                StudioLedButtonHelper.STUDIO_RED,
                StudioLedButtonHelper.STUDIO_GREEN,
                StudioLedButtonHelper.STUDIO_YELLOW,
                StudioLedButtonHelper.STUDIO_CYAN
        };
        int[] padModes = {
                NeonDspAudioProcessor.PAD_BASS_KILL,
                NeonDspAudioProcessor.PAD_VOCAL_FOCUS,
                NeonDspAudioProcessor.PAD_NIGHT_MODE,
                NeonDspAudioProcessor.PAD_LOFI_RADIO
        };

        for (int i = 0; i < 4; i++) {
            RectF pad = hitPads[i];
            boolean isActive = activePadMode == padModes[i];
            if (ledButtonHelper != null) {
                ledButtonHelper.drawTwoLineButton(
                        canvas,
                        pad,
                        10f * density,
                        density,
                        padTitles[i],
                        padSubs[i],
                        8.5f * density,
                        6.8f * density,
                        padColors[i],
                        isActive,
                        true,
                        beatEnergy,
                        getPressFlashFor(TARGET_PAD_1 + i)
                );
            }
        }
    }

    private void drawKaossKaraokeTab(Canvas canvas) {
        // Left: XY Neon Kaoss Touch Pad
        canvas.drawRoundRect(hitKaossPad, 12f * density, 12f * density, subPanelPaint);
        pillBorderPaint.setColor(activeTarget == TARGET_KAOSS_PAD ? accentHigh : 0x5522D3EE);
        canvas.drawRoundRect(hitKaossPad, 12f * density, 12f * density, pillBorderPaint);

        labelSmallPaint.setColor(0xFF7DD3FC);
        canvas.drawText("KAOSS XY (FILTER x REVERB)", hitKaossPad.centerX(), hitKaossPad.top + 11.5f * density, labelSmallPaint);

        float pL = hitKaossPad.left + 8f * density;
        float pR = hitKaossPad.right - 8f * density;
        float pT = hitKaossPad.top + 17f * density;
        float pB = hitKaossPad.bottom - 14f * density;
        float pCx = (pL + pR) * 0.5f;
        float pCy = (pT + pB) * 0.5f;

        canvas.drawLine(pCx, pT, pCx, pB, scopeGridPaint);
        canvas.drawLine(pL, pCy, pR, pCy, scopeGridPaint);
        canvas.drawRect(pL, pT, pR, pB, scopeGridPaint);

        // Draw neon fading trail
        if (kaossTrailCount >= 2) {
            for (int i = 1; i < kaossTrailCount; i++) {
                float frac = (float) i / Math.max(1, kaossTrailCount - 1);
                kaossTrailPaint.setStrokeWidth((1.2f + 2.6f * frac) * density);
                int alpha = Math.round(frac * 200f);
                kaossTrailPaint.setColor((accentHigh & 0x00FFFFFF) | (alpha << 24));
                float x1 = pL + kaossTrailX[i - 1] * (pR - pL);
                float y1 = pB - kaossTrailY[i - 1] * (pB - pT);
                float x2 = pL + kaossTrailX[i] * (pR - pL);
                float y2 = pB - kaossTrailY[i] * (pB - pT);
                canvas.drawLine(x1, y1, x2, y2, kaossTrailPaint);
            }
        }

        // Reticle synced to live djFilterPercent (X) & reverbMixPercent (Y)
        float normX = (djFilterPercent + 100f) / 200f;
        float normY = reverbEnabled ? (reverbMixPercent / 100f) : 0f;
        float rx = pL + normX * (pR - pL);
        float ry = pB - normY * (pB - pT);
        ledHaloPaint.setColor((accentHigh & 0x00FFFFFF) | 0x66000000);
        canvas.drawCircle(rx, ry, 10f * density, ledHaloPaint);
        ledPaint.setColor(0xFFFFFFFF);
        canvas.drawCircle(rx, ry, 4.2f * density, ledPaint);

        labelSmallPaint.setColor(0xFF94A3B8);
        canvas.drawText(String.format(Locale.US, "FILT %+d%% • REV %d%% (DBL-TAP FLAT)",
                djFilterPercent, reverbEnabled ? reverbMixPercent : 0),
                hitKaossPad.centerX(), hitKaossPad.bottom - 4f * density, labelSmallPaint);

        // Right Panel: Karaoke Vocal Remover + Harman Curve + Reference Match + Harmonic Mix
        float karL = hitKaossPad.right + 6f * density;
        tempRect.set(karL, contentRect.top, contentRect.right, contentRect.bottom);
        canvas.drawRoundRect(tempRect, 12f * density, 12f * density, subPanelPaint);
        canvas.drawRoundRect(tempRect, 12f * density, 12f * density, subPanelBorderPaint);

        drawRotaryKnob(canvas, hitKnobVocalRem, "KARAOKE",
                vocalRemoverPercent + "%", vocalRemoverPercent / 100f,
                vocalRemoverPercent > 0 ? 0xFF10B981 : 0xFF64748B, activeTarget == TARGET_KNOB_VOCAL_REM);

        drawStudioPill(canvas, hitSwHarman, harmanTargetEnabled ? "HARMAN ON" : "HARMAN CURVE",
                StudioLedButtonHelper.STUDIO_GREEN, harmanTargetEnabled, true, StudioLedButtonHelper.STUDIO_GREEN, TARGET_SW_HARMAN);

        drawStudioPill(canvas, hitBtnCaptureRef, hasRefProfile ? "REF READY • RECAP" : "CAPTURE REF",
                StudioLedButtonHelper.STUDIO_CYAN, hasRefProfile, false, StudioLedButtonHelper.STUDIO_CYAN, TARGET_BTN_CAPTURE_REF);

        drawStudioPill(canvas, hitSwMatchEq, refMatchEnabled ? "MATCH EQ ON" : "MATCH EQ",
                StudioLedButtonHelper.STUDIO_YELLOW, refMatchEnabled, true, StudioLedButtonHelper.STUDIO_YELLOW, TARGET_SW_MATCH_EQ);

        String harmonicLabel = "--".equals(detectedCamelot)
                ? "HARMONIC NEXT TRACK"
                : ("HARMONIC NEXT (" + detectedCamelot + " COMPAT)");
        drawStudioPill(canvas, hitBtnHarmonicNext, harmonicLabel,
                StudioLedButtonHelper.STUDIO_MAGENTA, true, false, StudioLedButtonHelper.STUDIO_MAGENTA, TARGET_BTN_HARMONIC_NEXT);
    }

    private void drawRotaryKnob(
            Canvas canvas,
            RectF slot,
            String label,
            String valueText,
            float normalized,
            int ringColor,
            boolean active) {
        float norm = Math.max(0f, Math.min(1f, normalized));
        float r = Math.max(10f * density, Math.min(slot.width(), slot.height() - 22f * density) * 0.34f);
        float cx = slot.centerX();
        float cy = slot.centerY() + 1f * density;

        labelSmallPaint.setColor(active ? Color.WHITE : 0xFFCBD5E1);
        canvas.drawText(label, cx, cy - r - 4.5f * density, labelSmallPaint);

        float arcR = r + 3.0f * density;
        tempRect.set(cx - arcR, cy - arcR, cx + arcR, cy + arcR);
        knobRimPaint.setColor(0x3394A3B8);
        knobRimPaint.setStrokeWidth(2.1f * density);
        canvas.drawArc(tempRect, 135f, 270f, false, knobRimPaint);

        knobRimPaint.setColor(ringColor);
        canvas.drawArc(tempRect, 135f, 270f * norm, false, knobRimPaint);
        knobRimPaint.setStrokeWidth(1.1f * density);

        // Shadow & brushed chrome dial via cached Matrix shader (0 allocations/frame)
        canvas.drawCircle(cx, cy + 2f * density, r, knobShadowPaint);
        shaderMatrix.reset();
        shaderMatrix.setScale(r, r);
        shaderMatrix.postTranslate(cx, cy);
        unitKnobChromeShader.setLocalMatrix(shaderMatrix);
        knobMetalPaint.setShader(unitKnobChromeShader);
        canvas.drawCircle(cx, cy, r, knobMetalPaint);
        knobMetalPaint.setShader(null);

        knobRimPaint.setColor(active ? Color.WHITE : 0x88E2E8F0);
        canvas.drawCircle(cx, cy, r, knobRimPaint);

        double angleRad = Math.toRadians(-225.0 + norm * 270.0);
        float px1 = cx + (float) (Math.cos(angleRad) * (r * 0.32f));
        float py1 = cy + (float) (Math.sin(angleRad) * (r * 0.32f));
        float px2 = cx + (float) (Math.cos(angleRad) * (r * 0.82f));
        float py2 = cy + (float) (Math.sin(angleRad) * (r * 0.82f));
        canvas.drawLine(px1, py1, px2, py2, knobPointerPaint);

        knobDotPaint.setColor(ringColor);
        canvas.drawCircle(px2, py2, 1.9f * density, knobDotPaint);

        valueTextPaint.setTextSize(7.8f * density);
        valueTextPaint.setColor(active ? Color.WHITE : 0xFFE2E8F0);
        canvas.drawText(valueText, cx, cy + r + 10.5f * density, valueTextPaint);
        valueTextPaint.setTextSize(9.2f * density);
    }

    private void drawFooterStrip(Canvas canvas) {
        String status;
        if (masterBypass) {
            status = "MASTER BYPASS ACTIVE • CLEAN UNPROCESSED SIGNAL";
            labelSmallPaint.setColor(0xFFFCA5A5);
        } else {
            StringBuilder sb = new StringBuilder("PRO STUDIO RACK • SNAP ")
                    .append(activeSnapshot == 0 ? "A" : "B");
            if (tubeEnabled) sb.append(" • TUBE");
            if (multibandEnabled) sb.append(" • MB");
            if (deEsserPercent > 0) sb.append(" • DE-ESS");
            if (reverbEnabled) sb.append(" • REV");
            if (harmanTargetEnabled) sb.append(" • HARMAN");
            if (vocalRemoverPercent > 0) sb.append(" • KAR");
            if (stutterDiv > 0) sb.append(" • STUT");
            status = sb.toString();
            labelSmallPaint.setColor(0xFF93C5FD);
        }
        canvas.drawText(status, footerRect.centerX(), footerRect.centerY() + 2.5f * density, labelSmallPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (getWidth() <= 0 || getHeight() <= 0) return false;
        float x = event.getX();
        float y = event.getY();
        int action = event.getActionMasked();

        if (action == MotionEvent.ACTION_DOWN) {
            if (getParent() != null) {
                getParent().requestDisallowInterceptTouchEvent(true);
            }
            activeTarget = hitTest(x, y);
            lastTouchY = y;
            longPressTriggered = false;
            touchHandler.removeCallbacks(snapLongPressRunnable);

            if (activeTarget == TARGET_SNAP_A || activeTarget == TARGET_SNAP_B) {
                touchHandler.postDelayed(snapLongPressRunnable, 550L);
                return true;
            }

            if (activeTarget == TARGET_KAOSS_PAD) {
                long now = SystemClock.uptimeMillis();
                if (lastTapTarget == TARGET_KAOSS_PAD && (now - lastTapMs) < 300L) {
                    djFilterPercent = 0;
                    reverbMixPercent = 0;
                    reverbEnabled = false;
                    kaossTrailCount = 0;
                    if (listener != null) listener.onKaossXyChanged(0, 0, false);
                    lastTapTarget = TARGET_NONE;
                } else {
                    lastTapTarget = TARGET_KAOSS_PAD;
                    lastTapMs = now;
                    applyKaossTouch(x, y);
                }
                invalidate();
                return true;
            }

            if (isImmediateTapTarget(activeTarget)) {
                handleTapTarget(activeTarget);
                return true;
            }

            if (isKnobTarget(activeTarget)) {
                long now = SystemClock.uptimeMillis();
                if (lastTapTarget == activeTarget && (now - lastTapMs) < 320L) {
                    resetKnobDefault(activeTarget);
                    lastTapTarget = TARGET_NONE;
                } else {
                    lastTapTarget = activeTarget;
                    lastTapMs = now;
                }
                invalidate();
                return true;
            }
            return activeTarget != TARGET_NONE;
        } else if (action == MotionEvent.ACTION_MOVE) {
            if (activeTarget == TARGET_KAOSS_PAD) {
                applyKaossTouch(x, y);
                invalidate();
                return true;
            } else if (isKnobTarget(activeTarget)) {
                float dy = lastTouchY - y;
                lastTouchY = y;
                float deltaNorm = dy / (125f * density);
                applyKnobDelta(activeTarget, deltaNorm);
                return true;
            }
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            touchHandler.removeCallbacks(snapLongPressRunnable);
            if ((activeTarget == TARGET_SNAP_A || activeTarget == TARGET_SNAP_B) && !longPressTriggered) {
                int slot = activeTarget == TARGET_SNAP_A ? 0 : 1;
                pressedLedTarget = activeTarget;
                pressedLedStartMs = SystemClock.uptimeMillis();
                activeSnapshot = slot;
                if (listener != null) {
                    listener.onSnapshotSelected(slot);
                }
                invalidate();
            }
            activeTarget = TARGET_NONE;
            if (getParent() != null) {
                getParent().requestDisallowInterceptTouchEvent(false);
            }
            invalidate();
            return true;
        }
        return super.onTouchEvent(event);
    }

    private void applyKaossTouch(float x, float y) {
        float pL = hitKaossPad.left + 8f * density;
        float pR = hitKaossPad.right - 8f * density;
        float pT = hitKaossPad.top + 17f * density;
        float pB = hitKaossPad.bottom - 14f * density;
        float nx = Math.max(0f, Math.min(1f, (x - pL) / Math.max(1f, pR - pL)));
        float ny = Math.max(0f, Math.min(1f, (pB - y) / Math.max(1f, pB - pT)));

        if (kaossTrailCount < KAOSS_TRAIL_LEN) {
            kaossTrailX[kaossTrailCount] = nx;
            kaossTrailY[kaossTrailCount] = ny;
            kaossTrailCount++;
        } else {
            System.arraycopy(kaossTrailX, 1, kaossTrailX, 0, KAOSS_TRAIL_LEN - 1);
            System.arraycopy(kaossTrailY, 1, kaossTrailY, 0, KAOSS_TRAIL_LEN - 1);
            kaossTrailX[KAOSS_TRAIL_LEN - 1] = nx;
            kaossTrailY[KAOSS_TRAIL_LEN - 1] = ny;
        }

        djFilterPercent = clampInt(Math.round((nx * 2f - 1f) * 100f), -100, 100);
        if (Math.abs(djFilterPercent) <= 4) djFilterPercent = 0;
        reverbMixPercent = clampInt(Math.round(ny * 100f), 0, 100);
        reverbEnabled = reverbMixPercent >= 5;
        if (listener != null) {
            listener.onKaossXyChanged(djFilterPercent, reverbMixPercent, reverbEnabled);
        }
    }

    private int hitTest(float x, float y) {
        if (hitBypass.contains(x, y)) return TARGET_BYPASS;
        if (hitSnapA.contains(x, y)) return TARGET_SNAP_A;
        if (hitSnapB.contains(x, y)) return TARGET_SNAP_B;
        if (hitStage3d.contains(x, y)) return TARGET_STAGE_3D;
        for (int i = 0; i < 5; i++) {
            if (hitTabs[i].contains(x, y)) return TARGET_TAB_0 + i;
        }

        if (activeTab == TAB_TUBE_REVERB) {
            if (hitSwTube.contains(x, y)) return TARGET_SW_TUBE;
            if (hitSwCrossfeed.contains(x, y)) return TARGET_SW_CROSSFEED;
            if (hitKnobDrive.contains(x, y)) return TARGET_KNOB_DRIVE;
            if (hitKnobWarmth.contains(x, y)) return TARGET_KNOB_WARMTH;
            if (hitSwMultiband.contains(x, y)) return TARGET_SW_MULTIBAND;
            if (hitKnobMbDrive.contains(x, y)) return TARGET_KNOB_MB_DRIVE;
            if (hitKnobDeEsser.contains(x, y)) return TARGET_KNOB_DE_ESS;
            if (hitSwReverb.contains(x, y)) return TARGET_SW_REVERB;
            if (hitKnobRoom.contains(x, y)) return TARGET_KNOB_ROOM;
            if (hitKnobDamp.contains(x, y)) return TARGET_KNOB_DAMP;
            if (hitKnobMix.contains(x, y)) return TARGET_KNOB_MIX;
        } else if (activeTab == TAB_DJ_PADS) {
            if (hitKnobDjFilter.contains(x, y)) return TARGET_KNOB_DJ_FILTER;
            if (hitKnobTempo.contains(x, y)) return TARGET_KNOB_TEMPO;
            if (hitKnobPitch.contains(x, y)) return TARGET_KNOB_PITCH;
            if (hitSwVinylBrake.contains(x, y)) return TARGET_SW_VINYL_BRAKE;
            if (hitLoopA.contains(x, y)) return TARGET_LOOP_A;
            if (hitLoopB.contains(x, y)) return TARGET_LOOP_B;
            for (int i = 0; i < 4; i++) {
                if (hitStutter[i].contains(x, y)) return TARGET_STUTTER_0 + i;
                if (hitPads[i].contains(x, y)) return TARGET_PAD_1 + i;
            }
        } else if (activeTab == TAB_KAOSS_KAR) {
            if (hitKaossPad.contains(x, y)) return TARGET_KAOSS_PAD;
            if (hitKnobVocalRem.contains(x, y)) return TARGET_KNOB_VOCAL_REM;
            if (hitSwHarman.contains(x, y)) return TARGET_SW_HARMAN;
            if (hitBtnCaptureRef.contains(x, y)) return TARGET_BTN_CAPTURE_REF;
            if (hitSwMatchEq.contains(x, y)) return TARGET_SW_MATCH_EQ;
            if (hitBtnHarmonicNext.contains(x, y)) return TARGET_BTN_HARMONIC_NEXT;
        }
        return TARGET_NONE;
    }

    private boolean isImmediateTapTarget(int t) {
        return t == TARGET_BYPASS || t == TARGET_STAGE_3D
                || (t >= TARGET_TAB_0 && t <= TARGET_TAB_4)
                || t == TARGET_SW_TUBE || t == TARGET_SW_CROSSFEED
                || t == TARGET_SW_MULTIBAND || t == TARGET_SW_REVERB
                || t == TARGET_SW_VINYL_BRAKE || t == TARGET_LOOP_A || t == TARGET_LOOP_B
                || (t >= TARGET_STUTTER_0 && t <= TARGET_STUTTER_3)
                || (t >= TARGET_PAD_1 && t <= TARGET_PAD_4)
                || t == TARGET_SW_HARMAN || t == TARGET_BTN_CAPTURE_REF
                || t == TARGET_SW_MATCH_EQ || t == TARGET_BTN_HARMONIC_NEXT;
    }

    private boolean isKnobTarget(int t) {
        return t == TARGET_KNOB_DRIVE || t == TARGET_KNOB_WARMTH
                || t == TARGET_KNOB_MB_DRIVE || t == TARGET_KNOB_DE_ESS
                || t == TARGET_KNOB_ROOM || t == TARGET_KNOB_DAMP || t == TARGET_KNOB_MIX
                || t == TARGET_KNOB_DJ_FILTER || t == TARGET_KNOB_TEMPO || t == TARGET_KNOB_PITCH
                || t == TARGET_KNOB_VOCAL_REM;
    }

    private void handleTapTarget(int t) {
        pressedLedTarget = t;
        pressedLedStartMs = SystemClock.uptimeMillis();
        if (t == TARGET_BYPASS) {
            masterBypass = !masterBypass;
            if (listener != null) listener.onMasterBypassChanged(masterBypass);
        } else if (t == TARGET_STAGE_3D) {
            if (listener != null) listener.onOpenStage3dRequested();
        } else if (t >= TARGET_TAB_0 && t <= TARGET_TAB_4) {
            activeTab = t - TARGET_TAB_0;
        } else if (t == TARGET_SW_TUBE) {
            tubeEnabled = !tubeEnabled;
            if (listener != null) listener.onTubeEnabledChanged(tubeEnabled);
        } else if (t == TARGET_SW_CROSSFEED) {
            crossfeedEnabled = !crossfeedEnabled;
            if (listener != null) listener.onCrossfeedChanged(crossfeedEnabled);
        } else if (t == TARGET_SW_MULTIBAND) {
            multibandEnabled = !multibandEnabled;
            if (listener != null) listener.onMultibandEnabledChanged(multibandEnabled);
        } else if (t == TARGET_SW_REVERB) {
            reverbEnabled = !reverbEnabled;
            if (listener != null) listener.onReverbEnabledChanged(reverbEnabled);
        } else if (t == TARGET_SW_VINYL_BRAKE) {
            vinylBrakeEnabled = !vinylBrakeEnabled;
            if (listener != null) listener.onVinylBrakeToggled(vinylBrakeEnabled);
        } else if (t == TARGET_LOOP_A) {
            if (listener != null) listener.onLoopSetARequested();
        } else if (t == TARGET_LOOP_B) {
            if (listener != null) listener.onLoopSetBOrClearRequested();
        } else if (t >= TARGET_STUTTER_0 && t <= TARGET_STUTTER_3) {
            int chosen = t - TARGET_STUTTER_0;
            stutterDiv = (chosen != 0 && stutterDiv == chosen) ? 0 : chosen;
            if (listener != null) listener.onStutterDivChanged(stutterDiv);
        } else if (t >= TARGET_PAD_1 && t <= TARGET_PAD_4) {
            int chosen = NeonDspAudioProcessor.PAD_BASS_KILL + (t - TARGET_PAD_1);
            activePadMode = (activePadMode == chosen) ? NeonDspAudioProcessor.PAD_NONE : chosen;
            if (listener != null) listener.onPerformancePadChanged(activePadMode);
        } else if (t == TARGET_SW_HARMAN) {
            harmanTargetEnabled = !harmanTargetEnabled;
            if (listener != null) listener.onHarmanTargetChanged(harmanTargetEnabled);
        } else if (t == TARGET_BTN_CAPTURE_REF) {
            hasRefProfile = true;
            if (listener != null) listener.onCaptureRefProfileRequested();
        } else if (t == TARGET_SW_MATCH_EQ) {
            refMatchEnabled = !refMatchEnabled;
            hasRefProfile = true;
            if (listener != null) listener.onRefMatchToggled(refMatchEnabled);
        } else if (t == TARGET_BTN_HARMONIC_NEXT) {
            if (listener != null) listener.onHarmonicNextTrackRequested();
        }
        invalidate();
    }

    private void applyKnobDelta(int target, float deltaNorm) {
        if (Math.abs(deltaNorm) < 0.001f) return;
        if (target == TARGET_KNOB_DRIVE) {
            tubeDrivePercent = clampInt(Math.round(tubeDrivePercent + deltaNorm * 100f), 0, 100);
            if (!tubeEnabled && tubeDrivePercent > 0) {
                tubeEnabled = true;
                if (listener != null) listener.onTubeEnabledChanged(true);
            }
            if (listener != null) listener.onTubeDriveChanged(tubeDrivePercent);
        } else if (target == TARGET_KNOB_WARMTH) {
            tapeWarmthPercent = clampInt(Math.round(tapeWarmthPercent + deltaNorm * 100f), 0, 100);
            if (listener != null) listener.onTapeWarmthChanged(tapeWarmthPercent);
        } else if (target == TARGET_KNOB_MB_DRIVE) {
            multibandDrivePercent = clampInt(Math.round(multibandDrivePercent + deltaNorm * 100f), 0, 100);
            if (!multibandEnabled && multibandDrivePercent > 0) {
                multibandEnabled = true;
                if (listener != null) listener.onMultibandEnabledChanged(true);
            }
            if (listener != null) listener.onMultibandDriveChanged(multibandDrivePercent);
        } else if (target == TARGET_KNOB_DE_ESS) {
            deEsserPercent = clampInt(Math.round(deEsserPercent + deltaNorm * 100f), 0, 100);
            if (listener != null) listener.onDeEsserChanged(deEsserPercent);
        } else if (target == TARGET_KNOB_ROOM) {
            reverbRoomPercent = clampInt(Math.round(reverbRoomPercent + deltaNorm * 100f), 0, 100);
            if (!reverbEnabled) {
                reverbEnabled = true;
                if (listener != null) listener.onReverbEnabledChanged(true);
            }
            if (listener != null) listener.onReverbParamsChanged(reverbRoomPercent, reverbDampPercent, reverbMixPercent);
        } else if (target == TARGET_KNOB_DAMP) {
            reverbDampPercent = clampInt(Math.round(reverbDampPercent + deltaNorm * 100f), 0, 100);
            if (listener != null) listener.onReverbParamsChanged(reverbRoomPercent, reverbDampPercent, reverbMixPercent);
        } else if (target == TARGET_KNOB_MIX) {
            reverbMixPercent = clampInt(Math.round(reverbMixPercent + deltaNorm * 100f), 0, 100);
            if (!reverbEnabled && reverbMixPercent > 0) {
                reverbEnabled = true;
                if (listener != null) listener.onReverbEnabledChanged(true);
            }
            if (listener != null) listener.onReverbParamsChanged(reverbRoomPercent, reverbDampPercent, reverbMixPercent);
        } else if (target == TARGET_KNOB_DJ_FILTER) {
            djFilterPercent = clampInt(Math.round(djFilterPercent + deltaNorm * 200f), -100, 100);
            if (Math.abs(djFilterPercent) <= 3) djFilterPercent = 0;
            if (listener != null) listener.onDjFilterChanged(djFilterPercent);
        } else if (target == TARGET_KNOB_TEMPO) {
            tempoValue = Math.max(0.50f, Math.min(1.50f, tempoValue + deltaNorm));
            if (listener != null) listener.onTempoChanged(tempoValue);
        } else if (target == TARGET_KNOB_PITCH) {
            pitchValue = Math.max(0.75f, Math.min(1.35f, pitchValue + deltaNorm * 0.60f));
            if (listener != null) listener.onPitchChanged(pitchValue);
        } else if (target == TARGET_KNOB_VOCAL_REM) {
            vocalRemoverPercent = clampInt(Math.round(vocalRemoverPercent + deltaNorm * 100f), 0, 100);
            if (listener != null) listener.onVocalRemoverChanged(vocalRemoverPercent);
        }
        invalidate();
    }

    private void resetKnobDefault(int target) {
        if (target == TARGET_KNOB_DRIVE) {
            tubeDrivePercent = 35;
            if (listener != null) listener.onTubeDriveChanged(tubeDrivePercent);
        } else if (target == TARGET_KNOB_WARMTH) {
            tapeWarmthPercent = 25;
            if (listener != null) listener.onTapeWarmthChanged(tapeWarmthPercent);
        } else if (target == TARGET_KNOB_MB_DRIVE) {
            multibandDrivePercent = 45;
            if (listener != null) listener.onMultibandDriveChanged(multibandDrivePercent);
        } else if (target == TARGET_KNOB_DE_ESS) {
            deEsserPercent = 0;
            if (listener != null) listener.onDeEsserChanged(0);
        } else if (target == TARGET_KNOB_ROOM) {
            reverbRoomPercent = 42;
            if (listener != null) listener.onReverbParamsChanged(reverbRoomPercent, reverbDampPercent, reverbMixPercent);
        } else if (target == TARGET_KNOB_DAMP) {
            reverbDampPercent = 35;
            if (listener != null) listener.onReverbParamsChanged(reverbRoomPercent, reverbDampPercent, reverbMixPercent);
        } else if (target == TARGET_KNOB_MIX) {
            reverbMixPercent = 24;
            if (listener != null) listener.onReverbParamsChanged(reverbRoomPercent, reverbDampPercent, reverbMixPercent);
        } else if (target == TARGET_KNOB_DJ_FILTER) {
            djFilterPercent = 0;
            if (listener != null) listener.onDjFilterChanged(0);
        } else if (target == TARGET_KNOB_TEMPO) {
            tempoValue = 1.0f;
            if (listener != null) listener.onTempoChanged(1.0f);
        } else if (target == TARGET_KNOB_PITCH) {
            pitchValue = 1.0f;
            if (listener != null) listener.onPitchChanged(1.0f);
        } else if (target == TARGET_KNOB_VOCAL_REM) {
            vocalRemoverPercent = 0;
            if (listener != null) listener.onVocalRemoverChanged(0);
        }
        invalidate();
    }

    private static int clampInt(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}
