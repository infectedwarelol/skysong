package dev.infectedware.skysong.mixin;

import dev.infectedware.skysong.hud.ServerStats;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
abstract class ClientPlayNetworkHandlerMixin {
    @Inject(method = "handleSetTime", at = @At("TAIL"))
    private void skysong$onWorldTime(ClientboundSetTimePacket clientboundSetTimePacket, CallbackInfo ci) {
        ServerStats.onTimePacket(clientboundSetTimePacket.gameTime());
    }
}
