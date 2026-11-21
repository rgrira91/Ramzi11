package com.example.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LrcParser {
    private static final Pattern LRC_LINE_PATTERN =
            Pattern.compile("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?](.*)");

    public static class LyricLine {
        public final long timeMs;
        public final String text;

        public LyricLine(long timeMs, String text) {
            this.timeMs = timeMs;
            this.text = text;
        }
    }

    private LrcParser() {}

    public static List<LyricLine> parse(String rawText) {
        List<LyricLine> lines = new ArrayList<>();
        if (rawText == null || rawText.trim().isEmpty()) {
            return lines;
        }
        String[] rawLines = rawText.split("\\r?\\n");
        boolean anyTimed = false;

        for (String raw : rawLines) {
            String trimmed = raw.trim();
            if (trimmed.isEmpty()) continue;

            Matcher matcher = LRC_LINE_PATTERN.matcher(trimmed);
            List<Long> timestamps = new ArrayList<>();
            int lastEnd = 0;
            while (matcher.find()) {
                anyTimed = true;
                long min = parseLongSafe(matcher.group(1));
                long sec = parseLongSafe(matcher.group(2));
                String fracStr = matcher.group(3);
                long ms = 0;
                if (fracStr != null) {
                    long val = parseLongSafe(fracStr);
                    if (fracStr.length() == 1) ms = val * 100L;
                    else if (fracStr.length() == 2) ms = val * 10L;
                    else ms = val;
                }
                timestamps.add(min * 60_000L + sec * 1000L + ms);
                lastEnd = matcher.end();
            }

            if (!timestamps.isEmpty()) {
                // Capture remaining lyric text after the last timestamp tag
                int closeBracket = trimmed.lastIndexOf(']');
                String lyricText = closeBracket >= 0 && closeBracket + 1 < trimmed.length()
                        ? trimmed.substring(closeBracket + 1).trim()
                        : "";
                if (!lyricText.isEmpty()) {
                    for (long ts : timestamps) {
                        lines.add(new LyricLine(ts, lyricText));
                    }
                }
            } else if (!trimmed.startsWith("[ti:") && !trimmed.startsWith("[ar:")
                    && !trimmed.startsWith("[al:") && !trimmed.startsWith("[by:")) {
                lines.add(new LyricLine(-1L, trimmed));
            }
        }

        if (anyTimed) {
            Collections.sort(lines, Comparator.comparingLong(l -> l.timeMs));
        }
        return lines;
    }

    public static int findActiveLineIndex(List<LyricLine> lines, long positionMs) {
        if (lines == null || lines.isEmpty()) return -1;
        int active = -1;
        for (int i = 0; i < lines.size(); i++) {
            long ts = lines.get(i).timeMs;
            if (ts < 0) return -1;
            if (ts <= positionMs) {
                active = i;
            } else {
                break;
            }
        }
        return active;
    }

    private static long parseLongSafe(String s) {
        if (s == null) return 0L;
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
