package dev.infectedware.skysong.hud;

import dev.infectedware.skysong.ModSettings;
import java.util.concurrent.ThreadLocalRandom;

public final class DragController {
    private float x;
    private float y;
    private float preX;
    private float preY;
    private float grabX;
    private float grabY;
    private boolean dragging;
    private boolean cancelled;
    private final DragController.Follow animX;
    private final DragController.Follow animY;
    private float tilt;
    private float tiltVel;
    private float mouseVel;
    private float lastTiltMouse;
    private long lastTiltNs;
    private boolean locked;
    private float lockedX;
    private float lockedY;
    private boolean ghost;
    private float ghostX;
    private float ghostY;
    private boolean outline;
    private float boxX;
    private float boxY;
    private final DragController.Follow boxAnimX;
    private final DragController.Follow boxAnimY;
    private long grabbedAtMs;

    public DragController(float x, float y) {
        this.x = x;
        this.y = y;
        this.animX = new DragController.Follow(x, 0.08F);
        this.animY = new DragController.Follow(y, 0.08F);
        this.boxAnimX = new DragController.Follow(x, 0.3F);
        this.boxAnimY = new DragController.Follow(y, 0.3F);
    }

    public float targetX() {
        return this.x;
    }

    public float targetY() {
        return this.y;
    }

    public boolean dragging() {
        return this.dragging;
    }

    public boolean wasCancelled() {
        return this.cancelled;
    }

    public float tilt() {
        return this.tilt;
    }

    public void setTarget(float x, float y) {
        this.x = x;
        this.y = y;
    }

    public void setTargetX(float x) {
        this.x = x;
    }

    public void adjustY(float dy) {
        this.y += dy;
    }

    public void syncToTarget() {
        this.animX.set(this.x);
        this.animY.set(this.y);
    }

    public boolean hovered(float mx, float my, float w, float h) {
        return mx > this.x && mx < this.x + w && my > this.y && my < this.y + h;
    }

    public boolean tryGrab(float mx, float my, float w, float h) {
        if (!this.hovered(mx, my, w, h)) {
            return false;
        }

        this.cancelled = false;
        this.dragging = true;
        this.preX = this.x;
        this.preY = this.y;
        this.grabX = mx - this.x;
        this.grabY = my - this.y;
        this.outline = ModSettings.hudDragStyle == 2;
        this.boxX = this.x;
        this.boxY = this.y;
        this.boxAnimX.set(this.x);
        this.boxAnimY.set(this.y);
        this.grabbedAtMs = System.currentTimeMillis();
        this.lastTiltMouse = mx;
        this.lastTiltNs = System.nanoTime();
        this.mouseVel = 0.0F;
        return true;
    }

    public void swapGrabOffset() {
        if (this.dragging) {
            float[] swapped = new float[]{this.grabY, this.grabX};
            this.grabX = swapped[0];
            this.grabY = swapped[1];
        }
    }

    public void release() {
        if (this.dragging) {
            this.dragging = false;
            this.cancelled = false;
            if (this.outline) {
                this.x = this.boxX;
                this.y = this.boxY;
            }
        }
    }

    public void cancel() {
        if (this.dragging) {
            this.cancelled = true;
            this.dragging = false;
            this.x = this.preX;
            this.y = this.preY;
        }
    }

    void tick(float mx, float my, float w, float h, boolean direct) {
        if (this.dragging) {
            float nx = HudSpace.clampX(mx - this.grabX, w);
            float ny = HudSpace.clampY(my - this.grabY, h);
            if (this.outline) {
                this.boxX = nx;
                this.boxY = ny;
                return;
            }

            this.x = nx;
            this.y = ny;
            if (direct) {
                this.syncToTarget();
            }
        }
    }

    void clamp(float w, float h) {
        this.x = HudSpace.clampX(this.x, w);
        this.y = HudSpace.clampY(this.y, h);
    }

    void beginRender() {
        if (!this.locked) {
            this.lockedX = this.computeX();
            this.lockedY = this.computeY();
            this.locked = true;
        }
    }

    void endRender() {
        this.locked = false;
    }

