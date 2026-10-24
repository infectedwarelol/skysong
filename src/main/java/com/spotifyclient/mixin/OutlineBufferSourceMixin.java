package com.spotifyclient.mixin;

import com.spotifyclient.compat.Hooks;
import net.minecraft.client.renderer.OutlineBufferSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(OutlineBufferSource.class)
abstract class OutlineBufferSourceMixin {
    @Inject(method = "endOutlineBatch", at = @At("TAIL"))
    private void versefall$entitiesDrawn(CallbackInfo ci) {
        Hooks.entitiesDrawn();
    }
}
