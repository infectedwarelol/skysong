package com.spotifyclient.mixin;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.spotifyclient.compat.Compat;
import com.spotifyclient.render.AspectRatio;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.CachedPerspectiveProjectionMatrixBuffer;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CachedPerspectiveProjectionMatrixBuffer.class)
abstract class HandProjectionMixin {
    @Shadow
    private int width;
    @Unique
    private float versefall$builtAspect = Float.NaN;

    @Inject(method = "getBuffer", at = @At("HEAD"))
    private void versefall$rebuild(int w, int h, float fov, CallbackInfoReturnable<GpuBufferSlice> cir) {
        if (this.versefall$isHands()) {
            float want = AspectRatio.handAspect((float)w / Math.max(1, h));
            if (want != this.versefall$builtAspect) {
                this.versefall$builtAspect = want;
                this.width = -1;
            }
        }
    }

    @Unique
    private boolean versefall$isHands() {
        return (Object)this == Minecraft.getInstance().gameRenderer.hud3dProjectionMatrixBuffer;
    }

    @ModifyArg(
        method = "createProjectionMatrix",
        at = @At(value = "INVOKE", target = "Lorg/joml/Matrix4f;perspective(FFFF)Lorg/joml/Matrix4f;", remap = false),
        index = 1
    )
    private float versefall$aspect(float aspect) {
        return this.versefall$isHands() ? AspectRatio.handAspect(aspect) : aspect;
    }

    @Inject(method = "createProjectionMatrix", at = @At("RETURN"))
    private void versefall$keep(int width, int height, float fov, CallbackInfoReturnable<Matrix4f> cir) {
        if (this.versefall$isHands()) {
            Compat.handProjection.set((Matrix4fc)cir.getReturnValue());
        }
    }
}
