package dev.infectedware.skysong.render.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.infectedware.skysong.ModSettings;
import dev.infectedware.skysong.compat.Compat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

final class PiecesFx {
    private static final float CELL = 0.3F;
    private static final float GRAVITY = 14.0F;
    private static final float SHRINK = 0.5F;
    private static final int FLESH = 0xFF8E1414;
    private static final int FLESH_SIDE = 0xFF6A0E0E;
    private static final List<Body> BODIES = new ArrayList<>();

    private PiecesFx() {
    }

    private static final class Chunk {
        float cx;
        float cy;
        float cz;
        float hx;
        float hy;
        float hz;
        float vx;
        float vy;
        float vz;
        final Vector3f axis = new Vector3f();
        float spin;
        float land;
        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float minZ = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        float maxZ = -Float.MAX_VALUE;
    }

    private static final class Body {
        final List<ModelSnapshot.Part> parts;
        final int[][] quadChunk;
        final List<Chunk> chunks = new ArrayList<>();
        final double x;
        final double y;
        final double z;
        final long born;
        final float delay;
        final float life;

        Body(List<ModelSnapshot.Part> parts, Vec3 at, float delay, float life) {
            this.parts = parts;
            this.quadChunk = new int[parts.size()][];
            this.x = at.x;
            this.y = at.y;
            this.z = at.z;
            this.born = System.currentTimeMillis();
            this.delay = delay;
            this.life = life;
        }
    }

    static void init() {
        WorldRenderEvents.BEFORE_ENTITIES.register(context -> render(context.matrices(), context.commandQueue()));
    }

    static void spawn(List<ModelSnapshot.Part> parts, Vec3 at, float height, float delay, long seed) {
        Body body = new Body(parts, at, delay, delay + 1.6F + ModSettings.killPiecesLie);
        Map<Long, Integer> cells = new HashMap<>();
        for (int p = 0; p < parts.size(); p++) {
            ModelSnapshot.Part part = parts.get(p);
            int quads = part.vertices / 4;
            body.quadChunk[p] = new int[quads];
            for (int q = 0; q < quads; q++) {
                float cx = 0.0F;
                float cy = 0.0F;
                float cz = 0.0F;
                for (int k = 0; k < 4; k++) {
                    int i = (q * 4 + k) * 8;
                    cx += part.data[i] / 4.0F;
                    cy += part.data[i + 1] / 4.0F;
                    cz += part.data[i + 2] / 4.0F;
                }

                long key = (long)Mth.floor(cx / CELL) * 73856093L ^ (long)Mth.floor(cy / CELL) * 19349663L ^ (long)Mth.floor(cz / CELL) * 83492791L;
                int index = cells.computeIfAbsent(key, k -> {
                    body.chunks.add(new Chunk());
                    return body.chunks.size() - 1;
                });
                body.quadChunk[p][q] = index;
                Chunk c = body.chunks.get(index);
                for (int k = 0; k < 4; k++) {
                    int i = (q * 4 + k) * 8;
                    c.minX = Math.min(c.minX, part.data[i]);
                    c.minY = Math.min(c.minY, part.data[i + 1]);
                    c.minZ = Math.min(c.minZ, part.data[i + 2]);
                    c.maxX = Math.max(c.maxX, part.data[i]);
                    c.maxY = Math.max(c.maxY, part.data[i + 1]);
                    c.maxZ = Math.max(c.maxZ, part.data[i + 2]);
                }
            }
        }

        Random r = new Random(seed);
        float mid = height * 0.5F;
        for (Chunk c : body.chunks) {
            c.cx = (c.minX + c.maxX) / 2.0F;
            c.cy = (c.minY + c.maxY) / 2.0F;
            c.cz = (c.minZ + c.maxZ) / 2.0F;
            c.hx = (c.maxX - c.minX) / 2.0F;
            c.hy = (c.maxY - c.minY) / 2.0F;
            c.hz = (c.maxZ - c.minZ) / 2.0F;
            float dx = c.cx + (r.nextFloat() - 0.5F) * 0.3F;
            float dz = c.cz + (r.nextFloat() - 0.5F) * 0.3F;
            float len = Math.max(0.05F, (float)Math.sqrt(dx * dx + dz * dz));
            float speed = 0.7F + r.nextFloat() * 1.5F;
            c.vx = dx / len * speed;
            c.vz = dz / len * speed;
            c.vy = 1.2F + r.nextFloat() * 1.8F + (c.cy - mid) * 1.0F;
            c.axis.set(r.nextFloat() - 0.5F, r.nextFloat() - 0.5F, r.nextFloat() - 0.5F);
            if (c.axis.lengthSquared() < 1.0E-4F) {
                c.axis.set(0.0F, 1.0F, 0.0F);
            }

            c.axis.normalize();
            c.spin = (4.0F + r.nextFloat() * 9.0F) * (r.nextBoolean() ? 1.0F : -1.0F);
            float rest = Math.max(0.03F, Math.min(c.hx, Math.min(c.hy, c.hz)));
            float drop = c.cy - rest;
            c.land = (c.vy + (float)Math.sqrt(c.vy * c.vy + 2.0F * GRAVITY * Math.max(0.0F, drop))) / GRAVITY;
        }

        BODIES.add(body);
    }

