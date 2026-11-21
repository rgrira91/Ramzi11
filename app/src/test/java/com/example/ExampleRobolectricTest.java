package com.example;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;
import androidx.media3.common.C;
import androidx.media3.common.audio.AudioProcessor;
import androidx.test.core.app.ApplicationProvider;
import com.example.audio.AudioFxEngine;
import com.example.audio.AudioSpectrumTapProcessor;
import com.example.audio.NeonDspAudioProcessor;
import com.example.data.AudioLibraryRepository;
import com.example.data.YoutubeRepository;
import com.example.model.EqPreset;
import com.example.model.YoutubeTrack;
import com.example.ui.theme.AlbumColorExtractor;
import com.example.ui.theme.NeonPalette;
import com.example.ui.theme.UiStylePreferences;
import com.example.ui.views.DiscoStrobeOverlayView;
import com.example.ui.views.DiscoVuGlassView;
import com.example.ui.views.NeonWaveformView;
import com.example.ui.views.ProStudioRackView;
import com.example.ui.views.PurificationSuiteView;
import com.example.ui.views.SideLedMeterView;
import com.example.ui.views.StudioQuadMeterView;
import com.example.util.DeviceResourceOptimizer;
import com.example.widget.NeonWaveWidgetProvider;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36)
public class ExampleRobolectricTest {

    @Test
    public void readAppNameFromContext() {
        Context context = ApplicationProvider.getApplicationContext();
        String appName = context.getString(R.string.app_name);
        assertEquals("NeonWave", appName);
    }

    @Test
    public void audioFxEngine_togglesPersistAndWidgetUpdatesSafely() {
        Context context = ApplicationProvider.getApplicationContext();
        AudioFxEngine fx = AudioFxEngine.getInstance();
        fx.init(context);

        fx.setSkipSilenceEnabled(true);
        assertTrue(fx.isSkipSilenceEnabled());
        fx.setSkipSilenceEnabled(false);
        assertFalse(fx.isSkipSilenceEnabled());

        fx.setSmoothFadeEnabled(false);
        assertFalse(fx.isSmoothFadeEnabled());
        fx.setSmoothFadeEnabled(true);
        assertTrue(fx.isSmoothFadeEnabled());

        assertTrue(fx.isDynamicThemeEnabled());
        fx.setDynamicThemeEnabled(false);
        assertFalse(fx.isDynamicThemeEnabled());
        fx.setDynamicThemeEnabled(true);
        assertTrue(fx.isDynamicThemeEnabled());

        NeonWaveWidgetProvider.updateAllWidgets(context, "Popcorn", "Gershon Kingsley", true);
    }

    @Test
    public void eqPresetsAnd10BandCustomPersistence_workCorrectly() {
        Context context = ApplicationProvider.getApplicationContext();
        AudioFxEngine fx = AudioFxEngine.getInstance();
        fx.init(context);

        assertEquals(10, AudioFxEngine.UI_BAND_COUNT);
        assertEquals(31, AudioFxEngine.UI_BAND_FREQUENCIES_HZ[0]);
        assertEquals(62, AudioFxEngine.UI_BAND_FREQUENCIES_HZ[1]);
        assertEquals(16000, AudioFxEngine.UI_BAND_FREQUENCIES_HZ[9]);
        assertEquals(12, EqPreset.values().length);

        // Verify Bass Boost preset specs: +12dB on 31Hz & 62Hz and strength 1000
        fx.applyPreset(EqPreset.BASS_BOOST);
        assertEquals(EqPreset.BASS_BOOST, fx.getCurrentPreset());
        assertEquals(1000, EqPreset.BASS_BOOST.getBassBoostStrength());
        float[] bassGains = fx.getUiBandGainsDb();
        assertEquals(12.0f, bassGains[0], 0.01f);
        assertEquals(12.0f, bassGains[1], 0.01f);
        assertTrue(fx.getBassBoostPercent() >= 150);

        // Verify Bass slider 0%..200% clamping
        fx.setBassBoostPercent(250);
        assertEquals(200, fx.getBassBoostPercent());
        fx.setBassBoostPercent(-20);
        assertEquals(0, fx.getBassBoostPercent());
        fx.setBassBoostPercent(160);
        assertEquals(160, fx.getBassBoostPercent());

        // Verify custom slider auto-saves as CUSTOM and survives preset switches
        fx.setUiBandGainDb(0, 14.5f);
        fx.setUiBandGainDb(9, -11.0f);
        assertEquals(EqPreset.CUSTOM, fx.getCurrentPreset());

        fx.applyPreset(EqPreset.VOCAL);
        assertEquals(EqPreset.VOCAL, fx.getCurrentPreset());
        assertEquals(-2.5f, fx.getUiBandGainsDb()[0], 0.01f);

        fx.applyPreset(EqPreset.CUSTOM);
        assertEquals(EqPreset.CUSTOM, fx.getCurrentPreset());
        assertEquals(14.5f, fx.getUiBandGainsDb()[0], 0.01f);
        assertEquals(-11.0f, fx.getUiBandGainsDb()[9], 0.01f);
    }

    @Test
    public void neonDspAudioProcessor_boostsSubBassPunchAndTamesZeroBass() throws Exception {
        NeonDspAudioProcessor dsp = new NeonDspAudioProcessor();
        AudioProcessor.AudioFormat fmt = new AudioProcessor.AudioFormat(44100, 2, C.ENCODING_PCM_16BIT);
        dsp.configure(fmt);
        dsp.flush();

        // Build a Popcorn-style 60Hz sub-bass stereo buffer (~46ms)
        int frames = 2048;
        double flatRms = measureProcessedRms(dsp, frames, new float[10], 100);
        double boostedRms = measureProcessedRms(
                dsp,
                frames,
                EqPreset.BASS_BOOST.getGainsDbCopy(),
                200);
        double zeroBassRms = measureProcessedRms(dsp, frames, new float[10], 0);

        assertTrue("Expected boosted sub-bass RMS (" + boostedRms + ") > flat (" + flatRms + ")",
                boostedRms > flatRms * 1.35);
        assertTrue("Expected zero-bass RMS (" + zeroBassRms + ") < flat (" + flatRms + ")",
                zeroBassRms < flatRms * 0.55);
        assertTrue("Expected non-clipped bounded output", boostedRms <= 1.0);
    }

    private static double measureProcessedRms(
            NeonDspAudioProcessor dsp,
            int frames,
            float[] gainsDb,
            int bassPercent) {
        dsp.updateParams(true, gainsDb, bassPercent, 50, 15);
        dsp.flush();

        ByteBuffer input = ByteBuffer.allocate(frames * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < frames; i++) {
            // 60Hz sub-bass pulse at moderate headroom so boost gain shows clearly
            short sample = (short) (Math.sin(2.0 * Math.PI * 60.0 * i / 44100.0) * 6500.0);
            input.putShort(sample);
            input.putShort(sample);
        }
        input.flip();
        dsp.queueInput(input);
        ByteBuffer output = dsp.getOutput().order(ByteOrder.LITTLE_ENDIAN);

        // Measure second half after biquad transient settle
        int startSample = (frames / 2) * 2;
        int totalSamples = output.remaining() / 2;
        double sumSq = 0.0;
        int count = 0;
        for (int s = 0; s < totalSamples; s++) {
            short val = output.getShort();
            if (s >= startSample) {
                double norm = val / 32768.0;
                assertFalse(Double.isNaN(norm));
                sumSq += norm * norm;
                count++;
            }
        }
        return Math.sqrt(sumSq / Math.max(1, count));
    }

    @Test
    public void albumColorExtractor_extractsDistinctVibrantNeonPalette() {
        assertEquals(NeonPalette.DEFAULT, AlbumColorExtractor.extract(null));

        Bitmap gray = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888);
        gray.eraseColor(Color.rgb(80, 80, 80));
        assertEquals(NeonPalette.DEFAULT.primaryNeon, AlbumColorExtractor.extract(gray).primaryNeon);
        gray.recycle();

        Bitmap art = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(art);
        Paint p = new Paint();
        p.setColor(Color.rgb(16, 185, 129));
        c.drawRect(0, 0, 32, 48, p);
        p.setColor(Color.rgb(245, 158, 11));
        c.drawRect(32, 0, 48, 48, p);

        NeonPalette extracted = AlbumColorExtractor.extract(art);
        art.recycle();

        assertNotEquals(NeonPalette.DEFAULT.primaryNeon, extracted.primaryNeon);
        float[] primaryHsv = new float[3];
        float[] secondaryHsv = new float[3];
        float[] canvasHsv = new float[3];
        Color.colorToHSV(extracted.primaryNeon, primaryHsv);
        Color.colorToHSV(extracted.secondaryNeon, secondaryHsv);
        Color.colorToHSV(extracted.canvasEnd, canvasHsv);

        assertTrue(primaryHsv[1] >= 0.70f);
        assertTrue(primaryHsv[2] >= 0.85f);
        assertTrue(AlbumColorExtractor.hueDistance(primaryHsv[0], secondaryHsv[0]) >= 35f);
        assertTrue(canvasHsv[2] <= 0.15f);

