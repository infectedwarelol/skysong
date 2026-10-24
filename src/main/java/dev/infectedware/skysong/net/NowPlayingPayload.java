package dev.infectedware.skysong.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record NowPlayingPayload(String track, String artist) implements CustomPacketPayload {
    public static final int MAX_LENGTH = 128;
    public static final CustomPacketPayload.Type<NowPlayingPayload> TYPE = new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("skysong", "now_playing"));
    public static final StreamCodec<RegistryFriendlyByteBuf, NowPlayingPayload> CODEC = StreamCodec.composite(
        ByteBufCodecs.stringUtf8(MAX_LENGTH), NowPlayingPayload::track, ByteBufCodecs.stringUtf8(MAX_LENGTH), NowPlayingPayload::artist, NowPlayingPayload::new
    );

    @Override
    public CustomPacketPayload.Type<NowPlayingPayload> type() {
        return TYPE;
    }
}
