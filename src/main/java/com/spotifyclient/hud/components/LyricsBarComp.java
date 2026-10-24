package com.spotifyclient.hud.components;

import com.spotifyclient.ModSettings;
import com.spotifyclient.SpotifyManager;
import com.spotifyclient.SpotifyModClient;
import com.spotifyclient.compat.GuiGraphics;
import com.spotifyclient.hud.DragSystem;
import com.spotifyclient.hud.Draggable;
import com.spotifyclient.hud.HudSpace;
import com.spotifyclient.lyrics.LyricsManager;
import com.spotifyclient.render.GlassRenderer;
import com.spotifyclient.render.Icons;
import com.spotifyclient.render.WorldTextRenderer;
import com.spotifyclient.render.r2d.MsdfFont;
import com.spotifyclient.render.r2d.R2D;
import com.spotifyclient.theme.Colors;
import com.spotifyclient.ui.Clip;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

public final class LyricsBarComp extends Draggable {
    static final int BG = -351597809;
    static final int BORDER = 486539263;
    static final int WHITE = -1;
    static final int DIM = -8553080;
    private static final float H = 22.0F;
    private static final float ICON = 22.0F;
    private static final float GAP = 4.0F;
    private static final float ART = 16.0F;
    private static final float TEXT = 8.2F;
    private static final float WAVE_W = 9.0F;
    private static final float MAX_TEXT_W = 250.0F;
    private static final float COORD_H = 14.0F;
    private static final float LINE_ANIM_MS = 280.0F;
    private final Smooth visibility = new Smooth(0.0F);
    private boolean lastVisible;
    private float pillW = 150.0F;
    private float shift;
    private String lineText = "";
    private String prevText = "";
    private long lineChangedAt;
    private final float[] wave = new float[]{3.0F, 5.0F, 4.0F, 3.0F};
    private long lastNs = System.nanoTime();

    public LyricsBarComp() {
        super("lyricsbar", 0.0F, 5.0F);
    }

    @Override
    public float[] defaultPosition() {
        return new float[]{(HudSpace.width() - this.width()) / 2.0F, 5.0F};
    }

    @Override
    public float width() {
        return (ModSettings.lyricsBarLogo ? 26.0F : 0.0F) + this.pillW;
    }

    @Override
    public float height() {
        return 22.0F + (ModSettings.lyricsBarCoords ? 18.0F : 0.0F);
    }

    @Override
    public boolean interactive() {
        return ModSettings.enableHudLyrics && ModSettings.hudLyricsStyle == 0;
    }

