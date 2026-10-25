package dev.infectedware.skysong.render.fx;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import dev.infectedware.skysong.ModSettings;
import dev.infectedware.skysong.compat.Compat;
import dev.infectedware.skysong.compat.WorldCtx;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public final class Afterimages {
    private static final List<Afterimages.Ghost> ghosts = new ArrayList<>();
    private static final Map<UUID, Long> lastSpawn = new HashMap<>();
    private static final ModelSnapshot.Composite composite = new ModelSnapshot.Composite();
    private static TextureTarget capture;

    private Afterimages() {
    }

    public static boolean active() {
        return Fx.allowed() && ModSettings.afterimages;
    }

    public static boolean recording() {
        return ModelSnapshot.recording();
    }

    public static void render(WorldCtx ctx) {
        if (!active()) {
            if (!ghosts.isEmpty()) {
                ghosts.clear();
            }
        } else {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null && mc.player != null) {
                RenderTarget main = mc.getMainRenderTarget();
                long now = System.currentTimeMillis();
                float tickDelta = ctx.partialTick();

                try {
                    spawn(mc, now, tickDelta);
                    long life = Math.max(50L, (long)(ModSettings.afterimagesLife * 1000.0F));
                    ghosts.removeIf(g -> now - g.born() > life);
                    if (!ghosts.isEmpty()) {
                        draw(mc, main, Compat.camPos(ctx.camera()), now, life, tickDelta);
                    }
                } catch (Throwable t) {
                    Fx.fail("afterimages", t);
                    ghosts.clear();
                } finally {
                    Fx.restore(main);
                }
            }
        }
    }

    private static void spawn(Minecraft mc, long now, float tickDelta) {
        long every = Math.max(50L, (long)(ModSettings.afterimagesInterval * 1000.0F));

        for (Player p : mc.level.players()) {
            if ((p == mc.player || ModSettings.afterimagesTargets == 1)
                && p.isAlive()
                && !p.isInvisible()
                && !p.isSpectator()
                && !(p.distanceToSqr(Compat.camPos(mc.gameRenderer.getMainCamera())) > 4096.0)
                && moving(p)) {
                Long last = lastSpawn.get(p.getUUID());
                if (last == null || now - last >= every) {
                    lastSpawn.put(p.getUUID(), now);
                    List<ModelSnapshot.Part> parts = ModelSnapshot.record(p, tickDelta);
                    if (!parts.isEmpty()) {
                        Vec3 at = p.getPosition(tickDelta);
                        if (ghosts.size() >= 48) {
                            ghosts.removeFirst();
                        }

                        ghosts.add(new Afterimages.Ghost(p.getUUID(), at.x, at.y, at.z, now, parts));
                    }
                }
            }
        }

        if (lastSpawn.size() > 64) {
            lastSpawn.keySet().removeIf(id -> mc.level.getPlayerByUUID(id) == null);
        }
    }

    private static boolean moving(Player p) {
        double dx = p.getX() - p.xo;
        double dy = p.getY() - p.yo;
        double dz = p.getZ() - p.zo;
        return dx * dx + dz * dz > 9.0E-4 || Math.abs(dy) > 0.08;
    }

    private static void draw(Minecraft mc, RenderTarget main, Vec3 cam, long now, long life, float tickDelta) {
        capture = ModelSnapshot.beginCapture(capture, main);
        int drawn = 0;

        for (Afterimages.Ghost g : ghosts) {
            float t = Mth.clamp((float)(now - g.born()) / (float)life, 0.0F, 1.0F);
            float s = Mth.clamp((t - 0.3F) / 0.7F, 0.0F, 1.0F);
            float fade = 1.0F - s * s * (3.0F - 2.0F * s);
            Player owner = mc.level.getPlayerByUUID(g.owner());
            if (owner != null) {
                double gap = owner.getPosition(tickDelta).distanceTo(new Vec3(g.x(), g.y(), g.z()));
                fade *= (float)Mth.clamp((gap - 0.3) / 0.7, 0.0, 1.0);
            }

            double near = new Vec3(g.x(), g.y() + 1.2, g.z()).distanceTo(cam);
            fade *= (float)Mth.clamp((near - 0.9) / 1.1, 0.0, 1.0);
            if (!(fade <= 0.01F)) {
                int alpha = Mth.clamp(Math.round(fade * 255.0F), 1, 255);
                float ox = (float)(g.x() - cam.x);
                float oy = (float)(g.y() - cam.y);
                float oz = (float)(g.z() - cam.z);

                for (ModelSnapshot.Part part : g.parts()) {
                    ModelSnapshot.draw(part, capture, (v, pos, n) -> {
                        pos[0] += ox;
                        pos[1] += oy;
                        pos[2] += oz;
                    }, alpha);
                }

                drawn++;
            }
        }

        if (drawn != 0) {
            boolean glow = ModSettings.afterimagesGlow && ModSettings.afterimagesGlowStrength > 0.0F;
            composite.apply(
                capture,
                main,
                ModSettings.afterimagesOpacity,
                ModSettings.afterimagesGlowRadius,
                glow ? ModSettings.afterimagesGlowStrength : 0.0F,
                Fx.echoTints(ModSettings.afterimagesColor, ModSettings.afterimagesCustomColor, ModSettings.afterimagesCustomColor2)
            );
        }
    }

    private record Ghost(UUID owner, double x, double y, double z, long born, List<ModelSnapshot.Part> parts) {
    }
}
