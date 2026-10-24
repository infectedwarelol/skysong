package com.spotifyclient.render.fx;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.spotifyclient.ModSettings;
import com.spotifyclient.compat.Compat;
import com.spotifyclient.compat.Gpu;
import com.spotifyclient.compat.Shader;
import java.util.Random;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.dimension.DimensionType.Skybox;
import net.minecraft.world.level.material.FogType;
import org.joml.Matrix4f;

public final class SkyFx {
    public static final int AURORA = 0;
    public static final int BLACK_HOLE = 1;
    public static final int STARFALL = 2;
    public static final int STORM = 3;
    private static final int BLOOM_LEVELS = 6;
    private static final TextureTarget[] sky = new TextureTarget[2];
    private static final TextureTarget[] bloom = new TextureTarget[6];
    private static DynamicTexture noise;
    private static final Matrix4f viewProj = new Matrix4f();
    private static final Matrix4f invViewProj = new Matrix4f();
    private static final Matrix4f prevViewProj = new Matrix4f();
    private static int frame;
    private static int lastType = -1;
    private static float taaSequence;
    private static boolean historyValid;
    private static long drawnFrame = -1L;

    private SkyFx() {
    }

    public static boolean active() {
        Minecraft mc = Minecraft.getInstance();
        if (Fx.allowed() && ModSettings.worldShader && mc.level != null) {
            Skybox type = mc.level.dimensionType().skybox();
            return type == Skybox.OVERWORLD || type == Skybox.END;
        } else {
            return false;
        }
    }

    static void forget() {
        historyValid = false;
        lastType = -1;
    }

    public static boolean drewThisFrame() {
        return Fx.recent(drawnFrame);
    }

    public static boolean render(Matrix4f view, Matrix4f projection, Camera camera, boolean thickFog) {
        if (active() && !thickFog && !hidesSky(camera)) {
            Minecraft mc = Minecraft.getInstance();
            RenderTarget main = mc.getMainRenderTarget();

            try {
                int type = skyType();
                boolean blackHole = type == 1;
                int scale = type == 2 ? 1 : 2;
                ensureTargets(main.width / scale, main.height / scale);
                if (type != lastType) {
                    historyValid = false;
                    lastType = type;
                }

                viewProj.set(projection).mul(view);
                invViewProj.set(viewProj).invert();
                int cur = frame & 1;
                int prev = cur ^ 1;
                float time = Fx.seconds();

                Shader march = switch (type) {
                    case 1 -> Fx.skyBlackHole;
                    case 2 -> Fx.skyStarfall;
                    case 3 -> Fx.skyStorm;
                    default -> Fx.skyAurora;
                };
                if (march.ready() && Fx.bloomDown.ready() && Fx.skyComposite.ready()) {
                    Gpu.bind(sky[cur]);
                    Fx.noBlend();
                    setSkyUniforms(march, time, main, type);
                    if (blackHole) {
                        Gpu.texture(0, noiseTexture());
                        Gpu.texture(1, Gpu.color(sky[prev]));
                        Fx.set(march, "PrevViewProj", prevViewProj);
                        float jx = halton(frame % 8 + 1, 2) - 0.5F;
                        float jy = halton(frame % 8 + 1, 3) - 0.5F;
                        Fx.set(march, "Taa", jx, jy, historyValid ? 1.0F : 0.0F, taaSequence);
                    }

                    Fx.draw(march);
                    int src = Gpu.color(sky[cur]);
                    int srcW = sky[cur].width;
                    int srcH = sky[cur].height;

                    for (int i = 0; i < 6; i++) {
                        Gpu.bind(bloom[i]);
                        Gpu.texture(0, src);
                        Fx.set(Fx.bloomDown, "Params", 1.0F / srcW, 1.0F / srcH, !blackHole && i == 0 ? 0.28F : 0.0F, blackHole ? 1.0F : 0.0F);
                        Fx.draw(Fx.bloomDown);
                        src = Gpu.color(bloom[i]);
                        srcW = bloom[i].width;
                        srcH = bloom[i].height;
                    }

                    Gpu.bind(main);
                    Gpu.texture(0, Gpu.color(sky[cur]));

                    for (int i = 0; i < 6; i++) {
                        Gpu.texture(1 + i, Gpu.color(bloom[i]));
                    }

                    setSkyUniforms(Fx.skyComposite, time, main, type);
                    Fx.draw(Fx.skyComposite);
                    prevViewProj.set(viewProj);
                    historyValid = true;
                    frame = frame + 1 & 1073741823;
                    taaSequence = (taaSequence + 0.618034F) % 1.0F;
                    drawnFrame = System.currentTimeMillis();
                    return true;
                } else {
                    return false;
                }
            } catch (Throwable t) {
                Fx.fail("sky", t);
                return false;
            } finally {
                Fx.restore(main);
            }
        } else {
            return false;
        }
    }

    public static boolean renderNow() {
        Minecraft mc = Minecraft.getInstance();
        return render(new Matrix4f(Gpu.worldView()), new Matrix4f(Gpu.worldProjection()), mc.gameRenderer.getMainCamera(), false);
    }

