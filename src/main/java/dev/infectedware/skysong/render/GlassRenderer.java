package dev.infectedware.skysong.render;

import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import dev.infectedware.skysong.compat.Compat;
import dev.infectedware.skysong.compat.Gpu;
import dev.infectedware.skysong.compat.GuiGraphics;
import dev.infectedware.skysong.compat.Mesh;
import dev.infectedware.skysong.compat.Shader;
import dev.infectedware.skysong.ui.Theme;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;

public final class GlassRenderer {

    private GlassRenderer() {
    }

    public static void fillPolygon(GuiGraphics ctx, float[][] pts, int color) {
        Compat.flush();
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
        Mesh b = Mesh.begin(Mode.TRIANGLE_FAN);
        b.addVertex(m, cx, cy, 0.0F).setColor(color);

        for (float[] p : inner) {
            b.addVertex(m, p[0], p[1], 0.0F).setColor(color);
        }

        b.draw();
        band(m, inner, outer, color);
        endShapes();
    }

    private static void band(Matrix4f m, List<float[]> from, List<float[]> to, int color) {
        Mesh b = Mesh.begin(Mode.TRIANGLE_STRIP);
        int transparent = Theme.alpha(color, 0.0F);

        for (int i = 0; i < from.size(); i++) {
            float[] p = from.get(i);
            float[] q = to.get(i);
            b.addVertex(m, p[0], p[1], 0.0F).setColor(color);
            b.addVertex(m, q[0], q[1], 0.0F).setColor(transparent);
        }

        b.draw();
    }

    private static float pixel(Matrix4f m) {
        float scale = (float)Math.sqrt(m.m00() * m.m00() + m.m01() * m.m01());
        double gui = Minecraft.getInstance().getWindow().getGuiScale();
        return (float)(1.0 / (scale * gui));
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

}
