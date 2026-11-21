package com.xemple.my;   // غيّر اسم الحزمة حسب تطبيقك

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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * شريط تنقل سفلي زجاجي (Liquid Glass) بدون أي مكتبات أو صور.
 *
 * الزجاج حقيقي: يلتقط الشريط المحتوى الواقع خلفه (blurSource) بدقة مصغّرة، يموّهه، يرفع تشبّعه،
 * ثم يضيف طبقة لمعان وحدًّا مضيئًا. يعمل على API 24+ دون RenderEffect.
 * مؤشر الاختيار "كبسولة" زجاجية تنزلق وتتمدد (إحساس سائل)، ولكل عنصر تأثير عند الاختيار:
 * glow / spin / plane / drop / wave — نفس أسماء NAV_ITEMS في الكود الأصلي.
 *
 * قيود معروفة:
 *  - blurSource يجب أن يكون "شقيقًا" خلف الشريط في نفس الحاوية، وليس أبًا له.
 *  - لا يمكن التقاط SurfaceView / TextureView (كاميرا، OpenGL): يظهر مكانها لون القاعدة + الصبغة.
 */
public class GlassNavBar extends View {

    // ---- الأيقونات والتأثيرات
    public static final int ICON_HOME = 0, ICON_GLOBE = 1, ICON_PLANE = 2, ICON_DROP = 3, ICON_USER = 4;
    public static final int FX_GLOW = 0, FX_SPIN = 1, FX_PLANE = 2, FX_DROP = 3, FX_WAVE = 4;

    public static final class Item {
        public final String label;
        public final int icon, fx;
        public Item(String label, int icon, int fx) { this.label = label; this.icon = icon; this.fx = fx; }
    }

    public interface OnSelectListener {
        void onSelect(int index, Item item, boolean reselected);
    }

    private static final int SCALE = 6;           // معامل تصغير لقطة الخلفية (أكبر = أسرع وأنعم)

    private final List<Item> items = new ArrayList<>();
    private OnSelectListener listener;
    private int selected = 0, pressed = -1;
    private int accent = 0xFF5FD8FF;               // لون التمييز
    private int tint = 0x26FFFFFF;                 // صبغة الزجاج
    private int backdropBase = 0xFF07111F;         // لون قاعدة اللقطة (خلف المحتوى الشفاف)

    // لقطة الخلفية
    private View blurSource;
    private Bitmap bd;
    private Canvas bdCanvas;
    private int[] px = new int[0], tmp = new int[0];
    private int lastHash;
    private long lastCapture;
    private boolean preAttached;
    private ViewTreeObserver observer;
    private final int[] myLoc = new int[2], srcLoc = new int[2];
    private final ViewTreeObserver.OnPreDrawListener pre = () -> { onSourcePreDraw(); return true; };

    // الكبسولة
    private float pillPos = 0f;
    private ValueAnimator pillAnim;
    private float pillFrom = 0f, pillTo = 0f, pillStretch = 0f;

    // تأثيرات العناصر
    private float[] fxP = new float[0];
    private ValueAnimator[] fxA = new ValueAnimator[0];

    // أدوات الرسم
    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint fillP = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokeP = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint iconP = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textP = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowP = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clip = new Path(), iconPath = new Path();
    private final RectF rect = new RectF(), rect2 = new RectF(), rf = new RectF();
    private Shader sheen, shade, borderShader;

    public GlassNavBar(Context c) { super(c); init(); }
    public GlassNavBar(Context c, AttributeSet a) { super(c, a); init(); }

