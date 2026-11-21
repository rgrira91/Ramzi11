package com.example.ui.theme;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.media3.common.Player;
import com.example.R;
import com.example.audio.AudioFxEngine;
import com.example.data.AudioLibraryRepository;
import com.example.model.Track;
import com.example.ui.adapter.PresetAdapter;
import com.example.ui.adapter.TrackAdapter;
import com.example.ui.views.AnalogVuMeterView;
import com.example.ui.views.NeonEqualizerView;
import com.example.ui.views.NeonWaveformView;
import com.example.ui.views.RetroVinylView;
import com.example.ui.views.RotaryKnobView;
import com.example.util.TimeFormatter;
import com.google.android.material.imageview.ShapeableImageView;
import java.util.List;

public class PlayerUiStyleController {

    public interface ActionsCallback {
        void onPlayPauseClicked();
        void onNextClicked();
        void onPrevClicked();
        void onShuffleClicked();
        void onRepeatClicked();
        void onFavoriteClicked();
        void onMoreOptionsClicked();
        void onSeekTo(int positionMs);
        void onUserSeekingChanged(boolean seeking);
        void onFloatingCollapsedChanged(boolean collapsed);
        void onTrebleKnobChanged(int treblePercent);
        void onUiStyleButtonClicked();
        void onStageVisibilityUpdated();
    }

    private final Activity activity;
    private final UiStylePreferences prefs;
    private final AudioLibraryRepository repository;
    private final AudioFxEngine fxEngine;
    private final ActionsCallback callback;

    // Shared host views
    private View rootScroll;
    private View heroPlayerCard;
    private View cardEqPresets;
    private View cardAudioFrequency;
    private View cardUpNext;
    private TextView headerAppTitle;
    private TextView badgeBrandSubtitle;
    private View btnUiStyle;
    private ImageView iconUiStyle;
    private TextView textUiStyleLabel;
    private ImageButton btnPickFiles;
    private ImageButton btnRescan;
    private TextView sectionEqPresetsTitle;
    private TextView sectionAudioFreqTitle;
    private TextView sectionUpNextTitle;
    private ImageView iconEqPresets;
    private ImageView iconUpNext;
    private TextView btnSortTracks;
    private ImageButton btnToggleSearch;
    private TextView badgeEqMode;
    private View btnMeterPower;
    private View btnMeterSwitch;
    private TextView textMeterPower;
    private TextView textMeterSwitch;
    private TrackAdapter trackAdapter;
    private PresetAdapter presetAdapter;
    private NeonEqualizerView equalizerView;

    // 07 Floating Mini views
    private View floatingStage;
    private ShapeableImageView floatingAlbumArt;
    private TextView floatingTitle;
    private TextView floatingArtist;
    private ImageButton floatingBtnCollapse;
    private ImageButton floatingBtnFavorite;
    private NeonWaveformView floatingWaveform;
    private SeekBar floatingSeekBar;
    private TextView floatingCurrentTime;
    private TextView floatingTotalTime;
    private ImageButton floatingBtnShuffle;
    private ImageButton floatingBtnPrev;
    private ImageButton floatingBtnPlayPause;
    private ImageButton floatingBtnNext;
    private ImageButton floatingBtnRepeat;
    private ImageButton floatingBtnMore;

    // 07 Floating Mini docked compact bar
    private View miniBarCard;
    private ShapeableImageView miniBarArt;
    private TextView miniBarTitle;
    private TextView miniBarArtist;
    private ImageButton miniBarBtnExpand;
    private SeekBar miniBarSeekBar;
    private TextView miniBarCurrentTime;
    private TextView miniBarTotalTime;
    private ImageButton miniBarBtnShuffle;
    private ImageButton miniBarBtnPrev;
    private ImageButton miniBarBtnPlayPause;
    private ImageButton miniBarBtnNext;
    private ImageButton miniBarBtnRepeat;

    // 03 Retro Vinyl views
    private View retroStage;
    private RetroVinylView retroVinylView;
    private TextView retroTitle;
    private TextView retroArtist;
    private ImageButton retroBtnFavorite;
    private ImageButton retroBtnMore;
    private SeekBar retroSeekBar;
    private TextView retroCurrentTime;
    private TextView retroTotalTime;
    private ImageButton retroBtnShuffle;
    private ImageButton retroBtnPrev;
    private ImageButton retroBtnPlayPause;
    private ImageButton retroBtnNext;
    private ImageButton retroBtnRepeat;
    private AnalogVuMeterView retroVuMeter;
    private RotaryKnobView retroKnobVolume;
    private RotaryKnobView retroKnobBass;
    private RotaryKnobView retroKnobTreble;

    // 02 Liquid Glass views
    private View liquidStage;
    private ShapeableImageView liquidArtPrev;
    private ShapeableImageView liquidArtCenter;
    private ShapeableImageView liquidArtNext;
    private TextView liquidTitle;
    private TextView liquidArtist;
    private ImageButton liquidBtnMore;
    private ImageButton liquidBtnFavorite;
    private SeekBar liquidSeekBar;
    private TextView liquidCurrentTime;
    private TextView liquidTotalTime;
    private ImageButton liquidBtnShuffle;
    private ImageButton liquidBtnPrev;
    private ImageButton liquidBtnPlayPause;
    private ImageButton liquidBtnNext;
    private ImageButton liquidBtnRepeat;

    private UiStyleMode currentMode = UiStyleMode.NEON_COSMIC;
    private boolean floatingCollapsed = false;
    private boolean showEqDeck = true;
    private NeonPalette activePalette = NeonPalette.DEFAULT;
    private String currentTrackId = null;

    private long lastProgressDurMs = -1L;
    private long lastProgressDurSec = -1L;
    private long lastProgressPosSec = -1L;
    private int lastRepeatIconRes = 0;

