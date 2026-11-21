package com.example.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.widget.RemoteViews;
import com.example.MainActivity;
import com.example.R;
import com.example.audio.NeonAudioService;

public class NeonWaveWidgetProvider extends AppWidgetProvider {
    private static final String PREFS_WIDGET = "neon_wave_widget";
    private static final String KEY_TITLE = "title";
    private static final String KEY_ARTIST = "artist";
    private static final String KEY_PLAYING = "playing";

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if (context == null || intent == null || intent.getAction() == null) return;
        String action = intent.getAction();
        if (NeonAudioService.ACTION_WIDGET_PLAY_PAUSE.equals(action)
                || NeonAudioService.ACTION_WIDGET_NEXT.equals(action)
                || NeonAudioService.ACTION_WIDGET_PREV.equals(action)) {
            boolean handled = NeonAudioService.handleWidgetActionIfActive(action);
            if (!handled) {
                Intent openApp = new Intent(context, MainActivity.class);
                openApp.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                context.startActivity(openApp);
            }
        }
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_WIDGET, Context.MODE_PRIVATE);
        String title = prefs.getString(KEY_TITLE, context.getString(R.string.app_name));
        String artist = prefs.getString(KEY_ARTIST, context.getString(R.string.default_track_artist));
        boolean playing = prefs.getBoolean(KEY_PLAYING, false);

        for (int widgetId : appWidgetIds) {
            updateSingleWidget(context, appWidgetManager, widgetId, title, artist, playing);
        }
    }

    public static void updateAllWidgets(Context context, String title, String artist, boolean playing) {
        if (context == null) return;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_WIDGET, Context.MODE_PRIVATE);
        String safeTitle = title != null ? title : context.getString(R.string.app_name);
        String safeArtist = artist != null ? artist : context.getString(R.string.default_track_artist);
        prefs.edit()
                .putString(KEY_TITLE, safeTitle)
                .putString(KEY_ARTIST, safeArtist)
                .putBoolean(KEY_PLAYING, playing)
                .apply();

        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = manager.getAppWidgetIds(new ComponentName(context, NeonWaveWidgetProvider.class));
        for (int widgetId : ids) {
            updateSingleWidget(context, manager, widgetId, safeTitle, safeArtist, playing);
        }
    }

    private static void updateSingleWidget(
            Context context,
            AppWidgetManager manager,
            int widgetId,
            String title,
            String artist,
            boolean playing) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_neon_player);
        views.setTextViewText(R.id.widget_title, title != null ? title : context.getString(R.string.app_name));
        views.setTextViewText(R.id.widget_subtitle, artist != null ? artist : context.getString(R.string.default_track_artist));
        views.setImageViewResource(R.id.widget_btn_play_pause, playing ? R.drawable.ic_pause : R.drawable.ic_play);

        Intent openApp = new Intent(context, MainActivity.class);
        openApp.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent openPending = PendingIntent.getActivity(
                context, 10, openApp, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_root, openPending);

        views.setOnClickPendingIntent(R.id.widget_btn_prev,
                createBroadcastPendingIntent(context, 11, NeonAudioService.ACTION_WIDGET_PREV));
        views.setOnClickPendingIntent(R.id.widget_btn_play_pause,
                createBroadcastPendingIntent(context, 12, NeonAudioService.ACTION_WIDGET_PLAY_PAUSE));
        views.setOnClickPendingIntent(R.id.widget_btn_next,
                createBroadcastPendingIntent(context, 13, NeonAudioService.ACTION_WIDGET_NEXT));

        manager.updateAppWidget(widgetId, views);
    }

    private static PendingIntent createBroadcastPendingIntent(Context context, int requestCode, String action) {
        Intent intent = new Intent(context, NeonWaveWidgetProvider.class);
        intent.setAction(action);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getBroadcast(context, requestCode, intent, flags);
    }
}
