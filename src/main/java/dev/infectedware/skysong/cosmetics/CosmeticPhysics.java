package dev.infectedware.skysong.cosmetics;

import dev.infectedware.skysong.ModSettings;
import java.util.Arrays;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;

final class CosmeticPhysics {
    static final int MAX_COLUMNS = 12;
    static final int MAX_ROWS = 16;
    private static final int MAX_POINTS = MAX_COLUMNS * MAX_ROWS;
    private static final int[] QUALITY_COLUMNS = new int[]{6, 9, 12};
    private static final int[] QUALITY_ROWS = new int[]{8, 12, 16};
    private static final int[] CAPELET_QUALITY_ROWS = new int[]{3, 4, 5};
    private static final int RIBBON_COLUMNS = 3;
    private static final double TELEPORT_DISTANCE_SQUARED = 64.0;
    private static final float MODEL_SCALE = 0.9375F;
    private static final float MAX_FRAME_STEP = 1.5F;
    private static final float CROUCH_TILT = 0.5F;
    private static final float REST_TILT = 0.1F;
    private static final int ITERATIONS = 6;
    private static final float GRAVITY = 0.035F;
    private static final float DRAG = 0.05F;
    private static final float WIND_SCALE = 0.4F;
    private static final float SHARD_HALF = 0.25F;
    private static final float SHARD_FLARE = 0.05F;
    private static final float SHARD_LENGTH = 0.95F;
    private static final float BANNER_HALF = 0.25F;
    private static final float BANNER_FLARE = 0.06F;
    private static final float BANNER_LENGTH = 0.98F;
    private static final float CAPELET_HALF = 0.28F;
    private static final float CAPELET_FLARE = 0.05F;
    private static final float CAPELET_LENGTH = 0.36F;
    private static final int CAPSULES = 5;
    private static final float[] TORSO_X = new float[]{-0.14F, 0.0F, 0.14F};
    private static final float TORSO_RADIUS = 0.14F;
    private static final float LEG_X = 0.119F;
    private static final float LEG_RADIUS = 0.13F;
    private static final float LEG_LENGTH = 0.7F;
    private static final float[] CAPSULE = new float[CAPSULES * 7];
    private static final float[] CLOTH = new float[MAX_POINTS * 3];
    private static final float[] CLOTH_PREVIOUS = new float[MAX_POINTS * 3];
    private static final float[] CLOTH_REST = new float[MAX_POINTS * 3];
    private static final float[] CLOTH_LAST = new float[MAX_POINTS * 3];
    private static final float[] CLOTH_SHOWN = new float[MAX_POINTS * 3];
    private static final float[] COLUMN_REST = new float[MAX_COLUMNS];
    private static final float[] ROW_REST = new float[MAX_ROWS];
    static final int WING_SPREAD = 0;
    static final int WING_BEAT = 1;
    static final int HALO_X = 2;
    static final int HALO_Z = 3;
    static final int HALO_PITCH = 4;
    static final int HALO_ROLL = 5;
    static final int HALO_WOBBLE = 6;
    static final int HAT_PITCH = 7;
    static final int HAT_ROLL = 8;
    static final int PET_OFFSET = 9;
    private static final int SPRINGS = PET_OFFSET + Cosmetics.MAX_PETS * 3;
    private static final float[] SPRING = new float[SPRINGS * 2];
    private static final float[] SPRING_LAST = new float[SPRINGS];
    private static final float MAX_PET_DRIFT = 1.5F / MODEL_SCALE;
    private static final float MAX_HALO_DRIFT = 0.2F;
    static final int CHAIN_SLOT_POINTS = 40;
    static final int TENTACLE_POINTS = 7;
    static final int LANTERN_POINTS = 5;
    static final int TAIL_POINTS = 5;
    private static final int CHAIN_NONE = 0;
    private static final int CHAIN_TENTACLES = 1;
    private static final int CHAIN_LANTERN = 2;
    private static final int CHAIN_TAIL = 3;
    private static final int CHAIN_ITERATIONS = 4;
    private static final float[] CHAIN = new float[Cosmetics.MAX_PETS * CHAIN_SLOT_POINTS * 3];
    private static final float[] CHAIN_PREVIOUS = new float[Cosmetics.MAX_PETS * CHAIN_SLOT_POINTS * 3];
    private static final float[] CHAIN_LAST = new float[Cosmetics.MAX_PETS * CHAIN_SLOT_POINTS * 3];
    private static final float[] CHAIN_SHOWN = new float[Cosmetics.MAX_PETS * CHAIN_SLOT_POINTS * 3];
    private static final float[] ANCHOR = new float[3];
    private static final int[] CHAIN_TYPE = new int[Cosmetics.MAX_PETS];
    private static final int[] SLOT_TYPES = new int[Cosmetics.MAX_PETS];
    static float capeOffsetY;
    static float capeOffsetZ = 0.135F;
    static float frameX;
    static float frameY;
    static float frameZ;
    static float frameTurn;
    private static int clothColumns;
    private static int clothRows;
    private static int clothStyle = -1;
    private static int clothQuality = -1;
    private static int capsuleCount;
    private static boolean clothReady;
    private static boolean tracking;
    private static LocalPlayer lastPlayer;
    private static ClientLevel lastLevel;
    private static double lastX;
    private static double lastY;
    private static double lastZ;
    private static float lastYaw;
    private static float lastHeadYaw;
    private static float lastPitch;
    private static float lastVerticalSpeed;
    private static boolean lastOnGround = true;

