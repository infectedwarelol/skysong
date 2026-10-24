package dev.infectedware.skysong.gui.click;

import dev.infectedware.skysong.ModConfig;
import dev.infectedware.skysong.compat.Compat;
import dev.infectedware.skysong.compat.GuiGraphics;
import dev.infectedware.skysong.module.Binds;
import dev.infectedware.skysong.module.Setting;
import dev.infectedware.skysong.render.r2d.R2D;
import dev.infectedware.skysong.theme.ClientAccent;
import dev.infectedware.skysong.theme.Colors;
import dev.infectedware.skysong.ui.Clip;
import java.awt.Color;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;

public abstract class Widget {
    protected final Setting setting;
    public static final float PILL_W = 19.1F;
    public static final float PILL_H = 10.4F;

    protected Widget(Setting setting) {
        this.setting = setting;
    }

    public boolean visible() {
        return Setting.visible(this.setting);
    }

    public abstract float height();

    public float preferredWidth() {
        return 120.0F;
    }

    public abstract void render(GuiGraphics var1, float var2, float var3, float var4, float var5, float var6, float var7);

    public boolean click(float x, float y, float w, float mx, float my) {
        return false;
    }

    public boolean middleClick(float x, float y, float w, float mx, float my) {
        return false;
    }

    public void release() {
    }

    public boolean hasOverlay() {
        return false;
    }

    public boolean overlayOpen() {
        return false;
    }

    public void renderOverlay(GuiGraphics c, float x, float y, float w, float a, float mx, float my) {
    }

    public boolean clickOverlay(float x, float y, float w, float mx, float my) {
        return false;
    }

    public boolean scrollOverlay(float x, float y, float w, float mx, float my, double amount) {
        return false;
    }

    public void closeOverlay() {
    }

    public boolean keyPressed(int key, int mods) {
        return false;
    }

    public boolean charTyped(char chr) {
        return false;
    }

    public boolean focused() {
        return false;
    }

    public boolean mouseBind(int button) {
        return false;
    }

    public static Widget of(Setting s) {
        Setting u = Setting.unwrap(s);
        if (u instanceof Setting.Header h) {
            return new Widget.Separator(s, h);
        } else if (u instanceof Setting.Toggle t) {
            return new Widget.Bool(s, t);
        } else if (u instanceof Setting.Slide sl) {
            return new Widget.Slider(s, sl);
        } else if (u instanceof Setting.Choice ch) {
            return new Widget.Select(s, ch);
        } else if (u instanceof Setting.Multi mu) {
            return new Widget.MultiSelect(s, mu);
        } else if (u instanceof Setting.ColorPick cp) {
            return new Widget.ColorPicker(s, cp);
        } else if (u instanceof Setting.Action ac) {
            return new Widget.Button(s, ac);
        } else if (u instanceof Setting.Info in) {
            return new Widget.InfoLine(s, in);
        } else if (u instanceof Setting.Input ip) {
            return new Widget.InputRow(s, ip);
        } else if (u instanceof Setting.KeyPick kp) {
            return new Widget.KeyRow(s, kp);
        } else if (u instanceof Setting.ListRow lr) {
            return new Widget.ListItem(s, lr);
        } else {
            throw new IllegalStateException("unknown setting " + u);
        }
    }

    public static void drawToggle(GuiGraphics c, float x, float y, float w, float h, float on, float a) {
        float r = h * 0.4615385F;
        if (on < 0.999F) {
            int top = Colors.rgba(96, 99, 105, 165.0F * a * (1.0F - on));
            int bottom = Colors.rgba(78, 81, 87, 165.0F * a * (1.0F - on));
            R2D.rect(c, x + 0.5F, y + 0.5F, w - 1.0F, h - 1.0F, r, top, bottom, bottom, top);
        }

        if (on > 0.001F) {
            R2D.accentFillHorizontal(c, x + 0.5F, y + 0.5F, w - 1.0F, h - 1.0F, r, 175.0F * a * on);
        }

        int edge = Colors.lerp(Colors.rgba(120, 123, 129, 150.0F * a), ClientAccent.accentBright(150.0F * a), on);
        R2D.outline(c, x, y, w, h, r, 0.5F, edge);
        float knob = h * 0.8076923F;
        float inset = h * 0.125F;
        float kx = x + inset + (x + w - knob - inset - (x + inset)) * on;
        float ky = y + (h - knob) * 0.5F;
        int k1 = Colors.rgba(239, 252, 255, 255.0F * a);
        int k2 = Colors.rgba(242, 250, 255, 255.0F * a);
        R2D.rect(c, kx, ky, knob, knob, knob * 0.5F, k1, k1, k2, k1);
        R2D.outline(c, kx, ky, knob, knob, knob * 0.5F, 0.5F, Colors.rgba(255, 255, 255, 210.0F * a));
    }

    public static List<Widget> build(List<Setting> settings) {
        return settings.stream().map(Widget::of).toList();
    }

    public static final class Bool extends Widget {
        private final Setting.Toggle t;
        private final Anim anim;

        Bool(Setting s, Setting.Toggle t) {
            super(s);
            this.t = t;
            this.anim = new Anim(120, t.get().getAsBoolean());
        }

        @Override
        public float height() {
            return 16.0F;
        }

        @Override
        public float preferredWidth() {
            return 6.0F + Ui.width(this.t.label(), 6.5F) + 6.0F + 19.1F + 4.0F;
        }

        @Override
        public void render(GuiGraphics c, float x, float y, float w, float a, float mx, float my) {
            float px = x + w - 19.1F - 4.0F;
            float py = y + 2.8F;
            Ui.name(c, this.t.label(), x, y, px - (x + 6.0F) - 4.0F, a);
            this.anim.forwards(this.t.get().getAsBoolean());
            drawToggle(c, px, py, 19.1F, 10.4F, this.anim.get(), a);
        }

