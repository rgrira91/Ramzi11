package com.example.ui.views;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;
import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Pure Java Liquid Glass bottom navigation bar (API 24+, zero external libraries).
 *
 * Captures the sibling blurSource backdrop at reduced resolution, runs a dual-pass sliding-window
 * box blur, boosts saturation, and layers top specular sheen + iridescent border with a liquid
 * stretching capsule and animated vector icons.
 */
public class GlassNavBar extends View {

    // Original + Music-tailored vector icons & item effects
    public static final int ICON_HOME = 0, ICON_GLOBE = 1, ICON_PLANE = 2, ICON_DROP = 3, ICON_USER = 4;
    public static final int ICON_PLAYER = 5, ICON_DISCO = 6, ICON_EQ = 7, ICON_LIBRARY = 8, ICON_STUDIO = 9;
    public static final int FX_GLOW = 0, FX_SPIN = 1, FX_PLANE = 2, FX_DROP = 3, FX_WAVE = 4;

    public static final class Item {
        public final String label;
        public final int icon, fx;

        public Item(String label, int icon, int fx) {
            this.label = label;
            this.icon = icon;
            this.fx = fx;
        }
    }

    public interface OnSelectListener {
        void onSelect(int index, Item item, boolean reselected);
    }

    private static final int SCALE = 6;

    private final List<Item> items = new ArrayList<>();
    private OnSelectListener listener;
    private int selected = 0, pressed = -1;
    private int accent = 0xFF5FD8FF;
    private int tint = 0x33140A2E;
    private int backdropBase = 0xFF070214;
    private boolean lightCanvas = false;
    private boolean liveCaptureEnabled = true;
    private long captureIntervalMs = 75L;

    // Sibling backdrop capture state
    @Nullable private View blurSource;
    @Nullable private Bitmap bd;
    @Nullable private Canvas bdCanvas;
    private int[] px = new int[0], tmp = new int[0];
    private int lastHash;
    private long lastCapture;
    private boolean isCapturing = false;
    private boolean preAttached;
    @Nullable private ViewTreeObserver observer;
    private final int[] myLoc = new int[2], srcLoc = new int[2];
    private final ViewTreeObserver.OnPreDrawListener pre = () -> {
        onSourcePreDraw();
        return true;
    };

    // Liquid capsule animation
    private float pillPos = 0f;
    @Nullable private ValueAnimator pillAnim;
    private float pillFrom = 0f, pillTo = 0f, pillStretch = 0f;

    // Per-item icon animations
    private float[] fxP = new float[0];
    private ValueAnimator[] fxA = new ValueAnimator[0];

    // Pre-allocated drawing tools (zero per-frame heap churn)
    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint fillP = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokeP = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint iconP = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textP = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowP = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clip = new Path(), iconPath = new Path();
    private final RectF rect = new RectF(), rect2 = new RectF(), rf = new RectF();
    private Shader sheen, shade, borderShader;
    @Nullable private Shader cachedGlowShader;
    private int cachedGlowAccent = 0;

    public GlassNavBar(Context c) {
        super(c);
        init();
    }

    public GlassNavBar(Context c, @Nullable AttributeSet a) {
        super(c, a);
        init();
    }

    public GlassNavBar(Context c, @Nullable AttributeSet a, int defStyleAttr) {
        super(c, a, defStyleAttr);
        init();
    }

    private void init() {
        setClickable(true);
        ColorMatrix cm = new ColorMatrix();
        cm.setSaturation(1.65f);
        bgPaint.setColorFilter(new ColorMatrixColorFilter(cm));
        fillP.setStyle(Paint.Style.FILL);
        strokeP.setStyle(Paint.Style.STROKE);
        strokeP.setStrokeWidth(dp(1f));
        iconP.setStyle(Paint.Style.STROKE);
        iconP.setStrokeCap(Paint.Cap.ROUND);
        iconP.setStrokeJoin(Paint.Join.ROUND);
        textP.setTextAlign(Paint.Align.CENTER);
        textP.setTypeface(Typeface.DEFAULT_BOLD);
        glowP.setStyle(Paint.Style.FILL);
    }

    // ================================================================ Public API

    public void setItems(Item... arr) {
        for (ValueAnimator a : fxA) {
            if (a != null) a.cancel();
        }
        items.clear();
        items.addAll(Arrays.asList(arr));
        fxP = new float[items.size()];
        fxA = new ValueAnimator[items.size()];
        selected = 0;
        pillPos = 0f;
        invalidate();
    }

