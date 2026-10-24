package dev.infectedware.skysong.gui.click;

public final class Anim {
    private final int ms;
    private boolean forwards;
    private float linear;
    private long last = System.nanoTime();

    public Anim(int ms) {
        this(ms, false);
    }

    public Anim(int ms, boolean startDone) {
        this.ms = ms;
        this.forwards = startDone;
        this.linear = startDone ? 1.0F : 0.0F;
    }

    public Anim forwards(boolean forwards) {
        this.tick();
        this.forwards = forwards;
        return this;
    }

    public boolean isForwards() {
        return this.forwards;
    }

    public void restart() {
        this.linear = 0.0F;
        this.forwards = true;
        this.last = System.nanoTime();
    }

    public void snap(boolean done) {
        this.forwards = done;
        this.linear = done ? 1.0F : 0.0F;
        this.last = System.nanoTime();
    }

    public float get() {
        this.tick();
        float t = this.linear;
        return this.forwards ? 1.0F - (1.0F - t) * (1.0F - t) : t * t;
    }

    public float linear() {
        this.tick();
        return this.linear;
    }

    public boolean done() {
        this.tick();
        return this.forwards ? this.linear >= 1.0F : this.linear <= 0.0F;
    }

    private void tick() {
        long now = System.nanoTime();
        float dt = (float)(now - this.last) / 1000000.0F;
        this.last = now;
        float step = this.ms <= 0 ? 1.0F : dt / this.ms;
        this.linear = this.forwards ? Math.min(1.0F, this.linear + step) : Math.max(0.0F, this.linear - step);
    }

    public static float follow(float current, float target, float dtSeconds, float rate) {
        return current + (target - current) * (1.0F - (float)Math.exp(-dtSeconds * rate));
    }

    public static float clamp01(float f) {
        return f < 0.0F ? 0.0F : Math.min(f, 1.0F);
    }

    public static float easeOutBack(float t) {
        t = clamp01(t);
        float c1 = 1.70158F;
        float c3 = c1 + 1.0F;
        float u = t - 1.0F;
        return 1.0F + c3 * u * u * u + c1 * u * u;
    }

    public static float smoothstep(float t) {
        t = clamp01(t);
        return t * t * (3.0F - 2.0F * t);
    }

    public static float easeOutCubic(float t) {
        float u = 1.0F - clamp01(t);
        return 1.0F - u * u * u;
    }
}
