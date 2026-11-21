package com.example.audio;

import androidx.annotation.OptIn;
import androidx.media3.common.C;
import androidx.media3.common.audio.BaseAudioProcessor;
import androidx.media3.common.util.UnstableApi;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Sample-accurate real-time Audio Processing & Purification Suite DSP inside ExoPlayer:
 *  - 10-Band Peaking EQ (-15dB..+15dB)
 *  - Sub-Bass Drive Low-Shelf & Punch (0%..200%)
 *  - Purification Suite HPF (20..250Hz) & LPF (8..22kHz) 2nd-order Butterworth Filters
 *  - Purification Bass Boost Shelf (+0..+12dB) & Treble Clarity High-Shelf (+0..+12dB)
 *  - Noise Gate (smooth hysteresis floor muting) & Noise Reduction (DC blocker + downward expander)
 *  - Dynamic Range Compressor (Threshold -30..0dB, Ratio 1:1..8:1)
 *  - Mid/Side Stereo Width (0%..200%) + 8D Spatial Orbit + L/R Channel Trim Gains
 *  - Adaptive Auto-Gain Loudness Leveler & Brickwall Peak Ceiling Limiter (-12..0dB)
 */
@OptIn(markerClass = UnstableApi.class)
public class NeonDspAudioProcessor extends BaseAudioProcessor {

    public static final int BAND_COUNT = 10;
    public static final float[] BAND_FREQS_HZ = {
            31f, 62f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f
    };

    public static final int PAD_NONE = 0;
    public static final int PAD_BASS_KILL = 1;
    public static final int PAD_VOCAL_FOCUS = 2;
    public static final int PAD_NIGHT_MODE = 3;
    public static final int PAD_LOFI_RADIO = 4;

    private static final int MAX_CHANNELS = 8;
    private static final float PEAK_Q = 1.35f;
    private static final float BASS_SHELF_HZ = 82.0f;
    private static final float SUB_WARMTH_HZ = 115.0f;
    private static final float PURIFY_BASS_HZ = 86.0f;
    private static final float CLARITY_HIGH_SHELF_HZ = 6800.0f;

    // Biquad indices:
    // [0..9]  = 10-band EQ
    // [10]    = Bass Slider Low-Shelf (0..200%)
    // [11]    = Sub Warmth Punch
    // [12]    = Purification HPF (20..250Hz)
    // [13]    = Purification LPF (8k..22kHz)
    // [14]    = Purification Bass Boost Shelf (0..+12dB)
    // [15]    = Purification Treble Clarity High-Shelf (0..+12dB)
    // [16]    = DJ Dual Filter (-100 LPF .. 0 .. +100 HPF)
    // [17]    = Analog Tape Warmth Soft High-Shelf
    // [18]    = Pad Mode Filter A (Bass Kill / Lo-Fi HPF / Vocal Presence)
    // [19]    = Pad Mode Filter B (Lo-Fi LPF)
    private static final int IDX_BASS_SLIDER = BAND_COUNT;
    private static final int IDX_SUB_WARMTH = BAND_COUNT + 1;
    private static final int IDX_HPF = BAND_COUNT + 2;
    private static final int IDX_LPF = BAND_COUNT + 3;
    private static final int IDX_PURIFY_BASS = BAND_COUNT + 4;
    private static final int IDX_TREBLE_CLARITY = BAND_COUNT + 5;
    private static final int IDX_DJ_FILTER = BAND_COUNT + 6;
    private static final int IDX_TAPE_WARMTH = BAND_COUNT + 7;
    private static final int IDX_PAD_A = BAND_COUNT + 8;
    private static final int IDX_PAD_B = BAND_COUNT + 9;
    private static final int IDX_HARMAN_LOW = BAND_COUNT + 10;
    private static final int IDX_HARMAN_MID = BAND_COUNT + 11;
    private static final int IDX_HARMAN_HIGH = BAND_COUNT + 12;
    private static final int IDX_4K_BOX_CLEAN = BAND_COUNT + 13;
    private static final int IDX_4K_AIR_SHELF = BAND_COUNT + 14;
    private static final int FILTER_COUNT = BAND_COUNT + 15;

    private final float[] b0 = new float[FILTER_COUNT];
    private final float[] b1 = new float[FILTER_COUNT];
    private final float[] b2 = new float[FILTER_COUNT];
    private final float[] a1 = new float[FILTER_COUNT];
    private final float[] a2 = new float[FILTER_COUNT];
    private final boolean[] filterActive = new boolean[FILTER_COUNT];

    // Direct Form II Transposed filter states [filter][channel]
    private final float[][] z1 = new float[FILTER_COUNT][MAX_CHANNELS];
    private final float[][] z2 = new float[FILTER_COUNT][MAX_CHANNELS];

    // Pending parameter snapshot (protected by paramLock)
    private final Object paramLock = new Object();
    private final float[] targetGainsDb = new float[BAND_COUNT];
    private boolean eqEnabled = true;
    private int bassPercent = 100;
    private int virtualizerPercent = 45;
    private int loudnessPercent = 10;
    private int balancePercent = 0; // -100..+100
    private boolean spatial8dEnabled = false;

    // Purification Suite parameters
    private float gainLDb = 0.0f;
    private float gainRDb = 0.0f;
    private float compressorRatio = 3.0f;         // 1.0..8.0
    private float compressorThresholdDb = -12.0f; // -30.0..0.0
    private boolean noiseGateEnabled = true;
    private boolean noiseReductionEnabled = true;
    private float purificationBassDb = 3.0f;      // 0..+12 dB
    private float trebleClarityDb = 2.5f;         // 0..+12 dB
    private int stereoWidthPercent = 120;         // 0..200%
    private float limiterCeilingDb = -1.0f;       // -12..0 dB
    private boolean autoGainEnabled = true;
    private int hpfHz = 80;                       // 20..250 Hz
    private int lpfHz = 18000;                    // 8000..22000 Hz

    // Pro Studio Rack parameters (Master Bypass, Tube/Tape, Crossfeed, Reverb, DJ Filter, Pads)
    private boolean masterBypass = false;
    private boolean tubeEnabled = false;
    private int tubeDrivePercent = 35;
    private int tapeWarmthPercent = 25;
    private boolean headphoneCrossfeedEnabled = false;
    private boolean reverbEnabled = false;
    private int reverbRoomPercent = 42;
    private int reverbDampPercent = 35;
    private int reverbMixPercent = 24;
    private int djFilterPercent = 0;              // -100..+100
    private int activePadMode = PAD_NONE;

    // Advanced Mastering & DJ Performance parameters
    private boolean multibandEnabled = false;
    private int multibandDrivePercent = 45;
    private int deEsserPercent = 0;               // 0..100%
    private int vocalRemoverPercent = 0;          // 0..100% (Karaoke w/ Bass Preserve)
    private boolean harmanTargetEnabled = false;
    private int stutterDiv = 0;                   // 0=OFF, 1=1/4, 2=1/8, 3=1/16
    private float stutterBpm = 120.0f;

    // 4K Ultra-HD Purifier & Zero-Crackle Anti-Distortion Protection
    private boolean ultraHd4kEnabled = true;
    private boolean antiCrackleEnabled = true;
    private boolean speakerOutputMode = true;

    private volatile boolean coeffsDirty = true;
    private volatile int coeffVersion = 0;
    private volatile int cachedCurveVersion48 = -1;
    private volatile int cachedCurveVersion64 = -1;
    private final float[] cachedCurve48 = new float[48];
    private final float[] cachedCurve64 = new float[64];

    // Audio-thread active parameters
    private boolean activeEq = true;
    private float stereoWidthMultiplier = 1.0f;
    private float preAmpGain = 1.0f;
    private float leftChannelLinearGain = 1.0f;
    private float rightChannelLinearGain = 1.0f;
    private boolean activeSpatial8d = false;
    private double spatialPhaseRad = 0.0;

    private float activeCompRatio = 3.0f;
    private float activeCompThreshDb = -12.0f;
    private boolean activeNoiseGate = true;
    private boolean activeNoiseReduction = true;
    private float activeLimiterCeilingLinear = 0.89125f;
    private boolean activeAutoGain = true;

    private boolean activeTubeEnabled = false;
    private float activeTubeDrive = 0f;
    private boolean activeCrossfeed = false;
    private boolean activeReverb = false;
    private float activeReverbRoom = 0.78f;
    private float activeReverbDamp = 0.35f;
    private float activeReverbMix = 0.24f;
    private float padMakeupGain = 1.0f;

    private boolean activeMultiband = false;
    private float activeMultibandDrive = 0f;
    private boolean activeDeEsser = false;
    private float activeDeEsserAmount = 0f;
    private boolean activeVocalRemover = false;
    private float activeVocalRemoverAmt = 0f;
    private int activeStutterDiv = 0;
    private int targetStutterFrames = 0;
    private boolean activeUltraHd4k = true;
    private boolean activeAntiCrackle = true;
    private boolean activeSpeakerOutput = true;

    private boolean anyDspActive = true;
    private int configuredSampleRate = 44100;

