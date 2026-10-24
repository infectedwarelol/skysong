package dev.infectedware.skysong.gui.click;

import dev.infectedware.skysong.compat.GuiGraphics;
import dev.infectedware.skysong.module.Binds;
import dev.infectedware.skysong.module.ClientModule;
import dev.infectedware.skysong.render.r2d.R2D;
import dev.infectedware.skysong.theme.ClientAccent;
import dev.infectedware.skysong.theme.Colors;

public final class BindPopup {
    private static final float W = 96.0F;
    private static final float H = 43.0F;
    private static final float PAD = 6.0F;
    private static final float ROW = 13.0F;
    private static final float GAP = 5.0F;
    private final Anim anim = new Anim(220);
    private ClientModule module;
    private boolean open;
    private boolean listening;
    private boolean dragging;
    private float x;
    private float y;
    private float grabX;
    private float grabY;
    private float selT;
    private long lastNs = System.nanoTime();

    public boolean isOpen() {
        return this.open;
    }

    public boolean isVisible() {
        return this.module != null && this.anim.linear() > 0.01F;
    }

    public boolean listening() {
        return this.open && this.listening;
    }

    public void open(ClientModule m, float mx, float my) {
        this.module = m;
        this.open = true;
        this.listening = false;
        this.x = mx;
        this.y = my + 2.0F;
        this.selT = Binds.isHold(m.name()) ? 1.0F : 0.0F;
        this.anim.forwards(true);
        Sounds.play("settings_open");
    }

    public void close() {
        if (this.open) {
            this.open = false;
            this.listening = false;
            this.dragging = false;
            this.anim.forwards(false);
        }
    }

    private String keyLabel() {
        return this.module == null ? "None" : Binds.shortName(Binds.key(this.module.name()));
    }

    public void render(GuiGraphics c, float alpha, float mx, float my) {
        if (this.module != null) {
            float p = this.anim.get();
            if (p <= 0.01F) {
                if (!this.open) {
                    this.module = null;
                }
            } else {
                long now = System.nanoTime();
                float dt = Math.min(0.1F, (float)(now - this.lastNs) / 1.0E9F);
                this.lastNs = now;
                if (this.dragging) {
                    this.x = mx - this.grabX;
                    this.y = my - this.grabY;
                }

                float a = p * alpha;
                float py = this.y + (1.0F - p) * 4.0F;
                this.selT = Anim.follow(this.selT, Binds.isHold(this.module.name()) ? 1.0F : 0.0F, dt, 14.0F);
                Ui.dropBackground(c, this.x, py, 96.0F, 43.0F, a);
                float row = py + 6.0F;
                Ui.text(c, "Bind", this.x + 6.0F, row + 3.0F, 6.0F, Colors.multAlpha(-1, 0.85F * a));
                String label = this.listening ? "..." : this.keyLabel();
                float kw = Math.max(34.0F, Ui.width(label, 6.0F) + 10.0F);
                float kx = this.x + 96.0F - 6.0F - kw;
                Ui.panelBg(c, kx, row, kw, 13.0F, 3.0F, a);
                Ui.text(c, label, kx + (kw - Ui.width(label, 6.0F)) * 0.5F, row + 3.0F, 6.0F, Colors.multAlpha(-1, 0.9F * a));
                float row2 = row + 13.0F + 5.0F;
                float half = 39.5F;
                float lx = this.x + 6.0F;
                float rx = lx + half + 5.0F;
                Ui.panelBg(c, lx, row2, half, 13.0F, 3.0F, a);
                Ui.panelBg(c, rx, row2, half, 13.0F, 3.0F, a);
                R2D.rect(
                    c, lx + (rx - lx) * this.selT, row2, half, 13.0F, Ui.cornerRadius(3.0F, half, 13.0F), Colors.multAlpha(ClientAccent.accentSoft(210.0F), a)
                );
                mode(c, lx, row2, half, "Toggle", 1.0F - this.selT, a);
                mode(c, rx, row2, half, "Hold", this.selT, a);
            }
        }
    }

    private static void mode(GuiGraphics c, float x, float y, float w, String s, float on, float a) {
        int color = Colors.lerp(Colors.multAlpha(-1, 0.65F * a), Colors.multAlpha(-15856114, a), on);
        Ui.text(c, s, x + (w - Ui.width(s, 6.0F)) * 0.5F, y + 3.0F, 6.0F, color);
    }

    public boolean click(float mx, float my) {
        if (!this.open || this.module == null) {
            return false;
        }

        if (!Ui.inside(mx, my, this.x, this.y, 96.0F, 43.0F)) {
            this.close();
            return false;
        }

        float row = this.y + 6.0F;
        String label = this.listening ? "..." : this.keyLabel();
        float kw = Math.max(34.0F, Ui.width(label, 6.0F) + 10.0F);
        float kx = this.x + 96.0F - 6.0F - kw;
        if (Ui.inside(mx, my, kx, row, kw, 13.0F)) {
            this.listening = !this.listening;
            return true;
        }

        float row2 = row + 13.0F + 5.0F;
        float half = 39.5F;
        if (my >= row2 && my <= row2 + 13.0F) {
            if (Ui.inside(mx, my, this.x + 6.0F, row2, half, 13.0F)) {
                Binds.setHold(this.module.name(), false);
                return true;
            }

            if (Ui.inside(mx, my, this.x + 6.0F + half + 5.0F, row2, half, 13.0F)) {
                Binds.setHold(this.module.name(), true);
                return true;
            }
        }

        this.dragging = true;
        this.grabX = mx - this.x;
        this.grabY = my - this.y;
        return true;
    }

    public boolean keyPressed(int key) {
        if (this.listening && this.module != null) {
            Binds.set(this.module.name(), key == 256 ? -1 : key);
            this.listening = false;
            return true;
        } else {
            return false;
        }
    }

    public boolean mouseBind(int button) {
        if (this.listening && this.module != null) {
            Binds.set(this.module.name(), -100 - button);
            this.listening = false;
            return true;
        } else {
            return false;
        }
    }

    public void releaseDrag() {
        this.dragging = false;
    }
}
