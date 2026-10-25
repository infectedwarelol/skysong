package dev.infectedware.skysong.gui.click;

import net.minecraft.util.Mth;
import dev.infectedware.skysong.ModConfig;
import dev.infectedware.skysong.ModSettings;
import dev.infectedware.skysong.compat.GuiGraphics;
import dev.infectedware.skysong.module.ClientModule;
import dev.infectedware.skysong.render.r2d.R2D;
import dev.infectedware.skysong.theme.Colors;
import dev.infectedware.skysong.ui.Clip;
import java.util.List;

public final class SettingsPopup {
    private final Anim anim = new Anim(220);
    private final ScrollBar scrollBar = new ScrollBar();
    private ClientModule module;
    private boolean open;
    private List<Widget> widgets = List.of();
    private float width = 180.0F;
    private float x;
    private float y;
    private float height;
    private float bodyH;
    private float maxBodyH;
    private float scroll;
    private float scrollTarget;
    private float renderX;
    private float renderY;
    private boolean dragging;
    private float grabX;
    private float grabY;
    private float screenW;
    private float screenH;
    private long lastNs = System.nanoTime();
    private static boolean rebuildSoon;

    public static void rebuildSoon() {
        rebuildSoon = true;
    }

    static boolean takeRebuild() {
        boolean pending = rebuildSoon;
        rebuildSoon = false;
        return pending;
    }

    public boolean isOpen() {
        return this.open;
    }

    public boolean isOpenFor(ClientModule m) {
        return this.open && this.module == m;
    }

    public ClientModule module() {
        return this.module;
    }

    public boolean isVisible() {
        return this.module != null && this.anim.linear() > 0.01F;
    }

    public List<Widget> widgets() {
        return this.open ? this.widgets : List.of();
    }

    public boolean toggle(ClientModule m, float mx, float my, float areaH, float screenW, float screenH) {
        if (this.isOpenFor(m)) {
            this.close();
            return false;
        }

        this.module = m;
        this.widgets = Widget.build(m.settings().get());
        this.open = true;
        this.scroll = this.scrollTarget = 0.0F;
        this.dragging = false;
        this.screenW = screenW;
        this.screenH = screenH;
        this.maxBodyH = areaH - 7.0F - 7.0F - 8.0F;
        float pref = 0.0F;

        for (Widget w : this.widgets) {
            pref = Math.max(pref, w.preferredWidth());
        }

        this.width = Math.clamp(pref + 10.0F + 4.0F, 110.0F, 180.0F);
        float[] saved = savedPosition(m.name());
        this.x = saved != null ? saved[0] : mx + 2.5F;
        this.y = saved != null ? saved[1] : my;
        this.x = Mth.clamp(this.x, 4.0F, screenW - this.width - 4.0F);
        this.layout();
        this.y = Mth.clamp(this.y, 4.0F, screenH - this.height - 4.0F);
        this.renderX = this.x;
        this.renderY = this.y;
        this.anim.forwards(true);
        return true;
    }

    public void reopen(ClientModule m, float atX, float atY, float areaH, float screenW, float screenH) {
        if (!this.isOpenFor(m)) {
            this.toggle(m, atX - 2.5F, atY, areaH, screenW, screenH);
            if (savedPosition(m.name()) == null) {
                this.x = Mth.clamp(atX, 4.0F, screenW - this.width - 4.0F);
                this.y = Mth.clamp(atY, 4.0F, screenH - this.height - 4.0F);
            }

            this.renderX = this.x;
            this.renderY = this.y;
        }
    }

    public float x() {
        return this.x;
    }

    public float y() {
        return this.y;
    }

    public void close() {
        if (this.open) {
            this.open = false;
            this.dragging = false;
            this.closeOverlays();
            this.anim.forwards(false);
            ModConfig.save();
        }
    }

    private float contentHeight() {
        float h = 0.0F;
        int n = 0;

        for (Widget w : this.widgets) {
            if (w.visible()) {
                h += w.height() + 4.0F;
                n++;
            }
        }

        return n == 0 ? 0.0F : h - 4.0F;
    }

