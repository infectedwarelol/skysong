package dev.infectedware.skysong.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.infectedware.skysong.ModSettings;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ScreenEffectRenderer.class)
abstract class ScreenEffectRendererMixin {
    @Inject(method = "renderItemActivationAnimation", at = @At("HEAD"))
    private void skysong$shrinkTotemStart(PoseStack poseStack, float f, SubmitNodeCollector submitNodeCollector, CallbackInfo ci) {
        poseStack.pushPose();
        if (ModSettings.pvpSmallTotem) {
            float scale = ModSettings.pvpTotemScale;
            poseStack.scale(scale, scale, scale);
        }
    }

    @Inject(method = "renderItemActivationAnimation", at = @At("RETURN"))
    private void skysong$shrinkTotemEnd(PoseStack poseStack, float f, SubmitNodeCollector submitNodeCollector, CallbackInfo ci) {
        poseStack.popPose();
    }

    @Inject(method = "renderFire", at = @At("HEAD"))
    private static void skysong$lowerFireStart(PoseStack poseStack, MultiBufferSource multiBufferSource, TextureAtlasSprite textureAtlasSprite, CallbackInfo ci) {
        poseStack.pushPose();
        if (ModSettings.pvpLowFire) {
            poseStack.translate(0.0F, -ModSettings.pvpFireDrop, 0.0F);
        }
    }

    @Inject(method = "renderFire", at = @At("RETURN"))
    private static void skysong$lowerFireEnd(PoseStack poseStack, MultiBufferSource multiBufferSource, TextureAtlasSprite textureAtlasSprite, CallbackInfo ci) {
        poseStack.popPose();
    }
}
