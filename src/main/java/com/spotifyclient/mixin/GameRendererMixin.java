package com.spotifyclient.mixin;

import com.spotifyclient.render.AspectRatio;
import com.spotifyclient.render.fx.HandFx;
import com.spotifyclient.viewmodel.ViewModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
abstract class GameRendererMixin {
    private static final String RENDER_ITEM = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderHandsWithItems(FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/player/LocalPlayer;I)V";
    private static final String PERSPECTIVE = "Lorg/joml/Matrix4f;perspective(FFFF)Lorg/joml/Matrix4f;";

    @ModifyArg(
        method = "getProjectionMatrix",
        at = @At(value = "INVOKE", target = "Lorg/joml/Matrix4f;perspective(FFFF)Lorg/joml/Matrix4f;", remap = false),
        index = 1
    )
    private float versefall$aspect(float aspect) {
        return AspectRatio.aspect(aspect);
    }

    @Inject(method = "renderItemInHand", at = @At("HEAD"))
    private void versefall$handsStart(float tickDelta, boolean sleeping, Matrix4f rotation, CallbackInfo ci) {
        handsStart();
    }

    @Inject(method = "renderItemInHand", at = @At("RETURN"))
    private void versefall$handsEnd(float tickDelta, boolean sleeping, Matrix4f rotation, CallbackInfo ci) {
        handsEnd();
    }

    @Inject(
        method = "renderItemInHand",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderHandsWithItems(FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/player/LocalPlayer;I)V"
        )
    )
    private void versefall$beforeHands(float tickDelta, boolean sleeping, Matrix4f rotation, CallbackInfo ci) {
        beforeHands();
    }

    @Inject(
        method = "renderItemInHand",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderHandsWithItems(FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/player/LocalPlayer;I)V",
            shift = Shift.AFTER
        )
    )
    private void versefall$afterHands(float tickDelta, boolean sleeping, Matrix4f rotation, CallbackInfo ci) {
        afterHands();
    }

    private static void handsStart() {
        AspectRatio.hands(true);
    }

    private static void handsEnd() {
        AspectRatio.hands(false);
    }

    private static void afterHands() {
        if (HandFx.capturing()) {
            Minecraft mc = Minecraft.getInstance();
            mc.gameRenderer.getFeatureRenderDispatcher().renderAllFeatures();
            mc.renderBuffers().bufferSource().endBatch();
        }

        HandFx.afterHands();
    }

    private static void beforeHands() {
        ViewModel.beginHandFrame();
        HandFx.beforeHands();
    }
}
