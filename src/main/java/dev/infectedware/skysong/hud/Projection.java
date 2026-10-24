package dev.infectedware.skysong.hud;

import dev.infectedware.skysong.compat.Compat;
import dev.infectedware.skysong.compat.WorldCtx;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

public final class Projection {
    private static final Matrix4f view = new Matrix4f();
    private static final Matrix4f proj = new Matrix4f();
    private static Vec3 camera = Vec3.ZERO;
    private static float tickDelta;
    private static boolean valid;
    private static final Vector4f scratch = new Vector4f();

    private Projection() {
    }

    public static void capture(WorldCtx ctx) {
        view.set(ctx.view());
        proj.set(ctx.projection());
        camera = Compat.camPos(ctx.camera());
        tickDelta = ctx.partialTick();
        valid = true;
    }

    public static float tickDelta() {
        return tickDelta;
    }

    public static Vec3 camera() {
        return camera;
    }

    public static float[] project(double x, double y, double z) {
        if (!valid) {
            return null;
        }

        scratch.set((float)(x - camera.x), (float)(y - camera.y), (float)(z - camera.z), 1.0F);
        view.transform(scratch);
        proj.transform(scratch);
        if (scratch.w <= 1.0E-4F) {
            return null;
        }

        float sx = (scratch.x / scratch.w * 0.5F + 0.5F) * HudSpace.width();
        float sy = (1.0F - (scratch.y / scratch.w * 0.5F + 0.5F)) * HudSpace.height();
        return !Float.isNaN(sx) && !Float.isNaN(sy) ? new float[]{sx, sy, scratch.w} : null;
    }
}
