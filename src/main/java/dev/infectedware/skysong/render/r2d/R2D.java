package dev.infectedware.skysong.render.r2d;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import dev.infectedware.skysong.ModSettings;
import dev.infectedware.skysong.compat.Compat;
import dev.infectedware.skysong.compat.Gpu;
import dev.infectedware.skysong.compat.GuiGraphics;
import dev.infectedware.skysong.compat.Mesh;
import dev.infectedware.skysong.compat.Shader;
import dev.infectedware.skysong.theme.ClientAccent;
import dev.infectedware.skysong.theme.Colors;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;

public final class R2D {
    private static final Shader shape = Shader.of("r2d_shape", DefaultVertexFormat.POSITION_TEX_COLOR);
    private static final Shader msdf = Shader.of("r2d_msdf", DefaultVertexFormat.POSITION_TEX_COLOR);
    private static final Shader glass = Shader.of("r2d_glass", DefaultVertexFormat.POSITION_TEX_COLOR);
    private static int[] paletteOverride;

    private R2D() {
    }

    public static void registerShaders() {
        Shader.registerAll();
    }

    public static boolean available() {
        return shape.ready() && msdf.ready() && glass.ready();
    }

    public static void rect(GuiGraphics c, float x, float y, float w, float h, float r, int color) {
        rect(c, x, y, w, h, r, r, r, r, color, color, color, color);
    }

    public static void rect(GuiGraphics c, float x, float y, float w, float h, float r, int tl, int tr, int br, int bl) {
        rect(c, x, y, w, h, r, r, r, r, tl, tr, br, bl);
    }

    public static void rect(GuiGraphics c, float x, float y, float w, float h, float rTL, float rTR, float rBR, float rBL, int color) {
        rect(c, x, y, w, h, rTL, rTR, rBR, rBL, color, color, color, color);
    }

    public static void rect(GuiGraphics c, float x, float y, float w, float h, float rTL, float rTR, float rBR, float rBL, int tl, int tr, int br, int bl) {
        if (!(w <= 0.0F) && !(h <= 0.0F) && (tl | tr | br | bl) >>> 24 != 0 && shape.ready()) {
            shapeQuad(c, x, y, w, h, rTL, rTR, rBR, rBL, 0.0F, false, 0, 0.0F, 0.0F, 1.0F, 1.0F, tl, tr, br, bl);
        }
    }

    public static void outline(GuiGraphics c, float x, float y, float w, float h, float r, float thickness, int color) {
        outline(c, x, y, w, h, r, thickness, color, color, color, color);
    }

    public static void outline(GuiGraphics c, float x, float y, float w, float h, float r, float thickness, int tl, int tr, int br, int bl) {
        if (!(w <= 0.0F) && !(h <= 0.0F) && (tl | tr | br | bl) >>> 24 != 0 && shape.ready()) {
            shapeQuad(c, x, y, w, h, r, r, r, r, Math.max(0.01F, thickness), false, 0, 0.0F, 0.0F, 1.0F, 1.0F, tl, tr, br, bl);
        }
    }

    public static void circle(GuiGraphics c, float cx, float cy, float radius, int color) {
        rect(c, cx - radius, cy - radius, radius * 2.0F, radius * 2.0F, radius, color);
    }

    public static void image(
        GuiGraphics c, Identifier texture, float x, float y, float w, float h, float r, float u0, float v0, float u1, float v1, int color, boolean smooth
    ) {
        image(c, Gpu.textureId(texture, smooth), x, y, w, h, r, u0, v0, u1, v1, color);
    }

    public static void image(GuiGraphics c, int glId, float x, float y, float w, float h, float r, float u0, float v0, float u1, float v1, int color) {
        if (!(w <= 0.0F) && !(h <= 0.0F) && color >>> 24 != 0 && shape.ready()) {
            shapeQuad(c, x, y, w, h, r, r, r, r, 0.0F, true, glId, u0, v0, u1, v1, color, color, color, color);
        }
    }

