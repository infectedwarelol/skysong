package dev.infectedware.skysong.theme;

import java.awt.Color;
import java.awt.image.BufferedImage;

public final class AlbumPalette {
    private static final int HUE_BINS = 24;
    private static final int MIN_HUE_DISTANCE = 3;
    private static final float MIN_SATURATION = 0.22F;
    private static final float MIN_BRIGHTNESS = 0.2F;
    private static final int FALLBACK_PRIMARY = 0xFFFFFFFF;
    private static final int FALLBACK_SECONDARY = 0xFF9B5CFF;

    private AlbumPalette() {
    }

    public static int[] extract(BufferedImage image) {
        if (image == null || image.getWidth() == 0 || image.getHeight() == 0) {
            return new int[]{FALLBACK_PRIMARY, FALLBACK_SECONDARY};
        }

        double[] weight = new double[HUE_BINS];
        double[] red = new double[HUE_BINS];
        double[] green = new double[HUE_BINS];
        double[] blue = new double[HUE_BINS];
        double averageRed = 0.0;
        double averageGreen = 0.0;
        double averageBlue = 0.0;
        int samples = 0;
        float[] hsb = new float[3];
        int step = Math.max(1, Math.min(image.getWidth(), image.getHeight()) / 64);

        for (int y = 0; y < image.getHeight(); y += step) {
            for (int x = 0; x < image.getWidth(); x += step) {
                int argb = image.getRGB(x, y);
                if ((argb >>> 24) < 128) {
                    continue;
                }

                int r = argb >> 16 & 0xFF;
                int g = argb >> 8 & 0xFF;
                int b = argb & 0xFF;
                averageRed += r;
                averageGreen += g;
                averageBlue += b;
                samples++;
                Color.RGBtoHSB(r, g, b, hsb);
                if (hsb[1] < MIN_SATURATION || hsb[2] < MIN_BRIGHTNESS) {
                    continue;
                }

                int bin = Math.min(HUE_BINS - 1, (int)(hsb[0] * HUE_BINS));
                double w = hsb[1] * hsb[2] * hsb[2];
                weight[bin] += w;
                red[bin] += r * w;
                green[bin] += g * w;
                blue[bin] += b * w;
            }
        }

        if (samples == 0) {
            return new int[]{FALLBACK_PRIMARY, FALLBACK_SECONDARY};
        }

        int primaryBin = strongest(weight, -1);
        if (primaryBin < 0) {
            int average = rgb(averageRed / samples, averageGreen / samples, averageBlue / samples);
            int lifted = Colors.mixWhite(average, 0.4F);
            return new int[]{lifted, Colors.mixWhite(lifted, 0.6F)};
        }

        int primary = vivid(rgb(red[primaryBin] / weight[primaryBin], green[primaryBin] / weight[primaryBin], blue[primaryBin] / weight[primaryBin]));
        int secondaryBin = strongest(weight, primaryBin);
        int secondary = secondaryBin < 0
            ? Colors.mixWhite(primary, 0.45F)
            : vivid(rgb(red[secondaryBin] / weight[secondaryBin], green[secondaryBin] / weight[secondaryBin], blue[secondaryBin] / weight[secondaryBin]));
        return new int[]{primary, secondary};
    }

    private static int strongest(double[] weight, int awayFrom) {
        int best = -1;
        double bestWeight = 0.0;

        for (int i = 0; i < weight.length; i++) {
            if (awayFrom >= 0) {
                int distance = Math.abs(i - awayFrom);
                if (Math.min(distance, weight.length - distance) < MIN_HUE_DISTANCE) {
                    continue;
                }
            }

            if (weight[i] > bestWeight) {
                bestWeight = weight[i];
                best = i;
            }
        }

        if (awayFrom >= 0 && best >= 0 && bestWeight < weight[awayFrom] * 0.08) {
            return -1;
        }

        return best;
    }

    private static int vivid(int argb) {
        float[] hsb = Color.RGBtoHSB(argb >> 16 & 0xFF, argb >> 8 & 0xFF, argb & 0xFF, null);
        float saturation = Math.min(1.0F, Math.max(hsb[1], 0.35F) * 1.1F);
        float brightness = Math.max(hsb[2], 0.75F);
        return 0xFF000000 | Color.HSBtoRGB(hsb[0], saturation, brightness) & 0xFFFFFF;
    }

    private static int rgb(double r, double g, double b) {
        int red = (int)Math.round(Math.max(0.0, Math.min(255.0, r)));
        int green = (int)Math.round(Math.max(0.0, Math.min(255.0, g)));
        int blueValue = (int)Math.round(Math.max(0.0, Math.min(255.0, b)));
        return 0xFF000000 | red << 16 | green << 8 | blueValue;
    }
}
