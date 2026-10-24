package dev.infectedware.skysong.render;

import dev.infectedware.skysong.compat.Compat;
import dev.infectedware.skysong.compat.GuiGraphics;
import dev.infectedware.skysong.compat.Ids;
import dev.infectedware.skysong.ui.Theme;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public final class UiText {
    private static final float BASELINE = 7.0F;
    private static final float CAP_HEIGHT = 0.7F;

    private UiText() {
    }

    public static Component styled(String s, int size, boolean bold) {
        return styled(s, size, bold, Theme.pixelsPerUnit());
    }

    private static Component styled(String s, int size, boolean bold, int density) {
        Identifier font = Ids.of("skysong", "ui_" + (bold ? "b" : "r") + size + "x" + density);
        return Component.literal(s).setStyle(Compat.withFont(Style.EMPTY, font));
    }

    public static void draw(GuiGraphics ctx, String s, float x, float y, int color, int size, boolean bold) {
        int density = densityAt(ctx);
        drawSnapped(ctx, styled(s, size, bold, density).getVisualOrderText(), x, y, color);
    }

    public static void drawCentered(GuiGraphics ctx, String s, float x, float cy, int color, int size, boolean bold) {
        draw(ctx, s, x, topFor(cy, size), color, size, bold);
    }

    public static void draw(GuiGraphics ctx, FormattedCharSequence t, float x, float y, int color) {
        drawSnapped(ctx, t, x, y, color);
    }

    public static float width(String s, int size, boolean bold) {
        return renderer().width(styled(s, size, bold));
    }

    public static String trim(String s, float maxWidth, int size, boolean bold) {
        if (width(s, size, bold) <= maxWidth) {
            return s;
        }

        while (!s.isEmpty() && width(s + "…", size, bold) > maxWidth) {
            s = s.substring(0, s.length() - 1);
        }

        return s.stripTrailing() + "…";
    }

    public static List<FormattedCharSequence> wrap(String s, float maxWidth, int size, boolean bold) {
        FormattedText text = styled(s, size, bold);
        return renderer().split(text, (int)maxWidth);
    }

    public static float topFor(float cy, int size) {
        return cy + size * 0.7F / 2.0F - 7.0F;
    }

    private static Font renderer() {
        return Minecraft.getInstance().font;
    }

    private static int densityAt(GuiGraphics ctx) {
        Matrix4f m = ctx.pose().last().pose();
        double ppu = m.m00() * Minecraft.getInstance().getWindow().getGuiScale();
        return (int)Math.max(2L, Math.min(4L, Math.round(ppu)));
    }

    private static void drawSnapped(GuiGraphics ctx, FormattedCharSequence t, float x, float y, int color) {
        Matrix4f m = ctx.pose().last().pose();
        double gui = Minecraft.getInstance().getWindow().getGuiScale();
        Vector3f o = m.transformPosition(x, y, 0.0F, new Vector3f());
        float sx = (float)(Math.round(o.x * gui) / gui);
        float sy = (float)(Math.round(o.y * gui) / gui);
        ctx.pose().pushPose();
        ctx.pose().last().pose().set(new Matrix4f().translation(sx, sy, o.z).scale(m.m00(), m.m11(), 1.0F));
        ctx.drawString(renderer(), t, 0, 0, color, false);
        ctx.pose().popPose();
    }
}
