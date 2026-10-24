package dev.infectedware.skysong.theme;

import dev.infectedware.skysong.ModConfig;
import dev.infectedware.skysong.ModSettings;

public final class ThemeManager {
    private static final long SWITCH_MS = 500L;
    private static ClientTheme current = ClientTheme.byName(ModSettings.theme);
    private static float[] fromShades = unpack(current.shades());
    private static int[] fromPalette = current.palette();
    private static long switchStart = System.currentTimeMillis() - 10000L;

    private ThemeManager() {
    }

    public static ClientTheme current() {
        if (!current.name().equals(ModSettings.theme)) {
            ClientTheme saved = ClientTheme.byName(ModSettings.theme);
            if (saved != current) {
                current = saved;
                fromShades = unpack(saved.shades());
                fromPalette = saved.palette();
            }
        }

        return current;
    }

    public static void set(ClientTheme theme) {
        if (theme != null && theme != current()) {
            fromShades = blendedShades();
            fromPalette = blendedPalette();
            current = theme;
            ModSettings.theme = theme.name();
            switchStart = System.currentTimeMillis();
            ModConfig.save();
        }
    }

    public static int accent(float alpha) {
        return shade(0, alpha);
    }

    public static int accentBright(float alpha) {
        return shade(1, alpha);
    }

    public static int accentSoft(float alpha) {
        return shade(2, alpha);
    }

    public static int accentFill(float alpha) {
        return shade(3, alpha);
    }

    public static int toggleOn(float alpha) {
        return shade(4, alpha);
    }

    public static int gradientA(float alpha) {
        return shade(5, alpha);
    }

    public static int gradientB(float alpha) {
        return shade(6, alpha);
    }

    static int shade(int i, float alpha) {
        int a = Math.max(0, Math.min(255, Math.round(alpha)));
        if (a <= 0) {
            return 0;
        }

        int target = current().shades()[i];
        float t = progress();
        if (t >= 1.0F) {
            return a << 24 | target & 16777215;
        }

        int r = Math.round(fromShades[i * 3] + ((target >> 16 & 0xFF) - fromShades[i * 3]) * t);
        int g = Math.round(fromShades[i * 3 + 1] + ((target >> 8 & 0xFF) - fromShades[i * 3 + 1]) * t);
        int b = Math.round(fromShades[i * 3 + 2] + ((target & 0xFF) - fromShades[i * 3 + 2]) * t);
        return a << 24 | r << 16 | g << 8 | b;
    }

    public static int[] blendedPalette() {
        int[] target = current().palette();
        float t = progress();
        if (t >= 1.0F) {
            return target;
        }

        int n = Math.max(target.length, fromPalette.length);
        int[] out = new int[n];

        for (int i = 0; i < n; i++) {
            float f = n <= 1 ? 0.0F : (float)i / (n - 1);
            out[i] = Colors.mixRgb(Colors.samplePalette(fromPalette, f), Colors.samplePalette(target, f), t);
        }

        return out;
    }

    static int[] currentShades() {
        return new int[]{
            shade(0, 255.0F) & 16777215,
            shade(1, 255.0F) & 16777215,
            shade(2, 255.0F) & 16777215,
            shade(3, 255.0F) & 16777215,
            shade(4, 255.0F) & 16777215,
            shade(5, 255.0F) & 16777215,
            shade(6, 255.0F) & 16777215
        };
    }

    private static float[] blendedShades() {
        return unpack(currentShades());
    }

    static float[] unpack(int[] shades) {
        float[] f = new float[shades.length * 3];

        for (int i = 0; i < shades.length; i++) {
            f[i * 3] = shades[i] >> 16 & 0xFF;
            f[i * 3 + 1] = shades[i] >> 8 & 0xFF;
            f[i * 3 + 2] = shades[i] & 0xFF;
        }

        return f;
    }

    private static float progress() {
        float t = (float)(System.currentTimeMillis() - switchStart) / 500.0F;
        if (t <= 0.0F) {
            return 0.0F;
        } else {
            return t >= 1.0F ? 1.0F : t * t * (3.0F - 2.0F * t);
        }
    }
}
