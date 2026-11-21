package com.example.audio;

import android.os.SystemClock;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.media3.common.C;
import androidx.media3.common.audio.AudioProcessor;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.audio.TeeAudioProcessor;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Sample-accurate stereo PCM tap attached directly inside ExoPlayer's AudioSink.
 * Computes true Left/Right stereo RMS & peak levels plus a 42-band log-spaced spectrum,
 * and timestamps frames to align UI visuals with speaker output latency.
 */
@OptIn(markerClass = UnstableApi.class)
public class AudioSpectrumTapProcessor implements TeeAudioProcessor.AudioBufferSink {

    public static final int SPECTRUM_BAR_COUNT = 42;
    public static final int XY_SAMPLE_COUNT = 80;
    private static final int FFT_SAMPLE_COUNT = 256;
    private static final long AUDIO_OUTPUT_DELAY_MS = 105L;
    private static final long DEFAULT_FRAME_SPACING_MS = 22L;
    private static final int QUEUE_CAPACITY = 32;

    public static final class SpectrumFrame {
        public final float[] bars = new float[SPECTRUM_BAR_COUNT];
        public final float[] xyLeft = new float[XY_SAMPLE_COUNT];
        public final float[] xyRight = new float[XY_SAMPLE_COUNT];
        public final float[] band10Db = new float[10];
        public float energy;
        public float leftLevel;
        public float rightLevel;
        public float bassEnergy;
        public float trebleEnergy;
        public int bassPowerPercent = 0;
        public float bassPowerDb = -48.0f;
        public float leftAvgDb = -48.0f;
        public float rightAvgDb = -48.0f;
        public float phaseCorrelation = 1.0f;
        public float lufsShortTerm = -48.0f;
        public float crestFactorDb = 0.0f;
        public boolean clipDetected = false;
        public int sampleRateHz = 44100;
        public int bitDepth = 24;
        public float dominantFreqHz = 0f;
        public String dominantNoteName = "--";
        public float liveBpm = 120.0f;
        public String detectedKeyLabel = "--";
        public String detectedCamelot = "--";
        public long playAtUptimeMs;
    }

    private static final String[] NOTE_NAMES = {
            "C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"
    };
    private static final String[] MAJOR_CAMELOT = {
            "8B", "3B", "10B", "5B", "12B", "7B", "2B", "9B", "4B", "11B", "6B", "1B"
    };
    private static final String[] MINOR_CAMELOT = {
            "5A", "12A", "7A", "2A", "9A", "4A", "11A", "6A", "1A", "8A", "3A", "10A"
    };
    private static final float[] MAJOR_PROFILE = {
            6.35f, 2.23f, 3.48f, 2.33f, 4.38f, 4.09f, 2.52f, 5.19f, 2.39f, 3.66f, 2.29f, 2.88f
    };
    private static final float[] MINOR_PROFILE = {
            6.33f, 2.68f, 3.52f, 5.38f, 2.60f, 3.53f, 2.54f, 4.75f, 3.98f, 2.69f, 3.34f, 3.17f
    };
    private static final double INV_LOG2 = 1.0 / Math.log(2.0);

    private final TeeAudioProcessor teeAudioProcessor;
    private final SpectrumFrame[] ring = new SpectrumFrame[QUEUE_CAPACITY];
    private int ringHead = 0;
    private int ringCount = 0;

    private final float[] monoSamples = new float[FFT_SAMPLE_COUNT];
    private final float[] scratchXyLeft = new float[XY_SAMPLE_COUNT];
    private final float[] scratchXyRight = new float[XY_SAMPLE_COUNT];
    private final float[] hanningWindow = new float[FFT_SAMPLE_COUNT];
    private final float[] binCos = new float[SPECTRUM_BAR_COUNT];
    private final float[] binSin = new float[SPECTRUM_BAR_COUNT];
    private final float[] barFreqHz = new float[SPECTRUM_BAR_COUNT];
    private final int[] barPitchClass = new int[SPECTRUM_BAR_COUNT];
    private final float[] scratchBars = new float[SPECTRUM_BAR_COUNT];
    private final float[] smoothBand10Db = new float[10];

