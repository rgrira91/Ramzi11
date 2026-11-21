package com.example.audio;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Built-in PCM signal generator for verifying Left/Right earphone channels
 * and Sub-Bass (65Hz -> 36Hz) response directly without external test files.
 */
public final class BuiltInAudioTester {
    public enum TestSignal {
        LEFT_CHANNEL,
        RIGHT_CHANNEL,
        SUB_BASS_PUNCH
    }

    public interface TestCompletionListener {
        void onTestFinished();
    }

    private static final int SAMPLE_RATE = 44100;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile AudioTrack activeTrack;

    public void playTest(TestSignal signal, TestCompletionListener onDone) {
        stop();
        running.set(true);
        executor.execute(() -> {
            AudioTrack track = null;
            try {
                float durationSec = signal == TestSignal.SUB_BASS_PUNCH ? 2.2f : 1.1f;
                int totalFrames = Math.round(SAMPLE_RATE * durationSec);
                short[] pcm = new short[totalFrames * 2];

                for (int i = 0; i < totalFrames; i++) {
                    double t = (double) i / SAMPLE_RATE;
                    double env = 1.0;
                    if (t < 0.03) env = t / 0.03;
                    else if (t > durationSec - 0.08) env = Math.max(0.0, (durationSec - t) / 0.08);

                    double l = 0.0;
                    double r = 0.0;
                    if (signal == TestSignal.LEFT_CHANNEL) {
                        double tone = Math.sin(2.0 * Math.PI * 440.0 * t)
                                + 0.35 * Math.sin(2.0 * Math.PI * 880.0 * t);
                        double pulse = 0.55 + 0.45 * Math.sin(2.0 * Math.PI * 4.0 * t);
                        l = tone * pulse * 0.48;
                    } else if (signal == TestSignal.RIGHT_CHANNEL) {
                        double tone = Math.sin(2.0 * Math.PI * 587.33 * t)
                                + 0.35 * Math.sin(2.0 * Math.PI * 1174.66 * t);
                        double pulse = 0.55 + 0.45 * Math.sin(2.0 * Math.PI * 4.0 * t);
                        r = tone * pulse * 0.48;
                    } else {
                        // Repeating punchy sub-bass kick + 60Hz -> 36Hz sweep
                        double beatTime = t % 0.55;
                        double freq = 68.0 - 30.0 * Math.min(1.0, beatTime / 0.25);
                        double kickEnv = Math.exp(-beatTime * 6.5);
                        double sub = Math.sin(2.0 * Math.PI * freq * beatTime) * kickEnv
                                + 0.28 * Math.sin(2.0 * Math.PI * 55.0 * t);
                        l = sub * 0.72;
                        r = sub * 0.72;
                    }

                    pcm[i * 2] = (short) Math.max(-32767, Math.min(32767, Math.round(l * env * 32767.0)));
                    pcm[i * 2 + 1] = (short) Math.max(-32767, Math.min(32767, Math.round(r * env * 32767.0)));
                }

                int bufBytes = Math.max(pcm.length * 2, AudioTrack.getMinBufferSize(
                        SAMPLE_RATE,
                        AudioFormat.CHANNEL_OUT_STEREO,
                        AudioFormat.ENCODING_PCM_16BIT));

                track = new AudioTrack.Builder()
                        .setAudioAttributes(new AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                .build())
                        .setAudioFormat(new AudioFormat.Builder()
                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                .setSampleRate(SAMPLE_RATE)
                                .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                                .build())
                        .setBufferSizeInBytes(bufBytes)
                        .setTransferMode(AudioTrack.MODE_STATIC)
                        .build();

                activeTrack = track;
                track.write(pcm, 0, pcm.length);
                if (running.get()) {
                    track.play();
                    long endWait = System.currentTimeMillis() + Math.round(durationSec * 1000f) + 80L;
                    while (running.get() && System.currentTimeMillis() < endWait) {
                        Thread.sleep(40L);
                    }
                }
            } catch (Throwable ignored) {
            } finally {
                if (track != null) {
                    try {
                        track.stop();
                    } catch (Throwable ignored) {}
                    try {
                        track.release();
                    } catch (Throwable ignored) {}
                }
                if (activeTrack == track) {
                    activeTrack = null;
                }
                running.set(false);
                if (onDone != null) {
                    onDone.onTestFinished();
                }
            }
        });
    }

    public void stop() {
        running.set(false);
        AudioTrack t = activeTrack;
        if (t != null) {
            try {
                t.pause();
                t.flush();
                t.stop();
            } catch (Throwable ignored) {}
        }
    }

    public void shutdown() {
        stop();
        executor.shutdownNow();
    }
}