    public void setOnSelectListener(@Nullable OnSelectListener l) {
        listener = l;
    }

    public void setAccentColor(int c) {
        if (this.accent != c) {
            this.accent = c;
            this.cachedGlowShader = null;
            invalidate();
        }
    }

    public void setGlassTint(int argb) {
        if (this.tint != argb) {
            this.tint = argb;
            invalidate();
        }
    }

    public void setBackdropBaseColor(int c) {
        if (this.backdropBase != c) {
            this.backdropBase = c;
            this.lastHash = 0;
            invalidate();
        }
    }

    public void setLightCanvas(boolean lightCanvas) {
        if (this.lightCanvas != lightCanvas) {
            this.lightCanvas = lightCanvas;
            invalidate();
        }
    }

    public void setAdaptiveCaptureConfig(long intervalMs, boolean liveBlurEnabled) {
        this.captureIntervalMs = Math.max(45L, intervalMs);
        this.liveCaptureEnabled = liveBlurEnabled;
        if (!liveBlurEnabled && bd == null && blurSource != null && getWidth() > 0 && getHeight() > 0) {
            if (captureBackdrop()) {
                invalidate();
            }
        }
    }

    public int getSelectedIndex() {
        return selected;
    }

    /** Sibling view rendered behind the bar to sample & blur (never an ancestor). */
    public void setBlurSource(@Nullable View v) {
        blurSource = v;
        lastHash = 0;
        attachPre();
        invalidate();
    }

