package com.example.audio;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.audiofx.BassBoost;
import android.media.audiofx.Equalizer;
import android.media.audiofx.LoudnessEnhancer;
import android.media.audiofx.Virtualizer;
import android.media.audiofx.Visualizer;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import com.example.model.EqPreset;
import com.example.util.DeviceResourceOptimizer;
import com.example.util.PermissionHelper;

public class AudioFxEngine {
    public static final int UI_BAND_COUNT = 10;
    public static final int[] UI_BAND_FREQUENCIES_HZ = {
            31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000
    };
    public static final float MIN_GAIN_DB = -15.0f;
    public static final float MAX_GAIN_DB = 15.0f;
    public static final int MAX_BASS_PERCENT = 200;

    public static final String DEVICE_SPEAKER = "speaker";
    public static final String DEVICE_WIRED = "wired";
    public static final String DEVICE_BLUETOOTH = "bluetooth";

    private static final String PREFS_NAME = "neon_wave_audio_fx";
    private static final String KEY_EQ_ENABLED = "eq_enabled";
    private static final String KEY_PRESET = "eq_preset";
    private static final String KEY_BAND_PREFIX = "eq_band_";
    private static final String KEY_CUSTOM_BAND_PREFIX = "eq_custom_band_";
    private static final String KEY_BASS_V2_PERCENT = "bass_v2_percent";
    private static final String KEY_VIRTUALIZER_PERCENT = "virtualizer_percent";
    private static final String KEY_LOUDNESS_PERCENT = "loudness_percent";
    private static final String KEY_BALANCE_PERCENT = "balance_percent";
    private static final String KEY_SPATIAL_8D = "spatial_8d";
    private static final String KEY_CROSSFADE_SEC = "crossfade_seconds";
    private static final String KEY_AUTO_DEVICE_EQ = "auto_device_eq";
    private static final String KEY_HAPTIC_BASS = "haptic_bass";
    private static final String KEY_EDGE_GLOW = "edge_glow";
    private static final String KEY_LAST_DEVICE = "last_output_device";
    private static final String KEY_SKIP_SILENCE = "skip_silence";
    private static final String KEY_SMOOTH_FADE = "smooth_fade";
    private static final String KEY_DYNAMIC_THEME = "dynamic_theme";

    // Audio Processing • Purification Suite & Gain L/R keys
    private static final String KEY_GAIN_L_DB = "suite_gain_l_db";
    private static final String KEY_GAIN_R_DB = "suite_gain_r_db";
    private static final String KEY_COMP_RATIO = "suite_comp_ratio";
    private static final String KEY_COMP_THRESH_DB = "suite_comp_thresh_db";
    private static final String KEY_NOISE_GATE = "suite_noise_gate";
    private static final String KEY_NOISE_REDUCTION = "suite_noise_reduction";
    private static final String KEY_PURIFY_BASS_DB = "suite_purify_bass_db";
    private static final String KEY_TREBLE_CLARITY_DB = "suite_treble_clarity_db";
    private static final String KEY_STEREO_WIDTH_PCT = "suite_stereo_width_pct";
    private static final String KEY_LIMITER_DB = "suite_limiter_db";
    private static final String KEY_AUTO_GAIN = "suite_auto_gain";
    private static final String KEY_HPF_HZ = "suite_hpf_hz";
    private static final String KEY_LPF_HZ = "suite_lpf_hz";
    private static final String KEY_ULTRA_HD_4K = "suite_ultra_hd_4k";
    private static final String KEY_ANTI_CRACKLE = "suite_anti_crackle";
    private static final String KEY_QUICK_MODE = "suite_quick_mode";

    public static final int QUICK_NONE = 0;
    public static final int QUICK_BASS_PRO = 1;
    public static final int QUICK_CLEAN_4K = 2;
    public static final int QUICK_CLUB = 3;

    // Pro Studio Rack keys (Bypass, A/B Snapshot, Tube/Tape, Crossfeed, Reverb, DJ Filter, Vinyl Brake, Pads)
    private static final String KEY_MASTER_BYPASS = "studio_master_bypass";
    private static final String KEY_ACTIVE_SNAPSHOT = "studio_active_snapshot";
    private static final String KEY_TUBE_ENABLED = "studio_tube_enabled";
    private static final String KEY_TUBE_DRIVE_PCT = "studio_tube_drive_pct";
    private static final String KEY_TAPE_WARMTH_PCT = "studio_tape_warmth_pct";
    private static final String KEY_HEADPHONE_CROSSFEED = "studio_headphone_crossfeed";
    private static final String KEY_REVERB_ENABLED = "studio_reverb_enabled";
    private static final String KEY_REVERB_ROOM_PCT = "studio_reverb_room_pct";
    private static final String KEY_REVERB_DAMP_PCT = "studio_reverb_damp_pct";
    private static final String KEY_REVERB_MIX_PCT = "studio_reverb_mix_pct";
    private static final String KEY_DJ_FILTER_PCT = "studio_dj_filter_pct";
    private static final String KEY_VINYL_BRAKE = "studio_vinyl_brake";
    private static final String KEY_ACTIVE_PAD_MODE = "studio_active_pad_mode";
    private static final String KEY_MULTIBAND_ENABLED = "studio_multiband_enabled";
    private static final String KEY_MULTIBAND_DRIVE_PCT = "studio_multiband_drive_pct";
    private static final String KEY_DE_ESSER_PCT = "studio_de_esser_pct";
    private static final String KEY_VOCAL_REMOVER_PCT = "studio_vocal_remover_pct";
    private static final String KEY_HARMAN_TARGET = "studio_harman_target";
    private static final String KEY_HAS_REF_PROFILE = "studio_has_ref_profile";
    private static final String KEY_REF_MATCH_ENABLED = "studio_ref_match_enabled";
    private static final String KEY_REF_PROFILE_PREFIX = "studio_ref_band_";

    public interface VisualizerListener {
        void onSpectrumData(float[] normalizedBars, float energy);
    }

    public interface SpectrumFrameListener {
        void onSpectrumFrame(
                float[] normalizedBars,
                float energy,
                float leftLevel,
                float rightLevel,
                float bassEnergy,
                float trebleEnergy,
                int sampleRateHz);
    }

    public interface DeviceProfileChangeListener {
        void onDeviceProfileChanged(String deviceKey);
    }

    private static AudioFxEngine instance;

    private final float[] uiBandGainsDb = new float[UI_BAND_COUNT];
    private final float[] savedCustomBandGainsDb = new float[UI_BAND_COUNT];
    private EqPreset currentPreset = EqPreset.ELECTRONIC;
    private boolean eqEnabled = true;
    private int bassBoostPercent = 140;
    private int virtualizerPercent = 45;
    private int loudnessPercent = 10;
    private int balancePercent = 0;
    private boolean spatial8dEnabled = false;
    private int crossfadeSeconds = 0;
    private boolean autoDeviceEqEnabled = true;
    private boolean hapticBassEnabled = false;
    private boolean edgeGlowEnabled = true;
    private String activeOutputDeviceKey = DEVICE_SPEAKER;
    private boolean skipSilenceEnabled = false;
    private boolean smoothFadeEnabled = true;
    private boolean dynamicThemeEnabled = true;

    // Audio Processing • Purification Suite parameters (matching hardware rack defaults)
    private float gainLDb = 0.0f;
    private float gainRDb = 0.0f;
    private float compressorRatio = 3.0f;
    private float compressorThresholdDb = -12.0f;
    private boolean noiseGateEnabled = true;
    private boolean noiseReductionEnabled = true;
    private float purificationBassBoostDb = 3.0f;
    private float trebleClarityDb = 2.5f;
    private int stereoWidthPercent = 120;
    private float limiterCeilingDb = -1.0f;
    private boolean autoGainEnabled = true;
    private int hpfHz = 80;
    private int lpfHz = 18000;
    private boolean ultraHd4kEnabled = true;
    private boolean antiCrackleEnabled = true;
    private int activeQuickMode = QUICK_CLEAN_4K;

    // Pro Studio Rack parameters
    private boolean masterBypass = false;
    private int activeSnapshotSlot = 0; // 0 = A, 1 = B
    private boolean tubeEnabled = false;
    private int tubeDrivePercent = 35;
    private int tapeWarmthPercent = 25;
    private boolean headphoneCrossfeedEnabled = false;
    private boolean reverbEnabled = false;
    private int reverbRoomPercent = 42;
    private int reverbDampPercent = 35;
    private int reverbMixPercent = 24;
    private int djFilterPercent = 0;
    private boolean vinylBrakeEnabled = true;
    private int activePadMode = NeonDspAudioProcessor.PAD_NONE;

    // Advanced Mastering & Reference Match EQ
    private boolean multibandEnabled = false;
    private int multibandDrivePercent = 45;
    private int deEsserPercent = 0;
    private int vocalRemoverPercent = 0;
    private boolean harmanTargetEnabled = false;
    private int stutterDiv = 0; // transient DJ performance stutter: 0=OFF, 1=1/4, 2=1/8, 3=1/16
    private boolean hasRefProfile = false;
    private boolean refMatchEnabled = false;
    private final float[] refProfileBandsDb = new float[UI_BAND_COUNT];
    private final float[] preMatchBandGainsDb = new float[UI_BAND_COUNT];
    private EqPreset preMatchPreset = EqPreset.ELECTRONIC;
    private boolean hasPreMatchBackup = false;

    // Live meter telemetry updated from PCM Tap & DSP Limiter
    private volatile float latestLeftAvgDb = -48.0f;
    private volatile float latestRightAvgDb = -48.0f;
    private volatile boolean latestClipActive = false;
    private volatile int latestBitDepth = 24;
    private volatile float latestPhaseCorrelation = 1.0f;
    private volatile float latestLufsShortTerm = -48.0f;
    private volatile float latestCrestFactorDb = 0.0f;
    private volatile float latestDominantFreqHz = 0.0f;
    private volatile String latestDominantNote = "--";
    private volatile float latestLiveBpm = 120.0f;
    private volatile String latestKeyLabel = "--";
    private volatile String latestCamelot = "--";
    private volatile int latestBassPowerPercent = 0;
    private volatile float latestBassPowerDb = -48.0f;
    private final float[] latestBand10Db = new float[UI_BAND_COUNT];
    private final float[] latestXyLeft = new float[AudioSpectrumTapProcessor.XY_SAMPLE_COUNT];
    private final float[] latestXyRight = new float[AudioSpectrumTapProcessor.XY_SAMPLE_COUNT];
    private final Object xyLock = new Object();

