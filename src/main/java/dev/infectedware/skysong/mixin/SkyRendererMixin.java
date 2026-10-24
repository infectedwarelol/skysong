package dev.infectedware.skysong.mixin;

import dev.infectedware.skysong.render.fx.SkyFx;
import net.minecraft.client.renderer.SkyRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SkyRenderer.class)
abstract class SkyRendererMixin {
    @Unique
    private static boolean skysong$ours;

    @Inject(method = "renderSkyDisc", at = @At("HEAD"), cancellable = true)
    private void skysong$sky(CallbackInfo ci) {
        skysong$ours = SkyFx.renderNow();
        if (skysong$ours) {
            ci.cancel();
        }
    }

    @Inject(method = "renderSunriseAndSunset", at = @At("HEAD"), cancellable = true)
    private void skysong$sunrise(CallbackInfo ci) {
        if (skysong$ours) {
            ci.cancel();
        }
    }

    @Inject(method = "renderSunMoonAndStars", at = @At("HEAD"), cancellable = true)
    private void skysong$sunMoonStars(CallbackInfo ci) {
        if (skysong$ours) {
            ci.cancel();
        }
    }

    @Inject(method = "renderDarkDisc", at = @At("HEAD"), cancellable = true)
    private void skysong$darkDisc(CallbackInfo ci) {
        if (skysong$ours) {
            ci.cancel();
        }
    }

    @Inject(method = "renderEndSky", at = @At("HEAD"), cancellable = true)
    private void skysong$endSky(CallbackInfo ci) {
        if (SkyFx.renderNow()) {
            ci.cancel();
        }
    }
}
