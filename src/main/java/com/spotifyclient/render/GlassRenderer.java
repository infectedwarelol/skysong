package com.spotifyclient.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import com.spotifyclient.compat.Compat;
import com.spotifyclient.compat.Gpu;
import com.spotifyclient.compat.GuiGraphics;
import com.spotifyclient.compat.Mesh;
import com.spotifyclient.compat.Shader;
import com.spotifyclient.compat.Targets;
import com.spotifyclient.render.fx.Fx;
import com.spotifyclient.ui.Theme;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL14;

public final class GlassRenderer {
    private static final int CORNER_SEGMENTS = 12;
    private static TextureTarget sharp;
    private static TextureTarget blurred;
    private static boolean blurReady;
    private static float backdropOpacity = 1.0F;

    private GlassRenderer() {
    }

    public static void captureBlurredBackground(GuiGraphics ctx, float delta) {
        Minecraft mc = Minecraft.getInstance();
        Compat.flush(ctx);
        Compat.drawCollectedGui();
        RenderTarget main = mc.getMainRenderTarget();
        int w = main.width;
        int h = main.height;
        sharp = ensureSize(sharp, w, h);
        blurred = ensureSize(blurred, w, h);
        Fx.boxBlur(main, blurred, 5.0F, 3);
        Gpu.bind(main);
        blurReady = true;
    }

    public static void drawGlass(GuiGraphics ctx, float x, float y, float w, float h, float r, int tintTop, int tintBottom) {
        if (blurReady) {
            drawBlurredBackdrop(ctx, x, y, w, h, r);
        }

        fillRounded(ctx, x, y, w, h, r, tintTop, tintBottom);
        strokeRounded(ctx, x, y, w, h, r, 0.5F, 1724437759, 415814911);
    }

    public static void endFrame() {
        blurReady = false;
    }

    public static void setOpacity(float opacity) {
        backdropOpacity = Math.max(0.0F, Math.min(1.0F, opacity));
    }

    public static void hudPanel(GuiGraphics ctx, float x, float y, float w, float h, float r, float opacity) {
        hudPanel(ctx, x, y, w, h, r, opacity, -10781446);
    }

    public static void hudPanel(GuiGraphics ctx, float x, float y, float w, float h, float r, float opacity, int accent) {
        shadow(ctx, x, y, w, h, r, 6.0F, opacity);
        fillRounded(
            ctx, x, y, w, h, r, Theme.alpha(Theme.tinted(-937020570, accent, 0.22F), opacity), Theme.alpha(Theme.tinted(-736484791, accent, 0.16F), opacity)
        );
        strokeRounded(ctx, x, y, w, h, r, 0.5F, Theme.alpha(1724437759, opacity), Theme.alpha(415814911, opacity));
    }

    public static void fillRounded(GuiGraphics ctx, float x, float y, float w, float h, float r, int color) {
        fillRounded(ctx, x, y, w, h, r, color, color);
    }

    public static void fillRounded(GuiGraphics ctx, float x, float y, float w, float h, float r, int top, int bottom) {
        if (!(w <= 0.0F) && !(h <= 0.0F)) {
            Compat.flush(ctx);
            Matrix4f m = ctx.pose().last().pose();
            float f = pixel(m);
            List<float[]> inner = outline(x, y, w, h, r, f / 2.0F);
            List<float[]> outer = outline(x, y, w, h, r, -f / 2.0F);
            beginShapes();
            Mesh b = Mesh.begin(Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION_COLOR);
            b.addVertex(m, x + w / 2.0F, y + h / 2.0F, 0.0F).setColor(Theme.lerpColor(top, bottom, 0.5F));

            for (float[] p : inner) {
                b.addVertex(m, p[0], p[1], 0.0F).setColor(Theme.lerpColor(top, bottom, (p[1] - y) / h));
            }

            b.draw();
            band(m, inner, outer, top, bottom, y, h, 1.0F, 0.0F);
            endShapes();
        }
    }

    public static void strokeRounded(GuiGraphics ctx, float x, float y, float w, float h, float r, float t, int color) {
        strokeRounded(ctx, x, y, w, h, r, t, color, color);
    }