        @Override
        public boolean click(float x, float y, float w, float mx, float my) {
            float px = x + w - 19.1F - 4.0F;
            float py = y + 2.8F;
            if (Ui.inside(mx, my, px - 2.0F, py - 2.0F, 23.1F, 14.4F)) {
                boolean now = !this.t.get().getAsBoolean();
                this.t.set().accept(now);
                Sounds.play(now ? "switch_right" : "switch_left");
                return true;
            } else {
                return false;
            }
        }
    }

    public static final class Button extends Widget {
        private final Setting.Action ac;
        private float hover;

        Button(Setting s, Setting.Action ac) {
            super(s);
            this.ac = ac;
        }

        @Override
        public float height() {
            return 16.0F;
        }

        @Override
        public float preferredWidth() {
            return Ui.width(this.ac.label(), 6.0F) + 48.0F;
        }

        @Override
        public void render(GuiGraphics c, float x, float y, float w, float a, float mx, float my) {
            boolean over = Ui.inside(mx, my, x + 4.0F, y + 1.0F, w - 8.0F, 14.0F);
            this.hover = this.hover + ((over ? 1.0F : 0.0F) - this.hover) * 0.25F;
            Ui.panelBg(c, x + 4.0F, y + 1.0F, w - 8.0F, 14.0F, 3.0F, a * (1.0F + this.hover * 0.6F));
            R2D.outline(
                c, x + 4.0F, y + 1.0F, w - 8.0F, 14.0F, Ui.cornerRadius(3.0F, w, 14.0F), 0.5F, ClientAccent.accentSoft((30.0F + 50.0F * this.hover) * a)
            );
            float tw = Ui.width(this.ac.label(), 6.0F);
            Ui.text(c, this.ac.label(), x + (w - tw) / 2.0F, y + 1.0F + 3.5F, 6.0F, ClientAccent.accentSoft(225.0F * a));
        }

        @Override
        public boolean click(float x, float y, float w, float mx, float my) {
            if (Ui.inside(mx, my, x + 4.0F, y + 1.0F, w - 8.0F, 14.0F)) {
                Sounds.play("button");
                this.ac.run().run();
                return true;
            } else {
                return false;
            }
        }
    }

    public static final class ColorPicker extends Widget {
        private static final float SV_W = 70.0F;
        private static final float SV_H = 50.0F;
        private static final float BAR_H = 4.0F;
        private static final float GAP = 4.0F;
        private static final float POP_W = 82.0F;
        private static final float POP_H = 78.0F;
        private final Setting.ColorPick cp;
        private final Anim anim = new Anim(220);
        private boolean open;
        private int drag;
        private float hue;
        private float sat;
        private float val;
        private float alpha = 1.0F;
        private float dHue;
        private float dSat;
        private float dVal;
        private float dAlpha;
        private float velX;
        private float velY;
        private long lastNs = System.nanoTime();

        ColorPicker(Setting s, Setting.ColorPick cp) {
            super(s);
            this.cp = cp;
        }

        private String hex() {
            return String.format("#%06X", this.cp.get().getAsInt() & 16777215);
        }

        @Override
        public float height() {
            return 16.0F;
        }

        @Override
        public float preferredWidth() {
            return 6.0F + Ui.width(this.cp.label(), 6.5F) + 6.0F + 8.0F + 4.0F + Ui.width(this.hex(), 5.5F) + 10.0F + 8.0F;
        }

        private float buttonW() {
            return 12.0F + Ui.width(this.hex(), 5.5F) + 10.0F;
        }

        @Override
        public void render(GuiGraphics c, float x, float y, float w, float a, float mx, float my) {
            int color = this.cp.get().getAsInt();
            float bw = this.buttonW();
            float bx = x + w - bw - 4.0F;
            float by = y + 2.0F;
            Ui.name(c, this.cp.label(), x, y, bx - (x + 6.0F) - 4.0F, a);
            Ui.panelBg(c, bx, by, bw, 12.0F, 3.0F, a);
            int swatch = color & 16777215 | Math.round((color >>> 24) * a) << 24;
            R2D.rect(c, bx + 3.0F, by + 2.0F, 8.0F, 8.0F, 2.0F, swatch);
            Ui.text(c, this.hex(), bx + 3.0F + 8.0F + 4.0F, by + 3.0F, 5.5F, ClientAccent.accentSoft(200.0F * a));
        }

        @Override
        public boolean click(float x, float y, float w, float mx, float my) {
            float bw = this.buttonW();
            float bx = x + w - bw - 4.0F;
            if (Ui.inside(mx, my, bx, y + 2.0F, bw, 12.0F)) {
                if (this.open) {
                    this.closeOverlay();
                } else {
                    this.openPicker();
                }

                return true;
            } else {
                return false;
            }
        }

        private void openPicker() {
            this.open = true;
            int col = this.cp.get().getAsInt();
            float[] hsb = Color.RGBtoHSB(col >> 16 & 0xFF, col >> 8 & 0xFF, col & 0xFF, null);
            this.hue = this.dHue = hsb[0];
            this.sat = this.dSat = hsb[1];
            this.val = this.dVal = hsb[2];
            this.alpha = this.dAlpha = (col >>> 24) / 255.0F;
            this.velX = this.velY = 0.0F;
            this.lastNs = System.nanoTime();
            this.anim.forwards(true);
            Sounds.play("settings_open");
        }

        private void commit() {
            int rgb = Color.HSBtoRGB(this.hue, this.sat, this.val) & 16777215;
            this.cp.set().accept(Math.round(this.alpha * 255.0F) << 24 | rgb);
        }

