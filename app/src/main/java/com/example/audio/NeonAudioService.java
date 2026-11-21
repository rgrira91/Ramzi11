package com.example.audio;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import androidx.media3.common.TrackSelectionParameters;
import androidx.media3.common.audio.AudioProcessor;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.audio.AudioSink;
import androidx.media3.exoplayer.audio.DefaultAudioSink;
import androidx.media3.session.CommandButton;
import androidx.media3.session.MediaSession;
import androidx.media3.session.MediaSessionService;
import androidx.media3.session.SessionCommand;
import androidx.media3.session.SessionCommands;
import androidx.media3.session.SessionResult;
import com.example.MainActivity;
import com.example.util.DeviceResourceOptimizer;
import com.example.widget.NeonWaveWidgetProvider;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

public class NeonAudioService extends MediaSessionService {
    public static final String ACTION_WIDGET_PLAY_PAUSE = "com.example.action.WIDGET_PLAY_PAUSE";
    public static final String ACTION_WIDGET_NEXT = "com.example.action.WIDGET_NEXT";
    public static final String ACTION_WIDGET_PREV = "com.example.action.WIDGET_PREV";
    public static final String CMD_SYNC_AUDIO_PREFS = "com.example.cmd.SYNC_AUDIO_PREFS";

    private static volatile NeonAudioService activeInstance;

    private ExoPlayer player;
    private MediaSession mediaSession;
    private DeviceResourceOptimizer resourceOptimizer;
    private final Handler fadeHandler = new Handler(Looper.getMainLooper());
    private float fadeProgress = 1.0f;
    private final DeviceResourceOptimizer.OptimizerListener optimizerListener =
            tier -> applyMediaOptimizationParams();

    public static boolean handleWidgetActionIfActive(@Nullable String action) {
        NeonAudioService service = activeInstance;
        if (service == null || service.player == null || action == null) {
            return false;
        }
        return service.dispatchWidgetAction(action);
    }

    private boolean dispatchWidgetAction(@NonNull String action) {
        if (player == null || player.getMediaItemCount() == 0) {
            return false;
        }
        if (ACTION_WIDGET_PLAY_PAUSE.equals(action)) {
            if (player.isPlaying()) {
                player.pause();
            } else {
                if (player.getPlaybackState() == Player.STATE_ENDED) {
                    player.seekTo(0);
                }
                player.play();
            }
            return true;
        } else if (ACTION_WIDGET_NEXT.equals(action)) {
            if (player.hasNextMediaItem()) {
                player.seekToNextMediaItem();
                player.play();
            }
            return true;
        } else if (ACTION_WIDGET_PREV.equals(action)) {
            if (player.getCurrentPosition() > 3500L) {
                player.seekTo(0);
            } else if (player.hasPreviousMediaItem()) {
                player.seekToPreviousMediaItem();
                player.play();
            }
            return true;
        }
        return false;
    }

    private final Runnable fadeRunnable = new Runnable() {
        @Override
        public void run() {
            if (player == null) return;
            int cfSec = AudioFxEngine.getInstance().getCrossfadeSeconds();
            float step = cfSec > 0 ? Math.max(0.03f, 0.35f / cfSec) : 0.12f;
            fadeProgress = Math.min(1.0f, fadeProgress + step);
            player.setVolume(fadeProgress);
            if (fadeProgress < 1.0f) {
                fadeHandler.postDelayed(this, 35L);
            }
        }
    };

    private final Runnable crossfadeMonitorRunnable = new Runnable() {
        @Override
        public void run() {
            if (player == null || !player.isPlaying()) return;
            int cfSec = AudioFxEngine.getInstance().getCrossfadeSeconds();
            if (cfSec <= 0) {
                return;
            }
            long dur = player.getDuration();
            long pos = player.getCurrentPosition();
            long cfMs = cfSec * 1000L;
            if (dur > cfMs + 1500L && pos >= 0L && (player.hasNextMediaItem() || player.getRepeatMode() == Player.REPEAT_MODE_ALL)) {
                long remaining = dur - pos;
                if (remaining > 0L && remaining <= cfMs) {
                    fadeHandler.removeCallbacks(fadeRunnable);
                    float vol = Math.max(0.05f, Math.min(1.0f, (float) remaining / cfMs));
                    player.setVolume(vol);
                } else if (fadeProgress >= 1.0f && player.getVolume() < 0.99f) {
                    player.setVolume(1.0f);
                }
            }
            fadeHandler.postDelayed(this, 180L);
        }
    };

