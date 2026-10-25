package dev.infectedware.skysong.hud.components;

import dev.infectedware.skysong.ModSettings;
import dev.infectedware.skysong.compat.GuiGraphics;
import dev.infectedware.skysong.hud.Draggable;
import dev.infectedware.skysong.hud.InputEvents;
import dev.infectedware.skysong.render.r2d.MsdfFont;
import dev.infectedware.skysong.render.r2d.R2D;
import dev.infectedware.skysong.theme.ClientAccent;
import dev.infectedware.skysong.theme.Colors;
import java.util.Locale;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

public final class KeyStrokesComp extends Draggable {
    private static final float KEY = 22.0F;
    private static final float GAP = 2.5F;
    private static final float MOUSE_H = 16.0F;
    private static final float SPACE_H = 14.5F;
    private static final float WIDTH = 71.0F;
    private static final float SINK = 1.1F;
    private static final int LABEL_IDLE = -4604215;
    private static final int TOP_IDLE = -233038042;
    private static final int BOTTOM_IDLE = -233498851;
    private final Smooth visibility = new Smooth(0.0F);
    private final Smooth[] press = new Smooth[7];
    private boolean lastVisible;
    private final boolean[] lastPressed = new boolean[7];

    public KeyStrokesComp() {
        super("keystrokes", 5.0F, 225.0F);

        for (int i = 0; i < this.press.length; i++) {
            this.press[i] = new Smooth(0.0F);
        }
    }

    @Override
    public float width() {
        return 71.0F;
    }

    @Override
    public float height() {
        float h = 46.5F;
        if (ModSettings.keyStrokesMouse) {
            h += 18.5F;
        }

        if (ModSettings.keyStrokesSpace) {
            h += 17.0F;
        }

        return h;
    }

    @Override
    public boolean interactive() {
        return ModSettings.keyStrokesEnabled;
    }

    @Override
    protected void render(GuiGraphics c) {
        boolean show = ModSettings.keyStrokesEnabled;
        if (show != this.lastVisible) {
            this.visibility.run(show ? 1.0F : 0.0F, show ? 0.18 : 0.12);
            this.lastVisible = show;
        }

        float a = this.visibility.get();
        if (!(a <= 0.01F)) {
            Minecraft mc = Minecraft.getInstance();
            float x = this.x();
            float y = this.y();
            float w = this.width();
            float h = this.height();
            pushScale(c, x + w / 2.0F, y + h / 2.0F, 0.92F + a * 0.08F);
            float row = y;
            this.key(c, 0, x + 22.0F + 2.5F, row, 22.0F, 22.0F, label(mc.options.keyUp), 8.5F, mc.options.keyUp.isDown(), a);
            row += 24.5F;
            this.key(c, 1, x, row, 22.0F, 22.0F, label(mc.options.keyLeft), 8.5F, mc.options.keyLeft.isDown(), a);
            this.key(c, 2, x + 22.0F + 2.5F, row, 22.0F, 22.0F, label(mc.options.keyDown), 8.5F, mc.options.keyDown.isDown(), a);
            this.key(c, 3, x + 49.0F, row, 22.0F, 22.0F, label(mc.options.keyRight), 8.5F, mc.options.keyRight.isDown(), a);
            row += 24.5F;
            if (ModSettings.keyStrokesMouse) {
                float half = (w - 2.5F) / 2.0F;
                this.key(c, 4, x, row, half, 16.0F, mouseLabel(true), 7.0F, mc.options.keyAttack.isDown(), a);
                this.key(c, 5, x + half + 2.5F, row, half, 16.0F, mouseLabel(false), 7.0F, mc.options.keyUse.isDown(), a);
                row += 18.5F;
            }

            if (ModSettings.keyStrokesSpace) {
                this.key(c, 6, x, row, w, 14.5F, null, 0.0F, mc.options.keyJump.isDown(), a);
            }

            c.pose().popPose();
        }
    }

    private static String mouseLabel(boolean left) {
        if (ModSettings.keyStrokesCps) {
            int cps = left ? InputEvents.leftCps() : InputEvents.rightCps();
            if (cps > 0) {
                return cps + " CPS";
            }
        }

        return left ? "LMB" : "RMB";
    }

    private static String label(KeyMapping key) {
        String t = key.saveString();
        if (!t.isBlank()) {
            if (t.startsWith("key.keyboard.")) {
                t = t.substring("key.keyboard.".length());
            } else if (t.startsWith("key.mouse.")) {
                t = "M " + t.substring("key.mouse.".length());
            }

            return t.replace('.', ' ').toUpperCase(Locale.ROOT);
        } else {
            return "?";
        }
    }