        @Override
        public boolean hasOverlay() {
            return this.anim.linear() > 0.01F;
        }

        @Override
        public boolean overlayOpen() {
            return this.open;
        }

        @Override
        public void renderOverlay(GuiGraphics c, float x, float y, float w, float a, float mx, float my) {
            float p = this.anim.get();
            if (!(p <= 0.01F)) {
                if (this.drag != 0) {
                    this.updateDrag(mx, my, x, y, w);
                }

                float fa = p * a;
                long now = System.nanoTime();
                float dt = Math.min(0.05F, (float)(now - this.lastNs) / 1.0E9F);
                this.lastNs = now;
                float k = 1.0F - (float)Math.exp(-dt * 16.0F);
                this.dHue = this.dHue + (this.hue - this.dHue) * k;
                this.dAlpha = this.dAlpha + (this.alpha - this.dAlpha) * k;
                this.velX = this.velX + ((this.sat - this.dSat) * 200.0F - this.velX * 22.0F) * dt;
                this.velY = this.velY + ((this.val - this.dVal) * 200.0F - this.velY * 22.0F) * dt;
                this.dSat = this.dSat + this.velX * dt;
                this.dVal = this.dVal + this.velY * dt;
                float px = x + w - 82.0F - 4.0F;
                float py = y + 17.0F;
                Ui.dropBackground(c, px, py, 82.0F, 78.0F, fa);
                float sx = px + 6.0F;
                float sy = py + 6.0F;
                int hueCol = Color.HSBtoRGB(this.hue, 1.0F, 1.0F) & 16777215;
                int opaque = Math.round(255.0F * fa) << 24;
                R2D.rect(c, sx, sy, 70.0F, 50.0F, 2.0F, opaque | 16777215, opaque | hueCol, opaque | hueCol, opaque | 16777215);
                R2D.rect(c, sx, sy, 70.0F, 50.0F, 2.0F, 0, 0, opaque, opaque);
                float cx = sx + 70.0F * Anim.clamp01(this.dSat);
                float cy = sy + 50.0F * (1.0F - Anim.clamp01(this.dVal));
                int ring = Math.round(220.0F * fa) << 24 | 16777215;
                R2D.outline(c, cx - 3.0F, cy - 3.0F, 5.0F, 5.0F, 3.0F, 1.0F, ring);
                float hy = sy + 50.0F + 4.0F;

                for (int i = 0; i < 6; i++) {
                    int c0 = Color.HSBtoRGB(i / 6.0F, 1.0F, 1.0F) & 16777215;
                    int c1 = Color.HSBtoRGB((i + 1) / 6.0F, 1.0F, 1.0F) & 16777215;
                    float segW = 11.666667F;
                    if (i != 0 && i != 5) {
                        float r = 0.0F;
                    } else {
                        float r = 2.0F;
                    }

                    R2D.rect(
                        c,
                        sx + i * segW - (i > 0 ? 0.3F : 0.0F),
                        hy,
                        segW + (i > 0 ? 0.3F : 0.0F),
                        4.0F,
                        i == 0 ? 2.0F : 0.0F,
                        i == 5 ? 2.0F : 0.0F,
                        i == 5 ? 2.0F : 0.0F,
                        i == 0 ? 2.0F : 0.0F,
                        opaque | c0,
                        opaque | c1,
                        opaque | c1,
                        opaque | c0
                    );
                }

                R2D.rect(c, sx + 70.0F * this.dHue - 1.0F, hy - 1.0F, 2.0F, 6.0F, 1.0F, ring);
                float ay = hy + 4.0F + 4.0F;
                int rgb = Color.HSBtoRGB(this.hue, this.sat, this.val) & 16777215;
                R2D.rect(c, sx, ay, 70.0F, 4.0F, 2.0F, Math.round(60.0F * fa) << 24 | 8421504);
                R2D.rect(c, sx, ay, 70.0F, 4.0F, 2.0F, rgb, opaque | rgb, opaque | rgb, rgb);
                R2D.rect(c, sx + 70.0F * this.dAlpha - 1.0F, ay - 1.0F, 2.0F, 6.0F, 1.0F, ring);
            }
        }

        @Override
        public boolean clickOverlay(float x, float y, float w, float mx, float my) {
            if (this.open && !(this.anim.linear() <= 0.1F)) {
                float px = x + w - 82.0F - 4.0F;
                float py = y + 17.0F;
                if (!Ui.inside(mx, my, px, py, 82.0F, 78.0F)) {
                    return false;
                }

                float sx = px + 6.0F;
                float sy = py + 6.0F;
                float hy = sy + 50.0F + 4.0F;
                float ay = hy + 4.0F + 4.0F;
                if (Ui.inside(mx, my, sx, sy, 70.0F, 50.0F)) {
                    this.drag = 1;
                } else if (Ui.inside(mx, my, sx, hy - 2.0F, 70.0F, 8.0F)) {
                    this.drag = 2;
                } else if (Ui.inside(mx, my, sx, ay - 2.0F, 70.0F, 8.0F)) {
                    this.drag = 3;
                }

                if (this.drag != 0) {
                    this.updateDrag(mx, my, x, y, w);
                }

                return true;
            } else {
                return false;
            }
        }

        private void updateDrag(float mx, float my, float x, float y, float w) {
            float px = x + w - 82.0F - 4.0F;
            float py = y + 17.0F;
            float sx = px + 6.0F;
            float sy = py + 6.0F;
            if (this.drag == 1) {
                this.sat = Anim.clamp01((mx - sx) / 70.0F);
                this.val = Anim.clamp01(1.0F - (my - sy) / 50.0F);
            } else if (this.drag == 2) {
                this.hue = Anim.clamp01((mx - sx) / 70.0F);
            } else if (this.drag == 3) {
                this.alpha = Anim.clamp01((mx - sx) / 70.0F);
            }

            this.commit();
        }

