package com.spotifyclient.mixin;

import com.spotifyclient.render.fx.SkyFx;
import net.minecraft.client.renderer.SkyRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SkyRenderer.class)
abstract class SkyRendererMixin {
    @Unique
    private static boolean versefall$ours;

    @Inject(method = "renderSkyDisc", at = @At("HEAD"), cancellable = true)
    private void versefall$sky(CallbackInfo ci) {
        versefall$ours = SkyFx.renderNow();
        if (versefall$ours) {
            ci.cancel();
        }
    }

    @Inject(method = "renderSunriseAndSunset", at = @At("HEAD"), cancellable = true)
    private void versefall$sunrise(CallbackInfo ci) {
        if (versefall$ours) {
            ci.cancel();
        }
    }

    @Inject(method = "renderSunMoonAndStars", at = @At("HEAD"), cancellable = true)
    private void versefall$sunMoonStars(CallbackInfo ci) {
        if (versefall$ours) {
            ci.cancel();
        }
    }

    @Inject(method = "renderDarkDisc", at = @At("HEAD"), cancellable = true)
    private void versefall$darkDisc(CallbackInfo ci) {
        if (versefall$ours) {
            ci.cancel();
        }
    }

    @Inject(method = "renderEndSky", at = @At("HEAD"), cancellable = true)
    private void versefall$endSky(CallbackInfo ci) {
        if (SkyFx.renderNow()) {
            ci.cancel();
        }
    }
}
