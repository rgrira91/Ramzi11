package com.example.ui;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;
import androidx.annotation.Nullable;
import com.example.R;
import com.example.model.Track;
import com.example.ui.theme.NeonPalette;
import com.example.ui.views.GyroParticleStageView;
import com.example.util.TimeFormatter;
import java.util.Locale;

/**
 * Fullscreen 3D Crystal Cover & Particle Stage Dialog.
 * Registers SensorManager rotation listener strictly while open and resumed,
 * and tears down all sensors cleanly on pause/dismiss to prevent battery drain.
 */
public class CinematicStageDialog implements SensorEventListener {

    public interface StageListener {
        void onPlayPauseClicked();
        void onPreviousClicked();
        void onNextClicked();
        void onHarmonicNextClicked();
        void onSeekToMs(long positionMs);
    }

    private final Context context;
    @Nullable
    private Dialog dialog;
    @Nullable
    private GyroParticleStageView stageView;
    @Nullable
    private TextView badgeBpmKey;
    @Nullable
    private TextView textLyricLine;
    @Nullable
    private TextView textTitle;
    @Nullable
    private TextView textArtist;
    @Nullable
    private TextView textCurrentTime;
    @Nullable
    private TextView textTotalTime;
    @Nullable
    private SeekBar seekBar;
    @Nullable
    private ImageButton btnPlayPause;

    @Nullable
    private SensorManager sensorManager;
    @Nullable
    private Sensor activeSensor;
    private boolean sensorRegistered = false;
    private boolean gyroAllowed = true;
    private boolean hostPaused = false;
    private boolean userSeeking = false;

    private final float[] rotationMatrix = new float[9];
    private final float[] orientationAngles = new float[3];
    private float basePitch = Float.NaN;
    private float baseRoll = Float.NaN;

    public CinematicStageDialog(Context context) {
        this.context = context;
        try {
            this.sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        } catch (Throwable ignored) {}
    }

    public boolean isShowing() {
        return dialog != null && dialog.isShowing();
    }

    public void configureOptimization(boolean allowGyro, int maxParticles, int targetFps) {
        this.gyroAllowed = allowGyro;
        if (stageView != null) {
            stageView.setMaxParticleCount(maxParticles);
            stageView.setTargetFps(targetFps);
        }
        if (isShowing()) {
            if (allowGyro && !hostPaused) {
                registerSensor();
            } else {
                unregisterSensor();
            }
        }
    }