    // Zero-alloc Beat-Onset BPM Tracker state
    private float runningBassAvg = 0.25f;
    private float prevBassEnergy = 0.0f;
    private long lastBeatOnsetMs = 0L;
    private final float[] beatIntervalsMs = new float[12];
    private final float[] sortedIntervalsScratch = new float[12];
    private int beatIntervalCount = 0;
    private int beatIntervalIdx = 0;
    private float smoothedBpm = 120.0f;

    // Zero-alloc 12-bin Chroma Key & Dominant Note state
    private final float[] chromaAccum = new float[12];
    private int chromaFrameCounter = 0;
    private String currentKeyLabel = "--";
    private String currentCamelot = "--";
    private float lastDomFreqHz = 0f;
    private String lastDomNoteName = "--";

    private int sampleRateHz = 44100;
    private int channelCount = 2;
    private int pcmEncoding = C.ENCODING_PCM_16BIT;
    private int cachedCoeffRate = -1;
    private long lastEnqueuedCaptureMs = 0L;
    private volatile boolean captureEnabled = true;
    private volatile long frameSpacingMs = DEFAULT_FRAME_SPACING_MS;

    public AudioSpectrumTapProcessor() {
        this.teeAudioProcessor = new TeeAudioProcessor(this);
        for (int i = 0; i < QUEUE_CAPACITY; i++) {
            ring[i] = new SpectrumFrame();
        }
        for (int n = 0; n < FFT_SAMPLE_COUNT; n++) {
            hanningWindow[n] = (float) (0.5 * (1.0 - Math.cos(2.0 * Math.PI * n / (FFT_SAMPLE_COUNT - 1))));
        }
        java.util.Arrays.fill(smoothBand10Db, -24f);
        updateGoertzelCoefficients(44100);
    }

    public AudioProcessor getAudioProcessor() {
        return teeAudioProcessor;
    }

    public int getSampleRateHz() {
        return sampleRateHz;
    }

    private float subLp1 = 0f;
    private float subLp2 = 0f;
    private float subLpAlpha = 0.019f;
    private float smoothedBassPowerNorm = 0f;

    public int getBitDepth() {
        switch (pcmEncoding) {
            case C.ENCODING_PCM_8BIT:
                return 8;
            case C.ENCODING_PCM_16BIT:
                return 16;
            case C.ENCODING_PCM_32BIT:
            case C.ENCODING_PCM_FLOAT:
                return 32;
            case C.ENCODING_PCM_24BIT:
            default:
                return 24;
        }
    }

    public void setCaptureEnabled(boolean enabled) {
        this.captureEnabled = enabled;
        if (!enabled) {
            clear();
        }
    }

    public void setFrameSpacingMs(long spacingMs) {
        this.frameSpacingMs = Math.max(18L, Math.min(80L, spacingMs));
    }

    public synchronized void clear() {
        ringHead = 0;
        ringCount = 0;
        lastEnqueuedCaptureMs = 0L;
        lastBeatOnsetMs = 0L;
        beatIntervalCount = 0;
        beatIntervalIdx = 0;
        chromaFrameCounter = 0;
        subLp1 = 0f;
        subLp2 = 0f;
        smoothedBassPowerNorm = 0f;
        java.util.Arrays.fill(chromaAccum, 0f);
    }

    @Override
    public void flush(int sampleRateHz, int channelCount, int encoding) {
        this.sampleRateHz = sampleRateHz > 0 ? sampleRateHz : 44100;
        this.channelCount = Math.max(1, channelCount);
        this.pcmEncoding = encoding;
        if (this.sampleRateHz != cachedCoeffRate) {
            updateGoertzelCoefficients(this.sampleRateHz);
        }
        clear();
    }

