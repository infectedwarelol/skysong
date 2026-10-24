package dev.infectedware.skysong.render;

import dev.infectedware.skysong.ModSettings;
import dev.infectedware.skysong.compat.GuiGraphics;
import dev.infectedware.skysong.compat.HudLayer;
import dev.infectedware.skysong.ui.Theme;

public class CinematicTextRenderer implements HudLayer {
    private static final float[][] SHADOW_OFFSETS = new float[][]{{0.5F, 0.5F}, {-0.5F, 0.5F}, {0.5F, -0.5F}, {-0.5F, -0.5F}, {0.0F, 1.0F}};
    private String currentLyric = "";
    private long lyricStartTime = 0L;

    public void setCurrentLyric(String lyric) {
        if (!this.currentLyric.equals(lyric)) {
            this.currentLyric = lyric;
            this.lyricStartTime = System.currentTimeMillis();
        }
    }

    @Override
    public void render(GuiGraphics ctx, float tickCounter) {
        if (ModSettings.enableHudLyrics && ModSettings.hudLyricsStyle == 1 && this.currentLyric != null && !this.currentLyric.isEmpty()) {
            float t = ModSettings.cinematicFades ? (float)(System.currentTimeMillis() - this.lyricStartTime) / 400.0F : 1.0F;
            float alpha = Theme.easeOutCubic(t);
            if (!(alpha < 0.03F)) {
                float scale = Theme.unitScale();
                float sw = ctx.guiWidth() / scale;
                float sh = ctx.guiHeight() / scale;
                float w = UiText.width(this.currentLyric, 11, true);
                float x = (sw - w) / 2.0F;
                float cy = sh * 0.72F + (1.0F - alpha) * 4.0F;
                if (ModSettings.bouncingLyrics) {
                    cy += (float)Math.sin(System.currentTimeMillis() / 150.0) * 1.5F;
                }

                ctx.pose().pushPose();
                ctx.pose().scale(scale, scale, 1.0F);
                if (ModSettings.textDropShadows) {
                    int shadow = Theme.alpha(1073741840, alpha);

                    for (float[] o : SHADOW_OFFSETS) {
                        UiText.drawCentered(ctx, this.currentLyric, x + o[0], cy + o[1] + 0.5F, shadow, 11, true);
                    }
                }

                UiText.drawCentered(ctx, this.currentLyric, x, cy, Theme.alpha(-723201, alpha), 11, true);
                ctx.pose().popPose();
            }
        }
    }
}
