package com.spotifyclient.mixin;

import com.spotifyclient.hud.NameTags;
import com.spotifyclient.render.fx.Afterimages;
import com.spotifyclient.render.fx.EntityFx;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderer.class)
abstract class EntityRendererMixin {
    @Inject(method = "shouldShowName", at = @At("HEAD"), cancellable = true)
    private void versefall$hasLabel(Entity entity, double distance, CallbackInfoReturnable<Boolean> cir) {
        if (NameTags.hidesNameTagFor(entity) || EntityFx.capturing() || Afterimages.recording()) {
            cir.setReturnValue(false);
        }
    }
}
