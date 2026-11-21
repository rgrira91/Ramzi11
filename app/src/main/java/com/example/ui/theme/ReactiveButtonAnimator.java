package com.example.ui.theme;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.Nullable;
import com.google.android.material.imageview.ShapeableImageView;
import java.util.ArrayList;
import java.util.List;

/**
 * Drives multi-color pulsing neon animations for interactive buttons,
 * tactile press/release spring bounce, and whole-screen beat-reactive Disco Mode motion.
 */
public class ReactiveButtonAnimator {

    private static final int[] DARK_NEON_CYCLE = {
            0xFF22D3EE, // Electric Cyan
            0xFF38BDF8, // Sky Neon
            0xFFA855F7, // Cosmic Violet
            0xFFEC4899, // Hot Magenta
            0xFFFB7185, // Coral Rose
            0xFFF59E0B, // Warm Amber
            0xFF10B981  // Neon Mint
    };

    private static final int[] LIGHT_RICH_CYCLE = {
            0xFF0284C7, // Ocean Blue
            0xFF4F46E5, // Indigo
            0xFF7C3AED, // Deep Violet
            0xFFDB2777, // Vivid Rose
            0xFFEA580C, // Tangerine
            0xFF059669  // Emerald
    };

    private static final class PillTarget {
        final View container;
        @Nullable final ImageView icon;
        @Nullable final TextView label;
        final float phaseOffsetDeg;
        final float cornerDp;
        GradientDrawable bgDrawable;
        Drawable originalBg;
        boolean savedOriginalBg = false;

        PillTarget(View container, @Nullable ImageView icon, @Nullable TextView label, float phaseOffsetDeg, float cornerDp) {
            this.container = container;
            this.icon = icon;
            this.label = label;
            this.phaseOffsetDeg = phaseOffsetDeg;
            this.cornerDp = cornerDp;
        }
    }

    private static final class IconTarget {
        final ImageView view;
        final float phaseOffsetDeg;
        final boolean subtle;
        final boolean pulseHalo;
        GradientDrawable haloDrawable;
        Drawable originalBg;
        boolean savedOriginalBg = false;

        IconTarget(ImageView view, float phaseOffsetDeg, boolean subtle, boolean pulseHalo) {
            this.view = view;
            this.phaseOffsetDeg = phaseOffsetDeg;
            this.subtle = subtle;
            this.pulseHalo = pulseHalo;
        }
    }

    private static final class CardPulseTarget {
        final View card;
        final float phaseOffsetDeg;
        final int[] colorBuf = new int[3];

        CardPulseTarget(View card, float phaseOffsetDeg) {
            this.card = card;
            this.phaseOffsetDeg = phaseOffsetDeg;
        }
    }

    private final List<ImageButton> playButtons = new ArrayList<>();
    private final List<PillTarget> pillButtons = new ArrayList<>();
    private final List<IconTarget> iconButtons = new ArrayList<>();
    private final List<ShapeableImageView> albumArtViews = new ArrayList<>();
    private final List<CardPulseTarget> discoPulseCards = new ArrayList<>();
    @Nullable private View heroCardView;
    @Nullable private View rootCanvasView;
    @Nullable private com.example.ui.views.GlassNavBar glassNavBarView;
    private GradientDrawable discoCanvasBg;
    private final int[] discoCanvasColors = new int[3];
    private final int[] heroColorsBuf = new int[3];
    private final int[] playColorsBuf = new int[3];
    private final android.graphics.Rect visibleRectScratch = new android.graphics.Rect();
    private int lastArtStrokeColor = 0;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean running = false;
    private boolean idleSettled = false;
    private boolean isPlaying = false;
    private boolean isFavorite = false;
    private boolean shuffleActive = false;
    private boolean repeatActive = false;
    private boolean abLoopActive = false;
    private boolean colorPulseEnabled = true;
    private boolean discoClubMode = false;
    private boolean partyStrobeSpeedBoost = false;
    private UiStyleMode uiStyleMode = UiStyleMode.NEON_COSMIC;

