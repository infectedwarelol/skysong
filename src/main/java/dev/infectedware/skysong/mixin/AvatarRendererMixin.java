package dev.infectedware.skysong.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.infectedware.skysong.ModSettings;
import dev.infectedware.skysong.compat.Compat;
import dev.infectedware.skysong.cosmetics.CosmeticTextures;
import dev.infectedware.skysong.cosmetics.Cosmetics;
import dev.infectedware.skysong.cosmetics.CosmeticsLayer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AvatarRenderer.class)
abstract class AvatarRendererMixin {
    @Unique
    private static final ClientAsset.Texture VANILLA_ELYTRA = new ClientAsset.ResourceTexture(Identifier.withDefaultNamespace("entity/equipment/wings/elytra"));

    @Inject(
        method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
        at = @At("TAIL")
    )
    private void skysong$customCape(Avatar avatar, AvatarRenderState state, float partialTick, CallbackInfo ci) {
        if (!ModSettings.cosmeticCape || !Cosmetics.wears(avatar.getId())) {
            return;
        }

        Identifier texture = CosmeticTextures.cape();
        if (texture != null) {
            PlayerSkin skin = state.skin;
            ClientAsset.Texture cape = new ClientAsset.ResourceTexture(texture, texture);
            ClientAsset.Texture elytra = ModSettings.cosmeticCapeElytra ? cape : (skin.elytra() != null ? skin.elytra() : VANILLA_ELYTRA);
            state.skin = new PlayerSkin(skin.body(), cape, elytra, skin.model(), skin.secure());
            state.showCape = true;
        }
    }

    @Inject(method = "renderHand", at = @At("TAIL"))
    private void skysong$handAura(
        PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int light, Identifier skin, ModelPart arm, boolean sleeve, CallbackInfo ci
    ) {
        Minecraft mc = Minecraft.getInstance();
        if (!ModSettings.cosmeticAura || !ModSettings.cosmeticAuraHands || ModSettings.cosmeticAuraStyle == 1 || mc.player == null) {
            return;
        }

        float age = mc.player.tickCount + Compat.partialTick();
        float shift = age * 0.01F * ModSettings.cosmeticAuraSpeed % 1.0F;
        poseStack.pushPose();
        poseStack.scale(1.08F, 1.04F, 1.08F);
        submitNodeCollector.order(1)
            .submitModelPart(
                arm,
                poseStack,
                RenderTypes.energySwirl(CosmeticsLayer.AURA_TEXTURE, shift, shift),
                light,
                OverlayTexture.NO_OVERLAY,
                null,
                CosmeticsLayer.auraTint(age),
                null
            );
        poseStack.popPose();
    }
}
