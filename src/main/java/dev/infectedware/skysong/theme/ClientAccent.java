package dev.infectedware.skysong.theme;

import dev.infectedware.skysong.ModSettings;
import java.awt.Color;

public final class ClientAccent {
    private static final long SWITCH_MS = 500L;
    private static final long RAINBOW_CYCLE_MS = 6000L;
    private static ClientAccent.Mode mode = ClientAccent.Mode.THEMES;
    private static float[] fromShades = ThemeManager.unpack(ClientTheme.SKYSONG.shades());
    private static int[] fromPalette = ClientTheme.SKYSONG.palette();
    private static float fromClosed;
    private static long switchStart = System.currentTimeMillis() - 10000L;
    private static int lastCustom;
    private static int lastCustom2;
    private static boolean lastCustomSecond;
    private static double huePhase;
    private static long hueLast;
    private static long frameId;
    private static long paletteFrame = -1L;
    private static int[] framePalette;

    private ClientAccent() {
    }

    public static void beginFrame() {
        long now = System.currentTimeMillis();
        if (hueLast == 0L || now - hueLast >= 2L) {
            if (hueLast != 0L) {
                long dt = Math.clamp(now - hueLast, 0L, 200L);
                huePhase = huePhase + (float)dt * Math.max(0.1F, ModSettings.rainbowSpeed) / 6000.0;
                huePhase = huePhase - Math.floor(huePhase);
            }

            hueLast = now;
            frameId++;
        }
    }