    private static void shapeQuad(
        GuiGraphics c,
        float x,
        float y,
        float w,
        float h,
        float rTL,
        float rTR,
        float rBR,
        float rBL,
        float thickness,
        boolean textured,
        int glId,
        float u0,
        float v0,
        float u1,
        float v1,
        int tl,
        int tr,
        int br,
        int bl
    ) {
        Compat.flush(c);
        Matrix4f m = c.pose().last().pose();
        float pad = pixel(m) * 2.0F;
        setShader(shape);
        shape.set("Size", w, h);
        shape.set("Radius", rTL, rTR, rBR, rBL);
        shape.set("Thickness", thickness);
        shape.set("TexMode", textured ? 1.0F : 0.0F);
        shape.set("UVRect", u0, v0, u1, v1);
        if (textured) {
            Gpu.texture(0, glId);
        }

        begin();
        Mesh b = Mesh.begin(Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        b.addVertex(m, x - pad, y - pad, 0.0F).setUv(-pad, -pad).setColor(tl);
        b.addVertex(m, x - pad, y + h + pad, 0.0F).setUv(-pad, h + pad).setColor(bl);
        b.addVertex(m, x + w + pad, y + h + pad, 0.0F).setUv(w + pad, h + pad).setColor(br);
        b.addVertex(m, x + w + pad, y - pad, 0.0F).setUv(w + pad, -pad).setColor(tr);
        b.draw();
        end();
    }

    public static float width(MsdfFont.Font font, String s, float size) {
        MsdfFont f = font.get();
        if (f != null && s != null) {
            MsdfFont.Font fallback = fallback(font);
            if (fallback != null && !covers(f, s)) {
                float w = 0.0F;

                for (String[] run : runs(f, s)) {
                    w += (run[0].equals("0") ? f : fallback.get()).width(run[1], size);
                }

                return w;
            } else {
                return f.width(s, size);
            }
        } else {
            return 0.0F;
        }
    }

    public static float[] bounds(MsdfFont.Font font, String s, float size) {
        MsdfFont f = font.get();
        return f == null ? new float[]{0.0F, 0.0F, 0.0F, size} : f.bounds(s, size);
    }

    public static float ascent(MsdfFont.Font font, float size) {
        MsdfFont f = font.get();
        return f == null ? size : f.ascender * size;
    }

    public static void text(GuiGraphics c, MsdfFont.Font font, String s, float x, float y, float size, int color) {
        text(c, font, s, x, y, size, color, color, color, color);
    }

    public static void text(GuiGraphics c, MsdfFont.Font font, String s, float x, float y, float size, int tl, int tr, int br, int bl) {
        MsdfFont f = font.get();
        if (f != null && s != null && !s.isEmpty() && msdf != null && (tl | tr | br | bl) >>> 24 != 0) {
            MsdfFont.Font fallback = fallback(font);
            if (fallback != null && !covers(f, s)) {
                float cursor = x;
                float base = y + f.ascender * size;
                MsdfFont fb = fallback.get();

                for (String[] run : runs(f, s)) {
                    boolean own = run[0].equals("0");
                    MsdfFont rf = own ? f : fb;
                    drawRun(c, rf, run[1], cursor, base - rf.ascender * size, size, tl, tr, br, bl);
                    cursor += rf.width(run[1], size);
                }
            } else {
                drawRun(c, f, s, x, y, size, tl, tr, br, bl);
            }
        }
    }

    private static MsdfFont.Font fallback(MsdfFont.Font font) {
        return switch (font) {
            case MEDIUM, BOLD -> MsdfFont.Font.SEMIBOLD;
            case PIXEL -> MsdfFont.Font.MEDIUM;
            default -> null;
        };
    }

    private static boolean covers(MsdfFont f, String s) {
        int i = 0;

        while (i < s.length()) {
            int cp = s.codePointAt(i);
            i += Character.charCount(cp);
            if (cp > 32 && !f.has(cp)) {
                return false;
            }
        }

        return true;
    }

    private static List<String[]> runs(MsdfFont f, String s) {
        List<String[]> out = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        String kind = null;
        int i = 0;

        while (i < s.length()) {
            int cp = s.codePointAt(i);
            i += Character.charCount(cp);
            String k = cp > 32 && !f.has(cp) ? "1" : "0";
            if (kind != null && !k.equals(kind) && cp > 32) {
                out.add(new String[]{kind, sb.toString()});
                sb.setLength(0);
            }

            if (cp > 32 || kind == null) {
                kind = k;
            }

            sb.appendCodePoint(cp);
        }

        if (!sb.isEmpty()) {
            out.add(new String[]{kind, sb.toString()});
        }

        return out;
    }

    private static void drawRun(GuiGraphics c, MsdfFont f, String s, float x, float y, float size, int tl, int tr, int br, int bl) {
        Compat.flush(c);
        Matrix4f m = c.pose().last().pose();
        setShader(msdf);
        msdf.set("DistanceRange", f.distanceRange);
        Gpu.texture(0, Gpu.textureId(f.atlas, true));
        begin();
        Mesh b = Mesh.begin(Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        float cursor = x;
        float baseline = y + f.ascender * size;
        int prev = -1;
        boolean any = false;
        int i = 0;

        while (i < s.length()) {
            int cp = s.codePointAt(i);
            i += Character.charCount(cp);
            if (cp == 10) {
                cursor = x;
                baseline += f.lineHeight * size;
                prev = -1;
            } else {
                MsdfFont.Glyph g = f.glyph(cp);
                if (prev != -1) {
                    cursor += f.kerning(prev, cp) * size;
                }

                if (g.drawable()) {
                    float x0 = cursor + g.left() * size;
                    float x1 = cursor + g.right() * size;
                    float y0 = baseline - g.top() * size;
                    float y1 = baseline - g.bottom() * size;
                    b.addVertex(m, x0, y0, 0.0F).setUv(g.u0(), g.v0()).setColor(tl);
                    b.addVertex(m, x0, y1, 0.0F).setUv(g.u0(), g.v1()).setColor(bl);
                    b.addVertex(m, x1, y1, 0.0F).setUv(g.u1(), g.v1()).setColor(br);
                    b.addVertex(m, x1, y0, 0.0F).setUv(g.u1(), g.v0()).setColor(tr);
                    any = true;
                }

                cursor += g.advance() * size;
                prev = cp;
            }
        }

        b.draw();
        end();
    }

    public static void textCentered(GuiGraphics c, MsdfFont.Font font, String s, float x, float cy, float size, int color) {
        float[] bb = bounds(font, "H", size);
        text(c, font, s, x, cy - (bb[1] + bb[3]) * 0.5F, size, color);
    }

    public static void clientRect(GuiGraphics c, float x, float y, float w, float h, float alpha) {
        float r = Math.min(ModSettings.rectCornerRadius, Math.min(w, h) * 0.5F);
        glass(c, x, y, w, h, r, r, r, r, alpha, true);
    }

    public static void clientRectFixed(GuiGraphics c, float x, float y, float w, float h, float radius, float alpha) {
        float r = Math.min(radius, Math.min(w, h) * 0.5F);
        glass(c, x, y, w, h, r, r, r, r, alpha, true);
    }

    public static void clientRectNoGlow(GuiGraphics c, float x, float y, float w, float h, float radius, float alpha) {
        float r = Math.min(radius, Math.min(w, h) * 0.5F);
        glass(c, x, y, w, h, r, r, r, r, alpha, false);
    }

    public static void glassTinted(GuiGraphics c, float x, float y, float w, float h, float r, float alpha, boolean withGlow, int[] palette) {
        r = Math.min(r, Math.min(w, h) * 0.5F);
        paletteOverride = palette;

        try {
            glass(c, x, y, w, h, r, r, r, r, alpha, withGlow);
        } finally {
            paletteOverride = null;
        }
    }

    public static void glass(GuiGraphics c, float x, float y, float w, float h, float rTL, float rTR, float rBR, float rBL, float alpha, boolean withGlow) {
        if (!(w <= 0.0F) && !(h <= 0.0F) && !(alpha <= 0.003F) && glass.ready()) {
            GlassStyle.update();
            if (withGlow) {
                float glowAmount = GlassStyle.glowBlend();
                if (glowAmount > 0.001F && ModSettings.rectGlowIntensity > 0.0F) {
                    float gr = ModSettings.rectGlowRadius;
                    glassQuad(c, x, y, w, h, rTL, rTR, rBR, rBL, alpha * glowAmount, 1.0F, gr);
                }
            }

            glassQuad(c, x, y, w, h, rTL, rTR, rBR, rBL, alpha, 0.0F, 0.0F);
        }
    }

    public static void glow(GuiGraphics c, float x, float y, float w, float h, float r, float alpha) {
        if (glass.ready()) {
            GlassStyle.update();
            float glowAmount = GlassStyle.glowBlend();
            if (!(glowAmount <= 0.001F)) {
                glassQuad(c, x, y, w, h, r, r, r, r, alpha * glowAmount, 1.0F, ModSettings.rectGlowRadius);
            }
        }
    }

    private static void glassQuad(
        GuiGraphics c, float x, float y, float w, float h, float rTL, float rTR, float rBR, float rBL, float alpha, float mode, float expand
    ) {
        Compat.flush(c);
        Matrix4f m = c.pose().last().pose();
        float pad = pixel(m) * 2.0F + expand;
        setShader(glass);
        glass.set("Size", w, h);
        glass.set("Radius", rTL, rTR, rBR, rBL);
        glass.set("Glass", Math.min(1.0F, alpha), ModSettings.rectEdgeSharpness, ModSettings.rectEdgeStrength, ModSettings.rectRefraction);
        int[] pal = paletteOverride != null && paletteOverride.length > 0 ? paletteOverride : GlassStyle.palette();
        glass.set("Style", GlassStyle.styleId(), GlassStyle.phase(), GlassStyle.colorOffset(), pal.length);
        glass.set("Extra", 0.8F, Backdrop.ready() ? 1.0F : 0.0F, mode, GlassStyle.closed());
        glass.set("Glow", ModSettings.rectGlowIntensity, Math.max(1.0F, expand), GlassStyle.sweep(), GlassStyle.previousStyle());

        for (int i = 0; i < 6; i++) {
            int col = pal[Math.min(i, pal.length - 1)];
            glass.set("Pal" + i, (col >> 16 & 0xFF) / 255.0F, (col >> 8 & 0xFF) / 255.0F, (col & 0xFF) / 255.0F, 1.0F);
        }

        Gpu.texture(0, Backdrop.ready() ? Backdrop.texture() : 0);
        begin();
        Mesh b = Mesh.begin(Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        int white = -1;
        b.addVertex(m, x - pad, y - pad, 0.0F).setUv(-pad, -pad).setColor(white);
        b.addVertex(m, x - pad, y + h + pad, 0.0F).setUv(-pad, h + pad).setColor(white);
        b.addVertex(m, x + w + pad, y + h + pad, 0.0F).setUv(w + pad, h + pad).setColor(white);
        b.addVertex(m, x + w + pad, y - pad, 0.0F).setUv(w + pad, -pad).setColor(white);
        b.draw();
        end();
    }

    public static void accentFillHorizontal(GuiGraphics c, float x, float y, float w, float h, float r, float alpha) {
        int a = ClientAccent.gradientColor(0.0F, alpha);
        int b = ClientAccent.gradientColor(1.0F, alpha);
        rect(c, x, y, w, h, r, a, b, b, a);
    }

    public static void accentFillVertical(GuiGraphics c, float x, float y, float w, float h, float r, float alpha) {
        int a = ClientAccent.gradientColor(0.0F, alpha);
        int b = ClientAccent.gradientColor(1.0F, alpha);
        rect(c, x, y, w, h, r, a, a, b, b);
    }

    public static void accentIcon(GuiGraphics c, String glyph, float x, float y, float size, float alpha, float t) {
        t = Math.max(0.0F, Math.min(1.0F, t));
        int a = ClientAccent.gradientColor(t * 0.42F, alpha);
        int b = ClientAccent.gradientColor(0.28F + t * 0.72F, alpha);
        text(c, MsdfFont.Font.ICONS, glyph, x, y, size, a, b, b, a);
    }

    private static void setShader(Shader p) {
        Gpu.useGuiMatrices();
        p.bind();
    }

    private static void begin() {
        Gpu.enableBlend();
        Gpu.defaultBlendFunc();
        Gpu.disableCull();
    }

    private static void end() {
        Gpu.enableCull();
        Gpu.disableBlend();
    }

    public static float pixel(Matrix4f m) {
        float scale = (float)Math.sqrt(m.m00() * m.m00() + m.m01() * m.m01());
        double gui = Minecraft.getInstance().getWindow().getGuiScale();
        return (float)(1.0 / Math.max(1.0E-4, scale * gui));
    }

    public static int alpha(int argb, float f) {
        return Colors.multAlpha(argb, f);
    }
}
