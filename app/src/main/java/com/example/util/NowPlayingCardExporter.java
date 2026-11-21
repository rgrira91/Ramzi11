package com.example.util;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import com.example.model.Track;
import com.example.ui.theme.NeonPalette;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Locale;

/**
 * Generates a sleek 9:12 glassmorphic Now Playing Story Card PNG and launches Android Share Sheet.
 */
public final class NowPlayingCardExporter {
    private NowPlayingCardExporter() {}

    @Nullable
    public static Uri renderCardToCache(
            @NonNull Context context,
            @NonNull Track track,
            @Nullable Bitmap albumArt,
            @NonNull NeonPalette palette,
            @NonNull String presetName,
            int bassPercent,
            @NonNull String visualModeName) {
        int w = 900;
        int h = 1200;
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);

        // Background Gradient matching current NeonPalette
        Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bgPaint.setShader(new LinearGradient(
                0f, 0f, w, h,
                new int[]{palette.canvasStart, palette.canvasCenter, palette.canvasEnd},
                null, Shader.TileMode.CLAMP));
        canvas.drawRect(0f, 0f, w, h, bgPaint);

        // Soft glowing orbs
        Paint orbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        orbPaint.setColor(NeonPalette.withAlpha(palette.primaryNeon, 0x3A));
        canvas.drawCircle(w * 0.85f, h * 0.16f, 260f, orbPaint);
        orbPaint.setColor(NeonPalette.withAlpha(palette.secondaryNeon, 0x33));
        canvas.drawCircle(w * 0.15f, h * 0.82f, 280f, orbPaint);

        // Glass Card Container
        RectF cardRect = new RectF(64f, 80f, w - 64f, h - 80f);
        Paint cardPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        cardPaint.setColor(0xC8140F28);
        canvas.drawRoundRect(cardRect, 48f, 48f, cardPaint);

        Paint cardStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        cardStroke.setStyle(Paint.Style.STROKE);
        cardStroke.setStrokeWidth(3.5f);
        cardStroke.setShader(new LinearGradient(
                cardRect.left, cardRect.top, cardRect.right, cardRect.bottom,
                palette.primaryNeon, palette.secondaryNeon, Shader.TileMode.CLAMP));
        canvas.drawRoundRect(cardRect, 48f, 48f, cardStroke);

        // Album Cover Art
        RectF artRect = new RectF(140f, 156f, w - 140f, 156f + (w - 280f));
        Path artClip = new Path();
        artClip.addRoundRect(artRect, 36f, 36f, Path.Direction.CW);
        canvas.save();
        canvas.clipPath(artClip);
        if (albumArt != null && !albumArt.isRecycled()) {
            canvas.drawBitmap(albumArt, null, artRect, new Paint(Paint.FILTER_BITMAP_FLAG));
        } else {
            Paint artPlaceholder = new Paint(Paint.ANTI_ALIAS_FLAG);
            artPlaceholder.setShader(new LinearGradient(
                    artRect.left, artRect.top, artRect.right, artRect.bottom,
                    palette.cardGlassStart, palette.cardGlassEnd, Shader.TileMode.CLAMP));
            canvas.drawRect(artRect, artPlaceholder);
        }
        canvas.restore();
        Paint artBorder = new Paint(Paint.ANTI_ALIAS_FLAG);
        artBorder.setStyle(Paint.Style.STROKE);
        artBorder.setStrokeWidth(3f);
        artBorder.setColor(NeonPalette.withAlpha(palette.secondaryNeon, 0xAA));
        canvas.drawRoundRect(artRect, 36f, 36f, artBorder);

        // Title & Artist
        Paint titlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        titlePaint.setColor(0xFFF8FAFC);
        titlePaint.setTextSize(46f);
        titlePaint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        titlePaint.setTextAlign(Paint.Align.CENTER);
        String title = ellipsize(track.getTitle(), 26);
        canvas.drawText(title, w * 0.5f, artRect.bottom + 76f, titlePaint);

