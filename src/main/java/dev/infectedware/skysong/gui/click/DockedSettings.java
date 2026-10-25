package dev.infectedware.skysong.gui.click;

import net.minecraft.util.Mth;
import dev.infectedware.skysong.ModConfig;
import dev.infectedware.skysong.compat.GuiGraphics;
import dev.infectedware.skysong.module.ClientModule;
import dev.infectedware.skysong.ui.Clip;
import java.util.List;

public final class DockedSettings {
    private static final float GAP = 4.0F;
    private static final float SIDE = 2.0F;
    private static final float SCROLL_STEP = 12.0F;
    private final ScrollBar scrollBar = new ScrollBar();
    private ClientModule module;
    private List<Widget> widgets = List.of();
    private float x;
    private float y;
    private float width;
    private float height;
    private float scroll;
    private float scrollTarget;
    private long lastNs = System.nanoTime();

    public void show(ClientModule m) {
        if (m == this.module) {
            return;
        }

        this.closeOverlays();
        this.module = m;
        this.widgets = m != null && m.hasSettings() ? Widget.build(m.settings().get()) : List.of();
        this.scroll = this.scrollTarget = 0.0F;
    }

    public void rebuild() {
        if (this.module != null && this.module.hasSettings()) {
            this.widgets = Widget.build(this.module.settings().get());
        }
    }

    public boolean isEmpty() {
        return this.widgets.isEmpty();
    }

    private float contentHeight() {
        float h = 0.0F;
        int n = 0;

        for (Widget w : this.widgets) {
            if (w.visible()) {
                h += w.height() + GAP;
                n++;
            }
        }

        return n == 0 ? 0.0F : h - GAP;
    }

    private float maxScroll() {
        return Math.max(0.0F, this.contentHeight() - this.height);
    }

    public void render(GuiGraphics c, float x, float y, float w, float h, float a, float mx, float my) {
        this.x = x;
        this.y = y;
        this.width = w;
        this.height = h;
        if (SettingsPopup.takeRebuild()) {
            this.rebuild();
        }

        long now = System.nanoTime();
        float dt = Math.min(0.1F, (now - this.lastNs) / 1.0E9F);
        this.lastNs = now;
        float max = this.maxScroll();
        this.scrollTarget = Mth.clamp(this.scrollTarget, 0.0F, max);
        this.scroll = Anim.follow(this.scroll, this.scrollTarget, dt, 16.0F);
        if (Math.abs(this.scroll - this.scrollTarget) < 0.05F) {
            this.scroll = this.scrollTarget;
        }

        float innerW = w - SIDE * 2.0F - 6.0F;
        Clip.push(c, x, y, x + w, y + h);
        float wy = y - this.scroll;

        for (Widget widget : this.widgets) {
            if (widget.visible()) {
                float wh = widget.height();
                if (wy + wh >= y - 6.0F && wy <= y + h + 6.0F) {
                    widget.render(c, x + SIDE, wy, innerW, a, mx, my);
                }

                wy += wh + GAP;
            }
        }

        Clip.pop(c);
        float content = this.contentHeight();
        if (content > h + 0.5F) {
            float s = this.scrollBar.render(c, x + w - 3.5F, y, h, h, content, this.scroll, a, mx, my);
            if (this.scrollBar.isDragging()) {
                this.scroll = this.scrollTarget = s;
            }
        }

        float oy = y - this.scroll;

        for (Widget widget : this.widgets) {
            if (widget.visible()) {
                if (widget.hasOverlay()) {
                    widget.renderOverlay(c, x + SIDE, oy, innerW, a, mx, my);
                }

                oy += widget.height() + GAP;
            }
        }
    }

    public boolean contains(float mx, float my) {
        return Ui.inside(mx, my, this.x, this.y, this.width, this.height);
    }

