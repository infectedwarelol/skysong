package dev.infectedware.skysong.cosmetics;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.infectedware.skysong.ModSettings;
import dev.infectedware.skysong.compat.Compat;
import dev.infectedware.skysong.theme.Colors;
import java.util.Arrays;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

final class GiantPets {
    private static final int FULL_BRIGHT = 15728880;
    private static final int BODY = 0;
    private static final int EMISSIVE = 1;
    private static final int GLOW = 2;
    private static final float MODEL_SCALE = 0.9375F;
    private static final float CREATURE_HEIGHT = 3.6F;
    private static final float CREATURE_MAX_HEIGHT = 3.6F;
    private static final float MAX_EXTENT = 4.0F;
    private static final float GIANT_EXTENT = 2.0F;
    private static final float NEAR_DISTANCE = 2.0F;
    private static final float LOD_DISTANCE_SQUARED = 576.0F;
    private static final float NEAR_ALPHA = 0.3F;
    private static final long SLOW_FRAME_NANOS = 16_700_000L;
    private static final long THROTTLE_NANOS = 2_000_000_000L;
    private static final int SLOW_FRAMES = 3;
    private static final int PARTICLE_CAP = 24;
    private static final float ACCENT_CHANCE = 0.15F;
    private static final float[] DENSITY = new float[]{0.5F, 1.0F, 1.5F};
    private static final int BODY_POINTS = 24;
    private static final int SERPENT_POINTS = 16;
    private static final int WYRM_POINTS = 24;
    private static final int TAIL_POINTS = 6;
    private static final int ARMS = 6;
    private static final int ARM_POINTS = 8;
    private static final int LEGS = 8;
    private static final int STEP_TICKS = 4;
    private static final int EMBER_WISPS = 4;
    private static final int VENT_WISPS = 6;
    private static final int SHELL_SHARDS = 6;
    private static final int BURST_FEATHERS = 10;
    private static final int BEETLE_STARS = 12;
    private static final int RAIN_DROPS = 6;
    private static final int TENDRILS = 5;
    private static final int TENDRIL_SEGMENTS = 4;
    private static final int IDLE_BURST_TICKS = 200;
    private static final int BURST_LENGTH = 30;
    private static final float[][] ANCHORS = new float[][]{{1.7F, -0.3F, 1.0F}, {-1.7F, -0.3F, 1.0F}, {3.0F, -0.2F, -0.2F}};
    private static final float[] BLOB = sphereQuads(4, 6);
    private static final float[] SMALL_BLOB = sphereQuads(3, 4);
    private static final int[] TUBE_U = new int[]{1, 0, -1, 0};
    private static final int[] TUBE_V = new int[]{0, 1, 0, -1};
    private static final float[] BEETLE_STAR_POSITIONS = beetleStars();
    private static final DustParticleOptions STAG_DUST = new DustParticleOptions(0xB8E6FF, 0.8F);
    private static final RandomSource RANDOM = RandomSource.create();
    private static final int MAX_PETS = Cosmetics.MAX_PETS;
    private static final float[] CHAIN = new float[MAX_PETS * BODY_POINTS * 3];
    private static final float[] CHAIN_PREVIOUS = new float[MAX_PETS * BODY_POINTS * 3];
    private static final float[] CHAIN_LAST = new float[MAX_PETS * BODY_POINTS * 3];
    private static final float[] CHAIN_SHOWN = new float[MAX_PETS * BODY_POINTS * 3];
    private static final float[] ARM = new float[MAX_PETS * ARMS * ARM_POINTS * 3];
    private static final float[] ARM_PREVIOUS = new float[MAX_PETS * ARMS * ARM_POINTS * 3];
    private static final float[] ARM_LAST = new float[MAX_PETS * ARMS * ARM_POINTS * 3];
    private static final float[] ARM_SHOWN = new float[MAX_PETS * ARMS * ARM_POINTS * 3];
    private static final float[] FOOT = new float[MAX_PETS * LEGS * 3];
    private static final float[] FOOT_LAST = new float[MAX_PETS * LEGS * 3];
    private static final float[] FOOT_SHOWN = new float[MAX_PETS * LEGS * 3];
    private static final float[] STEP_FROM = new float[MAX_PETS * LEGS * 3];
    private static final float[] STEP_TO = new float[MAX_PETS * LEGS * 3];
    private static final int[] STEP_TIMER = new int[MAX_PETS * LEGS];
    private static final int[] STATE_TYPE = new int[]{-1, -1, -1};
    private static final float[] SCRATCH = new float[3];
    private static final float[] WORLD = new float[3];
    private static UUID varianceOwner;
    private static float variance = 1.0F;
    private static long lastFrameNanos;
    private static long throttledUntil;
    private static int slowFrames;
    private static int particleBudget;
    private static int idleTicks;
    private static float burstStart = -1000.0F;
    private static double lastIdleX;
    private static double lastIdleY;
    private static double lastIdleZ;

    private GiantPets() {
    }

    static boolean giant(int type) {
        return type >= Cosmetics.SKY_DRAGON_PET && type < Cosmetics.PETS.length;
    }

    private static boolean creature(int type) {
        return type >= Cosmetics.EMBER_BEAST_PET;
    }

    static void frame() {
        long now = System.nanoTime();
        if (lastFrameNanos != 0L) {
            slowFrames = now - lastFrameNanos > SLOW_FRAME_NANOS ? slowFrames + 1 : 0;
            if (slowFrames >= SLOW_FRAMES) {
                throttledUntil = now + THROTTLE_NANOS;
                slowFrames = 0;
            }
        }

        lastFrameNanos = now;
    }

    private static float density() {
        float base = DENSITY[Math.clamp(ModSettings.cosmeticPetEffectDensity, 0, 2)];
        return System.nanoTime() < throttledUntil ? base * 0.5F : base;
    }

    static void clear() {
        Arrays.fill(CHAIN, 0.0F);
        Arrays.fill(CHAIN_PREVIOUS, 0.0F);
        Arrays.fill(CHAIN_LAST, 0.0F);
        Arrays.fill(CHAIN_SHOWN, 0.0F);
        Arrays.fill(ARM, 0.0F);
        Arrays.fill(ARM_PREVIOUS, 0.0F);
        Arrays.fill(ARM_LAST, 0.0F);
        Arrays.fill(ARM_SHOWN, 0.0F);
        Arrays.fill(FOOT, 0.0F);
        Arrays.fill(FOOT_LAST, 0.0F);
        Arrays.fill(FOOT_SHOWN, 0.0F);
        Arrays.fill(STEP_FROM, 0.0F);
        Arrays.fill(STEP_TO, 0.0F);
        Arrays.fill(STEP_TIMER, 0);
        Arrays.fill(STATE_TYPE, -1);
        varianceOwner = null;
        variance = 1.0F;
        idleTicks = 0;
        burstStart = -1000.0F;
    }

    private static float variance(UUID owner) {
        if (owner == null) {
            return 1.0F;
        }

        if (!owner.equals(varianceOwner)) {
            long bits = owner.getMostSignificantBits() ^ owner.getLeastSignificantBits();
            bits ^= bits >>> 29;
            bits *= 0xBF58476D1CE4E5B9L;
            bits ^= bits >>> 32;
            varianceOwner = owner;
            variance = (bits & 3L) == 0L ? 1.25F + (bits >>> 8 & 0xFFL) / 255.0F * 0.75F : 1.0F;
        }

        return variance;
    }

    private static float scale(int type, UUID owner) {
        float maxSize = Cosmetics.clamped(ModSettings.cosmeticPetMaxSize, 0.5F, 2.0F, 1.0F);
        if (creature(type)) {
            float height = Math.min(Math.min(CREATURE_HEIGHT * maxSize, CREATURE_MAX_HEIGHT), MAX_EXTENT);
            return height / CREATURE_HEIGHT;
        }

        return Math.min(variance(owner) * maxSize, MAX_EXTENT / GIANT_EXTENT);
    }

    private static float anchorX(int slot) {
        return (ModSettings.cosmeticPetSide == 1 ? 1.0F : -1.0F) * ANCHORS[slot][0];
    }