    private float huePhaseDeg = 0f;
    private float targetBass = 0f;
    private float smoothBass = 0f;
    private float prevSmoothBass = 0f;
    private float targetEnergy = 0f;
    private float smoothEnergy = 0f;
    private float beatFlash = 0f;
    private long lastTickMs = 0L;
    private long frameIntervalMs = 24L;
    private float density = 2f;

    @Nullable private ImageView favoriteIconPrimary;
    @Nullable private ImageView shuffleIconPrimary;
    @Nullable private ImageView repeatIconPrimary;

    private final Runnable tickRunnable = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            stepAnimation();
            if (!isPlaying && !discoClubMode && beatFlash <= 0.01f && smoothBass <= 0.01f) {
                idleSettled = true;
                return;
            }
            idleSettled = false;
            handler.postDelayed(this, frameIntervalMs);
        }
    };

    public void setDensity(float density) {
        if (density > 0f) this.density = density;
    }

    public void setTargetFps(int fps) {
        int clamped = Math.max(12, Math.min(60, fps));
        this.frameIntervalMs = Math.max(16L, Math.round(1000.0 / clamped));
    }

    public void setColorPulseEnabled(boolean enabled) {
        this.colorPulseEnabled = enabled;
        if (!enabled) {
            restoreDisabledButtonState();
        } else {
            wakeIfRunning();
        }
    }

    public boolean isColorPulseEnabled() {
        return colorPulseEnabled;
    }

    public void setDiscoClubMode(boolean discoClubMode) {
        if (this.discoClubMode == discoClubMode) return;
        this.discoClubMode = discoClubMode;
        if (!discoClubMode) {
            resetDiscoCardTransforms();
        } else {
            wakeIfRunning();
        }
    }

    public boolean isDiscoClubMode() {
        return discoClubMode;
    }

    public void setPartyStrobeSpeedBoost(boolean boost) {
        this.partyStrobeSpeedBoost = boost;
    }

    public void setUiStyleMode(UiStyleMode mode) {
        this.uiStyleMode = mode != null ? mode : UiStyleMode.NEON_COSMIC;
    }

    public void setHeroCard(@Nullable View heroCard) {
        this.heroCardView = heroCard;
    }

    public void setRootCanvasView(@Nullable View rootCanvas) {
        this.rootCanvasView = rootCanvas;
    }

    public void setGlassNavBar(@Nullable com.example.ui.views.GlassNavBar glassNavBar) {
        this.glassNavBarView = glassNavBar;
    }

    public void registerPulseCard(@Nullable View cardView, float phaseOffsetDeg) {
        if (cardView == null) return;
        for (CardPulseTarget existing : discoPulseCards) {
            if (existing.card == cardView) return;
        }
        discoPulseCards.add(new CardPulseTarget(cardView, phaseOffsetDeg));
    }

    public void registerAlbumArt(@Nullable ShapeableImageView artView) {
        if (artView != null && !albumArtViews.contains(artView)) {
            albumArtViews.add(artView);
        }
    }

    public void registerPlayButton(@Nullable ImageButton button) {
        if (button == null) return;
        if (!playButtons.contains(button)) {
            playButtons.add(button);
            attachTouchBounce(button);
        }
    }

    public void registerPillButton(
            @Nullable View container,
            @Nullable ImageView icon,
            @Nullable TextView label,
            float phaseOffsetDeg,
            float cornerDp) {
        if (container == null) return;
        attachTouchBounce(container);
        PillTarget pt = new PillTarget(container, icon, label, phaseOffsetDeg, cornerDp);
        pt.originalBg = container.getBackground();
        pt.savedOriginalBg = true;
        pillButtons.add(pt);
    }

    public void registerIconButton(@Nullable ImageView button, float phaseOffsetDeg, boolean subtle) {
        registerIconButton(button, phaseOffsetDeg, subtle, true);
    }

    public void registerIconButton(@Nullable ImageView button, float phaseOffsetDeg, boolean subtle, boolean pulseHalo) {
        if (button == null) return;
        attachTouchBounce(button);
        IconTarget it = new IconTarget(button, phaseOffsetDeg, subtle, pulseHalo);
        it.originalBg = button.getBackground();
        it.savedOriginalBg = true;
        iconButtons.add(it);
    }

    public void setSpecialButtonRefs(
            @Nullable ImageView favoriteIcon,
            @Nullable ImageView shuffleIcon,
            @Nullable ImageView repeatIcon) {
        this.favoriteIconPrimary = favoriteIcon;
        this.shuffleIconPrimary = shuffleIcon;
        this.repeatIconPrimary = repeatIcon;
    }

    public void setPlaybackFlags(boolean playing, boolean favorite, boolean shuffleOn, boolean repeatOn, boolean abActive) {
        this.isPlaying = playing;
        this.isFavorite = favorite;
        this.shuffleActive = shuffleOn;
        this.repeatActive = repeatOn;
        this.abLoopActive = abActive;
        if (!playing) {
            targetBass = 0f;
            targetEnergy = 0f;
        }
        wakeIfRunning();
    }

    private void wakeIfRunning() {
        if (running) {
            idleSettled = false;
            lastTickMs = SystemClock.uptimeMillis();
            handler.removeCallbacks(tickRunnable);
            handler.post(tickRunnable);
        }
    }

    public void onAudioFrame(float energy, float bassEnergy) {
        this.targetEnergy = Math.max(0f, Math.min(1f, energy));
        this.targetBass = Math.max(0f, Math.min(1f, bassEnergy));
    }

    public void start() {
        if (running) {
            if (idleSettled) {
                wakeIfRunning();
            }
            return;
        }
        running = true;
        idleSettled = false;
        lastTickMs = SystemClock.uptimeMillis();
        handler.removeCallbacks(tickRunnable);
        handler.post(tickRunnable);
    }

    public void stop() {
        running = false;
        idleSettled = false;
        handler.removeCallbacks(tickRunnable);
        resetScales();
        resetDiscoCardTransforms();
    }

    private void resetScales() {
        for (ImageButton btn : playButtons) {
            btn.setScaleX(1f);
            btn.setScaleY(1f);
        }
        for (IconTarget item : iconButtons) {
            item.view.setScaleX(1f);
            item.view.setScaleY(1f);
        }
        for (PillTarget pill : pillButtons) {
            pill.container.setScaleX(1f);
            pill.container.setScaleY(1f);
        }
        for (ShapeableImageView art : albumArtViews) {
            art.setScaleX(1f);
            art.setScaleY(1f);
        }
    }

    private void resetDiscoCardTransforms() {
        for (CardPulseTarget target : discoPulseCards) {
            target.card.setScaleX(1f);
            target.card.setScaleY(1f);
        }
    }

    private void restoreDisabledButtonState() {
        for (ImageButton btn : playButtons) {
            btn.setScaleX(1f);
            btn.setScaleY(1f);
        }
        for (IconTarget item : iconButtons) {
            item.view.setScaleX(1f);
            item.view.setScaleY(1f);
            item.view.clearColorFilter();
            if (item.savedOriginalBg && item.view.getBackground() == item.haloDrawable) {
                item.view.setBackground(item.originalBg);
            }
        }
        for (PillTarget pill : pillButtons) {
            pill.container.setScaleX(1f);
            pill.container.setScaleY(1f);
        }
    }

    private void stepAnimation() {
        long now = SystemClock.uptimeMillis();
        if (lastTickMs == 0L) lastTickMs = now;
        float dt = Math.min(0.06f, Math.max(0.004f, (now - lastTickMs) / 1000f));
        lastTickMs = now;

        prevSmoothBass = smoothBass;
        float bassRate = 1f - (float) Math.exp(-dt * (targetBass > smoothBass ? 24.0 : 10.0));
        smoothBass += (targetBass - smoothBass) * bassRate;
        float energyRate = 1f - (float) Math.exp(-dt * (targetEnergy > smoothEnergy ? 20.0 : 9.0));
        smoothEnergy += (targetEnergy - smoothEnergy) * energyRate;

        // Beat onset spike for snappy color jump & button/screen beat pulse
        if (isPlaying && smoothBass - prevSmoothBass > 0.06f && smoothBass > 0.35f) {
            beatFlash = Math.min(1f, smoothBass * 1.25f);
            if (discoClubMode || partyStrobeSpeedBoost) {
                huePhaseDeg = (huePhaseDeg + 18f) % 360f;
            }
        }
        beatFlash = Math.max(0f, beatFlash - dt * 3.4f);

        float clubBoost = (discoClubMode || partyStrobeSpeedBoost) ? 2.25f : 1.0f;
        float speedDegPerSec = (isPlaying ? (68f + smoothEnergy * 115f) : 34f) * clubBoost;
        huePhaseDeg = (huePhaseDeg + dt * speedDegPerSec) % 360f;

        boolean lightCanvas = uiStyleMode != null && !uiStyleMode.isDarkCanvas;
        float beatPulse = isPlaying
                ? Math.min(1f, Math.max(smoothBass, beatFlash) * (discoClubMode ? 1.25f : 1.05f))
                : (0.18f + 0.14f * (float) Math.sin(Math.toRadians(huePhaseDeg * 2f)));

        int ringC1 = sampleCycleColor(huePhaseDeg, lightCanvas);
        int ringC2 = sampleCycleColor(huePhaseDeg + 115f, lightCanvas);
        int ringC3 = sampleCycleColor(huePhaseDeg + 230f, lightCanvas);

        if (colorPulseEnabled) {
            // 1. Multi-color pulsing Play/Pause buttons + vivid beat scale
            float playScale = 1.0f + (isPlaying
                    ? (beatPulse * (discoClubMode ? 0.125f : 0.095f))
                    : (0.028f * (0.5f + 0.5f * (float) Math.sin(Math.toRadians(huePhaseDeg * 2f)))));

            for (ImageButton playBtn : playButtons) {
                if (!playBtn.isShown()) continue;
                if (!Boolean.TRUE.equals(playBtn.getTag(com.example.R.id.hero_player_card))) {
                    playBtn.setScaleX(playScale);
                    playBtn.setScaleY(playScale);
                }
                updatePlayButtonGradient(playBtn, ringC1, ringC2, ringC3, lightCanvas);
            }

            // 2. Multi-color pulsing pill buttons with glowing dual-tone fills, strokes & micro-bounce
            float pillScale = isPlaying ? (1.0f + beatPulse * (discoClubMode ? 0.036f : 0.02f)) : 1.0f;
            for (PillTarget pill : pillButtons) {
                if (!pill.container.isShown()) continue;
                int c1 = sampleCycleColor(huePhaseDeg + pill.phaseOffsetDeg, lightCanvas);
                int c2 = sampleCycleColor(huePhaseDeg + pill.phaseOffsetDeg + 95f, lightCanvas);
                updatePillStyling(pill, c1, c2, beatPulse, lightCanvas);
                if (!Boolean.TRUE.equals(pill.container.getTag(com.example.R.id.hero_player_card))) {
                    pill.container.setScaleX(pillScale);
                    pill.container.setScaleY(pillScale);
                }
            }

            // 3. Multi-color pulsing transport, header & toolbar icon buttons with neon halo
            float iconBeatScale = isPlaying ? (1.0f + beatPulse * (discoClubMode ? 0.10f : 0.065f)) : 1.0f;
            for (IconTarget item : iconButtons) {
                if (!item.view.isShown()) continue;
                int color = sampleCycleColor(huePhaseDeg + item.phaseOffsetDeg, lightCanvas);
                int glowColor = sampleCycleColor(huePhaseDeg + item.phaseOffsetDeg + 75f, lightCanvas);

                if (item.view == favoriteIconPrimary) {
                    int favCol = isFavorite
                            ? NeonPalette.lerpColor(0xFFEC4899, 0xFFFACC15, 0.5f + 0.5f * (float) Math.sin(Math.toRadians(huePhaseDeg * 3f)))
                            : NeonPalette.withAlpha(color, lightCanvas ? 0xDD : 0xEE);
                    item.view.setColorFilter(favCol);
                    updateIconHalo(item, isFavorite ? favCol : color, glowColor, beatPulse, lightCanvas);
                    if (!Boolean.TRUE.equals(item.view.getTag(com.example.R.id.hero_player_card))) {
                        float fScale = (isFavorite && isPlaying) ? (1.0f + beatPulse * 0.15f) : iconBeatScale;
                        item.view.setScaleX(fScale);
                        item.view.setScaleY(fScale);
                    }
                    continue;
                }
                if (item.view == shuffleIconPrimary && !shuffleActive) {
                    int muted = sampleCycleColor(huePhaseDeg + item.phaseOffsetDeg, lightCanvas);
                    item.view.setColorFilter(NeonPalette.withAlpha(muted, lightCanvas ? 0xB8 : 0xCC));
                    updateIconHalo(item, muted, glowColor, beatPulse * 0.6f, lightCanvas);
                    if (!Boolean.TRUE.equals(item.view.getTag(com.example.R.id.hero_player_card))) {
                        item.view.setScaleX(iconBeatScale);
                        item.view.setScaleY(iconBeatScale);
                    }
                    continue;
                }
                if (item.view == repeatIconPrimary && !repeatActive) {
                    int muted = sampleCycleColor(huePhaseDeg + item.phaseOffsetDeg, lightCanvas);
                    item.view.setColorFilter(NeonPalette.withAlpha(muted, lightCanvas ? 0xB8 : 0xCC));
                    updateIconHalo(item, muted, glowColor, beatPulse * 0.6f, lightCanvas);
                    if (!Boolean.TRUE.equals(item.view.getTag(com.example.R.id.hero_player_card))) {
                        item.view.setScaleX(iconBeatScale);
                        item.view.setScaleY(iconBeatScale);
                    }
                    continue;
                }

                if (item.subtle) {
                    color = NeonPalette.withAlpha(color, lightCanvas ? 0xDC : 0xEE);
                }
                item.view.setColorFilter(color);
                updateIconHalo(item, color, glowColor, beatPulse, lightCanvas);
                if (!Boolean.TRUE.equals(item.view.getTag(com.example.R.id.hero_player_card))) {
                    item.view.setScaleX(iconBeatScale);
                    item.view.setScaleY(iconBeatScale);
                }
            }
        }

        // 4. Player Album Art beat pulse + neon glowing border (skip ColorStateList churn when color unchanged)
        float artScale = isPlaying ? (1.0f + beatPulse * (discoClubMode ? 0.068f : 0.045f)) : 1.0f;
        int artStroke = NeonPalette.withAlpha(sampleCycleColor(huePhaseDeg + 45f, lightCanvas), isPlaying ? 0xF0 : 0xAA);
        android.content.res.ColorStateList artCsl = (artStroke != lastArtStrokeColor)
                ? android.content.res.ColorStateList.valueOf(artStroke)
                : null;
        if (artCsl != null) {
            lastArtStrokeColor = artStroke;
        }
        for (ShapeableImageView art : albumArtViews) {
            if (!art.isShown()) continue;
            art.setScaleX(artScale);
            art.setScaleY(artScale);
            if (artCsl != null) {
                art.setStrokeColor(artCsl);
            }
        }

        // 4b. Sync GlassNavBar accent & subtle disco rim glow when pulsing
        if (glassNavBarView != null && glassNavBarView.isShown()) {
            if (colorPulseEnabled && (isPlaying || discoClubMode)) {
                glassNavBarView.setAccentColor(ringC1);
                if (discoClubMode && !lightCanvas) {
                    glassNavBarView.setGlassTint(NeonPalette.withAlpha(ringC2, 0x3A));
                }
            }
        }

        // 5. Hero Player Card border glow (or full-screen Disco Mode whole-screen beat reactivity!)
        if (discoClubMode && !lightCanvas) {
            stepWholeScreenDiscoReaction(ringC1, ringC2, ringC3, beatPulse);
        } else if (heroCardView != null && heroCardView.isShown() && !lightCanvas) {
            Drawable bg = heroCardView.getBackground();
            if (bg instanceof LayerDrawable && ((LayerDrawable) bg).getNumberOfLayers() >= 1) {
                Drawable border = ((LayerDrawable) bg).getDrawable(0);
                if (border instanceof GradientDrawable) {
                    int bAlpha = Math.round((0.55f + 0.45f * beatPulse) * 255f);
                    heroColorsBuf[0] = NeonPalette.withAlpha(ringC1, bAlpha);
                    heroColorsBuf[1] = NeonPalette.withAlpha(ringC2, bAlpha);
                    heroColorsBuf[2] = NeonPalette.withAlpha(ringC3, bAlpha);
                    ((GradientDrawable) border).setColors(heroColorsBuf);
                }
            }
        }
    }

    private void stepWholeScreenDiscoReaction(int c1, int c2, int c3, float beatPulse) {
        // A. Whole-screen cosmic background gradient pulses & shifts with musical rhythm
        if (rootCanvasView != null) {
            if (discoCanvasBg == null) {
                discoCanvasBg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xFF28084D, 0xFF190636, 0xFF090218});
            }
            float bgIntensity = isPlaying ? (0.14f + 0.24f * beatPulse) : 0.14f;
            discoCanvasColors[0] = NeonPalette.lerpColor(0xFF0D041E, c1, bgIntensity);
            discoCanvasColors[1] = NeonPalette.lerpColor(0xFF080214, c2, bgIntensity * 0.72f);
            discoCanvasColors[2] = NeonPalette.lerpColor(0xFF05010E, c3, bgIntensity * 0.85f);
            discoCanvasBg.setColors(discoCanvasColors);
            if (rootCanvasView.getBackground() != discoCanvasBg) {
                rootCanvasView.setBackground(discoCanvasBg);
            }
        }

        // B. Every card currently visible on screen pulses subtly in scale & glows with beat-synced rainbow rims (0 alloc)
        for (CardPulseTarget target : discoPulseCards) {
            View card = target.card;
            if (!card.isShown() || !card.getLocalVisibleRect(visibleRectScratch)) continue;
            float s = isPlaying ? (1.0f + beatPulse * 0.016f) : 1.0f;
            card.setScaleX(s);
            card.setScaleY(s);

            int cardC1 = sampleCycleColor(huePhaseDeg + target.phaseOffsetDeg, false);
            int cardC2 = sampleCycleColor(huePhaseDeg + target.phaseOffsetDeg + 110f, false);
            int cardC3 = sampleCycleColor(huePhaseDeg + target.phaseOffsetDeg + 220f, false);

            Drawable bg = card.getBackground();
            if (bg instanceof LayerDrawable && ((LayerDrawable) bg).getNumberOfLayers() >= 1) {
                Drawable border = ((LayerDrawable) bg).getDrawable(0);
                if (border instanceof GradientDrawable) {
                    int bAlpha = Math.round((0.55f + 0.45f * beatPulse) * 255f);
                    target.colorBuf[0] = NeonPalette.withAlpha(cardC1, bAlpha);
                    target.colorBuf[1] = NeonPalette.withAlpha(cardC2, bAlpha);
                    target.colorBuf[2] = NeonPalette.withAlpha(cardC3, bAlpha);
                    ((GradientDrawable) border).setColors(target.colorBuf);
                }
            } else if (bg instanceof GradientDrawable) {
                int sAlpha = Math.round((0.45f + 0.55f * beatPulse) * 255f);
                ((GradientDrawable) bg).setStroke(Math.max(2, Math.round((1.5f + beatPulse) * density)),
                        NeonPalette.withAlpha(cardC1, sAlpha));
            }
        }
    }

    private void updateIconHalo(IconTarget item, int color, int glowColor, float beatPulse, boolean lightCanvas) {
        if (!item.pulseHalo) return;
        if (item.haloDrawable == null) {
            item.haloDrawable = new GradientDrawable();
            item.haloDrawable.setShape(GradientDrawable.OVAL);
        }
        int fillAlpha = isPlaying ? Math.round((0.16f + 0.24f * beatPulse) * 255f) : 0x26;
        int strokeAlpha = isPlaying ? Math.round((0.35f + 0.58f * beatPulse) * 255f) : 0x55;
        item.haloDrawable.setColor(NeonPalette.withAlpha(lightCanvas ? color : glowColor, fillAlpha));
        item.haloDrawable.setStroke(Math.max(1, Math.round((1.1f + 0.5f * beatPulse) * density)),
                NeonPalette.withAlpha(color, strokeAlpha));
        if (item.view.getBackground() != item.haloDrawable) {
            item.view.setBackground(item.haloDrawable);
        }
    }

    private void updatePlayButtonGradient(ImageButton btn, int c1, int c2, int c3, boolean lightCanvas) {
        Drawable bg = btn.getBackground();
        if (bg instanceof LayerDrawable && ((LayerDrawable) bg).getNumberOfLayers() >= 1) {
            Drawable ring = ((LayerDrawable) bg).getDrawable(0);
            if (ring instanceof GradientDrawable) {
                playColorsBuf[0] = c1;
                playColorsBuf[1] = c2;
                playColorsBuf[2] = c3;
                ((GradientDrawable) ring).setColors(playColorsBuf);
            }
        } else if (btn.getId() == com.example.R.id.btn_play_pause
                || btn.getId() == com.example.R.id.sticky_mini_btn_play_pause) {
            GradientDrawable ring = new GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    new int[]{c1, c2, c3});
            ring.setShape(GradientDrawable.OVAL);
            GradientDrawable center = new GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    new int[]{lightCanvas ? 0xFF1E1B4B : 0xFF28104B, lightCanvas ? 0xFF312E81 : 0xFF110C2B});
            center.setShape(GradientDrawable.OVAL);
            LayerDrawable layer = new LayerDrawable(new Drawable[]{ring, center});
            int inset = Math.max(2, Math.round(2.5f * density));
            layer.setLayerInset(1, inset, inset, inset, inset);
            btn.setTag(layer);
            btn.setBackground(layer);
        }
    }

    private void updatePillStyling(PillTarget pill, int c1, int c2, float pulse, boolean lightCanvas) {
        if (pill.bgDrawable == null || pill.container.getBackground() != pill.bgDrawable) {
            if (pill.bgDrawable == null) {
                pill.bgDrawable = new GradientDrawable();
                pill.bgDrawable.setCornerRadius(pill.cornerDp * density);
            }
            pill.container.setBackground(pill.bgDrawable);
        }
        int baseFill = lightCanvas ? 0xCCFFFFFF : 0x99171332;
        int tintedFill = NeonPalette.lerpColor(baseFill, NeonPalette.withAlpha(c1, lightCanvas ? 0x45 : 0x65),
                isPlaying ? (0.28f + 0.42f * pulse) : 0.25f);
        int strokeAlpha = Math.round((0.55f + 0.45f * pulse) * 255f);
        int strokeWidth = Math.max(1, Math.round((1.35f + 0.70f * pulse) * density));
        pill.bgDrawable.setColor(tintedFill);
        pill.bgDrawable.setStroke(strokeWidth, NeonPalette.withAlpha(c1, strokeAlpha));

        if (pill.icon != null) {
            pill.icon.setColorFilter(c1);
        }
        if (pill.label != null) {
            int id = pill.container.getId();
            if (id == com.example.R.id.btn_hero_lyrics
                    || id == com.example.R.id.btn_disco_mode_toggle
                    || id == com.example.R.id.btn_ui_style) {
                pill.label.setTextColor(c2);
            } else if (id == com.example.R.id.btn_ab_loop) {
                pill.label.setTextColor(abLoopActive ? c2 : (lightCanvas ? 0xFF334155 : 0xFFE2E8F0));
            } else if (id == com.example.R.id.btn_sort_tracks) {
                pill.label.setTextColor(c2);
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    public void attachTouchBounce(@Nullable View view) {
        if (view == null) return;
        view.setOnTouchListener((v, event) -> {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                v.setTag(com.example.R.id.hero_player_card, Boolean.TRUE);
                v.animate().cancel();
                v.animate()
                        .scaleX(0.89f)
                        .scaleY(0.89f)
                        .setDuration(75L)
                        .setInterpolator(new DecelerateInterpolator())
                        .start();
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                v.animate().cancel();
                v.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(220L)
                        .setInterpolator(new OvershootInterpolator(2.2f))
                        .withEndAction(() -> v.setTag(com.example.R.id.hero_player_card, null))
                        .start();
            }
            return false;
        });
    }

    public static void animateTrackSwap(@Nullable View artView, @Nullable View titleView, @Nullable View artistView) {
        if (artView != null) {
            artView.animate().cancel();
            artView.setScaleX(0.88f);
            artView.setScaleY(0.88f);
            artView.setAlpha(0.55f);
            artView.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .alpha(1f)
                    .setDuration(320L)
                    .setInterpolator(new OvershootInterpolator(1.3f))
                    .start();
        }
        if (titleView != null) {
            titleView.animate().cancel();
            titleView.setTranslationY(10f);
            titleView.setAlpha(0.35f);
            titleView.animate()
                    .translationY(0f)
                    .alpha(1f)
                    .setDuration(260L)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
        }
        if (artistView != null) {
            artistView.animate().cancel();
            artistView.setTranslationY(8f);
            artistView.setAlpha(0.35f);
            artistView.animate()
                    .translationY(0f)
                    .alpha(1f)
                    .setDuration(300L)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
        }
    }

    public static void animateMeterReveal(@Nullable View meterView, boolean show) {
        if (meterView == null) return;
        if (show) {
            if (meterView.getVisibility() != View.VISIBLE) {
                meterView.setVisibility(View.VISIBLE);
                meterView.setAlpha(0f);
                meterView.setScaleY(0.88f);
                meterView.animate()
                        .alpha(1f)
                        .scaleY(1f)
                        .setDuration(240L)
                        .setInterpolator(new DecelerateInterpolator())
                        .start();
            }
        } else {
            meterView.animate().cancel();
            meterView.setAlpha(1f);
            meterView.setScaleY(1f);
            meterView.setVisibility(View.GONE);
        }
    }

    public static int sampleCycleColor(float deg, boolean lightCanvas) {
        int[] palette = lightCanvas ? LIGHT_RICH_CYCLE : DARK_NEON_CYCLE;
        float wrapped = ((deg % 360f) + 360f) % 360f;
        float scaled = (wrapped / 360f) * palette.length;
        int idx0 = ((int) Math.floor(scaled)) % palette.length;
        int idx1 = (idx0 + 1) % palette.length;
        float t = scaled - (float) Math.floor(scaled);
        return NeonPalette.lerpColor(palette[idx0], palette[idx1], t);
    }
}