    public static float rainbowBaseHue() {
        return (float)huePhase;
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

    public static int accentOpaque() {
        return 0xFF000000 | shade(0, 255.0F) & 16777215;
    }

    public static int gradientColor(float t, float alpha) {
        return Colors.rgba(Colors.samplePalette(palette(), t), alpha);
    }

    public static int rainbowFlow(float offset, float alpha) {
        float h = (float)huePhase + offset;
        h -= (float)Math.floor(h);
        return Colors.rgba(Color.HSBtoRGB(h, ModSettings.rainbowSaturation, 1.0F) & 16777215, alpha);
    }

    public static int[] palette() {
        if (paletteFrame == frameId && framePalette != null) {
            return framePalette;
        }

        refresh();
        int[] target = desiredPalette();
        float t = progress();
        int[] result = target;
        if (t < 1.0F) {
            int n = Math.max(fromPalette.length, target.length);
            result = new int[n];

            for (int i = 0; i < n; i++) {
                float f = n <= 1 ? 0.0F : (float)i / (n - 1);
                result[i] = Colors.mixRgb(Colors.samplePalette(fromPalette, f), Colors.samplePalette(target, f), t);
            }
        }

        framePalette = result;
        paletteFrame = frameId;
        return result;
    }

    public static float closedFactor() {
        refresh();
        float target = mode == ClientAccent.Mode.RAINBOW ? 1.0F : 0.0F;
        return fromClosed + (target - fromClosed) * progress();
    }

    public static boolean isRainbow() {
        return ModSettings.clientColorMode == 2;
    }

    public static boolean isThemes() {
        return ModSettings.clientColorMode == 0;
    }

    public static boolean usesDual() {
        return isThemes() ? ThemeManager.current().gradientA() != ThemeManager.current().gradientB() : isRainbow() || ModSettings.rectUseSecondColor;
    }

    private static int shade(int i, float alpha) {
        refresh();
        if (mode == ClientAccent.Mode.THEMES && progress() >= 1.0F) {
            return ThemeManager.shade(i, alpha);
        }

        int a = Math.clamp(Math.round(alpha), 0, 255);
        if (a <= 0) {
            return 0;
        }

        int target = targetShades()[i];
        float t = progress();
        if (t >= 1.0F) {
            return a << 24 | target & 16777215;
        }

        int r = Math.round(fromShades[i * 3] + ((target >> 16 & 0xFF) - fromShades[i * 3]) * t);
        int g = Math.round(fromShades[i * 3 + 1] + ((target >> 8 & 0xFF) - fromShades[i * 3 + 1]) * t);
        int b = Math.round(fromShades[i * 3 + 2] + ((target & 0xFF) - fromShades[i * 3 + 2]) * t);
        return a << 24 | r << 16 | g << 8 | b;
    }

    private static int[] targetShades() {
        return switch (mode) {
            case THEMES -> ThemeManager.currentShades();
            case CUSTOM -> customShades();
            case RAINBOW -> rainbowShades();
        };
    }

    private static int[] desiredPalette() {
        return switch (mode) {
            case THEMES -> ThemeManager.blendedPalette();
            case CUSTOM -> ModSettings.rectUseSecondColor
                ? new int[]{ModSettings.rectColor & 16777215, ModSettings.rectSecondColor & 16777215}
                : new int[]{ModSettings.rectColor & 16777215};
            case RAINBOW -> rainbowPalette();
        };
    }

    private static void refresh() {
        ClientAccent.Mode wanted = switch (ModSettings.clientColorMode) {
            case 1 -> ClientAccent.Mode.CUSTOM;
            case 2 -> ClientAccent.Mode.RAINBOW;
            default -> ClientAccent.Mode.THEMES;
        };
        boolean customChanged = wanted == ClientAccent.Mode.CUSTOM
            && (ModSettings.rectColor != lastCustom || ModSettings.rectSecondColor != lastCustom2 || ModSettings.rectUseSecondColor != lastCustomSecond);
        lastCustom = ModSettings.rectColor;
        lastCustom2 = ModSettings.rectSecondColor;
        lastCustomSecond = ModSettings.rectUseSecondColor;
        if (wanted != mode || customChanged) {
            fromShades = ThemeManager.unpack(currentShadesNow());
            fromPalette = framePalette != null ? framePalette.clone() : desiredPalette();
            fromClosed = mode == ClientAccent.Mode.RAINBOW ? 1.0F : 0.0F;
            mode = wanted;
            switchStart = System.currentTimeMillis();
            paletteFrame = -1L;
        }
    }

    private static int[] currentShadesNow() {
        int[] s = new int[7];

        for (int i = 0; i < 7; i++) {
            s[i] = shadeNoRefresh(i);
        }

        return s;
    }

    private static int shadeNoRefresh(int i) {
        int target = targetShades()[i];
        float t = progress();
        if (t >= 1.0F) {
            return target & 16777215;
        }

        int r = Math.round(fromShades[i * 3] + ((target >> 16 & 0xFF) - fromShades[i * 3]) * t);
        int g = Math.round(fromShades[i * 3 + 1] + ((target >> 8 & 0xFF) - fromShades[i * 3 + 1]) * t);
        int b = Math.round(fromShades[i * 3 + 2] + ((target & 0xFF) - fromShades[i * 3 + 2]) * t);
        return r << 16 | g << 8 | b;
    }

    private static int[] customShades() {
        int base = ModSettings.rectColor & 16777215;
        int second = ModSettings.rectUseSecondColor ? ModSettings.rectSecondColor & 16777215 : base;
        return new int[]{
            base,
            ClientTheme.lighten(base, 0.65F, 1.15F, 0.1F),
            ClientTheme.lighten(base, 0.45F, 1.25F, 0.15F),
            ClientTheme.darken(base, 1.05F, 0.78F),
            ClientTheme.darken(base, 1.1F, 0.55F),
            base,
            second
        };
    }

    private static int[] rainbowShades() {
        float h = (float)huePhase;
        float s = ModSettings.rainbowSaturation;
        int a = Color.HSBtoRGB(h, s, 1.0F) & 16777215;
        float h2 = h + ModSettings.rainbowSpread;
        int b = Color.HSBtoRGB(h2 - (float)Math.floor(h2), s, 1.0F) & 16777215;
        return new int[]{
            a,
            Color.HSBtoRGB(h, s * 0.7F, 1.0F) & 16777215,
            Color.HSBtoRGB(h, s * 0.5F, 1.0F) & 16777215,
            Color.HSBtoRGB(h, s, 0.8F) & 16777215,
            Color.HSBtoRGB(h, s, 0.6F) & 16777215,
            a,
            b
        };
    }

    private static int[] rainbowPalette() {
        int[] p = new int[9];
        float spread = Math.min(1.0F, ModSettings.rainbowSpread * 4.0F);

        for (int i = 0; i < p.length; i++) {
            float h = (float)huePhase + (float)i / (p.length - 1) * spread;
            h -= (float)Math.floor(h);
            int rgb = Color.HSBtoRGB(h, 1.0F, 1.0F) & 16777215;
            p[i] = Colors.mixRgb(16777215, rgb, ModSettings.rainbowSaturation);
        }

        return p;
    }

    private static float progress() {
        float t = (float)(System.currentTimeMillis() - switchStart) / 500.0F;
        if (t <= 0.0F) {
            return 0.0F;
        } else {
            return t >= 1.0F ? 1.0F : t * t * (3.0F - 2.0F * t);
        }
    }

    private enum Mode {
        THEMES,
        CUSTOM,
        RAINBOW;
    }
}
