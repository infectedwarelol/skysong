package dev.infectedware.skysong.pvp;

import dev.infectedware.skysong.ModSettings;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.ParticleTypes;

public final class ParticleFilter {
    private ParticleFilter() {
    }

    public static boolean hides(ParticleOptions options) {
        if (!ModSettings.pvpParticleFilter) {
            return false;
        }

        if (Explosions.hidesParticles()) {
            return true;
        }

        ParticleType<?> type = options.getType();
        if (type == ParticleTypes.TOTEM_OF_UNDYING) {
            return ModSettings.pvpHideTotemParticles;
        } else if (type == ParticleTypes.CRIT || type == ParticleTypes.ENCHANTED_HIT) {
            return ModSettings.pvpHideCrits;
        } else if (type == ParticleTypes.SWEEP_ATTACK) {
            return ModSettings.pvpHideSweep;
        } else if (type == ParticleTypes.DAMAGE_INDICATOR) {
            return ModSettings.pvpHideDamageHearts;
        } else if (type == ParticleTypes.ENTITY_EFFECT) {
            return ModSettings.pvpHidePotionSwirls;
        }

        return false;
    }
}
