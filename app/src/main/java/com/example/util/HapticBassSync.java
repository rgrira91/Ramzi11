package com.example.util;

import android.content.Context;
import android.os.Build;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;

/**
 * Battery-aware Haptic Bass Sync: translates sharp sub-bass beat onsets
 * into subtle micro-vibration pulses when enabled and not in power-saver mode.
 */
public final class HapticBassSync {
    private static final long MIN_BEAT_INTERVAL_MS = 125L;

    private final Vibrator vibrator;
    private float smoothedBass = 0f;
    private long lastPulseMs = 0L;

    public HapticBassSync(Context context) {
        Vibrator resolved = null;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                VibratorManager vm = (VibratorManager) context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
                if (vm != null) resolved = vm.getDefaultVibrator();
            } else {
                resolved = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
            }
        } catch (Throwable ignored) {}
        this.vibrator = resolved;
    }

    public boolean isHardwareAvailable() {
        try {
            return vibrator != null && vibrator.hasVibrator();
        } catch (Throwable ignored) {
            return false;
        }
    }

    public void onBassSample(float bassEnergy, boolean enabled, boolean allowedByPowerTier) {
        if (!enabled || !allowedByPowerTier || vibrator == null) {
            smoothedBass *= 0.85f;
            return;
        }
        long now = SystemClock.uptimeMillis();
        float delta = bassEnergy - smoothedBass;
        smoothedBass = smoothedBass * 0.78f + bassEnergy * 0.22f;

        if (bassEnergy > 0.58f && delta > 0.13f && (now - lastPulseMs) >= MIN_BEAT_INTERVAL_MS) {
            lastPulseMs = now;
            int amp = Math.max(35, Math.min(185, Math.round(bassEnergy * 180f)));
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(18L, amp));
                } else {
                    vibrator.vibrate(18L);
                }
            } catch (Throwable ignored) {}
        }
    }
}
