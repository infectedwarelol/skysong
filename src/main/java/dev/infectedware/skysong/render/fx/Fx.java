package dev.infectedware.skysong.render.fx;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import dev.infectedware.skysong.ModSettings;
import dev.infectedware.skysong.SkysongClient;
import dev.infectedware.skysong.compat.Gpu;
import dev.infectedware.skysong.compat.Mesh;
import dev.infectedware.skysong.compat.Shader;
import dev.infectedware.skysong.compat.Targets;
import dev.infectedware.skysong.theme.ClientAccent;
import dev.infectedware.skysong.theme.Colors;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;

public final class Fx {
    static final Shader skyAurora = Shader.of("fx_sky_aurora");
    static final Shader skyStarfall = Shader.of("fx_sky_starfall");
    static final Shader skyBlackHole = Shader.of("fx_sky_blackhole");
    static final Shader skyStorm = Shader.of("fx_sky_storm");
    static final Shader storm = Shader.of("fx_storm");
    static final Shader bloomDown = Shader.of("fx_bloom_down");
    static final Shader skyComposite = Shader.of("fx_sky_composite");
    static final Shader mask = Shader.of("fx_mask");
    static final Shader blur = Shader.of("fx_blur");
    static final Shader boxBlur = Shader.of("fx_boxblur");
    static final Shader shape = Shader.of("fx_shape");
    static final Shader grade = Shader.of("fx_grade");
    static final Shader echo = Shader.of("fx_echo");
    static final Shader trailAcc = Shader.of("fx_trail_acc");
    static final Shader motion = Shader.of("fx_motion");
    static final Shader death = Shader.of("fx_death");
    static final Shader ghostGeo = Shader.of("fx_ghost_geo");
    private static String failure;
    private static long failedAt;
    private static long loggedFailures;
    private static int generation;
    private static final Map<TextureTarget, Integer> builtIn = new WeakHashMap<>();
    private static long irisCheckedAt;
    private static boolean irisShaders;
    private static TextureTarget boxA;
    private static TextureTarget boxB;

    private Fx() {
    }

    static boolean allowed() {
        if (failure != null && System.currentTimeMillis() - failedAt > 5000L) {
            failure = null;
        }

        return failure == null && !irisShaders();
    }

    public static void reset(String why) {
        failure = null;
        irisCheckedAt = 0L;
        generation++;
        SkyFx.forget();
        MotionBlur.forget();
        KillFx.forget();
        DeathFx.forget();
        SkysongClient.LOGGER.info("[Skysong] Shaders reset ({})", why);
    }

    public static String failure() {
        return failure;
    }

    static void fail(String what, Throwable t) {
        if (failure == null) {
            failure = what + ": " + t;
            failedAt = System.currentTimeMillis();
            if (loggedFailures++ < 5L) {
                SkysongClient.LOGGER.error("[Skysong] Shaders paused ({})", what, t);
            }
        }
    }

    static boolean irisShaders() {
        long now = System.currentTimeMillis();
        if (now - irisCheckedAt < 1000L) {
            return irisShaders;
        }

        irisCheckedAt = now;

        try {
            Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            Object instance = api.getMethod("getInstance").invoke(null);
            irisShaders = (Boolean)api.getMethod("isShaderPackInUse").invoke(instance);
        } catch (Throwable t) {
            irisShaders = false;
        }

        return irisShaders;
    }

    static void draw(Shader p) {
        p.bind();
        Gpu.disableDepthTest();
        Gpu.depthMask(false);
        Gpu.disableCull();
        Mesh b = Mesh.begin(Mode.QUADS);
        b.addVertex(-1.0F, -1.0F, 0.0F);
        b.addVertex(1.0F, -1.0F, 0.0F);
        b.addVertex(1.0F, 1.0F, 0.0F);
        b.addVertex(-1.0F, 1.0F, 0.0F);
        b.draw();
    }

    static void noBlend() {
        Gpu.disableBlend();
    }

    static void blendOver() {
        Gpu.enableBlend();
        Gpu.blendFuncSeparate(770, 771, 1, 771);
    }

    static void restore(RenderTarget main) {
        Gpu.clearTextures();
        Gpu.bind(main);
        Gpu.disableBlend();
        Gpu.defaultBlendFunc();
        Gpu.enableDepthTest();
        Gpu.depthMask(true);
        Gpu.enableCull();
    }

    static TextureTarget ensure(TextureTarget fb, int w, int h, boolean depth, boolean linear) {
        w = Math.max(1, w);
        h = Math.max(1, h);
        Integer built = fb == null ? null : builtIn.get(fb);
        if (fb == null) {
            fb = Targets.create(w, h, depth);
        } else {
            if (fb.width == w && fb.height == h && built != null && built == generation) {
                return fb;
            }

            Targets.resize(fb, w, h);
        }

        builtIn.put(fb, generation);
        Targets.setFilter(fb, linear);
        return fb;
    }

