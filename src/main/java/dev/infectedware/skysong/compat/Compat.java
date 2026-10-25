package dev.infectedware.skysong.compat;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.InputConstants.Type;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.Camera;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping.Category;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.state.GuiRenderState;
import net.minecraft.client.gui.render.state.ScreenArea;
import net.minecraft.client.gui.render.state.GuiRenderState.TraverseRange;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.fog.FogRenderer.FogMode;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderSetup.TextureBinding;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.FontDescription.Resource;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemCooldowns;
import net.minecraft.world.item.ItemCooldowns.CooldownInstance;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.ReadOnlyScoreInfo;
import net.minecraft.world.scores.Scoreboard;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

public final class Compat {
    private static Supplier<PlayerSkin> ownSkin;
    private static boolean guiPending;
    public static final Matrix4f handProjection = new Matrix4f();
    private static final Category KEY_CATEGORY = Category.register(Identifier.fromNamespaceAndPath("skysong", "main"));

    private Compat() {
    }

    public static float partialTick() {
        return Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true);
    }

    public static BufferSource immediate(int bytes) {
        return MultiBufferSource.immediate(new ByteBufferBuilder(bytes));
    }

    public static Identifier skin(AbstractClientPlayer p) {
        return p.getSkin().body().texturePath();
    }

    public static String name(GameProfile profile) {
        return profile.name();
    }

    public static Identifier ownSkin() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            return skin(mc.player);
        }

        if (ownSkin == null) {
            ownSkin = mc.getSkinManager().createLookup(mc.getGameProfile(), false);
        }

        return ownSkin.get().body().texturePath();
    }

    public static boolean debugScreenShown() {
        return Minecraft.getInstance().getDebugOverlay().showDebugScreen();
    }

    public static Level level(Entity e) {
        return e.level();
    }

    public static float mspt(MinecraftServer server) {
        return server.getCurrentSmoothedTickTime();
    }

    public static int belowNameScore(Player player) {
        Scoreboard board = level(player).getScoreboard();
        Objective below = board.getDisplayObjective(DisplaySlot.BELOW_NAME);
        if (below == null) {
            return 0;
        }

        ReadOnlyScoreInfo score = board.getPlayerScoreInfo(player, below);
        return score == null ? 0 : score.value();
    }

    public static PlayerTeam team(Player p) {
        return p.getTeam();
    }

    public static MobEffect effect(Object e) {
        return e instanceof Holder<?> h ? (MobEffect)h.value() : (MobEffect)e;
    }

    public static void setPixelAbgr(NativeImage image, int x, int y, int abgr) {
        image.setPixel(x, y, abgr & -16711936 | (abgr & 0xFF) << 16 | abgr >> 16 & 0xFF);
    }

    public static void later(Runnable r) {
        Minecraft.getInstance().schedule(r);
    }

    public static List<Entry<Item, CooldownInstance>> cooldowns(ItemCooldowns cooldowns) {
        List<Entry<Item, CooldownInstance>> out = new ArrayList<>();

        for (Entry<Identifier, CooldownInstance> e : cooldowns.cooldowns.entrySet()) {
            out.add(Map.entry(BuiltInRegistries.ITEM.getValue(e.getKey()), e.getValue()));
        }

        return out;
    }

    public static void flush() {
        Gpu.disableDepthTest();
    }

    private static GuiRenderState guiState() {
        return Minecraft.getInstance().gameRenderer.guiRenderState;
    }

    public static void guiChanged() {
        guiPending = true;
    }

    public static boolean drawCollectedGuiUnder(float x0, float y0, float x1, float y1) {
        if (!guiPending) {
            return false;
        }

        GuiRenderState state = guiState();
        boolean[] hit = new boolean[1];
        Consumer<ScreenArea> test = area -> {
            if (!hit[0]) {
                ScreenRectangle b = area.bounds();
                if (b == null || b.left() < x1 && b.right() > x0 && b.top() < y1 && b.bottom() > y0) {
                    hit[0] = true;
                }
            }
        };
        forEachGuiElement(state, test);
        state.forEachItem(test::accept);
        state.forEachText(test::accept);
        state.forEachPictureInPicture(test::accept);
        if (!hit[0]) {
            return false;
        }

        drawCollectedGui();
        return true;
    }

    private static void forEachGuiElement(GuiRenderState state, Consumer<ScreenArea> test) {
        state.forEachElement(test::accept, TraverseRange.ALL);
    }

    public static void drawCollectedGui() {
        if (guiPending) {
            guiPending = false;
            GameRenderer renderer = Minecraft.getInstance().gameRenderer;
            renderer.guiRenderer.render(renderer.fogRenderer.getBuffer(FogMode.NONE));
            guiState().reset();
            Gpu.afterDraw();
        }
    }

    public static DynamicTexture dynamicTexture(String name, NativeImage image) {
        return new DynamicTexture(() -> "Skysong " + name, image);
    }

    public static Identifier layerTexture(RenderType layer) {
        TextureBinding binding = layer.state.textures.get("Sampler0");
        return binding == null ? null : binding.location();
    }

    public static int selectedSlot(Player p) {
        return p.getInventory().getSelectedSlot();
    }

    public static GuiGraphics gui(net.minecraft.client.gui.GuiGraphics g) {
        return new GuiGraphics(g);
    }

    public static net.minecraft.client.gui.GuiGraphics vanilla(GuiGraphics g) {
        return g.vanilla();
    }

    public static long window() {
        return Minecraft.getInstance().getWindow().handle();
    }

    public static boolean shiftDown() {
        return Minecraft.getInstance().hasShiftDown();
    }

    public static boolean controlDown() {
        return Minecraft.getInstance().hasControlDown();
    }

    public static KeyMapping registerKey(KeyMapping key) {
        return KeyBindingHelper.registerKeyBinding(key);
    }

    public static KeyMapping keyMapping(String name, int key) {
        return new KeyMapping(name, Type.KEYSYM, key, KEY_CATEGORY);
    }

    public static Style withFont(Style style, Identifier font) {
        return style.withFont(new Resource(font));
    }

    public static ParticleOptions flash() {
        return ColorParticleOption.create(ParticleTypes.FLASH, -1);
    }

    public static void renderEntity(Entity e, double x, double y, double z, float tickDelta, PoseStack pose, MultiBufferSource consumers) {
        Minecraft mc = Minecraft.getInstance();
        EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();
        EntityRenderState state = dispatcher.extractEntity(e, tickDelta);
        state.shadowRadius = 0.0F;
        state.shadowPieces.clear();
        BufferSource source = consumers instanceof BufferSource b ? b : new Compat.Forwarding(consumers);
        SubmitNodeStorage storage = new SubmitNodeStorage();
        FeatureRenderDispatcher features = featureDispatcher(storage, source);
        dispatcher.submit(state, cameraState(), x, y, z, pose, storage);
        features.renderAllFeatures();
        storage.clear();
    }

    private static FeatureRenderDispatcher featureDispatcher(SubmitNodeStorage storage, BufferSource source) {
        Minecraft mc = Minecraft.getInstance();
        return new FeatureRenderDispatcher(
            storage,
            mc.getBlockRenderer(),
            source,
            mc.getAtlasManager(),
            mc.renderBuffers().outlineBufferSource(),
            mc.renderBuffers().crumblingBufferSource(),
            mc.font
        );
    }

    private static CameraRenderState cameraState() {
        Camera cam = Minecraft.getInstance().gameRenderer.getMainCamera();
        CameraRenderState state = new CameraRenderState();
        state.pos = camPos(cam);
        state.blockPos = camBlockPos(cam);
        state.entityPos = camEntity(cam).position();
        state.orientation = new Quaternionf(cam.rotation());
        state.initialized = true;
        return state;
    }

    public static Vec3 camPos(Camera c) {
        return c.position();
    }

    public static float camYaw(Camera c) {
        return c.yRot();
    }

    public static float camPitch(Camera c) {
        return c.xRot();
    }

    public static Entity camEntity(Camera c) {
        return c.entity();
    }

    public static BlockPos camBlockPos(Camera c) {
        return c.blockPosition();
    }

    public static float timeOfDay(Level level) {
        double d = Mth.frac(level.getDayTime() / 24000.0 - 0.25);
        double e = 0.5 - Math.cos(d * Math.PI) / 2.0;
        return (float)(d * 2.0 + e) / 3.0F;
    }

    private static final class Forwarding extends BufferSource {
        private final MultiBufferSource target;

        Forwarding(MultiBufferSource target) {
            super(new ByteBufferBuilder(256), new LinkedHashMap<>());
            this.target = target;
        }

        @Override
        public VertexConsumer getBuffer(RenderType type) {
            return this.target.getBuffer(type);
        }

        @Override
        public void endLastBatch() {
        }

        @Override
        public void endBatch() {
        }

        @Override
        public void endBatch(RenderType type) {
        }
    }
}
