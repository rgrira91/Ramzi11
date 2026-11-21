package com.example.util;

import android.app.ActivityManager;
import android.content.ComponentCallbacks2;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.BatteryManager;
import android.os.Build;
import android.os.PowerManager;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.WindowManager;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Self-adaptive media & device resource optimizer.
 * Monitors RAM pressure, low-RAM status, thermal state, power-save mode, and display refresh rate
 * to prevent audio stutter, UI jank, memory leaks, and excessive battery/CPU drain.
 */
public final class DeviceResourceOptimizer {
    public enum OptimizationMode {
        AUTO("auto"),
        PERFORMANCE("performance"),
        ECO_BATTERY("eco");

        public final String key;

        OptimizationMode(String key) {
            this.key = key;
        }

        public static OptimizationMode fromKey(@Nullable String key) {
            if (key != null) {
                for (OptimizationMode mode : values()) {
                    if (mode.key.equals(key)) return mode;
                }
            }
            return AUTO;
        }
    }

    public enum EffectiveTier {
        HIGH,
        BALANCED,
        POWER_SAVER
    }

    public static final class DeviceTelemetry {
        public final long totalRamMb;
        public final long availRamMb;
        public final int appHeapUsedPercent;
        public final boolean isLowRamDevice;
        public final boolean isSystemLowMemory;
        public final boolean isPowerSaveMode;
        public final boolean isThermalThrottled;
        public final int cpuCores;
        public final int displayRefreshHz;
        public final int batteryPercent;
        public final boolean isCharging;
        public final EffectiveTier effectiveTier;

        DeviceTelemetry(
                long totalRamMb,
                long availRamMb,
                int appHeapUsedPercent,
                boolean isLowRamDevice,
                boolean isSystemLowMemory,
                boolean isPowerSaveMode,
                boolean isThermalThrottled,
                int cpuCores,
                int displayRefreshHz,
                int batteryPercent,
                boolean isCharging,
                EffectiveTier effectiveTier) {
            this.totalRamMb = totalRamMb;
            this.availRamMb = availRamMb;
            this.appHeapUsedPercent = appHeapUsedPercent;
            this.isLowRamDevice = isLowRamDevice;
            this.isSystemLowMemory = isSystemLowMemory;
            this.isPowerSaveMode = isPowerSaveMode;
            this.isThermalThrottled = isThermalThrottled;
            this.cpuCores = cpuCores;
            this.displayRefreshHz = displayRefreshHz;
            this.batteryPercent = batteryPercent;
            this.isCharging = isCharging;
            this.effectiveTier = effectiveTier;
        }
    }

    public interface OptimizerListener {
        void onOptimizationChanged(EffectiveTier tier);
    }

    private static final String PREFS_OPTIMIZER = "neon_wave_optimizer";
    private static final String KEY_MODE = "optimization_mode";
    private static final String KEY_ANTI_STUTTER = "media_anti_stutter";
    private static final String KEY_AUDIO_OFFLOAD = "media_audio_offload";

    private static volatile DeviceResourceOptimizer instance;

    private final Context appContext;
    private final SharedPreferences prefs;
    private final CopyOnWriteArraySet<OptimizerListener> listeners = new CopyOnWriteArraySet<>();

    private OptimizationMode mode;
    private boolean mediaAntiStutterEnabled;
    private boolean audioOffloadPreferred;
    private volatile EffectiveTier lastComputedTier = EffectiveTier.BALANCED;

    private DeviceResourceOptimizer(Context context) {
        this.appContext = context.getApplicationContext();
        this.prefs = appContext.getSharedPreferences(PREFS_OPTIMIZER, Context.MODE_PRIVATE);
        this.mode = OptimizationMode.fromKey(prefs.getString(KEY_MODE, OptimizationMode.AUTO.key));
        this.mediaAntiStutterEnabled = prefs.getBoolean(KEY_ANTI_STUTTER, true);
        this.audioOffloadPreferred = prefs.getBoolean(KEY_AUDIO_OFFLOAD, true);
        this.lastComputedTier = evaluateTelemetry().effectiveTier;
    }