    static void clearAndBind(RenderTarget fb) {
        Gpu.clear(fb, 0.0F, 0.0F, 0.0F, 0.0F);
    }

    static void blitColor(RenderTarget from, RenderTarget to) {
        Gpu.blit(from, to);
    }

    public static void boxBlur(RenderTarget from, RenderTarget into, float radius, int runs) {
        if (!boxBlur.ready()) {
            blitColor(from, into);
        } else {
            int w = from.width;
            int h = from.height;
            boxA = ensure(boxA, w, h, false, true);
            boxB = ensure(boxB, w, h, false, true);
            blitColor(from, boxA);
            noBlend();

            for (int i = 0; i < runs * 3; i++) {
                Gpu.bind(boxB);
                Gpu.texture(0, Gpu.color(boxA));
                set(boxBlur, "Box", 1.0F / w, 0.0F, radius, 0.0F);
                draw(boxBlur);
                Gpu.bind(boxA);
                Gpu.texture(0, Gpu.color(boxB));
                set(boxBlur, "Box", 0.0F, 1.0F / h, radius, 0.0F);
                draw(boxBlur);
            }

            blitColor(boxA, into);
            Gpu.clearTextures();
            Gpu.enableBlend();
            Gpu.defaultBlendFunc();
            Gpu.enableDepthTest();
            Gpu.depthMask(true);
            Gpu.enableCull();
        }
    }

    static void set(Shader p, String name, float a, float b, float c, float d) {
        p.set(name, a, b, c, d);
    }

    static void setOutSize(Shader p, float width, float height) {
        p.set("OutSize", width, height);
    }

    static void set(Shader p, String name, Matrix4f m) {
        p.set(name, m);
    }

    static void palette(Shader p, float styleW) {
        int[] pal = paletteStops();

        for (int i = 0; i < pal.length; i++) {
            int c = pal[i];
            set(p, "Pal" + i, (c >> 16 & 0xFF) / 255.0F, (c >> 8 & 0xFF) / 255.0F, (c & 0xFF) / 255.0F, 1.0F);
        }

        set(p, "Style", pal.length, flowPhase(), closed(), styleW);
    }

    static int[] paletteStops() {
        int[] src = ModSettings.shaderColorMode == 1 ? new int[]{ModSettings.shaderColor1, ModSettings.shaderColor2} : ClientAccent.palette();
        int[] out = new int[6];

        for (int i = 0; i < 6; i++) {
            out[i] = Colors.samplePalette(src, i / 5.0F);
        }

        return out;
    }

    static int firstColor() {
        return paletteStops()[0];
    }

    static int lastColor() {
        return paletteStops()[5];
    }

    static boolean rainbow() {
        return ModSettings.shaderColorMode != 1 && ClientAccent.isRainbow();
    }

    static boolean twoColors() {
        return ModSettings.shaderColorMode == 1 ? ModSettings.shaderColor1 != ModSettings.shaderColor2 : ClientAccent.usesDual();
    }

    private static float closed() {
        return ModSettings.shaderColorMode == 1 ? 0.0F : ClientAccent.closedFactor();
    }

    static float[] echoTints(int mode, int c1, int c2) {
        int a;
        int b;
        switch (mode) {
            case 1:
                int[] pal = ClientAccent.palette();
                a = Colors.samplePalette(pal, flowPhase());
                b = Colors.samplePalette(pal, (flowPhase() + 0.5F) % 1.0F);
                break;
            case 2:
                b = c1;
                a = c1;
                break;
            case 3:
                a = c1;
                b = c2;
                break;
            default:
                b = -1;
                a = -1;
        }

        return new float[]{
            (a >> 16 & 0xFF) / 255.0F, (a >> 8 & 0xFF) / 255.0F, (a & 0xFF) / 255.0F, (b >> 16 & 0xFF) / 255.0F, (b >> 8 & 0xFF) / 255.0F, (b & 0xFF) / 255.0F
        };
    }

    static void setTints(Shader p, float[] t) {
        set(p, "Tint", t[0], t[1], t[2], 1.0F);
        if (t.length >= 6) {
            set(p, "Tint2", t[3], t[4], t[5], 1.0F);
        } else {
            set(p, "Tint2", t[0], t[1], t[2], 1.0F);
        }

        Window w = Minecraft.getInstance().getWindow();
        set(p, "Flow", seconds(), (float)w.getWidth() / Math.max(1, w.getHeight()), 0.0F, 0.0F);
    }

    static boolean recent(long drawnAtMs) {
        return drawnAtMs >= 0L && System.currentTimeMillis() - drawnAtMs < 250L;
    }

    static float flowPhase() {
        return (float)(System.currentTimeMillis() % 8000L) / 8000.0F;
    }

    static float seconds() {
        return (float)(System.currentTimeMillis() % 20000000L) / 1000.0F;
    }

    static float px(float at1080) {
        return at1080 * Math.max(0.5F, Minecraft.getInstance().getWindow().getHeight() / 1080.0F);
    }
}
