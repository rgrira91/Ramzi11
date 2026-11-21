package com.example.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.core.content.ContextCompat;
import com.example.R;
import com.example.data.YoutubeRepository;
import com.example.model.YoutubeTrack;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import java.util.List;
import java.util.Locale;

/**
 * Official YouTube IFrame Embed Music Deck & Queue inside NeonWave.
 * Plays pasted YouTube links/IDs or browsed search results using YT.Player API,
 * persists per-video resume positions, and pauses local ExoPlayer to avoid overlapping audio.
 */
public class YoutubeMusicDialog {

    public interface HostCallback {
        void onPauseLocalPlaybackForYoutube();
    }

    private final Context context;
    private final YoutubeRepository repo;
    private final HostCallback hostCallback;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private BottomSheetDialog dialog;
    private WebView playerWebView;
    private WebView browseWebView;
    private View playerContainer;
    private View browseContainer;
    private TextView btnModePlayer;
    private TextView btnModeBrowse;
    private EditText inputField;
    private TextView emptyOverlay;
    private TextView titleText;
    private TextView channelText;
    private SeekBar seekBar;
    private TextView timeCurrentText;
    private TextView timeTotalText;
    private ImageButton btnPlayPause;
    private ImageButton btnLoop;
    private LinearLayout queueContainer;

    private String currentVideoId;
    private long currentPosSec = 0L;
    private long currentDurSec = 0L;
    private boolean isPlaying = false;
    private boolean isLooping = false;
    private boolean isSeeking = false;
    private boolean playerReady = false;
    private String pendingLoadVideoId;
    private long pendingSeekSec = 0L;
    private long lastSavedResumeSec = -1L;

    public YoutubeMusicDialog(Context context, YoutubeRepository repo, HostCallback hostCallback) {
        this.context = context;
        this.repo = repo;
        this.hostCallback = hostCallback;
    }

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    public void show(String initialUrlOrQuery) {
        if (dialog != null && dialog.isShowing()) {
            if (initialUrlOrQuery != null && !initialUrlOrQuery.trim().isEmpty()) {
                handleInput(initialUrlOrQuery);
            }
            return;
        }

        dialog = new BottomSheetDialog(context);
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_youtube_music, null);
        dialog.setContentView(view);
        dialog.setOnShowListener(d -> {
            View sheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (sheet != null) {
                BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(sheet);
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                behavior.setSkipCollapsed(true);
            }
        });

        playerContainer = view.findViewById(R.id.yt_player_container);
        browseContainer = view.findViewById(R.id.yt_browse_container);
        btnModePlayer = view.findViewById(R.id.yt_btn_mode_player);
        btnModeBrowse = view.findViewById(R.id.yt_btn_mode_browse);
        inputField = view.findViewById(R.id.yt_input_url_or_query);
        View btnGo = view.findViewById(R.id.yt_btn_go);
        emptyOverlay = view.findViewById(R.id.yt_player_empty_overlay);
        titleText = view.findViewById(R.id.yt_track_title);
        channelText = view.findViewById(R.id.yt_track_channel);
        seekBar = view.findViewById(R.id.yt_seek_bar);
        timeCurrentText = view.findViewById(R.id.yt_time_current);
        timeTotalText = view.findViewById(R.id.yt_time_total);
        btnPlayPause = view.findViewById(R.id.yt_btn_play_pause);
        ImageButton btnPrev = view.findViewById(R.id.yt_btn_prev);
        ImageButton btnNext = view.findViewById(R.id.yt_btn_next);
        btnLoop = view.findViewById(R.id.yt_btn_loop);
        queueContainer = view.findViewById(R.id.yt_queue_container);
        playerWebView = view.findViewById(R.id.yt_player_webview);
        browseWebView = view.findViewById(R.id.yt_browse_webview);

        setupPlayerWebView();
        setupBrowseWebView();

        btnModePlayer.setOnClickListener(v -> showTab(false));
        btnModeBrowse.setOnClickListener(v -> showTab(true));

