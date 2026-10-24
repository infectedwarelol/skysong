package dev.infectedware.skysong.theme;

public final class Colors {
    private Colors() {
    }

    public static int rgba(int r, int g, int b, float alpha) {
        int a = Math.max(0, Math.min(255, Math.round(alpha)));
        return a <= 0 ? 0 : a << 24 | (r & 0xFF) << 16 | (g & 0xFF) << 8 | b & 0xFF;
    }

    public static int rgba(int rgb, float alpha) {
        int a = Math.max(0, Math.min(255, Math.round(alpha)));
        return a <= 0 ? 0 : a << 24 | rgb & 16777215;
    }

    public static int multAlpha(int argb, float f) {
        int a = Math.round((argb >>> 24 & 0xFF) * Math.max(0.0F, Math.min(1.0F, f)));
        return a << 24 | argb & 16777215;
    }

    public static int mixRgb(int a, int b, float t) {
        t = Math.max(0.0F, Math.min(1.0F, t));
        int r = Math.round((a >> 16 & 0xFF) + ((b >> 16 & 0xFF) - (a >> 16 & 0xFF)) * t);
        int g = Math.round((a >> 8 & 0xFF) + ((b >> 8 & 0xFF) - (a >> 8 & 0xFF)) * t);
        int bl = Math.round((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t);
        return r << 16 | g << 8 | bl;
    }

    public static int lerp(int a, int b, float t) {
        t = Math.max(0.0F, Math.min(1.0F, t));
        int al = Math.round((a >>> 24) + ((b >>> 24) - (a >>> 24)) * t);
        return al << 24 | mixRgb(a, b, t);
    }

    public static int samplePalette(int[] palette, float t) {
        if (palette != null && palette.length != 0) {
            if (palette.length == 1) {
                return palette[0] & 16777215;
            }

            float f = Math.max(0.0F, Math.min(1.0F, t)) * (palette.length - 1);
            int i = Math.min((int)f, palette.length - 2);
            return mixRgb(palette[i], palette[i + 1], f - i);
        } else {
            return 16777215;
        }
    }

    public static int mixWhite(int argb, float t) {
        return argb & 0xFF000000 | mixRgb(argb, 16777215, t);
    }

    public static int darken(int argb, float factor) {
        int r = Math.round((argb >> 16 & 0xFF) * factor);
        int g = Math.round((argb >> 8 & 0xFF) * factor);
        int b = Math.round((argb & 0xFF) * factor);
        return argb & 0xFF000000 | Math.min(255, r) << 16 | Math.min(255, g) << 8 | Math.min(255, b);
    }
}