    @Override
    public void handleBuffer(ByteBuffer buffer) {
        if (!captureEnabled || buffer == null || !buffer.hasRemaining()) {
            return;
        }
        long now = SystemClock.uptimeMillis();
        if (now - lastEnqueuedCaptureMs < frameSpacingMs) {
            return;
        }
        lastEnqueuedCaptureMs = now;

        int bytesPerSample = getBytesPerSample(pcmEncoding);
        if (bytesPerSample <= 0) {
            return;
        }

        int frameBytes = bytesPerSample * channelCount;
        int startPos = buffer.position();
        int remaining = buffer.remaining();
        int totalFrames = remaining / frameBytes;
        if (totalFrames <= 0) {
            return;
        }

        ByteOrder originalOrder = buffer.order();
        buffer.order(ByteOrder.LITTLE_ENDIAN);

        double leftSqSum = 0.0;
        double rightSqSum = 0.0;
        double lrProductSum = 0.0;
        float leftPeak = 0f;
        float rightPeak = 0f;

        int step = Math.max(1, totalFrames / FFT_SAMPLE_COUNT);
        int analyzedFrames = 0;

        for (int f = 0; f < totalFrames && analyzedFrames < FFT_SAMPLE_COUNT; f += step) {
            int offset = startPos + f * frameBytes;
            float l = readSampleNormalized(buffer, offset, pcmEncoding);
            float r = channelCount >= 2
                    ? readSampleNormalized(buffer, offset + bytesPerSample, pcmEncoding)
                    : l;

            leftSqSum += l * l;
            rightSqSum += r * r;
            lrProductSum += l * r;
            float absL = Math.abs(l);
            float absR = Math.abs(r);
            if (absL > leftPeak) leftPeak = absL;
            if (absR > rightPeak) rightPeak = absR;
            analyzedFrames++;
        }

        // Fill Goertzel FFT, XY buffers & Sub-Bass Power LPF using consecutive un-aliased frames (step = 1)
        int monoIdx = Math.min(totalFrames, FFT_SAMPLE_COUNT);
        int xyIdx = 0;
        int xyStride = Math.max(1, monoIdx / XY_SAMPLE_COUNT);
        double subSqSum = 0.0;
        float subPeak = 0f;
        for (int f = 0; f < monoIdx; f++) {
            int offset = startPos + f * frameBytes;
            float l = readSampleNormalized(buffer, offset, pcmEncoding);
            float r = channelCount >= 2
                    ? readSampleNormalized(buffer, offset + bytesPerSample, pcmEncoding)
                    : l;
            float mono = 0.5f * (l + r);
            monoSamples[f] = mono * hanningWindow[f];
            subLp1 += (mono - subLp1) * subLpAlpha;
            subLp2 += (subLp1 - subLp2) * subLpAlpha;
            subSqSum += subLp2 * subLp2;
            float absSub = Math.abs(subLp2);
            if (absSub > subPeak) subPeak = absSub;
            if (xyIdx < XY_SAMPLE_COUNT && (f % xyStride == 0)) {
                scratchXyLeft[xyIdx] = l;
                scratchXyRight[xyIdx] = r;
                xyIdx++;
            }
        }

        buffer.order(originalOrder);
        if (analyzedFrames == 0) {
            return;
        }

        for (int i = monoIdx; i < FFT_SAMPLE_COUNT; i++) {
            monoSamples[i] = 0f;
        }
        for (int i = xyIdx; i < XY_SAMPLE_COUNT; i++) {
            scratchXyLeft[i] = 0f;
            scratchXyRight[i] = 0f;
        }

        float leftRms = (float) Math.sqrt(leftSqSum / analyzedFrames);
        float rightRms = (float) Math.sqrt(rightSqSum / analyzedFrames);

        float leftLevel = calibrateMeterLevel(leftRms, leftPeak);
        float rightLevel = calibrateMeterLevel(rightRms, rightPeak);

        double denom = Math.sqrt(leftSqSum * rightSqSum);
        float correlation = denom > 1e-7 ? (float) (lrProductSum / denom) : 1.0f;
        correlation = Math.max(-1.0f, Math.min(1.0f, correlation));

        double meanPower = (leftSqSum + rightSqSum) / (2.0 * analyzedFrames);
        float lufs = meanPower > 1e-8
                ? (float) Math.max(-60.0, Math.min(0.0, -0.691 + 10.0 * Math.log10(meanPower)))
                : -60.0f;
        float combinedRms = (float) Math.sqrt(meanPower);
        float maxPeak = Math.max(leftPeak, rightPeak);
        float crestDb = (combinedRms > 1e-5f && maxPeak > combinedRms)
                ? (float) Math.max(0.0, Math.min(30.0, 20.0 * Math.log10(maxPeak / combinedRms)))
                : 0.0f;

        SpectrumFrame targetFrame;
        synchronized (this) {
            if (ringCount < QUEUE_CAPACITY) {
                int idx = (ringHead + ringCount) % QUEUE_CAPACITY;
                targetFrame = ring[idx];
                ringCount++;
            } else {
                targetFrame = ring[ringHead];
                ringHead = (ringHead + 1) % QUEUE_CAPACITY;
            }

            System.arraycopy(scratchXyLeft, 0, targetFrame.xyLeft, 0, XY_SAMPLE_COUNT);
            System.arraycopy(scratchXyRight, 0, targetFrame.xyRight, 0, XY_SAMPLE_COUNT);

            float sumBars = 0f;
            float bassSum = 0f;
            float bassPeak = 0f;
            float trebleSum = 0f;
            int trebleCount = 0;
            int nCount = Math.max(16, monoIdx);
            float normScale = 2.0f / nCount;

            int peakBarIdx = 0;
            float peakRawMag = 0f;

            for (int b = 0; b < SPECTRUM_BAR_COUNT; b++) {
                float coeff = 2.0f * binCos[b];
                float sPrev = 0f;
                float sPrev2 = 0f;
                for (int n = 0; n < nCount; n++) {
                    float s = monoSamples[n] + coeff * sPrev - sPrev2;
                    sPrev2 = sPrev;
                    sPrev = s;
                }
                float real = sPrev - sPrev2 * binCos[b];
                float imag = sPrev2 * binSin[b];
                float mag = (float) Math.sqrt(real * real + imag * imag) * normScale;

                if (mag > peakRawMag) {
                    peakRawMag = mag;
                    peakBarIdx = b;
                }

                // Apply subtle high-frequency tilt so treble bands stay lively alongside bass
                float tilt = 1.0f + (b / (float) (SPECTRUM_BAR_COUNT - 1)) * 1.65f;
                float barVal = (float) (Math.log10(1.0 + mag * 38.0 * tilt) / 1.55);
                barVal = Math.max(0.04f, Math.min(1f, barVal));

                targetFrame.bars[b] = barVal;
                scratchBars[b] = barVal;
                sumBars += barVal;
                if (b < 8) {
                    bassSum += barVal;
                    if (barVal > bassPeak) bassPeak = barVal;
                } else if (b >= 26) {
                    trebleSum += barVal;
                    trebleCount++;
                }
            }

            // Dominant Frequency & Musical Note Name (only update if meaningful signal)
            if (peakRawMag > 0.015f) {
                float freq = barFreqHz[peakBarIdx];
                lastDomFreqHz = freq;
                lastDomNoteName = freqToNoteName(freq);
            }
            targetFrame.dominantFreqHz = lastDomFreqHz;
            targetFrame.dominantNoteName = lastDomNoteName;

            float avgBass = bassSum / 8.0f;
            float computedBass = Math.max(0.05f, Math.min(1f, avgBass * 0.62f + bassPeak * 0.38f));

            // Beat-Onset BPM Tracker (60..190 BPM)
            updateBpmTracker(computedBass, now);
            targetFrame.liveBpm = smoothedBpm;

            // 12-Bin Chroma Key & Camelot Estimator + 10-band spectral fingerprint
            updateChromaAnd10BandProfile(scratchBars);
            targetFrame.detectedKeyLabel = currentKeyLabel;
            targetFrame.detectedCamelot = currentCamelot;
            System.arraycopy(smoothBand10Db, 0, targetFrame.band10Db, 0, 10);

            float subRms = (float) Math.sqrt(subSqSum / Math.max(1, monoIdx));
            float subDrive = subRms * 0.72f + subPeak * 0.28f;
            float subDb = subDrive > 0.0004f
                    ? (float) Math.max(-48.0, Math.min(6.0, 20.0 * Math.log10(subDrive) + 12.0))
                    : -48.0f;
            float rawSubNorm = subDrive > 0.0008f
                    ? (float) Math.max(0.0, Math.min(1.0, (20.0 * Math.log10(subDrive) + 38.0) / 36.0))
                    : 0f;
            float combinedBassNorm = Math.max(rawSubNorm, computedBass * 0.85f);
            float coeff = combinedBassNorm > smoothedBassPowerNorm ? 0.52f : 0.20f;
            smoothedBassPowerNorm += (combinedBassNorm - smoothedBassPowerNorm) * coeff;

            targetFrame.leftLevel = leftLevel;
            targetFrame.rightLevel = rightLevel;
            targetFrame.leftAvgDb = computeVuAvgDb(leftRms);
            targetFrame.rightAvgDb = computeVuAvgDb(rightRms);
            targetFrame.phaseCorrelation = correlation;
            targetFrame.lufsShortTerm = lufs;
            targetFrame.crestFactorDb = crestDb;
            targetFrame.clipDetected = leftPeak >= 0.96f || rightPeak >= 0.96f;
            targetFrame.bassEnergy = computedBass;
            targetFrame.bassPowerPercent = Math.max(0, Math.min(100, Math.round(smoothedBassPowerNorm * 100f)));
            targetFrame.bassPowerDb = subDb;
            targetFrame.trebleEnergy = trebleCount > 0
                    ? Math.max(0.04f, Math.min(1f, trebleSum / trebleCount))
                    : 0.04f;
            targetFrame.energy = Math.max(
                    0.04f,
                    Math.min(1f, 0.52f * ((leftLevel + rightLevel) * 0.5f) + 0.48f * (sumBars / SPECTRUM_BAR_COUNT))
            );
            targetFrame.sampleRateHz = sampleRateHz;
            targetFrame.bitDepth = getBitDepth();
            targetFrame.playAtUptimeMs = now + AUDIO_OUTPUT_DELAY_MS;
        }
    }