        @Override
        public void release() {
            this.drag = 0;
        }

        @Override
        public void closeOverlay() {
            if (this.open) {
                this.open = false;
                this.drag = 0;
                this.anim.forwards(false);
            }
        }
    }

    public static final class InfoLine extends Widget {
        private final Setting.Info in;

        InfoLine(Setting s, Setting.Info in) {
            super(s);
            this.in = in;
        }

        @Override
        public float height() {
            return 9.0F;
        }

        @Override
        public float preferredWidth() {
            return Ui.width(this.in.text().get(), 5.5F) + 14.0F;
        }

        @Override
        public void render(GuiGraphics c, float x, float y, float w, float a, float mx, float my) {
            Ui.scrollingText(c, this.in.text().get(), x + 6.0F, y + 1.5F, w - 12.0F, 5.5F, Ui.white(150.0F * a));
        }
    }

    public static final class InputRow extends Widget {
        private final Setting.Input ip;
        private String text = "";
        private boolean focus;

        InputRow(Setting s, Setting.Input ip) {
            super(s);
            this.ip = ip;
        }

        @Override
        public float height() {
            return 31.0F;
        }

        @Override
        public float preferredWidth() {
            return 150.0F;
        }

        @Override
        public void render(GuiGraphics c, float x, float y, float w, float a, float mx, float my) {
            float fx = x + 4.0F;
            float fw = w - 8.0F;
            float fy = y + 1.0F;
            float fh = 13.0F;
            R2D.rect(c, fx, fy, fw, fh, Ui.cornerRadius(3.0F, fw, fh), Colors.rgba(0, 0, 0, 60.0F * a));
            if (this.focus) {
                R2D.outline(c, fx, fy, fw, fh, Ui.cornerRadius(3.0F, fw, fh), 0.6F, ClientAccent.accentBright(190.0F * a));
            }

            boolean empty = this.text.isEmpty();
            String shown = empty ? this.ip.placeholder() : this.text;
            if (!empty && shown.length() > 160) {
                shown = shown.substring(shown.length() - 160);
            }

            while (shown.length() > 1 && Ui.width(shown, 6.0F) > fw - 10.0F) {
                shown = empty ? shown.substring(0, shown.length() - 1) : shown.substring(1);
            }

            Ui.text(c, shown, fx + 5.0F, fy + 2.8F, 6.0F, empty ? Ui.white(110.0F * a) : ClientAccent.accentSoft(220.0F * a));
            if (this.focus && System.currentTimeMillis() / 500L % 2L == 0L) {
                float cx = fx + 5.0F + (empty ? 0.0F : Ui.width(shown, 6.0F)) + 0.5F;
                R2D.rect(c, cx, fy + 2.5F, 0.8F, fh - 5.0F, 0.0F, ClientAccent.accentBright(220.0F * a));
            }

            Ui.button(c, x + 4.0F, y + 16.0F, w - 8.0F, 13.0F, this.ip.button(), a);
        }

        @Override
        public boolean click(float x, float y, float w, float mx, float my) {
            if (Ui.inside(mx, my, x + 4.0F, y + 1.0F, w - 8.0F, 13.0F)) {
                this.focus = true;
                return true;
            } else {
                this.focus = false;
                if (Ui.inside(mx, my, x + 4.0F, y + 16.0F, w - 8.0F, 13.0F)) {
                    Sounds.play("button");
                    this.ip.submit().accept(this.text.trim());
                    this.text = "";
                    return true;
                } else {
                    return false;
                }
            }
        }

        @Override
        public boolean focused() {
            return this.focus;
        }

        @Override
        public boolean keyPressed(int key, int mods) {
            if (!this.focus) {
                return false;
            }

            switch (key) {
                case 86:
                    if (Compat.controlDown()) {
                        String clip = Minecraft.getInstance().keyboardHandler.getClipboard();
                        if (clip != null) {
                            this.text = this.text + clip.replace("\n", "").trim();
                        }

                        if (this.text.length() > 65536) {
                            this.text = this.text.substring(0, 65536);
                        }
                    }
                    break;
                case 256:
                    this.focus = false;
                    break;
                case 257:
                case 335:
                    this.ip.submit().accept(this.text.trim());
                    this.text = "";
                    this.focus = false;
                    break;
                case 259:
                    if (!this.text.isEmpty()) {
                        this.text = this.text.substring(0, this.text.length() - 1);
                    }
            }

            return true;
        }

        @Override
        public boolean charTyped(char chr) {
            if (this.focus && !Character.isISOControl(chr)) {
                if (this.text.length() < 512) {
                    this.text = this.text + chr;
                }

                return true;
            } else {
                return false;
            }
        }
    }

    public static final class KeyRow extends Widget {
        private final Setting.KeyPick kp;
        private boolean listening;
        private float hover;

        KeyRow(Setting s, Setting.KeyPick kp) {
            super(s);
            this.kp = kp;
        }

        @Override
        public float height() {
            return 16.0F;
        }

        @Override
        public float preferredWidth() {
            return 6.0F + Ui.width(this.kp.label(), 6.5F) + 12.0F + 40.0F;
        }

        private String label() {
            return this.listening ? "..." : Binds.shortName(this.kp.get().getAsInt());
        }

        private float pillW() {
            return Math.max(34.0F, Ui.width(this.label(), 6.0F) + 12.0F);
        }