    public float renderX() {
        if (this.ghost) {
            return this.ghostX;
        } else {
            return this.locked ? this.lockedX : this.computeX();
        }
    }

    public float renderY() {
        if (this.ghost) {
            return this.ghostY;
        } else {
            return this.locked ? this.lockedY : this.computeY();
        }
    }

    public boolean outlineDragging() {
        return this.dragging && this.outline;
    }

    public float boxRenderX() {
        this.boxAnimX.to = this.boxX;
        return this.boxAnimX.get();
    }

    public float boxRenderY() {
        this.boxAnimY.to = this.boxY;
        return this.boxAnimY.get();
    }

    public long grabbedForMs() {
        return System.currentTimeMillis() - this.grabbedAtMs;
    }

    void beginGhost(float x, float y) {
        this.ghost = true;
        this.ghostX = x;
        this.ghostY = y;
    }

    void endGhost() {
        this.ghost = false;
    }

    void slideTo(float x, float y) {
        this.dragging = false;
        this.cancelled = false;
        this.x = x;
        this.y = y;
    }

    private float computeX() {
        this.animX.to = this.x;
        float v = this.animX.get();
        if (this.dragging) {
            v += jitter();
        }

        return v;
    }

    private float computeY() {
        this.animY.to = this.y;
        float v = this.animY.get();
        if (this.dragging) {
            v += jitter();
        }

        return v;
    }

    private static float jitter() {
        if (ModSettings.hudDragStyle == 1 && ModSettings.dragJitter) {
            float p = pulsePhase();
            float t = p > 0.5F ? 1.0F - p : p;
            float e = t < 0.5F ? 4.0F * t * t * t : 1.0F - (float)Math.pow(-2.0F * (t * 2.0F) + 2.0F, 3.0) / 2.0F;
            float s = e * e * 2.0F;
            return (ThreadLocalRandom.current().nextFloat() * 2.0F - 1.0F) * s;
        } else {
            return 0.0F;
        }
    }

    public static float pulsePhase() {
        return (float)(System.currentTimeMillis() % 500L) / 500.0F;
    }

    void updateTilt(float mx, boolean enabled) {
        long now = System.nanoTime();
        if (!enabled) {
            this.tilt = this.tiltVel = this.mouseVel = 0.0F;
            this.lastTiltMouse = mx;
            this.lastTiltNs = now;
        } else {
            float dt = Math.clamp((float)(now - this.lastTiltNs) / 1.0E9F, 0.001F, 0.05F);
            float v = this.dragging ? (mx - this.lastTiltMouse) / dt : 0.0F;
            this.lastTiltMouse = mx;
            this.lastTiltNs = now;
            this.mouseVel = this.mouseVel + (v - this.mouseVel) * (1.0F - (float)Math.exp(-14.0F * dt));
            float target = this.dragging ? Math.clamp(this.mouseVel * 0.012F, -6.5F, 6.5F) : 0.0F;
            float acc = (target - this.tilt) * 105.0F - this.tiltVel * 16.0F;
            this.tiltVel += acc * dt;
            this.tilt = Math.clamp(this.tilt + this.tiltVel * dt, -7.0F, 7.0F);
            if (!this.dragging && Math.abs(this.tilt) < 0.01F && Math.abs(this.tiltVel) < 0.05F) {
                this.tilt = this.tiltVel = 0.0F;
            }
        }
    }

    private static final class Follow {
        float value;
        float to;
        final float speed;
        long last = System.nanoTime();

        Follow(float v, float speed) {
            this.value = this.to = v;
            this.speed = speed;
        }

        void set(float v) {
            this.value = this.to = v;
            this.last = System.nanoTime();
        }

        float get() {
            long now = System.nanoTime();
            float dt = Math.min(0.05F, (float)(now - this.last) / 1.0E9F);
            this.last = now;
            float k = Math.min(0.999F, this.speed * 2.0F);
            float f = 1.0F - (float)Math.pow(1.0F - k, dt * 100.0F);
            this.value = this.value + (this.to - this.value) * f;
            return this.value;
        }
    }
}