    private void updateBpmTracker(float currentBass, long nowMs) {
        runningBassAvg = runningBassAvg * 0.94f + currentBass * 0.06f;
        boolean onset = currentBass > runningBassAvg * 1.24f
                && currentBass > prevBassEnergy + 0.065f
                && currentBass > 0.32f;
        prevBassEnergy = currentBass;
        if (onset) {
            if (lastBeatOnsetMs > 0L) {
                long delta = nowMs - lastBeatOnsetMs;
                if (delta >= 270L && delta <= 1650L) {
                    // Fold double-time or half-time intervals into comfortable [315ms..1000ms] musical range
                    float interval = delta;
                    while (interval < 315f) interval *= 2f;
                    while (interval > 1000f) interval *= 0.5f;
                    beatIntervalsMs[beatIntervalIdx] = interval;
                    beatIntervalIdx = (beatIntervalIdx + 1) % beatIntervalsMs.length;
                    if (beatIntervalCount < beatIntervalsMs.length) {
                        beatIntervalCount++;
                    }
                    if (beatIntervalCount >= 3) {
                        System.arraycopy(beatIntervalsMs, 0, sortedIntervalsScratch, 0, beatIntervalCount);
                        java.util.Arrays.sort(sortedIntervalsScratch, 0, beatIntervalCount);
                        float medianMs = sortedIntervalsScratch[beatIntervalCount / 2];
                        float estBpm = Math.max(60f, Math.min(190f, 60000f / Math.max(250f, medianMs)));
                        smoothedBpm = smoothedBpm * 0.72f + estBpm * 0.28f;
                    }
                    lastBeatOnsetMs = nowMs;
                } else if (delta > 1650L) {
                    lastBeatOnsetMs = nowMs;
                }
            } else {
                lastBeatOnsetMs = nowMs;
            }
        }
    }

