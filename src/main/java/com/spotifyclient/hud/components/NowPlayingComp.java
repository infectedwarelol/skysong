package com.spotifyclient.hud.components;

import com.spotifyclient.ModSettings;
import com.spotifyclient.SpotifyManager;
import com.spotifyclient.SpotifyModClient;
import com.spotifyclient.compat.GuiGraphics;
import com.spotifyclient.hud.DragSystem;
import com.spotifyclient.hud.Draggable;
import com.spotifyclient.render.GlassRenderer;
import com.spotifyclient.render.r2d.MsdfFont;
import com.spotifyclient.render.r2d.R2D;
import com.spotifyclient.theme.ClientAccent;
import com.spotifyclient.theme.Colors;
import java.util.Locale;
import net.minecraft.resources.Identifier;

public final class NowPlayingComp extends Draggable {
    private static final float H = 36.0F;
    private static final float PAD = 7.0F;
    private static final float ART = 22.0F;
    private static final float CONTROLS_W = 30.0F;
    private static final float ISLAND_W = 172.0F;
    private static final float ISLAND_H = 54.0F;
    private static final float ISLAND_ART = 42.0F;
    private float barPct;
    private final Smooth visibility = new Smooth(0.0F);
    private boolean lastVisible;

    public NowPlayingComp() {
        super("nowplaying", 5.0F, 29.0F);
    }

    private static boolean island() {
        return ModSettings.nowPlayingStyle == 0;
    }

    @Override
    public float width() {
        if (island()) {
            return ModSettings.showAlbumArt ? 172.0F : 124.0F;
        } else {
            return ModSettings.showAlbumArt ? 168.0F : 142.0F;
        }
    }

    @Override
    public float height() {
        return island() ? 54.0F : 36.0F;
    }

    @Override
    public boolean interactive() {
        return ModSettings.showNowPlaying;
    }

    @Override
    protected void render(GuiGraphics c) {
        SpotifyManager spotify = SpotifyModClient.getSpotifyManager();
        boolean hasSong = spotify != null && !spotify.getCurrentTrackName().isEmpty();
        boolean show = ModSettings.showNowPlaying && (hasSong || DragSystem.get().dragMode());
        if (show != this.lastVisible) {
            this.visibility.run(show ? 1.0F : 0.0F, show ? 0.18 : 0.12);
            this.lastVisible = show;
        }

        float a = this.visibility.get();
        if (!(a <= 0.01F)) {
            if (island()) {
                this.renderIsland(c, spotify, hasSong, a);
            } else {
                float w = this.width();
                float x = this.x();
                float y = this.y();
                int accent = ModSettings.dynamicColors && hasSong ? 0xFF000000 | spotify.getSongColor() : ClientAccent.accentOpaque();
                pushScale(c, x + w / 2.0F, y + 18.0F, 0.94F + a * 0.06F);
                R2D.clientRect(c, x, y, w, 36.0F, a);
                float textX = x + 7.0F;
                if (ModSettings.showAlbumArt) {
                    float ay = y + 7.0F;
                    float r = Math.min(ModSettings.rectCornerRadius - 1.0F, 11.0F);
                    Identifier art = hasSong ? spotify.getAlbumArtId() : null;
                    if (art != null) {
                        R2D.image(c, art, x + 7.0F, ay, 22.0F, 22.0F, r, 0.0F, 0.0F, 1.0F, 1.0F, Math.round(a * 255.0F) << 24 | 16777215, true);
                    } else {
                        int top = Colors.multAlpha(accent, a);
                        int bottom = Colors.multAlpha(Colors.mixWhite(accent, 0.25F), a);
                        R2D.rect(c, x + 7.0F, ay, 22.0F, 22.0F, r, top, top, bottom, bottom);
                        float nx = x + 7.0F + 11.0F;
                        float ny = ay + 11.0F;
                        int white = Colors.multAlpha(-1, a);
                        R2D.circle(c, nx - 2.0F, ny + 3.0F, 2.6F, white);
                        R2D.rect(c, nx - 0.2F, ny - 5.5F, 1.4F, 8.8F, 0.7F, white);
                        R2D.rect(c, nx - 0.2F, ny - 5.5F, 4.2F, 1.6F, 0.8F, white);
                    }

                    R2D.outline(c, x + 7.0F, ay, 22.0F, 22.0F, r, 0.5F, Colors.rgba(255, 255, 255, 50.0F * a));
                    textX = x + 7.0F + 22.0F + 8.0F;
                }

                float right = x + w - 7.0F;
                String title = hasSong ? spotify.getCurrentTrackName().toUpperCase(Locale.ROOT) : "NOW PLAYING";
                String artist = hasSong ? spotify.getCurrentArtist() : "Play something on Spotify";
                long prog = hasSong ? spotify.getSmoothedProgressMs() / 1000L : 0L;
                long dur = hasSong ? spotify.getDurationMs() / 1000L : 0L;
                String time = String.format("%d:%02d / %d:%02d", prog / 60L, prog % 60L, dur / 60L, dur % 60L);
                float timeW = R2D.width(MsdfFont.Font.MEDIUM, time, 6.0F);
                R2D.textCentered(c, MsdfFont.Font.MEDIUM, time, right - timeW, y + 10.0F, 6.0F, Colors.rgba(200, 205, 220, 220.0F * a));
                R2D.textCentered(
                    c,
                    MsdfFont.Font.SEMIBOLD,
                    trim(title, right - timeW - 6.0F - textX, 7.5F, MsdfFont.Font.SEMIBOLD),
                    textX,
                    y + 10.0F,
                    7.5F,
                    Colors.multAlpha(-1, a)
                );
                R2D.textCentered(
                    c,
                    MsdfFont.Font.MEDIUM,
                    trim(artist, right - textX, 6.5F, MsdfFont.Font.MEDIUM),
                    textX,
                    y + 19.0F,
                    6.5F,
                    Colors.rgba(215, 220, 235, 215.0F * a)
                );
                float barRight = ModSettings.showMediaControls ? right - 30.0F - 4.0F : right;
                float barY = y + 27.5F;
                float barW = barRight - textX;
                float pct = dur > 0L ? Math.min(1.0F, (float)prog / (float)dur) : 0.0F;
                R2D.rect(c, textX, barY, barW, 2.0F, 1.0F, Colors.rgba(0, 0, 0, 70.0F * a));
                int ga = ClientAccent.gradientA(255.0F * a);
                int gb = ClientAccent.gradientB(255.0F * a);
                if (ModSettings.dynamicColors && hasSong) {
                    ga = gb = Colors.multAlpha(accent, a);
                }

                R2D.rect(c, textX, barY, Math.max(2.0F, barW * pct), 2.0F, 1.0F, ga, gb, gb, ga);
                R2D.circle(c, textX + barW * pct, barY + 1.0F, 2.4F, Colors.multAlpha(-1, a));
                if (ModSettings.showMediaControls) {
                    float cy = barY + 1.0F;
                    float cx = right - 30.0F;
                    int dim = Colors.rgba(220, 225, 240, 200.0F * a);
                    int full = Colors.multAlpha(-1, a);
                    prev(c, cx + 3.0F, cy, dim);
                    if (hasSong && spotify.isPlaying()) {
                        pause(c, cx + 15.0F, cy, full);
                    } else {
                        play(c, cx + 15.0F, cy, full);
                    }

                    next(c, cx + 27.0F, cy, dim);
                }

                c.pose().popPose();
            }
        }
    }

