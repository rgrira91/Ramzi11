package com.example.model;

import android.net.Uri;
import java.util.Objects;

public class Track {
    private final String id;
    private final Uri uri;
    private final String title;
    private final String artist;
    private final String album;
    private final long durationMs;
    private final String format;
    private final int bitrateKbps;
    private final int sampleRateHz;
    private final String mimeType;
    private final Uri albumArtUri;
    private final long sizeBytes;
    private final String folderName;
    private final long dateAddedSec;
    private final boolean isFromSaf;
    private boolean favorite;

    public Track(
            String id,
            Uri uri,
            String title,
            String artist,
            String album,
            long durationMs,
            String format,
            int bitrateKbps,
            int sampleRateHz,
            String mimeType,
            Uri albumArtUri,
            long sizeBytes,
            String folderName,
            long dateAddedSec,
            boolean isFromSaf,
            boolean favorite) {
        this.id = id;
        this.uri = uri;
        this.title = title;
        this.artist = artist;
        this.album = album;
        this.durationMs = durationMs;
        this.format = format;
        this.bitrateKbps = bitrateKbps;
        this.sampleRateHz = sampleRateHz;
        this.mimeType = mimeType;
        this.albumArtUri = albumArtUri;
        this.sizeBytes = sizeBytes;
        this.folderName = folderName != null && !folderName.trim().isEmpty() ? folderName : "Audio";
        this.dateAddedSec = dateAddedSec;
        this.isFromSaf = isFromSaf;
        this.favorite = favorite;
    }

    public String getId() { return id; }
    public Uri getUri() { return uri; }
    public String getTitle() { return title; }
    public String getArtist() { return artist; }
    public String getAlbum() { return album; }
    public long getDurationMs() { return durationMs; }
    public String getFormat() { return format; }
    public int getBitrateKbps() { return bitrateKbps; }
    public int getSampleRateHz() { return sampleRateHz; }
    public String getMimeType() { return mimeType; }
    public Uri getAlbumArtUri() { return albumArtUri; }
    public long getSizeBytes() { return sizeBytes; }
    public String getFolderName() { return folderName; }
    public long getDateAddedSec() { return dateAddedSec; }
    public boolean isFromSaf() { return isFromSaf; }
    public boolean isFavorite() { return favorite; }
    public void setFavorite(boolean favorite) { this.favorite = favorite; }

    public String getQualityBadgeText() {
        if ("FLAC".equalsIgnoreCase(format) || "WAV".equalsIgnoreCase(format) || "ALAC".equalsIgnoreCase(format)) {
            return "Hi-Fi " + format;
        }
        return format != null && !format.isEmpty() ? format : "Audio";
    }

    public String getBitrateBadgeText() {
        if (bitrateKbps > 0) {
            return bitrateKbps + " kbps";
        }
        if (sampleRateHz > 0) {
            return (sampleRateHz / 1000) + " kHz";
        }
        return "Hi-Res";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Track)) return false;
        Track track = (Track) o;
        return durationMs == track.durationMs
                && bitrateKbps == track.bitrateKbps
                && favorite == track.favorite
                && Objects.equals(id, track.id)
                && Objects.equals(uri, track.uri)
                && Objects.equals(title, track.title)
                && Objects.equals(artist, track.artist);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, uri, title, artist, durationMs, bitrateKbps, favorite);
    }
}
