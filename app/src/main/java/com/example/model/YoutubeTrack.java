package com.example.model;

/**
 * Represents a YouTube music video/track played via official YouTube IFrame Embed API.
 */
public class YoutubeTrack {
    private final String videoId;
    private String title;
    private String channel;
    private long lastPositionSec;
    private long durationSec;
    private final long addedAtMs;

    public YoutubeTrack(
            String videoId,
            String title,
            String channel,
            long lastPositionSec,
            long durationSec,
            long addedAtMs) {
        this.videoId = videoId;
        this.title = title != null && !title.trim().isEmpty() ? title.trim() : ("YouTube • " + videoId);
        this.channel = channel != null && !channel.trim().isEmpty() ? channel.trim() : "YouTube Music";
        this.lastPositionSec = Math.max(0L, lastPositionSec);
        this.durationSec = Math.max(0L, durationSec);
        this.addedAtMs = addedAtMs;
    }

    public String getVideoId() {
        return videoId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        if (title != null && !title.trim().isEmpty()) {
            this.title = title.trim();
        }
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        if (channel != null && !channel.trim().isEmpty()) {
            this.channel = channel.trim();
        }
    }

    public long getLastPositionSec() {
        return lastPositionSec;
    }

    public void setLastPositionSec(long lastPositionSec) {
        this.lastPositionSec = Math.max(0L, lastPositionSec);
    }

    public long getDurationSec() {
        return durationSec;
    }

    public void setDurationSec(long durationSec) {
        this.durationSec = Math.max(0L, durationSec);
    }

    public long getAddedAtMs() {
        return addedAtMs;
    }
}