    private int currentSessionId = 0;

    private Equalizer equalizer;
    private BassBoost bassBoost;
    private Virtualizer virtualizer;
    private LoudnessEnhancer loudnessEnhancer;
    private Visualizer visualizer;
    private VisualizerListener visualizerListener;
    private SpectrumFrameListener spectrumFrameListener;
    private DeviceProfileChangeListener deviceProfileChangeListener;
    private AudioManager monitoredAudioManager;
    private AudioDeviceCallback audioDeviceCallback;
    private SharedPreferences prefs;
    private final float[][] reusableVisualizerBars = new float[2][42];
    private int reusableBarBufferIdx = 0;
    private int activeCaptureDivisor = -1;

    private final NeonDspAudioProcessor dspAudioProcessor = new NeonDspAudioProcessor();
    private final AudioSpectrumTapProcessor audioTapProcessor = new AudioSpectrumTapProcessor();
    private final AudioSpectrumTapProcessor.SpectrumFrame reusablePolledFrame =
            new AudioSpectrumTapProcessor.SpectrumFrame();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private volatile boolean playbackPlaying = false;
    private long lastTapFrameDispatchedMs = 0L;
    private long pumpIntervalMs = 20L;

    private final Runnable tapDispatchRunnable = new Runnable() {
        @Override
        public void run() {
            SpectrumFrameListener rich = spectrumFrameListener;
            VisualizerListener legacy = visualizerListener;
            if (!playbackPlaying || (rich == null && legacy == null)) {
                return;
            }
            long now = SystemClock.uptimeMillis();
            AudioSpectrumTapProcessor.SpectrumFrame frame =
                    audioTapProcessor.pollTimeAlignedFrame(now, reusablePolledFrame);
            if (frame != null) {
                lastTapFrameDispatchedMs = now;
                latestLeftAvgDb = frame.leftAvgDb;
                latestRightAvgDb = frame.rightAvgDb;
                latestClipActive = frame.clipDetected || dspAudioProcessor.consumeLimiterEngaged();
                latestBitDepth = frame.bitDepth;
                latestPhaseCorrelation = frame.phaseCorrelation;
                latestLufsShortTerm = frame.lufsShortTerm;
                latestCrestFactorDb = frame.crestFactorDb;
                latestDominantFreqHz = frame.dominantFreqHz;
                latestDominantNote = frame.dominantNoteName;
                if (Math.abs(frame.liveBpm - latestLiveBpm) >= 0.4f) {
                    latestLiveBpm = frame.liveBpm;
                    if (stutterDiv > 0) {
                        syncDspProcessorParams();
                    }
                } else {
                    latestLiveBpm = frame.liveBpm;
                }
                latestKeyLabel = frame.detectedKeyLabel;
                latestCamelot = frame.detectedCamelot;
                latestBassPowerPercent = frame.bassPowerPercent;
                latestBassPowerDb = frame.bassPowerDb;
                synchronized (xyLock) {
                    System.arraycopy(frame.xyLeft, 0, latestXyLeft, 0, latestXyLeft.length);
                    System.arraycopy(frame.xyRight, 0, latestXyRight, 0, latestXyRight.length);
                    System.arraycopy(frame.band10Db, 0, latestBand10Db, 0, UI_BAND_COUNT);
                }
                if (rich != null) {
                    rich.onSpectrumFrame(
                            frame.bars,
                            frame.energy,
                            frame.leftLevel,
                            frame.rightLevel,
                            frame.bassEnergy,
                            frame.trebleEnergy,
                            frame.sampleRateHz);
                } else if (legacy != null) {
                    legacy.onSpectrumData(frame.bars, frame.energy);
                }
            }
            mainHandler.postDelayed(this, pumpIntervalMs);
        }
    };

    private AudioFxEngine() {
        System.arraycopy(EqPreset.ELECTRONIC.getGainsDbCopy(), 0, uiBandGainsDb, 0, UI_BAND_COUNT);
        System.arraycopy(EqPreset.ELECTRONIC.getGainsDbCopy(), 0, savedCustomBandGainsDb, 0, UI_BAND_COUNT);
        syncDspProcessorParams();
    }

    public NeonDspAudioProcessor getDspAudioProcessor() {
        return dspAudioProcessor;
    }

    public AudioSpectrumTapProcessor getAudioTapProcessor() {
        return audioTapProcessor;
    }

    public synchronized boolean isSpectrumTapNeeded() {
        return spectrumFrameListener != null || visualizerListener != null;
    }

    public synchronized boolean isDspActive() {
        return !masterBypass && (eqEnabled
                || ultraHd4kEnabled
                || antiCrackleEnabled
                || tubeEnabled
                || reverbEnabled
                || headphoneCrossfeedEnabled
                || djFilterPercent != 0
                || activePadMode != NeonDspAudioProcessor.PAD_NONE
                || multibandEnabled
                || deEsserPercent > 0
                || vocalRemoverPercent > 0
                || harmanTargetEnabled
                || stutterDiv > 0);
    }

    public void setTargetVisualFps(int fps) {
        int clamped = Math.max(20, Math.min(60, fps));
        this.pumpIntervalMs = Math.max(16L, Math.round(1000.0 / clamped));
    }

    public void onPlaybackStateChanged(boolean playing) {
        this.playbackPlaying = playing;
        boolean needTap = isSpectrumTapNeeded();
        audioTapProcessor.setCaptureEnabled(playing && needTap);
        if (!playing) {
            audioTapProcessor.clear();
            mainHandler.removeCallbacks(tapDispatchRunnable);
        } else if (needTap) {
            mainHandler.removeCallbacks(tapDispatchRunnable);
            mainHandler.post(tapDispatchRunnable);
        }
        setVisualizerActive(playing);
    }

    public void onPlaybackSeekOrReset() {
        audioTapProcessor.clear();
    }

    public static synchronized AudioFxEngine getInstance() {
        if (instance == null) {
            instance = new AudioFxEngine();
        }
        return instance;
    }

    public synchronized void init(Context context) {
        if (prefs == null) {
            prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            eqEnabled = prefs.getBoolean(KEY_EQ_ENABLED, true);
            currentPreset = EqPreset.fromKey(prefs.getString(KEY_PRESET, EqPreset.ELECTRONIC.getKey()));
            bassBoostPercent = Math.max(0, Math.min(MAX_BASS_PERCENT,
                    prefs.getInt(KEY_BASS_V2_PERCENT, currentPreset.getDefaultBassSliderPercent())));
            virtualizerPercent = Math.max(0, Math.min(100, prefs.getInt(KEY_VIRTUALIZER_PERCENT, 45)));
            loudnessPercent = Math.max(0, Math.min(100, prefs.getInt(KEY_LOUDNESS_PERCENT, 10)));
            balancePercent = Math.max(-100, Math.min(100, prefs.getInt(KEY_BALANCE_PERCENT, 0)));
            spatial8dEnabled = prefs.getBoolean(KEY_SPATIAL_8D, false);
            crossfadeSeconds = Math.max(0, Math.min(10, prefs.getInt(KEY_CROSSFADE_SEC, 0)));
            autoDeviceEqEnabled = prefs.getBoolean(KEY_AUTO_DEVICE_EQ, true);
            hapticBassEnabled = prefs.getBoolean(KEY_HAPTIC_BASS, false);
            edgeGlowEnabled = prefs.getBoolean(KEY_EDGE_GLOW, true);
            activeOutputDeviceKey = prefs.getString(KEY_LAST_DEVICE, detectOutputDeviceKey(context));
            skipSilenceEnabled = prefs.getBoolean(KEY_SKIP_SILENCE, false);
            smoothFadeEnabled = prefs.getBoolean(KEY_SMOOTH_FADE, true);
            dynamicThemeEnabled = prefs.getBoolean(KEY_DYNAMIC_THEME, true);

            gainLDb = clamp(prefs.getFloat(KEY_GAIN_L_DB, 0.0f), -12f, 6f);
            gainRDb = clamp(prefs.getFloat(KEY_GAIN_R_DB, 0.0f), -12f, 6f);
            compressorRatio = clamp(prefs.getFloat(KEY_COMP_RATIO, 3.0f), 1f, 8f);
            compressorThresholdDb = clamp(prefs.getFloat(KEY_COMP_THRESH_DB, -12.0f), -30f, 0f);
            noiseGateEnabled = prefs.getBoolean(KEY_NOISE_GATE, true);
            noiseReductionEnabled = prefs.getBoolean(KEY_NOISE_REDUCTION, true);
            purificationBassBoostDb = clamp(prefs.getFloat(KEY_PURIFY_BASS_DB, 3.0f), 0f, 12f);
            trebleClarityDb = clamp(prefs.getFloat(KEY_TREBLE_CLARITY_DB, 2.5f), 0f, 12f);
            stereoWidthPercent = Math.max(0, Math.min(200, prefs.getInt(KEY_STEREO_WIDTH_PCT, 120)));
            limiterCeilingDb = clamp(prefs.getFloat(KEY_LIMITER_DB, -1.0f), -12f, 0f);
            autoGainEnabled = prefs.getBoolean(KEY_AUTO_GAIN, true);
            hpfHz = Math.max(20, Math.min(250, prefs.getInt(KEY_HPF_HZ, 80)));
            lpfHz = Math.max(8000, Math.min(22000, prefs.getInt(KEY_LPF_HZ, 18000)));
            ultraHd4kEnabled = prefs.getBoolean(KEY_ULTRA_HD_4K, true);
            antiCrackleEnabled = prefs.getBoolean(KEY_ANTI_CRACKLE, true);
            activeQuickMode = Math.max(QUICK_NONE, Math.min(QUICK_CLUB, prefs.getInt(KEY_QUICK_MODE, QUICK_CLEAN_4K)));

            masterBypass = prefs.getBoolean(KEY_MASTER_BYPASS, false);
            activeSnapshotSlot = prefs.getInt(KEY_ACTIVE_SNAPSHOT, 0) == 1 ? 1 : 0;
            tubeEnabled = prefs.getBoolean(KEY_TUBE_ENABLED, false);
            tubeDrivePercent = Math.max(0, Math.min(100, prefs.getInt(KEY_TUBE_DRIVE_PCT, 35)));
            tapeWarmthPercent = Math.max(0, Math.min(100, prefs.getInt(KEY_TAPE_WARMTH_PCT, 25)));
            headphoneCrossfeedEnabled = prefs.getBoolean(KEY_HEADPHONE_CROSSFEED, false);
            reverbEnabled = prefs.getBoolean(KEY_REVERB_ENABLED, false);
            reverbRoomPercent = Math.max(0, Math.min(100, prefs.getInt(KEY_REVERB_ROOM_PCT, 42)));
            reverbDampPercent = Math.max(0, Math.min(100, prefs.getInt(KEY_REVERB_DAMP_PCT, 35)));
            reverbMixPercent = Math.max(0, Math.min(100, prefs.getInt(KEY_REVERB_MIX_PCT, 24)));
            djFilterPercent = Math.max(-100, Math.min(100, prefs.getInt(KEY_DJ_FILTER_PCT, 0)));
            vinylBrakeEnabled = prefs.getBoolean(KEY_VINYL_BRAKE, true);
            activePadMode = Math.max(NeonDspAudioProcessor.PAD_NONE,
                    Math.min(NeonDspAudioProcessor.PAD_LOFI_RADIO, prefs.getInt(KEY_ACTIVE_PAD_MODE, 0)));
            multibandEnabled = prefs.getBoolean(KEY_MULTIBAND_ENABLED, false);
            multibandDrivePercent = Math.max(0, Math.min(100, prefs.getInt(KEY_MULTIBAND_DRIVE_PCT, 45)));
            deEsserPercent = Math.max(0, Math.min(100, prefs.getInt(KEY_DE_ESSER_PCT, 0)));
            vocalRemoverPercent = Math.max(0, Math.min(100, prefs.getInt(KEY_VOCAL_REMOVER_PCT, 0)));
            harmanTargetEnabled = prefs.getBoolean(KEY_HARMAN_TARGET, false);
            hasRefProfile = prefs.getBoolean(KEY_HAS_REF_PROFILE, false);
            refMatchEnabled = prefs.getBoolean(KEY_REF_MATCH_ENABLED, false) && hasRefProfile;
            for (int i = 0; i < UI_BAND_COUNT; i++) {
                refProfileBandsDb[i] = prefs.getFloat(KEY_REF_PROFILE_PREFIX + i, -18f);
                latestBand10Db[i] = -24f;
            }

            for (int i = 0; i < UI_BAND_COUNT; i++) {
                float fallback = currentPreset.getGainsDbCopy()[i];
                savedCustomBandGainsDb[i] = Math.max(MIN_GAIN_DB, Math.min(MAX_GAIN_DB,
                        prefs.getFloat(KEY_CUSTOM_BAND_PREFIX + i,
                                prefs.getFloat(KEY_BAND_PREFIX + i, fallback))));
            }

            if (currentPreset == EqPreset.CUSTOM) {
                System.arraycopy(savedCustomBandGainsDb, 0, uiBandGainsDb, 0, UI_BAND_COUNT);
            } else {
                System.arraycopy(currentPreset.getGainsDbCopy(), 0, uiBandGainsDb, 0, UI_BAND_COUNT);
            }
            syncDspProcessorParams();
        }
    }

