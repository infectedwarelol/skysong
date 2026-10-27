package dev.infectedware.skysong.cosmetics;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.infectedware.skysong.ModSettings;
import dev.infectedware.skysong.compat.Compat;
import dev.infectedware.skysong.theme.ClientAccent;
import dev.infectedware.skysong.theme.Colors;
import java.awt.Color;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

public final class Cosmetics {
    public static final String[] COLOR_MODES = new String[]{"Theme", "Rainbow", "Custom", "Two colors"};
    public static final String[] HALOS = new String[]{"Ring", "Double ring", "Rays", "Tilted"};
    public static final String[] HATS = new String[]{"Top hat", "Crown", "Witch hat", "Party hat", "Cat ears", "Bunny ears", "Horns", "Conical hat"};
    public static final String[] CAPES = new String[]{"Galaxy", "Gradient", "Flames", "Waves", "Stripes", "Checkers", "My image", "Silk ribbons", "Crystal shards"};
    public static final int CAPE_IMAGE = 6;
    public static final int RIBBON_CAPE = 7;
    public static final int SHARD_CAPE = 8;
    public static final String[] WINGS = new String[]{"Angel", "Demon", "Fairy", "Butterfly", "Dragon", "Neon"};
    public static final int NEON_WINGS = 5;
    public static final String[] AURAS = new String[]{"Energy", "Sparkles", "Energy + sparkles"};
    public static final String[] PETS = new String[]{"Wisp", "Creeper", "Dragon", "Wither", "Piglin", "Slime", "Ender eye", "Amethyst", "Jellyfish", "Orrery", "Paper crane"};
    public static final int JELLYFISH_PET = 8;
    public static final int ORRERY_PET = 9;
    public static final int CRANE_PET = 10;
    public static final int MAX_PETS = 3;
    public static final String[] TRAILS = new String[]{"Flames", "Hearts", "Notes", "Sparkles", "Souls", "Cherry petals", "Snow", "Enchant", "Glow", "Color dust"};
    public static final String[] SIDES = new String[]{"Right", "Left"};
    private static final RandomSource RANDOM = RandomSource.create();
    private static Vec3 lastTrailPos;
    private static int tickCount;

    private Cosmetics() {
    }