    @OptIn(markerClass = UnstableApi.class)
    @Override
    public void onCreate() {
        super.onCreate();
        activeInstance = this;
        AudioFxEngine fx = AudioFxEngine.getInstance();
        fx.init(this);
        resourceOptimizer = DeviceResourceOptimizer.getInstance(this);
        resourceOptimizer.addListener(optimizerListener);

        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build();

        int[] bufCfg = resourceOptimizer.getExoBufferConfig();
        DefaultLoadControl loadControl = new DefaultLoadControl.Builder()
                .setBufferDurationsMs(bufCfg[0], bufCfg[1], bufCfg[2], bufCfg[3])
                .setTargetBufferBytes(bufCfg[4])
                .setPrioritizeTimeOverSizeThresholds(true)
                .build();

        DefaultRenderersFactory renderersFactory = new DefaultRenderersFactory(this) {
            @Nullable
            @Override
            protected AudioSink buildAudioSink(
                    @NonNull Context context,
                    boolean enableFloatOutput,
                    boolean enableAudioTrackPlaybackParams) {
                AudioFxEngine engine = AudioFxEngine.getInstance();
                return new DefaultAudioSink.Builder(context)
                        .setEnableFloatOutput(enableFloatOutput || engine.isUltraHd4kEnabled())
                        .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                        .setAudioProcessors(new AudioProcessor[]{
                                engine.getDspAudioProcessor(),
                                engine.getAudioTapProcessor().getAudioProcessor()
                        })
                        .build();
            }
        };

        player = new ExoPlayer.Builder(this, renderersFactory)
                .setAudioAttributes(audioAttributes, true)
                .setHandleAudioBecomingNoisy(true)
                .setLoadControl(loadControl)
                .setWakeMode(C.WAKE_MODE_LOCAL)
                .build();
        player.setSkipSilenceEnabled(fx.isSkipSilenceEnabled());
        if (player.getAudioSessionId() > 0) {
            fx.attachToAudioSession(this, player.getAudioSessionId());
        }
        applyMediaOptimizationParams();

        player.addListener(new Player.Listener() {
            @Override
            public void onAudioSessionIdChanged(int audioSessionId) {
                AudioFxEngine.getInstance().attachToAudioSession(NeonAudioService.this, audioSessionId);
            }

            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                if (isPlaying && player != null) {
                    AudioFxEngine.getInstance().attachToAudioSession(
                            NeonAudioService.this, player.getAudioSessionId());
                    if (AudioFxEngine.getInstance().isSmoothFadeEnabled()) {
                        startSmoothFadeIn();
                    } else {
                        fadeHandler.removeCallbacks(fadeRunnable);
                        player.setVolume(1.0f);
                    }
                    fadeHandler.removeCallbacks(crossfadeMonitorRunnable);
                    if (AudioFxEngine.getInstance().getCrossfadeSeconds() > 0) {
                        fadeHandler.postDelayed(crossfadeMonitorRunnable, 180L);
                    }
                } else {
                    fadeHandler.removeCallbacks(crossfadeMonitorRunnable);
                }
                AudioFxEngine.getInstance().onPlaybackStateChanged(isPlaying);
                pushWidgetUpdate();
            }

            @Override
            public void onPositionDiscontinuity(
                    @NonNull Player.PositionInfo oldPosition,
                    @NonNull Player.PositionInfo newPosition,
                    int reason) {
                if (reason == Player.DISCONTINUITY_REASON_SEEK
                        || reason == Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT
                        || reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION) {
                    AudioFxEngine.getInstance().onPlaybackSeekOrReset();
                }
            }

            @Override
            public void onMediaItemTransition(@Nullable MediaItem mediaItem, int reason) {
                AudioFxEngine.getInstance().onPlaybackSeekOrReset();
                if ((AudioFxEngine.getInstance().isSmoothFadeEnabled()
                        || AudioFxEngine.getInstance().getCrossfadeSeconds() > 0)
                        && player != null && player.isPlaying()) {
                    startSmoothFadeIn();
                }
                pushWidgetUpdate();
            }
        });

        Intent launchIntent = new Intent(this, MainActivity.class);
        launchIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent sessionActivityPendingIntent = PendingIntent.getActivity(
                this,
                0,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        mediaSession = new MediaSession.Builder(this, player)
                .setSessionActivity(sessionActivityPendingIntent)
                .setCallback(new MediaSession.Callback() {
                    @NonNull
                    @Override
                    public MediaSession.ConnectionResult onConnect(
                            @NonNull MediaSession session,
                            @NonNull MediaSession.ControllerInfo controller) {
                        SessionCommands sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS
                                .buildUpon()
                                .add(new SessionCommand(CMD_SYNC_AUDIO_PREFS, Bundle.EMPTY))
                                .build();
                        return new MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                                .setAvailableSessionCommands(sessionCommands)
                                .build();
                    }

                    @NonNull
                    @Override
                    public ListenableFuture<SessionResult> onCustomCommand(
                            @NonNull MediaSession session,
                            @NonNull MediaSession.ControllerInfo controller,
                            @NonNull SessionCommand customCommand,
                            @NonNull Bundle args) {
                        if (CMD_SYNC_AUDIO_PREFS.equals(customCommand.customAction) && player != null) {
                            player.setSkipSilenceEnabled(AudioFxEngine.getInstance().isSkipSilenceEnabled());
                            applyMediaOptimizationParams();
                            return Futures.immediateFuture(new SessionResult(SessionResult.RESULT_SUCCESS));
                        }
                        return MediaSession.Callback.super.onCustomCommand(session, controller, customCommand, args);
                    }
                })
                .build();
    }

