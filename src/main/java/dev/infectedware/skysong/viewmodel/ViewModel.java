package dev.infectedware.skysong.viewmodel;

import net.minecraft.util.Mth;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.infectedware.skysong.ModConfig;
import dev.infectedware.skysong.ModSettings;
import dev.infectedware.skysong.compat.Compat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.joml.Vector3fc;
import org.joml.Vector4f;

public final class ViewModel {
    static final ViewModel.HandState MAIN = new ViewModel.HandState();
    static final ViewModel.HandState OFF = new ViewModel.HandState();
    private static long lastNanos;
    private static HumanoidArm lastMainArm;
    static InteractionHand held;

    private ViewModel() {
    }

    public static boolean enabled() {
        return ModSettings.viewModelEnabled;
    }

    public static boolean editing() {
        return Minecraft.getInstance().screen instanceof ViewModelEditScreen;
    }

    public static boolean suppressEatAnimation() {
        return enabled() && ModSettings.viewModelNoEat;
    }

    static ViewModel.HandState state(InteractionHand hand) {
        return hand == InteractionHand.MAIN_HAND ? MAIN : OFF;
    }

    public static void beginHandFrame() {
        MAIN.pivotValid = OFF.pivotValid = false;
        MAIN.outerValid = OFF.outerValid = false;
        handleMainArmSwap();
        long now = System.nanoTime();
        float dt = lastNanos == 0L ? 1.0F : Math.min(0.1F, (float)(now - lastNanos) / 1.0E9F);
        lastNanos = now;
        float k = (float)(1.0 - Math.exp(-dt * 16.0));
        ease(MAIN, InteractionHand.MAIN_HAND, k);
        ease(OFF, InteractionHand.OFF_HAND, k);
    }

    private static void ease(ViewModel.HandState s, InteractionHand hand, float k) {
        float[] t = targets(hand);
        float kk = held == hand ? 1.0F : k;
        s.x = s.x + (t[0] - s.x) * kk;
        s.y = s.y + (t[1] - s.y) * kk;
        s.z = s.z + (t[2] - s.z) * kk;
        s.pitch = s.pitch + (t[3] - s.pitch) * kk;
        s.yaw = s.yaw + (t[4] - s.yaw) * kk;
        s.roll = s.roll + (t[5] - s.roll) * kk;
        s.scale = s.scale + (t[6] - s.scale) * k;
    }

    public static void applyOffset(PoseStack matrices, InteractionHand hand) {
        if (enabled() && hand != null) {
            ViewModel.HandState s = state(hand);
            s.outer.set(Compat.handProjection).mul(RenderSystem.getModelViewMatrix()).mul(matrices.last().pose());
            s.outerValid = true;
            matrices.translate(s.x, s.y, s.z);
        }
    }

    public static void applyItemTransform(PoseStack matrices, InteractionHand hand, float cx, float cy, float cz) {
        if (enabled() && hand != null) {
            ViewModel.HandState s = state(hand);
            matrices.translate(cx, cy, cz);
            capturePivot(s, matrices);
            if (s.yaw != 0.0F) {
                matrices.mulPose(Axis.YP.rotationDegrees(s.yaw));
            }

            if (s.pitch != 0.0F) {
                matrices.mulPose(Axis.XP.rotationDegrees(s.pitch));
            }

            if (s.roll != 0.0F) {
                matrices.mulPose(Axis.ZP.rotationDegrees(s.roll));
            }

            if (s.scale != 1.0F) {
                matrices.scale(s.scale, s.scale, s.scale);
            }

            matrices.translate(-cx, -cy, -cz);
        }
    }

