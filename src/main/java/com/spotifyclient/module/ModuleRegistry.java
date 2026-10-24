package com.spotifyclient.module;

import com.spotifyclient.ModConfig;
import com.spotifyclient.ModSettings;
import com.spotifyclient.Profiles;
import com.spotifyclient.SpotifyManager;
import com.spotifyclient.SpotifyModClient;
import com.spotifyclient.gui.HudEditorScreen;
import com.spotifyclient.gui.click.CrosshairEditorScreen;
import com.spotifyclient.gui.click.SettingsPopup;
import com.spotifyclient.hud.Crosshair;
import com.spotifyclient.hud.InfoHud;
import com.spotifyclient.render.AspectRatio;
import com.spotifyclient.render.fx.DeathFx;
import com.spotifyclient.render.fx.Fx;
import com.spotifyclient.render.fx.KillFx;
import com.spotifyclient.render.fx.MotionBlur;
import com.spotifyclient.viewmodel.ViewModel;
import com.spotifyclient.viewmodel.ViewModelEditScreen;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import net.minecraft.client.Minecraft;

public final class ModuleRegistry {
    public static final String CLICK_GUI = "Click GUI";
    public static final String INTERFACE = "Interface";
    public static final String[] CATEGORY_NAMES = new String[]{"Visuals", "Display", "Utils"};
    public static final List<ClientModule> ALL = List.of(
        new ClientModule(
            "Lyrics Text",
            "Lines of the playing song float in the world in front of you.",
            Category.VISUALS,
            () -> ModSettings.enable3DLyrics,
            v -> ModSettings.enable3DLyrics = v,
            () -> ModSettings.SHOW_NAMES[ModSettings.lyricShowMode],
            () -> List.of(
                new Setting.Header("Text"),
                new Setting.Choice("Show", ModSettings.SHOW_NAMES, () -> ModSettings.lyricShowMode, v -> ModSettings.lyricShowMode = v),
                new Setting.Choice("Layout", ModSettings.LAYOUT_NAMES, () -> ModSettings.lyricLayout, v -> ModSettings.lyricLayout = v),
                new Setting.Choice("Font", ModSettings.FONT_NAMES, () -> ModSettings.lyricFont, v -> ModSettings.lyricFont = v),
                new Setting.Slide("Line size", 0.05F, 0.5F, 0.01F, 0.17, () -> ModSettings.lyricSize, v -> ModSettings.lyricSize = (float)v),
                new Setting.Slide("Opacity", 0.1F, 1.0F, 0.01F, 1.0, () -> ModSettings.lyricOpacity, v -> ModSettings.lyricOpacity = (float)v),
                new Setting.Toggle("Through walls", () -> ModSettings.lyricThroughWalls, v -> ModSettings.lyricThroughWalls = v),
                new Setting.Toggle("Follow player", () -> ModSettings.lyricFollowPlayer, v -> ModSettings.lyricFollowPlayer = v),
                new Setting.Toggle("Uppercase", () -> ModSettings.lyricUppercase, v -> ModSettings.lyricUppercase = v),
                new Setting.Header("Customization"),
                new Setting.Slide("Blur", 0.0F, 1.0F, 0.01F, 1.0, () -> ModSettings.lyricBlur, v -> ModSettings.lyricBlur = (float)v),
                new Setting.Slide("Glow", 0.0F, 1.0F, 0.01F, 1.0, () -> ModSettings.lyricGlow, v -> ModSettings.lyricGlow = (float)v),
                new Setting.Slide("Outline", 0.0F, 1.0F, 0.01F, 0.7, () -> ModSettings.lyricOutline, v -> ModSettings.lyricOutline = (float)v),
                new Setting.Slide("Bounce", 0.0F, 1.0F, 0.01F, 1.0, () -> ModSettings.lyricBounce, v -> ModSettings.lyricBounce = (float)v),
                new Setting.Toggle("Flicker effect", () -> ModSettings.lyricFlicker, v -> ModSettings.lyricFlicker = v),
                new Setting.Header("Colors"),
                new Setting.Choice("Text color", ModSettings.LYRIC_COLOR_NAMES, () -> ModSettings.lyricColorMode, v -> ModSettings.lyricColorMode = v),
                new Setting.Shown(
                    () -> ModSettings.lyricColorMode == ModSettings.LYRIC_COLOR_CUSTOM || ModSettings.lyricColorMode == ModSettings.LYRIC_COLOR_GRADIENT,
                    new Setting.ColorPick("Color 1", () -> ModSettings.lyricColor, v -> ModSettings.lyricColor = v)
                ),
                new Setting.Shown(
                    () -> ModSettings.lyricColorMode == ModSettings.LYRIC_COLOR_GRADIENT,
                    new Setting.ColorPick("Color 2", () -> ModSettings.lyricColor2, v -> ModSettings.lyricColor2 = v)
                ),
                new Setting.Toggle("Highlight sung word", () -> ModSettings.lyricHighlight, v -> ModSettings.lyricHighlight = v),
                new Setting.Shown(
                    () -> ModSettings.lyricHighlight,
                    new Setting.ColorPick("Highlight color", () -> ModSettings.lyricHighlightColor, v -> ModSettings.lyricHighlightColor = v)
                ),
                new Setting.Choice("Glow color", ModSettings.LYRIC_GLOW_NAMES, () -> ModSettings.lyricGlowMode, v -> ModSettings.lyricGlowMode = v),
                new Setting.Shown(
                    () -> ModSettings.lyricGlowMode == ModSettings.LYRIC_GLOW_CUSTOM,
                    new Setting.ColorPick("Glow", () -> ModSettings.lyricGlowColor, v -> ModSettings.lyricGlowColor = v)
                ),
                new Setting.ColorPick("Outline color", () -> ModSettings.lyricOutlineColor, v -> ModSettings.lyricOutlineColor = v),
                new Setting.Header("Timing"),
                new Setting.Slide(
                    "Sync offset (ms)", -2000.0F, 2000.0F, 50.0F, 0.0, () -> ModSettings.lyricOffsetMs, v -> ModSettings.lyricOffsetMs = (int)Math.round(v)
                ),
                new Setting.Info(() -> "+ earlier, - later"),
                new Setting.Header("Position"),
                new Setting.Shown(
                    () -> ModSettings.lyricLayout != ModSettings.LAYOUT_SKY,
                    new Setting.Slide("Min distance", 2.0F, 20.0F, 1.0F, 4.0, () -> ModSettings.lyricSpawnDistMin, v -> {
                        ModSettings.lyricSpawnDistMin = (int)Math.round(v);
                        ModSettings.lyricSpawnDistMax = Math.max(ModSettings.lyricSpawnDistMax, ModSettings.lyricSpawnDistMin + 1);
                    })
                ),
                new Setting.Shown(
                    () -> ModSettings.lyricLayout != ModSettings.LAYOUT_SKY,
                    new Setting.Slide("Max distance", 3.0F, 30.0F, 1.0F, 9.0, () -> ModSettings.lyricSpawnDistMax, v -> {
                        ModSettings.lyricSpawnDistMax = (int)Math.round(v);
                        ModSettings.lyricSpawnDistMin = Math.min(ModSettings.lyricSpawnDistMin, ModSettings.lyricSpawnDistMax - 1);
                    })
                ),
                new Setting.Shown(
                    () -> ModSettings.lyricLayout == ModSettings.LAYOUT_SKY,
                    new Setting.Slide("Sky distance", 16.0F, 56.0F, 1.0F, 40.0, () -> ModSettings.lyricSkyDistance, v -> ModSettings.lyricSkyDistance = (float)v)
                ),
                new Setting.Shown(
                    () -> ModSettings.lyricLayout == ModSettings.LAYOUT_SKY,
                    new Setting.Slide("Sky height (deg)", 5.0F, 75.0F, 1.0F, 35.0, () -> ModSettings.lyricSkyAngle, v -> ModSettings.lyricSkyAngle = (float)v)
                ),
                new Setting.Shown(
                    () -> ModSettings.lyricLayout == ModSettings.LAYOUT_SKY,
                    new Setting.Slide("Sky text scale", 0.5F, 3.0F, 0.05F, 1.0, () -> ModSettings.lyricSkyScale, v -> ModSettings.lyricSkyScale = (float)v)
                )
            )
        ),
        new ClientModule(
            "HUD Lyrics",
            "The line being sung in a bar at the top, karaoke style, with your coordinates.",
            Category.VISUALS,
            () -> ModSettings.enableHudLyrics,
            v -> ModSettings.enableHudLyrics = v,
            null,
            () -> List.of(
                new Setting.Header("Style"),
                new Setting.Choice("Style", ModSettings.HUD_LYRICS_STYLES, () -> ModSettings.hudLyricsStyle, v -> ModSettings.hudLyricsStyle = v),
                new Setting.Shown(
                    () -> ModSettings.hudLyricsStyle == 0,
                    new Setting.Toggle("Client logo", () -> ModSettings.lyricsBarLogo, v -> ModSettings.lyricsBarLogo = v)
                ),
                new Setting.Shown(
                    () -> ModSettings.hudLyricsStyle == 0,
                    new Setting.Toggle("Coordinates", () -> ModSettings.lyricsBarCoords, v -> ModSettings.lyricsBarCoords = v)
                ),
                new Setting.Toggle("Cinematic fade", () -> ModSettings.cinematicFades, v -> ModSettings.cinematicFades = v),
                new Setting.Toggle("Soft shadow", () -> ModSettings.textDropShadows, v -> ModSettings.textDropShadows = v),
                new Setting.Toggle("Bounce", () -> ModSettings.bouncingLyrics, v -> ModSettings.bouncingLyrics = v)
            )
        ),
        new ClientModule(
            "Name Tags",
            "Draws name tags only above players you can see.",
            Category.VISUALS,
            () -> ModSettings.nameTagsEnabled,
            v -> ModSettings.nameTagsEnabled = v,
            null,
            () -> List.of(
                new Setting.Header("Display"),
                new Setting.Toggle("Health", () -> ModSettings.nameTagsHealth, v -> ModSettings.nameTagsHealth = v),
                new Setting.Toggle("Yourself", () -> ModSettings.nameTagsSelf, v -> ModSettings.nameTagsSelf = v),
                new Setting.Slide("Scale", 0.5F, 2.0F, 0.05F, 1.0, () -> ModSettings.nameTagsScale, v -> ModSettings.nameTagsScale = (float)v)
            )
        ),
        new ClientModule(
            "ViewModel",
            "Move, turn and resize your hands. Edit key (middle mouse) for edit mode, Q to finish.",
            Category.VISUALS,
            () -> ModSettings.viewModelEnabled,
            v -> ModSettings.viewModelEnabled = v,
            null,
            ModuleRegistry::viewModelSettings
        ),
        new ClientModule(
            "Shader Hands",
            "Shader look for your hands and the items you hold.",
            Category.VISUALS,
            () -> ModSettings.shaderHands,
            v -> ModSettings.shaderHands = v,
            () -> ModSettings.SHADER_FILLS[ModSettings.shaderHandsFill],
            ModuleRegistry::shaderHandsSettings
        ),
        new ClientModule(
            "Motion Blur",
            "Smooth blur when you turn and move, tuned for PvP.",
            Category.VISUALS,
            () -> ModSettings.motionBlur,
            v -> ModSettings.motionBlur = v,
            null,
            ModuleRegistry::motionBlurSettings
        ),
        new ClientModule(
            "Aspect Ratio",
            "Stretched look: draw the world as 4:3, 16:10 or your own ratio.",
            Category.VISUALS,
            () -> ModSettings.aspectRatio,
            v -> ModSettings.aspectRatio = v,
            () -> AspectRatio.PRESETS[ModSettings.aspectRatioPreset],
            ModuleRegistry::aspectRatioSettings
        ),
        new ClientModule(
            "Crosshair",
            "Your own crosshair: draw it pixel by pixel, color, size, outline.",
            Category.VISUALS,
            () -> ModSettings.crosshair,
            v -> ModSettings.crosshair = v,
            () -> Crosshair.STYLES[ModSettings.crosshairStyle],
            ModuleRegistry::crosshairSettings
        ),
        new ClientModule(
            "Kill Effects",
            "What you kill goes out in style: six effects, from Shatter to Judgement.",
            Category.VISUALS,
            () -> ModSettings.killEffects,
            v -> ModSettings.killEffects = v,
            () -> KillFx.EFFECTS[ModSettings.killEffectType],
            ModuleRegistry::killEffectSettings
        ),
        new ClientModule(
            "Death Effect",
            "What you see when you die: Shattered Glass, Flatline, Ascension or Signal Lost.",
            Category.VISUALS,
            () -> ModSettings.deathEffect,
            v -> ModSettings.deathEffect = v,
            () -> DeathFx.EFFECTS[ModSettings.deathEffectType],
            () -> List.of(
                new Setting.Header("Effect"),
                new Setting.Choice("Effect", DeathFx.EFFECTS, () -> ModSettings.deathEffectType, v -> ModSettings.deathEffectType = v),
                new Setting.Action("Preview", () -> {
                    closeScreen();
                    DeathFx.preview(ModSettings.deathEffectType);
                }),
                new Setting.Shown(
                    () -> ModSettings.deathEffectType == 0,
                    new Setting.Toggle("Cracks", () -> ModSettings.deathEffectCracks, v -> ModSettings.deathEffectCracks = v)
                ),
                new Setting.Toggle("Sounds", () -> ModSettings.deathEffectSounds, v -> ModSettings.deathEffectSounds = v),
                new Setting.Header("Color"),
                new Setting.Choice("Color", KillFx.COLORS, () -> ModSettings.deathEffectColor, v -> ModSettings.deathEffectColor = v),
                new Setting.Shown(
                    () -> ModSettings.deathEffectColor >= 2,
                    new Setting.ColorPick("Color 1", () -> ModSettings.deathEffectCustomColor, v -> ModSettings.deathEffectCustomColor = v)
                ),
                new Setting.Shown(
                    () -> ModSettings.deathEffectColor == 3,
                    new Setting.ColorPick("Color 2", () -> ModSettings.deathEffectCustomColor2, v -> ModSettings.deathEffectCustomColor2 = v)
                )
            )
        ),
        new ClientModule(
            "Swing Trail",
            "Your hand leaves a glowing white trail when you hit.",
            Category.VISUALS,
            () -> ModSettings.swingTrail,
            v -> ModSettings.swingTrail = v,
            null,
            ModuleRegistry::swingTrailSettings
        ),
        new ClientModule(
            "Afterimages",
            "Glowing white copies of you stay behind while you move.",
            Category.VISUALS,
            () -> ModSettings.afterimages,
            v -> ModSettings.afterimages = v,
            null,
            ModuleRegistry::afterimagesSettings
        ),
        new ClientModule(
            "Shader ESP",
            "Fill, outline and glow on the players and mobs you can see.",
            Category.VISUALS,
            () -> ModSettings.shaderEntities,
            v -> ModSettings.shaderEntities = v,
            () -> ModSettings.SHADER_FILLS[ModSettings.shaderEntitiesFill],
            ModuleRegistry::shaderEspSettings
        ),
        new ClientModule(
            "World Shader",
            "Shaders for the whole world: Storm, Aurora, Black Hole, Starfall.",
            Category.VISUALS,
            () -> ModSettings.worldShader,
            v -> ModSettings.worldShader = v,
            () -> ModSettings.WORLD_SHADERS[ModSettings.worldShaderType],
            ModuleRegistry::worldShaderSettings
        ),
        new ClientModule(
            "Interface",
            "Shared style (glass, glow, colors) for every interface element.",
            Category.DISPLAY,
            null,
            null,
            null,
            ModuleRegistry::interfaceSettings
        ),
        new ClientModule("Click GUI", "Opens the client's click menu.", Category.DISPLAY, null, null, null, null),
        new ClientModule(
            "ArrayList",
            "Movable list of enabled modules.",
            Category.DISPLAY,
            () -> ModSettings.moduleListEnabled,
            v -> ModSettings.moduleListEnabled = v,
            null,
            () -> List.of(
                new Setting.Slide("Wave speed", 1.0F, 100.0F, 1.0F, 18.0, () -> ModSettings.arrayListWaveSpeed, v -> ModSettings.arrayListWaveSpeed = (float)v),
                new Setting.Multi("Categories", CATEGORY_NAMES, () -> ModSettings.arrayListCategories, v -> ModSettings.arrayListCategories = v)
            )
        ),
        new ClientModule(
            "Watermark",
            "Movable client watermark with your name and the time.",
            Category.DISPLAY,
            () -> ModSettings.watermarkEnabled,
            v -> ModSettings.watermarkEnabled = v,
            null,
            () -> List.of(
                new Setting.Toggle("Nickname", () -> ModSettings.watermarkUser, v -> ModSettings.watermarkUser = v),
                new Setting.Toggle("Time", () -> ModSettings.watermarkTime, v -> ModSettings.watermarkTime = v)
            )
        ),
        new ClientModule(
            "TargetHud",
            "Movable HUD with information about your target.",
            Category.DISPLAY,
            () -> ModSettings.targetHudEnabled,
            v -> ModSettings.targetHudEnabled = v,
            () -> ModSettings.TARGET_HUD_MODES[ModSettings.targetHudMode],
            () -> List.of(
                new Setting.Choice("Mode", ModSettings.TARGET_HUD_MODES, () -> ModSettings.targetHudMode, v -> ModSettings.targetHudMode = v),
                new Setting.Choice("Health bar color", ModSettings.TARGET_HUD_BAR, () -> ModSettings.targetHudBarColor, v -> ModSettings.targetHudBarColor = v),
                new Setting.Toggle("Armor", () -> ModSettings.targetHudArmor, v -> ModSettings.targetHudArmor = v),
                new Setting.Toggle("Follow target", () -> ModSettings.targetHudFollow, v -> ModSettings.targetHudFollow = v)
            )
        ),
        new ClientModule(
            "Potions", "Movable list of active effects.", Category.DISPLAY, () -> ModSettings.potionsEnabled, v -> ModSettings.potionsEnabled = v, null, null
        ),
        new ClientModule(
            "KeyStrokes",
            "Movement keys and clicks: a draggable block.",
            Category.DISPLAY,
            () -> ModSettings.keyStrokesEnabled,
            v -> ModSettings.keyStrokesEnabled = v,
            null,
            () -> List.of(
                new Setting.Toggle("Mouse buttons", () -> ModSettings.keyStrokesMouse, v -> ModSettings.keyStrokesMouse = v),
                new Setting.Shown(
                    () -> ModSettings.keyStrokesMouse, new Setting.Toggle("CPS", () -> ModSettings.keyStrokesCps, v -> ModSettings.keyStrokesCps = v)
                ),
                new Setting.Toggle("Space bar", () -> ModSettings.keyStrokesSpace, v -> ModSettings.keyStrokesSpace = v),
                new Setting.Header("Look"),
                new Setting.Toggle("Liquid glass", () -> ModSettings.keyStrokesGlass, v -> ModSettings.keyStrokesGlass = v),
                new Setting.Shown(
                    () -> ModSettings.keyStrokesGlass,
                    new Setting.Choice(
                        "Glass color",
                        new String[]{"GUI theme", "Own color", "Two colors"},
                        () -> ModSettings.keyStrokesGlassColor,
                        v -> ModSettings.keyStrokesGlassColor = v
                    )
                ),
                new Setting.Shown(
                    () -> ModSettings.keyStrokesGlass && ModSettings.keyStrokesGlassColor >= 1,
                    new Setting.ColorPick("Color 1", () -> ModSettings.keyStrokesGlassCustom, v -> ModSettings.keyStrokesGlassCustom = v)
                ),
                new Setting.Shown(
                    () -> ModSettings.keyStrokesGlass && ModSettings.keyStrokesGlassColor == 2,
                    new Setting.ColorPick("Color 2", () -> ModSettings.keyStrokesGlassCustom2, v -> ModSettings.keyStrokesGlassCustom2 = v)
                )
            )
        ),
        new ClientModule(
            "Armor",
            "Worn armor: a draggable block that turns vertical at the screen edges.",
            Category.DISPLAY,
            () -> ModSettings.armorEnabled,
            v -> ModSettings.armorEnabled = v,
            null,
            null
        ),
        new ClientModule(
            "Inventory",
            "Grid of your inventory items with divider lines.",
            Category.DISPLAY,
            () -> ModSettings.inventoryEnabled,
            v -> ModSettings.inventoryEnabled = v,
            null,
            null
        ),
        new ClientModule(
            "Cooldowns",
            "Movable list of item cooldowns.",
            Category.DISPLAY,
            () -> ModSettings.cooldownsEnabled,
            v -> ModSettings.cooldownsEnabled = v,
            null,
            null
        ),
        new ClientModule(
            "Hotkeys",
            "Movable list of the modules you have bound to keys.",
            Category.DISPLAY,
            () -> ModSettings.hotkeysEnabled,
            v -> ModSettings.hotkeysEnabled = v,
            null,
            null
        ),
        new ClientModule(
            "Info",
            "Summary on screen: BPS, coordinates, ping, FPS and TPS.",
            Category.DISPLAY,
            () -> ModSettings.infoEnabled,
            v -> ModSettings.infoEnabled = v,
            () -> InfoHud.STYLES[ModSettings.infoStyle],
            () -> List.of(
                new Setting.Choice("Style", InfoHud.STYLES, () -> ModSettings.infoStyle, v -> ModSettings.infoStyle = v),
                new Setting.Header("Coordinates"),
                new Setting.Toggle("Coordinates", () -> ModSettings.infoCoords, v -> ModSettings.infoCoords = v),
                new Setting.Shown(() -> ModSettings.infoStyle != 2, new Setting.Toggle("Facing", () -> ModSettings.infoFacing, v -> ModSettings.infoFacing = v)),
                new Setting.Toggle("Other dimension", () -> ModSettings.infoOtherDimension, v -> ModSettings.infoOtherDimension = v),
                new Setting.Toggle("Speed", () -> ModSettings.infoSpeed, v -> ModSettings.infoSpeed = v),
                new Setting.Header("Performance"),
                new Setting.Toggle("FPS, TPS and ping", () -> ModSettings.infoPerformance, v -> ModSettings.infoPerformance = v)
            )
        ),
        new ClientModule(
            "Notifications",
            "Toast notifications in the bottom right corner.",
            Category.DISPLAY,
            () -> ModSettings.notificationsEnabled,
            v -> ModSettings.notificationsEnabled = v,
            null,
            null
        ),
        new ClientModule(
            "Custom Hotbar",
            "Replaces the vanilla hotbar with the client's glass design.",
            Category.DISPLAY,
            () -> ModSettings.customHotbarEnabled,
            v -> ModSettings.customHotbarEnabled = v,
            null,
            null
        ),
        new ClientModule(
            "HPFocus",
            "Movable zoomed view of your hotbar, armor, food and health when your health is low.",
            Category.DISPLAY,
            () -> ModSettings.hpFocusEnabled,
            v -> ModSettings.hpFocusEnabled = v,
            null,
            () -> List.of(
                new Setting.Slide("Scale", 0.4F, 3.0F, 0.05F, 1.3, () -> ModSettings.hpFocusScale, v -> ModSettings.hpFocusScale = (float)v),
                new Setting.Slide("Capture width", 120.0F, 320.0F, 2.0F, 200.0, () -> ModSettings.hpFocusWidth, v -> ModSettings.hpFocusWidth = (float)v),
                new Setting.Slide("Capture height", 30.0F, 110.0F, 1.0F, 52.0, () -> ModSettings.hpFocusHeight, v -> ModSettings.hpFocusHeight = (float)v),
                new Setting.Slide("HP threshold", 0.0F, 20.0F, 0.5F, 10.0, () -> ModSettings.hpFocusThreshold, v -> ModSettings.hpFocusThreshold = (float)v)
            )
        ),
        new ClientModule(
            "Now Playing",
            "Song card with cover, time, progress and controls.",
            Category.DISPLAY,
            () -> ModSettings.showNowPlaying,
            v -> ModSettings.showNowPlaying = v,
            null,
            () -> List.of(
                new Setting.Choice("Style", ModSettings.NOW_PLAYING_STYLES, () -> ModSettings.nowPlayingStyle, v -> ModSettings.nowPlayingStyle = v),
                new Setting.Toggle("Album art", () -> ModSettings.showAlbumArt, v -> ModSettings.showAlbumArt = v),
                new Setting.Toggle("Media controls", () -> ModSettings.showMediaControls, v -> ModSettings.showMediaControls = v),
                new Setting.Action("Edit HUD layout", () -> Minecraft.getInstance().setScreen(new HudEditorScreen()))
            )
        ),
        new ClientModule(
            "Dynamic Colors",
            "The Now Playing card takes its color from the album cover.",
            Category.DISPLAY,
            () -> ModSettings.dynamicColors,
            v -> ModSettings.dynamicColors = v,
            null,
            null
        ),
        new ClientModule(
            "Spotify",
            "Reads what Spotify plays on this PC. No login needed.",
            Category.UTILS,
            null,
            null,
            null,
            () -> List.of(
                new Setting.Header("Status"),
                new Setting.Info(() -> spotify().getStatus()),
                new Setting.Info(() -> spotify().isPlaying() ? "Playing: " + spotify().getCurrentTrackName() : "Nothing playing"),
                new Setting.Toggle("Read Spotify on this PC", () -> ModSettings.useLocalMedia, v -> ModSettings.useLocalMedia = v),
                new Setting.Info(() -> "Works with Spotify Free, no login."),
                new Setting.Header("Online login (optional)"),
                new Setting.Info(() -> "Needs Spotify Premium. Not needed"),
                new Setting.Info(() -> "when reading Spotify on this PC."),
                new Setting.Action("Connect Spotify", () -> spotify().authenticate()),
                new Setting.Header("Link"),
                new Setting.Info(() -> "Didn't connect? Paste the link:"),
                new Setting.Input("google.com/?code=…", "Submit link", ModuleRegistry::submitLink),
                new Setting.Header("Your own Spotify app"),
                new Setting.Info(() -> spotify().usingOwnClientId() ? "Using: your app (auto login)" : "Using: built-in app"),
                new Setting.Action("Set up automatically", () -> {
                    closeScreen();
                    spotify().startGuidedSetup();
                }),
                new Setting.Info(() -> "Opens Spotify's dashboard; copy your"),
                new Setting.Info(() -> "new Client ID and it connects itself."),
                new Setting.Info(() -> "Or paste a Client ID by hand:"),
                new Setting.Input("Paste Client ID…", "Save Client ID", id -> spotify().setClientId(id))
            )
        ),
        new ClientModule(
            "Config", "Save profiles, share your settings with a code, or reset everything.", Category.UTILS, null, null, null, ModuleRegistry::configSettings
        )
    );

