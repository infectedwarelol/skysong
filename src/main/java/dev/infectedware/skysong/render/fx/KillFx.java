package dev.infectedware.skysong.render.fx;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import dev.infectedware.skysong.ModSettings;
import dev.infectedware.skysong.compat.Compat;
import dev.infectedware.skysong.compat.Gpu;
import dev.infectedware.skysong.compat.GuiGraphics;
import dev.infectedware.skysong.compat.Hooks;
import dev.infectedware.skysong.compat.Mesh;
import dev.infectedware.skysong.compat.Shader;
import dev.infectedware.skysong.compat.WorldCtx;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Map.Entry;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public final class KillFx {
    public static final String[] EFFECTS = new String[]{"Shatter", "Soul Rift", "Thunderclap", "Black Hole", "Disintegrate", "Judgement", "Firework", "Frostbite", "Inferno", "Blossom", "Ender Warp", "Vortex", "Comet", "Aurora", "Glitch", "Starfall", "Shockwave", "Eclipse", "Crystal Spikes", "Halo", "Supernova", "Random"};
    public static final String[] COLORS = new String[]{"Theme", "White", "Custom", "Two colors"};
    public static final int SHATTER = 0;
    public static final int SOUL = 1;
    public static final int VOID = 3;
    public static final int DUST = 4;
    public static final int FIREWORK = 6;
    public static final int FROST = 7;
    public static final int INFERNO = 8;
    public static final int BLOSSOM = 9;
    public static final int WARP = 10;
    public static final int VORTEX = 11;
    public static final int COMET = 12;
    public static final int AURORA = 13;
    public static final int GLITCH = 14;
    public static final int STARFALL = 15;
    public static final int SHOCKWAVE = 16;
    public static final int ECLIPSE = 17;
    public static final int CRYSTAL = 18;
    public static final int HALO = 19;
    public static final int NOVA = 20;
    private static final int KINDS = 21;
    private static final float[] LIFE = new float[]{
        1.7F, 2.4F, 1.2F, 1.7F, 2.1F, 1.8F, 1.9F, 2.0F, 2.2F, 2.6F, 1.2F, 2.2F, 1.9F, 2.6F, 1.4F, 2.3F, 1.5F, 2.4F, 2.0F, 2.4F, 1.8F
    };
    private static final float COMET_FALL = 0.45F;
    private static final float GLITCH_TIME = 0.95F;
    private static final float CRYSTAL_BREAK = 1.15F;
    private static final float NOVA_GATHER = 0.35F;
    private static final float[] WIND6 = new float[]{0.75F, 1.0F, 1.0F, 0.35F, 0.6F, 1.0F};
    private static final float[] COMET6 = new float[]{1.0F, 0.85F, 0.4F, 1.0F, 0.25F, 0.1F};
    private static final float[] AURORA6 = new float[]{0.25F, 1.0F, 0.6F, 0.6F, 0.3F, 1.0F};
    private static final float[] GLITCH6 = new float[]{0.2F, 1.0F, 0.9F, 1.0F, 0.2F, 0.6F};
    private static final float[] STAR6 = new float[]{1.0F, 0.95F, 0.7F, 0.7F, 0.8F, 1.0F};
    private static final float[] GOLD6 = new float[]{1.0F, 0.9F, 0.55F, 1.0F, 0.55F, 0.15F};
    private static final float[] AMETHYST6 = new float[]{0.85F, 0.6F, 1.0F, 0.5F, 0.3F, 0.95F};
    private static final float[] NOVA6 = new float[]{1.0F, 1.0F, 1.0F, 0.4F, 0.6F, 1.0F};
    private static final float FIREWORK_RISE = 0.35F;
    private static final float FIREWORK_HEIGHT = 5.0F;
    private static final float FROST_FREEZE = 0.45F;
    private static final float WARP_TIME = 0.35F;
    private static final float[] FIRE6 = new float[]{1.0F, 0.62F, 0.12F, 1.0F, 0.16F, 0.05F};
    private static final float[] ICE6 = new float[]{0.8F, 0.96F, 1.0F, 0.42F, 0.7F, 1.0F};
    private static final float[] PINK6 = new float[]{1.0F, 0.76F, 0.88F, 1.0F, 0.45F, 0.7F};
    private static final float[] ENDER6 = new float[]{0.78F, 0.38F, 1.0F, 0.3F, 0.92F, 0.86F};
    private static final List<Integer> randomBag = new ArrayList<>();
    private static int lastRandom = -1;
    private static final List<KillFx.Effect> effects = new ArrayList<>();
    private static final Map<Integer, Long> lastHits = new HashMap<>();
    private static final Map<Integer, Long> finished = new HashMap<>();
    private static final List<Integer> pendingKills = new ArrayList<>();
    private static final Map<Integer, Long> hidden = new HashMap<>();
    private static final ModelSnapshot.Composite composite = new ModelSnapshot.Composite();
    private static final Random RNG = new Random();
    private static TextureTarget capture;
    private static int previewType = -1;
    private static long flashAt = -1L;
    private static int spawnedCount;
    private static float swordSize = 1.0F;
    private static final float[] WHITE = new float[]{1.0F, 1.0F, 1.0F};
    private static final float[] WHITE6 = new float[]{1.0F, 1.0F, 1.0F, 1.0F, 1.0F, 1.0F};
    private static final float[] BLACK = new float[]{0.0F, 0.0F, 0.0F};
    private static long lastAmbient;

    private KillFx() {
    }

    public static void init() {
        AttackEntityCallback.EVENT
            .register(
                (player, world, hand, entity, hit) -> {
                    if (world.isClientSide()
                        && entity instanceof LivingEntity le
                        && player == Minecraft.getInstance().player
                        && le.isAlive()
                        && le.getHealth() > 0.0F
                        && !finished.containsKey(le.getId())) {
                        lastHits.put(le.getId(), System.currentTimeMillis());
                    }

                    return InteractionResult.PASS;
                }
            );
        ClientTickEvents.END_CLIENT_TICK.register(KillFx::tick);
        Hooks.hud(KillFx::hudFlash);
    }

    public static boolean active() {
        return Fx.allowed() && ModSettings.killEffects;
    }

    public static boolean hides(Entity e) {
        return !hidden.isEmpty() && hidden.containsKey(e.getId()) && !ModelSnapshot.recording();
    }

    public static void preview(int type) {
        previewType = type >= 0 && type < KINDS ? type : nextRandom();
    }

    private static int pick() {
        int t = ModSettings.killEffectType;
        return t >= 0 && t < KINDS ? t : nextRandom();
    }

    static int nextRandom() {
        if (randomBag.isEmpty()) {
            for (int i = 0; i < KINDS; i++) {
                randomBag.add(i);
            }

            java.util.Collections.shuffle(randomBag, RNG);
            if (randomBag.size() > 1 && randomBag.getLast() == lastRandom) {
                java.util.Collections.swap(randomBag, 0, randomBag.size() - 1);
            }
        }

        lastRandom = randomBag.removeLast();
        return lastRandom;
    }

    private static boolean usesModel(int type) {
        return type == SHATTER || type == SOUL || type == VOID || type == DUST || type == FROST || type == INFERNO || type == BLOSSOM || type == WARP || type == GLITCH;
    }

    private static boolean usesShards(int type) {
        return type == SHATTER || type == DUST || type == FROST || type == INFERNO;
    }

    private static float[] themed(float[] palette, float[] tint) {
        return ModSettings.killEffectColor == 0 ? palette : tint;
    }

    static void forget() {
        lastHits.clear();
        finished.clear();
        pendingKills.clear();
        hidden.clear();
        effects.clear();
    }

    private static void tick(Minecraft mc) {
        if (mc.level == null) {
            forget();
        } else {
            long now = System.currentTimeMillis();
            Iterator<Entry<Integer, Long>> it = lastHits.entrySet().iterator();

            while (it.hasNext()) {
                Entry<Integer, Long> hit = it.next();
                if (now - hit.getValue() > 5000L) {
                    it.remove();
                } else if (mc.level.getEntity(hit.getKey()) instanceof LivingEntity le && le.getHealth() <= 0.0F) {
                    it.remove();
                    if (finished.putIfAbsent(le.getId(), now) == null
                        && ModSettings.killEffects
                        && (!ModSettings.killEffectPlayersOnly || le instanceof Player)) {
                        pendingKills.add(le.getId());
                    }
                }
            }

            finished.values().removeIf(at -> now - at > 15000L);
            hidden.values().removeIf(at -> now - at > 3500L);
        }
    }

    public static void render(WorldCtx ctx) {
        if (!active()) {
            effects.clear();
            pendingKills.clear();
            previewType = -1;
        } else {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null && mc.player != null) {
                RenderTarget main = mc.getMainRenderTarget();
                float tickDelta = ctx.partialTick();
                long now = clock();

                try {
                    for (int id : pendingKills) {
                        if (mc.level.getEntity(id) instanceof LivingEntity le) {
                            spawn(mc, le, le.getPosition(tickDelta), tickDelta, pick());
                        }
                    }

                    pendingKills.clear();
                    if (previewType >= 0) {
                        Vec3 look = Vec3.directionFromRotation(0.0F, mc.player.getYRot());
                        Vec3 at = mc.player.getPosition(tickDelta).add(look.scale(3.5));
                        spawn(mc, mc.player, at, tickDelta, previewType);
                        previewType = -1;
                    }

                    effects.removeIf(ex -> (float)(now - ex.born) > LIFE[ex.type] * 1000.0F);
                    if (!effects.isEmpty()) {
                        Vec3 cam = Compat.camPos(ctx.camera());
                        float[] tint = tint();

                        for (KillFx.Effect e : effects) {
                            moments(mc, e, (float)(now - e.born) / 1000.0F);
                        }

                        boolean shapes = false;

                        for (KillFx.Effect e : effects) {
                            shapes |= !e.parts.isEmpty();
                        }

                        if (shapes) {
                            capture = ModelSnapshot.beginCapture(capture, main);

                            for (KillFx.Effect e : effects) {
                                float t = (float)(now - e.born) / 1000.0F;
                                switch (e.type) {
                                    case 0 -> drawShatter(e, t, cam);
                                    case 1, BLOSSOM -> drawSoul(e, t, cam);
                                    case 3 -> drawVoid(e, t, cam);
                                    case 4, INFERNO -> drawDust(e, t, cam);
                                    case FROST -> drawFrost(e, t, cam);
                                    case WARP -> drawWarp(e, t, cam);
                                    case GLITCH -> drawGlitch(e, t, cam);
                                    default -> {
                                    }
                                }
                            }

                            composite.apply(capture, main, 0.95F, 12.0F, 1.7F, tint);
                        }

                        for (KillFx.Effect e : effects) {
                            if (e.type == 3) {
                                singularity(main, e, (float)(now - e.born) / 1000.0F, cam);
                            }
                        }

                        beginAdditive(main);

                        for (KillFx.Effect e : effects) {
                            float t = (float)(now - e.born) / 1000.0F;
                            switch (e.type) {
                                case 1 -> drawBeam(e, t, cam, tint);
                                case 2 -> drawThunder(e, t, cam, tint);
                                case 3 -> drawVoidLight(e, t, cam, tint);
                                case 4 -> {
                                }
                                case 5 -> drawSword(e, t, cam, tint);
                                case FIREWORK -> drawFirework(e, t, cam, themed(WHITE6, tint));
                                case FROST -> drawFrostLight(e, t, cam, themed(ICE6, tint));
                                case INFERNO -> drawInferno(e, t, cam, themed(FIRE6, tint));
                                case BLOSSOM -> drawBlossom(e, t, cam, themed(PINK6, tint));
                                case WARP -> drawWarpLight(e, t, cam, themed(ENDER6, tint));
                                case VORTEX -> drawVortex(e, t, cam, themed(WIND6, tint));
                                case COMET -> drawComet(e, t, cam, themed(COMET6, tint));
                                case AURORA -> drawAurora(e, t, cam, themed(AURORA6, tint));
                                case GLITCH -> drawGlitchLight(e, t, cam, themed(GLITCH6, tint));
                                case STARFALL -> drawStarfall(e, t, cam, themed(STAR6, tint));
                                case SHOCKWAVE -> drawShockwave(e, t, cam, tint);
                                case ECLIPSE -> drawEclipse(e, t, cam, themed(GOLD6, tint));
                                case CRYSTAL -> drawCrystal(e, t, cam, themed(AMETHYST6, tint));
                                case HALO -> drawHalo(e, t, cam, themed(GOLD6, tint));
                                case NOVA -> drawSupernova(e, t, cam, themed(NOVA6, tint));
                                default -> drawShatterFlash(e, t, cam, tint);
                            }
                        }

                        ambientParticles(mc, now);
                    }
                } catch (Throwable t) {
                    Fx.fail("kill effects", t);
                    effects.clear();
                } finally {
                    Fx.restore(main);
                }
            }
        }
    }

    private static float[] tint() {
        int mode = switch (ModSettings.killEffectColor) {
            case 1 -> 0;
            case 2 -> 2;
            case 3 -> 3;
            default -> 1;
        };
        return Fx.echoTints(mode, ModSettings.killEffectCustomColor, ModSettings.killEffectCustomColor2);
    }

    private static void spawn(Minecraft mc, LivingEntity victim, Vec3 at, float tickDelta, int type) {
        KillFx.Effect e = new KillFx.Effect();
        e.type = type;
        e.x = at.x;
        e.y = at.y;
        e.z = at.z;
        e.height = victim.getBbHeight();
        e.born = clock();
        e.seed = RNG.nextLong();
        float windAngle = RNG.nextFloat() * (float) (Math.PI * 2);
        e.windX = Mth.cos(windAngle);
        e.windZ = Mth.sin(windAngle);
        if (usesModel(type)) {
            e.parts = ModelSnapshot.record(victim, tickDelta);
            if (usesShards(type)) {
                buildShards(e);
            }

            if (victim != mc.player) {
                hidden.put(victim.getId(), e.born);
            }
        }

        if (type == 2) {
            flashAt = e.born;
        }

        effects.add(e);
        spawnedCount++;
        burst(mc, e);
    }

    private static void buildShards(KillFx.Effect e) {
        Random r = new Random(e.seed);
        e.shards = new float[e.parts.size()][];
        float midY = e.height * 0.5F;

        for (int p = 0; p < e.parts.size(); p++) {
            ModelSnapshot.Part part = e.parts.get(p);
            int quads = part.vertices / 4;
            float[] s = new float[quads * 10];

            for (int q = 0; q < quads; q++) {
                float cx = 0.0F;
                float cy = 0.0F;
                float cz = 0.0F;

                for (int k = 0; k < 4; k++) {
                    int i = (q * 4 + k) * 8;
                    cx += part.data[i];
                    cy += part.data[i + 1];
                    cz += part.data[i + 2];
                }

                cx /= 4.0F;
                cy /= 4.0F;
                cz /= 4.0F;
                float dx = cx + (r.nextFloat() - 0.5F) * 0.5F;
                float dy = cy - midY + (r.nextFloat() - 0.5F) * 0.4F;
                float dz = cz + (r.nextFloat() - 0.5F) * 0.5F;
                float len = (float)Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (len < 0.001F) {
                    dx = r.nextFloat() - 0.5F;
                    dy = 0.3F;
                    dz = r.nextFloat() - 0.5F;
                    len = (float)Math.sqrt(dx * dx + dy * dy + dz * dz);
                }

                float ax = r.nextFloat() - 0.5F;
                float ay = r.nextFloat() - 0.5F;
                float az = r.nextFloat() - 0.5F;
                float al = Math.max(0.001F, (float)Math.sqrt(ax * ax + ay * ay + az * az));
                int o = q * 10;
                s[o] = cx;
                s[o + 1] = cy;
                s[o + 2] = cz;
                if (e.type == DUST || e.type == INFERNO) {
                    s[o + 3] = (1.0F - Mth.clamp(cy / Math.max(0.1F, e.height), 0.0F, 1.0F)) * 0.75F + r.nextFloat() * 0.15F;
                    s[o + 4] = 0.5F + r.nextFloat() * 0.9F;
                    s[o + 5] = 0.6F + r.nextFloat() * 0.8F;
                    s[o + 9] = (1.5F + r.nextFloat() * 4.0F) * (r.nextBoolean() ? 1.0F : -1.0F);
                } else {
                    float speed = 1.8F + r.nextFloat() * 3.4F;
                    s[o + 3] = dx / len * speed;
                    s[o + 4] = dy / len * speed + 2.2F + r.nextFloat() * 2.4F;
                    s[o + 5] = dz / len * speed;
                    s[o + 9] = (5.0F + r.nextFloat() * 13.0F) * (r.nextBoolean() ? 1.0F : -1.0F);
                }

                s[o + 6] = ax / al;
                s[o + 7] = ay / al;
                s[o + 8] = az / al;
            }

            e.shards[p] = s;
        }
    }

    private static void drawShatter(KillFx.Effect e, float t, Vec3 cam) {
        shatterParts(e, t, LIFE[e.type], cam);
    }

    private static void drawFrost(KillFx.Effect e, float t, Vec3 cam) {
        if (t >= FROST_FREEZE) {
            shatterParts(e, t - FROST_FREEZE, LIFE[FROST] - FROST_FREEZE, cam);
            return;
        }

        float shake = t > FROST_FREEZE - 0.15F ? Mth.sin(t * 90.0F) * 0.025F : 0.0F;
        float ox = (float)(e.x - cam.x) + shake;
        float oy = (float)(e.y - cam.y);
        float oz = (float)(e.z - cam.z);

        for (ModelSnapshot.Part part : e.parts) {
            ModelSnapshot.draw(part, capture, (v, pos, n) -> {
                pos[0] += ox;
                pos[1] += oy;
                pos[2] += oz;
            }, 255);
        }
    }

    private static void drawWarp(KillFx.Effect e, float t, Vec3 cam) {
        float k = Mth.clamp(t / WARP_TIME, 0.0F, 1.0F);
        int alpha = alpha(1.0F - smooth(0.7F, 1.0F, k));
        if (alpha <= 1) {
            return;
        }

        float squeeze = 1.0F - k * k;
        float stretch = 1.0F + 1.6F * k * k;
        float pivot = e.height * 0.5F;
        float ox = (float)(e.x - cam.x);
        float oy = (float)(e.y - cam.y);
        float oz = (float)(e.z - cam.z);

        for (ModelSnapshot.Part part : e.parts) {
            ModelSnapshot.draw(part, capture, (v, pos, n) -> {
                pos[0] = pos[0] * squeeze + ox;
                pos[1] = pivot + (pos[1] - pivot) * stretch + oy;
                pos[2] = pos[2] * squeeze + oz;
            }, alpha);
        }
    }

    private static void shatterParts(KillFx.Effect e, float t, float life, Vec3 cam) {
        float k = t / life;
        int alpha = alpha(1.0F - smooth(0.5F, 1.0F, k));
        float scale = 1.0F - 0.65F * k;
        float gravity = 7.5F;
        float ox = (float)(e.x - cam.x);
        float oy = (float)(e.y - cam.y);
        float oz = (float)(e.z - cam.z);

        for (int p = 0; p < e.parts.size(); p++) {
            float[] s = e.shards[p];
            ModelSnapshot.draw(e.parts.get(p), capture, (v, pos, n) -> {
                int o = v / 4 * 10;
                if (o + 10 <= s.length) {
                    float cx = s[o];
                    float cy = s[o + 1];
                    float cz = s[o + 2];
                    float ang = s[o + 9] * t;
                    float cos = Mth.cos(ang);
                    float sin = Mth.sin(ang);
                    float ax = s[o + 6];
                    float ay = s[o + 7];
                    float az = s[o + 8];
                    rotate(pos, cx, cy, cz, ax, ay, az, cos, sin, scale);
                    rotate(n, 0.0F, 0.0F, 0.0F, ax, ay, az, cos, sin, 1.0F);
                    float nx = s[o + 3] * t;
                    float nz = s[o + 5] * t;
                    float ny = s[o + 4] * t - 0.5F * gravity * t * t;
                    float floor = 0.04F - cy;
                    if (ny < floor) {
                        ny = floor;
                        nx *= 0.85F;
                        nz *= 0.85F;
                    }

                    pos[0] += nx + ox;
                    pos[1] += ny + oy;
                    pos[2] += nz + oz;
                }
            }, alpha);
        }
    }

    private static void drawDust(KillFx.Effect e, float t, Vec3 cam) {
        int alpha = alpha(1.0F - smooth(0.85F, 1.0F, t / LIFE[e.type]));
        float ox = (float)(e.x - cam.x);
        float oy = (float)(e.y - cam.y);
        float oz = (float)(e.z - cam.z);

        for (int p = 0; p < e.parts.size(); p++) {
            float[] s = e.shards[p];
            ModelSnapshot.draw(e.parts.get(p), capture, (v, pos, n) -> {
                int o = v / 4 * 10;
                if (o + 10 <= s.length) {
                    float cx = s[o];
                    float cy = s[o + 1];
                    float cz = s[o + 2];
                    float tau = t - s[o + 3];
                    if (tau < 0.0F) {
                        float glitch = -tau < 0.18F ? Mth.sin(t * 70.0F + cy * 31.0F) * 0.035F : 0.0F;
                        pos[0] += ox + glitch;
                        pos[1] += oy;
                        pos[2] += oz;
                    } else {
                        float left = Math.max(0.0F, 1.0F - tau / 1.05F);
                        float ang = s[o + 9] * tau;
                        float cos = Mth.cos(ang);
                        float sin = Mth.sin(ang);
                        rotate(pos, cx, cy, cz, s[o + 6], s[o + 7], s[o + 8], cos, sin, left);
                        rotate(n, 0.0F, 0.0F, 0.0F, s[o + 6], s[o + 7], s[o + 8], cos, sin, 1.0F);
                        float blow = s[o + 5] * tau * (1.0F + tau);
                        float swirl = Mth.sin(tau * 4.0F + cy * 5.0F) * 0.25F * tau;
                        pos[0] += e.windX * blow - e.windZ * swirl + ox;
                        pos[1] += s[o + 4] * tau + 0.6F * tau * tau + oy;
                        pos[2] += e.windZ * blow + e.windX * swirl + oz;
                    }
                }
            }, alpha);
        }
    }

    private static void rotate(float[] p, float cx, float cy, float cz, float ax, float ay, float az, float cos, float sin, float scale) {
        float x = p[0] - cx;
        float y = p[1] - cy;
        float z = p[2] - cz;
        float dot = ax * x + ay * y + az * z;
        float rx = x * cos + (ay * z - az * y) * sin + ax * dot * (1.0F - cos);
        float ry = y * cos + (az * x - ax * z) * sin + ay * dot * (1.0F - cos);
        float rz = z * cos + (ax * y - ay * x) * sin + az * dot * (1.0F - cos);
        p[0] = cx + rx * scale;
        p[1] = cy + ry * scale;
        p[2] = cz + rz * scale;
    }

    private static void drawShatterFlash(KillFx.Effect e, float t, Vec3 cam, float[] tint) {
        float k = t / 0.35F;
        if (!(k >= 1.0F)) {
            Vec3 feet = new Vec3(e.x, e.y + 0.05, e.z).subtract(cam);
            float a = (1.0F - k) * (1.0F - k);
            ring(feet, 0.4F + 3.2F * easeOut(k), 0.35F * (1.0F - k) + 0.05F, tint, a * 0.9F);
            ring(feet, 0.2F + 1.6F * easeOut(k), 0.12F, WHITE, a);
        }
    }

    private static void drawSoul(KillFx.Effect e, float t, Vec3 cam) {
        float k = t / LIFE[e.type];
        float fade = smooth(0.0F, 0.12F, t) * (1.0F - smooth(0.55F, 1.0F, k));
        if (!(fade <= 0.01F)) {
            int alpha = alpha(fade);
            float rise = 0.3F * t + 1.9F * t * t;
            float stretch = 1.0F + 0.9F * t * t;
            float squeeze = 1.0F / (1.0F + 0.75F * t * t);
            float pivot = e.height * 0.5F;
            float ox = (float)(e.x - cam.x);
            float oy = (float)(e.y - cam.y);
            float oz = (float)(e.z - cam.z);

            for (ModelSnapshot.Part part : e.parts) {
                ModelSnapshot.draw(part, capture, (v, pos, n) -> {
                    float sway = Mth.sin(t * 5.0F + pos[1] * 2.2F) * 0.07F * t;
                    pos[0] = pos[0] * squeeze + sway + ox;
                    pos[1] = pivot + (pos[1] - pivot) * stretch + rise + oy;
                    pos[2] = pos[2] * squeeze + oz;
                }, alpha);
            }
        }
    }

    private static void drawBeam(KillFx.Effect e, float t, Vec3 cam, float[] tint) {
        float k = t / LIFE[1];
        float env = smooth(0.0F, 0.18F, t) * (1.0F - smooth(0.6F, 1.0F, k));
        if (!(env <= 0.01F)) {
            Vec3 base = new Vec3(e.x, e.y, e.z).subtract(cam);
            float flicker = 0.9F + 0.1F * Mth.sin(t * 40.0F);
            beam(base, 40.0F, 0.95F, tint, 0.42F * env * flicker);
            beam(base, 40.0F, 0.16F, WHITE6, 0.85F * env);

            for (int i = 0; i < 4; i++) {
                float rt = t - i * 0.22F;
                if (!(rt < 0.0F) && !(rt > 1.2F)) {
                    float q = rt / 1.2F;
                    float y = 0.1F + 14.0F * q * q + 2.0F * q;
                    ring(base.add(0.0, y, 0.0), 1.5F * (1.0F - q) + 0.25F, 0.18F + 0.1F * (1.0F - q), tint, (1.0F - q) * env);
                }
            }

            ring(base.add(0.0, 0.05, 0.0), 0.9F + 0.4F * smooth(0.0F, 0.4F, t), 0.9F, tint, 0.35F * env);
        }
    }

    private static void drawFirework(KillFx.Effect e, float t, Vec3 cam, float[] tint) {
        Vec3 feet = new Vec3(e.x, e.y + 0.1, e.z).subtract(cam);
        if (t < FIREWORK_RISE) {
            float q = easeOut(t / FIREWORK_RISE);
            Vec3 rocket = feet.add(0.0, FIREWORK_HEIGHT * q, 0.0);
            List<Vec3> trail = List.of(feet.add(0.0, FIREWORK_HEIGHT * q * 0.35, 0.0), rocket);
            ribbon(trail, 0.14F, tint, 0.8F, null);
            ribbon(trail, 0.05F, WHITE6, 1.0F, null);
            return;
        }

        float q = Mth.clamp((t - FIREWORK_RISE) / (LIFE[FIREWORK] - FIREWORK_RISE), 0.0F, 1.0F);
        float a = (1.0F - q) * (1.0F - q);
        Vec3 burst = feet.add(0.0, FIREWORK_HEIGHT, 0.0);

        for (int i = 0; i < 3; i++) {
            float qi = Mth.clamp(q * (1.0F + i * 0.4F), 0.0F, 1.0F);
            facingRing(burst, 0.3F + 4.2F * easeOut(qi), 0.22F * (1.0F - qi) + 0.04F, tint, a * (1.0F - i * 0.25F));
        }

        Random r = new Random(e.seed);

        for (int s = 0; s < 14; s++) {
            double yaw = r.nextDouble() * Math.PI * 2.0;
            double pitch = (r.nextDouble() - 0.35) * Math.PI;
            Vec3 dir = new Vec3(Math.cos(yaw) * Math.cos(pitch), Math.sin(pitch), Math.sin(yaw) * Math.cos(pitch));
            float reach = 3.2F + r.nextFloat() * 1.6F;
            List<Vec3> spoke = new ArrayList<>();

            for (int j = 0; j <= 6; j++) {
                float f = j / 6.0F;
                float travel = reach * easeOut(q) * (0.4F + 0.6F * f);
                spoke.add(burst.add(dir.scale(travel)).add(0.0, -1.8F * q * q * f, 0.0));
            }

            ribbon(spoke, 0.07F, tint, 0.85F * a, null);
        }

        if (q < 0.15F) {
            facingRing(burst, 0.6F, 0.6F * (1.0F - q / 0.15F), WHITE6, 1.0F - q / 0.15F);
        }
    }

    private static void drawFrostLight(KillFx.Effect e, float t, Vec3 cam, float[] tint) {
        Vec3 feet = new Vec3(e.x, e.y + 0.05, e.z).subtract(cam);
        float grow = smooth(0.0F, FROST_FREEZE, t);
        float fade = 1.0F - smooth(FROST_FREEZE + 0.6F, LIFE[FROST], t);
        ring(feet, 0.3F + 1.4F * grow, 0.5F, tint, 0.55F * fade);
        ring(feet, 0.2F + 1.2F * grow, 0.06F, WHITE6, 0.7F * fade);
        if (t >= FROST_FREEZE) {
            float q = (t - FROST_FREEZE) / 0.45F;
            if (q < 1.0F) {
                float a = (1.0F - q) * (1.0F - q);
                Vec3 chest = new Vec3(e.x, e.y + e.height * 0.5, e.z).subtract(cam);
                facingRing(chest, 0.3F + 2.6F * easeOut(q), 0.2F * (1.0F - q) + 0.04F, tint, a);
                ring(feet, 0.4F + 3.4F * easeOut(q), 0.3F * (1.0F - q) + 0.05F, tint, a);
            }
        }
    }

    private static void drawInferno(KillFx.Effect e, float t, Vec3 cam, float[] tint) {
        float env = smooth(0.0F, 0.15F, t) * (1.0F - smooth(1.4F, LIFE[INFERNO], t));
        if (env <= 0.01F) {
            return;
        }

        Vec3 base = new Vec3(e.x, e.y, e.z).subtract(cam);
        float flicker = 0.8F + 0.2F * Mth.sin(t * 37.0F) * Mth.sin(t * 23.0F + 1.3F);
        beam(base, 4.5F + e.height, 1.25F, tint, 0.5F * env * flicker);
        beam(base, 2.5F + e.height, 0.45F, whiter(tint, 0.5F), 0.7F * env * flicker);
        ring(base.add(0.0, 0.05, 0.0), 0.8F + 0.5F * smooth(0.0F, 0.5F, t), 0.7F, tint, 0.45F * env);
    }

    private static void drawBlossom(KillFx.Effect e, float t, Vec3 cam, float[] tint) {
        float env = smooth(0.0F, 0.25F, t) * (1.0F - smooth(1.6F, LIFE[BLOSSOM], t));
        if (env <= 0.01F) {
            return;
        }

        Vec3 base = new Vec3(e.x, e.y, e.z).subtract(cam);
        beam(base, 9.0F, 0.7F, tint, 0.25F * env);

        for (int i = 0; i < 3; i++) {
            float rt = t - i * 0.4F;
            if (rt > 0.0F && rt < 1.6F) {
                float q = rt / 1.6F;
                ring(base.add(0.0, 0.1 + 3.5F * q, 0.0), 1.2F * (1.0F - q) + 0.3F, 0.12F, tint, (1.0F - q) * env);
            }
        }

        ring(base.add(0.0, 0.05, 0.0), 1.0F + 1.2F * smooth(0.0F, 1.0F, t), 0.35F, tint, 0.4F * env);
    }

    private static void drawWarpLight(KillFx.Effect e, float t, Vec3 cam, float[] tint) {
        Vec3 base = new Vec3(e.x, e.y, e.z).subtract(cam);
        Vec3 chest = new Vec3(e.x, e.y + e.height * 0.5, e.z).subtract(cam);
        float k = Mth.clamp(t / WARP_TIME, 0.0F, 1.0F);
        if (k < 1.0F) {
            facingRing(chest, 1.4F * (1.0F - k) + 0.1F, 0.12F, tint, 0.8F * k);
        }

        float q = (t - WARP_TIME) / (LIFE[WARP] - WARP_TIME);
        if (q > 0.0F && q < 1.0F) {
            float a = (1.0F - q) * (1.0F - q);
            beam(base, 7.0F * (1.0F - q) + 2.0F, 0.35F, tint, a);
            beam(base, 5.0F * (1.0F - q) + 1.0F, 0.1F, WHITE6, a);
            facingRing(chest, 0.2F + 2.8F * easeOut(q), 0.15F * (1.0F - q) + 0.03F, tint, a);
        }
    }

    private static float envelope(float t, float in, float outStart, float life) {
        return smooth(0.0F, in, t) * (1.0F - smooth(outStart, life, t));
    }

    private static void drawVortex(KillFx.Effect e, float t, Vec3 cam, float[] tint) {
        float env = envelope(t, 0.2F, LIFE[VORTEX] * 0.6F, LIFE[VORTEX]);
        if (env <= 0.01F) {
            return;
        }

        Vec3 base = new Vec3(e.x, e.y, e.z).subtract(cam);
        float grow = easeOut(t / 0.6F);
        for (int i = 0; i < 10; i++) {
            float level = i / 9.0F;
            double wob = t * 6.0F + i * 0.55F;
            Vec3 c = base.add(Math.cos(wob) * 0.12F * i, (0.1F + i * 0.5F) * grow, Math.sin(wob) * 0.12F * i);
            ring(c, (0.35F + level * 2.4F) * grow, 0.06F + 0.05F * level, tint, env * (0.85F - 0.45F * level));
        }

        for (int arm = 0; arm < 3; arm++) {
            List<Vec3> pts = new ArrayList<>();
            for (int j = 0; j <= 20; j++) {
                float f = j / 20.0F;
                double ang = t * 9.0F + arm * Math.PI * 2.0 / 3.0 + f * 6.0;
                float r = (0.3F + f * 2.6F) * grow;
                pts.add(base.add(Math.cos(ang) * r, f * 4.6F * grow, Math.sin(ang) * r));
            }

            ribbon(pts, 0.08F, whiter(tint, 0.3F), 0.7F * env, null);
        }

        beam(base, 5.0F * grow, 0.35F, WHITE6, 0.35F * env);
    }

    private static Vec3 cometStart(KillFx.Effect e) {
        Random r = new Random(e.seed);
        double ang = r.nextDouble() * Math.PI * 2.0;
        return new Vec3(e.x + Math.cos(ang) * 9.0, e.y + 22.0, e.z + Math.sin(ang) * 9.0);
    }

    private static void drawComet(KillFx.Effect e, float t, Vec3 cam, float[] tint) {
        Vec3 target = new Vec3(e.x, e.y + 0.2, e.z);
        Vec3 start = cometStart(e);
        if (t < COMET_FALL) {
            float q = t / COMET_FALL;
            q *= q;
            Vec3 head = start.add(target.subtract(start).scale(q)).subtract(cam);
            Vec3 back = target.subtract(start).normalize().scale(-1.0);
            List<Vec3> tail = new ArrayList<>();
            for (int j = 0; j <= 8; j++) {
                tail.add(head.add(back.scale(j * 0.9F)));
            }

            ribbon(tail.reversed(), 0.45F, tint, 0.75F, null);
            ribbon(tail.reversed(), 0.14F, WHITE6, 1.0F, null);
            facingRing(head, 0.25F, 0.3F, WHITE6, 1.0F);
            facingRing(head, 0.5F, 0.35F, tint, 0.7F);
            return;
        }

        float q = Mth.clamp((t - COMET_FALL) / (LIFE[COMET] - COMET_FALL), 0.0F, 1.0F);
        float a = (1.0F - q) * (1.0F - q);
        Vec3 feet = new Vec3(e.x, e.y + 0.05, e.z).subtract(cam);
        ring(feet, 0.4F + 6.5F * easeOut(q), 0.5F * (1.0F - q) + 0.06F, tint, a);
        ring(feet, 0.3F + 3.5F * easeOut(Math.min(1.0F, q * 1.5F)), 0.12F, WHITE6, a);
        ring(feet, 1.1F, 0.9F, tint, 0.5F * (1.0F - smooth(0.2F, 1.0F, q)));
        if (q < 0.3F) {
            beam(feet, 9.0F * (1.0F - q / 0.3F), 1.1F, tint, 1.0F - q / 0.3F);
        }

        Random r = new Random(e.seed + 3L);
        for (int i = 0; i < 6; i++) {
            double ang = Math.PI * 2.0 * i / 6.0 + r.nextFloat() * 0.6F;
            float len = (1.8F + r.nextFloat() * 1.6F) * easeOut(Math.min(1.0F, q * 5.0F));
            List<Vec3> crack = new ArrayList<>();
            for (int j = 0; j <= 5; j++) {
                float f = j / 5.0F;
                double wob = ang + (r.nextFloat() - 0.5F) * 0.5F;
                crack.add(feet.add(Math.cos(wob) * len * f, 0.01, Math.sin(wob) * len * f));
            }

            groundLine(crack, tint, 0.9F * (1.0F - smooth(0.4F, 1.0F, q)));
        }
    }

    private static void drawAurora(KillFx.Effect e, float t, Vec3 cam, float[] tint) {
        float env = envelope(t, 0.5F, LIFE[AURORA] * 0.62F, LIFE[AURORA]);
        if (env <= 0.01F) {
            return;
        }

        Vec3 base = new Vec3(e.x, e.y, e.z).subtract(cam);
        Random r = new Random(e.seed);
        for (int curtain = 0; curtain < 3; curtain++) {
            float radius = 2.2F + curtain * 0.8F;
            double start = r.nextDouble() * Math.PI * 2.0;
            for (int i = 0; i < 12; i++) {
                double ang = start + i * 0.14;
                float wave = Mth.sin(t * 2.5F + i * 0.7F + curtain * 1.9F);
                Vec3 foot = base.add(Math.cos(ang) * (radius + wave * 0.25F), 1.6F + curtain * 0.5F + wave * 0.3F, Math.sin(ang) * (radius + wave * 0.25F));
                float height = 2.2F + 0.9F * Mth.sin(t * 1.7F + i * 0.45F);
                beam(foot, height, 0.32F, tint, 0.22F * env);
            }
        }

        ring(base.add(0.0, 0.05, 0.0), 1.6F + 0.6F * smooth(0.0F, 1.0F, t), 0.9F, tint, 0.25F * env);
        beam(base, 3.5F, 0.4F, whiter(tint, 0.5F), 0.3F * env);
    }

    private static void drawGlitch(KillFx.Effect e, float t, Vec3 cam) {
        float k = Mth.clamp(t / GLITCH_TIME, 0.0F, 1.0F);
        long frame = (long)(t * 24.0F);
        if (k >= 1.0F || (frame % 5L == 3L && k > 0.4F)) {
            return;
        }

        int alpha = alpha(1.0F - smooth(0.75F, 1.0F, k));
        float ox = (float)(e.x - cam.x);
        float oy = (float)(e.y - cam.y);
        float oz = (float)(e.z - cam.z);
        float amount = 0.08F + 0.5F * k * k;
        long seed = e.seed;
        for (ModelSnapshot.Part part : e.parts) {
            ModelSnapshot.draw(part, capture, (v, pos, n) -> {
                int slice = (int)Math.floor(pos[1] * 7.0F);
                long h = (slice * 73856093L) ^ (frame * 19349663L) ^ seed;
                float shift = ((h >>> 8) % 1000L) / 1000.0F - 0.5F;
                float lift = ((h >>> 20) % 1000L) / 1000.0F > 0.85F ? 0.12F * k : 0.0F;
                pos[0] += shift * amount + ox;
                pos[1] += lift + oy;
                pos[2] += oz;
            }, alpha);
        }
    }

    private static void drawGlitchLight(KillFx.Effect e, float t, Vec3 cam, float[] tint) {
        float env = 1.0F - smooth(0.8F, LIFE[GLITCH], t);
        if (env <= 0.01F) {
            return;
        }

        Vec3 chest = new Vec3(e.x, e.y + e.height * 0.5, e.z).subtract(cam);
        long frame = (long)(t * 18.0F);
        Random r = new Random(e.seed + frame);
        for (int i = 0; i < 5; i++) {
            Vec3 at = chest.add((r.nextFloat() - 0.5F) * 1.6F, (r.nextFloat() - 0.5F) * e.height, (r.nextFloat() - 0.5F) * 1.6F);
            facingRing(at, 0.1F + r.nextFloat() * 0.35F, 0.03F, i % 2 == 0 ? tint : WHITE6, 0.8F * env);
        }

        if (t > GLITCH_TIME && t < GLITCH_TIME + 0.4F) {
            float q = (t - GLITCH_TIME) / 0.4F;
            facingRing(chest, 0.2F + 2.4F * easeOut(q), 0.08F, tint, 1.0F - q);
            beam(new Vec3(e.x, e.y, e.z).subtract(cam), 6.0F * (1.0F - q), 0.25F, WHITE6, 1.0F - q);
        }
    }

    private static void drawStarfall(KillFx.Effect e, float t, Vec3 cam, float[] tint) {
        Vec3 base = new Vec3(e.x, e.y, e.z).subtract(cam);
        Random r = new Random(e.seed);
        for (int i = 0; i < 16; i++) {
            double ang = r.nextDouble() * Math.PI * 2.0;
            float dist = 0.6F + r.nextFloat() * 2.8F;
            float delay = r.nextFloat() * 0.9F;
            float fall = 0.55F + r.nextFloat() * 0.25F;
            float local = t - delay;
            if (local < 0.0F) {
                continue;
            }

            Vec3 ground = base.add(Math.cos(ang) * dist, 0.05, Math.sin(ang) * dist);
            if (local < fall) {
                float q = local / fall;
                Vec3 star = ground.add(0.0, 12.0F * (1.0F - q * q), 0.0);
                ribbon(List.of(star.add(0.0, 2.2, 0.0), star), 0.07F, tint, 0.8F, null);
                facingRing(star, 0.08F, 0.12F, WHITE6, 1.0F);
            } else {
                float q = Mth.clamp((local - fall) / 0.6F, 0.0F, 1.0F);
                if (q < 1.0F) {
                    ring(ground, 0.1F + 0.9F * easeOut(q), 0.1F * (1.0F - q) + 0.02F, tint, 1.0F - q);
                    beam(ground, 1.4F * (1.0F - q), 0.18F, WHITE6, 0.8F * (1.0F - q));
                }
            }
        }

        float env = envelope(t, 0.3F, LIFE[STARFALL] * 0.7F, LIFE[STARFALL]);
        ring(base.add(0.0, 0.04, 0.0), 3.4F, 0.6F, tint, 0.18F * env);
    }

    private static void drawShockwave(KillFx.Effect e, float t, Vec3 cam, float[] tint) {
        Vec3 chest = new Vec3(e.x, e.y + e.height * 0.5, e.z).subtract(cam);
        Vec3 feet = new Vec3(e.x, e.y + 0.05, e.z).subtract(cam);
        for (int i = 0; i < 4; i++) {
            float q = (t - i * 0.12F) / 0.9F;
            if (q > 0.0F && q < 1.0F) {
                float a = (1.0F - q) * (1.0F - q);
                facingRing(chest, 0.2F + (3.0F + i) * easeOut(q), 0.18F * (1.0F - q) + 0.03F, i % 2 == 0 ? tint : WHITE6, a);
            }
        }

        for (int i = 0; i < 2; i++) {
            float q = (t - 0.05F - i * 0.2F) / 1.1F;
            if (q > 0.0F && q < 1.0F) {
                float a = (1.0F - q) * (1.0F - q);
                ring(feet, 0.3F + 8.0F * easeOut(q), 0.6F * (1.0F - q) + 0.05F, tint, a);
            }
        }

        if (t < 0.25F) {
            beam(feet, 10.0F, 0.9F, whiter(tint, 0.4F), 1.0F - t / 0.25F);
        }
    }

    private static void drawEclipse(KillFx.Effect e, float t, Vec3 cam, float[] tint) {
        float env = envelope(t, 0.35F, LIFE[ECLIPSE] * 0.65F, LIFE[ECLIPSE]);
        if (env <= 0.01F) {
            return;
        }

        Vec3 feet = new Vec3(e.x, e.y, e.z).subtract(cam);
        Vec3 sun = feet.add(0.0, e.height + 2.2F, 0.0);
        float size = 0.9F * easeOut(t / 0.5F);
        facingRing(sun, size, 0.06F, WHITE6, env);
        facingRing(sun, size * 1.15F, 0.35F, tint, 0.7F * env);
        Vec3[] basis = facing(sun);
        for (int i = 0; i < 18; i++) {
            double ang = Math.PI * 2.0 * i / 18.0 + t * 0.6F;
            float flick = 0.6F + 0.4F * Mth.sin(t * 9.0F + i * 1.3F);
            float reach = size * (1.5F + 1.4F * flick);
            ribbon(List.of(around(sun, basis, ang, size * 1.1F), around(sun, basis, ang + 0.05, reach)), 0.05F, tint, 0.6F * env * flick, null);
        }

        beam(feet, e.height + 2.2F, 0.5F, tint, 0.3F * env);
        ring(feet.add(0.0, 0.05, 0.0), 1.4F, 0.9F, tint, 0.3F * env);
    }

    private static void drawCrystal(KillFx.Effect e, float t, Vec3 cam, float[] tint) {
        Vec3 feet = new Vec3(e.x, e.y, e.z).subtract(cam);
        float grow = easeOut(t / 0.35F);
        float fade = 1.0F - smooth(CRYSTAL_BREAK, CRYSTAL_BREAK + 0.25F, t);
        Random r = new Random(e.seed);
        for (int i = 0; i < 9; i++) {
            double ang = Math.PI * 2.0 * i / 9.0 + r.nextFloat() * 0.4F;
            float dist = 0.55F + r.nextFloat() * 0.5F;
            float len = (1.4F + r.nextFloat() * 1.6F) * grow;
            float lean = 0.35F + r.nextFloat() * 0.35F;
            Vec3 root = feet.add(Math.cos(ang) * dist, 0.0, Math.sin(ang) * dist);
            Vec3 tip = root.add(Math.cos(ang) * len * lean, len, Math.sin(ang) * len * lean);
            if (fade > 0.01F) {
                Vec3 mid = root.add(tip.subtract(root).scale(0.55));
                ribbon(List.of(root, mid, tip), 0.22F, tint, 0.55F * fade, null);
                ribbon(List.of(root, mid, tip), 0.07F, WHITE6, 0.9F * fade, null);
            }
        }

        ring(feet.add(0.0, 0.05, 0.0), 1.3F * grow, 0.7F, tint, 0.4F * (1.0F - smooth(0.6F, LIFE[CRYSTAL], t)));
        float q = (t - CRYSTAL_BREAK) / 0.6F;
        if (q > 0.0F && q < 1.0F) {
            facingRing(feet.add(0.0, 1.0, 0.0), 0.3F + 3.0F * easeOut(q), 0.15F * (1.0F - q) + 0.03F, tint, (1.0F - q) * (1.0F - q));
        }
    }

    private static void drawHalo(KillFx.Effect e, float t, Vec3 cam, float[] tint) {
        float env = envelope(t, 0.3F, LIFE[HALO] * 0.6F, LIFE[HALO]);
        if (env <= 0.01F) {
            return;
        }

        Vec3 feet = new Vec3(e.x, e.y, e.z).subtract(cam);
        float rise = 1.6F * smooth(0.2F, LIFE[HALO], t);
        Vec3 crown = feet.add(0.0, e.height + 0.45F + rise, 0.0);
        ring(crown, 0.42F, 0.05F, WHITE6, env);
        ring(crown, 0.45F, 0.16F, tint, 0.7F * env);
        Vec3 back = feet.add(0.0, e.height * 0.7F + rise, 0.0);
        Vec3 toCam = new Vec3(-back.x, 0.0, -back.z);
        Vec3 side = toCam.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : new Vec3(0.0, 1.0, 0.0).cross(toCam).normalize();
        float spread = easeOut(t / 0.6F);
        for (int wing = -1; wing <= 1; wing += 2) {
            for (int f = 0; f < 6; f++) {
                List<Vec3> feather = new ArrayList<>();
                float lift = (f - 2.0F) * 0.22F;
                for (int j = 0; j <= 6; j++) {
                    float k = j / 6.0F;
                    float out = (0.25F + k * (1.5F - f * 0.12F)) * spread;
                    feather.add(back.add(side.scale(wing * out)).add(0.0, lift + Mth.sin(k * 2.4F) * 0.5F * spread - k * f * 0.08F, 0.0));
                }

                ribbon(feather, 0.07F, f % 2 == 0 ? tint : whiter(tint, 0.5F), 0.6F * env, null);
            }
        }

        beam(feet, 9.0F, 0.6F, whiter(tint, 0.6F), 0.28F * env);
    }

    private static void drawSupernova(KillFx.Effect e, float t, Vec3 cam, float[] tint) {
        Vec3 chest = new Vec3(e.x, e.y + e.height * 0.5, e.z).subtract(cam);
        if (t < NOVA_GATHER) {
            float q = t / NOVA_GATHER;
            for (int i = 0; i < 3; i++) {
                facingRing(chest, (2.4F - i * 0.5F) * (1.0F - q) + 0.05F, 0.05F, i == 1 ? WHITE6 : tint, q);
            }

            facingRing(chest, 0.08F + 0.2F * q, 0.2F * q, WHITE6, q);
            return;
        }

        float q = Mth.clamp((t - NOVA_GATHER) / (LIFE[NOVA] - NOVA_GATHER), 0.0F, 1.0F);
        float a = (1.0F - q) * (1.0F - q);
        float radius = 0.3F + 5.0F * easeOut(q);
        Random r = new Random(e.seed);
        for (int i = 0; i < 6; i++) {
            Vec3 axis = new Vec3(r.nextFloat() - 0.5F, r.nextFloat() - 0.5F, r.nextFloat() - 0.5F).normalize();
            Vec3 u = axis.cross(new Vec3(0.0, 1.0, 0.0));
            if (u.lengthSqr() < 1.0E-4) {
                u = new Vec3(1.0, 0.0, 0.0);
            }

            u = u.normalize();
            ringIn(chest, new Vec3[]{u, axis.cross(u).normalize()}, radius * (0.8F + 0.2F * i / 5.0F), 0.12F * (1.0F - q) + 0.03F, i % 2 == 0 ? tint : WHITE6, a);
        }

        for (int i = 0; i < 12; i++) {
            Vec3 dir = new Vec3(r.nextFloat() - 0.5F, r.nextFloat() - 0.3F, r.nextFloat() - 0.5F).normalize();
            ribbon(List.of(chest.add(dir.scale(radius * 0.3F)), chest.add(dir.scale(radius * 1.25F))), 0.06F, tint, 0.8F * a, null);
        }

        if (q < 0.2F) {
            facingRing(chest, 1.2F * (q / 0.2F) + 0.2F, 1.2F * (1.0F - q / 0.2F), WHITE6, 1.0F - q / 0.2F);
        }

        ring(new Vec3(e.x, e.y + 0.05, e.z).subtract(cam), 0.3F + 6.0F * easeOut(q), 0.4F * (1.0F - q) + 0.05F, tint, a);
    }

    private static void drawThunder(KillFx.Effect e, float t, Vec3 cam, float[] tint) {
        Vec3 feet = new Vec3(e.x, e.y, e.z).subtract(cam);
        boolean on = t < 0.09F || t > 0.13F && t < 0.21F || t > 0.27F && t < 0.45F;
        if (on) {
            float a = t > 0.27F ? 1.0F - (t - 0.27F) / 0.18F * 0.6F : 1.0F;
            Random r = new Random(e.seed + (long)(t / 0.07F));
            Vec3 top = feet.add((r.nextFloat() - 0.5F) * 5.0F, 26.0, (r.nextFloat() - 0.5F) * 5.0F);
            List<Vec3> main = zigzag(top, feet.add(0.0, 0.05, 0.0), 5, 0.2F, r);
            bolt(main, tint, a, 1.0F);

            for (int b = 0; b < 3; b++) {
                int from = 3 + r.nextInt(Math.max(1, main.size() - 8));
                Vec3 start = main.get(from);
                Vec3 dir = new Vec3(r.nextFloat() - 0.5F, -0.6F - r.nextFloat() * 0.6F, r.nextFloat() - 0.5F).normalize();
                bolt(zigzag(start, start.add(dir.scale(3.5F + r.nextFloat() * 4.0F)), 3, 0.22F, r), tint, a * 0.7F, 0.55F);
            }
        }

        for (int i = 0; i < 2; i++) {
            float q = (t - i * 0.12F) / 0.85F;
            if (!(q <= 0.0F) && !(q >= 1.0F)) {
                float radius = (i == 0 ? 7.5F : 4.5F) * easeOut(q);
                float a = (1.0F - q) * (1.0F - q);
                ring(feet.add(0.0, 0.06, 0.0), radius, 0.55F * (1.0F - q) + 0.08F, tint, a);
                ring(feet.add(0.0, 0.07, 0.0), radius, 0.1F, WHITE, a * 0.8F);
            }
        }

        float scorch = 1.0F - smooth(0.1F, 1.1F, t);
        if (scorch > 0.01F) {
            ring(feet.add(0.0, 0.04, 0.0), 0.5F, 0.6F, tint, 0.6F * scorch);
        }
    }

    private static List<Vec3> zigzag(Vec3 a, Vec3 b, int levels, float rough, Random r) {
        List<Vec3> pts = new ArrayList<>(List.of(a, b));
        float amount = (float)a.distanceTo(b) * rough;

        for (int l = 0; l < levels; l++) {
            List<Vec3> next = new ArrayList<>();

            for (int i = 0; i < pts.size() - 1; i++) {
                Vec3 p = pts.get(i);
                Vec3 q = pts.get(i + 1);
                next.add(p);
                next.add(p.add(q).scale(0.5).add((r.nextFloat() - 0.5F) * amount, (r.nextFloat() - 0.5F) * amount * 0.4F, (r.nextFloat() - 0.5F) * amount));
            }

            next.add(pts.getLast());
            pts = next;
            amount *= 0.55F;
        }

        return pts;
    }

    private static void bolt(List<Vec3> pts, float[] tint, float a, float width) {
        ribbon(pts, 0.55F * width, tint, 0.35F * a, null);
        ribbon(pts, 0.2F * width, whiter(tint, 0.4F), 0.75F * a, null);
        ribbon(pts, 0.06F * width, WHITE6, a, null);
    }

    private static void drawVoid(KillFx.Effect e, float t, Vec3 cam) {
        float k = Mth.clamp(t / 0.78F, 0.0F, 1.0F);
        float pull = k * k * k;
        int alpha = alpha(1.0F - smooth(0.62F, 0.82F, t));
        if (alpha > 1) {
            float cy = e.height * 0.5F;
            float ox = (float)(e.x - cam.x);
            float oy = (float)(e.y - cam.y);
            float oz = (float)(e.z - cam.z);

            for (ModelSnapshot.Part part : e.parts) {
                ModelSnapshot.draw(part, capture, (v, pos, n) -> {
                    float rx = pos[0];
                    float ry = pos[1] - cy;
                    float rz = pos[2];
                    float d = (float)Math.sqrt(rx * rx + ry * ry + rz * rz);
                    float ang = pull * (4.0F + 2.5F / (d + 0.25F));
                    float cos = Mth.cos(ang);
                    float sin = Mth.sin(ang);
                    float keep = 1.0F - pull;
                    float x = (rx * cos - rz * sin) * keep;
                    float z = (rx * sin + rz * cos) * keep;
                    pos[0] = x + ox;
                    pos[1] = cy + ry * keep * (1.0F - 0.5F * pull) + oy;
                    pos[2] = z + oz;
                }, alpha);
            }
        }
    }

    private static float voidRadius(float t) {
        return 0.55F * smooth(0.0F, 0.3F, t) * (1.0F - smooth(0.78F, 0.88F, t));
    }

    private static void singularity(RenderTarget main, KillFx.Effect e, float t, Vec3 cam) {
        float rs = voidRadius(t);
        if (!(rs <= 0.01F)) {
            Gpu.bind(main);
            Shader.POSITION_COLOR.bind();
            Gpu.enableBlend();
            Gpu.defaultBlendFunc();
            Gpu.enableDepthTest();
            Gpu.depthMask(false);
            Gpu.disableCull();
            Vec3 c = new Vec3(e.x, e.y + e.height * 0.5, e.z).subtract(cam);
            Vec3[] basis = facing(c);
            Mesh b = Mesh.begin(Mode.QUADS);
            int segs = 40;

            for (int i = 0; i < segs; i++) {
                double a0 = (Math.PI * 2) * i / segs;
                double a1 = (Math.PI * 2) * (i + 1) / segs;
                Vec3 p0 = around(c, basis, a0, rs);
                Vec3 p1 = around(c, basis, a1, rs);
                Vec3 q0 = around(c, basis, a0, rs * 1.25F);
                Vec3 q1 = around(c, basis, a1, rs * 1.25F);
                vtx(b, c, BLACK, 0.97F);
                vtx(b, p0, BLACK, 0.97F);
                vtx(b, p1, BLACK, 0.97F);
                vtx(b, c, BLACK, 0.97F);
                vtx(b, p0, BLACK, 0.97F);
                vtx(b, q0, BLACK, 0.0F);
                vtx(b, q1, BLACK, 0.0F);
                vtx(b, p1, BLACK, 0.97F);
            }

            b.draw();
            beginAdditive(main);
        }
    }

    private static void drawVoidLight(KillFx.Effect e, float t, Vec3 cam, float[] tint) {
        Vec3 c = new Vec3(e.x, e.y + e.height * 0.5, e.z).subtract(cam);
        float rs = voidRadius(t);
        if (rs > 0.01F) {
            facingRing(c, rs * 1.35F, 0.16F, tint, 0.9F);
            facingRing(c, rs * 1.05F, 0.05F, WHITE6, 0.9F);
            Vec3[] basis = facing(c);

            for (int arm = 0; arm < 3; arm++) {
                List<Vec3> pts = new ArrayList<>();

                for (int j = 0; j <= 18; j++) {
                    float f = j / 18.0F;
                    double ang = arm * Math.PI * 2.0 / 3.0 + t * 7.0F + f * 3.2;
                    pts.add(around(c, basis, ang, rs * (1.3F + (1.0F - f) * 2.4F)));
                }

                ribbon(pts, 0.09F, tint, 0.55F * smooth(0.0F, 0.25F, t), null);
            }
        }

        float q = (t - 0.78F) / 0.7F;
        if (q > 0.0F && q < 1.0F) {
            float a = (1.0F - q) * (1.0F - q);

            for (int i = 0; i < 3; i++) {
                float qi = Mth.clamp(q * (1.0F + i * 0.35F), 0.0F, 1.0F);
                facingRing(c, 0.3F + 4.5F * easeOut(qi), 0.25F * (1.0F - qi) + 0.05F, tint, a * (1.0F - i * 0.25F));
            }

            ring(new Vec3(e.x, e.y + 0.06, e.z).subtract(cam), 0.3F + 5.5F * easeOut(q), 0.4F * (1.0F - q) + 0.06F, tint, a);
            beam(new Vec3(e.x, e.y, e.z).subtract(cam), 5.0F * (1.0F - q) + 1.0F, 0.5F, WHITE6, a);
        }
    }

    private static void drawSword(KillFx.Effect e, float t, Vec3 cam, float[] tint) {
        float fall = Mth.clamp(t / 0.22F, 0.0F, 1.0F);
        float tipY = Mth.lerp(fall * fall, 14.0F, -0.465F);
        float shake = t > 0.22F ? Mth.sin(t * 90.0F) * 0.05F * Math.max(0.0F, 1.0F - (t - 0.22F) / 0.3F) : 0.0F;
        float env = smooth(0.0F, 0.04F, t) * (1.0F - smooth(0.95F, 1.7F, t));
        Vec3 tip = new Vec3(e.x, e.y + tipY, e.z).subtract(cam);
        Vec3 toCam = new Vec3(-tip.x, 0.0, -tip.z);
        if (toCam.lengthSqr() < 1.0E-4) {
            toCam = new Vec3(0.0, 0.0, 1.0);
        }

        Vec3 side = new Vec3(0.0, 1.0, 0.0).cross(toCam).normalize();
        Vec3 at = tip.add(side.scale(shake));
        if (env > 0.01F) {
            sword(at, side, 1.35F, tint, 0.2F * env);
            sword(at, side, 1.8F, tint, 0.1F * env);
            Gpu.defaultBlendFunc();
            sword(at, side, 1.0F, whiter(tint, 0.5F), 0.92F * env);
            sword(at, side, 0.45F, WHITE6, 0.9F * env);
            Gpu.blendFunc(770, 1);
            if (t < 0.22F) {
                beam(at.add(0.0, 2.852000021934509, 0.0), 8.0F, 0.3F, tint, 0.45F * env);
            }
        }

        float q = (t - 0.22F) / 1.1F;
        if (q > 0.0F && q < 1.0F) {
            Vec3 feet = new Vec3(e.x, e.y + 0.05, e.z).subtract(cam);
            float a = (1.0F - q) * (1.0F - q);
            ring(feet, 0.4F + 5.0F * easeOut(Math.min(1.0F, q * 1.6F)), 0.45F * (1.0F - q) + 0.06F, tint, a);
            Random r = new Random(e.seed);

            for (int i = 0; i < 7; i++) {
                double ang = (Math.PI * 2) * i / 7.0 + r.nextFloat() * 0.5;
                float len = (2.2F + r.nextFloat() * 1.8F) * easeOut(Math.min(1.0F, q * 4.0F));
                List<Vec3> crack = new ArrayList<>();
                crack.add(feet);

                for (int j = 1; j <= 6; j++) {
                    float f = j / 6.0F;
                    double wob = ang + (r.nextFloat() - 0.5F) * 0.5F;
                    crack.add(feet.add(Math.cos(wob) * len * f, 0.01, Math.sin(wob) * len * f));
                }

                groundLine(crack, tint, 0.9F * (1.0F - smooth(0.35F, 1.0F, q)));
            }

            if (q < 0.25F) {
                beam(new Vec3(e.x, e.y, e.z).subtract(cam), 7.0F, 0.7F, WHITE6, 1.0F - q / 0.25F);
            }
        }
    }

    private static void sword(Vec3 tip, Vec3 side, float width, float[] tint, float a) {
        Mesh b = Mesh.begin(Mode.QUADS);
        float g = (width - 1.0F) * 0.14F;
        swordSize = 0.62F;
        quad2d(b, tip, side, tint, a, 0.0F, -g, 0.26F * width, 0.55F, 0.0F, 0.55F, -0.26F * width, 0.55F);
        quad2d(b, tip, side, tint, a, -0.26F * width, 0.55F, 0.26F * width, 0.55F, 0.24F * width, 3.4F, -0.24F * width, 3.4F);
        quad2d(b, tip, side, tint, a, -0.95F * width, 3.36F - g, 0.95F * width, 3.36F - g, 0.8F * width, 3.64F + g, -0.8F * width, 3.64F + g);
        quad2d(b, tip, side, tint, a, -0.1F * width, 3.64F, 0.1F * width, 3.64F, 0.1F * width, 4.45F, -0.1F * width, 4.45F);
        float pw = 0.2F * width;
        quad2d(b, tip, side, tint, a, 0.0F, 4.4F - pw * 0.5F, pw, 4.58F, 0.0F, 4.58F + pw, -pw, 4.58F);
        b.draw();
    }

    private static void quad2d(Mesh b, Vec3 tip, Vec3 side, float[] tint, float a, float... uv) {
        for (int i = 0; i < 4; i++) {
            float u = uv[i * 2] * swordSize;
            float v = uv[i * 2 + 1] * swordSize;
            vtx(b, tip.add(side.scale(u)).add(0.0, v, 0.0), mixc(tint, Mth.clamp(v / (4.6F * swordSize), 0.0F, 1.0F)), a);
        }
    }

    private static void beginAdditive(RenderTarget main) {
        Gpu.bind(main);
        Shader.POSITION_COLOR.bind();
        Gpu.enableBlend();
        Gpu.blendFunc(770, 1);
        Gpu.enableDepthTest();
        Gpu.depthMask(false);
        Gpu.disableCull();
    }

    private static void ribbon(List<Vec3> pts, float width, float[] tint, float a, Vec3 flatUp) {
        if (pts.size() >= 2 && !(a <= 0.004F)) {
            Mesh b = Mesh.begin(Mode.QUADS);

            for (int i = 0; i < pts.size() - 1; i++) {
                Vec3 p = pts.get(i);
                Vec3 q = pts.get(i + 1);
                Vec3 across = flatUp != null ? flatUp : p.add(q).scale(0.5);
                Vec3 side = q.subtract(p).cross(across).normalize().scale(width);
                float[] c0 = mixc(tint, (float)i / (pts.size() - 1));
                float[] c1 = mixc(tint, (float)(i + 1) / (pts.size() - 1));
                vtx(b, p.subtract(side), c0, 0.0F);
                vtx(b, p, c0, a);
                vtx(b, q, c1, a);
                vtx(b, q.subtract(side), c1, 0.0F);
                vtx(b, p, c0, a);
                vtx(b, p.add(side), c0, 0.0F);
                vtx(b, q.add(side), c1, 0.0F);
                vtx(b, q, c1, a);
            }

            b.draw();
        }
    }

    private static void groundLine(List<Vec3> pts, float[] tint, float a) {
        ribbon(pts, 0.07F, tint, a, new Vec3(0.0, 1.0, 0.0));
    }

    private static void beam(Vec3 base, float height, float width, float[] tint, float a) {
        if (!(a <= 0.004F)) {
            Vec3 toCam = new Vec3(-base.x, 0.0, -base.z);
            if (toCam.lengthSqr() < 1.0E-4) {
                toCam = new Vec3(0.0, 0.0, 1.0);
            }

            Vec3 side = new Vec3(0.0, 1.0, 0.0).cross(toCam).normalize();
            Mesh b = Mesh.begin(Mode.QUADS);
            int segs = 14;

            for (int i = 0; i < segs; i++) {
                float y0 = height * i / segs;
                float y1 = height * (i + 1) / segs;
                float f0 = (float)Math.pow(1.0F - (float)i / segs, 1.6);
                float f1 = (float)Math.pow(1.0F - (float)(i + 1) / segs, 1.6);
                float w0 = width * (0.55F + 0.45F * f0);
                float w1 = width * (0.55F + 0.45F * f1);
                float[] c0 = mixc(tint, (float)i / segs);
                float[] c1 = mixc(tint, (float)(i + 1) / segs);
                Vec3 p = base.add(0.0, y0, 0.0);
                Vec3 q = base.add(0.0, y1, 0.0);
                vtx(b, p.subtract(side.scale(w0)), c0, 0.0F);
                vtx(b, p, c0, a * f0);
                vtx(b, q, c1, a * f1);
                vtx(b, q.subtract(side.scale(w1)), c1, 0.0F);
                vtx(b, p, c0, a * f0);
                vtx(b, p.add(side.scale(w0)), c0, 0.0F);
                vtx(b, q.add(side.scale(w1)), c1, 0.0F);
                vtx(b, q, c1, a * f1);
            }

            b.draw();
        }
    }

    private static void ring(Vec3 center, float radius, float thickness, float[] tint, float a) {
        ringIn(center, null, radius, thickness, tint, a);
    }

    private static void facingRing(Vec3 center, float radius, float thickness, float[] tint, float a) {
        ringIn(center, facing(center), radius, thickness, tint, a);
    }

    private static void ringIn(Vec3 center, Vec3[] basis, float radius, float thickness, float[] tint, float a) {
        if (!(a <= 0.004F) && !(radius <= 0.0F)) {
            Vec3[] axes = basis != null ? basis : new Vec3[]{new Vec3(1.0, 0.0, 0.0), new Vec3(0.0, 0.0, 1.0)};
            Mesh b = Mesh.begin(Mode.QUADS);
            int segs = 48;
            float in = Math.max(0.0F, radius - thickness);
            float out = radius + thickness;
            float spin = (float)(clock() % 100000L) / 1000.0F;

            for (int i = 0; i < segs; i++) {
                double a0 = (Math.PI * 2) * i / segs;
                double a1 = (Math.PI * 2) * (i + 1) / segs;
                float[] c0 = mixc(tint, 0.5F + 0.5F * (float)Math.sin(a0 * 2.0 + spin * 1.5));
                float[] c1 = mixc(tint, 0.5F + 0.5F * (float)Math.sin(a1 * 2.0 + spin * 1.5));
                vtx(b, around(center, axes, a0, in), c0, 0.0F);
                vtx(b, around(center, axes, a0, radius), c0, a);
                vtx(b, around(center, axes, a1, radius), c1, a);
                vtx(b, around(center, axes, a1, in), c1, 0.0F);
                vtx(b, around(center, axes, a0, radius), c0, a);
                vtx(b, around(center, axes, a0, out), c0, 0.0F);
                vtx(b, around(center, axes, a1, out), c1, 0.0F);
                vtx(b, around(center, axes, a1, radius), c1, a);
            }

            b.draw();
        }
    }

    private static Vec3[] facing(Vec3 c) {
        Vec3 view = c.lengthSqr() < 1.0E-6 ? new Vec3(0.0, 0.0, 1.0) : c.normalize();
        Vec3 right = view.cross(new Vec3(0.0, 1.0, 0.0));
        if (right.lengthSqr() < 1.0E-6) {
            right = new Vec3(1.0, 0.0, 0.0);
        }

        right = right.normalize();
        return new Vec3[]{right, right.cross(view).normalize()};
    }

    private static Vec3 around(Vec3 c, Vec3[] axes, double angle, float r) {
        double cos = Math.cos(angle) * r;
        double sin = Math.sin(angle) * r;
        return c.add(axes[0].scale(cos)).add(axes[1].scale(sin));
    }

    private static long clock() {
        return System.currentTimeMillis();
    }

    private static void vtx(Mesh b, Vec3 p, float[] c, float a) {
        b.addVertex((float)p.x, (float)p.y, (float)p.z).setColor(c[0], c[1], c[2], Mth.clamp(a, 0.0F, 1.0F));
    }

    private static float[] mixc(float[] t, float k) {
        return t.length < 6 ? t : new float[]{t[0] + (t[3] - t[0]) * k, t[1] + (t[4] - t[1]) * k, t[2] + (t[5] - t[2]) * k};
    }

    private static float[] whiter(float[] t, float w) {
        float[] o = t.clone();

        for (int i = 0; i < o.length; i++) {
            o[i] += (1.0F - o[i]) * w;
        }

        return o;
    }

    private static void burst(Minecraft mc, KillFx.Effect e) {
        sound(mc, FxSounds.of("kill", EFFECTS[e.type]), e);
        double cy = e.y + e.height * 0.5;
        switch (e.type) {
            case 0:
                particles(mc, ParticleTypes.END_ROD, e.x, cy, e.z, 22, 0.35);
                particles(mc, Compat.flash(), e.x, cy, e.z, 1, 0.0);
                break;
            case 1:
                particles(mc, ParticleTypes.SOUL_FIRE_FLAME, e.x, e.y + 0.3, e.z, 18, 0.06);
                break;
            case 2:
                particles(mc, ParticleTypes.ELECTRIC_SPARK, e.x, e.y + 0.3, e.z, 40, 0.9);
                particles(mc, Compat.flash(), e.x, e.y + 0.5, e.z, 1, 0.0);
                particles(mc, ParticleTypes.LARGE_SMOKE, e.x, e.y + 0.2, e.z, 6, 0.08);
                break;
            case FIREWORK:
                particles(mc, ParticleTypes.FIREWORK, e.x, e.y + 0.2, e.z, 8, 0.05);
                break;
            case FROST:
                particles(mc, ParticleTypes.SNOWFLAKE, e.x, cy, e.z, 24, 0.12);
                break;
            case INFERNO:
                particles(mc, ParticleTypes.FLAME, e.x, e.y + 0.2, e.z, 30, 0.12);
                particles(mc, ParticleTypes.LAVA, e.x, e.y + 0.3, e.z, 8, 0.2);
                break;
            case BLOSSOM:
                particles(mc, ParticleTypes.CHERRY_LEAVES, e.x, cy, e.z, 30, 0.15);
                break;
            case WARP:
                particles(mc, ParticleTypes.REVERSE_PORTAL, e.x, cy, e.z, 30, 0.3);
                break;
            case VORTEX:
                particles(mc, ParticleTypes.GUST, e.x, cy, e.z, 3, 0.0);
                particles(mc, ParticleTypes.CLOUD, e.x, e.y + 0.3, e.z, 16, 0.15);
                break;
            case AURORA:
                particles(mc, ParticleTypes.GLOW, e.x, cy, e.z, 14, 0.08);
                break;
            case GLITCH:
                particles(mc, ParticleTypes.ELECTRIC_SPARK, e.x, cy, e.z, 24, 0.4);
                break;
            case SHOCKWAVE:
                particles(mc, ParticleTypes.SONIC_BOOM, e.x, cy, e.z, 1, 0.0);
                particles(mc, ParticleTypes.POOF, e.x, e.y + 0.2, e.z, 20, 0.4);
                break;
            case ECLIPSE:
                particles(mc, ParticleTypes.END_ROD, e.x, e.y + e.height + 2.2, e.z, 16, 0.15);
                break;
            case CRYSTAL:
                particles(mc, ParticleTypes.WAX_OFF, e.x, e.y + 0.4, e.z, 20, 0.3);
                break;
            case HALO:
                particles(mc, ParticleTypes.END_ROD, e.x, e.y + e.height, e.z, 18, 0.12);
                break;
            case NOVA:
                particles(mc, ParticleTypes.ENCHANT, e.x, cy, e.z, 40, 1.2);
                break;
        }
    }

    private static void moments(Minecraft mc, KillFx.Effect e, float t) {
        double cy = e.y + e.height * 0.5;
        if (e.type == 3 && t >= 0.78F && (e.fired & 1) == 0) {
            e.fired |= 1;
            particles(mc, ParticleTypes.END_ROD, e.x, cy, e.z, 30, 0.6);
            particles(mc, Compat.flash(), e.x, cy, e.z, 1, 0.0);
        }

        if (e.type == FIREWORK && t >= FIREWORK_RISE && (e.fired & 1) == 0) {
            e.fired |= 1;
            double top = e.y + 0.1 + FIREWORK_HEIGHT;
            particles(mc, ParticleTypes.FIREWORK, e.x, top, e.z, 60, 0.35);
            particles(mc, Compat.flash(), e.x, top, e.z, 1, 0.0);
        }


        if (e.type == FROST && t >= FROST_FREEZE && (e.fired & 1) == 0) {
            e.fired |= 1;
            particles(mc, ParticleTypes.SNOWFLAKE, e.x, cy, e.z, 40, 0.35);
            particles(mc, Compat.flash(), e.x, cy, e.z, 1, 0.0);
        }

        if (e.type == WARP && t >= WARP_TIME && (e.fired & 1) == 0) {
            e.fired |= 1;
            particles(mc, ParticleTypes.PORTAL, e.x, cy, e.z, 50, 0.8);
        }

        if (e.type == COMET && t >= COMET_FALL && (e.fired & 1) == 0) {
            e.fired |= 1;
            particles(mc, Compat.flash(), e.x, e.y + 0.4, e.z, 1, 0.0);
            particles(mc, ParticleTypes.LAVA, e.x, e.y + 0.3, e.z, 14, 0.3);
            particles(mc, ParticleTypes.LARGE_SMOKE, e.x, e.y + 0.2, e.z, 10, 0.12);
        }

        if (e.type == GLITCH && t >= GLITCH_TIME && (e.fired & 1) == 0) {
            e.fired |= 1;
            particles(mc, ParticleTypes.ELECTRIC_SPARK, e.x, cy, e.z, 40, 0.8);
        }

        if (e.type == CRYSTAL && t >= CRYSTAL_BREAK && (e.fired & 1) == 0) {
            e.fired |= 1;
            particles(mc, ParticleTypes.WAX_OFF, e.x, e.y + 1.0, e.z, 50, 0.6);
        }

        if (e.type == NOVA && t >= NOVA_GATHER && (e.fired & 1) == 0) {
            e.fired |= 1;
            particles(mc, Compat.flash(), e.x, cy, e.z, 1, 0.0);
            particles(mc, ParticleTypes.FIREWORK, e.x, cy, e.z, 50, 0.45);
            particles(mc, ParticleTypes.END_ROD, e.x, cy, e.z, 30, 0.6);
        }

        if (e.type == 5 && t >= 0.22F && (e.fired & 1) == 0) {
            e.fired |= 1;
            particles(mc, Compat.flash(), e.x, e.y + 0.4, e.z, 1, 0.0);
            particles(mc, ParticleTypes.END_ROD, e.x, e.y + 0.2, e.z, 26, 0.5);
            particles(mc, ParticleTypes.CRIT, e.x, e.y + 0.2, e.z, 30, 0.9);
        }
    }

    private static void ambientParticles(Minecraft mc, long now) {
        if (now - lastAmbient >= 50L) {
            lastAmbient = now;

            for (KillFx.Effect e : effects) {
                float t = (float)(now - e.born) / 1000.0F;
                double cy = e.y + e.height * 0.5;
                if (e.type == 1 && t < 1.6F) {
                    float rise = 0.3F * t + 1.9F * t * t;
                    mc.particleEngine
                        .createParticle(
                            ParticleTypes.END_ROD, e.x + (RNG.nextDouble() - 0.5) * 0.5, cy + rise, e.z + (RNG.nextDouble() - 0.5) * 0.5, 0.0, 0.05, 0.0
                        );
                } else if (e.type == 3 && t < 0.78F) {
                    for (int i = 0; i < 4; i++) {
                        double dx = (RNG.nextDouble() - 0.5) * 4.0;
                        double dy = (RNG.nextDouble() - 0.5) * 3.0;
                        double dz = (RNG.nextDouble() - 0.5) * 4.0;
                        mc.particleEngine.createParticle(ParticleTypes.PORTAL, e.x, cy, e.z, dx, dy, dz);
                    }
                } else if (e.type == INFERNO && t < 1.6F) {
                    for (int i = 0; i < 3; i++) {
                        double px = e.x + (RNG.nextDouble() - 0.5) * 0.9;
                        double pz = e.z + (RNG.nextDouble() - 0.5) * 0.9;
                        mc.particleEngine.createParticle(ParticleTypes.FLAME, px, e.y + RNG.nextDouble() * e.height, pz, 0.0, 0.04, 0.0);
                        mc.particleEngine.createParticle(ParticleTypes.SMOKE, px, e.y + e.height, pz, 0.0, 0.06, 0.0);
                    }
                } else if (e.type == BLOSSOM && t < 2.0F) {
                    for (int i = 0; i < 2; i++) {
                        mc.particleEngine
                            .createParticle(
                                ParticleTypes.CHERRY_LEAVES,
                                e.x + (RNG.nextDouble() - 0.5) * 2.0,
                                e.y + e.height + RNG.nextDouble() * 2.0,
                                e.z + (RNG.nextDouble() - 0.5) * 2.0,
                                0.0,
                                0.0,
                                0.0
                            );
                    }
                } else if (e.type == AURORA && t < 2.0F) {
                    mc.particleEngine.createParticle(
                        ParticleTypes.END_ROD, e.x + (RNG.nextDouble() - 0.5) * 4.0, e.y + 1.5 + RNG.nextDouble() * 2.0, e.z + (RNG.nextDouble() - 0.5) * 4.0, 0.0, 0.02, 0.0
                    );
                } else if (e.type == VORTEX && t < 1.6F) {
                    double ang = RNG.nextDouble() * Math.PI * 2.0;
                    mc.particleEngine.createParticle(ParticleTypes.CLOUD, e.x + Math.cos(ang) * 1.2, e.y + RNG.nextDouble() * 3.0, e.z + Math.sin(ang) * 1.2, -Math.sin(ang) * 0.2, 0.08, Math.cos(ang) * 0.2);
                } else if (e.type == 4 && t < 1.4F) {
                    for (int i = 0; i < 3; i++) {
                        mc.particleEngine
                            .createParticle(
                                ParticleTypes.WHITE_ASH,
                                e.x + (RNG.nextDouble() - 0.5) * 0.8,
                                e.y + RNG.nextDouble() * e.height,
                                e.z + (RNG.nextDouble() - 0.5) * 0.8,
                                e.windX * 0.05,
                                0.03,
                                e.windZ * 0.05
                            );
                    }
                }
            }
        }
    }

    private static void particles(Minecraft mc, ParticleOptions type, double x, double y, double z, int n, double speed) {
        for (int i = 0; i < n; i++) {
            double vx = (RNG.nextDouble() - 0.5) * 2.0 * speed;
            double vy = RNG.nextDouble() * speed;
            double vz = (RNG.nextDouble() - 0.5) * 2.0 * speed;
            mc.particleEngine.createParticle(type, x, y, z, vx, vy, vz);
        }
    }

    private static void sound(Minecraft mc, SoundEvent s, KillFx.Effect e) {
        if (ModSettings.killEffectSounds) {
            mc.getSoundManager().play(new SimpleSoundInstance(s, SoundSource.PLAYERS, 1.0F, 1.0F, RandomSource.create(), e.x, e.y + 1.0, e.z));
        }
    }

    private static void hudFlash(GuiGraphics c) {
        if (flashAt >= 0L) {
            float t = (float)(clock() - flashAt) / 1000.0F;
            if (t > 0.3F) {
                flashAt = -1L;
            } else {
                float a = t < 0.05F ? 0.22F : 0.22F * (1.0F - (t - 0.05F) / 0.25F);
                int w = Minecraft.getInstance().getWindow().getGuiScaledWidth();
                int h = Minecraft.getInstance().getWindow().getGuiScaledHeight();
                c.fill(0, 0, w, h, (int)(a * 255.0F) << 24 | 16777215);
            }
        }
    }

    private static int alpha(float fade) {
        return Mth.clamp(Math.round(fade * 255.0F), 1, 255);
    }

    private static float smooth(float a, float b, float x) {
        float t = Mth.clamp((x - a) / (b - a), 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    private static float easeOut(float x) {
        float t = 1.0F - Mth.clamp(x, 0.0F, 1.0F);
        return 1.0F - t * t * t;
    }

    private static final class Effect {
        int type;
        double x;
        double y;
        double z;
        float height;
        long born;
        long seed;
        int fired;
        List<ModelSnapshot.Part> parts = List.of();
        float[][] shards;
        float windX;
        float windZ;
    }
}
