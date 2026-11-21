package com.example.ui.theme;

import android.graphics.Bitmap;
import android.graphics.Color;

public final class AlbumColorExtractor {
    private static final int SAMPLE_SIZE = 24;
    private static final int HUE_BINS = 24;

    private AlbumColorExtractor() {}

    public static NeonPalette extract(Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled() || bitmap.getWidth() <= 0 || bitmap.getHeight() <= 0) {
            return NeonPalette.DEFAULT;
        }

        Bitmap sampled = bitmap;
        boolean createdScaled = false;
        if (bitmap.getWidth() > SAMPLE_SIZE || bitmap.getHeight() > SAMPLE_SIZE) {
            sampled = Bitmap.createScaledBitmap(bitmap, SAMPLE_SIZE, SAMPLE_SIZE, true);
            createdScaled = (sampled != bitmap);
        }

        int w = sampled.getWidth();
        int h = sampled.getHeight();
        int[] pixels = new int[w * h];
        sampled.getPixels(pixels, 0, w, 0, 0, w, h);
        if (createdScaled) {
            sampled.recycle();
        }

        double[] binWeights = new double[HUE_BINS];
        double[] binSatSum = new double[HUE_BINS];
        double[] binValSum = new double[HUE_BINS];
        double[] binCosSum = new double[HUE_BINS];
        double[] binSinSum = new double[HUE_BINS];

        float[] hsv = new float[3];
        double totalWeight = 0.0;

        for (int pixel : pixels) {
            int alpha = Color.alpha(pixel);
            if (alpha < 128) continue;

            Color.colorToHSV(pixel, hsv);
            float hue = hsv[0];
            float sat = hsv[1];
            float val = hsv[2];

            // Ignore crushed blacks and washed-out whites so neon hues dominate
            if (val < 0.12f || (sat < 0.12f && val > 0.88f) || sat < 0.08f) {
                continue;
            }

            double vibrancyWeight = Math.pow(sat, 1.35) * (0.35 + 0.65 * val);
            int bin = Math.min(HUE_BINS - 1, Math.max(0, (int) (hue / (360f / HUE_BINS))));
            double rad = Math.toRadians(hue);

            binWeights[bin] += vibrancyWeight;
            binSatSum[bin] += sat * vibrancyWeight;
            binValSum[bin] += val * vibrancyWeight;
            binCosSum[bin] += Math.cos(rad) * vibrancyWeight;
            binSinSum[bin] += Math.sin(rad) * vibrancyWeight;
            totalWeight += vibrancyWeight;
        }

        if (totalWeight < 0.5) {
            return NeonPalette.DEFAULT;
        }

        int bestBin = 0;
        for (int i = 1; i < HUE_BINS; i++) {
            if (binWeights[i] > binWeights[bestBin]) {
                bestBin = i;
            }
        }

        float primaryHue = resolveClusterHue(bestBin, binWeights, binCosSum, binSinSum);
        float primarySat = (float) (binSatSum[bestBin] / Math.max(1e-5, binWeights[bestBin]));
        float primaryVal = (float) (binValSum[bestBin] / Math.max(1e-5, binWeights[bestBin]));

        int secondBin = -1;
        double secondBestWeight = 0.0;
        for (int i = 0; i < HUE_BINS; i++) {
            if (binWeights[i] <= 0.0) continue;
            float candidateHue = resolveClusterHue(i, binWeights, binCosSum, binSinSum);
            if (hueDistance(primaryHue, candidateHue) >= 42f && binWeights[i] > secondBestWeight) {
                secondBestWeight = binWeights[i];
                secondBin = i;
            }
        }

        float secondaryHue;
        float secondarySat;
        float secondaryVal;
        if (secondBin >= 0 && secondBestWeight >= binWeights[bestBin] * 0.14) {
            secondaryHue = resolveClusterHue(secondBin, binWeights, binCosSum, binSinSum);
            secondarySat = (float) (binSatSum[secondBin] / Math.max(1e-5, binWeights[secondBin]));
            secondaryVal = (float) (binValSum[secondBin] / Math.max(1e-5, binWeights[secondBin]));
        } else {
            // Synthesize a complementary NeonWave split accent when cover art is single-tone
            secondaryHue = normalizeHue(primaryHue + 115f);
            secondarySat = 0.86f;
            secondaryVal = 0.94f;
        }

