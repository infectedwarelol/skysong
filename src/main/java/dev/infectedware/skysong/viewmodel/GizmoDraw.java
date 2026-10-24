package dev.infectedware.skysong.viewmodel;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import dev.infectedware.skysong.compat.Gpu;
import dev.infectedware.skysong.compat.GuiGraphics;
import dev.infectedware.skysong.compat.Mesh;
import dev.infectedware.skysong.compat.Shader;
import org.joml.Matrix4f;

final class GizmoDraw {
    private static final int SEGMENTS = 96;

    private GizmoDraw() {
    }

    static void ellipse(GuiGraphics c, float cx, float cy, float rx, float ry, float thickness, int color, int behindColor) {
        Matrix4f m = c.pose().last().pose();
        Gpu.enableBlend();
        Gpu.defaultBlendFunc();
        Gpu.disableCull();
        Gpu.useGuiMatrices();
        Shader.POSITION_COLOR.bind();
        float half = thickness / 2.0F;
        float feather = 1.0F;
        float[][] bands = new float[][]{{-half - feather, -half}, {-half, half}, {half, half + feather}};

        for (float[] band : bands) {
            Mesh b = Mesh.begin(Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);
            boolean innerFade = band[0] < -half;
            boolean outerFade = band[1] > half;

            for (int i = 0; i <= 96; i++) {
                double a = (Math.PI * 2) * i / 96.0;
                float cos = (float)Math.cos(a);
                float sin = (float)Math.sin(a);
                float px = cx + rx * cos;
                float py = cy + ry * sin;
                float nx = ry * cos;
                float ny = rx * sin;
                float len = (float)Math.sqrt(nx * nx + ny * ny);
                nx /= len;
                ny /= len;
                int col = sin < 0.0F ? behindColor : color;
                int inner = innerFade ? col & 16777215 : col;
                int outer = outerFade ? col & 16777215 : col;
                b.addVertex(m, px + nx * band[0], py + ny * band[0], 0.0F).setColor(inner);
                b.addVertex(m, px + nx * band[1], py + ny * band[1], 0.0F).setColor(outer);
            }

            b.draw();
        }

        Gpu.enableCull();
    }

    static float distanceToEllipse(float px, float py, float cx, float cy, float rx, float ry) {
        float dx = (px - cx) / rx;
        float dy = (py - cy) / ry;
        float r = (float)Math.sqrt(dx * dx + dy * dy);
        if (r < 1.0E-4F) {
            return Math.min(rx, ry);
        }

        float ang = (float)Math.atan2(dy, dx);
        float local = (float)Math.hypot(rx * Math.cos(ang), ry * Math.sin(ang));
        return Math.abs(r - 1.0F) * local;
    }
}