    public void select(int idx, boolean animate) {
        if (idx < 0 || idx >= items.size()) return;
        boolean same = idx == selected;
        if (!same) {
            pillFrom = pillPos;
            pillTo = idx;
            selected = idx;
            if (pillAnim != null) pillAnim.cancel();
            if (animate) {
                ValueAnimator a = ValueAnimator.ofFloat(0f, 1f);
                a.setDuration(480L);
                a.setInterpolator(new OvershootInterpolator(1.15f));
                a.addUpdateListener(an -> {
                    float t = (Float) an.getAnimatedValue();
                    float f = an.getAnimatedFraction();
                    pillPos = pillFrom + (pillTo - pillFrom) * t;
                    pillStretch = (float) Math.sin(Math.PI * Math.min(1f, f)) * Math.min(1f, Math.abs(pillTo - pillFrom));
                    invalidate();
                });
                a.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator an) {
                        pillPos = pillTo;
                        pillStretch = 0f;
                        invalidate();
                    }
                });
                pillAnim = a;
                a.start();
            } else {
                pillPos = idx;
                pillStretch = 0f;
            }
        }
        if (animate) playFx(idx);
        invalidate();
    }

    private void playFx(int idx) {
        if (idx < 0 || idx >= fxA.length) return;
        if (fxA[idx] != null) fxA[idx].cancel();
        ValueAnimator a = ValueAnimator.ofFloat(0.001f, 1f);
        a.setDuration(680L);
        a.setInterpolator(new LinearInterpolator());
        final int i = idx;
        a.addUpdateListener(an -> {
            fxP[i] = (Float) an.getAnimatedValue();
            invalidate();
        });
        a.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator an) {
                fxP[i] = 0f;
                invalidate();
            }

            @Override
            public void onAnimationCancel(Animator an) {
                fxP[i] = 0f;
            }
        });
        fxA[idx] = a;
        a.start();
    }

    // ================================================================ Lifecycle

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        attachPre();
    }

    @Override
    protected void onDetachedFromWindow() {
        detachPre();
        if (pillAnim != null) pillAnim.cancel();
        for (ValueAnimator a : fxA) {
            if (a != null) a.cancel();
        }
        if (bd != null && !bd.isRecycled()) {
            bd.recycle();
        }
        bd = null;
        bdCanvas = null;
        lastHash = 0;
        super.onDetachedFromWindow();
    }

    private void attachPre() {
        if (blurSource == null || !isAttachedToWindow() || preAttached) return;
        observer = getViewTreeObserver();
        if (observer != null && observer.isAlive()) {
            observer.addOnPreDrawListener(pre);
            preAttached = true;
        }
    }

    private void detachPre() {
        if (observer != null && observer.isAlive()) {
            observer.removeOnPreDrawListener(pre);
        } else if (isAttachedToWindow()) {
            ViewTreeObserver current = getViewTreeObserver();
            if (current != null && current.isAlive()) {
                current.removeOnPreDrawListener(pre);
            }
        }
        observer = null;
        preAttached = false;
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        setMeasuredDimension(resolveSize((int) dp(320), wSpec), resolveSize((int) dp(64), hSpec));
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        sheen = new LinearGradient(0, 0, 0, h * 0.6f, 0x3CFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP);
        shade = new LinearGradient(0, h * 0.55f, 0, h, 0x00000000, 0x26000000, Shader.TileMode.CLAMP);
        borderShader = new LinearGradient(0, 0, w * 0.45f, h, 0xD0FFFFFF, 0x38FFFFFF, Shader.TileMode.CLAMP);
        lastHash = 0;
    }

    // ================================================================ Real Backdrop Blur

    private void onSourcePreDraw() {
        if (isCapturing || blurSource == null || getWidth() <= 0 || getHeight() <= 0
                || getVisibility() != VISIBLE || !isShown()) {
            return;
        }
        if (!liveCaptureEnabled && bd != null) return;
        long now = SystemClock.uptimeMillis();
        if (now - lastCapture < captureIntervalMs) return;
        lastCapture = now;
        if (captureBackdrop()) {
            invalidate();
        }
    }

    private boolean captureBackdrop() {
        if (isCapturing || blurSource == null || getWidth() <= 0 || getHeight() <= 0) return false;
        isCapturing = true;
        try {
            int w = Math.max(2, getWidth() / SCALE);
            int h = Math.max(2, getHeight() / SCALE);
            if (bd == null || bd.isRecycled() || bd.getWidth() != w || bd.getHeight() != h) {
                bd = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                bdCanvas = new Canvas(bd);
                px = new int[w * h];
                tmp = new int[w * h];
                lastHash = 0;
            }
            bd.eraseColor(backdropBase);
            getLocationInWindow(myLoc);
            blurSource.getLocationInWindow(srcLoc);
            float dx = myLoc[0] - srcLoc[0];
            float dy = myLoc[1] - srcLoc[1];
            bdCanvas.save();
            bdCanvas.scale((float) w / getWidth(), (float) h / getHeight());
            bdCanvas.translate(-dx, -dy);
            try {
                blurSource.draw(bdCanvas);
            } catch (Throwable t) {
                bdCanvas.restore();
                return false;
            }
            bdCanvas.restore();
            bd.getPixels(px, 0, w, 0, 0, w, h);
            boxBlur(px, tmp, w, h, 2);
            boxBlur(px, tmp, w, h, 2);
            int hash = Arrays.hashCode(px);
            if (hash == lastHash) return false;
            lastHash = hash;
            bd.setPixels(px, 0, w, 0, 0, w, h);
            return true;
        } finally {
            isCapturing = false;
        }
    }

    private static int cl(int v, int lo, int hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    /** Sliding-window O(W*H) dual-pass box blur on compact buffer. */
    private static void boxBlur(int[] a, int[] t, int w, int h, int r) {
        int div = 2 * r + 1;
        for (int y = 0; y < h; y++) {
            int row = y * w, sr = 0, sg = 0, sb = 0;
            for (int i = -r; i <= r; i++) {
                int p = a[row + cl(i, 0, w - 1)];
                sr += (p >> 16) & 255;
                sg += (p >> 8) & 255;
                sb += p & 255;
            }
            for (int x = 0; x < w; x++) {
                t[row + x] = 0xFF000000 | ((sr / div) << 16) | ((sg / div) << 8) | (sb / div);
                int add = a[row + cl(x + r + 1, 0, w - 1)];
                int rem = a[row + cl(x - r, 0, w - 1)];
                sr += ((add >> 16) & 255) - ((rem >> 16) & 255);
                sg += ((add >> 8) & 255) - ((rem >> 8) & 255);
                sb += (add & 255) - (rem & 255);
            }
        }
        for (int x = 0; x < w; x++) {
            int sr = 0, sg = 0, sb = 0;
            for (int i = -r; i <= r; i++) {
                int p = t[cl(i, 0, h - 1) * w + x];
                sr += (p >> 16) & 255;
                sg += (p >> 8) & 255;
                sb += p & 255;
            }
            for (int y = 0; y < h; y++) {
                a[y * w + x] = 0xFF000000 | ((sr / div) << 16) | ((sg / div) << 8) | (sb / div);
                int add = t[cl(y + r + 1, 0, h - 1) * w + x];
                int rem = t[cl(y - r, 0, h - 1) * w + x];
                sr += ((add >> 16) & 255) - ((rem >> 16) & 255);
                sg += ((add >> 8) & 255) - ((rem >> 8) & 255);
                sb += (add & 255) - (rem & 255);
            }
        }
    }

    // ================================================================ Drawing

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }

    private boolean rtl() {
        return getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
    }

    private float centerX(float index, float itemW, float w) {
        return rtl() ? w - (index + 0.5f) * itemW : (index + 0.5f) * itemW;
    }

    @Override
    protected void onDraw(Canvas c) {
        int n = items.size();
        float w = getWidth(), h = getHeight();
        if (n == 0 || w <= 0 || h <= 0) return;
        float r = h / 2f;
        rect.set(0, 0, w, h);
        clip.reset();
        clip.addRoundRect(rect, r, r, Path.Direction.CW);

        c.save();
        c.clipPath(clip);
        drawGlass(c, w, h);
        drawPill(c, w, h, n);
        drawItems(c, w, h, n);
        c.restore();

        float in = dp(0.5f);
        rf.set(in, in, w - in, h - in);
        strokeP.setShader(borderShader);
        strokeP.setStrokeWidth(dp(1f));
        c.drawRoundRect(rf, r, r, strokeP);
        strokeP.setShader(null);
    }

    private void drawGlass(Canvas c, float w, float h) {
        if (bd != null && !bd.isRecycled()) {
            c.drawBitmap(bd, null, rect, bgPaint);
        } else {
            fillP.setShader(null);
            fillP.setColor((backdropBase & 0x00FFFFFF) | 0xD8000000);
            c.drawRect(rect, fillP);
        }
        fillP.setShader(null);
        fillP.setColor(tint);
        c.drawRect(rect, fillP);
        fillP.setShader(sheen);
        c.drawRect(0, 0, w, h * 0.6f, fillP);
        fillP.setShader(shade);
        c.drawRect(0, h * 0.55f, w, h, fillP);
        fillP.setShader(null);
    }

    private void drawPill(Canvas c, float w, float h, int n) {
        float itemW = w / n;
        float cx = centerX(pillPos, itemW, w);
        float pw = itemW - dp(8) + pillStretch * dp(26);
        float ph = h - dp(12);
        rect2.set(cx - pw / 2f, (h - ph) / 2f, cx + pw / 2f, (h + ph) / 2f);
        fillP.setColor((accent & 0x00FFFFFF) | 0x36000000);
        c.drawRoundRect(rect2, ph / 2f, ph / 2f, fillP);
        fillP.setColor(lightCanvas ? 0x44FFFFFF : 0x22FFFFFF);
        c.drawRoundRect(rect2, ph / 2f, ph / 2f, fillP);
        strokeP.setColor((accent & 0x00FFFFFF) | 0xAA000000);
        strokeP.setStrokeWidth(dp(1.1f));
        c.drawRoundRect(rect2, ph / 2f, ph / 2f, strokeP);
    }

    private void drawItems(Canvas c, float w, float h, int n) {
        float itemW = w / n;
        float iconSize = dp(22);
        float cy = h * 0.39f;
        textP.setTextSize(dp(10f));
        int baseIconColor = lightCanvas ? 0xB81E1B4B : 0xB8EAF8FF;
        int activeIconColor = lightCanvas ? 0xFF1E1B4B : 0xFFFFFFFF;
        int baseTextColor = lightCanvas ? 0x99312E81 : 0x99EAF8FF;

        for (int i = 0; i < n; i++) {
            Item it = items.get(i);
            float cx = centerX(i, itemW, w);
            float near = Math.max(0f, Math.min(1f, 1f - Math.abs(i - pillPos)));
            int color = lerp(baseIconColor, activeIconColor, near);
            float scale = (1f + 0.08f * near) * (pressed == i ? 0.9f : 1f);
            drawIconFx(c, it, i, cx, cy, iconSize, color, scale);
            textP.setColor(lerp(baseTextColor, accent | 0xFF000000, near));
            c.drawText(it.label, cx, h * 0.81f, textP);
        }
    }

    private static int lerp(int a, int b, float t) {
        int aa = (int) (Color.alpha(a) + (Color.alpha(b) - Color.alpha(a)) * t);
        int rr = (int) (Color.red(a) + (Color.red(b) - Color.red(a)) * t);
        int gg = (int) (Color.green(a) + (Color.green(b) - Color.green(a)) * t);
        int bb = (int) (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t);
        return Color.argb(aa, rr, gg, bb);
    }

    private void drawIconFx(Canvas c, Item it, int i, float cx, float cy, float size, int color, float scale) {
        float p = i < fxP.length ? fxP[i] : 0f;
        float rot = 0f, tx = 0f, ty = 0f, alpha = 1f, s = scale;
        c.save();
        c.translate(cx, cy);
        if (p > 0f) {
            switch (it.fx) {
                case FX_GLOW: {
                    float k = (float) Math.sin(Math.PI * p);
                    s *= 1f + 0.18f * k;
                    float gr = dp(26);
                    if (cachedGlowShader == null || cachedGlowAccent != accent) {
                        cachedGlowAccent = accent;
                        cachedGlowShader = new RadialGradient(
                                0, 0, gr,
                                (accent & 0x00FFFFFF) | 0xAA000000,
                                accent & 0x00FFFFFF,
                                Shader.TileMode.CLAMP);
                    }
                    glowP.setShader(cachedGlowShader);
                    glowP.setAlpha(Math.round(255f * k));
                    c.drawCircle(0, 0, gr, glowP);
                    glowP.setAlpha(255);
                    glowP.setShader(null);
                    break;
                }
                case FX_SPIN:
                    rot = 360f * (1f - (1f - p) * (1f - p) * (1f - p));
                    break;
                case FX_PLANE: {
                    float o = p < 0.5f ? p * 2f : -(1f - p) * 2f;
                    tx = o * dp(16);
                    ty = -o * dp(16);
                    alpha = 1f - 0.85f * Math.abs(o);
                    break;
                }
                case FX_DROP: {
                    float k = (float) (Math.abs(Math.sin(p * Math.PI * 2.0)) * (1f - p));
                    ty = dp(8) * k;
                    float q = (p - 0.15f) / 0.85f;
                    if (q > 0f && q < 1f) {
                        float rw = dp(4) + dp(14) * q, y = dp(13);
                        rf.set(-rw, y - rw * 0.35f, rw, y + rw * 0.35f);
                        strokeP.setColor((accent & 0x00FFFFFF) | ((int) (200 * (1f - q)) << 24));
                        strokeP.setStrokeWidth(dp(1.3f));
                        c.drawOval(rf, strokeP);
                    }
                    break;
                }
                case FX_WAVE: {
                    rot = (float) (14.0 * Math.sin(p * Math.PI * 4.0) * (1f - p));
                    for (int j = 0; j < 2; j++) {
                        float q = p - j * 0.25f;
                        if (q <= 0f || q >= 1f) continue;
                        strokeP.setColor((accent & 0x00FFFFFF) | ((int) (180 * (1f - q)) << 24));
                        strokeP.setStrokeWidth(dp(1.4f));
                        c.drawCircle(0, 0, dp(8) + dp(20) * q, strokeP);
                    }
                    break;
                }
                default:
                    break;
            }
        }
        c.translate(tx, ty);
        c.rotate(rot);
        c.scale(s, s);
        int a = (int) (Color.alpha(color) * alpha);
        iconP.setColor((color & 0x00FFFFFF) | (a << 24));
        drawIcon(c, it.icon, size);
        c.restore();
    }

    /** Crisp 24x24 Canvas vector icons (both original shapes and Music/Disco Studio shapes). */
    private void drawIcon(Canvas c, int type, float size) {
        float u = size / 24f;
        c.save();
        c.scale(u, u);
        c.translate(-12f, -12f);
        iconP.setStrokeWidth(1.9f);
        iconPath.reset();
        switch (type) {
            case ICON_PLAYER:
                // Pulsing waveform bars inside stereo frame
                c.drawLine(4f, 15f, 4f, 9f, iconP);
                c.drawLine(8f, 18f, 8f, 6f, iconP);
                c.drawLine(12f, 20.5f, 12f, 3.5f, iconP);
                c.drawLine(16f, 17f, 16f, 7f, iconP);
                c.drawLine(20f, 14f, 20f, 10f, iconP);
                break;
            case ICON_DISCO:
                // Mirror Disco Ball & sparkle rays
                c.drawCircle(12f, 12f, 8.2f, iconP);
                c.drawLine(3.8f, 12f, 20.2f, 12f, iconP);
                c.drawLine(12f, 3.8f, 12f, 20.2f, iconP);
                rf.set(7.6f, 3.8f, 16.4f, 20.2f);
                c.drawOval(rf, iconP);
                break;
            case ICON_EQ:
                // 3 vertical Equalizer faders with knobs
                c.drawLine(6f, 4f, 6f, 20f, iconP);
                c.drawCircle(6f, 14f, 2f, iconP);
                c.drawLine(12f, 4f, 12f, 20f, iconP);
                c.drawCircle(12f, 8f, 2f, iconP);
                c.drawLine(18f, 4f, 18f, 20f, iconP);
                c.drawCircle(18f, 15f, 2f, iconP);
                break;
            case ICON_LIBRARY:
                // Musical playlist queue lines + note head
                c.drawLine(4f, 6f, 15f, 6f, iconP);
                c.drawLine(4f, 11f, 13f, 11f, iconP);
                c.drawLine(4f, 16f, 10f, 16f, iconP);
                iconPath.moveTo(15.5f, 16f);
                iconPath.lineTo(15.5f, 7.5f);
                iconPath.lineTo(20.5f, 6f);
                c.drawPath(iconPath, iconP);
                c.drawCircle(13.8f, 17f, 2.1f, iconP);
                break;
            case ICON_STUDIO:
                // Pro Audio Studio gear / star hub
                c.drawCircle(12f, 12f, 3.4f, iconP);
                c.drawCircle(12f, 12f, 8.2f, iconP);
                c.drawLine(12f, 2.2f, 12f, 4.2f, iconP);
                c.drawLine(12f, 19.8f, 12f, 21.8f, iconP);
                c.drawLine(2.2f, 12f, 4.2f, 12f, iconP);
                c.drawLine(19.8f, 12f, 21.8f, 12f, iconP);
                break;
            case ICON_HOME:
                iconPath.moveTo(3f, 11f);
                iconPath.lineTo(12f, 3f);
                iconPath.lineTo(21f, 11f);
                iconPath.moveTo(5.5f, 9.5f);
                iconPath.lineTo(5.5f, 20f);
                iconPath.lineTo(18.5f, 20f);
                iconPath.lineTo(18.5f, 9.5f);
                iconPath.moveTo(10f, 20f);
                iconPath.lineTo(10f, 14.5f);
                iconPath.lineTo(14f, 14.5f);
                iconPath.lineTo(14f, 20f);
                c.drawPath(iconPath, iconP);
                break;
            case ICON_GLOBE:
                c.drawCircle(12f, 12f, 9f, iconP);
                c.drawLine(3f, 12f, 21f, 12f, iconP);
                c.drawLine(4.2f, 7.5f, 19.8f, 7.5f, iconP);
                c.drawLine(4.2f, 16.5f, 19.8f, 16.5f, iconP);
                rf.set(8f, 3f, 16f, 21f);
                c.drawOval(rf, iconP);
                break;
            case ICON_PLANE:
                iconPath.moveTo(22f, 2f);
                iconPath.lineTo(15f, 22f);
                iconPath.lineTo(11f, 13f);
                iconPath.lineTo(2f, 9f);
                iconPath.close();
                iconPath.moveTo(22f, 2f);
                iconPath.lineTo(11f, 13f);
                c.drawPath(iconPath, iconP);
                break;
            case ICON_DROP:
                iconPath.moveTo(12f, 2.5f);
                iconPath.lineTo(17.56f, 10.76f);
                rf.set(5.3f, 7.8f, 18.7f, 21.2f);
                iconPath.arcTo(rf, -33.9f, 247.8f, false);
                iconPath.close();
                c.drawPath(iconPath, iconP);
                break;
            case ICON_USER:
                c.drawCircle(12f, 8f, 4f, iconP);
                iconPath.moveTo(4f, 21f);
                rf.set(4f, 13f, 20f, 29f);
                iconPath.arcTo(rf, 180f, 180f, false);
                c.drawPath(iconPath, iconP);
                break;
            default:
                break;
        }
        c.restore();
    }

    // ================================================================ Touch

    private int indexAt(float x) {
        int n = items.size();
        int i = (int) (x / (getWidth() / (float) n));
        i = cl(i, 0, n - 1);
        return rtl() ? n - 1 - i : i;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (items.isEmpty()) return false;
        int idx = indexAt(e.getX());
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                pressed = idx;
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE:
                if (idx != pressed && pressed != -1) {
                    pressed = -1;
                    invalidate();
                }
                return true;
            case MotionEvent.ACTION_UP: {
                int p = pressed;
                pressed = -1;
                if (p >= 0 && p == idx) {
                    boolean re = idx == selected;
                    select(idx, true);
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                    performClick();
                    if (listener != null) {
                        listener.onSelect(idx, items.get(idx), re);
                    }
                }
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_CANCEL:
                pressed = -1;
                invalidate();
                return true;
            default:
                return super.onTouchEvent(e);
        }
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }
}