        Paint artistPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        artistPaint.setColor(0xFFCBD5E1);
        artistPaint.setTextSize(30f);
        artistPaint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(ellipsize(track.getArtist(), 32), w * 0.5f, artRect.bottom + 122f, artistPaint);

        // Neon Spectrum Waveform Motif
        Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        barPaint.setShader(new LinearGradient(
                140f, 0f, w - 140f, 0f,
                palette.primaryNeon, palette.secondaryNeon, Shader.TileMode.CLAMP));
        float waveTop = artRect.bottom + 185f;
        int bars = 32;
        float span = w - 280f;
        float step = span / bars;
        for (int i = 0; i < bars; i++) {
            float norm = (float) Math.sin((double) i / bars * Math.PI);
            float amp = 16f + 64f * norm * (0.55f + 0.45f * (float) Math.abs(Math.sin(i * 0.85)));
            float cx = 140f + i * step + step * 0.5f;
            canvas.drawRoundRect(
                    new RectF(cx - step * 0.28f, waveTop - amp * 0.5f, cx + step * 0.28f, waveTop + amp * 0.5f),
                    5f, 5f, barPaint);
        }

        // Info Badges (Format / Preset / Sub-Bass)
        Paint pillBg = new Paint(Paint.ANTI_ALIAS_FLAG);
        pillBg.setColor(0x5522D3EE);
        RectF badgeRect = new RectF(130f, h - 220f, w - 130f, h - 160f);
        canvas.drawRoundRect(badgeRect, 30f, 30f, pillBg);

        Paint pillText = new Paint(Paint.ANTI_ALIAS_FLAG);
        pillText.setColor(0xFFFFFFFF);
        pillText.setTextSize(23f);
        pillText.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        pillText.setTextAlign(Paint.Align.CENTER);
        String badge = String.format(Locale.US, "%s • EQ: %s • BASS %d%%",
                track.getQualityBadgeText(), presetName.toUpperCase(Locale.US), bassPercent);
        canvas.drawText(ellipsize(badge, 44), w * 0.5f, badgeRect.centerY() + 8f, pillText);

        // Brand Footer
        Paint footerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        footerPaint.setColor(palette.secondaryNeon);
        footerPaint.setTextSize(22f);
        footerPaint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        footerPaint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("NEONWAVE • " + visualModeName.toUpperCase(Locale.US), w * 0.5f, h - 115f, footerPaint);

        File outDir = new File(context.getCacheDir(), "shared_cards");
        if (!outDir.exists() && !outDir.mkdirs()) {
            bmp.recycle();
            return null;
        }
        File outFile = new File(outDir, "neonwave_now_playing.png");
        try (FileOutputStream fos = new FileOutputStream(outFile)) {
            bmp.compress(Bitmap.CompressFormat.PNG, 95, fos);
            fos.flush();
            return FileProvider.getUriForFile(
                    context,
                    context.getPackageName() + ".fileprovider",
                    outFile);
        } catch (Throwable ignored) {
            return null;
        } finally {
            bmp.recycle();
        }
    }

    public static boolean shareCard(
            @NonNull Context context,
            @NonNull Track track,
            @Nullable Bitmap albumArt,
            @NonNull NeonPalette palette,
            @NonNull String presetName,
            int bassPercent,
            @NonNull String visualModeName) {
        Uri uri = renderCardToCache(context, track, albumArt, palette, presetName, bassPercent, visualModeName);
        if (uri == null) return false;
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("image/png");
        share.putExtra(Intent.EXTRA_STREAM, uri);
        share.putExtra(Intent.EXTRA_TEXT, "Listening to " + track.getTitle() + " - " + track.getArtist() + " on NeonWave");
        share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        context.startActivity(Intent.createChooser(share, "Share Now Playing Card"));
        return true;
    }

    private static String ellipsize(String s, int max) {
        if (s == null) return "";
        String t = s.trim();
        return t.length() <= max ? t : t.substring(0, Math.max(1, max - 1)) + "…";
    }
}