    public static void init() {
        LivingEntityFeatureRendererRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
            if (renderer instanceof AvatarRenderer<?> avatar) {
                helper.register(new CosmeticsLayer(avatar, context.getModelSet()));
            }
        });
        WorldRenderEvents.BEFORE_ENTITIES.register(context -> firstPersonPet(context.matrices(), context.commandQueue()));
        ClientTickEvents.END_CLIENT_TICK.register(client -> tick(client));
        Emotes.init();
    }

    public static boolean wears(int entityId) {
        LocalPlayer player = Minecraft.getInstance().player;
        return player != null && player.getId() == entityId;
    }

    public static boolean shapedCape() {
        return ModSettings.cosmeticCapeStyle == RIBBON_CAPE || ModSettings.cosmeticCapeStyle == SHARD_CAPE;
    }

    public static boolean shapedPet(int type) {
        return type >= JELLYFISH_PET && type <= CRANE_PET;
    }

    public static boolean petUsesColors(int type) {
        return type == 0 || shapedPet(type);
    }

    public static boolean reduceMotion() {
        return ModSettings.cosmeticReduceMotion;
    }

    public static int color(int mode, int custom, int custom2, float t) {
        return switch (mode) {
            case 0 -> ClientAccent.gradientColor(t, 255.0F);
            case 1 -> rainbow(t);
            case 3 -> 0xFF000000 | Colors.lerp(custom, custom2, t) & 0xFFFFFF;
            default -> 0xFF000000 | custom & 0xFFFFFF;
        };
    }

    private static int rainbow(float offset) {
        float speed = Math.max(0.1F, ModSettings.rainbowSpeed);
        float h = (float)(System.currentTimeMillis() % 600000L) / 6000.0F * speed + offset * 0.5F;
        return 0xFF000000 | Color.HSBtoRGB(h - (float)Math.floor(h), ModSettings.rainbowSaturation, 1.0F) & 0xFFFFFF;
    }

    static int withAlpha(int argb, float alpha) {
        return Math.clamp(Math.round(alpha * 255.0F), 0, 255) << 24 | argb & 0xFFFFFF;
    }

    private static void tick(Minecraft mc) {
        tickCount++;
        Emotes.tick(mc);
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || player.isSpectator() || player.isInvisible()) {
            lastTrailPos = null;
            return;
        }

        Vec3 pos = player.position();
        boolean moving = lastTrailPos != null && pos.distanceToSqr(lastTrailPos) > 4.0E-4;
        lastTrailPos = pos;
        if (reduceMotion()) {
            return;
        }

        if (ModSettings.cosmeticTrail && (moving || !ModSettings.cosmeticTrailMoving)) {
            trail(mc, player);
        }

        if (ModSettings.cosmeticAura && ModSettings.cosmeticAuraStyle != 0) {
            sparkles(mc, player);
        }
    }

    private static void trail(Minecraft mc, LocalPlayer player) {
        float density = Math.clamp(ModSettings.cosmeticTrailDensity, 0.1F, 4.0F);
        int count = (int)density + (RANDOM.nextFloat() < density - (int)density ? 1 : 0);
        for (int i = 0; i < count; i++) {
            double x = player.getX() + (RANDOM.nextDouble() - 0.5) * 0.5;
            double y = player.getY() + 0.1 + RANDOM.nextDouble() * 0.2;
            double z = player.getZ() + (RANDOM.nextDouble() - 0.5) * 0.5;
            float t = RANDOM.nextFloat();
            int type = ModSettings.cosmeticTrailType;
            double vx = type == 2 ? (tickCount % 24) / 24.0 : (RANDOM.nextDouble() - 0.5) * 0.02;
            mc.level.addParticle(trailParticle(type, t), x, y, z, vx, type == 2 ? 0.0 : 0.01, (RANDOM.nextDouble() - 0.5) * 0.02);
        }
    }

    private static ParticleOptions trailParticle(int type, float t) {
        return switch (type) {
            case 1 -> ParticleTypes.HEART;
            case 2 -> ParticleTypes.NOTE;
            case 3 -> ParticleTypes.END_ROD;
            case 4 -> ParticleTypes.SOUL_FIRE_FLAME;
            case 5 -> ParticleTypes.CHERRY_LEAVES;
            case 6 -> ParticleTypes.SNOWFLAKE;
            case 7 -> ParticleTypes.ENCHANT;
            case 8 -> ParticleTypes.GLOW;
            case 9 -> new DustParticleOptions(color(ModSettings.cosmeticTrailColorMode, ModSettings.cosmeticTrailColor, ModSettings.cosmeticTrailColor2, t) & 0xFFFFFF, 1.2F);
            default -> ParticleTypes.FLAME;
        };
    }

    private static void sparkles(Minecraft mc, LocalPlayer player) {
        if (tickCount % 2 != 0) {
            return;
        }

        float angle = RANDOM.nextFloat() * Mth.TWO_PI;
        double r = 0.55 + RANDOM.nextDouble() * 0.2;
        double x = player.getX() + Mth.cos(angle) * r;
        double y = player.getY() + RANDOM.nextDouble() * player.getBbHeight();
        double z = player.getZ() + Mth.sin(angle) * r;
        int c = color(ModSettings.cosmeticAuraColorMode, ModSettings.cosmeticAuraColor, ModSettings.cosmeticAuraColor2, RANDOM.nextFloat());
        mc.level.addParticle(new DustParticleOptions(c & 0xFFFFFF, 0.7F), x, y, z, 0.0, 0.02, 0.0);
    }

    private static void firstPersonPet(PoseStack pose, SubmitNodeCollector collector) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (!ModSettings.cosmeticPet || !ModSettings.cosmeticPetFirstPerson || player == null || mc.level == null || player.isSpectator() || player.isDeadOrDying()) {
            return;
        }

        if (!mc.options.getCameraType().isFirstPerson() || mc.getCameraEntity() != player) {
            return;
        }

        float pt = Compat.partialTick();
        Vec3 at = player.getPosition(pt);
        Vec3 cam = Compat.camPos(mc.gameRenderer.getMainCamera());
        float bodyYaw = Mth.rotLerp(pt, player.yHeadRotO, player.yHeadRot);
        pose.pushPose();
        pose.translate(at.x - cam.x, at.y - cam.y, at.z - cam.z);
        pose.mulPose(Axis.YP.rotationDegrees(180.0F - bodyYaw));
        pose.scale(-0.9375F, -0.9375F, 0.9375F);
        pose.translate(0.0F, -1.501F, 0.0F);
        int light = LevelRenderer.getLightColor(mc.level, BlockPos.containing(player.getEyePosition(pt)));
        CosmeticsLayer.pets(pose, collector, light, player.tickCount + pt, true);
        pose.popPose();
    }
}