        @Override
        public void render(GuiGraphics c, float x, float y, float w, float a, float mx, float my) {
            float kw = this.pillW();
            float kx = x + w - kw - 4.0F;
            float ky = y + 1.5F;
            float kh = 13.0F;
            Ui.name(c, this.kp.label(), x, y, kx - (x + 6.0F) - 4.0F, a);
            boolean over = Ui.inside(mx, my, kx, ky, kw, kh);
            this.hover = this.hover + ((!over && !this.listening ? 0.0F : 1.0F) - this.hover) * 0.25F;
            Ui.panelBg(c, kx, ky, kw, kh, 3.0F, a * (1.0F + this.hover * 0.6F));
            int edge = this.listening ? ClientAccent.accentBright(200.0F * a) : ClientAccent.accentSoft((30.0F + 50.0F * this.hover) * a);
            R2D.outline(c, kx, ky, kw, kh, Ui.cornerRadius(3.0F, kw, kh), this.listening ? 0.7F : 0.5F, edge);
            String s = this.label();
            Ui.text(c, s, kx + (kw - Ui.width(s, 6.0F)) / 2.0F, ky + (kh - 7.0F) / 2.0F, 6.0F, ClientAccent.accentSoft(225.0F * a));
        }

        @Override
        public boolean click(float x, float y, float w, float mx, float my) {
            float kw = this.pillW();
            float kx = x + w - kw - 4.0F;
            if (Ui.inside(mx, my, kx, y + 1.5F, kw, 13.0F)) {
                this.listening = !this.listening;
                Sounds.play("button");
                return true;
            } else {
                this.listening = false;
                return false;
            }
        }

        @Override
        public boolean focused() {
            return this.listening;
        }

        @Override
        public boolean keyPressed(int key, int mods) {
            if (!this.listening) {
                return false;
            }

            if (key != 256) {
                this.kp.set().accept(key);
            }

            this.listening = false;
            ModConfig.save();
            return true;
        }

        @Override
        public boolean mouseBind(int button) {
            if (!this.listening) {
                return false;
            }

            this.kp.set().accept(-100 - button);
            this.listening = false;
            ModConfig.save();
            return true;
        }

        @Override
        public void closeOverlay() {
            this.listening = false;
        }
    }

    public static final class ListItem extends Widget {
        private final Setting.ListRow row;
        private float hover;
        private float xHover;
        private long armedAt = -1L;

        ListItem(Setting s, Setting.ListRow row) {
            super(s);
            this.row = row;
        }

        @Override
        public float height() {
            return 16.0F;
        }

        @Override
        public float preferredWidth() {
            return Ui.width(this.row.label(), 6.0F) + 60.0F;
        }

        private boolean armed() {
            return this.armedAt >= 0L && System.currentTimeMillis() - this.armedAt < 2500L;
        }

        @Override
        public void render(GuiGraphics c, float x, float y, float w, float a, float mx, float my) {
            float bx = x + 4.0F;
            float by = y + 1.0F;
            float bw = w - 8.0F;
            float bh = 14.0F;
            float xs = 14.0F;
            float xx = bx + bw - xs;
            boolean overX = Ui.inside(mx, my, xx, by, xs, bh);
            boolean over = !overX && Ui.inside(mx, my, bx, by, bw - xs, bh);
            this.hover = this.hover + ((over ? 1.0F : 0.0F) - this.hover) * 0.25F;
            this.xHover = this.xHover + ((overX ? 1.0F : 0.0F) - this.xHover) * 0.25F;
            boolean on = this.row.active().getAsBoolean();
            Ui.panelBg(c, bx, by, bw, bh, 3.0F, a * (1.0F + this.hover * 0.6F));
            R2D.outline(
                c,
                bx,
                by,
                bw,
                bh,
                Ui.cornerRadius(3.0F, bw, bh),
                on ? 0.8F : 0.5F,
                on ? ClientAccent.accentBright(200.0F * a) : ClientAccent.accentSoft((30.0F + 50.0F * this.hover) * a)
            );
            if (on) {
                R2D.circle(c, bx + 7.0F, by + bh / 2.0F, 1.6F, ClientAccent.accentBright(230.0F * a));
            }

            boolean armed = this.armed();
            String label = armed ? "Click × again to delete" : this.row.label();
            Ui.scrollingText(
                c,
                label,
                bx + 12.0F,
                by + (bh - 7.0F) / 2.0F,
                bw - xs - 16.0F,
                6.0F,
                armed ? Colors.rgba(255, 120, 120, 230.0F * a) : (on ? ClientAccent.accentBright(235.0F * a) : ClientAccent.accentSoft(215.0F * a))
            );
            int xCol = armed ? Colors.rgba(255, 90, 90, 240.0F * a) : Ui.white((110.0F + 120.0F * this.xHover) * a);
            float cx = xx + xs / 2.0F;
            float cy = by + bh / 2.0F;
            Ui.text(c, "×", cx - Ui.width("×", 7.0F) / 2.0F, cy - 3.8F, 7.0F, xCol);
        }

        @Override
        public boolean click(float x, float y, float w, float mx, float my) {
            float bx = x + 4.0F;
            float by = y + 1.0F;
            float bw = w - 8.0F;
            float bh = 14.0F;
            float xs = 14.0F;
            if (Ui.inside(mx, my, bx + bw - xs, by, xs, bh)) {
                Sounds.play("button");
                if (this.armed()) {
                    this.armedAt = -1L;
                    this.row.remove().run();
                } else {
                    this.armedAt = System.currentTimeMillis();
                }

                return true;
            } else if (Ui.inside(mx, my, bx, by, bw - xs, bh)) {
                Sounds.play("button");
                this.armedAt = -1L;
                this.row.click().run();
                return true;
            } else {
                return false;
            }
        }
    }