    // Real-time dynamics state (audio thread)
    private float compEnv = 0f;
    private float gateEnv = 0f;
    private float gateGain = 1.0f;
    private boolean gateIsOpen = true;
    private float nrExpanderGain = 1.0f;
    private float autoGainRms = 0.20f;
    private float autoGainMultiplier = 1.0f;
    private final float[] dcPrevIn = new float[MAX_CHANNELS];
    private final float[] dcPrevOut = new float[MAX_CHANNELS];
    private float compAttackCoeff = 0.01f;
    private float compReleaseCoeff = 0.0005f;
    private float gateAttackCoeff = 0.02f;
    private float gateReleaseCoeff = 0.0004f;
    private float autoGainSmoothCoeff = 0.00002f;
    private float limEnv = 0f;
    private float limAttackCoeff = 0.14f;
    private float limReleaseCoeff = 0.00035f;
    private float hdAirAlpha = 0.48f;
    private float hdAirLpL = 0f;
    private float hdAirLpR = 0f;

    // 3-Band Multiband Crossover + De-Esser + Vocal-Bass Split state (all 1-pole cascade / minimal CPU)
    private float xoverLowAlpha = 0.03f;
    private float xoverHighAlpha = 0.36f;
    private float xoverVocalBassAlpha = 0.019f;
    private float deEssHpAlpha = 0.65f;
    private float mbLp1L = 0f, mbLp2L = 0f, mbLp1R = 0f, mbLp2R = 0f;
    private float mbHp1L = 0f, mbHp2L = 0f, mbHp1R = 0f, mbHp2R = 0f;
    private float mbEnvLow = 0f, mbEnvMid = 0f, mbEnvHigh = 0f;
    private float deEssLpL = 0f, deEssLpR = 0f, deEssEnv = 0f, deEssGain = 1.0f;
    private float vocBassLpL = 0f, vocBassLpR = 0f;

    // Lazy-allocated BPM Beat Repeat / Stutter buffer (0 RAM until Stutter is triggered)
    private float[] stutterBufL = null;
    private float[] stutterBufR = null;
    private int stutterCapFrames = 0;
    private int stutterWritePos = 0;
    private int stutterReadPos = 0;
    private int stutterCapturedFrames = 0;
    private int prevActiveStutterDiv = 0;

    // Binaural Headphone Crossfeed buffers (16-sample ITD delay + 720Hz 1-pole LPF)
    private static final int CROSSFEED_DELAY_LEN = 16;
    private final float[] cfDelayL = new float[CROSSFEED_DELAY_LEN];
    private final float[] cfDelayR = new float[CROSSFEED_DELAY_LEN];
    private int cfDelayIdx = 0;
    private float cfFilterL = 0f;
    private float cfFilterR = 0f;
    private float cfLpfAlpha = 0.09f;

    // Lazy-allocated Schroeder/Freeverb stereo reverb (0 RAM unless Reverb is enabled)
    private static final int[] COMB_LEN_L = {1116, 1188, 1277, 1356};
    private static final int[] COMB_LEN_R = {1139, 1211, 1300, 1379};
    private static final int[] ALLPASS_LEN_L = {556, 441};
    private static final int[] ALLPASS_LEN_R = {579, 464};
    private final float[][] combBufL = new float[4][];
    private final float[][] combBufR = new float[4][];
    private final int[] combIdxL = new int[4];
    private final int[] combIdxR = new int[4];
    private final float[] combFilterL = new float[4];
    private final float[] combFilterR = new float[4];
    private final float[][] allpassBufL = new float[2][];
    private final float[][] allpassBufR = new float[2][];
    private final int[] allpassIdxL = new int[2];
    private final int[] allpassIdxR = new int[2];
    private boolean reverbBuffersInitialized = false;

    // Live telemetry
    private volatile boolean peakWarningLatched = false;
    private volatile boolean limiterEngagedLatched = false;
    private volatile float liveCompReductionDb = 0.0f;
    private volatile float liveAutoGainDb = 0.0f;
    private volatile float liveTubeGlow = 0.0f;
    private volatile float liveMbLowGrDb = 0.0f;
    private volatile float liveMbMidGrDb = 0.0f;
    private volatile float liveMbHighGrDb = 0.0f;
    private volatile float liveLimitingDb = 0.0f;

    public void updateParams(
            boolean enabled,
            float[] bandGainsDb,
            int bassPercent,
            int virtualizerPercent,
            int loudnessPercent) {
        updateParams(enabled, bandGainsDb, bassPercent, virtualizerPercent, loudnessPercent,
                this.balancePercent, this.spatial8dEnabled);
    }

    public void updateParams(
            boolean enabled,
            float[] bandGainsDb,
            int bassPercent,
            int virtualizerPercent,
            int loudnessPercent,
            int balancePercent,
            boolean spatial8dEnabled) {
        synchronized (paramLock) {
            this.eqEnabled = enabled;
            if (bandGainsDb != null) {
                int len = Math.min(BAND_COUNT, bandGainsDb.length);
                System.arraycopy(bandGainsDb, 0, this.targetGainsDb, 0, len);
            }
            this.bassPercent = Math.max(0, Math.min(200, bassPercent));
            this.virtualizerPercent = Math.max(0, Math.min(100, virtualizerPercent));
            this.loudnessPercent = Math.max(0, Math.min(100, loudnessPercent));
            this.balancePercent = Math.max(-100, Math.min(100, balancePercent));
            this.spatial8dEnabled = spatial8dEnabled;
            this.coeffsDirty = true;
        }
    }

    public void updatePurificationParams(
            float gainLDb,
            float gainRDb,
            float compressorRatio,
            float compressorThresholdDb,
            boolean noiseGateEnabled,
            boolean noiseReductionEnabled,
            float purificationBassDb,
            float trebleClarityDb,
            int stereoWidthPercent,
            float limiterCeilingDb,
            boolean autoGainEnabled,
            int hpfHz,
            int lpfHz) {
        synchronized (paramLock) {
            this.gainLDb = Math.max(-12f, Math.min(6f, gainLDb));
            this.gainRDb = Math.max(-12f, Math.min(6f, gainRDb));
            this.compressorRatio = Math.max(1f, Math.min(8f, compressorRatio));
            this.compressorThresholdDb = Math.max(-30f, Math.min(0f, compressorThresholdDb));
            this.noiseGateEnabled = noiseGateEnabled;
            this.noiseReductionEnabled = noiseReductionEnabled;
            this.purificationBassDb = Math.max(0f, Math.min(12f, purificationBassDb));
            this.trebleClarityDb = Math.max(0f, Math.min(12f, trebleClarityDb));
            this.stereoWidthPercent = Math.max(0, Math.min(200, stereoWidthPercent));
            this.limiterCeilingDb = Math.max(-12f, Math.min(0f, limiterCeilingDb));
            this.autoGainEnabled = autoGainEnabled;
            this.hpfHz = Math.max(20, Math.min(250, hpfHz));
            this.lpfHz = Math.max(8000, Math.min(22000, lpfHz));
            this.coeffsDirty = true;
        }
    }

    public void updateStudioParams(
            boolean masterBypass,
            boolean tubeEnabled,
            int tubeDrivePercent,
            int tapeWarmthPercent,
            boolean headphoneCrossfeedEnabled,
            boolean reverbEnabled,
            int reverbRoomPercent,
            int reverbDampPercent,
            int reverbMixPercent,
            int djFilterPercent,
            int activePadMode) {
        synchronized (paramLock) {
            this.masterBypass = masterBypass;
            this.tubeEnabled = tubeEnabled;
            this.tubeDrivePercent = Math.max(0, Math.min(100, tubeDrivePercent));
            this.tapeWarmthPercent = Math.max(0, Math.min(100, tapeWarmthPercent));
            this.headphoneCrossfeedEnabled = headphoneCrossfeedEnabled;
            this.reverbEnabled = reverbEnabled;
            this.reverbRoomPercent = Math.max(0, Math.min(100, reverbRoomPercent));
            this.reverbDampPercent = Math.max(0, Math.min(100, reverbDampPercent));
            this.reverbMixPercent = Math.max(0, Math.min(100, reverbMixPercent));
            this.djFilterPercent = Math.max(-100, Math.min(100, djFilterPercent));
            this.activePadMode = Math.max(PAD_NONE, Math.min(PAD_LOFI_RADIO, activePadMode));
            this.coeffsDirty = true;
        }
    }

    public void updateAdvancedMasteringParams(
            boolean multibandEnabled,
            int multibandDrivePercent,
            int deEsserPercent,
            int vocalRemoverPercent,
            boolean harmanTargetEnabled,
            int stutterDiv,
            float stutterBpm) {
        synchronized (paramLock) {
            this.multibandEnabled = multibandEnabled;
            this.multibandDrivePercent = Math.max(0, Math.min(100, multibandDrivePercent));
            this.deEsserPercent = Math.max(0, Math.min(100, deEsserPercent));
            this.vocalRemoverPercent = Math.max(0, Math.min(100, vocalRemoverPercent));
            this.harmanTargetEnabled = harmanTargetEnabled;
            this.stutterDiv = Math.max(0, Math.min(3, stutterDiv));
            this.stutterBpm = Math.max(60f, Math.min(190f, stutterBpm));
            this.coeffsDirty = true;
        }
    }

    public void updatePurifier4kAndProtection(
            boolean ultraHd4kEnabled,
            boolean antiCrackleEnabled,
            boolean speakerOutputMode) {
        synchronized (paramLock) {
            this.ultraHd4kEnabled = ultraHd4kEnabled;
            this.antiCrackleEnabled = antiCrackleEnabled;
            this.speakerOutputMode = speakerOutputMode;
            this.coeffsDirty = true;
        }
    }