        btnGo.setOnClickListener(v -> handleInput(inputField.getText() != null ? inputField.getText().toString() : ""));
        inputField.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_SEARCH
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                handleInput(inputField.getText() != null ? inputField.getText().toString() : "");
                return true;
            }
            return false;
        });

        btnPlayPause.setOnClickListener(v -> {
            if (!playerReady || currentVideoId == null) return;
            if (isPlaying) {
                evalPlayerJs("if(window.ytPlayer){window.ytPlayer.pauseVideo();}");
            } else {
                if (hostCallback != null) hostCallback.onPauseLocalPlaybackForYoutube();
                evalPlayerJs("if(window.ytPlayer){window.ytPlayer.playVideo();}");
            }
        });

        btnPrev.setOnClickListener(v -> stepQueue(-1));
        btnNext.setOnClickListener(v -> stepQueue(1));
        btnLoop.setOnClickListener(v -> {
            isLooping = !isLooping;
            btnLoop.setColorFilter(ContextCompat.getColor(context,
                    isLooping ? R.color.neon_cyan : R.color.text_muted));
        });

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (fromUser && currentDurSec > 0) {
                    long targetSec = (currentDurSec * progress) / 1000L;
                    timeCurrentText.setText(formatSec(targetSec));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) {
                isSeeking = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar bar) {
                isSeeking = false;
                if (playerReady && currentDurSec > 0) {
                    long targetSec = (currentDurSec * bar.getProgress()) / 1000L;
                    evalPlayerJs("if(window.ytPlayer){window.ytPlayer.seekTo(" + targetSec + ", true);}");
                    if (currentVideoId != null) {
                        repo.updateResumePosition(currentVideoId, targetSec, currentDurSec);
                    }
                }
            }
        });

        renderQueue();

        String startUrl = initialUrlOrQuery;
        if ((startUrl == null || startUrl.trim().isEmpty())) {
            String lastId = repo.getLastVideoId();
            if (lastId != null) {
                playVideoId(lastId, null, null, false);
            }
        } else {
            inputField.setText(startUrl);
            handleInput(startUrl);
        }

        dialog.setOnDismissListener(d -> cleanup());
        dialog.show();
    }

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    private void setupPlayerWebView() {
        WebSettings s = playerWebView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        playerWebView.setBackgroundColor(0xFF0A0D1C);
        playerWebView.setWebChromeClient(new WebChromeClient());
        playerWebView.setWebViewClient(new WebViewClient());
        playerWebView.addJavascriptInterface(new NeonYtBridge(), "NeonYt");

        String html = "<!DOCTYPE html><html><head><meta name='viewport' "
                + "content='width=device-width,initial-scale=1.0,maximum-scale=1.0'>"
                + "<style>html,body,#player{margin:0;padding:0;width:100%;height:100%;background:#090416;overflow:hidden;}</style>"
                + "</head><body><div id='player'></div>"
                + "<script src='https://www.youtube.com/iframe_api'></script>"
                + "<script>"
                + "var ytPlayer = null; var pollId = null;"
                + "function onYouTubeIframeAPIReady(){"
                + "  ytPlayer = new YT.Player('player', {"
                + "    width:'100%', height:'100%',"
                + "    playerVars:{playsinline:1, rel:0, modestbranding:1, fs:0, controls:1},"
                + "    events:{'onReady':onReady, 'onStateChange':onStateChange, 'onError':onErr}"
                + "  });"
                + "}"
                + "function onReady(){ NeonYt.onApiReady(); }"
                + "function onStateChange(e){"
                + "  var data = ytPlayer && ytPlayer.getVideoData ? ytPlayer.getVideoData() : {};"
                + "  var dur = ytPlayer && ytPlayer.getDuration ? Math.floor(ytPlayer.getDuration()||0) : 0;"
                + "  var pos = ytPlayer && ytPlayer.getCurrentTime ? Math.floor(ytPlayer.getCurrentTime()||0) : 0;"
                + "  NeonYt.onState(e.data, pos, dur, data.title||'', data.author||'');"
                + "  if(e.data===1){"
                + "    if(!pollId){ pollId = setInterval(function(){"
                + "      if(!ytPlayer) return;"
                + "      var p = Math.floor(ytPlayer.getCurrentTime()||0);"
                + "      var d = Math.floor(ytPlayer.getDuration()||0);"
                + "      var v = ytPlayer.getVideoData ? ytPlayer.getVideoData() : {};"
                + "      NeonYt.onProgress(p, d, v.title||'', v.author||'');"
                + "    }, 1000); }"
                + "  } else if(pollId){ clearInterval(pollId); pollId=null; }"
                + "}"
                + "function onErr(e){ NeonYt.onError(e.data||0); }"
                + "</script></body></html>";

        playerWebView.loadDataWithBaseURL("https://www.youtube.com", html, "text/html", "UTF-8", null);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupBrowseWebView() {
        WebSettings s = browseWebView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        browseWebView.setBackgroundColor(0xFF0A0D1C);
        browseWebView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request != null && request.getUrl() != null ? request.getUrl().toString() : null;
                return interceptYoutubeWatchUrl(url);
            }

            @Override
            public void doUpdateVisitedHistory(WebView view, String url, boolean isReload) {
                super.doUpdateVisitedHistory(view, url, isReload);
                if (url != null && (url.contains("/watch") || url.contains("/shorts/"))) {
                    if (interceptYoutubeWatchUrl(url) && view.canGoBack()) {
                        view.goBack();
                    }
                }
            }
        });
    }

    private boolean interceptYoutubeWatchUrl(String url) {
        if (url == null) return false;
        String videoId = YoutubeRepository.extractVideoId(url);
        if (videoId != null && (url.contains("youtube.com/watch") || url.contains("youtu.be/") || url.contains("/shorts/"))) {
            mainHandler.post(() -> {
                showTab(false);
                playVideoId(videoId, null, null, true);
            });
            return true;
        }
        return false;
    }

    private void handleInput(String raw) {
        String text = raw != null ? raw.trim() : "";
        if (text.isEmpty()) return;

        String videoId = YoutubeRepository.extractVideoId(text);
        if (videoId != null) {
            showTab(false);
            playVideoId(videoId, null, null, true);
            return;
        }

        // Plain search query -> open official m.youtube search in Browse tab
        showTab(true);
        String searchUrl = "https://m.youtube.com/results?search_query=" + Uri.encode(text);
        browseWebView.loadUrl(searchUrl);
    }

    private void showTab(boolean browse) {
        if (playerContainer == null || browseContainer == null) return;
        playerContainer.setVisibility(browse ? View.GONE : View.VISIBLE);
        browseContainer.setVisibility(browse ? View.VISIBLE : View.GONE);
        btnModePlayer.setTextColor(ContextCompat.getColor(context,
                browse ? R.color.text_secondary : R.color.neon_cyan));
        btnModeBrowse.setTextColor(ContextCompat.getColor(context,
                browse ? R.color.neon_cyan : R.color.text_secondary));
        if (browse && (browseWebView.getUrl() == null || browseWebView.getUrl().isEmpty())) {
            browseWebView.loadUrl("https://m.youtube.com");
        }
    }

    private void playVideoId(String videoId, String knownTitle, String knownChannel, boolean autoPlay) {
        if (videoId == null) return;
        if (currentVideoId != null && !currentVideoId.equals(videoId) && currentPosSec > 0) {
            repo.updateResumePosition(currentVideoId, currentPosSec, currentDurSec);
        }

        YoutubeTrack saved = null;
        for (YoutubeTrack t : repo.getSavedQueue()) {
            if (videoId.equals(t.getVideoId())) {
                saved = t;
                break;
            }
        }
        long resumeSec = saved != null ? saved.getLastPositionSec() : 0L;
        YoutubeTrack track = repo.addOrUpdateTrack(videoId, knownTitle, knownChannel);

        currentVideoId = videoId;
        currentPosSec = resumeSec;
        currentDurSec = track != null ? track.getDurationSec() : 0L;
        lastSavedResumeSec = resumeSec;
        if (emptyOverlay != null) emptyOverlay.setVisibility(View.GONE);
        if (track != null) {
            titleText.setText(track.getTitle());
            channelText.setText(resumeSec > 2L
                    ? track.getChannel() + " • " + context.getString(R.string.yt_resuming_at, formatSec(resumeSec))
                    : track.getChannel());
        }
        renderQueue();

        if (autoPlay && hostCallback != null) {
            hostCallback.onPauseLocalPlaybackForYoutube();
        }

        if (playerReady) {
            dispatchLoadJs(videoId, resumeSec, autoPlay);
        } else {
            pendingLoadVideoId = videoId;
            pendingSeekSec = resumeSec;
        }
    }

    private void dispatchLoadJs(String videoId, long startSec, boolean autoPlay) {
        String safeId = videoId.replace("'", "");
        if (autoPlay) {
            evalPlayerJs("if(window.ytPlayer){window.ytPlayer.loadVideoById({videoId:'" + safeId
                    + "', startSeconds:" + startSec + "});}");
        } else {
            evalPlayerJs("if(window.ytPlayer){window.ytPlayer.cueVideoById({videoId:'" + safeId
                    + "', startSeconds:" + startSec + "});}");
        }
    }

    private void stepQueue(int delta) {
        List<YoutubeTrack> queue = repo.getSavedQueue();
        if (queue.isEmpty()) return;
        int idx = 0;
        for (int i = 0; i < queue.size(); i++) {
            if (queue.get(i).getVideoId().equals(currentVideoId)) {
                idx = i;
                break;
            }
        }
        int nextIdx = (idx + delta + queue.size()) % queue.size();
        YoutubeTrack target = queue.get(nextIdx);
        playVideoId(target.getVideoId(), target.getTitle(), target.getChannel(), true);
    }

    private void renderQueue() {
        if (queueContainer == null) return;
        queueContainer.removeAllViews();
        List<YoutubeTrack> queue = repo.getSavedQueue();
        if (queue.isEmpty()) {
            TextView empty = new TextView(context);
            empty.setText(R.string.yt_queue_empty);
            empty.setTextColor(ContextCompat.getColor(context, R.color.text_muted));
            empty.setTextSize(12f);
            empty.setPadding(8, 12, 8, 12);
            queueContainer.addView(empty);
            return;
        }

        float d = context.getResources().getDisplayMetrics().density;
        for (YoutubeTrack item : queue) {
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(Math.round(8 * d), Math.round(6 * d), Math.round(4 * d), Math.round(6 * d));
            boolean isCurrent = item.getVideoId().equals(currentVideoId);
            row.setBackgroundColor(isCurrent ? 0x3322D3EE : 0x00000000);

            LinearLayout infoCol = new LinearLayout(context);
            infoCol.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams infoLp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            infoCol.setLayoutParams(infoLp);

            TextView tTitle = new TextView(context);
            tTitle.setText(item.getTitle());
            tTitle.setMaxLines(1);
            tTitle.setTextSize(12.5f);
            tTitle.setTextColor(ContextCompat.getColor(context,
                    isCurrent ? R.color.neon_cyan : R.color.text_primary));
            infoCol.addView(tTitle);

            TextView tSub = new TextView(context);
            String sub = item.getChannel();
            if (item.getLastPositionSec() > 2L) {
                sub += " • " + context.getString(R.string.yt_resuming_at, formatSec(item.getLastPositionSec()));
            }
            tSub.setText(sub);
            tSub.setMaxLines(1);
            tSub.setTextSize(10.5f);
            tSub.setTextColor(ContextCompat.getColor(context, R.color.text_muted));
            infoCol.addView(tSub);

            ImageButton btnDel = new ImageButton(context);
            btnDel.setImageResource(R.drawable.ic_delete);
            btnDel.setBackgroundColor(0x00000000);
            btnDel.setColorFilter(ContextCompat.getColor(context, R.color.text_muted));
            btnDel.setContentDescription(context.getString(R.string.cd_remove_track));
            int btnSz = Math.round(36 * d);
            btnDel.setLayoutParams(new FrameLayout.LayoutParams(btnSz, btnSz));
            btnDel.setOnClickListener(v -> {
                repo.removeTrack(item.getVideoId());
                renderQueue();
            });

            row.addView(infoCol);
            row.addView(btnDel);
            row.setOnClickListener(v -> {
                showTab(false);
                playVideoId(item.getVideoId(), item.getTitle(), item.getChannel(), true);
            });
            queueContainer.addView(row);
        }
    }

    private void evalPlayerJs(String script) {
        if (playerWebView != null) {
            mainHandler.post(() -> playerWebView.evaluateJavascript(script, null));
        }
    }

    private void cleanup() {
        if (currentVideoId != null) {
            repo.updateResumePosition(currentVideoId, currentPosSec, currentDurSec);
        }
        if (playerWebView != null) {
            playerWebView.stopLoading();
            playerWebView.loadUrl("about:blank");
            playerWebView.onPause();
            playerWebView.removeAllViews();
            playerWebView.destroy();
            playerWebView = null;
        }
        if (browseWebView != null) {
            browseWebView.stopLoading();
            browseWebView.loadUrl("about:blank");
            browseWebView.onPause();
            browseWebView.removeAllViews();
            browseWebView.destroy();
            browseWebView = null;
        }
        playerReady = false;
        isPlaying = false;
    }

    private static String formatSec(long totalSec) {
        long s = Math.max(0L, totalSec);
        long mins = s / 60L;
        long secs = s % 60L;
        return String.format(Locale.US, "%d:%02d", mins, secs);
    }

    private final class NeonYtBridge {
        @JavascriptInterface
        public void onApiReady() {
            mainHandler.post(() -> {
                playerReady = true;
                if (pendingLoadVideoId != null) {
                    String vid = pendingLoadVideoId;
                    long seek = pendingSeekSec;
                    pendingLoadVideoId = null;
                    dispatchLoadJs(vid, seek, true);
                }
            });
        }

        @JavascriptInterface
        public void onState(int state, long posSec, long durSec, String title, String author) {
            mainHandler.post(() -> {
                isPlaying = (state == 1);
                btnPlayPause.setImageResource(isPlaying ? R.drawable.ic_pause : R.drawable.ic_play);
                if (isPlaying && hostCallback != null) {
                    hostCallback.onPauseLocalPlaybackForYoutube();
                }
                updateTrackMetaAndProgress(posSec, durSec, title, author);
                if (state == 0) { // ENDED
                    if (currentVideoId != null) {
                        repo.updateResumePosition(currentVideoId, 0L, currentDurSec);
                    }
                    if (isLooping && currentVideoId != null) {
                        evalPlayerJs("if(window.ytPlayer){window.ytPlayer.seekTo(0, true);window.ytPlayer.playVideo();}");
                    } else {
                        stepQueue(1);
                    }
                }
            });
        }

        @JavascriptInterface
        public void onProgress(long posSec, long durSec, String title, String author) {
            mainHandler.post(() -> updateTrackMetaAndProgress(posSec, durSec, title, author));
        }

        @JavascriptInterface
        public void onError(int errorCode) {
            mainHandler.post(() -> Toast.makeText(context,
                    context.getString(R.string.yt_embed_error, errorCode), Toast.LENGTH_SHORT).show());
        }
    }

    private void updateTrackMetaAndProgress(long posSec, long durSec, String title, String author) {
        currentPosSec = Math.max(0L, posSec);
        if (durSec > 0L) {
            currentDurSec = durSec;
        }
        if (currentVideoId != null && ((title != null && !title.trim().isEmpty())
                || (author != null && !author.trim().isEmpty()))) {
            repo.addOrUpdateTrack(currentVideoId, title, author);
            if (title != null && !title.trim().isEmpty()) {
                titleText.setText(title.trim());
            }
            if (author != null && !author.trim().isEmpty()) {
                channelText.setText(author.trim());
            }
        }
        if (!isSeeking) {
            timeCurrentText.setText(formatSec(currentPosSec));
            timeTotalText.setText(formatSec(currentDurSec));
            if (currentDurSec > 0) {
                seekBar.setProgress((int) Math.min(1000L, (currentPosSec * 1000L) / currentDurSec));
            }
        }
        if (currentVideoId != null && Math.abs(currentPosSec - lastSavedResumeSec) >= 2L) {
            lastSavedResumeSec = currentPosSec;
            repo.updateResumePosition(currentVideoId, currentPosSec, currentDurSec);
        }
    }
}