    public static final class MultiSelect extends Widget {
        private final Setting.Multi mu;
        private boolean open;
        private final Anim drop = new Anim(200);

        MultiSelect(Setting s, Setting.Multi mu) {
            super(s);
            this.mu = mu;
        }

        private String label() {
            return Integer.bitCount(this.mu.mask().getAsInt() & (1 << this.mu.options().length) - 1) + " of " + this.mu.options().length;
        }

        @Override
        public float height() {
            return 16.0F;
        }

        @Override
        public float preferredWidth() {
            return 6.0F + Ui.width(this.mu.label(), 6.5F) + 6.0F + Ui.width(this.label(), 6.0F) + 10.0F + 8.0F;
        }

        @Override
        public void render(GuiGraphics c, float x, float y, float w, float a, float mx, float my) {
            float bw = Ui.width(this.label(), 6.0F) + 10.0F;
            float bx = x + w - bw - 4.0F;
            Ui.name(c, this.mu.label(), x, y, bx - (x + 6.0F) - 4.0F, a);
            Ui.button(c, bx, y + 2.0F, bw, 12.0F, this.label(), a);
        }

        private float dropW() {
            float max = 0.0F;

            for (String o : this.mu.options()) {
                max = Math.max(max, Ui.width(o, 5.5F));
            }

            return max + 20.0F;
        }

        private float dropX(float x, float w) {
            float bw = Ui.width(this.label(), 6.0F) + 10.0F;
            return x + w - bw - 4.0F + bw - this.dropW();
        }

        @Override
        public boolean click(float x, float y, float w, float mx, float my) {
            float bw = Ui.width(this.label(), 6.0F) + 10.0F;
            float bx = x + w - bw - 4.0F;
            if (Ui.inside(mx, my, bx, y + 2.0F, bw, 12.0F)) {
                this.open = !this.open;
                Sounds.play(this.open ? "settings_open" : "settings_close");
                this.drop.forwards(this.open);
                return true;
            } else {
                return false;
            }
        }

        @Override
        public boolean hasOverlay() {
            return this.drop.linear() > 0.01F;
        }

        @Override
        public boolean overlayOpen() {
            return this.open;
        }

        @Override
        public void renderOverlay(GuiGraphics c, float x, float y, float w, float a, float mx, float my) {
            float p = this.drop.get();
            if (!(p <= 0.01F)) {
                float fa = a * p;
                String[] opts = this.mu.options();
                float dw = this.dropW();
                float dx = this.dropX(x, w);
                float dy = y + 17.0F;
                float dh = opts.length * 12.0F + 6.0F;
                Ui.dropBackground(c, dx, dy, dw, dh, fa);
                int mask = this.mu.mask().getAsInt();

                for (int i = 0; i < opts.length; i++) {
                    float iy = dy + 3.0F + i * 12.0F;
                    boolean on = (mask >> i & 1) != 0;
                    Ui.text(c, opts[i], dx + 6.0F, iy + 2.5F, 5.5F, on ? ClientAccent.accentSoft(230.0F * fa) : Ui.white(140.0F * fa));
                    if (on) {
                        R2D.accentFillVertical(c, dx + dw - 8.0F, iy + 5.0F, 2.0F, 2.0F, 1.0F, 200.0F * fa);
                    }
                }
            }
        }

        @Override
        public boolean clickOverlay(float x, float y, float w, float mx, float my) {
            if (this.open && !(this.drop.linear() <= 0.1F)) {
                String[] opts = this.mu.options();
                float dw = this.dropW();
                float dx = this.dropX(x, w);
                float dy = y + 17.0F;
                float dh = opts.length * 12.0F + 6.0F;
                if (!Ui.inside(mx, my, dx, dy, dw, dh)) {
                    return false;
                }

                int i = (int)Math.floor((my - dy - 3.0F) / 12.0F);
                if (i >= 0 && i < opts.length) {
                    this.mu.set().accept(this.mu.mask().getAsInt() ^ 1 << i);
                    Sounds.play("button");
                }

                return true;
            } else {
                return false;
            }
        }

        @Override
        public void closeOverlay() {
            if (this.open) {
                this.open = false;
                Sounds.play("settings_close");
                this.drop.forwards(false);
            }
        }
    }

    public static final class Select extends Widget {
        private static final int MAX_VISIBLE = 7;
        private static final float ITEM_H = 12.0F;
        private static final float PAD = 3.0F;
        private final Setting.Choice ch;
        private boolean open;
        private final Anim drop = new Anim(200);
        private float scroll;
        private float scrollTarget;
        private long lastNs = System.nanoTime();

        Select(Setting s, Setting.Choice ch) {
            super(s);
            this.ch = ch;
        }

        private String selected() {
            int i = this.ch.get().getAsInt();
            return i >= 0 && i < this.ch.options().length ? this.ch.options()[i] : "";
        }

        @Override
        public float height() {
            return 16.0F;
        }

        @Override
        public float preferredWidth() {
            return 6.0F + Ui.width(this.ch.label(), 6.5F) + 6.0F + Ui.width(this.selected(), 6.0F) + 10.0F + 8.0F;
        }

        private float buttonW() {
            return Ui.width(this.selected(), 6.0F) + 10.0F;
        }

        @Override
        public void render(GuiGraphics c, float x, float y, float w, float a, float mx, float my) {
            float bw = this.buttonW();
            float bx = x + w - bw - 4.0F;
            Ui.name(c, this.ch.label(), x, y, bx - (x + 6.0F) - 4.0F, a);
            Ui.button(c, bx, y + 2.0F, bw, 12.0F, this.selected(), a);
        }

