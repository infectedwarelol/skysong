package dev.infectedware.skysong.mixin;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.infectedware.skysong.compat.Hooks;
import dev.infectedware.skysong.render.fx.SkyFx;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.LevelRenderState;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
abstract class WorldRendererMixin {
    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void skysong$frame(
        GraphicsResourceAllocator graphicsResourceAllocator,
        DeltaTracker deltaTracker,
        boolean bl,
        Camera camera,
        Matrix4f matrix4f,
        Matrix4f matrix4f2,
        Matrix4f matrix4f3,
        GpuBufferSlice gpuBufferSlice,
        Vector4f vector4f,
        boolean bl2,
        CallbackInfo ci
    ) {
        Hooks.beginWorld(camera, matrix4f, matrix4f2, matrix4f3, deltaTracker.getGameTimeDeltaPartialTick(false));
    }

    @Inject(method = "submitEntities", at = @At("HEAD"))
    private void skysong$entities(PoseStack poseStack, LevelRenderState levelRenderState, SubmitNodeCollector submitNodeCollector, CallbackInfo ci) {
        Hooks.entitiesQueued(poseStack);
    }

    @Inject(method = "renderLevel", at = @At("TAIL"))
    private void skysong$last(
        GraphicsResourceAllocator graphicsResourceAllocator,
        DeltaTracker deltaTracker,
        boolean bl,
        Camera camera,
        Matrix4f matrix4f,
        Matrix4f matrix4f2,
        Matrix4f matrix4f3,
        GpuBufferSlice gpuBufferSlice,
        Vector4f vector4f,
        boolean bl2,
        CallbackInfo ci
    ) {
        Hooks.lastNow();
    }

    @Inject(method = "addCloudsPass", at = @At("HEAD"), cancellable = true)
    private void skysong$renderClouds(CallbackInfo ci) {
        if (SkyFx.active()) {
            ci.cancel();
        }
    }
}
