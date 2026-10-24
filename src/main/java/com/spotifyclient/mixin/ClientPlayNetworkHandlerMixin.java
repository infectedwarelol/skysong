package com.spotifyclient.mixin;

import com.spotifyclient.hud.ServerStats;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
abstract class ClientPlayNetworkHandlerMixin {
    @Inject(method = "handleSetTime", at = @At("TAIL"))
    private void versefall$onWorldTime(ClientboundSetTimePacket packet, CallbackInfo ci) {
        ServerStats.onTimePacket(packet.gameTime());
    }
}