    private CosmeticPhysics() {
    }

    static void tick(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            clear();
            return;
        }

        double x = player.getX();
        double y = player.getY();
        double z = player.getZ();
        float yaw = player.yBodyRot;
        boolean jump = !tracking || player != lastPlayer || mc.level != lastLevel || player.isDeadOrDying();
        double dx = x - lastX;
        double dy = y - lastY;
        double dz = z - lastZ;
        if (!jump && dx * dx + dy * dy + dz * dz > TELEPORT_DISTANCE_SQUARED) {
            jump = true;
        }

        if (jump) {
            frameX = 0.0F;
            frameY = 0.0F;
            frameZ = 0.0F;
            frameTurn = 0.0F;
        } else {
            float radians = yaw * Mth.DEG_TO_RAD;
            float cos = Mth.cos(radians);
            float sin = Mth.sin(radians);
            float stepX = (float)(dx * cos + dz * sin) / MODEL_SCALE;
            float stepY = (float)-dy / MODEL_SCALE;
            float stepZ = (float)(dx * sin - dz * cos) / MODEL_SCALE;
            float length = (float)Math.sqrt(stepX * stepX + stepY * stepY + stepZ * stepZ);
            float limit = length > MAX_FRAME_STEP ? MAX_FRAME_STEP / length : 1.0F;
            frameX = stepX * limit;
            frameY = stepY * limit;
            frameZ = stepZ * limit;
            frameTurn = Mth.wrapDegrees(yaw - lastYaw) * Mth.DEG_TO_RAD;
        }

