package dev.infectedware.skysong.hud;

import dev.infectedware.skysong.compat.Compat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

public final class ServerStats {
    private static long lastWorldTime = -1L;
    private static long lastPacketNanos;
    private static float tps = 20.0F;

    private ServerStats() {
    }

    public static void onTimePacket(long worldTime) {
        long now = System.nanoTime();
        if (lastWorldTime >= 0L && worldTime > lastWorldTime) {
            double seconds = (now - lastPacketNanos) / 1.0E9;
            if (seconds > 0.05) {
                float sample = (float)Math.min(20.0, (worldTime - lastWorldTime) / seconds);
                tps = tps + (sample - tps) * 0.35F;
            }
        }

        lastWorldTime = worldTime;
        lastPacketNanos = now;
    }

    public static float tps() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.isLocalServer() && mc.getSingleplayerServer() != null) {
            float mspt = Compat.mspt(mc.getSingleplayerServer());
            return mspt <= 50.0F ? 20.0F : Math.min(20.0F, 1000.0F / mspt);
        } else {
            return tps;
        }
    }

    public static int ping(Player player) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) {
            return 0;
        }

        PlayerInfo e = mc.getConnection().getPlayerInfo(player.getUUID());
        return e == null ? 0 : Math.max(0, e.getLatency());
    }

    public static float health(LivingEntity entity) {
        if (entity instanceof Player player && player != Minecraft.getInstance().player) {
            int score = Compat.belowNameScore(player);
            if (score > 0) {
                return score;
            }
        }

        return entity.getHealth() + entity.getAbsorptionAmount();
    }
}
