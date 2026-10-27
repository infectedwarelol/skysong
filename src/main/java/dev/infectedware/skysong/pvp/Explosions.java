package dev.infectedware.skysong.pvp;

import dev.infectedware.skysong.ModSettings;
import dev.infectedware.skysong.compat.Ids;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class Explosions {
    public static final String[] SOUNDS = new String[]{
        "Shatter", "Crystal Spikes", "Glass", "Amethyst", "Bell", "Anvil", "Firework", "Pling", "Bass", "Orb"
    };
    private static final long MINE_WINDOW_MS = 1500L;
    private static final Map<Integer, Long> HIT_CRYSTALS = new ConcurrentHashMap<>();
    private static Source current;
    private static boolean currentMine;

    public enum Source {
        CRYSTAL,
        ANCHOR,
        OTHER
    }

    private Explosions() {
    }

    public static void init() {
        AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            if (level.isClientSide() && entity instanceof EndCrystal) {
                HIT_CRYSTALS.put(entity.getId(), System.currentTimeMillis());
            }

            return InteractionResult.PASS;
        });
    }

    public static void begin(Vec3 center) {
        ClientLevel level = Minecraft.getInstance().level;
        current = Source.OTHER;
        currentMine = false;
        if (level == null) {
            return;
        }

        long now = System.currentTimeMillis();
        HIT_CRYSTALS.values().removeIf(at -> now - at > MINE_WINDOW_MS);
        for (EndCrystal crystal : level.getEntitiesOfClass(EndCrystal.class, AABB.ofSize(center, 3.0, 3.0, 3.0))) {
            current = Source.CRYSTAL;
            if (HIT_CRYSTALS.containsKey(crystal.getId())) {
                currentMine = true;
            }
        }

        if (current == Source.OTHER && level.getBlockState(BlockPos.containing(center)).is(Blocks.RESPAWN_ANCHOR)) {
            current = Source.ANCHOR;
        }
    }

    public static void end() {
        current = null;
        currentMine = false;
    }

    public static boolean hidesParticles() {
        if (current == null || !ModSettings.pvpParticleFilter) {
            return false;
        }

        return switch (current) {
            case CRYSTAL -> ModSettings.pvpHideCrystalParticles;
            case ANCHOR -> ModSettings.pvpHideAnchorParticles;
            case OTHER -> ModSettings.pvpHideOtherExplosions;
        };
    }

    public static boolean replacesSound() {
        return current == Source.CRYSTAL && ModSettings.pvpCrystalSound && (currentMine || !ModSettings.pvpCrystalOnlyMine);
    }

    public static boolean mutesVanillaSound() {
        return replacesSound() && ModSettings.pvpCrystalMuteVanilla;
    }

    public static void playCrystalSound(ClientLevel level, double x, double y, double z) {
        if (replacesSound()) {
            play(level, x, y, z);
        }
    }

    public static void preview() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.player != null) {
            play(mc.level, mc.player.getX(), mc.player.getEyeY(), mc.player.getZ());
        }
    }

    private static void play(ClientLevel level, double x, double y, double z) {
        level.playLocalSound(x, y, z, sound(ModSettings.pvpCrystalSoundType), SoundSource.PLAYERS, ModSettings.pvpCrystalVolume, ModSettings.pvpCrystalPitch, false);
    }

    private static SoundEvent sound(int type) {
        return switch (type) {
            case 1 -> SoundEvent.createVariableRangeEvent(Ids.of("skysong", "fx.kill_crystal_spikes"));
            case 2 -> SoundEvents.GLASS_BREAK;
            case 3 -> SoundEvents.AMETHYST_CLUSTER_BREAK;
            case 4 -> SoundEvents.BELL_BLOCK;
            case 5 -> SoundEvents.ANVIL_LAND;
            case 6 -> SoundEvents.FIREWORK_ROCKET_BLAST;
            case 7 -> SoundEvents.NOTE_BLOCK_PLING.value();
            case 8 -> SoundEvents.NOTE_BLOCK_BASS.value();
            case 9 -> SoundEvents.EXPERIENCE_ORB_PICKUP;
            default -> SoundEvent.createVariableRangeEvent(Ids.of("skysong", "fx.kill_shatter"));
        };
    }
}