    private void layout() {
        float content = this.contentHeight();
        this.bodyH = Math.min(content, this.maxBodyH);
        this.height = 7.0F + this.bodyH + 7.0F;
        float max = Math.max(0.0F, content - this.bodyH);
        this.scrollTarget = Mth.clamp(this.scrollTarget, 0.0F, max);
        this.scroll = Mth.clamp(this.scroll, 0.0F, max);
    }

    public void render(GuiGraphics c, float alpha, float mx, float my) {
        if (this.module != null) {
            float p = this.anim.get();
            if (p <= 0.01F) {
                if (!this.open) {
                    this.module = null;
                    this.widgets = List.of();
                }
            } else {
                if (rebuildSoon) {
                    rebuildSoon = false;
                    if (this.open) {
                        this.widgets = Widget.build(this.module.settings().get());
                    }
                }

                long now = System.nanoTime();
                float dt = Math.min(0.1F, (float)(now - this.lastNs) / 1.0E9F);
                this.lastNs = now;
                if (this.dragging) {
                    this.x = Mth.clamp(mx - this.grabX, 4.0F, this.screenW - this.width - 4.0F);
                    this.y = Mth.clamp(my - this.grabY, 4.0F, this.screenH - this.height - 4.0F);
                }

                this.renderX = Anim.follow(this.renderX, this.x, dt, 22.0F);
                this.renderY = Anim.follow(this.renderY, this.y, dt, 22.0F);
                this.layout();
                this.scroll = Anim.follow(this.scroll, this.scrollTarget, dt, 16.0F);
                if (Math.abs(this.scroll - this.scrollTarget) < 0.05F) {
                    this.scroll = this.scrollTarget;
                }

                float a = p * alpha;
                float px = this.renderX;
                float py = this.renderY + (1.0F - p) * 4.0F;
                R2D.clientRectFixed(c, px, py, this.width, this.height, 8.0F, a);
                R2D.outline(c, px, py, this.width, this.height, 8.0F, 0.6F, Colors.rgba(255, 255, 255, 24.0F * a));
                float bodyY = py + 7.0F;
                Clip.push(c, px, bodyY, px + this.width, bodyY + this.bodyH);
                float wy = bodyY - this.scroll;

                for (Widget w : this.widgets) {
                    if (w.visible()) {
                        float h = w.height();
                        if (wy + h >= bodyY - 6.0F && wy <= bodyY + this.bodyH + 6.0F) {
                            w.render(c, px + 5.0F, wy, this.width - 10.0F, a, mx, my);
                        }

                        wy += h + 4.0F;
                    }
                }

                Clip.pop(c);
                float content = this.contentHeight();
                if (content > this.bodyH + 0.5F) {
                    float s = this.scrollBar.render(c, px + this.width - 4.5F, bodyY, this.bodyH, this.bodyH, content, this.scroll, a, mx, my);
                    if (this.scrollBar.isDragging()) {
                        this.scroll = this.scrollTarget = s;
                    }
                }

                float oy = bodyY - this.scroll;

                for (Widget w : this.widgets) {
                    if (w.visible()) {
                        if (w.hasOverlay()) {
                            w.renderOverlay(c, px + 5.0F, oy, this.width - 10.0F, a, mx, my);
                        }

                        oy += w.height() + 4.0F;
                    }
                }
            }
        }
    }

    public boolean contains(float mx, float my) {
        return this.isVisible() && Ui.inside(mx, my, this.renderX, this.renderY, this.width, this.height);
    }

