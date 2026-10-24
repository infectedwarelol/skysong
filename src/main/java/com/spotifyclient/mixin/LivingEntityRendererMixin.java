package com.spotifyclient.mixin;

import com.spotifyclient.hud.NameTags;
import com.spotifyclient.render.fx.EntityFx;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntityRenderer.class)
abstract class LivingEntityRendererMixin {
    @Inject(method = "shouldShowName(Lnet/minecraft/world/entity/LivingEntity;D)Z", at = @At("HEAD"), cancellable = true)
    private void versefall$hasLabel(LivingEntity entity, double distance, CallbackInfoReturnable<Boolean> cir) {
        if (NameTags.hidesNameTagFor(entity) || EntityFx.capturing()) {
            cir.setReturnValue(false);
        }
    }
}