    public static DeviceResourceOptimizer getInstance(@NonNull Context context) {
        if (instance == null) {
            synchronized (DeviceResourceOptimizer.class) {
                if (instance == null) {
                    instance = new DeviceResourceOptimizer(context);
                }
            }
        }
        return instance;
    }

    public void addListener(@NonNull OptimizerListener listener) {
        listeners.add(listener);
    }

    public void removeListener(@NonNull OptimizerListener listener) {
        listeners.remove(listener);
    }

    public OptimizationMode getMode() {
        return mode;
    }

    public void setMode(@NonNull OptimizationMode newMode) {
        this.mode = newMode;
        prefs.edit().putString(KEY_MODE, newMode.key).apply();
        notifyListenersIfNeeded();
    }

    public boolean isMediaAntiStutterEnabled() {
        return mediaAntiStutterEnabled;
    }

    public void setMediaAntiStutterEnabled(boolean enabled) {
        this.mediaAntiStutterEnabled = enabled;
        prefs.edit().putBoolean(KEY_ANTI_STUTTER, enabled).apply();
        notifyListenersForce();
    }

    public boolean isAudioOffloadPreferred() {
        return audioOffloadPreferred;
    }

    public void setAudioOffloadPreferred(boolean preferred) {
        this.audioOffloadPreferred = preferred;
        prefs.edit().putBoolean(KEY_AUDIO_OFFLOAD, preferred).apply();
        notifyListenersForce();
    }

    public EffectiveTier getEffectiveTier() {
        EffectiveTier computed = evaluateTelemetry().effectiveTier;
        lastComputedTier = computed;
        return computed;
    }