    public static int skyType() {
        return switch (ModSettings.worldShaderType) {
            case 1 -> 0;
            case 2 -> 1;
            case 3 -> 2;
            default -> 3;
        };
    }

    public static float daylight() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return 1.0F;
        }

        float angle = Compat.timeOfDay(mc.level) * (float) (Math.PI * 2);
        return Math.max(0.06F, Math.min(1.0F, (float)Math.cos(angle) * 2.0F + 0.5F));
    }

    static float[] sunDirection() {
        Minecraft mc = Minecraft.getInstance();
        float angle = mc.level == null ? 0.0F : Compat.timeOfDay(mc.level) * (float) (Math.PI * 2);
        return new float[]{-((float)Math.sin(angle)), (float)Math.cos(angle), 0.15F};
    }

    public static float[] fogColor() {
        int c = Fx.firstColor();
        float r = (c >> 16 & 0xFF) / 255.0F;
        float g = (c >> 8 & 0xFF) / 255.0F;
        float b = (c & 0xFF) / 255.0F;
        float brightness = ModSettings.shaderSkyBrightness;
        if (skyType() == 3) {
            float day = daylight() * brightness;
            return new float[]{0.07F + 0.55F * day, 0.075F + 0.58F * day, 0.09F + 0.62F * day};
        }

        return switch (skyType()) {
            case 1 -> new float[]{0.012F + r * 0.02F, 0.012F + g * 0.02F, 0.02F + b * 0.02F};
            case 2 -> new float[]{0.02F + r * 0.04F * brightness, 0.028F + g * 0.04F * brightness, 0.06F + b * 0.04F * brightness};
            default -> new float[]{0.03F + r * 0.09F * brightness, 0.042F + g * 0.09F * brightness, 0.085F + b * 0.09F * brightness};
        };
    }

    public static boolean hidesSky(Camera camera) {
        FogType submersion = camera.getFluidInCamera();
        return submersion != FogType.POWDER_SNOW && submersion != FogType.LAVA
            ? Compat.camEntity(camera) instanceof LivingEntity living && (living.hasEffect(MobEffects.BLINDNESS) || living.hasEffect(MobEffects.DARKNESS))
            : true;
    }

    private static void setSkyUniforms(Shader p, float time, RenderTarget main, int type) {
        int c1 = Fx.firstColor();
        int c2 = Fx.lastColor();
        float mode = Fx.rainbow() ? 2.0F : (Fx.twoColors() ? 1.0F : 0.0F);
        Fx.set(p, "InvViewProj", invViewProj);
        Fx.set(p, "Misc", time, main.width, main.height, ModSettings.shaderSkyBrightness);
        Fx.set(p, "SkyCol", (c1 >> 16 & 0xFF) / 255.0F, (c1 >> 8 & 0xFF) / 255.0F, (c1 & 0xFF) / 255.0F, mode);
        Fx.set(p, "SkyCol2", (c2 >> 16 & 0xFF) / 255.0F, (c2 >> 8 & 0xFF) / 255.0F, (c2 & 0xFF) / 255.0F, type);
        if (type == 3) {
            float[] sun = sunDirection();
            Fx.set(p, "Sun", sun[0], sun[1], sun[2], daylight());
        }
    }

    private static void ensureTargets(int w, int h) {
        boolean resized = sky[0] == null || sky[0].width != Math.max(1, w) || sky[0].height != Math.max(1, h);

        for (int i = 0; i < 2; i++) {
            sky[i] = Fx.ensure(sky[i], w, h, false, true);
        }

        int bw = w;
        int bh = h;

        for (int i = 0; i < 6; i++) {
            bw = Math.max(1, bw / 2);
            bh = Math.max(1, bh / 2);
            bloom[i] = Fx.ensure(bloom[i], bw, bh, false, true);
        }

        if (resized) {
            historyValid = false;
        }
    }

    private static int noiseTexture() {
        if (noise == null) {
            int[] a = new int[65536];
            int[] b = new int[65536];
            Random random = new Random(1592639215L);

            for (int i = 0; i < a.length; i++) {
                a[i] = random.nextInt(256);
                b[i] = random.nextInt(256);
            }

            NativeImage image = new NativeImage(256, 256, false);

            for (int y = 0; y < 256; y++) {
                for (int x = 0; x < 256; x++) {
                    int red = a[y * 256 + x];
                    int green = a[Math.floorMod(y - 17, 256) * 256 + Math.floorMod(x - 37, 256)];
                    int blue = b[y * 256 + x];
                    Compat.setPixelAbgr(image, x, y, 0xFF000000 | blue << 16 | green << 8 | red);
                }
            }

            noise = Compat.dynamicTexture("sky noise", image);
        }

        return Gpu.textureId(noise, true);
    }

    private static float halton(int index, int base) {
        float f = 0.0F;
        float r = 1.0F;
        int i = index;

        while (i > 0) {
            r /= base;
            f += r * (i % base);
            i /= base;
        }

        return f;
    }
}
