package com.example.ui.theme;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ClipDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.view.View;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.materialswitch.MaterialSwitch;

public final class DynamicThemeRenderer {

    private DynamicThemeRenderer() {}

    public static void applyCosmicCanvas(View target, NeonPalette palette, float density) {
        if (target == null || palette == null) return;
        LayerDrawable layer;
        if (target.getTag(com.example.R.id.root_scroll) instanceof LayerDrawable) {
            layer = (LayerDrawable) target.getTag(com.example.R.id.root_scroll);
        } else {
            GradientDrawable base = new GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    new int[]{palette.canvasStart, palette.canvasCenter, palette.canvasEnd});

            GradientDrawable glowTopLeft = new GradientDrawable();
            glowTopLeft.setGradientType(GradientDrawable.RADIAL_GRADIENT);
            glowTopLeft.setGradientCenter(0.18f, 0.18f);
            glowTopLeft.setGradientRadius(360f * density);

            GradientDrawable glowBottomRight = new GradientDrawable();
            glowBottomRight.setGradientType(GradientDrawable.RADIAL_GRADIENT);
            glowBottomRight.setGradientCenter(0.88f, 0.78f);
            glowBottomRight.setGradientRadius(340f * density);

            layer = new LayerDrawable(new Drawable[]{base, glowTopLeft, glowBottomRight});
            target.setTag(com.example.R.id.root_scroll, layer);
            target.setBackground(layer);
        }

        GradientDrawable base = (GradientDrawable) layer.getDrawable(0);
        base.setColors(new int[]{palette.canvasStart, palette.canvasCenter, palette.canvasEnd});

        GradientDrawable glowTopLeft = (GradientDrawable) layer.getDrawable(1);
        glowTopLeft.setColors(new int[]{
                NeonPalette.withAlpha(palette.primaryNeon, 0x38),
                NeonPalette.withAlpha(palette.primaryNeon, 0x00)
        });

