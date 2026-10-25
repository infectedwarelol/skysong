package dev.infectedware.skysong.render.fx;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import dev.infectedware.skysong.ModSettings;
import dev.infectedware.skysong.compat.Gpu;
import net.minecraft.client.Minecraft;

public final class WorldFx {
    private static TextureTarget graded;
    private static long drawnFrame = -1L;

    private WorldFx() {
    }

    public static boolean active() {
        return Fx.allowed() && ModSettings.worldShader && ModSettings.shaderBlocks;
    }

    public static void apply() {
        if (active()) {
            Minecraft mc = Minecraft.getInstance();
            RenderTarget main = mc.getMainRenderTarget();

            try {
                graded = Fx.ensure(graded, main.width, main.height, false, false);
                Gpu.bind(graded);
                Fx.noBlend();
                Gpu.texture(0, Gpu.color(main));
                Gpu.texture(1, Gpu.depth(main));
                Fx.set(
                    Fx.grade,
                    "Grade",
                    ModSettings.shaderBlocksSaturation,
                    ModSettings.shaderBlocksBrightness,
                    ModSettings.shaderBlocksContrast,
                    ModSettings.shaderBlocksTint
                );
                Fx.palette(Fx.grade, SkyFx.active() ? 1.0F : 0.0F);
                Fx.draw(Fx.grade);
                Fx.blitColor(graded, main);
                drawnFrame = System.currentTimeMillis();
            } catch (Throwable t) {
                Fx.fail("blocks", t);
            } finally {
                Fx.restore(main);
            }
        }
    }
}
