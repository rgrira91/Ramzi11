package com.example.data;

import android.content.Context;
import android.content.SharedPreferences;
import com.example.model.Playlist;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.json.JSONArray;
import org.json.JSONObject;

public class PlaylistRepository {
    private static final String PREFS_NAME = "neon_wave_playlists";
    private static final String KEY_PLAYLISTS = "playlists_json";

    private final SharedPreferences prefs;

    public PlaylistRepository(Context context) {
        this.prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public synchronized List<Playlist> getPlaylists() {
        List<Playlist> list = new ArrayList<>();
        String raw = prefs.getString(KEY_PLAYLISTS, "[]");
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                String id = obj.getString("id");
                String name = obj.getString("name");
                JSONArray trackArr = obj.optJSONArray("tracks");
                List<String> trackIds = new ArrayList<>();
                if (trackArr != null) {
                    for (int j = 0; j < trackArr.length(); j++) {
                        trackIds.add(trackArr.getString(j));
                    }
                }
                list.add(new Playlist(id, name, trackIds));
            }
        } catch (Throwable ignored) {}
        return list;
    }

    public synchronized Playlist createPlaylist(String name) {
        String cleanName = name != null ? name.trim() : "";
        if (cleanName.isEmpty()) cleanName = "My Playlist";
        List<Playlist> playlists = getPlaylists();
        Playlist created = new Playlist(UUID.randomUUID().toString(), cleanName, new ArrayList<>());
        playlists.add(created);
        saveAll(playlists);
        return created;
    }

    public synchronized void deletePlaylist(String playlistId) {
        List<Playlist> playlists = getPlaylists();
        playlists.removeIf(p -> p.getId().equals(playlistId));
        saveAll(playlists);
    }

    public synchronized void addTrackToPlaylist(String playlistId, String trackId) {
        if (playlistId == null || trackId == null) return;
        List<Playlist> playlists = getPlaylists();
        for (Playlist p : playlists) {
            if (p.getId().equals(playlistId) && !p.getTrackIds().contains(trackId)) {
                p.getTrackIds().add(trackId);
                break;
            }
        }
        saveAll(playlists);
    }

    public synchronized void removeTrackFromPlaylist(String playlistId, String trackId) {
        List<Playlist> playlists = getPlaylists();
        for (Playlist p : playlists) {
            if (p.getId().equals(playlistId)) {
                p.getTrackIds().remove(trackId);
                break;
            }
        }
        saveAll(playlists);
    }

    private void saveAll(List<Playlist> playlists) {
        JSONArray array = new JSONArray();
        try {
            for (Playlist p : playlists) {
                JSONObject obj = new JSONObject();
                obj.put("id", p.getId());
                obj.put("name", p.getName());
                JSONArray tracks = new JSONArray();
                for (String tid : p.getTrackIds()) {
                    tracks.put(tid);
                }
                obj.put("tracks", tracks);
                array.put(obj);
            }
        } catch (Throwable ignored) {}
        prefs.edit().putString(KEY_PLAYLISTS, array.toString()).apply();
    }
}