    public static float[] itemCenter(LivingEntity holder, InteractionHand hand, ItemStack stack) {
        if (enabled() && stack != null && !stack.isEmpty()) {
            try {
                Minecraft mc = Minecraft.getInstance();
                HumanoidArm arm = hand == InteractionHand.MAIN_HAND ? holder.getMainArm() : holder.getMainArm().getOpposite();
                boolean left = arm == HumanoidArm.LEFT;
                ItemDisplayContext mode = left ? ItemDisplayContext.FIRST_PERSON_LEFT_HAND : ItemDisplayContext.FIRST_PERSON_RIGHT_HAND;
                ItemStackRenderState state = new ItemStackRenderState();
                mc.getItemModelResolver().updateForLiving(state, stack, mode, holder);
                if (state.activeLayerCount == 0) {
                    return new float[3];
                }

                Vector3fc tr = state.layers[0].transform.translation();
                return new float[]{left ? -tr.x() : tr.x(), tr.y(), tr.z()};
            } catch (RuntimeException e) {
                return new float[3];
            }
        } else {
            return new float[3];
        }
    }

    private static void capturePivot(ViewModel.HandState s, PoseStack matrices) {
        if (s.outerValid) {
            Matrix4f full = new Matrix4f(Compat.handProjection).mul(RenderSystem.getModelViewMatrix()).mul(matrices.last().pose());
            float[] center = project(full, 0.0F, 0.0F, 0.0F);
            float[] side = project(full, 0.5F, 0.0F, 0.0F);
            if (center != null) {
                s.screenX = center[0];
                s.screenY = center[1];
                s.screenSize = side == null ? 30.0F : Math.max(12.0F, (float)Math.hypot(side[0] - center[0], side[1] - center[1]) * 2.0F);
                Matrix4f outerInverse = new Matrix4f(s.outer).invert();
                Vector4f clip = full.transform(new Vector4f(0.0F, 0.0F, 0.0F, 1.0F));
                Vector4f local = outerInverse.transform(clip);
                if (!(Math.abs(local.w) < 1.0E-6F)) {
                    s.pivotLocal.set(local.x / local.w - s.x, local.y / local.w - s.y, local.z / local.w - s.z, 1.0F);
                    s.pivotValid = true;
                }
            }
        }
    }

    static float[] project(Matrix4f mvp, float x, float y, float z) {
        Vector4f v = mvp.transform(new Vector4f(x, y, z, 1.0F));
        if (v.w < 1.0E-5F) {
            return null;
        }

        Minecraft mc = Minecraft.getInstance();
        float gw = mc.getWindow().getGuiScaledWidth();
        float gh = mc.getWindow().getGuiScaledHeight();
        return new float[]{(v.x / v.w * 0.5F + 0.5F) * gw, (1.0F - (v.y / v.w * 0.5F + 0.5F)) * gh};
    }

    static float[] pivotOnScreen(ViewModel.HandState s, float ox, float oy, float oz) {
        return !s.pivotValid ? null : project(s.outer, s.pivotLocal.x + ox, s.pivotLocal.y + oy, s.pivotLocal.z + oz);
    }

    static float[] solveOffsetFor(InteractionHand hand, float gx, float gy) {
        ViewModel.HandState s = state(hand);
        float[] t = targets(hand);
        float ox = t[0];
        float oy = t[1];
        float oz = t[2];
        if (!s.pivotValid) {
            return new float[]{ox, oy};
        }

        for (int i = 0; i < 4; i++) {
            float[] p = pivotOnScreen(s, ox, oy, oz);
            float[] px = pivotOnScreen(s, ox + 0.01F, oy, oz);
            float[] py = pivotOnScreen(s, ox, oy + 0.01F, oz);
            if (p == null || px == null || py == null) {
                break;
            }

            float a = (px[0] - p[0]) / 0.01F;
            float b = (py[0] - p[0]) / 0.01F;
            float c = (px[1] - p[1]) / 0.01F;
            float d = (py[1] - p[1]) / 0.01F;
            float det = a * d - b * c;
            if (Math.abs(det) < 1.0E-4F) {
                break;
            }

            float ex = gx - p[0];
            float ey = gy - p[1];
            ox += (d * ex - b * ey) / det;
            oy += (-c * ex + a * ey) / det;
            ox = clamp(ox, -3.0F, 3.0F);
            oy = clamp(oy, -3.0F, 3.0F);
        }

        return new float[]{ox, oy};
    }