    public static void strokeRounded(GuiGraphics ctx, float x, float y, float w, float h, float r, float t, int top, int bottom) {
        if (!(w <= 2.0F * t) && !(h <= 2.0F * t)) {
            Compat.flush(ctx);
            Matrix4f m = ctx.pose().last().pose();
            float f = pixel(m);
            float solidA = Math.min(f / 2.0F, t / 2.0F);
            float solidB = Math.max(t - f / 2.0F, t / 2.0F);
            List<float[]> a = outline(x, y, w, h, r, -f / 2.0F);
            List<float[]> b = outline(x, y, w, h, r, solidA);
            List<float[]> c = outline(x, y, w, h, r, solidB);
            List<float[]> d = outline(x, y, w, h, r, t + f / 2.0F);
            beginShapes();
            band(m, a, b, top, bottom, y, h, 0.0F, 1.0F);
            if (solidB > solidA) {
                band(m, b, c, top, bottom, y, h, 1.0F, 1.0F);
            }

            band(m, c, d, top, bottom, y, h, 1.0F, 0.0F);
            endShapes();
        }
    }

    public static void shadow(GuiGraphics ctx, float x, float y, float w, float h, float r, float spread, float opacity) {
        Compat.flush(ctx);
        Matrix4f m = ctx.pose().last().pose();
        float oy = y + 2.0F * spread / 14.0F;
        int c = Theme.alpha(1493172232, opacity);
        List<float[]> o0 = outline(x, oy, w, h, r, 0.0F);
        List<float[]> o1 = outline(x, oy, w, h, r, -spread * 0.35F);
        List<float[]> o2 = outline(x, oy, w, h, r, -spread);
        beginShapes();
        band(m, o0, o1, c, c, oy, h, 1.0F, 0.45F);
        band(m, o1, o2, c, c, oy, h, 0.45F, 0.0F);
        endShapes();
    }

    public static void fillCircle(GuiGraphics ctx, float cx, float cy, float radius, int color) {
        fillRounded(ctx, cx - radius, cy - radius, radius * 2.0F, radius * 2.0F, radius, color, color);
    }

    public static void fillPolygon(GuiGraphics ctx, float[][] pts, int color) {
        Compat.flush(ctx);
        Matrix4f m = ctx.pose().last().pose();
        float f = pixel(m);
        float cx = 0.0F;
        float cy = 0.0F;

        for (float[] p : pts) {
            cx += p[0];
            cy += p[1];
        }

        cx /= pts.length;
        cy /= pts.length;
        List<float[]> inner = new ArrayList<>();
        List<float[]> outer = new ArrayList<>();

        for (int i = 0; i <= pts.length; i++) {
            float[] p = pts[i % pts.length];
            float dx = p[0] - cx;
            float dy = p[1] - cy;
            float len = (float)Math.sqrt(dx * dx + dy * dy);
            float nx = len == 0.0F ? 0.0F : dx / len * f / 2.0F;
            float ny = len == 0.0F ? 0.0F : dy / len * f / 2.0F;
            inner.add(new float[]{p[0] - nx, p[1] - ny});
            outer.add(new float[]{p[0] + nx, p[1] + ny});
        }

        beginShapes();
        Mesh b = Mesh.begin(Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION_COLOR);
        b.addVertex(m, cx, cy, 0.0F).setColor(color);

        for (float[] p : inner) {
            b.addVertex(m, p[0], p[1], 0.0F).setColor(color);
        }

        b.draw();
        band(m, inner, outer, color, color, cy, 1.0F, 1.0F, 0.0F);
        endShapes();
    }

    public static void textureRounded(GuiGraphics ctx, int glId, float x, float y, float w, float h, float r) {
        textureRoundedRegion(ctx, glId, x, y, w, h, r, 0.0F, 0.0F, 1.0F, 1.0F);
    }

    public static void textureRoundedRegion(GuiGraphics ctx, int glId, float x, float y, float w, float h, float r, float u0, float v0, float u1, float v1) {
        Compat.flush(ctx);
        Matrix4f m = ctx.pose().last().pose();
        float f = pixel(m);
        Gpu.disableCull();
        Gpu.enableBlend();
        Gpu.defaultBlendFunc();
        Gpu.useGuiMatrices();
        Shader.POSITION_TEX.bind();
        Gpu.texture(0, glId);
        Mesh b = Mesh.begin(Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION_TEX);
        b.addVertex(m, x + w / 2.0F, y + h / 2.0F, 0.0F).setUv((u0 + u1) / 2.0F, (v0 + v1) / 2.0F);

        for (float[] p : outline(x, y, w, h, r, f / 2.0F)) {
            b.addVertex(m, p[0], p[1], 0.0F).setUv(u0 + (p[0] - x) / w * (u1 - u0), v0 + (p[1] - y) / h * (v1 - v0));
        }

        b.draw();
        Gpu.disableBlend();
        Gpu.enableCull();
    }