    private static void render(PoseStack pose, SubmitNodeCollector collector) {
        Minecraft mc = Minecraft.getInstance();
        if (BODIES.isEmpty()) {
            return;
        }

        if (mc.level == null || !Fx.allowed()) {
            BODIES.clear();
            return;
        }

        long now = System.currentTimeMillis();
        Vec3 cam = Compat.camPos(mc.gameRenderer.getMainCamera());
        Iterator<Body> it = BODIES.iterator();
        while (it.hasNext()) {
            Body b = it.next();
            float t = (now - b.born) / 1000.0F;
            if (t > b.life) {
                it.remove();
                continue;
            }

            float scale = Mth.clamp((b.life - t) / SHRINK, 0.0F, 1.0F);
            float tt = Math.max(0.0F, t - b.delay);
            int light = LevelRenderer.getLightColor(mc.level, BlockPos.containing(b.x, b.y + 0.5, b.z));
            pose.pushPose();
            pose.translate(b.x - cam.x, b.y - cam.y, b.z - cam.z);
            for (int p = 0; p < b.parts.size(); p++) {
                ModelSnapshot.Part part = b.parts.get(p);
                if (part.location == null) {
                    continue;
                }

                int index = p;
                collector.submitCustomGeometry(pose, RenderTypes.entityCutoutNoCull(part.location), (base, out) -> skin(b, index, tt, scale, base, out, light));
            }

            if (tt > 0.0F) {
                collector.submitCustomGeometry(pose, RenderTypes.entityCutoutNoCull(CardFx.atlas()), (base, out) -> flesh(b, tt, scale, base, out, light));
            }

            pose.popPose();
        }
    }

    private static void place(Chunk c, float t, float scale, Vector3f point, Quaternionf rot) {
        float ft = Math.min(t, c.land);
        float ox = c.vx * ft;
        float oz = c.vz * ft;
        float oy = c.vy * ft - 0.5F * GRAVITY * ft * ft;
        if (t > c.land) {
            float slide = (1.0F - (float)Math.exp(-(t - c.land) * 6.0F)) * 0.12F;
            ox += c.vx * slide;
            oz += c.vz * slide;
        }

        point.sub(c.cx, c.cy, c.cz).mul(scale);
        rot.transform(point);
        point.add(c.cx + ox, c.cy + oy, c.cz + oz);
    }

    private static Quaternionf rotation(Chunk c, float t) {
        return new Quaternionf().fromAxisAngleRad(c.axis.x, c.axis.y, c.axis.z, c.spin * Math.min(t, c.land));
    }

    private static void skin(Body b, int p, float t, float scale, PoseStack.Pose pose, VertexConsumer out, int light) {
        ModelSnapshot.Part part = b.parts.get(p);
        int[] map = b.quadChunk[p];
        float[] d = part.data;
        Vector3f pos = new Vector3f();
        Vector3f normal = new Vector3f();
        Quaternionf[] rots = new Quaternionf[b.chunks.size()];
        for (int v = 0; v < part.vertices; v++) {
            int i = v * 8;
            int ci = map[v / 4];
            Chunk c = b.chunks.get(ci);
            if (rots[ci] == null) {
                rots[ci] = rotation(c, t);
            }

            pos.set(d[i], d[i + 1], d[i + 2]);
            normal.set(d[i + 5], d[i + 6], d[i + 7]);
            if (t > 0.0F) {
                place(c, t, scale, pos, rots[ci]);
                rots[ci].transform(normal);
            } else if (scale < 1.0F) {
                pos.mul(scale);
            }

            CardFx.vertex(out, pose, pos.x, pos.y, pos.z, -1, d[i + 3], d[i + 4], light, normal.x, normal.y, normal.z);
        }
    }

    private static void flesh(Body b, float t, float scale, PoseStack.Pose pose, VertexConsumer out, int light) {
        float u = CardFx.whiteU();
        float v = CardFx.whiteV();
        int[][] faces = {{0, 1, 3, 2}, {5, 4, 6, 7}, {4, 0, 2, 6}, {1, 5, 7, 3}, {4, 5, 1, 0}, {2, 3, 7, 6}};
        float[][] normals = {{0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}, {0, -1, 0}, {0, 1, 0}};
        Vector3f[] corners = new Vector3f[8];
        Vector3f n = new Vector3f();
        for (Chunk c : b.chunks) {
            float hx = c.hx * 0.6F;
            float hy = c.hy * 0.6F;
            float hz = c.hz * 0.6F;
            if (hx < 0.01F || hy < 0.01F || hz < 0.01F) {
                continue;
            }

            Quaternionf rot = rotation(c, t);
            for (int k = 0; k < 8; k++) {
                corners[k] = new Vector3f(c.cx + ((k & 1) == 0 ? -hx : hx), c.cy + ((k & 2) == 0 ? -hy : hy), c.cz + ((k & 4) == 0 ? -hz : hz));
                place(c, t, scale, corners[k], rot);
            }

            for (int f = 0; f < 6; f++) {
                rot.transform(n.set(normals[f][0], normals[f][1], normals[f][2]));
                int shade = f >= 4 ? FLESH : FLESH_SIDE;
                for (int k : faces[f]) {
                    Vector3f q = corners[k];
                    CardFx.vertex(out, pose, q.x, q.y, q.z, shade, u, v, light, n.x, n.y, n.z);
                }
            }
        }
    }
}