    private ModuleRegistry() {
    }

    private static List<Setting> viewModelSettings() {
        List<Setting> s = new ArrayList<>();
        s.add(new Setting.Header("Edit mode"));
        s.add(new Setting.KeyPick("Edit key", () -> ModSettings.viewModelEditKey, v -> ModSettings.viewModelEditKey = v));
        s.add(new Setting.Info(() -> Binds.shortName(ModSettings.viewModelEditKey) + " in game: drag, turn and resize with the gizmo. Q to finish."));
        s.add(new Setting.Action("Open edit mode", () -> {
            Minecraft mc = Minecraft.getInstance();
            ModSettings.viewModelEnabled = true;
            mc.setScreen(new ViewModelEditScreen());
        }));
        s.add(new Setting.Toggle("No eat animation", () -> ModSettings.viewModelNoEat, v -> ModSettings.viewModelNoEat = v));
        s.add(new Setting.Header("Main hand"));
        s.add(new Setting.Slide("Main X", -1.5F, 1.5F, 0.01F, 0.0, () -> ModSettings.vmMainX, v -> ModSettings.vmMainX = (float)v));
        s.add(new Setting.Slide("Main Y", -1.5F, 1.5F, 0.01F, 0.0, () -> ModSettings.vmMainY, v -> ModSettings.vmMainY = (float)v));
        s.add(new Setting.Slide("Main Z", -1.5F, 1.5F, 0.01F, 0.0, () -> ModSettings.vmMainZ, v -> ModSettings.vmMainZ = (float)v));
        s.add(new Setting.Slide("Main pitch", -180.0F, 180.0F, 1.0F, 0.0, () -> ModSettings.vmMainPitch, v -> ModSettings.vmMainPitch = (float)v));
        s.add(new Setting.Slide("Main yaw", -180.0F, 180.0F, 1.0F, 0.0, () -> ModSettings.vmMainYaw, v -> ModSettings.vmMainYaw = (float)v));
        s.add(new Setting.Slide("Main roll", -180.0F, 180.0F, 1.0F, 0.0, () -> ModSettings.vmMainRoll, v -> ModSettings.vmMainRoll = (float)v));
        s.add(new Setting.Slide("Main size", 0.2F, 3.0F, 0.01F, 1.0, () -> ModSettings.vmMainScale, v -> ModSettings.vmMainScale = (float)v));
        s.add(new Setting.Header("Off hand"));
        s.add(new Setting.Slide("Off X", -1.5F, 1.5F, 0.01F, 0.0, () -> ModSettings.vmOffX, v -> ModSettings.vmOffX = (float)v));
        s.add(new Setting.Slide("Off Y", -1.5F, 1.5F, 0.01F, 0.0, () -> ModSettings.vmOffY, v -> ModSettings.vmOffY = (float)v));
        s.add(new Setting.Slide("Off Z", -1.5F, 1.5F, 0.01F, 0.0, () -> ModSettings.vmOffZ, v -> ModSettings.vmOffZ = (float)v));
        s.add(new Setting.Slide("Off pitch", -180.0F, 180.0F, 1.0F, 0.0, () -> ModSettings.vmOffPitch, v -> ModSettings.vmOffPitch = (float)v));
        s.add(new Setting.Slide("Off yaw", -180.0F, 180.0F, 1.0F, 0.0, () -> ModSettings.vmOffYaw, v -> ModSettings.vmOffYaw = (float)v));
        s.add(new Setting.Slide("Off roll", -180.0F, 180.0F, 1.0F, 0.0, () -> ModSettings.vmOffRoll, v -> ModSettings.vmOffRoll = (float)v));
        s.add(new Setting.Slide("Off size", 0.2F, 3.0F, 0.01F, 1.0, () -> ModSettings.vmOffScale, v -> ModSettings.vmOffScale = (float)v));
        s.add(new Setting.Action("Reset both hands", () -> ViewModel.reset(null)));
        return s;
    }

