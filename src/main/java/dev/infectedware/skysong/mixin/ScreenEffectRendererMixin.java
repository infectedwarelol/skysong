package dev.infectedware.skysong.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.infectedware.skysong.ModSettings;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.model.ItemTransform;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ScreenEffectRenderer.class)
abstract class ScreenEffectRendererMixin {
    @Unique
    private static final ItemTransform VANILLA_FIXED = new ItemTransform(new Vector3f(0.0F, 180.0F, 0.0F), new Vector3f(), new Vector3f(1.0F, 1.0F, 1.0F));

    @Inject(
        method = "renderItemActivationAnimation",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;scale(FFF)V", shift = Shift.AFTER)
    )
    private void skysong$shrinkTotem(PoseStack poseStack, float f, SubmitNodeCollector submitNodeCollector, CallbackInfo ci) {
        if (ModSettings.pvpSmallTotem) {
            float scale = ModSettings.pvpTotemScale;
            poseStack.scale(scale, scale, scale);
        }
    }

    @WrapOperation(
        method = "renderItemActivationAnimation",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V"
        )
    )
    private void skysong$ignorePackTotemSize(
        ItemStackRenderState state, PoseStack poseStack, SubmitNodeCollector collector, int light, int overlay, int outline, Operation<Void> original
    ) {
        if (ModSettings.pvpSmallTotem && ModSettings.pvpTotemIgnorePack) {
            for (int i = 0; i < state.activeLayerCount; i++) {
                state.layers[i].transform = VANILLA_FIXED;
            }
        }

        original.call(state, poseStack, collector, light, overlay, outline);
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