    private void renderIsland(GuiGraphics c, SpotifyManager spotify, boolean hasSong, float a) {
        float w = this.width();
        float h = 54.0F;
        float x = this.x();
        float y = this.y();
        pushScale(c, x + w / 2.0F, y + h / 2.0F, 0.94F + a * 0.06F);
        R2D.rect(c, x, y, w, h, 12.0F, Colors.multAlpha(-351597809, a));
        R2D.outline(c, x, y, w, h, 12.0F, 0.5F, Colors.multAlpha(486539263, a));
        float pad = 6.0F;
        float textX = x + pad + 4.0F;
        if (ModSettings.showAlbumArt) {
            float ay = y + (h - 42.0F) / 2.0F;
            Identifier art = hasSong ? spotify.getAlbumArtId() : null;
            if (art != null) {
                R2D.image(c, art, x + pad, ay, 42.0F, 42.0F, 8.0F, 0.0F, 0.0F, 1.0F, 1.0F, Math.round(a * 255.0F) << 24 | 16777215, true);
            } else {
                R2D.rect(c, x + pad, ay, 42.0F, 42.0F, 8.0F, Colors.rgba(36, 36, 44, 255.0F * a));
                LyricsBarComp.note(c, x + pad + 21.0F, ay + 21.0F, Colors.multAlpha(-1, a));
            }

            textX = x + pad + 42.0F + 8.0F;
        }

        float right = x + w - 9.0F;
        float colW = right - textX;
        String title = hasSong ? spotify.getCurrentTrackName() : "Nothing playing";
        String artist = hasSong ? spotify.getCurrentArtist() : "Play something on Spotify";
        R2D.textCentered(c, MsdfFont.Font.SEMIBOLD, trim(title, colW, 8.4F, MsdfFont.Font.SEMIBOLD), textX, y + 11.5F, 8.4F, Colors.multAlpha(-1, a));
        R2D.textCentered(
            c, MsdfFont.Font.MEDIUM, trim(artist, colW, 6.8F, MsdfFont.Font.MEDIUM), textX, y + 21.0F, 6.8F, Colors.rgba(150, 150, 162, 255.0F * a)
        );
        float ctrlY = y + 32.5F;
        float mid = textX + colW / 2.0F;
        int white = Colors.multAlpha(-1, a);
        bigPrev(c, mid - 17.0F, ctrlY, white);
        if (hasSong && spotify.isPlaying()) {
            bigPause(c, mid, ctrlY, white);
        } else {
            bigPlay(c, mid, ctrlY, white);
        }

        bigNext(c, mid + 17.0F, ctrlY, white);
        long prog = hasSong ? spotify.getSmoothedProgressMs() : 0L;
        long dur = hasSong ? spotify.getDurationMs() : 0L;
        float pct = dur > 0L ? Math.min(1.0F, (float)prog / (float)dur) : 0.0F;
        this.barPct = this.barPct + (pct - this.barPct) * 0.3F;
        float barY = y + h - 10.0F;
        float barH = 3.4F;
        R2D.rect(c, textX, barY, colW, barH, barH / 2.0F, Colors.rgba(255, 255, 255, 46.0F * a));
        if (this.barPct > 0.001F) {
            R2D.rect(c, textX, barY, Math.max(barH, colW * this.barPct), barH, barH / 2.0F, white);
        }

        c.pose().popPose();
    }