        tickSprings(player, jump);
        tickPets(player, jump);
        GiantPets.tick(mc, player, jump);
        lastHeadYaw = player.yHeadRot;
        lastPitch = player.getXRot();
        lastVerticalSpeed = (float)player.getDeltaMovement().y;
        lastOnGround = player.onGround();
        lastPlayer = player;
        lastLevel = mc.level;
        lastX = x;
        lastY = y;
        lastZ = z;
        lastYaw = yaw;
        tracking = true;
        tickCloth(player, jump);
    }

    static void clear() {
        GiantPets.clear();
        Arrays.fill(CLOTH, 0.0F);
        Arrays.fill(CLOTH_PREVIOUS, 0.0F);
        Arrays.fill(CLOTH_REST, 0.0F);
        Arrays.fill(CLOTH_LAST, 0.0F);
        Arrays.fill(CLOTH_SHOWN, 0.0F);
        Arrays.fill(COLUMN_REST, 0.0F);
        Arrays.fill(ROW_REST, 0.0F);
        Arrays.fill(CAPSULE, 0.0F);
        Arrays.fill(SPRING, 0.0F);
        Arrays.fill(SPRING_LAST, 0.0F);
        SPRING[WING_BEAT * 2] = 1.0F;
        SPRING_LAST[WING_BEAT] = 1.0F;
        Arrays.fill(CHAIN, 0.0F);
        Arrays.fill(CHAIN_PREVIOUS, 0.0F);
        Arrays.fill(CHAIN_LAST, 0.0F);
        Arrays.fill(CHAIN_SHOWN, 0.0F);
        Arrays.fill(CHAIN_TYPE, -1);
        Arrays.fill(SLOT_TYPES, -1);
        lastOnGround = true;
        lastVerticalSpeed = 0.0F;
        clothReady = false;
        clothStyle = -1;
        clothQuality = -1;
        tracking = false;
        lastPlayer = null;
        lastLevel = null;
        frameX = 0.0F;
        frameY = 0.0F;
        frameZ = 0.0F;
        frameTurn = 0.0F;
    }

    private static void tickCloth(LocalPlayer player, boolean jump) {
        if (!ModSettings.cosmeticCape || !ModSettings.cosmeticCapePhysics || !Cosmetics.shapedCape()) {
            clothReady = false;
            clothStyle = -1;
            return;
        }

        int style = ModSettings.cosmeticCapeStyle;
        int quality = Cosmetics.capePhysicsQuality();
        if (jump || !clothReady || style != clothStyle || quality != clothQuality) {
            layoutCloth(style, quality);
            return;
        }

        stepCloth(player, style);
    }

    private static void layoutCloth(int style, int quality) {
        boolean ribbons = style == Cosmetics.RIBBON_CAPE;
        clothColumns = ribbons ? RIBBON_COLUMNS : QUALITY_COLUMNS[quality];
        clothRows = style == Cosmetics.CAPELET_CAPE ? CAPELET_QUALITY_ROWS[quality] : QUALITY_ROWS[quality];
        clothStyle = style;
        clothQuality = quality;
        float tiltCos = Mth.cos(REST_TILT);
        float tiltSin = Mth.sin(REST_TILT);
        for (int row = 0; row < clothRows; row++) {
            float v = (float)row / (clothRows - 1);
            float half = halfWidth(style, v);
            ROW_REST[row] = ribbons ? 0.0F : half * 2.0F / (clothColumns - 1);
            for (int column = 0; column < clothColumns; column++) {
                float length = ribbons ? CosmeticsLayer.RIBBON_LENGTH[column] : length(style);
                float x = ribbons ? CosmeticsLayer.RIBBON_X[column] : ((float)column / (clothColumns - 1) * 2.0F - 1.0F) * half;
                float depth = ribbons && column == 1 ? 0.012F : 0.0F;
                int i = (row * clothColumns + column) * 3;
                CLOTH_REST[i] = x;
                CLOTH_REST[i + 1] = v * length * tiltCos;
                CLOTH_REST[i + 2] = depth + v * length * tiltSin;
                if (row == 0) {
                    COLUMN_REST[column] = length / (clothRows - 1);
                }
            }
        }

        int used = clothColumns * clothRows * 3;
        System.arraycopy(CLOTH_REST, 0, CLOTH, 0, used);
        System.arraycopy(CLOTH_REST, 0, CLOTH_PREVIOUS, 0, used);
        System.arraycopy(CLOTH_REST, 0, CLOTH_LAST, 0, used);
        System.arraycopy(CLOTH_REST, 0, CLOTH_SHOWN, 0, used);
        clothReady = true;
    }

    private static float halfWidth(int style, float v) {
        return switch (style) {
            case Cosmetics.SHARD_CAPE -> SHARD_HALF + SHARD_FLARE * v;
            case Cosmetics.CAPELET_CAPE -> CAPELET_HALF + CAPELET_FLARE * v;
            default -> BANNER_HALF + BANNER_FLARE * v;
        };
    }

    private static float length(int style) {
        return switch (style) {
            case Cosmetics.SHARD_CAPE -> SHARD_LENGTH;
            case Cosmetics.CAPELET_CAPE -> CAPELET_LENGTH;
            default -> BANNER_LENGTH;
        };
    }

    private static void stepCloth(LocalPlayer player, int style) {
        int columns = clothColumns;
        int points = columns * clothRows;
        System.arraycopy(CLOTH, 0, CLOTH_LAST, 0, points * 3);
        float wind = Cosmetics.capeWind() * WIND_SCALE;
        float stiffness = Cosmetics.capeStiffness();
        boolean capelet = style == Cosmetics.CAPELET_CAPE;
        boolean ribbons = style == Cosmetics.RIBBON_CAPE;
        float turn = frameTurn * wind;
        Motion.shift(CLOTH, CLOTH_PREVIOUS, columns, points, 0.0F, -capeOffsetZ, Mth.cos(turn), Mth.sin(turn), frameX * wind, frameY * wind, frameZ * wind);
        boolean crouching = player.isCrouching();
        float tilt = crouching ? CROUCH_TILT : 0.0F;
        Motion.integrate(CLOTH, CLOTH_PREVIOUS, columns, points, 0.0F, GRAVITY * Mth.cos(tilt), -GRAVITY * Mth.sin(tilt), DRAG);
        layoutCapsules(player, crouching);
        float linkStiffness = capelet ? 1.0F : 0.6F + 0.4F * stiffness;
        float pull = capelet ? 0.08F + 0.4F * stiffness : 0.01F + 0.12F * stiffness;
        for (int pass = 0; pass < ITERATIONS; pass++) {
            for (int row = 1; row < clothRows; row++) {
                for (int column = 0; column < columns; column++) {
                    int point = row * columns + column;
                    Motion.link(CLOTH, point - columns, point, COLUMN_REST[column], linkStiffness, row == 1);
                    if (!ribbons && column > 0) {
                        Motion.link(CLOTH, point - 1, point, ROW_REST[row], linkStiffness, false);
                    }

                    if (capelet && row > 1) {
                        Motion.link(CLOTH, point - columns * 2, point, COLUMN_REST[column] * 2.0F, linkStiffness, row == 2);
                    }

                    if (capelet && column > 1) {
                        Motion.link(CLOTH, point - 2, point, ROW_REST[row] * 2.0F, linkStiffness, false);
                    }
                }
            }

            for (int point = columns; point < points; point++) {
                for (int capsule = 0; capsule < capsuleCount; capsule++) {
                    int c = capsule * 7;
                    Motion.capsule(CLOTH, point, CAPSULE[c], CAPSULE[c + 1], CAPSULE[c + 2], CAPSULE[c + 3], CAPSULE[c + 4], CAPSULE[c + 5], CAPSULE[c + 6]);
                }
            }
        }

        for (int i = columns * 3; i < points * 3; i++) {
            CLOTH[i] += (CLOTH_REST[i] - CLOTH[i]) * pull;
        }
    }

    private static void layoutCapsules(LocalPlayer player, boolean crouching) {
        float y = -capeOffsetY;
        float z = -capeOffsetZ;
        int count = 0;
        for (float x : TORSO_X) {
            count = capsule(count, x, y + 0.06F, z, x, y + 0.7F, z, TORSO_RADIUS);
        }

        if (!crouching) {
            float speed = Math.min(1.0F, player.walkAnimation.speed());
            float swing = Mth.cos(player.walkAnimation.position() * 0.6662F) * 1.4F * speed;
            count = capsule(count, -LEG_X, y + 0.75F, z, -LEG_X, y + 0.75F + LEG_LENGTH * Mth.cos(swing), z + LEG_LENGTH * Mth.sin(swing), LEG_RADIUS);
            count = capsule(count, LEG_X, y + 0.75F, z, LEG_X, y + 0.75F + LEG_LENGTH * Mth.cos(-swing), z + LEG_LENGTH * Mth.sin(-swing), LEG_RADIUS);
        }

        capsuleCount = count;
    }

    private static int capsule(int index, float ax, float ay, float az, float bx, float by, float bz, float radius) {
        int c = index * 7;
        CAPSULE[c] = ax;
        CAPSULE[c + 1] = ay;
        CAPSULE[c + 2] = az;
        CAPSULE[c + 3] = bx;
        CAPSULE[c + 4] = by;
        CAPSULE[c + 5] = bz;
        CAPSULE[c + 6] = radius;
        return index + 1;
    }

    static boolean clothShown(float partialTick) {
        if (!clothReady || !ModSettings.cosmeticCapePhysics || ModSettings.cosmeticCapeStyle != clothStyle) {
            return false;
        }

        float t = Math.clamp(partialTick, 0.0F, 1.0F);
        for (int i = 0; i < clothColumns * clothRows * 3; i++) {
            CLOTH_SHOWN[i] = Mth.lerp(t, CLOTH_LAST[i], CLOTH[i]);
        }

        return true;
    }

    static void ribbonPoint(int column, float t, float[] out) {
        float f = Math.clamp(t, 0.0F, 1.0F) * (clothRows - 1);
        int row = Math.min((int)f, clothRows - 2);
        float s = f - row;
        int a = (row * clothColumns + column) * 3;
        int b = a + clothColumns * 3;
        out[0] = Mth.lerp(s, CLOTH_SHOWN[a], CLOTH_SHOWN[b]);
        out[1] = Mth.lerp(s, CLOTH_SHOWN[a + 1], CLOTH_SHOWN[b + 1]);
        out[2] = Mth.lerp(s, CLOTH_SHOWN[a + 2], CLOTH_SHOWN[b + 2]);
    }

    static void sample(float u, float v, float[] out) {
        int columns = clothColumns;
        float fu = Math.clamp(u, 0.0F, 1.0F) * (columns - 1);
        float fv = Math.clamp(v, 0.0F, 1.0F) * (clothRows - 1);
        int column = Math.min((int)fu, columns - 2);
        int row = Math.min((int)fv, clothRows - 2);
        float su = fu - column;
        float sv = fv - row;
        int i00 = (row * columns + column) * 3;
        int i10 = i00 + 3;
        int i01 = i00 + columns * 3;
        int i11 = i01 + 3;
        float[] shown = CLOTH_SHOWN;
        for (int axis = 0; axis < 3; axis++) {
            float top = Mth.lerp(su, shown[i00 + axis], shown[i10 + axis]);
            float bottom = Mth.lerp(su, shown[i01 + axis], shown[i11 + axis]);
            out[axis] = Mth.lerp(sv, top, bottom);
            out[3 + axis] = Mth.lerp(sv, shown[i10 + axis] - shown[i00 + axis], shown[i11 + axis] - shown[i01 + axis]) * (columns - 1);
            out[6 + axis] = Mth.lerp(su, shown[i01 + axis] - shown[i00 + axis], shown[i11 + axis] - shown[i10 + axis]) * (clothRows - 1);
        }
    }

    static float spring(int index, float partialTick) {
        return Mth.lerp(Math.clamp(partialTick, 0.0F, 1.0F), SPRING_LAST[index], SPRING[index * 2]);
    }

    private static void resetSpring(int index, float value) {
        SPRING[index * 2] = value;
        SPRING[index * 2 + 1] = 0.0F;
        SPRING_LAST[index] = value;
    }

    private static void tickSprings(LocalPlayer player, boolean jump) {
        for (int i = 0; i < SPRINGS; i++) {
            SPRING_LAST[i] = SPRING[i * 2];
        }

        if (jump) {
            for (int i = 0; i < SPRINGS; i++) {
                resetSpring(i, i == WING_BEAT ? 1.0F : 0.0F);
            }

            return;
        }

        float verticalSpeed = (float)player.getDeltaMovement().y;
        boolean onGround = player.onGround();
        float impact = onGround && !lastOnGround && lastVerticalSpeed < -0.2F ? Math.min(1.0F, -lastVerticalSpeed) : 0.0F;
        boolean leapt = !onGround && lastOnGround && verticalSpeed > 0.25F;
        float headTurn = Math.clamp(Mth.wrapDegrees(player.yHeadRot - lastHeadYaw), -45.0F, 45.0F);
        float headNod = Math.clamp(player.getXRot() - lastPitch, -45.0F, 45.0F);
        boolean sprinting = player.isSprinting();
        if (ModSettings.cosmeticWingsPhysics) {
            SPRING[WING_SPREAD * 2 + 1] += impact * 18.0F;
            SPRING[WING_BEAT * 2 + 1] += impact * 0.6F;
            Motion.spring(SPRING, WING_SPREAD * 2, Math.clamp(-verticalSpeed * 45.0F, -20.0F, 25.0F) - (sprinting ? 10.0F : 0.0F), 0.18F, 0.22F);
            Motion.spring(SPRING, WING_BEAT * 2, 1.0F + (sprinting ? 0.45F : 0.0F) + Math.min(0.6F, Math.abs(verticalSpeed) * 1.5F), 0.2F, 0.3F);
            SPRING[WING_SPREAD * 2] = Math.clamp(SPRING[WING_SPREAD * 2], -40.0F, 40.0F);
            SPRING[WING_BEAT * 2] = Math.clamp(SPRING[WING_BEAT * 2], 0.2F, 2.5F);
        } else {
            resetSpring(WING_SPREAD, 0.0F);
            resetSpring(WING_BEAT, 1.0F);
        }

        if (ModSettings.cosmeticHaloPhysics) {
            SPRING[HALO_X * 2] = Math.clamp(SPRING[HALO_X * 2] - frameX * 0.5F, -MAX_HALO_DRIFT, MAX_HALO_DRIFT);
            SPRING[HALO_Z * 2] = Math.clamp(SPRING[HALO_Z * 2] - frameZ * 0.5F, -MAX_HALO_DRIFT, MAX_HALO_DRIFT);
            SPRING[HALO_PITCH * 2] = Math.clamp(SPRING[HALO_PITCH * 2] - headNod * 0.5F, -25.0F, 25.0F);
            SPRING[HALO_ROLL * 2] = Math.clamp(SPRING[HALO_ROLL * 2] + headTurn * 0.3F, -25.0F, 25.0F);
            Motion.critical(SPRING, HALO_X * 2, 0.0F, 2.5F);
            Motion.critical(SPRING, HALO_Z * 2, 0.0F, 2.5F);
            Motion.critical(SPRING, HALO_PITCH * 2, 0.0F, 3.0F);
            Motion.critical(SPRING, HALO_ROLL * 2, 0.0F, 3.0F);
            SPRING[HALO_WOBBLE * 2 + 1] += impact * 14.0F;
            Motion.spring(SPRING, HALO_WOBBLE * 2, 0.0F, 0.3F, 0.12F);
            SPRING[HALO_WOBBLE * 2] = Math.clamp(SPRING[HALO_WOBBLE * 2], -25.0F, 25.0F);
        } else {
            for (int i = HALO_X; i <= HALO_WOBBLE; i++) {
                resetSpring(i, 0.0F);
            }
        }

        if (ModSettings.cosmeticHatPhysics) {
            SPRING[HAT_PITCH * 2 + 1] += -headNod * 0.25F - frameZ * 20.0F - (leapt ? 6.0F : 0.0F) + impact * 8.0F;
            SPRING[HAT_ROLL * 2 + 1] += headTurn * 0.2F + frameX * 20.0F;
            Motion.spring(SPRING, HAT_PITCH * 2, 0.0F, 0.22F, 0.16F);
            Motion.spring(SPRING, HAT_ROLL * 2, 0.0F, 0.22F, 0.16F);
            SPRING[HAT_PITCH * 2] = Math.clamp(SPRING[HAT_PITCH * 2], -30.0F, 30.0F);
            SPRING[HAT_ROLL * 2] = Math.clamp(SPRING[HAT_ROLL * 2], -30.0F, 30.0F);
        } else {
            resetSpring(HAT_PITCH, 0.0F);
            resetSpring(HAT_ROLL, 0.0F);
        }
    }

    static int slotType(int slot) {
        return SLOT_TYPES[slot];
    }

    private static void tickPets(LocalPlayer player, boolean jump) {
        int count = ModSettings.cosmeticPet ? CosmeticsLayer.petTypes(SLOT_TYPES) : 0;
        for (int slot = count; slot < Cosmetics.MAX_PETS; slot++) {
            SLOT_TYPES[slot] = -1;
        }

        boolean physics = ModSettings.cosmeticPetPhysics && count > 0;
        float side = ModSettings.cosmeticPetSide == 1 ? 1.0F : -1.0F;
        float size = Cosmetics.petSize();
        float cos = Mth.cos(frameTurn);
        float sin = Mth.sin(frameTurn);
        float age = player.tickCount;
        for (int slot = 0; slot < Cosmetics.MAX_PETS; slot++) {
            int offset = PET_OFFSET + slot * 3;
            if (!physics || jump || SLOT_TYPES[slot] < 0) {
                resetSpring(offset, 0.0F);
                resetSpring(offset + 1, 0.0F);
                resetSpring(offset + 2, 0.0F);
                CHAIN_TYPE[slot] = -1;
                continue;
            }

            float[] anchor = CosmeticsLayer.PET_SLOTS[slot];
            float baseX = side * anchor[0];
            float baseZ = anchor[2];
            float oldX = SPRING[offset * 2];
            float oldY = SPRING[(offset + 1) * 2];
            float oldZ = SPRING[(offset + 2) * 2];
            float worldX = baseX + oldX;
            float worldZ = baseZ + oldZ;
            SPRING[offset * 2] = worldX * cos - worldZ * sin - frameX - baseX;
            SPRING[(offset + 1) * 2] = oldY - frameY;
            SPRING[(offset + 2) * 2] = worldX * sin + worldZ * cos - frameZ - baseZ;
            Motion.critical(SPRING, offset * 2, 0.0F, 4.0F);
            Motion.critical(SPRING, (offset + 1) * 2, 0.0F, 4.0F);
            Motion.critical(SPRING, (offset + 2) * 2, 0.0F, 4.0F);
            float x = SPRING[offset * 2];
            float y = SPRING[(offset + 1) * 2];
            float z = SPRING[(offset + 2) * 2];
            float drift = (float)Math.sqrt(x * x + y * y + z * z);
            if (drift > MAX_PET_DRIFT) {
                float scale = MAX_PET_DRIFT / drift;
                SPRING[offset * 2] = x * scale;
                SPRING[(offset + 1) * 2] = y * scale;
                SPRING[(offset + 2) * 2] = z * scale;
            }

            float shiftX = -(worldX * cos - worldZ * sin - frameX - baseX - SPRING[offset * 2]) / size;
            float shiftY = -(oldY - frameY - SPRING[(offset + 1) * 2]) / size;
            float shiftZ = -(worldX * sin + worldZ * cos - frameZ - baseZ - SPRING[(offset + 2) * 2]) / size;
            tickChains(slot, SLOT_TYPES[slot], age, cos, sin, shiftX, shiftY, shiftZ);
        }
    }

    private static int chainKind(int type) {
        return switch (type) {
            case Cosmetics.JELLYFISH_PET -> CHAIN_TENTACLES;
            case Cosmetics.LANTERN_PET -> CHAIN_LANTERN;
            case Cosmetics.KOI_PET -> CHAIN_TAIL;
            default -> CHAIN_NONE;
        };
    }

    private static void tickChains(int slot, int type, float age, float cos, float sin, float shiftX, float shiftY, float shiftZ) {
        int kind = chainKind(type);
        int first = slot * CHAIN_SLOT_POINTS;
        if (kind == CHAIN_NONE) {
            CHAIN_TYPE[slot] = type;
            return;
        }

        int strands = kind == CHAIN_TENTACLES ? CosmeticsLayer.TENTACLES : 1;
        int points = kind == CHAIN_TENTACLES ? TENTACLE_POINTS : kind == CHAIN_LANTERN ? LANTERN_POINTS : TAIL_POINTS;
        float rest = kind == CHAIN_TENTACLES ? 0.064F : kind == CHAIN_LANTERN ? 0.025F : 0.02F;
        float gravity = kind == CHAIN_TENTACLES ? 0.004F : kind == CHAIN_LANTERN ? 0.02F : 0.0F;
        float drag = kind == CHAIN_TENTACLES ? 0.12F : kind == CHAIN_LANTERN ? 0.04F : 0.15F;
        boolean fresh = CHAIN_TYPE[slot] != type;
        CHAIN_TYPE[slot] = type;
        System.arraycopy(CHAIN, first * 3, CHAIN_LAST, first * 3, strands * points * 3);
        if (!fresh) {
            Motion.shift(CHAIN, CHAIN_PREVIOUS, first, first + strands * points, 0.0F, 0.0F, cos, sin, shiftX, shiftY, shiftZ);
        }

        for (int strand = 0; strand < strands; strand++) {
            CosmeticsLayer.chainAnchor(type, strand, age, ANCHOR);
            int start = first + strand * points;
            if (fresh) {
                for (int k = 0; k < points; k++) {
                    int i = (start + k) * 3;
                    CHAIN[i] = ANCHOR[0];
                    CHAIN[i + 1] = ANCHOR[1] + (kind == CHAIN_TAIL ? 0.0F : k * rest);
                    CHAIN[i + 2] = ANCHOR[2] - (kind == CHAIN_TAIL ? k * rest : 0.0F);
                    CHAIN_PREVIOUS[i] = CHAIN[i];
                    CHAIN_PREVIOUS[i + 1] = CHAIN[i + 1];
                    CHAIN_PREVIOUS[i + 2] = CHAIN[i + 2];
                    CHAIN_LAST[i] = CHAIN[i];
                    CHAIN_LAST[i + 1] = CHAIN[i + 1];
                    CHAIN_LAST[i + 2] = CHAIN[i + 2];
                }
            } else {
                Motion.chain(CHAIN, CHAIN_PREVIOUS, start, points, ANCHOR[0], ANCHOR[1], ANCHOR[2], rest, gravity, drag, CHAIN_ITERATIONS);
            }
        }
    }

    static boolean chainShown(int slot, int type, float partialTick) {
        if (!ModSettings.cosmeticPetPhysics || CHAIN_TYPE[slot] != type || chainKind(type) == CHAIN_NONE) {
            return false;
        }

        float t = Math.clamp(partialTick, 0.0F, 1.0F);
        int from = slot * CHAIN_SLOT_POINTS * 3;
        int to = from + CHAIN_SLOT_POINTS * 3;
        for (int i = from; i < to; i++) {
            CHAIN_SHOWN[i] = Mth.lerp(t, CHAIN_LAST[i], CHAIN[i]);
        }

        return true;
    }

    static float chainPoint(int slot, int point, int axis) {
        return CHAIN_SHOWN[(slot * CHAIN_SLOT_POINTS + point) * 3 + axis];
    }
}