    private void updateChromaAnd10BandProfile(float[] bars) {
        // Decay accumulated chroma & reinforce melodic bands (bars 4..34)
        for (int c = 0; c < 12; c++) {
            chromaAccum[c] *= 0.94f;
        }
        for (int b = 4; b < 36; b++) {
            float val = bars[b];
            if (val > 0.15f) {
                chromaAccum[barPitchClass[b]] += val * val;
            }
        }

        // Update 10-band spectral fingerprint (maps 42 bars smoothly into 10 octave groups)
        for (int band = 0; band < 10; band++) {
            int start = Math.min(SPECTRUM_BAR_COUNT - 1, (band * SPECTRUM_BAR_COUNT) / 10);
            int end = Math.min(SPECTRUM_BAR_COUNT, ((band + 1) * SPECTRUM_BAR_COUNT) / 10);
            float sum = 0f;
            int cnt = Math.max(1, end - start);
            for (int k = start; k < end; k++) {
                sum += bars[k];
            }
            float dbVal = -36f + (sum / cnt) * 36f;
            smoothBand10Db[band] = smoothBand10Db[band] * 0.88f + dbVal * 0.12f;
        }

        chromaFrameCounter++;
        if (chromaFrameCounter % 6 != 0) {
            return;
        }

        float totalEnergy = 0f;
        for (int c = 0; c < 12; c++) totalEnergy += chromaAccum[c];
        if (totalEnergy < 0.25f) return;

        float bestScore = -1e9f;
        int bestRoot = 0;
        boolean bestMinor = false;
        for (int root = 0; root < 12; root++) {
            float maj = 0f;
            float min = 0f;
            for (int i = 0; i < 12; i++) {
                float energy = chromaAccum[(root + i) % 12];
                maj += energy * MAJOR_PROFILE[i];
                min += energy * MINOR_PROFILE[i];
            }
            if (maj > bestScore) {
                bestScore = maj;
                bestRoot = root;
                bestMinor = false;
            }
            if (min > bestScore) {
                bestScore = min;
                bestRoot = root;
                bestMinor = true;
            }
        }

        currentKeyLabel = bestMinor ? (NOTE_NAMES[bestRoot] + "m") : NOTE_NAMES[bestRoot];
        currentCamelot = bestMinor ? MINOR_CAMELOT[bestRoot] : MAJOR_CAMELOT[bestRoot];
    }