    private void applyMediaOptimizationParams() {
        if (player == null || resourceOptimizer == null) return;
        AudioFxEngine fx = AudioFxEngine.getInstance();
        boolean eqEnabled = fx.isEqEnabled();
        // Keep PCM AudioProcessor pipeline active whenever DSP EQ/Bass or live spectrum/VU/LED listeners are active
        boolean useOffload = !fx.isSpectrumTapNeeded()
                && !fx.isDspActive()
                && resourceOptimizer.shouldUseAudioOffload(eqEnabled);
        TrackSelectionParameters.AudioOffloadPreferences offloadPrefs =
                new TrackSelectionParameters.AudioOffloadPreferences.Builder()
                        .setAudioOffloadMode(useOffload
                                ? TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED
                                : TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_DISABLED)
                        .setIsGaplessSupportRequired(true)
                        .build();
        player.setTrackSelectionParameters(
                player.getTrackSelectionParameters()
                        .buildUpon()
                        .setAudioOffloadPreferences(offloadPrefs)
                        .build()
        );
        fx.updateAdaptiveVisualizerRate(this);
        fadeHandler.removeCallbacks(crossfadeMonitorRunnable);
        if (player.isPlaying() && fx.getCrossfadeSeconds() > 0) {
            fadeHandler.postDelayed(crossfadeMonitorRunnable, 180L);
        } else if (fadeProgress >= 1.0f && player.getVolume() < 0.99f) {
            player.setVolume(1.0f);
        }
    }

    private void startSmoothFadeIn() {
        if (player == null) return;
        fadeHandler.removeCallbacks(fadeRunnable);
        fadeProgress = 0.15f;
        player.setVolume(fadeProgress);
        fadeHandler.postDelayed(fadeRunnable, 35L);
    }

    private void pushWidgetUpdate() {
        if (player == null) return;
        MediaItem current = player.getCurrentMediaItem();
        String title = null;
        String artist = null;
        if (current != null && current.mediaMetadata != null) {
            MediaMetadata meta = current.mediaMetadata;
            if (meta.title != null) title = meta.title.toString();
            if (meta.artist != null) artist = meta.artist.toString();
        }
        NeonWaveWidgetProvider.updateAllWidgets(this, title, artist, player.isPlaying());
    }

    @Override
    public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null && player != null) {
            String action = intent.getAction();
            if (ACTION_WIDGET_PLAY_PAUSE.equals(action)) {
                if (player.getMediaItemCount() == 0) {
                    Intent openApp = new Intent(this, MainActivity.class);
                    openApp.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    startActivity(openApp);
                } else if (player.isPlaying()) {
                    player.pause();
                } else {
                    player.play();
                }
            } else if (ACTION_WIDGET_NEXT.equals(action)) {
                if (player.hasNextMediaItem()) {
                    player.seekToNextMediaItem();
                    player.play();
                }
            } else if (ACTION_WIDGET_PREV.equals(action)) {
                if (player.hasPreviousMediaItem()) {
                    player.seekToPreviousMediaItem();
                    player.play();
                }
            }
        }
        return super.onStartCommand(intent, flags, startId);
    }

    @Nullable
    @Override
    public MediaSession onGetSession(@NonNull MediaSession.ControllerInfo controllerInfo) {
        return mediaSession;
    }

    @Override
    public void onTaskRemoved(@Nullable Intent rootIntent) {
        if (player != null && (!player.getPlayWhenReady() || player.getMediaItemCount() == 0)) {
            stopSelf();
        }
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (resourceOptimizer != null && resourceOptimizer.isSevereMemoryTrimLevel(level)) {
            if (player != null && !player.isPlaying()) {
                fadeHandler.removeCallbacks(fadeRunnable);
            }
            AudioFxEngine.getInstance().setVisualizerActive(false);
        }
    }

    @Override
    public void onDestroy() {
        if (activeInstance == this) {
            activeInstance = null;
        }
        if (resourceOptimizer != null) {
            resourceOptimizer.removeListener(optimizerListener);
        }
        fadeHandler.removeCallbacksAndMessages(null);
        AudioFxEngine.getInstance().onPlaybackStateChanged(false);
        AudioFxEngine.getInstance().setSpectrumFrameListener(this, null);
        AudioFxEngine.getInstance().setVisualizerListener(this, null);
        AudioFxEngine.getInstance().releaseHardwareFx();
        if (mediaSession != null) {
            mediaSession.release();
            mediaSession = null;
        }
        if (player != null) {
            player.release();
            player = null;
        }
        super.onDestroy();
    }
}
