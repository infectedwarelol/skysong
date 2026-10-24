package com.spotifyclient.render.fx;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.spotifyclient.ModSettings;
import com.spotifyclient.compat.Gpu;

final class ShapeFx {
    public static final int FILL_NORMAL = 0;
    public static final int FILL_GRADIENT = 1;
    public static final int FILL_GLASS = 2;
    public static final int FILL_DARK = 3;
    public static final int FILL_AURA = 4;
    private static TextureTarget maskFb;
    private static TextureTarget blurA;
    private static TextureTarget blurB;
    private static TextureTarget scene;

    private ShapeFx() {
    }

    static void composite(RenderTarget captured, RenderTarget main, RenderTarget sceneDepth, RenderTarget sceneCopy, ShapeFx.Look look) {
        int w = main.width;
        int h = main.height;
        maskFb = Fx.ensure(maskFb, w, h, false, true);
        Gpu.bind(maskFb);
        Fx.noBlend();
        Gpu.texture(0, Gpu.color(captured));
        Gpu.texture(1, Gpu.depth(captured));
        Gpu.texture(2, sceneDepth != null ? Gpu.depth(sceneDepth) : 0);
        Fx.set(Fx.mask, "Flags", sceneDepth != null ? 1.0F : 0.0F, 0.0F, 0.0F, 0.0F);
        Fx.draw(Fx.mask);
        boolean glow = look.glowStrength() > 0.0F && look.glowRadius() > 0.5F;
        if (glow) {
            int hw = Math.max(1, w / 2);
            int hh = Math.max(1, h / 2);
            blurA = Fx.ensure(blurA, hw, hh, false, true);
            blurB = Fx.ensure(blurB, hw, hh, false, true);
            float radius = Fx.px(look.glowRadius()) / 2.0F;
            Gpu.bind(blurA);
            Gpu.texture(0, Gpu.color(maskFb));
            Fx.set(Fx.blur, "Blur", 1.0F / hw, 0.0F, radius, 0.0F);
            Fx.draw(Fx.blur);
            Gpu.bind(blurB);
            Gpu.texture(0, Gpu.color(blurA));
            Fx.set(Fx.blur, "Blur", 0.0F, 1.0F / hh, radius, 0.0F);
            Fx.draw(Fx.blur);
        }

        if (look.fill() == 2 && sceneCopy == null) {
            scene = Fx.ensure(scene, w, h, false, true);
            Fx.blitColor(main, scene);
            sceneCopy = scene;
        }

        Gpu.bind(main);
        Fx.blendOver();
        Gpu.texture(0, Gpu.color(maskFb));
        Gpu.texture(1, Gpu.color(captured));
        Gpu.texture(2, sceneCopy != null ? Gpu.color(sceneCopy) : 0);
        Gpu.texture(3, glow ? Gpu.color(blurB) : 0);
        Fx.set(Fx.shape, "OutSize", w, h);
        Fx.set(Fx.shape, "Fill", look.fill(), look.opacity(), Fx.seconds(), look.drawCaptured() ? 1.0F : 0.0F);
        Fx.set(
            Fx.shape,
            "Edge",
            look.outlineWidth() > 0.0F ? Fx.px(look.outlineWidth()) : 0.0F,
            glow ? look.glowStrength() : 0.0F,
            look.hiddenFill(),
            look.outlineWhiten()
        );
        Fx.set(
            Fx.shape, "GlassP", ModSettings.shaderGlassSaturation, ModSettings.shaderGlassWhite, ModSettings.shaderGlassDistortion, ModSettings.shaderGlassTint
        );
        Fx.set(Fx.shape, "AuraP", ModSettings.shaderAuraSpeed, ModSettings.shaderAuraScale, 0.0F, 0.0F);
        Fx.palette(Fx.shape, 0.0F);
        Fx.draw(Fx.shape);
    }

    static RenderTarget copyScene(RenderTarget main) {
        scene = Fx.ensure(scene, main.width, main.height, false, true);
        Fx.blitColor(main, scene);
        return scene;
    }

    static boolean ready() {
        return Fx.mask != null && Fx.blur != null && Fx.shape != null;
    }

    record Look(int fill, float opacity, boolean drawCaptured, float outlineWidth, float glowRadius, float glowStrength, float hiddenFill, float outlineWhiten) {
    }
}