        private float dropW() {
            float max = 0.0F;

            for (String o : this.ch.options()) {
                max = Math.max(max, Ui.width(o, 5.5F));
            }

            return max + 16.0F + (this.ch.options().length > 7 ? 5.0F : 0.0F);
        }

        private float maxScroll() {
            return Math.max(0, this.ch.options().length - 7) * 12.0F;
        }

        @Override
        public boolean click(float x, float y, float w, float mx, float my) {
            float bw = this.buttonW();
            float bx = x + w - bw - 4.0F;
            if (Ui.inside(mx, my, bx, y + 2.0F, bw, 12.0F)) {
                this.open = !this.open;
                Sounds.play(this.open ? "settings_open" : "settings_close");
                this.drop.forwards(this.open);
                if (this.open) {
                    int idx = Math.max(0, this.ch.get().getAsInt());
                    this.scrollTarget = this.scroll = Math.max(0, Math.min(idx - 3, this.ch.options().length - 7)) * 12.0F;
                }

                return true;
            } else {
                return false;
            }
        }

        @Override
        public boolean hasOverlay() {
            return this.drop.linear() > 0.01F;
        }

        @Override
        public boolean overlayOpen() {
            return this.open;
        }

        @Override
        public void renderOverlay(GuiGraphics c, float x, float y, float w, float a, float mx, float my) {
            float p = this.drop.get();
            if (!(p <= 0.01F)) {
                long now = System.nanoTime();
                float dt = Math.min(0.05F, (float)(now - this.lastNs) / 1.0E9F);
                this.lastNs = now;
                this.scrollTarget = Math.max(0.0F, Math.min(this.maxScroll(), this.scrollTarget));
                this.scroll = Anim.follow(this.scroll, this.scrollTarget, dt, 18.0F);
                float fa = a * p;
                String[] opts = this.ch.options();
                float dw = this.dropW();
                float dx = x + w - dw - 4.0F;
                float dy = y + 17.0F;
                int rows = Math.min(opts.length, 7);
                float dh = rows * 12.0F + 6.0F;
                boolean scrolling = opts.length > 7;
                Ui.dropBackground(c, dx, dy, dw, dh, fa);
                float top = dy + 1.0F;
                float bottom = dy + dh - 1.0F;
                Clip.push(c, dx, top, dx + dw, bottom);
                String sel = this.selected();

                for (int i = 0; i < opts.length; i++) {
                    float iy = dy + 3.0F + i * 12.0F - this.scroll;
                    if (!(iy + 12.0F < top) && !(iy > bottom)) {
                        float edge = scrolling ? Math.min(edgeAlpha(iy + 2.0F, top, bottom), edgeAlpha(iy + 12.0F - 2.0F, top, bottom)) : 1.0F;
                        if (!(edge <= 0.01F)) {
                            boolean isSel = opts[i].equals(sel);
                            int col = isSel ? ClientAccent.accentSoft(230.0F * fa * edge) : Ui.white(140.0F * fa * edge);
                            Ui.text(c, opts[i], dx + 6.0F, iy + 2.5F, 5.5F, col);
                            if (isSel) {
                                R2D.accentFillVertical(c, dx + dw - (scrolling ? 8.0F : 6.0F), iy + 5.0F, 2.0F, 2.0F, 1.0F, 200.0F * fa * edge);
                            }
                        }
                    }
                }

                Clip.pop(c);
                if (scrolling) {
                    float sx = dx + dw - 3.0F;
                    float sy = dy + 2.0F;
                    float sh = dh - 4.0F;
                    R2D.rect(c, sx, sy, 1.6F, sh, 0.8F, Ui.white(28.0F * fa));
                    float th = Math.max(10.0F, sh * 7.0F / opts.length);
                    float ty = sy + (this.maxScroll() <= 0.0F ? 0.0F : this.scroll / this.maxScroll() * (sh - th));
                    R2D.rect(c, sx, ty, 1.6F, th, 0.8F, ClientAccent.accentSoft(190.0F * fa));
                }
            }
        }

        private static float edgeAlpha(float v, float top, float bottom) {
            return Math.min(Anim.clamp01((v - top) / 4.0F), Anim.clamp01((bottom - v) / 4.0F));
        }

        @Override
        public boolean clickOverlay(float x, float y, float w, float mx, float my) {
            if (this.open && !(this.drop.linear() <= 0.1F)) {
                String[] opts = this.ch.options();
                float dw = this.dropW();
                float dx = x + w - dw - 4.0F;
                float dy = y + 17.0F;
                float dh = Math.min(opts.length, 7) * 12.0F + 6.0F;
                if (!Ui.inside(mx, my, dx, dy + 3.0F, dw, dh - 6.0F)) {
                    return false;
                }

                int i = (int)Math.floor((my - (dy + 3.0F) + this.scroll) / 12.0F);
                if (i >= 0 && i < opts.length) {
                    this.ch.set().accept(i);
                    Sounds.play("button");
                    this.closeOverlay();
                }

                return true;
            } else {
                return false;
            }
        }

        @Override
        public boolean scrollOverlay(float x, float y, float w, float mx, float my, double amount) {
            if (!this.open) {
                return false;
            }

            float dw = this.dropW();
            float dx = x + w - dw - 4.0F;
            float dy = y + 17.0F;
            float dh = Math.min(this.ch.options().length, 7) * 12.0F + 6.0F;
            if (!Ui.inside(mx, my, dx, dy, dw, dh)) {
                return false;
            }

            this.scrollTarget = Math.max(0.0F, Math.min(this.maxScroll(), this.scrollTarget - (float)amount * 12.0F));
            return true;
        }

        @Override
        public void closeOverlay() {
            if (this.open) {
                this.open = false;
                Sounds.play("settings_close");
                this.drop.forwards(false);
            }
        }
    }