    public PlayerUiStyleController(
            Activity activity,
            UiStylePreferences prefs,
            AudioLibraryRepository repository,
            AudioFxEngine fxEngine,
            ActionsCallback callback) {
        this.activity = activity;
        this.prefs = prefs;
        this.repository = repository;
        this.fxEngine = fxEngine;
        this.callback = callback;
        this.currentMode = prefs.getActiveStyle();
        this.floatingCollapsed = prefs.isFloatingMiniCollapsed();
        this.showEqDeck = prefs.isShowEqInCustomStyles();
    }

    public void bindViews(TrackAdapter trackAdapter, PresetAdapter presetAdapter, NeonEqualizerView equalizerView) {
        this.trackAdapter = trackAdapter;
        this.presetAdapter = presetAdapter;
        this.equalizerView = equalizerView;

        rootScroll = activity.findViewById(R.id.root_scroll);
        heroPlayerCard = activity.findViewById(R.id.hero_player_card);
        cardEqPresets = activity.findViewById(R.id.card_eq_presets);
        cardAudioFrequency = activity.findViewById(R.id.card_audio_frequency);
        cardUpNext = activity.findViewById(R.id.card_up_next);
        headerAppTitle = activity.findViewById(R.id.header_app_title);
        badgeBrandSubtitle = activity.findViewById(R.id.badge_brand_subtitle);
        btnUiStyle = activity.findViewById(R.id.btn_ui_style);
        iconUiStyle = activity.findViewById(R.id.icon_ui_style);
        textUiStyleLabel = activity.findViewById(R.id.text_ui_style_label);
        btnPickFiles = activity.findViewById(R.id.btn_pick_files);
        btnRescan = activity.findViewById(R.id.btn_rescan);
        sectionEqPresetsTitle = activity.findViewById(R.id.section_eq_presets_title);
        sectionAudioFreqTitle = activity.findViewById(R.id.section_audio_freq_title);
        sectionUpNextTitle = activity.findViewById(R.id.section_up_next_title);
        iconEqPresets = activity.findViewById(R.id.icon_eq_presets);
        iconUpNext = activity.findViewById(R.id.icon_up_next);
        btnSortTracks = activity.findViewById(R.id.btn_sort_tracks);
        btnToggleSearch = activity.findViewById(R.id.btn_toggle_search);
        badgeEqMode = activity.findViewById(R.id.badge_eq_mode);
        btnMeterPower = activity.findViewById(R.id.btn_meter_power);
        btnMeterSwitch = activity.findViewById(R.id.btn_meter_switch);
        textMeterPower = activity.findViewById(R.id.text_meter_power);
        textMeterSwitch = activity.findViewById(R.id.text_meter_switch);

        if (btnUiStyle != null) {
            btnUiStyle.setOnClickListener(v -> callback.onUiStyleButtonClicked());
        }

        bindFloatingMiniViews();
        bindRetroVinylViews();
        bindLiquidGlassViews();
    }

    private void bindFloatingMiniViews() {
        floatingStage = activity.findViewById(R.id.floating_player_stage);
        floatingAlbumArt = activity.findViewById(R.id.floating_album_art);
        floatingTitle = activity.findViewById(R.id.floating_track_title);
        if (floatingTitle != null) floatingTitle.setSelected(true);
        floatingArtist = activity.findViewById(R.id.floating_track_artist);
        floatingBtnCollapse = activity.findViewById(R.id.floating_btn_collapse);
        floatingBtnFavorite = activity.findViewById(R.id.floating_btn_favorite);
        floatingWaveform = activity.findViewById(R.id.floating_waveform_view);
        if (floatingWaveform != null) {
            floatingWaveform.setVisualMode(NeonWaveformView.VisualMode.MIRROR_PULSE);
            floatingWaveform.setOnClickListener(v -> floatingWaveform.cycleVisualMode());
        }
        floatingSeekBar = activity.findViewById(R.id.floating_seek_bar);
        floatingCurrentTime = activity.findViewById(R.id.floating_current_time);
        floatingTotalTime = activity.findViewById(R.id.floating_total_time);
        floatingBtnShuffle = activity.findViewById(R.id.floating_btn_shuffle);
        floatingBtnPrev = activity.findViewById(R.id.floating_btn_prev);
        floatingBtnPlayPause = activity.findViewById(R.id.floating_btn_play_pause);
        floatingBtnNext = activity.findViewById(R.id.floating_btn_next);
        floatingBtnRepeat = activity.findViewById(R.id.floating_btn_repeat);
        floatingBtnMore = activity.findViewById(R.id.floating_btn_more);

        miniBarCard = activity.findViewById(R.id.floating_mini_docked_card);
        miniBarArt = activity.findViewById(R.id.mini_bar_album_art);
        miniBarTitle = activity.findViewById(R.id.mini_bar_title);
        miniBarArtist = activity.findViewById(R.id.mini_bar_artist);
        miniBarBtnExpand = activity.findViewById(R.id.mini_bar_btn_expand);
        miniBarSeekBar = activity.findViewById(R.id.mini_bar_seek_bar);
        miniBarCurrentTime = activity.findViewById(R.id.mini_bar_current_time);
        miniBarTotalTime = activity.findViewById(R.id.mini_bar_total_time);
        miniBarBtnShuffle = activity.findViewById(R.id.mini_bar_btn_shuffle);
        miniBarBtnPrev = activity.findViewById(R.id.mini_bar_btn_prev);
        miniBarBtnPlayPause = activity.findViewById(R.id.mini_bar_btn_play_pause);
        miniBarBtnNext = activity.findViewById(R.id.mini_bar_btn_next);
        miniBarBtnRepeat = activity.findViewById(R.id.mini_bar_btn_repeat);

        if (floatingBtnCollapse != null) {
            floatingBtnCollapse.setOnClickListener(v -> setFloatingCollapsed(true));
        }
        if (miniBarBtnExpand != null) {
            miniBarBtnExpand.setOnClickListener(v -> setFloatingCollapsed(false));
        }
        wireCommonButtons(
                floatingBtnPlayPause, floatingBtnNext, floatingBtnPrev,
                floatingBtnShuffle, floatingBtnRepeat, floatingBtnFavorite, floatingBtnMore);
        wireCommonButtons(
                miniBarBtnPlayPause, miniBarBtnNext, miniBarBtnPrev,
                miniBarBtnShuffle, miniBarBtnRepeat, null, null);
        wireSeekBar(floatingSeekBar, floatingCurrentTime);
        wireSeekBar(miniBarSeekBar, miniBarCurrentTime);
    }