    @Override
    protected void render(GuiGraphics c) {
        long nowNs = System.nanoTime();
        float dt = Math.min(0.1F, (float)(nowNs - this.lastNs) / 1.0E9F);
        this.lastNs = nowNs;
        Minecraft mc = Minecraft.getInstance();
        SpotifyManager spotify = SpotifyModClient.getSpotifyManager();
        LyricsManager lyrics = SpotifyModClient.getLyricsManager();
        boolean hasSong = spotify != null && !spotify.getCurrentTrackName().isEmpty();
        boolean show = this.interactive() && (hasSong || DragSystem.get().dragMode());
        if (show != this.lastVisible) {
            this.visibility.run(show ? 1.0F : 0.0F, show ? 0.2 : 0.14);
            this.lastVisible = show;
        }

        float a = this.visibility.get();
        if (!(a <= 0.01F)) {
            long progress = hasSong ? spotify.getSmoothedProgressMs() + ModSettings.lyricOffsetMs : 0L;
            LyricsManager.LyricLine line = hasSong && spotify.isPlaying() && lyrics != null ? lyrics.getLineAtTime(progress) : null;
            String text = line != null ? line.text.trim() : "";
            long now = System.currentTimeMillis();
            if (!text.equals(this.lineText)) {
                this.prevText = this.lineText;
                this.lineText = text;
                this.lineChangedAt = now;
            }

            String[] words = this.lineText.isEmpty() ? new String[0] : this.lineText.split("\\s+");
            long[] at = line != null && words.length > 0 ? WorldTextRenderer.wordTimes(words, line.timeMs, line.endMs - line.timeMs) : null;
            String fallback = hasSong ? spotify.getCurrentTrackName() + "  ·  " + spotify.getCurrentArtist() : "Versefall";
            float textW = words.length > 0 ? lineWidth(words, currentWord(at, progress, words.length)) : R2D.width(MsdfFont.Font.SEMIBOLD, fallback, 8.2F);
            float areaW = Math.min(textW, 250.0F);
            boolean art = ModSettings.showAlbumArt;
            float target = 4.0F + (art ? 23.0F : 5.0F) + Math.max(areaW, 40.0F) + 9.0F + 9.0F + 8.0F;
            float before = this.pillW;
            this.pillW = this.pillW + (target - this.pillW) * (1.0F - (float)Math.exp(-dt * 10.0F));
            if (!this.drag().dragging()) {
                this.drag().setTarget(this.drag().targetX() - (this.pillW - before) / 2.0F, this.drag().targetY());
            }

            float x = this.x();
            float y = this.y();
            pushScale(c, x + this.width() / 2.0F, y + 11.0F, 0.92F + a * 0.08F);
            float px = x;
            if (ModSettings.lyricsBarLogo) {
                R2D.rect(c, x, y, 22.0F, 22.0F, 7.0F, Colors.multAlpha(-351597809, a));
                R2D.outline(c, x, y, 22.0F, 22.0F, 7.0F, 0.5F, Colors.multAlpha(486539263, a));
                Icons.logo(c, x + 1.5F, y + 1.5F, 19.0F, a);
                px = x + 22.0F + 4.0F;
            }

            R2D.rect(c, px, y, this.pillW, 22.0F, 11.0F, Colors.multAlpha(-351597809, a));
            R2D.outline(c, px, y, this.pillW, 22.0F, 11.0F, 0.5F, Colors.multAlpha(486539263, a));
            float cx = px + 4.0F;
            if (art) {
                float ay = y + 3.0F;
                Identifier cover = hasSong ? spotify.getAlbumArtId() : null;
                if (cover != null) {
                    R2D.image(c, cover, cx, ay, 16.0F, 16.0F, 8.0F, 0.0F, 0.0F, 1.0F, 1.0F, Math.round(a * 255.0F) << 24 | 16777215, true);
                } else {
                    R2D.circle(c, cx + 8.0F, ay + 8.0F, 8.0F, Colors.rgba(40, 40, 48, 255.0F * a));
                    note(c, cx + 8.0F, ay + 8.0F, Colors.multAlpha(-1, a));
                }

                cx += 23.0F;
            } else {
                cx += 5.0F;
            }

            float textRight = px + this.pillW - 9.0F - 9.0F - 8.0F;
            float visibleW = Math.max(0.0F, textRight - cx);
            float cy = y + 11.0F;
            Clip.push(c, cx - 1.0F, y, textRight + 1.0F, y + 22.0F);
            float t = Mth.clamp((float)(now - this.lineChangedAt) / 280.0F, 0.0F, 1.0F);
            float ease = 1.0F - (1.0F - t) * (1.0F - t) * (1.0F - t);
            if (t < 1.0F && !this.prevText.isEmpty()) {
                R2D.textCentered(c, MsdfFont.Font.SEMIBOLD, this.prevText, cx, cy - ease * 7.0F, 8.2F, Colors.multAlpha(-1, a * (1.0F - ease)));
            }

            float lineA = a * (this.prevText.isEmpty() && this.lineText.isEmpty() ? 1.0F : ease);
            float lineY = cy + (1.0F - ease) * 7.0F;
            if (words.length > 0) {
                int current = currentWord(at, progress, words.length);
                float focus = wordCenter(words, current);
                float targetShift = textW > visibleW ? Mth.clamp(focus - visibleW * 0.45F, 0.0F, textW - visibleW) : 0.0F;
                this.shift = this.shift + (targetShift - this.shift) * (1.0F - (float)Math.exp(-dt * 8.0F));
                drawKaraoke(c, words, at, progress, current, cx - this.shift, lineY, lineA);
            } else {
                this.shift = 0.0F;
                R2D.textCentered(c, MsdfFont.Font.SEMIBOLD, trim(fallback, visibleW), cx, lineY, 8.2F, Colors.multAlpha(-8553080, lineA));
            }

            Clip.pop(c);
            boolean playing = hasSong && spotify.isPlaying();
            float wx = px + this.pillW - 8.0F - 9.0F;
            float time = (float)now / 1000.0F;

            for (int i = 0; i < this.wave.length; i++) {
                float targetH = playing ? 3.0F + 7.0F * Math.abs((float)Math.sin(time * (5.1F + i * 1.7F) + i * 1.3F)) : 2.2F;
                this.wave[i] = this.wave[i] + (targetH - this.wave[i]) * (1.0F - (float)Math.exp(-dt * 14.0F));
                float bw = 1.7F;
                float bx = wx + i * (bw + 0.9F);
                R2D.rect(c, bx, cy - this.wave[i] / 2.0F, bw, this.wave[i], bw / 2.0F, Colors.multAlpha(i != 1 && i != 2 ? -4342328 : -1, a));
            }

            if (ModSettings.lyricsBarCoords && mc.player != null) {
                String coords = String.format(
                    Locale.ROOT, "x %d   y %d   z %d", (int)Math.floor(mc.player.getX()), (int)Math.floor(mc.player.getY()), (int)Math.floor(mc.player.getZ())
                );
                float cw = 17.0F + R2D.width(MsdfFont.Font.SEMIBOLD, coords, 7.0F) + 9.0F;
                float ccx = px + (this.pillW - cw) / 2.0F;
                float ccy = y + 22.0F + 4.0F;
                R2D.rect(c, ccx, ccy, cw, 14.0F, 7.0F, Colors.multAlpha(-351597809, a));
                R2D.outline(c, ccx, ccy, cw, 14.0F, 7.0F, 0.5F, Colors.multAlpha(486539263, a));
                navArrow(c, ccx + 10.0F, ccy + 7.0F, mc.player.getYRot(), Colors.multAlpha(-1, a));
                R2D.textCentered(c, MsdfFont.Font.SEMIBOLD, coords, ccx + 17.0F, ccy + 7.0F, 7.0F, Colors.multAlpha(-1, a));
            }

            c.pose().popPose();
        }
    }

