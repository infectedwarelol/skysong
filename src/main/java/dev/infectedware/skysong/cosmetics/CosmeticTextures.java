package dev.infectedware.skysong.cosmetics;

import com.mojang.blaze3d.platform.NativeImage;
import dev.infectedware.skysong.ModSettings;
import dev.infectedware.skysong.SkysongClient;
import dev.infectedware.skysong.compat.Compat;
import dev.infectedware.skysong.compat.Ids;
import dev.infectedware.skysong.theme.Colors;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

public final class CosmeticTextures {
    public static final Path CAPE_DIR = FabricLoader.getInstance().getConfigDir().resolve("skysong").resolve("capes");
    private static final int WING_SIZE = 64;
    private static final int CAPE_W = 64;
    private static final int CAPE_H = 32;
    private static final long CAPE_REFRESH_MS = 100L;
    private static final Map<Integer, Identifier> WINGS = new HashMap<>();
    private static Identifier white;
    private static DynamicTexture capeTexture;
    private static Identifier capeId;
    private static long capeKey = Long.MIN_VALUE;
    private static long capeCheckedAt;
    private static String imageName;
    private static Identifier imageId;
    private static String imageProblem = "";

    private CosmeticTextures() {
    }

    static Identifier white() {
        if (white == null) {
            NativeImage image = new NativeImage(NativeImage.Format.RGBA, 16, 16, false);
            image.fillRect(0, 0, 16, 16, -1);
            white = register("white", image);
        }

        return white;
    }

    static Identifier wings(int style) {
        return WINGS.computeIfAbsent(style, s -> register("wings_" + s, paintWings(s)));
    }

    public static Identifier cape() {
        if (ModSettings.cosmeticCapeStyle == Cosmetics.CAPE_IMAGE) {
            return capeImage();
        }

        long now = System.currentTimeMillis();
        if (capeTexture != null && now - capeCheckedAt < CAPE_REFRESH_MS) {
            return capeId;
        }

        capeCheckedAt = now;
        int c1 = Cosmetics.color(ModSettings.cosmeticCapeColorMode, ModSettings.cosmeticCapeColor, ModSettings.cosmeticCapeColor2, 0.0F);
        int c2 = Cosmetics.color(ModSettings.cosmeticCapeColorMode, ModSettings.cosmeticCapeColor, ModSettings.cosmeticCapeColor2, 1.0F);
        long key = ((long)ModSettings.cosmeticCapeStyle << 48) ^ ((long)(c1 & 0xFFFFFF) << 24) ^ (c2 & 0xFFFFFF);
        if (capeTexture == null) {
            capeTexture = Compat.dynamicTexture("cape", new NativeImage(NativeImage.Format.RGBA, CAPE_W, CAPE_H, false));
            capeId = Ids.of("skysong", "cosmetics/cape");
            Minecraft.getInstance().getTextureManager().register(capeId, capeTexture);
        }

        if (key != capeKey && capeTexture.getPixels() != null) {
            capeKey = key;
            paintCape(capeTexture.getPixels(), ModSettings.cosmeticCapeStyle, c1, c2);
            capeTexture.upload();
        }

        return capeId;
    }

    public static List<String> capeImages() {
        List<String> names = new ArrayList<>();
        try {
            Files.createDirectories(CAPE_DIR);
            try (Stream<Path> files = Files.list(CAPE_DIR)) {
                files.map(p -> p.getFileName().toString()).filter(n -> n.toLowerCase(Locale.ROOT).endsWith(".png")).sorted().forEach(names::add);
            }
        } catch (IOException e) {
            SkysongClient.LOGGER.warn("[Skysong] Could not list capes: {}", e.getMessage());
        }

        return names;
    }

    public static String capeProblem() {
        return ModSettings.cosmeticCapeStyle == Cosmetics.CAPE_IMAGE ? imageProblem : "";
    }

    public static void reloadCapeImage() {
        imageName = null;
    }

