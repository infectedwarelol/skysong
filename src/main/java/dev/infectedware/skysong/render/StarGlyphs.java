package dev.infectedware.skysong.render;

import com.mojang.blaze3d.platform.NativeImage;
import dev.infectedware.skysong.SkysongClient;
import dev.infectedware.skysong.compat.Ids;
import java.io.InputStream;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

public final class StarGlyphs {
    public static final int GRID = 8;
    private static final Identifier ASCII = Ids.mc("textures/font/ascii.png");
    private static final int FIRST = 33;
    private static final int LAST = 126;
    private static volatile boolean[][] glyphs;
    private static volatile boolean failed;

    private StarGlyphs() {
    }

    public static boolean[] glyph(int codePoint) {
        if (codePoint < FIRST || codePoint > LAST) {
            return null;
        }

        boolean[][] loaded = glyphs;
        if (loaded == null) {
            if (failed) {
                return null;
            }

            loaded = load();
            if (loaded == null) {
                return null;
            }
        }

        return loaded[codePoint];
    }

    public static void clear() {
        glyphs = null;
        failed = false;
    }

    private static synchronized boolean[][] load() {
        if (glyphs != null) {
            return glyphs;
        }

        try (InputStream in = Minecraft.getInstance().getResourceManager().open(ASCII); NativeImage image = NativeImage.read(in)) {
            int cell = image.getWidth() / 16;
            if (cell < GRID || image.getHeight() < cell * 8) {
                failed = true;
                return null;
            }

            boolean[][] out = new boolean[LAST + 1][];

            for (int codePoint = FIRST; codePoint <= LAST; codePoint++) {
                int cellX = codePoint % 16 * cell;
                int cellY = codePoint / 16 * cell;
                boolean[] grid = new boolean[GRID * GRID];
                boolean any = false;

                for (int gy = 0; gy < GRID; gy++) {
                    for (int gx = 0; gx < GRID; gx++) {
                        int px = cellX + gx * cell / GRID + cell / (GRID * 2);
                        int py = cellY + gy * cell / GRID + cell / (GRID * 2);
                        boolean lit = (image.getPixel(px, py) >>> 24) > 0;
                        grid[gy * GRID + gx] = lit;
                        any |= lit;
                    }
                }

                out[codePoint] = any ? grid : null;
            }

            glyphs = out;
            return out;
        } catch (Exception e) {
            failed = true;
            SkysongClient.LOGGER.warn("[Skysong] Could not read the font for constellation lyrics: {}", e.getMessage());
            return null;
        }
    }
}
