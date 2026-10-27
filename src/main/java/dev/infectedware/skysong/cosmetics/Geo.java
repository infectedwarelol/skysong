package dev.infectedware.skysong.cosmetics;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;

final class Geo {
    private static final int BALL_FACES = 32;
    private static final float[] BALL = octasphere();
    private final PoseStack.Pose pose;
    private final VertexConsumer out;
    private final int light;

    Geo(PoseStack.Pose pose, VertexConsumer out, int light) {
        this.pose = pose;
        this.out = out;
        this.light = light;
    }

    Geo on(PoseStack.Pose other) {
        return new Geo(other, this.out, this.light);
    }

    void vertex(float x, float y, float z, int argb, float u, float v, float nx, float ny, float nz) {
        this.out.addVertex(this.pose, x, y, z).setColor(argb).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(this.light).setNormal(this.pose, nx, ny, nz);
    }

    void quad(float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz, float dx, float dy, float dz, int argb) {
        this.quad(ax, ay, az, bx, by, bz, cx, cy, cz, dx, dy, dz, argb, argb, argb, argb);
    }

    void quad(
        float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz, float dx, float dy, float dz, int ca, int cb, int cc, int cd
    ) {
        float e1x = cx - ax;
        float e1y = cy - ay;
        float e1z = cz - az;
        float e2x = dx - bx;
        float e2y = dy - by;
        float e2z = dz - bz;
        float nx = e1y * e2z - e1z * e2y;
        float ny = e1z * e2x - e1x * e2z;
        float nz = e1x * e2y - e1y * e2x;
        float lengthSquared = nx * nx + ny * ny + nz * nz;
        if (lengthSquared < 1.0E-12F) {
            nx = 0.0F;
            ny = -1.0F;
            nz = 0.0F;
        } else {
            float inv = Mth.invSqrt(lengthSquared);
            nx *= inv;
            ny *= inv;
            nz *= inv;
        }

        this.vertex(ax, ay, az, ca, 0.0F, 0.0F, nx, ny, nz);
        this.vertex(bx, by, bz, cb, 1.0F, 0.0F, nx, ny, nz);
        this.vertex(cx, cy, cz, cc, 1.0F, 1.0F, nx, ny, nz);
        this.vertex(dx, dy, dz, cd, 0.0F, 1.0F, nx, ny, nz);
    }

    void tri(float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz, int argb) {
        this.quad(ax, ay, az, bx, by, bz, cx, cy, cz, cx, cy, cz, argb);
    }

    void box(float x0, float y0, float z0, float x1, float y1, float z1, int argb) {
        this.quad(x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0, argb);
        this.quad(x1, y0, z1, x0, y0, z1, x0, y1, z1, x1, y1, z1, argb);
        this.quad(x0, y0, z1, x0, y0, z0, x0, y1, z0, x0, y1, z1, argb);
        this.quad(x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0, argb);
        this.quad(x0, y0, z1, x1, y0, z1, x1, y0, z0, x0, y0, z0, argb);
        this.quad(x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, argb);
    }

    void cube(float cx, float cy, float cz, float half, int argb) {
        this.box(cx - half, cy - half, cz - half, cx + half, cy + half, cz + half, argb);
    }

    void torus(float radius, float tube, int segments, int sides, int[] colors) {
        for (int i = 0; i < segments; i++) {
            float a0 = Mth.TWO_PI * i / segments;
            float a1 = Mth.TWO_PI * (i + 1) / segments;
            float cos0 = Mth.cos(a0);
            float sin0 = Mth.sin(a0);
            float cos1 = Mth.cos(a1);
            float sin1 = Mth.sin(a1);
            int c0 = colors[i];
            int c1 = colors[i + 1];
            for (int j = 0; j < sides; j++) {
                float b0 = Mth.TWO_PI * j / sides;
                float b1 = Mth.TWO_PI * (j + 1) / sides;
                float r0 = radius + tube * Mth.cos(b0);
                float r1 = radius + tube * Mth.cos(b1);
                float y0 = tube * Mth.sin(b0);
                float y1 = tube * Mth.sin(b1);
                this.quad(r0 * cos0, y0, r0 * sin0, r0 * cos1, y0, r0 * sin1, r1 * cos1, y1, r1 * sin1, r1 * cos0, y1, r1 * sin0, c0, c1, c1, c0);
            }
        }
    }