    private static void shaderColors(List<Setting> s) {
        s.add(new Setting.Header("Colors"));
        s.add(new Setting.Choice("Colors", ModSettings.SHADER_COLOR_NAMES, () -> ModSettings.shaderColorMode, v -> ModSettings.shaderColorMode = v));
        BooleanSupplier custom = () -> ModSettings.shaderColorMode == 1;
        s.add(new Setting.Shown(custom, new Setting.ColorPick("Color 1", () -> ModSettings.shaderColor1, v -> ModSettings.shaderColor1 = v)));
        s.add(new Setting.Shown(custom, new Setting.ColorPick("Color 2", () -> ModSettings.shaderColor2, v -> ModSettings.shaderColor2 = v)));
    }

    private static void shaderError(List<Setting> s) {
        s.add(new Setting.Shown(() -> Fx.failure() != null, new Setting.Info(() -> "Stopped after an error: " + Fx.failure())));
    }

    private static List<Setting> shaderHandsSettings() {
        List<Setting> s = new ArrayList<>();
        s.add(new Setting.Header("Look"));
        s.add(new Setting.Choice("Fill", ModSettings.SHADER_FILLS, () -> ModSettings.shaderHandsFill, v -> {
            if (v == 4 && ModSettings.shaderHandsFill != v) {
                ModSettings.shaderHandsOpacity = 1.0F;
                ModSettings.shaderHandsOutline = false;
                ModSettings.shaderHandsGlow = true;
                ModSettings.shaderHandsGlowRadius = 10.0F;
                ModSettings.shaderHandsGlowStrength = 1.4F;
            }

            ModSettings.shaderHandsFill = v;
        }));
        s.add(
            new Setting.Shown(
                () -> ModSettings.shaderHandsFill == 4,
                new Setting.Slide("Aura speed", 0.0F, 3.0F, 0.05F, 1.0, () -> ModSettings.shaderAuraSpeed, v -> ModSettings.shaderAuraSpeed = (float)v)
            )
        );
        s.add(
            new Setting.Shown(
                () -> ModSettings.shaderHandsFill == 4,
                new Setting.Slide("Aura size", 0.3F, 3.0F, 0.05F, 1.0, () -> ModSettings.shaderAuraScale, v -> ModSettings.shaderAuraScale = (float)v)
            )
        );
        s.add(new Setting.Slide("Fill strength", 0.0F, 1.0F, 0.05F, 1.0, () -> ModSettings.shaderHandsOpacity, v -> ModSettings.shaderHandsOpacity = (float)v));
        BooleanSupplier glass = () -> ModSettings.shaderHandsFill == 2;
        s.add(
            new Setting.Shown(
                glass,
                new Setting.Slide(
                    "Glass saturation", 0.0F, 3.0F, 0.05F, 1.45, () -> ModSettings.shaderGlassSaturation, v -> ModSettings.shaderGlassSaturation = (float)v
                )
            )
        );
        s.add(
            new Setting.Shown(
                glass,
                new Setting.Slide("Glass whiteness", 0.0F, 1.0F, 0.02F, 0.5, () -> ModSettings.shaderGlassWhite, v -> ModSettings.shaderGlassWhite = (float)v)
            )
        );
        s.add(
            new Setting.Shown(
                glass,
                new Setting.Slide(
                    "Glass distortion", 0.0F, 0.05F, 0.001F, 0.012, () -> ModSettings.shaderGlassDistortion, v -> ModSettings.shaderGlassDistortion = (float)v
                )
            )
        );
        s.add(
            new Setting.Shown(
                glass, new Setting.Slide("Glass tint", 0.0F, 1.0F, 0.02F, 0.35, () -> ModSettings.shaderGlassTint, v -> ModSettings.shaderGlassTint = (float)v)
            )
        );
        s.add(new Setting.Header("Outline"));
        s.add(new Setting.Toggle("Outline", () -> ModSettings.shaderHandsOutline, v -> ModSettings.shaderHandsOutline = v));
        s.add(
            new Setting.Shown(
                () -> ModSettings.shaderHandsOutline,
                new Setting.Slide(
                    "Outline width", 0.5F, 6.0F, 0.5F, 2.0, () -> ModSettings.shaderHandsOutlineWidth, v -> ModSettings.shaderHandsOutlineWidth = (float)v
                )
            )
        );
        s.add(new Setting.Header("Glow"));
        s.add(new Setting.Toggle("Glow", () -> ModSettings.shaderHandsGlow, v -> ModSettings.shaderHandsGlow = v));
        s.add(
            new Setting.Shown(
                () -> ModSettings.shaderHandsGlow,
                new Setting.Slide(
                    "Glow radius", 2.0F, 30.0F, 1.0F, 12.0, () -> ModSettings.shaderHandsGlowRadius, v -> ModSettings.shaderHandsGlowRadius = (float)v
                )
            )
        );
        s.add(
            new Setting.Shown(
                () -> ModSettings.shaderHandsGlow,
                new Setting.Slide(
                    "Glow strength", 0.1F, 4.0F, 0.05F, 1.6, () -> ModSettings.shaderHandsGlowStrength, v -> ModSettings.shaderHandsGlowStrength = (float)v
                )
            )
        );
        shaderColors(s);
        shaderError(s);
        return s;
    }

