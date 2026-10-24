package com.spotifyclient.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.spotifyclient.compat.Capture;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
abstract class MainTargetMixin {
    @Inject(method = "getMainRenderTarget", at = @At("HEAD"), cancellable = true)
    private void versefall$capture(CallbackInfoReturnable<RenderTarget> cir) {
        RenderTarget into = Capture.target();
        if (into != null) {
            cir.setReturnValue(into);
        }
    }
}
