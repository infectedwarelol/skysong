package dev.infectedware.skysong.render.fx;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import dev.infectedware.skysong.ModSettings;
import dev.infectedware.skysong.compat.Gpu;
import net.minecraft.client.Minecraft;

public final class SwingTrail {
    private static TextureTarget trailA;
    private static TextureTarget trailB;
    private static TextureTarget blurA;
    private static TextureTarget blurB;
    private static long lastFrame = -1L;
    private static float swing;

    private SwingTrail() {
    }

    public static boolean active() {
        return Fx.allowed() && ModSettings.swingTrail;
    }

    static void apply(RenderTarget hands, RenderTarget main) {
        Minecraft mc = Minecraft.getInstance();
        int w = main.width;
        int h = main.height;
        long now = System.currentTimeMillis();
        boolean fresh = trailA == null || trailA.width != w || trailA.height != h || lastFrame < 0L || now - lastFrame > 250L;
        trailA = Fx.ensure(trailA, w, h, false, true);
        trailB = Fx.ensure(trailB, w, h, false, true);
        if (fresh) {
            Fx.clearAndBind(trailA);
            swing = 0.0F;
        }

        float dt = fresh ? 0.0F : Math.min(0.1F, (float)(now - lastFrame) / 1000.0F);
        lastFrame = now;
        boolean swinging = !ModSettings.swingTrailOnHit || mc.player != null && (mc.player.swinging || mc.player.getAttackAnim(1.0F) > 0.0F);
        swing = swinging ? 1.0F : Math.max(0.0F, swing - dt / 0.12F);
        float tau = 0.02F + (Math.max(0.03F, ModSettings.swingTrailLength) - 0.02F) * swing;
        float keep = dt <= 0.0F ? 0.0F : (float)Math.exp(-dt / tau);
        float[] tint = Fx.echoTints(ModSettings.swingTrailColor, ModSettings.swingTrailCustomColor, ModSettings.swingTrailCustomColor2);
        Gpu.bind(trailB);
        Fx.noBlend();
        Gpu.texture(0, Gpu.color(trailA));
        Gpu.texture(1, Gpu.color(hands));
        Fx.set(Fx.trailAcc, "Trail", keep, ModSettings.swingTrailWhiten, 0.0F, 0.0F);
        Fx.setTints(Fx.trailAcc, tint);
        Fx.draw(Fx.trailAcc);
        TextureTarget t = trailA;
        trailA = trailB;
        trailB = t;
        boolean glow = ModSettings.swingTrailGlow && ModSettings.swingTrailGlowStrength > 0.0F;
        if (glow) {
            int hw = Math.max(1, w / 2);
            int hh = Math.max(1, h / 2);
            blurA = Fx.ensure(blurA, hw, hh, false, true);
            blurB = Fx.ensure(blurB, hw, hh, false, true);
            float radius = Fx.px(ModSettings.swingTrailGlowRadius) / 2.0F;
            Gpu.bind(blurA);
            Gpu.texture(0, Gpu.color(trailA));
            Fx.set(Fx.blur, "Blur", 1.0F / hw, 0.0F, radius, 0.0F);
            Fx.draw(Fx.blur);
            Gpu.bind(blurB);
            Gpu.texture(0, Gpu.color(blurA));
            Fx.set(Fx.blur, "Blur", 0.0F, 1.0F / hh, radius, 0.0F);
            Fx.draw(Fx.blur);
        }

        Gpu.bind(main);
        Fx.blendOver();
        Gpu.texture(0, Gpu.color(trailA));
        Gpu.texture(1, glow ? Gpu.color(blurB) : Gpu.color(trailA));
        Gpu.texture(2, Gpu.color(hands));
        Fx.set(Fx.echo, "Look", ModSettings.swingTrailStrength, glow ? ModSettings.swingTrailGlowStrength : 0.0F, 1.0F, 0.2F);
        Fx.setTints(Fx.echo, tint);
        Fx.draw(Fx.echo);
    }
}