    static float[] targets(InteractionHand hand) {
        return hand == InteractionHand.MAIN_HAND
            ? new float[]{
                ModSettings.vmMainX,
                ModSettings.vmMainY,
                ModSettings.vmMainZ,
                ModSettings.vmMainPitch,
                ModSettings.vmMainYaw,
                ModSettings.vmMainRoll,
                ModSettings.vmMainScale
            }
            : new float[]{
                ModSettings.vmOffX,
                ModSettings.vmOffY,
                ModSettings.vmOffZ,
                ModSettings.vmOffPitch,
                ModSettings.vmOffYaw,
                ModSettings.vmOffRoll,
                ModSettings.vmOffScale
            };
    }

    static void setOffset(InteractionHand hand, float x, float y, float z) {
        if (hand == InteractionHand.MAIN_HAND) {
            ModSettings.vmMainX = x;
            ModSettings.vmMainY = y;
            ModSettings.vmMainZ = z;
        } else {
            ModSettings.vmOffX = x;
            ModSettings.vmOffY = y;
            ModSettings.vmOffZ = z;
        }
    }

    static void setRotation(InteractionHand hand, float pitch, float yaw, float roll) {
        pitch = wrap(pitch);
        yaw = wrap(yaw);
        roll = wrap(roll);
        if (hand == InteractionHand.MAIN_HAND) {
            ModSettings.vmMainPitch = pitch;
            ModSettings.vmMainYaw = yaw;
            ModSettings.vmMainRoll = roll;
        } else {
            ModSettings.vmOffPitch = pitch;
            ModSettings.vmOffYaw = yaw;
            ModSettings.vmOffRoll = roll;
        }
    }

    static void setScale(InteractionHand hand, float scale) {
        scale = clamp(scale, 0.2F, 3.0F);
        if (hand == InteractionHand.MAIN_HAND) {
            ModSettings.vmMainScale = scale;
        } else {
            ModSettings.vmOffScale = scale;
        }
    }

    public static void reset(InteractionHand hand) {
        if (hand == null || hand == InteractionHand.MAIN_HAND) {
            ModSettings.vmMainZ = 0.0F;
            ModSettings.vmMainY = 0.0F;
            ModSettings.vmMainX = 0.0F;
            ModSettings.vmMainRoll = 0.0F;
            ModSettings.vmMainYaw = 0.0F;
            ModSettings.vmMainPitch = 0.0F;
            ModSettings.vmMainScale = 1.0F;
        }

        if (hand == null || hand == InteractionHand.OFF_HAND) {
            ModSettings.vmOffZ = 0.0F;
            ModSettings.vmOffY = 0.0F;
            ModSettings.vmOffX = 0.0F;
            ModSettings.vmOffRoll = 0.0F;
            ModSettings.vmOffYaw = 0.0F;
            ModSettings.vmOffPitch = 0.0F;
            ModSettings.vmOffScale = 1.0F;
        }

        ModConfig.save();
    }

    private static void handleMainArmSwap() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            HumanoidArm arm = player.getMainArm();
            if (lastMainArm == null) {
                lastMainArm = arm;
            } else if (arm != lastMainArm) {
                lastMainArm = arm;
                ModSettings.vmMainX = -ModSettings.vmMainX;
                ModSettings.vmOffX = -ModSettings.vmOffX;
                MAIN.x = -MAIN.x;
                OFF.x = -OFF.x;
                ModConfig.save();
            }
        }
    }

    static float wrap(float degrees) {
        degrees %= 360.0F;
        if (degrees > 180.0F) {
            degrees -= 360.0F;
        }

        if (degrees < -180.0F) {
            degrees += 360.0F;
        }

        return degrees;
    }

    static float clamp(float v, float min, float max) {
        return Mth.clamp(v, min, max);
    }

    static final class HandState {
        float x;
        float y;
        float z;
        float pitch;
        float yaw;
        float roll;
        float scale = 1.0F;
        final Matrix4f outer = new Matrix4f();
        final Vector4f pivotLocal = new Vector4f();
        boolean outerValid;
        boolean pivotValid;
        float screenX;
        float screenY;
        float screenSize;
    }
}
