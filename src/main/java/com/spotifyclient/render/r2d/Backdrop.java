package com.spotifyclient.render.r2d;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.spotifyclient.ModSettings;
import com.spotifyclient.compat.Compat;
import com.spotifyclient.compat.Gpu;
import com.spotifyclient.compat.GuiGraphics;
import com.spotifyclient.compat.Targets;
import com.spotifyclient.render.fx.Fx;
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

    public static void captureForHud(GuiGraphics ctx, float delta) {
        Minecraft mc = Minecraft.getInstance();
        long frame = mc.getFrameTimeNs();
        if (frame != hudFrame || !ready) {
            hudFrame = frame;
            capture(ctx, delta);
        }
    }

    public static void capture(GuiGraphics ctx, float delta) {
        Minecraft mc = Minecraft.getInstance();
        Compat.flush(ctx);
        Compat.drawCollectedGui();
        RenderTarget main = mc.getMainRenderTarget();
        int w = main.width;
        int h = main.height;
        sharp = ensure(sharp, w, h);
        blurred = ensure(blurred, w, h);
        float radius = ModSettings.rectBackdropBlur;
        float perPass = Math.max(1.0F, Math.min(24.0F, radius * 0.5F * pixelsPerGuiUnit(mc)));
        if (radius > 0.5F) {
            Fx.boxBlur(main, blurred, perPass, 2);
        } else {
            blit(main, blurred, w, h);
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

    private static void blit(RenderTarget from, RenderTarget to, int w, int h) {
        Gpu.blit(from, to);
    }
}