    private static String freqToNoteName(float freqHz) {
        if (freqHz <= 16f) return "--";
        int midi = (int) Math.round(69.0 + 12.0 * (Math.log(freqHz / 440.0) * INV_LOG2));
        if (midi < 0 || midi > 127) return "--";
        int noteIdx = ((midi % 12) + 12) % 12;
        int octave = (midi / 12) - 1;
        return NOTE_NAMES[noteIdx] + octave;
    }

    @Nullable
    public synchronized SpectrumFrame pollTimeAlignedFrame(long nowUptimeMs, SpectrumFrame outFrame) {
        if (ringCount == 0 || outFrame == null) {
            return null;
        }
        SpectrumFrame ready = null;
        while (ringCount > 0) {
            SpectrumFrame head = ring[ringHead];
            if (head.playAtUptimeMs <= nowUptimeMs) {
                ready = head;
                ringHead = (ringHead + 1) % QUEUE_CAPACITY;
                ringCount--;
            } else {
                break;
            }
        }
        if (ready == null) {
            return null;
        }
        System.arraycopy(ready.bars, 0, outFrame.bars, 0, SPECTRUM_BAR_COUNT);
        System.arraycopy(ready.xyLeft, 0, outFrame.xyLeft, 0, XY_SAMPLE_COUNT);
        System.arraycopy(ready.xyRight, 0, outFrame.xyRight, 0, XY_SAMPLE_COUNT);
        System.arraycopy(ready.band10Db, 0, outFrame.band10Db, 0, 10);
        outFrame.energy = ready.energy;
        outFrame.leftLevel = ready.leftLevel;
        outFrame.rightLevel = ready.rightLevel;
        outFrame.bassEnergy = ready.bassEnergy;
        outFrame.trebleEnergy = ready.trebleEnergy;
        outFrame.bassPowerPercent = ready.bassPowerPercent;
        outFrame.bassPowerDb = ready.bassPowerDb;
        outFrame.leftAvgDb = ready.leftAvgDb;
        outFrame.rightAvgDb = ready.rightAvgDb;
        outFrame.phaseCorrelation = ready.phaseCorrelation;
        outFrame.lufsShortTerm = ready.lufsShortTerm;
        outFrame.crestFactorDb = ready.crestFactorDb;
        outFrame.clipDetected = ready.clipDetected;
        outFrame.sampleRateHz = ready.sampleRateHz;
        outFrame.bitDepth = ready.bitDepth;
        outFrame.dominantFreqHz = ready.dominantFreqHz;
        outFrame.dominantNoteName = ready.dominantNoteName;
        outFrame.liveBpm = ready.liveBpm;
        outFrame.detectedKeyLabel = ready.detectedKeyLabel;
        outFrame.detectedCamelot = ready.detectedCamelot;
        outFrame.playAtUptimeMs = ready.playAtUptimeMs;
        return outFrame;
    }

