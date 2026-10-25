package dev.infectedware.skysong.render.r2d;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import dev.infectedware.skysong.ModSettings;
import dev.infectedware.skysong.compat.Compat;
import dev.infectedware.skysong.compat.Gpu;
import dev.infectedware.skysong.compat.GuiGraphics;
import dev.infectedware.skysong.compat.Targets;
import dev.infectedware.skysong.render.fx.Fx;
import net.minecraft.client.Minecraft;

public final class Backdrop {
    private static TextureTarget sharp;
    private static TextureTarget blurred;
    private static boolean ready;
    private static long hudFrame = -1L;

    private Backdrop() {
    }

    public static boolean ready() {
        return ready;
    }

    public static int texture() {
        return blurred == null ? 0 : Gpu.color(blurred);
    }

    public static void captureForHud() {
        Minecraft mc = Minecraft.getInstance();
        long frame = mc.getFrameTimeNs();
        if (frame != hudFrame || !ready) {
            hudFrame = frame;
            capture();
        }
    }

    public static void capture() {
        Minecraft mc = Minecraft.getInstance();
        Compat.flush();
        Compat.drawCollectedGui();
        RenderTarget main = mc.getMainRenderTarget();
        int w = main.width;
        int h = main.height;
        sharp = ensure(sharp, w, h);
        blurred = ensure(blurred, w, h);
        float radius = ModSettings.rectBackdropBlur;
        float perPass = Math.clamp(radius * 0.5F * pixelsPerGuiUnit(mc), 1.0F, 24.0F);
        if (radius > 0.5F) {
            Fx.boxBlur(main, blurred, perPass, 2);
        } else {
            Gpu.blit(main, blurred);
        }

        Gpu.bind(main);
        ready = true;
    }

    public static void release() {
        ready = false;
    }

    private static float pixelsPerGuiUnit(Minecraft mc) {
        return Math.max(1.0F, mc.getWindow().getHeight() / 1080.0F);
    }

    private static TextureTarget ensure(TextureTarget fb, int w, int h) {
        return Targets.ensure(fb, w, h, false);
    }
}