    private void key(GuiGraphics c, int i, float x, float y, float w, float h, String text, float size, boolean down, float a) {
        if (down != this.lastPressed[i]) {
            this.press[i].run(down ? 1.0F : 0.0F, down ? 0.055 : 0.28);
            this.lastPressed[i] = down;
        }

        float p = this.press[i].get();
        float sink = 1.1F * p;
        float kx = x + sink;
        float ky = y + sink;
        float kw = w - sink * 2.0F;
        float kh = h - sink * 2.0F;
        float r = Math.min(ModSettings.rectCornerRadius, Math.min(kw, kh) * 0.5F);
        if (ModSettings.keyStrokesGlass) {
            glassKey(c, kx, ky, kw, kh, r, text, size, p, a);
        } else {
            float idle = a * (1.0F - p);
            if (idle > 0.004F) {
                int top = Colors.multAlpha(-233038042, idle);
                int bottom = Colors.multAlpha(-233498851, idle);
                R2D.rect(c, kx, ky, kw, kh, r, top, top, bottom, bottom);
            }

            if (p > 0.004F) {
                R2D.clientRectFixed(c, kx, ky, kw, kh, r, a * p);
            }

            int edge = Colors.rgba(255, 255, 255, 26.0F * a);
            float t = (float)(System.currentTimeMillis() % 24000L) / 24000.0F;
            int e0 = Colors.lerp(edge, ClientAccent.gradientColor(0.0F, 150.0F * a), p);
            int e1 = Colors.lerp(edge, ClientAccent.gradientColor(0.33F, 150.0F * a), p);
            int e2 = Colors.lerp(edge, ClientAccent.gradientColor(1.0F, 150.0F * a), p);
            int e3 = Colors.lerp(edge, ClientAccent.gradientColor(0.66F, 150.0F * a), p);
            R2D.outline(c, kx, ky, kw, kh, r, 0.5F, e0, e1, e2, e3);
            int labelColor = Colors.multAlpha(Colors.lerp(-4604215, -1, p), a);
            if (text == null) {
                float bw = kw * 0.42F;
                float bh = 2.5F;
                R2D.rect(c, kx + (kw - bw) / 2.0F, ky + (kh - bh) / 2.0F, bw, bh, bh / 2.0F, labelColor);
            } else {
                float s = size;

                float tw;
                for (tw = R2D.width(MsdfFont.Font.MEDIUM, text, s); tw > kw - 6.0F && s > 5.5F; tw = R2D.width(MsdfFont.Font.MEDIUM, text, s)) {
                    s -= 0.5F;
                }

                R2D.textCentered(c, MsdfFont.Font.MEDIUM, text, kx + (kw - tw) / 2.0F, ky + kh / 2.0F, s, labelColor);
            }
        }
    }

    private static void glassKey(GuiGraphics c, float kx, float ky, float kw, float kh, float r, String text, float size, float p, float a) {
        int[] pal = glassPalette();
        R2D.glassTinted(c, kx, ky, kw, kh, r, a * (0.82F + 0.18F * p), p > 0.02F, pal);
        if (p > 0.004F) {
            int tint = Colors.multAlpha(pal[0], 0.3F * p * a);
            int tint2 = Colors.multAlpha(pal[pal.length - 1], 0.3F * p * a);
            R2D.rect(c, kx, ky, kw, kh, r, tint, tint2, tint2, tint);
            R2D.rect(c, kx, ky, kw, kh, r, Colors.rgba(255, 255, 255, 26.0F * p * a));
        }

        int sheen = Colors.rgba(255, 255, 255, (20.0F + 16.0F * p) * a);
        int clear = Colors.rgba(255, 255, 255, 0.0F);
        R2D.rect(c, kx + 0.8F, ky + 0.6F, kw - 1.6F, kh * 0.5F, Math.max(0.0F, r - 0.6F), Math.max(0.0F, r - 0.6F), 0.0F, 0.0F, sheen, sheen, clear, clear);
        int edge = Colors.lerp(Colors.rgba(255, 255, 255, 60.0F * a), Colors.multAlpha(Colors.lerp(pal[0], -1, 0.35F), a), p);
        R2D.outline(c, kx, ky, kw, kh, r, 0.6F, edge);
        int labelColor = Colors.rgba(255, 255, 255, (215.0F + 40.0F * p) * a);
        if (text == null) {
            float bw = kw * 0.42F;
            float bh = 2.5F;
            R2D.rect(c, kx + (kw - bw) / 2.0F, ky + (kh - bh) / 2.0F, bw, bh, bh / 2.0F, labelColor);
        } else {
            float s = size;

            float tw;
            for (tw = R2D.width(MsdfFont.Font.SEMIBOLD, text, s); tw > kw - 6.0F && s > 5.5F; tw = R2D.width(MsdfFont.Font.SEMIBOLD, text, s)) {
                s -= 0.5F;
            }

            R2D.textCentered(c, MsdfFont.Font.SEMIBOLD, text, kx + (kw - tw) / 2.0F, ky + kh / 2.0F, s, labelColor);
        }
    }

    private static int[] glassPalette() {
        if (ModSettings.keyStrokesGlassColor == 0) {
            return ClientAccent.palette();
        } else {
            int base = ModSettings.keyStrokesGlassCustom | 0xFF000000;
            if (ModSettings.keyStrokesGlassColor == 2) {
                int second = ModSettings.keyStrokesGlassCustom2 | 0xFF000000;
                return new int[]{base, second, base, second, base, second};
            } else {
                int light = Colors.lerp(base, -1, 0.35F);
                int dark = Colors.lerp(base, -16777216, 0.25F);
                return new int[]{base, light, base, dark, base, light};
            }
        }
    }
}
