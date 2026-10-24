package com.spotifyclient;

import com.spotifyclient.compat.Compat;
import com.spotifyclient.compat.Hooks;
import com.spotifyclient.compat.Ids;
import com.spotifyclient.compat.Shader;
import com.spotifyclient.gui.HudEditorScreen;
import com.spotifyclient.gui.click.ClickGuiScreen;
import com.spotifyclient.hud.DragSystem;
import com.spotifyclient.hud.InfoHud;
import com.spotifyclient.hud.NameTags;
import com.spotifyclient.hud.Notifications;
import com.spotifyclient.lyrics.LyricsManager;
import com.spotifyclient.module.Binds;
import com.spotifyclient.module.Category;
import com.spotifyclient.module.ClientModule;
import com.spotifyclient.render.CinematicTextRenderer;
import com.spotifyclient.render.WorldTextRenderer;
import com.spotifyclient.render.fx.Afterimages;
import com.spotifyclient.render.fx.DeathFx;
import com.spotifyclient.render.fx.EntityFx;
import com.spotifyclient.render.fx.Fx;
import com.spotifyclient.render.fx.KillFx;
import com.spotifyclient.render.fx.MotionBlur;
import com.spotifyclient.render.fx.StormFx;
import com.spotifyclient.render.fx.WorldFx;
import com.spotifyclient.render.r2d.R2D;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
public class SpotifyModClient implements ClientModInitializer {
    public static final String MOD_ID = "spotify_mod";
    public static final Logger LOGGER = LoggerFactory.getLogger("spotify_mod");
    private static SpotifyManager spotifyManager;
    private static LyricsManager lyricsManager;
    private static CinematicTextRenderer textRenderer;
    private static WorldTextRenderer worldTextRenderer;
    private static DiscordManager discordManager;
    private int tickCounter = 0;
    private static ClientLevel lastWorld;

    @Override
    public void onInitializeClient() {
        LOGGER.info(
            "Initializing Versefall for Minecraft "
                + FabricLoader.getInstance().getModContainer("minecraft").map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("?")
                + "..."
        );
        ModConfig.load();
        startSelfTestIfRequested();
        spotifyManager = new SpotifyManager();
        lyricsManager = new LyricsManager();
        textRenderer = new CinematicTextRenderer();
        worldTextRenderer = new WorldTextRenderer();
        discordManager = new DiscordManager();
        discordManager.init();
        ClientSendMessageEvents.ALLOW_CHAT.register(message -> !spotifyManager.handleChatCode(message));
        ClientTickEvents.END_CLIENT_TICK
            .register(
                client -> {
                    spotifyManager.tickClipboardCheck();
                    spotifyManager.tickLocalSource();
                    if (!spotifyManager.getCurrentTrackName().isEmpty()) {
                        lyricsManager.fetchLyrics(spotifyManager.getCurrentTrackName(), spotifyManager.getCurrentArtist(), spotifyManager.getDurationMs());
                    }

                    this.tickCounter++;
                    if (this.tickCounter >= 40) {
                        this.tickCounter = 0;
                        spotifyManager.updatePlaybackState();
                        if (spotifyManager.isPlaying() && !spotifyManager.getCurrentTrackName().isEmpty()) {
                            discordManager.updatePresence(
                                spotifyManager.getCurrentTrackName(),
                                spotifyManager.getCurrentArtist(),
                                spotifyManager.getDurationMs(),
                                spotifyManager.getProgressMs(),
                                true
                            );
                        } else {
                            discordManager.updatePresence("", "", 0L, 0L, false);
                        }
                    }

                    if (spotifyManager.isPlaying()) {
                        long progress = spotifyManager.getSmoothedProgressMs() + ModSettings.lyricOffsetMs;
                        textRenderer.setCurrentLyric(lyricsManager.getLyricAtTime(progress));
                        worldTextRenderer.setCurrentLine(lyricsManager.getLineAtTime(progress), progress);
                    } else {
                        textRenderer.setCurrentLyric("");
                        worldTextRenderer.setCurrentLine(null, 0L);
                    }
                }
            );
        R2D.registerShaders();
        Hooks.hud(NameTags.get());
        DragSystem.get().init();
        Hooks.hud(textRenderer);
        Hooks.hud(new InfoHud());
        Hooks.hud(new Notifications());
        ClientModule.toggleListener = m -> {
            Notifications.onToggle(m);
            if (m.category() == Category.VISUALS) {
                Fx.reset("switched " + m.name());
            }
        };
        Hooks.afterEntities(worldTextRenderer::afterEntities);
        Hooks.last(ctx -> NameTags.get().afterWorld(ctx));
        Fx.registerShaders();
        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            if (client.level != lastWorld) {
                lastWorld = client.level;
                Fx.reset(client.level == null ? "left the world" : "new world");
            }
        });
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override
            public Identifier getFabricId() {
                return Ids.of("spotify_mod", "shader_reset");
            }

            @Override
            public void onResourceManagerReload(ResourceManager manager) {
                Shader.reloadAll();
                Fx.reset("resources reloaded");
            }
        });
        KillFx.init();
        DeathFx.init();
        ClientTickEvents.END_CLIENT_TICK.register(StormFx::tick);
        Hooks.trackWorldCamera();
        Hooks.afterEntities(EntityFx::capture);
        Hooks.last(ctx -> {
            Afterimages.render(ctx);
            KillFx.render(ctx);
            StormFx.apply(ctx);
            WorldFx.apply();
            EntityFx.composite();
            DeathFx.apply();
            MotionBlur.apply(ctx);
        });
        Binds.openClickGui = () -> {
            Minecraft mc = Minecraft.getInstance();
            Compat.later(() -> {
                if (mc.screen == null && mc.player != null) {
                    mc.setScreen(ClickGuiScreen.shared());
                }
            });
        };
        KeyMapping editorKeybind = Compat.registerKey(Compat.keyMapping("key.spotify_mod.open_hud_editor", 345));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (editorKeybind.consumeClick()) {
                client.setScreen(new HudEditorScreen());
            }
        });
    }

    public static SpotifyManager getSpotifyManager() {
        return spotifyManager;
    }

    public static LyricsManager getLyricsManager() {
        return lyricsManager;
    }

    private static void startSelfTestIfRequested() {
        if (Boolean.getBoolean("spotifymod.selftest")) {
            try {
                Class.forName("com.spotifyclient.dev.SelfTest").getMethod("start").invoke(null);
            } catch (ReflectiveOperationException e) {
                LOGGER.error("Self-test is not available in this build", e);
            }
        }
    }
}
