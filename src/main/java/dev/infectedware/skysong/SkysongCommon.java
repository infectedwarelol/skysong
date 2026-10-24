package dev.infectedware.skysong;

import dev.infectedware.skysong.net.NowPlayingPayload;
import dev.infectedware.skysong.net.PlayerSongPayload;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public class SkysongCommon implements ModInitializer {
    private static final long MIN_UPDATE_GAP_MS = 1000L;
    private static final Map<UUID, PlayerSongPayload> SONGS = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST_UPDATE = new ConcurrentHashMap<>();

    @Override
    public void onInitialize() {
        PayloadTypeRegistry.playC2S().register(NowPlayingPayload.TYPE, NowPlayingPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(PlayerSongPayload.TYPE, PlayerSongPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(NowPlayingPayload.TYPE, (payload, context) -> receive(context.server(), context.player(), payload));
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer joined = handler.getPlayer();
            if (ServerPlayNetworking.canSend(joined, PlayerSongPayload.TYPE)) {
                for (PlayerSongPayload song : SONGS.values()) {
                    ServerPlayNetworking.send(joined, song);
                }
            }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            UUID id = handler.getPlayer().getUUID();
            LAST_UPDATE.remove(id);
            if (SONGS.remove(id) != null) {
                broadcast(server, new PlayerSongPayload(id, "", ""));
            }
        });
    }

    private static void receive(MinecraftServer server, ServerPlayer player, NowPlayingPayload payload) {
        UUID id = player.getUUID();
        long now = System.currentTimeMillis();
        Long last = LAST_UPDATE.get(id);
        if (last != null && now - last < MIN_UPDATE_GAP_MS) {
            return;
        }

        LAST_UPDATE.put(id, now);
        String track = clean(payload.track());
        String artist = clean(payload.artist());
        PlayerSongPayload song = new PlayerSongPayload(id, track, artist);
        if (track.isEmpty()) {
            if (SONGS.remove(id) == null) {
                return;
            }
        } else {
            PlayerSongPayload previous = SONGS.put(id, song);
            if (song.equals(previous)) {
                return;
            }
        }

        broadcast(server, song);
    }

    private static void broadcast(MinecraftServer server, PlayerSongPayload song) {
        for (ServerPlayer other : server.getPlayerList().getPlayers()) {
            if (ServerPlayNetworking.canSend(other, PlayerSongPayload.TYPE)) {
                ServerPlayNetworking.send(other, song);
            }
        }
    }

    static String clean(String text) {
        if (text == null) {
            return "";
        }

        String stripped = text.replaceAll("[\\p{Cntrl}§]", "").trim();
        return stripped.length() > NowPlayingPayload.MAX_LENGTH ? stripped.substring(0, NowPlayingPayload.MAX_LENGTH) : stripped;
    }
}
