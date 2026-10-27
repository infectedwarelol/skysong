package dev.infectedware.skysong.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.infectedware.skysong.hud.ServerStats;
import dev.infectedware.skysong.pvp.Explosions;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.particles.ExplosionParticleInfo;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.phys.Vec3;
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

    @Inject(
        method = "handleExplosion",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V",
            shift = At.Shift.AFTER
        )
    )
    private void skysong$explosionStart(ClientboundExplodePacket clientboundExplodePacket, CallbackInfo ci) {
        Explosions.begin(clientboundExplodePacket.center());
    }

    @Inject(method = "handleExplosion", at = @At("RETURN"))
    private void skysong$explosionEnd(ClientboundExplodePacket clientboundExplodePacket, CallbackInfo ci) {
        Explosions.end();
    }

    @WrapOperation(
        method = "handleExplosion",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/ClientLevel;playLocalSound(DDDLnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FFZ)V"
        )
    )
    private void skysong$explosionSound(
        ClientLevel level, double x, double y, double z, SoundEvent sound, SoundSource source, float volume, float pitch, boolean delay, Operation<Void> original
    ) {
        if (!Explosions.mutesVanillaSound()) {
            original.call(level, x, y, z, sound, source, volume, pitch, delay);
        }

        Explosions.playCrystalSound(level, x, y, z);
    }

    @WrapOperation(
        method = "handleExplosion",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/ClientLevel;trackExplosionEffects(Lnet/minecraft/world/phys/Vec3;FILnet/minecraft/util/random/WeightedList;)V"
        )
    )
    private void skysong$explosionDebris(
        ClientLevel level, Vec3 center, float radius, int blocks, WeightedList<ExplosionParticleInfo> particles, Operation<Void> original
    ) {
        if (!Explosions.hidesParticles()) {
            original.call(level, center, radius, blocks, particles);
        }
    }
}