    private void updateGoertzelCoefficients(int sr) {
        cachedCoeffRate = sr;
        subLpAlpha = (float) (1.0 - Math.exp(-2.0 * Math.PI * 135.0 / Math.max(8000, sr)));
        double minFreq = 45.0;
        double maxFreq = Math.min(14500.0, sr * 0.45);
        double ratio = maxFreq / minFreq;
        for (int b = 0; b < SPECTRUM_BAR_COUNT; b++) {
            double norm = (double) b / (SPECTRUM_BAR_COUNT - 1);
            double freq = minFreq * Math.pow(ratio, norm);
            double omega = (2.0 * Math.PI * freq) / sr;
            binCos[b] = (float) Math.cos(omega);
            binSin[b] = (float) Math.sin(omega);
            barFreqHz[b] = (float) freq;
            int midi = (int) Math.round(69.0 + 12.0 * (Math.log(freq / 440.0) * INV_LOG2));
            barPitchClass[b] = ((midi % 12) + 12) % 12;
        }
    }

    private static float calibrateMeterLevel(float rms, float peak) {
        float blended = rms * 0.72f + peak * 0.28f;
        if (blended <= 0.0008f) {
            return 0.03f;
        }
        // Map -42 dBFS .. -1 dBFS logarithmically into 0.04 .. 1.0 for natural VU/LED response
        double db = 20.0 * Math.log10(blended);
        double norm = (db + 42.0) / 41.0;
        return (float) Math.max(0.04, Math.min(1.0, norm));
    }

    private static float computeVuAvgDb(float rms) {
        if (rms <= 0.0005f) {
            return -48.0f;
        }
        // Studio VU calibration: 0 VU = -14 dBFS RMS
        double vuDb = (20.0 * Math.log10(rms)) + 14.0;
        return (float) Math.max(-24.0, Math.min(3.5, vuDb));
    }

    private static int getBytesPerSample(int encoding) {
        switch (encoding) {
            case C.ENCODING_PCM_16BIT:
                return 2;
            case C.ENCODING_PCM_FLOAT:
            case C.ENCODING_PCM_32BIT:
                return 4;
            case C.ENCODING_PCM_24BIT:
                return 3;
            case C.ENCODING_PCM_8BIT:
                return 1;
            default:
                return 0;
        }
    }

    private static float readSampleNormalized(ByteBuffer buffer, int offset, int encoding) {
        switch (encoding) {
            case C.ENCODING_PCM_16BIT:
                return buffer.getShort(offset) / 32768.0f;
            case C.ENCODING_PCM_FLOAT: {
                float f = buffer.getFloat(offset);
                return Math.max(-1f, Math.min(1f, f));
            }
            case C.ENCODING_PCM_24BIT: {
                int b0 = buffer.get(offset) & 0xFF;
                int b1 = buffer.get(offset + 1) & 0xFF;
                int b2 = buffer.get(offset + 2);
                int val = (b2 << 16) | (b1 << 8) | b0;
                return val / 8388608.0f;
            }
            case C.ENCODING_PCM_32BIT: {
                int v = buffer.getInt(offset);
                return v / 2147483648.0f;
            }
            case C.ENCODING_PCM_8BIT: {
                int unsigned = buffer.get(offset) & 0xFF;
                return (unsigned - 128) / 128.0f;
            }
            default:
                return 0f;
        }
    }
}