        return buildNeonPalette(primaryHue, primarySat, primaryVal, secondaryHue, secondarySat, secondaryVal);
    }

    public static NeonPalette buildNeonPalette(
            float primaryHue,
            float primarySat,
            float primaryVal,
            float secondaryHue,
            float secondarySat,
            float secondaryVal) {
        float pHue = normalizeHue(primaryHue);
        float sHue = normalizeHue(secondaryHue);
        float tHue = midpointHue(pHue, sHue);

        int primaryNeon = hsvColor(pHue, clamp(primarySat, 0.72f, 0.96f), clamp(primaryVal, 0.90f, 1.0f));
        int secondaryNeon = hsvColor(sHue, clamp(secondarySat, 0.76f, 0.98f), clamp(secondaryVal, 0.88f, 1.0f));
        int tertiaryGlow = hsvColor(tHue, 0.72f, 0.88f);

        int canvasStart = hsvColor(pHue, 0.70f, 0.21f);
        int canvasCenter = hsvColor(tHue, 0.66f, 0.14f);
        int canvasEnd = hsvColor(sHue, 0.62f, 0.07f);

        int cardGlassStart = NeonPalette.withAlpha(hsvColor(pHue, 0.68f, 0.26f), 0xEB);
        int cardGlassCenter = NeonPalette.withAlpha(hsvColor(tHue, 0.64f, 0.18f), 0xEB);
        int cardGlassEnd = NeonPalette.withAlpha(hsvColor(sHue, 0.60f, 0.11f), 0xEB);

        int subCardFill = NeonPalette.withAlpha(hsvColor(tHue, 0.60f, 0.16f), 0xD6);
        int subCardStroke = NeonPalette.withAlpha(tertiaryGlow, 0x55);

        int playCenterStart = hsvColor(pHue, 0.74f, 0.44f);
        int playCenterEnd = hsvColor(tHue, 0.70f, 0.26f);

        return new NeonPalette(
                primaryNeon,
                secondaryNeon,
                tertiaryGlow,
                canvasStart,
                canvasCenter,
                canvasEnd,
                cardGlassStart,
                cardGlassCenter,
                cardGlassEnd,
                subCardFill,
                subCardStroke,
                playCenterStart,
                playCenterEnd
        );
    }

    public static float hueDistance(float h1, float h2) {
        float diff = Math.abs(normalizeHue(h1) - normalizeHue(h2));
        return Math.min(diff, 360f - diff);
    }

    private static float resolveClusterHue(int centerBin, double[] weights, double[] cosSum, double[] sinSum) {
        int prev = (centerBin - 1 + HUE_BINS) % HUE_BINS;
        int next = (centerBin + 1) % HUE_BINS;
        double c = cosSum[centerBin] + cosSum[prev] * 0.5 + cosSum[next] * 0.5;
        double s = sinSum[centerBin] + sinSum[prev] * 0.5 + sinSum[next] * 0.5;
        if (Math.abs(c) < 1e-6 && Math.abs(s) < 1e-6) {
            return centerBin * (360f / HUE_BINS);
        }
        return normalizeHue((float) Math.toDegrees(Math.atan2(s, c)));
    }

    private static float midpointHue(float h1, float h2) {
        float a = normalizeHue(h1);
        float b = normalizeHue(h2);
        float diff = b - a;
        if (diff > 180f) diff -= 360f;
        if (diff < -180f) diff += 360f;
        return normalizeHue(a + diff * 0.5f);
    }

    private static float normalizeHue(float hue) {
        float h = hue % 360f;
        return h < 0f ? h + 360f : h;
    }

    private static float clamp(float val, float min, float max) {
        return Math.max(min, Math.min(max, val));
    }

    private static int hsvColor(float hue, float sat, float val) {
        return Color.HSVToColor(new float[]{hue, sat, val});
    }
}