    public static final class Separator extends Widget {
        private final Setting.Header h;

        Separator(Setting s, Setting.Header h) {
            super(s);
            this.h = h;
        }

        @Override
        public float height() {
            return 18.0F;
        }

        @Override
        public float preferredWidth() {
            return Ui.width(this.h.title(), 6.0F) + 48.0F;
        }

        @Override
        public void render(GuiGraphics c, float x, float y, float w, float a, float mx, float my) {
            String s = this.h.title();
            float tw = Math.min(Ui.width(s, 6.0F), Math.max(8.0F, w - 24.0F));
            float tx = x + w / 2.0F - tw / 2.0F;
            float ly = y + 9.0F;
            int text = ClientAccent.accentSoft(210.0F * a);
            int line = ClientAccent.accentSoft(55.0F * a);
            float left = x + 4.0F;
            float lw = Math.max(0.0F, tx - 6.0F - left);
            float right = tx + tw + 6.0F;
            float rw = Math.max(0.0F, x + w - 4.0F - right);
            if (lw > 1.0F) {
                R2D.rect(c, left, ly, lw, 1.0F, 0.5F, line);
            }

            if (rw > 1.0F) {
                R2D.rect(c, right, ly, rw, 1.0F, 0.5F, line);
            }

            Ui.text(c, s, tx, ly - 3.0F - 1.3F, 6.0F, text);
        }
    }

    public static final class Slider extends Widget {
        private final Setting.Slide s;
        private boolean dragging;
        private String lastSoundText;
        private float shown = -1.0F;
        private long lastNs = System.nanoTime();

        Slider(Setting setting, Setting.Slide s) {
            super(setting);
            this.s = s;
        }

        @Override
        public float height() {
            return 22.0F;
        }

        @Override
        public float preferredWidth() {
            float valueW = Ui.width(String.format(Locale.ROOT, "%.2f", (double)this.s.max()), 6.0F);
            return Math.max(100.0F, 6.0F + Ui.width(this.s.label(), 6.5F) + 6.0F + valueW + 6.0F);
        }

        @Override
        public void render(GuiGraphics c, float x, float y, float w, float a, float mx, float my) {
            long now = System.nanoTime();
            float dt = Math.min(0.1F, (float)(now - this.lastNs) / 1.0E9F);
            this.lastNs = now;
            float tx = x + 6.0F;
            float tw = w - 12.0F;
            float ty = y + 16.0F;
            if (this.dragging) {
                this.s.setFraction((mx - tx) / tw);
                String text = this.s.text();
                if (!text.equals(this.lastSoundText)) {
                    this.lastSoundText = text;
                    Sounds.play("slider", 0.6F);
                }
            }

            String value = this.s.text();
            float vw = Ui.width(value, 6.0F);
            float vx = x + w - 6.0F - vw;
            Ui.text(c, value, vx, y + 5.25F, 6.0F, Ui.white(238.0F * a));
            Ui.name(c, this.s.label(), x, y, vx - (x + 6.0F) - 6.0F, a);
            float target = this.s.fraction();
            if (this.shown < 0.0F) {
                this.shown = target;
            }

            this.shown = Anim.follow(this.shown, target, dt, 18.0F);
            if (Math.abs(target - this.shown) < 0.0015F) {
                this.shown = target;
            }

            float fillEnd = tx + tw * Anim.clamp01(this.shown);
            R2D.rect(c, tx, ty, tw, 3.0F, 1.5F, Colors.rgba(16, 16, 16, 64.0F * a));
            if (fillEnd - tx > 0.6F) {
                int ga = ClientAccent.gradientA(215.0F * a);
                int gb = ClientAccent.gradientB(215.0F * a);
                R2D.rect(c, tx, ty, fillEnd - tx, 3.0F, 1.5F, ga, gb, gb, ga);
                R2D.outline(c, tx, ty, fillEnd - tx, 3.0F, 1.5F, 0.5F, ClientAccent.accentBright(150.0F * a));
            }

            float kw = 6.5F;
            float kh = 4.0F;
            float kx = Math.max(tx, Math.min(tx + tw - kw, fillEnd - kw * 0.5F));
            float ky = ty + 1.5F - kh * 0.5F;
            R2D.rect(c, kx, ky, kw, kh, 2.0F, Colors.rgba(255, 255, 255, 245.0F * a));
            R2D.outline(c, kx - 0.5F, ky - 0.5F, kw + 1.0F, kh + 1.0F, 2.0F, 0.5F, Colors.rgba(16, 16, 16, 128.0F * a));
        }

        @Override
        public boolean click(float x, float y, float w, float mx, float my) {
            float tx = x + 6.0F;
            float tw = w - 12.0F;
            float ty = y + 16.0F;
            if (Ui.inside(mx, my, tx - 4.0F, ty - 5.0F, tw + 8.0F, 13.0F)) {
                this.dragging = true;
                this.s.setFraction((mx - tx) / tw);
                this.lastSoundText = this.s.text();
                return true;
            } else {
                return false;
            }
        }

        @Override
        public boolean middleClick(float x, float y, float w, float mx, float my) {
            float tx = x + 6.0F;
            float tw = w - 12.0F;
            float ty = y + 16.0F;
            if (!Double.isNaN(this.s.resetTo()) && Ui.inside(mx, my, tx - 4.0F, ty - 8.0F, tw + 8.0F, 16.0F)) {
                this.dragging = false;
                this.s.set().accept(this.s.resetTo());
                Sounds.play("slider");
                return true;
            } else {
                return false;
            }
        }

        @Override
        public void release() {
            this.dragging = false;
        }
    }
}