    private static Identifier capeImage() {
        String wanted = ModSettings.cosmeticCapeImage;
        if (wanted.equals(imageName)) {
            return imageId;
        }

        imageName = wanted;
        imageId = null;
        imageProblem = "";
        if (wanted.isBlank()) {
            imageProblem = "Pick a cape image first.";
            return null;
        }

        Path file = CAPE_DIR.resolve(wanted).normalize();
        if (!file.startsWith(CAPE_DIR) || !Files.isRegularFile(file)) {
            imageProblem = wanted + " is missing.";
            return null;
        }

        try (InputStream in = Files.newInputStream(file)) {
            NativeImage image = NativeImage.read(in);
            if (image.getWidth() != image.getHeight() * 2 || image.getWidth() < CAPE_W || image.getWidth() > 4096) {
                imageProblem = "Use a 64x32 cape layout (or 128x64, 256x128...).";
                image.close();
                return null;
            }

            Identifier id = Ids.of("skysong", "cosmetics/cape_image");
            Minecraft.getInstance().getTextureManager().register(id, Compat.dynamicTexture("cape image", image));
            imageId = id;
        } catch (IOException | RuntimeException e) {
            imageProblem = "Couldn't read " + wanted + ".";
            SkysongClient.LOGGER.warn("[Skysong] Could not load cape {}: {}", wanted, e.getMessage());
        }

        return imageId;
    }

    private static Identifier register(String name, NativeImage image) {
        Identifier id = Ids.of("skysong", "cosmetics/" + name);
        Minecraft.getInstance().getTextureManager().register(id, Compat.dynamicTexture(name, image));
        return id;
    }

    private static NativeImage paintWings(int style) {
        boolean[][] mask = new boolean[WING_SIZE][WING_SIZE];
        int[][] shade = new int[WING_SIZE][WING_SIZE];
        int[][] alpha = new int[WING_SIZE][WING_SIZE];

        for (int y = 0; y < WING_SIZE; y++) {
            for (int x = 0; x < WING_SIZE; x++) {
                float u = (x + 0.5F) / WING_SIZE;
                float v = (y + 0.5F) / WING_SIZE;
                int[] px = switch (style) {
                    case 1 -> bat(u, v, false);
                    case 2 -> fairy(u, v, false);
                    case 3 -> fairy(u, v, true);
                    case 4 -> bat(u, v, true);
                    case 5 -> neon(u, v);
                    default -> feathers(u, v);
                };
                if (px != null) {
                    mask[y][x] = true;
                    shade[y][x] = px[0];
                    alpha[y][x] = px[1];
                }
            }
        }

        NativeImage image = new NativeImage(NativeImage.Format.RGBA, WING_SIZE, WING_SIZE, false);
        for (int y = 0; y < WING_SIZE; y++) {
            for (int x = 0; x < WING_SIZE; x++) {
                if (!mask[y][x]) {
                    image.setPixel(x, y, 0);
                    continue;
                }

                int g = shade[y][x];
                int a = alpha[y][x];
                if (edge(mask, x, y)) {
                    g = style == 2 || style == 5 ? 255 : Math.round(g * 0.72F);
                    a = Math.max(a, 235);
                }

                image.setPixel(x, y, a << 24 | g << 16 | g << 8 | g);
            }
        }

        return image;
    }

