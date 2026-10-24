package com.spotifyclient.hud.components;

import com.spotifyclient.ModSettings;
import com.spotifyclient.compat.Compat;
import com.spotifyclient.compat.GuiGraphics;
import com.spotifyclient.hud.Draggable;
import com.spotifyclient.render.r2d.MsdfFont;
import com.spotifyclient.render.r2d.R2D;
import com.spotifyclient.theme.ClientAccent;
import com.spotifyclient.theme.Colors;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import net.minecraft.client.Minecraft;

public final class WatermarkComp extends Draggable {
    private static final float PAD_X = 9.0F;
    private static final float PAD_Y = 6.0F;
    private static final float INFO_SIZE = 8.0F;
    private static final float BRAND_SIZE = 11.0F;
    private static final float H = 20.0F;
    private static final String BRAND = "Versefall";
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);
    private final Smooth visibility = new Smooth(0.0F);
    private boolean lastVisible;
    private float width = 80.0F;

    public WatermarkComp() {
        super("watermark", 5.0F, 5.0F);
    }

    @Override
    public float width() {
        return this.width;
    }

    @Override
    public float height() {
        return 20.0F;
    }

    @Override
    public boolean interactive() {
        return ModSettings.watermarkEnabled;
    }

    @Override
    protected void render(GuiGraphics c) {
        boolean show = ModSettings.watermarkEnabled;
        if (show != this.lastVisible) {
            this.visibility.run(show ? 1.0F : 0.0F, show ? 0.18 : 0.12);
            this.lastVisible = show;
        }

        float a = this.visibility.get();
        if (show && a <= 0.01F) {
            a = 0.01F;
        }

        if (show || !(a <= 0.01F)) {
            String name = playerName();
            String time = LocalTime.now().format(TIME);
            float brandW = R2D.width(MsdfFont.Font.PIXEL, "Versefall", 11.0F);
            float nameW = R2D.width(MsdfFont.Font.MEDIUM, name, 8.0F);
            float timeW = R2D.width(MsdfFont.Font.MEDIUM, time, 8.0F);
            float info = (ModSettings.watermarkUser ? 14.0F + nameW : 0.0F) + (ModSettings.watermarkTime ? 14.0F + timeW : 0.0F);
            this.width = brandW + info + 18.0F;
            float x = this.x();
            float y = this.y();
            pushScale(c, x + this.width / 2.0F, y + 10.0F, 0.92F + a * 0.08F);
            R2D.clientRect(c, x, y, this.width, 20.0F, a);
            int[] palette = palette();
            float phase = (float)(System.currentTimeMillis() % 1200L) / 1200.0F * 360.0F;
            float tx = x + 9.0F;
            float ty = y + 6.0F - 1.0F;
            float[] bb = R2D.bounds(MsdfFont.Font.PIXEL, "Versefall", 11.0F);
            float brandY = y + 10.0F - (bb[1] + bb[3]) / 2.0F;
            int index = 1;

            for (int i = 0; i < "Versefall".length(); i++) {
                String g = "Versefall".substring(i, i + 1);
                R2D.text(c, MsdfFont.Font.PIXEL, g, tx, brandY, 11.0F, Colors.multAlpha(indexed(palette, phase + index++ * 15.0F), a));
                tx += R2D.width(MsdfFont.Font.PIXEL, g, 11.0F);
            }

            float dotY = y + 10.0F + 0.5F;
            float[] ib = R2D.bounds(MsdfFont.Font.MEDIUM, "Hg", 8.0F);
            float infoY = y + 10.0F - (ib[1] + R2D.bounds(MsdfFont.Font.MEDIUM, "H", 8.0F)[3]) / 2.0F;
            if (ModSettings.watermarkUser) {
                tx += 6.0F;
                R2D.circle(c, tx + 1.0F, dotY, 1.0F, Colors.multAlpha(indexed(palette, phase), a));
                tx += 8.0F;
                tx = gradientString(c, name, tx, infoY, palette, phase, index, a);
                index += name.length() + 1;
            }

            if (ModSettings.watermarkTime) {
                tx += 6.0F;
                R2D.circle(c, tx + 1.0F, dotY, 1.0F, Colors.multAlpha(indexed(palette, phase + index * 15.0F), a));
                tx += 8.0F;
                gradientString(c, time, tx, infoY, palette, phase, index + 1, a);
            }

            c.pose().popPose();
        }
    }

    private static float gradientString(GuiGraphics c, String s, float x, float y, int[] palette, float phase, int index, float a) {
        for (int i = 0; i < s.length(); i++) {
            String g = s.substring(i, i + 1);
            R2D.text(c, MsdfFont.Font.MEDIUM, g, x, y, 8.0F, Colors.multAlpha(indexed(palette, phase + (index + i) * 15.0F), a));
            x += R2D.width(MsdfFont.Font.MEDIUM, g, 8.0F);
        }

        return x;
    }

    private static int[] palette() {
        int[] p = ClientAccent.palette();
        int[] out = new int[p.length];

        for (int i = 0; i < p.length; i++) {
            out[i] = Colors.lerp(0xFF000000 | p[i], -1, 0.45F);
        }

        return out;
    }

    private static int indexed(int[] palette, float degrees) {
        if (palette.length <= 1) {
            return palette[0];
        }

        float d = degrees % 360.0F;
        if (d < 0.0F) {
            d += 360.0F;
        }

        float t = d / 360.0F;
        float tri = t < 0.5F ? t * 2.0F : (1.0F - t) * 2.0F;
        float f = tri * (palette.length - 1);
        int i = Math.min((int)f, palette.length - 2);
        return Colors.lerp(palette[i], palette[i + 1], f - i);
    }

    private static String playerName() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getUser() != null && !mc.getUser().getName().isBlank()) {
            return mc.getUser().getName();
        } else {
            return mc.player != null ? Compat.name(mc.player.getGameProfile()) : "Player";
        }
    }
}