    private static void bigPrev(GuiGraphics c, float cx, float cy, int col) {
        R2D.rect(c, cx - 4.4F, cy - 3.8F, 1.5F, 7.6F, 0.75F, col);
        GlassRenderer.fillPolygon(c, new float[][]{{cx + 3.8F, cy - 3.8F}, {cx + 3.8F, cy + 3.8F}, {cx - 2.4F, cy}}, col);
    }

    private static void bigNext(GuiGraphics c, float cx, float cy, int col) {
        GlassRenderer.fillPolygon(c, new float[][]{{cx - 3.8F, cy - 3.8F}, {cx + 2.4F, cy}, {cx - 3.8F, cy + 3.8F}}, col);
        R2D.rect(c, cx + 2.9F, cy - 3.8F, 1.5F, 7.6F, 0.75F, col);
    }

    private static void bigPause(GuiGraphics c, float cx, float cy, int col) {
        R2D.rect(c, cx - 3.4F, cy - 4.4F, 2.4F, 8.8F, 1.2F, col);
        R2D.rect(c, cx + 1.0F, cy - 4.4F, 2.4F, 8.8F, 1.2F, col);
    }

    private static void bigPlay(GuiGraphics c, float cx, float cy, int col) {
        GlassRenderer.fillPolygon(c, new float[][]{{cx - 3.0F, cy - 4.4F}, {cx + 4.4F, cy}, {cx - 3.0F, cy + 4.4F}}, col);
    }

    private static String trim(String s, float max, float size, MsdfFont.Font font) {
        if (R2D.width(font, s, size) <= max) {
            return s;
        }

        while (s.length() > 1 && R2D.width(font, s + "…", size) > max) {
            s = s.substring(0, s.length() - 1);
        }

        return s.stripTrailing() + "…";
    }

    private static void prev(GuiGraphics c, float cx, float cy, int col) {
        R2D.rect(c, cx - 3.0F, cy - 3.0F, 1.2F, 6.0F, 0.6F, col);
        GlassRenderer.fillPolygon(c, new float[][]{{cx + 3.0F, cy - 3.0F}, {cx + 3.0F, cy + 3.0F}, {cx - 1.5F, cy}}, col);
    }

    private static void next(GuiGraphics c, float cx, float cy, int col) {
        GlassRenderer.fillPolygon(c, new float[][]{{cx - 3.0F, cy - 3.0F}, {cx + 1.5F, cy}, {cx - 3.0F, cy + 3.0F}}, col);
        R2D.rect(c, cx + 1.8F, cy - 3.0F, 1.2F, 6.0F, 0.6F, col);
    }

    private static void pause(GuiGraphics c, float cx, float cy, int col) {
        R2D.rect(c, cx - 2.4F, cy - 3.0F, 1.8F, 6.0F, 0.9F, col);
        R2D.rect(c, cx + 0.6F, cy - 3.0F, 1.8F, 6.0F, 0.9F, col);
    }

    private static void play(GuiGraphics c, float cx, float cy, int col) {
        GlassRenderer.fillPolygon(c, new float[][]{{cx - 2.0F, cy - 3.0F}, {cx + 3.0F, cy}, {cx - 2.0F, cy + 3.0F}}, col);
    }
}
