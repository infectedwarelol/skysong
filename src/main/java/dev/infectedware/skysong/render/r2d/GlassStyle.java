package dev.infectedware.skysong.render.r2d;

import dev.infectedware.skysong.ModSettings;
import dev.infectedware.skysong.theme.ClientAccent;
import dev.infectedware.skysong.theme.Colors;

final class GlassStyle {
    private static final int[] palette = new int[6];
    private static long lastUpdateNanos = -1L;
    private static long lastMs;
    private static float phase;
    private static float offset;
    private static float movementSpeed;
    private static float glow;
    private static int lastStyle = -1;
    private static int prevStyle;
    private static long sweepStart = -1L;

    private GlassStyle() {
    }

    static void update() {
        long nanos = System.nanoTime();
        if (nanos - lastUpdateNanos >= 2000000L) {
            lastUpdateNanos = nanos;
            long now = System.currentTimeMillis();
            float dtMs = lastMs == 0L ? 0.0F : (float)Math.min(100L, now - lastMs);
            lastMs = now;
            int[] src = ClientAccent.palette();

            for (int i = 0; i < 6; i++) {
                palette[i] = Colors.samplePalette(src, i / 5.0F);
            }

            boolean moving = ModSettings.rectColorMovement && ClientAccent.usesDual();
            float target = moving ? 1.0F : 0.0F;
            movementSpeed = movementSpeed + (target - movementSpeed) * Math.min(1.0F, dtMs / (moving ? 450.0F : 700.0F));
            offset = frac(offset + dtMs * movementSpeed / 2400.0F);
            if (moving) {
                phase = frac(phase + dtMs / 24000.0F);
            }

            int style = ModSettings.gradientStyle;
            if (lastStyle >= 0 && style != lastStyle) {
                prevStyle = lastStyle;
                sweepStart = now;
            }

            lastStyle = style;
            float glowTarget = ModSettings.rectGlow ? 1.0F : 0.0F;
            glow = glow + (glowTarget - glow) * Math.min(1.0F, dtMs / 500.0F * 3.0F);
            if (Math.abs(glow - glowTarget) < 0.002F) {
                glow = glowTarget;
            }
        }
    }

    static int[] palette() {
        return palette;
    }

    static float phase() {
        return phase;
    }

    static float colorOffset() {
        return offset;
    }

    static float closed() {
        return ClientAccent.closedFactor();
    }

    static float styleId() {
        return lastStyle < 0 ? ModSettings.gradientStyle : lastStyle;
    }

    static float previousStyle() {
        return prevStyle;
    }

    static float sweep() {
        if (sweepStart < 0L) {
            return -1.0F;
        } else {
            float t = (float)(System.currentTimeMillis() - sweepStart) / 900.0F;
            if (t >= 1.0F) {
                sweepStart = -1L;
                return -1.0F;
            } else {
                return t;
            }
        }
    }

    static float glowBlend() {
        return glow;
    }

    private static float frac(float f) {
        return f - (float)Math.floor(f);
    }
}