    public boolean consumePeakWarning() {
        boolean triggered = peakWarningLatched;
        peakWarningLatched = false;
        return triggered;
    }

    public boolean consumeLimiterEngaged() {
        boolean engaged = limiterEngagedLatched;
        limiterEngagedLatched = false;
        return engaged;
    }

    public float getLiveCompReductionDb() {
        return liveCompReductionDb;
    }

    public float getLiveLimitingDb() {
        return liveLimitingDb;
    }

    public float getLiveAutoGainDb() {
        return liveAutoGainDb;
    }

    public float getLiveTubeGlow() {
        return liveTubeGlow;
    }

    public float getLiveMbLowGrDb() {
        return liveMbLowGrDb;
    }

    public float getLiveMbMidGrDb() {
        return liveMbMidGrDb;
    }

    public float getLiveMbHighGrDb() {
        return liveMbHighGrDb;
    }

    public boolean isNoiseGateCurrentlyOpen() {
        return gateIsOpen;
    }

    /**
     * Computes exact combined logarithmic magnitude response in dB across all active biquad filters.
     * Cached by coeffVersion so steady-state playback burns 0 CPU cycles recomputing trig curves.
     */
    public void computeFrequencyResponseDb(float[] outDb, float minHz, float maxHz) {
        if (outDb == null || outDb.length == 0) return;
        rebuildCoefficientsIfNeeded();
        int count = outDb.length;
        int curVer = coeffVersion;
        if (count == 48 && cachedCurveVersion48 == curVer && minHz == 31f && maxHz == 16000f) {
            System.arraycopy(cachedCurve48, 0, outDb, 0, 48);
            return;
        }
        if (count == 64 && cachedCurveVersion64 == curVer && minHz == 31f && maxHz == 16000f) {
            System.arraycopy(cachedCurve64, 0, outDb, 0, 64);
            return;
        }
        if (!anyDspActive) {
            java.util.Arrays.fill(outDb, 0f);
        } else {
            int sr = Math.max(8000, configuredSampleRate);
            double ratio = maxHz / Math.max(10f, minHz);
            for (int p = 0; p < count; p++) {
                double norm = count > 1 ? (double) p / (count - 1) : 0.0;
                double freq = minHz * Math.pow(ratio, norm);
                double w = 2.0 * Math.PI * freq / sr;
                double cosW = Math.cos(w);
                double sinW = Math.sin(w);
                double cos2W = cosW * cosW - sinW * sinW;
                double sin2W = 2.0 * sinW * cosW;

                double totalDb = 0.0;
                for (int f = 0; f < FILTER_COUNT; f++) {
                    if (!filterActive[f]) continue;
                    double numRe = b0[f] + b1[f] * cosW + b2[f] * cos2W;
                    double numIm = -(b1[f] * sinW + b2[f] * sin2W);
                    double denRe = 1.0 + a1[f] * cosW + a2[f] * cos2W;
                    double denIm = -(a1[f] * sinW + a2[f] * sin2W);
                    double magSq = (numRe * numRe + numIm * numIm) / Math.max(1e-12, denRe * denRe + denIm * denIm);
                    totalDb += 10.0 * Math.log10(Math.max(1e-6, magSq));
                }
                outDb[p] = (float) Math.max(-24.0, Math.min(24.0, totalDb));
            }
        }
        if (count == 48 && minHz == 31f && maxHz == 16000f) {
            System.arraycopy(outDb, 0, cachedCurve48, 0, 48);
            cachedCurveVersion48 = curVer;
        } else if (count == 64 && minHz == 31f && maxHz == 16000f) {
            System.arraycopy(outDb, 0, cachedCurve64, 0, 64);
            cachedCurveVersion64 = curVer;
        }
    }

