package dev.infectedware.skysong.render.fx;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import dev.infectedware.skysong.ModSettings;
import dev.infectedware.skysong.compat.Capture;
import dev.infectedware.skysong.compat.Compat;
import dev.infectedware.skysong.compat.Gpu;
import dev.infectedware.skysong.compat.WorldCtx;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Matrix4f;

public final class StormFx {
    private static final int BLOOM_LEVELS = 6;
    private static TextureTarget graded;
    private static TextureTarget mirror;
    private static boolean mirrorReady;
    private static TextureTarget beforeLyrics;
    private static boolean beforeLyricsReady;
    private static final TextureTarget[] bloom = new TextureTarget[6];
    private static final Matrix4f invProj = new Matrix4f();
    private static boolean rainForced;
    private static long drawnFrame = -1L;
    private static float rainBefore;

    private StormFx() {
    }

    public static boolean active() {
        return Fx.allowed()
            && ModSettings.worldShader
            && ModSettings.worldShaderType == 0
            && Fx.storm != null
            && Fx.bloomDown != null
            && Fx.skyComposite != null
            && Minecraft.getInstance().level != null;
    }

    public static boolean drewThisFrame() {
        return Fx.recent(drawnFrame);
    }

    public static void beforeLyrics() {
        beforeLyricsReady = false;
        if (active()) {
            RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
            beforeLyrics = Fx.ensure(beforeLyrics, main.width, main.height, true, true);
            Fx.blitColor(main, beforeLyrics);
            Gpu.blitDepth(main, beforeLyrics);
            Gpu.bind(main);
            beforeLyricsReady = true;
        }
    }

    public static boolean beginMirror() {
        mirrorReady = false;
        if (!active()) {
            return false;
        }

        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        mirror = Fx.ensure(mirror, main.width, main.height, false, true);
        Fx.clearAndBind(mirror);
        Capture.begin(mirror);
        return true;
    }

    public static void endMirror() {
        mirrorReady = true;
        Capture.end();
        Gpu.bind(Minecraft.getInstance().getMainRenderTarget());
    }

    public static void apply(WorldCtx ctx) {
        if (active()) {
            Minecraft mc = Minecraft.getInstance();
            RenderTarget main = mc.getMainRenderTarget();

            try {
                int w = main.width;
                int h = main.height;
                graded = Fx.ensure(graded, w, h, false, true);
                Gpu.bind(graded);
                Fx.noBlend();
                Gpu.texture(0, Gpu.color(main));
                Gpu.texture(1, Gpu.depth(main));
                boolean words = mirrorReady && mirror != null;
                Gpu.texture(2, words ? Gpu.color(mirror) : Gpu.color(main));
                Gpu.texture(3, beforeLyricsReady ? Gpu.color(beforeLyrics) : Gpu.color(main));
                Gpu.texture(4, beforeLyricsReady ? Gpu.depth(beforeLyrics) : Gpu.depth(main));
                mirrorReady = false;
                beforeLyricsReady = false;
                Matrix4f proj = new Matrix4f(ctx.projection());
                invProj.set(proj).invert();
                Vec3 cam = Compat.camPos(ctx.camera());
                float[] fog = SkyFx.fogColor();
                Fx.set(Fx.storm, "Proj", proj);
                Fx.set(Fx.storm, "InvProj", invProj);
                Fx.set(Fx.storm, "ViewRot", new Matrix4f(ctx.view()));
                Fx.setOutSize(Fx.storm, w, h);
                Fx.set(Fx.storm, "CamPos", (float)(cam.x % 4096.0), (float)cam.y, (float)(cam.z % 4096.0), Fx.seconds());
                Fx.set(Fx.storm, "Params", ModSettings.stormReflections, ModSettings.stormPuddles, ModSettings.stormFog, SkyFx.daylight());
                Fx.set(Fx.storm, "FogCol", fog[0], fog[1], fog[2], raining(mc) ? 1.0F : 0.0F);
                float floorY = mc.player == null ? (float)cam.y - 1.6F : (float)floorUnder(mc);
                Fx.set(Fx.storm, "Mirror", floorY - (float)cam.y, words ? 1.0F : 0.0F, 0.0F, 0.0F);
                Fx.draw(Fx.storm);
                int src = Gpu.color(graded);
                int srcW = w;
                int srcH = h;
                int bw = w;
                int bh = h;
                float threshold = 1.0F - 0.45F * ModSettings.stormBloom;

                for (int i = 0; i < 6; i++) {
                    bw = Math.max(1, bw / 2);
                    bh = Math.max(1, bh / 2);
                    bloom[i] = Fx.ensure(bloom[i], bw, bh, false, true);
                    Gpu.bind(bloom[i]);
                    Gpu.texture(0, src);
                    Fx.set(Fx.bloomDown, "Params", 1.0F / srcW, 1.0F / srcH, i == 0 ? threshold : 0.0F, 0.0F);
                    Fx.draw(Fx.bloomDown);
                    src = Gpu.color(bloom[i]);
                    srcW = bw;
                    srcH = bh;
                }

                Gpu.bind(main);
                Gpu.texture(0, Gpu.color(graded));

                for (int i = 0; i < 6; i++) {
                    Gpu.texture(1 + i, Gpu.color(bloom[i]));
                }

                Fx.set(Fx.skyComposite, "SkyCol2", 0.0F, 0.0F, 0.0F, 0.0F);
                Fx.draw(Fx.skyComposite);
                drawnFrame = System.currentTimeMillis();
            } catch (Throwable t) {
                Fx.fail("world shader", t);
            } finally {
                Fx.restore(main);
            }
        }
    }

    public static void tick(Minecraft mc) {
        if (mc.level == null) {
            rainForced = false;
        } else {
            boolean want = active() && ModSettings.stormRain;
            if (want) {
                if (!rainForced) {
                    rainBefore = mc.level.getRainLevel(1.0F);
                }

                mc.level.setRainLevel(1.0F);
                rainForced = true;
            } else if (rainForced) {
                mc.level.setRainLevel(mc.level.getLevelData().isRaining() ? 1.0F : 0.0F);
                rainForced = false;
            }
        }
    }

    private static double floorUnder(Minecraft mc) {
        MutableBlockPos b = mc.player.blockPosition().mutable();
        int i = 0;

        while (i < 8) {
            FluidState fluid = mc.level.getFluidState(b);
            if (!fluid.isEmpty()) {
                return b.getY() + fluid.getHeight(mc.level, b);
            }

            VoxelShape shape = mc.level.getBlockState(b).getCollisionShape(mc.level, b);
            if (!shape.isEmpty()) {
                return b.getY() + shape.max(Axis.Y);
            }

            i++;
            b.move(0, -1, 0);
        }

        return mc.player.getY();
    }

    private static boolean raining(Minecraft mc) {
        return mc.level != null && mc.level.getRainLevel(1.0F) > 0.2F;
    }
}
