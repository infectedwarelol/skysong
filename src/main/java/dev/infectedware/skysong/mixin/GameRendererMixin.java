package dev.infectedware.skysong.mixin;

import dev.infectedware.skysong.render.AspectRatio;
import dev.infectedware.skysong.render.fx.HandFx;
import dev.infectedware.skysong.viewmodel.ViewModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
abstract class GameRendererMixin {
    @Unique
    private static final String RENDER_HANDS = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderHandsWithItems(FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/player/LocalPlayer;I)V";
    @Unique
    private static final String PERSPECTIVE = "Lorg/joml/Matrix4f;perspective(FFFF)Lorg/joml/Matrix4f;";

    @ModifyArg(method = "getProjectionMatrix", at = @At(value = "INVOKE", target = PERSPECTIVE, remap = false), index = 1)
    private float skysong$aspect(float aspect) {
        return AspectRatio.aspect(aspect);
    }

    @Inject(method = "renderItemInHand", at = @At("HEAD"))
    private void skysong$handsStart(float f, boolean bl, Matrix4f matrix4f, CallbackInfo ci) {
        AspectRatio.hands(true);
    }

    @Inject(method = "renderItemInHand", at = @At("RETURN"))
    private void skysong$handsEnd(float f, boolean bl, Matrix4f matrix4f, CallbackInfo ci) {
        AspectRatio.hands(false);
    }

    @Inject(method = "renderItemInHand", at = @At(value = "INVOKE", target = RENDER_HANDS))
    private void skysong$beforeHands(float f, boolean bl, Matrix4f matrix4f, CallbackInfo ci) {
        ViewModel.beginHandFrame();
        HandFx.beforeHands();
    }

    @Inject(method = "renderItemInHand", at = @At(value = "INVOKE", target = RENDER_HANDS, shift = Shift.AFTER))
    private void skysong$afterHands(float f, boolean bl, Matrix4f matrix4f, CallbackInfo ci) {
        if (HandFx.capturing()) {
            Minecraft mc = Minecraft.getInstance();
            mc.gameRenderer.getFeatureRenderDispatcher().renderAllFeatures();
            mc.renderBuffers().bufferSource().endBatch();
        }

        HandFx.afterHands();
    }
}
