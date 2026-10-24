package dev.infectedware.skysong.mixin;

import dev.infectedware.skysong.hud.NameTags;
import dev.infectedware.skysong.render.fx.Afterimages;
import dev.infectedware.skysong.render.fx.EntityFx;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderer.class)
abstract class EntityRendererMixin {
    @Inject(method = "shouldShowName", at = @At("HEAD"), cancellable = true)
    private void skysong$hasLabel(Entity entity, double distance, CallbackInfoReturnable<Boolean> cir) {
        if (NameTags.hidesNameTagFor(entity) || EntityFx.capturing() || Afterimages.recording()) {
            cir.setReturnValue(false);
        }
    }
}
