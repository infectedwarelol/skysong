package dev.infectedware.skysong.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.infectedware.skysong.viewmodel.ViewModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
abstract class HeldItemRendererMixin {
    @Unique
    private static final String RENDER_ITEM = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V";

    @Inject(method = "renderArmWithItem", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V", shift = Shift.AFTER))
    private void skysong$offset(
        AbstractClientPlayer abstractClientPlayer,
        float f,
        float g,
        InteractionHand interactionHand,
        float h,
        ItemStack itemStack,
        float i,
        PoseStack poseStack,
        SubmitNodeCollector submitNodeCollector,
        int j,
        CallbackInfo ci
    ) {
        if (!itemStack.isEmpty()) {
            ViewModel.applyOffset(poseStack, interactionHand);
        }
    }

    @Inject(method = "renderArmWithItem", at = @At(value = "INVOKE", target = RENDER_ITEM))
    private void skysong$itemTransform(
        AbstractClientPlayer abstractClientPlayer,
        float f,
        float g,
        InteractionHand interactionHand,
        float h,
        ItemStack itemStack,
        float i,
        PoseStack poseStack,
        SubmitNodeCollector submitNodeCollector,
        int j,
        CallbackInfo ci
    ) {
        float[] center = ViewModel.itemCenter(abstractClientPlayer, interactionHand, itemStack);
        ViewModel.applyItemTransform(poseStack, interactionHand, center[0], center[1], center[2]);
    }

    @Inject(method = "applyEatTransform", at = @At("HEAD"), cancellable = true)
    private void skysong$noEat(PoseStack poseStack, float f, HumanoidArm humanoidArm, ItemStack itemStack, Player player, CallbackInfo ci) {
        if (ViewModel.suppressEatAnimation()) {
            ci.cancel();
        }
    }
}