    private static List<Setting> afterimagesSettings() {
        List<Setting> s = new ArrayList<>();
        s.add(new Setting.Header("Copies"));
        s.add(new Setting.Choice("Players", ModSettings.AFTERIMAGE_TARGETS, () -> ModSettings.afterimagesTargets, v -> ModSettings.afterimagesTargets = v));
        s.add(
            new Setting.Slide(
                "Spawn every (s)", 0.1F, 3.0F, 0.05F, 0.35, () -> ModSettings.afterimagesInterval, v -> ModSettings.afterimagesInterval = (float)v
            )
        );
        s.add(new Setting.Slide("Lasts (s)", 0.2F, 4.0F, 0.1F, 1.0, () -> ModSettings.afterimagesLife, v -> ModSettings.afterimagesLife = (float)v));
        s.add(new Setting.Slide("Opacity", 0.1F, 1.0F, 0.05F, 0.95, () -> ModSettings.afterimagesOpacity, v -> ModSettings.afterimagesOpacity = (float)v));
        s.add(new Setting.Header("Glow"));
        s.add(new Setting.Toggle("Glow", () -> ModSettings.afterimagesGlow, v -> ModSettings.afterimagesGlow = v));
        s.add(
            new Setting.Shown(
                () -> ModSettings.afterimagesGlow,
                new Setting.Slide(
                    "Glow radius", 2.0F, 40.0F, 1.0F, 16.0, () -> ModSettings.afterimagesGlowRadius, v -> ModSettings.afterimagesGlowRadius = (float)v
                )
            )
        );
        s.add(
            new Setting.Shown(
                () -> ModSettings.afterimagesGlow,
                new Setting.Slide(
                    "Glow strength", 0.1F, 4.0F, 0.05F, 1.5, () -> ModSettings.afterimagesGlowStrength, v -> ModSettings.afterimagesGlowStrength = (float)v
                )
            )
        );
        s.add(new Setting.Header("Color"));
        s.add(new Setting.Choice("Color", ModSettings.ECHO_COLORS, () -> ModSettings.afterimagesColor, v -> ModSettings.afterimagesColor = v));
        s.add(
            new Setting.Shown(
                () -> ModSettings.afterimagesColor >= 2,
                new Setting.ColorPick("Color 1", () -> ModSettings.afterimagesCustomColor, v -> ModSettings.afterimagesCustomColor = v)
            )
        );
        s.add(
            new Setting.Shown(
                () -> ModSettings.afterimagesColor == 3,
                new Setting.ColorPick("Color 2", () -> ModSettings.afterimagesCustomColor2, v -> ModSettings.afterimagesCustomColor2 = v)
            )
        );
        shaderError(s);
        return s;
    }

