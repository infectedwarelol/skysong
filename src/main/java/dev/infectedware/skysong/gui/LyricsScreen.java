package dev.infectedware.skysong.gui;

import net.minecraft.util.Mth;
import dev.infectedware.skysong.ModSettings;
import dev.infectedware.skysong.SkysongClient;
import dev.infectedware.skysong.SpotifyManager;
import dev.infectedware.skysong.compat.GuiGraphics;
import dev.infectedware.skysong.compat.VScreen;
import dev.infectedware.skysong.hud.HudSpace;
import dev.infectedware.skysong.lyrics.LyricsManager;
import dev.infectedware.skysong.render.r2d.MsdfFont;
import dev.infectedware.skysong.render.r2d.R2D;
import dev.infectedware.skysong.theme.ClientAccent;
import dev.infectedware.skysong.theme.Colors;
import dev.infectedware.skysong.ui.Clip;
import java.util.List;
import net.minecraft.network.chat.Component;

public class LyricsScreen extends VScreen {
    private static final float MAX_PANEL_WIDTH = 380.0F;
    private static final float MARGIN = 24.0F;
    private static final float PADDING = 14.0F;
    private static final float HEADER_HEIGHT = 40.0F;
    private static final float LINE_SIZE = 8.5F;
    private static final float TRANSLATION_SIZE = 6.5F;
    private static final float ROW_HEIGHT = 15.0F;
    private static final float TRANSLATION_HEIGHT = 9.0F;
    private static final long MANUAL_SCROLL_HOLD_MS = 3000L;
    private static final float SCROLL_STEP = 30.0F;
    private float scroll;
    private long lastManualScroll;
    private float panelX;
    private float listTop;
    private float listBottom;

    public LyricsScreen() {
        super(Component.literal("Lyrics"));
    }

    @Override
    protected void draw(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        SpotifyManager spotify = SkysongClient.getSpotifyManager();
        LyricsManager lyrics = SkysongClient.getLyricsManager();
        if (spotify == null || lyrics == null) {
            return;
        }

        float s = HudSpace.scale();
        float w = HudSpace.width();
        float h = HudSpace.height();
        float panelW = Math.min(MAX_PANEL_WIDTH, w - MARGIN * 2.0F);
        float panelH = h - MARGIN * 2.0F;
        this.panelX = (w - panelW) / 2.0F;
        float panelY = MARGIN;
        this.listTop = panelY + HEADER_HEIGHT;
        this.listBottom = panelY + panelH - PADDING;
        ctx.pose().pushPose();
        ctx.pose().translate(0.0F, 0.0F, 300.0F);
        ctx.pose().scale(s, s, 1.0F);
        R2D.rect(ctx, 0.0F, 0.0F, w, h, 0.0F, 1426063360);
        R2D.clientRectFixed(ctx, this.panelX, panelY, panelW, panelH, 12.0F, 1.0F);
        float textX = this.panelX + PADDING;
        float textWidth = panelW - PADDING * 2.0F;
        String track = spotify.getCurrentTrackName();
        String title = track.isEmpty() ? "Nothing playing" : track + "  ·  " + spotify.getCurrentArtist();
        R2D.text(ctx, MsdfFont.Font.SEMIBOLD, trim(title, textWidth, 10.0F, MsdfFont.Font.SEMIBOLD), textX, panelY + PADDING, 10.0F, -1);
        R2D.text(
            ctx, MsdfFont.Font.MEDIUM, "Click a line to jump there  ·  Esc closes", textX, panelY + PADDING + 14.0F, 6.5F, Colors.rgba(200, 205, 220, 200.0F)
        );
        List<LyricsManager.LyricLine> lines = lyrics.lines();
        if (lines.isEmpty()) {
            R2D.textCentered(
                ctx, MsdfFont.Font.MEDIUM, "No synced lyrics for this song", this.panelX + panelW / 2.0F, (this.listTop + this.listBottom) / 2.0F, 8.0F,
                Colors.rgba(220, 225, 240, 220.0F)
            );
        } else {
            long progress = spotify.getSmoothedProgressMs() + ModSettings.lyricOffsetMs;
            int current = currentIndex(lines, progress);
            this.follow(lines, current);
            float hoverY = HudSpace.mouseY();
            float hoverX = HudSpace.mouseX();
            Clip.push(ctx, this.panelX, this.listTop, this.panelX + panelW, this.listBottom);
            float y = this.listTop - this.scroll;

            for (int i = 0; i < lines.size(); i++) {
                LyricsManager.LyricLine line = lines.get(i);
                float rowHeight = rowHeight(line);
                if (y + rowHeight >= this.listTop && y <= this.listBottom) {
                    boolean hovered = hoverX >= this.panelX && hoverX <= this.panelX + panelW && hoverY >= y && hoverY < y + rowHeight;
                    if (i == current) {
                        R2D.rect(ctx, this.panelX + 6.0F, y + 1.0F, 2.5F, rowHeight - 2.0F, 1.0F, ClientAccent.accent(255.0F));
                    }

                    if (hovered) {
                        R2D.rect(ctx, this.panelX + 4.0F, y, panelW - 8.0F, rowHeight, 5.0F, Colors.rgba(255, 255, 255, 22.0F));
                    }

                    int color = i == current ? -1 : (i < current ? Colors.rgba(170, 175, 190, 170.0F) : Colors.rgba(215, 220, 235, 225.0F));
                    MsdfFont.Font font = i == current ? MsdfFont.Font.SEMIBOLD : MsdfFont.Font.MEDIUM;
                    R2D.text(ctx, font, trim(line.text, textWidth, LINE_SIZE, font), textX, y + 3.0F, LINE_SIZE, color);
                    String translation = line.translation;
                    if (ModSettings.lyricTranslate && translation != null) {
                        R2D.text(
                            ctx, MsdfFont.Font.MEDIUM, trim(translation, textWidth, TRANSLATION_SIZE, MsdfFont.Font.MEDIUM), textX, y + ROW_HEIGHT - 1.0F,
                            TRANSLATION_SIZE, Colors.rgba(185, 192, 210, 190.0F)
                        );
                    }
                }

                y += rowHeight;
            }

            Clip.pop(ctx);
        }

        ctx.pose().popPose();
        this.drawWidgets(ctx, mouseX, mouseY, delta);
    }

