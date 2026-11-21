package com.example;

import com.example.audio.AudioFxEngine;
import com.example.model.EqPreset;
import com.example.util.LrcParser;
import com.example.util.TimeFormatter;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class AudioPlayerUtilsTest {
    @Test
    public void formatDuration_formatsMinutesAndHours() {
        assertEquals("0:00", TimeFormatter.formatDuration(0L));
        assertEquals("1:37", TimeFormatter.formatDuration(97_000L));
        assertEquals("4:28", TimeFormatter.formatDuration(268_000L));
        assertEquals("1:02:05", TimeFormatter.formatDuration(3_725_000L));
    }

    @Test
    public void eqPresets_haveTenBands() {
        for (EqPreset preset : EqPreset.values()) {
            assertEquals(AudioFxEngine.UI_BAND_COUNT, preset.getGainsDbCopy().length);
        }
        assertEquals(EqPreset.ELECTRONIC, EqPreset.fromKey("electronic"));
        assertEquals(EqPreset.BASS_BOOST, EqPreset.fromKey("bass_boost"));
    }

    @Test
    public void lrcParser_parsesAndFindsActiveLine() {
        String sample = "[00:05.50]Neon lights glow\n[00:12.00]Electric dreams tonight\n[00:25.25]Wave across the sky";
        List<LrcParser.LyricLine> lines = LrcParser.parse(sample);
        assertEquals(3, lines.size());
        assertEquals(5_500L, lines.get(0).timeMs);
        assertEquals("Neon lights glow", lines.get(0).text);
        assertEquals(-1, LrcParser.findActiveLineIndex(lines, 3_000L));
        assertEquals(0, LrcParser.findActiveLineIndex(lines, 7_000L));
        assertEquals(1, LrcParser.findActiveLineIndex(lines, 18_000L));
        assertEquals(2, LrcParser.findActiveLineIndex(lines, 30_000L));
    }
}
