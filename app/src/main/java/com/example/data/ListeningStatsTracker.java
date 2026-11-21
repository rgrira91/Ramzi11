package com.example.data;

import android.content.Context;
import android.content.SharedPreferences;
import com.example.model.Track;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Tracks local playback statistics (listening minutes, top track, top artist, and codec/format distribution).
 */
public final class ListeningStatsTracker {
    private static final String PREFS_NAME = "neon_wave_listening_stats";
    private static final String KEY_TOTAL_SECONDS = "total_listen_sec";
    private static final String KEY_TOTAL_PLAYS = "total_plays";
    private static final String KEY_TRACK_PLAYS_PREFIX = "track_plays_";
    private static final String KEY_TRACK_TITLE_PREFIX = "track_title_";
    private static final String KEY_ARTIST_PLAYS_PREFIX = "artist_plays_";
    private static final String KEY_FORMAT_PLAYS_PREFIX = "format_plays_";

    public static final class InsightsSummary {
        public final long totalListeningSeconds;
        public final int totalPlays;
        public final String topTrackTitle;
        public final int topTrackPlays;
        public final String topArtistName;
        public final int topArtistPlays;
        public final Map<String, Integer> formatCounts;

        public InsightsSummary(
                long totalListeningSeconds,
                int totalPlays,
                String topTrackTitle,
                int topTrackPlays,
                String topArtistName,
                int topArtistPlays,
                Map<String, Integer> formatCounts) {
            this.totalListeningSeconds = totalListeningSeconds;
            this.totalPlays = totalPlays;
            this.topTrackTitle = topTrackTitle;
            this.topTrackPlays = topTrackPlays;
            this.topArtistName = topArtistName;
            this.topArtistPlays = topArtistPlays;
            this.formatCounts = Collections.unmodifiableMap(formatCounts);
        }
    }

    private final SharedPreferences prefs;

    public ListeningStatsTracker(Context context) {
        this.prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public void recordTrackStarted(Track track) {
        if (track == null) return;
        String id = track.getId();
        String artist = track.getArtist() != null && !track.getArtist().trim().isEmpty()
                ? track.getArtist().trim()
                : "Unknown Artist";
        String format = track.getFormat() != null && !track.getFormat().trim().isEmpty()
                ? track.getFormat().trim().toUpperCase()
                : "AUDIO";

        SharedPreferences.Editor ed = prefs.edit();
        ed.putInt(KEY_TOTAL_PLAYS, prefs.getInt(KEY_TOTAL_PLAYS, 0) + 1);
        ed.putInt(KEY_TRACK_PLAYS_PREFIX + id, prefs.getInt(KEY_TRACK_PLAYS_PREFIX + id, 0) + 1);
        ed.putString(KEY_TRACK_TITLE_PREFIX + id, track.getTitle());
        ed.putInt(KEY_ARTIST_PLAYS_PREFIX + artist, prefs.getInt(KEY_ARTIST_PLAYS_PREFIX + artist, 0) + 1);
        ed.putInt(KEY_FORMAT_PLAYS_PREFIX + format, prefs.getInt(KEY_FORMAT_PLAYS_PREFIX + format, 0) + 1);
        ed.apply();
    }

    public void addListeningSeconds(long deltaSeconds) {
        if (deltaSeconds <= 0L) return;
        long total = prefs.getLong(KEY_TOTAL_SECONDS, 0L) + deltaSeconds;
        prefs.edit().putLong(KEY_TOTAL_SECONDS, total).apply();
    }

    public InsightsSummary buildSummary() {
        Map<String, ?> all = prefs.getAll();
        long totalSec = prefs.getLong(KEY_TOTAL_SECONDS, 0L);
        int totalPlays = prefs.getInt(KEY_TOTAL_PLAYS, 0);

        String topTrack = "-";
        int topTrackPlays = 0;
        String topArtist = "-";
        int topArtistPlays = 0;
        Map<String, Integer> formats = new LinkedHashMap<>();

        for (Map.Entry<String, ?> entry : all.entrySet()) {
            String k = entry.getKey();
            Object v = entry.getValue();
            if (!(v instanceof Integer)) continue;
            int count = (Integer) v;
            if (k.startsWith(KEY_TRACK_PLAYS_PREFIX)) {
                if (count > topTrackPlays) {
                    topTrackPlays = count;
                    String id = k.substring(KEY_TRACK_PLAYS_PREFIX.length());
                    topTrack = prefs.getString(KEY_TRACK_TITLE_PREFIX + id, id);
                }
            } else if (k.startsWith(KEY_ARTIST_PLAYS_PREFIX)) {
                if (count > topArtistPlays) {
                    topArtistPlays = count;
                    topArtist = k.substring(KEY_ARTIST_PLAYS_PREFIX.length());
                }
            } else if (k.startsWith(KEY_FORMAT_PLAYS_PREFIX)) {
                formats.put(k.substring(KEY_FORMAT_PLAYS_PREFIX.length()), count);
            }
        }

        return new InsightsSummary(
                totalSec,
                totalPlays,
                topTrack,
                topTrackPlays,
                topArtist,
                topArtistPlays,
                formats
        );
    }

    public void resetStats() {
        prefs.edit().clear().apply();
    }
}
