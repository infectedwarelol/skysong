package dev.infectedware.skysong.render.r2d;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.infectedware.skysong.SkysongClient;
import dev.infectedware.skysong.compat.Ids;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

public final class MsdfFont {
    private static final MsdfFont.Glyph EMPTY = new MsdfFont.Glyph(false, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);
    final Identifier atlas;
    final float ascender;
    final float descender;
    final float lineHeight;
    final float distanceRange;
    private final Map<Integer, MsdfFont.Glyph> glyphs;
    private final Map<Long, Float> kerning;
    private final MsdfFont.Glyph fallback;

    private MsdfFont(
        Identifier atlas, float ascender, float descender, float lineHeight, float distanceRange, Map<Integer, MsdfFont.Glyph> glyphs, Map<Long, Float> kerning
    ) {
        this.atlas = atlas;
        this.ascender = ascender;
        this.descender = descender;
        this.lineHeight = lineHeight;
        this.distanceRange = distanceRange;
        this.glyphs = glyphs;
        this.kerning = kerning;
        this.fallback = glyphs.getOrDefault(63, EMPTY);
    }

    MsdfFont.Glyph glyph(int cp) {
        MsdfFont.Glyph g = this.glyphs.get(cp);
        if (g != null) {
            return g;
        } else {
            return cp > 32 && !Character.isWhitespace(cp) ? this.fallback : this.glyphs.getOrDefault(32, EMPTY);
        }
    }

    public boolean lacks(int cp) {
        return !this.glyphs.containsKey(cp);
    }

    float kerning(int a, int b) {
        if (this.kerning.isEmpty()) {
            return 0.0F;
        }

        Float k = this.kerning.get((long)a << 32 | b & 4294967295L);
        return k == null ? 0.0F : k;
    }

    public float width(String s, float size) {
        if (s != null && !s.isEmpty()) {
            float w = 0.0F;
            float line = 0.0F;
            int prev = -1;
            int i = 0;

            while (i < s.length()) {
                int cp = s.codePointAt(i);
                i += Character.charCount(cp);
                if (cp == 10) {
                    w = Math.max(w, line);
                    line = 0.0F;
                    prev = -1;
                } else {
                    if (prev != -1) {
                        line += this.kerning(prev, cp) * size;
                    }

                    line += this.glyph(cp).advance * size;
                    prev = cp;
                }
            }

            return Math.max(w, line);
        } else {
            return 0.0F;
        }
    }

    public float[] bounds(String s, float size) {
        float asc = this.ascender * size;
        float minX = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        float cursor = 0.0F;
        int prev = -1;
        boolean any = false;
        int i = 0;

        while (i < s.length()) {
            int cp = s.codePointAt(i);
            i += Character.charCount(cp);
            MsdfFont.Glyph g = this.glyph(cp);
            if (prev != -1) {
                cursor += this.kerning(prev, cp) * size;
            }

            if (g.drawable) {
                minX = Math.min(minX, cursor + g.left * size);
                maxX = Math.max(maxX, cursor + g.right * size);
                minY = Math.min(minY, asc - g.top * size);
                maxY = Math.max(maxY, asc - g.bottom * size);
                any = true;
            }

            cursor += g.advance * size;
            prev = cp;
        }

        return any ? new float[]{minX, minY, maxX, maxY} : new float[]{0.0F, 0.0F, cursor, size};
    }

    private static MsdfFont load(String file) {
        Minecraft mc = Minecraft.getInstance();
        Identifier json = Ids.of("skysong", "msdf/" + file + ".json");
        Identifier png = Ids.of("skysong", "msdf/" + file + ".png");

        try (Reader reader = mc.getResourceManager().getResourceOrThrow(json).openAsReader()) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            JsonObject atlas = root.getAsJsonObject("atlas");
            int aw = atlas.get("width").getAsInt();
            int ah = atlas.get("height").getAsInt();
            float range = atlas.has("distanceRange") ? atlas.get("distanceRange").getAsFloat() : 4.0F;
            JsonObject metrics = root.getAsJsonObject("metrics");
            Map<Integer, MsdfFont.Glyph> glyphs = new HashMap<>();

            for (JsonElement e : root.getAsJsonArray("glyphs")) {
                JsonObject g = e.getAsJsonObject();
                int cp = g.get("unicode").getAsInt();
                float adv = g.has("advance") ? g.get("advance").getAsFloat() : 0.0F;
                if (g.has("planeBounds") && g.has("atlasBounds")) {
                    JsonObject pb = g.getAsJsonObject("planeBounds");
                    JsonObject ab = g.getAsJsonObject("atlasBounds");
                    float l = ab.get("left").getAsFloat();
                    float b = ab.get("bottom").getAsFloat();
                    float r = ab.get("right").getAsFloat();
                    float t = ab.get("top").getAsFloat();
                    glyphs.put(
                        cp,
                        new MsdfFont.Glyph(
                            true,
                            adv,
                            pb.get("left").getAsFloat(),
                            pb.get("top").getAsFloat(),
                            pb.get("right").getAsFloat(),
                            pb.get("bottom").getAsFloat(),
                            l / aw,
                            (ah - t) / ah,
                            r / aw,
                            (ah - b) / ah
                        )
                    );
                } else {
                    glyphs.put(cp, new MsdfFont.Glyph(false, adv, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
                }
            }

            Map<Long, Float> kerning = new HashMap<>();
            if (root.has("kerning")) {
                for (JsonElement e : root.getAsJsonArray("kerning")) {
                    JsonObject k = e.getAsJsonObject();
                    float adv = k.get("advance").getAsFloat();
                    if (adv != 0.0F) {
                        kerning.put((long)k.get("unicode1").getAsInt() << 32 | k.get("unicode2").getAsInt() & 4294967295L, adv);
                    }
                }
            }

            return new MsdfFont(
                png,
                metrics.get("ascender").getAsFloat(),
                metrics.get("descender").getAsFloat(),
                metrics.get("lineHeight").getAsFloat(),
                range,
                glyphs,
                kerning
            );
        } catch (Exception e) {
            SkysongClient.LOGGER.error("[Skysong] Could not load font {}", file, e);
            return null;
        }
    }

    public enum Font {
        MEDIUM("montserrat-medium"),
        SEMIBOLD("montserrat-semibold"),
        BOLD("montserrat-bold"),
        ICONS("icons"),
        HEART("heart"),
        PIXEL("small-pixel");

        final String file;
        private MsdfFont loaded;
        private boolean failed;

        Font(String file) {
            this.file = file;
        }

        public MsdfFont get() {
            if (this.loaded == null && !this.failed) {
                this.loaded = MsdfFont.load(this.file);
                this.failed = this.loaded == null;
            }

            return this.loaded;
        }
    }

    record Glyph(boolean drawable, float advance, float left, float top, float right, float bottom, float u0, float v0, float u1, float v1) {
    }
}
