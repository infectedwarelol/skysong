package dev.infectedware.skysong.hud.components;

public final class Smooth {
    private float from;
    private float to;
    private float value;
    private long start;
    private double seconds;

    public Smooth(float initial) {
        this.from = this.to = initial;
    }

    public void set(float v) {
        this.from = this.to = v;
        this.seconds = 0.0;
    }

    public void run(float target, double seconds) {
        this.from = this.get();
        this.to = target;
        this.seconds = seconds;
        this.start = System.nanoTime();
    }

    public void target(float target, double seconds) {
        if (Math.abs(target - this.to) > 1.0E-4F) {
            this.run(target, seconds);
        }
    }

    public float targetValue() {
        return this.to;
    }

    public float get() {
        if (this.seconds <= 0.0) {
            return this.to;
        } else {
            double t = (System.nanoTime() - this.start) / 1.0E9 / this.seconds;
            if (t >= 1.0) {
                this.seconds = 0.0;
                return this.to;
            } else {
                float e = (float)(t * t * (3.0 - 2.0 * t));
                return this.from + (this.to - this.from) * e;
            }
        }
    }
}