    private static List<Setting> swingTrailSettings() {
        List<Setting> s = new ArrayList<>();
        s.add(new Setting.Header("Trail"));
        s.add(new Setting.Toggle("Only when hitting", () -> ModSettings.swingTrailOnHit, v -> ModSettings.swingTrailOnHit = v));
        s.add(new Setting.Slide("Trail length (s)", 0.05F, 0.6F, 0.01F, 0.2, () -> ModSettings.swingTrailLength, v -> ModSettings.swingTrailLength = (float)v));
        s.add(new Setting.Slide("Trail strength", 0.1F, 1.0F, 0.05F, 1.0, () -> ModSettings.swingTrailStrength, v -> ModSettings.swingTrailStrength = (float)v));
        s.add(new Setting.Slide("Whiteness", 0.0F, 1.0F, 0.05F, 0.85, () -> ModSettings.swingTrailWhiten, v -> ModSettings.swingTrailWhiten = (float)v));
        s.add(new Setting.Header("Glow"));
        s.add(new Setting.Toggle("Glow", () -> ModSettings.swingTrailGlow, v -> ModSettings.swingTrailGlow = v));
        s.add(
            new Setting.Shown(
                () -> ModSettings.swingTrailGlow,
                new Setting.Slide(
                    "Glow radius", 2.0F, 30.0F, 1.0F, 12.0, () -> ModSettings.swingTrailGlowRadius, v -> ModSettings.swingTrailGlowRadius = (float)v
                )
            )
        );
        s.add(
            new Setting.Shown(
                () -> ModSettings.swingTrailGlow,
                new Setting.Slide(
                    "Glow strength", 0.1F, 4.0F, 0.05F, 1.5, () -> ModSettings.swingTrailGlowStrength, v -> ModSettings.swingTrailGlowStrength = (float)v
                )
            )
        );
        s.add(new Setting.Header("Color"));
        s.add(new Setting.Choice("Color", ModSettings.ECHO_COLORS, () -> ModSettings.swingTrailColor, v -> ModSettings.swingTrailColor = v));
        s.add(
            new Setting.Shown(
                () -> ModSettings.swingTrailColor >= 2,
                new Setting.ColorPick("Color 1", () -> ModSettings.swingTrailCustomColor, v -> ModSettings.swingTrailCustomColor = v)
            )
        );
        s.add(
            new Setting.Shown(
                () -> ModSettings.swingTrailColor == 3,
                new Setting.ColorPick("Color 2", () -> ModSettings.swingTrailCustomColor2, v -> ModSettings.swingTrailCustomColor2 = v)
            )
        );
        shaderError(s);
        return s;
    }