        NeonPalette mid = NeonPalette.lerp(NeonPalette.DEFAULT, extracted, 0.5f);
        assertNotEquals(NeonPalette.DEFAULT.primaryNeon, mid.primaryNeon);
        assertNotEquals(extracted.primaryNeon, mid.primaryNeon);
    }

    @Test
    public void uiStylePreferences_persistsSelectedInterfaceAndControls() {
        Context context = ApplicationProvider.getApplicationContext();
        UiStylePreferences prefs = new UiStylePreferences(context);

        assertEquals(com.example.ui.theme.UiStyleMode.NEON_COSMIC, prefs.getActiveStyle());
        prefs.setActiveStyle(com.example.ui.theme.UiStyleMode.RETRO_VINYL);
        assertEquals(com.example.ui.theme.UiStyleMode.RETRO_VINYL, prefs.getActiveStyle());

        prefs.setActiveStyle(com.example.ui.theme.UiStyleMode.LIQUID_GLASS);
        assertEquals(com.example.ui.theme.UiStyleMode.LIQUID_GLASS, prefs.getActiveStyle());

        prefs.setActiveStyle(com.example.ui.theme.UiStyleMode.FLOATING_MINI);
        assertEquals(com.example.ui.theme.UiStyleMode.FLOATING_MINI, prefs.getActiveStyle());

        prefs.setFloatingMiniCollapsed(true);
        assertTrue(prefs.isFloatingMiniCollapsed());

        prefs.setShowEqInCustomStyles(false);
        assertFalse(prefs.isShowEqInCustomStyles());
    }

    @Test
    public void deviceResourceOptimizer_adaptsFpsBuffersOffloadAndPersistsModes() {
        Context context = ApplicationProvider.getApplicationContext();
        DeviceResourceOptimizer opt = DeviceResourceOptimizer.getInstance(context);

        opt.setMode(DeviceResourceOptimizer.OptimizationMode.ECO_BATTERY);
        assertEquals(DeviceResourceOptimizer.OptimizationMode.ECO_BATTERY, opt.getMode());
        assertEquals(DeviceResourceOptimizer.EffectiveTier.POWER_SAVER, opt.getEffectiveTier());
        assertEquals(20, opt.getTargetVisualFps());
        assertEquals(14, opt.getReactiveButtonFps());
        assertEquals(0, opt.getWaveformParticleCount());
        assertFalse(opt.shouldAllowStrobeFlashes());
        assertFalse(opt.shouldAnimateThemeTransitions());
        // Must NEVER offload while EQ/Bass DSP is active so EQ/BassBoost work on every device
        assertFalse(opt.shouldUseAudioOffload(true));

        opt.setMode(DeviceResourceOptimizer.OptimizationMode.PERFORMANCE);
        assertEquals(DeviceResourceOptimizer.OptimizationMode.PERFORMANCE, opt.getMode());
        assertEquals(58, opt.getTargetVisualFps());
        assertEquals(32, opt.getReactiveButtonFps());
        assertEquals(14, opt.getWaveformParticleCount());
        assertTrue(opt.shouldAllowStrobeFlashes());
        assertTrue(opt.shouldAnimateThemeTransitions());

        opt.setAudioOffloadPreferred(false);
        assertFalse(opt.shouldUseAudioOffload(false));
        opt.setAudioOffloadPreferred(true);
        assertTrue(opt.shouldUseAudioOffload(false));

        opt.setMediaAntiStutterEnabled(true);
        int[] antiStutterBuf = opt.getExoBufferConfig();
        assertEquals(5, antiStutterBuf.length);
        assertTrue(antiStutterBuf[0] >= 20_000);

        opt.setMode(DeviceResourceOptimizer.OptimizationMode.AUTO);
        DeviceResourceOptimizer.DeviceTelemetry telemetry = opt.evaluateTelemetry();
        assertTrue(telemetry.cpuCores >= 1);
        assertTrue(telemetry.totalRamMb > 0);
    }

    @Test
    public void audioLibraryRepository_trimMemoryAndShutdownDoNotCrash() {
        Context context = ApplicationProvider.getApplicationContext();
        com.example.data.AudioLibraryRepository repo = new com.example.data.AudioLibraryRepository(context);
        repo.trimMemory(android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW);
        repo.clearArtCache();
        assertEquals(0, repo.getArtCacheKb());
        repo.shutdown();
    }

    @Test
    public void visualMeterPreferences_andAll8ModeViewsWorkSafely() {
        Context context = ApplicationProvider.getApplicationContext();
        UiStylePreferences prefs = new UiStylePreferences(context);

        assertTrue(prefs.isVisualMetersEnabled());
        assertEquals(UiStylePreferences.VisualMeterMode.DISCO_VU, prefs.getVisualMeterMode());
        assertEquals(9, UiStylePreferences.VisualMeterMode.values().length);

        for (UiStylePreferences.VisualMeterMode mode : UiStylePreferences.VisualMeterMode.values()) {
            NeonPalette palette = mode.getPalette();
            assertNotEquals(0, palette.primaryNeon);
            assertNotEquals(palette.primaryNeon, palette.secondaryNeon);
        }

        prefs.setVisualMetersEnabled(false);
        assertFalse(prefs.isVisualMetersEnabled());
        prefs.setVisualMeterMode(UiStylePreferences.VisualMeterMode.CYBERPUNK);
        assertEquals(UiStylePreferences.VisualMeterMode.CYBERPUNK, prefs.getVisualMeterMode());
        prefs.setVisualMeterMode(UiStylePreferences.VisualMeterMode.DISCO_VU);
        prefs.setVisualMetersEnabled(true);

        SideLedMeterView sideView = new SideLedMeterView(context);
        sideView.setChannel(SideLedMeterView.CHANNEL_RIGHT);
        sideView.setLedColorScheme(SideLedMeterView.LedColorScheme.BLUE_CYAN);
        sideView.setTargetFps(60);
        sideView.setPlaying(true);
        sideView.updateSpectrum(new float[]{0.3f, 0.7f, 0.9f, 0.5f}, 0.65f);
        sideView.measure(
                View.MeasureSpec.makeMeasureSpec(40, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY)
        );
        sideView.layout(0, 0, 40, 400);
        Bitmap sideBmp = Bitmap.createBitmap(40, 400, Bitmap.Config.ARGB_8888);
        sideView.draw(new Canvas(sideBmp));
        sideBmp.recycle();

        DiscoVuGlassView discoView = new DiscoVuGlassView(context);
        discoView.setSampleRateHz(48000);
        discoView.setTargetFps(60);
        discoView.setPlaying(true);
        discoView.updateStereoSpectrum(new float[]{0.4f, 0.8f, 0.6f, 0.9f}, 0.72f, 0.75f, 0.68f, 0.88f, 48000);
        discoView.measure(
                View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(440, View.MeasureSpec.EXACTLY)
        );
        discoView.layout(0, 0, 600, 440);
        Bitmap discoBmp = Bitmap.createBitmap(600, 440, Bitmap.Config.ARGB_8888);
        discoView.draw(new Canvas(discoBmp));
        discoBmp.recycle();

        StudioQuadMeterView studioView = new StudioQuadMeterView(context);
        studioView.setTargetFps(58);
        studioView.setPlaying(true);
        studioView.updateFourChannels(0.75f, 0.70f, 0.90f, 0.55f);
        studioView.measure(
                View.MeasureSpec.makeMeasureSpec(540, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY)
        );
        studioView.layout(0, 0, 540, 360);
        Bitmap studioBmp = Bitmap.createBitmap(540, 360, Bitmap.Config.ARGB_8888);
        studioView.draw(new Canvas(studioBmp));
        studioBmp.recycle();

        DiscoStrobeOverlayView strobeView = new DiscoStrobeOverlayView(context);
        strobeView.setActive(true);
        strobeView.setStrobeFlashesAllowed(true);
        strobeView.onBeatSample(0.8f, 0.92f);
        strobeView.measure(
                View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY)
        );
        strobeView.layout(0, 0, 400, 800);
        Bitmap strobeBmp = Bitmap.createBitmap(400, 800, Bitmap.Config.ARGB_8888);
        strobeView.draw(new Canvas(strobeBmp));
        strobeBmp.recycle();

        NeonWaveformView waveform = new NeonWaveformView(context);
        waveform.setPlaying(true);
        waveform.setMaxParticleCount(7);
        waveform.updateStereoWaveform(new float[]{0.8f, 0.6f, 0.4f, 0.3f}, 0.7f, 0.95f);
        waveform.measure(
                View.MeasureSpec.makeMeasureSpec(500, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.EXACTLY)
        );
        waveform.layout(0, 0, 500, 200);
        for (NeonWaveformView.VisualMode wfMode : new NeonWaveformView.VisualMode[]{
                NeonWaveformView.VisualMode.HEARTBEAT,
                NeonWaveformView.VisualMode.OCEAN_WAVE,
                NeonWaveformView.VisualMode.FIRE_FLAME
        }) {
            waveform.setVisualMode(wfMode);
            Bitmap wfBmp = Bitmap.createBitmap(500, 200, Bitmap.Config.ARGB_8888);
            waveform.draw(new Canvas(wfBmp));
            wfBmp.recycle();
        }
    }

    @Test
    public void repository_hideAndUnhideTracksWorks() {
        Context context = ApplicationProvider.getApplicationContext();
        com.example.data.AudioLibraryRepository repo = new com.example.data.AudioLibraryRepository(context);
        repo.unhideAllTracks();
        assertEquals(0, repo.getHiddenTrackCount());

        repo.hideTrackFromLibrary("track_123");
        assertEquals(1, repo.getHiddenTrackCount());
        assertTrue(repo.getHiddenTrackIds().contains("track_123"));

        repo.unhideAllTracks();
        assertEquals(0, repo.getHiddenTrackCount());
        repo.shutdown();
    }

    @Test
    public void mainActivity_8VisualModesToggleSwitchAndBassSliderWork() {
        MainActivity activity = Robolectric.buildActivity(MainActivity.class).create().get();
        View btnPower = activity.findViewById(R.id.btn_meter_power);
        View btnSwitch = activity.findViewById(R.id.btn_meter_switch);
        View sideLeft = activity.findViewById(R.id.side_led_left);
        View sideRight = activity.findViewById(R.id.side_led_right);
        View discoVu = activity.findViewById(R.id.disco_vu_glass_view);
        View studioQuad = activity.findViewById(R.id.studio_quad_meter_view);
        View vinylDisc = activity.findViewById(R.id.vinyl_mode_disc_view);
        View waveform = activity.findViewById(R.id.neon_waveform_view);
        View strobe = activity.findViewById(R.id.disco_strobe_overlay);
        SeekBar bassSeek = activity.findViewById(R.id.seek_hero_bass_drive);
        TextView bassPctText = activity.findViewById(R.id.text_hero_bass_percent);

        // 1. Default mode: DISCO_VU
        assertEquals(View.VISIBLE, discoVu.getVisibility());
        assertEquals(View.GONE, sideLeft.getVisibility());
        assertEquals(View.GONE, studioQuad.getVisibility());
        assertEquals(View.GONE, vinylDisc.getVisibility());
        assertEquals(View.VISIBLE, waveform.getVisibility());

        // 2. Switch -> DISCO_MODE (full-screen club mode: Disco VU + Side LED + Strobe overlay)
        btnSwitch.performClick();
        assertEquals(View.VISIBLE, discoVu.getVisibility());
        assertEquals(View.VISIBLE, sideLeft.getVisibility());
        assertEquals(View.VISIBLE, sideRight.getVisibility());
        assertEquals(View.VISIBLE, strobe.getVisibility());

        // 3. Switch -> SIDE_LED
        btnSwitch.performClick();
        assertEquals(View.VISIBLE, sideLeft.getVisibility());
        assertEquals(View.VISIBLE, sideRight.getVisibility());
        assertEquals(View.GONE, discoVu.getVisibility());
        assertEquals(View.GONE, strobe.getVisibility());

        // 4. Switch -> STUDIO
        btnSwitch.performClick();
        assertEquals(View.VISIBLE, studioQuad.getVisibility());
        assertEquals(View.GONE, sideLeft.getVisibility());

        // 5. Switch -> VINYL (centered rotating vinyl disc replaces central waveform)
        btnSwitch.performClick();
        assertEquals(View.VISIBLE, vinylDisc.getVisibility());
        assertEquals(View.GONE, waveform.getVisibility());
        assertEquals(View.GONE, studioQuad.getVisibility());

        // 6. Switch -> CYBERPUNK
        btnSwitch.performClick();
        assertEquals(View.GONE, vinylDisc.getVisibility());
        assertEquals(View.VISIBLE, waveform.getVisibility());
        assertEquals(View.VISIBLE, sideLeft.getVisibility());

        // 7. Switch -> PARTY_STROBE
        btnSwitch.performClick();
        assertEquals(View.VISIBLE, strobe.getVisibility());
        assertEquals(View.VISIBLE, discoVu.getVisibility());

        // Turn meters OFF
        btnPower.performClick();
        assertEquals(View.GONE, sideLeft.getVisibility());
        assertEquals(View.GONE, sideRight.getVisibility());
        assertEquals(View.GONE, discoVu.getVisibility());
        assertEquals(View.GONE, studioQuad.getVisibility());
        assertEquals(View.GONE, strobe.getVisibility());

        // Turn meters ON again -> restores PARTY_STROBE
        btnPower.performClick();
        assertEquals(View.VISIBLE, strobe.getVisibility());
        assertEquals(View.VISIBLE, discoVu.getVisibility());

        // Verify Hero Bass Slider supports 0..200% and syncs Engine + TextView on user input
        assertEquals(200, bassSeek.getMax());
        bassSeek.setProgress(194);
        bassSeek.onKeyDown(
                android.view.KeyEvent.KEYCODE_DPAD_RIGHT,
                new android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_DPAD_RIGHT));
        assertEquals(195, AudioFxEngine.getInstance().getBassBoostPercent());
        assertEquals("195%", bassPctText.getText().toString());
    }

    @Test
    public void audioSpectrumTapProcessor_extractsStereoLevelsBassAndTreble() {
        com.example.audio.AudioSpectrumTapProcessor tap = new com.example.audio.AudioSpectrumTapProcessor();
        tap.setCaptureEnabled(true);
        tap.flush(44100, 2, C.ENCODING_PCM_16BIT);

        int frames = 512;
        ByteBuffer buf = ByteBuffer.allocate(frames * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < frames; i++) {
            short left = (short) (Math.sin(2.0 * Math.PI * 120.0 * i / 44100.0) * 24000);
            short right = (short) (Math.sin(2.0 * Math.PI * 4000.0 * i / 44100.0) * 3000);
            buf.putShort(left);
            buf.putShort(right);
        }
        buf.flip();
        tap.handleBuffer(buf);

        com.example.audio.AudioSpectrumTapProcessor.SpectrumFrame out =
                new com.example.audio.AudioSpectrumTapProcessor.SpectrumFrame();
        com.example.audio.AudioSpectrumTapProcessor.SpectrumFrame polled =
                tap.pollTimeAlignedFrame(android.os.SystemClock.uptimeMillis() + 500L, out);
        assertTrue(polled != null);
        assertTrue(polled.leftLevel > polled.rightLevel);
        assertTrue(polled.bassEnergy > 0.1f);
        assertTrue(polled.trebleEnergy >= 0f);
        assertEquals(44100, polled.sampleRateHz);

        int c0 = com.example.ui.theme.ReactiveButtonAnimator.sampleCycleColor(0f, false);
        int c120 = com.example.ui.theme.ReactiveButtonAnimator.sampleCycleColor(120f, false);
        assertNotEquals(c0, c120);
    }

    @Test
    public void neonDspAudioProcessor_stereoBalance8dOrbitAndPeakLimiter_workAccurately() throws Exception {
        NeonDspAudioProcessor dsp = new NeonDspAudioProcessor();
        AudioProcessor.AudioFormat fmt = new AudioProcessor.AudioFormat(44100, 2, C.ENCODING_PCM_16BIT);
        dsp.configure(fmt);

        // 1. Balance -100 (Full Left): left RMS > right RMS
        dsp.updateParams(true, new float[10], 100, 0, 0, -100, false);
        dsp.flush();
        int frames = 1024;
        ByteBuffer leftInput = ByteBuffer.allocate(frames * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < frames; i++) {
            short s = (short) (Math.sin(2.0 * Math.PI * 440.0 * i / 44100.0) * 12000.0);
            leftInput.putShort(s);
            leftInput.putShort(s);
        }
        leftInput.flip();
        dsp.queueInput(leftInput);
        ByteBuffer leftOut = dsp.getOutput().order(ByteOrder.LITTLE_ENDIAN);
        double sumL = 0.0, sumR = 0.0;
        while (leftOut.remaining() >= 4) {
            double l = leftOut.getShort() / 32768.0;
            double r = leftOut.getShort() / 32768.0;
            sumL += l * l;
            sumR += r * r;
        }
        assertTrue("Full-Left balance should make Left energy dominate Right", sumL > sumR * 10.0);

        // 2. Balance +100 (Full Right) & Peak Warning at full-scale clipping drive
        dsp.updateParams(true, EqPreset.BASS_BOOST.getGainsDbCopy(), 200, 30, 80, 100, true);
        dsp.flush();
        ByteBuffer hotInput = ByteBuffer.allocate(frames * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < frames; i++) {
            short s = (short) (Math.sin(2.0 * Math.PI * 60.0 * i / 44100.0) * 31000.0);
            hotInput.putShort(s);
            hotInput.putShort(s);
        }
        hotInput.flip();
        dsp.queueInput(hotInput);
        ByteBuffer hotOut = dsp.getOutput().order(ByteOrder.LITTLE_ENDIAN);
        double hotSumL = 0.0, hotSumR = 0.0;
        while (hotOut.remaining() >= 4) {
            double l = hotOut.getShort() / 32768.0;
            double r = hotOut.getShort() / 32768.0;
            hotSumL += l * l;
            hotSumR += r * r;
        }
        assertTrue("Full-Right balance should make Right energy dominate Left", hotSumR > hotSumL * 10.0);
        assertTrue("Hot sub-bass boost should trigger peak limiter warning", dsp.consumePeakWarning());
        assertFalse("Peak warning flag should reset after consumePeakWarning()", dsp.consumePeakWarning());
    }

    @Test
    public void audioFxEngine_perDeviceProfilesCrossfadeAndProTogglesPersist() {
        Context context = ApplicationProvider.getApplicationContext();
        AudioFxEngine fx = AudioFxEngine.getInstance();
        fx.init(context);

        fx.switchToDeviceProfile(AudioFxEngine.DEVICE_SPEAKER);
        fx.applyPreset(EqPreset.VOCAL);
        fx.setBassBoostPercent(105);
        fx.setBalancePercent(15);
        fx.setSpatial8dEnabled(false);

        fx.switchToDeviceProfile(AudioFxEngine.DEVICE_WIRED);
        fx.applyPreset(EqPreset.BASS_BOOST);
        fx.setBassBoostPercent(190);
        fx.setBalancePercent(-25);
        fx.setSpatial8dEnabled(true);

        // Switch back to speaker and verify speaker profile was preserved independently
        fx.switchToDeviceProfile(AudioFxEngine.DEVICE_SPEAKER);
        assertEquals(EqPreset.VOCAL, fx.getCurrentPreset());
        assertEquals(105, fx.getBassBoostPercent());
        assertEquals(15, fx.getBalancePercent());
        assertFalse(fx.isSpatial8dEnabled());

        // Switch back to wired and verify wired profile was restored
        fx.switchToDeviceProfile(AudioFxEngine.DEVICE_WIRED);
        assertEquals(EqPreset.BASS_BOOST, fx.getCurrentPreset());
        assertEquals(190, fx.getBassBoostPercent());
        assertEquals(-25, fx.getBalancePercent());
        assertTrue(fx.isSpatial8dEnabled());

        // Verify crossfade clamping & toggles
        fx.setCrossfadeSeconds(25);
        assertEquals(10, fx.getCrossfadeSeconds());
        fx.setCrossfadeSeconds(6);
        assertEquals(6, fx.getCrossfadeSeconds());
        fx.setAutoDeviceEqEnabled(false);
        assertFalse(fx.isAutoDeviceEqEnabled());
        fx.setAutoDeviceEqEnabled(true);
        assertTrue(fx.isAutoDeviceEqEnabled());
        fx.setHapticBassEnabled(true);
        assertTrue(fx.isHapticBassEnabled());
        fx.setEdgeGlowEnabled(false);
        assertFalse(fx.isEdgeGlowEnabled());
        fx.setEdgeGlowEnabled(true);
        fx.setSpatial8dEnabled(false);
        fx.setBalancePercent(0);
        fx.setCrossfadeSeconds(0);
    }

    @Test
    public void listeningStatsAndSettingsBackup_roundtripRestoresEqAndPlaylists() {
        Context context = ApplicationProvider.getApplicationContext();
        com.example.data.ListeningStatsTracker stats = new com.example.data.ListeningStatsTracker(context);
        stats.resetStats();

        com.example.model.Track track = new com.example.model.Track(
                "trk_popcorn",
                android.net.Uri.parse("content://media/external/audio/media/99"),
                "Popcorn",
                "Gershon Kingsley",
                "Music to Moog By",
                148_000L,
                "FLAC",
                950,
                44100,
                "audio/flac",
                null,
                12_500_000L,
                "Electronic",
                1700000L,
                false,
                true
        );
        stats.recordTrackStarted(track);
        stats.recordTrackStarted(track);
        stats.addListeningSeconds(95L);

        com.example.data.ListeningStatsTracker.InsightsSummary summary = stats.buildSummary();
        assertEquals(95L, summary.totalListeningSeconds);
        assertEquals(2, summary.totalPlays);
        assertEquals("Popcorn", summary.topTrackTitle);
        assertEquals("Gershon Kingsley", summary.topArtistName);
        assertEquals(Integer.valueOf(2), summary.formatCounts.get("FLAC"));

        // Configure FX + Playlist and export JSON
        AudioFxEngine fx = AudioFxEngine.getInstance();
        fx.init(context);
        fx.setUiBandGainDb(0, 13.0f);
        fx.setUiBandGainDb(1, 11.5f);
        fx.setBassBoostPercent(185);
        fx.setBalancePercent(-35);
        fx.setSpatial8dEnabled(true);
        fx.setCrossfadeSeconds(4);

        com.example.data.PlaylistRepository plRepo = new com.example.data.PlaylistRepository(context);
        com.example.model.Playlist pl = plRepo.createPlaylist("Moog Classics");
        plRepo.addTrackToPlaylist(pl.getId(), "trk_popcorn");

        String backupJson = com.example.util.SettingsBackupManager.exportBackupJson(context);
        assertTrue(backupJson.contains("Moog Classics"));

        // Mutate state completely
        fx.applyPreset(EqPreset.FLAT);
        fx.setBassBoostPercent(50);
        fx.setBalancePercent(40);
        fx.setSpatial8dEnabled(false);
        fx.setCrossfadeSeconds(0);
        plRepo.deletePlaylist(pl.getId());

        // Restore JSON and verify roundtrip
        assertTrue(com.example.util.SettingsBackupManager.importBackupJson(context, backupJson));
        assertEquals(EqPreset.CUSTOM, fx.getCurrentPreset());
        assertEquals(13.0f, fx.getUiBandGainsDb()[0], 0.01f);
        assertEquals(11.5f, fx.getUiBandGainsDb()[1], 0.01f);
        assertEquals(185, fx.getBassBoostPercent());
        assertEquals(-35, fx.getBalancePercent());
        assertTrue(fx.isSpatial8dEnabled());
        assertEquals(4, fx.getCrossfadeSeconds());
        boolean foundRestoredPlaylist = false;
        for (com.example.model.Playlist p : plRepo.getPlaylists()) {
            if ("Moog Classics".equals(p.getName()) && p.getTrackIds().contains("trk_popcorn")) {
                foundRestoredPlaylist = true;
                break;
            }
        }
        assertTrue("Expected playlist to be restored from JSON backup", foundRestoredPlaylist);
    }

    @Test
    public void storyCardExporterAndNeonEdgeGlowView_renderSafely() {
        Context context = ApplicationProvider.getApplicationContext();
        com.example.model.Track track = new com.example.model.Track(
                "trk_card",
                android.net.Uri.parse("content://media/external/audio/media/77"),
                "Popcorn",
                "Gershon Kingsley",
                "Synth",
                148_000L,
                "MP3",
                320,
                44100,
                "audio/mpeg",
                null,
                5_000_000L,
                "Music",
                1700000L,
                false,
                false
        );
        android.net.Uri cardUri = com.example.util.NowPlayingCardExporter.renderCardToCache(
                context,
                track,
                null,
                NeonPalette.DEFAULT,
                "Bass Boost",
                175,
                "Disco VU"
        );
        assertTrue("Expected FileProvider URI for PNG card", cardUri != null);
        java.io.File outFile = new java.io.File(new java.io.File(context.getCacheDir(), "shared_cards"),
                "neonwave_now_playing.png");
        assertTrue("Expected cached Story Card PNG file to exist", outFile.exists() && outFile.length() > 500);

        com.example.ui.views.NeonEdgeGlowView edgeView = new com.example.ui.views.NeonEdgeGlowView(context);
        edgeView.setEdgeColors(NeonPalette.DEFAULT.primaryNeon, NeonPalette.DEFAULT.secondaryNeon);
        edgeView.setTargetFps(40);
        edgeView.setActive(true);
        edgeView.onBassSample(0.92f);
        edgeView.measure(
                View.MeasureSpec.makeMeasureSpec(480, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(900, View.MeasureSpec.EXACTLY)
        );
        edgeView.layout(0, 0, 480, 900);
        Bitmap bmp = Bitmap.createBitmap(480, 900, Bitmap.Config.ARGB_8888);
        edgeView.draw(new Canvas(bmp));
        bmp.recycle();
    }

    @Test
    public void mainActivity_modernUiControlsAndDriveMode_bindCleanly() {
        MainActivity activity = Robolectric.buildActivity(MainActivity.class).create().get();
        View btnDriveMode = activity.findViewById(R.id.btn_drive_mode);
        TextView badgePeak = activity.findViewById(R.id.badge_peak_clip);
        TextView badgeDevice = activity.findViewById(R.id.badge_output_device);
        View edgeGlow = activity.findViewById(R.id.neon_edge_glow_view);
        View stickyMini = activity.findViewById(R.id.sticky_scroll_mini_player);

        assertTrue(btnDriveMode != null);
        assertTrue(badgePeak != null);
        assertTrue(badgeDevice != null);
        assertTrue(edgeGlow != null);
        assertTrue(stickyMini != null);
        assertEquals(View.GONE, stickyMini.getVisibility());

        // Clicking Drive Mode opens high-contrast Drive Mode dialog without crashing
        btnDriveMode.performClick();
    }

    @Test
    public void discoModeAndColorPulsingButtons_workEndToEnd() {
        Context context = ApplicationProvider.getApplicationContext();
        UiStylePreferences prefs = new UiStylePreferences(context);
        assertTrue(prefs.isColorPulseButtonsEnabled());
        prefs.setColorPulseButtonsEnabled(false);
        assertFalse(prefs.isColorPulseButtonsEnabled());
        prefs.setColorPulseButtonsEnabled(true);
        assertTrue(prefs.isColorPulseButtonsEnabled());

        // Verify ReactiveButtonAnimator toggles and whole-screen Disco club flag
        com.example.ui.theme.ReactiveButtonAnimator animator = new com.example.ui.theme.ReactiveButtonAnimator();
        assertTrue(animator.isColorPulseEnabled());
        animator.setColorPulseEnabled(false);
        assertFalse(animator.isColorPulseEnabled());
        animator.setColorPulseEnabled(true);
        animator.setDiscoClubMode(true);
        assertTrue(animator.isDiscoClubMode());
        animator.setDiscoClubMode(false);
        assertFalse(animator.isDiscoClubMode());

        // Verify DiscoStrobeOverlayView full-screen Disco Mode club lasers & sparkles render safely
        DiscoStrobeOverlayView clubOverlay = new DiscoStrobeOverlayView(context);
        clubOverlay.setDiscoClubMode(true);
        clubOverlay.setStrobeFlashesAllowed(true);
        clubOverlay.setActive(true);
        clubOverlay.onBeatSample(0.85f, 0.95f, 0.78f);
        clubOverlay.measure(
                View.MeasureSpec.makeMeasureSpec(480, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(900, View.MeasureSpec.EXACTLY)
        );
        clubOverlay.layout(0, 0, 480, 900);
        Bitmap clubBmp = Bitmap.createBitmap(480, 900, Bitmap.Config.ARGB_8888);
        clubOverlay.draw(new Canvas(clubBmp));
        clubBmp.recycle();

        // Verify NeonEdgeGlowView rainbow cycle renders safely
        com.example.ui.views.NeonEdgeGlowView rainbowEdge = new com.example.ui.views.NeonEdgeGlowView(context);
        rainbowEdge.setRainbowCycle(true);
        rainbowEdge.setActive(true);
        rainbowEdge.onBassSample(0.88f);
        rainbowEdge.measure(
                View.MeasureSpec.makeMeasureSpec(480, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(900, View.MeasureSpec.EXACTLY)
        );
        rainbowEdge.layout(0, 0, 480, 900);
        Bitmap edgeBmp = Bitmap.createBitmap(480, 900, Bitmap.Config.ARGB_8888);
        rainbowEdge.draw(new Canvas(edgeBmp));
        edgeBmp.recycle();

        // Verify 1-tap Disco Mode toggle button in MainActivity activates and restores previous mode
        MainActivity activity = Robolectric.buildActivity(MainActivity.class).create().get();
        View btnDiscoToggle = activity.findViewById(R.id.btn_disco_mode_toggle);
        View strobe = activity.findViewById(R.id.disco_strobe_overlay);
        View sideLeft = activity.findViewById(R.id.side_led_left);
        View discoVu = activity.findViewById(R.id.disco_vu_glass_view);
        assertTrue(btnDiscoToggle != null);

        btnDiscoToggle.performClick();
        assertEquals(UiStylePreferences.VisualMeterMode.DISCO_MODE, prefs.getVisualMeterMode());
        assertEquals(View.VISIBLE, strobe.getVisibility());
        assertEquals(View.VISIBLE, sideLeft.getVisibility());
        assertEquals(View.VISIBLE, discoVu.getVisibility());

        // Tapping again toggles off DISCO_MODE back to previous non-disco mode
        btnDiscoToggle.performClick();
        assertNotEquals(UiStylePreferences.VisualMeterMode.DISCO_MODE, prefs.getVisualMeterMode());
        assertEquals(View.GONE, strobe.getVisibility());
    }

    @Test
    public void glassNavBar_rendersBlurredBackdropAndNavigatesCleanly() {
        Context context = ApplicationProvider.getApplicationContext();
        UiStylePreferences prefs = new UiStylePreferences(context);
        assertTrue(prefs.isGlassNavBarEnabled());
        prefs.setGlassNavBarEnabled(false);
        assertFalse(prefs.isGlassNavBarEnabled());
        prefs.setGlassNavBarEnabled(true);

        // Standalone measure, layout, draw across dark/light canvas & RTL modes
        com.example.ui.views.GlassNavBar bar = new com.example.ui.views.GlassNavBar(context);
        bar.setItems(
                new com.example.ui.views.GlassNavBar.Item("Player", com.example.ui.views.GlassNavBar.ICON_PLAYER, com.example.ui.views.GlassNavBar.FX_WAVE),
                new com.example.ui.views.GlassNavBar.Item("Disco", com.example.ui.views.GlassNavBar.ICON_DISCO, com.example.ui.views.GlassNavBar.FX_GLOW),
                new com.example.ui.views.GlassNavBar.Item("EQ", com.example.ui.views.GlassNavBar.ICON_EQ, com.example.ui.views.GlassNavBar.FX_SPIN),
                new com.example.ui.views.GlassNavBar.Item("Library", com.example.ui.views.GlassNavBar.ICON_LIBRARY, com.example.ui.views.GlassNavBar.FX_DROP),
                new com.example.ui.views.GlassNavBar.Item("Studio", com.example.ui.views.GlassNavBar.ICON_STUDIO, com.example.ui.views.GlassNavBar.FX_PLANE)
        );
        bar.setAdaptiveCaptureConfig(60L, true);
        bar.setAccentColor(0xFFEC4899);
        bar.setGlassTint(0x44140A2E);
        bar.setBackdropBaseColor(0xFF070214);
        bar.measure(
                View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(128, View.MeasureSpec.EXACTLY)
        );
        bar.layout(0, 0, 720, 128);
        Bitmap bmp = Bitmap.createBitmap(720, 128, Bitmap.Config.ARGB_8888);
        bar.draw(new Canvas(bmp));

        bar.setLightCanvas(true);
        bar.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        bar.select(2, true);
        bar.draw(new Canvas(bmp));
        assertEquals(2, bar.getSelectedIndex());
        bmp.recycle();

        // MainActivity integration: verify GlassNavBar binds, avoids mini-player overlap, and switches tabs
        MainActivity activity = Robolectric.buildActivity(MainActivity.class).create().get();
        com.example.ui.views.GlassNavBar nav = activity.findViewById(R.id.glass_nav_bar);
        View sticky = activity.findViewById(R.id.sticky_scroll_mini_player);
        assertTrue(nav != null);
        assertEquals(View.VISIBLE, nav.getVisibility());

        int stickyBottom = ((android.view.ViewGroup.MarginLayoutParams) sticky.getLayoutParams()).bottomMargin;
        assertTrue("Sticky mini player should sit cleanly above GlassNavBar", stickyBottom > 40);

        nav.select(1, false); // ensure clean starting state
        assertEquals(1, nav.getSelectedIndex());
        assertEquals("Audio Lab", context.getString(R.string.btn_audio_lab_insights));
    }

    @Test
    public void purificationSuiteAndUpgradedVu_processRealAudioAndRenderCleanly() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();

        // 1. Verify Real Brickwall Limiter Ceiling (-6dB -> ~0.501 peak max) in NeonDspAudioProcessor
        NeonDspAudioProcessor dsp = new NeonDspAudioProcessor();
        AudioProcessor.AudioFormat fmt = new AudioProcessor.AudioFormat(44100, 2, C.ENCODING_PCM_16BIT);
        dsp.configure(fmt);
        dsp.flush();
        float[] flat = new float[10];
        dsp.updateParams(true, flat, 100, 0, 0, 0, false);
        dsp.updatePurificationParams(0f, 0f, 1.0f, 0f, false, false, 0f, 0f, 100, -6.0f, false, 20, 22000);

        int frames = 512;
        ByteBuffer fullScale = ByteBuffer.allocateDirect(frames * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < frames; i++) {
            float s = (float) Math.sin(2.0 * Math.PI * 1000.0 * i / 44100.0) * 0.95f;
            short pcm = (short) Math.round(s * 32767f);
            fullScale.putShort(pcm);
            fullScale.putShort(pcm);
        }
        fullScale.flip();
        dsp.queueInput(fullScale);
        ByteBuffer limitedOut = dsp.getOutput().order(ByteOrder.LITTLE_ENDIAN);
        float maxLimited = 0f;
        while (limitedOut.remaining() >= 4) {
            float l = Math.abs(limitedOut.getShort() / 32768f);
            limitedOut.getShort();
            if (l > maxLimited) maxLimited = l;
        }
        assertTrue("Limiter at -6dB should clamp peak <= 0.54, was " + maxLimited, maxLimited > 0.1f && maxLimited <= 0.54f);
        assertTrue("Limiter should latch peak engagement when clamping", dsp.consumeLimiterEngaged());

        // 2. Verify Real HPF (220Hz) attenuates 40Hz sub-bass cleanly
        dsp.flush();
        dsp.updatePurificationParams(0f, 0f, 1.0f, 0f, false, false, 0f, 0f, 100, 0.0f, false, 220, 22000);
        ByteBuffer lowBass = ByteBuffer.allocateDirect(frames * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < frames; i++) {
            float s = (float) Math.sin(2.0 * Math.PI * 40.0 * i / 44100.0) * 0.7f;
            short pcm = (short) Math.round(s * 32767f);
            lowBass.putShort(pcm);
            lowBass.putShort(pcm);
        }
        lowBass.flip();
        dsp.queueInput(lowBass);
        ByteBuffer hpfOut = dsp.getOutput().order(ByteOrder.LITTLE_ENDIAN);
        double hpfRmsSum = 0;
        int hpfSamples = 0;
        while (hpfOut.remaining() >= 4) {
            float l = hpfOut.getShort() / 32768f;
            hpfOut.getShort();
            hpfRmsSum += l * l;
            hpfSamples++;
        }
        double hpfRms = Math.sqrt(hpfRmsSum / Math.max(1, hpfSamples));
        assertTrue("HPF 220Hz should strongly attenuate 40Hz sub-bass, rms=" + hpfRms, hpfRms < 0.25);

        // 3. Verify AudioFxEngine 5-band fader <-> 10-band EQ bi-directional sync
        AudioFxEngine fx = AudioFxEngine.getInstance();
        fx.init(context);
        fx.setFiveBandFaderDb(0, 7.5f);
        float[] bands = fx.getUiBandGainsDb();
        assertEquals(7.5f, bands[0], 0.01f);
        assertEquals(7.5f, bands[1], 0.01f);
        assertEquals(EqPreset.CUSTOM, fx.getCurrentPreset());
        assertEquals(7.5f, fx.getFiveBandFadersDb()[0], 0.01f);

        // 4. Render upgraded DiscoVuGlassView (32-band + AVG dB + CLIP + GAIN L/R) & PurificationSuiteView
        DiscoVuGlassView vu = new DiscoVuGlassView(context);
        vu.setPlaying(true);
        vu.setModeTitle("DISCO MODE");
        vu.setGainKnobs(1.5f, -1.0f);
        vu.updateMeterTelemetry(-6.2f, -3.8f, true, 24);
        float[] spec32 = new float[42];
        for (int i = 0; i < spec32.length; i++) spec32[i] = 0.4f + 0.5f * (i / 42f);
        vu.updateStereoSpectrum(spec32, 0.78f, 0.74f, 0.85f, 0.65f, 44100);
        vu.measure(
                View.MeasureSpec.makeMeasureSpec(900, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(640, View.MeasureSpec.EXACTLY)
        );
        vu.layout(0, 0, 900, 640);
        Bitmap vuBmp = Bitmap.createBitmap(900, 640, Bitmap.Config.ARGB_8888);
        vu.draw(new Canvas(vuBmp));
        vuBmp.recycle();

        PurificationSuiteView suite = new PurificationSuiteView(context);
        suite.syncFromEngine(new float[]{4f, 2f, 0f, -1.5f, 3f}, 3f, -12f, true, true, 3.0f, 2.5f, 120, -1.0f, true, 80, 18000, 24);
        suite.measure(
                View.MeasureSpec.makeMeasureSpec(900, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(680, View.MeasureSpec.EXACTLY)
        );
        suite.layout(0, 0, 900, 680);
        Bitmap suiteBmp = Bitmap.createBitmap(900, 680, Bitmap.Config.ARGB_8888);
        suite.draw(new Canvas(suiteBmp));
        suiteBmp.recycle();

        // 5. Verify MainActivity reveals both DiscoVuGlassView and PurificationSuiteView together
        UiStylePreferences prefs = new UiStylePreferences(context);
        prefs.setVisualMetersEnabled(true);
        prefs.setVisualMeterMode(UiStylePreferences.VisualMeterMode.DISCO_VU);
        MainActivity activity = Robolectric.buildActivity(MainActivity.class).create().get();
        View activityVu = activity.findViewById(R.id.disco_vu_glass_view);
        View activitySuite = activity.findViewById(R.id.purification_suite_view);
        assertTrue(activityVu != null && activitySuite != null);
        assertEquals(View.VISIBLE, activityVu.getVisibility());
        assertEquals(View.VISIBLE, activitySuite.getVisibility());
    }

    @Test
    public void proStudioRack_dspTapSnapshotsResumeYoutubeAndViewRender() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();

        // 1. Test NeonDspAudioProcessor Studio DSP: Master Bypass, Tube/Reverb, DJ Filter, Pads & Curve
        NeonDspAudioProcessor dsp = new NeonDspAudioProcessor();
        AudioProcessor.AudioFormat fmt = new AudioProcessor.AudioFormat(44100, 2, C.ENCODING_PCM_16BIT);
        dsp.configure(fmt);
        dsp.flush();

        int frames = 512;
        ByteBuffer input = ByteBuffer.allocateDirect(frames * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < frames; i++) {
            float l = (float) (0.35 * Math.sin(2.0 * Math.PI * 1000.0 * i / 44100.0));
            float r = (float) (0.25 * Math.sin(2.0 * Math.PI * 1200.0 * i / 44100.0));
            input.putShort((short) (l * 32767f));
            input.putShort((short) (r * 32767f));
        }
        input.flip();

        // Master bypass must pass samples cleanly through without altering values
        dsp.updateStudioParams(true, true, 80, 60, true, true, 70, 40, 50, -80, NeonDspAudioProcessor.PAD_BASS_KILL);
        dsp.queueInput(input.duplicate());
        ByteBuffer bypassOut = dsp.getOutput().order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(frames * 4, bypassOut.remaining());
        assertEquals(input.getShort(20), bypassOut.getShort(20));

        float[] flatCurve = new float[48];
        dsp.computeFrequencyResponseDb(flatCurve, 31f, 16000f);
        assertEquals(0f, flatCurve[0], 0.01f);

        // Disable bypass & enable Tube, Crossfeed, Reverb, DJ LPF sweep, and Lo-Fi Pad
        dsp.flush();
        dsp.updateParams(true, new float[]{4f, 2f, 0f, -2f, 1f, 0f, 2f, 3f, 4f, 1f}, 120, 40, 20, 0, false);
        dsp.updateStudioParams(false, true, 75, 50, true, true, 55, 35, 30, -80, NeonDspAudioProcessor.PAD_LOFI_RADIO);
        dsp.queueInput(input.duplicate());
        ByteBuffer activeOut = dsp.getOutput().order(ByteOrder.LITTLE_ENDIAN);
        assertTrue(activeOut.remaining() > 0);
        assertTrue(dsp.getLiveTubeGlow() > 0f);

        float[] shapedCurve = new float[48];
        dsp.computeFrequencyResponseDb(shapedCurve, 31f, 16000f);
        // Low-pass DJ sweep (-80) + Lo-Fi pad heavily attenuates 16kHz tail compared to midrange
        assertTrue(shapedCurve[47] < shapedCurve[20]);

        // 2. Test AudioSpectrumTapProcessor XY Vectorscope, Phase Correlation, LUFS, and Crest Factor
        AudioSpectrumTapProcessor tap = new AudioSpectrumTapProcessor();
        tap.flush(44100, 2, C.ENCODING_PCM_16BIT);
        tap.setCaptureEnabled(true);
        tap.handleBuffer(input.duplicate());
        AudioSpectrumTapProcessor.SpectrumFrame frame = tap.pollTimeAlignedFrame(
                android.os.SystemClock.uptimeMillis() + 500L,
                new AudioSpectrumTapProcessor.SpectrumFrame()
        );
        assertTrue(frame != null);
        assertEquals(AudioSpectrumTapProcessor.XY_SAMPLE_COUNT, frame.xyLeft.length);
        assertEquals(AudioSpectrumTapProcessor.XY_SAMPLE_COUNT, frame.xyRight.length);
        assertTrue(frame.phaseCorrelation >= -1.0f && frame.phaseCorrelation <= 1.0f);
        assertTrue(frame.lufsShortTerm > -48.0f);
        assertTrue(frame.crestFactorDb >= 0.0f);

        // 3. Test AudioFxEngine A/B Snapshots & Studio Params isolation
        AudioFxEngine fx = AudioFxEngine.getInstance();
        fx.init(context);
        fx.switchToSnapshot(0);
        fx.setTubeEnabled(false);
        fx.setDjFilterPercent(0);
        fx.saveSnapshot(0);

        fx.switchToSnapshot(1);
        assertEquals(1, fx.getActiveSnapshotSlot());
        assertTrue(fx.isTubeEnabled()); // Slot B seeded with Studio Warmth preset
        fx.setDjFilterPercent(45);

        fx.switchToSnapshot(0);
        assertEquals(0, fx.getActiveSnapshotSlot());
        assertFalse(fx.isTubeEnabled());
        assertEquals(0, fx.getDjFilterPercent());

        // 4. Test Per-Track Resume Playback (save, restore, auto-clear near start/end, toggle disable)
        AudioLibraryRepository libRepo = new AudioLibraryRepository(context);
        libRepo.setAutoResumeEnabled(true);
        String testTrackId = "track_resume_unit_test_1";
        libRepo.saveTrackResumePosition(testTrackId, 45000L, 180000L);
        assertEquals(45000L, libRepo.getTrackResumePosition(testTrackId));

        libRepo.setAutoResumeEnabled(false);
        assertEquals(0L, libRepo.getTrackResumePosition(testTrackId));

        libRepo.setAutoResumeEnabled(true);
        assertEquals(45000L, libRepo.getTrackResumePosition(testTrackId));

        // Near start (< 2500ms) clears resume
        libRepo.saveTrackResumePosition(testTrackId, 1200L, 180000L);
        assertEquals(0L, libRepo.getTrackResumePosition(testTrackId));

        // Near end (>= duration - 2500ms) clears resume
        libRepo.saveTrackResumePosition(testTrackId, 60000L, 180000L);
        assertEquals(60000L, libRepo.getTrackResumePosition(testTrackId));
        libRepo.saveTrackResumePosition(testTrackId, 178500L, 180000L);
        assertEquals(0L, libRepo.getTrackResumePosition(testTrackId));

        // 5. Test YoutubeRepository URL extraction & Queue/Resume persistence
        assertEquals("dQw4w9WgXcQ", YoutubeRepository.extractVideoId("https://www.youtube.com/watch?v=dQw4w9WgXcQ"));
        assertEquals("dQw4w9WgXcQ", YoutubeRepository.extractVideoId("https://youtu.be/dQw4w9WgXcQ?t=42"));
        assertEquals("dQw4w9WgXcQ", YoutubeRepository.extractVideoId("https://music.youtube.com/watch?v=dQw4w9WgXcQ&list=RD123"));
        assertEquals("dQw4w9WgXcQ", YoutubeRepository.extractVideoId("https://youtube.com/shorts/dQw4w9WgXcQ"));
        assertEquals("dQw4w9WgXcQ", YoutubeRepository.extractVideoId("dQw4w9WgXcQ"));

        YoutubeRepository ytRepo = new YoutubeRepository(context);
        YoutubeTrack added = ytRepo.addOrUpdateTrack("dQw4w9WgXcQ", "Test Track", "Test Channel");
        assertTrue(added != null);
        ytRepo.updateResumePosition("dQw4w9WgXcQ", 78L, 212L);
        assertEquals(78L, ytRepo.getSavedQueue().get(0).getLastPositionSec());

        // 6. Test ProStudioRackView measure & draw across all 3 tabs
        ProStudioRackView rack = new ProStudioRackView(context);
        rack.syncState(false, 0, true, 65, 40, true, true, 50, 35, 25, -25, 1.05f, 1.0f, true, 12000L, 42000L, NeonDspAudioProcessor.PAD_VOCAL_FOCUS);
        rack.updateLiveTelemetry(frame.xyLeft, frame.xyRight, frame.phaseCorrelation, frame.lufsShortTerm, frame.crestFactorDb, 0.72f, 0.65f, shapedCurve);
        rack.measure(
                View.MeasureSpec.makeMeasureSpec(900, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(540, View.MeasureSpec.EXACTLY)
        );
        rack.layout(0, 0, 900, 540);
        for (int tab = 0; tab <= 2; tab++) {
            rack.setActiveTab(tab);
            Bitmap bmp = Bitmap.createBitmap(900, 540, Bitmap.Config.ARGB_8888);
            rack.draw(new Canvas(bmp));
            bmp.recycle();
        }

        // 7. Verify MainActivity wiring for YouTube Music Deck and ProStudioRackView
        UiStylePreferences prefs = new UiStylePreferences(context);
        prefs.setVisualMetersEnabled(true);
        prefs.setVisualMeterMode(UiStylePreferences.VisualMeterMode.DISCO_VU);
        MainActivity activity = Robolectric.buildActivity(MainActivity.class).create().get();
        View ytBtn = activity.findViewById(R.id.btn_youtube_music);
        View rackView = activity.findViewById(R.id.pro_studio_rack_view);
        assertTrue(ytBtn != null && rackView != null);
        assertEquals(View.VISIBLE, rackView.getVisibility());
    }

    @Test
    public void advancedStudioMastering3dStageAndOptimization_workAccurately() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();

        // 1. Karaoke Vocal Remover preserves <135Hz center sub-bass while cancelling 1kHz center vocal
        NeonDspAudioProcessor dsp = new NeonDspAudioProcessor();
        dsp.configure(new AudioProcessor.AudioFormat(44100, 2, C.ENCODING_PCM_16BIT));
        dsp.updateParams(true, new float[10], 100, 0, 0, 0, false);
        dsp.updateStudioParams(false, false, 0, 0, false, false, 40, 30, 20, 0, NeonDspAudioProcessor.PAD_NONE);
        dsp.updateAdvancedMasteringParams(false, 0, 0, 100, false, 0, 120f);
        dsp.flush();

        int frames = 2048;
        ByteBuffer bassMono = ByteBuffer.allocateDirect(frames * 4).order(ByteOrder.LITTLE_ENDIAN);
        ByteBuffer vocalMono = ByteBuffer.allocateDirect(frames * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < frames; i++) {
            short low = (short) (Math.sin(2.0 * Math.PI * 65.0 * i / 44100.0) * 14000.0);
            short mid = (short) (Math.sin(2.0 * Math.PI * 1000.0 * i / 44100.0) * 14000.0);
            bassMono.putShort(low).putShort(low);
            vocalMono.putShort(mid).putShort(mid);
        }
        bassMono.flip();
        vocalMono.flip();

        dsp.queueInput(bassMono);
        ByteBuffer bassOut = dsp.getOutput().order(ByteOrder.LITTLE_ENDIAN);
        double bassRms = computeBufferRmsAfterHalf(bassOut, frames);

        dsp.flush();
        dsp.queueInput(vocalMono);
        ByteBuffer vocalOut = dsp.getOutput().order(ByteOrder.LITTLE_ENDIAN);
        double vocalRms = computeBufferRmsAfterHalf(vocalOut, frames);

        assertTrue("Karaoke remover should preserve 65Hz sub-bass (" + bassRms + ") vs 1kHz center vocal (" + vocalRms + ")",
                bassRms > vocalRms * 3.0);

        // 2. 3-Band Multiband Compressor + De-Esser + Harman Target + BPM Stutter
        dsp.flush();
        dsp.updateAdvancedMasteringParams(true, 90, 85, 0, true, 2, 128f);
        ByteBuffer hotMix = ByteBuffer.allocateDirect(frames * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < frames; i++) {
            double s = Math.sin(2.0 * Math.PI * 80.0 * i / 44100.0) * 0.45
                    + Math.sin(2.0 * Math.PI * 1200.0 * i / 44100.0) * 0.45
                    + Math.sin(2.0 * Math.PI * 6200.0 * i / 44100.0) * 0.45;
            short pcm = (short) (s * 24000.0);
            hotMix.putShort(pcm).putShort(pcm);
        }
        hotMix.flip();
        dsp.queueInput(hotMix);
        ByteBuffer mbOut = dsp.getOutput().order(ByteOrder.LITTLE_ENDIAN);
        assertTrue(mbOut.remaining() > 0);
        assertTrue(dsp.getLiveMbLowGrDb() >= 0f && dsp.getLiveMbMidGrDb() >= 0f && dsp.getLiveMbHighGrDb() >= 0f);

        float[] harmanCurve = new float[48];
        dsp.computeFrequencyResponseDb(harmanCurve, 31f, 16000f);
        assertTrue("Harman target should shape sub-bass/upper-mids smoothly",
                Math.abs(harmanCurve[1] - harmanCurve[20]) > 0.1f);

        // 3. Dominant Note/Hz (440Hz -> A4), BPM, Chroma Key & 10-band RMS telemetry
        AudioSpectrumTapProcessor tap = new AudioSpectrumTapProcessor();
        tap.setCaptureEnabled(true);
        tap.flush(44100, 2, C.ENCODING_PCM_16BIT);
        ByteBuffer a4Tone = ByteBuffer.allocate(4096 * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < 4096; i++) {
            short pcm = (short) (Math.sin(2.0 * Math.PI * 440.0 * i / 44100.0) * 20000.0);
            a4Tone.putShort(pcm).putShort(pcm);
        }
        a4Tone.flip();
        tap.handleBuffer(a4Tone);
        AudioSpectrumTapProcessor.SpectrumFrame frame = tap.pollTimeAlignedFrame(
                android.os.SystemClock.uptimeMillis() + 600L,
                new AudioSpectrumTapProcessor.SpectrumFrame()
        );
        assertTrue(frame != null);
        assertEquals(10, frame.band10Db.length);
        assertTrue("Dominant freq should detect near 440Hz, was " + frame.dominantFreqHz,
                Math.abs(frame.dominantFreqHz - 440f) < 60f);
        assertTrue("Dominant note should be A4, was " + frame.dominantNoteName,
                frame.dominantNoteName != null && frame.dominantNoteName.startsWith("A"));
        assertTrue(frame.liveBpm >= 60f && frame.liveBpm <= 190f);

        // 4. AudioFxEngine Reference Match EQ reversible toggle, Kaoss XY & Snapshot persistence
        AudioFxEngine fx = AudioFxEngine.getInstance();
        fx.init(context);
        fx.applyPreset(EqPreset.ROCK);
        float[] rockGains = fx.getUiBandGainsDb();
        fx.captureReferenceProfile();
        assertTrue(fx.hasRefProfile());
        fx.setRefMatchEnabled(true);
        assertTrue(fx.isRefMatchEnabled());
        fx.setRefMatchEnabled(false);
        assertFalse(fx.isRefMatchEnabled());
        assertEquals(EqPreset.ROCK, fx.getCurrentPreset());
        assertEquals(rockGains[0], fx.getUiBandGainsDb()[0], 0.01f);

        fx.setKaossXy(-65, 55, true);
        assertEquals(-65, fx.getDjFilterPercent());
        assertEquals(55, fx.getReverbMixPercent());
        assertTrue(fx.isReverbEnabled());

        fx.switchToSnapshot(0);
        fx.setMultibandEnabled(true);
        fx.setMultibandDrivePercent(72);
        fx.setDeEsserPercent(64);
        fx.setVocalRemoverPercent(50);
        fx.setHarmanTargetEnabled(true);
        fx.saveSnapshot(0);

        fx.switchToSnapshot(1);
        fx.setMultibandEnabled(false);
        fx.setHarmanTargetEnabled(false);
        fx.switchToSnapshot(0);
        assertTrue(fx.isMultibandEnabled());
        assertEquals(72, fx.getMultibandDrivePercent());
        assertEquals(64, fx.getDeEsserPercent());
        assertEquals(50, fx.getVocalRemoverPercent());
        assertTrue(fx.isHarmanTargetEnabled());

        // 5. DeviceResourceOptimizer adaptive scaling & ProStudioRackView 5-tab draw + GyroParticleStageView
        DeviceResourceOptimizer opt = DeviceResourceOptimizer.getInstance(context);
        opt.setMode(DeviceResourceOptimizer.OptimizationMode.ECO_BATTERY);
        assertFalse(opt.shouldEnableStageGyroscope());
        assertTrue(opt.getWaterfallHistoryRows() <= 16);
        assertTrue(opt.getStageParticleCount() <= 10);
        opt.setMode(DeviceResourceOptimizer.OptimizationMode.PERFORMANCE);
        assertTrue(opt.shouldEnableStageGyroscope());
        assertTrue(opt.getStageParticleCount() >= 20);
        opt.setMode(DeviceResourceOptimizer.OptimizationMode.AUTO);

        ProStudioRackView rack = new ProStudioRackView(context);
        rack.setTargetFps(45);
        rack.setWaterfallHistoryRows(24);
        rack.syncState(false, 0, true, 60, 40, true, true, 50, 35, 30, -30, 1.0f, 1.0f, true, 5000L, 15000L, NeonDspAudioProcessor.PAD_VOCAL_FOCUS);
        rack.syncAdvancedState(true, 70, 55, 65, true, true, true, 2);
        rack.updateLiveTelemetry(
                frame.xyLeft, frame.xyRight, frame.phaseCorrelation, frame.lufsShortTerm, frame.crestFactorDb,
                0.7f, 0.8f, harmanCurve, frame.bars, 128f, "Am", "8A", 440f, "A4", 2.1f, 3.4f, 1.5f
        );
        rack.measure(
                View.MeasureSpec.makeMeasureSpec(960, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY)
        );
        rack.layout(0, 0, 960, 600);
        for (int tab = 0; tab <= 4; tab++) {
            rack.setActiveTab(tab);
            Bitmap bmp = Bitmap.createBitmap(960, 600, Bitmap.Config.ARGB_8888);
            rack.draw(new Canvas(bmp));
            bmp.recycle();
        }

        com.example.ui.views.GyroParticleStageView stageView = new com.example.ui.views.GyroParticleStageView(context);
        stageView.setMaxParticleCount(20);
        stageView.setTargetFps(30);
        stageView.setPaletteColors(NeonPalette.DEFAULT.primaryNeon, NeonPalette.DEFAULT.secondaryNeon, NeonPalette.DEFAULT.tertiaryGlow);
        stageView.setGyroTilt(0.45f, -0.35f);
        stageView.updateAudioTelemetry(0.8f, 0.9f, 128f);
        stageView.measure(
                View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1280, View.MeasureSpec.EXACTLY)
        );
        stageView.layout(0, 0, 720, 1280);
        Bitmap stageBmp = Bitmap.createBitmap(720, 1280, Bitmap.Config.ARGB_8888);
        stageView.draw(new Canvas(stageBmp));
        stageBmp.recycle();
    }

    @Test
    public void purifier4kAntiCrackleBassPowerAndQuickModes_workEndToEnd() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();

        // 1. Extreme overload anti-crackle limiter protection: 200% bass + 15dB EQ + 12dB purify + 6dB gain
        NeonDspAudioProcessor dsp = new NeonDspAudioProcessor();
        dsp.configure(new AudioProcessor.AudioFormat(44100, 2, C.ENCODING_PCM_16BIT));
        float[] extremeGains = new float[]{15f, 15f, 12f, 6f, 2f, 0f, 3f, 5f, 8f, 10f};
        dsp.updateParams(true, extremeGains, 200, 35, 30, 0, false);
        dsp.updatePurificationParams(6.0f, 6.0f, 4.0f, -18.0f, false, true, 12.0f, 6.0f, 130, -1.0f, true, 20, 22000);
        dsp.updatePurifier4kAndProtection(true, true, true);
        dsp.flush();

        int frames = 2048;
        ByteBuffer extremeInput = ByteBuffer.allocateDirect(frames * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < frames; i++) {
            short pcm = (short) (Math.sin(2.0 * Math.PI * 75.0 * i / 44100.0) * 28000.0
                    + Math.sin(2.0 * Math.PI * 3000.0 * i / 44100.0) * 4000.0);
            extremeInput.putShort(pcm).putShort(pcm);
        }
        extremeInput.flip();
        dsp.queueInput(extremeInput);
        ByteBuffer safeOut = dsp.getOutput().order(ByteOrder.LITTLE_ENDIAN);
        float maxSample = 0f;
        while (safeOut.remaining() >= 4) {
            float l = Math.abs(safeOut.getShort() / 32768f);
            float r = Math.abs(safeOut.getShort() / 32768f);
            if (l > maxSample) maxSample = l;
            if (r > maxSample) maxSample = r;
        }
        assertTrue("Anti-crackle limiter must prevent overflow/wrap and bound peaks <= 0.96, was " + maxSample,
                maxSample > 0.25f && maxSample <= 0.96f);
        assertTrue("Anti-crackle limiter should report active gain reduction dB", dsp.getLiveLimitingDb() > 0.1f);

        // 2. Verify 4K Purifier curve difference & Speaker 52Hz Subsonic Guard vs Headphone Deep Extension
        dsp.updateParams(true, new float[10], 100, 0, 0, 0, false);
        dsp.updatePurificationParams(0f, 0f, 1.0f, 0f, false, false, 0f, 0f, 100, 0.0f, false, 20, 22000);
        dsp.updatePurifier4kAndProtection(true, true, true); // speaker mode
        float[] speakerCurve = new float[48];
        dsp.computeFrequencyResponseDb(speakerCurve, 31f, 16000f);

        dsp.updatePurifier4kAndProtection(true, true, false); // headphone mode
        float[] headphoneCurve = new float[48];
        dsp.computeFrequencyResponseDb(headphoneCurve, 31f, 16000f);
        assertTrue("Speaker rattle guard should attenuate 31Hz sub-bass relative to headphone 4K mode",
                speakerCurve[0] < headphoneCurve[0] - 0.5f);
        assertTrue("4K air shelf should boost 16kHz sparkle cleanly", headphoneCurve[47] > 0.5f);

        // 3. Verify AudioSpectrumTapProcessor Bass Power % and dB measurement
        AudioSpectrumTapProcessor tap = new AudioSpectrumTapProcessor();
        tap.setCaptureEnabled(true);
        tap.flush(44100, 2, C.ENCODING_PCM_16BIT);
        ByteBuffer subBass = ByteBuffer.allocate(2048 * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < 2048; i++) {
            short pcm = (short) (Math.sin(2.0 * Math.PI * 65.0 * i / 44100.0) * 26000.0);
            subBass.putShort(pcm).putShort(pcm);
        }
        subBass.flip();
        tap.handleBuffer(subBass);
        AudioSpectrumTapProcessor.SpectrumFrame bassFrame = tap.pollTimeAlignedFrame(
                android.os.SystemClock.uptimeMillis() + 500L,
                new AudioSpectrumTapProcessor.SpectrumFrame());
        assertTrue(bassFrame != null);
        assertTrue("Expected high live Bass Power % on 65Hz sub-bass, was " + bassFrame.bassPowerPercent,
                bassFrame.bassPowerPercent >= 45);
        assertTrue("Expected elevated Bass Power dB on 65Hz sub-bass, was " + bassFrame.bassPowerDb,
                bassFrame.bassPowerDb > -18.0f);

        // 4. Verify AudioFxEngine 4K, Anti-Crackle, and Quick Modes persistence
        AudioFxEngine fx = AudioFxEngine.getInstance();
        fx.init(context);
        fx.setUltraHd4kEnabled(true);
        fx.setAntiCrackleEnabled(true);
        assertTrue(fx.isUltraHd4kEnabled());
        assertTrue(fx.isAntiCrackleEnabled());

        fx.applyQuickMode(AudioFxEngine.QUICK_BASS_PRO);
        assertEquals(AudioFxEngine.QUICK_BASS_PRO, fx.getActiveQuickMode());
        assertTrue(fx.getBassBoostPercent() >= 150);
        assertTrue(fx.isAntiCrackleEnabled());

        fx.applyQuickMode(AudioFxEngine.QUICK_CLEAN_4K);
        assertEquals(AudioFxEngine.QUICK_CLEAN_4K, fx.getActiveQuickMode());
        assertTrue(fx.isUltraHd4kEnabled());
        assertTrue(fx.getTrebleClarityDb() >= 3.0f);

        fx.applyQuickMode(AudioFxEngine.QUICK_CLUB);
        assertEquals(AudioFxEngine.QUICK_CLUB, fx.getActiveQuickMode());

        // 5. Render DiscoVuGlassView & PurificationSuiteView with quick modes, 4K badges, & Bass Power meter
        DiscoVuGlassView vu = new DiscoVuGlassView(context);
        vu.setPlaying(true);
        vu.setQuickModeState(DiscoVuGlassView.QUICK_CLEAN_4K, 128.0f);
        vu.updateMeterTelemetry(-4.5f, -3.2f, false, 24);
        vu.updateStereoSpectrum(bassFrame.bars, 0.85f, 0.82f, 0.80f, 0.90f, 44100);
        vu.measure(
                View.MeasureSpec.makeMeasureSpec(900, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(640, View.MeasureSpec.EXACTLY)
        );
        vu.layout(0, 0, 900, 640);
        Bitmap vuBmp = Bitmap.createBitmap(900, 640, Bitmap.Config.ARGB_8888);
        vu.draw(new Canvas(vuBmp));
        vuBmp.recycle();

        PurificationSuiteView suite = new PurificationSuiteView(context);
        suite.setPlaying(true);
        suite.syncFromEngine(new float[]{6f, 2f, 0f, 2f, 4f}, 3.2f, -12f, true, true, 4.5f, 3.5f, 125, -1.0f, true, 55, 20000, 24);
        suite.syncExtendedPurifierState(true, true, true);
        suite.updateLiveStatus(2.4f, 1.8f, false, 24, bassFrame.bassPowerPercent, bassFrame.bassPowerDb, bassFrame.bars);
        suite.measure(
                View.MeasureSpec.makeMeasureSpec(900, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY)
        );
        suite.layout(0, 0, 900, 720);
        Bitmap suiteBmp = Bitmap.createBitmap(900, 720, Bitmap.Config.ARGB_8888);
        suite.draw(new Canvas(suiteBmp));
        suiteBmp.recycle();
    }

    @Test
    public void studioLedButtonHelper_illuminatesVividGreenRedYellowButtons() {
        Context context = ApplicationProvider.getApplicationContext();
        com.example.ui.views.StudioLedButtonHelper helper =
                new com.example.ui.views.StudioLedButtonHelper(2.0f);
        Bitmap bmp = Bitmap.createBitmap(320, 160, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        c.drawColor(0xFF060913);

        android.graphics.RectF greenRect = new android.graphics.RectF(20f, 20f, 100f, 70f);
        android.graphics.RectF redRect = new android.graphics.RectF(120f, 20f, 200f, 70f);
        android.graphics.RectF yellowRect = new android.graphics.RectF(220f, 20f, 300f, 70f);
        android.graphics.RectF offRect = new android.graphics.RectF(20f, 95f, 100f, 145f);

        helper.drawTwoLineButton(c, greenRect, 14f, 2.0f, "CLEAN", "4K HD", 16f, 12f,
                com.example.ui.views.StudioLedButtonHelper.STUDIO_GREEN, true, true, 0.8f, 0.5f);
        helper.drawTwoLineButton(c, redRect, 14f, 2.0f, "BASS", "PRO", 16f, 12f,
                com.example.ui.views.StudioLedButtonHelper.STUDIO_RED, true, true, 0.8f, 0.5f);
        helper.drawSingleLineButton(c, yellowRect, 14f, 2.0f, "4K HD ON", 14f,
                com.example.ui.views.StudioLedButtonHelper.STUDIO_YELLOW, true, true,
                com.example.ui.views.StudioLedButtonHelper.STUDIO_YELLOW, 0.8f, 0.5f);
        helper.drawSingleLineButton(c, offRect, 14f, 2.0f, "4K OFF", 14f,
                com.example.ui.views.StudioLedButtonHelper.STUDIO_YELLOW, false, true,
                com.example.ui.views.StudioLedButtonHelper.STUDIO_YELLOW, 0f, 0f);
        bmp.recycle();

        // Verify color math utilities produce saturated bright cores
        int litCore = com.example.ui.views.StudioLedButtonHelper.lerpColor(
                com.example.ui.views.StudioLedButtonHelper.STUDIO_GREEN, 0xFFFFFFFF, 0.45f);
        assertTrue(Color.green(litCore) > 235 && Color.red(litCore) > 100);
        int spillAlpha = com.example.ui.views.StudioLedButtonHelper.withAlpha(
                com.example.ui.views.StudioLedButtonHelper.STUDIO_RED, 0x99);
        assertEquals(0x99, Color.alpha(spillAlpha));

        // Verify real tap interaction lights up quick modes in DiscoVuGlassView
        final int[] tappedQuickMode = {-1};
        DiscoVuGlassView vu = new DiscoVuGlassView(context);
        vu.setOnQuickModeClickListener(mode -> tappedQuickMode[0] = mode);
        vu.measure(
                View.MeasureSpec.makeMeasureSpec(900, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(640, View.MeasureSpec.EXACTLY));
        vu.layout(0, 0, 900, 640);
        Bitmap vuBmp = Bitmap.createBitmap(900, 640, Bitmap.Config.ARGB_8888);
        vu.draw(new Canvas(vuBmp));
        vu.onTouchEvent(android.view.MotionEvent.obtain(0L, 0L,
                android.view.MotionEvent.ACTION_DOWN, 375f, 550f, 0));
        assertEquals(DiscoVuGlassView.QUICK_BASS_PRO, tappedQuickMode[0]);
        vu.onTouchEvent(android.view.MotionEvent.obtain(0L, 0L,
                android.view.MotionEvent.ACTION_DOWN, 450f, 550f, 0));
        assertEquals(DiscoVuGlassView.QUICK_CLEAN_4K, tappedQuickMode[0]);
        vu.onTouchEvent(android.view.MotionEvent.obtain(0L, 0L,
                android.view.MotionEvent.ACTION_DOWN, 525f, 550f, 0));
        assertEquals(DiscoVuGlassView.QUICK_CLUB, tappedQuickMode[0]);
        vu.setQuickModeState(DiscoVuGlassView.QUICK_BASS_PRO, 128f);
        vu.draw(new Canvas(vuBmp));
        vu.setQuickModeState(DiscoVuGlassView.QUICK_CLUB, 128f);
        vu.draw(new Canvas(vuBmp));
        vuBmp.recycle();

        // Verify real tap toggles in PurificationSuiteView (NO-CRACKLE & 4K HD)
        final boolean[] toggled = {false, false};
        PurificationSuiteView suite = new PurificationSuiteView(context);
        suite.setSuiteListener(new PurificationSuiteView.SuiteListener() {
            @Override public void onFaderChanged(int groupIndex, float gainDb) {}
            @Override public void onCompressorChanged(float ratio, float thresholdDb) {}
            @Override public void onNoiseGateToggled(boolean enabled) {}
            @Override public void onNoiseReductionToggled(boolean enabled) {}
            @Override public void onPurificationBassChanged(float boostDb) {}
            @Override public void onTrebleClarityChanged(float clarityDb) {}
            @Override public void onStereoWidthChanged(int widthPercent) {}
            @Override public void onLimiterChanged(float ceilingDb) {}
            @Override public void onAutoGainToggled(boolean enabled) {}
            @Override public void onHpfChanged(int hpfHz) {}
            @Override public void onLpfChanged(int lpfHz) {}
            @Override public void onUltraHd4kToggled(boolean enabled) { toggled[0] = true; }
            @Override public void onAntiCrackleToggled(boolean enabled) { toggled[1] = true; }
        });
        suite.measure(
                View.MeasureSpec.makeMeasureSpec(900, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY));
        suite.layout(0, 0, 900, 720);
        Bitmap suiteBmp = Bitmap.createBitmap(900, 720, Bitmap.Config.ARGB_8888);
        suite.draw(new Canvas(suiteBmp));
        suite.onTouchEvent(android.view.MotionEvent.obtain(0L, 0L,
                android.view.MotionEvent.ACTION_DOWN, 44f, 18f, 0));
        suite.onTouchEvent(android.view.MotionEvent.obtain(0L, 0L,
                android.view.MotionEvent.ACTION_DOWN, 856f, 18f, 0));
        assertTrue(toggled[0] && toggled[1]);
        suite.draw(new Canvas(suiteBmp));
        suiteBmp.recycle();
    }

    private static double computeBufferRmsAfterHalf(ByteBuffer buf, int frames) {
        int totalSamples = buf.remaining() / 2;
        int startSample = (frames / 2) * 2;
        double sumSq = 0.0;
        int count = 0;
        for (int s = 0; s < totalSamples; s++) {
            short v = buf.getShort();
            if (s >= startSample) {
                double n = v / 32768.0;
                sumSq += n * n;
                count++;
            }
        }
        return Math.sqrt(sumSq / Math.max(1, count));
    }
}