    private void follow(List<LyricsManager.LyricLine> lines, int current) {
        float total = 0.0F;
        float currentTop = 0.0F;

        for (int i = 0; i < lines.size(); i++) {
            if (i == current) {
                currentTop = total;
            }

            total += rowHeight(lines.get(i));
        }

        float visible = this.listBottom - this.listTop;
        float maxScroll = Math.max(0.0F, total - visible);
        if (System.currentTimeMillis() - this.lastManualScroll > MANUAL_SCROLL_HOLD_MS && current >= 0) {
            float target = currentTop - visible * 0.4F;
            this.scroll += (target - this.scroll) * 0.15F;
        }

        this.scroll = Mth.clamp(this.scroll, 0.0F, maxScroll);
    }

    @Override
    protected boolean scrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        this.scroll -= (float)vertical * SCROLL_STEP;
        this.lastManualScroll = System.currentTimeMillis();
        return true;
    }

    @Override
    public boolean onClick(double mouseX, double mouseY, int button) {
        LyricsManager lyrics = SkysongClient.getLyricsManager();
        SpotifyManager spotify = SkysongClient.getSpotifyManager();
        if (button != 0 || lyrics == null || spotify == null) {
            return super.onClick(mouseX, mouseY, button);
        }

        float y = (float)mouseY / HudSpace.scale();
        if (y < this.listTop || y > this.listBottom) {
            return super.onClick(mouseX, mouseY, button);
        }

        float rowTop = this.listTop - this.scroll;

        for (LyricsManager.LyricLine line : lyrics.lines()) {
            float rowHeight = rowHeight(line);
            if (y >= rowTop && y < rowTop + rowHeight) {
                spotify.seekTo(Math.max(0L, line.timeMs - ModSettings.lyricOffsetMs));
                this.lastManualScroll = 0L;
                return true;
            }

            rowTop += rowHeight;
        }

        return super.onClick(mouseX, mouseY, button);
    }

    private static float rowHeight(LyricsManager.LyricLine line) {
        return ModSettings.lyricTranslate && line.translation != null ? ROW_HEIGHT + TRANSLATION_HEIGHT : ROW_HEIGHT;
    }

    private static int currentIndex(List<LyricsManager.LyricLine> lines, long progress) {
        int current = -1;

        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).timeMs > progress) {
                break;
            }

            current = i;
        }

        return current;
    }

    private static String trim(String text, float maxWidth, float size, MsdfFont.Font font) {
        if (R2D.width(font, text, size) <= maxWidth) {
            return text;
        }

        String ellipsis = "…";
        int low = 0;
        int high = text.length();

        while (low < high) {
            int middle = (low + high + 1) >>> 1;
            if (R2D.width(font, text.substring(0, middle) + ellipsis, size) <= maxWidth) {
                low = middle;
            } else {
                high = middle - 1;
            }
        }

        return text.substring(0, low) + ellipsis;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