    @NonNull
    public DeviceTelemetry evaluateTelemetry() {
        long totalRamMb = 4096L;
        long availRamMb = 1536L;
        boolean lowRam = false;
        boolean sysLowMem = false;

        try {
            ActivityManager am = (ActivityManager) appContext.getSystemService(Context.ACTIVITY_SERVICE);
            if (am != null) {
                lowRam = am.isLowRamDevice();
                ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
                am.getMemoryInfo(mi);
                if (mi.totalMem > 0) {
                    totalRamMb = mi.totalMem / (1024L * 1024L);
                }
                availRamMb = Math.max(0L, mi.availMem / (1024L * 1024L));
                sysLowMem = mi.lowMemory;
            }
        } catch (Throwable ignored) {}

        Runtime rt = Runtime.getRuntime();
        long maxHeap = Math.max(1L, rt.maxMemory());
        long usedHeap = Math.max(0L, rt.totalMemory() - rt.freeMemory());
        int heapUsedPct = (int) Math.min(100L, (usedHeap * 100L) / maxHeap);
        int cpuCores = Math.max(1, rt.availableProcessors());

        boolean powerSave = false;
        boolean thermalThrottled = false;
        try {
            PowerManager pm = (PowerManager) appContext.getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                powerSave = pm.isPowerSaveMode();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    int status = pm.getCurrentThermalStatus();
                    thermalThrottled = status >= PowerManager.THERMAL_STATUS_MODERATE;
                }
            }
        } catch (Throwable ignored) {}

        int refreshHz = detectDisplayRefreshHz();

        int batteryPct = 100;
        boolean charging = false;
        try {
            Intent battStatus = appContext.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (battStatus != null) {
                int level = battStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = battStatus.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
                if (level >= 0 && scale > 0) {
                    batteryPct = Math.max(0, Math.min(100, (level * 100) / scale));
                }
                int plugged = battStatus.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
                int status = battStatus.getIntExtra(BatteryManager.EXTRA_STATUS, 0);
                charging = plugged != 0 || status == BatteryManager.BATTERY_STATUS_CHARGING;
            }
        } catch (Throwable ignored) {}

        EffectiveTier tier;
        if (mode == OptimizationMode.ECO_BATTERY) {
            tier = EffectiveTier.POWER_SAVER;
        } else if (mode == OptimizationMode.PERFORMANCE) {
            // Even in explicit performance mode, protect stability if system hits critical low memory or severe heat
            if (sysLowMem || heapUsedPct >= 92) {
                tier = EffectiveTier.BALANCED;
            } else {
                tier = EffectiveTier.HIGH;
            }
        } else {
            // AUTO self-adaptive logic: battery, thermal, RAM, and CPU aware
            if (powerSave
                    || thermalThrottled
                    || lowRam
                    || sysLowMem
                    || availRamMb < 450L
                    || heapUsedPct >= 85
                    || (!charging && batteryPct <= 15)) {
                tier = EffectiveTier.POWER_SAVER;
            } else if (totalRamMb >= 5500L
                    && availRamMb >= 1400L
                    && cpuCores >= 6
                    && heapUsedPct < 65
                    && (charging || batteryPct > 30)) {
                tier = EffectiveTier.HIGH;
            } else {
                tier = EffectiveTier.BALANCED;
            }
        }

        return new DeviceTelemetry(
                totalRamMb,
                availRamMb,
                heapUsedPct,
                lowRam,
                sysLowMem,
                powerSave,
                thermalThrottled,
                cpuCores,
                refreshHz,
                batteryPct,
                charging,
                tier
        );
    }

    private int detectDisplayRefreshHz() {
        try {
            WindowManager wm = (WindowManager) appContext.getSystemService(Context.WINDOW_SERVICE);
            if (wm != null) {
                Display display = wm.getDefaultDisplay();
                if (display != null) {
                    float hz = display.getRefreshRate();
                    if (hz >= 24f && hz <= 240f) {
                        return Math.round(hz);
                    }
                }
            }
            DisplayMetrics dm = appContext.getResources().getDisplayMetrics();
            if (dm != null && dm.densityDpi > 0) {
                return 60;
            }
        } catch (Throwable ignored) {}
        return 60;
    }

    /**
     * Caps visual frame rate to avoid burning 144Hz compositor cycles on mid-range GPUs/CPUs
     * while preserving buttery smoothness.
     */
    public int getTargetVisualFps() {
        EffectiveTier tier = getEffectiveTier();
        if (tier == EffectiveTier.POWER_SAVER) return 20;
        if (tier == EffectiveTier.BALANCED) return 38;
        return 58;
    }

    public int getReactiveButtonFps() {
        EffectiveTier tier = getEffectiveTier();
        if (tier == EffectiveTier.POWER_SAVER) return 14;
        if (tier == EffectiveTier.BALANCED) return 24;
        return 32;
    }

    public long getTapSpacingMs() {
        EffectiveTier tier = getEffectiveTier();
        if (tier == EffectiveTier.POWER_SAVER) return 46L;
        if (tier == EffectiveTier.BALANCED) return 30L;
        return 22L;
    }

    public int getWaveformParticleCount() {
        EffectiveTier tier = getEffectiveTier();
        if (tier == EffectiveTier.POWER_SAVER) return 0;
        if (tier == EffectiveTier.BALANCED) return 7;
        return 14;
    }

    public int getWaterfallHistoryRows() {
        EffectiveTier tier = getEffectiveTier();
        if (tier == EffectiveTier.POWER_SAVER) return 14;
        if (tier == EffectiveTier.BALANCED) return 22;
        return 30;
    }

    public int getStageParticleCount() {
        EffectiveTier tier = getEffectiveTier();
        if (tier == EffectiveTier.POWER_SAVER) return 8;
        if (tier == EffectiveTier.BALANCED) return 16;
        return 26;
    }

    public boolean shouldEnableStageGyroscope() {
        return getEffectiveTier() != EffectiveTier.POWER_SAVER;
    }

    public int getStudioRackTargetFps() {
        EffectiveTier tier = getEffectiveTier();
        if (tier == EffectiveTier.POWER_SAVER) return 18;
        if (tier == EffectiveTier.BALANCED) return 30;
        return 45;
    }

    public boolean shouldAllowStrobeFlashes() {
        return getEffectiveTier() != EffectiveTier.POWER_SAVER;
    }

    public long getProgressPollIntervalMs(boolean isPlaying) {
        if (!isPlaying) return 1200L;
        EffectiveTier tier = getEffectiveTier();
        if (tier == EffectiveTier.POWER_SAVER) return 750L;
        if (tier == EffectiveTier.BALANCED) return 450L;
        return 260L;
    }

    public boolean shouldAnimateThemeTransitions() {
        return getEffectiveTier() != EffectiveTier.POWER_SAVER;
    }

    public int getVisualizerCaptureRateDivisor() {
        EffectiveTier tier = getEffectiveTier();
        if (tier == EffectiveTier.POWER_SAVER) return 4;
        if (tier == EffectiveTier.BALANCED) return 3;
        return 2;
    }

    public int getArtCacheDivider() {
        EffectiveTier tier = getEffectiveTier();
        if (tier == EffectiveTier.POWER_SAVER) return 18;
        if (tier == EffectiveTier.BALANCED) return 12;
        return 8;
    }

    public int getMaxAlbumArtPx(int requestedPx) {
        EffectiveTier tier = getEffectiveTier();
        int cap = tier == EffectiveTier.POWER_SAVER ? 128 : (tier == EffectiveTier.BALANCED ? 192 : 288);
        return Math.max(64, Math.min(cap, requestedPx));
    }

    public boolean shouldUseAudioOffload(boolean eqEnabled) {
        if (!audioOffloadPreferred) return false;
        // Keep offload disabled whenever EQ/Bass DSP is enabled so sliders and BassBoost always work reliably
        return !eqEnabled;
    }

    /**
     * Returns adaptive ExoPlayer LoadControl parameters:
     * [0] minBufferMs, [1] maxBufferMs, [2] bufferForPlaybackMs, [3] bufferForPlaybackAfterRebufferMs, [4] targetBufferBytes
     */
    @NonNull
    public int[] getExoBufferConfig() {
        EffectiveTier tier = getEffectiveTier();
        if (!mediaAntiStutterEnabled) {
            return new int[]{10_000, 25_000, 1_500, 3_000, 6 * 1024 * 1024};
        }
        if (tier == EffectiveTier.POWER_SAVER) {
            // Larger min buffer reduces storage/CPU wakeups while capping RAM target bytes
            return new int[]{25_000, 45_000, 2_000, 4_000, 4 * 1024 * 1024};
        }
        if (tier == EffectiveTier.BALANCED) {
            return new int[]{20_000, 50_000, 1_500, 3_000, 8 * 1024 * 1024};
        }
        return new int[]{25_000, 60_000, 1_200, 2_500, 12 * 1024 * 1024};
    }

    public boolean isSevereMemoryTrimLevel(int trimLevel) {
        return trimLevel >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL
                || trimLevel >= ComponentCallbacks2.TRIM_MEMORY_COMPLETE
                || trimLevel == ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN;
    }

    public void triggerManualMemoryOptimization() {
        notifyListenersForce();
    }

    private void notifyListenersIfNeeded() {
        EffectiveTier current = evaluateTelemetry().effectiveTier;
        if (current != lastComputedTier) {
            lastComputedTier = current;
            for (OptimizerListener l : listeners) {
                l.onOptimizationChanged(current);
            }
        }
    }

    private void notifyListenersForce() {
        EffectiveTier current = evaluateTelemetry().effectiveTier;
        lastComputedTier = current;
        for (OptimizerListener l : listeners) {
            l.onOptimizationChanged(current);
        }
    }
}