    private static List<Setting> motionBlurSettings() {
        List<Setting> s = new ArrayList<>();
        s.add(new Setting.Header("Blur"));
        s.add(new Setting.Slide("Strength", 0.1F, 1.5F, 0.05F, 0.5, () -> ModSettings.motionBlurStrength, v -> ModSettings.motionBlurStrength = (float)v));
        s.add(new Setting.Slide("Max blur (%)", 0.5F, 8.0F, 0.25F, 3.5, () -> ModSettings.motionBlurMax, v -> ModSettings.motionBlurMax = (float)v));
        s.add(new Setting.Choice("Quality", MotionBlur.QUALITY, () -> ModSettings.motionBlurQuality, v -> ModSettings.motionBlurQuality = v));
        s.add(new Setting.Header("PvP"));
        s.add(new Setting.Toggle("Sharp crosshair", () -> ModSettings.motionBlurSharpCenter, v -> ModSettings.motionBlurSharpCenter = v));
        s.add(new Setting.Toggle("Blur when moving", () -> ModSettings.motionBlurMovement, v -> ModSettings.motionBlurMovement = v));
        s.add(new Setting.Info(() -> "Only the world blurs: your hands and the HUD stay sharp."));
        shaderError(s);
        return s;
    }

    private static List<Setting> aspectRatioSettings() {
        List<Setting> s = new ArrayList<>();
        s.add(new Setting.Header("Ratio"));
        s.add(new Setting.Choice("Ratio", AspectRatio.PRESETS, () -> ModSettings.aspectRatioPreset, v -> ModSettings.aspectRatioPreset = v));
        BooleanSupplier custom = () -> ModSettings.aspectRatioPreset == AspectRatio.PRESETS.length - 1;
        s.add(
            new Setting.Shown(
                custom, new Setting.Slide("Width", 1.0F, 32.0F, 0.5F, 4.0, () -> ModSettings.aspectRatioWidth, v -> ModSettings.aspectRatioWidth = (float)v)
            )
        );
        s.add(
            new Setting.Shown(
                custom, new Setting.Slide("Height", 1.0F, 32.0F, 0.5F, 3.0, () -> ModSettings.aspectRatioHeight, v -> ModSettings.aspectRatioHeight = (float)v)
            )
        );
        s.add(new Setting.Toggle("Stretch hand", () -> ModSettings.aspectRatioHand, v -> ModSettings.aspectRatioHand = v));
        s.add(new Setting.Info(() -> String.format(Locale.ROOT, "Now %.2f : 1", AspectRatio.target())));
        return s;
    }

    private static List<Setting> crosshairSettings() {
        List<Setting> s = new ArrayList<>();
        s.add(new Setting.Header("Look"));
        s.add(new Setting.Choice("Appearance", Crosshair.STYLES, () -> ModSettings.crosshairStyle, v -> ModSettings.crosshairStyle = v));
        s.add(new Setting.Action("Open editor", () -> Minecraft.getInstance().setScreen(new CrosshairEditorScreen())));
        s.add(new Setting.Choice("Color", Crosshair.COLORS, () -> ModSettings.crosshairColor, v -> ModSettings.crosshairColor = v));
        s.add(
            new Setting.Shown(
                () -> ModSettings.crosshairColor >= 2,
                new Setting.ColorPick("Color 1", () -> ModSettings.crosshairCustomColor, v -> ModSettings.crosshairCustomColor = v)
            )
        );
        s.add(
            new Setting.Shown(
                () -> ModSettings.crosshairColor == 3,
                new Setting.ColorPick("Color 2", () -> ModSettings.crosshairCustomColor2, v -> ModSettings.crosshairCustomColor2 = v)
            )
        );
        s.add(new Setting.Slide("Scale", 0.5F, 3.0F, 0.25F, 1.0, () -> ModSettings.crosshairScale, v -> ModSettings.crosshairScale = (float)v));
        s.add(new Setting.Toggle("Outline", () -> ModSettings.crosshairOutline, v -> ModSettings.crosshairOutline = v));
        s.add(new Setting.Toggle("React to target", () -> ModSettings.crosshairReact, v -> ModSettings.crosshairReact = v));
        return s;
    }

    private static List<Setting> killEffectSettings() {
        List<Setting> s = new ArrayList<>();
        s.add(new Setting.Header("Effect"));
        s.add(new Setting.Choice("Effect", KillFx.EFFECTS, () -> ModSettings.killEffectType, v -> ModSettings.killEffectType = v));
        s.add(new Setting.Action("Preview", () -> {
            closeScreen();
            KillFx.preview(ModSettings.killEffectType);
        }));
        s.add(new Setting.Toggle("Players only", () -> ModSettings.killEffectPlayersOnly, v -> ModSettings.killEffectPlayersOnly = v));
        s.add(new Setting.Toggle("Sounds", () -> ModSettings.killEffectSounds, v -> ModSettings.killEffectSounds = v));
        s.add(new Setting.Header("Color"));
        s.add(new Setting.Choice("Color", KillFx.COLORS, () -> ModSettings.killEffectColor, v -> ModSettings.killEffectColor = v));
        s.add(
            new Setting.Shown(
                () -> ModSettings.killEffectColor >= 2,
                new Setting.ColorPick("Color 1", () -> ModSettings.killEffectCustomColor, v -> ModSettings.killEffectCustomColor = v)
            )
        );
        s.add(
            new Setting.Shown(
                () -> ModSettings.killEffectColor == 3,
                new Setting.ColorPick("Color 2", () -> ModSettings.killEffectCustomColor2, v -> ModSettings.killEffectCustomColor2 = v)
            )
        );
        shaderError(s);
        return s;
    }

