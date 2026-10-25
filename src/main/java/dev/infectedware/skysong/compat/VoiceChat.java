package dev.infectedware.skysong.compat;

import dev.infectedware.skysong.SkysongClient;
import java.lang.reflect.Method;
import java.util.UUID;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;

public final class VoiceChat {
    private static final Identifier SPEAKER = icon("speaker");
    private static final Identifier WHISPER = icon("speaker_whisper");
    private static final Identifier MUTED = icon("speaker_off");
    private static final Identifier DISCONNECTED = icon("disconnected");
    private static boolean ready;
    private static boolean broken = !FabricLoader.getInstance().isModLoaded("voicechat");
    private static Method getClient;
    private static Method getTalkCache;
    private static Method isTalking;
    private static Method isWhispering;
    private static Method getStates;
    private static Method isDisabled;
    private static Method isDisconnected;

    private VoiceChat() {
    }

    private static Identifier icon(String name) {
        return Identifier.fromNamespaceAndPath("voicechat", "textures/icons/" + name + ".png");
    }

    private static boolean prepare() {
        if (ready || broken) {
            return ready;
        }

        try {
            Class<?> manager = Class.forName("de.maxhenkel.voicechat.voice.client.ClientManager");
            getClient = manager.getMethod("getClient");
            getStates = manager.getMethod("getPlayerStateManager");
            getTalkCache = getClient.getReturnType().getMethod("getTalkCache");
            isTalking = getTalkCache.getReturnType().getMethod("isTalking", UUID.class);
            isWhispering = getTalkCache.getReturnType().getMethod("isWhispering", UUID.class);
            isDisabled = getStates.getReturnType().getMethod("isPlayerDisabled", UUID.class);
            isDisconnected = getStates.getReturnType().getMethod("isPlayerDisconnected", UUID.class);
            ready = true;
        } catch (ReflectiveOperationException | LinkageError e) {
            broken = true;
            SkysongClient.LOGGER.warn("[Skysong] Simple Voice Chat icons unavailable: {}", e.toString());
        }

        return ready;
    }

    public static Identifier iconFor(UUID player) {
        if (!prepare()) {
            return null;
        }

        try {
            Object client = getClient.invoke(null);
            if (client == null) {
                return null;
            }

            Object talk = getTalkCache.invoke(client);
            if ((Boolean)isWhispering.invoke(talk, player)) {
                return WHISPER;
            }

            if ((Boolean)isTalking.invoke(talk, player)) {
                return SPEAKER;
            }

            Object states = getStates.invoke(null);
            if ((Boolean)isDisconnected.invoke(states, player)) {
                return DISCONNECTED;
            }

            return (Boolean)isDisabled.invoke(states, player) ? MUTED : null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            broken = true;
            ready = false;
            SkysongClient.LOGGER.warn("[Skysong] Simple Voice Chat icons stopped: {}", e.toString());
            return null;
        }
    }
}
