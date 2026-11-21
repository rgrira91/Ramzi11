package com.example.util;

import android.content.Context;
import androidx.annotation.NonNull;
import com.example.audio.AudioFxEngine;
import com.example.data.PlaylistRepository;
import com.example.model.EqPreset;
import com.example.model.Playlist;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Pure Android org.json backup exporter/importer for 10-band EQ, Bass/Balance/8D/Crossfade settings,
 * per-device EQ profiles, and user Playlists.
 */
public final class SettingsBackupManager {
    private SettingsBackupManager() {}

    @NonNull
    public static String exportBackupJson(@NonNull Context context) {
        AudioFxEngine fx = AudioFxEngine.getInstance();
        fx.init(context);
        PlaylistRepository playlistsRepo = new PlaylistRepository(context);

        JSONObject root = new JSONObject();
        try {
            root.put("version", 2);
            root.put("app", "NeonWave");

            JSONObject audio = new JSONObject();
            audio.put("eqEnabled", fx.isEqEnabled());
            audio.put("preset", fx.getCurrentPreset().getKey());
            audio.put("bassPercent", fx.getBassBoostPercent());
            audio.put("virtualizerPercent", fx.getVirtualizerPercent());
            audio.put("loudnessPercent", fx.getLoudnessPercent());
            audio.put("balancePercent", fx.getBalancePercent());
            audio.put("spatial8dEnabled", fx.isSpatial8dEnabled());
            audio.put("crossfadeSeconds", fx.getCrossfadeSeconds());
            audio.put("autoDeviceEqEnabled", fx.isAutoDeviceEqEnabled());
            audio.put("hapticBassEnabled", fx.isHapticBassEnabled());
            audio.put("edgeGlowEnabled", fx.isEdgeGlowEnabled());

            JSONArray bandsArr = new JSONArray();
            for (float db : fx.getUiBandGainsDb()) {
                bandsArr.put(db);
            }
            audio.put("bandGainsDb", bandsArr);

            JSONArray customArr = new JSONArray();
            for (float db : fx.getSavedCustomBandGainsDb()) {
                customArr.put(db);
            }
            audio.put("customBandGainsDb", customArr);
            root.put("audioFx", audio);

            JSONArray plsArr = new JSONArray();
            for (Playlist pl : playlistsRepo.getPlaylists()) {
                JSONObject pObj = new JSONObject();
                pObj.put("id", pl.getId());
                pObj.put("name", pl.getName());
                JSONArray trackIds = new JSONArray();
                for (String tid : pl.getTrackIds()) {
                    trackIds.put(tid);
                }
                pObj.put("trackIds", trackIds);
                plsArr.put(pObj);
            }
            root.put("playlists", plsArr);
        } catch (Throwable ignored) {}
        return root.toString();
    }

    public static boolean importBackupJson(@NonNull Context context, @NonNull String jsonText) {
        try {
            JSONObject root = new JSONObject(jsonText);
            AudioFxEngine fx = AudioFxEngine.getInstance();
            fx.init(context);

            JSONObject audio = root.optJSONObject("audioFx");
            if (audio != null) {
                if (audio.has("customBandGainsDb")) {
                    JSONArray custom = audio.getJSONArray("customBandGainsDb");
                    float[] arr = new float[AudioFxEngine.UI_BAND_COUNT];
                    for (int i = 0; i < Math.min(arr.length, custom.length()); i++) {
                        arr[i] = (float) custom.optDouble(i, 0.0);
                    }
                    fx.setCustomBandGainsDirect(arr);
                }
                EqPreset preset = EqPreset.fromKey(audio.optString("preset", EqPreset.ELECTRONIC.getKey()));
                fx.applyPreset(preset);
                fx.setEqEnabled(audio.optBoolean("eqEnabled", true));
                fx.setBassBoostPercent(audio.optInt("bassPercent", fx.getBassBoostPercent()));
                fx.setVirtualizerPercent(audio.optInt("virtualizerPercent", fx.getVirtualizerPercent()));
                fx.setLoudnessPercent(audio.optInt("loudnessPercent", fx.getLoudnessPercent()));
                fx.setBalancePercent(audio.optInt("balancePercent", 0));
                fx.setSpatial8dEnabled(audio.optBoolean("spatial8dEnabled", false));
                fx.setCrossfadeSeconds(audio.optInt("crossfadeSeconds", 0));
                fx.setAutoDeviceEqEnabled(audio.optBoolean("autoDeviceEqEnabled", true));
                fx.setHapticBassEnabled(audio.optBoolean("hapticBassEnabled", false));
                fx.setEdgeGlowEnabled(audio.optBoolean("edgeGlowEnabled", true));
            }

            JSONArray plsArr = root.optJSONArray("playlists");
            if (plsArr != null) {
                PlaylistRepository repo = new PlaylistRepository(context);
                for (int i = 0; i < plsArr.length(); i++) {
                    JSONObject pObj = plsArr.optJSONObject(i);
                    if (pObj == null) continue;
                    String name = pObj.optString("name", "").trim();
                    if (name.isEmpty()) continue;
                    Playlist created = repo.createPlaylist(name);
                    JSONArray trackIds = pObj.optJSONArray("trackIds");
                    if (trackIds != null) {
                        List<String> ids = new ArrayList<>();
                        for (int j = 0; j < trackIds.length(); j++) {
                            String id = trackIds.optString(j, "");
                            if (!id.isEmpty()) ids.add(id);
                        }
                        for (String id : ids) {
                            repo.addTrackToPlaylist(created.getId(), id);
                        }
                    }
                }
            }
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