    public void show(
            @Nullable Track track,
            @Nullable Bitmap albumBitmap,
            @Nullable NeonPalette palette,
            boolean playing,
            long positionMs,
            long durationMs,
            boolean allowGyro,
            int maxParticles,
            int targetFps,
            StageListener listener) {
        if (dialog != null && dialog.isShowing()) {
            updateTrack(track, albumBitmap);
            updatePalette(palette);
            updatePlayingState(playing);
            updateProgress(positionMs, durationMs);
            configureOptimization(allowGyro, maxParticles, targetFps);
            return;
        }

        this.gyroAllowed = allowGyro;
        this.hostPaused = false;
        this.basePitch = Float.NaN;
        this.baseRoll = Float.NaN;

        Dialog dlg = new Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        View root = LayoutInflater.from(context).inflate(R.layout.dialog_cinematic_stage, null);
        dlg.setContentView(root);

        stageView = root.findViewById(R.id.stage_canvas_view);
        badgeBpmKey = root.findViewById(R.id.stage_badge_bpm_key);
        textLyricLine = root.findViewById(R.id.stage_lyric_line);
        textTitle = root.findViewById(R.id.stage_track_title);
        textArtist = root.findViewById(R.id.stage_track_artist);
        textCurrentTime = root.findViewById(R.id.stage_current_time);
        textTotalTime = root.findViewById(R.id.stage_total_time);
        seekBar = root.findViewById(R.id.stage_seek_bar);
        btnPlayPause = root.findViewById(R.id.stage_btn_play_pause);

        if (stageView != null) {
            stageView.setMaxParticleCount(maxParticles);
            stageView.setTargetFps(targetFps);
            stageView.setPlaying(playing);
        }
        updatePalette(palette);
        updateTrack(track, albumBitmap);
        updatePlayingState(playing);
        updateProgress(positionMs, durationMs);

        View btnClose = root.findViewById(R.id.stage_btn_close);
        if (btnClose != null) {
            btnClose.setOnClickListener(v -> dismiss());
        }
        View btnPrev = root.findViewById(R.id.stage_btn_prev);
        if (btnPrev != null) {
            btnPrev.setOnClickListener(v -> {
                if (listener != null) listener.onPreviousClicked();
            });
        }
        View btnNext = root.findViewById(R.id.stage_btn_next);
        if (btnNext != null) {
            btnNext.setOnClickListener(v -> {
                if (listener != null) listener.onNextClicked();
            });
        }
        View btnHarmonic = root.findViewById(R.id.stage_btn_harmonic_next);
        if (btnHarmonic != null) {
            btnHarmonic.setOnClickListener(v -> {
                if (listener != null) listener.onHarmonicNextClicked();
            });
        }
        if (btnPlayPause != null) {
            btnPlayPause.setOnClickListener(v -> {
                if (listener != null) listener.onPlayPauseClicked();
            });
        }
        if (seekBar != null) {
            seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                    if (fromUser && textCurrentTime != null) {
                        textCurrentTime.setText(TimeFormatter.formatDuration(progress));
                    }
                }

                @Override
                public void onStartTrackingTouch(SeekBar sb) {
                    userSeeking = true;
                }

                @Override
                public void onStopTrackingTouch(SeekBar sb) {
                    userSeeking = false;
                    if (listener != null) {
                        listener.onSeekToMs(sb.getProgress());
                    }
                }
            });
        }

        dlg.setOnDismissListener(d -> {
            unregisterSensor();
            if (stageView != null) {
                stageView.setPlaying(false);
                stageView.setAlbumBitmap(null);
            }
            dialog = null;
            stageView = null;
            badgeBpmKey = null;
            textLyricLine = null;
            textTitle = null;
            textArtist = null;
            textCurrentTime = null;
            textTotalTime = null;
            seekBar = null;
            btnPlayPause = null;
        });

        this.dialog = dlg;
        dlg.show();
        if (gyroAllowed) {
            registerSensor();
        }
    }

    public void updateTrack(@Nullable Track track, @Nullable Bitmap albumBitmap) {
        if (!isShowing()) return;
        if (textTitle != null) {
            textTitle.setText(track != null ? track.getTitle() : context.getString(R.string.default_track_title));
        }
        if (textArtist != null) {
            textArtist.setText(track != null ? track.getArtist() : context.getString(R.string.default_track_artist));
        }
        if (stageView != null) {
            stageView.setAlbumBitmap(albumBitmap);
        }
        if (textLyricLine != null) {
            textLyricLine.setText("");
        }
    }

    public void updateAlbumBitmap(@Nullable Bitmap albumBitmap) {
        if (!isShowing() || stageView == null) return;
        stageView.setAlbumBitmap(albumBitmap);
    }

    public void updatePalette(@Nullable NeonPalette palette) {
        if (stageView == null || palette == null) return;
        stageView.setPaletteColors(palette.primaryNeon, palette.secondaryNeon, palette.tertiaryGlow);
    }

    public void updatePlayingState(boolean playing) {
        if (!isShowing()) return;
        if (btnPlayPause != null) {
            btnPlayPause.setImageResource(playing ? R.drawable.ic_pause : R.drawable.ic_play);
        }
        if (stageView != null) {
            stageView.setPlaying(playing && !hostPaused);
        }
    }

    public void updateProgress(long positionMs, long durationMs) {
        if (!isShowing() || userSeeking) return;
        if (seekBar != null && durationMs > 0L) {
            if (seekBar.getMax() != (int) durationMs) {
                seekBar.setMax((int) durationMs);
            }
            seekBar.setProgress((int) Math.max(0L, positionMs));
        }
        if (textCurrentTime != null) {
            textCurrentTime.setText(TimeFormatter.formatDuration(Math.max(0L, positionMs)));
        }
        if (textTotalTime != null && durationMs > 0L) {
            textTotalTime.setText(TimeFormatter.formatDuration(durationMs));
        }
    }

    public void updateAudioTelemetry(
            float energy,
            float bass,
            float bpm,
            @Nullable String keyLabel,
            @Nullable String camelot,
            float domFreqHz,
            @Nullable String domNote) {
        if (!isShowing()) return;
        if (stageView != null) {
            stageView.updateAudioTelemetry(energy, bass, bpm);
        }
        if (badgeBpmKey != null) {
            String keyPart = (camelot != null && !camelot.isEmpty() && !"--".equals(camelot))
                    ? (camelot + " " + (keyLabel != null ? keyLabel : ""))
                    : "--";
            String notePart = (domNote != null && !domNote.isEmpty() && domFreqHz > 18f)
                    ? String.format(Locale.US, " • %s (%dHz)", domNote, Math.round(domFreqHz))
                    : "";
            badgeBpmKey.setText(String.format(Locale.US, "%d BPM • %s%s",
                    Math.round(bpm > 0f ? bpm : 120f), keyPart.trim(), notePart));
        }
    }

    public void updateLyricLine(@Nullable String lyric) {
        if (!isShowing() || textLyricLine == null) return;
        String safe = lyric == null ? "" : lyric;
        if (!safe.equals(textLyricLine.getText().toString())) {
            textLyricLine.setText(safe);
        }
    }

    public void onHostPause() {
        hostPaused = true;
        unregisterSensor();
        if (stageView != null) {
            stageView.setPlaying(false);
        }
    }

    public void onHostResume(boolean isPlaying) {
        hostPaused = false;
        if (isShowing()) {
            if (gyroAllowed) {
                registerSensor();
            }
            if (stageView != null) {
                stageView.setPlaying(isPlaying);
            }
        }
    }

    public void dismiss() {
        unregisterSensor();
        if (dialog != null && dialog.isShowing()) {
            dialog.dismiss();
        }
    }

    private void registerSensor() {
        if (sensorRegistered || sensorManager == null) return;
        try {
            activeSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
            if (activeSensor == null) {
                activeSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            }
            if (activeSensor != null) {
                sensorManager.registerListener(this, activeSensor, SensorManager.SENSOR_DELAY_UI);
                sensorRegistered = true;
            }
        } catch (Throwable ignored) {}
    }

    private void unregisterSensor() {
        if (!sensorRegistered || sensorManager == null) return;
        try {
            sensorManager.unregisterListener(this);
        } catch (Throwable ignored) {}
        sensorRegistered = false;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event == null || stageView == null) return;
        if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values);
            SensorManager.getOrientation(rotationMatrix, orientationAngles);
            float pitch = orientationAngles[1];
            float roll = orientationAngles[2];
            if (Float.isNaN(basePitch)) {
                basePitch = pitch;
                baseRoll = roll;
            }
            float dp = Math.max(-0.55f, Math.min(0.55f, pitch - basePitch)) / 0.55f;
            float dr = Math.max(-0.55f, Math.min(0.55f, roll - baseRoll)) / 0.55f;
            stageView.setGyroTilt(dp, dr);
        } else if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER && event.values.length >= 2) {
            float roll = Math.max(-1f, Math.min(1f, -event.values[0] / 5.5f));
            float pitch = Math.max(-1f, Math.min(1f, (event.values[1] - 4.5f) / 5.5f));
            stageView.setGyroTilt(pitch, roll);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}
}
