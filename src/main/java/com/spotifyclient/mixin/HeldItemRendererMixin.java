package com.spotifyclient.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.spotifyclient.viewmodel.ViewModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
abstract class HeldItemRendererMixin {
    private static final String RENDER_ITEM = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V";
    private static final String RENDER_ARM = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderPlayerArm(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;IFFLnet/minecraft/world/entity/HumanoidArm;)V";

    @Inject(method = "renderArmWithItem", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V", shift = Shift.AFTER))
    private void versefall$offset(
        AbstractClientPlayer player,
        float tickDelta,
        float pitch,
        InteractionHand hand,
        float swingProgress,
        ItemStack item,
        float equipProgress,
        PoseStack matrices,
        SubmitNodeCollector vertexConsumers,
        int light,
        CallbackInfo ci
    ) {
        if (!item.isEmpty()) {
            ViewModel.applyOffset(matrices, hand);
        }
    }

    @Inject(
        method = "renderArmWithItem",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V"
        )
    )
    private void versefall$itemTransform(
        AbstractClientPlayer player,
        float tickDelta,
        float pitch,
        InteractionHand hand,
        float swingProgress,
        ItemStack item,
        float equipProgress,
        PoseStack matrices,
        SubmitNodeCollector vertexConsumers,
        int light,
        CallbackInfo ci
    ) {
        float[] center = ViewModel.itemCenter(player, hand, item);
        ViewModel.applyItemTransform(matrices, hand, center[0], center[1], center[2]);
    }

    @Inject(
        method = "renderArmWithItem",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderPlayerArm(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;IFFLnet/minecraft/world/entity/HumanoidArm;)V"
        )
    )
    private void versefall$armTransform(
        AbstractClientPlayer player,
        float tickDelta,
        float pitch,
        InteractionHand hand,
        float swingProgress,
        ItemStack item,
        float equipProgress,
        PoseStack matrices,
        SubmitNodeCollector vertexConsumers,
        int light,
        CallbackInfo ci
    ) {
    }

    @Inject(method = "applyEatTransform", at = @At("HEAD"), cancellable = true)
    private void versefall$noEat(PoseStack matrices, float tickDelta, HumanoidArm arm, ItemStack stack, Player player, CallbackInfo ci) {
        if (ViewModel.suppressEatAnimation()) {
            ci.cancel();
        }
    }
}
