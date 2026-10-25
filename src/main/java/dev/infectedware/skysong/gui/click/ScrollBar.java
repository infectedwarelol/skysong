package dev.infectedware.skysong.gui.click;

import net.minecraft.util.Mth;
import dev.infectedware.skysong.compat.GuiGraphics;
import dev.infectedware.skysong.render.r2d.R2D;

public final class ScrollBar {
    private boolean dragging;
    private boolean visible;
    private float grabOffset;
    private float hoverT;
    private float dragT;
    private float trackX;
    private float trackY;
    private float trackH;
    private float thumbY;
    private float thumbH;
    private long lastNs = System.nanoTime();

    public float render(GuiGraphics c, float x, float y, float trackHeight, float viewH, float contentH, float scroll, float a, float mx, float my) {
        long now = System.nanoTime();
        float dt = Math.min(0.1F, (float)(now - this.lastNs) / 1.0E9F);
        this.lastNs = now;
        float max = Math.max(0.0F, contentH - viewH);
        this.visible = max > 0.5F;
        if (!this.visible) {
            this.dragging = false;
            return scroll;
        }

        float th = Math.max(12.0F, trackHeight * (viewH / contentH));
        float travel = Math.max(1.0F, trackHeight - th);
        if (this.dragging) {
            float ty = Mth.clamp(my - this.grabOffset, y, y + travel);
            scroll = (ty - y) / travel * max;
        }

        scroll = Mth.clamp(scroll, 0.0F, max);
        float ty = y + travel * (scroll / max);
        boolean over = mx >= x - 4.0F && mx <= x + 1.5F + 4.0F && my >= ty - 2.0F && my <= ty + th + 2.0F;
        this.hoverT = Anim.follow(this.hoverT, over ? 1.0F : 0.0F, dt, 16.0F);
        this.dragT = Anim.follow(this.dragT, this.dragging ? 1.0F : 0.0F, dt, 16.0F);
        float k = Math.max(this.hoverT, this.dragT);
        R2D.rect(c, x, y, 1.5F, trackHeight, 0.75F, Ui.white((20.0F + 18.0F * k) * a));
        R2D.accentFillVertical(c, x, ty, 1.5F, th, 0.75F, (160.0F + 95.0F * k) * a);
        this.trackX = x;
        this.trackY = y;
        this.trackH = trackHeight;
        this.thumbY = ty;
        this.thumbH = th;
        return scroll;
    }

    public boolean isDragging() {
        return this.dragging;
    }

    public boolean tryGrab(float mx, float my) {
        if (!this.visible || mx < this.trackX - 4.0F || mx > this.trackX + 1.5F + 4.0F) {
            return false;
        } else if (my >= this.thumbY - 2.0F && my <= this.thumbY + this.thumbH + 2.0F) {
            this.dragging = true;
            this.grabOffset = my - this.thumbY;
            return true;
        } else if (my >= this.trackY && my <= this.trackY + this.trackH) {
            this.dragging = true;
            this.grabOffset = this.thumbH * 0.5F;
            return true;
        } else {
            return false;
        }
    }

    public void release() {
        this.dragging = false;
    }
}