    private static boolean edge(boolean[][] mask, int x, int y) {
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                int nx = x + dx;
                int ny = y + dy;
                if (nx < 0 || ny < 0 || nx >= WING_SIZE || ny >= WING_SIZE || !mask[ny][nx]) {
                    return true;
                }
            }
        }

        return false;
    }

    private static int[] feathers(float u, float v) {
        float top = 0.34F - 0.3F * Mth.sin((float)Math.PI * Math.min(u, 0.98F) * 0.88F);
        float base = 0.95F - 0.62F * (float)Math.pow(u, 1.25);
        float tips = 0.06F * (1.0F - Math.abs(Mth.sin(u * (float)Math.PI * 8.0F)));
        float bottom = base - tips;
        if (u > 0.97F || v < top || v > bottom) {
            return null;
        }

        float d = (v - top) / Math.max(0.01F, bottom - top);
        float lane = frac(u * 8.0F + d * 1.4F);
        int g = 238;
        if (lane < 0.1F) {
            g = 196;
        }

        if (Math.abs(d - 0.32F) < 0.025F || Math.abs(d - 0.56F) < 0.025F) {
            g = 205;
        }

        if (d < 0.32F) {
            g = Math.min(255, g + 12);
        }

        return new int[]{g, 255};
    }

    private static int[] bat(float u, float v, boolean dragon) {
        float[][] tips = {{1.0F, 0.06F}, {0.97F, 0.46F}, {0.76F, 0.74F}, {0.5F, 0.9F}, {0.22F, 0.92F}};
        float wx = 0.4F;
        float wy = 0.1F;
        float[][] outline = {{0.0F, 0.3F}, {wx, wy}, tips[0], tips[1], tips[2], tips[3], tips[4], {0.0F, 0.72F}};
        boolean spike = dragon && v < wy + (u - wx) * -0.04F && v > wy - 0.09F && u > 0.08F && frac(u * 9.0F) < 0.5F - (wy - v) * 5.0F;
        if (!inside(outline, u, v) && !spike) {
            return null;
        }

        for (int i = 1; i < tips.length; i++) {
            float[] p = tips[i - 1];
            float[] q = tips[i];
            float len = (float)Math.hypot(q[0] - p[0], q[1] - p[1]);
            float mx = (p[0] + q[0]) / 2.0F;
            float my = (p[1] + q[1]) / 2.0F;
            float ox = mx - wx;
            float oy = my - wy;
            float ol = (float)Math.hypot(ox, oy);
            float cx = mx + ox / ol * len * 0.42F;
            float cy = my + oy / ol * len * 0.42F;
            if (i > 1 && Math.hypot(u - cx, v - cy) < len * 0.62F) {
                return null;
            }
        }

        float bone = segment(u, v, 0.0F, 0.3F, wx, wy);
        for (float[] t : tips) {
            bone = Math.min(bone, segment(u, v, wx, wy, t[0], t[1]));
        }

        if (spike || bone < 0.022F) {
            return new int[]{dragon ? 200 : 215, 255};
        }

        int g = 118 + Math.round(30.0F * (1.0F - v));
        if (dragon && (((int)(u * 22.0F) + (int)(v * 22.0F)) & 1) == 0) {
            g -= 14;
        }

        return new int[]{g, dragon ? 250 : 232};
    }

    private static int[] neon(float u, float v) {
        float[][] tips = {{1.0F, 0.12F}, {0.86F, 0.52F}, {0.6F, 0.66F}, {0.34F, 0.7F}};
        float jx = 0.22F;
        float jy = 0.12F;
        float[][] outline = {{0.0F, 0.32F}, {jx, jy}, tips[0], tips[1], tips[2], tips[3], {0.0F, 0.62F}};
        if (!inside(outline, u, v)) {
            return null;
        }

        for (int i = 1; i < tips.length; i++) {
            float[] p = tips[i - 1];
            float[] q = tips[i];
            float len = (float)Math.hypot(q[0] - p[0], q[1] - p[1]);
            float mx = (p[0] + q[0]) / 2.0F;
            float my = (p[1] + q[1]) / 2.0F;
            if (Math.hypot(u - mx, v - (my + len * 0.38F)) < len * 0.55F) {
                return null;
            }
        }

        float rib = segment(u, v, 0.0F, 0.32F, jx, jy);
        for (int i = 1; i < tips.length; i++) {
            rib = Math.min(rib, segment(u, v, jx, jy, tips[i][0], tips[i][1]));
        }

        if (rib < 0.012F) {
            return new int[]{255, 255};
        }

        return new int[]{255, Math.round(165.0F + 55.0F * (1.0F - v))};
    }

    private static int[] fairy(float u, float v, boolean butterfly) {
        float upper = ellipse(u, v, 0.5F, 0.3F, 0.5F, 0.27F, -0.25F);
        float lower = ellipse(u, v, 0.33F, 0.68F, 0.3F, 0.22F, 0.45F);
        float inner = Math.min(upper, lower);
        if (inner > 1.0F || u < 0.0F) {
            return null;
        }

        if (butterfly) {
            if (inner > 0.8F) {
                return new int[]{70, 255};
            }

            if (Math.hypot(u - 0.72F, v - 0.24F) < 0.075F || Math.hypot(u - 0.36F, v - 0.72F) < 0.06F) {
                return new int[]{255, 255};
            }

            float vein = Math.min(segment(u, v, 0.0F, 0.45F, 0.95F, 0.2F), segment(u, v, 0.0F, 0.45F, 0.55F, 0.86F));
            return new int[]{vein < 0.012F ? 120 : 215, 255};
        }

        float vein = Math.min(
            Math.min(segment(u, v, 0.0F, 0.45F, 0.95F, 0.18F), segment(u, v, 0.0F, 0.45F, 0.7F, 0.42F)), segment(u, v, 0.0F, 0.45F, 0.5F, 0.84F)
        );
        int a = Math.round(110.0F + 110.0F * inner * inner);
        return vein < 0.012F ? new int[]{255, 235} : new int[]{255, a};
    }

    private static float ellipse(float u, float v, float cx, float cy, float rx, float ry, float angle) {
        float dx = u - cx;
        float dy = v - cy;
        float cos = Mth.cos(angle);
        float sin = Mth.sin(angle);
        float x = dx * cos + dy * sin;
        float y = -dx * sin + dy * cos;
        return (float)Math.sqrt(x * x / (rx * rx) + y * y / (ry * ry));
    }

    private static float segment(float px, float py, float ax, float ay, float bx, float by) {
        float dx = bx - ax;
        float dy = by - ay;
        float t = Math.clamp(((px - ax) * dx + (py - ay) * dy) / (dx * dx + dy * dy), 0.0F, 1.0F);
        return (float)Math.hypot(px - (ax + t * dx), py - (ay + t * dy));
    }

    private static boolean inside(float[][] poly, float x, float y) {
        boolean in = false;
        for (int i = 0, j = poly.length - 1; i < poly.length; j = i++) {
            if ((poly[i][1] > y) != (poly[j][1] > y) && x < (poly[j][0] - poly[i][0]) * (y - poly[i][1]) / (poly[j][1] - poly[i][1]) + poly[i][0]) {
                in = !in;
            }
        }

        return in;
    }

    private static void paintCape(NativeImage image, int style, int c1, int c2) {
        for (int y = 0; y < CAPE_H; y++) {
            for (int x = 0; x < CAPE_W; x++) {
                float u;
                float v;
                if (x < 22 && y < 17) {
                    u = x < 11 ? (x - 1 + 0.5F) / 10.0F : (21 - x + 0.5F) / 10.0F;
                    v = (y - 1 + 0.5F) / 16.0F;
                } else if (x >= 22 && x < 46 && y < 22) {
                    u = (x - 22 + 0.5F) / 24.0F;
                    v = (y + 0.5F) / 22.0F;
                } else {
                    image.setPixel(x, y, 0);
                    continue;
                }

                u = Math.clamp(u, 0.0F, 1.0F);
                v = Math.clamp(v, 0.0F, 1.0F);
                int color = capePixel(style, x, y, u, v, c1, c2);
                boolean border = x < 22 && y < 17 && (x == 1 || x == 10 || x == 12 || x == 21 || y == 16);
                image.setPixel(x, y, 0xFF000000 | (border ? Colors.darken(color, 0.7F) : color) & 0xFFFFFF);
            }
        }
    }

    private static int capePixel(int style, int x, int y, float u, float v, int c1, int c2) {
        return switch (style) {
            case 1 -> Colors.lerp(c1, c2, v + (hash(x, y) - 0.5F) * 0.06F);
            case 2 -> {
                float n = noise(u * 5.0F, v * 3.0F);
                float flame = 1.0F - v + 0.22F * Mth.sin(u * 13.0F + v * 6.0F) + n * 0.25F;
                yield flame > 0.85F ? Colors.darken(c2, 0.35F) : Colors.lerp(c1, Colors.mixWhite(c2, 0.35F), Math.clamp(flame / 0.85F, 0.0F, 1.0F));
            }
            case 3 -> Mth.sin(v * (float)Math.PI * 7.0F + Mth.sin(u * (float)Math.PI * 2.0F) * 1.4F) > 0.0F ? c1 : c2;
            case 4 -> ((x + y) / 3 & 1) == 0 ? c1 : c2;
            case 5 -> ((int)(u * 5.0F) + (int)(v * 8.0F) & 1) == 0 ? c1 : c2;
            default -> {
                int space = Colors.lerp(0xFF07071A, Colors.darken(c1, 0.35F), v * 0.7F);
                float neb = Math.clamp(noise(u * 3.0F, v * 4.0F) * 1.4F - 0.35F, 0.0F, 1.0F);
                int color = Colors.lerp(space, Colors.lerp(c1, c2, u), neb * 0.75F);
                float star = hash(x, y);
                yield star > 0.93F ? Colors.mixWhite(color, 0.6F + (star - 0.93F) * 5.0F) : color;
            }
        };
    }

    private static float frac(float f) {
        return f - (float)Math.floor(f);
    }

    private static float hash(int x, int y) {
        int h = x * 374761393 + y * 668265263;
        h = (h ^ h >>> 13) * 1274126177;
        return ((h ^ h >>> 16) & 0xFFFF) / 65535.0F;
    }

    private static float noise(float x, float y) {
        int x0 = (int)Math.floor(x);
        int y0 = (int)Math.floor(y);
        float fx = x - x0;
        float fy = y - y0;
        float sx = fx * fx * (3.0F - 2.0F * fx);
        float sy = fy * fy * (3.0F - 2.0F * fy);
        float a = Mth.lerp(sx, hash(x0, y0), hash(x0 + 1, y0));
        float b = Mth.lerp(sx, hash(x0, y0 + 1), hash(x0 + 1, y0 + 1));
        return Mth.lerp(sy, a, b);
    }
}