        GradientDrawable glowBottomRight = (GradientDrawable) layer.getDrawable(2);
        glowBottomRight.setColors(new int[]{
                NeonPalette.withAlpha(palette.secondaryNeon, 0x2E),
                NeonPalette.withAlpha(palette.secondaryNeon, 0x00)
        });
    }

    public static void applyHeroCard(View card, NeonPalette palette, float density) {
        if (card == null || palette == null) return;
        LayerDrawable layer;
        if (card.getTag() instanceof LayerDrawable) {
            layer = (LayerDrawable) card.getTag();
        } else {
            GradientDrawable border = new GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    new int[]{palette.primaryNeon, palette.tertiaryGlow, palette.secondaryNeon});
            border.setCornerRadius(28f * density);

            GradientDrawable fill = new GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    new int[]{palette.cardGlassStart, palette.cardGlassCenter, palette.cardGlassEnd});
            fill.setCornerRadius(26.5f * density);

            layer = new LayerDrawable(new Drawable[]{border, fill});
            int inset = Math.max(1, Math.round(1.5f * density));
            layer.setLayerInset(1, inset, inset, inset, inset);
            card.setTag(layer);
            card.setBackground(layer);
        }

        GradientDrawable border = (GradientDrawable) layer.getDrawable(0);
        border.setColors(new int[]{
                NeonPalette.withAlpha(palette.primaryNeon, 0xD4),
                NeonPalette.withAlpha(palette.tertiaryGlow, 0xCC),
                NeonPalette.withAlpha(palette.secondaryNeon, 0xCC)
        });

        GradientDrawable fill = (GradientDrawable) layer.getDrawable(1);
        fill.setColors(new int[]{palette.cardGlassStart, palette.cardGlassCenter, palette.cardGlassEnd});
    }

    public static void applySubCard(View card, NeonPalette palette, float density) {
        if (card == null || palette == null) return;
        GradientDrawable bg;
        if (card.getTag() instanceof GradientDrawable) {
            bg = (GradientDrawable) card.getTag();
        } else {
            bg = new GradientDrawable();
            bg.setCornerRadius(22f * density);
            card.setTag(bg);
            card.setBackground(bg);
        }
        bg.setColor(palette.subCardFill);
        bg.setStroke(Math.max(1, Math.round(1.2f * density)), palette.subCardStroke);
    }

    public static void applyPlayButton(ImageButton button, NeonPalette palette, float density) {
        if (button == null || palette == null) return;
        LayerDrawable layer;
        if (button.getTag() instanceof LayerDrawable) {
            layer = (LayerDrawable) button.getTag();
        } else {
            GradientDrawable ring = new GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    new int[]{palette.tertiaryGlow, palette.primaryNeon, palette.secondaryNeon});
            ring.setShape(GradientDrawable.OVAL);

            GradientDrawable center = new GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    new int[]{palette.playCenterStart, palette.playCenterEnd});
            center.setShape(GradientDrawable.OVAL);

            layer = new LayerDrawable(new Drawable[]{ring, center});
            int inset = Math.max(2, Math.round(2.5f * density));
            layer.setLayerInset(1, inset, inset, inset, inset);
            button.setTag(layer);
            button.setBackground(layer);
        }

        GradientDrawable ring = (GradientDrawable) layer.getDrawable(0);
        ring.setColors(new int[]{palette.tertiaryGlow, palette.primaryNeon, palette.secondaryNeon});

        GradientDrawable center = (GradientDrawable) layer.getDrawable(1);
        center.setColors(new int[]{palette.playCenterStart, palette.playCenterEnd});
    }

    public static void applySeekBarColors(SeekBar seekBar, NeonPalette palette) {
        if (seekBar == null || palette == null) return;
        Drawable progressDrawable = seekBar.getProgressDrawable();
        if (progressDrawable instanceof LayerDrawable) {
            LayerDrawable layer = (LayerDrawable) progressDrawable;
            Drawable prog = layer.findDrawableByLayerId(android.R.id.progress);
            if (prog instanceof ClipDrawable) {
                Drawable inner = ((ClipDrawable) prog).getDrawable();
                if (inner instanceof GradientDrawable) {
                    ((GradientDrawable) inner).setColors(
                            new int[]{palette.tertiaryGlow, palette.primaryNeon, Color.WHITE});
                }
            }
            Drawable sec = layer.findDrawableByLayerId(android.R.id.secondaryProgress);
            if (sec instanceof ClipDrawable) {
                Drawable inner = ((ClipDrawable) sec).getDrawable();
                if (inner instanceof GradientDrawable) {
                    ((GradientDrawable) inner).setColor(NeonPalette.withAlpha(palette.tertiaryGlow, 0x66));
                }
            }
        }

        Drawable thumb = seekBar.getThumb();
        if (thumb instanceof LayerDrawable) {
            LayerDrawable thumbLayer = (LayerDrawable) thumb;
            if (thumbLayer.getNumberOfLayers() > 0 && thumbLayer.getDrawable(0) instanceof GradientDrawable) {
                ((GradientDrawable) thumbLayer.getDrawable(0))
                        .setColor(NeonPalette.withAlpha(palette.primaryNeon, 0x55));
            }
        }
        seekBar.invalidate();
    }

    public static void applyAlbumArtStroke(ShapeableImageView imageView, NeonPalette palette) {
        if (imageView == null || palette == null) return;
        imageView.setStrokeColor(ColorStateList.valueOf(NeonPalette.withAlpha(palette.primaryNeon, 0xBA)));
    }

    public static void applySwitchColors(MaterialSwitch materialSwitch, NeonPalette palette) {
        if (materialSwitch == null || palette == null) return;
        int[][] states = new int[][]{
                new int[]{android.R.attr.state_checked},
                new int[]{-android.R.attr.state_checked}
        };
        ColorStateList trackTint = new ColorStateList(states, new int[]{
                NeonPalette.withAlpha(palette.primaryNeon, 0x99),
                0x44FFFFFF
        });
        ColorStateList thumbTint = new ColorStateList(states, new int[]{
                palette.secondaryNeon,
                0xFFD4D4D8
        });
        materialSwitch.setTrackTintList(trackTint);
        materialSwitch.setThumbTintList(thumbTint);
    }

    public static void applySelectedTileBackground(View view, NeonPalette palette, float density, float cornerDp) {
        if (view == null || palette == null) return;
        int accent = palette.primaryNeon;
        int core = NeonPalette.lerpColor(accent, 0xFFFFFFFF, 0.34f);
        int edge = NeonPalette.lerpColor(accent, 0xFF060914, 0.32f);

        GradientDrawable outerHalo = new GradientDrawable();
        outerHalo.setCornerRadius((cornerDp + 2f) * density);
        outerHalo.setColor(NeonPalette.withAlpha(accent, 0x55));

        GradientDrawable body = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{core, accent, edge});
        body.setCornerRadius(cornerDp * density);
        body.setStroke(Math.max(2, Math.round(1.6f * density)),
                NeonPalette.lerpColor(accent, 0xFFFFFFFF, 0.55f));

        LayerDrawable layer = new LayerDrawable(new Drawable[]{outerHalo, body});
        int pad = Math.max(1, Math.round(1.8f * density));
        layer.setLayerInset(1, pad, pad, pad, pad);
        view.setBackground(layer);
    }

    public static void applyAccentTextColor(TextView view, int color) {
        if (view != null) {
            view.setTextColor(color);
        }
    }

    public static void applySubtitlePill(Context context, TextView badge, NeonPalette palette) {
        if (context == null || badge == null || palette == null) return;
        badge.setTextColor(palette.secondaryNeon);
    }
}