    private static List<Setting> shaderEspSettings() {
        List<Setting> s = new ArrayList<>();
        s.add(new Setting.Header("Targets"));
        s.add(
            new Setting.Multi("Targets", ModSettings.SHADER_TARGET_NAMES, () -> ModSettings.shaderEntitiesTargets, v -> ModSettings.shaderEntitiesTargets = v)
        );
        s.add(new Setting.Header("Look"));
        s.add(new Setting.Choice("Fill", ModSettings.SHADER_FILLS, () -> ModSettings.shaderEntitiesFill, v -> {
            if (v == 4 && ModSettings.shaderEntitiesFill != v) {
                ModSettings.shaderEntitiesOpacity = 1.0F;
                ModSettings.shaderEntitiesOutline = false;
                ModSettings.shaderEntitiesGlow = true;
                ModSettings.shaderEntitiesGlowRadius = 10.0F;
                ModSettings.shaderEntitiesGlowStrength = 1.4F;
            }

            ModSettings.shaderEntitiesFill = v;
        }));
        s.add(
            new Setting.Slide(
                "Fill strength", 0.0F, 1.0F, 0.05F, 0.55, () -> ModSettings.shaderEntitiesOpacity, v -> ModSettings.shaderEntitiesOpacity = (float)v
            )
        );
        s.add(
            new Setting.Shown(
                () -> ModSettings.shaderEntitiesFill == 4,
                new Setting.Slide("Aura speed", 0.0F, 3.0F, 0.05F, 1.0, () -> ModSettings.shaderAuraSpeed, v -> ModSettings.shaderAuraSpeed = (float)v)
            )
        );
        s.add(
            new Setting.Shown(
                () -> ModSettings.shaderEntitiesFill == 4,
                new Setting.Slide("Aura size", 0.3F, 3.0F, 0.05F, 1.0, () -> ModSettings.shaderAuraScale, v -> ModSettings.shaderAuraScale = (float)v)
            )
        );
        s.add(new Setting.Toggle("Outline", () -> ModSettings.shaderEntitiesOutline, v -> ModSettings.shaderEntitiesOutline = v));
        s.add(
            new Setting.Shown(
                () -> ModSettings.shaderEntitiesOutline,
                new Setting.Slide(
                    "Outline width",
                    0.5F,
                    6.0F,
                    0.5F,
                    1.5,
                    () -> ModSettings.shaderEntitiesOutlineWidth,
                    v -> ModSettings.shaderEntitiesOutlineWidth = (float)v
                )
            )
        );
        s.add(new Setting.Toggle("Glow", () -> ModSettings.shaderEntitiesGlow, v -> ModSettings.shaderEntitiesGlow = v));
        s.add(
            new Setting.Shown(
                () -> ModSettings.shaderEntitiesGlow,
                new Setting.Slide(
                    "Glow radius", 2.0F, 30.0F, 1.0F, 10.0, () -> ModSettings.shaderEntitiesGlowRadius, v -> ModSettings.shaderEntitiesGlowRadius = (float)v
                )
            )
        );
        s.add(
            new Setting.Shown(
                () -> ModSettings.shaderEntitiesGlow,
                new Setting.Slide(
                    "Glow strength",
                    0.1F,
                    4.0F,
                    0.05F,
                    1.3,
                    () -> ModSettings.shaderEntitiesGlowStrength,
                    v -> ModSettings.shaderEntitiesGlowStrength = (float)v
                )
            )
        );
        shaderColors(s);
        shaderError(s);
        return s;
    }

    private static List<Setting> worldShaderSettings() {
        List<Setting> s = new ArrayList<>();
        s.add(new Setting.Header("Shader"));
        s.add(new Setting.Choice("Shader", ModSettings.WORLD_SHADERS, () -> ModSettings.worldShaderType, v -> ModSettings.worldShaderType = v));
        s.add(
            new Setting.Slide("Sky brightness", 0.1F, 2.0F, 0.05F, 1.0, () -> ModSettings.shaderSkyBrightness, v -> ModSettings.shaderSkyBrightness = (float)v)
        );
        BooleanSupplier storm = () -> ModSettings.worldShaderType == 0;
        s.add(new Setting.Shown(storm, new Setting.Header("Storm")));
        s.add(
            new Setting.Shown(
                storm,
                new Setting.Slide("Wet reflections", 0.0F, 1.0F, 0.05F, 0.85, () -> ModSettings.stormReflections, v -> ModSettings.stormReflections = (float)v)
            )
        );
        s.add(
            new Setting.Shown(
                storm, new Setting.Slide("Puddles", 0.0F, 1.0F, 0.05F, 0.55, () -> ModSettings.stormPuddles, v -> ModSettings.stormPuddles = (float)v)
            )
        );
        s.add(new Setting.Shown(storm, new Setting.Slide("Haze", 0.0F, 1.0F, 0.05F, 0.45, () -> ModSettings.stormFog, v -> ModSettings.stormFog = (float)v)));
        s.add(new Setting.Shown(storm, new Setting.Slide("Glow", 0.0F, 1.0F, 0.05F, 0.6, () -> ModSettings.stormBloom, v -> ModSettings.stormBloom = (float)v)));
        s.add(new Setting.Shown(storm, new Setting.Toggle("Rain", () -> ModSettings.stormRain, v -> ModSettings.stormRain = v)));
        s.add(new Setting.Header("Blocks"));
        s.add(new Setting.Toggle("Color grade", () -> ModSettings.shaderBlocks, v -> ModSettings.shaderBlocks = v));
        BooleanSupplier blocks = () -> ModSettings.shaderBlocks;
        s.add(
            new Setting.Shown(
                blocks,
                new Setting.Slide(
                    "Saturation", 0.0F, 2.0F, 0.05F, 0.6, () -> ModSettings.shaderBlocksSaturation, v -> ModSettings.shaderBlocksSaturation = (float)v
                )
            )
        );
        s.add(
            new Setting.Shown(
                blocks,
                new Setting.Slide(
                    "Brightness", 0.3F, 1.7F, 0.05F, 0.95, () -> ModSettings.shaderBlocksBrightness, v -> ModSettings.shaderBlocksBrightness = (float)v
                )
            )
        );
        s.add(
            new Setting.Shown(
                blocks,
                new Setting.Slide("Contrast", 0.5F, 1.8F, 0.05F, 1.05, () -> ModSettings.shaderBlocksContrast, v -> ModSettings.shaderBlocksContrast = (float)v)
            )
        );
        s.add(
            new Setting.Shown(
                blocks,
                new Setting.Slide("Color tint", 0.0F, 1.0F, 0.05F, 0.3, () -> ModSettings.shaderBlocksTint, v -> ModSettings.shaderBlocksTint = (float)v)
            )
        );
        shaderColors(s);
        shaderError(s);
        return s;
    }

    private static List<Setting> configSettings() {
        List<Setting> s = new ArrayList<>();
        s.add(new Setting.Shown(() -> !Profiles.status().isEmpty(), new Setting.Info(Profiles::status)));
        s.add(new Setting.Header("Profiles"));
        s.add(new Setting.Input("Profile name…", "Save profile", name -> {
            Profiles.save(name);
            SettingsPopup.rebuildSoon();
        }));
        List<String> names = Profiles.names();
        if (names.isEmpty()) {
            s.add(new Setting.Info(() -> "No profiles yet: type a name and save."));
        }

        for (String n : names) {
            s.add(new Setting.ListRow(n, () -> Profiles.isActive(n), () -> Profiles.load(n), () -> {
                Profiles.delete(n);
                SettingsPopup.rebuildSoon();
            }));
        }

        s.add(new Setting.Header("Share code"));
        s.add(new Setting.Info(() -> "All your settings in one code for a friend."));
        s.add(new Setting.Action("Copy my code", Profiles::copyCode));
        s.add(new Setting.Action("Save my code as a file", Profiles::saveCodeFile));
        s.add(new Setting.Header("Friend's code"));
        s.add(new Setting.Action("Use the code I copied", Profiles::importClipboard));
        s.add(new Setting.Input("Or paste it here…", "Use this code", Profiles::importCode));
        s.add(new Setting.Info(() -> "Got a code file? Drop it on this menu."));
        s.add(new Setting.Shown(Profiles::canUndo, new Setting.Action("Undo: get my settings back", Profiles::undo)));
        s.add(new Setting.Header("Config"));
        s.add(new Setting.Info(() -> "config/spotify_mod.json"));
        s.add(new Setting.Action("Reset to defaults", ModConfig::resetToDefaults));
        return s;
    }