    private static void drawBlurredBackdrop(GuiGraphics ctx, float x, float y, float w, float h, float r) {
        Compat.flush(ctx);
        Matrix4f m = ctx.pose().last().pose();
        float gw = ctx.guiWidth();
        float gh = ctx.guiHeight();
        float f = pixel(m);
        Gpu.disableCull();
        if (backdropOpacity < 0.999F) {
            Gpu.enableBlend();
            Gpu.blendFunc(32771, 32772);
            GL14.glBlendColor(0.0F, 0.0F, 0.0F, backdropOpacity);
        } else {
            Gpu.disableBlend();
        }

        Gpu.useGuiMatrices();
        Shader.POSITION_TEX.bind();
        Gpu.texture(0, Gpu.color(blurred));
        Mesh b = Mesh.begin(Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION_TEX);
        texVertex(b, m, x + w / 2.0F, y + h / 2.0F, gw, gh);

        for (float[] p : outline(x, y, w, h, r, f / 2.0F)) {
            texVertex(b, m, p[0], p[1], gw, gh);
        }

        b.draw();
        Gpu.defaultBlendFunc();
        Gpu.disableBlend();
        Gpu.enableCull();
    }

    private static void texVertex(Mesh b, Matrix4f m, float x, float y, float gw, float gh) {
        Vector3f s = m.transformPosition(x, y, 0.0F, new Vector3f());
        b.addVertex(m, x, y, 0.0F).setUv(s.x / gw, 1.0F - s.y / gh);
    }

    private static void band(Matrix4f m, List<float[]> from, List<float[]> to, int top, int bottom, float y, float h, float aFrom, float aTo) {
        Mesh b = Mesh.begin(Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);

        for (int i = 0; i < from.size(); i++) {
            float[] p = from.get(i);
            float[] q = to.get(i);
            b.addVertex(m, p[0], p[1], 0.0F).setColor(Theme.alpha(Theme.lerpColor(top, bottom, (p[1] - y) / h), aFrom));
            b.addVertex(m, q[0], q[1], 0.0F).setColor(Theme.alpha(Theme.lerpColor(top, bottom, (q[1] - y) / h), aTo));
        }

        b.draw();
    }

    private static float pixel(Matrix4f m) {
        float scale = (float)Math.sqrt(m.m00() * m.m00() + m.m01() * m.m01());
        double gui = Minecraft.getInstance().getWindow().getGuiScale();
        return (float)(1.0 / (scale * gui));
    }

    private static List<float[]> outline(float x, float y, float w, float h, float r, float inset) {
        r = Math.max(0.0F, Math.min(r, Math.min(w, h) / 2.0F));
        float co = Math.max(r, inset);
        float cr = Math.max(0.0F, r - inset);
        float[][] centers = new float[][]{{x + co, y + co}, {x + w - co, y + co}, {x + w - co, y + h - co}, {x + co, y + h - co}};
        List<float[]> pts = new ArrayList<>(53);

        for (int c = 0; c < 4; c++) {
            double start = Math.PI * (1.0 + c * 0.5);

            for (int i = 0; i <= 12; i++) {
                double a = start + (Math.PI / 2) * i / 12.0;
                pts.add(new float[]{centers[c][0] + (float)Math.cos(a) * cr, centers[c][1] + (float)Math.sin(a) * cr});
            }
        }

        pts.add(pts.get(0));
        return pts;
    }

    private static void beginShapes() {
        Gpu.enableBlend();
        Gpu.defaultBlendFunc();
        Gpu.disableCull();
        Gpu.useGuiMatrices();
        Shader.POSITION_COLOR.bind();
    }

    private static void endShapes() {
        Gpu.enableCull();
        Gpu.disableBlend();
    }

    private static TextureTarget ensureSize(TextureTarget fb, int w, int h) {
        if (fb == null) {
            return Targets.create(w, h, false);
        }

        if (fb.width != w || fb.height != h) {
            Targets.resize(fb, w, h);
        }

        return fb;
    }

    private static void blit(RenderTarget from, RenderTarget to, int w, int h) {
        Gpu.blit(from, to);
    }
}
