package dev.infectedware.skysong.render.fx;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.infectedware.skysong.ModSettings;
import dev.infectedware.skysong.compat.Capture;
import dev.infectedware.skysong.compat.Compat;
import dev.infectedware.skysong.compat.WorldCtx;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public final class EntityFx {
    private static TextureTarget captured;
    private static BufferSource buffers;
    private static boolean capturing;
    private static boolean hasCapture;

    private EntityFx() {
    }

    public static boolean active() {
        return Fx.allowed() && ModSettings.shaderEntities;
    }

    public static boolean capturing() {
        return capturing;
    }

    public static void capture(WorldCtx ctx) {
        hasCapture = false;
        if (active()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null && mc.player != null) {
                RenderTarget main = mc.getMainRenderTarget();
                EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();
                Vec3 cam = Compat.camPos(ctx.camera());
                float tickDelta = ctx.partialTick();
                Frustum frustum = ctx.frustum();
                int count = 0;

                try {
                    for (Entity e : mc.level.entitiesForRendering()) {
                        if (wanted(mc, e) && !(e.distanceToSqr(cam) > 9216.0) && (frustum == null || dispatcher.shouldRender(e, frustum, cam.x, cam.y, cam.z))) {
                            if (count == 0) {
                                captured = Fx.ensure(captured, main.width, main.height, true, true);
                                Fx.clearAndBind(captured);
                                Capture.begin(captured);
                                if (buffers == null) {
                                    buffers = Compat.immediate(262144);
                                }

                                capturing = true;
                            }

                            BufferSource consumers = buffers;
                            double x = Mth.lerp(tickDelta, e.xOld, e.getX());
                            double y = Mth.lerp(tickDelta, e.yOld, e.getY());
                            double z = Mth.lerp(tickDelta, e.zOld, e.getZ());
                            Compat.renderEntity(e, x - cam.x, y - cam.y, z - cam.z, tickDelta, new PoseStack(), consumers);
                            consumers.endBatch();
                            count++;
                        }
                    }

                    hasCapture = count > 0;
                } catch (Throwable t) {
                    Fx.fail("players", t);
                } finally {
                    if (capturing) {
                        capturing = false;
                        Capture.end();
                        Fx.restore(main);
                    }
                }
            }
        }
    }

    public static void composite() {
        if (hasCapture) {
            hasCapture = false;
            if (active()) {
                Minecraft mc = Minecraft.getInstance();
                RenderTarget main = mc.getMainRenderTarget();

                try {
                    RenderTarget entityLayer = mc.levelRenderer.getItemEntityTarget();
                    RenderTarget depth = entityLayer != null ? entityLayer : main;
                    ShapeFx.Look look = new ShapeFx.Look(
                        ModSettings.shaderEntitiesFill,
                        ModSettings.shaderEntitiesOpacity,
                        false,
                        ModSettings.shaderEntitiesOutline ? ModSettings.shaderEntitiesOutlineWidth : 0.0F,
                        ModSettings.shaderEntitiesGlowRadius,
                        ModSettings.shaderEntitiesGlow ? ModSettings.shaderEntitiesGlowStrength : 0.0F,
                        0.35F
                    );
                    ShapeFx.composite(captured, main, depth, null, look);
                } catch (Throwable t) {
                    Fx.fail("players", t);
                } finally {
                    Fx.restore(main);
                }
            }
        }
    }

    private static boolean wanted(Minecraft mc, Entity e) {
        if (e instanceof LivingEntity living && living.isAlive() && !e.isInvisible() && !e.isSpectator()) {
            int targets = ModSettings.shaderEntitiesTargets;
            if (e == mc.getCameraEntity()) {
                return (targets & 4) != 0 && !mc.options.getCameraType().isFirstPerson();
            } else {
                return e instanceof Player ? (targets & 1) != 0 : e instanceof Mob && (targets & 2) != 0;
            }
        } else {
            return false;
        }
    }
}
