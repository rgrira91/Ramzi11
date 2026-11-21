package com.example.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import com.example.model.YoutubeTrack;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Manages parsing of YouTube URLs/IDs, persistence of the YouTube music queue,
 * and per-video playback resume positions in SharedPreferences.
 */
public class YoutubeRepository {
    private static final String PREFS_NAME = "neon_wave_youtube";
    private static final String KEY_QUEUE_JSON = "youtube_queue_json";
    private static final String KEY_LAST_VIDEO_ID = "youtube_last_video_id";

    private static final Pattern VIDEO_ID_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{11}$");
    private static final Pattern URL_EXTRACTOR_PATTERN = Pattern.compile(
            "(?:youtube\\.com/(?:watch\\?(?:.*&)?v=|embed/|shorts/|live/)|youtu\\.be/|music\\.youtube\\.com/watch\\?(?:.*&)?v=)([A-Za-z0-9_-]{11})",
            Pattern.CASE_INSENSITIVE
    );

    private final SharedPreferences prefs;

    public YoutubeRepository(Context context) {
        this.prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /**
     * Extracts an 11-char YouTube Video ID from standard, mobile, shorts, music.youtube, or youtu.be URLs.
     */
    public static String extractVideoId(String input) {
        if (input == null) return null;
        String trimmed = input.trim();
        if (trimmed.isEmpty()) return null;

        if (VIDEO_ID_PATTERN.matcher(trimmed).matches()) {
            return trimmed;
        }

        Matcher matcher = URL_EXTRACTOR_PATTERN.matcher(trimmed);
        if (matcher.find()) {
            String candidate = matcher.group(1);
            if (candidate != null && VIDEO_ID_PATTERN.matcher(candidate).matches()) {
                return candidate;
            }
        }

        try {
            Uri uri = Uri.parse(trimmed);
            String vParam = uri.getQueryParameter("v");
            if (vParam != null && VIDEO_ID_PATTERN.matcher(vParam).matches()) {
                return vParam;
            }
            List<String> segments = uri.getPathSegments();
            if (segments != null && !segments.isEmpty()) {
                String last = segments.get(segments.size() - 1);
                if (VIDEO_ID_PATTERN.matcher(last).matches()) {
                    return last;
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    public static String extractPlaylistId(String input) {
        if (input == null) return null;
        try {
            Uri uri = Uri.parse(input.trim());
            String list = uri.getQueryParameter("list");
            if (list != null && !list.trim().isEmpty()) {
                return list.trim();
            }
        } catch (Throwable ignored) {}
        return null;
    }

    public synchronized List<YoutubeTrack> getSavedQueue() {
        List<YoutubeTrack> list = new ArrayList<>();
        String raw = prefs.getString(KEY_QUEUE_JSON, "[]");
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                String id = o.optString("videoId", "");
                if (!VIDEO_ID_PATTERN.matcher(id).matches()) continue;
                list.add(new YoutubeTrack(
                        id,
                        o.optString("title", "YouTube • " + id),
                        o.optString("channel", "YouTube Music"),
                        o.optLong("lastPositionSec", 0L),
                        o.optLong("durationSec", 0L),
                        o.optLong("addedAtMs", System.currentTimeMillis())
                ));
            }
        } catch (Throwable ignored) {}
        return list;
    }

    public synchronized void saveQueue(List<YoutubeTrack> tracks) {
        JSONArray arr = new JSONArray();
        if (tracks != null) {
            for (YoutubeTrack t : tracks) {
                if (t == null || t.getVideoId() == null) continue;
                try {
                    JSONObject o = new JSONObject();
                    o.put("videoId", t.getVideoId());
                    o.put("title", t.getTitle());
                    o.put("channel", t.getChannel());
                    o.put("lastPositionSec", t.getLastPositionSec());
                    o.put("durationSec", t.getDurationSec());
                    o.put("addedAtMs", t.getAddedAtMs());
                    arr.put(o);
                } catch (Throwable ignored) {}
            }
        }
        prefs.edit().putString(KEY_QUEUE_JSON, arr.toString()).apply();
    }

    public synchronized YoutubeTrack addOrUpdateTrack(String videoId, String title, String channel) {
        if (videoId == null || !VIDEO_ID_PATTERN.matcher(videoId).matches()) return null;
        List<YoutubeTrack> queue = getSavedQueue();
        YoutubeTrack existing = null;
        for (YoutubeTrack t : queue) {
            if (videoId.equals(t.getVideoId())) {
                existing = t;
                break;
            }
        }
        if (existing != null) {
            if (title != null && !title.trim().isEmpty()) {
                existing.setTitle(title);
            }
            if (channel != null && !channel.trim().isEmpty()) {
                existing.setChannel(channel);
            }
            queue.remove(existing);
            queue.add(0, existing);
        } else {
            existing = new YoutubeTrack(videoId, title, channel, 0L, 0L, System.currentTimeMillis());
            queue.add(0, existing);
            while (queue.size() > 40) {
                queue.remove(queue.size() - 1);
            }
        }
        saveQueue(queue);
        prefs.edit().putString(KEY_LAST_VIDEO_ID, videoId).apply();
        return existing;
    }

    public synchronized void updateResumePosition(String videoId, long positionSec, long durationSec) {
        if (videoId == null) return;
        List<YoutubeTrack> queue = getSavedQueue();
        boolean changed = false;
        long sanitizedPos = Math.max(0L, positionSec);
        if (durationSec > 6L && sanitizedPos >= durationSec - 3L) {
            sanitizedPos = 0L;
        }
        for (YoutubeTrack t : queue) {
            if (videoId.equals(t.getVideoId())) {
                t.setLastPositionSec(sanitizedPos);
                if (durationSec > 0L) {
                    t.setDurationSec(durationSec);
                }
                changed = true;
                break;
            }
        }
        if (changed) {
            saveQueue(queue);
        }
    }

    public synchronized void removeTrack(String videoId) {
        if (videoId == null) return;
        List<YoutubeTrack> queue = getSavedQueue();
        for (int i = queue.size() - 1; i >= 0; i--) {
            if (videoId.equals(queue.get(i).getVideoId())) {
                queue.remove(i);
            }
        }
        saveQueue(queue);
    }

    public String getLastVideoId() {
        return prefs.getString(KEY_LAST_VIDEO_ID, null);
    }

    public void setLastVideoId(String videoId) {
        prefs.edit().putString(KEY_LAST_VIDEO_ID, videoId).apply();
    }
}
