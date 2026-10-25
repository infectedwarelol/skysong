package dev.infectedware.skysong.render.fx;

import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import dev.infectedware.skysong.ModSettings;
import dev.infectedware.skysong.compat.Capture;
import dev.infectedware.skysong.compat.Gpu;
import dev.infectedware.skysong.viewmodel.ViewModel;
import java.nio.ByteBuffer;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;
import org.lwjgl.system.MemoryStack;

public final class HandFx {
    private static TextureTarget hands;
    private static boolean capturing;
    private static long drawnFrame = -1L;

    private HandFx() {
    }

    public static boolean active() {
        return Fx.allowed() && (ModSettings.shaderHands || ViewModel.editing() || SwingTrail.active());
    }

    public static boolean coveredAt(float guiX, float guiY, float guiW, float guiH) {
        if (hands != null && Fx.recent(drawnFrame)) {
            int px = Math.round(guiX / Math.max(1.0F, guiW) * hands.width);
            int py = Math.round((1.0F - guiY / Math.max(1.0F, guiH)) * hands.height);
            if (px >= 0 && py >= 0 && px < hands.width && py < hands.height) {
                try (MemoryStack stack = MemoryStack.stackPush()) {
                    ByteBuffer pixel = stack.malloc(4);
                    GlStateManager._glBindFramebuffer(36008, Gpu.fbo(hands));
                    GL11.glReadPixels(px, py, 1, 1, 6408, 5121, pixel);
                    GlStateManager._glBindFramebuffer(36008, 0);
                    Gpu.bind(Minecraft.getInstance().getMainRenderTarget());
                    return (pixel.get(3) & 255) > 24;
                }
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    public static boolean capturing() {
        return capturing;
    }

    public static void beforeHands() {
        capturing = false;
        if (active()) {
            try {
                RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
                hands = Fx.ensure(hands, main.width, main.height, true, true);
                Fx.clearAndBind(hands);
                Capture.begin(hands);
                capturing = true;
            } catch (Throwable t) {
                Fx.fail("hands", t);
                Gpu.bind(Minecraft.getInstance().getMainRenderTarget());
            }
        }
    }

    public static void afterHands() {
        if (capturing) {
            capturing = false;
            Capture.end();
            Minecraft mc = Minecraft.getInstance();
            RenderTarget main = mc.getMainRenderTarget();

            try {
                if (SwingTrail.active()) {
                    SwingTrail.apply(hands, main);
                }

                ShapeFx.composite(hands, main, null, null, look());
                drawnFrame = System.currentTimeMillis();
            } catch (Throwable t) {
                Fx.fail("hands", t);
            } finally {
                Fx.restore(main);
            }
        }
    }

    private static ShapeFx.Look look() {
        if (ViewModel.editing()) {
            return !ModSettings.shaderHands
                ? new ShapeFx.Look(0, 1.0F, true, 2.2F, 0.0F, 0.0F, 1.0F)
                : new ShapeFx.Look(
                    ModSettings.shaderHandsFill,
                    ModSettings.shaderHandsOpacity,
                    true,
                    Math.max(2.2F, ModSettings.shaderHandsOutlineWidth),
                    ModSettings.shaderHandsGlowRadius,
                    ModSettings.shaderHandsGlow ? ModSettings.shaderHandsGlowStrength : 0.0F,
                    1.0F
                );
        } else {
            return !ModSettings.shaderHands
                ? new ShapeFx.Look(0, 1.0F, true, 0.0F, 0.0F, 0.0F, 0.0F)
                : new ShapeFx.Look(
                    ModSettings.shaderHandsFill,
                    ModSettings.shaderHandsOpacity,
                    true,
                    ModSettings.shaderHandsOutline ? ModSettings.shaderHandsOutlineWidth : 0.0F,
                    ModSettings.shaderHandsGlowRadius,
                    ModSettings.shaderHandsGlow ? ModSettings.shaderHandsGlowStrength : 0.0F,
                    0.25F
                );
        }
    }
}