    @Override
    protected AudioFormat onConfigure(AudioFormat inputAudioFormat) throws UnhandledAudioFormatException {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT
                && inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT) {
            throw new UnhandledAudioFormatException(inputAudioFormat);
        }
        configuredSampleRate = Math.max(8000, inputAudioFormat.sampleRate);
        updateEnvelopeTimeConstants(configuredSampleRate);
        coeffsDirty = true;
        return inputAudioFormat;
    }

    private void ensureReverbBuffers() {
        if (reverbBuffersInitialized) return;
        for (int i = 0; i < 4; i++) {
            combBufL[i] = new float[COMB_LEN_L[i]];
            combBufR[i] = new float[COMB_LEN_R[i]];
        }
        for (int i = 0; i < 2; i++) {
            allpassBufL[i] = new float[ALLPASS_LEN_L[i]];
            allpassBufR[i] = new float[ALLPASS_LEN_R[i]];
        }
        reverbBuffersInitialized = true;
    }

    private void ensureStutterBuffers(int sr) {
        int needed = Math.max(8000, sr / 2); // 0.50s stereo ring max (~190KB total)
        if (stutterBufL != null && stutterCapFrames >= needed) return;
        stutterCapFrames = needed;
        stutterBufL = new float[needed];
        stutterBufR = new float[needed];
    }

    private void updateEnvelopeTimeConstants(int sr) {
        compAttackCoeff = 1.0f - (float) Math.exp(-1.0 / (0.005 * sr));
        compReleaseCoeff = 1.0f - (float) Math.exp(-1.0 / (0.085 * sr));
        gateAttackCoeff = 1.0f - (float) Math.exp(-1.0 / (0.003 * sr));
        gateReleaseCoeff = 1.0f - (float) Math.exp(-1.0 / (0.095 * sr));
        autoGainSmoothCoeff = 1.0f - (float) Math.exp(-1.0 / (1.15 * sr));
        limAttackCoeff = 1.0f - (float) Math.exp(-1.0 / (0.00018 * sr));
        limReleaseCoeff = 1.0f - (float) Math.exp(-1.0 / (0.075 * sr));
        hdAirAlpha = 1.0f - (float) Math.exp(-2.0 * Math.PI * 4800.0 / sr);
        cfLpfAlpha = 1.0f - (float) Math.exp(-2.0 * Math.PI * 720.0 / sr);
        xoverLowAlpha = 1.0f - (float) Math.exp(-2.0 * Math.PI * 220.0 / sr);
        xoverHighAlpha = 1.0f - (float) Math.exp(-2.0 * Math.PI * 3200.0 / sr);
        xoverVocalBassAlpha = 1.0f - (float) Math.exp(-2.0 * Math.PI * 135.0 / sr);
        deEssHpAlpha = 1.0f - (float) Math.exp(-2.0 * Math.PI * 6200.0 / sr);
    }

    @Override
    protected void onFlush() {
        resetFilterStates();
        spatialPhaseRad = 0.0;
        peakWarningLatched = false;
        limiterEngagedLatched = false;
        liveCompReductionDb = 0f;
        liveLimitingDb = 0f;
        liveTubeGlow = 0f;
        liveMbLowGrDb = 0f;
        liveMbMidGrDb = 0f;
        liveMbHighGrDb = 0f;
    }

    @Override
    protected void onReset() {
        resetFilterStates();
        spatialPhaseRad = 0.0;
        peakWarningLatched = false;
        limiterEngagedLatched = false;
        liveCompReductionDb = 0f;
        liveLimitingDb = 0f;
        liveTubeGlow = 0f;
        liveMbLowGrDb = 0f;
        liveMbMidGrDb = 0f;
        liveMbHighGrDb = 0f;
    }

    private void resetFilterStates() {
        for (int f = 0; f < FILTER_COUNT; f++) {
            for (int c = 0; c < MAX_CHANNELS; c++) {
                z1[f][c] = 0f;
                z2[f][c] = 0f;
            }
        }
        for (int c = 0; c < MAX_CHANNELS; c++) {
            dcPrevIn[c] = 0f;
            dcPrevOut[c] = 0f;
        }
        mbLp1L = mbLp2L = mbLp1R = mbLp2R = 0f;
        mbHp1L = mbHp2L = mbHp1R = mbHp2R = 0f;
        mbEnvLow = mbEnvMid = mbEnvHigh = 0f;
        deEssLpL = deEssLpR = deEssEnv = 0f;
        deEssGain = 1.0f;
        vocBassLpL = vocBassLpR = 0f;
        hdAirLpL = hdAirLpR = 0f;
        limEnv = 0f;
        stutterWritePos = 0;
        stutterReadPos = 0;
        stutterCapturedFrames = 0;
        prevActiveStutterDiv = 0;
        java.util.Arrays.fill(cfDelayL, 0f);
        java.util.Arrays.fill(cfDelayR, 0f);
        cfDelayIdx = 0;
        cfFilterL = 0f;
        cfFilterR = 0f;
        if (reverbBuffersInitialized) {
            for (int i = 0; i < 4; i++) {
                java.util.Arrays.fill(combBufL[i], 0f);
                java.util.Arrays.fill(combBufR[i], 0f);
                combIdxL[i] = 0;
                combIdxR[i] = 0;
                combFilterL[i] = 0f;
                combFilterR[i] = 0f;
            }
            for (int i = 0; i < 2; i++) {
                java.util.Arrays.fill(allpassBufL[i], 0f);
                java.util.Arrays.fill(allpassBufR[i], 0f);
                allpassIdxL[i] = 0;
                allpassIdxR[i] = 0;
            }
        }
        compEnv = 0f;
        gateEnv = 0f;
        gateGain = 1.0f;
        nrExpanderGain = 1.0f;
        autoGainRms = 0.20f;
        autoGainMultiplier = 1.0f;
    }

    private void rebuildCoefficientsIfNeeded() {
        if (!coeffsDirty) return;

        boolean localEq;
        float[] localBands = new float[BAND_COUNT];
        int localBass;
        int localVirt;
        int localLoud;
        int localBalance;
        boolean local8d;
        float localGainL;
        float localGainR;
        float localCompRatio;
        float localCompThresh;
        boolean localGate;
        boolean localNr;
        float localPurifyBass;
        float localTrebleClarity;
        int localStereoWidth;
        float localLimiterDb;
        boolean localAutoGain;
        int localHpf;
        int localLpf;

        boolean localBypass;
        boolean localTubeOn;
        int localTubeDrive;
        int localTapeWarmth;
        boolean localCrossfeed;
        boolean localReverb;
        int localRevRoom;
        int localRevDamp;
        int localRevMix;
        int localDjFilter;
        int localPadMode;

        boolean localMultiband;
        int localMbDrive;
        int localDeEsser;
        int localVocalRem;
        boolean localHarman;
        int localStutterDiv;
        float localStutterBpm;
        boolean local4k;
        boolean localAntiCrackle;
        boolean localSpeaker;

        synchronized (paramLock) {
            localEq = eqEnabled;
            System.arraycopy(targetGainsDb, 0, localBands, 0, BAND_COUNT);
            localBass = bassPercent;
            localVirt = virtualizerPercent;
            localLoud = loudnessPercent;
            localBalance = balancePercent;
            local8d = spatial8dEnabled;
            localGainL = gainLDb;
            localGainR = gainRDb;
            localCompRatio = compressorRatio;
            localCompThresh = compressorThresholdDb;
            localGate = noiseGateEnabled;
            localNr = noiseReductionEnabled;
            localPurifyBass = purificationBassDb;
            localTrebleClarity = trebleClarityDb;
            localStereoWidth = stereoWidthPercent;
            localLimiterDb = limiterCeilingDb;
            localAutoGain = autoGainEnabled;
            localHpf = hpfHz;
            localLpf = lpfHz;
            localBypass = masterBypass;
            localTubeOn = tubeEnabled;
            localTubeDrive = tubeDrivePercent;
            localTapeWarmth = tapeWarmthPercent;
            localCrossfeed = headphoneCrossfeedEnabled;
            localReverb = reverbEnabled;
            localRevRoom = reverbRoomPercent;
            localRevDamp = reverbDampPercent;
            localRevMix = reverbMixPercent;
            localDjFilter = djFilterPercent;
            localPadMode = activePadMode;
            localMultiband = multibandEnabled;
            localMbDrive = multibandDrivePercent;
            localDeEsser = deEsserPercent;
            localVocalRem = vocalRemoverPercent;
            localHarman = harmanTargetEnabled;
            localStutterDiv = stutterDiv;
            localStutterBpm = stutterBpm;
            local4k = ultraHd4kEnabled;
            localAntiCrackle = antiCrackleEnabled;
            localSpeaker = speakerOutputMode;
            coeffsDirty = false;
            coeffVersion++;
        }

        if (localBypass) {
            for (int f = 0; f < FILTER_COUNT; f++) {
                filterActive[f] = false;
            }
            activeEq = false;
            activeSpatial8d = false;
            activeNoiseGate = false;
            activeNoiseReduction = false;
            activeCompRatio = 1.0f;
            activeAutoGain = false;
            activeTubeEnabled = false;
            activeTubeDrive = 0f;
            activeCrossfeed = false;
            activeReverb = false;
            activeMultiband = false;
            activeDeEsser = false;
            activeVocalRemover = false;
            activeStutterDiv = 0;
            activeUltraHd4k = false;
            activeAntiCrackle = false;
            stereoWidthMultiplier = 1.0f;
            preAmpGain = 1.0f;
            leftChannelLinearGain = 1.0f;
            rightChannelLinearGain = 1.0f;
            activeLimiterCeilingLinear = 1.0f;
            padMakeupGain = 1.0f;
            anyDspActive = false;
            return;
        }

        activeUltraHd4k = local4k;
        activeAntiCrackle = localAntiCrackle;
        activeSpeakerOutput = localSpeaker;

        int sr = Math.max(8000, configuredSampleRate);
        activeEq = localEq;
        activeSpatial8d = local8d;
        activeCompRatio = (localPadMode == PAD_NIGHT_MODE) ? Math.max(localCompRatio, 5.5f) : localCompRatio;
        activeCompThreshDb = (localPadMode == PAD_NIGHT_MODE) ? Math.min(localCompThresh, -22.0f) : localCompThresh;
        activeNoiseGate = localGate;
        activeNoiseReduction = localNr;
        activeAutoGain = localAutoGain;
        float effLimiterDb = (localPadMode == PAD_NIGHT_MODE) ? Math.min(localLimiterDb, -2.5f) : localLimiterDb;
        activeLimiterCeilingLinear = (float) Math.pow(10.0, Math.max(-12f, Math.min(0f, effLimiterDb)) / 20.0);

        activeTubeEnabled = localTubeOn || (localPadMode == PAD_LOFI_RADIO);
        activeTubeDrive = activeTubeEnabled
                ? (localPadMode == PAD_LOFI_RADIO ? Math.max(0.45f, localTubeDrive / 100f) : (localTubeDrive / 100f))
                : 0f;
        activeCrossfeed = localCrossfeed;
        activeReverb = localReverb && localRevMix > 0;
        activeReverbRoom = 0.68f + (localRevRoom / 100f) * 0.27f;
        activeReverbDamp = 0.12f + (localRevDamp / 100f) * 0.68f;
        activeReverbMix = (localRevMix / 100f) * 0.55f;
        padMakeupGain = (localPadMode == PAD_NIGHT_MODE) ? 1.48f : 1.0f;

        activeMultiband = localMultiband && localMbDrive > 0;
        activeMultibandDrive = localMbDrive / 100f;
        activeDeEsser = localDeEsser > 4;
        activeDeEsserAmount = localDeEsser / 100f;
        activeVocalRemover = localVocalRem > 4;
        activeVocalRemoverAmt = localVocalRem / 100f;
        activeStutterDiv = localStutterDiv;
        if (activeStutterDiv > 0) {
            float beatSec = 60.0f / Math.max(60f, localStutterBpm);
            float divScale = activeStutterDiv == 1 ? 0.25f : (activeStutterDiv == 2 ? 0.125f : 0.0625f);
            int maxSafe = Math.max(2048, sr / 2 - 1);
            targetStutterFrames = Math.max(512, Math.min(maxSafe, Math.round(sr * beatSec * divScale * 4f)));
        } else {
            targetStutterFrames = 0;
        }

        // Combine Virtualizer (0..100%) and Purification Stereo Width (0..200%) cleanly
        float widthFromSuite = localStereoWidth / 100.0f; // 0..2.0 (1.0 = normal)
        float virtExtra = localEq ? (localVirt / 100.0f) * 0.25f : 0f;
        stereoWidthMultiplier = localEq ? Math.max(0f, Math.min(2.25f, widthFromSuite + virtExtra)) : 1.0f;
        if (localPadMode == PAD_VOCAL_FOCUS) {
            stereoWidthMultiplier = Math.min(stereoWidthMultiplier, 0.42f);
        }

        float leftBal = localBalance > 0 ? Math.max(0f, 1.0f - (localBalance / 100.0f)) : 1.0f;
        float rightBal = localBalance < 0 ? Math.max(0f, 1.0f + (localBalance / 100.0f)) : 1.0f;
        leftChannelLinearGain = leftBal * (float) Math.pow(10.0, localGainL / 20.0);
        rightChannelLinearGain = rightBal * (float) Math.pow(10.0, localGainR / 20.0);

        // Pre-amp from Loudness slider
        float loudDb = localEq ? (localLoud / 100f) * 4.5f : 0f;
        preAmpGain = (float) Math.pow(10.0, loudDb / 20.0);

        boolean anyFilter = false;
        // [0..9] 10-band Peaking Filters (31Hz..16kHz, -15dB..+15dB)
        for (int i = 0; i < BAND_COUNT; i++) {
            float gain = localEq ? Math.max(-15f, Math.min(15f, localBands[i])) : 0f;
            if (localEq && localBass < 100 && i <= 1) {
                gain *= (localBass / 100f);
            }
            if (Math.abs(gain) < 0.08f || BAND_FREQS_HZ[i] >= sr * 0.46f) {
                filterActive[i] = false;
            } else {
                computePeakingFilter(i, sr, BAND_FREQS_HZ[i], PEAK_Q, gain);
                filterActive[i] = true;
                anyFilter = true;
            }
        }

        // [10..11] Bass Slider 0%..200% -> Low-Shelf + Warm Punch
        if (!localEq || localBass == 100) {
            filterActive[IDX_BASS_SLIDER] = false;
            filterActive[IDX_SUB_WARMTH] = false;
        } else if (localBass < 100) {
            float cutRatio = 1f - (localBass / 100f);
            computeLowShelfFilter(IDX_BASS_SLIDER, sr, 105f, 0.78f, -16.5f * cutRatio);
            filterActive[IDX_BASS_SLIDER] = true;
            filterActive[IDX_SUB_WARMTH] = false;
            anyFilter = true;
        } else {
            float boostRatio = (localBass - 100f) / 100f;
            computeLowShelfFilter(IDX_BASS_SLIDER, sr, BASS_SHELF_HZ, 0.85f, 13.5f * boostRatio);
            filterActive[IDX_BASS_SLIDER] = true;
            if (boostRatio > 0.1f) {
                computePeakingFilter(IDX_SUB_WARMTH, sr, SUB_WARMTH_HZ, 1.15f, 4.2f * boostRatio);
                filterActive[IDX_SUB_WARMTH] = true;
            } else {
                filterActive[IDX_SUB_WARMTH] = false;
            }
            anyFilter = true;
        }

        // [12] Purification Suite HPF (Butterworth 2nd-order High-Pass)
        // When Anti-Crackle is active on Phone Speaker, enforces >=52Hz subsonic rattle protection
        int effectiveHpf = (localAntiCrackle && localSpeaker) ? Math.max(52, localHpf) : localHpf;
        if (effectiveHpf > 22 && effectiveHpf < sr * 0.40f) {
            computeHighPassFilter(IDX_HPF, sr, effectiveHpf, 0.7071f);
            filterActive[IDX_HPF] = true;
            anyFilter = true;
        } else {
            filterActive[IDX_HPF] = false;
        }

        // [13] Purification Suite LPF (Butterworth 2nd-order Low-Pass, active when < 21500Hz & below Nyquist)
        if (localLpf < 21500 && localLpf < sr * 0.47f) {
            computeLowPassFilter(IDX_LPF, sr, localLpf, 0.7071f);
            filterActive[IDX_LPF] = true;
            anyFilter = true;
        } else {
            filterActive[IDX_LPF] = false;
        }

        // [14] Purification Bass Boost Shelf (+0..+12dB at 86Hz)
        if (localPurifyBass >= 0.1f && localPadMode != PAD_BASS_KILL) {
            computeLowShelfFilter(IDX_PURIFY_BASS, sr, PURIFY_BASS_HZ, 0.84f, localPurifyBass);
            filterActive[IDX_PURIFY_BASS] = true;
            anyFilter = true;
        } else {
            filterActive[IDX_PURIFY_BASS] = false;
        }

        // [15] Purification Treble Clarity High-Shelf (+0..+12dB at 6.8kHz)
        if (localTrebleClarity >= 0.1f && CLARITY_HIGH_SHELF_HZ < sr * 0.46f) {
            computeHighShelfFilter(IDX_TREBLE_CLARITY, sr, CLARITY_HIGH_SHELF_HZ, 0.78f, localTrebleClarity);
            filterActive[IDX_TREBLE_CLARITY] = true;
            anyFilter = true;
        } else {
            filterActive[IDX_TREBLE_CLARITY] = false;
        }

        // [16] DJ Dual-Filter Sweep (-100 LPF .. 0 Flat .. +100 HPF)
        if (localDjFilter < -3) {
            float amt = Math.min(1f, (-localDjFilter - 3) / 97f);
            float cutoff = (float) (18000.0 * Math.pow(140.0 / 18000.0, amt));
            cutoff = Math.min(sr * 0.46f, Math.max(120f, cutoff));
            computeLowPassFilter(IDX_DJ_FILTER, sr, cutoff, 1.55f);
            filterActive[IDX_DJ_FILTER] = true;
            anyFilter = true;
        } else if (localDjFilter > 3) {
            float amt = Math.min(1f, (localDjFilter - 3) / 97f);
            float cutoff = (float) (32.0 * Math.pow(6200.0 / 32.0, amt));
            cutoff = Math.min(sr * 0.42f, Math.max(30f, cutoff));
            computeHighPassFilter(IDX_DJ_FILTER, sr, cutoff, 1.55f);
            filterActive[IDX_DJ_FILTER] = true;
            anyFilter = true;
        } else {
            filterActive[IDX_DJ_FILTER] = false;
        }

        // [17] Analog Tape Warmth gentle high-frequency rolloff
        if (localTubeOn && localTapeWarmth > 4 && 8500f < sr * 0.46f) {
            computeHighShelfFilter(IDX_TAPE_WARMTH, sr, 8500f, 0.65f, -4.5f * (localTapeWarmth / 100f));
            filterActive[IDX_TAPE_WARMTH] = true;
            anyFilter = true;
        } else {
            filterActive[IDX_TAPE_WARMTH] = false;
        }

        // [18..19] Performance Pad Mode Filters
        if (localPadMode == PAD_BASS_KILL) {
            computeHighPassFilter(IDX_PAD_A, sr, Math.min(sr * 0.35f, 185f), 0.85f);
            filterActive[IDX_PAD_A] = true;
            filterActive[IDX_PAD_B] = false;
            anyFilter = true;
        } else if (localPadMode == PAD_VOCAL_FOCUS) {
            computePeakingFilter(IDX_PAD_A, sr, Math.min(sr * 0.4f, 2400f), 0.9f, 5.0f);
            filterActive[IDX_PAD_A] = true;
            filterActive[IDX_PAD_B] = false;
            anyFilter = true;
        } else if (localPadMode == PAD_LOFI_RADIO) {
            computeHighPassFilter(IDX_PAD_A, sr, Math.min(sr * 0.35f, 360f), 0.8f);
            computeLowPassFilter(IDX_PAD_B, sr, Math.min(sr * 0.44f, 3600f), 0.9f);
            filterActive[IDX_PAD_A] = true;
            filterActive[IDX_PAD_B] = true;
            anyFilter = true;
        } else {
            filterActive[IDX_PAD_A] = false;
            filterActive[IDX_PAD_B] = false;
        }

        // [20..22] Harman Target Calibration Curve (+3.8dB sub-shelf @ 90Hz, -1.0dB 350Hz box cleanup, +2.4dB 3kHz presence)
        if (localHarman && 3200f < sr * 0.45f) {
            computeLowShelfFilter(IDX_HARMAN_LOW, sr, 90f, 0.82f, 3.8f);
            computePeakingFilter(IDX_HARMAN_MID, sr, 350f, 1.1f, -1.0f);
            computePeakingFilter(IDX_HARMAN_HIGH, sr, 3000f, 0.85f, 2.4f);
            filterActive[IDX_HARMAN_LOW] = true;
            filterActive[IDX_HARMAN_MID] = true;
            filterActive[IDX_HARMAN_HIGH] = true;
            anyFilter = true;
        } else {
            filterActive[IDX_HARMAN_LOW] = false;
            filterActive[IDX_HARMAN_MID] = false;
            filterActive[IDX_HARMAN_HIGH] = false;
        }

        // [23..24] 4K Ultra-HD Audio Purifier: clean 310Hz mud scoop + crystal 11.2kHz air shelf
        if (local4k && 11200f < sr * 0.46f) {
            computePeakingFilter(IDX_4K_BOX_CLEAN, sr, 310f, 1.15f, localSpeaker ? -1.8f : -1.2f);
            computeHighShelfFilter(IDX_4K_AIR_SHELF, sr, 11200f, 0.72f, localSpeaker ? 1.8f : 2.6f);
            filterActive[IDX_4K_BOX_CLEAN] = true;
            filterActive[IDX_4K_AIR_SHELF] = true;
            anyFilter = true;
        } else {
            filterActive[IDX_4K_BOX_CLEAN] = false;
            filterActive[IDX_4K_AIR_SHELF] = false;
        }

        anyDspActive = anyFilter
                || Math.abs(stereoWidthMultiplier - 1.0f) > 0.01f
                || Math.abs(preAmpGain - 1.0f) > 0.01f
                || Math.abs(leftChannelLinearGain - 1.0f) > 0.005f
                || Math.abs(rightChannelLinearGain - 1.0f) > 0.005f
                || Math.abs(padMakeupGain - 1.0f) > 0.01f
                || activeSpatial8d
                || activeNoiseGate
                || activeNoiseReduction
                || activeUltraHd4k
                || activeAntiCrackle
                || activeCompRatio > 1.02f
                || activeAutoGain
                || activeTubeEnabled
                || activeCrossfeed
                || activeReverb
                || activeMultiband
                || activeDeEsser
                || activeVocalRemover
                || activeStutterDiv > 0
                || activeLimiterCeilingLinear < 0.99f;
    }

    private void computePeakingFilter(int idx, int sr, float freqHz, float q, float gainDb) {
        double aAmp = Math.pow(10.0, gainDb / 40.0);
        double w0 = 2.0 * Math.PI * freqHz / sr;
        double cosW0 = Math.cos(w0);
        double sinW0 = Math.sin(w0);
        double alpha = sinW0 / (2.0 * q);

        double nb0 = 1.0 + alpha * aAmp;
        double nb1 = -2.0 * cosW0;
        double nb2 = 1.0 - alpha * aAmp;
        double na0 = 1.0 + alpha / aAmp;
        double na1 = -2.0 * cosW0;
        double na2 = 1.0 - alpha / aAmp;

        storeNormalizedBiquad(idx, nb0, nb1, nb2, na0, na1, na2);
    }

    private void computeLowShelfFilter(int idx, int sr, float freqHz, float slope, float gainDb) {
        double aAmp = Math.pow(10.0, gainDb / 40.0);
        double w0 = 2.0 * Math.PI * freqHz / sr;
        double cosW0 = Math.cos(w0);
        double sinW0 = Math.sin(w0);
        double alpha = sinW0 / 2.0 * Math.sqrt((aAmp + 1.0 / aAmp) * (1.0 / slope - 1.0) + 2.0);
        double twoSqrtAAlpha = 2.0 * Math.sqrt(aAmp) * alpha;

        double nb0 = aAmp * ((aAmp + 1.0) - (aAmp - 1.0) * cosW0 + twoSqrtAAlpha);
        double nb1 = 2.0 * aAmp * ((aAmp - 1.0) - (aAmp + 1.0) * cosW0);
        double nb2 = aAmp * ((aAmp + 1.0) - (aAmp - 1.0) * cosW0 - twoSqrtAAlpha);
        double na0 = (aAmp + 1.0) + (aAmp - 1.0) * cosW0 + twoSqrtAAlpha;
        double na1 = -2.0 * ((aAmp - 1.0) + (aAmp + 1.0) * cosW0);
        double na2 = (aAmp + 1.0) + (aAmp - 1.0) * cosW0 - twoSqrtAAlpha;

        storeNormalizedBiquad(idx, nb0, nb1, nb2, na0, na1, na2);
    }

    private void computeHighShelfFilter(int idx, int sr, float freqHz, float slope, float gainDb) {
        double aAmp = Math.pow(10.0, gainDb / 40.0);
        double w0 = 2.0 * Math.PI * freqHz / sr;
        double cosW0 = Math.cos(w0);
        double sinW0 = Math.sin(w0);
        double alpha = sinW0 / 2.0 * Math.sqrt((aAmp + 1.0 / aAmp) * (1.0 / slope - 1.0) + 2.0);
        double twoSqrtAAlpha = 2.0 * Math.sqrt(aAmp) * alpha;

        double nb0 = aAmp * ((aAmp + 1.0) + (aAmp - 1.0) * cosW0 + twoSqrtAAlpha);
        double nb1 = -2.0 * aAmp * ((aAmp - 1.0) + (aAmp + 1.0) * cosW0);
        double nb2 = aAmp * ((aAmp + 1.0) + (aAmp - 1.0) * cosW0 - twoSqrtAAlpha);
        double na0 = (aAmp + 1.0) - (aAmp - 1.0) * cosW0 + twoSqrtAAlpha;
        double na1 = 2.0 * ((aAmp - 1.0) - (aAmp + 1.0) * cosW0);
        double na2 = (aAmp + 1.0) - (aAmp - 1.0) * cosW0 - twoSqrtAAlpha;

        storeNormalizedBiquad(idx, nb0, nb1, nb2, na0, na1, na2);
    }

    private void computeHighPassFilter(int idx, int sr, float freqHz, float q) {
        double w0 = 2.0 * Math.PI * freqHz / sr;
        double cosW0 = Math.cos(w0);
        double sinW0 = Math.sin(w0);
        double alpha = sinW0 / (2.0 * q);

        double nb0 = (1.0 + cosW0) * 0.5;
        double nb1 = -(1.0 + cosW0);
        double nb2 = (1.0 + cosW0) * 0.5;
        double na0 = 1.0 + alpha;
        double na1 = -2.0 * cosW0;
        double na2 = 1.0 - alpha;

        storeNormalizedBiquad(idx, nb0, nb1, nb2, na0, na1, na2);
    }

    private void computeLowPassFilter(int idx, int sr, float freqHz, float q) {
        double w0 = 2.0 * Math.PI * freqHz / sr;
        double cosW0 = Math.cos(w0);
        double sinW0 = Math.sin(w0);
        double alpha = sinW0 / (2.0 * q);

        double nb0 = (1.0 - cosW0) * 0.5;
        double nb1 = 1.0 - cosW0;
        double nb2 = (1.0 - cosW0) * 0.5;
        double na0 = 1.0 + alpha;
        double na1 = -2.0 * cosW0;
        double na2 = 1.0 - alpha;

        storeNormalizedBiquad(idx, nb0, nb1, nb2, na0, na1, na2);
    }

    private void storeNormalizedBiquad(int idx, double nb0, double nb1, double nb2, double na0, double na1, double na2) {
        double invA0 = 1.0 / na0;
        b0[idx] = (float) (nb0 * invA0);
        b1[idx] = (float) (nb1 * invA0);
        b2[idx] = (float) (nb2 * invA0);
        a1[idx] = (float) (na1 * invA0);
        a2[idx] = (float) (na2 * invA0);
    }

    @Override
    public void queueInput(ByteBuffer inputBuffer) {
        int remaining = inputBuffer.remaining();
        if (remaining <= 0) return;

        rebuildCoefficientsIfNeeded();

        ByteBuffer out = replaceOutputBuffer(remaining);
        if (!anyDspActive) {
            out.put(inputBuffer);
            out.flip();
            liveCompReductionDb = 0f;
            liveAutoGainDb = 0f;
            return;
        }

        int channels = Math.min(MAX_CHANNELS, Math.max(1, inputAudioFormat.channelCount));
        boolean isFloat = inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT;
        int bytesPerSample = isFloat ? 4 : 2;
        int frameBytes = channels * bytesPerSample;
        int frames = remaining / frameBytes;

        ByteOrder origInOrder = inputBuffer.order();
        inputBuffer.order(ByteOrder.LITTLE_ENDIAN);
        out.order(ByteOrder.LITTLE_ENDIAN);

        int inPos = inputBuffer.position();

        if (channels == 2) {
            double phaseStep = activeSpatial8d ? (2.0 * Math.PI * 0.16 / Math.max(8000, configuredSampleRate)) : 0.0;
            float maxReductionDb = 0f;
            float maxLimiterGrDb = 0f;
            float peakTubeLevel = 0f;
            float maxMbLowGr = 0f;
            float maxMbMidGr = 0f;
            float maxMbHighGr = 0f;
            if (activeReverb && !reverbBuffersInitialized) {
                ensureReverbBuffers();
            }
            if (activeStutterDiv > 0) {
                ensureStutterBuffers(configuredSampleRate);
                if (prevActiveStutterDiv == 0) {
                    stutterWritePos = 0;
                    stutterReadPos = 0;
                    stutterCapturedFrames = 0;
                }
            } else {
                stutterCapturedFrames = 0;
                stutterWritePos = 0;
                stutterReadPos = 0;
            }
            prevActiveStutterDiv = activeStutterDiv;

            boolean needDcClean = activeNoiseReduction || activeUltraHd4k || activeAntiCrackle;

            for (int f = 0; f < frames; f++) {
                float l;
                float r;
                if (isFloat) {
                    l = inputBuffer.getFloat(inPos);
                    r = inputBuffer.getFloat(inPos + 4);
                } else {
                    l = inputBuffer.getShort(inPos) / 32768.0f;
                    r = inputBuffer.getShort(inPos + 2) / 32768.0f;
                }
                inPos += frameBytes;

                // 0. BPM Beat Repeat / Stutter Loop Grain (0 CPU/RAM when OFF)
                if (activeStutterDiv > 0 && stutterBufL != null && targetStutterFrames > 0) {
                    if (stutterCapturedFrames < targetStutterFrames) {
                        stutterBufL[stutterWritePos] = l;
                        stutterBufR[stutterWritePos] = r;
                        stutterWritePos++;
                        stutterCapturedFrames++;
                    } else {
                        int idx = stutterReadPos % targetStutterFrames;
                        float loopL = stutterBufL[idx];
                        float loopR = stutterBufR[idx];
                        // Micro 96-sample crossfade at grain boundary to prevent clicks
                        if (idx < 96 && targetStutterFrames > 192) {
                            float fade = idx / 96.0f;
                            int tailIdx = targetStutterFrames - 96 + idx;
                            loopL = loopL * fade + stutterBufL[tailIdx] * (1.0f - fade);
                            loopR = loopR * fade + stutterBufR[tailIdx] * (1.0f - fade);
                        }
                        l = loopL;
                        r = loopR;
                        stutterReadPos = (idx + 1) % targetStutterFrames;
                    }
                }

                // 1. Subsonic DC Blocker & Rumble Purifier (removes sub-18Hz mud & offset)
                if (needDcClean) {
                    float dcL = l - dcPrevIn[0] + 0.996f * dcPrevOut[0];
                    dcPrevIn[0] = l;
                    dcPrevOut[0] = dcL;
                    l = dcL;

                    float dcR = r - dcPrevIn[1] + 0.996f * dcPrevOut[1];
                    dcPrevIn[1] = r;
                    dcPrevOut[1] = dcR;
                    r = dcR;
                }

                // 2. Biquad Chain: 10-Band EQ + Bass + HPF + LPF + Bass Boost + Clarity + DJ Filter + Tape + Pads + Harman
                for (int idx = 0; idx < FILTER_COUNT; idx++) {
                    if (!filterActive[idx]) continue;
                    float fb0 = b0[idx], fb1 = b1[idx], fb2 = b2[idx], fa1 = a1[idx], fa2 = a2[idx];

                    float yL = fb0 * l + z1[idx][0];
                    z1[idx][0] = fb1 * l - fa1 * yL + z2[idx][0];
                    z2[idx][0] = fb2 * l - fa2 * yL;
                    l = yL;

                    float yR = fb0 * r + z1[idx][1];
                    z1[idx][1] = fb1 * r - fa1 * yR + z2[idx][1];
                    z2[idx][1] = fb2 * r - fa2 * yR;
                    r = yR;
                }

                // 2b. Analog Tube & Tape Asymmetric Triode Saturation
                if (activeTubeEnabled && activeTubeDrive > 0.02f) {
                    float driveAmt = activeTubeDrive * 2.4f;
                    l = saturateTriode(l, driveAmt);
                    r = saturateTriode(r, driveAmt);
                    float lvl = 0.5f * (Math.abs(l) + Math.abs(r));
                    if (lvl > peakTubeLevel) peakTubeLevel = lvl;
                }

                // 2c. Smart Karaoke Vocal Remover with Sub-Bass Preservation (<135Hz stays full strength)
                if (activeVocalRemover) {
                    vocBassLpL += (l - vocBassLpL) * xoverVocalBassAlpha;
                    vocBassLpR += (r - vocBassLpR) * xoverVocalBassAlpha;
                    float hiL = l - vocBassLpL;
                    float hiR = r - vocBassLpR;
                    float midHi = 0.5f * (hiL + hiR);
                    float sideHi = 0.5f * (hiL - hiR);
                    float keepCenter = 1.0f - activeVocalRemoverAmt * 0.92f;
                    float sideBoost = 1.0f + activeVocalRemoverAmt * 0.25f;
                    l = vocBassLpL + midHi * keepCenter + sideHi * sideBoost;
                    r = vocBassLpR + midHi * keepCenter - sideHi * sideBoost;
                }

                float detectorPeak = Math.max(Math.abs(l), Math.abs(r));

                // 3. Real Noise Gate (-45 dBFS open / -51 dBFS close with hysteresis)
                if (activeNoiseGate) {
                    float gCoeff = detectorPeak > gateEnv ? gateAttackCoeff : gateReleaseCoeff;
                    gateEnv += (detectorPeak - gateEnv) * gCoeff;
                    if (gateEnv > 0.0056f) {
                        gateIsOpen = true;
                    } else if (gateEnv < 0.0028f) {
                        gateIsOpen = false;
                    }
                    float targetGate = gateIsOpen ? 1.0f : 0.03f;
                    gateGain += (targetGate - gateGain) * (gateIsOpen ? gateAttackCoeff : gateReleaseCoeff);
                    l *= gateGain;
                    r *= gateGain;
                } else {
                    gateIsOpen = true;
                    gateGain = 1.0f;
                }

                // 4. Noise Reduction Stage 2: Smooth downward noise-floor expander below -38 dBFS
                if (activeNoiseReduction) {
                    float targetNr = detectorPeak < 0.012f
                            ? Math.max(0.28f, detectorPeak / 0.012f)
                            : 1.0f;
                    nrExpanderGain += (targetNr - nrExpanderGain) * 0.0025f;
                    l *= nrExpanderGain;
                    r *= nrExpanderGain;
                }

                // 5. Real Stereo-Linked Dynamic Range Compressor (with fast IEEE-754 log/pow math)
                if (activeCompRatio > 1.02f) {
                    float cCoeff = detectorPeak > compEnv ? compAttackCoeff : compReleaseCoeff;
                    compEnv += (detectorPeak - compEnv) * cCoeff;
                    if (compEnv > 0.0005f) {
                        float envDb = 20.0f * fastLog10(compEnv);
                        if (envDb > activeCompThreshDb) {
                            float overDb = envDb - activeCompThreshDb;
                            float reductionDb = overDb * (1.0f - 1.0f / activeCompRatio);
                            if (reductionDb > maxReductionDb) maxReductionDb = reductionDb;
                            float compGain = fastPow10(-reductionDb * 0.05f);
                            l *= compGain;
                            r *= compGain;
                        }
                    }
                }

                // 5b. 3-Band Linkwitz-Riley Multiband Compressor (220Hz & 3.2kHz)
                if (activeMultiband) {
                    mbLp1L += (l - mbLp1L) * xoverLowAlpha;
                    mbLp2L += (mbLp1L - mbLp2L) * xoverLowAlpha;
                    float lowL = mbLp2L;

                    mbLp1R += (r - mbLp1R) * xoverLowAlpha;
                    mbLp2R += (mbLp1R - mbLp2R) * xoverLowAlpha;
                    float lowR = mbLp2R;

                    float remL = l - lowL;
                    float remR = r - lowR;

                    mbHp1L += (remL - mbHp1L) * xoverHighAlpha;
                    mbHp2L += (mbHp1L - mbHp2L) * xoverHighAlpha;
                    float midL = mbHp2L;
                    float highL = remL - midL;

                    mbHp1R += (remR - mbHp1R) * xoverHighAlpha;
                    mbHp2R += (mbHp1R - mbHp2R) * xoverHighAlpha;
                    float midR = mbHp2R;
                    float highR = remR - midR;

                    float pLow = Math.max(Math.abs(lowL), Math.abs(lowR));
                    float pMid = Math.max(Math.abs(midL), Math.abs(midR));
                    float pHigh = Math.max(Math.abs(highL), Math.abs(highR));

                    mbEnvLow += (pLow - mbEnvLow) * (pLow > mbEnvLow ? compAttackCoeff : compReleaseCoeff);
                    mbEnvMid += (pMid - mbEnvMid) * (pMid > mbEnvMid ? compAttackCoeff : compReleaseCoeff);
                    mbEnvHigh += (pHigh - mbEnvHigh) * (pHigh > mbEnvHigh ? compAttackCoeff : compReleaseCoeff);

                    float thresh = 0.28f - activeMultibandDrive * 0.16f;
                    float ratioFactor = 0.32f + activeMultibandDrive * 0.42f;
                    float makeup = 1.0f + activeMultibandDrive * 0.24f;

                    float gLow = 1.0f;
                    if (mbEnvLow > thresh) {
                        float gr = 20.0f * fastLog10(mbEnvLow / thresh) * ratioFactor;
                        if (gr > maxMbLowGr) maxMbLowGr = gr;
                        gLow = fastPow10(-gr * 0.05f);
                    }
                    float gMid = 1.0f;
                    if (mbEnvMid > thresh * 0.90f) {
                        float gr = 20.0f * fastLog10(mbEnvMid / (thresh * 0.90f)) * ratioFactor;
                        if (gr > maxMbMidGr) maxMbMidGr = gr;
                        gMid = fastPow10(-gr * 0.05f);
                    }
                    float gHigh = 1.0f;
                    if (mbEnvHigh > thresh * 0.72f) {
                        float gr = 20.0f * fastLog10(mbEnvHigh / (thresh * 0.72f)) * (ratioFactor + 0.12f);
                        if (gr > maxMbHighGr) maxMbHighGr = gr;
                        gHigh = fastPow10(-gr * 0.05f);
                    }

                    l = (lowL * gLow + midL * gMid + highL * gHigh) * makeup;
                    r = (lowR * gLow + midR * gMid + highR * gHigh) * makeup;
                }

                // 5c. High-Frequency Sibilance De-Esser (~6.2kHz sidechain ducking)
                if (activeDeEsser) {
                    deEssLpL += (l - deEssLpL) * deEssHpAlpha;
                    deEssLpR += (r - deEssLpR) * deEssHpAlpha;
                    float sibL = l - deEssLpL;
                    float sibR = r - deEssLpR;
                    float sibPeak = Math.max(Math.abs(sibL), Math.abs(sibR));
                    deEssEnv += (sibPeak - deEssEnv) * (sibPeak > deEssEnv ? gateAttackCoeff : compReleaseCoeff);
                    float sibThresh = 0.18f - activeDeEsserAmount * 0.11f;
                    float targetDeEss = deEssEnv > sibThresh
                            ? Math.max(0.22f, 1.0f - ((deEssEnv - sibThresh) / Math.max(0.05f, deEssEnv)) * activeDeEsserAmount * 1.1f)
                            : 1.0f;
                    deEssGain += (targetDeEss - deEssGain) * 0.04f;
                    l = deEssLpL + sibL * deEssGain;
                    r = deEssLpR + sibR * deEssGain;
                }

                // 6. Real Adaptive Auto-Gain Loudness Leveler (target -14 dBFS RMS)
                if (activeAutoGain) {
                    float instLevel = Math.max(0.001f, 0.5f * (Math.abs(l) + Math.abs(r)));
                    autoGainRms += (instLevel - autoGainRms) * autoGainSmoothCoeff;
                    float targetGain = 0.20f / Math.max(0.08f, autoGainRms);
                    targetGain = Math.max(0.60f, Math.min(1.85f, targetGain));
                    autoGainMultiplier += (targetGain - autoGainMultiplier) * 0.0005f;
                } else {
                    autoGainMultiplier = 1.0f;
                }

                // 7. Real Mid/Side Stereo Width (0% = Mono .. 100% = Natural .. 200% = Ultra Wide)
                if (Math.abs(stereoWidthMultiplier - 1.0f) > 0.01f) {
                    float mid = 0.5f * (l + r);
                    float side = 0.5f * (l - r) * stereoWidthMultiplier;
                    l = mid + side;
                    r = mid - side;
                }

                // 7b. Binaural Headphone Crossfeed (ITD ~0.33ms delayed + 720Hz LPF opposite feed)
                if (activeCrossfeed) {
                    cfDelayL[cfDelayIdx] = l;
                    cfDelayR[cfDelayIdx] = r;
                    int tapIdx = (cfDelayIdx + 1) % CROSSFEED_DELAY_LEN;
                    cfFilterL += (cfDelayL[tapIdx] - cfFilterL) * cfLpfAlpha;
                    cfFilterR += (cfDelayR[tapIdx] - cfFilterR) * cfLpfAlpha;
                    cfDelayIdx = tapIdx;
                    l = l * 0.88f + cfFilterR * 0.24f;
                    r = r * 0.88f + cfFilterL * 0.24f;
                }

                // 7c. Studio Acoustic Reverb (4 Damped Combs + 2 Allpass Diffusers)
                if (activeReverb && reverbBuffersInitialized) {
                    float wetL = processReverbChannel(l, combBufL, combIdxL, combFilterL, allpassBufL, allpassIdxL);
                    float wetR = processReverbChannel(r, combBufR, combIdxR, combFilterR, allpassBufR, allpassIdxR);
                    float dry = 1.0f - activeReverbMix * 0.35f;
                    l = l * dry + wetL * activeReverbMix;
                    r = r * dry + wetR * activeReverbMix;
                }

                // 8. 8D Spatial Orbit
                if (activeSpatial8d) {
                    spatialPhaseRad += phaseStep;
                    if (spatialPhaseRad > 6.283185307179586) {
                        spatialPhaseRad -= 6.283185307179586;
                    }
                    float pan = (float) Math.sin(spatialPhaseRad);
                    l *= (0.78f - 0.28f * pan);
                    r *= (0.78f + 0.28f * pan);
                }

                // 8b. 4K Ultra-HD Acoustic Micro-Detail & Harmonic Air Restorer (>4.8kHz crystal sheen)
                if (activeUltraHd4k) {
                    hdAirLpL += (l - hdAirLpL) * hdAirAlpha;
                    hdAirLpR += (r - hdAirLpR) * hdAirAlpha;
                    float hiL = l - hdAirLpL;
                    float hiR = r - hdAirLpR;
                    float harmonicDrive = activeSpeakerOutput ? 0.045f : 0.075f;
                    // Smooth zero-aliasing crystal shimmer on compressed highs
                    float excL = (hiL / (1.0f + Math.abs(hiL) * 1.4f)) * harmonicDrive;
                    float excR = (hiR / (1.0f + Math.abs(hiR) * 1.4f)) * harmonicDrive;
                    l += excL;
                    r += excR;
                }

                // 9. Pre-Amp * AutoGain * PadMakeup * Independent GAIN L / GAIN R + Zero-Crackle Envelope Limiter
                float totalGain = preAmpGain * autoGainMultiplier * padMakeupGain;
                l *= totalGain * leftChannelLinearGain;
                r *= totalGain * rightChannelLinearGain;

                if (activeAntiCrackle) {
                    float instPeak = Math.max(Math.abs(l), Math.abs(r));
                    float lCoeff = instPeak > limEnv ? limAttackCoeff : limReleaseCoeff;
                    limEnv += (instPeak - limEnv) * lCoeff;
                    float ceiling = activeLimiterCeilingLinear;
                    float envGuard = ceiling * 0.92f;
                    if (limEnv > envGuard) {
                        float envGain = envGuard / limEnv;
                        l *= envGain;
                        r *= envGain;
                        float grDb = 20.0f * fastLog10(Math.max(0.05f, 1.0f / envGain));
                        if (grDb > maxLimiterGrDb) maxLimiterGrDb = grDb;
                        peakWarningLatched = true;
                        limiterEngagedLatched = true;
                    }
                }
                l = brickwallLimitTracked(l);
                r = brickwallLimitTracked(r);

                if (isFloat) {
                    out.putFloat(l);
                    out.putFloat(r);
                } else {
                    out.putShort((short) Math.max(-32768, Math.min(32767, Math.round(l * 32767.0f))));
                    out.putShort((short) Math.max(-32768, Math.min(32767, Math.round(r * 32767.0f))));
                }
            }
            liveCompReductionDb = maxReductionDb;
            liveLimitingDb = maxLimiterGrDb;
            liveAutoGainDb = activeAutoGain ? 20.0f * fastLog10(Math.max(0.1f, autoGainMultiplier)) : 0f;
            liveTubeGlow = activeTubeEnabled ? Math.min(1f, 0.25f + activeTubeDrive * 0.45f + peakTubeLevel * 0.65f) : 0f;
            liveMbLowGrDb = activeMultiband ? Math.min(12f, maxMbLowGr) : 0f;
            liveMbMidGrDb = activeMultiband ? Math.min(12f, maxMbMidGr) : 0f;
            liveMbHighGrDb = activeMultiband ? Math.min(12f, maxMbHighGr) : 0f;
        } else {
            for (int f = 0; f < frames; f++) {
                for (int c = 0; c < channels; c++) {
                    float s = isFloat
                            ? inputBuffer.getFloat(inPos + c * 4)
                            : inputBuffer.getShort(inPos + c * 2) / 32768.0f;
                    for (int idx = 0; idx < FILTER_COUNT; idx++) {
                        if (!filterActive[idx]) continue;
                        float y = b0[idx] * s + z1[idx][c];
                        z1[idx][c] = b1[idx] * s - a1[idx] * y + z2[idx][c];
                        z2[idx][c] = b2[idx] * s - a2[idx] * y;
                        s = y;
                    }
                    s = brickwallLimitTracked(s * preAmpGain * leftChannelLinearGain);
                    if (isFloat) {
                        out.putFloat(s);
                    } else {
                        out.putShort((short) Math.max(-32768, Math.min(32767, Math.round(s * 32767.0f))));
                    }
                }
                inPos += frameBytes;
            }
        }

        inputBuffer.position(inPos);
        inputBuffer.order(origInOrder);
        out.flip();
    }

    /**
     * Real soft-knee brickwall ceiling limiter enforcing activeLimiterCeilingLinear (-12dB..0dB).
     */
    private float brickwallLimitTracked(float x) {
        float abs = Math.abs(x);
        float ceiling = activeLimiterCeilingLinear;
        float kneeStart = ceiling * 0.90f;
        if (abs <= kneeStart) return x;

        peakWarningLatched = true;
        if (abs >= ceiling * 0.96f) {
            limiterEngagedLatched = true;
        }
        float headroom = Math.max(0.01f, ceiling - kneeStart);
        float excess = (abs - kneeStart) / headroom;
        float shaped = kneeStart + headroom * (excess / (1.0f + excess));
        return x >= 0f ? Math.min(ceiling, shaped) : -Math.min(ceiling, shaped);
    }

    private float saturateTriode(float x, float drive) {
        // Asymmetric soft triode curve generating subtle warm even + odd harmonics
        float boosted = x * (1.0f + drive * 0.55f);
        float asym = boosted + drive * 0.08f * boosted * boosted;
        float out = asym / (1.0f + drive * 0.48f * Math.abs(asym));
        // Normalize unity loudness gain at moderate drive
        return out * (1.0f / (1.0f + drive * 0.22f));
    }

    private float processReverbChannel(
            float input,
            float[][] combs,
            int[] combIdx,
            float[] combFilt,
            float[][] allpasses,
            int[] allpassIdx) {
        float scaled = input * 0.22f;
        float acc = 0f;
        float damp1 = activeReverbDamp;
        float damp2 = 1.0f - damp1;
        float fb = activeReverbRoom;

        for (int i = 0; i < 4; i++) {
            float[] buf = combs[i];
            int idx = combIdx[i];
            float output = buf[idx];
            combFilt[i] = output * damp2 + combFilt[i] * damp1;
            buf[idx] = scaled + combFilt[i] * fb;
            combIdx[i] = (idx + 1) % buf.length;
            acc += output;
        }
        float out = acc * 0.25f;
        for (int i = 0; i < 2; i++) {
            float[] buf = allpasses[i];
            int idx = allpassIdx[i];
            float bufOut = buf[idx];
            float y = -out + bufOut;
            buf[idx] = out + bufOut * 0.5f;
            allpassIdx[i] = (idx + 1) % buf.length;
            out = y;
        }
        return out;
    }

    // Fast IEEE-754 bit-level log10 & pow10 (~12x faster than Math.log10/Math.pow inside audio sample loop)
    private static float fastLog10(float x) {
        if (x <= 1e-10f) return -10.0f;
        int bits = Float.floatToRawIntBits(x);
        float exp = ((bits >> 23) & 0xFF) - 127;
        bits = (bits & 0x007FFFFF) | 0x3F800000;
        float m = Float.intBitsToFloat(bits);
        // log2(m) quadratic approximation on [1, 2)
        float log2 = exp + (-0.34484843f * m + 2.02466578f) * m - 1.67487759f;
        return log2 * 0.3010299957f;
    }

    private static float fastPow10(float x) {
        if (x <= -10.0f) return 0f;
        if (x >= 10.0f) return 1e10f;
        float p = x * 3.321928095f; // log2(10)
        int i = (int) Math.floor(p);
        float f = p - i;
        // 2^f polynomial approximation on [0, 1)
        float pow2f = 1.0f + f * (0.693147f + f * (0.240226f + f * 0.055504f));
        int bits = ((i + 127) << 23);
        if (bits <= 0) return 0f;
        return Float.intBitsToFloat(bits) * pow2f;
    }
}
