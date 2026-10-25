package dev.infectedware.skysong.net;

import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;

@NullMarked
public record PlayerSongPayload(UUID player, String track, String artist) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<PlayerSongPayload> TYPE = new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("skysong", "player_song"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PlayerSongPayload> CODEC = StreamCodec.composite(
        UUIDUtil.STREAM_CODEC,
        PlayerSongPayload::player,
        ByteBufCodecs.stringUtf8(NowPlayingPayload.MAX_LENGTH),
        PlayerSongPayload::track,
        ByteBufCodecs.stringUtf8(NowPlayingPayload.MAX_LENGTH),
        PlayerSongPayload::artist,
        PlayerSongPayload::new
    );

    @Override
    public CustomPacketPayload.Type<PlayerSongPayload> type() {
        return TYPE;
    }
}
