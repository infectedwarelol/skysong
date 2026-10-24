package com.spotifyclient.ui;

import com.mojang.blaze3d.platform.Window;
import com.spotifyclient.theme.ClientAccent;
import net.minecraft.client.Minecraft;

public final class Theme {
    public static final float RADIUS_XS = 3.0F;
    public static final float RADIUS_SM = 5.0F;
    public static final float RADIUS_MD = 8.0F;
    public static final float RADIUS_LG = 12.0F;
    public static final float HAIRLINE = 0.5F;
    public static final float SPACE_1 = 2.0F;
    public static final float SPACE_2 = 4.0F;
    public static final float SPACE_3 = 6.0F;
    public static final float SPACE_4 = 8.0F;
    public static final float SPACE_5 = 10.0F;
    public static final float SPACE_6 = 12.0F;
    public static final float SPACE_8 = 16.0F;
    public static final int TEXT_CAPTION = 6;
    public static final int TEXT_SMALL = 7;
    public static final int TEXT_BODY = 8;
    public static final int TEXT_TITLE = 9;
    public static final int TEXT_HEADING = 11;
    public static final int ACCENT = -10781446;
    public static final int ACCENT_HOVER = -9400065;
    public static final int ACCENT_VIOLET = -6587393;
    public static final int TEXT_PRIMARY = -723201;
    public static final int TEXT_SECONDARY = -3945757;
    public static final int TEXT_TERTIARY = -6445370;
    public static final int TEXT_DESCRIPTION = -4800806;
    public static final int LINE = 486539263;
    public static final int PANEL_TOP = -802802584;
    public static final int PANEL_BOTTOM = -602332598;
    public static final int POPUP_TOP = -601080968;
    public static final int POPUP_BOTTOM = -434297005;
    public static final int SIDEBAR_TINT = 805965608;
    public static final int HUD_TOP = -937020570;
    public static final int HUD_BOTTOM = -736484791;
    public static final int EDGE_TOP = 1724437759;
    public static final int EDGE_BOTTOM = 415814911;
    public static final int CARD = 385875967;
    public static final int CARD_ON = 780575487;
    public static final int CARD_HOVER = 218103807;
    public static final int CARD_EDGE = 452984831;
    public static final int FIELD = 939919902;
    public static final int SHADOW = 1493172232;
    public static final float SHADOW_PANEL = 14.0F;
    public static final float SHADOW_HUD = 6.0F;
    public static final float SHADOW_OFFSET = 2.0F;
    public static final int BLUR_PASSES = 3;
    public static final int ANIM_FAST = 120;
    public static final int ANIM_BASE = 180;
    public static final int ANIM_SLOW = 260;

    private Theme() {
    }

    public static int guiAccent() {
        return ClientAccent.accentOpaque();
    }

    public static int brighter(int color) {
        return lerpColor(color, -1, 0.18F);
    }

    public static int tinted(int base, int accent, float amount) {
        int rgb = lerpColor(0xFF000000 | base & 16777215, accent, amount);
        return base & 0xFF000000 | rgb & 16777215;
    }

    public static int pixelsPerUnit() {
        Window w = Minecraft.getInstance().getWindow();
        int ppu = Math.round(w.getHeight() / 540.0F);
        return Math.max(2, Math.min(4, ppu));
    }

    public static float unitScale() {
        return (float)pixelsPerUnit() / Minecraft.getInstance().getWindow().getGuiScale();
    }

    public static float approach(float current, float target, float dtSeconds, float speed) {
        return current + (target - current) * Math.min(1.0F, dtSeconds * speed);
    }

    public static float easeOutCubic(float t) {
        t = Math.max(0.0F, Math.min(1.0F, t));
        float u = 1.0F - t;
        return 1.0F - u * u * u;
    }

    public static int alpha(int argb, float factor) {
        int a = Math.round((argb >>> 24 & 0xFF) * Math.max(0.0F, Math.min(1.0F, factor)));
        return a << 24 | argb & 16777215;
    }

    public static int lerpColor(int a, int b, float t) {
        t = Math.max(0.0F, Math.min(1.0F, t));
        int ca = (int)((a >>> 24 & 0xFF) + ((b >>> 24 & 0xFF) - (a >>> 24 & 0xFF)) * t);
        int cr = (int)((a >> 16 & 0xFF) + ((b >> 16 & 0xFF) - (a >> 16 & 0xFF)) * t);
        int cg = (int)((a >> 8 & 0xFF) + ((b >> 8 & 0xFF) - (a >> 8 & 0xFF)) * t);
        int cb = (int)((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t);
        return ca << 24 | cr << 16 | cg << 8 | cb;
    }
}