    public boolean click(float mx, float my) {
        float innerW = this.width - SIDE * 2.0F - 6.0F;
        boolean overlayOpen = false;
        float wy = this.y - this.scroll;

        for (Widget w : this.widgets) {
            if (w.visible()) {
                if (w.overlayOpen()) {
                    overlayOpen = true;
                    if (w.clickOverlay(this.x + SIDE, wy, innerW, mx, my)) {
                        return true;
                    }
                }

                wy += w.height() + GAP;
            }
        }

        if (overlayOpen) {
            this.closeOverlays();
            return true;
        }

        if (!this.contains(mx, my)) {
            this.blurFields();
            return false;
        }

        if (this.scrollBar.tryGrab(mx, my)) {
            return true;
        }

        float rowY = this.y - this.scroll;

        for (Widget w : this.widgets) {
            if (w.visible()) {
                float h = w.height();
                if (my >= rowY && my < rowY + h) {
                    if (!(w instanceof Widget.Separator)) {
                        boolean hit = w.click(this.x + SIDE, rowY, innerW, mx, my);
                        if (hit && w.overlayOpen()) {
                            for (Widget other : this.widgets) {
                                if (other != w) {
                                    other.closeOverlay();
                                }
                            }
                        }

                        if (hit) {
                            ModConfig.save();
                            return true;
                        }
                    }
                    break;
                }

                rowY += h + GAP;
            }
        }

        this.blurFields();
        return true;
    }

    private void blurFields() {
        for (Widget w : this.widgets) {
            if (w.focused()) {
                w.click(-1000.0F, -1000.0F, 0.0F, -1000.0F, -1000.0F);
            }
        }
    }

    public boolean middleClick(float mx, float my) {
        if (!this.contains(mx, my)) {
            return false;
        }

        float innerW = this.width - SIDE * 2.0F - 6.0F;
        float wy = this.y - this.scroll;

        for (Widget w : this.widgets) {
            if (w.visible()) {
                float h = w.height();
                if (my >= wy && my < wy + h) {
                    if (w.middleClick(this.x + SIDE, wy, innerW, mx, my)) {
                        ModConfig.save();
                    }

                    return true;
                }

                wy += h + GAP;
            }
        }

        return true;
    }

    public boolean scroll(float mx, float my, double amount) {
        float innerW = this.width - SIDE * 2.0F - 6.0F;
        float wy = this.y - this.scroll;

        for (Widget w : this.widgets) {
            if (w.visible()) {
                if (w.overlayOpen() && w.scrollOverlay(this.x + SIDE, wy, innerW, mx, my, amount)) {
                    return true;
                }

                wy += w.height() + GAP;
            }
        }

        if (this.contains(mx, my)) {
            this.scrollTarget = Mth.clamp(this.scrollTarget - (float)amount * SCROLL_STEP, 0.0F, this.maxScroll());
            return true;
        }

        return false;
    }

    public void releaseDrags() {
        this.scrollBar.release();

        for (Widget w : this.widgets) {
            w.release();
        }

        ModConfig.save();
    }

    public boolean hasOpenOverlay() {
        for (Widget w : this.widgets) {
            if (w.overlayOpen()) {
                return true;
            }
        }

        return false;
    }

    public void closeOverlays() {
        for (Widget w : this.widgets) {
            w.closeOverlay();
        }
    }

    public boolean keyPressed(int key) {
        for (Widget w : this.widgets) {
            if (w.focused() && w.keyPressed(key)) {
                return true;
            }
        }

        return false;
    }

    public boolean mouseBind(int button) {
        for (Widget w : this.widgets) {
            if (w.focused() && w.mouseBind(button)) {
                return true;
            }
        }

        return false;
    }

    public boolean charTyped(char chr) {
        for (Widget w : this.widgets) {
            if (w.focused() && w.charTyped(chr)) {
                return true;
            }
        }

        return false;
    }

    public boolean typing() {
        for (Widget w : this.widgets) {
            if (w.focused()) {
                return true;
            }
        }

        return false;
    }
}