    private void bindRetroVinylViews() {
        retroStage = activity.findViewById(R.id.retro_vinyl_stage);
        retroVinylView = activity.findViewById(R.id.retro_vinyl_view);
        retroTitle = activity.findViewById(R.id.retro_track_title);
        if (retroTitle != null) retroTitle.setSelected(true);
        retroArtist = activity.findViewById(R.id.retro_track_artist);
        retroBtnFavorite = activity.findViewById(R.id.retro_btn_favorite);
        retroBtnMore = activity.findViewById(R.id.retro_btn_more);
        retroSeekBar = activity.findViewById(R.id.retro_seek_bar);
        retroCurrentTime = activity.findViewById(R.id.retro_current_time);
        retroTotalTime = activity.findViewById(R.id.retro_total_time);
        retroBtnShuffle = activity.findViewById(R.id.retro_btn_shuffle);
        retroBtnPrev = activity.findViewById(R.id.retro_btn_prev);
        retroBtnPlayPause = activity.findViewById(R.id.retro_btn_play_pause);
        retroBtnNext = activity.findViewById(R.id.retro_btn_next);
        retroBtnRepeat = activity.findViewById(R.id.retro_btn_repeat);
        retroVuMeter = activity.findViewById(R.id.retro_vu_meter);
        retroKnobVolume = activity.findViewById(R.id.retro_knob_volume);
        retroKnobBass = activity.findViewById(R.id.retro_knob_bass);
        retroKnobTreble = activity.findViewById(R.id.retro_knob_treble);

        if (retroVinylView != null) {
            retroVinylView.setOnClickListener(v -> callback.onPlayPauseClicked());
        }

        wireCommonButtons(
                retroBtnPlayPause, retroBtnNext, retroBtnPrev,
                retroBtnShuffle, retroBtnRepeat, retroBtnFavorite, retroBtnMore);
        wireSeekBar(retroSeekBar, retroCurrentTime);

        if (retroKnobVolume != null) {
            retroKnobVolume.setKnobLabel("VOLUME");
            retroKnobVolume.setPercent(readSystemVolumePercent());
            retroKnobVolume.setOnKnobChangedListener((percent, fromUser) -> {
                if (fromUser) applySystemVolumePercent(percent);
            });
        }
        if (retroKnobBass != null) {
            retroKnobBass.setKnobLabel("BASS");
            retroKnobBass.setPercent(fxEngine.getBassBoostPercent());
            retroKnobBass.setOnKnobChangedListener((percent, fromUser) -> {
                if (fromUser) fxEngine.setBassBoostPercent(percent);
            });
        }
        if (retroKnobTreble != null) {
            retroKnobTreble.setKnobLabel("TREBLE");
            retroKnobTreble.setPercent(readTreblePercentFromEq());
            retroKnobTreble.setOnKnobChangedListener((percent, fromUser) -> {
                if (fromUser) callback.onTrebleKnobChanged(percent);
            });
        }
    }

    private void bindLiquidGlassViews() {
        liquidStage = activity.findViewById(R.id.liquid_glass_stage);
        liquidArtPrev = activity.findViewById(R.id.liquid_art_prev);
        liquidArtCenter = activity.findViewById(R.id.liquid_art_center);
        liquidArtNext = activity.findViewById(R.id.liquid_art_next);
        liquidTitle = activity.findViewById(R.id.liquid_track_title);
        if (liquidTitle != null) liquidTitle.setSelected(true);
        liquidArtist = activity.findViewById(R.id.liquid_track_artist);
        liquidBtnMore = activity.findViewById(R.id.liquid_btn_more);
        liquidBtnFavorite = activity.findViewById(R.id.liquid_btn_favorite);
        liquidSeekBar = activity.findViewById(R.id.liquid_seek_bar);
        liquidCurrentTime = activity.findViewById(R.id.liquid_current_time);
        liquidTotalTime = activity.findViewById(R.id.liquid_total_time);
        liquidBtnShuffle = activity.findViewById(R.id.liquid_btn_shuffle);
        liquidBtnPrev = activity.findViewById(R.id.liquid_btn_prev);
        liquidBtnPlayPause = activity.findViewById(R.id.liquid_btn_play_pause);
        liquidBtnNext = activity.findViewById(R.id.liquid_btn_next);
        liquidBtnRepeat = activity.findViewById(R.id.liquid_btn_repeat);

        if (liquidArtPrev != null) liquidArtPrev.setOnClickListener(v -> callback.onPrevClicked());
        if (liquidArtNext != null) liquidArtNext.setOnClickListener(v -> callback.onNextClicked());
        if (liquidArtCenter != null) liquidArtCenter.setOnClickListener(v -> callback.onPlayPauseClicked());

        wireCommonButtons(
                liquidBtnPlayPause, liquidBtnNext, liquidBtnPrev,
                liquidBtnShuffle, liquidBtnRepeat, liquidBtnFavorite, liquidBtnMore);
        wireSeekBar(liquidSeekBar, liquidCurrentTime);
    }

