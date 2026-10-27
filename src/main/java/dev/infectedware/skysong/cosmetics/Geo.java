package dev.infectedware.skysong.cosmetics;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.function.IntUnaryOperator;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import org.joml.Vector3f;

final class Geo {
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

        this.vertex(ax, ay, az, argb, 0.0F, 0.0F, nx, ny, nz);
        this.vertex(bx, by, bz, argb, 1.0F, 0.0F, nx, ny, nz);
        this.vertex(cx, cy, cz, argb, 1.0F, 1.0F, nx, ny, nz);
        this.vertex(dx, dy, dz, argb, 0.0F, 1.0F, nx, ny, nz);
    }

    void quad(Vector3f a, Vector3f b, Vector3f c, Vector3f d, int argb) {
        this.quad(a, b, c, d, argb, argb, argb, argb);
    }

    void quad(Vector3f a, Vector3f b, Vector3f c, Vector3f d, int ca, int cb, int cc, int cd) {
        Vector3f n = new Vector3f(c).sub(a).cross(new Vector3f(d).sub(b));
        if (n.lengthSquared() < 1.0E-12F) {
            n.set(0.0F, -1.0F, 0.0F);
        }

        n.normalize();
        this.vertex(a.x, a.y, a.z, ca, 0.0F, 0.0F, n.x, n.y, n.z);
        this.vertex(b.x, b.y, b.z, cb, 1.0F, 0.0F, n.x, n.y, n.z);
        this.vertex(c.x, c.y, c.z, cc, 1.0F, 1.0F, n.x, n.y, n.z);
        this.vertex(d.x, d.y, d.z, cd, 0.0F, 1.0F, n.x, n.y, n.z);
    }

    void tri(Vector3f a, Vector3f b, Vector3f c, int argb) {
        this.quad(a, b, c, c, argb);
    }

    void box(float x0, float y0, float z0, float x1, float y1, float z1, int argb) {
        Vector3f p000 = new Vector3f(x0, y0, z0);
        Vector3f p100 = new Vector3f(x1, y0, z0);
        Vector3f p110 = new Vector3f(x1, y1, z0);
        Vector3f p010 = new Vector3f(x0, y1, z0);
        Vector3f p001 = new Vector3f(x0, y0, z1);
        Vector3f p101 = new Vector3f(x1, y0, z1);
        Vector3f p111 = new Vector3f(x1, y1, z1);
        Vector3f p011 = new Vector3f(x0, y1, z1);
        this.quad(p000, p100, p110, p010, argb);
        this.quad(p101, p001, p011, p111, argb);
        this.quad(p001, p000, p010, p011, argb);
        this.quad(p100, p101, p111, p110, argb);
        this.quad(p001, p101, p100, p000, argb);
        this.quad(p010, p110, p111, p011, argb);
    }

    void cube(float cx, float cy, float cz, float half, int argb) {
        this.box(cx - half, cy - half, cz - half, cx + half, cy + half, cz + half, argb);
    }

    void torus(float radius, float tube, int segments, int sides, IntUnaryOperator color) {
        for (int i = 0; i < segments; i++) {
            float a0 = Mth.TWO_PI * i / segments;
            float a1 = Mth.TWO_PI * (i + 1) / segments;
            int c0 = color.applyAsInt(i);
            int c1 = color.applyAsInt(i + 1);

            for (int j = 0; j < sides; j++) {
                float b0 = Mth.TWO_PI * j / sides;
                float b1 = Mth.TWO_PI * (j + 1) / sides;
                this.quad(ring(a0, b0, radius, tube), ring(a1, b0, radius, tube), ring(a1, b1, radius, tube), ring(a0, b1, radius, tube), c0, c1, c1, c0);
            }
        }
    }

    private static Vector3f ring(float around, float tubeAngle, float radius, float tube) {
        float r = radius + tube * Mth.cos(tubeAngle);
        return new Vector3f(r * Mth.cos(around), tube * Mth.sin(tubeAngle), r * Mth.sin(around));
    }

    void frustum(float r0, float r1, float y0, float y1, int segments, int argb, boolean capBottom, boolean capTop) {
        for (int i = 0; i < segments; i++) {
            float a0 = Mth.TWO_PI * i / segments;
            float a1 = Mth.TWO_PI * (i + 1) / segments;
            Vector3f b0 = new Vector3f(r0 * Mth.cos(a0), y0, r0 * Mth.sin(a0));
            Vector3f b1 = new Vector3f(r0 * Mth.cos(a1), y0, r0 * Mth.sin(a1));
            Vector3f t0 = new Vector3f(r1 * Mth.cos(a0), y1, r1 * Mth.sin(a0));
            Vector3f t1 = new Vector3f(r1 * Mth.cos(a1), y1, r1 * Mth.sin(a1));
            this.quad(b0, b1, t1, t0, argb);
            if (capBottom && r0 > 0.0F) {
                this.tri(new Vector3f(0.0F, y0, 0.0F), b1, b0, argb);
            }

            if (capTop && r1 > 0.0F) {
                this.tri(new Vector3f(0.0F, y1, 0.0F), t0, t1, argb);
            }
        }
    }

    void cone(float r, float y, Vector3f tip, int segments, IntUnaryOperator color, boolean capBase) {
        for (int i = 0; i < segments; i++) {
            float a0 = Mth.TWO_PI * i / segments;
            float a1 = Mth.TWO_PI * (i + 1) / segments;
            Vector3f b0 = new Vector3f(r * Mth.cos(a0), y, r * Mth.sin(a0));
            Vector3f b1 = new Vector3f(r * Mth.cos(a1), y, r * Mth.sin(a1));
            int c = color.applyAsInt(i);
            this.tri(b0, b1, tip, c);
            if (capBase) {
                this.tri(new Vector3f(0.0F, y, 0.0F), b1, b0, c);
            }
        }
    }

    void ball(float cx, float cy, float cz, float radius, int argb) {
        Vector3f[] axes = {
            new Vector3f(1.0F, 0.0F, 0.0F), new Vector3f(0.0F, 0.0F, 1.0F), new Vector3f(-1.0F, 0.0F, 0.0F), new Vector3f(0.0F, 0.0F, -1.0F)
        };
        Vector3f up = new Vector3f(0.0F, -1.0F, 0.0F);
        Vector3f down = new Vector3f(0.0F, 1.0F, 0.0F);

        for (int i = 0; i < 4; i++) {
            Vector3f a = axes[i];
            Vector3f b = axes[(i + 1) % 4];
            this.splitFace(cx, cy, cz, radius, up, b, a, argb);
            this.splitFace(cx, cy, cz, radius, down, a, b, argb);
        }
    }

    private void splitFace(float cx, float cy, float cz, float radius, Vector3f a, Vector3f b, Vector3f c, int argb) {
        Vector3f ab = new Vector3f(a).add(b).normalize();
        Vector3f bc = new Vector3f(b).add(c).normalize();
        Vector3f ca = new Vector3f(c).add(a).normalize();
        Vector3f[][] faces = {{a, ab, ca}, {ab, b, bc}, {ca, bc, c}, {ab, bc, ca}};
        for (Vector3f[] f : faces) {
            this.tri(at(cx, cy, cz, radius, f[0]), at(cx, cy, cz, radius, f[1]), at(cx, cy, cz, radius, f[2]), argb);
        }
    }

    private static Vector3f at(float cx, float cy, float cz, float radius, Vector3f dir) {
        return new Vector3f(cx + dir.x * radius, cy + dir.y * radius, cz + dir.z * radius);
    }

    void card(Vector3f a, Vector3f b, Vector3f c, Vector3f d, float u0, float u1, int ca, int cb, int cc, int cd) {
        Vector3f n = new Vector3f(c).sub(a).cross(new Vector3f(d).sub(b)).normalize();
        this.vertex(a.x, a.y, a.z, ca, u0, 0.0F, n.x, n.y, n.z);
        this.vertex(b.x, b.y, b.z, cb, u1, 0.0F, n.x, n.y, n.z);
        this.vertex(c.x, c.y, c.z, cc, u1, 1.0F, n.x, n.y, n.z);
        this.vertex(d.x, d.y, d.z, cd, u0, 1.0F, n.x, n.y, n.z);
    }
}