    public void click(float mx, float my) {
        if (this.open && this.module != null) {
            boolean overlayOpen = false;
            float wy = this.renderY + 7.0F - this.scroll;

            for (Widget w : this.widgets) {
                if (w.visible()) {
                    if (w.overlayOpen()) {
                        overlayOpen = true;
                        if (w.clickOverlay(this.renderX + 5.0F, wy, this.width - 10.0F, mx, my)) {
                            return;
                        }
                    }

                    wy += w.height() + 4.0F;
                }
            }

            if (overlayOpen) {
                this.closeOverlays();
                return;
            }

            if (!this.contains(mx, my)) {
                this.close();
                Sounds.play("module_settings_close");
                return;
            }

            if (this.scrollBar.tryGrab(mx, my)) {
                return;
            }

            if (my >= this.renderY + 7.0F && my <= this.renderY + 7.0F + this.bodyH) {
                float y2 = this.renderY + 7.0F - this.scroll;

                for (Widget w : this.widgets) {
                    if (w.visible()) {
                        float h = w.height();
                        if (my >= y2 && my < y2 + h) {
                            if (!(w instanceof Widget.Separator)) {
                                boolean hit = w.click(this.renderX + 5.0F, y2, this.width - 10.0F, mx, my);
                                if (hit && w.overlayOpen()) {
                                    for (Widget other : this.widgets) {
                                        if (other != w) {
                                            other.closeOverlay();
                                        }
                                    }
                                }

                                if (hit) {
                                    ModConfig.save();
                                    return;
                                }
                            }
                            break;
                        }

                        y2 += h + 4.0F;
                    }
                }
            }

            for (Widget w : this.widgets) {
                if (w.focused()) {
                    w.click(-1000.0F, -1000.0F, 0.0F, -1000.0F, -1000.0F);
                }
            }

            this.dragging = true;
            this.grabX = mx - this.x;
            this.grabY = my - this.y;
        }
    }

    public boolean middleClick(float mx, float my) {
        if (this.open && this.contains(mx, my)) {
            float wy = this.renderY + 7.0F - this.scroll;

            for (Widget w : this.widgets) {
                if (w.visible()) {
                    float h = w.height();
                    if (my >= wy && my < wy + h) {
                        if (w.middleClick(this.renderX + 5.0F, wy, this.width - 10.0F, mx, my)) {
                            ModConfig.save();
                        }

                        return true;
                    }

                    wy += h + 4.0F;
                }
            }

            return true;
        } else {
            return false;
        }
    }

    public boolean scroll(float mx, float my, double amount) {
        if (this.open && this.module != null) {
            float wy = this.renderY + 7.0F - this.scroll;

            for (Widget w : this.widgets) {
                if (w.visible()) {
                    if (w.overlayOpen() && w.scrollOverlay(this.renderX + 5.0F, wy, this.width - 10.0F, mx, my, amount)) {
                        return true;
                    }

                    wy += w.height() + 4.0F;
                }
            }

            if (this.contains(mx, my)) {
                float max = Math.max(0.0F, this.contentHeight() - this.bodyH);
                this.scrollTarget = Mth.clamp(this.scrollTarget - (float)amount * 12.0F, 0.0F, max);
                return true;
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    public void releaseDrags() {
        if (this.dragging && this.module != null) {
            savePosition(this.module.name(), this.x, this.y);
        }

        this.dragging = false;
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
        for (Widget w : this.widgets()) {
            if (w.focused() && w.keyPressed(key)) {
                return true;
            }
        }

        return false;
    }

    public boolean mouseBind(int button) {
        if (!this.open) {
            return false;
        }

        for (Widget w : this.widgets()) {
            if (w.focused() && w.mouseBind(button)) {
                return true;
            }
        }

        return false;
    }

    public boolean charTyped(char chr) {
        for (Widget w : this.widgets()) {
            if (w.focused() && w.charTyped(chr)) {
                return true;
            }
        }

        return false;
    }

    private static float[] savedPosition(String name) {
        for (String entry : ModSettings.popupPositions.split(";")) {
            int eq = entry.lastIndexOf(61);
            if (eq > 0 && entry.substring(0, eq).equals(name)) {
                String[] xy = entry.substring(eq + 1).split(",");

                try {
                    return new float[]{Float.parseFloat(xy[0]), Float.parseFloat(xy[1])};
                } catch (RuntimeException e) {
                    return null;
                }
            }
        }

        return null;
    }

    private static void savePosition(String name, float x, float y) {
        StringBuilder sb = new StringBuilder();

        for (String entry : ModSettings.popupPositions.split(";")) {
            int eq = entry.lastIndexOf(61);
            if (eq > 0 && !entry.substring(0, eq).equals(name)) {
                sb.append(entry).append(';');
            }
        }

        sb.append(name).append('=').append(Math.round(x * 10.0F) / 10.0F).append(',').append(Math.round(y * 10.0F) / 10.0F);
        ModSettings.popupPositions = sb.toString();
        ModConfig.save();
    }
}