    private void wireCommonButtons(
            @Nullable View playPause,
            @Nullable View next,
            @Nullable View prev,
            @Nullable View shuffle,
            @Nullable View repeat,
            @Nullable View favorite,
            @Nullable View more) {
        if (playPause != null) playPause.setOnClickListener(v -> callback.onPlayPauseClicked());
        if (next != null) next.setOnClickListener(v -> callback.onNextClicked());
        if (prev != null) prev.setOnClickListener(v -> callback.onPrevClicked());
        if (shuffle != null) shuffle.setOnClickListener(v -> callback.onShuffleClicked());
        if (repeat != null) repeat.setOnClickListener(v -> callback.onRepeatClicked());
        if (favorite != null) favorite.setOnClickListener(v -> callback.onFavoriteClicked());
        if (more != null) more.setOnClickListener(v -> callback.onMoreOptionsClicked());
    }

    private void wireSeekBar(@Nullable SeekBar seekBar, @Nullable TextView timeLabel) {
        if (seekBar == null) return;
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                if (fromUser && timeLabel != null) {
                    timeLabel.setText(TimeFormatter.formatDuration(progress));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar sb) {
                callback.onUserSeekingChanged(true);
            }

            @Override
            public void onStopTrackingTouch(SeekBar sb) {
                callback.onUserSeekingChanged(false);
                callback.onSeekTo(sb.getProgress());
            }
        });
    }

    public UiStyleMode getCurrentMode() {
        return currentMode;
    }

    public boolean isFloatingCollapsed() {
        return floatingCollapsed;
    }

    public boolean isShowEqDeck() {
        return showEqDeck;
    }

    public void setFloatingCollapsed(boolean collapsed) {
        this.floatingCollapsed = collapsed;
        prefs.setFloatingMiniCollapsed(collapsed);
        if (currentMode == UiStyleMode.FLOATING_MINI) {
            updateStageVisibility();
        }
        callback.onFloatingCollapsedChanged(collapsed);
    }

    public void setShowEqDeck(boolean show) {
        this.showEqDeck = show;
        prefs.setShowEqInCustomStyles(show);
        updateStageVisibility();
    }

    public void applyMode(UiStyleMode mode, boolean persist) {
        this.currentMode = mode != null ? mode : UiStyleMode.NEON_COSMIC;
        if (persist) {
            prefs.setActiveStyle(this.currentMode);
        }
        lastProgressDurMs = -1L;
        lastProgressDurSec = -1L;
        lastProgressPosSec = -1L;
        updateStageVisibility();
        applyChromeStyling();
        syncRetroKnobsFromSystemAndFx();
    }

    private void updateStageVisibility() {
        if (heroPlayerCard != null) {
            heroPlayerCard.setVisibility(currentMode == UiStyleMode.NEON_COSMIC ? View.VISIBLE : View.GONE);
        }
        if (floatingStage != null) {
            floatingStage.setVisibility(
                    currentMode == UiStyleMode.FLOATING_MINI && !floatingCollapsed ? View.VISIBLE : View.GONE);
        }
        if (miniBarCard != null) {
            miniBarCard.setVisibility(
                    currentMode == UiStyleMode.FLOATING_MINI && floatingCollapsed ? View.VISIBLE : View.GONE);
        }
        if (retroStage != null) {
            retroStage.setVisibility(currentMode == UiStyleMode.RETRO_VINYL ? View.VISIBLE : View.GONE);
        }
        if (liquidStage != null) {
            liquidStage.setVisibility(currentMode == UiStyleMode.LIQUID_GLASS ? View.VISIBLE : View.GONE);
        }

        boolean eqVisible = currentMode == UiStyleMode.NEON_COSMIC || showEqDeck;
        if (cardEqPresets != null) cardEqPresets.setVisibility(eqVisible ? View.VISIBLE : View.GONE);
        if (cardAudioFrequency != null) cardAudioFrequency.setVisibility(eqVisible ? View.VISIBLE : View.GONE);

        // Add extra bottom padding on scroll container when Floating Mini Bar is docked at bottom
        if (rootScroll != null) {
            float d = activity.getResources().getDisplayMetrics().density;
            int extraBottom = (currentMode == UiStyleMode.FLOATING_MINI && floatingCollapsed)
                    ? Math.round(168 * d)
                    : 0;
            rootScroll.setPadding(
                    rootScroll.getPaddingLeft(),
                    rootScroll.getPaddingTop(),
                    rootScroll.getPaddingRight(),
                    extraBottom
            );
        }
        callback.onStageVisibilityUpdated();
    }

    public void applyChromeStyling() {
        float density = activity.getResources().getDisplayMetrics().density;

        // Status bar light/dark icon polarity
        WindowInsetsControllerCompat insetsController =
                new WindowInsetsControllerCompat(activity.getWindow(), activity.getWindow().getDecorView());
        insetsController.setAppearanceLightStatusBars(!currentMode.isDarkCanvas);
        insetsController.setAppearanceLightNavigationBars(!currentMode.isDarkCanvas);

        if (textUiStyleLabel != null) {
            textUiStyleLabel.setText(activity.getString(R.string.btn_ui_style_label)
                    + " • " + activity.getString(currentMode.shortBadgeRes));
        }

        if (trackAdapter != null) trackAdapter.setUiStyleMode(currentMode);
        if (presetAdapter != null) presetAdapter.setUiStyleMode(currentMode);

        if (currentMode == UiStyleMode.RETRO_VINYL) {
            if (rootScroll != null) {
                rootScroll.setTag(R.id.root_scroll, null);
                rootScroll.setBackgroundResource(R.drawable.bg_retro_canvas);
            }
            if (headerAppTitle != null) headerAppTitle.setTextColor(0xFF2D1B10);
            if (badgeBrandSubtitle != null) {
                badgeBrandSubtitle.setBackgroundResource(R.drawable.bg_retro_sub_card);
                badgeBrandSubtitle.setTextColor(0xFF96380A);
            }
            if (btnUiStyle != null) btnUiStyle.setBackgroundResource(R.drawable.bg_retro_sub_card);
            if (iconUiStyle != null) iconUiStyle.setColorFilter(0xFFC25216);
            if (textUiStyleLabel != null) textUiStyleLabel.setTextColor(0xFF2D1B10);
            if (btnPickFiles != null) btnPickFiles.setColorFilter(0xFF96380A);
            if (btnRescan != null) btnRescan.setColorFilter(0xFF6B492D);

            styleSharedSubCard(cardEqPresets, R.drawable.bg_retro_sub_card);
            styleSharedSubCard(cardAudioFrequency, R.drawable.bg_retro_sub_card);
            styleSharedSubCard(cardUpNext, R.drawable.bg_retro_sub_card);

            if (sectionEqPresetsTitle != null) sectionEqPresetsTitle.setTextColor(0xFF2D1B10);
            if (sectionAudioFreqTitle != null) sectionAudioFreqTitle.setTextColor(0xFF2D1B10);
            if (sectionUpNextTitle != null) {
                sectionUpNextTitle.setText(R.string.section_vinyl_playlist);
                sectionUpNextTitle.setTextColor(0xFF2D1B10);
            }
            if (iconEqPresets != null) iconEqPresets.setColorFilter(0xFF8B4E24);
            if (iconUpNext != null) iconUpNext.setColorFilter(0xFF8B4E24);
            if (btnSortTracks != null) {
                btnSortTracks.setBackgroundResource(R.drawable.bg_retro_sub_card);
                btnSortTracks.setTextColor(0xFF6B492D);
            }
            if (btnToggleSearch != null) btnToggleSearch.setColorFilter(0xFF6B492D);
            if (badgeEqMode != null) badgeEqMode.setTextColor(0xFFC25216);
            if (btnMeterPower != null) btnMeterPower.setBackgroundResource(R.drawable.bg_retro_sub_card);
            if (btnMeterSwitch != null) btnMeterSwitch.setBackgroundResource(R.drawable.bg_retro_sub_card);
            if (textMeterPower != null) textMeterPower.setTextColor(0xFF2D1B10);
            if (textMeterSwitch != null) textMeterSwitch.setTextColor(0xFF2D1B10);
            if (equalizerView != null) equalizerView.setPaletteColors(0xFFC25216, 0xFFD99B4E, 0xFF8B4E24);
        } else if (currentMode == UiStyleMode.LIQUID_GLASS) {
            if (rootScroll != null) {
                rootScroll.setTag(R.id.root_scroll, null);
                rootScroll.setBackgroundResource(R.drawable.bg_liquid_canvas);
            }
            if (headerAppTitle != null) headerAppTitle.setTextColor(0xFF1E1B4B);
            if (badgeBrandSubtitle != null) {
                badgeBrandSubtitle.setBackgroundResource(R.drawable.bg_liquid_glass_card);
                badgeBrandSubtitle.setTextColor(0xFF6D28D9);
            }
            if (btnUiStyle != null) btnUiStyle.setBackgroundResource(R.drawable.bg_liquid_glass_card);
            if (iconUiStyle != null) iconUiStyle.setColorFilter(0xFF6D28D9);
            if (textUiStyleLabel != null) textUiStyleLabel.setTextColor(0xFF1E1B4B);
            if (btnPickFiles != null) btnPickFiles.setColorFilter(0xFF6D28D9);
            if (btnRescan != null) btnRescan.setColorFilter(0xFF4338CA);

            styleSharedSubCard(cardEqPresets, R.drawable.bg_liquid_glass_card);
            styleSharedSubCard(cardAudioFrequency, R.drawable.bg_liquid_glass_card);
            styleSharedSubCard(cardUpNext, R.drawable.bg_liquid_glass_card);

            if (sectionEqPresetsTitle != null) sectionEqPresetsTitle.setTextColor(0xFF1E1B4B);
            if (sectionAudioFreqTitle != null) sectionAudioFreqTitle.setTextColor(0xFF1E1B4B);
            if (sectionUpNextTitle != null) {
                sectionUpNextTitle.setText(R.string.section_up_next);
                sectionUpNextTitle.setTextColor(0xFF1E1B4B);
            }
            if (iconEqPresets != null) iconEqPresets.setColorFilter(0xFF6D28D9);
            if (iconUpNext != null) iconUpNext.setColorFilter(0xFF6D28D9);
            if (btnSortTracks != null) {
                btnSortTracks.setBackgroundResource(R.drawable.bg_liquid_glass_card);
                btnSortTracks.setTextColor(0xFF4338CA);
            }
            if (btnToggleSearch != null) btnToggleSearch.setColorFilter(0xFF4338CA);
            if (badgeEqMode != null) badgeEqMode.setTextColor(0xFF6D28D9);
            if (btnMeterPower != null) btnMeterPower.setBackgroundResource(R.drawable.bg_liquid_glass_card);
            if (btnMeterSwitch != null) btnMeterSwitch.setBackgroundResource(R.drawable.bg_liquid_glass_card);
            if (textMeterPower != null) textMeterPower.setTextColor(0xFF1E1B4B);
            if (textMeterSwitch != null) textMeterSwitch.setTextColor(0xFF1E1B4B);
            if (equalizerView != null) equalizerView.setPaletteColors(0xFF38BDF8, 0xFF8B5CF6, 0xFFEC4899);
        } else {
            // Dark cosmic styles (NEON_COSMIC & FLOATING_MINI)
            if (headerAppTitle != null) headerAppTitle.setTextColor(ContextCompat.getColor(activity, R.color.text_primary));
            if (badgeBrandSubtitle != null) badgeBrandSubtitle.setBackgroundResource(R.drawable.bg_pill_badge);
            if (btnUiStyle != null) btnUiStyle.setBackgroundResource(R.drawable.bg_pill_badge);
            if (iconUiStyle != null) iconUiStyle.setColorFilter(activePalette.secondaryNeon);
            if (textUiStyleLabel != null) textUiStyleLabel.setTextColor(ContextCompat.getColor(activity, R.color.text_primary));
            if (btnPickFiles != null) btnPickFiles.setColorFilter(activePalette.secondaryNeon);
            if (btnRescan != null) btnRescan.setColorFilter(ContextCompat.getColor(activity, R.color.text_secondary));

            if (cardEqPresets != null) cardEqPresets.setTag(null);
            if (cardAudioFrequency != null) cardAudioFrequency.setTag(null);
            if (cardUpNext != null) cardUpNext.setTag(null);

            if (sectionEqPresetsTitle != null) sectionEqPresetsTitle.setTextColor(ContextCompat.getColor(activity, R.color.text_primary));
            if (sectionAudioFreqTitle != null) sectionAudioFreqTitle.setTextColor(ContextCompat.getColor(activity, R.color.text_primary));
            if (sectionUpNextTitle != null) {
                sectionUpNextTitle.setText(currentMode == UiStyleMode.FLOATING_MINI
                        ? R.string.section_music_queue : R.string.section_up_next);
                sectionUpNextTitle.setTextColor(ContextCompat.getColor(activity, R.color.text_primary));
            }
            if (iconEqPresets != null) iconEqPresets.setColorFilter(ContextCompat.getColor(activity, R.color.text_secondary));
            if (iconUpNext != null) iconUpNext.setColorFilter(ContextCompat.getColor(activity, R.color.text_secondary));
            if (btnSortTracks != null) {
                btnSortTracks.setBackgroundResource(R.drawable.bg_pill_badge);
                btnSortTracks.setTextColor(ContextCompat.getColor(activity, R.color.text_secondary));
            }
            if (btnToggleSearch != null) btnToggleSearch.setColorFilter(ContextCompat.getColor(activity, R.color.text_secondary));
            if (btnMeterPower != null) btnMeterPower.setBackgroundResource(R.drawable.bg_pill_badge);
            if (btnMeterSwitch != null) btnMeterSwitch.setBackgroundResource(R.drawable.bg_pill_badge);
            if (textMeterPower != null) textMeterPower.setTextColor(ContextCompat.getColor(activity, R.color.text_primary));
            if (textMeterSwitch != null) textMeterSwitch.setTextColor(ContextCompat.getColor(activity, R.color.text_primary));

            applyDarkPalette(activePalette, density);
        }
    }

    private void styleSharedSubCard(@Nullable View card, int drawableRes) {
        if (card == null) return;
        card.setTag(null);
        card.setBackgroundResource(drawableRes);
    }

    public void applyDarkPalette(NeonPalette palette, float density) {
        this.activePalette = palette != null ? palette : NeonPalette.DEFAULT;
        if (!currentMode.isDarkCanvas) return;

        DynamicThemeRenderer.applyCosmicCanvas(rootScroll, activePalette, density);
        DynamicThemeRenderer.applySubCard(cardEqPresets, activePalette, density);
        DynamicThemeRenderer.applySubCard(cardAudioFrequency, activePalette, density);
        DynamicThemeRenderer.applySubCard(cardUpNext, activePalette, density);
        DynamicThemeRenderer.applySubtitlePill(activity, badgeBrandSubtitle, activePalette);
        DynamicThemeRenderer.applyAccentTextColor(badgeEqMode, activePalette.secondaryNeon);

        if (iconUiStyle != null) iconUiStyle.setColorFilter(activePalette.secondaryNeon);
        if (btnPickFiles != null) btnPickFiles.setColorFilter(activePalette.secondaryNeon);

        if (currentMode == UiStyleMode.FLOATING_MINI) {
            if (floatingWaveform != null) {
                floatingWaveform.setPaletteColors(
                        activePalette.primaryNeon, activePalette.secondaryNeon, activePalette.tertiaryGlow);
            }
            if (floatingSeekBar != null) {
                DynamicThemeRenderer.applySeekBarColors(floatingSeekBar, activePalette);
            }
            if (floatingBtnPlayPause != null) {
                DynamicThemeRenderer.applyPlayButton(floatingBtnPlayPause, activePalette, density);
            }
        }
        if (equalizerView != null) {
            equalizerView.setPaletteColors(
                    activePalette.secondaryNeon, activePalette.tertiaryGlow, activePalette.primaryNeon);
        }
    }

    public int getAccentTextColorForTabs() {
        if (currentMode == UiStyleMode.RETRO_VINYL) return 0xFFC25216;
        if (currentMode == UiStyleMode.LIQUID_GLASS) return 0xFF6D28D9;
        return activePalette.secondaryNeon;
    }

    public int getSecondaryTextColorForTabs() {
        if (currentMode == UiStyleMode.RETRO_VINYL) return 0xFF7A5638;
        if (currentMode == UiStyleMode.LIQUID_GLASS) return 0xFF4F46E5;
        return ContextCompat.getColor(activity, R.color.text_secondary);
    }

    public void bindTrack(Track track, List<Track> displayedTracks) {
        if (track == null) return;
        this.currentTrackId = track.getId();

        if (floatingTitle != null) floatingTitle.setText(track.getTitle());
        if (floatingArtist != null) floatingArtist.setText(track.getArtist());
        if (miniBarTitle != null) miniBarTitle.setText(track.getTitle());
        if (miniBarArtist != null) miniBarArtist.setText(track.getArtist());
        if (retroTitle != null) retroTitle.setText(track.getTitle());
        if (retroArtist != null) retroArtist.setText(track.getArtist());
        if (liquidTitle != null) liquidTitle.setText(track.getTitle());
        if (liquidArtist != null) liquidArtist.setText(track.getArtist());

        if (track.getDurationMs() > 0) {
            String totalStr = TimeFormatter.formatDuration(track.getDurationMs());
            int max = (int) track.getDurationMs();
            setSeekMaxAndTotal(floatingSeekBar, floatingTotalTime, max, totalStr);
            setSeekMaxAndTotal(miniBarSeekBar, miniBarTotalTime, max, totalStr);
            setSeekMaxAndTotal(retroSeekBar, retroTotalTime, max, totalStr);
            setSeekMaxAndTotal(liquidSeekBar, liquidTotalTime, max, totalStr);
            lastProgressDurMs = track.getDurationMs();
            lastProgressDurSec = track.getDurationMs() / 1000L;
        }
        lastProgressPosSec = -1L;

        updateFavoriteUi(track.isFavorite());
        setDefaultArt();
        loadNeighborArtForLiquidStack(track, displayedTracks);
    }

    public void onActiveAlbumBitmapLoaded(String trackId, @Nullable Bitmap bitmap) {
        if (currentTrackId == null || !currentTrackId.equals(trackId)) return;
        if (bitmap != null && !bitmap.isRecycled()) {
            if (floatingAlbumArt != null) floatingAlbumArt.setImageBitmap(bitmap);
            if (miniBarArt != null) miniBarArt.setImageBitmap(bitmap);
            if (liquidArtCenter != null) liquidArtCenter.setImageBitmap(bitmap);
            if (retroVinylView != null) retroVinylView.setAlbumBitmap(bitmap);
        } else {
            setDefaultArt();
        }
    }

    private void setDefaultArt() {
        if (floatingAlbumArt != null) floatingAlbumArt.setImageResource(R.drawable.img_default_album_art);
        if (miniBarArt != null) miniBarArt.setImageResource(R.drawable.img_default_album_art);
        if (liquidArtCenter != null) liquidArtCenter.setImageResource(R.drawable.img_default_album_art);
        if (retroVinylView != null) retroVinylView.setAlbumBitmap(null);
    }

    private void loadNeighborArtForLiquidStack(Track current, List<Track> list) {
        if (liquidArtPrev == null || liquidArtNext == null) return;
        liquidArtPrev.setImageResource(R.drawable.img_default_album_art);
        liquidArtNext.setImageResource(R.drawable.img_default_album_art);
        if (list == null || list.size() < 2) return;

        int idx = -1;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getId().equals(current.getId())) {
                idx = i;
                break;
            }
        }
        if (idx < 0) return;

        Track prevTrack = list.get((idx - 1 + list.size()) % list.size());
        Track nextTrack = list.get((idx + 1) % list.size());
        int artPx = Math.round(116 * activity.getResources().getDisplayMetrics().density);
        String expectedId = current.getId();

        repository.loadAlbumArtAsync(prevTrack, artPx, (tid, bmp) -> {
            if (expectedId.equals(currentTrackId) && bmp != null && !bmp.isRecycled()) {
                liquidArtPrev.setImageBitmap(bmp);
            }
        });
        repository.loadAlbumArtAsync(nextTrack, artPx, (tid, bmp) -> {
            if (expectedId.equals(currentTrackId) && bmp != null && !bmp.isRecycled()) {
                liquidArtNext.setImageBitmap(bmp);
            }
        });
    }

    public void updatePlayingState(boolean playing) {
        int iconRes = playing ? R.drawable.ic_pause : R.drawable.ic_play;
        if (floatingBtnPlayPause != null) floatingBtnPlayPause.setImageResource(iconRes);
        if (miniBarBtnPlayPause != null) miniBarBtnPlayPause.setImageResource(iconRes);
        if (retroBtnPlayPause != null) retroBtnPlayPause.setImageResource(iconRes);
        if (liquidBtnPlayPause != null) liquidBtnPlayPause.setImageResource(iconRes);

        if (floatingWaveform != null) floatingWaveform.setPlaying(playing);
        if (retroVinylView != null) retroVinylView.setPlaying(playing);
        if (retroVuMeter != null) retroVuMeter.setPlaying(playing);
    }

    public void setTargetVisualFps(int fps) {
        if (floatingWaveform != null) floatingWaveform.setTargetFps(fps);
        if (retroVinylView != null) retroVinylView.setTargetFps(fps);
        if (retroVuMeter != null) retroVuMeter.setTargetFps(fps);
    }

    public void updateSpectrum(float[] bars, float energy) {
        if (floatingWaveform != null && currentMode == UiStyleMode.FLOATING_MINI) {
            floatingWaveform.updateSpectrum(bars, energy);
        }
        if (retroVuMeter != null && currentMode == UiStyleMode.RETRO_VINYL) {
            retroVuMeter.updateLevel(energy);
        }
    }

    public void updateProgress(long positionMs, long durationMs) {
        if (currentMode == UiStyleMode.NEON_COSMIC) return;

        int pos = (int) positionMs;
        int max = (int) durationMs;
        long posSec = positionMs / 1000L;
        long durSec = durationMs > 0 ? (durationMs / 1000L) : -1L;

        boolean updateMax = durationMs > 0 && durationMs != lastProgressDurMs;
        String durStr = (durSec >= 0 && durSec != lastProgressDurSec)
                ? TimeFormatter.formatDuration(durationMs) : null;
        String posStr = posSec != lastProgressPosSec
                ? TimeFormatter.formatDuration(positionMs) : null;

        if (updateMax) lastProgressDurMs = durationMs;
        if (durStr != null) lastProgressDurSec = durSec;
        if (posStr != null) lastProgressPosSec = posSec;

        if (currentMode == UiStyleMode.FLOATING_MINI) {
            if (floatingCollapsed) {
                updateSeekPair(miniBarSeekBar, miniBarCurrentTime, miniBarTotalTime, pos, max, updateMax, posStr, durStr);
            } else {
                updateSeekPair(floatingSeekBar, floatingCurrentTime, floatingTotalTime, pos, max, updateMax, posStr, durStr);
            }
        } else if (currentMode == UiStyleMode.RETRO_VINYL) {
            updateSeekPair(retroSeekBar, retroCurrentTime, retroTotalTime, pos, max, updateMax, posStr, durStr);
        } else if (currentMode == UiStyleMode.LIQUID_GLASS) {
            updateSeekPair(liquidSeekBar, liquidCurrentTime, liquidTotalTime, pos, max, updateMax, posStr, durStr);
        }
    }

    public void updateFavoriteUi(boolean isFavorite) {
        int res = isFavorite ? R.drawable.ic_heart_filled : R.drawable.ic_heart_outline;
        if (floatingBtnFavorite != null) floatingBtnFavorite.setImageResource(res);
        if (retroBtnFavorite != null) retroBtnFavorite.setImageResource(res);
        if (liquidBtnFavorite != null) liquidBtnFavorite.setImageResource(res);
    }

    public void updateShuffleAndRepeatUi(boolean shuffleOn, int repeatMode) {
        // Floating Mini
        if (floatingBtnShuffle != null) {
            floatingBtnShuffle.setColorFilter(shuffleOn
                    ? activePalette.secondaryNeon : ContextCompat.getColor(activity, R.color.text_secondary));
        }
        if (miniBarBtnShuffle != null) {
            miniBarBtnShuffle.setColorFilter(shuffleOn ? 0xFF7C3AED : 0xFF64748B);
        }
        if (retroBtnShuffle != null) {
            retroBtnShuffle.setColorFilter(shuffleOn ? 0xFFC25216 : 0xFF8C6B4B);
        }
        if (liquidBtnShuffle != null) {
            liquidBtnShuffle.setColorFilter(shuffleOn ? 0xFF7C3AED : 0xFF475569);
        }

        int repeatIcon = repeatMode == Player.REPEAT_MODE_ONE ? R.drawable.ic_repeat_one : R.drawable.ic_repeat;
        boolean repeatActive = repeatMode != Player.REPEAT_MODE_OFF;
        boolean iconChanged = repeatIcon != lastRepeatIconRes;
        lastRepeatIconRes = repeatIcon;

        if (floatingBtnRepeat != null) {
            if (iconChanged) floatingBtnRepeat.setImageResource(repeatIcon);
            floatingBtnRepeat.setColorFilter(repeatActive
                    ? (repeatMode == Player.REPEAT_MODE_ONE ? activePalette.primaryNeon : activePalette.secondaryNeon)
                    : ContextCompat.getColor(activity, R.color.text_secondary));
        }
        if (miniBarBtnRepeat != null) {
            if (iconChanged) miniBarBtnRepeat.setImageResource(repeatIcon);
            miniBarBtnRepeat.setColorFilter(repeatActive ? 0xFF7C3AED : 0xFF64748B);
        }
        if (retroBtnRepeat != null) {
            if (iconChanged) retroBtnRepeat.setImageResource(repeatIcon);
            retroBtnRepeat.setColorFilter(repeatActive ? 0xFFC25216 : 0xFF8C6B4B);
        }
        if (liquidBtnRepeat != null) {
            if (iconChanged) liquidBtnRepeat.setImageResource(repeatIcon);
            liquidBtnRepeat.setColorFilter(repeatActive ? 0xFF7C3AED : 0xFF475569);
        }
    }

    public void syncRetroKnobsFromSystemAndFx() {
        if (retroKnobVolume != null) retroKnobVolume.setPercent(readSystemVolumePercent());
        if (retroKnobBass != null) retroKnobBass.setPercent(fxEngine.getBassBoostPercent());
        if (retroKnobTreble != null) retroKnobTreble.setPercent(readTreblePercentFromEq());
    }

    private int readSystemVolumePercent() {
        try {
            AudioManager am = (AudioManager) activity.getSystemService(Context.AUDIO_SERVICE);
            if (am == null) return 65;
            int max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            int cur = am.getStreamVolume(AudioManager.STREAM_MUSIC);
            return max > 0 ? Math.round((cur * 100f) / max) : 65;
        } catch (Throwable ignored) {
            return 65;
        }
    }

    private void applySystemVolumePercent(int percent) {
        try {
            AudioManager am = (AudioManager) activity.getSystemService(Context.AUDIO_SERVICE);
            if (am == null) return;
            int max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            int target = Math.round((percent / 100f) * max);
            am.setStreamVolume(AudioManager.STREAM_MUSIC, Math.max(0, Math.min(max, target)), 0);
        } catch (Throwable ignored) {}
    }

    private int readTreblePercentFromEq() {
        float[] gains = fxEngine.getUiBandGainsDb();
        float avgHigh = (gains[8] + gains[9]) * 0.5f;
        float norm = (avgHigh - AudioFxEngine.MIN_GAIN_DB) / (AudioFxEngine.MAX_GAIN_DB - AudioFxEngine.MIN_GAIN_DB);
        return Math.max(0, Math.min(100, Math.round(norm * 100f)));
    }

    private void setSeekMaxAndTotal(@Nullable SeekBar sb, @Nullable TextView totalTv, int max, String totalStr) {
        if (sb != null && sb.getMax() != max) sb.setMax(max);
        if (totalTv != null) totalTv.setText(totalStr);
    }

    private void updateSeekPair(
            @Nullable SeekBar sb,
            @Nullable TextView currentTv,
            @Nullable TextView totalTv,
            int pos,
            int max,
            boolean updateMax,
            @Nullable String posStr,
            @Nullable String durStr) {
        if (sb != null) {
            if (updateMax && max > 0) sb.setMax(max);
            sb.setProgress(pos);
        }
        if (currentTv != null && posStr != null) currentTv.setText(posStr);
        if (totalTv != null && durStr != null) totalTv.setText(durStr);
    }
}
