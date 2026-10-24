package dev.infectedware.skysong.render;

public final class RenderUtil {
    private RenderUtil() {
    }

    public static int withAlpha(int argb, float alpha) {
        int a = (int)((argb >>> 24 & 0xFF) * Math.max(0.0F, Math.min(1.0F, alpha)));
        return a << 24 | argb & 16777215;
    }

    public static float clamp01(float v) {
        return v < 0.0F ? 0.0F : Math.min(v, 1.0F);
    }

    public static float easeOutCubic(float t) {
        float u = 1.0F - t;
        return 1.0F - u * u * u;
    }

    public static float easeOutBack(float t, float overshoot) {
        float u = t - 1.0F;
        return 1.0F + (overshoot + 1.0F) * u * u * u + overshoot * u * u;
    }
}
