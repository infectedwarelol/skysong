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
        GraphicsResourceAllocator allocator,
        DeltaTracker delta,
        boolean outline,
        Camera camera,
        Matrix4f view,
        Matrix4f projection,
        Matrix4f cullProjection,
        GpuBufferSlice fog,
        Vector4f fogColor,
        boolean sky,
        CallbackInfo ci
    ) {
        Hooks.beginWorld(camera, view, projection, cullProjection, delta.getGameTimeDeltaPartialTick(false));
    }

    @Inject(method = "submitEntities", at = @At("HEAD"))
    private void skysong$entities(PoseStack pose, LevelRenderState state, SubmitNodeCollector collector, CallbackInfo ci) {
        Hooks.entitiesQueued(pose);
    }

    @Inject(method = "renderLevel", at = @At("TAIL"))
    private void skysong$last(
        GraphicsResourceAllocator allocator,
        DeltaTracker delta,
        boolean outline,
        Camera camera,
        Matrix4f view,
        Matrix4f projection,
        Matrix4f cullProjection,
        GpuBufferSlice fog,
        Vector4f fogColor,
        boolean sky,
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