    void frustum(float r0, float r1, float y0, float y1, int segments, int argb, boolean capBottom, boolean capTop) {
        for (int i = 0; i < segments; i++) {
            float a0 = Mth.TWO_PI * i / segments;
            float a1 = Mth.TWO_PI * (i + 1) / segments;
            float cos0 = Mth.cos(a0);
            float sin0 = Mth.sin(a0);
            float cos1 = Mth.cos(a1);
            float sin1 = Mth.sin(a1);
            this.quad(r0 * cos0, y0, r0 * sin0, r0 * cos1, y0, r0 * sin1, r1 * cos1, y1, r1 * sin1, r1 * cos0, y1, r1 * sin0, argb);
            if (capBottom && r0 > 0.0F) {
                this.tri(0.0F, y0, 0.0F, r0 * cos1, y0, r0 * sin1, r0 * cos0, y0, r0 * sin0, argb);
            }

            if (capTop && r1 > 0.0F) {
                this.tri(0.0F, y1, 0.0F, r1 * cos0, y1, r1 * sin0, r1 * cos1, y1, r1 * sin1, argb);
            }
        }
    }

    void cone(float r, float y, float tipX, float tipY, float tipZ, int segments, int even, int odd, int stripe, boolean capBase) {
        for (int i = 0; i < segments; i++) {
            float a0 = Mth.TWO_PI * i / segments;
            float a1 = Mth.TWO_PI * (i + 1) / segments;
            float x0 = r * Mth.cos(a0);
            float z0 = r * Mth.sin(a0);
            float x1 = r * Mth.cos(a1);
            float z1 = r * Mth.sin(a1);
            int c = (i / stripe & 1) == 0 ? even : odd;
            this.tri(x0, y, z0, x1, y, z1, tipX, tipY, tipZ, c);
            if (capBase) {
                this.tri(0.0F, y, 0.0F, x1, y, z1, x0, y, z0, c);
            }
        }
    }

    void ball(float cx, float cy, float cz, float radius, int argb) {
        float[] ball = BALL;
        for (int i = 0; i < ball.length; i += 9) {
            this.tri(
                cx + ball[i] * radius, cy + ball[i + 1] * radius, cz + ball[i + 2] * radius,
                cx + ball[i + 3] * radius, cy + ball[i + 4] * radius, cz + ball[i + 5] * radius,
                cx + ball[i + 6] * radius, cy + ball[i + 7] * radius, cz + ball[i + 8] * radius,
                argb
            );
        }
    }

    void card(
        float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz, float dx, float dy, float dz, float u0, float u1, int ca, int cb, int cc, int cd
    ) {
        float e1x = cx - ax;
        float e1y = cy - ay;
        float e1z = cz - az;
        float e2x = dx - bx;
        float e2y = dy - by;
        float e2z = dz - bz;
        float nx = e1y * e2z - e1z * e2y;
        float ny = e1z * e2x - e1x * e2z;
        float nz = e1x * e2y - e1y * e2x;
        float inv = Mth.invSqrt(Math.max(1.0E-12F, nx * nx + ny * ny + nz * nz));
        nx *= inv;
        ny *= inv;
        nz *= inv;
        this.vertex(ax, ay, az, ca, u0, 0.0F, nx, ny, nz);
        this.vertex(bx, by, bz, cb, u1, 0.0F, nx, ny, nz);
        this.vertex(cx, cy, cz, cc, u1, 1.0F, nx, ny, nz);
        this.vertex(dx, dy, dz, cd, u0, 1.0F, nx, ny, nz);
    }

    private static float[] octasphere() {
        float[][] axes = new float[][]{{1.0F, 0.0F, 0.0F}, {0.0F, 0.0F, 1.0F}, {-1.0F, 0.0F, 0.0F}, {0.0F, 0.0F, -1.0F}};
        float[] up = new float[]{0.0F, -1.0F, 0.0F};
        float[] down = new float[]{0.0F, 1.0F, 0.0F};
        float[] out = new float[BALL_FACES * 9];
        int index = 0;
        for (int i = 0; i < 4; i++) {
            float[] a = axes[i];
            float[] b = axes[(i + 1) % 4];
            index = split(out, index, up, b, a);
            index = split(out, index, down, a, b);
        }

        return out;
    }

    private static int split(float[] out, int index, float[] a, float[] b, float[] c) {
        float[] ab = middle(a, b);
        float[] bc = middle(b, c);
        float[] ca = middle(c, a);
        float[][] faces = new float[][]{a, ab, ca, ab, b, bc, ca, bc, c, ab, bc, ca};
        for (float[] corner : faces) {
            out[index++] = corner[0];
            out[index++] = corner[1];
            out[index++] = corner[2];
        }

        return index;
    }

    private static float[] middle(float[] a, float[] b) {
        float x = a[0] + b[0];
        float y = a[1] + b[1];
        float z = a[2] + b[2];
        float inv = Mth.invSqrt(x * x + y * y + z * z);
        return new float[]{x * inv, y * inv, z * inv};
    }
}
