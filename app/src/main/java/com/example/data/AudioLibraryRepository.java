package com.example.data;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.util.LruCache;
import android.util.Size;
import android.content.ComponentCallbacks2;
import com.example.model.Track;
import com.example.util.DeviceResourceOptimizer;
import com.example.util.PermissionHelper;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AudioLibraryRepository {
    private static final String PREFS_LIBRARY = "neon_wave_library";
    private static final String KEY_FAVORITES = "favorite_track_ids";
    private static final String KEY_SAF_URIS = "imported_saf_uris";
    private static final String KEY_RECENT_IDS = "recent_track_ids";
    private static final String KEY_LAST_TRACK_ID = "last_track_id";
    private static final String KEY_LAST_POSITION_MS = "last_position_ms";
    private static final String KEY_LYRICS_PREFIX = "lyrics_";
    private static final String KEY_HIDDEN_TRACK_IDS = "hidden_track_ids";
    private static final String KEY_AUTO_RESUME_ENABLED = "auto_resume_enabled";
    private static final String KEY_TRACK_RESUME_PREFIX = "track_resume_";

    private static final Set<String> SUPPORTED_AUDIO_EXTENSIONS = new HashSet<>();
    static {
        Collections.addAll(SUPPORTED_AUDIO_EXTENSIONS,
                "mp3", "flac", "wav", "aac", "m4a", "ogg", "opus",
                "amr", "3gp", "mka", "wma", "mid", "midi", "aiff", "alac");
    }

    public interface LibraryCallback {
        void onLibraryLoaded(List<Track> tracks);
    }

    public interface AlbumArtCallback {
        void onArtLoaded(String trackId, Bitmap bitmap);
    }

    public interface MetadataCallback {
        void onMetadataUpdated(Track enrichedTrack);
    }

    public interface LyricsCallback {
        void onLyricsLoaded(String trackId, String rawLyrics);
    }

    private final Context appContext;
    private final DeviceResourceOptimizer optimizer;
    private final SharedPreferences prefs;
    private final ExecutorService libraryExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService metadataExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService artExecutor = Executors.newFixedThreadPool(2);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final LruCache<String, Bitmap> artCache;
    private final Set<String> noArtCache = ConcurrentHashMap.newKeySet();

    public AudioLibraryRepository(Context context) {
        this.appContext = context.getApplicationContext();
        this.optimizer = DeviceResourceOptimizer.getInstance(appContext);
        this.prefs = appContext.getSharedPreferences(PREFS_LIBRARY, Context.MODE_PRIVATE);
        int maxKb = (int) (Runtime.getRuntime().maxMemory() / 1024L);
        int divider = Math.max(6, optimizer.getArtCacheDivider());
        int cacheSizeKb = Math.max(2048, maxKb / divider);
        this.artCache = new LruCache<String, Bitmap>(cacheSizeKb) {
            @Override
            protected int sizeOf(String key, Bitmap value) {
                return value.getByteCount() / 1024;
            }
        };
    }

    public void trimMemory(int level) {
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL
                || level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) {
            artCache.evictAll();
            noArtCache.clear();
        } else if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW
                || level == ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
            artCache.trimToSize(Math.max(1024, artCache.maxSize() / 3));
        }
    }

    public void clearArtCache() {
        artCache.evictAll();
        noArtCache.clear();
    }

    public int getArtCacheKb() {
        return artCache.size();
    }

    public int getArtCacheCount() {
        return artCache.putCount();
    }

    public void shutdown() {
        mainHandler.removeCallbacksAndMessages(null);
        libraryExecutor.shutdownNow();
        metadataExecutor.shutdownNow();
        artExecutor.shutdownNow();
        artCache.evictAll();
        noArtCache.clear();
    }

    public Set<String> getFavoriteIds() {
        return new HashSet<>(prefs.getStringSet(KEY_FAVORITES, new HashSet<>()));
    }

    public boolean toggleFavorite(Track track) {
        Set<String> favs = getFavoriteIds();
        boolean nowFav = !favs.contains(track.getId());
        if (nowFav) {
            favs.add(track.getId());
        } else {
            favs.remove(track.getId());
        }
        prefs.edit().putStringSet(KEY_FAVORITES, favs).apply();
        track.setFavorite(nowFav);
        return nowFav;
    }

    public void recordRecentPlay(String trackId) {
        if (trackId == null) return;
        String current = prefs.getString(KEY_RECENT_IDS, "");
        List<String> list = new ArrayList<>();
        list.add(trackId);
        if (!current.isEmpty()) {
            for (String item : current.split("\\|\\|\\|")) {
                if (!item.isEmpty() && !item.equals(trackId)) {
                    list.add(item);
                }
                if (list.size() >= 25) break;
            }
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append("|||");
            sb.append(list.get(i));
        }
        prefs.edit().putString(KEY_RECENT_IDS, sb.toString()).apply();
    }

    public List<String> getRecentTrackIds() {
        String current = prefs.getString(KEY_RECENT_IDS, "");
        List<String> list = new ArrayList<>();
        if (!current.isEmpty()) {
            for (String item : current.split("\\|\\|\\|")) {
                if (!item.isEmpty()) list.add(item);
            }
        }
        return list;
    }

    public void saveLastPlaybackState(String trackId, long positionMs) {
        if (trackId == null) return;
        prefs.edit()
                .putString(KEY_LAST_TRACK_ID, trackId)
                .putLong(KEY_LAST_POSITION_MS, Math.max(0L, positionMs))
                .apply();
    }

    public String getLastTrackId() {
        return prefs.getString(KEY_LAST_TRACK_ID, null);
    }

    public long getLastPositionMs() {
        return prefs.getLong(KEY_LAST_POSITION_MS, 0L);
    }

    public boolean isAutoResumeEnabled() {
        return prefs.getBoolean(KEY_AUTO_RESUME_ENABLED, true);
    }

    public void setAutoResumeEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_AUTO_RESUME_ENABLED, enabled).apply();
    }

    public void saveTrackResumePosition(String trackId, long positionMs, long durationMs) {
        if (trackId == null) return;
        String key = KEY_TRACK_RESUME_PREFIX + trackId.hashCode();
        long pos = Math.max(0L, positionMs);
        if (pos < 2500L || (durationMs > 5000L && pos >= durationMs - 2500L)) {
            prefs.edit().remove(key).apply();
        } else {
            prefs.edit().putLong(key, pos).apply();
        }
    }

    public long getTrackResumePosition(String trackId) {
        if (trackId == null || !isAutoResumeEnabled()) return 0L;
        return Math.max(0L, prefs.getLong(KEY_TRACK_RESUME_PREFIX + trackId.hashCode(), 0L));
    }

    public void clearTrackResumePosition(String trackId) {
        if (trackId == null) return;
        prefs.edit().remove(KEY_TRACK_RESUME_PREFIX + trackId.hashCode()).apply();
    }

    public void saveCustomLyrics(String trackId, String rawLyrics) {
        if (trackId == null) return;
        prefs.edit().putString(KEY_LYRICS_PREFIX + trackId.hashCode(), rawLyrics != null ? rawLyrics : "").apply();
    }

    public void loadLyricsAsync(Track track, LyricsCallback callback) {
        if (track == null) return;
        metadataExecutor.execute(() -> {
            String saved = prefs.getString(KEY_LYRICS_PREFIX + track.getId().hashCode(), null);
            if (saved != null && !saved.trim().isEmpty()) {
                mainHandler.post(() -> callback.onLyricsLoaded(track.getId(), saved));
                return;
            }
            String embedded = extractId3UsltLyrics(track.getUri());
            mainHandler.post(() -> callback.onLyricsLoaded(track.getId(), embedded));
        });
    }

    public Set<String> getHiddenTrackIds() {
        return new HashSet<>(prefs.getStringSet(KEY_HIDDEN_TRACK_IDS, new HashSet<>()));
    }

    public int getHiddenTrackCount() {
        return prefs.getStringSet(KEY_HIDDEN_TRACK_IDS, new HashSet<>()).size();
    }

    public void hideTrackFromLibrary(String trackId) {
        if (trackId == null) return;
        Set<String> hidden = getHiddenTrackIds();
        hidden.add(trackId);
        prefs.edit().putStringSet(KEY_HIDDEN_TRACK_IDS, hidden).apply();
        removeImportedUri(trackId);
        removeFavoriteTrackId(trackId);
        removeRecentTrackId(trackId);
    }

    public void unhideAllTracks() {
        prefs.edit().remove(KEY_HIDDEN_TRACK_IDS).apply();
    }

    public void removeFavoriteTrackId(String trackId) {
        if (trackId == null) return;
        Set<String> favs = getFavoriteIds();
        if (favs.remove(trackId)) {
            prefs.edit().putStringSet(KEY_FAVORITES, favs).apply();
        }
    }

    public void removeRecentTrackId(String trackId) {
        if (trackId == null) return;
        String current = prefs.getString(KEY_RECENT_IDS, "");
        if (current.isEmpty()) return;
        List<String> list = new ArrayList<>();
        for (String item : current.split("\\|\\|\\|")) {
            if (!item.isEmpty() && !item.equals(trackId)) {
                list.add(item);
            }
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append("|||");
            sb.append(list.get(i));
        }
        prefs.edit().putString(KEY_RECENT_IDS, sb.toString()).apply();
    }

    public void addImportedUris(List<Uri> uris) {
        if (uris == null || uris.isEmpty()) return;
        Set<String> stored = new HashSet<>(prefs.getStringSet(KEY_SAF_URIS, new HashSet<>()));
        Set<String> hidden = getHiddenTrackIds();
        boolean hiddenChanged = false;
        ContentResolver resolver = appContext.getContentResolver();
        for (Uri uri : uris) {
            if (uri == null) continue;
            try {
                resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Throwable ignored) {}
            String uriStr = uri.toString();
            stored.add(uriStr);
            if (hidden.remove(uriStr)) {
                hiddenChanged = true;
            }
        }
        SharedPreferences.Editor editor = prefs.edit().putStringSet(KEY_SAF_URIS, stored);
        if (hiddenChanged) {
            editor.putStringSet(KEY_HIDDEN_TRACK_IDS, hidden);
        }
        editor.apply();
    }

    public void removeImportedUri(String trackId) {
        if (trackId == null) return;
        Set<String> stored = new HashSet<>(prefs.getStringSet(KEY_SAF_URIS, new HashSet<>()));
        if (stored.remove(trackId)) {
            prefs.edit().putStringSet(KEY_SAF_URIS, stored).apply();
        }
    }

    public void loadAllTracks(LibraryCallback callback) {
        libraryExecutor.execute(() -> {
            Map<String, Track> trackMap = new LinkedHashMap<>();
            Set<String> knownPaths = new HashSet<>();
            Set<String> favorites = getFavoriteIds();
            Set<String> hiddenIds = getHiddenTrackIds();

            if (PermissionHelper.hasAudioReadPermission(appContext)) {
                scanMediaStoreAudio(trackMap, knownPaths, favorites);
                scanMediaStoreFilesFallback(trackMap, knownPaths, favorites);
                scanExternalStorageDirectories(trackMap, knownPaths, favorites);
            }
            loadImportedSafTracks(trackMap, knownPaths, favorites);

            if (!hiddenIds.isEmpty()) {
                for (String hid : hiddenIds) {
                    trackMap.remove(hid);
                }
            }

            List<Track> result = new ArrayList<>(trackMap.values());
            mainHandler.post(() -> callback.onLibraryLoaded(result));
        });
    }

    public Track createTrackFromExternalUri(Uri uri) {
        Set<String> favorites = getFavoriteIds();
        Track track = buildTrackFromUri(uri, favorites, true);
        if (track != null) {
            List<Uri> single = new ArrayList<>();
            single.add(uri);
            addImportedUris(single);
        }
        return track;
    }

    private void scanMediaStoreAudio(
            Map<String, Track> outMap,
            Set<String> knownPaths,
            Set<String> favorites) {
        ContentResolver resolver = appContext.getContentResolver();
        Uri collection = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                ? MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
                : MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;

        List<String> projList = new ArrayList<>();
        Collections.addAll(projList,
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.MIME_TYPE,
                MediaStore.Audio.Media.SIZE,
                MediaStore.Audio.Media.ALBUM_ID,
                MediaStore.Audio.Media.DISPLAY_NAME,
                MediaStore.Audio.Media.DATE_ADDED,
                MediaStore.Audio.Media.DATA
        );
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            projList.add(MediaStore.Audio.Media.BUCKET_DISPLAY_NAME);
            projList.add(MediaStore.Audio.Media.RELATIVE_PATH);
        } else {
            projList.add(MediaStore.Audio.Media.BUCKET_DISPLAY_NAME);
        }

        String[] projection = projList.toArray(new String[0]);
        String sortOrder = MediaStore.Audio.Media.DATE_ADDED + " DESC";
        try (Cursor cursor = resolver.query(collection, projection, null, null, sortOrder)) {
            if (cursor == null) return;
            int idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
            int titleCol = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE);
            int artistCol = cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST);
            int albumCol = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM);
            int durationCol = cursor.getColumnIndex(MediaStore.Audio.Media.DURATION);
            int mimeCol = cursor.getColumnIndex(MediaStore.Audio.Media.MIME_TYPE);
            int sizeCol = cursor.getColumnIndex(MediaStore.Audio.Media.SIZE);
            int albumIdCol = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID);
            int displayNameCol = cursor.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME);
            int bucketCol = cursor.getColumnIndex(MediaStore.Audio.Media.BUCKET_DISPLAY_NAME);
            int relPathCol = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                    ? cursor.getColumnIndex(MediaStore.Audio.Media.RELATIVE_PATH) : -1;
            int dateCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATE_ADDED);
            int dataCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATA);

            while (cursor.moveToNext()) {
                long id = cursor.getLong(idCol);
                long duration = durationCol >= 0 ? cursor.getLong(durationCol) : 0L;
                long size = sizeCol >= 0 ? cursor.getLong(sizeCol) : 0L;
                String filePath = dataCol >= 0 ? cursor.getString(dataCol) : null;
                String displayName = displayNameCol >= 0 ? cursor.getString(displayNameCol) : null;

                // Keep files even when MediaStore duration is 0/unpopulated; only skip empty/tiny UI click sounds
                if (size <= 0 && (filePath == null || !new File(filePath).exists())) {
                    continue;
                }
                if (duration > 0 && duration < 800 && size > 0 && size < 16384) {
                    continue;
                }
                if (filePath != null && isSystemRingtoneOrCachePath(filePath)) {
                    continue;
                }

                if (filePath != null) {
                    knownPaths.add(normalizePath(filePath));
                }

                String title = titleCol >= 0 ? cursor.getString(titleCol) : null;
                if (title == null || title.trim().isEmpty()) {
                    title = stripExtension(displayName != null ? displayName : fileNameFromPath(filePath));
                }
                String artist = artistCol >= 0 ? cursor.getString(artistCol) : null;
                if (artist == null || artist.trim().isEmpty() || "<unknown>".equalsIgnoreCase(artist)) {
                    artist = "Unknown Artist";
                }
                String album = albumCol >= 0 ? cursor.getString(albumCol) : null;
                if (album == null || album.trim().isEmpty()) album = "Unknown Album";
                String mimeType = mimeCol >= 0 ? cursor.getString(mimeCol) : null;
                String format = inferFormat(displayName != null ? displayName : filePath, mimeType);
                int estBitrate = duration > 0 && size > 0 ? (int) ((size * 8L) / duration) : 0;

                String folderName = bucketCol >= 0 ? cursor.getString(bucketCol) : null;
                if ((folderName == null || folderName.trim().isEmpty()) && relPathCol >= 0) {
                    folderName = folderFromRelativePath(cursor.getString(relPathCol));
                }
                if ((folderName == null || folderName.trim().isEmpty()) && filePath != null) {
                    File parent = new File(filePath).getParentFile();
                    if (parent != null) folderName = parent.getName();
                }
                if (folderName == null || folderName.trim().isEmpty()) {
                    folderName = "Music";
                }

                long dateAdded = dateCol >= 0 ? cursor.getLong(dateCol) : 0L;
                Uri contentUri = ContentUris.withAppendedId(collection, id);
                long albumId = albumIdCol >= 0 ? cursor.getLong(albumIdCol) : -1L;
                Uri artUri = albumId > 0
                        ? ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), albumId)
                        : null;

                String trackId = contentUri.toString();
                Track track = new Track(
                        trackId,
                        contentUri,
                        title,
                        artist,
                        album,
                        duration,
                        format,
                        estBitrate,
                        44100,
                        mimeType != null ? mimeType : "audio/*",
                        artUri,
                        size,
                        folderName,
                        dateAdded,
                        false,
                        favorites.contains(trackId)
                );
                outMap.put(trackId, track);
            }
        } catch (Throwable ignored) {}
    }

    private void scanMediaStoreFilesFallback(
            Map<String, Track> outMap,
            Set<String> knownPaths,
            Set<String> favorites) {
        try {
            ContentResolver resolver = appContext.getContentResolver();
            Uri filesUri = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                    ? MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
                    : MediaStore.Files.getContentUri("external");

            String[] projection = new String[]{
                    MediaStore.Files.FileColumns._ID,
                    MediaStore.Files.FileColumns.DISPLAY_NAME,
                    MediaStore.Files.FileColumns.MIME_TYPE,
                    MediaStore.Files.FileColumns.SIZE,
                    MediaStore.Files.FileColumns.DATE_ADDED,
                    MediaStore.Files.FileColumns.DATA
            };
            String selection = MediaStore.Files.FileColumns.MEDIA_TYPE + "="
                    + MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO
                    + " OR " + MediaStore.Files.FileColumns.MIME_TYPE + " LIKE 'audio/%'"
                    + " OR " + MediaStore.Files.FileColumns.MIME_TYPE + "='application/ogg'"
                    + " OR LOWER(" + MediaStore.Files.FileColumns.DISPLAY_NAME + ") LIKE '%.mp3'"
                    + " OR LOWER(" + MediaStore.Files.FileColumns.DISPLAY_NAME + ") LIKE '%.m4a'"
                    + " OR LOWER(" + MediaStore.Files.FileColumns.DISPLAY_NAME + ") LIKE '%.flac'"
                    + " OR LOWER(" + MediaStore.Files.FileColumns.DISPLAY_NAME + ") LIKE '%.wav'"
                    + " OR LOWER(" + MediaStore.Files.FileColumns.DISPLAY_NAME + ") LIKE '%.aac'"
                    + " OR LOWER(" + MediaStore.Files.FileColumns.DISPLAY_NAME + ") LIKE '%.opus'"
                    + " OR LOWER(" + MediaStore.Files.FileColumns.DISPLAY_NAME + ") LIKE '%.ogg'"
                    + " OR LOWER(" + MediaStore.Files.FileColumns.DISPLAY_NAME + ") LIKE '%.amr'"
                    + " OR LOWER(" + MediaStore.Files.FileColumns.DISPLAY_NAME + ") LIKE '%.wma'"
                    + " OR LOWER(" + MediaStore.Files.FileColumns.DISPLAY_NAME + ") LIKE '%.3gp'";

            try (Cursor cursor = resolver.query(filesUri, projection, selection, null,
                    MediaStore.Files.FileColumns.DATE_ADDED + " DESC")) {
                if (cursor == null) return;
                int idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID);
                int nameCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.DISPLAY_NAME);
                int mimeCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.MIME_TYPE);
                int sizeCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.SIZE);
                int dateCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATE_ADDED);
                int dataCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATA);

                while (cursor.moveToNext()) {
                    String path = dataCol >= 0 ? cursor.getString(dataCol) : null;
                    if (path != null) {
                        String norm = normalizePath(path);
                        if (knownPaths.contains(norm) || isSystemRingtoneOrCachePath(path)) {
                            continue;
                        }
                    }

                    long size = sizeCol >= 0 ? cursor.getLong(sizeCol) : 0L;
                    if (size <= 4096) continue;

                    String displayName = nameCol >= 0 ? cursor.getString(nameCol) : fileNameFromPath(path);
                    String mimeType = mimeCol >= 0 ? cursor.getString(mimeCol) : null;
                    if (!isAudioFileName(displayName) && (mimeType == null || !mimeType.startsWith("audio/"))) {
                        continue;
                    }

                    long id = cursor.getLong(idCol);
                    Uri contentUri = ContentUris.withAppendedId(filesUri, id);
                    String trackId = contentUri.toString();
                    if (outMap.containsKey(trackId)) continue;

                    if (path != null) knownPaths.add(normalizePath(path));

                    String folder = "Audio";
                    if (path != null) {
                        File p = new File(path).getParentFile();
                        if (p != null) folder = p.getName();
                    }

                    Track track = new Track(
                            trackId,
                            contentUri,
                            stripExtension(displayName),
                            "Unknown Artist",
                            "Unknown Album",
                            0L,
                            inferFormat(displayName, mimeType),
                            0,
                            44100,
                            mimeType != null ? mimeType : "audio/*",
                            null,
                            size,
                            folder,
                            dateCol >= 0 ? cursor.getLong(dateCol) : 0L,
                            false,
                            favorites.contains(trackId)
                    );
                    outMap.put(trackId, track);
                }
            }
        } catch (Throwable ignored) {}
    }

    private void scanExternalStorageDirectories(
            Map<String, Track> outMap,
            Set<String> knownPaths,
            Set<String> favorites) {
        List<File> roots = new ArrayList<>();
        try {
            File primary = Environment.getExternalStorageDirectory();
            if (primary != null && primary.exists() && primary.canRead()) {
                roots.add(primary);
            }
        } catch (Throwable ignored) {}

        // Also check secondary storage volumes (SD cards) via app external dirs
        try {
            File[] extDirs = appContext.getExternalFilesDirs(null);
            if (extDirs != null) {
                for (File dir : extDirs) {
                    if (dir == null) continue;
                    String abs = dir.getAbsolutePath();
                    int idx = abs.indexOf("/Android/data/");
                    if (idx > 0) {
                        File volRoot = new File(abs.substring(0, idx));
                        if (volRoot.exists() && volRoot.canRead() && !roots.contains(volRoot)) {
                            roots.add(volRoot);
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}

        List<String> unscannedPaths = new ArrayList<>();
        for (File root : roots) {
            walkAudioDirectory(root, 0, outMap, knownPaths, favorites, unscannedPaths);
        }

        if (!unscannedPaths.isEmpty()) {
            try {
                MediaScannerConnection.scanFile(
                        appContext,
                        unscannedPaths.toArray(new String[0]),
                        null,
                        null
                );
            } catch (Throwable ignored) {}
        }
    }

    private void walkAudioDirectory(
            File dir,
            int depth,
            Map<String, Track> outMap,
            Set<String> knownPaths,
            Set<String> favorites,
            List<String> unscannedPaths) {
        if (dir == null || depth > 6 || !dir.isDirectory() || !dir.canRead()) return;
        String name = dir.getName();
        if (name.startsWith(".")) return;
        if (depth <= 2 && ("DCIM".equalsIgnoreCase(name)
                || "Pictures".equalsIgnoreCase(name)
                || "Movies".equalsIgnoreCase(name))) {
            return;
        }
        String absDir = dir.getAbsolutePath().toLowerCase(Locale.US);
        if (absDir.contains("/android/data") || absDir.contains("/android/obb")) {
            return;
        }

        File[] children = dir.listFiles();
        if (children == null) return;

        for (File child : children) {
            if (child.isDirectory()) {
                if (!new File(child, ".nomedia").exists()) {
                    walkAudioDirectory(child, depth + 1, outMap, knownPaths, favorites, unscannedPaths);
                }
            } else if (child.isFile()) {
                long size = child.length();
                if (size <= 8192) continue;
                String fileName = child.getName();
                if (!isAudioFileName(fileName)) continue;
                String absPath = child.getAbsolutePath();
                String norm = normalizePath(absPath);
                if (knownPaths.contains(norm) || isSystemRingtoneOrCachePath(absPath)) continue;

                knownPaths.add(norm);
                unscannedPaths.add(absPath);

                Uri fileUri = Uri.fromFile(child);
                String trackId = fileUri.toString();
                String folderName = dir.getName();
                String format = inferFormat(fileName, null);

                Track track = new Track(
                        trackId,
                        fileUri,
                        stripExtension(fileName),
                        "Unknown Artist",
                        "Unknown Album",
                        0L,
                        format,
                        0,
                        44100,
                        "audio/*",
                        null,
                        size,
                        folderName.isEmpty() ? "Storage" : folderName,
                        child.lastModified() / 1000L,
                        false,
                        favorites.contains(trackId)
                );
                outMap.put(trackId, track);
            }
        }
    }

    private void loadImportedSafTracks(
            Map<String, Track> outMap,
            Set<String> knownPaths,
            Set<String> favorites) {
        Set<String> stored = prefs.getStringSet(KEY_SAF_URIS, new HashSet<>());
        for (String uriStr : stored) {
            try {
                Uri uri = Uri.parse(uriStr);
                if (outMap.containsKey(uri.toString())) continue;
                Track track = buildTrackFromUri(uri, favorites, true);
                if (track != null) {
                    outMap.put(track.getId(), track);
                }
            } catch (Throwable ignored) {}
        }
    }

    private Track buildTrackFromUri(Uri uri, Set<String> favorites, boolean isSaf) {
        ContentResolver resolver = appContext.getContentResolver();
        String displayName = null;
        long size = 0;
        try (Cursor cursor = resolver.query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (nameIdx >= 0) displayName = cursor.getString(nameIdx);
                int sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (sizeIdx >= 0) size = cursor.getLong(sizeIdx);
            }
        } catch (Throwable ignored) {}

        String title = stripExtension(displayName != null ? displayName : uri.getLastPathSegment());
        String artist = "Unknown Artist";
        String album = "Imported Audio";
        long duration = 0;
        int bitrateKbps = 0;
        int sampleRate = 44100;
        String mimeType = resolver.getType(uri);

        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(appContext, uri);
            String mTitle = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
            if (mTitle != null && !mTitle.trim().isEmpty()) title = mTitle.trim();
            String mArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
            if (mArtist != null && !mArtist.trim().isEmpty()) artist = mArtist.trim();
            String mAlbum = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM);
            if (mAlbum != null && !mAlbum.trim().isEmpty()) album = mAlbum.trim();
            String mDur = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            if (mDur != null) duration = Long.parseLong(mDur);
            String mBit = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE);
            if (mBit != null) bitrateKbps = Integer.parseInt(mBit) / 1000;
            String mSample = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE);
            if (mSample != null) sampleRate = Integer.parseInt(mSample);
            if (mimeType == null) {
                mimeType = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE);
            }
        } catch (Throwable ignored) {
        } finally {
            try {
                retriever.release();
            } catch (Throwable ignored) {}
        }

        if (bitrateKbps <= 0 && duration > 0 && size > 0) {
            bitrateKbps = (int) ((size * 8L) / duration);
        }

        String format = inferFormat(displayName, mimeType);
        String trackId = uri.toString();
        return new Track(
                trackId,
                uri,
                title.isEmpty() ? "Audio Track" : title,
                artist,
                album,
                duration,
                format,
                bitrateKbps,
                sampleRate,
                mimeType != null ? mimeType : "audio/*",
                null,
                size,
                "Imported",
                System.currentTimeMillis() / 1000L,
                isSaf,
                favorites.contains(trackId)
        );
    }

    public void enrichTrackDetailsAsync(Track track, MetadataCallback callback) {
        if (track == null) return;
        metadataExecutor.execute(() -> {
            int bitrateKbps = track.getBitrateKbps();
            int sampleRate = track.getSampleRateHz();
            long duration = track.getDurationMs();
            String title = track.getTitle();
            String artist = track.getArtist();

            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
            try {
                if ("file".equals(track.getUri().getScheme()) && track.getUri().getPath() != null) {
                    retriever.setDataSource(track.getUri().getPath());
                } else {
                    retriever.setDataSource(appContext, track.getUri());
                }
                String mBit = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE);
                if (mBit != null) {
                    int parsed = Integer.parseInt(mBit) / 1000;
                    if (parsed > 0) bitrateKbps = parsed;
                }
                String mSample = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE);
                if (mSample != null) {
                    int parsed = Integer.parseInt(mSample);
                    if (parsed > 0) sampleRate = parsed;
                }
                if (duration <= 0) {
                    String mDur = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                    if (mDur != null) duration = Long.parseLong(mDur);
                }
                if ("Unknown Artist".equals(artist)) {
                    String mArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
                    if (mArtist != null && !mArtist.trim().isEmpty()) artist = mArtist.trim();
                }
                String mTitle = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
                if (mTitle != null && !mTitle.trim().isEmpty() && title.equals(track.getTitle())) {
                    title = mTitle.trim();
                }
            } catch (Throwable ignored) {
            } finally {
                try {
                    retriever.release();
                } catch (Throwable ignored) {}
            }

            Track updated = new Track(
                    track.getId(),
                    track.getUri(),
                    title,
                    artist,
                    track.getAlbum(),
                    duration,
                    track.getFormat(),
                    bitrateKbps,
                    sampleRate,
                    track.getMimeType(),
                    track.getAlbumArtUri(),
                    track.getSizeBytes(),
                    track.getFolderName(),
                    track.getDateAddedSec(),
                    track.isFromSaf(),
                    track.isFavorite()
            );
            mainHandler.post(() -> callback.onMetadataUpdated(updated));
        });
    }

    public void loadAlbumArtAsync(Track track, int targetSizePx, AlbumArtCallback callback) {
        if (track == null || artExecutor.isShutdown()) return;
        Bitmap cached = artCache.get(track.getId());
        if (cached != null && !cached.isRecycled()) {
            callback.onArtLoaded(track.getId(), cached);
            return;
        }
        if (noArtCache.contains(track.getId())) {
            return;
        }
        final int effectiveSize = optimizer.getMaxAlbumArtPx(targetSizePx);

        artExecutor.execute(() -> {
            if (noArtCache.contains(track.getId())) return;
            Bitmap bitmap = null;

            // 1. Fast OS thumbnail on Android 10+ (avoids spinning up full MediaMetadataRetriever per row)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && "content".equals(track.getUri().getScheme())) {
                try {
                    bitmap = appContext.getContentResolver().loadThumbnail(
                            track.getUri(),
                            new Size(effectiveSize, effectiveSize),
                            null
                    );
                } catch (Throwable ignored) {}
            }

            // 2. Fast MediaStore albumart URI
            if (bitmap == null && track.getAlbumArtUri() != null) {
                try (InputStream is = appContext.getContentResolver().openInputStream(track.getAlbumArtUri())) {
                    if (is != null) {
                        BitmapFactory.Options opts = new BitmapFactory.Options();
                        opts.inSampleSize = 2;
                        Bitmap raw = BitmapFactory.decodeStream(is, null, opts);
                        if (raw != null && (raw.getWidth() > effectiveSize * 2 || raw.getHeight() > effectiveSize * 2)) {
                            bitmap = Bitmap.createScaledBitmap(raw, effectiveSize, effectiveSize, true);
                            if (bitmap != raw) {
                                raw.recycle();
                            }
                        } else {
                            bitmap = raw;
                        }
                    }
                } catch (Throwable ignored) {}
            }

            // 3. Fallback embedded art via MediaMetadataRetriever
            if (bitmap == null) {
                MediaMetadataRetriever retriever = new MediaMetadataRetriever();
                try {
                    if ("file".equals(track.getUri().getScheme()) && track.getUri().getPath() != null) {
                        retriever.setDataSource(track.getUri().getPath());
                    } else {
                        retriever.setDataSource(appContext, track.getUri());
                    }
                    byte[] embedded = retriever.getEmbeddedPicture();
                    if (embedded != null && embedded.length > 0) {
                        bitmap = decodeSampledBitmap(embedded, effectiveSize, effectiveSize);
                    }
                } catch (Throwable ignored) {
                } finally {
                    try {
                        retriever.release();
                    } catch (Throwable ignored) {}
                }
            }

            if (bitmap != null) {
                artCache.put(track.getId(), bitmap);
                final Bitmap finalBitmap = bitmap;
                mainHandler.post(() -> callback.onArtLoaded(track.getId(), finalBitmap));
            } else {
                noArtCache.add(track.getId());
            }
        });
    }

    private String extractId3UsltLyrics(Uri uri) {
        try (InputStream is = appContext.getContentResolver().openInputStream(uri)) {
            if (is == null) return null;
            byte[] header = new byte[10];
            if (is.read(header) < 10) return null;
            if (header[0] != 'I' || header[1] != 'D' || header[2] != '3') return null;

            int version = header[3] & 0xFF;
            int tagSize = ((header[6] & 0x7F) << 21)
                    | ((header[7] & 0x7F) << 14)
                    | ((header[8] & 0x7F) << 7)
                    | (header[9] & 0x7F);
            int toRead = Math.min(tagSize, 256 * 1024);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int remaining = toRead;
            while (remaining > 0) {
                int r = is.read(buf, 0, Math.min(buf.length, remaining));
                if (r <= 0) break;
                baos.write(buf, 0, r);
                remaining -= r;
            }
            byte[] tagBytes = baos.toByteArray();
            int pos = 0;
            while (pos + 10 < tagBytes.length) {
                String frameId = new String(tagBytes, pos, 4, StandardCharsets.ISO_8859_1);
                if (frameId.trim().isEmpty() || tagBytes[pos] == 0) break;
                int frameSize;
                if (version >= 4) {
                    frameSize = ((tagBytes[pos + 4] & 0x7F) << 21)
                            | ((tagBytes[pos + 5] & 0x7F) << 14)
                            | ((tagBytes[pos + 6] & 0x7F) << 7)
                            | (tagBytes[pos + 7] & 0x7F);
                } else {
                    frameSize = ((tagBytes[pos + 4] & 0xFF) << 24)
                            | ((tagBytes[pos + 5] & 0xFF) << 16)
                            | ((tagBytes[pos + 6] & 0xFF) << 8)
                            | (tagBytes[pos + 7] & 0xFF);
                }
                if (frameSize <= 0 || pos + 10 + frameSize > tagBytes.length) break;
                if ("USLT".equals(frameId) && frameSize > 5) {
                    int encoding = tagBytes[pos + 10] & 0xFF;
                    int contentStart = pos + 14;
                    int contentEnd = pos + 10 + frameSize;
                    if (encoding == 1 || encoding == 2) {
                        while (contentStart + 1 < contentEnd && !(tagBytes[contentStart] == 0 && tagBytes[contentStart + 1] == 0)) {
                            contentStart += 2;
                        }
                        contentStart = Math.min(contentEnd, contentStart + 2);
                        return new String(tagBytes, contentStart, contentEnd - contentStart, StandardCharsets.UTF_16).trim();
                    } else {
                        while (contentStart < contentEnd && tagBytes[contentStart] != 0) {
                            contentStart++;
                        }
                        contentStart = Math.min(contentEnd, contentStart + 1);
                        return new String(tagBytes, contentStart, contentEnd - contentStart,
                                encoding == 3 ? StandardCharsets.UTF_8 : StandardCharsets.ISO_8859_1).trim();
                    }
                }
                pos += 10 + frameSize;
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static boolean isAudioFileName(String name) {
        if (name == null) return false;
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot >= name.length() - 1) return false;
        String ext = name.substring(dot + 1).toLowerCase(Locale.US);
        return SUPPORTED_AUDIO_EXTENSIONS.contains(ext);
    }

    private static boolean isSystemRingtoneOrCachePath(String path) {
        if (path == null) return false;
        String lower = path.toLowerCase(Locale.US);
        return lower.contains("/android/data/")
                || lower.contains("/android/obb/")
                || lower.contains("/.thumbnails/")
                || lower.contains("/system/media/audio/ui/");
    }

    private static String normalizePath(String path) {
        return path.trim().toLowerCase(Locale.US);
    }

    private static String fileNameFromPath(String path) {
        if (path == null) return "Audio Track";
        int slash = path.lastIndexOf('/');
        return slash >= 0 && slash < path.length() - 1 ? path.substring(slash + 1) : path;
    }

    private static String folderFromRelativePath(String relPath) {
        if (relPath == null || relPath.trim().isEmpty()) return null;
        String trimmed = relPath.endsWith("/") ? relPath.substring(0, relPath.length() - 1) : relPath;
        int slash = trimmed.lastIndexOf('/');
        return slash >= 0 && slash < trimmed.length() - 1 ? trimmed.substring(slash + 1) : trimmed;
    }

    private static Bitmap decodeSampledBitmap(byte[] data, int reqWidth, int reqHeight) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, options);
        int inSampleSize = 1;
        while ((options.outHeight / inSampleSize) > reqHeight * 2
                && (options.outWidth / inSampleSize) > reqWidth * 2) {
            inSampleSize *= 2;
        }
        options.inJustDecodeBounds = false;
        options.inSampleSize = inSampleSize;
        return BitmapFactory.decodeByteArray(data, 0, data.length, options);
    }

    private static String stripExtension(String name) {
        if (name == null) return "Audio Track";
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            return name.substring(0, dot);
        }
        return name;
    }

    private static String inferFormat(String displayName, String mimeType) {
        if (displayName != null) {
            int dot = displayName.lastIndexOf('.');
            if (dot >= 0 && dot < displayName.length() - 1) {
                String ext = displayName.substring(dot + 1).toUpperCase(Locale.US);
                if (ext.length() <= 5) {
                    return ext;
                }
            }
        }
        if (mimeType != null) {
            String lower = mimeType.toLowerCase(Locale.US);
            if (lower.contains("mpeg") || lower.contains("mp3")) return "MP3";
            if (lower.contains("flac")) return "FLAC";
            if (lower.contains("wav") || lower.contains("x-wav")) return "WAV";
            if (lower.contains("opus")) return "OPUS";
            if (lower.contains("ogg") || lower.contains("vorbis")) return "OGG";
            if (lower.contains("mp4") || lower.contains("m4a") || lower.contains("aac")) return "AAC";
            if (lower.contains("amr")) return "AMR";
            if (lower.contains("webm") || lower.contains("matroska")) return "MKA";
        }
        return "AUDIO";
    }
}
