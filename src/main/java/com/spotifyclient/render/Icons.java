package com.spotifyclient.render;

import com.spotifyclient.compat.GuiGraphics;
import com.spotifyclient.compat.Ids;
import com.spotifyclient.render.r2d.R2D;
import com.spotifyclient.ui.Theme;
import net.minecraft.resources.Identifier;

public final class Icons {
    public static final String BRAND = "VERSEFALL";
    public static final Identifier LOGO = Ids.of("spotify_mod", "textures/gui/logo.png");
    private static final float[] TIPS = new float[]{0.5F, 0.625F, 0.765F, 0.625F, 0.5F};

    private Icons() {
    }

    public static void draw(GuiGraphics ctx, Icons.Icon icon, float cx, float cy, float size, int color) {
        float s = size / 8.0F;
        switch (icon) {
            case LIST:
                GlassRenderer.fillRounded(ctx, cx - 4.0F * s, cy - 4.0F * s, 8.0F * s, 8.0F * s, 2.0F * s, color);

                for (int i = 0; i < 3; i++) {
                    GlassRenderer.fillRounded(ctx, cx - 2.2F * s, cy - 2.2F * s + i * 2 * s, 4.4F * s, 0.8F * s, 0.4F * s, -723201);
                }
                break;
            case MUSIC:
                GlassRenderer.fillCircle(ctx, cx, cy, 4.0F * s, -14829228);
                spotifyWave(ctx, cx, cy, s, -1.65F, 0.72F, 3.0F);
                spotifyWave(ctx, cx, cy, s, -0.1F, 0.62F, 2.55F);
                spotifyWave(ctx, cx, cy, s, 1.3F, 0.52F, 2.1F);
                break;
            case GEAR:
                for (int i = 0; i < 8; i++) {
                    double a = i * Math.PI / 4.0;
                    GlassRenderer.fillCircle(ctx, cx + (float)Math.cos(a) * 3.1F * s, cy + (float)Math.sin(a) * 3.1F * s, 1.0F * s, color);
                }

                GlassRenderer.strokeRounded(ctx, cx - 3.0F * s, cy - 3.0F * s, 6.0F * s, 6.0F * s, 3.0F * s, 1.6F * s, color);
                break;
            case EYE:
                GlassRenderer.strokeRounded(ctx, cx - 4.0F * s, cy - 2.6F * s, 8.0F * s, 5.2F * s, 2.6F * s, 0.9F * s, color);
                GlassRenderer.fillCircle(ctx, cx, cy, 1.4F * s, color);
                break;
            case DISPLAY:
                GlassRenderer.strokeRounded(ctx, cx - 4.0F * s, cy - 3.4F * s, 8.0F * s, 5.4F * s, 1.2F * s, 0.9F * s, color);
                GlassRenderer.fillRounded(ctx, cx - 2.0F * s, cy + 3.0F * s, 4.0F * s, 0.9F * s, 0.45F * s, color);
                break;
            case USER:
                GlassRenderer.fillCircle(ctx, cx, cy - 1.7F * s, 1.8F * s, color);
                GlassRenderer.fillRounded(ctx, cx - 3.2F * s, cy + 1.0F * s, 6.4F * s, 3.0F * s, 1.5F * s, color);
                break;
            case FOLDER:
                GlassRenderer.fillRounded(ctx, cx - 4.0F * s, cy - 3.4F * s, 3.4F * s, 2.0F * s, 0.8F * s, color);
                GlassRenderer.fillRounded(ctx, cx - 4.0F * s, cy - 2.4F * s, 8.0F * s, 5.8F * s, 1.2F * s, color);
                break;
            case SEARCH:
                GlassRenderer.strokeRounded(ctx, cx - 3.6F * s, cy - 3.6F * s, 5.6F * s, 5.6F * s, 2.8F * s, 0.9F * s, color);

                for (int i = 0; i < 4; i++) {
                    GlassRenderer.fillCircle(ctx, cx + (1.4F + i * 0.7F) * s, cy + (1.4F + i * 0.7F) * s, 0.55F * s, color);
                }
                break;
            case SETTINGS:
                float t = 0.9F * s;
                float arm = 2.6F * s;
                float gap = 1.1F * s;
                float[][] dirs = new float[][]{{-1.0F, -1.0F}, {1.0F, -1.0F}, {-1.0F, 1.0F}, {1.0F, 1.0F}};

                for (float[] d : dirs) {
                    float ex = cx + d[0] * gap;
                    float ey = cy + d[1] * gap;
                    float hx = d[0] < 0.0F ? ex - arm : ex;
                    float vy = d[1] < 0.0F ? ey - arm : ey;
                    GlassRenderer.fillRounded(ctx, hx - (d[0] < 0.0F ? -t / 2.0F : t / 2.0F), ey - t / 2.0F, arm, t, t / 2.0F, color);
                    GlassRenderer.fillRounded(ctx, ex - t / 2.0F, vy - (d[1] < 0.0F ? -t / 2.0F : t / 2.0F), t, arm, t / 2.0F, color);
                }
        }
    }