    private void init() {
        setClickable(true);
        ColorMatrix cm = new ColorMatrix();
        cm.setSaturation(1.6f);
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

    // ================================================================ واجهة الاستخدام

    public void setItems(Item... arr) {
        for (ValueAnimator a : fxA) if (a != null) a.cancel();
        items.clear();
        items.addAll(Arrays.asList(arr));
        fxP = new float[items.size()];
        fxA = new ValueAnimator[items.size()];
        selected = 0;
        pillPos = 0f;
        invalidate();
    }

    public void setOnSelectListener(OnSelectListener l) { listener = l; }
    public void setAccentColor(int c) { accent = c; invalidate(); }
    public void setGlassTint(int argb) { tint = argb; invalidate(); }
    public void setBackdropBaseColor(int c) { backdropBase = c; lastHash = 0; }
    public int getSelectedIndex() { return selected; }

    /** المحتوى الذي يظهر خلف الشريط ليُموَّه (شقيق الشريط في نفس FrameLayout). */
    public void setBlurSource(View v) {
        blurSource = v;
        lastHash = 0;
        attachPre();
        invalidate();
    }

    /** اختيار عنصر برمجياً (animate=false لعدم التشغيل الحركي). */
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
                a.setDuration(520);
                a.setInterpolator(new OvershootInterpolator(1.15f));
                a.addUpdateListener(an -> {
                    float t = (Float) an.getAnimatedValue();
                    float f = an.getAnimatedFraction();
                    pillPos = pillFrom + (pillTo - pillFrom) * t;
                    pillStretch = (float) Math.sin(Math.PI * Math.min(1f, f)) * Math.min(1f, Math.abs(pillTo - pillFrom));
                    invalidate();
                });
                a.addListener(new android.animation.AnimatorListenerAdapter() {
                    @Override public void onAnimationEnd(android.animation.Animator an) {
                        pillPos = pillTo; pillStretch = 0f; invalidate();
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
        a.setDuration(700);
        a.setInterpolator(new LinearInterpolator());
        final int i = idx;
        a.addUpdateListener(an -> { fxP[i] = (Float) an.getAnimatedValue(); invalidate(); });
        a.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator an) { fxP[i] = 0f; invalidate(); }
            @Override public void onAnimationCancel(android.animation.Animator an) { fxP[i] = 0f; }
        });
        fxA[idx] = a;
        a.start();
    }