    static void render(PoseStack pose, SubmitNodeCollector collector, int light, float age, int type, int slot, float lagX, float lagY, float lagZ) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            return;
        }

        float partialTick = Compat.partialTick();
        float scale = scale(type, player.getUUID());
        float k = scale / MODEL_SCALE;
        float ax = anchorX(slot) + lagX;
        float ay = ANCHORS[slot][1] + lagY;
        float az = ANCHORS[slot][2] + lagZ;
        float yaw = Mth.rotLerp(partialTick, player.yBodyRotO, player.yBodyRot);
        double px = Mth.lerp(partialTick, player.xo, player.getX());
        double py = Mth.lerp(partialTick, player.yo, player.getY());
        double pz = Mth.lerp(partialTick, player.zo, player.getZ());
        toWorld(ax, ay, az, k, yaw, 0.0F, creature(type) ? CREATURE_HEIGHT * 0.5F : 0.9F, 0.0F, WORLD);
        Vec3 camera = Compat.camPos(mc.gameRenderer.getMainCamera());
        double dx = px + WORLD[0] - camera.x;
        double dy = py + WORLD[1] - camera.y;
        double dz = pz + WORLD[2] - camera.z;
        double distanceSquared = dx * dx + dy * dy + dz * dz;
        float nearRadius = NEAR_DISTANCE + (creature(type) ? CREATURE_HEIGHT * 0.5F : GIANT_EXTENT * 0.5F) * scale;
        boolean near = distanceSquared < nearRadius * nearRadius;
        boolean far = distanceSquared > LOD_DISTANCE_SQUARED;
        float alpha = near ? NEAR_ALPHA : 1.0F;
        int lod = far ? 2 : 1;
        int fx = Cosmetics.fxQuality();
        boolean glow = !near && !far && fx > 0;
        float effects = density();
        showState(slot, partialTick);
        pose.pushPose();
        pose.translate(ax, ay, az);
        pose.mulPose(Axis.ZP.rotationDegrees(180.0F));
        pose.scale(k, k, k);
        collector.submitCustomGeometry(pose, RenderTypes.entityTranslucent(CosmeticTextures.white()), (p, out) -> draw(new Geo(p, out, light), type, slot, BODY, age, alpha, lod, effects, fx));
        if (!near) {
            collector.submitCustomGeometry(
                pose, RenderTypes.entityTranslucentEmissive(CosmeticTextures.white()), (p, out) -> draw(new Geo(p, out, FULL_BRIGHT), type, slot, EMISSIVE, age, alpha, lod, effects, fx)
            );
        }

        if (glow) {
            RenderType additive = CosmeticsLayer.glowType();
            collector.submitCustomGeometry(pose, additive, (p, out) -> draw(new Geo(p, out, FULL_BRIGHT), type, slot, GLOW, age, alpha, lod, effects, fx));
        }

        pose.popPose();
    }

    private static void toWorld(float ax, float ay, float az, float k, float yaw, float lx, float ly, float lz, float[] out) {
        float mx = ax - lx * k;
        float my = ay - ly * k;
        float mz = az + lz * k;
        float qx = mx * MODEL_SCALE;
        float qy = (my - 1.501F) * MODEL_SCALE;
        float qz = mz * MODEL_SCALE;
        float radians = yaw * Mth.DEG_TO_RAD;
        float cos = Mth.cos(radians);
        float sin = Mth.sin(radians);
        out[0] = qx * cos + qz * sin;
        out[1] = -qy;
        out[2] = qx * sin - qz * cos;
    }

    static void tick(Minecraft mc, LocalPlayer player, boolean jump) {
        particleBudget = PARTICLE_CAP;
        trackIdle(player);
        boolean particles = !Cosmetics.reduceMotion();
        float age = player.tickCount;
        for (int slot = 0; slot < MAX_PETS; slot++) {
            int type = CosmeticPhysics.slotType(slot);
            if (!ModSettings.cosmeticPet || !giant(type) || player.isDeadOrDying()) {
                STATE_TYPE[slot] = -1;
                continue;
            }

            float k = scale(type, player.getUUID()) / MODEL_SCALE;
            boolean fresh = jump || STATE_TYPE[slot] != type;
            STATE_TYPE[slot] = type;
            step(slot, type, age, k, fresh);
            if (particles && mc.level != null) {
                emit(mc, player, slot, type, age, k);
            }
        }
    }

    private static void trackIdle(LocalPlayer player) {
        double dx = player.getX() - lastIdleX;
        double dy = player.getY() - lastIdleY;
        double dz = player.getZ() - lastIdleZ;
        lastIdleX = player.getX();
        lastIdleY = player.getY();
        lastIdleZ = player.getZ();
        idleTicks = dx * dx + dy * dy + dz * dz < 1.0E-4 ? idleTicks + 1 : 0;
        if (idleTicks > 0 && idleTicks % IDLE_BURST_TICKS == 0) {
            burstStart = player.tickCount;
        }
    }

    private static void step(int slot, int type, float age, float k, boolean fresh) {
        float turn = CosmeticPhysics.frameTurn;
        float cos = Mth.cos(turn);
        float sin = -Mth.sin(turn);
        float shiftX = -CosmeticPhysics.frameX / k;
        float shiftY = -CosmeticPhysics.frameY / k;
        float shiftZ = CosmeticPhysics.frameZ / k;
        boolean flip = ((int)age & 4) == 0;
        float pivotX = anchorX(slot) / k;
        float pivotZ = -ANCHORS[slot][2] / k;
        int chainPoints = chainPoints(type);
        int first = slot * BODY_POINTS;
        if (chainPoints > 0) {
            System.arraycopy(CHAIN, first * 3, CHAIN_LAST, first * 3, chainPoints * 3);
            chainHead(type, age, SCRATCH);
            float rest = chainRest(type);
            if (fresh) {
                for (int i = 0; i < chainPoints; i++) {
                    set(CHAIN, first + i, SCRATCH[0], SCRATCH[1], SCRATCH[2] + i * rest);
                    set(CHAIN_PREVIOUS, first + i, SCRATCH[0], SCRATCH[1], SCRATCH[2] + i * rest);
                    set(CHAIN_LAST, first + i, SCRATCH[0], SCRATCH[1], SCRATCH[2] + i * rest);
                }
            } else {
                Motion.shift(CHAIN, CHAIN_PREVIOUS, first, first + chainPoints, pivotX, pivotZ, cos, sin, shiftX, shiftY, shiftZ);
                float gravity = type == Cosmetics.PHOENIX_PET ? -0.01F : 0.0F;
                Motion.chain(CHAIN, CHAIN_PREVIOUS, first, chainPoints, SCRATCH[0], SCRATCH[1], SCRATCH[2], rest, gravity, 0.15F, 4);
            }
        }

        if (type == Cosmetics.KRAKEN_PET) {
            int arm = slot * ARMS * ARM_POINTS;
            System.arraycopy(ARM, arm * 3, ARM_LAST, arm * 3, ARMS * ARM_POINTS * 3);
            if (!fresh) {
                Motion.shift(ARM, ARM_PREVIOUS, arm, arm + ARMS * ARM_POINTS, pivotX, pivotZ, cos, sin, shiftX, shiftY, shiftZ);
            }

            for (int i = 0; i < ARMS; i++) {
                armRoot(i, age, SCRATCH);
                int start = arm + i * ARM_POINTS;
                if (fresh) {
                    for (int j = 0; j < ARM_POINTS; j++) {
                        set(ARM, start + j, SCRATCH[0], SCRATCH[1] - j * 0.16F, SCRATCH[2]);
                        set(ARM_PREVIOUS, start + j, SCRATCH[0], SCRATCH[1] - j * 0.16F, SCRATCH[2]);
                        set(ARM_LAST, start + j, SCRATCH[0], SCRATCH[1] - j * 0.16F, SCRATCH[2]);
                    }
                } else {
                    Motion.chain(ARM, ARM_PREVIOUS, start, ARM_POINTS, SCRATCH[0], SCRATCH[1], SCRATCH[2], 0.16F, -0.012F, 0.1F, 4);
                }
            }
        }

        if (type == Cosmetics.SPIDER_PET) {
            stepFeet(slot, fresh, flip, cos, sin, pivotX, pivotZ, shiftX, shiftY, shiftZ);
        }
    }

    private static void set(float[] points, int point, float x, float y, float z) {
        int i = point * 3;
        points[i] = x;
        points[i + 1] = y;
        points[i + 2] = z;
    }

    private static int chainPoints(int type) {
        return switch (type) {
            case Cosmetics.SERPENT_PET -> SERPENT_POINTS;
            case Cosmetics.WYRM_PET -> WYRM_POINTS;
            case Cosmetics.PHOENIX_PET -> TAIL_POINTS;
            default -> 0;
        };
    }

    private static float chainRest(int type) {
        return switch (type) {
            case Cosmetics.SERPENT_PET -> 0.13F;
            case Cosmetics.WYRM_PET -> 0.1F;
            default -> 0.16F;
        };
    }

    private static void chainHead(int type, float age, float[] out) {
        if (type == Cosmetics.SERPENT_PET) {
            float t = age * 0.03F;
            out[0] = Mth.sin(t) * 0.85F;
            out[1] = 0.9F + Mth.sin(age * 0.05F) * 0.3F;
            out[2] = Mth.cos(t) * 0.68F;
        } else if (type == Cosmetics.WYRM_PET) {
            float t = age * 0.025F;
            out[0] = Mth.sin(t) * 0.9F;
            out[1] = 1.0F + Mth.sin(t * 2.0F) * 0.25F;
            out[2] = Mth.sin(t * 2.0F) * 0.5F;
        } else {
            out[0] = 0.0F;
            out[1] = 0.9F;
            out[2] = 0.3F;
        }
    }

    private static void armRoot(int arm, float age, float[] out) {
        float angle = Mth.TWO_PI * arm / ARMS + Mth.sin(age * 0.03F) * 0.2F;
        out[0] = Mth.cos(angle) * 0.28F;
        out[1] = 0.62F;
        out[2] = Mth.sin(angle) * 0.28F;
    }

    private static void footRest(int leg, float[] out) {
        int side = leg < 4 ? -1 : 1;
        int index = leg & 3;
        out[0] = side * (index == 1 || index == 2 ? 1.1F : 1.0F);
        out[1] = 0.0F;
        out[2] = -0.9F + index * 0.55F;
    }

    private static void footHip(int leg, float[] out) {
        int side = leg < 4 ? -1 : 1;
        out[0] = side * 0.28F;
        out[1] = 0.85F;
        out[2] = -0.45F + (leg & 3) * 0.18F;
    }

    private static void stepFeet(int slot, boolean fresh, boolean flip, float cos, float sin, float pivotX, float pivotZ, float shiftX, float shiftY, float shiftZ) {
        int first = slot * LEGS;
        System.arraycopy(FOOT, first * 3, FOOT_LAST, first * 3, LEGS * 3);
        if (fresh) {
            for (int leg = 0; leg < LEGS; leg++) {
                footRest(leg, SCRATCH);
                set(FOOT, first + leg, SCRATCH[0], SCRATCH[1], SCRATCH[2]);
                set(FOOT_LAST, first + leg, SCRATCH[0], SCRATCH[1], SCRATCH[2]);
                STEP_TIMER[first + leg] = 0;
            }

            return;
        }

        boolean stepping = false;
        for (int leg = 0; leg < LEGS; leg++) {
            stepping |= STEP_TIMER[first + leg] > 0;
        }

        for (int leg = 0; leg < LEGS; leg++) {
            int point = first + leg;
            int i = point * 3;
            if (STEP_TIMER[point] > 0) {
                STEP_TIMER[point]--;
                float t = 1.0F - (float)STEP_TIMER[point] / STEP_TICKS;
                FOOT[i] = Mth.lerp(t, STEP_FROM[i], STEP_TO[i]);
                FOOT[i + 1] = Mth.lerp(t, STEP_FROM[i + 1], STEP_TO[i + 1]) + Mth.sin(t * (float)Math.PI) * 0.25F;
                FOOT[i + 2] = Mth.lerp(t, STEP_FROM[i + 2], STEP_TO[i + 2]);
                continue;
            }

            float x = FOOT[i] - pivotX;
            float z = FOOT[i + 2] - pivotZ;
            FOOT[i] = pivotX + x * cos - z * sin - shiftX;
            FOOT[i + 1] -= shiftY;
            FOOT[i + 2] = pivotZ + x * sin + z * cos - shiftZ;
            footRest(leg, SCRATCH);
            float dx = SCRATCH[0] - FOOT[i];
            float dy = SCRATCH[1] - FOOT[i + 1];
            float dz = SCRATCH[2] - FOOT[i + 2];
            boolean group = ((leg & 1) ^ (leg >> 2)) == 0;
            if (!stepping && group == flip && dx * dx + dy * dy + dz * dz > 0.2F) {
                STEP_FROM[i] = FOOT[i];
                STEP_FROM[i + 1] = FOOT[i + 1];
                STEP_FROM[i + 2] = FOOT[i + 2];
                STEP_TO[i] = SCRATCH[0] + dx * 0.4F;
                STEP_TO[i + 1] = SCRATCH[1];
                STEP_TO[i + 2] = SCRATCH[2] + dz * 0.4F;
                STEP_TIMER[point] = STEP_TICKS;
            }
        }
    }

    private static void showState(int slot, float partialTick) {
        float t = Math.clamp(partialTick, 0.0F, 1.0F);
        int chainFrom = slot * BODY_POINTS * 3;
        for (int i = chainFrom; i < chainFrom + BODY_POINTS * 3; i++) {
            CHAIN_SHOWN[i] = Mth.lerp(t, CHAIN_LAST[i], CHAIN[i]);
        }

        int armFrom = slot * ARMS * ARM_POINTS * 3;
        for (int i = armFrom; i < armFrom + ARMS * ARM_POINTS * 3; i++) {
            ARM_SHOWN[i] = Mth.lerp(t, ARM_LAST[i], ARM[i]);
        }

        int footFrom = slot * LEGS * 3;
        for (int i = footFrom; i < footFrom + LEGS * 3; i++) {
            FOOT_SHOWN[i] = Mth.lerp(t, FOOT_LAST[i], FOOT[i]);
        }
    }

    private static boolean spend() {
        if (particleBudget <= 0) {
            return false;
        }

        particleBudget--;
        return true;
    }

    private static void emit(Minecraft mc, LocalPlayer player, int slot, int type, float age, float k) {
        float effects = density();
        int count = (int)effects + (RANDOM.nextFloat() < effects - (int)effects ? 1 : 0);
        boolean accent = Cosmetics.fxQuality() > 0 && RANDOM.nextFloat() < ACCENT_CHANCE;
        float ax = anchorX(slot);
        float ay = ANCHORS[slot][1];
        float az = ANCHORS[slot][2];
        float yaw = player.yBodyRot;
        for (int i = 0; i < count && spend(); i++) {
            emitterPoint(type, slot, age, i, SCRATCH);
            toWorld(ax, ay, az, k, yaw, SCRATCH[0], SCRATCH[1], SCRATCH[2], WORLD);
            spawn(mc, player, signature(type), 0.02F);
        }

        if (accent && accentParticle(type) != null && spend()) {
            emitterPoint(type, slot, age, 0, SCRATCH);
            toWorld(ax, ay, az, k, yaw, SCRATCH[0], SCRATCH[1], SCRATCH[2], WORLD);
            spawn(mc, player, accentParticle(type), 0.03F);
        }

        if (type == Cosmetics.PHOENIX_PET && player.tickCount == (int)burstStart) {
            for (int i = 0; i < BURST_FEATHERS && spend(); i++) {
                float angle = Mth.TWO_PI * i / BURST_FEATHERS;
                toWorld(ax, ay, az, k, yaw, Mth.cos(angle) * 0.6F, 1.0F, Mth.sin(angle) * 0.6F, WORLD);
                spawn(mc, player, ParticleTypes.FLAME, 0.05F);
            }
        }

        if (type == Cosmetics.KRAKEN_PET && player.tickCount % 20 == 0) {
            for (int i = 0; i < 3 && spend(); i++) {
                toWorld(ax, ay, az, k, yaw, (RANDOM.nextFloat() - 0.5F) * 0.4F, 0.55F, (RANDOM.nextFloat() - 0.5F) * 0.4F, WORLD);
                spawn(mc, player, ParticleTypes.SQUID_INK, 0.02F);
            }
        }
    }

    private static void spawn(Minecraft mc, LocalPlayer player, ParticleOptions options, float spread) {
        mc.level.addParticle(
            options,
            player.getX() + WORLD[0],
            player.getY() + WORLD[1],
            player.getZ() + WORLD[2],
            (RANDOM.nextFloat() - 0.5F) * spread,
            0.01F + RANDOM.nextFloat() * spread,
            (RANDOM.nextFloat() - 0.5F) * spread
        );
    }

    private static ParticleOptions signature(int type) {
        return switch (type) {
            case Cosmetics.SKY_DRAGON_PET -> ParticleTypes.SMALL_FLAME;
            case Cosmetics.SERPENT_PET -> ParticleTypes.GLOW;
            case Cosmetics.TURTLE_PET -> ParticleTypes.DOLPHIN;
            case Cosmetics.PHOENIX_PET -> ParticleTypes.FLAME;
            case Cosmetics.SPIDER_PET -> ParticleTypes.WHITE_ASH;
            case Cosmetics.KRAKEN_PET -> ParticleTypes.GLOW_SQUID_INK;
            case Cosmetics.WYRM_PET -> ParticleTypes.PORTAL;
            case Cosmetics.EMBER_BEAST_PET -> ParticleTypes.FLAME;
            case Cosmetics.TIDE_TITAN_PET -> ParticleTypes.DRIPPING_WATER;
            case Cosmetics.LEAF_GOLEM_PET -> ParticleTypes.SPORE_BLOSSOM_AIR;
            case Cosmetics.STAR_BEETLE_PET -> ParticleTypes.END_ROD;
            case Cosmetics.CRYSTAL_STAG_PET -> STAG_DUST;
            default -> ParticleTypes.SMOKE;
        };
    }

    private static ParticleOptions accentParticle(int type) {
        return switch (type) {
            case Cosmetics.SKY_DRAGON_PET -> ParticleTypes.SMOKE;
            case Cosmetics.WYRM_PET -> ParticleTypes.REVERSE_PORTAL;
            case Cosmetics.EMBER_BEAST_PET -> ParticleTypes.LAVA;
            case Cosmetics.TIDE_TITAN_PET -> ParticleTypes.CLOUD;
            case Cosmetics.DUSK_FOX_PET -> ParticleTypes.WITCH;
            default -> null;
        };
    }

    private static void emitterPoint(int type, int slot, float age, int index, float[] out) {
        float jitter = (RANDOM.nextFloat() - 0.5F) * 0.3F;
        switch (type) {
            case Cosmetics.SKY_DRAGON_PET -> set3(out, jitter * 0.2F, 1.42F, -1.35F);
            case Cosmetics.SERPENT_PET, Cosmetics.WYRM_PET -> {
                int points = chainPoints(type);
                int point = (slot * BODY_POINTS + RANDOM.nextInt(points)) * 3;
                set3(out, CHAIN[point], CHAIN[point + 1], CHAIN[point + 2]);
            }
            case Cosmetics.TURTLE_PET -> set3(out, jitter * 2.0F, 1.0F, jitter * 2.0F);
            case Cosmetics.PHOENIX_PET -> {
                int point = (slot * BODY_POINTS + TAIL_POINTS - 1) * 3;
                set3(out, CHAIN[point], CHAIN[point + 1], CHAIN[point + 2]);
            }
            case Cosmetics.SPIDER_PET -> set3(out, jitter, 0.6F, 1.1F);
            case Cosmetics.KRAKEN_PET -> set3(out, jitter, 1.2F + jitter, jitter);
            case Cosmetics.EMBER_BEAST_PET -> set3(out, (index % 3 - 1) * 0.45F, 3.15F, -0.3F + (index % 3) * 0.4F);
            case Cosmetics.TIDE_TITAN_PET -> set3(out, jitter * 4.0F, 1.0F + RANDOM.nextFloat() * 2.0F, jitter * 3.0F);
            case Cosmetics.LEAF_GOLEM_PET -> set3(out, jitter * 4.0F, 3.3F, jitter * 4.0F);
            case Cosmetics.STAR_BEETLE_PET -> set3(out, jitter * 4.0F, 2.2F + jitter, 1.2F);
            case Cosmetics.CRYSTAL_STAG_PET -> set3(out, (index & 1) == 0 ? 0.5F : -0.5F, 3.2F + jitter, -1.0F);
            default -> set3(out, jitter * 2.0F, 2.2F, 0.6F + jitter);
        }
    }

    private static void set3(float[] out, float x, float y, float z) {
        out[0] = x;
        out[1] = y;
        out[2] = z;
    }

    private static float pulse(float age, float phase) {
        return Cosmetics.reduceMotion() ? 0.5F : 0.5F + 0.5F * Mth.sin(age * 0.25F + phase);
    }

    private static int tone(int argb, float alpha) {
        return Cosmetics.withAlpha(argb, (argb >>> 24) / 255.0F * alpha);
    }

    private static void draw(Geo geo, int type, int slot, int pass, float age, float alpha, int lod, float effects, int fx) {
        switch (type) {
            case Cosmetics.SKY_DRAGON_PET -> dragon(geo, pass, age, alpha, lod, effects, fx);
            case Cosmetics.SERPENT_PET -> serpent(geo, slot, pass, alpha, lod);
            case Cosmetics.TURTLE_PET -> turtle(geo, pass, age, alpha, fx);
            case Cosmetics.PHOENIX_PET -> phoenix(geo, slot, pass, age, alpha, lod, fx);
            case Cosmetics.SPIDER_PET -> spider(geo, slot, pass, age, alpha);
            case Cosmetics.KRAKEN_PET -> kraken(geo, slot, pass, age, alpha, lod);
            case Cosmetics.WYRM_PET -> wyrm(geo, slot, pass, alpha, lod);
            case Cosmetics.EMBER_BEAST_PET -> emberBeast(geo, pass, age, alpha, effects);
            case Cosmetics.TIDE_TITAN_PET -> tideTitan(geo, pass, age, alpha, effects);
            case Cosmetics.LEAF_GOLEM_PET -> leafGolem(geo, pass, age, alpha);
            case Cosmetics.STAR_BEETLE_PET -> starBeetle(geo, pass, age, alpha);
            case Cosmetics.CRYSTAL_STAG_PET -> crystalStag(geo, pass, age, alpha);
            default -> duskFox(geo, pass, age, alpha, lod);
        }
    }

    private static void dragon(Geo geo, int pass, float age, float alpha, int lod, float effects, int fx) {
        float bob = Mth.sin(age * 0.06F) * 0.06F;
        int teal = tone(0xFF1F5E6B, alpha);
        int gold = tone(0xFFE0B25A, alpha);
        int ivory = tone(0xFFE8E2D0, alpha);
        int membrane = tone(0xD02E8C8C, alpha);
        int innerMembrane = tone(0xD0184A50, alpha);
        int ember = 0xFFFF7A2E;
        float breath = Cosmetics.reduceMotion() ? 0.0F : age;
        if (pass == BODY) {
            blob(geo, BLOB, 0.0F, 0.9F + bob, 0.0F, 0.32F, 0.3F, 0.55F, teal, gold);
            blob(geo, BLOB, 0.0F, 0.95F + bob, -0.45F, 0.26F, 0.26F, 0.28F, teal, gold);
            tube(geo, 0.0F, 1.05F + bob, -0.6F, 0.0F, 1.4F + bob, -0.85F, 0.16F, 0.11F, teal);
            blob(geo, BLOB, 0.0F, 1.48F + bob, -0.98F, 0.15F, 0.13F, 0.22F, teal, gold);
            geo.box(-0.08F, 1.38F + bob, -1.26F, 0.08F, 1.5F + bob, -1.1F, teal);
            for (int side = -1; side <= 1; side += 2) {
                geo.tri(side * 0.07F, 1.58F + bob, -0.92F, side * 0.1F, 1.56F + bob, -0.86F, side * 0.16F, 1.78F + bob, -0.7F, ivory);
                tube(geo, side * 0.2F, 0.75F + bob, -0.3F, side * 0.24F, 0.35F + bob, -0.35F, 0.07F, 0.05F, teal);
                tube(geo, side * 0.22F, 0.75F + bob, 0.3F, side * 0.26F, 0.35F + bob, 0.35F, 0.07F, 0.05F, teal);
                dragonWing(geo, side, age, bob, membrane, innerMembrane, ivory);
            }

            for (int i = 0; i < 5; i++) {
                float z = -0.4F + i * 0.2F;
                geo.tri(-0.04F, 1.18F + bob, z - 0.06F, 0.04F, 1.18F + bob, z + 0.06F, 0.0F, 1.32F + bob, z, ivory);
            }

            int segments = 6 / lod;
            float prevX = 0.0F;
            float prevY = 0.85F + bob;
            float prevZ = 0.5F;
            for (int s = 1; s <= segments; s++) {
                float t = (float)s / segments;
                float x = Mth.sin(age * 0.05F + t * 3.0F) * 0.25F * t;
                float y = 0.85F - 0.25F * t + bob;
                float z = 0.5F + t;
                tube(geo, prevX, prevY, prevZ, x, y, z, 0.2F * (1.0F - t) + 0.05F, 0.2F * (1.0F - t) + 0.03F, teal);
                prevX = x;
                prevY = y;
                prevZ = z;
            }
        } else if (pass == EMISSIVE) {
            int eye = tone(0xFFFFC14A, alpha);
            geo.box(0.05F, 1.5F + bob, -1.14F, 0.1F, 1.54F + bob, -1.1F, eye);
            geo.box(-0.1F, 1.5F + bob, -1.14F, -0.05F, 1.54F + bob, -1.1F, eye);
            int wisps = Math.max(1, Math.round(EMBER_WISPS * Math.min(1.0F, effects)));
            for (int i = 0; i < wisps; i++) {
                float phase = frac(breath * 0.03F + i * 0.25F);
                float size = 0.04F * (1.0F - phase) + 0.01F;
                CosmeticsLayer.crossDiamond(geo, Mth.sin(i * 2.1F + breath * 0.1F) * 0.05F, 1.42F + phase * 0.25F + bob, -1.3F - phase * 0.6F, size, tone(ember, 1.0F - phase));
            }
        } else {
            float glow = 0.8F + 0.2F * pulse(age, 0.0F);
            blob(geo, BLOB, 0.0F, 0.95F + bob, -0.45F, 0.3F, 0.3F, 0.32F, CosmeticsLayer.glow(ember, 0.16F * glow), CosmeticsLayer.glow(ember, 0.08F * glow));
            for (int i = 0; i < EMBER_WISPS; i++) {
                float phase = frac(breath * 0.03F + i * 0.25F);
                CosmeticsLayer.crossDiamond(geo, 0.0F, 1.42F + phase * 0.25F + bob, -1.3F - phase * 0.6F, 0.1F, CosmeticsLayer.glow(ember, 0.3F * (1.0F - phase)));
            }

            if (fx == 2) {
                int heat = CosmeticsLayer.glow(0xFFFF9A50, 0.06F);
                float wobble = Cosmetics.reduceMotion() ? 0.0F : Mth.sin(age * 0.2F) * 0.03F;
                geo.quad(-0.15F + wobble, 1.35F + bob, -1.5F, 0.15F + wobble, 1.35F + bob, -1.5F, 0.15F - wobble, 1.62F + bob, -1.75F, -0.15F - wobble, 1.62F + bob, -1.75F, heat);
                geo.quad(-0.12F - wobble, 1.3F + bob, -1.6F, 0.12F - wobble, 1.3F + bob, -1.6F, 0.12F + wobble, 1.55F + bob, -1.9F, -0.12F + wobble, 1.55F + bob, -1.9F, heat);
            }
        }
    }

    private static void dragonWing(Geo geo, int side, float age, float bob, int membrane, int inner, int bone) {
        float flap = Mth.sin(age * 0.12F) * 25.0F * Mth.DEG_TO_RAD + 0.25F;
        float cos = Mth.cos(flap);
        float sin = Mth.sin(flap);
        float sx = side * 0.25F;
        float sy = 1.15F + bob;
        float p1x = sx + side * cos;
        float p1y = sy + sin;
        float p2x = sx + side * 0.85F * cos;
        float p2y = sy + 0.85F * sin - 0.15F;
        float p3x = sx + side * 0.4F * cos;
        float p3y = sy + 0.4F * sin - 0.25F;
        geo.tri(sx, sy - 0.02F, -0.2F, sx + (p1x - sx) * 0.75F, sy + (p1y - sy) * 0.75F - 0.02F, -0.12F, sx + (p2x - sx) * 0.75F, sy + (p2y - sy) * 0.75F - 0.02F, 0.3F, inner);
        geo.tri(sx, sy, -0.2F, p1x, p1y, -0.1F, p2x, p2y, 0.45F, membrane);
        geo.tri(sx, sy, -0.2F, p2x, p2y, 0.45F, p3x, p3y, 0.6F, membrane);
        tube(geo, sx, sy, -0.2F, p1x, p1y, -0.1F, 0.035F, 0.015F, bone);
    }

    private static void serpent(Geo geo, int slot, int pass, float alpha, int lod) {
        int jade = tone(0xFF2F8F6B, alpha);
        int band = tone(0xFF23704F, alpha);
        int cream = tone(0xFFE9E3C2, alpha);
        int coral = tone(0xCCFF7F6A, alpha);
        int first = slot * BODY_POINTS;
        int step = lod;
        if (pass == BODY) {
            for (int i = 0; i + step < SERPENT_POINTS; i += step) {
                float t = (float)i / (SERPENT_POINTS - 1);
                int a = (first + i) * 3;
                int b = (first + i + step) * 3;
                tube(geo, CHAIN_SHOWN[a], CHAIN_SHOWN[a + 1], CHAIN_SHOWN[a + 2], CHAIN_SHOWN[b], CHAIN_SHOWN[b + 1], CHAIN_SHOWN[b + 2], 0.13F - 0.1F * t, 0.12F - 0.1F * t, (i / step & 1) == 0 ? jade : band);
                if (i % 3 == 1) {
                    geo.tri(CHAIN_SHOWN[a], CHAIN_SHOWN[a + 1] + 0.08F, CHAIN_SHOWN[a + 2], CHAIN_SHOWN[a], CHAIN_SHOWN[a + 1] + 0.2F, CHAIN_SHOWN[a + 2], CHAIN_SHOWN[b], CHAIN_SHOWN[b + 1] + 0.08F, CHAIN_SHOWN[b + 2], coral);
                }
            }

            int head = first * 3;
            blob(geo, BLOB, CHAIN_SHOWN[head], CHAIN_SHOWN[head + 1], CHAIN_SHOWN[head + 2], 0.14F, 0.11F, 0.14F, jade, cream);
            headDirection(CHAIN_SHOWN, first, SCRATCH);
            tube(geo, CHAIN_SHOWN[head], CHAIN_SHOWN[head + 1] - 0.02F, CHAIN_SHOWN[head + 2], CHAIN_SHOWN[head] + SCRATCH[0] * 0.2F, CHAIN_SHOWN[head + 1] - 0.03F + SCRATCH[1] * 0.2F, CHAIN_SHOWN[head + 2] + SCRATCH[2] * 0.2F, 0.08F, 0.05F, cream);
        } else if (pass == EMISSIVE) {
            int head = first * 3;
            headDirection(CHAIN_SHOWN, first, SCRATCH);
            float rightX = -SCRATCH[2];
            float rightZ = SCRATCH[0];
            float inverse = Mth.invSqrt(Math.max(1.0E-6F, rightX * rightX + rightZ * rightZ));
            rightX *= inverse * 0.09F;
            rightZ *= inverse * 0.09F;
            int eye = tone(0xFFB8FF5A, alpha);
            for (int side = -1; side <= 1; side += 2) {
                float x = CHAIN_SHOWN[head] + side * rightX + SCRATCH[0] * 0.06F;
                float y = CHAIN_SHOWN[head + 1] + 0.05F;
                float z = CHAIN_SHOWN[head + 2] + side * rightZ + SCRATCH[2] * 0.06F;
                geo.cube(x, y, z, 0.022F, eye);
            }
        } else {
            int spine = CosmeticsLayer.glow(0xFF7CFFC8, 0.25F);
            for (int i = 1; i < SERPENT_POINTS; i += 2 * step) {
                int a = (first + i) * 3;
                CosmeticsLayer.crossDiamond(geo, CHAIN_SHOWN[a], CHAIN_SHOWN[a + 1] + 0.12F, CHAIN_SHOWN[a + 2], 0.06F, spine);
            }
        }
    }

    private static void headDirection(float[] points, int first, float[] out) {
        int a = first * 3;
        int b = a + 3;
        float dx = points[a] - points[b];
        float dy = points[a + 1] - points[b + 1];
        float dz = points[a + 2] - points[b + 2];
        float inverse = Mth.invSqrt(Math.max(1.0E-8F, dx * dx + dy * dy + dz * dz));
        out[0] = dx * inverse;
        out[1] = dy * inverse;
        out[2] = dz * inverse;
    }

    private static void turtle(Geo geo, int pass, float age, float alpha, int fx) {
        float paddle = Mth.sin(age * 0.08F) * 0.12F;
        float bob = Mth.sin(age * 0.04F) * 0.05F;
        int shell = tone(0xFF3B5D7A, alpha);
        int shellDark = tone(0xFF26405A, alpha);
        int skin = tone(0xFF6E8F5A, alpha);
        int belly = tone(0xFFC9D6A0, alpha);
        if (pass == BODY) {
            blob(geo, BLOB, 0.0F, 0.6F + bob, 0.0F, 0.75F, 0.42F, 0.85F, shell, shellDark);
            blob(geo, BLOB, 0.0F, 0.38F + bob, 0.0F, 0.7F, 0.12F, 0.8F, belly, skin);
            blob(geo, SMALL_BLOB, 0.0F, 0.55F + bob, -0.98F, 0.2F, 0.17F, 0.24F, skin, belly);
            tube(geo, 0.0F, 0.5F + bob, -0.7F, 0.0F, 0.55F + bob, -0.85F, 0.14F, 0.12F, skin);
            for (int side = -1; side <= 1; side += 2) {
                tube(geo, side * 0.6F, 0.42F + bob, -0.45F, side * 1.0F, 0.32F + bob + paddle * side, -0.75F, 0.13F, 0.05F, skin);
                tube(geo, side * 0.55F, 0.4F + bob, 0.55F, side * 0.8F, 0.32F + bob - paddle * side, 0.85F, 0.1F, 0.04F, skin);
            }

            tube(geo, 0.0F, 0.42F + bob, 0.8F, 0.0F, 0.38F + bob, 1.0F, 0.07F, 0.02F, skin);
        } else if (pass == EMISSIVE) {
            int crystal = tone(0xCC9AE8FF, alpha);
            int core = tone(0xFFDDF8FF, alpha);
            crystal(geo, 0.0F, 1.08F + bob, 0.0F, 0.12F, 0.28F, crystal);
            crystal(geo, 0.0F, 1.08F + bob, 0.0F, 0.05F, 0.2F, core);
            for (int i = 0; i < 6; i++) {
                float angle = Mth.TWO_PI * i / 6.0F;
                crystal(geo, Mth.cos(angle) * 0.45F, 0.92F + bob, Mth.sin(angle) * 0.5F, 0.08F, 0.18F, crystal);
            }

            float orbit = Cosmetics.reduceMotion() ? 0.0F : age * 0.02F;
            for (int i = 0; i < SHELL_SHARDS; i++) {
                float angle = orbit + Mth.TWO_PI * i / SHELL_SHARDS;
                crystal(geo, Mth.cos(angle) * 1.1F, 0.9F + bob + Mth.sin(angle * 2.0F) * 0.1F, Mth.sin(angle) * 1.1F, 0.05F, 0.09F, core);
            }
        } else {
            int glow = CosmeticsLayer.glow(0xFF9AE8FF, 0.22F);
            CosmeticsLayer.crossDiamond(geo, 0.0F, 1.08F + bob, 0.0F, 0.32F, glow);
            float orbit = Cosmetics.reduceMotion() ? 0.0F : age * 0.02F;
            for (int i = 0; i < SHELL_SHARDS; i++) {
                float angle = orbit + Mth.TWO_PI * i / SHELL_SHARDS;
                CosmeticsLayer.crossDiamond(geo, Mth.cos(angle) * 1.1F, 0.9F + bob + Mth.sin(angle * 2.0F) * 0.1F, Mth.sin(angle) * 1.1F, 0.14F, glow);
            }

            if (fx == 2) {
                for (int i = 0; i < 6; i++) {
                    float angle = Mth.TWO_PI * i / 6.0F;
                    CosmeticsLayer.crossDiamond(geo, Mth.cos(angle) * 0.45F, 0.92F + bob, Mth.sin(angle) * 0.5F, 0.16F, CosmeticsLayer.glow(0xFF9AE8FF, 0.1F));
                }
            }
        }
    }

    private static void phoenix(Geo geo, int slot, int pass, float age, float alpha, int lod, int fx) {
        float burst = Math.clamp((age - burstStart) / BURST_LENGTH, 0.0F, 1.0F);
        boolean bursting = age - burstStart < BURST_LENGTH && age >= burstStart;
        float swell = bursting && !Cosmetics.reduceMotion() ? 1.0F + 0.08F * Mth.sin(burst * (float)Math.PI) : 1.0F;
        float bob = Mth.sin(age * 0.07F) * 0.08F;
        int crimson = tone(0xFFE0402A, alpha);
        int orange = tone(0xFFFF9A2E, alpha);
        int gold = tone(0xFFFFE070, alpha);
        int first = slot * BODY_POINTS;
        if (pass == BODY) {
            int beak = tone(0xFFB8862E, alpha);
            geo.tri(-0.05F, 1.36F + bob, -0.4F, 0.05F, 1.36F + bob, -0.4F, 0.0F, 1.3F + bob, -0.58F, beak);
            for (int side = -1; side <= 1; side += 2) {
                tube(geo, side * 0.1F, 0.78F + bob, -0.05F, side * 0.12F, 0.55F + bob, -0.1F, 0.035F, 0.02F, beak);
            }
        } else if (pass == EMISSIVE) {
            blob(geo, BLOB, 0.0F, 1.0F + bob, 0.0F, 0.25F * swell, 0.28F * swell, 0.35F * swell, orange, crimson);
            blob(geo, SMALL_BLOB, 0.0F, 1.35F + bob, -0.28F, 0.14F, 0.14F, 0.15F, orange, crimson);
            for (int i = 0; i < 5; i++) {
                float spread = (i - 2) * 0.12F;
                geo.tri(spread * 0.3F - 0.03F, 1.45F + bob, -0.25F, spread * 0.3F + 0.03F, 1.45F + bob, -0.2F, spread, 1.75F + bob - Math.abs(spread) * 0.5F, 0.05F, gold);
            }

            float flap = Mth.sin(age * 0.1F) * 0.5F;
            for (int side = -1; side <= 1; side += 2) {
                for (int f = 0; f < 5; f++) {
                    float angle = 0.2F + f * 0.22F + flap;
                    float length = 1.0F - f * 0.1F;
                    float tipX = side * (0.2F + Mth.cos(angle) * length);
                    float tipY = 1.1F + bob + Mth.sin(angle) * length * 0.6F;
                    float tipZ = 0.05F + f * 0.12F;
                    geo.quad(side * 0.2F, 1.1F + bob, -0.1F + f * 0.06F, side * 0.2F, 1.16F + bob, f * 0.06F, tipX, tipY + 0.06F, tipZ + 0.06F, tipX, tipY, tipZ, f < 2 ? gold : orange);
                }
            }

            for (int i = 0; i + lod < TAIL_POINTS; i += lod) {
                int a = (first + i) * 3;
                int b = (first + i + lod) * 3;
                float t = (float)i / TAIL_POINTS;
                float width = 0.08F + 0.18F * t;
                int color = Colors.lerp(orange, gold, t);
                geo.quad(CHAIN_SHOWN[a] - width, CHAIN_SHOWN[a + 1], CHAIN_SHOWN[a + 2], CHAIN_SHOWN[a] + width, CHAIN_SHOWN[a + 1], CHAIN_SHOWN[a + 2], CHAIN_SHOWN[b] + width * 1.2F, CHAIN_SHOWN[b + 1], CHAIN_SHOWN[b + 2], CHAIN_SHOWN[b] - width * 1.2F, CHAIN_SHOWN[b + 1], CHAIN_SHOWN[b + 2], color);
                geo.quad(CHAIN_SHOWN[a], CHAIN_SHOWN[a + 1] - width, CHAIN_SHOWN[a + 2], CHAIN_SHOWN[a], CHAIN_SHOWN[a + 1] + width, CHAIN_SHOWN[a + 2], CHAIN_SHOWN[b], CHAIN_SHOWN[b + 1] + width, CHAIN_SHOWN[b + 2], CHAIN_SHOWN[b], CHAIN_SHOWN[b + 1] - width, CHAIN_SHOWN[b + 2], tone(crimson, 0.85F));
            }
        } else {
            float glow = 0.85F + 0.15F * pulse(age, 1.0F);
            blob(geo, BLOB, 0.0F, 1.0F + bob, 0.0F, 0.34F, 0.36F, 0.44F, CosmeticsLayer.glow(0xFFFF9A2E, 0.2F * glow), CosmeticsLayer.glow(0xFFE0402A, 0.12F * glow));
            if (bursting) {
                float radius = 0.5F + 1.2F * burst;
                int ring = CosmeticsLayer.glow(0xFFFFB347, 0.45F * (1.0F - burst));
                for (int i = 0; i < BURST_FEATHERS; i++) {
                    float angle = Mth.TWO_PI * i / BURST_FEATHERS;
                    CosmeticsLayer.crossDiamond(geo, Mth.cos(angle) * radius, 1.0F + bob, Mth.sin(angle) * radius, 0.15F, ring);
                }
            }

            if (fx == 2) {
                int a = (first + TAIL_POINTS - 1) * 3;
                CosmeticsLayer.crossDiamond(geo, CHAIN_SHOWN[a], CHAIN_SHOWN[a + 1], CHAIN_SHOWN[a + 2], 0.2F, CosmeticsLayer.glow(0xFFFFE070, 0.2F * glow));
            }
        }
    }

    private static void spider(Geo geo, int slot, int pass, float age, float alpha) {
        float sway = Mth.sin(age * 0.05F) * 0.04F;
        int chitin = tone(0xFF2A2230, alpha);
        int stripe = tone(0xFF8C2E3A, alpha);
        int leg = tone(0xFF3A3040, alpha);
        if (pass == BODY) {
            blob(geo, BLOB, 0.0F, 0.95F + sway, 0.55F, 0.55F, 0.45F, 0.65F, stripe, chitin);
            blob(geo, BLOB, 0.0F, 0.85F, -0.2F, 0.35F, 0.25F, 0.38F, chitin, chitin);
            for (int side = -1; side <= 1; side += 2) {
                geo.tri(side * 0.08F, 0.75F, -0.52F, side * 0.04F, 0.75F, -0.5F, side * 0.06F, 0.55F, -0.6F, stripe);
            }

            int first = slot * LEGS;
            for (int index = 0; index < LEGS; index++) {
                footHip(index, SCRATCH);
                float hx = SCRATCH[0];
                float hy = SCRATCH[1];
                float hz = SCRATCH[2];
                int f = (first + index) * 3;
                float fx = FOOT_SHOWN[f];
                float fy = FOOT_SHOWN[f + 1];
                float fz = FOOT_SHOWN[f + 2];
                knee(hx, hy, hz, fx, fy, fz, 0.75F, 0.85F, SCRATCH);
                tube(geo, hx, hy, hz, SCRATCH[0], SCRATCH[1], SCRATCH[2], 0.06F, 0.05F, leg);
                tube(geo, SCRATCH[0], SCRATCH[1], SCRATCH[2], fx, fy, fz, 0.05F, 0.02F, leg);
            }

            int silk = tone(0x90F2F2F2, alpha);
            float drift = Mth.sin(age * 0.03F) * 0.1F;
            geo.quad(-0.01F, 0.6F, 1.1F, 0.01F, 0.6F, 1.1F, 0.01F + drift, -0.8F, 1.3F, -0.01F + drift, -0.8F, 1.3F, silk);
            geo.quad(0.0F, 0.6F, 1.09F, 0.0F, 0.6F, 1.11F, drift, -0.8F, 1.31F, drift, -0.8F, 1.29F, silk);
        } else if (pass == EMISSIVE) {
            int eye = tone(0xFFB06BFF, alpha);
            for (int i = 0; i < 6; i++) {
                float x = (i % 3 - 1) * 0.08F;
                float y = 0.95F + (i / 3) * 0.06F;
                geo.quad(x - 0.02F, y - 0.02F, -0.57F, x + 0.02F, y - 0.02F, -0.57F, x + 0.02F, y + 0.02F, -0.57F, x - 0.02F, y + 0.02F, -0.57F, eye);
            }
        } else {
            CosmeticsLayer.crossDiamond(geo, 0.0F, 0.98F, -0.6F, 0.12F, CosmeticsLayer.glow(0xFFB06BFF, 0.2F));
        }
    }

    private static void knee(float hx, float hy, float hz, float fx, float fy, float fz, float upper, float lower, float[] out) {
        float dx = fx - hx;
        float dy = fy - hy;
        float dz = fz - hz;
        float distance = (float)Math.sqrt(dx * dx + dy * dy + dz * dz);
        float reach = Math.clamp(distance, 0.05F, upper + lower - 0.01F);
        float inverse = 1.0F / Math.max(1.0E-4F, distance);
        dx *= inverse;
        dy *= inverse;
        dz *= inverse;
        float along = (upper * upper - lower * lower + reach * reach) / (2.0F * reach);
        float height = (float)Math.sqrt(Math.max(0.0F, upper * upper - along * along));
        float upX = -dx * dy;
        float upY = 1.0F - dy * dy;
        float upZ = -dz * dy;
        float upInverse = Mth.invSqrt(Math.max(1.0E-8F, upX * upX + upY * upY + upZ * upZ));
        out[0] = hx + dx * along + upX * upInverse * height;
        out[1] = hy + dy * along + upY * upInverse * height;
        out[2] = hz + dz * along + upZ * upInverse * height;
    }

    private static void kraken(Geo geo, int slot, int pass, float age, float alpha, int lod) {
        float bob = Mth.sin(age * 0.05F) * 0.08F;
        int purple = tone(0xFF4B2A6B, alpha);
        int light = tone(0xFF7A4E9C, alpha);
        int sucker = tone(0xFFE79AC0, alpha);
        if (pass == BODY) {
            blob(geo, BLOB, 0.0F, 1.35F + bob, 0.0F, 0.42F, 0.6F, 0.42F, purple, light);
            blob(geo, BLOB, 0.0F, 0.85F + bob, 0.0F, 0.38F, 0.3F, 0.38F, light, sucker);
            for (int side = -1; side <= 1; side += 2) {
                geo.tri(side * 0.3F, 1.6F + bob, 0.0F, side * 0.55F, 1.85F + bob, 0.1F, side * 0.3F, 1.9F + bob, 0.05F, purple);
                geo.cube(side * 0.3F, 0.92F + bob, -0.3F, 0.03F, tone(0xFF101018, alpha));
            }

            int arm = slot * ARMS * ARM_POINTS;
            for (int i = 0; i < ARMS; i++) {
                int start = arm + i * ARM_POINTS;
                armRoot(i, age, SCRATCH);
                float shiftX = SCRATCH[0] - ARM_SHOWN[start * 3];
                float shiftY = SCRATCH[1] + bob - ARM_SHOWN[start * 3 + 1];
                float shiftZ = SCRATCH[2] - ARM_SHOWN[start * 3 + 2];
                for (int j = 0; j + lod < ARM_POINTS; j += lod) {
                    int a = (start + j) * 3;
                    int b = (start + j + lod) * 3;
                    float t = (float)j / ARM_POINTS;
                    tube(
                        geo,
                        ARM_SHOWN[a] + shiftX, ARM_SHOWN[a + 1] + shiftY, ARM_SHOWN[a + 2] + shiftZ,
                        ARM_SHOWN[b] + shiftX, ARM_SHOWN[b + 1] + shiftY, ARM_SHOWN[b + 2] + shiftZ,
                        0.1F - 0.09F * t, 0.09F - 0.08F * t, (j / lod & 1) == 0 ? purple : sucker
                    );
                }
            }
        } else if (pass == EMISSIVE) {
            int eye = tone(0xFFFFD25A, alpha);
            for (int side = -1; side <= 1; side += 2) {
                geo.cube(side * 0.3F, 0.95F + bob, -0.28F, 0.06F, eye);
            }
        } else {
            for (int i = 0; i < 6; i++) {
                float angle = Mth.TWO_PI * i / 6.0F;
                float intensity = 0.12F + 0.1F * pulse(age, i);
                CosmeticsLayer.crossDiamond(geo, Mth.cos(angle) * 0.4F, 1.4F + bob + (i & 1) * 0.2F, Mth.sin(angle) * 0.4F, 0.1F, CosmeticsLayer.glow(0xFF5AF0FF, intensity));
            }
        }
    }

    private static void wyrm(Geo geo, int slot, int pass, float alpha, int lod) {
        int obsidian = tone(0xFF22202A, alpha);
        int amethyst = tone(0xFF6B3FA0, alpha);
        int bronze = tone(0xFF9C6B3A, alpha);
        int first = slot * BODY_POINTS;
        if (pass == BODY) {
            for (int i = 0; i + lod < WYRM_POINTS; i += lod) {
                float t = (float)i / (WYRM_POINTS - 1);
                int a = (first + i) * 3;
                int b = (first + i + lod) * 3;
                int section = i / 8;
                int color = section == 0 ? amethyst : section == 1 ? obsidian : bronze;
                tube(geo, CHAIN_SHOWN[a], CHAIN_SHOWN[a + 1], CHAIN_SHOWN[a + 2], CHAIN_SHOWN[b], CHAIN_SHOWN[b + 1], CHAIN_SHOWN[b + 2], 0.14F - 0.11F * t, 0.13F - 0.11F * t, color);
                if ((i / lod & 1) == 0) {
                    geo.tri(CHAIN_SHOWN[a], CHAIN_SHOWN[a + 1] + 0.1F, CHAIN_SHOWN[a + 2], CHAIN_SHOWN[a], CHAIN_SHOWN[a + 1] + 0.24F - 0.12F * t, CHAIN_SHOWN[a + 2], CHAIN_SHOWN[b], CHAIN_SHOWN[b + 1] + 0.1F, CHAIN_SHOWN[b + 2], bronze);
                }
            }

            int head = first * 3;
            float x = CHAIN_SHOWN[head];
            float y = CHAIN_SHOWN[head + 1];
            float z = CHAIN_SHOWN[head + 2];
            blob(geo, BLOB, x, y, z, 0.17F, 0.13F, 0.17F, amethyst, obsidian);
            headDirection(CHAIN_SHOWN, first, SCRATCH);
            tube(geo, x, y - 0.03F, z, x + SCRATCH[0] * 0.25F, y - 0.05F + SCRATCH[1] * 0.25F, z + SCRATCH[2] * 0.25F, 0.1F, 0.05F, obsidian);
            for (int side = -1; side <= 1; side += 2) {
                geo.tri(x + side * 0.06F, y + 0.1F, z, x + side * 0.1F, y + 0.08F, z, x + side * 0.16F - SCRATCH[0] * 0.2F, y + 0.32F, z - SCRATCH[2] * 0.2F, bronze);
            }
        } else if (pass == EMISSIVE) {
            int head = first * 3;
            geo.cube(CHAIN_SHOWN[head], CHAIN_SHOWN[head + 1] + 0.06F, CHAIN_SHOWN[head + 2], 0.035F, tone(0xFFC28BFF, alpha));
        } else {
            int rune = CosmeticsLayer.glow(0xFFC28BFF, 0.25F);
            for (int i = 2; i < WYRM_POINTS; i += 3 * lod) {
                int a = (first + i) * 3;
                CosmeticsLayer.crossDiamond(geo, CHAIN_SHOWN[a], CHAIN_SHOWN[a + 1] + 0.16F, CHAIN_SHOWN[a + 2], 0.06F, rune);
            }
        }
    }

    private static void emberBeast(Geo geo, int pass, float age, float alpha, float effects) {
        float breathe = Mth.sin(age * 0.05F) * 0.04F;
        int basalt = tone(0xFF2B2422, alpha);
        int plate = tone(0xFF4A3A33, alpha);
        int magma = 0xFFFF6A1E;
        if (pass == BODY) {
            blob(geo, BLOB, 0.0F, 1.7F, 0.1F, 1.0F, 0.95F + breathe, 1.3F, basalt, basalt);
            for (int i = 0; i < 3; i++) {
                blob(geo, SMALL_BLOB, 0.0F, 2.3F + breathe, -0.6F + i * 0.7F, 0.9F, 0.45F, 0.45F, plate, basalt);
            }

            blob(geo, SMALL_BLOB, 0.0F, 1.4F, -1.3F, 0.45F, 0.4F, 0.45F, plate, basalt);
            geo.box(-0.25F, 1.1F, -1.62F, 0.25F, 1.4F, -1.38F, basalt);
            for (int side = -1; side <= 1; side += 2) {
                tube(geo, side * 0.7F, 1.2F, -0.6F, side * 0.8F, 0.0F, -0.7F, 0.3F, 0.25F, basalt);
                tube(geo, side * 0.7F, 1.2F, 0.7F, side * 0.8F, 0.0F, 0.8F, 0.3F, 0.25F, basalt);
            }

            tube(geo, 0.0F, 1.3F, 1.3F, 0.0F, 0.7F, 1.6F, 0.25F, 0.08F, plate);
            for (int i = 0; i < 3; i++) {
                float x = (i - 1) * 0.45F;
                float z = -0.3F + i * 0.4F;
                tube(geo, x, 2.6F, z, x, 3.1F, z, 0.14F, 0.12F, plate);
            }
        } else if (pass == EMISSIVE) {
            int crack = tone(magma, alpha);
            for (int i = 0; i < 3; i++) {
                float z = -0.6F + i * 0.7F;
                geo.quad(-0.5F, 2.62F + breathe, z - 0.03F, 0.5F, 2.62F + breathe, z - 0.03F, 0.5F, 2.62F + breathe, z + 0.03F, -0.5F, 2.62F + breathe, z + 0.03F, crack);
            }

            geo.cube(0.2F, 1.5F, -1.72F, 0.04F, crack);
            geo.cube(-0.2F, 1.5F, -1.72F, 0.04F, crack);
            int wisps = Math.max(1, Math.round(VENT_WISPS * Math.min(1.0F, effects)));
            float flow = Cosmetics.reduceMotion() ? 0.0F : age;
            for (int i = 0; i < wisps; i++) {
                int vent = i % 3;
                float phase = frac(flow * 0.025F + i * 0.17F);
                float x = (vent - 1) * 0.45F + Mth.sin(flow * 0.07F + i) * 0.05F;
                CosmeticsLayer.crossDiamond(geo, x, 3.15F + phase * 0.4F, -0.3F + vent * 0.4F, 0.1F * (1.0F - phase) + 0.02F, tone(0xFFFFB347, 1.0F - phase));
            }
        } else {
            for (int vent = 0; vent < 3; vent++) {
                CosmeticsLayer.crossDiamond(geo, (vent - 1) * 0.45F, 3.2F, -0.3F + vent * 0.4F, 0.25F, CosmeticsLayer.glow(magma, 0.18F + 0.06F * pulse(age, vent)));
            }
        }
    }

    private static void tideTitan(Geo geo, int pass, float age, float alpha, float effects) {
        float sway = Mth.sin(age * 0.04F) * 0.05F;
        int deep = tone(0xFF1B4A6E, alpha);
        int foam = tone(0xFF7FE0D2, alpha);
        int coral = tone(0xFFFF8A7A, alpha);
        if (pass == BODY) {
            blob(geo, BLOB, 0.0F, 0.75F, 0.0F, 0.85F, 0.6F, 0.85F, deep, foam);
            blob(geo, BLOB, sway, 1.65F, 0.0F, 0.7F, 0.5F, 0.7F, deep, foam);
            blob(geo, SMALL_BLOB, sway * 2.0F, 2.4F, 0.0F, 0.55F, 0.4F, 0.55F, deep, foam);
            blob(geo, SMALL_BLOB, sway * 3.0F, 2.95F, -0.05F, 0.4F, 0.3F, 0.45F, foam, deep);
            float flap = Mth.sin(age * 0.06F) * 0.35F;
            for (int side = -1; side <= 1; side += 2) {
                geo.tri(side * 0.6F, 1.8F, -0.3F, side * (1.6F), 1.5F + flap, 0.2F, side * 0.6F, 1.5F, 0.4F, foam);
                geo.tri(side * 0.6F, 1.8F, -0.3F, side * 1.2F, 2.1F + flap * 0.5F, -0.1F, side * 1.6F, 1.5F + flap, 0.2F, deep);
            }

            for (int i = 0; i < 4; i++) {
                float angle = Mth.TWO_PI * i / 4.0F + 0.4F;
                tube(geo, Mth.cos(angle) * 0.2F + sway * 3.0F, 3.2F, Mth.sin(angle) * 0.2F, Mth.cos(angle) * 0.35F + sway * 3.0F, 3.6F, Mth.sin(angle) * 0.35F, 0.07F, 0.03F, coral);
            }

            for (int i = 0; i < 3; i++) {
                float x = (i - 1) * 0.3F;
                tube(geo, x, 0.25F, -0.2F, x + Mth.sin(age * 0.05F + i) * 0.1F, -0.25F, -0.3F, 0.08F, 0.02F, foam);
            }
        } else if (pass == EMISSIVE) {
            int eye = tone(0xFF9FF6FF, alpha);
            geo.cube(0.18F + sway * 3.0F, 3.0F, -0.42F, 0.05F, eye);
            geo.cube(-0.18F + sway * 3.0F, 3.0F, -0.42F, 0.05F, eye);
            int drops = Math.max(1, Math.round(RAIN_DROPS * Math.min(1.0F, effects)));
            float flow = Cosmetics.reduceMotion() ? 0.0F : age;
            for (int i = 0; i < drops; i++) {
                float phase = frac(flow * 0.02F + i * 0.19F);
                float angle = i * 1.1F;
                CosmeticsLayer.crossDiamond(geo, Mth.cos(angle) * 1.0F, 2.6F - phase * 2.4F, Mth.sin(angle) * 1.0F, 0.04F, tone(0xC09FF6FF, 1.0F - phase * 0.7F));
            }
        } else {
            CosmeticsLayer.crossDiamond(geo, sway * 3.0F, 3.0F, -0.42F, 0.3F, CosmeticsLayer.glow(0xFF9FF6FF, 0.16F));
            CosmeticsLayer.crossDiamond(geo, sway, 1.65F, 0.0F, 0.9F, CosmeticsLayer.glow(0xFF7FE0D2, 0.06F));
        }
    }

    private static void leafGolem(Geo geo, int pass, float age, float alpha) {
        float lean = Mth.sin(age * 0.03F) * 0.05F;
        int moss = tone(0xFF5E6B4A, alpha);
        int bark = tone(0xFF5A4030, alpha);
        int leaf = tone(0xFF4FA34A, alpha);
        int leafLight = tone(0xFF7CCB5A, alpha);
        if (pass == BODY) {
            blob(geo, BLOB, 0.0F, 2.1F, -0.2F + lean, 0.9F, 0.8F, 0.7F, moss, bark);
            blob(geo, SMALL_BLOB, 0.0F, 2.6F, -0.75F + lean, 0.3F, 0.28F, 0.3F, moss, bark);
            for (int side = -1; side <= 1; side += 2) {
                tube(geo, side * 0.85F, 2.5F, -0.3F + lean, side * 1.2F, 1.4F, -0.7F, 0.3F, 0.26F, bark);
                tube(geo, side * 1.2F, 1.4F, -0.7F, side * 1.1F, 0.0F, -0.9F, 0.26F, 0.22F, moss);
                tube(geo, side * 0.5F, 1.4F, 0.4F, side * 0.55F, 0.0F, 0.5F, 0.28F, 0.24F, bark);
            }

            for (int i = 0; i < 10; i++) {
                float angle = Mth.TWO_PI * i / 10.0F;
                float r = i % 2 == 0 ? 0.8F : 0.55F;
                float x = Mth.cos(angle) * r;
                float z = Mth.sin(angle) * r + 0.1F;
                float rustle = Mth.sin(age * 0.06F + i) * 0.04F;
                geo.quad(x * 0.3F, 2.9F, z * 0.3F, x * 0.3F + 0.1F, 2.95F, z * 0.3F, x + 0.12F, 3.3F + rustle + (i & 1) * 0.3F, z, x - 0.1F, 3.25F + rustle + (i & 1) * 0.3F, z, (i & 1) == 0 ? leaf : leafLight);
            }
        } else if (pass == EMISSIVE) {
            int bloom = tone(0xFFFF9EC7, alpha);
            for (int i = 0; i < 5; i++) {
                float angle = Mth.TWO_PI * i / 5.0F + 0.3F;
                CosmeticsLayer.crossDiamond(geo, Mth.cos(angle) * 0.55F, 3.35F + (i & 1) * 0.15F, Mth.sin(angle) * 0.55F + 0.1F, 0.05F, bloom);
            }

            int eye = tone(0xFFC8FF7A, alpha);
            geo.cube(0.12F, 2.65F, -1.03F + lean, 0.035F, eye);
            geo.cube(-0.12F, 2.65F, -1.03F + lean, 0.035F, eye);
        } else {
            for (int i = 0; i < 5; i++) {
                float angle = Mth.TWO_PI * i / 5.0F + 0.3F;
                CosmeticsLayer.crossDiamond(geo, Mth.cos(angle) * 0.55F, 3.35F + (i & 1) * 0.15F, Mth.sin(angle) * 0.55F + 0.1F, 0.14F, CosmeticsLayer.glow(0xFFFF9EC7, 0.14F));
            }
        }
    }

    private static void starBeetle(Geo geo, int pass, float age, float alpha) {
        float bob = Mth.sin(age * 0.05F) * 0.03F;
        int midnight = tone(0xFF141A3A, alpha);
        int sheen = tone(0xFF2C3A7A, alpha);
        int horn = tone(0xFFD9B44A, alpha);
        if (pass == BODY) {
            blob(geo, BLOB, 0.0F, 1.6F + bob, 0.2F, 1.3F, 1.3F, 1.5F, sheen, midnight);
            blob(geo, SMALL_BLOB, 0.0F, 1.1F + bob, -1.3F, 0.5F, 0.4F, 0.4F, midnight, midnight);
            geo.quad(-0.03F, 2.91F + bob, -0.9F, 0.03F, 2.91F + bob, -0.9F, 0.03F, 2.91F + bob, 1.3F, -0.03F, 2.91F + bob, 1.3F, midnight);
            tube(geo, 0.0F, 1.4F + bob, -1.6F, 0.0F, 2.4F + bob, -1.85F, 0.18F, 0.13F, horn);
            tube(geo, 0.0F, 2.4F + bob, -1.85F, 0.0F, 3.2F + bob, -1.6F, 0.13F, 0.08F, horn);
            tube(geo, 0.0F, 3.2F + bob, -1.6F, 0.0F, 3.55F + bob, -1.2F, 0.08F, 0.03F, horn);
            for (int side = -1; side <= 1; side += 2) {
                for (int legIndex = 0; legIndex < 3; legIndex++) {
                    float z = -0.6F + legIndex * 0.7F;
                    float step = Mth.sin(age * 0.08F + legIndex * 2.0F + side) * 0.08F;
                    tube(geo, side * 0.9F, 0.9F + bob, z, side * 1.5F, 0.5F, z + step, 0.12F, 0.09F, midnight);
                    tube(geo, side * 1.5F, 0.5F, z + step, side * 1.55F, 0.0F, z + step - 0.1F, 0.09F, 0.05F, midnight);
                }
            }
        } else if (pass == EMISSIVE) {
            float[] stars = BEETLE_STAR_POSITIONS;
            for (int i = 0; i < BEETLE_STARS; i++) {
                float twinkle = 0.55F + 0.45F * pulse(age * 0.6F, i * 1.7F);
                CosmeticsLayer.crossDiamond(geo, stars[i * 3], stars[i * 3 + 1] + bob, stars[i * 3 + 2], 0.05F, tone(0xFFFFE9A0, alpha * twinkle));
            }
        } else {
            float[] stars = BEETLE_STAR_POSITIONS;
            for (int i = 0; i < BEETLE_STARS; i += 2) {
                CosmeticsLayer.crossDiamond(geo, stars[i * 3], stars[i * 3 + 1] + bob, stars[i * 3 + 2], 0.14F, CosmeticsLayer.glow(0xFFFFE9A0, 0.14F));
            }
        }
    }

    private static void crystalStag(Geo geo, int pass, float age, float alpha) {
        float graze = Mth.sin(age * 0.02F) * 0.08F;
        int stone = tone(0xFFC9C3D6, alpha);
        int ash = tone(0xFF8E879E, alpha);
        if (pass == BODY) {
            blob(geo, BLOB, 0.0F, 1.5F, 0.2F, 0.5F, 0.5F, 1.0F, stone, ash);
            tube(geo, 0.0F, 1.7F, -0.6F, 0.0F, 2.3F + graze, -0.95F, 0.28F, 0.2F, stone);
            blob(geo, SMALL_BLOB, 0.0F, 2.4F + graze, -1.15F, 0.2F, 0.22F, 0.35F, stone, ash);
            tube(geo, 0.0F, 2.32F + graze, -1.35F, 0.0F, 2.25F + graze, -1.55F, 0.1F, 0.07F, ash);
            for (int side = -1; side <= 1; side += 2) {
                tube(geo, side * 0.3F, 1.2F, -0.5F, side * 0.32F, 0.0F, -0.55F, 0.1F, 0.07F, ash);
                tube(geo, side * 0.3F, 1.2F, 0.9F, side * 0.32F, 0.0F, 0.95F, 0.1F, 0.07F, ash);
            }

            tube(geo, 0.0F, 1.7F, 1.15F, 0.0F, 1.5F, 1.35F, 0.08F, 0.03F, stone);
        } else if (pass == EMISSIVE) {
            int crystal = tone(0xCCB8E6FF, alpha);
            int core = tone(0xFFF2FBFF, alpha);
            for (int side = -1; side <= 1; side += 2) {
                for (int i = 0; i < 4; i++) {
                    float x = side * (0.18F + i * 0.17F);
                    float y = 2.75F + graze + i * 0.2F - (i == 3 ? 0.1F : 0.0F);
                    float z = -1.05F + i * 0.05F;
                    crystal(geo, x, y, z, 0.07F, 0.2F + (i == 2 ? 0.05F : 0.0F), crystal);
                }

                crystal(geo, side * 0.4F, 3.42F + graze, -0.95F, 0.05F, 0.18F, core);
            }

            for (int i = 0; i < 4; i++) {
                crystal(geo, 0.0F, 2.08F, -0.2F + i * 0.3F, 0.05F, 0.12F, crystal);
            }

            geo.cube(0.13F, 2.48F + graze, -1.38F, 0.025F, tone(0xFF5A3FA0, alpha));
            geo.cube(-0.13F, 2.48F + graze, -1.38F, 0.025F, tone(0xFF5A3FA0, alpha));
        } else {
            int glow = CosmeticsLayer.glow(0xFFB8E6FF, 0.16F);
            for (int side = -1; side <= 1; side += 2) {
                CosmeticsLayer.crossDiamond(geo, side * 0.45F, 3.1F + graze, -1.0F, 0.35F, glow);
            }
        }
    }

    private static void duskFox(Geo geo, int pass, float age, float alpha, int lod) {
        float breathe = Mth.sin(age * 0.05F) * 0.03F;
        int dusk = tone(0xFF2A1E3A, alpha);
        int violet = tone(0xFF5B3C8C, alpha);
        int shadow = tone(0xB0140E1E, alpha);
        if (pass == BODY) {
            blob(geo, BLOB, 0.0F, 1.9F + breathe, 0.3F, 0.45F, 0.45F, 0.95F, violet, dusk);
            tube(geo, 0.0F, 2.1F, -0.55F, 0.0F, 2.7F, -0.85F, 0.3F, 0.18F, violet);
            blob(geo, SMALL_BLOB, 0.0F, 2.85F, -1.0F, 0.25F, 0.22F, 0.3F, violet, dusk);
            tube(geo, 0.0F, 2.8F, -1.2F, 0.0F, 2.72F, -1.5F, 0.1F, 0.03F, dusk);
            for (int side = -1; side <= 1; side += 2) {
                geo.tri(side * 0.08F, 3.0F, -0.95F, side * 0.22F, 3.0F, -0.95F, side * 0.18F, 3.6F, -0.85F, dusk);
                tube(geo, side * 0.25F, 1.6F, -0.4F, side * 0.27F, 0.0F, -0.45F, 0.08F, 0.05F, dusk);
                tube(geo, side * 0.25F, 1.6F, 0.95F, side * 0.27F, 0.0F, 1.0F, 0.08F, 0.05F, dusk);
            }

            float prevX = 0.0F;
            float prevY = 2.0F;
            float prevZ = 1.2F;
            int tailSegments = 6 / lod;
            for (int s = 1; s <= tailSegments; s++) {
                float t = (float)s / tailSegments;
                float x = Mth.sin(age * 0.05F + t * 2.0F) * 0.2F * t;
                float y = 2.0F + t * 1.1F - t * t * 0.4F;
                float z = 1.2F + Mth.sin(t * 2.4F) * 0.45F;
                tube(geo, prevX, prevY, prevZ, x, y, z, 0.18F + 0.12F * t, 0.2F + 0.12F * t - 0.25F * t * t, violet);
                prevX = x;
                prevY = y;
                prevZ = z;
            }

            int segments = TENDRIL_SEGMENTS / lod;
            for (int i = 0; i < TENDRILS; i++) {
                float angle = (i - 2) * 0.45F;
                float baseX = Mth.sin(angle) * 0.3F;
                float tx = baseX;
                float ty = 2.3F;
                float tz = 0.1F + i * 0.15F;
                for (int s = 1; s <= segments; s++) {
                    float t = (float)s / segments;
                    float wave = Mth.sin(age * 0.07F + i * 1.3F + t * 3.0F) * 0.25F * t;
                    float nx = baseX * (1.0F + 2.0F * t) + wave;
                    float ny = 2.3F + t * 0.9F;
                    float nz = tz + t * 0.4F;
                    tube(geo, tx, ty, tz, nx, ny, nz, 0.05F * (1.0F - t) + 0.015F, 0.05F * (1.0F - t) + 0.01F, shadow);
                    tx = nx;
                    ty = ny;
                }
            }
        } else if (pass == EMISSIVE) {
            int eye = tone(0xFFFFB13B, alpha);
            geo.cube(0.1F, 2.9F, -1.25F, 0.03F, eye);
            geo.cube(-0.1F, 2.9F, -1.25F, 0.03F, eye);
            int tip = tone(0xFFB57CFF, alpha);
            for (int i = 0; i < TENDRILS; i++) {
                float angle = (i - 2) * 0.45F;
                float wave = Mth.sin(age * 0.07F + i * 1.3F + 3.0F) * 0.25F;
                CosmeticsLayer.crossDiamond(geo, Mth.sin(angle) * 0.9F + wave, 3.2F, 0.5F + i * 0.15F, 0.05F, tip);
            }
        } else {
            for (int i = 0; i < TENDRILS; i++) {
                float angle = (i - 2) * 0.45F;
                float wave = Mth.sin(age * 0.07F + i * 1.3F + 3.0F) * 0.25F;
                CosmeticsLayer.crossDiamond(geo, Mth.sin(angle) * 0.9F + wave, 3.2F, 0.5F + i * 0.15F, 0.14F, CosmeticsLayer.glow(0xFFB57CFF, 0.14F));
            }
        }
    }

    private static float frac(float value) {
        return value - Mth.floor(value);
    }

    private static void blob(Geo geo, float[] table, float cx, float cy, float cz, float rx, float ry, float rz, int top, int bottom) {
        for (int i = 0; i < table.length; i += 12) {
            geo.quad(
                cx + table[i] * rx, cy + table[i + 1] * ry, cz + table[i + 2] * rz,
                cx + table[i + 3] * rx, cy + table[i + 4] * ry, cz + table[i + 5] * rz,
                cx + table[i + 6] * rx, cy + table[i + 7] * ry, cz + table[i + 8] * rz,
                cx + table[i + 9] * rx, cy + table[i + 10] * ry, cz + table[i + 11] * rz,
                Colors.lerp(bottom, top, (table[i + 1] + 1.0F) * 0.5F),
                Colors.lerp(bottom, top, (table[i + 4] + 1.0F) * 0.5F),
                Colors.lerp(bottom, top, (table[i + 7] + 1.0F) * 0.5F),
                Colors.lerp(bottom, top, (table[i + 10] + 1.0F) * 0.5F)
            );
        }
    }

    private static void tube(Geo geo, float ax, float ay, float az, float bx, float by, float bz, float startRadius, float endRadius, int color) {
        float dx = bx - ax;
        float dy = by - ay;
        float dz = bz - az;
        float lengthSquared = dx * dx + dy * dy + dz * dz;
        if (lengthSquared < 1.0E-8F) {
            return;
        }

        float inverse = Mth.invSqrt(lengthSquared);
        dx *= inverse;
        dy *= inverse;
        dz *= inverse;
        boolean vertical = Math.abs(dy) > 0.9F;
        float hx = vertical ? 1.0F : 0.0F;
        float hy = vertical ? 0.0F : 1.0F;
        float ux = dy * 0.0F - dz * hy;
        float uy = dz * hx - dx * 0.0F;
        float uz = dx * hy - dy * hx;
        float uInverse = Mth.invSqrt(Math.max(1.0E-8F, ux * ux + uy * uy + uz * uz));
        ux *= uInverse;
        uy *= uInverse;
        uz *= uInverse;
        float vx = dy * uz - dz * uy;
        float vy = dz * ux - dx * uz;
        float vz = dx * uy - dy * ux;
        for (int k = 0; k < 4; k++) {
            int next = (k + 1) & 3;
            float ox0 = TUBE_U[k] * ux + TUBE_V[k] * vx;
            float oy0 = TUBE_U[k] * uy + TUBE_V[k] * vy;
            float oz0 = TUBE_U[k] * uz + TUBE_V[k] * vz;
            float ox1 = TUBE_U[next] * ux + TUBE_V[next] * vx;
            float oy1 = TUBE_U[next] * uy + TUBE_V[next] * vy;
            float oz1 = TUBE_U[next] * uz + TUBE_V[next] * vz;
            geo.quad(
                ax + ox0 * startRadius, ay + oy0 * startRadius, az + oz0 * startRadius,
                ax + ox1 * startRadius, ay + oy1 * startRadius, az + oz1 * startRadius,
                bx + ox1 * endRadius, by + oy1 * endRadius, bz + oz1 * endRadius,
                bx + ox0 * endRadius, by + oy0 * endRadius, bz + oz0 * endRadius,
                color
            );
        }
    }

    private static void crystal(Geo geo, float cx, float cy, float cz, float radius, float halfHeight, int color) {
        for (int k = 0; k < 4; k++) {
            int next = (k + 1) & 3;
            float x0 = cx + TUBE_U[k] * radius;
            float z0 = cz + TUBE_V[k] * radius;
            float x1 = cx + TUBE_U[next] * radius;
            float z1 = cz + TUBE_V[next] * radius;
            geo.tri(x0, cy, z0, x1, cy, z1, cx, cy + halfHeight, cz, color);
            geo.tri(x1, cy, z1, x0, cy, z0, cx, cy - halfHeight * 0.6F, cz, color);
        }
    }

    private static float[] sphereQuads(int latitudes, int longitudes) {
        float[] out = new float[latitudes * longitudes * 12];
        int index = 0;
        for (int lat = 0; lat < latitudes; lat++) {
            for (int lon = 0; lon < longitudes; lon++) {
                index = spherePoint(out, index, lat, lon, latitudes, longitudes);
                index = spherePoint(out, index, lat, lon + 1, latitudes, longitudes);
                index = spherePoint(out, index, lat + 1, lon + 1, latitudes, longitudes);
                index = spherePoint(out, index, lat + 1, lon, latitudes, longitudes);
            }
        }

        return out;
    }

    private static int spherePoint(float[] out, int index, int lat, int lon, int latitudes, int longitudes) {
        float theta = (float)Math.PI * lat / latitudes;
        float phi = Mth.TWO_PI * lon / longitudes;
        out[index] = Mth.sin(theta) * Mth.cos(phi);
        out[index + 1] = Mth.cos(theta);
        out[index + 2] = Mth.sin(theta) * Mth.sin(phi);
        return index + 3;
    }

    private static float[] beetleStars() {
        float[] out = new float[BEETLE_STARS * 3];
        for (int i = 0; i < BEETLE_STARS; i++) {
            float theta = 0.25F + (i % 4) * 0.28F;
            float phi = Mth.TWO_PI * i / BEETLE_STARS + (i & 1) * 0.4F;
            out[i * 3] = Mth.sin(theta) * Mth.cos(phi) * 1.32F;
            out[i * 3 + 1] = 1.6F + Mth.cos(theta) * 1.32F;
            out[i * 3 + 2] = 0.2F + Mth.sin(theta) * Mth.sin(phi) * 1.52F;
        }

        return out;
    }
}
