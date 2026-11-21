package com.example;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.NestedScrollView;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionCommand;
import androidx.media3.session.SessionToken;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.audio.AudioFxEngine;
import com.example.audio.AudioSpectrumTapProcessor;
import com.example.audio.BuiltInAudioTester;
import com.example.audio.NeonAudioService;
import com.example.data.AudioLibraryRepository;
import com.example.data.ListeningStatsTracker;
import com.example.data.PlaylistRepository;
import com.example.data.YoutubeRepository;
import com.example.model.EqPreset;
import com.example.model.Playlist;
import com.example.model.Track;
import com.example.ui.CinematicStageDialog;
import com.example.ui.YoutubeMusicDialog;
import com.example.ui.adapter.PresetAdapter;
import com.example.ui.adapter.TrackAdapter;
import com.example.ui.theme.AlbumColorExtractor;
import com.example.ui.theme.DynamicThemeRenderer;
import com.example.ui.theme.NeonPalette;
import com.example.ui.theme.PlayerUiStyleController;
import com.example.ui.theme.ReactiveButtonAnimator;
import com.example.ui.theme.UiStyleMode;
import com.example.ui.theme.UiStylePreferences;
import com.example.ui.theme.UiStyleSwitcherSheet;
import com.example.ui.views.DiscoStrobeOverlayView;
import com.example.ui.views.DiscoVuGlassView;
import com.example.ui.views.GlassNavBar;
import com.example.ui.views.NeonEdgeGlowView;
import com.example.ui.views.NeonEqualizerView;
import com.example.ui.views.NeonWaveformView;
import com.example.ui.views.ProStudioRackView;
import com.example.ui.views.PurificationSuiteView;
import com.example.ui.views.RetroVinylView;
import com.example.ui.views.SideLedMeterView;
import com.example.ui.views.StudioQuadMeterView;
import com.example.util.DeviceResourceOptimizer;
import com.example.util.HapticBassSync;
import com.example.util.LrcParser;
import com.example.util.NowPlayingCardExporter;
import com.example.util.PermissionHelper;
import com.example.util.SettingsBackupManager;
import com.example.util.TimeFormatter;
import com.example.widget.NeonWaveWidgetProvider;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.common.util.concurrent.ListenableFuture;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private enum FilterTab { ALL, FAVORITES, RECENT, PLAYLISTS, FORMATS, FOLDERS }
    private enum SortMode {
        NEWEST("Newest"),
        TITLE("A–Z"),
        ARTIST("Artist"),
        DURATION("Length"),
        FORMAT("Format");

        final String label;
        SortMode(String label) { this.label = label; }
    }

    private AudioLibraryRepository repository;
    private PlaylistRepository playlistRepository;
    private AudioFxEngine fxEngine;
    private DeviceResourceOptimizer resourceOptimizer;
    private final DeviceResourceOptimizer.OptimizerListener optimizerListener =
            tier -> runOnUiThread(this::applyAdaptiveOptimizationProfile);

    private View rootScroll;
    private View heroPlayerCard;
    private View cardEqPresets;
    private View cardAudioFrequency;
    private View cardUpNext;
    private TextView badgeBrandSubtitle;
    private TextView badgeEqMode;

    private ShapeableImageView heroAlbumArt;
    private TextView heroTrackTitle;
    private TextView heroTrackArtist;
    private TextView heroQualityBadge;
    private TextView heroBitrateBadge;
    private TextView btnHeroLyrics;
    private TextView btnAbLoop;
    private ImageButton btnFavorite;
    private ImageButton btnMoreOptions;

    private NeonWaveformView waveformView;
    private SeekBar seekBarProgress;
    private TextView textCurrentTime;
    private TextView textTotalTime;

    private ImageButton btnShuffle;
    private ImageButton btnPrev;
    private ImageButton btnPlayPause;
    private ImageButton btnNext;
    private ImageButton btnRepeat;

    private MaterialSwitch switchEqEnabled;
    private RecyclerView recyclerPresets;
    private PresetAdapter presetAdapter;
    private NeonEqualizerView equalizerView;

    private TextView btnSortTracks;
    private TextView btnFilterAll;
    private TextView btnFilterFavorites;
    private TextView btnFilterRecent;
    private TextView btnFilterPlaylists;
    private TextView btnFilterFormats;
    private TextView btnFilterFolders;
    private HorizontalScrollView subFilterScroll;
    private LinearLayout subFilterContainer;
    private ImageButton btnToggleSearch;
    private EditText editSearch;
    private View emptyStateContainer;
    private TextView emptyStateTitle;
    private TextView emptyStateSubtitle;
    private MaterialButton btnGrantPermissions;
    private MaterialButton btnEmptyPickFiles;
    private RecyclerView recyclerTracks;
    private TrackAdapter trackAdapter;

    private ListenableFuture<MediaController> controllerFuture;
    private MediaController mediaController;

    private final List<Track> allTracks = new ArrayList<>();
    private final List<Track> displayedTracks = new ArrayList<>();
    private Track activeTrack = null;

    private FilterTab activeFilterTab = FilterTab.ALL;
    private String selectedSubFilter = null;
    private SortMode activeSortMode = SortMode.NEWEST;
    private String searchQuery = "";
    private boolean isUserSeeking = false;
    private boolean restoredLastSession = false;

    private float currentPlaybackSpeed = 1.0f;
    private float currentPlaybackPitch = 1.0f;
    private int sleepMinutesSelected = 0;

    // A-B Loop segment state
    private long loopStartMs = -1L;
    private long loopEndMs = -1L;

    // Dynamic album-art NeonWave theme state
    private NeonPalette currentPalette = NeonPalette.DEFAULT;
    private ValueAnimator themeAnimator;
    private Bitmap currentAlbumBitmap = null;
    private final ExecutorService paletteExecutor = Executors.newSingleThreadExecutor();

    // Multi-interface theme switcher & controller
    private UiStylePreferences uiStylePreferences;
    private PlayerUiStyleController uiStyleController;
    private ReactiveButtonAnimator reactiveButtonAnimator;

    // 9 Visual Modes + Meters, Quick Disco Mode & 0-200% Bass Drive UI
    private View btnMeterPower;
    private ImageView iconMeterPower;
    private TextView textMeterPower;
    private View btnMeterSwitch;
    private ImageView iconMeterSwitch;
    private TextView textMeterSwitch;
    private View btnDiscoModeToggle;
    private ImageView iconDiscoModeToggle;
    private TextView textDiscoModeToggle;
    private HorizontalScrollView visualModeChipsScroll;
    private LinearLayout visualModeChipsContainer;
    private SideLedMeterView sideLedLeft;
    private SideLedMeterView sideLedRight;
    private DiscoVuGlassView discoVuGlassView;
    private PurificationSuiteView purificationSuiteView;
    private ProStudioRackView proStudioRackView;
    private StudioQuadMeterView studioQuadMeterView;
    private RetroVinylView vinylModeDiscView;
    private DiscoStrobeOverlayView discoStrobeOverlay;
    private SeekBar seekHeroBassDrive;
    private TextView textHeroBassPercent;
    private boolean updatingHeroBassSlider = false;
    private boolean visualMetersEnabled = true;
    private UiStylePreferences.VisualMeterMode visualMeterMode = UiStylePreferences.VisualMeterMode.DISCO_VU;
    private UiStylePreferences.VisualMeterMode previousNonDiscoMode = UiStylePreferences.VisualMeterMode.DISCO_VU;
    private Track pendingDeleteFileTrack;

    // Live Lyrics dialog state
    private BottomSheetDialog activeLyricsDialog;
    private String activeLyricsTrackId;
    private List<LrcParser.LyricLine> activeLyricLines = new ArrayList<>();
    private LinearLayout activeLyricsContainer;
    private ScrollView activeLyricsScroll;
    private int lastHighlightedLyricIdx = -2;
    private Track pendingLrcTargetTrack;

    private TextView badgePeakClip;
    private TextView badgeOutputDevice;
    private NeonEdgeGlowView neonEdgeGlowView;
    private GlassNavBar glassNavBar;
    private int cachedBottomSysInset = 0;
    private int baseContentTopPad = 0;
    private int baseContentLeftPad = 0;
    private int baseContentRightPad = 0;
    private View stickyMiniBar;
    private ShapeableImageView stickyMiniArt;
    private TextView stickyMiniTitle;
    private TextView stickyMiniArtist;
    private ImageButton stickyMiniPlayPause;
    private boolean isScrolledPastHero = false;

    private ListeningStatsTracker statsTracker;
    private HapticBassSync hapticBassSync;
    private BuiltInAudioTester audioTester;
    private long lastStatsUpdateMs = 0L;
    private long peakLitUntilMs = 0L;

    // Live Drive Mode dialog bindings
    private android.app.Dialog driveModeDialog;
    private ShapeableImageView driveAlbumArt;
    private TextView driveTitleText;
    private TextView driveArtistText;
    private TextView driveCurrentTime;
    private TextView driveTotalTime;
    private SeekBar driveSeekBar;
    private ImageButton drivePlayPauseBtn;

    // Track queue & UI update caches to avoid redundant Binder IPC and layout passes
    private final List<String> queuedTrackIds = new ArrayList<>();
    private long lastProgressDurMs = -1L;
    private long lastProgressTotalSec = -1L;
    private long lastProgressPosSec = -1L;
    private int lastRepeatIconRes = 0;
    private int lastAbLoopState = -1;
    private boolean hasRequestedAudioPermissionOnce = false;

    private YoutubeRepository youtubeRepository;
    private YoutubeMusicDialog youtubeMusicDialog;
    private CinematicStageDialog cinematicStageDialog;
    private List<LrcParser.LyricLine> stageLyricLines = new ArrayList<>();
    private String stageLyricsTrackId = null;
    private ValueAnimator vinylBrakeAnimator;
    private ValueAnimator sleepFadeAnimator;
    private final float[] tempXyLeft = new float[AudioSpectrumTapProcessor.XY_SAMPLE_COUNT];
    private final float[] tempXyRight = new float[AudioSpectrumTapProcessor.XY_SAMPLE_COUNT];
    private final float[] tempResponseCurve = new float[48];
    private long lastPerTrackSaveMs = 0L;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Runnable progressRunnable = new Runnable() {
        @Override
        public void run() {
            updatePlaybackProgress();
            updateActiveLyricsHighlight();
            updatePeakIndicatorAndStats();
            boolean playing = mediaController != null && mediaController.isPlaying();
            long interval = resourceOptimizer != null
                    ? resourceOptimizer.getProgressPollIntervalMs(playing)
                    : (playing ? 250L : 800L);
            mainHandler.postDelayed(this, interval);
        }
    };
    private final Runnable sleepTimerRunnable = () -> {
        sleepMinutesSelected = 0;
        if (mediaController != null && mediaController.isPlaying()) {
            startSleepLogarithmicFadeOut();
        }
    };

    private final ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                hasRequestedAudioPermissionOnce = true;
                refreshLibrary(false);
                if (PermissionHelper.hasRecordAudioPermission(this)) {
                    fxEngine.ensureVisualizerAttached(this);
                }
            });

    private final ActivityResultLauncher<String[]> pickAudioLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenMultipleDocuments(), uris -> {
                if (uris != null && !uris.isEmpty()) {
                    repository.addImportedUris(uris);
                    refreshLibraryAndSelectUri(uris.get(0));
                }
            });

    private final ActivityResultLauncher<String[]> pickLrcLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null && pendingLrcTargetTrack != null) {
                    String loaded = readTextFromUri(uri);
                    if (loaded != null && !loaded.trim().isEmpty()) {
                        repository.saveCustomLyrics(pendingLrcTargetTrack.getId(), loaded);
                        if (activeLyricsDialog != null && activeLyricsDialog.isShowing()) {
                            renderLyricsLines(loaded);
                        }
                        Toast.makeText(this, "Lyrics loaded", Toast.LENGTH_SHORT).show();
                    }
                }
            });

    private final ActivityResultLauncher<IntentSenderRequest> deleteFileLauncher =
            registerForActivityResult(new ActivityResultContracts.StartIntentSenderForResult(), result -> {
                Track target = pendingDeleteFileTrack;
                pendingDeleteFileTrack = null;
                if (result.getResultCode() == RESULT_OK && target != null) {
                    removeTrackFromAppLists(target);
                    Toast.makeText(this, R.string.toast_track_deleted, Toast.LENGTH_SHORT).show();
                }
            });

    private final ActivityResultLauncher<String> exportBackupLauncher =
            registerForActivityResult(new ActivityResultContracts.CreateDocument("application/json"), uri -> {
                if (uri == null) return;
                String json = SettingsBackupManager.exportBackupJson(this);
                try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                    if (os != null) {
                        os.write(json.getBytes(StandardCharsets.UTF_8));
                        os.flush();
                        Toast.makeText(this, R.string.toast_backup_exported, Toast.LENGTH_SHORT).show();
                        return;
                    }
                } catch (Throwable ignored) {}
                Toast.makeText(this, R.string.toast_backup_failed, Toast.LENGTH_SHORT).show();
            });

    private final ActivityResultLauncher<String[]> importBackupLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri == null) return;
                String raw = readTextFromUri(uri);
                if (raw != null && SettingsBackupManager.importBackupJson(this, raw)) {
                    syncAfterEqStateChanged();
                    rebuildSubFilterChips();
                    applyFilterAndRender();
                    Toast.makeText(this, R.string.toast_backup_imported, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, R.string.toast_backup_failed, Toast.LENGTH_SHORT).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        repository = new AudioLibraryRepository(this);
        playlistRepository = new PlaylistRepository(this);
        statsTracker = new ListeningStatsTracker(this);
        hapticBassSync = new HapticBassSync(this);
        audioTester = new BuiltInAudioTester();
        youtubeRepository = new YoutubeRepository(this);
        fxEngine = AudioFxEngine.getInstance();
        fxEngine.init(this);
        resourceOptimizer = DeviceResourceOptimizer.getInstance(this);
        resourceOptimizer.addListener(optimizerListener);
        uiStylePreferences = new UiStylePreferences(this);
        visualMetersEnabled = uiStylePreferences.isVisualMetersEnabled();
        visualMeterMode = uiStylePreferences.getVisualMeterMode();
        if (visualMeterMode != UiStylePreferences.VisualMeterMode.DISCO_MODE) {
            previousNonDiscoMode = visualMeterMode;
        }

        applyWindowInsets();
        bindViews();
        setupEqualizerSection();
        setupLibrarySection();
        setupTransportControls();
        setupUiStyleController();
        updateVisualMetersUi();
        updateOutputDeviceBadge();
        applyAdaptiveOptimizationProfile();

        requestInitialPermissionsIfNeeded();
        refreshLibrary();
        handleIncomingIntent(getIntent());
    }

    @Override
    protected void onNewIntent(@NonNull Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIncomingIntent(intent);
    }

    private void applyWindowInsets() {
        View container = findViewById(R.id.main_content_container);
        baseContentLeftPad = container.getPaddingLeft();
        baseContentTopPad = container.getPaddingTop();
        baseContentRightPad = container.getPaddingRight();

        View rootFrame = findViewById(R.id.root_frame);
        View target = rootFrame != null ? rootFrame : container;
        ViewCompat.setOnApplyWindowInsetsListener(target, (v, insets) -> {
            Insets sysBars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            int topInset = sysBars.top;
            if (topInset <= 0) {
                int resId = getResources().getIdentifier("status_bar_height", "dimen", "android");
                if (resId > 0) {
                    topInset = getResources().getDimensionPixelSize(resId);
                }
            }
            cachedBottomSysInset = sysBars.bottom;
            container.setPadding(
                    baseContentLeftPad + sysBars.left,
                    baseContentTopPad + topInset,
                    baseContentRightPad + sysBars.right,
                    getDesiredContentBottomPaddingPx()
            );
            updateBottomDockLayoutMargins();
            return insets;
        });
        ViewCompat.requestApplyInsets(target);
        target.post(() -> ViewCompat.requestApplyInsets(target));
    }

    private int getDesiredContentBottomPaddingPx() {
        float d = getResources().getDisplayMetrics().density;
        boolean showNav = uiStylePreferences == null || uiStylePreferences.isGlassNavBarEnabled();
        return Math.round((showNav ? 96 : 24) * d) + cachedBottomSysInset;
    }

    private void updateBottomDockLayoutMargins() {
        float d = getResources().getDisplayMetrics().density;
        boolean showNav = uiStylePreferences == null || uiStylePreferences.isGlassNavBarEnabled();
        int dockReservePx = showNav ? Math.round(76 * d) : 0;

        View container = findViewById(R.id.main_content_container);
        if (container != null) {
            int desiredBottom = getDesiredContentBottomPaddingPx();
            if (container.getPaddingBottom() != desiredBottom) {
                container.setPadding(
                        container.getPaddingLeft(),
                        container.getPaddingTop(),
                        container.getPaddingRight(),
                        desiredBottom);
            }
        }

        View nav = glassNavBar != null ? glassNavBar : findViewById(R.id.glass_nav_bar);
        if (nav != null) {
            nav.setVisibility(showNav ? View.VISIBLE : View.GONE);
            if (nav.getLayoutParams() instanceof android.view.ViewGroup.MarginLayoutParams) {
                android.view.ViewGroup.MarginLayoutParams nlp =
                        (android.view.ViewGroup.MarginLayoutParams) nav.getLayoutParams();
                int targetBottom = Math.round(10 * d) + cachedBottomSysInset;
                if (nlp.bottomMargin != targetBottom) {
                    nlp.bottomMargin = targetBottom;
                    nav.setLayoutParams(nlp);
                }
            }
        }

        View miniBar = findViewById(R.id.floating_mini_docked_card);
        if (miniBar != null && miniBar.getLayoutParams() instanceof android.view.ViewGroup.MarginLayoutParams) {
            android.view.ViewGroup.MarginLayoutParams mlp =
                    (android.view.ViewGroup.MarginLayoutParams) miniBar.getLayoutParams();
            int targetBottom = Math.round(12 * d) + dockReservePx + cachedBottomSysInset;
            if (mlp.bottomMargin != targetBottom) {
                mlp.bottomMargin = targetBottom;
                miniBar.setLayoutParams(mlp);
            }
        }

        View stickyView = stickyMiniBar != null ? stickyMiniBar : findViewById(R.id.sticky_scroll_mini_player);
        if (stickyView != null && stickyView.getLayoutParams() instanceof android.view.ViewGroup.MarginLayoutParams) {
            android.view.ViewGroup.MarginLayoutParams slp =
                    (android.view.ViewGroup.MarginLayoutParams) stickyView.getLayoutParams();
            int targetBottom = Math.round(10 * d) + dockReservePx + cachedBottomSysInset;
            if (slp.bottomMargin != targetBottom) {
                slp.bottomMargin = targetBottom;
                stickyView.setLayoutParams(slp);
            }
        }
    }

    private void bindViews() {
        rootScroll = findViewById(R.id.root_scroll);
        heroPlayerCard = findViewById(R.id.hero_player_card);
        cardEqPresets = findViewById(R.id.card_eq_presets);
        cardAudioFrequency = findViewById(R.id.card_audio_frequency);
        cardUpNext = findViewById(R.id.card_up_next);
        badgeBrandSubtitle = findViewById(R.id.badge_brand_subtitle);
        badgeEqMode = findViewById(R.id.badge_eq_mode);

        heroAlbumArt = findViewById(R.id.hero_album_art);
        heroTrackTitle = findViewById(R.id.hero_track_title);
        heroTrackTitle.setSelected(true);
        heroTrackArtist = findViewById(R.id.hero_track_artist);
        heroQualityBadge = findViewById(R.id.hero_quality_badge);
        heroBitrateBadge = findViewById(R.id.hero_bitrate_badge);
        btnHeroLyrics = findViewById(R.id.btn_hero_lyrics);
        btnAbLoop = findViewById(R.id.btn_ab_loop);
        btnFavorite = findViewById(R.id.btn_favorite);
        btnMoreOptions = findViewById(R.id.btn_more_options);

        waveformView = findViewById(R.id.neon_waveform_view);
        seekBarProgress = findViewById(R.id.seek_bar_progress);
        textCurrentTime = findViewById(R.id.text_current_time);
        textTotalTime = findViewById(R.id.text_total_time);

        btnShuffle = findViewById(R.id.btn_shuffle);
        btnPrev = findViewById(R.id.btn_prev);
        btnPlayPause = findViewById(R.id.btn_play_pause);
        btnNext = findViewById(R.id.btn_next);
        btnRepeat = findViewById(R.id.btn_repeat);

        switchEqEnabled = findViewById(R.id.switch_eq_enabled);
        recyclerPresets = findViewById(R.id.recycler_presets);
        equalizerView = findViewById(R.id.neon_equalizer_view);

        btnSortTracks = findViewById(R.id.btn_sort_tracks);
        btnFilterAll = findViewById(R.id.btn_filter_all);
        btnFilterFavorites = findViewById(R.id.btn_filter_favorites);
        btnFilterRecent = findViewById(R.id.btn_filter_recent);
        btnFilterPlaylists = findViewById(R.id.btn_filter_playlists);
        btnFilterFormats = findViewById(R.id.btn_filter_formats);
        btnFilterFolders = findViewById(R.id.btn_filter_folders);
        subFilterScroll = findViewById(R.id.sub_filter_scroll);
        subFilterContainer = findViewById(R.id.sub_filter_container);
        btnToggleSearch = findViewById(R.id.btn_toggle_search);
        editSearch = findViewById(R.id.edit_search);
        emptyStateContainer = findViewById(R.id.empty_state_container);
        emptyStateTitle = findViewById(R.id.empty_state_title);
        emptyStateSubtitle = findViewById(R.id.empty_state_subtitle);
        btnGrantPermissions = findViewById(R.id.btn_grant_permissions);
        btnEmptyPickFiles = findViewById(R.id.btn_empty_pick_files);
        recyclerTracks = findViewById(R.id.recycler_tracks);

        View youtubeBtn = findViewById(R.id.btn_youtube_music);
        if (youtubeBtn != null) {
            youtubeBtn.setOnClickListener(v -> openYoutubeMusicDialog(null));
        }
        View driveBtn = findViewById(R.id.btn_drive_mode);
        if (driveBtn != null) {
            driveBtn.setOnClickListener(v -> showDriveModeDialog());
        }
        findViewById(R.id.btn_pick_files).setOnClickListener(v -> launchAudioFilePicker());
        findViewById(R.id.btn_rescan).setOnClickListener(v -> {
            if (!PermissionHelper.hasAudioReadPermission(this)) {
                requestAudioLibraryPermissions();
            } else {
                refreshLibrary(true);
            }
        });

        waveformView.setOnClickListener(v -> {
            NeonWaveformView.VisualMode mode = waveformView.cycleVisualMode();
            Toast.makeText(this, mode.displayName, Toast.LENGTH_SHORT).show();
        });

        btnHeroLyrics.setOnClickListener(v -> {
            if (activeTrack != null) {
                showLyricsDialog(activeTrack);
            } else {
                Toast.makeText(this, R.string.default_track_artist, Toast.LENGTH_SHORT).show();
            }
        });

        btnAbLoop.setOnClickListener(v -> handleAbLoopTap());
        updateAbLoopUi();

        badgePeakClip = findViewById(R.id.badge_peak_clip);
        badgeOutputDevice = findViewById(R.id.badge_output_device);
        neonEdgeGlowView = findViewById(R.id.neon_edge_glow_view);
        stickyMiniBar = findViewById(R.id.sticky_scroll_mini_player);
        stickyMiniArt = findViewById(R.id.sticky_mini_art);
        stickyMiniTitle = findViewById(R.id.sticky_mini_title);
        stickyMiniArtist = findViewById(R.id.sticky_mini_artist);
        stickyMiniPlayPause = findViewById(R.id.sticky_mini_btn_play_pause);

        if (badgeOutputDevice != null) {
            badgeOutputDevice.setOnClickListener(v -> showAudioLabInsightsDialog());
        }
        if (stickyMiniBar != null) {
            View infoTap = findViewById(R.id.sticky_mini_info_tap);
            if (infoTap != null) {
                infoTap.setOnClickListener(v -> {
                    if (rootScroll instanceof NestedScrollView) {
                        ((NestedScrollView) rootScroll).smoothScrollTo(0, 0);
                    }
                });
            }
            View sPrev = findViewById(R.id.sticky_mini_btn_prev);
            View sNext = findViewById(R.id.sticky_mini_btn_next);
            if (sPrev != null) sPrev.setOnClickListener(v -> skipPrev());
            if (sNext != null) sNext.setOnClickListener(v -> skipNext());
            if (stickyMiniPlayPause != null) stickyMiniPlayPause.setOnClickListener(v -> togglePlayPause());
        }
        if (rootScroll instanceof NestedScrollView) {
            ((NestedScrollView) rootScroll).setOnScrollChangeListener(
                    (NestedScrollView.OnScrollChangeListener) (v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
                        boolean pastHero = scrollY > Math.round(280 * getResources().getDisplayMetrics().density);
                        if (pastHero != isScrolledPastHero) {
                            isScrolledPastHero = pastHero;
                            updateStickyMiniPlayerVisibility();
                        }
                    });
        }

        btnMeterPower = findViewById(R.id.btn_meter_power);
        iconMeterPower = findViewById(R.id.icon_meter_power);
        textMeterPower = findViewById(R.id.text_meter_power);
        btnMeterSwitch = findViewById(R.id.btn_meter_switch);
        iconMeterSwitch = findViewById(R.id.icon_meter_switch);
        textMeterSwitch = findViewById(R.id.text_meter_switch);
        btnDiscoModeToggle = findViewById(R.id.btn_disco_mode_toggle);
        iconDiscoModeToggle = findViewById(R.id.icon_disco_mode_toggle);
        textDiscoModeToggle = findViewById(R.id.text_disco_mode_toggle);
        visualModeChipsScroll = findViewById(R.id.visual_mode_chips_scroll);
        visualModeChipsContainer = findViewById(R.id.visual_mode_chips_container);
        sideLedLeft = findViewById(R.id.side_led_left);
        sideLedRight = findViewById(R.id.side_led_right);
        discoVuGlassView = findViewById(R.id.disco_vu_glass_view);
        purificationSuiteView = findViewById(R.id.purification_suite_view);
        proStudioRackView = findViewById(R.id.pro_studio_rack_view);
        studioQuadMeterView = findViewById(R.id.studio_quad_meter_view);
        vinylModeDiscView = findViewById(R.id.vinyl_mode_disc_view);
        discoStrobeOverlay = findViewById(R.id.disco_strobe_overlay);
        seekHeroBassDrive = findViewById(R.id.seek_hero_bass_drive);
        textHeroBassPercent = findViewById(R.id.text_hero_bass_percent);

        if (sideLedLeft != null) sideLedLeft.setChannel(SideLedMeterView.CHANNEL_LEFT);
        if (sideLedRight != null) sideLedRight.setChannel(SideLedMeterView.CHANNEL_RIGHT);
        if (vinylModeDiscView != null) {
            vinylModeDiscView.setCenterAligned(true);
            vinylModeDiscView.setOnClickListener(v -> togglePlayPause());
        }

        if (btnMeterPower != null) {
            btnMeterPower.setOnClickListener(v -> {
                visualMetersEnabled = !visualMetersEnabled;
                uiStylePreferences.setVisualMetersEnabled(visualMetersEnabled);
                updateVisualMetersUi();
                refreshVisualModeChips();
                syncAudioServicePrefs();
            });
        }
        if (btnMeterSwitch != null) {
            btnMeterSwitch.setOnClickListener(v -> {
                if (visualMeterMode != UiStylePreferences.VisualMeterMode.DISCO_MODE) {
                    previousNonDiscoMode = visualMeterMode;
                }
                visualMeterMode = visualMeterMode.next();
                if (visualMeterMode != UiStylePreferences.VisualMeterMode.DISCO_MODE) {
                    previousNonDiscoMode = visualMeterMode;
                }
                uiStylePreferences.setVisualMeterMode(visualMeterMode);
                if (!visualMetersEnabled) {
                    visualMetersEnabled = true;
                    uiStylePreferences.setVisualMetersEnabled(true);
                }
                updateVisualMetersUi();
                refreshVisualModeChips();
                syncAudioServicePrefs();
            });
        }
        if (btnDiscoModeToggle != null) {
            btnDiscoModeToggle.setOnClickListener(v -> {
                boolean currentlyDisco = visualMetersEnabled
                        && visualMeterMode == UiStylePreferences.VisualMeterMode.DISCO_MODE;
                if (currentlyDisco) {
                    visualMeterMode = previousNonDiscoMode != UiStylePreferences.VisualMeterMode.DISCO_MODE
                            ? previousNonDiscoMode
                            : UiStylePreferences.VisualMeterMode.DISCO_VU;
                } else {
                    if (visualMeterMode != UiStylePreferences.VisualMeterMode.DISCO_MODE) {
                        previousNonDiscoMode = visualMeterMode;
                    }
                    visualMeterMode = UiStylePreferences.VisualMeterMode.DISCO_MODE;
                    visualMetersEnabled = true;
                    uiStylePreferences.setVisualMetersEnabled(true);
                }
                uiStylePreferences.setVisualMeterMode(visualMeterMode);
                updateVisualMetersUi();
                refreshVisualModeChips();
                syncAudioServicePrefs();
            });
        }

        refreshVisualModeChips();
        setupGlassNavBar();
        setupPurificationSuite();
        setupProStudioRack();
        updateBottomDockLayoutMargins();
        setupReactiveAnimations();
    }

    private void setupGlassNavBar() {
        glassNavBar = findViewById(R.id.glass_nav_bar);
        if (glassNavBar == null) return;
        glassNavBar.setBlurSource(rootScroll);
        glassNavBar.setBackdropBaseColor(currentPalette.canvasEnd);
        glassNavBar.setGlassTint(NeonPalette.withAlpha(currentPalette.canvasCenter, 0x60));
        glassNavBar.setAccentColor(currentPalette.secondaryNeon);
        glassNavBar.setItems(
                new GlassNavBar.Item(getString(R.string.nav_player), GlassNavBar.ICON_PLAYER, GlassNavBar.FX_WAVE),
                new GlassNavBar.Item(getString(R.string.nav_disco), GlassNavBar.ICON_DISCO, GlassNavBar.FX_GLOW),
                new GlassNavBar.Item(getString(R.string.nav_eq), GlassNavBar.ICON_EQ, GlassNavBar.FX_SPIN),
                new GlassNavBar.Item(getString(R.string.nav_library), GlassNavBar.ICON_LIBRARY, GlassNavBar.FX_DROP),
                new GlassNavBar.Item(getString(R.string.nav_studio), GlassNavBar.ICON_STUDIO, GlassNavBar.FX_GLOW)
        );
        if (visualMetersEnabled && visualMeterMode == UiStylePreferences.VisualMeterMode.DISCO_MODE) {
            glassNavBar.select(1, false);
        }
        glassNavBar.setOnSelectListener((index, item, reselected) -> {
            if (index == 0) {
                scrollToSectionView(null);
            } else if (index == 1) {
                boolean currentlyDisco = visualMetersEnabled
                        && visualMeterMode == UiStylePreferences.VisualMeterMode.DISCO_MODE;
                if (currentlyDisco && reselected) {
                    visualMeterMode = previousNonDiscoMode != UiStylePreferences.VisualMeterMode.DISCO_MODE
                            ? previousNonDiscoMode
                            : UiStylePreferences.VisualMeterMode.DISCO_VU;
                    uiStylePreferences.setVisualMeterMode(visualMeterMode);
                    updateVisualMetersUi();
                    refreshVisualModeChips();
                    syncAudioServicePrefs();
                    glassNavBar.select(0, true);
                } else {
                    if (visualMeterMode != UiStylePreferences.VisualMeterMode.DISCO_MODE) {
                        previousNonDiscoMode = visualMeterMode;
                    }
                    visualMeterMode = UiStylePreferences.VisualMeterMode.DISCO_MODE;
                    visualMetersEnabled = true;
                    uiStylePreferences.setVisualMetersEnabled(true);
                    uiStylePreferences.setVisualMeterMode(visualMeterMode);
                    updateVisualMetersUi();
                    refreshVisualModeChips();
                    syncAudioServicePrefs();
                }
            } else if (index == 2) {
                if (uiStyleController != null && !uiStyleController.isShowEqDeck()) {
                    uiStyleController.setShowEqDeck(true);
                }
                scrollToSectionView(cardEqPresets != null ? cardEqPresets : cardAudioFrequency);
            } else if (index == 3) {
                scrollToSectionView(cardUpNext);
            } else if (index == 4) {
                showOptionsBottomSheet();
            }
        });
    }

    private void scrollToSectionView(@androidx.annotation.Nullable View targetSection) {
        if (!(rootScroll instanceof NestedScrollView)) return;
        NestedScrollView nsv = (NestedScrollView) rootScroll;
        if (targetSection == null) {
            nsv.smoothScrollTo(0, 0);
            return;
        }
        targetSection.post(() -> {
            int y = Math.max(0, targetSection.getTop() - Math.round(12 * getResources().getDisplayMetrics().density));
            nsv.smoothScrollTo(0, y);
        });
    }

    private void setupReactiveAnimations() {
        reactiveButtonAnimator = new ReactiveButtonAnimator();
        reactiveButtonAnimator.setDensity(getResources().getDisplayMetrics().density);
        reactiveButtonAnimator.setColorPulseEnabled(uiStylePreferences.isColorPulseButtonsEnabled());
        reactiveButtonAnimator.setRootCanvasView(rootScroll);
        reactiveButtonAnimator.setGlassNavBar(glassNavBar);
        reactiveButtonAnimator.setHeroCard(heroPlayerCard);
        reactiveButtonAnimator.registerPulseCard(heroPlayerCard, 0f);
        reactiveButtonAnimator.registerPulseCard(discoVuGlassView, 30f);
        reactiveButtonAnimator.registerPulseCard(purificationSuiteView, 45f);
        reactiveButtonAnimator.registerPulseCard(proStudioRackView, 52f);
        reactiveButtonAnimator.registerPulseCard(cardEqPresets, 60f);
        reactiveButtonAnimator.registerPulseCard(cardAudioFrequency, 120f);
        reactiveButtonAnimator.registerPulseCard(cardUpNext, 180f);

        reactiveButtonAnimator.registerAlbumArt(heroAlbumArt);
        reactiveButtonAnimator.registerAlbumArt(findViewById(R.id.floating_album_art));
        reactiveButtonAnimator.registerAlbumArt(findViewById(R.id.liquid_art_center));

        reactiveButtonAnimator.registerPlayButton(btnPlayPause);
        reactiveButtonAnimator.registerPlayButton(stickyMiniPlayPause);
        reactiveButtonAnimator.registerPlayButton(findViewById(R.id.floating_btn_play_pause));
        reactiveButtonAnimator.registerPlayButton(findViewById(R.id.mini_bar_btn_play_pause));
        reactiveButtonAnimator.registerPlayButton(findViewById(R.id.retro_btn_play_pause));
        reactiveButtonAnimator.registerPlayButton(findViewById(R.id.liquid_btn_play_pause));

        reactiveButtonAnimator.registerPillButton(btnMeterPower, iconMeterPower, textMeterPower, 0f, 18f);
        reactiveButtonAnimator.registerPillButton(btnMeterSwitch, iconMeterSwitch, textMeterSwitch, 95f, 18f);
        reactiveButtonAnimator.registerPillButton(btnDiscoModeToggle, iconDiscoModeToggle, textDiscoModeToggle, 48f, 18f);
        reactiveButtonAnimator.registerPillButton(
                findViewById(R.id.btn_ui_style),
                findViewById(R.id.icon_ui_style),
                findViewById(R.id.text_ui_style_label),
                185f,
                16f);
        reactiveButtonAnimator.registerPillButton(btnHeroLyrics, null, btnHeroLyrics, 245f, 14f);
        reactiveButtonAnimator.registerPillButton(btnAbLoop, null, btnAbLoop, 305f, 14f);
        reactiveButtonAnimator.registerPillButton(btnSortTracks, null, btnSortTracks, 130f, 16f);

        reactiveButtonAnimator.registerIconButton(btnShuffle, 25f, false);
        reactiveButtonAnimator.registerIconButton(btnPrev, 80f, false);
        reactiveButtonAnimator.registerIconButton(btnNext, 165f, false);
        reactiveButtonAnimator.registerIconButton(btnRepeat, 220f, false);
        reactiveButtonAnimator.registerIconButton(btnFavorite, 285f, false);
        reactiveButtonAnimator.registerIconButton(btnMoreOptions, 330f, true);
        reactiveButtonAnimator.registerIconButton(findViewById(R.id.btn_youtube_music), 310f, true);
        reactiveButtonAnimator.registerIconButton(findViewById(R.id.btn_drive_mode), 15f, true);
        reactiveButtonAnimator.registerIconButton(findViewById(R.id.btn_pick_files), 55f, true);
        reactiveButtonAnimator.registerIconButton(findViewById(R.id.btn_rescan), 140f, true);
        reactiveButtonAnimator.registerIconButton(btnToggleSearch, 195f, true);
        reactiveButtonAnimator.registerIconButton(findViewById(R.id.sticky_mini_btn_prev), 80f, false);
        reactiveButtonAnimator.registerIconButton(findViewById(R.id.sticky_mini_btn_next), 165f, false);

        reactiveButtonAnimator.setSpecialButtonRefs(btnFavorite, btnShuffle, btnRepeat);

        // Attach tactile touch spring bounce across alternate UI stage buttons as well
        int[] extraBounceIds = {
                R.id.floating_btn_prev, R.id.floating_btn_next, R.id.floating_btn_shuffle,
                R.id.floating_btn_repeat, R.id.floating_btn_favorite, R.id.floating_btn_more,
                R.id.floating_btn_collapse, R.id.mini_bar_btn_expand,
                R.id.mini_bar_btn_prev, R.id.mini_bar_btn_next, R.id.mini_bar_btn_shuffle,
                R.id.mini_bar_btn_repeat,
                R.id.retro_btn_prev, R.id.retro_btn_next, R.id.retro_btn_shuffle,
                R.id.retro_btn_repeat, R.id.retro_btn_favorite, R.id.retro_btn_more,
                R.id.liquid_btn_prev, R.id.liquid_btn_next, R.id.liquid_btn_shuffle,
                R.id.liquid_btn_repeat, R.id.liquid_btn_favorite, R.id.liquid_btn_more
        };
        for (int id : extraBounceIds) {
            reactiveButtonAnimator.attachTouchBounce(findViewById(id));
        }
    }

    private void setupEqualizerSection() {
        switchEqEnabled.setChecked(fxEngine.isEqEnabled());
        equalizerView.setEqEnabled(fxEngine.isEqEnabled());
        equalizerView.setBandsDb(fxEngine.getUiBandGainsDb());
        refreshEqFrequencyCurve();
        syncHeroBassDriveUi();
        syncPurificationSuiteUi();

        presetAdapter = new PresetAdapter(preset -> {
            fxEngine.applyPreset(preset);
            presetAdapter.setSelectedPreset(preset);
            equalizerView.setBandsDb(fxEngine.getUiBandGainsDb());
            refreshEqFrequencyCurve();
            syncHeroBassDriveUi();
            syncPurificationSuiteUi();
            if (uiStyleController != null) {
                uiStyleController.syncRetroKnobsFromSystemAndFx();
            }
            if (!switchEqEnabled.isChecked()) {
                switchEqEnabled.setChecked(true);
            } else {
                syncAudioServicePrefs();
            }
        });
        presetAdapter.setSelectedPreset(fxEngine.getCurrentPreset());
        recyclerPresets.setLayoutManager(
                new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        recyclerPresets.setAdapter(presetAdapter);

        switchEqEnabled.setOnCheckedChangeListener((buttonView, isChecked) -> {
            fxEngine.setEqEnabled(isChecked);
            equalizerView.setEqEnabled(isChecked);
            refreshEqFrequencyCurve();
            syncAudioServicePrefs();
        });

        equalizerView.setOnBandChangedListener((bandIndex, gainDb) -> {
            fxEngine.setUiBandGainDb(bandIndex, gainDb);
            presetAdapter.setSelectedPreset(EqPreset.CUSTOM);
            refreshEqFrequencyCurve();
            syncPurificationSuiteUi();
            if (!switchEqEnabled.isChecked()) {
                switchEqEnabled.setChecked(true);
            }
        });

        if (seekHeroBassDrive != null) {
            seekHeroBassDrive.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                    if (textHeroBassPercent != null) {
                        textHeroBassPercent.setText(progress + "%");
                    }
                    if (fromUser && !updatingHeroBassSlider) {
                        fxEngine.setBassBoostPercent(progress);
                        refreshEqFrequencyCurve();
                        if (!switchEqEnabled.isChecked()) {
                            switchEqEnabled.setChecked(true);
                        }
                        if (uiStyleController != null) {
                            uiStyleController.syncRetroKnobsFromSystemAndFx();
                        }
                    }
                }

                @Override public void onStartTrackingTouch(SeekBar sb) {}
                @Override public void onStopTrackingTouch(SeekBar sb) {}
            });
        }
    }

    private void syncHeroBassDriveUi() {
        int pct = fxEngine.getBassBoostPercent();
        updatingHeroBassSlider = true;
        if (seekHeroBassDrive != null) {
            seekHeroBassDrive.setProgress(pct);
        }
        if (textHeroBassPercent != null) {
            textHeroBassPercent.setText(pct + "%");
        }
        updatingHeroBassSlider = false;
    }

    private void setupPurificationSuite() {
        if (discoVuGlassView != null) {
            discoVuGlassView.setOnGainKnobsListener((gainLDb, gainRDb, fromUser) -> {
                if (fromUser && fxEngine != null) {
                    fxEngine.setGainLDb(gainLDb);
                    fxEngine.setGainRDb(gainRDb);
                }
            });
            discoVuGlassView.setOnQuickModeClickListener(quickMode -> {
                if (fxEngine == null) return;
                fxEngine.applyQuickMode(quickMode);
                if (presetAdapter != null) {
                    presetAdapter.setSelectedPreset(fxEngine.getCurrentPreset());
                }
                if (equalizerView != null) {
                    equalizerView.setBandsDb(fxEngine.getUiBandGainsDb());
                }
                if (switchEqEnabled != null && !switchEqEnabled.isChecked()) {
                    switchEqEnabled.setChecked(true);
                }
                syncHeroBassDriveUi();
                syncPurificationSuiteUi();
                if (uiStyleController != null) {
                    uiStyleController.syncRetroKnobsFromSystemAndFx();
                }
                syncAudioServicePrefs();
                int activeMode = fxEngine.getActiveQuickMode();
                if (activeMode == AudioFxEngine.QUICK_BASS_PRO) {
                    Toast.makeText(MainActivity.this, R.string.toast_quick_bass_pro, Toast.LENGTH_SHORT).show();
                } else if (activeMode == AudioFxEngine.QUICK_CLEAN_4K) {
                    Toast.makeText(MainActivity.this, R.string.toast_quick_clean_4k, Toast.LENGTH_SHORT).show();
                } else if (activeMode == AudioFxEngine.QUICK_CLUB) {
                    Toast.makeText(MainActivity.this, R.string.toast_quick_club, Toast.LENGTH_SHORT).show();
                }
            });
        }
        if (purificationSuiteView != null) {
            purificationSuiteView.setSuiteListener(new PurificationSuiteView.SuiteListener() {
                @Override
                public void onFaderChanged(int groupIndex, float gainDb) {
                    fxEngine.setFiveBandFaderDb(groupIndex, gainDb);
                    if (equalizerView != null) {
                        equalizerView.setBandsDb(fxEngine.getUiBandGainsDb());
                    }
                    if (presetAdapter != null) {
                        presetAdapter.setSelectedPreset(EqPreset.CUSTOM);
                    }
                    if (switchEqEnabled != null && !switchEqEnabled.isChecked()) {
                        switchEqEnabled.setChecked(true);
                    }
                }

                @Override
                public void onCompressorChanged(float ratio, float thresholdDb) {
                    fxEngine.setCompressorParams(ratio, thresholdDb);
                }

                @Override
                public void onNoiseGateToggled(boolean enabled) {
                    fxEngine.setNoiseGateEnabled(enabled);
                }

                @Override
                public void onNoiseReductionToggled(boolean enabled) {
                    fxEngine.setNoiseReductionEnabled(enabled);
                }

                @Override
                public void onPurificationBassChanged(float boostDb) {
                    fxEngine.setPurificationBassBoostDb(boostDb);
                }

                @Override
                public void onTrebleClarityChanged(float clarityDb) {
                    fxEngine.setTrebleClarityDb(clarityDb);
                }

                @Override
                public void onStereoWidthChanged(int widthPercent) {
                    fxEngine.setStereoWidthPercent(widthPercent);
                }

                @Override
                public void onLimiterChanged(float ceilingDb) {
                    fxEngine.setLimiterCeilingDb(ceilingDb);
                }

                @Override
                public void onAutoGainToggled(boolean enabled) {
                    fxEngine.setAutoGainEnabled(enabled);
                }

                @Override
                public void onHpfChanged(int hpfHz) {
                    fxEngine.setHpfHz(hpfHz);
                }

                @Override
                public void onLpfChanged(int lpfHz) {
                    fxEngine.setLpfHz(lpfHz);
                }

                @Override
                public void onUltraHd4kToggled(boolean enabled) {
                    fxEngine.setUltraHd4kEnabled(enabled);
                    refreshEqFrequencyCurve();
                    syncAudioServicePrefs();
                    Toast.makeText(MainActivity.this,
                            enabled ? R.string.toast_4k_purifier_on : R.string.toast_4k_purifier_off,
                            Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onAntiCrackleToggled(boolean enabled) {
                    fxEngine.setAntiCrackleEnabled(enabled);
                    refreshEqFrequencyCurve();
                    syncAudioServicePrefs();
                    Toast.makeText(MainActivity.this,
                            enabled ? R.string.toast_anti_crackle_on : R.string.toast_anti_crackle_off,
                            Toast.LENGTH_SHORT).show();
                }
            });
        }
        syncPurificationSuiteUi();
    }

    private void setupProStudioRack() {
        if (proStudioRackView == null) return;
        proStudioRackView.setRackListener(new ProStudioRackView.RackListener() {
            @Override
            public void onMasterBypassChanged(boolean bypass) {
                fxEngine.setMasterBypass(bypass);
                refreshEqFrequencyCurve();
                syncAudioServicePrefs();
                Toast.makeText(MainActivity.this,
                        bypass ? R.string.toast_master_bypass_on : R.string.toast_master_bypass_off,
                        Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onSnapshotSelected(int slot) {
                fxEngine.switchToSnapshot(slot);
                syncAfterEqStateChanged();
                Toast.makeText(MainActivity.this,
                        getString(R.string.toast_snapshot_switched, slot == 0 ? "A" : "B"),
                        Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onSnapshotSaveRequested(int slot) {
                fxEngine.saveSnapshot(slot);
                Toast.makeText(MainActivity.this,
                        getString(R.string.toast_snapshot_saved, slot == 0 ? "A" : "B"),
                        Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onTubeEnabledChanged(boolean enabled) {
                fxEngine.setTubeEnabled(enabled);
                syncAudioServicePrefs();
            }

            @Override
            public void onTubeDriveChanged(int percent) {
                fxEngine.setTubeDrivePercent(percent);
            }

            @Override
            public void onTapeWarmthChanged(int percent) {
                fxEngine.setTapeWarmthPercent(percent);
                refreshEqFrequencyCurve();
            }

            @Override
            public void onCrossfeedChanged(boolean enabled) {
                fxEngine.setHeadphoneCrossfeedEnabled(enabled);
                syncAudioServicePrefs();
            }

            @Override
            public void onReverbEnabledChanged(boolean enabled) {
                fxEngine.setReverbEnabled(enabled);
                syncAudioServicePrefs();
            }

            @Override
            public void onReverbParamsChanged(int roomPercent, int dampPercent, int mixPercent) {
                fxEngine.setReverbParams(roomPercent, dampPercent, mixPercent);
            }

            @Override
            public void onDjFilterChanged(int filterPercent) {
                fxEngine.setDjFilterPercent(filterPercent);
                refreshEqFrequencyCurve();
            }

            @Override
            public void onTempoChanged(float tempo) {
                currentPlaybackSpeed = Math.max(0.50f, Math.min(1.50f, tempo));
                applyPlaybackSpeedPitch();
            }

            @Override
            public void onPitchChanged(float pitch) {
                currentPlaybackPitch = Math.max(0.75f, Math.min(1.35f, pitch));
                applyPlaybackSpeedPitch();
            }

            @Override
            public void onVinylBrakeToggled(boolean enabled) {
                fxEngine.setVinylBrakeEnabled(enabled);
            }

            @Override
            public void onLoopSetARequested() {
                if (mediaController != null && activeTrack != null && mediaController.getMediaItemCount() > 0) {
                    loopStartMs = Math.max(0L, mediaController.getCurrentPosition());
                    loopEndMs = -1L;
                    updateAbLoopUi();
                }
            }

            @Override
            public void onLoopSetBOrClearRequested() {
                handleAbLoopTap();
            }

            @Override
            public void onPerformancePadChanged(int padMode) {
                fxEngine.setActivePadMode(padMode);
                refreshEqFrequencyCurve();
                syncAudioServicePrefs();
            }

            @Override
            public void onMultibandEnabledChanged(boolean enabled) {
                fxEngine.setMultibandEnabled(enabled);
                syncAudioServicePrefs();
            }

            @Override
            public void onMultibandDriveChanged(int percent) {
                fxEngine.setMultibandDrivePercent(percent);
            }

            @Override
            public void onDeEsserChanged(int percent) {
                fxEngine.setDeEsserPercent(percent);
                refreshEqFrequencyCurve();
            }

            @Override
            public void onVocalRemoverChanged(int percent) {
                fxEngine.setVocalRemoverPercent(percent);
                syncAudioServicePrefs();
            }

            @Override
            public void onHarmanTargetChanged(boolean enabled) {
                fxEngine.setHarmanTargetEnabled(enabled);
                refreshEqFrequencyCurve();
                syncAudioServicePrefs();
            }

            @Override
            public void onCaptureRefProfileRequested() {
                fxEngine.captureReferenceProfile();
                syncProStudioRackUi();
                Toast.makeText(MainActivity.this, R.string.toast_ref_captured, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onRefMatchToggled(boolean enabled) {
                fxEngine.setRefMatchEnabled(enabled);
                syncAfterEqStateChanged();
                syncProStudioRackUi();
                Toast.makeText(MainActivity.this,
                        enabled ? R.string.toast_ref_match_on : R.string.toast_ref_match_off,
                        Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onStutterDivChanged(int stutterDiv) {
                fxEngine.setStutterDiv(stutterDiv);
                syncAudioServicePrefs();
            }

            @Override
            public void onKaossXyChanged(int filterPercent, int reverbMixPercent, boolean reverbActive) {
                fxEngine.setKaossXy(filterPercent, reverbMixPercent, reverbActive);
                refreshEqFrequencyCurve();
                syncProStudioRackUi();
            }

            @Override
            public void onHarmonicNextTrackRequested() {
                findAndPlayHarmonicNextTrack();
            }

            @Override
            public void onOpenStage3dRequested() {
                openCinematicStageDialog();
            }
        });
        syncProStudioRackUi();
    }

    private void syncProStudioRackUi() {
        if (proStudioRackView == null || fxEngine == null) return;
        proStudioRackView.syncState(
                fxEngine.isMasterBypass(),
                fxEngine.getActiveSnapshotSlot(),
                fxEngine.isTubeEnabled(),
                fxEngine.getTubeDrivePercent(),
                fxEngine.getTapeWarmthPercent(),
                fxEngine.isHeadphoneCrossfeedEnabled(),
                fxEngine.isReverbEnabled(),
                fxEngine.getReverbRoomPercent(),
                fxEngine.getReverbDampPercent(),
                fxEngine.getReverbMixPercent(),
                fxEngine.getDjFilterPercent(),
                currentPlaybackSpeed,
                currentPlaybackPitch,
                fxEngine.isVinylBrakeEnabled(),
                loopStartMs,
                loopEndMs,
                fxEngine.getActivePadMode()
        );
        proStudioRackView.syncAdvancedState(
                fxEngine.isMultibandEnabled(),
                fxEngine.getMultibandDrivePercent(),
                fxEngine.getDeEsserPercent(),
                fxEngine.getVocalRemoverPercent(),
                fxEngine.isHarmanTargetEnabled(),
                fxEngine.hasRefProfile(),
                fxEngine.isRefMatchEnabled(),
                fxEngine.getStutterDiv()
        );
    }

    private void refreshEqFrequencyCurve() {
        if (fxEngine == null) return;
        boolean eqVisible = equalizerView != null && equalizerView.getVisibility() == View.VISIBLE;
        boolean rackScopeVisible = proStudioRackView != null
                && proStudioRackView.getVisibility() == View.VISIBLE
                && proStudioRackView.getActiveTab() == ProStudioRackView.TAB_SCOPE;
        if (!eqVisible && !rackScopeVisible) {
            return;
        }
        fxEngine.computeFrequencyResponseCurve(tempResponseCurve);
        if (equalizerView != null) {
            equalizerView.setResponseCurveDb(tempResponseCurve);
        }
    }

    private void applyPlaybackSpeedPitch() {
        if (mediaController != null) {
            mediaController.setPlaybackParameters(new PlaybackParameters(currentPlaybackSpeed, currentPlaybackPitch));
        }
    }

    private void syncPurificationSuiteUi() {
        if (fxEngine == null) return;
        if (discoVuGlassView != null) {
            discoVuGlassView.setGainKnobs(fxEngine.getGainLDb(), fxEngine.getGainRDb());
            discoVuGlassView.setBitDepth(fxEngine.getLatestBitDepth());
            discoVuGlassView.setQuickModeState(fxEngine.getActiveQuickMode(), fxEngine.getLatestLiveBpm());
        }
        if (purificationSuiteView != null) {
            purificationSuiteView.syncFromEngine(
                    fxEngine.getFiveBandFadersDb(),
                    fxEngine.getCompressorRatio(),
                    fxEngine.getCompressorThresholdDb(),
                    fxEngine.isNoiseGateEnabled(),
                    fxEngine.isNoiseReductionEnabled(),
                    fxEngine.getPurificationBassBoostDb(),
                    fxEngine.getTrebleClarityDb(),
                    fxEngine.getStereoWidthPercent(),
                    fxEngine.getLimiterCeilingDb(),
                    fxEngine.isAutoGainEnabled(),
                    fxEngine.getHpfHz(),
                    fxEngine.getLpfHz(),
                    fxEngine.getLatestBitDepth()
            );
            purificationSuiteView.syncExtendedPurifierState(
                    fxEngine.isUltraHd4kEnabled(),
                    fxEngine.isAntiCrackleEnabled(),
                    fxEngine.isSpeakerOutputActive()
            );
        }
        syncProStudioRackUi();
        refreshEqFrequencyCurve();
    }

    private void refreshVisualModeChips() {
        if (visualModeChipsContainer == null) return;
        visualModeChipsContainer.removeAllViews();
        float d = getResources().getDisplayMetrics().density;
        UiStylePreferences.VisualMeterMode[] modes = UiStylePreferences.VisualMeterMode.values();
        TextView selectedChip = null;

        for (UiStylePreferences.VisualMeterMode mode : modes) {
            boolean active = visualMetersEnabled && mode == visualMeterMode;
            NeonPalette modePal = mode.getPalette();

            TextView chip = new TextView(this);
            chip.setText(mode.shortTitleRes);
            chip.setTextSize(11.5f);
            chip.setTypeface(null, active ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
            chip.setGravity(Gravity.CENTER);
            chip.setMinHeight(Math.round(36 * d));
            chip.setPadding(Math.round(12 * d), Math.round(4 * d), Math.round(12 * d), Math.round(4 * d));
            chip.setTextColor(active ? 0xFFFFFFFF : ContextCompat.getColor(this, R.color.text_secondary));

            if (active) {
                int core = NeonPalette.lerpColor(modePal.primaryNeon, 0xFFFFFFFF, 0.36f);
                int edge = NeonPalette.lerpColor(modePal.primaryNeon, 0xFF060914, 0.28f);
                android.graphics.drawable.GradientDrawable halo = new android.graphics.drawable.GradientDrawable();
                halo.setCornerRadius(20f * d);
                halo.setColor(NeonPalette.withAlpha(modePal.primaryNeon, 0x62));

                android.graphics.drawable.GradientDrawable body = new android.graphics.drawable.GradientDrawable(
                        android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                        new int[]{core, modePal.primaryNeon, edge});
                body.setCornerRadius(18f * d);
                body.setStroke(Math.max(2, Math.round(1.6f * d)),
                        NeonPalette.lerpColor(modePal.secondaryNeon, 0xFFFFFFFF, 0.50f));

                android.graphics.drawable.LayerDrawable layer = new android.graphics.drawable.LayerDrawable(
                        new android.graphics.drawable.Drawable[]{halo, body});
                int inset = Math.max(1, Math.round(1.6f * d));
                layer.setLayerInset(1, inset, inset, inset, inset);
                chip.setBackground(layer);
                chip.setTextColor(0xFFFFFFFF);
                chip.setShadowLayer(2f * d, 0f, 1f * d, 0xCC04070E);
            } else {
                android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
                bg.setCornerRadius(18f * d);
                bg.setColor(0x990D1222);
                bg.setStroke(Math.max(1, Math.round(1.0f * d)), NeonPalette.withAlpha(modePal.primaryNeon, 0x60));
                chip.setBackground(bg);
            }

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMarginEnd(Math.round(7 * d));
            chip.setLayoutParams(lp);

            chip.setOnClickListener(v -> {
                if (mode != UiStylePreferences.VisualMeterMode.DISCO_MODE) {
                    previousNonDiscoMode = mode;
                }
                visualMeterMode = mode;
                uiStylePreferences.setVisualMeterMode(mode);
                if (!visualMetersEnabled) {
                    visualMetersEnabled = true;
                    uiStylePreferences.setVisualMetersEnabled(true);
                }
                updateVisualMetersUi();
                refreshVisualModeChips();
                syncAudioServicePrefs();
            });
            visualModeChipsContainer.addView(chip);
            if (active) {
                selectedChip = chip;
            }
        }

        if (selectedChip != null && visualModeChipsScroll != null) {
            final TextView target = selectedChip;
            visualModeChipsScroll.post(() -> {
                int scrollX = Math.max(0, target.getLeft() - visualModeChipsScroll.getWidth() / 3);
                visualModeChipsScroll.smoothScrollTo(scrollX, 0);
            });
        }
    }

    private void setupLibrarySection() {
        trackAdapter = new TrackAdapter(repository, new TrackAdapter.OnTrackClickListener() {
            @Override
            public void onTrackClicked(Track track, int position) {
                playTrackAtDisplayedPosition(position);
            }

            @Override
            public void onTrackMoreClicked(Track track, View anchorView) {
                showTrackContextMenu(track, anchorView);
            }

            @Override
            public void onTrackDeleteClicked(Track track, int position) {
                confirmAndDeleteTrack(track);
            }
        });
        recyclerTracks.setLayoutManager(new LinearLayoutManager(this));
        recyclerTracks.setItemViewCacheSize(12);
        recyclerTracks.setAdapter(trackAdapter);

        btnFilterAll.setOnClickListener(v -> selectFilterTab(FilterTab.ALL));
        btnFilterFavorites.setOnClickListener(v -> selectFilterTab(FilterTab.FAVORITES));
        btnFilterRecent.setOnClickListener(v -> selectFilterTab(FilterTab.RECENT));
        btnFilterPlaylists.setOnClickListener(v -> selectFilterTab(FilterTab.PLAYLISTS));
        btnFilterFormats.setOnClickListener(v -> selectFilterTab(FilterTab.FORMATS));
        btnFilterFolders.setOnClickListener(v -> selectFilterTab(FilterTab.FOLDERS));

        btnSortTracks.setOnClickListener(v -> {
            SortMode[] modes = SortMode.values();
            activeSortMode = modes[(activeSortMode.ordinal() + 1) % modes.length];
            btnSortTracks.setText(getString(R.string.sort_label) + ": " + activeSortMode.label);
            applyFilterAndRender();
        });

        btnToggleSearch.setOnClickListener(v -> {
            boolean show = editSearch.getVisibility() != View.VISIBLE;
            editSearch.setVisibility(show ? View.VISIBLE : View.GONE);
            if (!show) {
                editSearch.setText("");
                searchQuery = "";
                applyFilterAndRender();
            } else {
                editSearch.requestFocus();
            }
        });

        editSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                searchQuery = s != null ? s.toString().trim().toLowerCase(Locale.US) : "";
                applyFilterAndRender();
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        btnGrantPermissions.setOnClickListener(v -> requestAudioLibraryPermissions());
        btnEmptyPickFiles.setOnClickListener(v -> launchAudioFilePicker());
    }

    private void selectFilterTab(FilterTab tab) {
        activeFilterTab = tab;
        selectedSubFilter = null;
        updateFilterTabsUi();
        rebuildSubFilterChips();
        applyFilterAndRender();
    }

    private void updateFilterTabsUi() {
        TextView[] tabs = {
                btnFilterAll, btnFilterFavorites, btnFilterRecent,
                btnFilterPlaylists, btnFilterFormats, btnFilterFolders
        };
        FilterTab[] enums = FilterTab.values();
        int secondaryText = uiStyleController != null
                ? uiStyleController.getSecondaryTextColorForTabs()
                : ContextCompat.getColor(this, R.color.text_secondary);
        int activeText = uiStyleController != null
                ? uiStyleController.getAccentTextColorForTabs()
                : currentPalette.secondaryNeon;
        for (int i = 0; i < tabs.length; i++) {
            boolean active = enums[i] == activeFilterTab;
            Object prev = tabs[i].getTag();
            if (!(prev instanceof Boolean) || ((Boolean) prev) != active) {
                tabs[i].setTag(active);
                tabs[i].setBackgroundResource(active ? R.drawable.bg_pill_badge : 0);
            }
            tabs[i].setTextColor(active ? activeText : secondaryText);
        }
    }

    private void rebuildSubFilterChips() {
        subFilterContainer.removeAllViews();
        if (activeFilterTab == FilterTab.FORMATS) {
            Set<String> formats = new LinkedHashSet<>();
            for (Track t : allTracks) {
                if (t.getFormat() != null) formats.add(t.getFormat());
            }
            if (formats.isEmpty()) {
                subFilterScroll.setVisibility(View.GONE);
                return;
            }
            if (selectedSubFilter == null) selectedSubFilter = formats.iterator().next();
            subFilterScroll.setVisibility(View.VISIBLE);
            for (String fmt : formats) {
                addSubChip(fmt, fmt.equals(selectedSubFilter), () -> {
                    selectedSubFilter = fmt;
                    rebuildSubFilterChips();
                    applyFilterAndRender();
                }, null);
            }
        } else if (activeFilterTab == FilterTab.FOLDERS) {
            Set<String> folders = new LinkedHashSet<>();
            for (Track t : allTracks) {
                folders.add(t.getFolderName());
            }
            if (folders.isEmpty()) {
                subFilterScroll.setVisibility(View.GONE);
                return;
            }
            if (selectedSubFilter == null) selectedSubFilter = folders.iterator().next();
            subFilterScroll.setVisibility(View.VISIBLE);
            for (String folder : folders) {
                addSubChip(folder, folder.equals(selectedSubFilter), () -> {
                    selectedSubFilter = folder;
                    rebuildSubFilterChips();
                    applyFilterAndRender();
                }, null);
            }
        } else if (activeFilterTab == FilterTab.PLAYLISTS) {
            subFilterScroll.setVisibility(View.VISIBLE);
            addSubChip(getString(R.string.btn_new_playlist), false, () -> showCreatePlaylistDialog(null), null);

            List<Playlist> playlists = playlistRepository.getPlaylists();
            if (!playlists.isEmpty() && selectedSubFilter == null) {
                selectedSubFilter = playlists.get(0).getId();
            }
            for (Playlist pl : playlists) {
                addSubChip(
                        pl.getName() + " (" + pl.getTrackIds().size() + ")",
                        pl.getId().equals(selectedSubFilter),
                        () -> {
                            selectedSubFilter = pl.getId();
                            rebuildSubFilterChips();
                            applyFilterAndRender();
                        },
                        () -> {
                            new AlertDialog.Builder(this)
                                    .setTitle(pl.getName())
                                    .setMessage("Delete this playlist?")
                                    .setPositiveButton("Delete", (d, w) -> {
                                        playlistRepository.deletePlaylist(pl.getId());
                                        if (pl.getId().equals(selectedSubFilter)) selectedSubFilter = null;
                                        rebuildSubFilterChips();
                                        applyFilterAndRender();
                                    })
                                    .setNegativeButton("Cancel", null)
                                    .show();
                        }
                );
            }
        } else {
            subFilterScroll.setVisibility(View.GONE);
        }
    }

    private void addSubChip(String label, boolean selected, Runnable onClick, Runnable onLongClick) {
        float d = getResources().getDisplayMetrics().density;
        TextView chip = new TextView(this);
        chip.setText(label);
        chip.setTextSize(11.5f);
        chip.setGravity(Gravity.CENTER);
        chip.setMinHeight(Math.round(34 * d));
        chip.setPadding(Math.round(12 * d), 0, Math.round(12 * d), 0);
        chip.setBackgroundResource(selected ? R.drawable.bg_preset_tile_selected : R.drawable.bg_preset_tile_normal);
        chip.setTextColor(ContextCompat.getColor(this, selected ? R.color.neon_cyan : R.color.text_secondary));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMarginEnd(Math.round(6 * d));
        chip.setLayoutParams(lp);
        chip.setOnClickListener(v -> onClick.run());
        if (onLongClick != null) {
            chip.setOnLongClickListener(v -> {
                onLongClick.run();
                return true;
            });
        }
        subFilterContainer.addView(chip);
    }

    private void setupTransportControls() {
        btnPlayPause.setOnClickListener(v -> togglePlayPause());
        btnNext.setOnClickListener(v -> skipNext());
        btnPrev.setOnClickListener(v -> skipPrev());
        btnShuffle.setOnClickListener(v -> toggleShuffle());
        btnRepeat.setOnClickListener(v -> cycleRepeatMode());
        btnFavorite.setOnClickListener(v -> toggleActiveFavorite());
        btnMoreOptions.setOnClickListener(v -> showOptionsBottomSheet());

        seekBarProgress.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    textCurrentTime.setText(TimeFormatter.formatDuration(progress));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                isUserSeeking = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                isUserSeeking = false;
                if (mediaController != null) {
                    mediaController.seekTo(seekBar.getProgress());
                }
            }
        });

        // Swipe left/right on album cover to skip tracks, long-press to inspect technical details
        if (heroAlbumArt != null) {
            GestureDetector artGestures = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
                @Override
                public boolean onDown(@NonNull MotionEvent e) {
                    return true;
                }

                @Override
                public boolean onFling(MotionEvent e1, @NonNull MotionEvent e2, float vx, float vy) {
                    if (e1 == null) return false;
                    float dx = e2.getX() - e1.getX();
                    if (Math.abs(dx) > 70f && Math.abs(vx) > 120f) {
                        if (dx < 0f) skipNext();
                        else skipPrev();
                        return true;
                    }
                    return false;
                }

                @Override
                public void onLongPress(@NonNull MotionEvent e) {
                    if (activeTrack != null) {
                        showTrackTechInspectorDialog(activeTrack);
                    }
                }
            });
            heroAlbumArt.setOnTouchListener((v, event) -> artGestures.onTouchEvent(event));
        }
    }

    private void setupUiStyleController() {
        uiStyleController = new PlayerUiStyleController(
                this,
                uiStylePreferences,
                repository,
                fxEngine,
                new PlayerUiStyleController.ActionsCallback() {
                    @Override public void onPlayPauseClicked() { togglePlayPause(); }
                    @Override public void onNextClicked() { skipNext(); }
                    @Override public void onPrevClicked() { skipPrev(); }
                    @Override public void onShuffleClicked() { toggleShuffle(); }
                    @Override public void onRepeatClicked() { cycleRepeatMode(); }
                    @Override public void onFavoriteClicked() { toggleActiveFavorite(); }
                    @Override public void onMoreOptionsClicked() { showOptionsBottomSheet(); }
                    @Override public void onSeekTo(int positionMs) {
                        if (mediaController != null) {
                            mediaController.seekTo(positionMs);
                        }
                    }
                    @Override public void onUserSeekingChanged(boolean seeking) {
                        isUserSeeking = seeking;
                    }
                    @Override public void onFloatingCollapsedChanged(boolean collapsed) {
                        updatePlaybackProgress();
                    }
                    @Override public void onTrebleKnobChanged(int treblePercent) {
                        float gainDb = AudioFxEngine.MIN_GAIN_DB
                                + (treblePercent / 100f) * (AudioFxEngine.MAX_GAIN_DB - AudioFxEngine.MIN_GAIN_DB);
                        fxEngine.setUiBandGainDb(8, gainDb);
                        fxEngine.setUiBandGainDb(9, gainDb);
                        equalizerView.setBandsDb(fxEngine.getUiBandGainsDb());
                        presetAdapter.setSelectedPreset(EqPreset.CUSTOM);
                        if (!switchEqEnabled.isChecked()) {
                            switchEqEnabled.setChecked(true);
                        }
                    }
                    @Override public void onUiStyleButtonClicked() {
                        openUiStyleStudio();
                    }
                    @Override public void onStageVisibilityUpdated() {
                        updateVisualMetersUi();
                        updateStickyMiniPlayerVisibility();
                    }
                }
        );
        uiStyleController.bindViews(trackAdapter, presetAdapter, equalizerView);
        UiStyleMode initialStyle = uiStylePreferences.getActiveStyle();
        uiStyleController.applyMode(initialStyle, false);
        if (reactiveButtonAnimator != null) {
            reactiveButtonAnimator.setUiStyleMode(initialStyle);
        }
    }

    private void openUiStyleStudio() {
        UiStyleSwitcherSheet.show(
                this,
                uiStylePreferences.getActiveStyle(),
                uiStyleController.isFloatingCollapsed(),
                uiStyleController.isShowEqDeck(),
                new UiStyleSwitcherSheet.OnStudioActionListener() {
                    @Override
                    public void onPreviewStyle(UiStyleMode mode) {
                        uiStyleController.applyMode(mode, false);
                        if (reactiveButtonAnimator != null) {
                            reactiveButtonAnimator.setUiStyleMode(mode);
                        }
                        if (glassNavBar != null) {
                            glassNavBar.setLightCanvas(!mode.isDarkCanvas);
                        }
                        updateFilterTabsUi();
                        updateShuffleAndRepeatUi();
                    }

                    @Override
                    public void onAssignStyle(UiStyleMode mode) {
                        uiStyleController.applyMode(mode, true);
                        if (reactiveButtonAnimator != null) {
                            reactiveButtonAnimator.setUiStyleMode(mode);
                        }
                        if (glassNavBar != null) {
                            glassNavBar.setLightCanvas(!mode.isDarkCanvas);
                        }
                        updateFilterTabsUi();
                        updateShuffleAndRepeatUi();
                    }

                    @Override
                    public void onToggleFloatingCollapsed(boolean collapsed) {
                        uiStyleController.setFloatingCollapsed(collapsed);
                    }

                    @Override
                    public void onToggleShowEqDeck(boolean showEq) {
                        uiStyleController.setShowEqDeck(showEq);
                    }

                    @Override
                    public void onOpenAudioFxStudio() {
                        showOptionsBottomSheet();
                    }
                }
        );
    }

    private void togglePlayPause() {
        if (mediaController == null) return;
        cancelSleepFadeIfActive();
        if (mediaController.getMediaItemCount() == 0) {
            if (!displayedTracks.isEmpty()) {
                int targetIdx = 0;
                if (activeTrack != null) {
                    for (int i = 0; i < displayedTracks.size(); i++) {
                        if (displayedTracks.get(i).getId().equals(activeTrack.getId())) {
                            targetIdx = i;
                            break;
                        }
                    }
                }
                playTrackAtDisplayedPosition(targetIdx);
            } else {
                launchAudioFilePicker();
            }
            return;
        }
        if (mediaController.isPlaying()) {
            performPauseWithOptionalVinylBrake();
        } else {
            if (vinylBrakeAnimator != null && vinylBrakeAnimator.isRunning()) {
                vinylBrakeAnimator.cancel();
                mediaController.setPlaybackParameters(new PlaybackParameters(currentPlaybackSpeed, currentPlaybackPitch));
            }
            mediaController.setVolume(1.0f);
            if (mediaController.getPlaybackState() == Player.STATE_ENDED) {
                mediaController.seekTo(0);
            }
            mediaController.play();
        }
    }

    private void performPauseWithOptionalVinylBrake() {
        saveCurrentTrackResumePosition();
        if (mediaController == null) return;
        if (!fxEngine.isVinylBrakeEnabled() || !mediaController.isPlaying()) {
            mediaController.pause();
            return;
        }
        if (vinylBrakeAnimator != null) {
            vinylBrakeAnimator.cancel();
        }
        final float startSpeed = currentPlaybackSpeed;
        final float startPitch = currentPlaybackPitch;
        vinylBrakeAnimator = ValueAnimator.ofFloat(1.0f, 0.0f);
        vinylBrakeAnimator.setDuration(460L);
        vinylBrakeAnimator.addUpdateListener(anim -> {
            if (mediaController == null) return;
            float frac = (float) anim.getAnimatedValue();
            float s = Math.max(0.20f, startSpeed * (0.20f + 0.80f * frac));
            float p = Math.max(0.45f, startPitch * (0.45f + 0.55f * frac));
            mediaController.setPlaybackParameters(new PlaybackParameters(s, p));
        });
        vinylBrakeAnimator.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled = false;
            @Override public void onAnimationCancel(Animator animation) { cancelled = true; }
            @Override public void onAnimationEnd(Animator animation) {
                if (!cancelled && mediaController != null) {
                    mediaController.pause();
                    mediaController.setPlaybackParameters(new PlaybackParameters(startSpeed, startPitch));
                    saveCurrentTrackResumePosition();
                }
            }
        });
        vinylBrakeAnimator.start();
    }

    private void skipNext() {
        if (mediaController != null && mediaController.hasNextMediaItem()) {
            saveCurrentTrackResumePosition();
            mediaController.seekToNextMediaItem();
            mediaController.play();
        }
    }

    private void skipPrev() {
        if (mediaController == null) return;
        if (mediaController.getCurrentPosition() > 3500L) {
            mediaController.seekTo(0);
        } else if (mediaController.hasPreviousMediaItem()) {
            saveCurrentTrackResumePosition();
            mediaController.seekToPreviousMediaItem();
            mediaController.play();
        }
    }

    private void toggleShuffle() {
        if (mediaController != null) {
            boolean next = !mediaController.getShuffleModeEnabled();
            mediaController.setShuffleModeEnabled(next);
            updateShuffleAndRepeatUi();
        }
    }

    private void cycleRepeatMode() {
        if (mediaController == null) return;
        int current = mediaController.getRepeatMode();
        int next;
        if (current == Player.REPEAT_MODE_OFF) next = Player.REPEAT_MODE_ALL;
        else if (current == Player.REPEAT_MODE_ALL) next = Player.REPEAT_MODE_ONE;
        else next = Player.REPEAT_MODE_OFF;
        mediaController.setRepeatMode(next);
        updateShuffleAndRepeatUi();
    }

    private void toggleActiveFavorite() {
        if (activeTrack == null) return;
        boolean nowFav = repository.toggleFavorite(activeTrack);
        updateFavoriteButton(nowFav);
        if (activeFilterTab == FilterTab.FAVORITES) {
            applyFilterAndRender();
        }
    }

    private void requestInitialPermissionsIfNeeded() {
        String[] missing = PermissionHelper.getAllMissingPermissions(this, false);
        if (missing.length > 0) {
            permissionLauncher.launch(missing);
        }
    }

    private void requestAudioLibraryPermissions() {
        String audioPerm = PermissionHelper.getAudioReadPermission();
        if (!PermissionHelper.hasAudioReadPermission(this)
                && hasRequestedAudioPermissionOnce
                && !shouldShowRequestPermissionRationale(audioPerm)) {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", getPackageName(), null));
            startActivity(intent);
            return;
        }
        String[] missing = PermissionHelper.getAllMissingPermissions(this, false);
        if (missing.length > 0) {
            permissionLauncher.launch(missing);
        } else {
            refreshLibrary(true);
        }
    }

    private void requestPermissionsWithVisualizer() {
        String[] missing = PermissionHelper.getAllMissingPermissions(this, true);
        if (missing.length > 0) {
            permissionLauncher.launch(missing);
        } else {
            refreshLibrary(false);
        }
    }

    private void launchAudioFilePicker() {
        pickAudioLauncher.launch(new String[]{
                "audio/*",
                "application/ogg",
                "application/x-flac",
                "application/octet-stream"
        });
    }

    private void handleIncomingIntent(Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if (Intent.ACTION_VIEW.equals(action) && intent.getData() != null) {
            Uri uri = intent.getData();
            String urlStr = uri.toString();
            if (YoutubeRepository.extractVideoId(urlStr) != null) {
                openYoutubeMusicDialog(urlStr);
                return;
            }
            Track externalTrack = repository.createTrackFromExternalUri(uri);
            if (externalTrack != null) {
                refreshLibraryAndSelectUri(uri);
            }
        } else if (Intent.ACTION_SEND.equals(action)) {
            String sharedText = intent.getStringExtra(Intent.EXTRA_TEXT);
            if (sharedText != null && YoutubeRepository.extractVideoId(sharedText) != null) {
                openYoutubeMusicDialog(sharedText);
            }
        }
    }

    private void openYoutubeMusicDialog(String initialUrlOrQuery) {
        if (youtubeMusicDialog == null) {
            youtubeMusicDialog = new YoutubeMusicDialog(this, youtubeRepository, () -> {
                if (mediaController != null && mediaController.isPlaying()) {
                    saveCurrentTrackResumePosition();
                    mediaController.pause();
                }
            });
        }
        youtubeMusicDialog.show(initialUrlOrQuery);
    }

    private void refreshLibrary() {
        refreshLibraryAndSelectUri(null, false);
    }

    private void refreshLibrary(boolean showCountToast) {
        refreshLibraryAndSelectUri(null, showCountToast);
    }

    private void refreshLibraryAndSelectUri(Uri uriToPlayImmediately) {
        refreshLibraryAndSelectUri(uriToPlayImmediately, false);
    }

    private void refreshLibraryAndSelectUri(Uri uriToPlayImmediately, boolean showCountToast) {
        repository.loadAllTracks(tracks -> {
            allTracks.clear();
            allTracks.addAll(tracks);
            queuedTrackIds.clear();
            rebuildSubFilterChips();
            applyFilterAndRender();

            if (showCountToast) {
                Toast.makeText(
                        this,
                        getString(R.string.rescan_tracks_found, allTracks.size()),
                        Toast.LENGTH_SHORT
                ).show();
            }

            if (uriToPlayImmediately != null) {
                for (int i = 0; i < displayedTracks.size(); i++) {
                    if (uriToPlayImmediately.equals(displayedTracks.get(i).getUri())) {
                        playTrackAtDisplayedPosition(i);
                        return;
                    }
                }
            } else if (!restoredLastSession && activeTrack == null && !allTracks.isEmpty()) {
                restoredLastSession = true;
                String lastId = repository.getLastTrackId();
                Track target = allTracks.get(0);
                if (lastId != null) {
                    for (Track t : allTracks) {
                        if (t.getId().equals(lastId)) {
                            target = t;
                            break;
                        }
                    }
                }
                bindHeroTrack(target);
                long perTrackResume = repository.getTrackResumePosition(target.getId());
                long savedPos = perTrackResume > 0L
                        ? perTrackResume
                        : ((lastId != null && lastId.equals(target.getId()) && repository.isAutoResumeEnabled())
                                ? repository.getLastPositionMs()
                                : 0L);
                if (savedPos > 0L) {
                    seekBarProgress.setProgress((int) savedPos);
                    textCurrentTime.setText(TimeFormatter.formatDuration(savedPos));
                }
                prepareRestoredTrackIfIdle();
            }
        });
    }

    private void prepareRestoredTrackIfIdle() {
        if (mediaController == null || activeTrack == null || displayedTracks.isEmpty()) return;
        if (mediaController.getMediaItemCount() > 0) return;
        int idx = -1;
        for (int i = 0; i < displayedTracks.size(); i++) {
            if (displayedTracks.get(i).getId().equals(activeTrack.getId())) {
                idx = i;
                break;
            }
        }
        if (idx < 0) idx = 0;
        Track target = displayedTracks.get(idx);
        long savedPos = repository.getTrackResumePosition(target.getId());
        if (savedPos <= 0L && target.getId().equals(repository.getLastTrackId()) && repository.isAutoResumeEnabled()) {
            savedPos = repository.getLastPositionMs();
        }
        List<MediaItem> mediaItems = buildMediaItemsFromDisplayed();
        mediaController.setMediaItems(mediaItems, idx, Math.max(0L, savedPos));
        mediaController.prepare();
        mediaController.setPlaybackParameters(new PlaybackParameters(currentPlaybackSpeed, currentPlaybackPitch));
    }

    private void applyFilterAndRender() {
        displayedTracks.clear();

        if (activeFilterTab == FilterTab.RECENT) {
            List<String> recentIds = repository.getRecentTrackIds();
            for (String id : recentIds) {
                for (Track t : allTracks) {
                    if (t.getId().equals(id) && matchesSearch(t)) {
                        displayedTracks.add(t);
                        break;
                    }
                }
            }
        } else {
            List<String> activePlaylistIds = null;
            if (activeFilterTab == FilterTab.PLAYLISTS && selectedSubFilter != null) {
                for (Playlist pl : playlistRepository.getPlaylists()) {
                    if (pl.getId().equals(selectedSubFilter)) {
                        activePlaylistIds = pl.getTrackIds();
                        break;
                    }
                }
            }

            for (Track track : allTracks) {
                if (activeFilterTab == FilterTab.FAVORITES && !track.isFavorite()) continue;
                if (activeFilterTab == FilterTab.FORMATS && selectedSubFilter != null
                        && !selectedSubFilter.equalsIgnoreCase(track.getFormat())) continue;
                if (activeFilterTab == FilterTab.FOLDERS && selectedSubFilter != null
                        && !selectedSubFilter.equals(track.getFolderName())) continue;
                if (activeFilterTab == FilterTab.PLAYLISTS) {
                    if (activePlaylistIds == null || !activePlaylistIds.contains(track.getId())) continue;
                }
                if (!matchesSearch(track)) continue;
                displayedTracks.add(track);
            }

            sortDisplayedTracks();
        }

        trackAdapter.submitList(displayedTracks);
        updateRecyclerHeightForCount(displayedTracks.size());
        updateEmptyState();
    }

    private void updateRecyclerHeightForCount(int count) {
        if (recyclerTracks == null) return;
        android.view.ViewGroup.LayoutParams lp = recyclerTracks.getLayoutParams();
        if (lp == null) return;
        float density = getResources().getDisplayMetrics().density;
        int targetHeight = count <= 5
                ? android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                : Math.round(440 * density);
        boolean nested = count > 5;
        if (lp.height != targetHeight) {
            lp.height = targetHeight;
            recyclerTracks.setLayoutParams(lp);
        }
        recyclerTracks.setNestedScrollingEnabled(nested);
    }

    private boolean matchesSearch(Track track) {
        if (searchQuery.isEmpty()) return true;
        return track.getTitle().toLowerCase(Locale.US).contains(searchQuery)
                || track.getArtist().toLowerCase(Locale.US).contains(searchQuery)
                || track.getFormat().toLowerCase(Locale.US).contains(searchQuery)
                || track.getFolderName().toLowerCase(Locale.US).contains(searchQuery);
    }

    private void sortDisplayedTracks() {
        if (activeSortMode == SortMode.TITLE) {
            Collections.sort(displayedTracks, (a, b) -> a.getTitle().compareToIgnoreCase(b.getTitle()));
        } else if (activeSortMode == SortMode.ARTIST) {
            Collections.sort(displayedTracks, (a, b) -> a.getArtist().compareToIgnoreCase(b.getArtist()));
        } else if (activeSortMode == SortMode.DURATION) {
            Collections.sort(displayedTracks, (a, b) -> Long.compare(b.getDurationMs(), a.getDurationMs()));
        } else if (activeSortMode == SortMode.FORMAT) {
            Collections.sort(displayedTracks, (a, b) -> a.getFormat().compareToIgnoreCase(b.getFormat()));
        } else {
            Collections.sort(displayedTracks, (a, b) -> Long.compare(b.getDateAddedSec(), a.getDateAddedSec()));
        }
    }

    private void updateEmptyState() {
        if (!displayedTracks.isEmpty()) {
            emptyStateContainer.setVisibility(View.GONE);
            recyclerTracks.setVisibility(View.VISIBLE);
            return;
        }
        recyclerTracks.setVisibility(View.GONE);
        emptyStateContainer.setVisibility(View.VISIBLE);

        if (!PermissionHelper.hasAudioReadPermission(this)) {
            emptyStateTitle.setText(R.string.permission_title);
            emptyStateSubtitle.setText(R.string.permission_subtitle);
            btnGrantPermissions.setVisibility(View.VISIBLE);
        } else {
            emptyStateTitle.setText(R.string.empty_library_title);
            emptyStateSubtitle.setText(R.string.empty_library_subtitle);
            btnGrantPermissions.setVisibility(View.GONE);
        }
    }

    private List<MediaItem> buildMediaItemsFromDisplayed() {
        List<MediaItem> mediaItems = new ArrayList<>(displayedTracks.size());
        queuedTrackIds.clear();
        for (Track t : displayedTracks) {
            queuedTrackIds.add(t.getId());
            MediaMetadata.Builder meta = new MediaMetadata.Builder()
                    .setTitle(t.getTitle())
                    .setArtist(t.getArtist())
                    .setAlbumTitle(t.getAlbum());
            if (t.getAlbumArtUri() != null) {
                meta.setArtworkUri(t.getAlbumArtUri());
            }
            mediaItems.add(new MediaItem.Builder()
                    .setMediaId(t.getId())
                    .setUri(t.getUri())
                    .setMediaMetadata(meta.build())
                    .build());
        }
        return mediaItems;
    }

    private void saveCurrentTrackResumePosition() {
        if (activeTrack == null || mediaController == null || repository == null) return;
        long pos = Math.max(0L, mediaController.getCurrentPosition());
        long dur = Math.max(activeTrack.getDurationMs(), mediaController.getDuration());
        repository.saveTrackResumePosition(activeTrack.getId(), pos, dur);
        repository.saveLastPlaybackState(activeTrack.getId(), pos);
    }

    private void playTrackAtDisplayedPosition(int position) {
        if (position < 0 || position >= displayedTracks.size()) return;
        Track clicked = displayedTracks.get(position);

        if (mediaController != null && activeTrack != null
                && clicked.getId().equals(activeTrack.getId())
                && mediaController.getMediaItemCount() > 0) {
            if (mediaController.isPlaying()) {
                performPauseWithOptionalVinylBrake();
            } else {
                mediaController.setVolume(1.0f);
                mediaController.play();
            }
            return;
        }

        if (mediaController == null) {
            bindHeroTrack(clicked);
            return;
        }

        saveCurrentTrackResumePosition();
        cancelSleepFadeIfActive();
        long resumeMs = repository.getTrackResumePosition(clicked.getId());

        if (isQueuedListSyncedWithDisplayedTracks() && mediaController.getMediaItemCount() == displayedTracks.size()) {
            mediaController.seekTo(position, resumeMs);
            mediaController.setPlaybackParameters(new PlaybackParameters(currentPlaybackSpeed, currentPlaybackPitch));
            mediaController.setVolume(1.0f);
            mediaController.play();
        } else {
            List<MediaItem> mediaItems = buildMediaItemsFromDisplayed();
            mediaController.setMediaItems(mediaItems, position, resumeMs);
            mediaController.prepare();
            mediaController.setPlaybackParameters(new PlaybackParameters(currentPlaybackSpeed, currentPlaybackPitch));
            mediaController.setVolume(1.0f);
            mediaController.play();
        }
        resetAbLoop();
        bindHeroTrack(clicked);
        repository.recordRecentPlay(clicked.getId());
        if (resumeMs > 2500L) {
            Toast.makeText(this,
                    getString(R.string.toast_resumed_track, TimeFormatter.formatDuration(resumeMs)),
                    Toast.LENGTH_SHORT).show();
        }
    }

    private boolean isQueuedListSyncedWithDisplayedTracks() {
        if (queuedTrackIds.size() != displayedTracks.size() || queuedTrackIds.isEmpty()) {
            return false;
        }
        for (int i = 0; i < displayedTracks.size(); i++) {
            if (!queuedTrackIds.get(i).equals(displayedTracks.get(i).getId())) {
                return false;
            }
        }
        return true;
    }

    private void bindHeroTrack(Track track) {
        if (track == null) return;
        boolean trackChanged = activeTrack == null || !track.getId().equals(activeTrack.getId());
        if (trackChanged) {
            resetAbLoop();
        }
        activeTrack = track;
        heroTrackTitle.setText(track.getTitle());
        heroTrackArtist.setText(track.getArtist());
        heroQualityBadge.setText(track.getQualityBadgeText());
        heroBitrateBadge.setText(track.getBitrateBadgeText());
        updateFavoriteButton(track.isFavorite());
        if (trackChanged) {
            ReactiveButtonAnimator.animateTrackSwap(heroAlbumArt, heroTrackTitle, heroTrackArtist);
        }

        if (track.getDurationMs() > 0) {
            if (seekBarProgress.getMax() != (int) track.getDurationMs()) {
                seekBarProgress.setMax((int) track.getDurationMs());
            }
            textTotalTime.setText(TimeFormatter.formatDuration(track.getDurationMs()));
            lastProgressDurMs = track.getDurationMs();
            lastProgressTotalSec = track.getDurationMs() / 1000L;
        }
        lastProgressPosSec = -1L;

        if (uiStyleController != null) {
            uiStyleController.bindTrack(track, displayedTracks);
        }
        if (discoVuGlassView != null) {
            discoVuGlassView.setSampleRateHz(track.getSampleRateHz());
        }

        heroAlbumArt.setImageResource(R.drawable.img_default_album_art);
        if (stickyMiniArt != null) {
            stickyMiniArt.setImageResource(R.drawable.img_default_album_art);
        }
        if (stickyMiniTitle != null) {
            stickyMiniTitle.setText(track.getTitle());
        }
        if (stickyMiniArtist != null) {
            stickyMiniArtist.setText(track.getArtist());
        }
        if (driveModeDialog != null && driveModeDialog.isShowing()) {
            if (driveAlbumArt != null) driveAlbumArt.setImageResource(R.drawable.img_default_album_art);
            if (driveTitleText != null) driveTitleText.setText(track.getTitle());
            if (driveArtistText != null) driveArtistText.setText(track.getArtist());
            if (driveTotalTime != null && track.getDurationMs() > 0) {
                driveTotalTime.setText(TimeFormatter.formatDuration(track.getDurationMs()));
            }
            if (driveSeekBar != null && track.getDurationMs() > 0) {
                driveSeekBar.setMax((int) track.getDurationMs());
            }
        }
        updateStickyMiniPlayerVisibility();
        currentAlbumBitmap = null;
        if (vinylModeDiscView != null) {
            vinylModeDiscView.setAlbumBitmap(null);
        }
        if (!visualMetersEnabled && !fxEngine.isDynamicThemeEnabled()) {
            animateToPalette(NeonPalette.DEFAULT);
        }
        int sizePx = Math.round(96 * getResources().getDisplayMetrics().density);
        repository.loadAlbumArtAsync(track, sizePx, (trackId, bitmap) -> {
            if (activeTrack != null && trackId.equals(activeTrack.getId())) {
                if (bitmap != null && !bitmap.isRecycled()) {
                    heroAlbumArt.setImageBitmap(bitmap);
                    if (stickyMiniArt != null) {
                        stickyMiniArt.setImageBitmap(bitmap);
                    }
                    if (driveModeDialog != null && driveModeDialog.isShowing() && driveAlbumArt != null) {
                        driveAlbumArt.setImageBitmap(bitmap);
                    }
                    currentAlbumBitmap = bitmap;
                    if (vinylModeDiscView != null) {
                        vinylModeDiscView.setAlbumBitmap(bitmap);
                    }
                    if (uiStyleController != null) {
                        uiStyleController.onActiveAlbumBitmapLoaded(trackId, bitmap);
                    }
                    if (cinematicStageDialog != null && cinematicStageDialog.isShowing()) {
                        cinematicStageDialog.updateAlbumBitmap(bitmap);
                    }
                    extractAndApplyDynamicTheme(trackId, bitmap);
                } else {
                    currentAlbumBitmap = null;
                    if (vinylModeDiscView != null) {
                        vinylModeDiscView.setAlbumBitmap(null);
                    }
                    if (uiStyleController != null) {
                        uiStyleController.onActiveAlbumBitmapLoaded(trackId, null);
                    }
                    if (cinematicStageDialog != null && cinematicStageDialog.isShowing()) {
                        cinematicStageDialog.updateAlbumBitmap(null);
                    }
                    if (!visualMetersEnabled) {
                        animateToPalette(NeonPalette.DEFAULT);
                    }
                }
            }
        });

        repository.enrichTrackDetailsAsync(track, enriched -> {
            if (activeTrack != null && enriched.getId().equals(activeTrack.getId())) {
                activeTrack = enriched;
                heroQualityBadge.setText(enriched.getQualityBadgeText());
                heroBitrateBadge.setText(enriched.getBitrateBadgeText());
                if (discoVuGlassView != null) {
                    discoVuGlassView.setSampleRateHz(enriched.getSampleRateHz());
                }
            }
        });

        boolean playing = mediaController != null && mediaController.isPlaying();
        trackAdapter.setPlaybackState(track.getId(), playing);
        if (stickyMiniPlayPause != null) {
            stickyMiniPlayPause.setImageResource(playing ? R.drawable.ic_pause : R.drawable.ic_play);
        }
        if (driveModeDialog != null && driveModeDialog.isShowing() && drivePlayPauseBtn != null) {
            drivePlayPauseBtn.setImageResource(playing ? R.drawable.ic_pause : R.drawable.ic_play);
        }
        if (cinematicStageDialog != null && cinematicStageDialog.isShowing()) {
            cinematicStageDialog.updateTrack(track, currentAlbumBitmap);
            cinematicStageDialog.updatePlayingState(playing);
            loadStageLyricsIfNeeded(track);
        }
        if (uiStyleController != null) {
            uiStyleController.updatePlayingState(playing);
        }
        NeonWaveWidgetProvider.updateAllWidgets(this, track.getTitle(), track.getArtist(), playing);
    }

    private void showTrackContextMenu(Track track, View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenu().add(0, 1, 0, R.string.btn_add_to_playlist);
        popup.getMenu().add(0, 2, 1, track.isFavorite() ? "Remove Favorite" : "Add to Favorites");
        popup.getMenu().add(0, 3, 2, R.string.btn_open_lyrics);
        popup.getMenu().add(0, 4, 3, R.string.btn_share_track);
        if (activeFilterTab == FilterTab.PLAYLISTS && selectedSubFilter != null) {
            popup.getMenu().add(0, 5, 4, R.string.action_remove_from_playlist);
        } else {
            popup.getMenu().add(0, 6, 4, R.string.action_remove_from_list);
        }

        popup.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == 1) {
                showPlaylistPickerDialog(track);
            } else if (id == 2) {
                boolean nowFav = repository.toggleFavorite(track);
                if (activeTrack != null && activeTrack.getId().equals(track.getId())) {
                    updateFavoriteButton(nowFav);
                }
                applyFilterAndRender();
            } else if (id == 3) {
                showLyricsDialog(track);
            } else if (id == 4) {
                shareAudioTrack(track);
            } else if (id == 5 && selectedSubFilter != null) {
                playlistRepository.removeTrackFromPlaylist(selectedSubFilter, track.getId());
                rebuildSubFilterChips();
                applyFilterAndRender();
                Toast.makeText(this, R.string.toast_track_removed, Toast.LENGTH_SHORT).show();
            } else if (id == 6) {
                confirmAndDeleteTrack(track);
            }
            return true;
        });
        popup.show();
    }

    private void confirmAndDeleteTrack(Track track) {
        if (track == null) return;
        if (activeFilterTab == FilterTab.PLAYLISTS && selectedSubFilter != null) {
            new AlertDialog.Builder(this)
                    .setTitle(track.getTitle())
                    .setMessage(R.string.dialog_remove_track_message)
                    .setPositiveButton(R.string.action_remove_from_playlist, (d, w) -> {
                        playlistRepository.removeTrackFromPlaylist(selectedSubFilter, track.getId());
                        rebuildSubFilterChips();
                        applyFilterAndRender();
                        Toast.makeText(this, R.string.toast_track_removed, Toast.LENGTH_SHORT).show();
                    })
                    .setNeutralButton(R.string.action_remove_from_list, (d, w) -> {
                        removeTrackFromAppLists(track);
                        Toast.makeText(this, R.string.toast_track_removed, Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton(R.string.action_cancel, null)
                    .show();
            return;
        }

        if (activeFilterTab == FilterTab.FAVORITES) {
            new AlertDialog.Builder(this)
                    .setTitle(track.getTitle())
                    .setMessage(R.string.dialog_remove_track_message)
                    .setPositiveButton(R.string.action_remove_from_favorites, (d, w) -> {
                        repository.removeFavoriteTrackId(track.getId());
                        track.setFavorite(false);
                        if (activeTrack != null && activeTrack.getId().equals(track.getId())) {
                            updateFavoriteButton(false);
                        }
                        applyFilterAndRender();
                        Toast.makeText(this, R.string.toast_track_removed, Toast.LENGTH_SHORT).show();
                    })
                    .setNeutralButton(R.string.action_remove_from_list, (d, w) -> {
                        removeTrackFromAppLists(track);
                        Toast.makeText(this, R.string.toast_track_removed, Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton(R.string.action_cancel, null)
                    .show();
            return;
        }

        if (activeFilterTab == FilterTab.RECENT) {
            new AlertDialog.Builder(this)
                    .setTitle(track.getTitle())
                    .setMessage(R.string.dialog_remove_track_message)
                    .setPositiveButton(R.string.action_remove_from_recent, (d, w) -> {
                        repository.removeRecentTrackId(track.getId());
                        applyFilterAndRender();
                        Toast.makeText(this, R.string.toast_track_removed, Toast.LENGTH_SHORT).show();
                    })
                    .setNeutralButton(R.string.action_remove_from_list, (d, w) -> {
                        removeTrackFromAppLists(track);
                        Toast.makeText(this, R.string.toast_track_removed, Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton(R.string.action_cancel, null)
                    .show();
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(track.getTitle())
                .setMessage(R.string.dialog_remove_track_message)
                .setPositiveButton(R.string.action_remove_from_list, (d, w) -> {
                    removeTrackFromAppLists(track);
                    Toast.makeText(this, R.string.toast_track_removed, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.action_cancel, null);

        if (!track.isFromSaf() && canAttemptDeviceFileDelete(track)) {
            builder.setNeutralButton(R.string.action_delete_from_device, (d, w) -> deleteTrackFromDevice(track));
        }
        builder.show();
    }

    private boolean canAttemptDeviceFileDelete(Track track) {
        if (track == null || track.getUri() == null) return false;
        String scheme = track.getUri().getScheme();
        return "content".equals(scheme) || "file".equals(scheme);
    }

    private void deleteTrackFromDevice(Track track) {
        if (track == null || track.getUri() == null) return;
        Uri uri = track.getUri();
        if ("file".equals(uri.getScheme()) && uri.getPath() != null) {
            try {
                File f = new File(uri.getPath());
                if (!f.exists() || f.delete()) {
                    removeTrackFromAppLists(track);
                    Toast.makeText(this, R.string.toast_track_deleted, Toast.LENGTH_SHORT).show();
                    return;
                }
            } catch (Throwable ignored) {}
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && "content".equals(uri.getScheme())) {
            try {
                pendingDeleteFileTrack = track;
                android.app.PendingIntent pi = MediaStore.createDeleteRequest(
                        getContentResolver(),
                        Collections.singletonList(uri)
                );
                deleteFileLauncher.launch(new IntentSenderRequest.Builder(pi.getIntentSender()).build());
                return;
            } catch (Throwable ignored) {
                pendingDeleteFileTrack = null;
            }
        }

        try {
            int deleted = getContentResolver().delete(uri, null, null);
            if (deleted > 0) {
                removeTrackFromAppLists(track);
                Toast.makeText(this, R.string.toast_track_deleted, Toast.LENGTH_SHORT).show();
                return;
            }
        } catch (Throwable ignored) {}

        removeTrackFromAppLists(track);
        Toast.makeText(this, R.string.toast_track_removed, Toast.LENGTH_SHORT).show();
    }

    private void removeTrackFromAppLists(Track track) {
        if (track == null) return;
        String targetId = track.getId();
        repository.hideTrackFromLibrary(targetId);

        for (Playlist pl : playlistRepository.getPlaylists()) {
            if (pl.getTrackIds().contains(targetId)) {
                playlistRepository.removeTrackFromPlaylist(pl.getId(), targetId);
            }
        }

        allTracks.removeIf(t -> t.getId().equals(targetId));

        if (mediaController != null) {
            int queueIdx = queuedTrackIds.indexOf(targetId);
            if (queueIdx >= 0 && queueIdx < mediaController.getMediaItemCount()) {
                queuedTrackIds.remove(queueIdx);
                mediaController.removeMediaItem(queueIdx);
            }
        } else {
            queuedTrackIds.remove(targetId);
        }

        boolean wasActive = activeTrack != null && targetId.equals(activeTrack.getId());
        rebuildSubFilterChips();
        applyFilterAndRender();

        if (wasActive) {
            if (!displayedTracks.isEmpty()) {
                bindHeroTrack(displayedTracks.get(0));
            } else if (!allTracks.isEmpty()) {
                bindHeroTrack(allTracks.get(0));
            } else {
                activeTrack = null;
                heroTrackTitle.setText(R.string.default_track_title);
                heroTrackArtist.setText(R.string.default_track_artist);
                heroQualityBadge.setText(R.string.badge_hifi);
                heroBitrateBadge.setText(R.string.badge_default_bitrate);
                heroAlbumArt.setImageResource(R.drawable.img_default_album_art);
                seekBarProgress.setProgress(0);
                textCurrentTime.setText(R.string.time_zero);
                textTotalTime.setText(R.string.time_zero);
                updateFavoriteButton(false);
                trackAdapter.setPlaybackState(null, false);
                if (uiStyleController != null) {
                    uiStyleController.updatePlayingState(false);
                }
                updateVisualMetersUi();
            }
        }
    }

    private void showPlaylistPickerDialog(Track track) {
        if (track == null) return;
        List<Playlist> playlists = playlistRepository.getPlaylists();
        String[] names = new String[playlists.size() + 1];
        for (int i = 0; i < playlists.size(); i++) {
            names[i] = playlists.get(i).getName();
        }
        names[playlists.size()] = getString(R.string.btn_new_playlist);

        new AlertDialog.Builder(this)
                .setTitle(R.string.btn_add_to_playlist)
                .setItems(names, (d, which) -> {
                    if (which == playlists.size()) {
                        showCreatePlaylistDialog(track);
                    } else {
                        Playlist chosen = playlists.get(which);
                        playlistRepository.addTrackToPlaylist(chosen.getId(), track.getId());
                        Toast.makeText(this, "Added to " + chosen.getName(), Toast.LENGTH_SHORT).show();
                        if (activeFilterTab == FilterTab.PLAYLISTS) {
                            rebuildSubFilterChips();
                        }
                    }
                })
                .show();
    }

    private void showCreatePlaylistDialog(Track trackToAdd) {
        EditText input = new EditText(this);
        input.setHint("Playlist name");
        input.setPadding(48, 32, 48, 24);
        new AlertDialog.Builder(this)
                .setTitle(R.string.btn_new_playlist)
                .setView(input)
                .setPositiveButton("Create", (d, w) -> {
                    Playlist created = playlistRepository.createPlaylist(input.getText().toString());
                    if (trackToAdd != null) {
                        playlistRepository.addTrackToPlaylist(created.getId(), trackToAdd.getId());
                    }
                    if (activeFilterTab == FilterTab.PLAYLISTS) {
                        selectedSubFilter = created.getId();
                        rebuildSubFilterChips();
                        applyFilterAndRender();
                    }
                    Toast.makeText(this, "Created " + created.getName(), Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void shareAudioTrack(Track track) {
        if (track == null) return;
        try {
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType(track.getMimeType() != null ? track.getMimeType() : "audio/*");
            share.putExtra(Intent.EXTRA_STREAM, track.getUri());
            share.putExtra(Intent.EXTRA_SUBJECT, track.getTitle());
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(share, getString(R.string.btn_share_track)));
        } catch (Throwable e) {
            Toast.makeText(this, "Unable to share this track", Toast.LENGTH_SHORT).show();
        }
    }

    private void showLyricsDialog(Track track) {
        if (track == null) return;
        activeLyricsDialog = new BottomSheetDialog(this, R.style.NeonBottomSheetTheme);
        View view = getLayoutInflater().inflate(R.layout.dialog_lyrics, null);
        activeLyricsDialog.setContentView(view);

        ((TextView) view.findViewById(R.id.lyrics_track_title)).setText(track.getTitle());
        ((TextView) view.findViewById(R.id.lyrics_track_artist)).setText(track.getArtist());
        activeLyricsContainer = view.findViewById(R.id.lyrics_lines_container);
        activeLyricsScroll = view.findViewById(R.id.lyrics_scroll_view);
        activeLyricsTrackId = track.getId();
        lastHighlightedLyricIdx = -2;

        view.findViewById(R.id.btn_import_lrc).setOnClickListener(v -> {
            pendingLrcTargetTrack = track;
            pickLrcLauncher.launch(new String[]{"text/*", "application/octet-stream"});
        });

        view.findViewById(R.id.btn_paste_lyrics).setOnClickListener(v -> {
            EditText editor = new EditText(this);
            editor.setMinLines(6);
            editor.setHint("[00:12.00] Paste LRC or plain lyrics here...");
            editor.setPadding(40, 28, 40, 28);
            new AlertDialog.Builder(this)
                    .setTitle(R.string.btn_paste_lyrics)
                    .setView(editor)
                    .setPositiveButton("Save", (d, w) -> {
                        String text = editor.getText().toString();
                        repository.saveCustomLyrics(track.getId(), text);
                        renderLyricsLines(text);
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });

        repository.loadLyricsAsync(track, (loadedId, rawLyrics) -> {
            if (loadedId.equals(activeLyricsTrackId) && activeLyricsDialog != null && activeLyricsDialog.isShowing()) {
                renderLyricsLines(rawLyrics);
            }
        });

        activeLyricsDialog.setOnDismissListener(d -> {
            activeLyricsContainer = null;
            activeLyricsScroll = null;
            activeLyricsTrackId = null;
        });
        activeLyricsDialog.show();
    }

    private void renderLyricsLines(String rawLyrics) {
        if (activeLyricsContainer == null) return;
        activeLyricsContainer.removeAllViews();
        activeLyricLines = LrcParser.parse(rawLyrics);
        lastHighlightedLyricIdx = -2;

        if (activeLyricLines.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText(R.string.lyrics_empty_hint);
            empty.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
            empty.setTextSize(13.5f);
            activeLyricsContainer.addView(empty);
            return;
        }

        for (LrcParser.LyricLine line : activeLyricLines) {
            TextView tv = new TextView(this);
            if (line.timeMs >= 0) {
                tv.setText("[" + TimeFormatter.formatDuration(line.timeMs) + "]  " + line.text);
                tv.setOnClickListener(v -> {
                    if (mediaController != null && activeTrack != null && activeTrack.getId().equals(activeLyricsTrackId)) {
                        mediaController.seekTo(line.timeMs);
                    }
                });
            } else {
                tv.setText(line.text);
            }
            tv.setTextSize(15f);
            tv.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
            tv.setPadding(0, 10, 0, 10);
            activeLyricsContainer.addView(tv);
        }
        updateActiveLyricsHighlight();
    }

    private void updateActiveLyricsHighlight() {
        if (activeLyricsDialog == null || !activeLyricsDialog.isShowing()
                || activeLyricsContainer == null || activeLyricLines.isEmpty()
                || mediaController == null || activeTrack == null
                || !activeTrack.getId().equals(activeLyricsTrackId)) {
            return;
        }
        int activeIdx = LrcParser.findActiveLineIndex(activeLyricLines, mediaController.getCurrentPosition());
        if (activeIdx == lastHighlightedLyricIdx || activeIdx < 0) return;
        lastHighlightedLyricIdx = activeIdx;

        for (int i = 0; i < activeLyricsContainer.getChildCount(); i++) {
            View child = activeLyricsContainer.getChildAt(i);
            if (child instanceof TextView) {
                TextView tv = (TextView) child;
                boolean isCurrent = (i == activeIdx);
                tv.setTextColor(ContextCompat.getColor(
                        this, isCurrent ? R.color.neon_cyan : R.color.text_secondary));
                tv.setTextSize(isCurrent ? 17.5f : 15f);
                if (isCurrent && activeLyricsScroll != null) {
                    int targetY = Math.max(0, tv.getTop() - activeLyricsScroll.getHeight() / 3);
                    activeLyricsScroll.smoothScrollTo(0, targetY);
                }
            }
        }
    }

    private String readTextFromUri(Uri uri) {
        try (InputStream is = getContentResolver().openInputStream(uri)) {
            if (is == null) return null;
            BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
            return sb.toString();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void updateFavoriteButton(boolean isFavorite) {
        btnFavorite.setImageResource(isFavorite ? R.drawable.ic_heart_filled : R.drawable.ic_heart_outline);
        if (uiStyleController != null) {
            uiStyleController.updateFavoriteUi(isFavorite);
        }
        syncReactivePlaybackFlags();
    }

    private void updateShuffleAndRepeatUi() {
        if (mediaController == null) return;
        boolean shuffleOn = mediaController.getShuffleModeEnabled();
        btnShuffle.setColorFilter(shuffleOn
                ? currentPalette.secondaryNeon
                : ContextCompat.getColor(this, R.color.text_secondary));

        int repeatMode = mediaController.getRepeatMode();
        int desiredIcon = repeatMode == Player.REPEAT_MODE_ONE ? R.drawable.ic_repeat_one : R.drawable.ic_repeat;
        if (desiredIcon != lastRepeatIconRes) {
            lastRepeatIconRes = desiredIcon;
            btnRepeat.setImageResource(desiredIcon);
        }
        if (repeatMode == Player.REPEAT_MODE_ONE) {
            btnRepeat.setColorFilter(currentPalette.primaryNeon);
        } else if (repeatMode == Player.REPEAT_MODE_ALL) {
            btnRepeat.setColorFilter(currentPalette.secondaryNeon);
        } else {
            btnRepeat.setColorFilter(ContextCompat.getColor(this, R.color.text_secondary));
        }

        if (uiStyleController != null) {
            uiStyleController.updateShuffleAndRepeatUi(shuffleOn, repeatMode);
        }
        syncReactivePlaybackFlags();
    }

    private void extractAndApplyDynamicTheme(String trackId, Bitmap bitmap) {
        if (visualMetersEnabled) {
            return;
        }
        if (!fxEngine.isDynamicThemeEnabled() || bitmap == null || bitmap.isRecycled()) {
            animateToPalette(NeonPalette.DEFAULT);
            return;
        }
        paletteExecutor.execute(() -> {
            NeonPalette extracted = AlbumColorExtractor.extract(bitmap);
            mainHandler.post(() -> {
                if (!isFinishing() && !isDestroyed()
                        && !visualMetersEnabled
                        && activeTrack != null && trackId.equals(activeTrack.getId())
                        && fxEngine.isDynamicThemeEnabled()) {
                    animateToPalette(extracted);
                }
            });
        });
    }

    private void animateToPalette(NeonPalette target) {
        final NeonPalette safeTarget = target != null ? target : NeonPalette.DEFAULT;
        if (safeTarget.primaryNeon == currentPalette.primaryNeon
                && safeTarget.secondaryNeon == currentPalette.secondaryNeon
                && safeTarget.tertiaryGlow == currentPalette.tertiaryGlow
                && safeTarget.canvasStart == currentPalette.canvasStart) {
            return;
        }
        if (themeAnimator != null) {
            themeAnimator.cancel();
        }
        final NeonPalette start = currentPalette;
        trackAdapter.setThemePalette(safeTarget);
        presetAdapter.setThemePalette(safeTarget);
        waveformView.setPaletteColors(safeTarget.primaryNeon, safeTarget.secondaryNeon, safeTarget.tertiaryGlow);
        equalizerView.setPaletteColors(safeTarget.secondaryNeon, safeTarget.tertiaryGlow, safeTarget.primaryNeon);

        if (resourceOptimizer != null && !resourceOptimizer.shouldAnimateThemeTransitions()) {
            applyDynamicPalette(safeTarget, true);
            return;
        }

        themeAnimator = ValueAnimator.ofFloat(0f, 1f);
        themeAnimator.setDuration(380L);
        themeAnimator.addUpdateListener(anim -> {
            float frac = (float) anim.getAnimatedValue();
            applyDynamicPalette(NeonPalette.lerp(start, safeTarget, frac), false);
        });
        themeAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                applyDynamicPalette(safeTarget, true);
            }
        });
        themeAnimator.start();
    }

    private void applyDynamicPalette(NeonPalette palette, boolean updateChildControls) {
        currentPalette = palette != null ? palette : NeonPalette.DEFAULT;
        float density = getResources().getDisplayMetrics().density;

        boolean darkMode = uiStyleController == null || uiStyleController.getCurrentMode().isDarkCanvas;
        if (darkMode) {
            DynamicThemeRenderer.applyCosmicCanvas(rootScroll, currentPalette, density);
            DynamicThemeRenderer.applyHeroCard(heroPlayerCard, currentPalette, density);
            DynamicThemeRenderer.applySubCard(cardEqPresets, currentPalette, density);
            DynamicThemeRenderer.applySubCard(cardAudioFrequency, currentPalette, density);
            DynamicThemeRenderer.applySubCard(cardUpNext, currentPalette, density);
            DynamicThemeRenderer.applyPlayButton(btnPlayPause, currentPalette, density);
            DynamicThemeRenderer.applySeekBarColors(seekBarProgress, currentPalette);
            DynamicThemeRenderer.applySeekBarColors(seekHeroBassDrive, currentPalette);
            DynamicThemeRenderer.applyAlbumArtStroke(heroAlbumArt, currentPalette);
            DynamicThemeRenderer.applySwitchColors(switchEqEnabled, currentPalette);
            DynamicThemeRenderer.applySubtitlePill(this, badgeBrandSubtitle, currentPalette);
            DynamicThemeRenderer.applyAccentTextColor(badgeEqMode, currentPalette.secondaryNeon);
            DynamicThemeRenderer.applyAccentTextColor(btnHeroLyrics, currentPalette.secondaryNeon);
            DynamicThemeRenderer.applyAccentTextColor(textHeroBassPercent, currentPalette.secondaryNeon);
        }

        if (neonEdgeGlowView != null) {
            neonEdgeGlowView.setEdgeColors(currentPalette.primaryNeon, currentPalette.secondaryNeon);
        }
        if (glassNavBar != null) {
            glassNavBar.setLightCanvas(!darkMode);
            glassNavBar.setBackdropBaseColor(darkMode ? currentPalette.canvasEnd : 0xFFEFF6FF);
            glassNavBar.setGlassTint(darkMode ? NeonPalette.withAlpha(currentPalette.canvasCenter, 0x60) : 0x55FFFFFF);
            glassNavBar.setAccentColor(darkMode ? currentPalette.secondaryNeon : 0xFF4F46E5);
        }
        if (proStudioRackView != null) {
            proStudioRackView.setPaletteColors(
                    currentPalette.secondaryNeon, currentPalette.tertiaryGlow, currentPalette.primaryNeon);
        }
        if (cinematicStageDialog != null && cinematicStageDialog.isShowing()) {
            cinematicStageDialog.updatePalette(currentPalette);
        }
        if (updateChildControls) {
            if (uiStyleController != null) {
                uiStyleController.applyDarkPalette(currentPalette, density);
            }
            updateShuffleAndRepeatUi();
            updateFilterTabsUi();
            updateAbLoopUi();
        }
    }

    private void handleAbLoopTap() {
        if (mediaController == null || activeTrack == null || mediaController.getMediaItemCount() == 0) {
            Toast.makeText(this, "Play a track first to set an A-B loop", Toast.LENGTH_SHORT).show();
            return;
        }
        long pos = Math.max(0L, mediaController.getCurrentPosition());
        if (loopStartMs < 0L) {
            loopStartMs = pos;
            loopEndMs = -1L;
            updateAbLoopUi();
        } else if (loopEndMs < 0L) {
            if (pos > loopStartMs + 300L) {
                loopEndMs = pos;
                mediaController.seekTo(loopStartMs);
                updateAbLoopUi();
            } else {
                Toast.makeText(this, "Seek past Point A first", Toast.LENGTH_SHORT).show();
            }
        } else {
            resetAbLoop();
        }
    }

    private void resetAbLoop() {
        loopStartMs = -1L;
        loopEndMs = -1L;
        updateAbLoopUi();
    }

    private void updateAbLoopUi() {
        if (btnAbLoop == null) return;
        float density = getResources().getDisplayMetrics().density;
        if (loopStartMs >= 0L && loopEndMs > loopStartMs) {
            btnAbLoop.setText("A⇄B");
            DynamicThemeRenderer.applySelectedTileBackground(btnAbLoop, currentPalette, density, 14f);
            btnAbLoop.setTextColor(0xFFFFFFFF);
            lastAbLoopState = 2;
        } else if (loopStartMs >= 0L) {
            btnAbLoop.setText("A: " + TimeFormatter.formatDuration(loopStartMs));
            DynamicThemeRenderer.applySelectedTileBackground(btnAbLoop, currentPalette, density, 14f);
            btnAbLoop.setTextColor(0xFFFFFFFF);
            lastAbLoopState = 1;
        } else if (lastAbLoopState != 0) {
            lastAbLoopState = 0;
            btnAbLoop.setText(R.string.badge_ab_loop);
            btnAbLoop.setBackgroundResource(R.drawable.bg_pill_badge);
            btnAbLoop.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        } else {
            btnAbLoop.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        }
        syncProStudioRackUi();
    }

    private void updatePlaybackProgress() {
        if (mediaController == null || isUserSeeking) return;
        long duration = mediaController.getDuration();
        long position = Math.max(0L, mediaController.getCurrentPosition());
        if (loopStartMs >= 0L && loopEndMs > loopStartMs && position >= loopEndMs) {
            mediaController.seekTo(loopStartMs);
            position = loopStartMs;
        }
        if (duration > 0) {
            if (duration != lastProgressDurMs) {
                lastProgressDurMs = duration;
                seekBarProgress.setMax((int) duration);
            }
            long totalSec = duration / 1000L;
            if (totalSec != lastProgressTotalSec) {
                lastProgressTotalSec = totalSec;
                textTotalTime.setText(TimeFormatter.formatDuration(duration));
            }
        }
        seekBarProgress.setProgress((int) position);
        long posSec = position / 1000L;
        if (posSec != lastProgressPosSec) {
            lastProgressPosSec = posSec;
            textCurrentTime.setText(TimeFormatter.formatDuration(position));
        }
        long nowMs = android.os.SystemClock.uptimeMillis();
        if (activeTrack != null && mediaController.isPlaying() && (nowMs - lastPerTrackSaveMs) >= 2000L) {
            lastPerTrackSaveMs = nowMs;
            repository.saveTrackResumePosition(activeTrack.getId(), position, duration);
            repository.saveLastPlaybackState(activeTrack.getId(), position);
        }
        if (uiStyleController != null) {
            uiStyleController.updateProgress(position, duration);
        }
        if (driveModeDialog != null && driveModeDialog.isShowing()) {
            if (duration > 0 && driveSeekBar != null) {
                if (driveSeekBar.getMax() != (int) duration) {
                    driveSeekBar.setMax((int) duration);
                }
                if (driveTotalTime != null) {
                    driveTotalTime.setText(TimeFormatter.formatDuration(duration));
                }
            }
            if (driveSeekBar != null) {
                driveSeekBar.setProgress((int) position);
            }
            if (driveCurrentTime != null) {
                driveCurrentTime.setText(TimeFormatter.formatDuration(position));
            }
        }
        if (cinematicStageDialog != null && cinematicStageDialog.isShowing()) {
            cinematicStageDialog.updateProgress(position, duration);
            if (!stageLyricLines.isEmpty() && activeTrack != null && activeTrack.getId().equals(stageLyricsTrackId)) {
                int idx = LrcParser.findActiveLineIndex(stageLyricLines, position);
                if (idx >= 0 && idx < stageLyricLines.size()) {
                    cinematicStageDialog.updateLyricLine(stageLyricLines.get(idx).text);
                }
            }
        }
    }

    private void syncControllerStateToUi() {
        if (mediaController == null) return;
        boolean playing = mediaController != null && mediaController.isPlaying();
        btnPlayPause.setImageResource(playing ? R.drawable.ic_pause : R.drawable.ic_play);
        if (stickyMiniPlayPause != null) {
            stickyMiniPlayPause.setImageResource(playing ? R.drawable.ic_pause : R.drawable.ic_play);
        }
        if (driveModeDialog != null && driveModeDialog.isShowing() && drivePlayPauseBtn != null) {
            drivePlayPauseBtn.setImageResource(playing ? R.drawable.ic_pause : R.drawable.ic_play);
        }
        if (cinematicStageDialog != null && cinematicStageDialog.isShowing()) {
            cinematicStageDialog.updatePlayingState(playing);
        }
        waveformView.setPlaying(playing);
        if (uiStyleController != null) {
            uiStyleController.updatePlayingState(playing);
        }
        updateVisualMetersPlayingState(playing);
        updateShuffleAndRepeatUi();

        MediaItem currentItem = mediaController.getCurrentMediaItem();
        if (currentItem != null) {
            for (Track t : allTracks) {
                if (t.getId().equals(currentItem.mediaId)) {
                    bindHeroTrack(t);
                    break;
                }
            }
        }
        if (activeTrack != null) {
            trackAdapter.setPlaybackState(activeTrack.getId(), playing);
        }
        updatePlaybackProgress();
    }

    private void showOptionsBottomSheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(this, R.style.NeonBottomSheetTheme);
        View sheetView = getLayoutInflater().inflate(R.layout.bottom_sheet_options, null);
        dialog.setContentView(sheetView);

        View btnUiStudioSheet = sheetView.findViewById(R.id.sheet_btn_ui_studio);
        if (btnUiStudioSheet != null) {
            btnUiStudioSheet.setOnClickListener(v -> {
                dialog.dismiss();
                openUiStyleStudio();
            });
        }

        View btnYoutubeSheet = sheetView.findViewById(R.id.sheet_btn_youtube_music);
        if (btnYoutubeSheet != null) {
            btnYoutubeSheet.setOnClickListener(v -> {
                dialog.dismiss();
                openYoutubeMusicDialog(null);
            });
        }

        View btnDriveSheet = sheetView.findViewById(R.id.sheet_btn_drive_mode);
        if (btnDriveSheet != null) {
            btnDriveSheet.setOnClickListener(v -> {
                dialog.dismiss();
                showDriveModeDialog();
            });
        }
        View btnLabSheet = sheetView.findViewById(R.id.sheet_btn_audio_lab);
        if (btnLabSheet != null) {
            btnLabSheet.setOnClickListener(v -> {
                dialog.dismiss();
                showAudioLabInsightsDialog();
            });
        }
        View btnStage3dSheet = sheetView.findViewById(R.id.sheet_btn_stage_3d);
        if (btnStage3dSheet != null) {
            btnStage3dSheet.setOnClickListener(v -> {
                dialog.dismiss();
                openCinematicStageDialog();
            });
        }
        View btnHarmonicNextSheet = sheetView.findViewById(R.id.sheet_btn_harmonic_next);
        if (btnHarmonicNextSheet != null) {
            btnHarmonicNextSheet.setOnClickListener(v -> {
                dialog.dismiss();
                findAndPlayHarmonicNextTrack();
            });
        }
        View btnStoryCard = sheetView.findViewById(R.id.sheet_btn_story_card);
        if (btnStoryCard != null) {
            btnStoryCard.setOnClickListener(v -> {
                dialog.dismiss();
                if (activeTrack != null) {
                    boolean shared = NowPlayingCardExporter.shareCard(
                            this,
                            activeTrack,
                            currentAlbumBitmap,
                            currentPalette,
                            getString(fxEngine.getCurrentPreset().getTitleRes()),
                            fxEngine.getBassBoostPercent(),
                            getString(visualMeterMode.shortTitleRes));
                    if (!shared) {
                        Toast.makeText(this, "Could not generate story card", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(this, R.string.default_track_artist, Toast.LENGTH_SHORT).show();
                }
            });
        }

        sheetView.findViewById(R.id.sheet_btn_lyrics).setOnClickListener(v -> {
            dialog.dismiss();
            if (activeTrack != null) showLyricsDialog(activeTrack);
        });
        sheetView.findViewById(R.id.sheet_btn_add_playlist).setOnClickListener(v -> {
            dialog.dismiss();
            if (activeTrack != null) showPlaylistPickerDialog(activeTrack);
        });
        sheetView.findViewById(R.id.sheet_btn_share).setOnClickListener(v -> {
            dialog.dismiss();
            if (activeTrack != null) shareAudioTrack(activeTrack);
        });

        sheetView.findViewById(R.id.sheet_btn_jump_back).setOnClickListener(v -> {
            if (mediaController != null && mediaController.getMediaItemCount() > 0) {
                long target = Math.max(0L, mediaController.getCurrentPosition() - 10_000L);
                mediaController.seekTo(target);
                updatePlaybackProgress();
            }
        });
        sheetView.findViewById(R.id.sheet_btn_jump_fwd).setOnClickListener(v -> {
            if (mediaController != null && mediaController.getMediaItemCount() > 0) {
                long dur = mediaController.getDuration();
                long next = mediaController.getCurrentPosition() + 10_000L;
                long target = dur > 0 ? Math.min(dur, next) : next;
                mediaController.seekTo(target);
                updatePlaybackProgress();
            }
        });

        // Pro FX Sliders
        TextView bassLabel = sheetView.findViewById(R.id.label_bass_boost);
        SeekBar seekBass = sheetView.findViewById(R.id.seek_bass_boost);
        seekBass.setProgress(fxEngine.getBassBoostPercent());
        bassLabel.setText(getString(R.string.fx_bass_boost) + ": " + fxEngine.getBassBoostPercent() + "%");
        seekBass.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int p, boolean fromUser) {
                bassLabel.setText(getString(R.string.fx_bass_boost) + ": " + p + "%");
                if (fromUser) {
                    fxEngine.setBassBoostPercent(p);
                    syncHeroBassDriveUi();
                    if (uiStyleController != null) {
                        uiStyleController.syncRetroKnobsFromSystemAndFx();
                    }
                }
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });

        TextView virtLabel = sheetView.findViewById(R.id.label_virtualizer);
        SeekBar seekVirt = sheetView.findViewById(R.id.seek_virtualizer);
        seekVirt.setProgress(fxEngine.getVirtualizerPercent());
        virtLabel.setText(getString(R.string.fx_virtualizer) + ": " + fxEngine.getVirtualizerPercent() + "%");
        seekVirt.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int p, boolean fromUser) {
                virtLabel.setText(getString(R.string.fx_virtualizer) + ": " + p + "%");
                if (fromUser) fxEngine.setVirtualizerPercent(p);
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });

        TextView loudLabel = sheetView.findViewById(R.id.label_loudness);
        SeekBar seekLoud = sheetView.findViewById(R.id.seek_loudness);
        seekLoud.setProgress(fxEngine.getLoudnessPercent());
        loudLabel.setText(getString(R.string.fx_loudness) + ": " + fxEngine.getLoudnessPercent() + "%");
        seekLoud.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int p, boolean fromUser) {
                loudLabel.setText(getString(R.string.fx_loudness) + ": " + p + "%");
                if (fromUser) fxEngine.setLoudnessPercent(p);
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });

        TextView balanceLabel = sheetView.findViewById(R.id.label_balance_lr);
        SeekBar seekBalance = sheetView.findViewById(R.id.seek_balance_lr);
        if (seekBalance != null && balanceLabel != null) {
            int bal = fxEngine.getBalancePercent();
            seekBalance.setProgress(bal + 100);
            balanceLabel.setText(formatBalanceLabel(bal));
            seekBalance.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(SeekBar sb, int p, boolean fromUser) {
                    int b = p - 100;
                    balanceLabel.setText(formatBalanceLabel(b));
                    if (fromUser) fxEngine.setBalancePercent(b);
                }
                @Override public void onStartTrackingTouch(SeekBar sb) {}
                @Override public void onStopTrackingTouch(SeekBar sb) {}
            });
        }

        MaterialSwitch switchSpatial8d = sheetView.findViewById(R.id.switch_spatial_8d);
        if (switchSpatial8d != null) {
            switchSpatial8d.setChecked(fxEngine.isSpatial8dEnabled());
            switchSpatial8d.setOnCheckedChangeListener((b, checked) -> fxEngine.setSpatial8dEnabled(checked));
        }

        MaterialSwitch switchAutoDeviceEq = sheetView.findViewById(R.id.switch_auto_device_eq);
        if (switchAutoDeviceEq != null) {
            switchAutoDeviceEq.setChecked(fxEngine.isAutoDeviceEqEnabled());
            switchAutoDeviceEq.setOnCheckedChangeListener((b, checked) -> fxEngine.setAutoDeviceEqEnabled(checked));
        }

        MaterialSwitch switchHapticBass = sheetView.findViewById(R.id.switch_haptic_bass);
        if (switchHapticBass != null) {
            switchHapticBass.setChecked(fxEngine.isHapticBassEnabled());
            switchHapticBass.setOnCheckedChangeListener((b, checked) -> fxEngine.setHapticBassEnabled(checked));
        }

        MaterialSwitch switchEdgeGlow = sheetView.findViewById(R.id.switch_edge_glow);
        if (switchEdgeGlow != null) {
            switchEdgeGlow.setChecked(fxEngine.isEdgeGlowEnabled());
            switchEdgeGlow.setOnCheckedChangeListener((b, checked) -> {
                fxEngine.setEdgeGlowEnabled(checked);
                updateVisualMetersPlayingState(mediaController != null && mediaController.isPlaying());
            });
        }

        MaterialSwitch switchColorPulseButtons = sheetView.findViewById(R.id.switch_color_pulse_buttons);
        if (switchColorPulseButtons != null) {
            switchColorPulseButtons.setChecked(uiStylePreferences.isColorPulseButtonsEnabled());
            switchColorPulseButtons.setOnCheckedChangeListener((b, checked) -> {
                uiStylePreferences.setColorPulseButtonsEnabled(checked);
                if (reactiveButtonAnimator != null) {
                    reactiveButtonAnimator.setColorPulseEnabled(checked);
                }
            });
        }

        MaterialSwitch switchGlassNavBar = sheetView.findViewById(R.id.switch_glass_nav_bar);
        if (switchGlassNavBar != null) {
            switchGlassNavBar.setChecked(uiStylePreferences.isGlassNavBarEnabled());
            switchGlassNavBar.setOnCheckedChangeListener((b, checked) -> {
                uiStylePreferences.setGlassNavBarEnabled(checked);
                updateBottomDockLayoutMargins();
            });
        }

        MaterialSwitch switchSkipSilence = sheetView.findViewById(R.id.switch_skip_silence);
        switchSkipSilence.setChecked(fxEngine.isSkipSilenceEnabled());
        switchSkipSilence.setOnCheckedChangeListener((buttonView, isChecked) -> {
            fxEngine.setSkipSilenceEnabled(isChecked);
            syncAudioServicePrefs();
        });

        MaterialSwitch switchSmoothFade = sheetView.findViewById(R.id.switch_smooth_fade);
        switchSmoothFade.setChecked(fxEngine.isSmoothFadeEnabled());
        switchSmoothFade.setOnCheckedChangeListener((buttonView, isChecked) ->
                fxEngine.setSmoothFadeEnabled(isChecked));

        MaterialSwitch switchDynamicTheme = sheetView.findViewById(R.id.switch_dynamic_theme);
        switchDynamicTheme.setChecked(fxEngine.isDynamicThemeEnabled());
        switchDynamicTheme.setOnCheckedChangeListener((buttonView, isChecked) -> {
            fxEngine.setDynamicThemeEnabled(isChecked);
            if (isChecked && activeTrack != null && currentAlbumBitmap != null && !currentAlbumBitmap.isRecycled()) {
                extractAndApplyDynamicTheme(activeTrack.getId(), currentAlbumBitmap);
            } else {
                animateToPalette(NeonPalette.DEFAULT);
            }
        });

        MaterialSwitch switchAutoResume = sheetView.findViewById(R.id.switch_auto_resume);
        if (switchAutoResume != null) {
            switchAutoResume.setChecked(repository.isAutoResumeEnabled());
            switchAutoResume.setOnCheckedChangeListener((b, checked) -> repository.setAutoResumeEnabled(checked));
        }

        ChipGroup crossfadeGroup = sheetView.findViewById(R.id.chip_group_crossfade);
        if (crossfadeGroup != null) {
            int cf = fxEngine.getCrossfadeSeconds();
            if (cf == 2) ((Chip) sheetView.findViewById(R.id.chip_cf_2)).setChecked(true);
            else if (cf == 4) ((Chip) sheetView.findViewById(R.id.chip_cf_4)).setChecked(true);
            else if (cf == 6) ((Chip) sheetView.findViewById(R.id.chip_cf_6)).setChecked(true);
            else if (cf >= 10) ((Chip) sheetView.findViewById(R.id.chip_cf_10)).setChecked(true);
            else ((Chip) sheetView.findViewById(R.id.chip_cf_0)).setChecked(true);

            crossfadeGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
                if (checkedIds.isEmpty()) return;
                int id = checkedIds.get(0);
                int sec = 0;
                if (id == R.id.chip_cf_2) sec = 2;
                else if (id == R.id.chip_cf_4) sec = 4;
                else if (id == R.id.chip_cf_6) sec = 6;
                else if (id == R.id.chip_cf_10) sec = 10;
                fxEngine.setCrossfadeSeconds(sec);
                syncAudioServicePrefs();
            });
        }

        // Speed & Pitch
        ChipGroup speedGroup = sheetView.findViewById(R.id.chip_group_speed);
        if (currentPlaybackSpeed == 0.75f) ((Chip) sheetView.findViewById(R.id.chip_speed_075)).setChecked(true);
        else if (currentPlaybackSpeed == 1.25f) ((Chip) sheetView.findViewById(R.id.chip_speed_125)).setChecked(true);
        else if (currentPlaybackSpeed == 1.5f) ((Chip) sheetView.findViewById(R.id.chip_speed_150)).setChecked(true);
        else ((Chip) sheetView.findViewById(R.id.chip_speed_100)).setChecked(true);

        speedGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            int id = checkedIds.get(0);
            if (id == R.id.chip_speed_075) currentPlaybackSpeed = 0.75f;
            else if (id == R.id.chip_speed_125) currentPlaybackSpeed = 1.25f;
            else if (id == R.id.chip_speed_150) currentPlaybackSpeed = 1.5f;
            else currentPlaybackSpeed = 1.0f;
            applyPlaybackSpeedPitch();
            syncProStudioRackUi();
        });

        ChipGroup pitchGroup = sheetView.findViewById(R.id.chip_group_pitch);
        if (currentPlaybackPitch == 0.85f) ((Chip) sheetView.findViewById(R.id.chip_pitch_085)).setChecked(true);
        else if (currentPlaybackPitch == 1.15f) ((Chip) sheetView.findViewById(R.id.chip_pitch_115)).setChecked(true);
        else ((Chip) sheetView.findViewById(R.id.chip_pitch_100)).setChecked(true);

        pitchGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            int id = checkedIds.get(0);
            if (id == R.id.chip_pitch_085) currentPlaybackPitch = 0.85f;
            else if (id == R.id.chip_pitch_115) currentPlaybackPitch = 1.15f;
            else currentPlaybackPitch = 1.0f;
            applyPlaybackSpeedPitch();
            syncProStudioRackUi();
        });

        ChipGroup sleepGroup = sheetView.findViewById(R.id.chip_group_sleep);
        if (sleepMinutesSelected == 15) ((Chip) sheetView.findViewById(R.id.chip_sleep_15)).setChecked(true);
        else if (sleepMinutesSelected == 30) ((Chip) sheetView.findViewById(R.id.chip_sleep_30)).setChecked(true);
        else if (sleepMinutesSelected == 60) ((Chip) sheetView.findViewById(R.id.chip_sleep_60)).setChecked(true);
        else ((Chip) sheetView.findViewById(R.id.chip_sleep_off)).setChecked(true);

        sleepGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            mainHandler.removeCallbacks(sleepTimerRunnable);
            cancelSleepFadeIfActive();
            if (checkedIds.isEmpty()) return;
            int id = checkedIds.get(0);
            if (id == R.id.chip_sleep_15) sleepMinutesSelected = 15;
            else if (id == R.id.chip_sleep_30) sleepMinutesSelected = 30;
            else if (id == R.id.chip_sleep_60) sleepMinutesSelected = 60;
            else sleepMinutesSelected = 0;

            if (sleepMinutesSelected > 0) {
                mainHandler.postDelayed(sleepTimerRunnable, sleepMinutesSelected * 60_000L);
            }
        });

        // Media & Adaptive Optimization Controls
        TextView optimizerStatusText = sheetView.findViewById(R.id.sheet_optimizer_status_text);
        updateOptimizerTelemetryText(optimizerStatusText);

        ChipGroup optModeGroup = sheetView.findViewById(R.id.chip_group_opt_mode);
        DeviceResourceOptimizer.OptimizationMode activeOptMode = resourceOptimizer.getMode();
        if (activeOptMode == DeviceResourceOptimizer.OptimizationMode.PERFORMANCE) {
            ((Chip) sheetView.findViewById(R.id.chip_opt_performance)).setChecked(true);
        } else if (activeOptMode == DeviceResourceOptimizer.OptimizationMode.ECO_BATTERY) {
            ((Chip) sheetView.findViewById(R.id.chip_opt_eco)).setChecked(true);
        } else {
            ((Chip) sheetView.findViewById(R.id.chip_opt_auto)).setChecked(true);
        }
        optModeGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            int id = checkedIds.get(0);
            if (id == R.id.chip_opt_performance) {
                resourceOptimizer.setMode(DeviceResourceOptimizer.OptimizationMode.PERFORMANCE);
            } else if (id == R.id.chip_opt_eco) {
                resourceOptimizer.setMode(DeviceResourceOptimizer.OptimizationMode.ECO_BATTERY);
            } else {
                resourceOptimizer.setMode(DeviceResourceOptimizer.OptimizationMode.AUTO);
            }
            updateOptimizerTelemetryText(optimizerStatusText);
            syncAudioServicePrefs();
        });

        MaterialSwitch switchAntiStutter = sheetView.findViewById(R.id.switch_anti_stutter);
        switchAntiStutter.setChecked(resourceOptimizer.isMediaAntiStutterEnabled());
        switchAntiStutter.setOnCheckedChangeListener((buttonView, isChecked) -> {
            resourceOptimizer.setMediaAntiStutterEnabled(isChecked);
            updateOptimizerTelemetryText(optimizerStatusText);
            syncAudioServicePrefs();
        });

        MaterialSwitch switchAudioOffload = sheetView.findViewById(R.id.switch_audio_offload);
        switchAudioOffload.setChecked(resourceOptimizer.isAudioOffloadPreferred());
        switchAudioOffload.setOnCheckedChangeListener((buttonView, isChecked) -> {
            resourceOptimizer.setAudioOffloadPreferred(isChecked);
            updateOptimizerTelemetryText(optimizerStatusText);
            syncAudioServicePrefs();
        });

        sheetView.findViewById(R.id.sheet_btn_optimize_ram).setOnClickListener(v -> {
            if (themeAnimator != null && themeAnimator.isRunning()) {
                themeAnimator.cancel();
            }
            repository.clearArtCache();
            resourceOptimizer.triggerManualMemoryOptimization();
            updateOptimizerTelemetryText(optimizerStatusText);
            Toast.makeText(this, R.string.opt_cache_cleaned_toast, Toast.LENGTH_SHORT).show();
        });

        MaterialButton btnRestoreRemoved = sheetView.findViewById(R.id.sheet_btn_restore_removed);
        if (btnRestoreRemoved != null) {
            int hiddenCount = repository.getHiddenTrackCount();
            if (hiddenCount > 0) {
                btnRestoreRemoved.setVisibility(View.VISIBLE);
                btnRestoreRemoved.setText(getString(R.string.btn_restore_removed_tracks, hiddenCount));
                btnRestoreRemoved.setOnClickListener(v -> {
                    repository.unhideAllTracks();
                    dialog.dismiss();
                    refreshLibrary();
                    Toast.makeText(this, R.string.toast_tracks_restored, Toast.LENGTH_SHORT).show();
                });
            } else {
                btnRestoreRemoved.setVisibility(View.GONE);
            }
        }

        TextView trackInfoText = sheetView.findViewById(R.id.sheet_track_info_text);
        if (activeTrack != null) {
            trackInfoText.setText(String.format(Locale.US,
                    "Title: %s\nArtist: %s\nFolder: %s\nFormat: %s  (%s)\nBitrate: %s  •  Sample Rate: %d Hz\nSize: %s",
                    activeTrack.getTitle(),
                    activeTrack.getArtist(),
                    activeTrack.getFolderName(),
                    activeTrack.getFormat(),
                    activeTrack.getMimeType(),
                    activeTrack.getBitrateBadgeText(),
                    activeTrack.getSampleRateHz(),
                    TimeFormatter.formatFileSize(activeTrack.getSizeBytes())
            ));
        } else {
            trackInfoText.setText("No track loaded yet. Pick any MP3, FLAC, WAV, AAC, M4A, OGG, or OPUS file.");
        }

        TextView permissionsText = sheetView.findViewById(R.id.sheet_permissions_text);
        permissionsText.setText(String.format(Locale.US,
                "Audio Library Access: %s\nMedia Notifications: %s\nLive Audio Visualizer FFT: %s",
                PermissionHelper.hasAudioReadPermission(this) ? "Granted" : "Not Granted",
                PermissionHelper.hasNotificationPermission(this) ? "Granted" : "Not Granted",
                PermissionHelper.hasRecordAudioPermission(this) ? "Granted (Hardware FFT)" : "Optional (Smooth Wave Fallback)"
        ));

        MaterialButton btnGrantSheet = sheetView.findViewById(R.id.sheet_btn_request_all_perms);
        String[] missing = PermissionHelper.getAllMissingPermissions(this, true);
        btnGrantSheet.setVisibility(missing.length > 0 ? View.VISIBLE : View.GONE);
        btnGrantSheet.setOnClickListener(v -> {
            dialog.dismiss();
            requestPermissionsWithVisualizer();
        });

        dialog.show();
    }

    private void syncAudioServicePrefs() {
        if (mediaController != null) {
            mediaController.sendCustomCommand(
                    new SessionCommand(NeonAudioService.CMD_SYNC_AUDIO_PREFS, Bundle.EMPTY),
                    Bundle.EMPTY);
        }
    }

    private void applyAdaptiveOptimizationProfile() {
        if (resourceOptimizer == null) return;
        int targetFps = resourceOptimizer.getTargetVisualFps();
        if (waveformView != null) {
            waveformView.setTargetFps(targetFps);
            waveformView.setMaxParticleCount(resourceOptimizer.getWaveformParticleCount());
        }
        if (sideLedLeft != null) {
            sideLedLeft.setTargetFps(targetFps);
        }
        if (sideLedRight != null) {
            sideLedRight.setTargetFps(targetFps);
        }
        if (discoVuGlassView != null) {
            discoVuGlassView.setTargetFps(targetFps);
        }
        if (studioQuadMeterView != null) {
            studioQuadMeterView.setTargetFps(targetFps);
        }
        if (vinylModeDiscView != null) {
            vinylModeDiscView.setTargetFps(targetFps);
        }
        if (discoStrobeOverlay != null) {
            discoStrobeOverlay.setTargetFps(Math.min(40, targetFps));
            discoStrobeOverlay.setStrobeFlashesAllowed(resourceOptimizer.shouldAllowStrobeFlashes());
        }
        if (neonEdgeGlowView != null) {
            neonEdgeGlowView.setTargetFps(targetFps);
        }
        if (uiStyleController != null) {
            uiStyleController.setTargetVisualFps(targetFps);
        }
        if (reactiveButtonAnimator != null) {
            reactiveButtonAnimator.setTargetFps(resourceOptimizer.getReactiveButtonFps());
        }
        if (proStudioRackView != null) {
            proStudioRackView.setTargetFps(resourceOptimizer.getStudioRackTargetFps());
            proStudioRackView.setWaterfallHistoryRows(resourceOptimizer.getWaterfallHistoryRows());
        }
        if (cinematicStageDialog != null) {
            cinematicStageDialog.configureOptimization(
                    resourceOptimizer.shouldEnableStageGyroscope(),
                    resourceOptimizer.getStageParticleCount(),
                    resourceOptimizer.getStudioRackTargetFps());
        }
        if (glassNavBar != null) {
            DeviceResourceOptimizer.EffectiveTier tier = resourceOptimizer.getEffectiveTier();
            if (tier == DeviceResourceOptimizer.EffectiveTier.POWER_SAVER) {
                glassNavBar.setAdaptiveCaptureConfig(220L, false);
            } else if (tier == DeviceResourceOptimizer.EffectiveTier.HIGH) {
                glassNavBar.setAdaptiveCaptureConfig(58L, true);
            } else {
                glassNavBar.setAdaptiveCaptureConfig(120L, true);
            }
        }
        fxEngine.updateAdaptiveVisualizerRate(this);
        updateVisualMetersPlayingState(mediaController != null && mediaController.isPlaying());
    }

    private void updateVisualMetersUi() {
        boolean collapsedMini = uiStyleController != null
                && uiStyleController.getCurrentMode() == UiStyleMode.FLOATING_MINI
                && uiStyleController.isFloatingCollapsed();

        boolean isDiscoClub = visualMetersEnabled
                && visualMeterMode == UiStylePreferences.VisualMeterMode.DISCO_MODE;
        boolean wasDiscoClub = reactiveButtonAnimator != null && reactiveButtonAnimator.isDiscoClubMode();

        boolean showSideLed = visualMetersEnabled
                && (visualMeterMode == UiStylePreferences.VisualMeterMode.SIDE_LED
                || visualMeterMode == UiStylePreferences.VisualMeterMode.CYBERPUNK
                || isDiscoClub)
                && !collapsedMini;
        boolean showDiscoVu = visualMetersEnabled
                && (visualMeterMode == UiStylePreferences.VisualMeterMode.DISCO_VU
                || visualMeterMode == UiStylePreferences.VisualMeterMode.PARTY_STROBE
                || isDiscoClub);
        boolean showStudioQuad = visualMetersEnabled
                && visualMeterMode == UiStylePreferences.VisualMeterMode.STUDIO;
        boolean showVinylCenter = visualMetersEnabled
                && visualMeterMode == UiStylePreferences.VisualMeterMode.VINYL;
        boolean showPartyStrobe = visualMetersEnabled
                && (visualMeterMode == UiStylePreferences.VisualMeterMode.PARTY_STROBE
                || isDiscoClub);

        if (sideLedLeft != null) {
            sideLedLeft.setLedColorScheme(
                    visualMeterMode == UiStylePreferences.VisualMeterMode.CYBERPUNK
                            ? SideLedMeterView.LedColorScheme.CYBERPUNK_YELLOW_CYAN
                            : SideLedMeterView.LedColorScheme.BLUE_CYAN);
        }
        if (sideLedRight != null) {
            sideLedRight.setLedColorScheme(
                    visualMeterMode == UiStylePreferences.VisualMeterMode.CYBERPUNK
                            ? SideLedMeterView.LedColorScheme.CYBERPUNK_YELLOW_CYAN
                            : SideLedMeterView.LedColorScheme.BLUE_CYAN);
        }

        boolean showProRack = visualMetersEnabled && (showDiscoVu || showStudioQuad);

        ReactiveButtonAnimator.animateMeterReveal(sideLedLeft, showSideLed);
        ReactiveButtonAnimator.animateMeterReveal(sideLedRight, showSideLed);
        ReactiveButtonAnimator.animateMeterReveal(discoVuGlassView, showDiscoVu);
        ReactiveButtonAnimator.animateMeterReveal(purificationSuiteView, showDiscoVu);
        ReactiveButtonAnimator.animateMeterReveal(proStudioRackView, showProRack);
        ReactiveButtonAnimator.animateMeterReveal(studioQuadMeterView, showStudioQuad);

        if (waveformView != null) {
            waveformView.setVisibility(showVinylCenter ? View.GONE : View.VISIBLE);
            if (!visualMetersEnabled) {
                waveformView.setVisualMode(NeonWaveformView.VisualMode.SPECTRUM_WAVE);
            } else {
                switch (visualMeterMode) {
                    case CYBERPUNK:
                        waveformView.setVisualMode(NeonWaveformView.VisualMode.HEARTBEAT);
                        break;
                    case CHILL_OCEAN:
                        waveformView.setVisualMode(NeonWaveformView.VisualMode.OCEAN_WAVE);
                        break;
                    case FIRE:
                        waveformView.setVisualMode(NeonWaveformView.VisualMode.FIRE_FLAME);
                        break;
                    case PARTY_STROBE:
                    case DISCO_MODE:
                        waveformView.setVisualMode(NeonWaveformView.VisualMode.MIRROR_PULSE);
                        break;
                    case DISCO_VU:
                    case SIDE_LED:
                    case STUDIO:
                    case VINYL:
                    default:
                        waveformView.setVisualMode(NeonWaveformView.VisualMode.SPECTRUM_WAVE);
                        break;
                }
            }
        }
        if (vinylModeDiscView != null) {
            vinylModeDiscView.setVisibility(showVinylCenter ? View.VISIBLE : View.GONE);
            if (showVinylCenter) {
                vinylModeDiscView.setAlbumBitmap(currentAlbumBitmap);
            }
        }
        if (discoStrobeOverlay != null) {
            discoStrobeOverlay.setDiscoClubMode(isDiscoClub);
            discoStrobeOverlay.setVisibility(showPartyStrobe ? View.VISIBLE : View.GONE);
        }
        if (neonEdgeGlowView != null) {
            neonEdgeGlowView.setRainbowCycle(isDiscoClub);
        }
        if (discoVuGlassView != null) {
            discoVuGlassView.setModeTitle(getString(visualMeterMode.switchTitleRes));
            if (activeTrack != null) {
                discoVuGlassView.setSampleRateHz(activeTrack.getSampleRateHz());
            }
        }
        if (reactiveButtonAnimator != null) {
            reactiveButtonAnimator.setDiscoClubMode(isDiscoClub);
            reactiveButtonAnimator.setPartyStrobeSpeedBoost(showPartyStrobe);
        }

        if (heroPlayerCard != null && heroPlayerCard.getLayoutParams() instanceof android.view.ViewGroup.MarginLayoutParams) {
            android.view.ViewGroup.MarginLayoutParams lp =
                    (android.view.ViewGroup.MarginLayoutParams) heroPlayerCard.getLayoutParams();
            int targetMargin = showSideLed ? Math.round(6 * getResources().getDisplayMetrics().density) : 0;
            if (lp.leftMargin != targetMargin || lp.rightMargin != targetMargin) {
                lp.leftMargin = targetMargin;
                lp.rightMargin = targetMargin;
                heroPlayerCard.setLayoutParams(lp);
            }
        }

        if (textMeterPower != null) {
            textMeterPower.setText(visualMetersEnabled ? R.string.meter_power_on : R.string.meter_power_off);
        }
        if (iconMeterPower != null) {
            iconMeterPower.setColorFilter(visualMetersEnabled
                    ? ContextCompat.getColor(this, R.color.neon_cyan)
                    : ContextCompat.getColor(this, R.color.text_muted));
        }
        if (btnMeterPower != null) {
            btnMeterPower.setAlpha(visualMetersEnabled ? 1.0f : 0.72f);
        }

        if (textMeterSwitch != null) {
            textMeterSwitch.setText(visualMeterMode.switchTitleRes);
        }
        if (iconMeterSwitch != null) {
            iconMeterSwitch.setColorFilter(visualMeterMode.getPalette().primaryNeon);
        }
        if (btnMeterSwitch != null) {
            btnMeterSwitch.setAlpha(visualMetersEnabled ? 1.0f : 0.72f);
        }
        if (btnDiscoModeToggle != null) {
            btnDiscoModeToggle.setAlpha(isDiscoClub ? 1.0f : 0.85f);
        }
        if (textDiscoModeToggle != null) {
            textDiscoModeToggle.setText(isDiscoClub ? R.string.mode_disco_short : R.string.btn_disco_mode_quick);
        }

        if (wasDiscoClub && !isDiscoClub) {
            applyDynamicPalette(currentPalette, true);
            if (glassNavBar != null && glassNavBar.getSelectedIndex() == 1) {
                glassNavBar.select(0, false);
            }
        } else if (isDiscoClub && glassNavBar != null && glassNavBar.getSelectedIndex() != 1) {
            glassNavBar.select(1, false);
        }
        if (visualMetersEnabled) {
            animateToPalette(visualMeterMode.getPalette());
        } else if (fxEngine.isDynamicThemeEnabled() && currentAlbumBitmap != null && !currentAlbumBitmap.isRecycled() && activeTrack != null) {
            extractAndApplyDynamicTheme(activeTrack.getId(), currentAlbumBitmap);
        } else {
            animateToPalette(NeonPalette.DEFAULT);
        }

        boolean playing = mediaController != null && mediaController.isPlaying();
        updateVisualMetersPlayingState(playing);
    }

    private void updateVisualMetersPlayingState(boolean playing) {
        boolean isDiscoClub = visualMetersEnabled
                && visualMeterMode == UiStylePreferences.VisualMeterMode.DISCO_MODE;
        boolean sideActive = playing && visualMetersEnabled
                && (visualMeterMode == UiStylePreferences.VisualMeterMode.SIDE_LED
                || visualMeterMode == UiStylePreferences.VisualMeterMode.CYBERPUNK
                || isDiscoClub);
        boolean discoActive = playing && visualMetersEnabled
                && (visualMeterMode == UiStylePreferences.VisualMeterMode.DISCO_VU
                || visualMeterMode == UiStylePreferences.VisualMeterMode.PARTY_STROBE
                || isDiscoClub);
        boolean studioActive = playing && visualMetersEnabled
                && visualMeterMode == UiStylePreferences.VisualMeterMode.STUDIO;
        boolean vinylActive = playing && visualMetersEnabled
                && visualMeterMode == UiStylePreferences.VisualMeterMode.VINYL;
        boolean strobeActive = playing && visualMetersEnabled
                && (visualMeterMode == UiStylePreferences.VisualMeterMode.PARTY_STROBE
                || isDiscoClub);

        boolean allowStrobeOrEdge = resourceOptimizer == null || resourceOptimizer.shouldAllowStrobeFlashes();
        boolean edgeActive = playing && (fxEngine.isEdgeGlowEnabled() || isDiscoClub) && allowStrobeOrEdge;

        if (sideLedLeft != null) sideLedLeft.setPlaying(sideActive);
        if (sideLedRight != null) sideLedRight.setPlaying(sideActive);
        if (discoVuGlassView != null) discoVuGlassView.setPlaying(discoActive);
        if (purificationSuiteView != null) purificationSuiteView.setPlaying(discoActive);
        if (studioQuadMeterView != null) studioQuadMeterView.setPlaying(studioActive);
        if (vinylModeDiscView != null) vinylModeDiscView.setPlaying(vinylActive);
        if (discoStrobeOverlay != null) discoStrobeOverlay.setActive(strobeActive);
        if (neonEdgeGlowView != null) {
            neonEdgeGlowView.setVisibility(edgeActive ? View.VISIBLE : View.GONE);
            neonEdgeGlowView.setActive(edgeActive);
        }
        syncReactivePlaybackFlags();
    }

    private void syncReactivePlaybackFlags() {
        if (reactiveButtonAnimator == null) return;
        boolean playing = mediaController != null && mediaController.isPlaying();
        boolean fav = activeTrack != null && activeTrack.isFavorite();
        boolean shuf = mediaController != null && mediaController.getShuffleModeEnabled();
        boolean rep = mediaController != null && mediaController.getRepeatMode() != Player.REPEAT_MODE_OFF;
        boolean ab = loopStartMs >= 0L;
        reactiveButtonAnimator.setPlaybackFlags(playing, fav, shuf, rep, ab);
    }

    private void updateOptimizerTelemetryText(TextView statusView) {
        if (statusView == null || resourceOptimizer == null) return;
        DeviceResourceOptimizer.DeviceTelemetry t = resourceOptimizer.evaluateTelemetry();
        int targetFps = resourceOptimizer.getTargetVisualFps();
        int cacheKb = repository != null ? repository.getArtCacheKb() : 0;
        String flags = (t.isThermalThrottled ? "Thermal Guard • " : "")
                + (t.isPowerSaveMode ? "Battery Saver • " : "")
                + (t.isLowRamDevice || t.isSystemLowMemory ? "Low-RAM Guard • " : "");
        String chargeTag = t.isCharging ? "Charging" : "Battery";
        statusView.setText(String.format(Locale.US,
                "Tier: %s  •  FPS Cap: %d Hz (%d Hz Panel)\n%s: %d%%  •  RAM Free: %d / %d MB  •  Heap: %d%%  •  Cache: %d KB\n%sCores: %d  •  FX: %s  •  Offload: %s",
                t.effectiveTier.name(),
                targetFps,
                t.displayRefreshHz,
                chargeTag,
                t.batteryPercent,
                t.availRamMb,
                t.totalRamMb,
                t.appHeapUsedPercent,
                cacheKb,
                flags,
                t.cpuCores,
                resourceOptimizer.shouldAllowStrobeFlashes() ? "Full" : "Eco Lite",
                resourceOptimizer.shouldUseAudioOffload(fxEngine.isEqEnabled()) ? "Active" : "DSP Studio"
        ));
    }

    @Override
    protected void onStart() {
        super.onStart();
        SessionToken sessionToken = new SessionToken(
                this, new ComponentName(this, NeonAudioService.class));
        controllerFuture = new MediaController.Builder(this, sessionToken).buildAsync();
        controllerFuture.addListener(() -> {
            try {
                mediaController = controllerFuture.get();
                mediaController.addListener(playerListener);
                prepareRestoredTrackIfIdle();
                syncControllerStateToUi();
                syncAudioServicePrefs();
            } catch (Exception ignored) {}
        }, ContextCompat.getMainExecutor(this));
    }

    private void startSleepLogarithmicFadeOut() {
        if (mediaController == null || !mediaController.isPlaying()) return;
        cancelSleepFadeIfActive();
        sleepFadeAnimator = ValueAnimator.ofFloat(1.0f, 0.0f);
        sleepFadeAnimator.setDuration(20_000L);
        sleepFadeAnimator.addUpdateListener(anim -> {
            if (mediaController == null) return;
            float t = (float) anim.getAnimatedValue();
            mediaController.setVolume(t * t);
        });
        sleepFadeAnimator.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled = false;
            @Override public void onAnimationCancel(Animator animation) { cancelled = true; }
            @Override public void onAnimationEnd(Animator animation) {
                if (!cancelled && mediaController != null) {
                    saveCurrentTrackResumePosition();
                    mediaController.pause();
                    mediaController.setVolume(1.0f);
                    Toast.makeText(MainActivity.this, R.string.sheet_sleep_timer, Toast.LENGTH_SHORT).show();
                } else if (mediaController != null) {
                    mediaController.setVolume(1.0f);
                }
            }
        });
        sleepFadeAnimator.start();
    }

    private void cancelSleepFadeIfActive() {
        if (sleepFadeAnimator != null && sleepFadeAnimator.isRunning()) {
            sleepFadeAnimator.cancel();
        }
        if (mediaController != null) {
            mediaController.setVolume(1.0f);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (PermissionHelper.hasAudioReadPermission(this) && allTracks.isEmpty()) {
            refreshLibrary(false);
        }
        if (uiStyleController != null) {
            uiStyleController.syncRetroKnobsFromSystemAndFx();
        }
        fxEngine.registerAudioDeviceMonitor(this, deviceKey -> runOnUiThread(() -> {
            updateOutputDeviceBadge();
            syncAfterEqStateChanged();
            syncPurificationSuiteUi();
            String devLabel = getDeviceDisplayName(deviceKey);
            Toast.makeText(this, getString(R.string.toast_device_profile_switched, devLabel), Toast.LENGTH_SHORT).show();
        }));
        fxEngine.setSpectrumFrameListener(this, (bars, energy, leftLevel, rightLevel, bassEnergy, trebleEnergy, sampleRateHz) ->
                runOnUiThread(() -> {
                    waveformView.updateStereoWaveform(bars, energy, bassEnergy);
                    if (uiStyleController != null) {
                        uiStyleController.updateSpectrum(bars, energy);
                    }
                    boolean isDiscoClubMode = visualMetersEnabled
                            && visualMeterMode == UiStylePreferences.VisualMeterMode.DISCO_MODE;
                    if (neonEdgeGlowView != null && (fxEngine.isEdgeGlowEnabled() || isDiscoClubMode)) {
                        neonEdgeGlowView.onBassSample(bassEnergy);
                    }
                    if (hapticBassSync != null) {
                        boolean allowed = resourceOptimizer == null || resourceOptimizer.shouldAllowStrobeFlashes();
                        hapticBassSync.onBassSample(bassEnergy, fxEngine.isHapticBassEnabled(), allowed);
                    }
                    if (visualMetersEnabled) {
                        switch (visualMeterMode) {
                            case DISCO_MODE:
                                if (sideLedLeft != null) sideLedLeft.updateStereoLevel(leftLevel);
                                if (sideLedRight != null) sideLedRight.updateStereoLevel(rightLevel);
                                if (discoStrobeOverlay != null) {
                                    discoStrobeOverlay.onBeatSample(energy, bassEnergy, trebleEnergy);
                                }
                                if (discoVuGlassView != null) {
                                    discoVuGlassView.updateMeterTelemetry(
                                            fxEngine.getLatestLeftAvgDb(),
                                            fxEngine.getLatestRightAvgDb(),
                                            fxEngine.isLatestClipActive(),
                                            fxEngine.getLatestBitDepth());
                                    discoVuGlassView.updateStereoSpectrum(
                                            bars, energy, leftLevel, rightLevel, bassEnergy, sampleRateHz);
                                    discoVuGlassView.setQuickModeState(
                                            fxEngine.getActiveQuickMode(),
                                            fxEngine.getLatestLiveBpm());
                                }
                                break;
                            case SIDE_LED:
                            case CYBERPUNK:
                                if (sideLedLeft != null) sideLedLeft.updateStereoLevel(leftLevel);
                                if (sideLedRight != null) sideLedRight.updateStereoLevel(rightLevel);
                                break;
                            case STUDIO:
                                if (studioQuadMeterView != null) {
                                    studioQuadMeterView.updateFourChannels(leftLevel, rightLevel, bassEnergy, trebleEnergy);
                                }
                                break;
                            case PARTY_STROBE:
                                if (discoStrobeOverlay != null) {
                                    discoStrobeOverlay.onBeatSample(energy, bassEnergy, trebleEnergy);
                                }
                                if (discoVuGlassView != null) {
                                    discoVuGlassView.updateMeterTelemetry(
                                            fxEngine.getLatestLeftAvgDb(),
                                            fxEngine.getLatestRightAvgDb(),
                                            fxEngine.isLatestClipActive(),
                                            fxEngine.getLatestBitDepth());
                                    discoVuGlassView.updateStereoSpectrum(
                                            bars, energy, leftLevel, rightLevel, bassEnergy, sampleRateHz);
                                    discoVuGlassView.setQuickModeState(
                                            fxEngine.getActiveQuickMode(),
                                            fxEngine.getLatestLiveBpm());
                                }
                                break;
                            case DISCO_VU:
                                if (discoVuGlassView != null) {
                                    discoVuGlassView.updateMeterTelemetry(
                                            fxEngine.getLatestLeftAvgDb(),
                                            fxEngine.getLatestRightAvgDb(),
                                            fxEngine.isLatestClipActive(),
                                            fxEngine.getLatestBitDepth());
                                    discoVuGlassView.updateStereoSpectrum(
                                            bars, energy, leftLevel, rightLevel, bassEnergy, sampleRateHz);
                                    discoVuGlassView.setQuickModeState(
                                            fxEngine.getActiveQuickMode(),
                                            fxEngine.getLatestLiveBpm());
                                }
                                break;
                            case VINYL:
                            case CHILL_OCEAN:
                            case FIRE:
                            default:
                                break;
                        }
                    }
                    if (purificationSuiteView != null && purificationSuiteView.getVisibility() == View.VISIBLE) {
                        purificationSuiteView.updateLiveStatus(
                                fxEngine.getLiveCompReductionDb(),
                                fxEngine.getLiveLimitingDb(),
                                fxEngine.isLatestClipActive(),
                                fxEngine.getLatestBitDepth(),
                                fxEngine.getLatestBassPowerPercent(),
                                fxEngine.getLatestBassPowerDb(),
                                bars);
                    }
                    if (proStudioRackView != null && proStudioRackView.getVisibility() == View.VISIBLE) {
                        int activeRackTab = proStudioRackView.getActiveTab();
                        if (activeRackTab == ProStudioRackView.TAB_SCOPE) {
                            fxEngine.copyLatestXySamples(tempXyLeft, tempXyRight);
                            fxEngine.computeFrequencyResponseCurve(tempResponseCurve);
                        }
                        proStudioRackView.updateLiveTelemetry(
                                tempXyLeft,
                                tempXyRight,
                                fxEngine.getLatestPhaseCorrelation(),
                                fxEngine.getLatestLufsShortTerm(),
                                fxEngine.getLatestCrestFactorDb(),
                                fxEngine.getLiveTubeGlow(),
                                bassEnergy,
                                tempResponseCurve,
                                bars,
                                fxEngine.getLatestLiveBpm(),
                                fxEngine.getLatestKeyLabel(),
                                fxEngine.getLatestCamelot(),
                                fxEngine.getLatestDominantFreqHz(),
                                fxEngine.getLatestDominantNote(),
                                fxEngine.getLiveMbLowGrDb(),
                                fxEngine.getLiveMbMidGrDb(),
                                fxEngine.getLiveMbHighGrDb());
                    }
                    if (cinematicStageDialog != null && cinematicStageDialog.isShowing()) {
                        cinematicStageDialog.updateAudioTelemetry(
                                energy,
                                bassEnergy,
                                fxEngine.getLatestLiveBpm(),
                                fxEngine.getLatestKeyLabel(),
                                fxEngine.getLatestCamelot(),
                                fxEngine.getLatestDominantFreqHz(),
                                fxEngine.getLatestDominantNote());
                    }
                    if (reactiveButtonAnimator != null) {
                        reactiveButtonAnimator.onAudioFrame(energy, bassEnergy);
                    }
                }));
        syncAudioServicePrefs();
        if (cinematicStageDialog != null) {
            cinematicStageDialog.onHostResume(mediaController != null && mediaController.isPlaying());
        }
        if (reactiveButtonAnimator != null) {
            syncReactivePlaybackFlags();
            reactiveButtonAnimator.start();
        }
        mainHandler.removeCallbacks(progressRunnable);
        mainHandler.post(progressRunnable);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mainHandler.removeCallbacks(progressRunnable);
        lastStatsUpdateMs = 0L;
        if (cinematicStageDialog != null) {
            cinematicStageDialog.onHostPause();
        }
        if (reactiveButtonAnimator != null) {
            reactiveButtonAnimator.stop();
        }
        fxEngine.unregisterAudioDeviceMonitor();
        fxEngine.setSpectrumFrameListener(this, null);
        fxEngine.setVisualizerListener(this, null);
        if (audioTester != null) {
            audioTester.stop();
        }
        saveCurrentTrackResumePosition();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mediaController != null) {
            mediaController.removeListener(playerListener);
            mediaController = null;
        }
        if (controllerFuture != null) {
            MediaController.releaseFuture(controllerFuture);
            controllerFuture = null;
        }
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (repository != null) {
            repository.trimMemory(level);
        }
        if (resourceOptimizer != null && resourceOptimizer.isSevereMemoryTrimLevel(level)) {
            if (themeAnimator != null) {
                themeAnimator.cancel();
            }
            applyAdaptiveOptimizationProfile();
        }
    }

    @Override
    protected void onDestroy() {
        if (reactiveButtonAnimator != null) {
            reactiveButtonAnimator.stop();
        }
        if (resourceOptimizer != null) {
            resourceOptimizer.removeListener(optimizerListener);
        }
        if (themeAnimator != null) {
            themeAnimator.cancel();
            themeAnimator = null;
        }
        if (activeLyricsDialog != null && activeLyricsDialog.isShowing()) {
            activeLyricsDialog.dismiss();
        }
        if (driveModeDialog != null && driveModeDialog.isShowing()) {
            driveModeDialog.dismiss();
        }
        if (cinematicStageDialog != null) {
            cinematicStageDialog.dismiss();
            cinematicStageDialog = null;
        }
        driveModeDialog = null;
        activeLyricsDialog = null;
        activeLyricsContainer = null;
        activeLyricsScroll = null;
        pendingLrcTargetTrack = null;
        mainHandler.removeCallbacksAndMessages(null);
        paletteExecutor.shutdownNow();
        if (audioTester != null) {
            audioTester.shutdown();
        }
        if (repository != null) {
            repository.shutdown();
        }
        super.onDestroy();
    }

    private void openCinematicStageDialog() {
        if (cinematicStageDialog == null) {
            cinematicStageDialog = new CinematicStageDialog(this);
        }
        boolean playing = mediaController != null && mediaController.isPlaying();
        long pos = mediaController != null ? Math.max(0L, mediaController.getCurrentPosition()) : 0L;
        long dur = mediaController != null && mediaController.getDuration() > 0
                ? mediaController.getDuration()
                : (activeTrack != null ? activeTrack.getDurationMs() : 0L);
        boolean allowGyro = resourceOptimizer == null || resourceOptimizer.shouldEnableStageGyroscope();
        int maxParticles = resourceOptimizer != null ? resourceOptimizer.getStageParticleCount() : 16;
        int fps = resourceOptimizer != null ? resourceOptimizer.getStudioRackTargetFps() : 30;

        cinematicStageDialog.show(
                activeTrack,
                currentAlbumBitmap,
                currentPalette,
                playing,
                pos,
                dur,
                allowGyro,
                maxParticles,
                fps,
                new CinematicStageDialog.StageListener() {
                    @Override public void onPlayPauseClicked() { togglePlayPause(); }
                    @Override public void onPreviousClicked() { skipPrev(); }
                    @Override public void onNextClicked() { skipNext(); }
                    @Override public void onHarmonicNextClicked() { findAndPlayHarmonicNextTrack(); }
                    @Override public void onSeekToMs(long positionMs) {
                        if (mediaController != null && mediaController.getMediaItemCount() > 0) {
                            mediaController.seekTo(positionMs);
                            updatePlaybackProgress();
                        }
                    }
                }
        );
        if (activeTrack != null) {
            loadStageLyricsIfNeeded(activeTrack);
        }
    }

    private void loadStageLyricsIfNeeded(Track track) {
        if (track == null || repository == null) return;
        if (track.getId().equals(stageLyricsTrackId) && !stageLyricLines.isEmpty()) return;
        stageLyricLines.clear();
        stageLyricsTrackId = track.getId();
        repository.loadLyricsAsync(track, (loadedId, rawLyrics) -> {
            if (loadedId.equals(stageLyricsTrackId) && rawLyrics != null && !rawLyrics.isEmpty()) {
                stageLyricLines = LrcParser.parse(rawLyrics);
            }
        });
    }

    private void findAndPlayHarmonicNextTrack() {
        List<Track> pool = displayedTracks.size() > 1 ? displayedTracks : allTracks;
        if (pool.size() <= 1) {
            Toast.makeText(this, R.string.toast_no_harmonic_match, Toast.LENGTH_SHORT).show();
            return;
        }
        String currentId = activeTrack != null ? activeTrack.getId() : "";
        String curArtist = activeTrack != null ? activeTrack.getArtist() : "";
        String curAlbum = activeTrack != null ? activeTrack.getAlbum() : "";
        String curFormat = activeTrack != null ? activeTrack.getFormat() : "";
        long curDur = activeTrack != null ? activeTrack.getDurationMs() : 180_000L;
        float liveBpm = fxEngine != null ? fxEngine.getLatestLiveBpm() : 120f;
        int curIdx = -1;
        for (int i = 0; i < pool.size(); i++) {
            if (pool.get(i).getId().equals(currentId)) {
                curIdx = i;
                break;
            }
        }

        int bestIdx = -1;
        float bestScore = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < pool.size(); i++) {
            Track candidate = pool.get(i);
            if (candidate.getId().equals(currentId)) continue;
            float score = 0f;
            if (!curArtist.isEmpty() && curArtist.equalsIgnoreCase(candidate.getArtist())) {
                score += 3.5f;
            }
            if (!curAlbum.isEmpty() && curAlbum.equalsIgnoreCase(candidate.getAlbum())) {
                score += 2.5f;
            }
            if (!curFormat.isEmpty() && curFormat.equalsIgnoreCase(candidate.getFormat())) {
                score += 1.2f;
            }
            long durDiffSec = Math.abs(candidate.getDurationMs() - curDur) / 1000L;
            score += Math.max(0f, 3.0f - durDiffSec * 0.025f);
            // Slight deterministic Camelot/energy phase affinity from live BPM
            int bpmBucket = Math.round(liveBpm) % 5;
            if (Math.abs((i % 5) - bpmBucket) <= 1) {
                score += 1.4f;
            }
            if (candidate.isFavorite()) {
                score += 0.9f;
            }
            if (curIdx >= 0 && i == (curIdx + 1) % pool.size()) {
                score += 0.25f;
            }
            if (score > bestScore) {
                bestScore = score;
                bestIdx = i;
            }
        }

        if (bestIdx < 0) {
            Toast.makeText(this, R.string.toast_no_harmonic_match, Toast.LENGTH_SHORT).show();
            return;
        }
        Track match = pool.get(bestIdx);
        int dispIdx = displayedTracks.indexOf(match);
        if (dispIdx >= 0) {
            playTrackAtDisplayedPosition(dispIdx);
        } else {
            displayedTracks.add(0, match);
            trackAdapter.submitList(displayedTracks);
            playTrackAtDisplayedPosition(0);
        }
        Toast.makeText(this, getString(R.string.toast_harmonic_next, match.getTitle()), Toast.LENGTH_SHORT).show();
    }

    private String formatBalanceLabel(int balancePercent) {
        String base = getString(R.string.fx_balance_lr);
        if (balancePercent == 0) return base + ": Center";
        if (balancePercent < 0) return base + ": L " + Math.abs(balancePercent) + "%";
        return base + ": R " + balancePercent + "%";
    }

    private String getDeviceDisplayName(String deviceKey) {
        if (AudioFxEngine.DEVICE_BLUETOOTH.equals(deviceKey)) {
            return getString(R.string.device_bluetooth);
        } else if (AudioFxEngine.DEVICE_WIRED.equals(deviceKey)) {
            return getString(R.string.device_wired);
        }
        return getString(R.string.device_speaker);
    }

    private void updateOutputDeviceBadge() {
        if (badgeOutputDevice == null || fxEngine == null) return;
        badgeOutputDevice.setText(getDeviceDisplayName(fxEngine.getActiveOutputDeviceKey()));
    }

    private void syncAfterEqStateChanged() {
        if (fxEngine == null) return;
        if (switchEqEnabled != null) switchEqEnabled.setChecked(fxEngine.isEqEnabled());
        if (equalizerView != null) {
            equalizerView.setEqEnabled(fxEngine.isEqEnabled());
            equalizerView.setBandsDb(fxEngine.getUiBandGainsDb());
        }
        if (presetAdapter != null) {
            presetAdapter.setSelectedPreset(fxEngine.getCurrentPreset());
        }
        syncHeroBassDriveUi();
        syncPurificationSuiteUi();
        if (uiStyleController != null) {
            uiStyleController.syncRetroKnobsFromSystemAndFx();
        }
        updateVisualMetersPlayingState(mediaController != null && mediaController.isPlaying());
        syncAudioServicePrefs();
    }

    private void updatePeakIndicatorAndStats() {
        long now = android.os.SystemClock.uptimeMillis();
        if (fxEngine != null && badgePeakClip != null) {
            if (fxEngine.consumePeakLimiterTrigger()) {
                peakLitUntilMs = now + 750L;
            }
            boolean hot = now < peakLitUntilMs;
            badgePeakClip.setTextColor(hot ? 0xFFFF3B6B : ContextCompat.getColor(this, R.color.text_muted));
            badgePeakClip.setAlpha(hot ? 1.0f : 0.65f);
        }
        boolean playing = mediaController != null && mediaController.isPlaying();
        if (playing && statsTracker != null) {
            if (lastStatsUpdateMs == 0L) {
                lastStatsUpdateMs = now;
            } else if (now - lastStatsUpdateMs >= 1000L) {
                long deltaSec = (now - lastStatsUpdateMs) / 1000L;
                lastStatsUpdateMs += deltaSec * 1000L;
                statsTracker.addListeningSeconds(deltaSec);
            }
        } else {
            lastStatsUpdateMs = 0L;
        }
    }

    private void updateStickyMiniPlayerVisibility() {
        if (stickyMiniBar == null) return;
        boolean floatingMode = uiStyleController != null
                && uiStyleController.getCurrentMode() == UiStyleMode.FLOATING_MINI;
        boolean shouldShow = isScrolledPastHero && activeTrack != null && !floatingMode;
        int targetVis = shouldShow ? View.VISIBLE : View.GONE;
        if (stickyMiniBar.getVisibility() != targetVis) {
            stickyMiniBar.setVisibility(targetVis);
        }
    }

    private void showDriveModeDialog() {
        if (driveModeDialog != null && driveModeDialog.isShowing()) {
            return;
        }
        android.app.Dialog dialog = new android.app.Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        View view = getLayoutInflater().inflate(R.layout.dialog_drive_mode, null);
        dialog.setContentView(view);

        driveModeDialog = dialog;
        driveAlbumArt = view.findViewById(R.id.drive_album_art);
        driveTitleText = view.findViewById(R.id.drive_track_title);
        driveArtistText = view.findViewById(R.id.drive_track_artist);
        driveCurrentTime = view.findViewById(R.id.drive_current_time);
        driveTotalTime = view.findViewById(R.id.drive_total_time);
        driveSeekBar = view.findViewById(R.id.drive_seek_bar);
        drivePlayPauseBtn = view.findViewById(R.id.drive_btn_play_pause);
        if (driveTitleText != null) driveTitleText.setSelected(true);

        if (activeTrack != null) {
            if (driveTitleText != null) driveTitleText.setText(activeTrack.getTitle());
            if (driveArtistText != null) driveArtistText.setText(activeTrack.getArtist());
            if (currentAlbumBitmap != null && !currentAlbumBitmap.isRecycled() && driveAlbumArt != null) {
                driveAlbumArt.setImageBitmap(currentAlbumBitmap);
            }
        }
        boolean playing = mediaController != null && mediaController.isPlaying();
        if (drivePlayPauseBtn != null) {
            drivePlayPauseBtn.setImageResource(playing ? R.drawable.ic_pause : R.drawable.ic_play);
            drivePlayPauseBtn.setOnClickListener(v -> togglePlayPause());
        }

        view.findViewById(R.id.drive_btn_exit).setOnClickListener(v -> dialog.dismiss());
        view.findViewById(R.id.drive_btn_prev).setOnClickListener(v -> skipPrev());
        view.findViewById(R.id.drive_btn_next).setOnClickListener(v -> skipNext());
        view.findViewById(R.id.drive_btn_back10).setOnClickListener(v -> {
            if (mediaController != null && mediaController.getMediaItemCount() > 0) {
                mediaController.seekTo(Math.max(0L, mediaController.getCurrentPosition() - 10_000L));
                updatePlaybackProgress();
            }
        });
        view.findViewById(R.id.drive_btn_fwd10).setOnClickListener(v -> {
            if (mediaController != null && mediaController.getMediaItemCount() > 0) {
                long dur = mediaController.getDuration();
                long next = mediaController.getCurrentPosition() + 10_000L;
                mediaController.seekTo(dur > 0 ? Math.min(dur, next) : next);
                updatePlaybackProgress();
            }
        });
        view.findViewById(R.id.drive_btn_bass_toggle).setOnClickListener(v -> {
            int nextBass = fxEngine.getBassBoostPercent() >= 170 ? 110 : 195;
            fxEngine.setBassBoostPercent(nextBass);
            if (!fxEngine.isEqEnabled()) fxEngine.setEqEnabled(true);
            syncAfterEqStateChanged();
            Toast.makeText(this, getString(R.string.fx_bass_boost) + ": " + nextBass + "%", Toast.LENGTH_SHORT).show();
        });

        if (driveSeekBar != null) {
            driveSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                    if (fromUser && driveCurrentTime != null) {
                        driveCurrentTime.setText(TimeFormatter.formatDuration(progress));
                    }
                }
                @Override public void onStartTrackingTouch(SeekBar sb) { isUserSeeking = true; }
                @Override public void onStopTrackingTouch(SeekBar sb) {
                    isUserSeeking = false;
                    if (mediaController != null) mediaController.seekTo(sb.getProgress());
                }
            });
        }

        dialog.setOnDismissListener(d -> {
            driveModeDialog = null;
            driveAlbumArt = null;
            driveTitleText = null;
            driveArtistText = null;
            driveCurrentTime = null;
            driveTotalTime = null;
            driveSeekBar = null;
            drivePlayPauseBtn = null;
        });

        dialog.show();
        updatePlaybackProgress();
    }

    private void showAudioLabInsightsDialog() {
        BottomSheetDialog dialog = new BottomSheetDialog(this, R.style.NeonBottomSheetTheme);
        View view = getLayoutInflater().inflate(R.layout.dialog_insights_lab, null);
        dialog.setContentView(view);

        TextView testerStatus = view.findViewById(R.id.lab_tester_status);
        View btnLeft = view.findViewById(R.id.lab_btn_test_left);
        View btnBass = view.findViewById(R.id.lab_btn_test_bass);
        View btnRight = view.findViewById(R.id.lab_btn_test_right);

        btnLeft.setOnClickListener(v -> {
            testerStatus.setText("Playing Left Channel (440 Hz)…");
            audioTester.playTest(BuiltInAudioTester.TestSignal.LEFT_CHANNEL, () ->
                    runOnUiThread(() -> testerStatus.setText("Left Channel test finished")));
        });
        btnRight.setOnClickListener(v -> {
            testerStatus.setText("Playing Right Channel (587 Hz)…");
            audioTester.playTest(BuiltInAudioTester.TestSignal.RIGHT_CHANNEL, () ->
                    runOnUiThread(() -> testerStatus.setText("Right Channel test finished")));
        });
        btnBass.setOnClickListener(v -> {
            testerStatus.setText("Playing Sub-Bass Punch (68Hz → 38Hz)…");
            audioTester.playTest(BuiltInAudioTester.TestSignal.SUB_BASS_PUNCH, () ->
                    runOnUiThread(() -> testerStatus.setText("Sub-Bass sweep finished")));
        });

        TextView insightsText = view.findViewById(R.id.lab_insights_text);
        ListeningStatsTracker.InsightsSummary sum = statsTracker.buildSummary();
        StringBuilder formatsSb = new StringBuilder();
        for (Map.Entry<String, Integer> entry : sum.formatCounts.entrySet()) {
            if (formatsSb.length() > 0) formatsSb.append("  •  ");
            formatsSb.append(entry.getKey()).append(": ").append(entry.getValue());
        }
        if (formatsSb.length() == 0) formatsSb.append("No tracks played yet");
        long mins = sum.totalListeningSeconds / 60L;
        long secs = sum.totalListeningSeconds % 60L;
        insightsText.setText(String.format(Locale.US,
                "Active Output: %s\nTotal Listen Time: %dm %02ds  (%d plays)\nTop Track: %s (%d plays)\nTop Artist: %s (%d plays)\nFormats: %s",
                getDeviceDisplayName(fxEngine.getActiveOutputDeviceKey()),
                mins, secs, sum.totalPlays,
                sum.topTrackTitle, sum.topTrackPlays,
                sum.topArtistName, sum.topArtistPlays,
                formatsSb.toString()));

        view.findViewById(R.id.lab_btn_export_backup).setOnClickListener(v ->
                exportBackupLauncher.launch("neonwave_backup.json"));
        view.findViewById(R.id.lab_btn_import_backup).setOnClickListener(v -> {
            dialog.dismiss();
            importBackupLauncher.launch(new String[]{"application/json", "*/*"});
        });

        dialog.setOnDismissListener(d -> {
            if (audioTester != null) audioTester.stop();
        });
        dialog.show();
    }

    private void showTrackTechInspectorDialog(Track track) {
        if (track == null) return;
        String tierName = resourceOptimizer != null
                ? resourceOptimizer.evaluateTelemetry().effectiveTier.name()
                : "AUTO";
        String msg = String.format(Locale.US,
                "Track: %s\nArtist: %s\nFolder: %s\n\nFormat: %s (%s)\nBitrate: %s  •  Sample Rate: %d Hz\nFile Size: %s\n\nOutput Hardware: %s\nEQ Preset: %s (EQ %s)\nSub-Bass Drive: %d%%  •  3D: %d%%\nLoudness: %d%%  •  %s\n8D Orbit: %s  •  Crossfade: %ds\nResource Tier: %s",
                track.getTitle(),
                track.getArtist(),
                track.getFolderName(),
                track.getFormat(),
                track.getMimeType(),
                track.getBitrateBadgeText(),
                track.getSampleRateHz(),
                TimeFormatter.formatFileSize(track.getSizeBytes()),
                getDeviceDisplayName(fxEngine.getActiveOutputDeviceKey()),
                getString(fxEngine.getCurrentPreset().getTitleRes()),
                fxEngine.isEqEnabled() ? "ON" : "OFF",
                fxEngine.getBassBoostPercent(),
                fxEngine.getVirtualizerPercent(),
                fxEngine.getLoudnessPercent(),
                formatBalanceLabel(fxEngine.getBalancePercent()),
                fxEngine.isSpatial8dEnabled() ? "ON" : "OFF",
                fxEngine.getCrossfadeSeconds(),
                tierName
        );
        new AlertDialog.Builder(this)
                .setTitle("NeonWave Audio Inspector")
                .setMessage(msg)
                .setPositiveButton("OK", null)
                .show();
    }

    private final Player.Listener playerListener = new Player.Listener() {
        @Override
        public void onIsPlayingChanged(boolean isPlaying) {
            btnPlayPause.setImageResource(isPlaying ? R.drawable.ic_pause : R.drawable.ic_play);
            if (stickyMiniPlayPause != null) {
                stickyMiniPlayPause.setImageResource(isPlaying ? R.drawable.ic_pause : R.drawable.ic_play);
            }
            if (driveModeDialog != null && driveModeDialog.isShowing() && drivePlayPauseBtn != null) {
                drivePlayPauseBtn.setImageResource(isPlaying ? R.drawable.ic_pause : R.drawable.ic_play);
            }
            waveformView.setPlaying(isPlaying);
            if (uiStyleController != null) {
                uiStyleController.updatePlayingState(isPlaying);
            }
            updateVisualMetersPlayingState(isPlaying);
            if (activeTrack != null) {
                trackAdapter.setPlaybackState(activeTrack.getId(), isPlaying);
            }
            if (isPlaying) {
                mainHandler.removeCallbacks(progressRunnable);
                mainHandler.post(progressRunnable);
            } else {
                lastStatsUpdateMs = 0L;
            }
        }

        @Override
        public void onMediaItemTransition(MediaItem mediaItem, int reason) {
            syncControllerStateToUi();
            if (mediaItem != null) {
                repository.recordRecentPlay(mediaItem.mediaId);
                if (statsTracker != null) {
                    for (Track t : allTracks) {
                        if (t.getId().equals(mediaItem.mediaId)) {
                            statsTracker.recordTrackStarted(t);
                            break;
                        }
                    }
                }
            }
        }

        @Override
        public void onPlaybackStateChanged(int playbackState) {
            if (playbackState == Player.STATE_ENDED && activeTrack != null) {
                repository.clearTrackResumePosition(activeTrack.getId());
            }
            updatePlaybackProgress();
        }

        @Override
        public void onShuffleModeEnabledChanged(boolean shuffleModeEnabled) {
            updateShuffleAndRepeatUi();
        }

        @Override
        public void onRepeatModeChanged(int repeatMode) {
            updateShuffleAndRepeatUi();
        }

        @Override
        public void onPlayerError(@NonNull PlaybackException error) {
            Toast.makeText(
                    MainActivity.this,
                    "Unable to decode audio: " + error.getErrorCodeName(),
                    Toast.LENGTH_SHORT
            ).show();
        }
    };
}
