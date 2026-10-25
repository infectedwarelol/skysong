package dev.infectedware.skysong.mixin;

import dev.infectedware.skysong.render.fx.SkyFx;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.world.level.material.FogType;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FogRenderer.class)
abstract class FogColorMixin {
    @Inject(method = "computeFogColor", at = @At("RETURN"), cancellable = true)
    private void skysong$skyFog(Camera camera, float f, ClientLevel clientLevel, int i, float g, CallbackInfoReturnable<Vector4f> cir) {
        if (SkyFx.active() && camera.getFluidInCamera() == FogType.NONE && SkyFx.showsSky(camera)) {
            float[] c = SkyFx.fogColor();
            cir.setReturnValue(new Vector4f(c[0], c[1], c[2], cir.getReturnValue().w()));
        }
    }
}