    private static float clamp(float val, float min, float max) {
        return Math.max(min, Math.min(max, val));
    }

    public static String detectOutputDeviceKey(Context context) {
        try {
            AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (am != null) {
                AudioDeviceInfo[] outputs = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
                boolean hasWired = false;
                for (AudioDeviceInfo dev : outputs) {
                    int t = dev.getType();
                    if (t == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
                            || t == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
                            || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                            && (t == AudioDeviceInfo.TYPE_BLE_HEADSET || t == AudioDeviceInfo.TYPE_BLE_SPEAKER))) {
                        return DEVICE_BLUETOOTH;
                    }
                    if (t == AudioDeviceInfo.TYPE_WIRED_HEADPHONES
                            || t == AudioDeviceInfo.TYPE_WIRED_HEADSET
                            || t == AudioDeviceInfo.TYPE_USB_HEADSET
                            || t == AudioDeviceInfo.TYPE_USB_DEVICE) {
                        hasWired = true;
                    }
                }
                if (hasWired) return DEVICE_WIRED;
            }
        } catch (Throwable ignored) {}
        return DEVICE_SPEAKER;
    }

    public synchronized void registerAudioDeviceMonitor(Context context, DeviceProfileChangeListener listener) {
        init(context);
        this.deviceProfileChangeListener = listener;
        String currentHw = detectOutputDeviceKey(context);
        if (autoDeviceEqEnabled && !currentHw.equals(activeOutputDeviceKey)) {
            switchToDeviceProfile(currentHw);
        }
        if (monitoredAudioManager == null) {
            try {
                monitoredAudioManager = (AudioManager) context.getApplicationContext()
                        .getSystemService(Context.AUDIO_SERVICE);
                if (monitoredAudioManager != null) {
                    audioDeviceCallback = new AudioDeviceCallback() {
                        @Override
                        public void onAudioDevicesAdded(AudioDeviceInfo[] addedDevices) {
                            handleHardwareRouteChange(context);
                        }

                        @Override
                        public void onAudioDevicesRemoved(AudioDeviceInfo[] removedDevices) {
                            handleHardwareRouteChange(context);
                        }
                    };
                    monitoredAudioManager.registerAudioDeviceCallback(audioDeviceCallback, mainHandler);
                }
            } catch (Throwable ignored) {}
        }
    }

    public synchronized void unregisterAudioDeviceMonitor() {
        this.deviceProfileChangeListener = null;
        if (monitoredAudioManager != null && audioDeviceCallback != null) {
            try {
                monitoredAudioManager.unregisterAudioDeviceCallback(audioDeviceCallback);
            } catch (Throwable ignored) {}
        }
        audioDeviceCallback = null;
        monitoredAudioManager = null;
    }

    private void handleHardwareRouteChange(Context context) {
        String detected = detectOutputDeviceKey(context);
        DeviceProfileChangeListener cb;
        synchronized (this) {
            if (!autoDeviceEqEnabled || detected.equals(activeOutputDeviceKey)) {
                return;
            }
            switchToDeviceProfile(detected);
            cb = deviceProfileChangeListener;
        }
        if (cb != null) {
            cb.onDeviceProfileChanged(detected);
        }
    }

    public synchronized void switchToDeviceProfile(String newDeviceKey) {
        if (newDeviceKey == null || newDeviceKey.equals(activeOutputDeviceKey)) return;
        saveDeviceProfile(activeOutputDeviceKey);
        activeOutputDeviceKey = newDeviceKey;
        loadDeviceProfileOrSeedDefaults(newDeviceKey);
        saveState();
        applyEffectsToHardware();
    }

    private void saveDeviceProfile(String deviceKey) {
        if (prefs == null || deviceKey == null) return;
        String prefix = "prof_" + deviceKey + "_";
        SharedPreferences.Editor ed = prefs.edit();
        ed.putBoolean(prefix + "saved", true);
        ed.putString(prefix + "preset", currentPreset.getKey());
        ed.putInt(prefix + "bass", bassBoostPercent);
        ed.putInt(prefix + "virt", virtualizerPercent);
        ed.putInt(prefix + "balance", balancePercent);
        ed.putBoolean(prefix + "spatial8d", spatial8dEnabled);
        for (int i = 0; i < UI_BAND_COUNT; i++) {
            ed.putFloat(prefix + "custom_" + i, savedCustomBandGainsDb[i]);
        }
        ed.apply();
    }

    private void loadDeviceProfileOrSeedDefaults(String deviceKey) {
        if (prefs == null || deviceKey == null) return;
        String prefix = "prof_" + deviceKey + "_";
        if (prefs.getBoolean(prefix + "saved", false)) {
            currentPreset = EqPreset.fromKey(prefs.getString(prefix + "preset", currentPreset.getKey()));
            bassBoostPercent = Math.max(0, Math.min(MAX_BASS_PERCENT, prefs.getInt(prefix + "bass", bassBoostPercent)));
            virtualizerPercent = Math.max(0, Math.min(100, prefs.getInt(prefix + "virt", virtualizerPercent)));
            balancePercent = Math.max(-100, Math.min(100, prefs.getInt(prefix + "balance", 0)));
            spatial8dEnabled = prefs.getBoolean(prefix + "spatial8d", false);
            for (int i = 0; i < UI_BAND_COUNT; i++) {
                savedCustomBandGainsDb[i] = prefs.getFloat(prefix + "custom_" + i, savedCustomBandGainsDb[i]);
            }
            if (currentPreset == EqPreset.CUSTOM) {
                System.arraycopy(savedCustomBandGainsDb, 0, uiBandGainsDb, 0, UI_BAND_COUNT);
            } else {
                System.arraycopy(currentPreset.getGainsDbCopy(), 0, uiBandGainsDb, 0, UI_BAND_COUNT);
            }
        } else {
            // Sensible device-specific defaults on very first plug-in
            if (DEVICE_WIRED.equals(deviceKey) || DEVICE_BLUETOOTH.equals(deviceKey)) {
                bassBoostPercent = Math.max(bassBoostPercent, 145);
                virtualizerPercent = Math.max(virtualizerPercent, 50);
            }
        }
    }

    public synchronized void attachToAudioSession(Context context, int audioSessionId) {
        init(context);
        if (audioSessionId <= 0) {
            return;
        }
        if (currentSessionId == audioSessionId && equalizer != null) {
            applyEffectsToHardware();
            ensureVisualizerAttached(context);
            return;
        }

        releaseHardwareFx();
        currentSessionId = audioSessionId;

        try {
            equalizer = new Equalizer(0, audioSessionId);
            equalizer.setEnabled(eqEnabled);
        } catch (Throwable ignored) {
            equalizer = null;
        }

        try {
            bassBoost = new BassBoost(0, audioSessionId);
            bassBoost.setEnabled(eqEnabled);
        } catch (Throwable ignored) {
            bassBoost = null;
        }

        try {
            virtualizer = new Virtualizer(0, audioSessionId);
            virtualizer.setEnabled(eqEnabled && virtualizerPercent > 0);
        } catch (Throwable ignored) {
            virtualizer = null;
        }

        try {
            loudnessEnhancer = new LoudnessEnhancer(audioSessionId);
            loudnessEnhancer.setEnabled(eqEnabled && loudnessPercent > 0);
        } catch (Throwable ignored) {
            loudnessEnhancer = null;
        }

        applyEffectsToHardware();
        ensureVisualizerAttached(context);
    }

    public synchronized void ensureVisualizerAttached(Context context) {
        if (currentSessionId <= 0 || (visualizerListener == null && spectrumFrameListener == null) || context == null) {
            return;
        }
        if (!PermissionHelper.hasRecordAudioPermission(context)) {
            return;
        }
        int desiredDivisor = DeviceResourceOptimizer.getInstance(context).getVisualizerCaptureRateDivisor();
        if (visualizer != null) {
            if (activeCaptureDivisor != desiredDivisor) {
                releaseVisualizer();
            } else {
                try {
                    if (!visualizer.getEnabled()) {
                        visualizer.setEnabled(true);
                    }
                    return;
                } catch (Throwable ignored) {
                    releaseVisualizer();
                }
            }
        }

        try {
            visualizer = new Visualizer(currentSessionId);
            int[] sizeRange = Visualizer.getCaptureSizeRange();
            int captureSize = Math.min(256, Math.max(sizeRange[0], 128));
            visualizer.setCaptureSize(captureSize);
            int captureRate = Math.max(1000, Visualizer.getMaxCaptureRate() / Math.max(1, desiredDivisor));
            activeCaptureDivisor = desiredDivisor;
            visualizer.setDataCaptureListener(new Visualizer.OnDataCaptureListener() {
                @Override
                public void onWaveFormDataCapture(Visualizer v, byte[] waveform, int samplingRate) {}

                @Override
                public void onFftDataCapture(Visualizer v, byte[] fft, int samplingRate) {
                    // Prefer sample-accurate ExoPlayer PCM tap whenever it is actively delivering frames
                    if (SystemClock.uptimeMillis() - lastTapFrameDispatchedMs < 300L) {
                        return;
                    }
                    SpectrumFrameListener rich = spectrumFrameListener;
                    VisualizerListener listener = visualizerListener;
                    if (fft == null || fft.length < 4 || (listener == null && rich == null)) {
                        return;
                    }
                    int barCount = 42;
                    reusableBarBufferIdx ^= 1;
                    float[] bars = reusableVisualizerBars[reusableBarBufferIdx];
                    int bins = fft.length / 2;
                    float sumEnergy = 0f;
                    float bassSum = 0f;
                    float trebleSum = 0f;
                    int trebleCount = 0;
                    for (int i = 0; i < barCount; i++) {
                        double norm = (double) i / (barCount - 1);
                        int binIdx = 1 + (int) Math.round(Math.pow(norm, 1.55) * Math.max(1, bins - 2));
                        int rfkIdx = binIdx * 2;
                        int ifkIdx = rfkIdx + 1;
                        if (ifkIdx < fft.length) {
                            float re = fft[rfkIdx];
                            float im = fft[ifkIdx];
                            float mag = (float) Math.hypot(re, im);
                            float normalized = Math.min(1f, (float) (Math.log10(1.0 + mag * 1.6) / 2.05));
                            bars[i] = normalized;
                            sumEnergy += normalized;
                            if (i < 8) bassSum += normalized;
                            else if (i >= 26) {
                                trebleSum += normalized;
                                trebleCount++;
                            }
                        } else {
                            bars[i] = 0f;
                        }
                    }
                    float energy = sumEnergy / barCount;
                    float bass = bassSum / 8.0f;
                    float treble = trebleCount > 0 ? trebleSum / trebleCount : energy;
                    latestLeftAvgDb = Math.max(-24f, Math.min(3.5f, -24f + energy * 26f));
                    latestRightAvgDb = latestLeftAvgDb;
                    latestClipActive = energy >= 0.95f || dspAudioProcessor.consumeLimiterEngaged();
                    latestBitDepth = audioTapProcessor.getBitDepth();
                    latestLufsShortTerm = Math.max(-48f, Math.min(-3f, -34f + energy * 28f));
                    latestCrestFactorDb = Math.max(2f, Math.min(18f, 12f - energy * 5f));
                    latestPhaseCorrelation = 0.85f;
                    latestBassPowerPercent = Math.max(0, Math.min(100, Math.round(bass * 100f)));
                    latestBassPowerDb = Math.max(-48f, Math.min(6f, -38f + bass * 42f));
                    synchronized (xyLock) {
                        for (int k = 0; k < latestXyLeft.length; k++) {
                            float bar = bars[k % barCount];
                            double ang = (k / (double) latestXyLeft.length) * Math.PI * 2.0 + energy * 3.0;
                            latestXyLeft[k] = (float) (Math.sin(ang) * bar * 0.72f);
                            latestXyRight[k] = (float) (Math.sin(ang + 0.32) * bar * 0.72f);
                        }
                        for (int band = 0; band < UI_BAND_COUNT; band++) {
                            int bIdx = Math.min(barCount - 1, (band * barCount) / UI_BAND_COUNT);
                            float dbVal = -36f + bars[bIdx] * 36f;
                            latestBand10Db[band] = latestBand10Db[band] * 0.85f + dbVal * 0.15f;
                        }
                    }
                    if (rich != null) {
                        rich.onSpectrumFrame(bars, energy, energy, energy, bass, treble, audioTapProcessor.getSampleRateHz());
                    } else if (listener != null) {
                        listener.onSpectrumData(bars, energy);
                    }
                }
            }, captureRate, false, true);
            visualizer.setEnabled(true);
        } catch (Throwable ignored) {
            releaseVisualizer();
        }
    }

    public synchronized void updateAdaptiveVisualizerRate(Context context) {
        if (context == null) return;
        DeviceResourceOptimizer optimizer = DeviceResourceOptimizer.getInstance(context);
        setTargetVisualFps(optimizer.getTargetVisualFps());
        audioTapProcessor.setFrameSpacingMs(optimizer.getTapSpacingMs());
        if ((visualizerListener == null && spectrumFrameListener == null) || currentSessionId <= 0) {
            return;
        }
        int desiredDivisor = optimizer.getVisualizerCaptureRateDivisor();
        if (visualizer != null && activeCaptureDivisor != desiredDivisor) {
            ensureVisualizerAttached(context);
        }
    }

    public synchronized void setVisualizerListener(Context context, VisualizerListener listener) {
        this.visualizerListener = listener;
        syncPumpAndVisualizer(context);
    }

    public synchronized void setSpectrumFrameListener(Context context, SpectrumFrameListener listener) {
        this.spectrumFrameListener = listener;
        syncPumpAndVisualizer(context);
    }

    private void syncPumpAndVisualizer(Context context) {
        boolean hasListener = spectrumFrameListener != null || visualizerListener != null;
        audioTapProcessor.setCaptureEnabled(hasListener && playbackPlaying);
        if (!hasListener) {
            mainHandler.removeCallbacks(tapDispatchRunnable);
            setVisualizerActive(false);
        } else {
            if (playbackPlaying) {
                mainHandler.removeCallbacks(tapDispatchRunnable);
                mainHandler.post(tapDispatchRunnable);
            }
            if (currentSessionId > 0) {
                ensureVisualizerAttached(context);
            }
        }
    }

    public synchronized void setVisualizerActive(boolean active) {
        if (active && visualizerListener == null && spectrumFrameListener == null) {
            return;
        }
        if (visualizer != null) {
            try {
                if (visualizer.getEnabled() != active) {
                    visualizer.setEnabled(active);
                }
            } catch (Throwable ignored) {}
        }
    }

    public synchronized void setEqEnabled(boolean enabled) {
        this.eqEnabled = enabled;
        saveState();
        applyEffectsToHardware();
    }

    public synchronized boolean isEqEnabled() { return eqEnabled; }
    public synchronized EqPreset getCurrentPreset() { return currentPreset; }
    public synchronized float[] getUiBandGainsDb() { return uiBandGainsDb.clone(); }
    public synchronized float[] getSavedCustomBandGainsDb() { return savedCustomBandGainsDb.clone(); }
    public synchronized int getBassBoostPercent() { return bassBoostPercent; }
    public synchronized int getVirtualizerPercent() { return virtualizerPercent; }
    public synchronized int getLoudnessPercent() { return loudnessPercent; }
    public synchronized int getBalancePercent() { return balancePercent; }
    public synchronized boolean isSpatial8dEnabled() { return spatial8dEnabled; }
    public synchronized int getCrossfadeSeconds() { return crossfadeSeconds; }
    public synchronized boolean isAutoDeviceEqEnabled() { return autoDeviceEqEnabled; }
    public synchronized boolean isHapticBassEnabled() { return hapticBassEnabled; }
    public synchronized boolean isEdgeGlowEnabled() { return edgeGlowEnabled; }
    public synchronized String getActiveOutputDeviceKey() { return activeOutputDeviceKey; }
    public synchronized boolean isSkipSilenceEnabled() { return skipSilenceEnabled; }
    public synchronized boolean isSmoothFadeEnabled() { return smoothFadeEnabled; }
    public synchronized boolean isDynamicThemeEnabled() { return dynamicThemeEnabled; }

    public synchronized float getGainLDb() { return gainLDb; }
    public synchronized float getGainRDb() { return gainRDb; }
    public synchronized float getCompressorRatio() { return compressorRatio; }
    public synchronized float getCompressorThresholdDb() { return compressorThresholdDb; }
    public synchronized boolean isNoiseGateEnabled() { return noiseGateEnabled; }
    public synchronized boolean isNoiseReductionEnabled() { return noiseReductionEnabled; }
    public synchronized float getPurificationBassBoostDb() { return purificationBassBoostDb; }
    public synchronized float getTrebleClarityDb() { return trebleClarityDb; }
    public synchronized int getStereoWidthPercent() { return stereoWidthPercent; }
    public synchronized float getLimiterCeilingDb() { return limiterCeilingDb; }
    public synchronized boolean isAutoGainEnabled() { return autoGainEnabled; }
    public synchronized int getHpfHz() { return hpfHz; }
    public synchronized int getLpfHz() { return lpfHz; }
    public synchronized boolean isUltraHd4kEnabled() { return ultraHd4kEnabled; }
    public synchronized boolean isAntiCrackleEnabled() { return antiCrackleEnabled; }
    public synchronized int getActiveQuickMode() { return activeQuickMode; }
    public synchronized boolean isSpeakerOutputActive() { return DEVICE_SPEAKER.equals(activeOutputDeviceKey); }

    public float getLatestLeftAvgDb() { return latestLeftAvgDb; }
    public float getLatestRightAvgDb() { return latestRightAvgDb; }
    public boolean isLatestClipActive() { return latestClipActive; }
    public int getLatestBitDepth() { return latestBitDepth; }
    public float getLiveCompReductionDb() { return dspAudioProcessor.getLiveCompReductionDb(); }
    public float getLiveLimitingDb() { return dspAudioProcessor.getLiveLimitingDb(); }
    public float getLiveTubeGlow() { return dspAudioProcessor.getLiveTubeGlow(); }
    public int getLatestBassPowerPercent() { return latestBassPowerPercent; }
    public float getLatestBassPowerDb() { return latestBassPowerDb; }
    public float getLatestPhaseCorrelation() { return latestPhaseCorrelation; }
    public float getLatestLufsShortTerm() { return latestLufsShortTerm; }
    public float getLatestCrestFactorDb() { return latestCrestFactorDb; }
    public float getLatestDominantFreqHz() { return latestDominantFreqHz; }
    public String getLatestDominantNote() { return latestDominantNote; }
    public float getLatestLiveBpm() { return latestLiveBpm; }
    public String getLatestKeyLabel() { return latestKeyLabel; }
    public String getLatestCamelot() { return latestCamelot; }
    public float getLiveMbLowGrDb() { return dspAudioProcessor.getLiveMbLowGrDb(); }
    public float getLiveMbMidGrDb() { return dspAudioProcessor.getLiveMbMidGrDb(); }
    public float getLiveMbHighGrDb() { return dspAudioProcessor.getLiveMbHighGrDb(); }

    public void copyLatestXySamples(float[] outLeft, float[] outRight) {
        if (outLeft == null || outRight == null) return;
        synchronized (xyLock) {
            int len = Math.min(latestXyLeft.length, Math.min(outLeft.length, outRight.length));
            System.arraycopy(latestXyLeft, 0, outLeft, 0, len);
            System.arraycopy(latestXyRight, 0, outRight, 0, len);
        }
    }

    public void computeFrequencyResponseCurve(float[] outDb) {
        dspAudioProcessor.computeFrequencyResponseDb(outDb, 31f, 16000f);
    }

    public synchronized boolean isMasterBypass() { return masterBypass; }
    public synchronized int getActiveSnapshotSlot() { return activeSnapshotSlot; }
    public synchronized boolean isTubeEnabled() { return tubeEnabled; }
    public synchronized int getTubeDrivePercent() { return tubeDrivePercent; }
    public synchronized int getTapeWarmthPercent() { return tapeWarmthPercent; }
    public synchronized boolean isHeadphoneCrossfeedEnabled() { return headphoneCrossfeedEnabled; }
    public synchronized boolean isReverbEnabled() { return reverbEnabled; }
    public synchronized int getReverbRoomPercent() { return reverbRoomPercent; }
    public synchronized int getReverbDampPercent() { return reverbDampPercent; }
    public synchronized int getReverbMixPercent() { return reverbMixPercent; }
    public synchronized int getDjFilterPercent() { return djFilterPercent; }
    public synchronized boolean isVinylBrakeEnabled() { return vinylBrakeEnabled; }
    public synchronized int getActivePadMode() { return activePadMode; }
    public synchronized boolean isMultibandEnabled() { return multibandEnabled; }
    public synchronized int getMultibandDrivePercent() { return multibandDrivePercent; }
    public synchronized int getDeEsserPercent() { return deEsserPercent; }
    public synchronized int getVocalRemoverPercent() { return vocalRemoverPercent; }
    public synchronized boolean isHarmanTargetEnabled() { return harmanTargetEnabled; }
    public synchronized int getStutterDiv() { return stutterDiv; }
    public synchronized boolean hasRefProfile() { return hasRefProfile; }
    public synchronized boolean isRefMatchEnabled() { return refMatchEnabled; }

    public synchronized float[] getFiveBandFadersDb() {
        return new float[]{
                0.5f * (uiBandGainsDb[0] + uiBandGainsDb[1]),
                0.5f * (uiBandGainsDb[2] + uiBandGainsDb[3]),
                0.5f * (uiBandGainsDb[4] + uiBandGainsDb[5]),
                0.5f * (uiBandGainsDb[6] + uiBandGainsDb[7]),
                0.5f * (uiBandGainsDb[8] + uiBandGainsDb[9])
        };
    }

    public boolean consumePeakLimiterTrigger() {
        return dspAudioProcessor.consumePeakWarning();
    }

    public synchronized void setSkipSilenceEnabled(boolean enabled) {
        this.skipSilenceEnabled = enabled;
        saveState();
    }

    public synchronized void setSmoothFadeEnabled(boolean enabled) {
        this.smoothFadeEnabled = enabled;
        saveState();
    }

    public synchronized void setDynamicThemeEnabled(boolean enabled) {
        this.dynamicThemeEnabled = enabled;
        saveState();
    }

    public synchronized void setBalancePercent(int percent) {
        this.balancePercent = Math.max(-100, Math.min(100, percent));
        saveState();
        saveDeviceProfile(activeOutputDeviceKey);
        syncDspProcessorParams();
    }

    public synchronized void setSpatial8dEnabled(boolean enabled) {
        this.spatial8dEnabled = enabled;
        saveState();
        saveDeviceProfile(activeOutputDeviceKey);
        syncDspProcessorParams();
    }

    public synchronized void setCrossfadeSeconds(int seconds) {
        this.crossfadeSeconds = Math.max(0, Math.min(10, seconds));
        saveState();
    }

    public synchronized void setAutoDeviceEqEnabled(boolean enabled) {
        this.autoDeviceEqEnabled = enabled;
        saveState();
    }

    public synchronized void setHapticBassEnabled(boolean enabled) {
        this.hapticBassEnabled = enabled;
        saveState();
    }

    public synchronized void setEdgeGlowEnabled(boolean enabled) {
        this.edgeGlowEnabled = enabled;
        saveState();
    }

    public synchronized void setBassBoostPercent(int percent) {
        this.bassBoostPercent = Math.max(0, Math.min(MAX_BASS_PERCENT, percent));
        saveState();
        saveDeviceProfile(activeOutputDeviceKey);
        applyEffectsToHardware();
    }

    public synchronized void setVirtualizerPercent(int percent) {
        this.virtualizerPercent = Math.max(0, Math.min(100, percent));
        saveState();
        saveDeviceProfile(activeOutputDeviceKey);
        applyEffectsToHardware();
    }

    public synchronized void setLoudnessPercent(int percent) {
        this.loudnessPercent = Math.max(0, Math.min(100, percent));
        saveState();
        applyEffectsToHardware();
    }

    public synchronized void setCustomBandGainsDirect(float[] gains) {
        if (gains == null) return;
        for (int i = 0; i < Math.min(UI_BAND_COUNT, gains.length); i++) {
            savedCustomBandGainsDb[i] = Math.max(MIN_GAIN_DB, Math.min(MAX_GAIN_DB, gains[i]));
        }
        if (currentPreset == EqPreset.CUSTOM) {
            System.arraycopy(savedCustomBandGainsDb, 0, uiBandGainsDb, 0, UI_BAND_COUNT);
        }
        saveState();
        applyEffectsToHardware();
    }

    public synchronized void applyPreset(EqPreset preset) {
        if (preset == null) preset = EqPreset.FLAT;
        this.currentPreset = preset;
        if (preset == EqPreset.CUSTOM) {
            System.arraycopy(savedCustomBandGainsDb, 0, uiBandGainsDb, 0, UI_BAND_COUNT);
        } else {
            System.arraycopy(preset.getGainsDbCopy(), 0, uiBandGainsDb, 0, UI_BAND_COUNT);
            this.bassBoostPercent = preset.getDefaultBassSliderPercent();
            if (preset == EqPreset.BASS_BOOST && this.virtualizerPercent < 55) {
                this.virtualizerPercent = 55;
            }
        }
        saveState();
        saveDeviceProfile(activeOutputDeviceKey);
        applyEffectsToHardware();
    }

    public synchronized void setGainLDb(float gainDb) {
        this.gainLDb = clamp(gainDb, -12f, 6f);
        saveState();
        syncDspProcessorParams();
    }

    public synchronized void setGainRDb(float gainDb) {
        this.gainRDb = clamp(gainDb, -12f, 6f);
        saveState();
        syncDspProcessorParams();
    }

    public synchronized void setCompressorParams(float ratio, float thresholdDb) {
        this.compressorRatio = clamp(ratio, 1f, 8f);
        this.compressorThresholdDb = clamp(thresholdDb, -30f, 0f);
        saveState();
        syncDspProcessorParams();
    }

    public synchronized void setNoiseGateEnabled(boolean enabled) {
        this.noiseGateEnabled = enabled;
        saveState();
        syncDspProcessorParams();
    }

    public synchronized void setNoiseReductionEnabled(boolean enabled) {
        this.noiseReductionEnabled = enabled;
        saveState();
        syncDspProcessorParams();
    }

    public synchronized void setPurificationBassBoostDb(float db) {
        this.purificationBassBoostDb = clamp(db, 0f, 12f);
        saveState();
        syncDspProcessorParams();
    }

    public synchronized void setTrebleClarityDb(float db) {
        this.trebleClarityDb = clamp(db, 0f, 12f);
        saveState();
        syncDspProcessorParams();
    }

    public synchronized void setStereoWidthPercent(int percent) {
        this.stereoWidthPercent = Math.max(0, Math.min(200, percent));
        saveState();
        applyEffectsToHardware();
    }

    public synchronized void setLimiterCeilingDb(float db) {
        this.limiterCeilingDb = clamp(db, -12f, 0f);
        saveState();
        syncDspProcessorParams();
    }

    public synchronized void setAutoGainEnabled(boolean enabled) {
        this.autoGainEnabled = enabled;
        saveState();
        syncDspProcessorParams();
    }

    public synchronized void setHpfHz(int hz) {
        this.hpfHz = Math.max(20, Math.min(250, hz));
        saveState();
        syncDspProcessorParams();
    }

    public synchronized void setLpfHz(int hz) {
        this.lpfHz = Math.max(8000, Math.min(22000, hz));
        saveState();
        syncDspProcessorParams();
    }

    public synchronized void setUltraHd4kEnabled(boolean enabled) {
        this.ultraHd4kEnabled = enabled;
        saveState();
        syncDspProcessorParams();
    }

    public synchronized void setAntiCrackleEnabled(boolean enabled) {
        this.antiCrackleEnabled = enabled;
        saveState();
        syncDspProcessorParams();
    }

    public synchronized void applyQuickMode(int mode) {
        int clamped = Math.max(QUICK_NONE, Math.min(QUICK_CLUB, mode));
        if (clamped == this.activeQuickMode && clamped != QUICK_NONE) {
            // Tapping active mode turns off quick boost flag while keeping protections active
            this.activeQuickMode = QUICK_NONE;
            saveState();
            return;
        }
        this.activeQuickMode = clamped;
        this.masterBypass = false;
        this.eqEnabled = true;
        this.antiCrackleEnabled = true;
        if (clamped == QUICK_BASS_PRO) {
            this.bassBoostPercent = Math.max(this.bassBoostPercent, 178);
            this.purificationBassBoostDb = Math.max(this.purificationBassBoostDb, 6.0f);
            this.limiterCeilingDb = Math.min(this.limiterCeilingDb, -1.0f);
            this.compressorRatio = Math.max(this.compressorRatio, 3.0f);
            if (uiBandGainsDb[0] < 6.0f) {
                uiBandGainsDb[0] = 8.5f;
                uiBandGainsDb[1] = 8.0f;
                System.arraycopy(uiBandGainsDb, 0, savedCustomBandGainsDb, 0, UI_BAND_COUNT);
                currentPreset = EqPreset.CUSTOM;
            }
        } else if (clamped == QUICK_CLEAN_4K) {
            this.ultraHd4kEnabled = true;
            this.noiseGateEnabled = true;
            this.noiseReductionEnabled = true;
            this.autoGainEnabled = true;
            this.trebleClarityDb = Math.max(this.trebleClarityDb, 3.8f);
            this.limiterCeilingDb = Math.min(this.limiterCeilingDb, -0.8f);
            this.hpfHz = isSpeakerOutputActive() ? Math.max(55, this.hpfHz) : Math.min(45, this.hpfHz);
            this.lpfHz = Math.max(this.lpfHz, 20000);
        } else if (clamped == QUICK_CLUB) {
            this.ultraHd4kEnabled = true;
            this.bassBoostPercent = Math.max(this.bassBoostPercent, 165);
            this.stereoWidthPercent = Math.max(this.stereoWidthPercent, 145);
            this.virtualizerPercent = Math.max(this.virtualizerPercent, 65);
            this.purificationBassBoostDb = Math.max(this.purificationBassBoostDb, 4.5f);
            this.trebleClarityDb = Math.max(this.trebleClarityDb, 3.2f);
            this.compressorRatio = Math.max(this.compressorRatio, 3.5f);
            this.limiterCeilingDb = Math.min(this.limiterCeilingDb, -1.0f);
        }
        saveState();
        saveDeviceProfile(activeOutputDeviceKey);
        applyEffectsToHardware();
    }

    public synchronized void setFiveBandFaderDb(int groupIndex, float gainDb) {
        if (groupIndex < 0 || groupIndex > 4) return;
        float clamped = Math.max(MIN_GAIN_DB, Math.min(MAX_GAIN_DB, gainDb));
        int b1 = groupIndex * 2;
        int b2 = b1 + 1;
        uiBandGainsDb[b1] = clamped;
        uiBandGainsDb[b2] = clamped;
        System.arraycopy(uiBandGainsDb, 0, savedCustomBandGainsDb, 0, UI_BAND_COUNT);
        currentPreset = EqPreset.CUSTOM;
        saveState();
        saveDeviceProfile(activeOutputDeviceKey);
        applyEffectsToHardware();
    }

    public synchronized void setUiBandGainDb(int uiBandIndex, float gainDb) {
        if (uiBandIndex < 0 || uiBandIndex >= UI_BAND_COUNT) return;
        float clamped = Math.max(MIN_GAIN_DB, Math.min(MAX_GAIN_DB, gainDb));
        uiBandGainsDb[uiBandIndex] = clamped;
        System.arraycopy(uiBandGainsDb, 0, savedCustomBandGainsDb, 0, UI_BAND_COUNT);
        currentPreset = EqPreset.CUSTOM;
        saveState();
        saveDeviceProfile(activeOutputDeviceKey);
        applyEffectsToHardware();
    }

    public synchronized void setMasterBypass(boolean bypass) {
        this.masterBypass = bypass;
        saveState();
        applyEffectsToHardware();
    }

    public synchronized void setTubeEnabled(boolean enabled) {
        this.tubeEnabled = enabled;
        saveState();
        syncDspProcessorParams();
    }

    public synchronized void setTubeDrivePercent(int percent) {
        this.tubeDrivePercent = Math.max(0, Math.min(100, percent));
        saveState();
        syncDspProcessorParams();
    }

    public synchronized void setTapeWarmthPercent(int percent) {
        this.tapeWarmthPercent = Math.max(0, Math.min(100, percent));
        saveState();
        syncDspProcessorParams();
    }

    public synchronized void setHeadphoneCrossfeedEnabled(boolean enabled) {
        this.headphoneCrossfeedEnabled = enabled;
        saveState();
        syncDspProcessorParams();
    }

    public synchronized void setReverbEnabled(boolean enabled) {
        this.reverbEnabled = enabled;
        saveState();
        syncDspProcessorParams();
    }

    public synchronized void setReverbParams(int roomPercent, int dampPercent, int mixPercent) {
        this.reverbRoomPercent = Math.max(0, Math.min(100, roomPercent));
        this.reverbDampPercent = Math.max(0, Math.min(100, dampPercent));
        this.reverbMixPercent = Math.max(0, Math.min(100, mixPercent));
        saveState();
        syncDspProcessorParams();
    }

    public synchronized void setDjFilterPercent(int percent) {
        this.djFilterPercent = Math.max(-100, Math.min(100, percent));
        saveState();
        syncDspProcessorParams();
    }

    public synchronized void setVinylBrakeEnabled(boolean enabled) {
        this.vinylBrakeEnabled = enabled;
        saveState();
    }

    public synchronized void setActivePadMode(int padMode) {
        this.activePadMode = Math.max(NeonDspAudioProcessor.PAD_NONE,
                Math.min(NeonDspAudioProcessor.PAD_LOFI_RADIO, padMode));
        saveState();
        syncDspProcessorParams();
    }

    public synchronized void setMultibandEnabled(boolean enabled) {
        this.multibandEnabled = enabled;
        saveState();
        syncDspProcessorParams();
    }

    public synchronized void setMultibandDrivePercent(int percent) {
        this.multibandDrivePercent = Math.max(0, Math.min(100, percent));
        saveState();
        syncDspProcessorParams();
    }

    public synchronized void setDeEsserPercent(int percent) {
        this.deEsserPercent = Math.max(0, Math.min(100, percent));
        saveState();
        syncDspProcessorParams();
    }

    public synchronized void setVocalRemoverPercent(int percent) {
        this.vocalRemoverPercent = Math.max(0, Math.min(100, percent));
        saveState();
        syncDspProcessorParams();
    }

    public synchronized void setHarmanTargetEnabled(boolean enabled) {
        this.harmanTargetEnabled = enabled;
        saveState();
        syncDspProcessorParams();
    }

    public synchronized void setStutterDiv(int stutterDiv) {
        this.stutterDiv = Math.max(0, Math.min(3, stutterDiv));
        syncDspProcessorParams();
    }

    public synchronized void setKaossXy(int filterPercent, int reverbMixPct, boolean reverbOn) {
        this.djFilterPercent = Math.max(-100, Math.min(100, filterPercent));
        this.reverbMixPercent = Math.max(0, Math.min(100, reverbMixPct));
        this.reverbEnabled = reverbOn;
        saveState();
        syncDspProcessorParams();
    }

    public synchronized void captureReferenceProfile() {
        synchronized (xyLock) {
            System.arraycopy(latestBand10Db, 0, refProfileBandsDb, 0, UI_BAND_COUNT);
        }
        hasRefProfile = true;
        saveState();
    }

    public synchronized void setRefMatchEnabled(boolean enabled) {
        if (enabled && !hasRefProfile) {
            captureReferenceProfile();
        }
        if (enabled == refMatchEnabled) {
            return;
        }
        refMatchEnabled = enabled;
        if (enabled) {
            System.arraycopy(uiBandGainsDb, 0, preMatchBandGainsDb, 0, UI_BAND_COUNT);
            preMatchPreset = currentPreset;
            hasPreMatchBackup = true;

            float[] cur = new float[UI_BAND_COUNT];
            synchronized (xyLock) {
                System.arraycopy(latestBand10Db, 0, cur, 0, UI_BAND_COUNT);
            }
            float refMean = 0f;
            float curMean = 0f;
            for (int i = 0; i < UI_BAND_COUNT; i++) {
                refMean += refProfileBandsDb[i];
                curMean += cur[i];
            }
            refMean /= UI_BAND_COUNT;
            curMean /= UI_BAND_COUNT;

            for (int i = 0; i < UI_BAND_COUNT; i++) {
                float refNorm = refProfileBandsDb[i] - refMean;
                float curNorm = cur[i] - curMean;
                float delta = clamp((refNorm - curNorm) * 0.65f, -9.0f, 9.0f);
                float nextGain = clamp(preMatchBandGainsDb[i] + delta, MIN_GAIN_DB, MAX_GAIN_DB);
                uiBandGainsDb[i] = nextGain;
                savedCustomBandGainsDb[i] = nextGain;
            }
            currentPreset = EqPreset.CUSTOM;
            eqEnabled = true;
        } else if (hasPreMatchBackup) {
            System.arraycopy(preMatchBandGainsDb, 0, uiBandGainsDb, 0, UI_BAND_COUNT);
            if (preMatchPreset == EqPreset.CUSTOM) {
                System.arraycopy(preMatchBandGainsDb, 0, savedCustomBandGainsDb, 0, UI_BAND_COUNT);
            }
            currentPreset = preMatchPreset;
            hasPreMatchBackup = false;
        }
        saveState();
        applyEffectsToHardware();
    }

    public synchronized void saveSnapshot(int slot) {
        if (prefs == null) return;
        int normalized = slot == 1 ? 1 : 0;
        String p = "snap_" + normalized + "_";
        SharedPreferences.Editor ed = prefs.edit();
        ed.putBoolean(p + "saved", true);
        ed.putBoolean(p + "eq_enabled", eqEnabled);
        ed.putString(p + "preset", currentPreset.getKey());
        for (int i = 0; i < UI_BAND_COUNT; i++) {
            ed.putFloat(p + "band_" + i, uiBandGainsDb[i]);
            ed.putFloat(p + "custom_" + i, savedCustomBandGainsDb[i]);
        }
        ed.putInt(p + "bass", bassBoostPercent);
        ed.putInt(p + "virt", virtualizerPercent);
        ed.putInt(p + "loudness", loudnessPercent);
        ed.putInt(p + "balance", balancePercent);
        ed.putBoolean(p + "spatial8d", spatial8dEnabled);
        ed.putFloat(p + "gain_l", gainLDb);
        ed.putFloat(p + "gain_r", gainRDb);
        ed.putFloat(p + "comp_ratio", compressorRatio);
        ed.putFloat(p + "comp_thresh", compressorThresholdDb);
        ed.putBoolean(p + "noise_gate", noiseGateEnabled);
        ed.putBoolean(p + "noise_red", noiseReductionEnabled);
        ed.putFloat(p + "purify_bass", purificationBassBoostDb);
        ed.putFloat(p + "treble_clarity", trebleClarityDb);
        ed.putInt(p + "stereo_width", stereoWidthPercent);
        ed.putFloat(p + "limiter", limiterCeilingDb);
        ed.putBoolean(p + "auto_gain", autoGainEnabled);
        ed.putInt(p + "hpf", hpfHz);
        ed.putInt(p + "lpf", lpfHz);
        ed.putBoolean(p + "tube_on", tubeEnabled);
        ed.putInt(p + "tube_drive", tubeDrivePercent);
        ed.putInt(p + "tape_warmth", tapeWarmthPercent);
        ed.putBoolean(p + "crossfeed", headphoneCrossfeedEnabled);
        ed.putBoolean(p + "reverb_on", reverbEnabled);
        ed.putInt(p + "reverb_room", reverbRoomPercent);
        ed.putInt(p + "reverb_damp", reverbDampPercent);
        ed.putInt(p + "reverb_mix", reverbMixPercent);
        ed.putInt(p + "dj_filter", djFilterPercent);
        ed.putInt(p + "pad", activePadMode);
        ed.putBoolean(p + "mb_on", multibandEnabled);
        ed.putInt(p + "mb_drive", multibandDrivePercent);
        ed.putInt(p + "de_ess", deEsserPercent);
        ed.putInt(p + "vocal_rem", vocalRemoverPercent);
        ed.putBoolean(p + "harman", harmanTargetEnabled);
        ed.apply();
    }

    public synchronized void switchToSnapshot(int slot) {
        int normalized = slot == 1 ? 1 : 0;
        if (prefs != null) {
            saveSnapshot(activeSnapshotSlot);
            String targetPrefix = "snap_" + normalized + "_";
            if (!prefs.getBoolean(targetPrefix + "saved", false)) {
                if (normalized == 1) {
                    // Seed Slot B with a rich "Studio Warmth" preset on first switch for instant A/B compare
                    tubeEnabled = true;
                    tubeDrivePercent = 42;
                    tapeWarmthPercent = 35;
                    reverbEnabled = true;
                    reverbRoomPercent = 48;
                    reverbDampPercent = 32;
                    reverbMixPercent = 22;
                    purificationBassBoostDb = Math.max(purificationBassBoostDb, 4.0f);
                    trebleClarityDb = Math.max(trebleClarityDb, 3.5f);
                    stereoWidthPercent = Math.max(stereoWidthPercent, 135);
                }
                saveSnapshot(normalized);
            } else {
                eqEnabled = prefs.getBoolean(targetPrefix + "eq_enabled", true);
                currentPreset = EqPreset.fromKey(prefs.getString(targetPrefix + "preset", currentPreset.getKey()));
                for (int i = 0; i < UI_BAND_COUNT; i++) {
                    uiBandGainsDb[i] = prefs.getFloat(targetPrefix + "band_" + i, uiBandGainsDb[i]);
                    savedCustomBandGainsDb[i] = prefs.getFloat(targetPrefix + "custom_" + i, savedCustomBandGainsDb[i]);
                }
                bassBoostPercent = prefs.getInt(targetPrefix + "bass", bassBoostPercent);
                virtualizerPercent = prefs.getInt(targetPrefix + "virt", virtualizerPercent);
                loudnessPercent = prefs.getInt(targetPrefix + "loudness", loudnessPercent);
                balancePercent = prefs.getInt(targetPrefix + "balance", balancePercent);
                spatial8dEnabled = prefs.getBoolean(targetPrefix + "spatial8d", spatial8dEnabled);
                gainLDb = prefs.getFloat(targetPrefix + "gain_l", gainLDb);
                gainRDb = prefs.getFloat(targetPrefix + "gain_r", gainRDb);
                compressorRatio = prefs.getFloat(targetPrefix + "comp_ratio", compressorRatio);
                compressorThresholdDb = prefs.getFloat(targetPrefix + "comp_thresh", compressorThresholdDb);
                noiseGateEnabled = prefs.getBoolean(targetPrefix + "noise_gate", noiseGateEnabled);
                noiseReductionEnabled = prefs.getBoolean(targetPrefix + "noise_red", noiseReductionEnabled);
                purificationBassBoostDb = prefs.getFloat(targetPrefix + "purify_bass", purificationBassBoostDb);
                trebleClarityDb = prefs.getFloat(targetPrefix + "treble_clarity", trebleClarityDb);
                stereoWidthPercent = prefs.getInt(targetPrefix + "stereo_width", stereoWidthPercent);
                limiterCeilingDb = prefs.getFloat(targetPrefix + "limiter", limiterCeilingDb);
                autoGainEnabled = prefs.getBoolean(targetPrefix + "auto_gain", autoGainEnabled);
                hpfHz = prefs.getInt(targetPrefix + "hpf", hpfHz);
                lpfHz = prefs.getInt(targetPrefix + "lpf", lpfHz);
                tubeEnabled = prefs.getBoolean(targetPrefix + "tube_on", tubeEnabled);
                tubeDrivePercent = prefs.getInt(targetPrefix + "tube_drive", tubeDrivePercent);
                tapeWarmthPercent = prefs.getInt(targetPrefix + "tape_warmth", tapeWarmthPercent);
                headphoneCrossfeedEnabled = prefs.getBoolean(targetPrefix + "crossfeed", headphoneCrossfeedEnabled);
                reverbEnabled = prefs.getBoolean(targetPrefix + "reverb_on", reverbEnabled);
                reverbRoomPercent = prefs.getInt(targetPrefix + "reverb_room", reverbRoomPercent);
                reverbDampPercent = prefs.getInt(targetPrefix + "reverb_damp", reverbDampPercent);
                reverbMixPercent = prefs.getInt(targetPrefix + "reverb_mix", reverbMixPercent);
                djFilterPercent = prefs.getInt(targetPrefix + "dj_filter", djFilterPercent);
                activePadMode = prefs.getInt(targetPrefix + "pad", activePadMode);
                multibandEnabled = prefs.getBoolean(targetPrefix + "mb_on", multibandEnabled);
                multibandDrivePercent = prefs.getInt(targetPrefix + "mb_drive", multibandDrivePercent);
                deEsserPercent = prefs.getInt(targetPrefix + "de_ess", deEsserPercent);
                vocalRemoverPercent = prefs.getInt(targetPrefix + "vocal_rem", vocalRemoverPercent);
                harmanTargetEnabled = prefs.getBoolean(targetPrefix + "harman", harmanTargetEnabled);
            }
        }
        activeSnapshotSlot = normalized;
        saveState();
        applyEffectsToHardware();
    }

    private void syncDspProcessorParams() {
        dspAudioProcessor.updateParams(
                eqEnabled,
                uiBandGainsDb,
                bassBoostPercent,
                virtualizerPercent,
                loudnessPercent,
                balancePercent,
                spatial8dEnabled
        );
        dspAudioProcessor.updatePurificationParams(
                gainLDb,
                gainRDb,
                compressorRatio,
                compressorThresholdDb,
                noiseGateEnabled,
                noiseReductionEnabled,
                purificationBassBoostDb,
                trebleClarityDb,
                stereoWidthPercent,
                limiterCeilingDb,
                autoGainEnabled,
                hpfHz,
                lpfHz
        );
        dspAudioProcessor.updateStudioParams(
                masterBypass,
                tubeEnabled,
                tubeDrivePercent,
                tapeWarmthPercent,
                headphoneCrossfeedEnabled,
                reverbEnabled,
                reverbRoomPercent,
                reverbDampPercent,
                reverbMixPercent,
                djFilterPercent,
                activePadMode
        );
        dspAudioProcessor.updateAdvancedMasteringParams(
                multibandEnabled,
                multibandDrivePercent,
                deEsserPercent,
                vocalRemoverPercent,
                harmanTargetEnabled,
                stutterDiv,
                latestLiveBpm
        );
        dspAudioProcessor.updatePurifier4kAndProtection(
                ultraHd4kEnabled,
                antiCrackleEnabled,
                DEVICE_SPEAKER.equals(activeOutputDeviceKey)
        );
    }

    private void saveState() {
        if (prefs == null) return;
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(KEY_EQ_ENABLED, eqEnabled);
        editor.putString(KEY_PRESET, currentPreset.getKey());
        editor.putInt(KEY_BASS_V2_PERCENT, bassBoostPercent);
        editor.putInt(KEY_VIRTUALIZER_PERCENT, virtualizerPercent);
        editor.putInt(KEY_LOUDNESS_PERCENT, loudnessPercent);
        editor.putInt(KEY_BALANCE_PERCENT, balancePercent);
        editor.putBoolean(KEY_SPATIAL_8D, spatial8dEnabled);
        editor.putInt(KEY_CROSSFADE_SEC, crossfadeSeconds);
        editor.putBoolean(KEY_AUTO_DEVICE_EQ, autoDeviceEqEnabled);
        editor.putBoolean(KEY_HAPTIC_BASS, hapticBassEnabled);
        editor.putBoolean(KEY_EDGE_GLOW, edgeGlowEnabled);
        editor.putString(KEY_LAST_DEVICE, activeOutputDeviceKey);
        editor.putBoolean(KEY_SKIP_SILENCE, skipSilenceEnabled);
        editor.putBoolean(KEY_SMOOTH_FADE, smoothFadeEnabled);
        editor.putBoolean(KEY_DYNAMIC_THEME, dynamicThemeEnabled);
        editor.putFloat(KEY_GAIN_L_DB, gainLDb);
        editor.putFloat(KEY_GAIN_R_DB, gainRDb);
        editor.putFloat(KEY_COMP_RATIO, compressorRatio);
        editor.putFloat(KEY_COMP_THRESH_DB, compressorThresholdDb);
        editor.putBoolean(KEY_NOISE_GATE, noiseGateEnabled);
        editor.putBoolean(KEY_NOISE_REDUCTION, noiseReductionEnabled);
        editor.putFloat(KEY_PURIFY_BASS_DB, purificationBassBoostDb);
        editor.putFloat(KEY_TREBLE_CLARITY_DB, trebleClarityDb);
        editor.putInt(KEY_STEREO_WIDTH_PCT, stereoWidthPercent);
        editor.putFloat(KEY_LIMITER_DB, limiterCeilingDb);
        editor.putBoolean(KEY_AUTO_GAIN, autoGainEnabled);
        editor.putInt(KEY_HPF_HZ, hpfHz);
        editor.putInt(KEY_LPF_HZ, lpfHz);
        editor.putBoolean(KEY_ULTRA_HD_4K, ultraHd4kEnabled);
        editor.putBoolean(KEY_ANTI_CRACKLE, antiCrackleEnabled);
        editor.putInt(KEY_QUICK_MODE, activeQuickMode);
        editor.putBoolean(KEY_MASTER_BYPASS, masterBypass);
        editor.putInt(KEY_ACTIVE_SNAPSHOT, activeSnapshotSlot);
        editor.putBoolean(KEY_TUBE_ENABLED, tubeEnabled);
        editor.putInt(KEY_TUBE_DRIVE_PCT, tubeDrivePercent);
        editor.putInt(KEY_TAPE_WARMTH_PCT, tapeWarmthPercent);
        editor.putBoolean(KEY_HEADPHONE_CROSSFEED, headphoneCrossfeedEnabled);
        editor.putBoolean(KEY_REVERB_ENABLED, reverbEnabled);
        editor.putInt(KEY_REVERB_ROOM_PCT, reverbRoomPercent);
        editor.putInt(KEY_REVERB_DAMP_PCT, reverbDampPercent);
        editor.putInt(KEY_REVERB_MIX_PCT, reverbMixPercent);
        editor.putInt(KEY_DJ_FILTER_PCT, djFilterPercent);
        editor.putBoolean(KEY_VINYL_BRAKE, vinylBrakeEnabled);
        editor.putInt(KEY_ACTIVE_PAD_MODE, activePadMode);
        editor.putBoolean(KEY_MULTIBAND_ENABLED, multibandEnabled);
        editor.putInt(KEY_MULTIBAND_DRIVE_PCT, multibandDrivePercent);
        editor.putInt(KEY_DE_ESSER_PCT, deEsserPercent);
        editor.putInt(KEY_VOCAL_REMOVER_PCT, vocalRemoverPercent);
        editor.putBoolean(KEY_HARMAN_TARGET, harmanTargetEnabled);
        editor.putBoolean(KEY_HAS_REF_PROFILE, hasRefProfile);
        editor.putBoolean(KEY_REF_MATCH_ENABLED, refMatchEnabled);
        for (int i = 0; i < UI_BAND_COUNT; i++) {
            editor.putFloat(KEY_BAND_PREFIX + i, uiBandGainsDb[i]);
            editor.putFloat(KEY_CUSTOM_BAND_PREFIX + i, savedCustomBandGainsDb[i]);
            editor.putFloat(KEY_REF_PROFILE_PREFIX + i, refProfileBandsDb[i]);
        }
        editor.apply();
    }

    private void applyEffectsToHardware() {
        syncDspProcessorParams();
        boolean hwFxActive = eqEnabled && !masterBypass;

        // Hardware Equalizer provides complementary analog session curve (25% share so DSP + HW never over-clip)
        if (equalizer != null) {
            try {
                equalizer.setEnabled(hwFxActive);
                short numHwBands = equalizer.getNumberOfBands();
                short[] range = equalizer.getBandLevelRange();
                short minLevel = range[0];
                short maxLevel = range[1];

                for (short hwBand = 0; hwBand < numHwBands; hwBand++) {
                    int centerFreqHz = equalizer.getCenterFreq(hwBand) / 1000;
                    float interpolatedDb = interpolateDbForFrequency(centerFreqHz) * 0.25f;
                    float ratio = (interpolatedDb - MIN_GAIN_DB) / (MAX_GAIN_DB - MIN_GAIN_DB);
                    short targetMb = (short) Math.round(minLevel + ratio * (maxLevel - minLevel));
                    targetMb = (short) Math.max(minLevel, Math.min(maxLevel, targetMb));
                    equalizer.setBandLevel(hwBand, targetMb);
                }
            } catch (Throwable ignored) {}
        }

        // Android hardware BassBoost: guaranteed strength 1000 (MAX) when Bass Boost preset ON or Bass >= 170%
        if (bassBoost != null) {
            try {
                int hwStrength;
                if (currentPreset == EqPreset.BASS_BOOST && hwFxActive && bassBoostPercent >= 100) {
                    hwStrength = 1000;
                } else if (bassBoostPercent <= 100) {
                    hwStrength = bassBoostPercent * 2; // subtle hardware support below 100
                } else {
                    hwStrength = Math.min(1000, Math.max(currentPreset.getBassBoostStrength(), (bassBoostPercent - 100) * 10));
                }
                bassBoost.setEnabled(hwFxActive && bassBoostPercent > 20 && hwStrength > 0);
                if (bassBoost.getStrengthSupported()) {
                    bassBoost.setStrength((short) Math.max(0, Math.min(1000, hwStrength)));
                }
            } catch (Throwable ignored) {}
        }

        if (virtualizer != null) {
            try {
                virtualizer.setEnabled(hwFxActive && virtualizerPercent > 0);
                if (virtualizer.getStrengthSupported()) {
                    virtualizer.setStrength((short) (virtualizerPercent * 10));
                }
            } catch (Throwable ignored) {}
        }

        if (loudnessEnhancer != null) {
            try {
                int bassAutoGain = bassBoostPercent > 120 ? (bassBoostPercent - 120) * 4 : 0;
                int targetMb = loudnessPercent * 12 + bassAutoGain;
                loudnessEnhancer.setEnabled(hwFxActive && targetMb > 0);
                loudnessEnhancer.setTargetGain(Math.min(1600, targetMb));
            } catch (Throwable ignored) {}
        }
    }

    private float interpolateDbForFrequency(int freqHz) {
        if (freqHz <= UI_BAND_FREQUENCIES_HZ[0]) return uiBandGainsDb[0];
        if (freqHz >= UI_BAND_FREQUENCIES_HZ[UI_BAND_COUNT - 1]) return uiBandGainsDb[UI_BAND_COUNT - 1];

        for (int i = 0; i < UI_BAND_COUNT - 1; i++) {
            int f1 = UI_BAND_FREQUENCIES_HZ[i];
            int f2 = UI_BAND_FREQUENCIES_HZ[i + 1];
            if (freqHz >= f1 && freqHz <= f2) {
                double logPos = (Math.log(freqHz) - Math.log(f1)) / (Math.log(f2) - Math.log(f1));
                return (float) (uiBandGainsDb[i] + logPos * (uiBandGainsDb[i + 1] - uiBandGainsDb[i]));
            }
        }
        return 0f;
    }

    public synchronized void releaseVisualizer() {
        if (visualizer != null) {
            try {
                visualizer.setEnabled(false);
                visualizer.release();
            } catch (Throwable ignored) {}
            visualizer = null;
        }
        activeCaptureDivisor = -1;
    }

    public synchronized void releaseHardwareFx() {
        releaseVisualizer();
        if (equalizer != null) {
            try { equalizer.release(); } catch (Throwable ignored) {}
            equalizer = null;
        }
        if (bassBoost != null) {
            try { bassBoost.release(); } catch (Throwable ignored) {}
            bassBoost = null;
        }
        if (virtualizer != null) {
            try { virtualizer.release(); } catch (Throwable ignored) {}
            virtualizer = null;
        }
        if (loudnessEnhancer != null) {
            try { loudnessEnhancer.release(); } catch (Throwable ignored) {}
            loudnessEnhancer = null;
        }
        currentSessionId = 0;
    }
}