    private static void spotifyWave(GuiGraphics ctx, float cx, float cy, float s, float y, float thick, float halfWidth) {
        float[][] points = new float[14][2];

        for (int i = 0; i < 7; i++) {
            float t = i / 6.0F;
            float x = (t * 2.0F - 1.0F) * halfWidth;
            float bend = -0.55F * (1.0F - x * x / (halfWidth * halfWidth)) + t * 0.35F;
            points[i] = new float[]{cx + x * s, cy + (y + bend) * s};
            points[13 - i] = new float[]{cx + x * s, cy + (y + bend + thick) * s};
        }

        GlassRenderer.fillPolygon(ctx, points, -15584734);
    }

    public static void logo(GuiGraphics ctx, float x, float y, float size, float opacity) {
        int a = Math.round(Math.max(0.0F, Math.min(1.0F, opacity)) * 255.0F);
        R2D.image(ctx, LOGO, x, y, size, size, 0.0F, 0.0F, 0.0F, 1.0F, 1.0F, a << 24 | 16777215, true);
    }

    public static void emblem(GuiGraphics ctx, float x, float y, float size, float opacity) {
        boolean small = size < 24.0F;
        float bw = size * (small ? 0.11F : 0.082F);
        float gap = size * (small ? 0.035F : 0.046F);
        float x0 = x + (size - (bw * 5.0F + gap * 4.0F)) / 2.0F;
        float top = y + size * 0.19F;

        for (int i = 0; i < TIPS.length; i++) {
            float bx = x0 + i * (bw + gap);
            float tipY = y + size * TIPS[i];
            GlassRenderer.fillRounded(
                ctx, bx, top, bw, tipY - top, bw / 2.0F, Theme.alpha(Theme.guiAccent(), 0.0F), Theme.alpha(Theme.brighter(Theme.guiAccent()), opacity)
            );
            if (!small) {
                GlassRenderer.fillCircle(ctx, bx + bw / 2.0F, tipY - bw / 2.0F, bw * 0.95F, Theme.alpha(-3616001, 0.25F * opacity));
            }

            GlassRenderer.fillCircle(ctx, bx + bw / 2.0F, tipY - bw / 2.0F, bw / 2.0F, Theme.alpha(-1, opacity));
        }
    }

    public static float wordmark(GuiGraphics ctx, float x, float cy, int size, int color) {
        float tracking = size * 0.22F;
        float cursor = x;

        for (char c : "VERSEFALL".toCharArray()) {
            String ch = String.valueOf(c);
            UiText.drawCentered(ctx, ch, cursor, cy, color, size, true);
            cursor += UiText.width(ch, size, true) + tracking;
        }

        return cursor - tracking - x;
    }

    public static float wordmarkWidth(int size) {
        float tracking = size * 0.22F;
        float w = 0.0F;

        for (char c : "VERSEFALL".toCharArray()) {
            w += UiText.width(String.valueOf(c), size, true) + tracking;
        }

        return w - tracking;
    }

    public enum Icon {
        LIST,
        MUSIC,
        GEAR,
        EYE,
        DISPLAY,
        USER,
        FOLDER,
        SEARCH,
        SETTINGS;
    }
}
