package dev.infectedware.skysong.render;

import dev.infectedware.skysong.ModSettings;

public final class AspectRatio {
    public static final String[] PRESETS = new String[]{"4:3", "5:4", "16:10", "3:2", "1:1", "21:9", "Custom"};
    private static final float[][] RATIOS = new float[][]{{4.0F, 3.0F}, {5.0F, 4.0F}, {16.0F, 10.0F}, {3.0F, 2.0F}, {1.0F, 1.0F}, {21.0F, 9.0F}};
    private static boolean hand;

    private AspectRatio() {
    }

    public static float aspect(float windowAspect) {
        return ModSettings.aspectRatio && (!hand || ModSettings.aspectRatioHand) ? target() : windowAspect;
    }

    public static float handAspect(float windowAspect) {
        return ModSettings.aspectRatio && ModSettings.aspectRatioHand ? target() : windowAspect;
    }

    public static float target() {
        int i = ModSettings.aspectRatioPreset;
        return i >= 0 && i < RATIOS.length
            ? RATIOS[i][0] / RATIOS[i][1]
            : Math.max(0.2F, ModSettings.aspectRatioWidth) / Math.max(0.2F, ModSettings.aspectRatioHeight);
    }

    public static void hands(boolean rendering) {
        hand = rendering;
    }
}