    // ================================================================ دورة الحياة والقياس

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        attachPre();
    }

    @Override protected void onDetachedFromWindow() {
        detachPre();
        if (pillAnim != null) pillAnim.cancel();
        for (ValueAnimator a : fxA) if (a != null) a.cancel();
        super.onDetachedFromWindow();
    }

    private void attachPre() {
        if (blurSource == null || !isAttachedToWindow() || preAttached) return;
        observer = getViewTreeObserver();
        observer.addOnPreDrawListener(pre);
        preAttached = true;
    }

    private void detachPre() {
        if (observer != null && observer.isAlive()) observer.removeOnPreDrawListener(pre);
        observer = null;
        preAttached = false;
    }

    @Override protected void onMeasure(int wSpec, int hSpec) {
        setMeasuredDimension(resolveSize((int) dp(320), wSpec), resolveSize((int) dp(68), hSpec));
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        sheen = new LinearGradient(0, 0, 0, h * 0.6f, 0x3CFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP);
        shade = new LinearGradient(0, h * 0.55f, 0, h, 0x00000000, 0x26000000, Shader.TileMode.CLAMP);
        borderShader = new LinearGradient(0, 0, w * 0.45f, h, 0xD0FFFFFF, 0x38FFFFFF, Shader.TileMode.CLAMP);
        lastHash = 0;
    }

    // ================================================================ التقاط الخلفية (الزجاج الحقيقي)

    private void onSourcePreDraw() {
        long now = SystemClock.uptimeMillis();
        if (now - lastCapture < 60 || blurSource == null || getWidth() == 0 || getHeight() == 0
                || getVisibility() != VISIBLE) return;
        lastCapture = now;
        // لا نعيد الرسم إلا إذا تغيّر محتوى الخلفية فعلاً (يمنع حلقة رسم لا نهائية)
        if (captureBackdrop()) invalidate();
    }

    private boolean captureBackdrop() {
        int w = Math.max(2, getWidth() / SCALE), h = Math.max(2, getHeight() / SCALE);
        if (bd == null || bd.getWidth() != w || bd.getHeight() != h) {
            bd = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            bdCanvas = new Canvas(bd);
            px = new int[w * h];
            tmp = new int[w * h];
            lastHash = 0;
        }
        bd.eraseColor(backdropBase);
        getLocationInWindow(myLoc);
        blurSource.getLocationInWindow(srcLoc);
        float dx = myLoc[0] - srcLoc[0], dy = myLoc[1] - srcLoc[1];
        bdCanvas.save();
        bdCanvas.scale((float) w / getWidth(), (float) h / getHeight());
        bdCanvas.translate(-dx, -dy);
        try {
            blurSource.draw(bdCanvas);
        } catch (Exception e) {
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
    }

    private static int cl(int v, int lo, int hi) { return v < lo ? lo : (v > hi ? hi : v); }

    /** تمويه صندوقي (أفقي ثم عمودي) على بكسلات صغيرة؛ سريع جداً. */
    private static void boxBlur(int[] a, int[] t, int w, int h, int r) {
        int div = 2 * r + 1;
        for (int y = 0; y < h; y++) {
            int row = y * w, sr = 0, sg = 0, sb = 0;
            for (int i = -r; i <= r; i++) {
                int p = a[row + cl(i, 0, w - 1)];
                sr += (p >> 16) & 255; sg += (p >> 8) & 255; sb += p & 255;
            }
            for (int x = 0; x < w; x++) {
                t[row + x] = 0xFF000000 | ((sr / div) << 16) | ((sg / div) << 8) | (sb / div);
                int add = a[row + cl(x + r + 1, 0, w - 1)], rem = a[row + cl(x - r, 0, w - 1)];
                sr += ((add >> 16) & 255) - ((rem >> 16) & 255);
                sg += ((add >> 8) & 255) - ((rem >> 8) & 255);
                sb += (add & 255) - (rem & 255);
            }
        }
        for (int x = 0; x < w; x++) {
            int sr = 0, sg = 0, sb = 0;
            for (int i = -r; i <= r; i++) {
                int p = t[cl(i, 0, h - 1) * w + x];
                sr += (p >> 16) & 255; sg += (p >> 8) & 255; sb += p & 255;
            }
            for (int y = 0; y < h; y++) {
                a[y * w + x] = 0xFF000000 | ((sr / div) << 16) | ((sg / div) << 8) | (sb / div);
                int add = t[cl(y + r + 1, 0, h - 1) * w + x], rem = t[cl(y - r, 0, h - 1) * w + x];
                sr += ((add >> 16) & 255) - ((rem >> 16) & 255);
                sg += ((add >> 8) & 255) - ((rem >> 8) & 255);
                sb += (add & 255) - (rem & 255);
            }
        }
    }

    // ================================================================ الرسم

    private float dp(float v) { return v * getResources().getDisplayMetrics().density; }

    private boolean rtl() { return getLayoutDirection() == View.LAYOUT_DIRECTION_RTL; }

    private float centerX(float index, float itemW, float w) {
        return rtl() ? w - (index + 0.5f) * itemW : (index + 0.5f) * itemW;
    }

    @Override protected void onDraw(Canvas c) {
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

        // الحد المضيء
        float in = dp(0.5f);
        rf.set(in, in, w - in, h - in);
        strokeP.setShader(borderShader);
        strokeP.setStrokeWidth(dp(1f));
        c.drawRoundRect(rf, r, r, strokeP);
        strokeP.setShader(null);
    }

    private void drawGlass(Canvas c, float w, float h) {
        if (bd != null) {
            c.drawBitmap(bd, null, rect, bgPaint);          // الخلفية المموهة
        } else {
            fillP.setShader(null);
            fillP.setColor(0xCC16263A);
            c.drawRect(rect, fillP);
        }
        fillP.setShader(null);
        fillP.setColor(tint);
        c.drawRect(rect, fillP);                              // صبغة الزجاج
        fillP.setShader(sheen);
        c.drawRect(0, 0, w, h * 0.6f, fillP);                // لمعان علوي
        fillP.setShader(shade);
        c.drawRect(0, h * 0.55f, w, h, fillP);               // ظل سفلي خفيف
        fillP.setShader(null);
    }

    private void drawPill(Canvas c, float w, float h, int n) {
        float itemW = w / n;
        float cx = centerX(pillPos, itemW, w);
        float pw = itemW - dp(10) + pillStretch * dp(28);
        float ph = h - dp(14);
        rect2.set(cx - pw / 2f, (h - ph) / 2f, cx + pw / 2f, (h + ph) / 2f);
        fillP.setColor((accent & 0x00FFFFFF) | 0x26000000);
        c.drawRoundRect(rect2, ph / 2f, ph / 2f, fillP);
        fillP.setColor(0x22FFFFFF);
        c.drawRoundRect(rect2, ph / 2f, ph / 2f, fillP);
        strokeP.setColor(0x66FFFFFF);
        strokeP.setStrokeWidth(dp(1f));
        c.drawRoundRect(rect2, ph / 2f, ph / 2f, strokeP);
    }

    private void drawItems(Canvas c, float w, float h, int n) {
        float itemW = w / n, iconSize = dp(24), cy = h * 0.40f;
        textP.setTextSize(dp(10f));
        for (int i = 0; i < n; i++) {
            Item it = items.get(i);
            float cx = centerX(i, itemW, w);
            float near = Math.max(0f, Math.min(1f, 1f - Math.abs(i - pillPos)));
            int color = lerp(0xB3EAF8FF, 0xFFFFFFFF, near);
            float scale = (1f + 0.08f * near) * (pressed == i ? 0.9f : 1f);
            drawIconFx(c, it, i, cx, cy, iconSize, color, scale);
            textP.setColor(lerp(0x99EAF8FF, accent | 0xFF000000, near));
            c.drawText(it.label, cx, h * 0.80f, textP);
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
                    glowP.setShader(new RadialGradient(0, 0, gr, (accent & 0x00FFFFFF) | ((int) (170 * k) << 24),
                            accent & 0x00FFFFFF, Shader.TileMode.CLAMP));
                    c.drawCircle(0, 0, gr, glowP);
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
                    ty = dp(9) * k;
                    float q = (p - 0.15f) / 0.85f;
                    if (q > 0f && q < 1f) {
                        float rw = dp(4) + dp(14) * q, y = dp(14);
                        rf.set(-rw, y - rw * 0.35f, rw, y + rw * 0.35f);
                        strokeP.setColor((accent & 0x00FFFFFF) | ((int) (200 * (1f - q)) << 24));
                        strokeP.setStrokeWidth(dp(1.3f));
                        c.drawOval(rf, strokeP);
                    }
                    break;
                }
                case FX_WAVE: {
                    rot = (float) (16.0 * Math.sin(p * Math.PI * 4.0) * (1f - p));
                    for (int j = 0; j < 2; j++) {
                        float q = p - j * 0.25f;
                        if (q <= 0f || q >= 1f) continue;
                        strokeP.setColor((accent & 0x00FFFFFF) | ((int) (180 * (1f - q)) << 24));
                        strokeP.setStrokeWidth(dp(1.4f));
                        c.drawCircle(0, 0, dp(8) + dp(22) * q, strokeP);
                    }
                    break;
                }
                default: break;
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

    /** أيقونات خطية مرسومة على شبكة 24×24. */
    private void drawIcon(Canvas c, int type, float size) {
        float u = size / 24f;
        c.save();
        c.scale(u, u);
        c.translate(-12f, -12f);
        iconP.setStrokeWidth(1.9f);
        iconPath.reset();
        switch (type) {
            case ICON_HOME:
                iconPath.moveTo(3f, 11f); iconPath.lineTo(12f, 3f); iconPath.lineTo(21f, 11f);
                iconPath.moveTo(5.5f, 9.5f); iconPath.lineTo(5.5f, 20f); iconPath.lineTo(18.5f, 20f); iconPath.lineTo(18.5f, 9.5f);
                iconPath.moveTo(10f, 20f); iconPath.lineTo(10f, 14.5f); iconPath.lineTo(14f, 14.5f); iconPath.lineTo(14f, 20f);
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
                iconPath.moveTo(22f, 2f); iconPath.lineTo(15f, 22f); iconPath.lineTo(11f, 13f); iconPath.lineTo(2f, 9f); iconPath.close();
                iconPath.moveTo(22f, 2f); iconPath.lineTo(11f, 13f);
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
            default: break;
        }
        c.restore();
    }

    // ================================================================ اللمس

    private int indexAt(float x) {
        int n = items.size();
        int i = (int) (x / (getWidth() / (float) n));
        i = cl(i, 0, n - 1);
        return rtl() ? n - 1 - i : i;
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (items.isEmpty()) return false;
        int idx = indexAt(e.getX());
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                pressed = idx; invalidate(); return true;
            case MotionEvent.ACTION_MOVE:
                if (idx != pressed && pressed != -1) { pressed = -1; invalidate(); }
                return true;
            case MotionEvent.ACTION_UP: {
                int p = pressed;
                pressed = -1;
                if (p >= 0 && p == idx) {
                    boolean re = idx == selected;
                    select(idx, true);
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                    performClick();
                    if (listener != null) listener.onSelect(idx, items.get(idx), re);
                }
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_CANCEL:
                pressed = -1; invalidate(); return true;
            default:
                return super.onTouchEvent(e);
        }
    }

    @Override public boolean performClick() { return super.performClick(); }
}
