package dev.infectedware.skysong.gui.click;

import dev.infectedware.skysong.compat.Gpu;
import dev.infectedware.skysong.compat.GuiGraphics;
import dev.infectedware.skysong.render.r2d.R2D;
import dev.infectedware.skysong.theme.Colors;
import org.joml.Matrix4f;

public final class Holo {
    private static final float EYE = 0.9F;
    private static final float RADIUS = 0.62F;
    private static final float PUSH = 36.0F;
    private static final float FLY = 260.0F;
    private static final float STAGGER = 0.05F;
    private static final float SWAY = 0.07F;
    private static final float CORNER = 6.0F;
    private static float viewW = 1.0F;
    private static float viewH = 1.0F;
    private static float scale = 1.0F;
    private static float appear = 1.0F;
    private static float sway;
    private static float time;
    private static long lastNs = System.nanoTime();

    private Holo() {
    }

    static void frame(float vw, float vh, float s, float open, float mouseX) {
        long now = System.nanoTime();
        float dt = Math.min(0.1F, (now - lastNs) / 1.0E9F);
        lastNs = now;
        viewW = Math.max(1.0F, vw);
        viewH = Math.max(1.0F, vh);
        scale = s;
        appear = open;
        time += dt;
        float target = (mouseX / viewW - 0.5F) * 2.0F * SWAY;
        sway = Anim.follow(sway, Math.clamp(target, -SWAY, SWAY), dt, 4.0F);
    }

    static Matrix4f place(float x, float y, float w, float h, int index, float lift) {
        float cx = viewW / 2.0F;
        float cy = viewH / 2.0F;
        float eye = viewW * EYE;
        float radius = eye * RADIUS;
        float ax = x + w / 2.0F;
        float ay = y + h / 2.0F;
        float theta = (ax - cx) / radius + sway;
        float p = Anim.clamp01(appear * (1.0F + STAGGER * 8.0F) - index * STAGGER);
        float eased = 1.0F - (1.0F - p) * (1.0F - p) * (1.0F - p);
        float bob = (float)Math.sin(time * 1.3F + index * 0.8F) * 1.1F;
        float ox = radius * (float)Math.sin(theta);
        float oy = ay - cy + bob + lift;
        float oz = radius * (1.0F - (float)Math.cos(theta)) - PUSH - FLY * (1.0F - eased);
        float rx = (float)Math.cos(theta);
        float rz = (float)Math.sin(theta);
        float px0 = ox - rx * ax;
        float py0 = oy - ay;
        float pz0 = oz - rz * ax;
        float w0 = 1.0F - pz0 / eye;
        return new Matrix4f(
            rx - cx * rz / eye, -cy * rz / eye, 0.0F, -rz / eye,
            0.0F, 1.0F, 0.0F, 0.0F,
            0.0F, 0.0F, 0.0F, 0.0F,
            px0 + cx * w0, py0 + cy * w0, 0.0F, w0
        );
    }

    static void begin(Matrix4f virtualTransform) {
        Matrix4f gui = new Matrix4f().scaling(scale, scale, 1.0F).mul(virtualTransform).mul(new Matrix4f().scaling(1.0F / scale, 1.0F / scale, 1.0F));
        Gpu.setGuiTransform(gui);
    }

    static void end() {
        Gpu.setGuiTransform(null);
    }

    static float[] unproject(Matrix4f t, float sx, float sy) {
        float a0 = t.m00() - sx * t.m03();
        float a1 = t.m10() - sx * t.m13();
        float a2 = t.m30() - sx * t.m33();
        float b0 = t.m01() - sy * t.m03();
        float b1 = t.m11() - sy * t.m13();
        float b2 = t.m31() - sy * t.m33();
        float det = a0 * b1 - a1 * b0;
        if (Math.abs(det) < 1.0E-6F) {
            return null;
        }

        float lx = (-a2 * b1 + a1 * b2) / det;
        float ly = (-a0 * b2 + a2 * b0) / det;
        float w = t.m03() * lx + t.m13() * ly + t.m33();
        return w <= 0.0F ? null : new float[]{lx, ly};
    }

    static float[] inside(Matrix4f t, float sx, float sy, float x, float y, float w, float h) {
        float[] p = unproject(t, sx, sy);
        return p != null && Ui.inside(p[0], p[1], x, y, w, h) ? p : null;
    }

    static int cyan(float alpha) {
        return Colors.rgba(92, 230, 255, alpha);
    }

    static void backing(GuiGraphics c, float x, float y, float w, float h, float a, int index) {
        float flicker = 0.93F + 0.07F * (float)Math.sin(time * 6.0F + index * 1.7F);
        float fa = a * flicker;
        R2D.rect(c, x, y, w, h, 0.0F, Colors.rgba(8, 34, 46, 175.0F * fa), Colors.rgba(8, 34, 46, 175.0F * fa), Colors.rgba(4, 14, 22, 165.0F * fa), Colors.rgba(4, 14, 22, 165.0F * fa));
        R2D.outline(c, x, y, w, h, 0.0F, 0.5F, cyan(90.0F * fa));
        R2D.rect(c, x, y, w, 1.0F, 0.0F, cyan(230.0F * fa));
        float sweep = (time * 0.35F + index * 0.23F) % 1.0F;
        R2D.rect(c, x, y + sweep * h, w, 0.6F, 0.0F, cyan(28.0F * fa));
        int edge = cyan(220.0F * fa);
        R2D.rect(c, x - 1.0F, y + h - CORNER, 1.0F, CORNER + 1.0F, 0.0F, edge);
        R2D.rect(c, x - 1.0F, y + h, CORNER, 1.0F, 0.0F, edge);
        R2D.rect(c, x + w, y + h - CORNER, 1.0F, CORNER + 1.0F, 0.0F, edge);
        R2D.rect(c, x + w - CORNER + 1.0F, y + h, CORNER, 1.0F, 0.0F, edge);
    }
}