    private static void drawKaraoke(GuiGraphics c, String[] words, long[] at, long progress, int current, float x, float cy, float a) {
        float space = R2D.width(MsdfFont.Font.SEMIBOLD, " ", 8.2F) * 1.15F;

        for (int i = 0; i < words.length; i++) {
            MsdfFont.Font font = i == current ? MsdfFont.Font.BOLD : MsdfFont.Font.SEMIBOLD;
            float w = R2D.width(font, words[i], 8.2F);
            if (i < current) {
                R2D.textCentered(c, font, words[i], x, cy, 8.2F, Colors.multAlpha(-1, a));
            } else if (i > current) {
                R2D.textCentered(c, font, words[i], x, cy, 8.2F, Colors.multAlpha(-8553080, a));
            } else {
                float fill = wordFill(at, progress, i);
                R2D.textCentered(c, font, words[i], x, cy, 8.2F, Colors.multAlpha(-8553080, a));
                if (fill > 0.0F) {
                    Clip.push(c, x - 1.0F, cy - 10.0F, x + w * fill, cy + 10.0F);
                    R2D.textCentered(c, font, words[i], x, cy, 8.2F, Colors.multAlpha(-1, a));
                    Clip.pop(c);
                }
            }

            x += w + space;
        }
    }

    private static int currentWord(long[] at, long progress, int count) {
        if (at == null) {
            return -1;
        }

        int current = -1;

        for (int i = 0; i < count; i++) {
            if (progress >= at[i]) {
                current = i;
            }
        }

        return current;
    }

    private static float wordFill(long[] at, long progress, int i) {
        long start = at[i];
        long end = i + 1 < at.length ? at[i + 1] : start + 900L;
        end = Math.min(end, start + 1200L);
        return Mth.clamp((float)(progress - start) / (float)Math.max(80L, end - start), 0.0F, 1.0F);
    }

    private static float lineWidth(String[] words, int current) {
        float space = R2D.width(MsdfFont.Font.SEMIBOLD, " ", 8.2F) * 1.15F;
        float w = -space;

        for (int i = 0; i < words.length; i++) {
            w += R2D.width(i == current ? MsdfFont.Font.BOLD : MsdfFont.Font.SEMIBOLD, words[i], 8.2F) + space;
        }

        return Math.max(0.0F, w);
    }

    private static float wordCenter(String[] words, int current) {
        if (current < 0) {
            return 0.0F;
        }

        float space = R2D.width(MsdfFont.Font.SEMIBOLD, " ", 8.2F) * 1.15F;
        float x = 0.0F;

        for (int i = 0; i < current; i++) {
            x += R2D.width(MsdfFont.Font.SEMIBOLD, words[i], 8.2F) + space;
        }

        return x + R2D.width(MsdfFont.Font.BOLD, words[current], 8.2F) / 2.0F;
    }

    private static String trim(String s, float max) {
        if (R2D.width(MsdfFont.Font.SEMIBOLD, s, 8.2F) <= max) {
            return s;
        }

        while (s.length() > 1 && R2D.width(MsdfFont.Font.SEMIBOLD, s + "…", 8.2F) > max) {
            s = s.substring(0, s.length() - 1);
        }

        return s.stripTrailing() + "…";
    }

    private static void navArrow(GuiGraphics c, float cx, float cy, float yaw, int col) {
        navArrow(c, cx, cy, yaw, col, 1.0F);
    }

    public static void navArrow(GuiGraphics c, float cx, float cy, float yaw, int col, float scale) {
        double r = Math.toRadians(yaw + 180.0F);
        float sin = (float)Math.sin(r);
        float cos = (float)Math.cos(r);
        float[][] pts = new float[][]{{0.0F, -4.2F}, {3.2F, 3.4F}, {0.0F, 1.6F}, {-3.2F, 3.4F}};
        float[][] out = new float[pts.length][2];

        for (int i = 0; i < pts.length; i++) {
            float px = pts[i][0] * scale;
            float py = pts[i][1] * scale;
            out[i][0] = cx + px * cos - py * sin;
            out[i][1] = cy + px * sin + py * cos;
        }

        GlassRenderer.fillPolygon(c, new float[][]{out[0], out[1], out[2]}, col);
        GlassRenderer.fillPolygon(c, new float[][]{out[0], out[2], out[3]}, col);
    }

    static void note(GuiGraphics c, float nx, float ny, int col) {
        R2D.circle(c, nx - 1.6F, ny + 2.2F, 2.0F, col);
        R2D.rect(c, nx - 0.1F, ny - 4.2F, 1.1F, 6.6F, 0.5F, col);
        R2D.rect(c, nx - 0.1F, ny - 4.2F, 3.2F, 1.2F, 0.6F, col);
    }
}
