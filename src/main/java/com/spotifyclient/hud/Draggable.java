package com.spotifyclient.hud;

import com.mojang.math.Axis;
import com.spotifyclient.compat.GuiGraphics;

public abstract class Draggable {
    private final String id;
    private final float defaultX;
    private final float defaultY;
    private final DragController drag;
    boolean placed;

    protected Draggable(String id, float defaultX, float defaultY) {
        this.id = id;
        this.defaultX = defaultX;
        this.defaultY = defaultY;
        this.drag = new DragController(defaultX, defaultY);
    }

    public String id() {
        return this.id;
    }

    public DragController drag() {
        return this.drag;
    }

    public abstract float width();

    public abstract float height();

    public float overlayWidth() {
        return this.width();
    }

    public float overlayHeight() {
        return this.height();
    }

    public abstract boolean interactive();

    protected abstract void render(GuiGraphics var1);

    protected float x() {
        return this.drag.renderX();
    }

    protected float y() {
        return this.drag.renderY();
    }

    public float[] defaultPosition() {
        return new float[]{this.defaultX, this.defaultY};
    }

    public void slideHome() {
        float[] p = this.defaultPosition();
        this.drag.slideTo(p[0], p[1]);
    }

    final void renderGhost(GuiGraphics c, float gx, float gy) {
        this.drag.beginGhost(gx, gy);

        try {
            this.render(c);
        } finally {
            this.drag.endGhost();
        }
    }

    public void reset() {
        float[] p = this.defaultPosition();
        this.drag.setTarget(p[0], p[1]);
        this.drag.syncToTarget();
    }

    final void renderWithTilt(GuiGraphics c) {
        this.drag.beginRender();

        try {
            float t = this.drag.tilt();
            if (!(Math.abs(t) < 0.01F)) {
                float cx = this.x() + this.width() * 0.5F;
                float cy = this.y() + this.height() * 0.5F;
                c.pose().pushPose();
                c.pose().translate(cx, cy, 0.0F);
                c.pose().mulPose(Axis.ZP.rotationDegrees(t));
                c.pose().translate(-cx, -cy, 0.0F);
                this.render(c);
                c.pose().popPose();
                return;
            }

            this.render(c);
        } finally {
            this.drag.endRender();
        }
    }

    protected static void pushScale(GuiGraphics c, float cx, float cy, float s) {
        c.pose().pushPose();
        c.pose().translate(cx, cy, 0.0F);
        c.pose().scale(s, s, 1.0F);
        c.pose().translate(-cx, -cy, 0.0F);
    }
}
