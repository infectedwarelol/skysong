package com.spotifyclient.render.fx;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.spotifyclient.ModSettings;
import com.spotifyclient.compat.Compat;
import com.spotifyclient.compat.Gpu;
import com.spotifyclient.compat.WorldCtx;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

public final class MotionBlur {
    public static final String[] QUALITY = new String[]{"Low", "Medium", "High"};
    private static final int[] SAMPLES = new int[]{6, 10, 16};
    private static TextureTarget scene;
    private static Matrix4f prevViewProj;
    private static Vec3 prevCam;
    private static long prevNanos = -1L;
    private static long drawnFrame = -1L;
    private static float lastScale;
    private static float lastShift;

    private MotionBlur() {
    }

    public static boolean active() {
        return Fx.allowed() && ModSettings.motionBlur && Fx.motion != null;
    }

    public static boolean drewThisFrame() {
        return Fx.recent(drawnFrame);
    }

    public static float lastScale() {
        return lastScale;
    }

    public static float lastShift() {
        return lastShift;
    }

    static void forget() {
        prevViewProj = null;
        prevCam = null;
        prevNanos = -1L;
    }

    public static void apply(WorldCtx ctx) {
        Minecraft mc = Minecraft.getInstance();
        Matrix4f viewProj = new Matrix4f(ctx.projection()).mul(ctx.view());
        Vec3 cam = Compat.camPos(ctx.camera());
        long now = System.nanoTime();
        Matrix4f prev = prevViewProj;
        Vec3 before = prevCam;
        float dt = prevNanos < 0L ? 0.0F : (float)(now - prevNanos) / 1.0E9F;
        prevViewProj = viewProj;
        prevCam = cam;
        prevNanos = now;
        if (active() && mc.level != null) {
            if (prev != null && before != null && !(dt <= 0.0F) && !(dt > 0.2F) && !(cam.distanceToSqr(before) > 36.0)) {
                RenderTarget main = mc.getMainRenderTarget();

                try {
                    scene = Fx.ensure(scene, main.width, main.height, false, true);
                    Fx.blitColor(main, scene);
                    float scale = ModSettings.motionBlurStrength * Math.min(4.0F, 0.016666668F / dt);
                    lastScale = scale;
                    Matrix4f inv = new Matrix4f(viewProj).invert();
                    Vec3 delta = cam.subtract(before);
                    Vector4f p = new Vector4f(0.0F, 0.0F, 0.99F, 1.0F).mul(inv);
                    p.div(p.w);
                    p.w = 1.0F;
                    Vector4f back = new Vector4f(p).mul(prev);
                    lastShift = (float)Math.hypot(back.x / back.w * 0.5F * main.width, back.y / back.w * 0.5F * main.height);
                    Gpu.bind(main);
                    Fx.noBlend();
                    Gpu.texture(0, Gpu.color(scene));
                    Gpu.texture(1, Gpu.depth(main));
                    Fx.set(Fx.motion, "InvViewProj", inv);
                    Fx.set(Fx.motion, "PrevViewProj", prev);
                    Fx.set(Fx.motion, "CamDelta", (float)delta.x, (float)delta.y, (float)delta.z, ModSettings.motionBlurMovement ? 1.0F : 0.0F);
                    int q = Math.max(0, Math.min(SAMPLES.length - 1, ModSettings.motionBlurQuality));
                    Fx.set(Fx.motion, "Params", scale, ModSettings.motionBlurMax / 100.0F, SAMPLES[q], ModSettings.motionBlurSharpCenter ? 1.0F : 0.0F);
                    Fx.set(Fx.motion, "OutSize", main.width, main.height);
                    Fx.draw(Fx.motion);
                    drawnFrame = System.currentTimeMillis();
                } catch (Throwable t) {
                    Fx.fail("motion blur", t);
                } finally {
                    Fx.restore(main);
                }
            }
        }
    }
}
