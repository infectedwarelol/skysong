package com.spotifyclient.gui.click;

import com.spotifyclient.ModSettings;
import com.spotifyclient.compat.GuiGraphics;
import com.spotifyclient.render.r2d.MsdfFont;
import com.spotifyclient.render.r2d.R2D;
import com.spotifyclient.theme.ClientAccent;
import com.spotifyclient.theme.Colors;
import com.spotifyclient.ui.Clip;

public final class Ui {
    public static final MsdfFont.Font FONT = MsdfFont.Font.MEDIUM;

    private Ui() {
    }

    public static void text(GuiGraphics c, String s, float x, float y, float size, int color) {
        R2D.text(c, FONT, s, x, y, size, color);
    }

    public static float width(String s, float size) {
        return R2D.width(FONT, s, size);
    }

    public static int white(float alpha) {
        return Colors.rgba(255, 255, 255, alpha);
    }

    public static float radiusScale() {
        return ModSettings.rectCornerRadius / 7.0F;
    }

    public static float cornerRadius(float r, float w, float h) {
        return Math.min(Math.min(w, h) * 0.5F, r * radiusScale());
    }

    public static void panelBg(GuiGraphics c, float x, float y, float w, float h, float rTL, float rTR, float rBR, float rBL, float a) {
        int n = Math.round(40.0F * a);
        if (n > 0) {
            float s = radiusScale();
            float max = Math.min(w, h) * 0.5F;
            R2D.rect(c, x, y, w, h, Math.min(max, rTL * s), Math.min(max, rTR * s), Math.min(max, rBR * s), Math.min(max, rBL * s), n << 24);
        }
    }

    public static void panelBg(GuiGraphics c, float x, float y, float w, float h, float r, float a) {
        panelBg(c, x, y, w, h, r, r, r, r, a);
    }

    public static void dropBackground(GuiGraphics c, float x, float y, float w, float h, float a) {
        R2D.clientRectFixed(c, x, y, w, h, 2.0F, a);
    }

    public static void button(GuiGraphics c, float x, float y, float w, float h, String label, float a) {
        panelBg(c, x, y, w, h, 3.0F, a);
        float tw = width(label, 6.0F);
        text(c, label, x + (w - tw) * 0.5F, y + (h - 7.0F) * 0.5F, 6.0F, ClientAccent.accentSoft(220.0F * a));
    }

    public static void name(GuiGraphics c, String name, float x, float y, float maxW, float a) {
        scrollingText(c, name, x + 6.0F, y + 5.0F, maxW, 6.5F, white(200.0F * a));
    }

    public static void scrollingText(GuiGraphics c, String s, float x, float y, float maxW, float size, int color) {
        float w = width(s, size);
        float over = w - maxW;
        if (over <= 1.0F) {
            text(c, s, x, y, size, color);
        } else {
            float pause = 1.2F;
            float move = Math.max(0.35F, over / 11.0F);
            double cycle = (pause + move) * 2.0;
            double t = System.nanoTime() / 1.0E9 % cycle;
            float shift;
            if (t < pause) {
                shift = 0.0F;
            } else if (t < pause + move) {
                shift = over * easeInOutBack((float)((t - pause) / move));
            } else if (t < pause * 2.0F + move) {
                shift = over;
            } else {
                shift = over * (1.0F - easeInOutBack((float)((t - pause * 2.0F - move) / move)));
            }

            Clip.push(c, x, y - 2.0F, x + maxW, y + size + 3.0F);
            text(c, s, x - shift, y, size, color);
            Clip.pop(c);
        }
    }

    private static float easeInOutBack(float t) {
        float c1 = 1.70158F;
        float c2 = c1 * 1.525F;
        if (t < 0.5F) {
            float u = 2.0F * t;
            return u * u * ((c2 + 1.0F) * u - c2) * 0.5F;
        } else {
            float u = 2.0F * t - 2.0F;
            return (u * u * ((c2 + 1.0F) * u + c2) + 2.0F) * 0.5F;
        }
    }

    public static boolean inside(float mx, float my, float x, float y, float w, float h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }
}
