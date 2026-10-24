package dev.infectedware.skysong.hud;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.infectedware.skysong.ModSettings;
import dev.infectedware.skysong.SpotifyManager;
import dev.infectedware.skysong.compat.Compat;
import dev.infectedware.skysong.compat.Gpu;
import dev.infectedware.skysong.compat.WorldCtx;
import dev.infectedware.skysong.net.NowPlayingPayload;
import dev.infectedware.skysong.net.PlayerSongPayload;
import dev.infectedware.skysong.render.LyricLayers;
import dev.infectedware.skysong.theme.ClientAccent;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Font.DisplayMode;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public final class SongTags {
    private static final double MAX_DISTANCE_SQ = 1024.0;
    private static final float TEXT_SCALE = 0.02F;
    private static final double HEIGHT_ABOVE_HEAD = 0.85;
    private static final int TEXT_COLOR = -1;
    private static final int FULL_BRIGHT = 15728880;
    private static final Map<UUID, PlayerSongPayload> SONGS = new ConcurrentHashMap<>();
    private static final LyricLayers.Batch BATCH = new LyricLayers.Batch();
    private static String lastSentTrack = "";
    private static String lastSentArtist = "";

    private SongTags() {
    }

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(PlayerSongPayload.TYPE, (payload, context) -> {
            if (payload.track().isEmpty()) {
                SONGS.remove(payload.player());
            } else {
                SONGS.put(payload.player(), payload);
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            SONGS.clear();
            lastSentTrack = "";
            lastSentArtist = "";
        });
    }

    public static void tick(SpotifyManager spotify) {
        if (spotify == null || Minecraft.getInstance().getConnection() == null || !ClientPlayNetworking.canSend(NowPlayingPayload.TYPE)) {
            return;
        }

        boolean sharing = ModSettings.shareMySong && spotify.isPlaying();
        String track = sharing ? limit(spotify.getCurrentTrackName()) : "";
        String artist = sharing ? limit(spotify.getCurrentArtist()) : "";
        if (!track.equals(lastSentTrack) || !artist.equals(lastSentArtist)) {
            lastSentTrack = track;
            lastSentArtist = artist;
            ClientPlayNetworking.send(new NowPlayingPayload(track, artist));
        }
    }

    public static void render(WorldCtx context) {
        Minecraft mc = Minecraft.getInstance();
        if (!ModSettings.showOthersSongs || SONGS.isEmpty() || mc.level == null || mc.player == null) {
            return;
        }

        Font font = mc.font;
        Vec3 cam = Compat.camPos(context.camera());
        PoseStack pose = context.poseStack();
        LyricLayers.remap(BATCH, LyricLayers.Pass.MAIN, false, true);
        boolean drew = false;

        for (Player player : mc.level.players()) {
            if (player == mc.player || player.isInvisible()) {
                continue;
            }

            PlayerSongPayload song = SONGS.get(player.getUUID());
            if (song == null) {
                continue;
            }

            Vec3 at = player.getPosition(context.partialTick()).add(0.0, player.getBbHeight() + HEIGHT_ABOVE_HEAD, 0.0);
            if (at.distanceToSqr(cam) > MAX_DISTANCE_SQ) {
                continue;
            }

            Component text = Component.literal("♪ ")
                .withColor(ClientAccent.accentOpaque() & 16777215)
                .append(Component.literal(song.artist().isEmpty() ? song.track() : song.track() + " — " + song.artist()).withColor(TEXT_COLOR & 16777215));
            float width = font.width(text);
            pose.pushPose();
            pose.translate(at.x - cam.x, at.y - cam.y, at.z - cam.z);
            pose.mulPose(context.camera().rotation());
            pose.scale(TEXT_SCALE, -TEXT_SCALE, TEXT_SCALE);
            font.drawInBatch(text, -width / 2.0F, 0.0F, TEXT_COLOR, false, pose.last().pose(), BATCH, DisplayMode.NORMAL, 0, FULL_BRIGHT);
            pose.popPose();
            drew = true;
        }

        if (drew) {
            Gpu.bind(mc.getMainRenderTarget());
            BATCH.endBatch();
        }
    }

    private static String limit(String text) {
        if (text == null) {
            return "";
        }

        return text.length() > NowPlayingPayload.MAX_LENGTH ? text.substring(0, NowPlayingPayload.MAX_LENGTH) : text;
    }
}