    private static List<Setting> interfaceSettings() {
        List<Setting> s = new ArrayList<>();
        s.add(new Setting.Header("Client rect"));
        s.add(new Setting.Slide("Corner radius", 2.0F, 10.0F, 1.0F, 7.0, () -> ModSettings.rectCornerRadius, v -> ModSettings.rectCornerRadius = (float)v));
        s.add(new Setting.Header("Backdrop"));
        s.add(new Setting.Slide("Backdrop blur", 0.0F, 64.0F, 1.0F, 18.0, () -> ModSettings.rectBackdropBlur, v -> ModSettings.rectBackdropBlur = (float)v));
        s.add(new Setting.Slide("Refraction", 0.0F, 0.8F, 0.01F, 0.3, () -> ModSettings.rectRefraction, v -> ModSettings.rectRefraction = (float)v));
        s.add(new Setting.Header("Colors"));
        s.add(new Setting.Choice("Gradient", ModSettings.GRADIENT_NAMES, () -> ModSettings.gradientStyle, v -> ModSettings.gradientStyle = v));
        s.add(new Setting.Choice("Client color", ModSettings.CLIENT_COLOR_NAMES, () -> ModSettings.clientColorMode, v -> ModSettings.clientColorMode = v));
        BooleanSupplier rainbow = () -> ModSettings.clientColorMode == 2;
        BooleanSupplier custom = () -> ModSettings.clientColorMode == 1;
        s.add(
            new Setting.Shown(
                rainbow, new Setting.Slide("Rainbow speed", 0.1F, 4.0F, 0.1F, 1.0, () -> ModSettings.rainbowSpeed, v -> ModSettings.rainbowSpeed = (float)v)
            )
        );
        s.add(
            new Setting.Shown(
                rainbow,
                new Setting.Slide("Rainbow spread", 0.02F, 0.5F, 0.01F, 0.18, () -> ModSettings.rainbowSpread, v -> ModSettings.rainbowSpread = (float)v)
            )
        );
        s.add(
            new Setting.Shown(
                rainbow,
                new Setting.Slide(
                    "Rainbow saturation", 0.3F, 1.0F, 0.01F, 0.85, () -> ModSettings.rainbowSaturation, v -> ModSettings.rainbowSaturation = (float)v
                )
            )
        );
        s.add(new Setting.Shown(custom, new Setting.ColorPick("Color", () -> ModSettings.rectColor, v -> ModSettings.rectColor = v)));
        s.add(new Setting.Shown(custom, new Setting.Toggle("Second color", () -> ModSettings.rectUseSecondColor, v -> ModSettings.rectUseSecondColor = v)));
        s.add(
            new Setting.Shown(
                () -> custom.getAsBoolean() && ModSettings.rectUseSecondColor,
                new Setting.ColorPick("Color 2", () -> ModSettings.rectSecondColor, v -> ModSettings.rectSecondColor = v)
            )
        );
        s.add(
            new Setting.Shown(
                () -> ModSettings.clientColorMode != 1 || ModSettings.rectUseSecondColor,
                new Setting.Toggle("Color movement", () -> ModSettings.rectColorMovement, v -> ModSettings.rectColorMovement = v)
            )
        );
        s.add(new Setting.Header("Glass edge"));
        s.add(new Setting.Slide("Edge strength", 0.0F, 1.0F, 0.01F, 0.18, () -> ModSettings.rectEdgeStrength, v -> ModSettings.rectEdgeStrength = (float)v));
        s.add(
            new Setting.Shown(
                () -> ModSettings.rectEdgeStrength > 0.0F,
                new Setting.Slide(
                    "Edge sharpness", 2.0F, 100.0F, 1.0F, 55.0, () -> ModSettings.rectEdgeSharpness, v -> ModSettings.rectEdgeSharpness = (float)v
                )
            )
        );
        s.add(new Setting.Header("Glow"));
        s.add(new Setting.Toggle("Glow", () -> ModSettings.rectGlow, v -> ModSettings.rectGlow = v));
        s.add(
            new Setting.Shown(
                () -> ModSettings.rectGlow,
                new Setting.Slide("Glow intensity", 0.0F, 2.0F, 0.05F, 0.6, () -> ModSettings.rectGlowIntensity, v -> ModSettings.rectGlowIntensity = (float)v)
            )
        );
        s.add(
            new Setting.Shown(
                () -> ModSettings.rectGlow,
                new Setting.Slide("Glow radius", 15.0F, 70.0F, 1.0F, 15.0, () -> ModSettings.rectGlowRadius, v -> ModSettings.rectGlowRadius = (float)v)
            )
        );
        s.add(new Setting.Header("Other"));
        s.add(new Setting.Toggle("Icons", () -> ModSettings.hudIcons, v -> ModSettings.hudIcons = v));
        s.add(new Setting.Choice("Dragging", ModSettings.DRAG_STYLE_NAMES, () -> ModSettings.hudDragStyle, v -> ModSettings.hudDragStyle = v));
        s.add(
            new Setting.Shown(
                () -> ModSettings.hudDragStyle == 1, new Setting.Toggle("Jitter while dragging", () -> ModSettings.dragJitter, v -> ModSettings.dragJitter = v)
            )
        );
        s.add(
            new Setting.Shown(
                () -> ModSettings.hudDragStyle == 1, new Setting.Toggle("Waves while dragging", () -> ModSettings.dragWaves, v -> ModSettings.dragWaves = v)
            )
        );
        s.add(
            new Setting.Shown(
                () -> ModSettings.hudDragStyle == 0, new Setting.Toggle("Tilt while dragging", () -> ModSettings.dragTilt, v -> ModSettings.dragTilt = v)
            )
        );
        return s;
    }

    public static List<ClientModule> in(Category category) {
        List<ClientModule> out = new ArrayList<>();

        for (ClientModule m : ALL) {
            if (m.category() == category) {
                out.add(m);
            }
        }

        return out;
    }

    public static int countIn(Category category) {
        int n = 0;

        for (ClientModule m : ALL) {
            if (m.category() == category) {
                n++;
            }
        }

        return n;
    }

    public static ClientModule byName(String name) {
        for (ClientModule m : ALL) {
            if (m.name().equals(name)) {
                return m;
            }
        }

        return null;
    }

    public static boolean listedCategory(Category c) {
        return (ModSettings.arrayListCategories >> c.ordinal() & 1) != 0;
    }

    private static SpotifyManager spotify() {
        return SpotifyModClient.getSpotifyManager();
    }

    private static void closeScreen() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null) {
            mc.screen.onClose();
        }
    }

    private static void submitLink(String link) {
        if (link != null && link.contains("code=")) {
            String query = link.contains("?") ? link.substring(link.indexOf(63) + 1) : link;

            for (String p : query.split("&")) {
                if (p.startsWith("code=")) {
                    String code = p.substring(5).split("#")[0].trim();
                    if (!code.isEmpty()) {
                        spotify().exchangeCodeForTokenPublic(code);
                    }

                    return;
                }
            }
        }
    }
}
