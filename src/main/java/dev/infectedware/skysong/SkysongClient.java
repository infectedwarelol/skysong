package dev.infectedware.skysong;

import dev.infectedware.skysong.compat.Compat;
import dev.infectedware.skysong.compat.Hooks;
import dev.infectedware.skysong.compat.Ids;
import dev.infectedware.skysong.compat.Shader;
import dev.infectedware.skysong.gui.HudEditorScreen;
import dev.infectedware.skysong.gui.LyricsScreen;
import dev.infectedware.skysong.gui.click.ClientMenuScreen;
import dev.infectedware.skysong.gui.click.LiveEditorScreen;
import dev.infectedware.skysong.hud.DragSystem;
import dev.infectedware.skysong.hud.InfoHud;
import dev.infectedware.skysong.hud.NameTags;
import dev.infectedware.skysong.hud.SongTags;
import dev.infectedware.skysong.hud.Notifications;
import dev.infectedware.skysong.lyrics.LyricsManager;
import dev.infectedware.skysong.module.Binds;
import dev.infectedware.skysong.module.Category;
import dev.infectedware.skysong.module.ClientModule;
import dev.infectedware.skysong.render.CinematicTextRenderer;
import dev.infectedware.skysong.render.StarGlyphs;
import dev.infectedware.skysong.render.WorldTextRenderer;
import dev.infectedware.skysong.render.fx.Afterimages;
import dev.infectedware.skysong.render.fx.DeathFx;
import dev.infectedware.skysong.render.fx.EntityFx;
import dev.infectedware.skysong.render.fx.Fx;
import dev.infectedware.skysong.render.fx.KillFx;
import dev.infectedware.skysong.render.fx.MotionBlur;
import dev.infectedware.skysong.render.fx.StormFx;
import dev.infectedware.skysong.render.fx.WorldFx;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
public class SkysongClient implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("skysong");
    private static final int UNBOUND = -1;
    private static SpotifyManager spotifyManager;
    private static LyricsManager lyricsManager;
    private static CinematicTextRenderer textRenderer;
    private static WorldTextRenderer worldTextRenderer;
    private static DiscordManager discordManager;
    private int tickCounter = 0;
    private static ClientLevel lastWorld;

    @Override
    public void onInitializeClient() {
        LOGGER.info("Initializing Skysong for Minecraft {}...", FabricLoader.getInstance().getModContainer("minecraft").map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("?"));
        ModConfig.load();
        Profiles.saveBuiltIn("Jules", ModConfig.defaultsSnapshot());
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
                    SongTags.tick(spotifyManager);
                    spotifyManager.tickLocalSource();
                    if (!spotifyManager.getCurrentTrackName().isEmpty()) {
                        lyricsManager.fetchLyrics(spotifyManager.getCurrentTrackName(), spotifyManager.getCurrentArtist(), spotifyManager.getDurationMs());
                        lyricsManager.tickTranslation();
                    }

                    this.tickCounter++;
                    if (this.tickCounter >= 40) {
                        this.tickCounter = 0;
                        spotifyManager.updatePlaybackState();
                        if (spotifyManager.isPlaying() && !spotifyManager.getCurrentTrackName().isEmpty()) {
                            discordManager.updatePresence(spotifyManager.getCurrentTrackName(), spotifyManager.getCurrentArtist(), true);
                        } else {
                            discordManager.updatePresence("", "", false);
                        }
                    }

                    if (spotifyManager.isPlaying()) {
                        long progress = spotifyManager.getSmoothedProgressMs() + ModSettings.lyricOffsetMs;
                        textRenderer.setCurrentLyric(lyricsManager.getLyricAtTime(progress));
                        worldTextRenderer.setCurrentLine(lyricsManager.getLineAtTime(progress), progress);
                    } else if (client.screen instanceof LiveEditorScreen) {
                        long preview = System.currentTimeMillis() % LiveEditorScreen.PREVIEW_LOOP_MS;
                        LyricsManager.LyricLine line = LiveEditorScreen.previewLine(preview);
                        textRenderer.setCurrentLyric(line.text);
                        worldTextRenderer.setCurrentLine(line, preview);
                    } else {
                        textRenderer.setCurrentLyric("");
                        worldTextRenderer.setCurrentLine(null, 0L);
                    }
                }
            );
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
        SongTags.init();
        Hooks.afterEntities(SongTags::render);
        Hooks.last(ctx -> NameTags.get().afterWorld(ctx));
        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            if (client.level != lastWorld) {
                lastWorld = client.level;
                Fx.reset(client.level == null ? "left the world" : "new world");
            }
        });
        ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloader(Ids.of("skysong", "shader_reset"), (ResourceManagerReloadListener)manager -> {
            Shader.reloadAll();
            StarGlyphs.clear();
            Fx.reset("resources reloaded");
        });
        KillFx.init();
        DeathFx.init();
        ClientTickEvents.END_CLIENT_TICK.register(StormFx::tick);
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
                    mc.setScreen(ClientMenuScreen.create());
                }
            });
        };
        KeyMapping editorKeybind = Compat.registerKey(Compat.keyMapping("key.skysong.open_hud_editor", 345));
        KeyMapping playPauseKey = Compat.registerKey(Compat.keyMapping("key.skysong.play_pause", UNBOUND));
        KeyMapping nextKey = Compat.registerKey(Compat.keyMapping("key.skysong.next_track", UNBOUND));
        KeyMapping previousKey = Compat.registerKey(Compat.keyMapping("key.skysong.previous_track", UNBOUND));
        KeyMapping volumeUpKey = Compat.registerKey(Compat.keyMapping("key.skysong.volume_up", UNBOUND));
        KeyMapping volumeDownKey = Compat.registerKey(Compat.keyMapping("key.skysong.volume_down", UNBOUND));
        KeyMapping likeKey = Compat.registerKey(Compat.keyMapping("key.skysong.like_track", UNBOUND));
        KeyMapping lyricsKey = Compat.registerKey(Compat.keyMapping("key.skysong.open_lyrics", UNBOUND));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (editorKeybind.consumeClick()) {
                client.setScreen(new HudEditorScreen());
            }

            while (playPauseKey.consumeClick()) {
                spotifyManager.togglePlayback();
            }

            while (nextKey.consumeClick()) {
                spotifyManager.nextTrack();
            }

            while (previousKey.consumeClick()) {
                spotifyManager.previousTrack();
            }

            while (volumeUpKey.consumeClick()) {
                spotifyManager.changeVolume(1);
            }

            while (volumeDownKey.consumeClick()) {
                spotifyManager.changeVolume(-1);
            }

            while (likeKey.consumeClick()) {
                spotifyManager.toggleLike();
            }

            while (lyricsKey.consumeClick()) {
                if (client.screen == null) {
                    client.setScreen(new LyricsScreen());
                }
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
                Class.forName("dev.infectedware.skysong.dev.SelfTest").getMethod("start").invoke(null);
            } catch (ReflectiveOperationException e) {
                LOGGER.error("Self-test is not available in this build", e);
            }
        }
    }
}
