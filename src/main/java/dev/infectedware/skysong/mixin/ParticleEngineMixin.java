package dev.infectedware.skysong.mixin;

import dev.infectedware.skysong.pvp.ParticleFilter;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ParticleEngine.class)
abstract class ParticleEngineMixin {
    @Inject(method = "createParticle", at = @At("HEAD"), cancellable = true)
    private void skysong$filterParticle(
        ParticleOptions particleOptions, double d, double e, double f, double g, double h, double i, CallbackInfoReturnable<Particle> cir
    ) {
        if (ParticleFilter.hides(particleOptions)) {
            cir.setReturnValue(null);
        }
    }

    @Inject(method = "createTrackingEmitter(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/particles/ParticleOptions;)V", at = @At("HEAD"), cancellable = true)
    private void skysong$filterEmitter(Entity entity, ParticleOptions particleOptions, CallbackInfo ci) {
        if (ParticleFilter.hides(particleOptions)) {
            ci.cancel();
        }
    }

    @Inject(method = "createTrackingEmitter(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/particles/ParticleOptions;I)V", at = @At("HEAD"), cancellable = true)
    private void skysong$filterTimedEmitter(Entity entity, ParticleOptions particleOptions, int i, CallbackInfo ci) {
        if (ParticleFilter.hides(particleOptions)) {
            ci.cancel();
        }
    }
}
